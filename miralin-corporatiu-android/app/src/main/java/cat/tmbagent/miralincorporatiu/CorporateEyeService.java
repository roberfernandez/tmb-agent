package cat.tmbagent.miralincorporatiu;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.SharedPreferences;
import android.net.Uri;
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
import java.util.HashSet;
import java.util.Set;
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
    static final String EXTRA_PREVIEW_MODE = "previewMode";
    static final String EXTRA_SCHEDULE = "schedule";
    static final String EXTRA_VIEWER_DATE = "viewerDate";
    static final String EXTRA_VIEWER_SCHEDULE = "viewerSchedule";
    static final String EXTRA_SERVICE_IMAGE = "serviceImage";
    static final String EXTRA_SERVICE_ROW_BOUNDS = "serviceRowBounds";
    static final String ACTION_CONFIRM_PICKUP = "CONFIRM_PICKUP";
    static final String ACTION_SELECT_ROW = "SELECT_SERVICE_ROW";
    static final String EXTRA_PICKUP_ROW = "pickupRow";
    static final String EXTRA_PLAN_KEY = "planKey";
    static volatile boolean isActive;
    private static volatile String activeMode = "off";
    private static volatile String activeDutyJson = "";

    private static final String CHANNEL = "corporate_eye_active";
    private static final String ALERTS_CHANNEL = "corporate_service_alerts";
    private static final String VIEWER = "https://roberfernandez.github.io/tmb-agent/proves/servei/?serviceViewer=1";
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
    private volatile String mode = "manual";
    private CorporateServicePlan servicePlan;
    private CorporateDutyTracker duty;
    private boolean previewOnly;
    private CorporateServicePlan viewerPlan;
    private CorporateServiceViewer viewer;
    private String serviceImage = "";
    private String serviceRowBounds = "";
    private int manualViewerRow = -1;
    private long takeoverAt = -1;
    private int selectionKind = CorporateServicePlan.Selection.IDLE;
    private int selectionGeneration;
    private boolean alertsEnabled;
    private int alertMargin = 5;
    private String alertPlanIdentity = "";
    private Set<String> sentAlerts = new HashSet<>();
    private int nextAlertId = 9600;
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
            if (stopped || blocked || busy
                || ("service".equals(mode) && selectionKind == CorporateServicePlan.Selection.IDLE)) return;
            busy = true;
            final String requested = train;
            final int requestedStop = stop;
            final int requestedVia = targetVia;
            final int requestedKind = selectionKind;
            final int requestedRow = duty == null ? -1 : duty.at(System.currentTimeMillis()) == null
                ? -1 : duty.at(System.currentTimeMillis()).selection.row;
            final int generation = selectionGeneration;
            network.execute(() -> {
                CorporateTrainSelection.Train found = null;
                CorporateTrainSelection.Train matchingDeparture = null;
                CorporateTrainSelection.Train transferCandidate = null;
                Exception failure = null;
                try {
                    CredentialVault.Login login = new CredentialVault(CorporateEyeService.this).load();
                    if (login == null) throw new SecurityException("Cal iniciar sessió a l'aplicació.");
                    CorporateBoardParser.Board board = client.fetch(login.username, login.password, requestedStop);
                    if ("service".equals(mode)
                        && requestedKind == CorporateServicePlan.Selection.TRANSFER_SEARCH) {
                        transferCandidate = CorporateTrainSelection.nextForTransfer(
                            board, requestedVia, System.currentTimeMillis());
                    } else {
                        found = requestedVia == 0
                            ? CorporateTrainSelection.find(board, requested)
                            : CorporateTrainSelection.find(board, requested, requestedVia);
                        matchingDeparture = requestedVia == 0
                            ? onlyDepartureAtTerminal(board, requested)
                            : CorporateTrainSelection.find(board, requested, requestedVia);
                    }
                } catch (Exception e) {
                    failure = e;
                }
                final CorporateTrainSelection.Train result = found;
                final CorporateTrainSelection.Train departure = matchingDeparture;
                final CorporateTrainSelection.Train candidate = transferCandidate;
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
                        receivedAt = System.currentTimeMillis();
                        if ("service".equals(mode) && duty != null
                            && requestedKind == CorporateServicePlan.Selection.TRANSFER_SEARCH) {
                            if (candidate != null && duty.setTransferTrain(requestedRow, candidate.id)) {
                                connection = "Trasllat · seguint tren " + candidate.id;
                                applyServiceSelection(receivedAt, true);
                            } else {
                                lastTrain = null;
                                departureAt = -1;
                                connection = "Cercant tren en la direcció del trasllat…";
                            }
                        } else {
                            lastTrain = result;
                            departureAt = departure == null ? -1
                                : OfficialDepartureCountdown.departureEpochMs(departure.departure, receivedAt);
                            connection = result == null ? "Tren no localitzat a la taula consultada"
                                : departure == null ? "No consta SORTIDA per aquesta via"
                                : "Lectura oficial rebuda";
                            if ("service".equals(mode) && duty != null) {
                                boolean transferArrived = requestedKind == CorporateServicePlan.Selection.TRANSFER_TRACK
                                    && result != null && duty.observeTransfer(receivedAt, requested,
                                        result.station, result.track);
                                CorporateDutyTracker.Snapshot live = duty.at(receivedAt);
                                boolean unambiguous = live != null && live.driving
                                    ? (live.selection != null
                                        && CorporateServicePlan.terminal(live.selection.stop)
                                        ? departure != null : result != null)
                                    : departure != null;
                                boolean transitioned = requestedKind != CorporateServicePlan.Selection.TRANSFER_TRACK
                                    && duty.observe(receivedAt, requested,
                                        result == null ? "" : result.station,
                                        result == null ? "" : result.track, unambiguous);
                                if (transferArrived || transitioned) applyServiceSelection(receivedAt, true);
                                publishDutyStatus(receivedAt);
                            }
                        }
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
                    if (!blocked && (!"service".equals(mode)
                        || selectionKind != CorporateServicePlan.Selection.IDLE))
                        ui.postDelayed(poll, REFRESH_MS);
                });
            });
        }
    };

    private final Runnable ageTick = new Runnable() {
        @Override public void run() {
            if (stopped) return;
            if ("service".equals(mode)) applyServiceSelection(System.currentTimeMillis(), true);
            publishDutyStatus(System.currentTimeMillis());
            tickServiceAlerts(System.currentTimeMillis());
            if (viewer != null) viewer.refresh(System.currentTimeMillis(),
                currentViewerRow(System.currentTimeMillis()));
            render();
            if (!stopped) ui.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(
            CHANNEL, "El meu servei · seguiment", NotificationManager.IMPORTANCE_LOW));
        manager.createNotificationChannel(new NotificationChannel(
            ALERTS_CHANNEL, "El meu servei · avisos", NotificationManager.IMPORTANCE_HIGH));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_SELECT_ROW.equals(intent.getAction())) {
            if (isActive && "service".equals(mode) && duty != null && servicePlan != null
                && servicePlan.webKey().equals(intent.getStringExtra(EXTRA_PLAN_KEY))) {
                applyUserRowSelection(System.currentTimeMillis(),
                    intent.getIntExtra(EXTRA_PICKUP_ROW, -1));
            }
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_CONFIRM_PICKUP.equals(intent.getAction())) {
            if (isActive && "service".equals(mode) && duty != null && servicePlan != null
                && servicePlan.webKey().equals(intent.getStringExtra(EXTRA_PLAN_KEY))) {
                applyUserRowSelection(System.currentTimeMillis(),
                    intent.getIntExtra(EXTRA_PICKUP_ROW, -1));
            }
            return START_NOT_STICKY;
        }
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && "UPDATE_ALERTS".equals(intent.getAction()) && isActive) {
            configureAlerts();
            return START_NOT_STICKY;
        }
        if (intent != null) {
            String requestedMode = intent.getStringExtra(EXTRA_MODE);
            if ("service".equals(requestedMode)) {
                String schedule = intent.getStringExtra(EXTRA_SCHEDULE);
                String date = intent.getStringExtra(EXTRA_SERVICE_DATE);
                try {
                    servicePlan = new CorporateServicePlan(date, schedule);
                    previewOnly = intent.getBooleanExtra(EXTRA_PREVIEW_MODE, false);
                    selectionKind = CorporateServicePlan.Selection.IDLE;
                    duty = new CorporateDutyTracker(servicePlan, previewOnly);
                    manualViewerRow = -1;
                    viewerPlan = servicePlan;
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
                selectionKind = CorporateServicePlan.Selection.DRIVING;
                servicePlan = null;
                duty = null;
                previewOnly = false;
                manualViewerRow = -1;
                activeDutyJson = "";
                takeoverAt = -1;
                viewerPlan = null;
                String viewerDate = intent.getStringExtra(EXTRA_VIEWER_DATE);
                String viewerSchedule = intent.getStringExtra(EXTRA_VIEWER_SCHEDULE);
                if (viewerDate != null && viewerSchedule != null) {
                    try { viewerPlan = new CorporateServicePlan(viewerDate, viewerSchedule); }
                    catch (IllegalArgumentException ignored) {}
                }
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
        if (intent != null) {
            serviceImage = intent.getStringExtra(EXTRA_SERVICE_IMAGE);
            serviceRowBounds = intent.getStringExtra(EXTRA_SERVICE_ROW_BOUNDS);
        }
        if (viewer != null) { viewer.close(); viewer = null; }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        configureAlerts();
        startForegroundNotification();
        showOverlay();
        isActive = true;
        activeMode = mode;
        publishDutyStatus(System.currentTimeMillis());
        lastTrain = null;
        receivedAt = 0;
        departureAt = -1;
        blocked = false;
        selectionGeneration++;
        ui.removeCallbacks(poll);
        ui.removeCallbacks(ageTick);
        if (selectionKind != CorporateServicePlan.Selection.IDLE) ui.post(poll);
        ui.post(ageTick);
        return START_NOT_STICKY;
    }

    private void startForegroundNotification() {
        PendingIntent pending = viewerPendingIntent();
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
        eye.setOnClickListener(v -> openServiceViewer());
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
    private void openServiceViewer() {
        if (viewer != null && viewer.isShowing()) return;
        viewer = new CorporateServiceViewer(this, windows, viewerPlan,
            serviceImage, serviceRowBounds, () -> viewer = null,
            row -> applyUserRowSelection(System.currentTimeMillis(), row));
        viewer.show(currentViewerRow(System.currentTimeMillis()));
    }

    private PendingIntent viewerPendingIntent() {
        Intent viewer = new Intent(Intent.ACTION_VIEW, Uri.parse(VIEWER));
        viewer.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(this, 110, viewer,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void configureAlerts() {
        SharedPreferences settings = getSharedPreferences("corporate-alerts-v1", MODE_PRIVATE);
        alertsEnabled = settings.getBoolean("enabled", false);
        alertMargin = Math.max(1, Math.min(120, settings.getInt("margin", 5)));
        String key = servicePlan == null ? "" : servicePlan.identity();
        if (!key.equals(alertPlanIdentity)) {
            alertPlanIdentity = key;
            String previous = settings.getString("planIdentity", "");
            if (key.equals(previous))
                sentAlerts = new HashSet<>(settings.getStringSet("sent", new HashSet<>()));
            else {
                sentAlerts.clear();
                settings.edit().putString("planIdentity", key).remove("sent").apply();
            }
        }
    }

    private void tickServiceAlerts(long now) {
        if (previewOnly || !alertsEnabled || servicePlan == null || !"service".equals(mode)) return;
        for (CorporateServicePlan.Notice notice : servicePlan.noticesAt(now, alertMargin)) {
            if (sentAlerts.contains(notice.key)) continue;
            String content = notice.body;
            // The separate live source is the published SORTIDA, not measured arrival.
            if (notice.key.startsWith("take-") && departureAt > now && lastTrain != null) {
                String countdown = OfficialDepartureCountdown.display(departureAt, now);
                if (!"--".equals(countdown))
                    content += " · SORTIDA TMB " + countdown;
            }
            if (postServiceNotification(notice.title, content)) {
                sentAlerts.add(notice.key);
                getSharedPreferences("corporate-alerts-v1", MODE_PRIVATE).edit()
                    .putStringSet("sent", new HashSet<>(sentAlerts)).apply();
            }
        }
    }

    private boolean postServiceNotification(String title, String body) {
        try {
            Notification notification = new Notification.Builder(this, ALERTS_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(viewerPendingIntent())
                .setAutoCancel(true)
                .build();
            getSystemService(NotificationManager.class).notify(nextAlertId++, notification);
            return true;
        } catch (Exception ignored) { return false; }
    }

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
        CorporateDutyTracker.Snapshot snapshot = duty == null ? null : duty.at(now);
        CorporateServicePlan.Selection selected = snapshot == null ? null : snapshot.selection;
        if (selected == null) {
            // A service is finished: do not continue monitoring its last train forever.
            stopSelf();
            return false;
        }
        long nextTakeover = selected.driving ? -1 : selected.takeoverAt;
        boolean changed = !selected.train.equals(train) || selected.stop != stop
            || selected.via != targetVia || nextTakeover != takeoverAt
            || selected.kind != selectionKind;
        takeoverAt = nextTakeover;
        publishDutyStatus(now);
        if (!changed) return true;
        train = selected.train;
        stop = selected.stop;
        targetVia = selected.via;
        selectionKind = selected.kind;
        selectionGeneration++;
        lastTrain = null;
        receivedAt = 0;
        departureAt = -1;
        connection = selected.kind == CorporateServicePlan.Selection.IDLE
            ? "Esperant el proper moviment del servei…"
            : selected.kind == CorporateServicePlan.Selection.TRANSFER_SEARCH
                ? "Cercant tren per al trasllat…"
                : "Canvi d'activitat · consultant TMB…";
        render();
        boolean needsBoard = selected.kind != CorporateServicePlan.Selection.IDLE
            && selected.kind != CorporateServicePlan.Selection.WAIT_TRANSFER;
        if (refresh && needsBoard && !busy && !blocked) {
            ui.removeCallbacks(poll);
            ui.post(poll);
        }
        return true;
    }

    private boolean applyUserRowSelection(long now, int row) {
        if ("service".equals(mode) && duty != null) {
            if (!duty.selectRow(now, row)) return false;
            previewOnly = duty.isPreviewOnly();
            applyServiceSelection(now, true);
            publishDutyStatus(now);
        } else if ("manual".equals(mode) && viewerPlan != null
            && row >= 0 && row < viewerPlan.viewerSegments().size()) {
            // The independent manual train watch keeps following its selected
            // train; the user can still mark their position on the sheet.
            manualViewerRow = row;
        } else return false;
        if (viewer != null) viewer.refresh(now, currentViewerRow(now));
        return true;
    }

    private int currentViewerRow(long now) {
        if ("manual".equals(mode)) return manualViewerRow;
        CorporateDutyTracker.Snapshot snapshot =
            "service".equals(mode) && duty != null ? duty.at(now) : null;
        return snapshot == null ? -1 : snapshot.photoRow;
    }

    private void publishDutyStatus(long now) {
        if (!"service".equals(mode) || servicePlan == null || duty == null) {
            activeDutyJson = "";
            return;
        }
        CorporateDutyTracker.Snapshot snapshot = duty.at(now);
        activeDutyJson = snapshot == null ? "" :
            ",\"plan\":\"" + servicePlan.webKey()
            + "\",\"row\":" + snapshot.photoRow
            + ",\"pickup\":" + snapshot.pickupRow
            + ",\"waiting\":" + snapshot.waiting
            + ",\"preview\":" + snapshot.preview
            + ",\"source\":\"" + snapshot.source + "\"";
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
        String stationText = lastTrain == null
            ? (selectionKind == CorporateServicePlan.Selection.IDLE
                ? CorporateServicePlan.stationName(Integer.toString(stop))
                : receivedAt == 0 ? "Connectant…" : "Sense dades")
            : lastTrain.station;
        String trackText = lastTrain == null ? "—" : lastTrain.track.replaceFirst("(?i)^via\\s*", "").trim();
        String headway = lastTrain == null ? "--" : formatHeadway(lastTrain.previousTrainGap);
        if (blocked) stationText = "Desconn.";
        eye.setStatus(train, stationText, trackText, age);
        eye.setHeadway(headway);
        // SORTIDA is the official station departure, not a guaranteed arrival.
        // The tilde in the countdown marks this published-minute approximation.
        long now = System.currentTimeMillis();
        CorporateProjection.Result projection = "service".equals(mode)
            ? CorporateProjection.project(departureAt, now, takeoverAt)
            : new CorporateProjection.Result(departureAt, 0);
        String countdown = selectionKind == CorporateServicePlan.Selection.IDLE
            || selectionKind == CorporateServicePlan.Selection.WAIT_TRANSFER
            ? OfficialDepartureCountdown.display(takeoverAt, now)
            : ageSeconds <= 60 ? OfficialDepartureCountdown.display(projection.timeMs, now) : "--";
        eye.setCountdown(countdown);
        eye.setLaps(ageSeconds <= 60
            && selectionKind != CorporateServicePlan.Selection.IDLE
            && selectionKind != CorporateServicePlan.Selection.WAIT_TRANSFER ? projection.laps : 0);
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

    static String nativeMode() {
        return isActive && ("service".equals(activeMode) || "manual".equals(activeMode))
            ? activeMode : "off";
    }

    static String nativeStateJson() {
        String currentMode = nativeMode();
        String details = "service".equals(currentMode) ? activeDutyJson : "";
        return "{\"active\":" + !"off".equals(currentMode)
            + ",\"mode\":\"" + currentMode + "\"" + details + "}";
    }

    @Override public void onDestroy() {
        stopped = true;
        isActive = false;
        activeMode = "off";
        activeDutyJson = "";
        ui.removeCallbacks(poll);
        ui.removeCallbacks(ageTick);
        network.shutdownNow();
        if (viewer != null) { viewer.close(); viewer = null; }
        if (overlay != null) {
            try { windows.removeView(overlay); } catch (Exception ignored) {}
            overlay = null;
            eye = null;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
