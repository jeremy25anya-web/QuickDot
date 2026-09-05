package ca.amartian.quickdot.web;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableScheduling
public class AppConfig implements WebMvcConfigurer {

    private final String allowedOrigin;

    public AppConfig(@Value("${app.allowed-origin}") String allowedOrigin) {
        this.allowedOrigin = allowedOrigin;
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // spec §6: CORS locked to the frontend's exact origin, same
        // app.allowed-origin property as ws.WebSocketConfig.
        registry.addMapping("/api/**").allowedOrigins(allowedOrigin).allowedMethods("GET", "POST");
    }
}
