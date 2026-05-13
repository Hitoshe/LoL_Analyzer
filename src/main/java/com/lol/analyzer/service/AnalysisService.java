package com.lol.analyzer.service;

import com.lol.analyzer.model.*;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Builds a 5v5 (or partial roster) analysis: resolves each {@link Summoner} via {@link SummonerService},
 * computes a per-player {@link TeamMember#getWinScore()}, derives team win percentages, and materializes {@link LaneMatchup} rows.
 */
@Service
public class AnalysisService {

    private final SummonerService summonerService;

    public AnalysisService(SummonerService summonerService) {
        this.summonerService = summonerService;
    }

    public Map<String, Object> analyzeMatch(AnalysisRequest request) {
        List<TeamMember> teamA = processTeam(request.getTeamA());
        List<TeamMember> teamB = processTeam(request.getTeamB());

        if (teamA.isEmpty() || teamB.isEmpty()) {
            return Map.of("error", "Введите игроков");
        }

        Map<String, Object> result = new HashMap<>();
        List<LaneMatchup> matchups = new ArrayList<>();

        int size = Math.min(teamA.size(), teamB.size());
        for (int i = 0; i < size; i++) {
            TeamMember pA = teamA.get(i);
            TeamMember pB = teamB.get(i);

            String prediction = (pA.getWinScore() > pB.getWinScore()) ? "Синие сильнее" : "Красные сильнее";
            String difficulty = Math.abs(pA.getWinScore() - pB.getWinScore()) > 20 ? "HARD" : "MEDIUM";
            matchups.add(new LaneMatchup(pA.getLane(), pA.getSummoner(), pB.getSummoner(), pA.getSelectedChampion(), pB.getSelectedChampion(), prediction, difficulty));
        }

        result.put("matchups", matchups);
        double totalA = teamA.stream().mapToDouble(TeamMember::getWinScore).sum();
        double totalB = teamB.stream().mapToDouble(TeamMember::getWinScore).sum();

        result.put("teamA_chance", Math.round((totalA / (totalA + totalB)) * 1000.0) / 10.0);
        result.put("teamB_chance", Math.round((totalB / (totalA + totalB)) * 1000.0) / 10.0);
        result.put("playersA", teamA);
        result.put("playersB", teamB);

        return result;
    }

    private List<TeamMember> processTeam(List<AnalysisRequest.PlayerInput> inputs) {
        List<TeamMember> team = new ArrayList<>();
        if (inputs == null) {
            return team;
        }
        for (AnalysisRequest.PlayerInput input : inputs) {
            if (input.getName() == null || input.getName().isBlank()) {
                continue;
            }
            Summoner s = summonerService.getAccount(input.getName(), input.getTag());
            double score = calculateWinScore(s, input.getChampionName(), input.getLane());
            team.add(new TeamMember(s, input.getChampionName(), input.getLane(), score));
        }
        return team;
    }

    /**
     * Heuristic WinScore: weighted rank, account level, recent KDA, role-normalized GPM, plus optional main-champion synergy.
     */
    private double calculateWinScore(Summoner s, String selectedChamp, String lane) {
        double rankScore = getRankWeight(s.getTier());

        double levelScore = Math.min(100.0, s.getSummonerLevel() / 5.0);

        double kdaScore = Math.min(100.0, (s.getAvgKda() != null ? s.getAvgKda() : 2.5) * 20.0);

        double rawGpm = (s.getAvgGpm() != null ? s.getAvgGpm() : 300.0);
        double gpmScore = calculateGpmScore(rawGpm, lane);

        double totalScore = (rankScore * 0.4) + (levelScore * 0.15) + (kdaScore * 0.25) + (gpmScore * 0.2);

        if (selectedChamp != null && s.getTopChampionName() != null
                && selectedChamp.equalsIgnoreCase(s.getTopChampionName())) {
            totalScore *= 1.15;
        }

        return totalScore;
    }

    /**
     * Maps raw GPM to a 0–110 score using role-specific benchmarks (supports carry/jungle/support economy differences).
     */
    private double calculateGpmScore(double gpm, String lane) {
        double benchmark = switch (lane.toUpperCase()) {
            case "MID", "ADC" -> 450.0;
            case "TOP" -> 420.0;
            case "JNG" -> 380.0;
            case "SUP" -> 260.0;
            default -> 400.0;
        };
        return Math.min(110.0, (gpm / benchmark) * 100.0);
    }

    private double getRankWeight(String tier) {
        if (tier == null) {
            return 5;
        }
        return switch (tier.toUpperCase()) {
            case "IRON" -> 10;
            case "BRONZE" -> 20;
            case "SILVER" -> 30;
            case "GOLD" -> 40;
            case "PLATINUM" -> 55;
            case "EMERALD" -> 65;
            case "DIAMOND" -> 80;
            case "MASTER" -> 95;
            case "GRANDMASTER" -> 115;
            case "CHALLENGER" -> 130;
            default -> 5;
        };
    }
}
