-- =====================================================================
-- 회원 탈퇴 수동 테스트용 더미 시드 (격리된 fresh MySQL 전용)
--
-- 실행 계약
--   * 대상 스키마는 db/schema.sql 로 방금 만든 빈 스키마여야 한다.
--   * 실행자는 이 파일을 적용하기 전에 같은 세션에서 아래 변수를 반드시 설정한다.
--         SET @withdrawal_password_hash = '<BCrypt 해시>';
--     해시 값은 저장소·로그·문서에 남기지 않는다. 이 파일은 값을 하드코딩하지 않는다.
--   * INSERT 전용이다. 삭제·변경·DDL 문은 쓰지 않는다.
--   * 날짜는 모두 NOW() 기준 상대값이라 실행 시점과 무관하게 시나리오가 유지된다.
--   * 이메일은 example.invalid, 닉네임·연락처는 실제 인물과 무관한 더미 값이다.
--
-- 시나리오 계정과 기대 결과
--   withdraw-free@example.invalid                    차단 없음        -> 탈퇴 성공
--   withdraw-leader@example.invalid                  동호회장          -> 탈퇴 차단(leaderClubNames)
--   withdraw-team@example.invalid                    모집 중 팀 매치 신청 -> 탈퇴 차단(teamMatchClubNames)
--   withdraw-individual-recruiting@example.invalid   모집 중 개인 매치 신청 -> 탈퇴 차단(individualMatchNames)
--   withdraw-individual-matched@example.invalid      매칭 완료 개인 매치 신청 -> 탈퇴 차단(individualMatchNames)
--   withdraw-cancelled@example.invalid               취소 신청 + 취소 매치 -> 차단 없음(탈퇴 성공)
--   withdraw-ended@example.invalid                   종료 경기 확정 참가 -> 차단 없음(탈퇴 성공)
--
-- 지원 계정(시나리오 검증용, 로그인 대상 아님)
--   withdraw-club-leader@example.invalid  팀매치 동호회 회장(모집 중 팀 매치 묶음 대표)
--   withdraw-club-member@example.invalid  팀매치 동호회 회원(팀 신청 동반자)
--   withdraw-opponent@example.invalid     종료 경기 상대 참가자
--
-- 지원 계정도 탈퇴 차단 규칙이 그대로 적용된다(검증 시 참고).
--   withdraw-club-leader@  동호회장 + 모집 중 팀 매치 대표 -> 두 사유 모두 차단
--   withdraw-club-member@  모집 중 팀 매치 동반 참가자       -> teamMatchClubNames 차단
--   withdraw-opponent@     종료 경기 확정 참가자            -> 차단 없음
-- =====================================================================

-- 종목 -----------------------------------------------------------------
INSERT INTO sport (sport_id, name) VALUES
    (9201, '탈퇴검증 테니스'),
    (9202, '탈퇴검증 풋살');

-- 지역(시/도 + 자치구): 프로필 선호 지역 선택지와 시설 소재지로 쓴다. ---------
INSERT INTO region (region_id, parent_region_id, name) VALUES
    (9101, NULL, '탈퇴검증 광역시'),
    (9102, 9101, '탈퇴검증 중구'),
    (9103, 9101, '탈퇴검증 남구');

