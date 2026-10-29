package com.lol.analyzer.repository;

import com.lol.analyzer.model.GameMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/** Stores {@link GameMatch} rows keyed by Riot {@code matchId}. */
@Repository
public interface GameMatchRepository extends JpaRepository<GameMatch, String> {

    /** Ids already stored with full data (queue, time, results); others must be (re)downloaded. */
    @Query("select m.matchId from GameMatch m where m.matchId in :ids and m.queueId is not null")
    List<String> findCompleteIds(Collection<String> ids);

    long countByQueueIdAndGameStartTimestampGreaterThanEqual(Integer queueId, Long since);
}
