-- 회원 탈퇴 시나리오 추가 데이터 (MySQL 8.0.16+, utf8mb4)
-- 대상: 제공된 schema.sql과 seed.sql이 이미 적용된 테스트 DB.
-- 이 파일은 schema.sql이나 seed.sql을 실행하지 않는다.
-- 같은 테스트 계정과 이 파일의 관련 데이터를 삭제한 후 새로 생성한다.
-- mysql 클라이언트 또는 DELIMITER를 지원하는 도구에서 파일 전체를 실행한다.
-- CREATE ROUTINE 및 데이터 SELECT, INSERT, UPDATE, DELETE 권한이 필요하다.
-- 기존 트랜잭션을 종료한 후 실행한다. 계정과 연관된 다른 데이터가 있으면 오류 및 전체 롤백된다.
-- 오류로 중단되면 남아 있을 수 있는 spoit_wd_fixture_20261104 프로시저를 확인 후 지운다.
-- 다른 쓰기와 동시에 실행하면 ID 경합으로 롤백될 수 있다. 외래키 검사는 켜 둔다.
-- 모두 공통 테스트 전용 비밀번호: WithdrawTest123! (실서비스 사용 금지).
-- 계정 (모두 @example.invalid):
-- withdraw-free: 탈퇴 성공 / withdraw-leader: 동호회 가입 차단
-- withdraw-team: 동호회 가입 + 매치 신청 차단 / withdraw-individual-recruiting: 개인 모집 차단
-- withdraw-individual-matched: 개인 매칭 완료 차단 / withdraw-cancelled: 탈퇴 성공
-- withdraw-ended: 탈퇴 성공 / withdraw-club-leader: 동호회 가입 + 매치 신청 차단
-- withdraw-club-member: 동호회 가입 + 매치 신청 차단 / withdraw-opponent: 동호회 가입 차단
-- 매치 일정은 실행 시각 기준이다. 시작 시각 경과 후 스케줄러가 상태를 변경할 수 있다.

