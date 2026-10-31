package com.dayo.careround.apigateway.config;

import com.dayo.careround.apigateway.filter.AuthRateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.springframework.cloud.gateway.support.RouteMetadataUtils.CONNECT_TIMEOUT_ATTR;
import static org.springframework.cloud.gateway.support.RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR;

// Builds the routes from RouteTable, in its order. Headers (including Authorization) are forwarded unchanged.
@Configuration
@RequiredArgsConstructor
public class GatewayRoutesConfig {

    private final RoutesProperties routesProperties;
    private final AuthRateLimitFilter authRateLimitFilter;

    @Bean
    public RouteLocator careRoundRoutes(RouteLocatorBuilder builder) {
        RouteLocatorBuilder.Builder routes = builder.routes();
        for (int i = 0; i < RouteTable.ROUTES.size(); i++) {
            GatewayRoute route = RouteTable.ROUTES.get(i);
            int order = i;
            routes.route(route.id(), r -> {
                var spec = r.order(order)
                        .path(route.paths().toArray(String[]::new));
                var withFilters = RouteTable.AUTH_PUBLIC_ROUTE_ID.equals(route.id())
                        ? spec.filters(f -> f.filter(authRateLimitFilter))
                        : spec;
                return withFilters
                        .metadata(CONNECT_TIMEOUT_ATTR, routesProperties.getConnectTimeoutMs())
                        .metadata(RESPONSE_TIMEOUT_ATTR, routesProperties.getResponseTimeoutMs())
                        .uri(routesProperties.urlFor(route.service()));
            });
        }
        return routes.build();
    }
}
