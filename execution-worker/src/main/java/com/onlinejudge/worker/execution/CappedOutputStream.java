package com.onlinejudge.worker.execution;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

/**
 * An output sink that silently drops bytes past a hard cap (PRD §18 step 4: an output
 * bomb must not exhaust worker memory). Dropping is safe: a truncated run is treated as
 * a presentation failure by the verdict engine.
 */
final class CappedOutputStream extends OutputStream {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final int maxBytes;
    private boolean truncated;

    CappedOutputStream(int maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    public synchronized void write(int value) {
        if (buffer.size() < maxBytes) {
            buffer.write(value);
        } else {
            truncated = true;
        }
    }

    @Override
    public synchronized void write(byte[] bytes, int offset, int length) {
        int room = maxBytes - buffer.size();
        if (room <= 0) {
            truncated = true;
            return;
        }
        int writable = Math.min(room, length);
        buffer.write(bytes, offset, writable);
        if (writable < length) {
            truncated = true;
        }
    }

    synchronized boolean isTruncated() {
        return truncated;
    }

    synchronized String asString(java.nio.charset.Charset charset) {
        return buffer.toString(charset);
    }
}
