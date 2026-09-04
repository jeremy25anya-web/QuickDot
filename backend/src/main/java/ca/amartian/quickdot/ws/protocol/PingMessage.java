package ca.amartian.quickdot.ws.protocol;

public record PingMessage(String type) {
    public static PingMessage of() {
        return new PingMessage("ping");
    }
}
