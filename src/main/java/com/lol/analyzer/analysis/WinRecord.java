package com.lol.analyzer.analysis;

public record WinRecord(int games, int wins) {

    public static final WinRecord EMPTY = new WinRecord(0, 0);

    public WinRecord plus(WinRecord other) {
        return new WinRecord(games + other.games, wins + other.wins);
    }

    public double winRate() {
        return games == 0 ? 0.5 : wins / (double) games;
    }
}
