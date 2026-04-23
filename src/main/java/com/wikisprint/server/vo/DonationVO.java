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
    private String kofiAccountId;
    private String wikisprintAccountId;
    private String kofiMessageId;
    private String accountNick;
    private String accountProfileImgUrl;
    private String type;
    private String supporterName;
    private String message;
    private Boolean isAccountLinkedDisplay;
    private Long amountCents;
    private String currency;
    private Boolean isAnonymous;
    private LocalDateTime receivedAt;
    private LocalDateTime createdAt;
}
