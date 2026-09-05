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
    private final String allowedOrigin;

    public WebSocketConfig(QuickDotWebSocketHandler handler,
            @Value("${app.allowed-origin}") String allowedOrigin) {
        this.handler = handler;
        this.allowedOrigin = allowedOrigin;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // spec §6: reject handshakes whose Origin isn't the frontend's exact
        // origin. app.allowed-origin defaults to the Angular dev server
        // (application.properties) and is overridden via APP_ALLOWED_ORIGIN
        // in every other environment (see docs/DEPLOY.md).
        registry.addHandler(handler, "/ws/*")
                .addInterceptors(new RoomCodeHandshakeInterceptor())
                .setAllowedOrigins(allowedOrigin);
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
