package com.skincare.controller;

import com.skincare.dto.IngredientAnalysisRequest;
import com.skincare.dto.IngredientAnalysisResponseDto;
import com.skincare.model.IngredientAnalysis;
import com.skincare.service.IngredientAnalysisService;
import com.skincare.service.OpenAIVisionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/ingredients")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Ingredient Analysis", description = "Endpoints for analyzing skincare product ingredients")
@SecurityRequirement(name = "Bearer Authentication")
public class IngredientAnalysisController {

    private final IngredientAnalysisService analysisService;
    private final OpenAIVisionService visionService;

    @Operation(
            summary = "Analyze Ingredients from Text",
            description = "Analyze a list of ingredients provided as text and get safety analysis, scores, and recommendations"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Analysis completed successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = IngredientAnalysisResponseDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing JWT token",
                    content = @Content(mediaType = "application/json")
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error during analysis",
                    content = @Content(mediaType = "application/json")
            )
    })
    @PostMapping("/analyze")
    public ResponseEntity<IngredientAnalysisResponseDto> analyzeIngredient(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Ingredient analysis request containing ingredients list and product name",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = IngredientAnalysisRequest.class)
                    )
            )
            @RequestBody IngredientAnalysisRequest request
    ) throws IOException {
        IngredientAnalysis analysis = analysisService.analyzeIngredient(
                userDetails.getUsername(),
                request.getIngredients(),
                request.getProductName()
        );
        return ResponseEntity.ok(convertToDto(analysis));
    }

    @Operation(
            summary = "Analyze Ingredients from Image",
            description = "Upload an image of a skincare product label and automatically extract and analyze the ingredients using AI vision"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Image analysis completed successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = IngredientAnalysisResponseDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing JWT token",
                    content = @Content(mediaType = "application/json")
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid image file or format",
                    content = @Content(mediaType = "application/json")
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error during image analysis",
                    content = @Content(mediaType = "application/json")
            )
    })
    @PostMapping(value = "/analyze-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IngredientAnalysisResponseDto> analyzeIngredientFromImage(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(
                    description = "Image file of the skincare product label (JPEG, PNG, etc.)",
                    required = true
            )
            @RequestParam("image") MultipartFile image,
            @Parameter(
                    description = "Optional product name for better analysis context",
                    required = false
            )
            @RequestParam(value = "productName", required = false) String productName
    ) throws IOException {
        try {
            log.info("Received productName: {}", productName);
            
            // Extract ingredients from image using OpenAI Vision
            String extractedIngredients = visionService.extractIngredientsFromImage(image);
            
            // Analyze the extracted ingredients using existing service
            IngredientAnalysis analysis = analysisService.analyzeIngredient(
                    userDetails.getUsername(),
                    extractedIngredients,
                    productName
            );
            
            return ResponseEntity.ok(convertToDto(analysis));
            
        } catch (Exception e) {
            log.error("Error in image analysis: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(null);
        }
    }

    @Operation(
            summary = "Get Analysis History",
            description = "Retrieve the analysis history for the authenticated user"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Analysis history retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = IngredientAnalysisResponseDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing JWT token",
                    content = @Content(mediaType = "application/json")
            )
    })
    @GetMapping("/history")
    public ResponseEntity<List<IngredientAnalysisResponseDto>> getAnalysisHistory(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails
    ) {
        List<IngredientAnalysis> analyses = analysisService.getUserAnalyses(userDetails.getUsername());
        List<IngredientAnalysisResponseDto> dtos = analyses.stream()
                .map(this::convertToDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }
    
    private IngredientAnalysisResponseDto convertToDto(IngredientAnalysis analysis) {
        IngredientAnalysisResponseDto dto = new IngredientAnalysisResponseDto();
        dto.setId(analysis.getId());
        dto.setUsername(analysis.getUser().getUsername());
        dto.setIdentifiedIngredients(analysis.getIdentifiedIngredients());
        dto.setSafetyAnalysis(analysis.getSafetyAnalysis());
        dto.setSafetyScore(analysis.getSafetyScore());
        dto.setAnalysisDate(analysis.getAnalysisDate());
        dto.setProductName(analysis.getProductName());
        return dto;
    }
}
