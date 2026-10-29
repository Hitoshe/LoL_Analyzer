package com.lol.analyzer.controller;

import com.lol.analyzer.model.Summoner;
import com.lol.analyzer.riot.PlayerNotFoundException;
import com.lol.analyzer.riot.Region;
import com.lol.analyzer.riot.RiotApiException;
import com.lol.analyzer.riot.RiotId;
import com.lol.analyzer.service.SummonerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * JSON helper endpoint: {@code GET /api/summoner/{name}/{tag}?region=EUW} mirrors the data loaded for the UI pipeline.
 */
@RestController
@RequestMapping("/api/summoner")
public class SummonerController {

    private final SummonerService summonerService;
    private final Region defaultRegion;

    public SummonerController(SummonerService summonerService, Region defaultRegion) {
        this.summonerService = summonerService;
        this.defaultRegion = defaultRegion;
    }

    @GetMapping("/{name}/{tag}")
    public Summoner getSummonerInfo(@PathVariable String name, @PathVariable String tag,
                                    @RequestParam(required = false) String region) {
        return summonerService.getAccount(Region.parse(region).orElse(defaultRegion), new RiotId(name.trim(), tag.trim()));
    }

    @ExceptionHandler(PlayerNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(PlayerNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(RiotApiException.class)
    public ResponseEntity<Map<String, String>> riotError(RiotApiException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
    }
}
