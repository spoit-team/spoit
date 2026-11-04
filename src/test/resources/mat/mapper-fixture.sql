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

-- MatchAssignmentMapper: user 1 manages facility 1 only, and facility 2 is deleted.
INSERT INTO facility (
    facility_id, region_id, name, address, latitude, longitude,
    concurrent_match_limit, status, deleted_at
) VALUES
    (2, 1, 'MAT 삭제된 테스트 시설', '테스트 전용 주소', 37.5000000, 127.0000000,
     1, '운영중', '2026-10-21 00:00:00');

INSERT INTO facility_sport (
    facility_id, sport_id, default_min, default_max, default_fee, default_skill
) VALUES
    (2, 1, 1, 2, 0.00, '무관');

INSERT INTO facility_manager (facility_id, user_id, assigned_at) VALUES
    (1, 1, '2026-10-20 09:00:00');

INSERT INTO social_match (
    match_id, facility_id, sport_id, created_by, title, start_at, end_at,
    min_participants, max_participants, team_a_capacity, team_b_capacity,
    fee_per_person, required_skill, status, assignment_method,
    assigned_by, assignment_confirmed_at, rating_status
) VALUES
    (3, 1, 1, 1, 'MAT 배정 테스트 경기', '2026-10-23 10:00:00', '2026-10-23 12:00:00',
     6, 6, 3, 3, 0.00, '무관', '매칭 완료', 'MANUAL', 1, '2026-10-22 18:00:00', 'WAITING'),
    (4, 2, 1, 1, 'MAT 삭제 시설 경기', '2026-10-23 10:00:00', '2026-10-23 12:00:00',
     1, 2, 1, 1, 0.00, '무관', '모집 중', 'MANUAL', NULL, NULL, 'WAITING');

-- 팀 배정 저장·취소 쿼리: 1:1 모집 중 경기와 미배정 확정 참가자 2명
INSERT INTO social_match (
    match_id, facility_id, sport_id, created_by, title, start_at, end_at,
    min_participants, max_participants, team_a_capacity, team_b_capacity,
    fee_per_person, required_skill, status, assignment_method, rating_status
) VALUES
    (5, 1, 1, 1, 'MAT 배정 저장 테스트 경기', '2026-10-24 10:00:00', '2026-10-24 12:00:00',
     2, 2, 1, 1, 0.00, '무관', '모집 중', 'MANUAL', 'WAITING');

INSERT INTO match_application (
    application_id, match_id, applicant_user_id, club_id, application_type,
    status, receipt_no, created_at, confirmed_at, cancelled_at, cancelled_by
) VALUES
    (50, 5, 1, NULL, '개인', '확정', 'MAT-TEST-RECEIPT-50',
     '2026-10-20 09:00:00', '2026-10-20 09:00:00', NULL, NULL),
    (51, 5, 2, NULL, '개인', '확정', 'MAT-TEST-RECEIPT-51',
     '2026-10-20 09:00:00', '2026-10-20 09:00:00', NULL, NULL);

INSERT INTO match_participant (
    participant_id, application_id, match_id, user_id, participation_status,
    match_side, entry_mmr, joined_at, cancelled_at
) VALUES
    (50, 50, 5, 1, '확정', NULL, 1100.00, '2026-10-20 09:00:00', NULL),
    (51, 51, 5, 2, '확정', NULL, 900.00, '2026-10-20 09:00:00', NULL);

-- 레이팅 반영·자동 취소 수신자: 1:1 매칭 완료 경기
-- 회원 3은 레이팅 행이 있고, 회원 4는 없어 1000에서 시작한다. 회원 5는 대기 신청만, 회원 6은 취소 참가자다.
INSERT INTO app_user (
    user_id, email, password_hash, nickname, phone, role, created_at
) VALUES
    (3, 'mat-user-3@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
     'MAT 테스트 회원 3', '010-0000-0003', 'USER', '2026-10-20 09:00:00'),
    (4, 'mat-user-4@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
     'MAT 테스트 회원 4', '010-0000-0004', 'USER', '2026-10-20 09:00:00'),
    (5, 'mat-user-5@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
     'MAT 테스트 회원 5', '010-0000-0005', 'USER', '2026-10-20 09:00:00'),
    (6, 'mat-user-6@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
     'MAT 테스트 회원 6', '010-0000-0006', 'USER', '2026-10-20 09:00:00');

