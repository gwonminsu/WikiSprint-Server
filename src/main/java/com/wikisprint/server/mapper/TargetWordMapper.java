package com.wikisprint.server.mapper;

import com.wikisprint.server.vo.TargetWordVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TargetWordMapper {

    /** 랜덤 제시어 1개 조회 (언어 필터) */
    TargetWordVO selectRandomWord(@Param("lang") String lang);

    /** 랜덤 제시어 1개 조회 (언어 + 난이도 필터) */
    TargetWordVO selectRandomWordByDifficulty(@Param("lang") String lang, @Param("difficulty") Short difficulty);

    /** 전체 제시어 목록 조회 (admin 용도) */
    List<TargetWordVO> selectAllWords();

    /** 제시어 추가 (admin 용도) */
    void insertWord(@Param("word") String word, @Param("difficulty") Short difficulty, @Param("lang") String lang);

    /** 제시어 삭제 (admin 용도) */
    void deleteWord(@Param("wordId") Integer wordId);

    /** 단어로 난이도 조회 (랭킹 분류용 — 동일 단어가 여러 언어에 있으면 첫 번째 반환) */
    Short selectDifficultyByWord(@Param("word") String word);
}
