package com.fintrack.planning.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Single source of "now" for business dates. FinTrack users are Vietnam-based while the
 * service runs in UTC on Render, so "today" (milestone deadlines, future-dated expenses,
 * default plan start) must be evaluated in {@code Asia/Ho_Chi_Minh}, not the JVM zone.
 */
@Configuration
public class ClockConfig {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Bean
    public Clock clock() {
        return Clock.system(BUSINESS_ZONE);
    }
}
