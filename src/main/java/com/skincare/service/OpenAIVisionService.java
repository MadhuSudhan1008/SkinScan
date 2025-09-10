package com.skincare.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAIVisionService {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${chatgpt.api.url}")
    private String chatGPTApiUrl;

    @Value("${chatgpt.api.key}")
    private String chatGPTApiKey;

    @Value("${chatgpt.model}")
    private String chatGPTModel;

    public String extractIngredientsFromImage(MultipartFile imageFile) throws IOException {
        try {
            // Convert image to base64
            String base64Image = Base64.getEncoder().encodeToString(imageFile.getBytes());
            String imageDataUrl = "data:" + imageFile.getContentType() + ";base64," + base64Image;

            // Create vision request
            Map<String, Object> request = Map.of(
                "model", chatGPTModel,
                "messages", List.of(
                    Map.of(
                        "role", "system",
                        "content", "You are an expert at reading ingredient lists from skincare product images. Extract ONLY the ingredient names from the image and return them as a JSON array. Do not include any other text, explanations, or analysis. Example: [\"water\", \"niacinamide\", \"hyaluronic acid\"]"
                    ),
                    Map.of(
                        "role", "user",
                        "content", List.of(
                            Map.of("type", "text", "text", "Extract the ingredient list from this image and return as JSON array:"),
                            Map.of("type", "image_url", "image_url", Map.of("url", imageDataUrl))
                        )
                    )
                ),
                "max_tokens", 400,
                "temperature", 0.1
            );

            WebClient webClient = webClientBuilder
                .baseUrl(chatGPTApiUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + chatGPTApiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            if (response != null && response.containsKey("choices")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String content = (String) message.get("content");
                    
                    log.info("Vision extraction result: {}", content);
                    
                    // Parse and clean the JSON array
                    return parseAndCleanIngredients(content);
                }
            }

            log.error("No response received from OpenAI Vision API");
            throw new RuntimeException("Failed to extract ingredients from image");

        } catch (Exception e) {
            log.error("Error calling OpenAI Vision API: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to extract ingredients from image: " + e.getMessage());
        }
    }

    private String parseAndCleanIngredients(String content) {
        try {
            // Remove any markdown formatting or extra text
            String cleanedContent = content.trim();
            
            // Extract JSON array from the response
            if (cleanedContent.startsWith("```json")) {
                cleanedContent = cleanedContent.substring(7);
            }
            if (cleanedContent.startsWith("```")) {
                cleanedContent = cleanedContent.substring(3);
            }
            if (cleanedContent.endsWith("```")) {
                cleanedContent = cleanedContent.substring(0, cleanedContent.length() - 3);
            }
            
            cleanedContent = cleanedContent.trim();
            
            // Parse JSON array
            @SuppressWarnings("unchecked")
            List<String> ingredients = objectMapper.readValue(cleanedContent, List.class);
            
            // Clean and normalize ingredients
            List<String> cleanedIngredients = ingredients.stream()
                .map(ingredient -> ingredient.toString().toLowerCase().trim())
                .filter(ingredient -> !ingredient.isEmpty())
                .distinct()
                .toList();
            
            // Convert back to comma-separated string
            return String.join(", ", cleanedIngredients);
            
        } catch (Exception e) {
            log.error("Error parsing ingredients from vision response: {}", e.getMessage());
            throw new RuntimeException("Failed to parse ingredients from image: " + e.getMessage());
        }
    }
}
