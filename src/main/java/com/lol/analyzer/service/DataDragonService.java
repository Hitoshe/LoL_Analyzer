package com.lol.analyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class DataDragonService {

    private final Map<Long, String> championMap = new HashMap<>();
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper(); // Помощник для парсинга

    // Внедряем RestTemplate через конструктор
    public DataDragonService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    public void init() {
        try {
            // 1. Сначала узнаем актуальную версию
            String versionUrl = "https://ddragon.leagueoflegends.com/api/versions.json";
            String[] versions = restTemplate.getForObject(versionUrl, String[].class);

            if (versions == null || versions.length == 0) {
                throw new RuntimeException("Не удалось получить список версий");
            }

            String latestVersion = versions[0]; // Берем самую первую (свежую)
            System.out.println("=== DATA DRAGON: Detected latest version: " + latestVersion + " ===");

            // 2. Теперь подставляем эту версию в ссылку на чемпионов
            String champUrl = "https://ddragon.leagueoflegends.com/cdn/" + latestVersion + "/data/en_US/champion.json";

            String jsonResponse = restTemplate.getForObject(champUrl, String.class);
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode data = root.get("data");

            if (data != null) {
                data.fields().forEachRemaining(entry -> {
                    JsonNode champ = entry.getValue();
                    long id = Long.parseLong(champ.get("key").asText());
                    String name = champ.get("name").asText();
                    championMap.put(id, name);
                });
                System.out.println("=== DATA DRAGON: Loaded " + championMap.size() + " champions ===");
            }
        } catch (Exception e) {
            System.err.println("=== DATA DRAGON ERROR ===: " + e.getMessage());
        }
    }

    public String getChampionName(long id) {
        return championMap.getOrDefault(id, "Unknown (" + id + ")");
    }
}