package com.ruih.framedeck;

/** Standalone regression checks; run with a JDK, no Android device required. */
public final class FrameTimelineTest {
    private static int assertions;
    private static void equal(long actual, long expected) {
        assertions++;
        if (actual != expected) throw new AssertionError(actual + " != " + expected);
    }
    public static void main(String[] args) {
        FrameTimeline vfr = new FrameTimeline(new long[]{0, 40_000, 120_000, 150_000});
        equal(vfr.atTime(-100), 0);
        equal(vfr.atTime(0), 0);
        equal(vfr.atTime(39_999), 0);
        equal(vfr.atTime(40_000), 1);
        equal(vfr.atTime(100_000), 1);
        equal(vfr.atTime(120_000), 2);
        equal(vfr.atTime(Long.MAX_VALUE), 3);
        equal(vfr.clamp(-1), 0);
        equal(vfr.clamp(4), 3);
        FrameTimeline reordered = new FrameTimeline(new long[]{0, 120_000, 40_000, 80_000});
        equal(reordered.timeUs(1), 40_000);
        equal(reordered.timeUs(2), 80_000);
        FrameTimeline single = new FrameTimeline(new long[]{7000});
        equal(single.atTime(0), 0);
        equal(single.atTime(99999), 0);
        equal(single.clamp(1), 0);
        String formatted = FrameTimeline.format(3_723_004_000L);
        if (!"01:02:03.004".equals(formatted)) throw new AssertionError(formatted);
        assertions++;
        try { new FrameTimeline(new long[0]); throw new AssertionError("empty input accepted"); }
        catch (IllegalArgumentException expected) { assertions++; }
        try { new FrameTimeline(new long[]{0, 0}); throw new AssertionError("duplicate accepted"); }
        catch (IllegalArgumentException expected) { assertions++; }
        System.out.println("Passed " + assertions + " timeline checks.");
    }
}
