package ca.amartian.quickdot.room;

import ca.amartian.quickdot.audit.AuditLog;
import ca.amartian.quickdot.audit.IpHasher;
import ca.amartian.quickdot.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    public record ErrorResponse(String code, String message) {
    }

    private final RoomCreationLimiter roomCreationLimiter;
    private final AuditLog auditLog;
    private final IpHasher ipHasher;
    private final ClientIpResolver clientIpResolver;

    public RoomController(RoomCreationLimiter roomCreationLimiter, AuditLog auditLog, IpHasher ipHasher,
            ClientIpResolver clientIpResolver) {
        this.roomCreationLimiter = roomCreationLimiter;
        this.auditLog = auditLog;
        this.ipHasher = ipHasher;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/api/rooms")
    public CreateRoomResponse createRoom(HttpServletRequest request) {
        String hashedIp = ipHasher.hash(clientIpResolver.resolve(request));
        Room room = roomCreationLimiter.createRoom(hashedIp);
        auditLog.roomCreated(room.code(), hashedIp);
        return new CreateRoomResponse(room.code(), room.expiresAt());
    }

    @ExceptionHandler(RoomLimitException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ErrorResponse roomLimit(RoomLimitException e) {
        return new ErrorResponse("ROOM_LIMIT", e.getMessage());
    }
}
