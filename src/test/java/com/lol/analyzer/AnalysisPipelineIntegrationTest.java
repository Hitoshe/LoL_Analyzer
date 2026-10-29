package com.lol.analyzer;

import com.lol.analyzer.analysis.PlayerSnapshot;
import com.lol.analyzer.analysis.Prediction;
import com.lol.analyzer.analysis.RoleFit;
import com.lol.analyzer.analysis.WinProbabilityModel;
import com.lol.analyzer.analysis.view.MatchAnalysisView;
import com.lol.analyzer.controller.HistoryController;
import com.lol.analyzer.controller.WebController;
import com.lol.analyzer.model.*;
import com.lol.analyzer.repository.SummonerRepository;
import com.lol.analyzer.riot.Region;
import com.lol.analyzer.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ui.ExtendedModelMap;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs persistence, stats SQL, the model, view building and all templates against H2 without Riot calls.
 */
@SpringBootTest
class AnalysisPipelineIntegrationTest {

    private static final long NOW = System.currentTimeMillis();
    private static final long AHRI = 103, ZED = 238, JINX = 222, THRESH = 412, LEE = 64;

    @Autowired ProfileMatchPersistenceService persistence;
    @Autowired SummonerRepository summonerRepository;
    @Autowired PlayerStatsService playerStatsService;
    @Autowired ChampionStatsService championStatsService;
    @Autowired WinProbabilityModel model;
    @Autowired AnalysisViewBuilder viewBuilder;
    @Autowired AnalysisHistoryService historyService;
    @Autowired WebController webController;
    @Autowired HistoryController historyController;
    @Autowired ITemplateEngine templateEngine;

    @Test
    void storedMatchesDriveStatsModelViewsAndTemplates() {
        List<MatchDTO> matches = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            boolean blueWins = i % 3 != 0;
            long start = NOW - (i == 0 ? 50 : 60L * 24 * (i + 1)) * 60_000L;
            matches.add(match("EUW1_" + i, start, blueWins));
        }
        persistence.persistNewMatchesFromDtos(matches);
        persistence.persistNewMatchesFromDtos(matches);
        championStatsService.refresh();

        assertThat(championStatsService.sampleMatches()).isEqualTo(12);
        assertThat(championStatsService.meta(AHRI, Lane.MID).games()).isEqualTo(12);
        assertThat(championStatsService.matchup(Lane.MID, AHRI, ZED).games()).isEqualTo(12);
        assertThat(championStatsService.synergy(Lane.SUP, THRESH, Lane.ADC, JINX).games()).isEqualTo(12);

        Summoner mid = saveSummoner("blue-mid", "GOLD");
        PlayerSnapshot snap = playerStatsService.snapshot(mid, Lane.MID, AHRI, NOW);
        assertThat(snap.recentGames()).isEqualTo(12);
        assertThat(snap.recentWins()).isEqualTo(8);
        assertThat(snap.roleFit()).isEqualTo(RoleFit.MAIN);
        assertThat(snap.sessionGames()).isEqualTo(1);
        assertThat(snap.championGames()).isEqualTo(12);

        PlayerSnapshot offRole = playerStatsService.snapshot(mid, Lane.SUP, null, NOW);
        assertThat(offRole.roleFit()).isEqualTo(RoleFit.OFFROLE);

        List<PlayerSnapshot> blue = new ArrayList<>();
        List<PlayerSnapshot> red = new ArrayList<>();
        blue.add(snap);
        blue.add(playerStatsService.snapshot(saveSummoner("blue-adc", "GOLD"), Lane.ADC, JINX, NOW));
        red.add(playerStatsService.snapshot(saveSummoner("red-mid", "GOLD"), Lane.MID, ZED, NOW));
        red.add(playerStatsService.snapshot(saveSummoner("red-adc", null), Lane.ADC, null, NOW));

