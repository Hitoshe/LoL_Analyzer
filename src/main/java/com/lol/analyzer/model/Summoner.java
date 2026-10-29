package com.lol.analyzer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * JPA entity mapped to {@code summoners}: cached Riot identity, server, solo-queue snapshot, mastery top pick,
 * aggregated recent ranked stats, and {@code lastUpdated} for the profile cache policy.
 */
@Entity
@Table(name = "summoners")
@Data
public class Summoner {

    @Id
    private String puuid;

    private String gameName;
    private String tagLine;

    @Column(length = 8)
    private String platform;

    private String tier;
    private String rank;
    private Integer leaguePoints;
    private Integer rankedWins;
    private Integer rankedLosses;
    private Long summonerLevel;

    private Long topChampionId;
    private Integer topChampionPoints;
    private String topChampionName;

    private java.time.LocalDateTime lastUpdated;

    private Double avgKda;
    private Double avgGpm;

    public Summoner() {
    }

    public Summoner(AccountDTO dto) {
        this.puuid = dto.getPuuid();
        this.gameName = dto.getGameName().trim();
        this.tagLine = dto.getTagLine().trim();
    }

    public String riotIdDisplay() {
        return gameName + "#" + tagLine;
    }
}
