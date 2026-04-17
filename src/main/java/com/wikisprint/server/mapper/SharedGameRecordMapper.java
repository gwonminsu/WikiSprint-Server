package com.wikisprint.server.mapper;

import com.wikisprint.server.vo.SharedGameRecordVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SharedGameRecordMapper {

    SharedGameRecordVO selectActiveShareByRecordId(@Param("recordId") String recordId);

    SharedGameRecordVO selectShareByRecordId(@Param("recordId") String recordId);

    SharedGameRecordVO selectActiveShareByShareId(@Param("shareId") String shareId);

    void insertShareRecord(SharedGameRecordVO shareRecord);

    void updateShareRecord(SharedGameRecordVO shareRecord);

    int deleteExpiredShareRecords();

    void deleteAllByAccountId(@Param("accountId") String accountId);
}
