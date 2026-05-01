package com.wikisprint.server;

import com.wikisprint.server.dto.RankingAlertResponseDTO;
import com.wikisprint.server.mapper.RankingMapper;
import com.wikisprint.server.service.RankingService;
import com.wikisprint.server.vo.RankingRecordVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @Mock
    private RankingMapper rankingMapper;

    private RankingService rankingService;

    @BeforeEach
    void setUp() {
        rankingService = new RankingService(rankingMapper);
    }

    @Test
    void tryInsertRanking_returnsAlertsForPlayedDifficultyAcrossThreePeriodsOnly() {
        when(rankingMapper.selectByUser(any(), any(), any(), eq("ACC-1"))).thenReturn(null);
        when(rankingMapper.selectCount(any(), any(), any())).thenReturn(0);
        when(rankingMapper.selectTop100(eq("daily"), eq("easy"), any()))
                .thenReturn(List.of(), createTop100("ACC-1", "easy", "daily"));
        when(rankingMapper.selectTop100(eq("weekly"), eq("easy"), any()))
                .thenReturn(List.of(), createTop100("ACC-1", "easy", "weekly"));
        when(rankingMapper.selectTop100(eq("monthly"), eq("easy"), any()))
                .thenReturn(List.of(), createTop100("ACC-1", "easy", "monthly"));

        List<RankingAlertResponseDTO> result = rankingService.tryInsertRanking(
                "ACC-1",
                "banana",
                (short) 1,
                "apple",
                2,
                12345L
        );

        assertThat(result)
                .hasSize(3)
                .extracting(RankingAlertResponseDTO::getPeriodType)
                .containsExactly("daily", "weekly", "monthly");
        assertThat(result)
                .extracting(RankingAlertResponseDTO::getDifficulty)
                .containsOnly("easy");
    }

    private List<RankingRecordVO> createTop100(String accountId, String difficulty, String periodType) {
        RankingRecordVO winnerRecord = new RankingRecordVO();
        winnerRecord.setAccountId(accountId);
        winnerRecord.setNickname(accountId);
        winnerRecord.setDifficulty(difficulty);
        winnerRecord.setPeriodType(periodType);
        winnerRecord.setPeriodBucket(LocalDate.now());
        winnerRecord.setElapsedMs(12345L);
        winnerRecord.setTargetWord("banana");
        winnerRecord.setStartDoc("apple");
        winnerRecord.setPathLength(2);
        return List.of(winnerRecord);
    }
}
