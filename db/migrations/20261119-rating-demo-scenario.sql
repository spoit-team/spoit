-- =====================================================================
-- 레이팅 시연 시나리오 데이터 (발표 시연 6~9번: 자동 배정 → 결과 제출·평가 → 티어 변동)
-- 대상: spoit(로컬·운영). spoit_mat_test에는 적용하지 않는다.
-- 다른 마이그레이션과 달리 리허설 직전마다, 그리고 시연 당일 아침에 다시 실행한다.
--   결과 입력 기간이 경기 종료 후 24시간이라 결과 입력용 경기를 실행 시각 기준으로 새로 만든다.
--   실행할 때마다 레이팅을 시작값으로 되돌리고 지난 리허설 흔적을 정리하므로 여러 번 실행해도 안전하다.
-- 비밀번호: player@spoit.test의 해시를 복사한다(seed 회원과 같은 비밀번호).
-- 자세한 내용은 db/migrations/README.md의 "레이팅 시연 시나리오" 참고
-- =====================================================================

START TRANSACTION;

SET @sport_id = (SELECT sport_id FROM sport WHERE name = '축구+풋살');
SET @fm_user = (SELECT user_id FROM app_user WHERE email = 'facility@spoit.test');
SET @facility_id = (
    SELECT fm.facility_id
    FROM facility_manager fm
    JOIN facility f ON f.facility_id = fm.facility_id AND f.deleted_at IS NULL
    JOIN facility_sport fs ON fs.facility_id = fm.facility_id AND fs.sport_id = @sport_id
    WHERE fm.user_id = @fm_user
    ORDER BY fm.facility_id
    LIMIT 1);
SET @region_id = (SELECT region_id FROM facility WHERE facility_id = @facility_id);
SET @pw = (SELECT password_hash FROM app_user WHERE email = 'player@spoit.test');
-- 앱은 Asia/Seoul 기준 시각을 저장하므로 DB 서버 시간대와 관계없이 KST로 맞춘다.
SET @now = UTC_TIMESTAMP() + INTERVAL 9 HOUR;

-- ---------------------------------------------------------------------
-- 1. 계정 7개
--    leader, m1~m5: 동호회 팀 신청(4번) → 자동 배정(6번)에 쓰는 6명. 레이팅은 발표 24장 예시와 같다.
--    member: 티어 변동 확인(9번)용 팀원. 1050점 = 실버 3 · 0P
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS demo_user;
CREATE TEMPORARY TABLE demo_user (
    k        VARCHAR(10) PRIMARY KEY,
    email    VARCHAR(255) NOT NULL,
    nickname VARCHAR(50)  NOT NULL,
    phone    VARCHAR(20)  NOT NULL,
    mmr      DECIMAL(8,2) NOT NULL,
    user_id  BIGINT       NULL
);
INSERT INTO demo_user (k, email, nickname, phone, mmr) VALUES
  ('leader', 'demo.leader@spoit.test', '이도윤', '010-2000-0001', 1180),
  ('m1',     'demo.m1@spoit.test',     '정하준', '010-2000-0002', 1130),
  ('m2',     'demo.m2@spoit.test',     '최서진', '010-2000-0003', 1060),
  ('m3',     'demo.m3@spoit.test',     '한지호', '010-2000-0004', 1010),
  ('m4',     'demo.m4@spoit.test',     '윤태민', '010-2000-0005', 960),
  ('m5',     'demo.m5@spoit.test',     '강민재', '010-2000-0006', 890),
  ('member', 'demo.member@spoit.test', '오지안', '010-2000-0007', 1050);

INSERT INTO app_user (email, password_hash, nickname, phone, role, created_at)
SELECT d.email, @pw, d.nickname, d.phone, 'MEMBER', @now
FROM demo_user d
WHERE NOT EXISTS (SELECT 1 FROM app_user u WHERE u.email = d.email);

UPDATE demo_user d JOIN app_user u ON u.email = d.email SET d.user_id = u.user_id;
-- 리허설 중 정지·탈퇴 처리됐어도 시연 전에 되살린다.
UPDATE app_user u JOIN demo_user d ON d.user_id = u.user_id SET u.deleted_at = NULL, u.penalty_time = NULL;

INSERT INTO user_preferred_sport (user_id, sport_id)
SELECT user_id, @sport_id FROM demo_user
ON DUPLICATE KEY UPDATE sport_id = @sport_id;

INSERT IGNORE INTO user_region (user_id, region_id)
SELECT user_id, @region_id FROM demo_user;

-- ---------------------------------------------------------------------
-- 2. 동호회 (팀 신청용, 동호회장 = leader)
-- ---------------------------------------------------------------------
INSERT INTO club (sport_id, club_name, intro, max_members, recruit_status, activity_days,
                  activity_start_time, activity_end_time, created_at)
