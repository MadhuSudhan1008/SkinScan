package com.skincare.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class IngredientAnalysisResponse {
    private String productName;
    private List<String> ingredients;
    private Map<String, String> safetyInfo;
    private double safetyScore;
    private List<String> warnings;
    private List<String> allergens;
    private String nutritionalGrade;
    private Map<String, Double> nutritionalValues;
    
    // ChatGPT Analysis Results
    private IngredientAnalysisResult chatGPTAnalysis;
    private String analysisSource = "ChatGPT";
}
