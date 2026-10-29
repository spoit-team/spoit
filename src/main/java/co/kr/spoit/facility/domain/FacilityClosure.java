package co.kr.spoit.facility.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code schedule_change}에 저장하는 휴관 한 건이다.
 *
 * <p>휴관은 {@link #TYPE} 유형과 {@link #STATUS_ACTIVE}·{@link #STATUS_CANCELLED} 상태만
 * 사용한다. 기존에 저장된 다른 유형(임시 휴관·시설 점검·운영시간 변경 등)이나 다른
 * 상태 문자열은 이 서비스가 임의로 휴관으로 해석하지 않는다.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class FacilityClosure {

    /** 이 서비스가 등록하는 휴관 유형이다. */
    public static final String TYPE = "FACILITY_CLOSURE";

    /** 적용 중인 휴관 상태다. */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 취소된 휴관 상태다. 이용 가능 시간 계산에서 제외한다. */
    public static final String STATUS_CANCELLED = "CANCELLED";

    private Long scheduleChangeId;
    private Long facilityId;
    private Long createdBy;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String reason;
    private String status;

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    /** 휴관 구간과 대상 구간이 겹치는지 확인한다. 경계가 맞닿으면 겹치지 않는다. */
    public boolean overlaps(LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        return startAt.isBefore(rangeEnd) && endAt.isAfter(rangeStart);
    }
}
