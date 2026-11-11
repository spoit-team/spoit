package co.kr.spoit.notification.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 활성 회원 중 시스템 관리자 후보 행. MyBatis가 컬럼을 채우므로 가변 객체로 둔다.
 *
 * <p>{@code role}은 DB 원문이다. 관리자 여부는 {@code member.domain.MemberRole.from}의 정확 일치
 * 해석으로 다시 판정하므로, 이 DTO는 조회 결과를 나르는 역할만 한다.</p>
 */
@Getter
@Setter
public class SystemAdminCandidate {

    private Long userId;
    private String role;
}
