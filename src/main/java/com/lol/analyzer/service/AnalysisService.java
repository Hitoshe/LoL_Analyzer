package com.lol.analyzer.service;

import com.lol.analyzer.analysis.AnalysisException;
import com.lol.analyzer.analysis.PlayerSnapshot;
import com.lol.analyzer.analysis.Prediction;
import com.lol.analyzer.analysis.WinProbabilityModel;
import com.lol.analyzer.analysis.view.MatchAnalysisView;
import com.lol.analyzer.model.AnalysisRequest;
import com.lol.analyzer.model.ChampionInfo;
import com.lol.analyzer.model.Lane;
import com.lol.analyzer.model.Summoner;
import com.lol.analyzer.riot.PlayerNotFoundException;
import com.lol.analyzer.riot.Region;
import com.lol.analyzer.riot.RiotApiException;
import com.lol.analyzer.riot.RiotId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

/**
 * Validates the submitted rosters, loads all players in parallel, builds {@link PlayerSnapshot}s,
 * runs {@link WinProbabilityModel} and turns the result into a {@link MatchAnalysisView}.
 */
@Service
public class AnalysisService {

    private final SummonerService summonerService;
    private final PlayerStatsService playerStatsService;
    private final ChampionStatsService championStatsService;
    private final DataDragonService dataDragonService;
    private final WinProbabilityModel model;
    private final AnalysisViewBuilder viewBuilder;
    private final ExecutorService executor;
    private final Region defaultRegion;

    public AnalysisService(
            SummonerService summonerService,
            PlayerStatsService playerStatsService,
            ChampionStatsService championStatsService,
            DataDragonService dataDragonService,
            WinProbabilityModel model,
            AnalysisViewBuilder viewBuilder,
            @Qualifier("riotExecutor") ExecutorService executor,
            Region defaultRegion) {
        this.summonerService = summonerService;
        this.playerStatsService = playerStatsService;
        this.championStatsService = championStatsService;
        this.dataDragonService = dataDragonService;
        this.model = model;
        this.viewBuilder = viewBuilder;
        this.executor = executor;
        this.defaultRegion = defaultRegion;
    }

    public MatchAnalysisView analyze(AnalysisRequest request) {
        Region region = Region.parse(request.getRegion()).orElse(defaultRegion);
        List<String> errors = new ArrayList<>();
        List<Seat> blue = parseTeam(request.getTeamA(), region, "Синие", errors);
        List<Seat> red = parseTeam(request.getTeamB(), region, "Красные", errors);
        if (errors.isEmpty() && (blue.isEmpty() || red.isEmpty())) {
            errors.add("Введите хотя бы по одному игроку в каждой команде.");
        }
        checkDuplicatePlayers(blue, red, errors);
        if (!errors.isEmpty()) {
            throw new AnalysisException(errors);
        }

        Map<Seat, Summoner> summoners = loadAll(region, blue, red);
        long now = System.currentTimeMillis();
        List<PlayerSnapshot> blueSnaps = blue.stream().map(s -> snapshot(s, summoners.get(s), now)).toList();
        List<PlayerSnapshot> redSnaps = red.stream().map(s -> snapshot(s, summoners.get(s), now)).toList();

        Prediction prediction = model.predict(blueSnaps, redSnaps, championStatsService);
        Map<Long, ChampionInfo> champions = new HashMap<>();
        for (Seat s : concat(blue, red)) {
            if (s.champion() != null) {
                champions.put(s.champion().id(), s.champion());
            }
        }
        return viewBuilder.build(region, prediction, champions, now);
    }

    private PlayerSnapshot snapshot(Seat seat, Summoner summoner, long now) {
        Long championId = seat.champion() == null ? null : seat.champion().id();
        return playerStatsService.snapshot(summoner, seat.lane(), championId, now);
    }

    private List<Seat> parseTeam(List<AnalysisRequest.PlayerInput> rows, Region region, String teamName, List<String> errors) {
        List<Seat> seats = new ArrayList<>();
        if (rows == null) {
            return seats;
        }
        Set<Lane> usedLanes = EnumSet.noneOf(Lane.class);
        for (AnalysisRequest.PlayerInput row : rows) {
            if (row == null || row.getRiotId() == null || row.getRiotId().isBlank()) {
                continue;
            }
            Optional<RiotId> riotId = RiotId.parse(row.getRiotId(), region.getDefaultTag());
            if (riotId.isEmpty()) {
                errors.add(teamName + ": не удалось разобрать «" + row.getRiotId().trim() + "». Формат: Ник#ТЕГ.");
                continue;
            }
            Lane lane = Lane.parse(row.getLane()).orElse(null);
            if (lane == null) {
                errors.add(teamName + ": выберите линию для " + riotId.get().display() + ".");
            } else if (!usedLanes.add(lane)) {
                errors.add(teamName + ": линия " + lane.getLabel() + " указана дважды.");
            }
            ChampionInfo champion = null;
            if (row.getChampionName() != null && !row.getChampionName().isBlank()) {
                champion = dataDragonService.findChampion(row.getChampionName()).orElse(null);
                if (champion == null) {
                    errors.add(teamName + ": неизвестный чемпион «" + row.getChampionName().trim() + "».");
                }
            }
            seats.add(new Seat(riotId.get(), lane, champion));
        }
        return seats;
    }

    private static void checkDuplicatePlayers(List<Seat> blue, List<Seat> red, List<String> errors) {
        List<Seat> all = concat(blue, red);
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                if (all.get(i).riotId().sameAs(all.get(j).riotId())) {
                    errors.add("Игрок " + all.get(i).riotId().display() + " указан дважды.");
                }
            }
        }
    }

    private Map<Seat, Summoner> loadAll(Region region, List<Seat> blue, List<Seat> red) {
        Map<Seat, CompletableFuture<Summoner>> futures = new LinkedHashMap<>();
        for (Seat seat : concat(blue, red)) {
            futures.put(seat, CompletableFuture.supplyAsync(() -> summonerService.getAccount(region, seat.riotId()), executor));
        }
        Map<Seat, Summoner> out = new HashMap<>();
        List<String> notFound = new ArrayList<>();
        RiotApiException apiError = null;
        for (Map.Entry<Seat, CompletableFuture<Summoner>> e : futures.entrySet()) {
            try {
                out.put(e.getKey(), e.getValue().join());
            } catch (CompletionException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof PlayerNotFoundException nf) {
                    notFound.add(nf.getMessage());
                } else if (cause instanceof RiotApiException api) {
                    apiError = api;
                } else {
                    throw ex;
                }
            }
        }
        if (apiError != null) {
            throw new AnalysisException(apiError.getMessage());
        }
        if (!notFound.isEmpty()) {
            throw new AnalysisException(notFound);
        }
        return out;
    }

    private static List<Seat> concat(List<Seat> a, List<Seat> b) {
        List<Seat> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    private record Seat(RiotId riotId, Lane lane, ChampionInfo champion) {
    }
}
