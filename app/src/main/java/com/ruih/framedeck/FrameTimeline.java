package com.ruih.framedeck;

import java.util.Arrays;
import java.util.Locale;

/** Presentation order, including variable frame durations and reordered B-frames. */
public final class FrameTimeline {
    private final long[] ptsUs;

    public FrameTimeline(long[] times) {
        if (times.length == 0) throw new IllegalArgumentException("视频没有可读取的帧");
        ptsUs = times.clone();
        Arrays.sort(ptsUs);
        for (int i = 1; i < ptsUs.length; i++) {
            if (ptsUs[i] == ptsUs[i - 1]) {
                throw new IllegalArgumentException("视频存在重复帧时间戳，无法建立准确的帧索引");
            }
        }
    }

    public int size() { return ptsUs.length; }
    public int clamp(int index) { return Math.max(0, Math.min(ptsUs.length - 1, index)); }
    public long timeUs(int index) { return ptsUs[clamp(index)]; }

    /** Frame being displayed at this time; do not advance to a future frame. */
    public int atTime(long timeUs) {
        int found = Arrays.binarySearch(ptsUs, timeUs);
        return found >= 0 ? found : clamp(-found - 2);
    }

    public static String format(long us) {
        long ms = Math.max(0, us / 1000);
        return String.format(Locale.US, "%02d:%02d:%02d.%03d",
                ms / 3600000, ms / 60000 % 60, ms / 1000 % 60, ms % 1000);
    }
}
