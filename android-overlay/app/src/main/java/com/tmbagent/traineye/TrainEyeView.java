package com.tmbagent.traineye;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import java.util.Locale;

/** The floating eye, drawn in fixed proportions so updates never move the close button. */
final class TrainEyeView extends View {
    static final int WIDTH_DP = 156;
    static final int HEIGHT_DP = 54;
    static final int CLOSE_WIDTH_DP = 36;

    private static final int GOLD = 0xfff4d000;
    private static final int INK = 0xff111e30;
    private static final float IRIS_X = 71f;
    private static final float IRIS_Y = 32.5f;
    private static final float IRIS_RADIUS = 16f;
    private static final float STATION_WIDTH = 80f;
    private static final float STATION_SIZE = 11f;
    private static final Typeface LABEL_FACE = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Typeface TRAIN_FACE = Typeface.create("sans-serif-condensed", Typeface.BOLD);

    private final Path outline = new Path();
    private final Path stationArc = new Path();
    private final float stationArcLength;
    private final Paint surface = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iris = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);

    private String train = "";
    private String station = "";
    private String track = "";
    private String age = "";
    private String stationLabel = "";

    TrainEyeView(Context context) {
        super(context);
        outline.moveTo(2.5f, 28f);
        outline.cubicTo(23f, 10f, 47f, 2f, IRIS_X, 2f);
        outline.cubicTo(98f, 2f, 125f, 11f, 145f, 27f);
        outline.cubicTo(125f, 44f, 100f, 52f, IRIS_X, 52f);
        outline.cubicTo(44f, 52f, 20f, 44f, 2.5f, 28f);
        outline.close();

        stationArc.moveTo(27f, 22f);
        stationArc.quadTo(IRIS_X, 4f, 115f, 22f);
        stationArcLength = new PathMeasure(stationArc, false).getLength();

        surface.setShader(new LinearGradient(0, 2, 0, 52, 0xff192b40, INK, Shader.TileMode.CLAMP));
        rim.setColor(GOLD);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.5f);
        rim.setStrokeJoin(Paint.Join.ROUND);
        iris.setShader(new LinearGradient(0, IRIS_Y - IRIS_RADIUS, 0, IRIS_Y + IRIS_RADIUS,
            0xffffe566, GOLD, Shader.TileMode.CLAMP));
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
        canvas.drawPath(outline, rim);
        canvas.drawCircle(IRIS_X, IRIS_Y, IRIS_RADIUS, iris);

        drawStation(canvas);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(LABEL_FACE);
        text.setColor(0xffdce5ef);
        drawMetric(canvas, "v." + track, 32f, 11f, 27f);
        drawMetric(canvas, age, 108f, 11f, 25f);
        text.setTypeface(TRAIN_FACE);
        text.setColor(INK);
        drawMetric(canvas, train, IRIS_X, 18f, 27f);
        canvas.restoreToCount(save);
    }

    private void drawStation(Canvas canvas) {
        text.setTextAlign(Paint.Align.LEFT);
        text.setTypeface(LABEL_FACE);
        text.setColor(0xffffe674);
        text.setTextSize(STATION_SIZE);
        float width = text.measureText(stationLabel);
        if (width > STATION_WIDTH) {
            text.setTextSize(Math.max(9.5f, STATION_SIZE * STATION_WIDTH / width));
        }
        String label = TextUtils.ellipsize(stationLabel, text, STATION_WIDTH, TextUtils.TruncateAt.END).toString();
        float offset = (stationArcLength - text.measureText(label)) / 2f;
        canvas.drawTextOnPath(label, stationArc, offset, 0, text);
    }

    private void drawMetric(Canvas canvas, String label, float x, float size, float maxWidth) {
        text.setTextSize(size);
        float width = text.measureText(label);
        if (width > maxWidth) text.setTextSize(size * maxWidth / width);
        float baseline = IRIS_Y - (text.ascent() + text.descent()) / 2f;
        canvas.drawText(label, x, baseline, text);
    }

    private static String compactStation(String station) {
        String normalized = station.toLowerCase(Locale.ROOT);
        if (normalized.contains("maresme")) return "Maresme";
        if (normalized.contains("ciutadella")) return "Ciutadella";
        if (normalized.contains("guinardó") || normalized.contains("guinardo")) return "Guinardó";
        if (normalized.contains("passeig de gràcia") || normalized.contains("passeig de gracia")) return "Pg. Gràcia";
        return station;
    }
}
