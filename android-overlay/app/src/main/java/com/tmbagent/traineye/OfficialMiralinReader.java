package com.tmbagent.traineye;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.MutableContextWrapper;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.HttpAuthHandler;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebViewDatabase;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayInputStream;

/** A device-local, user-authenticated view of TMB. No credential goes through our backend. */
final class OfficialMiralinReader {
    interface AuthDelegate { void request(HttpAuthHandler handler); }
    private static final String READ_BOARD = """
        (function() {
          function byId(id) { return document.getElementById(id); }
          function text(el) { return el ? el.textContent.trim() : ''; }
          var line = byId('indexForm:linesSelectOneMenu_input');
          var stop = byId('indexForm:stationsSelectOneMenu_input');
          var header = byId('indexForm:panelHeader');
          var cells = header ? header.querySelectorAll('.ui-panelgrid-cell') : [];
          var first = byId('indexForm:tableArrivals1_data');
          var second = byId('indexForm:tableArrivals2_data');
          return JSON.stringify({
            ready: !!(line && stop && first && second && cells.length >= 4),
            line: line ? line.value : '', stop: stop ? stop.value : '',
            date: text(cells[2]), clock: text(cells[3]),
            tables: [
              {via: '1', html: first ? first.innerHTML : ''},
              {via: '2', html: second ? second.innerHTML : ''}
            ]
          });
        })()
        """;

