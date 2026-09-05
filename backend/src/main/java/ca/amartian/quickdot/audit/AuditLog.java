package ca.amartian.quickdot.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Metadata-only audit trail (spec §6): timestamp (from the log line itself),
 * room code, a hashed/truncated IP, and — for files — hash + size + type.
 * Never message or file content. Routed to its own rolling, 30-day-retained
 * appender by logger name (see {@code logback-spring.xml}), separate from
 * general application logs.
 */
@Component
public class AuditLog {

    private static final Logger log = LoggerFactory.getLogger("ca.amartian.quickdot.audit");

    public void roomCreated(String roomCode, String hashedIp) {
        log.info("event=room_created room={} ip={}", roomCode, hashedIp);
    }

    public void participantJoined(String roomCode, String hashedIp) {
        log.info("event=participant_joined room={} ip={}", roomCode, hashedIp);
    }

    public void fileRelayed(String roomCode, String hashedIp, String sha256Hex, long sizeBytes, String mimeType) {
        log.info("event=file_relayed room={} ip={} sha256={} size={} mime={}",
                roomCode, hashedIp, sha256Hex, sizeBytes, mimeType);
    }
}
