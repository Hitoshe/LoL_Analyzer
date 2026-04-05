package com.lol.analyzer.service;

import com.lol.analyzer.client.RiotClient;
import com.lol.analyzer.model.AccountDTO;
import org.springframework.stereotype.Service;

@Service
public class SummonerService {

    private final RiotClient riotClient;

    public SummonerService(RiotClient riotClient) {
        this.riotClient = riotClient;
    }

    public AccountDTO getAccount(String name, String tag) {
        return riotClient.getAccountData(name, tag);
    }
}