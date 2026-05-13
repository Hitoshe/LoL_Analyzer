package com.lol.analyzer.controller;

import com.lol.analyzer.repository.ChampionRepository;
import com.lol.analyzer.repository.GameMatchRepository;
import com.lol.analyzer.service.AnalysisHistoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Read-only Thymeleaf page listing persisted analyses plus lightweight DB counters for demos.
 */
@Controller
public class HistoryController {

    private final AnalysisHistoryService analysisHistoryService;
    private final GameMatchRepository gameMatchRepository;
    private final ChampionRepository championRepository;

    public HistoryController(
            AnalysisHistoryService analysisHistoryService,
            GameMatchRepository gameMatchRepository,
            ChampionRepository championRepository) {
        this.analysisHistoryService = analysisHistoryService;
        this.gameMatchRepository = gameMatchRepository;
        this.championRepository = championRepository;
    }

    @GetMapping("/history")
    public String history(Model model) {
        model.addAttribute("histories", analysisHistoryService.findRecent());
        model.addAttribute("savedMatchesCount", gameMatchRepository.count());
        model.addAttribute("championsInDbCount", championRepository.count());
        return "history";
    }
}
