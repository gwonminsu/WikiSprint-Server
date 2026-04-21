package com.wikisprint.server.controller;

import com.wikisprint.server.dto.ApiResponse;
import com.wikisprint.server.dto.DonationResponseDTO;
import com.wikisprint.server.service.DonationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 후원 조회 컨트롤러
@RestController
@RequiredArgsConstructor
@RequestMapping("/donations")
public class DonationController {

    private final DonationService donationService;

    @PostMapping("/latest")
    public ResponseEntity<ApiResponse<List<DonationResponseDTO>>> getLatestDonations() {
        return ResponseEntity.ok(ApiResponse.success(donationService.getLatestDonations()));
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
}
