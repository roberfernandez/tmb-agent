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

/** The floating eye, drawn in fixed proportions so updates never move the close button. */
final class TrainEyeView extends View {
    static final int WIDTH_DP = 132;
    static final int HEIGHT_DP = 48;
    static final int CLOSE_WIDTH_DP = 30;

    private static final int GOLD = 0xfff4d000;
    private static final int INK = 0xff111e30;
    private static final float CENTER_X = 55.5f;
    private static final float PUPIL_Y = 29f;
    private static final float PUPIL_RADIUS = 11.8f;
    private static final float STATION_WIDTH = 83f;
    private static final float STATION_SIZE = 10f;
    private static final Typeface LABEL_FACE = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Typeface STATION_FACE = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
    private static final Typeface TRAIN_FACE = Typeface.create(STATION_FACE, Typeface.BOLD);

    private final Path outline = new Path();
    private final Path lashes = new Path();
    private final Path stationArc = new Path();
    private final float stationArcLength;
    private final RectF irisBounds = new RectF(CENTER_X - 23f, 6f, CENTER_X + 23f, 42.4f);
    private final Paint surface = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = stroke(GOLD, 1.4f);
    private final Paint lashStroke = stroke(GOLD, 1.05f);
    private final Paint irisRim = stroke(0xffc8a04a, 0.85f);
    private final Paint iris = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pupil = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);

    private String train = "";
    private String station = "";
    private String track = "";
    private String age = "";
    private String stationLabel = "";

    TrainEyeView(Context context) {
        super(context);
        outline.moveTo(2.5f, 26.5f);
        outline.cubicTo(17f, 11f, 34f, 4.5f, CENTER_X, 4.5f);
        outline.cubicTo(78f, 4.5f, 99f, 11.5f, 119f, 24f);
        float upperLength = new PathMeasure(outline, false).getLength();
        outline.cubicTo(100f, 36.5f, 80f, 43.5f, CENTER_X, 43.5f);
        outline.cubicTo(31f, 43.5f, 13f, 36.5f, 2.5f, 26.5f);
        outline.close();
        PathMeasure eyelids = new PathMeasure(outline, false);
        float lowerLength = eyelids.getLength() - upperLength;
        addLashes(eyelids, 0.20f * upperLength, 0.58f * upperLength, 7, 2.55f);
        addLashes(eyelids, upperLength + 0.24f * lowerLength, 0.54f * lowerLength, 5, 1.75f);

        // Give the station name almost the full upper eyelid, below the top edge.
        // The arc is intentionally wider than the iris without enlarging the overlay.
        stationArc.moveTo(14f, 22.5f);
        stationArc.cubicTo(29f, 9.5f, 83f, 9.5f, 104f, 22.5f);
        stationArcLength = new PathMeasure(stationArc, false).getLength();

        surface.setShader(new LinearGradient(0, 4.5f, 0, 43.5f, 0xff192b40, INK, Shader.TileMode.CLAMP));
        iris.setShader(new LinearGradient(0, irisBounds.top, 0, irisBounds.bottom,
            0xff745420, 0xff45301c, Shader.TileMode.CLAMP));
        pupil.setColor(0xff0b1523);
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
        text.setColor(0xffdce5ef);
        drawMetric(canvas, "v." + track, 19.5f, 27.5f, 10.5f, 21f);
        drawMetric(canvas, age, 91.5f, 27.5f, 10.5f, 20f);
        text.setTypeface(TRAIN_FACE);
        text.setColor(0xffffdc4f);
        drawMetric(canvas, train, CENTER_X, PUPIL_Y, 15f, 20f);
        canvas.restoreToCount(save);
    }

    private void drawStation(Canvas canvas) {
        text.setTextAlign(Paint.Align.LEFT);
        text.setTypeface(STATION_FACE);
        text.setColor(0xfffff0bb);
        text.setTextSize(STATION_SIZE);
        float width = text.measureText(stationLabel);
        if (width > STATION_WIDTH) {
            text.setTextSize(Math.max(8.4f, STATION_SIZE * STATION_WIDTH / width));
        }
        String label = TextUtils.ellipsize(stationLabel, text, STATION_WIDTH, TextUtils.TruncateAt.END).toString();
        float offset = (stationArcLength - text.measureText(label)) / 2f;
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
