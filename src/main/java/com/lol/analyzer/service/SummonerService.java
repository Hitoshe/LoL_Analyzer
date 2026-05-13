package com.lol.analyzer.service;

import com.lol.analyzer.client.RiotClient;
import com.lol.analyzer.model.*;
import com.lol.analyzer.repository.SummonerRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Loads and caches {@link Summoner} profiles: reads from PostgreSQL when fresh,
 * otherwise refreshes from Riot, recomputes recent ranked aggregates, and persists related match rows.
 */
@Service
public class SummonerService {

    private final RiotClient riotClient;
    private final SummonerRepository summonerRepository;
    private final DataDragonService dataDragonService;
    private final ProfileMatchPersistenceService profileMatchPersistenceService;

    public SummonerService(
            RiotClient riotClient,
            SummonerRepository summonerRepository,
            DataDragonService dataDragonService,
            ProfileMatchPersistenceService profileMatchPersistenceService) {
        this.riotClient = riotClient;
        this.summonerRepository = summonerRepository;
        this.dataDragonService = dataDragonService;
        this.profileMatchPersistenceService = profileMatchPersistenceService;
    }

    public Summoner getAccount(String name, String tag) {
        String cleanName = name.trim();
        String cleanTag = tag.trim();

        Optional<Summoner> cached = summonerRepository.findByGameNameIgnoreCaseAndTagLineIgnoreCase(cleanName, cleanTag);

        if (cached.isPresent()) {
            Summoner s = cached.get();
            if (s.getLastUpdated() != null && s.getLastUpdated().isAfter(LocalDateTime.now().minusHours(24))) {
                System.out.println("CACHE HIT: summoner " + cleanName + " is still fresh.");
                return s;
            }
            System.out.println("CACHE STALE: refreshing summoner " + cleanName + " from Riot.");
        }

        AccountDTO accountDto = riotClient.getAccountData(cleanName, cleanTag);
        String puuid = accountDto.getPuuid();

        SummonerDTO summonerDto = riotClient.getSummonerByPuuid(puuid);
        LeagueDTO[] leagues = riotClient.getLeagueEntriesByPuuid(puuid);

        Summoner summoner = new Summoner(accountDto);
        if (summonerDto != null) {
            summoner.setSummonerLevel(summonerDto.getSummonerLevel());
        }

        if (leagues != null) {
            for (LeagueDTO league : leagues) {
                if ("RANKED_SOLO_5x5".equals(league.getQueueType())) {
                    summoner.setTier(league.getTier());
                    summoner.setRank(league.getRank());
                    summoner.setLeaguePoints(league.getLeaguePoints());
                }
            }
        }

        MasteryDTO[] masteries = riotClient.getTopMasteries(puuid);
        if (masteries != null && masteries.length > 0) {
            long champId = masteries[0].getChampionId();
            summoner.setTopChampionId(champId);
            summoner.setTopChampionPoints(masteries[0].getChampionPoints());
            summoner.setTopChampionName(dataDragonService.getChampionName(champId));
        }

        List<MatchDTO> recentMatches = fetchRankedMatchDetails(puuid);
        applyRecentStats(summoner, puuid, recentMatches);

        summoner.setLastUpdated(LocalDateTime.now());

        System.out.println("PROFILE UPDATED: " + cleanName);
        Summoner saved = summonerRepository.save(summoner);
        profileMatchPersistenceService.persistNewMatchesFromDtos(recentMatches);
        return saved;
    }

    /**
     * Fetches the last ranked match IDs for the player, then loads each match payload once.
     */
    private List<MatchDTO> fetchRankedMatchDetails(String puuid) {
        String[] matchIds = riotClient.getMatchIds(puuid);
        if (matchIds == null || matchIds.length == 0) {
            return List.of();
        }
        List<MatchDTO> out = new ArrayList<>();
        for (String mId : matchIds) {
            MatchDTO match = riotClient.getMatchDetails(mId);
            if (match != null && match.getInfo() != null) {
                out.add(match);
            }
        }
        return out;
    }

    /**
     * Computes average KDA and GPM across the loaded matches for the given {@code puuid} only.
     */
    private void applyRecentStats(Summoner summoner, String puuid, List<MatchDTO> matches) {
        double totalKills = 0, totalDeaths = 0, totalAssists = 0, totalGold = 0, totalSeconds = 0;

        for (MatchDTO match : matches) {
            totalSeconds += match.getInfo().getGameDuration();
            for (MatchDTO.ParticipantDTO p : match.getInfo().getParticipants()) {
                if (puuid.equals(p.getPuuid())) {
                    totalKills += p.getKills();
                    totalDeaths += p.getDeaths();
                    totalAssists += p.getAssists();
                    totalGold += p.getGoldEarned();
                    break;
                }
            }
        }

        double kda = (totalKills + totalAssists) / Math.max(1, totalDeaths);
        double gpm = totalGold / (Math.max(1, totalSeconds) / 60.0);

        summoner.setAvgKda(Math.round(kda * 100.0) / 100.0);
        summoner.setAvgGpm(Math.round(gpm * 100.0) / 100.0);
    }
}
