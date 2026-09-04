package ca.amartian.quickdot.relay;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Mutable state for one in-flight upload. Never buffers the file itself — only counters. */
public final class FileTransfer {

    private final String transferId;
    private final String roomCode;
    private final String senderId;
    private final String name;
    private final long declaredSize;

    private final AtomicLong bytesReceived = new AtomicLong();
    private final AtomicLong lastSequence = new AtomicLong(-1);
    private final AtomicBoolean firstChunkSeen = new AtomicBoolean(false);

    public FileTransfer(String transferId, String roomCode, String senderId, String name, long declaredSize) {
        this.transferId = transferId;
        this.roomCode = roomCode;
        this.senderId = senderId;
        this.name = name;
        this.declaredSize = declaredSize;
    }

    public String transferId() {
        return transferId;
    }

    public String roomCode() {
        return roomCode;
    }

    public String senderId() {
        return senderId;
    }

    public String name() {
        return name;
    }

    public long declaredSize() {
        return declaredSize;
    }

    public long bytesReceived() {
        return bytesReceived.get();
    }

    /** Returns false if this sequence isn't strictly increasing (out-of-order/duplicate/corrupt). */
    public boolean acceptSequence(long sequence) {
        return lastSequence.getAndUpdate(prev -> sequence > prev ? sequence : prev) < sequence;
    }

    public long addBytes(int count) {
        return bytesReceived.addAndGet(count);
    }

    /** True the first time this is called for the transfer, false afterward — gates the one-time MIME sniff. */
    public boolean isFirstChunk() {
        return firstChunkSeen.compareAndSet(false, true);
    }
}
