package com.lol.analyzer.analysis;

import com.lol.analyzer.model.Lane;
import com.lol.analyzer.model.Summoner;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Elo-style win probability for two rosters.
 * <ul>
 *   <li>Pre-draft: average ladder skill (tier, division, LP) plus small per-player adjustments for recent form,
 *       playing off-role, losing streak in the current session, and map side.</li>
 *   <li>Post-draft: adds personal champion experience, champion meta win rate, lane counters and duo synergy.</li>
 * </ul>
 * Every win-rate signal is shrunk toward its prior with a pseudo-count, so small samples barely move the result.
 * The constants are hand-set starting values; recalibrate them against resolved outcomes on {@code /history}.
 */
@Component
public class WinProbabilityModel {

    static final double BASE_ELO = 1000;
    static final double ELO_PER_LADDER_POINT = 0.25;
    static final double DEFAULT_LADDER = 1200;
    static final double SIDE_ELO = 7;

    static final double FORM_PSEUDO_GAMES = 12;
    static final double FORM_WEIGHT = 0.2;
    static final int FORM_MIN_RECENT = 5;
    static final double SEASON_PSEUDO_GAMES = 40;
    static final double SEASON_WEIGHT = 0.1;

    static final double SECONDARY_ROLE_ELO = -8;
    static final double OFFROLE_ELO = -20;
    static final double LOSS_STREAK_2_ELO = -5;
    static final double LOSS_STREAK_3_ELO = -10;

    static final double CHAMPION_PSEUDO_GAMES = 10;
    static final double CHAMPION_WEIGHT = 0.25;
    public static final double FIRST_TIME_ELO = -25;
    static final double LOW_EXPERIENCE_ELO = -10;
    static final double MAIN_CHAMPION_ELO = 10;
    static final int FIRST_TIME_MASTERY = 10_000;
    static final int LOW_EXPERIENCE_MASTERY = 30_000;
    static final int MAIN_MASTERY = 100_000;
    static final long MAIN_RECENT_DAYS = 30;

    static final double META_PSEUDO_GAMES = 300;
    static final double MATCHUP_PSEUDO_GAMES = 150;
    static final double SYNERGY_PSEUDO_GAMES = 150;

    private static final List<Lane[]> DUOS = List.of(
            new Lane[]{Lane.ADC, Lane.SUP},
            new Lane[]{Lane.JNG, Lane.MID},
            new Lane[]{Lane.JNG, Lane.TOP},
            new Lane[]{Lane.JNG, Lane.SUP},
            new Lane[]{Lane.MID, Lane.SUP});

    public Prediction predict(List<PlayerSnapshot> blueSeats, List<PlayerSnapshot> redSeats, DraftStats stats) {
        double lobbyLadder = lobbyAverageLadder(blueSeats, redSeats);
        List<SeatResult> blue = blueSeats.stream().map(s -> seat(s, lobbyLadder, stats)).toList();
        List<SeatResult> red = redSeats.stream().map(s -> seat(s, lobbyLadder, stats)).toList();
        boolean hasDraft = blueSeats.stream().anyMatch(PlayerSnapshot::hasChampion)
                || redSeats.stream().anyMatch(PlayerSnapshot::hasChampion);

        List<Prediction.LaneEdge> lanes = laneEdges(blue, red, stats);
        List<Prediction.Synergy> synergies = new ArrayList<>();
        synergies.addAll(synergies(blue, true, stats));
        synergies.addAll(synergies(red, false, stats));

        double counterBlue = lanes.stream().mapToDouble(l -> Math.max(0, l.counterElo())).sum();
        double counterRed = lanes.stream().mapToDouble(l -> Math.max(0, -l.counterElo())).sum();

        List<Prediction.Factor> factors = new ArrayList<>();
        factors.add(new Prediction.Factor("skill", "Уровень игроков (ранг, дивизион, LP)", false,
                mean(blue, SeatResult::skillElo), mean(red, SeatResult::skillElo)));
        factors.add(new Prediction.Factor("form", "Форма (винрейт последних игр)", false,
                sum(blue, SeatResult::formElo), sum(red, SeatResult::formElo)));
        factors.add(new Prediction.Factor("role", "Роли (своя / офф-роль)", false,
                sum(blue, SeatResult::roleElo), sum(red, SeatResult::roleElo)));
        factors.add(new Prediction.Factor("session", "Сессия (серия поражений)", false,
                sum(blue, SeatResult::sessionElo), sum(red, SeatResult::sessionElo)));
        factors.add(new Prediction.Factor("side", "Сторона карты", false, SIDE_ELO, 0));
        if (hasDraft) {
            factors.add(new Prediction.Factor("comfort", "Опыт на чемпионах", true,
                    sum(blue, SeatResult::comfortElo), sum(red, SeatResult::comfortElo)));
            factors.add(new Prediction.Factor("meta", "Мета (винрейт чемпионов на роли)", true,
                    sum(blue, SeatResult::metaElo), sum(red, SeatResult::metaElo)));
            factors.add(new Prediction.Factor("counter", "Контрпики на линиях", true, counterBlue, counterRed));
            factors.add(new Prediction.Factor("synergy", "Связки (дуо)", true,
                    synergies.stream().filter(Prediction.Synergy::blueTeam).mapToDouble(Prediction.Synergy::elo).sum(),
                    synergies.stream().filter(s -> !s.blueTeam()).mapToDouble(Prediction.Synergy::elo).sum()));
        }

        double preDiff = factors.stream().filter(f -> !f.draft()).mapToDouble(f -> f.blue() - f.red()).sum();
        double postDiff = factors.stream().mapToDouble(f -> f.blue() - f.red()).sum();
        return new Prediction(probability(preDiff), hasDraft ? probability(postDiff) : null,
                blue, red, factors, lanes, synergies);
    }

