package com.lol.analyzer.analysis;

/**
 * Per-player model output in Elo points. Pre-draft parts: skill, form, role, session; draft parts: comfort, meta.
 *
 * @param rankEstimated true when the player is unranked and skill was taken from the lobby average
 */
public record SeatResult(
        PlayerSnapshot snapshot,
        double skillElo,
        boolean rankEstimated,
        double formElo,
        double roleElo,
        double sessionElo,
        double comfortElo,
        double metaElo,
        WinRecord metaRecord) {

    public double preDraftStrength() {
        return skillElo + formElo + roleElo + sessionElo;
    }

    public double strength() {
        return preDraftStrength() + comfortElo + metaElo;
    }
}
