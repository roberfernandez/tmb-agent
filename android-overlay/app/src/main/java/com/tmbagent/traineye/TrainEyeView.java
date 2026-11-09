package com.tmbagent.traineye;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import java.util.Locale;

/** Floating train-status eye, with scalable station lettering above the iris. */
final class TrainEyeView extends View {
    static final int WIDTH_DP = 142;
    static final int HEIGHT_DP = 58;
    static final int CLOSE_WIDTH_DP = 0;

    private static final int GOLD = 0xfff4d000;
    private static final int INK = 0xff111e30;
    private static final float CENTER_X = 71f;
    private static final float PUPIL_Y = 29f;
    private static final float PUPIL_RADIUS = 22f;
    private static final float STATION_WIDTH = 137f;
    private static final float STATION_SIZE = 15f;
    private static final Typeface LABEL_FACE = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Typeface STATION_FACE = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
    private static final Typeface TRAIN_FACE = Typeface.create(STATION_FACE, Typeface.BOLD);

    private final Path outline = new Path();
    private final Path lashes = new Path();
    private final Path stationArc = new Path();
    private final float stationArcLength;
    private final RectF irisBounds = new RectF(CENTER_X - 29f, 2.5f, CENTER_X + 29f, 55.5f);
    private final Paint surface = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = stroke(GOLD, 1.4f);
    private final Paint lashStroke = stroke(GOLD, 1.05f);
    private final Paint irisRim = stroke(0xff0879c9, 0.85f);
    private final Paint iris = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pupil = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);

    private String train = "";
    private String station = "";
    private String track = "";
    private String age = "";
    private String stationLabel = "";
    private String countdown = "--";

    TrainEyeView(Context context) {
        super(context);
        outline.moveTo(2.5f, 29f);
        outline.cubicTo(19f, 11f, 45f, 2.5f, CENTER_X, 2.5f);
        outline.cubicTo(97f, 2.5f, 123f, 11f, 139.5f, 29f);
        float upperLength = new PathMeasure(outline, false).getLength();
        outline.cubicTo(123f, 47f, 97f, 55.5f, CENTER_X, 55.5f);
        outline.cubicTo(45f, 55.5f, 19f, 47f, 2.5f, 29f);
        outline.close();
        PathMeasure eyelids = new PathMeasure(outline, false);
        float lowerLength = eyelids.getLength() - upperLength;
        addLashes(eyelids, 0.20f * upperLength, 0.58f * upperLength, 7, 2.55f);
        addLashes(eyelids, upperLength + 0.24f * lowerLength, 0.54f * lowerLength, 5, 1.75f);

        stationArc.moveTo(4f, 30f);
        stationArc.cubicTo(39f, 4f, 103f, 4f, 138f, 30f);
        stationArcLength = new PathMeasure(stationArc, false).getLength();

        surface.setShader(new LinearGradient(0, 4.5f, 0, 43.5f, 0xfffffdf7, 0xffe7edf2, Shader.TileMode.CLAMP));
        iris.setShader(new LinearGradient(0, irisBounds.top, 0, irisBounds.bottom,
            0xff19b5e7, 0xff064e9b, Shader.TileMode.CLAMP));
        pupil.setColor(0xff020509);
    }

    private static Paint stroke(int color, float width) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        return paint;
    }

    private void addLashes(PathMeasure lid, float start, float span, int count, float length) {
        float[] point = new float[2];
        float[] tangent = new float[2];
        for (int i = 0; i < count; i++) {
            float fraction = i / (float) (count - 1);
            lid.getPosTan(start + span * fraction, point, tangent);
            float reach = length + 0.6f * (1 - Math.abs(2 * fraction - 1));
            float normalX = tangent[1] * reach;
            float normalY = -tangent[0] * reach;
            lashes.moveTo(point[0], point[1]);
            lashes.quadTo(point[0] + 0.6f * normalX, point[1] + 0.6f * normalY,
                point[0] + normalX + 0.7f, point[1] + normalY);
        }
    }

    void setCountdown(String value) {
        if (value == null) value = "--";
        if (!countdown.equals(value)) { countdown = value; invalidate(); }
    }

    void setStatus(String train, String station, String track, String age) {
        if (station == null || station.isEmpty()) station = "—";
        if (track == null || track.isEmpty()) track = "—";
        if (this.train.equals(train) && this.station.equals(station)
            && this.track.equals(track) && this.age.equals(age)) return;
        if (!this.station.equals(station)) stationLabel = compactStation(station);
        this.train = train;
        this.station = station;
        this.track = track;
        this.age = age;
        setContentDescription("Tren " + train + " · " + station + " · via " + track + " · " + age
            + ". Obre el visor del servei.");
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int save = canvas.save();
        canvas.scale(getWidth() / (float) WIDTH_DP, getHeight() / (float) HEIGHT_DP);
        canvas.drawPath(outline, surface);
        canvas.drawPath(lashes, lashStroke);
        canvas.drawPath(outline, rim);
        canvas.drawOval(irisBounds, iris);
        canvas.drawOval(irisBounds, irisRim);
        canvas.drawCircle(CENTER_X, PUPIL_Y, PUPIL_RADIUS, pupil);

        drawStation(canvas);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(LABEL_FACE);
        text.setColor(0xff10243a);
        text.setTypeface(TRAIN_FACE);
        drawMetric(canvas, "v." + track, 22f, 26f, 13f, 33f);
        text.setTypeface(LABEL_FACE);
        drawMetric(canvas, age, 22f, 37f, 8.5f, 29f);
        drawMetric(canvas, countdown, 120f, 29f, 11f, 36f);
        text.setTypeface(TRAIN_FACE);
        text.setColor(0xffffdc4f);
        drawMetric(canvas, train, CENTER_X, 32f, 21f, 43f);
        canvas.restoreToCount(save);
    }

    private void drawStation(Canvas canvas) {
        // One line across the upper sclera and iris, with a dark contour
        // for contrast on both the white and blue backgrounds.
        text.setTextAlign(Paint.Align.LEFT);
        text.setTypeface(Typeface.create(STATION_FACE, Typeface.BOLD));
        text.setTextSize(STATION_SIZE);
        float measured = text.measureText(stationLabel);
        if (measured > STATION_WIDTH) {
            text.setTextSize(Math.max(7.5f, STATION_SIZE * STATION_WIDTH / measured));
        }
        String label = TextUtils.ellipsize(stationLabel, text, STATION_WIDTH,
            TextUtils.TruncateAt.END).toString();
        float offset = (stationArcLength - text.measureText(label)) / 2f;
        text.setStyle(Paint.Style.STROKE);
        text.setStrokeJoin(Paint.Join.ROUND);
        text.setStrokeWidth(2.4f);
        text.setColor(0xff061424);
        canvas.drawTextOnPath(label, stationArc, offset, 0, text);
        text.setStyle(Paint.Style.FILL);
        text.setColor(0xffffdf30);
        canvas.drawTextOnPath(label, stationArc, offset, 0, text);
    }

    private void drawMetric(Canvas canvas, String label, float x, float y, float size, float maxWidth) {
        text.setTextSize(size);
        float width = text.measureText(label);
        if (width > maxWidth) text.setTextSize(size * maxWidth / width);
        float baseline = y - (text.ascent() + text.descent()) / 2f;
        canvas.drawText(label, x, baseline, text);
    }

    private static String compactStation(String station) {
        String normalized = station.toLowerCase(Locale.ROOT);
        if (normalized.contains("maresme")) return "Maresme";
        if (normalized.contains("ciutadella")) return "Ciutadella";
        if (normalized.contains("guinardó") || normalized.contains("guinardo")) return "Guinardó";
        if (normalized.contains("passeig de gràcia") || normalized.contains("passeig de gracia")) return "Pg. Gràcia";
        if (normalized.equals("trinitat nova")) return "Trinitat N.";
        if (normalized.equals("sense connexió")) return "Desconn.";
        return station;
    }
}
