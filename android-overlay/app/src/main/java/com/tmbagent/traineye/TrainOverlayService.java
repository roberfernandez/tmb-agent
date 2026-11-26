package com.tmbagent.traineye;

import android.app.ActivityOptions;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TrainOverlayService extends Service {
    static volatile boolean isActive;
    public static final String EXTRA_ALERTS = "alerts";
    public static final String EXTRA_MARGIN = "margin";
    public static final String EXTRA_TRAIN = "train";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_SERVICE_DATE = "serviceDate";
    public static final String EXTRA_SCHEDULE = "schedule";
    public static final String EXTRA_SERVICE_IMAGE = "serviceImage";
    public static final String EXTRA_SERVICE_ROW_BOUNDS = "serviceRowBounds";
    private static final String CHANNEL_ID = "train_eye_overlay";
    private static final int NOTIFICATION_ID = 9413;
    private static final String API = "https://tmb-servei-proves.onrender.com/api/trains?ids=";
    private static final String SERVICE_VIEWER_URL = "https://roberfernandez.github.io/tmb-agent/proves/servei/?serviceViewer=1";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private WindowManager windowManager;
    private View overlay;
    private TrainEyeView eye;
    private WindowManager.LayoutParams overlayParams;
    private View popupRoot;
    private ScrollView popupScroll;
    private ServiceImageView popupImageView;
    private TextView popupStatus;
    private final ArrayList<View> popupRowViews = new ArrayList<>();
    private int popupActiveIndex = -1;
    private float downRawX, downRawY;
    private int downX, downY;
    private boolean dragging;
    private String train = "401";
    private String mode = "manual";
    private String serviceDate = "";
    private String schedule = "";
    private String serviceImage = "";
    private String serviceRowBounds = "";
    private String station = "connectant…";
    private String track = "—";
    private int ageAtFetch = -1;
    private long fetchedAt = 0;
    private volatile boolean requestInFlight = false;
    private MiralinDirectClient directClient;
    private JSONObject directMap;
    private long directMapAt;
    private String connectionStatus = "Miralin directe · connectant…";
    private boolean destroyed;
    private ServicePlan servicePlan = new ServicePlan("");
    private ServiceAlerts serviceAlerts;
    private String alertPlanKey = "";
    private boolean alertsEnabled;
    private int alertMargin = 5;
    private PowerManager.WakeLock alertWakeLock;
    private long wakeRenewAt;

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
            updateEye();
            updatePopupHighlight();
            tickServiceAlerts();
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
        if (intent != null && "SHOW_OVERLAY".equals(intent.getAction())) {
            showOverlay();
            return START_STICKY;
        }
        android.content.SharedPreferences prefs = getSharedPreferences("overlay", MODE_PRIVATE);
        if (intent != null && "CONFIGURE_ALERTS".equals(intent.getAction()) && isActive) {
            configureAlerts();
            return START_STICKY;
        }
        if (intent != null) {
            if (popupRoot != null) closeServicePopup();
            train = intent.getStringExtra(EXTRA_TRAIN) == null ? train : intent.getStringExtra(EXTRA_TRAIN);
            mode = intent.getStringExtra(EXTRA_MODE) == null ? "manual" : intent.getStringExtra(EXTRA_MODE);
            serviceDate = intent.getStringExtra(EXTRA_SERVICE_DATE) == null ? "" : intent.getStringExtra(EXTRA_SERVICE_DATE);
            schedule = intent.getStringExtra(EXTRA_SCHEDULE) == null ? "" : intent.getStringExtra(EXTRA_SCHEDULE);
            serviceImage = intent.getStringExtra(EXTRA_SERVICE_IMAGE) == null ? "" : intent.getStringExtra(EXTRA_SERVICE_IMAGE);
            serviceRowBounds = intent.getStringExtra(EXTRA_SERVICE_ROW_BOUNDS) == null ? "" : intent.getStringExtra(EXTRA_SERVICE_ROW_BOUNDS);
            prefs.edit().putString(EXTRA_TRAIN, train).putString(EXTRA_MODE, mode)
                .putString(EXTRA_SERVICE_DATE, serviceDate).putString(EXTRA_SCHEDULE, schedule)
                .putString(EXTRA_SERVICE_IMAGE, serviceImage).putString(EXTRA_SERVICE_ROW_BOUNDS, serviceRowBounds).apply();
        } else {
            train = prefs.getString(EXTRA_TRAIN, "401");
            mode = prefs.getString(EXTRA_MODE, "manual");
            serviceDate = prefs.getString(EXTRA_SERVICE_DATE, "");
            schedule = prefs.getString(EXTRA_SCHEDULE, "");
            serviceImage = prefs.getString(EXTRA_SERVICE_IMAGE, "");
            serviceRowBounds = prefs.getString(EXTRA_SERVICE_ROW_BOUNDS, "");
        }
        servicePlan = new ServicePlan(schedule);
        configureAlerts();
        applyScheduledTrain(false);
        startAsForeground();
        if (!android.provider.Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        showOverlay();
        isActive = true;
        startDirectConnection();
        handler.removeCallbacks(poll);
        handler.post(poll);
        handler.removeCallbacks(ageTicker);
        handler.post(ageTicker);
        return START_STICKY;
    }

    private void startAsForeground() {
        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(SERVICE_VIEWER_URL));
        PendingIntent content = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        boolean serviceTracking = "service".equals(mode);
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle((serviceTracking ? "Servei segons horari" : "Seguiment manual") + " · tren " + train)
            .setContentText(serviceTracking ? "Horari rebut: " + schedule.split(";").length + " línies del servei." : "Seguiment actiu en segon pla.")
            .setContentIntent(content)
            .setOngoing(true)
            .setSubText(connectionStatus)
            .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Seguiment del servei", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Manté actiu el seguiment del tren seleccionat.");
            channel.setSound(null, null);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
            NotificationChannel alerts = new NotificationChannel("service_alerts", "Avisos del servei", NotificationManager.IMPORTANCE_HIGH);
            alerts.setDescription("Trasllats, enllaços i preses amb la pantalla apagada.");
            getSystemService(NotificationManager.class).createNotificationChannel(alerts);
        }
    }

    private void showOverlay() {
        if (overlay != null) return;
        FrameLayout bubble = new FrameLayout(this);
        eye = new TrainEyeView(this);
        bubble.addView(eye, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        eye.setOnClickListener(view -> openServiceViewer());

        ImageButton close = new ImageButton(this);
        close.setImageResource(R.drawable.ic_eye_stop);
        close.setScaleType(ImageView.ScaleType.CENTER);
        close.setBackground(null);
        close.setPadding(0, 0, 0, 0);
        close.setContentDescription("Atura el seguiment");
        close.setOnClickListener(v -> stopSelf());
        bubble.addView(close, new FrameLayout.LayoutParams(
            dp(TrainEyeView.CLOSE_WIDTH_DP), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.RIGHT));

        eye.setOnTouchListener((view, event) -> {
            if (overlayParams == null || overlay == null) return false;
            switch (event.getActionMasked()) {
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
                    if (!dragging && (Math.abs(deltaX) > dp(9) || Math.abs(deltaY) > dp(9))) dragging = true;
                    if (dragging) {
                        setOverlayPosition(downX + Math.round(deltaX), downY + Math.round(deltaY));
                        try { windowManager.updateViewLayout(overlay, overlayParams); } catch (Exception ignored) {}
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragging) {
                        getSharedPreferences("overlay", MODE_PRIVATE).edit()
                            .putInt("overlay_x", overlayParams.x)
                            .putInt("overlay_y", overlayParams.y)
                            .apply();
                    }
                    boolean wasDragging = dragging;
                    dragging = false;
                    if (!wasDragging) view.performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    return true;
                default:
                    return true;
            }
        });
        overlay = bubble;

        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        overlayParams = new WindowManager.LayoutParams(
            dp(TrainEyeView.WIDTH_DP),
            dp(TrainEyeView.HEIGHT_DP),
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
        overlayParams.gravity = Gravity.TOP | Gravity.LEFT;
        android.content.SharedPreferences prefs = getSharedPreferences("overlay", MODE_PRIVATE);
        setOverlayPosition(prefs.getInt("overlay_x", dp(12)), prefs.getInt("overlay_y", dp(38)));
        try {
            windowManager.addView(overlay, overlayParams);
            updateEye();
        } catch (Exception ignored) {
            stopSelf();
        }
    }

    private void setOverlayPosition(int x, int y) {
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        overlayParams.x = Math.max(0, Math.min(metrics.widthPixels - overlayParams.width, x));
        overlayParams.y = Math.max(0, Math.min(metrics.heightPixels - overlayParams.height, y));
    }

    private void hideOverlay() {
        if (overlay != null) {
            try { windowManager.removeView(overlay); } catch (Exception ignored) {}
            overlay = null;
            eye = null;
        }
    }

    private void openServiceViewer() {
        showServicePopup();
    }

    private void showServicePopup() {
        if (popupRoot != null) return;
        hideOverlay();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xb8000000);
        root.setFocusableInTouchMode(true);
        root.setOnClickListener(v -> closeServicePopup());
        root.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.getAction() == android.view.KeyEvent.ACTION_UP) {
                closeServicePopup();
                return true;
            }
            return false;
        });

        FrameLayout card = new FrameLayout(this);
        card.setPadding(dp(5), dp(5), dp(5), dp(5));
        GradientDrawable cardBackground = new GradientDrawable();
        cardBackground.setColor(0xff111e30);
        cardBackground.setCornerRadius(dp(16));
        cardBackground.setStroke(dp(2), 0xfff4d000);
        card.setBackground(cardBackground);
        card.setOnClickListener(v -> {});
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        int cardHeight = Math.min(dp(307), metrics.heightPixels - dp(64));
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, cardHeight, Gravity.CENTER);
        cardParams.leftMargin = dp(12);
        cardParams.rightMargin = dp(12);
        root.addView(card, cardParams);

        popupScroll = new ScrollView(this);
        popupScroll.setFillViewport(false);
        popupScroll.setClipToPadding(false);
        popupRowViews.clear();
        popupImageView = createServiceImageView();
        if (popupImageView != null) {
            popupScroll.addView(popupImageView, new ScrollView.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            LinearLayout list = buildScheduleRows();
            popupScroll.addView(list, new ScrollView.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT);
        card.addView(popupScroll, scrollParams);

        ImageButton close = new ImageButton(this);
        close.setImageResource(R.drawable.ic_service_viewer_close);
        close.setScaleType(ImageView.ScaleType.CENTER);
        close.setContentDescription("Tanca el visor del servei");
        GradientDrawable closeBackground = new GradientDrawable();
        closeBackground.setColor(0xe6111e30);
        closeBackground.setCornerRadius(dp(20));
        closeBackground.setStroke(dp(1), 0xfff4d000);
        close.setBackground(closeBackground);
        close.setPadding(0, 0, 0, 0);
        close.setElevation(dp(4));
        close.setOnClickListener(v -> closeServicePopup());
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.TOP | Gravity.END);
        closeParams.topMargin = dp(6);
        closeParams.rightMargin = dp(6);
        card.addView(close, closeParams);

        popupRoot = root;
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams popupParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        popupParams.gravity = Gravity.TOP | Gravity.START;
        try {
            windowManager.addView(root, popupParams);
            root.requestFocus();
            updatePopupHighlight();
        } catch (Exception ignored) {
            popupRoot = null;
            popupImageView = null;
            showOverlay();
        }
    }

    private LinearLayout buildScheduleRows() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        String[] entries = schedule == null ? new String[0] : schedule.split(";");
        for (String entry : entries) {
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

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(10), dp(7), dp(10), dp(7));
                row.setBackground(popupRowBackground(popupRowViews.size() == currentScheduleRowIndex()));
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, ViewGroupParams.WRAP);
                rowParams.bottomMargin = dp(5);
                row.setLayoutParams(rowParams);

                TextView timing = popupText(formatServiceTime(start) + "–" + formatServiceTime(end) +
                    "  ·  " + serviceTypeLabel(type), 13, 0xfff4d000, true);
                row.addView(timing);
                String route = stationName(from);
                if (!to.isEmpty() && !to.equals(from)) route += "  →  " + stationName(to);
                TextView routeView = popupText(route, 13, 0xffeef3fa, false);
                LinearLayout.LayoutParams routeParams = new LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, ViewGroupParams.WRAP);
                routeParams.topMargin = dp(2);
                row.addView(routeView, routeParams);
                if ("CON".equals(type) && !candidate.isEmpty()) {
                    TextView details = popupText("Tren " + candidate + "  ·  via " + via + " → " + out,
                        11, 0xffa9b8cb, false);
                    LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT, ViewGroupParams.WRAP);
                    detailsParams.topMargin = dp(1);
                    row.addView(details, detailsParams);
                }
                list.addView(row);
                popupRowViews.add(row);
            } catch (Exception ignored) {}
        }
        if (popupRowViews.isEmpty()) {
            TextView empty = popupText("No s’ha rebut el recorregut complet del servei.", 13, 0xffdce5ef, false);
            list.addView(empty);
        }
        return list;
    }

    private ServiceImageView createServiceImageView() {
        if (serviceImage == null || serviceImage.isEmpty()) return null;
        try {
            byte[] bytes = Base64.decode(serviceImage, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) return null;
            ArrayList<RectF> bounds = new ArrayList<>();
            JSONArray rows = new JSONArray(serviceRowBounds == null || serviceRowBounds.isEmpty() ? "[]" : serviceRowBounds);
            for (int i = 0; i < rows.length(); i++) {
                JSONArray row = rows.optJSONArray(i);
                if (row == null || row.length() < 4) continue;
                float left = clamp(row.optDouble(0, 0));
                float top = clamp(row.optDouble(1, 0));
                float width = clamp(row.optDouble(2, 0));
                float height = clamp(row.optDouble(3, 0));
                bounds.add(new RectF(left, top, left + width, top + height));
            }
            return new ServiceImageView(bitmap, bounds);
        } catch (Exception ignored) {
            return null;
        }
    }

    private float clamp(double value) {
        return (float) Math.max(0, Math.min(1, value));
    }

    private void closeServicePopup() {
        if (popupRoot != null) {
            try { windowManager.removeView(popupRoot); } catch (Exception ignored) {}
            popupRoot = null;
            popupScroll = null;
            popupStatus = null;
            popupRowViews.clear();
            popupImageView = null;
            popupActiveIndex = -1;
            showOverlay();
        }
    }

    private void updatePopupHighlight() {
        if (popupRoot == null) return;
        int active = currentScheduleRowIndex();
        for (int i = 0; i < popupRowViews.size(); i++) {
            popupRowViews.get(i).setBackground(popupRowBackground(i == active));
        }
        int rowCount = popupImageView != null ? popupImageView.rowBounds.size() : popupRowViews.size();
        if (popupImageView != null) popupImageView.setActiveRow(active);

        if (active >= 0 && active < rowCount && active != popupActiveIndex) {
            if (popupImageView != null) {
                final int selected = active;
                popupImageView.post(() -> {
                    if (popupScroll == null || popupRoot == null || popupImageView == null ||
                        selected >= popupImageView.rowBounds.size()) return;
                    RectF bounds = popupImageView.rowBounds.get(selected);
                    int y = Math.round(bounds.centerY() * popupImageView.getHeight() - popupScroll.getHeight() / 2f);
                    popupScroll.smoothScrollTo(0, Math.max(0, Math.min(y, popupImageView.getHeight() - popupScroll.getHeight())));
                });
            } else {
                View activeRow = popupRowViews.get(active);
                activeRow.post(() -> {
                    if (popupScroll != null && popupRoot != null) popupScroll.smoothScrollTo(0, Math.max(0, activeRow.getTop() - dp(12)));
                });
            }
        }
        popupActiveIndex = active;
    }

    private int currentScheduleRowIndex() {
        if (schedule == null || schedule.isEmpty()) return -1;
        int now = serviceMinutesNow();
        String[] entries = schedule.split(";");
        for (int i = 0; i < entries.length; i++) {
            String[] parts = entries[i].split(",", -1);
            try {
                int start = Integer.parseInt(parts[0]);
                int end = Integer.parseInt(parts[1]);
                if (now >= start && now < end) return i;
            } catch (Exception ignored) {}
        }
        for (int i = 0; i < entries.length; i++) {
            String[] parts = entries[i].split(",", -1);
            try {
                if (now < Integer.parseInt(parts[0])) return i;
            } catch (Exception ignored) {}
        }
        return -1;
    }

    private GradientDrawable popupRowBackground(boolean active) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(active ? 0xff26354a : 0xff0c1625);
        background.setCornerRadius(dp(12));
        background.setStroke(dp(active ? 2 : 1), active ? 0xfff4d000 : 0xff28394e);
        return background;
    }

    private TextView popupText(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private String formatServiceTime(int minutes) {
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }

    private String serviceTypeLabel(String type) {
        if ("TRA".equals(type)) return "Trasllat";
        if ("EST".equals(type)) return "Estació";
        return "Conducció";
    }

    private String stationName(String code) {
        switch (code) {
            case "413": return "La Pau";
            case "414": return "Besòs";
            case "415": return "Besòs Mar";
            case "416": return "El Maresme · Fòrum";
            case "417": return "Selva de Mar";
            case "418": return "Poblenou";
            case "419": return "Llacuna";
            case "420": return "Bogatell";
            case "421": return "Ciutadella · Vila Olímpica";
            case "422": return "Barceloneta";
            case "423": return "Jaume I";
            case "424": return "Urquinaona";
            case "425": return "Passeig de Gràcia";
            case "426": return "Girona";
            case "427": return "Verdaguer";
            case "428": return "Joanic";
            case "429": return "Alfons X";
            case "430": return "Guinardó · Hospital de Sant Pau";
            case "431": return "Maragall";
            case "432": return "Llucmajor";
            case "433": return "Via Júlia";
            case "434": return "Trinitat Nova";
            default: return "Estació " + code;
        }
    }

    private void startDirectConnection() {
        if (directClient != null) return;
        directClient = new MiralinDirectClient(new MiralinDirectClient.Listener() {
            @Override public void onMap(JSONObject trains) {
                final long received = android.os.SystemClock.elapsedRealtime();
                final long receivedAt = System.currentTimeMillis();
                handler.post(() -> {
                    if (destroyed) return;
                    directMap = trains;
                    directMapAt = received;
                    if (serviceAlerts != null) serviceAlerts.acceptMap(trains, receivedAt);
                    applyDirectTrain();
                    updateEye();
                });
            }
            @Override public void onState(String status) {
                handler.post(() -> {
                    if (destroyed || status.equals(connectionStatus)) return;
                    connectionStatus = status;
                    if (serviceAlerts != null && !status.contains("dades rebudes")) serviceAlerts.clearLiveData();
                    startAsForeground();
                });
            }
            @Override public void onBoard(JSONObject board) {
                final long received = System.currentTimeMillis();
                handler.post(() -> { if (!destroyed && serviceAlerts != null) serviceAlerts.acceptBoard(board, received); });
            }
        });
        directClient.start();
        updateAlertStops();
    }

    private void configureAlerts() {
        android.content.SharedPreferences prefs = getSharedPreferences("overlay", MODE_PRIVATE);
        alertsEnabled = prefs.getBoolean(EXTRA_ALERTS, false);
        alertMargin = Math.max(1, Math.min(120, prefs.getInt(EXTRA_MARGIN, 5)));
        long now = System.currentTimeMillis();
        String key = servicePlan.anchorAt(now) + ":" + schedule;
        if ("service".equals(mode) && !schedule.isEmpty() && (!key.equals(alertPlanKey) || serviceAlerts == null)) {
            alertPlanKey = key;
            serviceAlerts = new ServiceAlerts(servicePlan, now);
            if (key.equals(prefs.getString("alertPlan", ""))) {
                serviceAlerts.sent.addAll(prefs.getStringSet("alertSent", java.util.Collections.emptySet()));
                serviceAlerts.restoreTakeState(prefs.getString("liveTakeState", "[]"));
            } else {
                prefs.edit().putString("alertPlan", key).putStringSet("alertSent", java.util.Collections.emptySet()).remove("liveTakeState").apply();
            }
        }
        if (!"service".equals(mode)) serviceAlerts = null;
        updateAlertStops();
        updateAlertWakeLock();
    }

    private void updateAlertStops() {
        if (directClient != null) directClient.setStops(alertsEnabled && serviceAlerts != null ? serviceAlerts.requiredStops(System.currentTimeMillis(), alertMargin) : java.util.Collections.emptySet());
    }

    private void updateAlertWakeLock() {
        if (!alertsEnabled || serviceAlerts == null || !serviceAlerts.hasPending(System.currentTimeMillis())) {
            if (alertWakeLock != null && alertWakeLock.isHeld()) alertWakeLock.release();
            wakeRenewAt = 0; return;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        if (alertWakeLock == null) {
            alertWakeLock = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tmbagent:service-alerts");
            alertWakeLock.setReferenceCounted(false);
        }
        if (!alertWakeLock.isHeld() || now >= wakeRenewAt) { alertWakeLock.acquire(10 * 60000L); wakeRenewAt = now + 5 * 60000L; }
    }

    private void tickServiceAlerts() {
        updateAlertStops(); updateAlertWakeLock();
        if (!alertsEnabled || serviceAlerts == null) return;
        List<ServiceAlerts.Alert> due = serviceAlerts.due(System.currentTimeMillis(), alertMargin);
        NotificationManager manager = getSystemService(NotificationManager.class);
        boolean scheduledSent = false;
        if (!due.isEmpty() && manager.areNotificationsEnabled()) {
            for (ServiceAlerts.Alert alert : due) {
                if (postServiceAlert(manager, alert) && alert.takeRow < 0) scheduledSent = true;
            }
        }
        String takeState = serviceAlerts.changedTakeState();
        if (scheduledSent || takeState != null) {
            android.content.SharedPreferences.Editor edit = getSharedPreferences("overlay", MODE_PRIVATE).edit();
            if (scheduledSent) edit.putStringSet("alertSent", new java.util.HashSet<>(serviceAlerts.sent));
            if (takeState != null) edit.putString("liveTakeState", takeState);
            edit.apply();
        }
    }

    private boolean postServiceAlert(NotificationManager manager, ServiceAlerts.Alert alert) {
        long timeout = Math.min(180000, alert.expiresAt - System.currentTimeMillis());
        if (timeout <= 0) return false;
        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(SERVICE_VIEWER_URL));
        PendingIntent content = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, "service_alerts")
            .setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle(alert.title).setContentText(alert.body)
            .setStyle(new Notification.BigTextStyle().bigText(alert.body)).setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(content).setAutoCancel(true).setTimeoutAfter(timeout).build();
        try {
            manager.notify("service-" + alert.key, 9414, notification);
            serviceAlerts.markSent(alert);
            return true;
        } catch (SecurityException ignored) { return false; }
    }

    // Called on the main thread. Reception age uses a monotonic clock.
    private boolean applyDirectTrain() {
        if (directMap == null) return false;
        long age = (android.os.SystemClock.elapsedRealtime() - directMapAt) / 1000;
        if (age >= 45) return false;
        JSONObject item = directMap.optJSONObject(train);
        synchronized (this) {
            station = item == null ? "sense dades" : item.optString("estacion", "sense dades");
            track = item == null ? "—" : item.optString("platformTrack", item.optString("track", "—"));
            ageAtFetch = item == null ? -1 : (int) age;
            fetchedAt = System.currentTimeMillis();
        }
        return true;
    }

    private void fetchTrain() {
        if (destroyed || applyDirectTrain()) { updateEye(); return; }
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
                handler.post(() -> {
                    if (!destroyed && train.equals(requestedTrain) && !applyDirectTrain() &&
                        (fetchedAt == 0 || (newAge >= 0 && newAge < 45))) {
                        station = newStation;
                        track = newTrack;
                        ageAtFetch = newAge;
                        fetchedAt = System.currentTimeMillis();
                    }
                });
            } catch (Exception ignored) {
                handler.post(() -> {
                    if (!destroyed && !applyDirectTrain() && fetchedAt == 0) station = "sense connexió";
                });
            } finally {
                if (connection != null) connection.disconnect();
                requestInFlight = false;
                handler.post(this::updateEye);
            }
        });
    }

    private void applyScheduledTrain(boolean refreshNow) {
        if (!"service".equals(mode) || schedule == null || schedule.isEmpty()) return;
        String selected = servicePlan.trainAt(System.currentTimeMillis(), null);
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
        return servicePlan.minutesAt(System.currentTimeMillis());
    }

    private void updateEye() {
        if (eye == null) return;
        int age;
        String currentStation;
        String currentTrack;
        synchronized (this) {
            age = ageAtFetch < 0 ? -1 : ageAtFetch + (int) ((System.currentTimeMillis() - fetchedAt) / 1000);
            currentStation = station;
            currentTrack = track;
        }
        String ageText = age < 0 ? "—" : age < 60 ? age + "s" : (age / 60) + "m";
        eye.setStatus(train, currentStation, currentTrack, ageText);
    }

    @Override
    public void onDestroy() {
        isActive = false;
        if (alertWakeLock != null && alertWakeLock.isHeld()) alertWakeLock.release();
        destroyed = true;
        if (directClient != null) directClient.close();
        handler.removeCallbacks(poll);
        handler.removeCallbacks(ageTicker);
        if (popupRoot != null) {
            try { windowManager.removeView(popupRoot); } catch (Exception ignored) {}
            popupRoot = null;
            popupImageView = null;
        }
        hideOverlay();
        if (network != null) network.shutdownNow();
        stopForeground(true);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static final class ViewGroupParams {
        static final int WRAP = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
    }

    private final class ServiceImageView extends View {
        private final Bitmap bitmap;
        private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint highlightFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint highlightStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ArrayList<RectF> rowBounds;
        private int activeRow = -1;

        ServiceImageView(Bitmap bitmap, ArrayList<RectF> rowBounds) {
            super(TrainOverlayService.this);
            this.bitmap = bitmap;
            this.rowBounds = rowBounds;
            highlightFill.setColor(0x55f4d000);
            highlightStroke.setColor(0xfff4d000);
            highlightStroke.setStyle(Paint.Style.STROKE);
            highlightStroke.setStrokeWidth(dp(3));
        }

        void setActiveRow(int index) {
            if (activeRow != index) {
                activeRow = index;
                invalidate();
            }
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            if (width <= 0) width = dp(280);
            int height = Math.max(1, Math.round(width * (float) bitmap.getHeight() / bitmap.getWidth()));
            setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            RectF destination = new RectF(0, 0, getWidth(), getHeight());
            canvas.drawBitmap(bitmap, null, destination, imagePaint);
            if (activeRow >= 0 && activeRow < rowBounds.size()) {
                RectF normalized = rowBounds.get(activeRow);
                RectF selected = new RectF(normalized.left * getWidth(), normalized.top * getHeight(),
                    normalized.right * getWidth(), normalized.bottom * getHeight());
                canvas.drawRect(selected, highlightFill);
                canvas.drawRect(selected, highlightStroke);
            }
        }
    }

}
