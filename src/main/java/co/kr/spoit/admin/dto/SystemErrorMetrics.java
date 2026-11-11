package co.kr.spoit.admin.dto;

/**
 * 서버·오류 로그 화면의 요약 지표다.
 *
 * <p>값은 모두 현재 프로세스의 메모리 버퍼에서 계산한다. 응답 시간·확인 처리는 수집하지 않으므로
 * 0으로 위장하지 않고 {@code null}로 남긴다.</p>
 */
public class SystemErrorMetrics {

    private final Integer count;
    private final Integer warningCount;
    private final Integer uncheckedCount;
    private final Integer averageResponse;
    private final String lastCollected;

    public SystemErrorMetrics(Integer count, Integer warningCount, Integer uncheckedCount,
            Integer averageResponse, String lastCollected) {
        this.count = count;
        this.warningCount = warningCount;
        this.uncheckedCount = uncheckedCount;
        this.averageResponse = averageResponse;
        this.lastCollected = lastCollected;
    }

    /** 선택 기간 안의 오류(ERROR) 건수다. */
    public Integer getCount() {
        return count;
    }

    /** 선택 기간 안의 경고(WARN) 건수다. */
    public Integer getWarningCount() {
        return warningCount;
    }

    /** 확인 처리 상태를 저장하지 않으므로 수집한 오류 전부를 미확인으로 본다. */
    public Integer getUncheckedCount() {
        return uncheckedCount;
    }

    /** 선택한 기간의 오류 건수와 같다. */
    public Integer getOpenCount() {
        return count;
    }

    /** 응답 시간을 수집하지 않으므로 값이 없다. */
    public Integer getAverageResponse() {
        return averageResponse;
    }

    public String getLastCollected() {
        return lastCollected;
    }
}
