package ca.amartian.quickdot.room;

import ca.amartian.quickdot.audit.AuditLog;
import ca.amartian.quickdot.audit.IpHasher;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mints room codes over HTTP so a creator has something to share before
 * anyone connects over WebSocket — joining (spec §5) assumes the room
 * already exists.
 */
@RestController
public class RoomController {

    public record CreateRoomResponse(String code, Instant expiresAt) {
    }

    private final RoomRegistry roomRegistry;
    private final AuditLog auditLog;
    private final IpHasher ipHasher;

    public RoomController(RoomRegistry roomRegistry, AuditLog auditLog, IpHasher ipHasher) {
        this.roomRegistry = roomRegistry;
        this.auditLog = auditLog;
        this.ipHasher = ipHasher;
    }

    @PostMapping("/api/rooms")
    public CreateRoomResponse createRoom(HttpServletRequest request) {
        Room room = roomRegistry.createRoom();
        auditLog.roomCreated(room.code(), ipHasher.hash(request.getRemoteAddr()));
        return new CreateRoomResponse(room.code(), room.expiresAt());
    }
}
