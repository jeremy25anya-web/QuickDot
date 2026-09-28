package ca.amartian.quickdot.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoomCreationLimiterTest {

    private MutableClock clock;
    private RoomRegistry registry;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        registry = new RoomRegistry(new RoomCodeGenerator(), clock, 30, 30, 8);
    }

    @Test
    void sixthConcurrentRoomFromSameIpIsRejected() {
        RoomCreationLimiter limiter = new RoomCreationLimiter(registry, clock, 5, 20);
        for (int i = 0; i < 5; i++) {
            limiter.createRoom("ip-a");
        }

        assertThatThrownBy(() -> limiter.createRoom("ip-a"))
                .isInstanceOf(RoomLimitException.class)
                .hasMessageContaining("open rooms");
        assertThat(limiter.createRoom("ip-b")).isNotNull(); // other IPs unaffected
    }

    @Test
    void expiredRoomsFreeUpConcurrentSlots() {
        RoomCreationLimiter limiter = new RoomCreationLimiter(registry, clock, 5, 20);
        for (int i = 0; i < 5; i++) {
            limiter.createRoom("ip-a");
        }
        clock.advance(Duration.ofMinutes(30)); // room TTL

        assertThat(limiter.createRoom("ip-a").creatorKey()).isEqualTo("ip-a");
    }

    @Test
    void creationsPerHourAreCappedAndTheWindowSlides() {
        // Concurrent cap out of the way so only the hourly cap applies.
        RoomCreationLimiter limiter = new RoomCreationLimiter(registry, clock, 100, 20);
        for (int i = 0; i < 20; i++) {
            limiter.createRoom("ip-a");
        }

        assertThatThrownBy(() -> limiter.createRoom("ip-a"))
                .isInstanceOf(RoomLimitException.class)
                .hasMessageContaining("Try again later");

        clock.advance(Duration.ofMinutes(61));
        assertThat(limiter.createRoom("ip-a")).isNotNull();
    }

    @Test
    void idleKeysAreEvicted() {
        RoomCreationLimiter limiter = new RoomCreationLimiter(registry, clock, 5, 20);
        limiter.createRoom("ip-a");
        clock.advance(Duration.ofMinutes(61));

        limiter.evictIdle();

        assertThat(limiter.trackedKeys()).isZero();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
