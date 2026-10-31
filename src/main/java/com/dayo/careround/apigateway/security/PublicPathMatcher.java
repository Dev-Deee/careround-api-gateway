package com.dayo.careround.apigateway.security;

import com.dayo.careround.apigateway.config.GatewayRoute;
import com.dayo.careround.apigateway.config.RouteTable;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;
import java.util.Map;

/**
 * Decides from the route table whether a path skips the gateway JWT check. Mirrors routing exactly: the first
 * route (in RouteTable order) whose pattern matches decides, so /api/v1/auth/login is public even though
 * /api/v1/auth/** is protected. A path no route matches is not public (and gets a 404 anyway).
 */
@Component
public class PublicPathMatcher {

    private final List<Map.Entry<PathPattern, GatewayRoute>> patterns;

    public PublicPathMatcher() {
        this(RouteTable.ROUTES);
    }

    PublicPathMatcher(List<GatewayRoute> routes) {
        PathPatternParser parser = PathPatternParser.defaultInstance;
        this.patterns = routes.stream()
                .flatMap(route -> route.paths().stream().map(p -> Map.entry(parser.parse(p), route)))
                .toList();
    }

    public boolean isPublic(String path) {
        PathContainer container = PathContainer.parsePath(path);
        for (Map.Entry<PathPattern, GatewayRoute> entry : patterns) {
            if (entry.getKey().matches(container)) {
                return entry.getValue().isPublic();
            }
        }
        return false;
    }
}
