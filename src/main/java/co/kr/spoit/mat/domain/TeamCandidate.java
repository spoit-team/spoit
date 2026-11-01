package co.kr.spoit.mat.domain;

import java.math.BigDecimal;

/**
 * 팀 배정·팀장 선정에 쓰는 참가자 ID와 MMR.
 *
 * <p>MMR은 entry_mmr 컬럼(DECIMAL(8,2))과 같은 범위·정밀도만 허용하고 소수 둘째 자리로 맞춰 보관한다.
 * 어느 시점의 MMR을 넣을지는 호출하는 서비스가 정한다.</p>
 */
public record TeamCandidate(Long participantId, BigDecimal mmr) {

    /** entry_mmr DECIMAL(8,2)의 최댓값 */
    private static final BigDecimal MAX_MMR = new BigDecimal("999999.99");

    public TeamCandidate {
        if (participantId == null || participantId <= 0) {
            throw new IllegalArgumentException("participantId는 양수여야 합니다.");
        }
        if (mmr == null) {
            throw new IllegalArgumentException("MMR은 null일 수 없습니다. participantId=" + participantId);
        }
        if (mmr.signum() < 0) {
            throw new IllegalArgumentException("MMR은 음수일 수 없습니다. participantId=" + participantId);
        }
        if (mmr.compareTo(MAX_MMR) > 0) {
            throw new IllegalArgumentException("MMR은 999999.99 이하여야 합니다. participantId=" + participantId);
        }
        if (mmr.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(
                    "MMR은 소수 둘째 자리까지만 허용합니다. participantId=" + participantId + ", mmr=" + mmr.toPlainString());
        }
        // 1000.5와 1000.50이 같은 값으로 비교되도록 정밀도를 맞춘다. 위 검사로 반올림은 일어나지 않는다.
        mmr = mmr.setScale(2);
    }

    /** MMR을 0.01 단위 정수로 바꾼다. 합계 비교를 long으로 하기 위해 쓴다. */
    public long mmrCents() {
        return mmr.unscaledValue().longValueExact();
    }
}
