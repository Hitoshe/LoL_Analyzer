package com.lol.analyzer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One champion mastery row per player, replaced on every profile refresh.
 */
@Entity
@Table(name = "summoner_masteries", indexes = @Index(name = "idx_mastery_puuid_champ", columnList = "puuid, champion_id"))
@Data
@NoArgsConstructor
public class SummonerMastery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 78)
    private String puuid;

    @Column(name = "champion_id", nullable = false)
    private long championId;

    private int championLevel;

    private int championPoints;

    /** Epoch milliseconds. */
    private long lastPlayTime;
}
