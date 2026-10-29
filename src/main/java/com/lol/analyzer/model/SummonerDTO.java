package com.lol.analyzer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Summoner-V4 response fragment: legacy numeric {@code id} (unused here) and {@code summonerLevel}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SummonerDTO {
    @JsonProperty("id")
    private String id;

    @JsonProperty("summonerLevel")
    private long summonerLevel;
}
