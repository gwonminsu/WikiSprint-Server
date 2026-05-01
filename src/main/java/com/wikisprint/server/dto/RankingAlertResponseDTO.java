package com.wikisprint.server.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RankingAlertResponseDTO {
    private String alertId;
    private String kind;
    private LocalDateTime createdAt;
    private String periodType;
    private String difficulty;
    private RankingAlertPlayerDTO winner;
    private RankingAlertPlayerDTO loser;
    private Integer currentRank;
    private Integer previousRank;
    private Integer winnerRankDelta;
}
