package com.tessell.entra.controller;

import com.tessell.entra.dto.request.LoginRequest;
import com.tessell.entra.dto.response.ApiResponse;
import com.tessell.entra.dto.response.TokenResponse;
import com.tessell.entra.service.HybridAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Hybrid Authentication Controller
 * 
 * Provides endpoints for authentication without browser redirect.
 * Uses hybrid approach:
 * - Sign-up: Native Auth API (OTP verification)
 * - Sign-in: Custom JWT tokens (avoid refresh token bug)
 * - Refresh: Custom JWT tokens (30-90 day lifetime)
 * 
 * This controller provides the solution to switch from Azure AD B2C
 * to Entra External ID without browser redirect.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/hybrid-auth")
public class HybridAuthController {
    
    @Autowired
    private HybridAuthService hybridAuthService;
    
    @PostMapping("/signup/start")
    public ResponseEntity<ApiResponse> signUpStart(
            @RequestParam String email,
            @RequestParam String password) {
        
        log.info("Sign-up start request for email: {}", email);
        
        try {
            String continuationToken = hybridAuthService.signUpStart(email, password);
            
            return ResponseEntity.ok(
                ApiResponse.success(Map.of(
                    "continuationToken", continuationToken,
                    "message", "OTP sent to your email"
                ))
            );
            
        } catch (Exception e) {
            log.error("Sign-up start failed for email: {}", email, e);
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
        }
    }
    
    @PostMapping("/signup/complete")
    public ResponseEntity<ApiResponse> signUpComplete(
            @RequestParam String continuationToken,
            @RequestParam String otp,
            @RequestParam String displayName) {

        log.info("Sign-up complete request");

        try {
            TokenResponse tokens = hybridAuthService.signUpComplete(
                continuationToken, otp, displayName);

            return ResponseEntity.ok(ApiResponse.success(tokens));

        } catch (Exception e) {
            log.error("Sign-up complete failed", e);
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Sign-up without OTP verification (bypasses email verification)
     * Creates user directly and returns JWT token
     *
     * Use this endpoint when email verification is not required
     */
    @PostMapping("/signup/no-otp")
    public ResponseEntity<ApiResponse> signUpWithoutOtp(
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam(defaultValue = "User") String displayName) {

        log.info("Sign-up without OTP request for email: {}", email);

        try {
            TokenResponse tokens = hybridAuthService.signUpWithoutOtp(
                email, password, displayName);

            return ResponseEntity.ok(ApiResponse.success(tokens));

        } catch (Exception e) {
            log.error("Sign-up without OTP failed for email: {}", email, e);
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/signin")
    public ResponseEntity<ApiResponse> signIn(@RequestBody LoginRequest request) {
        log.info("Sign-in request for email: {}", request.getEmail());
        
        try {
            TokenResponse tokens = hybridAuthService.signIn(request);
            
            return ResponseEntity.ok(ApiResponse.success(tokens));
            
        } catch (Exception e) {
            log.error("Sign-in failed for email: {}", request.getEmail(), e);
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid credentials"));
        }
    }
    
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse> refreshToken(
            @RequestHeader("Authorization") String authHeader) {
        
        log.info("Token refresh request");
        
        try {
            String token = extractToken(authHeader);
            TokenResponse newTokens = hybridAuthService.refreshToken(token);
            
            return ResponseEntity.ok(ApiResponse.success(newTokens));
            
        } catch (Exception e) {
            log.error("Token refresh failed", e);
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid or expired token"));
        }
    }
    
    @GetMapping("/validate")
    public ResponseEntity<ApiResponse> validateToken(
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = extractToken(authHeader);
            String userId = hybridAuthService.validateToken(token);
            
            return ResponseEntity.ok(
                ApiResponse.success(Map.of(
                    "userId", userId,
                    "valid", true
                ))
            );
            
        } catch (Exception e) {
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid token"));
        }
    }
    
    /**
     * TEST ONLY: Delete user by email
     * This is for testing purposes only
     *
     * WARNING: Remove this endpoint in production!
     */
    @DeleteMapping("/test/delete-user")
    public ResponseEntity<ApiResponse> deleteUserForTesting(@RequestParam String email) {
        try {
            log.warn("TEST ENDPOINT: Deleting user: {}", email);

            hybridAuthService.deleteUserByEmail(email);

            return ResponseEntity.ok(
                ApiResponse.success(Map.of("message", "User deleted successfully: " + email))
            );

        } catch (Exception e) {
            log.error("Failed to delete user: {}", email, e);
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * TEST ONLY: Create user and get token without OTP verification
     * This bypasses email verification for testing purposes
     *
     * WARNING: Remove this endpoint in production!
     */
    @PostMapping("/test/create-user")
    public ResponseEntity<ApiResponse> createUserForTesting(
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam(defaultValue = "Test User") String displayName) {
        try {
            log.warn("TEST ENDPOINT: Creating user without OTP verification: {}", email);

            // Generate a test user ID
            String userId = "test-user-" + System.currentTimeMillis();

            // Generate JWT token directly (bypassing OTP)
            TokenResponse token = hybridAuthService.generateTestToken(userId, displayName, email);

            log.info("TEST: User token created successfully for: {}", email);

            return ResponseEntity.ok(ApiResponse.success(
                "Test user token created (OTP bypassed - FOR TESTING ONLY)",
                token
            ));
        } catch (Exception e) {
            log.error("Failed to create test user token", e);
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Failed to create test user: " + e.getMessage()));
        }
    }

    /**
     * Start password reset flow
     * Sends OTP to user's email for password reset
     */
    @PostMapping("/password-reset/start")
    public ResponseEntity<ApiResponse> passwordResetStart(@RequestParam String email) {
        log.info("Password reset start request for email: {}", email);

        try {
            String continuationToken = hybridAuthService.passwordResetStart(email);

            return ResponseEntity.ok(
                ApiResponse.success(Map.of(
                    "continuationToken", continuationToken,
                    "message", "Password reset code sent to your email"
                ))
            );

        } catch (Exception e) {
            log.error("Password reset start failed for email: {}", email, e);
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Complete password reset flow
     * Verifies OTP and updates password
     */
    @PostMapping("/password-reset/complete")
    public ResponseEntity<ApiResponse> passwordResetComplete(
            @RequestParam String continuationToken,
            @RequestParam String otp,
            @RequestParam String newPassword) {

        log.info("Password reset complete request");

        try {
            hybridAuthService.passwordResetComplete(continuationToken, otp, newPassword);

            return ResponseEntity.ok(
                ApiResponse.success(Map.of("message", "Password reset successful"))
            );

        } catch (Exception e) {
            log.error("Password reset complete failed", e);
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
        }
    }

    private String extractToken(String authHeader) throws Exception {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new Exception("Invalid Authorization header");
        }
        return authHeader.substring(7);
    }
}

