package com.lol.analyzer.analysis;

import com.lol.analyzer.model.Lane;

import java.util.*;

/**
 * Guesses lanes for one team of a live game (Spectator does not report positions).
 * Each player gets a likelihood per lane from the champion's observed lane split (or its class tags when there is
 * no data) multiplied by summoner-spell hints; the best one-lane-per-player assignment is found by brute force.
 */
public final class RoleAssigner {

    public static final long SPELL_CLEANSE = 1;
    public static final long SPELL_EXHAUST = 3;
    public static final long SPELL_GHOST = 6;
    public static final long SPELL_HEAL = 7;
    public static final long SPELL_SMITE = 11;
    public static final long SPELL_TELEPORT = 12;
    public static final long SPELL_IGNITE = 14;
    public static final long SPELL_BARRIER = 21;

    private static final int MIN_LANE_SAMPLE = 10;

    public record Candidate(long spell1, long spell2, List<String> tags, Map<Lane, WinRecord> laneStats) {
    }

    private RoleAssigner() {
    }

    /** Returns one lane per candidate, in input order. At most five candidates. */
    public static List<Lane> assign(List<Candidate> players) {
        int n = Math.min(players.size(), Lane.values().length);
        double[][] score = new double[n][Lane.values().length];
        for (int i = 0; i < n; i++) {
            for (Lane lane : Lane.values()) {
                score[i][lane.ordinal()] = Math.log(likelihood(players.get(i), lane));
            }
        }
        Lane[] best = new Lane[n];
        search(score, 0, new Lane[n], EnumSet.noneOf(Lane.class), 0, new double[]{Double.NEGATIVE_INFINITY}, best);
        return Arrays.asList(best);
    }

    private static void search(double[][] score, int i, Lane[] current, Set<Lane> used, double total,
                               double[] bestTotal, Lane[] best) {
        if (i == current.length) {
            if (total > bestTotal[0]) {
                bestTotal[0] = total;
                System.arraycopy(current, 0, best, 0, current.length);
            }
            return;
        }
        for (Lane lane : Lane.values()) {
            if (used.add(lane)) {
                current[i] = lane;
                search(score, i + 1, current, used, total + score[i][lane.ordinal()], bestTotal, best);
                used.remove(lane);
            }
        }
    }

    static double likelihood(Candidate c, Lane lane) {
        return championPrior(c, lane) * spellFactor(c.spell1(), lane) * spellFactor(c.spell2(), lane);
    }

    private static double championPrior(Candidate c, Lane lane) {
        Map<Lane, WinRecord> stats = c.laneStats() == null ? Map.of() : c.laneStats();
        int total = stats.values().stream().mapToInt(WinRecord::games).sum();
        if (total >= MIN_LANE_SAMPLE) {
            return (stats.getOrDefault(lane, WinRecord.EMPTY).games() + 1.0) / (total + Lane.values().length);
        }
        double weight = 1.0;
        for (String tag : c.tags() == null ? List.<String>of() : c.tags()) {
            weight *= switch (tag) {
                case "Marksman" -> lane == Lane.ADC ? 4.0 : 0.6;
                case "Support" -> lane == Lane.SUP ? 3.0 : 0.8;
                case "Mage" -> lane == Lane.MID ? 2.0 : lane == Lane.SUP ? 1.2 : 0.9;
                case "Assassin" -> lane == Lane.MID ? 2.0 : lane == Lane.JNG ? 1.3 : 0.9;
                case "Fighter" -> lane == Lane.TOP ? 2.0 : lane == Lane.JNG ? 1.5 : 0.8;
                case "Tank" -> lane == Lane.TOP || lane == Lane.SUP ? 1.5 : lane == Lane.JNG ? 1.2 : 0.8;
                default -> 1.0;
            };
        }
        return weight;
    }

    private static double spellFactor(long spell, Lane lane) {
        if (spell == SPELL_SMITE) {
            return lane == Lane.JNG ? 50.0 : 0.02;
        }
        if (spell == SPELL_HEAL) {
            return lane == Lane.ADC ? 4.0 : lane == Lane.SUP ? 1.5 : 0.6;
        }
        if (spell == SPELL_TELEPORT) {
            return lane == Lane.TOP ? 3.0 : lane == Lane.MID ? 1.5 : 0.5;
        }
        if (spell == SPELL_EXHAUST) {
            return lane == Lane.SUP ? 3.0 : 0.8;
        }
        if (spell == SPELL_IGNITE) {
            return lane == Lane.SUP ? 1.5 : lane == Lane.MID ? 1.3 : lane == Lane.TOP ? 1.2 : 0.9;
        }
        if (spell == SPELL_BARRIER) {
            return lane == Lane.MID ? 2.0 : 0.9;
        }
        if (spell == SPELL_CLEANSE) {
            return lane == Lane.ADC ? 2.0 : 0.9;
        }
        if (spell == SPELL_GHOST) {
            return lane == Lane.TOP ? 1.5 : lane == Lane.ADC ? 1.2 : 0.9;
        }
        return 1.0;
    }
}
