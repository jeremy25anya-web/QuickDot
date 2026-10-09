package ca.amartian.quickdot.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoomRegistryTest {

    private MutableClock clock;
    private RoomRegistry registry;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        registry = new RoomRegistry(new RoomCodeGenerator(), clock, 30, 30, 8);
    }

    @Test
    void createRoomIsFindableAndHasCodeFromAlphabet() {
        Room room = registry.createRoom();

        assertThat(room.code()).hasSize(6);
        assertThat(room.code()).matches("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}");
        assertThat(registry.findActiveRoom(room.code())).contains(room);
    }

    @Test
    void expiresAtIsFixedThirtyMinutesFromCreation() {
        Room room = registry.createRoom();

        assertThat(room.expiresAt()).isEqualTo(room.createdAt().plus(Duration.ofMinutes(30)));
    }

    @Test
    void joinAddsParticipantToRoom() {
        Room room = registry.createRoom();

        Participant participant = registry.join(room.code(), "Nimble Otter", "203.0.113.5");

        assertThat(room.participantCount()).isEqualTo(1);
        assertThat(room.participants()).contains(participant);
    }

    @Test
    void joinUnknownCodeThrows() {
        assertThatThrownBy(() -> registry.join("ZZZZZZ", "Nimble Otter", "203.0.113.5"))
                .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void joinAtCapacityThrows() {
        Room room = registry.createRoom();
        for (int i = 0; i < 8; i++) {
            registry.join(room.code(), "Nimble Otter", "203.0.113.5");
        }

        assertThatThrownBy(() -> registry.join(room.code(), "Nimble Otter", "203.0.113.5"))
                .isInstanceOf(RoomFullException.class);
    }

    @Test
    void roomNotFoundAfterExpiry() {
        Room room = registry.createRoom();

        clock.advance(Duration.ofMinutes(30));

        assertThat(registry.findActiveRoom(room.code())).isEmpty();
        assertThatThrownBy(() -> registry.join(room.code(), "Nimble Otter", "203.0.113.5"))
                .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void sweepEvictsExpiredRooms() {
        registry.createRoom();
        clock.advance(Duration.ofMinutes(31));

        registry.sweep();

        assertThat(registry.size()).isZero();
    }

    @Test
    void sweepEvictsRoomEmptyPastGraceWindow() {
        Room room = registry.createRoom();
        Participant participant = registry.join(room.code(), "Nimble Otter", "203.0.113.5");
        registry.leave(room.code(), participant.id());

        clock.advance(Duration.ofSeconds(31));
        registry.sweep();

        assertThat(registry.size()).isZero();
    }

    @Test
    void sweepKeepsRoomThatRejoinedWithinGraceWindow() {
        Room room = registry.createRoom();
        Participant participant = registry.join(room.code(), "Nimble Otter", "203.0.113.5");
        registry.leave(room.code(), participant.id());

        clock.advance(Duration.ofSeconds(10));
        registry.join(room.code(), "Nimble Otter", "203.0.113.6");
        clock.advance(Duration.ofSeconds(25));
        registry.sweep();

        assertThat(registry.size()).isEqualTo(1);
    }

    /** Fixed-then-advanceable clock for deterministic TTL/sweep tests. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void endRemovesRoomImmediately() {
        Room room = registry.createRoom();

        registry.end(room.code());

        assertThat(registry.findActiveRoom(room.code())).isEmpty();
    }

    @Test
    void hostTokenIsUniquePerRoomAndCheckedExactly() {
        Room a = registry.createRoom();
        Room b = registry.createRoom();

        assertThat(a.hostToken()).hasSize(32).isNotEqualTo(b.hostToken());
        assertThat(a.isHostToken(a.hostToken())).isTrue();
        assertThat(a.isHostToken(b.hostToken())).isFalse();
        assertThat(a.isHostToken(null)).isFalse();
        assertThat(a.isHostToken("")).isFalse();
    }
}
