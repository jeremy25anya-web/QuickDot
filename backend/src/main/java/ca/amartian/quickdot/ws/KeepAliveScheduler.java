package ca.amartian.quickdot.ws;

import ca.amartian.quickdot.ws.protocol.PingMessage;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Pings every live session on an interval and drops ones that haven't ponged
 * back within ~2 intervals (spec §6). Dropping a session triggers the
 * handler's normal {@code afterConnectionClosed} cleanup/broadcast — this
 * class doesn't touch the room registry directly.
 */
@Component
public class KeepAliveScheduler {

    private final SessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;
    private final Duration staleAfter;

    public KeepAliveScheduler(SessionRegistry sessionRegistry, ObjectMapper objectMapper,
            @Value("${ws.ping-interval-seconds:20}") long pingIntervalSeconds) {
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
        this.staleAfter = Duration.ofSeconds(pingIntervalSeconds * 2);
    }

    @Scheduled(fixedRateString = "#{${ws.ping-interval-seconds:20} * 1000}")
    public void pingAll() {
        for (String participantId : sessionRegistry.allParticipantIds()) {
            if (sessionRegistry.isStale(participantId, staleAfter)) {
                sessionRegistry.find(participantId).ifPresent(this::closeQuiet);
                continue;
            }
            sessionRegistry.find(participantId).ifPresent(this::sendPing);
        }
    }

    private void sendPing(WebSocketSession session) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(PingMessage.of())));
        } catch (IOException e) {
            closeQuiet(session);
        }
    }

    private void closeQuiet(WebSocketSession session) {
        try {
            session.close(CloseStatus.GOING_AWAY);
        } catch (IOException ignored) {
            // already gone
        }
    }
}
