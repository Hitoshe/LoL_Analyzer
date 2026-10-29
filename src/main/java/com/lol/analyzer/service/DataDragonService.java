package com.lol.analyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lol.analyzer.model.ChampionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Keeps the current Data Dragon patch in memory (champion ids, names, Russian aliases, icons, class tags)
 * and swaps in a new {@link ChampionCatalog} when Riot publishes a new version. No API key is needed.
 */
@Service
public class DataDragonService {

    private static final Logger log = LoggerFactory.getLogger(DataDragonService.class);
    private static final String CDN = "https://ddragon.leagueoflegends.com";
    private static final long RETRY_EMPTY_MS = 60_000;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final boolean loadOnStartup;
    private final String localizedLocale;

    private volatile ChampionCatalog catalog = ChampionCatalog.empty();
    private volatile long lastAttemptMs;

    public DataDragonService(
            RestTemplate restTemplate,
            @Value("${analyzer.ddragon.load-on-startup:true}") boolean loadOnStartup,
            @Value("${analyzer.ddragon.localized-locale:ru_RU}") String localizedLocale) {
        this.restTemplate = restTemplate;
        this.loadOnStartup = loadOnStartup;
        this.localizedLocale = localizedLocale;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        if (loadOnStartup) {
            refresh();
        }
    }

    @Scheduled(fixedDelayString = "${analyzer.ddragon.refresh-ms:3600000}", initialDelayString = "${analyzer.ddragon.refresh-ms:3600000}")
    public void scheduledRefresh() {
        refresh();
    }

    public synchronized void refresh() {
        lastAttemptMs = System.currentTimeMillis();
        try {
            String[] versions = restTemplate.getForObject(CDN + "/api/versions.json", String[].class);
            if (versions == null || versions.length == 0) {
                throw new IllegalStateException("empty versions list");
            }
            String latest = versions[0];
            if (latest.equals(catalog.version())) {
                return;
            }
            Map<String, String> localized = loadLocalizedNames(latest);
            JsonNode data = objectMapper.readTree(
                    restTemplate.getForObject(CDN + "/cdn/" + latest + "/data/en_US/champion.json", String.class)).get("data");
            List<ChampionInfo> champions = new ArrayList<>();
            data.properties().forEach(entry -> {
                JsonNode c = entry.getValue();
                String key = c.get("id").asText();
                List<String> tags = new ArrayList<>();
                c.path("tags").forEach(t -> tags.add(t.asText()));
                champions.add(new ChampionInfo(
                        Long.parseLong(c.get("key").asText()),
                        key,
                        c.get("name").asText(),
                        localized.get(key),
                        CDN + "/cdn/" + latest + "/img/champion/" + c.path("image").path("full").asText(key + ".png"),
                        List.copyOf(tags)));
            });
            catalog = new ChampionCatalog(latest, champions);
            log.info("Data Dragon patch {}: {} champions", latest, champions.size());
        } catch (Exception e) {
            log.error("Data Dragon refresh failed: {}", e.getMessage());
        }
    }

    private Map<String, String> loadLocalizedNames(String version) {
        Map<String, String> out = new HashMap<>();
        if (localizedLocale == null || localizedLocale.isBlank()) {
            return out;
        }
        try {
            JsonNode data = objectMapper.readTree(restTemplate.getForObject(
                    CDN + "/cdn/" + version + "/data/" + localizedLocale + "/champion.json", String.class)).get("data");
            data.properties().forEach(e -> out.put(e.getValue().get("id").asText(), e.getValue().get("name").asText()));
        } catch (Exception e) {
            log.warn("Data Dragon {} names unavailable: {}", localizedLocale, e.getMessage());
        }
        return out;
    }

    public ChampionCatalog catalog() {
        ChampionCatalog current = catalog;
        if (current.isEmpty() && System.currentTimeMillis() - lastAttemptMs > RETRY_EMPTY_MS) {
            refresh();
            current = catalog;
        }
        return current;
    }

    public String getChampionName(long id) {
        return catalog().byId(id).map(ChampionInfo::name).orElse("Unknown (" + id + ")");
    }

    public Optional<ChampionInfo> findChampion(String input) {
        return catalog().find(input);
    }

    public String currentPatch() {
        return catalog().patch();
    }
}
