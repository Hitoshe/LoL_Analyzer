package com.lol.analyzer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Champion mastery row returned by Riot's Champion-Mastery-V4 endpoints.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MasteryDTO {
    private long championId;
    private int championLevel;
    private int championPoints;
    private long lastPlayTime;
}
