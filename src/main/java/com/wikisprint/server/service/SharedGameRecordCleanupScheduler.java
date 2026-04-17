package com.wikisprint.server.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 만료된 공유 전적 스냅샷을 1시간마다 정리한다.
@Component
@RequiredArgsConstructor
public class SharedGameRecordCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(SharedGameRecordCleanupScheduler.class);

    private final GameRecordService gameRecordService;

    @Scheduled(cron = "0 0 * * * *")
    public void cleanupExpiredShareRecords() {
        try {
            int deletedCount = gameRecordService.cleanupExpiredShareRecords();
            if (deletedCount > 0) {
                log.info("만료된 공유 전적 정리 완료: {}건", deletedCount);
            }
        } catch (Exception e) {
            log.error("만료된 공유 전적 정리 실패", e);
        }
    }
}
