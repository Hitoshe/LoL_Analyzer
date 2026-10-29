package com.lol.analyzer.analysis;

/**
 * How the chosen lane relates to what the player usually plays in recent ranked games.
 * Riot does not expose an autofill flag, so {@link #OFFROLE} is the closest observable signal.
 */
public enum RoleFit {
    MAIN("Своя роль", "good"),
    SECONDARY("Вторая роль", "neutral"),
    OFFROLE("Офф-роль (вероятен автофилл)", "bad"),
    UNKNOWN("Мало игр для оценки роли", "muted");

    private final String label;
    private final String css;

    RoleFit(String label, String css) {
        this.label = label;
        this.css = css;
    }

    public String getLabel() {
        return label;
    }

    public String getCss() {
        return css;
    }
}
