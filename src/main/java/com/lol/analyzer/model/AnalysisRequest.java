package com.lol.analyzer.model;

import lombok.Data;

import java.util.List;

/**
 * Thymeleaf form binding for POST {@code /analyze}: two parallel lists of {@link PlayerInput} rows.
 */
@Data
public class AnalysisRequest {
    private List<PlayerInput> teamA;
    private List<PlayerInput> teamB;

    @Data
    public static class PlayerInput {
        private String name;
        private String tag;
        private String lane;
        private String championName;
    }
}
