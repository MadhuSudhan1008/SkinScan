package com.skincare.controller;

import com.skincare.dto.AuthRequest;
import com.skincare.dto.SignupRequest;
import com.skincare.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication endpoints for user registration and login")
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "User Registration",
            description = "Register a new user account and receive a JWT token for authentication"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User registered successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Map.class),
                            examples = @ExampleObject(
                                    name = "Success Response",
                                    value = "{\"token\": \"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...\", \"message\": \"User registered successfully\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data or user already exists",
                    content = @Content(mediaType = "application/json")
            )
    })
    @PostMapping("/signup")
    public ResponseEntity<Map<String, String>> signup(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "User registration details",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SignupRequest.class)
                    )
            )
            @RequestBody SignupRequest request) {
        String token = authService.signup(request);
        return ResponseEntity.ok(Map.of("token", token, "message", "User registered successfully"));
    }

    @Operation(
            summary = "User Login",
            description = "Authenticate user credentials and receive a JWT token"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Login successful",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Map.class),
                            examples = @ExampleObject(
                                    name = "Success Response",
                                    value = "{\"token\": \"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...\", \"message\": \"Login successful\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid credentials",
                    content = @Content(mediaType = "application/json")
            )
    })
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "User login credentials",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AuthRequest.class)
                    )
            )
            @RequestBody AuthRequest request) {
        String token = authService.login(request);
        return ResponseEntity.ok(Map.of("token", token, "message", "Login successful"));
    }
}
