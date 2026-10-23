package co.kr.spoit.member.dto;

import lombok.Getter;
import lombok.Setter;

/** 회원 탈퇴 재확인용 비밀번호만 담는 폼. */
@Getter
@Setter
public class WithdrawalRequestForm {

    private String password;
}
