package com.dayo.careround.apigateway.config;

import java.util.List;

/**
 * One row of the route table.
 *
 * @param id       route id (also used in logs)
 * @param service  which RoutesProperties URL the route forwards to
 * @param access   PUBLIC routes are not JWT-checked at the gateway
 * @param paths    Spring path patterns
 */
public record GatewayRoute(String id, Service service, Access access, List<String> paths) {

    public enum Access { PUBLIC, JWT }

    public enum Service { AUTH, USER, WARD, PATIENT, ROUND, CARE_TASK, NOTIFICATION, REALTIME }

    public boolean isPublic() {
        return access == Access.PUBLIC;
    }
}
