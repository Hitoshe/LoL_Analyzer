package com.lol.analyzer.repository;

import com.lol.analyzer.model.AnalysisHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Persistence for {@link AnalysisHistory} headers shown on {@code /history}. */
@Repository
public interface AnalysisHistoryRepository extends JpaRepository<AnalysisHistory, Long> {

    Page<AnalysisHistory> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
