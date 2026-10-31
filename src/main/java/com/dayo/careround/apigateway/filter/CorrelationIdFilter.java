package com.dayo.careround.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * X-Request-Id on every request: an inbound value is kept (if it looks like an id), otherwise one is generated.
 * It is added to the downstream request and the response, and every gateway access-log line carries it.
 * A WebFilter (not a route filter) so 401s, 404s and CORS rejections get one too.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements WebFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String ATTRIBUTE = CorrelationIdFilter.class.getName() + ".requestId";
    // Anything else (too long, control characters) is replaced, so the value is safe to log and echo.
    private static final Pattern ACCEPTED = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String inbound = exchange.getRequest().getHeaders().getFirst(HEADER);
        String requestId = inbound != null && ACCEPTED.matcher(inbound).matches() ? inbound : UUID.randomUUID().toString();

        ServerHttpRequest request = exchange.getRequest().mutate().headers(h -> h.set(HEADER, requestId)).build();
        ServerWebExchange mutated = exchange.mutate().request(request).build();
        mutated.getAttributes().put(ATTRIBUTE, requestId);
        mutated.getResponse().getHeaders().set(HEADER, requestId);
        mutated.getResponse().beforeCommit(() -> {
            mutated.getResponse().getHeaders().set(HEADER, requestId);
            return Mono.empty();
        });

        long start = System.nanoTime();
        return chain.filter(mutated).doFinally(signal -> {
            Route route = mutated.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            log.info("requestId={} {} {} -> {} route={} {}ms", requestId, request.getMethod(), request.getPath(),
                    mutated.getResponse().getStatusCode(), route != null ? route.getId() : "-",
                    (System.nanoTime() - start) / 1_000_000);
        });
    }
}
