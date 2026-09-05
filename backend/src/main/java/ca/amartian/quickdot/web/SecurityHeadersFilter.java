package ca.amartian.quickdot.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;

/**
 * Security headers on every response (spec §6). This backend never serves
 * HTML — just JSON and WebSocket upgrades — so the CSP can be maximally
 * restrictive rather than allow-listing script/style sources.
 */
@Component
public class SecurityHeadersFilter implements jakarta.servlet.Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (response instanceof HttpServletResponse http) {
            http.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
            http.setHeader("Strict-Transport-Security", "max-age=63072000; includeSubDomains");
            http.setHeader("X-Content-Type-Options", "nosniff");
            http.setHeader("Referrer-Policy", "no-referrer");
            http.setHeader("X-Frame-Options", "DENY");
            http.setHeader("Permissions-Policy", "geolocation=(), camera=(), microphone=()");
        }
        chain.doFilter(request, response);
    }
}
