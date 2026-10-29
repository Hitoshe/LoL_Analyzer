package com.lol.analyzer.analysis.view;

import java.util.List;

/**
 * Pre-formatted texts for one player on the result page.
 *
 * @param championLabel  {@code null} when no champion was entered
 */
public record PlayerCard(
        String riotId,
        String profileUrl,
        String laneLabel,
        String championName,
        String championIcon,
        boolean mainChampion,
        String rankLabel,
        String seasonLabel,
        Long level,
        String recentLabel,
        String kdaLabel,
        String roleFitLabel,
        String roleFitCss,
        String roleHabitLabel,
        String sessionLabel,
        String sessionCss,
        String championLabel,
        String strengthLabel,
        List<String> notes) {
}