-- 회원 -----------------------------------------------------------------
INSERT INTO app_user (user_id, email, password_hash, nickname, phone, role, created_at) VALUES
    (9301, 'withdraw-free@example.invalid',                   @withdrawal_password_hash, '자유탈퇴', '010-9000-0001', 'MEMBER', DATE_SUB(NOW(), INTERVAL 120 DAY)),
    (9302, 'withdraw-leader@example.invalid',                 @withdrawal_password_hash, '동호회장탈퇴', '010-9000-0002', 'MEMBER', DATE_SUB(NOW(), INTERVAL 110 DAY)),
    (9303, 'withdraw-team@example.invalid',                   @withdrawal_password_hash, '팀원탈퇴', '010-9000-0003', 'MEMBER', DATE_SUB(NOW(), INTERVAL 100 DAY)),
    (9304, 'withdraw-individual-recruiting@example.invalid',  @withdrawal_password_hash, '개인모집탈퇴', '010-9000-0004', 'MEMBER', DATE_SUB(NOW(), INTERVAL 90 DAY)),
    (9305, 'withdraw-individual-matched@example.invalid',     @withdrawal_password_hash, '개인완료탈퇴', '010-9000-0005', 'MEMBER', DATE_SUB(NOW(), INTERVAL 80 DAY)),
    (9306, 'withdraw-cancelled@example.invalid',              @withdrawal_password_hash, '취소탈퇴', '010-9000-0006', 'MEMBER', DATE_SUB(NOW(), INTERVAL 70 DAY)),
    (9307, 'withdraw-ended@example.invalid',                  @withdrawal_password_hash, '종료탈퇴', '010-9000-0007', 'MEMBER', DATE_SUB(NOW(), INTERVAL 60 DAY)),
    (9308, 'withdraw-club-leader@example.invalid',            @withdrawal_password_hash, '지원팀장', '010-9000-0008', 'MEMBER', DATE_SUB(NOW(), INTERVAL 150 DAY)),
    (9309, 'withdraw-club-member@example.invalid',            @withdrawal_password_hash, '지원팀원', '010-9000-0009', 'MEMBER', DATE_SUB(NOW(), INTERVAL 140 DAY)),
    (9310, 'withdraw-opponent@example.invalid',               @withdrawal_password_hash, '지원상대', '010-9000-0010', 'MEMBER', DATE_SUB(NOW(), INTERVAL 130 DAY));

-- 선호 종목(회원당 1행) ------------------------------------------------
INSERT INTO user_preferred_sport (user_id, sport_id) VALUES
    (9301, 9201),
    (9302, 9201),
    (9303, 9201),
    (9304, 9201),
    (9305, 9201),
    (9306, 9201),
    (9307, 9201),
    (9308, 9201),
    (9309, 9201),
    (9310, 9202);

-- 선호 지역 -------------------------------------------------------------
INSERT INTO user_region (user_id, region_id) VALUES
    (9301, 9102),
    (9302, 9102),
    (9303, 9103),
    (9304, 9102),
    (9305, 9103),
    (9306, 9102),
    (9307, 9102),
    (9308, 9102),
    (9309, 9103),
    (9310, 9103);

-- 시설 -----------------------------------------------------------------
INSERT INTO facility (facility_id, region_id, name, address, latitude, longitude,
                      concurrent_match_limit, status, deleted_at) VALUES
    (9401, 9102, '탈퇴검증 테니스 센터', '탈퇴검증 광역시 중구 검증로 1',
     37.5500000, 126.9200000, 3, '운영 중', NULL);

INSERT INTO facility_sport (facility_id, sport_id, default_min, default_max,
                            default_fee, default_skill, note) VALUES
    (9401, 9201, 2, 8, 15000.00, '초급', '탈퇴 수동 테스트용 시설 안내입니다.');

-- 요일별 운영 시간(월~일 모두 운영) ---------------------------------------
INSERT INTO operating_rule (facility_id, weekday, open_time, close_time, end_next_day, is_open) VALUES
    (9401, 1, '06:00:00', '23:00:00', FALSE, TRUE),
    (9401, 2, '06:00:00', '23:00:00', FALSE, TRUE),
    (9401, 3, '06:00:00', '23:00:00', FALSE, TRUE),
    (9401, 4, '06:00:00', '23:00:00', FALSE, TRUE),
    (9401, 5, '06:00:00', '23:00:00', FALSE, TRUE),
    (9401, 6, '08:00:00', '22:00:00', FALSE, TRUE),
    (9401, 7, '08:00:00', '22:00:00', FALSE, TRUE);

-- 동호회 ---------------------------------------------------------------
-- 9501: withdraw-leader 가 동호회장 -> leaderClubNames 차단
-- 9502: withdraw-team 이 동호회원 -> 모집 중 팀 매치 신청으로 teamMatchClubNames 차단
--       (withdraw-team 은 동호회장이 아니다)
INSERT INTO club (club_id, sport_id, club_name, intro, max_members, recruit_status,
                  image_url, activity_days, activity_start_time, activity_end_time,
                  deleted_at, created_at) VALUES
    (9501, 9201, '탈퇴검증 팀장 클럽', '동호회장 탈퇴 차단을 확인하는 동호회입니다.',
     10, '모집중', NULL, '2,4', '19:00:00', '21:00:00', NULL, DATE_SUB(NOW(), INTERVAL 90 DAY)),
    (9502, 9201, '탈퇴검증 팀매치 클럽', '모집 중 팀 매치 신청 차단을 확인하는 동호회입니다.',
     10, '모집중', NULL, '1,3', '20:00:00', '22:00:00', NULL, DATE_SUB(NOW(), INTERVAL 60 DAY));

