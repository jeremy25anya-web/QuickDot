package ca.amartian.quickdot.ws.protocol;

import java.time.Instant;
import java.util.List;

public record WelcomeMessage(String type, ParticipantView you, List<ParticipantView> participants, Instant expiresAt) {
    public static WelcomeMessage of(ParticipantView you, List<ParticipantView> participants, Instant expiresAt) {
        return new WelcomeMessage("welcome", you, participants, expiresAt);
    }
}
