package com.wikisprint.server.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CompleteRecordResponseDTO {
    private List<RankingAlertResponseDTO> rankingAlerts;
    // 게스트 복구 직삽입 응답에서만 채워진다. 결과 화면 공유 버튼이 recordId를 필요로 함.
    private String recordId;
}
