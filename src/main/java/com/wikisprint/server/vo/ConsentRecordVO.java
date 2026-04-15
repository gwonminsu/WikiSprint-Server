package com.wikisprint.server.vo;

import lombok.Data;
import org.apache.ibatis.type.Alias;

import java.time.LocalDateTime;

/**
 * 동의 이력 VO
 * 동의한 항목만 row를 생성하는 방식 (is_agreed 컬럼 없음)
 * row 존재 = 동의, row 부재 = 미동의
 */
@Data
@Alias("ConsentRecordVO")
public class ConsentRecordVO {

    private Long id;

    private String accountId;

    // 약관 타입 식별자 (ConsentConstants 참조)
    private String consentType;

    // 약관 버전 (ConsentConstants 참조)
    private String consentVersion;

    // 동의 일시
    private LocalDateTime agreedAt;
}
