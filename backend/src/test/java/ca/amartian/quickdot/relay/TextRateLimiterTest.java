package ca.amartian.quickdot.relay;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TextRateLimiterTest {

    @Test
    void allowsUpToTheLimitWithinTheWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        TextRateLimiter limiter = new TextRateLimiter(clock, 3, 10);

        assertThat(limiter.tryAcquire("p1")).isTrue();
        assertThat(limiter.tryAcquire("p1")).isTrue();
        assertThat(limiter.tryAcquire("p1")).isTrue();
        assertThat(limiter.tryAcquire("p1")).isFalse();
    }

    @Test
    void windowSlidingFreesUpCapacity() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        TextRateLimiter limiter = new TextRateLimiter(clock, 2, 10);
        limiter.tryAcquire("p1");
        limiter.tryAcquire("p1");
        assertThat(limiter.tryAcquire("p1")).isFalse();

        clock.advance(Duration.ofSeconds(11));

        assertThat(limiter.tryAcquire("p1")).isTrue();
    }

    @Test
    void limitsArePerParticipant() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        TextRateLimiter limiter = new TextRateLimiter(clock, 1, 10);
        limiter.tryAcquire("p1");

        assertThat(limiter.tryAcquire("p2")).isTrue();
    }

    @Test
    void clearResetsTheParticipant() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        TextRateLimiter limiter = new TextRateLimiter(clock, 1, 10);
        limiter.tryAcquire("p1");

        limiter.clear("p1");

        assertThat(limiter.tryAcquire("p1")).isTrue();
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
