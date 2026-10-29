package com.lol.analyzer.jobs;

import com.lol.analyzer.client.RiotClient;
import com.lol.analyzer.model.LeagueListDTO;
import com.lol.analyzer.riot.Region;
import com.lol.analyzer.riot.RiotApiException;
import com.lol.analyzer.service.ChampionStatsService;
import com.lol.analyzer.service.MatchSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Builds the meta / matchup / synergy sample by downloading recent Ranked Solo games of apex-ladder players.
 * <p>Disabled by default: with a development key (100 requests per 2 minutes) it would compete with user analyses
 * and collect only a few thousand matches a day. Enable with {@code analyzer.collector.enabled=true}
 * once a production key is available, and tune the budget below.
 */
@Component
@ConditionalOnProperty(name = "analyzer.collector.enabled", havingValue = "true")
public class MetaCollectorJob {

    private static final Logger log = LoggerFactory.getLogger(MetaCollectorJob.class);
    private static final List<String> APEX_LEAGUES = List.of("challengerleagues", "grandmasterleagues", "masterleagues");

    private final RiotClient riotClient;
    private final MatchSyncService matchSyncService;
    private final ChampionStatsService championStatsService;
    private final List<Region> regions;
    private final int playersPerRun;
    private final int matchesPerPlayer;
    private final Map<Region, Deque<String>> queues = new EnumMap<>(Region.class);

    public MetaCollectorJob(
            RiotClient riotClient,
            MatchSyncService matchSyncService,
            ChampionStatsService championStatsService,
            @Value("${analyzer.collector.regions:EUW}") List<String> regionCodes,
            @Value("${analyzer.collector.players-per-run:5}") int playersPerRun,
            @Value("${analyzer.collector.matches-per-player:10}") int matchesPerPlayer) {
        this.riotClient = riotClient;
        this.matchSyncService = matchSyncService;
        this.championStatsService = championStatsService;
        this.regions = regionCodes.stream().map(Region::parse).flatMap(Optional::stream).toList();
        this.playersPerRun = playersPerRun;
        this.matchesPerPlayer = matchesPerPlayer;
    }

    @Scheduled(fixedDelayString = "${analyzer.collector.interval-ms:600000}", initialDelayString = "${analyzer.collector.initial-delay-ms:60000}")
    public void run() {
        int downloaded = 0;
        try {
            for (Region region : regions) {
                for (int i = 0; i < playersPerRun; i++) {
                    String puuid = nextPlayer(region);
                    if (puuid == null) {
                        break;
                    }
                    String[] ids = riotClient.getMatchIds(region, puuid, ChampionStatsService.SOLO_QUEUE, null, matchesPerPlayer);
                    downloaded += matchSyncService.syncMissing(ids, matchesPerPlayer).size();
                }
            }
        } catch (RiotApiException e) {
            log.warn("Meta collector paused: {}", e.getMessage());
        }
        if (downloaded > 0) {
            log.info("Meta collector stored {} new matches", downloaded);
            championStatsService.refresh();
        }
    }

    private String nextPlayer(Region region) {
        Deque<String> queue = queues.computeIfAbsent(region, r -> new ArrayDeque<>());
        if (queue.isEmpty()) {
            List<String> ladder = new ArrayList<>();
            for (String league : APEX_LEAGUES) {
                LeagueListDTO list = riotClient.getApexLeague(region, league);
                if (list != null && list.getEntries() != null) {
                    list.getEntries().stream().map(LeagueListDTO.Entry::getPuuid).filter(Objects::nonNull).forEach(ladder::add);
                }
            }
            Collections.shuffle(ladder);
            queue.addAll(ladder);
            log.info("Meta collector: {} apex players queued on {}", ladder.size(), region.getCode());
        }
        return queue.pollFirst();
    }
}
