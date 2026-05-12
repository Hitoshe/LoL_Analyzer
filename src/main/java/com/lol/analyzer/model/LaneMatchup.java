package com.lol.analyzer.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@Data
@AllArgsConstructor
@ToString
public class LaneMatchup {
    private String lane;
    private Summoner playerA;
    private Summoner playerB;

    private String championA; // Кто выбран в текущем матче за команду А
    private String championB; // Кто выбран в текущем матче за команду Б

    private String prediction; // Кто фаворит на линии
    private String difficulty; // EASY, MEDIUM, HARD
}