package com.lol.analyzer.repository;

import com.lol.analyzer.model.AnalysisHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/** Persistence for {@link AnalysisHistory} headers shown on {@code /history}. */
@Repository
public interface AnalysisHistoryRepository extends JpaRepository<AnalysisHistory, Long> {

    Page<AnalysisHistory> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("""
            select h from AnalysisHistory h
            where h.actualBlueWin is null and h.resolvedMatchId is null
              and h.bluePuuids is not null and h.region is not null
              and h.createdAt between :from and :to
              and (h.resolveAttempts is null or h.resolveAttempts < :maxAttempts)
            order by h.createdAt asc
            """)
    List<AnalysisHistory> findPendingOutcome(LocalDateTime from, LocalDateTime to, int maxAttempts, Pageable pageable);

    List<AnalysisHistory> findByActualBlueWinIsNotNull();
}
