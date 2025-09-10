package com.skincare.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class IngredientAnalysisRequest {
    @NotBlank(message = "Ingredients list cannot be empty")
    private String ingredients;
    
    private String productName; // Optional product name
}
