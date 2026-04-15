package com.wikisprint.server.mapper;

import com.wikisprint.server.vo.ConsentRecordVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConsentMapper {

    // 동의 이력 삽입 (동의한 항목만 호출됨)
    void insertConsent(ConsentRecordVO consent);

    // 계정의 전체 동의 이력 조회 (동의한 항목 목록)
    List<ConsentRecordVO> selectConsentsByAccountId(@Param("accountId") String accountId);

    // [탈퇴 처리용] 계정의 모든 동의 이력 삭제
    void deleteAllByAccountId(@Param("accountId") String accountId);
}
