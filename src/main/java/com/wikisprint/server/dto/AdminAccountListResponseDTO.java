package com.wikisprint.server.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

// 관리자 계정 목록 페이지 응답 DTO
@Data
@Builder
public class AdminAccountListResponseDTO {
    private List<AdminAccountResponseDTO> accounts;
    private int page;
    private int size;
    private int totalPages;
    private long totalCount;
}