SET NAMES utf8mb4;
DELIMITER $$
CREATE PROCEDURE spoit_wd_fixture_20261104()
BEGIN
    DECLARE v_region, v_user, v_facility, v_club, v_member, v_match, v_application, v_participant BIGINT;
    DECLARE v_tennis, v_football BIGINT;
    DECLARE v_old_facility, v_old_club1, v_old_club2 BIGINT DEFAULT NULL;
    DECLARE v_old_region_parent, v_old_region_main, v_old_region_south BIGINT DEFAULT NULL;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    START TRANSACTION;
    -- 기존 더미의 시설과 동호회는 더미 계정과의 관계까지 확인해 식별한다.
    SELECT MAX(f.facility_id), MAX(f.region_id) INTO v_old_facility, v_old_region_main
      FROM facility f
      JOIN social_match sm ON sm.facility_id = f.facility_id
      JOIN app_user u ON u.user_id = sm.created_by
     WHERE f.name = '탈퇴검증 테니스 센터'
       AND sm.title = '탈퇴검증 개인 모집 매치'
       AND u.email = 'withdraw-club-leader@example.invalid';
    SELECT MAX(parent_region_id) INTO v_old_region_parent FROM region WHERE region_id = v_old_region_main;
    SELECT MAX(c.club_id) INTO v_old_club1 FROM club c
      JOIN club_member cm ON cm.club_id = c.club_id JOIN app_user u ON u.user_id = cm.user_id
     WHERE c.club_name = '탈퇴검증 팀장 클럽' AND u.email = 'withdraw-leader@example.invalid';
    SELECT MAX(c.club_id) INTO v_old_club2 FROM club c
      JOIN club_member cm ON cm.club_id = c.club_id JOIN app_user u ON u.user_id = cm.user_id
     WHERE c.club_name = '탈퇴검증 팀매치 클럽' AND u.email = 'withdraw-club-leader@example.invalid';
    SELECT MAX(r.region_id) INTO v_old_region_south FROM region r
      JOIN club_region cr ON cr.region_id = r.region_id
     WHERE cr.club_id = v_old_club2 AND r.parent_region_id = v_old_region_parent AND r.name = '탈퇴검증 남구';

    -- 다른 회원이 더미 매치나 동호회에 추가된 경우 자동 삭제하지 않는다.
    IF EXISTS (SELECT 1 FROM social_match sm JOIN app_user u ON u.user_id = sm.created_by
                WHERE sm.facility_id = v_old_facility AND (u.email <> 'withdraw-club-leader@example.invalid'
                 OR sm.title NOT IN ('탈퇴검증 개인 모집 매치','탈퇴검증 개인 매칭 완료 매치',
                   '탈퇴검증 개인 종료 매치','탈퇴검증 취소 매치','탈퇴검증 팀 모집 매치')))
       OR EXISTS (SELECT 1 FROM match_application ma JOIN social_match sm ON sm.match_id = ma.match_id
                 JOIN app_user u ON u.user_id = ma.applicant_user_id
                WHERE sm.facility_id = v_old_facility
                  AND u.email NOT IN ('withdraw-free@example.invalid','withdraw-leader@example.invalid','withdraw-team@example.invalid',
                    'withdraw-individual-recruiting@example.invalid','withdraw-individual-matched@example.invalid',
                    'withdraw-cancelled@example.invalid','withdraw-ended@example.invalid','withdraw-club-leader@example.invalid',
                    'withdraw-club-member@example.invalid','withdraw-opponent@example.invalid'))
       OR EXISTS (SELECT 1 FROM match_participant mp JOIN social_match sm ON sm.match_id = mp.match_id
                   JOIN app_user u ON u.user_id = mp.user_id WHERE sm.facility_id = v_old_facility
                    AND u.email NOT IN ('withdraw-free@example.invalid','withdraw-leader@example.invalid','withdraw-team@example.invalid',
                      'withdraw-individual-recruiting@example.invalid','withdraw-individual-matched@example.invalid',
                      'withdraw-cancelled@example.invalid','withdraw-ended@example.invalid','withdraw-club-leader@example.invalid',
                      'withdraw-club-member@example.invalid','withdraw-opponent@example.invalid'))
       OR EXISTS (SELECT 1 FROM club_member cm JOIN app_user u ON u.user_id = cm.user_id
                   WHERE cm.club_id IN (v_old_club1, v_old_club2)
                    AND u.email NOT IN ('withdraw-free@example.invalid','withdraw-leader@example.invalid','withdraw-team@example.invalid',
                      'withdraw-individual-recruiting@example.invalid','withdraw-individual-matched@example.invalid',
                      'withdraw-cancelled@example.invalid','withdraw-ended@example.invalid','withdraw-club-leader@example.invalid',
                      'withdraw-club-member@example.invalid','withdraw-opponent@example.invalid')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Fixture has non-fixture members or participants; reset aborted';
    END IF;

    -- FK 순서에 따라 이전 시나리오 데이터를 제거한다. 외부 참조가 있으면 롤백된다.
    DELETE mp FROM match_participant mp JOIN social_match sm ON sm.match_id = mp.match_id
     WHERE sm.facility_id = v_old_facility;
    UPDATE match_application ma JOIN social_match sm ON sm.match_id = ma.match_id
       SET ma.group_application_id = NULL WHERE sm.facility_id = v_old_facility;
    DELETE ma FROM match_application ma JOIN social_match sm ON sm.match_id = ma.match_id
     WHERE sm.facility_id = v_old_facility;
    DELETE FROM social_match WHERE facility_id = v_old_facility;
    DELETE FROM club_member WHERE club_id IN (v_old_club1, v_old_club2);
    DELETE FROM club_region WHERE club_id IN (v_old_club1, v_old_club2);
    DELETE FROM club WHERE club_id IN (v_old_club1, v_old_club2);
    DELETE FROM operating_rule WHERE facility_id = v_old_facility;
    DELETE FROM facility_sport WHERE facility_id = v_old_facility;
    DELETE FROM facility WHERE facility_id = v_old_facility;
    DELETE ups FROM user_preferred_sport ups JOIN app_user u ON u.user_id = ups.user_id
     WHERE u.email IN ('withdraw-free@example.invalid','withdraw-leader@example.invalid','withdraw-team@example.invalid',
       'withdraw-individual-recruiting@example.invalid','withdraw-individual-matched@example.invalid',
       'withdraw-cancelled@example.invalid','withdraw-ended@example.invalid','withdraw-club-leader@example.invalid',
       'withdraw-club-member@example.invalid','withdraw-opponent@example.invalid');
    DELETE ur FROM user_region ur JOIN app_user u ON u.user_id = ur.user_id
     WHERE u.email IN ('withdraw-free@example.invalid','withdraw-leader@example.invalid','withdraw-team@example.invalid',
       'withdraw-individual-recruiting@example.invalid','withdraw-individual-matched@example.invalid',
       'withdraw-cancelled@example.invalid','withdraw-ended@example.invalid','withdraw-club-leader@example.invalid',
       'withdraw-club-member@example.invalid','withdraw-opponent@example.invalid');
    DELETE sr FROM sport_rating sr JOIN app_user u ON u.user_id = sr.user_id
     WHERE u.email IN ('withdraw-free@example.invalid','withdraw-leader@example.invalid','withdraw-team@example.invalid',
       'withdraw-individual-recruiting@example.invalid','withdraw-individual-matched@example.invalid',
       'withdraw-cancelled@example.invalid','withdraw-ended@example.invalid','withdraw-club-leader@example.invalid',
       'withdraw-club-member@example.invalid','withdraw-opponent@example.invalid');
    DELETE FROM app_user WHERE email IN ('withdraw-free@example.invalid','withdraw-leader@example.invalid','withdraw-team@example.invalid',
       'withdraw-individual-recruiting@example.invalid','withdraw-individual-matched@example.invalid',
       'withdraw-cancelled@example.invalid','withdraw-ended@example.invalid','withdraw-club-leader@example.invalid',
       'withdraw-club-member@example.invalid','withdraw-opponent@example.invalid');
    DELETE FROM region WHERE region_id IN (v_old_region_main, v_old_region_south);
    DELETE FROM region WHERE region_id = v_old_region_parent;

    SELECT MAX(CASE WHEN name = '테니스' THEN sport_id END), MAX(CASE WHEN name = '축구+풋살' THEN sport_id END)
      INTO v_tennis, v_football FROM sport;
    IF v_tennis IS NULL OR v_football IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'seed.sql sports are missing';
    END IF;
    SELECT COALESCE(MAX(region_id),0) INTO v_region FROM region;
    SELECT COALESCE(MAX(user_id),0) INTO v_user FROM app_user;
    SELECT COALESCE(MAX(facility_id),0) INTO v_facility FROM facility;
    SELECT COALESCE(MAX(club_id),0) INTO v_club FROM club;
    SELECT COALESCE(MAX(club_member_id),0) INTO v_member FROM club_member;
    SELECT COALESCE(MAX(match_id),0) INTO v_match FROM social_match;
    SELECT COALESCE(MAX(application_id),0) INTO v_application FROM match_application;
    SELECT COALESCE(MAX(participant_id),0) INTO v_participant FROM match_participant;

-- 지역(시/도 + 자치구): 프로필 선호 지역 선택지와 시설 소재지로 쓴다. ---------
INSERT INTO region (region_id, parent_region_id, name) VALUES
    (v_region + 1, NULL, '탈퇴검증 광역시'),
    (v_region + 2, v_region + 1, '탈퇴검증 중구'),
    (v_region + 3, v_region + 1, '탈퇴검증 남구');

-- 회원 -----------------------------------------------------------------
INSERT INTO app_user (user_id, email, password_hash, nickname, phone, role, created_at) VALUES
    (v_user + 1, 'withdraw-free@example.invalid',                   '$2y$10$UFubgfNbK8aoN/7hmTN0Y.dTVjcUG9mO7CMpgu2nanQKVrdl0j9Di', '자유탈퇴', '010-9000-0001', 'MEMBER', DATE_SUB(NOW(), INTERVAL 120 DAY)),
    (v_user + 2, 'withdraw-leader@example.invalid',                 '$2y$10$UFubgfNbK8aoN/7hmTN0Y.dTVjcUG9mO7CMpgu2nanQKVrdl0j9Di', '동호회장탈퇴', '010-9000-0002', 'MEMBER', DATE_SUB(NOW(), INTERVAL 110 DAY)),
    (v_user + 3, 'withdraw-team@example.invalid',                   '$2y$10$UFubgfNbK8aoN/7hmTN0Y.dTVjcUG9mO7CMpgu2nanQKVrdl0j9Di', '팀원탈퇴', '010-9000-0003', 'MEMBER', DATE_SUB(NOW(), INTERVAL 100 DAY)),
    (v_user + 4, 'withdraw-individual-recruiting@example.invalid',  '$2y$10$UFubgfNbK8aoN/7hmTN0Y.dTVjcUG9mO7CMpgu2nanQKVrdl0j9Di', '개인모집탈퇴', '010-9000-0004', 'MEMBER', DATE_SUB(NOW(), INTERVAL 90 DAY)),
    (v_user + 5, 'withdraw-individual-matched@example.invalid',     '$2y$10$UFubgfNbK8aoN/7hmTN0Y.dTVjcUG9mO7CMpgu2nanQKVrdl0j9Di', '개인완료탈퇴', '010-9000-0005', 'MEMBER', DATE_SUB(NOW(), INTERVAL 80 DAY)),
    (v_user + 6, 'withdraw-cancelled@example.invalid',              '$2y$10$UFubgfNbK8aoN/7hmTN0Y.dTVjcUG9mO7CMpgu2nanQKVrdl0j9Di', '취소탈퇴', '010-9000-0006', 'MEMBER', DATE_SUB(NOW(), INTERVAL 70 DAY)),
    (v_user + 7, 'withdraw-ended@example.invalid',                  '$2y$10$UFubgfNbK8aoN/7hmTN0Y.dTVjcUG9mO7CMpgu2nanQKVrdl0j9Di', '종료탈퇴', '010-9000-0007', 'MEMBER', DATE_SUB(NOW(), INTERVAL 60 DAY)),
    (v_user + 8, 'withdraw-club-leader@example.invalid',            '$2y$10$UFubgfNbK8aoN/7hmTN0Y.dTVjcUG9mO7CMpgu2nanQKVrdl0j9Di', '지원팀장', '010-9000-0008', 'MEMBER', DATE_SUB(NOW(), INTERVAL 150 DAY)),
    (v_user + 9, 'withdraw-club-member@example.invalid',            '$2y$10$UFubgfNbK8aoN/7hmTN0Y.dTVjcUG9mO7CMpgu2nanQKVrdl0j9Di', '지원팀원', '010-9000-0009', 'MEMBER', DATE_SUB(NOW(), INTERVAL 140 DAY)),
    (v_user + 10, 'withdraw-opponent@example.invalid',               '$2y$10$UFubgfNbK8aoN/7hmTN0Y.dTVjcUG9mO7CMpgu2nanQKVrdl0j9Di', '지원상대', '010-9000-0010', 'MEMBER', DATE_SUB(NOW(), INTERVAL 130 DAY));

-- 선호 종목(회원당 1행) ------------------------------------------------
INSERT INTO user_preferred_sport (user_id, sport_id) VALUES
    (v_user + 1, v_tennis),
    (v_user + 2, v_tennis),
    (v_user + 3, v_tennis),
    (v_user + 4, v_tennis),
    (v_user + 5, v_tennis),
    (v_user + 6, v_tennis),
    (v_user + 7, v_tennis),
    (v_user + 8, v_tennis),
    (v_user + 9, v_tennis),
    (v_user + 10, v_football);

-- 선호 지역 -------------------------------------------------------------
INSERT INTO user_region (user_id, region_id) VALUES
    (v_user + 1, v_region + 2),
    (v_user + 2, v_region + 2),
    (v_user + 3, v_region + 3),
    (v_user + 4, v_region + 2),
    (v_user + 5, v_region + 3),
    (v_user + 6, v_region + 2),
    (v_user + 7, v_region + 2),
    (v_user + 8, v_region + 2),
    (v_user + 9, v_region + 3),
    (v_user + 10, v_region + 3);

-- 시설 -----------------------------------------------------------------
INSERT INTO facility (facility_id, region_id, name, address, latitude, longitude,
                      concurrent_match_limit, status, deleted_at) VALUES
    (v_facility + 1, v_region + 2, '탈퇴검증 테니스 센터', '탈퇴검증 광역시 중구 검증로 1',
     37.5500000, 126.9200000, 3, '운영 중', NULL);

INSERT INTO facility_sport (facility_id, sport_id, default_min, default_max,
                            default_fee, default_skill, note) VALUES
    (v_facility + 1, v_tennis, 2, 8, 15000.00, '초급', '탈퇴 수동 테스트용 시설 안내입니다.');

-- 요일별 운영 시간(월~일 모두 운영) ---------------------------------------
INSERT INTO operating_rule (facility_id, weekday, open_time, close_time, end_next_day, is_open) VALUES
    (v_facility + 1, 1, '06:00:00', '23:00:00', FALSE, TRUE),
    (v_facility + 1, 2, '06:00:00', '23:00:00', FALSE, TRUE),
    (v_facility + 1, 3, '06:00:00', '23:00:00', FALSE, TRUE),
    (v_facility + 1, 4, '06:00:00', '23:00:00', FALSE, TRUE),
    (v_facility + 1, 5, '06:00:00', '23:00:00', FALSE, TRUE),
    (v_facility + 1, 6, '08:00:00', '22:00:00', FALSE, TRUE),
    (v_facility + 1, 7, '08:00:00', '22:00:00', FALSE, TRUE);

-- 동호회 ---------------------------------------------------------------
-- v_club + 1: withdraw-leader 가 동호회장 -> leaderClubNames 차단
-- v_club + 2: withdraw-team 이 동호회원 -> leaderClubNames 차단, 모집 중 팀 매치 신청은 individualMatchNames 차단
--       (withdraw-team 은 동호회장이 아니다)
INSERT INTO club (club_id, sport_id, club_name, intro, max_members, recruit_status,
                  image_url, activity_days, activity_start_time, activity_end_time,
                  deleted_at, created_at) VALUES
    (v_club + 1, v_tennis, '탈퇴검증 팀장 클럽', '동호회장 탈퇴 차단을 확인하는 동호회입니다.',
     10, '모집중', NULL, '2,4', '19:00:00', '21:00:00', NULL, DATE_SUB(NOW(), INTERVAL 90 DAY)),
    (v_club + 2, v_tennis, '탈퇴검증 팀매치 클럽', '모집 중 팀 매치 신청 차단을 확인하는 동호회입니다.',
     10, '모집중', NULL, '1,3', '20:00:00', '22:00:00', NULL, DATE_SUB(NOW(), INTERVAL 60 DAY));

INSERT INTO club_region (club_id, region_id) VALUES
    (v_club + 1, v_region + 2),
    (v_club + 2, v_region + 2),
    (v_club + 2, v_region + 3);

INSERT INTO club_member (club_member_id, club_id, user_id, role, joined_at, left_at, leave_type, kick_reason) VALUES
    (v_member + 1, v_club + 1, v_user + 2, '동호회장', DATE_SUB(NOW(), INTERVAL 90 DAY), NULL, NULL, NULL),
    (v_member + 2, v_club + 1, v_user + 10, '동호회원', DATE_SUB(NOW(), INTERVAL 85 DAY), NULL, NULL, NULL),
    (v_member + 3, v_club + 2, v_user + 8, '동호회장', DATE_SUB(NOW(), INTERVAL 60 DAY), NULL, NULL, NULL),
    (v_member + 4, v_club + 2, v_user + 3, '동호회원', DATE_SUB(NOW(), INTERVAL 55 DAY), NULL, NULL, NULL),
    (v_member + 5, v_club + 2, v_user + 9, '동호회원', DATE_SUB(NOW(), INTERVAL 50 DAY), NULL, NULL, NULL);

-- 매칭방 ---------------------------------------------------------------
-- v_match + 1 모집 중 / v_match + 2 매칭 완료(경기 전) / v_match + 3 종료 / v_match + 4 취소 / v_match + 5 모집 중(팀)
INSERT INTO social_match (match_id, facility_id, sport_id, created_by, title, start_at, end_at,
                          min_participants, max_participants, team_a_capacity, team_b_capacity,
                          fee_per_person, required_skill, status, assignment_method,
                          assigned_by, assignment_confirmed_at, rating_status, rating_processed_at,
                          cancelled_at) VALUES
    (v_match + 1, v_facility + 1, v_tennis, v_user + 8, '탈퇴검증 개인 모집 매치',
     DATE_ADD(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 3 DAY) + INTERVAL 2 HOUR,
     2, 8, 4, 4, 15000.00, '초급', '모집 중', 'MANUAL',
     NULL, NULL, 'WAITING', NULL, NULL),
    (v_match + 2, v_facility + 1, v_tennis, v_user + 8, '탈퇴검증 개인 매칭 완료 매치',
     DATE_ADD(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 1 DAY) + INTERVAL 2 HOUR,
     2, 8, 4, 4, 15000.00, '중급', '매칭 완료', 'MANUAL',
     v_user + 8, DATE_SUB(NOW(), INTERVAL 6 HOUR), 'WAITING', NULL, NULL),
    (v_match + 3, v_facility + 1, v_tennis, v_user + 8, '탈퇴검증 개인 종료 매치',
     DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY) + INTERVAL 2 HOUR,
     2, 8, 4, 4, 15000.00, '초급', '종료', 'MANUAL',
     v_user + 8, DATE_SUB(NOW(), INTERVAL 3 DAY), 'APPLIED', DATE_SUB(NOW(), INTERVAL 2 DAY), NULL),
    (v_match + 4, v_facility + 1, v_tennis, v_user + 8, '탈퇴검증 취소 매치',
     DATE_ADD(NOW(), INTERVAL 5 DAY), DATE_ADD(NOW(), INTERVAL 5 DAY) + INTERVAL 2 HOUR,
     2, 8, 4, 4, 15000.00, '초급', '취소', 'MANUAL',
     NULL, NULL, 'EXCLUDED', NULL, DATE_SUB(NOW(), INTERVAL 1 DAY)),
    (v_match + 5, v_facility + 1, v_tennis, v_user + 8, '탈퇴검증 팀 모집 매치',
     DATE_ADD(NOW(), INTERVAL 4 DAY), DATE_ADD(NOW(), INTERVAL 4 DAY) + INTERVAL 2 HOUR,
     4, 8, 4, 4, 15000.00, '중급', '모집 중', 'MANUAL',
     NULL, NULL, 'WAITING', NULL, NULL);