    private final Context app;
    private final MutableContextWrapper context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable onChange;
    private WebView page;
    private AuthDelegate authDelegate;
    private HttpAuthHandler pendingAuth;
    private OfficialHeadwayBoard board;
    private String stop = "413";
    private String loadingUrl = "";
    private long lastLoadAt;
    private String status = "Inicia sessió a TMB.";
    private boolean disposed, ready, reading, needsLogin;
    private int generation;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (disposed) return;
            read();
            handler.postDelayed(this, 5000);
        }
    };

    OfficialMiralinReader(Context service, Runnable onChange) {
        app = service.getApplicationContext();
        context = new MutableContextWrapper(app);
        this.onChange = onChange;
        handler.post(poll);
    }

    String status() { return status; }

    @SuppressLint("SetJavaScriptEnabled")
    WebView attach(Context activity, AuthDelegate delegate) {
        if (disposed) throw new IllegalStateException("El seguiment ha finalitzat.");
        context.setBaseContext(activity);
        authDelegate = delegate;
        if (page == null) {
            page = new WebView(context);
            WebSettings settings = page.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            settings.setBuiltInZoomControls(true);
            settings.setDisplayZoomControls(false);
            settings.setSaveFormData(false);
            settings.setSavePassword(false);
            page.setSaveEnabled(false);
            CookieManager.getInstance().setAcceptThirdPartyCookies(page, false);
            page.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    String url = request.getUrl().toString();
                    if (!OfficialHeadwayBoard.allowsUrl(url)) return true;
                    if (request.isForMainFrame()) loadingUrl = url;
                    return false;
                }
                @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    if (OfficialHeadwayBoard.allowsUrl(request.getUrl().toString())) return null;
                    return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
                }
                @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    generation++; ready = false; reading = false; board = null;
                    loadingUrl = OfficialHeadwayBoard.allowsUrl(url) ? url : "";
                    state("Carregant el quadre oficial de TMB…");
                }
                @Override public void onPageFinished(WebView view, String url) {
                    ready = OfficialHeadwayBoard.allowsUrl(url) && !needsLogin;
                    if (ready) read();
                }
                @Override public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler auth, String host, String realm) {
                    board = null; needsLogin = true; ready = false;
                    if (disposed || !OfficialHeadwayBoard.HOST.equalsIgnoreCase(host)
                        || !OfficialHeadwayBoard.allowsUrl(loadingUrl)) { auth.cancel(); return; }
                    state("Cal iniciar sessió a aplicacions.tmb.cat.");
                    if (authDelegate == null || pendingAuth != null) { auth.cancel(); return; }
                    pendingAuth = auth;
                    authDelegate.request(auth);
                }
                @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                    if (!request.isForMainFrame()) return;
                    board = null; ready = false;
                    if (response.getStatusCode() == 401) needsLogin = true;
                    state(response.getStatusCode() == 401 ? "Sessió de TMB pendent."
                        : "TMB no ha pogut carregar el quadre (" + response.getStatusCode() + ").");
                }
                @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    if (request.isForMainFrame()) {
                        board = null; ready = false;
                        state("Sense connexió amb el Miralín oficial.");
                    }
                }
                @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                    board = null; ready = false; generation++;
                    if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
                    view.destroy();
                    if (page == view) page = null;
                    state("Torna a obrir l'accés a TMB per reconnectar.");
                    return true;
                }
            });
        }
        if (page.getParent() instanceof ViewGroup) ((ViewGroup) page.getParent()).removeView(page);
        page.setFocusable(true);
        page.setFocusableInTouchMode(true);
        page.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        page.onResume();
        page.resumeTimers();
        return page;
    }

    WebView backgroundView() {
        if (disposed || page == null || authDelegate != null) return null;
        if (page.getParent() instanceof ViewGroup) ((ViewGroup) page.getParent()).removeView(page);
        context.setBaseContext(app);
        page.setFocusable(false);
        page.setFocusableInTouchMode(false);
        page.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        return page;
    }

    void detach(AuthDelegate delegate) {
        if (authDelegate != delegate) return;
        authDelegate = null;
        cancelPendingAuth();
        if (page != null && page.getParent() instanceof ViewGroup)
            ((ViewGroup) page.getParent()).removeView(page);
        context.setBaseContext(app);
    }

    void authenticate(HttpAuthHandler auth, String username, String password) {
        if (disposed || auth != pendingAuth) { auth.cancel(); return; }
        pendingAuth = null; needsLogin = false;
        state("Autoritzant amb TMB…");
        // WebView handles the HTTPS challenge. Never persist or log these strings.
        auth.proceed(username, password);
    }

    void cancelAuthentication(HttpAuthHandler auth) {
        if (auth == pendingAuth) pendingAuth = null;
        auth.cancel();
        needsLogin = true; board = null;
        state("Sessió de TMB pendent.");
    }

    private void cancelPendingAuth() {
        if (pendingAuth != null) {
            pendingAuth.cancel(); pendingAuth = null; needsLogin = true;
        }
    }

    void selectStop(String next, boolean force) {
        if (disposed || next == null || !next.matches("41[3-9]|42[0-9]|43[0-4]")) return;
        boolean changed = !next.equals(stop);
        stop = next;
        if (changed) { board = null; generation++; }
        if (page != null && (force || changed) && (!needsLogin || authDelegate != null)) {
            ready = false; reading = false; needsLogin = false; board = null;
            loadingUrl = "https://" + OfficialHeadwayBoard.HOST + "/miralin/routes/4/stops/" + stop;
            lastLoadAt = android.os.SystemClock.elapsedRealtime();
            page.loadUrl(loadingUrl);
        }
    }

    private void read() {
        if (disposed || page == null || !ready || reading || needsLogin
            || !OfficialHeadwayBoard.allowsUrl(page.getUrl())) return;
        reading = true;
        int observedGeneration = generation;
        page.evaluateJavascript(READ_BOARD, value -> {
            reading = false;
            if (disposed || observedGeneration != generation || needsLogin) return;
            try {
                Object decoded = new JSONTokener(value).nextValue();
                if (!(decoded instanceof String)) throw new IllegalArgumentException();
                OfficialHeadwayBoard fresh = OfficialHeadwayBoard.parse(new JSONObject((String) decoded),
                    stop, System.currentTimeMillis());
                board = fresh;
                state("TMB connectat · " + ServiceAlerts.station(stop) + " · " + fresh.serverClock);
            } catch (Exception ignored) {
                board = null;
                state("Esperant un quadre oficial actualitzat de " + ServiceAlerts.station(stop) + ".");
                if (authDelegate == null
                    && android.os.SystemClock.elapsedRealtime() - lastLoadAt >= 60_000L)
                    selectStop(stop, true);
            }
        });
    }

    long seconds(String train, String via, String expectedStop, long now) {
        return board == null || !expectedStop.equals(board.stop) ? -1 : board.seconds(train, via, now);
    }

    JSONObject diagnostic(String train, String via, String expectedStop, long now) throws JSONException {
        return new JSONObject().put("status", status).put("stop", stop)
            .put("ageMs", board == null ? -1 : now - board.receivedAt)
            .put("serverDate", board == null ? "" : board.serverDate)
            .put("serverClock", board == null ? "" : board.serverClock)
            .put("headwaySeconds", seconds(train, via, expectedStop, now));
    }

    private void state(String value) {
        status = value;
        if (!disposed) onChange.run();
    }

    void dispose() {
        if (disposed) return;
        disposed = true; handler.removeCallbacks(poll);
        cancelPendingAuth(); authDelegate = null; board = null;
        if (page != null) {
            if (page.getParent() instanceof ViewGroup) ((ViewGroup) page.getParent()).removeView(page);
            page.stopLoading(); page.destroy(); page = null;
        }
        context.setBaseContext(app);
        WebViewDatabase.getInstance(app).clearHttpAuthUsernamePassword();
        CookieManager.getInstance().removeSessionCookies(null);
    }
}
