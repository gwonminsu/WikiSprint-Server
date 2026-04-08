package com.wikisprint.server.mapper;

import com.wikisprint.server.vo.GameRecordVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

// 게임 전적 MyBatis Mapper
@Mapper
public interface GameRecordMapper {

    /** 전적 생성 (in_progress 상태로 시작) */
    void insertRecord(GameRecordVO record);

    /** 경로 및 마지막 문서 업데이트 (in_progress 상태에서만) */
    void updateNavPath(
            @Param("recordId") String recordId,
            @Param("accountId") String accountId,
            @Param("navPath") String navPath,
            @Param("lastArticle") String lastArticle
    );

    /** 게임 클리어 처리 (cleared 상태로 전환) */
    void completeRecord(
            @Param("recordId") String recordId,
            @Param("accountId") String accountId,
            @Param("navPath") String navPath,
            @Param("elapsedMs") Long elapsedMs
    );

    /** 게임 포기 처리 (abandoned 상태로 전환) */
    void abandonRecord(
            @Param("recordId") String recordId,
            @Param("accountId") String accountId
    );

    /** 계정의 현재 in_progress 전적 조회 (최대 1건) */
    GameRecordVO selectInProgressRecord(@Param("accountId") String accountId);

    /** 계정별 최근 완료 전적 조회 (cleared/abandoned, 최신순 LIMIT 5) */
    List<GameRecordVO> selectRecentRecords(@Param("accountId") String accountId);

    /** 계정별 가장 오래된 터미널 전적 삭제 (keepCount 초과분) */
    void deleteOldestRecords(
            @Param("accountId") String accountId,
            @Param("keepCount") int keepCount
    );

    /** stale in_progress 전적을 abandoned로 일괄 전환 (thresholdMinutes 경과분) */
    int abandonStaleRecords(
            @Param("accountId") String accountId,
            @Param("thresholdMinutes") int thresholdMinutes
    );

    /** 전적 단건 조회 (랭킹 처리용 — target_word, start_doc 등 조회) */
    GameRecordVO selectRecordById(
            @Param("recordId") String recordId,
            @Param("accountId") String accountId
    );
}
