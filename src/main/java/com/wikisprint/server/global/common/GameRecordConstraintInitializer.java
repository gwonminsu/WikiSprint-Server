package com.wikisprint.server.global.common;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// 기존 운영 DB에도 진행 중 게임 1건 제약을 안전하게 반영한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class GameRecordConstraintInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initialize() {
        cleanupDuplicateInProgressRecords();
        ensureSingleInProgressConstraint();
    }

    private void cleanupDuplicateInProgressRecords() {
        int updated = jdbcTemplate.update("""
                WITH ranked_records AS (
                    SELECT record_id,
                           ROW_NUMBER() OVER (
                               PARTITION BY account_id
                               ORDER BY played_at DESC, created_at DESC, record_id DESC
                           ) AS row_num
                    FROM wikisprint.game_records
                    WHERE status = 'in_progress'
                )
                UPDATE wikisprint.game_records AS gr
                SET status = 'abandoned',
                    last_article = NULL
                FROM ranked_records AS rr
                WHERE gr.record_id = rr.record_id
                  AND rr.row_num > 1
                """);

        if (updated > 0) {
            log.warn("중복 in_progress 전적 {}건을 자동 정리했습니다.", updated);
        }
    }

    private void ensureSingleInProgressConstraint() {
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uq_game_records_in_progress_per_account
                    ON wikisprint.game_records (account_id)
                    WHERE status = 'in_progress'
                """);
    }
}
