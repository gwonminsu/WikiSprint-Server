package com.wikisprint.server.controller;

import com.wikisprint.server.dto.ApiResponse;
import com.wikisprint.server.dto.PendingAccountTransferDonationResponseDTO;
import com.wikisprint.server.global.common.auth.JwtTokenProvider;
import com.wikisprint.server.service.AuthService;
import com.wikisprint.server.service.DonationService;
import com.wikisprint.server.service.DonationService.DonationNotFoundException;
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
}
