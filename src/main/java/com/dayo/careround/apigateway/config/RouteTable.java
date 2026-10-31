package com.dayo.careround.apigateway.config;

import com.dayo.careround.apigateway.config.GatewayRoute.Access;
import com.dayo.careround.apigateway.config.GatewayRoute.Service;

import java.util.List;

/**
 * The gateway's routes, in match order: the first route whose path matches wins, so more specific routes come
 * first (notification's /api/v1/wards/{id}/alerts must precede ward-service's /api/v1/wards/**). This list
 * drives both routing (GatewayRoutesConfig) and the public-path check (PublicPathMatcher).
 * To add a route: add a row here (and a URL in RoutesProperties if it is a new service).
 */
public final class RouteTable {

    private RouteTable() {}

    public static final String AUTH_PUBLIC_ROUTE_ID = "auth-public";

    public static final List<GatewayRoute> ROUTES = List.of(
            new GatewayRoute("notification-ward-alerts", Service.NOTIFICATION, Access.JWT,
                    List.of("/api/v1/wards/*/alerts")),
            new GatewayRoute("notification-alerts", Service.NOTIFICATION, Access.JWT,
                    List.of("/api/v1/alerts/**")),
            new GatewayRoute(AUTH_PUBLIC_ROUTE_ID, Service.AUTH, Access.PUBLIC,
                    List.of("/.well-known/jwks.json", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/activate-account",
                            "/api/v1/auth/confirm-email-change")),
            new GatewayRoute("auth", Service.AUTH, Access.JWT,
                    List.of("/api/v1/auth/**")),
            new GatewayRoute("user", Service.USER, Access.JWT,
                    List.of("/api/v1/users/**")),
            new GatewayRoute("ward", Service.WARD, Access.JWT,
                    List.of("/api/v1/wards/**", "/api/v1/attendance/**", "/api/v1/charge-assignments/**",
                            "/api/v1/charge-succession/**", "/api/v1/handovers/**", "/api/v1/shift-definitions/**",
                            "/api/v1/staffing-requirements/**")),
            new GatewayRoute("patient", Service.PATIENT, Access.JWT,
                    List.of("/api/v1/patients/**")),
            new GatewayRoute("round", Service.ROUND, Access.JWT,
                    List.of("/api/v1/rounds/**", "/api/v1/reviews/**", "/api/v1/notes/**", "/api/v1/emergencies/**")),
            new GatewayRoute("care-task", Service.CARE_TASK, Access.JWT,
                    List.of("/api/v1/care-tasks/**", "/api/v1/care-plans/**", "/api/v1/care-protocols/**",
                            "/api/v1/task-type-definitions/**")),
            // WebSocket upgrade. Public at the gateway: realtime-service verifies the JWT on the STOMP CONNECT frame.
            new GatewayRoute("realtime-ws", Service.REALTIME, Access.PUBLIC,
                    List.of("/ws/**"))
    );
}
