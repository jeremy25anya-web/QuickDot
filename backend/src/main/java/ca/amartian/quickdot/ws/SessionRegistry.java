package ca.amartian.quickdot.ws;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * Live WebSocket sessions by participant id, plus last-pong timestamps for
 * the idle-timeout sweep (spec §6: ping/pong keepalive, drop after ~2 missed
 * pongs). Separate from {@code room.RoomRegistry} — the room domain stays
 * transport-agnostic.
 */
@Component
public class SessionRegistry {

    private record Entry(WebSocketSession session, Instant lastPongAt) {
    }

    private final ConcurrentHashMap<String, Entry> byParticipantId = new ConcurrentHashMap<>();
    private final Clock clock;

    public SessionRegistry(Clock clock) {
        this.clock = clock;
    }

    public void register(String participantId, WebSocketSession session) {
        byParticipantId.put(participantId, new Entry(session, clock.instant()));
    }

    public void unregister(String participantId) {
        byParticipantId.remove(participantId);
    }

    public Optional<WebSocketSession> find(String participantId) {
        Entry entry = byParticipantId.get(participantId);
        return Optional.ofNullable(entry).map(Entry::session);
    }

    public void markPong(String participantId) {
        byParticipantId.computeIfPresent(participantId, (id, entry) -> new Entry(entry.session(), clock.instant()));
    }

    public Set<String> allParticipantIds() {
        return byParticipantId.keySet();
    }

    public boolean isStale(String participantId, Duration staleAfter) {
        Entry entry = byParticipantId.get(participantId);
        return entry == null || clock.instant().isAfter(entry.lastPongAt().plus(staleAfter));
    }
}
