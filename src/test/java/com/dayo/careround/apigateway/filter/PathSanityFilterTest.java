package com.dayo.careround.apigateway.filter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class PathSanityFilterTest {

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/notes//commit", "/api/v1/reviews//acknowledge-tasks", "//api/v1/wards", "/api/v1/wards/a%2Fb", "/api/v1/wards/a%5cb"})
    void malformedPaths(String path) {
        assertThat(PathSanityFilter.isMalformed(path)).as(path).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/notes/0b5c7c4e-1111-4222-8333-944445555666/commit", "/api/v1/wards", "/", "/ws", "/api/v1/wards?since=2026-01-01T00:00:00Z"})
    void normalPaths(String path) {
        assertThat(PathSanityFilter.isMalformed(path)).as(path).isFalse();
    }

    @Test
    void malformedPathIs400AndNotForwarded() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.method(HttpMethod.POST, URI.create("http://localhost/api/v1/notes//commit")));
        AtomicBoolean forwarded = new AtomicBoolean(false);
        new PathSanityFilter().filter(exchange, ex -> { forwarded.set(true); return Mono.empty(); }).block();
        assertThat(forwarded).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"status\":400").contains("Malformed request path");
    }
}
