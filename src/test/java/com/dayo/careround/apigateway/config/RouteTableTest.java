package com.dayo.careround.apigateway.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// First-match resolution over RouteTable, the same order GatewayRoutesConfig and PublicPathMatcher use.
class RouteTableTest {

    private static String resolve(String path) {
        PathContainer container = PathContainer.parsePath(path);
        for (GatewayRoute route : RouteTable.ROUTES) {
            for (String pattern : route.paths()) {
                if (PathPatternParser.defaultInstance.parse(pattern).matches(container)) return route.id();
            }
        }
        return null;
    }

    @ParameterizedTest
    @CsvSource({
            // Phase 9 read side
            "/api/v1/care-tasks, care-task",
            "/api/v1/care-tasks/medication-log, care-task",
            "/api/v1/care-tasks/stats, care-task",
            "/api/v1/rounds, round",
            "/api/v1/rounds/00000000-0000-0000-0000-000000000001/patients, round",
            "/api/v1/attendance, ward",
            "/api/v1/handovers, ward",
            "/api/v1/handovers/00000000-0000-0000-0000-000000000001, ward",
            "/api/v1/wards, ward",
            "/api/v1/patients, patient",
            "/api/v1/users/directory, user",
            "/api/v1/auth/admin/users, auth",
            "/api/v1/admin/notifications, notification-admin",
            "/api/v1/admin/notifications/failed, notification-admin",
            // specific-before-general and public paths
            "/api/v1/wards/00000000-0000-0000-0000-000000000001/alerts, notification-ward-alerts",
            "/api/v1/wards/00000000-0000-0000-0000-000000000001, ward",
            "/api/v1/alerts/unresolved-ward, notification-alerts",
            "/api/v1/auth/login, auth-public",
            "/api/v1/auth/confirm-email-change, auth-public",
            "/api/v1/auth/change-password, auth",
            "/ws/websocket, realtime-ws",
    })
    void resolvesToTheOwningService(String path, String routeId) {
        assertEquals(routeId, resolve(path), path);
    }

    @Test
    void unknownPrefixesDoNotRoute() {
        assertEquals(null, resolve("/api/v1/admin/other"));
        assertEquals(null, resolve("/api/v2/rounds"));
    }

    // No pattern is listed twice, and no route is shadowed: a concrete path built from each pattern resolves to
    // that pattern's own route (an earlier, broader route would steal it).
    @Test
    void noDuplicateOrShadowedPatterns() {
        Set<String> seen = new HashSet<>();
        for (GatewayRoute route : RouteTable.ROUTES) {
            for (String pattern : route.paths()) {
                assertTrue(seen.add(pattern), "duplicate pattern " + pattern);
                String sample = pattern.replace("/**", "/x/y").replace("*", "x");
                assertEquals(route.id(), resolve(sample), "pattern " + pattern + " (sample " + sample + ") is shadowed");
            }
        }
    }
}
