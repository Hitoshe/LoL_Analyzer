package com.lol.analyzer.riot;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RiotIdTest {

    @Test
    void parsesPlainRiotId() {
        assertThat(RiotId.parse("Faker#KR1", "EUW")).contains(new RiotId("Faker", "KR1"));
    }

    @Test
    void stripsInvisibleCharactersCopiedFromClient() {
        assertThat(RiotId.parse("\u2066Some Player\u2069 #\u2066EUW\u2069", "EUW")).contains(new RiotId("Some Player", "EUW"));
    }

    @Test
    void stripsLobbyChatSuffix() {
        assertThat(RiotId.parse("Nick Name#EUW joined the lobby", "EUW")).contains(new RiotId("Nick Name", "EUW"));
        assertThat(RiotId.parse("Ник#RU1 присоединился к лобби", "EUW")).contains(new RiotId("Ник", "RU1"));
    }

    @Test
    void usesRegionDefaultTagWithoutHash() {
        assertThat(RiotId.parse("  Player  ", "EUNE")).contains(new RiotId("Player", "EUNE"));
    }

    @Test
    void rejectsBlankAndOverlongValues() {
        assertThat(RiotId.parse("   ", "EUW")).isEmpty();
        assertThat(RiotId.parse("Name#TOOLONGTAG", "EUW")).isEmpty();
    }

    @Test
    void splitsPastedBlock() {
        List<RiotId> ids = RiotId.parseMany("A#EUW\nB#EUW joined the lobby\r\n\nC, D#TR1", "EUW");
        assertThat(ids).containsExactly(
                new RiotId("A", "EUW"), new RiotId("B", "EUW"), new RiotId("C", "EUW"), new RiotId("D", "TR1"));
    }

    @Test
    void regionRoutingFromMatchId() {
        assertThat(Region.fromMatchId("KR_123")).contains(Region.KR);
        assertThat(Region.fromMatchId("EUN1_42")).contains(Region.EUNE);
        assertThat(Region.parse("na1")).contains(Region.NA);
        assertThat(Region.OCE.getMatchRouting()).isEqualTo("sea");
        assertThat(Region.OCE.getAccountRouting()).isEqualTo("asia");
    }
}
