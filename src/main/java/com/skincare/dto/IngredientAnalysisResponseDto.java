package com.skincare.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class IngredientAnalysisResponseDto {
    private Long id;
    private String username; // Only username, not full user object
    private String identifiedIngredients;
    private String safetyAnalysis;
    private Double safetyScore;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime analysisDate;
    private String productName;
}
