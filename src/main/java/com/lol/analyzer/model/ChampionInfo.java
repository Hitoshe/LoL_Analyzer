package com.lol.analyzer.model;

import java.util.List;

/**
 * Static champion data from Data Dragon: numeric id, internal key ({@code MonkeyKing}), English and Russian names,
 * square icon URL and class tags ({@code Marksman}, {@code Support}, ...).
 */
public record ChampionInfo(long id, String key, String name, String localizedName, String iconUrl, List<String> tags) {
}
