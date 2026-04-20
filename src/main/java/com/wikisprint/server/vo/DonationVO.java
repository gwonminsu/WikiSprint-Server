package com.wikisprint.server.vo;

import lombok.Data;
import org.apache.ibatis.type.Alias;

import java.time.LocalDateTime;

// 후원 저장 VO
@Data
@Alias("DonationVO")
public class DonationVO {
    private String donationId;
    private String source;
    private String externalId;
    private String type;
    private String supporterName;
    private String message;
    private String amount;
    private String currency;
    private Boolean isPublic;
    private String email;
    private String payload;
    private LocalDateTime receivedAt;
    private LocalDateTime createdAt;
}
