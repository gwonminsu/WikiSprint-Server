package com.wikisprint.server.controller;

import com.wikisprint.server.dto.ApiResponse;
import com.wikisprint.server.global.common.auth.JwtTokenProvider;
import com.wikisprint.server.global.common.util.FileStorageUtil;
import com.wikisprint.server.service.AccountService;
import com.wikisprint.server.service.AuthService;
import com.wikisprint.server.vo.AccountVO;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/account")
public class AccountController {
    private final AccountService accountService;
    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final FileStorageUtil fileStorageUtil;

    /**
     * 현재 로그인한 계정 정보 조회
     */
    @GetMapping("/me")
    public ResponseEntity<?> getMyAccount(
            @RequestHeader(value = "Authorization", required = false) String accessToken) {

        Authentication auth;
        try {
            auth = jwtTokenProvider.getAuthentication(accessToken, false);
        } catch (ExpiredJwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("ACCESS_TOKEN_EXPIRED"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("유효하지 않은 엑세스 토큰입니다."));
        }

        AccountVO accountVO = authService.getAccountByUuid(auth.getName());
        if (accountVO == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("계정을 찾을 수 없습니다."));
        }

        Map<String, Object> data = new HashMap<>();
        data.put("uuid", accountVO.getUuid());
        data.put("nick", accountVO.getNick());
        data.put("email", accountVO.getEmail());
        data.put("profile_img_url", accountVO.getProfileImgUrl());
        data.put("is_admin", Boolean.TRUE.equals(accountVO.getIsAdmin()));
        data.put("nationality", accountVO.getNationality());
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * 특정 계정 공개 정보 조회 (민감 필드 제외 — email, is_admin 미포함)
     */
    @PostMapping("/detail")
    public ResponseEntity<?> getAccount(@RequestBody Map<String, String> request) {
        String uuid = request.get("uuid");

        AccountVO accountVO = authService.getAccountByUuid(uuid);
        if (accountVO == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("계정을 찾을 수 없습니다."));
        }

        // 공개 필드만 반환 (이메일, 관리자 여부 등 민감 정보는 /account/me 에서만 제공)
        Map<String, Object> data = new HashMap<>();
        data.put("uuid", accountVO.getUuid());
        data.put("nick", accountVO.getNick());
        data.put("profile_img_url", accountVO.getProfileImgUrl());
        data.put("nationality", accountVO.getNationality());
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * 닉네임 변경
     */
    @PostMapping("/nick/update")
    public ResponseEntity<?> updateNick(
            @RequestHeader(value = "Authorization", required = false) String accessToken,
            @RequestBody Map<String, String> request) {

        String newNick = request.get("nick");

        Authentication auth;
        try {
            auth = jwtTokenProvider.getAuthentication(accessToken, false);
        } catch (ExpiredJwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("ACCESS_TOKEN_EXPIRED"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("유효하지 않은 엑세스 토큰입니다."));
        }

        AccountVO accountVO = authService.getAccountByUuid(auth.getName());
        if (accountVO == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("계정을 찾을 수 없습니다."));
        }

        if (!StringUtils.hasText(newNick)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("새 닉네임을 입력해주세요."));
        }

        try {
            accountService.updateNick(accountVO.getUuid(), newNick);
            Map<String, Object> data = new HashMap<>();
            data.put("nick", newNick);
            return ResponseEntity.ok(ApiResponse.success(data, "닉네임 변경 완료"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 국적 변경
     */
    @PostMapping("/nationality/update")
    public ResponseEntity<?> updateNationality(
            @RequestHeader(value = "Authorization", required = false) String accessToken,
            @RequestBody Map<String, String> request) {

        // nationality가 없거나 빈 문자열이면 null로 처리 (무국적)
        String nationality = request.get("nationality");
        if (nationality != null && nationality.isBlank()) {
            nationality = null;
        }

        Authentication auth;
        try {
            auth = jwtTokenProvider.getAuthentication(accessToken, false);
        } catch (ExpiredJwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("ACCESS_TOKEN_EXPIRED"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("유효하지 않은 엑세스 토큰입니다."));
        }

        AccountVO accountVO = authService.getAccountByUuid(auth.getName());
        if (accountVO == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("계정을 찾을 수 없습니다."));
        }

        try {
            accountService.updateNationality(accountVO.getUuid(), nationality);
            Map<String, Object> data = new HashMap<>();
            data.put("nationality", nationality);
            return ResponseEntity.ok(ApiResponse.success(data, "국적 변경 완료"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 프로필 이미지 업로드/변경
     */
    @PostMapping(value = "/profile/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadProfileImage(
            @RequestHeader(value = "Authorization", required = false) String accessToken,
            @RequestParam("file") MultipartFile file) {

        Authentication auth;
        try {
            auth = jwtTokenProvider.getAuthentication(accessToken, false);
        } catch (ExpiredJwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("ACCESS_TOKEN_EXPIRED"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("유효하지 않은 엑세스 토큰입니다."));
        }

        AccountVO accountVO = authService.getAccountByUuid(auth.getName());
        if (accountVO == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("계정을 찾을 수 없습니다."));
        }

        if (file == null || file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("업로드할 파일이 없습니다."));
        }

        try {
            String fileUri = accountService.updateProfileImage(accountVO.getUuid(), file);
            Map<String, Object> data = new HashMap<>();
            data.put("profile_img_url", fileUri);
            return ResponseEntity.ok(ApiResponse.success(data, "프로필 이미지 업로드 완료"));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("파일 업로드 실패: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 프로필 이미지 서빙 (공개 엔드포인트)
     */
    @GetMapping("/profile/image/**")
    public ResponseEntity<?> getProfileImage(HttpServletRequest request) {
        String requestUri = request.getRequestURI();

        String prefix = "/api/account/profile/image/";
        int idx = requestUri.indexOf(prefix);
        if (idx < 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String relativePath = requestUri.substring(idx + prefix.length());

        if (relativePath.contains("..")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        String fullPath = fileStorageUtil.getStoragePath() + "/" + relativePath;

        try {
            byte[] fileBytes = fileStorageUtil.readFile(fullPath);
            String contentType = Files.probeContentType(Paths.get(fullPath));
            if (contentType == null) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(fileBytes);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * 프로필 이미지 제거
     */
    @PostMapping("/profile/remove")
    public ResponseEntity<ApiResponse<Void>> removeProfileImage(
            @RequestHeader(value = "Authorization", required = false) String accessToken) {

        Authentication auth;
        try {
            auth = jwtTokenProvider.getAuthentication(accessToken, false);
        } catch (ExpiredJwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("ACCESS_TOKEN_EXPIRED"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("유효하지 않은 엑세스 토큰입니다."));
        }

        AccountVO accountVO = authService.getAccountByUuid(auth.getName());
        if (accountVO == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("계정을 찾을 수 없습니다."));
        }

        try {
            accountService.removeProfileImage(accountVO.getUuid());
            return ResponseEntity.ok(ApiResponse.message("프로필 이미지 제거 완료"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }
}
