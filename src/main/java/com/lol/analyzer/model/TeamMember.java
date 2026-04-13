package com.lol.analyzer.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TeamMember {
    private Summoner summoner;
    private String selectedChampion;
    private String lane;
    private double winScore; // Личный балл игрока для этого матча
}