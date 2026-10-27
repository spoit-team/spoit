-- MAT Mapper integration fixture for an isolated, disposable MySQL test database.
-- INSERT statements only: the enclosing test transaction rolls these rows back.
-- Decimal boundary values test DECIMAL(8,2) mapping, not an approved rating policy.

INSERT INTO app_user (
    user_id, email, password_hash, nickname, phone, role, created_at
) VALUES
    (1, 'mat-user-1@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
     'MAT 테스트 회원 1', '010-0000-0001', 'USER', '2026-10-20 09:00:00'),
    (2, 'mat-user-2@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
     'MAT 테스트 회원 2', '010-0000-0002', 'USER', '2026-10-20 09:00:00');

INSERT INTO sport (sport_id, name) VALUES
    (1, 'MAT 테스트 종목 1'),
    (2, 'MAT 테스트 종목 2');

INSERT INTO region (region_id, parent_region_id, name) VALUES
    (1, NULL, 'MAT 테스트 지역');

INSERT INTO facility (
    facility_id, region_id, name, address, latitude, longitude,
    concurrent_match_limit, status, deleted_at
) VALUES
    (1, 1, 'MAT 테스트 시설', '테스트 전용 주소', 37.5000000, 127.0000000,
     1, '운영중', NULL);

INSERT INTO facility_sport (
    facility_id, sport_id, default_min, default_max, default_fee, default_skill
) VALUES
    (1, 1, 1, 2, 0.00, '무관'),
    (1, 2, 1, 2, 0.00, '무관');

INSERT INTO social_match (
    match_id, facility_id, sport_id, created_by, title, start_at, end_at,
    min_participants, max_participants, team_a_capacity, team_b_capacity,
    fee_per_person, required_skill, status, assignment_method, rating_status
) VALUES
    (1, 1, 1, 1, 'MAT 테스트 경기 1', '2026-10-22 10:00:00', '2026-10-22 12:00:00',
     1, 2, 1, 1, 0.00, '무관', '모집 중', 'MANUAL', 'WAITING'),
    (2, 1, 2, 1, 'MAT 테스트 경기 2', '2026-10-22 10:00:00', '2026-10-22 12:00:00',
     1, 2, 1, 1, 0.00, '무관', '모집 중', 'MANUAL', 'WAITING');

INSERT INTO match_application (
    application_id, match_id, applicant_user_id, club_id, application_type,
    status, receipt_no, created_at, confirmed_at, cancelled_at, cancelled_by
) VALUES
    (30, 1, 1, NULL, '개인', '취소', 'MAT-TEST-RECEIPT-30',
     '2026-10-20 09:00:00', NULL, '2026-10-21 12:00:00', 1),
    (10, 1, 1, NULL, '개인', '대기', 'MAT-TEST-RECEIPT-10',
     '2026-10-20 09:00:00', NULL, NULL, NULL),
    (20, 1, 2, NULL, '개인', '확정', 'MAT-TEST-RECEIPT-20',
     '2026-10-20 09:00:00', '2026-10-20 09:00:00', NULL, NULL),
    (5, 2, 2, NULL, '개인', '확정', 'MAT-TEST-RECEIPT-5',
     '2026-10-20 09:00:00', '2026-10-20 09:00:00', NULL, NULL);

-- Insert IDs out of order so assertions can check the mapper's ORDER BY result.
-- The cancelled user-1 row can coexist with the waiting row under the active-user key.
INSERT INTO match_participant (
    participant_id, application_id, match_id, user_id, participation_status,
    match_side, entry_mmr, joined_at, cancelled_at
) VALUES
    (30, 30, 1, 1, '취소', 'B', 1012.34, '2026-10-20 09:00:00', '2026-10-21 12:00:00'),
    (10, 10, 1, 1, '대기', NULL, 987.65, '2026-10-20 09:00:00', NULL),
    (20, 20, 1, 2, '확정', 'A', 1000.00, '2026-10-20 09:00:00', NULL),
    (5, 5, 2, 2, '확정', 'B', 999999.99, '2026-10-20 09:00:00', NULL);

-- Leave (user_id=2, sport_id=2) absent to test both lookup keys and Optional.empty().
INSERT INTO sport_rating (
    user_id, sport_id, current_mmr, rated_match_count, updated_at
) VALUES
    (1, 1, 1023.45, 7, '2026-10-21 09:30:00'),
    (1, 2, 999999.99, 0, '2026-10-21 09:30:00'),
    (2, 1, -999999.99, 12, '2026-10-21 09:30:00');
