package ca.amartian.quickdot.ws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final QuickDotWebSocketHandler handler;

    public WebSocketConfig(QuickDotWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // TODO(Stage 6): lock allowed origins to the exact production origin
        // (spec §6). Permissive for now because local dev and the Cloudflare
        // Tunnel both put the Angular dev server on a different origin than
        // the backend.
        registry.addHandler(handler, "/ws/*")
                .addInterceptors(new RoomCodeHandshakeInterceptor())
                .setAllowedOriginPatterns("*");
    }

    /** Sized to fit one 256 KB file-chunk frame plus framing overhead (spec §5). */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer(
            @Value("${ws.max-binary-message-bytes:270336}") int maxBinaryMessageBytes,
            @Value("${ws.max-text-message-bytes:8192}") int maxTextMessageBytes) {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxBinaryMessageBufferSize(maxBinaryMessageBytes);
        container.setMaxTextMessageBufferSize(maxTextMessageBytes);
        return container;
    }
}
