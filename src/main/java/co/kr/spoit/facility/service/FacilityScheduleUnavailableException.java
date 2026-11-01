package co.kr.spoit.facility.service;

/**
 * 시설 운영 일정상 예약이 불가능할 때 던진다.
 *
 * <p>메시지는 사용자에게 그대로 보여 줄 수 있는 한국어 문장이다.</p>
 */
public class FacilityScheduleUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FacilityScheduleUnavailableException(String message) {
        super(message);
    }
}
