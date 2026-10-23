package co.kr.spoit.member.dto;

import lombok.Getter;
import lombok.Setter;

/** /mypage/profile 저장 폼 바인딩용. */
@Getter
@Setter
public class ProfileUpdateForm {
    private String nickname;
    private String phone;
    private Long representativeSportId;
    private Long regionId;

    @Override
    public String toString() {
        return "ProfileUpdateForm{nickname='" + nickname + "', phone='" + phone
                + "', representativeSportId=" + representativeSportId
                + ", regionId=" + regionId + "}";
    }
}
