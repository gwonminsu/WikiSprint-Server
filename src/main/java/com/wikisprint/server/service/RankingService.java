package com.wikisprint.server.service;

import com.wikisprint.server.mapper.RankingMapper;
import com.wikisprint.server.vo.RankingRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

// 랭킹 서비스 — Top 100 유지 구조 (기간 × 난이도 버킷별)
@Service
@RequiredArgsConstructor
public class RankingService {

    private final RankingMapper rankingMapper;

    // 서버 기준 타임존 (KST)
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int TOP_N = 100;

    // 난이도 코드(1=easy, 2=normal, 3=hard) → 랭킹 difficulty 문자열 변환
    private static final String[] DIFFICULTY_NAMES = { null, "easy", "normal", "hard" };

    /**
     * 게임 클리어 시 랭킹 삽입/갱신 시도
     *
     * @param accountId  유저 ID
     * @param targetWord 제시어
     * @param diffCode   난이도 코드 (1~3, null이면 스킵)
     * @param startDoc   시작 문서
     * @param pathLength 경로 길이
     * @param elapsedMs  클리어 시간 (ms)
     */
    // REQUIRES_NEW — 게임 클리어 트랜잭션과 분리하여 랭킹 실패 시 클리어 롤백을 방지
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void tryInsertRanking(String accountId, String targetWord, Short diffCode,
                                 String startDoc, int pathLength, long elapsedMs) {
        if (diffCode == null || diffCode < 1 || diffCode > 3) {
            // 알 수 없는 난이도 — 랭킹 스킵
            return;
        }

        String difficultyName = DIFFICULTY_NAMES[diffCode];

        // 현재 KST 기준 각 기간 버킷 산출
        LocalDate today = LocalDate.now(KST);
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate monthStart = today.withDayOfMonth(1);

        // 해당 난이도 버킷 + "all" 버킷 (각 3개 기간 타입) = 2 × 3 = 6회 처리
        String[] difficulties = { difficultyName, "all" };
        String[] periodTypes  = { "daily", "weekly", "monthly" };
        LocalDate[] buckets   = { today, weekStart, monthStart };

        for (String diff : difficulties) {
            for (int i = 0; i < periodTypes.length; i++) {
                upsertRankingRecord(accountId, targetWord, diff,
                        periodTypes[i], buckets[i], startDoc, pathLength, elapsedMs);
            }
        }
    }

    /**
     * 단일 버킷에 대한 삽입/갱신 처리
     */
    private void upsertRankingRecord(String accountId, String targetWord, String difficulty,
                                     String periodType, LocalDate bucket,
                                     String startDoc, int pathLength, long elapsedMs) {
        // 기존 기록 확인
        RankingRecordVO existing = rankingMapper.selectByUser(periodType, difficulty, bucket, accountId);

        if (existing != null) {
            // 더 좋은 기록일 때만 갱신
            if (elapsedMs < existing.getElapsedMs()) {
                RankingRecordVO vo = buildVO(accountId, targetWord, difficulty, periodType, bucket,
                        startDoc, pathLength, elapsedMs);
                rankingMapper.updateRecord(vo);
                // 갱신 후 100위 초과분 정리 (갱신이어도 created_at 변경으로 순위 변동 가능)
                rankingMapper.deleteWorstBeyond100(periodType, difficulty, bucket);
            }
            return;
        }

        // 신규 삽입 조건 확인
        int count = rankingMapper.selectCount(periodType, difficulty, bucket);

        if (count < TOP_N) {
            // 100개 미만 → 무조건 삽입
            RankingRecordVO vo = buildVO(accountId, targetWord, difficulty, periodType, bucket,
                    startDoc, pathLength, elapsedMs);
            rankingMapper.insertRecord(vo);
        } else {
            // 100개 이상 → 현재 100위 기록과 비교
            Long worstMs = rankingMapper.selectWorstElapsedInTop100(periodType, difficulty, bucket);
            if (worstMs != null && elapsedMs < worstMs) {
                RankingRecordVO vo = buildVO(accountId, targetWord, difficulty, periodType, bucket,
                        startDoc, pathLength, elapsedMs);
                rankingMapper.insertRecord(vo);
                rankingMapper.deleteWorstBeyond100(periodType, difficulty, bucket);
            }
        }
    }

    private RankingRecordVO buildVO(String accountId, String targetWord, String difficulty,
                                    String periodType, LocalDate bucket,
                                    String startDoc, int pathLength, long elapsedMs) {
        RankingRecordVO vo = new RankingRecordVO();
        vo.setAccountId(accountId);
        vo.setTargetWord(targetWord);
        vo.setDifficulty(difficulty);
        vo.setPeriodType(periodType);
        vo.setPeriodBucket(bucket);
        vo.setStartDoc(startDoc);
        vo.setPathLength(pathLength);
        vo.setElapsedMs(elapsedMs);
        return vo;
    }

    /**
     * 랭킹 목록 조회
     *
     * @param periodType daily | weekly | monthly
     * @param difficulty all | easy | normal | hard
     * @return Top 100 리스트 (KST 현재 버킷 기준)
     */
    @Transactional(readOnly = true)
    public List<RankingRecordVO> getTop100(String periodType, String difficulty) {
        LocalDate bucket = resolveBucket(periodType);
        return rankingMapper.selectTop100(periodType, difficulty, bucket);
    }

    /**
     * 특정 유저의 현재 버킷 기록 조회 (없으면 null)
     */
    @Transactional(readOnly = true)
    public RankingRecordVO getMyRecord(String periodType, String difficulty, String accountId) {
        LocalDate bucket = resolveBucket(periodType);
        return rankingMapper.selectByUser(periodType, difficulty, bucket, accountId);
    }

    /**
     * 기간 타입에 맞는 현재 KST 버킷 날짜 산출
     */
    public LocalDate resolveBucket(String periodType) {
        LocalDate today = LocalDate.now(KST);
        return switch (periodType) {
            case "weekly"  -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "monthly" -> today.withDayOfMonth(1);
            default        -> today; // daily
        };
    }
}
