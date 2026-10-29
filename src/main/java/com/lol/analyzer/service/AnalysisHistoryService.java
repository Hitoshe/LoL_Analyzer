package com.lol.analyzer.service;

import com.lol.analyzer.analysis.Prediction;
import com.lol.analyzer.analysis.SeatResult;
import com.lol.analyzer.analysis.view.MatchAnalysisView;
import com.lol.analyzer.model.AnalysisHistory;
import com.lol.analyzer.model.AnalysisLine;
import com.lol.analyzer.model.Summoner;
import com.lol.analyzer.repository.AnalysisHistoryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Stores successful UI analyses for the {@code /history} page and summarizes how well resolved predictions matched reality.
 * {@link #findRecent()} touches each {@code lines} collection while the read transaction is open so Thymeleaf can render lazies safely.
 */
@Service
public class AnalysisHistoryService {

    private final AnalysisHistoryRepository analysisHistoryRepository;
    private final DataDragonService dataDragonService;

    public AnalysisHistoryService(AnalysisHistoryRepository analysisHistoryRepository, DataDragonService dataDragonService) {
        this.analysisHistoryRepository = analysisHistoryRepository;
        this.dataDragonService = dataDragonService;
    }

    @Transactional(readOnly = true)
    public List<AnalysisHistory> findRecent() {
        List<AnalysisHistory> list = analysisHistoryRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
        for (AnalysisHistory h : list) {
            h.getLines().size();
        }
        return list;
    }

    @Transactional
    public void save(MatchAnalysisView view, String aiAdvice) {
        Prediction p = view.prediction();
        AnalysisHistory history = new AnalysisHistory();
        history.setCreatedAt(LocalDateTime.now());
        history.setRegion(view.region().name());
        history.setTeamAChance(view.finalBlue());
        history.setTeamBChance(Math.round((100 - view.finalBlue()) * 10.0) / 10.0);
        history.setPreDraftChanceA(view.preDraftBlue());
        history.setHadDraft(view.hasDraft());
        history.setBluePuuids(puuids(p.blue()));
        history.setRedPuuids(puuids(p.red()));
        history.setAiAdvice(aiAdvice != null ? aiAdvice : "");

        for (Prediction.LaneEdge edge : p.lanes()) {
            AnalysisLine line = new AnalysisLine();
            line.setHistory(history);
            line.setLane(edge.lane().name());
            double chance = edge.blueLaneChance() * 100;
            line.setPrediction(chance >= 50
                    ? String.format(Locale.ROOT, "Синие %.0f%%", chance)
                    : String.format(Locale.ROOT, "Красные %.0f%%", 100 - chance));
            line.setDifficulty(Math.abs(chance - 50) > 10 ? "HARD" : "MEDIUM");
            fillSide(line, edge.blue(), true);
            fillSide(line, edge.red(), false);
            history.getLines().add(line);
        }
        analysisHistoryRepository.save(history);
    }

    private void fillSide(AnalysisLine line, SeatResult seat, boolean blue) {
        Summoner s = seat.snapshot().summoner();
        Long championId = seat.snapshot().championId();
        String champion = championId == null ? null : dataDragonService.getChampionName(championId);
        if (blue) {
            line.setAGameName(s.getGameName());
            line.setATagLine(s.getTagLine());
            line.setAChampion(champion);
            line.setATier(s.getTier());
            line.setASummonerLevel(s.getSummonerLevel());
            line.setAAvgKda(s.getAvgKda());
            line.setAAvgGpm(s.getAvgGpm());
            line.setAWinScore(seat.strength());
        } else {
            line.setBGameName(s.getGameName());
            line.setBTagLine(s.getTagLine());
            line.setBChampion(champion);
            line.setBTier(s.getTier());
            line.setBSummonerLevel(s.getSummonerLevel());
            line.setBAvgKda(s.getAvgKda());
            line.setBAvgGpm(s.getAvgGpm());
            line.setBWinScore(seat.strength());
        }
    }

    private static String puuids(List<SeatResult> seats) {
        return seats.stream().map(s -> s.snapshot().summoner().getPuuid()).collect(Collectors.joining(","));
    }

    public record CalibrationBucket(String label, int games, double avgPredicted, double actualRate) {
    }

    /**
     * @param brier mean squared error of blue win probability (0.25 = coin flip, lower is better)
     */
    public record Calibration(int resolved, int correct, double brier, List<CalibrationBucket> buckets) {
        public double accuracy() {
            return resolved == 0 ? 0 : correct * 100.0 / resolved;
        }
    }

    @Transactional(readOnly = true)
    public Calibration calibration() {
        List<AnalysisHistory> resolved = analysisHistoryRepository.findByActualBlueWinIsNotNull();
        int correct = 0;
        double brier = 0;
        double[][] ranges = {{50, 55}, {55, 60}, {60, 70}, {70, 101}};
        int[] games = new int[ranges.length];
        double[] predictedSum = new double[ranges.length];
        int[] hits = new int[ranges.length];
        for (AnalysisHistory h : resolved) {
            double blue = h.getTeamAChance();
            boolean blueWon = h.getActualBlueWin();
            if ((blue >= 50) == blueWon) {
                correct++;
            }
            brier += Math.pow(blue / 100.0 - (blueWon ? 1 : 0), 2);
            double favorite = Math.max(blue, 100 - blue);
            boolean favoriteWon = (blue >= 50) == blueWon;
            for (int i = 0; i < ranges.length; i++) {
                if (favorite >= ranges[i][0] && favorite < ranges[i][1]) {
                    games[i]++;
                    predictedSum[i] += favorite;
                    hits[i] += favoriteWon ? 1 : 0;
                }
            }
        }
        List<CalibrationBucket> buckets = new ArrayList<>();
        for (int i = 0; i < ranges.length; i++) {
            String label = ranges[i][1] > 100 ? "70%+" : (int) ranges[i][0] + "–" + (int) ranges[i][1] + "%";
            buckets.add(new CalibrationBucket(label, games[i],
                    games[i] == 0 ? 0 : predictedSum[i] / games[i],
                    games[i] == 0 ? 0 : hits[i] * 100.0 / games[i]));
        }
        return new Calibration(resolved.size(), correct, resolved.isEmpty() ? 0 : brier / resolved.size(), buckets);
    }
}
