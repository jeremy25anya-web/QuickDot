package ca.amartian.quickdot.relay;

import java.util.Set;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

/**
 * Magic-byte MIME detection (spec §3, §6) — never trusts the client-declared
 * MIME type. Runs once, on the first chunk of a transfer, since that's the
 * only point bytes actually exist to sniff (spec §5: {@code file-start}
 * carries no bytes).
 */
@Component
public class MimeSniffer {

    /**
     * Detected types that mean "this is an executable/script regardless of
     * what extension or declared MIME type it was uploaded under."
     */
    private static final Set<String> DANGEROUS_MIME_TYPES = Set.of(
            "application/x-msdownload",
            "application/x-msdos-program",
            "application/vnd.microsoft.portable-executable",
            "application/x-executable",
            "application/x-sharedlib",
            "application/x-elf",
            "application/x-mach-binary",
            "application/x-sh",
            "application/x-shellscript",
            "application/x-bat",
            "application/x-msi",
            "application/java-archive",
            "application/x-java-archive");

    private final Tika tika = new Tika();

    /** Detects the MIME type from magic bytes only — filename/declared type play no part. */
    public String detect(byte[] bytes) {
        return tika.detect(bytes);
    }

    public boolean isDangerous(String detectedMimeType) {
        return DANGEROUS_MIME_TYPES.contains(detectedMimeType);
    }
}
