package com.lol.analyzer.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Header row for a persisted UI analysis: timestamps, team win percentages, Gemini text, and child {@link AnalysisLine}s.
 */
@Entity
@Table(name = "analysis_histories")
@Data
@NoArgsConstructor
public class AnalysisHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "team_a_chance", nullable = false)
    private double teamAChance;

    @Column(name = "team_b_chance", nullable = false)
    private double teamBChance;

    @Lob
    @Column(name = "ai_advice")
    private String aiAdvice;

    @OneToMany(mappedBy = "history", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AnalysisLine> lines = new ArrayList<>();
}
