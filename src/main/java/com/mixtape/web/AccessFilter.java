package com.mixtape.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
// Only loopback without proxy headers and with a local Host is the owner: this blocks tunnels and DNS rebinding.
public class AccessFilter extends OncePerRequestFilter {
    private static final List<String> PROXY_HEADERS = List.of(
            "X-Forwarded-For", "X-Forwarded-Host", "X-Forwarded-Proto", "X-Real-IP", "Forwarded",
            "CF-Connecting-IP", "True-Client-IP");
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

    private static final Set<String> GUEST_FILES = Set.of(
            "/join.html", "/join.css", "/style.css", "/favicon.ico", "/manifest.json");
    private static final List<String> GUEST_PREFIXES = List.of(
            "/join/", "/guest/", "/api/guest/", "/api/preview/", "/icons/");

    public static boolean isOwner(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        boolean loopback = address != null
                && (address.startsWith("127.") || address.equals("::1") || address.equals("0:0:0:0:0:0:0:1"));
        if (!loopback) {
            return false;
        }
        for (String header : PROXY_HEADERS) {
            if (request.getHeader(header) != null) {
                return false;
            }
        }
        return LOCAL_HOSTS.contains(hostOnly(request.getHeader("Host")));
    }

    private static String hostOnly(String hostHeader) {
        if (hostHeader == null) {
            return "";
        }
        String host = hostHeader.trim().toLowerCase(Locale.ROOT);
        if (host.startsWith("[")) {
            int end = host.indexOf(']');
            return end > 0 ? host.substring(0, end + 1) : host;
        }
        int colon = host.indexOf(':');
        return colon >= 0 ? host.substring(0, colon) : host;
    }

    static boolean guestMayOpen(String path) {
        if (path == null || path.contains("..") || path.contains("\\") || path.contains("//")) {
            return false;
        }
        if (GUEST_FILES.contains(path)) {
            return true;
        }
        for (String prefix : GUEST_PREFIXES) {
            if (path.startsWith(prefix) && path.length() > prefix.length()) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isOwner(request) || guestMayOpen(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (request.getRequestURI().startsWith("/api/")) {
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Access restricted to the computer where Toca Aí is running.\"}");
        } else {
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write("<!doctype html><meta charset=utf-8><title>Access restricted</title>"
                    + "<body style=\"font:16px system-ui;padding:40px;color:#0a0a0a\"><h1>Access restricted</h1>"
                    + "<p>To help with a playlist, open the link or QR code the owner shared.</p>");
        }
    }
}
