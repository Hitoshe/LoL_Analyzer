package com.lol.analyzer.controller;

import com.lol.analyzer.repository.ChampionRepository;
import com.lol.analyzer.repository.GameMatchRepository;
import com.lol.analyzer.service.AnalysisHistoryService;
import com.lol.analyzer.service.ChampionStatsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Read-only page listing persisted analyses, their real outcomes, prediction calibration and DB counters.
 */
@Controller
public class HistoryController {

    private final AnalysisHistoryService analysisHistoryService;
    private final GameMatchRepository gameMatchRepository;
    private final ChampionRepository championRepository;
    private final ChampionStatsService championStatsService;

    public HistoryController(
            AnalysisHistoryService analysisHistoryService,
            GameMatchRepository gameMatchRepository,
            ChampionRepository championRepository,
            ChampionStatsService championStatsService) {
        this.analysisHistoryService = analysisHistoryService;
        this.gameMatchRepository = gameMatchRepository;
        this.championRepository = championRepository;
        this.championStatsService = championStatsService;
    }

    @GetMapping("/history")
    public String history(Model model) {
        model.addAttribute("histories", analysisHistoryService.findRecent());
        model.addAttribute("calibration", analysisHistoryService.calibration());
        model.addAttribute("savedMatchesCount", gameMatchRepository.count());
        model.addAttribute("championsInDbCount", championRepository.count());
        model.addAttribute("metaSample", championStatsService.sampleMatches());
        model.addAttribute("metaWindowDays", championStatsService.windowDays());
        return "history";
    }
}
