SET NAMES utf8mb4;

-- 한 번만 적용한다. 기존 알림은 기존 가입 신청 연결과 본문을 유지한다.
ALTER TABLE notification
    ADD COLUMN club_id BIGINT NULL COMMENT '동호회 관련 알림의 대상 동호회' AFTER join_request_id,
    ADD CONSTRAINT fk_notification_club
        FOREIGN KEY (club_id) REFERENCES club (club_id);
