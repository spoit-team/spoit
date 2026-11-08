-- MyRatingRecordMapper 마이페이지 레이팅·경기 이력 조회.
-- MyRatingRecordMapperIntegrationTest 전용. 다른 fixture와 겹치지 않게 911번대부터 쓴다.
-- 조회가 현재 시각을 쓰지 않아 날짜는 고정값이다.
--
-- 회원 911(본인): 종목 A 반영 22경기, 종목 B 반영 1경기(동호회 신청 팀원), 종목 A 제외 경기 1개(이력 없음)
-- 회원 912(다른 회원): 911과 같은 종목 A 22경기에 이력이 있다.
-- 회원 913: 종목 B 동호회 신청의 대표. 911은 본인 신청 행 없이 이 신청의 참가자로만 있다.

INSERT INTO app_user (
    user_id, email, password_hash, nickname, phone, role, created_at
) VALUES
    (911, 'rating-record-911@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
     '레이팅 이력 테스트 회원 911', '010-0000-0911', 'USER', '2026-07-01 09:00:00'),
    (912, 'rating-record-912@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
     '레이팅 이력 테스트 회원 912', '010-0000-0912', 'USER', '2026-07-01 09:00:00'),
    (913, 'rating-record-913@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
     '레이팅 이력 테스트 회원 913', '010-0000-0913', 'USER', '2026-07-01 09:00:00');

INSERT INTO sport (sport_id, name) VALUES
    (911, '레이팅 이력 테스트 종목 A'),
    (912, '레이팅 이력 테스트 종목 B'),
    (913, '레이팅 이력 테스트 종목 C');

INSERT INTO region (region_id, parent_region_id, name) VALUES
    (911, NULL, '레이팅 이력 테스트 지역');

INSERT INTO facility (
    facility_id, region_id, name, address, latitude, longitude,
    concurrent_match_limit, status, deleted_at
) VALUES
    (911, 911, '레이팅 이력 테스트 시설', '테스트 전용 주소', 37.5000000, 127.0000000,
     1, '운영중', NULL);

INSERT INTO facility_sport (
    facility_id, sport_id, default_min, default_max, default_fee, default_skill
) VALUES
    (911, 911, 2, 2, 0.00, '무관'),
    (911, 912, 2, 2, 0.00, '무관');

-- 종목 A 반영 경기 n = 1..22: match_id 9100 + n, 경기일 2026-08-01 + n일
INSERT INTO social_match (
    match_id, facility_id, sport_id, created_by, title, start_at, end_at,
    min_participants, max_participants, team_a_capacity, team_b_capacity,
    fee_per_person, required_skill, status, assignment_method, rating_status
)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 22)
SELECT 9100 + n, 911, 911, 911, CONCAT('종목 A 반영 경기 ', n),
       TIMESTAMP('2026-08-01 10:00:00') + INTERVAL n DAY, TIMESTAMP('2026-08-01 12:00:00') + INTERVAL n DAY,
       2, 2, 1, 1, 0.00, '무관', '종료', 'MANUAL', 'APPLIED'
FROM seq;

INSERT INTO social_match (
    match_id, facility_id, sport_id, created_by, title, start_at, end_at,
    min_participants, max_participants, team_a_capacity, team_b_capacity,
    fee_per_person, required_skill, status, assignment_method, rating_status
) VALUES
    (9131, 911, 912, 913, '종목 B 동호회 경기', '2026-08-09 18:00:00', '2026-08-09 20:00:00',
     2, 2, 1, 1, 0.00, '무관', '종료', 'MANUAL', 'APPLIED'),
    (9141, 911, 911, 911, '종목 A 제외 경기', '2026-08-30 10:00:00', '2026-08-30 12:00:00',
     2, 2, 1, 1, 0.00, '무관', '종료', 'MANUAL', 'EXCLUDED');

INSERT INTO match_application (
    application_id, match_id, applicant_user_id, club_id, application_type,
    status, receipt_no, created_at, confirmed_at, cancelled_at, cancelled_by
)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 22)
SELECT 9800 + n, 9100 + n, 911, NULL, '개인', '확정', CONCAT('MRR-911-', n),
       '2026-07-20 09:00:00', '2026-07-20 09:00:00', NULL, NULL
FROM seq
UNION ALL
SELECT 9900 + n, 9100 + n, 912, NULL, '개인', '확정', CONCAT('MRR-912-', n),
       '2026-07-20 09:00:00', '2026-07-20 09:00:00', NULL, NULL