INSERT INTO club_region (club_id, region_id) VALUES
    (9501, 9102),
    (9502, 9102),
    (9502, 9103);

INSERT INTO club_member (club_member_id, club_id, user_id, role, joined_at, left_at, leave_type, kick_reason) VALUES
    (9551, 9501, 9302, '동호회장', DATE_SUB(NOW(), INTERVAL 90 DAY), NULL, NULL, NULL),
    (9552, 9501, 9310, '동호회원', DATE_SUB(NOW(), INTERVAL 85 DAY), NULL, NULL, NULL),
    (9553, 9502, 9308, '동호회장', DATE_SUB(NOW(), INTERVAL 60 DAY), NULL, NULL, NULL),
    (9554, 9502, 9303, '동호회원', DATE_SUB(NOW(), INTERVAL 55 DAY), NULL, NULL, NULL),
    (9555, 9502, 9309, '동호회원', DATE_SUB(NOW(), INTERVAL 50 DAY), NULL, NULL, NULL);

-- 매칭방 ---------------------------------------------------------------
-- 9601 모집 중 / 9602 매칭 완료(경기 전) / 9603 종료 / 9604 취소 / 9605 모집 중(팀)
INSERT INTO social_match (match_id, facility_id, sport_id, created_by, title, start_at, end_at,
                          min_participants, max_participants, team_a_capacity, team_b_capacity,
                          fee_per_person, required_skill, status, assignment_method,
                          assigned_by, assignment_confirmed_at, rating_status, rating_processed_at,
                          cancelled_at) VALUES
    (9601, 9401, 9201, 9308, '탈퇴검증 개인 모집 매치',
     DATE_ADD(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 3 DAY) + INTERVAL 2 HOUR,
     2, 8, 4, 4, 15000.00, '초급', '모집 중', 'MANUAL',
     NULL, NULL, 'WAITING', NULL, NULL),
    (9602, 9401, 9201, 9308, '탈퇴검증 개인 매칭 완료 매치',
     DATE_ADD(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 1 DAY) + INTERVAL 2 HOUR,
     2, 8, 4, 4, 15000.00, '중급', '매칭 완료', 'MANUAL',
     9308, DATE_SUB(NOW(), INTERVAL 6 HOUR), 'WAITING', NULL, NULL),
    (9603, 9401, 9201, 9308, '탈퇴검증 개인 종료 매치',
     DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY) + INTERVAL 2 HOUR,
     2, 8, 4, 4, 15000.00, '초급', '종료', 'MANUAL',
     9308, DATE_SUB(NOW(), INTERVAL 3 DAY), 'APPLIED', DATE_SUB(NOW(), INTERVAL 2 DAY), NULL),
    (9604, 9401, 9201, 9308, '탈퇴검증 취소 매치',
     DATE_ADD(NOW(), INTERVAL 5 DAY), DATE_ADD(NOW(), INTERVAL 5 DAY) + INTERVAL 2 HOUR,
     2, 8, 4, 4, 15000.00, '초급', '취소', 'MANUAL',
     NULL, NULL, 'EXCLUDED', NULL, DATE_SUB(NOW(), INTERVAL 1 DAY)),
    (9605, 9401, 9201, 9308, '탈퇴검증 팀 모집 매치',
     DATE_ADD(NOW(), INTERVAL 4 DAY), DATE_ADD(NOW(), INTERVAL 4 DAY) + INTERVAL 2 HOUR,
     4, 8, 4, 4, 15000.00, '중급', '모집 중', 'MANUAL',
     NULL, NULL, 'WAITING', NULL, NULL);

