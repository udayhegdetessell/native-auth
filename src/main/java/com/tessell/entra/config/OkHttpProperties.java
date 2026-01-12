package com.tessell.entra.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "okhttp.timeout")
public class OkHttpProperties {
    
    /**
     * Connection timeout in seconds
     */
    private int connect = 30;
    
    /**
     * Read timeout in seconds
     */
    private int read = 30;
    
    /**
     * Write timeout in seconds
     */
    private int write = 30;
}

