package com.tessell.entra.service.impl;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tessell.entra.config.EntraExternalIdConfig;
import com.tessell.entra.dto.request.LoginRequest;
import com.tessell.entra.dto.response.NativeAuthResponse;
import com.tessell.entra.dto.response.TokenResponse;
import com.tessell.entra.service.GraphApiService;
import com.tessell.entra.service.HybridAuthService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hybrid Authentication Implementation
 *
 * This implementation combines:
 * 1. Entra External ID Native Auth (for sign-up with OTP)
 * 2. Microsoft Graph API (for user management)
 * 3. Custom JWT tokens (to avoid refresh token bug)
 * 4. BCrypt password hashing (for password verification)
 *
 * Benefits:
 * - ✅ No browser redirect
 * - ✅ Control token lifetime (avoid 12-24h bug)
 * - ✅ OTP verification for sign-up
 * - ✅ Direct password verification for sign-in
 */
@Slf4j
@Service
public class HybridAuthServiceImpl implements HybridAuthService {

    private static final MediaType FORM_URL_ENCODED = MediaType.parse("application/x-www-form-urlencoded");
    private static final MediaType JSON = MediaType.parse("application/json");

    @Autowired
    private OkHttpClient httpClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntraExternalIdConfig config;

    @Autowired
    private GraphApiService graphApiService;

    @Value("${jwt.secret:your-secret-key-change-this-in-production}")
    private String jwtSecret;

    @Value("${jwt.issuer:tessell-iam}")
    private String jwtIssuer;

    @Value("${jwt.token.lifetime.days:30}")
    private int tokenLifetimeDays;

    // Temporary cache to store email and password during sign-up flow
    // Key: continuation token, Value: SignUpData (email + password)
    private final Map<String, SignUpData> signUpCache = new ConcurrentHashMap<>();

    // Inner class to store sign-up data
    private static class SignUpData {
        final String email;
        final String password;

        SignUpData(String email, String password) {
            this.email = email;
            this.password = password;
        }
    }

    private Algorithm getJwtAlgorithm() {
        return Algorithm.HMAC256(jwtSecret);
    }

    @Override
    public String signUpStart(String email, String password) throws Exception {
        log.info("Starting sign-up for email: {}", email);

        try {
            // Step 1: Start sign-up
            String url = config.getNativeAuthBaseUrl() + "/signup/v1.0/start";
            String formBody = String.format(
                "client_id=%s&challenge_type=oob%%20password%%20redirect&username=%s&password=%s",
                config.getClientId(), email, password
            );

            Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(formBody, FORM_URL_ENCODED))
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();

            NativeAuthResponse response;
            try (Response httpResponse = httpClient.newCall(request).execute()) {
                String responseBody = httpResponse.body() != null ? httpResponse.body().string() : "";
                response = objectMapper.readValue(responseBody, NativeAuthResponse.class);
            }

            if (response.getError() != null) {
                throw new Exception("Sign-up start failed: " + response.getErrorDescription());
            }

            // Step 2: Call challenge to trigger OTP email
            log.info("Calling challenge to trigger OTP email");
            url = config.getNativeAuthBaseUrl() + "/signup/v1.0/challenge";
            formBody = String.format(
                "client_id=%s&continuation_token=%s&challenge_type=oob",
                config.getClientId(), response.getContinuationToken()
            );

            request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(formBody, FORM_URL_ENCODED))
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();

            NativeAuthResponse challengeResponse;
            try (Response httpResponse = httpClient.newCall(request).execute()) {
                String responseBody = httpResponse.body() != null ? httpResponse.body().string() : "";
                challengeResponse = objectMapper.readValue(responseBody, NativeAuthResponse.class);
            }

            if (challengeResponse.getError() != null) {
                throw new Exception("Challenge failed: " + challengeResponse.getErrorDescription());
            }

