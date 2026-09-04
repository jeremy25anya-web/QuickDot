package ca.amartian.quickdot.ws;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FunNameGeneratorTest {

    @Test
    void generatesAdjectiveSpaceCreature() {
        String name = new FunNameGenerator().generate();

        assertThat(name).matches("[A-Z][a-z]+ [A-Z][a-z]+");
    }

    @Test
    void variesAcrossCalls() {
        FunNameGenerator generator = new FunNameGenerator();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            names.add(generator.generate());
        }

        assertThat(names.size()).isGreaterThan(1);
    }
}
