package com.lol.analyzer.service;

import com.lol.analyzer.analysis.AnalysisException;
import com.lol.analyzer.analysis.RoleAssigner;
import com.lol.analyzer.client.RiotClient;
import com.lol.analyzer.model.*;
import com.lol.analyzer.repository.SummonerRepository;
import com.lol.analyzer.riot.Region;
import com.lol.analyzer.riot.RiotId;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Fills both rosters from the Spectator API by one player's Riot ID: players, champions, and guessed lanes.
 * Works only once the game has started (loading screen); Riot does not expose other players' champ select.
 */
@Service
public class LiveGameService {

    private final RiotClient riotClient;
    private final SummonerRepository summonerRepository;
    private final DataDragonService dataDragonService;
    private final ChampionStatsService championStatsService;

    public LiveGameService(
            RiotClient riotClient,
            SummonerRepository summonerRepository,
            DataDragonService dataDragonService,
            ChampionStatsService championStatsService) {
        this.riotClient = riotClient;
        this.summonerRepository = summonerRepository;
        this.dataDragonService = dataDragonService;
        this.championStatsService = championStatsService;
    }

    public record LivePlayer(String riotId, String lane, String champion, String championIcon) {
    }

    public record LiveGame(String region, long queueId, String gameMode, List<LivePlayer> blue, List<LivePlayer> red) {
    }

    public LiveGame find(Region region, String rawRiotId) {
        RiotId riotId = RiotId.parse(rawRiotId, region.getDefaultTag())
                .orElseThrow(() -> new AnalysisException("Введите Riot ID в формате Ник#ТЕГ."));
        String puuid = summonerRepository.findByGameNameIgnoreCaseAndTagLineIgnoreCase(riotId.gameName(), riotId.tagLine())
                .map(Summoner::getPuuid)
                .orElseGet(() -> {
                    AccountDTO account = riotClient.getAccountByRiotId(region, riotId.gameName(), riotId.tagLine());
                    if (account == null) {
                        throw new AnalysisException("Игрок " + riotId.display() + " не найден.");
                    }
                    return account.getPuuid();
                });

        CurrentGameDTO game = riotClient.getActiveGame(region, puuid);
        if (game == null || game.getParticipants() == null) {
            throw new AnalysisException(riotId.display() + " сейчас не в игре на сервере " + region.getCode()
                    + ". Загрузка работает с экрана загрузки; пики в лобби Riot не отдаёт.");
        }
        return new LiveGame(region.getCode(), game.getGameQueueConfigId(), game.getGameMode(),
                team(region, game, 100), team(region, game, 200));
    }

    private List<LivePlayer> team(Region region, CurrentGameDTO game, long teamId) {
        List<CurrentGameDTO.Participant> players = game.getParticipants().stream()
                .filter(p -> p.getTeamId() == teamId && !p.isBot())
                .limit(5)
                .toList();
        ChampionCatalog catalog = dataDragonService.catalog();
        List<RoleAssigner.Candidate> candidates = players.stream()
                .map(p -> new RoleAssigner.Candidate(p.getSpell1Id(), p.getSpell2Id(),
                        catalog.byId(p.getChampionId()).map(ChampionInfo::tags).orElse(List.of()),
                        championStatsService.laneDistribution(p.getChampionId())))
                .toList();
        List<Lane> lanes = RoleAssigner.assign(candidates);

        List<LivePlayer> out = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            CurrentGameDTO.Participant p = players.get(i);
            ChampionInfo champ = catalog.byId(p.getChampionId()).orElse(null);
            out.add(new LivePlayer(riotIdOf(region, p), lanes.get(i).name(),
                    champ == null ? null : champ.name(), champ == null ? null : champ.iconUrl()));
        }
        out.sort((a, b) -> Lane.valueOf(a.lane()).compareTo(Lane.valueOf(b.lane())));
        return out;
    }

    private String riotIdOf(Region region, CurrentGameDTO.Participant p) {
        if (p.getRiotId() != null && p.getRiotId().contains("#")) {
            return p.getRiotId();
        }
        if (p.getPuuid() == null) {
            return p.getRiotId();
        }
        AccountDTO account = riotClient.getAccountByPuuid(region, p.getPuuid());
        return account == null ? p.getRiotId() : account.getGameName() + "#" + account.getTagLine();
    }
}
