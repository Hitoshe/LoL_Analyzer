package com.lol.analyzer.service;

import com.lol.analyzer.client.RiotClient;
import com.lol.analyzer.model.AccountDTO;
import com.lol.analyzer.model.LeagueDTO;
import com.lol.analyzer.model.Summoner;
import com.lol.analyzer.model.SummonerDTO;
import com.lol.analyzer.model.MasteryDTO;
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

        System.out.println("Ищем в базе: [" + cleanName + "] # [" + cleanTag + "]");

        // 1. Сначала проверяем базу (Кэш)
        Optional<Summoner> cachedSummoner = summonerRepository.findByGameNameIgnoreCaseAndTagLineIgnoreCase(cleanName, cleanTag);

        if (cachedSummoner.isPresent()) {
            System.out.println("Достали из базы: " + cleanName);
            return cachedSummoner.get();
        }

        // 2. Шаг 1: Получаем PUUID (через Account-V1)
        AccountDTO accountDto = riotClient.getAccountData(cleanName, cleanTag);
        String puuid = accountDto.getPuuid();

        // 3. Шаг 2: Получаем уровень аккаунта (через Summoner-V4)
        // Мы НЕ прерываем работу, если тут что-то не так, просто берем уровень
        SummonerDTO summonerDto = riotClient.getSummonerByPuuid(puuid);

        // 4. Шаг 3: Получаем Ранг напрямую через PUUID (Новый метод!)
        LeagueDTO[] leagues = riotClient.getLeagueEntriesByPuuid(puuid);

        // 5. Собираем нашу сущность Summoner для сохранения в базу
        Summoner summoner = new Summoner(accountDto);

        // Устанавливаем уровень, если данные пришли
        if (summonerDto != null) {
            summoner.setSummonerLevel(summonerDto.getSummonerLevel());
        }

        // Обрабатываем лиги (ищем SoloQ)
        if (leagues != null) {
            for (LeagueDTO league : leagues) {
                if ("RANKED_SOLO_5x5".equals(league.getQueueType())) {
                    summoner.setTier(league.getTier());
                    summoner.setRank(league.getRank());
                    summoner.setLeaguePoints(league.getLeaguePoints());
                }
            }
        }

        // Получаем мастерство чемпионов
        MasteryDTO[] masteries = riotClient.getTopMasteries(puuid);

        // Заполняем данные о топ-1 чемпионе, если они есть
        if (masteries != null && masteries.length > 0) {
            long champId = masteries[0].getChampionId();
            summoner.setTopChampionId(champId);
            summoner.setTopChampionPoints(masteries[0].getChampionPoints());

            // ПРЕВРАЩАЕМ ID В ИМЯ
            String champName = dataDragonService.getChampionName(champId);
            summoner.setTopChampionName(champName);
        }

        System.out.println("Данные получены из Riot API и сохранены в базу для: " + cleanName);
        return summonerRepository.save(summoner);
    }
}