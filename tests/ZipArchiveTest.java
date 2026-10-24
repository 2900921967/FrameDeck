package com.ruih.framedeck;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;

/** Archive parsing/cache regression checks, independent of Android. */
public final class ZipArchiveTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("check " + checks); }
    private static long fileCount(Path directory) throws IOException {
        try (var files = Files.list(directory)) { return files.count(); }
    }
    public static void main(String[] args) throws Exception {
        Path fixtures = Path.of(args[0]);
        Path cache = Files.createTempDirectory("framedeck-cache-test-");
        byte[] expected = Files.readAllBytes(fixtures.resolve("expected.bin"));
        try (FileChannel channel = FileChannel.open(fixtures.resolve("mixed.zip"), StandardOpenOption.READ)) {
            ZipArchive zip = new ZipArchive(channel);
            List<ZipArchive.Entry> entries = zip.videos(() -> false);
            check(entries.size() == 3);
            ZipArchive.Entry stored = entries.get(0);
            check(stored.name.equals("folder/中文视频.MP4"));
            check(stored.method == 0 && stored.size == expected.length);
            ByteBuffer bytes = ByteBuffer.allocate(expected.length);
            while (bytes.hasRemaining()) channel.read(bytes, stored.dataOffset + bytes.position());
            check(Arrays.equals(expected, bytes.array()));
            check(fileCount(cache) == 0); // Direct STORED playback creates no video copy.
            File extracted = zip.cacheEntry(entries.get(1), cache.toFile(), () -> false, done -> { });
            check(Arrays.equals(expected, Files.readAllBytes(extracted.toPath())));
            check(TempStore.discard(extracted));
            File traversal = zip.cacheEntry(entries.get(2), cache.toFile(), () -> false, done -> { });
            check(traversal.toPath().getParent().equals(cache));
            check(traversal.getName().startsWith("video-"));
            check(TempStore.discard(traversal));
            try { zip.videos(() -> true); throw new AssertionError("cancel ignored"); }
            catch (CancellationException good) { checks++; }
            try { zip.cacheEntry(entries.get(1), cache.toFile(), () -> true, done -> { });
                throw new AssertionError("cache cancel ignored"); }
            catch (CancellationException good) { checks++; }
            check(fileCount(cache) == 0);
        }
        for (String file : new String[]{"zip64.zip", "gbk.zip"}) {
            try (FileChannel channel = FileChannel.open(fixtures.resolve(file), StandardOpenOption.READ)) {
                ZipArchive.Entry entry = new ZipArchive(channel).videos(() -> false).get(0);
                check(entry.name.contains("测试") || entry.name.equals("中文目录/视频.mp4"));
                check(entry.size == expected.length);
                ByteBuffer bytes = ByteBuffer.allocate(expected.length);
                while (bytes.hasRemaining()) channel.read(bytes, entry.dataOffset + bytes.position());
                check(Arrays.equals(bytes.array(), expected));
            }
        }
        for (String file : new String[]{"encrypted.zip", "bad-crc.zip", "bad-size.zip", "unsupported.zip"}) {
            try (FileChannel channel = FileChannel.open(fixtures.resolve(file), StandardOpenOption.READ)) {
                ZipArchive zip = new ZipArchive(channel);
                ZipArchive.Entry entry = zip.videos(() -> false).get(0);
                try { zip.cacheEntry(entry, cache.toFile(), () -> false, done -> { });
                    throw new AssertionError("bad entry accepted: " + file); }
                catch (IOException good) { checks++; }
                check(fileCount(cache) == 0);
            }
        }
        try (FileChannel channel = FileChannel.open(fixtures.resolve("truncated.zip"), StandardOpenOption.READ)) {
            try { new ZipArchive(channel).videos(() -> false); throw new AssertionError("truncated accepted"); }
            catch (IOException good) { checks++; }
        }
        try (FileChannel channel = FileChannel.open(fixtures.resolve("empty.zip"), StandardOpenOption.READ)) {
            check(new ZipArchive(channel).videos(() -> false).isEmpty());
        }
        check(ZipArchive.isVideo("nested/MOVIE.MKV"));
        check(!ZipArchive.isVideo("movie.mp4/"));
        check(!ZipArchive.isVideo("movie.mp4.txt"));
        Files.delete(cache);
        System.out.println("Passed " + checks + " archive checks.");
    }
}
