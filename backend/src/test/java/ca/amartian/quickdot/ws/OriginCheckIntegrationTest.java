package ca.amartian.quickdot.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.amartian.quickdot.room.RoomRegistry;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * End-to-end proof of spec §6's origin check (Stage 6's "hostile-input pass"
 * done-when criterion: "bad origin ... fails safely"). Unlike the rest of
 * QuickDotWebSocketHandlerTest, this can't be a plain unit test against the
 * handler — the origin check happens in Spring's handshake interceptor,
 * before the handler ever runs — so it needs a real embedded server and a
 * real WebSocket client doing a real HTTP upgrade.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OriginCheckIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private RoomRegistry roomRegistry;

    @Test
    void handshakeWithWrongOriginIsRejected() {
        String code = roomRegistry.createRoom().code();
        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin("https://evil.example.com");

        CompletableFuture<WebSocketSession> future = client.execute(
                new TextWebSocketHandler() {}, headers, URI.create("ws://localhost:" + port + "/ws/" + code));

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void handshakeWithConfiguredOriginSucceeds() throws Exception {
        String code = roomRegistry.createRoom().code();
        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        // matches application.properties' default app.allowed-origin
        headers.setOrigin("http://localhost:4200");

        CompletableFuture<WebSocketSession> future = client.execute(
                new TextWebSocketHandler() {}, headers, URI.create("ws://localhost:" + port + "/ws/" + code));

        WebSocketSession session = future.get(5, TimeUnit.SECONDS);
        try {
            assertThat(session.isOpen()).isTrue();
        } finally {
            session.close();
        }
    }
}
