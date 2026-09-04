package ca.amartian.quickdot.ws.protocol;

public record FileStartMessage(String type, ParticipantView from, String transferId, String name, String mime, long size) {
    public static FileStartMessage of(ParticipantView from, String transferId, String name, String mime, long size) {
        return new FileStartMessage("file-start", from, transferId, name, mime, size);
    }
}
