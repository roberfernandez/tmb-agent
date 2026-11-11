package com.tmbagent.traineye;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.webkit.HttpAuthHandler;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** User-controlled sign-in at TMB; no login is performed by our servers. */
public final class TmbLoginActivity extends Activity implements OfficialMiralinReader.AuthDelegate {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private OfficialMiralinReader reader;
    private TextView status;
    private AlertDialog authDialog;
    private final Runnable update = new Runnable() {
        @Override public void run() {
            if (reader == null || isFinishing()) return;
            if (TrainOverlayService.activeService() == null) { finish(); return; }
            status.setText(reader.status());
            ui.postDelayed(this, 1000);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TrainOverlayService service = TrainOverlayService.activeService();
        if (service == null) { finish(); return; }
        service.setOfficialScreenOpen(true);
        reader = service.officialReader();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(24), dp(12), dp(12));
        root.setBackgroundColor(0xff102033);
        TextView title = new TextView(this);
        title.setText("T.ANT del Miralín oficial de TMB");
        title.setTextColor(0xffffdf00);
        title.setTextSize(20);
        root.addView(title);
        TextView explanation = new TextView(this);
        explanation.setText("Inicia sessió a aplicacions.tmb.cat. La connexió es manté mentre el seguiment està actiu.");
        explanation.setTextColor(0xffffffff);
        explanation.setTextSize(14);
        explanation.setPadding(0, dp(8), 0, dp(8));
        root.addView(explanation);
        status = new TextView(this);
        status.setTextColor(0xffffffff);
        status.setPadding(0, 0, 0, dp(8));
        root.addView(status);

        WebView page = reader.attach(this, this);
        root.addView(page, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout actions = new LinearLayout(this);
        Button reload = new Button(this);
        reload.setText("Recarrega");
        reload.setOnClickListener(v -> reader.selectStop(service.officialStop(), true));
        actions.addView(reload, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button done = new Button(this);
        done.setText("Torna a l'Ull");
        done.setOnClickListener(v -> finish());
        actions.addView(done, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        root.addView(actions);
        Button disconnect = new Button(this);
        disconnect.setText("Desconnecta TMB");
        disconnect.setOnClickListener(v -> {
            TrainOverlayService active = TrainOverlayService.activeService();
            if (active != null) active.disconnectOfficial();
            finish();
        });
        root.addView(disconnect);
        setContentView(root);
        reader.selectStop(service.officialStop(), true);
        ui.post(update);
    }

    @Override public void request(HttpAuthHandler auth) {
        if (isFinishing()) { reader.cancelAuthentication(auth); return; }
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(24), 0, dp(24), 0);
        EditText username = new EditText(this);
        username.setHint("Usuari de TMB");
        username.setSingleLine(true);
        username.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        username.setAutofillHints(View.AUTOFILL_HINT_USERNAME);
        fields.addView(username);
        EditText password = new EditText(this);
        password.setHint("Contrasenya");
        password.setSingleLine(true);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setAutofillHints(View.AUTOFILL_HINT_PASSWORD);
        fields.addView(password);
        authDialog = new AlertDialog.Builder(this)
            .setTitle("Autorització de TMB")
            .setMessage("Accés HTTPS a aplicacions.tmb.cat. Les credencials no es desen a les preferències de l'app.")
            .setView(fields)
            .setPositiveButton("Accedeix", (dialog, which) -> {
                reader.authenticate(auth, username.getText().toString().trim(), password.getText().toString());
                password.setText("");
            })
            .setNegativeButton("Cancel·la", (dialog, which) -> {
                password.setText("");
                reader.cancelAuthentication(auth);
            })
            .setOnCancelListener(dialog -> reader.cancelAuthentication(auth))
            .create();
        if (authDialog.getWindow() != null)
            authDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        authDialog.show();
    }

    @Override protected void onDestroy() {
        ui.removeCallbacks(update);
        if (authDialog != null) { authDialog.dismiss(); authDialog = null; }
        if (reader != null) reader.detach(this);
        TrainOverlayService service = TrainOverlayService.activeService();
        if (service != null && !isChangingConfigurations()) service.setOfficialScreenOpen(false);
        super.onDestroy();
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
