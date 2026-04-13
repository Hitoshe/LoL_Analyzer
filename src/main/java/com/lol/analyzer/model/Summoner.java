package com.lol.analyzer.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity // Создание таблицы на основе класса
@Table(name = "summoners") // Имя таблицы в базе
@Data
public class Summoner {

    @Id // PUUID - уникальный ключ
    private String puuid;

    private String gameName;
    private String tagLine;

    private String tier;
    private String rank;
    private Integer leaguePoints;
    private Long summonerLevel;

    private Long topChampionId;
    private Integer topChampionPoints;
    private String topChampionName;

    private double avgKda;
    private double avgGpm; // Gold Per Minute

    // Пустой конструктор (для JPA)
    public Summoner() {}

    // Удобный конструктор, чтобы быстро превращать DTO в Entity
    public Summoner(AccountDTO dto) {
        this.puuid = dto.getPuuid();
        this.gameName = dto.getGameName().trim();
        this.tagLine = dto.getTagLine().trim();
    }
}