package com.wikisprint.server.dto;

import lombok.Data;

// 신고 생성 요청 DTO
@Data
public class ReportCreateRequestDTO {
    private String targetType;
    private String targetAccountId;
    private String targetDonationId;
    private String reason;
    private String detail;
}
