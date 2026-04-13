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
            String url = "https://ddragon.leagueoflegends.com/cdn/14.1.1/data/en_US/champion.json";

            // 1. Получаем ответ как обычную строку (String)
            String jsonResponse = restTemplate.getForObject(url, String.class);

            // 2. Превращаем строку в дерево JSON через ObjectMapper
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