package com.wikisprint.server.mapper;

import com.wikisprint.server.vo.AccountVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AccountMapper {

    void insertAccount(AccountVO account);
    void updateLastLoginAt(String uuid);
    boolean checkExistedNick(String nick);
    AccountVO selectAccountByUuid(String uuid);
    AccountVO selectAccountByNick(String nick);
    AccountVO selectAccountByGoogleId(String googleId);

    void updateNick(@Param("uuid") String uuid, @Param("nick") String nick);
    void updateProfileImgUrl(@Param("uuid") String uuid, @Param("profileImgUrl") String profileImgUrl);
    void updateNationality(@Param("uuid") String uuid, @Param("nationality") String nationality);

    // 누적 통계 증가
    void incrementTotalGames(@Param("uuid") String uuid);
    void incrementTotalClears(@Param("uuid") String uuid);
    void incrementTotalAbandons(@Param("uuid") String uuid);
    // 벌크 포기 횟수 증가 (stale 정리 시 N회 → 1회 UPDATE로 처리)
    void addTotalAbandons(@Param("uuid") String uuid, @Param("count") int count);

    // 최고 기록 갱신 (현재 기록이 기존보다 짧을 때만 적용)
    void updateBestRecord(@Param("uuid") String uuid, @Param("elapsedMs") Long elapsedMs);
}
