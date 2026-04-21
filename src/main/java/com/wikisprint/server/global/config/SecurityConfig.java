package com.wikisprint.server.global.config;

import com.wikisprint.server.global.common.auth.JwtAuthenticationFilter;
import com.wikisprint.server.global.common.auth.JwtTokenProvider;
import com.wikisprint.server.global.filter.SimpleRateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtTokenProvider jwtTokenProvider;
    private final SimpleRateLimitFilter simpleRateLimitFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/auth/**", "/api/auth/**").permitAll()
                        .requestMatchers("/error/**", "/api/error/**").permitAll()
                        .requestMatchers("/account/profile/image/**", "/api/account/profile/image/**").permitAll()
                        // Wikipedia API 프록시 — 비로그인 접근 허용 (게임은 비로그인도 가능)
                        .requestMatchers("/wiki/**", "/api/wiki/**").permitAll()
                        // 랭킹 조회 — 비로그인도 접근 가능 (내 기록은 Controller에서 선택적 처리)
                        .requestMatchers("/ranking/**", "/api/ranking/**").permitAll()
                        .requestMatchers("/webhook/**", "/api/webhook/**").permitAll()
                        .requestMatchers("/donations/**", "/api/donations/**").permitAll()
                        // 공유 결과 조회 — 비로그인도 접근 가능
                        .requestMatchers("/record/share/**", "/api/record/share/**").permitAll()
                        // 관리자 전용 엔드포인트 — DB 레벨 검증은 AdminController.resolveAdmin()에서 처리
                        .anyRequest().authenticated())
                .addFilterBefore(simpleRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.addAllowedOrigin("http://localhost:5969");
        configuration.addAllowedOrigin("http://13.209.255.179:5969");
        configuration.addAllowedOrigin("https://main.d11crzf9vrq2hy.amplifyapp.com");
        configuration.addAllowedMethod("*");
        configuration.addAllowedHeader("*");
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
