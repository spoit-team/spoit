package co.kr.spoit.mat.domain;

import java.math.RoundingMode;
import java.time.Duration;

/** 레이팅 정책 숫자를 한 곳에 모은다. Elo 계산 값이나 공식이 바뀌면 ALGORITHM_VERSION도 함께 올린다. */
public final class RatingPolicy {

    public static final String ALGORITHM_VERSION = "ELO-K32-V1";

    public static final int K_FACTOR = 32;
    public static final int PLACEMENT_K_FACTOR = 64;
    public static final int PLACEMENT_MATCHES = 5;
    public static final double ELO_DIVISOR = 400;

    // sport_rating_history의 DECIMAL 자릿수와 맞춘다.
    public static final int MMR_SCALE = 2;
    public static final int EXPECTED_SCORE_SCALE = 4;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public static final int SILVER_MIN_MMR = 900;
    public static final int GOLD_MIN_MMR = 1050;
    public static final int PLATINUM_MIN_MMR = 1200;
    public static final int DIAMOND_MIN_MMR = 1350;

    // 종료 시각(end_at) 후 결과 제출 기간과, 마감 전에 리마인더를 보내는 시점
    public static final Duration RESULT_SUBMIT_PERIOD = Duration.ofHours(24);
    public static final Duration REMINDER_BEFORE_DEADLINE = Duration.ofHours(1);

    private RatingPolicy() {
    }
}
