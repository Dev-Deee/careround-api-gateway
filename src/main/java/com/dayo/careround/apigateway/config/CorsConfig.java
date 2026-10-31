package com.dayo.careround.apigateway.config;

import com.dayo.careround.apigateway.filter.CorrelationIdFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.time.Clock;
import java.util.List;

// CORS is handled here only; the services configure none, so their responses add no conflicting headers.
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter(@Value("${app.cors.allowed-origins:http://localhost:*}") List<String> allowedOrigins,
                                       @Value("${app.cors.allow-credentials:false}") boolean allowCredentials) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", CorrelationIdFilter.HEADER));
        config.setExposedHeaders(List.of(CorrelationIdFilter.HEADER));
        config.setAllowCredentials(allowCredentials);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
