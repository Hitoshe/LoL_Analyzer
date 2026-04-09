package com.lol.analyzer.model;
import lombok.Data;

@Data
public class LeagueDTO {
    private String queueType; // RANKED_SOLO_5x5 или RANKED_FLEX_SR
    private String tier;      // GOLD, DIAMOND, CHALLENGER
    private String rank;      // I, II, III, IV
    private int leaguePoints; // LP
}