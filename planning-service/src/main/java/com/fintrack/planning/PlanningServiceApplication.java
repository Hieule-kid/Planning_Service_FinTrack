package com.fintrack.planning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * FinTrack Planning Service Application.
 *
 * <p>JPA auditing ({@code @CreatedDate}/{@code @LastModifiedDate} on {@code BaseEntity})
 * is enabled in {@link com.fintrack.planning.config.JpaAuditingConfig} — kept out of this
 * class so {@code @WebMvcTest} slices don't try to initialize the full JPA stack.
 *
 * <p>{@link UserDetailsServiceAutoConfiguration} is excluded because this service authenticates
 * exclusively through {@link com.fintrack.planning.filter.JwtAuthFilter} and has no user store.
 * Left enabled, Spring Boot creates a default in-memory user and prints its random password to the
 * logs on every startup.
 *
 * <p>Default port: {@code 8090}
 *
 */
@SpringBootApplication(scanBasePackages = "com.fintrack", exclude = UserDetailsServiceAutoConfiguration.class)
@EnableDiscoveryClient
public class PlanningServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlanningServiceApplication.class, args);
    }
}
