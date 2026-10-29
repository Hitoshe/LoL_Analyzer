package com.lol.analyzer.analysis;

import com.lol.analyzer.model.Lane;
import com.lol.analyzer.model.Summoner;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class WinProbabilityModelTest {

    private final WinProbabilityModel model = new WinProbabilityModel();

    @Test
    void ladderPointsAreContinuousAcrossDivisions() {
        assertThat(WinProbabilityModel.ladderPoints(summoner("GOLD", "IV", 0))).isEqualTo(1200.0);
        assertThat(WinProbabilityModel.ladderPoints(summoner("GOLD", "I", 100))).isEqualTo(1600.0);
        assertThat(WinProbabilityModel.ladderPoints(summoner("PLATINUM", "IV", 0))).isEqualTo(1600.0);
        assertThat(WinProbabilityModel.ladderPoints(summoner("MASTER", "I", 0))).isEqualTo(2800.0);
        assertThat(WinProbabilityModel.ladderPoints(summoner(null, null, 0))).isNull();
    }

    @Test
    void equalTeamsAreNearFiftyPercentWithSmallBlueSideEdge() {
        Prediction p = model.predict(team("GOLD", "II"), team("GOLD", "II"), DraftStats.NONE);
        assertThat(p.preDraftBlue()).isCloseTo(0.51, within(0.01));
        assertThat(p.postDraftBlue()).isNull();
    }

    @Test
    void oneTierHigherTeamIsClearFavorite() {
        Prediction p = model.predict(team("PLATINUM", "II"), team("GOLD", "II"), DraftStats.NONE);
        assertThat(p.preDraftBlue()).isBetween(0.60, 0.70);
    }

    @Test
    void offRolePlayerLowersChance() {
        List<PlayerSnapshot> blue = new ArrayList<>(team("GOLD", "II"));
        blue.set(0, snapshot(summoner("GOLD", "II", 50), Lane.TOP, RoleFit.OFFROLE, 0, null));
        Prediction p = model.predict(blue, team("GOLD", "II"), DraftStats.NONE);
        assertThat(p.preDraftBlue()).isLessThan(0.5);
    }

    @Test
    void lossStreakInSessionLowersChance() {
        List<PlayerSnapshot> blue = new ArrayList<>(team("GOLD", "II"));
        blue.set(1, snapshot(summoner("GOLD", "II", 50), Lane.JNG, RoleFit.MAIN, 3, null));
        Prediction p = model.predict(blue, team("GOLD", "II"), DraftStats.NONE);
        assertThat(p.preDraftBlue()).isLessThan(0.51);
    }

    @Test
    void firstTimeChampionHurtsOnlyAfterDraft() {
        List<PlayerSnapshot> blue = new ArrayList<>(team("GOLD", "II"));
        blue.set(2, snapshot(summoner("GOLD", "II", 50), Lane.MID, RoleFit.MAIN, 0, 103L));
        Prediction p = model.predict(blue, team("GOLD", "II"), DraftStats.NONE);
        assertThat(p.postDraftBlue()).isNotNull();
        assertThat(p.postDraftBlue()).isLessThan(p.preDraftBlue());
    }

    @Test
    void smallMetaSamplesBarelyMoveTheResult() {
        DraftStats tinySample = new DraftStats() {
            public WinRecord meta(long championId, Lane lane) {
                return championId == 1 ? new WinRecord(5, 5) : WinRecord.EMPTY;
            }

            public WinRecord matchup(Lane lane, long championId, long enemyChampionId) {
                return WinRecord.EMPTY;
            }

            public WinRecord synergy(Lane laneA, long championA, Lane laneB, long championB) {
                return WinRecord.EMPTY;
            }
        };
        assertThat(WinProbabilityModel.metaElo(tinySample.meta(1, Lane.MID))).isLessThan(6);
        assertThat(WinProbabilityModel.metaElo(new WinRecord(20_000, 10_600))).isGreaterThan(18);
    }

    @Test
    void unrankedPlayerTakesLobbyAverage() {
        List<PlayerSnapshot> blue = new ArrayList<>(team("DIAMOND", "IV"));
        blue.set(0, snapshot(summoner(null, null, 0), Lane.TOP, RoleFit.MAIN, 0, null));
        Prediction p = model.predict(blue, team("DIAMOND", "IV"), DraftStats.NONE);
        assertThat(p.blue().get(0).rankEstimated()).isTrue();
        assertThat(p.blue().get(0).skillElo()).isGreaterThan(1500);
    }

    private static List<PlayerSnapshot> team(String tier, String division) {
        List<PlayerSnapshot> out = new ArrayList<>();
        for (Lane lane : Lane.values()) {
            out.add(snapshot(summoner(tier, division, 50), lane, RoleFit.MAIN, 0, null));
        }
        return out;
    }

    private static PlayerSnapshot snapshot(Summoner s, Lane lane, RoleFit fit, int lossStreak, Long championId) {
        return new PlayerSnapshot(s, lane, 20, 10, Map.of(), lane, fit, 1.0,
                lossStreak, 0, lossStreak, 10L, lossStreak,
                championId, 0, 0, championId == null ? null : 0, championId == null ? null : 0, null);
    }

    private static Summoner summoner(String tier, String division, int lp) {
        Summoner s = new Summoner();
        s.setPuuid("p-" + tier + division + lp + Math.random());
        s.setGameName("Player");
        s.setTagLine("EUW");
        s.setTier(tier);
        s.setRank(division);
        s.setLeaguePoints(lp);
        return s;
    }
}
