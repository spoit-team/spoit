package co.kr.spoit.member.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 시스템 관리자 사용자 관리 목록의 조회 행이다.
 *
 * <p>{@code pendingRequest}는 아직 대기 중인 {@code admin_request}가 있는지 여부다. 가입 승인 대기
 * 같은 별도 상태 컬럼이 없으므로 이 값과 {@code penalty_time}으로 화면 상태를 만든다.</p>
 */
@Getter
@Setter
public class AdminUserRow {

    private Long userId;
    private String nickname;
    private String email;
    private String role;
    private LocalDateTime createdAt;
    private LocalDateTime penaltyTime;
    private Boolean pendingRequest;
}
