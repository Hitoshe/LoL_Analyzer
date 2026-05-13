package com.lol.analyzer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Subset of Riot Match-V5 JSON used here: metadata for {@code matchId}, info for duration and per-participant stats.
 * Nested DTOs are {@code public static} so other packages (services) can reference them without synthetic accessors.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchDTO {

    private MetadataDTO metadata;

    private InfoDTO info;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MetadataDTO {
        private String matchId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InfoDTO {
        private List<ParticipantDTO> participants;
        private long gameDuration;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ParticipantDTO {
        private String puuid;
        private int championId;
        private int teamId;
        private int kills;
        private int deaths;
        private int assists;
        private int goldEarned;
    }
}
