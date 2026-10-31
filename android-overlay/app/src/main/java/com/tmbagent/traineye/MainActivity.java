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

        TextView title = text("Miralin · ull del tren", 23, 0xfff4d000, true);
        root.addView(title, matchWrap());
        TextView info = text("Mantén visibles el tren, l’estació, la via i l’antiguitat de la informació sobre altres aplicacions.", 15, 0xffdce5ef, false);
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
        start.setText("Activar ojo superpuesto");
        LinearLayout.LayoutParams startLp = matchWrap();
        startLp.topMargin = dp(18);
        root.addView(start, startLp);
        start.setOnClickListener(v -> startOverlayFor(String.valueOf(trainPicker.getSelectedItem())));

        Button stop = new Button(this);
        stop.setText("Desactivar ojo");
        LinearLayout.LayoutParams stopLp = matchWrap();
        stopLp.topMargin = dp(8);
        root.addView(stop, stopLp);
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, TrainOverlayService.class));
            status.setText("Seguimiento superpuesto detenido.");
        });

        status = text("Prueba Android. La app consulta Miralin.cat mediante el servidor Render.", 14, 0xffa9b8cb, false);
        LinearLayout.LayoutParams statusLp = matchWrap();
        statusLp.topMargin = dp(20);
        root.addView(status, statusLp);
        setContentView(root);
    }

    private void handleIntent(Intent intent) {
        Uri data = intent == null ? null : intent.getData();
        if (data == null || !"tmbservei".equals(data.getScheme())) return;
        String train = data.getQueryParameter("train");
        if (train != null && train.matches("4\\d{2}") && Integer.parseInt(train) >= 401 && Integer.parseInt(train) <= 415) {
            selectTrain(train);
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
        Intent service = new Intent(this, TrainOverlayService.class).putExtra(TrainOverlayService.EXTRA_TRAIN, pendingTrain);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
        status.setText("Ull actiu per al tren " + pendingTrain + ". Pots tornar a la PWA o obrir una altra aplicació.");
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
