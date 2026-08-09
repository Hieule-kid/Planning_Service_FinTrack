package com.fintrack.planning.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.core.dto.ApiResponse;
import com.fintrack.core.exception.ErrorCode;
import com.fintrack.planning.filter.JwtAuthFilter;
import com.fintrack.planning.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security configuration for the Planning Service.
 *
 * <p>Security model:
 * <ul>
 *   <li>Stateless JWT — no HTTP session is created</li>
 *   <li>This service only VALIDATES tokens issued by {@code auth-service}; it never issues them</li>
 *   <li>Public endpoints: {@code /actuator/health}, Swagger UI</li>
 *   <li>All {@code /api/v1/plans/**} endpoints require a valid JWT</li>
 *   <li>Unauthenticated requests receive a {@code 401} with the standard {@link ApiResponse} envelope</li>
 * </ul>
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    @Value("${app.cors.allowed-origin}")
    private String allowedOrigin;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter,
                                                    CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/actuator/health",
                    "/actuator/info",
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/v3/api-docs",
                    "/v3/api-docs/**",
                    "/v3/api-docs.yaml"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(ErrorCode.UNAUTHORIZED.getHttpStatus().value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    ApiResponse<Void> body = ApiResponse.error(
                            ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
                    response.getWriter().write(objectMapper.writeValueAsString(body));
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(ErrorCode.FORBIDDEN.getHttpStatus().value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    ApiResponse<Void> body = ApiResponse.error(
                            ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getMessage());
                    response.getWriter().write(objectMapper.writeValueAsString(body));
                })
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Creates the JWT authentication filter bean.
     *
     * @param jwtService the validation-only JWT service
     * @return configured {@link JwtAuthFilter}
     */
    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtService jwtService) {
        return new JwtAuthFilter(jwtService);
    }

    /**
     * Prevents Spring Boot from auto-registering {@link JwtAuthFilter} in the
     * servlet container's outer filter chain. The filter is already registered
     * inside {@link SecurityFilterChain} via {@code addFilterBefore}; without this,
     * it would run twice (once in Spring Security's chain, once at the container level).
     */
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtFilterRegistration(JwtAuthFilter jwtAuthFilter) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(jwtAuthFilter);
        registration.setEnabled(false);
        return registration;
    }
}
