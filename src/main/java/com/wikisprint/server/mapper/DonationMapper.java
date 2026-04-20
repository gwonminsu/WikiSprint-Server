package com.wikisprint.server.mapper;

import com.wikisprint.server.vo.DonationVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

// 후원 MyBatis Mapper
@Mapper
public interface DonationMapper {

    void insertDonation(DonationVO donation);

    List<DonationVO> selectLatestDonations(@Param("limit") int limit);

    DonationVO selectDonationById(@Param("donationId") String donationId);

    boolean existsBySourceAndExternalId(
            @Param("source") String source,
            @Param("externalId") String externalId
    );
}
