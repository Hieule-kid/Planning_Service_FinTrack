package com.fintrack.planning.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA auditing ({@code @CreatedDate}, {@code @LastModifiedDate}
 * on {@link com.fintrack.core.base.BaseEntity}).
 *
 * <p>Kept as a standalone {@code @Configuration} (rather than an annotation directly on
 * {@code PlanningServiceApplication}) so that {@code @WebMvcTest} slices — which only
 * scan web-layer beans — don't attempt to initialize the full JPA auditing stack.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
