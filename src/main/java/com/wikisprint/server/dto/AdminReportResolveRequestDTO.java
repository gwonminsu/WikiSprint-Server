package com.wikisprint.server.dto;

import lombok.Data;

// 관리자 신고 처리 완료 요청 DTO
@Data
public class AdminReportResolveRequestDTO {
    private String targetType;
    private String targetAccountId;
    private String targetDonationId;
}
