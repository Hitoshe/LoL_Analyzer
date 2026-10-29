package com.lol.analyzer.repository;

import com.lol.analyzer.model.MatchParticipant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MatchParticipantRepository extends JpaRepository<MatchParticipant, Long> {

    @Query("""
            select p from MatchParticipant p
            join fetch p.match m
            join fetch p.champion
            where p.puuid = :puuid and m.queueId in :queues and p.win is not null
            order by m.gameStartTimestamp desc
            """)
    List<MatchParticipant> findRecent(String puuid, Collection<Integer> queues, Pageable pageable);

    /** Returns a single row {@code [games, wins]} across every stored game of the player on the champion. */
    @Query("""
            select count(p), coalesce(sum(case when p.win = true then 1 else 0 end), 0)
            from MatchParticipant p join p.match m
            where p.puuid = :puuid and p.champion.id = :championId and m.queueId in :queues and p.win is not null
            """)
    List<Object[]> championRecord(String puuid, long championId, Collection<Integer> queues);
}
