package com.lol.analyzer.model;

import java.util.Locale;
import java.util.Optional;

/**
 * Lanes as shown in the UI; {@link #fromRiotPosition(String)} maps Match-V5 {@code teamPosition} values.
 */
public enum Lane {
    TOP("TOP", "Топ"),
    JNG("JUNGLE", "Лес"),
    MID("MIDDLE", "Мид"),
    ADC("BOTTOM", "АДК"),
    SUP("UTILITY", "Саппорт");

    private final String riotPosition;
    private final String label;

    Lane(String riotPosition, String label) {
        this.riotPosition = riotPosition;
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static Optional<Lane> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        for (Lane lane : values()) {
            if (lane.name().equals(value) || lane.riotPosition.equals(value)) {
                return Optional.of(lane);
            }
        }
        return switch (value) {
            case "JUNGLER", "JG" -> Optional.of(JNG);
            case "BOT", "ADCARRY" -> Optional.of(ADC);
            case "SUPPORT" -> Optional.of(SUP);
            default -> Optional.empty();
        };
    }

    public static Optional<Lane> fromRiotPosition(String teamPosition) {
        return parse(teamPosition);
    }
}
