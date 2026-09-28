package ca.amartian.quickdot.room;

/** A per-IP room-creation limit (spec §6) was hit. */
public class RoomLimitException extends RuntimeException {

    public RoomLimitException(String message) {
        super(message);
    }
}
