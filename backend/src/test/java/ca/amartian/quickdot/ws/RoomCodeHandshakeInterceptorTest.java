package ca.amartian.quickdot.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;

class RoomCodeHandshakeInterceptorTest {

    @Test
    void extractsAndUppercasesRoomCodeFromPath() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("http://localhost:8080/ws/ab12cd"));
        Map<String, Object> attributes = new HashMap<>();

        boolean result = new RoomCodeHandshakeInterceptor()
                .beforeHandshake(request, null, null, attributes);

        assertThat(result).isTrue();
        assertThat(attributes).containsEntry(RoomCodeHandshakeInterceptor.ATTR_ROOM_CODE, "AB12CD");
    }
}
