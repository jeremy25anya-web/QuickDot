package ca.amartian.quickdot.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetSocketAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;

/**
 * The real client IP for audit hashing and per-IP limits (spec §6).
 *
 * Behind Fly.io the socket peer is Fly's proxy, not the user, so every client
 * would look identical. Fly's proxy sets {@code Fly-Client-IP} to the address
 * it accepted the connection from. That header is only trusted when
 * {@code app.trust-fly-client-ip} is on (set in fly.toml) — anywhere else a
 * client could send it themselves to dodge per-IP limits.
 */
@Component
public class ClientIpResolver {

    static final String FLY_CLIENT_IP = "Fly-Client-IP";

    private final boolean trustFlyHeader;

    public ClientIpResolver(@Value("${app.trust-fly-client-ip:false}") boolean trustFlyHeader) {
        this.trustFlyHeader = trustFlyHeader;
    }

    public String resolve(HttpServletRequest request) {
        return resolve(request.getHeader(FLY_CLIENT_IP), request.getRemoteAddr());
    }

    public String resolve(ServerHttpRequest request) {
        InetSocketAddress peer = request.getRemoteAddress();
        return resolve(request.getHeaders().getFirst(FLY_CLIENT_IP),
                peer != null ? peer.getAddress().getHostAddress() : "unknown");
    }

    String resolve(String flyHeader, String peerAddress) {
        if (trustFlyHeader && flyHeader != null && !flyHeader.isBlank()) {
            return flyHeader.trim();
        }
        return peerAddress;
    }
}
