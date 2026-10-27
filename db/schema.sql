-- =====================================================================
-- SPO:IT 통합 ERD v2.0 기준 MySQL 8.0 DDL
-- 대상: MySQL 8.0.16 이상 (CHECK 제약 적용 버전)
-- 규칙: 테이블명 소문자 snake_case / 날짜·시각 DATETIME / 불리언 BOOLEAN
--       논리 삭제 deleted_at / 요일 1=월 ~ 7=일 (java.time.DayOfWeek)
-- 참조 무결성: FK 삭제 동작은 전부 기본값(RESTRICT). 행 삭제 대신 논리 삭제를 사용한다.
-- =====================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS audit_log, penalty, report_evidence, admin_request, notification, report,
    sport_rating_history, sport_rating, match_evaluation_score, match_evaluation,
    sport_evaluation_item, match_result_submission, match_participant, match_application,
    social_match, club_join_request, club_member, club_hashtag, hashtag, club_region, club,
    schedule_change, operating_rule, facility_sport, facility_manager, facility,
    user_preferred_sport, user_region, region, sport, app_user;

SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------------
-- 공통
-- ---------------------------------------------------------------------
CREATE TABLE app_user (
    user_id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '회원을 식별하는 내부 ID',
    email          VARCHAR(255) NOT NULL COMMENT '로그인 이메일, 중복 불가',
    password_hash  VARCHAR(255) NOT NULL COMMENT '비밀번호 검증용 해시값',
    nickname       VARCHAR(50)  NOT NULL COMMENT '화면과 매칭에서 표시할 회원명',
    phone          VARCHAR(20)  NOT NULL COMMENT '연락처',
    role           VARCHAR(30)  NOT NULL COMMENT '일반 회원·시설 관리자·시스템 관리자 등 계정 역할',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '계정 생성 시각',
    deleted_at     DATETIME     NULL COMMENT '탈퇴 시각. NULL이면 미탈퇴, 값이 있으면 탈퇴 회원',
    penalty_time   DATETIME     NULL COMMENT '이용정지 종료 시각. NULL이면 정지 종료 시각 미설정',
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_app_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='회원';

CREATE TABLE sport (
    sport_id  BIGINT      NOT NULL AUTO_INCREMENT COMMENT '종목을 식별하는 내부 ID',
    name      VARCHAR(50) NOT NULL COMMENT '종목명, 중복 불가',
    PRIMARY KEY (sport_id),
    UNIQUE KEY uk_sport_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='종목';

CREATE TABLE region (
    region_id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '지역을 식별하는 내부 ID',
    parent_region_id  BIGINT      NULL COMMENT '바로 위 상위 지역, 시/도면 NULL',
    name              VARCHAR(50) NOT NULL COMMENT '지역명',
    PRIMARY KEY (region_id),
    CONSTRAINT fk_region_parent FOREIGN KEY (parent_region_id) REFERENCES region (region_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='지역';

CREATE TABLE user_region (
    user_id    BIGINT NOT NULL COMMENT '선호 지역을 등록한 회원',
    region_id  BIGINT NOT NULL COMMENT '회원이 선택한 지역',
    PRIMARY KEY (user_id, region_id),
    CONSTRAINT fk_user_region_user   FOREIGN KEY (user_id)   REFERENCES app_user (user_id),
    CONSTRAINT fk_user_region_region FOREIGN KEY (region_id) REFERENCES region (region_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='선호 지역';

CREATE TABLE user_preferred_sport (
    user_id   BIGINT NOT NULL COMMENT '선호 종목을 설정한 회원',
    sport_id  BIGINT NOT NULL COMMENT '회원이 선택한 선호 종목',
    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_preferred_sport_user  FOREIGN KEY (user_id)  REFERENCES app_user (user_id),
    CONSTRAINT fk_user_preferred_sport_sport FOREIGN KEY (sport_id) REFERENCES sport (sport_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='선호 종목';

-- ---------------------------------------------------------------------
-- 시설
-- ---------------------------------------------------------------------
CREATE TABLE facility (
    facility_id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '시설을 식별하는 내부 ID',
    region_id               BIGINT        NOT NULL COMMENT '시설 소재 지역',
    name                    VARCHAR(100)  NOT NULL COMMENT '시설명',
    address                 VARCHAR(255)  NOT NULL COMMENT '시설 주소',
    latitude                DECIMAL(10,7) NOT NULL COMMENT '지도 표시용 위도',
    longitude               DECIMAL(10,7) NOT NULL COMMENT '지도 표시용 경도',
    concurrent_match_limit  INT           NOT NULL COMMENT '같은 시간에 운영 가능한 최대 매칭 수',
    status                  VARCHAR(30)   NOT NULL COMMENT '시설 운영 가능 상태',
    deleted_at              DATETIME      NULL COMMENT '논리 삭제 시각, 값이 있으면 삭제된 시설',
    PRIMARY KEY (facility_id),
    CONSTRAINT fk_facility_region FOREIGN KEY (region_id) REFERENCES region (region_id),
    CONSTRAINT ck_facility_lat   CHECK (latitude  BETWEEN -90  AND 90),
    CONSTRAINT ck_facility_lng   CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_facility_limit CHECK (concurrent_match_limit >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='시설';

CREATE TABLE facility_manager (
    facility_id  BIGINT   NOT NULL COMMENT '담당자가 배정된 시설',
    user_id      BIGINT   NOT NULL COMMENT '시설을 관리하는 회원',
    assigned_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '담당자 배정 시각',
    PRIMARY KEY (facility_id, user_id),
    CONSTRAINT fk_facility_manager_facility FOREIGN KEY (facility_id) REFERENCES facility (facility_id),
    CONSTRAINT fk_facility_manager_user     FOREIGN KEY (user_id)     REFERENCES app_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='담당 시설';

CREATE TABLE facility_sport (
    facility_id    BIGINT        NOT NULL COMMENT '종목을 제공하는 시설',
    sport_id       BIGINT        NOT NULL COMMENT '시설에서 제공하는 종목',
    default_min    INT           NOT NULL COMMENT '매칭방 생성 시 기본 최소 참가 인원',
    default_max    INT           NOT NULL COMMENT '매칭방 생성 시 기본 최대 참가 인원',
    default_fee    DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '참가자 1인당 기본 이용료',
    default_skill  VARCHAR(30)   NOT NULL COMMENT '매칭방 생성 시 기본 실력 조건',
    PRIMARY KEY (facility_id, sport_id),
    CONSTRAINT fk_facility_sport_facility FOREIGN KEY (facility_id) REFERENCES facility (facility_id),
    CONSTRAINT fk_facility_sport_sport    FOREIGN KEY (sport_id)    REFERENCES sport (sport_id),
    CONSTRAINT ck_facility_sport_minmax CHECK (default_min >= 1 AND default_max >= default_min),
    CONSTRAINT ck_facility_sport_fee    CHECK (default_fee >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='시설별 종목·기본 모집 조건';

CREATE TABLE operating_rule (
    operating_rule_id  BIGINT   NOT NULL AUTO_INCREMENT COMMENT '운영 시간 규칙 ID',
    facility_id        BIGINT   NOT NULL COMMENT '규칙이 적용되는 시설',
    weekday            TINYINT  NOT NULL COMMENT '요일, 1=월 ~ 7=일 (java.time.DayOfWeek)',
    open_time          TIME     NOT NULL COMMENT '운영 시작 시각',
    close_time         TIME     NOT NULL COMMENT '운영 종료 시각',
    is_open            BOOLEAN  NOT NULL DEFAULT TRUE COMMENT '운영 여부, 휴무면 false',
    PRIMARY KEY (operating_rule_id),
    CONSTRAINT fk_operating_rule_facility FOREIGN KEY (facility_id) REFERENCES facility (facility_id),
    CONSTRAINT ck_operating_rule_weekday CHECK (weekday BETWEEN 1 AND 7)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='요일별 운영 시간';

CREATE TABLE schedule_change (
    schedule_change_id  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '운영 변경 건 ID',
    facility_id         BIGINT       NOT NULL COMMENT '변경 대상 시설',
    created_by          BIGINT       NOT NULL COMMENT '변경을 등록한 회원',
    start_at            DATETIME     NOT NULL COMMENT '변경 시작 일시',
    end_at              DATETIME     NOT NULL COMMENT '변경 종료 일시',
    change_type         VARCHAR(30)  NOT NULL COMMENT '변경 종류 (임시 개방·폐쇄 등)',
    reason              VARCHAR(255) NOT NULL COMMENT '변경 사유',
    status              VARCHAR(30)  NOT NULL COMMENT '적용·취소 등 처리 상태',
    PRIMARY KEY (schedule_change_id),
    CONSTRAINT fk_schedule_change_facility FOREIGN KEY (facility_id) REFERENCES facility (facility_id),
    CONSTRAINT fk_schedule_change_user     FOREIGN KEY (created_by)  REFERENCES app_user (user_id),
    CONSTRAINT ck_schedule_change_period CHECK (end_at > start_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='날짜별 예외·점검·휴관';

-- ---------------------------------------------------------------------
-- 동호회
-- ---------------------------------------------------------------------
CREATE TABLE club (
    club_id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '동호회 식별자',
    sport_id             BIGINT       NOT NULL COMMENT '주 종목',
    club_name            VARCHAR(50)  NOT NULL COMMENT '동호회명, 활성 동호회끼리 중복 불가 (서비스 로직)',
    intro                VARCHAR(500) NOT NULL COMMENT '동호회 소개, 최대 500자',
    max_members          INT          NOT NULL COMMENT '최대 인원 2~30',
    recruit_status       VARCHAR(10)  NOT NULL DEFAULT '모집중' COMMENT '모집중 또는 마감',
    image_url            VARCHAR(500) NULL COMMENT '대표 이미지 경로, 없으면 이름 앞 2글자 아이콘',
    activity_days        VARCHAR(13)  NOT NULL COMMENT '대표 활동 요일, 쉼표 구분 숫자 예: 2,4',
    activity_start_time  TIME         NOT NULL COMMENT '대표 활동 시작 시각',
    activity_end_time    TIME         NOT NULL COMMENT '대표 활동 종료 시각',
    deleted_at           DATETIME     NULL COMMENT '해체 시각, 값이 있으면 해체된 동호회',
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각 = 창단일',
    PRIMARY KEY (club_id),
    CONSTRAINT fk_club_sport FOREIGN KEY (sport_id) REFERENCES sport (sport_id),
    CONSTRAINT ck_club_max_members    CHECK (max_members BETWEEN 2 AND 30),
    CONSTRAINT ck_club_recruit_status CHECK (recruit_status IN ('모집중', '마감')),
    CONSTRAINT ck_club_activity_days  CHECK (REGEXP_LIKE(activity_days, '^[1-7](,[1-7]){0,6}$'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='동호회';

CREATE TABLE club_region (
    club_id    BIGINT NOT NULL COMMENT '동호회',
    region_id  BIGINT NOT NULL COMMENT '활동 지역, 개수 제한 없음',
    PRIMARY KEY (club_id, region_id),
    CONSTRAINT fk_club_region_club   FOREIGN KEY (club_id)   REFERENCES club (club_id),
    CONSTRAINT fk_club_region_region FOREIGN KEY (region_id) REFERENCES region (region_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='동호회 활동 지역';

CREATE TABLE hashtag (
    hashtag_id  BIGINT      NOT NULL AUTO_INCREMENT COMMENT '해시태그 식별자',
    name        VARCHAR(30) NOT NULL COMMENT '태그명, # 없이 저장, 중복 불가',
    is_default  BOOLEAN     NOT NULL DEFAULT FALSE COMMENT 'true면 지정 태그, false면 자유 입력',
    PRIMARY KEY (hashtag_id),
    UNIQUE KEY uk_hashtag_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='해시태그';

CREATE TABLE club_hashtag (
    club_id     BIGINT  NOT NULL COMMENT '동호회',
    hashtag_id  BIGINT  NOT NULL COMMENT '해시태그',
    sort_order  TINYINT NOT NULL COMMENT '표시 순서 1~5, 1번이 카드 대표 태그',
    PRIMARY KEY (club_id, hashtag_id),
    CONSTRAINT fk_club_hashtag_club    FOREIGN KEY (club_id)    REFERENCES club (club_id),
    CONSTRAINT fk_club_hashtag_hashtag FOREIGN KEY (hashtag_id) REFERENCES hashtag (hashtag_id),
    CONSTRAINT ck_club_hashtag_order CHECK (sort_order BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='동호회 해시태그';

CREATE TABLE club_member (
    club_member_id  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '소속 이력 식별자, 재가입 시 새 행',
    club_id         BIGINT       NOT NULL COMMENT '동호회',
    user_id         BIGINT       NOT NULL COMMENT '회원',
    role            VARCHAR(10)  NOT NULL COMMENT '동호회장 또는 동호회원',
    joined_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '가입 시각',
    left_at         DATETIME     NULL COMMENT '탈퇴 또는 내보내기 시각, 값이 있으면 이탈',
    leave_type      VARCHAR(10)  NULL COMMENT '탈퇴 또는 내보내기',
    kick_reason     VARCHAR(255) NULL COMMENT '내보내기 사유',
    PRIMARY KEY (club_member_id),
    KEY idx_club_member_club_active (club_id, left_at),
    CONSTRAINT fk_club_member_club FOREIGN KEY (club_id) REFERENCES club (club_id),
    CONSTRAINT fk_club_member_user FOREIGN KEY (user_id) REFERENCES app_user (user_id),
    CONSTRAINT ck_club_member_role  CHECK (role IN ('동호회장', '동호회원')),
    CONSTRAINT ck_club_member_leave CHECK (
        (left_at IS NULL AND leave_type IS NULL AND kick_reason IS NULL)
        OR (left_at IS NOT NULL AND leave_type = '탈퇴' AND kick_reason IS NULL)
        OR (left_at IS NOT NULL AND leave_type = '내보내기')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='동호회 소속 및 이탈 이력';

CREATE TABLE club_join_request (
    request_id    BIGINT      NOT NULL AUTO_INCREMENT COMMENT '가입 신청 식별자',
    club_id       BIGINT      NOT NULL COMMENT '동호회',
    user_id       BIGINT      NOT NULL COMMENT '신청 회원',
    message       TEXT        NULL COMMENT '가입 신청 자기소개, 최대 500자, 처리 시 NULL',
    status        VARCHAR(10) NOT NULL DEFAULT '대기중' COMMENT '대기중, 승인, 거절, 취소',
    requested_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '신청 시각',
    processed_at  DATETIME    NULL COMMENT '처리 시각, 대기중이면 NULL',
    PRIMARY KEY (request_id),
    CONSTRAINT fk_club_join_request_club FOREIGN KEY (club_id) REFERENCES club (club_id),
    CONSTRAINT fk_club_join_request_user FOREIGN KEY (user_id) REFERENCES app_user (user_id),
    CONSTRAINT ck_club_join_request_status  CHECK (status IN ('대기중', '승인', '거절', '취소')),
    CONSTRAINT ck_club_join_request_message CHECK (message IS NULL OR CHAR_LENGTH(message) <= 500),
    CONSTRAINT ck_club_join_request_processed CHECK (
        (status = '대기중' AND processed_at IS NULL) OR (status <> '대기중' AND processed_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='동호회 가입 신청';

-- ---------------------------------------------------------------------
-- 매칭
-- ---------------------------------------------------------------------
CREATE TABLE social_match (
    match_id                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '매칭방·경기 회차 ID',
    facility_id               BIGINT        NOT NULL COMMENT '경기 시설, facility_sport 조합 참조',
    sport_id                  BIGINT        NOT NULL COMMENT '경기 종목, facility_sport 조합 참조',
    created_by                BIGINT        NOT NULL COMMENT '매칭방을 등록한 회원',
    title                     VARCHAR(100)  NOT NULL COMMENT '매칭방 제목',
    start_at                  DATETIME      NOT NULL COMMENT '경기 시작 일시',
    end_at                    DATETIME      NOT NULL COMMENT '경기 종료 일시',
    min_participants          INT           NOT NULL COMMENT '최소 참가 인원',
    max_participants          INT           NOT NULL COMMENT '최대 참가 인원',
    team_a_capacity           INT           NOT NULL COMMENT 'A팀 정원',
    team_b_capacity           INT           NOT NULL COMMENT 'B팀 정원',
    fee_per_person            DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '해당 회차 1인당 이용료',
    required_skill            VARCHAR(30)   NOT NULL COMMENT '요구 실력 조건 (내부 MMR과 별개)',
    status                    VARCHAR(20)   NOT NULL COMMENT '매칭방 상태 (모집 중, 매칭 완료, 종료, 취소)',
    assignment_method         VARCHAR(10)   NOT NULL DEFAULT 'MANUAL' COMMENT 'A/B팀 배정 방식, MANUAL 기본, AUTO 추천',
    assigned_by               BIGINT        NULL COMMENT 'A/B팀 배정을 확정한 회원',
    assignment_confirmed_at   DATETIME      NULL COMMENT 'A/B팀 배정 확정 시각',
    captain_a_participant_id  BIGINT        NULL COMMENT 'A팀 팀장, 같은 경기 참가자 (FK는 match_participant 생성 후 추가)',
    captain_b_participant_id  BIGINT        NULL COMMENT 'B팀 팀장, 같은 경기 참가자 (FK는 match_participant 생성 후 추가)',
    result_status             VARCHAR(20)   NULL COMMENT '경기 결과 처리 상태',
    final_result              VARCHAR(10)   NULL COMMENT '확정 결과 A_WIN, DRAW, B_WIN (스코어 없음)',
    result_confirmed_at       DATETIME      NULL COMMENT '경기 결과 확정 시각',
    result_reviewed_by        BIGINT        NULL COMMENT '경기 결과를 검토한 관리자',
    rating_status             VARCHAR(10)   NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING, READY, APPLIED, EXCLUDED',
    rating_processed_at       DATETIME      NULL COMMENT '레이팅 반영 처리 시각',
    cancelled_at              DATETIME      NULL COMMENT '매칭방 취소 시각',
    PRIMARY KEY (match_id),
    KEY idx_social_match_search (sport_id, start_at),
    KEY idx_social_match_status (status, start_at),
    CONSTRAINT fk_social_match_facility_sport FOREIGN KEY (facility_id, sport_id) REFERENCES facility_sport (facility_id, sport_id),
    CONSTRAINT fk_social_match_creator        FOREIGN KEY (created_by)         REFERENCES app_user (user_id),
    CONSTRAINT fk_social_match_assigned_by    FOREIGN KEY (assigned_by)        REFERENCES app_user (user_id),
    CONSTRAINT fk_social_match_reviewed_by    FOREIGN KEY (result_reviewed_by) REFERENCES app_user (user_id),
    CONSTRAINT ck_social_match_period   CHECK (end_at > start_at),
    CONSTRAINT ck_social_match_minmax   CHECK (min_participants >= 1 AND max_participants >= min_participants),
    CONSTRAINT ck_social_match_capacity CHECK (team_a_capacity >= 0 AND team_b_capacity >= 0
                                               AND team_a_capacity + team_b_capacity = max_participants),
    CONSTRAINT ck_social_match_fee      CHECK (fee_per_person >= 0),
    CONSTRAINT ck_social_match_assign   CHECK (assignment_method IN ('MANUAL', 'AUTO')),
    CONSTRAINT ck_social_match_result   CHECK (final_result IS NULL OR final_result IN ('A_WIN', 'DRAW', 'B_WIN')),
    CONSTRAINT ck_social_match_rating   CHECK (rating_status IN ('WAITING', 'READY', 'APPLIED', 'EXCLUDED')),
    CONSTRAINT ck_social_match_captains CHECK (captain_a_participant_id IS NULL OR captain_b_participant_id IS NULL
                                               OR captain_a_participant_id <> captain_b_participant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='소셜 매칭방·경기';

CREATE TABLE match_application (
    application_id     BIGINT      NOT NULL AUTO_INCREMENT COMMENT '신청 건 ID',
    match_id           BIGINT      NOT NULL COMMENT '신청 대상 매칭방',
    applicant_user_id  BIGINT      NOT NULL COMMENT '신청을 제출한 회원 또는 동호회장',
    club_id            BIGINT      NULL COMMENT '동호회 단위 신청이면 신청 동호회, 개인 신청이면 NULL',
    application_type   VARCHAR(10) NOT NULL COMMENT '개인 또는 동호회 단위 신청 구분',
    status             VARCHAR(10) NOT NULL COMMENT '신청 상태 (대기, 확정, 취소, 반려)',
    receipt_no         VARCHAR(30) NOT NULL COMMENT '고유 신청 접수 번호',
    created_at         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '신청 접수 시각',
    confirmed_at       DATETIME    NULL COMMENT '신청 확정 시각',
    cancelled_at       DATETIME    NULL COMMENT '신청 취소 시각',
    cancelled_by       BIGINT      NULL COMMENT '취소를 처리한 회원',
    PRIMARY KEY (application_id),
    UNIQUE KEY uk_match_application_receipt (receipt_no),
    UNIQUE KEY uk_match_application_match (application_id, match_id),
    CONSTRAINT fk_match_application_match     FOREIGN KEY (match_id)          REFERENCES social_match (match_id),
    CONSTRAINT fk_match_application_applicant FOREIGN KEY (applicant_user_id) REFERENCES app_user (user_id),
    CONSTRAINT fk_match_application_club      FOREIGN KEY (club_id)           REFERENCES club (club_id),
    CONSTRAINT fk_match_application_canceller FOREIGN KEY (cancelled_by)      REFERENCES app_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='개인·동호회 참가 신청';

CREATE TABLE match_participant (
    participant_id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '참가자 행 ID',
    application_id        BIGINT       NOT NULL COMMENT '이 참가자를 포함한 신청',
    match_id              BIGINT       NOT NULL COMMENT '참가할 매칭방, 신청의 매칭방과 같아야 함',
    user_id               BIGINT       NOT NULL COMMENT '실제 참가 회원',
    participation_status  VARCHAR(10)  NOT NULL COMMENT '참가 상태 (대기, 확정, 취소, 반려)',
    match_side            CHAR(1)      NULL COMMENT '배정된 A 또는 B, 미배정이면 NULL',
    entry_mmr             DECIMAL(8,2) NOT NULL DEFAULT 1000 COMMENT '신청 당시 sport_rating.current_mmr 스냅샷',
    joined_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '참가자 명단 등록 시각',
    cancelled_at          DATETIME     NULL COMMENT '이 참가자의 취소 시각',
    active_match_user     BIGINT AS (CASE WHEN participation_status IN ('대기', '확정') THEN user_id END) STORED
                          COMMENT '활성 참가자 중복 방지용 값 (생성 컬럼)',
    PRIMARY KEY (participant_id),
    UNIQUE KEY uk_match_participant_match (match_id, participant_id),
    UNIQUE KEY uk_match_participant_active (match_id, active_match_user),
    KEY idx_match_participant_user (user_id),
    CONSTRAINT fk_match_participant_application FOREIGN KEY (application_id, match_id) REFERENCES match_application (application_id, match_id),
    CONSTRAINT fk_match_participant_match       FOREIGN KEY (match_id) REFERENCES social_match (match_id),
    CONSTRAINT fk_match_participant_user        FOREIGN KEY (user_id)  REFERENCES app_user (user_id),
    CONSTRAINT ck_match_participant_side CHECK (match_side IS NULL OR match_side IN ('A', 'B'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='경기 참가자·A/B 배정';

-- 순환 참조: social_match ↔ match_participant. 팀장은 같은 경기 참가자만 지정 가능하도록 (match_id, participant_id) 복합 FK
ALTER TABLE social_match
    ADD CONSTRAINT fk_social_match_captain_a FOREIGN KEY (match_id, captain_a_participant_id)
        REFERENCES match_participant (match_id, participant_id),
    ADD CONSTRAINT fk_social_match_captain_b FOREIGN KEY (match_id, captain_b_participant_id)
        REFERENCES match_participant (match_id, participant_id);

CREATE TABLE match_result_submission (
    submission_id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '결과 제출 건 ID',
    match_id                BIGINT      NOT NULL COMMENT '결과를 제출한 경기',
    captain_participant_id  BIGINT      NOT NULL COMMENT '결과를 제출한 A 또는 B팀 팀장',
    reported_result         VARCHAR(10) NOT NULL COMMENT 'A_WIN, DRAW, B_WIN',
    submission_status       VARCHAR(20) NOT NULL COMMENT '제출 처리 상태',
    submitted_at            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '결과 제출 시각',
    PRIMARY KEY (submission_id),
    CONSTRAINT fk_match_result_submission_match   FOREIGN KEY (match_id) REFERENCES social_match (match_id),
    CONSTRAINT fk_match_result_submission_captain FOREIGN KEY (match_id, captain_participant_id)
        REFERENCES match_participant (match_id, participant_id),
    CONSTRAINT ck_match_result_submission_result CHECK (reported_result IN ('A_WIN', 'DRAW', 'B_WIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='팀장별 승무패 제출';

-- ---------------------------------------------------------------------
-- 평가·레이팅
-- ---------------------------------------------------------------------
CREATE TABLE sport_evaluation_item (
    item_id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '평가 항목 ID',
    sport_id       BIGINT       NOT NULL COMMENT '평가 항목이 속한 종목',
    item_code      VARCHAR(30)  NOT NULL COMMENT '평가 항목 코드',
    item_name      VARCHAR(50)  NOT NULL COMMENT '평가 항목명',
    weight         DECIMAL(5,2) NOT NULL COMMENT '종합 점수 계산 시 가중치',
    scale_max      TINYINT      NOT NULL DEFAULT 10 COMMENT '점수 척도 최대값, 10점 확정',
    display_order  INT          NOT NULL COMMENT '화면 표시 순서',
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '현재 평가에 사용하는 항목 여부',
    PRIMARY KEY (item_id),
    UNIQUE KEY uk_sport_evaluation_item_sport (item_id, sport_id),
    CONSTRAINT fk_sport_evaluation_item_sport FOREIGN KEY (sport_id) REFERENCES sport (sport_id),
    CONSTRAINT ck_sport_evaluation_item_scale  CHECK (scale_max = 10),
    CONSTRAINT ck_sport_evaluation_item_weight CHECK (weight >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='종목별 0~10 평가문항';

CREATE TABLE match_evaluation (
    evaluation_id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '평가 건 ID',
    match_id                  BIGINT       NOT NULL COMMENT '평가 대상 경기',
    sport_id                  BIGINT       NOT NULL COMMENT '평가 종목',
    evaluator_participant_id  BIGINT       NOT NULL COMMENT '평가한 팀장',
    target_participant_id     BIGINT       NOT NULL COMMENT '평가 대상 참가자',
    overall_score             DECIMAL(4,2) NULL COMMENT '평가자별 0에서 10점 평균',
    scale_max                 TINYINT      NOT NULL DEFAULT 10 COMMENT '점수 척도 최대값, 10점 확정',
    status                    VARCHAR(20)  NOT NULL COMMENT '평가 상태',
    submitted_at              DATETIME     NULL COMMENT '평가 제출 시각',
    PRIMARY KEY (evaluation_id),
    UNIQUE KEY uk_match_evaluation_sport (evaluation_id, sport_id),
    CONSTRAINT fk_match_evaluation_match     FOREIGN KEY (match_id) REFERENCES social_match (match_id),
    CONSTRAINT fk_match_evaluation_sport     FOREIGN KEY (sport_id) REFERENCES sport (sport_id),
    CONSTRAINT fk_match_evaluation_evaluator FOREIGN KEY (match_id, evaluator_participant_id)
        REFERENCES match_participant (match_id, participant_id),
    CONSTRAINT fk_match_evaluation_target    FOREIGN KEY (match_id, target_participant_id)
        REFERENCES match_participant (match_id, participant_id),
    CONSTRAINT ck_match_evaluation_scale CHECK (scale_max = 10),
    CONSTRAINT ck_match_evaluation_score CHECK (overall_score IS NULL OR overall_score BETWEEN 0 AND 10),
    CONSTRAINT ck_match_evaluation_self  CHECK (evaluator_participant_id <> target_participant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='경기별 팀장 기술평가';

CREATE TABLE match_evaluation_score (
    evaluation_id       BIGINT      NOT NULL COMMENT '평가 건',
    item_id             BIGINT      NOT NULL COMMENT '평가 항목',
    sport_id            BIGINT      NOT NULL COMMENT '평가와 항목의 종목 일치 확인용',
    score               TINYINT     NULL COMMENT '0점 유효, 관찰불가는 NULL',
    observation_status  VARCHAR(15) NOT NULL COMMENT 'OBSERVED 또는 NOT_OBSERVED',
    PRIMARY KEY (evaluation_id, item_id),
    CONSTRAINT fk_match_evaluation_score_eval  FOREIGN KEY (evaluation_id, sport_id)
        REFERENCES match_evaluation (evaluation_id, sport_id),
    CONSTRAINT fk_match_evaluation_score_item  FOREIGN KEY (item_id, sport_id)
        REFERENCES sport_evaluation_item (item_id, sport_id),
    CONSTRAINT fk_match_evaluation_score_sport FOREIGN KEY (sport_id) REFERENCES sport (sport_id),
    CONSTRAINT ck_match_evaluation_score_obs CHECK (
        (observation_status = 'OBSERVED'     AND score BETWEEN 0 AND 10)
        OR (observation_status = 'NOT_OBSERVED' AND score IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='항목별 0~10 점수';

CREATE TABLE sport_rating (
    user_id            BIGINT       NOT NULL COMMENT '회원',
    sport_id           BIGINT       NOT NULL COMMENT '종목',
    current_mmr        DECIMAL(8,2) NOT NULL DEFAULT 1000 COMMENT '내부 MMR, 기본 1000',
    rated_match_count  INT          NOT NULL DEFAULT 0 COMMENT '반영된 경기 수 (배치 진행 상태 구분)',
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'MMR 갱신 시각',
    PRIMARY KEY (user_id, sport_id),
    CONSTRAINT fk_sport_rating_user  FOREIGN KEY (user_id)  REFERENCES app_user (user_id),
    CONSTRAINT fk_sport_rating_sport FOREIGN KEY (sport_id) REFERENCES sport (sport_id),
    CONSTRAINT ck_sport_rating_count CHECK (rated_match_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='개인·종목 내부 MMR';

CREATE TABLE sport_rating_history (
    history_id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '레이팅 반영 이력 ID',
    match_id           BIGINT       NOT NULL COMMENT '반영 대상 경기',
    participant_id     BIGINT       NOT NULL COMMENT '반영 대상 참가자',
    user_id            BIGINT       NOT NULL COMMENT '회원',
    sport_id           BIGINT       NOT NULL COMMENT '종목',
    mmr_before         DECIMAL(8,2) NOT NULL COMMENT '반영 이전 MMR',
    mmr_after          DECIMAL(8,2) NOT NULL COMMENT '반영 이후 MMR',
    elo_delta          DECIMAL(8,2) NOT NULL COMMENT '승무패 기본 증감',
    evaluation_delta   DECIMAL(8,2) NOT NULL DEFAULT 0 COMMENT '기술평가 개인 보정 증감',
    final_delta        DECIMAL(8,2) NOT NULL COMMENT 'Elo + 평가보정',
    evaluation_score   DECIMAL(4,2) NULL COMMENT '보정에 사용한 0에서 10점 점수',
    actual_score       DECIMAL(2,1) NOT NULL COMMENT '승 1, 무 0.5, 패 0',
    expected_score     DECIMAL(5,4) NOT NULL COMMENT '팀 평균 Elo 기대 점수',
    team_avg_mmr_a     DECIMAL(8,2) NOT NULL COMMENT '반영 시점 A팀 평균 MMR',
    team_avg_mmr_b     DECIMAL(8,2) NOT NULL COMMENT '반영 시점 B팀 평균 MMR',
    algorithm_version  VARCHAR(20)  NOT NULL COMMENT '적용 공식 버전',
    calculated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '계산 시각',
    PRIMARY KEY (history_id),
    UNIQUE KEY uk_sport_rating_history_participant (participant_id),
    KEY idx_sport_rating_history_user_sport (user_id, sport_id, calculated_at),
    CONSTRAINT fk_sport_rating_history_match       FOREIGN KEY (match_id) REFERENCES social_match (match_id),
    CONSTRAINT fk_sport_rating_history_participant FOREIGN KEY (match_id, participant_id)
        REFERENCES match_participant (match_id, participant_id),
    CONSTRAINT fk_sport_rating_history_user        FOREIGN KEY (user_id)  REFERENCES app_user (user_id),
    CONSTRAINT fk_sport_rating_history_sport       FOREIGN KEY (sport_id) REFERENCES sport (sport_id),
    CONSTRAINT ck_sport_rating_history_actual   CHECK (actual_score IN (0, 0.5, 1)),
    CONSTRAINT ck_sport_rating_history_expected CHECK (expected_score BETWEEN 0 AND 1),
    CONSTRAINT ck_sport_rating_history_eval     CHECK (evaluation_score IS NULL OR evaluation_score BETWEEN 0 AND 10),
    CONSTRAINT ck_sport_rating_history_delta    CHECK (final_delta = elo_delta + evaluation_delta
                                                       AND mmr_after = mmr_before + final_delta)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Elo 및 평가보정 이력';

-- ---------------------------------------------------------------------
-- 신고·제재·알림·관리자
-- ---------------------------------------------------------------------
CREATE TABLE report (
    report_id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '신고 건 ID',
    reporter_user_id  BIGINT       NULL COMMENT '신고를 접수한 회원',
    target_user_id    BIGINT       NOT NULL COMMENT '신고 대상 회원',
    match_id          BIGINT       NULL COMMENT '신고 관련 경기',
    reviewed_by       BIGINT       NULL COMMENT '신고를 검토한 관리자',
    category          VARCHAR(30)  NOT NULL COMMENT '신고 사유 분류 코드',
    summary           TEXT         NOT NULL COMMENT '신고 내용',
    status            VARCHAR(20)  NOT NULL COMMENT '신고 처리 상태',
    resolution        VARCHAR(255) NULL COMMENT '검토 결과 또는 처리 결론',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '신고 접수 시각',
    reviewed_at       DATETIME     NULL COMMENT '신고 검토 완료 시각',
    PRIMARY KEY (report_id),
    CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_user_id) REFERENCES app_user (user_id),
    CONSTRAINT fk_report_target   FOREIGN KEY (target_user_id)   REFERENCES app_user (user_id),
    CONSTRAINT fk_report_match    FOREIGN KEY (match_id)         REFERENCES social_match (match_id),
    CONSTRAINT fk_report_reviewer FOREIGN KEY (reviewed_by)      REFERENCES app_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='신고';

CREATE TABLE report_evidence (
    evidence_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '근거 자료 ID',
    report_id       BIGINT       NOT NULL COMMENT '근거가 속한 신고',
    evidence_type   VARCHAR(30)  NOT NULL COMMENT '근거 자료 종류 코드',
    reference_code  VARCHAR(255) NOT NULL COMMENT '원본 파일·기록 참조 코드',
    summary         TEXT         NULL COMMENT '근거 요약 또는 검토 설명',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '근거 등록 시각',
    PRIMARY KEY (evidence_id),
    CONSTRAINT fk_report_evidence_report FOREIGN KEY (report_id) REFERENCES report (report_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='신고 근거';

CREATE TABLE penalty (
    penalty_id      BIGINT      NOT NULL AUTO_INCREMENT COMMENT '제재 건 ID',
    target_user_id  BIGINT      NOT NULL COMMENT '제재 대상 회원',
    report_id       BIGINT      NULL COMMENT '제재 근거 신고',
    issued_by       BIGINT      NOT NULL COMMENT '제재를 부여한 관리자',
    penalty_type    VARCHAR(30) NOT NULL COMMENT '제재 종류 코드',
    start_at        DATETIME    NOT NULL COMMENT '제재 시작 시각',
    end_at          DATETIME    NULL COMMENT '제재 종료 시각, NULL이면 기한 없음',
    status          VARCHAR(20) NOT NULL COMMENT '제재 처리 상태',
    reason          TEXT        NOT NULL COMMENT '제재 사유와 설명',
    released_by     BIGINT      NULL COMMENT '제재를 해제한 관리자',
    released_at     DATETIME    NULL COMMENT '제재 해제 시각',
    PRIMARY KEY (penalty_id),
    KEY idx_penalty_target_active (target_user_id, status),
    CONSTRAINT fk_penalty_target   FOREIGN KEY (target_user_id) REFERENCES app_user (user_id),
    CONSTRAINT fk_penalty_report   FOREIGN KEY (report_id)      REFERENCES report (report_id),
    CONSTRAINT fk_penalty_issuer   FOREIGN KEY (issued_by)      REFERENCES app_user (user_id),
    CONSTRAINT fk_penalty_releaser FOREIGN KEY (released_by)    REFERENCES app_user (user_id),
    CONSTRAINT ck_penalty_period  CHECK (end_at IS NULL OR end_at > start_at),
    CONSTRAINT ck_penalty_release CHECK ((released_by IS NULL) = (released_at IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='경고·이용 제한';

CREATE TABLE notification (
    notification_id    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '알림 ID',
    recipient_user_id  BIGINT       NOT NULL COMMENT '알림 수신 회원',
    application_id     BIGINT       NULL COMMENT '신청 관련 알림이면 연결할 신청',
    match_id           BIGINT       NULL COMMENT '매칭방 운영 알림이면 연결할 경기',
    report_id          BIGINT       NULL COMMENT '신고 처리 결과 알림이면 연결할 신고',
    join_request_id    BIGINT       NULL COMMENT '동호회 가입 신청 알림이면 연결할 신청',
    notification_type  VARCHAR(30)  NOT NULL COMMENT '알림 종류 코드',
    title              VARCHAR(100) NOT NULL COMMENT '알림 제목',
    body               TEXT         NOT NULL COMMENT '알림 본문',
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '알림 생성 시각',
    read_at            DATETIME     NULL COMMENT '읽은 시각',
    delivered_at       DATETIME     NULL COMMENT 'WebSocket 등 전달 완료 시각',
    PRIMARY KEY (notification_id),
    KEY idx_notification_inbox (recipient_user_id, read_at, created_at),
    CONSTRAINT fk_notification_recipient    FOREIGN KEY (recipient_user_id) REFERENCES app_user (user_id),
    CONSTRAINT fk_notification_application  FOREIGN KEY (application_id)    REFERENCES match_application (application_id),
    CONSTRAINT fk_notification_match        FOREIGN KEY (match_id)          REFERENCES social_match (match_id),
    CONSTRAINT fk_notification_report       FOREIGN KEY (report_id)         REFERENCES report (report_id),
    CONSTRAINT fk_notification_join_request FOREIGN KEY (join_request_id)   REFERENCES club_join_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='저장 알림';

CREATE TABLE admin_request (
    request_id      BIGINT      NOT NULL AUTO_INCREMENT COMMENT '권한 신청 건 ID',
    user_id         BIGINT      NOT NULL COMMENT '권한을 신청한 회원',
    facility_id     BIGINT      NULL COMMENT '시설 관리자 권한 신청 대상 시설',
    requested_role  VARCHAR(30) NOT NULL COMMENT '신청한 관리자 역할',
    status          VARCHAR(20) NOT NULL COMMENT '심사 상태',
    reviewed_by     BIGINT      NULL COMMENT '심사한 관리자',
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '신청 접수 시각',
    reviewed_at     DATETIME    NULL COMMENT '심사 완료 시각',
    PRIMARY KEY (request_id),
    CONSTRAINT fk_admin_request_user     FOREIGN KEY (user_id)     REFERENCES app_user (user_id),
    CONSTRAINT fk_admin_request_facility FOREIGN KEY (facility_id) REFERENCES facility (facility_id),
    CONSTRAINT fk_admin_request_reviewer FOREIGN KEY (reviewed_by) REFERENCES app_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='관리자 권한 신청';

CREATE TABLE audit_log (
    audit_log_id   BIGINT      NOT NULL AUTO_INCREMENT COMMENT '조치 기록 ID',
    actor_user_id  BIGINT      NULL COMMENT '조치를 실행한 관리자',
    action         VARCHAR(50) NOT NULL COMMENT '조치 종류 코드',
    target_type    VARCHAR(50) NOT NULL COMMENT '조치 대상 테이블·도메인',
    target_id      VARCHAR(50) NOT NULL COMMENT '조치 대상 ID (문자열)',
    detail         TEXT        NULL COMMENT '조치 상세',
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '조치 기록 시각',
    PRIMARY KEY (audit_log_id),
    KEY idx_audit_log_target (target_type, target_id),
    CONSTRAINT fk_audit_log_actor FOREIGN KEY (actor_user_id) REFERENCES app_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='관리자 조치 기록';