INSERT INTO social_match (
    match_id, facility_id, sport_id, created_by, title, start_at, end_at,
    min_participants, max_participants, team_a_capacity, team_b_capacity,
    fee_per_person, required_skill, status, assignment_method, rating_status
) VALUES
    (6, 1, 1, 1, 'MAT 레이팅 테스트 경기', '2026-10-25 10:00:00', '2026-10-25 12:00:00',
     2, 2, 1, 1, 0.00, '무관', '매칭 완료', 'AUTO', 'WAITING');

INSERT INTO match_application (
    application_id, match_id, applicant_user_id, club_id, application_type,
    status, receipt_no, created_at, confirmed_at, cancelled_at, cancelled_by
) VALUES
    (60, 6, 3, NULL, '개인', '확정', 'MAT-TEST-RECEIPT-60',
     '2026-10-20 09:00:00', '2026-10-20 09:00:00', NULL, NULL),
    (61, 6, 4, NULL, '개인', '확정', 'MAT-TEST-RECEIPT-61',
     '2026-10-20 09:00:00', '2026-10-20 09:00:00', NULL, NULL),
    (62, 6, 5, NULL, '개인', '대기', 'MAT-TEST-RECEIPT-62',
     '2026-10-20 09:00:00', NULL, NULL, NULL),
    (63, 6, 6, NULL, '개인', '취소', 'MAT-TEST-RECEIPT-63',
     '2026-10-20 09:00:00', NULL, '2026-10-21 12:00:00', 6);

INSERT INTO match_participant (
    participant_id, application_id, match_id, user_id, participation_status,
    match_side, entry_mmr, joined_at, cancelled_at
) VALUES
    (60, 60, 6, 3, '확정', 'A', 1100.00, '2026-10-20 09:00:00', NULL),
    (61, 61, 6, 4, '확정', 'B', 900.00, '2026-10-20 09:00:00', NULL),
    (63, 63, 6, 6, '취소', 'A', 1000.00, '2026-10-20 09:00:00', '2026-10-21 12:00:00');

INSERT INTO sport_rating (
    user_id, sport_id, current_mmr, rated_match_count, updated_at
) VALUES
    (3, 1, 1000.00, 7, '2026-10-21 09:30:00');

-- MatchResultMapper: 결과 제출 경기 7과 레이팅에서 이미 제외된 취소 경기 8
-- 경기 7의 팀장(70, 71)은 참가자 FK 때문에 INSERT로 지정할 수 없어 테스트에서 지정한다.
-- 팀장 70은 무효가 된 행과 활성 행이 하나씩 있고, 팀장 71은 제출 행이 없다.
INSERT INTO social_match (
    match_id, facility_id, sport_id, created_by, title, start_at, end_at,
    min_participants, max_participants, team_a_capacity, team_b_capacity,
    fee_per_person, required_skill, status, assignment_method, rating_status, cancelled_at
) VALUES
    (7, 1, 1, 1, 'MAT 결과 제출 테스트 경기', '2026-10-26 10:00:00', '2026-10-26 12:00:00',
     2, 2, 1, 1, 0.00, '무관', '매칭 완료', 'AUTO', 'WAITING', NULL),
    (8, 1, 1, 1, 'MAT 취소 테스트 경기', '2026-10-20 10:00:00', '2026-10-20 12:00:00',
     2, 2, 1, 1, 0.00, '무관', '취소', 'MANUAL', 'EXCLUDED', '2026-10-20 10:00:00');

INSERT INTO match_application (
    application_id, match_id, applicant_user_id, club_id, application_type,
    status, receipt_no, created_at, confirmed_at, cancelled_at, cancelled_by
) VALUES
    (70, 7, 3, NULL, '개인', '확정', 'MAT-TEST-RECEIPT-70',
     '2026-10-20 09:00:00', '2026-10-20 09:00:00', NULL, NULL),
    (71, 7, 4, NULL, '개인', '확정', 'MAT-TEST-RECEIPT-71',
     '2026-10-20 09:00:00', '2026-10-20 09:00:00', NULL, NULL);

