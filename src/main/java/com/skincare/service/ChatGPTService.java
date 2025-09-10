package com.skincare.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skincare.dto.ChatGPTRequest;
import com.skincare.dto.ChatGPTResponse;
import com.skincare.dto.IngredientAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
 

import java.util.Arrays;
import java.util.List;
 

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatGPTService {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${chatgpt.api.url}")
    private String chatGPTApiUrl;

    @Value("${chatgpt.api.key}")
    private String chatGPTApiKey;

    @Value("${chatgpt.model}")
    private String chatGPTModel;

    @Value("${chatgpt.temperature}")
    private double chatGPTTemperature;

    public IngredientAnalysisResult analyzeIngredients(String ingredientsText) {
        try {
            String normalizedIngredients = normalizeAndTrimIngredients(ingredientsText, 150, 8000);
            String prompt = buildPrompt(normalizedIngredients);
            
            ChatGPTRequest request = new ChatGPTRequest();
            request.setModel(chatGPTModel);
            request.setMessages(Arrays.asList(
                new ChatGPTRequest.Message("system", "You are an expert in cosmetic and skincare formulation analysis."),
                new ChatGPTRequest.Message("user", prompt)
            ));
            request.setTemperature(chatGPTTemperature);
            request.setMax_tokens(2000);

            WebClient webClient = webClientBuilder
                .baseUrl(chatGPTApiUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + chatGPTApiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

            ChatGPTResponse response = webClient.post()
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatGPTResponse.class)
                .block();

            if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                String content = response.getChoices().get(0).getMessage().getContent();
                log.info("ChatGPT Response: {}", content);

                String cleaned = extractJsonObject(content);
                return objectMapper.readValue(cleaned, IngredientAnalysisResult.class);
            } else {
                log.error("No response received from ChatGPT API");
                return createFallbackAnalysis(ingredientsText);
            }

        } catch (Exception e) {
            log.error("Error calling ChatGPT API: {}", e.getMessage(), e);
            return createFallbackAnalysis(ingredientsText);
        }
    }

    private String buildPrompt(String ingredientsText) {
        return String.format("""
            You are an expert in cosmetic and skincare formulation analysis. 
            I will provide you a list of skincare ingredients.
            For each ingredient, classify it as one of:
            - "Good" (beneficial for skin)
            - "Bad" (harmful, irritating, or unsafe)
            - "Neutral" (no major effect, commonly used)

            Then, provide a rating for the overall product on a scale of 1–10, 
            based on the balance of good vs bad ingredients.  

            Return the result strictly as a raw JSON object ONLY (no code fences, no markdown, no commentary), with the following structure:
            {
              "ingredients": [
                { "name": "Ingredient1", "classification": "Good", "reason": "Why it is good" },
                { "name": "Ingredient2", "classification": "Bad", "reason": "Why it is bad" }
              ],
              "overall_rating": 7,
              "summary": "Short summary about the product safety and effectiveness"
            }

            If there are more than 150 ingredients, analyze the first 150 and summarize the rest.

            Here are the ingredients to analyze: %s
            """, ingredientsText);
    }

    private String extractJsonObject(String content) throws Exception {
        String cleaned = content.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        }
        if (cleaned.startsWith("```") ) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();

        if (cleaned.startsWith("{") && cleaned.endsWith("}")) {
            // Looks like a JSON object already
            objectMapper.readTree(cleaned); // validate
            return cleaned;
        }

        // Fallback: extract the first JSON object substring
        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            String candidate = cleaned.substring(firstBrace, lastBrace + 1);
            objectMapper.readTree(candidate); // validate
            return candidate;
        }

        throw new IllegalArgumentException("No JSON object found in content");
    }

    private String normalizeAndTrimIngredients(String ingredientsText, int maxItems, int maxChars) {
        if (ingredientsText == null) {
            return "";
        }
        String[] parts = ingredientsText.split(",");
        List<String> normalized = Arrays.stream(parts)
                .map(s -> s == null ? "" : s.trim())
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .distinct()
                .limit(maxItems)
                .toList();
        String joined = String.join(", ", normalized);
        if (joined.length() > maxChars) {
            return joined.substring(0, maxChars);
        }
        return joined;
    }

    private IngredientAnalysisResult createFallbackAnalysis(String ingredientsText) {
        IngredientAnalysisResult result = new IngredientAnalysisResult();
        
        // Create a basic analysis when ChatGPT is unavailable
        List<String> ingredients = Arrays.asList(ingredientsText.split(","));
        List<IngredientAnalysisResult.IngredientDetail> ingredientDetails = ingredients.stream()
            .map(ingredient -> {
                IngredientAnalysisResult.IngredientDetail detail = new IngredientAnalysisResult.IngredientDetail();
                detail.setName(ingredient.trim());
                detail.setClassification("Neutral");
                detail.setReason("Unable to analyze - ChatGPT service unavailable");
                return detail;
            })
            .toList();
        
        result.setIngredients(ingredientDetails);
        result.setOverall_rating(5);
        result.setSummary("Analysis unavailable due to service error. Please try again later.");
        
        return result;
    }
}
