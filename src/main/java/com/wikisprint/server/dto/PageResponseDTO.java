package com.wikisprint.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
@AllArgsConstructor
public class PageResponseDTO<T> {

    private List<T> content;

    private int currentPage;

    private int totalPages;

    private long totalCount;

    private int size;

}
