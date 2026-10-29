package com.lol.analyzer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One participant line inside a {@link GameMatch}; includes denormalized per-match KDA/GPM, lane and result for analytics.
 * {@code win} is {@code null} for remakes and for rows stored before results were tracked.
 */
@Entity
@Table(name = "lol_match_participants", indexes = @Index(name = "idx_participant_puuid", columnList = "puuid"))
@Data
@NoArgsConstructor
public class MatchParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private GameMatch match;

    @Column(nullable = false, length = 78)
    private String puuid;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "champion_id", nullable = false)
    private Champion champion;

    @Column(nullable = false)
    private int teamId;

    @Enumerated(EnumType.STRING)
    @Column(length = 8)
    private Lane teamPosition;

    private Boolean win;

    @Column(nullable = false)
    private int kills;

    @Column(nullable = false)
    private int deaths;

    @Column(nullable = false)
    private int assists;

    @Column(name = "gold_earned", nullable = false)
    private int goldEarned;

    @Column(nullable = false)
    private double kda;

    @Column(nullable = false)
    private double gpm;
}
