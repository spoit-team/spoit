package co.kr.spoit.preview.admin;

import lombok.AllArgsConstructor;
import lombok.Getter;

public final class AdminCommonModels {

    private AdminCommonModels() {
    }

    @Getter
    @AllArgsConstructor
    public static class AdminUserView {
        private final String role;
        private final String facilityName;
        private final String initial;
        private final String nickname;
        private final String roleLabel;
    }

    @Getter
    @AllArgsConstructor
    public static class AdminNotificationView {
        private final Long notificationId;
        private final boolean read;
        private final String type;
        private final String title;
        private final String body;
        private final String time;
    }
}