SELECT @sport_id, '송파 토요 풋살회', '토요일 오전에 모여 3대3 풋살을 하는 동호회입니다.', 10, '모집중', '6',
       '10:00:00', '12:00:00', @now
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM club WHERE club_name = '송파 토요 풋살회' AND deleted_at IS NULL);

SET @club_id = (SELECT club_id FROM club WHERE club_name = '송파 토요 풋살회' AND deleted_at IS NULL);

INSERT IGNORE INTO club_region (club_id, region_id) VALUES (@club_id, @region_id);

INSERT INTO club_member (club_id, user_id, role, joined_at)
SELECT @club_id, d.user_id, IF(d.k = 'leader', '동호회장', '동호회원'), @now
FROM demo_user d
WHERE d.k <> 'member'
  AND NOT EXISTS (SELECT 1 FROM club_member cm
                  WHERE cm.club_id = @club_id AND cm.user_id = d.user_id AND cm.left_at IS NULL);

-- ---------------------------------------------------------------------
-- 3. 이전 리허설 정리
--    결과 입력용 경기는 신청 접수 번호 DEMO- 로 구분한다.
--    끝나지 않은 경기는 취소해 결과 입력 목록에 두 개가 보이지 않게 하고, 반영 이력은 지운다.
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS demo_old_match;
CREATE TEMPORARY TABLE demo_old_match (match_id BIGINT PRIMARY KEY);
INSERT IGNORE INTO demo_old_match
SELECT DISTINCT match_id FROM match_application WHERE receipt_no LIKE 'DEMO-%';

UPDATE social_match sm JOIN demo_old_match o ON o.match_id = sm.match_id
SET sm.status = '취소', sm.cancelled_at = @now
WHERE sm.rating_status = 'WAITING' AND sm.status <> '취소';

DELETE h FROM sport_rating_history h JOIN demo_old_match o ON o.match_id = h.match_id;

-- 리허설 때 시연 동호회가 팀 신청한 매칭방도 취소한다.
-- 남겨 두면 시작 시각이 지난 뒤 팀장의 결과 입력 목록에 함께 나와 본 시연에서 헷갈린다.
DROP TEMPORARY TABLE IF EXISTS demo_club_match;
CREATE TEMPORARY TABLE demo_club_match (match_id BIGINT PRIMARY KEY);
INSERT IGNORE INTO demo_club_match
SELECT DISTINCT match_id FROM match_application WHERE club_id = @club_id;

UPDATE social_match sm JOIN demo_club_match c ON c.match_id = sm.match_id
SET sm.status = '취소', sm.cancelled_at = @now
WHERE sm.status <> '취소';

UPDATE match_application a JOIN demo_club_match c ON c.match_id = a.match_id
SET a.status = '취소', a.cancelled_at = @now
WHERE a.status IN ('대기', '확정');

UPDATE match_participant p JOIN demo_club_match c ON c.match_id = p.match_id
SET p.participation_status = '취소', p.cancelled_at = @now
WHERE p.participation_status IN ('대기', '확정');

DELETE h FROM sport_rating_history h JOIN demo_club_match c ON c.match_id = h.match_id;

-- ---------------------------------------------------------------------
-- 4. 레이팅을 시연 시작값으로 (반영 5경기 = 배치 완료, K 32)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sport_rating (user_id, sport_id) SELECT user_id, @sport_id FROM demo_user;
UPDATE sport_rating sr JOIN demo_user d ON d.user_id = sr.user_id
SET sr.current_mmr = d.mmr, sr.rated_match_count = 5
WHERE sr.sport_id = @sport_id;

-- ---------------------------------------------------------------------
-- 5. 결과 입력용 경기 (8번)
--    1시간 전에 끝난 3대3. A팀 평균 1000 / B팀 평균 1100 (발표 26장 예시)
--    B팀장(m1)은 A팀 승리로 결과와 평가를 이미 냈다. 시연에서 A팀장(leader)이 결과와 평가를 내면 바로 반영된다.
--    entry_mmr은 신청 시점 스냅샷이라 현재 레이팅과 달라도 된다.
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS demo_lineup;
CREATE TEMPORARY TABLE demo_lineup (
    k       VARCHAR(10) PRIMARY KEY,
    side    CHAR(1)      NOT NULL,
    entry   DECIMAL(8,2) NOT NULL,
    eval_b  TINYINT      NULL      -- B팀장이 준 점수(항목 모두 같은 점수)
);
INSERT INTO demo_lineup VALUES
  ('leader', 'A', 1100, 7),
  ('member', 'A', 1050, 7),
  ('m5',     'A',  850, 6),
  ('m1',     'B', 1150, NULL),
  ('m2',     'B', 1100, 8),
  ('m3',     'B', 1050, 6);

