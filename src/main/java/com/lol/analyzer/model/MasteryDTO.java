package com.lol.analyzer.model;

import lombok.Data;

@Data
public class MasteryDTO {
    private long championId;     // ID чемпиона
    private int championLevel;   // Уровень мастерства
    private int championPoints;  // Очки мастерства
}