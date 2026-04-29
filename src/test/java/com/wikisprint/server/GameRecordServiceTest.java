package com.wikisprint.server;

import com.wikisprint.server.global.common.status.ConflictException;
import com.wikisprint.server.mapper.AccountMapper;
import com.wikisprint.server.mapper.GameRecordMapper;
import com.wikisprint.server.mapper.SharedGameRecordMapper;
import com.wikisprint.server.mapper.TargetWordMapper;
import com.wikisprint.server.service.GameRecordService;
import com.wikisprint.server.service.RankingAlertService;
import com.wikisprint.server.service.RankingService;
import com.wikisprint.server.vo.GameRecordVO;
import com.wikisprint.server.vo.SharedGameRecordVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DuplicateKeyException;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doThrow;
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

    @Mock
    private RankingAlertService rankingAlertService;

    private GameRecordService gameRecordService;

    @BeforeEach
    void setUp() {
        gameRecordService = new GameRecordService(
                gameRecordMapper,
                sharedGameRecordMapper,
                accountMapper,
                targetWordMapper,
                rankingService,
                rankingAlertService
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
    void completeRecord_skipsSideEffectsWhenStateTransitionDidNotOccur() {
        GameRecordVO record = new GameRecordVO();
        record.setRecordId("REC-1");
        record.setAccountId("ACC-1");
        record.setTargetWord("banana");
        record.setStartDoc("apple");

        when(gameRecordMapper.selectRecordById("REC-1", "ACC-1")).thenReturn(record);
        when(gameRecordMapper.completeRecord("REC-1", "ACC-1", "[\"apple\",\"banana\"]", 12345L)).thenReturn(0);

        gameRecordService.completeRecord("ACC-1", "REC-1", "[\"apple\",\"banana\"]", 12345L);

        verify(accountMapper, never()).incrementTotalClears("ACC-1");
        verify(accountMapper, never()).updateBestRecord("ACC-1", 12345L);
        verify(rankingService, never()).tryInsertRanking(any(), any(), any(), any(), anyInt(), anyLong());
        verify(gameRecordMapper, never()).deleteOldestRecords("ACC-1", 5);
    }

    @Test
    void abandonRecord_skipsSideEffectsWhenStateTransitionDidNotOccur() {
        when(gameRecordMapper.abandonRecord("REC-1", "ACC-1")).thenReturn(0);

        gameRecordService.abandonRecord("ACC-1", "REC-1");

        verify(accountMapper, never()).incrementTotalAbandons("ACC-1");
        verify(gameRecordMapper, never()).deleteOldestRecords("ACC-1", 5);
    }

    @Test
    void startRecord_rejectsWhenFreshInProgressRecordAlreadyExists() {
        GameRecordVO existing = new GameRecordVO();
        existing.setRecordId("REC-EXISTING");
        existing.setPlayedAt(LocalDateTime.now());
        when(gameRecordMapper.selectInProgressRecord("ACC-1")).thenReturn(existing);
        when(gameRecordMapper.abandonStaleLatestInProgressRecord("REC-EXISTING", "ACC-1", 60)).thenReturn(0);

        assertThatThrownBy(() -> gameRecordService.startRecord("ACC-1", "banana", "apple"))
                .isInstanceOf(ConflictException.class);

        verify(gameRecordMapper, never()).insertRecord(any(GameRecordVO.class));
        verify(accountMapper, never()).incrementTotalGames("ACC-1");
    }

    @Test
    void startRecord_abandonsStaleInProgressRecordBeforeCreatingNewOne() {
        GameRecordVO existing = new GameRecordVO();
        existing.setRecordId("REC-EXISTING");
        when(gameRecordMapper.selectInProgressRecord("ACC-1")).thenReturn(existing);
        when(gameRecordMapper.abandonStaleLatestInProgressRecord("REC-EXISTING", "ACC-1", 60)).thenReturn(1);

        gameRecordService.startRecord("ACC-1", "banana", "apple");

        verify(accountMapper).incrementTotalAbandons("ACC-1");
        verify(gameRecordMapper).deleteOldestRecords("ACC-1", 5);
        verify(gameRecordMapper).insertRecord(any(GameRecordVO.class));
        verify(accountMapper).incrementTotalGames("ACC-1");
    }

    @Test
    void startRecord_convertsUniqueConflictToDomainConflict() {
        doThrow(new DuplicateKeyException("duplicate")).when(gameRecordMapper).insertRecord(any(GameRecordVO.class));

        assertThatThrownBy(() -> gameRecordService.startRecord("ACC-1", "banana", "apple"))
                .isInstanceOf(ConflictException.class);

        verify(accountMapper, never()).incrementTotalGames("ACC-1");
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
