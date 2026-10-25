package com.ruih.framedeck;

/** Shared preview transform for the playing texture and the paused frame. */
final class VideoTransform {
    static final float MIN_SCALE = 1f, MAX_SCALE = 6f;
    private float viewportWidth, viewportHeight, contentWidth, contentHeight;
    private float scale = MIN_SCALE, x, y;

    float scale() { return scale; }
    float x() { return x; }
    float y() { return y; }

    void setGeometry(float width, float height, float fittedWidth, float fittedHeight) {
        if (!positive(width) || !positive(height) || !positive(fittedWidth) || !positive(fittedHeight))
            throw new IllegalArgumentException("Invalid preview geometry");
        viewportWidth = width; viewportHeight = height;
        contentWidth = fittedWidth; contentHeight = fittedHeight;
        constrain();
    }

    void zoomBy(float factor, float focusX, float focusY) {
        if (!positive(factor) || !Float.isFinite(focusX) || !Float.isFinite(focusY)
                || viewportWidth == 0 || viewportHeight == 0) return;
        float next = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * factor));
        float ratio = next / scale;
        float fx = focusX - viewportWidth / 2, fy = focusY - viewportHeight / 2;
        x = fx - (fx - x) * ratio;
        y = fy - (fy - y) * ratio;
        scale = next;
        constrain();
    }

    void panBy(float dx, float dy) {
        if (!Float.isFinite(dx) || !Float.isFinite(dy)) return;
        x += dx; y += dy;
        constrain();
    }

    void reset() { scale = MIN_SCALE; x = 0; y = 0; }

    private void constrain() {
        float limitX = Math.max(0, (contentWidth * scale - viewportWidth) / 2);
        float limitY = Math.max(0, (contentHeight * scale - viewportHeight) / 2);
        x = Math.max(-limitX, Math.min(limitX, x));
        y = Math.max(-limitY, Math.min(limitY, y));
    }

    private static boolean positive(float value) { return Float.isFinite(value) && value > 0; }
}
