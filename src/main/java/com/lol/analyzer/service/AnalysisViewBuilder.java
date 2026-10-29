package com.lol.analyzer.service;

import com.lol.analyzer.analysis.*;
import com.lol.analyzer.analysis.view.MatchAnalysisView;
import com.lol.analyzer.analysis.view.PlayerCard;
import com.lol.analyzer.model.ChampionInfo;
import com.lol.analyzer.model.Lane;
import com.lol.analyzer.model.Summoner;
import com.lol.analyzer.riot.Region;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Formats a {@link Prediction} into Russian texts for {@code result.html}, Gemini and history.
 */
@Component
public class AnalysisViewBuilder {

    private static final int MIN_RECENT_FOR_CONFIDENCE = 5;
    private static final long RELIABLE_META_SAMPLE = 20_000;

    private final ChampionStatsService championStatsService;

    public AnalysisViewBuilder(ChampionStatsService championStatsService) {
        this.championStatsService = championStatsService;
    }

    public MatchAnalysisView build(Region region, Prediction p, Map<Long, ChampionInfo> champions, long nowMs) {
        List<MatchAnalysisView.FactorRow> factors = p.factors().stream()
                .map(f -> new MatchAnalysisView.FactorRow(f.label(), f.draft(),
                        formatElo(f.key(), f.blue()), formatElo(f.key(), f.red()), round1(f.impactPercent())))
                .toList();

        Map<Lane, SeatResult> blueByLane = byLane(p.blue());
        Map<Lane, SeatResult> redByLane = byLane(p.red());
        Map<Lane, Prediction.LaneEdge> edges = new EnumMap<>(Lane.class);
        p.lanes().forEach(e -> edges.put(e.lane(), e));

        List<MatchAnalysisView.LaneRow> lanes = new ArrayList<>();
        for (Lane lane : Lane.values()) {
            SeatResult b = blueByLane.get(lane);
            SeatResult r = redByLane.get(lane);
            if (b == null && r == null) {
                continue;
            }
            Prediction.LaneEdge edge = edges.get(lane);
            lanes.add(new MatchAnalysisView.LaneRow(lane.getLabel(),
                    b == null ? null : card(b, region, champions, nowMs),
                    r == null ? null : card(r, region, champions, nowMs),
                    edge == null ? null : round1(edge.blueLaneChance() * 100),
                    edge == null ? null : counterNote(edge, champions)));
        }

        List<SeatResult> all = new ArrayList<>(p.blue());
        all.addAll(p.red());
        long withData = all.stream()
                .filter(s -> !s.rankEstimated() && s.snapshot().recentGames() >= MIN_RECENT_FOR_CONFIDENCE)
                .count();
        double coverage = all.isEmpty() ? 0 : withData / (double) all.size();
        String confidence = coverage >= 0.8 ? "Высокая" : coverage >= 0.5 ? "Средняя" : "Низкая";
        String confidenceCss = coverage >= 0.8 ? "good" : coverage >= 0.5 ? "neutral" : "bad";

        return new MatchAnalysisView(region,
                round1(p.preDraftBlue() * 100),
                p.postDraftBlue() == null ? null : round1(p.postDraftBlue() * 100),
                factors, lanes, synergyNotes(p, champions), confidence, confidenceCss,
                warnings(p, all, withData), p);
    }

    private List<String> warnings(Prediction p, List<SeatResult> all, long withData) {
        List<String> out = new ArrayList<>();
        long unranked = all.stream().filter(SeatResult::rankEstimated).count();
        if (unranked > 0) {
            out.add("Без ранга в соло-очереди: " + unranked + " игрок(ов) — их уровень принят равным среднему по лобби.");
        }
        long fewGames = all.size() - withData - unranked;
        if (fewGames > 0) {
            out.add("Мало сохранённых ранкед-игр у " + fewGames + " игрок(ов) — форма и роль оценены грубо. "
                    + "История накапливается с каждым анализом.");
        }
        if (p.postDraftBlue() != null && championStatsService.sampleMatches() < RELIABLE_META_SAMPLE) {
            out.add("Мета, контрпики и связки считаются по " + championStatsService.sampleMatches()
                    + " ранкед-матчам в базе за " + championStatsService.windowDays()
                    + " дн. Для надёжных цифр нужны десятки тысяч, поэтому сейчас эти факторы почти не влияют.");
        }
        return out;
    }

