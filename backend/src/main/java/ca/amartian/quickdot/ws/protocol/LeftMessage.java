package ca.amartian.quickdot.ws.protocol;

public record LeftMessage(String type, String id) {
    public static LeftMessage of(String id) {
        return new LeftMessage("left", id);
    }
}
