package com.skincare.dto;

import lombok.Data;
import java.util.List;

@Data
public class IngredientAnalysisResult {
    private List<IngredientDetail> ingredients;
    private int overall_rating;
    private String summary;

    @Data
    public static class IngredientDetail {
        private String name;
        private String classification;
        private String reason;
    }
}
