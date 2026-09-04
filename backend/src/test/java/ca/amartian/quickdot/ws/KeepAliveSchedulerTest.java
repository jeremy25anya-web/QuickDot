package ca.amartian.quickdot.ws;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

class KeepAliveSchedulerTest {

    @Test
    void pingsFreshSessionAndClosesStaleOne() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        SessionRegistry sessionRegistry = new SessionRegistry(clock);
        WebSocketSession fresh = mock(WebSocketSession.class);
        WebSocketSession stale = mock(WebSocketSession.class);
        sessionRegistry.register("fresh", fresh);
        sessionRegistry.register("stale", stale);

        clock.advance(Duration.ofSeconds(10));
        sessionRegistry.markPong("fresh");
        clock.advance(Duration.ofSeconds(35)); // fresh: 35s since pong; stale: 45s since register

        KeepAliveScheduler scheduler = new KeepAliveScheduler(sessionRegistry, new ObjectMapper(), 20);
        scheduler.pingAll();

        verify(fresh).sendMessage(any(TextMessage.class));
        verify(stale).close(CloseStatus.GOING_AWAY);
        verify(stale, never()).sendMessage(any());
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
