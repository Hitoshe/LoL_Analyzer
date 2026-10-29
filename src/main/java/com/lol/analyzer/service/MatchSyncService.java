package com.lol.analyzer.service;

import com.lol.analyzer.client.RiotClient;
import com.lol.analyzer.model.MatchDTO;
import com.lol.analyzer.repository.GameMatchRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Downloads only the matches that are not stored yet, newest first, and persists them.
 * Persisting is serialized because premade players share matches and are loaded in parallel.
 */
@Service
public class MatchSyncService {

    private final RiotClient riotClient;
    private final GameMatchRepository gameMatchRepository;
    private final ProfileMatchPersistenceService persistenceService;
    private final Object persistLock = new Object();

    public MatchSyncService(
            RiotClient riotClient,
            GameMatchRepository gameMatchRepository,
            ProfileMatchPersistenceService persistenceService) {
        this.riotClient = riotClient;
        this.gameMatchRepository = gameMatchRepository;
        this.persistenceService = persistenceService;
    }

    /**
     * @param matchIds ids as returned by Riot (newest first)
     * @param maxNew   download budget for this call
     * @return downloaded matches (already persisted)
     */
    public List<MatchDTO> syncMissing(String[] matchIds, int maxNew) {
        if (matchIds == null || matchIds.length == 0 || maxNew <= 0) {
            return List.of();
        }
        Set<String> complete = new HashSet<>(gameMatchRepository.findCompleteIds(List.of(matchIds)));
        List<MatchDTO> downloaded = new ArrayList<>();
        for (String id : matchIds) {
            if (downloaded.size() >= maxNew) {
                break;
            }
            if (complete.contains(id)) {
                continue;
            }
            MatchDTO match = riotClient.getMatch(id);
            if (match != null && match.getInfo() != null) {
                downloaded.add(match);
            }
        }
        if (!downloaded.isEmpty()) {
            synchronized (persistLock) {
                persistenceService.persistNewMatchesFromDtos(downloaded);
            }
        }
        return downloaded;
    }

    /** Always downloads the match (results are needed even if a stored copy exists) and stores it if missing. */
    public MatchDTO fetchAndStore(String matchId) {
        MatchDTO match = riotClient.getMatch(matchId);
        if (match != null && match.getInfo() != null) {
            synchronized (persistLock) {
                persistenceService.persistNewMatchesFromDtos(List.of(match));
            }
        }
        return match;
    }
}
