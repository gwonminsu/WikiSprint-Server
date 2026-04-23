package com.wikisprint.server.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

// 관리자 계정 목록 응답 DTO
@Data
@Builder
public class AdminAccountResponseDTO {
    private String accountId;
    private String email;
    private String nick;
    private String profileImgUrl;
    private String nationality;
    private Boolean isAdmin;
    private LocalDateTime lastLogin;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer totalGames;
    private Integer totalClears;
    private Integer totalAbandons;
    private Long bestRecord;
    private LocalDateTime deletionRequestedAt;
    private Integer pendingReportCount;
}
