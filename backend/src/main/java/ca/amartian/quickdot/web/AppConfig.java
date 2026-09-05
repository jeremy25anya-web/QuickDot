package ca.amartian.quickdot.web;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableScheduling
public class AppConfig implements WebMvcConfigurer {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // TODO(Stage 6): lock this to the exact production frontend origin
        // (spec §6). Permissive for now for the same reason as
        // ws.WebSocketConfig's allowed origin patterns — local dev and the
        // Cloudflare Tunnel put the Angular dev server on a different origin
        // than the backend.
        registry.addMapping("/api/**").allowedOriginPatterns("*").allowedMethods("GET", "POST");
    }
}
