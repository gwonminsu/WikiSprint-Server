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

    // 누적 통계 증가
    void incrementTotalGames(@Param("uuid") String uuid);
    void incrementTotalClears(@Param("uuid") String uuid);
    void incrementTotalAbandons(@Param("uuid") String uuid);
}
