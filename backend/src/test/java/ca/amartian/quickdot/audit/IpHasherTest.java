package ca.amartian.quickdot.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IpHasherTest {

    private final IpHasher hasher = new IpHasher("test-salt");

    @Test
    void hashIsStableForTheSameIp() {
        assertThat(hasher.hash("203.0.113.5")).isEqualTo(hasher.hash("203.0.113.5"));
    }

    @Test
    void differentIpsHashDifferently() {
        assertThat(hasher.hash("203.0.113.5")).isNotEqualTo(hasher.hash("203.0.113.6"));
    }

    @Test
    void hashIsTruncatedAndNeverContainsTheRawIp() {
        String hash = hasher.hash("203.0.113.5");
        assertThat(hash).hasSize(12);
        assertThat(hash).doesNotContain("203.0.113.5");
    }

    @Test
    void differentSaltsProduceDifferentHashesForTheSameIp() {
        IpHasher other = new IpHasher("different-salt");
        assertThat(hasher.hash("203.0.113.5")).isNotEqualTo(other.hash("203.0.113.5"));
    }
}
