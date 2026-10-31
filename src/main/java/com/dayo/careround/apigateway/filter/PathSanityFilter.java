package com.dayo.careround.apigateway.filter;

import com.dayo.careround.apigateway.utils.ErrorResponses;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Locale;

/**
 * Rejects paths with an empty segment ("//") or an encoded slash/backslash with 400 before routing. The forwarded
 * URI would otherwise be normalised, so e.g. POST /api/v1/notes//commit (an empty note id) reached round-service as
 * /api/v1/notes/commit, a different endpoint. Called directly, the services' Spring Security firewall rejects these
 * paths too; this keeps the gateway from changing what the client asked for.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1) // after CorrelationIdFilter, so the 400 carries X-Request-Id
public class PathSanityFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // getPath() keeps the path as received (still encoded, empty segments intact).
        String rawPath = exchange.getRequest().getPath().value();
        if (isMalformed(rawPath)) {
            log.info("400 malformed path {} {}", exchange.getRequest().getMethod(), rawPath);
            return ErrorResponses.write(exchange, HttpStatus.BAD_REQUEST, "Malformed request path (empty segment or encoded slash)");
        }
        return chain.filter(exchange);
    }

    static boolean isMalformed(String rawPath) {
        if (rawPath == null) {
            return false;
        }
        String lower = rawPath.toLowerCase(Locale.ROOT);
        return rawPath.contains("//") || lower.contains("%2f") || lower.contains("%5c") || rawPath.contains("\\");
    }
}
