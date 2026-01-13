package com.tessell.entra.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "entra.external-id")
public class EntraExternalIdConfig {

    private String tenantSubdomain;
    private String tenantId;
    private String clientId;
    private String clientSecret;
    private String scope;
    private boolean requireEmailVerification = true; // Default: require OTP verification
    private Urls urls = new Urls();

    @Getter
    @Setter
    public static class Urls {
        private String nativeAuthBase;
        private String graphApiBase;
        private String graphTokenEndpoint;
    }

    /**
     * Get the Native Auth base URL with tenant substitution
     * Format: https://{tenant}.ciamlogin.com/{tenant}.onmicrosoft.com
     */
    public String getNativeAuthBaseUrl() {
        return urls.getNativeAuthBase()
                .replace("{tenantSubdomain}", tenantSubdomain);
    }

    /**
     * Get the Microsoft Graph API base URL
     */
    public String getGraphApiBaseUrl() {
        return urls.getGraphApiBase();
    }

    /**
     * Get the token endpoint for Graph API authentication with tenant substitution
     */
    public String getGraphTokenEndpoint() {
        return urls.getGraphTokenEndpoint()
                .replace("{tenantId}", tenantId);
    }
}
