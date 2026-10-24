package com.ruih.framedeck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** A quiet, text-free player frame shown only before a video is loaded. */
final class EmptyPlayerView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private int topInset, bottomInset;
    EmptyPlayerView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        setContentDescription("打开视频或 ZIP 压缩包");
        setFocusable(true);
    }
    void setFrameInsets(int top, int bottom) {
        if (topInset != top || bottomInset != bottom) {
            topInset = top; bottomInset = bottom; invalidate();
        }
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float top = Math.max(24 * density, topInset);
        float bottom = getHeight() - Math.max(24 * density, bottomInset);
        if (bottom - top < 80 * density) { top = 24 * density; bottom = getHeight() - 24 * density; }
        RectF frame = new RectF(18 * density, top, getWidth() - 18 * density, bottom);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, top, 0, bottom,
                0xff101a15, 0xff090f0c, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(frame, 22 * density, 22 * density, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xff2c4135);
        paint.setStrokeWidth(1.2f * density);
        canvas.drawRoundRect(frame, 22 * density, 22 * density, paint);
        float x = frame.centerX(), y = frame.centerY();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xff1a2a21);
        canvas.drawCircle(x, y, 30 * density, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xff3b5947);
        paint.setStrokeWidth(density);
        canvas.drawCircle(x, y, 30 * density, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xff87bfa0);
        Path play = new Path();
        play.moveTo(x - 6 * density, y - 10 * density);
        play.lineTo(x + 11 * density, y);
        play.lineTo(x - 6 * density, y + 10 * density);
        play.close();
        canvas.drawPath(play, paint);
    }
}
