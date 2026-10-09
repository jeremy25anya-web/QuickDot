package ca.amartian.quickdot.room;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory room. {@code expiresAt} is fixed at creation (spec §4) — it never
 * extends for late joiners.
 */
public final class Room {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String code;
    private final Instant createdAt;
    private final Instant expiresAt;
    /** Hashed creator IP, for the per-IP concurrent-room limit; null if not tracked. */
    private final String creatorKey;
    private final Map<String, Participant> participants = new ConcurrentHashMap<>();
    private final AtomicInteger itemCount = new AtomicInteger();
    /**
     * Handed only to whoever created the room (POST /api/rooms). Proves the
     * right to end the room for everyone; never broadcast.
     */
    private final String hostToken;

    /** Set when the room drops to zero participants; cleared if someone rejoins. */
    private volatile Instant emptiedAt;

    public Room(String code, Instant createdAt, Duration ttl) {
        this(code, createdAt, ttl, null);
    }

    public Room(String code, Instant createdAt, Duration ttl, String creatorKey) {
        this.code = code;
        this.createdAt = createdAt;
        this.expiresAt = createdAt.plus(ttl);
        this.creatorKey = creatorKey;
        byte[] token = new byte[16];
        RANDOM.nextBytes(token);
        this.hostToken = HexFormat.of().formatHex(token);
    }

    public String code() {
        return code;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public String creatorKey() {
        return creatorKey;
    }

    public String hostToken() {
        return hostToken;
    }

    /** Constant-time check, so response timing can't be used to guess the token. */
    public boolean isHostToken(String candidate) {
        return candidate != null && MessageDigest.isEqual(
                hostToken.getBytes(StandardCharsets.UTF_8), candidate.getBytes(StandardCharsets.UTF_8));
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public void addParticipant(Participant participant) {
        participants.put(participant.id(), participant);
        emptiedAt = null;
    }

    public void removeParticipant(String participantId, Instant now) {
        participants.remove(participantId);
        if (participants.isEmpty()) {
            emptiedAt = now;
        }
    }

    public int participantCount() {
        return participants.size();
    }

    public Collection<Participant> participants() {
        return participants.values();
    }

    public boolean isEmptyPastGrace(Instant now, Duration grace) {
        Instant snapshot = emptiedAt;
        return snapshot != null && !now.isBefore(snapshot.plus(grace));
    }

    public int itemCount() {
        return itemCount.get();
    }

    /** Atomically increments the item count unless it's already at {@code max} (spec §6). */
    public boolean tryIncrementItemCount(int max) {
        while (true) {
            int current = itemCount.get();
            if (current >= max) {
                return false;
            }
            if (itemCount.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }
}
