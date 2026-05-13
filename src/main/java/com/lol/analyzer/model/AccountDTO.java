package com.lol.analyzer.model;

import lombok.Data;

/**
 * Riot Account-V1 payload: stable {@code puuid} plus Riot ID components.
 */
@Data
public class AccountDTO {
    private String puuid;
    private String gameName;
    private String tagLine;
}
