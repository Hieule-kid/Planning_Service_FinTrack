package com.fintrack.planning.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness ping used by the frontend cold-start warmup mechanism.
 *
 */
@Slf4j
@RestController
@Tag(name = "Warmup", description = "Liveness ping for cold-start warmup")
public class WarmupController {

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
