package ca.amartian.quickdot.room;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Generates 6-char room codes. Alphabet matches the prototype (quickdot.html) and spec §4. */
@Component
public class RoomCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 6;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