    private PlayerCard card(SeatResult seat, Region region, Map<Long, ChampionInfo> champions, long nowMs) {
        PlayerSnapshot s = seat.snapshot();
        Summoner sum = s.summoner();
        ChampionInfo champ = s.championId() == null ? null : champions.get(s.championId());
        boolean mainChamp = champ != null && sum.getTopChampionId() != null && sum.getTopChampionId() == champ.id();

        List<String> notes = new ArrayList<>();
        if (seat.rankEstimated()) {
            notes.add("Нет ранга — взят средний уровень лобби");
        }
        if (s.hasChampion()) {
            double exp = WinProbabilityModel.experienceElo(s);
            if (exp <= WinProbabilityModel.FIRST_TIME_ELO) {
                notes.add("Почти не играл на этом чемпионе");
            } else if (exp < 0) {
                notes.add("Мало опыта на этом чемпионе");
            } else if (exp > 0) {
                notes.add("Мейн-чемпион");
            }
        }
        if (s.sessionLossStreak() >= 2) {
            notes.add("Серия поражений: " + s.sessionLossStreak() + " подряд");
        }

        String strength = "Сила: " + Math.round(seat.strength());
        if (s.hasChampion()) {
            strength += " (до пиков " + Math.round(seat.preDraftStrength()) + ")";
        }

        return new PlayerCard(
                sum.riotIdDisplay(),
                profileUrl(region, sum),
                s.lane() == null ? "?" : s.lane().getLabel(),
                champ == null ? null : champ.name(),
                champ == null ? null : champ.iconUrl(),
                mainChamp,
                rankLabel(sum),
                seasonLabel(sum),
                sum.getSummonerLevel(),
                recentLabel(s),
                sum.getAvgKda() == null || sum.getAvgGpm() == null ? null
                        : "KDA " + sum.getAvgKda() + " · GPM " + Math.round(sum.getAvgGpm()),
                s.roleFit().getLabel(),
                s.roleFit().getCss(),
                roleHabit(s),
                sessionLabel(s),
                s.sessionLossStreak() >= 2 ? "bad" : s.inSession() ? "neutral" : "muted",
                championLabel(s, nowMs),
                strength,
                notes);
    }

    static String rankLabel(Summoner s) {
        if (s.getTier() == null) {
            return "Без ранга";
        }
        String tier = s.getTier().charAt(0) + s.getTier().substring(1).toLowerCase(Locale.ROOT);
        boolean apex = switch (s.getTier()) {
            case "MASTER", "GRANDMASTER", "CHALLENGER" -> true;
            default -> false;
        };
        int lp = s.getLeaguePoints() == null ? 0 : s.getLeaguePoints();
        return apex ? tier + " " + lp + " LP" : tier + " " + s.getRank() + " · " + lp + " LP";
    }

    private static String seasonLabel(Summoner s) {
        if (s.getRankedWins() == null || s.getRankedLosses() == null) {
            return null;
        }
        int games = s.getRankedWins() + s.getRankedLosses();
        return "Сезон: " + s.getRankedWins() + "W " + s.getRankedLosses() + "L (" + pct(s.getRankedWins(), games) + ")";
    }

    private static String recentLabel(PlayerSnapshot s) {
        if (s.recentGames() == 0) {
            return "Нет сохранённых ранкед-игр";
        }
        return "Последние " + s.recentGames() + ": " + s.recentWins() + "W " + (s.recentGames() - s.recentWins())
                + "L (" + pct(s.recentWins(), s.recentGames()) + ")";
    }

    private static String roleHabit(PlayerSnapshot s) {
        if (s.mainLane() == null) {
            return null;
        }
        int total = s.laneCounts().values().stream().mapToInt(Integer::intValue).sum();
        return "Обычно: " + s.mainLane().getLabel() + " (" + pct(s.laneCounts().get(s.mainLane()), total) + " игр)";
    }

