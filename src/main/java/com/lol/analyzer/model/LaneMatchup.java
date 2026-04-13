package com.lol.analyzer.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LaneMatchup {
    private String lane;
    private Summoner playerA;
    private Summoner playerB;
    private String prediction; // Кто фаворит на линии
    private String difficulty; // EASY, MEDIUM, HARD
}