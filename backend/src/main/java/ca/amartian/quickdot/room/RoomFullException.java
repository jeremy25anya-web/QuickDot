package ca.amartian.quickdot.room;

public class RoomFullException extends RuntimeException {
    public RoomFullException(String code) {
        super("Room is full: " + code);
    }
}
