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

    // [추가] base 자체 또는 base + 숫자 suffix(1~3자리) 패턴의 닉네임 목록 조회
    // NicknameGenerator의 suffix 계산에 사용. base는 내부 생성 문자열만 전달됨.
    java.util.List<String> selectNicksByBasePattern(@org.apache.ibatis.annotations.Param("base") String base);

    // [추가] 탈퇴 요청 일시 업데이트 (null 전달 시 탈퇴 취소)
    void updateDeletionRequestedAt(
        @org.apache.ibatis.annotations.Param("uuid") String uuid,
        @org.apache.ibatis.annotations.Param("deletionRequestedAt") java.time.LocalDateTime deletionRequestedAt
    );

    // [추가] 7일 경과 탈퇴 요청 계정 배치 조회 (LIMIT 적용)
    java.util.List<AccountVO> selectExpiredDeletionAccounts(@org.apache.ibatis.annotations.Param("limit") int limit);

    // [추가] 계정 삭제 (탈퇴 처리용 — FK cascade는 하위 테이블 먼저 삭제 후 호출)
    void deleteAccount(@org.apache.ibatis.annotations.Param("uuid") String uuid);

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
