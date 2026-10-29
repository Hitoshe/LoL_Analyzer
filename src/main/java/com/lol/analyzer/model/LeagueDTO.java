package com.lol.analyzer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * League-V4 ranked entry fragment used to read solo-queue tier/LP when {@code queueType} is {@code RANKED_SOLO_5x5}.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LeagueDTO {
    private String queueType;
    private String tier;
    private String rank;
    private int leaguePoints;
    private int wins;
    private int losses;
}
