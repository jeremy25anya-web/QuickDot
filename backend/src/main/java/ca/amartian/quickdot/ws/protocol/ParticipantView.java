package ca.amartian.quickdot.ws.protocol;

/** Wire shape for a participant in presence messages (spec §5). */
public record ParticipantView(String id, String name) {
}
