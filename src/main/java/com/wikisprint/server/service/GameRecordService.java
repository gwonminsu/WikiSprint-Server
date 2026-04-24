package com.wikisprint.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.uuid.Generators;
import com.wikisprint.server.mapper.AccountMapper;
import com.wikisprint.server.mapper.GameRecordMapper;
import com.wikisprint.server.mapper.SharedGameRecordMapper;
import com.wikisprint.server.mapper.TargetWordMapper;
import com.wikisprint.server.vo.AccountVO;
import com.wikisprint.server.vo.GameRecordVO;
import com.wikisprint.server.vo.SharedGameRecordVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

// 게임 전적 서비스 - 시작/완료/포기 라이프사이클과 공유 스냅샷 생성을 함께 관리한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class GameRecordService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_RECORDS = 5;
    private static final int STALE_THRESHOLD_MINUTES = 60;
    private static final int SHARE_EXPIRE_HOURS = 24;

    private final GameRecordMapper gameRecordMapper;
    private final SharedGameRecordMapper sharedGameRecordMapper;
    private final AccountMapper accountMapper;
    private final TargetWordMapper targetWordMapper;
    private final RankingService rankingService;

    // 게임 시작 시 in_progress 전적 생성
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

    // 문서 이동 시 경로 업데이트 (in_progress 상태에서만 적용)
    @Transactional
    public void updatePath(String accountId, String recordId, String navPath, String lastArticle) {
        gameRecordMapper.updateNavPath(recordId, accountId, navPath, lastArticle);
    }

    // 게임 클리어 처리 - 랭킹 반영과 FIFO 정리를 포함한다.
    @Transactional
    public void completeRecord(String accountId, String recordId, String navPath, Long elapsedMs) {
        GameRecordVO record = gameRecordMapper.selectRecordById(recordId, accountId);
        validateCompletedPath(record, navPath);

        gameRecordMapper.completeRecord(recordId, accountId, navPath, elapsedMs);
        accountMapper.incrementTotalClears(accountId);
        // 최고 기록 갱신
        accountMapper.updateBestRecord(accountId, elapsedMs);

        // 랭킹 삽입/갱신 시도
        try {
            if (record != null) {
                Short diffCode = targetWordMapper.selectDifficultyByWord(record.getTargetWord());
                int pathLength = parsePathLength(navPath);
                rankingService.tryInsertRanking(
                        accountId,
                        record.getTargetWord(),
                        diffCode,
                        record.getStartDoc(),
                        pathLength,
                        elapsedMs
                );
            }
        } catch (Exception e) {
            // 랭킹 처리 실패는 클리어 처리에 영향을 주지 않는다.
            log.warn("랭킹 처리 실패 - accountId={}, recordId={}: {}", accountId, recordId, e.getMessage());
        }

        gameRecordMapper.deleteOldestRecords(accountId, MAX_RECORDS);
    }

    // navPath JSON 배열 문자열에서 경로 길이(방문 문서 수) 추출
    private int parsePathLength(String navPath) {
        try {
            List<String> path = parseNavPath(navPath);
            return path.size();
        } catch (Exception e) {
            return 0;
        }
    }

    private List<String> parseNavPath(String navPath) throws Exception {
        return OBJECT_MAPPER.readValue(navPath, new TypeReference<>() {});
    }

    private void validateCompletedPath(GameRecordVO record, String navPath) {
        if (record == null) {
            throw new IllegalArgumentException("완료 처리할 전적을 찾을 수 없습니다.");
        }

        final List<String> path;
        try {
            path = parseNavPath(navPath);
        } catch (Exception e) {
            throw new IllegalArgumentException("완료 경로 형식이 올바르지 않습니다.");
        }

        if (path.isEmpty()) {
            throw new IllegalArgumentException("완료 경로가 비어 있습니다.");
        }

        String lastArticle = path.get(path.size() - 1);
        if (!normalizeTitle(lastArticle).equals(normalizeTitle(record.getTargetWord()))) {
            throw new IllegalArgumentException("완료 경로의 마지막 문서가 제시어와 일치하지 않습니다.");
        }
    }

    private String normalizeTitle(String title) {
        return URLDecoder.decode(title, StandardCharsets.UTF_8)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', ' ');
    }

    // 게임 포기 처리
    @Transactional
    public void abandonRecord(String accountId, String recordId) {
        gameRecordMapper.abandonRecord(recordId, accountId);
        accountMapper.incrementTotalAbandons(accountId);
        gameRecordMapper.deleteOldestRecords(accountId, MAX_RECORDS);
    }

    // stale in_progress 전적 정리
    @Transactional
    public void cleanupStaleRecords(String accountId) {
        int abandoned = gameRecordMapper.abandonStaleRecords(accountId, STALE_THRESHOLD_MINUTES);
        if (abandoned > 0) {
            // N건 포기를 한 번의 벌크 증가로 반영한다.
            accountMapper.addTotalAbandons(accountId, abandoned);
            gameRecordMapper.deleteOldestRecords(accountId, MAX_RECORDS);
        }
    }

    // 계정별 최근 완료 전적 조회 (cleared/abandoned, 최대 5건)
    @Transactional(readOnly = true)
    public List<GameRecordVO> getRecentRecords(String accountId) {
        return gameRecordMapper.selectRecentRecords(accountId);
    }

    // 공유 링크 생성 - 기존 미만료 링크가 있으면 재사용한다.
    @Transactional
    public SharedGameRecordVO createOrGetShareRecord(String accountId, String recordId) {
        GameRecordVO record = gameRecordMapper.selectRecordById(recordId, accountId);
        if (record == null || !"cleared".equals(record.getStatus()) || record.getElapsedMs() == null) {
            throw new IllegalArgumentException("공유할 수 있는 완료 전적이 없습니다.");
        }

        validateCompletedPath(record, record.getNavPath());

        SharedGameRecordVO activeShare = sharedGameRecordMapper.selectActiveShareByRecordId(recordId);
        if (activeShare != null) {
            return activeShare;
        }

        AccountVO account = accountMapper.selectAccountByUuid(accountId);
        if (account == null) {
            throw new IllegalArgumentException("계정을 찾을 수 없습니다.");
        }

        // 24시간 동안 유지되는 공유 스냅샷을 저장한다.
        SharedGameRecordVO shareRecord = new SharedGameRecordVO();
        shareRecord.setShareId(Generators.timeBasedEpochGenerator().generate().toString());
        shareRecord.setAccountId(accountId);
        shareRecord.setRecordId(recordId);
        shareRecord.setNick(account.getNick());
        shareRecord.setProfileImgUrl(account.getProfileImgUrl());
        shareRecord.setTargetWord(record.getTargetWord());
        shareRecord.setStartDoc(record.getStartDoc());
        shareRecord.setNavPath(record.getNavPath());
        shareRecord.setElapsedMs(record.getElapsedMs());
        shareRecord.setExpiresAt(LocalDateTime.now().plusHours(SHARE_EXPIRE_HOURS));

        SharedGameRecordVO existingShare = sharedGameRecordMapper.selectShareByRecordId(recordId);
        if (existingShare != null) {
            sharedGameRecordMapper.updateShareRecord(shareRecord);
        } else {
            sharedGameRecordMapper.insertShareRecord(shareRecord);
        }

        return shareRecord;
    }

    // 공유 페이지 조회 - 만료되지 않은 스냅샷만 반환한다.
    @Transactional(readOnly = true)
    public SharedGameRecordVO getSharedRecord(String shareId) {
        return sharedGameRecordMapper.selectActiveShareByShareId(shareId);
    }

    // 만료된 공유 스냅샷 정리
    @Transactional
    public int cleanupExpiredShareRecords() {
        return sharedGameRecordMapper.deleteExpiredShareRecords();
    }
}
