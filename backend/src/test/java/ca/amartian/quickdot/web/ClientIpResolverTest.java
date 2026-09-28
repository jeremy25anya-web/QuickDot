package ca.amartian.quickdot.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClientIpResolverTest {

    @Test
    void usesFlyHeaderWhenTrusted() {
        assertThat(new ClientIpResolver(true).resolve("203.0.113.5", "172.16.0.1")).isEqualTo("203.0.113.5");
    }

    @Test
    void ignoresFlyHeaderWhenNotTrusted() {
        // Off Fly, anyone could send the header to dodge per-IP limits.
        assertThat(new ClientIpResolver(false).resolve("203.0.113.5", "127.0.0.1")).isEqualTo("127.0.0.1");
    }

    @Test
    void fallsBackToPeerWhenHeaderMissing() {
        assertThat(new ClientIpResolver(true).resolve(null, "172.16.0.1")).isEqualTo("172.16.0.1");
        assertThat(new ClientIpResolver(true).resolve("  ", "172.16.0.1")).isEqualTo("172.16.0.1");
    }
}
