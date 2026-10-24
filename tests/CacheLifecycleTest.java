package com.ruih.framedeck;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Real-file lifecycle checks for retained archives and cache cleanup. */
public final class CacheLifecycleTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("check " + checks); }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("framedeck-lifecycle-");
        File directory = root.toFile();
        File archive = TempStore.create(directory, "archive-", ".zip");
        Files.write(archive.toPath(), new byte[100]);
        SharedResource retained = new SharedResource(() -> TempStore.discard(archive));
        SharedResource.Lease playingA = retained.retain();
        File videoA = TempStore.create(directory, "video-", ".cache");
        Files.write(videoA.toPath(), new byte[200]);
        SharedResource.Lease preparingB = retained.retain();
        SharedResource.Lease playingB = retained.retain();
        File videoB = TempStore.create(directory, "video-", ".cache");
        Files.write(videoB.toPath(), new byte[300]);
        File stale = root.resolve("video-orphan.cache").toFile();
        Files.write(stale.toPath(), new byte[400]); // A previous process left this unprotected.
        File original = root.resolve("original.mp4").toFile();
        Files.write(original.toPath(), new byte[500]);
        TempStore.Snapshot before = TempStore.snapshot(directory);
        check(before.total == 1000 && before.count == 4);
        check(before.activeBytes == 600 && before.activeCount == 3);
        check(before.idleBytes == 400 && before.idleCount == 1);
        check(before.archiveBytes == 100 && before.videoBytes == 900);
        TempStore.Cleanup cleaned = TempStore.clearIdle(directory);
        check(cleaned.deleted == 1 && cleaned.freed == 400 && cleaned.failed == 0);
        check(cleaned.skipped == 3 && !stale.exists());
        check(archive.exists() && videoA.exists() && videoB.exists() && original.exists());

        // Switching closes only the old video. The retained ZIP is still available.
        check(TempStore.discard(videoA));
        playingA.close();
        preparingB.close();
        check(archive.exists());
        TempStore.Snapshot switched = TempStore.snapshot(directory);
        check(switched.total == 400 && switched.activeCount == 2 && switched.idleCount == 0);
        SharedResource.Lease playingC = retained.retain();
        check(TempStore.discard(videoB));
        playingB.close();
        check(archive.exists());
        retained.close(); // Playlist closed, but the current player still has a lease.
        retained.close();
        check(archive.exists());
        playingC.close();
        playingC.close();
        check(!archive.exists());
        check(TempStore.snapshot(directory).total == 0);
        check(original.exists());
        try { retained.retain(); throw new AssertionError("closed ZIP retained"); }
        catch (IllegalStateException expected) { checks++; }

        // A failed deletion is reported as a failure and does not claim freed bytes.
        File failingReal = root.resolve("video-failure.cache").toFile();
        Files.write(failingReal.toPath(), new byte[700]);
        File failingView = new File(failingReal.getAbsolutePath()) {
            @Override public boolean delete() { return false; }
        };
        File directoryView = new File(directory.getAbsolutePath()) {
            @Override public File[] listFiles() { return new File[]{failingView}; }
        };
        TempStore.Cleanup failed = TempStore.clearIdle(directoryView);
        check(failed.failed == 1 && failed.deleted == 0 && failed.freed == 0);
        check(TempStore.snapshot(directory).idleBytes == 700);
        check(TempStore.clearIdle(directory).deleted == 1);
        check(TempStore.snapshot(directory).count == 0);
        check(TempStore.discard(archive)); // Already deleted is a successful no-op.

        // Concurrent lease completion runs the resource cleanup exactly once.
        AtomicInteger disposed = new AtomicInteger();
        SharedResource concurrent = new SharedResource(disposed::incrementAndGet);
        var pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < 40; i++) {
            SharedResource.Lease lease = concurrent.retain();
            pool.submit(() -> { try { start.await(); } catch (InterruptedException e) { throw new RuntimeException(e); }
                lease.close(); lease.close(); });
        }
        concurrent.close();
        check(disposed.get() == 0);
        start.countDown();
        pool.shutdown();
        check(pool.awaitTermination(10, TimeUnit.SECONDS));
        check(disposed.get() == 1);
        Files.delete(original.toPath());
        Files.delete(root);
        System.out.println("Passed " + checks + " cache/playlist lifecycle checks.");
    }
}
