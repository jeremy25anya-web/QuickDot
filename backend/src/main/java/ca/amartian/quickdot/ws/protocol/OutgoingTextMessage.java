package ca.amartian.quickdot.ws.protocol;

public record OutgoingTextMessage(String type, ParticipantView from, String id, String text) {
    public static OutgoingTextMessage of(ParticipantView from, String id, String text) {
        return new OutgoingTextMessage("text", from, id, text);
    }
}
