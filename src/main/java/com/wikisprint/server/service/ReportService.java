package com.wikisprint.server.service;

import com.fasterxml.uuid.Generators;
import com.wikisprint.server.dto.ReportCreateRequestDTO;
import com.wikisprint.server.dto.ReportSummaryDTO;
import com.wikisprint.server.mapper.AccountMapper;
import com.wikisprint.server.mapper.DonationMapper;
import com.wikisprint.server.mapper.ReportMapper;
import com.wikisprint.server.vo.AccountVO;
import com.wikisprint.server.vo.DonationVO;
import com.wikisprint.server.vo.ReportVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReportService {

    public static final String TARGET_ACCOUNT = "ACCOUNT";
    public static final String TARGET_DONATION = "DONATION";
    public static final String REASON_PROFILE_IMAGE = "PROFILE_IMAGE";
    public static final String REASON_NICKNAME = "NICKNAME";
    public static final String REASON_DONATION_CONTENT = "DONATION_CONTENT";
    public static final String REASON_OTHER = "OTHER";

    private static final String STATUS_PENDING = "PENDING";
    private static final int MAX_DETAIL_LENGTH = 100;
    private static final Set<String> ACCOUNT_REASONS = Set.of(
            REASON_PROFILE_IMAGE,
            REASON_NICKNAME,
            REASON_DONATION_CONTENT,
            REASON_OTHER
    );
    private static final Set<String> DONATION_REASONS = Set.of(
            REASON_PROFILE_IMAGE,
            REASON_NICKNAME,
            REASON_DONATION_CONTENT,
            REASON_OTHER
    );

    private final ReportMapper reportMapper;
    private final AccountMapper accountMapper;
    private final DonationMapper donationMapper;

    @Transactional
    public void createReport(String reporterAccountId, ReportCreateRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("신고 요청이 비어 있습니다.");
        }

        String targetType = normalizeRequired(request.getTargetType(), "신고 대상 유형은 필수입니다.");
        String reason = normalizeRequired(request.getReason(), "신고 사유는 필수입니다.");
        validateReason(targetType, reason);

        ReportVO report = new ReportVO();
        report.setReportId("RPT-" + Generators.timeBasedEpochGenerator().generate());
        report.setReporterAccountId(resolveReporterAccountId(reporterAccountId));
        report.setTargetType(targetType);
        report.setReason(reason);
        report.setDetail(normalizeDetail(request.getDetail()));
        report.setStatus(STATUS_PENDING);
        report.setCreatedAt(LocalDateTime.now());

        if (TARGET_ACCOUNT.equals(targetType)) {
            String targetAccountId = normalizeRequired(request.getTargetAccountId(), "신고 대상 계정은 필수입니다.");
            ensureAccountExists(targetAccountId);
            report.setTargetAccountId(targetAccountId);

            if (request.getTargetDonationId() != null && !request.getTargetDonationId().isBlank()) {
                DonationVO donation = ensureDonationExists(request.getTargetDonationId().trim());
                validateDonationAccountLink(donation, targetAccountId);
                report.setTargetDonationId(donation.getDonationId());
            }
        } else if (TARGET_DONATION.equals(targetType)) {
            String targetDonationId = normalizeRequired(request.getTargetDonationId(), "신고 대상 후원은 필수입니다.");
            DonationVO donation = ensureDonationExists(targetDonationId);
            report.setTargetDonationId(targetDonationId);

            if (shouldAttachDonationReportToAccount(donation)) {
                report.setTargetAccountId(donation.getWikisprintAccountId());
            }
        } else {
            throw new IllegalArgumentException("지원하지 않는 신고 대상 유형입니다.");
        }

        reportMapper.insertReport(report);
    }

    @Transactional(readOnly = true)
    public int countPendingReports() {
        return reportMapper.countPendingReports();
    }

    @Transactional(readOnly = true)
    public int countPendingReportsByTargetType(String targetType) {
        return reportMapper.countPendingReportsByTargetType(
                normalizeRequired(targetType, "신고 대상 유형은 필수입니다.")
        );
    }

    @Transactional(readOnly = true)
    public int countPendingStandaloneAccountReports() {
        return reportMapper.countPendingStandaloneAccountReports();
    }

    @Transactional(readOnly = true)
    public int countPendingReportsByTarget(String targetType, String targetId) {
        return reportMapper.countPendingReportsByTarget(targetType, targetId);
    }

    @Transactional(readOnly = true)
    public int countPendingStandaloneAccountReportsByTargetId(String targetAccountId) {
        return reportMapper.countPendingStandaloneAccountReportsByTargetId(
                normalizeRequired(targetAccountId, "신고 대상 계정은 필수입니다.")
        );
    }

    @Transactional(readOnly = true)
    public ReportSummaryDTO getSummary(String targetType, String targetId) {
        String normalizedTargetType = normalizeRequired(targetType, "신고 대상 유형은 필수입니다.");
        String normalizedTargetId = normalizeRequired(targetId, "신고 대상 ID는 필수입니다.");
        return buildSummary(reportMapper.selectPendingReportsByTarget(normalizedTargetType, normalizedTargetId));
    }

    @Transactional(readOnly = true)
    public ReportSummaryDTO getDonationSummary(String donationId) {
        String normalizedDonationId = normalizeRequired(donationId, "신고 대상 후원은 필수입니다.");
        ensureDonationExists(normalizedDonationId);
        return buildSummary(reportMapper.selectPendingReportsByDonationId(normalizedDonationId));
    }

    @Transactional
    public int deletePendingReports(String targetType, String targetId) {
        String normalizedTargetType = normalizeRequired(targetType, "신고 대상 유형은 필수입니다.");
        String normalizedTargetId = normalizeRequired(targetId, "신고 대상 ID는 필수입니다.");
        return reportMapper.deletePendingReportsByTarget(normalizedTargetType, normalizedTargetId);
    }

    @Transactional
    public int deletePendingReportsByDonationId(String donationId) {
        String normalizedDonationId = normalizeRequired(donationId, "신고 대상 후원은 필수입니다.");
        return reportMapper.deletePendingReportsByDonationId(normalizedDonationId);
    }

    private ReportSummaryDTO buildSummary(List<ReportVO> reports) {
        int profileImageCount = 0;
        int nicknameCount = 0;
        int donationContentCount = 0;
        int otherCount = 0;
        List<String> otherDetails = new ArrayList<>();

        for (ReportVO report : reports) {
            String reason = report.getReason();
            if (REASON_PROFILE_IMAGE.equals(reason)) {
                profileImageCount++;
            } else if (REASON_NICKNAME.equals(reason)) {
                nicknameCount++;
            } else if (REASON_DONATION_CONTENT.equals(reason)) {
                donationContentCount++;
            } else if (REASON_OTHER.equals(reason)) {
                otherCount++;
                if (report.getDetail() != null && !report.getDetail().isBlank()) {
                    otherDetails.add(report.getDetail());
                }
            }
        }

        return ReportSummaryDTO.builder()
                .profileImageCount(profileImageCount)
                .nicknameCount(nicknameCount)
                .donationContentCount(donationContentCount)
                .otherCount(otherCount)
                .totalPendingCount(reports.size())
                .otherDetails(otherDetails)
                .build();
    }

    private void validateReason(String targetType, String reason) {
        if (TARGET_ACCOUNT.equals(targetType)) {
            if (!ACCOUNT_REASONS.contains(reason)) {
                throw new IllegalArgumentException("계정 신고에 사용할 수 없는 사유입니다.");
            }
            return;
        }

        if (TARGET_DONATION.equals(targetType)) {
            if (!DONATION_REASONS.contains(reason)) {
                throw new IllegalArgumentException("후원 신고에 사용할 수 없는 사유입니다.");
            }
            return;
        }

        throw new IllegalArgumentException("지원하지 않는 신고 대상 유형입니다.");
    }

    private String resolveReporterAccountId(String reporterAccountId) {
        if (reporterAccountId == null || reporterAccountId.isBlank()) {
            return null;
        }

        AccountVO reporter = accountMapper.selectAccountByUuid(reporterAccountId.trim());
        return reporter == null ? null : reporter.getUuid();
    }

    private void ensureAccountExists(String accountId) {
        AccountVO account = accountMapper.selectAccountByUuid(accountId);
        if (account == null) {
            throw new IllegalArgumentException("신고 대상 계정을 찾을 수 없습니다.");
        }
    }

    private DonationVO ensureDonationExists(String donationId) {
        DonationVO donation = donationMapper.selectDonationById(donationId);
        if (donation == null) {
            throw new IllegalArgumentException("신고 대상 후원을 찾을 수 없습니다.");
        }
        return donation;
    }

    private boolean shouldAttachDonationReportToAccount(DonationVO donation) {
        if (!Boolean.TRUE.equals(donation.getIsAccountLinkedDisplay())) {
            return false;
        }
        return donation.getWikisprintAccountId() != null && !donation.getWikisprintAccountId().isBlank();
    }

    private void validateDonationAccountLink(DonationVO donation, String accountId) {
        if (!Boolean.TRUE.equals(donation.getIsAccountLinkedDisplay())) {
            throw new IllegalArgumentException("계정과 연동된 후원 정보가 아니므로 계정 신고로 처리할 수 없습니다.");
        }
        if (donation.getWikisprintAccountId() == null || !donation.getWikisprintAccountId().equals(accountId)) {
            throw new IllegalArgumentException("후원 정보와 신고 대상 계정이 일치하지 않습니다.");
        }
    }

    private String normalizeDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return null;
        }

        String normalizedDetail = detail.trim();
        if (normalizedDetail.length() > MAX_DETAIL_LENGTH) {
            throw new IllegalArgumentException("기타 신고 사유는 100자 이하로 입력해야 합니다.");
        }
        return normalizedDetail;
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