    private SeatResult seat(PlayerSnapshot s, double lobbyLadder, DraftStats stats) {
        Double ladder = ladderPoints(s.summoner());
        double skill = BASE_ELO + (ladder != null ? ladder : lobbyLadder) * ELO_PER_LADDER_POINT;

        double comfort = 0;
        double meta = 0;
        WinRecord metaRecord = WinRecord.EMPTY;
        if (s.hasChampion()) {
            comfort = comfortElo(s);
            if (s.lane() != null) {
                metaRecord = stats.meta(s.championId(), s.lane());
                meta = metaElo(metaRecord);
            }
        }
        return new SeatResult(s, skill, ladder == null, formElo(s), roleElo(s.roleFit()), sessionElo(s),
                comfort, meta, metaRecord);
    }

    static double formElo(PlayerSnapshot s) {
        if (s.recentGames() >= FORM_MIN_RECENT) {
            return FORM_WEIGHT * eloFromWinRate(shrink(s.recentWins(), s.recentGames(), 0.5, FORM_PSEUDO_GAMES));
        }
        Summoner sum = s.summoner();
        if (sum.getRankedWins() != null && sum.getRankedLosses() != null) {
            int games = sum.getRankedWins() + sum.getRankedLosses();
            return SEASON_WEIGHT * eloFromWinRate(shrink(sum.getRankedWins(), games, 0.5, SEASON_PSEUDO_GAMES));
        }
        return 0;
    }

    static double roleElo(RoleFit fit) {
        return switch (fit) {
            case SECONDARY -> SECONDARY_ROLE_ELO;
            case OFFROLE -> OFFROLE_ELO;
            default -> 0;
        };
    }

    static double sessionElo(PlayerSnapshot s) {
        if (s.sessionLossStreak() >= 3) {
            return LOSS_STREAK_3_ELO;
        }
        return s.sessionLossStreak() == 2 ? LOSS_STREAK_2_ELO : 0;
    }

    static double comfortElo(PlayerSnapshot s) {
        double personal = CHAMPION_WEIGHT * eloFromWinRate(
                shrink(s.championWins(), s.championGames(), 0.5, CHAMPION_PSEUDO_GAMES));
        return personal + experienceElo(s);
    }

    public static double experienceElo(PlayerSnapshot s) {
        int points = s.masteryPoints() == null ? 0 : s.masteryPoints();
        if (s.championGames() == 0 && points < FIRST_TIME_MASTERY) {
            return FIRST_TIME_ELO;
        }
        if (points < LOW_EXPERIENCE_MASTERY && s.championGames() < 3) {
            return LOW_EXPERIENCE_ELO;
        }
        boolean recent = s.daysSinceChampionPlayed() == null || s.daysSinceChampionPlayed() <= MAIN_RECENT_DAYS;
        return points >= MAIN_MASTERY && recent ? MAIN_CHAMPION_ELO : 0;
    }

    static double metaElo(WinRecord r) {
        return eloFromWinRate(shrink(r.wins(), r.games(), 0.5, META_PSEUDO_GAMES));
    }

