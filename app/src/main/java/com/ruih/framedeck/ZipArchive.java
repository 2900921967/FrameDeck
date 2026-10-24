package com.ruih.framedeck;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import java.util.zip.CRC32;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/** ZIP central-directory reader. Offsets refer to the supplied seekable channel. */
final class ZipArchive {
    static final class Entry {
        final String name;
        final int flags, method;
        final long size, compressedSize, crc, localOffset, dataOffset;
        Entry(String name, int flags, int method, long size, long compressedSize,
                long crc, long localOffset, long dataOffset) {
            this.name = name; this.flags = flags; this.method = method; this.size = size;
            this.compressedSize = compressedSize; this.crc = crc;
            this.localOffset = localOffset; this.dataOffset = dataOffset;
        }
        boolean encrypted() { return (flags & 65) != 0; }
    }

    private final FileChannel channel;
    ZipArchive(FileChannel channel) { this.channel = channel; }

    List<Entry> videos(BooleanSupplier cancelled) throws IOException {
        long length = channel.size();
        if (length < 22) throw new IOException("不是有效的 ZIP 压缩包");
        int tailLength = (int) Math.min(length, 65557);
        ByteBuffer tail = read(length - tailLength, tailLength);
        int end = -1;
        for (int i = tailLength - 22; i >= 0; i--) {
            if (u32(tail, i) == 0x06054b50L && i + 22 + u16(tail, i + 20) == tailLength) {
                end = i; break;
            }
        }
        if (end < 0) throw new IOException("找不到 ZIP 目录；暂不支持分卷、RAR 或 7z");
        long endOffset = length - tailLength + end;
        if (u16(tail, end + 4) != 0 || u16(tail, end + 6) != 0)
            throw new IOException("暂不支持分卷 ZIP");
        long count = u16(tail, end + 10);
        long directorySize = u32(tail, end + 12), directoryOffset = u32(tail, end + 16);
        if (count == 65535 || directorySize == 0xffffffffL || directoryOffset == 0xffffffffL) {
            if (endOffset < 20) throw new IOException("ZIP64 目录缺失");
            ByteBuffer locator = read(endOffset - 20, 20);
            if (u32(locator, 0) != 0x07064b50L || u32(locator, 4) != 0 || u32(locator, 16) != 1)
                throw new IOException("无效或分卷的 ZIP64 压缩包");
            long zip64Offset = positiveLong(locator, 8);
            ByteBuffer zip64 = read(zip64Offset, 56);
            if (u32(zip64, 0) != 0x06064b50L || positiveLong(zip64, 4) < 44
                    || u32(zip64, 16) != 0 || u32(zip64, 20) != 0)
                throw new IOException("无效的 ZIP64 目录");
            count = positiveLong(zip64, 32);
            directorySize = positiveLong(zip64, 40);
            directoryOffset = positiveLong(zip64, 48);
        } else if (u16(tail, end + 8) != count) throw new IOException("暂不支持分卷 ZIP");
        if (count > 100_000) throw new IOException("压缩包文件数量超过 10 万个，请使用较小的压缩包");
        if (directoryOffset > endOffset || directorySize > endOffset - directoryOffset)
            throw new IOException("ZIP 目录超出文件范围");
        long cursor = directoryOffset, directoryEnd = directoryOffset + directorySize;
        List<Entry> videos = new ArrayList<>();
        for (long i = 0; i < count; i++) {
            checkCancelled(cancelled);
            if (cursor > directoryEnd - 46) throw new IOException("ZIP 目录被截断");
            ByteBuffer c = read(cursor, 46);
            if (u32(c, 0) != 0x02014b50L) throw new IOException("ZIP 目录格式错误");
            int flags = u16(c, 8), method = u16(c, 10);
            long crc = u32(c, 16), compressed = u32(c, 20), size = u32(c, 24);
            int nameLength = u16(c, 28), extraLength = u16(c, 30), commentLength = u16(c, 32);
            long localOffset = u32(c, 42);
            if (u16(c, 34) != 0) throw new IOException("暂不支持分卷 ZIP");
            if (nameLength > 4096) throw new IOException("ZIP 内文件名过长");
            long recordLength = 46L + nameLength + extraLength + commentLength;
            if (recordLength > directoryEnd - cursor) throw new IOException("ZIP 文件名或扩展字段被截断");
            ByteBuffer fields = read(cursor + 46, nameLength + extraLength);
            byte[] nameBytes = new byte[nameLength]; fields.get(nameBytes);
            ByteBuffer extra = fields.slice().order(ByteOrder.LITTLE_ENDIAN);
            boolean needs64 = size == 0xffffffffL || compressed == 0xffffffffL || localOffset == 0xffffffffL;
            boolean found64 = false;
            String unicodeName = null;
            while (extra.remaining() >= 4) {
                int tag = Short.toUnsignedInt(extra.getShort());
                int n = Short.toUnsignedInt(extra.getShort());
                if (n > extra.remaining()) throw new IOException("ZIP 扩展字段损坏");
                ByteBuffer value = extra.slice().order(ByteOrder.LITTLE_ENDIAN); value.limit(n);
                if (tag == 1 && needs64) {
                    if (size == 0xffffffffL) size = nextPositiveLong(value);
                    if (compressed == 0xffffffffL) compressed = nextPositiveLong(value);
                    if (localOffset == 0xffffffffL) localOffset = nextPositiveLong(value);
                    found64 = true;
                }
                if (tag == 0x7075 && n >= 5 && value.get(0) == 1) {
                    CRC32 nameCrc = new CRC32(); nameCrc.update(nameBytes);
                    if (u32(value, 1) == nameCrc.getValue()) {
                        byte[] utfName = new byte[n - 5]; value.position(5); value.get(utfName);
                        unicodeName = new String(utfName, StandardCharsets.UTF_8);
                    }
                }
                extra.position(extra.position() + n);
            }
            if (needs64 && !found64) throw new IOException("ZIP64 文件长度缺失");
            String name = unicodeName != null ? unicodeName : decodeName(nameBytes, flags);
            if (isVideo(name)) {
                if (videos.size() >= 2000) throw new IOException("压缩包中的视频超过 2000 个");
                ByteBuffer local = read(localOffset, 30);
                if (u32(local, 0) != 0x04034b50L || u16(local, 8) != method
                        || u16(local, 6) != flags) throw new IOException("ZIP 本地文件头与目录不一致");
                long offset = localOffset + 30L + u16(local, 26) + u16(local, 28);
                if (offset < 0 || offset > directoryOffset || compressed > directoryOffset - offset)
                    throw new IOException("ZIP 视频数据超出文件范围");
                if (method == 0 && size != compressed) throw new IOException("ZIP 存储条目的长度不一致");
                videos.add(new Entry(name, flags, method, size, compressed, crc, localOffset, offset));
            }
            cursor += recordLength;
        }
        return videos;
    }

