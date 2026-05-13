package com.lol.analyzer.repository;

import com.lol.analyzer.model.GameMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Stores {@link GameMatch} rows keyed by Riot {@code matchId}. */
@Repository
public interface GameMatchRepository extends JpaRepository<GameMatch, String> {
}