-- 신청 -----------------------------------------------------------------
-- v_application + 1 모집 중 개인 대기(차단) / v_application + 2 매칭 완료 개인 확정(차단) / v_application + 3 종료 경기 확정(비차단)
-- v_application + 4 취소된 매치의 개인 대기(비차단) / v_application + 5 모집 중 매치의 취소된 개인 신청(비차단)
-- v_application + 6~v_application + 7 모집 중 팀 매치 동호회 묶음 신청(팀원 차단)
INSERT INTO match_application (application_id, match_id, applicant_user_id, club_id,
                               application_type, status, receipt_no, created_at,
                               confirmed_at, cancelled_at, cancelled_by, group_application_id) VALUES
    (v_application + 1, v_match + 1, v_user + 4, NULL, '개인', '대기', 'WD-IND-RECRUITING', DATE_SUB(NOW(), INTERVAL 2 HOUR),
     NULL, NULL, NULL, NULL),
    (v_application + 2, v_match + 2, v_user + 5, NULL, '개인', '확정', 'WD-IND-MATCHED', DATE_SUB(NOW(), INTERVAL 1 DAY),
     DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL, NULL),
    (v_application + 3, v_match + 3, v_user + 7, NULL, '개인', '확정', 'WD-ENDED', DATE_SUB(NOW(), INTERVAL 5 DAY),
     DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, NULL, NULL),
    (v_application + 4, v_match + 4, v_user + 6, NULL, '개인', '대기', 'WD-CANCELLED-MATCH', DATE_SUB(NOW(), INTERVAL 6 DAY),
     NULL, NULL, NULL, NULL),
    (v_application + 5, v_match + 1, v_user + 6, NULL, '개인', '취소', 'WD-CANCELLED-APP', DATE_SUB(NOW(), INTERVAL 3 DAY),
     NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), v_user + 6, NULL),
    (v_application + 6, v_match + 5, v_user + 8, v_club + 2, '동호회', '대기', 'WD-TEAM-LEADER', DATE_SUB(NOW(), INTERVAL 1 DAY),
     NULL, NULL, NULL, v_application + 6),
    (v_application + 7, v_match + 5, v_user + 3, v_club + 2, '동호회', '대기', 'WD-TEAM-MEMBER', DATE_SUB(NOW(), INTERVAL 1 DAY),
     NULL, NULL, NULL, v_application + 6),
    (v_application + 8, v_match + 3, v_user + 10, NULL, '개인', '확정', 'WD-ENDED-2', DATE_SUB(NOW(), INTERVAL 5 DAY),
     DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, NULL, NULL),
    (v_application + 9, v_match + 5, v_user + 9, v_club + 2, '동호회', '대기', 'WD-TEAM-MEMBER-2', DATE_SUB(NOW(), INTERVAL 1 DAY),
     NULL, NULL, NULL, v_application + 6);

