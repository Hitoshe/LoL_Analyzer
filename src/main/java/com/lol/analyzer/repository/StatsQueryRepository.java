package com.lol.analyzer.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Aggregations over stored ranked matches for champion meta, lane matchups and duo synergy.
 * Each row ends with {@code games, wins}; lanes are the {@code Lane} enum names stored in {@code team_position}.
 */
@Repository
@Transactional(readOnly = true)
public class StatsQueryRepository {

    @PersistenceContext
    private EntityManager em;

    /** Rows: {@code champion_id, lane, games, wins}. */
    @SuppressWarnings("unchecked")
    public List<Object[]> championRoleStats(int queueId, long sinceEpochMs) {
        return em.createNativeQuery("""
                        select p.champion_id, p.team_position, count(*), sum(case when p.win then 1 else 0 end)
                        from lol_match_participants p
                        join lol_matches m on m.match_id = p.match_id
                        where m.queue_id = :queue and m.game_start_timestamp >= :since
                          and p.team_position is not null and p.win is not null
                        group by p.champion_id, p.team_position
                        """)
                .setParameter("queue", queueId)
                .setParameter("since", sinceEpochMs)
                .getResultList();
    }

    /** Rows: {@code lane, champion_id, enemy_champion_id, games, wins} from the first champion's perspective. */
    @SuppressWarnings("unchecked")
    public List<Object[]> laneMatchupStats(int queueId, long sinceEpochMs) {
        return em.createNativeQuery("""
                        select a.team_position, a.champion_id, b.champion_id, count(*), sum(case when a.win then 1 else 0 end)
                        from lol_match_participants a
                        join lol_match_participants b
                          on b.match_id = a.match_id and b.team_position = a.team_position and b.team_id <> a.team_id
                        join lol_matches m on m.match_id = a.match_id
                        where m.queue_id = :queue and m.game_start_timestamp >= :since
                          and a.team_position is not null and a.win is not null
                        group by a.team_position, a.champion_id, b.champion_id
                        """)
                .setParameter("queue", queueId)
                .setParameter("since", sinceEpochMs)
                .getResultList();
    }

    /** Rows: {@code lane_a, champion_a, lane_b, champion_b, games, wins} for teammates, with {@code lane_a < lane_b}. */
    @SuppressWarnings("unchecked")
    public List<Object[]> duoSynergyStats(int queueId, long sinceEpochMs) {
        return em.createNativeQuery("""
                        select a.team_position, a.champion_id, b.team_position, b.champion_id,
                               count(*), sum(case when a.win then 1 else 0 end)
                        from lol_match_participants a
                        join lol_match_participants b
                          on b.match_id = a.match_id and b.team_id = a.team_id and a.team_position < b.team_position
                        join lol_matches m on m.match_id = a.match_id
                        where m.queue_id = :queue and m.game_start_timestamp >= :since
                          and a.team_position is not null and b.team_position is not null and a.win is not null
                        group by a.team_position, a.champion_id, b.team_position, b.champion_id
                        """)
                .setParameter("queue", queueId)
                .setParameter("since", sinceEpochMs)
                .getResultList();
    }
}
