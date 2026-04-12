package com.wikisprint.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.uuid.Generators;
import com.wikisprint.server.mapper.AccountMapper;
import com.wikisprint.server.mapper.GameRecordMapper;
import com.wikisprint.server.mapper.TargetWordMapper;
import com.wikisprint.server.vo.GameRecordVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// 게임 전적 서비스 — 라이프사이클: in_progress → cleared/abandoned
@Slf4j
@Service
@RequiredArgsConstructor
public class GameRecordService {

    private final GameRecordMapper gameRecordMapper;
    private final AccountMapper accountMapper;
    private final TargetWordMapper targetWordMapper;
    private final RankingService rankingService;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
        // 최고 기록 갱신 (현재 클리어 시간이 기존 기록보다 짧으면 업데이트)
        accountMapper.updateBestRecord(accountId, elapsedMs);

        // 랭킹 삽입/갱신 시도
        try {
            GameRecordVO rec = gameRecordMapper.selectRecordById(recordId, accountId);
            if (rec != null) {
                Short diffCode = targetWordMapper.selectDifficultyByWord(rec.getTargetWord());
                int pathLen = parsePathLength(navPath);
                rankingService.tryInsertRanking(accountId, rec.getTargetWord(), diffCode,
                        rec.getStartDoc(), pathLen, elapsedMs);
            }
        } catch (Exception e) {
            // 랭킹 처리 실패는 클리어 처리에 영향을 주지 않음
            log.warn("랭킹 처리 실패 — accountId={}, recordId={}: {}", accountId, recordId, e.getMessage());
        }

        gameRecordMapper.deleteOldestRecords(accountId, MAX_RECORDS);
    }

    /** navPath JSON 배열 문자열에서 경로 길이(방문 문서 수) 추출 */
    private int parsePathLength(String navPath) {
        try {
            List<String> path = OBJECT_MAPPER.readValue(navPath, new TypeReference<>() {});
            return path.size();
        } catch (Exception e) {
            return 0;
        }
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
        if (abandoned > 0) {
            // N번 개별 UPDATE 대신 한 번의 벌크 UPDATE로 처리
            accountMapper.addTotalAbandons(accountId, abandoned);
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
