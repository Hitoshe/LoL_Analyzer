package com.lol.analyzer.model;

import lombok.Data;

/**
 * Champion mastery row returned by Riot's top-masteries endpoint.
 */
@Data
public class MasteryDTO {
    private long championId;
    private int championLevel;
    private int championPoints;
}
