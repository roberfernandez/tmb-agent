package com.tmbagent.traineye;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import java.util.Locale;

/** Compact series-9000 front with live train, headway, refresh age and pickup ETA. */
final class TrainEyeView extends View {
    static final int WIDTH_DP = 204;
    static final int HEIGHT_DP = 68;
    static final int CLOSE_WIDTH_DP = 24;

    private static final int GOLD = 0xffffdf00;
    private static final int WHITE = 0xffffffff;
    private static final Typeface LABEL_FACE = Typeface.create("sans-serif-condensed", Typeface.BOLD);
    private final Bitmap background;
    private final RectF frame = new RectF(0, 0, WIDTH_DP, HEIGHT_DP);
    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);

    private String train = "";
    private String station = "";
    private String track = "";
    private String age = "";
    private String stationLabel = "";
    private String countdown = "--";
    private String headway = "--";

    TrainEyeView(Context context) {
        super(context);
        background = BitmapFactory.decodeResource(getResources(), R.drawable.train_front_9000);
    }

    void setCountdown(String value) {
        value = displayTime(value);
        if (!countdown.equals(value)) { countdown = value; updateDescription(); invalidate(); }
    }

    void setHeadway(String value) {
        value = value == null ? "--" : value;
        if (!headway.equals(value)) { headway = value; updateDescription(); invalidate(); }
    }

    void setStatus(String train, String station, String track, String age) {
        if (train == null) train = "";
        if (station == null || station.isEmpty()) station = "—";
        if (track == null || track.isEmpty()) track = "—";
        if (age == null || age.isEmpty()) age = "—";
        if (this.train.equals(train) && this.station.equals(station)
            && this.track.equals(track) && this.age.equals(age)) return;
        if (!this.station.equals(station)) stationLabel = compactStation(station);
        this.train = train;
        this.station = station;
        this.track = track;
        this.age = age;
        updateDescription();
        invalidate();
    }

    private void updateDescription() {
        setContentDescription("Tren " + train + " · " + station + " · via " + track
            + ". Temps amb el tren anterior: " + headway + ". Arribada: " + countdown
            + ". Actualització: " + age + ". Toca per obrir el visor; la creu atura el seguiment.");
    }

    boolean isCloseHit(float x, float y) {
        return x >= getWidth() * (WIDTH_DP - CLOSE_WIDTH_DP) / WIDTH_DP
            && y <= getHeight() * 0.43f;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int save = canvas.save();
        canvas.scale(getWidth() / (float) WIDTH_DP, getHeight() / (float) HEIGHT_DP);
        canvas.drawBitmap(background, null, frame, imagePaint);

        text.setTypeface(LABEL_FACE);
        text.setColor(GOLD);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(14.5f);
        String label = TextUtils.ellipsize(stationLabel, text, 119f, TextUtils.TruncateAt.END).toString();
        drawText(canvas, label, 14f, 17f);
        ink.setStyle(Paint.Style.FILL);
        ink.setColor(GOLD);
        canvas.drawCircle(138f, 17f, 1.35f, ink);

        text.setColor(WHITE);
        text.setTextAlign(Paint.Align.CENTER);
        drawMetric(canvas, "Via " + track, 158f, 17f, 12.5f, 34f);
        ink.setColor(GOLD);
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeWidth(2.1f);
        ink.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(185f, 13.5f, 191f, 20f, ink);
        canvas.drawLine(191f, 13.5f, 185f, 20f, ink);

        ink.setColor(0xffa0a0a0);
        ink.setStrokeWidth(0.6f);
        ink.setStrokeCap(Paint.Cap.BUTT);
        canvas.drawLine(11.5f, 26f, 193f, 26f, ink);
        for (float x : new float[]{61f, 108f, 143f}) canvas.drawLine(x, 29f, x, 50.5f, ink);

        text.setColor(GOLD);
        drawMetric(canvas, train, 36.5f, 40.5f, 27f, 45f);

        // The white hat always belongs to T.ANT, never to the arrival countdown.
        ink.setColor(WHITE);
        ink.setStrokeWidth(1.9f);
        ink.setStrokeCap(Paint.Cap.ROUND);
        ink.setStrokeJoin(Paint.Join.ROUND);
        Path hat = new Path();
        hat.moveTo(80f, 33.3f);
        hat.lineTo(84.5f, 29.5f);
        hat.lineTo(89f, 33.3f);
        canvas.drawPath(hat, ink);
        text.setColor(WHITE);
        drawMetric(canvas, headway, 84.5f, 43.7f, 15f, 41f);

        drawRefresh(canvas, 117f, 40.5f);
        drawMetric(canvas, age, 132f, 40.5f, 10f, 17f);
        drawMetric(canvas, countdown, 169f, 40.5f, 18.5f, 45f);
        canvas.restoreToCount(save);
    }

    private void drawRefresh(Canvas canvas, float x, float y) {
        ink.setColor(WHITE);
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeWidth(1.5f);
        ink.setStrokeCap(Paint.Cap.ROUND);
        RectF ring = new RectF(x - 4.2f, y - 4.2f, x + 4.2f, y + 4.2f);
        canvas.drawArc(ring, 208, 142, false, ink);
        canvas.drawArc(ring, 28, 142, false, ink);
        Path arrows = new Path();
        arrows.moveTo(x + 4.2f, y - 3.5f);
        arrows.lineTo(x + 4.2f, y - 0.8f);
        arrows.lineTo(x + 1.5f, y - 0.8f);
        arrows.moveTo(x - 4.2f, y + 3.5f);
        arrows.lineTo(x - 4.2f, y + 0.8f);
        arrows.lineTo(x - 1.5f, y + 0.8f);
        canvas.drawPath(arrows, ink);
    }

    private void drawMetric(Canvas canvas, String label, float x, float y, float size, float maxWidth) {
        text.setTextSize(size);
        float width = text.measureText(label);
        if (width > maxWidth) text.setTextSize(size * maxWidth / width);
        drawText(canvas, label, x, y);
    }

    private void drawText(Canvas canvas, String label, float x, float y) {
        canvas.drawText(label, x, y - (text.ascent() + text.descent()) / 2f, text);
    }

    private static String displayTime(String value) {
        return value == null ? "--" : value.replace("''", "″").replace("'", "′");
    }

    private static String compactStation(String station) {
        String normalized = station.toLowerCase(Locale.ROOT);
        if (normalized.contains("maresme")) return "Maresme · Fòrum";
        if (normalized.contains("ciutadella")) return "Ciutadella";
        if (normalized.contains("guinardó") || normalized.contains("guinardo")) return "Guinardó";
        if (normalized.contains("passeig de gràcia") || normalized.contains("passeig de gracia")) return "Pg. Gràcia";
        if (normalized.equals("trinitat nova")) return "Trinitat N.";
        if (normalized.equals("sense connexió")) return "Desconn.";
        return station;
    }
}
