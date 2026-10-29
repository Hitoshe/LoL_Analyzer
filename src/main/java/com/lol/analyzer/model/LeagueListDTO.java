package com.lol.analyzer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * League-V4 apex ladder (Challenger / Grandmaster / Master) used by the meta collector to pick sample players.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LeagueListDTO {
    private String tier;
    private List<Entry> entries;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Entry {
        private String puuid;
        private int leaguePoints;
    }
}
