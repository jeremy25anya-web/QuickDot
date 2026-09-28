package ca.amartian.quickdot.ws;

import ca.amartian.quickdot.web.ClientIpResolver;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Pulls the room code out of {@code /ws/{roomCode}}, and the real client IP
 * (see {@link ClientIpResolver}), into session attributes before handshake.
 */
public class RoomCodeHandshakeInterceptor implements HandshakeInterceptor {

    static final String ATTR_ROOM_CODE = "roomCode";
    static final String ATTR_CLIENT_IP = "clientIp";

    private final ClientIpResolver clientIpResolver;

    public RoomCodeHandshakeInterceptor(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String path = request.getURI().getPath();
        String roomCode = path.substring(path.lastIndexOf('/') + 1).trim().toUpperCase();
        attributes.put(ATTR_ROOM_CODE, roomCode);
        attributes.put(ATTR_CLIENT_IP, clientIpResolver.resolve(request));
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
