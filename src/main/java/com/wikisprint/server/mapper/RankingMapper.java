package com.wikisprint.server.mapper;

import com.wikisprint.server.vo.RankingRecordVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

// 랭킹 기록 MyBatis Mapper
@Mapper
public interface RankingMapper {

    /** Top 100 조회 (accounts 테이블 JOIN — nickname, profile_image_url 포함) */
    List<RankingRecordVO> selectTop100(
            @Param("periodType") String periodType,
            @Param("difficulty") String difficulty,
            @Param("bucket") LocalDate bucket
    );

    /** 특정 유저의 해당 버킷 기록 1건 조회 */
    RankingRecordVO selectByUser(
            @Param("periodType") String periodType,
            @Param("difficulty") String difficulty,
            @Param("bucket") LocalDate bucket,
            @Param("accountId") String accountId
    );

    /** 현재 버킷의 총 기록 수 조회 */
    int selectCount(
            @Param("periodType") String periodType,
            @Param("difficulty") String difficulty,
            @Param("bucket") LocalDate bucket
    );

    /** 100위 기록의 elapsed_ms 조회 (100개 중 최악) */
    Long selectWorstElapsedInTop100(
            @Param("periodType") String periodType,
            @Param("difficulty") String difficulty,
            @Param("bucket") LocalDate bucket
    );

    /** 랭킹 신규 삽입 */
    void insertRecord(RankingRecordVO record);

    /** 기존 기록 갱신 (더 좋은 기록일 때) */
    void updateRecord(RankingRecordVO record);

    /** 100위 밖 기록 삭제 */
    void deleteWorstBeyond100(
            @Param("periodType") String periodType,
            @Param("difficulty") String difficulty,
            @Param("bucket") LocalDate bucket
    );
}
