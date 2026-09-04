package ca.amartian.quickdot.ws;

import java.security.SecureRandom;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Server-assigned "fun names" (adjective + creature), spec §5. Word lists
 * match the prototype's {@code ADJ}/{@code CREA} arrays exactly, including
 * no dedup within a room — same behavior as quickdot.html's {@code genName()}.
 */
@Component
public class FunNameGenerator {

    private static final List<String> ADJECTIVES = List.of(
            "Amorphous", "Spontaneous", "Velvet", "Nimble", "Cosmic", "Wandering", "Curious", "Gentle",
            "Luminous", "Peculiar", "Dapper", "Sleepy", "Electric", "Mellow", "Radiant", "Wobbly",
            "Sublime", "Brave", "Quiet", "Plucky");

    private static final List<String> CREATURES = List.of(
            "Duckling", "Manatee", "Pangolin", "Otter", "Wombat", "Axolotl", "Narwhal", "Heron",
            "Lynx", "Cuttlefish", "Tapir", "Puffin", "Quokka", "Salamander", "Meerkat", "Capybara",
            "Firefly", "Jellyfish", "Marmot", "Ibex");

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        String adjective = ADJECTIVES.get(random.nextInt(ADJECTIVES.size()));
        String creature = CREATURES.get(random.nextInt(CREATURES.size()));
        return adjective + " " + creature;
    }
}
