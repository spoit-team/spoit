package co.kr.spoit.member.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 회원가입 폼. Thymeleaf가 프로퍼티를 읽고 스프링 데이터 바인더가 값을 채우므로
 * 기본 생성자와 setter를 가진 가변 객체로 둔다.
 *
 * <p>비밀번호와 관리자 초대 코드는 {@link #toString()}에 포함하지 않는다.</p>
 */
@Getter
@Setter
public class SignupForm {

    private boolean agreeService;
    private boolean agreePrivacy;
    private boolean marketingOptIn;

    /** 가입 유형. 현재는 일반 회원({@code member})만 지원하며 이것이 기본값이다. */
    private String role = "member";
    private String facilityName;
    private String adminInviteCode;

    private String email;
    private String phone;
    private String password;
    private String passwordConfirm;
    private String nickname;

    /** 선택 지역. 미선택이면 null이다. */
    private Long regionId;

    /** 선택 선호 종목. 미선택이면 null이다. */
    private Long representativeSportId;

    @Override
    public String toString() {
        return "SignupForm{"
                + "agreeService=" + agreeService
                + ", agreePrivacy=" + agreePrivacy
                + ", marketingOptIn=" + marketingOptIn
                + ", role='" + role + '\''
                + ", email='" + email + '\''
                + ", phone='" + phone + '\''
                + ", nickname='" + nickname + '\''
                + ", regionId=" + regionId
                + ", representativeSportId=" + representativeSportId
                + '}';
    }
}