INSERT INTO social_match (facility_id, sport_id, created_by, title, start_at, end_at,
                          min_participants, max_participants, team_a_capacity, team_b_capacity,
                          fee_per_person, required_skill, status, assignment_method, assigned_by,
                          assignment_confirmed_at, result_status, rating_status)
VALUES (@facility_id, @sport_id, @fm_user, '송파 3대3 풋살 친선전',
        @now - INTERVAL 2 HOUR, @now - INTERVAL 1 HOUR,
        6, 6, 3, 3, 0, '골드', '매칭 완료', 'AUTO', @fm_user,
        @now - INTERVAL 1 DAY, 'SUBMITTED', 'WAITING');
SET @match_id = LAST_INSERT_ID();

INSERT INTO match_application (match_id, applicant_user_id, application_type, status, receipt_no,
                               created_at, confirmed_at)
SELECT @match_id, d.user_id, '개인', '확정', CONCAT('DEMO-', @match_id, '-', l.k),
       @now - INTERVAL 3 DAY, @now - INTERVAL 2 DAY
FROM demo_lineup l JOIN demo_user d ON d.k = l.k;

INSERT INTO match_participant (application_id, match_id, user_id, participation_status, match_side,
                               entry_mmr, joined_at)
SELECT a.application_id, @match_id, a.applicant_user_id, '확정', l.side, l.entry, @now - INTERVAL 2 DAY
FROM match_application a
JOIN demo_lineup l ON a.receipt_no = CONCAT('DEMO-', @match_id, '-', l.k)
WHERE a.match_id = @match_id;

SET @cap_a = (SELECT p.participant_id FROM match_participant p
              JOIN match_application a ON a.application_id = p.application_id
              WHERE a.receipt_no = CONCAT('DEMO-', @match_id, '-leader'));
SET @cap_b = (SELECT p.participant_id FROM match_participant p
              JOIN match_application a ON a.application_id = p.application_id
              WHERE a.receipt_no = CONCAT('DEMO-', @match_id, '-m1'));

UPDATE social_match
SET captain_a_participant_id = @cap_a, captain_b_participant_id = @cap_b
WHERE match_id = @match_id;

INSERT INTO match_result_submission (match_id, captain_participant_id, reported_result,
                                     submission_status, submitted_at)
VALUES (@match_id, @cap_b, 'A_WIN', 'SUBMITTED', @now - INTERVAL 30 MINUTE);

INSERT INTO match_evaluation (match_id, sport_id, evaluator_participant_id, target_participant_id,
                              overall_score, scale_max, status, submitted_at)
SELECT @match_id, @sport_id, @cap_b, p.participant_id, l.eval_b, 10, '제출 완료', @now - INTERVAL 30 MINUTE
FROM match_participant p
JOIN match_application a ON a.application_id = p.application_id
JOIN demo_lineup l ON a.receipt_no = CONCAT('DEMO-', @match_id, '-', l.k)
WHERE p.match_id = @match_id AND p.participant_id <> @cap_b;

INSERT INTO match_evaluation_score (evaluation_id, item_id, sport_id, score, observation_status)
SELECT e.evaluation_id, i.item_id, @sport_id, ROUND(e.overall_score), 'OBSERVED'
FROM match_evaluation e
JOIN sport_evaluation_item i ON i.sport_id = @sport_id AND i.is_active = TRUE
WHERE e.match_id = @match_id;

COMMIT;

-- ---------------------------------------------------------------------
-- 확인
-- ---------------------------------------------------------------------
-- 6번 자동 배정용: 1180 1130 1060 1010 960 890, 모두 반영 5경기
SELECT d.k, d.email, d.nickname, sr.current_mmr, sr.rated_match_count
FROM demo_user d JOIN sport_rating sr ON sr.user_id = d.user_id AND sr.sport_id = @sport_id
ORDER BY sr.current_mmr DESC;

-- 8번 경기: A 평균 1000.00 / B 평균 1100.00, 마감 = 종료 + 24시간
SELECT @match_id AS match_id, sm.end_at, sm.end_at + INTERVAL 24 HOUR AS deadline,
       AVG(CASE WHEN p.match_side = 'A' THEN p.entry_mmr END) AS avg_a,
       AVG(CASE WHEN p.match_side = 'B' THEN p.entry_mmr END) AS avg_b
FROM social_match sm JOIN match_participant p ON p.match_id = sm.match_id
WHERE sm.match_id = @match_id
GROUP BY sm.match_id, sm.end_at;