-- 신청 -----------------------------------------------------------------
-- 9701 모집 중 개인 대기(차단) / 9702 매칭 완료 개인 확정(차단) / 9703 종료 경기 확정(비차단)
-- 9704 취소된 매치의 개인 대기(비차단) / 9705 모집 중 매치의 취소된 개인 신청(비차단)
-- 9706~9707 모집 중 팀 매치 동호회 묶음 신청(팀원 차단)
INSERT INTO match_application (application_id, match_id, applicant_user_id, club_id,
                               application_type, status, receipt_no, created_at,
                               confirmed_at, cancelled_at, cancelled_by, group_application_id) VALUES
    (9701, 9601, 9304, NULL, '개인', '대기', 'WD-IND-RECRUITING', DATE_SUB(NOW(), INTERVAL 2 HOUR),
     NULL, NULL, NULL, NULL),
    (9702, 9602, 9305, NULL, '개인', '확정', 'WD-IND-MATCHED', DATE_SUB(NOW(), INTERVAL 1 DAY),
     DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL, NULL),
    (9703, 9603, 9307, NULL, '개인', '확정', 'WD-ENDED', DATE_SUB(NOW(), INTERVAL 5 DAY),
     DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, NULL, NULL),
    (9704, 9604, 9306, NULL, '개인', '대기', 'WD-CANCELLED-MATCH', DATE_SUB(NOW(), INTERVAL 6 DAY),
     NULL, NULL, NULL, NULL),
    (9705, 9601, 9306, NULL, '개인', '취소', 'WD-CANCELLED-APP', DATE_SUB(NOW(), INTERVAL 3 DAY),
     NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), 9306, NULL),
    (9706, 9605, 9303, 9502, '동호회', '대기', 'WD-TEAM-LEADER', DATE_SUB(NOW(), INTERVAL 1 DAY),
     NULL, NULL, NULL, 9706),
    (9707, 9605, 9309, 9502, '동호회', '대기', 'WD-TEAM-MEMBER', DATE_SUB(NOW(), INTERVAL 1 DAY),
     NULL, NULL, NULL, 9706),
    (9708, 9603, 9310, NULL, '개인', '확정', 'WD-ENDED-2', DATE_SUB(NOW(), INTERVAL 5 DAY),
     DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, NULL, NULL);

-- 참가자 ---------------------------------------------------------------
INSERT INTO match_participant (participant_id, application_id, match_id, user_id,
                               participation_status, match_side, entry_mmr, joined_at, cancelled_at) VALUES
    (9801, 9702, 9602, 9305, '확정', 'A', 1180.00, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL),
    (9802, 9703, 9603, 9307, '확정', 'B', 1020.00, DATE_SUB(NOW(), INTERVAL 5 DAY), NULL),
    -- 취소된 개인 신청의 참가자 행: active_match_user 가 NULL 이 되어 정원·중복 판정에서 빠진다.
    (9803, 9705, 9601, 9306, '취소', NULL, 1000.00, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
    (9804, 9708, 9603, 9310, '확정', 'A', 1010.00, DATE_SUB(NOW(), INTERVAL 5 DAY), NULL);

-- 종목별 레이팅(프로필 표시값) -------------------------------------------
INSERT INTO sport_rating (user_id, sport_id, current_mmr, rated_match_count, updated_at) VALUES
    (9301, 9201, 1042.50, 3, DATE_SUB(NOW(), INTERVAL 10 DAY)),
    (9302, 9201, 1000.00, 0, DATE_SUB(NOW(), INTERVAL 90 DAY)),
    (9303, 9201, 1000.00, 0, DATE_SUB(NOW(), INTERVAL 55 DAY)),
    (9304, 9201, 1000.00, 0, DATE_SUB(NOW(), INTERVAL 20 DAY)),
    (9305, 9201, 1180.00, 2, DATE_SUB(NOW(), INTERVAL 1 DAY)),
    (9306, 9201, 1000.00, 0, DATE_SUB(NOW(), INTERVAL 30 DAY)),
    (9307, 9201, 1020.00, 1, DATE_SUB(NOW(), INTERVAL 2 DAY)),
    (9308, 9201, 1000.00, 0, DATE_SUB(NOW(), INTERVAL 60 DAY)),
    (9309, 9201, 1000.00, 0, DATE_SUB(NOW(), INTERVAL 50 DAY)),
    (9310, 9201, 1010.00, 1, DATE_SUB(NOW(), INTERVAL 5 DAY));