INSERT INTO match_participant (
    participant_id, application_id, match_id, user_id, participation_status,
    match_side, entry_mmr, joined_at, cancelled_at
) VALUES
    (70, 70, 7, 3, '확정', 'A', 1000.00, '2026-10-20 09:00:00', NULL),
    (71, 71, 7, 4, '확정', 'B', 1000.00, '2026-10-20 09:00:00', NULL);

INSERT INTO match_result_submission (
    submission_id, match_id, captain_participant_id, reported_result, submission_status, submitted_at
) VALUES
    (700, 7, 70, 'A_WIN', 'REPLACED', '2026-10-26 12:10:00'),
    (701, 7, 70, 'B_WIN', 'SUBMITTED', '2026-10-26 12:20:00');

-- 결과 제출 화면 명단: 경기 7의 취소 참가자(회원 5)는 확정 명단에서 빠진다.
INSERT INTO match_application (
    application_id, match_id, applicant_user_id, club_id, application_type,
    status, receipt_no, created_at, confirmed_at, cancelled_at, cancelled_by
) VALUES
    (72, 7, 5, NULL, '개인', '취소', 'MAT-TEST-RECEIPT-72',
     '2026-10-20 09:00:00', NULL, '2026-10-21 12:00:00', 5);

INSERT INTO match_participant (
    participant_id, application_id, match_id, user_id, participation_status,
    match_side, entry_mmr, joined_at, cancelled_at
) VALUES
    (72, 72, 7, 5, '취소', 'A', 1000.00, '2026-10-20 09:00:00', '2026-10-21 12:00:00');

-- ParticipantHistoryMapper: 회원 3의 신고(처리 완료·기각·접수)와 제재(적용 중 2건, 종료가 지난 적용 중, 해제)
-- 회원 4의 처리 완료 신고와 적용 중 제재로 대상 회원 조건을 확인한다.
INSERT INTO report (
    report_id, reporter_user_id, target_user_id, match_id, reviewed_by, category, summary,
    status, resolution, created_at, reviewed_at
) VALUES
    (1, 1, 3, 7, 1, '경기 매너', 'MAT 테스트 신고 1',
     '처리 완료', '경고', '2026-10-26 13:00:00', '2026-10-26 14:00:00'),
    (2, 1, 3, NULL, 1, '불참', 'MAT 테스트 신고 2',
     '기각', '기각', '2026-10-26 13:00:00', '2026-10-26 14:00:00'),
    (3, 1, 3, NULL, NULL, '허위 정보', 'MAT 테스트 신고 3',
     '접수', NULL, '2026-10-26 13:00:00', NULL),
    (4, 1, 4, 7, 1, '경기 매너', 'MAT 테스트 신고 4',
     '처리 완료', '경고', '2026-10-26 13:00:00', '2026-10-26 14:00:00');

INSERT INTO penalty (
    penalty_id, target_user_id, report_id, issued_by, penalty_type, start_at, end_at,
    status, reason, released_by, released_at
) VALUES
    (1, 3, 1, 1, '경고', '2026-10-20 10:00:00', NULL,
     '적용 중', 'MAT 테스트 제재 1', NULL, NULL),
    (2, 3, NULL, 1, '이용 제한', '2026-10-24 10:00:00', '2026-10-31 10:00:00',
     '적용 중', 'MAT 테스트 제재 2', NULL, NULL),
    (3, 3, NULL, 1, '경고', '2026-10-01 10:00:00', '2026-10-15 10:00:00',
     '적용 중', 'MAT 테스트 제재 3', NULL, NULL),
    (4, 3, NULL, 1, '경고', '2026-10-22 10:00:00', NULL,
     '해제', 'MAT 테스트 제재 4', 1, '2026-10-23 10:00:00'),
    (5, 4, 4, 1, '경고', '2026-10-26 14:00:00', NULL,
     '적용 중', 'MAT 테스트 제재 5', NULL, NULL);
