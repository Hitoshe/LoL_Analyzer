package com.lol.analyzer.service;

import com.lol.analyzer.client.RiotClient;
import com.lol.analyzer.model.*;
import com.lol.analyzer.repository.SummonerRepository;
import org.springframework.stereotype.Service;
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

        // [ШАГ 1] Проверка локальной базы (Кэш)
        Optional<Summoner> cached = summonerRepository.findByGameNameIgnoreCaseAndTagLineIgnoreCase(cleanName, cleanTag);
        if (cached.isPresent()) {
            return cached.get();
        }

        // [ШАГ 2] Получаем PUUID (Глобальный паспорт игрока)
        AccountDTO accountDto = riotClient.getAccountData(cleanName, cleanTag);
        String puuid = accountDto.getPuuid();

        // [ШАГ 3] Получаем уровень и статы лиги (Rank/Tier)
        SummonerDTO summonerDto = riotClient.getSummonerByPuuid(puuid);
        LeagueDTO[] leagues = riotClient.getLeagueEntriesByPuuid(puuid);

        // Создаем объект для базы
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

        // [ШАГ 4] Мастерство чемпионов (На ком игрок "тащит")
        MasteryDTO[] masteries = riotClient.getTopMasteries(puuid);
        if (masteries != null && masteries.length > 0) {
            long champId = masteries[0].getChampionId();
            summoner.setTopChampionId(champId);
            summoner.setTopChampionPoints(masteries[0].getChampionPoints());
            summoner.setTopChampionName(dataDragonService.getChampionName(champId));
        }

        // [ШАГ 5] Анализ последних матчей (Расчет KDA и GPM)
        calculateRecentStats(summoner, puuid);

        System.out.println("Анализ завершен для: " + cleanName);
        return summonerRepository.save(summoner);
    }

    /**
     * Вспомогательный метод для расчета KDA и Золота за последние матчи
     */
    private void calculateRecentStats(Summoner summoner, String puuid) {
        // Берем последние 5 матчей (для экономии лимитов API)
        String[] matchIds = riotClient.getMatchIds(puuid);

        double totalKills = 0, totalDeaths = 0, totalAssists = 0;
        double totalGold = 0, totalSeconds = 0;

        if (matchIds != null) {
            for (String mId : matchIds) {
                MatchDTO match = riotClient.getMatchDetails(mId);
                if (match != null) {
                    totalSeconds += match.getInfo().getGameDuration();

                    // Ищем нашего игрока среди 10 участников матча
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

        // Формула KDA: (Убийства + Помощь) / Смерти (минимум 1 смерть, чтобы не делить на 0)
        double kda = (totalKills + totalAssists) / Math.max(1, totalDeaths);
        // Золото в минуту
        double gpm = totalGold / (Math.max(1, totalSeconds) / 60.0);

        summoner.setAvgKda(Math.round(kda * 100.0) / 100.0);
        summoner.setAvgGpm(Math.round(gpm * 100.0) / 100.0);
    }
}