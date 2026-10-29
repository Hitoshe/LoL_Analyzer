package com.lol.analyzer.analysis;

import com.lol.analyzer.model.Lane;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.lol.analyzer.analysis.RoleAssigner.*;
import static org.assertj.core.api.Assertions.assertThat;

class RoleAssignerTest {

    @Test
    void assignsTypicalTeamFromSpellsAndClasses() {
        List<RoleAssigner.Candidate> team = List.of(
                new Candidate(4, SPELL_HEAL, List.of("Marksman"), Map.of()),
                new Candidate(4, SPELL_EXHAUST, List.of("Support", "Tank"), Map.of()),
                new Candidate(4, SPELL_SMITE, List.of("Fighter"), Map.of()),
                new Candidate(4, SPELL_TELEPORT, List.of("Fighter", "Tank"), Map.of()),
                new Candidate(4, SPELL_IGNITE, List.of("Mage", "Assassin"), Map.of()));
        assertThat(RoleAssigner.assign(team)).containsExactly(Lane.ADC, Lane.SUP, Lane.JNG, Lane.TOP, Lane.MID);
    }

    @Test
    void observedLaneSplitBeatsClassTags() {
        Map<Lane, WinRecord> playedTop = Map.of(Lane.TOP, new WinRecord(90, 45), Lane.MID, new WinRecord(10, 5));
        List<RoleAssigner.Candidate> team = List.of(
                new Candidate(4, SPELL_IGNITE, List.of("Mage"), playedTop),
                new Candidate(4, SPELL_IGNITE, List.of("Mage"), Map.of()));
        assertThat(RoleAssigner.assign(team)).containsExactly(Lane.TOP, Lane.MID);
    }
}
