package com.dayo.careround.apigateway.filter;

import com.dayo.careround.apigateway.config.RateLimitProperties;
import com.dayo.careround.apigateway.utils.ErrorResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;

/**
 * Per-client-IP, per-minute limit on the public auth endpoints (login, refresh, activate-account), counted in
 * Redis (fixed one-minute windows). Over the limit -> 429 JSON with Retry-After. Fails open: if Redis is down
 * or slow the request is let through (WARN), so a Redis outage cannot lock staff out.
 * The client IP is the socket peer; X-Forwarded-For is not trusted (it is client-controlled).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthRateLimitFilter implements GatewayFilter {

    private static final String KEY_PREFIX = "careround:gateway:ratelimit:";

    private final ReactiveStringRedisTemplate redis;
    private final RateLimitProperties properties;
    private final Clock clock;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        RateLimitProperties.Bucket bucket = properties.bucketFor(path);
        if (!properties.isEnabled() || bucket == null) {
            return chain.filter(exchange);
        }

        String ip = clientIp(exchange);
        long now = clock.millis();
        long window = now / 60_000;
        String key = KEY_PREFIX + bucket.name() + ":" + ip + ":" + window;

        return redis.opsForValue().increment(key)
                .flatMap(count -> count == 1
                        ? redis.expire(key, Duration.ofSeconds(70)).thenReturn(count)
                        : Mono.just(count))
                .timeout(Duration.ofMillis(properties.getRedisTimeoutMs()))
                .map(count -> count <= bucket.limit())
                .onErrorResume(e -> {
                    log.warn("Rate limiter unavailable ({}): allowing {} from {}", e.toString(), path, ip);
                    return Mono.just(true);
                })
                .defaultIfEmpty(true)
                .flatMap(allowed -> {
                    if (allowed) {
                        return chain.filter(exchange);
                    }
                    long retryAfter = Math.max(1, 60 - (now % 60_000) / 1000);
                    log.info("429 {} from {}: over {} per minute", path, ip, bucket.limit());
                    return ErrorResponses.write(exchange, HttpStatus.TOO_MANY_REQUESTS,
                            "Too many requests: limit is " + bucket.limit() + " per minute, retry later",
                            headers -> headers.set("Retry-After", String.valueOf(retryAfter)));
                });
    }

    private static String clientIp(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote != null && remote.getAddress() != null ? remote.getAddress().getHostAddress() : "unknown";
    }
}
