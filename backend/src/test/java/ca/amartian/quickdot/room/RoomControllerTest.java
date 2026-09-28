package ca.amartian.quickdot.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import ca.amartian.quickdot.audit.AuditLog;
import ca.amartian.quickdot.audit.IpHasher;
import ca.amartian.quickdot.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(RoomController.class)
class RoomControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private RoomCreationLimiter roomCreationLimiter;

    @MockitoBean
    private AuditLog auditLog;

    @MockitoBean
    private IpHasher ipHasher;

    @MockitoBean
    private ClientIpResolver clientIpResolver;

    @Test
    void createRoomReturnsCodeAndExpiry() {
        Room room = new Room("ABCDEF", Instant.parse("2026-01-01T00:00:00Z"), java.time.Duration.ofMinutes(30));
        when(clientIpResolver.resolve(any(HttpServletRequest.class))).thenReturn("203.0.113.5");
        when(ipHasher.hash("203.0.113.5")).thenReturn("hashed");
        when(roomCreationLimiter.createRoom("hashed")).thenReturn(room);

        assertThat(mvc.post().uri("/api/rooms"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("ABCDEF");
    }

    @Test
    void roomLimitReturns429WithErrorCode() {
        when(clientIpResolver.resolve(any(HttpServletRequest.class))).thenReturn("203.0.113.5");
        when(ipHasher.hash(anyString())).thenReturn("hashed");
        when(roomCreationLimiter.createRoom("hashed")).thenThrow(new RoomLimitException("Too many rooms created. Try again later."));

        assertThat(mvc.post().uri("/api/rooms"))
                .hasStatus(HttpStatus.TOO_MANY_REQUESTS)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("ROOM_LIMIT");
    }
}
