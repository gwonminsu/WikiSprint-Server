package com.wikisprint.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 후원 응답 DTO
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DonationResponseDTO {
    private String alertId;
    private String donationId;
    private String source;
    private String accountId;
    private String accountNick;
    private String accountProfileImgUrl;
    private String type;
    private String supporterName;
    private String message;
    private Boolean isAccountLinkedDisplay;
    private String amount;
    private String currency;
    private Boolean isAnonymous;
    private LocalDateTime receivedAt;
}
