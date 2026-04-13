package com.lol.analyzer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchDTO {
    private InfoDTO info;

    // Сделали класс public и static, чтобы сервис его видел
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InfoDTO {
        private List<ParticipantDTO> participants;
        private long gameDuration;
    }

    // Сделали класс public и static
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ParticipantDTO {
        private String puuid;
        private int kills;
        private int deaths;
        private int assists;
        private int goldEarned;
    }
}