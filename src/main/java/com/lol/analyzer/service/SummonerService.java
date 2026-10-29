package com.lol.analyzer.service;

import com.lol.analyzer.client.RiotClient;
import com.lol.analyzer.model.*;
import com.lol.analyzer.repository.MatchParticipantRepository;
import com.lol.analyzer.repository.SummonerRepository;
import com.lol.analyzer.riot.PlayerNotFoundException;
import com.lol.analyzer.riot.Region;
import com.lol.analyzer.riot.RiotId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Loads and caches {@link Summoner} profiles per server. A fresh cached profile costs no Riot calls;
 * a stale one refreshes rank and mastery and downloads only ranked matches that are not stored yet.
 */
@Service
public class SummonerService {

    private static final Logger log = LoggerFactory.getLogger(SummonerService.class);

    private final RiotClient riotClient;
    private final SummonerRepository summonerRepository;
    private final MatchParticipantRepository participantRepository;
    private final DataDragonService dataDragonService;
    private final ProfileMatchPersistenceService persistenceService;
    private final MatchSyncService matchSyncService;
    private final long profileTtlMinutes;
    private final int matchIdsPerRefresh;
    private final int maxNewMatchesPerRefresh;

    public SummonerService(
            RiotClient riotClient,
            SummonerRepository summonerRepository,
            MatchParticipantRepository participantRepository,
            DataDragonService dataDragonService,
            ProfileMatchPersistenceService persistenceService,
            MatchSyncService matchSyncService,
            @Value("${analyzer.profile.ttl-minutes:20}") long profileTtlMinutes,
            @Value("${analyzer.profile.match-ids:20}") int matchIdsPerRefresh,
            @Value("${analyzer.profile.max-new-matches:5}") int maxNewMatchesPerRefresh) {
        this.riotClient = riotClient;
        this.summonerRepository = summonerRepository;
        this.participantRepository = participantRepository;
        this.dataDragonService = dataDragonService;
        this.persistenceService = persistenceService;
        this.matchSyncService = matchSyncService;
        this.profileTtlMinutes = profileTtlMinutes;
        this.matchIdsPerRefresh = matchIdsPerRefresh;
        this.maxNewMatchesPerRefresh = maxNewMatchesPerRefresh;
    }

    public Summoner getAccount(Region region, RiotId riotId) {
        Optional<Summoner> cached = summonerRepository
                .findByGameNameIgnoreCaseAndTagLineIgnoreCase(riotId.gameName(), riotId.tagLine());

        if (cached.isPresent() && isFresh(cached.get(), region)) {
            return cached.get();
        }

        Summoner summoner;
        if (cached.isPresent()) {
            summoner = cached.get();
        } else {
            AccountDTO account = riotClient.getAccountByRiotId(region, riotId.gameName(), riotId.tagLine());
            if (account == null || account.getPuuid() == null) {
                throw new PlayerNotFoundException("Игрок " + riotId.display() + " не найден. Проверьте ник и тег.");
            }
            summoner = summonerRepository.findById(account.getPuuid()).orElseGet(() -> new Summoner(account));
            summoner.setGameName(account.getGameName().trim());
            summoner.setTagLine(account.getTagLine().trim());
        }
        String puuid = summoner.getPuuid();

        SummonerDTO summonerDto = riotClient.getSummonerByPuuid(region, puuid);
        if (summonerDto == null) {
            throw new PlayerNotFoundException("У " + riotId.display() + " нет профиля League of Legends на сервере "
                    + region.getCode() + ". Проверьте выбранный регион.");
        }
        summoner.setPlatform(region.getPlatformId());
        summoner.setSummonerLevel(summonerDto.getSummonerLevel());
        applySoloQueue(summoner, riotClient.getLeagueEntriesByPuuid(region, puuid));

        MasteryDTO[] masteries = riotClient.getMasteries(region, puuid);
        persistenceService.replaceMasteries(puuid, masteries);
        applyTopMastery(summoner, masteries);

        String[] matchIds = riotClient.getRankedMatchIds(region, puuid, matchIdsPerRefresh);
        matchSyncService.syncMissing(matchIds, maxNewMatchesPerRefresh);
        applyRecentAverages(summoner);

        summoner.setLastUpdated(LocalDateTime.now());
        log.info("Profile refreshed: {} on {}", summoner.riotIdDisplay(), region.getCode());
        return summonerRepository.save(summoner);
    }

    private boolean isFresh(Summoner s, Region region) {
        return region.getPlatformId().equals(s.getPlatform())
                && s.getLastUpdated() != null
                && s.getLastUpdated().isAfter(LocalDateTime.now().minusMinutes(profileTtlMinutes));
    }

    private static void applySoloQueue(Summoner summoner, LeagueDTO[] leagues) {
        summoner.setTier(null);
        summoner.setRank(null);
        summoner.setLeaguePoints(null);
        summoner.setRankedWins(null);
        summoner.setRankedLosses(null);
        if (leagues == null) {
            return;
        }
        for (LeagueDTO league : leagues) {
            if ("RANKED_SOLO_5x5".equals(league.getQueueType())) {
                summoner.setTier(league.getTier());
                summoner.setRank(league.getRank());
                summoner.setLeaguePoints(league.getLeaguePoints());
                summoner.setRankedWins(league.getWins());
                summoner.setRankedLosses(league.getLosses());
            }
        }
    }

    private void applyTopMastery(Summoner summoner, MasteryDTO[] masteries) {
        if (masteries == null || masteries.length == 0) {
            return;
        }
        MasteryDTO top = Arrays.stream(masteries)
                .max((a, b) -> Integer.compare(a.getChampionPoints(), b.getChampionPoints()))
                .orElse(masteries[0]);
        summoner.setTopChampionId(top.getChampionId());
        summoner.setTopChampionPoints(top.getChampionPoints());
        summoner.setTopChampionName(dataDragonService.getChampionName(top.getChampionId()));
    }

    private void applyRecentAverages(Summoner summoner) {
        List<MatchParticipant> recent = participantRepository.findRecent(
                summoner.getPuuid(), PlayerStatsService.RANKED_QUEUES, PageRequest.of(0, matchIdsPerRefresh));
        if (recent.isEmpty()) {
            return;
        }
        double kills = 0, deaths = 0, assists = 0, gold = 0, seconds = 0;
        for (MatchParticipant p : recent) {
            kills += p.getKills();
            deaths += p.getDeaths();
            assists += p.getAssists();
            gold += p.getGoldEarned();
            seconds += p.getMatch().getGameDurationSeconds();
        }
        summoner.setAvgKda(Math.round((kills + assists) / Math.max(1, deaths) * 100.0) / 100.0);
        summoner.setAvgGpm(Math.round(gold / (Math.max(1, seconds) / 60.0) * 100.0) / 100.0);
    }
}
