package com.lol.analyzer.analysis;

import com.lol.analyzer.model.Lane;
import com.lol.analyzer.model.Summoner;

import java.util.Map;

/**
 * Everything known about one seat before the model runs: profile, recent ranked form, role habits,
 * current session and (if a champion was picked) personal record and mastery on that champion.
 *
 * @param minutesSinceLastGame {@code null} when no stored ranked games
 * @param championGames        {@code 0} when no champion was picked or never played in stored games
 * @param masteryPoints        {@code null} when no champion was picked
 * @param daysSinceChampionPlayed {@code null} when unknown or never played
 */
public record PlayerSnapshot(
        Summoner summoner,
        Lane lane,
        int recentGames,
        int recentWins,
        Map<Lane, Integer> laneCounts,
        Lane mainLane,
        RoleFit roleFit,
        double laneShare,
        int sessionGames,
        int sessionWins,
        int sessionLossStreak,
        Long minutesSinceLastGame,
        int gamesLast24h,
        Long championId,
        int championGames,
        int championWins,
        Integer masteryPoints,
        Integer masteryLevel,
        Long daysSinceChampionPlayed) {

    public boolean inSession() {
        return sessionGames > 0;
    }

    public boolean hasChampion() {
        return championId != null;
    }
}
