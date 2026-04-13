package com.lol.analyzer.client;

import com.lol.analyzer.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class RiotClient {

    @Value("${riot.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate;

    public RiotClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public AccountDTO getAccountData(String name, String tag) {
        String url = "https://europe.api.riotgames.com/riot/account/v1/accounts/by-riot-id/"
                + name + "/" + tag + "?api_key=" + apiKey;
        return restTemplate.getForObject(url, AccountDTO.class);
    }

    // Получаем ID по PUUID (через платформу, например ru или euw1)
    public SummonerDTO getSummonerByPuuid(String puuid) {
        String cleanPuuid = puuid.trim();
        String url = "https://euw1.api.riotgames.com/lol/summoner/v4/summoners/by-puuid/"
                + cleanPuuid + "?api_key=" + apiKey;

        System.out.println("Запрос к Summoner-V4: " + url);

        return restTemplate.getForObject(url, SummonerDTO.class);
    }

    // Получаем ранг напрямую по PUUID
    public LeagueDTO[] getLeagueEntriesByPuuid(String puuid) {
        String url = "https://euw1.api.riotgames.com/lol/league/v4/entries/by-puuid/"
                + puuid + "?api_key=" + apiKey;

        System.out.println("Запрос к League-V4 (by-puuid): " + url);
        return restTemplate.getForObject(url, LeagueDTO[].class);
    }

    public MasteryDTO[] getTopMasteries(String puuid) {
        String url = "https://euw1.api.riotgames.com/lol/champion-mastery/v4/champion-masteries/by-puuid/"
                + puuid + "/top?count=3&api_key=" + apiKey;

        return restTemplate.getForObject(url, MasteryDTO[].class);
    }

    // 1. Получаем список ID матчей
    public String[] getMatchIds(String puuid) {
        // &type=ranked
        // Это автоматически отфильтрует SoloQ и Flex, убрав Арамы и Обычки
        String url = "https://europe.api.riotgames.com/lol/match/v5/matches/by-puuid/"
                + puuid + "/ids?start=0&count=5&type=ranked&api_key=" + apiKey;

        System.out.println("Запрашиваем только ранговые матчи для: " + puuid);
        return restTemplate.getForObject(url, String[].class);
    }

    // 2. Получаем данные конкретного матча
    public MatchDTO getMatchDetails(String matchId) {
        String url = "https://europe.api.riotgames.com/lol/match/v5/matches/"
                + matchId + "?api_key=" + apiKey;
        return restTemplate.getForObject(url, MatchDTO.class);
    }
}