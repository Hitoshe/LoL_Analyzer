package com.lol.analyzer.client;

import com.lol.analyzer.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Thin HTTP client for Riot Games REST APIs used by this app.
 * <p>Routing follows Riot rules: Account and Match v5 use the {@code europe} routing host;
 * Summoner, League, and Champion Mastery use the {@code euw1} platform host (see URLs below).
 * <p>Each call applies a short delay to stay under developer rate limits (see {@link #sleep()}).
 */
@Component
public class RiotClient {

    @Value("${riot.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate;

    public RiotClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public AccountDTO getAccountData(String name, String tag) {
        sleep();
        String url = "https://europe.api.riotgames.com/riot/account/v1/accounts/by-riot-id/"
                + name + "/" + tag + "?api_key=" + apiKey;
        return restTemplate.getForObject(url, AccountDTO.class);
    }

    public SummonerDTO getSummonerByPuuid(String puuid) {
        sleep();
        String cleanPuuid = puuid.trim();
        String url = "https://euw1.api.riotgames.com/lol/summoner/v4/summoners/by-puuid/"
                + cleanPuuid + "?api_key=" + apiKey;
        return restTemplate.getForObject(url, SummonerDTO.class);
    }

    public LeagueDTO[] getLeagueEntriesByPuuid(String puuid) {
        sleep();
        String url = "https://euw1.api.riotgames.com/lol/league/v4/entries/by-puuid/"
                + puuid + "?api_key=" + apiKey;
        return restTemplate.getForObject(url, LeagueDTO[].class);
    }

    public MasteryDTO[] getTopMasteries(String puuid) {
        sleep();
        String url = "https://euw1.api.riotgames.com/lol/champion-mastery/v4/champion-masteries/by-puuid/"
                + puuid + "/top?count=3&api_key=" + apiKey;
        return restTemplate.getForObject(url, MasteryDTO[].class);
    }

    public String[] getMatchIds(String puuid) {
        sleep();
        String url = "https://europe.api.riotgames.com/lol/match/v5/matches/by-puuid/"
                + puuid + "/ids?start=0&count=5&type=ranked&api_key=" + apiKey;
        return restTemplate.getForObject(url, String[].class);
    }

    public MatchDTO getMatchDetails(String matchId) {
        sleep();
        String url = "https://europe.api.riotgames.com/lol/match/v5/matches/"
                + matchId + "?api_key=" + apiKey;
        return restTemplate.getForObject(url, MatchDTO.class);
    }

    /**
     * Throttle outbound calls (~10 req/s per thread) to reduce risk of hitting Riot's rate limits.
     */
    private void sleep() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
