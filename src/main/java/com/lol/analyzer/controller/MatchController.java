package com.lol.analyzer.controller;

import com.lol.analyzer.analysis.AnalysisException;
import com.lol.analyzer.analysis.view.MatchAnalysisView;
import com.lol.analyzer.model.AnalysisRequest;
import com.lol.analyzer.service.AnalysisHistoryService;
import com.lol.analyzer.service.AnalysisService;
import com.lol.analyzer.service.GeminiService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

/**
 * Submits both rosters: renders {@code result.html}, or the form again with errors and the entered values kept.
 */
@Controller
public class MatchController {

    private final AnalysisService analysisService;
    private final GeminiService geminiService;
    private final AnalysisHistoryService analysisHistoryService;
    private final WebController webController;

    public MatchController(
            AnalysisService analysisService,
            GeminiService geminiService,
            AnalysisHistoryService analysisHistoryService,
            WebController webController) {
        this.analysisService = analysisService;
        this.geminiService = geminiService;
        this.analysisHistoryService = analysisHistoryService;
        this.webController = webController;
    }

    @PostMapping("/analyze")
    public String analyze(AnalysisRequest request, Model model) {
        MatchAnalysisView view;
        try {
            view = analysisService.analyze(request);
        } catch (AnalysisException e) {
            return webController.renderForm(model, request, e.getErrors());
        }
        String aiVerdict = geminiService.getMatchAnalysis(view);
        analysisHistoryService.save(view, aiVerdict);

        model.addAttribute("a", view);
        model.addAttribute("aiCommentary", aiVerdict);
        model.addAttribute("form", request);
        return "result";
    }

    /** Back to the form with the same lobby, e.g. to add champions after the pre-draft estimate. */
    @PostMapping("/edit")
    public String edit(AnalysisRequest request, Model model) {
        return webController.renderForm(model, request, List.of());
    }
}
