package ca.amartian.quickdot.relay;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sliding-window upload rate limit, scoped per **room** (spec §6: ≤10
 * uploads per room per minute) — unlike {@link TextRateLimiter}, which is
 * per connection.
 */
@Component
public class UploadRateLimiter {

    private final ConcurrentHashMap<String, Deque<Instant>> timestampsByRoom = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxUploads;
    private final Duration window;

    public UploadRateLimiter(
            Clock clock,
            @Value("${relay.upload.max-per-room:10}") int maxUploads,
            @Value("${relay.upload.window-seconds:60}") long windowSeconds) {
        this.clock = clock;
        this.maxUploads = maxUploads;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    public boolean tryAcquire(String roomCode) {
        Instant now = clock.instant();
        Deque<Instant> timestamps = timestampsByRoom.computeIfAbsent(roomCode, code -> new ConcurrentLinkedDeque<>());
        synchronized (timestamps) {
            Instant cutoff = now.minus(window);
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxUploads) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }
}