        Prediction prediction = model.predict(blue, red, championStatsService);
        Map<Long, ChampionInfo> champs = Map.of(
                AHRI, new ChampionInfo(AHRI, "Ahri", "Ahri", "Ари", "https://example/ahri.png", List.of("Mage")),
                JINX, new ChampionInfo(JINX, "Jinx", "Jinx", "Джинкс", "https://example/jinx.png", List.of("Marksman")),
                ZED, new ChampionInfo(ZED, "Zed", "Zed", "Зед", "https://example/zed.png", List.of("Assassin")));
        MatchAnalysisView view = viewBuilder.build(Region.EUW, prediction, champs, NOW);
        assertThat(view.hasDraft()).isTrue();
        assertThat(view.lanes()).hasSize(2);
        assertThat(view.warnings()).isNotEmpty();

        historyService.save(view, "AI text");
        assertThat(historyService.findRecent()).hasSize(1);

        AnalysisRequest form = AnalysisRequest.blank("EUW");
        form.getTeamA().get(2).setRiotId("blue-mid#EUW");
        form.getTeamA().get(2).setChampionName("Ahri");

        Context result = new Context(Locale.ROOT, Map.of("a", view, "aiCommentary", "AI text", "form", form));
        String resultHtml = templateEngine.process("result", result);
        assertThat(resultHtml).contains("blue-mid#EUW").contains("Мид").contains("До пиков");

        ExtendedModelMap indexModel = new ExtendedModelMap();
        String indexView = webController.renderForm(indexModel, form, List.of("Тестовая ошибка"));
        String indexHtml = templateEngine.process(indexView, new Context(Locale.ROOT, indexModel));
        assertThat(indexHtml).contains("blue-mid#EUW").contains("Тестовая ошибка").contains("teamB[4].riotId");

        ExtendedModelMap historyModel = new ExtendedModelMap();
        String historyView = historyController.history(historyModel);
        String historyHtml = templateEngine.process(historyView, new Context(Locale.ROOT, historyModel));
        assertThat(historyHtml).contains("blue-mid#EUW");
    }

    private Summoner saveSummoner(String puuid, String tier) {
        Summoner s = new Summoner();
        s.setPuuid(puuid);
        s.setGameName(puuid);
        s.setTagLine("EUW");
        s.setPlatform("EUW1");
        s.setTier(tier);
        s.setRank(tier == null ? null : "II");
        s.setLeaguePoints(tier == null ? null : 40);
        s.setSummonerLevel(200L);
        return summonerRepository.save(s);
    }

    private static MatchDTO match(String id, long start, boolean blueWins) {
        MatchDTO dto = new MatchDTO();
        MatchDTO.MetadataDTO meta = new MatchDTO.MetadataDTO();
        meta.setMatchId(id);
        dto.setMetadata(meta);
        MatchDTO.InfoDTO info = new MatchDTO.InfoDTO();
        info.setGameDuration(1800);
        info.setGameStartTimestamp(start);
        info.setQueueId(420);
        info.setGameVersion("15.21.123.4567");
        info.setPlatformId("EUW1");
        info.setParticipants(List.of(
                participant("blue-mid", AHRI, 100, "MIDDLE", blueWins),
                participant("blue-adc", JINX, 100, "BOTTOM", blueWins),
                participant("blue-sup", THRESH, 100, "UTILITY", blueWins),
                participant("blue-jng", LEE, 100, "JUNGLE", blueWins),
                participant("red-mid", ZED, 200, "MIDDLE", !blueWins)));
        dto.setInfo(info);
        return dto;
    }

    private static MatchDTO.ParticipantDTO participant(String puuid, long champ, int team, String position, boolean win) {
        MatchDTO.ParticipantDTO p = new MatchDTO.ParticipantDTO();
        p.setPuuid(puuid);
        p.setChampionId((int) champ);
        p.setTeamId(team);
        p.setTeamPosition(position);
        p.setWin(win);
        p.setKills(5);
        p.setDeaths(3);
        p.setAssists(7);
        p.setGoldEarned(12000);
        return p;
    }
}
