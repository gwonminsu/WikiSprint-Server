package com.wikisprint.server.controller;

import com.wikisprint.server.dto.ApiResponse;
import com.wikisprint.server.dto.GoogleLoginReqDTO;
import com.wikisprint.server.dto.TokenDTO;
import com.wikisprint.server.global.common.status.UnauthorizedException;
import com.wikisprint.server.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger; // 수정: 예외 로그 출력용 import 추가
import org.slf4j.LoggerFactory; // 수정: 예외 로그 출력용 import 추가
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap; // 수정: Map.of 대신 HashMap 사용
import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    // 예외 로그 출력용 logger 추가
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

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

            Map<String, Object> data = new HashMap<>();
            data.put("uuid", result.get("uuid"));
            data.put("nick", result.get("nick"));
            data.put("email", result.get("email"));
            data.put("profile_img_url", result.get("profile_img_url"));
            data.put("is_admin", result.get("is_admin"));
            data.put("nationality", result.get("nationality"));

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
            // 실제 서버 예외 로그 남기기
            log.error("GOOGLE LOGIN FAILED", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("로그인 처리 중 오류가 발생했습니다."));
        }
    }

    /**
     * iOS OAuth2 code flow 로그인 (authorization code → id_token 교환)
     * Google의 implicit flow 제한으로 response_type=code 사용 시 호출
     */
    @PostMapping("/google/code")
    public ResponseEntity<?> googleLoginWithCode(@RequestBody Map<String, String> request) {
        String code = request.get("code");
        String redirectUri = request.get("redirectUri");

        if (code == null || code.isBlank() || redirectUri == null || redirectUri.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("code 또는 redirectUri가 없습니다."));
        }

        try {
            Map<String, Object> result = authService.googleLoginWithCode(code, redirectUri);
            TokenDTO token = (TokenDTO) result.get("token");

            Map<String, Object> data = new HashMap<>();
            data.put("uuid", result.get("uuid"));
            data.put("nick", result.get("nick"));
            data.put("email", result.get("email"));
            data.put("profile_img_url", result.get("profile_img_url"));
            data.put("is_admin", result.get("is_admin"));
            data.put("nationality", result.get("nationality"));

            return ResponseEntity.ok(ApiResponse.withAuth(
                    data,
                    "Google 로그인 성공 (code flow)",
                    token.getAccessToken(),
                    token.getRefreshToken()
            ));
        } catch (UnauthorizedException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            // 실제 서버 예외 로그 남기기
            log.error("GOOGLE CODE LOGIN FAILED", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("로그인 처리 중 오류가 발생했습니다."));
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
            // 실제 서버 예외 로그 남기기
            log.error("TOKEN REFRESH FAILED", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("토큰 재발급 중 오류가 발생했습니다."));
        }
    }
}