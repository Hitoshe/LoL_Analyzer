package com.lol.analyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lol.analyzer.model.LaneMatchup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Calls Google Gemini REST API to produce a short Russian tactical summary for the UI.
 * The prompt is built from team win chances and per-lane {@link LaneMatchup} rows.
 */
@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String getMatchAnalysis(double chanceA, double chanceB, List<?> matchups) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=" + apiKey;

        StringBuilder prompt = new StringBuilder();
        prompt.append("Ты — экспертный аналитик League of Legends. Проанализируй данные матча и дай краткий прогноз на русском языке.\n");
        prompt.append(String.format("Шанс Синих: %.1f%%, Шанс Красных: %.1f%%.\n", chanceA, chanceB));
        prompt.append("Данные по линиям:\n");

        for (Object mObj : matchups) {
            LaneMatchup m = (LaneMatchup) mObj;
            prompt.append(String.format("- Линия %s: %s на герое %s (Ранг: %s) VS %s на герое %s (Ранг: %s). Статистика линии: %s, Сложность: %s\n",
                    m.getLane(),
                    m.getPlayerA().getGameName(), m.getChampionA(), m.getPlayerA().getTier(),
                    m.getPlayerB().getGameName(), m.getChampionB(), m.getPlayerB().getTier(),
                    m.getPrediction(), m.getDifficulty()));
        }

        prompt.append("\nНапиши на русском языке 3 предложения: кто победит, почему, и дай один конкретный тактический совет.");

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt.toString())
                        ))
                )
        );

        try {
            System.out.println("--- GEMINI REQUEST ---");

            String jsonResponse = restTemplate.postForObject(url, requestBody, String.class);

            JsonNode response = objectMapper.readTree(jsonResponse);

            if (response != null && response.has("candidates")) {
                return response.path("candidates").get(0)
                        .path("content").path("parts").get(0)
                        .path("text").asText();
            }
            return "AI прислал пустой ответ.";

        } catch (Exception e) {
            System.err.println("GEMINI ERROR: " + e.getMessage());
            e.printStackTrace();
            return "Аналитик AI временно в тильте. Полагайтесь на свои силы!";
        }
    }
}
