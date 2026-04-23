package com.wikisprint.server.controller;

import com.wikisprint.server.dto.AdminAccountListRequestDTO;
import com.wikisprint.server.dto.AdminReportResolveRequestDTO;
import com.wikisprint.server.dto.ApiResponse;
import com.wikisprint.server.global.common.auth.JwtTokenProvider;
import com.wikisprint.server.service.AccountService;
import com.wikisprint.server.service.AdminAccountService;
import com.wikisprint.server.service.AuthService;
import com.wikisprint.server.service.ReportService;
import com.wikisprint.server.vo.AccountVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/accounts")
public class AdminAccountController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final AdminAccountService adminAccountService;
    private final AccountService accountService;
    private final ReportService reportService;

    @PostMapping("/list")
    public ResponseEntity<?> getAccounts(
            @RequestHeader(value = "Authorization", required = false) String accessToken,
            @RequestBody(required = false) AdminAccountListRequestDTO request
    ) {
        if (resolveAdmin(accessToken) == null) {
            return forbidden();
        }

        return ResponseEntity.ok(ApiResponse.success(adminAccountService.getAccounts(request)));
    }

    @PostMapping("/reports/pending-count")
    public ResponseEntity<?> getPendingReportCount(
            @RequestHeader(value = "Authorization", required = false) String accessToken
    ) {
        if (resolveAdmin(accessToken) == null) {
            return forbidden();
        }

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "count",
                reportService.countPendingReportsByTargetType(ReportService.TARGET_ACCOUNT)
        )));
    }

    @PostMapping("/reports/summary")
    public ResponseEntity<?> getReportSummary(
            @RequestHeader(value = "Authorization", required = false) String accessToken,
            @RequestBody AdminReportResolveRequestDTO request
    ) {
        if (resolveAdmin(accessToken) == null) {
            return forbidden();
        }

        String accountId = resolveTargetAccountId(request);
        if (!StringUtils.hasText(accountId)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("targetAccountId는 필수입니다."));
        }

        return ResponseEntity.ok(ApiResponse.success(reportService.getSummary(ReportService.TARGET_ACCOUNT, accountId)));
    }

    @PostMapping("/reports/resolve")
    public ResponseEntity<?> deleteReports(
            @RequestHeader(value = "Authorization", required = false) String accessToken,
            @RequestBody AdminReportResolveRequestDTO request
    ) {
        if (resolveAdmin(accessToken) == null) {
            return forbidden();
        }

        String accountId = resolveTargetAccountId(request);
        if (!StringUtils.hasText(accountId)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("targetAccountId는 필수입니다."));
        }

        int deletedCount = reportService.deletePendingReports(ReportService.TARGET_ACCOUNT, accountId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("deletedCount", deletedCount), "신고 처리가 완료되었습니다."));
    }

    @PostMapping("/censor-profile")
    public ResponseEntity<?> censorProfileImage(
            @RequestHeader(value = "Authorization", required = false) String accessToken,
            @RequestBody Map<String, String> request
    ) {
        if (resolveAdmin(accessToken) == null) {
            return forbidden();
        }

        String accountId = request.get("accountId");
        if (!StringUtils.hasText(accountId)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("accountId는 필수입니다."));
        }

        try {
            return ResponseEntity.ok(ApiResponse.success(
                    Map.of("profileImgUrl", accountService.censorProfileImage(accountId.trim())),
                    "프로필 이미지 검열이 완료되었습니다."
            ));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(exception.getMessage()));
        } catch (IOException exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("프로필 이미지 검열에 실패했습니다."));
        }
    }

    @PostMapping("/censor-nickname")
    public ResponseEntity<?> censorNickname(
            @RequestHeader(value = "Authorization", required = false) String accessToken,
            @RequestBody Map<String, String> request
    ) {
        if (resolveAdmin(accessToken) == null) {
            return forbidden();
        }

        String accountId = request.get("accountId");
        if (!StringUtils.hasText(accountId)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("accountId는 필수입니다."));
        }

        try {
            return ResponseEntity.ok(ApiResponse.success(
                    Map.of("nick", accountService.censorNick(accountId.trim())),
                    "닉네임 검열이 완료되었습니다."
            ));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(exception.getMessage()));
        }
    }

    @PostMapping("/grant-admin")
    public ResponseEntity<?> grantAdmin(
            @RequestHeader(value = "Authorization", required = false) String accessToken,
            @RequestBody Map<String, String> request
    ) {
        if (resolveAdmin(accessToken) == null) {
            return forbidden();
        }

        String accountId = request.get("accountId");
        if (!StringUtils.hasText(accountId)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("accountId는 필수입니다."));
        }

        try {
            accountService.grantAdmin(accountId.trim());
            return ResponseEntity.ok(ApiResponse.message("관리자 권한 부여가 완료되었습니다."));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(exception.getMessage()));
        }
    }

    private AccountVO resolveAdmin(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return null;
        }

        try {
            Authentication authentication = jwtTokenProvider.getAuthentication(accessToken, false);
            AccountVO account = authService.getAccountByUuid(authentication.getName());
            if (account == null || !Boolean.TRUE.equals(account.getIsAdmin())) {
                return null;
            }
            return account;
        } catch (Exception exception) {
            return null;
        }
    }

    private String resolveTargetAccountId(AdminReportResolveRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getTargetAccountId())) {
            return null;
        }
        return request.getTargetAccountId().trim();
    }

    private ResponseEntity<?> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("관리자 권한이 필요합니다."));
    }
}
