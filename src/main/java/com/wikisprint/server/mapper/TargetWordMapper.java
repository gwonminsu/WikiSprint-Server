package com.wikisprint.server.mapper;

import com.wikisprint.server.vo.TargetWordVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TargetWordMapper {

    /** 랜덤 제시어 1개 조회 */
    TargetWordVO selectRandomWord();

    /** 전체 제시어 목록 조회 (admin 용도) */
    List<TargetWordVO> selectAllWords();

    /** 제시어 추가 (admin 용도) */
    void insertWord(@Param("word") String word, @Param("difficulty") Short difficulty);

    /** 제시어 삭제 (admin 용도) */
    void deleteWord(@Param("wordId") Integer wordId);
}
