package cat.tmbagent.miralincorporatiu;

/** Thirds of the train body. The top-right close control takes precedence. */
final class CorporateEyeTapZones {
    static final int MIRALIN = 0;
    static final int SERVICE = 1;
    static final int VIEWER = 2;

    private CorporateEyeTapZones() {}

    static int actionFor(float x, float width) {
        if (width <= 0 || Float.isNaN(x)) return SERVICE;
        float fraction = Math.max(0, Math.min(1, x / width));
        if (fraction < 1f / 3) return MIRALIN;
        if (fraction < 2f / 3) return SERVICE;
        return VIEWER;
    }
}
