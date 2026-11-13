package co.kr.spoit.member.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import co.kr.spoit.member.domain.PasswordResetChallenge;
import co.kr.spoit.member.domain.PasswordResetRateLimitBucket;

/** 비밀번호 재설정 도전과 이메일/IP 제한 버킷의 단일 SQL 조회·수정 매퍼. */
@Mapper
public interface PasswordResetMapper {

    Long findActiveUserIdByEmail(@Param("email") String email);

    int insertChallenge(PasswordResetChallenge challenge);

    int invalidateActiveChallengesByEmailFingerprint(
            @Param("emailFingerprint") String emailFingerprint, @Param("now") LocalDateTime now);

    PasswordResetChallenge findChallenge(@Param("challengeId") String challengeId);

    PasswordResetChallenge lockChallenge(@Param("challengeId") String challengeId);

    int recordChallengeFailure(@Param("challengeId") String challengeId, @Param("now") LocalDateTime now);

    int markChallengeVerified(@Param("challengeId") String challengeId, @Param("grantHmac") String grantHmac,
            @Param("verifiedAt") LocalDateTime verifiedAt,
            @Param("grantExpiresAt") LocalDateTime grantExpiresAt);

    int consumeChallenge(@Param("challengeId") String challengeId, @Param("grantHmac") String grantHmac,
            @Param("now") LocalDateTime now);

    int invalidateChallenge(@Param("challengeId") String challengeId, @Param("now") LocalDateTime now);

    int invalidateActiveChallengesByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    int deleteExpiredChallenges(@Param("now") LocalDateTime now);

    int ensureRateLimitBucket(@Param("bucketType") String bucketType,
            @Param("bucketFingerprint") String bucketFingerprint, @Param("now") LocalDateTime now);

    PasswordResetRateLimitBucket lockRateLimitBucket(@Param("bucketType") String bucketType,
            @Param("bucketFingerprint") String bucketFingerprint);

    int updateRateLimitBucket(PasswordResetRateLimitBucket bucket);

    int deleteExpiredRateLimitBuckets(@Param("cutoff") LocalDateTime cutoff);
}
