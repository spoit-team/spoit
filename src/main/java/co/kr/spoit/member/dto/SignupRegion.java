package co.kr.spoit.member.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 회원가입 화면의 지역 선택지. MyBatis가 컬럼을 채우므로 가변 객체로 둔다.
 */
@Getter
@Setter
public class SignupRegion {

    private Long regionId;
    private String name;
}
