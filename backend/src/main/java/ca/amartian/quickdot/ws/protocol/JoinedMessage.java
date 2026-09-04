package ca.amartian.quickdot.ws.protocol;

public record JoinedMessage(String type, String id, String name) {
    public static JoinedMessage of(String id, String name) {
        return new JoinedMessage("joined", id, name);
    }
}
