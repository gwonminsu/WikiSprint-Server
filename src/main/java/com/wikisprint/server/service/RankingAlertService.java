package com.wikisprint.server.service;

import com.fasterxml.uuid.Generators;
import com.wikisprint.server.dto.RankingAlertResponseDTO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
public class RankingAlertService {

    private static final int RECENT_ALERT_MINUTES = 10;
    private static final int MAX_ALERT_EVENTS = 200;

    private final Deque<RankingAlertResponseDTO> recentAlerts = new ConcurrentLinkedDeque<>();

    public RankingAlertResponseDTO publish(RankingAlertResponseDTO alert) {
        RankingAlertResponseDTO publishedAlert = alert.toBuilder()
                .alertId(generateAlertId())
                .createdAt(LocalDateTime.now())
                .build();

        recentAlerts.addLast(publishedAlert);
        pruneExpiredAlerts(LocalDateTime.now().minusMinutes(RECENT_ALERT_MINUTES));
        trimAlerts();
        return publishedAlert;
    }

    public List<RankingAlertResponseDTO> getRecentAlerts() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(RECENT_ALERT_MINUTES);
        pruneExpiredAlerts(cutoff);
        return new ArrayList<>(recentAlerts);
    }

    private String generateAlertId() {
        return "RAL-" + Generators.timeBasedEpochGenerator().generate();
    }

    private void pruneExpiredAlerts(LocalDateTime cutoff) {
        while (true) {
            RankingAlertResponseDTO firstAlert = recentAlerts.peekFirst();
            if (firstAlert == null || firstAlert.getCreatedAt() == null || !firstAlert.getCreatedAt().isBefore(cutoff)) {
                return;
            }

            recentAlerts.pollFirst();
        }
    }

    private void trimAlerts() {
        while (recentAlerts.size() > MAX_ALERT_EVENTS) {
            recentAlerts.pollFirst();
        }
    }
}
