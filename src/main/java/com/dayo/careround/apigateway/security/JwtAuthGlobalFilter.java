package com.dayo.careround.apigateway.security;

import com.dayo.careround.apigateway.utils.ErrorResponses;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Rejects requests to protected routes that lack a valid, unexpired JWT with 401, before they reach a service.
 * Authentication only: role and ward rules stay in the services, which verify the token again (defence in
 * depth). The Authorization header is forwarded unchanged; the gateway never mints or rewrites tokens.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    public static final int ORDER = -100;

    private final JwtVerificationService jwtVerificationService;
    private final PublicPathMatcher publicPathMatcher;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (publicPathMatcher.isPublic(path)) {
            return chain.filter(exchange);
        }

        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ") || header.length() <= 7) {
            return unauthorized(exchange, "Missing bearer token");
        }

        try {
            jwtVerificationService.verifyToken(header.substring(7));
        } catch (ExpiredJwtException e) {
            return unauthorized(exchange, "Token expired");
        } catch (JwtException | IllegalArgumentException e) {
            return unauthorized(exchange, "Invalid token");
        }
        return chain.filter(exchange);
    }

    private static Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        log.info("401 {} {}: {}", exchange.getRequest().getMethod(), exchange.getRequest().getPath(), message);
        return ErrorResponses.write(exchange, HttpStatus.UNAUTHORIZED, message,
                headers -> headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
