package com.tessell.entra.service;

import com.tessell.entra.dto.request.LoginRequest;
import com.tessell.entra.dto.response.TokenResponse;

/**
 * Hybrid Authentication Service
 * 
 * Combines Entra External ID Native Auth (for sign-up) with custom JWT tokens
 * to avoid the refresh token bug while maintaining "no browser redirect" requirement.
 * 
 * Flow:
 * 1. Sign-Up: Use Native Auth API (OTP verification, no redirect)
 * 2. Sign-In: Verify password via Graph API (no redirect)
 * 3. Tokens: Generate custom JWT tokens (control lifetime)
 * 4. Refresh: Validate and reissue JWT tokens (no Microsoft dependency)
 */
public interface HybridAuthService {
    
    /**
     * Start sign-up flow
     * Sends OTP to user's email via Native Auth API
     * 
     * @param email User's email address
     * @param password User's password
     * @return Continuation token for completing sign-up
     */
    String signUpStart(String email, String password) throws Exception;
    
    /**
     * Complete sign-up flow
     * Verifies OTP and creates user in Entra
     * Returns custom JWT token (not Microsoft's refresh token)
     * 
     * @param continuationToken Token from signUpStart
     * @param otp OTP received via email
     * @param displayName User's display name
     * @return Custom JWT token with configurable lifetime
     */
    TokenResponse signUpComplete(String continuationToken, String otp, String displayName) 
        throws Exception;
    
    /**
     * Sign in existing user
     * Verifies password using Graph API (no browser redirect)
     * Returns custom JWT token (not Microsoft's refresh token)
     * 
     * @param request Login request with email and password
     * @return Custom JWT token with configurable lifetime
     */
    TokenResponse signIn(LoginRequest request) throws Exception;
    
    /**
     * Refresh custom JWT token
     * Validates existing token and issues new one
     * No dependency on Microsoft's refresh token
     * 
     * @param token Current JWT token
     * @return New JWT token
     */
    TokenResponse refreshToken(String token) throws Exception;
    
    /**
     * Validate JWT token
     *
     * @param token JWT token to validate
     * @return User ID if valid
     */
    String validateToken(String token) throws Exception;

    /**
     * TEST ONLY: Generate JWT token without OTP verification
     * For testing when OTP emails are not being delivered
     *
     * @param userId User ID
     * @param displayName User's display name
     * @param email User's email
     * @return JWT token
     */
    TokenResponse generateTestToken(String userId, String displayName, String email) throws Exception;
}

