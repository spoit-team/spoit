package co.kr.spoit.facility.service;

/** 존재하지 않거나 이미 삭제된 시설을 변경하려 할 때 던진다. */
public class FacilityNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FacilityNotFoundException(String message) {
        super(message);
    }
}
