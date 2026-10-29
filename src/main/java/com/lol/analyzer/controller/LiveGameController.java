package com.lol.analyzer.controller;

import com.lol.analyzer.analysis.AnalysisException;
import com.lol.analyzer.riot.Region;
import com.lol.analyzer.riot.RiotApiException;
import com.lol.analyzer.service.LiveGameService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * {@code GET /api/live-game?riotId=Name%23TAG&region=EUW}: both rosters of the player's current game for the form.
 */
@RestController
public class LiveGameController {

    private final LiveGameService liveGameService;
    private final Region defaultRegion;

    public LiveGameController(LiveGameService liveGameService, Region defaultRegion) {
        this.liveGameService = liveGameService;
        this.defaultRegion = defaultRegion;
    }

    @GetMapping("/api/live-game")
    public ResponseEntity<?> liveGame(@RequestParam String riotId, @RequestParam(required = false) String region) {
        try {
            return ResponseEntity.ok(liveGameService.find(Region.parse(region).orElse(defaultRegion), riotId));
        } catch (AnalysisException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (RiotApiException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
        }
    }
}
