package co.kr.spoit.member.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * {@code app_user} 테이블의 회원 한 행을 표현한다. 컬럼과 1:1로 대응하며
 * MyBatis 자동 매핑을 위해 setter를 가진 가변 객체로 둔다.
 */
@Getter
@Setter
public class Member {

    private Long userId;
    private String email;
    private String passwordHash;
    private String nickname;
    private String phone;
    private String role;
    private String status;
    private LocalDateTime createdAt;
    /** 탈퇴 시각. null이면 미탈퇴 회원이다. */
    private LocalDateTime deletedAt;
    /** 이용정지 종료 시각. null이면 정지 종료 시각이 설정되지 않은 상태다. */
    private LocalDateTime penaltyTime;
}
