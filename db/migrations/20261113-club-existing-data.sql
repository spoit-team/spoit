-- =====================================================================
-- 기존 동호회 데이터: seed 동호회 14곳에 새 회원 62명, 지난 동호회 경기 42개(레이팅 이력 204행),
--                   예정 매칭방 14개와 팀 신청 34행을 넣고, seed·HY 님 데이터 동호회 28곳의 소개 글을 다시 쓴다.
-- 이 파일은 scripts/club-demo-data.py가 만든다. 직접 고치지 말고 스크립트를 고쳐 다시 만든다.
-- 대상: 20261112-club-demo-data.sql까지 적용한 DB (로컬·운영)
-- 내용
--   * 기존 회원(seed 계정, demo-NNN)은 바꾸지 않는다. 새 회원(club-user-NNN@spoit.test, 비밀번호 spoit1234)만 동호회원으로 넣고,
--     지난 경기는 새 회원끼리 같은 종목 동호회와 치른 것이다(농구 4:4, 배드민턴·테니스 복식 2:2).
--   * 지난 경기는 2026-08~11월 초, 홈 동호회의 구장·활동 시간. 팀 신청 확정 → 참가자 → 두 팀장 결과 제출 → 결과 마감(종료 24시간 뒤)에
--     평가 없이 레이팅 반영까지 끝난 종료 상태다. 레이팅은 RatingCalculator와 같은 식으로 1000부터 시간순 계산했다.
--   * 예정 일정: 새 회원을 넣은 동호회마다 11/24~12/6 활동 시간에 매칭방 1개와 팀 신청(대기).
--   * 소개 글만 바꾸는 곳: 주말 풋살 클럽, 송파 셔틀콕, 은평 풋살 클럽, 노원 풋살회, 관악 족구회, 잠실 농구회, 뚝섬 FC, 망원 풋살 크루, 잠실 풋살 클럽, 반포 농구단, 광나루 바스켓, 한강 족구회, 뚝섬 셔틀콕, 마포 랠리 테니스.
--     seed 가입 신청·매치에 쓰이거나 다른 파트 테스트 계정이 있는 곳, HY 님 시연 데이터 동호회(회원·경기·매칭방은 그대로),
--     같은 종목 상대 동호회가 없는 곳이다.
-- 원칙
--   * 한 트랜잭션. 사전 확인에 실패하면 멈추고 아무 행도 넣지 않는다.
--   * club-user-001@spoit.test가 이미 있으면 적용된 것으로 보고 아무것도 넣지 않는다(오류 없음).
--   * 동호회·종목·지역·시설·담당자는 이름으로 찾는다. 새 행 번호는 각 테이블의 현재 최대 번호 다음부터 쓴다.
--     기존 행은 동호회 소개 글만 바꾼다.
-- =====================================================================

SET NAMES utf8mb4;
START TRANSACTION;

-- 0. 이름으로 번호 찾기
SET @cx_s_축구 = (SELECT MAX(sport_id) FROM sport WHERE name = '축구+풋살');
SET @cx_s_농구 = (SELECT MAX(sport_id) FROM sport WHERE name = '농구');
SET @cx_s_배드민턴 = (SELECT MAX(sport_id) FROM sport WHERE name = '배드민턴');
SET @cx_s_테니스 = (SELECT MAX(sport_id) FROM sport WHERE name = '테니스');
SET @cx_s_족구 = (SELECT MAX(sport_id) FROM sport WHERE name = '족구');
SET @cx_r_강동구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '강동구');
SET @cx_r_강서구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '강서구');
SET @cx_r_관악구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '관악구');
SET @cx_r_광진구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '광진구');
SET @cx_r_노원구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '노원구');
SET @cx_r_마포구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '마포구');
SET @cx_r_서초구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '서초구');
SET @cx_r_성동구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '성동구');
SET @cx_r_송파구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '송파구');
SET @cx_r_영등포구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '영등포구');
SET @cx_r_은평구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '은평구');
SET @cx_c1 = (SELECT MAX(c.club_id) FROM club c JOIN club_region cr ON cr.club_id = c.club_id WHERE c.active_club_name = '주말 풋살 클럽' AND c.sport_id = @cx_s_축구 AND cr.region_id = @cx_r_성동구);
SET @cx_c2 = (SELECT MAX(c.club_id) FROM club c JOIN club_region cr ON cr.club_id = c.club_id WHERE c.active_club_name = '노원 농구회' AND c.sport_id = @cx_s_농구 AND cr.region_id = @cx_r_노원구);
SET @cx_c3 = (SELECT MAX(c.club_id) FROM club c JOIN club_region cr ON cr.club_id = c.club_id WHERE c.active_club_name = '송파 셔틀콕' AND c.sport_id = @cx_s_배드민턴 AND cr.region_id = @cx_r_송파구);
SET @cx_c4 = (SELECT MAX(c.club_id) FROM club c JOIN club_region cr ON cr.club_id = c.club_id WHERE c.active_club_name = '마포 테니스 클럽' AND c.sport_id = @cx_s_테니스 AND cr.region_id = @cx_r_마포구);
SET @cx_c5 = (SELECT MAX(c.club_id) FROM club c JOIN club_region cr ON cr.club_id = c.club_id WHERE c.active_club_name = '서초 나이트 테니스' AND c.sport_id = @cx_s_테니스 AND cr.region_id = @cx_r_서초구);
SET @cx_c6 = (SELECT MAX(c.club_id) FROM club c JOIN club_region cr ON cr.club_id = c.club_id WHERE c.active_club_name = '성동 셔틀콕 모임' AND c.sport_id = @cx_s_배드민턴 AND cr.region_id = @cx_r_성동구);
SET @cx_c7 = (SELECT MAX(c.club_id) FROM club c JOIN club_region cr ON cr.club_id = c.club_id WHERE c.active_club_name = '광진 배드민턴회' AND c.sport_id = @cx_s_배드민턴 AND cr.region_id = @cx_r_광진구);
SET @cx_c8 = (SELECT MAX(c.club_id) FROM club c JOIN club_region cr ON cr.club_id = c.club_id WHERE c.active_club_name = '서초 테니스회' AND c.sport_id = @cx_s_테니스 AND cr.region_id = @cx_r_서초구);
SET @cx_c9 = (SELECT MAX(c.club_id) FROM club c JOIN club_region cr ON cr.club_id = c.club_id WHERE c.active_club_name = '영등포 테니스 클럽' AND c.sport_id = @cx_s_테니스 AND cr.region_id = @cx_r_영등포구);
SET @cx_c10 = (SELECT MAX(c.club_id) FROM club c JOIN club_region cr ON cr.club_id = c.club_id WHERE c.active_club_name = '송파 농구 동호회' AND c.sport_id = @cx_s_농구 AND cr.region_id = @cx_r_송파구);
SET @cx_c11 = (SELECT MAX(c.club_id) FROM club c JOIN club_region cr ON cr.club_id = c.club_id WHERE c.active_club_name = '송파 배드민턴회' AND c.sport_id = @cx_s_배드민턴 AND cr.region_id = @cx_r_송파구);
SET @cx_c12 = (SELECT MAX(c.club_id) FROM club c JOIN club_region cr ON cr.club_id = c.club_id WHERE c.active_club_name = '은평 풋살 클럽' AND c.sport_id = @cx_s_축구 AND cr.region_id = @cx_r_은평구);
SET @cx_c13 = (SELECT MAX(c.club_id) FROM club c JOIN club_region cr ON cr.club_id = c.club_id WHERE c.active_club_name = '광진 테니스회' AND c.sport_id = @cx_s_테니스 AND cr.region_id = @cx_r_광진구);
SET @cx_c14 = (SELECT MAX(c.club_id) FROM club c JOIN club_region cr ON cr.club_id = c.club_id WHERE c.active_club_name = '강서 배드민턴회' AND c.sport_id = @cx_s_배드민턴 AND cr.region_id = @cx_r_강서구);
SET @cx_c15 = (SELECT MAX(c.club_id) FROM club c JOIN club_region cr ON cr.club_id = c.club_id WHERE c.active_club_name = '영등포 농구클럽' AND c.sport_id = @cx_s_농구 AND cr.region_id = @cx_r_영등포구);
SET @cx_c16 = (SELECT MAX(c.club_id) FROM club c JOIN club_region cr ON cr.club_id = c.club_id WHERE c.active_club_name = '노원 풋살회' AND c.sport_id = @cx_s_축구 AND cr.region_id = @cx_r_노원구);
SET @cx_c17 = (SELECT MAX(c.club_id) FROM club c JOIN club_region cr ON cr.club_id = c.club_id WHERE c.active_club_name = '관악 족구회' AND c.sport_id = @cx_s_족구 AND cr.region_id = @cx_r_관악구);
SET @cx_c18 = (SELECT MAX(c.club_id) FROM club c JOIN club_region cr ON cr.club_id = c.club_id WHERE c.active_club_name = '강서 테니스회' AND c.sport_id = @cx_s_테니스 AND cr.region_id = @cx_r_강서구);
SET @cx_c19 = (SELECT MAX(c.club_id) FROM club c JOIN club_region cr ON cr.club_id = c.club_id WHERE c.active_club_name = '송파 배드민턴' AND c.sport_id = @cx_s_배드민턴 AND cr.region_id = @cx_r_송파구);
SET @cx_c20 = (SELECT MAX(c.club_id) FROM club c JOIN club_region cr ON cr.club_id = c.club_id WHERE c.active_club_name = '잠실 농구회' AND c.sport_id = @cx_s_농구 AND cr.region_id = @cx_r_송파구);
SET @cx_c21 = (SELECT MAX(c.club_id) FROM club c JOIN club_region cr ON cr.club_id = c.club_id WHERE c.active_club_name = '뚝섬 FC' AND c.sport_id = @cx_s_축구 AND cr.region_id = @cx_r_광진구);
SET @cx_c22 = (SELECT MAX(c.club_id) FROM club c JOIN club_region cr ON cr.club_id = c.club_id WHERE c.active_club_name = '망원 풋살 크루' AND c.sport_id = @cx_s_축구 AND cr.region_id = @cx_r_마포구);
SET @cx_c23 = (SELECT MAX(c.club_id) FROM club c JOIN club_region cr ON cr.club_id = c.club_id WHERE c.active_club_name = '잠실 풋살 클럽' AND c.sport_id = @cx_s_축구 AND cr.region_id = @cx_r_송파구);
SET @cx_c24 = (SELECT MAX(c.club_id) FROM club c JOIN club_region cr ON cr.club_id = c.club_id WHERE c.active_club_name = '반포 농구단' AND c.sport_id = @cx_s_농구 AND cr.region_id = @cx_r_서초구);
SET @cx_c25 = (SELECT MAX(c.club_id) FROM club c JOIN club_region cr ON cr.club_id = c.club_id WHERE c.active_club_name = '광나루 바스켓' AND c.sport_id = @cx_s_농구 AND cr.region_id = @cx_r_강동구);
SET @cx_c26 = (SELECT MAX(c.club_id) FROM club c JOIN club_region cr ON cr.club_id = c.club_id WHERE c.active_club_name = '한강 족구회' AND c.sport_id = @cx_s_족구 AND cr.region_id = @cx_r_광진구);
SET @cx_c27 = (SELECT MAX(c.club_id) FROM club c JOIN club_region cr ON cr.club_id = c.club_id WHERE c.active_club_name = '뚝섬 셔틀콕' AND c.sport_id = @cx_s_배드민턴 AND cr.region_id = @cx_r_광진구);
SET @cx_c28 = (SELECT MAX(c.club_id) FROM club c JOIN club_region cr ON cr.club_id = c.club_id WHERE c.active_club_name = '마포 랠리 테니스' AND c.sport_id = @cx_s_테니스 AND cr.region_id = @cx_r_마포구);
SET @cx_f1 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '노원구' AND f.name = '노원 농구장' AND f.deleted_at IS NULL);
SET @cx_f1_mgr = (SELECT MIN(user_id) FROM facility_manager WHERE facility_id = @cx_f1);
SET @cx_f2 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '마포구' AND f.name = '월드컵공원 테니스장' AND f.deleted_at IS NULL);
SET @cx_f2_mgr = (SELECT MIN(user_id) FROM facility_manager WHERE facility_id = @cx_f2);
SET @cx_f3 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '서초구' AND f.name = '서초 테니스클럽' AND f.deleted_at IS NULL);
SET @cx_f3_mgr = (SELECT MIN(user_id) FROM facility_manager WHERE facility_id = @cx_f3);
SET @cx_f4 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '성동구' AND f.name = '중랑물재생센터 배드민턴장' AND f.deleted_at IS NULL);
SET @cx_f4_mgr = (SELECT MIN(user_id) FROM facility_manager WHERE facility_id = @cx_f4);
SET @cx_f5 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '광진구' AND f.name = '광진 배드민턴장' AND f.deleted_at IS NULL);
SET @cx_f5_mgr = (SELECT MIN(user_id) FROM facility_manager WHERE facility_id = @cx_f5);
SET @cx_f6 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '서초구' AND f.name = '서초 스포츠센터' AND f.deleted_at IS NULL);
SET @cx_f6_mgr = (SELECT MIN(user_id) FROM facility_manager WHERE facility_id = @cx_f6);
SET @cx_f7 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '영등포구' AND f.name = '영등포 테니스장' AND f.deleted_at IS NULL);
SET @cx_f7_mgr = (SELECT MIN(user_id) FROM facility_manager WHERE facility_id = @cx_f7);
SET @cx_f8 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '송파구' AND f.name = '광나루 농구장(올림픽대로 방향)' AND f.deleted_at IS NULL);
SET @cx_f8_mgr = (SELECT MIN(user_id) FROM facility_manager WHERE facility_id = @cx_f8);
SET @cx_f9 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '송파구' AND f.name = '송파 배드민턴홀' AND f.deleted_at IS NULL);
SET @cx_f9_mgr = (SELECT MIN(user_id) FROM facility_manager WHERE facility_id = @cx_f9);
SET @cx_f10 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '광진구' AND f.name = '어린이대공원 테니스장 A코트' AND f.deleted_at IS NULL);
SET @cx_f10_mgr = (SELECT MIN(user_id) FROM facility_manager WHERE facility_id = @cx_f10);
SET @cx_f11 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '강서구' AND f.name = '강서 배드민턴장' AND f.deleted_at IS NULL);
SET @cx_f11_mgr = (SELECT MIN(user_id) FROM facility_manager WHERE facility_id = @cx_f11);
SET @cx_f12 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '영등포구' AND f.name = '영등포 농구센터' AND f.deleted_at IS NULL);
SET @cx_f12_mgr = (SELECT MIN(user_id) FROM facility_manager WHERE facility_id = @cx_f12);
SET @cx_f13 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '강서구' AND f.name = '강서 테니스장' AND f.deleted_at IS NULL);
SET @cx_f13_mgr = (SELECT MIN(user_id) FROM facility_manager WHERE facility_id = @cx_f13);
SET @cx_f14 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '송파구' AND f.name = '송파 배드민턴코트' AND f.deleted_at IS NULL);
SET @cx_f14_mgr = (SELECT MIN(user_id) FROM facility_manager WHERE facility_id = @cx_f14);

-- 사전 확인. 출력되는 값이 모두 1이어야 진행한다. already_applied가 1이면 이미 적용된 DB라 아무것도 넣지 않는다.
SELECT (@cx_s_축구 IS NOT NULL AND @cx_s_농구 IS NOT NULL AND @cx_s_배드민턴 IS NOT NULL AND @cx_s_테니스 IS NOT NULL AND @cx_s_족구 IS NOT NULL),
       ((@cx_r_강동구 IS NOT NULL) + (@cx_r_강서구 IS NOT NULL) + (@cx_r_관악구 IS NOT NULL) + (@cx_r_광진구 IS NOT NULL) + (@cx_r_노원구 IS NOT NULL) + (@cx_r_마포구 IS NOT NULL) + (@cx_r_서초구 IS NOT NULL) + (@cx_r_성동구 IS NOT NULL) + (@cx_r_송파구 IS NOT NULL) + (@cx_r_영등포구 IS NOT NULL) + (@cx_r_은평구 IS NOT NULL)) = 11,
       ((@cx_c1 IS NOT NULL) + (@cx_c2 IS NOT NULL) + (@cx_c3 IS NOT NULL) + (@cx_c4 IS NOT NULL) + (@cx_c5 IS NOT NULL) + (@cx_c6 IS NOT NULL) + (@cx_c7 IS NOT NULL) + (@cx_c8 IS NOT NULL) + (@cx_c9 IS NOT NULL) + (@cx_c10 IS NOT NULL) + (@cx_c11 IS NOT NULL) + (@cx_c12 IS NOT NULL) + (@cx_c13 IS NOT NULL) + (@cx_c14 IS NOT NULL) + (@cx_c15 IS NOT NULL) + (@cx_c16 IS NOT NULL) + (@cx_c17 IS NOT NULL) + (@cx_c18 IS NOT NULL) + (@cx_c19 IS NOT NULL) + (@cx_c20 IS NOT NULL) + (@cx_c21 IS NOT NULL) + (@cx_c22 IS NOT NULL) + (@cx_c23 IS NOT NULL) + (@cx_c24 IS NOT NULL) + (@cx_c25 IS NOT NULL) + (@cx_c26 IS NOT NULL) + (@cx_c27 IS NOT NULL) + (@cx_c28 IS NOT NULL)) = 28,
       (((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c2 AND left_at IS NULL) + 6 < (SELECT max_members FROM club WHERE club_id = @cx_c2)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c4 AND left_at IS NULL) + 4 < (SELECT max_members FROM club WHERE club_id = @cx_c4)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c5 AND left_at IS NULL) + 4 < (SELECT max_members FROM club WHERE club_id = @cx_c5)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c6 AND left_at IS NULL) + 4 < (SELECT max_members FROM club WHERE club_id = @cx_c6)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c7 AND left_at IS NULL) + 4 < (SELECT max_members FROM club WHERE club_id = @cx_c7)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c8 AND left_at IS NULL) + 4 < (SELECT max_members FROM club WHERE club_id = @cx_c8)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c9 AND left_at IS NULL) + 4 < (SELECT max_members FROM club WHERE club_id = @cx_c9)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c10 AND left_at IS NULL) + 6 < (SELECT max_members FROM club WHERE club_id = @cx_c10)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c11 AND left_at IS NULL) + 4 < (SELECT max_members FROM club WHERE club_id = @cx_c11)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c13 AND left_at IS NULL) + 4 < (SELECT max_members FROM club WHERE club_id = @cx_c13)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c14 AND left_at IS NULL) + 4 < (SELECT max_members FROM club WHERE club_id = @cx_c14)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c15 AND left_at IS NULL) + 6 < (SELECT max_members FROM club WHERE club_id = @cx_c15)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c18 AND left_at IS NULL) + 4 < (SELECT max_members FROM club WHERE club_id = @cx_c18)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c19 AND left_at IS NULL) + 4 < (SELECT max_members FROM club WHERE club_id = @cx_c19))) = 14,
       ((@cx_f1 IS NOT NULL AND @cx_f1_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f1 AND sport_id = @cx_s_농구)) + (@cx_f2 IS NOT NULL AND @cx_f2_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f2 AND sport_id = @cx_s_테니스)) + (@cx_f3 IS NOT NULL AND @cx_f3_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f3 AND sport_id = @cx_s_테니스)) + (@cx_f4 IS NOT NULL AND @cx_f4_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f4 AND sport_id = @cx_s_배드민턴)) + (@cx_f5 IS NOT NULL AND @cx_f5_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f5 AND sport_id = @cx_s_배드민턴)) + (@cx_f6 IS NOT NULL AND @cx_f6_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f6 AND sport_id = @cx_s_테니스)) + (@cx_f7 IS NOT NULL AND @cx_f7_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f7 AND sport_id = @cx_s_테니스)) + (@cx_f8 IS NOT NULL AND @cx_f8_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f8 AND sport_id = @cx_s_농구)) + (@cx_f9 IS NOT NULL AND @cx_f9_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f9 AND sport_id = @cx_s_배드민턴)) + (@cx_f10 IS NOT NULL AND @cx_f10_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f10 AND sport_id = @cx_s_테니스)) + (@cx_f11 IS NOT NULL AND @cx_f11_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f11 AND sport_id = @cx_s_배드민턴)) + (@cx_f12 IS NOT NULL AND @cx_f12_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f12 AND sport_id = @cx_s_농구)) + (@cx_f13 IS NOT NULL AND @cx_f13_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f13 AND sport_id = @cx_s_테니스)) + (@cx_f14 IS NOT NULL AND @cx_f14_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f14 AND sport_id = @cx_s_배드민턴))) = 14,
       NOT EXISTS (SELECT 1 FROM social_match WHERE status <> '취소' AND (
         (facility_id = @cx_f8 AND start_at < '2026-08-01 12:00:00' AND end_at > '2026-08-01 10:00:00') OR
         (facility_id = @cx_f2 AND start_at < '2026-08-07 09:00:00' AND end_at > '2026-08-07 07:00:00') OR
         (facility_id = @cx_f5 AND start_at < '2026-08-07 22:00:00' AND end_at > '2026-08-07 20:00:00') OR
         (facility_id = @cx_f4 AND start_at < '2026-08-08 21:00:00' AND end_at > '2026-08-08 19:00:00') OR
         (facility_id = @cx_f1 AND start_at < '2026-08-09 11:00:00' AND end_at > '2026-08-09 09:00:00') OR
         (facility_id = @cx_f3 AND start_at < '2026-08-11 23:00:00' AND end_at > '2026-08-11 21:00:00') OR
         (facility_id = @cx_f5 AND start_at < '2026-08-14 22:00:00' AND end_at > '2026-08-14 20:00:00') OR
         (facility_id = @cx_f7 AND start_at < '2026-08-14 22:00:00' AND end_at > '2026-08-14 20:00:00') OR
         (facility_id = @cx_f14 AND start_at < '2026-08-15 11:00:00' AND end_at > '2026-08-15 09:00:00') OR
         (facility_id = @cx_f10 AND start_at < '2026-08-16 10:00:00' AND end_at > '2026-08-16 08:00:00') OR
         (facility_id = @cx_f11 AND start_at < '2026-08-18 22:00:00' AND end_at > '2026-08-18 20:00:00') OR
         (facility_id = @cx_f7 AND start_at < '2026-08-19 22:00:00' AND end_at > '2026-08-19 20:00:00') OR
         (facility_id = @cx_f6 AND start_at < '2026-08-24 21:30:00' AND end_at > '2026-08-24 19:30:00') OR
         (facility_id = @cx_f4 AND start_at < '2026-08-26 21:00:00' AND end_at > '2026-08-26 19:00:00') OR
         (facility_id = @cx_f6 AND start_at < '2026-08-27 21:30:00' AND end_at > '2026-08-27 19:30:00') OR
         (facility_id = @cx_f10 AND start_at < '2026-08-30 10:00:00' AND end_at > '2026-08-30 08:00:00') OR
         (facility_id = @cx_f9 AND start_at < '2026-08-30 12:00:00' AND end_at > '2026-08-30 09:00:00') OR
         (facility_id = @cx_f13 AND start_at < '2026-09-02 22:00:00' AND end_at > '2026-09-02 20:00:00') OR
         (facility_id = @cx_f2 AND start_at < '2026-09-11 09:00:00' AND end_at > '2026-09-11 07:00:00') OR
         (facility_id = @cx_f12 AND start_at < '2026-09-11 21:00:00' AND end_at > '2026-09-11 19:00:00') OR
         (facility_id = @cx_f10 AND start_at < '2026-09-12 10:00:00' AND end_at > '2026-09-12 08:00:00') OR
         (facility_id = @cx_f14 AND start_at < '2026-09-13 11:00:00' AND end_at > '2026-09-13 09:00:00') OR
         (facility_id = @cx_f6 AND start_at < '2026-09-14 21:30:00' AND end_at > '2026-09-14 19:30:00') OR
         (facility_id = @cx_f4 AND start_at < '2026-09-16 21:00:00' AND end_at > '2026-09-16 19:00:00') OR
         (facility_id = @cx_f8 AND start_at < '2026-09-19 12:00:00' AND end_at > '2026-09-19 10:00:00') OR
         (facility_id = @cx_f12 AND start_at < '2026-09-25 21:00:00' AND end_at > '2026-09-25 19:00:00') OR
         (facility_id = @cx_f5 AND start_at < '2026-09-25 22:00:00' AND end_at > '2026-09-25 20:00:00') OR
         (facility_id = @cx_f6 AND start_at < '2026-09-28 21:30:00' AND end_at > '2026-09-28 19:30:00') OR
         (facility_id = @cx_f10 AND start_at < '2026-10-03 10:00:00' AND end_at > '2026-10-03 08:00:00') OR
         (facility_id = @cx_f14 AND start_at < '2026-10-03 11:00:00' AND end_at > '2026-10-03 09:00:00') OR
         (facility_id = @cx_f9 AND start_at < '2026-10-04 12:00:00' AND end_at > '2026-10-04 09:00:00') OR
         (facility_id = @cx_f3 AND start_at < '2026-10-08 23:00:00' AND end_at > '2026-10-08 21:00:00') OR
         (facility_id = @cx_f12 AND start_at < '2026-10-09 21:00:00' AND end_at > '2026-10-09 19:00:00') OR
         (facility_id = @cx_f6 AND start_at < '2026-10-15 21:30:00' AND end_at > '2026-10-15 19:30:00') OR
         (facility_id = @cx_f11 AND start_at < '2026-10-15 22:00:00' AND end_at > '2026-10-15 20:00:00') OR
         (facility_id = @cx_f12 AND start_at < '2026-10-16 21:00:00' AND end_at > '2026-10-16 19:00:00') OR
         (facility_id = @cx_f10 AND start_at < '2026-10-17 10:00:00' AND end_at > '2026-10-17 08:00:00') OR
         (facility_id = @cx_f4 AND start_at < '2026-10-17 21:00:00' AND end_at > '2026-10-17 19:00:00') OR
         (facility_id = @cx_f12 AND start_at < '2026-10-23 21:00:00' AND end_at > '2026-10-23 19:00:00') OR
         (facility_id = @cx_f13 AND start_at < '2026-10-28 22:00:00' AND end_at > '2026-10-28 20:00:00') OR
         (facility_id = @cx_f11 AND start_at < '2026-11-03 22:00:00' AND end_at > '2026-11-03 20:00:00') OR
         (facility_id = @cx_f8 AND start_at < '2026-11-07 12:00:00' AND end_at > '2026-11-07 10:00:00') OR
         (facility_id = @cx_f1 AND start_at < '2026-12-05 11:00:00' AND end_at > '2026-12-05 09:00:00') OR
         (facility_id = @cx_f2 AND start_at < '2026-12-04 09:00:00' AND end_at > '2026-12-04 07:00:00') OR
         (facility_id = @cx_f3 AND start_at < '2026-11-26 23:00:00' AND end_at > '2026-11-26 21:00:00') OR
         (facility_id = @cx_f4 AND start_at < '2026-11-28 21:00:00' AND end_at > '2026-11-28 19:00:00') OR
         (facility_id = @cx_f5 AND start_at < '2026-12-01 22:00:00' AND end_at > '2026-12-01 20:00:00') OR
         (facility_id = @cx_f6 AND start_at < '2026-11-26 21:30:00' AND end_at > '2026-11-26 19:30:00') OR
         (facility_id = @cx_f7 AND start_at < '2026-11-25 22:00:00' AND end_at > '2026-11-25 20:00:00') OR
         (facility_id = @cx_f8 AND start_at < '2026-11-28 12:00:00' AND end_at > '2026-11-28 10:00:00') OR
         (facility_id = @cx_f9 AND start_at < '2026-12-06 12:00:00' AND end_at > '2026-12-06 09:00:00') OR
         (facility_id = @cx_f10 AND start_at < '2026-12-05 10:00:00' AND end_at > '2026-12-05 08:00:00') OR
         (facility_id = @cx_f11 AND start_at < '2026-12-01 22:00:00' AND end_at > '2026-12-01 20:00:00') OR
         (facility_id = @cx_f12 AND start_at < '2026-11-27 21:00:00' AND end_at > '2026-11-27 19:00:00') OR
         (facility_id = @cx_f13 AND start_at < '2026-11-25 22:00:00' AND end_at > '2026-11-25 20:00:00') OR
         (facility_id = @cx_f14 AND start_at < '2026-11-28 11:00:00' AND end_at > '2026-11-28 09:00:00'))),
       NOT EXISTS (SELECT 1 FROM match_application WHERE receipt_no IN ('RCPT-20260729-FFBDXDZF', 'RCPT-20260729-95P7WMSR', 'RCPT-20260729-PE4K329P', 'RCPT-20260729-4LD8WLWC', 'RCPT-20260728-SXFMYCNW', 'RCPT-20260728-WXTGA7WK', 'RCPT-20260728-WL8TCM6E', 'RCPT-20260728-3EVJ6UXD', 'RCPT-20260804-X9YRB65R', 'RCPT-20260804-7GHAGD98', 'RCPT-20260803-AE2P5LLN', 'RCPT-20260803-D2CVDKLT', 'RCPT-20260805-9UYAUNM5', 'RCPT-20260805-63RF9M77', 'RCPT-20260805-4WDEHR9M', 'RCPT-20260805-4T7KY876', 'RCPT-20260804-2RRSZWKP', 'RCPT-20260804-P28QYFRG', 'RCPT-20260804-FD9ANJ6Q', 'RCPT-20260804-SNKG2QJF', 'RCPT-20260806-CRDRUK3H', 'RCPT-20260806-6BGPLATW', 'RCPT-20260806-D8NAJ6GN', 'RCPT-20260806-RZ7B4B55', 'RCPT-20260806-YQYHDGR5', 'RCPT-20260806-S4AG7N4V', 'RCPT-20260806-V6WPQK7P', 'RCPT-20260806-RRQWY2HP', 'RCPT-20260808-SS5ZS664', 'RCPT-20260808-NGQF5UCV', 'RCPT-20260808-CJ5C3YZE', 'RCPT-20260808-LSSSC3TX', 'RCPT-20260812-YQ3ALXB3', 'RCPT-20260812-7YVCEV7D', 'RCPT-20260810-LHVF97JE', 'RCPT-20260810-NTRTKN93', 'RCPT-20260812-QADRM4L4', 'RCPT-20260812-S8GXGEVH', 'RCPT-20260811-XF3EFMUB', 'RCPT-20260811-22UVYVFZ', 'RCPT-20260813-3JMAVHNK', 'RCPT-20260813-ASNMMJ7U', 'RCPT-20260812-TY42KP22', 'RCPT-20260812-W7BVJB6P', 'RCPT-20260812-67AR7LBP', 'RCPT-20260812-PVPVPNW9', 'RCPT-20260813-2YWJR83K', 'RCPT-20260813-8ZQ795HL', 'RCPT-20260814-8FMHW38S', 'RCPT-20260814-P3M4223M', 'RCPT-20260816-M3XUL8H5', 'RCPT-20260816-8V99MQHF', 'RCPT-20260817-8N3YA8K4', 'RCPT-20260817-LQ2BNS4T', 'RCPT-20260815-T9HN8D3X', 'RCPT-20260815-VNE3XE5M', 'RCPT-20260820-UCDTYLRW', 'RCPT-20260820-6UFUQX4A', 'RCPT-20260820-7U9NVSTU', 'RCPT-20260820-9EBZA5KB', 'RCPT-20260823-UZBWBC8G', 'RCPT-20260823-H9TPDFMH', 'RCPT-20260823-L76L7ZBS', 'RCPT-20260823-9UT6TFLC', 'RCPT-20260826-YHXXY5Y6', 'RCPT-20260826-UNVGKMV2', 'RCPT-20260824-N5C7H37Y', 'RCPT-20260824-QFFFR78E', 'RCPT-20260825-4THM5QF6', 'RCPT-20260825-8ANMAKU3', 'RCPT-20260827-R3B9MWXU', 'RCPT-20260827-W7UREAUG', 'RCPT-20260828-DW49WURF', 'RCPT-20260828-L3P9WAUF', 'RCPT-20260827-NAXWD9QA', 'RCPT-20260827-DW8NXG4C', 'RCPT-20260829-JZ6K4QTC', 'RCPT-20260829-7E4UPP9C', 'RCPT-20260830-WMEYLEEE', 'RCPT-20260830-PCMARLU6', 'RCPT-20260906-M3RAVP6J', 'RCPT-20260906-29WVTSLQ', 'RCPT-20260909-75CJNURF', 'RCPT-20260909-MZTLWLEP', 'RCPT-20260909-EVC62H7W', 'RCPT-20260909-6EJZDQAE', 'RCPT-20260909-JB9VD52Z', 'RCPT-20260909-5HDH3JCQ', 'RCPT-20260907-LKVBVYWA', 'RCPT-20260907-J4YVLGCP', 'RCPT-20260907-R4WB72B8', 'RCPT-20260907-A5BJN67E', 'RCPT-20260908-XE3DGP77', 'RCPT-20260908-EXRZUBKP', 'RCPT-20260908-VPSGTVM8', 'RCPT-20260908-L23CBKFV', 'RCPT-20260911-LZ95KQ93', 'RCPT-20260911-XKF9449L', 'RCPT-20260908-UK55HV9A', 'RCPT-20260908-5ENE38TP', 'RCPT-20260911-D4FPU6HG', 'RCPT-20260911-9LF4FK52', 'RCPT-20260912-DJE47QW5', 'RCPT-20260912-ZQVC32PM', 'RCPT-20260914-E55M6D4K', 'RCPT-20260914-U9GM4J2U', 'RCPT-20260914-FRYT39YF', 'RCPT-20260914-GBJF3EL6', 'RCPT-20260915-EBX2DYST', 'RCPT-20260915-44A9RSZS', 'RCPT-20260915-CSTRYCQX', 'RCPT-20260915-MA5AAGNS', 'RCPT-20260916-PQEJ7ZHE', 'RCPT-20260916-CV36ST64', 'RCPT-20260916-Z44TVQLF', 'RCPT-20260916-BH3XT8JV', 'RCPT-20260922-U5JE9H7R', 'RCPT-20260922-3XKQHW9M', 'RCPT-20260922-ACSXRKPA', 'RCPT-20260922-YB7WF5T4', 'RCPT-20260922-AEUSPPHL', 'RCPT-20260922-JTK274M8', 'RCPT-20260922-CVFE5P48', 'RCPT-20260922-AHNMUW2H', 'RCPT-20260921-DQJNW4HU', 'RCPT-20260921-6U4PWNYC', 'RCPT-20260921-QWEPPN23', 'RCPT-20260921-UDQ548A7', 'RCPT-20260925-NS8KZNYZ', 'RCPT-20260925-NF6NM63M', 'RCPT-20260926-2NYS8JTB', 'RCPT-20260926-HMNN8T8A', 'RCPT-20260929-5YZT87YG', 'RCPT-20260929-5KTLMUUA', 'RCPT-20261001-MERRPG9F', 'RCPT-20261001-4U8LRQJ4', 'RCPT-20261001-3JEVGQJJ', 'RCPT-20261001-Y6C87UR2', 'RCPT-20261001-UHJB69QK', 'RCPT-20261001-LKBSC8EH', 'RCPT-20261002-PJ7XM4FF', 'RCPT-20261002-3H79UB64', 'RCPT-20260930-J9MEX4VE', 'RCPT-20260930-HAE6CLAP', 'RCPT-20261006-MLF78643', 'RCPT-20261006-86LQXAEQ', 'RCPT-20261004-TBTTUDRQ', 'RCPT-20261004-QCZCTLQ9', 'RCPT-20261006-EZH25R8B', 'RCPT-20261006-VP7QG4WH', 'RCPT-20261006-MM2WBENV', 'RCPT-20261006-8MZBFKCM', 'RCPT-20261005-WLEFFMS4', 'RCPT-20261005-WZ8R8GEW', 'RCPT-20261005-87XC6NBU', 'RCPT-20261005-F9Z3BZFM', 'RCPT-20261011-LE9HCMMF', 'RCPT-20261011-MZUQADHQ', 'RCPT-20261012-YL6UNW5W', 'RCPT-20261012-RR2GRW6X', 'RCPT-20261012-VX2TZMFP', 'RCPT-20261012-Q7G2XX63', 'RCPT-20261013-Z76J5ZRT', 'RCPT-20261013-ZJMXA5XM', 'RCPT-20261013-T4B4SY8S', 'RCPT-20261013-8FPCAD2B', 'RCPT-20261013-C3N27AE8', 'RCPT-20261013-UM9UN4NB', 'RCPT-20261012-AX632WYJ', 'RCPT-20261012-RQPNX6M9', 'RCPT-20261012-5H5L8GRP', 'RCPT-20261012-9FNALW3K', 'RCPT-20261015-KML9X22Z', 'RCPT-20261015-D8QG3994', 'RCPT-20261014-NCAM8CMY', 'RCPT-20261014-AN4A2GEA', 'RCPT-20261014-EF67LSV4', 'RCPT-20261014-273DAC2J', 'RCPT-20261013-JCV98A93', 'RCPT-20261013-8RJVW7ES', 'RCPT-20261019-WV87BJC4', 'RCPT-20261019-9H6CYKFX', 'RCPT-20261019-R8SEGUU4', 'RCPT-20261019-8BY6W2A3', 'RCPT-20261020-DLRYAYSB', 'RCPT-20261020-W2XU6H6X', 'RCPT-20261020-72SDL6GP', 'RCPT-20261020-B5VKDLM8', 'RCPT-20261026-H5MTJSCD', 'RCPT-20261026-LSV2YLY7', 'RCPT-20261024-Q9BXNCFA', 'RCPT-20261024-5LPFUQXC', 'RCPT-20261030-LGCX9LHX', 'RCPT-20261030-PWC5U3L4', 'RCPT-20261031-YFWY5NBY', 'RCPT-20261031-EMABE5TE', 'RCPT-20261103-CF3W3PYD', 'RCPT-20261103-396UUCP2', 'RCPT-20261103-Y9QV3J97', 'RCPT-20261103-7JQYD2EM', 'RCPT-20261105-SM2FR24W', 'RCPT-20261105-LFGS4C88', 'RCPT-20261105-CP9TKEGC', 'RCPT-20261105-PBD9H4A4', 'RCPT-20261112-U76B7L9L', 'RCPT-20261112-J9MV9XJC', 'RCPT-20261112-NC5DPKCW', 'RCPT-20261112-3MLB36WA', 'RCPT-20261112-M9NS8AZL', 'RCPT-20261112-P5YLMHHY', 'RCPT-20261112-CB4R5X55', 'RCPT-20261112-3A2YSAGD', 'RCPT-20261112-W4V6KZD3', 'RCPT-20261112-T2HDMV9U', 'RCPT-20261112-7FBXEK4A', 'RCPT-20261112-CJYPRP32', 'RCPT-20261112-2JWEL8Y5', 'RCPT-20261112-CJA2AHUQ', 'RCPT-20261112-KFQM9BHY', 'RCPT-20261112-PTDGEXPV', 'RCPT-20261112-NU2QWSC5', 'RCPT-20261112-2UD5YFAD', 'RCPT-20261112-XXK4WASH', 'RCPT-20261112-7RR3WM5A', 'RCPT-20261112-PCY88EFS', 'RCPT-20261112-C6KWNWXE', 'RCPT-20261112-SF3Y6TKT', 'RCPT-20261112-XSSYRCKD', 'RCPT-20261112-5TPB4LK3', 'RCPT-20261112-MTQ7ZTMH', 'RCPT-20261112-LZ39CUH7', 'RCPT-20261112-RDNEWRJM', 'RCPT-20261112-XX3KNFZX', 'RCPT-20261112-9KN64VCE', 'RCPT-20261112-3T33VSZX', 'RCPT-20261112-M8ERZWGF', 'RCPT-20261112-7FMZT4FC', 'RCPT-20261112-QHD4USRA')),
       NOT EXISTS (SELECT 1 FROM app_user WHERE email IN ('club-user-002@spoit.test', 'club-user-003@spoit.test', 'club-user-004@spoit.test', 'club-user-005@spoit.test', 'club-user-006@spoit.test', 'club-user-007@spoit.test', 'club-user-008@spoit.test', 'club-user-009@spoit.test', 'club-user-010@spoit.test', 'club-user-011@spoit.test', 'club-user-012@spoit.test', 'club-user-013@spoit.test', 'club-user-014@spoit.test', 'club-user-015@spoit.test', 'club-user-016@spoit.test', 'club-user-017@spoit.test', 'club-user-018@spoit.test', 'club-user-019@spoit.test', 'club-user-020@spoit.test', 'club-user-021@spoit.test', 'club-user-022@spoit.test', 'club-user-023@spoit.test', 'club-user-024@spoit.test', 'club-user-025@spoit.test', 'club-user-026@spoit.test', 'club-user-027@spoit.test', 'club-user-028@spoit.test', 'club-user-029@spoit.test', 'club-user-030@spoit.test', 'club-user-031@spoit.test', 'club-user-032@spoit.test', 'club-user-033@spoit.test', 'club-user-034@spoit.test', 'club-user-035@spoit.test', 'club-user-036@spoit.test', 'club-user-037@spoit.test', 'club-user-038@spoit.test', 'club-user-039@spoit.test', 'club-user-040@spoit.test', 'club-user-041@spoit.test', 'club-user-042@spoit.test', 'club-user-043@spoit.test', 'club-user-044@spoit.test', 'club-user-045@spoit.test', 'club-user-046@spoit.test', 'club-user-047@spoit.test', 'club-user-048@spoit.test', 'club-user-049@spoit.test', 'club-user-050@spoit.test', 'club-user-051@spoit.test', 'club-user-052@spoit.test', 'club-user-053@spoit.test', 'club-user-054@spoit.test', 'club-user-055@spoit.test', 'club-user-056@spoit.test', 'club-user-057@spoit.test', 'club-user-058@spoit.test', 'club-user-059@spoit.test', 'club-user-060@spoit.test', 'club-user-061@spoit.test', 'club-user-062@spoit.test')),
       EXISTS (SELECT 1 FROM app_user WHERE email = 'club-user-001@spoit.test')
  INTO @cx_sport, @cx_districts, @cx_clubs, @cx_capacity, @cx_facilities, @cx_slots_free, @cx_receipts_free, @cx_emails_free, @cx_already_applied;
SET @cx_checks = @cx_sport AND @cx_districts AND @cx_clubs AND @cx_capacity AND @cx_facilities AND @cx_slots_free AND @cx_receipts_free AND @cx_emails_free;
SET @cx_ready = @cx_checks AND NOT @cx_already_applied;
SELECT @cx_sport AS sport, @cx_districts AS districts, @cx_clubs AS clubs, @cx_capacity AS capacity, @cx_facilities AS facilities,
       @cx_slots_free AS slots_free, @cx_receipts_free AS receipts_free, @cx_emails_free AS emails_free,
       @cx_already_applied AS already_applied;
-- 이미 적용된 DB가 아닌데 확인에 실패하면 "Subquery returns more than 1 row" 오류로 멈춘다.
DO (SELECT 1 UNION ALL SELECT 1 FROM DUAL WHERE @cx_checks = 0 AND @cx_already_applied = 0);

SELECT MAX(user_id), (SELECT COALESCE(MAX(club_member_id), 0) FROM club_member),
       (SELECT COALESCE(MAX(match_id), 0) FROM social_match), (SELECT COALESCE(MAX(application_id), 0) FROM match_application),
       (SELECT COALESCE(MAX(participant_id), 0) FROM match_participant)
  INTO @cx_u, @cx_m, @cx_sm, @cx_a, @cx_p
  FROM app_user;

-- 1. 새 회원 (비밀번호 spoit1234)
INSERT INTO app_user (user_id, email, password_hash, nickname, phone, role, created_at)
SELECT * FROM (
                 SELECT (@cx_u + 1) AS user_id, 'club-user-001@spoit.test' AS email, '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi' AS password_hash, '류태윤' AS nickname, '010-8701-1830' AS phone, 'MEMBER' AS role, '2026-04-23 10:30:00' AS created_at
       UNION ALL SELECT (@cx_u + 2), 'club-user-002@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '조수아', '010-8702-4409', 'MEMBER', '2026-04-30 19:30:00'
       UNION ALL SELECT (@cx_u + 3), 'club-user-003@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '남지우', '010-8703-3352', 'MEMBER', '2026-05-11 15:30:00'
       UNION ALL SELECT (@cx_u + 4), 'club-user-004@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '오건우', '010-8704-9828', 'MEMBER', '2026-05-17 11:00:00'
       UNION ALL SELECT (@cx_u + 5), 'club-user-005@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '안준서', '010-8705-9043', 'MEMBER', '2026-06-20 12:00:00'
       UNION ALL SELECT (@cx_u + 6), 'club-user-006@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '김태현', '010-8706-9505', 'MEMBER', '2026-06-30 08:30:00'
       UNION ALL SELECT (@cx_u + 7), 'club-user-007@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '문유준', '010-8707-9294', 'MEMBER', '2026-04-11 18:30:00'
       UNION ALL SELECT (@cx_u + 8), 'club-user-008@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '배승우', '010-8708-6816', 'MEMBER', '2026-05-10 11:30:00'
       UNION ALL SELECT (@cx_u + 9), 'club-user-009@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '허연우', '010-8709-6995', 'MEMBER', '2026-06-03 16:00:00'
       UNION ALL SELECT (@cx_u + 10), 'club-user-010@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '황주원', '010-8710-9324', 'MEMBER', '2026-06-11 20:30:00'
       UNION ALL SELECT (@cx_u + 11), 'club-user-011@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '한수아', '010-8711-6182', 'MEMBER', '2026-06-17 12:00:00'
       UNION ALL SELECT (@cx_u + 12), 'club-user-012@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '백하린', '010-8712-6300', 'MEMBER', '2026-06-26 12:00:00'
       UNION ALL SELECT (@cx_u + 13), 'club-user-013@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '정도현', '010-8713-9469', 'MEMBER', '2026-07-03 10:30:00'
       UNION ALL SELECT (@cx_u + 14), 'club-user-014@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '윤경민', '010-8714-2291', 'MEMBER', '2026-07-06 17:00:00'
       UNION ALL SELECT (@cx_u + 15), 'club-user-015@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '오지훈', '010-8715-6620', 'MEMBER', '2026-04-29 06:30:00'
       UNION ALL SELECT (@cx_u + 16), 'club-user-016@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '허예린', '010-8716-4157', 'MEMBER', '2026-06-03 06:00:00'
       UNION ALL SELECT (@cx_u + 17), 'club-user-017@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '조승현', '010-8717-8171', 'MEMBER', '2026-05-25 17:30:00'
       UNION ALL SELECT (@cx_u + 18), 'club-user-018@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '황준혁', '010-8718-4683', 'MEMBER', '2026-06-10 18:30:00'
       UNION ALL SELECT (@cx_u + 19), 'club-user-019@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '윤민준', '010-8719-5601', 'MEMBER', '2026-04-09 10:00:00'
       UNION ALL SELECT (@cx_u + 20), 'club-user-020@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '정유준', '010-8720-1992', 'MEMBER', '2026-04-22 15:00:00'
       UNION ALL SELECT (@cx_u + 21), 'club-user-021@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '김재민', '010-8721-6560', 'MEMBER', '2026-05-24 10:00:00'
       UNION ALL SELECT (@cx_u + 22), 'club-user-022@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '황준우', '010-8722-3891', 'MEMBER', '2026-06-30 14:00:00'
       UNION ALL SELECT (@cx_u + 23), 'club-user-023@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '양다은', '010-8723-6662', 'MEMBER', '2026-05-10 14:00:00'
       UNION ALL SELECT (@cx_u + 24), 'club-user-024@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '권재민', '010-8724-7990', 'MEMBER', '2026-05-19 12:00:00'
       UNION ALL SELECT (@cx_u + 25), 'club-user-025@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '송지후', '010-8725-8104', 'MEMBER', '2026-05-26 09:30:00'
       UNION ALL SELECT (@cx_u + 26), 'club-user-026@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '백정우', '010-8726-4963', 'MEMBER', '2026-06-09 08:00:00'
       UNION ALL SELECT (@cx_u + 27), 'club-user-027@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '임지호', '010-8727-2816', 'MEMBER', '2026-04-20 14:00:00'
       UNION ALL SELECT (@cx_u + 28), 'club-user-028@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '장연우', '010-8728-3093', 'MEMBER', '2026-04-25 12:00:00'
       UNION ALL SELECT (@cx_u + 29), 'club-user-029@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '문서진', '010-8729-6291', 'MEMBER', '2026-07-03 06:30:00'
       UNION ALL SELECT (@cx_u + 30), 'club-user-030@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '황하준', '010-8730-3832', 'MEMBER', '2026-07-11 13:00:00'
       UNION ALL SELECT (@cx_u + 31), 'club-user-031@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '유지환', '010-8731-2340', 'MEMBER', '2026-04-26 07:00:00'
       UNION ALL SELECT (@cx_u + 32), 'club-user-032@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '오성민', '010-8732-6313', 'MEMBER', '2026-04-22 17:30:00'
       UNION ALL SELECT (@cx_u + 33), 'club-user-033@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '강연우', '010-8733-1876', 'MEMBER', '2026-04-13 03:30:00'
       UNION ALL SELECT (@cx_u + 34), 'club-user-034@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '유서연', '010-8734-5090', 'MEMBER', '2026-05-06 17:00:00'
       UNION ALL SELECT (@cx_u + 35), 'club-user-035@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '양준혁', '010-8735-3414', 'MEMBER', '2026-05-11 10:30:00'
       UNION ALL SELECT (@cx_u + 36), 'club-user-036@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '서연우', '010-8736-6401', 'MEMBER', '2026-06-27 14:00:00'
       UNION ALL SELECT (@cx_u + 37), 'club-user-037@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '윤민서', '010-8737-7085', 'MEMBER', '2026-05-07 19:00:00'
       UNION ALL SELECT (@cx_u + 38), 'club-user-038@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '최영호', '010-8738-3273', 'MEMBER', '2026-05-22 08:30:00'
       UNION ALL SELECT (@cx_u + 39), 'club-user-039@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '서경민', '010-8739-3717', 'MEMBER', '2026-06-13 10:30:00'
       UNION ALL SELECT (@cx_u + 40), 'club-user-040@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '남태현', '010-8740-2672', 'MEMBER', '2026-07-20 19:30:00'
       UNION ALL SELECT (@cx_u + 41), 'club-user-041@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '류하린', '010-8741-2582', 'MEMBER', '2026-04-29 14:00:00'
       UNION ALL SELECT (@cx_u + 42), 'club-user-042@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '허지후', '010-8742-4512', 'MEMBER', '2026-07-08 13:30:00'
       UNION ALL SELECT (@cx_u + 43), 'club-user-043@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '임채원', '010-8743-8413', 'MEMBER', '2026-07-18 13:00:00'
       UNION ALL SELECT (@cx_u + 44), 'club-user-044@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '양예린', '010-8744-4751', 'MEMBER', '2026-07-18 12:30:00'
       UNION ALL SELECT (@cx_u + 45), 'club-user-045@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '백은우', '010-8745-5622', 'MEMBER', '2026-05-07 12:30:00'
       UNION ALL SELECT (@cx_u + 46), 'club-user-046@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '홍진수', '010-8746-8225', 'MEMBER', '2026-06-01 09:00:00'
       UNION ALL SELECT (@cx_u + 47), 'club-user-047@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '류서준', '010-8747-8782', 'MEMBER', '2026-06-07 12:00:00'
       UNION ALL SELECT (@cx_u + 48), 'club-user-048@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '배현우', '010-8748-7268', 'MEMBER', '2026-06-28 06:30:00'
       UNION ALL SELECT (@cx_u + 49), 'club-user-049@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '장서윤', '010-8749-3217', 'MEMBER', '2026-04-29 09:00:00'
       UNION ALL SELECT (@cx_u + 50), 'club-user-050@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '강예린', '010-8750-9114', 'MEMBER', '2026-05-09 17:30:00'
       UNION ALL SELECT (@cx_u + 51), 'club-user-051@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '조시윤', '010-8751-6279', 'MEMBER', '2026-05-24 19:00:00'
       UNION ALL SELECT (@cx_u + 52), 'club-user-052@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '류하준', '010-8752-7129', 'MEMBER', '2026-06-09 17:30:00'
       UNION ALL SELECT (@cx_u + 53), 'club-user-053@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '강시윤', '010-8753-1121', 'MEMBER', '2026-06-22 03:30:00'
       UNION ALL SELECT (@cx_u + 54), 'club-user-054@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '권지후', '010-8754-5732', 'MEMBER', '2026-06-22 11:30:00'
       UNION ALL SELECT (@cx_u + 55), 'club-user-055@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '윤지호', '010-8755-3309', 'MEMBER', '2026-04-04 16:00:00'
       UNION ALL SELECT (@cx_u + 56), 'club-user-056@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '서대현', '010-8756-9400', 'MEMBER', '2026-06-19 12:00:00'
       UNION ALL SELECT (@cx_u + 57), 'club-user-057@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '홍지환', '010-8757-4152', 'MEMBER', '2026-06-11 06:00:00'
       UNION ALL SELECT (@cx_u + 58), 'club-user-058@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '배영호', '010-8758-4176', 'MEMBER', '2026-06-28 07:00:00'
       UNION ALL SELECT (@cx_u + 59), 'club-user-059@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '서예은', '010-8759-4498', 'MEMBER', '2026-06-15 01:00:00'
       UNION ALL SELECT (@cx_u + 60), 'club-user-060@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '한서준', '010-8760-9181', 'MEMBER', '2026-07-09 09:00:00'
       UNION ALL SELECT (@cx_u + 61), 'club-user-061@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '오동현', '010-8761-4233', 'MEMBER', '2026-07-09 09:30:00'
       UNION ALL SELECT (@cx_u + 62), 'club-user-062@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '안지우', '010-8762-9795', 'MEMBER', '2026-07-04 15:00:00'
) v
 WHERE @cx_ready = 1;

INSERT INTO user_region (user_id, region_id)
SELECT * FROM (
                 SELECT (@cx_u + 1) AS user_id, @cx_r_노원구 AS region_id
       UNION ALL SELECT (@cx_u + 2), @cx_r_노원구
       UNION ALL SELECT (@cx_u + 3), @cx_r_노원구
       UNION ALL SELECT (@cx_u + 4), @cx_r_노원구
       UNION ALL SELECT (@cx_u + 5), @cx_r_노원구
       UNION ALL SELECT (@cx_u + 6), @cx_r_노원구
       UNION ALL SELECT (@cx_u + 7), @cx_r_마포구
       UNION ALL SELECT (@cx_u + 8), @cx_r_마포구
       UNION ALL SELECT (@cx_u + 9), @cx_r_마포구
       UNION ALL SELECT (@cx_u + 10), @cx_r_마포구
       UNION ALL SELECT (@cx_u + 11), @cx_r_서초구
       UNION ALL SELECT (@cx_u + 12), @cx_r_서초구
       UNION ALL SELECT (@cx_u + 13), @cx_r_서초구
       UNION ALL SELECT (@cx_u + 14), @cx_r_서초구
       UNION ALL SELECT (@cx_u + 15), @cx_r_성동구
       UNION ALL SELECT (@cx_u + 16), @cx_r_성동구
       UNION ALL SELECT (@cx_u + 17), @cx_r_성동구
       UNION ALL SELECT (@cx_u + 18), @cx_r_성동구
       UNION ALL SELECT (@cx_u + 19), @cx_r_광진구
       UNION ALL SELECT (@cx_u + 20), @cx_r_광진구
       UNION ALL SELECT (@cx_u + 21), @cx_r_광진구
       UNION ALL SELECT (@cx_u + 22), @cx_r_광진구
       UNION ALL SELECT (@cx_u + 23), @cx_r_서초구
       UNION ALL SELECT (@cx_u + 24), @cx_r_서초구
       UNION ALL SELECT (@cx_u + 25), @cx_r_서초구
       UNION ALL SELECT (@cx_u + 26), @cx_r_서초구
       UNION ALL SELECT (@cx_u + 27), @cx_r_영등포구
       UNION ALL SELECT (@cx_u + 28), @cx_r_영등포구
       UNION ALL SELECT (@cx_u + 29), @cx_r_영등포구
       UNION ALL SELECT (@cx_u + 30), @cx_r_영등포구
       UNION ALL SELECT (@cx_u + 31), @cx_r_송파구
       UNION ALL SELECT (@cx_u + 32), @cx_r_송파구
       UNION ALL SELECT (@cx_u + 33), @cx_r_송파구
       UNION ALL SELECT (@cx_u + 34), @cx_r_송파구
       UNION ALL SELECT (@cx_u + 35), @cx_r_송파구
       UNION ALL SELECT (@cx_u + 36), @cx_r_송파구
       UNION ALL SELECT (@cx_u + 37), @cx_r_송파구
       UNION ALL SELECT (@cx_u + 38), @cx_r_송파구
       UNION ALL SELECT (@cx_u + 39), @cx_r_송파구
       UNION ALL SELECT (@cx_u + 40), @cx_r_송파구
       UNION ALL SELECT (@cx_u + 41), @cx_r_광진구
       UNION ALL SELECT (@cx_u + 42), @cx_r_광진구
       UNION ALL SELECT (@cx_u + 43), @cx_r_광진구
       UNION ALL SELECT (@cx_u + 44), @cx_r_광진구
       UNION ALL SELECT (@cx_u + 45), @cx_r_강서구
       UNION ALL SELECT (@cx_u + 46), @cx_r_강서구
       UNION ALL SELECT (@cx_u + 47), @cx_r_강서구
       UNION ALL SELECT (@cx_u + 48), @cx_r_강서구
       UNION ALL SELECT (@cx_u + 49), @cx_r_영등포구
       UNION ALL SELECT (@cx_u + 50), @cx_r_영등포구
       UNION ALL SELECT (@cx_u + 51), @cx_r_영등포구
       UNION ALL SELECT (@cx_u + 52), @cx_r_영등포구
       UNION ALL SELECT (@cx_u + 53), @cx_r_영등포구
       UNION ALL SELECT (@cx_u + 54), @cx_r_영등포구
       UNION ALL SELECT (@cx_u + 55), @cx_r_강서구
       UNION ALL SELECT (@cx_u + 56), @cx_r_강서구
       UNION ALL SELECT (@cx_u + 57), @cx_r_강서구
       UNION ALL SELECT (@cx_u + 58), @cx_r_강서구
       UNION ALL SELECT (@cx_u + 59), @cx_r_송파구
       UNION ALL SELECT (@cx_u + 60), @cx_r_송파구
       UNION ALL SELECT (@cx_u + 61), @cx_r_송파구
       UNION ALL SELECT (@cx_u + 62), @cx_r_송파구
) v
 WHERE @cx_ready = 1;

INSERT INTO user_preferred_sport (user_id, sport_id)
SELECT * FROM (
                 SELECT (@cx_u + 1) AS user_id, @cx_s_농구 AS sport_id
       UNION ALL SELECT (@cx_u + 2), @cx_s_농구
       UNION ALL SELECT (@cx_u + 3), @cx_s_농구
       UNION ALL SELECT (@cx_u + 4), @cx_s_농구
       UNION ALL SELECT (@cx_u + 5), @cx_s_농구
       UNION ALL SELECT (@cx_u + 6), @cx_s_농구
       UNION ALL SELECT (@cx_u + 7), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 8), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 9), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 10), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 11), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 12), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 13), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 14), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 15), @cx_s_배드민턴
       UNION ALL SELECT (@cx_u + 16), @cx_s_배드민턴
       UNION ALL SELECT (@cx_u + 17), @cx_s_배드민턴
       UNION ALL SELECT (@cx_u + 18), @cx_s_배드민턴
       UNION ALL SELECT (@cx_u + 19), @cx_s_배드민턴
       UNION ALL SELECT (@cx_u + 20), @cx_s_배드민턴
       UNION ALL SELECT (@cx_u + 21), @cx_s_배드민턴
       UNION ALL SELECT (@cx_u + 22), @cx_s_배드민턴
       UNION ALL SELECT (@cx_u + 23), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 24), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 25), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 26), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 27), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 28), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 29), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 30), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 31), @cx_s_농구
       UNION ALL SELECT (@cx_u + 32), @cx_s_농구
       UNION ALL SELECT (@cx_u + 33), @cx_s_농구
       UNION ALL SELECT (@cx_u + 34), @cx_s_농구
       UNION ALL SELECT (@cx_u + 35), @cx_s_농구
       UNION ALL SELECT (@cx_u + 36), @cx_s_농구
       UNION ALL SELECT (@cx_u + 37), @cx_s_배드민턴
       UNION ALL SELECT (@cx_u + 38), @cx_s_배드민턴
       UNION ALL SELECT (@cx_u + 39), @cx_s_배드민턴
       UNION ALL SELECT (@cx_u + 40), @cx_s_배드민턴
       UNION ALL SELECT (@cx_u + 41), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 42), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 43), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 44), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 45), @cx_s_배드민턴
       UNION ALL SELECT (@cx_u + 46), @cx_s_배드민턴
       UNION ALL SELECT (@cx_u + 47), @cx_s_배드민턴
       UNION ALL SELECT (@cx_u + 48), @cx_s_배드민턴
       UNION ALL SELECT (@cx_u + 49), @cx_s_농구
       UNION ALL SELECT (@cx_u + 50), @cx_s_농구
       UNION ALL SELECT (@cx_u + 51), @cx_s_농구
       UNION ALL SELECT (@cx_u + 52), @cx_s_농구
       UNION ALL SELECT (@cx_u + 53), @cx_s_농구
       UNION ALL SELECT (@cx_u + 54), @cx_s_농구
       UNION ALL SELECT (@cx_u + 55), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 56), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 57), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 58), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 59), @cx_s_배드민턴
       UNION ALL SELECT (@cx_u + 60), @cx_s_배드민턴
       UNION ALL SELECT (@cx_u + 61), @cx_s_배드민턴
       UNION ALL SELECT (@cx_u + 62), @cx_s_배드민턴
) v
 WHERE @cx_ready = 1;

-- 종목별 레이팅 = 마지막 레이팅 이력. rated_match_count가 5 이상이면 배치 완료로 팀 등급 평균에 들어간다.
INSERT INTO sport_rating (user_id, sport_id, current_mmr, rated_match_count, updated_at)
SELECT * FROM (
                 SELECT (@cx_u + 1) AS user_id, @cx_s_농구 AS sport_id, 939.58 AS current_mmr, 6 AS rated_match_count, '2026-11-08 12:00:00' AS updated_at
       UNION ALL SELECT (@cx_u + 2), @cx_s_농구, 939.58, 6, '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_u + 3), @cx_s_농구, 941.41, 3, '2026-10-24 21:00:00'
       UNION ALL SELECT (@cx_u + 4), @cx_s_농구, 941.41, 3, '2026-10-24 21:00:00'
       UNION ALL SELECT (@cx_u + 5), @cx_s_농구, 1019.77, 3, '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_u + 6), @cx_s_농구, 1019.77, 3, '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_u + 7), @cx_s_테니스, 985.74, 6, '2026-10-18 10:00:00'
       UNION ALL SELECT (@cx_u + 8), @cx_s_테니스, 999.80, 2, '2026-09-12 09:00:00'
       UNION ALL SELECT (@cx_u + 9), @cx_s_테니스, 1003.11, 2, '2026-10-09 23:00:00'
       UNION ALL SELECT (@cx_u + 10), @cx_s_테니스, 999.13, 2, '2026-10-18 10:00:00'
       UNION ALL SELECT (@cx_u + 11), @cx_s_테니스, 950.11, 6, '2026-10-16 21:30:00'
       UNION ALL SELECT (@cx_u + 12), @cx_s_테니스, 1063.86, 2, '2026-09-13 10:00:00'
       UNION ALL SELECT (@cx_u + 13), @cx_s_테니스, 939.00, 2, '2026-10-16 21:30:00'
       UNION ALL SELECT (@cx_u + 14), @cx_s_테니스, 932.75, 2, '2026-10-09 23:00:00'
       UNION ALL SELECT (@cx_u + 15), @cx_s_배드민턴, 957.88, 6, '2026-10-18 21:00:00'
       UNION ALL SELECT (@cx_u + 16), @cx_s_배드민턴, 1003.68, 2, '2026-10-18 21:00:00'
       UNION ALL SELECT (@cx_u + 17), @cx_s_배드민턴, 995.89, 2, '2026-10-05 12:00:00'
       UNION ALL SELECT (@cx_u + 18), @cx_s_배드민턴, 943.42, 2, '2026-09-17 21:00:00'
       UNION ALL SELECT (@cx_u + 19), @cx_s_배드민턴, 1047.98, 6, '2026-10-16 22:00:00'
       UNION ALL SELECT (@cx_u + 20), @cx_s_배드민턴, 968.93, 2, '2026-10-16 22:00:00'
       UNION ALL SELECT (@cx_u + 21), @cx_s_배드민턴, 1058.79, 2, '2026-09-26 22:00:00'
       UNION ALL SELECT (@cx_u + 22), @cx_s_배드민턴, 1004.73, 2, '2026-10-04 11:00:00'
       UNION ALL SELECT (@cx_u + 23), @cx_s_테니스, 1046.03, 6, '2026-10-16 21:30:00'
       UNION ALL SELECT (@cx_u + 24), @cx_s_테니스, 1033.33, 2, '2026-09-29 21:30:00'
       UNION ALL SELECT (@cx_u + 25), @cx_s_테니스, 1031.67, 2, '2026-09-15 21:30:00'
       UNION ALL SELECT (@cx_u + 26), @cx_s_테니스, 995.53, 2, '2026-10-16 21:30:00'
       UNION ALL SELECT (@cx_u + 27), @cx_s_테니스, 1058.79, 6, '2026-10-29 22:00:00'
       UNION ALL SELECT (@cx_u + 28), @cx_s_테니스, 1057.00, 2, '2026-10-04 10:00:00'
       UNION ALL SELECT (@cx_u + 29), @cx_s_테니스, 968.33, 2, '2026-09-15 21:30:00'
       UNION ALL SELECT (@cx_u + 30), @cx_s_테니스, 1033.46, 2, '2026-10-29 22:00:00'
       UNION ALL SELECT (@cx_u + 31), @cx_s_농구, 1085.64, 6, '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_u + 32), @cx_s_농구, 1085.64, 6, '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_u + 33), @cx_s_농구, 1016.52, 3, '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_u + 34), @cx_s_농구, 980.23, 3, '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_u + 35), @cx_s_농구, 1083.81, 3, '2026-10-17 21:00:00'
       UNION ALL SELECT (@cx_u + 36), @cx_s_농구, 1047.52, 3, '2026-10-17 21:00:00'
       UNION ALL SELECT (@cx_u + 37), @cx_s_배드민턴, 879.90, 6, '2026-11-04 22:00:00'
       UNION ALL SELECT (@cx_u + 38), @cx_s_배드민턴, 938.50, 2, '2026-10-05 12:00:00'
       UNION ALL SELECT (@cx_u + 39), @cx_s_배드민턴, 1017.72, 2, '2026-11-04 22:00:00'
       UNION ALL SELECT (@cx_u + 40), @cx_s_배드민턴, 945.76, 2, '2026-09-26 22:00:00'
       UNION ALL SELECT (@cx_u + 41), @cx_s_테니스, 899.32, 6, '2026-10-18 10:00:00'
       UNION ALL SELECT (@cx_u + 42), @cx_s_테니스, 1000.87, 2, '2026-10-18 10:00:00'
       UNION ALL SELECT (@cx_u + 43), @cx_s_테니스, 943.14, 2, '2026-10-04 10:00:00'
       UNION ALL SELECT (@cx_u + 44), @cx_s_테니스, 939.01, 2, '2026-09-13 10:00:00'
       UNION ALL SELECT (@cx_u + 45), @cx_s_배드민턴, 1060.62, 6, '2026-11-04 22:00:00'
       UNION ALL SELECT (@cx_u + 46), @cx_s_배드민턴, 993.19, 2, '2026-09-14 11:00:00'
       UNION ALL SELECT (@cx_u + 47), @cx_s_배드민턴, 982.28, 2, '2026-11-04 22:00:00'
       UNION ALL SELECT (@cx_u + 48), @cx_s_배드민턴, 1063.07, 2, '2026-10-16 22:00:00'
       UNION ALL SELECT (@cx_u + 49), @cx_s_농구, 958.86, 6, '2026-10-24 21:00:00'
       UNION ALL SELECT (@cx_u + 50), @cx_s_농구, 958.86, 6, '2026-10-24 21:00:00'
       UNION ALL SELECT (@cx_u + 51), @cx_s_농구, 978.65, 3, '2026-10-17 21:00:00'
       UNION ALL SELECT (@cx_u + 52), @cx_s_농구, 972.18, 3, '2026-10-24 21:00:00'
       UNION ALL SELECT (@cx_u + 53), @cx_s_농구, 1058.59, 3, '2026-10-24 21:00:00'
       UNION ALL SELECT (@cx_u + 54), @cx_s_농구, 940.14, 3, '2026-10-17 21:00:00'
       UNION ALL SELECT (@cx_u + 55), @cx_s_테니스, 1060.01, 6, '2026-10-29 22:00:00'
       UNION ALL SELECT (@cx_u + 56), @cx_s_테니스, 1030.81, 2, '2026-09-29 21:30:00'
       UNION ALL SELECT (@cx_u + 57), @cx_s_테니스, 997.19, 2, '2026-09-12 09:00:00'
       UNION ALL SELECT (@cx_u + 58), @cx_s_테니스, 1032.01, 2, '2026-10-29 22:00:00'
       UNION ALL SELECT (@cx_u + 59), @cx_s_배드민턴, 1069.15, 6, '2026-10-18 21:00:00'
       UNION ALL SELECT (@cx_u + 60), @cx_s_배드민턴, 1029.79, 2, '2026-10-18 21:00:00'
       UNION ALL SELECT (@cx_u + 61), @cx_s_배드민턴, 1056.58, 2, '2026-09-17 21:00:00'
       UNION ALL SELECT (@cx_u + 62), @cx_s_배드민턴, 997.67, 2, '2026-10-04 11:00:00'
) v
 WHERE @cx_ready = 1;

-- 2. 동호회원 (기존 동호회에 새 회원이 가입)
INSERT INTO club_member (club_member_id, club_id, user_id, role, joined_at)
SELECT * FROM (
                 SELECT (@cx_m + 1) AS club_member_id, @cx_c2 AS club_id, (@cx_u + 1) AS user_id, '동호회원' AS role, '2026-05-05 10:30:00' AS joined_at
       UNION ALL SELECT (@cx_m + 2), @cx_c2, (@cx_u + 2), '동호회원', '2026-05-10 21:30:00'
       UNION ALL SELECT (@cx_m + 3), @cx_c2, (@cx_u + 3), '동호회원', '2026-06-01 22:30:00'
       UNION ALL SELECT (@cx_m + 4), @cx_c2, (@cx_u + 4), '동호회원', '2026-06-12 17:00:00'
       UNION ALL SELECT (@cx_m + 5), @cx_c2, (@cx_u + 5), '동호회원', '2026-06-29 12:00:00'
       UNION ALL SELECT (@cx_m + 6), @cx_c2, (@cx_u + 6), '동호회원', '2026-07-15 13:30:00'
       UNION ALL SELECT (@cx_m + 7), @cx_c4, (@cx_u + 7), '동호회원', '2026-05-06 22:30:00'
       UNION ALL SELECT (@cx_m + 8), @cx_c4, (@cx_u + 8), '동호회원', '2026-05-12 15:30:00'
       UNION ALL SELECT (@cx_m + 9), @cx_c4, (@cx_u + 9), '동호회원', '2026-06-10 18:00:00'
       UNION ALL SELECT (@cx_m + 10), @cx_c4, (@cx_u + 10), '동호회원', '2026-07-05 22:30:00'
       UNION ALL SELECT (@cx_m + 11), @cx_c5, (@cx_u + 11), '동호회원', '2026-06-22 18:00:00'
       UNION ALL SELECT (@cx_m + 12), @cx_c5, (@cx_u + 12), '동호회원', '2026-07-02 20:00:00'
       UNION ALL SELECT (@cx_m + 13), @cx_c5, (@cx_u + 13), '동호회원', '2026-07-27 11:30:00'
       UNION ALL SELECT (@cx_m + 14), @cx_c5, (@cx_u + 14), '동호회원', '2026-08-04 19:00:00'
       UNION ALL SELECT (@cx_m + 15), @cx_c6, (@cx_u + 15), '동호회원', '2026-05-12 13:30:00'
       UNION ALL SELECT (@cx_m + 16), @cx_c6, (@cx_u + 16), '동호회원', '2026-06-09 13:00:00'
       UNION ALL SELECT (@cx_m + 17), @cx_c6, (@cx_u + 17), '동호회원', '2026-06-14 17:30:00'
       UNION ALL SELECT (@cx_m + 18), @cx_c6, (@cx_u + 18), '동호회원', '2026-06-15 18:30:00'
       UNION ALL SELECT (@cx_m + 19), @cx_c7, (@cx_u + 19), '동호회원', '2026-05-06 15:00:00'
       UNION ALL SELECT (@cx_m + 20), @cx_c7, (@cx_u + 20), '동호회원', '2026-05-08 16:00:00'
       UNION ALL SELECT (@cx_m + 21), @cx_c7, (@cx_u + 21), '동호회원', '2026-06-20 13:00:00'
       UNION ALL SELECT (@cx_m + 22), @cx_c7, (@cx_u + 22), '동호회원', '2026-07-13 21:00:00'
       UNION ALL SELECT (@cx_m + 23), @cx_c8, (@cx_u + 23), '동호회원', '2026-05-27 20:00:00'
       UNION ALL SELECT (@cx_m + 24), @cx_c8, (@cx_u + 24), '동호회원', '2026-06-15 12:00:00'
       UNION ALL SELECT (@cx_m + 25), @cx_c8, (@cx_u + 25), '동호회원', '2026-06-18 12:30:00'
       UNION ALL SELECT (@cx_m + 26), @cx_c8, (@cx_u + 26), '동호회원', '2026-06-28 16:00:00'
       UNION ALL SELECT (@cx_m + 27), @cx_c9, (@cx_u + 27), '동호회원', '2026-05-01 18:00:00'
       UNION ALL SELECT (@cx_m + 28), @cx_c9, (@cx_u + 28), '동호회원', '2026-05-16 20:00:00'
       UNION ALL SELECT (@cx_m + 29), @cx_c9, (@cx_u + 29), '동호회원', '2026-07-20 12:30:00'
       UNION ALL SELECT (@cx_m + 30), @cx_c9, (@cx_u + 30), '동호회원', '2026-08-04 21:00:00'
       UNION ALL SELECT (@cx_m + 31), @cx_c10, (@cx_u + 31), '동호회원', '2026-05-07 13:00:00'
       UNION ALL SELECT (@cx_m + 32), @cx_c10, (@cx_u + 32), '동호회원', '2026-05-08 18:30:00'
       UNION ALL SELECT (@cx_m + 33), @cx_c10, (@cx_u + 33), '동호회원', '2026-05-10 10:30:00'
       UNION ALL SELECT (@cx_m + 34), @cx_c10, (@cx_u + 34), '동호회원', '2026-05-17 21:00:00'
       UNION ALL SELECT (@cx_m + 35), @cx_c10, (@cx_u + 35), '동호회원', '2026-05-27 15:30:00'
       UNION ALL SELECT (@cx_m + 36), @cx_c10, (@cx_u + 36), '동호회원', '2026-06-30 16:00:00'
       UNION ALL SELECT (@cx_m + 37), @cx_c11, (@cx_u + 37), '동호회원', '2026-05-26 22:00:00'
       UNION ALL SELECT (@cx_m + 38), @cx_c11, (@cx_u + 38), '동호회원', '2026-06-19 10:30:00'
       UNION ALL SELECT (@cx_m + 39), @cx_c11, (@cx_u + 39), '동호회원', '2026-07-12 12:30:00'
       UNION ALL SELECT (@cx_m + 40), @cx_c11, (@cx_u + 40), '동호회원', '2026-08-03 19:30:00'
       UNION ALL SELECT (@cx_m + 41), @cx_c13, (@cx_u + 41), '동호회원', '2026-05-24 21:00:00'
       UNION ALL SELECT (@cx_m + 42), @cx_c13, (@cx_u + 42), '동호회원', '2026-07-22 17:30:00'
       UNION ALL SELECT (@cx_m + 43), @cx_c13, (@cx_u + 43), '동호회원', '2026-07-24 16:00:00'
       UNION ALL SELECT (@cx_m + 44), @cx_c13, (@cx_u + 44), '동호회원', '2026-07-27 20:30:00'
       UNION ALL SELECT (@cx_m + 45), @cx_c14, (@cx_u + 45), '동호회원', '2026-05-24 20:30:00'
       UNION ALL SELECT (@cx_m + 46), @cx_c14, (@cx_u + 46), '동호회원', '2026-06-10 14:00:00'
       UNION ALL SELECT (@cx_m + 47), @cx_c14, (@cx_u + 47), '동호회원', '2026-07-04 19:00:00'
       UNION ALL SELECT (@cx_m + 48), @cx_c14, (@cx_u + 48), '동호회원', '2026-07-25 11:30:00'
       UNION ALL SELECT (@cx_m + 49), @cx_c15, (@cx_u + 49), '동호회원', '2026-05-12 16:00:00'
       UNION ALL SELECT (@cx_m + 50), @cx_c15, (@cx_u + 50), '동호회원', '2026-05-13 20:30:00'
       UNION ALL SELECT (@cx_m + 51), @cx_c15, (@cx_u + 51), '동호회원', '2026-06-06 21:00:00'
       UNION ALL SELECT (@cx_m + 52), @cx_c15, (@cx_u + 52), '동호회원', '2026-06-17 22:30:00'
       UNION ALL SELECT (@cx_m + 53), @cx_c15, (@cx_u + 53), '동호회원', '2026-06-28 11:30:00'
       UNION ALL SELECT (@cx_m + 54), @cx_c15, (@cx_u + 54), '동호회원', '2026-07-07 11:30:00'
       UNION ALL SELECT (@cx_m + 55), @cx_c18, (@cx_u + 55), '동호회원', '2026-05-02 18:00:00'
       UNION ALL SELECT (@cx_m + 56), @cx_c18, (@cx_u + 56), '동호회원', '2026-06-26 20:00:00'
       UNION ALL SELECT (@cx_m + 57), @cx_c18, (@cx_u + 57), '동호회원', '2026-07-03 09:00:00'
       UNION ALL SELECT (@cx_m + 58), @cx_c18, (@cx_u + 58), '동호회원', '2026-07-27 12:00:00'
       UNION ALL SELECT (@cx_m + 59), @cx_c19, (@cx_u + 59), '동호회원', '2026-07-03 09:00:00'
       UNION ALL SELECT (@cx_m + 60), @cx_c19, (@cx_u + 60), '동호회원', '2026-07-18 15:00:00'
       UNION ALL SELECT (@cx_m + 61), @cx_c19, (@cx_u + 61), '동호회원', '2026-07-23 15:30:00'
       UNION ALL SELECT (@cx_m + 62), @cx_c19, (@cx_u + 62), '동호회원', '2026-07-31 17:00:00'
) v
 WHERE @cx_ready = 1;

-- 소개 글 (시연 데이터와 같은 형식)
UPDATE club
   SET intro = CASE club_id
         WHEN @cx_c1 THEN '이름은 주말 풋살 클럽이지만 지금은 평일 저녁에 모이는 성동구 풋살팀입니다.\n\n처음엔 주말에 뛰다가 다들 주말 일정이 많아져 화·목 저녁으로 옮겼어요. 오래 손발을 맞춰 온 멤버들이라 패스 위주로 차분하게 경기합니다.\n\n[이런 분을 찾아요]\n· 화·목 저녁에 꾸준히 나올 수 있는 분\n· 처음이어도 괜찮아요, 천천히 손발을 맞춰 가요\n\n[활동 안내]\n· 정기 모임: 매주 화·목 19:00~21:00\n· 다른 동호회와 5:5 매치를 가끔 잡아요'
         WHEN @cx_c2 THEN '노원 농구장에서 주말 아침마다 모이는 농구 동호회입니다.\n\n아침 9시에 모여 몸을 풀고 3:3 하프코트로 시작해서, 사람이 모이면 4:4로 다른 동호회와 경기를 합니다. 끝나고 같이 아침을 먹는 날이 많아요.\n\n[이런 분을 찾아요]\n· 주말 아침 일찍 나올 수 있는 분\n· 슛보다 패스를 먼저 보는 분\n\n[활동 안내]\n· 정기 모임: 매주 토·일 09:00~11:00, 노원 농구장\n· 한 달에 두 번 정도 다른 동호회와 교류전\n\n가입 신청에 농구 경력과 주 포지션을 적어 주세요.'
         WHEN @cx_c3 THEN '송파구에서 월요일과 수요일 저녁에 모여 복식을 치는 배드민턴 동호회입니다.\n\n초중급 회원이 섞여 있어 매번 실력이 비슷하게 짝을 나누고, 경기 사이사이 서로 자세를 봐 줍니다.\n\n[이런 분을 찾아요]\n· 지금은 모집을 마감했어요\n· 다시 모집할 때 이 글로 알릴게요\n\n[활동 안내]\n· 정기 모임: 매주 월·수 20:00~22:00\n· 셔틀콕은 동호회에서 같이 삽니다'
         WHEN @cx_c4 THEN '금요일 아침 출근 전에 한 게임 치고 하루를 시작하는 테니스 동호회입니다.\n\n월드컵공원 테니스장에서 7시에 모여 랠리로 몸을 풀고 복식 두세 게임을 칩니다. 8시 50분에는 정리하고 바로 출근해요.\n\n[이런 분을 찾아요]\n· 금요일 아침 7시에 나올 수 있는 분\n· 랠리를 10번 이상 이어 갈 수 있는 분\n\n[활동 안내]\n· 정기 모임: 매주 금요일 07:00~09:00, 월드컵공원 테니스장\n· 공은 돌아가며 준비합니다\n\n가입 신청에 구력과 출근 시간대를 적어 주세요.'
         WHEN @cx_c5 THEN '퇴근하고 밤 9시에 모여 복식을 치는 서초구 야간 테니스 동호회입니다.\n\n조명 아래에서 치는 밤 테니스를 좋아하는 사람들이 모였어요. 늦은 시간이라 짧고 굵게 복식만 치고 11시에 끝냅니다.\n\n[이런 분을 찾아요]\n· 화·목 밤 시간이 비는 분\n· 복식 로테이션에 익숙한 분\n\n[활동 안내]\n· 정기 모임: 매주 화·목 21:00~23:00, 서초 테니스클럽\n· 다른 동호회와 복식 교류전: 한 달에 한두 번\n\n가입 신청에 구력과 주로 치는 위치를 적어 주세요.'
         WHEN @cx_c6 THEN '성동구 생활체육 배드민턴 모임입니다. 수요일과 토요일 저녁에 만나요.\n\n중랑물재생센터 배드민턴장에서 복식 위주로 치고, 처음 온 분은 기존 회원과 짝이 되어 경기 흐름을 익힙니다.\n\n[이런 분을 찾아요]\n· 배드민턴을 다시 시작하고 싶은 분\n· 일주일에 한 번은 나올 수 있는 분\n\n[활동 안내]\n· 정기 모임: 매주 수·토 19:00~21:00, 중랑물재생센터 배드민턴장\n· 분기마다 동호회 안에서 작은 대회를 엽니다\n\n가입 신청에 배드민턴 경력을 간단히 적어 주세요.'
         WHEN @cx_c7 THEN '광진구 주민들이 화요일과 금요일 저녁에 모이는 배드민턴 동호회입니다.\n\n광진 배드민턴장 코트 두 면을 쓰며 복식을 칩니다. 오래 함께한 회원이 많아 분위기가 편하고, 경기 뒤에는 서로 잘된 점을 짧게 이야기해요.\n\n[이런 분을 찾아요]\n· 지금은 모집을 마감했어요\n· 자리가 나면 다시 모집할게요\n\n[활동 안내]\n· 정기 모임: 매주 화·금 20:00~22:00, 광진 배드민턴장\n· 다른 동호회와 복식 교류전: 한 달에 한두 번'
         WHEN @cx_c8 THEN '서초 스포츠센터에서 월요일과 목요일 저녁에 복식을 치는 테니스 동호회입니다.\n\n잘 치는 것보다 꾸준히 치는 걸 중요하게 생각해요. 매번 짝을 바꿔 복식을 치고, 처음 오신 분도 첫날부터 경기에 들어갑니다.\n\n[이런 분을 찾아요]\n· 레슨을 3개월 이상 받아 본 분\n· 월·목 저녁에 꾸준히 나올 수 있는 분\n\n[활동 안내]\n· 정기 모임: 매주 월·목 19:30~21:30, 서초 스포츠센터\n· 코트비는 참석 인원끼리 나눠 냅니다\n\n가입 신청에 구력과 레슨 경험을 적어 주세요.'
         WHEN @cx_c9 THEN '영등포 테니스장에서 수요일과 금요일 저녁에 모이는 초중급 테니스 모임입니다.\n\n레슨을 막 끝내고 실전이 필요한 분들이 많아요. 복식 경기 중에 포지션과 콜을 서로 알려 주며 칩니다.\n\n[이런 분을 찾아요]\n· 레슨은 받았는데 경기 경험이 적은 분\n· 실수해도 웃고 넘길 수 있는 분\n\n[활동 안내]\n· 정기 모임: 매주 수·금 20:00~22:00, 영등포 테니스장\n· 다른 동호회와 복식 교류전: 한 달에 한 번\n\n가입 신청에 구력을 적어 주세요.'
         WHEN @cx_c10 THEN '토요일 오전 한강 바람 맞으며 농구하는 송파 지역 동호회입니다.\n\n광나루 농구장(올림픽대로 방향)에서 3:3으로 몸을 풀고, 인원이 차면 4:4 경기를 합니다. 비가 오면 쉬고 단체 채팅방에 미리 알려요.\n\n[이런 분을 찾아요]\n· 토요일 오전에 꾸준히 나올 수 있는 분\n· 야외 코트 농구를 좋아하는 분\n\n[활동 안내]\n· 정기 모임: 매주 토요일 10:00~12:00, 광나루 농구장\n· 다른 동호회와 4:4 교류전: 한 달에 두 번\n\n가입 신청에 농구 경력과 주 포지션을 적어 주세요.'
         WHEN @cx_c11 THEN '일요일 아침 송파 배드민턴홀에서 세 시간 동안 넉넉하게 치는 직장인 배드민턴 모임입니다.\n\n평일에 못 친 만큼 일요일에 몰아서 칩니다. 복식 위주로 돌아가며 치고, 중간에 쉬는 시간도 충분히 둬요.\n\n[이런 분을 찾아요]\n· 평일엔 바쁜 직장인\n· 일요일 오전을 운동으로 시작하고 싶은 분\n\n[활동 안내]\n· 정기 모임: 매주 일요일 09:00~12:00, 송파 배드민턴홀\n· 한 달에 한 번 다른 동호회와 교류전\n\n가입 신청에 배드민턴 경력과 주로 치는 시간대를 적어 주세요.'
         WHEN @cx_c12 THEN '은평 풋살아레나에서 수요일과 토요일 밤에 정기전을 여는 풋살 동호회입니다.\n\n매번 두 팀으로 나눠 정기전을 하고, 한 달에 두세 번은 다른 동호회를 불러 5:5 경기를 합니다.\n\n[이런 분을 찾아요]\n· 은평구 근처에 살거나 일하는 분\n· 풋살화를 갖고 있는 분\n\n[활동 안내]\n· 정기 모임: 매주 수·토 20:00~22:00, 은평 풋살아레나\n· 구장비는 참석 인원끼리 나눠 냅니다\n\n가입 신청에 주 포지션을 적어 주세요.'
         WHEN @cx_c13 THEN '주말 아침 어린이대공원 테니스장에서 복식을 치는 광진 지역 동호회입니다.\n\n8시에 모여 랠리로 몸을 풀고, 실력별로 짝을 나눠 복식 서너 게임을 치고 10시에 마칩니다.\n\n[이런 분을 찾아요]\n· 주말 아침 일찍 일어나는 분\n· 복식 경기 규칙을 아는 분\n\n[활동 안내]\n· 정기 모임: 매주 토·일 08:00~10:00, 어린이대공원 테니스장\n· 다른 동호회와 복식 교류전: 한 달에 한두 번\n\n가입 신청에 구력과 주말 중 나올 수 있는 날을 적어 주세요.'
         WHEN @cx_c14 THEN '강서 배드민턴장에서 화요일과 목요일 저녁에 모이는 배드민턴 동호회입니다. 초보도 환영해요.\n\n처음 2주는 기본 스텝과 클리어를 같이 연습하고, 그다음부터 복식 경기에 들어갑니다. 라켓이 없으면 동호회 라켓을 빌려 드려요.\n\n[이런 분을 찾아요]\n· 배드민턴을 처음 배우거나 다시 시작하는 분\n· 화·목 저녁에 시간이 되는 분\n\n[활동 안내]\n· 정기 모임: 매주 화·목 20:00~22:00, 강서 배드민턴장\n· 다른 동호회와 복식 교류전: 한 달에 한 번\n\n가입 신청에 배드민턴 경험이 있는지 적어 주세요.'
         WHEN @cx_c15 THEN '금요일 저녁 영등포 농구센터에서 한 주를 마무리하는 농구 동호회입니다.\n\n4:4로 뛰어요. 오래 같이 뛴 회원이 많아 손발이 잘 맞고, 경기 뒤에는 근처에서 저녁을 먹습니다.\n\n[이런 분을 찾아요]\n· 지금은 모집을 마감했어요\n· 자리가 나면 다시 모집할게요\n\n[활동 안내]\n· 정기 모임: 매주 금요일 19:00~21:00, 영등포 농구센터\n· 다른 동호회와 4:4 교류전: 한 달에 두 번'
         WHEN @cx_c16 THEN '노원구에서 일요일 오후에 공 차는 풋살 동호회입니다.\n\n주말 오후 느긋하게 모여 5:5로 뛰어요. 경기보다 같이 땀 흘리는 시간을 더 좋아하는 사람들이 모였습니다.\n\n[이런 분을 찾아요]\n· 일요일 오후에 시간이 되는 분\n· 노원구 근처에 사는 분\n\n[활동 안내]\n· 정기 모임: 매주 일요일 15:00~17:00\n· 구장비는 참석 인원끼리 나눠 냅니다\n\n가입 신청에 간단한 자기소개를 남겨 주세요.'
         WHEN @cx_c17 THEN '토요일 아침 관악 족구장에서 3:3 족구를 하는 지역 동호회입니다.\n\n군대에서 족구를 처음 배운 분부터 대학 동아리 출신까지 모였어요. 서브와 토스 연습을 하고 나서 세트 경기를 합니다.\n\n[이런 분을 찾아요]\n· 족구 규칙을 아는 분\n· 토요일 아침에 꾸준히 나올 수 있는 분\n\n[활동 안내]\n· 정기 모임: 매주 토요일 09:00~11:00, 관악 족구장\n· 다른 동호회와 3:3 교류전: 한 달에 한두 번\n\n가입 신청에 족구 경험을 적어 주세요.'
         WHEN @cx_c18 THEN '마곡·발산 직장인들이 수요일 저녁 강서 테니스장에 모이는 테니스 동호회입니다.\n\n일주일의 한가운데에 한 번 크게 땀을 흘리자는 모임이에요. 복식 위주로 치고, 처음 오신 분은 기존 회원과 짝이 됩니다.\n\n[이런 분을 찾아요]\n· 마곡·발산·가양 근처에서 일하는 분\n· 복식 경기를 해 본 분\n\n[활동 안내]\n· 정기 모임: 매주 수요일 20:00~22:00, 강서 테니스장\n· 다른 동호회와 복식 교류전: 한 달에 한 번\n\n가입 신청에 구력과 일하는 동네를 적어 주세요.'
         WHEN @cx_c19 THEN '주말 아침 송파 배드민턴코트에서 복식을 치는 배드민턴 모임입니다.\n\n석촌호수 산책을 하다 모인 이웃들이 시작한 모임이에요. 아침 9시에 모여 두 시간 동안 짝을 바꿔 가며 칩니다.\n\n[이런 분을 찾아요]\n· 주말 아침에 운동하고 싶은 분\n· 송파·잠실 근처에 사는 분\n\n[활동 안내]\n· 정기 모임: 매주 토·일 09:00~11:00, 송파 배드민턴코트\n· 다른 동호회와 복식 교류전: 한 달에 한 번\n\n가입 신청에 배드민턴 경력을 적어 주세요.'
         WHEN @cx_c20 THEN '잠실에서 월요일과 수요일 저녁에 모이는 농구 동호회입니다.\n\n퇴근 후 가볍게 슈팅 연습을 하고 3:3, 4:4로 경기를 합니다.\n\n[이런 분을 찾아요]\n· 지금은 모집을 마감했어요\n· 다시 모집할 때 이 글로 알릴게요\n\n[활동 안내]\n· 정기 모임: 매주 월·수 20:00~22:00\n· 다른 동호회와 교류전은 인원이 모이면 잡아요'
         WHEN @cx_c21 THEN '뚝섬한강공원 축구장에서 주말 오전에 뛰는 광진구 풋살팀 뚝섬 FC입니다.\n\n한강을 옆에 두고 5:5로 뛰는 맛에 모인 팀이에요. 초보도 환영하고, 실력이 섞이도록 매번 팀을 다시 나눕니다.\n\n[이런 분을 찾아요]\n· 주말 오전에 한 번 이상 나올 수 있는 분\n· 초보부터 중급까지 누구나\n\n[활동 안내]\n· 정기 모임: 매주 토·일 10:00~12:00, 뚝섬한강공원 축구장 2\n· 다른 동호회와 5:5 매치: 한 달에 두세 번\n\n가입 신청에 주 포지션과 풋살 경력을 적어 주세요.'
         WHEN @cx_c22 THEN '화요일과 목요일 저녁 망원유수지 풋살장에서 모이는 마포구 풋살 크루입니다.\n\n합정·망원·상수에서 일하는 사람들이 퇴근길에 들러 뛰고 가요. 초보도 환영하고, 처음 오신 분은 수비부터 같이 맞춰 봅니다.\n\n[이런 분을 찾아요]\n· 평일 저녁 7시에 올 수 있는 분\n· 같이 뛰고 같이 치우는 분\n\n[활동 안내]\n· 정기 모임: 매주 화·목 19:00~21:00, 망원유수지 풋살장\n· 다른 동호회와 5:5 매치: 한 달에 두세 번\n\n가입 신청에 주 포지션을 적어 주세요.'
         WHEN @cx_c23 THEN '토요일 오후 잠실종합운동장 제2풋살경기장에서 뛰는 송파구 풋살 클럽입니다.\n\n점심 먹고 느긋하게 모여 두 시간 동안 5:5 경기를 합니다. 초보도 환영하고, 실력에 맞춰 팀을 나눠요.\n\n[이런 분을 찾아요]\n· 토요일 오후에 시간이 되는 분\n· 송파구 근처에 사는 분\n\n[활동 안내]\n· 정기 모임: 매주 토요일 14:00~16:00, 잠실종합운동장 제2풋살경기장\n· 다른 동호회와 5:5 매치: 한 달에 두 번\n\n가입 신청에 간단한 자기소개를 남겨 주세요.'
         WHEN @cx_c24 THEN '반포한강공원 농구장에서 해 질 무렵 농구하는 서초구 동호회입니다.\n\n수요일과 토요일 저녁 6시에 모여 4:4로 뜁니다. 초보도 환영하고, 처음 오신 분은 3:3 하프코트부터 같이 해요.\n\n[이런 분을 찾아요]\n· 저녁 6시에 올 수 있는 분\n· 야외 코트를 좋아하는 분\n\n[활동 안내]\n· 정기 모임: 매주 수·토 18:00~20:00, 반포한강공원 농구장\n· 다른 동호회와 4:4 교류전: 한 달에 두세 번\n\n가입 신청에 농구 경력과 주 포지션을 적어 주세요.'
         WHEN @cx_c25 THEN '일요일 오후 광나루한강공원 농구장에서 뛰는 강동구 농구 동호회입니다.\n\n천호·암사 이웃들이 모여 만든 팀이에요. 4:4 경기를 주로 하고, 초보도 환영해서 경기마다 실력을 섞어 팀을 나눕니다.\n\n[이런 분을 찾아요]\n· 일요일 오후에 시간이 되는 분\n· 강동구 근처에 사는 분\n\n[활동 안내]\n· 정기 모임: 매주 일요일 14:00~16:00, 광나루한강공원 농구장\n· 다른 동호회와 4:4 교류전: 한 달에 두 번\n\n가입 신청에 농구 경력을 적어 주세요.'
         WHEN @cx_c26 THEN '주말 오전 뚝섬한강공원 족구장에서 3:3 족구를 하는 광진구 동호회입니다.\n\n족구를 처음 하는 분도 환영해요. 서브 받는 법부터 같이 연습하고, 몸이 풀리면 세트 경기를 합니다.\n\n[이런 분을 찾아요]\n· 주말 오전에 시간이 되는 분\n· 족구를 해 보고 싶었던 분\n\n[활동 안내]\n· 정기 모임: 매주 토·일 10:00~12:00, 뚝섬한강공원 족구장\n· 다른 동호회와 3:3 교류전: 한 달에 한두 번\n\n가입 신청에 간단한 자기소개를 남겨 주세요.'
         WHEN @cx_c27 THEN '화요일과 금요일 저녁 뚝섬한강공원 배드민턴장에서 치는 광진구 배드민턴 동호회입니다.\n\n바람이 약한 저녁 시간에 야외 코트에서 복식을 칩니다. 초보도 환영하고, 셔틀콕은 동호회에서 준비해요.\n\n[이런 분을 찾아요]\n· 퇴근 후 6시에 올 수 있는 분\n· 야외에서 운동하는 걸 좋아하는 분\n\n[활동 안내]\n· 정기 모임: 매주 화·금 18:00~20:00, 뚝섬한강공원 배드민턴장\n· 다른 동호회와 복식 교류전: 한 달에 한두 번\n\n가입 신청에 배드민턴 경력을 적어 주세요.'
         WHEN @cx_c28 THEN '토요일 오전 망원나들목 테니스장에서 랠리와 복식을 즐기는 마포구 테니스 동호회입니다.\n\n이름처럼 랠리를 길게 이어 가는 걸 좋아해요. 앞 한 시간은 랠리, 뒤 한 시간은 복식 경기를 합니다. 초보도 환영해요.\n\n[이런 분을 찾아요]\n· 토요일 오전에 시간이 되는 분\n· 랠리를 오래 치고 싶은 분\n\n[활동 안내]\n· 정기 모임: 매주 토요일 10:00~12:00, 망원나들목 테니스장\n· 다른 동호회와 복식 교류전: 한 달에 한 번\n\n가입 신청에 구력을 적어 주세요.'
       END
 WHERE @cx_ready = 1 AND club_id IN (@cx_c1, @cx_c2, @cx_c3, @cx_c4, @cx_c5, @cx_c6, @cx_c7, @cx_c8, @cx_c9, @cx_c10, @cx_c11, @cx_c12, @cx_c13, @cx_c14, @cx_c15, @cx_c16, @cx_c17, @cx_c18, @cx_c19, @cx_c20, @cx_c21, @cx_c22, @cx_c23, @cx_c24, @cx_c25, @cx_c26, @cx_c27, @cx_c28);

-- 3. 경기: 지난 동호회 경기(종료, 결과 확정, 레이팅 반영) + 예정 매칭방(모집 중). 팀장은 참가자를 넣은 뒤 지정한다.
INSERT INTO social_match (match_id, facility_id, sport_id, created_by, title, start_at, end_at, min_participants, max_participants, team_a_capacity, team_b_capacity, fee_per_person, required_skill, status, photo_url, assignment_method, assigned_by, assignment_confirmed_at, result_status, final_result, result_confirmed_at, rating_status, rating_processed_at)
SELECT * FROM (
                 SELECT (@cx_sm + 1) AS match_id, @cx_f8 AS facility_id, @cx_s_농구 AS sport_id, @cx_f8_mgr AS created_by, '광나루 농구장 토요일 오전 4:4' AS title, '2026-08-01 10:00:00' AS start_at, '2026-08-01 12:00:00' AS end_at, 8 AS min_participants, 8 AS max_participants, 4 AS team_a_capacity, 4 AS team_b_capacity, 0 AS fee_per_person, '중급' AS required_skill, '종료' AS status, '/images/matches/basketball.jpg' AS photo_url, 'MANUAL' AS assignment_method, @cx_f8_mgr AS assigned_by, '2026-08-01 09:00:00' AS assignment_confirmed_at, 'CONFIRMED' AS result_status, 'A_WIN' AS final_result, '2026-08-01 13:45:00' AS result_confirmed_at, 'APPLIED' AS rating_status, '2026-08-02 12:00:00' AS rating_processed_at
       UNION ALL SELECT (@cx_sm + 2), @cx_f2, @cx_s_테니스, @cx_f2_mgr, '월드컵공원 테니스장 금요일 오전 2:2', '2026-08-07 07:00:00', '2026-08-07 09:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f2_mgr, '2026-08-07 06:00:00', 'CONFIRMED', 'A_WIN', '2026-08-07 11:34:00', 'APPLIED', '2026-08-08 09:00:00'
       UNION ALL SELECT (@cx_sm + 3), @cx_f5, @cx_s_배드민턴, @cx_f5_mgr, '광진 배드민턴장 금요일 저녁 2:2', '2026-08-07 20:00:00', '2026-08-07 22:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f5_mgr, '2026-08-07 19:00:00', 'CONFIRMED', 'DRAW', '2026-08-07 23:28:00', 'APPLIED', '2026-08-08 22:00:00'
       UNION ALL SELECT (@cx_sm + 4), @cx_f4, @cx_s_배드민턴, @cx_f4_mgr, '중랑물재생 배드민턴장 토요일 저녁 2:2', '2026-08-08 19:00:00', '2026-08-08 21:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f4_mgr, '2026-08-08 18:00:00', 'CONFIRMED', 'B_WIN', '2026-08-08 21:52:00', 'APPLIED', '2026-08-09 21:00:00'
       UNION ALL SELECT (@cx_sm + 5), @cx_f1, @cx_s_농구, @cx_f1_mgr, '노원 농구장 일요일 오전 4:4', '2026-08-09 09:00:00', '2026-08-09 11:00:00', 8, 8, 4, 4, 0, '중급', '종료', '/images/matches/basketball.jpg', 'MANUAL', @cx_f1_mgr, '2026-08-09 08:00:00', 'CONFIRMED', 'B_WIN', '2026-08-09 13:41:00', 'APPLIED', '2026-08-10 11:00:00'
       UNION ALL SELECT (@cx_sm + 6), @cx_f3, @cx_s_테니스, @cx_f3_mgr, '서초 테니스클럽 화요일 저녁 2:2', '2026-08-11 21:00:00', '2026-08-11 23:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f3_mgr, '2026-08-11 20:00:00', 'CONFIRMED', 'B_WIN', '2026-08-12 01:55:00', 'APPLIED', '2026-08-12 23:00:00'
       UNION ALL SELECT (@cx_sm + 7), @cx_f5, @cx_s_배드민턴, @cx_f5_mgr, '광진 배드민턴장 금요일 저녁 2:2', '2026-08-14 20:00:00', '2026-08-14 22:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f5_mgr, '2026-08-14 19:00:00', 'CONFIRMED', 'B_WIN', '2026-08-14 23:22:00', 'APPLIED', '2026-08-15 22:00:00'
       UNION ALL SELECT (@cx_sm + 8), @cx_f7, @cx_s_테니스, @cx_f7_mgr, '영등포 테니스장 금요일 저녁 2:2', '2026-08-14 20:00:00', '2026-08-14 22:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f7_mgr, '2026-08-14 19:00:00', 'CONFIRMED', 'A_WIN', '2026-08-15 00:02:00', 'APPLIED', '2026-08-15 22:00:00'
       UNION ALL SELECT (@cx_sm + 9), @cx_f14, @cx_s_배드민턴, @cx_f14_mgr, '송파 배드민턴코트 토요일 오전 2:2', '2026-08-15 09:00:00', '2026-08-15 11:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f14_mgr, '2026-08-15 08:00:00', 'CONFIRMED', 'A_WIN', '2026-08-15 11:44:00', 'APPLIED', '2026-08-16 11:00:00'
       UNION ALL SELECT (@cx_sm + 10), @cx_f10, @cx_s_테니스, @cx_f10_mgr, '어린이대공원 테니스장 일요일 오전 2:2', '2026-08-16 08:00:00', '2026-08-16 10:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f10_mgr, '2026-08-16 07:00:00', 'CONFIRMED', 'A_WIN', '2026-08-16 12:25:00', 'APPLIED', '2026-08-17 10:00:00'
       UNION ALL SELECT (@cx_sm + 11), @cx_f11, @cx_s_배드민턴, @cx_f11_mgr, '강서 배드민턴장 화요일 저녁 2:2', '2026-08-18 20:00:00', '2026-08-18 22:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f11_mgr, '2026-08-18 19:00:00', 'CONFIRMED', 'A_WIN', '2026-08-18 23:14:00', 'APPLIED', '2026-08-19 22:00:00'
       UNION ALL SELECT (@cx_sm + 12), @cx_f7, @cx_s_테니스, @cx_f7_mgr, '영등포 테니스장 수요일 저녁 2:2', '2026-08-19 20:00:00', '2026-08-19 22:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f7_mgr, '2026-08-19 19:00:00', 'CONFIRMED', 'B_WIN', '2026-08-19 23:34:00', 'APPLIED', '2026-08-20 22:00:00'
       UNION ALL SELECT (@cx_sm + 13), @cx_f6, @cx_s_테니스, @cx_f6_mgr, '서초 스포츠센터 월요일 저녁 2:2', '2026-08-24 19:30:00', '2026-08-24 21:30:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f6_mgr, '2026-08-24 18:30:00', 'CONFIRMED', 'A_WIN', '2026-08-24 23:18:00', 'APPLIED', '2026-08-25 21:30:00'
       UNION ALL SELECT (@cx_sm + 14), @cx_f4, @cx_s_배드민턴, @cx_f4_mgr, '중랑물재생 배드민턴장 수요일 저녁 2:2', '2026-08-26 19:00:00', '2026-08-26 21:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f4_mgr, '2026-08-26 18:00:00', 'CONFIRMED', 'B_WIN', '2026-08-26 22:13:00', 'APPLIED', '2026-08-27 21:00:00'
       UNION ALL SELECT (@cx_sm + 15), @cx_f6, @cx_s_테니스, @cx_f6_mgr, '서초 스포츠센터 목요일 저녁 2:2', '2026-08-27 19:30:00', '2026-08-27 21:30:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f6_mgr, '2026-08-27 18:30:00', 'CONFIRMED', 'A_WIN', '2026-08-27 23:56:00', 'APPLIED', '2026-08-28 21:30:00'
       UNION ALL SELECT (@cx_sm + 16), @cx_f10, @cx_s_테니스, @cx_f10_mgr, '어린이대공원 테니스장 일요일 오전 2:2', '2026-08-30 08:00:00', '2026-08-30 10:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f10_mgr, '2026-08-30 07:00:00', 'CONFIRMED', 'B_WIN', '2026-08-30 11:15:00', 'APPLIED', '2026-08-31 10:00:00'
       UNION ALL SELECT (@cx_sm + 17), @cx_f9, @cx_s_배드민턴, @cx_f9_mgr, '송파 배드민턴홀 일요일 오전 2:2', '2026-08-30 09:00:00', '2026-08-30 12:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f9_mgr, '2026-08-30 08:00:00', 'CONFIRMED', 'B_WIN', '2026-08-30 13:01:00', 'APPLIED', '2026-08-31 12:00:00'
       UNION ALL SELECT (@cx_sm + 18), @cx_f13, @cx_s_테니스, @cx_f13_mgr, '강서 테니스장 수요일 저녁 2:2', '2026-09-02 20:00:00', '2026-09-02 22:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f13_mgr, '2026-09-02 19:00:00', 'CONFIRMED', 'A_WIN', '2026-09-03 00:41:00', 'APPLIED', '2026-09-03 22:00:00'
       UNION ALL SELECT (@cx_sm + 19), @cx_f2, @cx_s_테니스, @cx_f2_mgr, '월드컵공원 테니스장 금요일 오전 2:2', '2026-09-11 07:00:00', '2026-09-11 09:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f2_mgr, '2026-09-11 06:00:00', 'CONFIRMED', 'B_WIN', '2026-09-11 10:35:00', 'APPLIED', '2026-09-12 09:00:00'
       UNION ALL SELECT (@cx_sm + 20), @cx_f12, @cx_s_농구, @cx_f12_mgr, '영등포 농구센터 금요일 저녁 4:4', '2026-09-11 19:00:00', '2026-09-11 21:00:00', 8, 8, 4, 4, 0, '중급', '종료', '/images/matches/basketball.jpg', 'MANUAL', @cx_f12_mgr, '2026-09-11 18:00:00', 'CONFIRMED', 'B_WIN', '2026-09-11 21:52:00', 'APPLIED', '2026-09-12 21:00:00'
       UNION ALL SELECT (@cx_sm + 21), @cx_f10, @cx_s_테니스, @cx_f10_mgr, '어린이대공원 테니스장 토요일 오전 2:2', '2026-09-12 08:00:00', '2026-09-12 10:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f10_mgr, '2026-09-12 07:00:00', 'CONFIRMED', 'B_WIN', '2026-09-12 10:52:00', 'APPLIED', '2026-09-13 10:00:00'
       UNION ALL SELECT (@cx_sm + 22), @cx_f14, @cx_s_배드민턴, @cx_f14_mgr, '송파 배드민턴코트 일요일 오전 2:2', '2026-09-13 09:00:00', '2026-09-13 11:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f14_mgr, '2026-09-13 08:00:00', 'CONFIRMED', 'A_WIN', '2026-09-13 13:15:00', 'APPLIED', '2026-09-14 11:00:00'
       UNION ALL SELECT (@cx_sm + 23), @cx_f6, @cx_s_테니스, @cx_f6_mgr, '서초 스포츠센터 월요일 저녁 2:2', '2026-09-14 19:30:00', '2026-09-14 21:30:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f6_mgr, '2026-09-14 18:30:00', 'CONFIRMED', 'DRAW', '2026-09-14 23:56:00', 'APPLIED', '2026-09-15 21:30:00'
       UNION ALL SELECT (@cx_sm + 24), @cx_f4, @cx_s_배드민턴, @cx_f4_mgr, '중랑물재생 배드민턴장 수요일 저녁 2:2', '2026-09-16 19:00:00', '2026-09-16 21:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f4_mgr, '2026-09-16 18:00:00', 'CONFIRMED', 'B_WIN', '2026-09-16 23:28:00', 'APPLIED', '2026-09-17 21:00:00'
       UNION ALL SELECT (@cx_sm + 25), @cx_f8, @cx_s_농구, @cx_f8_mgr, '광나루 농구장 토요일 오전 4:4', '2026-09-19 10:00:00', '2026-09-19 12:00:00', 8, 8, 4, 4, 0, '중급', '종료', '/images/matches/basketball.jpg', 'MANUAL', @cx_f8_mgr, '2026-09-19 09:00:00', 'CONFIRMED', 'DRAW', '2026-09-19 14:53:00', 'APPLIED', '2026-09-20 12:00:00'
       UNION ALL SELECT (@cx_sm + 26), @cx_f12, @cx_s_농구, @cx_f12_mgr, '영등포 농구센터 금요일 저녁 4:4', '2026-09-25 19:00:00', '2026-09-25 21:00:00', 8, 8, 4, 4, 0, '중급', '종료', '/images/matches/basketball.jpg', 'MANUAL', @cx_f12_mgr, '2026-09-25 18:00:00', 'CONFIRMED', 'DRAW', '2026-09-25 23:52:00', 'APPLIED', '2026-09-26 21:00:00'
       UNION ALL SELECT (@cx_sm + 27), @cx_f5, @cx_s_배드민턴, @cx_f5_mgr, '광진 배드민턴장 금요일 저녁 2:2', '2026-09-25 20:00:00', '2026-09-25 22:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f5_mgr, '2026-09-25 19:00:00', 'CONFIRMED', 'A_WIN', '2026-09-25 23:22:00', 'APPLIED', '2026-09-26 22:00:00'
       UNION ALL SELECT (@cx_sm + 28), @cx_f6, @cx_s_테니스, @cx_f6_mgr, '서초 스포츠센터 월요일 저녁 2:2', '2026-09-28 19:30:00', '2026-09-28 21:30:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f6_mgr, '2026-09-28 18:30:00', 'CONFIRMED', 'DRAW', '2026-09-28 22:41:00', 'APPLIED', '2026-09-29 21:30:00'
       UNION ALL SELECT (@cx_sm + 29), @cx_f10, @cx_s_테니스, @cx_f10_mgr, '어린이대공원 테니스장 토요일 오전 2:2', '2026-10-03 08:00:00', '2026-10-03 10:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f10_mgr, '2026-10-03 07:00:00', 'CONFIRMED', 'B_WIN', '2026-10-03 11:22:00', 'APPLIED', '2026-10-04 10:00:00'
       UNION ALL SELECT (@cx_sm + 30), @cx_f14, @cx_s_배드민턴, @cx_f14_mgr, '송파 배드민턴코트 토요일 오전 2:2', '2026-10-03 09:00:00', '2026-10-03 11:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f14_mgr, '2026-10-03 08:00:00', 'CONFIRMED', 'B_WIN', '2026-10-03 11:30:00', 'APPLIED', '2026-10-04 11:00:00'
       UNION ALL SELECT (@cx_sm + 31), @cx_f9, @cx_s_배드민턴, @cx_f9_mgr, '송파 배드민턴홀 일요일 오전 2:2', '2026-10-04 09:00:00', '2026-10-04 12:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f9_mgr, '2026-10-04 08:00:00', 'CONFIRMED', 'B_WIN', '2026-10-04 13:13:00', 'APPLIED', '2026-10-05 12:00:00'
       UNION ALL SELECT (@cx_sm + 32), @cx_f3, @cx_s_테니스, @cx_f3_mgr, '서초 테니스클럽 목요일 저녁 2:2', '2026-10-08 21:00:00', '2026-10-08 23:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f3_mgr, '2026-10-08 20:00:00', 'CONFIRMED', 'B_WIN', '2026-10-09 01:47:00', 'APPLIED', '2026-10-09 23:00:00'
       UNION ALL SELECT (@cx_sm + 33), @cx_f12, @cx_s_농구, @cx_f12_mgr, '영등포 농구센터 금요일 저녁 4:4', '2026-10-09 19:00:00', '2026-10-09 21:00:00', 8, 8, 4, 4, 0, '중급', '종료', '/images/matches/basketball.jpg', 'MANUAL', @cx_f12_mgr, '2026-10-09 18:00:00', 'CONFIRMED', 'B_WIN', '2026-10-09 21:49:00', 'APPLIED', '2026-10-10 21:00:00'
       UNION ALL SELECT (@cx_sm + 34), @cx_f6, @cx_s_테니스, @cx_f6_mgr, '서초 스포츠센터 목요일 저녁 2:2', '2026-10-15 19:30:00', '2026-10-15 21:30:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f6_mgr, '2026-10-15 18:30:00', 'CONFIRMED', 'A_WIN', '2026-10-15 23:49:00', 'APPLIED', '2026-10-16 21:30:00'
       UNION ALL SELECT (@cx_sm + 35), @cx_f11, @cx_s_배드민턴, @cx_f11_mgr, '강서 배드민턴장 목요일 저녁 2:2', '2026-10-15 20:00:00', '2026-10-15 22:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f11_mgr, '2026-10-15 19:00:00', 'CONFIRMED', 'A_WIN', '2026-10-15 23:22:00', 'APPLIED', '2026-10-16 22:00:00'
       UNION ALL SELECT (@cx_sm + 36), @cx_f12, @cx_s_농구, @cx_f12_mgr, '영등포 농구센터 금요일 저녁 4:4', '2026-10-16 19:00:00', '2026-10-16 21:00:00', 8, 8, 4, 4, 0, '중급', '종료', '/images/matches/basketball.jpg', 'MANUAL', @cx_f12_mgr, '2026-10-16 18:00:00', 'CONFIRMED', 'B_WIN', '2026-10-16 22:33:00', 'APPLIED', '2026-10-17 21:00:00'
       UNION ALL SELECT (@cx_sm + 37), @cx_f10, @cx_s_테니스, @cx_f10_mgr, '어린이대공원 테니스장 토요일 오전 2:2', '2026-10-17 08:00:00', '2026-10-17 10:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f10_mgr, '2026-10-17 07:00:00', 'CONFIRMED', 'B_WIN', '2026-10-17 12:20:00', 'APPLIED', '2026-10-18 10:00:00'
       UNION ALL SELECT (@cx_sm + 38), @cx_f4, @cx_s_배드민턴, @cx_f4_mgr, '중랑물재생 배드민턴장 토요일 저녁 2:2', '2026-10-17 19:00:00', '2026-10-17 21:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f4_mgr, '2026-10-17 18:00:00', 'CONFIRMED', 'B_WIN', '2026-10-17 23:53:00', 'APPLIED', '2026-10-18 21:00:00'
       UNION ALL SELECT (@cx_sm + 39), @cx_f12, @cx_s_농구, @cx_f12_mgr, '영등포 농구센터 금요일 저녁 4:4', '2026-10-23 19:00:00', '2026-10-23 21:00:00', 8, 8, 4, 4, 0, '중급', '종료', '/images/matches/basketball.jpg', 'MANUAL', @cx_f12_mgr, '2026-10-23 18:00:00', 'CONFIRMED', 'A_WIN', '2026-10-23 22:04:00', 'APPLIED', '2026-10-24 21:00:00'
       UNION ALL SELECT (@cx_sm + 40), @cx_f13, @cx_s_테니스, @cx_f13_mgr, '강서 테니스장 수요일 저녁 2:2', '2026-10-28 20:00:00', '2026-10-28 22:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f13_mgr, '2026-10-28 19:00:00', 'CONFIRMED', 'DRAW', '2026-10-28 23:28:00', 'APPLIED', '2026-10-29 22:00:00'
       UNION ALL SELECT (@cx_sm + 41), @cx_f11, @cx_s_배드민턴, @cx_f11_mgr, '강서 배드민턴장 화요일 저녁 2:2', '2026-11-03 20:00:00', '2026-11-03 22:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f11_mgr, '2026-11-03 19:00:00', 'CONFIRMED', 'B_WIN', '2026-11-04 00:37:00', 'APPLIED', '2026-11-04 22:00:00'
       UNION ALL SELECT (@cx_sm + 42), @cx_f8, @cx_s_농구, @cx_f8_mgr, '광나루 농구장 토요일 오전 4:4', '2026-11-07 10:00:00', '2026-11-07 12:00:00', 8, 8, 4, 4, 0, '중급', '종료', '/images/matches/basketball.jpg', 'MANUAL', @cx_f8_mgr, '2026-11-07 09:00:00', 'CONFIRMED', 'B_WIN', '2026-11-07 13:54:00', 'APPLIED', '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_sm + 43), @cx_f1, @cx_s_농구, @cx_f1_mgr, '노원 농구장 토요일 오전 4:4', '2026-12-05 09:00:00', '2026-12-05 11:00:00', 8, 8, 4, 4, 0, '중급', '모집 중', '/images/matches/basketball.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
       UNION ALL SELECT (@cx_sm + 44), @cx_f2, @cx_s_테니스, @cx_f2_mgr, '월드컵공원 테니스장 금요일 오전 2:2', '2026-12-04 07:00:00', '2026-12-04 09:00:00', 4, 4, 2, 2, 0, '중급', '모집 중', '/images/matches/tennis.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
       UNION ALL SELECT (@cx_sm + 45), @cx_f3, @cx_s_테니스, @cx_f3_mgr, '서초 테니스클럽 목요일 저녁 2:2', '2026-11-26 21:00:00', '2026-11-26 23:00:00', 4, 4, 2, 2, 0, '중급', '모집 중', '/images/matches/tennis.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
       UNION ALL SELECT (@cx_sm + 46), @cx_f4, @cx_s_배드민턴, @cx_f4_mgr, '중랑물재생 배드민턴장 토요일 저녁 2:2', '2026-11-28 19:00:00', '2026-11-28 21:00:00', 4, 4, 2, 2, 0, '중급', '모집 중', '/images/matches/badminton.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
       UNION ALL SELECT (@cx_sm + 47), @cx_f5, @cx_s_배드민턴, @cx_f5_mgr, '광진 배드민턴장 화요일 저녁 2:2', '2026-12-01 20:00:00', '2026-12-01 22:00:00', 4, 4, 2, 2, 0, '중급', '모집 중', '/images/matches/badminton.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
       UNION ALL SELECT (@cx_sm + 48), @cx_f6, @cx_s_테니스, @cx_f6_mgr, '서초 스포츠센터 목요일 저녁 2:2', '2026-11-26 19:30:00', '2026-11-26 21:30:00', 4, 4, 2, 2, 0, '중급', '모집 중', '/images/matches/tennis.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
       UNION ALL SELECT (@cx_sm + 49), @cx_f7, @cx_s_테니스, @cx_f7_mgr, '영등포 테니스장 수요일 저녁 2:2', '2026-11-25 20:00:00', '2026-11-25 22:00:00', 4, 4, 2, 2, 0, '중급', '모집 중', '/images/matches/tennis.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
       UNION ALL SELECT (@cx_sm + 50), @cx_f8, @cx_s_농구, @cx_f8_mgr, '광나루 농구장 토요일 오전 4:4', '2026-11-28 10:00:00', '2026-11-28 12:00:00', 8, 8, 4, 4, 0, '중급', '모집 중', '/images/matches/basketball.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
       UNION ALL SELECT (@cx_sm + 51), @cx_f9, @cx_s_배드민턴, @cx_f9_mgr, '송파 배드민턴홀 일요일 오전 2:2', '2026-12-06 09:00:00', '2026-12-06 12:00:00', 4, 4, 2, 2, 0, '중급', '모집 중', '/images/matches/badminton.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
       UNION ALL SELECT (@cx_sm + 52), @cx_f10, @cx_s_테니스, @cx_f10_mgr, '어린이대공원 테니스장 토요일 오전 2:2', '2026-12-05 08:00:00', '2026-12-05 10:00:00', 4, 4, 2, 2, 0, '중급', '모집 중', '/images/matches/tennis.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
       UNION ALL SELECT (@cx_sm + 53), @cx_f11, @cx_s_배드민턴, @cx_f11_mgr, '강서 배드민턴장 화요일 저녁 2:2', '2026-12-01 20:00:00', '2026-12-01 22:00:00', 4, 4, 2, 2, 0, '중급', '모집 중', '/images/matches/badminton.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
       UNION ALL SELECT (@cx_sm + 54), @cx_f12, @cx_s_농구, @cx_f12_mgr, '영등포 농구센터 금요일 저녁 4:4', '2026-11-27 19:00:00', '2026-11-27 21:00:00', 8, 8, 4, 4, 0, '중급', '모집 중', '/images/matches/basketball.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
       UNION ALL SELECT (@cx_sm + 55), @cx_f13, @cx_s_테니스, @cx_f13_mgr, '강서 테니스장 수요일 저녁 2:2', '2026-11-25 20:00:00', '2026-11-25 22:00:00', 4, 4, 2, 2, 0, '중급', '모집 중', '/images/matches/tennis.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
       UNION ALL SELECT (@cx_sm + 56), @cx_f14, @cx_s_배드민턴, @cx_f14_mgr, '송파 배드민턴코트 토요일 오전 2:2', '2026-11-28 09:00:00', '2026-11-28 11:00:00', 4, 4, 2, 2, 0, '중급', '모집 중', '/images/matches/badminton.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
) v
 WHERE @cx_ready = 1;

-- 4. 팀 신청: 동호회마다 대표 1명 + 팀원. 지난 경기는 시설 담당자가 확정, 예정 매칭방은 대기. 행을 넣은 뒤 묶음 번호를 붙인다.
INSERT INTO match_application (application_id, match_id, applicant_user_id, club_id, application_type, status, receipt_no, created_at, confirmed_at)
SELECT * FROM (
                 SELECT (@cx_a + 1) AS application_id, (@cx_sm + 1) AS match_id, (@cx_u + 31) AS applicant_user_id, @cx_c10 AS club_id, '동호회' AS application_type, '확정' AS status, 'RCPT-20260729-FFBDXDZF' AS receipt_no, '2026-07-29 06:00:00' AS created_at, '2026-07-29 07:39:00' AS confirmed_at
       UNION ALL SELECT (@cx_a + 2), (@cx_sm + 1), (@cx_u + 32), @cx_c10, '동호회', '확정', 'RCPT-20260729-95P7WMSR', '2026-07-29 06:00:00', '2026-07-29 07:06:00'
       UNION ALL SELECT (@cx_a + 3), (@cx_sm + 1), (@cx_u + 33), @cx_c10, '동호회', '확정', 'RCPT-20260729-PE4K329P', '2026-07-29 06:00:00', '2026-07-29 07:36:00'
       UNION ALL SELECT (@cx_a + 4), (@cx_sm + 1), (@cx_u + 34), @cx_c10, '동호회', '확정', 'RCPT-20260729-4LD8WLWC', '2026-07-29 06:00:00', '2026-07-29 08:30:00'
       UNION ALL SELECT (@cx_a + 5), (@cx_sm + 1), (@cx_u + 1), @cx_c2, '동호회', '확정', 'RCPT-20260728-SXFMYCNW', '2026-07-28 04:00:00', '2026-07-28 06:24:00'
       UNION ALL SELECT (@cx_a + 6), (@cx_sm + 1), (@cx_u + 2), @cx_c2, '동호회', '확정', 'RCPT-20260728-WXTGA7WK', '2026-07-28 04:00:00', '2026-07-28 06:06:00'
       UNION ALL SELECT (@cx_a + 7), (@cx_sm + 1), (@cx_u + 6), @cx_c2, '동호회', '확정', 'RCPT-20260728-WL8TCM6E', '2026-07-28 04:00:00', '2026-07-28 05:28:00'
       UNION ALL SELECT (@cx_a + 8), (@cx_sm + 1), (@cx_u + 5), @cx_c2, '동호회', '확정', 'RCPT-20260728-3EVJ6UXD', '2026-07-28 04:00:00', '2026-07-28 06:46:00'
       UNION ALL SELECT (@cx_a + 9), (@cx_sm + 2), (@cx_u + 7), @cx_c4, '동호회', '확정', 'RCPT-20260804-X9YRB65R', '2026-08-04 23:00:00', '2026-08-05 02:00:00'
       UNION ALL SELECT (@cx_a + 10), (@cx_sm + 2), (@cx_u + 8), @cx_c4, '동호회', '확정', 'RCPT-20260804-7GHAGD98', '2026-08-04 23:00:00', '2026-08-05 01:10:00'
       UNION ALL SELECT (@cx_a + 11), (@cx_sm + 2), (@cx_u + 41), @cx_c13, '동호회', '확정', 'RCPT-20260803-AE2P5LLN', '2026-08-03 02:00:00', '2026-08-03 03:59:00'
       UNION ALL SELECT (@cx_a + 12), (@cx_sm + 2), (@cx_u + 43), @cx_c13, '동호회', '확정', 'RCPT-20260803-D2CVDKLT', '2026-08-03 02:00:00', '2026-08-03 04:30:00'
       UNION ALL SELECT (@cx_a + 13), (@cx_sm + 3), (@cx_u + 19), @cx_c7, '동호회', '확정', 'RCPT-20260805-9UYAUNM5', '2026-08-05 10:00:00', '2026-08-05 11:13:00'
       UNION ALL SELECT (@cx_a + 14), (@cx_sm + 3), (@cx_u + 20), @cx_c7, '동호회', '확정', 'RCPT-20260805-63RF9M77', '2026-08-05 10:00:00', '2026-08-05 12:23:00'
       UNION ALL SELECT (@cx_a + 15), (@cx_sm + 3), (@cx_u + 59), @cx_c19, '동호회', '확정', 'RCPT-20260805-4WDEHR9M', '2026-08-05 20:00:00', '2026-08-05 22:44:00'
       UNION ALL SELECT (@cx_a + 16), (@cx_sm + 3), (@cx_u + 60), @cx_c19, '동호회', '확정', 'RCPT-20260805-4T7KY876', '2026-08-05 20:00:00', '2026-08-05 22:02:00'
       UNION ALL SELECT (@cx_a + 17), (@cx_sm + 4), (@cx_u + 15), @cx_c6, '동호회', '확정', 'RCPT-20260804-2RRSZWKP', '2026-08-04 11:00:00', '2026-08-04 13:27:00'
       UNION ALL SELECT (@cx_a + 18), (@cx_sm + 4), (@cx_u + 18), @cx_c6, '동호회', '확정', 'RCPT-20260804-P28QYFRG', '2026-08-04 11:00:00', '2026-08-04 12:08:00'
       UNION ALL SELECT (@cx_a + 19), (@cx_sm + 4), (@cx_u + 45), @cx_c14, '동호회', '확정', 'RCPT-20260804-FD9ANJ6Q', '2026-08-04 14:00:00', '2026-08-04 16:07:00'
       UNION ALL SELECT (@cx_a + 20), (@cx_sm + 4), (@cx_u + 48), @cx_c14, '동호회', '확정', 'RCPT-20260804-SNKG2QJF', '2026-08-04 14:00:00', '2026-08-04 15:32:00'
       UNION ALL SELECT (@cx_a + 21), (@cx_sm + 5), (@cx_u + 1), @cx_c2, '동호회', '확정', 'RCPT-20260806-CRDRUK3H', '2026-08-06 08:00:00', '2026-08-06 10:48:00'
       UNION ALL SELECT (@cx_a + 22), (@cx_sm + 5), (@cx_u + 2), @cx_c2, '동호회', '확정', 'RCPT-20260806-6BGPLATW', '2026-08-06 08:00:00', '2026-08-06 09:50:00'
       UNION ALL SELECT (@cx_a + 23), (@cx_sm + 5), (@cx_u + 4), @cx_c2, '동호회', '확정', 'RCPT-20260806-D8NAJ6GN', '2026-08-06 08:00:00', '2026-08-06 10:08:00'
       UNION ALL SELECT (@cx_a + 24), (@cx_sm + 5), (@cx_u + 3), @cx_c2, '동호회', '확정', 'RCPT-20260806-RZ7B4B55', '2026-08-06 08:00:00', '2026-08-06 10:19:00'
       UNION ALL SELECT (@cx_a + 25), (@cx_sm + 5), (@cx_u + 49), @cx_c15, '동호회', '확정', 'RCPT-20260806-YQYHDGR5', '2026-08-06 23:00:00', '2026-08-07 00:56:00'
       UNION ALL SELECT (@cx_a + 26), (@cx_sm + 5), (@cx_u + 50), @cx_c15, '동호회', '확정', 'RCPT-20260806-S4AG7N4V', '2026-08-06 23:00:00', '2026-08-07 00:54:00'
       UNION ALL SELECT (@cx_a + 27), (@cx_sm + 5), (@cx_u + 53), @cx_c15, '동호회', '확정', 'RCPT-20260806-V6WPQK7P', '2026-08-06 23:00:00', '2026-08-07 00:44:00'
       UNION ALL SELECT (@cx_a + 28), (@cx_sm + 5), (@cx_u + 51), @cx_c15, '동호회', '확정', 'RCPT-20260806-RRQWY2HP', '2026-08-06 23:00:00', '2026-08-07 00:07:00'
       UNION ALL SELECT (@cx_a + 29), (@cx_sm + 6), (@cx_u + 11), @cx_c5, '동호회', '확정', 'RCPT-20260808-SS5ZS664', '2026-08-08 11:00:00', '2026-08-08 12:18:00'
       UNION ALL SELECT (@cx_a + 30), (@cx_sm + 6), (@cx_u + 13), @cx_c5, '동호회', '확정', 'RCPT-20260808-NGQF5UCV', '2026-08-08 11:00:00', '2026-08-08 12:33:00'
       UNION ALL SELECT (@cx_a + 31), (@cx_sm + 6), (@cx_u + 55), @cx_c18, '동호회', '확정', 'RCPT-20260808-CJ5C3YZE', '2026-08-08 12:00:00', '2026-08-08 14:23:00'
       UNION ALL SELECT (@cx_a + 32), (@cx_sm + 6), (@cx_u + 58), @cx_c18, '동호회', '확정', 'RCPT-20260808-LSSSC3TX', '2026-08-08 12:00:00', '2026-08-08 13:11:00'
       UNION ALL SELECT (@cx_a + 33), (@cx_sm + 7), (@cx_u + 19), @cx_c7, '동호회', '확정', 'RCPT-20260812-YQ3ALXB3', '2026-08-12 19:00:00', '2026-08-12 20:01:00'
       UNION ALL SELECT (@cx_a + 34), (@cx_sm + 7), (@cx_u + 22), @cx_c7, '동호회', '확정', 'RCPT-20260812-7YVCEV7D', '2026-08-12 19:00:00', '2026-08-12 21:31:00'
       UNION ALL SELECT (@cx_a + 35), (@cx_sm + 7), (@cx_u + 15), @cx_c6, '동호회', '확정', 'RCPT-20260810-LHVF97JE', '2026-08-10 12:00:00', '2026-08-10 13:39:00'
       UNION ALL SELECT (@cx_a + 36), (@cx_sm + 7), (@cx_u + 16), @cx_c6, '동호회', '확정', 'RCPT-20260810-NTRTKN93', '2026-08-10 12:00:00', '2026-08-10 14:08:00'
       UNION ALL SELECT (@cx_a + 37), (@cx_sm + 8), (@cx_u + 27), @cx_c9, '동호회', '확정', 'RCPT-20260812-QADRM4L4', '2026-08-12 14:00:00', '2026-08-12 15:07:00'
       UNION ALL SELECT (@cx_a + 38), (@cx_sm + 8), (@cx_u + 30), @cx_c9, '동호회', '확정', 'RCPT-20260812-S8GXGEVH', '2026-08-12 14:00:00', '2026-08-12 16:11:00'
       UNION ALL SELECT (@cx_a + 39), (@cx_sm + 8), (@cx_u + 7), @cx_c4, '동호회', '확정', 'RCPT-20260811-XF3EFMUB', '2026-08-11 10:00:00', '2026-08-11 11:04:00'
       UNION ALL SELECT (@cx_a + 40), (@cx_sm + 8), (@cx_u + 10), @cx_c4, '동호회', '확정', 'RCPT-20260811-22UVYVFZ', '2026-08-11 10:00:00', '2026-08-11 11:03:00'
       UNION ALL SELECT (@cx_a + 41), (@cx_sm + 9), (@cx_u + 59), @cx_c19, '동호회', '확정', 'RCPT-20260813-3JMAVHNK', '2026-08-13 08:00:00', '2026-08-13 09:20:00'
       UNION ALL SELECT (@cx_a + 42), (@cx_sm + 9), (@cx_u + 61), @cx_c19, '동호회', '확정', 'RCPT-20260813-ASNMMJ7U', '2026-08-13 08:00:00', '2026-08-13 10:12:00'
       UNION ALL SELECT (@cx_a + 43), (@cx_sm + 9), (@cx_u + 37), @cx_c11, '동호회', '확정', 'RCPT-20260812-TY42KP22', '2026-08-12 03:00:00', '2026-08-12 05:09:00'
       UNION ALL SELECT (@cx_a + 44), (@cx_sm + 9), (@cx_u + 38), @cx_c11, '동호회', '확정', 'RCPT-20260812-W7BVJB6P', '2026-08-12 03:00:00', '2026-08-12 05:09:00'
       UNION ALL SELECT (@cx_a + 45), (@cx_sm + 10), (@cx_u + 41), @cx_c13, '동호회', '확정', 'RCPT-20260812-67AR7LBP', '2026-08-12 22:00:00', '2026-08-13 00:09:00'
       UNION ALL SELECT (@cx_a + 46), (@cx_sm + 10), (@cx_u + 42), @cx_c13, '동호회', '확정', 'RCPT-20260812-PVPVPNW9', '2026-08-12 22:00:00', '2026-08-13 00:46:00'
       UNION ALL SELECT (@cx_a + 47), (@cx_sm + 10), (@cx_u + 23), @cx_c8, '동호회', '확정', 'RCPT-20260813-2YWJR83K', '2026-08-13 22:00:00', '2026-08-14 00:19:00'
       UNION ALL SELECT (@cx_a + 48), (@cx_sm + 10), (@cx_u + 26), @cx_c8, '동호회', '확정', 'RCPT-20260813-8ZQ795HL', '2026-08-13 22:00:00', '2026-08-14 00:40:00'
       UNION ALL SELECT (@cx_a + 49), (@cx_sm + 11), (@cx_u + 45), @cx_c14, '동호회', '확정', 'RCPT-20260814-8FMHW38S', '2026-08-14 12:00:00', '2026-08-14 13:32:00'
       UNION ALL SELECT (@cx_a + 50), (@cx_sm + 11), (@cx_u + 46), @cx_c14, '동호회', '확정', 'RCPT-20260814-P3M4223M', '2026-08-14 12:00:00', '2026-08-14 14:58:00'
       UNION ALL SELECT (@cx_a + 51), (@cx_sm + 11), (@cx_u + 37), @cx_c11, '동호회', '확정', 'RCPT-20260816-M3XUL8H5', '2026-08-16 17:00:00', '2026-08-16 18:05:00'
       UNION ALL SELECT (@cx_a + 52), (@cx_sm + 11), (@cx_u + 40), @cx_c11, '동호회', '확정', 'RCPT-20260816-8V99MQHF', '2026-08-16 17:00:00', '2026-08-16 18:13:00'
       UNION ALL SELECT (@cx_a + 53), (@cx_sm + 12), (@cx_u + 27), @cx_c9, '동호회', '확정', 'RCPT-20260817-8N3YA8K4', '2026-08-17 17:00:00', '2026-08-17 18:40:00'
       UNION ALL SELECT (@cx_a + 54), (@cx_sm + 12), (@cx_u + 29), @cx_c9, '동호회', '확정', 'RCPT-20260817-LQ2BNS4T', '2026-08-17 17:00:00', '2026-08-17 18:03:00'
       UNION ALL SELECT (@cx_a + 55), (@cx_sm + 12), (@cx_u + 11), @cx_c5, '동호회', '확정', 'RCPT-20260815-T9HN8D3X', '2026-08-15 13:00:00', '2026-08-15 15:15:00'
       UNION ALL SELECT (@cx_a + 56), (@cx_sm + 12), (@cx_u + 12), @cx_c5, '동호회', '확정', 'RCPT-20260815-VNE3XE5M', '2026-08-15 13:00:00', '2026-08-15 15:28:00'
       UNION ALL SELECT (@cx_a + 57), (@cx_sm + 13), (@cx_u + 23), @cx_c8, '동호회', '확정', 'RCPT-20260820-UCDTYLRW', '2026-08-20 16:30:00', '2026-08-20 19:17:00'
       UNION ALL SELECT (@cx_a + 58), (@cx_sm + 13), (@cx_u + 25), @cx_c8, '동호회', '확정', 'RCPT-20260820-6UFUQX4A', '2026-08-20 16:30:00', '2026-08-20 18:25:00'
       UNION ALL SELECT (@cx_a + 59), (@cx_sm + 13), (@cx_u + 55), @cx_c18, '동호회', '확정', 'RCPT-20260820-7U9NVSTU', '2026-08-20 15:30:00', '2026-08-20 18:20:00'
       UNION ALL SELECT (@cx_a + 60), (@cx_sm + 13), (@cx_u + 57), @cx_c18, '동호회', '확정', 'RCPT-20260820-9EBZA5KB', '2026-08-20 15:30:00', '2026-08-20 17:44:00'
       UNION ALL SELECT (@cx_a + 61), (@cx_sm + 14), (@cx_u + 15), @cx_c6, '동호회', '확정', 'RCPT-20260823-UZBWBC8G', '2026-08-23 17:00:00', '2026-08-23 19:58:00'
       UNION ALL SELECT (@cx_a + 62), (@cx_sm + 14), (@cx_u + 17), @cx_c6, '동호회', '확정', 'RCPT-20260823-H9TPDFMH', '2026-08-23 17:00:00', '2026-08-23 18:45:00'
       UNION ALL SELECT (@cx_a + 63), (@cx_sm + 14), (@cx_u + 19), @cx_c7, '동호회', '확정', 'RCPT-20260823-L76L7ZBS', '2026-08-23 15:00:00', '2026-08-23 17:42:00'
       UNION ALL SELECT (@cx_a + 64), (@cx_sm + 14), (@cx_u + 21), @cx_c7, '동호회', '확정', 'RCPT-20260823-9UT6TFLC', '2026-08-23 15:00:00', '2026-08-23 17:42:00'
       UNION ALL SELECT (@cx_a + 65), (@cx_sm + 15), (@cx_u + 23), @cx_c8, '동호회', '확정', 'RCPT-20260826-YHXXY5Y6', '2026-08-26 03:30:00', '2026-08-26 04:38:00'
       UNION ALL SELECT (@cx_a + 66), (@cx_sm + 15), (@cx_u + 24), @cx_c8, '동호회', '확정', 'RCPT-20260826-UNVGKMV2', '2026-08-26 03:30:00', '2026-08-26 05:39:00'
       UNION ALL SELECT (@cx_a + 67), (@cx_sm + 15), (@cx_u + 7), @cx_c4, '동호회', '확정', 'RCPT-20260824-N5C7H37Y', '2026-08-24 14:30:00', '2026-08-24 15:44:00'
       UNION ALL SELECT (@cx_a + 68), (@cx_sm + 15), (@cx_u + 9), @cx_c4, '동호회', '확정', 'RCPT-20260824-QFFFR78E', '2026-08-24 14:30:00', '2026-08-24 17:03:00'
       UNION ALL SELECT (@cx_a + 69), (@cx_sm + 16), (@cx_u + 41), @cx_c13, '동호회', '확정', 'RCPT-20260825-4THM5QF6', '2026-08-25 23:00:00', '2026-08-26 01:56:00'
       UNION ALL SELECT (@cx_a + 70), (@cx_sm + 16), (@cx_u + 44), @cx_c13, '동호회', '확정', 'RCPT-20260825-8ANMAKU3', '2026-08-25 23:00:00', '2026-08-26 00:16:00'
       UNION ALL SELECT (@cx_a + 71), (@cx_sm + 16), (@cx_u + 27), @cx_c9, '동호회', '확정', 'RCPT-20260827-R3B9MWXU', '2026-08-27 02:00:00', '2026-08-27 04:34:00'
       UNION ALL SELECT (@cx_a + 72), (@cx_sm + 16), (@cx_u + 28), @cx_c9, '동호회', '확정', 'RCPT-20260827-W7UREAUG', '2026-08-27 02:00:00', '2026-08-27 03:51:00'
       UNION ALL SELECT (@cx_a + 73), (@cx_sm + 17), (@cx_u + 37), @cx_c11, '동호회', '확정', 'RCPT-20260828-DW49WURF', '2026-08-28 01:00:00', '2026-08-28 03:03:00'
       UNION ALL SELECT (@cx_a + 74), (@cx_sm + 17), (@cx_u + 39), @cx_c11, '동호회', '확정', 'RCPT-20260828-L3P9WAUF', '2026-08-28 01:00:00', '2026-08-28 03:49:00'
       UNION ALL SELECT (@cx_a + 75), (@cx_sm + 17), (@cx_u + 45), @cx_c14, '동호회', '확정', 'RCPT-20260827-NAXWD9QA', '2026-08-27 04:00:00', '2026-08-27 05:01:00'
       UNION ALL SELECT (@cx_a + 76), (@cx_sm + 17), (@cx_u + 47), @cx_c14, '동호회', '확정', 'RCPT-20260827-DW8NXG4C', '2026-08-27 04:00:00', '2026-08-27 05:44:00'
       UNION ALL SELECT (@cx_a + 77), (@cx_sm + 18), (@cx_u + 55), @cx_c18, '동호회', '확정', 'RCPT-20260829-JZ6K4QTC', '2026-08-29 19:00:00', '2026-08-29 20:09:00'
       UNION ALL SELECT (@cx_a + 78), (@cx_sm + 18), (@cx_u + 56), @cx_c18, '동호회', '확정', 'RCPT-20260829-7E4UPP9C', '2026-08-29 19:00:00', '2026-08-29 20:03:00'
       UNION ALL SELECT (@cx_a + 79), (@cx_sm + 18), (@cx_u + 11), @cx_c5, '동호회', '확정', 'RCPT-20260830-WMEYLEEE', '2026-08-30 17:00:00', '2026-08-30 18:14:00'
       UNION ALL SELECT (@cx_a + 80), (@cx_sm + 18), (@cx_u + 14), @cx_c5, '동호회', '확정', 'RCPT-20260830-PCMARLU6', '2026-08-30 17:00:00', '2026-08-30 18:59:00'
       UNION ALL SELECT (@cx_a + 81), (@cx_sm + 19), (@cx_u + 7), @cx_c4, '동호회', '확정', 'RCPT-20260906-M3RAVP6J', '2026-09-06 23:00:00', '2026-09-07 00:11:00'
       UNION ALL SELECT (@cx_a + 82), (@cx_sm + 19), (@cx_u + 8), @cx_c4, '동호회', '확정', 'RCPT-20260906-29WVTSLQ', '2026-09-06 23:00:00', '2026-09-07 00:45:00'
       UNION ALL SELECT (@cx_a + 83), (@cx_sm + 19), (@cx_u + 55), @cx_c18, '동호회', '확정', 'RCPT-20260909-75CJNURF', '2026-09-09 01:00:00', '2026-09-09 02:09:00'
       UNION ALL SELECT (@cx_a + 84), (@cx_sm + 19), (@cx_u + 57), @cx_c18, '동호회', '확정', 'RCPT-20260909-MZTLWLEP', '2026-09-09 01:00:00', '2026-09-09 02:43:00'
       UNION ALL SELECT (@cx_a + 85), (@cx_sm + 20), (@cx_u + 49), @cx_c15, '동호회', '확정', 'RCPT-20260909-EVC62H7W', '2026-09-09 10:00:00', '2026-09-09 11:40:00'
       UNION ALL SELECT (@cx_a + 86), (@cx_sm + 20), (@cx_u + 50), @cx_c15, '동호회', '확정', 'RCPT-20260909-6EJZDQAE', '2026-09-09 10:00:00', '2026-09-09 12:46:00'
       UNION ALL SELECT (@cx_a + 87), (@cx_sm + 20), (@cx_u + 52), @cx_c15, '동호회', '확정', 'RCPT-20260909-JB9VD52Z', '2026-09-09 10:00:00', '2026-09-09 11:14:00'
       UNION ALL SELECT (@cx_a + 88), (@cx_sm + 20), (@cx_u + 54), @cx_c15, '동호회', '확정', 'RCPT-20260909-5HDH3JCQ', '2026-09-09 10:00:00', '2026-09-09 12:48:00'
       UNION ALL SELECT (@cx_a + 89), (@cx_sm + 20), (@cx_u + 31), @cx_c10, '동호회', '확정', 'RCPT-20260907-LKVBVYWA', '2026-09-07 09:00:00', '2026-09-07 10:13:00'
       UNION ALL SELECT (@cx_a + 90), (@cx_sm + 20), (@cx_u + 32), @cx_c10, '동호회', '확정', 'RCPT-20260907-J4YVLGCP', '2026-09-07 09:00:00', '2026-09-07 11:22:00'
       UNION ALL SELECT (@cx_a + 91), (@cx_sm + 20), (@cx_u + 35), @cx_c10, '동호회', '확정', 'RCPT-20260907-R4WB72B8', '2026-09-07 09:00:00', '2026-09-07 11:05:00'
       UNION ALL SELECT (@cx_a + 92), (@cx_sm + 20), (@cx_u + 36), @cx_c10, '동호회', '확정', 'RCPT-20260907-A5BJN67E', '2026-09-07 09:00:00', '2026-09-07 11:01:00'
       UNION ALL SELECT (@cx_a + 93), (@cx_sm + 21), (@cx_u + 41), @cx_c13, '동호회', '확정', 'RCPT-20260908-XE3DGP77', '2026-09-08 01:00:00', '2026-09-08 03:15:00'
       UNION ALL SELECT (@cx_a + 94), (@cx_sm + 21), (@cx_u + 44), @cx_c13, '동호회', '확정', 'RCPT-20260908-EXRZUBKP', '2026-09-08 01:00:00', '2026-09-08 02:24:00'
       UNION ALL SELECT (@cx_a + 95), (@cx_sm + 21), (@cx_u + 11), @cx_c5, '동호회', '확정', 'RCPT-20260908-VPSGTVM8', '2026-09-08 04:00:00', '2026-09-08 05:56:00'
       UNION ALL SELECT (@cx_a + 96), (@cx_sm + 21), (@cx_u + 12), @cx_c5, '동호회', '확정', 'RCPT-20260908-L23CBKFV', '2026-09-08 04:00:00', '2026-09-08 05:04:00'
       UNION ALL SELECT (@cx_a + 97), (@cx_sm + 22), (@cx_u + 59), @cx_c19, '동호회', '확정', 'RCPT-20260911-LZ95KQ93', '2026-09-11 03:00:00', '2026-09-11 05:01:00'
       UNION ALL SELECT (@cx_a + 98), (@cx_sm + 22), (@cx_u + 62), @cx_c19, '동호회', '확정', 'RCPT-20260911-XKF9449L', '2026-09-11 03:00:00', '2026-09-11 04:20:00'
       UNION ALL SELECT (@cx_a + 99), (@cx_sm + 22), (@cx_u + 45), @cx_c14, '동호회', '확정', 'RCPT-20260908-UK55HV9A', '2026-09-08 23:00:00', '2026-09-09 00:20:00'
       UNION ALL SELECT (@cx_a + 100), (@cx_sm + 22), (@cx_u + 46), @cx_c14, '동호회', '확정', 'RCPT-20260908-5ENE38TP', '2026-09-08 23:00:00', '2026-09-09 01:07:00'
       UNION ALL SELECT (@cx_a + 101), (@cx_sm + 23), (@cx_u + 23), @cx_c8, '동호회', '확정', 'RCPT-20260911-D4FPU6HG', '2026-09-11 10:30:00', '2026-09-11 12:47:00'
       UNION ALL SELECT (@cx_a + 102), (@cx_sm + 23), (@cx_u + 25), @cx_c8, '동호회', '확정', 'RCPT-20260911-9LF4FK52', '2026-09-11 10:30:00', '2026-09-11 11:38:00'
       UNION ALL SELECT (@cx_a + 103), (@cx_sm + 23), (@cx_u + 27), @cx_c9, '동호회', '확정', 'RCPT-20260912-DJE47QW5', '2026-09-12 14:30:00', '2026-09-12 17:23:00'
       UNION ALL SELECT (@cx_a + 104), (@cx_sm + 23), (@cx_u + 29), @cx_c9, '동호회', '확정', 'RCPT-20260912-ZQVC32PM', '2026-09-12 14:30:00', '2026-09-12 15:33:00'
       UNION ALL SELECT (@cx_a + 105), (@cx_sm + 24), (@cx_u + 15), @cx_c6, '동호회', '확정', 'RCPT-20260914-E55M6D4K', '2026-09-14 17:00:00', '2026-09-14 19:41:00'
       UNION ALL SELECT (@cx_a + 106), (@cx_sm + 24), (@cx_u + 18), @cx_c6, '동호회', '확정', 'RCPT-20260914-U9GM4J2U', '2026-09-14 17:00:00', '2026-09-14 18:18:00'
       UNION ALL SELECT (@cx_a + 107), (@cx_sm + 24), (@cx_u + 59), @cx_c19, '동호회', '확정', 'RCPT-20260914-FRYT39YF', '2026-09-14 14:00:00', '2026-09-14 15:45:00'
       UNION ALL SELECT (@cx_a + 108), (@cx_sm + 24), (@cx_u + 61), @cx_c19, '동호회', '확정', 'RCPT-20260914-GBJF3EL6', '2026-09-14 14:00:00', '2026-09-14 15:54:00'
       UNION ALL SELECT (@cx_a + 109), (@cx_sm + 25), (@cx_u + 31), @cx_c10, '동호회', '확정', 'RCPT-20260915-EBX2DYST', '2026-09-15 04:00:00', '2026-09-15 06:36:00'
       UNION ALL SELECT (@cx_a + 110), (@cx_sm + 25), (@cx_u + 32), @cx_c10, '동호회', '확정', 'RCPT-20260915-44A9RSZS', '2026-09-15 04:00:00', '2026-09-15 06:39:00'
       UNION ALL SELECT (@cx_a + 111), (@cx_sm + 25), (@cx_u + 34), @cx_c10, '동호회', '확정', 'RCPT-20260915-CSTRYCQX', '2026-09-15 04:00:00', '2026-09-15 05:55:00'
       UNION ALL SELECT (@cx_a + 112), (@cx_sm + 25), (@cx_u + 36), @cx_c10, '동호회', '확정', 'RCPT-20260915-MA5AAGNS', '2026-09-15 04:00:00', '2026-09-15 06:44:00'
       UNION ALL SELECT (@cx_a + 113), (@cx_sm + 25), (@cx_u + 1), @cx_c2, '동호회', '확정', 'RCPT-20260916-PQEJ7ZHE', '2026-09-16 10:00:00', '2026-09-16 12:04:00'
       UNION ALL SELECT (@cx_a + 114), (@cx_sm + 25), (@cx_u + 2), @cx_c2, '동호회', '확정', 'RCPT-20260916-CV36ST64', '2026-09-16 10:00:00', '2026-09-16 12:24:00'
       UNION ALL SELECT (@cx_a + 115), (@cx_sm + 25), (@cx_u + 5), @cx_c2, '동호회', '확정', 'RCPT-20260916-Z44TVQLF', '2026-09-16 10:00:00', '2026-09-16 11:19:00'
       UNION ALL SELECT (@cx_a + 116), (@cx_sm + 25), (@cx_u + 6), @cx_c2, '동호회', '확정', 'RCPT-20260916-BH3XT8JV', '2026-09-16 10:00:00', '2026-09-16 11:47:00'
       UNION ALL SELECT (@cx_a + 117), (@cx_sm + 26), (@cx_u + 49), @cx_c15, '동호회', '확정', 'RCPT-20260922-U5JE9H7R', '2026-09-22 09:00:00', '2026-09-22 10:49:00'
       UNION ALL SELECT (@cx_a + 118), (@cx_sm + 26), (@cx_u + 50), @cx_c15, '동호회', '확정', 'RCPT-20260922-3XKQHW9M', '2026-09-22 09:00:00', '2026-09-22 10:33:00'
       UNION ALL SELECT (@cx_a + 119), (@cx_sm + 26), (@cx_u + 53), @cx_c15, '동호회', '확정', 'RCPT-20260922-ACSXRKPA', '2026-09-22 09:00:00', '2026-09-22 10:39:00'
       UNION ALL SELECT (@cx_a + 120), (@cx_sm + 26), (@cx_u + 54), @cx_c15, '동호회', '확정', 'RCPT-20260922-YB7WF5T4', '2026-09-22 09:00:00', '2026-09-22 11:54:00'
       UNION ALL SELECT (@cx_a + 121), (@cx_sm + 26), (@cx_u + 1), @cx_c2, '동호회', '확정', 'RCPT-20260922-AEUSPPHL', '2026-09-22 12:00:00', '2026-09-22 13:21:00'
       UNION ALL SELECT (@cx_a + 122), (@cx_sm + 26), (@cx_u + 2), @cx_c2, '동호회', '확정', 'RCPT-20260922-JTK274M8', '2026-09-22 12:00:00', '2026-09-22 13:18:00'
       UNION ALL SELECT (@cx_a + 123), (@cx_sm + 26), (@cx_u + 4), @cx_c2, '동호회', '확정', 'RCPT-20260922-CVFE5P48', '2026-09-22 12:00:00', '2026-09-22 13:35:00'
       UNION ALL SELECT (@cx_a + 124), (@cx_sm + 26), (@cx_u + 3), @cx_c2, '동호회', '확정', 'RCPT-20260922-AHNMUW2H', '2026-09-22 12:00:00', '2026-09-22 13:29:00'
       UNION ALL SELECT (@cx_a + 125), (@cx_sm + 27), (@cx_u + 19), @cx_c7, '동호회', '확정', 'RCPT-20260921-DQJNW4HU', '2026-09-21 18:00:00', '2026-09-21 19:19:00'
       UNION ALL SELECT (@cx_a + 126), (@cx_sm + 27), (@cx_u + 21), @cx_c7, '동호회', '확정', 'RCPT-20260921-6U4PWNYC', '2026-09-21 18:00:00', '2026-09-21 20:02:00'
       UNION ALL SELECT (@cx_a + 127), (@cx_sm + 27), (@cx_u + 37), @cx_c11, '동호회', '확정', 'RCPT-20260921-QWEPPN23', '2026-09-21 14:00:00', '2026-09-21 15:49:00'
       UNION ALL SELECT (@cx_a + 128), (@cx_sm + 27), (@cx_u + 40), @cx_c11, '동호회', '확정', 'RCPT-20260921-UDQ548A7', '2026-09-21 14:00:00', '2026-09-21 16:10:00'
       UNION ALL SELECT (@cx_a + 129), (@cx_sm + 28), (@cx_u + 23), @cx_c8, '동호회', '확정', 'RCPT-20260925-NS8KZNYZ', '2026-09-25 14:30:00', '2026-09-25 16:22:00'
       UNION ALL SELECT (@cx_a + 130), (@cx_sm + 28), (@cx_u + 24), @cx_c8, '동호회', '확정', 'RCPT-20260925-NF6NM63M', '2026-09-25 14:30:00', '2026-09-25 16:07:00'
       UNION ALL SELECT (@cx_a + 131), (@cx_sm + 28), (@cx_u + 55), @cx_c18, '동호회', '확정', 'RCPT-20260926-2NYS8JTB', '2026-09-26 14:30:00', '2026-09-26 16:45:00'
       UNION ALL SELECT (@cx_a + 132), (@cx_sm + 28), (@cx_u + 56), @cx_c18, '동호회', '확정', 'RCPT-20260926-HMNN8T8A', '2026-09-26 14:30:00', '2026-09-26 16:01:00'
       UNION ALL SELECT (@cx_a + 133), (@cx_sm + 29), (@cx_u + 41), @cx_c13, '동호회', '확정', 'RCPT-20260929-5YZT87YG', '2026-09-29 23:00:00', '2026-09-30 00:33:00'
       UNION ALL SELECT (@cx_a + 134), (@cx_sm + 29), (@cx_u + 43), @cx_c13, '동호회', '확정', 'RCPT-20260929-5KTLMUUA', '2026-09-29 23:00:00', '2026-09-30 00:09:00'
       UNION ALL SELECT (@cx_a + 135), (@cx_sm + 29), (@cx_u + 27), @cx_c9, '동호회', '확정', 'RCPT-20261001-MERRPG9F', '2026-10-01 08:00:00', '2026-10-01 10:12:00'
       UNION ALL SELECT (@cx_a + 136), (@cx_sm + 29), (@cx_u + 28), @cx_c9, '동호회', '확정', 'RCPT-20261001-4U8LRQJ4', '2026-10-01 08:00:00', '2026-10-01 10:34:00'
       UNION ALL SELECT (@cx_a + 137), (@cx_sm + 30), (@cx_u + 59), @cx_c19, '동호회', '확정', 'RCPT-20261001-3JEVGQJJ', '2026-10-01 00:00:00', '2026-10-01 02:25:00'
       UNION ALL SELECT (@cx_a + 138), (@cx_sm + 30), (@cx_u + 62), @cx_c19, '동호회', '확정', 'RCPT-20261001-Y6C87UR2', '2026-10-01 00:00:00', '2026-10-01 01:15:00'
       UNION ALL SELECT (@cx_a + 139), (@cx_sm + 30), (@cx_u + 19), @cx_c7, '동호회', '확정', 'RCPT-20261001-UHJB69QK', '2026-10-01 02:00:00', '2026-10-01 03:03:00'
       UNION ALL SELECT (@cx_a + 140), (@cx_sm + 30), (@cx_u + 22), @cx_c7, '동호회', '확정', 'RCPT-20261001-LKBSC8EH', '2026-10-01 02:00:00', '2026-10-01 04:13:00'
       UNION ALL SELECT (@cx_a + 141), (@cx_sm + 31), (@cx_u + 37), @cx_c11, '동호회', '확정', 'RCPT-20261002-PJ7XM4FF', '2026-10-02 01:00:00', '2026-10-02 02:37:00'
       UNION ALL SELECT (@cx_a + 142), (@cx_sm + 31), (@cx_u + 38), @cx_c11, '동호회', '확정', 'RCPT-20261002-3H79UB64', '2026-10-02 01:00:00', '2026-10-02 02:24:00'
       UNION ALL SELECT (@cx_a + 143), (@cx_sm + 31), (@cx_u + 15), @cx_c6, '동호회', '확정', 'RCPT-20260930-J9MEX4VE', '2026-09-30 04:00:00', '2026-09-30 05:46:00'
       UNION ALL SELECT (@cx_a + 144), (@cx_sm + 31), (@cx_u + 17), @cx_c6, '동호회', '확정', 'RCPT-20260930-HAE6CLAP', '2026-09-30 04:00:00', '2026-09-30 06:24:00'
       UNION ALL SELECT (@cx_a + 145), (@cx_sm + 32), (@cx_u + 11), @cx_c5, '동호회', '확정', 'RCPT-20261006-MLF78643', '2026-10-06 14:00:00', '2026-10-06 16:38:00'
       UNION ALL SELECT (@cx_a + 146), (@cx_sm + 32), (@cx_u + 14), @cx_c5, '동호회', '확정', 'RCPT-20261006-86LQXAEQ', '2026-10-06 14:00:00', '2026-10-06 16:29:00'
       UNION ALL SELECT (@cx_a + 147), (@cx_sm + 32), (@cx_u + 7), @cx_c4, '동호회', '확정', 'RCPT-20261004-TBTTUDRQ', '2026-10-04 11:00:00', '2026-10-04 12:14:00'
       UNION ALL SELECT (@cx_a + 148), (@cx_sm + 32), (@cx_u + 9), @cx_c4, '동호회', '확정', 'RCPT-20261004-QCZCTLQ9', '2026-10-04 11:00:00', '2026-10-04 12:28:00'
       UNION ALL SELECT (@cx_a + 149), (@cx_sm + 33), (@cx_u + 49), @cx_c15, '동호회', '확정', 'RCPT-20261006-EZH25R8B', '2026-10-06 15:00:00', '2026-10-06 16:10:00'
       UNION ALL SELECT (@cx_a + 150), (@cx_sm + 33), (@cx_u + 50), @cx_c15, '동호회', '확정', 'RCPT-20261006-VP7QG4WH', '2026-10-06 15:00:00', '2026-10-06 17:34:00'
       UNION ALL SELECT (@cx_a + 151), (@cx_sm + 33), (@cx_u + 52), @cx_c15, '동호회', '확정', 'RCPT-20261006-MM2WBENV', '2026-10-06 15:00:00', '2026-10-06 17:55:00'
       UNION ALL SELECT (@cx_a + 152), (@cx_sm + 33), (@cx_u + 51), @cx_c15, '동호회', '확정', 'RCPT-20261006-8MZBFKCM', '2026-10-06 15:00:00', '2026-10-06 17:34:00'
       UNION ALL SELECT (@cx_a + 153), (@cx_sm + 33), (@cx_u + 31), @cx_c10, '동호회', '확정', 'RCPT-20261005-WLEFFMS4', '2026-10-05 15:00:00', '2026-10-05 17:51:00'
       UNION ALL SELECT (@cx_a + 154), (@cx_sm + 33), (@cx_u + 32), @cx_c10, '동호회', '확정', 'RCPT-20261005-WZ8R8GEW', '2026-10-05 15:00:00', '2026-10-05 17:44:00'
       UNION ALL SELECT (@cx_a + 155), (@cx_sm + 33), (@cx_u + 35), @cx_c10, '동호회', '확정', 'RCPT-20261005-87XC6NBU', '2026-10-05 15:00:00', '2026-10-05 16:49:00'
       UNION ALL SELECT (@cx_a + 156), (@cx_sm + 33), (@cx_u + 33), @cx_c10, '동호회', '확정', 'RCPT-20261005-F9Z3BZFM', '2026-10-05 15:00:00', '2026-10-05 16:13:00'
       UNION ALL SELECT (@cx_a + 157), (@cx_sm + 34), (@cx_u + 23), @cx_c8, '동호회', '확정', 'RCPT-20261011-LE9HCMMF', '2026-10-11 11:30:00', '2026-10-11 12:42:00'
       UNION ALL SELECT (@cx_a + 158), (@cx_sm + 34), (@cx_u + 26), @cx_c8, '동호회', '확정', 'RCPT-20261011-MZUQADHQ', '2026-10-11 11:30:00', '2026-10-11 14:09:00'
       UNION ALL SELECT (@cx_a + 159), (@cx_sm + 34), (@cx_u + 11), @cx_c5, '동호회', '확정', 'RCPT-20261012-YL6UNW5W', '2026-10-12 13:30:00', '2026-10-12 14:54:00'
       UNION ALL SELECT (@cx_a + 160), (@cx_sm + 34), (@cx_u + 13), @cx_c5, '동호회', '확정', 'RCPT-20261012-RR2GRW6X', '2026-10-12 13:30:00', '2026-10-12 15:41:00'
       UNION ALL SELECT (@cx_a + 161), (@cx_sm + 35), (@cx_u + 45), @cx_c14, '동호회', '확정', 'RCPT-20261012-VX2TZMFP', '2026-10-12 11:00:00', '2026-10-12 12:33:00'
       UNION ALL SELECT (@cx_a + 162), (@cx_sm + 35), (@cx_u + 48), @cx_c14, '동호회', '확정', 'RCPT-20261012-Q7G2XX63', '2026-10-12 11:00:00', '2026-10-12 12:41:00'
       UNION ALL SELECT (@cx_a + 163), (@cx_sm + 35), (@cx_u + 19), @cx_c7, '동호회', '확정', 'RCPT-20261013-Z76J5ZRT', '2026-10-13 20:00:00', '2026-10-13 22:11:00'
       UNION ALL SELECT (@cx_a + 164), (@cx_sm + 35), (@cx_u + 20), @cx_c7, '동호회', '확정', 'RCPT-20261013-ZJMXA5XM', '2026-10-13 20:00:00', '2026-10-13 22:13:00'
       UNION ALL SELECT (@cx_a + 165), (@cx_sm + 36), (@cx_u + 49), @cx_c15, '동호회', '확정', 'RCPT-20261013-T4B4SY8S', '2026-10-13 09:00:00', '2026-10-13 10:07:00'
       UNION ALL SELECT (@cx_a + 166), (@cx_sm + 36), (@cx_u + 50), @cx_c15, '동호회', '확정', 'RCPT-20261013-8FPCAD2B', '2026-10-13 09:00:00', '2026-10-13 10:55:00'
       UNION ALL SELECT (@cx_a + 167), (@cx_sm + 36), (@cx_u + 51), @cx_c15, '동호회', '확정', 'RCPT-20261013-C3N27AE8', '2026-10-13 09:00:00', '2026-10-13 10:46:00'
       UNION ALL SELECT (@cx_a + 168), (@cx_sm + 36), (@cx_u + 54), @cx_c15, '동호회', '확정', 'RCPT-20261013-UM9UN4NB', '2026-10-13 09:00:00', '2026-10-13 10:19:00'
       UNION ALL SELECT (@cx_a + 169), (@cx_sm + 36), (@cx_u + 31), @cx_c10, '동호회', '확정', 'RCPT-20261012-AX632WYJ', '2026-10-12 09:00:00', '2026-10-12 11:38:00'
       UNION ALL SELECT (@cx_a + 170), (@cx_sm + 36), (@cx_u + 32), @cx_c10, '동호회', '확정', 'RCPT-20261012-RQPNX6M9', '2026-10-12 09:00:00', '2026-10-12 10:40:00'
       UNION ALL SELECT (@cx_a + 171), (@cx_sm + 36), (@cx_u + 35), @cx_c10, '동호회', '확정', 'RCPT-20261012-5H5L8GRP', '2026-10-12 09:00:00', '2026-10-12 11:04:00'
       UNION ALL SELECT (@cx_a + 172), (@cx_sm + 36), (@cx_u + 36), @cx_c10, '동호회', '확정', 'RCPT-20261012-9FNALW3K', '2026-10-12 09:00:00', '2026-10-12 10:50:00'
       UNION ALL SELECT (@cx_a + 173), (@cx_sm + 37), (@cx_u + 41), @cx_c13, '동호회', '확정', 'RCPT-20261015-KML9X22Z', '2026-10-15 00:00:00', '2026-10-15 01:30:00'
       UNION ALL SELECT (@cx_a + 174), (@cx_sm + 37), (@cx_u + 42), @cx_c13, '동호회', '확정', 'RCPT-20261015-D8QG3994', '2026-10-15 00:00:00', '2026-10-15 02:58:00'
       UNION ALL SELECT (@cx_a + 175), (@cx_sm + 37), (@cx_u + 7), @cx_c4, '동호회', '확정', 'RCPT-20261014-NCAM8CMY', '2026-10-14 01:00:00', '2026-10-14 02:31:00'
       UNION ALL SELECT (@cx_a + 176), (@cx_sm + 37), (@cx_u + 10), @cx_c4, '동호회', '확정', 'RCPT-20261014-AN4A2GEA', '2026-10-14 01:00:00', '2026-10-14 03:31:00'
       UNION ALL SELECT (@cx_a + 177), (@cx_sm + 38), (@cx_u + 15), @cx_c6, '동호회', '확정', 'RCPT-20261014-EF67LSV4', '2026-10-14 17:00:00', '2026-10-14 18:20:00'
       UNION ALL SELECT (@cx_a + 178), (@cx_sm + 38), (@cx_u + 16), @cx_c6, '동호회', '확정', 'RCPT-20261014-273DAC2J', '2026-10-14 17:00:00', '2026-10-14 18:02:00'
       UNION ALL SELECT (@cx_a + 179), (@cx_sm + 38), (@cx_u + 59), @cx_c19, '동호회', '확정', 'RCPT-20261013-JCV98A93', '2026-10-13 12:00:00', '2026-10-13 13:37:00'
       UNION ALL SELECT (@cx_a + 180), (@cx_sm + 38), (@cx_u + 60), @cx_c19, '동호회', '확정', 'RCPT-20261013-8RJVW7ES', '2026-10-13 12:00:00', '2026-10-13 13:27:00'
       UNION ALL SELECT (@cx_a + 181), (@cx_sm + 39), (@cx_u + 49), @cx_c15, '동호회', '확정', 'RCPT-20261019-WV87BJC4', '2026-10-19 10:00:00', '2026-10-19 11:06:00'
       UNION ALL SELECT (@cx_a + 182), (@cx_sm + 39), (@cx_u + 50), @cx_c15, '동호회', '확정', 'RCPT-20261019-9H6CYKFX', '2026-10-19 10:00:00', '2026-10-19 11:19:00'
       UNION ALL SELECT (@cx_a + 183), (@cx_sm + 39), (@cx_u + 53), @cx_c15, '동호회', '확정', 'RCPT-20261019-R8SEGUU4', '2026-10-19 10:00:00', '2026-10-19 12:32:00'
       UNION ALL SELECT (@cx_a + 184), (@cx_sm + 39), (@cx_u + 52), @cx_c15, '동호회', '확정', 'RCPT-20261019-8BY6W2A3', '2026-10-19 10:00:00', '2026-10-19 11:30:00'
       UNION ALL SELECT (@cx_a + 185), (@cx_sm + 39), (@cx_u + 1), @cx_c2, '동호회', '확정', 'RCPT-20261020-DLRYAYSB', '2026-10-20 11:00:00', '2026-10-20 13:46:00'
       UNION ALL SELECT (@cx_a + 186), (@cx_sm + 39), (@cx_u + 2), @cx_c2, '동호회', '확정', 'RCPT-20261020-W2XU6H6X', '2026-10-20 11:00:00', '2026-10-20 12:07:00'
       UNION ALL SELECT (@cx_a + 187), (@cx_sm + 39), (@cx_u + 3), @cx_c2, '동호회', '확정', 'RCPT-20261020-72SDL6GP', '2026-10-20 11:00:00', '2026-10-20 13:18:00'
       UNION ALL SELECT (@cx_a + 188), (@cx_sm + 39), (@cx_u + 4), @cx_c2, '동호회', '확정', 'RCPT-20261020-B5VKDLM8', '2026-10-20 11:00:00', '2026-10-20 13:49:00'
       UNION ALL SELECT (@cx_a + 189), (@cx_sm + 40), (@cx_u + 55), @cx_c18, '동호회', '확정', 'RCPT-20261026-H5MTJSCD', '2026-10-26 16:00:00', '2026-10-26 17:15:00'
       UNION ALL SELECT (@cx_a + 190), (@cx_sm + 40), (@cx_u + 58), @cx_c18, '동호회', '확정', 'RCPT-20261026-LSV2YLY7', '2026-10-26 16:00:00', '2026-10-26 17:37:00'
       UNION ALL SELECT (@cx_a + 191), (@cx_sm + 40), (@cx_u + 27), @cx_c9, '동호회', '확정', 'RCPT-20261024-Q9BXNCFA', '2026-10-24 12:00:00', '2026-10-24 13:19:00'
       UNION ALL SELECT (@cx_a + 192), (@cx_sm + 40), (@cx_u + 30), @cx_c9, '동호회', '확정', 'RCPT-20261024-5LPFUQXC', '2026-10-24 12:00:00', '2026-10-24 13:01:00'
       UNION ALL SELECT (@cx_a + 193), (@cx_sm + 41), (@cx_u + 45), @cx_c14, '동호회', '확정', 'RCPT-20261030-LGCX9LHX', '2026-10-30 15:00:00', '2026-10-30 16:29:00'
       UNION ALL SELECT (@cx_a + 194), (@cx_sm + 41), (@cx_u + 47), @cx_c14, '동호회', '확정', 'RCPT-20261030-PWC5U3L4', '2026-10-30 15:00:00', '2026-10-30 16:06:00'
       UNION ALL SELECT (@cx_a + 195), (@cx_sm + 41), (@cx_u + 37), @cx_c11, '동호회', '확정', 'RCPT-20261031-YFWY5NBY', '2026-10-31 10:00:00', '2026-10-31 12:02:00'
       UNION ALL SELECT (@cx_a + 196), (@cx_sm + 41), (@cx_u + 39), @cx_c11, '동호회', '확정', 'RCPT-20261031-EMABE5TE', '2026-10-31 10:00:00', '2026-10-31 11:25:00'
       UNION ALL SELECT (@cx_a + 197), (@cx_sm + 42), (@cx_u + 31), @cx_c10, '동호회', '확정', 'RCPT-20261103-CF3W3PYD', '2026-11-03 10:00:00', '2026-11-03 12:37:00'
       UNION ALL SELECT (@cx_a + 198), (@cx_sm + 42), (@cx_u + 32), @cx_c10, '동호회', '확정', 'RCPT-20261103-396UUCP2', '2026-11-03 10:00:00', '2026-11-03 11:22:00'
       UNION ALL SELECT (@cx_a + 199), (@cx_sm + 42), (@cx_u + 33), @cx_c10, '동호회', '확정', 'RCPT-20261103-Y9QV3J97', '2026-11-03 10:00:00', '2026-11-03 11:46:00'
       UNION ALL SELECT (@cx_a + 200), (@cx_sm + 42), (@cx_u + 34), @cx_c10, '동호회', '확정', 'RCPT-20261103-7JQYD2EM', '2026-11-03 10:00:00', '2026-11-03 12:24:00'
       UNION ALL SELECT (@cx_a + 201), (@cx_sm + 42), (@cx_u + 1), @cx_c2, '동호회', '확정', 'RCPT-20261105-SM2FR24W', '2026-11-05 02:00:00', '2026-11-05 03:46:00'
       UNION ALL SELECT (@cx_a + 202), (@cx_sm + 42), (@cx_u + 2), @cx_c2, '동호회', '확정', 'RCPT-20261105-LFGS4C88', '2026-11-05 02:00:00', '2026-11-05 04:00:00'
       UNION ALL SELECT (@cx_a + 203), (@cx_sm + 42), (@cx_u + 5), @cx_c2, '동호회', '확정', 'RCPT-20261105-CP9TKEGC', '2026-11-05 02:00:00', '2026-11-05 03:41:00'
       UNION ALL SELECT (@cx_a + 204), (@cx_sm + 42), (@cx_u + 6), @cx_c2, '동호회', '확정', 'RCPT-20261105-PBD9H4A4', '2026-11-05 02:00:00', '2026-11-05 04:13:00'
       UNION ALL SELECT (@cx_a + 205), (@cx_sm + 43), (@cx_u + 1), @cx_c2, '동호회', '대기', 'RCPT-20261112-U76B7L9L', '2026-11-12 21:30:00', NULL
       UNION ALL SELECT (@cx_a + 206), (@cx_sm + 43), (@cx_u + 2), @cx_c2, '동호회', '대기', 'RCPT-20261112-J9MV9XJC', '2026-11-12 21:30:00', NULL
       UNION ALL SELECT (@cx_a + 207), (@cx_sm + 43), (@cx_u + 3), @cx_c2, '동호회', '대기', 'RCPT-20261112-NC5DPKCW', '2026-11-12 21:30:00', NULL
       UNION ALL SELECT (@cx_a + 208), (@cx_sm + 43), (@cx_u + 5), @cx_c2, '동호회', '대기', 'RCPT-20261112-3MLB36WA', '2026-11-12 21:30:00', NULL
       UNION ALL SELECT (@cx_a + 209), (@cx_sm + 44), (@cx_u + 7), @cx_c4, '동호회', '대기', 'RCPT-20261112-M9NS8AZL', '2026-11-12 21:37:00', NULL
       UNION ALL SELECT (@cx_a + 210), (@cx_sm + 44), (@cx_u + 8), @cx_c4, '동호회', '대기', 'RCPT-20261112-P5YLMHHY', '2026-11-12 21:37:00', NULL
       UNION ALL SELECT (@cx_a + 211), (@cx_sm + 45), (@cx_u + 11), @cx_c5, '동호회', '대기', 'RCPT-20261112-CB4R5X55', '2026-11-12 21:44:00', NULL
       UNION ALL SELECT (@cx_a + 212), (@cx_sm + 45), (@cx_u + 13), @cx_c5, '동호회', '대기', 'RCPT-20261112-3A2YSAGD', '2026-11-12 21:44:00', NULL
       UNION ALL SELECT (@cx_a + 213), (@cx_sm + 46), (@cx_u + 15), @cx_c6, '동호회', '대기', 'RCPT-20261112-W4V6KZD3', '2026-11-12 21:51:00', NULL
       UNION ALL SELECT (@cx_a + 214), (@cx_sm + 46), (@cx_u + 17), @cx_c6, '동호회', '대기', 'RCPT-20261112-T2HDMV9U', '2026-11-12 21:51:00', NULL
       UNION ALL SELECT (@cx_a + 215), (@cx_sm + 47), (@cx_u + 19), @cx_c7, '동호회', '대기', 'RCPT-20261112-7FBXEK4A', '2026-11-12 21:58:00', NULL
       UNION ALL SELECT (@cx_a + 216), (@cx_sm + 47), (@cx_u + 20), @cx_c7, '동호회', '대기', 'RCPT-20261112-CJYPRP32', '2026-11-12 21:58:00', NULL
       UNION ALL SELECT (@cx_a + 217), (@cx_sm + 48), (@cx_u + 23), @cx_c8, '동호회', '대기', 'RCPT-20261112-2JWEL8Y5', '2026-11-12 22:05:00', NULL
       UNION ALL SELECT (@cx_a + 218), (@cx_sm + 48), (@cx_u + 26), @cx_c8, '동호회', '대기', 'RCPT-20261112-CJA2AHUQ', '2026-11-12 22:05:00', NULL
       UNION ALL SELECT (@cx_a + 219), (@cx_sm + 49), (@cx_u + 27), @cx_c9, '동호회', '대기', 'RCPT-20261112-KFQM9BHY', '2026-11-12 22:12:00', NULL
       UNION ALL SELECT (@cx_a + 220), (@cx_sm + 49), (@cx_u + 28), @cx_c9, '동호회', '대기', 'RCPT-20261112-PTDGEXPV', '2026-11-12 22:12:00', NULL
       UNION ALL SELECT (@cx_a + 221), (@cx_sm + 50), (@cx_u + 31), @cx_c10, '동호회', '대기', 'RCPT-20261112-NU2QWSC5', '2026-11-12 22:19:00', NULL
       UNION ALL SELECT (@cx_a + 222), (@cx_sm + 50), (@cx_u + 32), @cx_c10, '동호회', '대기', 'RCPT-20261112-2UD5YFAD', '2026-11-12 22:19:00', NULL
       UNION ALL SELECT (@cx_a + 223), (@cx_sm + 50), (@cx_u + 35), @cx_c10, '동호회', '대기', 'RCPT-20261112-XXK4WASH', '2026-11-12 22:19:00', NULL
       UNION ALL SELECT (@cx_a + 224), (@cx_sm + 50), (@cx_u + 36), @cx_c10, '동호회', '대기', 'RCPT-20261112-7RR3WM5A', '2026-11-12 22:19:00', NULL
       UNION ALL SELECT (@cx_a + 225), (@cx_sm + 51), (@cx_u + 37), @cx_c11, '동호회', '대기', 'RCPT-20261112-PCY88EFS', '2026-11-12 22:26:00', NULL
       UNION ALL SELECT (@cx_a + 226), (@cx_sm + 51), (@cx_u + 39), @cx_c11, '동호회', '대기', 'RCPT-20261112-C6KWNWXE', '2026-11-12 22:26:00', NULL
       UNION ALL SELECT (@cx_a + 227), (@cx_sm + 52), (@cx_u + 41), @cx_c13, '동호회', '대기', 'RCPT-20261112-SF3Y6TKT', '2026-11-12 22:33:00', NULL
       UNION ALL SELECT (@cx_a + 228), (@cx_sm + 52), (@cx_u + 43), @cx_c13, '동호회', '대기', 'RCPT-20261112-XSSYRCKD', '2026-11-12 22:33:00', NULL
       UNION ALL SELECT (@cx_a + 229), (@cx_sm + 53), (@cx_u + 45), @cx_c14, '동호회', '대기', 'RCPT-20261112-5TPB4LK3', '2026-11-12 22:40:00', NULL
       UNION ALL SELECT (@cx_a + 230), (@cx_sm + 53), (@cx_u + 48), @cx_c14, '동호회', '대기', 'RCPT-20261112-MTQ7ZTMH', '2026-11-12 22:40:00', NULL
       UNION ALL SELECT (@cx_a + 231), (@cx_sm + 54), (@cx_u + 49), @cx_c15, '동호회', '대기', 'RCPT-20261112-LZ39CUH7', '2026-11-12 22:47:00', NULL
       UNION ALL SELECT (@cx_a + 232), (@cx_sm + 54), (@cx_u + 50), @cx_c15, '동호회', '대기', 'RCPT-20261112-RDNEWRJM', '2026-11-12 22:47:00', NULL
       UNION ALL SELECT (@cx_a + 233), (@cx_sm + 54), (@cx_u + 51), @cx_c15, '동호회', '대기', 'RCPT-20261112-XX3KNFZX', '2026-11-12 22:47:00', NULL
       UNION ALL SELECT (@cx_a + 234), (@cx_sm + 54), (@cx_u + 53), @cx_c15, '동호회', '대기', 'RCPT-20261112-9KN64VCE', '2026-11-12 22:47:00', NULL
       UNION ALL SELECT (@cx_a + 235), (@cx_sm + 55), (@cx_u + 55), @cx_c18, '동호회', '대기', 'RCPT-20261112-3T33VSZX', '2026-11-12 22:54:00', NULL
       UNION ALL SELECT (@cx_a + 236), (@cx_sm + 55), (@cx_u + 56), @cx_c18, '동호회', '대기', 'RCPT-20261112-M8ERZWGF', '2026-11-12 22:54:00', NULL
       UNION ALL SELECT (@cx_a + 237), (@cx_sm + 56), (@cx_u + 59), @cx_c19, '동호회', '대기', 'RCPT-20261112-7FMZT4FC', '2026-11-12 23:01:00', NULL
       UNION ALL SELECT (@cx_a + 238), (@cx_sm + 56), (@cx_u + 62), @cx_c19, '동호회', '대기', 'RCPT-20261112-QHD4USRA', '2026-11-12 23:01:00', NULL
) v
 WHERE @cx_ready = 1;
UPDATE match_application
   SET group_application_id = CASE
         WHEN application_id BETWEEN (@cx_a + 1) AND (@cx_a + 4) THEN (@cx_a + 1)
         WHEN application_id BETWEEN (@cx_a + 5) AND (@cx_a + 8) THEN (@cx_a + 5)
         WHEN application_id BETWEEN (@cx_a + 9) AND (@cx_a + 10) THEN (@cx_a + 9)
         WHEN application_id BETWEEN (@cx_a + 11) AND (@cx_a + 12) THEN (@cx_a + 11)
         WHEN application_id BETWEEN (@cx_a + 13) AND (@cx_a + 14) THEN (@cx_a + 13)
         WHEN application_id BETWEEN (@cx_a + 15) AND (@cx_a + 16) THEN (@cx_a + 15)
         WHEN application_id BETWEEN (@cx_a + 17) AND (@cx_a + 18) THEN (@cx_a + 17)
         WHEN application_id BETWEEN (@cx_a + 19) AND (@cx_a + 20) THEN (@cx_a + 19)
         WHEN application_id BETWEEN (@cx_a + 21) AND (@cx_a + 24) THEN (@cx_a + 21)
         WHEN application_id BETWEEN (@cx_a + 25) AND (@cx_a + 28) THEN (@cx_a + 25)
         WHEN application_id BETWEEN (@cx_a + 29) AND (@cx_a + 30) THEN (@cx_a + 29)
         WHEN application_id BETWEEN (@cx_a + 31) AND (@cx_a + 32) THEN (@cx_a + 31)
         WHEN application_id BETWEEN (@cx_a + 33) AND (@cx_a + 34) THEN (@cx_a + 33)
         WHEN application_id BETWEEN (@cx_a + 35) AND (@cx_a + 36) THEN (@cx_a + 35)
         WHEN application_id BETWEEN (@cx_a + 37) AND (@cx_a + 38) THEN (@cx_a + 37)
         WHEN application_id BETWEEN (@cx_a + 39) AND (@cx_a + 40) THEN (@cx_a + 39)
         WHEN application_id BETWEEN (@cx_a + 41) AND (@cx_a + 42) THEN (@cx_a + 41)
         WHEN application_id BETWEEN (@cx_a + 43) AND (@cx_a + 44) THEN (@cx_a + 43)
         WHEN application_id BETWEEN (@cx_a + 45) AND (@cx_a + 46) THEN (@cx_a + 45)
         WHEN application_id BETWEEN (@cx_a + 47) AND (@cx_a + 48) THEN (@cx_a + 47)
         WHEN application_id BETWEEN (@cx_a + 49) AND (@cx_a + 50) THEN (@cx_a + 49)
         WHEN application_id BETWEEN (@cx_a + 51) AND (@cx_a + 52) THEN (@cx_a + 51)
         WHEN application_id BETWEEN (@cx_a + 53) AND (@cx_a + 54) THEN (@cx_a + 53)
         WHEN application_id BETWEEN (@cx_a + 55) AND (@cx_a + 56) THEN (@cx_a + 55)
         WHEN application_id BETWEEN (@cx_a + 57) AND (@cx_a + 58) THEN (@cx_a + 57)
         WHEN application_id BETWEEN (@cx_a + 59) AND (@cx_a + 60) THEN (@cx_a + 59)
         WHEN application_id BETWEEN (@cx_a + 61) AND (@cx_a + 62) THEN (@cx_a + 61)
         WHEN application_id BETWEEN (@cx_a + 63) AND (@cx_a + 64) THEN (@cx_a + 63)
         WHEN application_id BETWEEN (@cx_a + 65) AND (@cx_a + 66) THEN (@cx_a + 65)
         WHEN application_id BETWEEN (@cx_a + 67) AND (@cx_a + 68) THEN (@cx_a + 67)
         WHEN application_id BETWEEN (@cx_a + 69) AND (@cx_a + 70) THEN (@cx_a + 69)
         WHEN application_id BETWEEN (@cx_a + 71) AND (@cx_a + 72) THEN (@cx_a + 71)
         WHEN application_id BETWEEN (@cx_a + 73) AND (@cx_a + 74) THEN (@cx_a + 73)
         WHEN application_id BETWEEN (@cx_a + 75) AND (@cx_a + 76) THEN (@cx_a + 75)
         WHEN application_id BETWEEN (@cx_a + 77) AND (@cx_a + 78) THEN (@cx_a + 77)
         WHEN application_id BETWEEN (@cx_a + 79) AND (@cx_a + 80) THEN (@cx_a + 79)
         WHEN application_id BETWEEN (@cx_a + 81) AND (@cx_a + 82) THEN (@cx_a + 81)
         WHEN application_id BETWEEN (@cx_a + 83) AND (@cx_a + 84) THEN (@cx_a + 83)
         WHEN application_id BETWEEN (@cx_a + 85) AND (@cx_a + 88) THEN (@cx_a + 85)
         WHEN application_id BETWEEN (@cx_a + 89) AND (@cx_a + 92) THEN (@cx_a + 89)
         WHEN application_id BETWEEN (@cx_a + 93) AND (@cx_a + 94) THEN (@cx_a + 93)
         WHEN application_id BETWEEN (@cx_a + 95) AND (@cx_a + 96) THEN (@cx_a + 95)
         WHEN application_id BETWEEN (@cx_a + 97) AND (@cx_a + 98) THEN (@cx_a + 97)
         WHEN application_id BETWEEN (@cx_a + 99) AND (@cx_a + 100) THEN (@cx_a + 99)
         WHEN application_id BETWEEN (@cx_a + 101) AND (@cx_a + 102) THEN (@cx_a + 101)
         WHEN application_id BETWEEN (@cx_a + 103) AND (@cx_a + 104) THEN (@cx_a + 103)
         WHEN application_id BETWEEN (@cx_a + 105) AND (@cx_a + 106) THEN (@cx_a + 105)
         WHEN application_id BETWEEN (@cx_a + 107) AND (@cx_a + 108) THEN (@cx_a + 107)
         WHEN application_id BETWEEN (@cx_a + 109) AND (@cx_a + 112) THEN (@cx_a + 109)
         WHEN application_id BETWEEN (@cx_a + 113) AND (@cx_a + 116) THEN (@cx_a + 113)
         WHEN application_id BETWEEN (@cx_a + 117) AND (@cx_a + 120) THEN (@cx_a + 117)
         WHEN application_id BETWEEN (@cx_a + 121) AND (@cx_a + 124) THEN (@cx_a + 121)
         WHEN application_id BETWEEN (@cx_a + 125) AND (@cx_a + 126) THEN (@cx_a + 125)
         WHEN application_id BETWEEN (@cx_a + 127) AND (@cx_a + 128) THEN (@cx_a + 127)
         WHEN application_id BETWEEN (@cx_a + 129) AND (@cx_a + 130) THEN (@cx_a + 129)
         WHEN application_id BETWEEN (@cx_a + 131) AND (@cx_a + 132) THEN (@cx_a + 131)
         WHEN application_id BETWEEN (@cx_a + 133) AND (@cx_a + 134) THEN (@cx_a + 133)
         WHEN application_id BETWEEN (@cx_a + 135) AND (@cx_a + 136) THEN (@cx_a + 135)
         WHEN application_id BETWEEN (@cx_a + 137) AND (@cx_a + 138) THEN (@cx_a + 137)
         WHEN application_id BETWEEN (@cx_a + 139) AND (@cx_a + 140) THEN (@cx_a + 139)
         WHEN application_id BETWEEN (@cx_a + 141) AND (@cx_a + 142) THEN (@cx_a + 141)
         WHEN application_id BETWEEN (@cx_a + 143) AND (@cx_a + 144) THEN (@cx_a + 143)
         WHEN application_id BETWEEN (@cx_a + 145) AND (@cx_a + 146) THEN (@cx_a + 145)
         WHEN application_id BETWEEN (@cx_a + 147) AND (@cx_a + 148) THEN (@cx_a + 147)
         WHEN application_id BETWEEN (@cx_a + 149) AND (@cx_a + 152) THEN (@cx_a + 149)
         WHEN application_id BETWEEN (@cx_a + 153) AND (@cx_a + 156) THEN (@cx_a + 153)
         WHEN application_id BETWEEN (@cx_a + 157) AND (@cx_a + 158) THEN (@cx_a + 157)
         WHEN application_id BETWEEN (@cx_a + 159) AND (@cx_a + 160) THEN (@cx_a + 159)
         WHEN application_id BETWEEN (@cx_a + 161) AND (@cx_a + 162) THEN (@cx_a + 161)
         WHEN application_id BETWEEN (@cx_a + 163) AND (@cx_a + 164) THEN (@cx_a + 163)
         WHEN application_id BETWEEN (@cx_a + 165) AND (@cx_a + 168) THEN (@cx_a + 165)
         WHEN application_id BETWEEN (@cx_a + 169) AND (@cx_a + 172) THEN (@cx_a + 169)
         WHEN application_id BETWEEN (@cx_a + 173) AND (@cx_a + 174) THEN (@cx_a + 173)
         WHEN application_id BETWEEN (@cx_a + 175) AND (@cx_a + 176) THEN (@cx_a + 175)
         WHEN application_id BETWEEN (@cx_a + 177) AND (@cx_a + 178) THEN (@cx_a + 177)
         WHEN application_id BETWEEN (@cx_a + 179) AND (@cx_a + 180) THEN (@cx_a + 179)
         WHEN application_id BETWEEN (@cx_a + 181) AND (@cx_a + 184) THEN (@cx_a + 181)
         WHEN application_id BETWEEN (@cx_a + 185) AND (@cx_a + 188) THEN (@cx_a + 185)
         WHEN application_id BETWEEN (@cx_a + 189) AND (@cx_a + 190) THEN (@cx_a + 189)
         WHEN application_id BETWEEN (@cx_a + 191) AND (@cx_a + 192) THEN (@cx_a + 191)
         WHEN application_id BETWEEN (@cx_a + 193) AND (@cx_a + 194) THEN (@cx_a + 193)
         WHEN application_id BETWEEN (@cx_a + 195) AND (@cx_a + 196) THEN (@cx_a + 195)
         WHEN application_id BETWEEN (@cx_a + 197) AND (@cx_a + 200) THEN (@cx_a + 197)
         WHEN application_id BETWEEN (@cx_a + 201) AND (@cx_a + 204) THEN (@cx_a + 201)
         WHEN application_id BETWEEN (@cx_a + 205) AND (@cx_a + 208) THEN (@cx_a + 205)
         WHEN application_id BETWEEN (@cx_a + 209) AND (@cx_a + 210) THEN (@cx_a + 209)
         WHEN application_id BETWEEN (@cx_a + 211) AND (@cx_a + 212) THEN (@cx_a + 211)
         WHEN application_id BETWEEN (@cx_a + 213) AND (@cx_a + 214) THEN (@cx_a + 213)
         WHEN application_id BETWEEN (@cx_a + 215) AND (@cx_a + 216) THEN (@cx_a + 215)
         WHEN application_id BETWEEN (@cx_a + 217) AND (@cx_a + 218) THEN (@cx_a + 217)
         WHEN application_id BETWEEN (@cx_a + 219) AND (@cx_a + 220) THEN (@cx_a + 219)
         WHEN application_id BETWEEN (@cx_a + 221) AND (@cx_a + 224) THEN (@cx_a + 221)
         WHEN application_id BETWEEN (@cx_a + 225) AND (@cx_a + 226) THEN (@cx_a + 225)
         WHEN application_id BETWEEN (@cx_a + 227) AND (@cx_a + 228) THEN (@cx_a + 227)
         WHEN application_id BETWEEN (@cx_a + 229) AND (@cx_a + 230) THEN (@cx_a + 229)
         WHEN application_id BETWEEN (@cx_a + 231) AND (@cx_a + 234) THEN (@cx_a + 231)
         WHEN application_id BETWEEN (@cx_a + 235) AND (@cx_a + 236) THEN (@cx_a + 235)
         WHEN application_id BETWEEN (@cx_a + 237) AND (@cx_a + 238) THEN (@cx_a + 237)
       END
 WHERE @cx_ready = 1 AND application_id BETWEEN (@cx_a + 1) AND (@cx_a + 238);

-- 5. 참가자 (확정 순서대로 번호, entry_mmr은 확정 시점의 레이팅 = 직전 경기 반영 뒤 값)
INSERT INTO match_participant (participant_id, application_id, match_id, user_id, participation_status, match_side, entry_mmr, joined_at)
SELECT * FROM (
                 SELECT (@cx_p + 1) AS participant_id, (@cx_a + 7) AS application_id, (@cx_sm + 1) AS match_id, (@cx_u + 6) AS user_id, '확정' AS participation_status, 'B' AS match_side, 1000.00 AS entry_mmr, '2026-07-28 05:28:00' AS joined_at
       UNION ALL SELECT (@cx_p + 2), (@cx_a + 6), (@cx_sm + 1), (@cx_u + 2), '확정', 'B', 1000.00, '2026-07-28 06:06:00'
       UNION ALL SELECT (@cx_p + 3), (@cx_a + 5), (@cx_sm + 1), (@cx_u + 1), '확정', 'B', 1000.00, '2026-07-28 06:24:00'
       UNION ALL SELECT (@cx_p + 4), (@cx_a + 8), (@cx_sm + 1), (@cx_u + 5), '확정', 'B', 1000.00, '2026-07-28 06:46:00'
       UNION ALL SELECT (@cx_p + 5), (@cx_a + 2), (@cx_sm + 1), (@cx_u + 32), '확정', 'A', 1000.00, '2026-07-29 07:06:00'
       UNION ALL SELECT (@cx_p + 6), (@cx_a + 3), (@cx_sm + 1), (@cx_u + 33), '확정', 'A', 1000.00, '2026-07-29 07:36:00'
       UNION ALL SELECT (@cx_p + 7), (@cx_a + 1), (@cx_sm + 1), (@cx_u + 31), '확정', 'A', 1000.00, '2026-07-29 07:39:00'
       UNION ALL SELECT (@cx_p + 8), (@cx_a + 4), (@cx_sm + 1), (@cx_u + 34), '확정', 'A', 1000.00, '2026-07-29 08:30:00'
       UNION ALL SELECT (@cx_p + 9), (@cx_a + 11), (@cx_sm + 2), (@cx_u + 41), '확정', 'B', 1000.00, '2026-08-03 03:59:00'
       UNION ALL SELECT (@cx_p + 10), (@cx_a + 12), (@cx_sm + 2), (@cx_u + 43), '확정', 'B', 1000.00, '2026-08-03 04:30:00'
       UNION ALL SELECT (@cx_p + 11), (@cx_a + 10), (@cx_sm + 2), (@cx_u + 8), '확정', 'A', 1000.00, '2026-08-05 01:10:00'
       UNION ALL SELECT (@cx_p + 12), (@cx_a + 9), (@cx_sm + 2), (@cx_u + 7), '확정', 'A', 1000.00, '2026-08-05 02:00:00'
       UNION ALL SELECT (@cx_p + 13), (@cx_a + 13), (@cx_sm + 3), (@cx_u + 19), '확정', 'A', 1000.00, '2026-08-05 11:13:00'
       UNION ALL SELECT (@cx_p + 14), (@cx_a + 14), (@cx_sm + 3), (@cx_u + 20), '확정', 'A', 1000.00, '2026-08-05 12:23:00'
       UNION ALL SELECT (@cx_p + 15), (@cx_a + 16), (@cx_sm + 3), (@cx_u + 60), '확정', 'B', 1000.00, '2026-08-05 22:02:00'
       UNION ALL SELECT (@cx_p + 16), (@cx_a + 15), (@cx_sm + 3), (@cx_u + 59), '확정', 'B', 1000.00, '2026-08-05 22:44:00'
       UNION ALL SELECT (@cx_p + 17), (@cx_a + 18), (@cx_sm + 4), (@cx_u + 18), '확정', 'A', 1000.00, '2026-08-04 12:08:00'
       UNION ALL SELECT (@cx_p + 18), (@cx_a + 17), (@cx_sm + 4), (@cx_u + 15), '확정', 'A', 1000.00, '2026-08-04 13:27:00'
       UNION ALL SELECT (@cx_p + 19), (@cx_a + 20), (@cx_sm + 4), (@cx_u + 48), '확정', 'B', 1000.00, '2026-08-04 15:32:00'
       UNION ALL SELECT (@cx_p + 20), (@cx_a + 19), (@cx_sm + 4), (@cx_u + 45), '확정', 'B', 1000.00, '2026-08-04 16:07:00'
       UNION ALL SELECT (@cx_p + 21), (@cx_a + 22), (@cx_sm + 5), (@cx_u + 2), '확정', 'A', 968.00, '2026-08-06 09:50:00'
       UNION ALL SELECT (@cx_p + 22), (@cx_a + 23), (@cx_sm + 5), (@cx_u + 4), '확정', 'A', 1000.00, '2026-08-06 10:08:00'
       UNION ALL SELECT (@cx_p + 23), (@cx_a + 24), (@cx_sm + 5), (@cx_u + 3), '확정', 'A', 1000.00, '2026-08-06 10:19:00'
       UNION ALL SELECT (@cx_p + 24), (@cx_a + 21), (@cx_sm + 5), (@cx_u + 1), '확정', 'A', 968.00, '2026-08-06 10:48:00'
       UNION ALL SELECT (@cx_p + 25), (@cx_a + 28), (@cx_sm + 5), (@cx_u + 51), '확정', 'B', 1000.00, '2026-08-07 00:07:00'
       UNION ALL SELECT (@cx_p + 26), (@cx_a + 27), (@cx_sm + 5), (@cx_u + 53), '확정', 'B', 1000.00, '2026-08-07 00:44:00'
       UNION ALL SELECT (@cx_p + 27), (@cx_a + 26), (@cx_sm + 5), (@cx_u + 50), '확정', 'B', 1000.00, '2026-08-07 00:54:00'
       UNION ALL SELECT (@cx_p + 28), (@cx_a + 25), (@cx_sm + 5), (@cx_u + 49), '확정', 'B', 1000.00, '2026-08-07 00:56:00'
       UNION ALL SELECT (@cx_p + 29), (@cx_a + 29), (@cx_sm + 6), (@cx_u + 11), '확정', 'A', 1000.00, '2026-08-08 12:18:00'
       UNION ALL SELECT (@cx_p + 30), (@cx_a + 30), (@cx_sm + 6), (@cx_u + 13), '확정', 'A', 1000.00, '2026-08-08 12:33:00'
       UNION ALL SELECT (@cx_p + 31), (@cx_a + 32), (@cx_sm + 6), (@cx_u + 58), '확정', 'B', 1000.00, '2026-08-08 13:11:00'
       UNION ALL SELECT (@cx_p + 32), (@cx_a + 31), (@cx_sm + 6), (@cx_u + 55), '확정', 'B', 1000.00, '2026-08-08 14:23:00'
       UNION ALL SELECT (@cx_p + 33), (@cx_a + 35), (@cx_sm + 7), (@cx_u + 15), '확정', 'B', 968.00, '2026-08-10 13:39:00'
       UNION ALL SELECT (@cx_p + 34), (@cx_a + 36), (@cx_sm + 7), (@cx_u + 16), '확정', 'B', 1000.00, '2026-08-10 14:08:00'
       UNION ALL SELECT (@cx_p + 35), (@cx_a + 33), (@cx_sm + 7), (@cx_u + 19), '확정', 'A', 1000.00, '2026-08-12 20:01:00'
       UNION ALL SELECT (@cx_p + 36), (@cx_a + 34), (@cx_sm + 7), (@cx_u + 22), '확정', 'A', 1000.00, '2026-08-12 21:31:00'
       UNION ALL SELECT (@cx_p + 37), (@cx_a + 40), (@cx_sm + 8), (@cx_u + 10), '확정', 'B', 1000.00, '2026-08-11 11:03:00'
       UNION ALL SELECT (@cx_p + 38), (@cx_a + 39), (@cx_sm + 8), (@cx_u + 7), '확정', 'B', 1032.00, '2026-08-11 11:04:00'
       UNION ALL SELECT (@cx_p + 39), (@cx_a + 37), (@cx_sm + 8), (@cx_u + 27), '확정', 'A', 1000.00, '2026-08-12 15:07:00'
       UNION ALL SELECT (@cx_p + 40), (@cx_a + 38), (@cx_sm + 8), (@cx_u + 30), '확정', 'A', 1000.00, '2026-08-12 16:11:00'
       UNION ALL SELECT (@cx_p + 41), (@cx_a + 43), (@cx_sm + 9), (@cx_u + 37), '확정', 'B', 1000.00, '2026-08-12 05:09:00'
       UNION ALL SELECT (@cx_p + 42), (@cx_a + 44), (@cx_sm + 9), (@cx_u + 38), '확정', 'B', 1000.00, '2026-08-12 05:09:00'
       UNION ALL SELECT (@cx_p + 43), (@cx_a + 41), (@cx_sm + 9), (@cx_u + 59), '확정', 'A', 1000.00, '2026-08-13 09:20:00'
       UNION ALL SELECT (@cx_p + 44), (@cx_a + 42), (@cx_sm + 9), (@cx_u + 61), '확정', 'A', 1000.00, '2026-08-13 10:12:00'
       UNION ALL SELECT (@cx_p + 45), (@cx_a + 45), (@cx_sm + 10), (@cx_u + 41), '확정', 'A', 968.00, '2026-08-13 00:09:00'
       UNION ALL SELECT (@cx_p + 46), (@cx_a + 46), (@cx_sm + 10), (@cx_u + 42), '확정', 'A', 1000.00, '2026-08-13 00:46:00'
       UNION ALL SELECT (@cx_p + 47), (@cx_a + 47), (@cx_sm + 10), (@cx_u + 23), '확정', 'B', 1000.00, '2026-08-14 00:19:00'
       UNION ALL SELECT (@cx_p + 48), (@cx_a + 48), (@cx_sm + 10), (@cx_u + 26), '확정', 'B', 1000.00, '2026-08-14 00:40:00'
       UNION ALL SELECT (@cx_p + 49), (@cx_a + 49), (@cx_sm + 11), (@cx_u + 45), '확정', 'A', 1032.00, '2026-08-14 13:32:00'
       UNION ALL SELECT (@cx_p + 50), (@cx_a + 50), (@cx_sm + 11), (@cx_u + 46), '확정', 'A', 1000.00, '2026-08-14 14:58:00'
       UNION ALL SELECT (@cx_p + 51), (@cx_a + 51), (@cx_sm + 11), (@cx_u + 37), '확정', 'B', 968.00, '2026-08-16 18:05:00'
       UNION ALL SELECT (@cx_p + 52), (@cx_a + 52), (@cx_sm + 11), (@cx_u + 40), '확정', 'B', 1000.00, '2026-08-16 18:13:00'
       UNION ALL SELECT (@cx_p + 53), (@cx_a + 55), (@cx_sm + 12), (@cx_u + 11), '확정', 'B', 968.00, '2026-08-15 15:15:00'
       UNION ALL SELECT (@cx_p + 54), (@cx_a + 56), (@cx_sm + 12), (@cx_u + 12), '확정', 'B', 1000.00, '2026-08-15 15:28:00'
       UNION ALL SELECT (@cx_p + 55), (@cx_a + 54), (@cx_sm + 12), (@cx_u + 29), '확정', 'A', 1000.00, '2026-08-17 18:03:00'
       UNION ALL SELECT (@cx_p + 56), (@cx_a + 53), (@cx_sm + 12), (@cx_u + 27), '확정', 'A', 1033.47, '2026-08-17 18:40:00'
       UNION ALL SELECT (@cx_p + 57), (@cx_a + 60), (@cx_sm + 13), (@cx_u + 57), '확정', 'B', 1000.00, '2026-08-20 17:44:00'
       UNION ALL SELECT (@cx_p + 58), (@cx_a + 59), (@cx_sm + 13), (@cx_u + 55), '확정', 'B', 1032.00, '2026-08-20 18:20:00'
       UNION ALL SELECT (@cx_p + 59), (@cx_a + 58), (@cx_sm + 13), (@cx_u + 25), '확정', 'A', 1000.00, '2026-08-20 18:25:00'
       UNION ALL SELECT (@cx_p + 60), (@cx_a + 57), (@cx_sm + 13), (@cx_u + 23), '확정', 'A', 966.53, '2026-08-20 19:17:00'
       UNION ALL SELECT (@cx_p + 61), (@cx_a + 63), (@cx_sm + 14), (@cx_u + 19), '확정', 'B', 966.53, '2026-08-23 17:42:00'
       UNION ALL SELECT (@cx_p + 62), (@cx_a + 64), (@cx_sm + 14), (@cx_u + 21), '확정', 'B', 1000.00, '2026-08-23 17:42:00'
       UNION ALL SELECT (@cx_p + 63), (@cx_a + 62), (@cx_sm + 14), (@cx_u + 17), '확정', 'A', 1000.00, '2026-08-23 18:45:00'
       UNION ALL SELECT (@cx_p + 64), (@cx_a + 61), (@cx_sm + 14), (@cx_u + 15), '확정', 'A', 1001.47, '2026-08-23 19:58:00'
       UNION ALL SELECT (@cx_p + 65), (@cx_a + 67), (@cx_sm + 15), (@cx_u + 7), '확정', 'B', 998.53, '2026-08-24 15:44:00'
       UNION ALL SELECT (@cx_p + 66), (@cx_a + 68), (@cx_sm + 15), (@cx_u + 9), '확정', 'B', 1000.00, '2026-08-24 17:03:00'
       UNION ALL SELECT (@cx_p + 67), (@cx_a + 65), (@cx_sm + 15), (@cx_u + 23), '확정', 'A', 1001.54, '2026-08-26 04:38:00'
       UNION ALL SELECT (@cx_p + 68), (@cx_a + 66), (@cx_sm + 15), (@cx_u + 24), '확정', 'A', 1000.00, '2026-08-26 05:39:00'
       UNION ALL SELECT (@cx_p + 69), (@cx_a + 70), (@cx_sm + 16), (@cx_u + 44), '확정', 'A', 1000.00, '2026-08-26 00:16:00'
       UNION ALL SELECT (@cx_p + 70), (@cx_a + 69), (@cx_sm + 16), (@cx_u + 41), '확정', 'A', 1001.47, '2026-08-26 01:56:00'
       UNION ALL SELECT (@cx_p + 71), (@cx_a + 72), (@cx_sm + 16), (@cx_u + 28), '확정', 'B', 1000.00, '2026-08-27 03:51:00'
       UNION ALL SELECT (@cx_p + 72), (@cx_a + 71), (@cx_sm + 16), (@cx_u + 27), '확정', 'B', 998.46, '2026-08-27 04:34:00'
       UNION ALL SELECT (@cx_p + 73), (@cx_a + 75), (@cx_sm + 17), (@cx_u + 45), '확정', 'B', 1061.06, '2026-08-27 05:01:00'
       UNION ALL SELECT (@cx_p + 74), (@cx_a + 76), (@cx_sm + 17), (@cx_u + 47), '확정', 'B', 1000.00, '2026-08-27 05:44:00'
       UNION ALL SELECT (@cx_p + 75), (@cx_a + 73), (@cx_sm + 17), (@cx_u + 37), '확정', 'A', 938.94, '2026-08-28 03:03:00'
       UNION ALL SELECT (@cx_p + 76), (@cx_a + 74), (@cx_sm + 17), (@cx_u + 39), '확정', 'A', 1000.00, '2026-08-28 03:49:00'
       UNION ALL SELECT (@cx_p + 77), (@cx_a + 78), (@cx_sm + 18), (@cx_u + 56), '확정', 'A', 1000.00, '2026-08-29 20:03:00'
       UNION ALL SELECT (@cx_p + 78), (@cx_a + 77), (@cx_sm + 18), (@cx_u + 55), '확정', 'A', 996.99, '2026-08-29 20:09:00'
       UNION ALL SELECT (@cx_p + 79), (@cx_a + 79), (@cx_sm + 18), (@cx_u + 11), '확정', 'B', 1003.01, '2026-08-30 18:14:00'
       UNION ALL SELECT (@cx_p + 80), (@cx_a + 80), (@cx_sm + 18), (@cx_u + 14), '확정', 'B', 1000.00, '2026-08-30 18:59:00'
       UNION ALL SELECT (@cx_p + 81), (@cx_a + 81), (@cx_sm + 19), (@cx_u + 7), '확정', 'A', 966.67, '2026-09-07 00:11:00'
       UNION ALL SELECT (@cx_p + 82), (@cx_a + 82), (@cx_sm + 19), (@cx_u + 8), '확정', 'A', 1032.00, '2026-09-07 00:45:00'
       UNION ALL SELECT (@cx_p + 83), (@cx_a + 83), (@cx_sm + 19), (@cx_u + 55), '확정', 'B', 1029.27, '2026-09-09 02:09:00'
       UNION ALL SELECT (@cx_p + 84), (@cx_a + 84), (@cx_sm + 19), (@cx_u + 57), '확정', 'B', 964.99, '2026-09-09 02:43:00'
       UNION ALL SELECT (@cx_p + 85), (@cx_a + 89), (@cx_sm + 20), (@cx_u + 31), '확정', 'B', 1032.00, '2026-09-07 10:13:00'
       UNION ALL SELECT (@cx_p + 86), (@cx_a + 92), (@cx_sm + 20), (@cx_u + 36), '확정', 'B', 1000.00, '2026-09-07 11:01:00'
       UNION ALL SELECT (@cx_p + 87), (@cx_a + 91), (@cx_sm + 20), (@cx_u + 35), '확정', 'B', 1000.00, '2026-09-07 11:05:00'
       UNION ALL SELECT (@cx_p + 88), (@cx_a + 90), (@cx_sm + 20), (@cx_u + 32), '확정', 'B', 1032.00, '2026-09-07 11:22:00'
       UNION ALL SELECT (@cx_p + 89), (@cx_a + 87), (@cx_sm + 20), (@cx_u + 52), '확정', 'A', 1000.00, '2026-09-09 11:14:00'
       UNION ALL SELECT (@cx_p + 90), (@cx_a + 85), (@cx_sm + 20), (@cx_u + 49), '확정', 'A', 1030.53, '2026-09-09 11:40:00'
       UNION ALL SELECT (@cx_p + 91), (@cx_a + 86), (@cx_sm + 20), (@cx_u + 50), '확정', 'A', 1030.53, '2026-09-09 12:46:00'
       UNION ALL SELECT (@cx_p + 92), (@cx_a + 88), (@cx_sm + 20), (@cx_u + 54), '확정', 'A', 1000.00, '2026-09-09 12:48:00'
       UNION ALL SELECT (@cx_p + 93), (@cx_a + 94), (@cx_sm + 21), (@cx_u + 44), '확정', 'A', 967.86, '2026-09-08 02:24:00'
       UNION ALL SELECT (@cx_p + 94), (@cx_a + 93), (@cx_sm + 21), (@cx_u + 41), '확정', 'A', 969.33, '2026-09-08 03:15:00'
       UNION ALL SELECT (@cx_p + 95), (@cx_a + 96), (@cx_sm + 21), (@cx_u + 12), '확정', 'B', 1035.01, '2026-09-08 05:04:00'
       UNION ALL SELECT (@cx_p + 96), (@cx_a + 95), (@cx_sm + 21), (@cx_u + 11), '확정', 'B', 970.73, '2026-09-08 05:56:00'
       UNION ALL SELECT (@cx_p + 97), (@cx_a + 99), (@cx_sm + 22), (@cx_u + 45), '확정', 'B', 1087.49, '2026-09-09 00:20:00'
       UNION ALL SELECT (@cx_p + 98), (@cx_a + 100), (@cx_sm + 22), (@cx_u + 46), '확정', 'B', 1029.06, '2026-09-09 01:07:00'
       UNION ALL SELECT (@cx_p + 99), (@cx_a + 98), (@cx_sm + 22), (@cx_u + 62), '확정', 'A', 1000.00, '2026-09-11 04:20:00'
       UNION ALL SELECT (@cx_p + 100), (@cx_a + 97), (@cx_sm + 22), (@cx_u + 59), '확정', 'A', 1032.00, '2026-09-11 05:01:00'
       UNION ALL SELECT (@cx_p + 101), (@cx_a + 102), (@cx_sm + 23), (@cx_u + 25), '확정', 'A', 1035.01, '2026-09-11 11:38:00'
       UNION ALL SELECT (@cx_p + 102), (@cx_a + 101), (@cx_sm + 23), (@cx_u + 23), '확정', 'A', 1033.40, '2026-09-11 12:47:00'
       UNION ALL SELECT (@cx_p + 103), (@cx_a + 104), (@cx_sm + 23), (@cx_u + 29), '확정', 'B', 964.99, '2026-09-12 15:33:00'
       UNION ALL SELECT (@cx_p + 104), (@cx_a + 103), (@cx_sm + 23), (@cx_u + 27), '확정', 'B', 1030.60, '2026-09-12 17:23:00'
       UNION ALL SELECT (@cx_p + 105), (@cx_a + 107), (@cx_sm + 24), (@cx_u + 59), '확정', 'B', 1067.87, '2026-09-14 15:45:00'
       UNION ALL SELECT (@cx_p + 106), (@cx_a + 108), (@cx_sm + 24), (@cx_u + 61), '확정', 'B', 1032.00, '2026-09-14 15:54:00'
       UNION ALL SELECT (@cx_p + 107), (@cx_a + 106), (@cx_sm + 24), (@cx_u + 18), '확정', 'A', 968.00, '2026-09-14 18:18:00'
       UNION ALL SELECT (@cx_p + 108), (@cx_a + 105), (@cx_sm + 24), (@cx_u + 15), '확정', 'A', 967.86, '2026-09-14 19:41:00'
       UNION ALL SELECT (@cx_p + 109), (@cx_a + 111), (@cx_sm + 25), (@cx_u + 34), '확정', 'A', 1032.00, '2026-09-15 05:55:00'
       UNION ALL SELECT (@cx_p + 110), (@cx_a + 109), (@cx_sm + 25), (@cx_u + 31), '확정', 'A', 1063.93, '2026-09-15 06:36:00'
       UNION ALL SELECT (@cx_p + 111), (@cx_a + 110), (@cx_sm + 25), (@cx_u + 32), '확정', 'A', 1063.93, '2026-09-15 06:39:00'
       UNION ALL SELECT (@cx_p + 112), (@cx_a + 112), (@cx_sm + 25), (@cx_u + 36), '확정', 'A', 1031.93, '2026-09-15 06:44:00'
       UNION ALL SELECT (@cx_p + 113), (@cx_a + 115), (@cx_sm + 25), (@cx_u + 5), '확정', 'B', 968.00, '2026-09-16 11:19:00'
       UNION ALL SELECT (@cx_p + 114), (@cx_a + 116), (@cx_sm + 25), (@cx_u + 6), '확정', 'B', 968.00, '2026-09-16 11:47:00'
       UNION ALL SELECT (@cx_p + 115), (@cx_a + 113), (@cx_sm + 25), (@cx_u + 1), '확정', 'B', 937.47, '2026-09-16 12:04:00'
       UNION ALL SELECT (@cx_p + 116), (@cx_a + 114), (@cx_sm + 25), (@cx_u + 2), '확정', 'B', 937.47, '2026-09-16 12:24:00'
       UNION ALL SELECT (@cx_p + 117), (@cx_a + 118), (@cx_sm + 26), (@cx_u + 50), '확정', 'A', 998.60, '2026-09-22 10:33:00'
       UNION ALL SELECT (@cx_p + 118), (@cx_a + 119), (@cx_sm + 26), (@cx_u + 53), '확정', 'A', 1030.53, '2026-09-22 10:39:00'
       UNION ALL SELECT (@cx_p + 119), (@cx_a + 117), (@cx_sm + 26), (@cx_u + 49), '확정', 'A', 998.60, '2026-09-22 10:49:00'
       UNION ALL SELECT (@cx_p + 120), (@cx_a + 120), (@cx_sm + 26), (@cx_u + 54), '확정', 'A', 968.07, '2026-09-22 11:54:00'
       UNION ALL SELECT (@cx_p + 121), (@cx_a + 122), (@cx_sm + 26), (@cx_u + 2), '확정', 'B', 946.03, '2026-09-22 13:18:00'
       UNION ALL SELECT (@cx_p + 122), (@cx_a + 121), (@cx_sm + 26), (@cx_u + 1), '확정', 'B', 946.03, '2026-09-22 13:21:00'
       UNION ALL SELECT (@cx_p + 123), (@cx_a + 124), (@cx_sm + 26), (@cx_u + 3), '확정', 'B', 969.47, '2026-09-22 13:29:00'
       UNION ALL SELECT (@cx_p + 124), (@cx_a + 123), (@cx_sm + 26), (@cx_u + 4), '확정', 'B', 969.47, '2026-09-22 13:35:00'
       UNION ALL SELECT (@cx_p + 125), (@cx_a + 127), (@cx_sm + 27), (@cx_u + 37), '확정', 'B', 912.51, '2026-09-21 15:49:00'
       UNION ALL SELECT (@cx_p + 126), (@cx_a + 128), (@cx_sm + 27), (@cx_u + 40), '확정', 'B', 970.94, '2026-09-21 16:10:00'
       UNION ALL SELECT (@cx_p + 127), (@cx_a + 125), (@cx_sm + 27), (@cx_u + 19), '확정', 'A', 1000.14, '2026-09-21 19:19:00'
       UNION ALL SELECT (@cx_p + 128), (@cx_a + 126), (@cx_sm + 27), (@cx_u + 21), '확정', 'A', 1033.61, '2026-09-21 20:02:00'
       UNION ALL SELECT (@cx_p + 129), (@cx_a + 130), (@cx_sm + 28), (@cx_u + 24), '확정', 'A', 1031.86, '2026-09-25 16:07:00'
       UNION ALL SELECT (@cx_p + 130), (@cx_a + 129), (@cx_sm + 28), (@cx_u + 23), '확정', 'A', 1030.06, '2026-09-25 16:22:00'
       UNION ALL SELECT (@cx_p + 131), (@cx_a + 132), (@cx_sm + 28), (@cx_u + 56), '확정', 'B', 1032.28, '2026-09-26 16:01:00'
       UNION ALL SELECT (@cx_p + 132), (@cx_a + 131), (@cx_sm + 28), (@cx_u + 55), '확정', 'B', 1061.47, '2026-09-26 16:45:00'
       UNION ALL SELECT (@cx_p + 133), (@cx_a + 134), (@cx_sm + 29), (@cx_u + 43), '확정', 'A', 968.00, '2026-09-30 00:09:00'
       UNION ALL SELECT (@cx_p + 134), (@cx_a + 133), (@cx_sm + 29), (@cx_u + 41), '확정', 'A', 940.48, '2026-09-30 00:33:00'
       UNION ALL SELECT (@cx_p + 135), (@cx_a + 135), (@cx_sm + 29), (@cx_u + 27), '확정', 'B', 1033.94, '2026-10-01 10:12:00'
       UNION ALL SELECT (@cx_p + 136), (@cx_a + 136), (@cx_sm + 29), (@cx_u + 28), '확정', 'B', 1032.14, '2026-10-01 10:34:00'
       UNION ALL SELECT (@cx_p + 137), (@cx_a + 138), (@cx_sm + 30), (@cx_u + 62), '확정', 'A', 1035.87, '2026-10-01 01:15:00'
       UNION ALL SELECT (@cx_p + 138), (@cx_a + 137), (@cx_sm + 30), (@cx_u + 59), '확정', 'A', 1092.45, '2026-10-01 02:25:00'
       UNION ALL SELECT (@cx_p + 139), (@cx_a + 139), (@cx_sm + 30), (@cx_u + 19), '확정', 'B', 1025.32, '2026-10-01 03:03:00'
       UNION ALL SELECT (@cx_p + 140), (@cx_a + 140), (@cx_sm + 30), (@cx_u + 22), '확정', 'B', 966.53, '2026-10-01 04:13:00'
       UNION ALL SELECT (@cx_p + 141), (@cx_a + 143), (@cx_sm + 31), (@cx_u + 15), '확정', 'B', 943.28, '2026-09-30 05:46:00'
       UNION ALL SELECT (@cx_p + 142), (@cx_a + 144), (@cx_sm + 31), (@cx_u + 17), '확정', 'B', 966.39, '2026-09-30 06:24:00'
       UNION ALL SELECT (@cx_p + 143), (@cx_a + 142), (@cx_sm + 31), (@cx_u + 38), '확정', 'A', 968.00, '2026-10-02 02:24:00'
       UNION ALL SELECT (@cx_p + 144), (@cx_a + 141), (@cx_sm + 31), (@cx_u + 37), '확정', 'A', 887.33, '2026-10-02 02:37:00'
       UNION ALL SELECT (@cx_p + 145), (@cx_a + 147), (@cx_sm + 32), (@cx_u + 7), '확정', 'B', 934.47, '2026-10-04 12:14:00'
       UNION ALL SELECT (@cx_p + 146), (@cx_a + 148), (@cx_sm + 32), (@cx_u + 9), '확정', 'B', 968.14, '2026-10-04 12:28:00'
       UNION ALL SELECT (@cx_p + 147), (@cx_a + 146), (@cx_sm + 32), (@cx_u + 14), '확정', 'A', 967.72, '2026-10-06 16:29:00'
       UNION ALL SELECT (@cx_p + 148), (@cx_a + 145), (@cx_sm + 32), (@cx_u + 11), '확정', 'A', 999.58, '2026-10-06 16:38:00'
       UNION ALL SELECT (@cx_p + 149), (@cx_a + 156), (@cx_sm + 33), (@cx_u + 33), '확정', 'B', 1032.00, '2026-10-05 16:13:00'
       UNION ALL SELECT (@cx_p + 150), (@cx_a + 155), (@cx_sm + 33), (@cx_u + 35), '확정', 'B', 1031.93, '2026-10-05 16:49:00'
       UNION ALL SELECT (@cx_p + 151), (@cx_a + 154), (@cx_sm + 33), (@cx_u + 32), '확정', 'B', 1055.37, '2026-10-05 17:44:00'
       UNION ALL SELECT (@cx_p + 152), (@cx_a + 153), (@cx_sm + 33), (@cx_u + 31), '확정', 'B', 1055.37, '2026-10-05 17:51:00'
       UNION ALL SELECT (@cx_p + 153), (@cx_a + 149), (@cx_sm + 33), (@cx_u + 49), '확정', 'A', 994.82, '2026-10-06 16:10:00'
       UNION ALL SELECT (@cx_p + 154), (@cx_a + 150), (@cx_sm + 33), (@cx_u + 50), '확정', 'A', 994.82, '2026-10-06 17:34:00'
       UNION ALL SELECT (@cx_p + 155), (@cx_a + 152), (@cx_sm + 33), (@cx_u + 51), '확정', 'A', 1030.53, '2026-10-06 17:34:00'
       UNION ALL SELECT (@cx_p + 156), (@cx_a + 151), (@cx_sm + 33), (@cx_u + 52), '확정', 'A', 968.07, '2026-10-06 17:55:00'
       UNION ALL SELECT (@cx_p + 157), (@cx_a + 157), (@cx_sm + 34), (@cx_u + 23), '확정', 'A', 1031.53, '2026-10-11 12:42:00'
       UNION ALL SELECT (@cx_p + 158), (@cx_a + 158), (@cx_sm + 34), (@cx_u + 26), '확정', 'A', 966.53, '2026-10-11 14:09:00'
       UNION ALL SELECT (@cx_p + 159), (@cx_a + 159), (@cx_sm + 34), (@cx_u + 11), '확정', 'B', 964.61, '2026-10-12 14:54:00'
       UNION ALL SELECT (@cx_p + 160), (@cx_a + 160), (@cx_sm + 34), (@cx_u + 13), '확정', 'B', 968.00, '2026-10-12 15:41:00'
       UNION ALL SELECT (@cx_p + 161), (@cx_a + 161), (@cx_sm + 35), (@cx_u + 45), '확정', 'A', 1051.62, '2026-10-12 12:33:00'
       UNION ALL SELECT (@cx_p + 162), (@cx_a + 162), (@cx_sm + 35), (@cx_u + 48), '확정', 'A', 1032.00, '2026-10-12 12:41:00'
       UNION ALL SELECT (@cx_p + 163), (@cx_a + 163), (@cx_sm + 35), (@cx_u + 19), '확정', 'B', 1063.52, '2026-10-13 22:11:00'
       UNION ALL SELECT (@cx_p + 164), (@cx_a + 164), (@cx_sm + 35), (@cx_u + 20), '확정', 'B', 1000.00, '2026-10-13 22:13:00'
       UNION ALL SELECT (@cx_p + 165), (@cx_a + 170), (@cx_sm + 36), (@cx_u + 32), '확정', 'B', 1083.10, '2026-10-12 10:40:00'
       UNION ALL SELECT (@cx_p + 166), (@cx_a + 172), (@cx_sm + 36), (@cx_u + 36), '확정', 'B', 1023.37, '2026-10-12 10:50:00'
       UNION ALL SELECT (@cx_p + 167), (@cx_a + 171), (@cx_sm + 36), (@cx_u + 35), '확정', 'B', 1059.66, '2026-10-12 11:04:00'
       UNION ALL SELECT (@cx_p + 168), (@cx_a + 169), (@cx_sm + 36), (@cx_u + 31), '확정', 'B', 1083.10, '2026-10-12 11:38:00'
       UNION ALL SELECT (@cx_p + 169), (@cx_a + 165), (@cx_sm + 36), (@cx_u + 49), '확정', 'A', 967.09, '2026-10-13 10:07:00'
       UNION ALL SELECT (@cx_p + 170), (@cx_a + 168), (@cx_sm + 36), (@cx_u + 54), '확정', 'A', 964.29, '2026-10-13 10:19:00'
       UNION ALL SELECT (@cx_p + 171), (@cx_a + 167), (@cx_sm + 36), (@cx_u + 51), '확정', 'A', 1002.80, '2026-10-13 10:46:00'
       UNION ALL SELECT (@cx_p + 172), (@cx_a + 166), (@cx_sm + 36), (@cx_u + 50), '확정', 'A', 967.09, '2026-10-13 10:55:00'
       UNION ALL SELECT (@cx_p + 173), (@cx_a + 175), (@cx_sm + 37), (@cx_u + 7), '확정', 'B', 969.44, '2026-10-14 02:31:00'
       UNION ALL SELECT (@cx_p + 174), (@cx_a + 176), (@cx_sm + 37), (@cx_u + 10), '확정', 'B', 966.53, '2026-10-14 03:31:00'
       UNION ALL SELECT (@cx_p + 175), (@cx_a + 173), (@cx_sm + 37), (@cx_u + 41), '확정', 'A', 915.62, '2026-10-15 01:30:00'
       UNION ALL SELECT (@cx_p + 176), (@cx_a + 174), (@cx_sm + 37), (@cx_u + 42), '확정', 'A', 1033.47, '2026-10-15 02:58:00'
       UNION ALL SELECT (@cx_p + 177), (@cx_a + 180), (@cx_sm + 38), (@cx_u + 60), '확정', 'B', 1000.00, '2026-10-13 13:27:00'
       UNION ALL SELECT (@cx_p + 178), (@cx_a + 179), (@cx_sm + 38), (@cx_u + 59), '확정', 'B', 1054.25, '2026-10-13 13:37:00'
       UNION ALL SELECT (@cx_p + 179), (@cx_a + 178), (@cx_sm + 38), (@cx_u + 16), '확정', 'A', 1033.47, '2026-10-14 18:02:00'
       UNION ALL SELECT (@cx_p + 180), (@cx_a + 177), (@cx_sm + 38), (@cx_u + 15), '확정', 'A', 972.78, '2026-10-14 18:20:00'
       UNION ALL SELECT (@cx_p + 181), (@cx_a + 181), (@cx_sm + 39), (@cx_u + 49), '확정', 'A', 942.94, '2026-10-19 11:06:00'
       UNION ALL SELECT (@cx_p + 182), (@cx_a + 182), (@cx_sm + 39), (@cx_u + 50), '확정', 'A', 942.94, '2026-10-19 11:19:00'
       UNION ALL SELECT (@cx_p + 183), (@cx_a + 184), (@cx_sm + 39), (@cx_u + 52), '확정', 'A', 940.34, '2026-10-19 11:30:00'
       UNION ALL SELECT (@cx_p + 184), (@cx_a + 183), (@cx_sm + 39), (@cx_u + 53), '확정', 'A', 1026.75, '2026-10-19 12:32:00'
       UNION ALL SELECT (@cx_p + 185), (@cx_a + 186), (@cx_sm + 39), (@cx_u + 2), '확정', 'B', 949.81, '2026-10-20 12:07:00'
       UNION ALL SELECT (@cx_p + 186), (@cx_a + 187), (@cx_sm + 39), (@cx_u + 3), '확정', 'B', 973.25, '2026-10-20 13:18:00'
       UNION ALL SELECT (@cx_p + 187), (@cx_a + 185), (@cx_sm + 39), (@cx_u + 1), '확정', 'B', 949.81, '2026-10-20 13:46:00'
       UNION ALL SELECT (@cx_p + 188), (@cx_a + 188), (@cx_sm + 39), (@cx_u + 4), '확정', 'B', 973.25, '2026-10-20 13:49:00'
       UNION ALL SELECT (@cx_p + 189), (@cx_a + 192), (@cx_sm + 40), (@cx_u + 30), '확정', 'B', 1033.47, '2026-10-24 13:01:00'
       UNION ALL SELECT (@cx_p + 190), (@cx_a + 191), (@cx_sm + 40), (@cx_u + 27), '확정', 'B', 1058.80, '2026-10-24 13:19:00'
       UNION ALL SELECT (@cx_p + 191), (@cx_a + 189), (@cx_sm + 40), (@cx_u + 55), '확정', 'A', 1060.00, '2026-10-26 17:15:00'
       UNION ALL SELECT (@cx_p + 192), (@cx_a + 190), (@cx_sm + 40), (@cx_u + 58), '확정', 'A', 1032.00, '2026-10-26 17:37:00'
       UNION ALL SELECT (@cx_p + 193), (@cx_a + 194), (@cx_sm + 41), (@cx_u + 47), '확정', 'A', 1026.43, '2026-10-30 16:06:00'
       UNION ALL SELECT (@cx_p + 194), (@cx_a + 193), (@cx_sm + 41), (@cx_u + 45), '확정', 'A', 1082.69, '2026-10-30 16:29:00'
       UNION ALL SELECT (@cx_p + 195), (@cx_a + 196), (@cx_sm + 41), (@cx_u + 39), '확정', 'B', 973.57, '2026-10-31 11:25:00'
       UNION ALL SELECT (@cx_p + 196), (@cx_a + 195), (@cx_sm + 41), (@cx_u + 37), '확정', 'B', 857.83, '2026-10-31 12:02:00'
       UNION ALL SELECT (@cx_p + 197), (@cx_a + 198), (@cx_sm + 42), (@cx_u + 32), '확정', 'A', 1107.25, '2026-11-03 11:22:00'
       UNION ALL SELECT (@cx_p + 198), (@cx_a + 199), (@cx_sm + 42), (@cx_u + 33), '확정', 'A', 1059.73, '2026-11-03 11:46:00'
       UNION ALL SELECT (@cx_p + 199), (@cx_a + 200), (@cx_sm + 42), (@cx_u + 34), '확정', 'A', 1023.44, '2026-11-03 12:24:00'
       UNION ALL SELECT (@cx_p + 200), (@cx_a + 197), (@cx_sm + 42), (@cx_u + 31), '확정', 'A', 1107.25, '2026-11-03 12:37:00'
       UNION ALL SELECT (@cx_p + 201), (@cx_a + 203), (@cx_sm + 42), (@cx_u + 5), '확정', 'B', 976.56, '2026-11-05 03:41:00'
       UNION ALL SELECT (@cx_p + 202), (@cx_a + 201), (@cx_sm + 42), (@cx_u + 1), '확정', 'B', 917.97, '2026-11-05 03:46:00'
       UNION ALL SELECT (@cx_p + 203), (@cx_a + 202), (@cx_sm + 42), (@cx_u + 2), '확정', 'B', 917.97, '2026-11-05 04:00:00'
       UNION ALL SELECT (@cx_p + 204), (@cx_a + 204), (@cx_sm + 42), (@cx_u + 6), '확정', 'B', 976.56, '2026-11-05 04:13:00'
) v
 WHERE @cx_ready = 1;
-- 팀장: 팀에서 entry_mmr이 가장 높은 참가자(같으면 번호가 작은 쪽, TeamCaptainSelector)
UPDATE social_match
   SET captain_a_participant_id = CASE match_id
         WHEN (@cx_sm + 1) THEN (@cx_p + 5)
         WHEN (@cx_sm + 2) THEN (@cx_p + 11)
         WHEN (@cx_sm + 3) THEN (@cx_p + 13)
         WHEN (@cx_sm + 4) THEN (@cx_p + 17)
         WHEN (@cx_sm + 5) THEN (@cx_p + 22)
         WHEN (@cx_sm + 6) THEN (@cx_p + 29)
         WHEN (@cx_sm + 7) THEN (@cx_p + 35)
         WHEN (@cx_sm + 8) THEN (@cx_p + 39)
         WHEN (@cx_sm + 9) THEN (@cx_p + 43)
         WHEN (@cx_sm + 10) THEN (@cx_p + 46)
         WHEN (@cx_sm + 11) THEN (@cx_p + 49)
         WHEN (@cx_sm + 12) THEN (@cx_p + 56)
         WHEN (@cx_sm + 13) THEN (@cx_p + 59)
         WHEN (@cx_sm + 14) THEN (@cx_p + 64)
         WHEN (@cx_sm + 15) THEN (@cx_p + 67)
         WHEN (@cx_sm + 16) THEN (@cx_p + 70)
         WHEN (@cx_sm + 17) THEN (@cx_p + 76)
         WHEN (@cx_sm + 18) THEN (@cx_p + 77)
         WHEN (@cx_sm + 19) THEN (@cx_p + 82)
         WHEN (@cx_sm + 20) THEN (@cx_p + 90)
         WHEN (@cx_sm + 21) THEN (@cx_p + 94)
         WHEN (@cx_sm + 22) THEN (@cx_p + 100)
         WHEN (@cx_sm + 23) THEN (@cx_p + 101)
         WHEN (@cx_sm + 24) THEN (@cx_p + 107)
         WHEN (@cx_sm + 25) THEN (@cx_p + 110)
         WHEN (@cx_sm + 26) THEN (@cx_p + 118)
         WHEN (@cx_sm + 27) THEN (@cx_p + 128)
         WHEN (@cx_sm + 28) THEN (@cx_p + 129)
         WHEN (@cx_sm + 29) THEN (@cx_p + 133)
         WHEN (@cx_sm + 30) THEN (@cx_p + 138)
         WHEN (@cx_sm + 31) THEN (@cx_p + 143)
         WHEN (@cx_sm + 32) THEN (@cx_p + 148)
         WHEN (@cx_sm + 33) THEN (@cx_p + 155)
         WHEN (@cx_sm + 34) THEN (@cx_p + 157)
         WHEN (@cx_sm + 35) THEN (@cx_p + 161)
         WHEN (@cx_sm + 36) THEN (@cx_p + 171)
         WHEN (@cx_sm + 37) THEN (@cx_p + 176)
         WHEN (@cx_sm + 38) THEN (@cx_p + 179)
         WHEN (@cx_sm + 39) THEN (@cx_p + 184)
         WHEN (@cx_sm + 40) THEN (@cx_p + 191)
         WHEN (@cx_sm + 41) THEN (@cx_p + 194)
         WHEN (@cx_sm + 42) THEN (@cx_p + 197)
       END,
       captain_b_participant_id = CASE match_id
         WHEN (@cx_sm + 1) THEN (@cx_p + 1)
         WHEN (@cx_sm + 2) THEN (@cx_p + 9)
         WHEN (@cx_sm + 3) THEN (@cx_p + 15)
         WHEN (@cx_sm + 4) THEN (@cx_p + 19)
         WHEN (@cx_sm + 5) THEN (@cx_p + 25)
         WHEN (@cx_sm + 6) THEN (@cx_p + 31)
         WHEN (@cx_sm + 7) THEN (@cx_p + 34)
         WHEN (@cx_sm + 8) THEN (@cx_p + 38)
         WHEN (@cx_sm + 9) THEN (@cx_p + 41)
         WHEN (@cx_sm + 10) THEN (@cx_p + 47)
         WHEN (@cx_sm + 11) THEN (@cx_p + 52)
         WHEN (@cx_sm + 12) THEN (@cx_p + 54)
         WHEN (@cx_sm + 13) THEN (@cx_p + 58)
         WHEN (@cx_sm + 14) THEN (@cx_p + 62)
         WHEN (@cx_sm + 15) THEN (@cx_p + 66)
         WHEN (@cx_sm + 16) THEN (@cx_p + 71)
         WHEN (@cx_sm + 17) THEN (@cx_p + 73)
         WHEN (@cx_sm + 18) THEN (@cx_p + 79)
         WHEN (@cx_sm + 19) THEN (@cx_p + 83)
         WHEN (@cx_sm + 20) THEN (@cx_p + 85)
         WHEN (@cx_sm + 21) THEN (@cx_p + 95)
         WHEN (@cx_sm + 22) THEN (@cx_p + 97)
         WHEN (@cx_sm + 23) THEN (@cx_p + 104)
         WHEN (@cx_sm + 24) THEN (@cx_p + 105)
         WHEN (@cx_sm + 25) THEN (@cx_p + 113)
         WHEN (@cx_sm + 26) THEN (@cx_p + 123)
         WHEN (@cx_sm + 27) THEN (@cx_p + 126)
         WHEN (@cx_sm + 28) THEN (@cx_p + 132)
         WHEN (@cx_sm + 29) THEN (@cx_p + 135)
         WHEN (@cx_sm + 30) THEN (@cx_p + 139)
         WHEN (@cx_sm + 31) THEN (@cx_p + 142)
         WHEN (@cx_sm + 32) THEN (@cx_p + 146)
         WHEN (@cx_sm + 33) THEN (@cx_p + 151)
         WHEN (@cx_sm + 34) THEN (@cx_p + 160)
         WHEN (@cx_sm + 35) THEN (@cx_p + 163)
         WHEN (@cx_sm + 36) THEN (@cx_p + 165)
         WHEN (@cx_sm + 37) THEN (@cx_p + 173)
         WHEN (@cx_sm + 38) THEN (@cx_p + 178)
         WHEN (@cx_sm + 39) THEN (@cx_p + 186)
         WHEN (@cx_sm + 40) THEN (@cx_p + 190)
         WHEN (@cx_sm + 41) THEN (@cx_p + 195)
         WHEN (@cx_sm + 42) THEN (@cx_p + 201)
       END
 WHERE @cx_ready = 1 AND match_id BETWEEN (@cx_sm + 1) AND (@cx_sm + 42);

-- 6. 두 팀장의 결과 제출 (같은 결과라 확정)
INSERT INTO match_result_submission (match_id, captain_participant_id, reported_result, submission_status, submitted_at)
SELECT * FROM (
                 SELECT (@cx_sm + 1) AS match_id, (@cx_p + 5) AS captain_participant_id, 'A_WIN' AS reported_result, 'CONFIRMED' AS submission_status, '2026-08-01 12:33:00' AS submitted_at
       UNION ALL SELECT (@cx_sm + 1), (@cx_p + 1), 'A_WIN', 'CONFIRMED', '2026-08-01 13:45:00'
       UNION ALL SELECT (@cx_sm + 2), (@cx_p + 11), 'A_WIN', 'CONFIRMED', '2026-08-07 09:53:00'
       UNION ALL SELECT (@cx_sm + 2), (@cx_p + 9), 'A_WIN', 'CONFIRMED', '2026-08-07 11:34:00'
       UNION ALL SELECT (@cx_sm + 3), (@cx_p + 13), 'DRAW', 'CONFIRMED', '2026-08-07 23:28:00'
       UNION ALL SELECT (@cx_sm + 3), (@cx_p + 15), 'DRAW', 'CONFIRMED', '2026-08-07 22:55:00'
       UNION ALL SELECT (@cx_sm + 4), (@cx_p + 17), 'B_WIN', 'CONFIRMED', '2026-08-08 21:35:00'
       UNION ALL SELECT (@cx_sm + 4), (@cx_p + 19), 'B_WIN', 'CONFIRMED', '2026-08-08 21:52:00'
       UNION ALL SELECT (@cx_sm + 5), (@cx_p + 22), 'B_WIN', 'CONFIRMED', '2026-08-09 12:25:00'
       UNION ALL SELECT (@cx_sm + 5), (@cx_p + 25), 'B_WIN', 'CONFIRMED', '2026-08-09 13:41:00'
       UNION ALL SELECT (@cx_sm + 6), (@cx_p + 29), 'B_WIN', 'CONFIRMED', '2026-08-11 23:38:00'
       UNION ALL SELECT (@cx_sm + 6), (@cx_p + 31), 'B_WIN', 'CONFIRMED', '2026-08-12 01:55:00'
       UNION ALL SELECT (@cx_sm + 7), (@cx_p + 35), 'B_WIN', 'CONFIRMED', '2026-08-14 22:34:00'
       UNION ALL SELECT (@cx_sm + 7), (@cx_p + 34), 'B_WIN', 'CONFIRMED', '2026-08-14 23:22:00'
       UNION ALL SELECT (@cx_sm + 8), (@cx_p + 39), 'A_WIN', 'CONFIRMED', '2026-08-14 23:28:00'
       UNION ALL SELECT (@cx_sm + 8), (@cx_p + 38), 'A_WIN', 'CONFIRMED', '2026-08-15 00:02:00'
       UNION ALL SELECT (@cx_sm + 9), (@cx_p + 43), 'A_WIN', 'CONFIRMED', '2026-08-15 11:38:00'
       UNION ALL SELECT (@cx_sm + 9), (@cx_p + 41), 'A_WIN', 'CONFIRMED', '2026-08-15 11:44:00'
       UNION ALL SELECT (@cx_sm + 10), (@cx_p + 46), 'A_WIN', 'CONFIRMED', '2026-08-16 10:55:00'
       UNION ALL SELECT (@cx_sm + 10), (@cx_p + 47), 'A_WIN', 'CONFIRMED', '2026-08-16 12:25:00'
       UNION ALL SELECT (@cx_sm + 11), (@cx_p + 49), 'A_WIN', 'CONFIRMED', '2026-08-18 22:56:00'
       UNION ALL SELECT (@cx_sm + 11), (@cx_p + 52), 'A_WIN', 'CONFIRMED', '2026-08-18 23:14:00'
       UNION ALL SELECT (@cx_sm + 12), (@cx_p + 56), 'B_WIN', 'CONFIRMED', '2026-08-19 22:21:00'
       UNION ALL SELECT (@cx_sm + 12), (@cx_p + 54), 'B_WIN', 'CONFIRMED', '2026-08-19 23:34:00'
       UNION ALL SELECT (@cx_sm + 13), (@cx_p + 59), 'A_WIN', 'CONFIRMED', '2026-08-24 23:00:00'
       UNION ALL SELECT (@cx_sm + 13), (@cx_p + 58), 'A_WIN', 'CONFIRMED', '2026-08-24 23:18:00'
       UNION ALL SELECT (@cx_sm + 14), (@cx_p + 64), 'B_WIN', 'CONFIRMED', '2026-08-26 22:13:00'
       UNION ALL SELECT (@cx_sm + 14), (@cx_p + 62), 'B_WIN', 'CONFIRMED', '2026-08-26 22:08:00'
       UNION ALL SELECT (@cx_sm + 15), (@cx_p + 67), 'A_WIN', 'CONFIRMED', '2026-08-27 22:34:00'
       UNION ALL SELECT (@cx_sm + 15), (@cx_p + 66), 'A_WIN', 'CONFIRMED', '2026-08-27 23:56:00'
       UNION ALL SELECT (@cx_sm + 16), (@cx_p + 70), 'B_WIN', 'CONFIRMED', '2026-08-30 11:15:00'
       UNION ALL SELECT (@cx_sm + 16), (@cx_p + 71), 'B_WIN', 'CONFIRMED', '2026-08-30 10:34:00'
       UNION ALL SELECT (@cx_sm + 17), (@cx_p + 76), 'B_WIN', 'CONFIRMED', '2026-08-30 12:32:00'
       UNION ALL SELECT (@cx_sm + 17), (@cx_p + 73), 'B_WIN', 'CONFIRMED', '2026-08-30 13:01:00'
       UNION ALL SELECT (@cx_sm + 18), (@cx_p + 77), 'A_WIN', 'CONFIRMED', '2026-09-02 22:40:00'
       UNION ALL SELECT (@cx_sm + 18), (@cx_p + 79), 'A_WIN', 'CONFIRMED', '2026-09-03 00:41:00'
       UNION ALL SELECT (@cx_sm + 19), (@cx_p + 82), 'B_WIN', 'CONFIRMED', '2026-09-11 09:53:00'
       UNION ALL SELECT (@cx_sm + 19), (@cx_p + 83), 'B_WIN', 'CONFIRMED', '2026-09-11 10:35:00'
       UNION ALL SELECT (@cx_sm + 20), (@cx_p + 90), 'B_WIN', 'CONFIRMED', '2026-09-11 21:25:00'
       UNION ALL SELECT (@cx_sm + 20), (@cx_p + 85), 'B_WIN', 'CONFIRMED', '2026-09-11 21:52:00'
       UNION ALL SELECT (@cx_sm + 21), (@cx_p + 94), 'B_WIN', 'CONFIRMED', '2026-09-12 10:33:00'
       UNION ALL SELECT (@cx_sm + 21), (@cx_p + 95), 'B_WIN', 'CONFIRMED', '2026-09-12 10:52:00'
       UNION ALL SELECT (@cx_sm + 22), (@cx_p + 100), 'A_WIN', 'CONFIRMED', '2026-09-13 12:00:00'
       UNION ALL SELECT (@cx_sm + 22), (@cx_p + 97), 'A_WIN', 'CONFIRMED', '2026-09-13 13:15:00'
       UNION ALL SELECT (@cx_sm + 23), (@cx_p + 101), 'DRAW', 'CONFIRMED', '2026-09-14 22:50:00'
       UNION ALL SELECT (@cx_sm + 23), (@cx_p + 104), 'DRAW', 'CONFIRMED', '2026-09-14 23:56:00'
       UNION ALL SELECT (@cx_sm + 24), (@cx_p + 107), 'B_WIN', 'CONFIRMED', '2026-09-16 21:34:00'
       UNION ALL SELECT (@cx_sm + 24), (@cx_p + 105), 'B_WIN', 'CONFIRMED', '2026-09-16 23:28:00'
       UNION ALL SELECT (@cx_sm + 25), (@cx_p + 110), 'DRAW', 'CONFIRMED', '2026-09-19 12:58:00'
       UNION ALL SELECT (@cx_sm + 25), (@cx_p + 113), 'DRAW', 'CONFIRMED', '2026-09-19 14:53:00'
       UNION ALL SELECT (@cx_sm + 26), (@cx_p + 118), 'DRAW', 'CONFIRMED', '2026-09-25 21:48:00'
       UNION ALL SELECT (@cx_sm + 26), (@cx_p + 123), 'DRAW', 'CONFIRMED', '2026-09-25 23:52:00'
       UNION ALL SELECT (@cx_sm + 27), (@cx_p + 128), 'A_WIN', 'CONFIRMED', '2026-09-25 23:15:00'
       UNION ALL SELECT (@cx_sm + 27), (@cx_p + 126), 'A_WIN', 'CONFIRMED', '2026-09-25 23:22:00'
       UNION ALL SELECT (@cx_sm + 28), (@cx_p + 129), 'DRAW', 'CONFIRMED', '2026-09-28 22:41:00'
       UNION ALL SELECT (@cx_sm + 28), (@cx_p + 132), 'DRAW', 'CONFIRMED', '2026-09-28 22:16:00'
       UNION ALL SELECT (@cx_sm + 29), (@cx_p + 133), 'B_WIN', 'CONFIRMED', '2026-10-03 10:52:00'
       UNION ALL SELECT (@cx_sm + 29), (@cx_p + 135), 'B_WIN', 'CONFIRMED', '2026-10-03 11:22:00'
       UNION ALL SELECT (@cx_sm + 30), (@cx_p + 138), 'B_WIN', 'CONFIRMED', '2026-10-03 11:25:00'
       UNION ALL SELECT (@cx_sm + 30), (@cx_p + 139), 'B_WIN', 'CONFIRMED', '2026-10-03 11:30:00'
       UNION ALL SELECT (@cx_sm + 31), (@cx_p + 143), 'B_WIN', 'CONFIRMED', '2026-10-04 13:02:00'
       UNION ALL SELECT (@cx_sm + 31), (@cx_p + 142), 'B_WIN', 'CONFIRMED', '2026-10-04 13:13:00'
       UNION ALL SELECT (@cx_sm + 32), (@cx_p + 148), 'B_WIN', 'CONFIRMED', '2026-10-08 23:58:00'
       UNION ALL SELECT (@cx_sm + 32), (@cx_p + 146), 'B_WIN', 'CONFIRMED', '2026-10-09 01:47:00'
       UNION ALL SELECT (@cx_sm + 33), (@cx_p + 155), 'B_WIN', 'CONFIRMED', '2026-10-09 21:49:00'
       UNION ALL SELECT (@cx_sm + 33), (@cx_p + 151), 'B_WIN', 'CONFIRMED', '2026-10-09 21:40:00'
       UNION ALL SELECT (@cx_sm + 34), (@cx_p + 157), 'A_WIN', 'CONFIRMED', '2026-10-15 22:27:00'
       UNION ALL SELECT (@cx_sm + 34), (@cx_p + 160), 'A_WIN', 'CONFIRMED', '2026-10-15 23:49:00'
       UNION ALL SELECT (@cx_sm + 35), (@cx_p + 161), 'A_WIN', 'CONFIRMED', '2026-10-15 22:57:00'
       UNION ALL SELECT (@cx_sm + 35), (@cx_p + 163), 'A_WIN', 'CONFIRMED', '2026-10-15 23:22:00'
       UNION ALL SELECT (@cx_sm + 36), (@cx_p + 171), 'B_WIN', 'CONFIRMED', '2026-10-16 21:30:00'
       UNION ALL SELECT (@cx_sm + 36), (@cx_p + 165), 'B_WIN', 'CONFIRMED', '2026-10-16 22:33:00'
       UNION ALL SELECT (@cx_sm + 37), (@cx_p + 176), 'B_WIN', 'CONFIRMED', '2026-10-17 10:55:00'
       UNION ALL SELECT (@cx_sm + 37), (@cx_p + 173), 'B_WIN', 'CONFIRMED', '2026-10-17 12:20:00'
       UNION ALL SELECT (@cx_sm + 38), (@cx_p + 179), 'B_WIN', 'CONFIRMED', '2026-10-17 22:22:00'
       UNION ALL SELECT (@cx_sm + 38), (@cx_p + 178), 'B_WIN', 'CONFIRMED', '2026-10-17 23:53:00'
       UNION ALL SELECT (@cx_sm + 39), (@cx_p + 184), 'A_WIN', 'CONFIRMED', '2026-10-23 22:04:00'
       UNION ALL SELECT (@cx_sm + 39), (@cx_p + 186), 'A_WIN', 'CONFIRMED', '2026-10-23 21:43:00'
       UNION ALL SELECT (@cx_sm + 40), (@cx_p + 191), 'DRAW', 'CONFIRMED', '2026-10-28 23:28:00'
       UNION ALL SELECT (@cx_sm + 40), (@cx_p + 190), 'DRAW', 'CONFIRMED', '2026-10-28 22:39:00'
       UNION ALL SELECT (@cx_sm + 41), (@cx_p + 194), 'B_WIN', 'CONFIRMED', '2026-11-03 23:16:00'
       UNION ALL SELECT (@cx_sm + 41), (@cx_p + 195), 'B_WIN', 'CONFIRMED', '2026-11-04 00:37:00'
       UNION ALL SELECT (@cx_sm + 42), (@cx_p + 197), 'B_WIN', 'CONFIRMED', '2026-11-07 13:01:00'
       UNION ALL SELECT (@cx_sm + 42), (@cx_p + 201), 'B_WIN', 'CONFIRMED', '2026-11-07 13:54:00'
) v
 WHERE @cx_ready = 1;

-- 7. 레이팅 이력 (평가 없이 반영: evaluation_delta 0, evaluation_score NULL)
INSERT INTO sport_rating_history (match_id, participant_id, user_id, sport_id, mmr_before, mmr_after, elo_delta, evaluation_delta, final_delta, evaluation_score, actual_score, expected_score, team_avg_mmr_a, team_avg_mmr_b, algorithm_version, calculated_at)
SELECT * FROM (
                 SELECT (@cx_sm + 1) AS match_id, (@cx_p + 3) AS participant_id, (@cx_u + 1) AS user_id, @cx_s_농구 AS sport_id, 1000.00 AS mmr_before, 968.00 AS mmr_after, -32.00 AS elo_delta, 0.00 AS evaluation_delta, -32.00 AS final_delta, NULL AS evaluation_score, 0.0 AS actual_score, 0.5000 AS expected_score, 1000.00 AS team_avg_mmr_a, 1000.00 AS team_avg_mmr_b, 'ELO-K32-V2' AS algorithm_version, '2026-08-02 12:00:00' AS calculated_at
       UNION ALL SELECT (@cx_sm + 1), (@cx_p + 2), (@cx_u + 2), @cx_s_농구, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 12:00:00'
       UNION ALL SELECT (@cx_sm + 1), (@cx_p + 4), (@cx_u + 5), @cx_s_농구, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 12:00:00'
       UNION ALL SELECT (@cx_sm + 1), (@cx_p + 1), (@cx_u + 6), @cx_s_농구, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 12:00:00'
       UNION ALL SELECT (@cx_sm + 1), (@cx_p + 7), (@cx_u + 31), @cx_s_농구, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 12:00:00'
       UNION ALL SELECT (@cx_sm + 1), (@cx_p + 5), (@cx_u + 32), @cx_s_농구, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 12:00:00'
       UNION ALL SELECT (@cx_sm + 1), (@cx_p + 6), (@cx_u + 33), @cx_s_농구, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 12:00:00'
       UNION ALL SELECT (@cx_sm + 1), (@cx_p + 8), (@cx_u + 34), @cx_s_농구, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 12:00:00'
       UNION ALL SELECT (@cx_sm + 2), (@cx_p + 12), (@cx_u + 7), @cx_s_테니스, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-08 09:00:00'
       UNION ALL SELECT (@cx_sm + 2), (@cx_p + 11), (@cx_u + 8), @cx_s_테니스, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-08 09:00:00'
       UNION ALL SELECT (@cx_sm + 2), (@cx_p + 9), (@cx_u + 41), @cx_s_테니스, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-08 09:00:00'
       UNION ALL SELECT (@cx_sm + 2), (@cx_p + 10), (@cx_u + 43), @cx_s_테니스, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-08 09:00:00'
       UNION ALL SELECT (@cx_sm + 3), (@cx_p + 13), (@cx_u + 19), @cx_s_배드민턴, 1000.00, 1000.00, 0.00, 0.00, 0.00, NULL, 0.5, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-08 22:00:00'
       UNION ALL SELECT (@cx_sm + 3), (@cx_p + 14), (@cx_u + 20), @cx_s_배드민턴, 1000.00, 1000.00, 0.00, 0.00, 0.00, NULL, 0.5, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-08 22:00:00'
       UNION ALL SELECT (@cx_sm + 3), (@cx_p + 16), (@cx_u + 59), @cx_s_배드민턴, 1000.00, 1000.00, 0.00, 0.00, 0.00, NULL, 0.5, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-08 22:00:00'
       UNION ALL SELECT (@cx_sm + 3), (@cx_p + 15), (@cx_u + 60), @cx_s_배드민턴, 1000.00, 1000.00, 0.00, 0.00, 0.00, NULL, 0.5, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-08 22:00:00'
       UNION ALL SELECT (@cx_sm + 4), (@cx_p + 18), (@cx_u + 15), @cx_s_배드민턴, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-09 21:00:00'
       UNION ALL SELECT (@cx_sm + 4), (@cx_p + 17), (@cx_u + 18), @cx_s_배드민턴, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-09 21:00:00'
       UNION ALL SELECT (@cx_sm + 4), (@cx_p + 20), (@cx_u + 45), @cx_s_배드민턴, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-09 21:00:00'
       UNION ALL SELECT (@cx_sm + 4), (@cx_p + 19), (@cx_u + 48), @cx_s_배드민턴, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-09 21:00:00'
       UNION ALL SELECT (@cx_sm + 5), (@cx_p + 24), (@cx_u + 1), @cx_s_농구, 968.00, 937.47, -30.53, 0.00, -30.53, NULL, 0.0, 0.4770, 984.00, 1000.00, 'ELO-K32-V2', '2026-08-10 11:00:00'
       UNION ALL SELECT (@cx_sm + 5), (@cx_p + 21), (@cx_u + 2), @cx_s_농구, 968.00, 937.47, -30.53, 0.00, -30.53, NULL, 0.0, 0.4770, 984.00, 1000.00, 'ELO-K32-V2', '2026-08-10 11:00:00'
       UNION ALL SELECT (@cx_sm + 5), (@cx_p + 23), (@cx_u + 3), @cx_s_농구, 1000.00, 969.47, -30.53, 0.00, -30.53, NULL, 0.0, 0.4770, 984.00, 1000.00, 'ELO-K32-V2', '2026-08-10 11:00:00'
       UNION ALL SELECT (@cx_sm + 5), (@cx_p + 22), (@cx_u + 4), @cx_s_농구, 1000.00, 969.47, -30.53, 0.00, -30.53, NULL, 0.0, 0.4770, 984.00, 1000.00, 'ELO-K32-V2', '2026-08-10 11:00:00'
       UNION ALL SELECT (@cx_sm + 5), (@cx_p + 28), (@cx_u + 49), @cx_s_농구, 1000.00, 1030.53, 30.53, 0.00, 30.53, NULL, 1.0, 0.5230, 984.00, 1000.00, 'ELO-K32-V2', '2026-08-10 11:00:00'
       UNION ALL SELECT (@cx_sm + 5), (@cx_p + 27), (@cx_u + 50), @cx_s_농구, 1000.00, 1030.53, 30.53, 0.00, 30.53, NULL, 1.0, 0.5230, 984.00, 1000.00, 'ELO-K32-V2', '2026-08-10 11:00:00'
       UNION ALL SELECT (@cx_sm + 5), (@cx_p + 25), (@cx_u + 51), @cx_s_농구, 1000.00, 1030.53, 30.53, 0.00, 30.53, NULL, 1.0, 0.5230, 984.00, 1000.00, 'ELO-K32-V2', '2026-08-10 11:00:00'
       UNION ALL SELECT (@cx_sm + 5), (@cx_p + 26), (@cx_u + 53), @cx_s_농구, 1000.00, 1030.53, 30.53, 0.00, 30.53, NULL, 1.0, 0.5230, 984.00, 1000.00, 'ELO-K32-V2', '2026-08-10 11:00:00'
       UNION ALL SELECT (@cx_sm + 6), (@cx_p + 29), (@cx_u + 11), @cx_s_테니스, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-12 23:00:00'
       UNION ALL SELECT (@cx_sm + 6), (@cx_p + 30), (@cx_u + 13), @cx_s_테니스, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-12 23:00:00'
       UNION ALL SELECT (@cx_sm + 6), (@cx_p + 32), (@cx_u + 55), @cx_s_테니스, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-12 23:00:00'
       UNION ALL SELECT (@cx_sm + 6), (@cx_p + 31), (@cx_u + 58), @cx_s_테니스, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-12 23:00:00'
       UNION ALL SELECT (@cx_sm + 7), (@cx_p + 33), (@cx_u + 15), @cx_s_배드민턴, 968.00, 1001.47, 33.47, 0.00, 33.47, NULL, 1.0, 0.4770, 1000.00, 984.00, 'ELO-K32-V2', '2026-08-15 22:00:00'
       UNION ALL SELECT (@cx_sm + 7), (@cx_p + 34), (@cx_u + 16), @cx_s_배드민턴, 1000.00, 1033.47, 33.47, 0.00, 33.47, NULL, 1.0, 0.4770, 1000.00, 984.00, 'ELO-K32-V2', '2026-08-15 22:00:00'
       UNION ALL SELECT (@cx_sm + 7), (@cx_p + 35), (@cx_u + 19), @cx_s_배드민턴, 1000.00, 966.53, -33.47, 0.00, -33.47, NULL, 0.0, 0.5230, 1000.00, 984.00, 'ELO-K32-V2', '2026-08-15 22:00:00'
       UNION ALL SELECT (@cx_sm + 7), (@cx_p + 36), (@cx_u + 22), @cx_s_배드민턴, 1000.00, 966.53, -33.47, 0.00, -33.47, NULL, 0.0, 0.5230, 1000.00, 984.00, 'ELO-K32-V2', '2026-08-15 22:00:00'
       UNION ALL SELECT (@cx_sm + 8), (@cx_p + 38), (@cx_u + 7), @cx_s_테니스, 1032.00, 998.53, -33.47, 0.00, -33.47, NULL, 0.0, 0.5230, 1000.00, 1016.00, 'ELO-K32-V2', '2026-08-15 22:00:00'
       UNION ALL SELECT (@cx_sm + 8), (@cx_p + 37), (@cx_u + 10), @cx_s_테니스, 1000.00, 966.53, -33.47, 0.00, -33.47, NULL, 0.0, 0.5230, 1000.00, 1016.00, 'ELO-K32-V2', '2026-08-15 22:00:00'
       UNION ALL SELECT (@cx_sm + 8), (@cx_p + 39), (@cx_u + 27), @cx_s_테니스, 1000.00, 1033.47, 33.47, 0.00, 33.47, NULL, 1.0, 0.4770, 1000.00, 1016.00, 'ELO-K32-V2', '2026-08-15 22:00:00'
       UNION ALL SELECT (@cx_sm + 8), (@cx_p + 40), (@cx_u + 30), @cx_s_테니스, 1000.00, 1033.47, 33.47, 0.00, 33.47, NULL, 1.0, 0.4770, 1000.00, 1016.00, 'ELO-K32-V2', '2026-08-15 22:00:00'
       UNION ALL SELECT (@cx_sm + 9), (@cx_p + 41), (@cx_u + 37), @cx_s_배드민턴, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-16 11:00:00'
       UNION ALL SELECT (@cx_sm + 9), (@cx_p + 42), (@cx_u + 38), @cx_s_배드민턴, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-16 11:00:00'
       UNION ALL SELECT (@cx_sm + 9), (@cx_p + 43), (@cx_u + 59), @cx_s_배드민턴, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-16 11:00:00'
       UNION ALL SELECT (@cx_sm + 9), (@cx_p + 44), (@cx_u + 61), @cx_s_배드민턴, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-16 11:00:00'
       UNION ALL SELECT (@cx_sm + 10), (@cx_p + 47), (@cx_u + 23), @cx_s_테니스, 1000.00, 966.53, -33.47, 0.00, -33.47, NULL, 0.0, 0.5230, 984.00, 1000.00, 'ELO-K32-V2', '2026-08-17 10:00:00'
       UNION ALL SELECT (@cx_sm + 10), (@cx_p + 48), (@cx_u + 26), @cx_s_테니스, 1000.00, 966.53, -33.47, 0.00, -33.47, NULL, 0.0, 0.5230, 984.00, 1000.00, 'ELO-K32-V2', '2026-08-17 10:00:00'
       UNION ALL SELECT (@cx_sm + 10), (@cx_p + 45), (@cx_u + 41), @cx_s_테니스, 968.00, 1001.47, 33.47, 0.00, 33.47, NULL, 1.0, 0.4770, 984.00, 1000.00, 'ELO-K32-V2', '2026-08-17 10:00:00'
       UNION ALL SELECT (@cx_sm + 10), (@cx_p + 46), (@cx_u + 42), @cx_s_테니스, 1000.00, 1033.47, 33.47, 0.00, 33.47, NULL, 1.0, 0.4770, 984.00, 1000.00, 'ELO-K32-V2', '2026-08-17 10:00:00'
       UNION ALL SELECT (@cx_sm + 11), (@cx_p + 51), (@cx_u + 37), @cx_s_배드민턴, 968.00, 938.94, -29.06, 0.00, -29.06, NULL, 0.0, 0.4541, 1016.00, 984.00, 'ELO-K32-V2', '2026-08-19 22:00:00'
       UNION ALL SELECT (@cx_sm + 11), (@cx_p + 52), (@cx_u + 40), @cx_s_배드민턴, 1000.00, 970.94, -29.06, 0.00, -29.06, NULL, 0.0, 0.4541, 1016.00, 984.00, 'ELO-K32-V2', '2026-08-19 22:00:00'
       UNION ALL SELECT (@cx_sm + 11), (@cx_p + 49), (@cx_u + 45), @cx_s_배드민턴, 1032.00, 1061.06, 29.06, 0.00, 29.06, NULL, 1.0, 0.5459, 1016.00, 984.00, 'ELO-K32-V2', '2026-08-19 22:00:00'
       UNION ALL SELECT (@cx_sm + 11), (@cx_p + 50), (@cx_u + 46), @cx_s_배드민턴, 1000.00, 1029.06, 29.06, 0.00, 29.06, NULL, 1.0, 0.5459, 1016.00, 984.00, 'ELO-K32-V2', '2026-08-19 22:00:00'
       UNION ALL SELECT (@cx_sm + 12), (@cx_p + 53), (@cx_u + 11), @cx_s_테니스, 968.00, 1003.01, 35.01, 0.00, 35.01, NULL, 1.0, 0.4530, 1016.74, 984.00, 'ELO-K32-V2', '2026-08-20 22:00:00'
       UNION ALL SELECT (@cx_sm + 12), (@cx_p + 54), (@cx_u + 12), @cx_s_테니스, 1000.00, 1035.01, 35.01, 0.00, 35.01, NULL, 1.0, 0.4530, 1016.74, 984.00, 'ELO-K32-V2', '2026-08-20 22:00:00'
       UNION ALL SELECT (@cx_sm + 12), (@cx_p + 56), (@cx_u + 27), @cx_s_테니스, 1033.47, 998.46, -35.01, 0.00, -35.01, NULL, 0.0, 0.5470, 1016.74, 984.00, 'ELO-K32-V2', '2026-08-20 22:00:00'
       UNION ALL SELECT (@cx_sm + 12), (@cx_p + 55), (@cx_u + 29), @cx_s_테니스, 1000.00, 964.99, -35.01, 0.00, -35.01, NULL, 0.0, 0.5470, 1016.74, 984.00, 'ELO-K32-V2', '2026-08-20 22:00:00'
       UNION ALL SELECT (@cx_sm + 13), (@cx_p + 60), (@cx_u + 23), @cx_s_테니스, 966.53, 1001.54, 35.01, 0.00, 35.01, NULL, 1.0, 0.4530, 983.27, 1016.00, 'ELO-K32-V2', '2026-08-25 21:30:00'
       UNION ALL SELECT (@cx_sm + 13), (@cx_p + 59), (@cx_u + 25), @cx_s_테니스, 1000.00, 1035.01, 35.01, 0.00, 35.01, NULL, 1.0, 0.4530, 983.27, 1016.00, 'ELO-K32-V2', '2026-08-25 21:30:00'
       UNION ALL SELECT (@cx_sm + 13), (@cx_p + 58), (@cx_u + 55), @cx_s_테니스, 1032.00, 996.99, -35.01, 0.00, -35.01, NULL, 0.0, 0.5470, 983.27, 1016.00, 'ELO-K32-V2', '2026-08-25 21:30:00'
       UNION ALL SELECT (@cx_sm + 13), (@cx_p + 57), (@cx_u + 57), @cx_s_테니스, 1000.00, 964.99, -35.01, 0.00, -35.01, NULL, 0.0, 0.5470, 983.27, 1016.00, 'ELO-K32-V2', '2026-08-25 21:30:00'
       UNION ALL SELECT (@cx_sm + 14), (@cx_p + 64), (@cx_u + 15), @cx_s_배드민턴, 1001.47, 967.86, -33.61, 0.00, -33.61, NULL, 0.0, 0.5251, 1000.74, 983.27, 'ELO-K32-V2', '2026-08-27 21:00:00'
       UNION ALL SELECT (@cx_sm + 14), (@cx_p + 63), (@cx_u + 17), @cx_s_배드민턴, 1000.00, 966.39, -33.61, 0.00, -33.61, NULL, 0.0, 0.5251, 1000.74, 983.27, 'ELO-K32-V2', '2026-08-27 21:00:00'
       UNION ALL SELECT (@cx_sm + 14), (@cx_p + 61), (@cx_u + 19), @cx_s_배드민턴, 966.53, 1000.14, 33.61, 0.00, 33.61, NULL, 1.0, 0.4749, 1000.74, 983.27, 'ELO-K32-V2', '2026-08-27 21:00:00'
       UNION ALL SELECT (@cx_sm + 14), (@cx_p + 62), (@cx_u + 21), @cx_s_배드민턴, 1000.00, 1033.61, 33.61, 0.00, 33.61, NULL, 1.0, 0.4749, 1000.74, 983.27, 'ELO-K32-V2', '2026-08-27 21:00:00'
       UNION ALL SELECT (@cx_sm + 15), (@cx_p + 65), (@cx_u + 7), @cx_s_테니스, 998.53, 966.67, -31.86, 0.00, -31.86, NULL, 0.0, 0.4978, 1000.77, 999.27, 'ELO-K32-V2', '2026-08-28 21:30:00'
       UNION ALL SELECT (@cx_sm + 15), (@cx_p + 66), (@cx_u + 9), @cx_s_테니스, 1000.00, 968.14, -31.86, 0.00, -31.86, NULL, 0.0, 0.4978, 1000.77, 999.27, 'ELO-K32-V2', '2026-08-28 21:30:00'
       UNION ALL SELECT (@cx_sm + 15), (@cx_p + 67), (@cx_u + 23), @cx_s_테니스, 1001.54, 1033.40, 31.86, 0.00, 31.86, NULL, 1.0, 0.5022, 1000.77, 999.27, 'ELO-K32-V2', '2026-08-28 21:30:00'
       UNION ALL SELECT (@cx_sm + 15), (@cx_p + 68), (@cx_u + 24), @cx_s_테니스, 1000.00, 1031.86, 31.86, 0.00, 31.86, NULL, 1.0, 0.5022, 1000.77, 999.27, 'ELO-K32-V2', '2026-08-28 21:30:00'
       UNION ALL SELECT (@cx_sm + 16), (@cx_p + 72), (@cx_u + 27), @cx_s_테니스, 998.46, 1030.60, 32.14, 0.00, 32.14, NULL, 1.0, 0.4978, 1000.74, 999.23, 'ELO-K32-V2', '2026-08-31 10:00:00'
       UNION ALL SELECT (@cx_sm + 16), (@cx_p + 71), (@cx_u + 28), @cx_s_테니스, 1000.00, 1032.14, 32.14, 0.00, 32.14, NULL, 1.0, 0.4978, 1000.74, 999.23, 'ELO-K32-V2', '2026-08-31 10:00:00'
       UNION ALL SELECT (@cx_sm + 16), (@cx_p + 70), (@cx_u + 41), @cx_s_테니스, 1001.47, 969.33, -32.14, 0.00, -32.14, NULL, 0.0, 0.5022, 1000.74, 999.23, 'ELO-K32-V2', '2026-08-31 10:00:00'
       UNION ALL SELECT (@cx_sm + 16), (@cx_p + 69), (@cx_u + 44), @cx_s_테니스, 1000.00, 967.86, -32.14, 0.00, -32.14, NULL, 0.0, 0.5022, 1000.74, 999.23, 'ELO-K32-V2', '2026-08-31 10:00:00'
       UNION ALL SELECT (@cx_sm + 17), (@cx_p + 75), (@cx_u + 37), @cx_s_배드민턴, 938.94, 912.51, -26.43, 0.00, -26.43, NULL, 0.0, 0.4130, 969.47, 1030.53, 'ELO-K32-V2', '2026-08-31 12:00:00'
       UNION ALL SELECT (@cx_sm + 17), (@cx_p + 76), (@cx_u + 39), @cx_s_배드민턴, 1000.00, 973.57, -26.43, 0.00, -26.43, NULL, 0.0, 0.4130, 969.47, 1030.53, 'ELO-K32-V2', '2026-08-31 12:00:00'
       UNION ALL SELECT (@cx_sm + 17), (@cx_p + 73), (@cx_u + 45), @cx_s_배드민턴, 1061.06, 1087.49, 26.43, 0.00, 26.43, NULL, 1.0, 0.5870, 969.47, 1030.53, 'ELO-K32-V2', '2026-08-31 12:00:00'
       UNION ALL SELECT (@cx_sm + 17), (@cx_p + 74), (@cx_u + 47), @cx_s_배드민턴, 1000.00, 1026.43, 26.43, 0.00, 26.43, NULL, 1.0, 0.5870, 969.47, 1030.53, 'ELO-K32-V2', '2026-08-31 12:00:00'
       UNION ALL SELECT (@cx_sm + 18), (@cx_p + 79), (@cx_u + 11), @cx_s_테니스, 1003.01, 970.73, -32.28, 0.00, -32.28, NULL, 0.0, 0.5043, 998.50, 1001.51, 'ELO-K32-V2', '2026-09-03 22:00:00'
       UNION ALL SELECT (@cx_sm + 18), (@cx_p + 80), (@cx_u + 14), @cx_s_테니스, 1000.00, 967.72, -32.28, 0.00, -32.28, NULL, 0.0, 0.5043, 998.50, 1001.51, 'ELO-K32-V2', '2026-09-03 22:00:00'
       UNION ALL SELECT (@cx_sm + 18), (@cx_p + 78), (@cx_u + 55), @cx_s_테니스, 996.99, 1029.27, 32.28, 0.00, 32.28, NULL, 1.0, 0.4957, 998.50, 1001.51, 'ELO-K32-V2', '2026-09-03 22:00:00'
       UNION ALL SELECT (@cx_sm + 18), (@cx_p + 77), (@cx_u + 56), @cx_s_테니스, 1000.00, 1032.28, 32.28, 0.00, 32.28, NULL, 1.0, 0.4957, 998.50, 1001.51, 'ELO-K32-V2', '2026-09-03 22:00:00'
       UNION ALL SELECT (@cx_sm + 19), (@cx_p + 81), (@cx_u + 7), @cx_s_테니스, 966.67, 934.47, -32.20, 0.00, -32.20, NULL, 0.0, 0.5032, 999.34, 997.13, 'ELO-K32-V2', '2026-09-12 09:00:00'
       UNION ALL SELECT (@cx_sm + 19), (@cx_p + 82), (@cx_u + 8), @cx_s_테니스, 1032.00, 999.80, -32.20, 0.00, -32.20, NULL, 0.0, 0.5032, 999.34, 997.13, 'ELO-K32-V2', '2026-09-12 09:00:00'
       UNION ALL SELECT (@cx_sm + 19), (@cx_p + 83), (@cx_u + 55), @cx_s_테니스, 1029.27, 1061.47, 32.20, 0.00, 32.20, NULL, 1.0, 0.4968, 999.34, 997.13, 'ELO-K32-V2', '2026-09-12 09:00:00'
       UNION ALL SELECT (@cx_sm + 19), (@cx_p + 84), (@cx_u + 57), @cx_s_테니스, 964.99, 997.19, 32.20, 0.00, 32.20, NULL, 1.0, 0.4968, 999.34, 997.13, 'ELO-K32-V2', '2026-09-12 09:00:00'
       UNION ALL SELECT (@cx_sm + 20), (@cx_p + 85), (@cx_u + 31), @cx_s_농구, 1032.00, 1063.93, 31.93, 0.00, 31.93, NULL, 1.0, 0.5011, 1015.27, 1016.00, 'ELO-K32-V2', '2026-09-12 21:00:00'
       UNION ALL SELECT (@cx_sm + 20), (@cx_p + 88), (@cx_u + 32), @cx_s_농구, 1032.00, 1063.93, 31.93, 0.00, 31.93, NULL, 1.0, 0.5011, 1015.27, 1016.00, 'ELO-K32-V2', '2026-09-12 21:00:00'
       UNION ALL SELECT (@cx_sm + 20), (@cx_p + 87), (@cx_u + 35), @cx_s_농구, 1000.00, 1031.93, 31.93, 0.00, 31.93, NULL, 1.0, 0.5011, 1015.27, 1016.00, 'ELO-K32-V2', '2026-09-12 21:00:00'
       UNION ALL SELECT (@cx_sm + 20), (@cx_p + 86), (@cx_u + 36), @cx_s_농구, 1000.00, 1031.93, 31.93, 0.00, 31.93, NULL, 1.0, 0.5011, 1015.27, 1016.00, 'ELO-K32-V2', '2026-09-12 21:00:00'
       UNION ALL SELECT (@cx_sm + 20), (@cx_p + 90), (@cx_u + 49), @cx_s_농구, 1030.53, 998.60, -31.93, 0.00, -31.93, NULL, 0.0, 0.4989, 1015.27, 1016.00, 'ELO-K32-V2', '2026-09-12 21:00:00'
       UNION ALL SELECT (@cx_sm + 20), (@cx_p + 91), (@cx_u + 50), @cx_s_농구, 1030.53, 998.60, -31.93, 0.00, -31.93, NULL, 0.0, 0.4989, 1015.27, 1016.00, 'ELO-K32-V2', '2026-09-12 21:00:00'
       UNION ALL SELECT (@cx_sm + 20), (@cx_p + 89), (@cx_u + 52), @cx_s_농구, 1000.00, 968.07, -31.93, 0.00, -31.93, NULL, 0.0, 0.4989, 1015.27, 1016.00, 'ELO-K32-V2', '2026-09-12 21:00:00'
       UNION ALL SELECT (@cx_sm + 20), (@cx_p + 92), (@cx_u + 54), @cx_s_농구, 1000.00, 968.07, -31.93, 0.00, -31.93, NULL, 0.0, 0.4989, 1015.27, 1016.00, 'ELO-K32-V2', '2026-09-12 21:00:00'
       UNION ALL SELECT (@cx_sm + 21), (@cx_p + 96), (@cx_u + 11), @cx_s_테니스, 970.73, 999.58, 28.85, 0.00, 28.85, NULL, 1.0, 0.5492, 968.60, 1002.87, 'ELO-K32-V2', '2026-09-13 10:00:00'
       UNION ALL SELECT (@cx_sm + 21), (@cx_p + 95), (@cx_u + 12), @cx_s_테니스, 1035.01, 1063.86, 28.85, 0.00, 28.85, NULL, 1.0, 0.5492, 968.60, 1002.87, 'ELO-K32-V2', '2026-09-13 10:00:00'
       UNION ALL SELECT (@cx_sm + 21), (@cx_p + 94), (@cx_u + 41), @cx_s_테니스, 969.33, 940.48, -28.85, 0.00, -28.85, NULL, 0.0, 0.4508, 968.60, 1002.87, 'ELO-K32-V2', '2026-09-13 10:00:00'
       UNION ALL SELECT (@cx_sm + 21), (@cx_p + 93), (@cx_u + 44), @cx_s_테니스, 967.86, 939.01, -28.85, 0.00, -28.85, NULL, 0.0, 0.4508, 968.60, 1002.87, 'ELO-K32-V2', '2026-09-13 10:00:00'
       UNION ALL SELECT (@cx_sm + 22), (@cx_p + 97), (@cx_u + 45), @cx_s_배드민턴, 1087.49, 1051.62, -35.87, 0.00, -35.87, NULL, 0.0, 0.5605, 1016.00, 1058.28, 'ELO-K32-V2', '2026-09-14 11:00:00'
       UNION ALL SELECT (@cx_sm + 22), (@cx_p + 98), (@cx_u + 46), @cx_s_배드민턴, 1029.06, 993.19, -35.87, 0.00, -35.87, NULL, 0.0, 0.5605, 1016.00, 1058.28, 'ELO-K32-V2', '2026-09-14 11:00:00'
       UNION ALL SELECT (@cx_sm + 22), (@cx_p + 100), (@cx_u + 59), @cx_s_배드민턴, 1032.00, 1067.87, 35.87, 0.00, 35.87, NULL, 1.0, 0.4395, 1016.00, 1058.28, 'ELO-K32-V2', '2026-09-14 11:00:00'
       UNION ALL SELECT (@cx_sm + 22), (@cx_p + 99), (@cx_u + 62), @cx_s_배드민턴, 1000.00, 1035.87, 35.87, 0.00, 35.87, NULL, 1.0, 0.4395, 1016.00, 1058.28, 'ELO-K32-V2', '2026-09-14 11:00:00'
       UNION ALL SELECT (@cx_sm + 23), (@cx_p + 102), (@cx_u + 23), @cx_s_테니스, 1033.40, 1030.06, -3.34, 0.00, -3.34, NULL, 0.5, 0.5522, 1034.21, 997.80, 'ELO-K32-V2', '2026-09-15 21:30:00'
       UNION ALL SELECT (@cx_sm + 23), (@cx_p + 101), (@cx_u + 25), @cx_s_테니스, 1035.01, 1031.67, -3.34, 0.00, -3.34, NULL, 0.5, 0.5522, 1034.21, 997.80, 'ELO-K32-V2', '2026-09-15 21:30:00'
       UNION ALL SELECT (@cx_sm + 23), (@cx_p + 104), (@cx_u + 27), @cx_s_테니스, 1030.60, 1033.94, 3.34, 0.00, 3.34, NULL, 0.5, 0.4478, 1034.21, 997.80, 'ELO-K32-V2', '2026-09-15 21:30:00'
       UNION ALL SELECT (@cx_sm + 23), (@cx_p + 103), (@cx_u + 29), @cx_s_테니스, 964.99, 968.33, 3.34, 0.00, 3.34, NULL, 0.5, 0.4478, 1034.21, 997.80, 'ELO-K32-V2', '2026-09-15 21:30:00'
       UNION ALL SELECT (@cx_sm + 24), (@cx_p + 108), (@cx_u + 15), @cx_s_배드민턴, 967.86, 943.28, -24.58, 0.00, -24.58, NULL, 0.0, 0.3841, 967.93, 1049.94, 'ELO-K32-V2', '2026-09-17 21:00:00'
       UNION ALL SELECT (@cx_sm + 24), (@cx_p + 107), (@cx_u + 18), @cx_s_배드민턴, 968.00, 943.42, -24.58, 0.00, -24.58, NULL, 0.0, 0.3841, 967.93, 1049.94, 'ELO-K32-V2', '2026-09-17 21:00:00'
       UNION ALL SELECT (@cx_sm + 24), (@cx_p + 105), (@cx_u + 59), @cx_s_배드민턴, 1067.87, 1092.45, 24.58, 0.00, 24.58, NULL, 1.0, 0.6159, 967.93, 1049.94, 'ELO-K32-V2', '2026-09-17 21:00:00'
       UNION ALL SELECT (@cx_sm + 24), (@cx_p + 106), (@cx_u + 61), @cx_s_배드민턴, 1032.00, 1056.58, 24.58, 0.00, 24.58, NULL, 1.0, 0.6159, 967.93, 1049.94, 'ELO-K32-V2', '2026-09-17 21:00:00'
       UNION ALL SELECT (@cx_sm + 25), (@cx_p + 115), (@cx_u + 1), @cx_s_농구, 937.47, 946.03, 8.56, 0.00, 8.56, NULL, 0.5, 0.3663, 1047.95, 952.74, 'ELO-K32-V2', '2026-09-20 12:00:00'
       UNION ALL SELECT (@cx_sm + 25), (@cx_p + 116), (@cx_u + 2), @cx_s_농구, 937.47, 946.03, 8.56, 0.00, 8.56, NULL, 0.5, 0.3663, 1047.95, 952.74, 'ELO-K32-V2', '2026-09-20 12:00:00'
       UNION ALL SELECT (@cx_sm + 25), (@cx_p + 113), (@cx_u + 5), @cx_s_농구, 968.00, 976.56, 8.56, 0.00, 8.56, NULL, 0.5, 0.3663, 1047.95, 952.74, 'ELO-K32-V2', '2026-09-20 12:00:00'
       UNION ALL SELECT (@cx_sm + 25), (@cx_p + 114), (@cx_u + 6), @cx_s_농구, 968.00, 976.56, 8.56, 0.00, 8.56, NULL, 0.5, 0.3663, 1047.95, 952.74, 'ELO-K32-V2', '2026-09-20 12:00:00'
       UNION ALL SELECT (@cx_sm + 25), (@cx_p + 110), (@cx_u + 31), @cx_s_농구, 1063.93, 1055.37, -8.56, 0.00, -8.56, NULL, 0.5, 0.6337, 1047.95, 952.74, 'ELO-K32-V2', '2026-09-20 12:00:00'
       UNION ALL SELECT (@cx_sm + 25), (@cx_p + 111), (@cx_u + 32), @cx_s_농구, 1063.93, 1055.37, -8.56, 0.00, -8.56, NULL, 0.5, 0.6337, 1047.95, 952.74, 'ELO-K32-V2', '2026-09-20 12:00:00'
       UNION ALL SELECT (@cx_sm + 25), (@cx_p + 109), (@cx_u + 34), @cx_s_농구, 1032.00, 1023.44, -8.56, 0.00, -8.56, NULL, 0.5, 0.6337, 1047.95, 952.74, 'ELO-K32-V2', '2026-09-20 12:00:00'
       UNION ALL SELECT (@cx_sm + 25), (@cx_p + 112), (@cx_u + 36), @cx_s_농구, 1031.93, 1023.37, -8.56, 0.00, -8.56, NULL, 0.5, 0.6337, 1047.95, 952.74, 'ELO-K32-V2', '2026-09-20 12:00:00'
       UNION ALL SELECT (@cx_sm + 26), (@cx_p + 122), (@cx_u + 1), @cx_s_농구, 946.03, 949.81, 3.78, 0.00, 3.78, NULL, 0.5, 0.4410, 998.95, 957.75, 'ELO-K32-V2', '2026-09-26 21:00:00'
       UNION ALL SELECT (@cx_sm + 26), (@cx_p + 121), (@cx_u + 2), @cx_s_농구, 946.03, 949.81, 3.78, 0.00, 3.78, NULL, 0.5, 0.4410, 998.95, 957.75, 'ELO-K32-V2', '2026-09-26 21:00:00'
       UNION ALL SELECT (@cx_sm + 26), (@cx_p + 123), (@cx_u + 3), @cx_s_농구, 969.47, 973.25, 3.78, 0.00, 3.78, NULL, 0.5, 0.4410, 998.95, 957.75, 'ELO-K32-V2', '2026-09-26 21:00:00'
       UNION ALL SELECT (@cx_sm + 26), (@cx_p + 124), (@cx_u + 4), @cx_s_농구, 969.47, 973.25, 3.78, 0.00, 3.78, NULL, 0.5, 0.4410, 998.95, 957.75, 'ELO-K32-V2', '2026-09-26 21:00:00'
       UNION ALL SELECT (@cx_sm + 26), (@cx_p + 119), (@cx_u + 49), @cx_s_농구, 998.60, 994.82, -3.78, 0.00, -3.78, NULL, 0.5, 0.5590, 998.95, 957.75, 'ELO-K32-V2', '2026-09-26 21:00:00'
       UNION ALL SELECT (@cx_sm + 26), (@cx_p + 117), (@cx_u + 50), @cx_s_농구, 998.60, 994.82, -3.78, 0.00, -3.78, NULL, 0.5, 0.5590, 998.95, 957.75, 'ELO-K32-V2', '2026-09-26 21:00:00'
       UNION ALL SELECT (@cx_sm + 26), (@cx_p + 118), (@cx_u + 53), @cx_s_농구, 1030.53, 1026.75, -3.78, 0.00, -3.78, NULL, 0.5, 0.5590, 998.95, 957.75, 'ELO-K32-V2', '2026-09-26 21:00:00'
       UNION ALL SELECT (@cx_sm + 26), (@cx_p + 120), (@cx_u + 54), @cx_s_농구, 968.07, 964.29, -3.78, 0.00, -3.78, NULL, 0.5, 0.5590, 998.95, 957.75, 'ELO-K32-V2', '2026-09-26 21:00:00'
       UNION ALL SELECT (@cx_sm + 27), (@cx_p + 127), (@cx_u + 19), @cx_s_배드민턴, 1000.14, 1025.32, 25.18, 0.00, 25.18, NULL, 1.0, 0.6065, 1016.88, 941.73, 'ELO-K32-V2', '2026-09-26 22:00:00'
       UNION ALL SELECT (@cx_sm + 27), (@cx_p + 128), (@cx_u + 21), @cx_s_배드민턴, 1033.61, 1058.79, 25.18, 0.00, 25.18, NULL, 1.0, 0.6065, 1016.88, 941.73, 'ELO-K32-V2', '2026-09-26 22:00:00'
       UNION ALL SELECT (@cx_sm + 27), (@cx_p + 125), (@cx_u + 37), @cx_s_배드민턴, 912.51, 887.33, -25.18, 0.00, -25.18, NULL, 0.0, 0.3935, 1016.88, 941.73, 'ELO-K32-V2', '2026-09-26 22:00:00'
       UNION ALL SELECT (@cx_sm + 27), (@cx_p + 126), (@cx_u + 40), @cx_s_배드민턴, 970.94, 945.76, -25.18, 0.00, -25.18, NULL, 0.0, 0.3935, 1016.88, 941.73, 'ELO-K32-V2', '2026-09-26 22:00:00'
       UNION ALL SELECT (@cx_sm + 28), (@cx_p + 130), (@cx_u + 23), @cx_s_테니스, 1030.06, 1031.53, 1.47, 0.00, 1.47, NULL, 0.5, 0.4771, 1030.96, 1046.88, 'ELO-K32-V2', '2026-09-29 21:30:00'
       UNION ALL SELECT (@cx_sm + 28), (@cx_p + 129), (@cx_u + 24), @cx_s_테니스, 1031.86, 1033.33, 1.47, 0.00, 1.47, NULL, 0.5, 0.4771, 1030.96, 1046.88, 'ELO-K32-V2', '2026-09-29 21:30:00'
       UNION ALL SELECT (@cx_sm + 28), (@cx_p + 132), (@cx_u + 55), @cx_s_테니스, 1061.47, 1060.00, -1.47, 0.00, -1.47, NULL, 0.5, 0.5229, 1030.96, 1046.88, 'ELO-K32-V2', '2026-09-29 21:30:00'
       UNION ALL SELECT (@cx_sm + 28), (@cx_p + 131), (@cx_u + 56), @cx_s_테니스, 1032.28, 1030.81, -1.47, 0.00, -1.47, NULL, 0.5, 0.5229, 1030.96, 1046.88, 'ELO-K32-V2', '2026-09-29 21:30:00'
       UNION ALL SELECT (@cx_sm + 29), (@cx_p + 135), (@cx_u + 27), @cx_s_테니스, 1033.94, 1058.80, 24.86, 0.00, 24.86, NULL, 1.0, 0.6115, 954.24, 1033.04, 'ELO-K32-V2', '2026-10-04 10:00:00'
       UNION ALL SELECT (@cx_sm + 29), (@cx_p + 136), (@cx_u + 28), @cx_s_테니스, 1032.14, 1057.00, 24.86, 0.00, 24.86, NULL, 1.0, 0.6115, 954.24, 1033.04, 'ELO-K32-V2', '2026-10-04 10:00:00'
       UNION ALL SELECT (@cx_sm + 29), (@cx_p + 134), (@cx_u + 41), @cx_s_테니스, 940.48, 915.62, -24.86, 0.00, -24.86, NULL, 0.0, 0.3885, 954.24, 1033.04, 'ELO-K32-V2', '2026-10-04 10:00:00'
       UNION ALL SELECT (@cx_sm + 29), (@cx_p + 133), (@cx_u + 43), @cx_s_테니스, 968.00, 943.14, -24.86, 0.00, -24.86, NULL, 0.0, 0.3885, 954.24, 1033.04, 'ELO-K32-V2', '2026-10-04 10:00:00'
       UNION ALL SELECT (@cx_sm + 30), (@cx_p + 139), (@cx_u + 19), @cx_s_배드민턴, 1025.32, 1063.52, 38.20, 0.00, 38.20, NULL, 1.0, 0.4031, 1064.16, 995.93, 'ELO-K32-V2', '2026-10-04 11:00:00'
       UNION ALL SELECT (@cx_sm + 30), (@cx_p + 140), (@cx_u + 22), @cx_s_배드민턴, 966.53, 1004.73, 38.20, 0.00, 38.20, NULL, 1.0, 0.4031, 1064.16, 995.93, 'ELO-K32-V2', '2026-10-04 11:00:00'
       UNION ALL SELECT (@cx_sm + 30), (@cx_p + 138), (@cx_u + 59), @cx_s_배드민턴, 1092.45, 1054.25, -38.20, 0.00, -38.20, NULL, 0.0, 0.5969, 1064.16, 995.93, 'ELO-K32-V2', '2026-10-04 11:00:00'
       UNION ALL SELECT (@cx_sm + 30), (@cx_p + 137), (@cx_u + 62), @cx_s_배드민턴, 1035.87, 997.67, -38.20, 0.00, -38.20, NULL, 0.0, 0.5969, 1064.16, 995.93, 'ELO-K32-V2', '2026-10-04 11:00:00'
       UNION ALL SELECT (@cx_sm + 31), (@cx_p + 141), (@cx_u + 15), @cx_s_배드민턴, 943.28, 972.78, 29.50, 0.00, 29.50, NULL, 1.0, 0.5390, 927.67, 954.84, 'ELO-K32-V2', '2026-10-05 12:00:00'
       UNION ALL SELECT (@cx_sm + 31), (@cx_p + 142), (@cx_u + 17), @cx_s_배드민턴, 966.39, 995.89, 29.50, 0.00, 29.50, NULL, 1.0, 0.5390, 927.67, 954.84, 'ELO-K32-V2', '2026-10-05 12:00:00'
       UNION ALL SELECT (@cx_sm + 31), (@cx_p + 144), (@cx_u + 37), @cx_s_배드민턴, 887.33, 857.83, -29.50, 0.00, -29.50, NULL, 0.0, 0.4610, 927.67, 954.84, 'ELO-K32-V2', '2026-10-05 12:00:00'
       UNION ALL SELECT (@cx_sm + 31), (@cx_p + 143), (@cx_u + 38), @cx_s_배드민턴, 968.00, 938.50, -29.50, 0.00, -29.50, NULL, 0.0, 0.4610, 927.67, 954.84, 'ELO-K32-V2', '2026-10-05 12:00:00'
       UNION ALL SELECT (@cx_sm + 32), (@cx_p + 145), (@cx_u + 7), @cx_s_테니스, 934.47, 969.44, 34.97, 0.00, 34.97, NULL, 1.0, 0.4536, 983.65, 951.31, 'ELO-K32-V2', '2026-10-09 23:00:00'
       UNION ALL SELECT (@cx_sm + 32), (@cx_p + 146), (@cx_u + 9), @cx_s_테니스, 968.14, 1003.11, 34.97, 0.00, 34.97, NULL, 1.0, 0.4536, 983.65, 951.31, 'ELO-K32-V2', '2026-10-09 23:00:00'
       UNION ALL SELECT (@cx_sm + 32), (@cx_p + 148), (@cx_u + 11), @cx_s_테니스, 999.58, 964.61, -34.97, 0.00, -34.97, NULL, 0.0, 0.5464, 983.65, 951.31, 'ELO-K32-V2', '2026-10-09 23:00:00'
       UNION ALL SELECT (@cx_sm + 32), (@cx_p + 147), (@cx_u + 14), @cx_s_테니스, 967.72, 932.75, -34.97, 0.00, -34.97, NULL, 0.0, 0.5464, 983.65, 951.31, 'ELO-K32-V2', '2026-10-09 23:00:00'
       UNION ALL SELECT (@cx_sm + 33), (@cx_p + 152), (@cx_u + 31), @cx_s_농구, 1055.37, 1083.10, 27.73, 0.00, 27.73, NULL, 1.0, 0.5667, 997.06, 1043.67, 'ELO-K32-V2', '2026-10-10 21:00:00'
       UNION ALL SELECT (@cx_sm + 33), (@cx_p + 151), (@cx_u + 32), @cx_s_농구, 1055.37, 1083.10, 27.73, 0.00, 27.73, NULL, 1.0, 0.5667, 997.06, 1043.67, 'ELO-K32-V2', '2026-10-10 21:00:00'
       UNION ALL SELECT (@cx_sm + 33), (@cx_p + 149), (@cx_u + 33), @cx_s_농구, 1032.00, 1059.73, 27.73, 0.00, 27.73, NULL, 1.0, 0.5667, 997.06, 1043.67, 'ELO-K32-V2', '2026-10-10 21:00:00'
       UNION ALL SELECT (@cx_sm + 33), (@cx_p + 150), (@cx_u + 35), @cx_s_농구, 1031.93, 1059.66, 27.73, 0.00, 27.73, NULL, 1.0, 0.5667, 997.06, 1043.67, 'ELO-K32-V2', '2026-10-10 21:00:00'
       UNION ALL SELECT (@cx_sm + 33), (@cx_p + 153), (@cx_u + 49), @cx_s_농구, 994.82, 967.09, -27.73, 0.00, -27.73, NULL, 0.0, 0.4333, 997.06, 1043.67, 'ELO-K32-V2', '2026-10-10 21:00:00'
       UNION ALL SELECT (@cx_sm + 33), (@cx_p + 154), (@cx_u + 50), @cx_s_농구, 994.82, 967.09, -27.73, 0.00, -27.73, NULL, 0.0, 0.4333, 997.06, 1043.67, 'ELO-K32-V2', '2026-10-10 21:00:00'
       UNION ALL SELECT (@cx_sm + 33), (@cx_p + 155), (@cx_u + 51), @cx_s_농구, 1030.53, 1002.80, -27.73, 0.00, -27.73, NULL, 0.0, 0.4333, 997.06, 1043.67, 'ELO-K32-V2', '2026-10-10 21:00:00'
       UNION ALL SELECT (@cx_sm + 33), (@cx_p + 156), (@cx_u + 52), @cx_s_농구, 968.07, 940.34, -27.73, 0.00, -27.73, NULL, 0.0, 0.4333, 997.06, 1043.67, 'ELO-K32-V2', '2026-10-10 21:00:00'
       UNION ALL SELECT (@cx_sm + 34), (@cx_p + 159), (@cx_u + 11), @cx_s_테니스, 964.61, 950.11, -14.50, 0.00, -14.50, NULL, 0.0, 0.4531, 999.03, 966.31, 'ELO-K32-V2', '2026-10-16 21:30:00'
       UNION ALL SELECT (@cx_sm + 34), (@cx_p + 160), (@cx_u + 13), @cx_s_테니스, 968.00, 939.00, -29.00, 0.00, -29.00, NULL, 0.0, 0.4531, 999.03, 966.31, 'ELO-K32-V2', '2026-10-16 21:30:00'
       UNION ALL SELECT (@cx_sm + 34), (@cx_p + 157), (@cx_u + 23), @cx_s_테니스, 1031.53, 1046.03, 14.50, 0.00, 14.50, NULL, 1.0, 0.5469, 999.03, 966.31, 'ELO-K32-V2', '2026-10-16 21:30:00'
       UNION ALL SELECT (@cx_sm + 34), (@cx_p + 158), (@cx_u + 26), @cx_s_테니스, 966.53, 995.53, 29.00, 0.00, 29.00, NULL, 1.0, 0.5469, 999.03, 966.31, 'ELO-K32-V2', '2026-10-16 21:30:00'
       UNION ALL SELECT (@cx_sm + 35), (@cx_p + 163), (@cx_u + 19), @cx_s_배드민턴, 1063.52, 1047.98, -15.54, 0.00, -15.54, NULL, 0.0, 0.4855, 1041.81, 1031.76, 'ELO-K32-V2', '2026-10-16 22:00:00'
       UNION ALL SELECT (@cx_sm + 35), (@cx_p + 164), (@cx_u + 20), @cx_s_배드민턴, 1000.00, 968.93, -31.07, 0.00, -31.07, NULL, 0.0, 0.4855, 1041.81, 1031.76, 'ELO-K32-V2', '2026-10-16 22:00:00'
       UNION ALL SELECT (@cx_sm + 35), (@cx_p + 161), (@cx_u + 45), @cx_s_배드민턴, 1051.62, 1082.69, 31.07, 0.00, 31.07, NULL, 1.0, 0.5145, 1041.81, 1031.76, 'ELO-K32-V2', '2026-10-16 22:00:00'
       UNION ALL SELECT (@cx_sm + 35), (@cx_p + 162), (@cx_u + 48), @cx_s_배드민턴, 1032.00, 1063.07, 31.07, 0.00, 31.07, NULL, 1.0, 0.5145, 1041.81, 1031.76, 'ELO-K32-V2', '2026-10-16 22:00:00'
       UNION ALL SELECT (@cx_sm + 36), (@cx_p + 168), (@cx_u + 31), @cx_s_농구, 1083.10, 1107.25, 24.15, 0.00, 24.15, NULL, 1.0, 0.6226, 975.32, 1062.31, 'ELO-K32-V2', '2026-10-17 21:00:00'
       UNION ALL SELECT (@cx_sm + 36), (@cx_p + 165), (@cx_u + 32), @cx_s_농구, 1083.10, 1107.25, 24.15, 0.00, 24.15, NULL, 1.0, 0.6226, 975.32, 1062.31, 'ELO-K32-V2', '2026-10-17 21:00:00'
       UNION ALL SELECT (@cx_sm + 36), (@cx_p + 167), (@cx_u + 35), @cx_s_농구, 1059.66, 1083.81, 24.15, 0.00, 24.15, NULL, 1.0, 0.6226, 975.32, 1062.31, 'ELO-K32-V2', '2026-10-17 21:00:00'
       UNION ALL SELECT (@cx_sm + 36), (@cx_p + 166), (@cx_u + 36), @cx_s_농구, 1023.37, 1047.52, 24.15, 0.00, 24.15, NULL, 1.0, 0.6226, 975.32, 1062.31, 'ELO-K32-V2', '2026-10-17 21:00:00'
       UNION ALL SELECT (@cx_sm + 36), (@cx_p + 169), (@cx_u + 49), @cx_s_농구, 967.09, 942.94, -24.15, 0.00, -24.15, NULL, 0.0, 0.3774, 975.32, 1062.31, 'ELO-K32-V2', '2026-10-17 21:00:00'
       UNION ALL SELECT (@cx_sm + 36), (@cx_p + 172), (@cx_u + 50), @cx_s_농구, 967.09, 942.94, -24.15, 0.00, -24.15, NULL, 0.0, 0.3774, 975.32, 1062.31, 'ELO-K32-V2', '2026-10-17 21:00:00'
       UNION ALL SELECT (@cx_sm + 36), (@cx_p + 171), (@cx_u + 51), @cx_s_농구, 1002.80, 978.65, -24.15, 0.00, -24.15, NULL, 0.0, 0.3774, 975.32, 1062.31, 'ELO-K32-V2', '2026-10-17 21:00:00'
       UNION ALL SELECT (@cx_sm + 36), (@cx_p + 170), (@cx_u + 54), @cx_s_농구, 964.29, 940.14, -24.15, 0.00, -24.15, NULL, 0.0, 0.3774, 975.32, 1062.31, 'ELO-K32-V2', '2026-10-17 21:00:00'
       UNION ALL SELECT (@cx_sm + 37), (@cx_p + 173), (@cx_u + 7), @cx_s_테니스, 969.44, 985.74, 16.30, 0.00, 16.30, NULL, 1.0, 0.4906, 974.55, 967.99, 'ELO-K32-V2', '2026-10-18 10:00:00'
       UNION ALL SELECT (@cx_sm + 37), (@cx_p + 174), (@cx_u + 10), @cx_s_테니스, 966.53, 999.13, 32.60, 0.00, 32.60, NULL, 1.0, 0.4906, 974.55, 967.99, 'ELO-K32-V2', '2026-10-18 10:00:00'
       UNION ALL SELECT (@cx_sm + 37), (@cx_p + 175), (@cx_u + 41), @cx_s_테니스, 915.62, 899.32, -16.30, 0.00, -16.30, NULL, 0.0, 0.5094, 974.55, 967.99, 'ELO-K32-V2', '2026-10-18 10:00:00'
       UNION ALL SELECT (@cx_sm + 37), (@cx_p + 176), (@cx_u + 42), @cx_s_테니스, 1033.47, 1000.87, -32.60, 0.00, -32.60, NULL, 0.0, 0.5094, 974.55, 967.99, 'ELO-K32-V2', '2026-10-18 10:00:00'
       UNION ALL SELECT (@cx_sm + 38), (@cx_p + 180), (@cx_u + 15), @cx_s_배드민턴, 972.78, 957.88, -14.90, 0.00, -14.90, NULL, 0.0, 0.4655, 1003.13, 1027.13, 'ELO-K32-V2', '2026-10-18 21:00:00'
       UNION ALL SELECT (@cx_sm + 38), (@cx_p + 179), (@cx_u + 16), @cx_s_배드민턴, 1033.47, 1003.68, -29.79, 0.00, -29.79, NULL, 0.0, 0.4655, 1003.13, 1027.13, 'ELO-K32-V2', '2026-10-18 21:00:00'
       UNION ALL SELECT (@cx_sm + 38), (@cx_p + 178), (@cx_u + 59), @cx_s_배드민턴, 1054.25, 1069.15, 14.90, 0.00, 14.90, NULL, 1.0, 0.5345, 1003.13, 1027.13, 'ELO-K32-V2', '2026-10-18 21:00:00'
       UNION ALL SELECT (@cx_sm + 38), (@cx_p + 177), (@cx_u + 60), @cx_s_배드민턴, 1000.00, 1029.79, 29.79, 0.00, 29.79, NULL, 1.0, 0.5345, 1003.13, 1027.13, 'ELO-K32-V2', '2026-10-18 21:00:00'
       UNION ALL SELECT (@cx_sm + 39), (@cx_p + 187), (@cx_u + 1), @cx_s_농구, 949.81, 917.97, -31.84, 0.00, -31.84, NULL, 0.0, 0.4975, 963.24, 961.53, 'ELO-K32-V2', '2026-10-24 21:00:00'
       UNION ALL SELECT (@cx_sm + 39), (@cx_p + 185), (@cx_u + 2), @cx_s_농구, 949.81, 917.97, -31.84, 0.00, -31.84, NULL, 0.0, 0.4975, 963.24, 961.53, 'ELO-K32-V2', '2026-10-24 21:00:00'
       UNION ALL SELECT (@cx_sm + 39), (@cx_p + 186), (@cx_u + 3), @cx_s_농구, 973.25, 941.41, -31.84, 0.00, -31.84, NULL, 0.0, 0.4975, 963.24, 961.53, 'ELO-K32-V2', '2026-10-24 21:00:00'
       UNION ALL SELECT (@cx_sm + 39), (@cx_p + 188), (@cx_u + 4), @cx_s_농구, 973.25, 941.41, -31.84, 0.00, -31.84, NULL, 0.0, 0.4975, 963.24, 961.53, 'ELO-K32-V2', '2026-10-24 21:00:00'
       UNION ALL SELECT (@cx_sm + 39), (@cx_p + 181), (@cx_u + 49), @cx_s_농구, 942.94, 958.86, 15.92, 0.00, 15.92, NULL, 1.0, 0.5025, 963.24, 961.53, 'ELO-K32-V2', '2026-10-24 21:00:00'
       UNION ALL SELECT (@cx_sm + 39), (@cx_p + 182), (@cx_u + 50), @cx_s_농구, 942.94, 958.86, 15.92, 0.00, 15.92, NULL, 1.0, 0.5025, 963.24, 961.53, 'ELO-K32-V2', '2026-10-24 21:00:00'
       UNION ALL SELECT (@cx_sm + 39), (@cx_p + 183), (@cx_u + 52), @cx_s_농구, 940.34, 972.18, 31.84, 0.00, 31.84, NULL, 1.0, 0.5025, 963.24, 961.53, 'ELO-K32-V2', '2026-10-24 21:00:00'
       UNION ALL SELECT (@cx_sm + 39), (@cx_p + 184), (@cx_u + 53), @cx_s_농구, 1026.75, 1058.59, 31.84, 0.00, 31.84, NULL, 1.0, 0.5025, 963.24, 961.53, 'ELO-K32-V2', '2026-10-24 21:00:00'
       UNION ALL SELECT (@cx_sm + 40), (@cx_p + 190), (@cx_u + 27), @cx_s_테니스, 1058.80, 1058.79, -0.01, 0.00, -0.01, NULL, 0.5, 0.5002, 1046.00, 1046.14, 'ELO-K32-V2', '2026-10-29 22:00:00'
       UNION ALL SELECT (@cx_sm + 40), (@cx_p + 189), (@cx_u + 30), @cx_s_테니스, 1033.47, 1033.46, -0.01, 0.00, -0.01, NULL, 0.5, 0.5002, 1046.00, 1046.14, 'ELO-K32-V2', '2026-10-29 22:00:00'
       UNION ALL SELECT (@cx_sm + 40), (@cx_p + 191), (@cx_u + 55), @cx_s_테니스, 1060.00, 1060.01, 0.01, 0.00, 0.01, NULL, 0.5, 0.4998, 1046.00, 1046.14, 'ELO-K32-V2', '2026-10-29 22:00:00'
       UNION ALL SELECT (@cx_sm + 40), (@cx_p + 192), (@cx_u + 58), @cx_s_테니스, 1032.00, 1032.01, 0.01, 0.00, 0.01, NULL, 0.5, 0.4998, 1046.00, 1046.14, 'ELO-K32-V2', '2026-10-29 22:00:00'
       UNION ALL SELECT (@cx_sm + 41), (@cx_p + 196), (@cx_u + 37), @cx_s_배드민턴, 857.83, 879.90, 22.07, 0.00, 22.07, NULL, 1.0, 0.3102, 1054.56, 915.70, 'ELO-K32-V2', '2026-11-04 22:00:00'
       UNION ALL SELECT (@cx_sm + 41), (@cx_p + 195), (@cx_u + 39), @cx_s_배드민턴, 973.57, 1017.72, 44.15, 0.00, 44.15, NULL, 1.0, 0.3102, 1054.56, 915.70, 'ELO-K32-V2', '2026-11-04 22:00:00'
       UNION ALL SELECT (@cx_sm + 41), (@cx_p + 194), (@cx_u + 45), @cx_s_배드민턴, 1082.69, 1060.62, -22.07, 0.00, -22.07, NULL, 0.0, 0.6898, 1054.56, 915.70, 'ELO-K32-V2', '2026-11-04 22:00:00'
       UNION ALL SELECT (@cx_sm + 41), (@cx_p + 193), (@cx_u + 47), @cx_s_배드민턴, 1026.43, 982.28, -44.15, 0.00, -44.15, NULL, 0.0, 0.6898, 1054.56, 915.70, 'ELO-K32-V2', '2026-11-04 22:00:00'
       UNION ALL SELECT (@cx_sm + 42), (@cx_p + 202), (@cx_u + 1), @cx_s_농구, 917.97, 939.58, 21.61, 0.00, 21.61, NULL, 1.0, 0.3248, 1074.42, 947.27, 'ELO-K32-V2', '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_sm + 42), (@cx_p + 203), (@cx_u + 2), @cx_s_농구, 917.97, 939.58, 21.61, 0.00, 21.61, NULL, 1.0, 0.3248, 1074.42, 947.27, 'ELO-K32-V2', '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_sm + 42), (@cx_p + 201), (@cx_u + 5), @cx_s_농구, 976.56, 1019.77, 43.21, 0.00, 43.21, NULL, 1.0, 0.3248, 1074.42, 947.27, 'ELO-K32-V2', '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_sm + 42), (@cx_p + 204), (@cx_u + 6), @cx_s_농구, 976.56, 1019.77, 43.21, 0.00, 43.21, NULL, 1.0, 0.3248, 1074.42, 947.27, 'ELO-K32-V2', '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_sm + 42), (@cx_p + 200), (@cx_u + 31), @cx_s_농구, 1107.25, 1085.64, -21.61, 0.00, -21.61, NULL, 0.0, 0.6752, 1074.42, 947.27, 'ELO-K32-V2', '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_sm + 42), (@cx_p + 197), (@cx_u + 32), @cx_s_농구, 1107.25, 1085.64, -21.61, 0.00, -21.61, NULL, 0.0, 0.6752, 1074.42, 947.27, 'ELO-K32-V2', '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_sm + 42), (@cx_p + 198), (@cx_u + 33), @cx_s_농구, 1059.73, 1016.52, -43.21, 0.00, -43.21, NULL, 0.0, 0.6752, 1074.42, 947.27, 'ELO-K32-V2', '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_sm + 42), (@cx_p + 199), (@cx_u + 34), @cx_s_농구, 1023.44, 980.23, -43.21, 0.00, -43.21, NULL, 0.0, 0.6752, 1074.42, 947.27, 'ELO-K32-V2', '2026-11-08 12:00:00'
) v
 WHERE @cx_ready = 1;

COMMIT;

-- 확인 1: 넣은 행 수
SELECT (SELECT COUNT(*) FROM app_user u WHERE u.email LIKE 'club-user-___@spoit.test') AS new_users,
       (SELECT COUNT(*) FROM club_member m JOIN app_user u ON u.user_id = m.user_id WHERE u.email LIKE 'club-user-___@spoit.test' AND m.left_at IS NULL) AS new_members,
       (SELECT COUNT(DISTINCT ma.match_id) FROM match_application ma JOIN app_user u ON u.user_id = ma.applicant_user_id
          JOIN social_match sm ON sm.match_id = ma.match_id WHERE u.email LIKE 'club-user-___@spoit.test' AND ma.status = '확정' AND sm.status = '종료') AS past_matches,
       (SELECT COUNT(*) FROM sport_rating_history h JOIN app_user u ON u.user_id = h.user_id WHERE u.email LIKE 'club-user-___@spoit.test') AS history_rows,
       (SELECT COUNT(*) FROM match_application WHERE receipt_no IN ('RCPT-20261112-U76B7L9L', 'RCPT-20261112-J9MV9XJC', 'RCPT-20261112-NC5DPKCW', 'RCPT-20261112-3MLB36WA', 'RCPT-20261112-M9NS8AZL', 'RCPT-20261112-P5YLMHHY', 'RCPT-20261112-CB4R5X55', 'RCPT-20261112-3A2YSAGD', 'RCPT-20261112-W4V6KZD3', 'RCPT-20261112-T2HDMV9U', 'RCPT-20261112-7FBXEK4A', 'RCPT-20261112-CJYPRP32', 'RCPT-20261112-2JWEL8Y5', 'RCPT-20261112-CJA2AHUQ', 'RCPT-20261112-KFQM9BHY', 'RCPT-20261112-PTDGEXPV', 'RCPT-20261112-NU2QWSC5', 'RCPT-20261112-2UD5YFAD', 'RCPT-20261112-XXK4WASH', 'RCPT-20261112-7RR3WM5A', 'RCPT-20261112-PCY88EFS', 'RCPT-20261112-C6KWNWXE', 'RCPT-20261112-SF3Y6TKT', 'RCPT-20261112-XSSYRCKD', 'RCPT-20261112-5TPB4LK3', 'RCPT-20261112-MTQ7ZTMH', 'RCPT-20261112-LZ39CUH7', 'RCPT-20261112-RDNEWRJM', 'RCPT-20261112-XX3KNFZX', 'RCPT-20261112-9KN64VCE', 'RCPT-20261112-3T33VSZX', 'RCPT-20261112-M8ERZWGF', 'RCPT-20261112-7FMZT4FC', 'RCPT-20261112-QHD4USRA') AND status = '대기' AND group_application_id IS NOT NULL) AS team_applications,
       (SELECT COUNT(*) FROM club WHERE club_name IN ('주말 풋살 클럽', '노원 농구회', '송파 셔틀콕', '마포 테니스 클럽', '서초 나이트 테니스', '성동 셔틀콕 모임', '광진 배드민턴회', '서초 테니스회', '영등포 테니스 클럽', '송파 농구 동호회', '송파 배드민턴회', '은평 풋살 클럽', '광진 테니스회', '강서 배드민턴회', '영등포 농구클럽', '노원 풋살회', '관악 족구회', '강서 테니스회', '송파 배드민턴', '잠실 농구회', '뚝섬 FC', '망원 풋살 크루', '잠실 풋살 클럽', '반포 농구단', '광나루 바스켓', '한강 족구회', '뚝섬 셔틀콕', '마포 랠리 테니스') AND deleted_at IS NULL AND intro LIKE '%[활동 안내]%') AS new_intros;
-- 기대값: new_users 62, new_members 62, past_matches 42, history_rows 204, team_applications 34, new_intros 28

-- 확인 2: 새 회원의 레이팅이 이력과 맞는지 (sport_rating = 마지막 이력, 경기 수 = 이력 행 수). 0이면 맞다.
SELECT COUNT(*) AS rating_mismatches
  FROM app_user u JOIN sport_rating sr ON sr.user_id = u.user_id
 WHERE u.email LIKE 'club-user-___@spoit.test'
   AND (sr.rated_match_count <> (SELECT COUNT(*) FROM sport_rating_history h WHERE h.user_id = u.user_id AND h.sport_id = sr.sport_id)
        OR sr.current_mmr <> COALESCE((SELECT h.mmr_after FROM sport_rating_history h WHERE h.user_id = u.user_id AND h.sport_id = sr.sport_id
                                        ORDER BY h.calculated_at DESC, h.history_id DESC LIMIT 1), 1000));

-- 확인 3: 동호회별 인원과 팀 등급(배치를 마친 동호회원 평균, 기존 회원 포함)
SELECT c.club_name, c.recruit_status, CONCAT(COUNT(m.club_member_id), '/', c.max_members) AS members,
       (SELECT ROUND(AVG(sr.current_mmr)) FROM club_member am JOIN sport_rating sr ON sr.user_id = am.user_id AND sr.sport_id = c.sport_id
         WHERE am.club_id = c.club_id AND am.left_at IS NULL AND sr.rated_match_count >= 5) AS average_mmr
  FROM club c JOIN club_member m ON m.club_id = c.club_id AND m.left_at IS NULL
 WHERE c.club_name IN ('주말 풋살 클럽', '노원 농구회', '송파 셔틀콕', '마포 테니스 클럽', '서초 나이트 테니스', '성동 셔틀콕 모임', '광진 배드민턴회', '서초 테니스회', '영등포 테니스 클럽', '송파 농구 동호회', '송파 배드민턴회', '은평 풋살 클럽', '광진 테니스회', '강서 배드민턴회', '영등포 농구클럽', '노원 풋살회', '관악 족구회', '강서 테니스회', '송파 배드민턴', '잠실 농구회', '뚝섬 FC', '망원 풋살 크루', '잠실 풋살 클럽', '반포 농구단', '광나루 바스켓', '한강 족구회', '뚝섬 셔틀콕', '마포 랠리 테니스') AND c.deleted_at IS NULL
 GROUP BY c.club_id ORDER BY average_mmr DESC;
