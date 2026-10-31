package com.dayo.careround.apigateway.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

// Writes the same {status, error, message, timestamp} body the services use, so clients see one error shape.
public final class ErrorResponses {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ErrorResponses() {}

    public static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String message) {
        return write(exchange, status, message, headers -> { });
    }

    public static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String message, Consumer<HttpHeaders> extraHeaders) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("timestamp", Instant.now().toString());

        byte[] bytes;
        try {
            bytes = MAPPER.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = ("{\"status\":" + status.value() + "}").getBytes(StandardCharsets.UTF_8);
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        extraHeaders.accept(response.getHeaders());
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
