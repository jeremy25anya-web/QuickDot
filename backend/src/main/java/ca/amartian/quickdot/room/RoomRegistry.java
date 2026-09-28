package ca.amartian.quickdot.room;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Single-instance in-memory room registry (spec §4). No persistence, no
 * cross-instance sync — v1 is explicitly one process.
 */
@Service
public class RoomRegistry {

    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();
    private final RoomCodeGenerator codeGenerator;
    private final Clock clock;
    private final Duration ttl;
    private final Duration emptyGrace;
    private final int maxParticipants;

    public RoomRegistry(
            RoomCodeGenerator codeGenerator,
            Clock clock,
            @Value("${room.ttl-minutes:30}") long ttlMinutes,
            @Value("${room.empty-grace-seconds:30}") long emptyGraceSeconds,
            @Value("${room.max-participants:8}") int maxParticipants) {
        this.codeGenerator = codeGenerator;
        this.clock = clock;
        this.ttl = Duration.ofMinutes(ttlMinutes);
        this.emptyGrace = Duration.ofSeconds(emptyGraceSeconds);
        this.maxParticipants = maxParticipants;
    }

    public Room createRoom() {
        return createRoom(null);
    }

    public Room createRoom(String creatorKey) {
        Room room;
        String code;
        do {
            code = codeGenerator.generate();
            room = new Room(code, clock.instant(), ttl, creatorKey);
        } while (rooms.putIfAbsent(code, room) != null);
        return room;
    }

    /** Rooms created by {@code creatorKey} that haven't expired or been swept. */
    public long activeRoomsCreatedBy(String creatorKey) {
        Instant now = clock.instant();
        return rooms.values().stream()
                .filter(room -> creatorKey.equals(room.creatorKey()) && !room.isExpired(now))
                .count();
    }

    public Optional<Room> findActiveRoom(String code) {
        Room room = rooms.get(code);
        if (room == null || room.isExpired(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(room);
    }

    public Participant join(String code, String name, String ip) {
        Room room = findActiveRoom(code).orElseThrow(() -> new RoomNotFoundException(code));
        if (room.participantCount() >= maxParticipants) {
            throw new RoomFullException(code);
        }
        Participant participant = new Participant(UUID.randomUUID().toString(), name, ip);
        room.addParticipant(participant);
        return participant;
    }

    public void leave(String code, String participantId) {
        Room room = rooms.get(code);
        if (room != null) {
            room.removeParticipant(participantId, clock.instant());
        }
    }

    /** Evicts expired rooms and rooms empty past the grace window. */
    @Scheduled(fixedDelayString = "${room.sweep-interval-ms:10000}")
    public void sweep() {
        Instant now = clock.instant();
        rooms.values().removeIf(room -> room.isExpired(now) || room.isEmptyPastGrace(now, emptyGrace));
    }

    /** Test/diagnostic helper — not part of the room protocol. */
    int size() {
        return rooms.size();
    }
}
