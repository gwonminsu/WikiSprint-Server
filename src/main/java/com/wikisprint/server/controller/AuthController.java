package com.wikisprint.server.controller;

import com.wikisprint.server.dto.ApiResponse;
import com.wikisprint.server.dto.GoogleLoginReqDTO;
import com.wikisprint.server.dto.RegisterReqDTO;
import com.wikisprint.server.dto.TokenDTO;
import com.wikisprint.server.global.common.status.UnauthorizedException;
import com.wikisprint.server.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
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
            return buildLoginResponse(result, "Google 로그인 성공");
        } catch (UnauthorizedException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
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
            return buildLoginResponse(result, "Google 로그인 성공 (code flow)");
        } catch (UnauthorizedException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("GOOGLE CODE LOGIN FAILED", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("로그인 처리 중 오류가 발생했습니다."));
        }
    }

    /**
     * 신규 회원가입 완료
     * googleLogin에서 is_new_user=true를 받은 프론트가 약관 동의 완료 후 호출.
     * 계정 생성 + 동의 이력 저장을 단일 트랜잭션으로 처리.
     * Request body: { "credential": "Google ID Token", "consents": ["terms_of_service", ...] }
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterReqDTO request) {
        String credential = request.getCredential();

        if (credential == null || credential.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("credential이 없습니다."));
        }

        try {
            Map<String, Object> result = authService.register(credential, request.getConsents());
            return buildLoginResponse(result, "회원가입 성공");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (UnauthorizedException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("REGISTER FAILED", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("회원가입 처리 중 오류가 발생했습니다."));
        }
    }

    /**
     * [추가] 탈퇴 취소
     * 탈퇴 요청 중인 계정의 Google ID Token으로 본인 확인 후 탈퇴 요청 취소 + 정상 로그인 처리.
     * /auth/** 하위이므로 토큰 없이 호출 가능 (permitAll).
     * Request body: { "credential": "Google ID Token" }
     */
    @PostMapping("/cancel-deletion")
    public ResponseEntity<?> cancelDeletion(@RequestBody Map<String, String> request) {
        String credential = request.get("credential");

        if (credential == null || credential.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("credential이 없습니다."));
        }

        try {
            Map<String, Object> result = authService.cancelDeletion(credential);
            return buildLoginResponse(result, "탈퇴 취소 및 로그인 성공");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (UnauthorizedException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("CANCEL DELETION FAILED", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("탈퇴 취소 처리 중 오류가 발생했습니다."));
        }
    }

    /**
     * 로그아웃 — Refresh 토큰의 jti를 서버 블랙리스트에 추가한다.
     * jti가 없는 구형 토큰은 조용히 성공 처리(기존 사용자 UX 영향 없음).
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody(required = false) Map<String, String> body) {
        if (body != null) {
            String refreshToken = body.get("refreshToken");
            if (refreshToken != null && !refreshToken.isBlank()) {
                authService.logout(refreshToken);
            }
        }
        return ResponseEntity.ok(ApiResponse.success(null, "로그아웃되었습니다."));
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
            log.error("TOKEN REFRESH FAILED", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("토큰 재발급 중 오류가 발생했습니다."));
        }
    }

    /**
     * [추가] 로그인 결과 Map을 ResponseEntity로 변환하는 헬퍼
     * is_new_user, is_deletion_pending에 따라 토큰 포함 여부를 분기함
     */
    private ResponseEntity<?> buildLoginResponse(Map<String, Object> result, String successMessage) {
        Boolean isNewUser = (Boolean) result.get("is_new_user");
        Boolean isDeletionPending = (Boolean) result.get("is_deletion_pending");

        Map<String, Object> data = new HashMap<>();

        // 신규 유저: 계정 정보 없이 신규 가입 플래그 + id_token_string만 반환
        if (Boolean.TRUE.equals(isNewUser)) {
            data.put("is_new_user", true);
            data.put("is_deletion_pending", false);
            data.put("id_token_string", result.get("id_token_string"));
            data.put("email", result.get("email"));
            return ResponseEntity.ok(ApiResponse.success(data, "신규 가입 필요"));
        }

        // 탈퇴 대기 계정: 탈퇴 관련 정보만 반환, 토큰 미발급
        if (Boolean.TRUE.equals(isDeletionPending)) {
            data.put("is_new_user", false);
            data.put("is_deletion_pending", true);
            data.put("deletion_scheduled_at", result.get("deletion_scheduled_at"));
            data.put("id_token_string", result.get("id_token_string"));
            return ResponseEntity.ok(ApiResponse.success(data, "탈퇴 요청 중인 계정"));
        }

        // 정상 로그인: 계정 정보 + JWT 토큰 반환
        TokenDTO token = (TokenDTO) result.get("token");
        data.put("uuid", result.get("uuid"));
        data.put("nick", result.get("nick"));
        data.put("email", result.get("email"));
        data.put("profile_img_url", result.get("profile_img_url"));
        data.put("is_admin", result.get("is_admin"));
        data.put("nationality", result.get("nationality"));
        data.put("is_new_user", false);
        data.put("is_deletion_pending", false);
        return ResponseEntity.ok(ApiResponse.withAuth(
                data,
                successMessage,
                token.getAccessToken(),
                token.getRefreshToken()
        ));
    }
}