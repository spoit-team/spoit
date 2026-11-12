-- =====================================================================
-- 동호회 시연 데이터: 축구+풋살 동호회 10개(시연 2개 포함), 회원 76명,
--                   지난 동호회 경기 50개(레이팅 이력 500행), 11/29 매칭방 2개와 팀 신청 10행
-- 이 파일은 scripts/club-demo-data.py가 만든다. 직접 고치지 말고 스크립트를 고쳐 다시 만든다.
-- 대상: 20261110-seoul-public-facilities.sql과 20261112-club-single-region.sql을 적용한 DB (로컬·운영)
-- 내용
--   * 축구+풋살 동호회 10개: 이름·소개·활동 시간·인원이 실제 동호회처럼 다르다. 비밀번호는 모두 spoit1234.
--   * 시연 동호회 가락 FS(발표)와 코사 FS(리허설): club-00·03 = 동호회장 A, club-01·04 = B(가장 먼저 가입),
--     club-02·05 = C(11/29 팀 신청 대표). 나머지 회원은 club-member-NNN@spoit.test.
--   * 지난 경기는 2026-08~11월 초 동호회끼리 치른 5:5(홈 동호회의 구장·활동 시간). 팀 신청 확정 → 참가자 → 두 팀장 결과 제출 →
--     결과 마감(종료 24시간 뒤)에 평가 없이 레이팅 반영까지 끝난 종료 상태다. 이력은 RatingCalculator와 같은 식으로
--     1000부터 시간순 계산했고, sport_rating은 마지막 이력 값·이력 행 수다. 팀 등급은 이 레이팅에서 나온다.
--   * 시연 동호회마다 11/29(일) 잠실종합운동장 제2풋살경기장 매칭방 1개와 C~G 팀 신청(대기).
--     → 발표 때 "모집 중인 팀 매치에 신청된 동호회원은 추방할 수 없습니다"가 나온다.
-- 원칙
--   * 한 트랜잭션. 사전 확인에 실패하면 멈추고 아무 행도 넣지 않는다.
--   * club-00@spoit.test가 이미 있으면 적용된 것으로 보고 아무것도 넣지 않는다(오류 없음).
--   * 종목·지역·시설·담당자는 이름으로 찾는다. 새 행 번호는 각 테이블의 현재 최대 번호 다음부터 쓴다. 기존 행은 바꾸지 않는다.
-- =====================================================================

SET NAMES utf8mb4;
START TRANSACTION;

-- 0. 이름으로 번호 찾기
SET @cd_s = (SELECT MAX(sport_id) FROM sport WHERE name = '축구+풋살');
SET @cd_r_광진구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '광진구');
SET @cd_r_구로구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '구로구');
SET @cd_r_동작구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '동작구');
SET @cd_r_마포구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '마포구');
SET @cd_r_서초구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '서초구');
SET @cd_r_송파구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '송파구');
SET @cd_r_영등포구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '영등포구');
SET @cd_r_용산구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '용산구');
SET @cd_f1 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '송파구' AND f.name = '잠실종합운동장 풋살경기장' AND f.deleted_at IS NULL);
SET @cd_f1_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @cd_f1 AND u.email LIKE 'facility-__@spoit.test');
SET @cd_f2 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '서초구' AND f.name = '반포한강공원 축구장' AND f.deleted_at IS NULL);
SET @cd_f2_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @cd_f2 AND u.email LIKE 'facility-__@spoit.test');
SET @cd_f3 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '용산구' AND f.name = '이촌한강공원 풋살경기장(옥수역하부)' AND f.deleted_at IS NULL);
SET @cd_f3_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @cd_f3 AND u.email LIKE 'facility-__@spoit.test');
SET @cd_f4 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '영등포구' AND f.name = '영등포공원 풋살경기장' AND f.deleted_at IS NULL);
SET @cd_f4_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @cd_f4 AND u.email LIKE 'facility-__@spoit.test');
SET @cd_f5 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '마포구' AND f.name = '망원유수지 풋살장' AND f.deleted_at IS NULL);
SET @cd_f5_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @cd_f5 AND u.email LIKE 'facility-__@spoit.test');
SET @cd_f6 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '광진구' AND f.name = '뚝섬한강공원 축구장 1' AND f.deleted_at IS NULL);
SET @cd_f6_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @cd_f6 AND u.email LIKE 'facility-__@spoit.test');
SET @cd_f7 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '구로구' AND f.name = '안양천 체육시설 풋살경기장(구일역 하부)' AND f.deleted_at IS NULL);
SET @cd_f7_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @cd_f7 AND u.email LIKE 'facility-__@spoit.test');
SET @cd_f8 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '동작구' AND f.name = '노량진 축구장' AND f.deleted_at IS NULL);
SET @cd_f8_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @cd_f8 AND u.email LIKE 'facility-__@spoit.test');
SET @cd_f9 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '송파구' AND f.name = '잠실종합운동장 제2풋살경기장' AND f.deleted_at IS NULL);
SET @cd_f9_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @cd_f9 AND u.email LIKE 'facility-__@spoit.test');

-- 사전 확인. 출력되는 값이 모두 1이어야 진행한다. already_applied가 1이면 이미 적용된 DB라 아무것도 넣지 않는다.
SELECT (@cd_s IS NOT NULL),
       ((@cd_r_광진구 IS NOT NULL) + (@cd_r_구로구 IS NOT NULL) + (@cd_r_동작구 IS NOT NULL) + (@cd_r_마포구 IS NOT NULL) + (@cd_r_서초구 IS NOT NULL) + (@cd_r_송파구 IS NOT NULL) + (@cd_r_영등포구 IS NOT NULL) + (@cd_r_용산구 IS NOT NULL)) = 8,
       ((@cd_f1 IS NOT NULL AND @cd_f1_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cd_f1 AND sport_id = @cd_s)) + (@cd_f2 IS NOT NULL AND @cd_f2_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cd_f2 AND sport_id = @cd_s)) + (@cd_f3 IS NOT NULL AND @cd_f3_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cd_f3 AND sport_id = @cd_s)) + (@cd_f4 IS NOT NULL AND @cd_f4_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cd_f4 AND sport_id = @cd_s)) + (@cd_f5 IS NOT NULL AND @cd_f5_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cd_f5 AND sport_id = @cd_s)) + (@cd_f6 IS NOT NULL AND @cd_f6_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cd_f6 AND sport_id = @cd_s)) + (@cd_f7 IS NOT NULL AND @cd_f7_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cd_f7 AND sport_id = @cd_s)) + (@cd_f8 IS NOT NULL AND @cd_f8_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cd_f8 AND sport_id = @cd_s)) + (@cd_f9 IS NOT NULL AND @cd_f9_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cd_f9 AND sport_id = @cd_s))) = 9,
       NOT EXISTS (SELECT 1 FROM club WHERE active_club_name IN ('잠실 드림풋살', '반포 리버사이드 FC', '이촌 한강 FS', '영등포 레인보우 FS', '망원 선데이 FC', 'BLUE WAVE FS', '구일 FS', 'OFFSIDE FC', '가락 FS', '코사 FS')),
       NOT EXISTS (SELECT 1 FROM social_match WHERE status <> '취소' AND (
         (facility_id = @cd_f7 AND start_at < '2026-08-01 16:00:00' AND end_at > '2026-08-01 14:00:00') OR
         (facility_id = @cd_f2 AND start_at < '2026-08-01 09:00:00' AND end_at > '2026-08-01 07:00:00') OR
         (facility_id = @cd_f9 AND start_at < '2026-08-02 20:00:00' AND end_at > '2026-08-02 18:00:00') OR
         (facility_id = @cd_f1 AND start_at < '2026-08-05 21:00:00' AND end_at > '2026-08-05 19:00:00') OR
         (facility_id = @cd_f7 AND start_at < '2026-08-08 16:00:00' AND end_at > '2026-08-08 14:00:00') OR
         (facility_id = @cd_f2 AND start_at < '2026-08-09 09:00:00' AND end_at > '2026-08-09 07:00:00') OR
         (facility_id = @cd_f1 AND start_at < '2026-08-12 21:00:00' AND end_at > '2026-08-12 19:00:00') OR
         (facility_id = @cd_f8 AND start_at < '2026-08-14 22:00:00' AND end_at > '2026-08-14 20:00:00') OR
         (facility_id = @cd_f9 AND start_at < '2026-08-16 20:00:00' AND end_at > '2026-08-16 18:00:00') OR
         (facility_id = @cd_f8 AND start_at < '2026-08-21 22:00:00' AND end_at > '2026-08-21 20:00:00') OR
         (facility_id = @cd_f7 AND start_at < '2026-08-22 16:00:00' AND end_at > '2026-08-22 14:00:00') OR
         (facility_id = @cd_f5 AND start_at < '2026-08-23 10:00:00' AND end_at > '2026-08-23 08:00:00') OR
         (facility_id = @cd_f1 AND start_at < '2026-08-26 21:00:00' AND end_at > '2026-08-26 19:00:00') OR
         (facility_id = @cd_f3 AND start_at < '2026-08-28 22:00:00' AND end_at > '2026-08-28 20:00:00') OR
         (facility_id = @cd_f9 AND start_at < '2026-08-30 20:00:00' AND end_at > '2026-08-30 18:00:00') OR
         (facility_id = @cd_f8 AND start_at < '2026-09-01 22:00:00' AND end_at > '2026-09-01 20:00:00') OR
         (facility_id = @cd_f4 AND start_at < '2026-09-03 22:00:00' AND end_at > '2026-09-03 20:00:00') OR
         (facility_id = @cd_f3 AND start_at < '2026-09-04 22:00:00' AND end_at > '2026-09-04 20:00:00') OR
         (facility_id = @cd_f2 AND start_at < '2026-09-05 09:00:00' AND end_at > '2026-09-05 07:00:00') OR
         (facility_id = @cd_f5 AND start_at < '2026-09-06 10:00:00' AND end_at > '2026-09-06 08:00:00') OR
         (facility_id = @cd_f1 AND start_at < '2026-09-09 21:00:00' AND end_at > '2026-09-09 19:00:00') OR
         (facility_id = @cd_f8 AND start_at < '2026-09-11 22:00:00' AND end_at > '2026-09-11 20:00:00') OR
         (facility_id = @cd_f6 AND start_at < '2026-09-13 18:00:00' AND end_at > '2026-09-13 16:00:00') OR
         (facility_id = @cd_f4 AND start_at < '2026-09-17 22:00:00' AND end_at > '2026-09-17 20:00:00') OR
         (facility_id = @cd_f3 AND start_at < '2026-09-18 22:00:00' AND end_at > '2026-09-18 20:00:00') OR
         (facility_id = @cd_f2 AND start_at < '2026-09-20 09:00:00' AND end_at > '2026-09-20 07:00:00') OR
         (facility_id = @cd_f8 AND start_at < '2026-09-22 22:00:00' AND end_at > '2026-09-22 20:00:00') OR
         (facility_id = @cd_f2 AND start_at < '2026-09-26 09:00:00' AND end_at > '2026-09-26 07:00:00') OR
         (facility_id = @cd_f7 AND start_at < '2026-09-26 16:00:00' AND end_at > '2026-09-26 14:00:00') OR
         (facility_id = @cd_f1 AND start_at < '2026-09-26 21:00:00' AND end_at > '2026-09-26 19:00:00') OR
         (facility_id = @cd_f4 AND start_at < '2026-10-01 22:00:00' AND end_at > '2026-10-01 20:00:00') OR
         (facility_id = @cd_f8 AND start_at < '2026-10-02 22:00:00' AND end_at > '2026-10-02 20:00:00') OR
         (facility_id = @cd_f5 AND start_at < '2026-10-04 10:00:00' AND end_at > '2026-10-04 08:00:00') OR
         (facility_id = @cd_f1 AND start_at < '2026-10-07 21:00:00' AND end_at > '2026-10-07 19:00:00') OR
         (facility_id = @cd_f3 AND start_at < '2026-10-09 22:00:00' AND end_at > '2026-10-09 20:00:00') OR
         (facility_id = @cd_f2 AND start_at < '2026-10-10 09:00:00' AND end_at > '2026-10-10 07:00:00') OR
         (facility_id = @cd_f7 AND start_at < '2026-10-10 16:00:00' AND end_at > '2026-10-10 14:00:00') OR
         (facility_id = @cd_f8 AND start_at < '2026-10-13 22:00:00' AND end_at > '2026-10-13 20:00:00') OR
         (facility_id = @cd_f1 AND start_at < '2026-10-14 21:00:00' AND end_at > '2026-10-14 19:00:00') OR
         (facility_id = @cd_f4 AND start_at < '2026-10-15 22:00:00' AND end_at > '2026-10-15 20:00:00') OR
         (facility_id = @cd_f7 AND start_at < '2026-10-17 16:00:00' AND end_at > '2026-10-17 14:00:00') OR
         (facility_id = @cd_f4 AND start_at < '2026-10-22 22:00:00' AND end_at > '2026-10-22 20:00:00') OR
         (facility_id = @cd_f3 AND start_at < '2026-10-23 22:00:00' AND end_at > '2026-10-23 20:00:00') OR
         (facility_id = @cd_f7 AND start_at < '2026-10-24 16:00:00' AND end_at > '2026-10-24 14:00:00') OR
         (facility_id = @cd_f2 AND start_at < '2026-10-24 09:00:00' AND end_at > '2026-10-24 07:00:00') OR
         (facility_id = @cd_f5 AND start_at < '2026-10-25 10:00:00' AND end_at > '2026-10-25 08:00:00') OR
         (facility_id = @cd_f8 AND start_at < '2026-10-27 22:00:00' AND end_at > '2026-10-27 20:00:00') OR
         (facility_id = @cd_f1 AND start_at < '2026-10-28 21:00:00' AND end_at > '2026-10-28 19:00:00') OR
         (facility_id = @cd_f1 AND start_at < '2026-11-04 21:00:00' AND end_at > '2026-11-04 19:00:00') OR
         (facility_id = @cd_f6 AND start_at < '2026-11-08 18:00:00' AND end_at > '2026-11-08 16:00:00') OR
         (facility_id = @cd_f9 AND start_at < '2026-11-29 12:00:00' AND end_at > '2026-11-29 10:00:00') OR
         (facility_id = @cd_f9 AND start_at < '2026-11-29 20:00:00' AND end_at > '2026-11-29 18:00:00'))),
       NOT EXISTS (SELECT 1 FROM match_application WHERE receipt_no IN ('RCPT-20260728-62YJ339Y', 'RCPT-20260728-L2476YH3', 'RCPT-20260728-CUHKUS58', 'RCPT-20260728-RJS27FB4', 'RCPT-20260728-ACJ3M3K3', 'RCPT-20260729-K7RXTSJG', 'RCPT-20260729-4FAAC2B3', 'RCPT-20260729-SLMW5HEK', 'RCPT-20260729-BU223EB8', 'RCPT-20260729-RCV8YCHD', 'RCPT-20260729-SXYAGK8X', 'RCPT-20260729-S3YTC9N5', 'RCPT-20260729-X47X6TWG', 'RCPT-20260729-BJQ7VCSQ', 'RCPT-20260729-YVQDZEEA', 'RCPT-20260729-MDJM2X3W', 'RCPT-20260729-RAQFA9L8', 'RCPT-20260729-FJH2GKW4', 'RCPT-20260729-2JB6KDA2', 'RCPT-20260729-MDYVUX52', 'RCPT-20260731-NT3RGTQ5', 'RCPT-20260731-YH3MUMGB', 'RCPT-20260731-UL2XE7BH', 'RCPT-20260731-HBHHUZXS', 'RCPT-20260731-AR3JLAW3', 'RCPT-20260731-CQBSJM39', 'RCPT-20260731-RLAQ9HDN', 'RCPT-20260731-76XLCM32', 'RCPT-20260731-KJHH7JAK', 'RCPT-20260731-3RWM9VYW', 'RCPT-20260802-UZABRW5H', 'RCPT-20260802-KQM36QFJ', 'RCPT-20260802-9KDPWJ2N', 'RCPT-20260802-DD84MC4X', 'RCPT-20260802-KE87CGTW', 'RCPT-20260802-7VL4SH9M', 'RCPT-20260802-NJTC8687', 'RCPT-20260802-MBXKXLLL', 'RCPT-20260802-FGWN4RSB', 'RCPT-20260802-H3KQ8RLP', 'RCPT-20260805-K6MMCSLW', 'RCPT-20260805-FAY8LSFF', 'RCPT-20260805-AKALQBRM', 'RCPT-20260805-5E87GP5M', 'RCPT-20260805-ZC87BS8J', 'RCPT-20260807-PHW25767', 'RCPT-20260807-Q5CLFCVM', 'RCPT-20260807-X9JNZGQH', 'RCPT-20260807-ZJWHCJJX', 'RCPT-20260807-BVV9BZCF', 'RCPT-20260807-QVAU7KPM', 'RCPT-20260807-BXM7MT7Z', 'RCPT-20260807-8G5XTX3Q', 'RCPT-20260807-G8SCXXSA', 'RCPT-20260807-SP5V3Z2P', 'RCPT-20260805-MRH6L8MV', 'RCPT-20260805-6G67XDSP', 'RCPT-20260805-3WW6467F', 'RCPT-20260805-C5FEMHAU', 'RCPT-20260805-MUW5HXQC', 'RCPT-20260809-7CZ5F4F2', 'RCPT-20260809-8DT8D9XM', 'RCPT-20260809-72DD9QRP', 'RCPT-20260809-NZLZNWAS', 'RCPT-20260809-T8DDPH7J', 'RCPT-20260810-CMY6FXRD', 'RCPT-20260810-EAUAMCUN', 'RCPT-20260810-4JED9ZA6', 'RCPT-20260810-UNQHFLEJ', 'RCPT-20260810-4BWVYL4P', 'RCPT-20260812-G8LJ7VAJ', 'RCPT-20260812-E84Q9J8S', 'RCPT-20260812-N3YKPZ4S', 'RCPT-20260812-WZXJC95U', 'RCPT-20260812-4G4WAFRE', 'RCPT-20260811-W48AM235', 'RCPT-20260811-XQS5F63X', 'RCPT-20260811-JR9AQCN6', 'RCPT-20260811-W789JB77', 'RCPT-20260811-VHS3BCHN', 'RCPT-20260814-7VA4VAPX', 'RCPT-20260814-RUWP5EH9', 'RCPT-20260814-CD5NVAEY', 'RCPT-20260814-E4S7MYWH', 'RCPT-20260814-S2XADRXT', 'RCPT-20260814-4T3KK52M', 'RCPT-20260814-5GDG28LG', 'RCPT-20260814-FK3JUF2X', 'RCPT-20260814-TV2BN9YV', 'RCPT-20260814-Y7FMCRNF', 'RCPT-20260817-Z4T2MJ2H', 'RCPT-20260817-WDL2JUBX', 'RCPT-20260817-HM6JW3VV', 'RCPT-20260817-YM6LD8UA', 'RCPT-20260817-EBZCJLD6', 'RCPT-20260817-KVE4FAR2', 'RCPT-20260817-6YH9MCJV', 'RCPT-20260817-WLTGUC34', 'RCPT-20260817-NBT8WXFG', 'RCPT-20260817-G358R96P', 'RCPT-20260819-9WFHXV22', 'RCPT-20260819-4BP5GGH5', 'RCPT-20260819-6835VRXM', 'RCPT-20260819-Z8Y5GR2Y', 'RCPT-20260819-W3FY6F9E', 'RCPT-20260819-H9MLVBDT', 'RCPT-20260819-J7HTAVSV', 'RCPT-20260819-WZYW9RRM', 'RCPT-20260819-DDLXUA53', 'RCPT-20260819-2HX93RRN', 'RCPT-20260820-E53U26G4', 'RCPT-20260820-Z8NSLGGQ', 'RCPT-20260820-5JP3LWVD', 'RCPT-20260820-AJ93CPYX', 'RCPT-20260820-XBN6WP9B', 'RCPT-20260819-G5GFMPCM', 'RCPT-20260819-X9KXQA23', 'RCPT-20260819-9DADNDDA', 'RCPT-20260819-TEHLN34Y', 'RCPT-20260819-F9WQ54RT', 'RCPT-20260824-2JF2GMAB', 'RCPT-20260824-TEPWMB6P', 'RCPT-20260824-BGXSAN2J', 'RCPT-20260824-69T74R65', 'RCPT-20260824-KTGDPTF8', 'RCPT-20260824-VHXD8GNB', 'RCPT-20260824-FYBQVE2C', 'RCPT-20260824-U5B9FQFP', 'RCPT-20260824-JNU972FV', 'RCPT-20260824-KWPUAJTP', 'RCPT-20260825-NABG5MFY', 'RCPT-20260825-FY8HHEGM', 'RCPT-20260825-PR8FPKJW', 'RCPT-20260825-SPMK3KXU', 'RCPT-20260825-9DEEHJLH', 'RCPT-20260825-FSNMMF2G', 'RCPT-20260825-CVQSKQAN', 'RCPT-20260825-J4SX8SUS', 'RCPT-20260825-EBYY8NKT', 'RCPT-20260825-QL3UWKAL', 'RCPT-20260828-2JBUZ29Q', 'RCPT-20260828-8K9SYM5H', 'RCPT-20260828-DNZYNYHJ', 'RCPT-20260828-EPKAXDDU', 'RCPT-20260828-6DLSE85K', 'RCPT-20260828-Q7REWT3V', 'RCPT-20260828-GYSCCAEV', 'RCPT-20260828-A4QPUPQT', 'RCPT-20260828-5969P9JW', 'RCPT-20260828-26BH9X6Y', 'RCPT-20260829-BXWTFJQS', 'RCPT-20260829-9EBWXLJD', 'RCPT-20260829-NPHWYA3C', 'RCPT-20260829-W8LK8ESD', 'RCPT-20260829-XL85V2SF', 'RCPT-20260829-CZ3J8LWC', 'RCPT-20260829-ZVLTU8T2', 'RCPT-20260829-NWVNX4D7', 'RCPT-20260829-R4HJM8BC', 'RCPT-20260829-AZ3CPWFF', 'RCPT-20260831-TLV4X46T', 'RCPT-20260831-SMSZ6EBL', 'RCPT-20260831-3J75KSLC', 'RCPT-20260831-6NFJXDPW', 'RCPT-20260831-9MUSD6XK', 'RCPT-20260831-JM8DNE9F', 'RCPT-20260831-ZZ9X5YAZ', 'RCPT-20260831-3ET46UX7', 'RCPT-20260831-RY8Y7DJ8', 'RCPT-20260831-LRSNWCEF', 'RCPT-20260831-YDC2Q772', 'RCPT-20260831-KXTXQ8DU', 'RCPT-20260831-QJQ38E52', 'RCPT-20260831-A3RZA4EE', 'RCPT-20260831-FTECJ2KJ', 'RCPT-20260902-MZQWVZQK', 'RCPT-20260902-MH352L2S', 'RCPT-20260902-XZCVE9PD', 'RCPT-20260902-88XUYZNE', 'RCPT-20260902-PEG2NS67', 'RCPT-20260831-232TGMBZ', 'RCPT-20260831-G2SLLRHR', 'RCPT-20260831-KB9KEZNY', 'RCPT-20260831-M9BE6M76', 'RCPT-20260831-AVA5PACF', 'RCPT-20260902-L2VDF2G3', 'RCPT-20260902-YPSQME7M', 'RCPT-20260902-RLAL8RK4', 'RCPT-20260902-82M4ZPXX', 'RCPT-20260902-VHP98AHH', 'RCPT-20260903-8YLPHRSG', 'RCPT-20260903-EMC6Z64A', 'RCPT-20260903-WLN4ZUFE', 'RCPT-20260903-S4FSCETL', 'RCPT-20260903-N299US5U', 'RCPT-20260904-HXALH8R3', 'RCPT-20260904-LFFTEJT5', 'RCPT-20260904-VMB4536H', 'RCPT-20260904-WW7FJQYC', 'RCPT-20260904-9NGRPBPM', 'RCPT-20260906-XHEWXBHH', 'RCPT-20260906-KE4PGKSC', 'RCPT-20260906-Y2VLLF7A', 'RCPT-20260906-NCQSULJS', 'RCPT-20260906-9EM9GVNP', 'RCPT-20260907-LAW3R9AL', 'RCPT-20260907-9MWGG856', 'RCPT-20260907-S4WZMU4X', 'RCPT-20260907-VYBVVUZS', 'RCPT-20260907-U5JN59DA', 'RCPT-20260908-UPTQ2NM5', 'RCPT-20260908-625GD2RW', 'RCPT-20260908-VQMBKPMH', 'RCPT-20260908-JVRAWHY8', 'RCPT-20260908-TX2BY7WS', 'RCPT-20260908-A85SA2GE', 'RCPT-20260908-3V844754', 'RCPT-20260908-L874DTBK', 'RCPT-20260908-9FDY4DSE', 'RCPT-20260908-BGZPN9EL', 'RCPT-20260910-2DKWEL72', 'RCPT-20260910-SST7GF86', 'RCPT-20260910-YU5WGAB6', 'RCPT-20260910-GZZCVTBK', 'RCPT-20260910-CCPMFCKM', 'RCPT-20260911-7CPVAGCW', 'RCPT-20260911-V7ATDWN6', 'RCPT-20260911-QLGCLEDC', 'RCPT-20260911-63YLYRCR', 'RCPT-20260911-937HX7TM', 'RCPT-20260915-R3GJG6M6', 'RCPT-20260915-FXUM4QWN', 'RCPT-20260915-YQGLXJYB', 'RCPT-20260915-CMSD9Q6P', 'RCPT-20260915-7CX7KP6S', 'RCPT-20260915-PS4TMQCJ', 'RCPT-20260915-JQJ5VZMA', 'RCPT-20260915-2B29VSBD', 'RCPT-20260915-7XC52Q7D', 'RCPT-20260915-HK99PG6M', 'RCPT-20260916-J5JF2JCM', 'RCPT-20260916-NY2C48FP', 'RCPT-20260916-GFHBVWB9', 'RCPT-20260916-ZXFLACPR', 'RCPT-20260916-2FYYA8ND', 'RCPT-20260915-3DZNSWDS', 'RCPT-20260915-WU78YNR5', 'RCPT-20260915-NLEJLDUD', 'RCPT-20260915-776ZC32X', 'RCPT-20260915-3DZDENV6', 'RCPT-20260915-F7TNWC7N', 'RCPT-20260915-QSXPE725', 'RCPT-20260915-8DBN85MQ', 'RCPT-20260915-2ADPYBDS', 'RCPT-20260915-VWECR5SM', 'RCPT-20260915-EA2YDKPY', 'RCPT-20260915-5HJ79MFG', 'RCPT-20260915-259YDB3Y', 'RCPT-20260915-BLHRB2LN', 'RCPT-20260915-YJTBTW9Q', 'RCPT-20260920-XZJSG9ZQ', 'RCPT-20260920-SMB9BUHU', 'RCPT-20260920-N9C7LZPK', 'RCPT-20260920-5SKDM2XG', 'RCPT-20260920-F84ZUZ8R', 'RCPT-20260919-DSKXYGT8', 'RCPT-20260919-22ZAPDJL', 'RCPT-20260919-ZT996N7W', 'RCPT-20260919-7EXR9WW6', 'RCPT-20260919-6YMUGDSR', 'RCPT-20260923-S7EQR6FL', 'RCPT-20260923-N5V5GLHF', 'RCPT-20260923-23JQN4CF', 'RCPT-20260923-MB3S2AWG', 'RCPT-20260923-SWPJKYC6', 'RCPT-20260922-NLE2LBR8', 'RCPT-20260922-RF5N7YER', 'RCPT-20260922-U868LL3X', 'RCPT-20260922-6HQLAX93', 'RCPT-20260922-2AK86W7T', 'RCPT-20260922-UEQ549V9', 'RCPT-20260922-FGXDEAAU', 'RCPT-20260922-82XJV8TP', 'RCPT-20260922-EQPQLXJV', 'RCPT-20260922-78MB6NMU', 'RCPT-20260924-G5NUANLV', 'RCPT-20260924-B792VGTX', 'RCPT-20260924-NWDT9M6M', 'RCPT-20260924-D63UEF2E', 'RCPT-20260924-UPXK6TBC', 'RCPT-20260924-DHK4ECR8', 'RCPT-20260924-HMWDCQEV', 'RCPT-20260924-4MWSWSCF', 'RCPT-20260924-PNTE7DC4', 'RCPT-20260924-Q38CFSWB', 'RCPT-20260924-7AELVW67', 'RCPT-20260924-AFR9QQLT', 'RCPT-20260924-2CBHAK2J', 'RCPT-20260924-GDJQ9UUE', 'RCPT-20260924-2LB8EPH8', 'RCPT-20260928-QC8KVAAK', 'RCPT-20260928-SMXLVEW4', 'RCPT-20260928-MWCH6K6V', 'RCPT-20260928-PVYS9CNG', 'RCPT-20260928-39UDPSBK', 'RCPT-20260929-HJMT9WBB', 'RCPT-20260929-38FSH5X2', 'RCPT-20260929-WFAS3FQC', 'RCPT-20260929-D95CH3YT', 'RCPT-20260929-SQPU73D8', 'RCPT-20260928-7WQ9G3CK', 'RCPT-20260928-7H6WBZHJ', 'RCPT-20260928-KA7MFP3G', 'RCPT-20260928-C5MGHAL4', 'RCPT-20260928-XUL359KA', 'RCPT-20260929-VFPANRQ3', 'RCPT-20260929-Z5J9R72K', 'RCPT-20260929-U6Z7NJNR', 'RCPT-20260929-6ND3NJKV', 'RCPT-20260929-6W7PEHGF', 'RCPT-20260930-R9XWX6AF', 'RCPT-20260930-SALZ6V78', 'RCPT-20260930-S6SE7Y67', 'RCPT-20260930-B564AQU7', 'RCPT-20260930-85WX8LS8', 'RCPT-20260930-8VRNSR2L', 'RCPT-20260930-4VPE23KU', 'RCPT-20260930-96JL2UWX', 'RCPT-20260930-XYBTCQ6Z', 'RCPT-20260930-YES22T7Q', 'RCPT-20261004-XMT6AQKH', 'RCPT-20261004-D4U34DWZ', 'RCPT-20261004-VVLMN4EA', 'RCPT-20261004-A3UHE8QT', 'RCPT-20261004-PHFWRYA2', 'RCPT-20261003-PYE66P67', 'RCPT-20261003-ZT872JPR', 'RCPT-20261003-92UGREAL', 'RCPT-20261003-GPFX5P2F', 'RCPT-20261003-DC64EXDK', 'RCPT-20261006-AEX8AXWR', 'RCPT-20261006-HYUMM962', 'RCPT-20261006-MXEMPBPM', 'RCPT-20261006-PJTR6922', 'RCPT-20261006-S9QLQARS', 'RCPT-20261007-5XU437X4', 'RCPT-20261007-H7Y4CK8Q', 'RCPT-20261007-ZMCNP9U7', 'RCPT-20261007-FM76N67Y', 'RCPT-20261007-NMH6VTWA', 'RCPT-20261006-SZ56D2JG', 'RCPT-20261006-7VWZR5KW', 'RCPT-20261006-PDGK8F4H', 'RCPT-20261006-3RAEH2Q3', 'RCPT-20261006-V5NUANR2', 'RCPT-20261009-E9S988YJ', 'RCPT-20261009-2AFEES3F', 'RCPT-20261009-H37WXWA6', 'RCPT-20261009-JHBM53HK', 'RCPT-20261009-WRKFNTRY', 'RCPT-20261007-BVA9HNW6', 'RCPT-20261007-TFF2N2L6', 'RCPT-20261007-JRZZKT4J', 'RCPT-20261007-G5EA2JP5', 'RCPT-20261007-WR89674H', 'RCPT-20261007-SZJXHPJE', 'RCPT-20261007-GSPTQ6VN', 'RCPT-20261007-LY4ZLXUR', 'RCPT-20261007-F7MTUMNV', 'RCPT-20261007-D2ZQVLWV', 'RCPT-20261009-V65SUVHL', 'RCPT-20261009-8B5PPQES', 'RCPT-20261009-Q32647WZ', 'RCPT-20261009-XDE5XFW6', 'RCPT-20261009-CCCGDT8T', 'RCPT-20261011-6G6NN3UJ', 'RCPT-20261011-7TBSXFDK', 'RCPT-20261011-UNRJYAF9', 'RCPT-20261011-UTXHXQZM', 'RCPT-20261011-MATT9XFQ', 'RCPT-20261011-8FTHDNMM', 'RCPT-20261011-SX9LAP4P', 'RCPT-20261011-UX9VTVHV', 'RCPT-20261011-YM6N7WZY', 'RCPT-20261011-6V8M99X8', 'RCPT-20261012-SFQH3KWX', 'RCPT-20261012-33NJF739', 'RCPT-20261012-PUFEXZ8E', 'RCPT-20261012-N2J4SLKS', 'RCPT-20261012-GD6XXFBH', 'RCPT-20261011-6YW2BWA9', 'RCPT-20261011-9WQZXKH9', 'RCPT-20261011-YHR6SPJM', 'RCPT-20261011-P2JXZ8HU', 'RCPT-20261011-WT8PHLCP', 'RCPT-20261013-9CTJMZ66', 'RCPT-20261013-73472TV7', 'RCPT-20261013-88AQ2JV4', 'RCPT-20261013-EBM2723X', 'RCPT-20261013-479JH9AF', 'RCPT-20261014-RUAV86B7', 'RCPT-20261014-XV4NLVNH', 'RCPT-20261014-WY67PZKY', 'RCPT-20261014-KGJA4R94', 'RCPT-20261014-RKLJBY4A', 'RCPT-20261014-3QQYV3SM', 'RCPT-20261014-FK578HLK', 'RCPT-20261014-J244LSV2', 'RCPT-20261014-4VZY63MT', 'RCPT-20261014-7JEH3SEP', 'RCPT-20261019-ZLJXGMM9', 'RCPT-20261019-YLW7WAAM', 'RCPT-20261019-VH3UF624', 'RCPT-20261019-EQHSF7TD', 'RCPT-20261019-FWDX399U', 'RCPT-20261018-P7KZF59P', 'RCPT-20261018-XCHUYS6X', 'RCPT-20261018-J7AQVR72', 'RCPT-20261018-VQ4JSBNS', 'RCPT-20261018-43BJ397S', 'RCPT-20261019-S2ZBSASM', 'RCPT-20261019-J54T9QEY', 'RCPT-20261019-TB7PYY3G', 'RCPT-20261019-VRWFSJ9J', 'RCPT-20261019-QXH8YYJW', 'RCPT-20261020-2X8CPHQV', 'RCPT-20261020-6V8PGECE', 'RCPT-20261020-56JVHYVK', 'RCPT-20261020-F6GQYVQ3', 'RCPT-20261020-9MXRFBJN', 'RCPT-20261020-G6M3PTRV', 'RCPT-20261020-BXDZVAFE', 'RCPT-20261020-H7FW2GZX', 'RCPT-20261020-C2JHN3YP', 'RCPT-20261020-6V4EMJ6N', 'RCPT-20261022-3CJS2VLW', 'RCPT-20261022-SBDFFLMH', 'RCPT-20261022-ZCLG4XDF', 'RCPT-20261022-KAL9KMB8', 'RCPT-20261022-QR2ERCRY', 'RCPT-20261022-SWTKRJN2', 'RCPT-20261022-9KEBCWFB', 'RCPT-20261022-DELNL8ZG', 'RCPT-20261022-AS33A65X', 'RCPT-20261022-KHD2TYGX', 'RCPT-20261021-8YV4NE3C', 'RCPT-20261021-9JPC2N69', 'RCPT-20261021-CUPRGFGY', 'RCPT-20261021-A22QMXKL', 'RCPT-20261021-6SLQ38VU', 'RCPT-20261023-U74YACU5', 'RCPT-20261023-QUYBSBD9', 'RCPT-20261023-PN6LFLPY', 'RCPT-20261023-4GR6DMC4', 'RCPT-20261023-EKXQCY3S', 'RCPT-20261024-GXS7DMHC', 'RCPT-20261024-K3ELBC3C', 'RCPT-20261024-66YKNUVD', 'RCPT-20261024-234SR8YU', 'RCPT-20261024-E4TYTN5N', 'RCPT-20261025-RP7853SA', 'RCPT-20261025-T5F2GP3G', 'RCPT-20261025-6SGMJP3P', 'RCPT-20261025-8AHW9LTG', 'RCPT-20261025-U7UHGMU3', 'RCPT-20261025-NW89PZ8Y', 'RCPT-20261025-BVZSKJKX', 'RCPT-20261025-NX6WWWMY', 'RCPT-20261025-UQHN8KBS', 'RCPT-20261025-UXHHKHKC', 'RCPT-20261026-DUKD59NK', 'RCPT-20261026-DFUDJVXH', 'RCPT-20261026-28Q9DBH8', 'RCPT-20261026-TFS4ES4N', 'RCPT-20261026-4BZWBK2B', 'RCPT-20261026-MVZRMFXA', 'RCPT-20261026-JEMQXGSY', 'RCPT-20261026-7WQ5M8J8', 'RCPT-20261026-WF6X2J2V', 'RCPT-20261026-GAX4XZTH', 'RCPT-20261102-3E3FVJYH', 'RCPT-20261102-YFU6CFDK', 'RCPT-20261102-4WSHBHZ4', 'RCPT-20261102-3WQ4R8RA', 'RCPT-20261102-DN8BLP4E', 'RCPT-20261031-NPKC96PN', 'RCPT-20261031-TKBD3RYE', 'RCPT-20261031-RQX8YKDY', 'RCPT-20261031-EPM7H4DS', 'RCPT-20261031-RQEH6T6X', 'RCPT-20261104-YS2N42GT', 'RCPT-20261104-JY4JFKWN', 'RCPT-20261104-YZWRQBLY', 'RCPT-20261104-QNQNENNH', 'RCPT-20261104-JFPV7VXK', 'RCPT-20261104-Y53MDKV7', 'RCPT-20261104-5GCNVNH9', 'RCPT-20261104-A97UCMRN', 'RCPT-20261104-LM5VEE9V', 'RCPT-20261104-NQH6HHXB', 'RCPT-20261111-GKRSKPBM', 'RCPT-20261111-3ESVPJEC', 'RCPT-20261111-CUSJ9E6L', 'RCPT-20261111-RG4G2CAL', 'RCPT-20261111-WK7SR4HY', 'RCPT-20261111-487KTF3X', 'RCPT-20261111-SNNGBLSX', 'RCPT-20261111-QH2BVVNW', 'RCPT-20261111-JTFM5EC6', 'RCPT-20261111-EW4TSLED')),
       NOT EXISTS (SELECT 1 FROM app_user WHERE email IN ('club-member-001@spoit.test', 'club-member-002@spoit.test', 'club-member-003@spoit.test', 'club-member-004@spoit.test', 'club-member-005@spoit.test', 'club-member-006@spoit.test', 'club-member-007@spoit.test', 'club-member-008@spoit.test', 'club-member-009@spoit.test', 'club-member-010@spoit.test', 'club-member-011@spoit.test', 'club-member-012@spoit.test', 'club-member-013@spoit.test', 'club-member-014@spoit.test', 'club-member-015@spoit.test', 'club-member-016@spoit.test', 'club-member-017@spoit.test', 'club-member-018@spoit.test', 'club-member-019@spoit.test', 'club-member-020@spoit.test', 'club-member-021@spoit.test', 'club-member-022@spoit.test', 'club-member-023@spoit.test', 'club-member-024@spoit.test', 'club-member-025@spoit.test', 'club-member-026@spoit.test', 'club-member-027@spoit.test', 'club-member-028@spoit.test', 'club-member-029@spoit.test', 'club-member-030@spoit.test', 'club-member-031@spoit.test', 'club-member-032@spoit.test', 'club-member-033@spoit.test', 'club-member-034@spoit.test', 'club-member-035@spoit.test', 'club-member-036@spoit.test', 'club-member-037@spoit.test', 'club-member-038@spoit.test', 'club-member-039@spoit.test', 'club-member-040@spoit.test', 'club-member-041@spoit.test', 'club-member-042@spoit.test', 'club-member-043@spoit.test', 'club-member-044@spoit.test', 'club-member-045@spoit.test', 'club-member-046@spoit.test', 'club-member-047@spoit.test', 'club-member-048@spoit.test', 'club-member-049@spoit.test', 'club-member-050@spoit.test', 'club-member-051@spoit.test', 'club-member-052@spoit.test', 'club-member-053@spoit.test', 'club-member-054@spoit.test', 'club-member-055@spoit.test', 'club-member-056@spoit.test', 'club-01@spoit.test', 'club-02@spoit.test', 'club-member-057@spoit.test', 'club-member-058@spoit.test', 'club-member-059@spoit.test', 'club-member-060@spoit.test', 'club-member-061@spoit.test', 'club-member-062@spoit.test', 'club-member-063@spoit.test', 'club-03@spoit.test', 'club-04@spoit.test', 'club-05@spoit.test', 'club-member-064@spoit.test', 'club-member-065@spoit.test', 'club-member-066@spoit.test', 'club-member-067@spoit.test', 'club-member-068@spoit.test', 'club-member-069@spoit.test', 'club-member-070@spoit.test')),
       EXISTS (SELECT 1 FROM app_user WHERE email = 'club-00@spoit.test')
  INTO @cd_sport, @cd_districts, @cd_facilities, @cd_club_names_free, @cd_slots_free, @cd_receipts_free, @cd_emails_free, @cd_already_applied;
SET @cd_checks = @cd_sport AND @cd_districts AND @cd_facilities AND @cd_club_names_free AND @cd_slots_free AND @cd_receipts_free AND @cd_emails_free;
SET @cd_ready = @cd_checks AND NOT @cd_already_applied;
SELECT @cd_sport AS sport, @cd_districts AS districts, @cd_facilities AS facilities, @cd_club_names_free AS club_names_free,
       @cd_slots_free AS slots_free, @cd_receipts_free AS receipts_free, @cd_emails_free AS emails_free,
       @cd_already_applied AS already_applied;
-- 이미 적용된 DB가 아닌데 확인에 실패하면 "Subquery returns more than 1 row" 오류로 멈춘다.
DO (SELECT 1 UNION ALL SELECT 1 FROM DUAL WHERE @cd_checks = 0 AND @cd_already_applied = 0);

SELECT MAX(user_id), (SELECT COALESCE(MAX(club_id), 0) FROM club), (SELECT COALESCE(MAX(club_member_id), 0) FROM club_member),
       (SELECT COALESCE(MAX(match_id), 0) FROM social_match), (SELECT COALESCE(MAX(application_id), 0) FROM match_application),
       (SELECT COALESCE(MAX(participant_id), 0) FROM match_participant)
  INTO @cd_u, @cd_c, @cd_m, @cd_sm, @cd_a, @cd_p
  FROM app_user;

-- 1. 회원 (비밀번호 spoit1234)
INSERT INTO app_user (user_id, email, password_hash, nickname, phone, role, created_at)
SELECT * FROM (
                 SELECT (@cd_u + 1) AS user_id, 'club-member-001@spoit.test' AS email, '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi' AS password_hash, '배시윤' AS nickname, '010-8801-5091' AS phone, 'MEMBER' AS role, '2026-06-11 05:00:00' AS created_at
       UNION ALL SELECT (@cd_u + 2), 'club-member-002@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '최대현', '010-8802-4425', 'MEMBER', '2026-07-02 02:00:00'
       UNION ALL SELECT (@cd_u + 3), 'club-member-003@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '전은우', '010-8803-2289', 'MEMBER', '2026-07-05 09:30:00'
       UNION ALL SELECT (@cd_u + 4), 'club-member-004@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '송형준', '010-8804-3651', 'MEMBER', '2026-07-04 08:00:00'
       UNION ALL SELECT (@cd_u + 5), 'club-member-005@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '류현준', '010-8805-7207', 'MEMBER', '2026-07-12 19:00:00'
       UNION ALL SELECT (@cd_u + 6), 'club-member-006@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '윤하린', '010-8806-4802', 'MEMBER', '2026-07-03 11:00:00'
       UNION ALL SELECT (@cd_u + 7), 'club-member-007@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '서상훈', '010-8807-9512', 'MEMBER', '2026-09-17 11:00:00'
       UNION ALL SELECT (@cd_u + 8), 'club-member-008@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '조민준', '010-8808-2210', 'MEMBER', '2025-05-21 02:00:00'
       UNION ALL SELECT (@cd_u + 9), 'club-member-009@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '강지우', '010-8809-4491', 'MEMBER', '2025-10-04 15:00:00'
       UNION ALL SELECT (@cd_u + 10), 'club-member-010@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '문시우', '010-8810-9695', 'MEMBER', '2026-03-11 15:00:00'
       UNION ALL SELECT (@cd_u + 11), 'club-member-011@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '백대현', '010-8811-8247', 'MEMBER', '2026-04-21 04:00:00'
       UNION ALL SELECT (@cd_u + 12), 'club-member-012@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '전승민', '010-8812-5638', 'MEMBER', '2026-06-06 04:30:00'
       UNION ALL SELECT (@cd_u + 13), 'club-member-013@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '윤우진', '010-8813-8490', 'MEMBER', '2026-07-16 14:30:00'
       UNION ALL SELECT (@cd_u + 14), 'club-member-014@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '이재원', '010-8814-7132', 'MEMBER', '2026-07-01 04:00:00'
       UNION ALL SELECT (@cd_u + 15), 'club-member-015@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '이태현', '010-8815-4972', 'MEMBER', '2026-04-13 04:00:00'
       UNION ALL SELECT (@cd_u + 16), 'club-member-016@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '배유준', '010-8816-2783', 'MEMBER', '2026-04-07 05:30:00'
       UNION ALL SELECT (@cd_u + 17), 'club-member-017@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '서윤서', '010-8817-5843', 'MEMBER', '2026-05-14 07:00:00'
       UNION ALL SELECT (@cd_u + 18), 'club-member-018@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '송지훈', '010-8818-7256', 'MEMBER', '2026-05-11 08:30:00'
       UNION ALL SELECT (@cd_u + 19), 'club-member-019@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '안수빈', '010-8819-4348', 'MEMBER', '2026-06-13 08:30:00'
       UNION ALL SELECT (@cd_u + 20), 'club-member-020@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '손태윤', '010-8820-5346', 'MEMBER', '2026-06-14 14:30:00'
       UNION ALL SELECT (@cd_u + 21), 'club-member-021@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '양채원', '010-8821-5407', 'MEMBER', '2026-10-08 12:00:00'
       UNION ALL SELECT (@cd_u + 22), 'club-member-022@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '남준혁', '010-8822-6189', 'MEMBER', '2025-10-30 05:00:00'
       UNION ALL SELECT (@cd_u + 23), 'club-member-023@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '안현우', '010-8823-6971', 'MEMBER', '2025-12-09 05:30:00'
       UNION ALL SELECT (@cd_u + 24), 'club-member-024@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '이도현', '010-8824-9652', 'MEMBER', '2026-02-26 06:00:00'
       UNION ALL SELECT (@cd_u + 25), 'club-member-025@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '홍하준', '010-8825-6240', 'MEMBER', '2026-04-12 02:30:00'
       UNION ALL SELECT (@cd_u + 26), 'club-member-026@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '양도윤', '010-8826-1763', 'MEMBER', '2026-04-08 09:30:00'
       UNION ALL SELECT (@cd_u + 27), 'club-member-027@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '홍준혁', '010-8827-8195', 'MEMBER', '2026-05-04 15:00:00'
       UNION ALL SELECT (@cd_u + 28), 'club-member-028@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '서형준', '010-8828-4920', 'MEMBER', '2026-06-06 08:30:00'
       UNION ALL SELECT (@cd_u + 29), 'club-member-029@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '강지호', '010-8829-9300', 'MEMBER', '2025-03-14 10:00:00'
       UNION ALL SELECT (@cd_u + 30), 'club-member-030@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '양유준', '010-8830-5221', 'MEMBER', '2025-05-01 04:00:00'
       UNION ALL SELECT (@cd_u + 31), 'club-member-031@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '이준우', '010-8831-3475', 'MEMBER', '2025-06-19 05:00:00'
       UNION ALL SELECT (@cd_u + 32), 'club-member-032@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '정수빈', '010-8832-2390', 'MEMBER', '2025-10-29 12:00:00'
       UNION ALL SELECT (@cd_u + 33), 'club-member-033@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '장예은', '010-8833-6521', 'MEMBER', '2026-01-18 13:00:00'
       UNION ALL SELECT (@cd_u + 34), 'club-member-034@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '고현우', '010-8834-4192', 'MEMBER', '2026-04-03 08:30:00'
       UNION ALL SELECT (@cd_u + 35), 'club-member-035@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '임영호', '010-8835-1719', 'MEMBER', '2026-09-02 05:00:00'
       UNION ALL SELECT (@cd_u + 36), 'club-member-036@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '이수빈', '010-8836-6086', 'MEMBER', '2026-08-08 10:00:00'
       UNION ALL SELECT (@cd_u + 37), 'club-member-037@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '오수빈', '010-8837-8884', 'MEMBER', '2026-07-26 17:00:00'
       UNION ALL SELECT (@cd_u + 38), 'club-member-038@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '안주원', '010-8838-7398', 'MEMBER', '2026-08-06 11:00:00'
       UNION ALL SELECT (@cd_u + 39), 'club-member-039@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '조하은', '010-8839-4371', 'MEMBER', '2026-08-25 06:30:00'
       UNION ALL SELECT (@cd_u + 40), 'club-member-040@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '유채원', '010-8840-1660', 'MEMBER', '2026-08-24 12:30:00'
       UNION ALL SELECT (@cd_u + 41), 'club-member-041@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '송민서', '010-8841-8830', 'MEMBER', '2026-08-16 12:30:00'
       UNION ALL SELECT (@cd_u + 42), 'club-member-042@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '송재원', '010-8842-5123', 'MEMBER', '2026-08-31 05:00:00'
       UNION ALL SELECT (@cd_u + 43), 'club-member-043@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '장재민', '010-8843-9969', 'MEMBER', '2026-06-10 05:00:00'
       UNION ALL SELECT (@cd_u + 44), 'club-member-044@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '조현우', '010-8844-9141', 'MEMBER', '2026-06-10 15:30:00'
       UNION ALL SELECT (@cd_u + 45), 'club-member-045@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '문우진', '010-8845-2102', 'MEMBER', '2026-07-09 12:30:00'
       UNION ALL SELECT (@cd_u + 46), 'club-member-046@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '정민지', '010-8846-6143', 'MEMBER', '2026-07-08 09:00:00'
       UNION ALL SELECT (@cd_u + 47), 'club-member-047@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '문도현', '010-8847-8087', 'MEMBER', '2026-07-15 15:30:00'
       UNION ALL SELECT (@cd_u + 48), 'club-member-048@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '전승우', '010-8848-3680', 'MEMBER', '2026-07-03 04:30:00'
       UNION ALL SELECT (@cd_u + 49), 'club-member-049@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '배은우', '010-8849-9940', 'MEMBER', '2026-08-27 04:30:00'
       UNION ALL SELECT (@cd_u + 50), 'club-member-050@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '박태현', '010-8850-8428', 'MEMBER', '2026-07-18 05:00:00'
       UNION ALL SELECT (@cd_u + 51), 'club-member-051@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '조지훈', '010-8851-1126', 'MEMBER', '2026-06-27 15:30:00'
       UNION ALL SELECT (@cd_u + 52), 'club-member-052@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '남예린', '010-8852-1524', 'MEMBER', '2026-07-07 09:00:00'
       UNION ALL SELECT (@cd_u + 53), 'club-member-053@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '조민지', '010-8853-8591', 'MEMBER', '2026-07-26 07:30:00'
       UNION ALL SELECT (@cd_u + 54), 'club-member-054@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '신수빈', '010-8854-2018', 'MEMBER', '2026-06-30 09:30:00'
       UNION ALL SELECT (@cd_u + 55), 'club-member-055@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '백예준', '010-8855-5045', 'MEMBER', '2026-07-30 08:30:00'
       UNION ALL SELECT (@cd_u + 56), 'club-member-056@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '배소율', '010-8856-5828', 'MEMBER', '2026-07-11 09:00:00'
       UNION ALL SELECT (@cd_u + 57), 'club-00@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '권선우', '010-8857-9675', 'MEMBER', '2026-02-19 03:00:00'
       UNION ALL SELECT (@cd_u + 58), 'club-01@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '오진수', '010-8858-2654', 'MEMBER', '2026-03-17 08:00:00'
       UNION ALL SELECT (@cd_u + 59), 'club-02@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '신현준', '010-8859-5131', 'MEMBER', '2026-04-07 12:00:00'
       UNION ALL SELECT (@cd_u + 60), 'club-member-057@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '서예준', '010-8860-6449', 'MEMBER', '2026-04-11 13:00:00'
       UNION ALL SELECT (@cd_u + 61), 'club-member-058@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '임민준', '010-8861-5325', 'MEMBER', '2026-05-13 04:00:00'
       UNION ALL SELECT (@cd_u + 62), 'club-member-059@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '최수빈', '010-8862-2445', 'MEMBER', '2026-05-02 22:00:00'
       UNION ALL SELECT (@cd_u + 63), 'club-member-060@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '전영호', '010-8863-2993', 'MEMBER', '2026-05-22 21:00:00'
       UNION ALL SELECT (@cd_u + 64), 'club-member-061@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '유형준', '010-8864-6579', 'MEMBER', '2026-06-09 10:30:00'
       UNION ALL SELECT (@cd_u + 65), 'club-member-062@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '오다은', '010-8865-2159', 'MEMBER', '2026-07-15 10:00:00'
       UNION ALL SELECT (@cd_u + 66), 'club-member-063@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '백성민', '010-8866-3502', 'MEMBER', '2026-09-24 07:00:00'
       UNION ALL SELECT (@cd_u + 67), 'club-03@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '백형준', '010-8867-6539', 'MEMBER', '2026-05-30 02:00:00'
       UNION ALL SELECT (@cd_u + 68), 'club-04@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '임정우', '010-8868-2667', 'MEMBER', '2026-06-07 09:00:00'
       UNION ALL SELECT (@cd_u + 69), 'club-05@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '윤도윤', '010-8869-1248', 'MEMBER', '2026-06-16 09:00:00'
       UNION ALL SELECT (@cd_u + 70), 'club-member-064@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '고성민', '010-8870-9934', 'MEMBER', '2026-06-02 18:30:00'
       UNION ALL SELECT (@cd_u + 71), 'club-member-065@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '유하준', '010-8871-8479', 'MEMBER', '2026-07-01 09:00:00'
       UNION ALL SELECT (@cd_u + 72), 'club-member-066@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '장하린', '010-8872-9565', 'MEMBER', '2026-06-29 13:00:00'
       UNION ALL SELECT (@cd_u + 73), 'club-member-067@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '전태윤', '010-8873-7500', 'MEMBER', '2026-06-29 15:00:00'
       UNION ALL SELECT (@cd_u + 74), 'club-member-068@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '황태현', '010-8874-9346', 'MEMBER', '2026-07-11 08:30:00'
       UNION ALL SELECT (@cd_u + 75), 'club-member-069@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '김서진', '010-8875-1488', 'MEMBER', '2026-07-23 09:30:00'
       UNION ALL SELECT (@cd_u + 76), 'club-member-070@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '한건우', '010-8876-1435', 'MEMBER', '2026-10-16 12:30:00'
) v
 WHERE @cd_ready = 1;

INSERT INTO user_region (user_id, region_id)
SELECT * FROM (
                 SELECT (@cd_u + 1) AS user_id, @cd_r_송파구 AS region_id
       UNION ALL SELECT (@cd_u + 2), @cd_r_송파구
       UNION ALL SELECT (@cd_u + 3), @cd_r_송파구
       UNION ALL SELECT (@cd_u + 4), @cd_r_송파구
       UNION ALL SELECT (@cd_u + 5), @cd_r_송파구
       UNION ALL SELECT (@cd_u + 6), @cd_r_송파구
       UNION ALL SELECT (@cd_u + 7), @cd_r_송파구
       UNION ALL SELECT (@cd_u + 8), @cd_r_서초구
       UNION ALL SELECT (@cd_u + 9), @cd_r_서초구
       UNION ALL SELECT (@cd_u + 10), @cd_r_서초구
       UNION ALL SELECT (@cd_u + 11), @cd_r_서초구
       UNION ALL SELECT (@cd_u + 12), @cd_r_서초구
       UNION ALL SELECT (@cd_u + 13), @cd_r_서초구
       UNION ALL SELECT (@cd_u + 14), @cd_r_서초구
       UNION ALL SELECT (@cd_u + 15), @cd_r_용산구
       UNION ALL SELECT (@cd_u + 16), @cd_r_용산구
       UNION ALL SELECT (@cd_u + 17), @cd_r_용산구
       UNION ALL SELECT (@cd_u + 18), @cd_r_용산구
       UNION ALL SELECT (@cd_u + 19), @cd_r_용산구
       UNION ALL SELECT (@cd_u + 20), @cd_r_용산구
       UNION ALL SELECT (@cd_u + 21), @cd_r_용산구
       UNION ALL SELECT (@cd_u + 22), @cd_r_영등포구
       UNION ALL SELECT (@cd_u + 23), @cd_r_영등포구
       UNION ALL SELECT (@cd_u + 24), @cd_r_영등포구
       UNION ALL SELECT (@cd_u + 25), @cd_r_영등포구
       UNION ALL SELECT (@cd_u + 26), @cd_r_영등포구
       UNION ALL SELECT (@cd_u + 27), @cd_r_영등포구
       UNION ALL SELECT (@cd_u + 28), @cd_r_영등포구
       UNION ALL SELECT (@cd_u + 29), @cd_r_마포구
       UNION ALL SELECT (@cd_u + 30), @cd_r_마포구
       UNION ALL SELECT (@cd_u + 31), @cd_r_마포구
       UNION ALL SELECT (@cd_u + 32), @cd_r_마포구
       UNION ALL SELECT (@cd_u + 33), @cd_r_마포구
       UNION ALL SELECT (@cd_u + 34), @cd_r_마포구
       UNION ALL SELECT (@cd_u + 35), @cd_r_마포구
       UNION ALL SELECT (@cd_u + 36), @cd_r_광진구
       UNION ALL SELECT (@cd_u + 37), @cd_r_광진구
       UNION ALL SELECT (@cd_u + 38), @cd_r_광진구
       UNION ALL SELECT (@cd_u + 39), @cd_r_광진구
       UNION ALL SELECT (@cd_u + 40), @cd_r_광진구
       UNION ALL SELECT (@cd_u + 41), @cd_r_광진구
       UNION ALL SELECT (@cd_u + 42), @cd_r_광진구
       UNION ALL SELECT (@cd_u + 43), @cd_r_구로구
       UNION ALL SELECT (@cd_u + 44), @cd_r_구로구
       UNION ALL SELECT (@cd_u + 45), @cd_r_구로구
       UNION ALL SELECT (@cd_u + 46), @cd_r_구로구
       UNION ALL SELECT (@cd_u + 47), @cd_r_구로구
       UNION ALL SELECT (@cd_u + 48), @cd_r_구로구
       UNION ALL SELECT (@cd_u + 49), @cd_r_구로구
       UNION ALL SELECT (@cd_u + 50), @cd_r_동작구
       UNION ALL SELECT (@cd_u + 51), @cd_r_동작구
       UNION ALL SELECT (@cd_u + 52), @cd_r_동작구
       UNION ALL SELECT (@cd_u + 53), @cd_r_동작구
       UNION ALL SELECT (@cd_u + 54), @cd_r_동작구
       UNION ALL SELECT (@cd_u + 55), @cd_r_동작구
       UNION ALL SELECT (@cd_u + 56), @cd_r_동작구
       UNION ALL SELECT (@cd_u + 57), @cd_r_송파구
       UNION ALL SELECT (@cd_u + 58), @cd_r_송파구
       UNION ALL SELECT (@cd_u + 59), @cd_r_송파구
       UNION ALL SELECT (@cd_u + 60), @cd_r_송파구
       UNION ALL SELECT (@cd_u + 61), @cd_r_송파구
       UNION ALL SELECT (@cd_u + 62), @cd_r_송파구
       UNION ALL SELECT (@cd_u + 63), @cd_r_송파구
       UNION ALL SELECT (@cd_u + 64), @cd_r_송파구
       UNION ALL SELECT (@cd_u + 65), @cd_r_송파구
       UNION ALL SELECT (@cd_u + 66), @cd_r_송파구
       UNION ALL SELECT (@cd_u + 67), @cd_r_송파구
       UNION ALL SELECT (@cd_u + 68), @cd_r_송파구
       UNION ALL SELECT (@cd_u + 69), @cd_r_송파구
       UNION ALL SELECT (@cd_u + 70), @cd_r_송파구
       UNION ALL SELECT (@cd_u + 71), @cd_r_송파구
       UNION ALL SELECT (@cd_u + 72), @cd_r_송파구
       UNION ALL SELECT (@cd_u + 73), @cd_r_송파구
       UNION ALL SELECT (@cd_u + 74), @cd_r_송파구
       UNION ALL SELECT (@cd_u + 75), @cd_r_송파구
       UNION ALL SELECT (@cd_u + 76), @cd_r_송파구
) v
 WHERE @cd_ready = 1;

INSERT INTO user_preferred_sport (user_id, sport_id)
SELECT * FROM (
                 SELECT (@cd_u + 1) AS user_id, @cd_s AS sport_id
       UNION ALL SELECT (@cd_u + 2), @cd_s
       UNION ALL SELECT (@cd_u + 3), @cd_s
       UNION ALL SELECT (@cd_u + 4), @cd_s
       UNION ALL SELECT (@cd_u + 5), @cd_s
       UNION ALL SELECT (@cd_u + 6), @cd_s
       UNION ALL SELECT (@cd_u + 7), @cd_s
       UNION ALL SELECT (@cd_u + 8), @cd_s
       UNION ALL SELECT (@cd_u + 9), @cd_s
       UNION ALL SELECT (@cd_u + 10), @cd_s
       UNION ALL SELECT (@cd_u + 11), @cd_s
       UNION ALL SELECT (@cd_u + 12), @cd_s
       UNION ALL SELECT (@cd_u + 13), @cd_s
       UNION ALL SELECT (@cd_u + 14), @cd_s
       UNION ALL SELECT (@cd_u + 15), @cd_s
       UNION ALL SELECT (@cd_u + 16), @cd_s
       UNION ALL SELECT (@cd_u + 17), @cd_s
       UNION ALL SELECT (@cd_u + 18), @cd_s
       UNION ALL SELECT (@cd_u + 19), @cd_s
       UNION ALL SELECT (@cd_u + 20), @cd_s
       UNION ALL SELECT (@cd_u + 21), @cd_s
       UNION ALL SELECT (@cd_u + 22), @cd_s
       UNION ALL SELECT (@cd_u + 23), @cd_s
       UNION ALL SELECT (@cd_u + 24), @cd_s
       UNION ALL SELECT (@cd_u + 25), @cd_s
       UNION ALL SELECT (@cd_u + 26), @cd_s
       UNION ALL SELECT (@cd_u + 27), @cd_s
       UNION ALL SELECT (@cd_u + 28), @cd_s
       UNION ALL SELECT (@cd_u + 29), @cd_s
       UNION ALL SELECT (@cd_u + 30), @cd_s
       UNION ALL SELECT (@cd_u + 31), @cd_s
       UNION ALL SELECT (@cd_u + 32), @cd_s
       UNION ALL SELECT (@cd_u + 33), @cd_s
       UNION ALL SELECT (@cd_u + 34), @cd_s
       UNION ALL SELECT (@cd_u + 35), @cd_s
       UNION ALL SELECT (@cd_u + 36), @cd_s
       UNION ALL SELECT (@cd_u + 37), @cd_s
       UNION ALL SELECT (@cd_u + 38), @cd_s
       UNION ALL SELECT (@cd_u + 39), @cd_s
       UNION ALL SELECT (@cd_u + 40), @cd_s
       UNION ALL SELECT (@cd_u + 41), @cd_s
       UNION ALL SELECT (@cd_u + 42), @cd_s
       UNION ALL SELECT (@cd_u + 43), @cd_s
       UNION ALL SELECT (@cd_u + 44), @cd_s
       UNION ALL SELECT (@cd_u + 45), @cd_s
       UNION ALL SELECT (@cd_u + 46), @cd_s
       UNION ALL SELECT (@cd_u + 47), @cd_s
       UNION ALL SELECT (@cd_u + 48), @cd_s
       UNION ALL SELECT (@cd_u + 49), @cd_s
       UNION ALL SELECT (@cd_u + 50), @cd_s
       UNION ALL SELECT (@cd_u + 51), @cd_s
       UNION ALL SELECT (@cd_u + 52), @cd_s
       UNION ALL SELECT (@cd_u + 53), @cd_s
       UNION ALL SELECT (@cd_u + 54), @cd_s
       UNION ALL SELECT (@cd_u + 55), @cd_s
       UNION ALL SELECT (@cd_u + 56), @cd_s
       UNION ALL SELECT (@cd_u + 57), @cd_s
       UNION ALL SELECT (@cd_u + 58), @cd_s
       UNION ALL SELECT (@cd_u + 59), @cd_s
       UNION ALL SELECT (@cd_u + 60), @cd_s
       UNION ALL SELECT (@cd_u + 61), @cd_s
       UNION ALL SELECT (@cd_u + 62), @cd_s
       UNION ALL SELECT (@cd_u + 63), @cd_s
       UNION ALL SELECT (@cd_u + 64), @cd_s
       UNION ALL SELECT (@cd_u + 65), @cd_s
       UNION ALL SELECT (@cd_u + 66), @cd_s
       UNION ALL SELECT (@cd_u + 67), @cd_s
       UNION ALL SELECT (@cd_u + 68), @cd_s
       UNION ALL SELECT (@cd_u + 69), @cd_s
       UNION ALL SELECT (@cd_u + 70), @cd_s
       UNION ALL SELECT (@cd_u + 71), @cd_s
       UNION ALL SELECT (@cd_u + 72), @cd_s
       UNION ALL SELECT (@cd_u + 73), @cd_s
       UNION ALL SELECT (@cd_u + 74), @cd_s
       UNION ALL SELECT (@cd_u + 75), @cd_s
       UNION ALL SELECT (@cd_u + 76), @cd_s
) v
 WHERE @cd_ready = 1;

-- 종목별 레이팅 = 마지막 레이팅 이력. rated_match_count가 5 이상이면 배치 완료로 팀 등급 평균에 들어간다.
INSERT INTO sport_rating (user_id, sport_id, current_mmr, rated_match_count, updated_at)
SELECT * FROM (
                 SELECT (@cd_u + 1) AS user_id, @cd_s AS sport_id, 1236.45 AS current_mmr, 18 AS rated_match_count, '2026-11-05 21:00:00' AS updated_at
       UNION ALL SELECT (@cd_u + 2), @cd_s, 1202.57, 17, '2026-10-29 21:00:00'
       UNION ALL SELECT (@cd_u + 3), @cd_s, 1198.03, 18, '2026-11-05 21:00:00'
       UNION ALL SELECT (@cd_u + 4), @cd_s, 1186.32, 17, '2026-10-26 10:00:00'
       UNION ALL SELECT (@cd_u + 5), @cd_s, 1200.92, 18, '2026-11-05 21:00:00'
       UNION ALL SELECT (@cd_u + 6), @cd_s, 1191.20, 18, '2026-11-05 21:00:00'
       UNION ALL SELECT (@cd_u + 7), @cd_s, 1067.52, 4, '2026-11-05 21:00:00'
       UNION ALL SELECT (@cd_u + 8), @cd_s, 1047.22, 6, '2026-10-25 09:00:00'
       UNION ALL SELECT (@cd_u + 9), @cd_s, 1064.64, 6, '2026-10-25 09:00:00'
       UNION ALL SELECT (@cd_u + 10), @cd_s, 964.27, 6, '2026-10-25 09:00:00'
       UNION ALL SELECT (@cd_u + 11), @cd_s, 996.71, 5, '2026-10-11 09:00:00'
       UNION ALL SELECT (@cd_u + 12), @cd_s, 1009.94, 6, '2026-10-25 09:00:00'
       UNION ALL SELECT (@cd_u + 13), @cd_s, 964.27, 6, '2026-10-25 09:00:00'
       UNION ALL SELECT (@cd_u + 14), @cd_s, 1065.55, 5, '2026-10-11 09:00:00'
       UNION ALL SELECT (@cd_u + 15), @cd_s, 984.14, 7, '2026-10-24 22:00:00'
       UNION ALL SELECT (@cd_u + 16), @cd_s, 1003.80, 6, '2026-10-24 22:00:00'
       UNION ALL SELECT (@cd_u + 17), @cd_s, 1007.07, 6, '2026-10-15 21:00:00'
       UNION ALL SELECT (@cd_u + 18), @cd_s, 1069.10, 6, '2026-10-15 21:00:00'
       UNION ALL SELECT (@cd_u + 19), @cd_s, 1070.64, 6, '2026-10-24 22:00:00'
       UNION ALL SELECT (@cd_u + 20), @cd_s, 987.31, 7, '2026-10-24 22:00:00'
       UNION ALL SELECT (@cd_u + 21), @cd_s, 939.47, 2, '2026-10-24 22:00:00'
       UNION ALL SELECT (@cd_u + 22), @cd_s, 1026.18, 5, '2026-10-16 22:00:00'
       UNION ALL SELECT (@cd_u + 23), @cd_s, 965.26, 5, '2026-10-16 22:00:00'
       UNION ALL SELECT (@cd_u + 24), @cd_s, 972.73, 6, '2026-10-23 22:00:00'
       UNION ALL SELECT (@cd_u + 25), @cd_s, 970.23, 6, '2026-10-23 22:00:00'
       UNION ALL SELECT (@cd_u + 26), @cd_s, 1032.06, 6, '2026-10-23 22:00:00'
       UNION ALL SELECT (@cd_u + 27), @cd_s, 968.07, 6, '2026-10-23 22:00:00'
       UNION ALL SELECT (@cd_u + 28), @cd_s, 910.12, 6, '2026-10-23 22:00:00'
       UNION ALL SELECT (@cd_u + 29), @cd_s, 910.76, 6, '2026-10-26 10:00:00'
       UNION ALL SELECT (@cd_u + 30), @cd_s, 917.53, 6, '2026-10-26 10:00:00'
       UNION ALL SELECT (@cd_u + 31), @cd_s, 922.75, 6, '2026-10-14 22:00:00'
       UNION ALL SELECT (@cd_u + 32), @cd_s, 913.50, 6, '2026-10-26 10:00:00'
       UNION ALL SELECT (@cd_u + 33), @cd_s, 937.25, 6, '2026-10-14 22:00:00'
       UNION ALL SELECT (@cd_u + 34), @cd_s, 981.09, 6, '2026-10-26 10:00:00'
       UNION ALL SELECT (@cd_u + 35), @cd_s, 954.12, 4, '2026-10-26 10:00:00'
       UNION ALL SELECT (@cd_u + 36), @cd_s, 883.80, 5, '2026-10-29 21:00:00'
       UNION ALL SELECT (@cd_u + 37), @cd_s, 952.15, 6, '2026-11-09 18:00:00'
       UNION ALL SELECT (@cd_u + 38), @cd_s, 884.34, 6, '2026-11-09 18:00:00'
       UNION ALL SELECT (@cd_u + 39), @cd_s, 952.98, 5, '2026-10-29 21:00:00'
       UNION ALL SELECT (@cd_u + 40), @cd_s, 946.94, 6, '2026-11-09 18:00:00'
       UNION ALL SELECT (@cd_u + 41), @cd_s, 926.86, 6, '2026-11-09 18:00:00'
       UNION ALL SELECT (@cd_u + 42), @cd_s, 939.63, 6, '2026-11-09 18:00:00'
       UNION ALL SELECT (@cd_u + 43), @cd_s, 848.67, 6, '2026-10-25 16:00:00'
       UNION ALL SELECT (@cd_u + 44), @cd_s, 919.92, 6, '2026-10-18 16:00:00'
       UNION ALL SELECT (@cd_u + 45), @cd_s, 859.05, 6, '2026-10-25 16:00:00'
       UNION ALL SELECT (@cd_u + 46), @cd_s, 917.11, 6, '2026-10-25 16:00:00'
       UNION ALL SELECT (@cd_u + 47), @cd_s, 918.46, 6, '2026-10-18 16:00:00'
       UNION ALL SELECT (@cd_u + 48), @cd_s, 923.91, 6, '2026-10-25 16:00:00'
       UNION ALL SELECT (@cd_u + 49), @cd_s, 957.64, 4, '2026-10-25 16:00:00'
       UNION ALL SELECT (@cd_u + 50), @cd_s, 895.90, 6, '2026-10-28 22:00:00'
       UNION ALL SELECT (@cd_u + 51), @cd_s, 871.85, 6, '2026-10-28 22:00:00'
       UNION ALL SELECT (@cd_u + 52), @cd_s, 965.55, 5, '2026-10-03 22:00:00'
       UNION ALL SELECT (@cd_u + 53), @cd_s, 919.57, 6, '2026-10-28 22:00:00'
       UNION ALL SELECT (@cd_u + 54), @cd_s, 870.86, 6, '2026-10-28 22:00:00'
       UNION ALL SELECT (@cd_u + 55), @cd_s, 954.48, 6, '2026-10-28 22:00:00'
       UNION ALL SELECT (@cd_u + 56), @cd_s, 952.04, 5, '2026-10-14 22:00:00'
       UNION ALL SELECT (@cd_u + 57), @cd_s, 994.92, 11, '2026-11-05 21:00:00'
       UNION ALL SELECT (@cd_u + 58), @cd_s, 1036.80, 5, '2026-10-16 22:00:00'
       UNION ALL SELECT (@cd_u + 59), @cd_s, 1019.22, 6, '2026-11-05 21:00:00'
       UNION ALL SELECT (@cd_u + 60), @cd_s, 1057.54, 6, '2026-11-05 21:00:00'
       UNION ALL SELECT (@cd_u + 61), @cd_s, 1060.67, 5, '2026-10-16 22:00:00'
       UNION ALL SELECT (@cd_u + 62), @cd_s, 1052.27, 5, '2026-10-25 16:00:00'
       UNION ALL SELECT (@cd_u + 63), @cd_s, 1032.13, 5, '2026-10-25 16:00:00'
       UNION ALL SELECT (@cd_u + 64), @cd_s, 1043.43, 5, '2026-10-16 22:00:00'
       UNION ALL SELECT (@cd_u + 65), @cd_s, 1005.33, 5, '2026-11-05 21:00:00'
       UNION ALL SELECT (@cd_u + 66), @cd_s, 1003.73, 2, '2026-11-05 21:00:00'
       UNION ALL SELECT (@cd_u + 67), @cd_s, 1020.35, 11, '2026-11-09 18:00:00'
       UNION ALL SELECT (@cd_u + 68), @cd_s, 1027.84, 6, '2026-11-09 18:00:00'
       UNION ALL SELECT (@cd_u + 69), @cd_s, 975.45, 5, '2026-10-25 09:00:00'
       UNION ALL SELECT (@cd_u + 70), @cd_s, 1027.07, 6, '2026-11-09 18:00:00'
       UNION ALL SELECT (@cd_u + 71), @cd_s, 1076.18, 5, '2026-10-28 22:00:00'
       UNION ALL SELECT (@cd_u + 72), @cd_s, 1101.41, 6, '2026-11-09 18:00:00'
       UNION ALL SELECT (@cd_u + 73), @cd_s, 1015.40, 5, '2026-10-25 09:00:00'
       UNION ALL SELECT (@cd_u + 74), @cd_s, 1032.14, 5, '2026-10-28 22:00:00'
       UNION ALL SELECT (@cd_u + 75), @cd_s, 971.36, 5, '2026-10-25 09:00:00'
       UNION ALL SELECT (@cd_u + 76), @cd_s, 1024.47, 1, '2026-11-09 18:00:00'
) v
 WHERE @cd_ready = 1;

-- 2. 동호회 (정원이 찬 동호회는 마감)
INSERT INTO club (club_id, sport_id, club_name, intro, max_members, recruit_status, activity_days, activity_start_time, activity_end_time, created_at)
SELECT * FROM (
                 SELECT (@cd_c + 1) AS club_id, @cd_s AS sport_id, '잠실 드림풋살' AS club_name, '잠실종합운동장 풋살경기장에서 수요일·토요일 저녁마다 모이는 팀입니다.\n\n5:5 위주로 쿼터마다 팀을 섞어서 고르게 뛰어요. 꾸준히 운동해 온 분들이 많아 경기 수준이 높은 편이고, 다른 동호회와의 매치도 일주일에 한두 번씩 있습니다.\n\n· 매주 수·토 19:00~21:00 (둘 중 편한 날 참석)\n· 구장비는 참석 인원끼리 나눠 내요\n· 지금은 풋살 경력 2년 이상인 분 위주로 받고 있어요' AS intro, 12 AS max_members, '모집중' AS recruit_status, '3,6' AS activity_days, '19:00:00' AS activity_start_time, '21:00:00' AS activity_end_time, '2026-07-04 10:00:00' AS created_at
       UNION ALL SELECT (@cd_c + 2), @cd_s, '반포 리버사이드 FC', '주말 이른 아침 반포한강공원 축구장에서 공을 찹니다.\n\n· 토·일 07:00~09:00 (둘 중 편한 날 참석)\n· 경기 전 15분 같이 몸 풀기\n· 포지션은 돌아가며 맡아요\n\n아침 공기 마시며 뛰고 나면 하루가 길어져요. 지금은 정원이 다 찼습니다. 빈자리가 생기면 모집을 다시 열게요.', 7, '마감', '6,7', '07:00:00', '09:00:00', '2025-06-14 10:00:00'
       UNION ALL SELECT (@cd_c + 3), @cd_s, '이촌 한강 FS', '이촌한강공원 풋살경기장(옥수역 아래)에서 금요일 저녁에 뜁니다.\n\n· 매주 금요일 20:00~22:00\n· 경기 전에 실력이 비슷하게 팀을 나눠요\n· 용산·성동·강남 어디서든 오기 편해요\n\n한 주를 마무리하는 운동이라 분위기는 편한 편이에요. 한 달에 두 번 이상 나올 수 있는 분이면 좋겠습니다.', 15, '모집중', '5', '20:00:00', '22:00:00', '2026-04-25 10:00:00'
       UNION ALL SELECT (@cd_c + 4), @cd_s, '영등포 레인보우 FS', '영등포공원 풋살경기장에서 목요일 저녁마다 뛰는 혼성 풋살팀입니다.\n\n남녀가 함께 뛰어서 패스 위주로 경기해요. 풋살을 2년 넘게 해 온 멤버가 많아 기본기가 탄탄한 편입니다. 새로 오신 분은 처음 두세 번은 같은 팀에서 뛰며 적응을 돕습니다.\n\n· 매주 목요일 20:00~22:00\n· 분기마다 다른 동호회와 교류전', 15, '모집중', '4', '20:00:00', '22:00:00', '2025-11-03 10:00:00'
       UNION ALL SELECT (@cd_c + 5), @cd_s, '망원 선데이 FC', '일요일 아침 망원유수지 풋살장에서 공 차는 팀입니다.\n\n· 매주 일요일 08:00~10:00\n· 20대 후반~30대 직장인 위주, 중급 이상\n· 경기 끝나고 망원시장 아침 식사는 자유 참여\n\n요즘은 다른 동호회와 친선 매치도 자주 잡고 있어요. 지각·노쇼 없이 꾸준히 나오실 분이면 환영합니다.', 20, '모집중', '7', '08:00:00', '10:00:00', '2025-04-12 10:00:00'
       UNION ALL SELECT (@cd_c + 6), @cd_s, 'BLUE WAVE FS', '뚝섬한강공원에서 일요일 오후마다 뛰는 BLUE WAVE입니다.\n\n빠른 패스와 압박을 좋아하는 팀이라 체력이 중요해요. 정기전 말고도 가끔 평일 번개가 있고, 경기 뒤에는 한강에서 잠깐 쉬었다 갑니다.\n\n· 매주 일요일 16:00~18:00, 뚝섬한강공원 축구장 1\n· 유니폼은 가입 후 맞춰요(선택)', 10, '모집중', '7', '16:00:00', '18:00:00', '2026-08-23 10:00:00'
       UNION ALL SELECT (@cd_c + 7), @cd_s, '구일 FS', '구일역 아래 안양천 풋살경기장에서 토요일 오후에 모입니다.\n\n· 매주 토요일 14:00~16:00\n· 구로·광명·영등포 주민 위주\n· 역 바로 아래라 찾아오기 쉬워요\n\n아직 손발을 맞춰 가는 팀이라 결과보다 즐겁게 뛰는 걸 더 중요하게 생각해요. 처음 오시는 분은 한 번 같이 뛰어 보고 가입하셔도 됩니다.', 12, '모집중', '6', '14:00:00', '16:00:00', '2026-06-20 10:00:00'
       UNION ALL SELECT (@cd_c + 8), @cd_s, 'OFFSIDE FC', '노량진 축구장에서 화·금 저녁에 뛰는 OFFSIDE FC입니다.\n\n동네 친구 몇 명이 시작해서 지금은 매주 두 번 모여요. 경기 영상을 찍어서 다 같이 돌려 보는 걸 좋아하고, 지난 경기에서 아쉬웠던 점을 짧게 이야기하고 시작합니다.\n\n· 화·금 20:00~22:00\n· 정원이 차서 지금은 모집 마감입니다', 7, '마감', '2,5', '20:00:00', '22:00:00', '2026-07-24 10:00:00'
       UNION ALL SELECT (@cd_c + 9), @cd_s, '가락 FS', '가락동·문정동 이웃들이 일요일 아침마다 모여 공을 차는 풋살팀 가락 FS입니다.\n\n잠실종합운동장 제2풋살경기장에서 매주 5:5로 뛰어요. 시작 전 20분은 다 같이 몸을 풀고, 쿼터마다 팀을 다시 나눠 한쪽으로 기울지 않게 맞춥니다. 끝나면 근처에서 가볍게 아침을 먹고 헤어져요.\n\n[이런 분을 찾아요]\n· 한 달에 두 번 이상 일요일 오전을 비울 수 있는 분\n· 풋살 경험 1년 안팎, 중급 정도 실력\n· 이기는 것보다 다치지 않는 걸 먼저 챙기는 분\n\n[활동 안내]\n· 정기 모임: 매주 일요일 10:00~12:00\n· 다른 동호회와 친선 매치: 한 달에 두세 번\n· 구장비는 그날 나온 사람끼리 나눠 냅니다\n\n가입 신청에 주 포지션과 풋살 경력을 적어 주세요. 동호회장이 확인하고 단체 채팅방 링크를 보내 드려요.', 15, '모집중', '7', '10:00:00', '12:00:00', '2026-03-07 10:00:00'
       UNION ALL SELECT (@cd_c + 10), @cd_s, '코사 FS', '일요일 저녁, 한 주를 땀으로 마무리하고 싶은 사람들이 모인 풋살팀 코사 FS입니다.\n\n회사도 사는 곳도 제각각이지만 일요일 저녁만큼은 꼭 모여요. 처음 오신 분도 첫 쿼터부터 바로 뛰고, 그날 온 사람 실력에 맞춰 팀을 나눕니다.\n\n[이런 분을 찾아요]\n· 일요일 저녁 시간을 꾸준히 낼 수 있는 분\n· 초급부터 중급까지, 즐겁게 오래 뛰고 싶은 분\n· 송파·강동 쪽에서 오가기 편한 분\n\n[활동 안내]\n· 정기 모임: 매주 일요일 18:00~20:00, 잠실종합운동장 제2풋살경기장\n· 한 달에 한두 번 다른 동호회와 매치\n· 경기 뒤 저녁 식사는 원하는 분만\n\n가입 신청에 간단한 자기소개를 남겨 주시면 동호회장이 확인하고 연락드릴게요.', 15, '모집중', '7', '18:00:00', '20:00:00', '2026-06-13 10:00:00'
) v
 WHERE @cd_ready = 1;

INSERT INTO club_region (club_id, region_id)
SELECT * FROM (
                 SELECT (@cd_c + 1) AS club_id, @cd_r_송파구 AS region_id
       UNION ALL SELECT (@cd_c + 2), @cd_r_서초구
       UNION ALL SELECT (@cd_c + 3), @cd_r_용산구
       UNION ALL SELECT (@cd_c + 4), @cd_r_영등포구
       UNION ALL SELECT (@cd_c + 5), @cd_r_마포구
       UNION ALL SELECT (@cd_c + 6), @cd_r_광진구
       UNION ALL SELECT (@cd_c + 7), @cd_r_구로구
       UNION ALL SELECT (@cd_c + 8), @cd_r_동작구
       UNION ALL SELECT (@cd_c + 9), @cd_r_송파구
       UNION ALL SELECT (@cd_c + 10), @cd_r_송파구
) v
 WHERE @cd_ready = 1;

-- 동호회장은 창단일에 가입, 동호회원 목록은 동호회장 → 가입 순서라 시연 동호회의 B가 동호회장 바로 아래에 보인다.
INSERT INTO club_member (club_member_id, club_id, user_id, role, joined_at)
SELECT * FROM (
                 SELECT (@cd_m + 1) AS club_member_id, (@cd_c + 1) AS club_id, (@cd_u + 1) AS user_id, '동호회장' AS role, '2026-07-04 10:00:00' AS joined_at
       UNION ALL SELECT (@cd_m + 2), (@cd_c + 1), (@cd_u + 2), '동호회원', '2026-07-05 09:00:00'
       UNION ALL SELECT (@cd_m + 3), (@cd_c + 1), (@cd_u + 3), '동호회원', '2026-07-06 13:30:00'
       UNION ALL SELECT (@cd_m + 4), (@cd_c + 1), (@cd_u + 4), '동호회원', '2026-07-13 10:00:00'
       UNION ALL SELECT (@cd_m + 5), (@cd_c + 1), (@cd_u + 5), '동호회원', '2026-07-13 22:00:00'
       UNION ALL SELECT (@cd_m + 6), (@cd_c + 1), (@cd_u + 6), '동호회원', '2026-07-14 15:00:00'
       UNION ALL SELECT (@cd_m + 7), (@cd_c + 1), (@cd_u + 7), '동호회원', '2026-10-14 14:00:00'
       UNION ALL SELECT (@cd_m + 8), (@cd_c + 2), (@cd_u + 8), '동호회장', '2025-06-14 10:00:00'
       UNION ALL SELECT (@cd_m + 9), (@cd_c + 2), (@cd_u + 9), '동호회원', '2025-11-03 19:00:00'
       UNION ALL SELECT (@cd_m + 10), (@cd_c + 2), (@cd_u + 10), '동호회원', '2026-03-19 20:00:00'
       UNION ALL SELECT (@cd_m + 11), (@cd_c + 2), (@cd_u + 11), '동호회원', '2026-05-19 09:00:00'
       UNION ALL SELECT (@cd_m + 12), (@cd_c + 2), (@cd_u + 12), '동호회원', '2026-06-20 10:30:00'
       UNION ALL SELECT (@cd_m + 13), (@cd_c + 2), (@cd_u + 13), '동호회원', '2026-07-17 19:30:00'
       UNION ALL SELECT (@cd_m + 14), (@cd_c + 2), (@cd_u + 14), '동호회원', '2026-07-28 11:00:00'
       UNION ALL SELECT (@cd_m + 15), (@cd_c + 3), (@cd_u + 15), '동호회장', '2026-04-25 10:00:00'
       UNION ALL SELECT (@cd_m + 16), (@cd_c + 3), (@cd_u + 16), '동호회원', '2026-05-02 13:30:00'
       UNION ALL SELECT (@cd_m + 17), (@cd_c + 3), (@cd_u + 17), '동호회원', '2026-05-19 13:00:00'
       UNION ALL SELECT (@cd_m + 18), (@cd_c + 3), (@cd_u + 18), '동호회원', '2026-06-05 12:30:00'
       UNION ALL SELECT (@cd_m + 19), (@cd_c + 3), (@cd_u + 19), '동호회원', '2026-06-25 09:30:00'
       UNION ALL SELECT (@cd_m + 20), (@cd_c + 3), (@cd_u + 20), '동호회원', '2026-07-09 16:30:00'
       UNION ALL SELECT (@cd_m + 21), (@cd_c + 3), (@cd_u + 21), '동호회원', '2026-10-11 13:00:00'
       UNION ALL SELECT (@cd_m + 22), (@cd_c + 4), (@cd_u + 22), '동호회장', '2025-11-03 10:00:00'
       UNION ALL SELECT (@cd_m + 23), (@cd_c + 4), (@cd_u + 23), '동호회원', '2026-01-01 13:30:00'
       UNION ALL SELECT (@cd_m + 24), (@cd_c + 4), (@cd_u + 24), '동호회원', '2026-02-27 12:00:00'
       UNION ALL SELECT (@cd_m + 25), (@cd_c + 4), (@cd_u + 25), '동호회원', '2026-04-14 09:30:00'
       UNION ALL SELECT (@cd_m + 26), (@cd_c + 4), (@cd_u + 26), '동호회원', '2026-04-26 17:30:00'
       UNION ALL SELECT (@cd_m + 27), (@cd_c + 4), (@cd_u + 27), '동호회원', '2026-05-21 17:00:00'
       UNION ALL SELECT (@cd_m + 28), (@cd_c + 4), (@cd_u + 28), '동호회원', '2026-06-12 10:30:00'
       UNION ALL SELECT (@cd_m + 29), (@cd_c + 5), (@cd_u + 29), '동호회장', '2025-04-12 10:00:00'
       UNION ALL SELECT (@cd_m + 30), (@cd_c + 5), (@cd_u + 30), '동호회원', '2025-05-22 12:00:00'
       UNION ALL SELECT (@cd_m + 31), (@cd_c + 5), (@cd_u + 31), '동호회원', '2025-06-23 10:00:00'
       UNION ALL SELECT (@cd_m + 32), (@cd_c + 5), (@cd_u + 32), '동호회원', '2025-11-17 15:00:00'
       UNION ALL SELECT (@cd_m + 33), (@cd_c + 5), (@cd_u + 33), '동호회원', '2026-02-10 18:00:00'
       UNION ALL SELECT (@cd_m + 34), (@cd_c + 5), (@cd_u + 34), '동호회원', '2026-04-21 10:30:00'
       UNION ALL SELECT (@cd_m + 35), (@cd_c + 5), (@cd_u + 35), '동호회원', '2026-09-18 12:00:00'
       UNION ALL SELECT (@cd_m + 36), (@cd_c + 6), (@cd_u + 36), '동호회장', '2026-08-23 10:00:00'
       UNION ALL SELECT (@cd_m + 37), (@cd_c + 6), (@cd_u + 37), '동호회원', '2026-08-24 17:00:00'
       UNION ALL SELECT (@cd_m + 38), (@cd_c + 6), (@cd_u + 38), '동호회원', '2026-08-25 16:00:00'
       UNION ALL SELECT (@cd_m + 39), (@cd_c + 6), (@cd_u + 39), '동호회원', '2026-08-30 10:30:00'
       UNION ALL SELECT (@cd_m + 40), (@cd_c + 6), (@cd_u + 40), '동호회원', '2026-09-03 12:30:00'
       UNION ALL SELECT (@cd_m + 41), (@cd_c + 6), (@cd_u + 41), '동호회원', '2026-09-08 16:30:00'
       UNION ALL SELECT (@cd_m + 42), (@cd_c + 6), (@cd_u + 42), '동호회원', '2026-09-09 11:00:00'
       UNION ALL SELECT (@cd_m + 43), (@cd_c + 7), (@cd_u + 43), '동호회장', '2026-06-20 10:00:00'
       UNION ALL SELECT (@cd_m + 44), (@cd_c + 7), (@cd_u + 44), '동호회원', '2026-07-09 17:30:00'
       UNION ALL SELECT (@cd_m + 45), (@cd_c + 7), (@cd_u + 45), '동호회원', '2026-07-16 13:30:00'
       UNION ALL SELECT (@cd_m + 46), (@cd_c + 7), (@cd_u + 46), '동호회원', '2026-07-18 10:00:00'
       UNION ALL SELECT (@cd_m + 47), (@cd_c + 7), (@cd_u + 47), '동호회원', '2026-07-22 18:30:00'
       UNION ALL SELECT (@cd_m + 48), (@cd_c + 7), (@cd_u + 48), '동호회원', '2026-07-26 10:30:00'
       UNION ALL SELECT (@cd_m + 49), (@cd_c + 7), (@cd_u + 49), '동호회원', '2026-09-12 10:30:00'
       UNION ALL SELECT (@cd_m + 50), (@cd_c + 8), (@cd_u + 50), '동호회장', '2026-07-24 10:00:00'
       UNION ALL SELECT (@cd_m + 51), (@cd_c + 8), (@cd_u + 51), '동호회원', '2026-07-25 16:30:00'
       UNION ALL SELECT (@cd_m + 52), (@cd_c + 8), (@cd_u + 52), '동호회원', '2026-07-27 10:00:00'
       UNION ALL SELECT (@cd_m + 53), (@cd_c + 8), (@cd_u + 53), '동호회원', '2026-07-29 15:30:00'
       UNION ALL SELECT (@cd_m + 54), (@cd_c + 8), (@cd_u + 54), '동호회원', '2026-07-30 11:30:00'
       UNION ALL SELECT (@cd_m + 55), (@cd_c + 8), (@cd_u + 55), '동호회원', '2026-08-05 11:30:00'
       UNION ALL SELECT (@cd_m + 56), (@cd_c + 8), (@cd_u + 56), '동호회원', '2026-08-06 09:00:00'
       UNION ALL SELECT (@cd_m + 57), (@cd_c + 9), (@cd_u + 57), '동호회장', '2026-03-07 10:00:00'
       UNION ALL SELECT (@cd_m + 58), (@cd_c + 9), (@cd_u + 58), '동호회원', '2026-03-27 12:00:00'
       UNION ALL SELECT (@cd_m + 59), (@cd_c + 9), (@cd_u + 59), '동호회원', '2026-04-12 20:00:00'
       UNION ALL SELECT (@cd_m + 60), (@cd_c + 9), (@cd_u + 60), '동호회원', '2026-04-25 20:00:00'
       UNION ALL SELECT (@cd_m + 61), (@cd_c + 9), (@cd_u + 61), '동호회원', '2026-05-15 10:00:00'
       UNION ALL SELECT (@cd_m + 62), (@cd_c + 9), (@cd_u + 62), '동호회원', '2026-05-21 22:00:00'
       UNION ALL SELECT (@cd_m + 63), (@cd_c + 9), (@cd_u + 63), '동호회원', '2026-06-07 22:00:00'
       UNION ALL SELECT (@cd_m + 64), (@cd_c + 9), (@cd_u + 64), '동호회원', '2026-06-23 14:30:00'
       UNION ALL SELECT (@cd_m + 65), (@cd_c + 9), (@cd_u + 65), '동호회원', '2026-07-24 12:00:00'
       UNION ALL SELECT (@cd_m + 66), (@cd_c + 9), (@cd_u + 66), '동호회원', '2026-10-19 11:00:00'
       UNION ALL SELECT (@cd_m + 67), (@cd_c + 10), (@cd_u + 67), '동호회장', '2026-06-13 10:00:00'
       UNION ALL SELECT (@cd_m + 68), (@cd_c + 10), (@cd_u + 68), '동호회원', '2026-06-19 11:00:00'
       UNION ALL SELECT (@cd_m + 69), (@cd_c + 10), (@cd_u + 69), '동호회원', '2026-06-22 10:00:00'
       UNION ALL SELECT (@cd_m + 70), (@cd_c + 10), (@cd_u + 70), '동호회원', '2026-06-27 20:30:00'
       UNION ALL SELECT (@cd_m + 71), (@cd_c + 10), (@cd_u + 71), '동호회원', '2026-07-14 14:00:00'
       UNION ALL SELECT (@cd_m + 72), (@cd_c + 10), (@cd_u + 72), '동호회원', '2026-07-15 21:00:00'
       UNION ALL SELECT (@cd_m + 73), (@cd_c + 10), (@cd_u + 73), '동호회원', '2026-07-18 18:00:00'
       UNION ALL SELECT (@cd_m + 74), (@cd_c + 10), (@cd_u + 74), '동호회원', '2026-07-20 12:30:00'
       UNION ALL SELECT (@cd_m + 75), (@cd_c + 10), (@cd_u + 75), '동호회원', '2026-07-29 14:30:00'
       UNION ALL SELECT (@cd_m + 76), (@cd_c + 10), (@cd_u + 76), '동호회원', '2026-10-30 16:30:00'
) v
 WHERE @cd_ready = 1;

-- 3. 경기: 지난 동호회 경기(종료, 결과 확정, 레이팅 반영) + 11/29 매칭방(모집 중). 팀장은 참가자를 넣은 뒤 지정한다.
INSERT INTO social_match (match_id, facility_id, sport_id, created_by, title, start_at, end_at, min_participants, max_participants, team_a_capacity, team_b_capacity, fee_per_person, required_skill, status, photo_url, assignment_method, assigned_by, assignment_confirmed_at, result_status, final_result, result_confirmed_at, rating_status, rating_processed_at)
SELECT * FROM (
                 SELECT (@cd_sm + 1) AS match_id, @cd_f7 AS facility_id, @cd_s AS sport_id, @cd_f7_mgr AS created_by, '안양천 풋살경기장 토요일 오후 5:5' AS title, '2026-08-01 14:00:00' AS start_at, '2026-08-01 16:00:00' AS end_at, 10 AS min_participants, 10 AS max_participants, 5 AS team_a_capacity, 5 AS team_b_capacity, 0 AS fee_per_person, '중급' AS required_skill, '종료' AS status, '/images/matches/soccer.jpg' AS photo_url, 'MANUAL' AS assignment_method, @cd_f7_mgr AS assigned_by, '2026-08-01 13:00:00' AS assignment_confirmed_at, 'CONFIRMED' AS result_status, 'B_WIN' AS final_result, '2026-08-01 18:01:00' AS result_confirmed_at, 'APPLIED' AS rating_status, '2026-08-02 16:00:00' AS rating_processed_at
       UNION ALL SELECT (@cd_sm + 2), @cd_f2, @cd_s, @cd_f2_mgr, '반포한강공원 축구장 토요일 오전 5:5', '2026-08-01 07:00:00', '2026-08-01 09:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f2_mgr, '2026-08-01 06:00:00', 'CONFIRMED', 'A_WIN', '2026-08-01 11:05:00', 'APPLIED', '2026-08-02 09:00:00'
       UNION ALL SELECT (@cd_sm + 3), @cd_f9, @cd_s, @cd_f9_mgr, '잠실 제2풋살경기장 일요일 저녁 5:5', '2026-08-02 18:00:00', '2026-08-02 20:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f9_mgr, '2026-08-02 17:00:00', 'CONFIRMED', 'A_WIN', '2026-08-02 21:27:00', 'APPLIED', '2026-08-03 20:00:00'
       UNION ALL SELECT (@cd_sm + 4), @cd_f1, @cd_s, @cd_f1_mgr, '잠실 풋살경기장 수요일 저녁 5:5', '2026-08-05 19:00:00', '2026-08-05 21:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f1_mgr, '2026-08-05 18:00:00', 'CONFIRMED', 'A_WIN', '2026-08-05 22:40:00', 'APPLIED', '2026-08-06 21:00:00'
       UNION ALL SELECT (@cd_sm + 5), @cd_f7, @cd_s, @cd_f7_mgr, '안양천 풋살경기장 토요일 오후 5:5', '2026-08-08 14:00:00', '2026-08-08 16:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f7_mgr, '2026-08-08 13:00:00', 'CONFIRMED', 'B_WIN', '2026-08-08 18:01:00', 'APPLIED', '2026-08-09 16:00:00'
       UNION ALL SELECT (@cd_sm + 6), @cd_f2, @cd_s, @cd_f2_mgr, '반포한강공원 축구장 일요일 오전 5:5', '2026-08-09 07:00:00', '2026-08-09 09:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f2_mgr, '2026-08-09 06:00:00', 'CONFIRMED', 'A_WIN', '2026-08-09 10:35:00', 'APPLIED', '2026-08-10 09:00:00'
       UNION ALL SELECT (@cd_sm + 7), @cd_f1, @cd_s, @cd_f1_mgr, '잠실 풋살경기장 수요일 저녁 5:5', '2026-08-12 19:00:00', '2026-08-12 21:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f1_mgr, '2026-08-12 18:00:00', 'CONFIRMED', 'A_WIN', '2026-08-12 22:24:00', 'APPLIED', '2026-08-13 21:00:00'
       UNION ALL SELECT (@cd_sm + 8), @cd_f8, @cd_s, @cd_f8_mgr, '노량진 축구장 금요일 저녁 5:5', '2026-08-14 20:00:00', '2026-08-14 22:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f8_mgr, '2026-08-14 19:00:00', 'CONFIRMED', 'B_WIN', '2026-08-14 22:45:00', 'APPLIED', '2026-08-15 22:00:00'
       UNION ALL SELECT (@cd_sm + 9), @cd_f9, @cd_s, @cd_f9_mgr, '잠실 제2풋살경기장 일요일 저녁 5:5', '2026-08-16 18:00:00', '2026-08-16 20:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f9_mgr, '2026-08-16 17:00:00', 'CONFIRMED', 'B_WIN', '2026-08-16 21:20:00', 'APPLIED', '2026-08-17 20:00:00'
       UNION ALL SELECT (@cd_sm + 10), @cd_f8, @cd_s, @cd_f8_mgr, '노량진 축구장 금요일 저녁 5:5', '2026-08-21 20:00:00', '2026-08-21 22:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f8_mgr, '2026-08-21 19:00:00', 'CONFIRMED', 'DRAW', '2026-08-22 00:51:00', 'APPLIED', '2026-08-22 22:00:00'
       UNION ALL SELECT (@cd_sm + 11), @cd_f7, @cd_s, @cd_f7_mgr, '안양천 풋살경기장 토요일 오후 5:5', '2026-08-22 14:00:00', '2026-08-22 16:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f7_mgr, '2026-08-22 13:00:00', 'CONFIRMED', 'B_WIN', '2026-08-22 16:54:00', 'APPLIED', '2026-08-23 16:00:00'
       UNION ALL SELECT (@cd_sm + 12), @cd_f5, @cd_s, @cd_f5_mgr, '망원유수지 풋살장 일요일 오전 5:5', '2026-08-23 08:00:00', '2026-08-23 10:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f5_mgr, '2026-08-23 07:00:00', 'CONFIRMED', 'B_WIN', '2026-08-23 11:52:00', 'APPLIED', '2026-08-24 10:00:00'
       UNION ALL SELECT (@cd_sm + 13), @cd_f1, @cd_s, @cd_f1_mgr, '잠실 풋살경기장 수요일 저녁 5:5', '2026-08-26 19:00:00', '2026-08-26 21:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f1_mgr, '2026-08-26 18:00:00', 'CONFIRMED', 'A_WIN', '2026-08-26 22:18:00', 'APPLIED', '2026-08-27 21:00:00'
       UNION ALL SELECT (@cd_sm + 14), @cd_f3, @cd_s, @cd_f3_mgr, '이촌한강공원 풋살장 금요일 저녁 5:5', '2026-08-28 20:00:00', '2026-08-28 22:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f3_mgr, '2026-08-28 19:00:00', 'CONFIRMED', 'A_WIN', '2026-08-28 23:10:00', 'APPLIED', '2026-08-29 22:00:00'
       UNION ALL SELECT (@cd_sm + 15), @cd_f9, @cd_s, @cd_f9_mgr, '잠실 제2풋살경기장 일요일 저녁 5:5', '2026-08-30 18:00:00', '2026-08-30 20:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f9_mgr, '2026-08-30 17:00:00', 'CONFIRMED', 'DRAW', '2026-08-30 21:09:00', 'APPLIED', '2026-08-31 20:00:00'
       UNION ALL SELECT (@cd_sm + 16), @cd_f8, @cd_s, @cd_f8_mgr, '노량진 축구장 화요일 저녁 5:5', '2026-09-01 20:00:00', '2026-09-01 22:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f8_mgr, '2026-09-01 19:00:00', 'CONFIRMED', 'A_WIN', '2026-09-02 00:42:00', 'APPLIED', '2026-09-02 22:00:00'
       UNION ALL SELECT (@cd_sm + 17), @cd_f4, @cd_s, @cd_f4_mgr, '영등포공원 풋살장 목요일 저녁 5:5', '2026-09-03 20:00:00', '2026-09-03 22:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f4_mgr, '2026-09-03 19:00:00', 'CONFIRMED', 'A_WIN', '2026-09-04 00:01:00', 'APPLIED', '2026-09-04 22:00:00'
       UNION ALL SELECT (@cd_sm + 18), @cd_f3, @cd_s, @cd_f3_mgr, '이촌한강공원 풋살장 금요일 저녁 5:5', '2026-09-04 20:00:00', '2026-09-04 22:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f3_mgr, '2026-09-04 19:00:00', 'CONFIRMED', 'A_WIN', '2026-09-04 22:45:00', 'APPLIED', '2026-09-05 22:00:00'
       UNION ALL SELECT (@cd_sm + 19), @cd_f2, @cd_s, @cd_f2_mgr, '반포한강공원 축구장 토요일 오전 5:5', '2026-09-05 07:00:00', '2026-09-05 09:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f2_mgr, '2026-09-05 06:00:00', 'CONFIRMED', 'B_WIN', '2026-09-05 10:48:00', 'APPLIED', '2026-09-06 09:00:00'
       UNION ALL SELECT (@cd_sm + 20), @cd_f5, @cd_s, @cd_f5_mgr, '망원유수지 풋살장 일요일 오전 5:5', '2026-09-06 08:00:00', '2026-09-06 10:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f5_mgr, '2026-09-06 07:00:00', 'CONFIRMED', 'A_WIN', '2026-09-06 11:29:00', 'APPLIED', '2026-09-07 10:00:00'
       UNION ALL SELECT (@cd_sm + 21), @cd_f1, @cd_s, @cd_f1_mgr, '잠실 풋살경기장 수요일 저녁 5:5', '2026-09-09 19:00:00', '2026-09-09 21:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f1_mgr, '2026-09-09 18:00:00', 'CONFIRMED', 'A_WIN', '2026-09-09 22:23:00', 'APPLIED', '2026-09-10 21:00:00'
       UNION ALL SELECT (@cd_sm + 22), @cd_f8, @cd_s, @cd_f8_mgr, '노량진 축구장 금요일 저녁 5:5', '2026-09-11 20:00:00', '2026-09-11 22:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f8_mgr, '2026-09-11 19:00:00', 'CONFIRMED', 'B_WIN', '2026-09-11 23:08:00', 'APPLIED', '2026-09-12 22:00:00'
       UNION ALL SELECT (@cd_sm + 23), @cd_f6, @cd_s, @cd_f6_mgr, '뚝섬한강공원 축구장 일요일 오후 5:5', '2026-09-13 16:00:00', '2026-09-13 18:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f6_mgr, '2026-09-13 15:00:00', 'CONFIRMED', 'B_WIN', '2026-09-13 18:47:00', 'APPLIED', '2026-09-14 18:00:00'
       UNION ALL SELECT (@cd_sm + 24), @cd_f4, @cd_s, @cd_f4_mgr, '영등포공원 풋살장 목요일 저녁 5:5', '2026-09-17 20:00:00', '2026-09-17 22:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f4_mgr, '2026-09-17 19:00:00', 'CONFIRMED', 'B_WIN', '2026-09-18 00:29:00', 'APPLIED', '2026-09-18 22:00:00'
       UNION ALL SELECT (@cd_sm + 25), @cd_f3, @cd_s, @cd_f3_mgr, '이촌한강공원 풋살장 금요일 저녁 5:5', '2026-09-18 20:00:00', '2026-09-18 22:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f3_mgr, '2026-09-18 19:00:00', 'CONFIRMED', 'B_WIN', '2026-09-19 00:22:00', 'APPLIED', '2026-09-19 22:00:00'
       UNION ALL SELECT (@cd_sm + 26), @cd_f2, @cd_s, @cd_f2_mgr, '반포한강공원 축구장 일요일 오전 5:5', '2026-09-20 07:00:00', '2026-09-20 09:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f2_mgr, '2026-09-20 06:00:00', 'CONFIRMED', 'A_WIN', '2026-09-20 10:10:00', 'APPLIED', '2026-09-21 09:00:00'
       UNION ALL SELECT (@cd_sm + 27), @cd_f8, @cd_s, @cd_f8_mgr, '노량진 축구장 화요일 저녁 5:5', '2026-09-22 20:00:00', '2026-09-22 22:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f8_mgr, '2026-09-22 19:00:00', 'CONFIRMED', 'B_WIN', '2026-09-23 00:51:00', 'APPLIED', '2026-09-23 22:00:00'
       UNION ALL SELECT (@cd_sm + 28), @cd_f2, @cd_s, @cd_f2_mgr, '반포한강공원 축구장 토요일 오전 5:5', '2026-09-26 07:00:00', '2026-09-26 09:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f2_mgr, '2026-09-26 06:00:00', 'CONFIRMED', 'B_WIN', '2026-09-26 11:57:00', 'APPLIED', '2026-09-27 09:00:00'
       UNION ALL SELECT (@cd_sm + 29), @cd_f7, @cd_s, @cd_f7_mgr, '안양천 풋살경기장 토요일 오후 5:5', '2026-09-26 14:00:00', '2026-09-26 16:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f7_mgr, '2026-09-26 13:00:00', 'CONFIRMED', 'B_WIN', '2026-09-26 17:59:00', 'APPLIED', '2026-09-27 16:00:00'
       UNION ALL SELECT (@cd_sm + 30), @cd_f1, @cd_s, @cd_f1_mgr, '잠실 풋살경기장 토요일 저녁 5:5', '2026-09-26 19:00:00', '2026-09-26 21:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f1_mgr, '2026-09-26 18:00:00', 'CONFIRMED', 'A_WIN', '2026-09-26 22:00:00', 'APPLIED', '2026-09-27 21:00:00'
       UNION ALL SELECT (@cd_sm + 31), @cd_f4, @cd_s, @cd_f4_mgr, '영등포공원 풋살장 목요일 저녁 5:5', '2026-10-01 20:00:00', '2026-10-01 22:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f4_mgr, '2026-10-01 19:00:00', 'CONFIRMED', 'A_WIN', '2026-10-01 23:18:00', 'APPLIED', '2026-10-02 22:00:00'
       UNION ALL SELECT (@cd_sm + 32), @cd_f8, @cd_s, @cd_f8_mgr, '노량진 축구장 금요일 저녁 5:5', '2026-10-02 20:00:00', '2026-10-02 22:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f8_mgr, '2026-10-02 19:00:00', 'CONFIRMED', 'B_WIN', '2026-10-02 23:07:00', 'APPLIED', '2026-10-03 22:00:00'
       UNION ALL SELECT (@cd_sm + 33), @cd_f5, @cd_s, @cd_f5_mgr, '망원유수지 풋살장 일요일 오전 5:5', '2026-10-04 08:00:00', '2026-10-04 10:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f5_mgr, '2026-10-04 07:00:00', 'CONFIRMED', 'B_WIN', '2026-10-04 11:53:00', 'APPLIED', '2026-10-05 10:00:00'
       UNION ALL SELECT (@cd_sm + 34), @cd_f1, @cd_s, @cd_f1_mgr, '잠실 풋살경기장 수요일 저녁 5:5', '2026-10-07 19:00:00', '2026-10-07 21:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f1_mgr, '2026-10-07 18:00:00', 'CONFIRMED', 'B_WIN', '2026-10-07 23:58:00', 'APPLIED', '2026-10-08 21:00:00'
       UNION ALL SELECT (@cd_sm + 35), @cd_f3, @cd_s, @cd_f3_mgr, '이촌한강공원 풋살장 금요일 저녁 5:5', '2026-10-09 20:00:00', '2026-10-09 22:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f3_mgr, '2026-10-09 19:00:00', 'CONFIRMED', 'A_WIN', '2026-10-09 23:42:00', 'APPLIED', '2026-10-10 22:00:00'
       UNION ALL SELECT (@cd_sm + 36), @cd_f2, @cd_s, @cd_f2_mgr, '반포한강공원 축구장 토요일 오전 5:5', '2026-10-10 07:00:00', '2026-10-10 09:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f2_mgr, '2026-10-10 06:00:00', 'CONFIRMED', 'B_WIN', '2026-10-10 10:25:00', 'APPLIED', '2026-10-11 09:00:00'
       UNION ALL SELECT (@cd_sm + 37), @cd_f7, @cd_s, @cd_f7_mgr, '안양천 풋살경기장 토요일 오후 5:5', '2026-10-10 14:00:00', '2026-10-10 16:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f7_mgr, '2026-10-10 13:00:00', 'CONFIRMED', 'A_WIN', '2026-10-10 17:47:00', 'APPLIED', '2026-10-11 16:00:00'
       UNION ALL SELECT (@cd_sm + 38), @cd_f8, @cd_s, @cd_f8_mgr, '노량진 축구장 화요일 저녁 5:5', '2026-10-13 20:00:00', '2026-10-13 22:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f8_mgr, '2026-10-13 19:00:00', 'CONFIRMED', 'B_WIN', '2026-10-14 00:58:00', 'APPLIED', '2026-10-14 22:00:00'
       UNION ALL SELECT (@cd_sm + 39), @cd_f1, @cd_s, @cd_f1_mgr, '잠실 풋살경기장 수요일 저녁 5:5', '2026-10-14 19:00:00', '2026-10-14 21:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f1_mgr, '2026-10-14 18:00:00', 'CONFIRMED', 'A_WIN', '2026-10-14 23:59:00', 'APPLIED', '2026-10-15 21:00:00'
       UNION ALL SELECT (@cd_sm + 40), @cd_f4, @cd_s, @cd_f4_mgr, '영등포공원 풋살장 목요일 저녁 5:5', '2026-10-15 20:00:00', '2026-10-15 22:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f4_mgr, '2026-10-15 19:00:00', 'CONFIRMED', 'B_WIN', '2026-10-16 00:01:00', 'APPLIED', '2026-10-16 22:00:00'
       UNION ALL SELECT (@cd_sm + 41), @cd_f7, @cd_s, @cd_f7_mgr, '안양천 풋살경기장 토요일 오후 5:5', '2026-10-17 14:00:00', '2026-10-17 16:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f7_mgr, '2026-10-17 13:00:00', 'CONFIRMED', 'B_WIN', '2026-10-17 16:46:00', 'APPLIED', '2026-10-18 16:00:00'
       UNION ALL SELECT (@cd_sm + 42), @cd_f4, @cd_s, @cd_f4_mgr, '영등포공원 풋살장 목요일 저녁 5:5', '2026-10-22 20:00:00', '2026-10-22 22:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f4_mgr, '2026-10-22 19:00:00', 'CONFIRMED', 'B_WIN', '2026-10-22 23:54:00', 'APPLIED', '2026-10-23 22:00:00'
       UNION ALL SELECT (@cd_sm + 43), @cd_f3, @cd_s, @cd_f3_mgr, '이촌한강공원 풋살장 금요일 저녁 5:5', '2026-10-23 20:00:00', '2026-10-23 22:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f3_mgr, '2026-10-23 19:00:00', 'CONFIRMED', 'B_WIN', '2026-10-23 23:03:00', 'APPLIED', '2026-10-24 22:00:00'
       UNION ALL SELECT (@cd_sm + 44), @cd_f7, @cd_s, @cd_f7_mgr, '안양천 풋살경기장 토요일 오후 5:5', '2026-10-24 14:00:00', '2026-10-24 16:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f7_mgr, '2026-10-24 13:00:00', 'CONFIRMED', 'B_WIN', '2026-10-24 16:45:00', 'APPLIED', '2026-10-25 16:00:00'
       UNION ALL SELECT (@cd_sm + 45), @cd_f2, @cd_s, @cd_f2_mgr, '반포한강공원 축구장 토요일 오전 5:5', '2026-10-24 07:00:00', '2026-10-24 09:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f2_mgr, '2026-10-24 06:00:00', 'CONFIRMED', 'A_WIN', '2026-10-24 11:09:00', 'APPLIED', '2026-10-25 09:00:00'
       UNION ALL SELECT (@cd_sm + 46), @cd_f5, @cd_s, @cd_f5_mgr, '망원유수지 풋살장 일요일 오전 5:5', '2026-10-25 08:00:00', '2026-10-25 10:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f5_mgr, '2026-10-25 07:00:00', 'CONFIRMED', 'B_WIN', '2026-10-25 11:47:00', 'APPLIED', '2026-10-26 10:00:00'
       UNION ALL SELECT (@cd_sm + 47), @cd_f8, @cd_s, @cd_f8_mgr, '노량진 축구장 화요일 저녁 5:5', '2026-10-27 20:00:00', '2026-10-27 22:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f8_mgr, '2026-10-27 19:00:00', 'CONFIRMED', 'B_WIN', '2026-10-28 00:51:00', 'APPLIED', '2026-10-28 22:00:00'
       UNION ALL SELECT (@cd_sm + 48), @cd_f1, @cd_s, @cd_f1_mgr, '잠실 풋살경기장 수요일 저녁 5:5', '2026-10-28 19:00:00', '2026-10-28 21:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f1_mgr, '2026-10-28 18:00:00', 'CONFIRMED', 'A_WIN', '2026-10-28 23:24:00', 'APPLIED', '2026-10-29 21:00:00'
       UNION ALL SELECT (@cd_sm + 49), @cd_f1, @cd_s, @cd_f1_mgr, '잠실 풋살경기장 수요일 저녁 5:5', '2026-11-04 19:00:00', '2026-11-04 21:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f1_mgr, '2026-11-04 18:00:00', 'CONFIRMED', 'A_WIN', '2026-11-04 21:50:00', 'APPLIED', '2026-11-05 21:00:00'
       UNION ALL SELECT (@cd_sm + 50), @cd_f6, @cd_s, @cd_f6_mgr, '뚝섬한강공원 축구장 일요일 오후 5:5', '2026-11-08 16:00:00', '2026-11-08 18:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cd_f6_mgr, '2026-11-08 15:00:00', 'CONFIRMED', 'B_WIN', '2026-11-08 20:57:00', 'APPLIED', '2026-11-09 18:00:00'
       UNION ALL SELECT (@cd_sm + 51), @cd_f9, @cd_s, @cd_f9_mgr, '잠실 제2풋살경기장 일요일 오전 5:5', '2026-11-29 10:00:00', '2026-11-29 12:00:00', 10, 10, 5, 5, 0, '중급', '모집 중', '/images/matches/soccer.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
       UNION ALL SELECT (@cd_sm + 52), @cd_f9, @cd_s, @cd_f9_mgr, '잠실 제2풋살경기장 일요일 저녁 5:5', '2026-11-29 18:00:00', '2026-11-29 20:00:00', 10, 10, 5, 5, 0, '중급', '모집 중', '/images/matches/soccer.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
) v
 WHERE @cd_ready = 1;

-- 4. 팀 신청: 동호회마다 대표 1명 + 팀원 4명. 지난 경기는 시설 담당자가 확정, 11/29는 대기. 행을 넣은 뒤 묶음 번호를 붙인다.
INSERT INTO match_application (application_id, match_id, applicant_user_id, club_id, application_type, status, receipt_no, created_at, confirmed_at)
SELECT * FROM (
                 SELECT (@cd_a + 1) AS application_id, (@cd_sm + 1) AS match_id, (@cd_u + 43) AS applicant_user_id, (@cd_c + 7) AS club_id, '동호회' AS application_type, '확정' AS status, 'RCPT-20260728-62YJ339Y' AS receipt_no, '2026-07-28 09:00:00' AS created_at, '2026-07-28 10:12:00' AS confirmed_at
       UNION ALL SELECT (@cd_a + 2), (@cd_sm + 1), (@cd_u + 44), (@cd_c + 7), '동호회', '확정', 'RCPT-20260728-L2476YH3', '2026-07-28 09:00:00', '2026-07-28 10:42:00'
       UNION ALL SELECT (@cd_a + 3), (@cd_sm + 1), (@cd_u + 45), (@cd_c + 7), '동호회', '확정', 'RCPT-20260728-CUHKUS58', '2026-07-28 09:00:00', '2026-07-28 11:25:00'
       UNION ALL SELECT (@cd_a + 4), (@cd_sm + 1), (@cd_u + 46), (@cd_c + 7), '동호회', '확정', 'RCPT-20260728-RJS27FB4', '2026-07-28 09:00:00', '2026-07-28 10:52:00'
       UNION ALL SELECT (@cd_a + 5), (@cd_sm + 1), (@cd_u + 47), (@cd_c + 7), '동호회', '확정', 'RCPT-20260728-ACJ3M3K3', '2026-07-28 09:00:00', '2026-07-28 11:07:00'
       UNION ALL SELECT (@cd_a + 6), (@cd_sm + 1), (@cd_u + 1), (@cd_c + 1), '동호회', '확정', 'RCPT-20260729-K7RXTSJG', '2026-07-29 05:00:00', '2026-07-29 07:49:00'
       UNION ALL SELECT (@cd_a + 7), (@cd_sm + 1), (@cd_u + 2), (@cd_c + 1), '동호회', '확정', 'RCPT-20260729-4FAAC2B3', '2026-07-29 05:00:00', '2026-07-29 06:03:00'
       UNION ALL SELECT (@cd_a + 8), (@cd_sm + 1), (@cd_u + 4), (@cd_c + 1), '동호회', '확정', 'RCPT-20260729-SLMW5HEK', '2026-07-29 05:00:00', '2026-07-29 06:21:00'
       UNION ALL SELECT (@cd_a + 9), (@cd_sm + 1), (@cd_u + 5), (@cd_c + 1), '동호회', '확정', 'RCPT-20260729-BU223EB8', '2026-07-29 05:00:00', '2026-07-29 07:16:00'
       UNION ALL SELECT (@cd_a + 10), (@cd_sm + 1), (@cd_u + 3), (@cd_c + 1), '동호회', '확정', 'RCPT-20260729-RCV8YCHD', '2026-07-29 05:00:00', '2026-07-29 07:08:00'
       UNION ALL SELECT (@cd_a + 11), (@cd_sm + 2), (@cd_u + 8), (@cd_c + 2), '동호회', '확정', 'RCPT-20260729-SXYAGK8X', '2026-07-29 21:00:00', '2026-07-29 23:21:00'
       UNION ALL SELECT (@cd_a + 12), (@cd_sm + 2), (@cd_u + 9), (@cd_c + 2), '동호회', '확정', 'RCPT-20260729-S3YTC9N5', '2026-07-29 21:00:00', '2026-07-29 23:17:00'
       UNION ALL SELECT (@cd_a + 13), (@cd_sm + 2), (@cd_u + 10), (@cd_c + 2), '동호회', '확정', 'RCPT-20260729-X47X6TWG', '2026-07-29 21:00:00', '2026-07-29 23:15:00'
       UNION ALL SELECT (@cd_a + 14), (@cd_sm + 2), (@cd_u + 13), (@cd_c + 2), '동호회', '확정', 'RCPT-20260729-BJQ7VCSQ', '2026-07-29 21:00:00', '2026-07-29 22:51:00'
       UNION ALL SELECT (@cd_a + 15), (@cd_sm + 2), (@cd_u + 14), (@cd_c + 2), '동호회', '확정', 'RCPT-20260729-YVQDZEEA', '2026-07-29 21:00:00', '2026-07-29 22:25:00'
       UNION ALL SELECT (@cd_a + 16), (@cd_sm + 2), (@cd_u + 57), (@cd_c + 9), '동호회', '확정', 'RCPT-20260729-MDJM2X3W', '2026-07-29 01:00:00', '2026-07-29 03:44:00'
       UNION ALL SELECT (@cd_a + 17), (@cd_sm + 2), (@cd_u + 65), (@cd_c + 9), '동호회', '확정', 'RCPT-20260729-RAQFA9L8', '2026-07-29 01:00:00', '2026-07-29 03:54:00'
       UNION ALL SELECT (@cd_a + 18), (@cd_sm + 2), (@cd_u + 61), (@cd_c + 9), '동호회', '확정', 'RCPT-20260729-FJH2GKW4', '2026-07-29 01:00:00', '2026-07-29 03:52:00'
       UNION ALL SELECT (@cd_a + 19), (@cd_sm + 2), (@cd_u + 58), (@cd_c + 9), '동호회', '확정', 'RCPT-20260729-2JB6KDA2', '2026-07-29 01:00:00', '2026-07-29 02:27:00'
       UNION ALL SELECT (@cd_a + 20), (@cd_sm + 2), (@cd_u + 59), (@cd_c + 9), '동호회', '확정', 'RCPT-20260729-MDYVUX52', '2026-07-29 01:00:00', '2026-07-29 03:45:00'
       UNION ALL SELECT (@cd_a + 21), (@cd_sm + 3), (@cd_u + 67), (@cd_c + 10), '동호회', '확정', 'RCPT-20260731-NT3RGTQ5', '2026-07-31 18:00:00', '2026-07-31 19:35:00'
       UNION ALL SELECT (@cd_a + 22), (@cd_sm + 3), (@cd_u + 74), (@cd_c + 10), '동호회', '확정', 'RCPT-20260731-YH3MUMGB', '2026-07-31 18:00:00', '2026-07-31 20:00:00'
       UNION ALL SELECT (@cd_a + 23), (@cd_sm + 3), (@cd_u + 72), (@cd_c + 10), '동호회', '확정', 'RCPT-20260731-UL2XE7BH', '2026-07-31 18:00:00', '2026-07-31 20:14:00'
       UNION ALL SELECT (@cd_a + 24), (@cd_sm + 3), (@cd_u + 75), (@cd_c + 10), '동호회', '확정', 'RCPT-20260731-HBHHUZXS', '2026-07-31 18:00:00', '2026-07-31 19:31:00'
       UNION ALL SELECT (@cd_a + 25), (@cd_sm + 3), (@cd_u + 70), (@cd_c + 10), '동호회', '확정', 'RCPT-20260731-AR3JLAW3', '2026-07-31 18:00:00', '2026-07-31 19:40:00'
       UNION ALL SELECT (@cd_a + 26), (@cd_sm + 3), (@cd_u + 29), (@cd_c + 5), '동호회', '확정', 'RCPT-20260731-CQBSJM39', '2026-07-31 17:00:00', '2026-07-31 19:36:00'
       UNION ALL SELECT (@cd_a + 27), (@cd_sm + 3), (@cd_u + 31), (@cd_c + 5), '동호회', '확정', 'RCPT-20260731-RLAQ9HDN', '2026-07-31 17:00:00', '2026-07-31 18:02:00'
       UNION ALL SELECT (@cd_a + 28), (@cd_sm + 3), (@cd_u + 30), (@cd_c + 5), '동호회', '확정', 'RCPT-20260731-76XLCM32', '2026-07-31 17:00:00', '2026-07-31 19:23:00'
       UNION ALL SELECT (@cd_a + 29), (@cd_sm + 3), (@cd_u + 34), (@cd_c + 5), '동호회', '확정', 'RCPT-20260731-KJHH7JAK', '2026-07-31 17:00:00', '2026-07-31 18:54:00'
       UNION ALL SELECT (@cd_a + 30), (@cd_sm + 3), (@cd_u + 33), (@cd_c + 5), '동호회', '확정', 'RCPT-20260731-3RWM9VYW', '2026-07-31 17:00:00', '2026-07-31 18:02:00'
       UNION ALL SELECT (@cd_a + 31), (@cd_sm + 4), (@cd_u + 1), (@cd_c + 1), '동호회', '확정', 'RCPT-20260802-UZABRW5H', '2026-08-02 22:00:00', '2026-08-03 00:52:00'
       UNION ALL SELECT (@cd_a + 32), (@cd_sm + 4), (@cd_u + 6), (@cd_c + 1), '동호회', '확정', 'RCPT-20260802-KQM36QFJ', '2026-08-02 22:00:00', '2026-08-03 00:25:00'
       UNION ALL SELECT (@cd_a + 33), (@cd_sm + 4), (@cd_u + 5), (@cd_c + 1), '동호회', '확정', 'RCPT-20260802-9KDPWJ2N', '2026-08-02 22:00:00', '2026-08-02 23:48:00'
       UNION ALL SELECT (@cd_a + 34), (@cd_sm + 4), (@cd_u + 2), (@cd_c + 1), '동호회', '확정', 'RCPT-20260802-DD84MC4X', '2026-08-02 22:00:00', '2026-08-03 00:04:00'
       UNION ALL SELECT (@cd_a + 35), (@cd_sm + 4), (@cd_u + 3), (@cd_c + 1), '동호회', '확정', 'RCPT-20260802-KE87CGTW', '2026-08-02 22:00:00', '2026-08-02 23:16:00'
       UNION ALL SELECT (@cd_a + 36), (@cd_sm + 4), (@cd_u + 24), (@cd_c + 4), '동호회', '확정', 'RCPT-20260802-7VL4SH9M', '2026-08-02 14:00:00', '2026-08-02 16:15:00'
       UNION ALL SELECT (@cd_a + 37), (@cd_sm + 4), (@cd_u + 25), (@cd_c + 4), '동호회', '확정', 'RCPT-20260802-NJTC8687', '2026-08-02 14:00:00', '2026-08-02 15:17:00'
       UNION ALL SELECT (@cd_a + 38), (@cd_sm + 4), (@cd_u + 23), (@cd_c + 4), '동호회', '확정', 'RCPT-20260802-MBXKXLLL', '2026-08-02 14:00:00', '2026-08-02 15:19:00'
       UNION ALL SELECT (@cd_a + 39), (@cd_sm + 4), (@cd_u + 26), (@cd_c + 4), '동호회', '확정', 'RCPT-20260802-FGWN4RSB', '2026-08-02 14:00:00', '2026-08-02 16:58:00'
       UNION ALL SELECT (@cd_a + 40), (@cd_sm + 4), (@cd_u + 28), (@cd_c + 4), '동호회', '확정', 'RCPT-20260802-H3KQ8RLP', '2026-08-02 14:00:00', '2026-08-02 15:08:00'
       UNION ALL SELECT (@cd_a + 41), (@cd_sm + 5), (@cd_u + 43), (@cd_c + 7), '동호회', '확정', 'RCPT-20260805-K6MMCSLW', '2026-08-05 13:00:00', '2026-08-05 15:29:00'
       UNION ALL SELECT (@cd_a + 42), (@cd_sm + 5), (@cd_u + 48), (@cd_c + 7), '동호회', '확정', 'RCPT-20260805-FAY8LSFF', '2026-08-05 13:00:00', '2026-08-05 15:20:00'
       UNION ALL SELECT (@cd_a + 43), (@cd_sm + 5), (@cd_u + 44), (@cd_c + 7), '동호회', '확정', 'RCPT-20260805-AKALQBRM', '2026-08-05 13:00:00', '2026-08-05 14:35:00'
       UNION ALL SELECT (@cd_a + 44), (@cd_sm + 5), (@cd_u + 45), (@cd_c + 7), '동호회', '확정', 'RCPT-20260805-5E87GP5M', '2026-08-05 13:00:00', '2026-08-05 14:07:00'
       UNION ALL SELECT (@cd_a + 45), (@cd_sm + 5), (@cd_u + 47), (@cd_c + 7), '동호회', '확정', 'RCPT-20260805-ZC87BS8J', '2026-08-05 13:00:00', '2026-08-05 14:52:00'
       UNION ALL SELECT (@cd_a + 46), (@cd_sm + 5), (@cd_u + 1), (@cd_c + 1), '동호회', '확정', 'RCPT-20260807-PHW25767', '2026-08-07 03:00:00', '2026-08-07 04:06:00'
       UNION ALL SELECT (@cd_a + 47), (@cd_sm + 5), (@cd_u + 6), (@cd_c + 1), '동호회', '확정', 'RCPT-20260807-Q5CLFCVM', '2026-08-07 03:00:00', '2026-08-07 05:22:00'
       UNION ALL SELECT (@cd_a + 48), (@cd_sm + 5), (@cd_u + 4), (@cd_c + 1), '동호회', '확정', 'RCPT-20260807-X9JNZGQH', '2026-08-07 03:00:00', '2026-08-07 05:09:00'
       UNION ALL SELECT (@cd_a + 49), (@cd_sm + 5), (@cd_u + 5), (@cd_c + 1), '동호회', '확정', 'RCPT-20260807-ZJWHCJJX', '2026-08-07 03:00:00', '2026-08-07 05:53:00'
       UNION ALL SELECT (@cd_a + 50), (@cd_sm + 5), (@cd_u + 2), (@cd_c + 1), '동호회', '확정', 'RCPT-20260807-BVV9BZCF', '2026-08-07 03:00:00', '2026-08-07 05:32:00'
       UNION ALL SELECT (@cd_a + 51), (@cd_sm + 6), (@cd_u + 8), (@cd_c + 2), '동호회', '확정', 'RCPT-20260807-QVAU7KPM', '2026-08-07 01:00:00', '2026-08-07 03:55:00'
       UNION ALL SELECT (@cd_a + 52), (@cd_sm + 6), (@cd_u + 12), (@cd_c + 2), '동호회', '확정', 'RCPT-20260807-BXM7MT7Z', '2026-08-07 01:00:00', '2026-08-07 03:03:00'
       UNION ALL SELECT (@cd_a + 53), (@cd_sm + 6), (@cd_u + 11), (@cd_c + 2), '동호회', '확정', 'RCPT-20260807-8G5XTX3Q', '2026-08-07 01:00:00', '2026-08-07 02:18:00'
       UNION ALL SELECT (@cd_a + 54), (@cd_sm + 6), (@cd_u + 9), (@cd_c + 2), '동호회', '확정', 'RCPT-20260807-G8SCXXSA', '2026-08-07 01:00:00', '2026-08-07 03:52:00'
       UNION ALL SELECT (@cd_a + 55), (@cd_sm + 6), (@cd_u + 14), (@cd_c + 2), '동호회', '확정', 'RCPT-20260807-SP5V3Z2P', '2026-08-07 01:00:00', '2026-08-07 02:54:00'
       UNION ALL SELECT (@cd_a + 56), (@cd_sm + 6), (@cd_u + 15), (@cd_c + 3), '동호회', '확정', 'RCPT-20260805-MRH6L8MV', '2026-08-05 22:00:00', '2026-08-05 23:10:00'
       UNION ALL SELECT (@cd_a + 57), (@cd_sm + 6), (@cd_u + 16), (@cd_c + 3), '동호회', '확정', 'RCPT-20260805-6G67XDSP', '2026-08-05 22:00:00', '2026-08-05 23:40:00'
       UNION ALL SELECT (@cd_a + 58), (@cd_sm + 6), (@cd_u + 20), (@cd_c + 3), '동호회', '확정', 'RCPT-20260805-3WW6467F', '2026-08-05 22:00:00', '2026-08-06 00:26:00'
       UNION ALL SELECT (@cd_a + 59), (@cd_sm + 6), (@cd_u + 19), (@cd_c + 3), '동호회', '확정', 'RCPT-20260805-C5FEMHAU', '2026-08-05 22:00:00', '2026-08-06 00:02:00'
       UNION ALL SELECT (@cd_a + 60), (@cd_sm + 6), (@cd_u + 17), (@cd_c + 3), '동호회', '확정', 'RCPT-20260805-MUW5HXQC', '2026-08-05 22:00:00', '2026-08-06 00:11:00'
       UNION ALL SELECT (@cd_a + 61), (@cd_sm + 7), (@cd_u + 1), (@cd_c + 1), '동호회', '확정', 'RCPT-20260809-7CZ5F4F2', '2026-08-09 19:00:00', '2026-08-09 21:51:00'
       UNION ALL SELECT (@cd_a + 62), (@cd_sm + 7), (@cd_u + 3), (@cd_c + 1), '동호회', '확정', 'RCPT-20260809-8DT8D9XM', '2026-08-09 19:00:00', '2026-08-09 21:56:00'
       UNION ALL SELECT (@cd_a + 63), (@cd_sm + 7), (@cd_u + 6), (@cd_c + 1), '동호회', '확정', 'RCPT-20260809-72DD9QRP', '2026-08-09 19:00:00', '2026-08-09 20:53:00'
       UNION ALL SELECT (@cd_a + 64), (@cd_sm + 7), (@cd_u + 4), (@cd_c + 1), '동호회', '확정', 'RCPT-20260809-NZLZNWAS', '2026-08-09 19:00:00', '2026-08-09 21:08:00'
       UNION ALL SELECT (@cd_a + 65), (@cd_sm + 7), (@cd_u + 2), (@cd_c + 1), '동호회', '확정', 'RCPT-20260809-T8DDPH7J', '2026-08-09 19:00:00', '2026-08-09 21:44:00'
       UNION ALL SELECT (@cd_a + 66), (@cd_sm + 7), (@cd_u + 57), (@cd_c + 9), '동호회', '확정', 'RCPT-20260810-CMY6FXRD', '2026-08-10 16:00:00', '2026-08-10 18:00:00'
       UNION ALL SELECT (@cd_a + 67), (@cd_sm + 7), (@cd_u + 62), (@cd_c + 9), '동호회', '확정', 'RCPT-20260810-EAUAMCUN', '2026-08-10 16:00:00', '2026-08-10 18:52:00'
       UNION ALL SELECT (@cd_a + 68), (@cd_sm + 7), (@cd_u + 64), (@cd_c + 9), '동호회', '확정', 'RCPT-20260810-4JED9ZA6', '2026-08-10 16:00:00', '2026-08-10 18:53:00'
       UNION ALL SELECT (@cd_a + 69), (@cd_sm + 7), (@cd_u + 60), (@cd_c + 9), '동호회', '확정', 'RCPT-20260810-UNQHFLEJ', '2026-08-10 16:00:00', '2026-08-10 18:31:00'
       UNION ALL SELECT (@cd_a + 70), (@cd_sm + 7), (@cd_u + 63), (@cd_c + 9), '동호회', '확정', 'RCPT-20260810-4BWVYL4P', '2026-08-10 16:00:00', '2026-08-10 17:04:00'
       UNION ALL SELECT (@cd_a + 71), (@cd_sm + 8), (@cd_u + 50), (@cd_c + 8), '동호회', '확정', 'RCPT-20260812-G8LJ7VAJ', '2026-08-12 20:00:00', '2026-08-12 21:43:00'
       UNION ALL SELECT (@cd_a + 72), (@cd_sm + 8), (@cd_u + 51), (@cd_c + 8), '동호회', '확정', 'RCPT-20260812-E84Q9J8S', '2026-08-12 20:00:00', '2026-08-12 22:53:00'
       UNION ALL SELECT (@cd_a + 73), (@cd_sm + 8), (@cd_u + 53), (@cd_c + 8), '동호회', '확정', 'RCPT-20260812-N3YKPZ4S', '2026-08-12 20:00:00', '2026-08-12 21:57:00'
       UNION ALL SELECT (@cd_a + 74), (@cd_sm + 8), (@cd_u + 56), (@cd_c + 8), '동호회', '확정', 'RCPT-20260812-WZXJC95U', '2026-08-12 20:00:00', '2026-08-12 22:05:00'
       UNION ALL SELECT (@cd_a + 75), (@cd_sm + 8), (@cd_u + 54), (@cd_c + 8), '동호회', '확정', 'RCPT-20260812-4G4WAFRE', '2026-08-12 20:00:00', '2026-08-12 21:14:00'
       UNION ALL SELECT (@cd_a + 76), (@cd_sm + 8), (@cd_u + 18), (@cd_c + 3), '동호회', '확정', 'RCPT-20260811-W48AM235', '2026-08-11 11:00:00', '2026-08-11 12:02:00'
       UNION ALL SELECT (@cd_a + 77), (@cd_sm + 8), (@cd_u + 19), (@cd_c + 3), '동호회', '확정', 'RCPT-20260811-XQS5F63X', '2026-08-11 11:00:00', '2026-08-11 13:48:00'
       UNION ALL SELECT (@cd_a + 78), (@cd_sm + 8), (@cd_u + 17), (@cd_c + 3), '동호회', '확정', 'RCPT-20260811-JR9AQCN6', '2026-08-11 11:00:00', '2026-08-11 13:40:00'
       UNION ALL SELECT (@cd_a + 79), (@cd_sm + 8), (@cd_u + 16), (@cd_c + 3), '동호회', '확정', 'RCPT-20260811-W789JB77', '2026-08-11 11:00:00', '2026-08-11 13:42:00'
       UNION ALL SELECT (@cd_a + 80), (@cd_sm + 8), (@cd_u + 20), (@cd_c + 3), '동호회', '확정', 'RCPT-20260811-VHS3BCHN', '2026-08-11 11:00:00', '2026-08-11 12:50:00'
       UNION ALL SELECT (@cd_a + 81), (@cd_sm + 9), (@cd_u + 67), (@cd_c + 10), '동호회', '확정', 'RCPT-20260814-7VA4VAPX', '2026-08-14 14:00:00', '2026-08-14 15:45:00'
       UNION ALL SELECT (@cd_a + 82), (@cd_sm + 9), (@cd_u + 69), (@cd_c + 10), '동호회', '확정', 'RCPT-20260814-RUWP5EH9', '2026-08-14 14:00:00', '2026-08-14 15:38:00'
       UNION ALL SELECT (@cd_a + 83), (@cd_sm + 9), (@cd_u + 68), (@cd_c + 10), '동호회', '확정', 'RCPT-20260814-CD5NVAEY', '2026-08-14 14:00:00', '2026-08-14 16:56:00'
       UNION ALL SELECT (@cd_a + 84), (@cd_sm + 9), (@cd_u + 71), (@cd_c + 10), '동호회', '확정', 'RCPT-20260814-E4S7MYWH', '2026-08-14 14:00:00', '2026-08-14 16:14:00'
       UNION ALL SELECT (@cd_a + 85), (@cd_sm + 9), (@cd_u + 73), (@cd_c + 10), '동호회', '확정', 'RCPT-20260814-S2XADRXT', '2026-08-14 14:00:00', '2026-08-14 15:58:00'
       UNION ALL SELECT (@cd_a + 86), (@cd_sm + 9), (@cd_u + 1), (@cd_c + 1), '동호회', '확정', 'RCPT-20260814-4T3KK52M', '2026-08-14 02:00:00', '2026-08-14 04:57:00'
       UNION ALL SELECT (@cd_a + 87), (@cd_sm + 9), (@cd_u + 4), (@cd_c + 1), '동호회', '확정', 'RCPT-20260814-5GDG28LG', '2026-08-14 02:00:00', '2026-08-14 04:13:00'
       UNION ALL SELECT (@cd_a + 88), (@cd_sm + 9), (@cd_u + 6), (@cd_c + 1), '동호회', '확정', 'RCPT-20260814-FK3JUF2X', '2026-08-14 02:00:00', '2026-08-14 04:20:00'
       UNION ALL SELECT (@cd_a + 89), (@cd_sm + 9), (@cd_u + 3), (@cd_c + 1), '동호회', '확정', 'RCPT-20260814-TV2BN9YV', '2026-08-14 02:00:00', '2026-08-14 04:10:00'
       UNION ALL SELECT (@cd_a + 90), (@cd_sm + 9), (@cd_u + 5), (@cd_c + 1), '동호회', '확정', 'RCPT-20260814-Y7FMCRNF', '2026-08-14 02:00:00', '2026-08-14 04:03:00'
       UNION ALL SELECT (@cd_a + 91), (@cd_sm + 10), (@cd_u + 54), (@cd_c + 8), '동호회', '확정', 'RCPT-20260817-Z4T2MJ2H', '2026-08-17 10:00:00', '2026-08-17 11:15:00'
       UNION ALL SELECT (@cd_a + 92), (@cd_sm + 10), (@cd_u + 52), (@cd_c + 8), '동호회', '확정', 'RCPT-20260817-WDL2JUBX', '2026-08-17 10:00:00', '2026-08-17 11:03:00'
       UNION ALL SELECT (@cd_a + 93), (@cd_sm + 10), (@cd_u + 55), (@cd_c + 8), '동호회', '확정', 'RCPT-20260817-HM6JW3VV', '2026-08-17 10:00:00', '2026-08-17 12:59:00'
       UNION ALL SELECT (@cd_a + 94), (@cd_sm + 10), (@cd_u + 51), (@cd_c + 8), '동호회', '확정', 'RCPT-20260817-YM6LD8UA', '2026-08-17 10:00:00', '2026-08-17 11:45:00'
       UNION ALL SELECT (@cd_a + 95), (@cd_sm + 10), (@cd_u + 56), (@cd_c + 8), '동호회', '확정', 'RCPT-20260817-EBZCJLD6', '2026-08-17 10:00:00', '2026-08-17 12:56:00'
       UNION ALL SELECT (@cd_a + 96), (@cd_sm + 10), (@cd_u + 14), (@cd_c + 2), '동호회', '확정', 'RCPT-20260817-KVE4FAR2', '2026-08-17 16:00:00', '2026-08-17 18:47:00'
       UNION ALL SELECT (@cd_a + 97), (@cd_sm + 10), (@cd_u + 13), (@cd_c + 2), '동호회', '확정', 'RCPT-20260817-6YH9MCJV', '2026-08-17 16:00:00', '2026-08-17 18:28:00'
       UNION ALL SELECT (@cd_a + 98), (@cd_sm + 10), (@cd_u + 10), (@cd_c + 2), '동호회', '확정', 'RCPT-20260817-WLTGUC34', '2026-08-17 16:00:00', '2026-08-17 18:30:00'
       UNION ALL SELECT (@cd_a + 99), (@cd_sm + 10), (@cd_u + 11), (@cd_c + 2), '동호회', '확정', 'RCPT-20260817-NBT8WXFG', '2026-08-17 16:00:00', '2026-08-17 18:09:00'
       UNION ALL SELECT (@cd_a + 100), (@cd_sm + 10), (@cd_u + 12), (@cd_c + 2), '동호회', '확정', 'RCPT-20260817-G358R96P', '2026-08-17 16:00:00', '2026-08-17 17:08:00'
       UNION ALL SELECT (@cd_a + 101), (@cd_sm + 11), (@cd_u + 47), (@cd_c + 7), '동호회', '확정', 'RCPT-20260819-9WFHXV22', '2026-08-19 07:00:00', '2026-08-19 08:06:00'
       UNION ALL SELECT (@cd_a + 102), (@cd_sm + 11), (@cd_u + 48), (@cd_c + 7), '동호회', '확정', 'RCPT-20260819-4BP5GGH5', '2026-08-19 07:00:00', '2026-08-19 08:47:00'
       UNION ALL SELECT (@cd_a + 103), (@cd_sm + 11), (@cd_u + 46), (@cd_c + 7), '동호회', '확정', 'RCPT-20260819-6835VRXM', '2026-08-19 07:00:00', '2026-08-19 09:23:00'
       UNION ALL SELECT (@cd_a + 104), (@cd_sm + 11), (@cd_u + 45), (@cd_c + 7), '동호회', '확정', 'RCPT-20260819-Z8Y5GR2Y', '2026-08-19 07:00:00', '2026-08-19 08:27:00'
       UNION ALL SELECT (@cd_a + 105), (@cd_sm + 11), (@cd_u + 44), (@cd_c + 7), '동호회', '확정', 'RCPT-20260819-W3FY6F9E', '2026-08-19 07:00:00', '2026-08-19 08:22:00'
       UNION ALL SELECT (@cd_a + 106), (@cd_sm + 11), (@cd_u + 2), (@cd_c + 1), '동호회', '확정', 'RCPT-20260819-H9MLVBDT', '2026-08-19 07:00:00', '2026-08-19 08:01:00'
       UNION ALL SELECT (@cd_a + 107), (@cd_sm + 11), (@cd_u + 6), (@cd_c + 1), '동호회', '확정', 'RCPT-20260819-J7HTAVSV', '2026-08-19 07:00:00', '2026-08-19 08:14:00'
       UNION ALL SELECT (@cd_a + 108), (@cd_sm + 11), (@cd_u + 5), (@cd_c + 1), '동호회', '확정', 'RCPT-20260819-WZYW9RRM', '2026-08-19 07:00:00', '2026-08-19 09:50:00'
       UNION ALL SELECT (@cd_a + 109), (@cd_sm + 11), (@cd_u + 3), (@cd_c + 1), '동호회', '확정', 'RCPT-20260819-DDLXUA53', '2026-08-19 07:00:00', '2026-08-19 08:23:00'
       UNION ALL SELECT (@cd_a + 110), (@cd_sm + 11), (@cd_u + 4), (@cd_c + 1), '동호회', '확정', 'RCPT-20260819-2HX93RRN', '2026-08-19 07:00:00', '2026-08-19 08:50:00'
       UNION ALL SELECT (@cd_a + 111), (@cd_sm + 12), (@cd_u + 29), (@cd_c + 5), '동호회', '확정', 'RCPT-20260820-E53U26G4', '2026-08-20 01:00:00', '2026-08-20 02:30:00'
       UNION ALL SELECT (@cd_a + 112), (@cd_sm + 12), (@cd_u + 32), (@cd_c + 5), '동호회', '확정', 'RCPT-20260820-Z8NSLGGQ', '2026-08-20 01:00:00', '2026-08-20 03:38:00'
       UNION ALL SELECT (@cd_a + 113), (@cd_sm + 12), (@cd_u + 34), (@cd_c + 5), '동호회', '확정', 'RCPT-20260820-5JP3LWVD', '2026-08-20 01:00:00', '2026-08-20 03:13:00'
       UNION ALL SELECT (@cd_a + 114), (@cd_sm + 12), (@cd_u + 31), (@cd_c + 5), '동호회', '확정', 'RCPT-20260820-AJ93CPYX', '2026-08-20 01:00:00', '2026-08-20 02:53:00'
       UNION ALL SELECT (@cd_a + 115), (@cd_sm + 12), (@cd_u + 30), (@cd_c + 5), '동호회', '확정', 'RCPT-20260820-XBN6WP9B', '2026-08-20 01:00:00', '2026-08-20 03:55:00'
       UNION ALL SELECT (@cd_a + 116), (@cd_sm + 12), (@cd_u + 22), (@cd_c + 4), '동호회', '확정', 'RCPT-20260819-G5GFMPCM', '2026-08-19 22:00:00', '2026-08-20 00:08:00'
       UNION ALL SELECT (@cd_a + 117), (@cd_sm + 12), (@cd_u + 27), (@cd_c + 4), '동호회', '확정', 'RCPT-20260819-X9KXQA23', '2026-08-19 22:00:00', '2026-08-20 00:02:00'
       UNION ALL SELECT (@cd_a + 118), (@cd_sm + 12), (@cd_u + 25), (@cd_c + 4), '동호회', '확정', 'RCPT-20260819-9DADNDDA', '2026-08-19 22:00:00', '2026-08-20 00:31:00'
       UNION ALL SELECT (@cd_a + 119), (@cd_sm + 12), (@cd_u + 24), (@cd_c + 4), '동호회', '확정', 'RCPT-20260819-TEHLN34Y', '2026-08-19 22:00:00', '2026-08-19 23:28:00'
       UNION ALL SELECT (@cd_a + 120), (@cd_sm + 12), (@cd_u + 26), (@cd_c + 4), '동호회', '확정', 'RCPT-20260819-F9WQ54RT', '2026-08-19 22:00:00', '2026-08-19 23:18:00'
       UNION ALL SELECT (@cd_a + 121), (@cd_sm + 13), (@cd_u + 1), (@cd_c + 1), '동호회', '확정', 'RCPT-20260824-2JF2GMAB', '2026-08-24 15:00:00', '2026-08-24 17:22:00'
       UNION ALL SELECT (@cd_a + 122), (@cd_sm + 13), (@cd_u + 2), (@cd_c + 1), '동호회', '확정', 'RCPT-20260824-TEPWMB6P', '2026-08-24 15:00:00', '2026-08-24 17:52:00'
       UNION ALL SELECT (@cd_a + 123), (@cd_sm + 13), (@cd_u + 3), (@cd_c + 1), '동호회', '확정', 'RCPT-20260824-BGXSAN2J', '2026-08-24 15:00:00', '2026-08-24 16:22:00'
       UNION ALL SELECT (@cd_a + 124), (@cd_sm + 13), (@cd_u + 6), (@cd_c + 1), '동호회', '확정', 'RCPT-20260824-69T74R65', '2026-08-24 15:00:00', '2026-08-24 16:32:00'
       UNION ALL SELECT (@cd_a + 125), (@cd_sm + 13), (@cd_u + 4), (@cd_c + 1), '동호회', '확정', 'RCPT-20260824-KTGDPTF8', '2026-08-24 15:00:00', '2026-08-24 17:55:00'
       UNION ALL SELECT (@cd_a + 126), (@cd_sm + 13), (@cd_u + 29), (@cd_c + 5), '동호회', '확정', 'RCPT-20260824-VHXD8GNB', '2026-08-24 16:00:00', '2026-08-24 17:37:00'
       UNION ALL SELECT (@cd_a + 127), (@cd_sm + 13), (@cd_u + 33), (@cd_c + 5), '동호회', '확정', 'RCPT-20260824-FYBQVE2C', '2026-08-24 16:00:00', '2026-08-24 19:00:00'
       UNION ALL SELECT (@cd_a + 128), (@cd_sm + 13), (@cd_u + 32), (@cd_c + 5), '동호회', '확정', 'RCPT-20260824-U5B9FQFP', '2026-08-24 16:00:00', '2026-08-24 17:19:00'
       UNION ALL SELECT (@cd_a + 129), (@cd_sm + 13), (@cd_u + 30), (@cd_c + 5), '동호회', '확정', 'RCPT-20260824-JNU972FV', '2026-08-24 16:00:00', '2026-08-24 19:00:00'
       UNION ALL SELECT (@cd_a + 130), (@cd_sm + 13), (@cd_u + 34), (@cd_c + 5), '동호회', '확정', 'RCPT-20260824-KWPUAJTP', '2026-08-24 16:00:00', '2026-08-24 17:19:00'
       UNION ALL SELECT (@cd_a + 131), (@cd_sm + 14), (@cd_u + 15), (@cd_c + 3), '동호회', '확정', 'RCPT-20260825-NABG5MFY', '2026-08-25 17:00:00', '2026-08-25 19:31:00'
       UNION ALL SELECT (@cd_a + 132), (@cd_sm + 14), (@cd_u + 18), (@cd_c + 3), '동호회', '확정', 'RCPT-20260825-FY8HHEGM', '2026-08-25 17:00:00', '2026-08-25 18:07:00'
       UNION ALL SELECT (@cd_a + 133), (@cd_sm + 14), (@cd_u + 16), (@cd_c + 3), '동호회', '확정', 'RCPT-20260825-PR8FPKJW', '2026-08-25 17:00:00', '2026-08-25 19:42:00'
       UNION ALL SELECT (@cd_a + 134), (@cd_sm + 14), (@cd_u + 19), (@cd_c + 3), '동호회', '확정', 'RCPT-20260825-SPMK3KXU', '2026-08-25 17:00:00', '2026-08-25 18:35:00'
       UNION ALL SELECT (@cd_a + 135), (@cd_sm + 14), (@cd_u + 20), (@cd_c + 3), '동호회', '확정', 'RCPT-20260825-9DEEHJLH', '2026-08-25 17:00:00', '2026-08-25 18:36:00'
       UNION ALL SELECT (@cd_a + 136), (@cd_sm + 14), (@cd_u + 22), (@cd_c + 4), '동호회', '확정', 'RCPT-20260825-FSNMMF2G', '2026-08-25 11:00:00', '2026-08-25 12:41:00'
       UNION ALL SELECT (@cd_a + 137), (@cd_sm + 14), (@cd_u + 27), (@cd_c + 4), '동호회', '확정', 'RCPT-20260825-CVQSKQAN', '2026-08-25 11:00:00', '2026-08-25 13:45:00'
       UNION ALL SELECT (@cd_a + 138), (@cd_sm + 14), (@cd_u + 23), (@cd_c + 4), '동호회', '확정', 'RCPT-20260825-J4SX8SUS', '2026-08-25 11:00:00', '2026-08-25 12:47:00'
       UNION ALL SELECT (@cd_a + 139), (@cd_sm + 14), (@cd_u + 28), (@cd_c + 4), '동호회', '확정', 'RCPT-20260825-EBYY8NKT', '2026-08-25 11:00:00', '2026-08-25 12:40:00'
       UNION ALL SELECT (@cd_a + 140), (@cd_sm + 14), (@cd_u + 24), (@cd_c + 4), '동호회', '확정', 'RCPT-20260825-QL3UWKAL', '2026-08-25 11:00:00', '2026-08-25 13:49:00'
       UNION ALL SELECT (@cd_a + 141), (@cd_sm + 15), (@cd_u + 67), (@cd_c + 10), '동호회', '확정', 'RCPT-20260828-2JBUZ29Q', '2026-08-28 10:00:00', '2026-08-28 12:50:00'
       UNION ALL SELECT (@cd_a + 142), (@cd_sm + 15), (@cd_u + 68), (@cd_c + 10), '동호회', '확정', 'RCPT-20260828-8K9SYM5H', '2026-08-28 10:00:00', '2026-08-28 11:37:00'
       UNION ALL SELECT (@cd_a + 143), (@cd_sm + 15), (@cd_u + 71), (@cd_c + 10), '동호회', '확정', 'RCPT-20260828-DNZYNYHJ', '2026-08-28 10:00:00', '2026-08-28 12:04:00'
       UNION ALL SELECT (@cd_a + 144), (@cd_sm + 15), (@cd_u + 70), (@cd_c + 10), '동호회', '확정', 'RCPT-20260828-EPKAXDDU', '2026-08-28 10:00:00', '2026-08-28 12:19:00'
       UNION ALL SELECT (@cd_a + 145), (@cd_sm + 15), (@cd_u + 73), (@cd_c + 10), '동호회', '확정', 'RCPT-20260828-6DLSE85K', '2026-08-28 10:00:00', '2026-08-28 12:48:00'
       UNION ALL SELECT (@cd_a + 146), (@cd_sm + 15), (@cd_u + 1), (@cd_c + 1), '동호회', '확정', 'RCPT-20260828-Q7REWT3V', '2026-08-28 01:00:00', '2026-08-28 03:32:00'
       UNION ALL SELECT (@cd_a + 147), (@cd_sm + 15), (@cd_u + 5), (@cd_c + 1), '동호회', '확정', 'RCPT-20260828-GYSCCAEV', '2026-08-28 01:00:00', '2026-08-28 03:20:00'
       UNION ALL SELECT (@cd_a + 148), (@cd_sm + 15), (@cd_u + 3), (@cd_c + 1), '동호회', '확정', 'RCPT-20260828-A4QPUPQT', '2026-08-28 01:00:00', '2026-08-28 02:35:00'
       UNION ALL SELECT (@cd_a + 149), (@cd_sm + 15), (@cd_u + 6), (@cd_c + 1), '동호회', '확정', 'RCPT-20260828-5969P9JW', '2026-08-28 01:00:00', '2026-08-28 02:53:00'
       UNION ALL SELECT (@cd_a + 150), (@cd_sm + 15), (@cd_u + 4), (@cd_c + 1), '동호회', '확정', 'RCPT-20260828-26BH9X6Y', '2026-08-28 01:00:00', '2026-08-28 02:13:00'
       UNION ALL SELECT (@cd_a + 151), (@cd_sm + 16), (@cd_u + 50), (@cd_c + 8), '동호회', '확정', 'RCPT-20260829-BXWTFJQS', '2026-08-29 19:00:00', '2026-08-29 21:14:00'
       UNION ALL SELECT (@cd_a + 152), (@cd_sm + 16), (@cd_u + 52), (@cd_c + 8), '동호회', '확정', 'RCPT-20260829-9EBWXLJD', '2026-08-29 19:00:00', '2026-08-29 20:56:00'
       UNION ALL SELECT (@cd_a + 153), (@cd_sm + 16), (@cd_u + 53), (@cd_c + 8), '동호회', '확정', 'RCPT-20260829-NPHWYA3C', '2026-08-29 19:00:00', '2026-08-29 21:02:00'
       UNION ALL SELECT (@cd_a + 154), (@cd_sm + 16), (@cd_u + 55), (@cd_c + 8), '동호회', '확정', 'RCPT-20260829-W8LK8ESD', '2026-08-29 19:00:00', '2026-08-29 20:05:00'
       UNION ALL SELECT (@cd_a + 155), (@cd_sm + 16), (@cd_u + 56), (@cd_c + 8), '동호회', '확정', 'RCPT-20260829-XL85V2SF', '2026-08-29 19:00:00', '2026-08-29 21:51:00'
       UNION ALL SELECT (@cd_a + 156), (@cd_sm + 16), (@cd_u + 57), (@cd_c + 9), '동호회', '확정', 'RCPT-20260829-CZ3J8LWC', '2026-08-29 13:00:00', '2026-08-29 15:29:00'
       UNION ALL SELECT (@cd_a + 157), (@cd_sm + 16), (@cd_u + 61), (@cd_c + 9), '동호회', '확정', 'RCPT-20260829-ZVLTU8T2', '2026-08-29 13:00:00', '2026-08-29 14:04:00'
       UNION ALL SELECT (@cd_a + 158), (@cd_sm + 16), (@cd_u + 60), (@cd_c + 9), '동호회', '확정', 'RCPT-20260829-NWVNX4D7', '2026-08-29 13:00:00', '2026-08-29 14:44:00'
       UNION ALL SELECT (@cd_a + 159), (@cd_sm + 16), (@cd_u + 59), (@cd_c + 9), '동호회', '확정', 'RCPT-20260829-R4HJM8BC', '2026-08-29 13:00:00', '2026-08-29 15:50:00'
       UNION ALL SELECT (@cd_a + 160), (@cd_sm + 16), (@cd_u + 58), (@cd_c + 9), '동호회', '확정', 'RCPT-20260829-AZ3CPWFF', '2026-08-29 13:00:00', '2026-08-29 14:11:00'
       UNION ALL SELECT (@cd_a + 161), (@cd_sm + 17), (@cd_u + 22), (@cd_c + 4), '동호회', '확정', 'RCPT-20260831-TLV4X46T', '2026-08-31 16:00:00', '2026-08-31 17:59:00'
       UNION ALL SELECT (@cd_a + 162), (@cd_sm + 17), (@cd_u + 27), (@cd_c + 4), '동호회', '확정', 'RCPT-20260831-SMSZ6EBL', '2026-08-31 16:00:00', '2026-08-31 18:06:00'
       UNION ALL SELECT (@cd_a + 163), (@cd_sm + 17), (@cd_u + 23), (@cd_c + 4), '동호회', '확정', 'RCPT-20260831-3J75KSLC', '2026-08-31 16:00:00', '2026-08-31 18:58:00'
       UNION ALL SELECT (@cd_a + 164), (@cd_sm + 17), (@cd_u + 25), (@cd_c + 4), '동호회', '확정', 'RCPT-20260831-6NFJXDPW', '2026-08-31 16:00:00', '2026-08-31 17:48:00'
       UNION ALL SELECT (@cd_a + 165), (@cd_sm + 17), (@cd_u + 26), (@cd_c + 4), '동호회', '확정', 'RCPT-20260831-9MUSD6XK', '2026-08-31 16:00:00', '2026-08-31 17:38:00'
       UNION ALL SELECT (@cd_a + 166), (@cd_sm + 17), (@cd_u + 43), (@cd_c + 7), '동호회', '확정', 'RCPT-20260831-JM8DNE9F', '2026-08-31 17:00:00', '2026-08-31 18:34:00'
       UNION ALL SELECT (@cd_a + 167), (@cd_sm + 17), (@cd_u + 48), (@cd_c + 7), '동호회', '확정', 'RCPT-20260831-ZZ9X5YAZ', '2026-08-31 17:00:00', '2026-08-31 18:12:00'
       UNION ALL SELECT (@cd_a + 168), (@cd_sm + 17), (@cd_u + 46), (@cd_c + 7), '동호회', '확정', 'RCPT-20260831-3ET46UX7', '2026-08-31 17:00:00', '2026-08-31 18:56:00'
       UNION ALL SELECT (@cd_a + 169), (@cd_sm + 17), (@cd_u + 44), (@cd_c + 7), '동호회', '확정', 'RCPT-20260831-RY8Y7DJ8', '2026-08-31 17:00:00', '2026-08-31 18:47:00'
       UNION ALL SELECT (@cd_a + 170), (@cd_sm + 17), (@cd_u + 45), (@cd_c + 7), '동호회', '확정', 'RCPT-20260831-LRSNWCEF', '2026-08-31 17:00:00', '2026-08-31 19:10:00'
       UNION ALL SELECT (@cd_a + 171), (@cd_sm + 18), (@cd_u + 15), (@cd_c + 3), '동호회', '확정', 'RCPT-20260831-YDC2Q772', '2026-08-31 16:00:00', '2026-08-31 17:31:00'
       UNION ALL SELECT (@cd_a + 172), (@cd_sm + 18), (@cd_u + 17), (@cd_c + 3), '동호회', '확정', 'RCPT-20260831-KXTXQ8DU', '2026-08-31 16:00:00', '2026-08-31 17:04:00'
       UNION ALL SELECT (@cd_a + 173), (@cd_sm + 18), (@cd_u + 18), (@cd_c + 3), '동호회', '확정', 'RCPT-20260831-QJQ38E52', '2026-08-31 16:00:00', '2026-08-31 17:56:00'
       UNION ALL SELECT (@cd_a + 174), (@cd_sm + 18), (@cd_u + 16), (@cd_c + 3), '동호회', '확정', 'RCPT-20260831-A3RZA4EE', '2026-08-31 16:00:00', '2026-08-31 18:50:00'
       UNION ALL SELECT (@cd_a + 175), (@cd_sm + 18), (@cd_u + 19), (@cd_c + 3), '동호회', '확정', 'RCPT-20260831-FTECJ2KJ', '2026-08-31 16:00:00', '2026-08-31 17:13:00'
       UNION ALL SELECT (@cd_a + 176), (@cd_sm + 18), (@cd_u + 67), (@cd_c + 10), '동호회', '확정', 'RCPT-20260902-MZQWVZQK', '2026-09-02 19:00:00', '2026-09-02 20:08:00'
       UNION ALL SELECT (@cd_a + 177), (@cd_sm + 18), (@cd_u + 75), (@cd_c + 10), '동호회', '확정', 'RCPT-20260902-MH352L2S', '2026-09-02 19:00:00', '2026-09-02 20:14:00'
       UNION ALL SELECT (@cd_a + 178), (@cd_sm + 18), (@cd_u + 72), (@cd_c + 10), '동호회', '확정', 'RCPT-20260902-XZCVE9PD', '2026-09-02 19:00:00', '2026-09-02 21:36:00'
       UNION ALL SELECT (@cd_a + 179), (@cd_sm + 18), (@cd_u + 74), (@cd_c + 10), '동호회', '확정', 'RCPT-20260902-88XUYZNE', '2026-09-02 19:00:00', '2026-09-02 21:39:00'
       UNION ALL SELECT (@cd_a + 180), (@cd_sm + 18), (@cd_u + 69), (@cd_c + 10), '동호회', '확정', 'RCPT-20260902-PEG2NS67', '2026-09-02 19:00:00', '2026-09-02 20:54:00'
       UNION ALL SELECT (@cd_a + 181), (@cd_sm + 19), (@cd_u + 8), (@cd_c + 2), '동호회', '확정', 'RCPT-20260831-232TGMBZ', '2026-08-31 21:00:00', '2026-08-31 23:06:00'
       UNION ALL SELECT (@cd_a + 182), (@cd_sm + 19), (@cd_u + 12), (@cd_c + 2), '동호회', '확정', 'RCPT-20260831-G2SLLRHR', '2026-08-31 21:00:00', '2026-08-31 22:04:00'
       UNION ALL SELECT (@cd_a + 183), (@cd_sm + 19), (@cd_u + 9), (@cd_c + 2), '동호회', '확정', 'RCPT-20260831-KB9KEZNY', '2026-08-31 21:00:00', '2026-08-31 22:37:00'
       UNION ALL SELECT (@cd_a + 184), (@cd_sm + 19), (@cd_u + 10), (@cd_c + 2), '동호회', '확정', 'RCPT-20260831-M9BE6M76', '2026-08-31 21:00:00', '2026-08-31 23:58:00'
       UNION ALL SELECT (@cd_a + 185), (@cd_sm + 19), (@cd_u + 13), (@cd_c + 2), '동호회', '확정', 'RCPT-20260831-AVA5PACF', '2026-08-31 21:00:00', '2026-08-31 23:01:00'
       UNION ALL SELECT (@cd_a + 186), (@cd_sm + 19), (@cd_u + 1), (@cd_c + 1), '동호회', '확정', 'RCPT-20260902-L2VDF2G3', '2026-09-02 01:00:00', '2026-09-02 02:14:00'
       UNION ALL SELECT (@cd_a + 187), (@cd_sm + 19), (@cd_u + 5), (@cd_c + 1), '동호회', '확정', 'RCPT-20260902-YPSQME7M', '2026-09-02 01:00:00', '2026-09-02 03:48:00'
       UNION ALL SELECT (@cd_a + 188), (@cd_sm + 19), (@cd_u + 2), (@cd_c + 1), '동호회', '확정', 'RCPT-20260902-RLAL8RK4', '2026-09-02 01:00:00', '2026-09-02 03:39:00'
       UNION ALL SELECT (@cd_a + 189), (@cd_sm + 19), (@cd_u + 3), (@cd_c + 1), '동호회', '확정', 'RCPT-20260902-82M4ZPXX', '2026-09-02 01:00:00', '2026-09-02 02:01:00'
       UNION ALL SELECT (@cd_a + 190), (@cd_sm + 19), (@cd_u + 4), (@cd_c + 1), '동호회', '확정', 'RCPT-20260902-VHP98AHH', '2026-09-02 01:00:00', '2026-09-02 03:00:00'
       UNION ALL SELECT (@cd_a + 191), (@cd_sm + 20), (@cd_u + 29), (@cd_c + 5), '동호회', '확정', 'RCPT-20260903-8YLPHRSG', '2026-09-03 22:00:00', '2026-09-03 23:56:00'
       UNION ALL SELECT (@cd_a + 192), (@cd_sm + 20), (@cd_u + 32), (@cd_c + 5), '동호회', '확정', 'RCPT-20260903-EMC6Z64A', '2026-09-03 22:00:00', '2026-09-03 23:21:00'
       UNION ALL SELECT (@cd_a + 193), (@cd_sm + 20), (@cd_u + 33), (@cd_c + 5), '동호회', '확정', 'RCPT-20260903-WLN4ZUFE', '2026-09-03 22:00:00', '2026-09-04 00:27:00'
       UNION ALL SELECT (@cd_a + 194), (@cd_sm + 20), (@cd_u + 31), (@cd_c + 5), '동호회', '확정', 'RCPT-20260903-S4FSCETL', '2026-09-03 22:00:00', '2026-09-03 23:57:00'
       UNION ALL SELECT (@cd_a + 195), (@cd_sm + 20), (@cd_u + 34), (@cd_c + 5), '동호회', '확정', 'RCPT-20260903-N299US5U', '2026-09-03 22:00:00', '2026-09-03 23:01:00'
       UNION ALL SELECT (@cd_a + 196), (@cd_sm + 20), (@cd_u + 57), (@cd_c + 9), '동호회', '확정', 'RCPT-20260904-HXALH8R3', '2026-09-04 06:00:00', '2026-09-04 07:14:00'
       UNION ALL SELECT (@cd_a + 197), (@cd_sm + 20), (@cd_u + 63), (@cd_c + 9), '동호회', '확정', 'RCPT-20260904-LFFTEJT5', '2026-09-04 06:00:00', '2026-09-04 08:29:00'
       UNION ALL SELECT (@cd_a + 198), (@cd_sm + 20), (@cd_u + 62), (@cd_c + 9), '동호회', '확정', 'RCPT-20260904-VMB4536H', '2026-09-04 06:00:00', '2026-09-04 07:06:00'
       UNION ALL SELECT (@cd_a + 199), (@cd_sm + 20), (@cd_u + 64), (@cd_c + 9), '동호회', '확정', 'RCPT-20260904-WW7FJQYC', '2026-09-04 06:00:00', '2026-09-04 07:32:00'
       UNION ALL SELECT (@cd_a + 200), (@cd_sm + 20), (@cd_u + 65), (@cd_c + 9), '동호회', '확정', 'RCPT-20260904-9NGRPBPM', '2026-09-04 06:00:00', '2026-09-04 07:36:00'
       UNION ALL SELECT (@cd_a + 201), (@cd_sm + 21), (@cd_u + 3), (@cd_c + 1), '동호회', '확정', 'RCPT-20260906-XHEWXBHH', '2026-09-06 15:00:00', '2026-09-06 17:04:00'
       UNION ALL SELECT (@cd_a + 202), (@cd_sm + 21), (@cd_u + 5), (@cd_c + 1), '동호회', '확정', 'RCPT-20260906-KE4PGKSC', '2026-09-06 15:00:00', '2026-09-06 16:30:00'
       UNION ALL SELECT (@cd_a + 203), (@cd_sm + 21), (@cd_u + 6), (@cd_c + 1), '동호회', '확정', 'RCPT-20260906-Y2VLLF7A', '2026-09-06 15:00:00', '2026-09-06 16:56:00'
       UNION ALL SELECT (@cd_a + 204), (@cd_sm + 21), (@cd_u + 2), (@cd_c + 1), '동호회', '확정', 'RCPT-20260906-NCQSULJS', '2026-09-06 15:00:00', '2026-09-06 16:17:00'
       UNION ALL SELECT (@cd_a + 205), (@cd_sm + 21), (@cd_u + 4), (@cd_c + 1), '동호회', '확정', 'RCPT-20260906-9EM9GVNP', '2026-09-06 15:00:00', '2026-09-06 17:23:00'
       UNION ALL SELECT (@cd_a + 206), (@cd_sm + 21), (@cd_u + 67), (@cd_c + 10), '동호회', '확정', 'RCPT-20260907-LAW3R9AL', '2026-09-07 17:00:00', '2026-09-07 19:33:00'
       UNION ALL SELECT (@cd_a + 207), (@cd_sm + 21), (@cd_u + 68), (@cd_c + 10), '동호회', '확정', 'RCPT-20260907-9MWGG856', '2026-09-07 17:00:00', '2026-09-07 18:58:00'
       UNION ALL SELECT (@cd_a + 208), (@cd_sm + 21), (@cd_u + 75), (@cd_c + 10), '동호회', '확정', 'RCPT-20260907-S4WZMU4X', '2026-09-07 17:00:00', '2026-09-07 19:42:00'
       UNION ALL SELECT (@cd_a + 209), (@cd_sm + 21), (@cd_u + 70), (@cd_c + 10), '동호회', '확정', 'RCPT-20260907-VYBVVUZS', '2026-09-07 17:00:00', '2026-09-07 19:58:00'
       UNION ALL SELECT (@cd_a + 210), (@cd_sm + 21), (@cd_u + 74), (@cd_c + 10), '동호회', '확정', 'RCPT-20260907-U5JN59DA', '2026-09-07 17:00:00', '2026-09-07 18:36:00'
       UNION ALL SELECT (@cd_a + 211), (@cd_sm + 22), (@cd_u + 50), (@cd_c + 8), '동호회', '확정', 'RCPT-20260908-UPTQ2NM5', '2026-09-08 10:00:00', '2026-09-08 12:22:00'
       UNION ALL SELECT (@cd_a + 212), (@cd_sm + 22), (@cd_u + 51), (@cd_c + 8), '동호회', '확정', 'RCPT-20260908-625GD2RW', '2026-09-08 10:00:00', '2026-09-08 11:46:00'
       UNION ALL SELECT (@cd_a + 213), (@cd_sm + 22), (@cd_u + 54), (@cd_c + 8), '동호회', '확정', 'RCPT-20260908-VQMBKPMH', '2026-09-08 10:00:00', '2026-09-08 12:59:00'
       UNION ALL SELECT (@cd_a + 214), (@cd_sm + 22), (@cd_u + 52), (@cd_c + 8), '동호회', '확정', 'RCPT-20260908-JVRAWHY8', '2026-09-08 10:00:00', '2026-09-08 12:39:00'
       UNION ALL SELECT (@cd_a + 215), (@cd_sm + 22), (@cd_u + 55), (@cd_c + 8), '동호회', '확정', 'RCPT-20260908-TX2BY7WS', '2026-09-08 10:00:00', '2026-09-08 12:07:00'
       UNION ALL SELECT (@cd_a + 216), (@cd_sm + 22), (@cd_u + 57), (@cd_c + 9), '동호회', '확정', 'RCPT-20260908-A85SA2GE', '2026-09-08 14:00:00', '2026-09-08 16:43:00'
       UNION ALL SELECT (@cd_a + 217), (@cd_sm + 22), (@cd_u + 58), (@cd_c + 9), '동호회', '확정', 'RCPT-20260908-3V844754', '2026-09-08 14:00:00', '2026-09-08 15:28:00'
       UNION ALL SELECT (@cd_a + 218), (@cd_sm + 22), (@cd_u + 62), (@cd_c + 9), '동호회', '확정', 'RCPT-20260908-L874DTBK', '2026-09-08 14:00:00', '2026-09-08 15:52:00'
       UNION ALL SELECT (@cd_a + 219), (@cd_sm + 22), (@cd_u + 59), (@cd_c + 9), '동호회', '확정', 'RCPT-20260908-9FDY4DSE', '2026-09-08 14:00:00', '2026-09-08 16:05:00'
       UNION ALL SELECT (@cd_a + 220), (@cd_sm + 22), (@cd_u + 63), (@cd_c + 9), '동호회', '확정', 'RCPT-20260908-BGZPN9EL', '2026-09-08 14:00:00', '2026-09-08 15:40:00'
       UNION ALL SELECT (@cd_a + 221), (@cd_sm + 23), (@cd_u + 36), (@cd_c + 6), '동호회', '확정', 'RCPT-20260910-2DKWEL72', '2026-09-10 16:00:00', '2026-09-10 17:39:00'
       UNION ALL SELECT (@cd_a + 222), (@cd_sm + 23), (@cd_u + 42), (@cd_c + 6), '동호회', '확정', 'RCPT-20260910-SST7GF86', '2026-09-10 16:00:00', '2026-09-10 18:14:00'
       UNION ALL SELECT (@cd_a + 223), (@cd_sm + 23), (@cd_u + 41), (@cd_c + 6), '동호회', '확정', 'RCPT-20260910-YU5WGAB6', '2026-09-10 16:00:00', '2026-09-10 18:47:00'
       UNION ALL SELECT (@cd_a + 224), (@cd_sm + 23), (@cd_u + 38), (@cd_c + 6), '동호회', '확정', 'RCPT-20260910-GZZCVTBK', '2026-09-10 16:00:00', '2026-09-10 18:57:00'
       UNION ALL SELECT (@cd_a + 225), (@cd_sm + 23), (@cd_u + 37), (@cd_c + 6), '동호회', '확정', 'RCPT-20260910-CCPMFCKM', '2026-09-10 16:00:00', '2026-09-10 17:28:00'
       UNION ALL SELECT (@cd_a + 226), (@cd_sm + 23), (@cd_u + 1), (@cd_c + 1), '동호회', '확정', 'RCPT-20260911-7CPVAGCW', '2026-09-11 12:00:00', '2026-09-11 14:32:00'
       UNION ALL SELECT (@cd_a + 227), (@cd_sm + 23), (@cd_u + 6), (@cd_c + 1), '동호회', '확정', 'RCPT-20260911-V7ATDWN6', '2026-09-11 12:00:00', '2026-09-11 14:23:00'
       UNION ALL SELECT (@cd_a + 228), (@cd_sm + 23), (@cd_u + 5), (@cd_c + 1), '동호회', '확정', 'RCPT-20260911-QLGCLEDC', '2026-09-11 12:00:00', '2026-09-11 13:39:00'
       UNION ALL SELECT (@cd_a + 229), (@cd_sm + 23), (@cd_u + 2), (@cd_c + 1), '동호회', '확정', 'RCPT-20260911-63YLYRCR', '2026-09-11 12:00:00', '2026-09-11 14:42:00'
       UNION ALL SELECT (@cd_a + 230), (@cd_sm + 23), (@cd_u + 4), (@cd_c + 1), '동호회', '확정', 'RCPT-20260911-937HX7TM', '2026-09-11 12:00:00', '2026-09-11 13:35:00'
       UNION ALL SELECT (@cd_a + 231), (@cd_sm + 24), (@cd_u + 24), (@cd_c + 4), '동호회', '확정', 'RCPT-20260915-R3GJG6M6', '2026-09-15 17:00:00', '2026-09-15 19:19:00'
       UNION ALL SELECT (@cd_a + 232), (@cd_sm + 24), (@cd_u + 28), (@cd_c + 4), '동호회', '확정', 'RCPT-20260915-FXUM4QWN', '2026-09-15 17:00:00', '2026-09-15 19:07:00'
       UNION ALL SELECT (@cd_a + 233), (@cd_sm + 24), (@cd_u + 27), (@cd_c + 4), '동호회', '확정', 'RCPT-20260915-YQGLXJYB', '2026-09-15 17:00:00', '2026-09-15 19:44:00'
       UNION ALL SELECT (@cd_a + 234), (@cd_sm + 24), (@cd_u + 26), (@cd_c + 4), '동호회', '확정', 'RCPT-20260915-CMSD9Q6P', '2026-09-15 17:00:00', '2026-09-15 19:14:00'
       UNION ALL SELECT (@cd_a + 235), (@cd_sm + 24), (@cd_u + 25), (@cd_c + 4), '동호회', '확정', 'RCPT-20260915-7CX7KP6S', '2026-09-15 17:00:00', '2026-09-15 18:14:00'
       UNION ALL SELECT (@cd_a + 236), (@cd_sm + 24), (@cd_u + 1), (@cd_c + 1), '동호회', '확정', 'RCPT-20260915-PS4TMQCJ', '2026-09-15 11:00:00', '2026-09-15 13:48:00'
       UNION ALL SELECT (@cd_a + 237), (@cd_sm + 24), (@cd_u + 2), (@cd_c + 1), '동호회', '확정', 'RCPT-20260915-JQJ5VZMA', '2026-09-15 11:00:00', '2026-09-15 12:07:00'
       UNION ALL SELECT (@cd_a + 238), (@cd_sm + 24), (@cd_u + 5), (@cd_c + 1), '동호회', '확정', 'RCPT-20260915-2B29VSBD', '2026-09-15 11:00:00', '2026-09-15 13:26:00'
       UNION ALL SELECT (@cd_a + 239), (@cd_sm + 24), (@cd_u + 6), (@cd_c + 1), '동호회', '확정', 'RCPT-20260915-7XC52Q7D', '2026-09-15 11:00:00', '2026-09-15 13:33:00'
       UNION ALL SELECT (@cd_a + 240), (@cd_sm + 24), (@cd_u + 3), (@cd_c + 1), '동호회', '확정', 'RCPT-20260915-HK99PG6M', '2026-09-15 11:00:00', '2026-09-15 13:35:00'
       UNION ALL SELECT (@cd_a + 241), (@cd_sm + 25), (@cd_u + 15), (@cd_c + 3), '동호회', '확정', 'RCPT-20260916-J5JF2JCM', '2026-09-16 19:00:00', '2026-09-16 21:52:00'
       UNION ALL SELECT (@cd_a + 242), (@cd_sm + 25), (@cd_u + 20), (@cd_c + 3), '동호회', '확정', 'RCPT-20260916-NY2C48FP', '2026-09-16 19:00:00', '2026-09-16 20:50:00'
       UNION ALL SELECT (@cd_a + 243), (@cd_sm + 25), (@cd_u + 17), (@cd_c + 3), '동호회', '확정', 'RCPT-20260916-GFHBVWB9', '2026-09-16 19:00:00', '2026-09-16 20:26:00'
       UNION ALL SELECT (@cd_a + 244), (@cd_sm + 25), (@cd_u + 18), (@cd_c + 3), '동호회', '확정', 'RCPT-20260916-ZXFLACPR', '2026-09-16 19:00:00', '2026-09-16 21:00:00'
       UNION ALL SELECT (@cd_a + 245), (@cd_sm + 25), (@cd_u + 16), (@cd_c + 3), '동호회', '확정', 'RCPT-20260916-2FYYA8ND', '2026-09-16 19:00:00', '2026-09-16 20:59:00'
       UNION ALL SELECT (@cd_a + 246), (@cd_sm + 25), (@cd_u + 57), (@cd_c + 9), '동호회', '확정', 'RCPT-20260915-3DZNSWDS', '2026-09-15 20:00:00', '2026-09-15 22:36:00'
       UNION ALL SELECT (@cd_a + 247), (@cd_sm + 25), (@cd_u + 60), (@cd_c + 9), '동호회', '확정', 'RCPT-20260915-WU78YNR5', '2026-09-15 20:00:00', '2026-09-15 21:23:00'
       UNION ALL SELECT (@cd_a + 248), (@cd_sm + 25), (@cd_u + 61), (@cd_c + 9), '동호회', '확정', 'RCPT-20260915-NLEJLDUD', '2026-09-15 20:00:00', '2026-09-15 22:54:00'
       UNION ALL SELECT (@cd_a + 249), (@cd_sm + 25), (@cd_u + 64), (@cd_c + 9), '동호회', '확정', 'RCPT-20260915-776ZC32X', '2026-09-15 20:00:00', '2026-09-15 22:29:00'
       UNION ALL SELECT (@cd_a + 250), (@cd_sm + 25), (@cd_u + 65), (@cd_c + 9), '동호회', '확정', 'RCPT-20260915-3DZDENV6', '2026-09-15 20:00:00', '2026-09-15 22:29:00'
       UNION ALL SELECT (@cd_a + 251), (@cd_sm + 26), (@cd_u + 8), (@cd_c + 2), '동호회', '확정', 'RCPT-20260915-F7TNWC7N', '2026-09-15 21:00:00', '2026-09-15 23:55:00'
       UNION ALL SELECT (@cd_a + 252), (@cd_sm + 26), (@cd_u + 11), (@cd_c + 2), '동호회', '확정', 'RCPT-20260915-QSXPE725', '2026-09-15 21:00:00', '2026-09-15 23:14:00'
       UNION ALL SELECT (@cd_a + 253), (@cd_sm + 26), (@cd_u + 12), (@cd_c + 2), '동호회', '확정', 'RCPT-20260915-8DBN85MQ', '2026-09-15 21:00:00', '2026-09-15 23:57:00'
       UNION ALL SELECT (@cd_a + 254), (@cd_sm + 26), (@cd_u + 9), (@cd_c + 2), '동호회', '확정', 'RCPT-20260915-2ADPYBDS', '2026-09-15 21:00:00', '2026-09-15 23:14:00'
       UNION ALL SELECT (@cd_a + 255), (@cd_sm + 26), (@cd_u + 14), (@cd_c + 2), '동호회', '확정', 'RCPT-20260915-VWECR5SM', '2026-09-15 21:00:00', '2026-09-15 23:50:00'
       UNION ALL SELECT (@cd_a + 256), (@cd_sm + 26), (@cd_u + 36), (@cd_c + 6), '동호회', '확정', 'RCPT-20260915-EA2YDKPY', '2026-09-15 22:00:00', '2026-09-15 23:42:00'
       UNION ALL SELECT (@cd_a + 257), (@cd_sm + 26), (@cd_u + 39), (@cd_c + 6), '동호회', '확정', 'RCPT-20260915-5HJ79MFG', '2026-09-15 22:00:00', '2026-09-16 00:51:00'
       UNION ALL SELECT (@cd_a + 258), (@cd_sm + 26), (@cd_u + 40), (@cd_c + 6), '동호회', '확정', 'RCPT-20260915-259YDB3Y', '2026-09-15 22:00:00', '2026-09-16 00:59:00'
       UNION ALL SELECT (@cd_a + 259), (@cd_sm + 26), (@cd_u + 41), (@cd_c + 6), '동호회', '확정', 'RCPT-20260915-BLHRB2LN', '2026-09-15 22:00:00', '2026-09-15 23:09:00'
       UNION ALL SELECT (@cd_a + 260), (@cd_sm + 26), (@cd_u + 37), (@cd_c + 6), '동호회', '확정', 'RCPT-20260915-YJTBTW9Q', '2026-09-15 22:00:00', '2026-09-16 00:32:00'
       UNION ALL SELECT (@cd_a + 261), (@cd_sm + 27), (@cd_u + 50), (@cd_c + 8), '동호회', '확정', 'RCPT-20260920-XZJSG9ZQ', '2026-09-20 20:00:00', '2026-09-20 22:59:00'
       UNION ALL SELECT (@cd_a + 262), (@cd_sm + 27), (@cd_u + 53), (@cd_c + 8), '동호회', '확정', 'RCPT-20260920-SMB9BUHU', '2026-09-20 20:00:00', '2026-09-20 21:08:00'
       UNION ALL SELECT (@cd_a + 263), (@cd_sm + 27), (@cd_u + 52), (@cd_c + 8), '동호회', '확정', 'RCPT-20260920-N9C7LZPK', '2026-09-20 20:00:00', '2026-09-20 22:38:00'
       UNION ALL SELECT (@cd_a + 264), (@cd_sm + 27), (@cd_u + 55), (@cd_c + 8), '동호회', '확정', 'RCPT-20260920-5SKDM2XG', '2026-09-20 20:00:00', '2026-09-20 21:44:00'
       UNION ALL SELECT (@cd_a + 265), (@cd_sm + 27), (@cd_u + 54), (@cd_c + 8), '동호회', '확정', 'RCPT-20260920-F84ZUZ8R', '2026-09-20 20:00:00', '2026-09-20 22:20:00'
       UNION ALL SELECT (@cd_a + 266), (@cd_sm + 27), (@cd_u + 1), (@cd_c + 1), '동호회', '확정', 'RCPT-20260919-DSKXYGT8', '2026-09-19 20:00:00', '2026-09-19 22:23:00'
       UNION ALL SELECT (@cd_a + 267), (@cd_sm + 27), (@cd_u + 3), (@cd_c + 1), '동호회', '확정', 'RCPT-20260919-22ZAPDJL', '2026-09-19 20:00:00', '2026-09-19 22:23:00'
       UNION ALL SELECT (@cd_a + 268), (@cd_sm + 27), (@cd_u + 2), (@cd_c + 1), '동호회', '확정', 'RCPT-20260919-ZT996N7W', '2026-09-19 20:00:00', '2026-09-19 21:53:00'
       UNION ALL SELECT (@cd_a + 269), (@cd_sm + 27), (@cd_u + 6), (@cd_c + 1), '동호회', '확정', 'RCPT-20260919-7EXR9WW6', '2026-09-19 20:00:00', '2026-09-19 22:55:00'
       UNION ALL SELECT (@cd_a + 270), (@cd_sm + 27), (@cd_u + 4), (@cd_c + 1), '동호회', '확정', 'RCPT-20260919-6YMUGDSR', '2026-09-19 20:00:00', '2026-09-19 22:45:00'
       UNION ALL SELECT (@cd_a + 271), (@cd_sm + 28), (@cd_u + 8), (@cd_c + 2), '동호회', '확정', 'RCPT-20260923-S7EQR6FL', '2026-09-23 02:00:00', '2026-09-23 03:05:00'
       UNION ALL SELECT (@cd_a + 272), (@cd_sm + 28), (@cd_u + 13), (@cd_c + 2), '동호회', '확정', 'RCPT-20260923-N5V5GLHF', '2026-09-23 02:00:00', '2026-09-23 03:58:00'
       UNION ALL SELECT (@cd_a + 273), (@cd_sm + 28), (@cd_u + 11), (@cd_c + 2), '동호회', '확정', 'RCPT-20260923-23JQN4CF', '2026-09-23 02:00:00', '2026-09-23 03:33:00'
       UNION ALL SELECT (@cd_a + 274), (@cd_sm + 28), (@cd_u + 10), (@cd_c + 2), '동호회', '확정', 'RCPT-20260923-MB3S2AWG', '2026-09-23 02:00:00', '2026-09-23 04:53:00'
       UNION ALL SELECT (@cd_a + 275), (@cd_sm + 28), (@cd_u + 12), (@cd_c + 2), '동호회', '확정', 'RCPT-20260923-SWPJKYC6', '2026-09-23 02:00:00', '2026-09-23 03:42:00'
       UNION ALL SELECT (@cd_a + 276), (@cd_sm + 28), (@cd_u + 67), (@cd_c + 10), '동호회', '확정', 'RCPT-20260922-NLE2LBR8', '2026-09-22 07:00:00', '2026-09-22 09:33:00'
       UNION ALL SELECT (@cd_a + 277), (@cd_sm + 28), (@cd_u + 71), (@cd_c + 10), '동호회', '확정', 'RCPT-20260922-RF5N7YER', '2026-09-22 07:00:00', '2026-09-22 08:46:00'
       UNION ALL SELECT (@cd_a + 278), (@cd_sm + 28), (@cd_u + 72), (@cd_c + 10), '동호회', '확정', 'RCPT-20260922-U868LL3X', '2026-09-22 07:00:00', '2026-09-22 09:55:00'
       UNION ALL SELECT (@cd_a + 279), (@cd_sm + 28), (@cd_u + 73), (@cd_c + 10), '동호회', '확정', 'RCPT-20260922-6HQLAX93', '2026-09-22 07:00:00', '2026-09-22 09:50:00'
       UNION ALL SELECT (@cd_a + 280), (@cd_sm + 28), (@cd_u + 69), (@cd_c + 10), '동호회', '확정', 'RCPT-20260922-2AK86W7T', '2026-09-22 07:00:00', '2026-09-22 10:00:00'
       UNION ALL SELECT (@cd_a + 281), (@cd_sm + 29), (@cd_u + 43), (@cd_c + 7), '동호회', '확정', 'RCPT-20260922-UEQ549V9', '2026-09-22 09:00:00', '2026-09-22 11:11:00'
       UNION ALL SELECT (@cd_a + 282), (@cd_sm + 29), (@cd_u + 49), (@cd_c + 7), '동호회', '확정', 'RCPT-20260922-FGXDEAAU', '2026-09-22 09:00:00', '2026-09-22 10:26:00'
       UNION ALL SELECT (@cd_a + 283), (@cd_sm + 29), (@cd_u + 48), (@cd_c + 7), '동호회', '확정', 'RCPT-20260922-82XJV8TP', '2026-09-22 09:00:00', '2026-09-22 11:36:00'
       UNION ALL SELECT (@cd_a + 284), (@cd_sm + 29), (@cd_u + 46), (@cd_c + 7), '동호회', '확정', 'RCPT-20260922-EQPQLXJV', '2026-09-22 09:00:00', '2026-09-22 10:29:00'
       UNION ALL SELECT (@cd_a + 285), (@cd_sm + 29), (@cd_u + 47), (@cd_c + 7), '동호회', '확정', 'RCPT-20260922-78MB6NMU', '2026-09-22 09:00:00', '2026-09-22 11:13:00'
       UNION ALL SELECT (@cd_a + 286), (@cd_sm + 29), (@cd_u + 57), (@cd_c + 9), '동호회', '확정', 'RCPT-20260924-G5NUANLV', '2026-09-24 14:00:00', '2026-09-24 16:03:00'
       UNION ALL SELECT (@cd_a + 287), (@cd_sm + 29), (@cd_u + 59), (@cd_c + 9), '동호회', '확정', 'RCPT-20260924-B792VGTX', '2026-09-24 14:00:00', '2026-09-24 15:26:00'
       UNION ALL SELECT (@cd_a + 288), (@cd_sm + 29), (@cd_u + 63), (@cd_c + 9), '동호회', '확정', 'RCPT-20260924-NWDT9M6M', '2026-09-24 14:00:00', '2026-09-24 15:14:00'
       UNION ALL SELECT (@cd_a + 289), (@cd_sm + 29), (@cd_u + 64), (@cd_c + 9), '동호회', '확정', 'RCPT-20260924-D63UEF2E', '2026-09-24 14:00:00', '2026-09-24 15:37:00'
       UNION ALL SELECT (@cd_a + 290), (@cd_sm + 29), (@cd_u + 58), (@cd_c + 9), '동호회', '확정', 'RCPT-20260924-UPXK6TBC', '2026-09-24 14:00:00', '2026-09-24 15:41:00'
       UNION ALL SELECT (@cd_a + 291), (@cd_sm + 30), (@cd_u + 1), (@cd_c + 1), '동호회', '확정', 'RCPT-20260924-DHK4ECR8', '2026-09-24 04:00:00', '2026-09-24 06:48:00'
       UNION ALL SELECT (@cd_a + 292), (@cd_sm + 30), (@cd_u + 5), (@cd_c + 1), '동호회', '확정', 'RCPT-20260924-HMWDCQEV', '2026-09-24 04:00:00', '2026-09-24 06:01:00'
       UNION ALL SELECT (@cd_a + 293), (@cd_sm + 30), (@cd_u + 3), (@cd_c + 1), '동호회', '확정', 'RCPT-20260924-4MWSWSCF', '2026-09-24 04:00:00', '2026-09-24 05:31:00'
       UNION ALL SELECT (@cd_a + 294), (@cd_sm + 30), (@cd_u + 4), (@cd_c + 1), '동호회', '확정', 'RCPT-20260924-PNTE7DC4', '2026-09-24 04:00:00', '2026-09-24 05:46:00'
       UNION ALL SELECT (@cd_a + 295), (@cd_sm + 30), (@cd_u + 2), (@cd_c + 1), '동호회', '확정', 'RCPT-20260924-Q38CFSWB', '2026-09-24 04:00:00', '2026-09-24 06:09:00'
       UNION ALL SELECT (@cd_a + 296), (@cd_sm + 30), (@cd_u + 42), (@cd_c + 6), '동호회', '확정', 'RCPT-20260924-7AELVW67', '2026-09-24 15:00:00', '2026-09-24 16:15:00'
       UNION ALL SELECT (@cd_a + 297), (@cd_sm + 30), (@cd_u + 38), (@cd_c + 6), '동호회', '확정', 'RCPT-20260924-AFR9QQLT', '2026-09-24 15:00:00', '2026-09-24 17:50:00'
       UNION ALL SELECT (@cd_a + 298), (@cd_sm + 30), (@cd_u + 40), (@cd_c + 6), '동호회', '확정', 'RCPT-20260924-2CBHAK2J', '2026-09-24 15:00:00', '2026-09-24 16:52:00'
       UNION ALL SELECT (@cd_a + 299), (@cd_sm + 30), (@cd_u + 39), (@cd_c + 6), '동호회', '확정', 'RCPT-20260924-GDJQ9UUE', '2026-09-24 15:00:00', '2026-09-24 17:26:00'
       UNION ALL SELECT (@cd_a + 300), (@cd_sm + 30), (@cd_u + 37), (@cd_c + 6), '동호회', '확정', 'RCPT-20260924-2LB8EPH8', '2026-09-24 15:00:00', '2026-09-24 16:43:00'
       UNION ALL SELECT (@cd_a + 301), (@cd_sm + 31), (@cd_u + 22), (@cd_c + 4), '동호회', '확정', 'RCPT-20260928-QC8KVAAK', '2026-09-28 14:00:00', '2026-09-28 16:10:00'
       UNION ALL SELECT (@cd_a + 302), (@cd_sm + 31), (@cd_u + 28), (@cd_c + 4), '동호회', '확정', 'RCPT-20260928-SMXLVEW4', '2026-09-28 14:00:00', '2026-09-28 15:51:00'
       UNION ALL SELECT (@cd_a + 303), (@cd_sm + 31), (@cd_u + 23), (@cd_c + 4), '동호회', '확정', 'RCPT-20260928-MWCH6K6V', '2026-09-28 14:00:00', '2026-09-28 16:18:00'
       UNION ALL SELECT (@cd_a + 304), (@cd_sm + 31), (@cd_u + 24), (@cd_c + 4), '동호회', '확정', 'RCPT-20260928-PVYS9CNG', '2026-09-28 14:00:00', '2026-09-28 15:23:00'
       UNION ALL SELECT (@cd_a + 305), (@cd_sm + 31), (@cd_u + 26), (@cd_c + 4), '동호회', '확정', 'RCPT-20260928-39UDPSBK', '2026-09-28 14:00:00', '2026-09-28 15:38:00'
       UNION ALL SELECT (@cd_a + 306), (@cd_sm + 31), (@cd_u + 36), (@cd_c + 6), '동호회', '확정', 'RCPT-20260929-HJMT9WBB', '2026-09-29 10:00:00', '2026-09-29 12:43:00'
       UNION ALL SELECT (@cd_a + 307), (@cd_sm + 31), (@cd_u + 42), (@cd_c + 6), '동호회', '확정', 'RCPT-20260929-38FSH5X2', '2026-09-29 10:00:00', '2026-09-29 11:36:00'
       UNION ALL SELECT (@cd_a + 308), (@cd_sm + 31), (@cd_u + 41), (@cd_c + 6), '동호회', '확정', 'RCPT-20260929-WFAS3FQC', '2026-09-29 10:00:00', '2026-09-29 11:12:00'
       UNION ALL SELECT (@cd_a + 309), (@cd_sm + 31), (@cd_u + 38), (@cd_c + 6), '동호회', '확정', 'RCPT-20260929-D95CH3YT', '2026-09-29 10:00:00', '2026-09-29 11:18:00'
       UNION ALL SELECT (@cd_a + 310), (@cd_sm + 31), (@cd_u + 39), (@cd_c + 6), '동호회', '확정', 'RCPT-20260929-SQPU73D8', '2026-09-29 10:00:00', '2026-09-29 12:17:00'
       UNION ALL SELECT (@cd_a + 311), (@cd_sm + 32), (@cd_u + 55), (@cd_c + 8), '동호회', '확정', 'RCPT-20260928-7WQ9G3CK', '2026-09-28 20:00:00', '2026-09-28 21:12:00'
       UNION ALL SELECT (@cd_a + 312), (@cd_sm + 32), (@cd_u + 53), (@cd_c + 8), '동호회', '확정', 'RCPT-20260928-7H6WBZHJ', '2026-09-28 20:00:00', '2026-09-28 22:24:00'
       UNION ALL SELECT (@cd_a + 313), (@cd_sm + 32), (@cd_u + 56), (@cd_c + 8), '동호회', '확정', 'RCPT-20260928-KA7MFP3G', '2026-09-28 20:00:00', '2026-09-28 21:07:00'
       UNION ALL SELECT (@cd_a + 314), (@cd_sm + 32), (@cd_u + 51), (@cd_c + 8), '동호회', '확정', 'RCPT-20260928-C5MGHAL4', '2026-09-28 20:00:00', '2026-09-28 22:13:00'
       UNION ALL SELECT (@cd_a + 315), (@cd_sm + 32), (@cd_u + 52), (@cd_c + 8), '동호회', '확정', 'RCPT-20260928-XUL359KA', '2026-09-28 20:00:00', '2026-09-28 22:30:00'
       UNION ALL SELECT (@cd_a + 316), (@cd_sm + 32), (@cd_u + 1), (@cd_c + 1), '동호회', '확정', 'RCPT-20260929-VFPANRQ3', '2026-09-29 15:00:00', '2026-09-29 17:17:00'
       UNION ALL SELECT (@cd_a + 317), (@cd_sm + 32), (@cd_u + 5), (@cd_c + 1), '동호회', '확정', 'RCPT-20260929-Z5J9R72K', '2026-09-29 15:00:00', '2026-09-29 17:57:00'
       UNION ALL SELECT (@cd_a + 318), (@cd_sm + 32), (@cd_u + 6), (@cd_c + 1), '동호회', '확정', 'RCPT-20260929-U6Z7NJNR', '2026-09-29 15:00:00', '2026-09-29 16:04:00'
       UNION ALL SELECT (@cd_a + 319), (@cd_sm + 32), (@cd_u + 2), (@cd_c + 1), '동호회', '확정', 'RCPT-20260929-6ND3NJKV', '2026-09-29 15:00:00', '2026-09-29 17:54:00'
       UNION ALL SELECT (@cd_a + 320), (@cd_sm + 32), (@cd_u + 3), (@cd_c + 1), '동호회', '확정', 'RCPT-20260929-6W7PEHGF', '2026-09-29 15:00:00', '2026-09-29 16:16:00'
       UNION ALL SELECT (@cd_a + 321), (@cd_sm + 33), (@cd_u + 32), (@cd_c + 5), '동호회', '확정', 'RCPT-20260930-R9XWX6AF', '2026-09-30 01:00:00', '2026-09-30 02:04:00'
       UNION ALL SELECT (@cd_a + 322), (@cd_sm + 33), (@cd_u + 35), (@cd_c + 5), '동호회', '확정', 'RCPT-20260930-SALZ6V78', '2026-09-30 01:00:00', '2026-09-30 03:39:00'
       UNION ALL SELECT (@cd_a + 323), (@cd_sm + 33), (@cd_u + 31), (@cd_c + 5), '동호회', '확정', 'RCPT-20260930-S6SE7Y67', '2026-09-30 01:00:00', '2026-09-30 03:15:00'
       UNION ALL SELECT (@cd_a + 324), (@cd_sm + 33), (@cd_u + 33), (@cd_c + 5), '동호회', '확정', 'RCPT-20260930-B564AQU7', '2026-09-30 01:00:00', '2026-09-30 03:13:00'
       UNION ALL SELECT (@cd_a + 325), (@cd_sm + 33), (@cd_u + 30), (@cd_c + 5), '동호회', '확정', 'RCPT-20260930-85WX8LS8', '2026-09-30 01:00:00', '2026-09-30 03:38:00'
       UNION ALL SELECT (@cd_a + 326), (@cd_sm + 33), (@cd_u + 67), (@cd_c + 10), '동호회', '확정', 'RCPT-20260930-8VRNSR2L', '2026-09-30 03:00:00', '2026-09-30 04:50:00'
       UNION ALL SELECT (@cd_a + 327), (@cd_sm + 33), (@cd_u + 72), (@cd_c + 10), '동호회', '확정', 'RCPT-20260930-4VPE23KU', '2026-09-30 03:00:00', '2026-09-30 05:03:00'
       UNION ALL SELECT (@cd_a + 328), (@cd_sm + 33), (@cd_u + 69), (@cd_c + 10), '동호회', '확정', 'RCPT-20260930-96JL2UWX', '2026-09-30 03:00:00', '2026-09-30 05:19:00'
       UNION ALL SELECT (@cd_a + 329), (@cd_sm + 33), (@cd_u + 71), (@cd_c + 10), '동호회', '확정', 'RCPT-20260930-XYBTCQ6Z', '2026-09-30 03:00:00', '2026-09-30 04:38:00'
       UNION ALL SELECT (@cd_a + 330), (@cd_sm + 33), (@cd_u + 74), (@cd_c + 10), '동호회', '확정', 'RCPT-20260930-YES22T7Q', '2026-09-30 03:00:00', '2026-09-30 05:02:00'
       UNION ALL SELECT (@cd_a + 331), (@cd_sm + 34), (@cd_u + 6), (@cd_c + 1), '동호회', '확정', 'RCPT-20261004-XMT6AQKH', '2026-10-04 10:00:00', '2026-10-04 11:38:00'
       UNION ALL SELECT (@cd_a + 332), (@cd_sm + 34), (@cd_u + 4), (@cd_c + 1), '동호회', '확정', 'RCPT-20261004-D4U34DWZ', '2026-10-04 10:00:00', '2026-10-04 12:17:00'
       UNION ALL SELECT (@cd_a + 333), (@cd_sm + 34), (@cd_u + 5), (@cd_c + 1), '동호회', '확정', 'RCPT-20261004-VVLMN4EA', '2026-10-04 10:00:00', '2026-10-04 11:23:00'
       UNION ALL SELECT (@cd_a + 334), (@cd_sm + 34), (@cd_u + 2), (@cd_c + 1), '동호회', '확정', 'RCPT-20261004-A3UHE8QT', '2026-10-04 10:00:00', '2026-10-04 11:43:00'
       UNION ALL SELECT (@cd_a + 335), (@cd_sm + 34), (@cd_u + 3), (@cd_c + 1), '동호회', '확정', 'RCPT-20261004-PHFWRYA2', '2026-10-04 10:00:00', '2026-10-04 12:17:00'
       UNION ALL SELECT (@cd_a + 336), (@cd_sm + 34), (@cd_u + 57), (@cd_c + 9), '동호회', '확정', 'RCPT-20261003-PYE66P67', '2026-10-03 19:00:00', '2026-10-03 22:00:00'
       UNION ALL SELECT (@cd_a + 337), (@cd_sm + 34), (@cd_u + 61), (@cd_c + 9), '동호회', '확정', 'RCPT-20261003-ZT872JPR', '2026-10-03 19:00:00', '2026-10-03 20:30:00'
       UNION ALL SELECT (@cd_a + 338), (@cd_sm + 34), (@cd_u + 65), (@cd_c + 9), '동호회', '확정', 'RCPT-20261003-92UGREAL', '2026-10-03 19:00:00', '2026-10-03 20:12:00'
       UNION ALL SELECT (@cd_a + 339), (@cd_sm + 34), (@cd_u + 62), (@cd_c + 9), '동호회', '확정', 'RCPT-20261003-GPFX5P2F', '2026-10-03 19:00:00', '2026-10-03 21:05:00'
       UNION ALL SELECT (@cd_a + 340), (@cd_sm + 34), (@cd_u + 60), (@cd_c + 9), '동호회', '확정', 'RCPT-20261003-DC64EXDK', '2026-10-03 19:00:00', '2026-10-03 20:18:00'
       UNION ALL SELECT (@cd_a + 341), (@cd_sm + 35), (@cd_u + 15), (@cd_c + 3), '동호회', '확정', 'RCPT-20261006-AEX8AXWR', '2026-10-06 14:00:00', '2026-10-06 15:23:00'
       UNION ALL SELECT (@cd_a + 342), (@cd_sm + 35), (@cd_u + 17), (@cd_c + 3), '동호회', '확정', 'RCPT-20261006-HYUMM962', '2026-10-06 14:00:00', '2026-10-06 15:51:00'
       UNION ALL SELECT (@cd_a + 343), (@cd_sm + 35), (@cd_u + 18), (@cd_c + 3), '동호회', '확정', 'RCPT-20261006-MXEMPBPM', '2026-10-06 14:00:00', '2026-10-06 16:42:00'
       UNION ALL SELECT (@cd_a + 344), (@cd_sm + 35), (@cd_u + 19), (@cd_c + 3), '동호회', '확정', 'RCPT-20261006-PJTR6922', '2026-10-06 14:00:00', '2026-10-06 16:18:00'
       UNION ALL SELECT (@cd_a + 345), (@cd_sm + 35), (@cd_u + 20), (@cd_c + 3), '동호회', '확정', 'RCPT-20261006-S9QLQARS', '2026-10-06 14:00:00', '2026-10-06 16:29:00'
       UNION ALL SELECT (@cd_a + 346), (@cd_sm + 35), (@cd_u + 36), (@cd_c + 6), '동호회', '확정', 'RCPT-20261007-5XU437X4', '2026-10-07 17:00:00', '2026-10-07 18:48:00'
       UNION ALL SELECT (@cd_a + 347), (@cd_sm + 35), (@cd_u + 40), (@cd_c + 6), '동호회', '확정', 'RCPT-20261007-H7Y4CK8Q', '2026-10-07 17:00:00', '2026-10-07 18:09:00'
       UNION ALL SELECT (@cd_a + 348), (@cd_sm + 35), (@cd_u + 38), (@cd_c + 6), '동호회', '확정', 'RCPT-20261007-ZMCNP9U7', '2026-10-07 17:00:00', '2026-10-07 18:36:00'
       UNION ALL SELECT (@cd_a + 349), (@cd_sm + 35), (@cd_u + 42), (@cd_c + 6), '동호회', '확정', 'RCPT-20261007-FM76N67Y', '2026-10-07 17:00:00', '2026-10-07 18:03:00'
       UNION ALL SELECT (@cd_a + 350), (@cd_sm + 35), (@cd_u + 41), (@cd_c + 6), '동호회', '확정', 'RCPT-20261007-NMH6VTWA', '2026-10-07 17:00:00', '2026-10-07 18:34:00'
       UNION ALL SELECT (@cd_a + 351), (@cd_sm + 36), (@cd_u + 9), (@cd_c + 2), '동호회', '확정', 'RCPT-20261006-SZ56D2JG', '2026-10-06 04:00:00', '2026-10-06 05:09:00'
       UNION ALL SELECT (@cd_a + 352), (@cd_sm + 36), (@cd_u + 11), (@cd_c + 2), '동호회', '확정', 'RCPT-20261006-7VWZR5KW', '2026-10-06 04:00:00', '2026-10-06 05:37:00'
       UNION ALL SELECT (@cd_a + 353), (@cd_sm + 36), (@cd_u + 10), (@cd_c + 2), '동호회', '확정', 'RCPT-20261006-PDGK8F4H', '2026-10-06 04:00:00', '2026-10-06 06:32:00'
       UNION ALL SELECT (@cd_a + 354), (@cd_sm + 36), (@cd_u + 14), (@cd_c + 2), '동호회', '확정', 'RCPT-20261006-3RAEH2Q3', '2026-10-06 04:00:00', '2026-10-06 05:35:00'
       UNION ALL SELECT (@cd_a + 355), (@cd_sm + 36), (@cd_u + 13), (@cd_c + 2), '동호회', '확정', 'RCPT-20261006-V5NUANR2', '2026-10-06 04:00:00', '2026-10-06 06:23:00'
       UNION ALL SELECT (@cd_a + 356), (@cd_sm + 36), (@cd_u + 1), (@cd_c + 1), '동호회', '확정', 'RCPT-20261009-E9S988YJ', '2026-10-09 03:00:00', '2026-10-09 04:31:00'
       UNION ALL SELECT (@cd_a + 357), (@cd_sm + 36), (@cd_u + 4), (@cd_c + 1), '동호회', '확정', 'RCPT-20261009-2AFEES3F', '2026-10-09 03:00:00', '2026-10-09 05:28:00'
       UNION ALL SELECT (@cd_a + 358), (@cd_sm + 36), (@cd_u + 5), (@cd_c + 1), '동호회', '확정', 'RCPT-20261009-H37WXWA6', '2026-10-09 03:00:00', '2026-10-09 04:26:00'
       UNION ALL SELECT (@cd_a + 359), (@cd_sm + 36), (@cd_u + 6), (@cd_c + 1), '동호회', '확정', 'RCPT-20261009-JHBM53HK', '2026-10-09 03:00:00', '2026-10-09 04:09:00'
       UNION ALL SELECT (@cd_a + 360), (@cd_sm + 36), (@cd_u + 2), (@cd_c + 1), '동호회', '확정', 'RCPT-20261009-WRKFNTRY', '2026-10-09 03:00:00', '2026-10-09 04:11:00'
       UNION ALL SELECT (@cd_a + 361), (@cd_sm + 37), (@cd_u + 46), (@cd_c + 7), '동호회', '확정', 'RCPT-20261007-BVA9HNW6', '2026-10-07 07:00:00', '2026-10-07 08:44:00'
       UNION ALL SELECT (@cd_a + 362), (@cd_sm + 37), (@cd_u + 49), (@cd_c + 7), '동호회', '확정', 'RCPT-20261007-TFF2N2L6', '2026-10-07 07:00:00', '2026-10-07 08:11:00'
       UNION ALL SELECT (@cd_a + 363), (@cd_sm + 37), (@cd_u + 47), (@cd_c + 7), '동호회', '확정', 'RCPT-20261007-JRZZKT4J', '2026-10-07 07:00:00', '2026-10-07 09:47:00'
       UNION ALL SELECT (@cd_a + 364), (@cd_sm + 37), (@cd_u + 44), (@cd_c + 7), '동호회', '확정', 'RCPT-20261007-G5EA2JP5', '2026-10-07 07:00:00', '2026-10-07 09:25:00'
       UNION ALL SELECT (@cd_a + 365), (@cd_sm + 37), (@cd_u + 48), (@cd_c + 7), '동호회', '확정', 'RCPT-20261007-WR89674H', '2026-10-07 07:00:00', '2026-10-07 08:39:00'
       UNION ALL SELECT (@cd_a + 366), (@cd_sm + 37), (@cd_u + 29), (@cd_c + 5), '동호회', '확정', 'RCPT-20261007-SZJXHPJE', '2026-10-07 12:00:00', '2026-10-07 13:59:00'
       UNION ALL SELECT (@cd_a + 367), (@cd_sm + 37), (@cd_u + 35), (@cd_c + 5), '동호회', '확정', 'RCPT-20261007-GSPTQ6VN', '2026-10-07 12:00:00', '2026-10-07 14:58:00'
       UNION ALL SELECT (@cd_a + 368), (@cd_sm + 37), (@cd_u + 31), (@cd_c + 5), '동호회', '확정', 'RCPT-20261007-LY4ZLXUR', '2026-10-07 12:00:00', '2026-10-07 13:52:00'
       UNION ALL SELECT (@cd_a + 369), (@cd_sm + 37), (@cd_u + 32), (@cd_c + 5), '동호회', '확정', 'RCPT-20261007-F7MTUMNV', '2026-10-07 12:00:00', '2026-10-07 14:46:00'
       UNION ALL SELECT (@cd_a + 370), (@cd_sm + 37), (@cd_u + 33), (@cd_c + 5), '동호회', '확정', 'RCPT-20261007-D2ZQVLWV', '2026-10-07 12:00:00', '2026-10-07 13:08:00'
       UNION ALL SELECT (@cd_a + 371), (@cd_sm + 38), (@cd_u + 50), (@cd_c + 8), '동호회', '확정', 'RCPT-20261009-V65SUVHL', '2026-10-09 17:00:00', '2026-10-09 18:42:00'
       UNION ALL SELECT (@cd_a + 372), (@cd_sm + 38), (@cd_u + 54), (@cd_c + 8), '동호회', '확정', 'RCPT-20261009-8B5PPQES', '2026-10-09 17:00:00', '2026-10-09 19:38:00'
       UNION ALL SELECT (@cd_a + 373), (@cd_sm + 38), (@cd_u + 51), (@cd_c + 8), '동호회', '확정', 'RCPT-20261009-Q32647WZ', '2026-10-09 17:00:00', '2026-10-09 18:23:00'
       UNION ALL SELECT (@cd_a + 374), (@cd_sm + 38), (@cd_u + 53), (@cd_c + 8), '동호회', '확정', 'RCPT-20261009-XDE5XFW6', '2026-10-09 17:00:00', '2026-10-09 19:36:00'
       UNION ALL SELECT (@cd_a + 375), (@cd_sm + 38), (@cd_u + 56), (@cd_c + 8), '동호회', '확정', 'RCPT-20261009-CCCGDT8T', '2026-10-09 17:00:00', '2026-10-09 18:17:00'
       UNION ALL SELECT (@cd_a + 376), (@cd_sm + 38), (@cd_u + 31), (@cd_c + 5), '동호회', '확정', 'RCPT-20261011-6G6NN3UJ', '2026-10-11 21:00:00', '2026-10-11 23:19:00'
       UNION ALL SELECT (@cd_a + 377), (@cd_sm + 38), (@cd_u + 35), (@cd_c + 5), '동호회', '확정', 'RCPT-20261011-7TBSXFDK', '2026-10-11 21:00:00', '2026-10-11 23:41:00'
       UNION ALL SELECT (@cd_a + 378), (@cd_sm + 38), (@cd_u + 34), (@cd_c + 5), '동호회', '확정', 'RCPT-20261011-UNRJYAF9', '2026-10-11 21:00:00', '2026-10-11 22:09:00'
       UNION ALL SELECT (@cd_a + 379), (@cd_sm + 38), (@cd_u + 30), (@cd_c + 5), '동호회', '확정', 'RCPT-20261011-UTXHXQZM', '2026-10-11 21:00:00', '2026-10-11 22:54:00'
       UNION ALL SELECT (@cd_a + 380), (@cd_sm + 38), (@cd_u + 33), (@cd_c + 5), '동호회', '확정', 'RCPT-20261011-MATT9XFQ', '2026-10-11 21:00:00', '2026-10-11 22:47:00'
       UNION ALL SELECT (@cd_a + 381), (@cd_sm + 39), (@cd_u + 1), (@cd_c + 1), '동호회', '확정', 'RCPT-20261011-8FTHDNMM', '2026-10-11 16:00:00', '2026-10-11 18:58:00'
       UNION ALL SELECT (@cd_a + 382), (@cd_sm + 39), (@cd_u + 6), (@cd_c + 1), '동호회', '확정', 'RCPT-20261011-SX9LAP4P', '2026-10-11 16:00:00', '2026-10-11 17:20:00'
       UNION ALL SELECT (@cd_a + 383), (@cd_sm + 39), (@cd_u + 4), (@cd_c + 1), '동호회', '확정', 'RCPT-20261011-UX9VTVHV', '2026-10-11 16:00:00', '2026-10-11 18:33:00'
       UNION ALL SELECT (@cd_a + 384), (@cd_sm + 39), (@cd_u + 3), (@cd_c + 1), '동호회', '확정', 'RCPT-20261011-YM6N7WZY', '2026-10-11 16:00:00', '2026-10-11 17:07:00'
       UNION ALL SELECT (@cd_a + 385), (@cd_sm + 39), (@cd_u + 5), (@cd_c + 1), '동호회', '확정', 'RCPT-20261011-6V8M99X8', '2026-10-11 16:00:00', '2026-10-11 18:57:00'
       UNION ALL SELECT (@cd_a + 386), (@cd_sm + 39), (@cd_u + 15), (@cd_c + 3), '동호회', '확정', 'RCPT-20261012-SFQH3KWX', '2026-10-12 18:00:00', '2026-10-12 20:22:00'
       UNION ALL SELECT (@cd_a + 387), (@cd_sm + 39), (@cd_u + 21), (@cd_c + 3), '동호회', '확정', 'RCPT-20261012-33NJF739', '2026-10-12 18:00:00', '2026-10-12 20:43:00'
       UNION ALL SELECT (@cd_a + 388), (@cd_sm + 39), (@cd_u + 20), (@cd_c + 3), '동호회', '확정', 'RCPT-20261012-PUFEXZ8E', '2026-10-12 18:00:00', '2026-10-12 20:47:00'
       UNION ALL SELECT (@cd_a + 389), (@cd_sm + 39), (@cd_u + 17), (@cd_c + 3), '동호회', '확정', 'RCPT-20261012-N2J4SLKS', '2026-10-12 18:00:00', '2026-10-12 20:34:00'
       UNION ALL SELECT (@cd_a + 390), (@cd_sm + 39), (@cd_u + 18), (@cd_c + 3), '동호회', '확정', 'RCPT-20261012-GD6XXFBH', '2026-10-12 18:00:00', '2026-10-12 20:04:00'
       UNION ALL SELECT (@cd_a + 391), (@cd_sm + 40), (@cd_u + 22), (@cd_c + 4), '동호회', '확정', 'RCPT-20261011-6YW2BWA9', '2026-10-11 19:00:00', '2026-10-11 21:22:00'
       UNION ALL SELECT (@cd_a + 392), (@cd_sm + 40), (@cd_u + 25), (@cd_c + 4), '동호회', '확정', 'RCPT-20261011-9WQZXKH9', '2026-10-11 19:00:00', '2026-10-11 20:47:00'
       UNION ALL SELECT (@cd_a + 393), (@cd_sm + 40), (@cd_u + 27), (@cd_c + 4), '동호회', '확정', 'RCPT-20261011-YHR6SPJM', '2026-10-11 19:00:00', '2026-10-11 21:53:00'
       UNION ALL SELECT (@cd_a + 394), (@cd_sm + 40), (@cd_u + 23), (@cd_c + 4), '동호회', '확정', 'RCPT-20261011-P2JXZ8HU', '2026-10-11 19:00:00', '2026-10-11 21:11:00'
       UNION ALL SELECT (@cd_a + 395), (@cd_sm + 40), (@cd_u + 28), (@cd_c + 4), '동호회', '확정', 'RCPT-20261011-WT8PHLCP', '2026-10-11 19:00:00', '2026-10-11 20:33:00'
       UNION ALL SELECT (@cd_a + 396), (@cd_sm + 40), (@cd_u + 57), (@cd_c + 9), '동호회', '확정', 'RCPT-20261013-9CTJMZ66', '2026-10-13 17:00:00', '2026-10-13 18:48:00'
       UNION ALL SELECT (@cd_a + 397), (@cd_sm + 40), (@cd_u + 64), (@cd_c + 9), '동호회', '확정', 'RCPT-20261013-73472TV7', '2026-10-13 17:00:00', '2026-10-13 18:04:00'
       UNION ALL SELECT (@cd_a + 398), (@cd_sm + 40), (@cd_u + 60), (@cd_c + 9), '동호회', '확정', 'RCPT-20261013-88AQ2JV4', '2026-10-13 17:00:00', '2026-10-13 18:41:00'
       UNION ALL SELECT (@cd_a + 399), (@cd_sm + 40), (@cd_u + 58), (@cd_c + 9), '동호회', '확정', 'RCPT-20261013-EBM2723X', '2026-10-13 17:00:00', '2026-10-13 19:41:00'
       UNION ALL SELECT (@cd_a + 400), (@cd_sm + 40), (@cd_u + 61), (@cd_c + 9), '동호회', '확정', 'RCPT-20261013-479JH9AF', '2026-10-13 17:00:00', '2026-10-13 19:08:00'
       UNION ALL SELECT (@cd_a + 401), (@cd_sm + 41), (@cd_u + 43), (@cd_c + 7), '동호회', '확정', 'RCPT-20261014-RUAV86B7', '2026-10-14 06:00:00', '2026-10-14 07:50:00'
       UNION ALL SELECT (@cd_a + 402), (@cd_sm + 41), (@cd_u + 49), (@cd_c + 7), '동호회', '확정', 'RCPT-20261014-XV4NLVNH', '2026-10-14 06:00:00', '2026-10-14 07:22:00'
       UNION ALL SELECT (@cd_a + 403), (@cd_sm + 41), (@cd_u + 45), (@cd_c + 7), '동호회', '확정', 'RCPT-20261014-WY67PZKY', '2026-10-14 06:00:00', '2026-10-14 08:36:00'
       UNION ALL SELECT (@cd_a + 404), (@cd_sm + 41), (@cd_u + 44), (@cd_c + 7), '동호회', '확정', 'RCPT-20261014-KGJA4R94', '2026-10-14 06:00:00', '2026-10-14 07:04:00'
       UNION ALL SELECT (@cd_a + 405), (@cd_sm + 41), (@cd_u + 47), (@cd_c + 7), '동호회', '확정', 'RCPT-20261014-RKLJBY4A', '2026-10-14 06:00:00', '2026-10-14 08:05:00'
       UNION ALL SELECT (@cd_a + 406), (@cd_sm + 41), (@cd_u + 67), (@cd_c + 10), '동호회', '확정', 'RCPT-20261014-3QQYV3SM', '2026-10-14 04:00:00', '2026-10-14 06:26:00'
       UNION ALL SELECT (@cd_a + 407), (@cd_sm + 41), (@cd_u + 68), (@cd_c + 10), '동호회', '확정', 'RCPT-20261014-FK578HLK', '2026-10-14 04:00:00', '2026-10-14 06:55:00'
       UNION ALL SELECT (@cd_a + 408), (@cd_sm + 41), (@cd_u + 75), (@cd_c + 10), '동호회', '확정', 'RCPT-20261014-J244LSV2', '2026-10-14 04:00:00', '2026-10-14 06:59:00'
       UNION ALL SELECT (@cd_a + 409), (@cd_sm + 41), (@cd_u + 73), (@cd_c + 10), '동호회', '확정', 'RCPT-20261014-4VZY63MT', '2026-10-14 04:00:00', '2026-10-14 06:08:00'
       UNION ALL SELECT (@cd_a + 410), (@cd_sm + 41), (@cd_u + 70), (@cd_c + 10), '동호회', '확정', 'RCPT-20261014-7JEH3SEP', '2026-10-14 04:00:00', '2026-10-14 06:54:00'
       UNION ALL SELECT (@cd_a + 411), (@cd_sm + 42), (@cd_u + 27), (@cd_c + 4), '동호회', '확정', 'RCPT-20261019-ZLJXGMM9', '2026-10-19 12:00:00', '2026-10-19 14:58:00'
       UNION ALL SELECT (@cd_a + 412), (@cd_sm + 42), (@cd_u + 25), (@cd_c + 4), '동호회', '확정', 'RCPT-20261019-YLW7WAAM', '2026-10-19 12:00:00', '2026-10-19 14:00:00'
       UNION ALL SELECT (@cd_a + 413), (@cd_sm + 42), (@cd_u + 26), (@cd_c + 4), '동호회', '확정', 'RCPT-20261019-VH3UF624', '2026-10-19 12:00:00', '2026-10-19 15:00:00'
       UNION ALL SELECT (@cd_a + 414), (@cd_sm + 42), (@cd_u + 28), (@cd_c + 4), '동호회', '확정', 'RCPT-20261019-EQHSF7TD', '2026-10-19 12:00:00', '2026-10-19 14:29:00'
       UNION ALL SELECT (@cd_a + 415), (@cd_sm + 42), (@cd_u + 24), (@cd_c + 4), '동호회', '확정', 'RCPT-20261019-FWDX399U', '2026-10-19 12:00:00', '2026-10-19 13:30:00'
       UNION ALL SELECT (@cd_a + 416), (@cd_sm + 42), (@cd_u + 4), (@cd_c + 1), '동호회', '확정', 'RCPT-20261018-P7KZF59P', '2026-10-18 10:00:00', '2026-10-18 11:30:00'
       UNION ALL SELECT (@cd_a + 417), (@cd_sm + 42), (@cd_u + 7), (@cd_c + 1), '동호회', '확정', 'RCPT-20261018-XCHUYS6X', '2026-10-18 10:00:00', '2026-10-18 12:55:00'
       UNION ALL SELECT (@cd_a + 418), (@cd_sm + 42), (@cd_u + 6), (@cd_c + 1), '동호회', '확정', 'RCPT-20261018-J7AQVR72', '2026-10-18 10:00:00', '2026-10-18 12:23:00'
       UNION ALL SELECT (@cd_a + 419), (@cd_sm + 42), (@cd_u + 3), (@cd_c + 1), '동호회', '확정', 'RCPT-20261018-VQ4JSBNS', '2026-10-18 10:00:00', '2026-10-18 12:17:00'
       UNION ALL SELECT (@cd_a + 420), (@cd_sm + 42), (@cd_u + 5), (@cd_c + 1), '동호회', '확정', 'RCPT-20261018-43BJ397S', '2026-10-18 10:00:00', '2026-10-18 12:21:00'
       UNION ALL SELECT (@cd_a + 421), (@cd_sm + 43), (@cd_u + 15), (@cd_c + 3), '동호회', '확정', 'RCPT-20261019-S2ZBSASM', '2026-10-19 17:00:00', '2026-10-19 19:55:00'
       UNION ALL SELECT (@cd_a + 422), (@cd_sm + 43), (@cd_u + 21), (@cd_c + 3), '동호회', '확정', 'RCPT-20261019-J54T9QEY', '2026-10-19 17:00:00', '2026-10-19 19:53:00'
       UNION ALL SELECT (@cd_a + 423), (@cd_sm + 43), (@cd_u + 19), (@cd_c + 3), '동호회', '확정', 'RCPT-20261019-TB7PYY3G', '2026-10-19 17:00:00', '2026-10-19 18:02:00'
       UNION ALL SELECT (@cd_a + 424), (@cd_sm + 43), (@cd_u + 16), (@cd_c + 3), '동호회', '확정', 'RCPT-20261019-VRWFSJ9J', '2026-10-19 17:00:00', '2026-10-19 19:52:00'
       UNION ALL SELECT (@cd_a + 425), (@cd_sm + 43), (@cd_u + 20), (@cd_c + 3), '동호회', '확정', 'RCPT-20261019-QXH8YYJW', '2026-10-19 17:00:00', '2026-10-19 19:50:00'
       UNION ALL SELECT (@cd_a + 426), (@cd_sm + 43), (@cd_u + 41), (@cd_c + 6), '동호회', '확정', 'RCPT-20261020-2X8CPHQV', '2026-10-20 15:00:00', '2026-10-20 17:46:00'
       UNION ALL SELECT (@cd_a + 427), (@cd_sm + 43), (@cd_u + 37), (@cd_c + 6), '동호회', '확정', 'RCPT-20261020-6V8PGECE', '2026-10-20 15:00:00', '2026-10-20 16:30:00'
       UNION ALL SELECT (@cd_a + 428), (@cd_sm + 43), (@cd_u + 39), (@cd_c + 6), '동호회', '확정', 'RCPT-20261020-56JVHYVK', '2026-10-20 15:00:00', '2026-10-20 18:00:00'
       UNION ALL SELECT (@cd_a + 429), (@cd_sm + 43), (@cd_u + 40), (@cd_c + 6), '동호회', '확정', 'RCPT-20261020-F6GQYVQ3', '2026-10-20 15:00:00', '2026-10-20 17:54:00'
       UNION ALL SELECT (@cd_a + 430), (@cd_sm + 43), (@cd_u + 42), (@cd_c + 6), '동호회', '확정', 'RCPT-20261020-9MXRFBJN', '2026-10-20 15:00:00', '2026-10-20 16:09:00'
       UNION ALL SELECT (@cd_a + 431), (@cd_sm + 44), (@cd_u + 43), (@cd_c + 7), '동호회', '확정', 'RCPT-20261020-G6M3PTRV', '2026-10-20 08:00:00', '2026-10-20 09:48:00'
       UNION ALL SELECT (@cd_a + 432), (@cd_sm + 44), (@cd_u + 49), (@cd_c + 7), '동호회', '확정', 'RCPT-20261020-BXDZVAFE', '2026-10-20 08:00:00', '2026-10-20 09:12:00'
       UNION ALL SELECT (@cd_a + 433), (@cd_sm + 44), (@cd_u + 48), (@cd_c + 7), '동호회', '확정', 'RCPT-20261020-H7FW2GZX', '2026-10-20 08:00:00', '2026-10-20 09:15:00'
       UNION ALL SELECT (@cd_a + 434), (@cd_sm + 44), (@cd_u + 46), (@cd_c + 7), '동호회', '확정', 'RCPT-20261020-C2JHN3YP', '2026-10-20 08:00:00', '2026-10-20 09:19:00'
       UNION ALL SELECT (@cd_a + 435), (@cd_sm + 44), (@cd_u + 45), (@cd_c + 7), '동호회', '확정', 'RCPT-20261020-6V4EMJ6N', '2026-10-20 08:00:00', '2026-10-20 09:04:00'
       UNION ALL SELECT (@cd_a + 436), (@cd_sm + 44), (@cd_u + 57), (@cd_c + 9), '동호회', '확정', 'RCPT-20261022-3CJS2VLW', '2026-10-22 10:00:00', '2026-10-22 11:38:00'
       UNION ALL SELECT (@cd_a + 437), (@cd_sm + 44), (@cd_u + 66), (@cd_c + 9), '동호회', '확정', 'RCPT-20261022-SBDFFLMH', '2026-10-22 10:00:00', '2026-10-22 12:52:00'
       UNION ALL SELECT (@cd_a + 438), (@cd_sm + 44), (@cd_u + 62), (@cd_c + 9), '동호회', '확정', 'RCPT-20261022-ZCLG4XDF', '2026-10-22 10:00:00', '2026-10-22 11:29:00'
       UNION ALL SELECT (@cd_a + 439), (@cd_sm + 44), (@cd_u + 63), (@cd_c + 9), '동호회', '확정', 'RCPT-20261022-KAL9KMB8', '2026-10-22 10:00:00', '2026-10-22 11:16:00'
       UNION ALL SELECT (@cd_a + 440), (@cd_sm + 44), (@cd_u + 59), (@cd_c + 9), '동호회', '확정', 'RCPT-20261022-QR2ERCRY', '2026-10-22 10:00:00', '2026-10-22 11:04:00'
       UNION ALL SELECT (@cd_a + 441), (@cd_sm + 45), (@cd_u + 8), (@cd_c + 2), '동호회', '확정', 'RCPT-20261022-SWTKRJN2', '2026-10-22 05:00:00', '2026-10-22 06:35:00'
       UNION ALL SELECT (@cd_a + 442), (@cd_sm + 45), (@cd_u + 10), (@cd_c + 2), '동호회', '확정', 'RCPT-20261022-9KEBCWFB', '2026-10-22 05:00:00', '2026-10-22 07:01:00'
       UNION ALL SELECT (@cd_a + 443), (@cd_sm + 45), (@cd_u + 9), (@cd_c + 2), '동호회', '확정', 'RCPT-20261022-DELNL8ZG', '2026-10-22 05:00:00', '2026-10-22 06:11:00'
       UNION ALL SELECT (@cd_a + 444), (@cd_sm + 45), (@cd_u + 13), (@cd_c + 2), '동호회', '확정', 'RCPT-20261022-AS33A65X', '2026-10-22 05:00:00', '2026-10-22 06:15:00'
       UNION ALL SELECT (@cd_a + 445), (@cd_sm + 45), (@cd_u + 12), (@cd_c + 2), '동호회', '확정', 'RCPT-20261022-KHD2TYGX', '2026-10-22 05:00:00', '2026-10-22 06:36:00'
       UNION ALL SELECT (@cd_a + 446), (@cd_sm + 45), (@cd_u + 67), (@cd_c + 10), '동호회', '확정', 'RCPT-20261021-8YV4NE3C', '2026-10-21 04:00:00', '2026-10-21 05:05:00'
       UNION ALL SELECT (@cd_a + 447), (@cd_sm + 45), (@cd_u + 69), (@cd_c + 10), '동호회', '확정', 'RCPT-20261021-9JPC2N69', '2026-10-21 04:00:00', '2026-10-21 05:55:00'
       UNION ALL SELECT (@cd_a + 448), (@cd_sm + 45), (@cd_u + 70), (@cd_c + 10), '동호회', '확정', 'RCPT-20261021-CUPRGFGY', '2026-10-21 04:00:00', '2026-10-21 06:01:00'
       UNION ALL SELECT (@cd_a + 449), (@cd_sm + 45), (@cd_u + 75), (@cd_c + 10), '동호회', '확정', 'RCPT-20261021-A22QMXKL', '2026-10-21 04:00:00', '2026-10-21 06:58:00'
       UNION ALL SELECT (@cd_a + 450), (@cd_sm + 45), (@cd_u + 73), (@cd_c + 10), '동호회', '확정', 'RCPT-20261021-6SLQ38VU', '2026-10-21 04:00:00', '2026-10-21 05:20:00'
       UNION ALL SELECT (@cd_a + 451), (@cd_sm + 46), (@cd_u + 29), (@cd_c + 5), '동호회', '확정', 'RCPT-20261023-U74YACU5', '2026-10-23 03:00:00', '2026-10-23 05:01:00'
       UNION ALL SELECT (@cd_a + 452), (@cd_sm + 46), (@cd_u + 35), (@cd_c + 5), '동호회', '확정', 'RCPT-20261023-QUYBSBD9', '2026-10-23 03:00:00', '2026-10-23 05:27:00'
       UNION ALL SELECT (@cd_a + 453), (@cd_sm + 46), (@cd_u + 34), (@cd_c + 5), '동호회', '확정', 'RCPT-20261023-PN6LFLPY', '2026-10-23 03:00:00', '2026-10-23 04:54:00'
       UNION ALL SELECT (@cd_a + 454), (@cd_sm + 46), (@cd_u + 32), (@cd_c + 5), '동호회', '확정', 'RCPT-20261023-4GR6DMC4', '2026-10-23 03:00:00', '2026-10-23 05:35:00'
       UNION ALL SELECT (@cd_a + 455), (@cd_sm + 46), (@cd_u + 30), (@cd_c + 5), '동호회', '확정', 'RCPT-20261023-EKXQCY3S', '2026-10-23 03:00:00', '2026-10-23 04:01:00'
       UNION ALL SELECT (@cd_a + 456), (@cd_sm + 46), (@cd_u + 1), (@cd_c + 1), '동호회', '확정', 'RCPT-20261024-GXS7DMHC', '2026-10-24 04:00:00', '2026-10-24 06:09:00'
       UNION ALL SELECT (@cd_a + 457), (@cd_sm + 46), (@cd_u + 7), (@cd_c + 1), '동호회', '확정', 'RCPT-20261024-K3ELBC3C', '2026-10-24 04:00:00', '2026-10-24 06:56:00'
       UNION ALL SELECT (@cd_a + 458), (@cd_sm + 46), (@cd_u + 2), (@cd_c + 1), '동호회', '확정', 'RCPT-20261024-66YKNUVD', '2026-10-24 04:00:00', '2026-10-24 05:00:00'
       UNION ALL SELECT (@cd_a + 459), (@cd_sm + 46), (@cd_u + 6), (@cd_c + 1), '동호회', '확정', 'RCPT-20261024-234SR8YU', '2026-10-24 04:00:00', '2026-10-24 05:38:00'
       UNION ALL SELECT (@cd_a + 460), (@cd_sm + 46), (@cd_u + 4), (@cd_c + 1), '동호회', '확정', 'RCPT-20261024-E4TYTN5N', '2026-10-24 04:00:00', '2026-10-24 05:43:00'
       UNION ALL SELECT (@cd_a + 461), (@cd_sm + 47), (@cd_u + 50), (@cd_c + 8), '동호회', '확정', 'RCPT-20261025-RP7853SA', '2026-10-25 19:00:00', '2026-10-25 20:46:00'
       UNION ALL SELECT (@cd_a + 462), (@cd_sm + 47), (@cd_u + 53), (@cd_c + 8), '동호회', '확정', 'RCPT-20261025-T5F2GP3G', '2026-10-25 19:00:00', '2026-10-25 21:29:00'
       UNION ALL SELECT (@cd_a + 463), (@cd_sm + 47), (@cd_u + 55), (@cd_c + 8), '동호회', '확정', 'RCPT-20261025-6SGMJP3P', '2026-10-25 19:00:00', '2026-10-25 21:45:00'
       UNION ALL SELECT (@cd_a + 464), (@cd_sm + 47), (@cd_u + 51), (@cd_c + 8), '동호회', '확정', 'RCPT-20261025-8AHW9LTG', '2026-10-25 19:00:00', '2026-10-25 21:36:00'
       UNION ALL SELECT (@cd_a + 465), (@cd_sm + 47), (@cd_u + 54), (@cd_c + 8), '동호회', '확정', 'RCPT-20261025-U7UHGMU3', '2026-10-25 19:00:00', '2026-10-25 20:22:00'
       UNION ALL SELECT (@cd_a + 466), (@cd_sm + 47), (@cd_u + 67), (@cd_c + 10), '동호회', '확정', 'RCPT-20261025-NW89PZ8Y', '2026-10-25 13:00:00', '2026-10-25 14:30:00'
       UNION ALL SELECT (@cd_a + 467), (@cd_sm + 47), (@cd_u + 68), (@cd_c + 10), '동호회', '확정', 'RCPT-20261025-BVZSKJKX', '2026-10-25 13:00:00', '2026-10-25 14:36:00'
       UNION ALL SELECT (@cd_a + 468), (@cd_sm + 47), (@cd_u + 72), (@cd_c + 10), '동호회', '확정', 'RCPT-20261025-NX6WWWMY', '2026-10-25 13:00:00', '2026-10-25 14:00:00'
       UNION ALL SELECT (@cd_a + 469), (@cd_sm + 47), (@cd_u + 74), (@cd_c + 10), '동호회', '확정', 'RCPT-20261025-UQHN8KBS', '2026-10-25 13:00:00', '2026-10-25 15:40:00'
       UNION ALL SELECT (@cd_a + 470), (@cd_sm + 47), (@cd_u + 71), (@cd_c + 10), '동호회', '확정', 'RCPT-20261025-UXHHKHKC', '2026-10-25 13:00:00', '2026-10-25 14:42:00'
       UNION ALL SELECT (@cd_a + 471), (@cd_sm + 48), (@cd_u + 1), (@cd_c + 1), '동호회', '확정', 'RCPT-20261026-DUKD59NK', '2026-10-26 15:00:00', '2026-10-26 17:53:00'
       UNION ALL SELECT (@cd_a + 472), (@cd_sm + 48), (@cd_u + 7), (@cd_c + 1), '동호회', '확정', 'RCPT-20261026-DFUDJVXH', '2026-10-26 15:00:00', '2026-10-26 17:32:00'
       UNION ALL SELECT (@cd_a + 473), (@cd_sm + 48), (@cd_u + 5), (@cd_c + 1), '동호회', '확정', 'RCPT-20261026-28Q9DBH8', '2026-10-26 15:00:00', '2026-10-26 16:29:00'
       UNION ALL SELECT (@cd_a + 474), (@cd_sm + 48), (@cd_u + 3), (@cd_c + 1), '동호회', '확정', 'RCPT-20261026-TFS4ES4N', '2026-10-26 15:00:00', '2026-10-26 16:45:00'
       UNION ALL SELECT (@cd_a + 475), (@cd_sm + 48), (@cd_u + 2), (@cd_c + 1), '동호회', '확정', 'RCPT-20261026-4BZWBK2B', '2026-10-26 15:00:00', '2026-10-26 16:33:00'
       UNION ALL SELECT (@cd_a + 476), (@cd_sm + 48), (@cd_u + 36), (@cd_c + 6), '동호회', '확정', 'RCPT-20261026-MVZRMFXA', '2026-10-26 16:00:00', '2026-10-26 18:47:00'
       UNION ALL SELECT (@cd_a + 477), (@cd_sm + 48), (@cd_u + 38), (@cd_c + 6), '동호회', '확정', 'RCPT-20261026-JEMQXGSY', '2026-10-26 16:00:00', '2026-10-26 18:27:00'
       UNION ALL SELECT (@cd_a + 478), (@cd_sm + 48), (@cd_u + 40), (@cd_c + 6), '동호회', '확정', 'RCPT-20261026-7WQ5M8J8', '2026-10-26 16:00:00', '2026-10-26 18:06:00'
       UNION ALL SELECT (@cd_a + 479), (@cd_sm + 48), (@cd_u + 37), (@cd_c + 6), '동호회', '확정', 'RCPT-20261026-WF6X2J2V', '2026-10-26 16:00:00', '2026-10-26 18:14:00'
       UNION ALL SELECT (@cd_a + 480), (@cd_sm + 48), (@cd_u + 39), (@cd_c + 6), '동호회', '확정', 'RCPT-20261026-GAX4XZTH', '2026-10-26 16:00:00', '2026-10-26 18:37:00'
       UNION ALL SELECT (@cd_a + 481), (@cd_sm + 49), (@cd_u + 1), (@cd_c + 1), '동호회', '확정', 'RCPT-20261102-3E3FVJYH', '2026-11-02 12:00:00', '2026-11-02 14:33:00'
       UNION ALL SELECT (@cd_a + 482), (@cd_sm + 49), (@cd_u + 7), (@cd_c + 1), '동호회', '확정', 'RCPT-20261102-YFU6CFDK', '2026-11-02 12:00:00', '2026-11-02 13:40:00'
       UNION ALL SELECT (@cd_a + 483), (@cd_sm + 49), (@cd_u + 5), (@cd_c + 1), '동호회', '확정', 'RCPT-20261102-4WSHBHZ4', '2026-11-02 12:00:00', '2026-11-02 14:44:00'
       UNION ALL SELECT (@cd_a + 484), (@cd_sm + 49), (@cd_u + 3), (@cd_c + 1), '동호회', '확정', 'RCPT-20261102-3WQ4R8RA', '2026-11-02 12:00:00', '2026-11-02 14:37:00'
       UNION ALL SELECT (@cd_a + 485), (@cd_sm + 49), (@cd_u + 6), (@cd_c + 1), '동호회', '확정', 'RCPT-20261102-DN8BLP4E', '2026-11-02 12:00:00', '2026-11-02 13:35:00'
       UNION ALL SELECT (@cd_a + 486), (@cd_sm + 49), (@cd_u + 57), (@cd_c + 9), '동호회', '확정', 'RCPT-20261031-NPKC96PN', '2026-10-31 15:00:00', '2026-10-31 16:56:00'
       UNION ALL SELECT (@cd_a + 487), (@cd_sm + 49), (@cd_u + 66), (@cd_c + 9), '동호회', '확정', 'RCPT-20261031-TKBD3RYE', '2026-10-31 15:00:00', '2026-10-31 16:53:00'
       UNION ALL SELECT (@cd_a + 488), (@cd_sm + 49), (@cd_u + 65), (@cd_c + 9), '동호회', '확정', 'RCPT-20261031-RQX8YKDY', '2026-10-31 15:00:00', '2026-10-31 16:00:00'
       UNION ALL SELECT (@cd_a + 489), (@cd_sm + 49), (@cd_u + 59), (@cd_c + 9), '동호회', '확정', 'RCPT-20261031-EPM7H4DS', '2026-10-31 15:00:00', '2026-10-31 17:23:00'
       UNION ALL SELECT (@cd_a + 490), (@cd_sm + 49), (@cd_u + 60), (@cd_c + 9), '동호회', '확정', 'RCPT-20261031-RQEH6T6X', '2026-10-31 15:00:00', '2026-10-31 16:26:00'
       UNION ALL SELECT (@cd_a + 491), (@cd_sm + 50), (@cd_u + 38), (@cd_c + 6), '동호회', '확정', 'RCPT-20261104-YS2N42GT', '2026-11-04 14:00:00', '2026-11-04 15:26:00'
       UNION ALL SELECT (@cd_a + 492), (@cd_sm + 50), (@cd_u + 40), (@cd_c + 6), '동호회', '확정', 'RCPT-20261104-JY4JFKWN', '2026-11-04 14:00:00', '2026-11-04 16:45:00'
       UNION ALL SELECT (@cd_a + 493), (@cd_sm + 50), (@cd_u + 42), (@cd_c + 6), '동호회', '확정', 'RCPT-20261104-YZWRQBLY', '2026-11-04 14:00:00', '2026-11-04 16:04:00'
       UNION ALL SELECT (@cd_a + 494), (@cd_sm + 50), (@cd_u + 41), (@cd_c + 6), '동호회', '확정', 'RCPT-20261104-QNQNENNH', '2026-11-04 14:00:00', '2026-11-04 16:27:00'
       UNION ALL SELECT (@cd_a + 495), (@cd_sm + 50), (@cd_u + 37), (@cd_c + 6), '동호회', '확정', 'RCPT-20261104-JFPV7VXK', '2026-11-04 14:00:00', '2026-11-04 16:22:00'
       UNION ALL SELECT (@cd_a + 496), (@cd_sm + 50), (@cd_u + 67), (@cd_c + 10), '동호회', '확정', 'RCPT-20261104-Y53MDKV7', '2026-11-04 14:00:00', '2026-11-04 15:18:00'
       UNION ALL SELECT (@cd_a + 497), (@cd_sm + 50), (@cd_u + 76), (@cd_c + 10), '동호회', '확정', 'RCPT-20261104-5GCNVNH9', '2026-11-04 14:00:00', '2026-11-04 15:22:00'
       UNION ALL SELECT (@cd_a + 498), (@cd_sm + 50), (@cd_u + 72), (@cd_c + 10), '동호회', '확정', 'RCPT-20261104-A97UCMRN', '2026-11-04 14:00:00', '2026-11-04 16:01:00'
       UNION ALL SELECT (@cd_a + 499), (@cd_sm + 50), (@cd_u + 70), (@cd_c + 10), '동호회', '확정', 'RCPT-20261104-LM5VEE9V', '2026-11-04 14:00:00', '2026-11-04 16:19:00'
       UNION ALL SELECT (@cd_a + 500), (@cd_sm + 50), (@cd_u + 68), (@cd_c + 10), '동호회', '확정', 'RCPT-20261104-NQH6HHXB', '2026-11-04 14:00:00', '2026-11-04 15:08:00'
       UNION ALL SELECT (@cd_a + 501), (@cd_sm + 51), (@cd_u + 59), (@cd_c + 9), '동호회', '대기', 'RCPT-20261111-GKRSKPBM', '2026-11-11 21:17:00', NULL
       UNION ALL SELECT (@cd_a + 502), (@cd_sm + 51), (@cd_u + 60), (@cd_c + 9), '동호회', '대기', 'RCPT-20261111-3ESVPJEC', '2026-11-11 21:17:00', NULL
       UNION ALL SELECT (@cd_a + 503), (@cd_sm + 51), (@cd_u + 61), (@cd_c + 9), '동호회', '대기', 'RCPT-20261111-CUSJ9E6L', '2026-11-11 21:17:00', NULL
       UNION ALL SELECT (@cd_a + 504), (@cd_sm + 51), (@cd_u + 62), (@cd_c + 9), '동호회', '대기', 'RCPT-20261111-RG4G2CAL', '2026-11-11 21:17:00', NULL
       UNION ALL SELECT (@cd_a + 505), (@cd_sm + 51), (@cd_u + 63), (@cd_c + 9), '동호회', '대기', 'RCPT-20261111-WK7SR4HY', '2026-11-11 21:17:00', NULL
       UNION ALL SELECT (@cd_a + 506), (@cd_sm + 52), (@cd_u + 69), (@cd_c + 10), '동호회', '대기', 'RCPT-20261111-487KTF3X', '2026-11-11 21:34:00', NULL
       UNION ALL SELECT (@cd_a + 507), (@cd_sm + 52), (@cd_u + 70), (@cd_c + 10), '동호회', '대기', 'RCPT-20261111-SNNGBLSX', '2026-11-11 21:34:00', NULL
       UNION ALL SELECT (@cd_a + 508), (@cd_sm + 52), (@cd_u + 71), (@cd_c + 10), '동호회', '대기', 'RCPT-20261111-QH2BVVNW', '2026-11-11 21:34:00', NULL
       UNION ALL SELECT (@cd_a + 509), (@cd_sm + 52), (@cd_u + 72), (@cd_c + 10), '동호회', '대기', 'RCPT-20261111-JTFM5EC6', '2026-11-11 21:34:00', NULL
       UNION ALL SELECT (@cd_a + 510), (@cd_sm + 52), (@cd_u + 73), (@cd_c + 10), '동호회', '대기', 'RCPT-20261111-EW4TSLED', '2026-11-11 21:34:00', NULL
) v
 WHERE @cd_ready = 1;
UPDATE match_application
   SET group_application_id = CASE
         WHEN application_id BETWEEN (@cd_a + 1) AND (@cd_a + 5) THEN (@cd_a + 1)
         WHEN application_id BETWEEN (@cd_a + 6) AND (@cd_a + 10) THEN (@cd_a + 6)
         WHEN application_id BETWEEN (@cd_a + 11) AND (@cd_a + 15) THEN (@cd_a + 11)
         WHEN application_id BETWEEN (@cd_a + 16) AND (@cd_a + 20) THEN (@cd_a + 16)
         WHEN application_id BETWEEN (@cd_a + 21) AND (@cd_a + 25) THEN (@cd_a + 21)
         WHEN application_id BETWEEN (@cd_a + 26) AND (@cd_a + 30) THEN (@cd_a + 26)
         WHEN application_id BETWEEN (@cd_a + 31) AND (@cd_a + 35) THEN (@cd_a + 31)
         WHEN application_id BETWEEN (@cd_a + 36) AND (@cd_a + 40) THEN (@cd_a + 36)
         WHEN application_id BETWEEN (@cd_a + 41) AND (@cd_a + 45) THEN (@cd_a + 41)
         WHEN application_id BETWEEN (@cd_a + 46) AND (@cd_a + 50) THEN (@cd_a + 46)
         WHEN application_id BETWEEN (@cd_a + 51) AND (@cd_a + 55) THEN (@cd_a + 51)
         WHEN application_id BETWEEN (@cd_a + 56) AND (@cd_a + 60) THEN (@cd_a + 56)
         WHEN application_id BETWEEN (@cd_a + 61) AND (@cd_a + 65) THEN (@cd_a + 61)
         WHEN application_id BETWEEN (@cd_a + 66) AND (@cd_a + 70) THEN (@cd_a + 66)
         WHEN application_id BETWEEN (@cd_a + 71) AND (@cd_a + 75) THEN (@cd_a + 71)
         WHEN application_id BETWEEN (@cd_a + 76) AND (@cd_a + 80) THEN (@cd_a + 76)
         WHEN application_id BETWEEN (@cd_a + 81) AND (@cd_a + 85) THEN (@cd_a + 81)
         WHEN application_id BETWEEN (@cd_a + 86) AND (@cd_a + 90) THEN (@cd_a + 86)
         WHEN application_id BETWEEN (@cd_a + 91) AND (@cd_a + 95) THEN (@cd_a + 91)
         WHEN application_id BETWEEN (@cd_a + 96) AND (@cd_a + 100) THEN (@cd_a + 96)
         WHEN application_id BETWEEN (@cd_a + 101) AND (@cd_a + 105) THEN (@cd_a + 101)
         WHEN application_id BETWEEN (@cd_a + 106) AND (@cd_a + 110) THEN (@cd_a + 106)
         WHEN application_id BETWEEN (@cd_a + 111) AND (@cd_a + 115) THEN (@cd_a + 111)
         WHEN application_id BETWEEN (@cd_a + 116) AND (@cd_a + 120) THEN (@cd_a + 116)
         WHEN application_id BETWEEN (@cd_a + 121) AND (@cd_a + 125) THEN (@cd_a + 121)
         WHEN application_id BETWEEN (@cd_a + 126) AND (@cd_a + 130) THEN (@cd_a + 126)
         WHEN application_id BETWEEN (@cd_a + 131) AND (@cd_a + 135) THEN (@cd_a + 131)
         WHEN application_id BETWEEN (@cd_a + 136) AND (@cd_a + 140) THEN (@cd_a + 136)
         WHEN application_id BETWEEN (@cd_a + 141) AND (@cd_a + 145) THEN (@cd_a + 141)
         WHEN application_id BETWEEN (@cd_a + 146) AND (@cd_a + 150) THEN (@cd_a + 146)
         WHEN application_id BETWEEN (@cd_a + 151) AND (@cd_a + 155) THEN (@cd_a + 151)
         WHEN application_id BETWEEN (@cd_a + 156) AND (@cd_a + 160) THEN (@cd_a + 156)
         WHEN application_id BETWEEN (@cd_a + 161) AND (@cd_a + 165) THEN (@cd_a + 161)
         WHEN application_id BETWEEN (@cd_a + 166) AND (@cd_a + 170) THEN (@cd_a + 166)
         WHEN application_id BETWEEN (@cd_a + 171) AND (@cd_a + 175) THEN (@cd_a + 171)
         WHEN application_id BETWEEN (@cd_a + 176) AND (@cd_a + 180) THEN (@cd_a + 176)
         WHEN application_id BETWEEN (@cd_a + 181) AND (@cd_a + 185) THEN (@cd_a + 181)
         WHEN application_id BETWEEN (@cd_a + 186) AND (@cd_a + 190) THEN (@cd_a + 186)
         WHEN application_id BETWEEN (@cd_a + 191) AND (@cd_a + 195) THEN (@cd_a + 191)
         WHEN application_id BETWEEN (@cd_a + 196) AND (@cd_a + 200) THEN (@cd_a + 196)
         WHEN application_id BETWEEN (@cd_a + 201) AND (@cd_a + 205) THEN (@cd_a + 201)
         WHEN application_id BETWEEN (@cd_a + 206) AND (@cd_a + 210) THEN (@cd_a + 206)
         WHEN application_id BETWEEN (@cd_a + 211) AND (@cd_a + 215) THEN (@cd_a + 211)
         WHEN application_id BETWEEN (@cd_a + 216) AND (@cd_a + 220) THEN (@cd_a + 216)
         WHEN application_id BETWEEN (@cd_a + 221) AND (@cd_a + 225) THEN (@cd_a + 221)
         WHEN application_id BETWEEN (@cd_a + 226) AND (@cd_a + 230) THEN (@cd_a + 226)
         WHEN application_id BETWEEN (@cd_a + 231) AND (@cd_a + 235) THEN (@cd_a + 231)
         WHEN application_id BETWEEN (@cd_a + 236) AND (@cd_a + 240) THEN (@cd_a + 236)
         WHEN application_id BETWEEN (@cd_a + 241) AND (@cd_a + 245) THEN (@cd_a + 241)
         WHEN application_id BETWEEN (@cd_a + 246) AND (@cd_a + 250) THEN (@cd_a + 246)
         WHEN application_id BETWEEN (@cd_a + 251) AND (@cd_a + 255) THEN (@cd_a + 251)
         WHEN application_id BETWEEN (@cd_a + 256) AND (@cd_a + 260) THEN (@cd_a + 256)
         WHEN application_id BETWEEN (@cd_a + 261) AND (@cd_a + 265) THEN (@cd_a + 261)
         WHEN application_id BETWEEN (@cd_a + 266) AND (@cd_a + 270) THEN (@cd_a + 266)
         WHEN application_id BETWEEN (@cd_a + 271) AND (@cd_a + 275) THEN (@cd_a + 271)
         WHEN application_id BETWEEN (@cd_a + 276) AND (@cd_a + 280) THEN (@cd_a + 276)
         WHEN application_id BETWEEN (@cd_a + 281) AND (@cd_a + 285) THEN (@cd_a + 281)
         WHEN application_id BETWEEN (@cd_a + 286) AND (@cd_a + 290) THEN (@cd_a + 286)
         WHEN application_id BETWEEN (@cd_a + 291) AND (@cd_a + 295) THEN (@cd_a + 291)
         WHEN application_id BETWEEN (@cd_a + 296) AND (@cd_a + 300) THEN (@cd_a + 296)
         WHEN application_id BETWEEN (@cd_a + 301) AND (@cd_a + 305) THEN (@cd_a + 301)
         WHEN application_id BETWEEN (@cd_a + 306) AND (@cd_a + 310) THEN (@cd_a + 306)
         WHEN application_id BETWEEN (@cd_a + 311) AND (@cd_a + 315) THEN (@cd_a + 311)
         WHEN application_id BETWEEN (@cd_a + 316) AND (@cd_a + 320) THEN (@cd_a + 316)
         WHEN application_id BETWEEN (@cd_a + 321) AND (@cd_a + 325) THEN (@cd_a + 321)
         WHEN application_id BETWEEN (@cd_a + 326) AND (@cd_a + 330) THEN (@cd_a + 326)
         WHEN application_id BETWEEN (@cd_a + 331) AND (@cd_a + 335) THEN (@cd_a + 331)
         WHEN application_id BETWEEN (@cd_a + 336) AND (@cd_a + 340) THEN (@cd_a + 336)
         WHEN application_id BETWEEN (@cd_a + 341) AND (@cd_a + 345) THEN (@cd_a + 341)
         WHEN application_id BETWEEN (@cd_a + 346) AND (@cd_a + 350) THEN (@cd_a + 346)
         WHEN application_id BETWEEN (@cd_a + 351) AND (@cd_a + 355) THEN (@cd_a + 351)
         WHEN application_id BETWEEN (@cd_a + 356) AND (@cd_a + 360) THEN (@cd_a + 356)
         WHEN application_id BETWEEN (@cd_a + 361) AND (@cd_a + 365) THEN (@cd_a + 361)
         WHEN application_id BETWEEN (@cd_a + 366) AND (@cd_a + 370) THEN (@cd_a + 366)
         WHEN application_id BETWEEN (@cd_a + 371) AND (@cd_a + 375) THEN (@cd_a + 371)
         WHEN application_id BETWEEN (@cd_a + 376) AND (@cd_a + 380) THEN (@cd_a + 376)
         WHEN application_id BETWEEN (@cd_a + 381) AND (@cd_a + 385) THEN (@cd_a + 381)
         WHEN application_id BETWEEN (@cd_a + 386) AND (@cd_a + 390) THEN (@cd_a + 386)
         WHEN application_id BETWEEN (@cd_a + 391) AND (@cd_a + 395) THEN (@cd_a + 391)
         WHEN application_id BETWEEN (@cd_a + 396) AND (@cd_a + 400) THEN (@cd_a + 396)
         WHEN application_id BETWEEN (@cd_a + 401) AND (@cd_a + 405) THEN (@cd_a + 401)
         WHEN application_id BETWEEN (@cd_a + 406) AND (@cd_a + 410) THEN (@cd_a + 406)
         WHEN application_id BETWEEN (@cd_a + 411) AND (@cd_a + 415) THEN (@cd_a + 411)
         WHEN application_id BETWEEN (@cd_a + 416) AND (@cd_a + 420) THEN (@cd_a + 416)
         WHEN application_id BETWEEN (@cd_a + 421) AND (@cd_a + 425) THEN (@cd_a + 421)
         WHEN application_id BETWEEN (@cd_a + 426) AND (@cd_a + 430) THEN (@cd_a + 426)
         WHEN application_id BETWEEN (@cd_a + 431) AND (@cd_a + 435) THEN (@cd_a + 431)
         WHEN application_id BETWEEN (@cd_a + 436) AND (@cd_a + 440) THEN (@cd_a + 436)
         WHEN application_id BETWEEN (@cd_a + 441) AND (@cd_a + 445) THEN (@cd_a + 441)
         WHEN application_id BETWEEN (@cd_a + 446) AND (@cd_a + 450) THEN (@cd_a + 446)
         WHEN application_id BETWEEN (@cd_a + 451) AND (@cd_a + 455) THEN (@cd_a + 451)
         WHEN application_id BETWEEN (@cd_a + 456) AND (@cd_a + 460) THEN (@cd_a + 456)
         WHEN application_id BETWEEN (@cd_a + 461) AND (@cd_a + 465) THEN (@cd_a + 461)
         WHEN application_id BETWEEN (@cd_a + 466) AND (@cd_a + 470) THEN (@cd_a + 466)
         WHEN application_id BETWEEN (@cd_a + 471) AND (@cd_a + 475) THEN (@cd_a + 471)
         WHEN application_id BETWEEN (@cd_a + 476) AND (@cd_a + 480) THEN (@cd_a + 476)
         WHEN application_id BETWEEN (@cd_a + 481) AND (@cd_a + 485) THEN (@cd_a + 481)
         WHEN application_id BETWEEN (@cd_a + 486) AND (@cd_a + 490) THEN (@cd_a + 486)
         WHEN application_id BETWEEN (@cd_a + 491) AND (@cd_a + 495) THEN (@cd_a + 491)
         WHEN application_id BETWEEN (@cd_a + 496) AND (@cd_a + 500) THEN (@cd_a + 496)
         WHEN application_id BETWEEN (@cd_a + 501) AND (@cd_a + 505) THEN (@cd_a + 501)
         WHEN application_id BETWEEN (@cd_a + 506) AND (@cd_a + 510) THEN (@cd_a + 506)
       END
 WHERE @cd_ready = 1 AND application_id BETWEEN (@cd_a + 1) AND (@cd_a + 510);

-- 5. 참가자 (확정 순서대로 번호, entry_mmr은 확정 시점의 레이팅 = 직전 경기 반영 뒤 값)
INSERT INTO match_participant (participant_id, application_id, match_id, user_id, participation_status, match_side, entry_mmr, joined_at)
SELECT * FROM (
                 SELECT (@cd_p + 1) AS participant_id, (@cd_a + 1) AS application_id, (@cd_sm + 1) AS match_id, (@cd_u + 43) AS user_id, '확정' AS participation_status, 'A' AS match_side, 1000.00 AS entry_mmr, '2026-07-28 10:12:00' AS joined_at
       UNION ALL SELECT (@cd_p + 2), (@cd_a + 2), (@cd_sm + 1), (@cd_u + 44), '확정', 'A', 1000.00, '2026-07-28 10:42:00'
       UNION ALL SELECT (@cd_p + 3), (@cd_a + 4), (@cd_sm + 1), (@cd_u + 46), '확정', 'A', 1000.00, '2026-07-28 10:52:00'
       UNION ALL SELECT (@cd_p + 4), (@cd_a + 5), (@cd_sm + 1), (@cd_u + 47), '확정', 'A', 1000.00, '2026-07-28 11:07:00'
       UNION ALL SELECT (@cd_p + 5), (@cd_a + 3), (@cd_sm + 1), (@cd_u + 45), '확정', 'A', 1000.00, '2026-07-28 11:25:00'
       UNION ALL SELECT (@cd_p + 6), (@cd_a + 7), (@cd_sm + 1), (@cd_u + 2), '확정', 'B', 1000.00, '2026-07-29 06:03:00'
       UNION ALL SELECT (@cd_p + 7), (@cd_a + 8), (@cd_sm + 1), (@cd_u + 4), '확정', 'B', 1000.00, '2026-07-29 06:21:00'
       UNION ALL SELECT (@cd_p + 8), (@cd_a + 10), (@cd_sm + 1), (@cd_u + 3), '확정', 'B', 1000.00, '2026-07-29 07:08:00'
       UNION ALL SELECT (@cd_p + 9), (@cd_a + 9), (@cd_sm + 1), (@cd_u + 5), '확정', 'B', 1000.00, '2026-07-29 07:16:00'
       UNION ALL SELECT (@cd_p + 10), (@cd_a + 6), (@cd_sm + 1), (@cd_u + 1), '확정', 'B', 1000.00, '2026-07-29 07:49:00'
       UNION ALL SELECT (@cd_p + 11), (@cd_a + 19), (@cd_sm + 2), (@cd_u + 58), '확정', 'B', 1000.00, '2026-07-29 02:27:00'
       UNION ALL SELECT (@cd_p + 12), (@cd_a + 16), (@cd_sm + 2), (@cd_u + 57), '확정', 'B', 1000.00, '2026-07-29 03:44:00'
       UNION ALL SELECT (@cd_p + 13), (@cd_a + 20), (@cd_sm + 2), (@cd_u + 59), '확정', 'B', 1000.00, '2026-07-29 03:45:00'
       UNION ALL SELECT (@cd_p + 14), (@cd_a + 18), (@cd_sm + 2), (@cd_u + 61), '확정', 'B', 1000.00, '2026-07-29 03:52:00'
       UNION ALL SELECT (@cd_p + 15), (@cd_a + 17), (@cd_sm + 2), (@cd_u + 65), '확정', 'B', 1000.00, '2026-07-29 03:54:00'
       UNION ALL SELECT (@cd_p + 16), (@cd_a + 15), (@cd_sm + 2), (@cd_u + 14), '확정', 'A', 1000.00, '2026-07-29 22:25:00'
       UNION ALL SELECT (@cd_p + 17), (@cd_a + 14), (@cd_sm + 2), (@cd_u + 13), '확정', 'A', 1000.00, '2026-07-29 22:51:00'
       UNION ALL SELECT (@cd_p + 18), (@cd_a + 13), (@cd_sm + 2), (@cd_u + 10), '확정', 'A', 1000.00, '2026-07-29 23:15:00'
       UNION ALL SELECT (@cd_p + 19), (@cd_a + 12), (@cd_sm + 2), (@cd_u + 9), '확정', 'A', 1000.00, '2026-07-29 23:17:00'
       UNION ALL SELECT (@cd_p + 20), (@cd_a + 11), (@cd_sm + 2), (@cd_u + 8), '확정', 'A', 1000.00, '2026-07-29 23:21:00'
       UNION ALL SELECT (@cd_p + 21), (@cd_a + 27), (@cd_sm + 3), (@cd_u + 31), '확정', 'B', 1000.00, '2026-07-31 18:02:00'
       UNION ALL SELECT (@cd_p + 22), (@cd_a + 30), (@cd_sm + 3), (@cd_u + 33), '확정', 'B', 1000.00, '2026-07-31 18:02:00'
       UNION ALL SELECT (@cd_p + 23), (@cd_a + 29), (@cd_sm + 3), (@cd_u + 34), '확정', 'B', 1000.00, '2026-07-31 18:54:00'
       UNION ALL SELECT (@cd_p + 24), (@cd_a + 28), (@cd_sm + 3), (@cd_u + 30), '확정', 'B', 1000.00, '2026-07-31 19:23:00'
       UNION ALL SELECT (@cd_p + 25), (@cd_a + 24), (@cd_sm + 3), (@cd_u + 75), '확정', 'A', 1000.00, '2026-07-31 19:31:00'
       UNION ALL SELECT (@cd_p + 26), (@cd_a + 21), (@cd_sm + 3), (@cd_u + 67), '확정', 'A', 1000.00, '2026-07-31 19:35:00'
       UNION ALL SELECT (@cd_p + 27), (@cd_a + 26), (@cd_sm + 3), (@cd_u + 29), '확정', 'B', 1000.00, '2026-07-31 19:36:00'
       UNION ALL SELECT (@cd_p + 28), (@cd_a + 25), (@cd_sm + 3), (@cd_u + 70), '확정', 'A', 1000.00, '2026-07-31 19:40:00'
       UNION ALL SELECT (@cd_p + 29), (@cd_a + 22), (@cd_sm + 3), (@cd_u + 74), '확정', 'A', 1000.00, '2026-07-31 20:00:00'
       UNION ALL SELECT (@cd_p + 30), (@cd_a + 23), (@cd_sm + 3), (@cd_u + 72), '확정', 'A', 1000.00, '2026-07-31 20:14:00'
       UNION ALL SELECT (@cd_p + 31), (@cd_a + 40), (@cd_sm + 4), (@cd_u + 28), '확정', 'B', 1000.00, '2026-08-02 15:08:00'
       UNION ALL SELECT (@cd_p + 32), (@cd_a + 37), (@cd_sm + 4), (@cd_u + 25), '확정', 'B', 1000.00, '2026-08-02 15:17:00'
       UNION ALL SELECT (@cd_p + 33), (@cd_a + 38), (@cd_sm + 4), (@cd_u + 23), '확정', 'B', 1000.00, '2026-08-02 15:19:00'
       UNION ALL SELECT (@cd_p + 34), (@cd_a + 36), (@cd_sm + 4), (@cd_u + 24), '확정', 'B', 1000.00, '2026-08-02 16:15:00'
       UNION ALL SELECT (@cd_p + 35), (@cd_a + 39), (@cd_sm + 4), (@cd_u + 26), '확정', 'B', 1000.00, '2026-08-02 16:58:00'
       UNION ALL SELECT (@cd_p + 36), (@cd_a + 35), (@cd_sm + 4), (@cd_u + 3), '확정', 'A', 1032.00, '2026-08-02 23:16:00'
       UNION ALL SELECT (@cd_p + 37), (@cd_a + 33), (@cd_sm + 4), (@cd_u + 5), '확정', 'A', 1032.00, '2026-08-02 23:48:00'
       UNION ALL SELECT (@cd_p + 38), (@cd_a + 34), (@cd_sm + 4), (@cd_u + 2), '확정', 'A', 1032.00, '2026-08-03 00:04:00'
       UNION ALL SELECT (@cd_p + 39), (@cd_a + 32), (@cd_sm + 4), (@cd_u + 6), '확정', 'A', 1000.00, '2026-08-03 00:25:00'
       UNION ALL SELECT (@cd_p + 40), (@cd_a + 31), (@cd_sm + 4), (@cd_u + 1), '확정', 'A', 1032.00, '2026-08-03 00:52:00'
       UNION ALL SELECT (@cd_p + 41), (@cd_a + 44), (@cd_sm + 5), (@cd_u + 45), '확정', 'A', 968.00, '2026-08-05 14:07:00'
       UNION ALL SELECT (@cd_p + 42), (@cd_a + 43), (@cd_sm + 5), (@cd_u + 44), '확정', 'A', 968.00, '2026-08-05 14:35:00'
       UNION ALL SELECT (@cd_p + 43), (@cd_a + 45), (@cd_sm + 5), (@cd_u + 47), '확정', 'A', 968.00, '2026-08-05 14:52:00'
       UNION ALL SELECT (@cd_p + 44), (@cd_a + 42), (@cd_sm + 5), (@cd_u + 48), '확정', 'A', 1000.00, '2026-08-05 15:20:00'
       UNION ALL SELECT (@cd_p + 45), (@cd_a + 41), (@cd_sm + 5), (@cd_u + 43), '확정', 'A', 968.00, '2026-08-05 15:29:00'
       UNION ALL SELECT (@cd_p + 46), (@cd_a + 46), (@cd_sm + 5), (@cd_u + 1), '확정', 'B', 1061.64, '2026-08-07 04:06:00'
       UNION ALL SELECT (@cd_p + 47), (@cd_a + 48), (@cd_sm + 5), (@cd_u + 4), '확정', 'B', 1032.00, '2026-08-07 05:09:00'
       UNION ALL SELECT (@cd_p + 48), (@cd_a + 47), (@cd_sm + 5), (@cd_u + 6), '확정', 'B', 1029.64, '2026-08-07 05:22:00'
       UNION ALL SELECT (@cd_p + 49), (@cd_a + 50), (@cd_sm + 5), (@cd_u + 2), '확정', 'B', 1061.64, '2026-08-07 05:32:00'
       UNION ALL SELECT (@cd_p + 50), (@cd_a + 49), (@cd_sm + 5), (@cd_u + 5), '확정', 'B', 1061.64, '2026-08-07 05:53:00'
       UNION ALL SELECT (@cd_p + 51), (@cd_a + 56), (@cd_sm + 6), (@cd_u + 15), '확정', 'B', 1000.00, '2026-08-05 23:10:00'
       UNION ALL SELECT (@cd_p + 52), (@cd_a + 57), (@cd_sm + 6), (@cd_u + 16), '확정', 'B', 1000.00, '2026-08-05 23:40:00'
       UNION ALL SELECT (@cd_p + 53), (@cd_a + 59), (@cd_sm + 6), (@cd_u + 19), '확정', 'B', 1000.00, '2026-08-06 00:02:00'
       UNION ALL SELECT (@cd_p + 54), (@cd_a + 60), (@cd_sm + 6), (@cd_u + 17), '확정', 'B', 1000.00, '2026-08-06 00:11:00'
       UNION ALL SELECT (@cd_p + 55), (@cd_a + 58), (@cd_sm + 6), (@cd_u + 20), '확정', 'B', 1000.00, '2026-08-06 00:26:00'
       UNION ALL SELECT (@cd_p + 56), (@cd_a + 53), (@cd_sm + 6), (@cd_u + 11), '확정', 'A', 1000.00, '2026-08-07 02:18:00'
       UNION ALL SELECT (@cd_p + 57), (@cd_a + 55), (@cd_sm + 6), (@cd_u + 14), '확정', 'A', 1032.00, '2026-08-07 02:54:00'
       UNION ALL SELECT (@cd_p + 58), (@cd_a + 52), (@cd_sm + 6), (@cd_u + 12), '확정', 'A', 1000.00, '2026-08-07 03:03:00'
       UNION ALL SELECT (@cd_p + 59), (@cd_a + 54), (@cd_sm + 6), (@cd_u + 9), '확정', 'A', 1032.00, '2026-08-07 03:52:00'
       UNION ALL SELECT (@cd_p + 60), (@cd_a + 51), (@cd_sm + 6), (@cd_u + 8), '확정', 'A', 1032.00, '2026-08-07 03:55:00'
       UNION ALL SELECT (@cd_p + 61), (@cd_a + 63), (@cd_sm + 7), (@cd_u + 6), '확정', 'A', 1054.84, '2026-08-09 20:53:00'
       UNION ALL SELECT (@cd_p + 62), (@cd_a + 64), (@cd_sm + 7), (@cd_u + 4), '확정', 'A', 1057.20, '2026-08-09 21:08:00'
       UNION ALL SELECT (@cd_p + 63), (@cd_a + 65), (@cd_sm + 7), (@cd_u + 2), '확정', 'A', 1086.84, '2026-08-09 21:44:00'
       UNION ALL SELECT (@cd_p + 64), (@cd_a + 61), (@cd_sm + 7), (@cd_u + 1), '확정', 'A', 1086.84, '2026-08-09 21:51:00'
       UNION ALL SELECT (@cd_p + 65), (@cd_a + 62), (@cd_sm + 7), (@cd_u + 3), '확정', 'A', 1061.64, '2026-08-09 21:56:00'
       UNION ALL SELECT (@cd_p + 66), (@cd_a + 70), (@cd_sm + 7), (@cd_u + 63), '확정', 'B', 1000.00, '2026-08-10 17:04:00'
       UNION ALL SELECT (@cd_p + 67), (@cd_a + 66), (@cd_sm + 7), (@cd_u + 57), '확정', 'B', 968.00, '2026-08-10 18:00:00'
       UNION ALL SELECT (@cd_p + 68), (@cd_a + 69), (@cd_sm + 7), (@cd_u + 60), '확정', 'B', 1000.00, '2026-08-10 18:31:00'
       UNION ALL SELECT (@cd_p + 69), (@cd_a + 67), (@cd_sm + 7), (@cd_u + 62), '확정', 'B', 1000.00, '2026-08-10 18:52:00'
       UNION ALL SELECT (@cd_p + 70), (@cd_a + 68), (@cd_sm + 7), (@cd_u + 64), '확정', 'B', 1000.00, '2026-08-10 18:53:00'
       UNION ALL SELECT (@cd_p + 71), (@cd_a + 76), (@cd_sm + 8), (@cd_u + 18), '확정', 'B', 1000.00, '2026-08-11 12:02:00'
       UNION ALL SELECT (@cd_p + 72), (@cd_a + 80), (@cd_sm + 8), (@cd_u + 20), '확정', 'B', 969.77, '2026-08-11 12:50:00'
       UNION ALL SELECT (@cd_p + 73), (@cd_a + 78), (@cd_sm + 8), (@cd_u + 17), '확정', 'B', 969.77, '2026-08-11 13:40:00'
       UNION ALL SELECT (@cd_p + 74), (@cd_a + 79), (@cd_sm + 8), (@cd_u + 16), '확정', 'B', 969.77, '2026-08-11 13:42:00'
       UNION ALL SELECT (@cd_p + 75), (@cd_a + 77), (@cd_sm + 8), (@cd_u + 19), '확정', 'B', 969.77, '2026-08-11 13:48:00'
       UNION ALL SELECT (@cd_p + 76), (@cd_a + 75), (@cd_sm + 8), (@cd_u + 54), '확정', 'A', 1000.00, '2026-08-12 21:14:00'
       UNION ALL SELECT (@cd_p + 77), (@cd_a + 71), (@cd_sm + 8), (@cd_u + 50), '확정', 'A', 1000.00, '2026-08-12 21:43:00'
       UNION ALL SELECT (@cd_p + 78), (@cd_a + 73), (@cd_sm + 8), (@cd_u + 53), '확정', 'A', 1000.00, '2026-08-12 21:57:00'
       UNION ALL SELECT (@cd_p + 79), (@cd_a + 74), (@cd_sm + 8), (@cd_u + 56), '확정', 'A', 1000.00, '2026-08-12 22:05:00'
       UNION ALL SELECT (@cd_p + 80), (@cd_a + 72), (@cd_sm + 8), (@cd_u + 51), '확정', 'A', 1000.00, '2026-08-12 22:53:00'
       UNION ALL SELECT (@cd_p + 81), (@cd_a + 90), (@cd_sm + 9), (@cd_u + 5), '확정', 'B', 1086.84, '2026-08-14 04:03:00'
       UNION ALL SELECT (@cd_p + 82), (@cd_a + 89), (@cd_sm + 9), (@cd_u + 3), '확정', 'B', 1086.76, '2026-08-14 04:10:00'
       UNION ALL SELECT (@cd_p + 83), (@cd_a + 87), (@cd_sm + 9), (@cd_u + 4), '확정', 'B', 1082.32, '2026-08-14 04:13:00'
       UNION ALL SELECT (@cd_p + 84), (@cd_a + 88), (@cd_sm + 9), (@cd_u + 6), '확정', 'B', 1079.96, '2026-08-14 04:20:00'
       UNION ALL SELECT (@cd_p + 85), (@cd_a + 86), (@cd_sm + 9), (@cd_u + 1), '확정', 'B', 1111.96, '2026-08-14 04:57:00'
       UNION ALL SELECT (@cd_p + 86), (@cd_a + 82), (@cd_sm + 9), (@cd_u + 69), '확정', 'A', 1000.00, '2026-08-14 15:38:00'
       UNION ALL SELECT (@cd_p + 87), (@cd_a + 81), (@cd_sm + 9), (@cd_u + 67), '확정', 'A', 1032.00, '2026-08-14 15:45:00'
       UNION ALL SELECT (@cd_p + 88), (@cd_a + 85), (@cd_sm + 9), (@cd_u + 73), '확정', 'A', 1000.00, '2026-08-14 15:58:00'
       UNION ALL SELECT (@cd_p + 89), (@cd_a + 84), (@cd_sm + 9), (@cd_u + 71), '확정', 'A', 1000.00, '2026-08-14 16:14:00'
       UNION ALL SELECT (@cd_p + 90), (@cd_a + 83), (@cd_sm + 9), (@cd_u + 68), '확정', 'A', 1000.00, '2026-08-14 16:56:00'
       UNION ALL SELECT (@cd_p + 91), (@cd_a + 92), (@cd_sm + 10), (@cd_u + 52), '확정', 'A', 1000.00, '2026-08-17 11:03:00'
       UNION ALL SELECT (@cd_p + 92), (@cd_a + 91), (@cd_sm + 10), (@cd_u + 54), '확정', 'A', 965.78, '2026-08-17 11:15:00'
       UNION ALL SELECT (@cd_p + 93), (@cd_a + 94), (@cd_sm + 10), (@cd_u + 51), '확정', 'A', 965.78, '2026-08-17 11:45:00'
       UNION ALL SELECT (@cd_p + 94), (@cd_a + 95), (@cd_sm + 10), (@cd_u + 56), '확정', 'A', 965.78, '2026-08-17 12:56:00'
       UNION ALL SELECT (@cd_p + 95), (@cd_a + 93), (@cd_sm + 10), (@cd_u + 55), '확정', 'A', 1000.00, '2026-08-17 12:59:00'
       UNION ALL SELECT (@cd_p + 96), (@cd_a + 100), (@cd_sm + 10), (@cd_u + 12), '확정', 'B', 1030.23, '2026-08-17 17:08:00'
       UNION ALL SELECT (@cd_p + 97), (@cd_a + 99), (@cd_sm + 10), (@cd_u + 11), '확정', 'B', 1030.23, '2026-08-17 18:09:00'
       UNION ALL SELECT (@cd_p + 98), (@cd_a + 97), (@cd_sm + 10), (@cd_u + 13), '확정', 'B', 1032.00, '2026-08-17 18:28:00'
       UNION ALL SELECT (@cd_p + 99), (@cd_a + 98), (@cd_sm + 10), (@cd_u + 10), '확정', 'B', 1032.00, '2026-08-17 18:30:00'
       UNION ALL SELECT (@cd_p + 100), (@cd_a + 96), (@cd_sm + 10), (@cd_u + 14), '확정', 'B', 1062.23, '2026-08-17 18:47:00'
       UNION ALL SELECT (@cd_p + 101), (@cd_a + 106), (@cd_sm + 11), (@cd_u + 2), '확정', 'B', 1111.96, '2026-08-19 08:01:00'
       UNION ALL SELECT (@cd_p + 102), (@cd_a + 101), (@cd_sm + 11), (@cd_u + 47), '확정', 'A', 942.80, '2026-08-19 08:06:00'
       UNION ALL SELECT (@cd_p + 103), (@cd_a + 107), (@cd_sm + 11), (@cd_u + 6), '확정', 'B', 1104.44, '2026-08-19 08:14:00'
       UNION ALL SELECT (@cd_p + 104), (@cd_a + 105), (@cd_sm + 11), (@cd_u + 44), '확정', 'A', 942.80, '2026-08-19 08:22:00'
       UNION ALL SELECT (@cd_p + 105), (@cd_a + 109), (@cd_sm + 11), (@cd_u + 3), '확정', 'B', 1111.24, '2026-08-19 08:23:00'
       UNION ALL SELECT (@cd_p + 106), (@cd_a + 104), (@cd_sm + 11), (@cd_u + 45), '확정', 'A', 942.80, '2026-08-19 08:27:00'
       UNION ALL SELECT (@cd_p + 107), (@cd_a + 102), (@cd_sm + 11), (@cd_u + 48), '확정', 'A', 974.80, '2026-08-19 08:47:00'
       UNION ALL SELECT (@cd_p + 108), (@cd_a + 110), (@cd_sm + 11), (@cd_u + 4), '확정', 'B', 1106.80, '2026-08-19 08:50:00'
       UNION ALL SELECT (@cd_p + 109), (@cd_a + 103), (@cd_sm + 11), (@cd_u + 46), '확정', 'A', 968.00, '2026-08-19 09:23:00'
       UNION ALL SELECT (@cd_p + 110), (@cd_a + 108), (@cd_sm + 11), (@cd_u + 5), '확정', 'B', 1111.32, '2026-08-19 09:50:00'
       UNION ALL SELECT (@cd_p + 111), (@cd_a + 120), (@cd_sm + 12), (@cd_u + 26), '확정', 'B', 970.36, '2026-08-19 23:18:00'
       UNION ALL SELECT (@cd_p + 112), (@cd_a + 119), (@cd_sm + 12), (@cd_u + 24), '확정', 'B', 970.36, '2026-08-19 23:28:00'
       UNION ALL SELECT (@cd_p + 113), (@cd_a + 117), (@cd_sm + 12), (@cd_u + 27), '확정', 'B', 1000.00, '2026-08-20 00:02:00'
       UNION ALL SELECT (@cd_p + 114), (@cd_a + 116), (@cd_sm + 12), (@cd_u + 22), '확정', 'B', 1000.00, '2026-08-20 00:08:00'
       UNION ALL SELECT (@cd_p + 115), (@cd_a + 118), (@cd_sm + 12), (@cd_u + 25), '확정', 'B', 970.36, '2026-08-20 00:31:00'
       UNION ALL SELECT (@cd_p + 116), (@cd_a + 111), (@cd_sm + 12), (@cd_u + 29), '확정', 'A', 968.00, '2026-08-20 02:30:00'
       UNION ALL SELECT (@cd_p + 117), (@cd_a + 114), (@cd_sm + 12), (@cd_u + 31), '확정', 'A', 968.00, '2026-08-20 02:53:00'
       UNION ALL SELECT (@cd_p + 118), (@cd_a + 113), (@cd_sm + 12), (@cd_u + 34), '확정', 'A', 968.00, '2026-08-20 03:13:00'
       UNION ALL SELECT (@cd_p + 119), (@cd_a + 112), (@cd_sm + 12), (@cd_u + 32), '확정', 'A', 1000.00, '2026-08-20 03:38:00'
       UNION ALL SELECT (@cd_p + 120), (@cd_a + 115), (@cd_sm + 12), (@cd_u + 30), '확정', 'A', 968.00, '2026-08-20 03:55:00'
       UNION ALL SELECT (@cd_p + 121), (@cd_a + 123), (@cd_sm + 13), (@cd_u + 3), '확정', 'A', 1129.85, '2026-08-24 16:22:00'
       UNION ALL SELECT (@cd_p + 122), (@cd_a + 124), (@cd_sm + 13), (@cd_u + 6), '확정', 'A', 1123.05, '2026-08-24 16:32:00'
       UNION ALL SELECT (@cd_p + 123), (@cd_a + 128), (@cd_sm + 13), (@cd_u + 32), '확정', 'B', 968.72, '2026-08-24 17:19:00'
       UNION ALL SELECT (@cd_p + 124), (@cd_a + 130), (@cd_sm + 13), (@cd_u + 34), '확정', 'B', 936.72, '2026-08-24 17:19:00'
       UNION ALL SELECT (@cd_p + 125), (@cd_a + 121), (@cd_sm + 13), (@cd_u + 1), '확정', 'A', 1136.44, '2026-08-24 17:22:00'
       UNION ALL SELECT (@cd_p + 126), (@cd_a + 126), (@cd_sm + 13), (@cd_u + 29), '확정', 'B', 936.72, '2026-08-24 17:37:00'
       UNION ALL SELECT (@cd_p + 127), (@cd_a + 122), (@cd_sm + 13), (@cd_u + 2), '확정', 'A', 1130.57, '2026-08-24 17:52:00'
       UNION ALL SELECT (@cd_p + 128), (@cd_a + 125), (@cd_sm + 13), (@cd_u + 4), '확정', 'A', 1125.41, '2026-08-24 17:55:00'
       UNION ALL SELECT (@cd_p + 129), (@cd_a + 129), (@cd_sm + 13), (@cd_u + 30), '확정', 'B', 936.72, '2026-08-24 19:00:00'
       UNION ALL SELECT (@cd_p + 130), (@cd_a + 127), (@cd_sm + 13), (@cd_u + 33), '확정', 'B', 968.00, '2026-08-24 19:00:00'
       UNION ALL SELECT (@cd_p + 131), (@cd_a + 139), (@cd_sm + 14), (@cd_u + 28), '확정', 'B', 970.36, '2026-08-25 12:40:00'
       UNION ALL SELECT (@cd_p + 132), (@cd_a + 136), (@cd_sm + 14), (@cd_u + 22), '확정', 'B', 1031.28, '2026-08-25 12:41:00'
       UNION ALL SELECT (@cd_p + 133), (@cd_a + 138), (@cd_sm + 14), (@cd_u + 23), '확정', 'B', 970.36, '2026-08-25 12:47:00'
       UNION ALL SELECT (@cd_p + 134), (@cd_a + 137), (@cd_sm + 14), (@cd_u + 27), '확정', 'B', 1031.28, '2026-08-25 13:45:00'
       UNION ALL SELECT (@cd_p + 135), (@cd_a + 140), (@cd_sm + 14), (@cd_u + 24), '확정', 'B', 1001.64, '2026-08-25 13:49:00'
       UNION ALL SELECT (@cd_p + 136), (@cd_a + 132), (@cd_sm + 14), (@cd_u + 18), '확정', 'A', 1034.22, '2026-08-25 18:07:00'
       UNION ALL SELECT (@cd_p + 137), (@cd_a + 134), (@cd_sm + 14), (@cd_u + 19), '확정', 'A', 1003.99, '2026-08-25 18:35:00'
       UNION ALL SELECT (@cd_p + 138), (@cd_a + 135), (@cd_sm + 14), (@cd_u + 20), '확정', 'A', 1003.99, '2026-08-25 18:36:00'
       UNION ALL SELECT (@cd_p + 139), (@cd_a + 131), (@cd_sm + 14), (@cd_u + 15), '확정', 'A', 969.77, '2026-08-25 19:31:00'
       UNION ALL SELECT (@cd_p + 140), (@cd_a + 133), (@cd_sm + 14), (@cd_u + 16), '확정', 'A', 1003.99, '2026-08-25 19:42:00'
       UNION ALL SELECT (@cd_p + 141), (@cd_a + 150), (@cd_sm + 15), (@cd_u + 4), '확정', 'B', 1133.80, '2026-08-28 02:13:00'
       UNION ALL SELECT (@cd_p + 142), (@cd_a + 148), (@cd_sm + 15), (@cd_u + 3), '확정', 'B', 1138.24, '2026-08-28 02:35:00'
       UNION ALL SELECT (@cd_p + 143), (@cd_a + 149), (@cd_sm + 15), (@cd_u + 6), '확정', 'B', 1131.44, '2026-08-28 02:53:00'
       UNION ALL SELECT (@cd_p + 144), (@cd_a + 147), (@cd_sm + 15), (@cd_u + 5), '확정', 'B', 1129.93, '2026-08-28 03:20:00'
       UNION ALL SELECT (@cd_p + 145), (@cd_a + 146), (@cd_sm + 15), (@cd_u + 1), '확정', 'B', 1144.83, '2026-08-28 03:32:00'
       UNION ALL SELECT (@cd_p + 146), (@cd_a + 142), (@cd_sm + 15), (@cd_u + 68), '확정', 'A', 975.52, '2026-08-28 11:37:00'
       UNION ALL SELECT (@cd_p + 147), (@cd_a + 143), (@cd_sm + 15), (@cd_u + 71), '확정', 'A', 975.52, '2026-08-28 12:04:00'
       UNION ALL SELECT (@cd_p + 148), (@cd_a + 144), (@cd_sm + 15), (@cd_u + 70), '확정', 'A', 1032.00, '2026-08-28 12:19:00'
       UNION ALL SELECT (@cd_p + 149), (@cd_a + 145), (@cd_sm + 15), (@cd_u + 73), '확정', 'A', 975.52, '2026-08-28 12:48:00'
       UNION ALL SELECT (@cd_p + 150), (@cd_a + 141), (@cd_sm + 15), (@cd_u + 67), '확정', 'A', 1007.52, '2026-08-28 12:50:00'
       UNION ALL SELECT (@cd_p + 151), (@cd_a + 157), (@cd_sm + 16), (@cd_u + 61), '확정', 'B', 968.00, '2026-08-29 14:04:00'
       UNION ALL SELECT (@cd_p + 152), (@cd_a + 160), (@cd_sm + 16), (@cd_u + 58), '확정', 'B', 968.00, '2026-08-29 14:11:00'
       UNION ALL SELECT (@cd_p + 153), (@cd_a + 158), (@cd_sm + 16), (@cd_u + 60), '확정', 'B', 974.88, '2026-08-29 14:44:00'
       UNION ALL SELECT (@cd_p + 154), (@cd_a + 156), (@cd_sm + 16), (@cd_u + 57), '확정', 'B', 942.88, '2026-08-29 15:29:00'
       UNION ALL SELECT (@cd_p + 155), (@cd_a + 159), (@cd_sm + 16), (@cd_u + 59), '확정', 'B', 968.00, '2026-08-29 15:50:00'
       UNION ALL SELECT (@cd_p + 156), (@cd_a + 154), (@cd_sm + 16), (@cd_u + 55), '확정', 'A', 1005.28, '2026-08-29 20:05:00'
       UNION ALL SELECT (@cd_p + 157), (@cd_a + 152), (@cd_sm + 16), (@cd_u + 52), '확정', 'A', 1005.28, '2026-08-29 20:56:00'
       UNION ALL SELECT (@cd_p + 158), (@cd_a + 153), (@cd_sm + 16), (@cd_u + 53), '확정', 'A', 965.78, '2026-08-29 21:02:00'
       UNION ALL SELECT (@cd_p + 159), (@cd_a + 151), (@cd_sm + 16), (@cd_u + 50), '확정', 'A', 965.78, '2026-08-29 21:14:00'
       UNION ALL SELECT (@cd_p + 160), (@cd_a + 155), (@cd_sm + 16), (@cd_u + 56), '확정', 'A', 971.06, '2026-08-29 21:51:00'
       UNION ALL SELECT (@cd_p + 161), (@cd_a + 165), (@cd_sm + 17), (@cd_u + 26), '확정', 'A', 1001.64, '2026-08-31 17:38:00'
       UNION ALL SELECT (@cd_p + 162), (@cd_a + 164), (@cd_sm + 17), (@cd_u + 25), '확정', 'A', 1001.64, '2026-08-31 17:48:00'
       UNION ALL SELECT (@cd_p + 163), (@cd_a + 161), (@cd_sm + 17), (@cd_u + 22), '확정', 'A', 999.48, '2026-08-31 17:59:00'
       UNION ALL SELECT (@cd_p + 164), (@cd_a + 162), (@cd_sm + 17), (@cd_u + 27), '확정', 'A', 999.48, '2026-08-31 18:06:00'
       UNION ALL SELECT (@cd_p + 165), (@cd_a + 167), (@cd_sm + 17), (@cd_u + 48), '확정', 'B', 956.19, '2026-08-31 18:12:00'
       UNION ALL SELECT (@cd_p + 166), (@cd_a + 166), (@cd_sm + 17), (@cd_u + 43), '확정', 'B', 942.80, '2026-08-31 18:34:00'
       UNION ALL SELECT (@cd_p + 167), (@cd_a + 169), (@cd_sm + 17), (@cd_u + 44), '확정', 'B', 924.19, '2026-08-31 18:47:00'
       UNION ALL SELECT (@cd_p + 168), (@cd_a + 168), (@cd_sm + 17), (@cd_u + 46), '확정', 'B', 949.39, '2026-08-31 18:56:00'
       UNION ALL SELECT (@cd_p + 169), (@cd_a + 163), (@cd_sm + 17), (@cd_u + 23), '확정', 'A', 938.56, '2026-08-31 18:58:00'
       UNION ALL SELECT (@cd_p + 170), (@cd_a + 170), (@cd_sm + 17), (@cd_u + 45), '확정', 'B', 924.19, '2026-08-31 19:10:00'
       UNION ALL SELECT (@cd_p + 171), (@cd_a + 172), (@cd_sm + 18), (@cd_u + 17), '확정', 'A', 1003.99, '2026-08-31 17:04:00'
       UNION ALL SELECT (@cd_p + 172), (@cd_a + 175), (@cd_sm + 18), (@cd_u + 19), '확정', 'A', 1035.79, '2026-08-31 17:13:00'
       UNION ALL SELECT (@cd_p + 173), (@cd_a + 171), (@cd_sm + 18), (@cd_u + 15), '확정', 'A', 1001.57, '2026-08-31 17:31:00'
       UNION ALL SELECT (@cd_p + 174), (@cd_a + 173), (@cd_sm + 18), (@cd_u + 18), '확정', 'A', 1066.02, '2026-08-31 17:56:00'
       UNION ALL SELECT (@cd_p + 175), (@cd_a + 174), (@cd_sm + 18), (@cd_u + 16), '확정', 'A', 1035.79, '2026-08-31 18:50:00'
       UNION ALL SELECT (@cd_p + 176), (@cd_a + 176), (@cd_sm + 18), (@cd_u + 67), '확정', 'B', 1019.95, '2026-09-02 20:08:00'
       UNION ALL SELECT (@cd_p + 177), (@cd_a + 177), (@cd_sm + 18), (@cd_u + 75), '확정', 'B', 1032.00, '2026-09-02 20:14:00'
       UNION ALL SELECT (@cd_p + 178), (@cd_a + 180), (@cd_sm + 18), (@cd_u + 69), '확정', 'B', 975.52, '2026-09-02 20:54:00'
       UNION ALL SELECT (@cd_p + 179), (@cd_a + 178), (@cd_sm + 18), (@cd_u + 72), '확정', 'B', 1032.00, '2026-09-02 21:36:00'
       UNION ALL SELECT (@cd_p + 180), (@cd_a + 179), (@cd_sm + 18), (@cd_u + 74), '확정', 'B', 1032.00, '2026-09-02 21:39:00'
       UNION ALL SELECT (@cd_p + 181), (@cd_a + 182), (@cd_sm + 19), (@cd_u + 12), '확정', 'A', 1024.95, '2026-08-31 22:04:00'
       UNION ALL SELECT (@cd_p + 182), (@cd_a + 183), (@cd_sm + 19), (@cd_u + 9), '확정', 'A', 1062.23, '2026-08-31 22:37:00'
       UNION ALL SELECT (@cd_p + 183), (@cd_a + 185), (@cd_sm + 19), (@cd_u + 13), '확정', 'A', 1026.72, '2026-08-31 23:01:00'
       UNION ALL SELECT (@cd_p + 184), (@cd_a + 181), (@cd_sm + 19), (@cd_u + 8), '확정', 'A', 1062.23, '2026-08-31 23:06:00'
       UNION ALL SELECT (@cd_p + 185), (@cd_a + 184), (@cd_sm + 19), (@cd_u + 10), '확정', 'A', 1026.72, '2026-08-31 23:58:00'
       UNION ALL SELECT (@cd_p + 186), (@cd_a + 189), (@cd_sm + 19), (@cd_u + 3), '확정', 'B', 1132.03, '2026-09-02 02:01:00'
       UNION ALL SELECT (@cd_p + 187), (@cd_a + 186), (@cd_sm + 19), (@cd_u + 1), '확정', 'B', 1138.62, '2026-09-02 02:14:00'
       UNION ALL SELECT (@cd_p + 188), (@cd_a + 190), (@cd_sm + 19), (@cd_u + 4), '확정', 'B', 1127.59, '2026-09-02 03:00:00'
       UNION ALL SELECT (@cd_p + 189), (@cd_a + 188), (@cd_sm + 19), (@cd_u + 2), '확정', 'B', 1138.96, '2026-09-02 03:39:00'
       UNION ALL SELECT (@cd_p + 190), (@cd_a + 187), (@cd_sm + 19), (@cd_u + 5), '확정', 'B', 1123.72, '2026-09-02 03:48:00'
       UNION ALL SELECT (@cd_p + 191), (@cd_a + 195), (@cd_sm + 20), (@cd_u + 34), '확정', 'A', 919.94, '2026-09-03 23:01:00'
       UNION ALL SELECT (@cd_p + 192), (@cd_a + 192), (@cd_sm + 20), (@cd_u + 32), '확정', 'A', 951.94, '2026-09-03 23:21:00'
       UNION ALL SELECT (@cd_p + 193), (@cd_a + 191), (@cd_sm + 20), (@cd_u + 29), '확정', 'A', 919.94, '2026-09-03 23:56:00'
       UNION ALL SELECT (@cd_p + 194), (@cd_a + 194), (@cd_sm + 20), (@cd_u + 31), '확정', 'A', 936.72, '2026-09-03 23:57:00'
       UNION ALL SELECT (@cd_p + 195), (@cd_a + 193), (@cd_sm + 20), (@cd_u + 33), '확정', 'A', 951.22, '2026-09-04 00:27:00'
       UNION ALL SELECT (@cd_p + 196), (@cd_a + 198), (@cd_sm + 20), (@cd_u + 62), '확정', 'B', 974.88, '2026-09-04 07:06:00'
       UNION ALL SELECT (@cd_p + 197), (@cd_a + 196), (@cd_sm + 20), (@cd_u + 57), '확정', 'B', 912.56, '2026-09-04 07:14:00'
       UNION ALL SELECT (@cd_p + 198), (@cd_a + 199), (@cd_sm + 20), (@cd_u + 64), '확정', 'B', 974.88, '2026-09-04 07:32:00'
       UNION ALL SELECT (@cd_p + 199), (@cd_a + 200), (@cd_sm + 20), (@cd_u + 65), '확정', 'B', 968.00, '2026-09-04 07:36:00'
       UNION ALL SELECT (@cd_p + 200), (@cd_a + 197), (@cd_sm + 20), (@cd_u + 63), '확정', 'B', 974.88, '2026-09-04 08:29:00'
       UNION ALL SELECT (@cd_p + 201), (@cd_a + 204), (@cd_sm + 21), (@cd_u + 2), '확정', 'A', 1150.84, '2026-09-06 16:17:00'
       UNION ALL SELECT (@cd_p + 202), (@cd_a + 202), (@cd_sm + 21), (@cd_u + 5), '확정', 'A', 1135.60, '2026-09-06 16:30:00'
       UNION ALL SELECT (@cd_p + 203), (@cd_a + 203), (@cd_sm + 21), (@cd_u + 6), '확정', 'A', 1125.23, '2026-09-06 16:56:00'
       UNION ALL SELECT (@cd_p + 204), (@cd_a + 201), (@cd_sm + 21), (@cd_u + 3), '확정', 'A', 1143.91, '2026-09-06 17:04:00'
       UNION ALL SELECT (@cd_p + 205), (@cd_a + 205), (@cd_sm + 21), (@cd_u + 4), '확정', 'A', 1139.47, '2026-09-06 17:23:00'
       UNION ALL SELECT (@cd_p + 206), (@cd_a + 210), (@cd_sm + 21), (@cd_u + 74), '확정', 'B', 1000.95, '2026-09-07 18:36:00'
       UNION ALL SELECT (@cd_p + 207), (@cd_a + 207), (@cd_sm + 21), (@cd_u + 68), '확정', 'B', 987.95, '2026-09-07 18:58:00'
       UNION ALL SELECT (@cd_p + 208), (@cd_a + 206), (@cd_sm + 21), (@cd_u + 67), '확정', 'B', 988.90, '2026-09-07 19:33:00'
       UNION ALL SELECT (@cd_p + 209), (@cd_a + 208), (@cd_sm + 21), (@cd_u + 75), '확정', 'B', 1000.95, '2026-09-07 19:42:00'
       UNION ALL SELECT (@cd_p + 210), (@cd_a + 209), (@cd_sm + 21), (@cd_u + 70), '확정', 'B', 1044.43, '2026-09-07 19:58:00'
       UNION ALL SELECT (@cd_p + 211), (@cd_a + 212), (@cd_sm + 22), (@cd_u + 51), '확정', 'A', 971.06, '2026-09-08 11:46:00'
       UNION ALL SELECT (@cd_p + 212), (@cd_a + 215), (@cd_sm + 22), (@cd_u + 55), '확정', 'A', 1035.60, '2026-09-08 12:07:00'
       UNION ALL SELECT (@cd_p + 213), (@cd_a + 211), (@cd_sm + 22), (@cd_u + 50), '확정', 'A', 996.10, '2026-09-08 12:22:00'
       UNION ALL SELECT (@cd_p + 214), (@cd_a + 214), (@cd_sm + 22), (@cd_u + 52), '확정', 'A', 1035.60, '2026-09-08 12:39:00'
       UNION ALL SELECT (@cd_p + 215), (@cd_a + 213), (@cd_sm + 22), (@cd_u + 54), '확정', 'A', 971.06, '2026-09-08 12:59:00'
       UNION ALL SELECT (@cd_p + 216), (@cd_a + 217), (@cd_sm + 22), (@cd_u + 58), '확정', 'B', 937.68, '2026-09-08 15:28:00'
       UNION ALL SELECT (@cd_p + 217), (@cd_a + 220), (@cd_sm + 22), (@cd_u + 63), '확정', 'B', 940.58, '2026-09-08 15:40:00'
       UNION ALL SELECT (@cd_p + 218), (@cd_a + 218), (@cd_sm + 22), (@cd_u + 62), '확정', 'B', 940.58, '2026-09-08 15:52:00'
       UNION ALL SELECT (@cd_p + 219), (@cd_a + 219), (@cd_sm + 22), (@cd_u + 59), '확정', 'B', 937.68, '2026-09-08 16:05:00'
       UNION ALL SELECT (@cd_p + 220), (@cd_a + 216), (@cd_sm + 22), (@cd_u + 57), '확정', 'B', 878.26, '2026-09-08 16:43:00'
       UNION ALL SELECT (@cd_p + 221), (@cd_a + 225), (@cd_sm + 23), (@cd_u + 37), '확정', 'A', 1000.00, '2026-09-10 17:28:00'
       UNION ALL SELECT (@cd_p + 222), (@cd_a + 221), (@cd_sm + 23), (@cd_u + 36), '확정', 'A', 1000.00, '2026-09-10 17:39:00'
       UNION ALL SELECT (@cd_p + 223), (@cd_a + 222), (@cd_sm + 23), (@cd_u + 42), '확정', 'A', 1000.00, '2026-09-10 18:14:00'
       UNION ALL SELECT (@cd_p + 224), (@cd_a + 223), (@cd_sm + 23), (@cd_u + 41), '확정', 'A', 1000.00, '2026-09-10 18:47:00'
       UNION ALL SELECT (@cd_p + 225), (@cd_a + 224), (@cd_sm + 23), (@cd_u + 38), '확정', 'A', 1000.00, '2026-09-10 18:57:00'
       UNION ALL SELECT (@cd_p + 226), (@cd_a + 230), (@cd_sm + 23), (@cd_u + 4), '확정', 'B', 1149.57, '2026-09-11 13:35:00'
       UNION ALL SELECT (@cd_p + 227), (@cd_a + 228), (@cd_sm + 23), (@cd_u + 5), '확정', 'B', 1145.70, '2026-09-11 13:39:00'
       UNION ALL SELECT (@cd_p + 228), (@cd_a + 227), (@cd_sm + 23), (@cd_u + 6), '확정', 'B', 1135.33, '2026-09-11 14:23:00'
       UNION ALL SELECT (@cd_p + 229), (@cd_a + 226), (@cd_sm + 23), (@cd_u + 1), '확정', 'B', 1150.50, '2026-09-11 14:32:00'
       UNION ALL SELECT (@cd_p + 230), (@cd_a + 229), (@cd_sm + 23), (@cd_u + 2), '확정', 'B', 1160.94, '2026-09-11 14:42:00'
       UNION ALL SELECT (@cd_p + 231), (@cd_a + 237), (@cd_sm + 24), (@cd_u + 2), '확정', 'B', 1170.49, '2026-09-15 12:07:00'
       UNION ALL SELECT (@cd_p + 232), (@cd_a + 238), (@cd_sm + 24), (@cd_u + 5), '확정', 'B', 1155.25, '2026-09-15 13:26:00'
       UNION ALL SELECT (@cd_p + 233), (@cd_a + 239), (@cd_sm + 24), (@cd_u + 6), '확정', 'B', 1144.88, '2026-09-15 13:33:00'
       UNION ALL SELECT (@cd_p + 234), (@cd_a + 240), (@cd_sm + 24), (@cd_u + 3), '확정', 'B', 1154.01, '2026-09-15 13:35:00'
       UNION ALL SELECT (@cd_p + 235), (@cd_a + 236), (@cd_sm + 24), (@cd_u + 1), '확정', 'B', 1160.05, '2026-09-15 13:48:00'
       UNION ALL SELECT (@cd_p + 236), (@cd_a + 235), (@cd_sm + 24), (@cd_u + 25), '확정', 'A', 1029.17, '2026-09-15 18:14:00'
       UNION ALL SELECT (@cd_p + 237), (@cd_a + 232), (@cd_sm + 24), (@cd_u + 28), '확정', 'A', 938.56, '2026-09-15 19:07:00'
       UNION ALL SELECT (@cd_p + 238), (@cd_a + 234), (@cd_sm + 24), (@cd_u + 26), '확정', 'A', 1029.17, '2026-09-15 19:14:00'
       UNION ALL SELECT (@cd_p + 239), (@cd_a + 231), (@cd_sm + 24), (@cd_u + 24), '확정', 'A', 969.84, '2026-09-15 19:19:00'
       UNION ALL SELECT (@cd_p + 240), (@cd_a + 233), (@cd_sm + 24), (@cd_u + 27), '확정', 'A', 1027.01, '2026-09-15 19:44:00'
       UNION ALL SELECT (@cd_p + 241), (@cd_a + 247), (@cd_sm + 25), (@cd_u + 60), '확정', 'B', 944.56, '2026-09-15 21:23:00'
       UNION ALL SELECT (@cd_p + 242), (@cd_a + 249), (@cd_sm + 25), (@cd_u + 64), '확정', 'B', 940.58, '2026-09-15 22:29:00'
       UNION ALL SELECT (@cd_p + 243), (@cd_a + 250), (@cd_sm + 25), (@cd_u + 65), '확정', 'B', 933.70, '2026-09-15 22:29:00'
       UNION ALL SELECT (@cd_p + 244), (@cd_a + 246), (@cd_sm + 25), (@cd_u + 57), '확정', 'B', 917.06, '2026-09-15 22:36:00'
       UNION ALL SELECT (@cd_p + 245), (@cd_a + 248), (@cd_sm + 25), (@cd_u + 61), '확정', 'B', 937.68, '2026-09-15 22:54:00'
       UNION ALL SELECT (@cd_p + 246), (@cd_a + 243), (@cd_sm + 25), (@cd_u + 17), '확정', 'A', 1035.04, '2026-09-16 20:26:00'
       UNION ALL SELECT (@cd_p + 247), (@cd_a + 242), (@cd_sm + 25), (@cd_u + 20), '확정', 'A', 1035.79, '2026-09-16 20:50:00'
       UNION ALL SELECT (@cd_p + 248), (@cd_a + 245), (@cd_sm + 25), (@cd_u + 16), '확정', 'A', 1066.84, '2026-09-16 20:59:00'
       UNION ALL SELECT (@cd_p + 249), (@cd_a + 244), (@cd_sm + 25), (@cd_u + 18), '확정', 'A', 1097.07, '2026-09-16 21:00:00'
       UNION ALL SELECT (@cd_p + 250), (@cd_a + 241), (@cd_sm + 25), (@cd_u + 15), '확정', 'A', 1032.62, '2026-09-16 21:52:00'
       UNION ALL SELECT (@cd_p + 251), (@cd_a + 259), (@cd_sm + 26), (@cd_u + 41), '확정', 'B', 980.90, '2026-09-15 23:09:00'
       UNION ALL SELECT (@cd_p + 252), (@cd_a + 254), (@cd_sm + 26), (@cd_u + 9), '확정', 'A', 1038.48, '2026-09-15 23:14:00'
       UNION ALL SELECT (@cd_p + 253), (@cd_a + 252), (@cd_sm + 26), (@cd_u + 11), '확정', 'A', 1024.95, '2026-09-15 23:14:00'
       UNION ALL SELECT (@cd_p + 254), (@cd_a + 256), (@cd_sm + 26), (@cd_u + 36), '확정', 'B', 980.90, '2026-09-15 23:42:00'
       UNION ALL SELECT (@cd_p + 255), (@cd_a + 255), (@cd_sm + 26), (@cd_u + 14), '확정', 'A', 1056.95, '2026-09-15 23:50:00'
       UNION ALL SELECT (@cd_p + 256), (@cd_a + 251), (@cd_sm + 26), (@cd_u + 8), '확정', 'A', 1038.48, '2026-09-15 23:55:00'
       UNION ALL SELECT (@cd_p + 257), (@cd_a + 253), (@cd_sm + 26), (@cd_u + 12), '확정', 'A', 1001.20, '2026-09-15 23:57:00'
       UNION ALL SELECT (@cd_p + 258), (@cd_a + 260), (@cd_sm + 26), (@cd_u + 37), '확정', 'B', 980.90, '2026-09-16 00:32:00'
       UNION ALL SELECT (@cd_p + 259), (@cd_a + 257), (@cd_sm + 26), (@cd_u + 39), '확정', 'B', 1000.00, '2026-09-16 00:51:00'
       UNION ALL SELECT (@cd_p + 260), (@cd_a + 258), (@cd_sm + 26), (@cd_u + 40), '확정', 'B', 1000.00, '2026-09-16 00:59:00'
       UNION ALL SELECT (@cd_p + 261), (@cd_a + 268), (@cd_sm + 27), (@cd_u + 2), '확정', 'B', 1179.67, '2026-09-19 21:53:00'
       UNION ALL SELECT (@cd_p + 262), (@cd_a + 266), (@cd_sm + 27), (@cd_u + 1), '확정', 'B', 1169.23, '2026-09-19 22:23:00'
       UNION ALL SELECT (@cd_p + 263), (@cd_a + 267), (@cd_sm + 27), (@cd_u + 3), '확정', 'B', 1163.19, '2026-09-19 22:23:00'
       UNION ALL SELECT (@cd_p + 264), (@cd_a + 270), (@cd_sm + 27), (@cd_u + 4), '확정', 'B', 1159.12, '2026-09-19 22:45:00'
       UNION ALL SELECT (@cd_p + 265), (@cd_a + 269), (@cd_sm + 27), (@cd_u + 6), '확정', 'B', 1154.06, '2026-09-19 22:55:00'
       UNION ALL SELECT (@cd_p + 266), (@cd_a + 262), (@cd_sm + 27), (@cd_u + 53), '확정', 'A', 996.10, '2026-09-20 21:08:00'
       UNION ALL SELECT (@cd_p + 267), (@cd_a + 264), (@cd_sm + 27), (@cd_u + 55), '확정', 'A', 996.80, '2026-09-20 21:44:00'
       UNION ALL SELECT (@cd_p + 268), (@cd_a + 265), (@cd_sm + 27), (@cd_u + 54), '확정', 'A', 932.26, '2026-09-20 22:20:00'
       UNION ALL SELECT (@cd_p + 269), (@cd_a + 263), (@cd_sm + 27), (@cd_u + 52), '확정', 'A', 996.80, '2026-09-20 22:38:00'
       UNION ALL SELECT (@cd_p + 270), (@cd_a + 261), (@cd_sm + 27), (@cd_u + 50), '확정', 'A', 957.30, '2026-09-20 22:59:00'
       UNION ALL SELECT (@cd_p + 271), (@cd_a + 277), (@cd_sm + 28), (@cd_u + 71), '확정', 'B', 987.95, '2026-09-22 08:46:00'
       UNION ALL SELECT (@cd_p + 272), (@cd_a + 276), (@cd_sm + 28), (@cd_u + 67), '확정', 'B', 968.70, '2026-09-22 09:33:00'
       UNION ALL SELECT (@cd_p + 273), (@cd_a + 279), (@cd_sm + 28), (@cd_u + 73), '확정', 'B', 987.95, '2026-09-22 09:50:00'
       UNION ALL SELECT (@cd_p + 274), (@cd_a + 278), (@cd_sm + 28), (@cd_u + 72), '확정', 'B', 1000.95, '2026-09-22 09:55:00'
       UNION ALL SELECT (@cd_p + 275), (@cd_a + 280), (@cd_sm + 28), (@cd_u + 69), '확정', 'B', 944.47, '2026-09-22 10:00:00'
       UNION ALL SELECT (@cd_p + 276), (@cd_a + 271), (@cd_sm + 28), (@cd_u + 8), '확정', 'A', 1066.50, '2026-09-23 03:05:00'
       UNION ALL SELECT (@cd_p + 277), (@cd_a + 273), (@cd_sm + 28), (@cd_u + 11), '확정', 'A', 1052.97, '2026-09-23 03:33:00'
       UNION ALL SELECT (@cd_p + 278), (@cd_a + 275), (@cd_sm + 28), (@cd_u + 12), '확정', 'A', 1029.22, '2026-09-23 03:42:00'
       UNION ALL SELECT (@cd_p + 279), (@cd_a + 272), (@cd_sm + 28), (@cd_u + 13), '확정', 'A', 1002.97, '2026-09-23 03:58:00'
       UNION ALL SELECT (@cd_p + 280), (@cd_a + 274), (@cd_sm + 28), (@cd_u + 10), '확정', 'A', 1002.97, '2026-09-23 04:53:00'
       UNION ALL SELECT (@cd_p + 281), (@cd_a + 282), (@cd_sm + 29), (@cd_u + 49), '확정', 'A', 1000.00, '2026-09-22 10:26:00'
       UNION ALL SELECT (@cd_p + 282), (@cd_a + 284), (@cd_sm + 29), (@cd_u + 46), '확정', 'A', 921.86, '2026-09-22 10:29:00'
       UNION ALL SELECT (@cd_p + 283), (@cd_a + 281), (@cd_sm + 29), (@cd_u + 43), '확정', 'A', 915.27, '2026-09-22 11:11:00'
       UNION ALL SELECT (@cd_p + 284), (@cd_a + 285), (@cd_sm + 29), (@cd_u + 47), '확정', 'A', 924.19, '2026-09-22 11:13:00'
       UNION ALL SELECT (@cd_p + 285), (@cd_a + 283), (@cd_sm + 29), (@cd_u + 48), '확정', 'A', 928.66, '2026-09-22 11:36:00'
       UNION ALL SELECT (@cd_p + 286), (@cd_a + 288), (@cd_sm + 29), (@cd_u + 63), '확정', 'B', 979.38, '2026-09-24 15:14:00'
       UNION ALL SELECT (@cd_p + 287), (@cd_a + 287), (@cd_sm + 29), (@cd_u + 59), '확정', 'B', 976.48, '2026-09-24 15:26:00'
       UNION ALL SELECT (@cd_p + 288), (@cd_a + 289), (@cd_sm + 29), (@cd_u + 64), '확정', 'B', 983.11, '2026-09-24 15:37:00'
       UNION ALL SELECT (@cd_p + 289), (@cd_a + 290), (@cd_sm + 29), (@cd_u + 58), '확정', 'B', 976.48, '2026-09-24 15:41:00'
       UNION ALL SELECT (@cd_p + 290), (@cd_a + 286), (@cd_sm + 29), (@cd_u + 57), '확정', 'B', 938.32, '2026-09-24 16:03:00'
       UNION ALL SELECT (@cd_p + 291), (@cd_a + 293), (@cd_sm + 30), (@cd_u + 3), '확정', 'A', 1171.25, '2026-09-24 05:31:00'
       UNION ALL SELECT (@cd_p + 292), (@cd_a + 294), (@cd_sm + 30), (@cd_u + 4), '확정', 'A', 1167.18, '2026-09-24 05:46:00'
       UNION ALL SELECT (@cd_p + 293), (@cd_a + 292), (@cd_sm + 30), (@cd_u + 5), '확정', 'A', 1164.43, '2026-09-24 06:01:00'
       UNION ALL SELECT (@cd_p + 294), (@cd_a + 295), (@cd_sm + 30), (@cd_u + 2), '확정', 'A', 1187.73, '2026-09-24 06:09:00'
       UNION ALL SELECT (@cd_p + 295), (@cd_a + 291), (@cd_sm + 30), (@cd_u + 1), '확정', 'A', 1177.29, '2026-09-24 06:48:00'
       UNION ALL SELECT (@cd_p + 296), (@cd_a + 296), (@cd_sm + 30), (@cd_u + 42), '확정', 'B', 980.90, '2026-09-24 16:15:00'
       UNION ALL SELECT (@cd_p + 297), (@cd_a + 300), (@cd_sm + 30), (@cd_u + 37), '확정', 'B', 952.88, '2026-09-24 16:43:00'
       UNION ALL SELECT (@cd_p + 298), (@cd_a + 298), (@cd_sm + 30), (@cd_u + 40), '확정', 'B', 971.98, '2026-09-24 16:52:00'
       UNION ALL SELECT (@cd_p + 299), (@cd_a + 299), (@cd_sm + 30), (@cd_u + 39), '확정', 'B', 971.98, '2026-09-24 17:26:00'
       UNION ALL SELECT (@cd_p + 300), (@cd_a + 297), (@cd_sm + 30), (@cd_u + 38), '확정', 'B', 980.90, '2026-09-24 17:50:00'
       UNION ALL SELECT (@cd_p + 301), (@cd_a + 304), (@cd_sm + 31), (@cd_u + 24), '확정', 'A', 951.48, '2026-09-28 15:23:00'
       UNION ALL SELECT (@cd_p + 302), (@cd_a + 305), (@cd_sm + 31), (@cd_u + 26), '확정', 'A', 1010.81, '2026-09-28 15:38:00'
       UNION ALL SELECT (@cd_p + 303), (@cd_a + 302), (@cd_sm + 31), (@cd_u + 28), '확정', 'A', 920.20, '2026-09-28 15:51:00'
       UNION ALL SELECT (@cd_p + 304), (@cd_a + 301), (@cd_sm + 31), (@cd_u + 22), '확정', 'A', 1027.01, '2026-09-28 16:10:00'
       UNION ALL SELECT (@cd_p + 305), (@cd_a + 303), (@cd_sm + 31), (@cd_u + 23), '확정', 'A', 966.09, '2026-09-28 16:18:00'
       UNION ALL SELECT (@cd_p + 306), (@cd_a + 308), (@cd_sm + 31), (@cd_u + 41), '확정', 'B', 952.88, '2026-09-29 11:12:00'
       UNION ALL SELECT (@cd_p + 307), (@cd_a + 309), (@cd_sm + 31), (@cd_u + 38), '확정', 'B', 965.65, '2026-09-29 11:18:00'
       UNION ALL SELECT (@cd_p + 308), (@cd_a + 307), (@cd_sm + 31), (@cd_u + 42), '확정', 'B', 965.65, '2026-09-29 11:36:00'
       UNION ALL SELECT (@cd_p + 309), (@cd_a + 310), (@cd_sm + 31), (@cd_u + 39), '확정', 'B', 956.73, '2026-09-29 12:17:00'
       UNION ALL SELECT (@cd_p + 310), (@cd_a + 306), (@cd_sm + 31), (@cd_u + 36), '확정', 'B', 952.88, '2026-09-29 12:43:00'
       UNION ALL SELECT (@cd_p + 311), (@cd_a + 313), (@cd_sm + 32), (@cd_u + 56), '확정', 'A', 1001.38, '2026-09-28 21:07:00'
       UNION ALL SELECT (@cd_p + 312), (@cd_a + 311), (@cd_sm + 32), (@cd_u + 55), '확정', 'A', 980.68, '2026-09-28 21:12:00'
       UNION ALL SELECT (@cd_p + 313), (@cd_a + 314), (@cd_sm + 32), (@cd_u + 51), '확정', 'A', 932.26, '2026-09-28 22:13:00'
       UNION ALL SELECT (@cd_p + 314), (@cd_a + 312), (@cd_sm + 32), (@cd_u + 53), '확정', 'A', 979.98, '2026-09-28 22:24:00'
       UNION ALL SELECT (@cd_p + 315), (@cd_a + 315), (@cd_sm + 32), (@cd_u + 52), '확정', 'A', 980.68, '2026-09-28 22:30:00'
       UNION ALL SELECT (@cd_p + 316), (@cd_a + 318), (@cd_sm + 32), (@cd_u + 6), '확정', 'B', 1162.12, '2026-09-29 16:04:00'
       UNION ALL SELECT (@cd_p + 317), (@cd_a + 320), (@cd_sm + 32), (@cd_u + 3), '확정', 'B', 1178.88, '2026-09-29 16:16:00'
       UNION ALL SELECT (@cd_p + 318), (@cd_a + 316), (@cd_sm + 32), (@cd_u + 1), '확정', 'B', 1184.92, '2026-09-29 17:17:00'
       UNION ALL SELECT (@cd_p + 319), (@cd_a + 319), (@cd_sm + 32), (@cd_u + 2), '확정', 'B', 1195.36, '2026-09-29 17:54:00'
       UNION ALL SELECT (@cd_p + 320), (@cd_a + 317), (@cd_sm + 32), (@cd_u + 5), '확정', 'B', 1172.06, '2026-09-29 17:57:00'
       UNION ALL SELECT (@cd_p + 321), (@cd_a + 321), (@cd_sm + 33), (@cd_u + 32), '확정', 'A', 986.24, '2026-09-30 02:04:00'
       UNION ALL SELECT (@cd_p + 322), (@cd_a + 324), (@cd_sm + 33), (@cd_u + 33), '확정', 'A', 985.52, '2026-09-30 03:13:00'
       UNION ALL SELECT (@cd_p + 323), (@cd_a + 323), (@cd_sm + 33), (@cd_u + 31), '확정', 'A', 971.02, '2026-09-30 03:15:00'
       UNION ALL SELECT (@cd_p + 324), (@cd_a + 325), (@cd_sm + 33), (@cd_u + 30), '확정', 'A', 919.94, '2026-09-30 03:38:00'
       UNION ALL SELECT (@cd_p + 325), (@cd_a + 322), (@cd_sm + 33), (@cd_u + 35), '확정', 'A', 1000.00, '2026-09-30 03:39:00'
       UNION ALL SELECT (@cd_p + 326), (@cd_a + 329), (@cd_sm + 33), (@cd_u + 71), '확정', 'B', 1024.79, '2026-09-30 04:38:00'
       UNION ALL SELECT (@cd_p + 327), (@cd_a + 326), (@cd_sm + 33), (@cd_u + 67), '확정', 'B', 987.12, '2026-09-30 04:50:00'
       UNION ALL SELECT (@cd_p + 328), (@cd_a + 330), (@cd_sm + 33), (@cd_u + 74), '확정', 'B', 980.75, '2026-09-30 05:02:00'
       UNION ALL SELECT (@cd_p + 329), (@cd_a + 327), (@cd_sm + 33), (@cd_u + 72), '확정', 'B', 1037.79, '2026-09-30 05:03:00'
       UNION ALL SELECT (@cd_p + 330), (@cd_a + 328), (@cd_sm + 33), (@cd_u + 69), '확정', 'B', 981.31, '2026-09-30 05:19:00'
       UNION ALL SELECT (@cd_p + 331), (@cd_a + 338), (@cd_sm + 34), (@cd_u + 65), '확정', 'B', 976.23, '2026-10-03 20:12:00'
       UNION ALL SELECT (@cd_p + 332), (@cd_a + 340), (@cd_sm + 34), (@cd_u + 60), '확정', 'B', 987.09, '2026-10-03 20:18:00'
       UNION ALL SELECT (@cd_p + 333), (@cd_a + 337), (@cd_sm + 34), (@cd_u + 61), '확정', 'B', 980.21, '2026-10-03 20:30:00'
       UNION ALL SELECT (@cd_p + 334), (@cd_a + 339), (@cd_sm + 34), (@cd_u + 62), '확정', 'B', 979.38, '2026-10-03 21:05:00'
       UNION ALL SELECT (@cd_p + 335), (@cd_a + 336), (@cd_sm + 34), (@cd_u + 57), '확정', 'B', 952.82, '2026-10-03 22:00:00'
       UNION ALL SELECT (@cd_p + 336), (@cd_a + 333), (@cd_sm + 34), (@cd_u + 5), '확정', 'A', 1179.62, '2026-10-04 11:23:00'
       UNION ALL SELECT (@cd_p + 337), (@cd_a + 331), (@cd_sm + 34), (@cd_u + 6), '확정', 'A', 1169.68, '2026-10-04 11:38:00'
       UNION ALL SELECT (@cd_p + 338), (@cd_a + 334), (@cd_sm + 34), (@cd_u + 2), '확정', 'A', 1202.92, '2026-10-04 11:43:00'
       UNION ALL SELECT (@cd_p + 339), (@cd_a + 335), (@cd_sm + 34), (@cd_u + 3), '확정', 'A', 1186.44, '2026-10-04 12:17:00'
       UNION ALL SELECT (@cd_p + 340), (@cd_a + 332), (@cd_sm + 34), (@cd_u + 4), '확정', 'A', 1174.81, '2026-10-04 12:17:00'
       UNION ALL SELECT (@cd_p + 341), (@cd_a + 341), (@cd_sm + 35), (@cd_u + 15), '확정', 'A', 990.09, '2026-10-06 15:23:00'
       UNION ALL SELECT (@cd_p + 342), (@cd_a + 342), (@cd_sm + 35), (@cd_u + 17), '확정', 'A', 992.51, '2026-10-06 15:51:00'
       UNION ALL SELECT (@cd_p + 343), (@cd_a + 344), (@cd_sm + 35), (@cd_u + 19), '확정', 'A', 1066.84, '2026-10-06 16:18:00'
       UNION ALL SELECT (@cd_p + 344), (@cd_a + 345), (@cd_sm + 35), (@cd_u + 20), '확정', 'A', 993.26, '2026-10-06 16:29:00'
       UNION ALL SELECT (@cd_p + 345), (@cd_a + 343), (@cd_sm + 35), (@cd_u + 18), '확정', 'A', 1054.54, '2026-10-06 16:42:00'
       UNION ALL SELECT (@cd_p + 346), (@cd_a + 349), (@cd_sm + 35), (@cd_u + 42), '확정', 'B', 935.15, '2026-10-07 18:03:00'
       UNION ALL SELECT (@cd_p + 347), (@cd_a + 347), (@cd_sm + 35), (@cd_u + 40), '확정', 'B', 956.73, '2026-10-07 18:09:00'
       UNION ALL SELECT (@cd_p + 348), (@cd_a + 350), (@cd_sm + 35), (@cd_u + 41), '확정', 'B', 922.38, '2026-10-07 18:34:00'
       UNION ALL SELECT (@cd_p + 349), (@cd_a + 348), (@cd_sm + 35), (@cd_u + 38), '확정', 'B', 935.15, '2026-10-07 18:36:00'
       UNION ALL SELECT (@cd_p + 350), (@cd_a + 346), (@cd_sm + 35), (@cd_u + 36), '확정', 'B', 922.38, '2026-10-07 18:48:00'
       UNION ALL SELECT (@cd_p + 351), (@cd_a + 351), (@cd_sm + 36), (@cd_u + 9), '확정', 'A', 1066.50, '2026-10-06 05:09:00'
       UNION ALL SELECT (@cd_p + 352), (@cd_a + 354), (@cd_sm + 36), (@cd_u + 14), '확정', 'A', 1084.97, '2026-10-06 05:35:00'
       UNION ALL SELECT (@cd_p + 353), (@cd_a + 352), (@cd_sm + 36), (@cd_u + 11), '확정', 'A', 1016.13, '2026-10-06 05:37:00'
       UNION ALL SELECT (@cd_p + 354), (@cd_a + 355), (@cd_sm + 36), (@cd_u + 13), '확정', 'A', 966.13, '2026-10-06 06:23:00'
       UNION ALL SELECT (@cd_p + 355), (@cd_a + 353), (@cd_sm + 36), (@cd_u + 10), '확정', 'A', 966.13, '2026-10-06 06:32:00'
       UNION ALL SELECT (@cd_p + 356), (@cd_a + 359), (@cd_sm + 36), (@cd_u + 6), '확정', 'B', 1145.12, '2026-10-09 04:09:00'
       UNION ALL SELECT (@cd_p + 357), (@cd_a + 360), (@cd_sm + 36), (@cd_u + 2), '확정', 'B', 1178.36, '2026-10-09 04:11:00'
       UNION ALL SELECT (@cd_p + 358), (@cd_a + 358), (@cd_sm + 36), (@cd_u + 5), '확정', 'B', 1155.06, '2026-10-09 04:26:00'
       UNION ALL SELECT (@cd_p + 359), (@cd_a + 356), (@cd_sm + 36), (@cd_u + 1), '확정', 'B', 1192.48, '2026-10-09 04:31:00'
       UNION ALL SELECT (@cd_p + 360), (@cd_a + 357), (@cd_sm + 36), (@cd_u + 4), '확정', 'B', 1150.25, '2026-10-09 05:28:00'
       UNION ALL SELECT (@cd_p + 361), (@cd_a + 362), (@cd_sm + 37), (@cd_u + 49), '확정', 'A', 971.01, '2026-10-07 08:11:00'
       UNION ALL SELECT (@cd_p + 362), (@cd_a + 365), (@cd_sm + 37), (@cd_u + 48), '확정', 'A', 899.67, '2026-10-07 08:39:00'
       UNION ALL SELECT (@cd_p + 363), (@cd_a + 361), (@cd_sm + 37), (@cd_u + 46), '확정', 'A', 892.87, '2026-10-07 08:44:00'
       UNION ALL SELECT (@cd_p + 364), (@cd_a + 364), (@cd_sm + 37), (@cd_u + 44), '확정', 'A', 896.66, '2026-10-07 09:25:00'
       UNION ALL SELECT (@cd_p + 365), (@cd_a + 363), (@cd_sm + 37), (@cd_u + 47), '확정', 'A', 895.20, '2026-10-07 09:47:00'
       UNION ALL SELECT (@cd_p + 366), (@cd_a + 370), (@cd_sm + 37), (@cd_u + 33), '확정', 'B', 956.26, '2026-10-07 13:08:00'
       UNION ALL SELECT (@cd_p + 367), (@cd_a + 368), (@cd_sm + 37), (@cd_u + 31), '확정', 'B', 941.76, '2026-10-07 13:52:00'
       UNION ALL SELECT (@cd_p + 368), (@cd_a + 366), (@cd_sm + 37), (@cd_u + 29), '확정', 'B', 954.24, '2026-10-07 13:59:00'
       UNION ALL SELECT (@cd_p + 369), (@cd_a + 369), (@cd_sm + 37), (@cd_u + 32), '확정', 'B', 956.98, '2026-10-07 14:46:00'
       UNION ALL SELECT (@cd_p + 370), (@cd_a + 367), (@cd_sm + 37), (@cd_u + 35), '확정', 'B', 970.74, '2026-10-07 14:58:00'
       UNION ALL SELECT (@cd_p + 371), (@cd_a + 375), (@cd_sm + 38), (@cd_u + 56), '확정', 'A', 986.25, '2026-10-09 18:17:00'
       UNION ALL SELECT (@cd_p + 372), (@cd_a + 373), (@cd_sm + 38), (@cd_u + 51), '확정', 'A', 917.13, '2026-10-09 18:23:00'
       UNION ALL SELECT (@cd_p + 373), (@cd_a + 371), (@cd_sm + 38), (@cd_u + 50), '확정', 'A', 941.18, '2026-10-09 18:42:00'
       UNION ALL SELECT (@cd_p + 374), (@cd_a + 374), (@cd_sm + 38), (@cd_u + 53), '확정', 'A', 964.85, '2026-10-09 19:36:00'
       UNION ALL SELECT (@cd_p + 375), (@cd_a + 372), (@cd_sm + 38), (@cd_u + 54), '확정', 'A', 916.14, '2026-10-09 19:38:00'
       UNION ALL SELECT (@cd_p + 376), (@cd_a + 378), (@cd_sm + 38), (@cd_u + 34), '확정', 'B', 954.24, '2026-10-11 22:09:00'
       UNION ALL SELECT (@cd_p + 377), (@cd_a + 380), (@cd_sm + 38), (@cd_u + 33), '확정', 'B', 920.14, '2026-10-11 22:47:00'
       UNION ALL SELECT (@cd_p + 378), (@cd_a + 379), (@cd_sm + 38), (@cd_u + 30), '확정', 'B', 890.68, '2026-10-11 22:54:00'
       UNION ALL SELECT (@cd_p + 379), (@cd_a + 376), (@cd_sm + 38), (@cd_u + 31), '확정', 'B', 905.64, '2026-10-11 23:19:00'
       UNION ALL SELECT (@cd_p + 380), (@cd_a + 377), (@cd_sm + 38), (@cd_u + 35), '확정', 'B', 934.62, '2026-10-11 23:41:00'
       UNION ALL SELECT (@cd_p + 381), (@cd_a + 384), (@cd_sm + 39), (@cd_u + 3), '확정', 'A', 1161.88, '2026-10-11 17:07:00'
       UNION ALL SELECT (@cd_p + 382), (@cd_a + 382), (@cd_sm + 39), (@cd_u + 6), '확정', 'A', 1154.83, '2026-10-11 17:20:00'
       UNION ALL SELECT (@cd_p + 383), (@cd_a + 383), (@cd_sm + 39), (@cd_u + 4), '확정', 'A', 1159.96, '2026-10-11 18:33:00'
       UNION ALL SELECT (@cd_p + 384), (@cd_a + 385), (@cd_sm + 39), (@cd_u + 5), '확정', 'A', 1164.77, '2026-10-11 18:57:00'
       UNION ALL SELECT (@cd_p + 385), (@cd_a + 381), (@cd_sm + 39), (@cd_u + 1), '확정', 'A', 1202.19, '2026-10-11 18:58:00'
       UNION ALL SELECT (@cd_p + 386), (@cd_a + 390), (@cd_sm + 39), (@cd_u + 18), '확정', 'B', 1078.85, '2026-10-12 20:04:00'
       UNION ALL SELECT (@cd_p + 387), (@cd_a + 386), (@cd_sm + 39), (@cd_u + 15), '확정', 'B', 1014.40, '2026-10-12 20:22:00'
       UNION ALL SELECT (@cd_p + 388), (@cd_a + 389), (@cd_sm + 39), (@cd_u + 17), '확정', 'B', 1016.82, '2026-10-12 20:34:00'
       UNION ALL SELECT (@cd_p + 389), (@cd_a + 387), (@cd_sm + 39), (@cd_u + 21), '확정', 'B', 1000.00, '2026-10-12 20:43:00'
       UNION ALL SELECT (@cd_p + 390), (@cd_a + 388), (@cd_sm + 39), (@cd_u + 20), '확정', 'B', 1017.57, '2026-10-12 20:47:00'
       UNION ALL SELECT (@cd_p + 391), (@cd_a + 395), (@cd_sm + 40), (@cd_u + 28), '확정', 'A', 950.70, '2026-10-11 20:33:00'
       UNION ALL SELECT (@cd_p + 392), (@cd_a + 392), (@cd_sm + 40), (@cd_u + 25), '확정', 'A', 1010.81, '2026-10-11 20:47:00'
       UNION ALL SELECT (@cd_p + 393), (@cd_a + 394), (@cd_sm + 40), (@cd_u + 23), '확정', 'A', 996.59, '2026-10-11 21:11:00'
       UNION ALL SELECT (@cd_p + 394), (@cd_a + 391), (@cd_sm + 40), (@cd_u + 22), '확정', 'A', 1057.51, '2026-10-11 21:22:00'
       UNION ALL SELECT (@cd_p + 395), (@cd_a + 393), (@cd_sm + 40), (@cd_u + 27), '확정', 'A', 1008.65, '2026-10-11 21:53:00'
       UNION ALL SELECT (@cd_p + 396), (@cd_a + 397), (@cd_sm + 40), (@cd_u + 64), '확정', 'B', 1012.10, '2026-10-13 18:04:00'
       UNION ALL SELECT (@cd_p + 397), (@cd_a + 398), (@cd_sm + 40), (@cd_u + 60), '확정', 'B', 1036.22, '2026-10-13 18:41:00'
       UNION ALL SELECT (@cd_p + 398), (@cd_a + 396), (@cd_sm + 40), (@cd_u + 57), '확정', 'B', 977.38, '2026-10-13 18:48:00'
       UNION ALL SELECT (@cd_p + 399), (@cd_a + 400), (@cd_sm + 40), (@cd_u + 61), '확정', 'B', 1029.34, '2026-10-13 19:08:00'
       UNION ALL SELECT (@cd_p + 400), (@cd_a + 399), (@cd_sm + 40), (@cd_u + 58), '확정', 'B', 1005.47, '2026-10-13 19:41:00'
       UNION ALL SELECT (@cd_p + 401), (@cd_a + 409), (@cd_sm + 41), (@cd_u + 73), '확정', 'B', 1024.79, '2026-10-14 06:08:00'
       UNION ALL SELECT (@cd_p + 402), (@cd_a + 406), (@cd_sm + 41), (@cd_u + 67), '확정', 'B', 1001.75, '2026-10-14 06:26:00'
       UNION ALL SELECT (@cd_p + 403), (@cd_a + 410), (@cd_sm + 41), (@cd_u + 70), '확정', 'B', 1024.23, '2026-10-14 06:54:00'
       UNION ALL SELECT (@cd_p + 404), (@cd_a + 407), (@cd_sm + 41), (@cd_u + 68), '확정', 'B', 967.75, '2026-10-14 06:55:00'
       UNION ALL SELECT (@cd_p + 405), (@cd_a + 408), (@cd_sm + 41), (@cd_u + 75), '확정', 'B', 980.75, '2026-10-14 06:59:00'
       UNION ALL SELECT (@cd_p + 406), (@cd_a + 404), (@cd_sm + 41), (@cd_u + 44), '확정', 'A', 932.78, '2026-10-14 07:04:00'
       UNION ALL SELECT (@cd_p + 407), (@cd_a + 402), (@cd_sm + 41), (@cd_u + 49), '확정', 'A', 1007.13, '2026-10-14 07:22:00'
       UNION ALL SELECT (@cd_p + 408), (@cd_a + 401), (@cd_sm + 41), (@cd_u + 43), '확정', 'A', 886.28, '2026-10-14 07:50:00'
       UNION ALL SELECT (@cd_p + 409), (@cd_a + 405), (@cd_sm + 41), (@cd_u + 47), '확정', 'A', 931.32, '2026-10-14 08:05:00'
       UNION ALL SELECT (@cd_p + 410), (@cd_a + 403), (@cd_sm + 41), (@cd_u + 45), '확정', 'A', 896.66, '2026-10-14 08:36:00'
       UNION ALL SELECT (@cd_p + 411), (@cd_a + 416), (@cd_sm + 42), (@cd_u + 4), '확정', 'B', 1169.71, '2026-10-18 11:30:00'
       UNION ALL SELECT (@cd_p + 412), (@cd_a + 419), (@cd_sm + 42), (@cd_u + 3), '확정', 'B', 1171.63, '2026-10-18 12:17:00'
       UNION ALL SELECT (@cd_p + 413), (@cd_a + 420), (@cd_sm + 42), (@cd_u + 5), '확정', 'B', 1174.52, '2026-10-18 12:21:00'
       UNION ALL SELECT (@cd_p + 414), (@cd_a + 418), (@cd_sm + 42), (@cd_u + 6), '확정', 'B', 1164.58, '2026-10-18 12:23:00'
       UNION ALL SELECT (@cd_p + 415), (@cd_a + 417), (@cd_sm + 42), (@cd_u + 7), '확정', 'B', 1000.00, '2026-10-18 12:55:00'
       UNION ALL SELECT (@cd_p + 416), (@cd_a + 415), (@cd_sm + 42), (@cd_u + 24), '확정', 'A', 981.98, '2026-10-19 13:30:00'
       UNION ALL SELECT (@cd_p + 417), (@cd_a + 412), (@cd_sm + 42), (@cd_u + 25), '확정', 'A', 979.48, '2026-10-19 14:00:00'
       UNION ALL SELECT (@cd_p + 418), (@cd_a + 414), (@cd_sm + 42), (@cd_u + 28), '확정', 'A', 919.37, '2026-10-19 14:29:00'
       UNION ALL SELECT (@cd_p + 419), (@cd_a + 411), (@cd_sm + 42), (@cd_u + 27), '확정', 'A', 977.32, '2026-10-19 14:58:00'
       UNION ALL SELECT (@cd_p + 420), (@cd_a + 413), (@cd_sm + 42), (@cd_u + 26), '확정', 'A', 1041.31, '2026-10-19 15:00:00'
       UNION ALL SELECT (@cd_p + 421), (@cd_a + 423), (@cd_sm + 43), (@cd_u + 19), '확정', 'A', 1091.15, '2026-10-19 18:02:00'
       UNION ALL SELECT (@cd_p + 422), (@cd_a + 425), (@cd_sm + 43), (@cd_u + 20), '확정', 'A', 1007.82, '2026-10-19 19:50:00'
       UNION ALL SELECT (@cd_p + 423), (@cd_a + 424), (@cd_sm + 43), (@cd_u + 16), '확정', 'A', 1024.31, '2026-10-19 19:52:00'
       UNION ALL SELECT (@cd_p + 424), (@cd_a + 422), (@cd_sm + 43), (@cd_u + 21), '확정', 'A', 980.49, '2026-10-19 19:53:00'
       UNION ALL SELECT (@cd_p + 425), (@cd_a + 421), (@cd_sm + 43), (@cd_u + 15), '확정', 'A', 1004.65, '2026-10-19 19:55:00'
       UNION ALL SELECT (@cd_p + 426), (@cd_a + 430), (@cd_sm + 43), (@cd_u + 42), '확정', 'B', 910.84, '2026-10-20 16:09:00'
       UNION ALL SELECT (@cd_p + 427), (@cd_a + 427), (@cd_sm + 43), (@cd_u + 37), '확정', 'B', 937.63, '2026-10-20 16:30:00'
       UNION ALL SELECT (@cd_p + 428), (@cd_a + 426), (@cd_sm + 43), (@cd_u + 41), '확정', 'B', 898.07, '2026-10-20 17:46:00'
       UNION ALL SELECT (@cd_p + 429), (@cd_a + 429), (@cd_sm + 43), (@cd_u + 40), '확정', 'B', 932.42, '2026-10-20 17:54:00'
       UNION ALL SELECT (@cd_p + 430), (@cd_a + 428), (@cd_sm + 43), (@cd_u + 39), '확정', 'B', 926.23, '2026-10-20 18:00:00'
       UNION ALL SELECT (@cd_p + 431), (@cd_a + 435), (@cd_sm + 44), (@cd_u + 45), '확정', 'A', 870.93, '2026-10-20 09:04:00'
       UNION ALL SELECT (@cd_p + 432), (@cd_a + 432), (@cd_sm + 44), (@cd_u + 49), '확정', 'A', 981.40, '2026-10-20 09:12:00'
       UNION ALL SELECT (@cd_p + 433), (@cd_a + 433), (@cd_sm + 44), (@cd_u + 48), '확정', 'A', 935.79, '2026-10-20 09:15:00'
       UNION ALL SELECT (@cd_p + 434), (@cd_a + 434), (@cd_sm + 44), (@cd_u + 46), '확정', 'A', 928.99, '2026-10-20 09:19:00'
       UNION ALL SELECT (@cd_p + 435), (@cd_a + 431), (@cd_sm + 44), (@cd_u + 43), '확정', 'A', 860.55, '2026-10-20 09:48:00'
       UNION ALL SELECT (@cd_p + 436), (@cd_a + 440), (@cd_sm + 44), (@cd_u + 59), '확정', 'B', 1005.47, '2026-10-22 11:04:00'
       UNION ALL SELECT (@cd_p + 437), (@cd_a + 439), (@cd_sm + 44), (@cd_u + 63), '확정', 'B', 1008.37, '2026-10-22 11:16:00'
       UNION ALL SELECT (@cd_p + 438), (@cd_a + 438), (@cd_sm + 44), (@cd_u + 62), '확정', 'B', 1028.51, '2026-10-22 11:29:00'
       UNION ALL SELECT (@cd_p + 439), (@cd_a + 436), (@cd_sm + 44), (@cd_u + 57), '확정', 'B', 993.05, '2026-10-22 11:38:00'
       UNION ALL SELECT (@cd_p + 440), (@cd_a + 437), (@cd_sm + 44), (@cd_u + 66), '확정', 'B', 1000.00, '2026-10-22 12:52:00'
       UNION ALL SELECT (@cd_p + 441), (@cd_a + 446), (@cd_sm + 45), (@cd_u + 67), '확정', 'B', 1014.61, '2026-10-21 05:05:00'
       UNION ALL SELECT (@cd_p + 442), (@cd_a + 450), (@cd_sm + 45), (@cd_u + 73), '확정', 'B', 1050.52, '2026-10-21 05:20:00'
       UNION ALL SELECT (@cd_p + 443), (@cd_a + 447), (@cd_sm + 45), (@cd_u + 69), '확정', 'B', 1010.57, '2026-10-21 05:55:00'
       UNION ALL SELECT (@cd_p + 444), (@cd_a + 448), (@cd_sm + 45), (@cd_u + 70), '확정', 'B', 1049.96, '2026-10-21 06:01:00'
       UNION ALL SELECT (@cd_p + 445), (@cd_a + 449), (@cd_sm + 45), (@cd_u + 75), '확정', 'B', 1006.48, '2026-10-21 06:58:00'
       UNION ALL SELECT (@cd_p + 446), (@cd_a + 443), (@cd_sm + 45), (@cd_u + 9), '확정', 'A', 1047.08, '2026-10-22 06:11:00'
       UNION ALL SELECT (@cd_p + 447), (@cd_a + 444), (@cd_sm + 45), (@cd_u + 13), '확정', 'A', 946.71, '2026-10-22 06:15:00'
       UNION ALL SELECT (@cd_p + 448), (@cd_a + 441), (@cd_sm + 45), (@cd_u + 8), '확정', 'A', 1029.66, '2026-10-22 06:35:00'
       UNION ALL SELECT (@cd_p + 449), (@cd_a + 445), (@cd_sm + 45), (@cd_u + 12), '확정', 'A', 992.38, '2026-10-22 06:36:00'
       UNION ALL SELECT (@cd_p + 450), (@cd_a + 442), (@cd_sm + 45), (@cd_u + 10), '확정', 'A', 946.71, '2026-10-22 07:01:00'
       UNION ALL SELECT (@cd_p + 451), (@cd_a + 455), (@cd_sm + 46), (@cd_u + 30), '확정', 'A', 924.89, '2026-10-23 04:01:00'
       UNION ALL SELECT (@cd_p + 452), (@cd_a + 453), (@cd_sm + 46), (@cd_u + 34), '확정', 'A', 988.45, '2026-10-23 04:54:00'
       UNION ALL SELECT (@cd_p + 453), (@cd_a + 451), (@cd_sm + 46), (@cd_u + 29), '확정', 'A', 918.12, '2026-10-23 05:01:00'
       UNION ALL SELECT (@cd_p + 454), (@cd_a + 452), (@cd_sm + 46), (@cd_u + 35), '확정', 'A', 968.83, '2026-10-23 05:27:00'
       UNION ALL SELECT (@cd_p + 455), (@cd_a + 454), (@cd_sm + 46), (@cd_u + 32), '확정', 'A', 920.86, '2026-10-23 05:35:00'
       UNION ALL SELECT (@cd_p + 456), (@cd_a + 458), (@cd_sm + 46), (@cd_u + 2), '확정', 'B', 1188.07, '2026-10-24 05:00:00'
       UNION ALL SELECT (@cd_p + 457), (@cd_a + 459), (@cd_sm + 46), (@cd_u + 6), '확정', 'B', 1173.83, '2026-10-24 05:38:00'
       UNION ALL SELECT (@cd_p + 458), (@cd_a + 460), (@cd_sm + 46), (@cd_u + 4), '확정', 'B', 1178.96, '2026-10-24 05:43:00'
       UNION ALL SELECT (@cd_p + 459), (@cd_a + 456), (@cd_sm + 46), (@cd_u + 1), '확정', 'B', 1211.94, '2026-10-24 06:09:00'
       UNION ALL SELECT (@cd_p + 460), (@cd_a + 457), (@cd_sm + 46), (@cd_u + 7), '확정', 'B', 1018.51, '2026-10-24 06:56:00'
       UNION ALL SELECT (@cd_p + 461), (@cd_a + 468), (@cd_sm + 47), (@cd_u + 72), '확정', 'B', 1067.05, '2026-10-25 14:00:00'
       UNION ALL SELECT (@cd_p + 462), (@cd_a + 466), (@cd_sm + 47), (@cd_u + 67), '확정', 'B', 997.05, '2026-10-25 14:30:00'
       UNION ALL SELECT (@cd_p + 463), (@cd_a + 467), (@cd_sm + 47), (@cd_u + 68), '확정', 'B', 993.48, '2026-10-25 14:36:00'
       UNION ALL SELECT (@cd_p + 464), (@cd_a + 470), (@cd_sm + 47), (@cd_u + 71), '확정', 'B', 1054.05, '2026-10-25 14:42:00'
       UNION ALL SELECT (@cd_p + 465), (@cd_a + 469), (@cd_sm + 47), (@cd_u + 74), '확정', 'B', 1010.01, '2026-10-25 15:40:00'
       UNION ALL SELECT (@cd_p + 466), (@cd_a + 465), (@cd_sm + 47), (@cd_u + 54), '확정', 'A', 881.93, '2026-10-25 20:22:00'
       UNION ALL SELECT (@cd_p + 467), (@cd_a + 461), (@cd_sm + 47), (@cd_u + 50), '확정', 'A', 906.97, '2026-10-25 20:46:00'
       UNION ALL SELECT (@cd_p + 468), (@cd_a + 462), (@cd_sm + 47), (@cd_u + 53), '확정', 'A', 930.64, '2026-10-25 21:29:00'
       UNION ALL SELECT (@cd_p + 469), (@cd_a + 464), (@cd_sm + 47), (@cd_u + 51), '확정', 'A', 882.92, '2026-10-25 21:36:00'
       UNION ALL SELECT (@cd_p + 470), (@cd_a + 463), (@cd_sm + 47), (@cd_u + 55), '확정', 'A', 965.55, '2026-10-25 21:45:00'
       UNION ALL SELECT (@cd_p + 471), (@cd_a + 473), (@cd_sm + 48), (@cd_u + 5), '확정', 'A', 1183.77, '2026-10-26 16:29:00'
       UNION ALL SELECT (@cd_p + 472), (@cd_a + 475), (@cd_sm + 48), (@cd_u + 2), '확정', 'A', 1195.43, '2026-10-26 16:33:00'
       UNION ALL SELECT (@cd_p + 473), (@cd_a + 474), (@cd_sm + 48), (@cd_u + 3), '확정', 'A', 1180.88, '2026-10-26 16:45:00'
       UNION ALL SELECT (@cd_p + 474), (@cd_a + 472), (@cd_sm + 48), (@cd_u + 7), '확정', 'A', 1033.22, '2026-10-26 17:32:00'
       UNION ALL SELECT (@cd_p + 475), (@cd_a + 471), (@cd_sm + 48), (@cd_u + 1), '확정', 'A', 1219.30, '2026-10-26 17:53:00'
       UNION ALL SELECT (@cd_p + 476), (@cd_a + 478), (@cd_sm + 48), (@cd_u + 40), '확정', 'B', 973.44, '2026-10-26 18:06:00'
       UNION ALL SELECT (@cd_p + 477), (@cd_a + 479), (@cd_sm + 48), (@cd_u + 37), '확정', 'B', 978.65, '2026-10-26 18:14:00'
       UNION ALL SELECT (@cd_p + 478), (@cd_a + 477), (@cd_sm + 48), (@cd_u + 38), '확정', 'B', 910.84, '2026-10-26 18:27:00'
       UNION ALL SELECT (@cd_p + 479), (@cd_a + 480), (@cd_sm + 48), (@cd_u + 39), '확정', 'B', 967.25, '2026-10-26 18:37:00'
       UNION ALL SELECT (@cd_p + 480), (@cd_a + 476), (@cd_sm + 48), (@cd_u + 36), '확정', 'B', 898.07, '2026-10-26 18:47:00'
       UNION ALL SELECT (@cd_p + 481), (@cd_a + 488), (@cd_sm + 49), (@cd_u + 65), '확정', 'B', 1025.36, '2026-10-31 16:00:00'
       UNION ALL SELECT (@cd_p + 482), (@cd_a + 490), (@cd_sm + 49), (@cd_u + 60), '확정', 'B', 1067.55, '2026-10-31 16:26:00'
       UNION ALL SELECT (@cd_p + 483), (@cd_a + 487), (@cd_sm + 49), (@cd_u + 66), '확정', 'B', 1023.76, '2026-10-31 16:53:00'
       UNION ALL SELECT (@cd_p + 484), (@cd_a + 486), (@cd_sm + 49), (@cd_u + 57), '확정', 'B', 1004.93, '2026-10-31 16:56:00'
       UNION ALL SELECT (@cd_p + 485), (@cd_a + 489), (@cd_sm + 49), (@cd_u + 59), '확정', 'B', 1029.23, '2026-10-31 17:23:00'
       UNION ALL SELECT (@cd_p + 486), (@cd_a + 485), (@cd_sm + 49), (@cd_u + 6), '확정', 'A', 1181.19, '2026-11-02 13:35:00'
       UNION ALL SELECT (@cd_p + 487), (@cd_a + 482), (@cd_sm + 49), (@cd_u + 7), '확정', 'A', 1047.49, '2026-11-02 13:40:00'
       UNION ALL SELECT (@cd_p + 488), (@cd_a + 481), (@cd_sm + 49), (@cd_u + 1), '확정', 'A', 1226.44, '2026-11-02 14:33:00'
       UNION ALL SELECT (@cd_p + 489), (@cd_a + 484), (@cd_sm + 49), (@cd_u + 3), '확정', 'A', 1188.02, '2026-11-02 14:37:00'
       UNION ALL SELECT (@cd_p + 490), (@cd_a + 483), (@cd_sm + 49), (@cd_u + 5), '확정', 'A', 1190.91, '2026-11-02 14:44:00'
       UNION ALL SELECT (@cd_p + 491), (@cd_a + 500), (@cd_sm + 50), (@cd_u + 68), '확정', 'B', 1015.61, '2026-11-04 15:08:00'
       UNION ALL SELECT (@cd_p + 492), (@cd_a + 496), (@cd_sm + 50), (@cd_u + 67), '확정', 'B', 1008.12, '2026-11-04 15:18:00'
       UNION ALL SELECT (@cd_p + 493), (@cd_a + 497), (@cd_sm + 50), (@cd_u + 76), '확정', 'B', 1000.00, '2026-11-04 15:22:00'
       UNION ALL SELECT (@cd_p + 494), (@cd_a + 491), (@cd_sm + 50), (@cd_u + 38), '확정', 'A', 896.57, '2026-11-04 15:26:00'
       UNION ALL SELECT (@cd_p + 495), (@cd_a + 498), (@cd_sm + 50), (@cd_u + 72), '확정', 'B', 1089.18, '2026-11-04 16:01:00'
       UNION ALL SELECT (@cd_p + 496), (@cd_a + 493), (@cd_sm + 50), (@cd_u + 42), '확정', 'A', 951.86, '2026-11-04 16:04:00'
       UNION ALL SELECT (@cd_p + 497), (@cd_a + 499), (@cd_sm + 50), (@cd_u + 70), '확정', 'B', 1014.84, '2026-11-04 16:19:00'
       UNION ALL SELECT (@cd_p + 498), (@cd_a + 495), (@cd_sm + 50), (@cd_u + 37), '확정', 'A', 964.38, '2026-11-04 16:22:00'
       UNION ALL SELECT (@cd_p + 499), (@cd_a + 494), (@cd_sm + 50), (@cd_u + 41), '확정', 'A', 939.09, '2026-11-04 16:27:00'
       UNION ALL SELECT (@cd_p + 500), (@cd_a + 492), (@cd_sm + 50), (@cd_u + 40), '확정', 'A', 959.17, '2026-11-04 16:45:00'
) v
 WHERE @cd_ready = 1;
-- 팀장: 팀에서 entry_mmr이 가장 높은 참가자(같으면 번호가 작은 쪽, TeamCaptainSelector)
UPDATE social_match
   SET captain_a_participant_id = CASE match_id
         WHEN (@cd_sm + 1) THEN (@cd_p + 1)
         WHEN (@cd_sm + 2) THEN (@cd_p + 16)
         WHEN (@cd_sm + 3) THEN (@cd_p + 25)
         WHEN (@cd_sm + 4) THEN (@cd_p + 36)
         WHEN (@cd_sm + 5) THEN (@cd_p + 44)
         WHEN (@cd_sm + 6) THEN (@cd_p + 57)
         WHEN (@cd_sm + 7) THEN (@cd_p + 63)
         WHEN (@cd_sm + 8) THEN (@cd_p + 76)
         WHEN (@cd_sm + 9) THEN (@cd_p + 87)
         WHEN (@cd_sm + 10) THEN (@cd_p + 91)
         WHEN (@cd_sm + 11) THEN (@cd_p + 107)
         WHEN (@cd_sm + 12) THEN (@cd_p + 119)
         WHEN (@cd_sm + 13) THEN (@cd_p + 125)
         WHEN (@cd_sm + 14) THEN (@cd_p + 136)
         WHEN (@cd_sm + 15) THEN (@cd_p + 148)
         WHEN (@cd_sm + 16) THEN (@cd_p + 156)
         WHEN (@cd_sm + 17) THEN (@cd_p + 161)
         WHEN (@cd_sm + 18) THEN (@cd_p + 174)
         WHEN (@cd_sm + 19) THEN (@cd_p + 182)
         WHEN (@cd_sm + 20) THEN (@cd_p + 192)
         WHEN (@cd_sm + 21) THEN (@cd_p + 201)
         WHEN (@cd_sm + 22) THEN (@cd_p + 212)
         WHEN (@cd_sm + 23) THEN (@cd_p + 221)
         WHEN (@cd_sm + 24) THEN (@cd_p + 236)
         WHEN (@cd_sm + 25) THEN (@cd_p + 249)
         WHEN (@cd_sm + 26) THEN (@cd_p + 255)
         WHEN (@cd_sm + 27) THEN (@cd_p + 267)
         WHEN (@cd_sm + 28) THEN (@cd_p + 276)
         WHEN (@cd_sm + 29) THEN (@cd_p + 281)
         WHEN (@cd_sm + 30) THEN (@cd_p + 294)
         WHEN (@cd_sm + 31) THEN (@cd_p + 304)
         WHEN (@cd_sm + 32) THEN (@cd_p + 311)
         WHEN (@cd_sm + 33) THEN (@cd_p + 325)
         WHEN (@cd_sm + 34) THEN (@cd_p + 338)
         WHEN (@cd_sm + 35) THEN (@cd_p + 343)
         WHEN (@cd_sm + 36) THEN (@cd_p + 352)
         WHEN (@cd_sm + 37) THEN (@cd_p + 361)
         WHEN (@cd_sm + 38) THEN (@cd_p + 371)
         WHEN (@cd_sm + 39) THEN (@cd_p + 385)
         WHEN (@cd_sm + 40) THEN (@cd_p + 394)
         WHEN (@cd_sm + 41) THEN (@cd_p + 407)
         WHEN (@cd_sm + 42) THEN (@cd_p + 420)
         WHEN (@cd_sm + 43) THEN (@cd_p + 421)
         WHEN (@cd_sm + 44) THEN (@cd_p + 432)
         WHEN (@cd_sm + 45) THEN (@cd_p + 446)
         WHEN (@cd_sm + 46) THEN (@cd_p + 452)
         WHEN (@cd_sm + 47) THEN (@cd_p + 470)
         WHEN (@cd_sm + 48) THEN (@cd_p + 475)
         WHEN (@cd_sm + 49) THEN (@cd_p + 488)
         WHEN (@cd_sm + 50) THEN (@cd_p + 498)
       END,
       captain_b_participant_id = CASE match_id
         WHEN (@cd_sm + 1) THEN (@cd_p + 6)
         WHEN (@cd_sm + 2) THEN (@cd_p + 11)
         WHEN (@cd_sm + 3) THEN (@cd_p + 21)
         WHEN (@cd_sm + 4) THEN (@cd_p + 31)
         WHEN (@cd_sm + 5) THEN (@cd_p + 46)
         WHEN (@cd_sm + 6) THEN (@cd_p + 51)
         WHEN (@cd_sm + 7) THEN (@cd_p + 66)
         WHEN (@cd_sm + 8) THEN (@cd_p + 71)
         WHEN (@cd_sm + 9) THEN (@cd_p + 85)
         WHEN (@cd_sm + 10) THEN (@cd_p + 100)
         WHEN (@cd_sm + 11) THEN (@cd_p + 101)
         WHEN (@cd_sm + 12) THEN (@cd_p + 113)
         WHEN (@cd_sm + 13) THEN (@cd_p + 123)
         WHEN (@cd_sm + 14) THEN (@cd_p + 132)
         WHEN (@cd_sm + 15) THEN (@cd_p + 145)
         WHEN (@cd_sm + 16) THEN (@cd_p + 153)
         WHEN (@cd_sm + 17) THEN (@cd_p + 165)
         WHEN (@cd_sm + 18) THEN (@cd_p + 177)
         WHEN (@cd_sm + 19) THEN (@cd_p + 189)
         WHEN (@cd_sm + 20) THEN (@cd_p + 196)
         WHEN (@cd_sm + 21) THEN (@cd_p + 210)
         WHEN (@cd_sm + 22) THEN (@cd_p + 217)
         WHEN (@cd_sm + 23) THEN (@cd_p + 230)
         WHEN (@cd_sm + 24) THEN (@cd_p + 231)
         WHEN (@cd_sm + 25) THEN (@cd_p + 241)
         WHEN (@cd_sm + 26) THEN (@cd_p + 259)
         WHEN (@cd_sm + 27) THEN (@cd_p + 261)
         WHEN (@cd_sm + 28) THEN (@cd_p + 274)
         WHEN (@cd_sm + 29) THEN (@cd_p + 288)
         WHEN (@cd_sm + 30) THEN (@cd_p + 296)
         WHEN (@cd_sm + 31) THEN (@cd_p + 307)
         WHEN (@cd_sm + 32) THEN (@cd_p + 319)
         WHEN (@cd_sm + 33) THEN (@cd_p + 329)
         WHEN (@cd_sm + 34) THEN (@cd_p + 332)
         WHEN (@cd_sm + 35) THEN (@cd_p + 347)
         WHEN (@cd_sm + 36) THEN (@cd_p + 359)
         WHEN (@cd_sm + 37) THEN (@cd_p + 370)
         WHEN (@cd_sm + 38) THEN (@cd_p + 376)
         WHEN (@cd_sm + 39) THEN (@cd_p + 386)
         WHEN (@cd_sm + 40) THEN (@cd_p + 397)
         WHEN (@cd_sm + 41) THEN (@cd_p + 401)
         WHEN (@cd_sm + 42) THEN (@cd_p + 413)
         WHEN (@cd_sm + 43) THEN (@cd_p + 427)
         WHEN (@cd_sm + 44) THEN (@cd_p + 438)
         WHEN (@cd_sm + 45) THEN (@cd_p + 442)
         WHEN (@cd_sm + 46) THEN (@cd_p + 459)
         WHEN (@cd_sm + 47) THEN (@cd_p + 461)
         WHEN (@cd_sm + 48) THEN (@cd_p + 477)
         WHEN (@cd_sm + 49) THEN (@cd_p + 482)
         WHEN (@cd_sm + 50) THEN (@cd_p + 495)
       END
 WHERE @cd_ready = 1 AND match_id BETWEEN (@cd_sm + 1) AND (@cd_sm + 50);

-- 6. 두 팀장의 결과 제출 (같은 결과라 확정)
INSERT INTO match_result_submission (match_id, captain_participant_id, reported_result, submission_status, submitted_at)
SELECT * FROM (
                 SELECT (@cd_sm + 1) AS match_id, (@cd_p + 1) AS captain_participant_id, 'B_WIN' AS reported_result, 'CONFIRMED' AS submission_status, '2026-08-01 17:15:00' AS submitted_at
       UNION ALL SELECT (@cd_sm + 1), (@cd_p + 6), 'B_WIN', 'CONFIRMED', '2026-08-01 18:01:00'
       UNION ALL SELECT (@cd_sm + 2), (@cd_p + 16), 'A_WIN', 'CONFIRMED', '2026-08-01 09:40:00'
       UNION ALL SELECT (@cd_sm + 2), (@cd_p + 11), 'A_WIN', 'CONFIRMED', '2026-08-01 11:05:00'
       UNION ALL SELECT (@cd_sm + 3), (@cd_p + 25), 'A_WIN', 'CONFIRMED', '2026-08-02 21:27:00'
       UNION ALL SELECT (@cd_sm + 3), (@cd_p + 21), 'A_WIN', 'CONFIRMED', '2026-08-02 21:08:00'
       UNION ALL SELECT (@cd_sm + 4), (@cd_p + 36), 'A_WIN', 'CONFIRMED', '2026-08-05 21:31:00'
       UNION ALL SELECT (@cd_sm + 4), (@cd_p + 31), 'A_WIN', 'CONFIRMED', '2026-08-05 22:40:00'
       UNION ALL SELECT (@cd_sm + 5), (@cd_p + 44), 'B_WIN', 'CONFIRMED', '2026-08-08 17:13:00'
       UNION ALL SELECT (@cd_sm + 5), (@cd_p + 46), 'B_WIN', 'CONFIRMED', '2026-08-08 18:01:00'
       UNION ALL SELECT (@cd_sm + 6), (@cd_p + 57), 'A_WIN', 'CONFIRMED', '2026-08-09 10:11:00'
       UNION ALL SELECT (@cd_sm + 6), (@cd_p + 51), 'A_WIN', 'CONFIRMED', '2026-08-09 10:35:00'
       UNION ALL SELECT (@cd_sm + 7), (@cd_p + 63), 'A_WIN', 'CONFIRMED', '2026-08-12 22:24:00'
       UNION ALL SELECT (@cd_sm + 7), (@cd_p + 66), 'A_WIN', 'CONFIRMED', '2026-08-12 21:30:00'
       UNION ALL SELECT (@cd_sm + 8), (@cd_p + 76), 'B_WIN', 'CONFIRMED', '2026-08-14 22:45:00'
       UNION ALL SELECT (@cd_sm + 8), (@cd_p + 71), 'B_WIN', 'CONFIRMED', '2026-08-14 22:39:00'
       UNION ALL SELECT (@cd_sm + 9), (@cd_p + 87), 'B_WIN', 'CONFIRMED', '2026-08-16 21:20:00'
       UNION ALL SELECT (@cd_sm + 9), (@cd_p + 85), 'B_WIN', 'CONFIRMED', '2026-08-16 20:35:00'
       UNION ALL SELECT (@cd_sm + 10), (@cd_p + 91), 'DRAW', 'CONFIRMED', '2026-08-21 23:27:00'
       UNION ALL SELECT (@cd_sm + 10), (@cd_p + 100), 'DRAW', 'CONFIRMED', '2026-08-22 00:51:00'
       UNION ALL SELECT (@cd_sm + 11), (@cd_p + 107), 'B_WIN', 'CONFIRMED', '2026-08-22 16:41:00'
       UNION ALL SELECT (@cd_sm + 11), (@cd_p + 101), 'B_WIN', 'CONFIRMED', '2026-08-22 16:54:00'
       UNION ALL SELECT (@cd_sm + 12), (@cd_p + 119), 'B_WIN', 'CONFIRMED', '2026-08-23 10:50:00'
       UNION ALL SELECT (@cd_sm + 12), (@cd_p + 113), 'B_WIN', 'CONFIRMED', '2026-08-23 11:52:00'
       UNION ALL SELECT (@cd_sm + 13), (@cd_p + 125), 'A_WIN', 'CONFIRMED', '2026-08-26 21:35:00'
       UNION ALL SELECT (@cd_sm + 13), (@cd_p + 123), 'A_WIN', 'CONFIRMED', '2026-08-26 22:18:00'
       UNION ALL SELECT (@cd_sm + 14), (@cd_p + 136), 'A_WIN', 'CONFIRMED', '2026-08-28 22:57:00'
       UNION ALL SELECT (@cd_sm + 14), (@cd_p + 132), 'A_WIN', 'CONFIRMED', '2026-08-28 23:10:00'
       UNION ALL SELECT (@cd_sm + 15), (@cd_p + 148), 'DRAW', 'CONFIRMED', '2026-08-30 21:09:00'
       UNION ALL SELECT (@cd_sm + 15), (@cd_p + 145), 'DRAW', 'CONFIRMED', '2026-08-30 20:34:00'
       UNION ALL SELECT (@cd_sm + 16), (@cd_p + 156), 'A_WIN', 'CONFIRMED', '2026-09-01 22:47:00'
       UNION ALL SELECT (@cd_sm + 16), (@cd_p + 153), 'A_WIN', 'CONFIRMED', '2026-09-02 00:42:00'
       UNION ALL SELECT (@cd_sm + 17), (@cd_p + 161), 'A_WIN', 'CONFIRMED', '2026-09-03 23:11:00'
       UNION ALL SELECT (@cd_sm + 17), (@cd_p + 165), 'A_WIN', 'CONFIRMED', '2026-09-04 00:01:00'
       UNION ALL SELECT (@cd_sm + 18), (@cd_p + 174), 'A_WIN', 'CONFIRMED', '2026-09-04 22:45:00'
       UNION ALL SELECT (@cd_sm + 18), (@cd_p + 177), 'A_WIN', 'CONFIRMED', '2026-09-04 22:41:00'
       UNION ALL SELECT (@cd_sm + 19), (@cd_p + 182), 'B_WIN', 'CONFIRMED', '2026-09-05 09:20:00'
       UNION ALL SELECT (@cd_sm + 19), (@cd_p + 189), 'B_WIN', 'CONFIRMED', '2026-09-05 10:48:00'
       UNION ALL SELECT (@cd_sm + 20), (@cd_p + 192), 'A_WIN', 'CONFIRMED', '2026-09-06 10:46:00'
       UNION ALL SELECT (@cd_sm + 20), (@cd_p + 196), 'A_WIN', 'CONFIRMED', '2026-09-06 11:29:00'
       UNION ALL SELECT (@cd_sm + 21), (@cd_p + 201), 'A_WIN', 'CONFIRMED', '2026-09-09 22:23:00'
       UNION ALL SELECT (@cd_sm + 21), (@cd_p + 210), 'A_WIN', 'CONFIRMED', '2026-09-09 21:51:00'
       UNION ALL SELECT (@cd_sm + 22), (@cd_p + 212), 'B_WIN', 'CONFIRMED', '2026-09-11 22:53:00'
       UNION ALL SELECT (@cd_sm + 22), (@cd_p + 217), 'B_WIN', 'CONFIRMED', '2026-09-11 23:08:00'
       UNION ALL SELECT (@cd_sm + 23), (@cd_p + 221), 'B_WIN', 'CONFIRMED', '2026-09-13 18:47:00'
       UNION ALL SELECT (@cd_sm + 23), (@cd_p + 230), 'B_WIN', 'CONFIRMED', '2026-09-13 18:33:00'
       UNION ALL SELECT (@cd_sm + 24), (@cd_p + 236), 'B_WIN', 'CONFIRMED', '2026-09-17 23:23:00'
       UNION ALL SELECT (@cd_sm + 24), (@cd_p + 231), 'B_WIN', 'CONFIRMED', '2026-09-18 00:29:00'
       UNION ALL SELECT (@cd_sm + 25), (@cd_p + 249), 'B_WIN', 'CONFIRMED', '2026-09-18 22:23:00'
       UNION ALL SELECT (@cd_sm + 25), (@cd_p + 241), 'B_WIN', 'CONFIRMED', '2026-09-19 00:22:00'
       UNION ALL SELECT (@cd_sm + 26), (@cd_p + 255), 'A_WIN', 'CONFIRMED', '2026-09-20 10:10:00'
       UNION ALL SELECT (@cd_sm + 26), (@cd_p + 259), 'A_WIN', 'CONFIRMED', '2026-09-20 09:45:00'
       UNION ALL SELECT (@cd_sm + 27), (@cd_p + 267), 'B_WIN', 'CONFIRMED', '2026-09-22 23:12:00'
       UNION ALL SELECT (@cd_sm + 27), (@cd_p + 261), 'B_WIN', 'CONFIRMED', '2026-09-23 00:51:00'
       UNION ALL SELECT (@cd_sm + 28), (@cd_p + 276), 'B_WIN', 'CONFIRMED', '2026-09-26 10:21:00'
       UNION ALL SELECT (@cd_sm + 28), (@cd_p + 274), 'B_WIN', 'CONFIRMED', '2026-09-26 11:57:00'
       UNION ALL SELECT (@cd_sm + 29), (@cd_p + 281), 'B_WIN', 'CONFIRMED', '2026-09-26 16:35:00'
       UNION ALL SELECT (@cd_sm + 29), (@cd_p + 288), 'B_WIN', 'CONFIRMED', '2026-09-26 17:59:00'
       UNION ALL SELECT (@cd_sm + 30), (@cd_p + 294), 'A_WIN', 'CONFIRMED', '2026-09-26 21:57:00'
       UNION ALL SELECT (@cd_sm + 30), (@cd_p + 296), 'A_WIN', 'CONFIRMED', '2026-09-26 22:00:00'
       UNION ALL SELECT (@cd_sm + 31), (@cd_p + 304), 'A_WIN', 'CONFIRMED', '2026-10-01 23:18:00'
       UNION ALL SELECT (@cd_sm + 31), (@cd_p + 307), 'A_WIN', 'CONFIRMED', '2026-10-01 23:17:00'
       UNION ALL SELECT (@cd_sm + 32), (@cd_p + 311), 'B_WIN', 'CONFIRMED', '2026-10-02 23:07:00'
       UNION ALL SELECT (@cd_sm + 32), (@cd_p + 319), 'B_WIN', 'CONFIRMED', '2026-10-02 22:56:00'
       UNION ALL SELECT (@cd_sm + 33), (@cd_p + 325), 'B_WIN', 'CONFIRMED', '2026-10-04 11:13:00'
       UNION ALL SELECT (@cd_sm + 33), (@cd_p + 329), 'B_WIN', 'CONFIRMED', '2026-10-04 11:53:00'
       UNION ALL SELECT (@cd_sm + 34), (@cd_p + 338), 'B_WIN', 'CONFIRMED', '2026-10-07 21:44:00'
       UNION ALL SELECT (@cd_sm + 34), (@cd_p + 332), 'B_WIN', 'CONFIRMED', '2026-10-07 23:58:00'
       UNION ALL SELECT (@cd_sm + 35), (@cd_p + 343), 'A_WIN', 'CONFIRMED', '2026-10-09 23:04:00'
       UNION ALL SELECT (@cd_sm + 35), (@cd_p + 347), 'A_WIN', 'CONFIRMED', '2026-10-09 23:42:00'
       UNION ALL SELECT (@cd_sm + 36), (@cd_p + 352), 'B_WIN', 'CONFIRMED', '2026-10-10 10:25:00'
       UNION ALL SELECT (@cd_sm + 36), (@cd_p + 359), 'B_WIN', 'CONFIRMED', '2026-10-10 10:06:00'
       UNION ALL SELECT (@cd_sm + 37), (@cd_p + 361), 'A_WIN', 'CONFIRMED', '2026-10-10 16:22:00'
       UNION ALL SELECT (@cd_sm + 37), (@cd_p + 370), 'A_WIN', 'CONFIRMED', '2026-10-10 17:47:00'
       UNION ALL SELECT (@cd_sm + 38), (@cd_p + 371), 'B_WIN', 'CONFIRMED', '2026-10-13 22:31:00'
       UNION ALL SELECT (@cd_sm + 38), (@cd_p + 376), 'B_WIN', 'CONFIRMED', '2026-10-14 00:58:00'
       UNION ALL SELECT (@cd_sm + 39), (@cd_p + 385), 'A_WIN', 'CONFIRMED', '2026-10-14 22:23:00'
       UNION ALL SELECT (@cd_sm + 39), (@cd_p + 386), 'A_WIN', 'CONFIRMED', '2026-10-14 23:59:00'
       UNION ALL SELECT (@cd_sm + 40), (@cd_p + 394), 'B_WIN', 'CONFIRMED', '2026-10-15 22:48:00'
       UNION ALL SELECT (@cd_sm + 40), (@cd_p + 397), 'B_WIN', 'CONFIRMED', '2026-10-16 00:01:00'
       UNION ALL SELECT (@cd_sm + 41), (@cd_p + 407), 'B_WIN', 'CONFIRMED', '2026-10-17 16:30:00'
       UNION ALL SELECT (@cd_sm + 41), (@cd_p + 401), 'B_WIN', 'CONFIRMED', '2026-10-17 16:46:00'
       UNION ALL SELECT (@cd_sm + 42), (@cd_p + 420), 'B_WIN', 'CONFIRMED', '2026-10-22 23:13:00'
       UNION ALL SELECT (@cd_sm + 42), (@cd_p + 413), 'B_WIN', 'CONFIRMED', '2026-10-22 23:54:00'
       UNION ALL SELECT (@cd_sm + 43), (@cd_p + 421), 'B_WIN', 'CONFIRMED', '2026-10-23 22:28:00'
       UNION ALL SELECT (@cd_sm + 43), (@cd_p + 427), 'B_WIN', 'CONFIRMED', '2026-10-23 23:03:00'
       UNION ALL SELECT (@cd_sm + 44), (@cd_p + 432), 'B_WIN', 'CONFIRMED', '2026-10-24 16:38:00'
       UNION ALL SELECT (@cd_sm + 44), (@cd_p + 438), 'B_WIN', 'CONFIRMED', '2026-10-24 16:45:00'
       UNION ALL SELECT (@cd_sm + 45), (@cd_p + 446), 'A_WIN', 'CONFIRMED', '2026-10-24 10:03:00'
       UNION ALL SELECT (@cd_sm + 45), (@cd_p + 442), 'A_WIN', 'CONFIRMED', '2026-10-24 11:09:00'
       UNION ALL SELECT (@cd_sm + 46), (@cd_p + 452), 'B_WIN', 'CONFIRMED', '2026-10-25 10:56:00'
       UNION ALL SELECT (@cd_sm + 46), (@cd_p + 459), 'B_WIN', 'CONFIRMED', '2026-10-25 11:47:00'
       UNION ALL SELECT (@cd_sm + 47), (@cd_p + 470), 'B_WIN', 'CONFIRMED', '2026-10-27 22:50:00'
       UNION ALL SELECT (@cd_sm + 47), (@cd_p + 461), 'B_WIN', 'CONFIRMED', '2026-10-28 00:51:00'
       UNION ALL SELECT (@cd_sm + 48), (@cd_p + 475), 'A_WIN', 'CONFIRMED', '2026-10-28 22:09:00'
       UNION ALL SELECT (@cd_sm + 48), (@cd_p + 477), 'A_WIN', 'CONFIRMED', '2026-10-28 23:24:00'
       UNION ALL SELECT (@cd_sm + 49), (@cd_p + 488), 'A_WIN', 'CONFIRMED', '2026-11-04 21:50:00'
       UNION ALL SELECT (@cd_sm + 49), (@cd_p + 482), 'A_WIN', 'CONFIRMED', '2026-11-04 21:39:00'
       UNION ALL SELECT (@cd_sm + 50), (@cd_p + 498), 'B_WIN', 'CONFIRMED', '2026-11-08 18:44:00'
       UNION ALL SELECT (@cd_sm + 50), (@cd_p + 495), 'B_WIN', 'CONFIRMED', '2026-11-08 20:57:00'
) v
 WHERE @cd_ready = 1;

-- 7. 레이팅 이력 (평가 없이 반영: evaluation_delta 0, evaluation_score NULL)
INSERT INTO sport_rating_history (match_id, participant_id, user_id, sport_id, mmr_before, mmr_after, elo_delta, evaluation_delta, final_delta, evaluation_score, actual_score, expected_score, team_avg_mmr_a, team_avg_mmr_b, algorithm_version, calculated_at)
SELECT * FROM (
                 SELECT (@cd_sm + 1) AS match_id, (@cd_p + 10) AS participant_id, (@cd_u + 1) AS user_id, @cd_s AS sport_id, 1000.00 AS mmr_before, 1032.00 AS mmr_after, 32.00 AS elo_delta, 0.00 AS evaluation_delta, 32.00 AS final_delta, NULL AS evaluation_score, 1.0 AS actual_score, 0.5000 AS expected_score, 1000.00 AS team_avg_mmr_a, 1000.00 AS team_avg_mmr_b, 'ELO-K32-V2' AS algorithm_version, '2026-08-02 16:00:00' AS calculated_at
       UNION ALL SELECT (@cd_sm + 1), (@cd_p + 6), (@cd_u + 2), @cd_s, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 16:00:00'
       UNION ALL SELECT (@cd_sm + 1), (@cd_p + 8), (@cd_u + 3), @cd_s, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 16:00:00'
       UNION ALL SELECT (@cd_sm + 1), (@cd_p + 7), (@cd_u + 4), @cd_s, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 16:00:00'
       UNION ALL SELECT (@cd_sm + 1), (@cd_p + 9), (@cd_u + 5), @cd_s, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 16:00:00'
       UNION ALL SELECT (@cd_sm + 1), (@cd_p + 1), (@cd_u + 43), @cd_s, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 16:00:00'
       UNION ALL SELECT (@cd_sm + 1), (@cd_p + 2), (@cd_u + 44), @cd_s, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 16:00:00'
       UNION ALL SELECT (@cd_sm + 1), (@cd_p + 5), (@cd_u + 45), @cd_s, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 16:00:00'
       UNION ALL SELECT (@cd_sm + 1), (@cd_p + 3), (@cd_u + 46), @cd_s, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 16:00:00'
       UNION ALL SELECT (@cd_sm + 1), (@cd_p + 4), (@cd_u + 47), @cd_s, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 16:00:00'
       UNION ALL SELECT (@cd_sm + 2), (@cd_p + 20), (@cd_u + 8), @cd_s, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 09:00:00'
       UNION ALL SELECT (@cd_sm + 2), (@cd_p + 19), (@cd_u + 9), @cd_s, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 09:00:00'
       UNION ALL SELECT (@cd_sm + 2), (@cd_p + 18), (@cd_u + 10), @cd_s, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 09:00:00'
       UNION ALL SELECT (@cd_sm + 2), (@cd_p + 17), (@cd_u + 13), @cd_s, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 09:00:00'
       UNION ALL SELECT (@cd_sm + 2), (@cd_p + 16), (@cd_u + 14), @cd_s, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 09:00:00'
       UNION ALL SELECT (@cd_sm + 2), (@cd_p + 12), (@cd_u + 57), @cd_s, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 09:00:00'
       UNION ALL SELECT (@cd_sm + 2), (@cd_p + 11), (@cd_u + 58), @cd_s, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 09:00:00'
       UNION ALL SELECT (@cd_sm + 2), (@cd_p + 13), (@cd_u + 59), @cd_s, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 09:00:00'
       UNION ALL SELECT (@cd_sm + 2), (@cd_p + 14), (@cd_u + 61), @cd_s, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 09:00:00'
       UNION ALL SELECT (@cd_sm + 2), (@cd_p + 15), (@cd_u + 65), @cd_s, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 09:00:00'
       UNION ALL SELECT (@cd_sm + 3), (@cd_p + 27), (@cd_u + 29), @cd_s, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-03 20:00:00'
       UNION ALL SELECT (@cd_sm + 3), (@cd_p + 24), (@cd_u + 30), @cd_s, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-03 20:00:00'
       UNION ALL SELECT (@cd_sm + 3), (@cd_p + 21), (@cd_u + 31), @cd_s, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-03 20:00:00'
       UNION ALL SELECT (@cd_sm + 3), (@cd_p + 22), (@cd_u + 33), @cd_s, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-03 20:00:00'
       UNION ALL SELECT (@cd_sm + 3), (@cd_p + 23), (@cd_u + 34), @cd_s, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-03 20:00:00'
       UNION ALL SELECT (@cd_sm + 3), (@cd_p + 26), (@cd_u + 67), @cd_s, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-03 20:00:00'
       UNION ALL SELECT (@cd_sm + 3), (@cd_p + 28), (@cd_u + 70), @cd_s, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-03 20:00:00'
       UNION ALL SELECT (@cd_sm + 3), (@cd_p + 30), (@cd_u + 72), @cd_s, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-03 20:00:00'
       UNION ALL SELECT (@cd_sm + 3), (@cd_p + 29), (@cd_u + 74), @cd_s, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-03 20:00:00'
       UNION ALL SELECT (@cd_sm + 3), (@cd_p + 25), (@cd_u + 75), @cd_s, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-03 20:00:00'
       UNION ALL SELECT (@cd_sm + 4), (@cd_p + 40), (@cd_u + 1), @cd_s, 1032.00, 1061.64, 29.64, 0.00, 29.64, NULL, 1.0, 0.5368, 1025.60, 1000.00, 'ELO-K32-V2', '2026-08-06 21:00:00'
       UNION ALL SELECT (@cd_sm + 4), (@cd_p + 38), (@cd_u + 2), @cd_s, 1032.00, 1061.64, 29.64, 0.00, 29.64, NULL, 1.0, 0.5368, 1025.60, 1000.00, 'ELO-K32-V2', '2026-08-06 21:00:00'
       UNION ALL SELECT (@cd_sm + 4), (@cd_p + 36), (@cd_u + 3), @cd_s, 1032.00, 1061.64, 29.64, 0.00, 29.64, NULL, 1.0, 0.5368, 1025.60, 1000.00, 'ELO-K32-V2', '2026-08-06 21:00:00'
       UNION ALL SELECT (@cd_sm + 4), (@cd_p + 37), (@cd_u + 5), @cd_s, 1032.00, 1061.64, 29.64, 0.00, 29.64, NULL, 1.0, 0.5368, 1025.60, 1000.00, 'ELO-K32-V2', '2026-08-06 21:00:00'
       UNION ALL SELECT (@cd_sm + 4), (@cd_p + 39), (@cd_u + 6), @cd_s, 1000.00, 1029.64, 29.64, 0.00, 29.64, NULL, 1.0, 0.5368, 1025.60, 1000.00, 'ELO-K32-V2', '2026-08-06 21:00:00'
       UNION ALL SELECT (@cd_sm + 4), (@cd_p + 33), (@cd_u + 23), @cd_s, 1000.00, 970.36, -29.64, 0.00, -29.64, NULL, 0.0, 0.4632, 1025.60, 1000.00, 'ELO-K32-V2', '2026-08-06 21:00:00'
       UNION ALL SELECT (@cd_sm + 4), (@cd_p + 34), (@cd_u + 24), @cd_s, 1000.00, 970.36, -29.64, 0.00, -29.64, NULL, 0.0, 0.4632, 1025.60, 1000.00, 'ELO-K32-V2', '2026-08-06 21:00:00'
       UNION ALL SELECT (@cd_sm + 4), (@cd_p + 32), (@cd_u + 25), @cd_s, 1000.00, 970.36, -29.64, 0.00, -29.64, NULL, 0.0, 0.4632, 1025.60, 1000.00, 'ELO-K32-V2', '2026-08-06 21:00:00'
       UNION ALL SELECT (@cd_sm + 4), (@cd_p + 35), (@cd_u + 26), @cd_s, 1000.00, 970.36, -29.64, 0.00, -29.64, NULL, 0.0, 0.4632, 1025.60, 1000.00, 'ELO-K32-V2', '2026-08-06 21:00:00'
       UNION ALL SELECT (@cd_sm + 4), (@cd_p + 31), (@cd_u + 28), @cd_s, 1000.00, 970.36, -29.64, 0.00, -29.64, NULL, 0.0, 0.4632, 1025.60, 1000.00, 'ELO-K32-V2', '2026-08-06 21:00:00'
       UNION ALL SELECT (@cd_sm + 5), (@cd_p + 46), (@cd_u + 1), @cd_s, 1061.64, 1086.84, 25.20, 0.00, 25.20, NULL, 1.0, 0.6062, 974.40, 1049.31, 'ELO-K32-V2', '2026-08-09 16:00:00'
       UNION ALL SELECT (@cd_sm + 5), (@cd_p + 49), (@cd_u + 2), @cd_s, 1061.64, 1086.84, 25.20, 0.00, 25.20, NULL, 1.0, 0.6062, 974.40, 1049.31, 'ELO-K32-V2', '2026-08-09 16:00:00'
       UNION ALL SELECT (@cd_sm + 5), (@cd_p + 47), (@cd_u + 4), @cd_s, 1032.00, 1057.20, 25.20, 0.00, 25.20, NULL, 1.0, 0.6062, 974.40, 1049.31, 'ELO-K32-V2', '2026-08-09 16:00:00'
       UNION ALL SELECT (@cd_sm + 5), (@cd_p + 50), (@cd_u + 5), @cd_s, 1061.64, 1086.84, 25.20, 0.00, 25.20, NULL, 1.0, 0.6062, 974.40, 1049.31, 'ELO-K32-V2', '2026-08-09 16:00:00'
       UNION ALL SELECT (@cd_sm + 5), (@cd_p + 48), (@cd_u + 6), @cd_s, 1029.64, 1054.84, 25.20, 0.00, 25.20, NULL, 1.0, 0.6062, 974.40, 1049.31, 'ELO-K32-V2', '2026-08-09 16:00:00'
       UNION ALL SELECT (@cd_sm + 5), (@cd_p + 45), (@cd_u + 43), @cd_s, 968.00, 942.80, -25.20, 0.00, -25.20, NULL, 0.0, 0.3938, 974.40, 1049.31, 'ELO-K32-V2', '2026-08-09 16:00:00'
       UNION ALL SELECT (@cd_sm + 5), (@cd_p + 42), (@cd_u + 44), @cd_s, 968.00, 942.80, -25.20, 0.00, -25.20, NULL, 0.0, 0.3938, 974.40, 1049.31, 'ELO-K32-V2', '2026-08-09 16:00:00'
       UNION ALL SELECT (@cd_sm + 5), (@cd_p + 41), (@cd_u + 45), @cd_s, 968.00, 942.80, -25.20, 0.00, -25.20, NULL, 0.0, 0.3938, 974.40, 1049.31, 'ELO-K32-V2', '2026-08-09 16:00:00'
       UNION ALL SELECT (@cd_sm + 5), (@cd_p + 43), (@cd_u + 47), @cd_s, 968.00, 942.80, -25.20, 0.00, -25.20, NULL, 0.0, 0.3938, 974.40, 1049.31, 'ELO-K32-V2', '2026-08-09 16:00:00'
       UNION ALL SELECT (@cd_sm + 5), (@cd_p + 44), (@cd_u + 48), @cd_s, 1000.00, 974.80, -25.20, 0.00, -25.20, NULL, 0.0, 0.3938, 974.40, 1049.31, 'ELO-K32-V2', '2026-08-09 16:00:00'
       UNION ALL SELECT (@cd_sm + 6), (@cd_p + 60), (@cd_u + 8), @cd_s, 1032.00, 1062.23, 30.23, 0.00, 30.23, NULL, 1.0, 0.5276, 1019.20, 1000.00, 'ELO-K32-V2', '2026-08-10 09:00:00'
       UNION ALL SELECT (@cd_sm + 6), (@cd_p + 59), (@cd_u + 9), @cd_s, 1032.00, 1062.23, 30.23, 0.00, 30.23, NULL, 1.0, 0.5276, 1019.20, 1000.00, 'ELO-K32-V2', '2026-08-10 09:00:00'
       UNION ALL SELECT (@cd_sm + 6), (@cd_p + 56), (@cd_u + 11), @cd_s, 1000.00, 1030.23, 30.23, 0.00, 30.23, NULL, 1.0, 0.5276, 1019.20, 1000.00, 'ELO-K32-V2', '2026-08-10 09:00:00'
       UNION ALL SELECT (@cd_sm + 6), (@cd_p + 58), (@cd_u + 12), @cd_s, 1000.00, 1030.23, 30.23, 0.00, 30.23, NULL, 1.0, 0.5276, 1019.20, 1000.00, 'ELO-K32-V2', '2026-08-10 09:00:00'
       UNION ALL SELECT (@cd_sm + 6), (@cd_p + 57), (@cd_u + 14), @cd_s, 1032.00, 1062.23, 30.23, 0.00, 30.23, NULL, 1.0, 0.5276, 1019.20, 1000.00, 'ELO-K32-V2', '2026-08-10 09:00:00'
       UNION ALL SELECT (@cd_sm + 6), (@cd_p + 51), (@cd_u + 15), @cd_s, 1000.00, 969.77, -30.23, 0.00, -30.23, NULL, 0.0, 0.4724, 1019.20, 1000.00, 'ELO-K32-V2', '2026-08-10 09:00:00'
       UNION ALL SELECT (@cd_sm + 6), (@cd_p + 52), (@cd_u + 16), @cd_s, 1000.00, 969.77, -30.23, 0.00, -30.23, NULL, 0.0, 0.4724, 1019.20, 1000.00, 'ELO-K32-V2', '2026-08-10 09:00:00'
       UNION ALL SELECT (@cd_sm + 6), (@cd_p + 54), (@cd_u + 17), @cd_s, 1000.00, 969.77, -30.23, 0.00, -30.23, NULL, 0.0, 0.4724, 1019.20, 1000.00, 'ELO-K32-V2', '2026-08-10 09:00:00'
       UNION ALL SELECT (@cd_sm + 6), (@cd_p + 53), (@cd_u + 19), @cd_s, 1000.00, 969.77, -30.23, 0.00, -30.23, NULL, 0.0, 0.4724, 1019.20, 1000.00, 'ELO-K32-V2', '2026-08-10 09:00:00'
       UNION ALL SELECT (@cd_sm + 6), (@cd_p + 55), (@cd_u + 20), @cd_s, 1000.00, 969.77, -30.23, 0.00, -30.23, NULL, 0.0, 0.4724, 1019.20, 1000.00, 'ELO-K32-V2', '2026-08-10 09:00:00'
       UNION ALL SELECT (@cd_sm + 7), (@cd_p + 64), (@cd_u + 1), @cd_s, 1086.84, 1111.96, 25.12, 0.00, 25.12, NULL, 1.0, 0.6075, 1069.47, 993.60, 'ELO-K32-V2', '2026-08-13 21:00:00'
       UNION ALL SELECT (@cd_sm + 7), (@cd_p + 63), (@cd_u + 2), @cd_s, 1086.84, 1111.96, 25.12, 0.00, 25.12, NULL, 1.0, 0.6075, 1069.47, 993.60, 'ELO-K32-V2', '2026-08-13 21:00:00'
       UNION ALL SELECT (@cd_sm + 7), (@cd_p + 65), (@cd_u + 3), @cd_s, 1061.64, 1086.76, 25.12, 0.00, 25.12, NULL, 1.0, 0.6075, 1069.47, 993.60, 'ELO-K32-V2', '2026-08-13 21:00:00'
       UNION ALL SELECT (@cd_sm + 7), (@cd_p + 62), (@cd_u + 4), @cd_s, 1057.20, 1082.32, 25.12, 0.00, 25.12, NULL, 1.0, 0.6075, 1069.47, 993.60, 'ELO-K32-V2', '2026-08-13 21:00:00'
       UNION ALL SELECT (@cd_sm + 7), (@cd_p + 61), (@cd_u + 6), @cd_s, 1054.84, 1079.96, 25.12, 0.00, 25.12, NULL, 1.0, 0.6075, 1069.47, 993.60, 'ELO-K32-V2', '2026-08-13 21:00:00'
       UNION ALL SELECT (@cd_sm + 7), (@cd_p + 67), (@cd_u + 57), @cd_s, 968.00, 942.88, -25.12, 0.00, -25.12, NULL, 0.0, 0.3925, 1069.47, 993.60, 'ELO-K32-V2', '2026-08-13 21:00:00'
       UNION ALL SELECT (@cd_sm + 7), (@cd_p + 68), (@cd_u + 60), @cd_s, 1000.00, 974.88, -25.12, 0.00, -25.12, NULL, 0.0, 0.3925, 1069.47, 993.60, 'ELO-K32-V2', '2026-08-13 21:00:00'
       UNION ALL SELECT (@cd_sm + 7), (@cd_p + 69), (@cd_u + 62), @cd_s, 1000.00, 974.88, -25.12, 0.00, -25.12, NULL, 0.0, 0.3925, 1069.47, 993.60, 'ELO-K32-V2', '2026-08-13 21:00:00'
       UNION ALL SELECT (@cd_sm + 7), (@cd_p + 66), (@cd_u + 63), @cd_s, 1000.00, 974.88, -25.12, 0.00, -25.12, NULL, 0.0, 0.3925, 1069.47, 993.60, 'ELO-K32-V2', '2026-08-13 21:00:00'
       UNION ALL SELECT (@cd_sm + 7), (@cd_p + 70), (@cd_u + 64), @cd_s, 1000.00, 974.88, -25.12, 0.00, -25.12, NULL, 0.0, 0.3925, 1069.47, 993.60, 'ELO-K32-V2', '2026-08-13 21:00:00'
       UNION ALL SELECT (@cd_sm + 8), (@cd_p + 74), (@cd_u + 16), @cd_s, 969.77, 1003.99, 34.22, 0.00, 34.22, NULL, 1.0, 0.4653, 1000.00, 975.82, 'ELO-K32-V2', '2026-08-15 22:00:00'
       UNION ALL SELECT (@cd_sm + 8), (@cd_p + 73), (@cd_u + 17), @cd_s, 969.77, 1003.99, 34.22, 0.00, 34.22, NULL, 1.0, 0.4653, 1000.00, 975.82, 'ELO-K32-V2', '2026-08-15 22:00:00'
       UNION ALL SELECT (@cd_sm + 8), (@cd_p + 71), (@cd_u + 18), @cd_s, 1000.00, 1034.22, 34.22, 0.00, 34.22, NULL, 1.0, 0.4653, 1000.00, 975.82, 'ELO-K32-V2', '2026-08-15 22:00:00'
       UNION ALL SELECT (@cd_sm + 8), (@cd_p + 75), (@cd_u + 19), @cd_s, 969.77, 1003.99, 34.22, 0.00, 34.22, NULL, 1.0, 0.4653, 1000.00, 975.82, 'ELO-K32-V2', '2026-08-15 22:00:00'
       UNION ALL SELECT (@cd_sm + 8), (@cd_p + 72), (@cd_u + 20), @cd_s, 969.77, 1003.99, 34.22, 0.00, 34.22, NULL, 1.0, 0.4653, 1000.00, 975.82, 'ELO-K32-V2', '2026-08-15 22:00:00'
       UNION ALL SELECT (@cd_sm + 8), (@cd_p + 77), (@cd_u + 50), @cd_s, 1000.00, 965.78, -34.22, 0.00, -34.22, NULL, 0.0, 0.5347, 1000.00, 975.82, 'ELO-K32-V2', '2026-08-15 22:00:00'
       UNION ALL SELECT (@cd_sm + 8), (@cd_p + 80), (@cd_u + 51), @cd_s, 1000.00, 965.78, -34.22, 0.00, -34.22, NULL, 0.0, 0.5347, 1000.00, 975.82, 'ELO-K32-V2', '2026-08-15 22:00:00'
       UNION ALL SELECT (@cd_sm + 8), (@cd_p + 78), (@cd_u + 53), @cd_s, 1000.00, 965.78, -34.22, 0.00, -34.22, NULL, 0.0, 0.5347, 1000.00, 975.82, 'ELO-K32-V2', '2026-08-15 22:00:00'
       UNION ALL SELECT (@cd_sm + 8), (@cd_p + 76), (@cd_u + 54), @cd_s, 1000.00, 965.78, -34.22, 0.00, -34.22, NULL, 0.0, 0.5347, 1000.00, 975.82, 'ELO-K32-V2', '2026-08-15 22:00:00'
       UNION ALL SELECT (@cd_sm + 8), (@cd_p + 79), (@cd_u + 56), @cd_s, 1000.00, 965.78, -34.22, 0.00, -34.22, NULL, 0.0, 0.5347, 1000.00, 975.82, 'ELO-K32-V2', '2026-08-15 22:00:00'
       UNION ALL SELECT (@cd_sm + 9), (@cd_p + 85), (@cd_u + 1), @cd_s, 1111.96, 1136.44, 24.48, 0.00, 24.48, NULL, 1.0, 0.6175, 1006.40, 1089.57, 'ELO-K32-V2', '2026-08-17 20:00:00'
       UNION ALL SELECT (@cd_sm + 9), (@cd_p + 82), (@cd_u + 3), @cd_s, 1086.76, 1111.24, 24.48, 0.00, 24.48, NULL, 1.0, 0.6175, 1006.40, 1089.57, 'ELO-K32-V2', '2026-08-17 20:00:00'
       UNION ALL SELECT (@cd_sm + 9), (@cd_p + 83), (@cd_u + 4), @cd_s, 1082.32, 1106.80, 24.48, 0.00, 24.48, NULL, 1.0, 0.6175, 1006.40, 1089.57, 'ELO-K32-V2', '2026-08-17 20:00:00'
       UNION ALL SELECT (@cd_sm + 9), (@cd_p + 81), (@cd_u + 5), @cd_s, 1086.84, 1111.32, 24.48, 0.00, 24.48, NULL, 1.0, 0.6175, 1006.40, 1089.57, 'ELO-K32-V2', '2026-08-17 20:00:00'
       UNION ALL SELECT (@cd_sm + 9), (@cd_p + 84), (@cd_u + 6), @cd_s, 1079.96, 1104.44, 24.48, 0.00, 24.48, NULL, 1.0, 0.6175, 1006.40, 1089.57, 'ELO-K32-V2', '2026-08-17 20:00:00'
       UNION ALL SELECT (@cd_sm + 9), (@cd_p + 87), (@cd_u + 67), @cd_s, 1032.00, 1007.52, -24.48, 0.00, -24.48, NULL, 0.0, 0.3825, 1006.40, 1089.57, 'ELO-K32-V2', '2026-08-17 20:00:00'
       UNION ALL SELECT (@cd_sm + 9), (@cd_p + 90), (@cd_u + 68), @cd_s, 1000.00, 975.52, -24.48, 0.00, -24.48, NULL, 0.0, 0.3825, 1006.40, 1089.57, 'ELO-K32-V2', '2026-08-17 20:00:00'
       UNION ALL SELECT (@cd_sm + 9), (@cd_p + 86), (@cd_u + 69), @cd_s, 1000.00, 975.52, -24.48, 0.00, -24.48, NULL, 0.0, 0.3825, 1006.40, 1089.57, 'ELO-K32-V2', '2026-08-17 20:00:00'
       UNION ALL SELECT (@cd_sm + 9), (@cd_p + 89), (@cd_u + 71), @cd_s, 1000.00, 975.52, -24.48, 0.00, -24.48, NULL, 0.0, 0.3825, 1006.40, 1089.57, 'ELO-K32-V2', '2026-08-17 20:00:00'
       UNION ALL SELECT (@cd_sm + 9), (@cd_p + 88), (@cd_u + 73), @cd_s, 1000.00, 975.52, -24.48, 0.00, -24.48, NULL, 0.0, 0.3825, 1006.40, 1089.57, 'ELO-K32-V2', '2026-08-17 20:00:00'
       UNION ALL SELECT (@cd_sm + 10), (@cd_p + 99), (@cd_u + 10), @cd_s, 1032.00, 1026.72, -5.28, 0.00, -5.28, NULL, 0.5, 0.5825, 979.47, 1037.34, 'ELO-K32-V2', '2026-08-22 22:00:00'
       UNION ALL SELECT (@cd_sm + 10), (@cd_p + 97), (@cd_u + 11), @cd_s, 1030.23, 1024.95, -5.28, 0.00, -5.28, NULL, 0.5, 0.5825, 979.47, 1037.34, 'ELO-K32-V2', '2026-08-22 22:00:00'
       UNION ALL SELECT (@cd_sm + 10), (@cd_p + 96), (@cd_u + 12), @cd_s, 1030.23, 1024.95, -5.28, 0.00, -5.28, NULL, 0.5, 0.5825, 979.47, 1037.34, 'ELO-K32-V2', '2026-08-22 22:00:00'
       UNION ALL SELECT (@cd_sm + 10), (@cd_p + 98), (@cd_u + 13), @cd_s, 1032.00, 1026.72, -5.28, 0.00, -5.28, NULL, 0.5, 0.5825, 979.47, 1037.34, 'ELO-K32-V2', '2026-08-22 22:00:00'
       UNION ALL SELECT (@cd_sm + 10), (@cd_p + 100), (@cd_u + 14), @cd_s, 1062.23, 1056.95, -5.28, 0.00, -5.28, NULL, 0.5, 0.5825, 979.47, 1037.34, 'ELO-K32-V2', '2026-08-22 22:00:00'
       UNION ALL SELECT (@cd_sm + 10), (@cd_p + 93), (@cd_u + 51), @cd_s, 965.78, 971.06, 5.28, 0.00, 5.28, NULL, 0.5, 0.4175, 979.47, 1037.34, 'ELO-K32-V2', '2026-08-22 22:00:00'
       UNION ALL SELECT (@cd_sm + 10), (@cd_p + 91), (@cd_u + 52), @cd_s, 1000.00, 1005.28, 5.28, 0.00, 5.28, NULL, 0.5, 0.4175, 979.47, 1037.34, 'ELO-K32-V2', '2026-08-22 22:00:00'
       UNION ALL SELECT (@cd_sm + 10), (@cd_p + 92), (@cd_u + 54), @cd_s, 965.78, 971.06, 5.28, 0.00, 5.28, NULL, 0.5, 0.4175, 979.47, 1037.34, 'ELO-K32-V2', '2026-08-22 22:00:00'
       UNION ALL SELECT (@cd_sm + 10), (@cd_p + 95), (@cd_u + 55), @cd_s, 1000.00, 1005.28, 5.28, 0.00, 5.28, NULL, 0.5, 0.4175, 979.47, 1037.34, 'ELO-K32-V2', '2026-08-22 22:00:00'
       UNION ALL SELECT (@cd_sm + 10), (@cd_p + 94), (@cd_u + 56), @cd_s, 965.78, 971.06, 5.28, 0.00, 5.28, NULL, 0.5, 0.4175, 979.47, 1037.34, 'ELO-K32-V2', '2026-08-22 22:00:00'
       UNION ALL SELECT (@cd_sm + 11), (@cd_p + 101), (@cd_u + 2), @cd_s, 1111.96, 1130.57, 18.61, 0.00, 18.61, NULL, 1.0, 0.7092, 954.24, 1109.15, 'ELO-K32-V2', '2026-08-23 16:00:00'
       UNION ALL SELECT (@cd_sm + 11), (@cd_p + 105), (@cd_u + 3), @cd_s, 1111.24, 1129.85, 18.61, 0.00, 18.61, NULL, 1.0, 0.7092, 954.24, 1109.15, 'ELO-K32-V2', '2026-08-23 16:00:00'
       UNION ALL SELECT (@cd_sm + 11), (@cd_p + 108), (@cd_u + 4), @cd_s, 1106.80, 1125.41, 18.61, 0.00, 18.61, NULL, 1.0, 0.7092, 954.24, 1109.15, 'ELO-K32-V2', '2026-08-23 16:00:00'
       UNION ALL SELECT (@cd_sm + 11), (@cd_p + 110), (@cd_u + 5), @cd_s, 1111.32, 1129.93, 18.61, 0.00, 18.61, NULL, 1.0, 0.7092, 954.24, 1109.15, 'ELO-K32-V2', '2026-08-23 16:00:00'
       UNION ALL SELECT (@cd_sm + 11), (@cd_p + 103), (@cd_u + 6), @cd_s, 1104.44, 1123.05, 18.61, 0.00, 18.61, NULL, 1.0, 0.7092, 954.24, 1109.15, 'ELO-K32-V2', '2026-08-23 16:00:00'
       UNION ALL SELECT (@cd_sm + 11), (@cd_p + 104), (@cd_u + 44), @cd_s, 942.80, 924.19, -18.61, 0.00, -18.61, NULL, 0.0, 0.2908, 954.24, 1109.15, 'ELO-K32-V2', '2026-08-23 16:00:00'
       UNION ALL SELECT (@cd_sm + 11), (@cd_p + 106), (@cd_u + 45), @cd_s, 942.80, 924.19, -18.61, 0.00, -18.61, NULL, 0.0, 0.2908, 954.24, 1109.15, 'ELO-K32-V2', '2026-08-23 16:00:00'
       UNION ALL SELECT (@cd_sm + 11), (@cd_p + 109), (@cd_u + 46), @cd_s, 968.00, 949.39, -18.61, 0.00, -18.61, NULL, 0.0, 0.2908, 954.24, 1109.15, 'ELO-K32-V2', '2026-08-23 16:00:00'
       UNION ALL SELECT (@cd_sm + 11), (@cd_p + 102), (@cd_u + 47), @cd_s, 942.80, 924.19, -18.61, 0.00, -18.61, NULL, 0.0, 0.2908, 954.24, 1109.15, 'ELO-K32-V2', '2026-08-23 16:00:00'
       UNION ALL SELECT (@cd_sm + 11), (@cd_p + 107), (@cd_u + 48), @cd_s, 974.80, 956.19, -18.61, 0.00, -18.61, NULL, 0.0, 0.2908, 954.24, 1109.15, 'ELO-K32-V2', '2026-08-23 16:00:00'
       UNION ALL SELECT (@cd_sm + 12), (@cd_p + 114), (@cd_u + 22), @cd_s, 1000.00, 1031.28, 31.28, 0.00, 31.28, NULL, 1.0, 0.5113, 974.40, 982.22, 'ELO-K32-V2', '2026-08-24 10:00:00'
       UNION ALL SELECT (@cd_sm + 12), (@cd_p + 112), (@cd_u + 24), @cd_s, 970.36, 1001.64, 31.28, 0.00, 31.28, NULL, 1.0, 0.5113, 974.40, 982.22, 'ELO-K32-V2', '2026-08-24 10:00:00'
       UNION ALL SELECT (@cd_sm + 12), (@cd_p + 115), (@cd_u + 25), @cd_s, 970.36, 1001.64, 31.28, 0.00, 31.28, NULL, 1.0, 0.5113, 974.40, 982.22, 'ELO-K32-V2', '2026-08-24 10:00:00'
       UNION ALL SELECT (@cd_sm + 12), (@cd_p + 111), (@cd_u + 26), @cd_s, 970.36, 1001.64, 31.28, 0.00, 31.28, NULL, 1.0, 0.5113, 974.40, 982.22, 'ELO-K32-V2', '2026-08-24 10:00:00'
       UNION ALL SELECT (@cd_sm + 12), (@cd_p + 113), (@cd_u + 27), @cd_s, 1000.00, 1031.28, 31.28, 0.00, 31.28, NULL, 1.0, 0.5113, 974.40, 982.22, 'ELO-K32-V2', '2026-08-24 10:00:00'
       UNION ALL SELECT (@cd_sm + 12), (@cd_p + 116), (@cd_u + 29), @cd_s, 968.00, 936.72, -31.28, 0.00, -31.28, NULL, 0.0, 0.4887, 974.40, 982.22, 'ELO-K32-V2', '2026-08-24 10:00:00'
       UNION ALL SELECT (@cd_sm + 12), (@cd_p + 120), (@cd_u + 30), @cd_s, 968.00, 936.72, -31.28, 0.00, -31.28, NULL, 0.0, 0.4887, 974.40, 982.22, 'ELO-K32-V2', '2026-08-24 10:00:00'
       UNION ALL SELECT (@cd_sm + 12), (@cd_p + 117), (@cd_u + 31), @cd_s, 968.00, 936.72, -31.28, 0.00, -31.28, NULL, 0.0, 0.4887, 974.40, 982.22, 'ELO-K32-V2', '2026-08-24 10:00:00'
       UNION ALL SELECT (@cd_sm + 12), (@cd_p + 119), (@cd_u + 32), @cd_s, 1000.00, 968.72, -31.28, 0.00, -31.28, NULL, 0.0, 0.4887, 974.40, 982.22, 'ELO-K32-V2', '2026-08-24 10:00:00'
       UNION ALL SELECT (@cd_sm + 12), (@cd_p + 118), (@cd_u + 34), @cd_s, 968.00, 936.72, -31.28, 0.00, -31.28, NULL, 0.0, 0.4887, 974.40, 982.22, 'ELO-K32-V2', '2026-08-24 10:00:00'
       UNION ALL SELECT (@cd_sm + 13), (@cd_p + 125), (@cd_u + 1), @cd_s, 1136.44, 1144.83, 8.39, 0.00, 8.39, NULL, 1.0, 0.7378, 1129.06, 949.38, 'ELO-K32-V2', '2026-08-27 21:00:00'
       UNION ALL SELECT (@cd_sm + 13), (@cd_p + 127), (@cd_u + 2), @cd_s, 1130.57, 1138.96, 8.39, 0.00, 8.39, NULL, 1.0, 0.7378, 1129.06, 949.38, 'ELO-K32-V2', '2026-08-27 21:00:00'
       UNION ALL SELECT (@cd_sm + 13), (@cd_p + 121), (@cd_u + 3), @cd_s, 1129.85, 1138.24, 8.39, 0.00, 8.39, NULL, 1.0, 0.7378, 1129.06, 949.38, 'ELO-K32-V2', '2026-08-27 21:00:00'
       UNION ALL SELECT (@cd_sm + 13), (@cd_p + 128), (@cd_u + 4), @cd_s, 1125.41, 1133.80, 8.39, 0.00, 8.39, NULL, 1.0, 0.7378, 1129.06, 949.38, 'ELO-K32-V2', '2026-08-27 21:00:00'
       UNION ALL SELECT (@cd_sm + 13), (@cd_p + 122), (@cd_u + 6), @cd_s, 1123.05, 1131.44, 8.39, 0.00, 8.39, NULL, 1.0, 0.7378, 1129.06, 949.38, 'ELO-K32-V2', '2026-08-27 21:00:00'
       UNION ALL SELECT (@cd_sm + 13), (@cd_p + 126), (@cd_u + 29), @cd_s, 936.72, 919.94, -16.78, 0.00, -16.78, NULL, 0.0, 0.2622, 1129.06, 949.38, 'ELO-K32-V2', '2026-08-27 21:00:00'
       UNION ALL SELECT (@cd_sm + 13), (@cd_p + 129), (@cd_u + 30), @cd_s, 936.72, 919.94, -16.78, 0.00, -16.78, NULL, 0.0, 0.2622, 1129.06, 949.38, 'ELO-K32-V2', '2026-08-27 21:00:00'
       UNION ALL SELECT (@cd_sm + 13), (@cd_p + 123), (@cd_u + 32), @cd_s, 968.72, 951.94, -16.78, 0.00, -16.78, NULL, 0.0, 0.2622, 1129.06, 949.38, 'ELO-K32-V2', '2026-08-27 21:00:00'
       UNION ALL SELECT (@cd_sm + 13), (@cd_p + 130), (@cd_u + 33), @cd_s, 968.00, 951.22, -16.78, 0.00, -16.78, NULL, 0.0, 0.2622, 1129.06, 949.38, 'ELO-K32-V2', '2026-08-27 21:00:00'
       UNION ALL SELECT (@cd_sm + 13), (@cd_p + 124), (@cd_u + 34), @cd_s, 936.72, 919.94, -16.78, 0.00, -16.78, NULL, 0.0, 0.2622, 1129.06, 949.38, 'ELO-K32-V2', '2026-08-27 21:00:00'
       UNION ALL SELECT (@cd_sm + 14), (@cd_p + 139), (@cd_u + 15), @cd_s, 969.77, 1001.57, 31.80, 0.00, 31.80, NULL, 1.0, 0.5032, 1003.19, 1000.98, 'ELO-K32-V2', '2026-08-29 22:00:00'
       UNION ALL SELECT (@cd_sm + 14), (@cd_p + 140), (@cd_u + 16), @cd_s, 1003.99, 1035.79, 31.80, 0.00, 31.80, NULL, 1.0, 0.5032, 1003.19, 1000.98, 'ELO-K32-V2', '2026-08-29 22:00:00'
       UNION ALL SELECT (@cd_sm + 14), (@cd_p + 136), (@cd_u + 18), @cd_s, 1034.22, 1066.02, 31.80, 0.00, 31.80, NULL, 1.0, 0.5032, 1003.19, 1000.98, 'ELO-K32-V2', '2026-08-29 22:00:00'
       UNION ALL SELECT (@cd_sm + 14), (@cd_p + 137), (@cd_u + 19), @cd_s, 1003.99, 1035.79, 31.80, 0.00, 31.80, NULL, 1.0, 0.5032, 1003.19, 1000.98, 'ELO-K32-V2', '2026-08-29 22:00:00'
       UNION ALL SELECT (@cd_sm + 14), (@cd_p + 138), (@cd_u + 20), @cd_s, 1003.99, 1035.79, 31.80, 0.00, 31.80, NULL, 1.0, 0.5032, 1003.19, 1000.98, 'ELO-K32-V2', '2026-08-29 22:00:00'
       UNION ALL SELECT (@cd_sm + 14), (@cd_p + 132), (@cd_u + 22), @cd_s, 1031.28, 999.48, -31.80, 0.00, -31.80, NULL, 0.0, 0.4968, 1003.19, 1000.98, 'ELO-K32-V2', '2026-08-29 22:00:00'
       UNION ALL SELECT (@cd_sm + 14), (@cd_p + 133), (@cd_u + 23), @cd_s, 970.36, 938.56, -31.80, 0.00, -31.80, NULL, 0.0, 0.4968, 1003.19, 1000.98, 'ELO-K32-V2', '2026-08-29 22:00:00'
       UNION ALL SELECT (@cd_sm + 14), (@cd_p + 135), (@cd_u + 24), @cd_s, 1001.64, 969.84, -31.80, 0.00, -31.80, NULL, 0.0, 0.4968, 1003.19, 1000.98, 'ELO-K32-V2', '2026-08-29 22:00:00'
       UNION ALL SELECT (@cd_sm + 14), (@cd_p + 134), (@cd_u + 27), @cd_s, 1031.28, 999.48, -31.80, 0.00, -31.80, NULL, 0.0, 0.4968, 1003.19, 1000.98, 'ELO-K32-V2', '2026-08-29 22:00:00'
       UNION ALL SELECT (@cd_sm + 14), (@cd_p + 131), (@cd_u + 28), @cd_s, 970.36, 938.56, -31.80, 0.00, -31.80, NULL, 0.0, 0.4968, 1003.19, 1000.98, 'ELO-K32-V2', '2026-08-29 22:00:00'
       UNION ALL SELECT (@cd_sm + 15), (@cd_p + 145), (@cd_u + 1), @cd_s, 1144.83, 1138.62, -6.21, 0.00, -6.21, NULL, 0.5, 0.6942, 993.22, 1135.65, 'ELO-K32-V2', '2026-08-31 20:00:00'
       UNION ALL SELECT (@cd_sm + 15), (@cd_p + 142), (@cd_u + 3), @cd_s, 1138.24, 1132.03, -6.21, 0.00, -6.21, NULL, 0.5, 0.6942, 993.22, 1135.65, 'ELO-K32-V2', '2026-08-31 20:00:00'
       UNION ALL SELECT (@cd_sm + 15), (@cd_p + 141), (@cd_u + 4), @cd_s, 1133.80, 1127.59, -6.21, 0.00, -6.21, NULL, 0.5, 0.6942, 993.22, 1135.65, 'ELO-K32-V2', '2026-08-31 20:00:00'
       UNION ALL SELECT (@cd_sm + 15), (@cd_p + 144), (@cd_u + 5), @cd_s, 1129.93, 1123.72, -6.21, 0.00, -6.21, NULL, 0.5, 0.6942, 993.22, 1135.65, 'ELO-K32-V2', '2026-08-31 20:00:00'
       UNION ALL SELECT (@cd_sm + 15), (@cd_p + 143), (@cd_u + 6), @cd_s, 1131.44, 1125.23, -6.21, 0.00, -6.21, NULL, 0.5, 0.6942, 993.22, 1135.65, 'ELO-K32-V2', '2026-08-31 20:00:00'
       UNION ALL SELECT (@cd_sm + 15), (@cd_p + 150), (@cd_u + 67), @cd_s, 1007.52, 1019.95, 12.43, 0.00, 12.43, NULL, 0.5, 0.3058, 993.22, 1135.65, 'ELO-K32-V2', '2026-08-31 20:00:00'
       UNION ALL SELECT (@cd_sm + 15), (@cd_p + 146), (@cd_u + 68), @cd_s, 975.52, 987.95, 12.43, 0.00, 12.43, NULL, 0.5, 0.3058, 993.22, 1135.65, 'ELO-K32-V2', '2026-08-31 20:00:00'
       UNION ALL SELECT (@cd_sm + 15), (@cd_p + 148), (@cd_u + 70), @cd_s, 1032.00, 1044.43, 12.43, 0.00, 12.43, NULL, 0.5, 0.3058, 993.22, 1135.65, 'ELO-K32-V2', '2026-08-31 20:00:00'
       UNION ALL SELECT (@cd_sm + 15), (@cd_p + 147), (@cd_u + 71), @cd_s, 975.52, 987.95, 12.43, 0.00, 12.43, NULL, 0.5, 0.3058, 993.22, 1135.65, 'ELO-K32-V2', '2026-08-31 20:00:00'
       UNION ALL SELECT (@cd_sm + 15), (@cd_p + 149), (@cd_u + 73), @cd_s, 975.52, 987.95, 12.43, 0.00, 12.43, NULL, 0.5, 0.3058, 993.22, 1135.65, 'ELO-K32-V2', '2026-08-31 20:00:00'
       UNION ALL SELECT (@cd_sm + 16), (@cd_p + 159), (@cd_u + 50), @cd_s, 965.78, 996.10, 30.32, 0.00, 30.32, NULL, 1.0, 0.5263, 982.64, 964.35, 'ELO-K32-V2', '2026-09-02 22:00:00'
       UNION ALL SELECT (@cd_sm + 16), (@cd_p + 157), (@cd_u + 52), @cd_s, 1005.28, 1035.60, 30.32, 0.00, 30.32, NULL, 1.0, 0.5263, 982.64, 964.35, 'ELO-K32-V2', '2026-09-02 22:00:00'
       UNION ALL SELECT (@cd_sm + 16), (@cd_p + 158), (@cd_u + 53), @cd_s, 965.78, 996.10, 30.32, 0.00, 30.32, NULL, 1.0, 0.5263, 982.64, 964.35, 'ELO-K32-V2', '2026-09-02 22:00:00'
       UNION ALL SELECT (@cd_sm + 16), (@cd_p + 156), (@cd_u + 55), @cd_s, 1005.28, 1035.60, 30.32, 0.00, 30.32, NULL, 1.0, 0.5263, 982.64, 964.35, 'ELO-K32-V2', '2026-09-02 22:00:00'
       UNION ALL SELECT (@cd_sm + 16), (@cd_p + 160), (@cd_u + 56), @cd_s, 971.06, 1001.38, 30.32, 0.00, 30.32, NULL, 1.0, 0.5263, 982.64, 964.35, 'ELO-K32-V2', '2026-09-02 22:00:00'
       UNION ALL SELECT (@cd_sm + 16), (@cd_p + 154), (@cd_u + 57), @cd_s, 942.88, 912.56, -30.32, 0.00, -30.32, NULL, 0.0, 0.4737, 982.64, 964.35, 'ELO-K32-V2', '2026-09-02 22:00:00'
       UNION ALL SELECT (@cd_sm + 16), (@cd_p + 152), (@cd_u + 58), @cd_s, 968.00, 937.68, -30.32, 0.00, -30.32, NULL, 0.0, 0.4737, 982.64, 964.35, 'ELO-K32-V2', '2026-09-02 22:00:00'
       UNION ALL SELECT (@cd_sm + 16), (@cd_p + 155), (@cd_u + 59), @cd_s, 968.00, 937.68, -30.32, 0.00, -30.32, NULL, 0.0, 0.4737, 982.64, 964.35, 'ELO-K32-V2', '2026-09-02 22:00:00'
       UNION ALL SELECT (@cd_sm + 16), (@cd_p + 153), (@cd_u + 60), @cd_s, 974.88, 944.56, -30.32, 0.00, -30.32, NULL, 0.0, 0.4737, 982.64, 964.35, 'ELO-K32-V2', '2026-09-02 22:00:00'
       UNION ALL SELECT (@cd_sm + 16), (@cd_p + 151), (@cd_u + 61), @cd_s, 968.00, 937.68, -30.32, 0.00, -30.32, NULL, 0.0, 0.4737, 982.64, 964.35, 'ELO-K32-V2', '2026-09-02 22:00:00'
       UNION ALL SELECT (@cd_sm + 17), (@cd_p + 163), (@cd_u + 22), @cd_s, 999.48, 1027.01, 27.53, 0.00, 27.53, NULL, 1.0, 0.5698, 988.16, 939.35, 'ELO-K32-V2', '2026-09-04 22:00:00'
       UNION ALL SELECT (@cd_sm + 17), (@cd_p + 169), (@cd_u + 23), @cd_s, 938.56, 966.09, 27.53, 0.00, 27.53, NULL, 1.0, 0.5698, 988.16, 939.35, 'ELO-K32-V2', '2026-09-04 22:00:00'
       UNION ALL SELECT (@cd_sm + 17), (@cd_p + 162), (@cd_u + 25), @cd_s, 1001.64, 1029.17, 27.53, 0.00, 27.53, NULL, 1.0, 0.5698, 988.16, 939.35, 'ELO-K32-V2', '2026-09-04 22:00:00'
       UNION ALL SELECT (@cd_sm + 17), (@cd_p + 161), (@cd_u + 26), @cd_s, 1001.64, 1029.17, 27.53, 0.00, 27.53, NULL, 1.0, 0.5698, 988.16, 939.35, 'ELO-K32-V2', '2026-09-04 22:00:00'
       UNION ALL SELECT (@cd_sm + 17), (@cd_p + 164), (@cd_u + 27), @cd_s, 999.48, 1027.01, 27.53, 0.00, 27.53, NULL, 1.0, 0.5698, 988.16, 939.35, 'ELO-K32-V2', '2026-09-04 22:00:00'
       UNION ALL SELECT (@cd_sm + 17), (@cd_p + 166), (@cd_u + 43), @cd_s, 942.80, 915.27, -27.53, 0.00, -27.53, NULL, 0.0, 0.4302, 988.16, 939.35, 'ELO-K32-V2', '2026-09-04 22:00:00'
       UNION ALL SELECT (@cd_sm + 17), (@cd_p + 167), (@cd_u + 44), @cd_s, 924.19, 896.66, -27.53, 0.00, -27.53, NULL, 0.0, 0.4302, 988.16, 939.35, 'ELO-K32-V2', '2026-09-04 22:00:00'
       UNION ALL SELECT (@cd_sm + 17), (@cd_p + 170), (@cd_u + 45), @cd_s, 924.19, 896.66, -27.53, 0.00, -27.53, NULL, 0.0, 0.4302, 988.16, 939.35, 'ELO-K32-V2', '2026-09-04 22:00:00'
       UNION ALL SELECT (@cd_sm + 17), (@cd_p + 168), (@cd_u + 46), @cd_s, 949.39, 921.86, -27.53, 0.00, -27.53, NULL, 0.0, 0.4302, 988.16, 939.35, 'ELO-K32-V2', '2026-09-04 22:00:00'
       UNION ALL SELECT (@cd_sm + 17), (@cd_p + 165), (@cd_u + 48), @cd_s, 956.19, 928.66, -27.53, 0.00, -27.53, NULL, 0.0, 0.4302, 988.16, 939.35, 'ELO-K32-V2', '2026-09-04 22:00:00'
       UNION ALL SELECT (@cd_sm + 18), (@cd_p + 173), (@cd_u + 15), @cd_s, 1001.57, 1032.62, 31.05, 0.00, 31.05, NULL, 1.0, 0.5149, 1028.63, 1018.29, 'ELO-K32-V2', '2026-09-05 22:00:00'
       UNION ALL SELECT (@cd_sm + 18), (@cd_p + 175), (@cd_u + 16), @cd_s, 1035.79, 1066.84, 31.05, 0.00, 31.05, NULL, 1.0, 0.5149, 1028.63, 1018.29, 'ELO-K32-V2', '2026-09-05 22:00:00'
       UNION ALL SELECT (@cd_sm + 18), (@cd_p + 171), (@cd_u + 17), @cd_s, 1003.99, 1035.04, 31.05, 0.00, 31.05, NULL, 1.0, 0.5149, 1028.63, 1018.29, 'ELO-K32-V2', '2026-09-05 22:00:00'
       UNION ALL SELECT (@cd_sm + 18), (@cd_p + 174), (@cd_u + 18), @cd_s, 1066.02, 1097.07, 31.05, 0.00, 31.05, NULL, 1.0, 0.5149, 1028.63, 1018.29, 'ELO-K32-V2', '2026-09-05 22:00:00'
       UNION ALL SELECT (@cd_sm + 18), (@cd_p + 172), (@cd_u + 19), @cd_s, 1035.79, 1066.84, 31.05, 0.00, 31.05, NULL, 1.0, 0.5149, 1028.63, 1018.29, 'ELO-K32-V2', '2026-09-05 22:00:00'
       UNION ALL SELECT (@cd_sm + 18), (@cd_p + 176), (@cd_u + 67), @cd_s, 1019.95, 988.90, -31.05, 0.00, -31.05, NULL, 0.0, 0.4851, 1028.63, 1018.29, 'ELO-K32-V2', '2026-09-05 22:00:00'
       UNION ALL SELECT (@cd_sm + 18), (@cd_p + 178), (@cd_u + 69), @cd_s, 975.52, 944.47, -31.05, 0.00, -31.05, NULL, 0.0, 0.4851, 1028.63, 1018.29, 'ELO-K32-V2', '2026-09-05 22:00:00'
       UNION ALL SELECT (@cd_sm + 18), (@cd_p + 179), (@cd_u + 72), @cd_s, 1032.00, 1000.95, -31.05, 0.00, -31.05, NULL, 0.0, 0.4851, 1028.63, 1018.29, 'ELO-K32-V2', '2026-09-05 22:00:00'
       UNION ALL SELECT (@cd_sm + 18), (@cd_p + 180), (@cd_u + 74), @cd_s, 1032.00, 1000.95, -31.05, 0.00, -31.05, NULL, 0.0, 0.4851, 1028.63, 1018.29, 'ELO-K32-V2', '2026-09-05 22:00:00'
       UNION ALL SELECT (@cd_sm + 18), (@cd_p + 177), (@cd_u + 75), @cd_s, 1032.00, 1000.95, -31.05, 0.00, -31.05, NULL, 0.0, 0.4851, 1028.63, 1018.29, 'ELO-K32-V2', '2026-09-05 22:00:00'
       UNION ALL SELECT (@cd_sm + 19), (@cd_p + 187), (@cd_u + 1), @cd_s, 1138.62, 1150.50, 11.88, 0.00, 11.88, NULL, 1.0, 0.6289, 1040.57, 1132.18, 'ELO-K32-V2', '2026-09-06 09:00:00'
       UNION ALL SELECT (@cd_sm + 19), (@cd_p + 189), (@cd_u + 2), @cd_s, 1138.96, 1150.84, 11.88, 0.00, 11.88, NULL, 1.0, 0.6289, 1040.57, 1132.18, 'ELO-K32-V2', '2026-09-06 09:00:00'
       UNION ALL SELECT (@cd_sm + 19), (@cd_p + 186), (@cd_u + 3), @cd_s, 1132.03, 1143.91, 11.88, 0.00, 11.88, NULL, 1.0, 0.6289, 1040.57, 1132.18, 'ELO-K32-V2', '2026-09-06 09:00:00'
       UNION ALL SELECT (@cd_sm + 19), (@cd_p + 188), (@cd_u + 4), @cd_s, 1127.59, 1139.47, 11.88, 0.00, 11.88, NULL, 1.0, 0.6289, 1040.57, 1132.18, 'ELO-K32-V2', '2026-09-06 09:00:00'
       UNION ALL SELECT (@cd_sm + 19), (@cd_p + 190), (@cd_u + 5), @cd_s, 1123.72, 1135.60, 11.88, 0.00, 11.88, NULL, 1.0, 0.6289, 1040.57, 1132.18, 'ELO-K32-V2', '2026-09-06 09:00:00'
       UNION ALL SELECT (@cd_sm + 19), (@cd_p + 184), (@cd_u + 8), @cd_s, 1062.23, 1038.48, -23.75, 0.00, -23.75, NULL, 0.0, 0.3711, 1040.57, 1132.18, 'ELO-K32-V2', '2026-09-06 09:00:00'
       UNION ALL SELECT (@cd_sm + 19), (@cd_p + 182), (@cd_u + 9), @cd_s, 1062.23, 1038.48, -23.75, 0.00, -23.75, NULL, 0.0, 0.3711, 1040.57, 1132.18, 'ELO-K32-V2', '2026-09-06 09:00:00'
       UNION ALL SELECT (@cd_sm + 19), (@cd_p + 185), (@cd_u + 10), @cd_s, 1026.72, 1002.97, -23.75, 0.00, -23.75, NULL, 0.0, 0.3711, 1040.57, 1132.18, 'ELO-K32-V2', '2026-09-06 09:00:00'
       UNION ALL SELECT (@cd_sm + 19), (@cd_p + 181), (@cd_u + 12), @cd_s, 1024.95, 1001.20, -23.75, 0.00, -23.75, NULL, 0.0, 0.3711, 1040.57, 1132.18, 'ELO-K32-V2', '2026-09-06 09:00:00'
       UNION ALL SELECT (@cd_sm + 19), (@cd_p + 183), (@cd_u + 13), @cd_s, 1026.72, 1002.97, -23.75, 0.00, -23.75, NULL, 0.0, 0.3711, 1040.57, 1132.18, 'ELO-K32-V2', '2026-09-06 09:00:00'
       UNION ALL SELECT (@cd_sm + 20), (@cd_p + 193), (@cd_u + 29), @cd_s, 919.94, 954.24, 34.30, 0.00, 34.30, NULL, 1.0, 0.4640, 935.95, 961.04, 'ELO-K32-V2', '2026-09-07 10:00:00'
       UNION ALL SELECT (@cd_sm + 20), (@cd_p + 194), (@cd_u + 31), @cd_s, 936.72, 971.02, 34.30, 0.00, 34.30, NULL, 1.0, 0.4640, 935.95, 961.04, 'ELO-K32-V2', '2026-09-07 10:00:00'
       UNION ALL SELECT (@cd_sm + 20), (@cd_p + 192), (@cd_u + 32), @cd_s, 951.94, 986.24, 34.30, 0.00, 34.30, NULL, 1.0, 0.4640, 935.95, 961.04, 'ELO-K32-V2', '2026-09-07 10:00:00'
       UNION ALL SELECT (@cd_sm + 20), (@cd_p + 195), (@cd_u + 33), @cd_s, 951.22, 985.52, 34.30, 0.00, 34.30, NULL, 1.0, 0.4640, 935.95, 961.04, 'ELO-K32-V2', '2026-09-07 10:00:00'
       UNION ALL SELECT (@cd_sm + 20), (@cd_p + 191), (@cd_u + 34), @cd_s, 919.94, 954.24, 34.30, 0.00, 34.30, NULL, 1.0, 0.4640, 935.95, 961.04, 'ELO-K32-V2', '2026-09-07 10:00:00'
       UNION ALL SELECT (@cd_sm + 20), (@cd_p + 197), (@cd_u + 57), @cd_s, 912.56, 878.26, -34.30, 0.00, -34.30, NULL, 0.0, 0.5360, 935.95, 961.04, 'ELO-K32-V2', '2026-09-07 10:00:00'
       UNION ALL SELECT (@cd_sm + 20), (@cd_p + 196), (@cd_u + 62), @cd_s, 974.88, 940.58, -34.30, 0.00, -34.30, NULL, 0.0, 0.5360, 935.95, 961.04, 'ELO-K32-V2', '2026-09-07 10:00:00'
       UNION ALL SELECT (@cd_sm + 20), (@cd_p + 200), (@cd_u + 63), @cd_s, 974.88, 940.58, -34.30, 0.00, -34.30, NULL, 0.0, 0.5360, 935.95, 961.04, 'ELO-K32-V2', '2026-09-07 10:00:00'
       UNION ALL SELECT (@cd_sm + 20), (@cd_p + 198), (@cd_u + 64), @cd_s, 974.88, 940.58, -34.30, 0.00, -34.30, NULL, 0.0, 0.5360, 935.95, 961.04, 'ELO-K32-V2', '2026-09-07 10:00:00'
       UNION ALL SELECT (@cd_sm + 20), (@cd_p + 199), (@cd_u + 65), @cd_s, 968.00, 933.70, -34.30, 0.00, -34.30, NULL, 0.0, 0.5360, 935.95, 961.04, 'ELO-K32-V2', '2026-09-07 10:00:00'
       UNION ALL SELECT (@cd_sm + 21), (@cd_p + 201), (@cd_u + 2), @cd_s, 1150.84, 1160.94, 10.10, 0.00, 10.10, NULL, 1.0, 0.6843, 1139.01, 1004.64, 'ELO-K32-V2', '2026-09-10 21:00:00'
       UNION ALL SELECT (@cd_sm + 21), (@cd_p + 204), (@cd_u + 3), @cd_s, 1143.91, 1154.01, 10.10, 0.00, 10.10, NULL, 1.0, 0.6843, 1139.01, 1004.64, 'ELO-K32-V2', '2026-09-10 21:00:00'
       UNION ALL SELECT (@cd_sm + 21), (@cd_p + 205), (@cd_u + 4), @cd_s, 1139.47, 1149.57, 10.10, 0.00, 10.10, NULL, 1.0, 0.6843, 1139.01, 1004.64, 'ELO-K32-V2', '2026-09-10 21:00:00'
       UNION ALL SELECT (@cd_sm + 21), (@cd_p + 202), (@cd_u + 5), @cd_s, 1135.60, 1145.70, 10.10, 0.00, 10.10, NULL, 1.0, 0.6843, 1139.01, 1004.64, 'ELO-K32-V2', '2026-09-10 21:00:00'
       UNION ALL SELECT (@cd_sm + 21), (@cd_p + 203), (@cd_u + 6), @cd_s, 1125.23, 1135.33, 10.10, 0.00, 10.10, NULL, 1.0, 0.6843, 1139.01, 1004.64, 'ELO-K32-V2', '2026-09-10 21:00:00'
       UNION ALL SELECT (@cd_sm + 21), (@cd_p + 208), (@cd_u + 67), @cd_s, 988.90, 968.70, -20.20, 0.00, -20.20, NULL, 0.0, 0.3157, 1139.01, 1004.64, 'ELO-K32-V2', '2026-09-10 21:00:00'
       UNION ALL SELECT (@cd_sm + 21), (@cd_p + 207), (@cd_u + 68), @cd_s, 987.95, 967.75, -20.20, 0.00, -20.20, NULL, 0.0, 0.3157, 1139.01, 1004.64, 'ELO-K32-V2', '2026-09-10 21:00:00'
       UNION ALL SELECT (@cd_sm + 21), (@cd_p + 210), (@cd_u + 70), @cd_s, 1044.43, 1024.23, -20.20, 0.00, -20.20, NULL, 0.0, 0.3157, 1139.01, 1004.64, 'ELO-K32-V2', '2026-09-10 21:00:00'
       UNION ALL SELECT (@cd_sm + 21), (@cd_p + 206), (@cd_u + 74), @cd_s, 1000.95, 980.75, -20.20, 0.00, -20.20, NULL, 0.0, 0.3157, 1139.01, 1004.64, 'ELO-K32-V2', '2026-09-10 21:00:00'
       UNION ALL SELECT (@cd_sm + 21), (@cd_p + 209), (@cd_u + 75), @cd_s, 1000.95, 980.75, -20.20, 0.00, -20.20, NULL, 0.0, 0.3157, 1139.01, 1004.64, 'ELO-K32-V2', '2026-09-10 21:00:00'
       UNION ALL SELECT (@cd_sm + 22), (@cd_p + 213), (@cd_u + 50), @cd_s, 996.10, 957.30, -38.80, 0.00, -38.80, NULL, 0.0, 0.6062, 1001.88, 926.96, 'ELO-K32-V2', '2026-09-12 22:00:00'
       UNION ALL SELECT (@cd_sm + 22), (@cd_p + 211), (@cd_u + 51), @cd_s, 971.06, 932.26, -38.80, 0.00, -38.80, NULL, 0.0, 0.6062, 1001.88, 926.96, 'ELO-K32-V2', '2026-09-12 22:00:00'
       UNION ALL SELECT (@cd_sm + 22), (@cd_p + 214), (@cd_u + 52), @cd_s, 1035.60, 996.80, -38.80, 0.00, -38.80, NULL, 0.0, 0.6062, 1001.88, 926.96, 'ELO-K32-V2', '2026-09-12 22:00:00'
       UNION ALL SELECT (@cd_sm + 22), (@cd_p + 215), (@cd_u + 54), @cd_s, 971.06, 932.26, -38.80, 0.00, -38.80, NULL, 0.0, 0.6062, 1001.88, 926.96, 'ELO-K32-V2', '2026-09-12 22:00:00'
       UNION ALL SELECT (@cd_sm + 22), (@cd_p + 212), (@cd_u + 55), @cd_s, 1035.60, 996.80, -38.80, 0.00, -38.80, NULL, 0.0, 0.6062, 1001.88, 926.96, 'ELO-K32-V2', '2026-09-12 22:00:00'
       UNION ALL SELECT (@cd_sm + 22), (@cd_p + 220), (@cd_u + 57), @cd_s, 878.26, 917.06, 38.80, 0.00, 38.80, NULL, 1.0, 0.3938, 1001.88, 926.96, 'ELO-K32-V2', '2026-09-12 22:00:00'
       UNION ALL SELECT (@cd_sm + 22), (@cd_p + 216), (@cd_u + 58), @cd_s, 937.68, 976.48, 38.80, 0.00, 38.80, NULL, 1.0, 0.3938, 1001.88, 926.96, 'ELO-K32-V2', '2026-09-12 22:00:00'
       UNION ALL SELECT (@cd_sm + 22), (@cd_p + 219), (@cd_u + 59), @cd_s, 937.68, 976.48, 38.80, 0.00, 38.80, NULL, 1.0, 0.3938, 1001.88, 926.96, 'ELO-K32-V2', '2026-09-12 22:00:00'
       UNION ALL SELECT (@cd_sm + 22), (@cd_p + 218), (@cd_u + 62), @cd_s, 940.58, 979.38, 38.80, 0.00, 38.80, NULL, 1.0, 0.3938, 1001.88, 926.96, 'ELO-K32-V2', '2026-09-12 22:00:00'
       UNION ALL SELECT (@cd_sm + 22), (@cd_p + 217), (@cd_u + 63), @cd_s, 940.58, 979.38, 38.80, 0.00, 38.80, NULL, 1.0, 0.3938, 1001.88, 926.96, 'ELO-K32-V2', '2026-09-12 22:00:00'
       UNION ALL SELECT (@cd_sm + 23), (@cd_p + 229), (@cd_u + 1), @cd_s, 1150.50, 1160.05, 9.55, 0.00, 9.55, NULL, 1.0, 0.7015, 1000.00, 1148.41, 'ELO-K32-V2', '2026-09-14 18:00:00'
       UNION ALL SELECT (@cd_sm + 23), (@cd_p + 230), (@cd_u + 2), @cd_s, 1160.94, 1170.49, 9.55, 0.00, 9.55, NULL, 1.0, 0.7015, 1000.00, 1148.41, 'ELO-K32-V2', '2026-09-14 18:00:00'
       UNION ALL SELECT (@cd_sm + 23), (@cd_p + 226), (@cd_u + 4), @cd_s, 1149.57, 1159.12, 9.55, 0.00, 9.55, NULL, 1.0, 0.7015, 1000.00, 1148.41, 'ELO-K32-V2', '2026-09-14 18:00:00'
       UNION ALL SELECT (@cd_sm + 23), (@cd_p + 227), (@cd_u + 5), @cd_s, 1145.70, 1155.25, 9.55, 0.00, 9.55, NULL, 1.0, 0.7015, 1000.00, 1148.41, 'ELO-K32-V2', '2026-09-14 18:00:00'
       UNION ALL SELECT (@cd_sm + 23), (@cd_p + 228), (@cd_u + 6), @cd_s, 1135.33, 1144.88, 9.55, 0.00, 9.55, NULL, 1.0, 0.7015, 1000.00, 1148.41, 'ELO-K32-V2', '2026-09-14 18:00:00'
       UNION ALL SELECT (@cd_sm + 23), (@cd_p + 222), (@cd_u + 36), @cd_s, 1000.00, 980.90, -19.10, 0.00, -19.10, NULL, 0.0, 0.2985, 1000.00, 1148.41, 'ELO-K32-V2', '2026-09-14 18:00:00'
       UNION ALL SELECT (@cd_sm + 23), (@cd_p + 221), (@cd_u + 37), @cd_s, 1000.00, 980.90, -19.10, 0.00, -19.10, NULL, 0.0, 0.2985, 1000.00, 1148.41, 'ELO-K32-V2', '2026-09-14 18:00:00'
       UNION ALL SELECT (@cd_sm + 23), (@cd_p + 225), (@cd_u + 38), @cd_s, 1000.00, 980.90, -19.10, 0.00, -19.10, NULL, 0.0, 0.2985, 1000.00, 1148.41, 'ELO-K32-V2', '2026-09-14 18:00:00'
       UNION ALL SELECT (@cd_sm + 23), (@cd_p + 224), (@cd_u + 41), @cd_s, 1000.00, 980.90, -19.10, 0.00, -19.10, NULL, 0.0, 0.2985, 1000.00, 1148.41, 'ELO-K32-V2', '2026-09-14 18:00:00'
       UNION ALL SELECT (@cd_sm + 23), (@cd_p + 223), (@cd_u + 42), @cd_s, 1000.00, 980.90, -19.10, 0.00, -19.10, NULL, 0.0, 0.2985, 1000.00, 1148.41, 'ELO-K32-V2', '2026-09-14 18:00:00'
       UNION ALL SELECT (@cd_sm + 24), (@cd_p + 235), (@cd_u + 1), @cd_s, 1160.05, 1169.23, 9.18, 0.00, 9.18, NULL, 1.0, 0.7131, 998.75, 1156.94, 'ELO-K32-V2', '2026-09-18 22:00:00'
       UNION ALL SELECT (@cd_sm + 24), (@cd_p + 231), (@cd_u + 2), @cd_s, 1170.49, 1179.67, 9.18, 0.00, 9.18, NULL, 1.0, 0.7131, 998.75, 1156.94, 'ELO-K32-V2', '2026-09-18 22:00:00'
       UNION ALL SELECT (@cd_sm + 24), (@cd_p + 234), (@cd_u + 3), @cd_s, 1154.01, 1163.19, 9.18, 0.00, 9.18, NULL, 1.0, 0.7131, 998.75, 1156.94, 'ELO-K32-V2', '2026-09-18 22:00:00'
       UNION ALL SELECT (@cd_sm + 24), (@cd_p + 232), (@cd_u + 5), @cd_s, 1155.25, 1164.43, 9.18, 0.00, 9.18, NULL, 1.0, 0.7131, 998.75, 1156.94, 'ELO-K32-V2', '2026-09-18 22:00:00'
       UNION ALL SELECT (@cd_sm + 24), (@cd_p + 233), (@cd_u + 6), @cd_s, 1144.88, 1154.06, 9.18, 0.00, 9.18, NULL, 1.0, 0.7131, 998.75, 1156.94, 'ELO-K32-V2', '2026-09-18 22:00:00'
       UNION ALL SELECT (@cd_sm + 24), (@cd_p + 239), (@cd_u + 24), @cd_s, 969.84, 951.48, -18.36, 0.00, -18.36, NULL, 0.0, 0.2869, 998.75, 1156.94, 'ELO-K32-V2', '2026-09-18 22:00:00'
       UNION ALL SELECT (@cd_sm + 24), (@cd_p + 236), (@cd_u + 25), @cd_s, 1029.17, 1010.81, -18.36, 0.00, -18.36, NULL, 0.0, 0.2869, 998.75, 1156.94, 'ELO-K32-V2', '2026-09-18 22:00:00'
       UNION ALL SELECT (@cd_sm + 24), (@cd_p + 238), (@cd_u + 26), @cd_s, 1029.17, 1010.81, -18.36, 0.00, -18.36, NULL, 0.0, 0.2869, 998.75, 1156.94, 'ELO-K32-V2', '2026-09-18 22:00:00'
       UNION ALL SELECT (@cd_sm + 24), (@cd_p + 240), (@cd_u + 27), @cd_s, 1027.01, 1008.65, -18.36, 0.00, -18.36, NULL, 0.0, 0.2869, 998.75, 1156.94, 'ELO-K32-V2', '2026-09-18 22:00:00'
       UNION ALL SELECT (@cd_sm + 24), (@cd_p + 237), (@cd_u + 28), @cd_s, 938.56, 920.20, -18.36, 0.00, -18.36, NULL, 0.0, 0.2869, 998.75, 1156.94, 'ELO-K32-V2', '2026-09-18 22:00:00'
       UNION ALL SELECT (@cd_sm + 25), (@cd_p + 250), (@cd_u + 15), @cd_s, 1032.62, 990.09, -42.53, 0.00, -42.53, NULL, 0.0, 0.6645, 1053.47, 934.72, 'ELO-K32-V2', '2026-09-19 22:00:00'
       UNION ALL SELECT (@cd_sm + 25), (@cd_p + 248), (@cd_u + 16), @cd_s, 1066.84, 1024.31, -42.53, 0.00, -42.53, NULL, 0.0, 0.6645, 1053.47, 934.72, 'ELO-K32-V2', '2026-09-19 22:00:00'
       UNION ALL SELECT (@cd_sm + 25), (@cd_p + 246), (@cd_u + 17), @cd_s, 1035.04, 992.51, -42.53, 0.00, -42.53, NULL, 0.0, 0.6645, 1053.47, 934.72, 'ELO-K32-V2', '2026-09-19 22:00:00'
       UNION ALL SELECT (@cd_sm + 25), (@cd_p + 249), (@cd_u + 18), @cd_s, 1097.07, 1054.54, -42.53, 0.00, -42.53, NULL, 0.0, 0.6645, 1053.47, 934.72, 'ELO-K32-V2', '2026-09-19 22:00:00'
       UNION ALL SELECT (@cd_sm + 25), (@cd_p + 247), (@cd_u + 20), @cd_s, 1035.79, 993.26, -42.53, 0.00, -42.53, NULL, 0.0, 0.6645, 1053.47, 934.72, 'ELO-K32-V2', '2026-09-19 22:00:00'
       UNION ALL SELECT (@cd_sm + 25), (@cd_p + 244), (@cd_u + 57), @cd_s, 917.06, 938.32, 21.26, 0.00, 21.26, NULL, 1.0, 0.3355, 1053.47, 934.72, 'ELO-K32-V2', '2026-09-19 22:00:00'
       UNION ALL SELECT (@cd_sm + 25), (@cd_p + 241), (@cd_u + 60), @cd_s, 944.56, 987.09, 42.53, 0.00, 42.53, NULL, 1.0, 0.3355, 1053.47, 934.72, 'ELO-K32-V2', '2026-09-19 22:00:00'
       UNION ALL SELECT (@cd_sm + 25), (@cd_p + 245), (@cd_u + 61), @cd_s, 937.68, 980.21, 42.53, 0.00, 42.53, NULL, 1.0, 0.3355, 1053.47, 934.72, 'ELO-K32-V2', '2026-09-19 22:00:00'
       UNION ALL SELECT (@cd_sm + 25), (@cd_p + 242), (@cd_u + 64), @cd_s, 940.58, 983.11, 42.53, 0.00, 42.53, NULL, 1.0, 0.3355, 1053.47, 934.72, 'ELO-K32-V2', '2026-09-19 22:00:00'
       UNION ALL SELECT (@cd_sm + 25), (@cd_p + 243), (@cd_u + 65), @cd_s, 933.70, 976.23, 42.53, 0.00, 42.53, NULL, 1.0, 0.3355, 1053.47, 934.72, 'ELO-K32-V2', '2026-09-19 22:00:00'
       UNION ALL SELECT (@cd_sm + 26), (@cd_p + 256), (@cd_u + 8), @cd_s, 1038.48, 1066.50, 28.02, 0.00, 28.02, NULL, 1.0, 0.5622, 1032.01, 988.54, 'ELO-K32-V2', '2026-09-21 09:00:00'
       UNION ALL SELECT (@cd_sm + 26), (@cd_p + 252), (@cd_u + 9), @cd_s, 1038.48, 1066.50, 28.02, 0.00, 28.02, NULL, 1.0, 0.5622, 1032.01, 988.54, 'ELO-K32-V2', '2026-09-21 09:00:00'
       UNION ALL SELECT (@cd_sm + 26), (@cd_p + 253), (@cd_u + 11), @cd_s, 1024.95, 1052.97, 28.02, 0.00, 28.02, NULL, 1.0, 0.5622, 1032.01, 988.54, 'ELO-K32-V2', '2026-09-21 09:00:00'
       UNION ALL SELECT (@cd_sm + 26), (@cd_p + 257), (@cd_u + 12), @cd_s, 1001.20, 1029.22, 28.02, 0.00, 28.02, NULL, 1.0, 0.5622, 1032.01, 988.54, 'ELO-K32-V2', '2026-09-21 09:00:00'
       UNION ALL SELECT (@cd_sm + 26), (@cd_p + 255), (@cd_u + 14), @cd_s, 1056.95, 1084.97, 28.02, 0.00, 28.02, NULL, 1.0, 0.5622, 1032.01, 988.54, 'ELO-K32-V2', '2026-09-21 09:00:00'
       UNION ALL SELECT (@cd_sm + 26), (@cd_p + 254), (@cd_u + 36), @cd_s, 980.90, 952.88, -28.02, 0.00, -28.02, NULL, 0.0, 0.4378, 1032.01, 988.54, 'ELO-K32-V2', '2026-09-21 09:00:00'
       UNION ALL SELECT (@cd_sm + 26), (@cd_p + 258), (@cd_u + 37), @cd_s, 980.90, 952.88, -28.02, 0.00, -28.02, NULL, 0.0, 0.4378, 1032.01, 988.54, 'ELO-K32-V2', '2026-09-21 09:00:00'
       UNION ALL SELECT (@cd_sm + 26), (@cd_p + 259), (@cd_u + 39), @cd_s, 1000.00, 971.98, -28.02, 0.00, -28.02, NULL, 0.0, 0.4378, 1032.01, 988.54, 'ELO-K32-V2', '2026-09-21 09:00:00'
       UNION ALL SELECT (@cd_sm + 26), (@cd_p + 260), (@cd_u + 40), @cd_s, 1000.00, 971.98, -28.02, 0.00, -28.02, NULL, 0.0, 0.4378, 1032.01, 988.54, 'ELO-K32-V2', '2026-09-21 09:00:00'
       UNION ALL SELECT (@cd_sm + 26), (@cd_p + 251), (@cd_u + 41), @cd_s, 980.90, 952.88, -28.02, 0.00, -28.02, NULL, 0.0, 0.4378, 1032.01, 988.54, 'ELO-K32-V2', '2026-09-21 09:00:00'
       UNION ALL SELECT (@cd_sm + 27), (@cd_p + 262), (@cd_u + 1), @cd_s, 1169.23, 1177.29, 8.06, 0.00, 8.06, NULL, 1.0, 0.7482, 975.85, 1165.05, 'ELO-K32-V2', '2026-09-23 22:00:00'
       UNION ALL SELECT (@cd_sm + 27), (@cd_p + 261), (@cd_u + 2), @cd_s, 1179.67, 1187.73, 8.06, 0.00, 8.06, NULL, 1.0, 0.7482, 975.85, 1165.05, 'ELO-K32-V2', '2026-09-23 22:00:00'
       UNION ALL SELECT (@cd_sm + 27), (@cd_p + 263), (@cd_u + 3), @cd_s, 1163.19, 1171.25, 8.06, 0.00, 8.06, NULL, 1.0, 0.7482, 975.85, 1165.05, 'ELO-K32-V2', '2026-09-23 22:00:00'
       UNION ALL SELECT (@cd_sm + 27), (@cd_p + 264), (@cd_u + 4), @cd_s, 1159.12, 1167.18, 8.06, 0.00, 8.06, NULL, 1.0, 0.7482, 975.85, 1165.05, 'ELO-K32-V2', '2026-09-23 22:00:00'
       UNION ALL SELECT (@cd_sm + 27), (@cd_p + 265), (@cd_u + 6), @cd_s, 1154.06, 1162.12, 8.06, 0.00, 8.06, NULL, 1.0, 0.7482, 975.85, 1165.05, 'ELO-K32-V2', '2026-09-23 22:00:00'
       UNION ALL SELECT (@cd_sm + 27), (@cd_p + 270), (@cd_u + 50), @cd_s, 957.30, 941.18, -16.12, 0.00, -16.12, NULL, 0.0, 0.2518, 975.85, 1165.05, 'ELO-K32-V2', '2026-09-23 22:00:00'
       UNION ALL SELECT (@cd_sm + 27), (@cd_p + 269), (@cd_u + 52), @cd_s, 996.80, 980.68, -16.12, 0.00, -16.12, NULL, 0.0, 0.2518, 975.85, 1165.05, 'ELO-K32-V2', '2026-09-23 22:00:00'
       UNION ALL SELECT (@cd_sm + 27), (@cd_p + 266), (@cd_u + 53), @cd_s, 996.10, 979.98, -16.12, 0.00, -16.12, NULL, 0.0, 0.2518, 975.85, 1165.05, 'ELO-K32-V2', '2026-09-23 22:00:00'
       UNION ALL SELECT (@cd_sm + 27), (@cd_p + 268), (@cd_u + 54), @cd_s, 932.26, 916.14, -16.12, 0.00, -16.12, NULL, 0.0, 0.2518, 975.85, 1165.05, 'ELO-K32-V2', '2026-09-23 22:00:00'
       UNION ALL SELECT (@cd_sm + 27), (@cd_p + 267), (@cd_u + 55), @cd_s, 996.80, 980.68, -16.12, 0.00, -16.12, NULL, 0.0, 0.2518, 975.85, 1165.05, 'ELO-K32-V2', '2026-09-23 22:00:00'
       UNION ALL SELECT (@cd_sm + 28), (@cd_p + 276), (@cd_u + 8), @cd_s, 1066.50, 1029.66, -36.84, 0.00, -36.84, NULL, 0.0, 0.5756, 1030.93, 978.00, 'ELO-K32-V2', '2026-09-27 09:00:00'
       UNION ALL SELECT (@cd_sm + 28), (@cd_p + 280), (@cd_u + 10), @cd_s, 1002.97, 966.13, -36.84, 0.00, -36.84, NULL, 0.0, 0.5756, 1030.93, 978.00, 'ELO-K32-V2', '2026-09-27 09:00:00'
       UNION ALL SELECT (@cd_sm + 28), (@cd_p + 277), (@cd_u + 11), @cd_s, 1052.97, 1016.13, -36.84, 0.00, -36.84, NULL, 0.0, 0.5756, 1030.93, 978.00, 'ELO-K32-V2', '2026-09-27 09:00:00'
       UNION ALL SELECT (@cd_sm + 28), (@cd_p + 278), (@cd_u + 12), @cd_s, 1029.22, 992.38, -36.84, 0.00, -36.84, NULL, 0.0, 0.5756, 1030.93, 978.00, 'ELO-K32-V2', '2026-09-27 09:00:00'
       UNION ALL SELECT (@cd_sm + 28), (@cd_p + 279), (@cd_u + 13), @cd_s, 1002.97, 966.13, -36.84, 0.00, -36.84, NULL, 0.0, 0.5756, 1030.93, 978.00, 'ELO-K32-V2', '2026-09-27 09:00:00'
       UNION ALL SELECT (@cd_sm + 28), (@cd_p + 272), (@cd_u + 67), @cd_s, 968.70, 987.12, 18.42, 0.00, 18.42, NULL, 1.0, 0.4244, 1030.93, 978.00, 'ELO-K32-V2', '2026-09-27 09:00:00'
       UNION ALL SELECT (@cd_sm + 28), (@cd_p + 275), (@cd_u + 69), @cd_s, 944.47, 981.31, 36.84, 0.00, 36.84, NULL, 1.0, 0.4244, 1030.93, 978.00, 'ELO-K32-V2', '2026-09-27 09:00:00'
       UNION ALL SELECT (@cd_sm + 28), (@cd_p + 271), (@cd_u + 71), @cd_s, 987.95, 1024.79, 36.84, 0.00, 36.84, NULL, 1.0, 0.4244, 1030.93, 978.00, 'ELO-K32-V2', '2026-09-27 09:00:00'
       UNION ALL SELECT (@cd_sm + 28), (@cd_p + 274), (@cd_u + 72), @cd_s, 1000.95, 1037.79, 36.84, 0.00, 36.84, NULL, 1.0, 0.4244, 1030.93, 978.00, 'ELO-K32-V2', '2026-09-27 09:00:00'
       UNION ALL SELECT (@cd_sm + 28), (@cd_p + 273), (@cd_u + 73), @cd_s, 987.95, 1024.79, 36.84, 0.00, 36.84, NULL, 1.0, 0.4244, 1030.93, 978.00, 'ELO-K32-V2', '2026-09-27 09:00:00'
       UNION ALL SELECT (@cd_sm + 29), (@cd_p + 283), (@cd_u + 43), @cd_s, 915.27, 886.28, -28.99, 0.00, -28.99, NULL, 0.0, 0.4530, 938.00, 970.75, 'ELO-K32-V2', '2026-09-27 16:00:00'
       UNION ALL SELECT (@cd_sm + 29), (@cd_p + 282), (@cd_u + 46), @cd_s, 921.86, 892.87, -28.99, 0.00, -28.99, NULL, 0.0, 0.4530, 938.00, 970.75, 'ELO-K32-V2', '2026-09-27 16:00:00'
       UNION ALL SELECT (@cd_sm + 29), (@cd_p + 284), (@cd_u + 47), @cd_s, 924.19, 895.20, -28.99, 0.00, -28.99, NULL, 0.0, 0.4530, 938.00, 970.75, 'ELO-K32-V2', '2026-09-27 16:00:00'
       UNION ALL SELECT (@cd_sm + 29), (@cd_p + 285), (@cd_u + 48), @cd_s, 928.66, 899.67, -28.99, 0.00, -28.99, NULL, 0.0, 0.4530, 938.00, 970.75, 'ELO-K32-V2', '2026-09-27 16:00:00'
       UNION ALL SELECT (@cd_sm + 29), (@cd_p + 281), (@cd_u + 49), @cd_s, 1000.00, 971.01, -28.99, 0.00, -28.99, NULL, 0.0, 0.4530, 938.00, 970.75, 'ELO-K32-V2', '2026-09-27 16:00:00'
       UNION ALL SELECT (@cd_sm + 29), (@cd_p + 290), (@cd_u + 57), @cd_s, 938.32, 952.82, 14.50, 0.00, 14.50, NULL, 1.0, 0.5470, 938.00, 970.75, 'ELO-K32-V2', '2026-09-27 16:00:00'
       UNION ALL SELECT (@cd_sm + 29), (@cd_p + 289), (@cd_u + 58), @cd_s, 976.48, 1005.47, 28.99, 0.00, 28.99, NULL, 1.0, 0.5470, 938.00, 970.75, 'ELO-K32-V2', '2026-09-27 16:00:00'
       UNION ALL SELECT (@cd_sm + 29), (@cd_p + 287), (@cd_u + 59), @cd_s, 976.48, 1005.47, 28.99, 0.00, 28.99, NULL, 1.0, 0.5470, 938.00, 970.75, 'ELO-K32-V2', '2026-09-27 16:00:00'
       UNION ALL SELECT (@cd_sm + 29), (@cd_p + 286), (@cd_u + 63), @cd_s, 979.38, 1008.37, 28.99, 0.00, 28.99, NULL, 1.0, 0.5470, 938.00, 970.75, 'ELO-K32-V2', '2026-09-27 16:00:00'
       UNION ALL SELECT (@cd_sm + 29), (@cd_p + 288), (@cd_u + 64), @cd_s, 983.11, 1012.10, 28.99, 0.00, 28.99, NULL, 1.0, 0.5470, 938.00, 970.75, 'ELO-K32-V2', '2026-09-27 16:00:00'
       UNION ALL SELECT (@cd_sm + 30), (@cd_p + 295), (@cd_u + 1), @cd_s, 1177.29, 1184.92, 7.63, 0.00, 7.63, NULL, 1.0, 0.7617, 1173.58, 971.73, 'ELO-K32-V2', '2026-09-27 21:00:00'
       UNION ALL SELECT (@cd_sm + 30), (@cd_p + 294), (@cd_u + 2), @cd_s, 1187.73, 1195.36, 7.63, 0.00, 7.63, NULL, 1.0, 0.7617, 1173.58, 971.73, 'ELO-K32-V2', '2026-09-27 21:00:00'
       UNION ALL SELECT (@cd_sm + 30), (@cd_p + 291), (@cd_u + 3), @cd_s, 1171.25, 1178.88, 7.63, 0.00, 7.63, NULL, 1.0, 0.7617, 1173.58, 971.73, 'ELO-K32-V2', '2026-09-27 21:00:00'
       UNION ALL SELECT (@cd_sm + 30), (@cd_p + 292), (@cd_u + 4), @cd_s, 1167.18, 1174.81, 7.63, 0.00, 7.63, NULL, 1.0, 0.7617, 1173.58, 971.73, 'ELO-K32-V2', '2026-09-27 21:00:00'
       UNION ALL SELECT (@cd_sm + 30), (@cd_p + 293), (@cd_u + 5), @cd_s, 1164.43, 1172.06, 7.63, 0.00, 7.63, NULL, 1.0, 0.7617, 1173.58, 971.73, 'ELO-K32-V2', '2026-09-27 21:00:00'
       UNION ALL SELECT (@cd_sm + 30), (@cd_p + 297), (@cd_u + 37), @cd_s, 952.88, 937.63, -15.25, 0.00, -15.25, NULL, 0.0, 0.2383, 1173.58, 971.73, 'ELO-K32-V2', '2026-09-27 21:00:00'
       UNION ALL SELECT (@cd_sm + 30), (@cd_p + 300), (@cd_u + 38), @cd_s, 980.90, 965.65, -15.25, 0.00, -15.25, NULL, 0.0, 0.2383, 1173.58, 971.73, 'ELO-K32-V2', '2026-09-27 21:00:00'
       UNION ALL SELECT (@cd_sm + 30), (@cd_p + 299), (@cd_u + 39), @cd_s, 971.98, 956.73, -15.25, 0.00, -15.25, NULL, 0.0, 0.2383, 1173.58, 971.73, 'ELO-K32-V2', '2026-09-27 21:00:00'
       UNION ALL SELECT (@cd_sm + 30), (@cd_p + 298), (@cd_u + 40), @cd_s, 971.98, 956.73, -15.25, 0.00, -15.25, NULL, 0.0, 0.2383, 1173.58, 971.73, 'ELO-K32-V2', '2026-09-27 21:00:00'
       UNION ALL SELECT (@cd_sm + 30), (@cd_p + 296), (@cd_u + 42), @cd_s, 980.90, 965.65, -15.25, 0.00, -15.25, NULL, 0.0, 0.2383, 1173.58, 971.73, 'ELO-K32-V2', '2026-09-27 21:00:00'
       UNION ALL SELECT (@cd_sm + 31), (@cd_p + 304), (@cd_u + 22), @cd_s, 1027.01, 1057.51, 30.50, 0.00, 30.50, NULL, 1.0, 0.5235, 975.12, 958.76, 'ELO-K32-V2', '2026-10-02 22:00:00'
       UNION ALL SELECT (@cd_sm + 31), (@cd_p + 305), (@cd_u + 23), @cd_s, 966.09, 996.59, 30.50, 0.00, 30.50, NULL, 1.0, 0.5235, 975.12, 958.76, 'ELO-K32-V2', '2026-10-02 22:00:00'
       UNION ALL SELECT (@cd_sm + 31), (@cd_p + 301), (@cd_u + 24), @cd_s, 951.48, 981.98, 30.50, 0.00, 30.50, NULL, 1.0, 0.5235, 975.12, 958.76, 'ELO-K32-V2', '2026-10-02 22:00:00'
       UNION ALL SELECT (@cd_sm + 31), (@cd_p + 302), (@cd_u + 26), @cd_s, 1010.81, 1041.31, 30.50, 0.00, 30.50, NULL, 1.0, 0.5235, 975.12, 958.76, 'ELO-K32-V2', '2026-10-02 22:00:00'
       UNION ALL SELECT (@cd_sm + 31), (@cd_p + 303), (@cd_u + 28), @cd_s, 920.20, 950.70, 30.50, 0.00, 30.50, NULL, 1.0, 0.5235, 975.12, 958.76, 'ELO-K32-V2', '2026-10-02 22:00:00'
       UNION ALL SELECT (@cd_sm + 31), (@cd_p + 310), (@cd_u + 36), @cd_s, 952.88, 922.38, -30.50, 0.00, -30.50, NULL, 0.0, 0.4765, 975.12, 958.76, 'ELO-K32-V2', '2026-10-02 22:00:00'
       UNION ALL SELECT (@cd_sm + 31), (@cd_p + 307), (@cd_u + 38), @cd_s, 965.65, 935.15, -30.50, 0.00, -30.50, NULL, 0.0, 0.4765, 975.12, 958.76, 'ELO-K32-V2', '2026-10-02 22:00:00'
       UNION ALL SELECT (@cd_sm + 31), (@cd_p + 309), (@cd_u + 39), @cd_s, 956.73, 926.23, -30.50, 0.00, -30.50, NULL, 0.0, 0.4765, 975.12, 958.76, 'ELO-K32-V2', '2026-10-02 22:00:00'
       UNION ALL SELECT (@cd_sm + 31), (@cd_p + 306), (@cd_u + 41), @cd_s, 952.88, 922.38, -30.50, 0.00, -30.50, NULL, 0.0, 0.4765, 975.12, 958.76, 'ELO-K32-V2', '2026-10-02 22:00:00'
       UNION ALL SELECT (@cd_sm + 31), (@cd_p + 308), (@cd_u + 42), @cd_s, 965.65, 935.15, -30.50, 0.00, -30.50, NULL, 0.0, 0.4765, 975.12, 958.76, 'ELO-K32-V2', '2026-10-02 22:00:00'
       UNION ALL SELECT (@cd_sm + 32), (@cd_p + 318), (@cd_u + 1), @cd_s, 1184.92, 1192.48, 7.56, 0.00, 7.56, NULL, 1.0, 0.7636, 975.00, 1178.67, 'ELO-K32-V2', '2026-10-03 22:00:00'
       UNION ALL SELECT (@cd_sm + 32), (@cd_p + 319), (@cd_u + 2), @cd_s, 1195.36, 1202.92, 7.56, 0.00, 7.56, NULL, 1.0, 0.7636, 975.00, 1178.67, 'ELO-K32-V2', '2026-10-03 22:00:00'
       UNION ALL SELECT (@cd_sm + 32), (@cd_p + 317), (@cd_u + 3), @cd_s, 1178.88, 1186.44, 7.56, 0.00, 7.56, NULL, 1.0, 0.7636, 975.00, 1178.67, 'ELO-K32-V2', '2026-10-03 22:00:00'
       UNION ALL SELECT (@cd_sm + 32), (@cd_p + 320), (@cd_u + 5), @cd_s, 1172.06, 1179.62, 7.56, 0.00, 7.56, NULL, 1.0, 0.7636, 975.00, 1178.67, 'ELO-K32-V2', '2026-10-03 22:00:00'
       UNION ALL SELECT (@cd_sm + 32), (@cd_p + 316), (@cd_u + 6), @cd_s, 1162.12, 1169.68, 7.56, 0.00, 7.56, NULL, 1.0, 0.7636, 975.00, 1178.67, 'ELO-K32-V2', '2026-10-03 22:00:00'
       UNION ALL SELECT (@cd_sm + 32), (@cd_p + 313), (@cd_u + 51), @cd_s, 932.26, 917.13, -15.13, 0.00, -15.13, NULL, 0.0, 0.2364, 975.00, 1178.67, 'ELO-K32-V2', '2026-10-03 22:00:00'
       UNION ALL SELECT (@cd_sm + 32), (@cd_p + 315), (@cd_u + 52), @cd_s, 980.68, 965.55, -15.13, 0.00, -15.13, NULL, 0.0, 0.2364, 975.00, 1178.67, 'ELO-K32-V2', '2026-10-03 22:00:00'
       UNION ALL SELECT (@cd_sm + 32), (@cd_p + 314), (@cd_u + 53), @cd_s, 979.98, 964.85, -15.13, 0.00, -15.13, NULL, 0.0, 0.2364, 975.00, 1178.67, 'ELO-K32-V2', '2026-10-03 22:00:00'
       UNION ALL SELECT (@cd_sm + 32), (@cd_p + 312), (@cd_u + 55), @cd_s, 980.68, 965.55, -15.13, 0.00, -15.13, NULL, 0.0, 0.2364, 975.00, 1178.67, 'ELO-K32-V2', '2026-10-03 22:00:00'
       UNION ALL SELECT (@cd_sm + 32), (@cd_p + 311), (@cd_u + 56), @cd_s, 1001.38, 986.25, -15.13, 0.00, -15.13, NULL, 0.0, 0.2364, 975.00, 1178.67, 'ELO-K32-V2', '2026-10-03 22:00:00'
       UNION ALL SELECT (@cd_sm + 33), (@cd_p + 324), (@cd_u + 30), @cd_s, 919.94, 890.68, -29.26, 0.00, -29.26, NULL, 0.0, 0.4572, 972.54, 1002.35, 'ELO-K32-V2', '2026-10-05 10:00:00'
       UNION ALL SELECT (@cd_sm + 33), (@cd_p + 323), (@cd_u + 31), @cd_s, 971.02, 941.76, -29.26, 0.00, -29.26, NULL, 0.0, 0.4572, 972.54, 1002.35, 'ELO-K32-V2', '2026-10-05 10:00:00'
       UNION ALL SELECT (@cd_sm + 33), (@cd_p + 321), (@cd_u + 32), @cd_s, 986.24, 956.98, -29.26, 0.00, -29.26, NULL, 0.0, 0.4572, 972.54, 1002.35, 'ELO-K32-V2', '2026-10-05 10:00:00'
       UNION ALL SELECT (@cd_sm + 33), (@cd_p + 322), (@cd_u + 33), @cd_s, 985.52, 956.26, -29.26, 0.00, -29.26, NULL, 0.0, 0.4572, 972.54, 1002.35, 'ELO-K32-V2', '2026-10-05 10:00:00'
       UNION ALL SELECT (@cd_sm + 33), (@cd_p + 325), (@cd_u + 35), @cd_s, 1000.00, 970.74, -29.26, 0.00, -29.26, NULL, 0.0, 0.4572, 972.54, 1002.35, 'ELO-K32-V2', '2026-10-05 10:00:00'
       UNION ALL SELECT (@cd_sm + 33), (@cd_p + 327), (@cd_u + 67), @cd_s, 987.12, 1001.75, 14.63, 0.00, 14.63, NULL, 1.0, 0.5428, 972.54, 1002.35, 'ELO-K32-V2', '2026-10-05 10:00:00'
       UNION ALL SELECT (@cd_sm + 33), (@cd_p + 330), (@cd_u + 69), @cd_s, 981.31, 1010.57, 29.26, 0.00, 29.26, NULL, 1.0, 0.5428, 972.54, 1002.35, 'ELO-K32-V2', '2026-10-05 10:00:00'
       UNION ALL SELECT (@cd_sm + 33), (@cd_p + 326), (@cd_u + 71), @cd_s, 1024.79, 1054.05, 29.26, 0.00, 29.26, NULL, 1.0, 0.5428, 972.54, 1002.35, 'ELO-K32-V2', '2026-10-05 10:00:00'
       UNION ALL SELECT (@cd_sm + 33), (@cd_p + 329), (@cd_u + 72), @cd_s, 1037.79, 1067.05, 29.26, 0.00, 29.26, NULL, 1.0, 0.5428, 972.54, 1002.35, 'ELO-K32-V2', '2026-10-05 10:00:00'
       UNION ALL SELECT (@cd_sm + 33), (@cd_p + 328), (@cd_u + 74), @cd_s, 980.75, 1010.01, 29.26, 0.00, 29.26, NULL, 1.0, 0.5428, 972.54, 1002.35, 'ELO-K32-V2', '2026-10-05 10:00:00'
       UNION ALL SELECT (@cd_sm + 34), (@cd_p + 338), (@cd_u + 2), @cd_s, 1202.92, 1178.36, -24.56, 0.00, -24.56, NULL, 0.0, 0.7676, 1182.69, 975.15, 'ELO-K32-V2', '2026-10-08 21:00:00'
       UNION ALL SELECT (@cd_sm + 34), (@cd_p + 339), (@cd_u + 3), @cd_s, 1186.44, 1161.88, -24.56, 0.00, -24.56, NULL, 0.0, 0.7676, 1182.69, 975.15, 'ELO-K32-V2', '2026-10-08 21:00:00'
       UNION ALL SELECT (@cd_sm + 34), (@cd_p + 340), (@cd_u + 4), @cd_s, 1174.81, 1150.25, -24.56, 0.00, -24.56, NULL, 0.0, 0.7676, 1182.69, 975.15, 'ELO-K32-V2', '2026-10-08 21:00:00'
       UNION ALL SELECT (@cd_sm + 34), (@cd_p + 336), (@cd_u + 5), @cd_s, 1179.62, 1155.06, -24.56, 0.00, -24.56, NULL, 0.0, 0.7676, 1182.69, 975.15, 'ELO-K32-V2', '2026-10-08 21:00:00'
       UNION ALL SELECT (@cd_sm + 34), (@cd_p + 337), (@cd_u + 6), @cd_s, 1169.68, 1145.12, -24.56, 0.00, -24.56, NULL, 0.0, 0.7676, 1182.69, 975.15, 'ELO-K32-V2', '2026-10-08 21:00:00'
       UNION ALL SELECT (@cd_sm + 34), (@cd_p + 335), (@cd_u + 57), @cd_s, 952.82, 977.38, 24.56, 0.00, 24.56, NULL, 1.0, 0.2324, 1182.69, 975.15, 'ELO-K32-V2', '2026-10-08 21:00:00'
       UNION ALL SELECT (@cd_sm + 34), (@cd_p + 332), (@cd_u + 60), @cd_s, 987.09, 1036.22, 49.13, 0.00, 49.13, NULL, 1.0, 0.2324, 1182.69, 975.15, 'ELO-K32-V2', '2026-10-08 21:00:00'
       UNION ALL SELECT (@cd_sm + 34), (@cd_p + 333), (@cd_u + 61), @cd_s, 980.21, 1029.34, 49.13, 0.00, 49.13, NULL, 1.0, 0.2324, 1182.69, 975.15, 'ELO-K32-V2', '2026-10-08 21:00:00'
       UNION ALL SELECT (@cd_sm + 34), (@cd_p + 334), (@cd_u + 62), @cd_s, 979.38, 1028.51, 49.13, 0.00, 49.13, NULL, 1.0, 0.2324, 1182.69, 975.15, 'ELO-K32-V2', '2026-10-08 21:00:00'
       UNION ALL SELECT (@cd_sm + 34), (@cd_p + 331), (@cd_u + 65), @cd_s, 976.23, 1025.36, 49.13, 0.00, 49.13, NULL, 1.0, 0.2324, 1182.69, 975.15, 'ELO-K32-V2', '2026-10-08 21:00:00'
       UNION ALL SELECT (@cd_sm + 35), (@cd_p + 341), (@cd_u + 15), @cd_s, 990.09, 1014.40, 24.31, 0.00, 24.31, NULL, 1.0, 0.6201, 1019.45, 934.36, 'ELO-K32-V2', '2026-10-10 22:00:00'
       UNION ALL SELECT (@cd_sm + 35), (@cd_p + 342), (@cd_u + 17), @cd_s, 992.51, 1016.82, 24.31, 0.00, 24.31, NULL, 1.0, 0.6201, 1019.45, 934.36, 'ELO-K32-V2', '2026-10-10 22:00:00'
       UNION ALL SELECT (@cd_sm + 35), (@cd_p + 345), (@cd_u + 18), @cd_s, 1054.54, 1078.85, 24.31, 0.00, 24.31, NULL, 1.0, 0.6201, 1019.45, 934.36, 'ELO-K32-V2', '2026-10-10 22:00:00'
       UNION ALL SELECT (@cd_sm + 35), (@cd_p + 343), (@cd_u + 19), @cd_s, 1066.84, 1091.15, 24.31, 0.00, 24.31, NULL, 1.0, 0.6201, 1019.45, 934.36, 'ELO-K32-V2', '2026-10-10 22:00:00'
       UNION ALL SELECT (@cd_sm + 35), (@cd_p + 344), (@cd_u + 20), @cd_s, 993.26, 1017.57, 24.31, 0.00, 24.31, NULL, 1.0, 0.6201, 1019.45, 934.36, 'ELO-K32-V2', '2026-10-10 22:00:00'
       UNION ALL SELECT (@cd_sm + 35), (@cd_p + 350), (@cd_u + 36), @cd_s, 922.38, 898.07, -24.31, 0.00, -24.31, NULL, 0.0, 0.3799, 1019.45, 934.36, 'ELO-K32-V2', '2026-10-10 22:00:00'
       UNION ALL SELECT (@cd_sm + 35), (@cd_p + 349), (@cd_u + 38), @cd_s, 935.15, 910.84, -24.31, 0.00, -24.31, NULL, 0.0, 0.3799, 1019.45, 934.36, 'ELO-K32-V2', '2026-10-10 22:00:00'
       UNION ALL SELECT (@cd_sm + 35), (@cd_p + 347), (@cd_u + 40), @cd_s, 956.73, 932.42, -24.31, 0.00, -24.31, NULL, 0.0, 0.3799, 1019.45, 934.36, 'ELO-K32-V2', '2026-10-10 22:00:00'
       UNION ALL SELECT (@cd_sm + 35), (@cd_p + 348), (@cd_u + 41), @cd_s, 922.38, 898.07, -24.31, 0.00, -24.31, NULL, 0.0, 0.3799, 1019.45, 934.36, 'ELO-K32-V2', '2026-10-10 22:00:00'
       UNION ALL SELECT (@cd_sm + 35), (@cd_p + 346), (@cd_u + 42), @cd_s, 935.15, 910.84, -24.31, 0.00, -24.31, NULL, 0.0, 0.3799, 1019.45, 934.36, 'ELO-K32-V2', '2026-10-10 22:00:00'
       UNION ALL SELECT (@cd_sm + 36), (@cd_p + 359), (@cd_u + 1), @cd_s, 1192.48, 1202.19, 9.71, 0.00, 9.71, NULL, 1.0, 0.6965, 1019.97, 1164.25, 'ELO-K32-V2', '2026-10-11 09:00:00'
       UNION ALL SELECT (@cd_sm + 36), (@cd_p + 357), (@cd_u + 2), @cd_s, 1178.36, 1188.07, 9.71, 0.00, 9.71, NULL, 1.0, 0.6965, 1019.97, 1164.25, 'ELO-K32-V2', '2026-10-11 09:00:00'
       UNION ALL SELECT (@cd_sm + 36), (@cd_p + 360), (@cd_u + 4), @cd_s, 1150.25, 1159.96, 9.71, 0.00, 9.71, NULL, 1.0, 0.6965, 1019.97, 1164.25, 'ELO-K32-V2', '2026-10-11 09:00:00'
       UNION ALL SELECT (@cd_sm + 36), (@cd_p + 358), (@cd_u + 5), @cd_s, 1155.06, 1164.77, 9.71, 0.00, 9.71, NULL, 1.0, 0.6965, 1019.97, 1164.25, 'ELO-K32-V2', '2026-10-11 09:00:00'
       UNION ALL SELECT (@cd_sm + 36), (@cd_p + 356), (@cd_u + 6), @cd_s, 1145.12, 1154.83, 9.71, 0.00, 9.71, NULL, 1.0, 0.6965, 1019.97, 1164.25, 'ELO-K32-V2', '2026-10-11 09:00:00'
       UNION ALL SELECT (@cd_sm + 36), (@cd_p + 351), (@cd_u + 9), @cd_s, 1066.50, 1047.08, -19.42, 0.00, -19.42, NULL, 0.0, 0.3035, 1019.97, 1164.25, 'ELO-K32-V2', '2026-10-11 09:00:00'
       UNION ALL SELECT (@cd_sm + 36), (@cd_p + 355), (@cd_u + 10), @cd_s, 966.13, 946.71, -19.42, 0.00, -19.42, NULL, 0.0, 0.3035, 1019.97, 1164.25, 'ELO-K32-V2', '2026-10-11 09:00:00'
       UNION ALL SELECT (@cd_sm + 36), (@cd_p + 353), (@cd_u + 11), @cd_s, 1016.13, 996.71, -19.42, 0.00, -19.42, NULL, 0.0, 0.3035, 1019.97, 1164.25, 'ELO-K32-V2', '2026-10-11 09:00:00'
       UNION ALL SELECT (@cd_sm + 36), (@cd_p + 354), (@cd_u + 13), @cd_s, 966.13, 946.71, -19.42, 0.00, -19.42, NULL, 0.0, 0.3035, 1019.97, 1164.25, 'ELO-K32-V2', '2026-10-11 09:00:00'
       UNION ALL SELECT (@cd_sm + 36), (@cd_p + 352), (@cd_u + 14), @cd_s, 1084.97, 1065.55, -19.42, 0.00, -19.42, NULL, 0.0, 0.3035, 1019.97, 1164.25, 'ELO-K32-V2', '2026-10-11 09:00:00'
       UNION ALL SELECT (@cd_sm + 37), (@cd_p + 368), (@cd_u + 29), @cd_s, 954.24, 918.12, -36.12, 0.00, -36.12, NULL, 0.0, 0.5643, 911.08, 956.00, 'ELO-K32-V2', '2026-10-11 16:00:00'
       UNION ALL SELECT (@cd_sm + 37), (@cd_p + 367), (@cd_u + 31), @cd_s, 941.76, 905.64, -36.12, 0.00, -36.12, NULL, 0.0, 0.5643, 911.08, 956.00, 'ELO-K32-V2', '2026-10-11 16:00:00'
       UNION ALL SELECT (@cd_sm + 37), (@cd_p + 369), (@cd_u + 32), @cd_s, 956.98, 920.86, -36.12, 0.00, -36.12, NULL, 0.0, 0.5643, 911.08, 956.00, 'ELO-K32-V2', '2026-10-11 16:00:00'
       UNION ALL SELECT (@cd_sm + 37), (@cd_p + 366), (@cd_u + 33), @cd_s, 956.26, 920.14, -36.12, 0.00, -36.12, NULL, 0.0, 0.5643, 911.08, 956.00, 'ELO-K32-V2', '2026-10-11 16:00:00'
       UNION ALL SELECT (@cd_sm + 37), (@cd_p + 370), (@cd_u + 35), @cd_s, 970.74, 934.62, -36.12, 0.00, -36.12, NULL, 0.0, 0.5643, 911.08, 956.00, 'ELO-K32-V2', '2026-10-11 16:00:00'
       UNION ALL SELECT (@cd_sm + 37), (@cd_p + 364), (@cd_u + 44), @cd_s, 896.66, 932.78, 36.12, 0.00, 36.12, NULL, 1.0, 0.4357, 911.08, 956.00, 'ELO-K32-V2', '2026-10-11 16:00:00'
       UNION ALL SELECT (@cd_sm + 37), (@cd_p + 363), (@cd_u + 46), @cd_s, 892.87, 928.99, 36.12, 0.00, 36.12, NULL, 1.0, 0.4357, 911.08, 956.00, 'ELO-K32-V2', '2026-10-11 16:00:00'
       UNION ALL SELECT (@cd_sm + 37), (@cd_p + 365), (@cd_u + 47), @cd_s, 895.20, 931.32, 36.12, 0.00, 36.12, NULL, 1.0, 0.4357, 911.08, 956.00, 'ELO-K32-V2', '2026-10-11 16:00:00'
       UNION ALL SELECT (@cd_sm + 37), (@cd_p + 362), (@cd_u + 48), @cd_s, 899.67, 935.79, 36.12, 0.00, 36.12, NULL, 1.0, 0.4357, 911.08, 956.00, 'ELO-K32-V2', '2026-10-11 16:00:00'
       UNION ALL SELECT (@cd_sm + 37), (@cd_p + 361), (@cd_u + 49), @cd_s, 971.01, 1007.13, 36.12, 0.00, 36.12, NULL, 1.0, 0.4357, 911.08, 956.00, 'ELO-K32-V2', '2026-10-11 16:00:00'
       UNION ALL SELECT (@cd_sm + 38), (@cd_p + 378), (@cd_u + 30), @cd_s, 890.68, 924.89, 34.21, 0.00, 34.21, NULL, 1.0, 0.4654, 945.11, 921.06, 'ELO-K32-V2', '2026-10-14 22:00:00'
       UNION ALL SELECT (@cd_sm + 38), (@cd_p + 379), (@cd_u + 31), @cd_s, 905.64, 922.75, 17.11, 0.00, 17.11, NULL, 1.0, 0.4654, 945.11, 921.06, 'ELO-K32-V2', '2026-10-14 22:00:00'
       UNION ALL SELECT (@cd_sm + 38), (@cd_p + 377), (@cd_u + 33), @cd_s, 920.14, 937.25, 17.11, 0.00, 17.11, NULL, 1.0, 0.4654, 945.11, 921.06, 'ELO-K32-V2', '2026-10-14 22:00:00'
       UNION ALL SELECT (@cd_sm + 38), (@cd_p + 376), (@cd_u + 34), @cd_s, 954.24, 988.45, 34.21, 0.00, 34.21, NULL, 1.0, 0.4654, 945.11, 921.06, 'ELO-K32-V2', '2026-10-14 22:00:00'
       UNION ALL SELECT (@cd_sm + 38), (@cd_p + 380), (@cd_u + 35), @cd_s, 934.62, 968.83, 34.21, 0.00, 34.21, NULL, 1.0, 0.4654, 945.11, 921.06, 'ELO-K32-V2', '2026-10-14 22:00:00'
       UNION ALL SELECT (@cd_sm + 38), (@cd_p + 373), (@cd_u + 50), @cd_s, 941.18, 906.97, -34.21, 0.00, -34.21, NULL, 0.0, 0.5346, 945.11, 921.06, 'ELO-K32-V2', '2026-10-14 22:00:00'
       UNION ALL SELECT (@cd_sm + 38), (@cd_p + 372), (@cd_u + 51), @cd_s, 917.13, 882.92, -34.21, 0.00, -34.21, NULL, 0.0, 0.5346, 945.11, 921.06, 'ELO-K32-V2', '2026-10-14 22:00:00'
       UNION ALL SELECT (@cd_sm + 38), (@cd_p + 374), (@cd_u + 53), @cd_s, 964.85, 930.64, -34.21, 0.00, -34.21, NULL, 0.0, 0.5346, 945.11, 921.06, 'ELO-K32-V2', '2026-10-14 22:00:00'
       UNION ALL SELECT (@cd_sm + 38), (@cd_p + 375), (@cd_u + 54), @cd_s, 916.14, 881.93, -34.21, 0.00, -34.21, NULL, 0.0, 0.5346, 945.11, 921.06, 'ELO-K32-V2', '2026-10-14 22:00:00'
       UNION ALL SELECT (@cd_sm + 38), (@cd_p + 371), (@cd_u + 56), @cd_s, 986.25, 952.04, -34.21, 0.00, -34.21, NULL, 0.0, 0.5346, 945.11, 921.06, 'ELO-K32-V2', '2026-10-14 22:00:00'
       UNION ALL SELECT (@cd_sm + 39), (@cd_p + 385), (@cd_u + 1), @cd_s, 1202.19, 1211.94, 9.75, 0.00, 9.75, NULL, 1.0, 0.6952, 1168.73, 1025.53, 'ELO-K32-V2', '2026-10-15 21:00:00'
       UNION ALL SELECT (@cd_sm + 39), (@cd_p + 381), (@cd_u + 3), @cd_s, 1161.88, 1171.63, 9.75, 0.00, 9.75, NULL, 1.0, 0.6952, 1168.73, 1025.53, 'ELO-K32-V2', '2026-10-15 21:00:00'
       UNION ALL SELECT (@cd_sm + 39), (@cd_p + 383), (@cd_u + 4), @cd_s, 1159.96, 1169.71, 9.75, 0.00, 9.75, NULL, 1.0, 0.6952, 1168.73, 1025.53, 'ELO-K32-V2', '2026-10-15 21:00:00'
       UNION ALL SELECT (@cd_sm + 39), (@cd_p + 384), (@cd_u + 5), @cd_s, 1164.77, 1174.52, 9.75, 0.00, 9.75, NULL, 1.0, 0.6952, 1168.73, 1025.53, 'ELO-K32-V2', '2026-10-15 21:00:00'
       UNION ALL SELECT (@cd_sm + 39), (@cd_p + 382), (@cd_u + 6), @cd_s, 1154.83, 1164.58, 9.75, 0.00, 9.75, NULL, 1.0, 0.6952, 1168.73, 1025.53, 'ELO-K32-V2', '2026-10-15 21:00:00'
       UNION ALL SELECT (@cd_sm + 39), (@cd_p + 387), (@cd_u + 15), @cd_s, 1014.40, 1004.65, -9.75, 0.00, -9.75, NULL, 0.0, 0.3048, 1168.73, 1025.53, 'ELO-K32-V2', '2026-10-15 21:00:00'
       UNION ALL SELECT (@cd_sm + 39), (@cd_p + 388), (@cd_u + 17), @cd_s, 1016.82, 1007.07, -9.75, 0.00, -9.75, NULL, 0.0, 0.3048, 1168.73, 1025.53, 'ELO-K32-V2', '2026-10-15 21:00:00'
       UNION ALL SELECT (@cd_sm + 39), (@cd_p + 386), (@cd_u + 18), @cd_s, 1078.85, 1069.10, -9.75, 0.00, -9.75, NULL, 0.0, 0.3048, 1168.73, 1025.53, 'ELO-K32-V2', '2026-10-15 21:00:00'
       UNION ALL SELECT (@cd_sm + 39), (@cd_p + 390), (@cd_u + 20), @cd_s, 1017.57, 1007.82, -9.75, 0.00, -9.75, NULL, 0.0, 0.3048, 1168.73, 1025.53, 'ELO-K32-V2', '2026-10-15 21:00:00'
       UNION ALL SELECT (@cd_sm + 39), (@cd_p + 389), (@cd_u + 21), @cd_s, 1000.00, 980.49, -19.51, 0.00, -19.51, NULL, 0.0, 0.3048, 1168.73, 1025.53, 'ELO-K32-V2', '2026-10-15 21:00:00'
       UNION ALL SELECT (@cd_sm + 40), (@cd_p + 394), (@cd_u + 22), @cd_s, 1057.51, 1026.18, -31.33, 0.00, -31.33, NULL, 0.0, 0.4896, 1004.85, 1012.10, 'ELO-K32-V2', '2026-10-16 22:00:00'
       UNION ALL SELECT (@cd_sm + 40), (@cd_p + 393), (@cd_u + 23), @cd_s, 996.59, 965.26, -31.33, 0.00, -31.33, NULL, 0.0, 0.4896, 1004.85, 1012.10, 'ELO-K32-V2', '2026-10-16 22:00:00'
       UNION ALL SELECT (@cd_sm + 40), (@cd_p + 392), (@cd_u + 25), @cd_s, 1010.81, 979.48, -31.33, 0.00, -31.33, NULL, 0.0, 0.4896, 1004.85, 1012.10, 'ELO-K32-V2', '2026-10-16 22:00:00'
       UNION ALL SELECT (@cd_sm + 40), (@cd_p + 395), (@cd_u + 27), @cd_s, 1008.65, 977.32, -31.33, 0.00, -31.33, NULL, 0.0, 0.4896, 1004.85, 1012.10, 'ELO-K32-V2', '2026-10-16 22:00:00'
       UNION ALL SELECT (@cd_sm + 40), (@cd_p + 391), (@cd_u + 28), @cd_s, 950.70, 919.37, -31.33, 0.00, -31.33, NULL, 0.0, 0.4896, 1004.85, 1012.10, 'ELO-K32-V2', '2026-10-16 22:00:00'
       UNION ALL SELECT (@cd_sm + 40), (@cd_p + 398), (@cd_u + 57), @cd_s, 977.38, 993.05, 15.67, 0.00, 15.67, NULL, 1.0, 0.5104, 1004.85, 1012.10, 'ELO-K32-V2', '2026-10-16 22:00:00'
       UNION ALL SELECT (@cd_sm + 40), (@cd_p + 400), (@cd_u + 58), @cd_s, 1005.47, 1036.80, 31.33, 0.00, 31.33, NULL, 1.0, 0.5104, 1004.85, 1012.10, 'ELO-K32-V2', '2026-10-16 22:00:00'
       UNION ALL SELECT (@cd_sm + 40), (@cd_p + 397), (@cd_u + 60), @cd_s, 1036.22, 1067.55, 31.33, 0.00, 31.33, NULL, 1.0, 0.5104, 1004.85, 1012.10, 'ELO-K32-V2', '2026-10-16 22:00:00'
       UNION ALL SELECT (@cd_sm + 40), (@cd_p + 399), (@cd_u + 61), @cd_s, 1029.34, 1060.67, 31.33, 0.00, 31.33, NULL, 1.0, 0.5104, 1004.85, 1012.10, 'ELO-K32-V2', '2026-10-16 22:00:00'
       UNION ALL SELECT (@cd_sm + 40), (@cd_p + 396), (@cd_u + 64), @cd_s, 1012.10, 1043.43, 31.33, 0.00, 31.33, NULL, 1.0, 0.5104, 1004.85, 1012.10, 'ELO-K32-V2', '2026-10-16 22:00:00'
       UNION ALL SELECT (@cd_sm + 41), (@cd_p + 408), (@cd_u + 43), @cd_s, 886.28, 860.55, -25.73, 0.00, -25.73, NULL, 0.0, 0.4020, 930.83, 999.85, 'ELO-K32-V2', '2026-10-18 16:00:00'
       UNION ALL SELECT (@cd_sm + 41), (@cd_p + 406), (@cd_u + 44), @cd_s, 932.78, 919.92, -12.86, 0.00, -12.86, NULL, 0.0, 0.4020, 930.83, 999.85, 'ELO-K32-V2', '2026-10-18 16:00:00'
       UNION ALL SELECT (@cd_sm + 41), (@cd_p + 410), (@cd_u + 45), @cd_s, 896.66, 870.93, -25.73, 0.00, -25.73, NULL, 0.0, 0.4020, 930.83, 999.85, 'ELO-K32-V2', '2026-10-18 16:00:00'
       UNION ALL SELECT (@cd_sm + 41), (@cd_p + 409), (@cd_u + 47), @cd_s, 931.32, 918.46, -12.86, 0.00, -12.86, NULL, 0.0, 0.4020, 930.83, 999.85, 'ELO-K32-V2', '2026-10-18 16:00:00'
       UNION ALL SELECT (@cd_sm + 41), (@cd_p + 407), (@cd_u + 49), @cd_s, 1007.13, 981.40, -25.73, 0.00, -25.73, NULL, 0.0, 0.4020, 930.83, 999.85, 'ELO-K32-V2', '2026-10-18 16:00:00'
       UNION ALL SELECT (@cd_sm + 41), (@cd_p + 402), (@cd_u + 67), @cd_s, 1001.75, 1014.61, 12.86, 0.00, 12.86, NULL, 1.0, 0.5980, 930.83, 999.85, 'ELO-K32-V2', '2026-10-18 16:00:00'
       UNION ALL SELECT (@cd_sm + 41), (@cd_p + 404), (@cd_u + 68), @cd_s, 967.75, 993.48, 25.73, 0.00, 25.73, NULL, 1.0, 0.5980, 930.83, 999.85, 'ELO-K32-V2', '2026-10-18 16:00:00'
       UNION ALL SELECT (@cd_sm + 41), (@cd_p + 403), (@cd_u + 70), @cd_s, 1024.23, 1049.96, 25.73, 0.00, 25.73, NULL, 1.0, 0.5980, 930.83, 999.85, 'ELO-K32-V2', '2026-10-18 16:00:00'
       UNION ALL SELECT (@cd_sm + 41), (@cd_p + 401), (@cd_u + 73), @cd_s, 1024.79, 1050.52, 25.73, 0.00, 25.73, NULL, 1.0, 0.5980, 930.83, 999.85, 'ELO-K32-V2', '2026-10-18 16:00:00'
       UNION ALL SELECT (@cd_sm + 41), (@cd_p + 405), (@cd_u + 75), @cd_s, 980.75, 1006.48, 25.73, 0.00, 25.73, NULL, 1.0, 0.5980, 930.83, 999.85, 'ELO-K32-V2', '2026-10-18 16:00:00'
       UNION ALL SELECT (@cd_sm + 42), (@cd_p + 412), (@cd_u + 3), @cd_s, 1171.63, 1180.88, 9.25, 0.00, 9.25, NULL, 1.0, 0.7108, 979.89, 1136.09, 'ELO-K32-V2', '2026-10-23 22:00:00'
       UNION ALL SELECT (@cd_sm + 42), (@cd_p + 411), (@cd_u + 4), @cd_s, 1169.71, 1178.96, 9.25, 0.00, 9.25, NULL, 1.0, 0.7108, 979.89, 1136.09, 'ELO-K32-V2', '2026-10-23 22:00:00'
       UNION ALL SELECT (@cd_sm + 42), (@cd_p + 413), (@cd_u + 5), @cd_s, 1174.52, 1183.77, 9.25, 0.00, 9.25, NULL, 1.0, 0.7108, 979.89, 1136.09, 'ELO-K32-V2', '2026-10-23 22:00:00'
       UNION ALL SELECT (@cd_sm + 42), (@cd_p + 414), (@cd_u + 6), @cd_s, 1164.58, 1173.83, 9.25, 0.00, 9.25, NULL, 1.0, 0.7108, 979.89, 1136.09, 'ELO-K32-V2', '2026-10-23 22:00:00'
       UNION ALL SELECT (@cd_sm + 42), (@cd_p + 415), (@cd_u + 7), @cd_s, 1000.00, 1018.51, 18.51, 0.00, 18.51, NULL, 1.0, 0.7108, 979.89, 1136.09, 'ELO-K32-V2', '2026-10-23 22:00:00'
       UNION ALL SELECT (@cd_sm + 42), (@cd_p + 416), (@cd_u + 24), @cd_s, 981.98, 972.73, -9.25, 0.00, -9.25, NULL, 0.0, 0.2892, 979.89, 1136.09, 'ELO-K32-V2', '2026-10-23 22:00:00'
       UNION ALL SELECT (@cd_sm + 42), (@cd_p + 417), (@cd_u + 25), @cd_s, 979.48, 970.23, -9.25, 0.00, -9.25, NULL, 0.0, 0.2892, 979.89, 1136.09, 'ELO-K32-V2', '2026-10-23 22:00:00'
       UNION ALL SELECT (@cd_sm + 42), (@cd_p + 420), (@cd_u + 26), @cd_s, 1041.31, 1032.06, -9.25, 0.00, -9.25, NULL, 0.0, 0.2892, 979.89, 1136.09, 'ELO-K32-V2', '2026-10-23 22:00:00'
       UNION ALL SELECT (@cd_sm + 42), (@cd_p + 419), (@cd_u + 27), @cd_s, 977.32, 968.07, -9.25, 0.00, -9.25, NULL, 0.0, 0.2892, 979.89, 1136.09, 'ELO-K32-V2', '2026-10-23 22:00:00'
       UNION ALL SELECT (@cd_sm + 42), (@cd_p + 418), (@cd_u + 28), @cd_s, 919.37, 910.12, -9.25, 0.00, -9.25, NULL, 0.0, 0.2892, 979.89, 1136.09, 'ELO-K32-V2', '2026-10-23 22:00:00'
       UNION ALL SELECT (@cd_sm + 43), (@cd_p + 425), (@cd_u + 15), @cd_s, 1004.65, 984.14, -20.51, 0.00, -20.51, NULL, 0.0, 0.6409, 1021.68, 921.04, 'ELO-K32-V2', '2026-10-24 22:00:00'
       UNION ALL SELECT (@cd_sm + 43), (@cd_p + 423), (@cd_u + 16), @cd_s, 1024.31, 1003.80, -20.51, 0.00, -20.51, NULL, 0.0, 0.6409, 1021.68, 921.04, 'ELO-K32-V2', '2026-10-24 22:00:00'
       UNION ALL SELECT (@cd_sm + 43), (@cd_p + 421), (@cd_u + 19), @cd_s, 1091.15, 1070.64, -20.51, 0.00, -20.51, NULL, 0.0, 0.6409, 1021.68, 921.04, 'ELO-K32-V2', '2026-10-24 22:00:00'
       UNION ALL SELECT (@cd_sm + 43), (@cd_p + 422), (@cd_u + 20), @cd_s, 1007.82, 987.31, -20.51, 0.00, -20.51, NULL, 0.0, 0.6409, 1021.68, 921.04, 'ELO-K32-V2', '2026-10-24 22:00:00'
       UNION ALL SELECT (@cd_sm + 43), (@cd_p + 424), (@cd_u + 21), @cd_s, 980.49, 939.47, -41.02, 0.00, -41.02, NULL, 0.0, 0.6409, 1021.68, 921.04, 'ELO-K32-V2', '2026-10-24 22:00:00'
       UNION ALL SELECT (@cd_sm + 43), (@cd_p + 427), (@cd_u + 37), @cd_s, 937.63, 978.65, 41.02, 0.00, 41.02, NULL, 1.0, 0.3591, 1021.68, 921.04, 'ELO-K32-V2', '2026-10-24 22:00:00'
       UNION ALL SELECT (@cd_sm + 43), (@cd_p + 430), (@cd_u + 39), @cd_s, 926.23, 967.25, 41.02, 0.00, 41.02, NULL, 1.0, 0.3591, 1021.68, 921.04, 'ELO-K32-V2', '2026-10-24 22:00:00'
       UNION ALL SELECT (@cd_sm + 43), (@cd_p + 429), (@cd_u + 40), @cd_s, 932.42, 973.44, 41.02, 0.00, 41.02, NULL, 1.0, 0.3591, 1021.68, 921.04, 'ELO-K32-V2', '2026-10-24 22:00:00'
       UNION ALL SELECT (@cd_sm + 43), (@cd_p + 428), (@cd_u + 41), @cd_s, 898.07, 939.09, 41.02, 0.00, 41.02, NULL, 1.0, 0.3591, 1021.68, 921.04, 'ELO-K32-V2', '2026-10-24 22:00:00'
       UNION ALL SELECT (@cd_sm + 43), (@cd_p + 426), (@cd_u + 42), @cd_s, 910.84, 951.86, 41.02, 0.00, 41.02, NULL, 1.0, 0.3591, 1021.68, 921.04, 'ELO-K32-V2', '2026-10-24 22:00:00'
       UNION ALL SELECT (@cd_sm + 44), (@cd_p + 435), (@cd_u + 43), @cd_s, 860.55, 848.67, -11.88, 0.00, -11.88, NULL, 0.0, 0.3712, 915.53, 1007.08, 'ELO-K32-V2', '2026-10-25 16:00:00'
       UNION ALL SELECT (@cd_sm + 44), (@cd_p + 431), (@cd_u + 45), @cd_s, 870.93, 859.05, -11.88, 0.00, -11.88, NULL, 0.0, 0.3712, 915.53, 1007.08, 'ELO-K32-V2', '2026-10-25 16:00:00'
       UNION ALL SELECT (@cd_sm + 44), (@cd_p + 434), (@cd_u + 46), @cd_s, 928.99, 917.11, -11.88, 0.00, -11.88, NULL, 0.0, 0.3712, 915.53, 1007.08, 'ELO-K32-V2', '2026-10-25 16:00:00'
       UNION ALL SELECT (@cd_sm + 44), (@cd_p + 433), (@cd_u + 48), @cd_s, 935.79, 923.91, -11.88, 0.00, -11.88, NULL, 0.0, 0.3712, 915.53, 1007.08, 'ELO-K32-V2', '2026-10-25 16:00:00'
       UNION ALL SELECT (@cd_sm + 44), (@cd_p + 432), (@cd_u + 49), @cd_s, 981.40, 957.64, -23.76, 0.00, -23.76, NULL, 0.0, 0.3712, 915.53, 1007.08, 'ELO-K32-V2', '2026-10-25 16:00:00'
       UNION ALL SELECT (@cd_sm + 44), (@cd_p + 439), (@cd_u + 57), @cd_s, 993.05, 1004.93, 11.88, 0.00, 11.88, NULL, 1.0, 0.6288, 915.53, 1007.08, 'ELO-K32-V2', '2026-10-25 16:00:00'
       UNION ALL SELECT (@cd_sm + 44), (@cd_p + 436), (@cd_u + 59), @cd_s, 1005.47, 1029.23, 23.76, 0.00, 23.76, NULL, 1.0, 0.6288, 915.53, 1007.08, 'ELO-K32-V2', '2026-10-25 16:00:00'
       UNION ALL SELECT (@cd_sm + 44), (@cd_p + 438), (@cd_u + 62), @cd_s, 1028.51, 1052.27, 23.76, 0.00, 23.76, NULL, 1.0, 0.6288, 915.53, 1007.08, 'ELO-K32-V2', '2026-10-25 16:00:00'
       UNION ALL SELECT (@cd_sm + 44), (@cd_p + 437), (@cd_u + 63), @cd_s, 1008.37, 1032.13, 23.76, 0.00, 23.76, NULL, 1.0, 0.6288, 915.53, 1007.08, 'ELO-K32-V2', '2026-10-25 16:00:00'
       UNION ALL SELECT (@cd_sm + 44), (@cd_p + 440), (@cd_u + 66), @cd_s, 1000.00, 1023.76, 23.76, 0.00, 23.76, NULL, 1.0, 0.6288, 915.53, 1007.08, 'ELO-K32-V2', '2026-10-25 16:00:00'
       UNION ALL SELECT (@cd_sm + 45), (@cd_p + 448), (@cd_u + 8), @cd_s, 1029.66, 1047.22, 17.56, 0.00, 17.56, NULL, 1.0, 0.4513, 992.51, 1026.43, 'ELO-K32-V2', '2026-10-25 09:00:00'
       UNION ALL SELECT (@cd_sm + 45), (@cd_p + 446), (@cd_u + 9), @cd_s, 1047.08, 1064.64, 17.56, 0.00, 17.56, NULL, 1.0, 0.4513, 992.51, 1026.43, 'ELO-K32-V2', '2026-10-25 09:00:00'
       UNION ALL SELECT (@cd_sm + 45), (@cd_p + 450), (@cd_u + 10), @cd_s, 946.71, 964.27, 17.56, 0.00, 17.56, NULL, 1.0, 0.4513, 992.51, 1026.43, 'ELO-K32-V2', '2026-10-25 09:00:00'
       UNION ALL SELECT (@cd_sm + 45), (@cd_p + 449), (@cd_u + 12), @cd_s, 992.38, 1009.94, 17.56, 0.00, 17.56, NULL, 1.0, 0.4513, 992.51, 1026.43, 'ELO-K32-V2', '2026-10-25 09:00:00'
       UNION ALL SELECT (@cd_sm + 45), (@cd_p + 447), (@cd_u + 13), @cd_s, 946.71, 964.27, 17.56, 0.00, 17.56, NULL, 1.0, 0.4513, 992.51, 1026.43, 'ELO-K32-V2', '2026-10-25 09:00:00'
       UNION ALL SELECT (@cd_sm + 45), (@cd_p + 441), (@cd_u + 67), @cd_s, 1014.61, 997.05, -17.56, 0.00, -17.56, NULL, 0.0, 0.5487, 992.51, 1026.43, 'ELO-K32-V2', '2026-10-25 09:00:00'
       UNION ALL SELECT (@cd_sm + 45), (@cd_p + 443), (@cd_u + 69), @cd_s, 1010.57, 975.45, -35.12, 0.00, -35.12, NULL, 0.0, 0.5487, 992.51, 1026.43, 'ELO-K32-V2', '2026-10-25 09:00:00'
       UNION ALL SELECT (@cd_sm + 45), (@cd_p + 444), (@cd_u + 70), @cd_s, 1049.96, 1014.84, -35.12, 0.00, -35.12, NULL, 0.0, 0.5487, 992.51, 1026.43, 'ELO-K32-V2', '2026-10-25 09:00:00'
       UNION ALL SELECT (@cd_sm + 45), (@cd_p + 442), (@cd_u + 73), @cd_s, 1050.52, 1015.40, -35.12, 0.00, -35.12, NULL, 0.0, 0.5487, 992.51, 1026.43, 'ELO-K32-V2', '2026-10-25 09:00:00'
       UNION ALL SELECT (@cd_sm + 45), (@cd_p + 445), (@cd_u + 75), @cd_s, 1006.48, 971.36, -35.12, 0.00, -35.12, NULL, 0.0, 0.5487, 992.51, 1026.43, 'ELO-K32-V2', '2026-10-25 09:00:00'
       UNION ALL SELECT (@cd_sm + 46), (@cd_p + 459), (@cd_u + 1), @cd_s, 1211.94, 1219.30, 7.36, 0.00, 7.36, NULL, 1.0, 0.7701, 944.23, 1154.26, 'ELO-K32-V2', '2026-10-26 10:00:00'
       UNION ALL SELECT (@cd_sm + 46), (@cd_p + 456), (@cd_u + 2), @cd_s, 1188.07, 1195.43, 7.36, 0.00, 7.36, NULL, 1.0, 0.7701, 944.23, 1154.26, 'ELO-K32-V2', '2026-10-26 10:00:00'
       UNION ALL SELECT (@cd_sm + 46), (@cd_p + 458), (@cd_u + 4), @cd_s, 1178.96, 1186.32, 7.36, 0.00, 7.36, NULL, 1.0, 0.7701, 944.23, 1154.26, 'ELO-K32-V2', '2026-10-26 10:00:00'
       UNION ALL SELECT (@cd_sm + 46), (@cd_p + 457), (@cd_u + 6), @cd_s, 1173.83, 1181.19, 7.36, 0.00, 7.36, NULL, 1.0, 0.7701, 944.23, 1154.26, 'ELO-K32-V2', '2026-10-26 10:00:00'
       UNION ALL SELECT (@cd_sm + 46), (@cd_p + 460), (@cd_u + 7), @cd_s, 1018.51, 1033.22, 14.71, 0.00, 14.71, NULL, 1.0, 0.7701, 944.23, 1154.26, 'ELO-K32-V2', '2026-10-26 10:00:00'
       UNION ALL SELECT (@cd_sm + 46), (@cd_p + 453), (@cd_u + 29), @cd_s, 918.12, 910.76, -7.36, 0.00, -7.36, NULL, 0.0, 0.2299, 944.23, 1154.26, 'ELO-K32-V2', '2026-10-26 10:00:00'
       UNION ALL SELECT (@cd_sm + 46), (@cd_p + 451), (@cd_u + 30), @cd_s, 924.89, 917.53, -7.36, 0.00, -7.36, NULL, 0.0, 0.2299, 944.23, 1154.26, 'ELO-K32-V2', '2026-10-26 10:00:00'
       UNION ALL SELECT (@cd_sm + 46), (@cd_p + 455), (@cd_u + 32), @cd_s, 920.86, 913.50, -7.36, 0.00, -7.36, NULL, 0.0, 0.2299, 944.23, 1154.26, 'ELO-K32-V2', '2026-10-26 10:00:00'
       UNION ALL SELECT (@cd_sm + 46), (@cd_p + 452), (@cd_u + 34), @cd_s, 988.45, 981.09, -7.36, 0.00, -7.36, NULL, 0.0, 0.2299, 944.23, 1154.26, 'ELO-K32-V2', '2026-10-26 10:00:00'
       UNION ALL SELECT (@cd_sm + 46), (@cd_p + 454), (@cd_u + 35), @cd_s, 968.83, 954.12, -14.71, 0.00, -14.71, NULL, 0.0, 0.2299, 944.23, 1154.26, 'ELO-K32-V2', '2026-10-26 10:00:00'
       UNION ALL SELECT (@cd_sm + 47), (@cd_p + 467), (@cd_u + 50), @cd_s, 906.97, 895.90, -11.07, 0.00, -11.07, NULL, 0.0, 0.3458, 913.60, 1024.33, 'ELO-K32-V2', '2026-10-28 22:00:00'
       UNION ALL SELECT (@cd_sm + 47), (@cd_p + 469), (@cd_u + 51), @cd_s, 882.92, 871.85, -11.07, 0.00, -11.07, NULL, 0.0, 0.3458, 913.60, 1024.33, 'ELO-K32-V2', '2026-10-28 22:00:00'
       UNION ALL SELECT (@cd_sm + 47), (@cd_p + 468), (@cd_u + 53), @cd_s, 930.64, 919.57, -11.07, 0.00, -11.07, NULL, 0.0, 0.3458, 913.60, 1024.33, 'ELO-K32-V2', '2026-10-28 22:00:00'
       UNION ALL SELECT (@cd_sm + 47), (@cd_p + 466), (@cd_u + 54), @cd_s, 881.93, 870.86, -11.07, 0.00, -11.07, NULL, 0.0, 0.3458, 913.60, 1024.33, 'ELO-K32-V2', '2026-10-28 22:00:00'
       UNION ALL SELECT (@cd_sm + 47), (@cd_p + 470), (@cd_u + 55), @cd_s, 965.55, 954.48, -11.07, 0.00, -11.07, NULL, 0.0, 0.3458, 913.60, 1024.33, 'ELO-K32-V2', '2026-10-28 22:00:00'
       UNION ALL SELECT (@cd_sm + 47), (@cd_p + 462), (@cd_u + 67), @cd_s, 997.05, 1008.12, 11.07, 0.00, 11.07, NULL, 1.0, 0.6542, 913.60, 1024.33, 'ELO-K32-V2', '2026-10-28 22:00:00'
       UNION ALL SELECT (@cd_sm + 47), (@cd_p + 463), (@cd_u + 68), @cd_s, 993.48, 1015.61, 22.13, 0.00, 22.13, NULL, 1.0, 0.6542, 913.60, 1024.33, 'ELO-K32-V2', '2026-10-28 22:00:00'
       UNION ALL SELECT (@cd_sm + 47), (@cd_p + 464), (@cd_u + 71), @cd_s, 1054.05, 1076.18, 22.13, 0.00, 22.13, NULL, 1.0, 0.6542, 913.60, 1024.33, 'ELO-K32-V2', '2026-10-28 22:00:00'
       UNION ALL SELECT (@cd_sm + 47), (@cd_p + 461), (@cd_u + 72), @cd_s, 1067.05, 1089.18, 22.13, 0.00, 22.13, NULL, 1.0, 0.6542, 913.60, 1024.33, 'ELO-K32-V2', '2026-10-28 22:00:00'
       UNION ALL SELECT (@cd_sm + 47), (@cd_p + 465), (@cd_u + 74), @cd_s, 1010.01, 1032.14, 22.13, 0.00, 22.13, NULL, 1.0, 0.6542, 913.60, 1024.33, 'ELO-K32-V2', '2026-10-28 22:00:00'
       UNION ALL SELECT (@cd_sm + 48), (@cd_p + 475), (@cd_u + 1), @cd_s, 1219.30, 1226.44, 7.14, 0.00, 7.14, NULL, 1.0, 0.7770, 1162.52, 945.65, 'ELO-K32-V2', '2026-10-29 21:00:00'
       UNION ALL SELECT (@cd_sm + 48), (@cd_p + 472), (@cd_u + 2), @cd_s, 1195.43, 1202.57, 7.14, 0.00, 7.14, NULL, 1.0, 0.7770, 1162.52, 945.65, 'ELO-K32-V2', '2026-10-29 21:00:00'
       UNION ALL SELECT (@cd_sm + 48), (@cd_p + 473), (@cd_u + 3), @cd_s, 1180.88, 1188.02, 7.14, 0.00, 7.14, NULL, 1.0, 0.7770, 1162.52, 945.65, 'ELO-K32-V2', '2026-10-29 21:00:00'
       UNION ALL SELECT (@cd_sm + 48), (@cd_p + 471), (@cd_u + 5), @cd_s, 1183.77, 1190.91, 7.14, 0.00, 7.14, NULL, 1.0, 0.7770, 1162.52, 945.65, 'ELO-K32-V2', '2026-10-29 21:00:00'
       UNION ALL SELECT (@cd_sm + 48), (@cd_p + 474), (@cd_u + 7), @cd_s, 1033.22, 1047.49, 14.27, 0.00, 14.27, NULL, 1.0, 0.7770, 1162.52, 945.65, 'ELO-K32-V2', '2026-10-29 21:00:00'
       UNION ALL SELECT (@cd_sm + 48), (@cd_p + 480), (@cd_u + 36), @cd_s, 898.07, 883.80, -14.27, 0.00, -14.27, NULL, 0.0, 0.2230, 1162.52, 945.65, 'ELO-K32-V2', '2026-10-29 21:00:00'
       UNION ALL SELECT (@cd_sm + 48), (@cd_p + 477), (@cd_u + 37), @cd_s, 978.65, 964.38, -14.27, 0.00, -14.27, NULL, 0.0, 0.2230, 1162.52, 945.65, 'ELO-K32-V2', '2026-10-29 21:00:00'
       UNION ALL SELECT (@cd_sm + 48), (@cd_p + 478), (@cd_u + 38), @cd_s, 910.84, 896.57, -14.27, 0.00, -14.27, NULL, 0.0, 0.2230, 1162.52, 945.65, 'ELO-K32-V2', '2026-10-29 21:00:00'
       UNION ALL SELECT (@cd_sm + 48), (@cd_p + 479), (@cd_u + 39), @cd_s, 967.25, 952.98, -14.27, 0.00, -14.27, NULL, 0.0, 0.2230, 1162.52, 945.65, 'ELO-K32-V2', '2026-10-29 21:00:00'
       UNION ALL SELECT (@cd_sm + 48), (@cd_p + 476), (@cd_u + 40), @cd_s, 973.44, 959.17, -14.27, 0.00, -14.27, NULL, 0.0, 0.2230, 1162.52, 945.65, 'ELO-K32-V2', '2026-10-29 21:00:00'
       UNION ALL SELECT (@cd_sm + 49), (@cd_p + 488), (@cd_u + 1), @cd_s, 1226.44, 1236.45, 10.01, 0.00, 10.01, NULL, 1.0, 0.6871, 1166.81, 1030.17, 'ELO-K32-V2', '2026-11-05 21:00:00'
       UNION ALL SELECT (@cd_sm + 49), (@cd_p + 489), (@cd_u + 3), @cd_s, 1188.02, 1198.03, 10.01, 0.00, 10.01, NULL, 1.0, 0.6871, 1166.81, 1030.17, 'ELO-K32-V2', '2026-11-05 21:00:00'
       UNION ALL SELECT (@cd_sm + 49), (@cd_p + 490), (@cd_u + 5), @cd_s, 1190.91, 1200.92, 10.01, 0.00, 10.01, NULL, 1.0, 0.6871, 1166.81, 1030.17, 'ELO-K32-V2', '2026-11-05 21:00:00'
       UNION ALL SELECT (@cd_sm + 49), (@cd_p + 486), (@cd_u + 6), @cd_s, 1181.19, 1191.20, 10.01, 0.00, 10.01, NULL, 1.0, 0.6871, 1166.81, 1030.17, 'ELO-K32-V2', '2026-11-05 21:00:00'
       UNION ALL SELECT (@cd_sm + 49), (@cd_p + 487), (@cd_u + 7), @cd_s, 1047.49, 1067.52, 20.03, 0.00, 20.03, NULL, 1.0, 0.6871, 1166.81, 1030.17, 'ELO-K32-V2', '2026-11-05 21:00:00'
       UNION ALL SELECT (@cd_sm + 49), (@cd_p + 484), (@cd_u + 57), @cd_s, 1004.93, 994.92, -10.01, 0.00, -10.01, NULL, 0.0, 0.3129, 1166.81, 1030.17, 'ELO-K32-V2', '2026-11-05 21:00:00'
       UNION ALL SELECT (@cd_sm + 49), (@cd_p + 485), (@cd_u + 59), @cd_s, 1029.23, 1019.22, -10.01, 0.00, -10.01, NULL, 0.0, 0.3129, 1166.81, 1030.17, 'ELO-K32-V2', '2026-11-05 21:00:00'
       UNION ALL SELECT (@cd_sm + 49), (@cd_p + 482), (@cd_u + 60), @cd_s, 1067.55, 1057.54, -10.01, 0.00, -10.01, NULL, 0.0, 0.3129, 1166.81, 1030.17, 'ELO-K32-V2', '2026-11-05 21:00:00'
       UNION ALL SELECT (@cd_sm + 49), (@cd_p + 481), (@cd_u + 65), @cd_s, 1025.36, 1005.33, -20.03, 0.00, -20.03, NULL, 0.0, 0.3129, 1166.81, 1030.17, 'ELO-K32-V2', '2026-11-05 21:00:00'
       UNION ALL SELECT (@cd_sm + 49), (@cd_p + 483), (@cd_u + 66), @cd_s, 1023.76, 1003.73, -20.03, 0.00, -20.03, NULL, 0.0, 0.3129, 1166.81, 1030.17, 'ELO-K32-V2', '2026-11-05 21:00:00'
       UNION ALL SELECT (@cd_sm + 50), (@cd_p + 498), (@cd_u + 37), @cd_s, 964.38, 952.15, -12.23, 0.00, -12.23, NULL, 0.0, 0.3823, 942.21, 1025.55, 'ELO-K32-V2', '2026-11-09 18:00:00'
       UNION ALL SELECT (@cd_sm + 50), (@cd_p + 494), (@cd_u + 38), @cd_s, 896.57, 884.34, -12.23, 0.00, -12.23, NULL, 0.0, 0.3823, 942.21, 1025.55, 'ELO-K32-V2', '2026-11-09 18:00:00'
       UNION ALL SELECT (@cd_sm + 50), (@cd_p + 500), (@cd_u + 40), @cd_s, 959.17, 946.94, -12.23, 0.00, -12.23, NULL, 0.0, 0.3823, 942.21, 1025.55, 'ELO-K32-V2', '2026-11-09 18:00:00'
       UNION ALL SELECT (@cd_sm + 50), (@cd_p + 499), (@cd_u + 41), @cd_s, 939.09, 926.86, -12.23, 0.00, -12.23, NULL, 0.0, 0.3823, 942.21, 1025.55, 'ELO-K32-V2', '2026-11-09 18:00:00'
       UNION ALL SELECT (@cd_sm + 50), (@cd_p + 496), (@cd_u + 42), @cd_s, 951.86, 939.63, -12.23, 0.00, -12.23, NULL, 0.0, 0.3823, 942.21, 1025.55, 'ELO-K32-V2', '2026-11-09 18:00:00'
       UNION ALL SELECT (@cd_sm + 50), (@cd_p + 492), (@cd_u + 67), @cd_s, 1008.12, 1020.35, 12.23, 0.00, 12.23, NULL, 1.0, 0.6177, 942.21, 1025.55, 'ELO-K32-V2', '2026-11-09 18:00:00'
       UNION ALL SELECT (@cd_sm + 50), (@cd_p + 491), (@cd_u + 68), @cd_s, 1015.61, 1027.84, 12.23, 0.00, 12.23, NULL, 1.0, 0.6177, 942.21, 1025.55, 'ELO-K32-V2', '2026-11-09 18:00:00'
       UNION ALL SELECT (@cd_sm + 50), (@cd_p + 497), (@cd_u + 70), @cd_s, 1014.84, 1027.07, 12.23, 0.00, 12.23, NULL, 1.0, 0.6177, 942.21, 1025.55, 'ELO-K32-V2', '2026-11-09 18:00:00'
       UNION ALL SELECT (@cd_sm + 50), (@cd_p + 495), (@cd_u + 72), @cd_s, 1089.18, 1101.41, 12.23, 0.00, 12.23, NULL, 1.0, 0.6177, 942.21, 1025.55, 'ELO-K32-V2', '2026-11-09 18:00:00'
       UNION ALL SELECT (@cd_sm + 50), (@cd_p + 493), (@cd_u + 76), @cd_s, 1000.00, 1024.47, 24.47, 0.00, 24.47, NULL, 1.0, 0.6177, 942.21, 1025.55, 'ELO-K32-V2', '2026-11-09 18:00:00'
) v
 WHERE @cd_ready = 1;

COMMIT;

-- 확인 1: 넣은 행 수 (이미 적용된 DB에서도 위임·탈퇴 전이면 같은 값)
SELECT (SELECT COUNT(*) FROM app_user u WHERE u.email LIKE 'club-member-___@spoit.test' OR u.email IN ('club-00@spoit.test', 'club-01@spoit.test', 'club-02@spoit.test', 'club-03@spoit.test', 'club-04@spoit.test', 'club-05@spoit.test')) AS club_demo_users,
       (SELECT COUNT(*) FROM club WHERE club_name IN ('잠실 드림풋살', '반포 리버사이드 FC', '이촌 한강 FS', '영등포 레인보우 FS', '망원 선데이 FC', 'BLUE WAVE FS', '구일 FS', 'OFFSIDE FC', '가락 FS', '코사 FS') AND deleted_at IS NULL) AS club_demo_clubs,
       (SELECT COUNT(*) FROM club_member m JOIN club c ON c.club_id = m.club_id WHERE c.club_name IN ('잠실 드림풋살', '반포 리버사이드 FC', '이촌 한강 FS', '영등포 레인보우 FS', '망원 선데이 FC', 'BLUE WAVE FS', '구일 FS', 'OFFSIDE FC', '가락 FS', '코사 FS') AND c.deleted_at IS NULL AND m.left_at IS NULL) AS active_members,
       (SELECT COUNT(DISTINCT ma.match_id) FROM match_application ma JOIN club c ON c.club_id = ma.club_id JOIN social_match sm ON sm.match_id = ma.match_id
         WHERE c.club_name IN ('잠실 드림풋살', '반포 리버사이드 FC', '이촌 한강 FS', '영등포 레인보우 FS', '망원 선데이 FC', 'BLUE WAVE FS', '구일 FS', 'OFFSIDE FC', '가락 FS', '코사 FS') AND ma.status = '확정' AND sm.status = '종료') AS past_matches,
       (SELECT COUNT(*) FROM sport_rating_history h JOIN app_user u ON u.user_id = h.user_id WHERE u.email LIKE 'club-member-___@spoit.test' OR u.email IN ('club-00@spoit.test', 'club-01@spoit.test', 'club-02@spoit.test', 'club-03@spoit.test', 'club-04@spoit.test', 'club-05@spoit.test')) AS history_rows,
       (SELECT COUNT(*) FROM match_application WHERE receipt_no IN ('RCPT-20261111-GKRSKPBM', 'RCPT-20261111-3ESVPJEC', 'RCPT-20261111-CUSJ9E6L', 'RCPT-20261111-RG4G2CAL', 'RCPT-20261111-WK7SR4HY', 'RCPT-20261111-487KTF3X', 'RCPT-20261111-SNNGBLSX', 'RCPT-20261111-QH2BVVNW', 'RCPT-20261111-JTFM5EC6', 'RCPT-20261111-EW4TSLED') AND status = '대기' AND group_application_id IS NOT NULL) AS team_applications;
-- 기대값: club_demo_users 76, club_demo_clubs 10, active_members 76, past_matches 50, history_rows 500, team_applications 10

-- 확인 2: 레이팅이 이력과 맞는지 (sport_rating = 마지막 이력, 경기 수 = 이력 행 수). 0이면 맞다.
SELECT COUNT(*) AS rating_mismatches
  FROM app_user u JOIN sport_rating sr ON sr.user_id = u.user_id
  JOIN sport s ON s.sport_id = sr.sport_id AND s.name = '축구+풋살'
 WHERE (u.email LIKE 'club-member-___@spoit.test' OR u.email IN ('club-00@spoit.test', 'club-01@spoit.test', 'club-02@spoit.test', 'club-03@spoit.test', 'club-04@spoit.test', 'club-05@spoit.test'))
   AND (sr.rated_match_count <> (SELECT COUNT(*) FROM sport_rating_history h WHERE h.user_id = u.user_id AND h.sport_id = sr.sport_id)
        OR sr.current_mmr <> COALESCE((SELECT h.mmr_after FROM sport_rating_history h WHERE h.user_id = u.user_id AND h.sport_id = sr.sport_id
                                        ORDER BY h.calculated_at DESC, h.history_id DESC LIMIT 1), 1000));

-- 확인 3: 동호회별 인원과 팀 등급(배치를 마친 동호회원 평균, ClubMapper.xml averageMmr과 같은 식)
SELECT c.club_name, c.recruit_status, CONCAT(COUNT(m.club_member_id), '/', c.max_members) AS members,
       (SELECT ROUND(AVG(sr.current_mmr)) FROM club_member am JOIN sport_rating sr ON sr.user_id = am.user_id AND sr.sport_id = c.sport_id
         WHERE am.club_id = c.club_id AND am.left_at IS NULL AND sr.rated_match_count >= 5) AS average_mmr
  FROM club c JOIN club_member m ON m.club_id = c.club_id AND m.left_at IS NULL
 WHERE c.club_name IN ('잠실 드림풋살', '반포 리버사이드 FC', '이촌 한강 FS', '영등포 레인보우 FS', '망원 선데이 FC', 'BLUE WAVE FS', '구일 FS', 'OFFSIDE FC', '가락 FS', '코사 FS') AND c.deleted_at IS NULL
 GROUP BY c.club_id ORDER BY average_mmr DESC;
-- 기대 등급(위임·탈퇴 전): 잠실 드림풋살 골드(1203), 반포 리버사이드 FC 실버(1016), 이촌 한강 FS 실버(1020), 영등포 레인보우 FS 브론즈(978), 망원 선데이 FC 브론즈(930), BLUE WAVE FS 브론즈(927), 구일 FS 브론즈(898), OFFSIDE FC 브론즈(919), 가락 FS 실버(1034), 코사 FS 실버(1027)
-- 등급별 동호회 수: 브론즈 5, 실버 4, 골드 1, 플래티넘 0, 다이아 0
