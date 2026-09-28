package ca.amartian.quickdot.room;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Per-IP room-creation limits (spec §6): at most {@code room.max-concurrent-per-ip}
 * live rooms and {@code room.max-created-per-hour} creations in a sliding
 * hour. Keyed by the hashed IP, never the raw address.
 *
 * Creation is rare, so one lock around check-and-create keeps both limits
 * exact under concurrent requests from the same client.
 */
@Component
public class RoomCreationLimiter {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final Map<String, Deque<Instant>> creationsByKey = new HashMap<>();
    private final RoomRegistry roomRegistry;
    private final Clock clock;
    private final int maxConcurrent;
    private final int maxPerHour;

    public RoomCreationLimiter(
            RoomRegistry roomRegistry,
            Clock clock,
            @Value("${room.max-concurrent-per-ip:5}") int maxConcurrent,
            @Value("${room.max-created-per-hour:20}") int maxPerHour) {
        this.roomRegistry = roomRegistry;
        this.clock = clock;
        this.maxConcurrent = maxConcurrent;
        this.maxPerHour = maxPerHour;
    }

    public synchronized Room createRoom(String creatorKey) {
        if (roomRegistry.activeRoomsCreatedBy(creatorKey) >= maxConcurrent) {
            throw new RoomLimitException("Too many open rooms. Close one or wait for it to expire.");
        }
        Instant now = clock.instant();
        Deque<Instant> creations = creationsByKey.computeIfAbsent(creatorKey, key -> new ArrayDeque<>());
        prune(creations, now);
        if (creations.size() >= maxPerHour) {
            throw new RoomLimitException("Too many rooms created. Try again later.");
        }
        creations.addLast(now);
        return roomRegistry.createRoom(creatorKey);
    }

    /** Drops keys with no creations inside the window so the map doesn't grow forever. */
    @Scheduled(fixedDelayString = "PT10M")
    public synchronized void evictIdle() {
        Instant now = clock.instant();
        creationsByKey.values().removeIf(creations -> {
            prune(creations, now);
            return creations.isEmpty();
        });
    }

    synchronized int trackedKeys() {
        return creationsByKey.size();
    }

    private static void prune(Deque<Instant> creations, Instant now) {
        Instant cutoff = now.minus(WINDOW);
        while (!creations.isEmpty() && !creations.peekFirst().isAfter(cutoff)) {
            creations.pollFirst();
        }
    }
}
