package com.wikisprint.server;

import com.wikisprint.server.mapper.AccountMapper;
import com.wikisprint.server.mapper.GameRecordMapper;
import com.wikisprint.server.mapper.SharedGameRecordMapper;
import com.wikisprint.server.mapper.TargetWordMapper;
import com.wikisprint.server.service.GameRecordService;
import com.wikisprint.server.service.RankingService;
import com.wikisprint.server.vo.GameRecordVO;
import com.wikisprint.server.vo.SharedGameRecordVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameRecordServiceTest {

    @Mock
    private GameRecordMapper gameRecordMapper;

    @Mock
    private SharedGameRecordMapper sharedGameRecordMapper;

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private TargetWordMapper targetWordMapper;

    @Mock
    private RankingService rankingService;

    private GameRecordService gameRecordService;

    @BeforeEach
    void setUp() {
        gameRecordService = new GameRecordService(
                gameRecordMapper,
                sharedGameRecordMapper,
                accountMapper,
                targetWordMapper,
                rankingService
        );
    }

    @Test
    void completeRecord_rejectsWhenLastPathDoesNotMatchTargetWord() {
        GameRecordVO record = new GameRecordVO();
        record.setRecordId("REC-1");
        record.setAccountId("ACC-1");
        record.setTargetWord("banana");

        when(gameRecordMapper.selectRecordById("REC-1", "ACC-1")).thenReturn(record);

        assertThatThrownBy(() -> gameRecordService.completeRecord(
                "ACC-1",
                "REC-1",
                "[\"apple\",\"philippines\"]",
                12345L
        )).isInstanceOf(IllegalArgumentException.class);

        verify(gameRecordMapper, never()).completeRecord("REC-1", "ACC-1", "[\"apple\",\"philippines\"]", 12345L);
        verify(accountMapper, never()).incrementTotalClears("ACC-1");
    }

    @Test
    void createOrGetShareRecord_rejectsWhenStoredPathDoesNotMatchTargetWord() {
        GameRecordVO record = new GameRecordVO();
        record.setRecordId("REC-1");
        record.setAccountId("ACC-1");
        record.setTargetWord("banana");
        record.setStatus("cleared");
        record.setElapsedMs(12345L);
        record.setNavPath("[\"apple\",\"philippines\"]");

        when(gameRecordMapper.selectRecordById("REC-1", "ACC-1")).thenReturn(record);

        assertThatThrownBy(() -> gameRecordService.createOrGetShareRecord("ACC-1", "REC-1"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(sharedGameRecordMapper, never()).insertShareRecord(isA(SharedGameRecordVO.class));
        verify(sharedGameRecordMapper, never()).updateShareRecord(isA(SharedGameRecordVO.class));
    }
}