-- 참가자 ---------------------------------------------------------------
INSERT INTO match_participant (participant_id, application_id, match_id, user_id,
                               participation_status, match_side, entry_mmr, joined_at, cancelled_at) VALUES
    (v_participant + 1, v_application + 2, v_match + 2, v_user + 5, '확정', 'A', 1180.00, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL),
    (v_participant + 2, v_application + 3, v_match + 3, v_user + 7, '확정', 'B', 1020.00, DATE_SUB(NOW(), INTERVAL 5 DAY), NULL),
    -- 취소된 개인 신청의 참가자 행: active_match_user 가 NULL 이 되어 정원·중복 판정에서 빠진다.
    (v_participant + 3, v_application + 5, v_match + 1, v_user + 6, '취소', NULL, 1000.00, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
    (v_participant + 4, v_application + 8, v_match + 3, v_user + 10, '확정', 'A', 1010.00, DATE_SUB(NOW(), INTERVAL 5 DAY), NULL),
    (v_participant + 5, v_application + 6, v_match + 5, v_user + 8, '대기', NULL, 1000.00, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL),
    (v_participant + 6, v_application + 7, v_match + 5, v_user + 3, '대기', NULL, 1000.00, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL),
    (v_participant + 7, v_application + 9, v_match + 5, v_user + 9, '대기', NULL, 1000.00, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL);

-- 종목별 레이팅(프로필 표시값) -------------------------------------------
INSERT INTO sport_rating (user_id, sport_id, current_mmr, rated_match_count, updated_at) VALUES
    (v_user + 1, v_tennis, 1042.50, 3, DATE_SUB(NOW(), INTERVAL 10 DAY)),
    (v_user + 2, v_tennis, 1000.00, 0, DATE_SUB(NOW(), INTERVAL 90 DAY)),
    (v_user + 3, v_tennis, 1000.00, 0, DATE_SUB(NOW(), INTERVAL 55 DAY)),
    (v_user + 4, v_tennis, 1000.00, 0, DATE_SUB(NOW(), INTERVAL 20 DAY)),
    (v_user + 5, v_tennis, 1180.00, 2, DATE_SUB(NOW(), INTERVAL 1 DAY)),
    (v_user + 6, v_tennis, 1000.00, 0, DATE_SUB(NOW(), INTERVAL 30 DAY)),
    (v_user + 7, v_tennis, 1020.00, 1, DATE_SUB(NOW(), INTERVAL 2 DAY)),
    (v_user + 8, v_tennis, 1000.00, 0, DATE_SUB(NOW(), INTERVAL 60 DAY)),
    (v_user + 9, v_tennis, 1000.00, 0, DATE_SUB(NOW(), INTERVAL 50 DAY)),
    (v_user + 10, v_tennis, 1010.00, 1, DATE_SUB(NOW(), INTERVAL 5 DAY));

    COMMIT;
END$$
DELIMITER ;
CALL spoit_wd_fixture_20261104();
DROP PROCEDURE spoit_wd_fixture_20261104;

-- 결과 확인: 차단 이유는 애플리케이션의 조회 조건과 같다.
SELECT u.email,
  (SELECT COUNT(DISTINCT c.club_id) FROM club_member cm JOIN club c ON c.club_id=cm.club_id
    WHERE cm.user_id=u.user_id AND cm.role='동호회장' AND cm.left_at IS NULL AND c.deleted_at IS NULL) AS leader_blocks,
  (SELECT COUNT(DISTINCT ma.club_id) FROM match_application ma JOIN social_match sm ON sm.match_id=ma.match_id
    WHERE ma.club_id IS NOT NULL AND ma.application_type='동호회' AND ma.status IN ('대기','확정') AND sm.status='모집 중'
      AND (ma.applicant_user_id=u.user_id OR EXISTS (SELECT 1 FROM match_participant mp WHERE mp.application_id=ma.application_id AND mp.user_id=u.user_id AND mp.participation_status IN ('대기','확정')))) AS team_blocks,
  (SELECT COUNT(DISTINCT sm.match_id) FROM match_application ma JOIN social_match sm ON sm.match_id=ma.match_id
    WHERE ma.applicant_user_id=u.user_id AND ma.application_type='개인' AND ma.status IN ('대기','확정') AND sm.status IN ('모집 중','매칭 완료')) AS individual_blocks
FROM app_user u WHERE u.email IN ('withdraw-free@example.invalid','withdraw-leader@example.invalid','withdraw-team@example.invalid',
 'withdraw-individual-recruiting@example.invalid','withdraw-individual-matched@example.invalid','withdraw-cancelled@example.invalid',
 'withdraw-ended@example.invalid','withdraw-club-leader@example.invalid','withdraw-club-member@example.invalid','withdraw-opponent@example.invalid')
ORDER BY u.email;
