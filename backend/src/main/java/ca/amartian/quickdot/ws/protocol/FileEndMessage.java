package ca.amartian.quickdot.ws.protocol;

public record FileEndMessage(String type, ParticipantView from, String transferId) {
    public static FileEndMessage of(ParticipantView from, String transferId) {
        return new FileEndMessage("file-end", from, transferId);
    }
}
