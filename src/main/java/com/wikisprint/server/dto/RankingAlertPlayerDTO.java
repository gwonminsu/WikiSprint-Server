package com.wikisprint.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class RankingAlertPlayerDTO {
    private String accountId;
    private String nickname;
    private String profileImageUrl;
    private String nationality;
    private String startDoc;
    private String targetWord;
    private Integer pathLength;
    private Long elapsedMs;
}
