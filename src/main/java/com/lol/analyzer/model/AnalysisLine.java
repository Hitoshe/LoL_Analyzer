package com.lol.analyzer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Snapshot of both sides for a single lane within an {@link AnalysisHistory} record.
 */
@Entity
@Table(name = "analysis_lines")
@Data
@NoArgsConstructor
public class AnalysisLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "history_id", nullable = false)
    private AnalysisHistory history;

    @Column(nullable = false, length = 16)
    private String lane;

    @Column(length = 64)
    private String prediction;

    @Column(length = 16)
    private String difficulty;

    @Column(name = "a_game_name", length = 64)
    private String aGameName;

    @Column(name = "a_tag_line", length = 32)
    private String aTagLine;

    @Column(name = "a_champion", length = 128)
    private String aChampion;

    @Column(name = "a_tier", length = 32)
    private String aTier;

    @Column(name = "a_summoner_level")
    private Long aSummonerLevel;

    @Column(name = "a_avg_kda")
    private Double aAvgKda;

    @Column(name = "a_avg_gpm")
    private Double aAvgGpm;

    @Column(name = "a_win_score", nullable = false)
    private double aWinScore;

    @Column(name = "b_game_name", length = 64)
    private String bGameName;

    @Column(name = "b_tag_line", length = 32)
    private String bTagLine;

    @Column(name = "b_champion", length = 128)
    private String bChampion;

    @Column(name = "b_tier", length = 32)
    private String bTier;

    @Column(name = "b_summoner_level")
    private Long bSummonerLevel;

    @Column(name = "b_avg_kda")
    private Double bAvgKda;

    @Column(name = "b_avg_gpm")
    private Double bAvgGpm;

    @Column(name = "b_win_score", nullable = false)
    private double bWinScore;
}
