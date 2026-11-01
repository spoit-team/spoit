-- RoomManagementMapper integration fixture for an isolated, disposable MySQL test database.
-- INSERT statements only: the enclosing test transaction rolls these rows back.

INSERT INTO app_user (
    user_id, email, password_hash, nickname, phone, role, created_at
) VALUES
    (9201, 'room-manager-a@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
     '방관리자에이', '010-9200-0001', '시설 관리자', '2026-10-20 09:00:00'),
    (9202, 'room-manager-b@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
     '방관리자비', '010-9200-0002', '시설 관리자', '2026-10-20 09:00:00'),
    (9203, 'room-host@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
     '호스트비', '010-9200-0003', 'USER', '2026-10-20 09:00:00'),
    (9204, 'room-user-1@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
     '신청자하나', '010-9200-0004', 'USER', '2026-10-20 09:00:00'),
    (9205, 'room-user-2@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
     '참가자둘', '010-9200-0005', 'USER', '2026-10-20 09:00:00'),
    (9206, 'room-user-3@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
     '참가자셋', '010-9200-0006', 'USER', '2026-10-20 09:00:00'),
    (9207, 'room-manager-c@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
     '방관리자씨', '010-9200-0007', '시설 관리자', '2026-10-20 09:00:00');

INSERT INTO region (region_id, parent_region_id, name) VALUES
    (9201, NULL, '매치 관리 테스트 지역');

INSERT INTO sport (sport_id, name) VALUES
    (9201, 'Match 테스트 농구'),
    (9202, 'Match 테스트 축구'),
    -- 시설 알파가 제공하지만 담당 매칭방에는 없는 종목: 선택지에는 나와야 한다.
    (9203, 'Match 테스트 배드민턴'),
    -- 삭제된 시설에만 매핑된 종목: 선택지에 나오면 안 된다.
    (9204, 'Match 테스트 테니스');

INSERT INTO facility (
    facility_id, region_id, name, address, latitude, longitude,
    concurrent_match_limit, status, deleted_at
) VALUES
    (9301, 9201, '관리 시설 알파', '테스트 주소 알파', 37.5000000, 127.0000000,
     3, '운영 중', NULL),
    (9302, 9201, '삭제된 관리 시설', '테스트 주소 삭제', 37.5001000, 127.0001000,
     2, '운영 중', '2026-10-01 10:00:00'),
    (9303, 9201, '다른 관리 시설', '테스트 주소 다른', 37.5002000, 127.0002000,
     2, '운영 중', NULL),
    (9304, 9201, '관리 시설 베타', '테스트 주소 베타', 37.5003000, 127.0003000,
     2, '운영 중', NULL),
    -- 매칭방이 하나도 없는 담당 시설: 종목 선택지는 여전히 나와야 한다.
    (9305, 9201, '관리 시설 감마', '테스트 주소 감마', 37.5004000, 127.0004000,
     2, '운영 중', NULL);

INSERT INTO facility_manager (facility_id, user_id, assigned_at) VALUES
    (9301, 9201, '2026-10-20 09:00:00'),
    (9302, 9201, '2026-10-20 09:00:00'),
    (9303, 9202, '2026-10-20 09:00:00'),
    (9304, 9201, '2026-10-20 09:00:00'),
    -- 매칭방이 없는 시설만 담당하는 관리자.
    (9305, 9207, '2026-10-20 09:00:00');

INSERT INTO facility_sport (
    facility_id, sport_id, default_min, default_max, default_fee, default_skill
) VALUES
    (9301, 9201, 1, 8, 0.00, '무관'),
    (9301, 9202, 1, 10, 0.00, '무관'),
    (9301, 9203, 1, 10, 0.00, '무관'),
    (9302, 9204, 1, 6, 0.00, '무관'),
    (9302, 9201, 1, 6, 0.00, '무관'),
    (9303, 9201, 1, 6, 0.00, '무관'),
    (9304, 9202, 1, 6, 0.00, '무관'),
    (9305, 9201, 1, 6, 0.00, '무관');

INSERT INTO club (
    club_id, sport_id, club_name, intro, max_members, recruit_status,
    image_url, activity_days, activity_start_time, activity_end_time,
    deleted_at, created_at
) VALUES
    (9601, 9201, 'Match 테스트 동호회', '테스트 소개', 10, '모집중',
     NULL, '2,4', '19:00:00', '21:00:00', NULL, '2026-10-20 09:00:00');