            // Store email and password temporarily for use in signUpComplete
            signUpCache.put(challengeResponse.getContinuationToken(), new SignUpData(email, password));

            log.info("Sign-up started successfully, OTP sent to: {}", email);
            return challengeResponse.getContinuationToken();

        } catch (IOException e) {
            log.error("Failed to start sign-up for email: {}", email, e);
            throw new Exception("Failed to start sign-up: " + e.getMessage());
        }
    }

    @Override
    public TokenResponse signUpComplete(String continuationToken, String otp, String displayName)
            throws Exception {
        log.info("Completing sign-up with OTP");

        try {
            // Step 1: Submit OTP
            log.info("Step 1: Submitting OTP");
            String url = config.getNativeAuthBaseUrl() + "/signup/v1.0/continue";
            String formBody = String.format(
                "client_id=%s&continuation_token=%s&grant_type=oob&oob=%s",
                config.getClientId(), continuationToken, otp
            );

            Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(formBody, FORM_URL_ENCODED))
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();

            NativeAuthResponse otpResponse;
            try (Response httpResponse = httpClient.newCall(request).execute()) {
                String responseBody = httpResponse.body() != null ? httpResponse.body().string() : "";
                otpResponse = objectMapper.readValue(responseBody, NativeAuthResponse.class);
            }

            // Check if attributes are required (this is expected)
            if ("attributes_required".equals(otpResponse.getError())) {
                log.info("OTP verified, attributes required");
            } else if (otpResponse.getError() != null) {
                throw new Exception("OTP verification failed: " + otpResponse.getErrorDescription());
            }

            // Step 2: Submit attributes (displayName)
            log.info("Step 2: Submitting displayName attribute");
            url = config.getNativeAuthBaseUrl() + "/signup/v1.0/continue";
            formBody = String.format(
                "client_id=%s&continuation_token=%s&grant_type=attributes&attributes={\"displayName\":\"%s\"}",
                config.getClientId(), otpResponse.getContinuationToken(), displayName != null ? displayName : "User"
            );

            request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(formBody, FORM_URL_ENCODED))
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();

            NativeAuthResponse attributesResponse;
            try (Response httpResponse = httpClient.newCall(request).execute()) {
                String responseBody = httpResponse.body() != null ? httpResponse.body().string() : "";
                attributesResponse = objectMapper.readValue(responseBody, NativeAuthResponse.class);
            }

            if (attributesResponse.getError() != null) {
                throw new Exception("Attributes submission failed: " + attributesResponse.getErrorDescription());
            }

            // Step 3: Get tokens
            log.info("Step 3: Getting tokens");
            url = config.getNativeAuthBaseUrl() + "/oauth2/v2.0/token";
            formBody = String.format(
                "client_id=%s&grant_type=continuation_token&continuation_token=%s&scope=openid%%20profile%%20email%%20offline_access",
                config.getClientId(), attributesResponse.getContinuationToken()
            );

            request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(formBody, FORM_URL_ENCODED))
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();

            TokenResponse nativeTokens;
            try (Response httpResponse = httpClient.newCall(request).execute()) {
                String responseBody = httpResponse.body() != null ? httpResponse.body().string() : "";
                nativeTokens = objectMapper.readValue(responseBody, TokenResponse.class);
            }

            log.info("Sign-up completed successfully via Native Auth");

            // Retrieve email and password from cache
            SignUpData signUpData = signUpCache.remove(continuationToken);
            if (signUpData == null) {
                throw new Exception("Sign-up session expired or invalid");
            }

            String email = signUpData.email;
            String password = signUpData.password;

            // Extract user ID from the ID token (sub claim)
            String userId = null;
            if (nativeTokens.getIdToken() != null) {
                try {
                    userId = extractUserIdFromIdToken(nativeTokens.getIdToken());
                    log.info("Extracted user ID from ID token: {} for email: {}", userId, email);
                } catch (Exception e) {
                    log.warn("Failed to extract user ID from ID token: {}", e.getMessage());
                }
            }

            // If we couldn't get userId from ID token, try Graph API as fallback with retry
            if (userId == null) {
                int maxRetries = 3;
                int retryDelayMs = 2000; // 2 seconds

                for (int attempt = 1; attempt <= maxRetries && userId == null; attempt++) {
                    try {
                        if (attempt > 1) {
                            log.info("Retry attempt {} to get user ID from Graph API for email: {}", attempt, email);
                            Thread.sleep(retryDelayMs);
                        }
                        userId = graphApiService.getUserIdByEmail(email);
                        if (userId != null) {
                            log.info("Found user ID from Graph API: {} for email: {} (attempt {})", userId, email, attempt);
                        }
                    } catch (Exception e) {
                        log.warn("Attempt {} failed to get user ID from Graph API for email: {}", attempt, email, e);
                        if (attempt == maxRetries) {
                            log.error("All {} attempts failed to get user ID from Graph API for email: {}", maxRetries, email);
                        }
                    }
                }
            }

            // Store BCrypt password hash
            if (password != null && userId != null) {
                try {
                    log.info("Storing BCrypt password hash for user: {}", email);
                    String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(12));
                    graphApiService.updateUserExtensionAttribute(userId, "passwordHash", passwordHash);
                    log.info("Password hash stored successfully for user: {}", email);
                } catch (Exception e) {
                    log.error("Failed to store password hash for user: {}", email, e);
                    // Don't fail the sign-up if password hash storage fails
                }
            } else {
                log.warn("Cannot store password hash - userId: {}, password: {}", userId, password != null ? "present" : "null");
            }

            // Generate custom JWT token (NOT using Microsoft's refresh token)
            // Use userId if available, otherwise generate a temporary one
            String tokenUserId = userId != null ? userId : "user-" + System.currentTimeMillis();
            String jwtToken = generateJwtToken(tokenUserId, displayName, email);

            return TokenResponse.builder()
                .accessToken(jwtToken)
                .tokenType("Bearer")
                .expiresIn(tokenLifetimeDays * 24 * 3600) // Convert days to seconds
                .build();

        } catch (IOException e) {
            log.error("Failed to complete sign-up", e);
            throw new Exception("Failed to complete sign-up: " + e.getMessage());
        }
    }

    @Override
    public TokenResponse signIn(LoginRequest request) throws Exception {
        log.info("Signing in user: {}", request.getEmail());

        try {
            // Step 1: Get user from Entra using Graph API
            log.info("Retrieving user from Entra: {}", request.getEmail());
            JsonNode user = graphApiService.getUserByEmail(request.getEmail());

            if (user == null) {
                log.error("User not found: {}", request.getEmail());
                throw new Exception("Invalid credentials");
            }

            String userId = user.get("id").asText();
            String displayName = user.has("displayName") ? user.get("displayName").asText() : "User";

            // Step 2: Retrieve and verify password hash
            log.info("Verifying password for user: {}", request.getEmail());
            String storedPasswordHash = graphApiService.getUserExtensionAttribute(userId, "passwordHash");

            if (storedPasswordHash == null || storedPasswordHash.isEmpty()) {
                log.error("No password hash found for user: {}", request.getEmail());
                throw new Exception("Invalid credentials");
            }

            // Step 3: Verify password using BCrypt
            if (!BCrypt.checkpw(request.getPassword(), storedPasswordHash)) {
                log.error("Password verification failed for user: {}", request.getEmail());
                throw new Exception("Invalid credentials");
            }

            log.info("Password verified successfully for user: {}", request.getEmail());

            // Step 4: Generate custom JWT token
            String jwtToken = generateJwtToken(userId, displayName, request.getEmail());

            log.info("Sign-in successful for user: {}", request.getEmail());

            return TokenResponse.builder()
                .accessToken(jwtToken)
                .tokenType("Bearer")
                .expiresIn(tokenLifetimeDays * 24 * 3600)
                .build();

        } catch (IOException e) {
            log.error("Failed to sign in user: {}", request.getEmail(), e);
            throw new Exception("Sign-in failed: " + e.getMessage());
        }
    }

    @Override
    public TokenResponse refreshToken(String token) throws Exception {
        log.info("Refreshing JWT token");

        try {
            // Validate existing token and extract claims
            DecodedJWT jwt = JWT.require(getJwtAlgorithm())
                .withIssuer(jwtIssuer)
                .build()
                .verify(token);

            String userId = jwt.getSubject();
            String displayName = jwt.getClaim("name").asString();
            String email = jwt.getClaim("email").asString();

            // Generate new JWT token with same claims
            String newJwtToken = generateJwtToken(userId, displayName, email);

            log.info("Token refreshed successfully for user: {}", userId);

            return TokenResponse.builder()
                .accessToken(newJwtToken)
                .tokenType("Bearer")
                .expiresIn(tokenLifetimeDays * 24 * 3600)
                .build();

        } catch (Exception e) {
            log.error("Failed to refresh token", e);
            throw new Exception("Token refresh failed: " + e.getMessage());
        }
    }

    @Override
    public String validateToken(String token) throws Exception {
        try {
            DecodedJWT jwt = JWT.require(getJwtAlgorithm())
                .withIssuer(jwtIssuer)
                .build()
                .verify(token);

            return jwt.getSubject(); // Returns user ID

        } catch (JWTVerificationException e) {
            log.error("Token validation failed", e);
            throw new Exception("Invalid token: " + e.getMessage());
        }
    }

    @Override
    public TokenResponse generateTestToken(String userId, String displayName, String email) throws Exception {
        log.warn("Generating test token (OTP bypassed) for: {}", email);

        String jwtToken = generateJwtToken(userId, displayName, email);

        return TokenResponse.builder()
            .accessToken(jwtToken)
            .tokenType("Bearer")
            .expiresIn(tokenLifetimeDays * 24 * 3600)
            .build();
    }

    // Private helper methods

    private String generateJwtToken(String userId, String displayName, String email) {
        Instant now = Instant.now();
        Instant expiry = now.plus(tokenLifetimeDays, ChronoUnit.DAYS);

        return JWT.create()
            .withIssuer(jwtIssuer)
            .withSubject(userId)
            .withClaim("name", displayName)
            .withClaim("email", email)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(expiry))
            .sign(getJwtAlgorithm());
    }

    private String extractEmailFromIdToken(String idToken) {
        try {
            DecodedJWT jwt = JWT.decode(idToken);
            String email = jwt.getClaim("email").asString();
            if (email == null || email.isEmpty()) {
                email = jwt.getClaim("preferred_username").asString();
            }
            return email != null ? email : "unknown@example.com";
        } catch (Exception e) {
            log.warn("Failed to extract email from ID token", e);
            return "unknown@example.com";
        }
    }

    private String extractUserIdFromIdToken(String idToken) {
        try {
            DecodedJWT jwt = JWT.decode(idToken);
            String userId = jwt.getClaim("oid").asString();
            if (userId == null || userId.isEmpty()) {
                userId = jwt.getSubject();
            }
            return userId != null ? userId : "user-" + System.currentTimeMillis();
        } catch (Exception e) {
            log.warn("Failed to extract user ID from ID token", e);
            return "user-" + System.currentTimeMillis();
        }
    }

    private String extractDisplayNameFromIdToken(String idToken) {
        try {
            DecodedJWT jwt = JWT.decode(idToken);
            String displayName = jwt.getClaim("name").asString();
            if (displayName == null || displayName.isEmpty()) {
                displayName = jwt.getClaim("given_name").asString();
            }
            return displayName != null ? displayName : "User";
        } catch (Exception e) {
            log.warn("Failed to extract display name from ID token", e);
            return "User";
        }
    }

}

