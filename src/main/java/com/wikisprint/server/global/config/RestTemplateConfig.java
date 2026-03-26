package com.wikisprint.server.global.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/* 외부 api 통신용 클라이언트 객체 */

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                // 연결 타임아웃: 5초
                .connectTimeout(Duration.ofSeconds(5))
                // 읽기 타임아웃: 10초
                .readTimeout(Duration.ofSeconds(10))
                .build();
    }
}