FROM seq;

INSERT INTO match_application (
    application_id, match_id, applicant_user_id, club_id, application_type,
    status, receipt_no, created_at, confirmed_at, cancelled_at, cancelled_by
) VALUES
    (9831, 9131, 913, NULL, '동호회', '확정', 'MRR-913-CLUB',
     '2026-07-20 09:00:00', '2026-07-20 09:00:00', NULL, NULL),
    (9841, 9141, 911, NULL, '개인', '확정', 'MRR-911-EXCLUDED',
     '2026-07-20 09:00:00', '2026-07-20 09:00:00', NULL, NULL);

INSERT INTO match_participant (
    participant_id, application_id, match_id, user_id, participation_status,
    match_side, entry_mmr, joined_at, cancelled_at
)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 22)
SELECT 9600 + n, 9800 + n, 9100 + n, 911, '확정', 'A', 1000.00, '2026-07-20 09:00:00', NULL
FROM seq
UNION ALL
SELECT 9700 + n, 9900 + n, 9100 + n, 912, '확정', 'B', 1000.00, '2026-07-20 09:00:00', NULL
FROM seq;

-- 911은 913의 동호회 신청(9831)에 참가자로만 들어 있다.
INSERT INTO match_participant (
    participant_id, application_id, match_id, user_id, participation_status,
    match_side, entry_mmr, joined_at, cancelled_at
) VALUES
    (9631, 9831, 9131, 911, '확정', 'B', 1032.40, '2026-07-20 09:00:00', NULL),
    (9632, 9831, 9131, 913, '확정', 'B', 1000.00, '2026-07-20 09:00:00', NULL),
    (9641, 9841, 9141, 911, '확정', 'A', 1000.00, '2026-07-20 09:00:00', NULL);

-- history_id를 반영 시각과 반대 순서로 매겨, ID 순이 아니라 반영 시각 순으로 정렬하는지 본다.
-- 911 종목 A: history_id 9300 - n, 반영 2026-08-02 12:00 + n일
-- 912 종목 A: history_id 9400 + n, 같은 반영 시각
INSERT INTO sport_rating_history (
    history_id, match_id, participant_id, user_id, sport_id, mmr_before, mmr_after,
    elo_delta, evaluation_delta, final_delta, evaluation_score,
    actual_score, expected_score, team_avg_mmr_a, team_avg_mmr_b,
    algorithm_version, calculated_at
)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 22)
SELECT 9300 - n, 9100 + n, 9600 + n, 911, 911, 1000.00, 1016.50,
       16.00, 0.50, 16.50, 7.00, 1.0, 0.5000, 1000.00, 1000.00,
       'TEST', TIMESTAMP('2026-08-02 12:00:00') + INTERVAL n DAY
FROM seq
UNION ALL
SELECT 9400 + n, 9100 + n, 9700 + n, 912, 911, 1000.00, 984.00,
       -16.00, 0.00, -16.00, NULL, 0.0, 0.5000, 1000.00, 1000.00,
       'TEST', TIMESTAMP('2026-08-02 12:00:00') + INTERVAL n DAY
FROM seq;

-- 종목 B 한 경기는 종목 A n=8(08-10 12:00)과 n=9(08-11 12:00) 사이에 반영됐다.
INSERT INTO sport_rating_history (
    history_id, match_id, participant_id, user_id, sport_id, mmr_before, mmr_after,
    elo_delta, evaluation_delta, final_delta, evaluation_score,
    actual_score, expected_score, team_avg_mmr_a, team_avg_mmr_b,
    algorithm_version, calculated_at
) VALUES
    (9500, 9131, 9631, 911, 912, 1032.40, 1016.40,
     -16.00, 0.00, -16.00, NULL, 0.0, 0.5000, 1010.25, 990.75,
     'TEST', '2026-08-10 18:00:00');

-- 911의 종목 C는 반영 없이 만든 0경기 행이라 내 레이팅 목록에서 빠진다.
INSERT INTO sport_rating (
    user_id, sport_id, current_mmr, rated_match_count, updated_at
) VALUES
    (911, 911, 1363.00, 22, '2026-08-24 12:00:00'),
    (911, 912, 1016.40, 1, '2026-08-10 18:00:00'),
    (911, 913, 1000.00, 0, '2026-07-01 09:00:00'),
    (912, 911, 648.00, 22, '2026-08-24 12:00:00');
