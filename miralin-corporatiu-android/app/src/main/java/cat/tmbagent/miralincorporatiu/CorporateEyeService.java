package cat.tmbagent.miralincorporatiu;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Independent, user-activated corporate TMB overlay.
 * No miralin.cat, Render or remote relay. No credentials in logs or intents.
 */
public final class CorporateEyeService extends Service {
    static final String EXTRA_TRAIN = "train";
    static final String EXTRA_STOP = "stop";
    static final String EXTRA_TARGET_VIA = "targetVia";
    static final String EXTRA_MODE = "mode";
    static final String EXTRA_SERVICE_DATE = "serviceDate";
    static final String EXTRA_SCHEDULE = "schedule";
    static volatile boolean isActive;

    private static final String CHANNEL = "corporate_eye_active";
    private static final int NOTIFICATION = 9404;
    private static final int REFRESH_MS = 10_000;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final CorporateMiralinClient client = new CorporateMiralinClient();

    private WindowManager windows;
    private View overlay;
    private TrainEyeView eye;
    private WindowManager.LayoutParams position;
    private String train = "406";
    private int stop = 413;
    private int targetVia = 1;
    private String mode = "manual";
    private CorporateServicePlan servicePlan;
    private int selectionGeneration;
    private long departureAt = -1;
    private CorporateTrainSelection.Train lastTrain;
    private long receivedAt;
    private String connection = "Connectant al Miralín corporatiu…";
    private boolean busy;
    private boolean stopped;
    private boolean blocked;
    private float touchX, touchY;
    private int originX, originY;
    private boolean dragging;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (stopped || blocked || busy) return;
            busy = true;
            final String requested = train;
            final int requestedStop = stop;
            final int requestedVia = targetVia;
            final int generation = selectionGeneration;
            network.execute(() -> {
                CorporateTrainSelection.Train found = null;
                CorporateTrainSelection.Train matchingDeparture = null;
                Exception failure = null;
                try {
                    CredentialVault.Login login = new CredentialVault(CorporateEyeService.this).load();
                    if (login == null) throw new SecurityException("Cal iniciar sessió a l'aplicació.");
                    CorporateBoardParser.Board board = client.fetch(login.username, login.password, requestedStop);
                    found = CorporateTrainSelection.find(board, requested);
                    matchingDeparture = requestedVia == 0
                        ? onlyDepartureAtTerminal(board, requested)
                        : CorporateTrainSelection.find(board, requested, requestedVia);
                } catch (Exception e) {
                    failure = e;
                }
                final CorporateTrainSelection.Train result = found;
                final CorporateTrainSelection.Train departure = matchingDeparture;
                final Exception error = failure;
                ui.post(() -> {
                    busy = false;
                    if (stopped) return;
                    if (generation != selectionGeneration) {
                        ui.removeCallbacks(poll);
                        ui.post(poll);
                        return;
                    }
                    if (error == null) {
                        lastTrain = result;
                        receivedAt = System.currentTimeMillis();
                        departureAt = departure == null ? -1
                            : OfficialDepartureCountdown.departureEpochMs(departure.departure, receivedAt);
                        connection = result == null ? "Tren no localitzat a la taula consultada"
                            : departure == null ? "No consta SORTIDA per aquesta via"
                            : "Lectura oficial rebuda";
                    } else {
                        connection = error instanceof CorporateMiralinClient.AccessDenied
                            || error instanceof CorporateMiralinClient.AuthenticationRedirect
                            || error instanceof SecurityException
                            ? "Accés no disponible · obre l'aplicació" : "Sense connexió · dades anteriors";
                        if (error instanceof CorporateMiralinClient.AccessDenied
                            || error instanceof CorporateMiralinClient.AuthenticationRedirect
                            || error instanceof SecurityException) blocked = true;
                    }
                    render();
                    if (!blocked) ui.postDelayed(poll, REFRESH_MS);
                });
            });
        }
    };

    private final Runnable ageTick = new Runnable() {
        @Override public void run() {
            if (stopped) return;
            if ("service".equals(mode)) applyServiceSelection(System.currentTimeMillis(), true);
            render();
            if (!stopped) ui.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(
            CHANNEL, "Ull Miralín corporatiu", NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null) {
            String requestedMode = intent.getStringExtra(EXTRA_MODE);
            if ("service".equals(requestedMode)) {
                String schedule = intent.getStringExtra(EXTRA_SCHEDULE);
                String date = intent.getStringExtra(EXTRA_SERVICE_DATE);
                try {
                    servicePlan = new CorporateServicePlan(date, schedule);
                    mode = "service";
                    if (!applyServiceSelection(System.currentTimeMillis(), false)) {
                        stopSelf();
                        return START_NOT_STICKY;
                    }
                } catch (IllegalArgumentException badSchedule) {
                    stopSelf();
                    return START_NOT_STICKY;
                }
            } else {
                mode = "manual";
                servicePlan = null;
                String candidate = intent.getStringExtra(EXTRA_TRAIN);
                if (candidate != null && CorporateServicePlan.validTrain(candidate)) {
                    train = candidate;
                }
                int requestedStop = intent.getIntExtra(EXTRA_STOP, stop);
                if (requestedStop >= 413 && requestedStop <= 434) stop = requestedStop;
                int requestedVia = intent.getIntExtra(EXTRA_TARGET_VIA, targetVia);
                if (requestedVia == 1 || requestedVia == 2 || (requestedVia == 0
                    && (stop == 413 || stop == 434))) targetVia = requestedVia;
                if (stop == 413 || stop == 434) targetVia = 0;
            }
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForegroundNotification();
        showOverlay();
        isActive = true;
        lastTrain = null;
        receivedAt = 0;
        departureAt = -1;
        blocked = false;
        selectionGeneration++;
        ui.removeCallbacks(poll);
        ui.removeCallbacks(ageTick);
        ui.post(poll);
        ui.post(ageTick);
        return START_NOT_STICKY;
    }

    private void startForegroundNotification() {
        Intent activity = new Intent(this, MainActivity.class);
        activity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, 0, activity,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle(("service".equals(mode) ? "El meu servei" : "Miralín corporatiu")
                + " · tren " + train)
            .setContentText("Seguiment en segon pla · font oficial TMB")
            .setContentIntent(pending)
            .setOngoing(true)
            .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION, notification);
        }
    }

    private void showOverlay() {
        if (overlay != null) {
            render();
            return;
        }
        FrameLayout bubble = new FrameLayout(this);
        eye = new TrainEyeView(this);
        bubble.addView(eye, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        eye.setOnClickListener(v -> {
            Intent open = "service".equals(mode)
                ? new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://roberfernandez.github.io/tmb-agent/proves/servei/?serviceViewer=1"))
                : new Intent(this, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            try { startActivity(open); } catch (Exception ignored) {}
        });
        eye.setOnTouchListener((view, event) -> {
            if (position == null || overlay == null) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    touchX = event.getRawX();
                    touchY = event.getRawY();
                    originX = position.x;
                    originY = position.y;
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = Math.round(event.getRawX() - touchX);
                    int dy = Math.round(event.getRawY() - touchY);
                    if (!dragging && (Math.abs(dx) > dp(9) || Math.abs(dy) > dp(9))) dragging = true;
                    if (dragging) {
                        setOverlayPosition(originX + dx, originY + dy);
                        try { windows.updateViewLayout(overlay, position); } catch (Exception ignored) {}
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragging) {
                        getSharedPreferences("eye", MODE_PRIVATE).edit()
                            .putInt("x", position.x).putInt("y", position.y).apply();
                    } else if (eye != null && eye.isCloseHit(event.getX(), event.getY())) {
                        stopSelf();
                    } else {
                        view.performClick();
                    }
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    return true;
                default:
                    return true;
            }
        });
        overlay = bubble;
        position = new WindowManager.LayoutParams(
            dp(TrainEyeView.WIDTH_DP), dp(TrainEyeView.HEIGHT_DP),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        position.gravity = Gravity.TOP | Gravity.LEFT;
        setOverlayPosition(
            getSharedPreferences("eye", MODE_PRIVATE).getInt("x", dp(12)),
            getSharedPreferences("eye", MODE_PRIVATE).getInt("y", dp(38)));
        try {
            windows.addView(overlay, position);
        } catch (Exception e) {
            overlay = null;
            eye = null;
            stopSelf();
            return;
        }
        render();
    }

    /**
     * A single physical platform at either terminal can appear in either table.
     * We use the only matching row; when both exist, keep the earlier published
     * departure rather than silently guessing a direction.
     */
    private static CorporateTrainSelection.Train onlyDepartureAtTerminal(
        CorporateBoardParser.Board board, String wantedTrain) {
        CorporateTrainSelection.Train first = CorporateTrainSelection.find(board, wantedTrain, 1);
        CorporateTrainSelection.Train second = CorporateTrainSelection.find(board, wantedTrain, 2);
        if (first == null) return second;
        if (second == null) return first;
        // When direction is ambiguous, never invent an arrival/departure.
        return null;
    }

    private boolean applyServiceSelection(long now, boolean refresh) {
        if (servicePlan == null || !"service".equals(mode)) return true;
        CorporateServicePlan.Selection selected = servicePlan.at(now);
        if (selected == null) {
            // A service is finished: do not continue monitoring its last train forever.
            stopSelf();
            return false;
        }
        boolean changed = !selected.train.equals(train) || selected.stop != stop || selected.via != targetVia;
        if (!changed) return true;
        train = selected.train;
        stop = selected.stop;
        targetVia = selected.via;
        selectionGeneration++;
        lastTrain = null;
        receivedAt = 0;
        departureAt = -1;
        connection = "Canvi d'activitat · consultant TMB…";
        render();
        if (refresh && !busy && !blocked) {
            ui.removeCallbacks(poll);
            ui.post(poll);
        }
        return true;
    }

    private void setOverlayPosition(int x, int y) {
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        position.x = Math.max(0, Math.min(metrics.widthPixels - position.width, x));
        position.y = Math.max(0, Math.min(metrics.heightPixels - position.height, y));
    }

    private void render() {
        if (eye == null) return;
        long ageSeconds = receivedAt == 0 ? -1 : Math.max(0, (System.currentTimeMillis() - receivedAt) / 1000);
        String age = ageSeconds < 0 ? "—" : ageSeconds < 60 ? ageSeconds + "s" : (ageSeconds / 60) + "m";
        String stationText = lastTrain == null ? (receivedAt == 0 ? "Connectant…" : "Sense dades") : lastTrain.station;
        String trackText = lastTrain == null ? "—" : lastTrain.track.replaceFirst("(?i)^via\\s*", "").trim();
        String headway = lastTrain == null ? "--" : formatHeadway(lastTrain.previousTrainGap);
        if (blocked) stationText = "Desconn.";
        eye.setStatus(train, stationText, trackText, age);
        eye.setHeadway(headway);
        // SORTIDA is the official station departure, not a guaranteed arrival.
        // The tilde in the countdown marks this published-minute approximation.
        eye.setCountdown(ageSeconds <= 60
            ? OfficialDepartureCountdown.display(departureAt, System.currentTimeMillis())
            : "--");
    }

    private static String formatHeadway(String original) {
        if (original == null || original.trim().isEmpty()) return "--";
        String value = original.trim();
        if (!value.matches("[0-9]{1,3}:[0-9]{2}")) return value;
        String[] parts = value.split(":");
        try {
            return Integer.parseInt(parts[0]) + "'" + parts[1] + "''";
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override public void onDestroy() {
        stopped = true;
        isActive = false;
        ui.removeCallbacks(poll);
        ui.removeCallbacks(ageTick);
        network.shutdownNow();
        if (overlay != null) {
            try { windows.removeView(overlay); } catch (Exception ignored) {}
            overlay = null;
            eye = null;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
