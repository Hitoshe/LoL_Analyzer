package com.lol.analyzer.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Form binding for POST {@code /analyze}: server and two rosters. Each row holds a Riot ID ({@code Name#TAG}),
 * a lane and an optional champion; rows with an empty Riot ID are ignored.
 */
@Data
public class AnalysisRequest {

    public static final int TEAM_SIZE = 5;

    private String region;
    private List<PlayerInput> teamA = new ArrayList<>();
    private List<PlayerInput> teamB = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayerInput {
        private String riotId;
        private String lane;
        private String championName;
    }

    public static AnalysisRequest blank(String region) {
        AnalysisRequest request = new AnalysisRequest();
        request.setRegion(region);
        request.padRows();
        return request;
    }

    /** Ensures five rows per team so the form always renders full rosters with default lanes. */
    public void padRows() {
        teamA = pad(teamA);
        teamB = pad(teamB);
    }

    private static List<PlayerInput> pad(List<PlayerInput> rows) {
        List<PlayerInput> out = new ArrayList<>(rows == null ? List.of() : rows);
        Lane[] lanes = Lane.values();
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i) == null) {
                out.set(i, new PlayerInput(null, lanes[i % lanes.length].name(), null));
            }
        }
        while (out.size() < TEAM_SIZE) {
            out.add(new PlayerInput(null, lanes[out.size()].name(), null));
        }
        return new ArrayList<>(out.subList(0, TEAM_SIZE));
    }
}
