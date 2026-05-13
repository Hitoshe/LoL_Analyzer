package com.lol.analyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads Riot Data Dragon {@code champion.json} once at startup and keeps an in-memory id→name map.
 * Used to resolve champion display names without extra HTTP calls during normal requests.
 */
@Service
public class DataDragonService {

    private final Map<Long, String> championMap = new HashMap<>();
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DataDragonService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    public void init() {
        try {
            String versionUrl = "https://ddragon.leagueoflegends.com/api/versions.json";
            String[] versions = restTemplate.getForObject(versionUrl, String[].class);

            if (versions == null || versions.length == 0) {
                throw new RuntimeException("Data Dragon: empty versions list");
            }

            String latestVersion = versions[0];
            System.out.println("=== DATA DRAGON: latest patch version: " + latestVersion + " ===");

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
                System.out.println("=== DATA DRAGON: loaded " + championMap.size() + " champions ===");
            }
        } catch (Exception e) {
            System.err.println("=== DATA DRAGON ERROR ===: " + e.getMessage());
        }
    }

    public String getChampionName(long id) {
        return championMap.getOrDefault(id, "Unknown (" + id + ")");
    }
}
