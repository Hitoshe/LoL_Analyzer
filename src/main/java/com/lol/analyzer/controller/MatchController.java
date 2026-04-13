package com.lol.analyzer.controller;

import com.lol.analyzer.model.AnalysisRequest;
import com.lol.analyzer.service.AnalysisService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Map;

@Controller
public class MatchController {

    private final AnalysisService analysisService;

    public MatchController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/analyze")
    public String analyze(AnalysisRequest request, Model model) {
        // Вызываем логику анализа
        Map<String, Object> data = analysisService.analyzeMatch(request);

        // Кладём результаты в "коробку" (Model), которую увидит HTML
        model.addAllAttributes(data);

        // Открыть файл src/main/resources/templates/result.html
        return "result";
    }
}