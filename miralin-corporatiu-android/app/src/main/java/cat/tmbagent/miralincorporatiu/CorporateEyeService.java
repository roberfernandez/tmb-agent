package cat.tmbagent.miralincorporatiu;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
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
    static final String ALERTS_CHANNEL = "corporate_service_alerts";
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
    private int pressedThird;
    private int notificationRow = -1;
    private String notificationKey = "";
    private String boardMdm = "";
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
    private int connectionDestination;
    private java.util.List<ConnectionOptions.Option> options = java.util.Collections.emptyList();
    private String activityLabel = "";
    private int selectionGeneration;
    private boolean alertsEnabled;
    private int alertMargin = 5;
    private String alertPlanIdentity = "";
    private Set<String> sentAlerts = new HashSet<>();
    private long departureAt = -1;
    private CorporateTrainSelection.Train lastTrain;
    private boolean exceedsMdm;
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
                || !watchesTrain()) return;
            busy = true;
            final String requested = train;
            final int requestedStop = stop;
            final int requestedVia = targetVia;
            final int requestedKind = selectionKind;
            final int destination = connectionDestination;
            final long plannedRelay = takeoverAt;
            final int generation = selectionGeneration;
            network.execute(() -> {
                CorporateTrainSelection.Train found = null;
                CorporateTrainSelection.Train matchingDeparture = null;
                java.util.List<ConnectionOptions.Option> connections = java.util.Collections.emptyList();
                String mdm = "";
                Exception failure = null;
                try {
                    CredentialVault.Login login = new CredentialVault(CorporateEyeService.this).load();
                    if (login == null) throw new SecurityException("Cal iniciar sessió a l'aplicació.");
                    CorporateBoardParser.Board board = client.fetch(login.username, login.password, requestedStop);
                    mdm = board.mdm;
                    if (requestedKind == CorporateServicePlan.Selection.CONNECTION) {
                        CorporateBoardParser.Board other = client.fetch(login.username, login.password, destination);
                        connections = ConnectionOptions.calculate(board, other, requested, requestedVia,
                            plannedRelay, System.currentTimeMillis());
                    } else {
                        matchingDeparture = requestedVia == 0
                            ? onlyDepartureAtTerminal(board, requested)
                            : CorporateTrainSelection.find(board, requested, requestedVia);
                        if (matchingDeparture == null && requestedVia == 0
                            && requestedKind == CorporateServicePlan.Selection.DRIVING) {
                            int inboundVia = requestedStop == CorporateServicePlan.FIRST_STOP ? 2 : 1;
                            matchingDeparture = CorporateTrainSelection.find(board, requested, inboundVia);
                        }
                        found = matchingDeparture;
                        if (found == null && (requestedVia == 0
                            || requestedKind == CorporateServicePlan.Selection.TRANSFER_PICKUP
                            || requestedKind == CorporateServicePlan.Selection.DRIVING))
                            found = CorporateTrainSelection.find(board, requested);
                    }
                } catch (Exception e) {
                    failure = e;
                }
                final CorporateTrainSelection.Train result = found;
                final CorporateTrainSelection.Train departure = matchingDeparture;
                final java.util.List<ConnectionOptions.Option> alternatives = connections;
                final String publishedMdm = mdm;
                final boolean longGap = found != null && CorporateBoardParser.exceedsMdm(found.previousTrainGap, mdm);
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
                        options = alternatives;
                        lastTrain = result;
                        exceedsMdm = longGap;
                        boardMdm = publishedMdm;
                        departureAt = departure == null ? -1
                            : OfficialDepartureCountdown.departureEpochMs(departure.departure, receivedAt);
                        connection = result == null ? "Tren no localitzat a la taula consultada"
                            : departure == null ? "No consta SORTIDA per aquesta via"
                            : "Lectura oficial rebuda";
                        if (requestedKind != CorporateServicePlan.Selection.CONNECTION && "service".equals(mode) && duty != null) {
                            boolean driving = requestedKind == CorporateServicePlan.Selection.DRIVING;
                            boolean unambiguous = (driving && requestedVia != 0)
                                ? result != null : departure != null;
                            if (requestedKind == CorporateServicePlan.Selection.TRANSFER_PICKUP)
                                unambiguous = result != null;
                            if (driving && CorporateProjection.project(departureAt, receivedAt, takeoverAt).laps > 0)
                                unambiguous = false;
                            CorporateDutyTracker.Snapshot beforeObservation = duty.at(receivedAt);
                            if (duty.observe(receivedAt, requested,
                                result == null ? "" : result.station,
                                result == null ? "" : result.track, unambiguous, departureAt)) {
                                if ((requestedKind == CorporateServicePlan.Selection.PICKUP
                                    || requestedKind == CorporateServicePlan.Selection.TRANSFER_PICKUP)
                                    && alertsEnabled && result != null && CorporateDutyTracker.sameStation(result.station,
                                        CorporateServicePlan.stationName(Integer.toString(requestedStop)))) {
                                    int row = beforeObservation == null ? notificationRow
                                        : beforeObservation.pickupRow;
                                    CorporateServicePlan.Notice arrival = ServiceAlertPolicy.confirmedArrival(
                                        servicePlan, row, requested, result.station, destination, receivedAt);
                                    if (arrival != null) sendServiceNoticeOnce(arrival, arrival.body);
                                }
                                applyServiceSelection(receivedAt, true);
                            }
                            publishDutyStatus(receivedAt);
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
                    if (!blocked && watchesTrain()) {
                        ui.removeCallbacks(poll);
                        ui.postDelayed(poll, REFRESH_MS);
                    }
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
            if (!stopped) {
                startForegroundNotification();
                ui.postDelayed(this, 1000);
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(
            CHANNEL, "El meu servei · seguiment", NotificationManager.IMPORTANCE_LOW));
        createAlertsChannel(manager);
        migrateLegacyServiceNotifications(manager);
    }

    static void createAlertsChannel(NotificationManager manager) {
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
            cleanupServiceNotifications(System.currentTimeMillis());
            return START_NOT_STICKY;
        }
        if (intent != null) {
            String requestedMode = intent.getStringExtra(EXTRA_MODE);
            if ("service".equals(requestedMode)) {
                String schedule = intent.getStringExtra(EXTRA_SCHEDULE);
                String date = intent.getStringExtra(EXTRA_SERVICE_DATE);
                try {
                    CorporateServicePlan requestedPlan = new CorporateServicePlan(date, schedule);
                    boolean reuse = duty != null && servicePlan != null
                        && servicePlan.identity().equals(requestedPlan.identity())
                        && previewOnly == intent.getBooleanExtra(EXTRA_PREVIEW_MODE, false);
                    servicePlan = requestedPlan;
                    previewOnly = intent.getBooleanExtra(EXTRA_PREVIEW_MODE, false);
                    selectionKind = CorporateServicePlan.Selection.IDLE;
                    if (!reuse) {
                        duty = new CorporateDutyTracker(servicePlan, previewOnly);
                        duty.resumeAt(System.currentTimeMillis());
                    }
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
        cleanupServiceNotifications(System.currentTimeMillis());
        lastTrain = null;
        receivedAt = 0;
        departureAt = -1;
        notificationKey = "";
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
        if (watchesTrain()) ui.post(poll);
        ui.post(ageTick);
        return START_NOT_STICKY;
    }

    private void startForegroundNotification() {
        long now = System.currentTimeMillis();
        boolean manual = "manual".equals(mode);
        boolean fresh = receivedAt > 0 && now - receivedAt <= 60_000;
        String station = CorporateServicePlan.stationName(Integer.toString(stop));
        String platform = targetVia > 0 ? " · V" + targetVia : "";
        String position = fresh && lastTrain != null ? lastTrain.station + " · " + lastTrain.track : "";
        String title = train + (position.isEmpty() ? "" : " · " + position);
        String label = manual ? station : "Fi a " + station;
        long time = fresh ? (manual ? departureAt : CorporateProjection.project(departureAt, now, takeoverAt).timeMs) : -1;
        boolean scheduled = false;
        String gap = fresh && lastTrain != null ? lastTrain.previousTrainGap : "";
        if (!manual && selectionKind != CorporateServicePlan.Selection.DRIVING) {
            title = activityLabel + " · " + station + platform;
            if (selectionKind == CorporateServicePlan.Selection.CONNECTION) {
                ConnectionOptions.Option option = lastConnection(now);
                label = option == null ? "Sense enllaç confirmat" : "Últim enllaç: " + option.train;
                time = option == null ? -1 : option.departure;
                gap = option == null ? "" : option.previousTrainGap;
                if (option != null) title = "EST · " + station + platform;
            } else if ("TRA".equals(activityLabel) && watchesTrain()) {
                title = "TRA · " + train + " → " + station;
                label = "Relleu";
            } else if (watchesTrain()) {
                label = train;
            } else {
                scheduled = true;
                time = takeoverAt;
                label = selectionKind == CorporateServicePlan.Selection.TRANSFER ? "Trasllat" : "Fi";
                if ("TRA".equals(activityLabel) && servicePlan != null && notificationRow >= 0) {
                    CorporateServicePlan.Segment leg = servicePlan.viewerSegments().get(notificationRow);
                    title = "TRA · " + station + " → " + CorporateServicePlan.stationName(leg.to);
                    time = servicePlan.minuteMillis(leg.end);
                    label = "Fi previst";
                }
            }
        }
        if (!manual && selectionKind == CorporateServicePlan.Selection.DRIVING && time < 0) {
            scheduled = true;
            time = takeoverAt;
        }
        String summary = label;
        if (time >= 0) {
            summary += (scheduled ? " " : " ≈") + OfficialDepartureCountdown.clock(time)
                + (scheduled ? " · segons servei" : " · en " + Math.max(0, (time - now + 59_999) / 60_000) + " min");
        } else if (watchesTrain() && selectionKind != CorporateServicePlan.Selection.CONNECTION) {
            summary += " · pendent de previsió";
        }
        String details = summary;
        if (!position.isEmpty() && !title.contains(position)) details += "\nAra a " + position;
        if (!gap.isEmpty()) details += "\n" + ((manual || selectionKind == CorporateServicePlan.Selection.DRIVING)
            && exceedsMdm ? "2 galons" : "1 galó") + " · Interval " + formatHeadway(gap);
        if (fresh && !boardMdm.isEmpty()) details += " · MDM " + formatHeadway(boardMdm);
        String key = title + "\n" + details;
        if (key.equals(notificationKey)) return;
        boolean first = notificationKey.isEmpty();
        notificationKey = key;
        PendingIntent pending = viewerPendingIntent();
        Notification notification = new Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_status_train)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(new Notification.BigTextStyle().bigText(details))
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(pending)
            .setOngoing(true)
            .build();
        if (!first) {
            getSystemService(NotificationManager.class).notify(NOTIFICATION, notification);
        } else if (Build.VERSION.SDK_INT >= 34) {
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
                    pressedThird = eye.tapThird(event.getX());
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
                    } else if (eye != null && pressedThird == eye.tapThird(event.getX())) {
                        if (pressedThird == 2) view.performClick();
                        else {
                            String url = pressedThird == 0 ? CorporateMiralinClient.boardUrl(stop)
                                : "https://roberfernandez.github.io/tmb-agent/proves/servei/";
                            Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            try { startActivity(open); }
                            catch (android.content.ActivityNotFoundException e) {
                                android.widget.Toast.makeText(this, "Cal un navegador per obrir l’enllaç.",
                                    android.widget.Toast.LENGTH_SHORT).show();
                            }
                        }
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
        return viewerPendingIntent(this);
    }

    private static PendingIntent viewerPendingIntent(Context context) {
        Intent viewer = new Intent(Intent.ACTION_VIEW, Uri.parse(VIEWER));
        viewer.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, 110, viewer,
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

    private void migrateLegacyServiceNotifications(NotificationManager manager) {
        SharedPreferences settings = getSharedPreferences("corporate-alerts-v1", MODE_PRIVATE);
        if (settings.getBoolean("stable-notification-tags-v1", false)) return;
        // Versions through 0.14.28 used process-local IDs starting at 9600.
        // Remove only that app-owned range once, leaving the foreground ID intact.
        for (int id = 9600; id < 10_000; id++) manager.cancel(id);
        settings.edit().putBoolean("stable-notification-tags-v1", true).apply();
    }

    private void cleanupServiceNotifications(long now) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        CorporateDutyTracker.Snapshot snapshot = "service".equals(mode) && duty != null
            ? duty.at(now) : null;
        for (android.service.notification.StatusBarNotification active
                : manager.getActiveNotifications()) {
            String tag = active.getTag();
            if (tag == null || !tag.startsWith(ServiceAlertPolicy.TAG_PREFIX)) continue;
            String key = servicePlan == null ? null
                : ServiceAlertPolicy.keyFromTag(servicePlan.identity(), tag);
            if (key == null
                || !ServiceAlertPolicy.isRelevant(key, now, alertsEnabled, servicePlan, snapshot))
                manager.cancel(tag, active.getId());
        }
    }

    private void cancelAllServiceNotifications() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        for (android.service.notification.StatusBarNotification active
                : manager.getActiveNotifications()) {
            String tag = active.getTag();
            if (tag != null && tag.startsWith(ServiceAlertPolicy.TAG_PREFIX))
                manager.cancel(tag, active.getId());
        }
    }

    private void tickServiceAlerts(long now) {
        cleanupServiceNotifications(now);
        if (previewOnly || !alertsEnabled || servicePlan == null || !"service".equals(mode)) return;
        java.util.List<CorporateServicePlan.Notice> notices = servicePlan.noticesAt(now, alertMargin);
        CorporateDutyTracker.Snapshot snapshot = duty == null ? null : duty.at(now);
        CorporateServicePlan.Notice liveTrainNotice = ServiceAlertPolicy.liveTrainNotice(
            servicePlan, snapshot, lastTrain, receivedAt, departureAt, now, alertMargin);
        if (liveTrainNotice != null) notices.add(liveTrainNotice);
        if (selectionKind == CorporateServicePlan.Selection.CONNECTION && now - receivedAt <= 60_000) {
            StringBuilder details = new StringBuilder();
            long first = 0;
            for (ConnectionOptions.Option option : options) {
                if (option.departure <= now) continue;
                if (first == 0) first = option.departure;
                if (details.length() > 0) details.append("\n");
                details.append(option.train).append(" · ").append(OfficialDepartureCountdown.clock(option.departure))
                    .append(" · marge ").append(option.margin / 60_000L).append(" min");
            }
            if (first > now && first - now <= alertMargin * 60_000L) {
                String route = CorporateServicePlan.stationName(Integer.toString(stop)) + " · Via " + targetVia
                    + " → " + CorporateServicePlan.stationName(Integer.toString(connectionDestination));
                int row = snapshot == null ? notificationRow : snapshot.pickupRow;
                notices.add(new CorporateServicePlan.Notice(ServiceAlertPolicy.connectionKey(row),
                    "TREN · enllaç amb el " + train, route, OfficialDepartureCountdown.clock(first),
                    "Previsions de Miralín corporatiu\n" + details, true));
            }
        }
        for (CorporateServicePlan.Notice notice : notices) {
            String content = notice.body;
            if (notice.key.startsWith("live-train-") && departureAt > now && lastTrain != null) {
                String countdown = OfficialDepartureCountdown.display(departureAt, now);
                if (!"--".equals(countdown))
                    content += "\nPas previst " + countdown;
            }
            if (sentAlerts.contains(notice.key)) {
                // Keep a visible live notice synchronized when TMB changes its
                // published passage time, without alerting again.
                if (notice.key.startsWith("live-")) postServiceNotification(notice, content);
                continue;
            }
            sendServiceNoticeOnce(notice, content);
        }
    }

    private void sendServiceNoticeOnce(CorporateServicePlan.Notice notice, String content) {
        if (!sentAlerts.contains(notice.key) && postServiceNotification(notice, content)) {
            sentAlerts.add(notice.key);
            getSharedPreferences("corporate-alerts-v1", MODE_PRIVATE).edit()
                .putStringSet("sent", new HashSet<>(sentAlerts)).apply();
        }
    }

    private boolean postServiceNotification(CorporateServicePlan.Notice notice, String body) {
        try {
            getSystemService(NotificationManager.class).notify(
                ServiceAlertPolicy.tag(servicePlan.identity(), notice.key),
                ServiceAlertPolicy.NOTIFICATION_ID,
                serviceNotification(this, notice, body));
            return true;
        } catch (Exception ignored) { return false; }
    }

    private static Notification serviceNotification(Context context, CorporateServicePlan.Notice notice,
                                            String body) {
        int icon = notice.pickup ? R.drawable.ic_notice_train : R.drawable.ic_notice_transfer;
        int color = notice.pickup ? 0xffbc2639 : 0xff205bbb;
        PendingIntent open = viewerPendingIntent(context);
        return new Notification.Builder(context, ALERTS_CHANNEL)
            .setSmallIcon(notice.pickup ? R.drawable.ic_status_train : R.drawable.ic_status_transfer)
            .setLargeIcon(serviceNoticeIcon(context, icon, color))
            .setColor(color)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentTitle(notice.title
                + " · " + notice.time)
            .setContentText(notice.route)
            .setStyle(new Notification.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .addAction(new Notification.Action.Builder((android.graphics.drawable.Icon) null,
                "Obre el servei", open).build())
            .setAutoCancel(true)
            .build();
    }

    private static Bitmap serviceNoticeIcon(Context context, int resource, int color) {
        float density = context.getResources().getDisplayMetrics().density;
        int size = Math.round(48 * density);
        int inset = Math.round(3 * density);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Drawable icon = context.getDrawable(resource).mutate();
        icon.setTint(color);
        icon.setBounds(inset, inset, size - inset, size - inset);
        icon.draw(canvas);
        return bitmap;
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
        // A delayed relay may still have a catchable connection after the printed transfer ends.
        if (selectionKind == CorporateServicePlan.Selection.CONNECTION
            && selected.kind == CorporateServicePlan.Selection.PICKUP && selected.train.equals(train)
            && selected.stop == connectionDestination && selected.takeoverAt == takeoverAt
            && lastConnection(now) != null) return true;
        String type = servicePlan.viewerSegments().get(snapshot.photoRow).type;
        activityLabel = snapshot.driving ? "" : "CON".equals(type) ? "EST" : type;
        if (selected.kind == CorporateServicePlan.Selection.TRANSFER_PICKUP) activityLabel = "EST";
        notificationRow = selected.row;
        long nextTakeover = selected.takeoverAt;
        boolean changed = !selected.train.equals(train) || selected.stop != stop
            || selected.via != targetVia || nextTakeover != takeoverAt
            || selected.kind != selectionKind || selected.destination != connectionDestination;
        takeoverAt = nextTakeover;
        publishDutyStatus(now);
        if (!changed) return true;
        train = selected.train;
        stop = selected.stop;
        targetVia = selected.via;
        connectionDestination = selected.destination;
        options = java.util.Collections.emptyList();
        selectionKind = selected.kind;
        selectionGeneration++;
        lastTrain = null;
        receivedAt = 0;
        departureAt = -1;
        connection = watchesTrain() ? "Canvi d'activitat · consultant TMB…"
            : "Esperant el proper moviment del servei…";
        if (refresh) startForegroundNotification();
        render();
        if (refresh && selected.watchesTrain() && !busy && !blocked) {
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

    private boolean watchesTrain() {
        return selectionKind == CorporateServicePlan.Selection.DRIVING
            || selectionKind == CorporateServicePlan.Selection.PICKUP
            || selectionKind == CorporateServicePlan.Selection.TRANSFER_PICKUP
            || selectionKind == CorporateServicePlan.Selection.CONNECTION;
    }

    private ConnectionOptions.Option lastConnection(long now) {
        if (options.isEmpty() || now - receivedAt > 60_000) return null;
        ConnectionOptions.Option last = options.get(options.size() - 1);
        return last.departure > now ? last : null;
    }

    private void render() {
        if (eye == null) return;
        long now = System.currentTimeMillis();
        boolean scheduledMovement = !watchesTrain();
        boolean transfer = selectionKind == CorporateServicePlan.Selection.TRANSFER;
        long ageSeconds = receivedAt == 0 ? -1 : Math.max(0, (now - receivedAt) / 1000);
        String age = ageSeconds < 0 ? "—" : ageSeconds < 60 ? ageSeconds + "s" : (ageSeconds / 60) + "m";
        if (selectionKind == CorporateServicePlan.Selection.CONNECTION) {
            ConnectionOptions.Option last = lastConnection(now);
            boolean valid = last != null;
            eye.setStatus(valid ? last.train : "—", CorporateServicePlan.stationName(Integer.toString(stop)),
                Integer.toString(targetVia), age);
            eye.setCountdown(valid ? OfficialDepartureCountdown.display(last.departure, now) : "--");
            eye.setMovement(valid ? "EST" : activityLabel, valid ? "≈" + OfficialDepartureCountdown.clock(last.departure) : "");
            eye.setHeadway(valid ? formatHeadway(last.previousTrainGap) : "--", false);
            eye.setLaps(0);
            return;
        }
        String stationText = lastTrain == null
            ? (scheduledMovement ? CorporateServicePlan.stationName(Integer.toString(stop))
                : receivedAt == 0 ? "Connectant…" : "Sense dades")
            : lastTrain.station;
        String trackText = lastTrain == null
            ? (transfer && targetVia != 0 ? Integer.toString(targetVia) : "—")
            : lastTrain.track.replaceFirst("(?i)^via\\s*", "").trim();
        if (blocked && !scheduledMovement) stationText = "Desconn.";
        if (selectionKind == CorporateServicePlan.Selection.TRANSFER_PICKUP) {
            stationText = CorporateServicePlan.stationName(Integer.toString(stop));
            trackText = Integer.toString(targetVia);
        }
        eye.setStatus(train, stationText, trackText, age);


        CorporateProjection.Result projection = "service".equals(mode)
            ? CorporateProjection.project(departureAt, now, takeoverAt)
            : new CorporateProjection.Result(departureAt, 0);
        boolean fresh = ageSeconds >= 0 && ageSeconds <= 60;
        String countdown = scheduledMovement ? OfficialDepartureCountdown.displayExact(takeoverAt, now)
            : fresh ? OfficialDepartureCountdown.display(projection.timeMs, now) : "--";
        boolean driving = "service".equals(mode) && selectionKind == CorporateServicePlan.Selection.DRIVING;
        eye.setHeadway(lastTrain == null ? "--" : formatHeadway(lastTrain.previousTrainGap),
            (driving || "manual".equals(mode)) && fresh && lastTrain != null && exceedsMdm);
        String clock = "";
        if (selectionKind == CorporateServicePlan.Selection.TRANSFER_PICKUP) {
            if (!"--".equals(countdown)) clock = "≈" + OfficialDepartureCountdown.clock(projection.timeMs);
        } else if (transfer) {
            clock = OfficialDepartureCountdown.clock(takeoverAt);
        } else if (selectionKind == CorporateServicePlan.Selection.DRIVING) {
            if (!"--".equals(countdown)) {
                clock = "≈" + OfficialDepartureCountdown.clock(projection.timeMs);
            } else if (driving && takeoverAt > 0) {
                clock = "Hor. " + OfficialDepartureCountdown.clock(takeoverAt);
            }
        }
        eye.setCountdown(countdown);
        eye.setLaps(fresh && !scheduledMovement ? projection.laps : 0);
        eye.setMovement("service".equals(mode) ? activityLabel : "", clock);
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
        cancelAllServiceNotifications();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
