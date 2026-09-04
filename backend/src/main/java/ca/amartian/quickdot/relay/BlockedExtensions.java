package ca.amartian.quickdot.relay;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Extension blocklist (spec §6) — checked before any bytes are even accepted. */
@Component
public class BlockedExtensions {

    private final Set<String> blocked;

    public BlockedExtensions(@Value("${relay.file.blocked-extensions}") String blockedExtensionsCsv) {
        List<String> parsed = Arrays.stream(blockedExtensionsCsv.split(","))
                .map(String::trim)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .toList();
        this.blocked = Set.copyOf(parsed);
    }

    public boolean isBlocked(String fileName) {
        String extension = extensionOf(fileName);
        return !extension.isEmpty() && blocked.contains(extension);
    }

    private String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
