package com.wikisprint.server.controller;

import com.wikisprint.server.dto.ApiResponse;
import com.wikisprint.server.service.DonationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Ko-fi 웹훅 수신 컨트롤러
@RestController
@RequiredArgsConstructor
@RequestMapping("/webhook")
public class DonationWebhookController {

    private final DonationService donationService;

    @PostMapping(value = "/kofi", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<ApiResponse<Void>> receiveKofiWebhook(
            @RequestParam(value = "verification_token", required = false) String verificationToken,
            @RequestParam(value = "data", required = false) String data
    ) {
        donationService.processKofiWebhook(verificationToken, data);
        return ResponseEntity.ok(ApiResponse.message("웹훅 처리 완료"));
    }
}
