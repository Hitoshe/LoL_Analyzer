package com.lol.analyzer.service;

import com.lol.analyzer.model.*;
import com.lol.analyzer.repository.ChampionRepository;
import com.lol.analyzer.repository.GameMatchRepository;
import com.lol.analyzer.repository.SummonerMasteryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Persists profile data pulled from Riot: full {@link GameMatch} rows (queue, start time, patch, lane, result per participant)
 * and the champion mastery list. Matches stored by older versions without results are replaced when downloaded again.
 */
@Service
public class ProfileMatchPersistenceService {

    private final GameMatchRepository gameMatchRepository;
    private final ChampionRepository championRepository;
    private final SummonerMasteryRepository masteryRepository;
    private final DataDragonService dataDragonService;

    public ProfileMatchPersistenceService(
            GameMatchRepository gameMatchRepository,
            ChampionRepository championRepository,
            SummonerMasteryRepository masteryRepository,
            DataDragonService dataDragonService) {
        this.gameMatchRepository = gameMatchRepository;
        this.championRepository = championRepository;
        this.masteryRepository = masteryRepository;
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
            var existing = gameMatchRepository.findById(matchId);
            if (existing.isPresent()) {
                if (existing.get().getQueueId() != null) {
                    continue;
                }
                gameMatchRepository.delete(existing.get());
                gameMatchRepository.flush();
            }
            GameMatch gameMatch = toEntity(matchId, dto.getInfo());
            if (!gameMatch.getParticipants().isEmpty()) {
                gameMatchRepository.save(gameMatch);
            }
        }
    }

    @Transactional
    public void replaceMasteries(String puuid, MasteryDTO[] masteries) {
        masteryRepository.deleteAllByPuuid(puuid);
        if (masteries == null) {
            return;
        }
        List<SummonerMastery> rows = new ArrayList<>(masteries.length);
        for (MasteryDTO dto : masteries) {
            SummonerMastery row = new SummonerMastery();
            row.setPuuid(puuid);
            row.setChampionId(dto.getChampionId());
            row.setChampionLevel(dto.getChampionLevel());
            row.setChampionPoints(dto.getChampionPoints());
            row.setLastPlayTime(dto.getLastPlayTime());
            rows.add(row);
        }
        masteryRepository.saveAll(rows);
    }

    private GameMatch toEntity(String matchId, MatchDTO.InfoDTO info) {
        int durationSec = (int) Math.max(1L, info.getGameDuration());
        GameMatch gameMatch = new GameMatch();
        gameMatch.setMatchId(matchId);
        gameMatch.setGameDurationSeconds(durationSec);
        gameMatch.setPlatformId(info.getPlatformId());
        gameMatch.setQueueId(info.getQueueId());
        gameMatch.setGameStartTimestamp(info.getGameStartTimestamp());
        gameMatch.setGameVersion(info.getGameVersion());
        gameMatch.setPatch(ChampionCatalog.patchOf(info.getGameVersion()));

        for (MatchDTO.ParticipantDTO p : info.getParticipants()) {
            if (p.getPuuid() == null) {
                continue;
            }
            MatchParticipant row = new MatchParticipant();
            row.setMatch(gameMatch);
            row.setPuuid(p.getPuuid());
            row.setChampion(resolveChampion(p.getChampionId()));
            row.setTeamId(p.getTeamId());
            row.setTeamPosition(Lane.fromRiotPosition(p.getTeamPosition()).orElse(null));
            row.setWin(p.isGameEndedInEarlySurrender() ? null : p.isWin());
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
        return gameMatch;
    }

    private Champion resolveChampion(long championId) {
        return championRepository.findById(championId).orElseGet(() -> {
            String name = dataDragonService.getChampionName(championId);
            return championRepository.save(new Champion(championId, name));
        });
    }
}
