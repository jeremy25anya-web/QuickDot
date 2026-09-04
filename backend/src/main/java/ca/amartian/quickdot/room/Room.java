package ca.amartian.quickdot.room;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory room. {@code expiresAt} is fixed at creation (spec §4) — it never
 * extends for late joiners.
 */
public final class Room {

    private final String code;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final Map<String, Participant> participants = new ConcurrentHashMap<>();
    private final AtomicInteger itemCount = new AtomicInteger();

    /** Set when the room drops to zero participants; cleared if someone rejoins. */
    private volatile Instant emptiedAt;

    public Room(String code, Instant createdAt, Duration ttl) {
        this.code = code;
        this.createdAt = createdAt;
        this.expiresAt = createdAt.plus(ttl);
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
