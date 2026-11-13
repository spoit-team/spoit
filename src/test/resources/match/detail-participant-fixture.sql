-- MatchMapper.findDetailParticipants 신청 시점 반영 경기 수.
-- MatchDetailParticipantMapperIntegrationTest 전용. 다른 fixture와 겹치지 않게 9960번대를 쓴다.
--
-- 대상 매칭방 9961(종목 9961): 참가 확정(joined_at) 2026-09-10 09:00
-- 회원 9961: 이력 행 없이 sport_rating에 반영 12경기만 있다(데모 회원과 같은 모양) -> 12
-- 회원 9962: 반영 7경기. 이력은 확정 전 1건(9960), 확정 뒤 2건(9961 이 경기, 9962) -> 7 - 2 = 5
--            다른 종목(9962)의 확정 뒤 이력 1건은 세지 않는다.
-- 회원 9963: sport_rating 행이 없다 -> 0
-- 회원 9964: 취소한 참가자라 조회에서 빠진다.

INSERT INTO app_user (
    user_id, email, password_hash, nickname, phone, role, created_at
) VALUES
    (9961, 'detail-participant-9961@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
     '상세 참가자 9961', '010-0000-9961', 'USER', '2026-07-01 09:00:00'),
    (9962, 'detail-participant-9962@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
     '상세 참가자 9962', '010-0000-9962', 'USER', '2026-07-01 09:00:00'),
    (9963, 'detail-participant-9963@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
     '상세 참가자 9963', '010-0000-9963', 'USER', '2026-07-01 09:00:00'),
    (9964, 'detail-participant-9964@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
     '상세 참가자 9964', '010-0000-9964', 'USER', '2026-07-01 09:00:00');

INSERT INTO sport (sport_id, name) VALUES
    (9961, '상세 참가자 테스트 종목'),
    (9962, '상세 참가자 테스트 다른 종목');

INSERT INTO region (region_id, parent_region_id, name) VALUES
    (9961, NULL, '상세 참가자 테스트 지역');

INSERT INTO facility (
    facility_id, region_id, name, address, latitude, longitude,
    concurrent_match_limit, status, deleted_at
) VALUES
    (9961, 9961, '상세 참가자 테스트 시설', '테스트 전용 주소', 37.5000000, 127.0000000,
     3, '운영중', NULL);

INSERT INTO facility_sport (
    facility_id, sport_id, default_min, default_max, default_fee, default_skill
) VALUES
    (9961, 9961, 2, 4, 0.00, '무관'),
    (9961, 9962, 2, 4, 0.00, '무관');

INSERT INTO social_match (
    match_id, facility_id, sport_id, created_by, title, start_at, end_at,
    min_participants, max_participants, team_a_capacity, team_b_capacity,
    fee_per_person, required_skill, status, assignment_method, rating_status
) VALUES
    (9960, 9961, 9961, 9962, '확정 전에 반영된 경기', '2026-09-05 10:00:00', '2026-09-05 12:00:00',
     2, 4, 2, 2, 0.00, '무관', '종료', 'MANUAL', 'APPLIED'),
    (9961, 9961, 9961, 9961, '대상 경기', '2026-09-12 10:00:00', '2026-09-12 12:00:00',
     2, 4, 2, 2, 0.00, '무관', '종료', 'MANUAL', 'APPLIED'),
    (9962, 9961, 9961, 9962, '확정 뒤에 반영된 경기', '2026-09-20 10:00:00', '2026-09-20 12:00:00',
     2, 4, 2, 2, 0.00, '무관', '종료', 'MANUAL', 'APPLIED'),
    (9963, 9961, 9962, 9962, '다른 종목 경기', '2026-09-21 10:00:00', '2026-09-21 12:00:00',
     2, 4, 2, 2, 0.00, '무관', '종료', 'MANUAL', 'APPLIED');

