package co.kr.spoit.preview.member;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/**
 * Preview-only login, signup and shared region option fixtures.
 */
public final class MemberAuthModels {

    private MemberAuthModels() {
    }

    @Getter
    @Builder
    public static class LoginPageDto {

        private final String error;
        private final String message;
        private final String redirect;
        private final String email;
        private final boolean remember;
    }

    @Getter
    @Builder
    public static class SignupPageDto {

        private final SignupForm signupForm;
        private final List<RegionOptionDto> districts;
        private final String error;
        private final String message;

        @Getter
        @Builder
        public static class SignupForm {

            private final boolean agreeService;
            private final boolean agreePrivacy;
            private final boolean marketingOptIn;
            private final String role;
            private final String facilityName;
            private final String email;
            private final String phone;
            private final String nickname;
            private final Long regionId;
        }
    }

    @Getter
    @Builder
    public static class RegionOptionDto {

        private final Long regionId;
        private final String name;
    }
}
