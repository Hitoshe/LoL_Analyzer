package com.lol.analyzer.service;

import com.lol.analyzer.model.ChampionInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChampionCatalogTest {

    private final ChampionCatalog catalog = new ChampionCatalog("15.21.1", List.of(
            new ChampionInfo(145, "Kaisa", "Kai'Sa", "Кай'Са", "icon", List.of("Marksman")),
            new ChampionInfo(62, "MonkeyKing", "Wukong", "Вуконг", "icon", List.of("Fighter")),
            new ChampionInfo(20, "Nunu", "Nunu & Willump", "Нуну и Виллумп", "icon", List.of("Tank")),
            new ChampionInfo(103, "Ahri", "Ahri", "Ари", "icon", List.of("Mage")),
            new ChampionInfo(1, "Annie", "Annie", "Энни", "icon", List.of("Mage"))));

    @Test
    void findsByLooseSpellingKeyAndRussianName() {
        assertThat(catalog.find("kaisa")).map(ChampionInfo::id).contains(145L);
        assertThat(catalog.find("KAI'SA")).map(ChampionInfo::id).contains(145L);
        assertThat(catalog.find("Кайса")).map(ChampionInfo::id).contains(145L);
        assertThat(catalog.find("MonkeyKing")).map(ChampionInfo::id).contains(62L);
        assertThat(catalog.find("Ари")).map(ChampionInfo::id).contains(103L);
    }

    @Test
    void acceptsOnlyUniquePrefixes() {
        assertThat(catalog.find("nunu")).map(ChampionInfo::id).contains(20L);
        assertThat(catalog.find("an")).isEmpty();
        assertThat(catalog.find("xyz")).isEmpty();
    }

    @Test
    void patchIsMajorMinor() {
        assertThat(catalog.patch()).isEqualTo("15.21");
    }
}
