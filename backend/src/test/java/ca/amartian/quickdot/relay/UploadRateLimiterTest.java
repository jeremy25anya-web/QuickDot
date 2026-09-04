package ca.amartian.quickdot.relay;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class UploadRateLimiterTest {

    @Test
    void allowsUpToTheLimitPerRoomWithinTheWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        UploadRateLimiter limiter = new UploadRateLimiter(clock, 2, 60);

        assertThat(limiter.tryAcquire("ROOM01")).isTrue();
        assertThat(limiter.tryAcquire("ROOM01")).isTrue();
        assertThat(limiter.tryAcquire("ROOM01")).isFalse();
    }

    @Test
    void limitIsSharedAcrossParticipantsInTheSameRoom() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        UploadRateLimiter limiter = new UploadRateLimiter(clock, 1, 60);
        limiter.tryAcquire("ROOM01"); // participant A's upload

        assertThat(limiter.tryAcquire("ROOM01")).isFalse(); // participant B in the same room
    }

    @Test
    void limitsArePerRoom() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        UploadRateLimiter limiter = new UploadRateLimiter(clock, 1, 60);
        limiter.tryAcquire("ROOM01");

        assertThat(limiter.tryAcquire("ROOM02")).isTrue();
    }

    @Test
    void windowSlidingFreesUpCapacity() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        UploadRateLimiter limiter = new UploadRateLimiter(clock, 1, 60);
        limiter.tryAcquire("ROOM01");
        assertThat(limiter.tryAcquire("ROOM01")).isFalse();

        clock.advance(Duration.ofSeconds(61));

        assertThat(limiter.tryAcquire("ROOM01")).isTrue();
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
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
