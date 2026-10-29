package com.lol.analyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lol.analyzer.analysis.view.MatchAnalysisView;
import com.lol.analyzer.analysis.view.PlayerCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Calls Google Gemini REST API to produce a short Russian tactical summary for the UI.
 * The prompt carries the computed chances, factor breakdown and per-lane player context; the model only explains it.
 */
@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private final String apiKey;
    private final String model;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiService(
            RestTemplate restTemplate,
            @Value("${gemini.api.key:}") String apiKey,
            @Value("${gemini.model:gemini-flash-latest}") String model) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    public String getMatchAnalysis(MatchAnalysisView view) {
        if (!isConfigured()) {
            return null;
        }
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", buildPrompt(view))))));
        try {
            JsonNode response = objectMapper.readTree(restTemplate.postForObject(url, requestBody, String.class));
            if (response != null && response.has("candidates")) {
                return response.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
            }
            return "AI прислал пустой ответ.";
        } catch (Exception e) {
            log.warn("Gemini request failed: {}", e.getMessage());
            return "Аналитик AI временно недоступен.";
        }
    }

    static String buildPrompt(MatchAnalysisView view) {
        StringBuilder p = new StringBuilder();
        p.append("Ты — аналитик League of Legends. Ниже уже посчитанный прогноз; не пересчитывай цифры, объясни их.\n");
        p.append(String.format(Locale.ROOT, "Сервер: %s. Шанс синих до пиков: %.1f%%", view.region().getCode(), view.preDraftBlue()));
        if (view.hasDraft()) {
            p.append(String.format(Locale.ROOT, ", после пиков: %.1f%%", view.postDraftBlue()));
        }
        p.append(". Уверенность прогноза: ").append(view.confidenceLabel()).append(".\n");
        p.append("Факторы (Elo синих / красных, влияние на шанс синих в п.п.):\n");
        for (MatchAnalysisView.FactorRow f : view.factors()) {
            p.append(String.format(Locale.ROOT, "- %s: %s / %s, %+.1f п.п.\n", f.label(), f.blue(), f.red(), f.impact()));
        }
        p.append("Линии:\n");
        for (MatchAnalysisView.LaneRow lane : view.lanes()) {
            p.append("- ").append(lane.lane()).append(": ")
                    .append(describe(lane.blue())).append(" VS ").append(describe(lane.red()));
            if (lane.counterNote() != null) {
                p.append(". ").append(lane.counterNote());
            }
            p.append('\n');
        }
        view.synergyNotes().forEach(n -> p.append("- ").append(n).append('\n'));
        p.append("\nНапиши по-русски 3–4 предложения: кто фаворит и почему (опирайся на самые весомые факторы), ");
        p.append("на какую линию или игрока обратить внимание, и один конкретный тактический совет синим.");
        return p.toString();
    }

    private static String describe(PlayerCard c) {
        if (c == null) {
            return "нет игрока";
        }
        StringBuilder sb = new StringBuilder(c.riotId()).append(" (").append(c.rankLabel());
        if (c.championName() != null) {
            sb.append(", ").append(c.championName());
        }
        sb.append(", ").append(c.roleFitLabel()).append(", ").append(c.recentLabel()).append(", ").append(c.sessionLabel());
        if (c.championLabel() != null) {
            sb.append(", ").append(c.championLabel());
        }
        return sb.append(')').toString();
    }
}
