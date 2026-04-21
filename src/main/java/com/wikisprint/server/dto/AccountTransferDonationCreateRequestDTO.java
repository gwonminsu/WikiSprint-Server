package com.wikisprint.server.dto;

import lombok.Data;

// 국내 계좌이체 후원 요청 DTO
@Data
public class AccountTransferDonationCreateRequestDTO {
    private Integer coffeeCount;
    private String nickname;
    private String remitterName;
    private String message;
    private Boolean anonymous;
}
