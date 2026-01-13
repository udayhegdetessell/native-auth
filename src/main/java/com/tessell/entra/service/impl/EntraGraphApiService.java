package com.tessell.entra.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tessell.entra.config.EntraExternalIdConfig;
import com.tessell.entra.service.GraphApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class EntraGraphApiService implements GraphApiService {
    
    private final EntraExternalIdConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private static final MediaType JSON = MediaType.parse("application/json");
    
    private String accessToken;
    private long tokenExpiresAt = 0;
    
    /**
     * Get access token for Graph API using client credentials
     */
    private String getAccessToken() throws IOException {
        if (accessToken != null && System.currentTimeMillis() < tokenExpiresAt) {
            return accessToken;
        }
        
        String url = config.getGraphTokenEndpoint();
        
        String formBody = String.format(
                "client_id=%s&client_secret=%s&scope=https://graph.microsoft.com/.default&grant_type=client_credentials",
                config.getClientId(),
                config.getClientSecret()
        );
        
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(formBody, MediaType.parse("application/x-www-form-urlencoded")))
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                log.error("Failed to get Graph API token. Status: {}, Response: {}", response.code(), responseBody);
                throw new RuntimeException("Failed to get Graph API token: " + responseBody);
            }

            JsonNode json = objectMapper.readTree(responseBody);
            accessToken = json.get("access_token").asText();
            int expiresIn = json.get("expires_in").asInt();
            tokenExpiresAt = System.currentTimeMillis() + (expiresIn - 60) * 1000L;

            log.info("Obtained Graph API access token successfully");
            return accessToken;
        }
    }
    
    /**
     * Create a user in Entra External ID using Graph API
     */
    public JsonNode createUser(String email, String displayName, String password) throws IOException {
        String url = config.getGraphApiBaseUrl() + "/users";

        // For Entra External ID, userPrincipalName must use the tenant domain
        String mailNickname = email.split("@")[0].replaceAll("[^a-zA-Z0-9]", "");
        String userPrincipalName = mailNickname + "@" + config.getTenantSubdomain() + ".onmicrosoft.com";

        ObjectNode body = objectMapper.createObjectNode();
        body.put("accountEnabled", true);
        body.put("displayName", displayName);
        body.put("mailNickname", mailNickname);
        body.put("userPrincipalName", userPrincipalName);
        
        // Password profile
        ObjectNode passwordProfile = objectMapper.createObjectNode();
        passwordProfile.put("password", password);
        passwordProfile.put("forceChangePasswordNextSignIn", false);
        body.set("passwordProfile", passwordProfile);
        
        // Identities for external ID
        ArrayNode identities = objectMapper.createArrayNode();
        ObjectNode identity = objectMapper.createObjectNode();
        identity.put("signInType", "emailAddress");
        identity.put("issuer", config.getTenantSubdomain() + ".onmicrosoft.com");
        identity.put("issuerAssignedId", email);
        identities.add(identity);
        body.set("identities", identities);
        
        log.info("Creating user: {}", email);
        
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body.toString(), JSON))
                .addHeader("Authorization", "Bearer " + getAccessToken())
                .addHeader("Content-Type", "application/json")
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            log.debug("Create user response: {}", responseBody);
            
            if (!response.isSuccessful()) {
                throw new RuntimeException("Failed to create user: " + responseBody);
            }
            
            return objectMapper.readTree(responseBody);
        }
    }
    
    /**
     * Get user by email
     */
    public JsonNode getUserByEmail(String email) throws IOException {
        // First try to find by identities (for users created via Native Auth or Graph API)
        String issuer = config.getTenantSubdomain() + ".onmicrosoft.com";
        String url = config.getGraphApiBaseUrl() + "/users?$filter=identities/any(c:c/issuerAssignedId eq '" + email + "' and c/issuer eq '" + issuer + "')&$select=id,displayName,identities,userPrincipalName,mail";

        log.debug("Searching for user by email: {} with issuer: {}", email, issuer);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", "Bearer " + getAccessToken())
                .addHeader("ConsistencyLevel", "eventual")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            log.debug("Get user response: {}", responseBody);

            if (!response.isSuccessful()) {
                throw new RuntimeException("Failed to get user: " + responseBody);
            }

            JsonNode result = objectMapper.readTree(responseBody);
            JsonNode users = result.get("value");

            if (users != null && users.size() > 0) {
                log.debug("Found user by identities: {}", users.get(0));
                return users.get(0);
            }

            log.debug("User not found by identities, trying mail/userPrincipalName");

            // Fallback: try by mail or userPrincipalName
            url = config.getGraphApiBaseUrl() + "/users?$filter=mail eq '" + email + "' or userPrincipalName eq '" + email + "'";
            request = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Authorization", "Bearer " + getAccessToken())
                    .build();

            try (Response response2 = httpClient.newCall(request).execute()) {
                responseBody = response2.body() != null ? response2.body().string() : "";

                if (!response2.isSuccessful()) {
                    throw new RuntimeException("Failed to get user: " + responseBody);
                }

                result = objectMapper.readTree(responseBody);
                users = result.get("value");

                if (users != null && users.size() > 0) {
                    log.debug("Found user by mail/userPrincipalName: {}", users.get(0));
                    return users.get(0);
                }
            }

            log.debug("User not found: {}", email);
            return null;
        }
    }

    /**
     * Get user ID by email
     */
    @Override
    public String getUserIdByEmail(String email) throws IOException {
        JsonNode user = getUserByEmail(email);
        if (user != null && user.has("id")) {
            return user.get("id").asText();
        }
        return null;
    }
    
    /**
     * Delete user by ID
     */
    public void deleteUser(String userId) throws IOException {
        String url = config.getGraphApiBaseUrl() + "/users/" + userId;
        
        log.info("Deleting user: {}", userId);
        
        Request request = new Request.Builder()
                .url(url)
                .delete()
                .addHeader("Authorization", "Bearer " + getAccessToken())
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                throw new RuntimeException("Failed to delete user: " + responseBody);
            }
            log.info("User deleted successfully: {}", userId);
        }
    }
    
    /**
     * Enable or disable user
     */
    public void setUserEnabled(String userId, boolean enabled) throws IOException {
        String url = config.getGraphApiBaseUrl() + "/users/" + userId;
        
        ObjectNode body = objectMapper.createObjectNode();
        body.put("accountEnabled", enabled);
        
        log.info("{} user: {}", enabled ? "Enabling" : "Disabling", userId);
        
        Request request = new Request.Builder()
                .url(url)
                .patch(RequestBody.create(body.toString(), JSON))
                .addHeader("Authorization", "Bearer " + getAccessToken())
                .addHeader("Content-Type", "application/json")
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                throw new RuntimeException("Failed to update user: " + responseBody);
            }
            log.info("User {} successfully: {}", enabled ? "enabled" : "disabled", userId);
        }
    }
    
    /**
     * Force password reset for user (sets forceChangePasswordNextSignIn)
     */
    public void forcePasswordReset(String userId) throws IOException {
        String url = config.getGraphApiBaseUrl() + "/users/" + userId;

        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode passwordProfile = objectMapper.createObjectNode();
        passwordProfile.put("forceChangePasswordNextSignIn", true);
        body.set("passwordProfile", passwordProfile);

        log.info("Setting force password reset for user: {}", userId);

        Request request = new Request.Builder()
                .url(url)
                .patch(RequestBody.create(body.toString(), JSON))
                .addHeader("Authorization", "Bearer " + getAccessToken())
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                throw new RuntimeException("Failed to set force password reset: " + responseBody);
            }
            log.info("Force password reset set for user: {}", userId);
        }
    }

    /**
     * Update user extension attribute using Microsoft Graph Open Extensions
     * This approach doesn't require SharePoint Online license
     * Reference: https://learn.microsoft.com/en-us/graph/extensibility-open-users
     */
    @Override
    public void updateUserExtensionAttribute(String userId, String attributeName, String attributeValue) throws IOException {
        String extensionName = "com.tessell.auth";
        String url = config.getGraphApiBaseUrl() + "/users/" + userId + "/extensions/" + extensionName;

        // First, try to get existing extension
        JsonNode existingExtension = null;
        try {
            existingExtension = getOpenExtension(userId, extensionName);
        } catch (Exception e) {
            log.debug("No existing extension found, will create new one");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("@odata.type", "microsoft.graph.openTypeExtension");
        body.put("extensionName", extensionName);

        // If extension exists, copy all existing attributes
        if (existingExtension != null) {
            existingExtension.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                // Skip metadata fields
                if (!key.startsWith("@odata") && !key.equals("id") && !key.equals("extensionName")) {
                    body.set(key, entry.getValue());
                }
            });
        }

        // Add/update the new attribute
        body.put(attributeName, attributeValue);

        log.info("Updating extension attribute {} for user: {}", attributeName, userId);

        Request request;
        if (existingExtension != null) {
            // Update existing extension using PATCH
            request = new Request.Builder()
                    .url(url)
                    .patch(RequestBody.create(body.toString(), JSON))
                    .addHeader("Authorization", "Bearer " + getAccessToken())
                    .addHeader("Content-Type", "application/json")
                    .build();
        } else {
            // Create new extension using POST
            String createUrl = config.getGraphApiBaseUrl() + "/users/" + userId + "/extensions";
            request = new Request.Builder()
                    .url(createUrl)
                    .post(RequestBody.create(body.toString(), JSON))
                    .addHeader("Authorization", "Bearer " + getAccessToken())
                    .addHeader("Content-Type", "application/json")
                    .build();
        }

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                log.error("Failed to update extension attribute. Status: {}, Response: {}", response.code(), responseBody);
                throw new RuntimeException("Failed to update extension attribute: " + responseBody);
            }
            log.info("Extension attribute {} updated for user: {}", attributeName, userId);
        }
    }

    /**
     * Get user extension attribute from Open Extensions
     */
    @Override
    public String getUserExtensionAttribute(String userId, String attributeName) throws IOException {
        String extensionName = "com.tessell.auth";

        try {
            JsonNode extension = getOpenExtension(userId, extensionName);
            if (extension != null && extension.has(attributeName)) {
                return extension.get(attributeName).asText();
            }
        } catch (Exception e) {
            log.warn("Failed to get extension attribute {}: {}", attributeName, e.getMessage());
        }

        return null;
    }

    /**
     * Get Open Extension by name
     */
    private JsonNode getOpenExtension(String userId, String extensionName) throws IOException {
        String url = config.getGraphApiBaseUrl() + "/users/" + userId + "/extensions/" + extensionName;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", "Bearer " + getAccessToken())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                if (response.code() == 404) {
                    return null; // Extension doesn't exist yet
                }
                log.error("Failed to get open extension. Status: {}, Response: {}", response.code(), responseBody);
                throw new RuntimeException("Failed to get open extension: " + responseBody);
            }

            return objectMapper.readTree(responseBody);
        }
    }

    /**
     * Get user by ID
     */
    private JsonNode getUserById(String userId) throws IOException {
        String url = config.getGraphApiBaseUrl() + "/users/" + userId + "?$select=id,displayName,mail,userPrincipalName";

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", "Bearer " + getAccessToken())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                log.error("Failed to get user by ID. Status: {}, Response: {}", response.code(), responseBody);
                throw new RuntimeException("Failed to get user: " + responseBody);
            }

            return objectMapper.readTree(responseBody);
        }
    }
}
