package com.ruih.framedeck;

/** Coordinate-based regressions independent of Android's gesture event plumbing. */
public final class VideoTransformTest {
    private static int checks;
    private static void equal(float actual, float expected) {
        checks++;
        if (Math.abs(actual - expected) > 0.001f)
            throw new AssertionError(actual + " != " + expected);
    }
    public static void main(String[] args) {
        VideoTransform t = new VideoTransform();
        t.zoomBy(2, 400, 300); // Ignore input before a video has a viewport.
        equal(t.scale(), 1);
        t.setGeometry(1000, 600, 1000, 562.5f);
        t.zoomBy(2, 750, 300);
        equal(t.scale(), 2); equal(t.x(), -250); equal(t.y(), 0);
        // A pixel originally at x=750 remains under the pinch focus at x=750.
        equal(500 + t.scale() * 250 + t.x(), 750);
        t.panBy(2000, -2000);
        equal(t.x(), 500); equal(t.y(), -262.5f);
        t.panBy(-4000, 4000);
        equal(t.x(), -500); equal(t.y(), 262.5f);
        t.zoomBy(0.001f, 750, 300);
        equal(t.scale(), 1); equal(t.x(), 0); equal(t.y(), 0);

        t.setGeometry(600, 1000, 600, 337.5f);
        t.zoomBy(2, 300, 500);
        t.panBy(1000, 1000);
        equal(t.scale(), 2); equal(t.x(), 300); equal(t.y(), 0);
        // Letterboxing stays centered until the video becomes taller than the viewport.
        t.zoomBy(100, 300, 500);
        equal(t.scale(), 6);
        t.panBy(10000, 10000);
        equal(t.x(), 1500); equal(t.y(), 512.5f);
        t.setGeometry(1000, 600, 1000, 562.5f);
        equal(t.scale(), 6); equal(t.x(), 1500); equal(t.y(), 512.5f);
        t.reset(); equal(t.scale(), 1); equal(t.x(), 0); equal(t.y(), 0);

        for (float bad : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY}) {
            t.zoomBy(bad, 500, 300); equal(t.scale(), 1);
        }
        t.zoomBy(2, Float.NaN, 300); equal(t.scale(), 1);
        t.panBy(Float.NaN, 20); equal(t.x(), 0); equal(t.y(), 0);
        try { t.setGeometry(0, 600, 1000, 562.5f); throw new AssertionError("invalid viewport accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        System.out.println("Passed " + checks + " preview zoom/pan checks.");
    }
}
