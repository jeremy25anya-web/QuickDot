package ca.amartian.quickdot.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ca.amartian.quickdot.audit.AuditLog;
import ca.amartian.quickdot.audit.IpHasher;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(RoomController.class)
class RoomControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private RoomRegistry roomRegistry;

    @MockitoBean
    private AuditLog auditLog;

    @MockitoBean
    private IpHasher ipHasher;

    @Test
    void createRoomReturnsCodeAndExpiry() {
        Room room = new Room("ABCDEF", Instant.parse("2026-01-01T00:00:00Z"), java.time.Duration.ofMinutes(30));
        when(roomRegistry.createRoom()).thenReturn(room);

        assertThat(mvc.post().uri("/api/rooms"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("ABCDEF");
    }
}
