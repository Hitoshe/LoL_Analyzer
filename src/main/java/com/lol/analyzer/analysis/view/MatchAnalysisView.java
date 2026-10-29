package com.lol.analyzer.analysis.view;

import com.lol.analyzer.analysis.Prediction;
import com.lol.analyzer.riot.Region;

import java.util.List;

/**
 * Everything {@code result.html} renders. Chances are percentages for the blue side (red = 100 - blue).
 *
 * @param postDraftBlue {@code null} when no champions were entered
 */
public record MatchAnalysisView(
        Region region,
        double preDraftBlue,
        Double postDraftBlue,
        List<FactorRow> factors,
        List<LaneRow> lanes,
        List<String> synergyNotes,
        String confidenceLabel,
        String confidenceCss,
        List<String> warnings,
        Prediction prediction) {

    public double finalBlue() {
        return postDraftBlue != null ? postDraftBlue : preDraftBlue;
    }

    public boolean hasDraft() {
        return postDraftBlue != null;
    }

    public record FactorRow(String label, boolean draft, String blue, String red, double impact) {
    }

    /**
     * @param blueLaneChance {@code null} when only one side has a player in this lane
     */
    public record LaneRow(String lane, PlayerCard blue, PlayerCard red, Double blueLaneChance, String counterNote) {
    }
}
