package cat.tmbagent.miralincorporatiu;

import android.app.Application;

/**
 * Keeps a small, read-only local status responder alive for the lifetime of
 * the Android process, including after the floating X closes the eye Service.
 * No foreground notification, Chrome launch, login or extra remote requests.
 * If Android later kills the process, the eye is necessarily stopped too.
 */
public final class CorporateMotorApplication extends Application {
    private NativeStatusServer status;

    @Override public void onCreate() {
        super.onCreate();
        status = new NativeStatusServer(CorporateEyeService::nativeStateJson);
        status.start();
    }

    @Override public void onTerminate() {
        if (status != null) status.close();
        super.onTerminate();
    }
}