    private static String sessionLabel(PlayerSnapshot s) {
        if (s.minutesSinceLastGame() == null) {
            return "Нет данных о последних играх";
        }
        if (s.inSession()) {
            return "В сессии: " + s.sessionGames() + " игр (" + s.sessionWins() + "W "
                    + (s.sessionGames() - s.sessionWins()) + "L), последняя " + ago(s.minutesSinceLastGame()) + " назад";
        }
        return "Первая игра сессии · последняя ранкед-игра " + ago(s.minutesSinceLastGame()) + " назад"
                + (s.gamesLast24h() > 0 ? " · за 24 ч: " + s.gamesLast24h() : "");
    }

    private static String championLabel(PlayerSnapshot s, long nowMs) {
        if (!s.hasChampion()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (s.championGames() > 0) {
            sb.append("На чемпионе: ").append(s.championGames()).append(" игр, WR ")
                    .append(pct(s.championWins(), s.championGames()));
        } else {
            sb.append("Нет ранкед-игр на чемпионе в базе");
        }
        int points = s.masteryPoints() == null ? 0 : s.masteryPoints();
        sb.append(" · мастерство ").append(points >= 1000 ? (points / 1000) + "k" : String.valueOf(points));
        if (s.masteryLevel() != null && s.masteryLevel() > 0) {
            sb.append(" (ур. ").append(s.masteryLevel()).append(")");
        }
        if (s.daysSinceChampionPlayed() != null) {
            sb.append(" · играл ").append(s.daysSinceChampionPlayed() == 0 ? "сегодня" : s.daysSinceChampionPlayed() + " дн. назад");
        }
        return sb.toString();
    }

    private static String counterNote(Prediction.LaneEdge edge, Map<Long, ChampionInfo> champions) {
        if (!edge.blue().snapshot().hasChampion() || !edge.red().snapshot().hasChampion()) {
            return null;
        }
        WinRecord r = edge.counterRecord();
        if (r.games() == 0) {
            return "Нет статистики по этой паре чемпионов";
        }
        String blueChamp = Optional.ofNullable(champions.get(edge.blue().snapshot().championId())).map(ChampionInfo::name).orElse("синие");
        return "Пара в базе: " + r.games() + " игр, " + blueChamp + " выигрывает " + pct(r.wins(), r.games())
                + " · поправка " + String.format(Locale.ROOT, "%+.0f", edge.counterElo()) + " Elo";
    }

    private static List<String> synergyNotes(Prediction p, Map<Long, ChampionInfo> champions) {
        List<String> out = new ArrayList<>();
        for (Prediction.Synergy s : p.synergies()) {
            if (s.record().games() == 0) {
                continue;
            }
            out.add((s.blueTeam() ? "Синие " : "Красные ") + s.laneA().getLabel() + " + " + s.laneB().getLabel()
                    + ": " + s.record().games() + " игр вместе, WR " + pct(s.record().wins(), s.record().games())
                    + " · поправка " + String.format(Locale.ROOT, "%+.0f", s.elo()) + " Elo");
        }
        return out;
    }

    private static String profileUrl(Region region, Summoner s) {
        String slug = URLEncoder.encode(s.getGameName() + "-" + s.getTagLine(), StandardCharsets.UTF_8).replace("+", "%20");
        return "https://op.gg/lol/summoners/" + region.getOpggSlug() + "/" + slug;
    }

    private static Map<Lane, SeatResult> byLane(List<SeatResult> seats) {
        Map<Lane, SeatResult> out = new EnumMap<>(Lane.class);
        seats.forEach(s -> {
            if (s.snapshot().lane() != null) {
                out.put(s.snapshot().lane(), s);
            }
        });
        return out;
    }

    private static String formatElo(String key, double value) {
        return "skill".equals(key) ? String.valueOf(Math.round(value)) : String.format(Locale.ROOT, "%+.0f", value);
    }

    static String ago(long minutes) {
        if (minutes < 60) {
            return minutes + " мин";
        }
        if (minutes < 48 * 60) {
            return (minutes / 60) + " ч";
        }
        return (minutes / 1440) + " дн.";
    }

    private static String pct(Integer part, int total) {
        if (part == null || total <= 0) {
            return "—";
        }
        return Math.round(part * 100.0 / total) + "%";
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
