package ca.amartian.quickdot.room;

public class RoomNotFoundException extends RuntimeException {
    public RoomNotFoundException(String code) {
        super("Room not found or expired: " + code);
    }
}
