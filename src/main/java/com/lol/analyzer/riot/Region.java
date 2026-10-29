package com.lol.analyzer.riot;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * League of Legends servers with Riot API routing.
 * <p>Summoner, League, Mastery and Spectator use the platform host ({@code euw1}, {@code kr}, ...);
 * Match-V5 uses the regional cluster ({@code europe}, {@code americas}, {@code asia}, {@code sea});
 * Account-V1 only exists on {@code europe}/{@code americas}/{@code asia}, and any of them resolves any account.
 */
public enum Region {
    EUW("EUW1", "europe", "europe", "EUW", "Europe West", "euw"),
    EUNE("EUN1", "europe", "europe", "EUNE", "Europe Nordic & East", "eune"),
    TR("TR1", "europe", "europe", "TR1", "Turkey", "tr"),
    RU("RU", "europe", "europe", "RU1", "Russia", "ru"),
    ME("ME1", "europe", "europe", "ME1", "Middle East", "me"),
    NA("NA1", "americas", "americas", "NA1", "North America", "na"),
    BR("BR1", "americas", "americas", "BR1", "Brazil", "br"),
    LAN("LA1", "americas", "americas", "LAN", "Latin America North", "lan"),
    LAS("LA2", "americas", "americas", "LAS", "Latin America South", "las"),
    KR("KR", "asia", "asia", "KR1", "Korea", "kr"),
    JP("JP1", "asia", "asia", "JP1", "Japan", "jp"),
    OCE("OC1", "sea", "asia", "OCE", "Oceania", "oce"),
    SEA("SG2", "sea", "asia", "SG2", "Southeast Asia", "sea"),
    TW("TW2", "sea", "asia", "TW2", "Taiwan", "tw"),
    VN("VN2", "sea", "asia", "VN2", "Vietnam", "vn");

    private final String platformId;
    private final String matchRouting;
    private final String accountRouting;
    private final String defaultTag;
    private final String displayName;
    private final String opggSlug;

    Region(String platformId, String matchRouting, String accountRouting, String defaultTag, String displayName, String opggSlug) {
        this.platformId = platformId;
        this.matchRouting = matchRouting;
        this.accountRouting = accountRouting;
        this.defaultTag = defaultTag;
        this.displayName = displayName;
        this.opggSlug = opggSlug;
    }

    public String getPlatformId() {
        return platformId;
    }

    public String getPlatformHost() {
        return platformId.toLowerCase(Locale.ROOT);
    }

    public String getMatchRouting() {
        return matchRouting;
    }

    public String getAccountRouting() {
        return accountRouting;
    }

    public String getDefaultTag() {
        return defaultTag;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCode() {
        return name();
    }

    public String getOpggSlug() {
        return opggSlug;
    }

    /** Accepts the enum name ({@code EUW}) or the platform id ({@code EUW1}), case-insensitive. */
    public static Optional<Region> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(r -> r.name().equals(value) || r.platformId.equals(value))
                .findFirst();
    }

    /** Match ids look like {@code EUW1_7234567890}; the prefix is the platform id. */
    public static Optional<Region> fromMatchId(String matchId) {
        if (matchId == null) {
            return Optional.empty();
        }
        int sep = matchId.indexOf('_');
        return sep > 0 ? parse(matchId.substring(0, sep)) : Optional.empty();
    }
}
