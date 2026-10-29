package com.lol.analyzer.repository;

import com.lol.analyzer.model.Summoner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** CRUD + case-insensitive lookup by Riot ID components for the 24h cache path. */
@Repository
public interface SummonerRepository extends JpaRepository<Summoner, String> {
    Optional<Summoner> findByGameNameIgnoreCaseAndTagLineIgnoreCase(String gameName, String tagLine);
}
