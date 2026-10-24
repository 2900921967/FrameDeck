package com.ruih.framedeck;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Tracks files being prepared or used; cleanup never deletes protected files. */
final class TempStore {
    private static final Set<String> ACTIVE = new HashSet<>();
    static synchronized File create(File directory, String prefix, String suffix) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建临时目录");
        File file = File.createTempFile(prefix, suffix, directory);
        ACTIVE.add(file.getAbsolutePath());
        return file;
    }
    static synchronized boolean discard(File file) {
        ACTIVE.remove(file.getAbsolutePath());
        return !file.exists() || file.delete();
    }
    static synchronized Snapshot snapshot(File directory) {
        Snapshot result = new Snapshot();
        File[] files = directory.listFiles();
        if (files == null) { result.unreadable = directory.exists(); return result; }
        for (File file : files) {
            if (!owned(file)) continue;
            long length = file.length();
            result.count++;
            result.total += length;
            if (file.getName().startsWith("video-")) result.videoBytes += length;
            else result.archiveBytes += length;
            if (ACTIVE.contains(file.getAbsolutePath())) {
                result.activeCount++; result.activeBytes += length;
            } else { result.idleCount++; result.idleBytes += length; }
        }
        return result;
    }
    static synchronized Cleanup clearIdle(File directory) {
        Cleanup result = new Cleanup();
        File[] files = directory.listFiles();
        if (files == null) { result.failed = directory.exists() ? 1 : 0; return result; }
        for (File file : files) {
            if (!owned(file)) continue;
            if (ACTIVE.contains(file.getAbsolutePath())) { result.skipped++; continue; }
            long length = file.length();
            if (file.delete()) { result.deleted++; result.freed += length; }
            else result.failed++;
        }
        return result;
    }
    private static boolean owned(File file) {
        String name = file.getName();
        return file.isFile() && ((name.startsWith("video-") && name.endsWith(".cache"))
                || (name.startsWith("archive-") && name.endsWith(".zip")));
    }
    static final class Snapshot {
        long total, activeBytes, idleBytes, videoBytes, archiveBytes;
        int count, activeCount, idleCount;
        boolean unreadable;
    }
    static final class Cleanup {
        long freed;
        int deleted, skipped, failed;
    }
}
