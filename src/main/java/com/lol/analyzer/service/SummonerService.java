package com.lol.analyzer.service;

import com.lol.analyzer.client.RiotClient;
import com.lol.analyzer.model.AccountDTO;
import com.lol.analyzer.model.Summoner;
import com.lol.analyzer.repository.SummonerRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SummonerService {

    private final RiotClient riotClient;
    private final SummonerRepository summonerRepository;

    public SummonerService(RiotClient riotClient, SummonerRepository summonerRepository) {
        this.riotClient = riotClient;
        this.summonerRepository = summonerRepository;
    }

    public Summoner getAccount(String name, String tag) {
        String cleanName = name.trim();
        String cleanTag = tag.trim();

        System.out.println("Ищем в базе: [" + cleanName + "] # [" + cleanTag + "]");

        // 1. Ищем в базе
        Optional<Summoner> cachedSummoner = summonerRepository.findByGameNameIgnoreCaseAndTagLineIgnoreCase(name, tag);

        if (cachedSummoner.isPresent()) {
            System.out.println("Достали из базы: " + name);
            return cachedSummoner.get();
        }

        // 2. Если в базе нет, идем в Riot
        System.out.println("В базе нет, идем в Riot API: " + name);
        AccountDTO dto = riotClient.getAccountData(name, tag);

        // 3. Сохраняем результат в базу на будущее
        Summoner newSummoner = new Summoner(dto);
        return summonerRepository.save(newSummoner);
    }
}