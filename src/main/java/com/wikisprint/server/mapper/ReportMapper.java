package com.wikisprint.server.mapper;

import com.wikisprint.server.vo.ReportVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

// 신고 MyBatis Mapper
@Mapper
public interface ReportMapper {
    void insertReport(ReportVO report);

    int countPendingReports();

    int countPendingReportsByTargetType(@Param("targetType") String targetType);

    int countPendingStandaloneAccountReports();

    int countPendingReportsByTarget(
            @Param("targetType") String targetType,
            @Param("targetId") String targetId
    );

    int countPendingStandaloneAccountReportsByTargetId(@Param("targetAccountId") String targetAccountId);

    int countPendingReportsByDonationId(@Param("donationId") String donationId);

    List<ReportVO> selectPendingReportsByTarget(
            @Param("targetType") String targetType,
            @Param("targetId") String targetId
    );

    List<ReportVO> selectPendingReportsByDonationId(@Param("donationId") String donationId);

    int deletePendingReportsByTarget(
            @Param("targetType") String targetType,
            @Param("targetId") String targetId
    );

    int deletePendingReportsByDonationId(@Param("donationId") String donationId);

    int clearReporterAccountId(@Param("accountId") String accountId);

    int clearTargetAccountId(@Param("accountId") String accountId);

    int clearResolvedByAccountId(@Param("accountId") String accountId);
}
