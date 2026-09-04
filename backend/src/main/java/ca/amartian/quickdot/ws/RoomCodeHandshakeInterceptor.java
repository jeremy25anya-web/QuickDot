package ca.amartian.quickdot.ws;

import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/** Pulls the room code out of {@code /ws/{roomCode}} into a session attribute before handshake. */
public class RoomCodeHandshakeInterceptor implements HandshakeInterceptor {

    static final String ATTR_ROOM_CODE = "roomCode";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String path = request.getURI().getPath();
        String roomCode = path.substring(path.lastIndexOf('/') + 1).trim().toUpperCase();
        attributes.put(ATTR_ROOM_CODE, roomCode);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
