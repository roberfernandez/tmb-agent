package cat.tmbagent.miralincorporatiu;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int START_STOP = 413;
    private static final int REFRESH_MS = 20_000;
    private static final int BG = Color.rgb(9, 17, 28);
    private static final int PANEL = Color.rgb(19, 34, 52);
    private static final int TEXT = Color.rgb(241, 246, 252);
    private static final int MUTED = Color.rgb(176, 194, 211);
    private static final int ACCENT = Color.rgb(246, 205, 44);

    private static final String[] STOPS = {
        "La Pau", "Besòs", "Besòs Mar", "El Maresme | Fòrum", "Selva de Mar",
        "Poblenou", "Llacuna", "Bogatell", "Ciutadella | Vila Olímpica",
        "Barceloneta", "Jaume I", "Urquinaona", "Passeig de Gràcia", "Girona",
        "Verdaguer", "Joanic", "Alfons X", "Guinardó | Hospital de Sant Pau",
        "Maragall", "Llucmajor", "Via Júlia", "Trinitat Nova"
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final CorporateMiralinClient client = new CorporateMiralinClient();
    private CredentialVault vault;
    private CredentialVault.Login activeLogin;
    private EditText username;
    private EditText password;
    private EditText trainInput;
    private Spinner targetVia;
    private TextView eyeStatus;
    private Spinner station;
    private TextView status;
    private TextView updatedAt;
    private LinearLayout tables;
    private CorporateBoardParser.Board lastBoard;
    private long lastReadAt;
    private int lastStop = -1;
    private boolean running;
    private boolean busy;
    private boolean visible;

    private final Runnable autoRefresh = new Runnable() {
        @Override public void run() {
            if (!visible) return;
            if (running && activeLogin != null && !CorporateEyeService.isActive) fetch(false);
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        vault = new CredentialVault(this);
        buildScreen();
        try {
            activeLogin = vault.load();
            if (activeLogin != null) {
                username.setText(activeLogin.username);
                password.setHint("Contrasenya xifrada al dispositiu");
                running = true;
                status.setText("Credencials desades. Connectant al Miralín corporatiu…");
            }
        } catch (Exception ignored) {
            status.setText("No s'han pogut recuperar les credencials xifrades. Torna-les a introduir.");
            running = false;
            activeLogin = null;
        }
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setPadding(dp(16), dp(22), dp(16), dp(35));
        scroll.addView(screen);
        setContentView(scroll);

        TextView heading = label("MIRALÍN · TMB", 25, ACCENT, true);
        screen.addView(heading);
        TextView caption = label("Línia 4 · Consulta directa del cartell corporatiu", 13, MUTED, false);
        screen.addView(caption);
        space(screen, 16);

        station = new Spinner(this);
        String[] options = new String[STOPS.length];
        for (int i = 0; i < STOPS.length; i++) options[i] = (START_STOP + i) + " · " + STOPS[i];
        station.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options));
        station.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        int chosen = getSharedPreferences("ui", MODE_PRIVATE).getInt("stop", START_STOP);
        station.setSelection(Math.max(0, Math.min(STOPS.length - 1, chosen - START_STOP)));
        screen.addView(station);
        station.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                getSharedPreferences("ui", MODE_PRIVATE).edit().putInt("stop", START_STOP + pos).apply();
                if (lastStop != START_STOP + pos) {
                    lastBoard = null;
                    lastReadAt = 0;
                    renderTables();
                    if (running && activeLogin != null) fetch(false);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        space(screen, 12);

        username = new EditText(this);
        username.setSingleLine(true);
        username.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        username.setHint("Usuari corporatiu TMB");
        username.setTextColor(TEXT);
        username.setHintTextColor(MUTED);
        screen.addView(username);

        password = new EditText(this);
        password.setSingleLine(true);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setHint("Contrasenya corporativa");
        password.setTextColor(TEXT);
        password.setHintTextColor(MUTED);
        screen.addView(password);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button connect = new Button(this);
        connect.setText("Connecta");
        connect.setOnClickListener(v -> connectWithCredentials());
        actions.addView(connect, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button update = new Button(this);
        update.setText("Actualitza");
        update.setOnClickListener(v -> {
            if (activeLogin == null || !running) connectWithCredentials();
            else fetch(false);
        });
        actions.addView(update, new LinearLayout.LayoutParams(0, dp(52), 1));
        screen.addView(actions);
        space(screen, 12);

        screen.addView(label("ULL FLOTANT · SEGUIMENT DE TREN", 18, ACCENT, true));
        screen.addView(label("Escull un tren de la taula o escriu-ne el número. "
            + "T. ant. és la separació amb el tren anterior, no el temps d'arribada.", 12, MUTED, false));
        trainInput = new EditText(this);
        trainInput.setSingleLine(true);
        trainInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        trainInput.setText(getSharedPreferences("ui", MODE_PRIVATE).getString("train", "406"));
        trainInput.setHint("Tren: 406, 401, P09…");
        trainInput.setTextColor(TEXT);
        trainInput.setHintTextColor(MUTED);
        screen.addView(trainInput);
        screen.addView(label("VIA DE DESTÍ · per a la columna SORTIDA", 12, MUTED, false));
        targetVia = new Spinner(this);
        targetVia.setAdapter(new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item, new String[] {"Via 1", "Via 2"}));
        int savedVia = getSharedPreferences("ui", MODE_PRIVATE).getInt("targetVia", 1);
        targetVia.setSelection(savedVia == 2 ? 1 : 0);
        screen.addView(targetVia);
        screen.addView(label("SORTIDA és la sortida prevista a l'estació escollida. "
            + "L'ull en mostra un compte enrere aproximat, no una arribada exacta.", 12, MUTED, false));
        LinearLayout eyeActions = new LinearLayout(this);
        eyeActions.setOrientation(LinearLayout.HORIZONTAL);
        Button startEye = new Button(this);
        startEye.setText("Activa l'ull");
        startEye.setOnClickListener(v -> startCorporateEye());
        eyeActions.addView(startEye, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button stopEye = new Button(this);
        stopEye.setText("Atura l'ull");
        stopEye.setOnClickListener(v -> {
            stopService(new Intent(this, CorporateEyeService.class));
            eyeStatus.setText("Seguiment aturat.");
        });
        eyeActions.addView(stopEye, new LinearLayout.LayoutParams(0, dp(52), 1));
        screen.addView(eyeActions);
        eyeStatus = label("L'ull segueix un tren amb dades exclusives del Miralín corporatiu.", 12, MUTED, false);
        screen.addView(eyeStatus);
        space(screen, 12);

        Button forget = new Button(this);
        forget.setText("Esborra les credencials desades");
        forget.setTextColor(MUTED);
        forget.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setTitle("Esborrar credencials?")
            .setMessage("S'eliminaran de l'emmagatzematge xifrat d'aquest telèfon.")
            .setNegativeButton("Cancel·la", null)
            .setPositiveButton("Esborra", (d, w) -> forgetCredentials())
            .show());
        screen.addView(forget);

        status = label("Introdueix les credencials per llegir el Miralín de TMB.", 14, MUTED, false);
        status.setPadding(dp(8), dp(12), dp(8), dp(8));
        screen.addView(status);

        updatedAt = label("", 12, MUTED, false);
        screen.addView(updatedAt);
        space(screen, 14);

        tables = new LinearLayout(this);
        tables.setOrientation(LinearLayout.VERTICAL);
        screen.addView(tables);
        renderTables();

        space(screen, 22);
        screen.addView(label("Dades mostrades tal com arriben de TMB, sense ajustar horaris. "
            + "Refresc automàtic cada 20 segons mentre la pantalla és visible. "
            + "Aplicació de prova independent, no oficial.", 12, MUTED, false));
    }

    private void connectWithCredentials() {
        String user = username.getText().toString().trim();
        String newPassword = password.getText().toString();
        CredentialVault.Login supplied;
        if (!newPassword.isEmpty()) {
            supplied = new CredentialVault.Login(user, newPassword);
        } else if (activeLogin != null && user.equals(activeLogin.username)) {
            supplied = activeLogin;
        } else {
            status.setText("Introdueix l'usuari i la contrasenya corporatius.");
            return;
        }
        if (user.isEmpty() || user.contains(":")) {
            status.setText("L'usuari no és vàlid per a l'accés HTTP Basic.");
            return;
        }
        fetchWith(supplied, !newPassword.isEmpty());
    }

    private void fetch(boolean persist) {
        if (activeLogin != null) fetchWith(activeLogin, persist);
    }

    private void fetchWith(CredentialVault.Login login, boolean saveOnSuccess) {
        if (busy) return;
        busy = true;
        int selectedStop = START_STOP + station.getSelectedItemPosition();
        if (selectedStop < START_STOP || selectedStop >= START_STOP + STOPS.length) {
            busy = false;
            return;
        }
        status.setText("Consultant el Miralín corporatiu…");
        io.execute(() -> {
            CorporateBoardParser.Board result = null;
            Exception problem = null;
            try {
                result = client.fetch(login.username, login.password, selectedStop);
                if (saveOnSuccess) vault.save(login.username, login.password);
            } catch (Exception ex) {
                problem = ex;
            }
            CorporateBoardParser.Board board = result;
            Exception error = problem;
            long capturedAt = System.currentTimeMillis();
            runOnUiThread(() -> {
                busy = false;
                if (isFinishing() || isDestroyed()) return;
                if (error != null) {
                    if (error instanceof CorporateMiralinClient.AccessDenied
                        || error instanceof CorporateMiralinClient.AuthenticationRedirect) {
                        running = false; // No retry loop on login redirects, 401 or 403.
                        handler.removeCallbacks(autoRefresh);
                        status.setText(error.getMessage() + " No es repetiran automàticament les consultes.");
                    } else {
                        status.setText("No s'ha pogut actualitzar: " + error.getMessage());
                    }
                    if (lastBoard != null) updatedAt.setText("Última lectura vàlida: "
                        + DateFormat.getTimeInstance(DateFormat.MEDIUM, new Locale("ca", "ES"))
                          .format(new Date(lastReadAt)) + " · dades anteriors");
                    return;
                }
                activeLogin = login;
                running = true;
                password.setText(""); // Never leave the password in the visible form.
                password.setHint("Contrasenya xifrada al dispositiu");
                if (selectedStop == START_STOP + station.getSelectedItemPosition()) {
                    lastStop = selectedStop;
                    lastBoard = board;
                    lastReadAt = capturedAt;
                    status.setText("Connectat directament al Miralín corporatiu de TMB.");
                    renderTables();
                }
                // The session has now been authorised: begin the visible-only polling loop.
                if (visible) {
                    handler.removeCallbacks(autoRefresh);
                    handler.postDelayed(autoRefresh, REFRESH_MS);
                }
            });
        });
    }

    private void forgetCredentials() {
        stopService(new Intent(this, CorporateEyeService.class));
        running = false;
        handler.removeCallbacks(autoRefresh);
        io.execute(() -> {
            try { vault.clear(); } catch (Exception ignored) {}
            runOnUiThread(() -> {
                activeLogin = null;
                username.setText("");
                password.setText("");
                password.setHint("Contrasenya corporativa");
                lastBoard = null;
                lastReadAt = 0;
                renderTables();
                status.setText("Credencials esborrades. Cal tornar a iniciar sessió.");
            });
        });
    }

    private void startCorporateEye() {
        if (activeLogin == null || !running) {
            eyeStatus.setText("Connecta amb el teu usuari corporatiu abans d'activar l'ull.");
            return;
        }
        String requested = trainInput.getText().toString().trim().toUpperCase(Locale.ROOT);
        if (!requested.matches("[A-Z0-9]{2,6}")) {
            eyeStatus.setText("Número de tren incorrecte. Escriu, per exemple, 406 o P09.");
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            eyeStatus.setText("Permet mostrar l'ull sobre altres aplicacions i torna a prémer Activa l'ull.");
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())));
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
            && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            eyeStatus.setText("Concedeix el permís de notificacions i torna a prémer Activa l'ull.");
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 101);
            return;
        }
        int chosenStop = START_STOP + station.getSelectedItemPosition();
        int chosenVia = targetVia.getSelectedItemPosition() + 1;
        getSharedPreferences("ui", MODE_PRIVATE).edit().putString("train", requested)
            .putInt("targetVia", chosenVia).apply();
        Intent intent = new Intent(this, CorporateEyeService.class);
        intent.putExtra(CorporateEyeService.EXTRA_TRAIN, requested);
        intent.putExtra(CorporateEyeService.EXTRA_STOP, chosenStop);
        intent.putExtra(CorporateEyeService.EXTRA_TARGET_VIA, chosenVia);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
            else startService(intent);
            eyeStatus.setText("Ull activat · tren " + requested + " · via destí " + chosenVia
                + " · SORTIDA TMB cada 20 s.");
        } catch (Exception e) {
            eyeStatus.setText("No s'ha pogut activar l'ull. Revisa els permisos del dispositiu.");
        }
    }

    private void renderTables() {
        if (tables == null) return;
        tables.removeAllViews();
        if (lastBoard == null) {
            updatedAt.setText("");
            tables.addView(label("Selecciona una estació i connecta per veure les taules.", 14, MUTED, false));
            return;
        }
        updatedAt.setText("Lectura: " + DateFormat.getTimeInstance(DateFormat.MEDIUM, new Locale("ca", "ES"))
            .format(new Date(lastReadAt)) + " · L4 · " + STOPS[lastStop - START_STOP]);
        renderTrack(lastBoard.via1);
        renderTrack(lastBoard.via2);
    }

    private void renderTrack(CorporateBoardParser.Track track) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(8), dp(10), dp(8), dp(10));
        section.setBackgroundColor(PANEL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(12);
        tables.addView(section, params);
        section.addView(label("VIA " + track.number, 20, ACCENT, true));
        space(section, 7);
        if (!track.present) {
            section.addView(label("Taula no disponible en aquesta pàgina.", 13, MUTED, false));
            return;
        }
        if (track.rows.isEmpty()) {
            section.addView(label("Cap tren a la taula.", 14, MUTED, false));
            return;
        }
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setFillViewport(false);
        TableLayout table = new TableLayout(this);
        int columns = track.headers.size();
        for (List<String> row : track.rows) columns = Math.max(columns, row.size());
        if (!track.headers.isEmpty()) addCells(table, track.headers, columns, true);
        for (List<String> row : track.rows) {
            TableRow entry = addCells(table, row, columns, false);
            if (!row.isEmpty() && row.get(0).matches("[A-Za-z0-9]{2,6}")) {
                entry.setOnClickListener(v -> {
                    trainInput.setText(row.get(0));
                    targetVia.setSelection(track.number == 2 ? 1 : 0);
                    eyeStatus.setText("Tren " + row.get(0) + " · via destí " + track.number
                        + " seleccionats. Prem Activa l'ull.");
                });
            }
        }
        horizontal.addView(table);
        section.addView(horizontal);
    }

    private TableRow addCells(TableLayout table, List<String> cells, int columns, boolean header) {
        TableRow row = new TableRow(this);
        row.setPadding(0, dp(3), 0, dp(3));
        if (header) row.setBackgroundColor(Color.rgb(32, 55, 76));
        for (int i = 0; i < columns; i++) {
            String value = i < cells.size() ? cells.get(i) : "";
            TextView cell = label(value, header ? 12 : 14, header ? ACCENT : TEXT, header);
            cell.setPadding(dp(8), dp(8), dp(8), dp(8));
            row.addView(cell, new TableRow.LayoutParams(dp(120), TableRow.LayoutParams.WRAP_CONTENT));
        }
        table.addView(row);
        return row;
    }

    private TextView label(String value, int sp, int color, boolean strong) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(color);
        text.setTextSize(sp);
        if (strong) text.setTypeface(null, android.graphics.Typeface.BOLD);
        return text;
    }

    private void space(LinearLayout parent, int heightDp) {
        View view = new View(this);
        parent.addView(view, new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override protected void onResume() {
        super.onResume();
        visible = true;
        handler.removeCallbacks(autoRefresh);
        handler.post(autoRefresh);
    }

    @Override protected void onPause() {
        visible = false;
        handler.removeCallbacks(autoRefresh);
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(autoRefresh);
        io.shutdownNow();
        super.onDestroy();
    }
}
