package com.lol.analyzer.model;

import lombok.Data;

/**
 * League-V4 ranked entry fragment used to read solo-queue tier/LP when {@code queueType} is {@code RANKED_SOLO_5x5}.
 */
@Data
public class LeagueDTO {
    private String queueType;
    private String tier;
    private String rank;
    private int leaguePoints;
}
