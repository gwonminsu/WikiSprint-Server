package com.wikisprint.server.controller;

import com.wikisprint.server.dto.ApiResponse;
import com.wikisprint.server.service.DonationService;
import com.wikisprint.server.service.DonationService.InvalidWebhookPayloadException;
import com.wikisprint.server.service.DonationService.InvalidWebhookTokenException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
        try {
            DonationService.KofiWebhookResult result = donationService.processKofiWebhook(verificationToken, data);
            String message = switch (result) {
                case SAVED -> "웹훅을 저장했습니다.";
                case DUPLICATE -> "이미 처리된 웹훅입니다.";
                case SKIPPED_DISABLED -> "웹훅 기능이 비활성화되어 있습니다.";
            };

            return ResponseEntity.ok(ApiResponse.message(message));
        } catch (InvalidWebhookTokenException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("verification_token이 올바르지 않습니다."));
        } catch (InvalidWebhookPayloadException exception) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(exception.getMessage()));
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("웹훅 처리 중 오류가 발생했습니다."));
        }
    }
}
