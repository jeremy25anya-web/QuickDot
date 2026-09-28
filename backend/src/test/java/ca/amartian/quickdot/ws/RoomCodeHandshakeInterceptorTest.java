package ca.amartian.quickdot.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.amartian.quickdot.web.ClientIpResolver;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;

class RoomCodeHandshakeInterceptorTest {

    @Test
    void extractsAndUppercasesRoomCodeFromPath() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("http://localhost:8080/ws/ab12cd"));
        when(request.getHeaders()).thenReturn(new HttpHeaders());
        Map<String, Object> attributes = new HashMap<>();

        boolean result = new RoomCodeHandshakeInterceptor(new ClientIpResolver(false))
                .beforeHandshake(request, null, null, attributes);

        assertThat(result).isTrue();
        assertThat(attributes).containsEntry(RoomCodeHandshakeInterceptor.ATTR_ROOM_CODE, "AB12CD");
    }

    @Test
    void storesFlyClientIpWhenTrusted() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("http://localhost:8080/ws/AB12CD"));
        HttpHeaders headers = new HttpHeaders();
        headers.add("Fly-Client-IP", "203.0.113.5");
        when(request.getHeaders()).thenReturn(headers);
        Map<String, Object> attributes = new HashMap<>();

        new RoomCodeHandshakeInterceptor(new ClientIpResolver(true)).beforeHandshake(request, null, null, attributes);

        assertThat(attributes).containsEntry(RoomCodeHandshakeInterceptor.ATTR_CLIENT_IP, "203.0.113.5");
    }
}
