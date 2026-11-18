package cat.tmbagent.miralincorporatiu;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.Base64;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Native small service-sheet viewer rendered over the current app.
 * Never launches Chrome, a browser activity, or the PWA. The eye keeps running behind it.
 */
final class CorporateServiceViewer {
    private final Context context;
    private final WindowManager windows;
    private final CorporateServicePlan plan;
    private final String imageBase64;
    private final String rowBoundsJson;
    private final Runnable onClose;
    private final ArrayList<View> timelineRows = new ArrayList<>();
    private FrameLayout root;
    private ScrollView scroll;
    private PhotoView photo;
    private int activeIndex = Integer.MIN_VALUE;

    CorporateServiceViewer(Context context, WindowManager windows, CorporateServicePlan plan,
                           String imageBase64, String rowBoundsJson, Runnable onClose) {
        this.context = context;
        this.windows = windows;
        this.plan = plan;
        this.imageBase64 = imageBase64 == null ? "" : imageBase64;
        this.rowBoundsJson = rowBoundsJson == null ? "" : rowBoundsJson;
        this.onClose = onClose;
    }

    boolean isShowing() { return root != null; }
    private int dp(float value) { return (int) (value * context.getResources().getDisplayMetrics().density + .5f); }

    void show(int realRow) {
        if (root != null) return;
        FrameLayout surface = new FrameLayout(context);
        surface.setBackgroundColor(0x99000000);
        surface.setFocusableInTouchMode(true);
        surface.setOnClickListener(v -> close());
        surface.setOnKeyListener((v, code, event) -> {
            if (code == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                close(); return true;
            }
            return false;
        });

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(5), dp(5), dp(5), dp(5));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0xff111e30);
        shape.setCornerRadius(dp(13));
        shape.setStroke(dp(2), 0xfff4d000);
        card.setBackground(shape);
        card.setOnClickListener(v -> {}); // Touches in the image must not close the viewer.

        LinearLayout bar = new LinearLayout(context);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = text("El meu servei", 14, 0xfff4d000, true);
        title.setPadding(dp(9), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(38), 1));
        title.setGravity(Gravity.CENTER_VERTICAL);
        TextView close = text("×", 29, 0xffffffff, false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Tanca el visor del servei");
        close.setOnClickListener(v -> close());
        bar.addView(close, new LinearLayout.LayoutParams(dp(45), dp(38)));
        card.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

        scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        photo = preparePhoto();
        if (photo != null) {
            scroll.addView(photo, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            scroll.addView(buildTimeline(), new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        card.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int width = Math.min(dp(325), metrics.widthPixels - dp(24));
        int height = Math.min(dp(480), metrics.heightPixels - dp(72));
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
            Math.max(dp(190), width), Math.max(dp(220), height), Gravity.CENTER);
        surface.addView(card, cardParams);
        root = surface;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        try {
            windows.addView(surface, params);
            surface.requestFocus();
            refresh(System.currentTimeMillis(), realRow);
        } catch (Exception error) {
            root = null;
            scroll = null;
            photo = null;
            timelineRows.clear();
        }
    }

    void refresh(long now, int realRow) {
        if (root == null) return;
        // realRow overrides the timetable only during native automatic follow.
        // Manual follow keeps the original planned row highlighting.
        int next = realRow >= 0 ? realRow : plan == null ? -1 : plan.viewerIndex(now);
        if (next == activeIndex) return;
        activeIndex = next;
        if (photo != null) {
            photo.setActiveRow(next);
            if (next >= 0 && next < photo.bounds.size()) {
                scroll.post(() -> {
                    if (root == null || scroll == null || photo == null || next != activeIndex) return;
                    int y = Math.round(photo.bounds.get(next).centerY() * photo.getHeight() - scroll.getHeight() * .4f);
                    scroll.smoothScrollTo(0, Math.max(0, y));
                });
            }
        } else {
            for (int i = 0; i < timelineRows.size(); i++)
                timelineRows.get(i).setBackground(rowBackground(i == next));
            if (next >= 0 && next < timelineRows.size()) {
                View chosen = timelineRows.get(next);
                scroll.post(() -> {
                    if (root != null && next == activeIndex && scroll != null)
                        scroll.smoothScrollTo(0, Math.max(0, chosen.getTop() - dp(35)));
                });
            }
        }
    }

    private PhotoView preparePhoto() {
        if (imageBase64.isEmpty() || imageBase64.length() > 220000) return null;
        try {
            byte[] bytes = Base64.decode(imageBase64, Base64.DEFAULT);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            if (bitmap == null || bitmap.getWidth() < 1 || bitmap.getHeight() < 1) return null;
            ArrayList<RectF> bounds = new ArrayList<>();
            JSONArray rows = new JSONArray(rowBoundsJson.isEmpty() ? "[]" : rowBoundsJson);
            for (int i = 0; i < Math.min(140, rows.length()); i++) {
                JSONArray row = rows.optJSONArray(i);
                if (row == null || row.length() < 4) continue;
                float x = clamp(row.optDouble(0)), y = clamp(row.optDouble(1));
                float w = clamp(row.optDouble(2)), h = clamp(row.optDouble(3));
                bounds.add(new RectF(x, y, Math.min(1, x + w), Math.min(1, y + h)));
            }
            return new PhotoView(bitmap, bounds);
        } catch (Exception invalidImage) { return null; }
    }

    private static float clamp(double number) {
        return (float) Math.max(0, Math.min(1, number));
    }

    private LinearLayout buildTimeline() {
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        List<CorporateServicePlan.Segment> segments =
            plan == null ? java.util.Collections.emptyList() : plan.viewerSegments();
        if (segments.isEmpty()) {
            TextView empty = text("No hi ha cap servei carregat. Obre El meu servei i carrega una captura.", 14,
                0xffeef3fa, false);
            empty.setPadding(dp(10), dp(20), dp(10), dp(20));
            list.addView(empty);
            return list;
        }
        for (CorporateServicePlan.Segment segment : segments) {
            LinearLayout line = new LinearLayout(context);
            line.setOrientation(LinearLayout.VERTICAL);
            line.setPadding(dp(9), dp(7), dp(9), dp(7));
            line.setBackground(rowBackground(false));
            String type = "TRA".equals(segment.type) ? "Trasllat"
                : "EST".equals(segment.type) ? "Estació" : "Conducció";
            line.addView(text(time(segment.start)+"–"+time(segment.end)+" · "+type, 13, 0xfff4d000, true));
            line.addView(text(CorporateServicePlan.stationName(segment.from) +
                (segment.from.equals(segment.to) ? "" : " → " + CorporateServicePlan.stationName(segment.to)),
                12, 0xffeef3fa, false));
            if (segment.driving()) line.addView(text(
                "Tren "+segment.train+" · Via "+segment.via+" → "+segment.out, 11, 0xffa9b8cb, false));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(4);
            list.addView(line, lp);
            timelineRows.add(line);
        }
        return list;
    }

    private static String time(int minute) {
        return String.format(Locale.ROOT, "%02d:%02d", (minute / 60) % 24, minute % 60);
    }

    private GradientDrawable rowBackground(boolean active) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(active ? 0xff26354a : 0xff0c1625);
        bg.setCornerRadius(dp(7));
        bg.setStroke(dp(active ? 2 : 1), active ? 0xfff4d000 : 0xff28394e);
        return bg;
    }
    private TextView text(String content, int size, int color, boolean bold) {
        TextView label = new TextView(context);
        label.setText(content);
        label.setTextSize(size);
        label.setTextColor(color);
        if (bold) label.setTypeface(null, Typeface.BOLD);
        return label;
    }

    void close() {
        if (root == null) return;
        try { windows.removeView(root); } catch (Exception ignored) {}
        root = null;
        scroll = null;
        photo = null;
        timelineRows.clear();
        onClose.run();
    }

    private final class PhotoView extends View {
        private final Bitmap bitmap;
        private final ArrayList<RectF> bounds;
        private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int row = -1;

        PhotoView(Bitmap bitmap, ArrayList<RectF> bounds) {
            super(context);
            this.bitmap = bitmap;
            this.bounds = bounds;
            fill.setColor(0x66f4d000);
            stroke.setColor(0xfff4d000);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(3));
        }
        void setActiveRow(int row) { this.row = row; invalidate(); }
        @Override protected void onMeasure(int w, int h) {
            int width = MeasureSpec.getSize(w);
            if (width < 1) width = dp(300);
            int height = Math.max(1, Math.round(width * (float) bitmap.getHeight() / bitmap.getWidth()));
            setMeasuredDimension(width, height);
        }
        @Override protected void onDraw(Canvas canvas) {
            RectF rect = new RectF(0, 0, getWidth(), getHeight());
            canvas.drawBitmap(bitmap, null, rect, ink);
            if (row >= 0 && row < bounds.size()) {
                RectF b = bounds.get(row);
                RectF area = new RectF(b.left * getWidth(), b.top * getHeight(),
                    b.right * getWidth(), b.bottom * getHeight());
                canvas.drawRect(area, fill);
                canvas.drawRect(area, stroke);
            }
        }
    }
}
