package ca.amartian.quickdot.ws.protocol;

public record RoomEndedMessage(String type, String by) {
    public static RoomEndedMessage of(String by) {
        return new RoomEndedMessage("room-ended", by);
    }
}
