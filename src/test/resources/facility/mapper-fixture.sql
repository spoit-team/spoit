-- Facility Mapper integration fixture for an isolated, disposable MySQL test database.
-- INSERT statements only: the enclosing test transaction rolls these rows back.

INSERT INTO app_user (
    user_id, email, password_hash, nickname, phone, role, created_at
) VALUES
    (9001, 'facility-manager-a@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
     '시설 관리자 A', '010-9000-0001', '시설 관리자', '2026-10-20 09:00:00'),
    (9002, 'facility-manager-b@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
     '시설 관리자 B', '010-9000-0002', '시설 관리자', '2026-10-20 09:00:00'),
    (9003, 'facility-manager-c@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
     '시설 관리자 C', '010-9000-0003', '시설 관리자', '2026-10-20 09:00:00');

INSERT INTO region (region_id, parent_region_id, name) VALUES
    (1, NULL, '서울특별시'),
    (9001, NULL, '시설 테스트 지역');

INSERT INTO sport (sport_id, name) VALUES
    (9001, '시설 테스트 농구'),
    (9002, '시설 테스트 축구');

INSERT INTO facility (
    facility_id, region_id, name, address, latitude, longitude,
    concurrent_match_limit, status, deleted_at
) VALUES
    (9101, 9001, 'A 관리 시설 1', '테스트 주소 1', 37.5000000, 127.0000000,
     3, '운영 중', NULL),
    (9102, 9001, 'A 관리 시설 2', '테스트 주소 2', 37.5001000, 127.0001000,
     1, '특별 운영', NULL),
    (9103, 9001, 'B 관리 시설', '테스트 주소 3', 37.5002000, 127.0002000,
     2, '운영 중', NULL),
    (9104, 9001, 'A 삭제 시설', '테스트 주소 4', 37.5003000, 127.0003000,
     2, '운영 중', '2026-10-01 10:00:00');

INSERT INTO facility_manager (facility_id, user_id, assigned_at) VALUES
    (9101, 9001, '2026-10-20 09:00:00'),
    (9102, 9001, '2026-10-20 09:00:00'),
    (9103, 9002, '2026-10-20 09:00:00'),
    (9104, 9001, '2026-10-20 09:00:00'),
    -- 9101 has a second manager. The user-scoped filter must still return it once for manager A.
    (9101, 9003, '2026-10-20 09:00:00');

-- 9101 has two sports so the query must aggregate instead of duplicating the facility.
-- 9102 intentionally has no sport row to verify the empty-sports case.
INSERT INTO facility_sport (
    facility_id, sport_id, default_min, default_max, default_fee, default_skill
) VALUES
    (9101, 9002, 1, 2, 0.00, '무관'),
    (9101, 9001, 1, 2, 0.00, '무관');
