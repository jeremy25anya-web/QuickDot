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
 * Sliding-window per-connection text rate limit (spec §6: ≤30 messages per
 * 10s). Server-enforced regardless of what the client throttles itself.
 */
@Component
public class TextRateLimiter {

    private final ConcurrentHashMap<String, Deque<Instant>> timestampsByParticipant = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxMessages;
    private final Duration window;

    public TextRateLimiter(
            Clock clock,
            @Value("${relay.text.max-messages:30}") int maxMessages,
            @Value("${relay.text.window-seconds:10}") long windowSeconds) {
        this.clock = clock;
        this.maxMessages = maxMessages;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    /** Returns true if the message may be relayed, false if the sender is over the limit. */
    public boolean tryAcquire(String participantId) {
        Instant now = clock.instant();
        Deque<Instant> timestamps = timestampsByParticipant.computeIfAbsent(
                participantId, id -> new ConcurrentLinkedDeque<>());
        synchronized (timestamps) {
            Instant cutoff = now.minus(window);
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxMessages) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    public void clear(String participantId) {
        timestampsByParticipant.remove(participantId);
    }
}
