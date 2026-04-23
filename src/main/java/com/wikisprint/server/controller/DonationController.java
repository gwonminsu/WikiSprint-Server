package com.wikisprint.server.controller;

import com.wikisprint.server.dto.AccountTransferDonationCreateRequestDTO;
import com.wikisprint.server.dto.ApiResponse;
import com.wikisprint.server.dto.DonationResponseDTO;
import com.wikisprint.server.service.DonationService;
import com.wikisprint.server.service.DonationService.InvalidDonationRequestException;
import com.wikisprint.server.vo.AccountVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 후원 조회와 국내 계좌이체 후원 요청을 처리한다.
@RestController
@RequiredArgsConstructor
@RequestMapping("/donations")
public class DonationController {

    private final DonationService donationService;

    @PostMapping("/latest")
    public ResponseEntity<ApiResponse<List<DonationResponseDTO>>> getLatestDonations() {
        return ResponseEntity.ok(ApiResponse.success(donationService.getLatestDonations()));
    }

    @PostMapping("/alerts/recent")
    public ResponseEntity<ApiResponse<List<DonationResponseDTO>>> getRecentAlertDonations() {
        return ResponseEntity.ok(ApiResponse.success(donationService.getRecentAlertDonations()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<List<DonationResponseDTO>>> getAllDonations() {
        return ResponseEntity.ok(ApiResponse.success(donationService.getAllDonations()));
    }

    @PostMapping("/{donationId}")
    public ResponseEntity<?> getDonation(@PathVariable String donationId) {
        DonationResponseDTO donation = donationService.getDonationById(donationId);
        if (donation == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("후원 내역을 찾을 수 없습니다."));
        }
        return ResponseEntity.ok(ApiResponse.success(donation));
    }

    @PostMapping("/account-transfer/request")
    public ResponseEntity<?> createAccountTransferDonation(
            Authentication authentication,
            @RequestBody AccountTransferDonationCreateRequestDTO request
    ) {
        try {
            AccountVO requester = null;
            if (authentication != null) {
                requester = new AccountVO();
                requester.setUuid(authentication.getName());
            }

            String donationId = donationService.createAccountTransferDonation(requester, request);
            return ResponseEntity.ok(ApiResponse.message("국내 후원 신청이 저장되었습니다. 입금 확인 후 후원 목록에 반영됩니다. (ID: " + donationId + ")"));
        } catch (InvalidDonationRequestException exception) {
            return ResponseEntity.badRequest().body(ApiResponse.error(exception.getMessage()));
        }
    }
}
