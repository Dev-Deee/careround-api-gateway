package com.dayo.careround.apigateway.exception;

import com.dayo.careround.apigateway.filter.CorrelationIdFilter;
import com.dayo.careround.apigateway.utils.ErrorResponses;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

/**
 * Every gateway-generated error gets the {status, error, message, timestamp} body: no route 404, downstream
 * unreachable 503, downstream too slow 504, anything unexpected 500 (logged at ERROR with the stack trace).
 * Messages are fixed strings, so no exception detail reaches the client.
 */
@Slf4j
@Component
@Order(-2) // before Spring Boot's default handler
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        String requestId = exchange.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        String target = exchange.getRequest().getMethod() + " " + exchange.getRequest().getPath();

        if (hasCause(ex, ConnectException.class) || hasCause(ex, UnknownHostException.class)) {
            log.warn("requestId={} {}: downstream unavailable ({})", requestId, target, rootMessage(ex));
            return ErrorResponses.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable, please retry");
        }
        if (ex instanceof ResponseStatusException rse) {
            HttpStatusCode code = rse.getStatusCode();
            if (code.value() == HttpStatus.NOT_FOUND.value()) {
                return ErrorResponses.write(exchange, HttpStatus.NOT_FOUND, "No route for " + exchange.getRequest().getPath());
            }
            if (code.value() == HttpStatus.GATEWAY_TIMEOUT.value()) {
                log.warn("requestId={} {}: downstream timed out", requestId, target);
                return ErrorResponses.write(exchange, HttpStatus.GATEWAY_TIMEOUT, "Service did not respond in time");
            }
            if (code.value() == HttpStatus.SERVICE_UNAVAILABLE.value()) {
                log.warn("requestId={} {}: downstream unavailable ({})", requestId, target, rootMessage(ex));
                return ErrorResponses.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable, please retry");
            }
            HttpStatus status = HttpStatus.resolve(code.value());
            if (status != null && status.is4xxClientError()) {
                return ErrorResponses.write(exchange, status, status.getReasonPhrase());
            }
        }
        if (hasCause(ex, TimeoutException.class)) {
            log.warn("requestId={} {}: downstream timed out", requestId, target);
            return ErrorResponses.write(exchange, HttpStatus.GATEWAY_TIMEOUT, "Service did not respond in time");
        }

        log.error("requestId={} {}: unexpected gateway error: {}", requestId, target, ex.toString(), ex);
        return ErrorResponses.write(exchange, HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private static boolean hasCause(Throwable ex, Class<? extends Throwable> type) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (type.isInstance(t)) return true;
            if (t.getCause() == t) break;
        }
        return false;
    }

    private static String rootMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t.getClass().getSimpleName();
    }
}
