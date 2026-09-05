package ca.amartian.quickdot.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(HealthController.class)
class SecurityHeadersFilterTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void appliesSecurityHeadersToEveryResponse() {
        var result = mvc.get().uri("/healthz").exchange();
        assertThat(result.getResponse().getHeader("Content-Security-Policy"))
                .isEqualTo("default-src 'none'; frame-ancestors 'none'");
        assertThat(result.getResponse().getHeader("Strict-Transport-Security"))
                .isEqualTo("max-age=63072000; includeSubDomains");
        assertThat(result.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(result.getResponse().getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(result.getResponse().getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(result.getResponse().getHeader("Permissions-Policy"))
                .isEqualTo("geolocation=(), camera=(), microphone=()");
    }
}
