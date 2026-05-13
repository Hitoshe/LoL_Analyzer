package com.lol.analyzer.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * One seat in a submitted roster: resolved {@link Summoner}, form champion pick, lane label, and computed WinScore.
 */
@Data
@AllArgsConstructor
public class TeamMember {
    private Summoner summoner;
    private String selectedChampion;
    private String lane;
    private double winScore;
}
