package com.tmbagent.traineye;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQUEST_OVERLAY = 101;
    private static final int REQUEST_NOTIFICATIONS = 102;
    private static final int REQUEST_BACKGROUND = 103;

    private String pendingTrain;
    private String pendingMode = "manual";
    private String pendingDate;
    private String pendingSchedule;
    private String pendingImage = "";
    private String pendingRowBounds = "";
    private String pendingManualStop = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        Uri data = intent == null ? null : intent.getData();
        if (intent != null && (
            intent.getBooleanExtra("open_service_viewer", false)
            || "com.tmbagent.traineye.OPEN_SERVICE_VIEWER".equals(intent.getAction())
            || (data != null && "open-viewer".equals(data.getHost()))
        )) {
            openServicePage();
            return;
        }
        if (data == null || !"tmbservei".equals(data.getScheme())) {
            finish();
            return;
        }
        if ("stop".equals(data.getHost())) {
            stopService(new Intent(this, TrainOverlayService.class));
            finish();
            return;
        }
        saveAlertSettings(data);
        if ("alerts".equals(data.getHost())) {
            if (TrainOverlayService.isActive) startService(new Intent(this, TrainOverlayService.class).setAction("CONFIGURE_ALERTS"));
            finishOrOfferBackground();
            return;
        }
        if ("viewer-close".equals(data.getHost())) {
            Intent restore = new Intent(this, TrainOverlayService.class).setAction("SHOW_OVERLAY");
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(restore); else startService(restore);
            finish();
            return;
        }

        String requestedMode = data.getQueryParameter("mode");
        if ("service".equals(requestedMode)) {
            startServiceTracking(data);
            return;
        }

        String train = data.getQueryParameter("train");
        if (ServicePlan.isTrain(train)) {
            pendingTrain = train;
            pendingMode = "manual";
            pendingDate = null;
            pendingSchedule = null;
            pendingImage = "";
            pendingRowBounds = "";
            pendingManualStop = "";
            chooseManualStation();
        } else {
            showMessageAndClose("No s’ha rebut un tren vàlid.");
        }
    }

    private void chooseManualStation() {
        new AlertDialog.Builder(this)
            .setTitle("On esperes el tren " + pendingTrain + "?")
            .setItems(ServiceAlerts.stations(), (dialog, which) -> {
                pendingManualStop = Integer.toString(ServiceAlerts.FIRST_STOP + which);
                startPendingOverlay();
            })
            .setNegativeButton("Només posició", (dialog, which) -> startPendingOverlay())
            .setOnCancelListener(dialog -> startPendingOverlay())
            .show();
    }

    private void startServiceTracking(Uri data) {
        String date = data.getQueryParameter("date");
        String schedule = data.getQueryParameter("schedule");
        String image = data.getQueryParameter("image");
        String rowBounds = data.getQueryParameter("rowBounds");
        if (schedule == null || schedule.isEmpty()) {
            showMessageAndClose("Carrega un servei amb trams de conducció abans d’activar el seguiment.");
            return;
        }

        StringBuilder validSchedule = new StringBuilder();
        String firstTrain = null;
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
                    if (!ServicePlan.isTrain(candidate) ||
                        (parts.length == 8 && (!"1".equals(via) && !"2".equals(via) ||
                            (!"1".equals(out) && !"2".equals(out) && !"RET".equals(out))))) continue;
                    if (firstTrain == null) firstTrain = candidate;
                    validSegments++;
                }
                if (validSchedule.length() > 0) validSchedule.append(';');
                validSchedule.append(start).append(',').append(end).append(',').append(type).append(',')
                    .append(from).append(',').append(to).append(',').append(candidate).append(',').append(via).append(',').append(out);
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {}
        }
        if (validSegments == 0 || firstTrain == null) {
            showMessageAndClose("L’horari no conté cap tram de conducció vàlid. Torna a interpretar el servei.");
            return;
        }
        pendingMode = "service";
        pendingDate = date == null ? "" : date;
        pendingSchedule = validSchedule.toString();
        pendingImage = image != null && image.length() <= 220000 ? image : "";
        pendingRowBounds = rowBounds != null && rowBounds.length() <= 12000 ? rowBounds : "";
        pendingManualStop = "";
        pendingTrain = trainForCurrentTime(pendingSchedule, firstTrain);
        startPendingOverlay();
    }

    private String trainForCurrentTime(String schedule, String fallback) {
        return new ServicePlan(schedule).trainAt(System.currentTimeMillis(), fallback);
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
            .setNegativeButton("Ara no", (dialog, which) -> showMessageAndClose("Permís de superposició pendent."))
            .setPositiveButton("Obrir permisos", (dialog, which) -> {
                Intent permission = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivityForResult(permission, REQUEST_OVERLAY);
            })
            .setOnCancelListener(dialog -> finish())
            .show();
    }

    private void launchOverlayService() {
        if (pendingTrain == null) {
            finish();
            return;
        }
        Intent service = new Intent(this, TrainOverlayService.class)
            .putExtra(TrainOverlayService.EXTRA_TRAIN, pendingTrain)
            .putExtra(TrainOverlayService.EXTRA_MODE, pendingMode)
            .putExtra(TrainOverlayService.EXTRA_SERVICE_DATE, pendingDate)
            .putExtra(TrainOverlayService.EXTRA_SCHEDULE, pendingSchedule)
            .putExtra(TrainOverlayService.EXTRA_SERVICE_IMAGE, pendingImage)
            .putExtra(TrainOverlayService.EXTRA_SERVICE_ROW_BOUNDS, pendingRowBounds)
            .putExtra(TrainOverlayService.EXTRA_MANUAL_STOP, pendingManualStop);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
        finishOrOfferBackground();
    }

    private void saveAlertSettings(Uri data) {
        android.content.SharedPreferences.Editor prefs = getSharedPreferences("overlay", MODE_PRIVATE).edit();
        String enabled = data.getQueryParameter("alerts"), margin = data.getQueryParameter("margin");
        if (enabled != null) prefs.putBoolean(TrainOverlayService.EXTRA_ALERTS, "1".equals(enabled));
        try { if (margin != null) prefs.putInt(TrainOverlayService.EXTRA_MARGIN, Math.max(1, Math.min(120, Integer.parseInt(margin)))); } catch (NumberFormatException ignored) {}
        prefs.apply();
    }

    private void finishOrOfferBackground() {
        android.content.SharedPreferences prefs = getSharedPreferences("overlay", MODE_PRIVATE);
        PowerManager power = getSystemService(PowerManager.class);
        if (!prefs.getBoolean(TrainOverlayService.EXTRA_ALERTS, false) || power.isIgnoringBatteryOptimizations(getPackageName()) || prefs.getBoolean("backgroundAsked", false)) { finish(); return; }
        prefs.edit().putBoolean("backgroundAsked", true).apply();
        new AlertDialog.Builder(this).setTitle("Avisos amb la pantalla apagada")
            .setMessage("Per rebre les arribades en directe amb el mòbil bloquejat, permet que Vigila el servei continuï en segon pla mentre el seguiment està actiu. Pot consumir més bateria.")
            .setNegativeButton("Ara no", (dialog, which) -> finish())
            .setPositiveButton("Permet-ho", (dialog, which) -> {
                try { startActivityForResult(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())), REQUEST_BACKGROUND); }
                catch (Exception ignored) { finish(); }
            }).setOnCancelListener(dialog -> finish()).show();
    }

    private void openServicePage() {
        try {
            Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse("https://roberfernandez.github.io/tmb-agent/proves/servei/?serviceViewer=1"));
            startActivity(browser);
        } catch (Exception ignored) {}
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_BACKGROUND) { finish(); return; }
        if (requestCode == REQUEST_OVERLAY) {
            if (Settings.canDrawOverlays(this)) launchOverlayService();
            else showMessageAndClose("Android no ha concedit el permís de superposició.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) ensureOverlayPermission();
            else showMessageAndClose("Cal permetre la notificació contínua perquè Android mantingui el seguiment actiu.");
        }
    }

    private void showMessageAndClose(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }
}

