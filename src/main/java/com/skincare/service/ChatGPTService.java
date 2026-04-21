package com.skincare.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skincare.dto.IngredientAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

            Map<String, Object> request = new LinkedHashMap<>();
            request.put("model", chatGPTModel);
            request.put("input", List.of(
                    Map.of(
                            "role", "system",
                            "content", List.of(
                                    Map.of(
                                            "type", "input_text",
                                            "text", "You are an expert in cosmetic and skincare formulation analysis."
                                    )
                            )
                    ),
                    Map.of(
                            "role", "user",
                            "content", List.of(
                                    Map.of(
                                            "type", "input_text",
                                            "text", prompt
                                    )
                            )
                    )
            ));
            request.put("max_output_tokens", 2000);

            if (isGpt5Model(chatGPTModel)) {
                request.put("reasoning", Map.of("effort", "none"));
            } else {
                request.put("temperature", chatGPTTemperature);
            }

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

            String content = extractTextFromResponse(response);
            if (StringUtils.hasText(content)) {
                log.info("ChatGPT Response: {}", content);

                String cleaned = extractJsonObject(content);
                return objectMapper.readValue(cleaned, IngredientAnalysisResult.class);
            } else {
                log.error("No valid response received from OpenAI API");
                return createFallbackAnalysis(ingredientsText);
            }

        } catch (WebClientResponseException e) {
            log.error("OpenAI API returned {} with body: {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            return createFallbackAnalysis(ingredientsText);
        } catch (Exception e) {
            log.error("Error calling OpenAI API: {}", e.getMessage(), e);
            return createFallbackAnalysis(ingredientsText);
        }
    }

    private boolean isGpt5Model(String model) {
        return StringUtils.hasText(model) && model.toLowerCase().startsWith("gpt-5");
    }

    private String extractTextFromResponse(Map<String, Object> response) {
        if (response == null) {
            return null;
        }

        Object outputText = response.get("output_text");
        if (outputText instanceof String outputTextValue && StringUtils.hasText(outputTextValue)) {
            return outputTextValue;
        }

        Object outputObject = response.get("output");
        if (!(outputObject instanceof List<?> outputList)) {
            return null;
        }

        for (Object item : outputList) {
            if (!(item instanceof Map<?, ?> outputItem)) {
                continue;
            }

            Object contentObject = outputItem.get("content");
            if (!(contentObject instanceof List<?> contentList)) {
                continue;
            }

            for (Object content : contentList) {
                if (!(content instanceof Map<?, ?> contentItem)) {
                    continue;
                }

                Object text = contentItem.get("text");
                if (text instanceof String textValue && StringUtils.hasText(textValue)) {
                    return textValue;
                }
            }
        }

        return null;
    }

    private String buildPrompt(String ingredientsText) {
        return String.format("""
                You are an expert cosmetic chemist and dermatologist.
                       \s
                        Analyze the following skincare product ingredients for a user with skin type/concerns: %s
                       \s
                        Classify each ingredient as:
                        - "Good": clinically backed benefits (e.g., hyaluronic acid, niacinamide, ceramides, peptides, retinoids, AHAs/BHAs)
                        - "Bad": known irritants, sensitizers, or harmful compounds (e.g., denatured alcohol, artificial fragrance/parfum, formaldehyde releasers, high-risk parabens)
                        - "Neutral": functional ingredients with no notable benefit or harm (e.g., emulsifiers, thickeners, pH adjusters)
                       \s
                        Important rules:
                        - Ingredients appear in descending concentration order; weigh early ingredients more heavily
                        - A single harmful ingredient (carcinogen, known sensitizer) should significantly impact the overall_rating
                        - Factor in the user's skin type when classifying borderline ingredients
                        - If more than 150 ingredients, analyze first 150 and note the count skipped
                        - Do not guess
                            - If evidence is weak → mark as "uncertain"
                            - Follow dermatological consensus (INCI + scientific studies)
                            - Be conservative for sensitive/acne-prone skin
                       \s
                        Return ONLY a raw JSON object, no markdown, no code fences:
                        {
                          "ingredients": [
                            { "name": "Ingredient1", "classification": "Good", "reason": "Brief reason" }
                          ],
                          "overall_rating": 7,
                          "rating_breakdown": {
                            "good_count": 5,
                            "bad_count": 2,
                            "neutral_count": 8
                          },
                          "summary": "Short summary about safety and effectiveness for this skin type"
                        }
                       \s
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
