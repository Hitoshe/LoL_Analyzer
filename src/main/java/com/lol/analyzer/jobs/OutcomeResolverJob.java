package com.lol.analyzer.jobs;

import com.lol.analyzer.client.RiotClient;
import com.lol.analyzer.model.AnalysisHistory;
import com.lol.analyzer.model.MatchDTO;
import com.lol.analyzer.repository.AnalysisHistoryRepository;
import com.lol.analyzer.riot.Region;
import com.lol.analyzer.riot.RiotApiException;
import com.lol.analyzer.service.MatchSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Finds the real match played after an analysis (most analyzed players in one game) and stores who won,
 * so {@code /history} can show prediction accuracy and calibration. Cheap enough for a development key.
 */
@Component
@ConditionalOnProperty(name = "analyzer.outcome.enabled", havingValue = "true", matchIfMissing = true)
public class OutcomeResolverJob {

    private static final Logger log = LoggerFactory.getLogger(OutcomeResolverJob.class);
    private static final int MAX_ATTEMPTS = 8;
    private static final int MATCHES_TO_CHECK = 3;

    private final AnalysisHistoryRepository historyRepository;
    private final RiotClient riotClient;
    private final MatchSyncService matchSyncService;
    private final int batchSize;

    public OutcomeResolverJob(
            AnalysisHistoryRepository historyRepository,
            RiotClient riotClient,
            MatchSyncService matchSyncService,
            @Value("${analyzer.outcome.batch-size:3}") int batchSize) {
        this.historyRepository = historyRepository;
        this.riotClient = riotClient;
        this.matchSyncService = matchSyncService;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${analyzer.outcome.interval-ms:900000}", initialDelayString = "${analyzer.outcome.initial-delay-ms:120000}")
    public void run() {
        if (!riotClient.hasApiKey()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<AnalysisHistory> pending = historyRepository.findPendingOutcome(
                now.minusHours(24), now.minusMinutes(25), MAX_ATTEMPTS, PageRequest.of(0, batchSize));
        for (AnalysisHistory history : pending) {
            try {
                resolve(history);
            } catch (RiotApiException e) {
                log.warn("Outcome resolver paused: {}", e.getMessage());
                return;
            }
        }
    }

    void resolve(AnalysisHistory history) {
        Region region = Region.parse(history.getRegion()).orElse(Region.EUW);
        List<String> blue = split(history.getBluePuuids());
        Set<String> all = new HashSet<>(blue);
        all.addAll(split(history.getRedPuuids()));
        if (blue.isEmpty()) {
            history.setResolveAttempts(MAX_ATTEMPTS);
            historyRepository.save(history);
            return;
        }
        long since = history.getCreatedAt().minusMinutes(60).atZone(ZoneId.systemDefault()).toEpochSecond();
        String[] ids = riotClient.getMatchIds(region, blue.get(0), null, since, 10);
        List<String> oldestFirst = ids == null ? List.of() : new ArrayList<>(Arrays.asList(ids));
        Collections.reverse(oldestFirst);

        int required = all.size() >= 6 ? all.size() - 2 : all.size();
        for (String id : oldestFirst.subList(0, Math.min(MATCHES_TO_CHECK, oldestFirst.size()))) {
            MatchDTO match = matchSyncService.fetchAndStore(id);
            if (match == null || match.getInfo() == null || match.getInfo().getParticipants() == null) {
                continue;
            }
            List<MatchDTO.ParticipantDTO> participants = match.getInfo().getParticipants();
            long overlap = participants.stream().filter(p -> all.contains(p.getPuuid())).count();
            if (overlap < required) {
                continue;
            }
            Map<Integer, Long> blueTeams = new HashMap<>();
            participants.stream().filter(p -> blue.contains(p.getPuuid()))
                    .forEach(p -> blueTeams.merge(p.getTeamId(), 1L, Long::sum));
            int blueTeamId = blueTeams.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(100);
            Optional<MatchDTO.ParticipantDTO> anyBlue = participants.stream().filter(p -> p.getTeamId() == blueTeamId).findFirst();
            history.setResolvedMatchId(id);
            if (anyBlue.isPresent() && !anyBlue.get().isGameEndedInEarlySurrender()) {
                history.setActualBlueWin(anyBlue.get().isWin());
            }
            historyRepository.save(history);
            log.info("Analysis #{} resolved by {}: blue {}", history.getId(), id,
                    Boolean.TRUE.equals(history.getActualBlueWin()) ? "won" : "lost/remake");
            return;
        }
        history.setResolveAttempts((history.getResolveAttempts() == null ? 0 : history.getResolveAttempts()) + 1);
        historyRepository.save(history);
    }

    private static List<String> split(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
