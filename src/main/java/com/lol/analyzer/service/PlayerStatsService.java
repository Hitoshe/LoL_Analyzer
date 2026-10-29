package com.lol.analyzer.service;

import com.lol.analyzer.analysis.PlayerSnapshot;
import com.lol.analyzer.analysis.RoleFit;
import com.lol.analyzer.model.Lane;
import com.lol.analyzer.model.MatchParticipant;
import com.lol.analyzer.model.Summoner;
import com.lol.analyzer.model.SummonerMastery;
import com.lol.analyzer.repository.MatchParticipantRepository;
import com.lol.analyzer.repository.SummonerMasteryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Derives {@link PlayerSnapshot} features from stored ranked matches and mastery rows (no Riot calls).
 */
@Service
public class PlayerStatsService {

    /** Ranked Solo/Duo and Ranked Flex. */
    public static final List<Integer> RANKED_QUEUES = List.of(420, 440);

    private static final int MIN_GAMES_FOR_ROLE = 5;
    private static final double SECONDARY_SHARE = 0.2;
    private static final long SESSION_START_WINDOW_MIN = 180;
    private static final long SESSION_GAP_MIN = 90;

    private final MatchParticipantRepository participantRepository;
    private final SummonerMasteryRepository masteryRepository;
    private final int recentGames;

    public PlayerStatsService(
            MatchParticipantRepository participantRepository,
            SummonerMasteryRepository masteryRepository,
            @Value("${analyzer.player.recent-games:20}") int recentGames) {
        this.participantRepository = participantRepository;
        this.masteryRepository = masteryRepository;
        this.recentGames = recentGames;
    }

    public PlayerSnapshot snapshot(Summoner summoner, Lane lane, Long championId, long nowMs) {
        List<MatchParticipant> recent = participantRepository.findRecent(
                summoner.getPuuid(), RANKED_QUEUES, PageRequest.of(0, recentGames));

        int wins = (int) recent.stream().filter(p -> Boolean.TRUE.equals(p.getWin())).count();

        Map<Lane, Integer> laneCounts = new EnumMap<>(Lane.class);
        for (MatchParticipant p : recent) {
            if (p.getTeamPosition() != null) {
                laneCounts.merge(p.getTeamPosition(), 1, Integer::sum);
            }
        }
        int gamesWithLane = laneCounts.values().stream().mapToInt(Integer::intValue).sum();
        Lane mainLane = laneCounts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        double share = gamesWithLane == 0 ? 0 : laneCounts.getOrDefault(lane, 0) / (double) gamesWithLane;
        RoleFit fit = roleFit(lane, mainLane, share, gamesWithLane);

        Session session = session(recent, nowMs);
        Long minutesSinceLast = recent.isEmpty() ? null : Math.max(0, (nowMs - gameEndMs(recent.get(0))) / 60_000);
        int last24h = (int) recent.stream().filter(p -> nowMs - startMs(p) <= 24 * 3_600_000L).count();

        int champGames = 0;
        int champWins = 0;
        Integer masteryPoints = null;
        Integer masteryLevel = null;
        Long daysSincePlayed = null;
        if (championId != null) {
            List<Object[]> record = participantRepository.championRecord(summoner.getPuuid(), championId, RANKED_QUEUES);
            if (!record.isEmpty() && record.get(0)[0] != null) {
                champGames = ((Number) record.get(0)[0]).intValue();
                champWins = ((Number) record.get(0)[1]).intValue();
            }
            Optional<SummonerMastery> mastery = masteryRepository.findFirstByPuuidAndChampionId(summoner.getPuuid(), championId);
            masteryPoints = mastery.map(SummonerMastery::getChampionPoints).orElse(0);
            masteryLevel = mastery.map(SummonerMastery::getChampionLevel).orElse(0);
            daysSincePlayed = mastery.filter(m -> m.getLastPlayTime() > 0)
                    .map(m -> Math.max(0, (nowMs - m.getLastPlayTime()) / 86_400_000L))
                    .orElse(null);
        }

        return new PlayerSnapshot(summoner, lane, recent.size(), wins, laneCounts, mainLane, fit, share,
                session.games, session.wins, session.lossStreak, minutesSinceLast, last24h,
                championId, champGames, champWins, masteryPoints, masteryLevel, daysSincePlayed);
    }

    static RoleFit roleFit(Lane lane, Lane mainLane, double share, int gamesWithLane) {
        if (lane == null || gamesWithLane < MIN_GAMES_FOR_ROLE) {
            return RoleFit.UNKNOWN;
        }
        if (lane == mainLane) {
            return RoleFit.MAIN;
        }
        return share >= SECONDARY_SHARE ? RoleFit.SECONDARY : RoleFit.OFFROLE;
    }

    /** Games chained back from the latest one while gaps stay short; empty if the latest game ended long ago. */
    static Session session(List<MatchParticipant> newestFirst, long nowMs) {
        if (newestFirst.isEmpty() || nowMs - gameEndMs(newestFirst.get(0)) > SESSION_START_WINDOW_MIN * 60_000) {
            return new Session(0, 0, 0);
        }
        int games = 0;
        int wins = 0;
        int lossStreak = 0;
        boolean streakOpen = true;
        long nextStart = Long.MAX_VALUE;
        for (MatchParticipant p : newestFirst) {
            if (nextStart != Long.MAX_VALUE && nextStart - gameEndMs(p) > SESSION_GAP_MIN * 60_000) {
                break;
            }
            games++;
            boolean won = Boolean.TRUE.equals(p.getWin());
            if (won) {
                wins++;
                streakOpen = false;
            } else if (streakOpen) {
                lossStreak++;
            }
            nextStart = startMs(p);
        }
        return new Session(games, wins, lossStreak);
    }

    private static long startMs(MatchParticipant p) {
        Long start = p.getMatch().getGameStartTimestamp();
        return start == null ? 0 : start;
    }

    private static long gameEndMs(MatchParticipant p) {
        return startMs(p) + p.getMatch().getGameDurationSeconds() * 1000L;
    }

    record Session(int games, int wins, int lossStreak) {
    }
}
