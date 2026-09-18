package com.fintrack.planning.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ultra-lightweight liveness ping used by the frontend cold-start warmup mechanism.
 *
 * <p>On Render's free tier the service spins down after ~15 min idle and takes several
 * minutes to cold-start. The frontend hits {@code GET /ping} as soon as the user lands on
 * the login page so the JVM + Spring context start warming before the dashboard calls
 * {@code /api/v1/plans}.
 *
 * <p>This endpoint intentionally touches no database, repository, or service-layer bean —
 * its sole purpose is to confirm the process is up and serving requests. It is public
 * (see {@code SecurityConfig}) so it can be called without a JWT.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
@Slf4j
@RestController
@Tag(name = "Warmup", description = "Liveness ping for cold-start warmup")
public class WarmupController {

    /** Logged once, the first time {@code /ping} is hit after a (cold) start. */
    private volatile boolean firstPingLogged = false;

    private final long contextStartedAt = System.currentTimeMillis();

    @Operation(summary = "Lightweight liveness ping — does not touch the DB or service layer")
    @GetMapping("/ping")
    public ResponseEntity<Void> ping() {
        if (!firstPingLogged) {
            firstPingLogged = true;
            log.info("First /ping received {}ms after controller init (warmup traffic arrived)",
                    System.currentTimeMillis() - contextStartedAt);
        }
        return ResponseEntity.ok().build();
    }
}
