package com.wikisprint.server.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

// 신고 사유별 집계 및 기타 신고 목록 DTO
@Data
@Builder
public class ReportSummaryDTO {
    private int profileImageCount;
    private int nicknameCount;
    private int donationContentCount;
    private int otherCount;
    private int totalPendingCount;
    private List<String> otherDetails;
}
