package com.wikisprint.server.mapper;

import com.wikisprint.server.vo.DonationVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

// 후원 MyBatis Mapper
@Mapper
public interface DonationMapper {

    void insertDonation(DonationVO donation);

    boolean existsByKofiMessageId(@Param("kofiMessageId") String kofiMessageId);

    void unlinkAccountByAccountId(@Param("accountId") String accountId);

    List<DonationVO> selectPendingAccountTransfers();

    int confirmAccountTransferDonation(@Param("donationId") String donationId);

    List<DonationVO> selectLatestDonations(@Param("limit") int limit);

    List<DonationVO> selectRecentDonations(@Param("receivedAfter") LocalDateTime receivedAfter);

    List<DonationVO> selectAllDonations();

    DonationVO selectDonationById(@Param("donationId") String donationId);

    int updateSupporterName(
            @Param("donationId") String donationId,
            @Param("supporterName") String supporterName
    );

    int updateDonationMessage(
            @Param("donationId") String donationId,
            @Param("message") String message
    );

    int deleteDonation(@Param("donationId") String donationId);
}
