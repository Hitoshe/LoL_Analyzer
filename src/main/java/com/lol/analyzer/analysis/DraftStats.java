package com.lol.analyzer.analysis;

import com.lol.analyzer.model.Lane;

/**
 * Population win rates used by the draft part of {@link WinProbabilityModel}.
 */
public interface DraftStats {

    WinRecord meta(long championId, Lane lane);

    /** Record of {@code championId} against {@code enemyChampionId} in the same lane. */
    WinRecord matchup(Lane lane, long championId, long enemyChampionId);

    WinRecord synergy(Lane laneA, long championA, Lane laneB, long championB);

    DraftStats NONE = new DraftStats() {
        @Override
        public WinRecord meta(long championId, Lane lane) {
            return WinRecord.EMPTY;
        }

        @Override
        public WinRecord matchup(Lane lane, long championId, long enemyChampionId) {
            return WinRecord.EMPTY;
        }

        @Override
        public WinRecord synergy(Lane laneA, long championA, Lane laneB, long championB) {
            return WinRecord.EMPTY;
        }
    };
}
