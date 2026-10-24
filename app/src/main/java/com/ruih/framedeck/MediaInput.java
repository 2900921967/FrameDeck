package com.ruih.framedeck;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;

/** Shared fd region for the indexer, frame retriever and continuous player. */
final class MediaInput implements AutoCloseable {
    final Uri originalUri;
    final String displayName, archiveEntry;
    final long archiveOffset;
    private final SharedResource.Lease archiveLease;
    private final File cachedVideo;
    private final FileInputStream videoStream;
    private final FileDescriptor descriptor;
    private final long offset, length;
    private boolean closed;

    MediaInput(Uri uri, String displayName) {
        originalUri = uri; this.displayName = displayName;
        archiveEntry = null; archiveOffset = -1; archiveLease = null; cachedVideo = null;
        videoStream = null; descriptor = null; offset = length = 0;
    }
    MediaInput(ArchiveSession archive, ZipArchive.Entry entry, File cachedVideo) throws IOException {
        this.cachedVideo = cachedVideo;
        originalUri = archive.uri; archiveEntry = entry.name; archiveOffset = entry.localOffset;
        displayName = entry.name.substring(Math.max(entry.name.lastIndexOf('/'), entry.name.lastIndexOf('\\')) + 1);
        videoStream = cachedVideo == null ? null : new FileInputStream(cachedVideo);
        descriptor = videoStream == null ? archive.descriptor : videoStream.getFD();
        offset = cachedVideo == null ? entry.dataOffset : 0;
        length = entry.size;
        archiveLease = archive.retain();
    }
    void attach(Context context, MediaMetadataRetriever retriever) {
        if (descriptor == null) retriever.setDataSource(context, originalUri);
        else retriever.setDataSource(descriptor, offset, length);
    }
    void attach(Context context, MediaExtractor extractor) throws IOException {
        if (descriptor == null) extractor.setDataSource(context, originalUri, null);
        else extractor.setDataSource(descriptor, offset, length);
    }
    void attach(Context context, MediaPlayer player) throws IOException {
        if (descriptor == null) player.setDataSource(context, originalUri);
        else player.setDataSource(descriptor, offset, length);
    }
    boolean usesVideoCache() { return cachedVideo != null; }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        if (videoStream != null) try { videoStream.close(); } catch (IOException ignored) { }
        if (cachedVideo != null) TempStore.discard(cachedVideo);
        if (archiveLease != null) archiveLease.close();
    }
}
