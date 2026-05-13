package com.lol.analyzer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reference table for champion IDs observed in stored matches; names come from Data Dragon on first insert.
 */
@Entity
@Table(name = "champions")
@Data
@NoArgsConstructor
public class Champion {

    @Id
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    public Champion(Long id, String name) {
        this.id = id;
        this.name = name;
    }
}
