package com.ruih.framedeck;

/** One playlist owner and independently closed playback/preparation leases. */
final class SharedResource implements AutoCloseable {
    private int references = 1;
    private boolean ownerClosed;
    private final Runnable dispose;
    SharedResource(Runnable dispose) { this.dispose = dispose; }
    synchronized Lease retain() {
        if (references == 0) throw new IllegalStateException("压缩包已关闭，请重新打开");
        references++;
        return new Lease(this);
    }
    private void release() {
        boolean last;
        synchronized (this) { last = --references == 0; }
        if (last) dispose.run();
    }
    @Override public void close() {
        synchronized (this) {
            if (ownerClosed) return;
            ownerClosed = true;
        }
        release();
    }
    static final class Lease implements AutoCloseable {
        private SharedResource resource;
        private Lease(SharedResource resource) { this.resource = resource; }
        @Override public void close() {
            SharedResource released;
            synchronized (this) { released = resource; resource = null; }
            if (released != null) released.release();
        }
    }
}
