package com.lol.analyzer.service;

import com.lol.analyzer.model.Champion;
import com.lol.analyzer.model.GameMatch;
import com.lol.analyzer.model.MatchDTO;
import com.lol.analyzer.model.MatchParticipant;
import com.lol.analyzer.repository.ChampionRepository;
import com.lol.analyzer.repository.GameMatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Persists ranked {@link GameMatch} rows (and {@link MatchParticipant} children) when a profile refresh pulls new payloads.
 * Skips matches already stored to avoid duplicate keys; upserts {@link Champion} rows for referenced champion IDs.
 */
@Service
public class ProfileMatchPersistenceService {

    private final GameMatchRepository gameMatchRepository;
    private final ChampionRepository championRepository;
    private final DataDragonService dataDragonService;

    public ProfileMatchPersistenceService(
            GameMatchRepository gameMatchRepository,
            ChampionRepository championRepository,
            DataDragonService dataDragonService) {
        this.gameMatchRepository = gameMatchRepository;
        this.championRepository = championRepository;
        this.dataDragonService = dataDragonService;
    }

    @Transactional
    public void persistNewMatchesFromDtos(List<MatchDTO> matches) {
        if (matches == null || matches.isEmpty()) {
            return;
        }
        for (MatchDTO dto : matches) {
            if (dto.getMetadata() == null || dto.getMetadata().getMatchId() == null) {
                continue;
            }
            if (dto.getInfo() == null || dto.getInfo().getParticipants() == null) {
                continue;
            }
            String matchId = dto.getMetadata().getMatchId();
            if (gameMatchRepository.existsById(matchId)) {
                continue;
            }
            int durationSec = (int) Math.max(1L, dto.getInfo().getGameDuration());

            GameMatch gameMatch = new GameMatch();
            gameMatch.setMatchId(matchId);
            gameMatch.setGameDurationSeconds(durationSec);

            for (MatchDTO.ParticipantDTO p : dto.getInfo().getParticipants()) {
                if (p.getPuuid() == null) {
                    continue;
                }
                Champion champion = resolveChampion((long) p.getChampionId());
                MatchParticipant row = new MatchParticipant();
                row.setMatch(gameMatch);
                row.setPuuid(p.getPuuid());
                row.setChampion(champion);
                row.setTeamId(p.getTeamId());
                row.setKills(p.getKills());
                row.setDeaths(p.getDeaths());
                row.setAssists(p.getAssists());
                row.setGoldEarned(p.getGoldEarned());
                double kda = (p.getKills() + p.getAssists()) / (double) Math.max(1, p.getDeaths());
                double gpm = p.getGoldEarned() / (durationSec / 60.0);
                row.setKda(Math.round(kda * 100.0) / 100.0);
                row.setGpm(Math.round(gpm * 100.0) / 100.0);
                gameMatch.getParticipants().add(row);
            }
            if (!gameMatch.getParticipants().isEmpty()) {
                gameMatchRepository.save(gameMatch);
            }
        }
    }

    private Champion resolveChampion(long championId) {
        return championRepository.findById(championId).orElseGet(() -> {
            String name = dataDragonService.getChampionName(championId);
            return championRepository.save(new Champion(championId, name));
        });
    }
}
