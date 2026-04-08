package com.wikisprint.server.controller;

import com.wikisprint.server.dto.ApiResponse;
import com.wikisprint.server.global.common.auth.JwtTokenProvider;
import com.wikisprint.server.service.RankingService;
import com.wikisprint.server.vo.RankingRecordVO;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 랭킹 Controller — 기간 × 난이도 조합으로 Top 100 조회
@RequiredArgsConstructor
@RestController
@RequestMapping("/ranking")
public class RankingController {

    private final RankingService rankingService;
    private final JwtTokenProvider jwtTokenProvider;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * 랭킹 목록 조회 (비로그인도 가능)
     * Request: { periodType: "daily"|"weekly"|"monthly", difficulty: "all"|"easy"|"normal"|"hard" }
     * Response: { top100, me, bucketDate, serverNow }
     */
    @PostMapping("/list")
    public ResponseEntity<?> getRankingList(
            @RequestHeader(value = "Authorization", required = false) String accessToken,
            @RequestBody Map<String, Object> request) {

        // JWT 파싱 (선택적 — 없거나 만료돼도 목록은 조회 가능)
        String accountId = null;
        if (accessToken != null && !accessToken.isBlank()) {
            try {
                Authentication auth = jwtTokenProvider.getAuthentication(accessToken, false);
                if (auth != null) {
                    accountId = auth.getName();
                }
            } catch (ExpiredJwtException e) {
                // 만료된 토큰 — 비로그인으로 처리
            } catch (Exception ignored) {
                // 기타 인증 오류 — 비로그인으로 처리
            }
        }

        String periodType = (String) request.getOrDefault("periodType", "daily");
        String difficulty = (String) request.getOrDefault("difficulty", "all");

        // 유효성 검증
        if (!isValidPeriodType(periodType) || !isValidDifficulty(difficulty)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("유효하지 않은 기간 또는 난이도입니다."));
        }

        List<RankingRecordVO> top100 = rankingService.getTop100(periodType, difficulty);

        // 내 기록 조회 (로그인 시에만)
        RankingRecordVO myRecord = null;
        if (accountId != null) {
            myRecord = rankingService.getMyRecord(periodType, difficulty, accountId);
        }

        // 버킷 시작일 및 서버 현재 시각 (KST)
        LocalDate bucketDate = rankingService.resolveBucket(periodType);
        LocalDateTime serverNow = LocalDateTime.now(KST);

        // 응답 데이터 조립
        List<Map<String, Object>> top100List = new ArrayList<>();
        for (RankingRecordVO r : top100) {
            top100List.add(rankingRecordToMap(r));
        }

        Map<String, Object> data = new HashMap<>();
        data.put("top100", top100List);
        data.put("me", myRecord != null ? rankingRecordToMap(myRecord) : null);
        data.put("bucketDate", bucketDate.toString());
        data.put("serverNow", serverNow.toString());

        return ResponseEntity.ok(ApiResponse.success(data));
    }

    private Map<String, Object> rankingRecordToMap(RankingRecordVO r) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",              r.getId());
        m.put("accountId",       r.getAccountId());
        m.put("nickname",        r.getNickname());
        m.put("profileImageUrl", r.getProfileImageUrl());
        m.put("periodType",      r.getPeriodType());
        m.put("difficulty",      r.getDifficulty());
        m.put("elapsedMs",       r.getElapsedMs());
        m.put("targetWord",      r.getTargetWord());
        m.put("startDoc",        r.getStartDoc());
        m.put("pathLength",      r.getPathLength());
        m.put("createdAt",       r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
        return m;
    }

    private boolean isValidPeriodType(String v) {
        return v != null && (v.equals("daily") || v.equals("weekly") || v.equals("monthly"));
    }

    private boolean isValidDifficulty(String v) {
        return v != null && (v.equals("all") || v.equals("easy") || v.equals("normal") || v.equals("hard"));
    }
}
