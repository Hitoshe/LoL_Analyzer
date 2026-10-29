package com.lol.analyzer.client;

import com.lol.analyzer.model.*;
import com.lol.analyzer.riot.Region;
import com.lol.analyzer.riot.RiotApiException;
import com.lol.analyzer.riot.RiotRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP client for the Riot Games APIs used by this app, for every {@link Region}.
 * <p>Each routing host gets its own {@link RiotRateLimiter} because Riot counts application limits per host.
 * 404 is returned as {@code null}; other failures become {@link RiotApiException} with a user-facing message.
 */
@Component
public class RiotClient {

    private static final Logger log = LoggerFactory.getLogger(RiotClient.class);
    private static final int MAX_ATTEMPTS = 3;

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String rateLimitSpec;
    private final long maxRetryWaitSeconds;
    private final Map<String, RiotRateLimiter> limiters = new ConcurrentHashMap<>();

    public RiotClient(
            RestTemplate restTemplate,
            @Value("${riot.api.key:}") String apiKey,
            @Value("${riot.rate-limit:20:1,100:120}") String rateLimitSpec,
            @Value("${riot.max-retry-wait-seconds:20}") long maxRetryWaitSeconds) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.rateLimitSpec = rateLimitSpec;
        this.maxRetryWaitSeconds = maxRetryWaitSeconds;
    }

    public boolean hasApiKey() {
        return !apiKey.isEmpty();
    }

    public AccountDTO getAccountByRiotId(Region region, String gameName, String tagLine) {
        return get(region.getAccountRouting(), "/riot/account/v1/accounts/by-riot-id/{name}/{tag}",
                AccountDTO.class, gameName, tagLine);
    }

    public AccountDTO getAccountByPuuid(Region region, String puuid) {
        return get(region.getAccountRouting(), "/riot/account/v1/accounts/by-puuid/{puuid}", AccountDTO.class, puuid);
    }

    public SummonerDTO getSummonerByPuuid(Region region, String puuid) {
        return get(region.getPlatformHost(), "/lol/summoner/v4/summoners/by-puuid/{puuid}", SummonerDTO.class, puuid);
    }

    public LeagueDTO[] getLeagueEntriesByPuuid(Region region, String puuid) {
        return get(region.getPlatformHost(), "/lol/league/v4/entries/by-puuid/{puuid}", LeagueDTO[].class, puuid);
    }

    /** Full mastery list (one call), including {@code lastPlayTime} for every champion. */
    public MasteryDTO[] getMasteries(Region region, String puuid) {
        return get(region.getPlatformHost(), "/lol/champion-mastery/v4/champion-masteries/by-puuid/{puuid}",
                MasteryDTO[].class, puuid);
    }

    /** Ranked (solo + flex) match ids, newest first. */
    public String[] getRankedMatchIds(Region region, String puuid, int count) {
        return get(region.getMatchRouting(), "/lol/match/v5/matches/by-puuid/{puuid}/ids?type=ranked&start=0&count={count}",
                String[].class, puuid, count);
    }

    /** Match ids, newest first; {@code queueId} and {@code startTimeEpochSec} are optional filters. */
    public String[] getMatchIds(Region region, String puuid, Integer queueId, Long startTimeEpochSec, int count) {
        StringBuilder path = new StringBuilder("/lol/match/v5/matches/by-puuid/{puuid}/ids?start=0&count={count}");
        List<Object> vars = new ArrayList<>(List.of(puuid, count));
        if (queueId != null) {
            path.append("&queue={queue}");
            vars.add(queueId);
        }
        if (startTimeEpochSec != null) {
            path.append("&startTime={start}");
            vars.add(startTimeEpochSec);
        }
        return get(region.getMatchRouting(), path.toString(), String[].class, vars.toArray());
    }

    /** Routing comes from the match id prefix, so matches from any server can be loaded. */
    public MatchDTO getMatch(String matchId) {
        Region region = Region.fromMatchId(matchId).orElse(Region.EUW);
        return get(region.getMatchRouting(), "/lol/match/v5/matches/{id}", MatchDTO.class, matchId);
    }

    public CurrentGameDTO getActiveGame(Region region, String puuid) {
        return get(region.getPlatformHost(), "/lol/spectator/v5/active-games/by-summoner/{puuid}", CurrentGameDTO.class, puuid);
    }

    /** {@code tierPath} is one of {@code challengerleagues}, {@code grandmasterleagues}, {@code masterleagues}. */
    public LeagueListDTO getApexLeague(Region region, String tierPath) {
        return get(region.getPlatformHost(), "/lol/league/v4/" + tierPath + "/by-queue/RANKED_SOLO_5x5", LeagueListDTO.class);
    }

    private <T> T get(String host, String pathTemplate, Class<T> type, Object... vars) {
        if (apiKey.isEmpty()) {
            throw new RiotApiException(401, "Не задан RIOT_API_KEY. Добавьте ключ в файл .env и перезапустите приложение.");
        }
        URI uri = UriComponentsBuilder.fromUriString("https://" + host + ".api.riotgames.com" + pathTemplate)
                .encode()
                .buildAndExpand(vars)
                .toUri();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Riot-Token", apiKey);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        RiotRateLimiter limiter = limiters.computeIfAbsent(host, h -> RiotRateLimiter.fromSpec(rateLimitSpec));

        for (int attempt = 1; ; attempt++) {
            try {
                limiter.acquire();
                return restTemplate.exchange(uri, HttpMethod.GET, entity, type).getBody();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RiotApiException(503, "Запрос к Riot API прерван.");
            } catch (HttpClientErrorException.NotFound e) {
                return null;
            } catch (HttpClientErrorException.TooManyRequests e) {
                long retryAfter = parseRetryAfter(e.getResponseHeaders());
                if (attempt >= MAX_ATTEMPTS || retryAfter > maxRetryWaitSeconds) {
                    throw new RiotApiException(429, "Лимит запросов Riot API исчерпан. Повторите через " + retryAfter + " с.");
                }
                log.warn("Riot 429 on {}, retry in {}s", host, retryAfter);
                sleepSeconds(retryAfter);
            } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
                throw new RiotApiException(e.getStatusCode().value(),
                        "Riot API отклонил ключ. Ключ разработчика живёт 24 часа — получите новый на developer.riotgames.com и обновите RIOT_API_KEY.");
            } catch (HttpClientErrorException e) {
                throw new RiotApiException(e.getStatusCode().value(), "Riot API вернул ошибку " + e.getStatusCode().value() + ".");
            } catch (HttpServerErrorException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw new RiotApiException(503, "Riot API временно недоступен (" + e.getStatusCode().value() + "). Попробуйте позже.");
                }
                sleepSeconds(attempt);
            } catch (ResourceAccessException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw new RiotApiException(503, "Нет связи с Riot API: " + e.getMessage());
                }
                sleepSeconds(attempt);
            }
        }
    }

    private static long parseRetryAfter(HttpHeaders headers) {
        String value = headers == null ? null : headers.getFirst("Retry-After");
        if (value == null) {
            return 5;
        }
        try {
            return Math.max(1, Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            return 5;
        }
    }

    private static void sleepSeconds(long seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RiotApiException(503, "Запрос к Riot API прерван.");
        }
    }
}
