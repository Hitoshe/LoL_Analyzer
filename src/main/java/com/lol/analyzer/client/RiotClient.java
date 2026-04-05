package com.lol.analyzer.client;

import com.lol.analyzer.model.AccountDTO;
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
}