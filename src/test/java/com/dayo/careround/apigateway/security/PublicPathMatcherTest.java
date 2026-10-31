package com.dayo.careround.apigateway.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PublicPathMatcherTest {

    private final PublicPathMatcher matcher = new PublicPathMatcher();

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/activate-account",
            "/api/v1/auth/confirm-email-change", "/.well-known/jwks.json", "/ws", "/ws/info"})
    void publicPaths(String path) {
        assertThat(matcher.isPublic(path)).as(path).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/logout", "/api/v1/auth/admin/create-user", "/api/v1/auth/login/extra",
            "/api/v1/users/me", "/api/v1/wards", "/api/v1/wards/123", "/api/v1/wards/123/alerts", "/api/v1/alerts/x/acknowledge",
            "/api/v1/patients", "/api/v1/emergencies", "/api/v1/care-tasks/1/medication-details",
            "/actuator/env", "/unknown", "/"})
    void protectedOrUnroutedPaths(String path) {
        assertThat(matcher.isPublic(path)).as(path).isFalse();
    }
}
