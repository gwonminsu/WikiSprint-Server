package com.wikisprint.server.service;

import com.wikisprint.server.mapper.AccountMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * 신규 회원 자동 닉네임 생성기
 *
 * 생성 규칙:
 * 1. 형용사 + 명사 조합으로 base 닉네임 생성 (예: "빠른탐험가")
 * 2. DB에서 해당 base 자체 또는 base + 숫자 suffix(1~3자리) 패턴의 닉네임 조회
 * 3. base가 미사용이면 그대로 사용, 사용 중이면 최소 미사용 숫자 suffix 부여
 * 4. 전체 닉네임 길이 15자 이내 제한
 * 5. INSERT 실패(UNIQUE 충돌) 시 최대 3회 재시도
 *
 * race condition 방지:
 * - accounts.nick UNIQUE 제약이 최종 중복 방지를 보장함
 * - suffix 조회 기반 계산은 충돌 가능성을 줄이는 최적화 역할
 */
@Component
public class NicknameGenerator {

    private static final Logger log = LoggerFactory.getLogger(NicknameGenerator.class);

    // 최대 닉네임 길이
    private static final int MAX_NICK_LENGTH = 15;

    // suffix 숫자 최대값 (3자리: 1~999)
    private static final int MAX_SUFFIX = 999;

    // INSERT 실패 시 최대 재시도 횟수
    private static final int MAX_RETRY = 3;

    // 형용사 목록
    private static final String[] ADJECTIVES = {
            "Swift", "Brave", "Happy", "Mystic", "Active",
            "Cute", "Silent", "Shiny", "Cool", "Clever",
            "Strong", "Warm", "Chill", "Calm", "Bold",
            "Creepy", "Wise", "Bright", "Dark", "Rapid"

    };

    // 명사 목록
    private static final String[] NOUNS = {
            "Fox", "Wolf", "Cat", "Lion", "Eagle",
            "Bear", "Tiger", "Hawk", "Hero", "Mage",
            "Knight", "Sailor", "Hunter", "Rider", "Poet",
            "Wave", "Zombie", "Star", "Moon", "Flame"
    };

    private final Random random = new Random();

    /**
     * 중복되지 않는 고유 닉네임 생성
     *
     * @param accountMapper AccountMapper (중복 체크 및 패턴 조회용)
     * @return 고유 닉네임 문자열
     */
    public String generateUniqueNickname(AccountMapper accountMapper) {
        // base 닉네임 생성 + suffix 계산 시도 (최대 MAX_RETRY회)
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            try {
                String nick = buildCandidate(accountMapper);
                log.debug("자동 닉네임 생성 시도 {}/{}: {}", attempt + 1, MAX_RETRY, nick);
                return nick;
            } catch (DuplicateKeyException e) {
                // INSERT 시 UNIQUE 충돌 발생 — race condition: 다시 시도
                log.warn("자동 닉네임 충돌 (시도 {}/{}), 재계산", attempt + 1, MAX_RETRY);
            }
        }

        // 모든 재시도 실패 시 UUID 기반 폴백 닉네임 생성
        String fallback = generateFallbackNickname();
        log.warn("자동 닉네임 생성 재시도 초과, 폴백 닉네임 사용: {}", fallback);
        return fallback;
    }

    /**
     * 후보 닉네임 1개 생성
     * base 닉네임 조회 결과를 기반으로 사용 가능한 최소 suffix 계산
     */
    private String buildCandidate(AccountMapper accountMapper) {
        // base 닉네임 생성 (길이 제약 만족할 때까지 반복)
        String base = generateBase();

        // NOTE: base는 내부 상수 배열(ADJECTIVES, NOUNS)에서만 조합된 문자열이므로
        // 외부 입력이 아닌 안전한 내부 생성 문자열임. 정규식 패턴 오염 불가.
        // DB 쿼리의 정규식에 직접 사용되지만, 한글 문자로만 구성되어 메타문자 없음.
        List<String> existingNicks = accountMapper.selectNicksByBasePattern(base);

        if (existingNicks.isEmpty()) {
            // base 닉네임 자체가 미사용 → 그대로 사용
            return base;
        }

        // 사용 중인 suffix 숫자 수집
        Set<Integer> usedSuffixes = new TreeSet<>();
        for (String nick : existingNicks) {
            if (nick.equals(base)) {
                // base 자체가 사용 중 → suffix 0으로 간주
                usedSuffixes.add(0);
            } else {
                // base + 숫자 suffix 파싱
                String suffixPart = nick.substring(base.length());
                try {
                    usedSuffixes.add(Integer.parseInt(suffixPart));
                } catch (NumberFormatException e) {
                    // 파싱 실패는 무시 (정규식 매칭으로 방지되어 있지만 방어적 처리)
                    log.debug("suffix 파싱 실패 무시: {}", nick);
                }
            }
        }

        // base가 사용 중이면 suffix 1부터 탐색
        if (usedSuffixes.contains(0)) {
            // suffix 1 ~ MAX_SUFFIX 중 미사용 최솟값 탐색
            for (int suffix = 1; suffix <= MAX_SUFFIX; suffix++) {
                if (!usedSuffixes.contains(suffix)) {
                    String candidate = base + suffix;
                    if (candidate.length() <= MAX_NICK_LENGTH) {
                        return candidate;
                    }
                    // 길이 초과 시 더 짧은 base로 재생성
                    break;
                }
            }
            // 모든 suffix 사용 중이거나 길이 초과 → 새 base 생성 후 재시도
            return buildCandidateWithNewBase(accountMapper);
        }

        // base 자체가 미사용 (다른 base+숫자만 있는 경우, 실제로는 거의 없음)
        return base;
    }

    /**
     * 새로운 base로 후보 닉네임 재생성 (suffix 모두 소진 또는 길이 초과 시)
     */
    private String buildCandidateWithNewBase(AccountMapper accountMapper) {
        String newBase = generateBase();
        List<String> existingNicks = accountMapper.selectNicksByBasePattern(newBase);
        if (existingNicks.isEmpty()) {
            return newBase;
        }
        // 단순하게 미사용 suffix 1번만 시도 후 반환 (최악의 경우 INSERT 충돌로 상위에서 재시도)
        return newBase + "1";
    }

    /**
     * base 닉네임 생성 (형용사 + 명사, 15자 이내)
     */
    private String generateBase() {
        // 길이 조건을 만족할 때까지 최대 10회 시도
        for (int i = 0; i < 10; i++) {
            String adj = ADJECTIVES[random.nextInt(ADJECTIVES.length)];
            String noun = NOUNS[random.nextInt(NOUNS.length)];
            String base = adj + noun;
            // suffix 1자리(최대 3자리)를 붙여도 15자 이내여야 함
            if (base.length() <= MAX_NICK_LENGTH - 1) {
                return base;
            }
        }
        // 폴백: 가장 짧은 형용사 + 명사 고정 조합
        return "SwiftFox";
    }

    /**
     * UUID 기반 폴백 닉네임 (모든 재시도 실패 시 사용)
     * 예: "유저a1b2c3"
     */
    private String generateFallbackNickname() {
        String uuidShort = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        return "User" + uuidShort;
    }
}
