package com.wikisprint.server.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private T data;
    private String message;
    private AuthToken auth;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AuthToken {
        private String accessToken;
        private String refreshToken;
    }

    // 데이터만 반환
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .data(data)
                .build();
    }

    // 데이터 + 메시지 반환
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .data(data)
                .message(message)
                .build();
    }

    // 메시지만 반환
    public static ApiResponse<Void> message(String message) {
        return ApiResponse.<Void>builder()
                .message(message)
                .build();
    }

    // 인증 토큰 포함 반환
    public static <T> ApiResponse<T> withAuth(T data, String message, String accessToken, String refreshToken) {
        return ApiResponse.<T>builder()
                .data(data)
                .message(message)
                .auth(AuthToken.builder()
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .build())
                .build();
    }

    // 에러 응답
    public static ApiResponse<Void> error(String message) {
        return ApiResponse.<Void>builder()
                .message(message)
                .build();
    }
}
