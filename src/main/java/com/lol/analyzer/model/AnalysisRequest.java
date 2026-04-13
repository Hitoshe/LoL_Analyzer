package com.lol.analyzer.model;

import lombok.Data;
import java.util.List;

@Data
public class AnalysisRequest {
    private List<PlayerInput> teamA;
    private List<PlayerInput> teamB;

    @Data
    public static class PlayerInput {
        private String name;
        private String tag;
        private String lane; // TOP, JUNGLE, MIDDLE, BOTTOM, UTILITY
        private String championName;
    }
}