    private static List<Prediction.LaneEdge> laneEdges(List<SeatResult> blue, List<SeatResult> red, DraftStats stats) {
        Map<Lane, SeatResult> redByLane = new EnumMap<>(Lane.class);
        red.forEach(r -> redByLane.put(r.snapshot().lane(), r));
        List<Prediction.LaneEdge> out = new ArrayList<>();
        for (SeatResult b : blue) {
            Lane lane = b.snapshot().lane();
            SeatResult r = redByLane.get(lane);
            if (lane == null || r == null) {
                continue;
            }
            double counter = 0;
            WinRecord record = WinRecord.EMPTY;
            if (b.snapshot().hasChampion() && r.snapshot().hasChampion()) {
                record = stats.matchup(lane, b.snapshot().championId(), r.snapshot().championId());
                double priorElo = b.metaElo() - r.metaElo();
                double shrunk = shrink(record.wins(), record.games(), probability(priorElo), MATCHUP_PSEUDO_GAMES);
                counter = eloFromWinRate(shrunk) - priorElo;
            }
            out.add(new Prediction.LaneEdge(lane, b, r, counter, record, b.strength() - r.strength() + counter));
        }
        out.sort(Comparator.comparing(Prediction.LaneEdge::lane));
        return out;
    }

    private static List<Prediction.Synergy> synergies(List<SeatResult> team, boolean blueTeam, DraftStats stats) {
        Map<Lane, SeatResult> byLane = new EnumMap<>(Lane.class);
        team.forEach(s -> {
            if (s.snapshot().lane() != null && s.snapshot().hasChampion()) {
                byLane.put(s.snapshot().lane(), s);
            }
        });
        List<Prediction.Synergy> out = new ArrayList<>();
        for (Lane[] duo : DUOS) {
            SeatResult a = byLane.get(duo[0]);
            SeatResult b = byLane.get(duo[1]);
            if (a == null || b == null) {
                continue;
            }
            WinRecord record = stats.synergy(duo[0], a.snapshot().championId(), duo[1], b.snapshot().championId());
            double priorElo = a.metaElo() + b.metaElo();
            double shrunk = shrink(record.wins(), record.games(), probability(priorElo), SYNERGY_PSEUDO_GAMES);
            out.add(new Prediction.Synergy(blueTeam, duo[0], duo[1], eloFromWinRate(shrunk) - priorElo, record));
        }
        return out;
    }

    private static double lobbyAverageLadder(List<PlayerSnapshot> blue, List<PlayerSnapshot> red) {
        List<Double> known = new ArrayList<>();
        for (PlayerSnapshot s : blue) {
            Optional.ofNullable(ladderPoints(s.summoner())).ifPresent(known::add);
        }
        for (PlayerSnapshot s : red) {
            Optional.ofNullable(ladderPoints(s.summoner())).ifPresent(known::add);
        }
        return known.isEmpty() ? DEFAULT_LADDER : known.stream().mapToDouble(Double::doubleValue).average().orElse(DEFAULT_LADDER);
    }

    /**
     * Continuous solo-queue ladder: 400 points per tier, 100 per division, plus LP (Iron IV 0 LP = 0, Master 0 LP = 2800).
     * Apex LP is compressed because Master+ LP gains are much cheaper per point of skill.
     */
    public static Double ladderPoints(Summoner s) {
        if (s == null || s.getTier() == null) {
            return null;
        }
        int lp = s.getLeaguePoints() == null ? 0 : s.getLeaguePoints();
        String tier = s.getTier().toUpperCase(Locale.ROOT);
        Integer tierIndex = switch (tier) {
            case "IRON" -> 0;
            case "BRONZE" -> 1;
            case "SILVER" -> 2;
            case "GOLD" -> 3;
            case "PLATINUM" -> 4;
            case "EMERALD" -> 5;
            case "DIAMOND" -> 6;
            default -> null;
        };
        if (tierIndex == null) {
            return switch (tier) {
                case "MASTER", "GRANDMASTER", "CHALLENGER" -> 2800 + Math.min(lp, 1500) * 0.6;
                default -> null;
            };
        }
        int division = switch (s.getRank() == null ? "" : s.getRank()) {
            case "III" -> 1;
            case "II" -> 2;
            case "I" -> 3;
            default -> 0;
        };
        return tierIndex * 400.0 + division * 100.0 + Math.min(lp, 100);
    }

    public static double probability(double eloDiff) {
        return 1.0 / (1.0 + Math.pow(10, -eloDiff / 400.0));
    }

    public static double eloFromWinRate(double p) {
        double clamped = Math.min(0.99, Math.max(0.01, p));
        return 400.0 * Math.log10(clamped / (1 - clamped));
    }

    static double shrink(int wins, int games, double prior, double pseudoGames) {
        return (wins + prior * pseudoGames) / (games + pseudoGames);
    }

    private static double mean(List<SeatResult> seats, java.util.function.ToDoubleFunction<SeatResult> f) {
        return seats.stream().mapToDouble(f).average().orElse(BASE_ELO);
    }

    private static double sum(List<SeatResult> seats, java.util.function.ToDoubleFunction<SeatResult> f) {
        return seats.stream().mapToDouble(f).sum();
    }
}
