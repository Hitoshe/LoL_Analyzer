package com.lol.analyzer.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

/**
 * One lane row in the UI: both {@link Summoner} references, chosen champions, heuristic prediction, and difficulty band.
 */
@Data
@AllArgsConstructor
@ToString
public class LaneMatchup {
    private String lane;
    private Summoner playerA;
    private Summoner playerB;

    private String championA;
    private String championB;

    private String prediction;
    private String difficulty;
}
