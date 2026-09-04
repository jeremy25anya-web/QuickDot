package ca.amartian.quickdot.ws;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.socket.WebSocketSession;

class SessionRegistryTest {

    private MutableClock clock;
    private SessionRegistry registry;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        registry = new SessionRegistry(clock);
    }

    @Test
    void registeredSessionIsFindable() {
        WebSocketSession session = Mockito.mock(WebSocketSession.class);

        registry.register("p1", session);

        assertThat(registry.find("p1")).contains(session);
        assertThat(registry.allParticipantIds()).containsExactly("p1");
    }

    @Test
    void unregisteredSessionIsGone() {
        WebSocketSession session = Mockito.mock(WebSocketSession.class);
        registry.register("p1", session);

        registry.unregister("p1");

        assertThat(registry.find("p1")).isEmpty();
    }

    @Test
    void isStaleFalseBeforeThresholdElapses() {
        WebSocketSession session = Mockito.mock(WebSocketSession.class);
        registry.register("p1", session);

        clock.advance(Duration.ofSeconds(39));

        assertThat(registry.isStale("p1", Duration.ofSeconds(40))).isFalse();
    }

    @Test
    void isStaleTrueAfterThresholdElapsesWithNoPong() {
        WebSocketSession session = Mockito.mock(WebSocketSession.class);
        registry.register("p1", session);

        clock.advance(Duration.ofSeconds(41));

        assertThat(registry.isStale("p1", Duration.ofSeconds(40))).isTrue();
    }

    @Test
    void markPongResetsStaleness() {
        WebSocketSession session = Mockito.mock(WebSocketSession.class);
        registry.register("p1", session);
        clock.advance(Duration.ofSeconds(39));

        registry.markPong("p1");
        clock.advance(Duration.ofSeconds(39));

        assertThat(registry.isStale("p1", Duration.ofSeconds(40))).isFalse();
    }

    @Test
    void unknownParticipantIsStale() {
        assertThat(registry.isStale("ghost", Duration.ofSeconds(40))).isTrue();
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
