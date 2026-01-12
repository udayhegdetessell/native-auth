package com.tessell.entra.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * Interface for Microsoft Graph API operations
 */
public interface GraphApiService {
    
    /**
     * Create a user in Entra External ID using Graph API
     *
     * @param email the user's email address
     * @param displayName the user's display name
     * @param password the user's password
     * @return JsonNode containing the created user details
     * @throws IOException if the API call fails
     */
    JsonNode createUser(String email, String displayName, String password) throws IOException;
    
    /**
     * Get user by email address
     *
     * @param email the user's email address
     * @return JsonNode containing the user details, or null if not found
     * @throws IOException if the API call fails
     */
    JsonNode getUserByEmail(String email) throws IOException;

    /**
     * Get user ID by email address
     *
     * @param email the user's email address
     * @return the user's ID, or null if not found
     * @throws IOException if the API call fails
     */
    String getUserIdByEmail(String email) throws IOException;
    
    /**
     * Delete user by ID
     *
     * @param userId the user's ID
     * @throws IOException if the API call fails
     */
    void deleteUser(String userId) throws IOException;
    
    /**
     * Enable or disable user account
     *
     * @param userId the user's ID
     * @param enabled true to enable, false to disable
     * @throws IOException if the API call fails
     */
    void setUserEnabled(String userId, boolean enabled) throws IOException;
    
    /**
     * Force password reset for user (sets forceChangePasswordNextSignIn)
     *
     * @param userId the user's ID
     * @throws IOException if the API call fails
     */
    void forcePasswordReset(String userId) throws IOException;

    /**
     * Update user extension attribute
     *
     * @param userId the user's ID
     * @param attributeName the extension attribute name
     * @param attributeValue the extension attribute value
     * @throws IOException if the API call fails
     */
    void updateUserExtensionAttribute(String userId, String attributeName, String attributeValue) throws IOException;

    /**
     * Get user extension attribute
     *
     * @param userId the user's ID
     * @param attributeName the extension attribute name
     * @return the extension attribute value, or null if not found
     * @throws IOException if the API call fails
     */
    String getUserExtensionAttribute(String userId, String attributeName) throws IOException;
}

