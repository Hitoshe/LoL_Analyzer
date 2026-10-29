package com.lol.analyzer.repository;

import com.lol.analyzer.model.SummonerMastery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SummonerMasteryRepository extends JpaRepository<SummonerMastery, Long> {

    Optional<SummonerMastery> findFirstByPuuidAndChampionId(String puuid, long championId);

    @Modifying
    @Query("delete from SummonerMastery m where m.puuid = :puuid")
    void deleteAllByPuuid(String puuid);
}
