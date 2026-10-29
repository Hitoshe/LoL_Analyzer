package com.lol.analyzer.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * One stored Match-V5 row (global match id) with owned {@link MatchParticipant} rows.
 * Rows saved before queue/time/patch were tracked have {@code queueId == null} and are re-downloaded when seen again.
 */
@Entity
@Table(name = "lol_matches", indexes = @Index(name = "idx_match_queue_start", columnList = "queue_id, game_start_timestamp"))
@Data
@NoArgsConstructor
public class GameMatch {

    @Id
    @Column(name = "match_id", length = 32)
    private String matchId;

    @Column(name = "game_duration_seconds", nullable = false)
    private int gameDurationSeconds;

    @Column(length = 8)
    private String platformId;

    @Column(name = "queue_id")
    private Integer queueId;

    /** Epoch milliseconds. */
    @Column(name = "game_start_timestamp")
    private Long gameStartTimestamp;

    @Column(length = 32)
    private String gameVersion;

    /** {@code major.minor} of {@link #gameVersion}, e.g. {@code 15.21}. */
    @Column(length = 8)
    private String patch;

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MatchParticipant> participants = new ArrayList<>();
}
