package com.lol.analyzer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SummonerDTO {
    @JsonProperty("id")
    private String id;

    @JsonProperty("summonerLevel")
    private long summonerLevel;
}