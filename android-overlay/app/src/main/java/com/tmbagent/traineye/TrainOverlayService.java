package com.tmbagent.traineye;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TrainOverlayService extends Service {
    public static final String EXTRA_TRAIN = "train";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_SERVICE_DATE = "serviceDate";
    public static final String EXTRA_SCHEDULE = "schedule";
    private static final String CHANNEL_ID = "train_eye_overlay";
    private static final int NOTIFICATION_ID = 9413;
    private static final String API = "https://tmb-servei-proves.onrender.com/api/trains?ids=";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private WindowManager windowManager;
    private View overlay;
    private TextView trainLine;
    private TextView statusLine;
    private WindowManager.LayoutParams overlayParams;
    private float downRawX, downRawY;
    private int downX, downY;
    private boolean dragging;
    private String train = "401";
    private String mode = "manual";
    private String serviceDate = "";
    private String schedule = "";
    private String station = "connectant…";
    private String track = "—";
    private int ageAtFetch = -1;
    private long fetchedAt = 0;
    private boolean expanded = true;
    private boolean requestInFlight = false;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            applyScheduledTrain(false);
            fetchTrain();
            handler.postDelayed(this, 15000);
        }
    };

    private final Runnable ageTicker = new Runnable() {
        @Override public void run() {
            applyScheduledTrain(true);
            updateLine();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        android.content.SharedPreferences prefs = getSharedPreferences("overlay", MODE_PRIVATE);
        if (intent != null) {
            train = intent.getStringExtra(EXTRA_TRAIN) == null ? train : intent.getStringExtra(EXTRA_TRAIN);
            mode = intent.getStringExtra(EXTRA_MODE) == null ? "manual" : intent.getStringExtra(EXTRA_MODE);
            serviceDate = intent.getStringExtra(EXTRA_SERVICE_DATE) == null ? "" : intent.getStringExtra(EXTRA_SERVICE_DATE);
            schedule = intent.getStringExtra(EXTRA_SCHEDULE) == null ? "" : intent.getStringExtra(EXTRA_SCHEDULE);
            prefs.edit().putString(EXTRA_TRAIN, train).putString(EXTRA_MODE, mode)
                .putString(EXTRA_SERVICE_DATE, serviceDate).putString(EXTRA_SCHEDULE, schedule).apply();
        } else {
            train = prefs.getString(EXTRA_TRAIN, "401");
            mode = prefs.getString(EXTRA_MODE, "manual");
            serviceDate = prefs.getString(EXTRA_SERVICE_DATE, "");
            schedule = prefs.getString(EXTRA_SCHEDULE, "");
        }
        applyScheduledTrain(false);
        startAsForeground();
        if (!android.provider.Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        showOverlay();
        handler.removeCallbacks(poll);
        handler.post(poll);
        handler.removeCallbacks(ageTicker);
        handler.post(ageTicker);
        return START_STICKY;
    }

    private void startAsForeground() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, TrainOverlayService.class).setAction("STOP");
        PendingIntent stopAction = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Ull superposat · tren " + train)
            .setContentText("Toca «Aturar» per tancar el seguiment.")
            .setContentIntent(content)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Aturar", stopAction)
            .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Ull superposat del tren", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Manté actiu l’ull flotant del tren seleccionat.");
            channel.setSound(null, null);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void showOverlay() {
        if (overlay != null) return;
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(5), dp(4), dp(5), dp(4));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xff111e30);
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), 0xfff4d000);
        bar.setBackground(background);

        TrainEyeIcon eye = new TrainEyeIcon(this);
        bar.addView(eye, new LinearLayout.LayoutParams(dp(32), dp(32)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(6), 0, dp(2), 0);
        details.setMinimumWidth(dp(175));
        trainLine = new TextView(this);
        trainLine.setTextColor(0xfff4d000);
        trainLine.setTextSize(12);
        trainLine.setTypeface(null, android.graphics.Typeface.BOLD);
        trainLine.setSingleLine(true);
        trainLine.setEllipsize(android.text.TextUtils.TruncateAt.END);
        trainLine.setMaxWidth(dp(220));
        details.addView(trainLine, new LinearLayout.LayoutParams(ViewGroupParams.WRAP, ViewGroupParams.WRAP));

        LinearLayout secondLine = new LinearLayout(this);
        secondLine.setGravity(Gravity.CENTER_VERTICAL);
        secondLine.setOrientation(LinearLayout.HORIZONTAL);
        statusLine = new TextView(this);
        statusLine.setTextColor(0xffdce5ef);
        statusLine.setTextSize(10);
        statusLine.setSingleLine(true);
        statusLine.setEllipsize(android.text.TextUtils.TruncateAt.END);
        statusLine.setMaxWidth(dp(155));
        secondLine.addView(statusLine, new LinearLayout.LayoutParams(0, ViewGroupParams.WRAP, 1f));

        TextView close = new TextView(this);
        close.setText("×");
        close.setTextSize(18);
        close.setTextColor(0xfff4d000);
        close.setGravity(Gravity.CENTER);
        close.setMinWidth(dp(28));
        close.setMinHeight(dp(24));
        secondLine.addView(close, new LinearLayout.LayoutParams(dp(30), dp(24)));
        close.setOnClickListener(v -> stopSelf());

        details.addView(secondLine, new LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, ViewGroupParams.WRAP));
        LinearLayout.LayoutParams detailsLp = new LinearLayout.LayoutParams(ViewGroupParams.WRAP, ViewGroupParams.WRAP);
        detailsLp.gravity = Gravity.CENTER_VERTICAL;
        bar.addView(details, detailsLp);

        View.OnTouchListener dragListener = (view, event) -> {
            if (overlayParams == null || overlay == null) return false;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    downX = overlayParams.x;
                    downY = overlayParams.y;
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float deltaX = event.getRawX() - downRawX;
                    float deltaY = event.getRawY() - downRawY;
                    if (!dragging && (Math.abs(deltaX) > dp(5) || Math.abs(deltaY) > dp(5))) dragging = true;
                    if (dragging) {
                        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
                        overlayParams.x = Math.max(0, Math.min(metrics.widthPixels - overlay.getWidth(), downX + Math.round(deltaX)));
                        overlayParams.y = Math.max(0, Math.min(metrics.heightPixels - overlay.getHeight(), downY + Math.round(deltaY)));
                        try { windowManager.updateViewLayout(overlay, overlayParams); } catch (Exception ignored) {}
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging) {
                        getSharedPreferences("overlay", MODE_PRIVATE).edit()
                            .putInt("overlay_x", overlayParams.x)
                            .putInt("overlay_y", overlayParams.y)
                            .apply();
                    }
                    dragging = false;
                    return true;
                default:
                    return true;
            }
        };
        bar.setOnTouchListener(dragListener);
        eye.setOnTouchListener(dragListener);
        details.setOnTouchListener(dragListener);
        trainLine.setOnTouchListener(dragListener);
        statusLine.setOnTouchListener(dragListener);
        overlay = bar;

        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        overlayParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
        overlayParams.gravity = Gravity.TOP | Gravity.START;
        overlayParams.x = getSharedPreferences("overlay", MODE_PRIVATE).getInt("overlay_x", dp(12));
        overlayParams.y = getSharedPreferences("overlay", MODE_PRIVATE).getInt("overlay_y", dp(38));
        try {
            windowManager.addView(overlay, overlayParams);
            updateLine();
        } catch (Exception ignored) {
            stopSelf();
        }
    }

    private void fetchTrain() {
        if (requestInFlight) return;
        requestInFlight = true;
        final String requestedTrain = train;
        network.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(API + requestedTrain).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestMethod("GET");
                int code = connection.getResponseCode();
                InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
                StringBuilder raw = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                    String line;
                    while ((line = reader.readLine()) != null) raw.append(line);
                }
                JSONObject response = new JSONObject(raw.toString());
                JSONObject item = response.optJSONObject("trains") == null ? null : response.optJSONObject("trains").optJSONObject(requestedTrain);
                String newStation = item == null ? "sense dades" : item.optString("station", "sense dades");
                String newTrack = item == null ? "—" : item.optString("platformTrack", item.optString("track", "—"));
                int newAge = response.isNull("ageSeconds") ? -1 : response.optInt("ageSeconds", -1);
                synchronized (this) {
                    if (train.equals(requestedTrain)) {
                        station = newStation;
                        track = newTrack;
                        ageAtFetch = newAge;
                        fetchedAt = System.currentTimeMillis();
                    }
                }
            } catch (Exception ignored) {
                synchronized (this) {
                    if (fetchedAt == 0) station = "sense connexió";
                }
            } finally {
                if (connection != null) connection.disconnect();
                requestInFlight = false;
                handler.post(this::updateLine);
            }
        });
    }

    private void applyScheduledTrain(boolean refreshNow) {
        if (!"service".equals(mode) || schedule == null || schedule.isEmpty()) return;
        String selected = null;
        int now = serviceMinutesNow();
        int bestUpcoming = Integer.MAX_VALUE;
        String latestTrain = null;
        int latestEnd = -1;
        for (String entry : schedule.split(";")) {
            String[] parts = entry.split(",");
            if (parts.length != 3) continue;
            try {
                int start = Integer.parseInt(parts[0]);
                int end = Integer.parseInt(parts[1]);
                String candidate = parts[2];
                if (!candidate.matches("4\\d{2}")) continue;
                if (now >= start && now < end) { selected = candidate; break; }
                if (start > now && start < bestUpcoming) { bestUpcoming = start; selected = candidate; }
                if (end > latestEnd) { latestEnd = end; latestTrain = candidate; }
            } catch (NumberFormatException ignored) {}
        }
        if (selected == null) selected = latestTrain;
        if (selected != null && !selected.equals(train)) {
            train = selected;
            station = "connectant…";
            track = "—";
            ageAtFetch = -1;
            fetchedAt = 0;
            getSharedPreferences("overlay", MODE_PRIVATE).edit().putString(EXTRA_TRAIN, train).apply();
            startAsForeground();
            if (refreshNow) fetchTrain();
        }
    }

    private int serviceMinutesNow() {
        try {
            java.time.LocalDate date = java.time.LocalDate.parse(serviceDate);
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            long minutes = java.time.Duration.between(date.atStartOfDay(), now).toMinutes();
            return (int) Math.max(-1, Math.min(2879, minutes));
        } catch (Exception ignored) {
            java.util.Calendar c = java.util.Calendar.getInstance();
            return c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE);
        }
    }

    private void updateLine() {
        if (trainLine == null || statusLine == null) return;
        int age;
        String currentStation;
        String currentTrack;
        synchronized (this) {
            age = ageAtFetch < 0 ? -1 : ageAtFetch + (int) ((System.currentTimeMillis() - fetchedAt) / 1000);
            currentStation = station;
            currentTrack = track;
        }
        String ageText = age < 0 ? "—" : age < 60 ? age + "s" : (age / 60) + "m";
        trainLine.setText(train + " · " + compactStation(currentStation));
        statusLine.setText("v" + currentTrack + " · " + ageText);
    }

    private String compactStation(String value) {
        String normalized = value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("maresme")) return "Maresme";
        if (normalized.contains("ciutadella")) return "Ciutadella";
        if (normalized.contains("guinardó") || normalized.contains("guinardo")) return "Guinardó";
        if (normalized.contains("passeig de gràcia") || normalized.contains("passeig de gracia")) return "Pg. Gràcia";
        return value == null ? "—" : value;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(poll);
        handler.removeCallbacks(ageTicker);
        if (overlay != null) {
            try { windowManager.removeView(overlay); } catch (Exception ignored) {}
            overlay = null;
        }
        if (network != null) network.shutdownNow();
        stopForeground(true);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static final class ViewGroupParams {
        static final int WRAP = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
    }

    private static final class TrainEyeIcon extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        TrainEyeIcon(android.content.Context context) { super(context); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float s = getResources().getDisplayMetrics().density;
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            paint.setColor(0xfff4d000); paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy, 15*s, paint);
            paint.setColor(0xff17202d);
            canvas.drawRoundRect(cx-8*s, cy-7*s, cx+8*s, cy+9*s, 3*s, 3*s, paint);
            paint.setColor(0xffdce5ef);
            canvas.drawRoundRect(cx-5*s, cy-4*s, cx+5*s, cy+1*s, 1.5f*s, 1.5f*s, paint);
            paint.setColor(0xff17202d);
            canvas.drawCircle(cx-4.5f*s, cy+10*s, 1.5f*s, paint);
            canvas.drawCircle(cx+4.5f*s, cy+10*s, 1.5f*s, paint);
            Path eye = new Path();
            eye.moveTo(cx-6*s, cy+4*s);
            eye.quadTo(cx, cy-2*s, cx+6*s, cy+4*s);
            eye.quadTo(cx, cy+10*s, cx-6*s, cy+4*s);
            paint.setColor(0xffffffff); paint.setStyle(Paint.Style.FILL); canvas.drawPath(eye, paint);
            paint.setColor(0xff17202d); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.2f*s); canvas.drawPath(eye, paint);
            paint.setStyle(Paint.Style.FILL); canvas.drawCircle(cx, cy+4*s, 2*s, paint);
        }
    }
}
