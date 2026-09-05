package ca.amartian.quickdot.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Turns a raw client IP into the "hashed/truncated IP" spec §6 requires for
 * metadata logging — never the raw address. Salted so hashes aren't
 * reversible by rainbow table, and truncated since abuse response only needs
 * to correlate repeat offenders within the retention window, not recover
 * the address.
 */
@Component
public class IpHasher {

    private static final int TRUNCATED_HEX_LENGTH = 12;

    private final String salt;

    public IpHasher(@Value("${app.ip-hash-salt}") String salt) {
        this.salt = salt;
    }

    public String hash(String ip) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest((salt + ip).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed).substring(0, TRUNCATED_HEX_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
