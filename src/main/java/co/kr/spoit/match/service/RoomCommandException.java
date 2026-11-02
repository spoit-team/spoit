package co.kr.spoit.match.service;

/**
 * 매칭방 생성·수정·취소 요청을 처리할 수 없을 때 던진다.
 *
 * <p>메시지는 사용자에게 그대로 보여 줄 수 있는 한국어 문장이다.</p>
 */
public class RoomCommandException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RoomCommandException(String message) {
        super(message);
    }
}
