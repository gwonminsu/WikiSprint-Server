package com.wikisprint.server.dto;

import lombok.Data;

// 관리자 계정 목록 조회 요청 DTO
@Data
public class AdminAccountListRequestDTO {
    private String view;
    private String sort;
    private String direction;
    private String search;
    private Integer page;
    private Integer size;
}
