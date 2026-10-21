package co.kr.spoit.preview.member;

import java.util.List;

import co.kr.spoit.preview.member.MemberAuthModels.RegionOptionDto;
import lombok.Builder;
import lombok.Getter;

/**
 * Preview fixture backing the profile edit screen.
 */
@Getter
@Builder
public class ProfilePageDto {

    private final ProfileForm profile;
    private final List<SportOption> sports;
    private final List<RegionOptionDto> districts;
    private final String error;
    private final String message;

    @Getter
    @Builder
    public static class ProfileForm {

        private final Long representativeSportId;
        private final String representativeRatingText;
        private final String representativeRatingNote;
        private final String email;
        private final String nickname;
        private final String phone;
        private final Long regionId;
    }

    @Getter
    @Builder
    public static class SportOption {

        private final Long sportId;
        private final String name;
        private final String ratingText;
        private final String ratingNote;
    }
}
