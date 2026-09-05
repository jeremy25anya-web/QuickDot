package ca.amartian.quickdot.relay;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mutable state for one in-flight upload. Never buffers the file itself —
 * only counters and a running hash (spec §6: metadata logging needs a file
 * hash, never the content itself).
 */
public final class FileTransfer {

    private final String transferId;
    private final String roomCode;
    private final String senderId;
    private final String name;
    private final String mime;
    private final long declaredSize;

    private final AtomicLong bytesReceived = new AtomicLong();
    private final AtomicLong lastSequence = new AtomicLong(-1);
    private final AtomicBoolean firstChunkSeen = new AtomicBoolean(false);
    private final MessageDigest digest = newSha256();

    public FileTransfer(String transferId, String roomCode, String senderId, String name, String mime, long declaredSize) {
        this.transferId = transferId;
        this.roomCode = roomCode;
        this.senderId = senderId;
        this.name = name;
        this.mime = mime;
        this.declaredSize = declaredSize;
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
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

    public String mime() {
        return mime;
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

    /** Feeds the chunk into the running hash and returns the cumulative byte count. */
    public long addBytes(byte[] payload) {
        digest.update(payload);
        return bytesReceived.addAndGet(payload.length);
    }

    /** True the first time this is called for the transfer, false afterward — gates the one-time MIME sniff. */
    public boolean isFirstChunk() {
        return firstChunkSeen.compareAndSet(false, true);
    }

    /** Lowercase hex SHA-256 of all bytes seen so far — for metadata logging only, never the content. */
    public String digestHex() {
        return HexFormat.of().formatHex(digest.digest());
    }
}
