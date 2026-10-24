package com.ruih.framedeck;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

/** Owns a seekable provider fd, or a private copy for pipe/cloud providers. */
final class ArchiveSession implements AutoCloseable {
    final Uri uri;
    final FileDescriptor descriptor;
    private final FileInputStream stream;
    private final File privateArchive;
    private final File cacheDir;
    private final ZipArchive archive;
    private final SharedResource lifetime;

    static ArchiveSession open(Context context, Uri uri, BooleanSupplier cancelled,
            LongConsumer progress) throws IOException {
        File dir = new File(context.getCacheDir(), "archives");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("无法创建播放器临时目录");
        FileInputStream direct = null;
        try {
            ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r");
            if (pfd == null) throw new IOException("无法打开压缩包");
            direct = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
            FileChannel channel = direct.getChannel();
            long size = channel.size();
            if (size < 0 || channel.read(ByteBuffer.allocate(1), 0) < 0) throw new IOException("压缩包不可随机读取");
            ZipArchive.checkCancelled(cancelled);
            return new ArchiveSession(uri, direct, null, dir);
        } catch (IOException | RuntimeException e) {
            if (direct != null) try { direct.close(); } catch (IOException ignored) { }
            ZipArchive.checkCancelled(cancelled);
        }
        File local = TempStore.create(dir, "archive-", ".zip");
        boolean copied = false;
        try {
          try (InputStream in = context.getContentResolver().openInputStream(uri);
                FileOutputStream out = new FileOutputStream(local)) {
            if (in == null) throw new IOException("无法读取压缩包");
            byte[] buffer = new byte[256 * 1024];
            long copiedBytes = 0, lastProgress = 0;
            int n;
            while ((n = in.read(buffer)) != -1) {
                ZipArchive.checkCancelled(cancelled);
                if (dir.getUsableSpace() < n + 32L * 1024 * 1024)
                    throw new IOException("临时空间不足，无法读取此压缩包");
                out.write(buffer, 0, n); copiedBytes += n;
                if (copiedBytes - lastProgress >= 1024 * 1024) {
                    progress.accept(copiedBytes); lastProgress = copiedBytes;
                }
            }
            ZipArchive.checkCancelled(cancelled);
          }
          copied = true;
        } finally { if (!copied) TempStore.discard(local); }
        try { return new ArchiveSession(uri, new FileInputStream(local), local, dir); }
        catch (IOException e) { TempStore.discard(local); throw e; }
    }
    private ArchiveSession(Uri uri, FileInputStream stream, File privateArchive, File cacheDir)
            throws IOException {
        this.uri = uri; this.stream = stream; this.privateArchive = privateArchive; this.cacheDir = cacheDir;
        descriptor = stream.getFD(); archive = new ZipArchive(stream.getChannel());
        lifetime = new SharedResource(() -> {
            try { stream.close(); } catch (IOException ignored) { }
            if (privateArchive != null) TempStore.discard(privateArchive);
        });
    }
    List<ZipArchive.Entry> videos(BooleanSupplier cancelled) throws IOException { return archive.videos(cancelled); }
    MediaInput prepare(ZipArchive.Entry entry, BooleanSupplier cancelled, LongConsumer progress) throws IOException {
        if (entry.encrypted()) throw new IOException("当前版本暂不支持带密码的 ZIP 压缩包");
        if (entry.size == 0) throw new IOException("所选视频是空文件");
        try (SharedResource.Lease preparing = lifetime.retain()) {
            ZipArchive.checkCancelled(cancelled);
            File video = entry.method == 0 ? null : archive.cacheEntry(entry, cacheDir, cancelled, progress);
            try { return new MediaInput(this, entry, video); }
            catch (IOException | RuntimeException e) { if (video != null) TempStore.discard(video); throw e; }
        }
    }
    SharedResource.Lease retain() { return lifetime.retain(); }
    @Override public void close() { lifetime.close(); }
}
