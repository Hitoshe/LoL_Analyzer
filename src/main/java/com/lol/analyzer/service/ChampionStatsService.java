package com.lol.analyzer.service;

import com.lol.analyzer.analysis.DraftStats;
import com.lol.analyzer.analysis.WinRecord;
import com.lol.analyzer.model.Lane;
import com.lol.analyzer.repository.GameMatchRepository;
import com.lol.analyzer.repository.StatsQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * In-memory champion meta, lane matchup and duo synergy win rates over stored Ranked Solo matches
 * from the last {@code analyzer.stats.window-days}. Rebuilt on a schedule; lookups never hit the database.
 * <p>The sample is whatever the app has stored: profiles of analyzed players plus the meta collector when enabled.
 */
@Service
public class ChampionStatsService implements DraftStats {

    private static final Logger log = LoggerFactory.getLogger(ChampionStatsService.class);
    public static final int SOLO_QUEUE = 420;

    private final StatsQueryRepository statsQueryRepository;
    private final GameMatchRepository gameMatchRepository;
    private final int windowDays;

    private volatile Snapshot snapshot = Snapshot.EMPTY;

    public ChampionStatsService(
            StatsQueryRepository statsQueryRepository,
            GameMatchRepository gameMatchRepository,
            @Value("${analyzer.stats.window-days:30}") int windowDays) {
        this.statsQueryRepository = statsQueryRepository;
        this.gameMatchRepository = gameMatchRepository;
        this.windowDays = windowDays;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        refresh();
    }

    @Scheduled(fixedDelayString = "${analyzer.stats.refresh-ms:1800000}", initialDelayString = "${analyzer.stats.refresh-ms:1800000}")
    public void refresh() {
        try {
            long since = System.currentTimeMillis() - windowDays * 86_400_000L;
            Map<Long, Map<Lane, WinRecord>> roles = new HashMap<>();
            for (Object[] r : statsQueryRepository.championRoleStats(SOLO_QUEUE, since)) {
                Lane lane = Lane.valueOf((String) r[1]);
                roles.computeIfAbsent(num(r[0]), k -> new EnumMap<>(Lane.class)).put(lane, record(r[2], r[3]));
            }
            Map<String, WinRecord> matchups = new HashMap<>();
            for (Object[] r : statsQueryRepository.laneMatchupStats(SOLO_QUEUE, since)) {
                matchups.put(matchupKey(Lane.valueOf((String) r[0]), num(r[1]), num(r[2])), record(r[3], r[4]));
            }
            Map<String, WinRecord> synergies = new HashMap<>();
            for (Object[] r : statsQueryRepository.duoSynergyStats(SOLO_QUEUE, since)) {
                synergies.put(synergyKey(Lane.valueOf((String) r[0]), num(r[1]), Lane.valueOf((String) r[2]), num(r[3])),
                        record(r[4], r[5]));
            }
            long matches = gameMatchRepository.countByQueueIdAndGameStartTimestampGreaterThanEqual(SOLO_QUEUE, since);
            snapshot = new Snapshot(roles, matchups, synergies, matches);
            log.info("Champion stats rebuilt: {} solo-queue matches in last {} days", matches, windowDays);
        } catch (Exception e) {
            log.error("Champion stats rebuild failed: {}", e.getMessage());
        }
    }

    public long sampleMatches() {
        return snapshot.sampleMatches;
    }

    public int windowDays() {
        return windowDays;
    }

    @Override
    public WinRecord meta(long championId, Lane lane) {
        Map<Lane, WinRecord> byLane = snapshot.roles.get(championId);
        return byLane == null ? WinRecord.EMPTY : byLane.getOrDefault(lane, WinRecord.EMPTY);
    }

    /** Games per lane for the champion; used to guess roles in a live game. */
    public Map<Lane, WinRecord> laneDistribution(long championId) {
        return snapshot.roles.getOrDefault(championId, Map.of());
    }

    @Override
    public WinRecord matchup(Lane lane, long championId, long enemyChampionId) {
        return snapshot.matchups.getOrDefault(matchupKey(lane, championId, enemyChampionId), WinRecord.EMPTY);
    }

    @Override
    public WinRecord synergy(Lane laneA, long championA, Lane laneB, long championB) {
        return snapshot.synergies.getOrDefault(synergyKey(laneA, championA, laneB, championB), WinRecord.EMPTY);
    }

    private static String matchupKey(Lane lane, long a, long b) {
        return lane.name() + "|" + a + "|" + b;
    }

    /** Lanes are ordered by enum name to match the SQL {@code a.team_position < b.team_position}. */
    private static String synergyKey(Lane laneA, long a, Lane laneB, long b) {
        if (laneA.name().compareTo(laneB.name()) > 0) {
            return synergyKey(laneB, b, laneA, a);
        }
        return laneA.name() + "|" + a + "|" + laneB.name() + "|" + b;
    }

    private static long num(Object o) {
        return ((Number) o).longValue();
    }

    private static WinRecord record(Object games, Object wins) {
        return new WinRecord(((Number) games).intValue(), wins == null ? 0 : ((Number) wins).intValue());
    }

    private record Snapshot(Map<Long, Map<Lane, WinRecord>> roles, Map<String, WinRecord> matchups,
                            Map<String, WinRecord> synergies, long sampleMatches) {
        static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of(), Map.of(), 0);
    }
}
