package com.wikisprint.server.controller;

import com.wikisprint.server.dto.ApiResponse;
import com.wikisprint.server.global.common.auth.JwtTokenProvider;
import com.wikisprint.server.global.common.status.ConflictException;
import com.wikisprint.server.mapper.AccountMapper;
import com.wikisprint.server.service.GameRecordService;
import com.wikisprint.server.vo.AccountVO;
import com.wikisprint.server.vo.GameRecordVO;
import com.wikisprint.server.vo.SharedGameRecordVO;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 게임 전적 Controller - 전적 수명주기와 공유 공개 API를 제공한다.
@RequiredArgsConstructor
@RestController
@RequestMapping("/record")
public class GameRecordController {

    private final GameRecordService gameRecordService;
    private final JwtTokenProvider jwtTokenProvider;
    private final AccountMapper accountMapper;

    // JWT 파싱 공통 헬퍼
    private Authentication resolveAuth(String accessToken) {
        return jwtTokenProvider.getAuthentication(accessToken, false);
    }

    /**
     * 게임 시작 - in_progress 전적 생성
     * Request: { targetWord, startDoc }
     * Response: { recordId }
     */
    @PostMapping("/start")
    public ResponseEntity<?> startRecord(
            @RequestHeader(value = "Authorization", required = false) String accessToken,
            @RequestBody Map<String, Object> request) {

        Authentication auth;
        try {
            auth = resolveAuth(accessToken);
        } catch (ExpiredJwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("ACCESS_TOKEN_EXPIRED"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("유효하지 않은 액세스 토큰입니다."));
        }

        String accountId = auth.getName();
        String targetWord = (String) request.get("targetWord");
        String startDoc = (String) request.get("startDoc");

        try {
            GameRecordVO record = gameRecordService.startRecord(accountId, targetWord, startDoc);
            Map<String, Object> data = new HashMap<>();
            data.put("recordId", record.getRecordId());
            return ResponseEntity.ok(ApiResponse.success(data, "전적 생성 완료"));
        } catch (ConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("전적 생성 중 오류가 발생했습니다."));
        }
    }

    /**
     * 경로 업데이트 - 문서 이동 시 호출 (디바운스 적용)
     * Request: { recordId, navPath (JSON 문자열), lastArticle }
     */
    @PostMapping("/update-path")
    public ResponseEntity<?> updatePath(
            @RequestHeader(value = "Authorization", required = false) String accessToken,
            @RequestBody Map<String, Object> request) {

        Authentication auth;
        try {
            auth = resolveAuth(accessToken);
        } catch (ExpiredJwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("ACCESS_TOKEN_EXPIRED"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("유효하지 않은 액세스 토큰입니다."));
        }

        String accountId = auth.getName();
        String recordId = (String) request.get("recordId");
        String navPath = (String) request.get("navPath");
        String lastArticle = (String) request.get("lastArticle");

        try {
            gameRecordService.updatePath(accountId, recordId, navPath, lastArticle);
            return ResponseEntity.ok(ApiResponse.message("경로 업데이트 완료"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("경로 업데이트 중 오류가 발생했습니다."));
        }
    }

    /**
     * 클리어 처리
     * Request: { recordId, navPath (JSON 문자열), elapsedMs }
     */
    @PostMapping("/complete")
    public ResponseEntity<?> completeRecord(
            @RequestHeader(value = "Authorization", required = false) String accessToken,
            @RequestBody Map<String, Object> request) {

        Authentication auth;
        try {
            auth = resolveAuth(accessToken);
        } catch (ExpiredJwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("ACCESS_TOKEN_EXPIRED"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("유효하지 않은 액세스 토큰입니다."));
        }

        String accountId = auth.getName();
        String recordId = (String) request.get("recordId");
        String navPath = (String) request.get("navPath");
        Long elapsedMs = ((Number) request.get("elapsedMs")).longValue();

        try {
            gameRecordService.completeRecord(accountId, recordId, navPath, elapsedMs);
            return ResponseEntity.ok(ApiResponse.message("클리어 처리 완료"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("클리어 처리 중 오류가 발생했습니다."));
        }
    }

    /**
     * 포기 처리
     * Request: { recordId }
     */
    @PostMapping("/abandon")
    public ResponseEntity<?> abandonRecord(
            @RequestHeader(value = "Authorization", required = false) String accessToken,
            @RequestBody Map<String, Object> request) {

        Authentication auth;
        try {
            auth = resolveAuth(accessToken);
        } catch (ExpiredJwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("ACCESS_TOKEN_EXPIRED"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("유효하지 않은 액세스 토큰입니다."));
        }

        String accountId = auth.getName();
        String recordId = (String) request.get("recordId");

        try {
            gameRecordService.abandonRecord(accountId, recordId);
            return ResponseEntity.ok(ApiResponse.message("포기 처리 완료"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("포기 처리 중 오류가 발생했습니다."));
        }
    }

    /**
     * 공유 링크 생성
     * Request: { recordId }
     * Response: { shareId, expiresAt }
     */
    @PostMapping("/share")
    public ResponseEntity<?> createShareRecord(
            @RequestHeader(value = "Authorization", required = false) String accessToken,
            @RequestBody Map<String, Object> request) {

        Authentication auth;
        try {
            auth = resolveAuth(accessToken);
        } catch (ExpiredJwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("ACCESS_TOKEN_EXPIRED"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("유효하지 않은 액세스 토큰입니다."));
        }

        String accountId = auth.getName();
        String recordId = (String) request.get("recordId");

        try {
            SharedGameRecordVO shareRecord = gameRecordService.createOrGetShareRecord(accountId, recordId);
            Map<String, Object> data = new HashMap<>();
            data.put("shareId", shareRecord.getShareId());
            data.put("expiresAt", shareRecord.getExpiresAt() != null ? shareRecord.getExpiresAt().toString() : null);
            return ResponseEntity.ok(ApiResponse.success(data, "공유 링크 생성 완료"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("공유 링크 생성 중 오류가 발생했습니다."));
        }
    }

    /**
     * 공유 링크용 전적 조회 - 공개 API (JWT 불필요)
     * Response: { nick, profileImgUrl, targetWord, startDoc, navPath, elapsedMs, expiresAt }
     */
    @PostMapping("/share/{shareId}")
    public ResponseEntity<?> getSharedRecord(@PathVariable String shareId) {
        SharedGameRecordVO shareRecord = gameRecordService.getSharedRecord(shareId);
        if (shareRecord == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("공유 링크가 유효하지 않습니다."));
        }

        Map<String, Object> data = new HashMap<>();
        data.put("nick", shareRecord.getNick());
        data.put("profileImgUrl", shareRecord.getProfileImgUrl());
        data.put("targetWord", shareRecord.getTargetWord());
        data.put("startDoc", shareRecord.getStartDoc());
        data.put("navPath", shareRecord.getNavPath());
        data.put("elapsedMs", shareRecord.getElapsedMs());
        data.put("expiresAt", shareRecord.getExpiresAt() != null ? shareRecord.getExpiresAt().toString() : null);

        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * 전적 목록 + 누적 통계 조회
     * - /record/list 호출 시 stale in_progress 자동 정리
     * Response: { records, summary }
     */
    @PostMapping("/list")
    public ResponseEntity<?> getRecords(
            @RequestHeader(value = "Authorization", required = false) String accessToken) {

        Authentication auth;
        try {
            auth = resolveAuth(accessToken);
        } catch (ExpiredJwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("ACCESS_TOKEN_EXPIRED"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("유효하지 않은 액세스 토큰입니다."));
        }

        String accountId = auth.getName();
        gameRecordService.cleanupStaleRecords(accountId);

        List<GameRecordVO> records = gameRecordService.getRecentRecords(accountId);
        AccountVO account = accountMapper.selectAccountByUuid(accountId);
        int totalGames = account != null && account.getTotalGames() != null ? account.getTotalGames() : 0;
        int totalClears = account != null && account.getTotalClears() != null ? account.getTotalClears() : 0;
        int totalAbandons = account != null && account.getTotalAbandons() != null ? account.getTotalAbandons() : 0;
        Long bestTimeMs = account != null ? account.getBestRecord() : null;

        List<Map<String, Object>> recordList = new ArrayList<>();
        for (GameRecordVO record : records) {
            Map<String, Object> item = new HashMap<>();
            item.put("recordId", record.getRecordId());
            item.put("accountId", record.getAccountId());
            item.put("targetWord", record.getTargetWord());
            item.put("difficulty", record.getDifficulty());
            item.put("startDoc", record.getStartDoc());
            item.put("navPath", record.getNavPath());
            item.put("elapsedMs", record.getElapsedMs());
            item.put("status", record.getStatus());
            item.put("lastArticle", record.getLastArticle());
            item.put("playedAt", record.getPlayedAt() != null ? record.getPlayedAt().toString() : null);
            recordList.add(item);
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalPlays", totalGames);
        summary.put("clearCount", totalClears);
        summary.put("giveUpCount", totalAbandons);
        summary.put("bestTimeMs", bestTimeMs);

        Map<String, Object> data = new HashMap<>();
        data.put("records", recordList);
        data.put("summary", summary);

        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
