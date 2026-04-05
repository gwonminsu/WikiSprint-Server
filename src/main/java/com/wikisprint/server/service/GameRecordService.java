package com.wikisprint.server.service;

import com.fasterxml.uuid.Generators;
import com.wikisprint.server.mapper.AccountMapper;
import com.wikisprint.server.mapper.GameRecordMapper;
import com.wikisprint.server.vo.GameRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// 게임 전적 서비스 — 라이프사이클: in_progress → cleared/abandoned
@Service
@RequiredArgsConstructor
public class GameRecordService {

    private final GameRecordMapper gameRecordMapper;
    private final AccountMapper accountMapper;

    // 계정당 유지할 최대 터미널 전적 수 (in_progress 제외)
    private static final int MAX_RECORDS = 5;
    // stale in_progress 판단 기준 (분)
    private static final int STALE_THRESHOLD_MINUTES = 60;

    /**
     * 게임 시작 시 in_progress 전적 생성
     * - 기존 in_progress가 있으면 자동 포기 처리
     * - totalGames 증가
     */
    @Transactional
    public GameRecordVO startRecord(String accountId, String targetWord, String startDoc) {
        // 기존 in_progress 전적 자동 포기 처리
        GameRecordVO existing = gameRecordMapper.selectInProgressRecord(accountId);
        if (existing != null) {
            gameRecordMapper.abandonRecord(existing.getRecordId(), accountId);
            accountMapper.incrementTotalAbandons(accountId);
            gameRecordMapper.deleteOldestRecords(accountId, MAX_RECORDS);
        }

        // 새 전적 생성
        String recordId = "REC-" + Generators.timeBasedEpochGenerator().generate().toString();
        String initialNavPath = "[\"" + startDoc.replace("\"", "\\\"") + "\"]";

        GameRecordVO record = new GameRecordVO();
        record.setRecordId(recordId);
        record.setAccountId(accountId);
        record.setTargetWord(targetWord);
        record.setStartDoc(startDoc);
        record.setNavPath(initialNavPath);
        record.setLastArticle(startDoc);
        record.setPlayedAt(LocalDateTime.now());

        gameRecordMapper.insertRecord(record);

        // 누적 게임 수 증가
        accountMapper.incrementTotalGames(accountId);

        return record;
    }

    /**
     * 문서 이동 시 경로 업데이트 (in_progress 상태에서만 적용)
     */
    @Transactional
    public void updatePath(String accountId, String recordId, String navPath, String lastArticle) {
        gameRecordMapper.updateNavPath(recordId, accountId, navPath, lastArticle);
    }

    /**
     * 게임 클리어 처리
     * - status → cleared, elapsedMs 저장
     * - totalClears 증가, FIFO 정리
     */
    @Transactional
    public void completeRecord(String accountId, String recordId, String navPath, Long elapsedMs) {
        gameRecordMapper.completeRecord(recordId, accountId, navPath, elapsedMs);
        accountMapper.incrementTotalClears(accountId);
        gameRecordMapper.deleteOldestRecords(accountId, MAX_RECORDS);
    }

    /**
     * 게임 포기 처리
     * - status → abandoned
     * - totalAbandons 증가, FIFO 정리
     */
    @Transactional
    public void abandonRecord(String accountId, String recordId) {
        gameRecordMapper.abandonRecord(recordId, accountId);
        accountMapper.incrementTotalAbandons(accountId);
        gameRecordMapper.deleteOldestRecords(accountId, MAX_RECORDS);
    }

    /**
     * stale in_progress 전적 정리 (재접속 시 또는 /record/list 호출 시 실행)
     * - STALE_THRESHOLD_MINUTES 경과한 in_progress → abandoned 일괄 전환
     */
    @Transactional
    public void cleanupStaleRecords(String accountId) {
        int abandoned = gameRecordMapper.abandonStaleRecords(accountId, STALE_THRESHOLD_MINUTES);
        for (int i = 0; i < abandoned; i++) {
            accountMapper.incrementTotalAbandons(accountId);
        }
        if (abandoned > 0) {
            gameRecordMapper.deleteOldestRecords(accountId, MAX_RECORDS);
        }
    }

    /**
     * 계정별 최근 완료 전적 조회 (cleared/abandoned, 최대 5건)
     */
    @Transactional(readOnly = true)
    public List<GameRecordVO> getRecentRecords(String accountId) {
        return gameRecordMapper.selectRecentRecords(accountId);
    }
}
