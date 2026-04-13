package com.lol.analyzer.service;

import com.lol.analyzer.client.RiotClient;
import com.lol.analyzer.model.*;
import com.lol.analyzer.repository.SummonerRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class SummonerService {

    private final RiotClient riotClient;
    private final SummonerRepository summonerRepository;
    private final DataDragonService dataDragonService;

    public SummonerService(RiotClient riotClient, SummonerRepository summonerRepository, DataDragonService dataDragonService) {
        this.riotClient = riotClient;
        this.summonerRepository = summonerRepository;
        this.dataDragonService = dataDragonService;
    }

    public Summoner getAccount(String name, String tag) {
        String cleanName = name.trim();
        String cleanTag = tag.trim();

        // 1. Проверяем кэш в базе
        Optional<Summoner> cached = summonerRepository.findByGameNameIgnoreCaseAndTagLineIgnoreCase(cleanName, cleanTag);

        if (cached.isPresent()) {
            Summoner s = cached.get();
            // Если данные свежие (обновлялись менее 24 часов назад) - отдаем сразу
            if (s.getLastUpdated() != null && s.getLastUpdated().isAfter(LocalDateTime.now().minusHours(24))) {
                System.out.println("ИСПОЛЬЗУЕМ КЭШ: Игрок " + cleanName + " обновлялся недавно.");
                return s;
            }
            System.out.println("ОБНОВЛЕНИЕ: Данные игрока " + cleanName + " устарели, идем в API.");
        }

        // 2. Если данных нет или они старые - делаем цепочку запросов
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

        calculateRecentStats(summoner, puuid);

        // 3. Ставим метку времени обновления перед сохранением
        summoner.setLastUpdated(LocalDateTime.now());

        System.out.println("ДАННЫЕ ОБНОВЛЕНЫ: " + cleanName);
        return summonerRepository.save(summoner);
    }

    private void calculateRecentStats(Summoner summoner, String puuid) {
        String[] matchIds = riotClient.getMatchIds(puuid);
        double totalKills = 0, totalDeaths = 0, totalAssists = 0, totalGold = 0, totalSeconds = 0;

        if (matchIds != null) {
            for (String mId : matchIds) {
                MatchDTO match = riotClient.getMatchDetails(mId);
                if (match != null) {
                    totalSeconds += match.getInfo().getGameDuration();
                    for (MatchDTO.ParticipantDTO p : match.getInfo().getParticipants()) {
                        if (p.getPuuid().equals(puuid)) {
                            totalKills += p.getKills();
                            totalDeaths += p.getDeaths();
                            totalAssists += p.getAssists();
                            totalGold += p.getGoldEarned();
                            break;
                        }
                    }
                }
            }
        }

        double kda = (totalKills + totalAssists) / Math.max(1, totalDeaths);
        double gpm = totalGold / (Math.max(1, totalSeconds) / 60.0);

        summoner.setAvgKda(Math.round(kda * 100.0) / 100.0);
        summoner.setAvgGpm(Math.round(gpm * 100.0) / 100.0);
    }
}