package com.fintrack.planning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * FinTrack Planning Service Application.
 *
 * <p>JPA auditing ({@code @CreatedDate}/{@code @LastModifiedDate} on {@code BaseEntity})
 * is enabled in {@link com.fintrack.planning.config.JpaAuditingConfig} — kept out of this
 * class so {@code @WebMvcTest} slices don't try to initialize the full JPA stack.
 *
 * <p>Default port: {@code 8090}
 *
 */
@SpringBootApplication(scanBasePackages = "com.fintrack")
@EnableDiscoveryClient
public class PlanningServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlanningServiceApplication.class, args);
    }
}
