package com.skincare.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.skincare.dto.IngredientAnalysisResult;
import com.skincare.model.IngredientAnalysis;
import com.skincare.model.User;
import com.skincare.repository.IngredientAnalysisRepository;
import com.skincare.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngredientAnalysisService {

    private final IngredientAnalysisRepository analysisRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final ChatGPTService chatGPTService;



    public IngredientAnalysis analyzeIngredient(String username, String ingredientsText, String productName) throws IOException {
        log.info("Service received productName: {}", productName);
        
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Split the input text into individual ingredients
        List<String> ingredients = Arrays.asList(ingredientsText.split(","));
        
        // Get ChatGPT analysis directly
        IngredientAnalysisResult chatGPTAnalysis = chatGPTService.analyzeIngredients(ingredientsText);
        
        // Convert ChatGPT rating to safety score (0-1 scale)
        double safetyScore = chatGPTAnalysis.getOverall_rating() / 10.0;

        return createAnalysis(user, ingredients, chatGPTAnalysis, safetyScore, productName);
    }

    private IngredientAnalysis createAnalysis(User user, List<String> ingredients, 
            IngredientAnalysisResult chatGPTAnalysis, double safetyScore, String productName) throws IOException {
        
        // Save analysis to database with simplified structure
        IngredientAnalysis analysis = new IngredientAnalysis();
        analysis.setUser(user);
        analysis.setIdentifiedIngredients(objectMapper.writeValueAsString(ingredients));
        analysis.setSafetyAnalysis(objectMapper.writeValueAsString(chatGPTAnalysis)); // Store ChatGPT response directly
        analysis.setSafetyScore(safetyScore);
        analysis.setProductName(productName); // Set the product name

        return analysisRepository.save(analysis);
    }

    public List<IngredientAnalysis> getUserAnalyses(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return analysisRepository.findByUserOrderByAnalysisDateDesc(user);
    }
}
