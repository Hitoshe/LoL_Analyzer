package com.lol.analyzer.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * One stored Match-V5 row (global match id) with owned {@link MatchParticipant} rows.
 */
@Entity
@Table(name = "lol_matches")
@Data
@NoArgsConstructor
public class GameMatch {

    @Id
    @Column(name = "match_id", length = 32)
    private String matchId;

    @Column(name = "game_duration_seconds", nullable = false)
    private int gameDurationSeconds;

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MatchParticipant> participants = new ArrayList<>();
}
