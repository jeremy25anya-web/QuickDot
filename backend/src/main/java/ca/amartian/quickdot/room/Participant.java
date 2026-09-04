package ca.amartian.quickdot.room;

/** A room participant. WebSocket session wiring lives in the {@code ws} package, not here. */
public record Participant(String id, String name, String ip) {
}
