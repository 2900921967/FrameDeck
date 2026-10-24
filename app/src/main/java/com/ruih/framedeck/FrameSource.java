package com.ruih.framedeck;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/** Owned by the decode executor; never call from the UI or another thread. */
final class FrameSource implements AutoCloseable {
    final FrameTimeline timeline;
    final long durationUs;
    final int width, height;
    private final MediaMetadataRetriever retriever;
    private final MediaInput input;

    FrameSource(Context context, MediaInput input, BooleanSupplier cancelled, IntConsumer progress)
            throws IOException {
        this.input = input;
        retriever = new MediaMetadataRetriever();
        MediaExtractor extractor = new MediaExtractor();
        try {
            input.attach(context, retriever);
            int frameCount = metadataInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT);
            if (frameCount <= 0) throw new IOException(
                    "此视频未提供逐帧索引。请先转为 H.264 编码的 MP4 文件。");
            if (frameCount > 5_000_000) throw new IOException("视频超过 500 万帧，请先裁剪后打开");
            durationUs = Math.max(1, metadataLong(MediaMetadataRetriever.METADATA_KEY_DURATION) * 1000);
            int w = metadataInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            int h = metadataInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            int rotation = metadataInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            width = rotation == 90 || rotation == 270 ? h : w;
            height = rotation == 90 || rotation == 270 ? w : h;

            input.attach(context, extractor);
            int videoTrack = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                String mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/")) { videoTrack = i; break; }
            }
            if (videoTrack < 0) throw new IOException("文件中没有视频轨道");
            extractor.selectTrack(videoTrack);
            long[] times = new long[Math.min(frameCount, 16384)];
            int count = 0;
            while (extractor.getSampleTime() >= 0) {
                if (cancelled.getAsBoolean()) throw new CancellationException();
                if ((extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_ENCRYPTED) != 0)
                    throw new IOException("不支持受 DRM 保护的视频");
                if (count == 5_000_000) throw new IOException("视频过长，请先裁剪");
                if (count == times.length) times = Arrays.copyOf(times, Math.min(5_000_000, times.length * 2));
                times[count++] = extractor.getSampleTime();
                if (count % 4096 == 0) progress.accept(Math.min(99, (int) (100L * count / frameCount)));
                if (!extractor.advance()) break;
            }
            if (count != frameCount) throw new IOException(
                    "容器帧数与轨道索引不一致，暂不支持此文件。请转为标准 MP4 后重试。");
            timeline = new FrameTimeline(Arrays.copyOf(times, count));
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            close();
            throw e;
        } finally { extractor.release(); }
    }

    Bitmap frame(int index) throws IOException {
        MediaMetadataRetriever.BitmapParams params = new MediaMetadataRetriever.BitmapParams();
        params.setPreferredConfig(Bitmap.Config.ARGB_8888);
        Bitmap bitmap = retriever.getFrameAtIndex(timeline.clamp(index), params);
        if (bitmap == null) throw new IOException("设备解码器未能读取此帧，请尝试 H.264 MP4 视频");
        return bitmap;
    }

    private int metadataInt(int key) { return (int) metadataLong(key); }
    private long metadataLong(int key) {
        try { return Long.parseLong(retriever.extractMetadata(key)); }
        catch (NumberFormatException e) { return 0; }
    }

    @Override public void close() {
        try { retriever.release(); } catch (Exception ignored) { }
        input.close();
    }
}
