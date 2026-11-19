package cat.tmbagent.miralincorporatiu;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Native helper for El meu servei; not a standalone train-management screen.
 * The PWA owns train, station, schedule, alerts and viewer controls.
 * This Activity only validates deep links, requests Android permissions and
 * displays a native, secure TMB sign-in dialog when initiated by the user.
 */
public final class ServiceBridgeActivity extends Activity {
    private static final int OVERLAY_PERMISSION = 202;
    private static final int NOTIFICATION_PERMISSION = 203;
    private static final String VIEWER = "https://roberfernandez.github.io/tmb-agent/proves/servei/";
    private final ExecutorService workers = Executors.newSingleThreadExecutor();
    private Intent pendingWatch;
    private AlertDialog loginDialog;
    private boolean verifying;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        process(getIntent());
    }

    private void process(Intent intent) {
        Uri data = intent == null ? null : intent.getData();
        if (data == null || !"miralincorp".equals(data.getScheme())) { finish(); return; }
        String host = data.getHost();
        if ("stop".equals(host)) {
            stopService(new Intent(this, CorporateEyeService.class));
            finish();
            return;
        }
        if ("status".equals(host)) {
            // User-requested fallback if Chrome cannot read Android loopback.
            // Return only the active mode, never a schedule or credentials.
            String nonce = data.getQueryParameter("nonce");
            if (nonce != null && nonce.matches("[a-zA-Z0-9-]{5,64}")) {
                String state = CorporateEyeService.nativeMode();
                Intent back = new Intent(Intent.ACTION_VIEW,
                    Uri.parse(VIEWER + "?nativeState=" + state + "&nonce=" + nonce));
                back.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { startActivity(back); } catch (Exception ignored) {}
            }
            finish();
            return;
        }
        if ("row".equals(host)) {
            // Changing the highlighted service row is not a request to stop the
            // eye. The running service verifies both the current plan and index.
            String key = data.getQueryParameter("plan");
            String row = data.getQueryParameter("row");
            if (CorporateEyeService.isActive && key != null
                && key.matches("[0-9a-f]{1,8}") && row != null
                && row.matches("[0-9]{1,3}")) {
                int index = Integer.parseInt(row);
                if (index < 140) {
                    Intent choice = new Intent(this, CorporateEyeService.class)
                        .setAction(CorporateEyeService.ACTION_SELECT_ROW)
                        .putExtra(CorporateEyeService.EXTRA_PICKUP_ROW, index)
                        .putExtra(CorporateEyeService.EXTRA_PLAN_KEY, key);
                    try { startService(choice); } catch (Exception ignored) {}
                }
            }
            finish();
            return;
        }
        if ("pickup".equals(host)) {
            // Explicit worker confirmation from the PWA. Only affect the
            // currently running service if the uploaded plan key matches.
            String key = data.getQueryParameter("plan");
            String row = data.getQueryParameter("row");
            if (CorporateEyeService.isActive && key != null
                && key.matches("[0-9a-f]{1,8}") && row != null
                && row.matches("[0-9]{1,3}")) {
                int index = Integer.parseInt(row);
                if (index < 140) {
                    Intent confirm = new Intent(this, CorporateEyeService.class)
                        .setAction(CorporateEyeService.ACTION_CONFIRM_PICKUP)
                        .putExtra(CorporateEyeService.EXTRA_PICKUP_ROW, index)
                        .putExtra(CorporateEyeService.EXTRA_PLAN_KEY, key);
                    try { startService(confirm); } catch (Exception ignored) {}
                }
            }
            finish();
            return;
        }
        if ("login".equals(host)) {
            promptLogin(false);
            return;
        }
        if ("alerts".equals(host)) {
            saveAlertOptions(data);
            if (CorporateEyeService.isActive) {
                Intent change = new Intent(this, CorporateEyeService.class).setAction("UPDATE_ALERTS");
                try {
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(change);
                    else startService(change);
                } catch (Exception ignored) {}
            }
            finish();
            return;
        }
        if (!"watch".equals(host)) { finish(); return; }

        try {
            saveAlertOptions(data);
            String mode = data.getQueryParameter("mode");
            Intent watch = new Intent(this, CorporateEyeService.class);
            if ("service".equals(mode)) {
                String date = data.getQueryParameter("date");
                String schedule = data.getQueryParameter("schedule");
                CorporateServicePlan plan = new CorporateServicePlan(date, schedule);
                if (new CorporateDutyTracker(plan).at(System.currentTimeMillis()) == null)
                    throw new IllegalArgumentException("Aquest servei ja no és vigent.");
                watch.putExtra(CorporateEyeService.EXTRA_MODE, "service");
                watch.putExtra(CorporateEyeService.EXTRA_SERVICE_DATE, date);
                watch.putExtra(CorporateEyeService.EXTRA_SCHEDULE, schedule);
            } else if ("manual".equals(mode)) {
                String train = data.getQueryParameter("train");
                String stop = data.getQueryParameter("stop");
                String via = data.getQueryParameter("via");
                if (!CorporateServicePlan.validTrain(train) || !CorporateServicePlan.validStop(stop))
                    throw new IllegalArgumentException("Tren o estació incorrectes.");
                int station = Integer.parseInt(stop);
                int platform = station == 413 || station == 434 ? 0
                    : CorporateServicePlan.validVia(via) ? Integer.parseInt(via) : -1;
                if (platform < 0) throw new IllegalArgumentException("Cal seleccionar la via.");
                watch.putExtra(CorporateEyeService.EXTRA_MODE, "manual");
                watch.putExtra(CorporateEyeService.EXTRA_TRAIN, train);
                watch.putExtra(CorporateEyeService.EXTRA_STOP, station);
                watch.putExtra(CorporateEyeService.EXTRA_TARGET_VIA, platform);
            } else {
                throw new IllegalArgumentException("Mode de seguiment desconegut.");
            }
            // The viewer is fully local: the original service image and row positions
            // are transferred to the native motor, never uploaded to any server.
            String image = data.getQueryParameter("image");
            String bounds = data.getQueryParameter("rowBounds");
            if (image != null && image.length() <= 220000 && image.matches("[A-Za-z0-9+/=]+"))
                watch.putExtra(CorporateEyeService.EXTRA_SERVICE_IMAGE, image);
            if (bounds != null && bounds.length() <= 12000)
                watch.putExtra(CorporateEyeService.EXTRA_SERVICE_ROW_BOUNDS, bounds);
            if ("manual".equals(mode)) {
                String viewerDate = data.getQueryParameter("viewerDate");
                String viewerSchedule = data.getQueryParameter("viewerSchedule");
                if (viewerDate != null && viewerSchedule != null && viewerSchedule.length() <= 16000) {
                    try {
                        new CorporateServicePlan(viewerDate, viewerSchedule);
                        watch.putExtra(CorporateEyeService.EXTRA_VIEWER_DATE, viewerDate);
                        watch.putExtra(CorporateEyeService.EXTRA_VIEWER_SCHEDULE, viewerSchedule);
                    } catch (Exception ignored) { /* Eye tracking still works without a service sheet. */ }
                }
            }
            pendingWatch = watch;
            continueWatch();
        } catch (Exception error) {
            showError("El meu servei", error.getMessage());
        }
    }

    private void saveAlertOptions(Uri data) {
        String enabled = data.getQueryParameter("alerts");
        if (enabled == null) enabled = data.getQueryParameter("enabled");
        String margin = data.getQueryParameter("margin");
        android.content.SharedPreferences.Editor preferences =
            getSharedPreferences("corporate-alerts-v1", MODE_PRIVATE).edit();
        if ("1".equals(enabled) || "0".equals(enabled))
            preferences.putBoolean("enabled", "1".equals(enabled));
        if (margin != null) {
            try {
                preferences.putInt("margin", Math.max(1, Math.min(120, Integer.parseInt(margin))));
            } catch (NumberFormatException ignored) {}
        }
        preferences.apply();
    }

    private void continueWatch() {
        if (pendingWatch == null) { finish(); return; }
        try {
            CredentialVault.Login login = new CredentialVault(this).load();
            if (login == null) {
                promptLogin(true);
                return;
            }
        } catch (Exception error) {
            promptLogin(true);
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                .setTitle("Permís per a l'ull flotant")
                .setMessage("Permet que El meu servei mostri l'ull per sobre d'altres aplicacions.")
                .setNegativeButton("Cancel·la", (dialog, which) -> finish())
                .setPositiveButton("Obrir", (dialog, which) -> {
                    startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())), OVERLAY_PERMISSION);
                }).setOnCancelListener(dialog -> finish()).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
            && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                NOTIFICATION_PERMISSION);
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(pendingWatch);
            else startService(pendingWatch);
            pendingWatch = null;
            finish();
        } catch (Exception error) {
            showError("No s'ha pogut activar l'ull", "Comprova els permisos d'Android.");
        }
    }

    private void promptLogin(boolean resumeWatchAfterLogin) {
        if (verifying || (loginDialog != null && loginDialog.isShowing())) return;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int px = (int) (22 * getResources().getDisplayMetrics().density);
        form.setPadding(px, px / 2, px, 0);
        TextView note = new TextView(this);
        note.setText("Accés corporatiu de TMB per a El meu servei. "
            + "Les credencials es validen directament amb TMB i es desen xifrades al mòbil. "
            + "No s'envien a la miniapp ni s'inclouen en cap enllaç.");
        form.addView(note);
        EditText user = new EditText(this);
        user.setSingleLine(true);
        user.setHint("Usuari TMB");
        user.setInputType(InputType.TYPE_CLASS_TEXT);
        form.addView(user);
        try {
            CredentialVault.Login previous = new CredentialVault(this).load();
            if (previous != null) user.setText(previous.username);
        } catch (Exception ignored) {}
        EditText password = new EditText(this);
        password.setSingleLine(true);
        password.setHint("Contrasenya");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(password);
        loginDialog = new AlertDialog.Builder(this)
            .setTitle("El meu servei · Identificació TMB")
            .setView(form)
            .setNegativeButton("Cancel·la", (dialog, which) -> finish())
            .setPositiveButton("Identifica'm", null)
            .setOnCancelListener(dialog -> finish())
            .create();
        loginDialog.setOnShowListener(ignored -> {
            loginDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String username = user.getText().toString().trim();
                String secret = password.getText().toString();
                if (username.isEmpty() || secret.isEmpty() || username.contains(":")) {
                    password.setError("Indica un usuari i contrasenya vàlids.");
                    return;
                }
                if (verifying) return;
                verifying = true;
                loginDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                note.setText("Verificant l'accés directament amb el Miralín corporatiu…");
                workers.execute(() -> {
                    Exception failure = null;
                    try {
                        new CorporateMiralinClient().fetch(username, secret, 413);
                        new CredentialVault(ServiceBridgeActivity.this).save(username, secret);
                    } catch (Exception error) {
                        failure = error;
                    }
                    Exception result = failure;
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        verifying = false;
                        if (result != null) {
                            note.setText("No s'ha pogut validar l'accés: " + result.getMessage());
                            loginDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                            return;
                        }
                        loginDialog.dismiss();
                        loginDialog = null;
                        if (resumeWatchAfterLogin) continueWatch();
                        else {
                            Toast.makeText(this, "Identificació TMB verificada per a El meu servei.",
                                Toast.LENGTH_LONG).show();
                            Intent back = new Intent(Intent.ACTION_VIEW,
                                Uri.parse(VIEWER + "?nativeReady=1"));
                            back.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            try { startActivity(back); } catch (Exception launchError) {}
                            finish();
                        }
                    });
                });
            });
        });
        loginDialog.show();
    }

    private void showError(String title, String message) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message == null ? "No s'ha pogut completar l'operació." : message)
            .setPositiveButton("Tanca", (dialog, which) -> finish())
            .setOnCancelListener(dialog -> finish()).show();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == OVERLAY_PERMISSION) {
            if (Settings.canDrawOverlays(this)) continueWatch();
            else showError("Permís pendent", "L'ull flotant necessita aquest permís.");
        }
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request == NOTIFICATION_PERMISSION) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) continueWatch();
            else showError("Permís pendent",
                "Per al seguiment en segon pla, cal permetre les notificacions.");
        }
    }

    @Override protected void onDestroy() {
        if (loginDialog != null) loginDialog.dismiss();
        workers.shutdownNow();
        super.onDestroy();
    }
}
