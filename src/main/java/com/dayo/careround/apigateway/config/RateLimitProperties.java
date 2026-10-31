package com.dayo.careround.apigateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private int loginPerMinute = 10;
    // A whole ward often shares one IP (NAT) and every tablet refreshes every 15 minutes, so refresh gets more room.
    private int refreshPerMinute = 30;
    private int activatePerMinute = 10;
    private int redisTimeoutMs = 500;

    public record Bucket(String name, int limit) {}

    // Only these three paths are limited (the JWKS endpoint on the same route is not).
    public Bucket bucketFor(String path) {
        return switch (path) {
            case "/api/v1/auth/login" -> new Bucket("login", loginPerMinute);
            case "/api/v1/auth/refresh" -> new Bucket("refresh", refreshPerMinute);
            case "/api/v1/auth/activate-account" -> new Bucket("activate-account", activatePerMinute);
            default -> null;
        };
    }
}
