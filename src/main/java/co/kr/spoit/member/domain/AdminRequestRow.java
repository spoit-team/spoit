package co.kr.spoit.member.domain;

import lombok.Getter;
import lombok.Setter;

/** 승인·거절 대상 {@code admin_request} 행이다. */
@Getter
@Setter
public class AdminRequestRow {

    private Long requestId;
    private Long userId;
    private String requestedRole;
    private String status;
}
