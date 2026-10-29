package com.lol.analyzer.analysis;

import com.lol.analyzer.model.Lane;

import java.util.List;

/**
 * Result of {@link WinProbabilityModel#predict}: blue-side win probability before and after draft,
 * the Elo breakdown by factor, and per-lane / per-duo draft effects.
 *
 * @param postDraftBlue {@code null} when no champions were entered
 */
public record Prediction(
        double preDraftBlue,
        Double postDraftBlue,
        List<SeatResult> blue,
        List<SeatResult> red,
        List<Factor> factors,
        List<LaneEdge> lanes,
        List<Synergy> synergies) {

    public double finalBlue() {
        return postDraftBlue != null ? postDraftBlue : preDraftBlue;
    }

    /** {@code blue}/{@code red} are team totals in Elo; the impact is what this factor alone adds to blue's chance. */
    public record Factor(String key, String label, boolean draft, double blue, double red) {
        public double impactPercent() {
            return (WinProbabilityModel.probability(blue - red) - 0.5) * 100.0;
        }
    }

    /**
     * @param counterElo  draft-only lane effect from blue's perspective (0 without matchup data)
     * @param edgeElo     blue player strength minus red player strength plus {@code counterElo}
     */
    public record LaneEdge(Lane lane, SeatResult blue, SeatResult red, double counterElo, WinRecord counterRecord, double edgeElo) {
        public double blueLaneChance() {
            return WinProbabilityModel.probability(edgeElo);
        }
    }

    public record Synergy(boolean blueTeam, Lane laneA, Lane laneB, double elo, WinRecord record) {
    }
}
