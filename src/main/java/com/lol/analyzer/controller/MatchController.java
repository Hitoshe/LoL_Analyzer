package com.lol.analyzer.controller;

import com.lol.analyzer.model.AnalysisRequest;
import com.lol.analyzer.service.AnalysisHistoryService;
import com.lol.analyzer.service.AnalysisService;
import com.lol.analyzer.service.GeminiService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;
import java.util.Map;

/**
 * Thymeleaf flow for submitting both rosters and rendering {@code result.html}.
 */
@Controller
public class MatchController {

    private final AnalysisService analysisService;
    private final GeminiService geminiService;
    private final AnalysisHistoryService analysisHistoryService;

    public MatchController(
            AnalysisService analysisService,
            GeminiService geminiService,
            AnalysisHistoryService analysisHistoryService) {
        this.analysisService = analysisService;
        this.geminiService = geminiService;
        this.analysisHistoryService = analysisHistoryService;
    }

    @PostMapping("/analyze")
    public String analyze(AnalysisRequest request, Model model) {
        Map<String, Object> data = analysisService.analyzeMatch(request);

        if (!data.containsKey("error")) {
            String aiVerdict = geminiService.getMatchAnalysis(
                    (double) data.get("teamA_chance"),
                    (double) data.get("teamB_chance"),
                    (List<?>) data.get("matchups")
            );
            model.addAttribute("aiCommentary", aiVerdict);
            analysisHistoryService.saveSuccessfulAnalysis(data, aiVerdict);
        }

        model.addAllAttributes(data);
        return "result";
    }
}
