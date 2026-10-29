package com.lol.analyzer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Spectator-V5 active game: available only after loading screen starts (champ select is not exposed by Riot).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CurrentGameDTO {
    private long gameId;
    private long gameQueueConfigId;
    private String gameMode;
    private List<Participant> participants;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Participant {
        private String puuid;
        private String riotId;
        private long teamId;
        private long championId;
        private long spell1Id;
        private long spell2Id;
        private boolean bot;
    }
}
