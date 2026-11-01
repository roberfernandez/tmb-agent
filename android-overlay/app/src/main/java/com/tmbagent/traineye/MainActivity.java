package com.tmbagent.traineye;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQUEST_OVERLAY = 101;
    private static final int REQUEST_NOTIFICATIONS = 102;
    private Spinner trainPicker;
    private TextView status;
    private String pendingTrain;
    private String pendingMode = "manual";
    private String pendingDate;
    private String pendingSchedule;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildScreen();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(24));
        root.setBackgroundColor(0xff080f1c);

        TextView title = text("Vigila el servei", 23, 0xfff4d000, true);
        root.addView(title, matchWrap());
        TextView info = text("Consulta el tren, l’estació, la via i l’antiguitat de les dades mentre uses altres aplicacions.", 15, 0xffdce5ef, false);
        LinearLayout.LayoutParams infoLp = matchWrap();
        infoLp.topMargin = dp(12);
        root.addView(info, infoLp);

        trainPicker = new Spinner(this);
        String[] trains = new String[15];
        for (int i = 0; i < trains.length; i++) trains[i] = String.valueOf(401 + i);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, trains);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        trainPicker.setAdapter(adapter);
        LinearLayout.LayoutParams pickerLp = matchWrap();
        pickerLp.topMargin = dp(24);
        root.addView(trainPicker, pickerLp);

        Button start = new Button(this);
        start.setText("Activa el seguiment");
        LinearLayout.LayoutParams startLp = matchWrap();
        startLp.topMargin = dp(18);
        root.addView(start, startLp);
        start.setOnClickListener(v -> startOverlayFor(String.valueOf(trainPicker.getSelectedItem())));

        Button stop = new Button(this);
        stop.setText("Desactiva el seguiment");
        LinearLayout.LayoutParams stopLp = matchWrap();
        stopLp.topMargin = dp(8);
        root.addView(stop, stopLp);
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, TrainOverlayService.class));
            status.setText("Seguiment aturat.");
        });

        status = text("Prova Android. L’app consulta Miralin.cat a través del servidor Render.", 14, 0xffa9b8cb, false);
        LinearLayout.LayoutParams statusLp = matchWrap();
        statusLp.topMargin = dp(20);
        root.addView(status, statusLp);
        setContentView(root);
    }

    private void handleIntent(Intent intent) {
        Uri requestedUri = intent == null ? null : intent.getData();
        boolean openViewer = intent != null && (
            intent.getBooleanExtra("open_service_viewer", false)
            || "com.tmbagent.traineye.OPEN_SERVICE_VIEWER".equals(intent.getAction())
            || (requestedUri != null && "open-viewer".equals(requestedUri.getHost()))
        );
        if (openViewer) {
            try {
                Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse("https://roberfernandez.github.io/tmb-agent/proves/servei/?serviceViewer=1"));
                startActivity(browser);
            } catch (Exception ignored) {
                if (status != null) status.setText("No s’ha pogut obrir el visor del servei.");
                Intent restore = new Intent(this, TrainOverlayService.class).setAction("SHOW_OVERLAY");
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(restore); else startService(restore);
            }
            finish();
            return;
        }
        Uri data = intent == null ? null : intent.getData();
        if (data == null || !"tmbservei".equals(data.getScheme())) return;
        if ("stop".equals(data.getHost())) {
            stopService(new Intent(this, TrainOverlayService.class));
            finish();
            return;
        }
        if ("viewer-close".equals(data.getHost())) {
            Intent restore = new Intent(this, TrainOverlayService.class).setAction("SHOW_OVERLAY");
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(restore); else startService(restore);
            finish();
            return;
        }
        String mode = data.getQueryParameter("mode");
        if ("service".equals(mode)) {
            String date = data.getQueryParameter("date");
            String schedule = data.getQueryParameter("schedule");
            if (schedule == null || schedule.isEmpty()) {
                status.setText("Carrega un servei amb trams de conducció abans d’activar el seguiment.");
                return;
            }
            StringBuilder validSchedule = new StringBuilder();
            String firstTrain = null;
            int validRows = 0;
            int validSegments = 0;
            for (String entry : schedule.split(";")) {
                String[] parts = entry.split(",", -1);
                try {
                    int start = Integer.parseInt(parts[0]);
                    int end = Integer.parseInt(parts[1]);
                    String type;
                    String from = "";
                    String to = "";
                    String candidate;
                    String via = "";
                    String out = "";
                    if (parts.length == 3) {
                        type = "CON";
                        candidate = parts[2];
                    } else if (parts.length == 8) {
                        type = parts[2];
                        from = parts[3];
                        to = parts[4];
                        candidate = parts[5];
                        via = parts[6];
                        out = parts[7];
                    } else continue;
                    if (start < 0 || end <= start || end > 2879 ||
                        !("TRA".equals(type) || "EST".equals(type) || "CON".equals(type))) continue;
                    if (parts.length == 8 &&
                        (!from.matches("4\\d{2}") || Integer.parseInt(from) < 413 || Integer.parseInt(from) > 434 ||
                         !to.matches("4\\d{2}") || Integer.parseInt(to) < 413 || Integer.parseInt(to) > 434)) continue;
                    if ("CON".equals(type)) {
                        if (!candidate.matches("4\\d{2}") ||
                            Integer.parseInt(candidate) < 401 || Integer.parseInt(candidate) > 415 ||
                            (parts.length == 8 && (!"1".equals(via) && !"2".equals(via) ||
                                (!"1".equals(out) && !"2".equals(out) && !"RET".equals(out))))) continue;
                        if (firstTrain == null) firstTrain = candidate;
                        validSegments++;
                    }
                    if (validSchedule.length() > 0) validSchedule.append(';');
                    validSchedule.append(start).append(',').append(end).append(',').append(type).append(',')
                        .append(from).append(',').append(to).append(',').append(candidate).append(',').append(via).append(',').append(out);
                    validRows++;
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {}
            }
            if (validSegments == 0 || firstTrain == null) {
                status.setText("L’horari ha arribat sense cap tram de conducció vàlid. Torna a interpretar el servei.");
                return;
            }
            pendingMode = "service";
            pendingDate = date == null ? "" : date;
            pendingSchedule = validSchedule.toString();
            pendingTrain = firstTrain;
            status.setText("Horari rebut: " + validRows + " línies i " + validSegments + " trams de conducció.");
            startPendingOverlay();
            return;
        }
        String train = data.getQueryParameter("train");
        if (train != null && train.matches("4\\d{2}") && Integer.parseInt(train) >= 401 && Integer.parseInt(train) <= 415) {
            selectTrain(train);
            pendingMode = "manual";
            pendingDate = null;
            pendingSchedule = null;
            startOverlayFor(train);
        }
    }

    private void selectTrain(String train) {
        if (trainPicker == null) return;
        int position = Integer.parseInt(train) - 401;
        if (position >= 0 && position < trainPicker.getCount()) trainPicker.setSelection(position);
    }

    private void startOverlayFor(String train) {
        pendingTrain = train;
        pendingMode = "manual";
        pendingDate = null;
        pendingSchedule = null;
        startPendingOverlay();
    }

    private void startPendingOverlay() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            return;
        }
        ensureOverlayPermission();
    }

    private void ensureOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            launchOverlayService();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Permetre l’ull superposat")
            .setMessage("Android ha d’autoritzar Miralin Tren Ull a mostrar el tren per sobre d’altres aplicacions. No llegeix ni controla les altres pantalles.")
            .setNegativeButton("Ara no", (dialog, which) -> status.setText("Permís de superposició pendent."))
            .setPositiveButton("Obrir permisos", (dialog, which) -> {
                Intent permission = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivityForResult(permission, REQUEST_OVERLAY);
            })
            .show();
    }

    private void launchOverlayService() {
        if (pendingTrain == null) return;
        Intent service = new Intent(this, TrainOverlayService.class)
            .putExtra(TrainOverlayService.EXTRA_TRAIN, pendingTrain)
            .putExtra(TrainOverlayService.EXTRA_MODE, pendingMode)
            .putExtra(TrainOverlayService.EXTRA_SERVICE_DATE, pendingDate)
            .putExtra(TrainOverlayService.EXTRA_SCHEDULE, pendingSchedule);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
        status.setText(pendingMode.equals("service") ? "Seguiment automàtic actiu. Horari rebut: " + pendingSchedule.split(";").length + " línies del servei." : "Ull actiu per al tren " + pendingTrain + ". Pots tornar a la PWA o obrir una altra aplicació.");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY) {
            if (Settings.canDrawOverlays(this)) launchOverlayService();
            else status.setText("Android no ha concedit el permís de superposició.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) ensureOverlayPermission();
            else status.setText("Cal permetre la notificació contínua perquè Android mantingui l’ull actiu.");
        }
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        view.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
