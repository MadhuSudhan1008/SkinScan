package com.skincare.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ingredient_analyses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngredientAnalysis {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "identified_ingredients", columnDefinition = "TEXT")
    private String identifiedIngredients;
    
    @Column(name = "safety_analysis", columnDefinition = "TEXT")
    private String safetyAnalysis;
    
    @Column(name = "safety_score")
    private Double safetyScore;
    
    @Column(name = "analysis_date")
    private LocalDateTime analysisDate;
    
    @Column(name = "product_name")
    private String productName;
    
    @PrePersist
    protected void onCreate() {
        analysisDate = LocalDateTime.now();
    }
}
