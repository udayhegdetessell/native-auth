package com.tessell.entra.config;

import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@RequiredArgsConstructor
public class OkHttpConfig {

    private final OkHttpProperties okHttpProperties;

    @Bean
    public OkHttpClient okHttpClient() {
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        return new OkHttpClient.Builder()
                .connectTimeout(okHttpProperties.getConnect(), TimeUnit.SECONDS)
                .readTimeout(okHttpProperties.getRead(), TimeUnit.SECONDS)
                .writeTimeout(okHttpProperties.getWrite(), TimeUnit.SECONDS)
                .addInterceptor(loggingInterceptor)
                .build();
    }
}