    /** Only DEFLATE entries are spooled; STORED entries are read by fd/offset/length. */
    File cacheEntry(Entry entry, File cacheDir, BooleanSupplier cancelled, LongConsumer progress)
            throws IOException {
        if (entry.encrypted()) throw new IOException("此 ZIP 视频已加密；当前版本暂不支持密码压缩包");
        if (entry.method != 8) throw new IOException("此 ZIP 使用了暂不支持的压缩算法");
        if (entry.size > Math.max(0, cacheDir.getUsableSpace() - 32L * 1024 * 1024))
            throw new IOException("临时空间不足，无法准备所选视频");
        File output = TempStore.create(cacheDir, "video-", ".cache");
        Inflater inflater = new Inflater(true);
        boolean complete = false;
        try {
          try (InputStream raw = new RangeInputStream(channel, entry.dataOffset, entry.compressedSize);
                InputStream in = new InflaterInputStream(raw, inflater, 128 * 1024);
                FileOutputStream out = new FileOutputStream(output)) {
            CRC32 crc = new CRC32();
            byte[] buffer = new byte[128 * 1024];
            long written = 0, lastProgress = 0;
            int n;
            while ((n = in.read(buffer)) != -1) {
                checkCancelled(cancelled);
                if (n > entry.size - written) throw new IOException("ZIP 视频展开长度与目录不符");
                out.write(buffer, 0, n); crc.update(buffer, 0, n); written += n;
                if (written - lastProgress >= 1024 * 1024) { progress.accept(written); lastProgress = written; }
            }
            checkCancelled(cancelled);
            if (!inflater.finished() || written != entry.size || crc.getValue() != entry.crc)
                throw new IOException("ZIP 视频数据损坏，长度或 CRC 校验未通过");
            progress.accept(written);
          }
          complete = true;
          return output;
        } finally {
            inflater.end();
            if (!complete) TempStore.discard(output);
        }
    }

    private ByteBuffer read(long offset, int size) throws IOException {
        if (offset < 0 || size < 0 || offset > channel.size() - size) throw new IOException("ZIP 文件被截断");
        ByteBuffer buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        while (buffer.hasRemaining()) {
            int n = channel.read(buffer, offset + buffer.position());
            if (n <= 0) throw new IOException("无法读取 ZIP 目录");
        }
        buffer.flip(); return buffer;
    }
    private static int u16(ByteBuffer b, int offset) { return Short.toUnsignedInt(b.getShort(offset)); }
    private static long u32(ByteBuffer b, int offset) { return Integer.toUnsignedLong(b.getInt(offset)); }
    private static long positiveLong(ByteBuffer b, int offset) throws IOException {
        long result = b.getLong(offset);
        if (result < 0) throw new IOException("ZIP64 文件长度过大");
        return result;
    }
    private static long nextPositiveLong(ByteBuffer b) throws IOException {
        if (b.remaining() < 8) throw new IOException("ZIP64 扩展字段被截断");
        long result = b.getLong();
        if (result < 0) throw new IOException("ZIP64 文件长度过大");
        return result;
    }
    private static String decodeName(byte[] bytes, int flags) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            if ((flags & 2048) != 0) throw new IOException("ZIP 的 UTF-8 文件名损坏", e);
            return new String(bytes, Charset.forName("GB18030"));
        }
    }
    static boolean isVideo(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return !lower.endsWith("/") && lower.matches(".*\\.(mp4|m4v|mov|3gp|mkv|webm|avi|ts|mts|m2ts)$");
    }
    static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean()) throw new CancellationException();
    }
    private static final class RangeInputStream extends InputStream {
        private final FileChannel channel;
        private long position, remaining;
        RangeInputStream(FileChannel channel, long position, long length) {
            this.channel = channel; this.position = position; remaining = length;
        }
        @Override public int read() throws IOException {
            byte[] b = new byte[1]; return read(b, 0, 1) == -1 ? -1 : b[0] & 255;
        }
        @Override public int read(byte[] b, int offset, int length) throws IOException {
            if (length == 0) return 0;
            if (remaining == 0) return -1;
            int n = channel.read(ByteBuffer.wrap(b, offset, (int) Math.min(length, remaining)), position);
            if (n <= 0) throw new IOException("ZIP 视频数据被截断");
            position += n; remaining -= n; return n;
        }
    }
}
