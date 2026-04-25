package com.wikisprint.server.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 만료된 탈퇴 요청 계정을 배치 삭제하는 스케줄러
 *
 * 실행 주기: 매일 자정 (cron = "0 0 0 * * *")
 * 동작:
 * 1. deletion_requested_at + 7일이 지난 계정 조회 (최대 100건)
 * 2. 각 계정을 독립 트랜잭션으로 삭제
 * 3. 실패한 계정은 다음 배치에서 다시 시도
 */
@Component
@RequiredArgsConstructor
public class AccountDeletionScheduler {

    private final AccountService accountService;
    private static final Logger log = LoggerFactory.getLogger(AccountDeletionScheduler.class);

    /**
     * 매일 자정 실행: 만료된 탈퇴 계정 배치 삭제
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void deleteExpiredAccounts() {
        log.info("만료 탈퇴 계정 삭제 스케줄러 시작");
        try {
            int deletedCount = accountService.deleteExpiredAccounts();
            log.info("만료 탈퇴 계정 삭제 완료: {}건", deletedCount);
        } catch (Exception e) {
            log.error("만료 탈퇴 계정 삭제 스케줄러 오류", e);
        }
    }
}
