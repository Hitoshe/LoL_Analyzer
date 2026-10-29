package com.lol.analyzer.repository;

import com.lol.analyzer.model.Champion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Lookup/insert helper for {@link Champion} rows referenced from {@code lol_match_participants}. */
@Repository
public interface ChampionRepository extends JpaRepository<Champion, Long> {
}
