package com.wikisprint.server.controller;

import com.wikisprint.server.dto.ApiResponse;
import com.wikisprint.server.dto.DonationResponseDTO;
import com.wikisprint.server.dto.PendingAccountTransferDonationResponseDTO;
import com.wikisprint.server.global.common.auth.JwtTokenProvider;
import com.wikisprint.server.service.AuthService;
import com.wikisprint.server.service.DonationService;
import com.wikisprint.server.service.DonationService.DonationNotFoundException;
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

import java.util.List;
import java.util.Map;

// 관리자 전용 후원 확인 API를 처리한다.
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/donations")
public class DonationAdminController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final DonationService donationService;
    private final ReportService reportService;

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

    @PostMapping("/account-transfer/pending")
    public ResponseEntity<?> getPendingAccountTransferDonations(
            @RequestHeader(value = "Authorization", required = false) String accessToken
    ) {
        AccountVO admin = resolveAdmin(accessToken);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("관리자 권한이 필요합니다."));
        }

        List<PendingAccountTransferDonationResponseDTO> pendingDonations = donationService.getPendingAccountTransferDonations();
        return ResponseEntity.ok(ApiResponse.success(pendingDonations));
    }

    @PostMapping("/account-transfer/confirm")
    public ResponseEntity<?> confirmAccountTransferDonation(
            @RequestHeader(value = "Authorization", required = false) String accessToken,
            @RequestBody Map<String, String> request
    ) {
        AccountVO admin = resolveAdmin(accessToken);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("관리자 권한이 필요합니다."));
        }

        String donationId = request.get("donationId");
        if (!StringUtils.hasText(donationId)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("donationId는 필수입니다."));
        }

        try {
            donationService.confirmAccountTransferDonation(donationId.trim());
            return ResponseEntity.ok(ApiResponse.message("국내 후원 확인이 완료되었습니다."));
        } catch (DonationNotFoundException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("확인 대기 중인 국내 후원 요청을 찾을 수 없습니다."));
        }
    }

    @PostMapping("/alert-replay")
    public ResponseEntity<?> replayDonationAlert(
            @RequestHeader(value = "Authorization", required = false) String accessToken,
            @RequestBody Map<String, String> request
    ) {
        AccountVO admin = resolveAdmin(accessToken);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("관리자 권한이 필요합니다."));
        }

        String donationId = request.get("donationId");
        if (!StringUtils.hasText(donationId)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("donationId는 필수입니다."));
        }

        try {
            DonationResponseDTO replayDonation = donationService.createDonationAlertReplay(
                    donationId.trim(),
                    admin.getUuid()
            );
            return ResponseEntity.ok(ApiResponse.success(replayDonation, "후원 알림 재송출을 예약했습니다."));
        } catch (DonationNotFoundException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("재송출할 후원 내역을 찾을 수 없습니다."));
        }
    }

    @PostMapping("/reports/summary")
    public ResponseEntity<?> getDonationReportSummary(
            @RequestHeader(value = "Authorization", required = false) String accessToken,
            @RequestBody Map<String, String> request
    ) {
        AccountVO admin = resolveAdmin(accessToken);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("관리자 권한이 필요합니다."));
        }

        String donationId = request.get("donationId");
        if (!StringUtils.hasText(donationId)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("donationId는 필수입니다."));
        }

        return ResponseEntity.ok(ApiResponse.success(reportService.getDonationSummary(donationId.trim())));
    }

    @PostMapping("/reports/resolve")
    public ResponseEntity<?> deleteDonationReports(
            @RequestHeader(value = "Authorization", required = false) String accessToken,
            @RequestBody Map<String, String> request
    ) {
        AccountVO admin = resolveAdmin(accessToken);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("관리자 권한이 필요합니다."));
        }

        String donationId = request.get("donationId");
        if (!StringUtils.hasText(donationId)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("donationId는 필수입니다."));
        }

        int deletedCount = reportService.deletePendingReportsByDonationId(donationId.trim());
        return ResponseEntity.ok(ApiResponse.success(Map.of("deletedCount", deletedCount), "신고 처리가 완료되었습니다."));
    }

    @PostMapping("/censor-supporter-name")
    public ResponseEntity<?> censorSupporterName(
            @RequestHeader(value = "Authorization", required = false) String accessToken,
            @RequestBody Map<String, String> request
    ) {
        AccountVO admin = resolveAdmin(accessToken);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("관리자 권한이 필요합니다."));
        }

        String donationId = request.get("donationId");
        if (!StringUtils.hasText(donationId)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("donationId는 필수입니다."));
        }

        try {
            donationService.censorSupporterName(donationId.trim());
            return ResponseEntity.ok(ApiResponse.message("서포터 네임 검열이 완료되었습니다."));
        } catch (DonationNotFoundException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("후원 정보를 찾을 수 없습니다."));
        }
    }

    @PostMapping("/censor-message")
    public ResponseEntity<?> censorDonationMessage(
            @RequestHeader(value = "Authorization", required = false) String accessToken,
            @RequestBody Map<String, String> request
    ) {
        AccountVO admin = resolveAdmin(accessToken);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("관리자 권한이 필요합니다."));
        }

        String donationId = request.get("donationId");
        if (!StringUtils.hasText(donationId)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("donationId는 필수입니다."));
        }

        try {
            donationService.censorDonationMessage(donationId.trim());
            return ResponseEntity.ok(ApiResponse.message("후원 내용 검열이 완료되었습니다."));
        } catch (DonationNotFoundException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("후원 정보를 찾을 수 없습니다."));
        }
    }

    @PostMapping("/delete")
    public ResponseEntity<?> deleteDonation(
            @RequestHeader(value = "Authorization", required = false) String accessToken,
            @RequestBody Map<String, String> request
    ) {
        AccountVO admin = resolveAdmin(accessToken);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("관리자 권한이 필요합니다."));
        }

        String donationId = request.get("donationId");
        if (!StringUtils.hasText(donationId)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("donationId는 필수입니다."));
        }

        try {
            reportService.deletePendingReportsByDonationId(donationId.trim());
            donationService.deleteDonation(donationId.trim());
            return ResponseEntity.ok(ApiResponse.message("후원 정보가 삭제되었습니다."));
        } catch (DonationNotFoundException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("후원 정보를 찾을 수 없습니다."));
        }
    }
}
