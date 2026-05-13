package com.lol.analyzer.service;

import com.lol.analyzer.model.AnalysisHistory;
import com.lol.analyzer.model.AnalysisLine;
import com.lol.analyzer.model.LaneMatchup;
import com.lol.analyzer.model.TeamMember;
import com.lol.analyzer.repository.AnalysisHistoryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Stores successful UI analyses for the {@code /history} page. The {@link #findRecent()} method
 * touches each {@code lines} collection while the read transaction is open so Thymeleaf can render lazies safely.
 */
@Service
public class AnalysisHistoryService {

    private final AnalysisHistoryRepository analysisHistoryRepository;

    public AnalysisHistoryService(AnalysisHistoryRepository analysisHistoryRepository) {
        this.analysisHistoryRepository = analysisHistoryRepository;
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
    @SuppressWarnings("unchecked")
    public void saveSuccessfulAnalysis(Map<String, Object> data, String aiAdvice) {
        List<LaneMatchup> matchups = (List<LaneMatchup>) data.get("matchups");
        List<TeamMember> playersA = (List<TeamMember>) data.get("playersA");
        List<TeamMember> playersB = (List<TeamMember>) data.get("playersB");
        if (matchups == null || playersA == null || playersB == null) {
            return;
        }
        int n = Math.min(matchups.size(), Math.min(playersA.size(), playersB.size()));
        if (n == 0) {
            return;
        }

        AnalysisHistory history = new AnalysisHistory();
        history.setCreatedAt(LocalDateTime.now());
        history.setTeamAChance((Double) data.get("teamA_chance"));
        history.setTeamBChance((Double) data.get("teamB_chance"));
        history.setAiAdvice(aiAdvice != null ? aiAdvice : "");

        for (int i = 0; i < n; i++) {
            LaneMatchup m = matchups.get(i);
            TeamMember ta = playersA.get(i);
            TeamMember tb = playersB.get(i);
            if (m.getPlayerA() == null || m.getPlayerB() == null || ta.getSummoner() == null || tb.getSummoner() == null) {
                continue;
            }

            AnalysisLine line = new AnalysisLine();
            line.setHistory(history);
            line.setLane(m.getLane());
            line.setPrediction(m.getPrediction());
            line.setDifficulty(m.getDifficulty());

            var sa = ta.getSummoner();
            line.setAGameName(sa.getGameName());
            line.setATagLine(sa.getTagLine());
            line.setAChampion(ta.getSelectedChampion());
            line.setATier(sa.getTier());
            line.setASummonerLevel(sa.getSummonerLevel());
            line.setAAvgKda(sa.getAvgKda());
            line.setAAvgGpm(sa.getAvgGpm());
            line.setAWinScore(ta.getWinScore());

            var sb = tb.getSummoner();
            line.setBGameName(sb.getGameName());
            line.setBTagLine(sb.getTagLine());
            line.setBChampion(tb.getSelectedChampion());
            line.setBTier(sb.getTier());
            line.setBSummonerLevel(sb.getSummonerLevel());
            line.setBAvgKda(sb.getAvgKda());
            line.setBAvgGpm(sb.getAvgGpm());
            line.setBWinScore(tb.getWinScore());

            history.getLines().add(line);
        }

        if (!history.getLines().isEmpty()) {
            analysisHistoryRepository.save(history);
        }
    }
}