INSERT INTO match_application (
    application_id, match_id, applicant_user_id, club_id, application_type,
    status, receipt_no, created_at, confirmed_at, cancelled_at, cancelled_by
) VALUES
    (9961, 9961, 9961, NULL, '개인', '확정', 'DPT-9961', '2026-09-09 09:00:00', '2026-09-10 09:00:00', NULL, NULL),
    (9962, 9961, 9962, NULL, '개인', '확정', 'DPT-9962', '2026-09-09 09:00:00', '2026-09-10 09:00:00', NULL, NULL),
    (9963, 9961, 9963, NULL, '개인', '확정', 'DPT-9963', '2026-09-09 09:00:00', '2026-09-10 09:00:00', NULL, NULL),
    (9964, 9961, 9964, NULL, '개인', '확정', 'DPT-9964', '2026-09-09 09:00:00', '2026-09-10 09:00:00', NULL, NULL),
    (9965, 9960, 9962, NULL, '개인', '확정', 'DPT-9965', '2026-09-01 09:00:00', '2026-09-01 09:00:00', NULL, NULL),
    (9966, 9962, 9962, NULL, '개인', '확정', 'DPT-9966', '2026-09-15 09:00:00', '2026-09-15 09:00:00', NULL, NULL),
    (9967, 9963, 9962, NULL, '개인', '확정', 'DPT-9967', '2026-09-15 09:00:00', '2026-09-15 09:00:00', NULL, NULL);

INSERT INTO match_participant (
    participant_id, application_id, match_id, user_id, participation_status,
    match_side, entry_mmr, joined_at, cancelled_at
) VALUES
    (9961, 9961, 9961, 9961, '확정', 'A', 1150.00, '2026-09-10 09:00:00', NULL),
    (9962, 9962, 9961, 9962, '확정', 'B', 1020.00, '2026-09-10 09:00:00', NULL),
    (9963, 9963, 9961, 9963, '확정', 'B', 1000.00, '2026-09-10 09:00:00', NULL),
    (9964, 9964, 9961, 9964, '취소', NULL, 1000.00, '2026-09-10 09:00:00', '2026-09-11 09:00:00'),
    (9965, 9965, 9960, 9962, '확정', 'A', 1000.00, '2026-09-01 09:00:00', NULL),
    (9966, 9966, 9962, 9962, '확정', 'A', 1030.00, '2026-09-15 09:00:00', NULL),
    (9967, 9967, 9963, 9962, '확정', 'A', 1000.00, '2026-09-15 09:00:00', NULL);

INSERT INTO sport_rating_history (
    history_id, match_id, participant_id, user_id, sport_id, mmr_before, mmr_after,
    elo_delta, evaluation_delta, final_delta, evaluation_score,
    actual_score, expected_score, team_avg_mmr_a, team_avg_mmr_b,
    algorithm_version, calculated_at
) VALUES
    (9965, 9960, 9965, 9962, 9961, 1004.00, 1020.00, 16.00, 0.00, 16.00, NULL, 1.0, 0.5000, 1000.00, 1000.00,
     'TEST', '2026-09-05 14:00:00'),
    (9962, 9961, 9962, 9962, 9961, 1020.00, 1030.00, 10.00, 0.00, 10.00, NULL, 1.0, 0.5000, 1000.00, 1000.00,
     'TEST', '2026-09-12 14:00:00'),
    (9966, 9962, 9966, 9962, 9961, 1030.00, 1046.00, 16.00, 0.00, 16.00, NULL, 1.0, 0.5000, 1000.00, 1000.00,
     'TEST', '2026-09-20 14:00:00'),
    (9967, 9963, 9967, 9962, 9962, 1000.00, 1016.00, 16.00, 0.00, 16.00, NULL, 1.0, 0.5000, 1000.00, 1000.00,
     'TEST', '2026-09-21 14:00:00');

INSERT INTO sport_rating (
    user_id, sport_id, current_mmr, rated_match_count, updated_at
) VALUES
    (9961, 9961, 1150.00, 12, '2026-08-30 12:00:00'),
    (9962, 9961, 1046.00, 7, '2026-09-20 14:00:00'),
    (9962, 9962, 1016.00, 1, '2026-09-21 14:00:00');
