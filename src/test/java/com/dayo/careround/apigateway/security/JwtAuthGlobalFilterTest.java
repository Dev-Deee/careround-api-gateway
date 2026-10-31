package com.dayo.careround.apigateway.security;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthGlobalFilterTest {

    private static KeyPair keys;
    private static KeyPair otherKeys;
    private static JwtAuthGlobalFilter filter;

    @BeforeAll
    static void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        otherKeys = generator.generateKeyPair();
        filter = new JwtAuthGlobalFilter(new JwtVerificationService(keys.getPublic()), new PublicPathMatcher());
    }

    @Test
    void validTokenIsForwardedWithAuthorizationUnchanged() {
        String header = "Bearer " + token(keys.getPrivate(), Instant.now().plus(Duration.ofMinutes(15)));
        Result r = run("/api/v1/patients", header);
        assertThat(r.forwarded).isTrue();
        assertThat(r.forwardedAuthorization).isEqualTo(header);
        assertThat(r.exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void publicPathNeedsNoToken() {
        assertThat(run("/api/v1/auth/login", null).forwarded).isTrue();
        assertThat(run("/ws", null).forwarded).isTrue();
    }

    @Test
    void publicPathIsNotCheckedEvenWithAGarbageToken() {
        assertThat(run("/api/v1/auth/refresh", "Bearer garbage").forwarded).isTrue();
    }

    @Test
    void missingHeaderIs401() {
        assertUnauthorized(run("/api/v1/patients", null), "Missing bearer token");
    }

    @Test
    void nonBearerHeaderIs401() {
        assertUnauthorized(run("/api/v1/patients", "Basic dXNlcjpwYXNz"), "Missing bearer token");
        assertUnauthorized(run("/api/v1/patients", "Bearer "), "Missing bearer token");
        assertUnauthorized(run("/api/v1/patients", "bearer abc"), "Missing bearer token");
    }

    @Test
    void garbageTokenIs401() {
        assertUnauthorized(run("/api/v1/patients", "Bearer not.a.jwt"), "Invalid token");
    }

    @Test
    void badSignatureIs401() {
        String forged = token(otherKeys.getPrivate(), Instant.now().plus(Duration.ofMinutes(15)));
        assertUnauthorized(run("/api/v1/patients", "Bearer " + forged), "Invalid token");
    }

    @Test
    void expiredTokenIs401() {
        String expired = token(keys.getPrivate(), Instant.now().minus(Duration.ofMinutes(1)));
        assertUnauthorized(run("/api/v1/patients", "Bearer " + expired), "Token expired");
    }

    @Test
    void routeOrderSpecificNotificationPathIsStillProtected() {
        assertUnauthorized(run("/api/v1/wards/" + UUID.randomUUID() + "/alerts", null), "Missing bearer token");
    }

    private static void assertUnauthorized(Result r, String message) {
        assertThat(r.forwarded).isFalse();
        assertThat(r.exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(r.exchange.getResponse().getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        String body = r.exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("\"status\":401").contains("\"error\":\"Unauthorized\"")
                .contains("\"message\":\"" + message + "\"").contains("\"timestamp\":");
    }

    private static Result run(String path, String authorization) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get(path);
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        Result result = new Result(exchange);
        GatewayFilterChain chain = ex -> {
            result.forwarded = true;
            result.forwardedAuthorization = ex.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        return result;
    }

    private static String token(PrivateKey key, Instant expiresAt) {
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", "NURSE")
                .issuedAt(Date.from(expiresAt.minus(Duration.ofMinutes(15))))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    private static final class Result {
        final MockServerWebExchange exchange;
        boolean forwarded;
        String forwardedAuthorization;

        Result(MockServerWebExchange exchange) { this.exchange = exchange; }
    }
}
