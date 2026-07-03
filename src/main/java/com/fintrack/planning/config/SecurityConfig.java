package com.fintrack.planning.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.core.dto.ApiResponse;
import com.fintrack.core.exception.ErrorCode;
import com.fintrack.planning.filter.JwtAuthFilter;
import com.fintrack.planning.service.JwtService;
import lombok.RequiredArgsConstructor;
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

    /**
     * Configures the HTTP security filter chain.
     *
     * @param http the {@link HttpSecurity} to configure
     * @param jwtAuthFilter the {@link JwtAuthFilter} bean
     * @return the built {@link SecurityFilterChain}
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
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
}
