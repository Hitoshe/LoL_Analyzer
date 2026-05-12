package com.lol.analyzer.controller;

import com.lol.analyzer.model.AnalysisRequest;
import com.lol.analyzer.service.AnalysisService;
import com.lol.analyzer.service.GeminiService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;
import java.util.Map;

@Controller
public class MatchController {

    private final AnalysisService analysisService;
    private final GeminiService geminiService;

    public MatchController(AnalysisService analysisService, GeminiService geminiService) {
        this.analysisService = analysisService;
        this.geminiService = geminiService;
    }

    @PostMapping("/analyze")
    public String analyze(AnalysisRequest request, Model model) {
        // Вызываем логику анализа
        Map<String, Object> data = analysisService.analyzeMatch(request);

        // Если ошибок нет, вызываем AI
        if (!data.containsKey("error")) {
            String aiVerdict = geminiService.getMatchAnalysis(
                    (double) data.get("teamA_chance"),
                    (double) data.get("teamB_chance"),
                    (List<Map<String, Object>>) data.get("matchups")
            );
            model.addAttribute("aiCommentary", aiVerdict);
        }


        // Кладём результаты в "коробку" (Model), которую увидит HTML
        model.addAllAttributes(data);

        // Открыть файл src/main/resources/templates/result.html
        return "result";
    }


}