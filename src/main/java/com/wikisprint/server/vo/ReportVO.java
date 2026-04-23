package com.wikisprint.server.vo;

import lombok.Data;
import org.apache.ibatis.type.Alias;

import java.time.LocalDateTime;

// 사용자/후원 신고 저장 VO
@Data
@Alias("ReportVO")
public class ReportVO {
    private String reportId;
    private String reporterAccountId;
    private String targetType;
    private String targetAccountId;
    private String targetDonationId;
    private String reason;
    private String detail;
    private String status;
    private String resolvedBy;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
}
