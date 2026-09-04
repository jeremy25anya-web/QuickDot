package ca.amartian.quickdot.room;

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

    public RoomController(RoomRegistry roomRegistry) {
        this.roomRegistry = roomRegistry;
    }

    @PostMapping("/api/rooms")
    public CreateRoomResponse createRoom() {
        Room room = roomRegistry.createRoom();
        return new CreateRoomResponse(room.code(), room.expiresAt());
    }
}
