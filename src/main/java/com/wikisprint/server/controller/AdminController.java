package com.wikisprint.server.controller;

import com.wikisprint.server.dto.ApiResponse;
import com.wikisprint.server.global.common.auth.JwtTokenProvider;
import com.wikisprint.server.mapper.TargetWordMapper;
import com.wikisprint.server.service.AuthService;
import com.wikisprint.server.vo.AccountVO;
import com.wikisprint.server.vo.TargetWordVO;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 관리자 전용 컨트롤러 — 제시어(target_words) CRUD
 * 모든 엔드포인트는 JWT + is_admin 이중 검증 적용
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final TargetWordMapper targetWordMapper;

    /**
     * 요청자가 관리자인지 검증 후 AccountVO 반환
     * 비관리자/미인증이면 null 반환
     */
    private AccountVO resolveAdmin(String accessToken) {
        if (!StringUtils.hasText(accessToken)) return null;
        Authentication auth;
        try {
            auth = jwtTokenProvider.getAuthentication(accessToken, false);
        } catch (Exception e) {
            return null;
        }
        AccountVO account = authService.getAccountByUuid(auth.getName());
        if (account == null || !Boolean.TRUE.equals(account.getIsAdmin())) return null;
        return account;
    }

    /**
     * 전체 제시어 목록 조회
     * POST /admin/words/list
     */
    @PostMapping("/words/list")
    public ResponseEntity<?> getAllWords(
            @RequestHeader(value = "Authorization", required = false) String accessToken) {

        AccountVO admin = resolveAdmin(accessToken);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("관리자 권한이 필요합니다."));
        }

        List<TargetWordVO> words = targetWordMapper.selectAllWords();
        return ResponseEntity.ok(ApiResponse.success(words));
    }

    /**
     * 제시어 추가
     * POST /admin/words/add
     * body: { word, difficulty, lang }
     */
    @PostMapping("/words/add")
    public ResponseEntity<?> addWord(
            @RequestHeader(value = "Authorization", required = false) String accessToken,
            @RequestBody Map<String, Object> request) {

        AccountVO admin = resolveAdmin(accessToken);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("관리자 권한이 필요합니다."));
        }

        String word = (String) request.get("word");
        if (!StringUtils.hasText(word)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("제시어를 입력해주세요."));
        }

        Object difficultyObj = request.get("difficulty");
        if (difficultyObj == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("난이도를 선택해주세요."));
        }
        short difficulty = ((Number) difficultyObj).shortValue();
        if (difficulty < 1 || difficulty > 3) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("난이도는 1~3 사이여야 합니다."));
        }

        String lang = (String) request.get("lang");
        if (!StringUtils.hasText(lang)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("언어를 선택해주세요."));
        }

        try {
            targetWordMapper.insertWord(word.trim(), difficulty, lang);
            return ResponseEntity.ok(ApiResponse.message("제시어가 추가되었습니다."));
        } catch (Exception e) {
            // UNIQUE 제약 조건 위반 처리
            if (e.getMessage() != null && e.getMessage().contains("unique")) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error("이미 존재하는 제시어입니다."));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("제시어 추가에 실패했습니다."));
        }
    }

    /**
     * 제시어 삭제
     * POST /admin/words/delete
     * body: { wordId }
     */
    @PostMapping("/words/delete")
    public ResponseEntity<?> deleteWord(
            @RequestHeader(value = "Authorization", required = false) String accessToken,
            @RequestBody Map<String, Object> request) {

        AccountVO admin = resolveAdmin(accessToken);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("관리자 권한이 필요합니다."));
        }

        Object wordIdObj = request.get("wordId");
        if (wordIdObj == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("wordId를 입력해주세요."));
        }
        int wordId = ((Number) wordIdObj).intValue();

        try {
            targetWordMapper.deleteWord(wordId);
            return ResponseEntity.ok(ApiResponse.message("제시어가 삭제되었습니다."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("제시어 삭제에 실패했습니다."));
        }
    }
}
