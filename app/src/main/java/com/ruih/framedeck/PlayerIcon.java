package com.ruih.framedeck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Small vector icons drawn with the same stroke and accent as the player. */
final class PlayerIcon extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String kind;
    private boolean checked;
    PlayerIcon(Context context, String kind) {
        super(context); this.kind = kind;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    void setChecked(boolean checked) { this.checked = checked; invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        float scale = Math.min(getWidth(), getHeight()) / 28f;
        canvas.translate((getWidth() - 24 * scale) / 2, (getHeight() - 24 * scale) / 2);
        canvas.scale(scale, scale);
        paint.setColor(0xff8be4bc);
        paint.setStrokeWidth(1.65f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStyle(Paint.Style.STROKE);
        switch (kind) {
            case "video":
                canvas.drawRoundRect(new RectF(2.5f, 5, 21.5f, 19), 3, 3, paint);
                path(canvas, 10, 9, 15, 12, 10, 15, 10, 9); break;
            case "zip":
                path(canvas, 6, 2.5f, 15, 2.5f, 20, 7.5f, 20, 21, 4, 21, 4, 2.5f, 6, 2.5f);
                path(canvas, 15, 2.5f, 15, 8, 20, 8);
                for (int y = 10; y <= 16; y += 3) canvas.drawLine(10, y, 13, y, paint);
                canvas.drawRoundRect(new RectF(10, 18, 13, 20), 0.8f, 0.8f, paint); break;
            case "playlist":
                for (int y = 5; y <= 17; y += 6) {
                    canvas.drawCircle(4, y, 0.7f, paint);
                    canvas.drawLine(9, y, y == 17 ? 14 : 20, y, paint);
                }
                path(canvas, 18, 14, 22, 17, 18, 20); break;
            case "history":
                canvas.drawArc(new RectF(4, 4, 22, 22), -130, 310, false, paint);
                path(canvas, 3, 4, 3, 10, 9, 10);
                path(canvas, 13, 7, 13, 13, 17, 15); break;
            case "cache":
                path(canvas, 7, 7, 8, 21, 18, 21, 19, 7);
                canvas.drawLine(4, 6, 21, 6, paint);
                path(canvas, 9, 6, 9, 3, 16, 3, 16, 6);
                canvas.drawLine(11, 10, 11, 17, paint);
                canvas.drawLine(15, 10, 15, 17, paint); break;
            case "speed":
                canvas.drawArc(new RectF(3, 3, 21, 21), 145, 250, false, paint);
                canvas.drawLine(12, 12, 17, 7, paint);
                canvas.drawCircle(12, 12, 1.7f, paint);
                canvas.drawLine(6, 20, 18, 20, paint); break;
            case "jump":
                path(canvas, 5, 6, 12, 12, 5, 18);
                path(canvas, 12, 6, 19, 12, 12, 18);
                canvas.drawLine(21, 5, 21, 19, paint); break;
            case "radio":
                paint.setColor(checked ? 0xff8be4bc : 0xff64796b);
                canvas.drawCircle(12, 12, 8.5f, paint);
                if (checked) { paint.setStyle(Paint.Style.FILL); canvas.drawCircle(12, 12, 4.6f, paint); }
                break;
            case "chevron":
                paint.setColor(0xff718a7c); path(canvas, 9, 6, 15, 12, 9, 18); break;
            case "settings":
                canvas.drawCircle(12, 12, 6, paint);
                canvas.drawCircle(12, 12, 2.2f, paint);
                for (int i = 0; i < 8; i++) {
                    double angle = Math.PI * i / 4;
                    float x = (float) Math.cos(angle), y = (float) Math.sin(angle);
                    canvas.drawLine(12 + 7 * x, 12 + 7 * y, 12 + 10 * x, 12 + 10 * y, paint);
                }
                break;
            case "info":
                canvas.drawCircle(12, 12, 9, paint);
                canvas.drawPoint(12, 7, paint);
                canvas.drawLine(12, 11, 12, 17, paint); break;
            default:
                path(canvas, 3, 7, 9, 7, 11, 5, 20, 5, 21, 20, 3, 20, 3, 7);
                canvas.drawLine(3, 10, 20, 10, paint);
        }
        canvas.restore();
    }
    private void path(Canvas canvas, float... points) {
        Path path = new Path(); path.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
        canvas.drawPath(path, paint);
    }
}
