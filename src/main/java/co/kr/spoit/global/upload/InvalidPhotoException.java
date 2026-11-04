package co.kr.spoit.global.upload;

/** 업로드한 사진이 형식·크기 조건에 맞지 않을 때 던진다. 메시지는 화면에 그대로 보여 준다. */
public class InvalidPhotoException extends RuntimeException {

    public InvalidPhotoException(String message) {
        super(message);
    }
}
