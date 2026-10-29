package com.lol.analyzer.service;

import com.lol.analyzer.model.ChampionInfo;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Immutable snapshot of one Data Dragon patch with forgiving name lookup:
 * {@code kaisa}, {@code Kai'Sa}, {@code Кайса}, {@code MonkeyKing} and unique prefixes such as {@code nunu} all resolve.
 */
public final class ChampionCatalog {

    private final String version;
    private final Map<Long, ChampionInfo> byId;
    private final Map<String, ChampionInfo> byNormalizedName;

    public ChampionCatalog(String version, Collection<ChampionInfo> champions) {
        this.version = version;
        Map<Long, ChampionInfo> ids = new HashMap<>();
        Map<String, ChampionInfo> names = new HashMap<>();
        for (ChampionInfo c : champions) {
            ids.put(c.id(), c);
            names.put(normalize(c.name()), c);
            names.put(normalize(c.key()), c);
            if (c.localizedName() != null) {
                names.putIfAbsent(normalize(c.localizedName()), c);
            }
        }
        this.byId = Map.copyOf(ids);
        this.byNormalizedName = Map.copyOf(names);
    }

    public static ChampionCatalog empty() {
        return new ChampionCatalog(null, List.of());
    }

    public String version() {
        return version;
    }

    /** {@code 15.21.1} becomes {@code 15.21}; the format matches {@code GameMatch.patch}. */
    public String patch() {
        return patchOf(version);
    }

    public static String patchOf(String version) {
        if (version == null) {
            return null;
        }
        String[] parts = version.split("\\.");
        return parts.length >= 2 ? parts[0] + "." + parts[1] : version;
    }

    public boolean isEmpty() {
        return byId.isEmpty();
    }

    public int size() {
        return byId.size();
    }

    public Optional<ChampionInfo> byId(long id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<ChampionInfo> find(String input) {
        String key = normalize(input);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        ChampionInfo exact = byNormalizedName.get(key);
        if (exact != null) {
            return Optional.of(exact);
        }
        if (key.length() < 3) {
            return Optional.empty();
        }
        Set<ChampionInfo> prefixMatches = byNormalizedName.entrySet().stream()
                .filter(e -> e.getKey().startsWith(key))
                .map(Map.Entry::getValue)
                .collect(Collectors.toSet());
        return prefixMatches.size() == 1 ? prefixMatches.stream().findFirst() : Optional.empty();
    }

    public List<ChampionInfo> sortedByName() {
        return byId.values().stream().sorted(Comparator.comparing(ChampionInfo::name)).toList();
    }

    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return decomposed.toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
