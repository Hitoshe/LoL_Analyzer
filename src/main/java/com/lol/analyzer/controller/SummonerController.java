package com.lol.analyzer.controller;

import com.lol.analyzer.model.AccountDTO;
import com.lol.analyzer.model.Summoner;
import com.lol.analyzer.service.SummonerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/summoner")
public class SummonerController {

    private final SummonerService summonerService;

    public SummonerController(SummonerService summonerService) {
        this.summonerService = summonerService;
    }

    // Path = /api/summoner/Faker/T1
    @GetMapping("/{name}/{tag}")
    public Summoner getSummonerInfo(@PathVariable String name, @PathVariable String tag) {
        return summonerService.getAccount(name, tag);
    }
}