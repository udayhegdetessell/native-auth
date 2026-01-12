package com.tessell.entra.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.tessell.entra.dto.request.CreateUserRequest;
import com.tessell.entra.dto.response.ApiResponse;
import com.tessell.entra.service.GraphApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    
    private final GraphApiService graphApiService;
    
    /**
     * Create a new user using Graph API
     */
    @PostMapping
    public ResponseEntity<ApiResponse<JsonNode>> createUser(@RequestBody CreateUserRequest request) {
        try {
            log.info("Creating user: {}", request.getEmail());
            JsonNode user = graphApiService.createUser(
                    request.getEmail(),
                    request.getDisplayName(),
                    request.getPassword()
            );
            return ResponseEntity.ok(ApiResponse.success("User created successfully", user));
        } catch (Exception e) {
            log.error("Failed to create user", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
    
    /**
     * Get user by email
     */
    @GetMapping("/{email}")
    public ResponseEntity<ApiResponse<JsonNode>> getUser(@PathVariable String email) {
        try {
            log.info("Getting user: {}", email);
            JsonNode user = graphApiService.getUserByEmail(email);
            
            if (user == null) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(ApiResponse.success(user));
        } catch (Exception e) {
            log.error("Failed to get user", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
    
    /**
     * Delete user by email
     */
    @DeleteMapping("/{email}")
    public ResponseEntity<ApiResponse<String>> deleteUser(@PathVariable String email) {
        try {
            log.info("Deleting user: {}", email);
            
            // First get user ID
            JsonNode user = graphApiService.getUserByEmail(email);
            if (user == null) {
                return ResponseEntity.notFound().build();
            }
            
            String userId = user.get("id").asText();
            graphApiService.deleteUser(userId);
            
            return ResponseEntity.ok(ApiResponse.success("User deleted successfully", null));
        } catch (Exception e) {
            log.error("Failed to delete user", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
    
    /**
     * Disable user
     */
    @PatchMapping("/{email}/disable")
    public ResponseEntity<ApiResponse<String>> disableUser(@PathVariable String email) {
        try {
            log.info("Disabling user: {}", email);
            
            JsonNode user = graphApiService.getUserByEmail(email);
            if (user == null) {
                return ResponseEntity.notFound().build();
            }
            
            String userId = user.get("id").asText();
            graphApiService.setUserEnabled(userId, false);
            
            return ResponseEntity.ok(ApiResponse.success("User disabled successfully", null));
        } catch (Exception e) {
            log.error("Failed to disable user", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
    
    /**
     * Enable user
     */
    @PatchMapping("/{email}/enable")
    public ResponseEntity<ApiResponse<String>> enableUser(@PathVariable String email) {
        try {
            log.info("Enabling user: {}", email);
            
            JsonNode user = graphApiService.getUserByEmail(email);
            if (user == null) {
                return ResponseEntity.notFound().build();
            }
            
            String userId = user.get("id").asText();
            graphApiService.setUserEnabled(userId, true);
            
            return ResponseEntity.ok(ApiResponse.success("User enabled successfully", null));
        } catch (Exception e) {
            log.error("Failed to enable user", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
    
    /**
     * Force password reset for user
     */
    @PostMapping("/{email}/force-password-reset")
    public ResponseEntity<ApiResponse<String>> forcePasswordReset(@PathVariable String email) {
        try {
            log.info("Setting force password reset for user: {}", email);
            
            JsonNode user = graphApiService.getUserByEmail(email);
            if (user == null) {
                return ResponseEntity.notFound().build();
            }
            
            String userId = user.get("id").asText();
            graphApiService.forcePasswordReset(userId);
            
            return ResponseEntity.ok(ApiResponse.success("Force password reset set successfully", null));
        } catch (Exception e) {
            log.error("Failed to set force password reset", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
