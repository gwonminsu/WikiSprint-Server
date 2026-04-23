package com.wikisprint.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 관리자 확인 대기 중인 국내 후원 응답 DTO
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PendingAccountTransferDonationResponseDTO {
    private String donationId;
    private String source;
    private String accountId;
    private String accountNick;
    private String accountProfileImgUrl;
    private String supporterName;
    private String remitterName;
    private String message;
    private Boolean isAccountLinkedDisplay;
    private Integer coffeeCount;
    private String amount;
    private String currency;
    private Boolean isAnonymous;
    private LocalDateTime receivedAt;
}
