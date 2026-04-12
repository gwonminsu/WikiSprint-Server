package com.wikisprint.server.controller;

import com.wikisprint.server.dto.ApiResponse;
import com.wikisprint.server.dto.GoogleLoginReqDTO;
import com.wikisprint.server.dto.TokenDTO;
import com.wikisprint.server.global.common.status.UnauthorizedException;
import com.wikisprint.server.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /**
     * Google 로그인 / 자동 가입
     */
    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@RequestBody GoogleLoginReqDTO request) {
        if (request.getCredential() == null || request.getCredential().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Google credential이 없습니다."));
        }

        try {
            Map<String, Object> result = authService.googleLogin(request.getCredential());
            TokenDTO token = (TokenDTO) result.get("token");

            Map<String, Object> data = Map.of(
                    "uuid", result.get("uuid"),
                    "nick", result.get("nick"),
                    "email", result.get("email"),
                    "profile_img_url", result.get("profile_img_url"),
                    "is_admin", result.get("is_admin"),
                    "nationality", result.get("nationality")
            );

            return ResponseEntity.ok(ApiResponse.withAuth(
                    data,
                    "Google 로그인 성공",
                    token.getAccessToken(),
                    token.getRefreshToken()
            ));
        } catch (UnauthorizedException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("로그인 처리 중 오류가 발생했습니다."));
        }
    }

    /**
     * iOS redirect 방식 Google 로그인 콜백
     * <GoogleLogin ux_mode="redirect" login_uri="..."> 사용 시 Google이 POST로 credential 전달
     */
    @PostMapping("/google/redirect")
    public void googleRedirect(
            @RequestParam("credential") String credential,
            @RequestParam(value = "g_csrf_token", required = false) String csrfToken,
            HttpServletResponse response) throws IOException {

        try {
            Map<String, Object> result = authService.googleLogin(credential);
            TokenDTO token = (TokenDTO) result.get("token");

            String accessToken = URLEncoder.encode(token.getAccessToken(), StandardCharsets.UTF_8);
            String refreshToken = URLEncoder.encode(token.getRefreshToken(), StandardCharsets.UTF_8);
            String uuid = URLEncoder.encode((String) result.get("uuid"), StandardCharsets.UTF_8);
            String nick = URLEncoder.encode((String) result.get("nick"), StandardCharsets.UTF_8);
            String email = URLEncoder.encode((String) result.get("email"), StandardCharsets.UTF_8);
            String profileImgUrl = URLEncoder.encode(
                    result.get("profile_img_url") != null ? (String) result.get("profile_img_url") : "",
                    StandardCharsets.UTF_8);
            String isAdmin = String.valueOf(result.get("is_admin"));
            Object nationalityObj = result.get("nationality");
            String nationality = nationalityObj != null
                    ? URLEncoder.encode((String) nationalityObj, StandardCharsets.UTF_8)
                    : "";

            // URL 해시(#)로 전달 — 서버 로그에 토큰이 기록되지 않도록 fragment 사용
            String redirectUrl = frontendUrl + "/auth#at=" + accessToken
                    + "&rt=" + refreshToken
                    + "&uuid=" + uuid
                    + "&nick=" + nick
                    + "&email=" + email
                    + "&piu=" + profileImgUrl
                    + "&adm=" + isAdmin
                    + "&nat=" + nationality;

            response.sendRedirect(redirectUrl);
        } catch (Exception e) {
            response.sendRedirect(frontendUrl + "/auth#error=login_failed");
        }
    }

    /**
     * 만료된 accessToken 갱신
     */
    @PostMapping("/token/refresh")
    public ResponseEntity<ApiResponse<Void>> authRefresh(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("리프레시 토큰이 헤더에 존재하지 않습니다."));
        }

        String refreshToken = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;

        try {
            TokenDTO newToken = authService.reissueToken(refreshToken);
            return ResponseEntity.ok(ApiResponse.withAuth(
                    null,
                    "토큰 재발급 성공",
                    newToken.getAccessToken(),
                    newToken.getRefreshToken()
            ));
        } catch (UnauthorizedException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("토큰 재발급 중 오류가 발생했습니다."));
        }
    }
}
