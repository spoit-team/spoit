package co.kr.spoit.facility.service;

/** 담당하지 않는 시설을 변경하려 할 때 던진다. */
public class FacilityAccessDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FacilityAccessDeniedException(String message) {
        super(message);
    }
}
