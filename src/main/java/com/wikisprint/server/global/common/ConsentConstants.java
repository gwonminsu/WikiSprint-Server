package com.wikisprint.server.global.common;

import java.util.List;

/**
 * 약관 동의 관련 상수 정의
 *
 * 약관 버전 변경 시 이 파일의 버전 상수만 수정하면 됨.
 * TODO: 향후 설정 파일(application.yaml)이나 DB 테이블로 이관하여 동적 관리 가능하도록 구조 설계됨
 */
public final class ConsentConstants {

    private ConsentConstants() {
        // 유틸 클래스 — 인스턴스화 금지
    }

    // 약관 타입 식별자
    public static final String TYPE_TERMS_OF_SERVICE       = "terms_of_service";
    public static final String TYPE_PRIVACY_POLICY         = "privacy_policy";
    public static final String TYPE_AGE_VERIFICATION       = "age_verification";
    public static final String TYPE_MARKETING_NOTIFICATION = "marketing_notification";

    // 약관 버전 (버전 변경 시 아래 상수 수정)
    public static final String VERSION_TERMS_OF_SERVICE       = "v1.0";
    public static final String VERSION_PRIVACY_POLICY         = "v1.0";
    public static final String VERSION_AGE_VERIFICATION       = "v1.0";
    public static final String VERSION_MARKETING_NOTIFICATION = "v1.0";

    // 필수 동의 항목 타입 목록
    public static final List<String> REQUIRED_TYPES = List.of(
        TYPE_TERMS_OF_SERVICE,
        TYPE_PRIVACY_POLICY,
        TYPE_AGE_VERIFICATION
    );
}