-- Ordered by start_at in assertions. 9402 crosses midnight.
INSERT INTO social_match (
    match_id, facility_id, sport_id, created_by, title, start_at, end_at,
    min_participants, max_participants, team_a_capacity, team_b_capacity,
    fee_per_person, required_skill, status, assignment_method, rating_status
) VALUES
    (9401, 9301, 9201, 9203, '금요 농구 모집', '2026-11-06 18:00:00', '2026-11-06 20:00:00',
     2, 8, 4, 4, 0.00, '무관', '모집 중', 'MANUAL', 'WAITING'),
    (9402, 9301, 9202, 9203, '토요 축구 매칭', '2026-11-07 22:00:00', '2026-11-08 00:30:00',
     2, 10, 5, 5, 0.00, '무관', '매칭 완료', 'MANUAL', 'WAITING'),
    (9403, 9304, 9202, 9201, '일요 축구 종료', '2026-10-25 09:00:00', '2026-10-25 11:00:00',
     2, 6, 3, 3, 0.00, '무관', '종료', 'MANUAL', 'WAITING'),
    (9404, 9301, 9201, 9203, '취소된 농구 방', '2026-11-08 10:00:00', '2026-11-08 12:00:00',
     2, 6, 3, 3, 0.00, '무관', '취소', 'MANUAL', 'WAITING'),
    -- 삭제된 시설의 방: 담당자여도 목록·상세에 나오면 안 된다.
    (9405, 9302, 9201, 9203, '삭제 시설 방', '2026-11-09 10:00:00', '2026-11-09 12:00:00',
     2, 6, 3, 3, 0.00, '무관', '모집 중', 'MANUAL', 'WAITING'),
    -- 다른 관리자의 시설 방: 조회되면 안 된다.
    (9406, 9303, 9201, 9203, '다른 관리자 방', '2026-11-09 14:00:00', '2026-11-09 16:00:00',
     2, 6, 3, 3, 0.00, '무관', '모집 중', 'MANUAL', 'WAITING');

INSERT INTO match_application (
    application_id, match_id, applicant_user_id, club_id, application_type,
    status, receipt_no, created_at, confirmed_at, cancelled_at, cancelled_by
) VALUES
    -- 정원 점유: 확정 대기 개인 신청 1건
    (9501, 9401, 9204, NULL, '개인', '대기', 'ROOM-TEST-9501',
     '2026-11-01 10:00:00', NULL, NULL, NULL),
    (9502, 9401, 9205, NULL, '개인', '확정', 'ROOM-TEST-9502',
     '2026-11-01 10:30:00', '2026-11-01 10:35:00', NULL, NULL),
    (9503, 9401, 9206, NULL, '개인', '취소', 'ROOM-TEST-9503',
     '2026-11-01 11:00:00', NULL, '2026-11-02 09:00:00', 9206),
    -- 동호회 대기 신청은 정원 점유 대상이 아니다.
    (9504, 9401, 9203, 9601, '동호회', '대기', 'ROOM-TEST-9504',
     '2026-11-01 11:30:00', NULL, NULL, NULL),
    (9505, 9402, 9204, NULL, '개인', '확정', 'ROOM-TEST-9505',
     '2026-11-02 10:00:00', '2026-11-02 10:05:00', NULL, NULL),
    (9506, 9402, 9206, NULL, '개인', '확정', 'ROOM-TEST-9506',
     '2026-11-02 10:10:00', '2026-11-02 10:15:00', NULL, NULL);

-- IDs intentionally out of order so ORDER BY joined_at is exercised.
INSERT INTO match_participant (
    participant_id, application_id, match_id, user_id, participation_status,
    match_side, entry_mmr, joined_at, cancelled_at
) VALUES
    (9701, 9502, 9401, 9205, '확정', 'A', 1000.00, '2026-11-01 10:35:00', NULL),
    (9703, 9505, 9402, 9204, '확정', 'A', 1000.00, '2026-11-02 10:05:00', NULL),
    (9704, 9506, 9402, 9206, '확정', 'B', 1000.00, '2026-11-02 10:15:00', NULL),
    -- 취소된 참가자 행은 명단·정원에서 빠진다.
    (9702, 9503, 9401, 9206, '취소', 'B', 1000.00, '2026-11-01 10:40:00', '2026-11-02 09:00:00');
