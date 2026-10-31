package com.dayo.careround.apigateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.routes")
public class RoutesProperties {
    private String authUrl = "http://localhost:8081";
    private String userUrl = "http://localhost:8082";
    private String wardUrl = "http://localhost:8083";
    private String patientUrl = "http://localhost:8084";
    private String roundUrl = "http://localhost:8085";
    private String careTaskUrl = "http://localhost:8086";
    private String notificationUrl = "http://localhost:8087";
    private String realtimeUrl = "http://localhost:8088";
    private int connectTimeoutMs = 2000;
    private int responseTimeoutMs = 30000;

    public String urlFor(GatewayRoute.Service service) {
        return switch (service) {
            case AUTH -> authUrl;
            case USER -> userUrl;
            case WARD -> wardUrl;
            case PATIENT -> patientUrl;
            case ROUND -> roundUrl;
            case CARE_TASK -> careTaskUrl;
            case NOTIFICATION -> notificationUrl;
            // WebSocket routing needs a ws:// target.
            case REALTIME -> realtimeUrl.replaceFirst("^http", "ws");
        };
    }
}
