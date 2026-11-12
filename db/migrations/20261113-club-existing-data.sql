-- =====================================================================
-- 기존 동호회 데이터: seed·HY 님 데이터의 동호회 24곳에 새 회원 118명, 지난 동호회 경기 71개(레이팅 이력 426행),
--                   예정 매칭방 19개와 팀 신청 49행을 넣고, 28곳의 소개 글을 다시 쓴다.
-- 이 파일은 scripts/club-demo-data.py가 만든다. 직접 고치지 말고 스크립트를 고쳐 다시 만든다.
-- 대상: 20261112-club-demo-data.sql까지 적용한 DB (로컬·운영)
-- 내용
--   * 기존 회원(seed 계정, demo-NNN)은 바꾸지 않는다. 새 회원(club-user-NNN@spoit.test, 비밀번호 spoit1234)만 동호회원으로 넣고,
--     지난 경기는 새 회원끼리 같은 종목 동호회와 치른 것이다(축구+풋살 5:5, 농구 4:4, 족구 3:3, 배드민턴·테니스 복식 2:2).
--   * 지난 경기는 2026-08~11월 초, 홈 동호회의 구장·활동 시간. 팀 신청 확정 → 참가자 → 두 팀장 결과 제출 → 결과 마감(종료 24시간 뒤)에
--     평가 없이 레이팅 반영까지 끝난 종료 상태다. 레이팅은 RatingCalculator와 같은 식으로 1000부터 시간순 계산했다.
--   * 예정 일정: HY 님 데이터에 예정 매칭방이 없는 동호회마다 11/24~12/6 활동 시간에 매칭방 1개와 팀 신청(대기).
--   * 소개 글만 바꾸는 곳: 주말 풋살 클럽, 송파 셔틀콕, 노원 풋살회, 잠실 농구회(seed 가입 신청·매치에 쓰이거나 다른 파트 테스트 계정이 있음).
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
SET @cx_f10 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '은평구' AND f.name = '은평 풋살아레나' AND f.deleted_at IS NULL);
SET @cx_f10_mgr = (SELECT MIN(user_id) FROM facility_manager WHERE facility_id = @cx_f10);
SET @cx_f11 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '광진구' AND f.name = '어린이대공원 테니스장 A코트' AND f.deleted_at IS NULL);
SET @cx_f11_mgr = (SELECT MIN(user_id) FROM facility_manager WHERE facility_id = @cx_f11);
SET @cx_f12 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '강서구' AND f.name = '강서 배드민턴장' AND f.deleted_at IS NULL);
SET @cx_f12_mgr = (SELECT MIN(user_id) FROM facility_manager WHERE facility_id = @cx_f12);
SET @cx_f13 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '영등포구' AND f.name = '영등포 농구센터' AND f.deleted_at IS NULL);
SET @cx_f13_mgr = (SELECT MIN(user_id) FROM facility_manager WHERE facility_id = @cx_f13);
SET @cx_f14 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '관악구' AND f.name = '관악 족구장' AND f.deleted_at IS NULL);
SET @cx_f14_mgr = (SELECT MIN(user_id) FROM facility_manager WHERE facility_id = @cx_f14);
SET @cx_f15 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '강서구' AND f.name = '강서 테니스장' AND f.deleted_at IS NULL);
SET @cx_f15_mgr = (SELECT MIN(user_id) FROM facility_manager WHERE facility_id = @cx_f15);
SET @cx_f16 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '송파구' AND f.name = '송파 배드민턴코트' AND f.deleted_at IS NULL);
SET @cx_f16_mgr = (SELECT MIN(user_id) FROM facility_manager WHERE facility_id = @cx_f16);
SET @cx_f17 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '광진구' AND f.name = '뚝섬한강공원 축구장 2' AND f.deleted_at IS NULL);
SET @cx_f17_mgr = (SELECT MIN(user_id) FROM facility_manager WHERE facility_id = @cx_f17);
SET @cx_f18 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '마포구' AND f.name = '망원유수지 풋살장' AND f.deleted_at IS NULL);
SET @cx_f18_mgr = (SELECT MIN(user_id) FROM facility_manager WHERE facility_id = @cx_f18);
SET @cx_f19 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '송파구' AND f.name = '잠실종합운동장 제2풋살경기장' AND f.deleted_at IS NULL);
SET @cx_f19_mgr = (SELECT MIN(user_id) FROM facility_manager WHERE facility_id = @cx_f19);
SET @cx_f20 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '서초구' AND f.name = '반포한강공원 농구장(1,2)' AND f.deleted_at IS NULL);
SET @cx_f20_mgr = (SELECT MIN(user_id) FROM facility_manager WHERE facility_id = @cx_f20);
SET @cx_f21 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '강동구' AND f.name = '광나루한강공원 농구장 1, 2' AND f.deleted_at IS NULL);
SET @cx_f21_mgr = (SELECT MIN(user_id) FROM facility_manager WHERE facility_id = @cx_f21);
SET @cx_f22 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '광진구' AND f.name = '뚝섬한강공원 족구장 1' AND f.deleted_at IS NULL);
SET @cx_f22_mgr = (SELECT MIN(user_id) FROM facility_manager WHERE facility_id = @cx_f22);
SET @cx_f23 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '광진구' AND f.name = '뚝섬한강공원 배드민턴장 2' AND f.deleted_at IS NULL);
SET @cx_f23_mgr = (SELECT MIN(user_id) FROM facility_manager WHERE facility_id = @cx_f23);
SET @cx_f24 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '마포구' AND f.name = '망원나들목 테니스장' AND f.deleted_at IS NULL);
SET @cx_f24_mgr = (SELECT MIN(user_id) FROM facility_manager WHERE facility_id = @cx_f24);

-- 사전 확인. 출력되는 값이 모두 1이어야 진행한다. already_applied가 1이면 이미 적용된 DB라 아무것도 넣지 않는다.
SELECT (@cx_s_축구 IS NOT NULL AND @cx_s_농구 IS NOT NULL AND @cx_s_배드민턴 IS NOT NULL AND @cx_s_테니스 IS NOT NULL AND @cx_s_족구 IS NOT NULL),
       ((@cx_r_강동구 IS NOT NULL) + (@cx_r_강서구 IS NOT NULL) + (@cx_r_관악구 IS NOT NULL) + (@cx_r_광진구 IS NOT NULL) + (@cx_r_노원구 IS NOT NULL) + (@cx_r_마포구 IS NOT NULL) + (@cx_r_서초구 IS NOT NULL) + (@cx_r_성동구 IS NOT NULL) + (@cx_r_송파구 IS NOT NULL) + (@cx_r_영등포구 IS NOT NULL) + (@cx_r_은평구 IS NOT NULL)) = 11,
       ((@cx_c1 IS NOT NULL) + (@cx_c2 IS NOT NULL) + (@cx_c3 IS NOT NULL) + (@cx_c4 IS NOT NULL) + (@cx_c5 IS NOT NULL) + (@cx_c6 IS NOT NULL) + (@cx_c7 IS NOT NULL) + (@cx_c8 IS NOT NULL) + (@cx_c9 IS NOT NULL) + (@cx_c10 IS NOT NULL) + (@cx_c11 IS NOT NULL) + (@cx_c12 IS NOT NULL) + (@cx_c13 IS NOT NULL) + (@cx_c14 IS NOT NULL) + (@cx_c15 IS NOT NULL) + (@cx_c16 IS NOT NULL) + (@cx_c17 IS NOT NULL) + (@cx_c18 IS NOT NULL) + (@cx_c19 IS NOT NULL) + (@cx_c20 IS NOT NULL) + (@cx_c21 IS NOT NULL) + (@cx_c22 IS NOT NULL) + (@cx_c23 IS NOT NULL) + (@cx_c24 IS NOT NULL) + (@cx_c25 IS NOT NULL) + (@cx_c26 IS NOT NULL) + (@cx_c27 IS NOT NULL) + (@cx_c28 IS NOT NULL)) = 28,
       (((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c2 AND left_at IS NULL) + 6 < (SELECT max_members FROM club WHERE club_id = @cx_c2)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c4 AND left_at IS NULL) + 4 < (SELECT max_members FROM club WHERE club_id = @cx_c4)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c5 AND left_at IS NULL) + 4 < (SELECT max_members FROM club WHERE club_id = @cx_c5)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c6 AND left_at IS NULL) + 4 < (SELECT max_members FROM club WHERE club_id = @cx_c6)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c7 AND left_at IS NULL) + 4 < (SELECT max_members FROM club WHERE club_id = @cx_c7)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c8 AND left_at IS NULL) + 4 < (SELECT max_members FROM club WHERE club_id = @cx_c8)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c9 AND left_at IS NULL) + 4 < (SELECT max_members FROM club WHERE club_id = @cx_c9)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c10 AND left_at IS NULL) + 6 < (SELECT max_members FROM club WHERE club_id = @cx_c10)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c11 AND left_at IS NULL) + 4 < (SELECT max_members FROM club WHERE club_id = @cx_c11)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c12 AND left_at IS NULL) + 7 < (SELECT max_members FROM club WHERE club_id = @cx_c12)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c13 AND left_at IS NULL) + 4 < (SELECT max_members FROM club WHERE club_id = @cx_c13)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c14 AND left_at IS NULL) + 4 < (SELECT max_members FROM club WHERE club_id = @cx_c14)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c15 AND left_at IS NULL) + 6 < (SELECT max_members FROM club WHERE club_id = @cx_c15)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c17 AND left_at IS NULL) + 4 < (SELECT max_members FROM club WHERE club_id = @cx_c17)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c18 AND left_at IS NULL) + 4 < (SELECT max_members FROM club WHERE club_id = @cx_c18)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c19 AND left_at IS NULL) + 4 < (SELECT max_members FROM club WHERE club_id = @cx_c19)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c21 AND left_at IS NULL) + 7 < (SELECT max_members FROM club WHERE club_id = @cx_c21)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c22 AND left_at IS NULL) + 7 < (SELECT max_members FROM club WHERE club_id = @cx_c22)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c23 AND left_at IS NULL) + 7 < (SELECT max_members FROM club WHERE club_id = @cx_c23)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c24 AND left_at IS NULL) + 6 < (SELECT max_members FROM club WHERE club_id = @cx_c24)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c25 AND left_at IS NULL) + 6 < (SELECT max_members FROM club WHERE club_id = @cx_c25)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c26 AND left_at IS NULL) + 4 < (SELECT max_members FROM club WHERE club_id = @cx_c26)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c27 AND left_at IS NULL) + 4 < (SELECT max_members FROM club WHERE club_id = @cx_c27)) + ((SELECT COUNT(*) FROM club_member WHERE club_id = @cx_c28 AND left_at IS NULL) + 4 < (SELECT max_members FROM club WHERE club_id = @cx_c28))) = 24,
       ((@cx_f1 IS NOT NULL AND @cx_f1_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f1 AND sport_id = @cx_s_농구)) + (@cx_f2 IS NOT NULL AND @cx_f2_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f2 AND sport_id = @cx_s_테니스)) + (@cx_f3 IS NOT NULL AND @cx_f3_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f3 AND sport_id = @cx_s_테니스)) + (@cx_f4 IS NOT NULL AND @cx_f4_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f4 AND sport_id = @cx_s_배드민턴)) + (@cx_f5 IS NOT NULL AND @cx_f5_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f5 AND sport_id = @cx_s_배드민턴)) + (@cx_f6 IS NOT NULL AND @cx_f6_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f6 AND sport_id = @cx_s_테니스)) + (@cx_f7 IS NOT NULL AND @cx_f7_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f7 AND sport_id = @cx_s_테니스)) + (@cx_f8 IS NOT NULL AND @cx_f8_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f8 AND sport_id = @cx_s_농구)) + (@cx_f9 IS NOT NULL AND @cx_f9_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f9 AND sport_id = @cx_s_배드민턴)) + (@cx_f10 IS NOT NULL AND @cx_f10_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f10 AND sport_id = @cx_s_축구)) + (@cx_f11 IS NOT NULL AND @cx_f11_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f11 AND sport_id = @cx_s_테니스)) + (@cx_f12 IS NOT NULL AND @cx_f12_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f12 AND sport_id = @cx_s_배드민턴)) + (@cx_f13 IS NOT NULL AND @cx_f13_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f13 AND sport_id = @cx_s_농구)) + (@cx_f14 IS NOT NULL AND @cx_f14_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f14 AND sport_id = @cx_s_족구)) + (@cx_f15 IS NOT NULL AND @cx_f15_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f15 AND sport_id = @cx_s_테니스)) + (@cx_f16 IS NOT NULL AND @cx_f16_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f16 AND sport_id = @cx_s_배드민턴)) + (@cx_f17 IS NOT NULL AND @cx_f17_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f17 AND sport_id = @cx_s_축구)) + (@cx_f18 IS NOT NULL AND @cx_f18_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f18 AND sport_id = @cx_s_축구)) + (@cx_f19 IS NOT NULL AND @cx_f19_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f19 AND sport_id = @cx_s_축구)) + (@cx_f20 IS NOT NULL AND @cx_f20_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f20 AND sport_id = @cx_s_농구)) + (@cx_f21 IS NOT NULL AND @cx_f21_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f21 AND sport_id = @cx_s_농구)) + (@cx_f22 IS NOT NULL AND @cx_f22_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f22 AND sport_id = @cx_s_족구)) + (@cx_f23 IS NOT NULL AND @cx_f23_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f23 AND sport_id = @cx_s_배드민턴)) + (@cx_f24 IS NOT NULL AND @cx_f24_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = @cx_f24 AND sport_id = @cx_s_테니스))) = 24,
       NOT EXISTS (SELECT 1 FROM social_match WHERE status <> '취소' AND (
         (facility_id = @cx_f8 AND start_at < '2026-08-01 12:00:00' AND end_at > '2026-08-01 10:00:00') OR
         (facility_id = @cx_f11 AND start_at < '2026-08-02 10:00:00' AND end_at > '2026-08-02 08:00:00') OR
         (facility_id = @cx_f16 AND start_at < '2026-08-02 11:00:00' AND end_at > '2026-08-02 09:00:00') OR
         (facility_id = @cx_f12 AND start_at < '2026-08-04 22:00:00' AND end_at > '2026-08-04 20:00:00') OR
         (facility_id = @cx_f2 AND start_at < '2026-08-07 09:00:00' AND end_at > '2026-08-07 07:00:00') OR
         (facility_id = @cx_f1 AND start_at < '2026-08-09 11:00:00' AND end_at > '2026-08-09 09:00:00') OR
         (facility_id = @cx_f3 AND start_at < '2026-08-11 23:00:00' AND end_at > '2026-08-11 21:00:00') OR
         (facility_id = @cx_f6 AND start_at < '2026-08-13 21:30:00' AND end_at > '2026-08-13 19:30:00') OR
         (facility_id = @cx_f15 AND start_at < '2026-08-19 22:00:00' AND end_at > '2026-08-19 20:00:00') OR
         (facility_id = @cx_f3 AND start_at < '2026-08-20 23:00:00' AND end_at > '2026-08-20 21:00:00') OR
         (facility_id = @cx_f5 AND start_at < '2026-08-21 22:00:00' AND end_at > '2026-08-21 20:00:00') OR
         (facility_id = @cx_f1 AND start_at < '2026-08-23 11:00:00' AND end_at > '2026-08-23 09:00:00') OR
         (facility_id = @cx_f6 AND start_at < '2026-08-27 21:30:00' AND end_at > '2026-08-27 19:30:00') OR
         (facility_id = @cx_f5 AND start_at < '2026-08-28 22:00:00' AND end_at > '2026-08-28 20:00:00') OR
         (facility_id = @cx_f11 AND start_at < '2026-08-29 10:00:00' AND end_at > '2026-08-29 08:00:00') OR
         (facility_id = @cx_f8 AND start_at < '2026-08-29 12:00:00' AND end_at > '2026-08-29 10:00:00') OR
         (facility_id = @cx_f12 AND start_at < '2026-09-01 22:00:00' AND end_at > '2026-09-01 20:00:00') OR
         (facility_id = @cx_f3 AND start_at < '2026-09-03 23:00:00' AND end_at > '2026-09-03 21:00:00') OR
         (facility_id = @cx_f7 AND start_at < '2026-09-11 22:00:00' AND end_at > '2026-09-11 20:00:00') OR
         (facility_id = @cx_f16 AND start_at < '2026-09-13 11:00:00' AND end_at > '2026-09-13 09:00:00') OR
         (facility_id = @cx_f5 AND start_at < '2026-09-15 22:00:00' AND end_at > '2026-09-15 20:00:00') OR
         (facility_id = @cx_f11 AND start_at < '2026-09-19 10:00:00' AND end_at > '2026-09-19 08:00:00') OR
         (facility_id = @cx_f8 AND start_at < '2026-09-19 12:00:00' AND end_at > '2026-09-19 10:00:00') OR
         (facility_id = @cx_f6 AND start_at < '2026-09-24 21:30:00' AND end_at > '2026-09-24 19:30:00') OR
         (facility_id = @cx_f10 AND start_at < '2026-09-26 22:00:00' AND end_at > '2026-09-26 20:00:00') OR
         (facility_id = @cx_f18 AND start_at < '2026-09-29 21:00:00' AND end_at > '2026-09-29 19:00:00') OR
         (facility_id = @cx_f5 AND start_at < '2026-09-29 22:00:00' AND end_at > '2026-09-29 20:00:00') OR
         (facility_id = @cx_f10 AND start_at < '2026-09-30 22:00:00' AND end_at > '2026-09-30 20:00:00') OR
         (facility_id = @cx_f13 AND start_at < '2026-10-02 21:00:00' AND end_at > '2026-10-02 19:00:00') OR
         (facility_id = @cx_f1 AND start_at < '2026-10-03 11:00:00' AND end_at > '2026-10-03 09:00:00') OR
         (facility_id = @cx_f17 AND start_at < '2026-10-03 12:00:00' AND end_at > '2026-10-03 10:00:00') OR
         (facility_id = @cx_f11 AND start_at < '2026-10-04 10:00:00' AND end_at > '2026-10-04 08:00:00') OR
         (facility_id = @cx_f18 AND start_at < '2026-10-06 21:00:00' AND end_at > '2026-10-06 19:00:00') OR
         (facility_id = @cx_f12 AND start_at < '2026-10-06 22:00:00' AND end_at > '2026-10-06 20:00:00') OR
         (facility_id = @cx_f20 AND start_at < '2026-10-07 20:00:00' AND end_at > '2026-10-07 18:00:00') OR
         (facility_id = @cx_f7 AND start_at < '2026-10-07 22:00:00' AND end_at > '2026-10-07 20:00:00') OR
         (facility_id = @cx_f1 AND start_at < '2026-10-10 11:00:00' AND end_at > '2026-10-10 09:00:00') OR
         (facility_id = @cx_f22 AND start_at < '2026-10-10 12:00:00' AND end_at > '2026-10-10 10:00:00') OR
         (facility_id = @cx_f16 AND start_at < '2026-10-11 11:00:00' AND end_at > '2026-10-11 09:00:00') OR
         (facility_id = @cx_f18 AND start_at < '2026-10-13 21:00:00' AND end_at > '2026-10-13 19:00:00') OR
         (facility_id = @cx_f3 AND start_at < '2026-10-13 23:00:00' AND end_at > '2026-10-13 21:00:00') OR
         (facility_id = @cx_f4 AND start_at < '2026-10-14 21:00:00' AND end_at > '2026-10-14 19:00:00') OR
         (facility_id = @cx_f10 AND start_at < '2026-10-14 22:00:00' AND end_at > '2026-10-14 20:00:00') OR
         (facility_id = @cx_f12 AND start_at < '2026-10-15 22:00:00' AND end_at > '2026-10-15 20:00:00') OR
         (facility_id = @cx_f2 AND start_at < '2026-10-16 09:00:00' AND end_at > '2026-10-16 07:00:00') OR
         (facility_id = @cx_f5 AND start_at < '2026-10-16 22:00:00' AND end_at > '2026-10-16 20:00:00') OR
         (facility_id = @cx_f14 AND start_at < '2026-10-17 11:00:00' AND end_at > '2026-10-17 09:00:00') OR
         (facility_id = @cx_f19 AND start_at < '2026-10-17 16:00:00' AND end_at > '2026-10-17 14:00:00') OR
         (facility_id = @cx_f20 AND start_at < '2026-10-17 20:00:00' AND end_at > '2026-10-17 18:00:00') OR
         (facility_id = @cx_f11 AND start_at < '2026-10-18 10:00:00' AND end_at > '2026-10-18 08:00:00') OR
         (facility_id = @cx_f9 AND start_at < '2026-10-18 12:00:00' AND end_at > '2026-10-18 09:00:00') OR
         (facility_id = @cx_f3 AND start_at < '2026-10-20 23:00:00' AND end_at > '2026-10-20 21:00:00') OR
         (facility_id = @cx_f20 AND start_at < '2026-10-21 20:00:00' AND end_at > '2026-10-21 18:00:00') OR
         (facility_id = @cx_f10 AND start_at < '2026-10-21 22:00:00' AND end_at > '2026-10-21 20:00:00') OR
         (facility_id = @cx_f23 AND start_at < '2026-10-23 20:00:00' AND end_at > '2026-10-23 18:00:00') OR
         (facility_id = @cx_f16 AND start_at < '2026-10-24 11:00:00' AND end_at > '2026-10-24 09:00:00') OR
         (facility_id = @cx_f17 AND start_at < '2026-10-24 12:00:00' AND end_at > '2026-10-24 10:00:00') OR
         (facility_id = @cx_f22 AND start_at < '2026-10-24 12:00:00' AND end_at > '2026-10-24 10:00:00') OR
         (facility_id = @cx_f24 AND start_at < '2026-10-24 12:00:00' AND end_at > '2026-10-24 10:00:00') OR
         (facility_id = @cx_f20 AND start_at < '2026-10-24 20:00:00' AND end_at > '2026-10-24 18:00:00') OR
         (facility_id = @cx_f1 AND start_at < '2026-10-25 11:00:00' AND end_at > '2026-10-25 09:00:00') OR
         (facility_id = @cx_f23 AND start_at < '2026-10-27 20:00:00' AND end_at > '2026-10-27 18:00:00') OR
         (facility_id = @cx_f15 AND start_at < '2026-10-28 22:00:00' AND end_at > '2026-10-28 20:00:00') OR
         (facility_id = @cx_f18 AND start_at < '2026-10-29 21:00:00' AND end_at > '2026-10-29 19:00:00') OR
         (facility_id = @cx_f14 AND start_at < '2026-10-31 11:00:00' AND end_at > '2026-10-31 09:00:00') OR
         (facility_id = @cx_f20 AND start_at < '2026-10-31 20:00:00' AND end_at > '2026-10-31 18:00:00') OR
         (facility_id = @cx_f6 AND start_at < '2026-11-05 21:30:00' AND end_at > '2026-11-05 19:30:00') OR
         (facility_id = @cx_f23 AND start_at < '2026-11-06 20:00:00' AND end_at > '2026-11-06 18:00:00') OR
         (facility_id = @cx_f8 AND start_at < '2026-11-07 12:00:00' AND end_at > '2026-11-07 10:00:00') OR
         (facility_id = @cx_f22 AND start_at < '2026-11-07 12:00:00' AND end_at > '2026-11-07 10:00:00') OR
         (facility_id = @cx_f19 AND start_at < '2026-11-07 16:00:00' AND end_at > '2026-11-07 14:00:00') OR
         (facility_id = @cx_f1 AND start_at < '2026-11-29 11:00:00' AND end_at > '2026-11-29 09:00:00') OR
         (facility_id = @cx_f2 AND start_at < '2026-11-27 09:00:00' AND end_at > '2026-11-27 07:00:00') OR
         (facility_id = @cx_f3 AND start_at < '2026-11-26 23:00:00' AND end_at > '2026-11-26 21:00:00') OR
         (facility_id = @cx_f4 AND start_at < '2026-11-28 21:00:00' AND end_at > '2026-11-28 19:00:00') OR
         (facility_id = @cx_f5 AND start_at < '2026-12-04 22:00:00' AND end_at > '2026-12-04 20:00:00') OR
         (facility_id = @cx_f6 AND start_at < '2026-11-26 21:30:00' AND end_at > '2026-11-26 19:30:00') OR
         (facility_id = @cx_f7 AND start_at < '2026-11-25 22:00:00' AND end_at > '2026-11-25 20:00:00') OR
         (facility_id = @cx_f8 AND start_at < '2026-12-05 12:00:00' AND end_at > '2026-12-05 10:00:00') OR
         (facility_id = @cx_f9 AND start_at < '2026-11-29 12:00:00' AND end_at > '2026-11-29 09:00:00') OR
         (facility_id = @cx_f10 AND start_at < '2026-12-02 22:00:00' AND end_at > '2026-12-02 20:00:00') OR
         (facility_id = @cx_f11 AND start_at < '2026-12-06 10:00:00' AND end_at > '2026-12-06 08:00:00') OR
         (facility_id = @cx_f12 AND start_at < '2026-11-24 22:00:00' AND end_at > '2026-11-24 20:00:00') OR
         (facility_id = @cx_f13 AND start_at < '2026-12-04 21:00:00' AND end_at > '2026-12-04 19:00:00') OR
         (facility_id = @cx_f14 AND start_at < '2026-11-28 11:00:00' AND end_at > '2026-11-28 09:00:00') OR
         (facility_id = @cx_f15 AND start_at < '2026-11-25 22:00:00' AND end_at > '2026-11-25 20:00:00') OR
         (facility_id = @cx_f16 AND start_at < '2026-12-06 11:00:00' AND end_at > '2026-12-06 09:00:00') OR
         (facility_id = @cx_f22 AND start_at < '2026-12-05 12:00:00' AND end_at > '2026-12-05 10:00:00') OR
         (facility_id = @cx_f23 AND start_at < '2026-12-04 20:00:00' AND end_at > '2026-12-04 18:00:00') OR
         (facility_id = @cx_f24 AND start_at < '2026-11-28 12:00:00' AND end_at > '2026-11-28 10:00:00'))),
       NOT EXISTS (SELECT 1 FROM match_application WHERE receipt_no IN ('RCPT-20260730-S4JACR8K', 'RCPT-20260730-L2LK468E', 'RCPT-20260730-JXFWZ7E8', 'RCPT-20260730-PDHX5TRW', 'RCPT-20260730-N9DAYDFX', 'RCPT-20260730-24SV2WCF', 'RCPT-20260730-499VS35A', 'RCPT-20260730-KPNYLTUW', 'RCPT-20260728-YK6U475U', 'RCPT-20260728-5Q98Z24A', 'RCPT-20260731-VMZ3KFEM', 'RCPT-20260731-58MKD5JW', 'RCPT-20260729-7GHV43TE', 'RCPT-20260729-V9RVF3NH', 'RCPT-20260731-TBMMJKSK', 'RCPT-20260731-LKREGUMY', 'RCPT-20260802-97B8L9JJ', 'RCPT-20260802-72FCXCJN', 'RCPT-20260801-79AX6CTF', 'RCPT-20260801-858WXD7A', 'RCPT-20260804-6WLXHMCD', 'RCPT-20260804-5LA2LKYK', 'RCPT-20260805-6Y3D2279', 'RCPT-20260805-XG9U5685', 'RCPT-20260807-GHPJ3QVA', 'RCPT-20260807-CP5B6NFE', 'RCPT-20260807-MM79ARUG', 'RCPT-20260807-ZFWD9FKK', 'RCPT-20260807-5SYMFCPR', 'RCPT-20260807-LBP62TDC', 'RCPT-20260807-ECX869UY', 'RCPT-20260807-Q75K44N8', 'RCPT-20260807-CD2C68HW', 'RCPT-20260807-HH3KHDN7', 'RCPT-20260807-799WEYDP', 'RCPT-20260807-CDLB5AHA', 'RCPT-20260811-467XSRC9', 'RCPT-20260811-6NS3HK78', 'RCPT-20260810-MBSHN9UF', 'RCPT-20260810-TREG5E7X', 'RCPT-20260815-25FTHJL4', 'RCPT-20260815-NBHJAJ3Q', 'RCPT-20260816-FEWZW7MG', 'RCPT-20260816-ZSN3BUMG', 'RCPT-20260818-PRTW882U', 'RCPT-20260818-4UDK4HQU', 'RCPT-20260818-NGY9KBED', 'RCPT-20260818-CZBEM8S8', 'RCPT-20260817-MFED8AC4', 'RCPT-20260817-CZV47A8E', 'RCPT-20260819-JAGP96MG', 'RCPT-20260819-CNVFZA72', 'RCPT-20260821-E6FFF83M', 'RCPT-20260821-FP49TQ37', 'RCPT-20260821-H8E6ULGL', 'RCPT-20260821-NLQGNF7C', 'RCPT-20260820-CTTJM3RX', 'RCPT-20260820-SNEFZV8K', 'RCPT-20260820-K4NP3XH5', 'RCPT-20260820-7S6PENE2', 'RCPT-20260824-NVE4PDPT', 'RCPT-20260824-M572KNUA', 'RCPT-20260824-C6N9D989', 'RCPT-20260824-3R8Z8WD8', 'RCPT-20260826-82ZDNWGU', 'RCPT-20260826-J7WHD522', 'RCPT-20260826-ZXS829M8', 'RCPT-20260826-2JDKYRT4', 'RCPT-20260825-AZNBR63P', 'RCPT-20260825-V5WBUAEM', 'RCPT-20260827-CB9CH8LK', 'RCPT-20260827-PMLKCSHF', 'RCPT-20260827-PJK3ASNS', 'RCPT-20260827-5SQCPDFR', 'RCPT-20260827-E7YEJ47K', 'RCPT-20260827-TETFL469', 'RCPT-20260825-22KYXGGN', 'RCPT-20260825-M8W8BR8J', 'RCPT-20260825-TVBHEZ29', 'RCPT-20260825-MRLU7DHU', 'RCPT-20260830-KZK3EJGL', 'RCPT-20260830-T3QGHHKY', 'RCPT-20260829-CE2JXPGD', 'RCPT-20260829-H8QGVG2M', 'RCPT-20260901-GTMVALLT', 'RCPT-20260901-KDLQ4SM4', 'RCPT-20260831-275S82BB', 'RCPT-20260831-M657DBFC', 'RCPT-20260908-3Z5WS5LP', 'RCPT-20260908-WD8UMQU7', 'RCPT-20260907-REBGTHP6', 'RCPT-20260907-PNA8PVKG', 'RCPT-20260909-LS9EWCVH', 'RCPT-20260909-LVDYCGHX', 'RCPT-20260909-UHAUF92K', 'RCPT-20260909-99LUT3MP', 'RCPT-20260912-BHD82HTK', 'RCPT-20260912-9RUK2DCE', 'RCPT-20260911-BA75E7TN', 'RCPT-20260911-A3W8QSUY', 'RCPT-20260915-U87CNU3Y', 'RCPT-20260915-69T87CN3', 'RCPT-20260917-NA9SDR48', 'RCPT-20260917-SV5SFGUL', 'RCPT-20260917-2P7SAQSJ', 'RCPT-20260917-8RC4L6V6', 'RCPT-20260917-ZESXRHD2', 'RCPT-20260917-RH779JJS', 'RCPT-20260915-XMZZFEE2', 'RCPT-20260915-RLBTVH2M', 'RCPT-20260915-R8F5V99J', 'RCPT-20260915-9CZTAX3P', 'RCPT-20260921-9FSU2KXD', 'RCPT-20260921-TJZTQ46H', 'RCPT-20260921-K8NW5S79', 'RCPT-20260921-NDK73K66', 'RCPT-20260922-M8WAWN3M', 'RCPT-20260922-68T63MGV', 'RCPT-20260922-KU5QLY4C', 'RCPT-20260922-4KZVXNSP', 'RCPT-20260922-SL7FPU83', 'RCPT-20260924-V5KGU7LN', 'RCPT-20260924-4U9JA2Q9', 'RCPT-20260924-HB6NRZD6', 'RCPT-20260924-PQRAFQMD', 'RCPT-20260924-9YLCNA8T', 'RCPT-20260926-THCRRUX6', 'RCPT-20260926-Z9792X2Z', 'RCPT-20260926-DLHXZR3X', 'RCPT-20260926-B86RRK2G', 'RCPT-20260926-BM9VWXBR', 'RCPT-20260928-R9L9HRDD', 'RCPT-20260928-B7AGBVGY', 'RCPT-20260928-EBYEBGTS', 'RCPT-20260928-YRGSA54A', 'RCPT-20260928-QZ7ES9EE', 'RCPT-20260927-BJJX7NPU', 'RCPT-20260927-NY8YWM6A', 'RCPT-20260927-8Z3LYDQ5', 'RCPT-20260927-L5KM8ZF8', 'RCPT-20260928-V3RXKBXT', 'RCPT-20260928-CE35L4PQ', 'RCPT-20260928-3C3WP8ME', 'RCPT-20260928-CBX88RM7', 'RCPT-20260928-LNVPQHHK', 'RCPT-20260926-SMRC9JN6', 'RCPT-20260926-853CJRVE', 'RCPT-20260926-UMTZQZRY', 'RCPT-20260926-UBQVYKNE', 'RCPT-20260926-FQAPD3KQ', 'RCPT-20260929-HJ3RRSMJ', 'RCPT-20260929-KWVGU498', 'RCPT-20260929-7M9FMVVJ', 'RCPT-20260929-ZLJWFPLS', 'RCPT-20260928-WA8Q8KBR', 'RCPT-20260928-XHYGG4BS', 'RCPT-20260928-VF2TQZ3U', 'RCPT-20260928-NP8HJUQH', 'RCPT-20260930-8FP3YBBG', 'RCPT-20260930-M3TW6QER', 'RCPT-20260930-6W4M63CA', 'RCPT-20260930-A74VVLDQ', 'RCPT-20260930-MCYHU3KR', 'RCPT-20260930-GW5NWF2M', 'RCPT-20260930-7EXURF5D', 'RCPT-20260930-DHLFSUMJ', 'RCPT-20261001-3DQ86XWE', 'RCPT-20261001-XHEV4CT4', 'RCPT-20261001-K54B2PZZ', 'RCPT-20261001-W73WA79S', 'RCPT-20261001-S5CES8R2', 'RCPT-20261002-X7GYHWEH', 'RCPT-20261002-3ZRN3GA3', 'RCPT-20261002-EY9V86UL', 'RCPT-20261002-AR4LR7WE', 'RCPT-20261002-VAYR9CH5', 'RCPT-20261001-QSELLJQ4', 'RCPT-20261001-K8DXQR3E', 'RCPT-20260929-D62SN5E8', 'RCPT-20260929-YB4LQNTS', 'RCPT-20261003-HSLTB5LB', 'RCPT-20261003-Z8N9GK6R', 'RCPT-20261003-544M7UUN', 'RCPT-20261003-XXBVX4TP', 'RCPT-20261003-SZ2L2J6K', 'RCPT-20261002-LZAJRDXH', 'RCPT-20261002-63SZ8UHM', 'RCPT-20261002-5MEY3X68', 'RCPT-20261002-E6YSJM3S', 'RCPT-20261002-KZHX769C', 'RCPT-20261003-ER5NFN7A', 'RCPT-20261003-YDB6RVU4', 'RCPT-20261003-PAKFX3CF', 'RCPT-20261003-84R7ZT35', 'RCPT-20261005-MC96YHPR', 'RCPT-20261005-AKQ3VGKE', 'RCPT-20261005-CPKCRS2Z', 'RCPT-20261005-X3P6RG82', 'RCPT-20261004-FKF2UMEF', 'RCPT-20261004-7WBLS6JR', 'RCPT-20261004-RFCDXXJE', 'RCPT-20261004-WB3KA92S', 'RCPT-20261005-7849YTN2', 'RCPT-20261005-L9XMQB93', 'RCPT-20261005-PU7FNJYH', 'RCPT-20261005-UGGWTCEV', 'RCPT-20261008-D45RCKSX', 'RCPT-20261008-KUGMVPDE', 'RCPT-20261008-R6QBGTJR', 'RCPT-20261008-QV2AC5XS', 'RCPT-20261009-NLXFEF28', 'RCPT-20261009-AHDD55EZ', 'RCPT-20261009-736BUNR3', 'RCPT-20261009-MNRWKD7A', 'RCPT-20261008-89JS8AVS', 'RCPT-20261008-FQLVSVHF', 'RCPT-20261008-9MF9ZWR7', 'RCPT-20261007-XW3VWZ36', 'RCPT-20261007-QGBH7EP4', 'RCPT-20261007-RAKW2RSR', 'RCPT-20261008-ELBVGNJB', 'RCPT-20261008-C8PE28BP', 'RCPT-20261008-FRXCG5SC', 'RCPT-20261008-P7FB9EMQ', 'RCPT-20261010-2E22NALT', 'RCPT-20261010-ZNQCZSX2', 'RCPT-20261010-HDWTR5D9', 'RCPT-20261010-QNBAA7FY', 'RCPT-20261010-4GMFJYWS', 'RCPT-20261010-6JUF7MDT', 'RCPT-20261010-3ZDA8D9P', 'RCPT-20261010-HT8364N7', 'RCPT-20261010-C6G8RFKQ', 'RCPT-20261010-88MMLLYR', 'RCPT-20261009-5ERMCVEQ', 'RCPT-20261009-9FFHFEPC', 'RCPT-20261010-Z47M85PJ', 'RCPT-20261010-VMRG5LNA', 'RCPT-20261011-6J828ZPC', 'RCPT-20261011-5KMGBRSS', 'RCPT-20261012-UVGYH34Z', 'RCPT-20261012-3HEKK9A4', 'RCPT-20261011-ZEERJH7G', 'RCPT-20261011-7585QFSC', 'RCPT-20261011-9RBPPEZJ', 'RCPT-20261011-A5PD757B', 'RCPT-20261011-B5ASFKNP', 'RCPT-20261012-ZNPYYR2Z', 'RCPT-20261012-U2LATW33', 'RCPT-20261012-KJ3KLLR9', 'RCPT-20261012-FNE6UQET', 'RCPT-20261012-2LMVY8YZ', 'RCPT-20261011-G2N844XL', 'RCPT-20261011-DVR63DPW', 'RCPT-20261012-8DP3HQHE', 'RCPT-20261012-L26P2GYD', 'RCPT-20261012-PU89W6YQ', 'RCPT-20261012-49XRCDT6', 'RCPT-20261015-P7CZ2XCT', 'RCPT-20261015-G92LETZZ', 'RCPT-20261013-F9VQTCSA', 'RCPT-20261013-P9ZPPR2D', 'RCPT-20261014-LQE2Z9PL', 'RCPT-20261014-77E4ZRWT', 'RCPT-20261014-N4GKVE9V', 'RCPT-20261014-RT5QJDPR', 'RCPT-20261014-AJ6MNNY8', 'RCPT-20261015-WSE9L4P8', 'RCPT-20261015-JLM3JXYB', 'RCPT-20261015-EZNZT3MC', 'RCPT-20261015-2ZJP7VDR', 'RCPT-20261015-R929RX9A', 'RCPT-20261015-SRH4RXVP', 'RCPT-20261015-9KZ6B3DP', 'RCPT-20261015-PBKEFWJ2', 'RCPT-20261016-2LKNG8LV', 'RCPT-20261016-H9U7LSZ7', 'RCPT-20261016-EV8HCT34', 'RCPT-20261016-AVXKJU8D', 'RCPT-20261016-764PBQD8', 'RCPT-20261015-L2VHBPMB', 'RCPT-20261015-8QFYDV8D', 'RCPT-20261015-S7GB8SK4', 'RCPT-20261015-QAWQMBKN', 'RCPT-20261013-GZE8QU4U', 'RCPT-20261013-V93LURPP', 'RCPT-20261013-XDHCDBT5', 'RCPT-20261013-S43CLN2K', 'RCPT-20261014-9KJSRMDD', 'RCPT-20261014-WN98R5TH', 'RCPT-20261016-89H9H8YF', 'RCPT-20261016-9DFXSNUV', 'RCPT-20261015-G9CUXDKP', 'RCPT-20261015-GQ33FWQW', 'RCPT-20261016-XUP6VM97', 'RCPT-20261016-TBQ66EDB', 'RCPT-20261017-2MCBTQY6', 'RCPT-20261017-LF9U9DNW', 'RCPT-20261017-GDJY636T', 'RCPT-20261017-88S32L9Y', 'RCPT-20261019-WMGLTW7X', 'RCPT-20261019-TH3K7AB4', 'RCPT-20261019-FE5GXLD3', 'RCPT-20261019-XVG7DAT8', 'RCPT-20261018-26X9NMG2', 'RCPT-20261018-K8WKSX7F', 'RCPT-20261018-N9C7G549', 'RCPT-20261018-LFKUELRB', 'RCPT-20261019-WM6NRKMF', 'RCPT-20261019-Q4CB2ZMR', 'RCPT-20261019-TKQWQXL6', 'RCPT-20261019-QHMMUPHW', 'RCPT-20261019-DYF3DLSA', 'RCPT-20261018-TC57CY98', 'RCPT-20261018-ASWQKPF6', 'RCPT-20261018-8P6E6BRL', 'RCPT-20261018-78WPUYM8', 'RCPT-20261018-7YCJ4TBZ', 'RCPT-20261019-F4JDRRN7', 'RCPT-20261019-BH2A8VX2', 'RCPT-20261021-2URJ2E5Z', 'RCPT-20261021-QTYPWFBT', 'RCPT-20261021-W9BCEP6G', 'RCPT-20261021-APM3SL59', 'RCPT-20261020-2H99N4CU', 'RCPT-20261020-6UD46R7Z', 'RCPT-20261020-VA9ZEDYG', 'RCPT-20261020-QBU5BWCP', 'RCPT-20261020-VUVSDFA4', 'RCPT-20261020-NKTDJWVA', 'RCPT-20261020-CDRGD7R3', 'RCPT-20261023-R7JF9J4L', 'RCPT-20261023-CEX7J6VM', 'RCPT-20261023-V9QK923X', 'RCPT-20261023-ZTH6L447', 'RCPT-20261023-WPLV7ZHT', 'RCPT-20261020-DG6EUV3V', 'RCPT-20261020-8DLK53QE', 'RCPT-20261020-HHD85P2N', 'RCPT-20261021-5M5HFSX6', 'RCPT-20261021-MM89PRRG', 'RCPT-20261021-DWPUEC8L', 'RCPT-20261022-MCZ7GPHV', 'RCPT-20261022-PZRYAXTJ', 'RCPT-20261021-U68G29QJ', 'RCPT-20261021-NLSGNJGW', 'RCPT-20261023-LM5FGCKL', 'RCPT-20261023-DECYSM6D', 'RCPT-20261023-9WV8559C', 'RCPT-20261023-FWGV8RAN', 'RCPT-20261023-BFN95DMD', 'RCPT-20261023-3YUM76NZ', 'RCPT-20261023-TVSDAE6Z', 'RCPT-20261023-CGXXJJ63', 'RCPT-20261021-5CDD6PCT', 'RCPT-20261021-CNXGQA9R', 'RCPT-20261021-Y9MT3BUK', 'RCPT-20261021-NKGE435L', 'RCPT-20261021-73V2FJWC', 'RCPT-20261021-XNZ9VM3W', 'RCPT-20261021-FQNF7H5Y', 'RCPT-20261021-GHVEAR52', 'RCPT-20261024-JPU3DR9X', 'RCPT-20261024-4T9ZPZ4S', 'RCPT-20261025-QF6ZUK24', 'RCPT-20261025-ZWSN5V5T', 'RCPT-20261024-98JKQKV7', 'RCPT-20261024-3Q7ASY3N', 'RCPT-20261025-6WCLYWVR', 'RCPT-20261025-7JU3PB5G', 'RCPT-20261027-JHBJDZ6E', 'RCPT-20261027-WWQQQXA7', 'RCPT-20261027-UJX7CNH3', 'RCPT-20261027-KF52FHWH', 'RCPT-20261027-UPJWTUQ8', 'RCPT-20261026-ZB5TTGVV', 'RCPT-20261026-F6VE4KSD', 'RCPT-20261026-CJZQN9SZ', 'RCPT-20261026-3UCPYLSP', 'RCPT-20261026-5R2XH244', 'RCPT-20261027-87C75RAW', 'RCPT-20261027-3NJBLCTS', 'RCPT-20261027-GU5U6A2A', 'RCPT-20261029-JUCXGPUQ', 'RCPT-20261029-HNHRMUJT', 'RCPT-20261029-CYBADL78', 'RCPT-20261027-V3QZKHNU', 'RCPT-20261027-EXNTKNX2', 'RCPT-20261027-D9KWUVZY', 'RCPT-20261027-DWWTP9QJ', 'RCPT-20261029-7AC466X2', 'RCPT-20261029-GQHTR6ZL', 'RCPT-20261029-544GCAD9', 'RCPT-20261029-B8FVGUT2', 'RCPT-20261102-X48UXVFT', 'RCPT-20261102-MXFDFR44', 'RCPT-20261103-CE9LHFQM', 'RCPT-20261103-DYUSAF4W', 'RCPT-20261102-HLT4QZH2', 'RCPT-20261102-7DB95C84', 'RCPT-20261103-SX3F65VB', 'RCPT-20261103-HVUT4N5V', 'RCPT-20261104-8B9SJU8Q', 'RCPT-20261104-UF2AJ78M', 'RCPT-20261104-VX3DWJDM', 'RCPT-20261104-R82699U4', 'RCPT-20261105-ZLCQXDDH', 'RCPT-20261105-FD7E6NJP', 'RCPT-20261105-GGQ45FR6', 'RCPT-20261105-UH55RVJH', 'RCPT-20261105-UBKF72ZY', 'RCPT-20261105-JXQ8GT3Z', 'RCPT-20261105-M47YHS9Z', 'RCPT-20261103-96RU7B8L', 'RCPT-20261103-S3WPM5G9', 'RCPT-20261103-XNQRYH6N', 'RCPT-20261105-W8JHN6JF', 'RCPT-20261105-X7QDCPMD', 'RCPT-20261105-3SJ9GCQZ', 'RCPT-20261105-NJKC3M4L', 'RCPT-20261105-4L9ZRZCH', 'RCPT-20261105-3TAZF8RU', 'RCPT-20261105-CUTTNGAN', 'RCPT-20261105-JXL89C6N', 'RCPT-20261105-648Z7BHD', 'RCPT-20261105-NLGDTEVV', 'RCPT-20261112-SNXM525S', 'RCPT-20261112-8BRQ6RTT', 'RCPT-20261112-VZBXLHXC', 'RCPT-20261112-7HL8ZJQU', 'RCPT-20261112-48Y2V5HM', 'RCPT-20261112-QEC2JNWG', 'RCPT-20261112-MKFAWJUK', 'RCPT-20261112-3VL668KG', 'RCPT-20261112-S9XHNPLE', 'RCPT-20261112-U2HEFXTX', 'RCPT-20261112-U2MPK3L6', 'RCPT-20261112-XDR4QBH2', 'RCPT-20261112-GNX36CVF', 'RCPT-20261112-UXRWTABZ', 'RCPT-20261112-8WTJJD2P', 'RCPT-20261112-LV4V8DKG', 'RCPT-20261112-HVJ7SEV9', 'RCPT-20261112-A3BK5TGM', 'RCPT-20261112-U9C88AQ7', 'RCPT-20261112-Y4FSXJXZ', 'RCPT-20261112-GQTRDC94', 'RCPT-20261112-8CGN2EAB', 'RCPT-20261112-CZATMTA3', 'RCPT-20261112-GSRNH8ZH', 'RCPT-20261112-3JEKVU2W', 'RCPT-20261112-4D2X7XXY', 'RCPT-20261112-UDUDEXPQ', 'RCPT-20261112-26D6V3UN', 'RCPT-20261112-D6T3HUQ2', 'RCPT-20261112-NG7MJZBS', 'RCPT-20261112-JX3HZG4W', 'RCPT-20261112-R7WY9WEA', 'RCPT-20261112-PSPX6GC9', 'RCPT-20261112-26F7HWXT', 'RCPT-20261112-J4REXXRD', 'RCPT-20261112-CAVRY2FG', 'RCPT-20261112-JZADVN35', 'RCPT-20261112-JHLVFXPZ', 'RCPT-20261112-S9WC5L5W', 'RCPT-20261112-GEVNWWD7', 'RCPT-20261112-F67QE43D', 'RCPT-20261112-QD9PFLGL', 'RCPT-20261112-R2XNUBW5', 'RCPT-20261112-TF75SRAF', 'RCPT-20261112-GXHA95GR', 'RCPT-20261112-NVTMN9KS', 'RCPT-20261112-E96EPRWN', 'RCPT-20261112-3C3C92GE', 'RCPT-20261112-HXWPHL6N')),
       NOT EXISTS (SELECT 1 FROM app_user WHERE email IN ('club-user-002@spoit.test', 'club-user-003@spoit.test', 'club-user-004@spoit.test', 'club-user-005@spoit.test', 'club-user-006@spoit.test', 'club-user-007@spoit.test', 'club-user-008@spoit.test', 'club-user-009@spoit.test', 'club-user-010@spoit.test', 'club-user-011@spoit.test', 'club-user-012@spoit.test', 'club-user-013@spoit.test', 'club-user-014@spoit.test', 'club-user-015@spoit.test', 'club-user-016@spoit.test', 'club-user-017@spoit.test', 'club-user-018@spoit.test', 'club-user-019@spoit.test', 'club-user-020@spoit.test', 'club-user-021@spoit.test', 'club-user-022@spoit.test', 'club-user-023@spoit.test', 'club-user-024@spoit.test', 'club-user-025@spoit.test', 'club-user-026@spoit.test', 'club-user-027@spoit.test', 'club-user-028@spoit.test', 'club-user-029@spoit.test', 'club-user-030@spoit.test', 'club-user-031@spoit.test', 'club-user-032@spoit.test', 'club-user-033@spoit.test', 'club-user-034@spoit.test', 'club-user-035@spoit.test', 'club-user-036@spoit.test', 'club-user-037@spoit.test', 'club-user-038@spoit.test', 'club-user-039@spoit.test', 'club-user-040@spoit.test', 'club-user-041@spoit.test', 'club-user-042@spoit.test', 'club-user-043@spoit.test', 'club-user-044@spoit.test', 'club-user-045@spoit.test', 'club-user-046@spoit.test', 'club-user-047@spoit.test', 'club-user-048@spoit.test', 'club-user-049@spoit.test', 'club-user-050@spoit.test', 'club-user-051@spoit.test', 'club-user-052@spoit.test', 'club-user-053@spoit.test', 'club-user-054@spoit.test', 'club-user-055@spoit.test', 'club-user-056@spoit.test', 'club-user-057@spoit.test', 'club-user-058@spoit.test', 'club-user-059@spoit.test', 'club-user-060@spoit.test', 'club-user-061@spoit.test', 'club-user-062@spoit.test', 'club-user-063@spoit.test', 'club-user-064@spoit.test', 'club-user-065@spoit.test', 'club-user-066@spoit.test', 'club-user-067@spoit.test', 'club-user-068@spoit.test', 'club-user-069@spoit.test', 'club-user-070@spoit.test', 'club-user-071@spoit.test', 'club-user-072@spoit.test', 'club-user-073@spoit.test', 'club-user-074@spoit.test', 'club-user-075@spoit.test', 'club-user-076@spoit.test', 'club-user-077@spoit.test', 'club-user-078@spoit.test', 'club-user-079@spoit.test', 'club-user-080@spoit.test', 'club-user-081@spoit.test', 'club-user-082@spoit.test', 'club-user-083@spoit.test', 'club-user-084@spoit.test', 'club-user-085@spoit.test', 'club-user-086@spoit.test', 'club-user-087@spoit.test', 'club-user-088@spoit.test', 'club-user-089@spoit.test', 'club-user-090@spoit.test', 'club-user-091@spoit.test', 'club-user-092@spoit.test', 'club-user-093@spoit.test', 'club-user-094@spoit.test', 'club-user-095@spoit.test', 'club-user-096@spoit.test', 'club-user-097@spoit.test', 'club-user-098@spoit.test', 'club-user-099@spoit.test', 'club-user-100@spoit.test', 'club-user-101@spoit.test', 'club-user-102@spoit.test', 'club-user-103@spoit.test', 'club-user-104@spoit.test', 'club-user-105@spoit.test', 'club-user-106@spoit.test', 'club-user-107@spoit.test', 'club-user-108@spoit.test', 'club-user-109@spoit.test', 'club-user-110@spoit.test', 'club-user-111@spoit.test', 'club-user-112@spoit.test', 'club-user-113@spoit.test', 'club-user-114@spoit.test', 'club-user-115@spoit.test', 'club-user-116@spoit.test', 'club-user-117@spoit.test', 'club-user-118@spoit.test')),
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
                 SELECT (@cx_u + 1) AS user_id, 'club-user-001@spoit.test' AS email, '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi' AS password_hash, '송지호' AS nickname, '010-8701-1064' AS phone, 'MEMBER' AS role, '2026-04-22 10:00:00' AS created_at
       UNION ALL SELECT (@cx_u + 2), 'club-user-002@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '신건우', '010-8702-2929', 'MEMBER', '2026-05-22 21:00:00'
       UNION ALL SELECT (@cx_u + 3), 'club-user-003@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '신상훈', '010-8703-5324', 'MEMBER', '2026-06-19 07:00:00'
       UNION ALL SELECT (@cx_u + 4), 'club-user-004@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '장지훈', '010-8704-5535', 'MEMBER', '2026-06-22 03:00:00'
       UNION ALL SELECT (@cx_u + 5), 'club-user-005@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '오서윤', '010-8705-6945', 'MEMBER', '2026-06-22 09:30:00'
       UNION ALL SELECT (@cx_u + 6), 'club-user-006@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '서준서', '010-8706-2468', 'MEMBER', '2026-07-28 03:00:00'
       UNION ALL SELECT (@cx_u + 7), 'club-user-007@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '강선우', '010-8707-6424', 'MEMBER', '2026-05-08 14:00:00'
       UNION ALL SELECT (@cx_u + 8), 'club-user-008@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '허선우', '010-8708-3105', 'MEMBER', '2026-06-17 15:30:00'
       UNION ALL SELECT (@cx_u + 9), 'club-user-009@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '한준혁', '010-8709-8806', 'MEMBER', '2026-07-18 15:00:00'
       UNION ALL SELECT (@cx_u + 10), 'club-user-010@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '김민지', '010-8710-1549', 'MEMBER', '2026-07-15 19:00:00'
       UNION ALL SELECT (@cx_u + 11), 'club-user-011@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '허지환', '010-8711-2930', 'MEMBER', '2026-04-29 11:30:00'
       UNION ALL SELECT (@cx_u + 12), 'club-user-012@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '강대현', '010-8712-3882', 'MEMBER', '2026-05-09 16:00:00'
       UNION ALL SELECT (@cx_u + 13), 'club-user-013@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '조재민', '010-8713-7414', 'MEMBER', '2026-05-10 18:30:00'
       UNION ALL SELECT (@cx_u + 14), 'club-user-014@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '정재민', '010-8714-2941', 'MEMBER', '2026-07-06 10:30:00'
       UNION ALL SELECT (@cx_u + 15), 'club-user-015@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '홍지우', '010-8715-4398', 'MEMBER', '2026-05-28 16:00:00'
       UNION ALL SELECT (@cx_u + 16), 'club-user-016@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '윤하은', '010-8716-9867', 'MEMBER', '2026-06-05 14:00:00'
       UNION ALL SELECT (@cx_u + 17), 'club-user-017@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '강소율', '010-8717-3854', 'MEMBER', '2026-06-13 04:30:00'
       UNION ALL SELECT (@cx_u + 18), 'club-user-018@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '권지훈', '010-8718-4206', 'MEMBER', '2026-06-30 12:30:00'
       UNION ALL SELECT (@cx_u + 19), 'club-user-019@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '백지훈', '010-8719-8725', 'MEMBER', '2026-04-20 16:00:00'
       UNION ALL SELECT (@cx_u + 20), 'club-user-020@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '서지환', '010-8720-4525', 'MEMBER', '2026-05-12 16:30:00'
       UNION ALL SELECT (@cx_u + 21), 'club-user-021@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '박현준', '010-8721-1658', 'MEMBER', '2026-04-30 13:30:00'
       UNION ALL SELECT (@cx_u + 22), 'club-user-022@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '황영호', '010-8722-5954', 'MEMBER', '2026-06-15 10:00:00'
       UNION ALL SELECT (@cx_u + 23), 'club-user-023@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '강하준', '010-8723-8282', 'MEMBER', '2026-04-16 06:30:00'
       UNION ALL SELECT (@cx_u + 24), 'club-user-024@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '양서준', '010-8724-7674', 'MEMBER', '2026-06-03 12:00:00'
       UNION ALL SELECT (@cx_u + 25), 'club-user-025@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '이형준', '010-8725-3264', 'MEMBER', '2026-06-12 12:30:00'
       UNION ALL SELECT (@cx_u + 26), 'club-user-026@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '정상훈', '010-8726-5293', 'MEMBER', '2026-06-17 07:30:00'
       UNION ALL SELECT (@cx_u + 27), 'club-user-027@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '유건우', '010-8727-9623', 'MEMBER', '2026-06-04 13:00:00'
       UNION ALL SELECT (@cx_u + 28), 'club-user-028@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '배건우', '010-8728-5470', 'MEMBER', '2026-06-03 09:00:00'
       UNION ALL SELECT (@cx_u + 29), 'club-user-029@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '한대현', '010-8729-2535', 'MEMBER', '2026-07-09 11:30:00'
       UNION ALL SELECT (@cx_u + 30), 'club-user-030@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '고서연', '010-8730-4491', 'MEMBER', '2026-07-08 16:30:00'
       UNION ALL SELECT (@cx_u + 31), 'club-user-031@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '이정우', '010-8731-6172', 'MEMBER', '2026-04-24 16:30:00'
       UNION ALL SELECT (@cx_u + 32), 'club-user-032@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '배민서', '010-8732-1042', 'MEMBER', '2026-05-18 08:30:00'
       UNION ALL SELECT (@cx_u + 33), 'club-user-033@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '백도윤', '010-8733-1808', 'MEMBER', '2026-06-01 08:00:00'
       UNION ALL SELECT (@cx_u + 34), 'club-user-034@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '오하은', '010-8734-4110', 'MEMBER', '2026-06-03 07:30:00'
       UNION ALL SELECT (@cx_u + 35), 'club-user-035@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '전건우', '010-8735-1828', 'MEMBER', '2026-07-03 15:00:00'
       UNION ALL SELECT (@cx_u + 36), 'club-user-036@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '이상훈', '010-8736-1193', 'MEMBER', '2026-06-26 04:00:00'
       UNION ALL SELECT (@cx_u + 37), 'club-user-037@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '장서연', '010-8737-4511', 'MEMBER', '2026-04-17 03:00:00'
       UNION ALL SELECT (@cx_u + 38), 'club-user-038@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '고다은', '010-8738-2518', 'MEMBER', '2026-05-06 13:00:00'
       UNION ALL SELECT (@cx_u + 39), 'club-user-039@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '남태현', '010-8739-8429', 'MEMBER', '2026-06-03 10:00:00'
       UNION ALL SELECT (@cx_u + 40), 'club-user-040@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '유경민', '010-8740-1692', 'MEMBER', '2026-08-03 04:00:00'
       UNION ALL SELECT (@cx_u + 41), 'club-user-041@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '문건우', '010-8741-7064', 'MEMBER', '2026-07-05 18:00:00'
       UNION ALL SELECT (@cx_u + 42), 'club-user-042@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '박하은', '010-8742-3747', 'MEMBER', '2026-07-07 04:30:00'
       UNION ALL SELECT (@cx_u + 43), 'club-user-043@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '전다은', '010-8743-1810', 'MEMBER', '2026-08-28 12:30:00'
       UNION ALL SELECT (@cx_u + 44), 'club-user-044@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '고재민', '010-8744-3108', 'MEMBER', '2026-09-02 12:00:00'
       UNION ALL SELECT (@cx_u + 45), 'club-user-045@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '홍민서', '010-8745-5189', 'MEMBER', '2026-08-21 22:00:00'
       UNION ALL SELECT (@cx_u + 46), 'club-user-046@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '안건우', '010-8746-1696', 'MEMBER', '2026-08-20 08:30:00'
       UNION ALL SELECT (@cx_u + 47), 'club-user-047@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '남민준', '010-8747-3017', 'MEMBER', '2026-09-12 13:30:00'
       UNION ALL SELECT (@cx_u + 48), 'club-user-048@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '최준서', '010-8748-6813', 'MEMBER', '2026-05-04 11:30:00'
       UNION ALL SELECT (@cx_u + 49), 'club-user-049@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '손건우', '010-8749-2230', 'MEMBER', '2026-04-24 07:30:00'
       UNION ALL SELECT (@cx_u + 50), 'club-user-050@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '김서연', '010-8750-6921', 'MEMBER', '2026-06-24 07:30:00'
       UNION ALL SELECT (@cx_u + 51), 'club-user-051@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '윤현준', '010-8751-3519', 'MEMBER', '2026-06-15 07:30:00'
       UNION ALL SELECT (@cx_u + 52), 'club-user-052@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '황지아', '010-8752-5184', 'MEMBER', '2026-05-16 14:00:00'
       UNION ALL SELECT (@cx_u + 53), 'club-user-053@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '문준서', '010-8753-7854', 'MEMBER', '2026-06-03 08:00:00'
       UNION ALL SELECT (@cx_u + 54), 'club-user-054@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '강준우', '010-8754-4763', 'MEMBER', '2026-06-07 16:30:00'
       UNION ALL SELECT (@cx_u + 55), 'club-user-055@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '박승우', '010-8755-6394', 'MEMBER', '2026-06-18 12:00:00'
       UNION ALL SELECT (@cx_u + 56), 'club-user-056@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '이민재', '010-8756-3272', 'MEMBER', '2026-04-21 06:30:00'
       UNION ALL SELECT (@cx_u + 57), 'club-user-057@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '송지우', '010-8757-9044', 'MEMBER', '2026-05-07 20:30:00'
       UNION ALL SELECT (@cx_u + 58), 'club-user-058@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '양동현', '010-8758-7819', 'MEMBER', '2026-05-11 10:00:00'
       UNION ALL SELECT (@cx_u + 59), 'club-user-059@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '황지민', '010-8759-8461', 'MEMBER', '2026-06-09 10:30:00'
       UNION ALL SELECT (@cx_u + 60), 'club-user-060@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '임현준', '010-8760-9170', 'MEMBER', '2026-06-20 13:30:00'
       UNION ALL SELECT (@cx_u + 61), 'club-user-061@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '임상훈', '010-8761-9323', 'MEMBER', '2026-07-09 10:00:00'
       UNION ALL SELECT (@cx_u + 62), 'club-user-062@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '임하준', '010-8762-7367', 'MEMBER', '2026-04-27 16:30:00'
       UNION ALL SELECT (@cx_u + 63), 'club-user-063@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '유서진', '010-8763-8184', 'MEMBER', '2026-04-10 15:30:00'
       UNION ALL SELECT (@cx_u + 64), 'club-user-064@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '허준우', '010-8764-1281', 'MEMBER', '2026-06-24 09:30:00'
       UNION ALL SELECT (@cx_u + 65), 'club-user-065@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '강우진', '010-8765-9222', 'MEMBER', '2026-06-29 15:00:00'
       UNION ALL SELECT (@cx_u + 66), 'club-user-066@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '유태현', '010-8766-8966', 'MEMBER', '2026-04-05 22:30:00'
       UNION ALL SELECT (@cx_u + 67), 'club-user-067@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '이우진', '010-8767-4574', 'MEMBER', '2026-04-25 07:30:00'
       UNION ALL SELECT (@cx_u + 68), 'club-user-068@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '홍시윤', '010-8768-2802', 'MEMBER', '2026-06-01 18:30:00'
       UNION ALL SELECT (@cx_u + 69), 'club-user-069@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '허건우', '010-8769-4946', 'MEMBER', '2026-07-15 13:00:00'
       UNION ALL SELECT (@cx_u + 70), 'club-user-070@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '백예린', '010-8770-6494', 'MEMBER', '2026-05-04 11:00:00'
       UNION ALL SELECT (@cx_u + 71), 'club-user-071@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '손성민', '010-8771-1834', 'MEMBER', '2026-06-07 03:00:00'
       UNION ALL SELECT (@cx_u + 72), 'club-user-072@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '조지민', '010-8772-4411', 'MEMBER', '2026-06-14 17:00:00'
       UNION ALL SELECT (@cx_u + 73), 'club-user-073@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '황민재', '010-8773-9736', 'MEMBER', '2026-06-24 05:30:00'
       UNION ALL SELECT (@cx_u + 74), 'club-user-074@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '양정우', '010-8774-2512', 'MEMBER', '2026-08-11 17:30:00'
       UNION ALL SELECT (@cx_u + 75), 'club-user-075@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '신지아', '010-8775-3370', 'MEMBER', '2026-08-12 08:00:00'
       UNION ALL SELECT (@cx_u + 76), 'club-user-076@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '임예린', '010-8776-7602', 'MEMBER', '2026-08-31 09:00:00'
       UNION ALL SELECT (@cx_u + 77), 'club-user-077@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '전서연', '010-8777-9611', 'MEMBER', '2026-08-21 05:00:00'
       UNION ALL SELECT (@cx_u + 78), 'club-user-078@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '권연우', '010-8778-2152', 'MEMBER', '2026-09-08 10:00:00'
       UNION ALL SELECT (@cx_u + 79), 'club-user-079@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '홍정우', '010-8779-5868', 'MEMBER', '2026-08-29 08:30:00'
       UNION ALL SELECT (@cx_u + 80), 'club-user-080@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '서태윤', '010-8780-7505', 'MEMBER', '2026-09-14 16:30:00'
       UNION ALL SELECT (@cx_u + 81), 'club-user-081@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '홍도윤', '010-8781-4249', 'MEMBER', '2026-09-03 08:00:00'
       UNION ALL SELECT (@cx_u + 82), 'club-user-082@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '송민준', '010-8782-7028', 'MEMBER', '2026-08-31 15:00:00'
       UNION ALL SELECT (@cx_u + 83), 'club-user-083@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '백채원', '010-8783-8577', 'MEMBER', '2026-08-21 12:00:00'
       UNION ALL SELECT (@cx_u + 84), 'club-user-084@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '정서진', '010-8784-8558', 'MEMBER', '2026-08-20 14:30:00'
       UNION ALL SELECT (@cx_u + 85), 'club-user-085@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '홍하린', '010-8785-5850', 'MEMBER', '2026-09-20 20:30:00'
       UNION ALL SELECT (@cx_u + 86), 'club-user-086@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '한서진', '010-8786-8901', 'MEMBER', '2026-08-30 15:30:00'
       UNION ALL SELECT (@cx_u + 87), 'club-user-087@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '조예린', '010-8787-1294', 'MEMBER', '2026-08-26 10:00:00'
       UNION ALL SELECT (@cx_u + 88), 'club-user-088@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '안예준', '010-8788-5579', 'MEMBER', '2026-09-04 08:00:00'
       UNION ALL SELECT (@cx_u + 89), 'club-user-089@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '한예준', '010-8789-6378', 'MEMBER', '2026-09-07 13:00:00'
       UNION ALL SELECT (@cx_u + 90), 'club-user-090@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '오지훈', '010-8790-9218', 'MEMBER', '2026-09-09 14:30:00'
       UNION ALL SELECT (@cx_u + 91), 'club-user-091@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '전도윤', '010-8791-9276', 'MEMBER', '2026-08-19 04:30:00'
       UNION ALL SELECT (@cx_u + 92), 'club-user-092@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '최준우', '010-8792-8352', 'MEMBER', '2026-09-17 16:00:00'
       UNION ALL SELECT (@cx_u + 93), 'club-user-093@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '홍서윤', '010-8793-8306', 'MEMBER', '2026-09-02 04:30:00'
       UNION ALL SELECT (@cx_u + 94), 'club-user-094@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '오서진', '010-8794-4919', 'MEMBER', '2026-09-09 05:00:00'
       UNION ALL SELECT (@cx_u + 95), 'club-user-095@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '권서연', '010-8795-6888', 'MEMBER', '2026-08-31 11:30:00'
       UNION ALL SELECT (@cx_u + 96), 'club-user-096@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '허승민', '010-8796-8297', 'MEMBER', '2026-08-17 15:00:00'
       UNION ALL SELECT (@cx_u + 97), 'club-user-097@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '한예은', '010-8797-8371', 'MEMBER', '2026-08-16 17:30:00'
       UNION ALL SELECT (@cx_u + 98), 'club-user-098@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '전선우', '010-8798-1958', 'MEMBER', '2026-08-23 16:00:00'
       UNION ALL SELECT (@cx_u + 99), 'club-user-099@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '김수빈', '010-8799-2628', 'MEMBER', '2026-08-30 03:30:00'
       UNION ALL SELECT (@cx_u + 100), 'club-user-100@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '이다은', '010-8700-4785', 'MEMBER', '2026-09-22 07:00:00'
       UNION ALL SELECT (@cx_u + 101), 'club-user-101@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '안준서', '010-8701-9023', 'MEMBER', '2026-09-05 06:00:00'
       UNION ALL SELECT (@cx_u + 102), 'club-user-102@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '남건우', '010-8702-1634', 'MEMBER', '2026-09-09 03:30:00'
       UNION ALL SELECT (@cx_u + 103), 'club-user-103@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '김다은', '010-8703-9851', 'MEMBER', '2026-08-24 06:30:00'
       UNION ALL SELECT (@cx_u + 104), 'club-user-104@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '장경민', '010-8704-6722', 'MEMBER', '2026-09-14 08:30:00'
       UNION ALL SELECT (@cx_u + 105), 'club-user-105@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '안예은', '010-8705-9983', 'MEMBER', '2026-09-11 15:30:00'
       UNION ALL SELECT (@cx_u + 106), 'club-user-106@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '김지우', '010-8706-3258', 'MEMBER', '2026-09-06 10:00:00'
       UNION ALL SELECT (@cx_u + 107), 'club-user-107@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '윤하준', '010-8707-9813', 'MEMBER', '2026-09-04 19:00:00'
       UNION ALL SELECT (@cx_u + 108), 'club-user-108@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '안예린', '010-8708-4808', 'MEMBER', '2026-08-27 10:00:00'
       UNION ALL SELECT (@cx_u + 109), 'club-user-109@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '임도현', '010-8709-1751', 'MEMBER', '2026-09-22 05:00:00'
       UNION ALL SELECT (@cx_u + 110), 'club-user-110@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '정민준', '010-8710-7235', 'MEMBER', '2026-09-14 09:00:00'
       UNION ALL SELECT (@cx_u + 111), 'club-user-111@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '홍은우', '010-8711-8580', 'MEMBER', '2026-09-19 11:00:00'
       UNION ALL SELECT (@cx_u + 112), 'club-user-112@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '송하린', '010-8712-3039', 'MEMBER', '2026-09-11 07:30:00'
       UNION ALL SELECT (@cx_u + 113), 'club-user-113@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '김지아', '010-8713-1593', 'MEMBER', '2026-09-21 12:00:00'
       UNION ALL SELECT (@cx_u + 114), 'club-user-114@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '최연우', '010-8714-3708', 'MEMBER', '2026-09-09 08:00:00'
       UNION ALL SELECT (@cx_u + 115), 'club-user-115@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '정은우', '010-8715-4257', 'MEMBER', '2026-09-11 11:00:00'
       UNION ALL SELECT (@cx_u + 116), 'club-user-116@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '손지후', '010-8716-5172', 'MEMBER', '2026-10-01 18:30:00'
       UNION ALL SELECT (@cx_u + 117), 'club-user-117@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '김정우', '010-8717-3869', 'MEMBER', '2026-09-11 11:30:00'
       UNION ALL SELECT (@cx_u + 118), 'club-user-118@spoit.test', '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi', '오민서', '010-8718-7285', 'MEMBER', '2026-09-21 17:30:00'
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
       UNION ALL SELECT (@cx_u + 41), @cx_r_은평구
       UNION ALL SELECT (@cx_u + 42), @cx_r_은평구
       UNION ALL SELECT (@cx_u + 43), @cx_r_은평구
       UNION ALL SELECT (@cx_u + 44), @cx_r_은평구
       UNION ALL SELECT (@cx_u + 45), @cx_r_은평구
       UNION ALL SELECT (@cx_u + 46), @cx_r_은평구
       UNION ALL SELECT (@cx_u + 47), @cx_r_은평구
       UNION ALL SELECT (@cx_u + 48), @cx_r_광진구
       UNION ALL SELECT (@cx_u + 49), @cx_r_광진구
       UNION ALL SELECT (@cx_u + 50), @cx_r_광진구
       UNION ALL SELECT (@cx_u + 51), @cx_r_광진구
       UNION ALL SELECT (@cx_u + 52), @cx_r_강서구
       UNION ALL SELECT (@cx_u + 53), @cx_r_강서구
       UNION ALL SELECT (@cx_u + 54), @cx_r_강서구
       UNION ALL SELECT (@cx_u + 55), @cx_r_강서구
       UNION ALL SELECT (@cx_u + 56), @cx_r_영등포구
       UNION ALL SELECT (@cx_u + 57), @cx_r_영등포구
       UNION ALL SELECT (@cx_u + 58), @cx_r_영등포구
       UNION ALL SELECT (@cx_u + 59), @cx_r_영등포구
       UNION ALL SELECT (@cx_u + 60), @cx_r_영등포구
       UNION ALL SELECT (@cx_u + 61), @cx_r_영등포구
       UNION ALL SELECT (@cx_u + 62), @cx_r_관악구
       UNION ALL SELECT (@cx_u + 63), @cx_r_관악구
       UNION ALL SELECT (@cx_u + 64), @cx_r_관악구
       UNION ALL SELECT (@cx_u + 65), @cx_r_관악구
       UNION ALL SELECT (@cx_u + 66), @cx_r_강서구
       UNION ALL SELECT (@cx_u + 67), @cx_r_강서구
       UNION ALL SELECT (@cx_u + 68), @cx_r_강서구
       UNION ALL SELECT (@cx_u + 69), @cx_r_강서구
       UNION ALL SELECT (@cx_u + 70), @cx_r_송파구
       UNION ALL SELECT (@cx_u + 71), @cx_r_송파구
       UNION ALL SELECT (@cx_u + 72), @cx_r_송파구
       UNION ALL SELECT (@cx_u + 73), @cx_r_송파구
       UNION ALL SELECT (@cx_u + 74), @cx_r_광진구
       UNION ALL SELECT (@cx_u + 75), @cx_r_광진구
       UNION ALL SELECT (@cx_u + 76), @cx_r_광진구
       UNION ALL SELECT (@cx_u + 77), @cx_r_광진구
       UNION ALL SELECT (@cx_u + 78), @cx_r_광진구
       UNION ALL SELECT (@cx_u + 79), @cx_r_광진구
       UNION ALL SELECT (@cx_u + 80), @cx_r_광진구
       UNION ALL SELECT (@cx_u + 81), @cx_r_마포구
       UNION ALL SELECT (@cx_u + 82), @cx_r_마포구
       UNION ALL SELECT (@cx_u + 83), @cx_r_마포구
       UNION ALL SELECT (@cx_u + 84), @cx_r_마포구
       UNION ALL SELECT (@cx_u + 85), @cx_r_마포구
       UNION ALL SELECT (@cx_u + 86), @cx_r_마포구
       UNION ALL SELECT (@cx_u + 87), @cx_r_마포구
       UNION ALL SELECT (@cx_u + 88), @cx_r_송파구
       UNION ALL SELECT (@cx_u + 89), @cx_r_송파구
       UNION ALL SELECT (@cx_u + 90), @cx_r_송파구
       UNION ALL SELECT (@cx_u + 91), @cx_r_송파구
       UNION ALL SELECT (@cx_u + 92), @cx_r_송파구
       UNION ALL SELECT (@cx_u + 93), @cx_r_송파구
       UNION ALL SELECT (@cx_u + 94), @cx_r_송파구
       UNION ALL SELECT (@cx_u + 95), @cx_r_서초구
       UNION ALL SELECT (@cx_u + 96), @cx_r_서초구
       UNION ALL SELECT (@cx_u + 97), @cx_r_서초구
       UNION ALL SELECT (@cx_u + 98), @cx_r_서초구
       UNION ALL SELECT (@cx_u + 99), @cx_r_서초구
       UNION ALL SELECT (@cx_u + 100), @cx_r_서초구
       UNION ALL SELECT (@cx_u + 101), @cx_r_강동구
       UNION ALL SELECT (@cx_u + 102), @cx_r_강동구
       UNION ALL SELECT (@cx_u + 103), @cx_r_강동구
       UNION ALL SELECT (@cx_u + 104), @cx_r_강동구
       UNION ALL SELECT (@cx_u + 105), @cx_r_강동구
       UNION ALL SELECT (@cx_u + 106), @cx_r_강동구
       UNION ALL SELECT (@cx_u + 107), @cx_r_광진구
       UNION ALL SELECT (@cx_u + 108), @cx_r_광진구
       UNION ALL SELECT (@cx_u + 109), @cx_r_광진구
       UNION ALL SELECT (@cx_u + 110), @cx_r_광진구
       UNION ALL SELECT (@cx_u + 111), @cx_r_광진구
       UNION ALL SELECT (@cx_u + 112), @cx_r_광진구
       UNION ALL SELECT (@cx_u + 113), @cx_r_광진구
       UNION ALL SELECT (@cx_u + 114), @cx_r_광진구
       UNION ALL SELECT (@cx_u + 115), @cx_r_마포구
       UNION ALL SELECT (@cx_u + 116), @cx_r_마포구
       UNION ALL SELECT (@cx_u + 117), @cx_r_마포구
       UNION ALL SELECT (@cx_u + 118), @cx_r_마포구
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
       UNION ALL SELECT (@cx_u + 41), @cx_s_축구
       UNION ALL SELECT (@cx_u + 42), @cx_s_축구
       UNION ALL SELECT (@cx_u + 43), @cx_s_축구
       UNION ALL SELECT (@cx_u + 44), @cx_s_축구
       UNION ALL SELECT (@cx_u + 45), @cx_s_축구
       UNION ALL SELECT (@cx_u + 46), @cx_s_축구
       UNION ALL SELECT (@cx_u + 47), @cx_s_축구
       UNION ALL SELECT (@cx_u + 48), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 49), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 50), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 51), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 52), @cx_s_배드민턴
       UNION ALL SELECT (@cx_u + 53), @cx_s_배드민턴
       UNION ALL SELECT (@cx_u + 54), @cx_s_배드민턴
       UNION ALL SELECT (@cx_u + 55), @cx_s_배드민턴
       UNION ALL SELECT (@cx_u + 56), @cx_s_농구
       UNION ALL SELECT (@cx_u + 57), @cx_s_농구
       UNION ALL SELECT (@cx_u + 58), @cx_s_농구
       UNION ALL SELECT (@cx_u + 59), @cx_s_농구
       UNION ALL SELECT (@cx_u + 60), @cx_s_농구
       UNION ALL SELECT (@cx_u + 61), @cx_s_농구
       UNION ALL SELECT (@cx_u + 62), @cx_s_족구
       UNION ALL SELECT (@cx_u + 63), @cx_s_족구
       UNION ALL SELECT (@cx_u + 64), @cx_s_족구
       UNION ALL SELECT (@cx_u + 65), @cx_s_족구
       UNION ALL SELECT (@cx_u + 66), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 67), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 68), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 69), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 70), @cx_s_배드민턴
       UNION ALL SELECT (@cx_u + 71), @cx_s_배드민턴
       UNION ALL SELECT (@cx_u + 72), @cx_s_배드민턴
       UNION ALL SELECT (@cx_u + 73), @cx_s_배드민턴
       UNION ALL SELECT (@cx_u + 74), @cx_s_축구
       UNION ALL SELECT (@cx_u + 75), @cx_s_축구
       UNION ALL SELECT (@cx_u + 76), @cx_s_축구
       UNION ALL SELECT (@cx_u + 77), @cx_s_축구
       UNION ALL SELECT (@cx_u + 78), @cx_s_축구
       UNION ALL SELECT (@cx_u + 79), @cx_s_축구
       UNION ALL SELECT (@cx_u + 80), @cx_s_축구
       UNION ALL SELECT (@cx_u + 81), @cx_s_축구
       UNION ALL SELECT (@cx_u + 82), @cx_s_축구
       UNION ALL SELECT (@cx_u + 83), @cx_s_축구
       UNION ALL SELECT (@cx_u + 84), @cx_s_축구
       UNION ALL SELECT (@cx_u + 85), @cx_s_축구
       UNION ALL SELECT (@cx_u + 86), @cx_s_축구
       UNION ALL SELECT (@cx_u + 87), @cx_s_축구
       UNION ALL SELECT (@cx_u + 88), @cx_s_축구
       UNION ALL SELECT (@cx_u + 89), @cx_s_축구
       UNION ALL SELECT (@cx_u + 90), @cx_s_축구
       UNION ALL SELECT (@cx_u + 91), @cx_s_축구
       UNION ALL SELECT (@cx_u + 92), @cx_s_축구
       UNION ALL SELECT (@cx_u + 93), @cx_s_축구
       UNION ALL SELECT (@cx_u + 94), @cx_s_축구
       UNION ALL SELECT (@cx_u + 95), @cx_s_농구
       UNION ALL SELECT (@cx_u + 96), @cx_s_농구
       UNION ALL SELECT (@cx_u + 97), @cx_s_농구
       UNION ALL SELECT (@cx_u + 98), @cx_s_농구
       UNION ALL SELECT (@cx_u + 99), @cx_s_농구
       UNION ALL SELECT (@cx_u + 100), @cx_s_농구
       UNION ALL SELECT (@cx_u + 101), @cx_s_농구
       UNION ALL SELECT (@cx_u + 102), @cx_s_농구
       UNION ALL SELECT (@cx_u + 103), @cx_s_농구
       UNION ALL SELECT (@cx_u + 104), @cx_s_농구
       UNION ALL SELECT (@cx_u + 105), @cx_s_농구
       UNION ALL SELECT (@cx_u + 106), @cx_s_농구
       UNION ALL SELECT (@cx_u + 107), @cx_s_족구
       UNION ALL SELECT (@cx_u + 108), @cx_s_족구
       UNION ALL SELECT (@cx_u + 109), @cx_s_족구
       UNION ALL SELECT (@cx_u + 110), @cx_s_족구
       UNION ALL SELECT (@cx_u + 111), @cx_s_배드민턴
       UNION ALL SELECT (@cx_u + 112), @cx_s_배드민턴
       UNION ALL SELECT (@cx_u + 113), @cx_s_배드민턴
       UNION ALL SELECT (@cx_u + 114), @cx_s_배드민턴
       UNION ALL SELECT (@cx_u + 115), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 116), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 117), @cx_s_테니스
       UNION ALL SELECT (@cx_u + 118), @cx_s_테니스
) v
 WHERE @cx_ready = 1;

-- 종목별 레이팅 = 마지막 레이팅 이력. rated_match_count가 5 이상이면 배치 완료로 팀 등급 평균에 들어간다.
INSERT INTO sport_rating (user_id, sport_id, current_mmr, rated_match_count, updated_at)
SELECT * FROM (
                 SELECT (@cx_u + 1) AS user_id, @cx_s_농구 AS sport_id, 895.16 AS current_mmr, 6 AS rated_match_count, '2026-10-26 11:00:00' AS updated_at
       UNION ALL SELECT (@cx_u + 2), @cx_s_농구, 895.16, 6, '2026-10-26 11:00:00'
       UNION ALL SELECT (@cx_u + 3), @cx_s_농구, 918.67, 3, '2026-10-26 11:00:00'
       UNION ALL SELECT (@cx_u + 4), @cx_s_농구, 976.72, 3, '2026-10-18 20:00:00'
       UNION ALL SELECT (@cx_u + 5), @cx_s_농구, 903.61, 3, '2026-10-18 20:00:00'
       UNION ALL SELECT (@cx_u + 6), @cx_s_농구, 970.48, 3, '2026-10-26 11:00:00'
       UNION ALL SELECT (@cx_u + 7), @cx_s_테니스, 1051.66, 6, '2026-10-17 09:00:00'
       UNION ALL SELECT (@cx_u + 8), @cx_s_테니스, 989.79, 2, '2026-10-08 22:00:00'
       UNION ALL SELECT (@cx_u + 9), @cx_s_테니스, 992.77, 2, '2026-10-17 09:00:00'
       UNION ALL SELECT (@cx_u + 10), @cx_s_테니스, 1051.58, 2, '2026-09-12 22:00:00'
       UNION ALL SELECT (@cx_u + 11), @cx_s_테니스, 990.64, 6, '2026-10-21 23:00:00'
       UNION ALL SELECT (@cx_u + 12), @cx_s_테니스, 1064.97, 2, '2026-09-25 21:30:00'
       UNION ALL SELECT (@cx_u + 13), @cx_s_테니스, 1004.34, 2, '2026-10-21 23:00:00'
       UNION ALL SELECT (@cx_u + 14), @cx_s_테니스, 940.23, 2, '2026-10-14 23:00:00'
       UNION ALL SELECT (@cx_u + 15), @cx_s_배드민턴, 1050.65, 6, '2026-10-28 20:00:00'
       UNION ALL SELECT (@cx_u + 16), @cx_s_배드민턴, 999.98, 2, '2026-10-15 21:00:00'
       UNION ALL SELECT (@cx_u + 17), @cx_s_배드민턴, 1000.86, 2, '2026-10-24 20:00:00'
       UNION ALL SELECT (@cx_u + 18), @cx_s_배드민턴, 1067.63, 2, '2026-10-28 20:00:00'
       UNION ALL SELECT (@cx_u + 19), @cx_s_배드민턴, 1013.25, 6, '2026-10-17 22:00:00'
       UNION ALL SELECT (@cx_u + 20), @cx_s_배드민턴, 989.99, 2, '2026-10-17 22:00:00'
       UNION ALL SELECT (@cx_u + 21), @cx_s_배드민턴, 1004.53, 2, '2026-09-30 22:00:00'
       UNION ALL SELECT (@cx_u + 22), @cx_s_배드민턴, 998.46, 2, '2026-09-16 22:00:00'
       UNION ALL SELECT (@cx_u + 23), @cx_s_테니스, 940.01, 6, '2026-11-06 21:30:00'
       UNION ALL SELECT (@cx_u + 24), @cx_s_테니스, 940.72, 2, '2026-09-25 21:30:00'
       UNION ALL SELECT (@cx_u + 25), @cx_s_테니스, 945.71, 2, '2026-11-06 21:30:00'
       UNION ALL SELECT (@cx_u + 26), @cx_s_테니스, 1040.34, 2, '2026-10-19 10:00:00'
       UNION ALL SELECT (@cx_u + 27), @cx_s_테니스, 944.33, 6, '2026-10-25 12:00:00'
       UNION ALL SELECT (@cx_u + 28), @cx_s_테니스, 946.95, 2, '2026-09-12 22:00:00'
       UNION ALL SELECT (@cx_u + 29), @cx_s_테니스, 943.74, 2, '2026-10-25 12:00:00'
       UNION ALL SELECT (@cx_u + 30), @cx_s_테니스, 1040.74, 2, '2026-10-08 22:00:00'
       UNION ALL SELECT (@cx_u + 31), @cx_s_농구, 951.19, 6, '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_u + 32), @cx_s_농구, 951.19, 6, '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_u + 33), @cx_s_농구, 985.74, 3, '2026-10-04 11:00:00'
       UNION ALL SELECT (@cx_u + 34), @cx_s_농구, 985.52, 3, '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_u + 35), @cx_s_농구, 985.17, 3, '2026-10-04 11:00:00'
       UNION ALL SELECT (@cx_u + 36), @cx_s_농구, 984.95, 3, '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_u + 37), @cx_s_배드민턴, 1002.91, 6, '2026-10-25 11:00:00'
       UNION ALL SELECT (@cx_u + 38), @cx_s_배드민턴, 1056.05, 2, '2026-10-25 11:00:00'
       UNION ALL SELECT (@cx_u + 39), @cx_s_배드민턴, 963.29, 2, '2026-10-19 12:00:00'
       UNION ALL SELECT (@cx_u + 40), @cx_s_배드민턴, 997.03, 2, '2026-10-15 21:00:00'
       UNION ALL SELECT (@cx_u + 41), @cx_s_축구, 965.20, 6, '2026-10-22 22:00:00'
       UNION ALL SELECT (@cx_u + 42), @cx_s_축구, 965.20, 6, '2026-10-22 22:00:00'
       UNION ALL SELECT (@cx_u + 43), @cx_s_축구, 965.20, 6, '2026-10-22 22:00:00'
       UNION ALL SELECT (@cx_u + 44), @cx_s_축구, 974.27, 3, '2026-10-22 22:00:00'
       UNION ALL SELECT (@cx_u + 45), @cx_s_축구, 1043.17, 3, '2026-10-18 16:00:00'
       UNION ALL SELECT (@cx_u + 46), @cx_s_축구, 910.68, 3, '2026-10-22 22:00:00'
       UNION ALL SELECT (@cx_u + 47), @cx_s_축구, 975.22, 3, '2026-10-18 16:00:00'
       UNION ALL SELECT (@cx_u + 48), @cx_s_테니스, 1014.56, 6, '2026-10-19 10:00:00'
       UNION ALL SELECT (@cx_u + 49), @cx_s_테니스, 1065.13, 2, '2026-10-05 10:00:00'
       UNION ALL SELECT (@cx_u + 50), @cx_s_테니스, 994.60, 2, '2026-10-19 10:00:00'
       UNION ALL SELECT (@cx_u + 51), @cx_s_테니스, 935.40, 2, '2026-09-20 10:00:00'
       UNION ALL SELECT (@cx_u + 52), @cx_s_배드민턴, 937.67, 6, '2026-10-16 22:00:00'
       UNION ALL SELECT (@cx_u + 53), @cx_s_배드민턴, 1005.60, 2, '2026-09-14 11:00:00'
       UNION ALL SELECT (@cx_u + 54), @cx_s_배드민턴, 977.56, 2, '2026-10-07 22:00:00'
       UNION ALL SELECT (@cx_u + 55), @cx_s_배드민턴, 941.01, 2, '2026-10-16 22:00:00'
       UNION ALL SELECT (@cx_u + 56), @cx_s_농구, 1079.94, 6, '2026-10-26 11:00:00'
       UNION ALL SELECT (@cx_u + 57), @cx_s_농구, 1079.94, 6, '2026-10-26 11:00:00'
       UNION ALL SELECT (@cx_u + 58), @cx_s_농구, 1016.05, 3, '2026-10-03 21:00:00'
       UNION ALL SELECT (@cx_u + 59), @cx_s_농구, 1074.24, 3, '2026-10-26 11:00:00'
       UNION ALL SELECT (@cx_u + 60), @cx_s_농구, 1014.08, 3, '2026-10-03 21:00:00'
       UNION ALL SELECT (@cx_u + 61), @cx_s_농구, 1076.35, 3, '2026-10-26 11:00:00'
       UNION ALL SELECT (@cx_u + 62), @cx_s_족구, 994.02, 5, '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_u + 63), @cx_s_족구, 994.02, 5, '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_u + 64), @cx_s_족구, 1029.22, 2, '2026-11-01 11:00:00'
       UNION ALL SELECT (@cx_u + 65), @cx_s_족구, 964.80, 3, '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_u + 66), @cx_s_테니스, 1007.39, 6, '2026-10-29 22:00:00'
       UNION ALL SELECT (@cx_u + 67), @cx_s_테니스, 995.79, 2, '2026-10-05 10:00:00'
       UNION ALL SELECT (@cx_u + 68), @cx_s_테니스, 1001.38, 2, '2026-10-29 22:00:00'
       UNION ALL SELECT (@cx_u + 69), @cx_s_테니스, 994.91, 2, '2026-09-20 10:00:00'
       UNION ALL SELECT (@cx_u + 70), @cx_s_배드민턴, 905.66, 6, '2026-11-07 20:00:00'
       UNION ALL SELECT (@cx_u + 71), @cx_s_배드민턴, 936.95, 2, '2026-10-25 11:00:00'
       UNION ALL SELECT (@cx_u + 72), @cx_s_배드민턴, 1011.67, 2, '2026-10-17 22:00:00'
       UNION ALL SELECT (@cx_u + 73), @cx_s_배드민턴, 946.09, 2, '2026-11-07 20:00:00'
       UNION ALL SELECT (@cx_u + 74), @cx_s_축구, 970.35, 6, '2026-11-08 16:00:00'
       UNION ALL SELECT (@cx_u + 75), @cx_s_축구, 970.35, 6, '2026-11-08 16:00:00'
       UNION ALL SELECT (@cx_u + 76), @cx_s_축구, 970.35, 6, '2026-11-08 16:00:00'
       UNION ALL SELECT (@cx_u + 77), @cx_s_축구, 1035.38, 3, '2026-10-25 12:00:00'
       UNION ALL SELECT (@cx_u + 78), @cx_s_축구, 937.44, 3, '2026-11-08 16:00:00'
       UNION ALL SELECT (@cx_u + 79), @cx_s_축구, 1003.62, 3, '2026-11-08 16:00:00'
       UNION ALL SELECT (@cx_u + 80), @cx_s_축구, 969.20, 3, '2026-10-25 12:00:00'
       UNION ALL SELECT (@cx_u + 81), @cx_s_축구, 1063.52, 6, '2026-10-30 21:00:00'
       UNION ALL SELECT (@cx_u + 82), @cx_s_축구, 1063.52, 6, '2026-10-30 21:00:00'
       UNION ALL SELECT (@cx_u + 83), @cx_s_축구, 1063.52, 6, '2026-10-30 21:00:00'
       UNION ALL SELECT (@cx_u + 84), @cx_s_축구, 954.82, 3, '2026-10-30 21:00:00'
       UNION ALL SELECT (@cx_u + 85), @cx_s_축구, 1023.21, 3, '2026-10-30 21:00:00'
       UNION ALL SELECT (@cx_u + 86), @cx_s_축구, 1089.89, 3, '2026-10-25 12:00:00'
       UNION ALL SELECT (@cx_u + 87), @cx_s_축구, 1021.50, 3, '2026-10-25 12:00:00'
       UNION ALL SELECT (@cx_u + 88), @cx_s_축구, 1033.27, 6, '2026-11-08 16:00:00'
       UNION ALL SELECT (@cx_u + 89), @cx_s_축구, 1033.27, 6, '2026-11-08 16:00:00'
       UNION ALL SELECT (@cx_u + 90), @cx_s_축구, 1033.27, 6, '2026-11-08 16:00:00'
       UNION ALL SELECT (@cx_u + 91), @cx_s_축구, 989.12, 3, '2026-11-08 16:00:00'
       UNION ALL SELECT (@cx_u + 92), @cx_s_축구, 1031.67, 3, '2026-10-30 21:00:00'
       UNION ALL SELECT (@cx_u + 93), @cx_s_축구, 1041.68, 3, '2026-10-30 21:00:00'
       UNION ALL SELECT (@cx_u + 94), @cx_s_축구, 999.13, 3, '2026-11-08 16:00:00'
       UNION ALL SELECT (@cx_u + 95), @cx_s_농구, 1078.13, 6, '2026-11-01 20:00:00'
       UNION ALL SELECT (@cx_u + 96), @cx_s_농구, 1078.13, 6, '2026-11-01 20:00:00'
       UNION ALL SELECT (@cx_u + 97), @cx_s_농구, 1026.41, 3, '2026-11-01 20:00:00'
       UNION ALL SELECT (@cx_u + 98), @cx_s_농구, 1094.63, 3, '2026-10-25 20:00:00'
       UNION ALL SELECT (@cx_u + 99), @cx_s_농구, 964.38, 3, '2026-11-01 20:00:00'
       UNION ALL SELECT (@cx_u + 100), @cx_s_농구, 1032.60, 3, '2026-10-25 20:00:00'
       UNION ALL SELECT (@cx_u + 101), @cx_s_농구, 1014.70, 6, '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_u + 102), @cx_s_농구, 1014.70, 6, '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_u + 103), @cx_s_농구, 1035.62, 3, '2026-11-01 20:00:00'
       UNION ALL SELECT (@cx_u + 104), @cx_s_농구, 953.15, 3, '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_u + 105), @cx_s_농구, 964.83, 3, '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_u + 106), @cx_s_농구, 1036.80, 3, '2026-11-01 20:00:00'
       UNION ALL SELECT (@cx_u + 107), @cx_s_족구, 1005.98, 5, '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_u + 108), @cx_s_족구, 1005.98, 5, '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_u + 109), @cx_s_족구, 998.52, 2, '2026-10-25 12:00:00'
       UNION ALL SELECT (@cx_u + 110), @cx_s_족구, 1007.46, 3, '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_u + 111), @cx_s_배드민턴, 1092.35, 6, '2026-11-07 20:00:00'
       UNION ALL SELECT (@cx_u + 112), @cx_s_배드민턴, 991.36, 2, '2026-10-28 20:00:00'
       UNION ALL SELECT (@cx_u + 113), @cx_s_배드민턴, 1060.01, 2, '2026-10-24 20:00:00'
       UNION ALL SELECT (@cx_u + 114), @cx_s_배드민턴, 1051.93, 2, '2026-11-07 20:00:00'
       UNION ALL SELECT (@cx_u + 115), @cx_s_테니스, 1097.67, 6, '2026-11-06 21:30:00'
       UNION ALL SELECT (@cx_u + 116), @cx_s_테니스, 1061.40, 2, '2026-10-29 22:00:00'
       UNION ALL SELECT (@cx_u + 117), @cx_s_테니스, 1060.84, 2, '2026-10-25 12:00:00'
       UNION ALL SELECT (@cx_u + 118), @cx_s_테니스, 988.67, 2, '2026-11-06 21:30:00'
) v
 WHERE @cx_ready = 1;

-- 2. 동호회원 (기존 동호회에 새 회원이 가입)
INSERT INTO club_member (club_member_id, club_id, user_id, role, joined_at)
SELECT * FROM (
                 SELECT (@cx_m + 1) AS club_member_id, @cx_c2 AS club_id, (@cx_u + 1) AS user_id, '동호회원' AS role, '2026-05-16 14:00:00' AS joined_at
       UNION ALL SELECT (@cx_m + 2), @cx_c2, (@cx_u + 2), '동호회원', '2026-06-07 22:00:00'
       UNION ALL SELECT (@cx_m + 3), @cx_c2, (@cx_u + 3), '동호회원', '2026-07-08 10:00:00'
       UNION ALL SELECT (@cx_m + 4), @cx_c2, (@cx_u + 4), '동호회원', '2026-07-10 11:00:00'
       UNION ALL SELECT (@cx_m + 5), @cx_c2, (@cx_u + 5), '동호회원', '2026-07-15 11:30:00'
       UNION ALL SELECT (@cx_m + 6), @cx_c2, (@cx_u + 6), '동호회원', '2026-08-03 10:00:00'
       UNION ALL SELECT (@cx_m + 7), @cx_c4, (@cx_u + 7), '동호회원', '2026-05-16 20:00:00'
       UNION ALL SELECT (@cx_m + 8), @cx_c4, (@cx_u + 8), '동호회원', '2026-07-10 18:30:00'
       UNION ALL SELECT (@cx_m + 9), @cx_c4, (@cx_u + 9), '동호회원', '2026-07-22 21:00:00'
       UNION ALL SELECT (@cx_m + 10), @cx_c4, (@cx_u + 10), '동호회원', '2026-07-29 19:00:00'
       UNION ALL SELECT (@cx_m + 11), @cx_c5, (@cx_u + 11), '동호회원', '2026-05-19 11:30:00'
       UNION ALL SELECT (@cx_m + 12), @cx_c5, (@cx_u + 12), '동호회원', '2026-05-22 19:00:00'
       UNION ALL SELECT (@cx_m + 13), @cx_c5, (@cx_u + 13), '동호회원', '2026-06-09 19:30:00'
       UNION ALL SELECT (@cx_m + 14), @cx_c5, (@cx_u + 14), '동호회원', '2026-07-08 14:30:00'
       UNION ALL SELECT (@cx_m + 15), @cx_c6, (@cx_u + 15), '동호회원', '2026-06-01 17:00:00'
       UNION ALL SELECT (@cx_m + 16), @cx_c6, (@cx_u + 16), '동호회원', '2026-06-10 21:00:00'
       UNION ALL SELECT (@cx_m + 17), @cx_c6, (@cx_u + 17), '동호회원', '2026-06-16 12:30:00'
       UNION ALL SELECT (@cx_m + 18), @cx_c6, (@cx_u + 18), '동호회원', '2026-07-14 16:30:00'
       UNION ALL SELECT (@cx_m + 19), @cx_c7, (@cx_u + 19), '동호회원', '2026-05-09 16:00:00'
       UNION ALL SELECT (@cx_m + 20), @cx_c7, (@cx_u + 20), '동호회원', '2026-05-23 21:30:00'
       UNION ALL SELECT (@cx_m + 21), @cx_c7, (@cx_u + 21), '동호회원', '2026-05-29 20:30:00'
       UNION ALL SELECT (@cx_m + 22), @cx_c7, (@cx_u + 22), '동호회원', '2026-07-07 16:00:00'
       UNION ALL SELECT (@cx_m + 23), @cx_c8, (@cx_u + 23), '동호회원', '2026-05-04 11:30:00'
       UNION ALL SELECT (@cx_m + 24), @cx_c8, (@cx_u + 24), '동호회원', '2026-06-12 17:00:00'
       UNION ALL SELECT (@cx_m + 25), @cx_c8, (@cx_u + 25), '동호회원', '2026-06-16 13:30:00'
       UNION ALL SELECT (@cx_m + 26), @cx_c8, (@cx_u + 26), '동호회원', '2026-07-13 12:30:00'
       UNION ALL SELECT (@cx_m + 27), @cx_c9, (@cx_u + 27), '동호회원', '2026-06-19 21:00:00'
       UNION ALL SELECT (@cx_m + 28), @cx_c9, (@cx_u + 28), '동호회원', '2026-06-21 09:00:00'
       UNION ALL SELECT (@cx_m + 29), @cx_c9, (@cx_u + 29), '동호회원', '2026-07-19 13:30:00'
       UNION ALL SELECT (@cx_m + 30), @cx_c9, (@cx_u + 30), '동호회원', '2026-07-26 22:30:00'
       UNION ALL SELECT (@cx_m + 31), @cx_c10, (@cx_u + 31), '동호회원', '2026-05-05 17:30:00'
       UNION ALL SELECT (@cx_m + 32), @cx_c10, (@cx_u + 32), '동호회원', '2026-05-26 15:30:00'
       UNION ALL SELECT (@cx_m + 33), @cx_c10, (@cx_u + 33), '동호회원', '2026-06-06 14:00:00'
       UNION ALL SELECT (@cx_m + 34), @cx_c10, (@cx_u + 34), '동호회원', '2026-06-13 11:30:00'
       UNION ALL SELECT (@cx_m + 35), @cx_c10, (@cx_u + 35), '동호회원', '2026-07-04 15:00:00'
       UNION ALL SELECT (@cx_m + 36), @cx_c10, (@cx_u + 36), '동호회원', '2026-07-19 09:00:00'
       UNION ALL SELECT (@cx_m + 37), @cx_c11, (@cx_u + 37), '동호회원', '2026-05-16 09:00:00'
       UNION ALL SELECT (@cx_m + 38), @cx_c11, (@cx_u + 38), '동호회원', '2026-06-05 19:00:00'
       UNION ALL SELECT (@cx_m + 39), @cx_c11, (@cx_u + 39), '동호회원', '2026-07-03 16:00:00'
       UNION ALL SELECT (@cx_m + 40), @cx_c11, (@cx_u + 40), '동호회원', '2026-08-25 11:00:00'
       UNION ALL SELECT (@cx_m + 41), @cx_c12, (@cx_u + 41), '동호회원', '2026-07-13 20:00:00'
       UNION ALL SELECT (@cx_m + 42), @cx_c12, (@cx_u + 42), '동호회원', '2026-08-06 11:30:00'
       UNION ALL SELECT (@cx_m + 43), @cx_c12, (@cx_u + 43), '동호회원', '2026-09-02 16:30:00'
       UNION ALL SELECT (@cx_m + 44), @cx_c12, (@cx_u + 44), '동호회원', '2026-09-03 16:00:00'
       UNION ALL SELECT (@cx_m + 45), @cx_c12, (@cx_u + 45), '동호회원', '2026-09-08 22:00:00'
       UNION ALL SELECT (@cx_m + 46), @cx_c12, (@cx_u + 46), '동호회원', '2026-09-15 12:30:00'
       UNION ALL SELECT (@cx_m + 47), @cx_c12, (@cx_u + 47), '동호회원', '2026-09-16 15:30:00'
       UNION ALL SELECT (@cx_m + 48), @cx_c13, (@cx_u + 48), '동호회원', '2026-05-07 16:30:00'
       UNION ALL SELECT (@cx_m + 49), @cx_c13, (@cx_u + 49), '동호회원', '2026-05-23 13:30:00'
       UNION ALL SELECT (@cx_m + 50), @cx_c13, (@cx_u + 50), '동호회원', '2026-06-26 10:30:00'
       UNION ALL SELECT (@cx_m + 51), @cx_c13, (@cx_u + 51), '동호회원', '2026-07-03 14:30:00'
       UNION ALL SELECT (@cx_m + 52), @cx_c14, (@cx_u + 52), '동호회원', '2026-06-06 14:00:00'
       UNION ALL SELECT (@cx_m + 53), @cx_c14, (@cx_u + 53), '동호회원', '2026-06-07 10:00:00'
       UNION ALL SELECT (@cx_m + 54), @cx_c14, (@cx_u + 54), '동호회원', '2026-06-24 16:30:00'
       UNION ALL SELECT (@cx_m + 55), @cx_c14, (@cx_u + 55), '동호회원', '2026-07-02 17:00:00'
       UNION ALL SELECT (@cx_m + 56), @cx_c15, (@cx_u + 56), '동호회원', '2026-05-10 10:30:00'
       UNION ALL SELECT (@cx_m + 57), @cx_c15, (@cx_u + 57), '동호회원', '2026-05-15 20:30:00'
       UNION ALL SELECT (@cx_m + 58), @cx_c15, (@cx_u + 58), '동호회원', '2026-05-30 16:00:00'
       UNION ALL SELECT (@cx_m + 59), @cx_c15, (@cx_u + 59), '동호회원', '2026-06-20 11:30:00'
       UNION ALL SELECT (@cx_m + 60), @cx_c15, (@cx_u + 60), '동호회원', '2026-06-25 21:30:00'
       UNION ALL SELECT (@cx_m + 61), @cx_c15, (@cx_u + 61), '동호회원', '2026-07-28 15:00:00'
       UNION ALL SELECT (@cx_m + 62), @cx_c17, (@cx_u + 62), '동호회원', '2026-05-03 20:30:00'
       UNION ALL SELECT (@cx_m + 63), @cx_c17, (@cx_u + 63), '동호회원', '2026-05-06 15:30:00'
       UNION ALL SELECT (@cx_m + 64), @cx_c17, (@cx_u + 64), '동호회원', '2026-06-25 16:30:00'
       UNION ALL SELECT (@cx_m + 65), @cx_c17, (@cx_u + 65), '동호회원', '2026-07-08 20:00:00'
       UNION ALL SELECT (@cx_m + 66), @cx_c18, (@cx_u + 66), '동호회원', '2026-05-05 22:30:00'
       UNION ALL SELECT (@cx_m + 67), @cx_c18, (@cx_u + 67), '동호회원', '2026-05-14 09:30:00'
       UNION ALL SELECT (@cx_m + 68), @cx_c18, (@cx_u + 68), '동호회원', '2026-06-04 20:30:00'
       UNION ALL SELECT (@cx_m + 69), @cx_c18, (@cx_u + 69), '동호회원', '2026-07-26 19:00:00'
       UNION ALL SELECT (@cx_m + 70), @cx_c19, (@cx_u + 70), '동호회원', '2026-05-22 15:00:00'
       UNION ALL SELECT (@cx_m + 71), @cx_c19, (@cx_u + 71), '동호회원', '2026-06-17 10:00:00'
       UNION ALL SELECT (@cx_m + 72), @cx_c19, (@cx_u + 72), '동호회원', '2026-06-24 22:00:00'
       UNION ALL SELECT (@cx_m + 73), @cx_c19, (@cx_u + 73), '동호회원', '2026-07-17 12:30:00'
       UNION ALL SELECT (@cx_m + 74), @cx_c21, (@cx_u + 74), '동호회원', '2026-09-06 17:30:00'
       UNION ALL SELECT (@cx_m + 75), @cx_c21, (@cx_u + 75), '동호회원', '2026-09-09 16:00:00'
       UNION ALL SELECT (@cx_m + 76), @cx_c21, (@cx_u + 76), '동호회원', '2026-09-10 09:00:00'
       UNION ALL SELECT (@cx_m + 77), @cx_c21, (@cx_u + 77), '동호회원', '2026-09-12 09:00:00'
       UNION ALL SELECT (@cx_m + 78), @cx_c21, (@cx_u + 78), '동호회원', '2026-09-18 13:00:00'
       UNION ALL SELECT (@cx_m + 79), @cx_c21, (@cx_u + 79), '동호회원', '2026-09-21 11:30:00'
       UNION ALL SELECT (@cx_m + 80), @cx_c21, (@cx_u + 80), '동호회원', '2026-09-22 20:30:00'
       UNION ALL SELECT (@cx_m + 81), @cx_c22, (@cx_u + 81), '동호회원', '2026-09-06 11:00:00'
       UNION ALL SELECT (@cx_m + 82), @cx_c22, (@cx_u + 82), '동호회원', '2026-09-11 15:00:00'
       UNION ALL SELECT (@cx_m + 83), @cx_c22, (@cx_u + 83), '동호회원', '2026-09-12 19:00:00'
       UNION ALL SELECT (@cx_m + 84), @cx_c22, (@cx_u + 84), '동호회원', '2026-09-19 15:30:00'
       UNION ALL SELECT (@cx_m + 85), @cx_c22, (@cx_u + 85), '동호회원', '2026-09-23 22:30:00'
       UNION ALL SELECT (@cx_m + 86), @cx_c22, (@cx_u + 86), '동호회원', '2026-09-24 19:30:00'
       UNION ALL SELECT (@cx_m + 87), @cx_c22, (@cx_u + 87), '동호회원', '2026-09-25 16:00:00'
       UNION ALL SELECT (@cx_m + 88), @cx_c23, (@cx_u + 88), '동호회원', '2026-09-08 12:00:00'
       UNION ALL SELECT (@cx_m + 89), @cx_c23, (@cx_u + 89), '동호회원', '2026-09-09 21:00:00'
       UNION ALL SELECT (@cx_m + 90), @cx_c23, (@cx_u + 90), '동호회원', '2026-09-10 18:30:00'
       UNION ALL SELECT (@cx_m + 91), @cx_c23, (@cx_u + 91), '동호회원', '2026-09-18 11:30:00'
       UNION ALL SELECT (@cx_m + 92), @cx_c23, (@cx_u + 92), '동호회원', '2026-09-18 21:00:00'
       UNION ALL SELECT (@cx_m + 93), @cx_c23, (@cx_u + 93), '동호회원', '2026-09-20 10:30:00'
       UNION ALL SELECT (@cx_m + 94), @cx_c23, (@cx_u + 94), '동호회원', '2026-09-22 12:00:00'
       UNION ALL SELECT (@cx_m + 95), @cx_c24, (@cx_u + 95), '동호회원', '2026-09-11 12:30:00'
       UNION ALL SELECT (@cx_m + 96), @cx_c24, (@cx_u + 96), '동호회원', '2026-09-13 17:00:00'
       UNION ALL SELECT (@cx_m + 97), @cx_c24, (@cx_u + 97), '동호회원', '2026-09-13 19:30:00'
       UNION ALL SELECT (@cx_m + 98), @cx_c24, (@cx_u + 98), '동호회원', '2026-09-14 20:00:00'
       UNION ALL SELECT (@cx_m + 99), @cx_c24, (@cx_u + 99), '동호회원', '2026-09-23 10:30:00'
       UNION ALL SELECT (@cx_m + 100), @cx_c24, (@cx_u + 100), '동호회원', '2026-09-23 14:00:00'
       UNION ALL SELECT (@cx_m + 101), @cx_c25, (@cx_u + 101), '동호회원', '2026-09-16 14:00:00'
       UNION ALL SELECT (@cx_m + 102), @cx_c25, (@cx_u + 102), '동호회원', '2026-09-22 10:30:00'
       UNION ALL SELECT (@cx_m + 103), @cx_c25, (@cx_u + 103), '동호회원', '2026-09-22 13:30:00'
       UNION ALL SELECT (@cx_m + 104), @cx_c25, (@cx_u + 104), '동호회원', '2026-09-24 16:30:00'
       UNION ALL SELECT (@cx_m + 105), @cx_c25, (@cx_u + 105), '동호회원', '2026-09-26 21:30:00'
       UNION ALL SELECT (@cx_m + 106), @cx_c25, (@cx_u + 106), '동호회원', '2026-09-30 11:00:00'
       UNION ALL SELECT (@cx_m + 107), @cx_c26, (@cx_u + 107), '동호회원', '2026-09-17 21:00:00'
       UNION ALL SELECT (@cx_m + 108), @cx_c26, (@cx_u + 108), '동호회원', '2026-09-22 16:00:00'
       UNION ALL SELECT (@cx_m + 109), @cx_c26, (@cx_u + 109), '동호회원', '2026-09-27 13:00:00'
       UNION ALL SELECT (@cx_m + 110), @cx_c26, (@cx_u + 110), '동호회원', '2026-09-30 14:00:00'
       UNION ALL SELECT (@cx_m + 111), @cx_c27, (@cx_u + 111), '동호회원', '2026-09-28 11:00:00'
       UNION ALL SELECT (@cx_m + 112), @cx_c27, (@cx_u + 112), '동호회원', '2026-09-29 15:30:00'
       UNION ALL SELECT (@cx_m + 113), @cx_c27, (@cx_u + 113), '동호회원', '2026-10-05 12:00:00'
       UNION ALL SELECT (@cx_m + 114), @cx_c27, (@cx_u + 114), '동호회원', '2026-10-06 09:00:00'
       UNION ALL SELECT (@cx_m + 115), @cx_c28, (@cx_u + 115), '동호회원', '2026-09-27 19:00:00'
       UNION ALL SELECT (@cx_m + 116), @cx_c28, (@cx_u + 116), '동호회원', '2026-10-02 19:30:00'
       UNION ALL SELECT (@cx_m + 117), @cx_c28, (@cx_u + 117), '동호회원', '2026-10-04 17:30:00'
       UNION ALL SELECT (@cx_m + 118), @cx_c28, (@cx_u + 118), '동호회원', '2026-10-08 17:30:00'
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
                 SELECT (@cx_sm + 1) AS match_id, @cx_f8 AS facility_id, @cx_s_농구 AS sport_id, @cx_f8_mgr AS created_by, '광나루 농구장 토요일 오전 4:4' AS title, '2026-08-01 10:00:00' AS start_at, '2026-08-01 12:00:00' AS end_at, 8 AS min_participants, 8 AS max_participants, 4 AS team_a_capacity, 4 AS team_b_capacity, 0 AS fee_per_person, '중급' AS required_skill, '종료' AS status, '/images/matches/basketball.jpg' AS photo_url, 'MANUAL' AS assignment_method, @cx_f8_mgr AS assigned_by, '2026-08-01 09:00:00' AS assignment_confirmed_at, 'CONFIRMED' AS result_status, 'B_WIN' AS final_result, '2026-08-01 13:25:00' AS result_confirmed_at, 'APPLIED' AS rating_status, '2026-08-02 12:00:00' AS rating_processed_at
       UNION ALL SELECT (@cx_sm + 2), @cx_f11, @cx_s_테니스, @cx_f11_mgr, '어린이대공원 테니스장 일요일 오전 2:2', '2026-08-02 08:00:00', '2026-08-02 10:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f11_mgr, '2026-08-02 07:00:00', 'CONFIRMED', 'B_WIN', '2026-08-02 11:50:00', 'APPLIED', '2026-08-03 10:00:00'
       UNION ALL SELECT (@cx_sm + 3), @cx_f16, @cx_s_배드민턴, @cx_f16_mgr, '송파 배드민턴코트 일요일 오전 2:2', '2026-08-02 09:00:00', '2026-08-02 11:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f16_mgr, '2026-08-02 08:00:00', 'CONFIRMED', 'B_WIN', '2026-08-02 12:01:00', 'APPLIED', '2026-08-03 11:00:00'
       UNION ALL SELECT (@cx_sm + 4), @cx_f12, @cx_s_배드민턴, @cx_f12_mgr, '강서 배드민턴장 화요일 저녁 2:2', '2026-08-04 20:00:00', '2026-08-04 22:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f12_mgr, '2026-08-04 19:00:00', 'CONFIRMED', 'B_WIN', '2026-08-04 23:22:00', 'APPLIED', '2026-08-05 22:00:00'
       UNION ALL SELECT (@cx_sm + 5), @cx_f2, @cx_s_테니스, @cx_f2_mgr, '월드컵공원 테니스장 금요일 오전 2:2', '2026-08-07 07:00:00', '2026-08-07 09:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f2_mgr, '2026-08-07 06:00:00', 'CONFIRMED', 'A_WIN', '2026-08-07 11:15:00', 'APPLIED', '2026-08-08 09:00:00'
       UNION ALL SELECT (@cx_sm + 6), @cx_f1, @cx_s_농구, @cx_f1_mgr, '노원 농구장 일요일 오전 4:4', '2026-08-09 09:00:00', '2026-08-09 11:00:00', 8, 8, 4, 4, 0, '중급', '종료', '/images/matches/basketball.jpg', 'MANUAL', @cx_f1_mgr, '2026-08-09 08:00:00', 'CONFIRMED', 'A_WIN', '2026-08-09 12:44:00', 'APPLIED', '2026-08-10 11:00:00'
       UNION ALL SELECT (@cx_sm + 7), @cx_f3, @cx_s_테니스, @cx_f3_mgr, '서초 테니스클럽 화요일 저녁 2:2', '2026-08-11 21:00:00', '2026-08-11 23:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f3_mgr, '2026-08-11 20:00:00', 'CONFIRMED', 'B_WIN', '2026-08-12 01:31:00', 'APPLIED', '2026-08-12 23:00:00'
       UNION ALL SELECT (@cx_sm + 8), @cx_f6, @cx_s_테니스, @cx_f6_mgr, '서초 스포츠센터 목요일 저녁 2:2', '2026-08-13 19:30:00', '2026-08-13 21:30:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f6_mgr, '2026-08-13 18:30:00', 'CONFIRMED', 'DRAW', '2026-08-13 22:01:00', 'APPLIED', '2026-08-14 21:30:00'
       UNION ALL SELECT (@cx_sm + 9), @cx_f15, @cx_s_테니스, @cx_f15_mgr, '강서 테니스장 수요일 저녁 2:2', '2026-08-19 20:00:00', '2026-08-19 22:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f15_mgr, '2026-08-19 19:00:00', 'CONFIRMED', 'A_WIN', '2026-08-19 23:42:00', 'APPLIED', '2026-08-20 22:00:00'
       UNION ALL SELECT (@cx_sm + 10), @cx_f3, @cx_s_테니스, @cx_f3_mgr, '서초 테니스클럽 목요일 저녁 2:2', '2026-08-20 21:00:00', '2026-08-20 23:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f3_mgr, '2026-08-20 20:00:00', 'CONFIRMED', 'B_WIN', '2026-08-21 00:15:00', 'APPLIED', '2026-08-21 23:00:00'
       UNION ALL SELECT (@cx_sm + 11), @cx_f5, @cx_s_배드민턴, @cx_f5_mgr, '광진 배드민턴장 금요일 저녁 2:2', '2026-08-21 20:00:00', '2026-08-21 22:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f5_mgr, '2026-08-21 19:00:00', 'CONFIRMED', 'B_WIN', '2026-08-21 23:46:00', 'APPLIED', '2026-08-22 22:00:00'
       UNION ALL SELECT (@cx_sm + 12), @cx_f1, @cx_s_농구, @cx_f1_mgr, '노원 농구장 일요일 오전 4:4', '2026-08-23 09:00:00', '2026-08-23 11:00:00', 8, 8, 4, 4, 0, '중급', '종료', '/images/matches/basketball.jpg', 'MANUAL', @cx_f1_mgr, '2026-08-23 08:00:00', 'CONFIRMED', 'B_WIN', '2026-08-23 12:16:00', 'APPLIED', '2026-08-24 11:00:00'
       UNION ALL SELECT (@cx_sm + 13), @cx_f6, @cx_s_테니스, @cx_f6_mgr, '서초 스포츠센터 목요일 저녁 2:2', '2026-08-27 19:30:00', '2026-08-27 21:30:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f6_mgr, '2026-08-27 18:30:00', 'CONFIRMED', 'B_WIN', '2026-08-28 00:23:00', 'APPLIED', '2026-08-28 21:30:00'
       UNION ALL SELECT (@cx_sm + 14), @cx_f5, @cx_s_배드민턴, @cx_f5_mgr, '광진 배드민턴장 금요일 저녁 2:2', '2026-08-28 20:00:00', '2026-08-28 22:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f5_mgr, '2026-08-28 19:00:00', 'CONFIRMED', 'A_WIN', '2026-08-28 23:43:00', 'APPLIED', '2026-08-29 22:00:00'
       UNION ALL SELECT (@cx_sm + 15), @cx_f11, @cx_s_테니스, @cx_f11_mgr, '어린이대공원 테니스장 토요일 오전 2:2', '2026-08-29 08:00:00', '2026-08-29 10:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f11_mgr, '2026-08-29 07:00:00', 'CONFIRMED', 'A_WIN', '2026-08-29 11:26:00', 'APPLIED', '2026-08-30 10:00:00'
       UNION ALL SELECT (@cx_sm + 16), @cx_f8, @cx_s_농구, @cx_f8_mgr, '광나루 농구장 토요일 오전 4:4', '2026-08-29 10:00:00', '2026-08-29 12:00:00', 8, 8, 4, 4, 0, '중급', '종료', '/images/matches/basketball.jpg', 'MANUAL', @cx_f8_mgr, '2026-08-29 09:00:00', 'CONFIRMED', 'B_WIN', '2026-08-29 14:09:00', 'APPLIED', '2026-08-30 12:00:00'
       UNION ALL SELECT (@cx_sm + 17), @cx_f12, @cx_s_배드민턴, @cx_f12_mgr, '강서 배드민턴장 화요일 저녁 2:2', '2026-09-01 20:00:00', '2026-09-01 22:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f12_mgr, '2026-09-01 19:00:00', 'CONFIRMED', 'B_WIN', '2026-09-02 00:16:00', 'APPLIED', '2026-09-02 22:00:00'
       UNION ALL SELECT (@cx_sm + 18), @cx_f3, @cx_s_테니스, @cx_f3_mgr, '서초 테니스클럽 목요일 저녁 2:2', '2026-09-03 21:00:00', '2026-09-03 23:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f3_mgr, '2026-09-03 20:00:00', 'CONFIRMED', 'A_WIN', '2026-09-03 23:30:00', 'APPLIED', '2026-09-04 23:00:00'
       UNION ALL SELECT (@cx_sm + 19), @cx_f7, @cx_s_테니스, @cx_f7_mgr, '영등포 테니스장 금요일 저녁 2:2', '2026-09-11 20:00:00', '2026-09-11 22:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f7_mgr, '2026-09-11 19:00:00', 'CONFIRMED', 'B_WIN', '2026-09-11 23:54:00', 'APPLIED', '2026-09-12 22:00:00'
       UNION ALL SELECT (@cx_sm + 20), @cx_f16, @cx_s_배드민턴, @cx_f16_mgr, '송파 배드민턴코트 일요일 오전 2:2', '2026-09-13 09:00:00', '2026-09-13 11:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f16_mgr, '2026-09-13 08:00:00', 'CONFIRMED', 'B_WIN', '2026-09-13 13:33:00', 'APPLIED', '2026-09-14 11:00:00'
       UNION ALL SELECT (@cx_sm + 21), @cx_f5, @cx_s_배드민턴, @cx_f5_mgr, '광진 배드민턴장 화요일 저녁 2:2', '2026-09-15 20:00:00', '2026-09-15 22:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f5_mgr, '2026-09-15 19:00:00', 'CONFIRMED', 'B_WIN', '2026-09-15 23:57:00', 'APPLIED', '2026-09-16 22:00:00'
       UNION ALL SELECT (@cx_sm + 22), @cx_f11, @cx_s_테니스, @cx_f11_mgr, '어린이대공원 테니스장 토요일 오전 2:2', '2026-09-19 08:00:00', '2026-09-19 10:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f11_mgr, '2026-09-19 07:00:00', 'CONFIRMED', 'B_WIN', '2026-09-19 11:17:00', 'APPLIED', '2026-09-20 10:00:00'
       UNION ALL SELECT (@cx_sm + 23), @cx_f8, @cx_s_농구, @cx_f8_mgr, '광나루 농구장 토요일 오전 4:4', '2026-09-19 10:00:00', '2026-09-19 12:00:00', 8, 8, 4, 4, 0, '중급', '종료', '/images/matches/basketball.jpg', 'MANUAL', @cx_f8_mgr, '2026-09-19 09:00:00', 'CONFIRMED', 'B_WIN', '2026-09-19 14:02:00', 'APPLIED', '2026-09-20 12:00:00'
       UNION ALL SELECT (@cx_sm + 24), @cx_f6, @cx_s_테니스, @cx_f6_mgr, '서초 스포츠센터 목요일 저녁 2:2', '2026-09-24 19:30:00', '2026-09-24 21:30:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f6_mgr, '2026-09-24 18:30:00', 'CONFIRMED', 'B_WIN', '2026-09-24 23:38:00', 'APPLIED', '2026-09-25 21:30:00'
       UNION ALL SELECT (@cx_sm + 25), @cx_f10, @cx_s_축구, @cx_f10_mgr, '은평 풋살아레나 토요일 저녁 5:5', '2026-09-26 20:00:00', '2026-09-26 22:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cx_f10_mgr, '2026-09-26 19:00:00', 'CONFIRMED', 'B_WIN', '2026-09-26 23:33:00', 'APPLIED', '2026-09-27 22:00:00'
       UNION ALL SELECT (@cx_sm + 26), @cx_f18, @cx_s_축구, @cx_f18_mgr, '망원유수지 풋살장 화요일 저녁 5:5', '2026-09-29 19:00:00', '2026-09-29 21:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cx_f18_mgr, '2026-09-29 18:00:00', 'CONFIRMED', 'A_WIN', '2026-09-29 21:39:00', 'APPLIED', '2026-09-30 21:00:00'
       UNION ALL SELECT (@cx_sm + 27), @cx_f5, @cx_s_배드민턴, @cx_f5_mgr, '광진 배드민턴장 화요일 저녁 2:2', '2026-09-29 20:00:00', '2026-09-29 22:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f5_mgr, '2026-09-29 19:00:00', 'CONFIRMED', 'A_WIN', '2026-09-29 23:32:00', 'APPLIED', '2026-09-30 22:00:00'
       UNION ALL SELECT (@cx_sm + 28), @cx_f10, @cx_s_축구, @cx_f10_mgr, '은평 풋살아레나 수요일 저녁 5:5', '2026-09-30 20:00:00', '2026-09-30 22:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cx_f10_mgr, '2026-09-30 19:00:00', 'CONFIRMED', 'A_WIN', '2026-09-30 22:52:00', 'APPLIED', '2026-10-01 22:00:00'
       UNION ALL SELECT (@cx_sm + 29), @cx_f13, @cx_s_농구, @cx_f13_mgr, '영등포 농구센터 금요일 저녁 4:4', '2026-10-02 19:00:00', '2026-10-02 21:00:00', 8, 8, 4, 4, 0, '중급', '종료', '/images/matches/basketball.jpg', 'MANUAL', @cx_f13_mgr, '2026-10-02 18:00:00', 'CONFIRMED', 'B_WIN', '2026-10-02 22:09:00', 'APPLIED', '2026-10-03 21:00:00'
       UNION ALL SELECT (@cx_sm + 30), @cx_f1, @cx_s_농구, @cx_f1_mgr, '노원 농구장 토요일 오전 4:4', '2026-10-03 09:00:00', '2026-10-03 11:00:00', 8, 8, 4, 4, 0, '중급', '종료', '/images/matches/basketball.jpg', 'MANUAL', @cx_f1_mgr, '2026-10-03 08:00:00', 'CONFIRMED', 'B_WIN', '2026-10-03 11:45:00', 'APPLIED', '2026-10-04 11:00:00'
       UNION ALL SELECT (@cx_sm + 31), @cx_f17, @cx_s_축구, @cx_f17_mgr, '뚝섬한강공원 축구장 토요일 오전 5:5', '2026-10-03 10:00:00', '2026-10-03 12:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cx_f17_mgr, '2026-10-03 09:00:00', 'CONFIRMED', 'B_WIN', '2026-10-03 13:25:00', 'APPLIED', '2026-10-04 12:00:00'
       UNION ALL SELECT (@cx_sm + 32), @cx_f11, @cx_s_테니스, @cx_f11_mgr, '어린이대공원 테니스장 일요일 오전 2:2', '2026-10-04 08:00:00', '2026-10-04 10:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f11_mgr, '2026-10-04 07:00:00', 'CONFIRMED', 'A_WIN', '2026-10-04 12:20:00', 'APPLIED', '2026-10-05 10:00:00'
       UNION ALL SELECT (@cx_sm + 33), @cx_f18, @cx_s_축구, @cx_f18_mgr, '망원유수지 풋살장 화요일 저녁 5:5', '2026-10-06 19:00:00', '2026-10-06 21:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cx_f18_mgr, '2026-10-06 18:00:00', 'CONFIRMED', 'A_WIN', '2026-10-06 22:41:00', 'APPLIED', '2026-10-07 21:00:00'
       UNION ALL SELECT (@cx_sm + 34), @cx_f12, @cx_s_배드민턴, @cx_f12_mgr, '강서 배드민턴장 화요일 저녁 2:2', '2026-10-06 20:00:00', '2026-10-06 22:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f12_mgr, '2026-10-06 19:00:00', 'CONFIRMED', 'DRAW', '2026-10-07 00:55:00', 'APPLIED', '2026-10-07 22:00:00'
       UNION ALL SELECT (@cx_sm + 35), @cx_f20, @cx_s_농구, @cx_f20_mgr, '반포한강공원 농구장 수요일 저녁 4:4', '2026-10-07 18:00:00', '2026-10-07 20:00:00', 8, 8, 4, 4, 0, '중급', '종료', '/images/matches/basketball.jpg', 'MANUAL', @cx_f20_mgr, '2026-10-07 17:00:00', 'CONFIRMED', 'B_WIN', '2026-10-07 21:09:00', 'APPLIED', '2026-10-08 20:00:00'
       UNION ALL SELECT (@cx_sm + 36), @cx_f7, @cx_s_테니스, @cx_f7_mgr, '영등포 테니스장 수요일 저녁 2:2', '2026-10-07 20:00:00', '2026-10-07 22:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f7_mgr, '2026-10-07 19:00:00', 'CONFIRMED', 'A_WIN', '2026-10-08 00:11:00', 'APPLIED', '2026-10-08 22:00:00'
       UNION ALL SELECT (@cx_sm + 37), @cx_f1, @cx_s_농구, @cx_f1_mgr, '노원 농구장 토요일 오전 4:4', '2026-10-10 09:00:00', '2026-10-10 11:00:00', 8, 8, 4, 4, 0, '중급', '종료', '/images/matches/basketball.jpg', 'MANUAL', @cx_f1_mgr, '2026-10-10 08:00:00', 'CONFIRMED', 'B_WIN', '2026-10-10 11:47:00', 'APPLIED', '2026-10-11 11:00:00'
       UNION ALL SELECT (@cx_sm + 38), @cx_f22, @cx_s_족구, @cx_f22_mgr, '뚝섬한강공원 족구장 토요일 오전 3:3', '2026-10-10 10:00:00', '2026-10-10 12:00:00', 6, 6, 3, 3, 0, '중급', '종료', '/images/matches/jokgu.jpg', 'MANUAL', @cx_f22_mgr, '2026-10-10 09:00:00', 'CONFIRMED', 'A_WIN', '2026-10-10 13:58:00', 'APPLIED', '2026-10-11 12:00:00'
       UNION ALL SELECT (@cx_sm + 39), @cx_f16, @cx_s_배드민턴, @cx_f16_mgr, '송파 배드민턴코트 일요일 오전 2:2', '2026-10-11 09:00:00', '2026-10-11 11:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f16_mgr, '2026-10-11 08:00:00', 'CONFIRMED', 'B_WIN', '2026-10-11 12:56:00', 'APPLIED', '2026-10-12 11:00:00'
       UNION ALL SELECT (@cx_sm + 40), @cx_f18, @cx_s_축구, @cx_f18_mgr, '망원유수지 풋살장 화요일 저녁 5:5', '2026-10-13 19:00:00', '2026-10-13 21:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cx_f18_mgr, '2026-10-13 18:00:00', 'CONFIRMED', 'B_WIN', '2026-10-13 23:34:00', 'APPLIED', '2026-10-14 21:00:00'
       UNION ALL SELECT (@cx_sm + 41), @cx_f3, @cx_s_테니스, @cx_f3_mgr, '서초 테니스클럽 화요일 저녁 2:2', '2026-10-13 21:00:00', '2026-10-13 23:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f3_mgr, '2026-10-13 20:00:00', 'CONFIRMED', 'B_WIN', '2026-10-14 00:57:00', 'APPLIED', '2026-10-14 23:00:00'
       UNION ALL SELECT (@cx_sm + 42), @cx_f4, @cx_s_배드민턴, @cx_f4_mgr, '중랑물재생 배드민턴장 수요일 저녁 2:2', '2026-10-14 19:00:00', '2026-10-14 21:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f4_mgr, '2026-10-14 18:00:00', 'CONFIRMED', 'A_WIN', '2026-10-14 22:37:00', 'APPLIED', '2026-10-15 21:00:00'
       UNION ALL SELECT (@cx_sm + 43), @cx_f10, @cx_s_축구, @cx_f10_mgr, '은평 풋살아레나 수요일 저녁 5:5', '2026-10-14 20:00:00', '2026-10-14 22:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cx_f10_mgr, '2026-10-14 19:00:00', 'CONFIRMED', 'B_WIN', '2026-10-15 00:43:00', 'APPLIED', '2026-10-15 22:00:00'
       UNION ALL SELECT (@cx_sm + 44), @cx_f12, @cx_s_배드민턴, @cx_f12_mgr, '강서 배드민턴장 목요일 저녁 2:2', '2026-10-15 20:00:00', '2026-10-15 22:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f12_mgr, '2026-10-15 19:00:00', 'CONFIRMED', 'B_WIN', '2026-10-15 23:20:00', 'APPLIED', '2026-10-16 22:00:00'
       UNION ALL SELECT (@cx_sm + 45), @cx_f2, @cx_s_테니스, @cx_f2_mgr, '월드컵공원 테니스장 금요일 오전 2:2', '2026-10-16 07:00:00', '2026-10-16 09:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f2_mgr, '2026-10-16 06:00:00', 'CONFIRMED', 'B_WIN', '2026-10-16 10:26:00', 'APPLIED', '2026-10-17 09:00:00'
       UNION ALL SELECT (@cx_sm + 46), @cx_f5, @cx_s_배드민턴, @cx_f5_mgr, '광진 배드민턴장 금요일 저녁 2:2', '2026-10-16 20:00:00', '2026-10-16 22:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f5_mgr, '2026-10-16 19:00:00', 'CONFIRMED', 'B_WIN', '2026-10-17 00:30:00', 'APPLIED', '2026-10-17 22:00:00'
       UNION ALL SELECT (@cx_sm + 47), @cx_f14, @cx_s_족구, @cx_f14_mgr, '관악 족구장 토요일 오전 3:3', '2026-10-17 09:00:00', '2026-10-17 11:00:00', 6, 6, 3, 3, 0, '중급', '종료', '/images/matches/jokgu.jpg', 'MANUAL', @cx_f14_mgr, '2026-10-17 08:00:00', 'CONFIRMED', 'A_WIN', '2026-10-17 13:32:00', 'APPLIED', '2026-10-18 11:00:00'
       UNION ALL SELECT (@cx_sm + 48), @cx_f19, @cx_s_축구, @cx_f19_mgr, '잠실 제2풋살경기장 토요일 오후 5:5', '2026-10-17 14:00:00', '2026-10-17 16:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cx_f19_mgr, '2026-10-17 13:00:00', 'CONFIRMED', 'B_WIN', '2026-10-17 17:48:00', 'APPLIED', '2026-10-18 16:00:00'
       UNION ALL SELECT (@cx_sm + 49), @cx_f20, @cx_s_농구, @cx_f20_mgr, '반포한강공원 농구장 토요일 저녁 4:4', '2026-10-17 18:00:00', '2026-10-17 20:00:00', 8, 8, 4, 4, 0, '중급', '종료', '/images/matches/basketball.jpg', 'MANUAL', @cx_f20_mgr, '2026-10-17 17:00:00', 'CONFIRMED', 'A_WIN', '2026-10-17 22:38:00', 'APPLIED', '2026-10-18 20:00:00'
       UNION ALL SELECT (@cx_sm + 50), @cx_f11, @cx_s_테니스, @cx_f11_mgr, '어린이대공원 테니스장 일요일 오전 2:2', '2026-10-18 08:00:00', '2026-10-18 10:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f11_mgr, '2026-10-18 07:00:00', 'CONFIRMED', 'B_WIN', '2026-10-18 10:58:00', 'APPLIED', '2026-10-19 10:00:00'
       UNION ALL SELECT (@cx_sm + 51), @cx_f9, @cx_s_배드민턴, @cx_f9_mgr, '송파 배드민턴홀 일요일 오전 2:2', '2026-10-18 09:00:00', '2026-10-18 12:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f9_mgr, '2026-10-18 08:00:00', 'CONFIRMED', 'B_WIN', '2026-10-18 12:37:00', 'APPLIED', '2026-10-19 12:00:00'
       UNION ALL SELECT (@cx_sm + 52), @cx_f3, @cx_s_테니스, @cx_f3_mgr, '서초 테니스클럽 화요일 저녁 2:2', '2026-10-20 21:00:00', '2026-10-20 23:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f3_mgr, '2026-10-20 20:00:00', 'CONFIRMED', 'A_WIN', '2026-10-21 01:59:00', 'APPLIED', '2026-10-21 23:00:00'
       UNION ALL SELECT (@cx_sm + 53), @cx_f20, @cx_s_농구, @cx_f20_mgr, '반포한강공원 농구장 수요일 저녁 4:4', '2026-10-21 18:00:00', '2026-10-21 20:00:00', 8, 8, 4, 4, 0, '중급', '종료', '/images/matches/basketball.jpg', 'MANUAL', @cx_f20_mgr, '2026-10-21 17:00:00', 'CONFIRMED', 'A_WIN', '2026-10-21 22:37:00', 'APPLIED', '2026-10-22 20:00:00'
       UNION ALL SELECT (@cx_sm + 54), @cx_f10, @cx_s_축구, @cx_f10_mgr, '은평 풋살아레나 수요일 저녁 5:5', '2026-10-21 20:00:00', '2026-10-21 22:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cx_f10_mgr, '2026-10-21 19:00:00', 'CONFIRMED', 'B_WIN', '2026-10-21 23:08:00', 'APPLIED', '2026-10-22 22:00:00'
       UNION ALL SELECT (@cx_sm + 55), @cx_f23, @cx_s_배드민턴, @cx_f23_mgr, '뚝섬한강공원 배드민턴장 금요일 저녁 2:2', '2026-10-23 18:00:00', '2026-10-23 20:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f23_mgr, '2026-10-23 17:00:00', 'CONFIRMED', 'A_WIN', '2026-10-23 21:27:00', 'APPLIED', '2026-10-24 20:00:00'
       UNION ALL SELECT (@cx_sm + 56), @cx_f16, @cx_s_배드민턴, @cx_f16_mgr, '송파 배드민턴코트 토요일 오전 2:2', '2026-10-24 09:00:00', '2026-10-24 11:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f16_mgr, '2026-10-24 08:00:00', 'CONFIRMED', 'B_WIN', '2026-10-24 12:13:00', 'APPLIED', '2026-10-25 11:00:00'
       UNION ALL SELECT (@cx_sm + 57), @cx_f17, @cx_s_축구, @cx_f17_mgr, '뚝섬한강공원 축구장 토요일 오전 5:5', '2026-10-24 10:00:00', '2026-10-24 12:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cx_f17_mgr, '2026-10-24 09:00:00', 'CONFIRMED', 'B_WIN', '2026-10-24 12:31:00', 'APPLIED', '2026-10-25 12:00:00'
       UNION ALL SELECT (@cx_sm + 58), @cx_f22, @cx_s_족구, @cx_f22_mgr, '뚝섬한강공원 족구장 토요일 오전 3:3', '2026-10-24 10:00:00', '2026-10-24 12:00:00', 6, 6, 3, 3, 0, '중급', '종료', '/images/matches/jokgu.jpg', 'MANUAL', @cx_f22_mgr, '2026-10-24 09:00:00', 'CONFIRMED', 'B_WIN', '2026-10-24 14:23:00', 'APPLIED', '2026-10-25 12:00:00'
       UNION ALL SELECT (@cx_sm + 59), @cx_f24, @cx_s_테니스, @cx_f24_mgr, '망원나들목 테니스장 토요일 오전 2:2', '2026-10-24 10:00:00', '2026-10-24 12:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f24_mgr, '2026-10-24 09:00:00', 'CONFIRMED', 'A_WIN', '2026-10-24 13:53:00', 'APPLIED', '2026-10-25 12:00:00'
       UNION ALL SELECT (@cx_sm + 60), @cx_f20, @cx_s_농구, @cx_f20_mgr, '반포한강공원 농구장 토요일 저녁 4:4', '2026-10-24 18:00:00', '2026-10-24 20:00:00', 8, 8, 4, 4, 0, '중급', '종료', '/images/matches/basketball.jpg', 'MANUAL', @cx_f20_mgr, '2026-10-24 17:00:00', 'CONFIRMED', 'A_WIN', '2026-10-24 20:58:00', 'APPLIED', '2026-10-25 20:00:00'
       UNION ALL SELECT (@cx_sm + 61), @cx_f1, @cx_s_농구, @cx_f1_mgr, '노원 농구장 일요일 오전 4:4', '2026-10-25 09:00:00', '2026-10-25 11:00:00', 8, 8, 4, 4, 0, '중급', '종료', '/images/matches/basketball.jpg', 'MANUAL', @cx_f1_mgr, '2026-10-25 08:00:00', 'CONFIRMED', 'B_WIN', '2026-10-25 13:05:00', 'APPLIED', '2026-10-26 11:00:00'
       UNION ALL SELECT (@cx_sm + 62), @cx_f23, @cx_s_배드민턴, @cx_f23_mgr, '뚝섬한강공원 배드민턴장 화요일 저녁 2:2', '2026-10-27 18:00:00', '2026-10-27 20:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f23_mgr, '2026-10-27 17:00:00', 'CONFIRMED', 'B_WIN', '2026-10-27 21:38:00', 'APPLIED', '2026-10-28 20:00:00'
       UNION ALL SELECT (@cx_sm + 63), @cx_f15, @cx_s_테니스, @cx_f15_mgr, '강서 테니스장 수요일 저녁 2:2', '2026-10-28 20:00:00', '2026-10-28 22:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f15_mgr, '2026-10-28 19:00:00', 'CONFIRMED', 'B_WIN', '2026-10-28 22:50:00', 'APPLIED', '2026-10-29 22:00:00'
       UNION ALL SELECT (@cx_sm + 64), @cx_f18, @cx_s_축구, @cx_f18_mgr, '망원유수지 풋살장 목요일 저녁 5:5', '2026-10-29 19:00:00', '2026-10-29 21:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cx_f18_mgr, '2026-10-29 18:00:00', 'CONFIRMED', 'B_WIN', '2026-10-29 23:19:00', 'APPLIED', '2026-10-30 21:00:00'
       UNION ALL SELECT (@cx_sm + 65), @cx_f14, @cx_s_족구, @cx_f14_mgr, '관악 족구장 토요일 오전 3:3', '2026-10-31 09:00:00', '2026-10-31 11:00:00', 6, 6, 3, 3, 0, '중급', '종료', '/images/matches/jokgu.jpg', 'MANUAL', @cx_f14_mgr, '2026-10-31 08:00:00', 'CONFIRMED', 'DRAW', '2026-10-31 12:40:00', 'APPLIED', '2026-11-01 11:00:00'
       UNION ALL SELECT (@cx_sm + 66), @cx_f20, @cx_s_농구, @cx_f20_mgr, '반포한강공원 농구장 토요일 저녁 4:4', '2026-10-31 18:00:00', '2026-10-31 20:00:00', 8, 8, 4, 4, 0, '중급', '종료', '/images/matches/basketball.jpg', 'MANUAL', @cx_f20_mgr, '2026-10-31 17:00:00', 'CONFIRMED', 'B_WIN', '2026-10-31 21:47:00', 'APPLIED', '2026-11-01 20:00:00'
       UNION ALL SELECT (@cx_sm + 67), @cx_f6, @cx_s_테니스, @cx_f6_mgr, '서초 스포츠센터 목요일 저녁 2:2', '2026-11-05 19:30:00', '2026-11-05 21:30:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/tennis.jpg', 'MANUAL', @cx_f6_mgr, '2026-11-05 18:30:00', 'CONFIRMED', 'B_WIN', '2026-11-05 23:13:00', 'APPLIED', '2026-11-06 21:30:00'
       UNION ALL SELECT (@cx_sm + 68), @cx_f23, @cx_s_배드민턴, @cx_f23_mgr, '뚝섬한강공원 배드민턴장 금요일 저녁 2:2', '2026-11-06 18:00:00', '2026-11-06 20:00:00', 4, 4, 2, 2, 0, '중급', '종료', '/images/matches/badminton.jpg', 'MANUAL', @cx_f23_mgr, '2026-11-06 17:00:00', 'CONFIRMED', 'A_WIN', '2026-11-06 22:03:00', 'APPLIED', '2026-11-07 20:00:00'
       UNION ALL SELECT (@cx_sm + 69), @cx_f8, @cx_s_농구, @cx_f8_mgr, '광나루 농구장 토요일 오전 4:4', '2026-11-07 10:00:00', '2026-11-07 12:00:00', 8, 8, 4, 4, 0, '중급', '종료', '/images/matches/basketball.jpg', 'MANUAL', @cx_f8_mgr, '2026-11-07 09:00:00', 'CONFIRMED', 'A_WIN', '2026-11-07 14:53:00', 'APPLIED', '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_sm + 70), @cx_f22, @cx_s_족구, @cx_f22_mgr, '뚝섬한강공원 족구장 토요일 오전 3:3', '2026-11-07 10:00:00', '2026-11-07 12:00:00', 6, 6, 3, 3, 0, '중급', '종료', '/images/matches/jokgu.jpg', 'MANUAL', @cx_f22_mgr, '2026-11-07 09:00:00', 'CONFIRMED', 'A_WIN', '2026-11-07 13:23:00', 'APPLIED', '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_sm + 71), @cx_f19, @cx_s_축구, @cx_f19_mgr, '잠실 제2풋살경기장 토요일 오후 5:5', '2026-11-07 14:00:00', '2026-11-07 16:00:00', 10, 10, 5, 5, 0, '중급', '종료', '/images/matches/soccer.jpg', 'MANUAL', @cx_f19_mgr, '2026-11-07 13:00:00', 'CONFIRMED', 'DRAW', '2026-11-07 17:16:00', 'APPLIED', '2026-11-08 16:00:00'
       UNION ALL SELECT (@cx_sm + 72), @cx_f1, @cx_s_농구, @cx_f1_mgr, '노원 농구장 일요일 오전 4:4', '2026-11-29 09:00:00', '2026-11-29 11:00:00', 8, 8, 4, 4, 0, '중급', '모집 중', '/images/matches/basketball.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
       UNION ALL SELECT (@cx_sm + 73), @cx_f2, @cx_s_테니스, @cx_f2_mgr, '월드컵공원 테니스장 금요일 오전 2:2', '2026-11-27 07:00:00', '2026-11-27 09:00:00', 4, 4, 2, 2, 0, '중급', '모집 중', '/images/matches/tennis.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
       UNION ALL SELECT (@cx_sm + 74), @cx_f3, @cx_s_테니스, @cx_f3_mgr, '서초 테니스클럽 목요일 저녁 2:2', '2026-11-26 21:00:00', '2026-11-26 23:00:00', 4, 4, 2, 2, 0, '중급', '모집 중', '/images/matches/tennis.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
       UNION ALL SELECT (@cx_sm + 75), @cx_f4, @cx_s_배드민턴, @cx_f4_mgr, '중랑물재생 배드민턴장 토요일 저녁 2:2', '2026-11-28 19:00:00', '2026-11-28 21:00:00', 4, 4, 2, 2, 0, '중급', '모집 중', '/images/matches/badminton.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
       UNION ALL SELECT (@cx_sm + 76), @cx_f5, @cx_s_배드민턴, @cx_f5_mgr, '광진 배드민턴장 금요일 저녁 2:2', '2026-12-04 20:00:00', '2026-12-04 22:00:00', 4, 4, 2, 2, 0, '중급', '모집 중', '/images/matches/badminton.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
       UNION ALL SELECT (@cx_sm + 77), @cx_f6, @cx_s_테니스, @cx_f6_mgr, '서초 스포츠센터 목요일 저녁 2:2', '2026-11-26 19:30:00', '2026-11-26 21:30:00', 4, 4, 2, 2, 0, '중급', '모집 중', '/images/matches/tennis.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
       UNION ALL SELECT (@cx_sm + 78), @cx_f7, @cx_s_테니스, @cx_f7_mgr, '영등포 테니스장 수요일 저녁 2:2', '2026-11-25 20:00:00', '2026-11-25 22:00:00', 4, 4, 2, 2, 0, '중급', '모집 중', '/images/matches/tennis.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
       UNION ALL SELECT (@cx_sm + 79), @cx_f8, @cx_s_농구, @cx_f8_mgr, '광나루 농구장 토요일 오전 4:4', '2026-12-05 10:00:00', '2026-12-05 12:00:00', 8, 8, 4, 4, 0, '중급', '모집 중', '/images/matches/basketball.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
       UNION ALL SELECT (@cx_sm + 80), @cx_f9, @cx_s_배드민턴, @cx_f9_mgr, '송파 배드민턴홀 일요일 오전 2:2', '2026-11-29 09:00:00', '2026-11-29 12:00:00', 4, 4, 2, 2, 0, '중급', '모집 중', '/images/matches/badminton.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
       UNION ALL SELECT (@cx_sm + 81), @cx_f10, @cx_s_축구, @cx_f10_mgr, '은평 풋살아레나 수요일 저녁 5:5', '2026-12-02 20:00:00', '2026-12-02 22:00:00', 10, 10, 5, 5, 0, '중급', '모집 중', '/images/matches/soccer.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
       UNION ALL SELECT (@cx_sm + 82), @cx_f11, @cx_s_테니스, @cx_f11_mgr, '어린이대공원 테니스장 일요일 오전 2:2', '2026-12-06 08:00:00', '2026-12-06 10:00:00', 4, 4, 2, 2, 0, '중급', '모집 중', '/images/matches/tennis.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
       UNION ALL SELECT (@cx_sm + 83), @cx_f12, @cx_s_배드민턴, @cx_f12_mgr, '강서 배드민턴장 화요일 저녁 2:2', '2026-11-24 20:00:00', '2026-11-24 22:00:00', 4, 4, 2, 2, 0, '중급', '모집 중', '/images/matches/badminton.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
       UNION ALL SELECT (@cx_sm + 84), @cx_f13, @cx_s_농구, @cx_f13_mgr, '영등포 농구센터 금요일 저녁 4:4', '2026-12-04 19:00:00', '2026-12-04 21:00:00', 8, 8, 4, 4, 0, '중급', '모집 중', '/images/matches/basketball.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
       UNION ALL SELECT (@cx_sm + 85), @cx_f14, @cx_s_족구, @cx_f14_mgr, '관악 족구장 토요일 오전 3:3', '2026-11-28 09:00:00', '2026-11-28 11:00:00', 6, 6, 3, 3, 0, '중급', '모집 중', '/images/matches/jokgu.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
       UNION ALL SELECT (@cx_sm + 86), @cx_f15, @cx_s_테니스, @cx_f15_mgr, '강서 테니스장 수요일 저녁 2:2', '2026-11-25 20:00:00', '2026-11-25 22:00:00', 4, 4, 2, 2, 0, '중급', '모집 중', '/images/matches/tennis.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
       UNION ALL SELECT (@cx_sm + 87), @cx_f16, @cx_s_배드민턴, @cx_f16_mgr, '송파 배드민턴코트 일요일 오전 2:2', '2026-12-06 09:00:00', '2026-12-06 11:00:00', 4, 4, 2, 2, 0, '중급', '모집 중', '/images/matches/badminton.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
       UNION ALL SELECT (@cx_sm + 88), @cx_f22, @cx_s_족구, @cx_f22_mgr, '뚝섬한강공원 족구장 토요일 오전 3:3', '2026-12-05 10:00:00', '2026-12-05 12:00:00', 6, 6, 3, 3, 0, '중급', '모집 중', '/images/matches/jokgu.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
       UNION ALL SELECT (@cx_sm + 89), @cx_f23, @cx_s_배드민턴, @cx_f23_mgr, '뚝섬한강공원 배드민턴장 금요일 저녁 2:2', '2026-12-04 18:00:00', '2026-12-04 20:00:00', 4, 4, 2, 2, 0, '중급', '모집 중', '/images/matches/badminton.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
       UNION ALL SELECT (@cx_sm + 90), @cx_f24, @cx_s_테니스, @cx_f24_mgr, '망원나들목 테니스장 토요일 오전 2:2', '2026-11-28 10:00:00', '2026-11-28 12:00:00', 4, 4, 2, 2, 0, '중급', '모집 중', '/images/matches/tennis.jpg', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', NULL
) v
 WHERE @cx_ready = 1;

-- 4. 팀 신청: 동호회마다 대표 1명 + 팀원. 지난 경기는 시설 담당자가 확정, 예정 매칭방은 대기. 행을 넣은 뒤 묶음 번호를 붙인다.
INSERT INTO match_application (application_id, match_id, applicant_user_id, club_id, application_type, status, receipt_no, created_at, confirmed_at)
SELECT * FROM (
                 SELECT (@cx_a + 1) AS application_id, (@cx_sm + 1) AS match_id, (@cx_u + 31) AS applicant_user_id, @cx_c10 AS club_id, '동호회' AS application_type, '확정' AS status, 'RCPT-20260730-S4JACR8K' AS receipt_no, '2026-07-30 04:00:00' AS created_at, '2026-07-30 06:15:00' AS confirmed_at
       UNION ALL SELECT (@cx_a + 2), (@cx_sm + 1), (@cx_u + 32), @cx_c10, '동호회', '확정', 'RCPT-20260730-L2LK468E', '2026-07-30 04:00:00', '2026-07-30 06:17:00'
       UNION ALL SELECT (@cx_a + 3), (@cx_sm + 1), (@cx_u + 34), @cx_c10, '동호회', '확정', 'RCPT-20260730-JXFWZ7E8', '2026-07-30 04:00:00', '2026-07-30 06:20:00'
       UNION ALL SELECT (@cx_a + 4), (@cx_sm + 1), (@cx_u + 33), @cx_c10, '동호회', '확정', 'RCPT-20260730-PDHX5TRW', '2026-07-30 04:00:00', '2026-07-30 06:06:00'
       UNION ALL SELECT (@cx_a + 5), (@cx_sm + 1), (@cx_u + 56), @cx_c15, '동호회', '확정', 'RCPT-20260730-N9DAYDFX', '2026-07-30 07:00:00', '2026-07-30 08:52:00'
       UNION ALL SELECT (@cx_a + 6), (@cx_sm + 1), (@cx_u + 57), @cx_c15, '동호회', '확정', 'RCPT-20260730-24SV2WCF', '2026-07-30 07:00:00', '2026-07-30 08:17:00'
       UNION ALL SELECT (@cx_a + 7), (@cx_sm + 1), (@cx_u + 60), @cx_c15, '동호회', '확정', 'RCPT-20260730-499VS35A', '2026-07-30 07:00:00', '2026-07-30 08:34:00'
       UNION ALL SELECT (@cx_a + 8), (@cx_sm + 1), (@cx_u + 61), @cx_c15, '동호회', '확정', 'RCPT-20260730-KPNYLTUW', '2026-07-30 07:00:00', '2026-07-30 09:04:00'
       UNION ALL SELECT (@cx_a + 9), (@cx_sm + 2), (@cx_u + 48), @cx_c13, '동호회', '확정', 'RCPT-20260728-YK6U475U', '2026-07-28 23:00:00', '2026-07-29 01:18:00'
       UNION ALL SELECT (@cx_a + 10), (@cx_sm + 2), (@cx_u + 51), @cx_c13, '동호회', '확정', 'RCPT-20260728-5Q98Z24A', '2026-07-28 23:00:00', '2026-07-29 01:58:00'
       UNION ALL SELECT (@cx_a + 11), (@cx_sm + 2), (@cx_u + 66), @cx_c18, '동호회', '확정', 'RCPT-20260731-VMZ3KFEM', '2026-07-31 01:00:00', '2026-07-31 02:14:00'
       UNION ALL SELECT (@cx_a + 12), (@cx_sm + 2), (@cx_u + 68), @cx_c18, '동호회', '확정', 'RCPT-20260731-58MKD5JW', '2026-07-31 01:00:00', '2026-07-31 02:38:00'
       UNION ALL SELECT (@cx_a + 13), (@cx_sm + 3), (@cx_u + 70), @cx_c19, '동호회', '확정', 'RCPT-20260729-7GHV43TE', '2026-07-29 09:00:00', '2026-07-29 10:44:00'
       UNION ALL SELECT (@cx_a + 14), (@cx_sm + 3), (@cx_u + 73), @cx_c19, '동호회', '확정', 'RCPT-20260729-V9RVF3NH', '2026-07-29 09:00:00', '2026-07-29 10:26:00'
       UNION ALL SELECT (@cx_a + 15), (@cx_sm + 3), (@cx_u + 19), @cx_c7, '동호회', '확정', 'RCPT-20260731-TBMMJKSK', '2026-07-31 02:00:00', '2026-07-31 03:16:00'
       UNION ALL SELECT (@cx_a + 16), (@cx_sm + 3), (@cx_u + 22), @cx_c7, '동호회', '확정', 'RCPT-20260731-LKREGUMY', '2026-07-31 02:00:00', '2026-07-31 04:44:00'
       UNION ALL SELECT (@cx_a + 17), (@cx_sm + 4), (@cx_u + 52), @cx_c14, '동호회', '확정', 'RCPT-20260802-97B8L9JJ', '2026-08-02 10:00:00', '2026-08-02 12:19:00'
       UNION ALL SELECT (@cx_a + 18), (@cx_sm + 4), (@cx_u + 55), @cx_c14, '동호회', '확정', 'RCPT-20260802-72FCXCJN', '2026-08-02 10:00:00', '2026-08-02 12:20:00'
       UNION ALL SELECT (@cx_a + 19), (@cx_sm + 4), (@cx_u + 15), @cx_c6, '동호회', '확정', 'RCPT-20260801-79AX6CTF', '2026-08-01 20:00:00', '2026-08-01 21:11:00'
       UNION ALL SELECT (@cx_a + 20), (@cx_sm + 4), (@cx_u + 18), @cx_c6, '동호회', '확정', 'RCPT-20260801-858WXD7A', '2026-08-01 20:00:00', '2026-08-01 22:20:00'
       UNION ALL SELECT (@cx_a + 21), (@cx_sm + 5), (@cx_u + 7), @cx_c4, '동호회', '확정', 'RCPT-20260804-6WLXHMCD', '2026-08-04 03:00:00', '2026-08-04 04:04:00'
       UNION ALL SELECT (@cx_a + 22), (@cx_sm + 5), (@cx_u + 8), @cx_c4, '동호회', '확정', 'RCPT-20260804-5LA2LKYK', '2026-08-04 03:00:00', '2026-08-04 05:15:00'
       UNION ALL SELECT (@cx_a + 23), (@cx_sm + 5), (@cx_u + 23), @cx_c8, '동호회', '확정', 'RCPT-20260805-6Y3D2279', '2026-08-05 07:00:00', '2026-08-05 08:28:00'
       UNION ALL SELECT (@cx_a + 24), (@cx_sm + 5), (@cx_u + 24), @cx_c8, '동호회', '확정', 'RCPT-20260805-XG9U5685', '2026-08-05 07:00:00', '2026-08-05 09:42:00'
       UNION ALL SELECT (@cx_a + 25), (@cx_sm + 6), (@cx_u + 1), @cx_c2, '동호회', '확정', 'RCPT-20260807-GHPJ3QVA', '2026-08-07 00:00:00', '2026-08-07 02:08:00'
       UNION ALL SELECT (@cx_a + 26), (@cx_sm + 6), (@cx_u + 2), @cx_c2, '동호회', '확정', 'RCPT-20260807-CP5B6NFE', '2026-08-07 00:00:00', '2026-08-07 01:47:00'
       UNION ALL SELECT (@cx_a + 27), (@cx_sm + 6), (@cx_u + 4), @cx_c2, '동호회', '확정', 'RCPT-20260807-MM79ARUG', '2026-08-07 00:00:00', '2026-08-07 01:29:00'
       UNION ALL SELECT (@cx_a + 28), (@cx_sm + 6), (@cx_u + 6), @cx_c2, '동호회', '확정', 'RCPT-20260807-ZFWD9FKK', '2026-08-07 00:00:00', '2026-08-07 01:17:00'
       UNION ALL SELECT (@cx_a + 29), (@cx_sm + 6), (@cx_u + 31), @cx_c10, '동호회', '확정', 'RCPT-20260807-5SYMFCPR', '2026-08-07 05:00:00', '2026-08-07 06:48:00'
       UNION ALL SELECT (@cx_a + 30), (@cx_sm + 6), (@cx_u + 32), @cx_c10, '동호회', '확정', 'RCPT-20260807-LBP62TDC', '2026-08-07 05:00:00', '2026-08-07 07:24:00'
       UNION ALL SELECT (@cx_a + 31), (@cx_sm + 6), (@cx_u + 35), @cx_c10, '동호회', '확정', 'RCPT-20260807-ECX869UY', '2026-08-07 05:00:00', '2026-08-07 07:03:00'
       UNION ALL SELECT (@cx_a + 32), (@cx_sm + 6), (@cx_u + 36), @cx_c10, '동호회', '확정', 'RCPT-20260807-Q75K44N8', '2026-08-07 05:00:00', '2026-08-07 06:49:00'
       UNION ALL SELECT (@cx_a + 33), (@cx_sm + 7), (@cx_u + 11), @cx_c5, '동호회', '확정', 'RCPT-20260807-CD2C68HW', '2026-08-07 20:00:00', '2026-08-07 21:45:00'
       UNION ALL SELECT (@cx_a + 34), (@cx_sm + 7), (@cx_u + 13), @cx_c5, '동호회', '확정', 'RCPT-20260807-HH3KHDN7', '2026-08-07 20:00:00', '2026-08-07 21:04:00'
       UNION ALL SELECT (@cx_a + 35), (@cx_sm + 7), (@cx_u + 48), @cx_c13, '동호회', '확정', 'RCPT-20260807-799WEYDP', '2026-08-07 13:00:00', '2026-08-07 15:13:00'
       UNION ALL SELECT (@cx_a + 36), (@cx_sm + 7), (@cx_u + 50), @cx_c13, '동호회', '확정', 'RCPT-20260807-CDLB5AHA', '2026-08-07 13:00:00', '2026-08-07 15:37:00'
       UNION ALL SELECT (@cx_a + 37), (@cx_sm + 8), (@cx_u + 23), @cx_c8, '동호회', '확정', 'RCPT-20260811-467XSRC9', '2026-08-11 16:30:00', '2026-08-11 17:35:00'
       UNION ALL SELECT (@cx_a + 38), (@cx_sm + 8), (@cx_u + 26), @cx_c8, '동호회', '확정', 'RCPT-20260811-6NS3HK78', '2026-08-11 16:30:00', '2026-08-11 19:29:00'
       UNION ALL SELECT (@cx_a + 39), (@cx_sm + 8), (@cx_u + 27), @cx_c9, '동호회', '확정', 'RCPT-20260810-MBSHN9UF', '2026-08-10 14:30:00', '2026-08-10 16:11:00'
       UNION ALL SELECT (@cx_a + 40), (@cx_sm + 8), (@cx_u + 30), @cx_c9, '동호회', '확정', 'RCPT-20260810-TREG5E7X', '2026-08-10 14:30:00', '2026-08-10 16:25:00'
       UNION ALL SELECT (@cx_a + 41), (@cx_sm + 9), (@cx_u + 66), @cx_c18, '동호회', '확정', 'RCPT-20260815-25FTHJL4', '2026-08-15 14:00:00', '2026-08-15 15:51:00'
       UNION ALL SELECT (@cx_a + 42), (@cx_sm + 9), (@cx_u + 67), @cx_c18, '동호회', '확정', 'RCPT-20260815-NBHJAJ3Q', '2026-08-15 14:00:00', '2026-08-15 15:23:00'
       UNION ALL SELECT (@cx_a + 43), (@cx_sm + 9), (@cx_u + 27), @cx_c9, '동호회', '확정', 'RCPT-20260816-FEWZW7MG', '2026-08-16 13:00:00', '2026-08-16 14:08:00'
       UNION ALL SELECT (@cx_a + 44), (@cx_sm + 9), (@cx_u + 28), @cx_c9, '동호회', '확정', 'RCPT-20260816-ZSN3BUMG', '2026-08-16 13:00:00', '2026-08-16 15:29:00'
       UNION ALL SELECT (@cx_a + 45), (@cx_sm + 10), (@cx_u + 11), @cx_c5, '동호회', '확정', 'RCPT-20260818-PRTW882U', '2026-08-18 11:00:00', '2026-08-18 12:33:00'
       UNION ALL SELECT (@cx_a + 46), (@cx_sm + 10), (@cx_u + 14), @cx_c5, '동호회', '확정', 'RCPT-20260818-4UDK4HQU', '2026-08-18 11:00:00', '2026-08-18 13:50:00'
       UNION ALL SELECT (@cx_a + 47), (@cx_sm + 10), (@cx_u + 7), @cx_c4, '동호회', '확정', 'RCPT-20260818-NGY9KBED', '2026-08-18 16:00:00', '2026-08-18 17:50:00'
       UNION ALL SELECT (@cx_a + 48), (@cx_sm + 10), (@cx_u + 10), @cx_c4, '동호회', '확정', 'RCPT-20260818-CZBEM8S8', '2026-08-18 16:00:00', '2026-08-18 18:11:00'
       UNION ALL SELECT (@cx_a + 49), (@cx_sm + 11), (@cx_u + 19), @cx_c7, '동호회', '확정', 'RCPT-20260817-MFED8AC4', '2026-08-17 19:00:00', '2026-08-17 21:16:00'
       UNION ALL SELECT (@cx_a + 50), (@cx_sm + 11), (@cx_u + 21), @cx_c7, '동호회', '확정', 'RCPT-20260817-CZV47A8E', '2026-08-17 19:00:00', '2026-08-17 21:17:00'
       UNION ALL SELECT (@cx_a + 51), (@cx_sm + 11), (@cx_u + 15), @cx_c6, '동호회', '확정', 'RCPT-20260819-JAGP96MG', '2026-08-19 18:00:00', '2026-08-19 19:13:00'
       UNION ALL SELECT (@cx_a + 52), (@cx_sm + 11), (@cx_u + 17), @cx_c6, '동호회', '확정', 'RCPT-20260819-CNVFZA72', '2026-08-19 18:00:00', '2026-08-19 19:24:00'
       UNION ALL SELECT (@cx_a + 53), (@cx_sm + 12), (@cx_u + 1), @cx_c2, '동호회', '확정', 'RCPT-20260821-E6FFF83M', '2026-08-21 07:00:00', '2026-08-21 09:21:00'
       UNION ALL SELECT (@cx_a + 54), (@cx_sm + 12), (@cx_u + 2), @cx_c2, '동호회', '확정', 'RCPT-20260821-FP49TQ37', '2026-08-21 07:00:00', '2026-08-21 08:10:00'
       UNION ALL SELECT (@cx_a + 55), (@cx_sm + 12), (@cx_u + 3), @cx_c2, '동호회', '확정', 'RCPT-20260821-H8E6ULGL', '2026-08-21 07:00:00', '2026-08-21 09:12:00'
       UNION ALL SELECT (@cx_a + 56), (@cx_sm + 12), (@cx_u + 5), @cx_c2, '동호회', '확정', 'RCPT-20260821-NLQGNF7C', '2026-08-21 07:00:00', '2026-08-21 08:41:00'
       UNION ALL SELECT (@cx_a + 57), (@cx_sm + 12), (@cx_u + 56), @cx_c15, '동호회', '확정', 'RCPT-20260820-CTTJM3RX', '2026-08-20 09:00:00', '2026-08-20 10:48:00'
       UNION ALL SELECT (@cx_a + 58), (@cx_sm + 12), (@cx_u + 57), @cx_c15, '동호회', '확정', 'RCPT-20260820-SNEFZV8K', '2026-08-20 09:00:00', '2026-08-20 11:34:00'
       UNION ALL SELECT (@cx_a + 59), (@cx_sm + 12), (@cx_u + 59), @cx_c15, '동호회', '확정', 'RCPT-20260820-K4NP3XH5', '2026-08-20 09:00:00', '2026-08-20 10:46:00'
       UNION ALL SELECT (@cx_a + 60), (@cx_sm + 12), (@cx_u + 58), @cx_c15, '동호회', '확정', 'RCPT-20260820-7S6PENE2', '2026-08-20 09:00:00', '2026-08-20 11:22:00'
       UNION ALL SELECT (@cx_a + 61), (@cx_sm + 13), (@cx_u + 23), @cx_c8, '동호회', '확정', 'RCPT-20260824-NVE4PDPT', '2026-08-24 10:30:00', '2026-08-24 12:51:00'
       UNION ALL SELECT (@cx_a + 62), (@cx_sm + 13), (@cx_u + 25), @cx_c8, '동호회', '확정', 'RCPT-20260824-M572KNUA', '2026-08-24 10:30:00', '2026-08-24 13:16:00'
       UNION ALL SELECT (@cx_a + 63), (@cx_sm + 13), (@cx_u + 7), @cx_c4, '동호회', '확정', 'RCPT-20260824-C6N9D989', '2026-08-24 13:30:00', '2026-08-24 16:28:00'
       UNION ALL SELECT (@cx_a + 64), (@cx_sm + 13), (@cx_u + 9), @cx_c4, '동호회', '확정', 'RCPT-20260824-3R8Z8WD8', '2026-08-24 13:30:00', '2026-08-24 14:49:00'
       UNION ALL SELECT (@cx_a + 65), (@cx_sm + 14), (@cx_u + 19), @cx_c7, '동호회', '확정', 'RCPT-20260826-82ZDNWGU', '2026-08-26 14:00:00', '2026-08-26 16:07:00'
       UNION ALL SELECT (@cx_a + 66), (@cx_sm + 14), (@cx_u + 20), @cx_c7, '동호회', '확정', 'RCPT-20260826-J7WHD522', '2026-08-26 14:00:00', '2026-08-26 16:56:00'
       UNION ALL SELECT (@cx_a + 67), (@cx_sm + 14), (@cx_u + 52), @cx_c14, '동호회', '확정', 'RCPT-20260826-ZXS829M8', '2026-08-26 20:00:00', '2026-08-26 22:19:00'
       UNION ALL SELECT (@cx_a + 68), (@cx_sm + 14), (@cx_u + 53), @cx_c14, '동호회', '확정', 'RCPT-20260826-2JDKYRT4', '2026-08-26 20:00:00', '2026-08-26 22:47:00'
       UNION ALL SELECT (@cx_a + 69), (@cx_sm + 15), (@cx_u + 48), @cx_c13, '동호회', '확정', 'RCPT-20260825-AZNBR63P', '2026-08-25 05:00:00', '2026-08-25 07:28:00'
       UNION ALL SELECT (@cx_a + 70), (@cx_sm + 15), (@cx_u + 49), @cx_c13, '동호회', '확정', 'RCPT-20260825-V5WBUAEM', '2026-08-25 05:00:00', '2026-08-25 06:13:00'
       UNION ALL SELECT (@cx_a + 71), (@cx_sm + 15), (@cx_u + 27), @cx_c9, '동호회', '확정', 'RCPT-20260827-CB9CH8LK', '2026-08-27 01:00:00', '2026-08-27 02:42:00'
       UNION ALL SELECT (@cx_a + 72), (@cx_sm + 15), (@cx_u + 29), @cx_c9, '동호회', '확정', 'RCPT-20260827-PMLKCSHF', '2026-08-27 01:00:00', '2026-08-27 02:13:00'
       UNION ALL SELECT (@cx_a + 73), (@cx_sm + 16), (@cx_u + 31), @cx_c10, '동호회', '확정', 'RCPT-20260827-PJK3ASNS', '2026-08-27 04:00:00', '2026-08-27 06:43:00'
       UNION ALL SELECT (@cx_a + 74), (@cx_sm + 16), (@cx_u + 32), @cx_c10, '동호회', '확정', 'RCPT-20260827-5SQCPDFR', '2026-08-27 04:00:00', '2026-08-27 05:36:00'
       UNION ALL SELECT (@cx_a + 75), (@cx_sm + 16), (@cx_u + 35), @cx_c10, '동호회', '확정', 'RCPT-20260827-E7YEJ47K', '2026-08-27 04:00:00', '2026-08-27 06:50:00'
       UNION ALL SELECT (@cx_a + 76), (@cx_sm + 16), (@cx_u + 36), @cx_c10, '동호회', '확정', 'RCPT-20260827-TETFL469', '2026-08-27 04:00:00', '2026-08-27 06:58:00'
       UNION ALL SELECT (@cx_a + 77), (@cx_sm + 16), (@cx_u + 56), @cx_c15, '동호회', '확정', 'RCPT-20260825-22KYXGGN', '2026-08-25 07:00:00', '2026-08-25 08:34:00'
       UNION ALL SELECT (@cx_a + 78), (@cx_sm + 16), (@cx_u + 57), @cx_c15, '동호회', '확정', 'RCPT-20260825-M8W8BR8J', '2026-08-25 07:00:00', '2026-08-25 09:10:00'
       UNION ALL SELECT (@cx_a + 79), (@cx_sm + 16), (@cx_u + 58), @cx_c15, '동호회', '확정', 'RCPT-20260825-TVBHEZ29', '2026-08-25 07:00:00', '2026-08-25 09:21:00'
       UNION ALL SELECT (@cx_a + 80), (@cx_sm + 16), (@cx_u + 61), @cx_c15, '동호회', '확정', 'RCPT-20260825-MRLU7DHU', '2026-08-25 07:00:00', '2026-08-25 08:39:00'
       UNION ALL SELECT (@cx_a + 81), (@cx_sm + 17), (@cx_u + 52), @cx_c14, '동호회', '확정', 'RCPT-20260830-KZK3EJGL', '2026-08-30 04:00:00', '2026-08-30 06:48:00'
       UNION ALL SELECT (@cx_a + 82), (@cx_sm + 17), (@cx_u + 54), @cx_c14, '동호회', '확정', 'RCPT-20260830-T3QGHHKY', '2026-08-30 04:00:00', '2026-08-30 05:38:00'
       UNION ALL SELECT (@cx_a + 83), (@cx_sm + 17), (@cx_u + 37), @cx_c11, '동호회', '확정', 'RCPT-20260829-CE2JXPGD', '2026-08-29 18:00:00', '2026-08-29 20:44:00'
       UNION ALL SELECT (@cx_a + 84), (@cx_sm + 17), (@cx_u + 38), @cx_c11, '동호회', '확정', 'RCPT-20260829-H8QGVG2M', '2026-08-29 18:00:00', '2026-08-29 19:35:00'
       UNION ALL SELECT (@cx_a + 85), (@cx_sm + 18), (@cx_u + 11), @cx_c5, '동호회', '확정', 'RCPT-20260901-GTMVALLT', '2026-09-01 17:00:00', '2026-09-01 19:17:00'
       UNION ALL SELECT (@cx_a + 86), (@cx_sm + 18), (@cx_u + 12), @cx_c5, '동호회', '확정', 'RCPT-20260901-KDLQ4SM4', '2026-09-01 17:00:00', '2026-09-01 19:27:00'
       UNION ALL SELECT (@cx_a + 87), (@cx_sm + 18), (@cx_u + 66), @cx_c18, '동호회', '확정', 'RCPT-20260831-275S82BB', '2026-08-31 15:00:00', '2026-08-31 16:19:00'
       UNION ALL SELECT (@cx_a + 88), (@cx_sm + 18), (@cx_u + 69), @cx_c18, '동호회', '확정', 'RCPT-20260831-M657DBFC', '2026-08-31 15:00:00', '2026-08-31 16:12:00'
       UNION ALL SELECT (@cx_a + 89), (@cx_sm + 19), (@cx_u + 27), @cx_c9, '동호회', '확정', 'RCPT-20260908-3Z5WS5LP', '2026-09-08 13:00:00', '2026-09-08 14:15:00'
       UNION ALL SELECT (@cx_a + 90), (@cx_sm + 19), (@cx_u + 28), @cx_c9, '동호회', '확정', 'RCPT-20260908-WD8UMQU7', '2026-09-08 13:00:00', '2026-09-08 14:38:00'
       UNION ALL SELECT (@cx_a + 91), (@cx_sm + 19), (@cx_u + 7), @cx_c4, '동호회', '확정', 'RCPT-20260907-REBGTHP6', '2026-09-07 11:00:00', '2026-09-07 12:31:00'
       UNION ALL SELECT (@cx_a + 92), (@cx_sm + 19), (@cx_u + 10), @cx_c4, '동호회', '확정', 'RCPT-20260907-PNA8PVKG', '2026-09-07 11:00:00', '2026-09-07 12:42:00'
       UNION ALL SELECT (@cx_a + 93), (@cx_sm + 20), (@cx_u + 70), @cx_c19, '동호회', '확정', 'RCPT-20260909-LS9EWCVH', '2026-09-09 02:00:00', '2026-09-09 04:15:00'
       UNION ALL SELECT (@cx_a + 94), (@cx_sm + 20), (@cx_u + 71), @cx_c19, '동호회', '확정', 'RCPT-20260909-LVDYCGHX', '2026-09-09 02:00:00', '2026-09-09 03:33:00'
       UNION ALL SELECT (@cx_a + 95), (@cx_sm + 20), (@cx_u + 52), @cx_c14, '동호회', '확정', 'RCPT-20260909-UHAUF92K', '2026-09-09 05:00:00', '2026-09-09 06:06:00'
       UNION ALL SELECT (@cx_a + 96), (@cx_sm + 20), (@cx_u + 53), @cx_c14, '동호회', '확정', 'RCPT-20260909-99LUT3MP', '2026-09-09 05:00:00', '2026-09-09 06:16:00'
       UNION ALL SELECT (@cx_a + 97), (@cx_sm + 21), (@cx_u + 19), @cx_c7, '동호회', '확정', 'RCPT-20260912-BHD82HTK', '2026-09-12 14:00:00', '2026-09-12 15:16:00'
       UNION ALL SELECT (@cx_a + 98), (@cx_sm + 21), (@cx_u + 22), @cx_c7, '동호회', '확정', 'RCPT-20260912-9RUK2DCE', '2026-09-12 14:00:00', '2026-09-12 16:16:00'
       UNION ALL SELECT (@cx_a + 99), (@cx_sm + 21), (@cx_u + 37), @cx_c11, '동호회', '확정', 'RCPT-20260911-BA75E7TN', '2026-09-11 12:00:00', '2026-09-11 13:55:00'
       UNION ALL SELECT (@cx_a + 100), (@cx_sm + 21), (@cx_u + 40), @cx_c11, '동호회', '확정', 'RCPT-20260911-A3W8QSUY', '2026-09-11 12:00:00', '2026-09-11 14:04:00'
       UNION ALL SELECT (@cx_a + 101), (@cx_sm + 22), (@cx_u + 48), @cx_c13, '동호회', '확정', 'RCPT-20260915-U87CNU3Y', '2026-09-15 01:00:00', '2026-09-15 02:42:00'
       UNION ALL SELECT (@cx_a + 102), (@cx_sm + 22), (@cx_u + 51), @cx_c13, '동호회', '확정', 'RCPT-20260915-69T87CN3', '2026-09-15 01:00:00', '2026-09-15 03:00:00'
       UNION ALL SELECT (@cx_a + 103), (@cx_sm + 22), (@cx_u + 66), @cx_c18, '동호회', '확정', 'RCPT-20260917-NA9SDR48', '2026-09-17 00:00:00', '2026-09-17 02:33:00'
       UNION ALL SELECT (@cx_a + 104), (@cx_sm + 22), (@cx_u + 69), @cx_c18, '동호회', '확정', 'RCPT-20260917-SV5SFGUL', '2026-09-17 00:00:00', '2026-09-17 02:30:00'
       UNION ALL SELECT (@cx_a + 105), (@cx_sm + 23), (@cx_u + 31), @cx_c10, '동호회', '확정', 'RCPT-20260917-2P7SAQSJ', '2026-09-17 06:00:00', '2026-09-17 08:08:00'
       UNION ALL SELECT (@cx_a + 106), (@cx_sm + 23), (@cx_u + 32), @cx_c10, '동호회', '확정', 'RCPT-20260917-8RC4L6V6', '2026-09-17 06:00:00', '2026-09-17 08:58:00'
       UNION ALL SELECT (@cx_a + 107), (@cx_sm + 23), (@cx_u + 34), @cx_c10, '동호회', '확정', 'RCPT-20260917-ZESXRHD2', '2026-09-17 06:00:00', '2026-09-17 08:55:00'
       UNION ALL SELECT (@cx_a + 108), (@cx_sm + 23), (@cx_u + 33), @cx_c10, '동호회', '확정', 'RCPT-20260917-RH779JJS', '2026-09-17 06:00:00', '2026-09-17 07:20:00'
       UNION ALL SELECT (@cx_a + 109), (@cx_sm + 23), (@cx_u + 56), @cx_c15, '동호회', '확정', 'RCPT-20260915-XMZZFEE2', '2026-09-15 01:00:00', '2026-09-15 02:06:00'
       UNION ALL SELECT (@cx_a + 110), (@cx_sm + 23), (@cx_u + 57), @cx_c15, '동호회', '확정', 'RCPT-20260915-RLBTVH2M', '2026-09-15 01:00:00', '2026-09-15 03:38:00'
       UNION ALL SELECT (@cx_a + 111), (@cx_sm + 23), (@cx_u + 60), @cx_c15, '동호회', '확정', 'RCPT-20260915-R8F5V99J', '2026-09-15 01:00:00', '2026-09-15 02:02:00'
       UNION ALL SELECT (@cx_a + 112), (@cx_sm + 23), (@cx_u + 59), @cx_c15, '동호회', '확정', 'RCPT-20260915-9CZTAX3P', '2026-09-15 01:00:00', '2026-09-15 03:58:00'
       UNION ALL SELECT (@cx_a + 113), (@cx_sm + 24), (@cx_u + 23), @cx_c8, '동호회', '확정', 'RCPT-20260921-9FSU2KXD', '2026-09-21 10:30:00', '2026-09-21 11:37:00'
       UNION ALL SELECT (@cx_a + 114), (@cx_sm + 24), (@cx_u + 24), @cx_c8, '동호회', '확정', 'RCPT-20260921-TJZTQ46H', '2026-09-21 10:30:00', '2026-09-21 13:08:00'
       UNION ALL SELECT (@cx_a + 115), (@cx_sm + 24), (@cx_u + 11), @cx_c5, '동호회', '확정', 'RCPT-20260921-K8NW5S79', '2026-09-21 14:30:00', '2026-09-21 16:07:00'
       UNION ALL SELECT (@cx_a + 116), (@cx_sm + 24), (@cx_u + 12), @cx_c5, '동호회', '확정', 'RCPT-20260921-NDK73K66', '2026-09-21 14:30:00', '2026-09-21 15:52:00'
       UNION ALL SELECT (@cx_a + 117), (@cx_sm + 25), (@cx_u + 41), @cx_c12, '동호회', '확정', 'RCPT-20260922-M8WAWN3M', '2026-09-22 17:00:00', '2026-09-22 19:15:00'
       UNION ALL SELECT (@cx_a + 118), (@cx_sm + 25), (@cx_u + 42), @cx_c12, '동호회', '확정', 'RCPT-20260922-68T63MGV', '2026-09-22 17:00:00', '2026-09-22 18:13:00'
       UNION ALL SELECT (@cx_a + 119), (@cx_sm + 25), (@cx_u + 43), @cx_c12, '동호회', '확정', 'RCPT-20260922-KU5QLY4C', '2026-09-22 17:00:00', '2026-09-22 18:47:00'
       UNION ALL SELECT (@cx_a + 120), (@cx_sm + 25), (@cx_u + 46), @cx_c12, '동호회', '확정', 'RCPT-20260922-4KZVXNSP', '2026-09-22 17:00:00', '2026-09-22 18:20:00'
       UNION ALL SELECT (@cx_a + 121), (@cx_sm + 25), (@cx_u + 47), @cx_c12, '동호회', '확정', 'RCPT-20260922-SL7FPU83', '2026-09-22 17:00:00', '2026-09-22 18:10:00'
       UNION ALL SELECT (@cx_a + 122), (@cx_sm + 25), (@cx_u + 74), @cx_c21, '동호회', '확정', 'RCPT-20260924-V5KGU7LN', '2026-09-24 17:00:00', '2026-09-24 19:21:00'
       UNION ALL SELECT (@cx_a + 123), (@cx_sm + 25), (@cx_u + 75), @cx_c21, '동호회', '확정', 'RCPT-20260924-4U9JA2Q9', '2026-09-24 17:00:00', '2026-09-24 18:51:00'
       UNION ALL SELECT (@cx_a + 124), (@cx_sm + 25), (@cx_u + 76), @cx_c21, '동호회', '확정', 'RCPT-20260924-HB6NRZD6', '2026-09-24 17:00:00', '2026-09-24 19:11:00'
       UNION ALL SELECT (@cx_a + 125), (@cx_sm + 25), (@cx_u + 77), @cx_c21, '동호회', '확정', 'RCPT-20260924-PQRAFQMD', '2026-09-24 17:00:00', '2026-09-24 18:22:00'
       UNION ALL SELECT (@cx_a + 126), (@cx_sm + 25), (@cx_u + 80), @cx_c21, '동호회', '확정', 'RCPT-20260924-9YLCNA8T', '2026-09-24 17:00:00', '2026-09-24 19:25:00'
       UNION ALL SELECT (@cx_a + 127), (@cx_sm + 26), (@cx_u + 81), @cx_c22, '동호회', '확정', 'RCPT-20260926-THCRRUX6', '2026-09-26 11:00:00', '2026-09-26 12:15:00'
       UNION ALL SELECT (@cx_a + 128), (@cx_sm + 26), (@cx_u + 82), @cx_c22, '동호회', '확정', 'RCPT-20260926-Z9792X2Z', '2026-09-26 11:00:00', '2026-09-26 13:05:00'
       UNION ALL SELECT (@cx_a + 129), (@cx_sm + 26), (@cx_u + 83), @cx_c22, '동호회', '확정', 'RCPT-20260926-DLHXZR3X', '2026-09-26 11:00:00', '2026-09-26 12:37:00'
       UNION ALL SELECT (@cx_a + 130), (@cx_sm + 26), (@cx_u + 85), @cx_c22, '동호회', '확정', 'RCPT-20260926-B86RRK2G', '2026-09-26 11:00:00', '2026-09-26 12:59:00'
       UNION ALL SELECT (@cx_a + 131), (@cx_sm + 26), (@cx_u + 86), @cx_c22, '동호회', '확정', 'RCPT-20260926-BM9VWXBR', '2026-09-26 11:00:00', '2026-09-26 12:25:00'
       UNION ALL SELECT (@cx_a + 132), (@cx_sm + 26), (@cx_u + 74), @cx_c21, '동호회', '확정', 'RCPT-20260928-R9L9HRDD', '2026-09-28 00:00:00', '2026-09-28 01:50:00'
       UNION ALL SELECT (@cx_a + 133), (@cx_sm + 26), (@cx_u + 75), @cx_c21, '동호회', '확정', 'RCPT-20260928-B7AGBVGY', '2026-09-28 00:00:00', '2026-09-28 02:39:00'
       UNION ALL SELECT (@cx_a + 134), (@cx_sm + 26), (@cx_u + 76), @cx_c21, '동호회', '확정', 'RCPT-20260928-EBYEBGTS', '2026-09-28 00:00:00', '2026-09-28 02:32:00'
       UNION ALL SELECT (@cx_a + 135), (@cx_sm + 26), (@cx_u + 78), @cx_c21, '동호회', '확정', 'RCPT-20260928-YRGSA54A', '2026-09-28 00:00:00', '2026-09-28 01:17:00'
       UNION ALL SELECT (@cx_a + 136), (@cx_sm + 26), (@cx_u + 79), @cx_c21, '동호회', '확정', 'RCPT-20260928-QZ7ES9EE', '2026-09-28 00:00:00', '2026-09-28 01:21:00'
       UNION ALL SELECT (@cx_a + 137), (@cx_sm + 27), (@cx_u + 19), @cx_c7, '동호회', '확정', 'RCPT-20260927-BJJX7NPU', '2026-09-27 20:00:00', '2026-09-27 22:33:00'
       UNION ALL SELECT (@cx_a + 138), (@cx_sm + 27), (@cx_u + 21), @cx_c7, '동호회', '확정', 'RCPT-20260927-NY8YWM6A', '2026-09-27 20:00:00', '2026-09-27 22:09:00'
       UNION ALL SELECT (@cx_a + 139), (@cx_sm + 27), (@cx_u + 15), @cx_c6, '동호회', '확정', 'RCPT-20260927-8Z3LYDQ5', '2026-09-27 20:00:00', '2026-09-27 22:22:00'
       UNION ALL SELECT (@cx_a + 140), (@cx_sm + 27), (@cx_u + 16), @cx_c6, '동호회', '확정', 'RCPT-20260927-L5KM8ZF8', '2026-09-27 20:00:00', '2026-09-27 21:12:00'
       UNION ALL SELECT (@cx_a + 141), (@cx_sm + 28), (@cx_u + 41), @cx_c12, '동호회', '확정', 'RCPT-20260928-V3RXKBXT', '2026-09-28 10:00:00', '2026-09-28 11:40:00'
       UNION ALL SELECT (@cx_a + 142), (@cx_sm + 28), (@cx_u + 42), @cx_c12, '동호회', '확정', 'RCPT-20260928-CE35L4PQ', '2026-09-28 10:00:00', '2026-09-28 11:40:00'
       UNION ALL SELECT (@cx_a + 143), (@cx_sm + 28), (@cx_u + 43), @cx_c12, '동호회', '확정', 'RCPT-20260928-3C3WP8ME', '2026-09-28 10:00:00', '2026-09-28 11:38:00'
       UNION ALL SELECT (@cx_a + 144), (@cx_sm + 28), (@cx_u + 44), @cx_c12, '동호회', '확정', 'RCPT-20260928-CBX88RM7', '2026-09-28 10:00:00', '2026-09-28 12:21:00'
       UNION ALL SELECT (@cx_a + 145), (@cx_sm + 28), (@cx_u + 45), @cx_c12, '동호회', '확정', 'RCPT-20260928-LNVPQHHK', '2026-09-28 10:00:00', '2026-09-28 11:19:00'
       UNION ALL SELECT (@cx_a + 146), (@cx_sm + 28), (@cx_u + 88), @cx_c23, '동호회', '확정', 'RCPT-20260926-SMRC9JN6', '2026-09-26 20:00:00', '2026-09-26 22:44:00'
       UNION ALL SELECT (@cx_a + 147), (@cx_sm + 28), (@cx_u + 89), @cx_c23, '동호회', '확정', 'RCPT-20260926-853CJRVE', '2026-09-26 20:00:00', '2026-09-26 22:51:00'
       UNION ALL SELECT (@cx_a + 148), (@cx_sm + 28), (@cx_u + 90), @cx_c23, '동호회', '확정', 'RCPT-20260926-UMTZQZRY', '2026-09-26 20:00:00', '2026-09-26 21:44:00'
       UNION ALL SELECT (@cx_a + 149), (@cx_sm + 28), (@cx_u + 94), @cx_c23, '동호회', '확정', 'RCPT-20260926-UBQVYKNE', '2026-09-26 20:00:00', '2026-09-26 21:43:00'
       UNION ALL SELECT (@cx_a + 150), (@cx_sm + 28), (@cx_u + 93), @cx_c23, '동호회', '확정', 'RCPT-20260926-FQAPD3KQ', '2026-09-26 20:00:00', '2026-09-26 21:24:00'
       UNION ALL SELECT (@cx_a + 151), (@cx_sm + 29), (@cx_u + 56), @cx_c15, '동호회', '확정', 'RCPT-20260929-HJ3RRSMJ', '2026-09-29 19:00:00', '2026-09-29 21:19:00'
       UNION ALL SELECT (@cx_a + 152), (@cx_sm + 29), (@cx_u + 57), @cx_c15, '동호회', '확정', 'RCPT-20260929-KWVGU498', '2026-09-29 19:00:00', '2026-09-29 20:49:00'
       UNION ALL SELECT (@cx_a + 153), (@cx_sm + 29), (@cx_u + 60), @cx_c15, '동호회', '확정', 'RCPT-20260929-7M9FMVVJ', '2026-09-29 19:00:00', '2026-09-29 21:22:00'
       UNION ALL SELECT (@cx_a + 154), (@cx_sm + 29), (@cx_u + 58), @cx_c15, '동호회', '확정', 'RCPT-20260929-ZLJWFPLS', '2026-09-29 19:00:00', '2026-09-29 20:47:00'
       UNION ALL SELECT (@cx_a + 155), (@cx_sm + 29), (@cx_u + 95), @cx_c24, '동호회', '확정', 'RCPT-20260928-WA8Q8KBR', '2026-09-28 12:00:00', '2026-09-28 13:20:00'
       UNION ALL SELECT (@cx_a + 156), (@cx_sm + 29), (@cx_u + 96), @cx_c24, '동호회', '확정', 'RCPT-20260928-XHYGG4BS', '2026-09-28 12:00:00', '2026-09-28 13:34:00'
       UNION ALL SELECT (@cx_a + 157), (@cx_sm + 29), (@cx_u + 97), @cx_c24, '동호회', '확정', 'RCPT-20260928-VF2TQZ3U', '2026-09-28 12:00:00', '2026-09-28 14:23:00'
       UNION ALL SELECT (@cx_a + 158), (@cx_sm + 29), (@cx_u + 98), @cx_c24, '동호회', '확정', 'RCPT-20260928-NP8HJUQH', '2026-09-28 12:00:00', '2026-09-28 13:33:00'
       UNION ALL SELECT (@cx_a + 159), (@cx_sm + 30), (@cx_u + 1), @cx_c2, '동호회', '확정', 'RCPT-20260930-8FP3YBBG', '2026-09-30 01:00:00', '2026-09-30 02:06:00'
       UNION ALL SELECT (@cx_a + 160), (@cx_sm + 30), (@cx_u + 2), @cx_c2, '동호회', '확정', 'RCPT-20260930-M3TW6QER', '2026-09-30 01:00:00', '2026-09-30 02:42:00'
       UNION ALL SELECT (@cx_a + 161), (@cx_sm + 30), (@cx_u + 5), @cx_c2, '동호회', '확정', 'RCPT-20260930-6W4M63CA', '2026-09-30 01:00:00', '2026-09-30 02:37:00'
       UNION ALL SELECT (@cx_a + 162), (@cx_sm + 30), (@cx_u + 6), @cx_c2, '동호회', '확정', 'RCPT-20260930-A74VVLDQ', '2026-09-30 01:00:00', '2026-09-30 02:32:00'
       UNION ALL SELECT (@cx_a + 163), (@cx_sm + 30), (@cx_u + 31), @cx_c10, '동호회', '확정', 'RCPT-20260930-MCYHU3KR', '2026-09-30 04:00:00', '2026-09-30 05:29:00'
       UNION ALL SELECT (@cx_a + 164), (@cx_sm + 30), (@cx_u + 32), @cx_c10, '동호회', '확정', 'RCPT-20260930-GW5NWF2M', '2026-09-30 04:00:00', '2026-09-30 06:16:00'
       UNION ALL SELECT (@cx_a + 165), (@cx_sm + 30), (@cx_u + 35), @cx_c10, '동호회', '확정', 'RCPT-20260930-7EXURF5D', '2026-09-30 04:00:00', '2026-09-30 06:31:00'
       UNION ALL SELECT (@cx_a + 166), (@cx_sm + 30), (@cx_u + 33), @cx_c10, '동호회', '확정', 'RCPT-20260930-DHLFSUMJ', '2026-09-30 04:00:00', '2026-09-30 06:20:00'
       UNION ALL SELECT (@cx_a + 167), (@cx_sm + 31), (@cx_u + 74), @cx_c21, '동호회', '확정', 'RCPT-20261001-3DQ86XWE', '2026-10-01 03:00:00', '2026-10-01 04:59:00'
       UNION ALL SELECT (@cx_a + 168), (@cx_sm + 31), (@cx_u + 75), @cx_c21, '동호회', '확정', 'RCPT-20261001-XHEV4CT4', '2026-10-01 03:00:00', '2026-10-01 04:08:00'
       UNION ALL SELECT (@cx_a + 169), (@cx_sm + 31), (@cx_u + 76), @cx_c21, '동호회', '확정', 'RCPT-20261001-K54B2PZZ', '2026-10-01 03:00:00', '2026-10-01 04:16:00'
       UNION ALL SELECT (@cx_a + 170), (@cx_sm + 31), (@cx_u + 80), @cx_c21, '동호회', '확정', 'RCPT-20261001-W73WA79S', '2026-10-01 03:00:00', '2026-10-01 05:13:00'
       UNION ALL SELECT (@cx_a + 171), (@cx_sm + 31), (@cx_u + 78), @cx_c21, '동호회', '확정', 'RCPT-20261001-S5CES8R2', '2026-10-01 03:00:00', '2026-10-01 05:15:00'
       UNION ALL SELECT (@cx_a + 172), (@cx_sm + 31), (@cx_u + 88), @cx_c23, '동호회', '확정', 'RCPT-20261002-X7GYHWEH', '2026-10-02 02:00:00', '2026-10-02 03:33:00'
       UNION ALL SELECT (@cx_a + 173), (@cx_sm + 31), (@cx_u + 89), @cx_c23, '동호회', '확정', 'RCPT-20261002-3ZRN3GA3', '2026-10-02 02:00:00', '2026-10-02 04:38:00'
       UNION ALL SELECT (@cx_a + 174), (@cx_sm + 31), (@cx_u + 90), @cx_c23, '동호회', '확정', 'RCPT-20261002-EY9V86UL', '2026-10-02 02:00:00', '2026-10-02 04:27:00'
       UNION ALL SELECT (@cx_a + 175), (@cx_sm + 31), (@cx_u + 92), @cx_c23, '동호회', '확정', 'RCPT-20261002-AR4LR7WE', '2026-10-02 02:00:00', '2026-10-02 03:28:00'
       UNION ALL SELECT (@cx_a + 176), (@cx_sm + 31), (@cx_u + 91), @cx_c23, '동호회', '확정', 'RCPT-20261002-VAYR9CH5', '2026-10-02 02:00:00', '2026-10-02 03:03:00'
       UNION ALL SELECT (@cx_a + 177), (@cx_sm + 32), (@cx_u + 48), @cx_c13, '동호회', '확정', 'RCPT-20261001-QSELLJQ4', '2026-10-01 01:00:00', '2026-10-01 03:10:00'
       UNION ALL SELECT (@cx_a + 178), (@cx_sm + 32), (@cx_u + 49), @cx_c13, '동호회', '확정', 'RCPT-20261001-K8DXQR3E', '2026-10-01 01:00:00', '2026-10-01 02:27:00'
       UNION ALL SELECT (@cx_a + 179), (@cx_sm + 32), (@cx_u + 66), @cx_c18, '동호회', '확정', 'RCPT-20260929-D62SN5E8', '2026-09-29 22:00:00', '2026-09-29 23:29:00'
       UNION ALL SELECT (@cx_a + 180), (@cx_sm + 32), (@cx_u + 67), @cx_c18, '동호회', '확정', 'RCPT-20260929-YB4LQNTS', '2026-09-29 22:00:00', '2026-09-30 00:56:00'
       UNION ALL SELECT (@cx_a + 181), (@cx_sm + 33), (@cx_u + 81), @cx_c22, '동호회', '확정', 'RCPT-20261003-HSLTB5LB', '2026-10-03 12:00:00', '2026-10-03 14:20:00'
       UNION ALL SELECT (@cx_a + 182), (@cx_sm + 33), (@cx_u + 82), @cx_c22, '동호회', '확정', 'RCPT-20261003-Z8N9GK6R', '2026-10-03 12:00:00', '2026-10-03 14:36:00'
       UNION ALL SELECT (@cx_a + 183), (@cx_sm + 33), (@cx_u + 83), @cx_c22, '동호회', '확정', 'RCPT-20261003-544M7UUN', '2026-10-03 12:00:00', '2026-10-03 13:03:00'
       UNION ALL SELECT (@cx_a + 184), (@cx_sm + 33), (@cx_u + 84), @cx_c22, '동호회', '확정', 'RCPT-20261003-XXBVX4TP', '2026-10-03 12:00:00', '2026-10-03 13:57:00'
       UNION ALL SELECT (@cx_a + 185), (@cx_sm + 33), (@cx_u + 87), @cx_c22, '동호회', '확정', 'RCPT-20261003-SZ2L2J6K', '2026-10-03 12:00:00', '2026-10-03 14:11:00'
       UNION ALL SELECT (@cx_a + 186), (@cx_sm + 33), (@cx_u + 41), @cx_c12, '동호회', '확정', 'RCPT-20261002-LZAJRDXH', '2026-10-02 11:00:00', '2026-10-02 12:14:00'
       UNION ALL SELECT (@cx_a + 187), (@cx_sm + 33), (@cx_u + 42), @cx_c12, '동호회', '확정', 'RCPT-20261002-63SZ8UHM', '2026-10-02 11:00:00', '2026-10-02 13:07:00'
       UNION ALL SELECT (@cx_a + 188), (@cx_sm + 33), (@cx_u + 43), @cx_c12, '동호회', '확정', 'RCPT-20261002-5MEY3X68', '2026-10-02 11:00:00', '2026-10-02 13:01:00'
       UNION ALL SELECT (@cx_a + 189), (@cx_sm + 33), (@cx_u + 46), @cx_c12, '동호회', '확정', 'RCPT-20261002-E6YSJM3S', '2026-10-02 11:00:00', '2026-10-02 12:37:00'
       UNION ALL SELECT (@cx_a + 190), (@cx_sm + 33), (@cx_u + 45), @cx_c12, '동호회', '확정', 'RCPT-20261002-KZHX769C', '2026-10-02 11:00:00', '2026-10-02 12:01:00'
       UNION ALL SELECT (@cx_a + 191), (@cx_sm + 34), (@cx_u + 52), @cx_c14, '동호회', '확정', 'RCPT-20261003-ER5NFN7A', '2026-10-03 14:00:00', '2026-10-03 16:40:00'
       UNION ALL SELECT (@cx_a + 192), (@cx_sm + 34), (@cx_u + 54), @cx_c14, '동호회', '확정', 'RCPT-20261003-YDB6RVU4', '2026-10-03 14:00:00', '2026-10-03 15:43:00'
       UNION ALL SELECT (@cx_a + 193), (@cx_sm + 34), (@cx_u + 37), @cx_c11, '동호회', '확정', 'RCPT-20261003-PAKFX3CF', '2026-10-03 20:00:00', '2026-10-03 21:12:00'
       UNION ALL SELECT (@cx_a + 194), (@cx_sm + 34), (@cx_u + 39), @cx_c11, '동호회', '확정', 'RCPT-20261003-84R7ZT35', '2026-10-03 20:00:00', '2026-10-03 22:44:00'
       UNION ALL SELECT (@cx_a + 195), (@cx_sm + 35), (@cx_u + 95), @cx_c24, '동호회', '확정', 'RCPT-20261005-MC96YHPR', '2026-10-05 16:00:00', '2026-10-05 18:53:00'
       UNION ALL SELECT (@cx_a + 196), (@cx_sm + 35), (@cx_u + 96), @cx_c24, '동호회', '확정', 'RCPT-20261005-AKQ3VGKE', '2026-10-05 16:00:00', '2026-10-05 18:31:00'
       UNION ALL SELECT (@cx_a + 197), (@cx_sm + 35), (@cx_u + 99), @cx_c24, '동호회', '확정', 'RCPT-20261005-CPKCRS2Z', '2026-10-05 16:00:00', '2026-10-05 17:53:00'
       UNION ALL SELECT (@cx_a + 198), (@cx_sm + 35), (@cx_u + 100), @cx_c24, '동호회', '확정', 'RCPT-20261005-X3P6RG82', '2026-10-05 16:00:00', '2026-10-05 17:13:00'
       UNION ALL SELECT (@cx_a + 199), (@cx_sm + 35), (@cx_u + 101), @cx_c25, '동호회', '확정', 'RCPT-20261004-FKF2UMEF', '2026-10-04 12:00:00', '2026-10-04 13:35:00'
       UNION ALL SELECT (@cx_a + 200), (@cx_sm + 35), (@cx_u + 102), @cx_c25, '동호회', '확정', 'RCPT-20261004-7WBLS6JR', '2026-10-04 12:00:00', '2026-10-04 14:29:00'
       UNION ALL SELECT (@cx_a + 201), (@cx_sm + 35), (@cx_u + 103), @cx_c25, '동호회', '확정', 'RCPT-20261004-RFCDXXJE', '2026-10-04 12:00:00', '2026-10-04 13:39:00'
       UNION ALL SELECT (@cx_a + 202), (@cx_sm + 35), (@cx_u + 105), @cx_c25, '동호회', '확정', 'RCPT-20261004-WB3KA92S', '2026-10-04 12:00:00', '2026-10-04 13:39:00'
       UNION ALL SELECT (@cx_a + 203), (@cx_sm + 36), (@cx_u + 27), @cx_c9, '동호회', '확정', 'RCPT-20261005-7849YTN2', '2026-10-05 12:00:00', '2026-10-05 14:01:00'
       UNION ALL SELECT (@cx_a + 204), (@cx_sm + 36), (@cx_u + 30), @cx_c9, '동호회', '확정', 'RCPT-20261005-L9XMQB93', '2026-10-05 12:00:00', '2026-10-05 14:53:00'
       UNION ALL SELECT (@cx_a + 205), (@cx_sm + 36), (@cx_u + 7), @cx_c4, '동호회', '확정', 'RCPT-20261005-PU7FNJYH', '2026-10-05 20:00:00', '2026-10-05 22:40:00'
       UNION ALL SELECT (@cx_a + 206), (@cx_sm + 36), (@cx_u + 8), @cx_c4, '동호회', '확정', 'RCPT-20261005-UGGWTCEV', '2026-10-05 20:00:00', '2026-10-05 22:30:00'
       UNION ALL SELECT (@cx_a + 207), (@cx_sm + 37), (@cx_u + 1), @cx_c2, '동호회', '확정', 'RCPT-20261008-D45RCKSX', '2026-10-08 09:00:00', '2026-10-08 11:54:00'
       UNION ALL SELECT (@cx_a + 208), (@cx_sm + 37), (@cx_u + 2), @cx_c2, '동호회', '확정', 'RCPT-20261008-KUGMVPDE', '2026-10-08 09:00:00', '2026-10-08 11:15:00'
       UNION ALL SELECT (@cx_a + 209), (@cx_sm + 37), (@cx_u + 4), @cx_c2, '동호회', '확정', 'RCPT-20261008-R6QBGTJR', '2026-10-08 09:00:00', '2026-10-08 10:41:00'
       UNION ALL SELECT (@cx_a + 210), (@cx_sm + 37), (@cx_u + 3), @cx_c2, '동호회', '확정', 'RCPT-20261008-QV2AC5XS', '2026-10-08 09:00:00', '2026-10-08 10:41:00'
       UNION ALL SELECT (@cx_a + 211), (@cx_sm + 37), (@cx_u + 101), @cx_c25, '동호회', '확정', 'RCPT-20261009-NLXFEF28', '2026-10-09 02:00:00', '2026-10-09 04:48:00'
       UNION ALL SELECT (@cx_a + 212), (@cx_sm + 37), (@cx_u + 102), @cx_c25, '동호회', '확정', 'RCPT-20261009-AHDD55EZ', '2026-10-09 02:00:00', '2026-10-09 03:07:00'
       UNION ALL SELECT (@cx_a + 213), (@cx_sm + 37), (@cx_u + 104), @cx_c25, '동호회', '확정', 'RCPT-20261009-736BUNR3', '2026-10-09 02:00:00', '2026-10-09 03:38:00'
       UNION ALL SELECT (@cx_a + 214), (@cx_sm + 37), (@cx_u + 106), @cx_c25, '동호회', '확정', 'RCPT-20261009-MNRWKD7A', '2026-10-09 02:00:00', '2026-10-09 04:51:00'
       UNION ALL SELECT (@cx_a + 215), (@cx_sm + 38), (@cx_u + 107), @cx_c26, '동호회', '확정', 'RCPT-20261008-89JS8AVS', '2026-10-08 06:00:00', '2026-10-08 08:32:00'
       UNION ALL SELECT (@cx_a + 216), (@cx_sm + 38), (@cx_u + 108), @cx_c26, '동호회', '확정', 'RCPT-20261008-FQLVSVHF', '2026-10-08 06:00:00', '2026-10-08 08:44:00'
       UNION ALL SELECT (@cx_a + 217), (@cx_sm + 38), (@cx_u + 109), @cx_c26, '동호회', '확정', 'RCPT-20261008-9MF9ZWR7', '2026-10-08 06:00:00', '2026-10-08 07:23:00'
       UNION ALL SELECT (@cx_a + 218), (@cx_sm + 38), (@cx_u + 62), @cx_c17, '동호회', '확정', 'RCPT-20261007-XW3VWZ36', '2026-10-07 04:00:00', '2026-10-07 05:09:00'
       UNION ALL SELECT (@cx_a + 219), (@cx_sm + 38), (@cx_u + 63), @cx_c17, '동호회', '확정', 'RCPT-20261007-QGBH7EP4', '2026-10-07 04:00:00', '2026-10-07 06:51:00'
       UNION ALL SELECT (@cx_a + 220), (@cx_sm + 38), (@cx_u + 65), @cx_c17, '동호회', '확정', 'RCPT-20261007-RAKW2RSR', '2026-10-07 04:00:00', '2026-10-07 06:19:00'
       UNION ALL SELECT (@cx_a + 221), (@cx_sm + 39), (@cx_u + 70), @cx_c19, '동호회', '확정', 'RCPT-20261008-ELBVGNJB', '2026-10-08 06:00:00', '2026-10-08 07:05:00'
       UNION ALL SELECT (@cx_a + 222), (@cx_sm + 39), (@cx_u + 72), @cx_c19, '동호회', '확정', 'RCPT-20261008-C8PE28BP', '2026-10-08 06:00:00', '2026-10-08 07:21:00'
       UNION ALL SELECT (@cx_a + 223), (@cx_sm + 39), (@cx_u + 111), @cx_c27, '동호회', '확정', 'RCPT-20261008-FRXCG5SC', '2026-10-08 03:00:00', '2026-10-08 04:40:00'
       UNION ALL SELECT (@cx_a + 224), (@cx_sm + 39), (@cx_u + 113), @cx_c27, '동호회', '확정', 'RCPT-20261008-P7FB9EMQ', '2026-10-08 03:00:00', '2026-10-08 04:39:00'
       UNION ALL SELECT (@cx_a + 225), (@cx_sm + 40), (@cx_u + 81), @cx_c22, '동호회', '확정', 'RCPT-20261010-2E22NALT', '2026-10-10 12:00:00', '2026-10-10 13:51:00'
       UNION ALL SELECT (@cx_a + 226), (@cx_sm + 40), (@cx_u + 82), @cx_c22, '동호회', '확정', 'RCPT-20261010-ZNQCZSX2', '2026-10-10 12:00:00', '2026-10-10 14:09:00'
       UNION ALL SELECT (@cx_a + 227), (@cx_sm + 40), (@cx_u + 83), @cx_c22, '동호회', '확정', 'RCPT-20261010-HDWTR5D9', '2026-10-10 12:00:00', '2026-10-10 13:16:00'
       UNION ALL SELECT (@cx_a + 228), (@cx_sm + 40), (@cx_u + 84), @cx_c22, '동호회', '확정', 'RCPT-20261010-QNBAA7FY', '2026-10-10 12:00:00', '2026-10-10 14:47:00'
       UNION ALL SELECT (@cx_a + 229), (@cx_sm + 40), (@cx_u + 87), @cx_c22, '동호회', '확정', 'RCPT-20261010-4GMFJYWS', '2026-10-10 12:00:00', '2026-10-10 13:11:00'
       UNION ALL SELECT (@cx_a + 230), (@cx_sm + 40), (@cx_u + 88), @cx_c23, '동호회', '확정', 'RCPT-20261010-6JUF7MDT', '2026-10-10 19:00:00', '2026-10-10 20:28:00'
       UNION ALL SELECT (@cx_a + 231), (@cx_sm + 40), (@cx_u + 89), @cx_c23, '동호회', '확정', 'RCPT-20261010-3ZDA8D9P', '2026-10-10 19:00:00', '2026-10-10 21:30:00'
       UNION ALL SELECT (@cx_a + 232), (@cx_sm + 40), (@cx_u + 90), @cx_c23, '동호회', '확정', 'RCPT-20261010-HT8364N7', '2026-10-10 19:00:00', '2026-10-10 20:22:00'
       UNION ALL SELECT (@cx_a + 233), (@cx_sm + 40), (@cx_u + 93), @cx_c23, '동호회', '확정', 'RCPT-20261010-C6G8RFKQ', '2026-10-10 19:00:00', '2026-10-10 21:52:00'
       UNION ALL SELECT (@cx_a + 234), (@cx_sm + 40), (@cx_u + 94), @cx_c23, '동호회', '확정', 'RCPT-20261010-88MMLLYR', '2026-10-10 19:00:00', '2026-10-10 21:56:00'
       UNION ALL SELECT (@cx_a + 235), (@cx_sm + 41), (@cx_u + 11), @cx_c5, '동호회', '확정', 'RCPT-20261009-5ERMCVEQ', '2026-10-09 18:00:00', '2026-10-09 20:07:00'
       UNION ALL SELECT (@cx_a + 236), (@cx_sm + 41), (@cx_u + 14), @cx_c5, '동호회', '확정', 'RCPT-20261009-9FFHFEPC', '2026-10-09 18:00:00', '2026-10-09 20:32:00'
       UNION ALL SELECT (@cx_a + 237), (@cx_sm + 41), (@cx_u + 115), @cx_c28, '동호회', '확정', 'RCPT-20261010-Z47M85PJ', '2026-10-10 14:00:00', '2026-10-10 15:47:00'
       UNION ALL SELECT (@cx_a + 238), (@cx_sm + 41), (@cx_u + 116), @cx_c28, '동호회', '확정', 'RCPT-20261010-VMRG5LNA', '2026-10-10 14:00:00', '2026-10-10 16:48:00'
       UNION ALL SELECT (@cx_a + 239), (@cx_sm + 42), (@cx_u + 15), @cx_c6, '동호회', '확정', 'RCPT-20261011-6J828ZPC', '2026-10-11 09:00:00', '2026-10-11 10:25:00'
       UNION ALL SELECT (@cx_a + 240), (@cx_sm + 42), (@cx_u + 16), @cx_c6, '동호회', '확정', 'RCPT-20261011-5KMGBRSS', '2026-10-11 09:00:00', '2026-10-11 11:38:00'
       UNION ALL SELECT (@cx_a + 241), (@cx_sm + 42), (@cx_u + 37), @cx_c11, '동호회', '확정', 'RCPT-20261012-UVGYH34Z', '2026-10-12 11:00:00', '2026-10-12 12:05:00'
       UNION ALL SELECT (@cx_a + 242), (@cx_sm + 42), (@cx_u + 40), @cx_c11, '동호회', '확정', 'RCPT-20261012-3HEKK9A4', '2026-10-12 11:00:00', '2026-10-12 13:05:00'
       UNION ALL SELECT (@cx_a + 243), (@cx_sm + 43), (@cx_u + 41), @cx_c12, '동호회', '확정', 'RCPT-20261011-ZEERJH7G', '2026-10-11 18:00:00', '2026-10-11 20:48:00'
       UNION ALL SELECT (@cx_a + 244), (@cx_sm + 43), (@cx_u + 42), @cx_c12, '동호회', '확정', 'RCPT-20261011-7585QFSC', '2026-10-11 18:00:00', '2026-10-11 20:32:00'
       UNION ALL SELECT (@cx_a + 245), (@cx_sm + 43), (@cx_u + 43), @cx_c12, '동호회', '확정', 'RCPT-20261011-9RBPPEZJ', '2026-10-11 18:00:00', '2026-10-11 20:08:00'
       UNION ALL SELECT (@cx_a + 246), (@cx_sm + 43), (@cx_u + 44), @cx_c12, '동호회', '확정', 'RCPT-20261011-A5PD757B', '2026-10-11 18:00:00', '2026-10-11 20:37:00'
       UNION ALL SELECT (@cx_a + 247), (@cx_sm + 43), (@cx_u + 47), @cx_c12, '동호회', '확정', 'RCPT-20261011-B5ASFKNP', '2026-10-11 18:00:00', '2026-10-11 20:54:00'
       UNION ALL SELECT (@cx_a + 248), (@cx_sm + 43), (@cx_u + 74), @cx_c21, '동호회', '확정', 'RCPT-20261012-ZNPYYR2Z', '2026-10-12 12:00:00', '2026-10-12 14:07:00'
       UNION ALL SELECT (@cx_a + 249), (@cx_sm + 43), (@cx_u + 75), @cx_c21, '동호회', '확정', 'RCPT-20261012-U2LATW33', '2026-10-12 12:00:00', '2026-10-12 14:10:00'
       UNION ALL SELECT (@cx_a + 250), (@cx_sm + 43), (@cx_u + 76), @cx_c21, '동호회', '확정', 'RCPT-20261012-KJ3KLLR9', '2026-10-12 12:00:00', '2026-10-12 14:33:00'
       UNION ALL SELECT (@cx_a + 251), (@cx_sm + 43), (@cx_u + 79), @cx_c21, '동호회', '확정', 'RCPT-20261012-FNE6UQET', '2026-10-12 12:00:00', '2026-10-12 14:07:00'
       UNION ALL SELECT (@cx_a + 252), (@cx_sm + 43), (@cx_u + 77), @cx_c21, '동호회', '확정', 'RCPT-20261012-2LMVY8YZ', '2026-10-12 12:00:00', '2026-10-12 14:30:00'
       UNION ALL SELECT (@cx_a + 253), (@cx_sm + 44), (@cx_u + 52), @cx_c14, '동호회', '확정', 'RCPT-20261011-G2N844XL', '2026-10-11 19:00:00', '2026-10-11 21:53:00'
       UNION ALL SELECT (@cx_a + 254), (@cx_sm + 44), (@cx_u + 55), @cx_c14, '동호회', '확정', 'RCPT-20261011-DVR63DPW', '2026-10-11 19:00:00', '2026-10-11 21:59:00'
       UNION ALL SELECT (@cx_a + 255), (@cx_sm + 44), (@cx_u + 111), @cx_c27, '동호회', '확정', 'RCPT-20261012-8DP3HQHE', '2026-10-12 13:00:00', '2026-10-12 15:38:00'
       UNION ALL SELECT (@cx_a + 256), (@cx_sm + 44), (@cx_u + 112), @cx_c27, '동호회', '확정', 'RCPT-20261012-L26P2GYD', '2026-10-12 13:00:00', '2026-10-12 14:50:00'
       UNION ALL SELECT (@cx_a + 257), (@cx_sm + 45), (@cx_u + 7), @cx_c4, '동호회', '확정', 'RCPT-20261012-PU89W6YQ', '2026-10-12 03:00:00', '2026-10-12 05:24:00'
       UNION ALL SELECT (@cx_a + 258), (@cx_sm + 45), (@cx_u + 9), @cx_c4, '동호회', '확정', 'RCPT-20261012-49XRCDT6', '2026-10-12 03:00:00', '2026-10-12 05:27:00'
       UNION ALL SELECT (@cx_a + 259), (@cx_sm + 45), (@cx_u + 115), @cx_c28, '동호회', '확정', 'RCPT-20261015-P7CZ2XCT', '2026-10-15 01:00:00', '2026-10-15 02:32:00'
       UNION ALL SELECT (@cx_a + 260), (@cx_sm + 45), (@cx_u + 117), @cx_c28, '동호회', '확정', 'RCPT-20261015-G92LETZZ', '2026-10-15 01:00:00', '2026-10-15 03:12:00'
       UNION ALL SELECT (@cx_a + 261), (@cx_sm + 46), (@cx_u + 19), @cx_c7, '동호회', '확정', 'RCPT-20261013-F9VQTCSA', '2026-10-13 19:00:00', '2026-10-13 20:24:00'
       UNION ALL SELECT (@cx_a + 262), (@cx_sm + 46), (@cx_u + 20), @cx_c7, '동호회', '확정', 'RCPT-20261013-P9ZPPR2D', '2026-10-13 19:00:00', '2026-10-13 20:02:00'
       UNION ALL SELECT (@cx_a + 263), (@cx_sm + 46), (@cx_u + 70), @cx_c19, '동호회', '확정', 'RCPT-20261014-LQE2Z9PL', '2026-10-14 13:00:00', '2026-10-14 15:10:00'
       UNION ALL SELECT (@cx_a + 264), (@cx_sm + 46), (@cx_u + 72), @cx_c19, '동호회', '확정', 'RCPT-20261014-77E4ZRWT', '2026-10-14 13:00:00', '2026-10-14 15:01:00'
       UNION ALL SELECT (@cx_a + 265), (@cx_sm + 47), (@cx_u + 62), @cx_c17, '동호회', '확정', 'RCPT-20261014-N4GKVE9V', '2026-10-14 04:00:00', '2026-10-14 06:58:00'
       UNION ALL SELECT (@cx_a + 266), (@cx_sm + 47), (@cx_u + 63), @cx_c17, '동호회', '확정', 'RCPT-20261014-RT5QJDPR', '2026-10-14 04:00:00', '2026-10-14 05:41:00'
       UNION ALL SELECT (@cx_a + 267), (@cx_sm + 47), (@cx_u + 64), @cx_c17, '동호회', '확정', 'RCPT-20261014-AJ6MNNY8', '2026-10-14 04:00:00', '2026-10-14 05:00:00'
       UNION ALL SELECT (@cx_a + 268), (@cx_sm + 47), (@cx_u + 107), @cx_c26, '동호회', '확정', 'RCPT-20261015-WSE9L4P8', '2026-10-15 03:00:00', '2026-10-15 04:13:00'
       UNION ALL SELECT (@cx_a + 269), (@cx_sm + 47), (@cx_u + 108), @cx_c26, '동호회', '확정', 'RCPT-20261015-JLM3JXYB', '2026-10-15 03:00:00', '2026-10-15 04:14:00'
       UNION ALL SELECT (@cx_a + 270), (@cx_sm + 47), (@cx_u + 110), @cx_c26, '동호회', '확정', 'RCPT-20261015-EZNZT3MC', '2026-10-15 03:00:00', '2026-10-15 05:23:00'
       UNION ALL SELECT (@cx_a + 271), (@cx_sm + 48), (@cx_u + 88), @cx_c23, '동호회', '확정', 'RCPT-20261015-2ZJP7VDR', '2026-10-15 09:00:00', '2026-10-15 10:43:00'
       UNION ALL SELECT (@cx_a + 272), (@cx_sm + 48), (@cx_u + 89), @cx_c23, '동호회', '확정', 'RCPT-20261015-R929RX9A', '2026-10-15 09:00:00', '2026-10-15 10:44:00'
       UNION ALL SELECT (@cx_a + 273), (@cx_sm + 48), (@cx_u + 90), @cx_c23, '동호회', '확정', 'RCPT-20261015-SRH4RXVP', '2026-10-15 09:00:00', '2026-10-15 10:46:00'
       UNION ALL SELECT (@cx_a + 274), (@cx_sm + 48), (@cx_u + 92), @cx_c23, '동호회', '확정', 'RCPT-20261015-9KZ6B3DP', '2026-10-15 09:00:00', '2026-10-15 11:24:00'
       UNION ALL SELECT (@cx_a + 275), (@cx_sm + 48), (@cx_u + 91), @cx_c23, '동호회', '확정', 'RCPT-20261015-PBKEFWJ2', '2026-10-15 09:00:00', '2026-10-15 10:51:00'
       UNION ALL SELECT (@cx_a + 276), (@cx_sm + 48), (@cx_u + 41), @cx_c12, '동호회', '확정', 'RCPT-20261016-2LKNG8LV', '2026-10-16 02:00:00', '2026-10-16 03:54:00'
       UNION ALL SELECT (@cx_a + 277), (@cx_sm + 48), (@cx_u + 42), @cx_c12, '동호회', '확정', 'RCPT-20261016-H9U7LSZ7', '2026-10-16 02:00:00', '2026-10-16 04:01:00'
       UNION ALL SELECT (@cx_a + 278), (@cx_sm + 48), (@cx_u + 43), @cx_c12, '동호회', '확정', 'RCPT-20261016-EV8HCT34', '2026-10-16 02:00:00', '2026-10-16 03:29:00'
       UNION ALL SELECT (@cx_a + 279), (@cx_sm + 48), (@cx_u + 45), @cx_c12, '동호회', '확정', 'RCPT-20261016-AVXKJU8D', '2026-10-16 02:00:00', '2026-10-16 04:31:00'
       UNION ALL SELECT (@cx_a + 280), (@cx_sm + 48), (@cx_u + 47), @cx_c12, '동호회', '확정', 'RCPT-20261016-764PBQD8', '2026-10-16 02:00:00', '2026-10-16 04:26:00'
       UNION ALL SELECT (@cx_a + 281), (@cx_sm + 49), (@cx_u + 95), @cx_c24, '동호회', '확정', 'RCPT-20261015-L2VHBPMB', '2026-10-15 14:00:00', '2026-10-15 15:48:00'
       UNION ALL SELECT (@cx_a + 282), (@cx_sm + 49), (@cx_u + 96), @cx_c24, '동호회', '확정', 'RCPT-20261015-8QFYDV8D', '2026-10-15 14:00:00', '2026-10-15 16:14:00'
       UNION ALL SELECT (@cx_a + 283), (@cx_sm + 49), (@cx_u + 98), @cx_c24, '동호회', '확정', 'RCPT-20261015-S7GB8SK4', '2026-10-15 14:00:00', '2026-10-15 16:56:00'
       UNION ALL SELECT (@cx_a + 284), (@cx_sm + 49), (@cx_u + 97), @cx_c24, '동호회', '확정', 'RCPT-20261015-QAWQMBKN', '2026-10-15 14:00:00', '2026-10-15 15:52:00'
       UNION ALL SELECT (@cx_a + 285), (@cx_sm + 49), (@cx_u + 1), @cx_c2, '동호회', '확정', 'RCPT-20261013-GZE8QU4U', '2026-10-13 09:00:00', '2026-10-13 10:12:00'
       UNION ALL SELECT (@cx_a + 286), (@cx_sm + 49), (@cx_u + 2), @cx_c2, '동호회', '확정', 'RCPT-20261013-V93LURPP', '2026-10-13 09:00:00', '2026-10-13 10:48:00'
       UNION ALL SELECT (@cx_a + 287), (@cx_sm + 49), (@cx_u + 4), @cx_c2, '동호회', '확정', 'RCPT-20261013-XDHCDBT5', '2026-10-13 09:00:00', '2026-10-13 10:05:00'
       UNION ALL SELECT (@cx_a + 288), (@cx_sm + 49), (@cx_u + 5), @cx_c2, '동호회', '확정', 'RCPT-20261013-S43CLN2K', '2026-10-13 09:00:00', '2026-10-13 10:00:00'
       UNION ALL SELECT (@cx_a + 289), (@cx_sm + 50), (@cx_u + 48), @cx_c13, '동호회', '확정', 'RCPT-20261014-9KJSRMDD', '2026-10-14 04:00:00', '2026-10-14 05:45:00'
       UNION ALL SELECT (@cx_a + 290), (@cx_sm + 50), (@cx_u + 50), @cx_c13, '동호회', '확정', 'RCPT-20261014-WN98R5TH', '2026-10-14 04:00:00', '2026-10-14 06:15:00'
       UNION ALL SELECT (@cx_a + 291), (@cx_sm + 50), (@cx_u + 23), @cx_c8, '동호회', '확정', 'RCPT-20261016-89H9H8YF', '2026-10-16 02:00:00', '2026-10-16 04:35:00'
       UNION ALL SELECT (@cx_a + 292), (@cx_sm + 50), (@cx_u + 26), @cx_c8, '동호회', '확정', 'RCPT-20261016-9DFXSNUV', '2026-10-16 02:00:00', '2026-10-16 03:59:00'
       UNION ALL SELECT (@cx_a + 293), (@cx_sm + 51), (@cx_u + 37), @cx_c11, '동호회', '확정', 'RCPT-20261015-G9CUXDKP', '2026-10-15 22:00:00', '2026-10-16 00:25:00'
       UNION ALL SELECT (@cx_a + 294), (@cx_sm + 51), (@cx_u + 39), @cx_c11, '동호회', '확정', 'RCPT-20261015-GQ33FWQW', '2026-10-15 22:00:00', '2026-10-16 00:36:00'
       UNION ALL SELECT (@cx_a + 295), (@cx_sm + 51), (@cx_u + 111), @cx_c27, '동호회', '확정', 'RCPT-20261016-XUP6VM97', '2026-10-16 23:00:00', '2026-10-17 00:24:00'
       UNION ALL SELECT (@cx_a + 296), (@cx_sm + 51), (@cx_u + 114), @cx_c27, '동호회', '확정', 'RCPT-20261016-TBQ66EDB', '2026-10-16 23:00:00', '2026-10-17 01:58:00'
       UNION ALL SELECT (@cx_a + 297), (@cx_sm + 52), (@cx_u + 11), @cx_c5, '동호회', '확정', 'RCPT-20261017-2MCBTQY6', '2026-10-17 15:00:00', '2026-10-17 17:49:00'
       UNION ALL SELECT (@cx_a + 298), (@cx_sm + 52), (@cx_u + 13), @cx_c5, '동호회', '확정', 'RCPT-20261017-LF9U9DNW', '2026-10-17 15:00:00', '2026-10-17 17:32:00'
       UNION ALL SELECT (@cx_a + 299), (@cx_sm + 52), (@cx_u + 115), @cx_c28, '동호회', '확정', 'RCPT-20261017-GDJY636T', '2026-10-17 11:00:00', '2026-10-17 12:55:00'
       UNION ALL SELECT (@cx_a + 300), (@cx_sm + 52), (@cx_u + 118), @cx_c28, '동호회', '확정', 'RCPT-20261017-88S32L9Y', '2026-10-17 11:00:00', '2026-10-17 13:18:00'
       UNION ALL SELECT (@cx_a + 301), (@cx_sm + 53), (@cx_u + 95), @cx_c24, '동호회', '확정', 'RCPT-20261019-WMGLTW7X', '2026-10-19 01:00:00', '2026-10-19 03:43:00'
       UNION ALL SELECT (@cx_a + 302), (@cx_sm + 53), (@cx_u + 96), @cx_c24, '동호회', '확정', 'RCPT-20261019-TH3K7AB4', '2026-10-19 01:00:00', '2026-10-19 03:19:00'
       UNION ALL SELECT (@cx_a + 303), (@cx_sm + 53), (@cx_u + 100), @cx_c24, '동호회', '확정', 'RCPT-20261019-FE5GXLD3', '2026-10-19 01:00:00', '2026-10-19 03:34:00'
       UNION ALL SELECT (@cx_a + 304), (@cx_sm + 53), (@cx_u + 99), @cx_c24, '동호회', '확정', 'RCPT-20261019-XVG7DAT8', '2026-10-19 01:00:00', '2026-10-19 03:54:00'
       UNION ALL SELECT (@cx_a + 305), (@cx_sm + 53), (@cx_u + 101), @cx_c25, '동호회', '확정', 'RCPT-20261018-26X9NMG2', '2026-10-18 08:00:00', '2026-10-18 09:34:00'
       UNION ALL SELECT (@cx_a + 306), (@cx_sm + 53), (@cx_u + 102), @cx_c25, '동호회', '확정', 'RCPT-20261018-K8WKSX7F', '2026-10-18 08:00:00', '2026-10-18 10:51:00'
       UNION ALL SELECT (@cx_a + 307), (@cx_sm + 53), (@cx_u + 104), @cx_c25, '동호회', '확정', 'RCPT-20261018-N9C7G549', '2026-10-18 08:00:00', '2026-10-18 09:18:00'
       UNION ALL SELECT (@cx_a + 308), (@cx_sm + 53), (@cx_u + 103), @cx_c25, '동호회', '확정', 'RCPT-20261018-LFKUELRB', '2026-10-18 08:00:00', '2026-10-18 10:31:00'
       UNION ALL SELECT (@cx_a + 309), (@cx_sm + 54), (@cx_u + 41), @cx_c12, '동호회', '확정', 'RCPT-20261019-WM6NRKMF', '2026-10-19 12:00:00', '2026-10-19 14:10:00'
       UNION ALL SELECT (@cx_a + 310), (@cx_sm + 54), (@cx_u + 42), @cx_c12, '동호회', '확정', 'RCPT-20261019-Q4CB2ZMR', '2026-10-19 12:00:00', '2026-10-19 14:24:00'
       UNION ALL SELECT (@cx_a + 311), (@cx_sm + 54), (@cx_u + 43), @cx_c12, '동호회', '확정', 'RCPT-20261019-TKQWQXL6', '2026-10-19 12:00:00', '2026-10-19 13:10:00'
       UNION ALL SELECT (@cx_a + 312), (@cx_sm + 54), (@cx_u + 44), @cx_c12, '동호회', '확정', 'RCPT-20261019-QHMMUPHW', '2026-10-19 12:00:00', '2026-10-19 14:02:00'
       UNION ALL SELECT (@cx_a + 313), (@cx_sm + 54), (@cx_u + 46), @cx_c12, '동호회', '확정', 'RCPT-20261019-DYF3DLSA', '2026-10-19 12:00:00', '2026-10-19 14:18:00'
       UNION ALL SELECT (@cx_a + 314), (@cx_sm + 54), (@cx_u + 81), @cx_c22, '동호회', '확정', 'RCPT-20261018-TC57CY98', '2026-10-18 18:00:00', '2026-10-18 20:29:00'
       UNION ALL SELECT (@cx_a + 315), (@cx_sm + 54), (@cx_u + 82), @cx_c22, '동호회', '확정', 'RCPT-20261018-ASWQKPF6', '2026-10-18 18:00:00', '2026-10-18 19:15:00'
       UNION ALL SELECT (@cx_a + 316), (@cx_sm + 54), (@cx_u + 83), @cx_c22, '동호회', '확정', 'RCPT-20261018-8P6E6BRL', '2026-10-18 18:00:00', '2026-10-18 20:38:00'
       UNION ALL SELECT (@cx_a + 317), (@cx_sm + 54), (@cx_u + 86), @cx_c22, '동호회', '확정', 'RCPT-20261018-78WPUYM8', '2026-10-18 18:00:00', '2026-10-18 19:36:00'
       UNION ALL SELECT (@cx_a + 318), (@cx_sm + 54), (@cx_u + 85), @cx_c22, '동호회', '확정', 'RCPT-20261018-7YCJ4TBZ', '2026-10-18 18:00:00', '2026-10-18 20:36:00'
       UNION ALL SELECT (@cx_a + 319), (@cx_sm + 55), (@cx_u + 111), @cx_c27, '동호회', '확정', 'RCPT-20261019-F4JDRRN7', '2026-10-19 15:00:00', '2026-10-19 17:59:00'
       UNION ALL SELECT (@cx_a + 320), (@cx_sm + 55), (@cx_u + 113), @cx_c27, '동호회', '확정', 'RCPT-20261019-BH2A8VX2', '2026-10-19 15:00:00', '2026-10-19 16:47:00'
       UNION ALL SELECT (@cx_a + 321), (@cx_sm + 55), (@cx_u + 15), @cx_c6, '동호회', '확정', 'RCPT-20261021-2URJ2E5Z', '2026-10-21 13:00:00', '2026-10-21 15:51:00'
       UNION ALL SELECT (@cx_a + 322), (@cx_sm + 55), (@cx_u + 17), @cx_c6, '동호회', '확정', 'RCPT-20261021-QTYPWFBT', '2026-10-21 13:00:00', '2026-10-21 14:21:00'
       UNION ALL SELECT (@cx_a + 323), (@cx_sm + 56), (@cx_u + 70), @cx_c19, '동호회', '확정', 'RCPT-20261021-W9BCEP6G', '2026-10-21 08:00:00', '2026-10-21 09:45:00'
       UNION ALL SELECT (@cx_a + 324), (@cx_sm + 56), (@cx_u + 71), @cx_c19, '동호회', '확정', 'RCPT-20261021-APM3SL59', '2026-10-21 08:00:00', '2026-10-21 09:41:00'
       UNION ALL SELECT (@cx_a + 325), (@cx_sm + 56), (@cx_u + 37), @cx_c11, '동호회', '확정', 'RCPT-20261020-2H99N4CU', '2026-10-20 04:00:00', '2026-10-20 06:10:00'
       UNION ALL SELECT (@cx_a + 326), (@cx_sm + 56), (@cx_u + 38), @cx_c11, '동호회', '확정', 'RCPT-20261020-6UD46R7Z', '2026-10-20 04:00:00', '2026-10-20 06:15:00'
       UNION ALL SELECT (@cx_a + 327), (@cx_sm + 57), (@cx_u + 74), @cx_c21, '동호회', '확정', 'RCPT-20261020-VA9ZEDYG', '2026-10-20 08:00:00', '2026-10-20 09:48:00'
       UNION ALL SELECT (@cx_a + 328), (@cx_sm + 57), (@cx_u + 75), @cx_c21, '동호회', '확정', 'RCPT-20261020-QBU5BWCP', '2026-10-20 08:00:00', '2026-10-20 10:31:00'
       UNION ALL SELECT (@cx_a + 329), (@cx_sm + 57), (@cx_u + 76), @cx_c21, '동호회', '확정', 'RCPT-20261020-VUVSDFA4', '2026-10-20 08:00:00', '2026-10-20 10:46:00'
       UNION ALL SELECT (@cx_a + 330), (@cx_sm + 57), (@cx_u + 77), @cx_c21, '동호회', '확정', 'RCPT-20261020-NKTDJWVA', '2026-10-20 08:00:00', '2026-10-20 09:06:00'
       UNION ALL SELECT (@cx_a + 331), (@cx_sm + 57), (@cx_u + 80), @cx_c21, '동호회', '확정', 'RCPT-20261020-CDRGD7R3', '2026-10-20 08:00:00', '2026-10-20 10:17:00'
       UNION ALL SELECT (@cx_a + 332), (@cx_sm + 57), (@cx_u + 81), @cx_c22, '동호회', '확정', 'RCPT-20261023-R7JF9J4L', '2026-10-23 03:00:00', '2026-10-23 04:10:00'
       UNION ALL SELECT (@cx_a + 333), (@cx_sm + 57), (@cx_u + 82), @cx_c22, '동호회', '확정', 'RCPT-20261023-CEX7J6VM', '2026-10-23 03:00:00', '2026-10-23 05:02:00'
       UNION ALL SELECT (@cx_a + 334), (@cx_sm + 57), (@cx_u + 83), @cx_c22, '동호회', '확정', 'RCPT-20261023-V9QK923X', '2026-10-23 03:00:00', '2026-10-23 04:53:00'
       UNION ALL SELECT (@cx_a + 335), (@cx_sm + 57), (@cx_u + 86), @cx_c22, '동호회', '확정', 'RCPT-20261023-ZTH6L447', '2026-10-23 03:00:00', '2026-10-23 05:26:00'
       UNION ALL SELECT (@cx_a + 336), (@cx_sm + 57), (@cx_u + 87), @cx_c22, '동호회', '확정', 'RCPT-20261023-WPLV7ZHT', '2026-10-23 03:00:00', '2026-10-23 04:53:00'
       UNION ALL SELECT (@cx_a + 337), (@cx_sm + 58), (@cx_u + 107), @cx_c26, '동호회', '확정', 'RCPT-20261020-DG6EUV3V', '2026-10-20 08:00:00', '2026-10-20 10:10:00'
       UNION ALL SELECT (@cx_a + 338), (@cx_sm + 58), (@cx_u + 108), @cx_c26, '동호회', '확정', 'RCPT-20261020-8DLK53QE', '2026-10-20 08:00:00', '2026-10-20 10:15:00'
       UNION ALL SELECT (@cx_a + 339), (@cx_sm + 58), (@cx_u + 109), @cx_c26, '동호회', '확정', 'RCPT-20261020-HHD85P2N', '2026-10-20 08:00:00', '2026-10-20 09:29:00'
       UNION ALL SELECT (@cx_a + 340), (@cx_sm + 58), (@cx_u + 62), @cx_c17, '동호회', '확정', 'RCPT-20261021-5M5HFSX6', '2026-10-21 08:00:00', '2026-10-21 10:48:00'
       UNION ALL SELECT (@cx_a + 341), (@cx_sm + 58), (@cx_u + 63), @cx_c17, '동호회', '확정', 'RCPT-20261021-MM89PRRG', '2026-10-21 08:00:00', '2026-10-21 09:11:00'
       UNION ALL SELECT (@cx_a + 342), (@cx_sm + 58), (@cx_u + 65), @cx_c17, '동호회', '확정', 'RCPT-20261021-DWPUEC8L', '2026-10-21 08:00:00', '2026-10-21 09:33:00'
       UNION ALL SELECT (@cx_a + 343), (@cx_sm + 59), (@cx_u + 115), @cx_c28, '동호회', '확정', 'RCPT-20261022-MCZ7GPHV', '2026-10-22 07:00:00', '2026-10-22 08:56:00'
       UNION ALL SELECT (@cx_a + 344), (@cx_sm + 59), (@cx_u + 117), @cx_c28, '동호회', '확정', 'RCPT-20261022-PZRYAXTJ', '2026-10-22 07:00:00', '2026-10-22 09:36:00'
       UNION ALL SELECT (@cx_a + 345), (@cx_sm + 59), (@cx_u + 27), @cx_c9, '동호회', '확정', 'RCPT-20261021-U68G29QJ', '2026-10-21 03:00:00', '2026-10-21 05:25:00'
       UNION ALL SELECT (@cx_a + 346), (@cx_sm + 59), (@cx_u + 29), @cx_c9, '동호회', '확정', 'RCPT-20261021-NLSGNJGW', '2026-10-21 03:00:00', '2026-10-21 04:36:00'
       UNION ALL SELECT (@cx_a + 347), (@cx_sm + 60), (@cx_u + 95), @cx_c24, '동호회', '확정', 'RCPT-20261023-LM5FGCKL', '2026-10-23 01:00:00', '2026-10-23 03:51:00'
       UNION ALL SELECT (@cx_a + 348), (@cx_sm + 60), (@cx_u + 96), @cx_c24, '동호회', '확정', 'RCPT-20261023-DECYSM6D', '2026-10-23 01:00:00', '2026-10-23 02:15:00'
       UNION ALL SELECT (@cx_a + 349), (@cx_sm + 60), (@cx_u + 100), @cx_c24, '동호회', '확정', 'RCPT-20261023-9WV8559C', '2026-10-23 01:00:00', '2026-10-23 02:17:00'
       UNION ALL SELECT (@cx_a + 350), (@cx_sm + 60), (@cx_u + 98), @cx_c24, '동호회', '확정', 'RCPT-20261023-FWGV8RAN', '2026-10-23 01:00:00', '2026-10-23 02:35:00'
       UNION ALL SELECT (@cx_a + 351), (@cx_sm + 60), (@cx_u + 101), @cx_c25, '동호회', '확정', 'RCPT-20261023-BFN95DMD', '2026-10-23 02:00:00', '2026-10-23 04:53:00'
       UNION ALL SELECT (@cx_a + 352), (@cx_sm + 60), (@cx_u + 102), @cx_c25, '동호회', '확정', 'RCPT-20261023-3YUM76NZ', '2026-10-23 02:00:00', '2026-10-23 04:12:00'
       UNION ALL SELECT (@cx_a + 353), (@cx_sm + 60), (@cx_u + 105), @cx_c25, '동호회', '확정', 'RCPT-20261023-TVSDAE6Z', '2026-10-23 02:00:00', '2026-10-23 03:13:00'
       UNION ALL SELECT (@cx_a + 354), (@cx_sm + 60), (@cx_u + 106), @cx_c25, '동호회', '확정', 'RCPT-20261023-CGXXJJ63', '2026-10-23 02:00:00', '2026-10-23 03:32:00'
       UNION ALL SELECT (@cx_a + 355), (@cx_sm + 61), (@cx_u + 1), @cx_c2, '동호회', '확정', 'RCPT-20261021-5CDD6PCT', '2026-10-21 01:00:00', '2026-10-21 03:36:00'
       UNION ALL SELECT (@cx_a + 356), (@cx_sm + 61), (@cx_u + 2), @cx_c2, '동호회', '확정', 'RCPT-20261021-CNXGQA9R', '2026-10-21 01:00:00', '2026-10-21 02:51:00'
       UNION ALL SELECT (@cx_a + 357), (@cx_sm + 61), (@cx_u + 3), @cx_c2, '동호회', '확정', 'RCPT-20261021-Y9MT3BUK', '2026-10-21 01:00:00', '2026-10-21 03:08:00'
       UNION ALL SELECT (@cx_a + 358), (@cx_sm + 61), (@cx_u + 6), @cx_c2, '동호회', '확정', 'RCPT-20261021-NKGE435L', '2026-10-21 01:00:00', '2026-10-21 02:17:00'
       UNION ALL SELECT (@cx_a + 359), (@cx_sm + 61), (@cx_u + 56), @cx_c15, '동호회', '확정', 'RCPT-20261021-73V2FJWC', '2026-10-21 00:00:00', '2026-10-21 02:21:00'
       UNION ALL SELECT (@cx_a + 360), (@cx_sm + 61), (@cx_u + 57), @cx_c15, '동호회', '확정', 'RCPT-20261021-XNZ9VM3W', '2026-10-21 00:00:00', '2026-10-21 02:34:00'
       UNION ALL SELECT (@cx_a + 361), (@cx_sm + 61), (@cx_u + 61), @cx_c15, '동호회', '확정', 'RCPT-20261021-FQNF7H5Y', '2026-10-21 00:00:00', '2026-10-21 02:57:00'
       UNION ALL SELECT (@cx_a + 362), (@cx_sm + 61), (@cx_u + 59), @cx_c15, '동호회', '확정', 'RCPT-20261021-GHVEAR52', '2026-10-21 00:00:00', '2026-10-21 01:45:00'
       UNION ALL SELECT (@cx_a + 363), (@cx_sm + 62), (@cx_u + 111), @cx_c27, '동호회', '확정', 'RCPT-20261024-JPU3DR9X', '2026-10-24 23:00:00', '2026-10-25 01:07:00'
       UNION ALL SELECT (@cx_a + 364), (@cx_sm + 62), (@cx_u + 112), @cx_c27, '동호회', '확정', 'RCPT-20261024-4T9ZPZ4S', '2026-10-24 23:00:00', '2026-10-25 00:26:00'
       UNION ALL SELECT (@cx_a + 365), (@cx_sm + 62), (@cx_u + 15), @cx_c6, '동호회', '확정', 'RCPT-20261025-QF6ZUK24', '2026-10-25 09:00:00', '2026-10-25 10:34:00'
       UNION ALL SELECT (@cx_a + 366), (@cx_sm + 62), (@cx_u + 18), @cx_c6, '동호회', '확정', 'RCPT-20261025-ZWSN5V5T', '2026-10-25 09:00:00', '2026-10-25 11:41:00'
       UNION ALL SELECT (@cx_a + 367), (@cx_sm + 63), (@cx_u + 66), @cx_c18, '동호회', '확정', 'RCPT-20261024-98JKQKV7', '2026-10-24 19:00:00', '2026-10-24 20:30:00'
       UNION ALL SELECT (@cx_a + 368), (@cx_sm + 63), (@cx_u + 68), @cx_c18, '동호회', '확정', 'RCPT-20261024-3Q7ASY3N', '2026-10-24 19:00:00', '2026-10-24 20:52:00'
       UNION ALL SELECT (@cx_a + 369), (@cx_sm + 63), (@cx_u + 115), @cx_c28, '동호회', '확정', 'RCPT-20261025-6WCLYWVR', '2026-10-25 13:00:00', '2026-10-25 15:10:00'
       UNION ALL SELECT (@cx_a + 370), (@cx_sm + 63), (@cx_u + 116), @cx_c28, '동호회', '확정', 'RCPT-20261025-7JU3PB5G', '2026-10-25 13:00:00', '2026-10-25 14:03:00'
       UNION ALL SELECT (@cx_a + 371), (@cx_sm + 64), (@cx_u + 81), @cx_c22, '동호회', '확정', 'RCPT-20261027-JHBJDZ6E', '2026-10-27 10:00:00', '2026-10-27 12:09:00'
       UNION ALL SELECT (@cx_a + 372), (@cx_sm + 64), (@cx_u + 82), @cx_c22, '동호회', '확정', 'RCPT-20261027-WWQQQXA7', '2026-10-27 10:00:00', '2026-10-27 12:51:00'
       UNION ALL SELECT (@cx_a + 373), (@cx_sm + 64), (@cx_u + 83), @cx_c22, '동호회', '확정', 'RCPT-20261027-UJX7CNH3', '2026-10-27 10:00:00', '2026-10-27 12:33:00'
       UNION ALL SELECT (@cx_a + 374), (@cx_sm + 64), (@cx_u + 84), @cx_c22, '동호회', '확정', 'RCPT-20261027-KF52FHWH', '2026-10-27 10:00:00', '2026-10-27 12:12:00'
       UNION ALL SELECT (@cx_a + 375), (@cx_sm + 64), (@cx_u + 85), @cx_c22, '동호회', '확정', 'RCPT-20261027-UPJWTUQ8', '2026-10-27 10:00:00', '2026-10-27 11:40:00'
       UNION ALL SELECT (@cx_a + 376), (@cx_sm + 64), (@cx_u + 88), @cx_c23, '동호회', '확정', 'RCPT-20261026-ZB5TTGVV', '2026-10-26 19:00:00', '2026-10-26 20:59:00'
       UNION ALL SELECT (@cx_a + 377), (@cx_sm + 64), (@cx_u + 89), @cx_c23, '동호회', '확정', 'RCPT-20261026-F6VE4KSD', '2026-10-26 19:00:00', '2026-10-26 21:03:00'
       UNION ALL SELECT (@cx_a + 378), (@cx_sm + 64), (@cx_u + 90), @cx_c23, '동호회', '확정', 'RCPT-20261026-CJZQN9SZ', '2026-10-26 19:00:00', '2026-10-26 21:37:00'
       UNION ALL SELECT (@cx_a + 379), (@cx_sm + 64), (@cx_u + 92), @cx_c23, '동호회', '확정', 'RCPT-20261026-3UCPYLSP', '2026-10-26 19:00:00', '2026-10-26 21:39:00'
       UNION ALL SELECT (@cx_a + 380), (@cx_sm + 64), (@cx_u + 93), @cx_c23, '동호회', '확정', 'RCPT-20261026-5R2XH244', '2026-10-26 19:00:00', '2026-10-26 20:40:00'
       UNION ALL SELECT (@cx_a + 381), (@cx_sm + 65), (@cx_u + 62), @cx_c17, '동호회', '확정', 'RCPT-20261027-87C75RAW', '2026-10-27 08:00:00', '2026-10-27 09:19:00'
       UNION ALL SELECT (@cx_a + 382), (@cx_sm + 65), (@cx_u + 63), @cx_c17, '동호회', '확정', 'RCPT-20261027-3NJBLCTS', '2026-10-27 08:00:00', '2026-10-27 09:01:00'
       UNION ALL SELECT (@cx_a + 383), (@cx_sm + 65), (@cx_u + 64), @cx_c17, '동호회', '확정', 'RCPT-20261027-GU5U6A2A', '2026-10-27 08:00:00', '2026-10-27 10:55:00'
       UNION ALL SELECT (@cx_a + 384), (@cx_sm + 65), (@cx_u + 107), @cx_c26, '동호회', '확정', 'RCPT-20261029-JUCXGPUQ', '2026-10-29 05:00:00', '2026-10-29 07:07:00'
       UNION ALL SELECT (@cx_a + 385), (@cx_sm + 65), (@cx_u + 108), @cx_c26, '동호회', '확정', 'RCPT-20261029-HNHRMUJT', '2026-10-29 05:00:00', '2026-10-29 06:13:00'
       UNION ALL SELECT (@cx_a + 386), (@cx_sm + 65), (@cx_u + 110), @cx_c26, '동호회', '확정', 'RCPT-20261029-CYBADL78', '2026-10-29 05:00:00', '2026-10-29 07:58:00'
       UNION ALL SELECT (@cx_a + 387), (@cx_sm + 66), (@cx_u + 95), @cx_c24, '동호회', '확정', 'RCPT-20261027-V3QZKHNU', '2026-10-27 09:00:00', '2026-10-27 11:37:00'
       UNION ALL SELECT (@cx_a + 388), (@cx_sm + 66), (@cx_u + 96), @cx_c24, '동호회', '확정', 'RCPT-20261027-EXNTKNX2', '2026-10-27 09:00:00', '2026-10-27 10:03:00'
       UNION ALL SELECT (@cx_a + 389), (@cx_sm + 66), (@cx_u + 97), @cx_c24, '동호회', '확정', 'RCPT-20261027-D9KWUVZY', '2026-10-27 09:00:00', '2026-10-27 10:12:00'
       UNION ALL SELECT (@cx_a + 390), (@cx_sm + 66), (@cx_u + 99), @cx_c24, '동호회', '확정', 'RCPT-20261027-DWWTP9QJ', '2026-10-27 09:00:00', '2026-10-27 10:07:00'
       UNION ALL SELECT (@cx_a + 391), (@cx_sm + 66), (@cx_u + 101), @cx_c25, '동호회', '확정', 'RCPT-20261029-7AC466X2', '2026-10-29 17:00:00', '2026-10-29 18:55:00'
       UNION ALL SELECT (@cx_a + 392), (@cx_sm + 66), (@cx_u + 102), @cx_c25, '동호회', '확정', 'RCPT-20261029-GQHTR6ZL', '2026-10-29 17:00:00', '2026-10-29 19:04:00'
       UNION ALL SELECT (@cx_a + 393), (@cx_sm + 66), (@cx_u + 106), @cx_c25, '동호회', '확정', 'RCPT-20261029-544GCAD9', '2026-10-29 17:00:00', '2026-10-29 18:54:00'
       UNION ALL SELECT (@cx_a + 394), (@cx_sm + 66), (@cx_u + 103), @cx_c25, '동호회', '확정', 'RCPT-20261029-B8FVGUT2', '2026-10-29 17:00:00', '2026-10-29 19:39:00'
       UNION ALL SELECT (@cx_a + 395), (@cx_sm + 67), (@cx_u + 23), @cx_c8, '동호회', '확정', 'RCPT-20261102-X48UXVFT', '2026-11-02 19:30:00', '2026-11-02 20:35:00'
       UNION ALL SELECT (@cx_a + 396), (@cx_sm + 67), (@cx_u + 25), @cx_c8, '동호회', '확정', 'RCPT-20261102-MXFDFR44', '2026-11-02 19:30:00', '2026-11-02 21:14:00'
       UNION ALL SELECT (@cx_a + 397), (@cx_sm + 67), (@cx_u + 115), @cx_c28, '동호회', '확정', 'RCPT-20261103-CE9LHFQM', '2026-11-03 14:30:00', '2026-11-03 16:45:00'
       UNION ALL SELECT (@cx_a + 398), (@cx_sm + 67), (@cx_u + 118), @cx_c28, '동호회', '확정', 'RCPT-20261103-DYUSAF4W', '2026-11-03 14:30:00', '2026-11-03 16:49:00'
       UNION ALL SELECT (@cx_a + 399), (@cx_sm + 68), (@cx_u + 111), @cx_c27, '동호회', '확정', 'RCPT-20261102-HLT4QZH2', '2026-11-02 18:00:00', '2026-11-02 19:37:00'
       UNION ALL SELECT (@cx_a + 400), (@cx_sm + 68), (@cx_u + 114), @cx_c27, '동호회', '확정', 'RCPT-20261102-7DB95C84', '2026-11-02 18:00:00', '2026-11-02 19:03:00'
       UNION ALL SELECT (@cx_a + 401), (@cx_sm + 68), (@cx_u + 70), @cx_c19, '동호회', '확정', 'RCPT-20261103-SX3F65VB', '2026-11-03 10:00:00', '2026-11-03 11:13:00'
       UNION ALL SELECT (@cx_a + 402), (@cx_sm + 68), (@cx_u + 73), @cx_c19, '동호회', '확정', 'RCPT-20261103-HVUT4N5V', '2026-11-03 10:00:00', '2026-11-03 12:02:00'
       UNION ALL SELECT (@cx_a + 403), (@cx_sm + 69), (@cx_u + 31), @cx_c10, '동호회', '확정', 'RCPT-20261104-8B9SJU8Q', '2026-11-04 05:00:00', '2026-11-04 06:03:00'
       UNION ALL SELECT (@cx_a + 404), (@cx_sm + 69), (@cx_u + 32), @cx_c10, '동호회', '확정', 'RCPT-20261104-UF2AJ78M', '2026-11-04 05:00:00', '2026-11-04 07:45:00'
       UNION ALL SELECT (@cx_a + 405), (@cx_sm + 69), (@cx_u + 36), @cx_c10, '동호회', '확정', 'RCPT-20261104-VX3DWJDM', '2026-11-04 05:00:00', '2026-11-04 07:48:00'
       UNION ALL SELECT (@cx_a + 406), (@cx_sm + 69), (@cx_u + 34), @cx_c10, '동호회', '확정', 'RCPT-20261104-R82699U4', '2026-11-04 05:00:00', '2026-11-04 07:47:00'
       UNION ALL SELECT (@cx_a + 407), (@cx_sm + 69), (@cx_u + 101), @cx_c25, '동호회', '확정', 'RCPT-20261105-ZLCQXDDH', '2026-11-05 01:00:00', '2026-11-05 02:15:00'
       UNION ALL SELECT (@cx_a + 408), (@cx_sm + 69), (@cx_u + 102), @cx_c25, '동호회', '확정', 'RCPT-20261105-FD7E6NJP', '2026-11-05 01:00:00', '2026-11-05 02:14:00'
       UNION ALL SELECT (@cx_a + 409), (@cx_sm + 69), (@cx_u + 105), @cx_c25, '동호회', '확정', 'RCPT-20261105-GGQ45FR6', '2026-11-05 01:00:00', '2026-11-05 02:08:00'
       UNION ALL SELECT (@cx_a + 410), (@cx_sm + 69), (@cx_u + 104), @cx_c25, '동호회', '확정', 'RCPT-20261105-UH55RVJH', '2026-11-05 01:00:00', '2026-11-05 02:11:00'
       UNION ALL SELECT (@cx_a + 411), (@cx_sm + 70), (@cx_u + 107), @cx_c26, '동호회', '확정', 'RCPT-20261105-UBKF72ZY', '2026-11-05 07:00:00', '2026-11-05 08:51:00'
       UNION ALL SELECT (@cx_a + 412), (@cx_sm + 70), (@cx_u + 108), @cx_c26, '동호회', '확정', 'RCPT-20261105-JXQ8GT3Z', '2026-11-05 07:00:00', '2026-11-05 09:52:00'
       UNION ALL SELECT (@cx_a + 413), (@cx_sm + 70), (@cx_u + 110), @cx_c26, '동호회', '확정', 'RCPT-20261105-M47YHS9Z', '2026-11-05 07:00:00', '2026-11-05 08:02:00'
       UNION ALL SELECT (@cx_a + 414), (@cx_sm + 70), (@cx_u + 62), @cx_c17, '동호회', '확정', 'RCPT-20261103-96RU7B8L', '2026-11-03 09:00:00', '2026-11-03 11:02:00'
       UNION ALL SELECT (@cx_a + 415), (@cx_sm + 70), (@cx_u + 63), @cx_c17, '동호회', '확정', 'RCPT-20261103-S3WPM5G9', '2026-11-03 09:00:00', '2026-11-03 11:47:00'
       UNION ALL SELECT (@cx_a + 416), (@cx_sm + 70), (@cx_u + 65), @cx_c17, '동호회', '확정', 'RCPT-20261103-XNQRYH6N', '2026-11-03 09:00:00', '2026-11-03 11:33:00'
       UNION ALL SELECT (@cx_a + 417), (@cx_sm + 71), (@cx_u + 88), @cx_c23, '동호회', '확정', 'RCPT-20261105-W8JHN6JF', '2026-11-05 04:00:00', '2026-11-05 05:58:00'
       UNION ALL SELECT (@cx_a + 418), (@cx_sm + 71), (@cx_u + 89), @cx_c23, '동호회', '확정', 'RCPT-20261105-X7QDCPMD', '2026-11-05 04:00:00', '2026-11-05 05:31:00'
       UNION ALL SELECT (@cx_a + 419), (@cx_sm + 71), (@cx_u + 90), @cx_c23, '동호회', '확정', 'RCPT-20261105-3SJ9GCQZ', '2026-11-05 04:00:00', '2026-11-05 06:59:00'
       UNION ALL SELECT (@cx_a + 420), (@cx_sm + 71), (@cx_u + 94), @cx_c23, '동호회', '확정', 'RCPT-20261105-NJKC3M4L', '2026-11-05 04:00:00', '2026-11-05 05:32:00'
       UNION ALL SELECT (@cx_a + 421), (@cx_sm + 71), (@cx_u + 91), @cx_c23, '동호회', '확정', 'RCPT-20261105-4L9ZRZCH', '2026-11-05 04:00:00', '2026-11-05 05:33:00'
       UNION ALL SELECT (@cx_a + 422), (@cx_sm + 71), (@cx_u + 74), @cx_c21, '동호회', '확정', 'RCPT-20261105-3TAZF8RU', '2026-11-05 14:00:00', '2026-11-05 15:36:00'
       UNION ALL SELECT (@cx_a + 423), (@cx_sm + 71), (@cx_u + 75), @cx_c21, '동호회', '확정', 'RCPT-20261105-CUTTNGAN', '2026-11-05 14:00:00', '2026-11-05 15:01:00'
       UNION ALL SELECT (@cx_a + 424), (@cx_sm + 71), (@cx_u + 76), @cx_c21, '동호회', '확정', 'RCPT-20261105-JXL89C6N', '2026-11-05 14:00:00', '2026-11-05 15:44:00'
       UNION ALL SELECT (@cx_a + 425), (@cx_sm + 71), (@cx_u + 78), @cx_c21, '동호회', '확정', 'RCPT-20261105-648Z7BHD', '2026-11-05 14:00:00', '2026-11-05 16:34:00'
       UNION ALL SELECT (@cx_a + 426), (@cx_sm + 71), (@cx_u + 79), @cx_c21, '동호회', '확정', 'RCPT-20261105-NLGDTEVV', '2026-11-05 14:00:00', '2026-11-05 16:04:00'
       UNION ALL SELECT (@cx_a + 427), (@cx_sm + 72), (@cx_u + 1), @cx_c2, '동호회', '대기', 'RCPT-20261112-SNXM525S', '2026-11-12 21:30:00', NULL
       UNION ALL SELECT (@cx_a + 428), (@cx_sm + 72), (@cx_u + 2), @cx_c2, '동호회', '대기', 'RCPT-20261112-8BRQ6RTT', '2026-11-12 21:30:00', NULL
       UNION ALL SELECT (@cx_a + 429), (@cx_sm + 72), (@cx_u + 3), @cx_c2, '동호회', '대기', 'RCPT-20261112-VZBXLHXC', '2026-11-12 21:30:00', NULL
       UNION ALL SELECT (@cx_a + 430), (@cx_sm + 72), (@cx_u + 6), @cx_c2, '동호회', '대기', 'RCPT-20261112-7HL8ZJQU', '2026-11-12 21:30:00', NULL
       UNION ALL SELECT (@cx_a + 431), (@cx_sm + 73), (@cx_u + 7), @cx_c4, '동호회', '대기', 'RCPT-20261112-48Y2V5HM', '2026-11-12 21:37:00', NULL
       UNION ALL SELECT (@cx_a + 432), (@cx_sm + 73), (@cx_u + 9), @cx_c4, '동호회', '대기', 'RCPT-20261112-QEC2JNWG', '2026-11-12 21:37:00', NULL
       UNION ALL SELECT (@cx_a + 433), (@cx_sm + 74), (@cx_u + 11), @cx_c5, '동호회', '대기', 'RCPT-20261112-MKFAWJUK', '2026-11-12 21:44:00', NULL
       UNION ALL SELECT (@cx_a + 434), (@cx_sm + 74), (@cx_u + 13), @cx_c5, '동호회', '대기', 'RCPT-20261112-3VL668KG', '2026-11-12 21:44:00', NULL
       UNION ALL SELECT (@cx_a + 435), (@cx_sm + 75), (@cx_u + 15), @cx_c6, '동호회', '대기', 'RCPT-20261112-S9XHNPLE', '2026-11-12 21:51:00', NULL
       UNION ALL SELECT (@cx_a + 436), (@cx_sm + 75), (@cx_u + 18), @cx_c6, '동호회', '대기', 'RCPT-20261112-U2HEFXTX', '2026-11-12 21:51:00', NULL
       UNION ALL SELECT (@cx_a + 437), (@cx_sm + 76), (@cx_u + 19), @cx_c7, '동호회', '대기', 'RCPT-20261112-U2MPK3L6', '2026-11-12 21:58:00', NULL
       UNION ALL SELECT (@cx_a + 438), (@cx_sm + 76), (@cx_u + 22), @cx_c7, '동호회', '대기', 'RCPT-20261112-XDR4QBH2', '2026-11-12 21:58:00', NULL
       UNION ALL SELECT (@cx_a + 439), (@cx_sm + 77), (@cx_u + 23), @cx_c8, '동호회', '대기', 'RCPT-20261112-GNX36CVF', '2026-11-12 22:05:00', NULL
       UNION ALL SELECT (@cx_a + 440), (@cx_sm + 77), (@cx_u + 26), @cx_c8, '동호회', '대기', 'RCPT-20261112-UXRWTABZ', '2026-11-12 22:05:00', NULL
       UNION ALL SELECT (@cx_a + 441), (@cx_sm + 78), (@cx_u + 27), @cx_c9, '동호회', '대기', 'RCPT-20261112-8WTJJD2P', '2026-11-12 22:12:00', NULL
       UNION ALL SELECT (@cx_a + 442), (@cx_sm + 78), (@cx_u + 28), @cx_c9, '동호회', '대기', 'RCPT-20261112-LV4V8DKG', '2026-11-12 22:12:00', NULL
       UNION ALL SELECT (@cx_a + 443), (@cx_sm + 79), (@cx_u + 31), @cx_c10, '동호회', '대기', 'RCPT-20261112-HVJ7SEV9', '2026-11-12 22:19:00', NULL
       UNION ALL SELECT (@cx_a + 444), (@cx_sm + 79), (@cx_u + 32), @cx_c10, '동호회', '대기', 'RCPT-20261112-A3BK5TGM', '2026-11-12 22:19:00', NULL
       UNION ALL SELECT (@cx_a + 445), (@cx_sm + 79), (@cx_u + 33), @cx_c10, '동호회', '대기', 'RCPT-20261112-U9C88AQ7', '2026-11-12 22:19:00', NULL
       UNION ALL SELECT (@cx_a + 446), (@cx_sm + 79), (@cx_u + 34), @cx_c10, '동호회', '대기', 'RCPT-20261112-Y4FSXJXZ', '2026-11-12 22:19:00', NULL
       UNION ALL SELECT (@cx_a + 447), (@cx_sm + 80), (@cx_u + 37), @cx_c11, '동호회', '대기', 'RCPT-20261112-GQTRDC94', '2026-11-12 22:26:00', NULL
       UNION ALL SELECT (@cx_a + 448), (@cx_sm + 80), (@cx_u + 39), @cx_c11, '동호회', '대기', 'RCPT-20261112-8CGN2EAB', '2026-11-12 22:26:00', NULL
       UNION ALL SELECT (@cx_a + 449), (@cx_sm + 81), (@cx_u + 41), @cx_c12, '동호회', '대기', 'RCPT-20261112-CZATMTA3', '2026-11-12 22:33:00', NULL
       UNION ALL SELECT (@cx_a + 450), (@cx_sm + 81), (@cx_u + 42), @cx_c12, '동호회', '대기', 'RCPT-20261112-GSRNH8ZH', '2026-11-12 22:33:00', NULL
       UNION ALL SELECT (@cx_a + 451), (@cx_sm + 81), (@cx_u + 43), @cx_c12, '동호회', '대기', 'RCPT-20261112-3JEKVU2W', '2026-11-12 22:33:00', NULL
       UNION ALL SELECT (@cx_a + 452), (@cx_sm + 81), (@cx_u + 46), @cx_c12, '동호회', '대기', 'RCPT-20261112-4D2X7XXY', '2026-11-12 22:33:00', NULL
       UNION ALL SELECT (@cx_a + 453), (@cx_sm + 81), (@cx_u + 47), @cx_c12, '동호회', '대기', 'RCPT-20261112-UDUDEXPQ', '2026-11-12 22:33:00', NULL
       UNION ALL SELECT (@cx_a + 454), (@cx_sm + 82), (@cx_u + 48), @cx_c13, '동호회', '대기', 'RCPT-20261112-26D6V3UN', '2026-11-12 22:40:00', NULL
       UNION ALL SELECT (@cx_a + 455), (@cx_sm + 82), (@cx_u + 49), @cx_c13, '동호회', '대기', 'RCPT-20261112-D6T3HUQ2', '2026-11-12 22:40:00', NULL
       UNION ALL SELECT (@cx_a + 456), (@cx_sm + 83), (@cx_u + 52), @cx_c14, '동호회', '대기', 'RCPT-20261112-NG7MJZBS', '2026-11-12 22:47:00', NULL
       UNION ALL SELECT (@cx_a + 457), (@cx_sm + 83), (@cx_u + 54), @cx_c14, '동호회', '대기', 'RCPT-20261112-JX3HZG4W', '2026-11-12 22:47:00', NULL
       UNION ALL SELECT (@cx_a + 458), (@cx_sm + 84), (@cx_u + 56), @cx_c15, '동호회', '대기', 'RCPT-20261112-R7WY9WEA', '2026-11-12 22:54:00', NULL
       UNION ALL SELECT (@cx_a + 459), (@cx_sm + 84), (@cx_u + 57), @cx_c15, '동호회', '대기', 'RCPT-20261112-PSPX6GC9', '2026-11-12 22:54:00', NULL
       UNION ALL SELECT (@cx_a + 460), (@cx_sm + 84), (@cx_u + 61), @cx_c15, '동호회', '대기', 'RCPT-20261112-26F7HWXT', '2026-11-12 22:54:00', NULL
       UNION ALL SELECT (@cx_a + 461), (@cx_sm + 84), (@cx_u + 58), @cx_c15, '동호회', '대기', 'RCPT-20261112-J4REXXRD', '2026-11-12 22:54:00', NULL
       UNION ALL SELECT (@cx_a + 462), (@cx_sm + 85), (@cx_u + 62), @cx_c17, '동호회', '대기', 'RCPT-20261112-CAVRY2FG', '2026-11-12 23:01:00', NULL
       UNION ALL SELECT (@cx_a + 463), (@cx_sm + 85), (@cx_u + 63), @cx_c17, '동호회', '대기', 'RCPT-20261112-JZADVN35', '2026-11-12 23:01:00', NULL
       UNION ALL SELECT (@cx_a + 464), (@cx_sm + 85), (@cx_u + 65), @cx_c17, '동호회', '대기', 'RCPT-20261112-JHLVFXPZ', '2026-11-12 23:01:00', NULL
       UNION ALL SELECT (@cx_a + 465), (@cx_sm + 86), (@cx_u + 66), @cx_c18, '동호회', '대기', 'RCPT-20261112-S9WC5L5W', '2026-11-12 23:08:00', NULL
       UNION ALL SELECT (@cx_a + 466), (@cx_sm + 86), (@cx_u + 67), @cx_c18, '동호회', '대기', 'RCPT-20261112-GEVNWWD7', '2026-11-12 23:08:00', NULL
       UNION ALL SELECT (@cx_a + 467), (@cx_sm + 87), (@cx_u + 70), @cx_c19, '동호회', '대기', 'RCPT-20261112-F67QE43D', '2026-11-12 23:15:00', NULL
       UNION ALL SELECT (@cx_a + 468), (@cx_sm + 87), (@cx_u + 71), @cx_c19, '동호회', '대기', 'RCPT-20261112-QD9PFLGL', '2026-11-12 23:15:00', NULL
       UNION ALL SELECT (@cx_a + 469), (@cx_sm + 88), (@cx_u + 107), @cx_c26, '동호회', '대기', 'RCPT-20261112-R2XNUBW5', '2026-11-12 23:22:00', NULL
       UNION ALL SELECT (@cx_a + 470), (@cx_sm + 88), (@cx_u + 108), @cx_c26, '동호회', '대기', 'RCPT-20261112-TF75SRAF', '2026-11-12 23:22:00', NULL
       UNION ALL SELECT (@cx_a + 471), (@cx_sm + 88), (@cx_u + 109), @cx_c26, '동호회', '대기', 'RCPT-20261112-GXHA95GR', '2026-11-12 23:22:00', NULL
       UNION ALL SELECT (@cx_a + 472), (@cx_sm + 89), (@cx_u + 111), @cx_c27, '동호회', '대기', 'RCPT-20261112-NVTMN9KS', '2026-11-12 23:29:00', NULL
       UNION ALL SELECT (@cx_a + 473), (@cx_sm + 89), (@cx_u + 114), @cx_c27, '동호회', '대기', 'RCPT-20261112-E96EPRWN', '2026-11-12 23:29:00', NULL
       UNION ALL SELECT (@cx_a + 474), (@cx_sm + 90), (@cx_u + 115), @cx_c28, '동호회', '대기', 'RCPT-20261112-3C3C92GE', '2026-11-12 23:36:00', NULL
       UNION ALL SELECT (@cx_a + 475), (@cx_sm + 90), (@cx_u + 117), @cx_c28, '동호회', '대기', 'RCPT-20261112-HXWPHL6N', '2026-11-12 23:36:00', NULL
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
         WHEN application_id BETWEEN (@cx_a + 21) AND (@cx_a + 22) THEN (@cx_a + 21)
         WHEN application_id BETWEEN (@cx_a + 23) AND (@cx_a + 24) THEN (@cx_a + 23)
         WHEN application_id BETWEEN (@cx_a + 25) AND (@cx_a + 28) THEN (@cx_a + 25)
         WHEN application_id BETWEEN (@cx_a + 29) AND (@cx_a + 32) THEN (@cx_a + 29)
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
         WHEN application_id BETWEEN (@cx_a + 53) AND (@cx_a + 56) THEN (@cx_a + 53)
         WHEN application_id BETWEEN (@cx_a + 57) AND (@cx_a + 60) THEN (@cx_a + 57)
         WHEN application_id BETWEEN (@cx_a + 61) AND (@cx_a + 62) THEN (@cx_a + 61)
         WHEN application_id BETWEEN (@cx_a + 63) AND (@cx_a + 64) THEN (@cx_a + 63)
         WHEN application_id BETWEEN (@cx_a + 65) AND (@cx_a + 66) THEN (@cx_a + 65)
         WHEN application_id BETWEEN (@cx_a + 67) AND (@cx_a + 68) THEN (@cx_a + 67)
         WHEN application_id BETWEEN (@cx_a + 69) AND (@cx_a + 70) THEN (@cx_a + 69)
         WHEN application_id BETWEEN (@cx_a + 71) AND (@cx_a + 72) THEN (@cx_a + 71)
         WHEN application_id BETWEEN (@cx_a + 73) AND (@cx_a + 76) THEN (@cx_a + 73)
         WHEN application_id BETWEEN (@cx_a + 77) AND (@cx_a + 80) THEN (@cx_a + 77)
         WHEN application_id BETWEEN (@cx_a + 81) AND (@cx_a + 82) THEN (@cx_a + 81)
         WHEN application_id BETWEEN (@cx_a + 83) AND (@cx_a + 84) THEN (@cx_a + 83)
         WHEN application_id BETWEEN (@cx_a + 85) AND (@cx_a + 86) THEN (@cx_a + 85)
         WHEN application_id BETWEEN (@cx_a + 87) AND (@cx_a + 88) THEN (@cx_a + 87)
         WHEN application_id BETWEEN (@cx_a + 89) AND (@cx_a + 90) THEN (@cx_a + 89)
         WHEN application_id BETWEEN (@cx_a + 91) AND (@cx_a + 92) THEN (@cx_a + 91)
         WHEN application_id BETWEEN (@cx_a + 93) AND (@cx_a + 94) THEN (@cx_a + 93)
         WHEN application_id BETWEEN (@cx_a + 95) AND (@cx_a + 96) THEN (@cx_a + 95)
         WHEN application_id BETWEEN (@cx_a + 97) AND (@cx_a + 98) THEN (@cx_a + 97)
         WHEN application_id BETWEEN (@cx_a + 99) AND (@cx_a + 100) THEN (@cx_a + 99)
         WHEN application_id BETWEEN (@cx_a + 101) AND (@cx_a + 102) THEN (@cx_a + 101)
         WHEN application_id BETWEEN (@cx_a + 103) AND (@cx_a + 104) THEN (@cx_a + 103)
         WHEN application_id BETWEEN (@cx_a + 105) AND (@cx_a + 108) THEN (@cx_a + 105)
         WHEN application_id BETWEEN (@cx_a + 109) AND (@cx_a + 112) THEN (@cx_a + 109)
         WHEN application_id BETWEEN (@cx_a + 113) AND (@cx_a + 114) THEN (@cx_a + 113)
         WHEN application_id BETWEEN (@cx_a + 115) AND (@cx_a + 116) THEN (@cx_a + 115)
         WHEN application_id BETWEEN (@cx_a + 117) AND (@cx_a + 121) THEN (@cx_a + 117)
         WHEN application_id BETWEEN (@cx_a + 122) AND (@cx_a + 126) THEN (@cx_a + 122)
         WHEN application_id BETWEEN (@cx_a + 127) AND (@cx_a + 131) THEN (@cx_a + 127)
         WHEN application_id BETWEEN (@cx_a + 132) AND (@cx_a + 136) THEN (@cx_a + 132)
         WHEN application_id BETWEEN (@cx_a + 137) AND (@cx_a + 138) THEN (@cx_a + 137)
         WHEN application_id BETWEEN (@cx_a + 139) AND (@cx_a + 140) THEN (@cx_a + 139)
         WHEN application_id BETWEEN (@cx_a + 141) AND (@cx_a + 145) THEN (@cx_a + 141)
         WHEN application_id BETWEEN (@cx_a + 146) AND (@cx_a + 150) THEN (@cx_a + 146)
         WHEN application_id BETWEEN (@cx_a + 151) AND (@cx_a + 154) THEN (@cx_a + 151)
         WHEN application_id BETWEEN (@cx_a + 155) AND (@cx_a + 158) THEN (@cx_a + 155)
         WHEN application_id BETWEEN (@cx_a + 159) AND (@cx_a + 162) THEN (@cx_a + 159)
         WHEN application_id BETWEEN (@cx_a + 163) AND (@cx_a + 166) THEN (@cx_a + 163)
         WHEN application_id BETWEEN (@cx_a + 167) AND (@cx_a + 171) THEN (@cx_a + 167)
         WHEN application_id BETWEEN (@cx_a + 172) AND (@cx_a + 176) THEN (@cx_a + 172)
         WHEN application_id BETWEEN (@cx_a + 177) AND (@cx_a + 178) THEN (@cx_a + 177)
         WHEN application_id BETWEEN (@cx_a + 179) AND (@cx_a + 180) THEN (@cx_a + 179)
         WHEN application_id BETWEEN (@cx_a + 181) AND (@cx_a + 185) THEN (@cx_a + 181)
         WHEN application_id BETWEEN (@cx_a + 186) AND (@cx_a + 190) THEN (@cx_a + 186)
         WHEN application_id BETWEEN (@cx_a + 191) AND (@cx_a + 192) THEN (@cx_a + 191)
         WHEN application_id BETWEEN (@cx_a + 193) AND (@cx_a + 194) THEN (@cx_a + 193)
         WHEN application_id BETWEEN (@cx_a + 195) AND (@cx_a + 198) THEN (@cx_a + 195)
         WHEN application_id BETWEEN (@cx_a + 199) AND (@cx_a + 202) THEN (@cx_a + 199)
         WHEN application_id BETWEEN (@cx_a + 203) AND (@cx_a + 204) THEN (@cx_a + 203)
         WHEN application_id BETWEEN (@cx_a + 205) AND (@cx_a + 206) THEN (@cx_a + 205)
         WHEN application_id BETWEEN (@cx_a + 207) AND (@cx_a + 210) THEN (@cx_a + 207)
         WHEN application_id BETWEEN (@cx_a + 211) AND (@cx_a + 214) THEN (@cx_a + 211)
         WHEN application_id BETWEEN (@cx_a + 215) AND (@cx_a + 217) THEN (@cx_a + 215)
         WHEN application_id BETWEEN (@cx_a + 218) AND (@cx_a + 220) THEN (@cx_a + 218)
         WHEN application_id BETWEEN (@cx_a + 221) AND (@cx_a + 222) THEN (@cx_a + 221)
         WHEN application_id BETWEEN (@cx_a + 223) AND (@cx_a + 224) THEN (@cx_a + 223)
         WHEN application_id BETWEEN (@cx_a + 225) AND (@cx_a + 229) THEN (@cx_a + 225)
         WHEN application_id BETWEEN (@cx_a + 230) AND (@cx_a + 234) THEN (@cx_a + 230)
         WHEN application_id BETWEEN (@cx_a + 235) AND (@cx_a + 236) THEN (@cx_a + 235)
         WHEN application_id BETWEEN (@cx_a + 237) AND (@cx_a + 238) THEN (@cx_a + 237)
         WHEN application_id BETWEEN (@cx_a + 239) AND (@cx_a + 240) THEN (@cx_a + 239)
         WHEN application_id BETWEEN (@cx_a + 241) AND (@cx_a + 242) THEN (@cx_a + 241)
         WHEN application_id BETWEEN (@cx_a + 243) AND (@cx_a + 247) THEN (@cx_a + 243)
         WHEN application_id BETWEEN (@cx_a + 248) AND (@cx_a + 252) THEN (@cx_a + 248)
         WHEN application_id BETWEEN (@cx_a + 253) AND (@cx_a + 254) THEN (@cx_a + 253)
         WHEN application_id BETWEEN (@cx_a + 255) AND (@cx_a + 256) THEN (@cx_a + 255)
         WHEN application_id BETWEEN (@cx_a + 257) AND (@cx_a + 258) THEN (@cx_a + 257)
         WHEN application_id BETWEEN (@cx_a + 259) AND (@cx_a + 260) THEN (@cx_a + 259)
         WHEN application_id BETWEEN (@cx_a + 261) AND (@cx_a + 262) THEN (@cx_a + 261)
         WHEN application_id BETWEEN (@cx_a + 263) AND (@cx_a + 264) THEN (@cx_a + 263)
         WHEN application_id BETWEEN (@cx_a + 265) AND (@cx_a + 267) THEN (@cx_a + 265)
         WHEN application_id BETWEEN (@cx_a + 268) AND (@cx_a + 270) THEN (@cx_a + 268)
         WHEN application_id BETWEEN (@cx_a + 271) AND (@cx_a + 275) THEN (@cx_a + 271)
         WHEN application_id BETWEEN (@cx_a + 276) AND (@cx_a + 280) THEN (@cx_a + 276)
         WHEN application_id BETWEEN (@cx_a + 281) AND (@cx_a + 284) THEN (@cx_a + 281)
         WHEN application_id BETWEEN (@cx_a + 285) AND (@cx_a + 288) THEN (@cx_a + 285)
         WHEN application_id BETWEEN (@cx_a + 289) AND (@cx_a + 290) THEN (@cx_a + 289)
         WHEN application_id BETWEEN (@cx_a + 291) AND (@cx_a + 292) THEN (@cx_a + 291)
         WHEN application_id BETWEEN (@cx_a + 293) AND (@cx_a + 294) THEN (@cx_a + 293)
         WHEN application_id BETWEEN (@cx_a + 295) AND (@cx_a + 296) THEN (@cx_a + 295)
         WHEN application_id BETWEEN (@cx_a + 297) AND (@cx_a + 298) THEN (@cx_a + 297)
         WHEN application_id BETWEEN (@cx_a + 299) AND (@cx_a + 300) THEN (@cx_a + 299)
         WHEN application_id BETWEEN (@cx_a + 301) AND (@cx_a + 304) THEN (@cx_a + 301)
         WHEN application_id BETWEEN (@cx_a + 305) AND (@cx_a + 308) THEN (@cx_a + 305)
         WHEN application_id BETWEEN (@cx_a + 309) AND (@cx_a + 313) THEN (@cx_a + 309)
         WHEN application_id BETWEEN (@cx_a + 314) AND (@cx_a + 318) THEN (@cx_a + 314)
         WHEN application_id BETWEEN (@cx_a + 319) AND (@cx_a + 320) THEN (@cx_a + 319)
         WHEN application_id BETWEEN (@cx_a + 321) AND (@cx_a + 322) THEN (@cx_a + 321)
         WHEN application_id BETWEEN (@cx_a + 323) AND (@cx_a + 324) THEN (@cx_a + 323)
         WHEN application_id BETWEEN (@cx_a + 325) AND (@cx_a + 326) THEN (@cx_a + 325)
         WHEN application_id BETWEEN (@cx_a + 327) AND (@cx_a + 331) THEN (@cx_a + 327)
         WHEN application_id BETWEEN (@cx_a + 332) AND (@cx_a + 336) THEN (@cx_a + 332)
         WHEN application_id BETWEEN (@cx_a + 337) AND (@cx_a + 339) THEN (@cx_a + 337)
         WHEN application_id BETWEEN (@cx_a + 340) AND (@cx_a + 342) THEN (@cx_a + 340)
         WHEN application_id BETWEEN (@cx_a + 343) AND (@cx_a + 344) THEN (@cx_a + 343)
         WHEN application_id BETWEEN (@cx_a + 345) AND (@cx_a + 346) THEN (@cx_a + 345)
         WHEN application_id BETWEEN (@cx_a + 347) AND (@cx_a + 350) THEN (@cx_a + 347)
         WHEN application_id BETWEEN (@cx_a + 351) AND (@cx_a + 354) THEN (@cx_a + 351)
         WHEN application_id BETWEEN (@cx_a + 355) AND (@cx_a + 358) THEN (@cx_a + 355)
         WHEN application_id BETWEEN (@cx_a + 359) AND (@cx_a + 362) THEN (@cx_a + 359)
         WHEN application_id BETWEEN (@cx_a + 363) AND (@cx_a + 364) THEN (@cx_a + 363)
         WHEN application_id BETWEEN (@cx_a + 365) AND (@cx_a + 366) THEN (@cx_a + 365)
         WHEN application_id BETWEEN (@cx_a + 367) AND (@cx_a + 368) THEN (@cx_a + 367)
         WHEN application_id BETWEEN (@cx_a + 369) AND (@cx_a + 370) THEN (@cx_a + 369)
         WHEN application_id BETWEEN (@cx_a + 371) AND (@cx_a + 375) THEN (@cx_a + 371)
         WHEN application_id BETWEEN (@cx_a + 376) AND (@cx_a + 380) THEN (@cx_a + 376)
         WHEN application_id BETWEEN (@cx_a + 381) AND (@cx_a + 383) THEN (@cx_a + 381)
         WHEN application_id BETWEEN (@cx_a + 384) AND (@cx_a + 386) THEN (@cx_a + 384)
         WHEN application_id BETWEEN (@cx_a + 387) AND (@cx_a + 390) THEN (@cx_a + 387)
         WHEN application_id BETWEEN (@cx_a + 391) AND (@cx_a + 394) THEN (@cx_a + 391)
         WHEN application_id BETWEEN (@cx_a + 395) AND (@cx_a + 396) THEN (@cx_a + 395)
         WHEN application_id BETWEEN (@cx_a + 397) AND (@cx_a + 398) THEN (@cx_a + 397)
         WHEN application_id BETWEEN (@cx_a + 399) AND (@cx_a + 400) THEN (@cx_a + 399)
         WHEN application_id BETWEEN (@cx_a + 401) AND (@cx_a + 402) THEN (@cx_a + 401)
         WHEN application_id BETWEEN (@cx_a + 403) AND (@cx_a + 406) THEN (@cx_a + 403)
         WHEN application_id BETWEEN (@cx_a + 407) AND (@cx_a + 410) THEN (@cx_a + 407)
         WHEN application_id BETWEEN (@cx_a + 411) AND (@cx_a + 413) THEN (@cx_a + 411)
         WHEN application_id BETWEEN (@cx_a + 414) AND (@cx_a + 416) THEN (@cx_a + 414)
         WHEN application_id BETWEEN (@cx_a + 417) AND (@cx_a + 421) THEN (@cx_a + 417)
         WHEN application_id BETWEEN (@cx_a + 422) AND (@cx_a + 426) THEN (@cx_a + 422)
         WHEN application_id BETWEEN (@cx_a + 427) AND (@cx_a + 430) THEN (@cx_a + 427)
         WHEN application_id BETWEEN (@cx_a + 431) AND (@cx_a + 432) THEN (@cx_a + 431)
         WHEN application_id BETWEEN (@cx_a + 433) AND (@cx_a + 434) THEN (@cx_a + 433)
         WHEN application_id BETWEEN (@cx_a + 435) AND (@cx_a + 436) THEN (@cx_a + 435)
         WHEN application_id BETWEEN (@cx_a + 437) AND (@cx_a + 438) THEN (@cx_a + 437)
         WHEN application_id BETWEEN (@cx_a + 439) AND (@cx_a + 440) THEN (@cx_a + 439)
         WHEN application_id BETWEEN (@cx_a + 441) AND (@cx_a + 442) THEN (@cx_a + 441)
         WHEN application_id BETWEEN (@cx_a + 443) AND (@cx_a + 446) THEN (@cx_a + 443)
         WHEN application_id BETWEEN (@cx_a + 447) AND (@cx_a + 448) THEN (@cx_a + 447)
         WHEN application_id BETWEEN (@cx_a + 449) AND (@cx_a + 453) THEN (@cx_a + 449)
         WHEN application_id BETWEEN (@cx_a + 454) AND (@cx_a + 455) THEN (@cx_a + 454)
         WHEN application_id BETWEEN (@cx_a + 456) AND (@cx_a + 457) THEN (@cx_a + 456)
         WHEN application_id BETWEEN (@cx_a + 458) AND (@cx_a + 461) THEN (@cx_a + 458)
         WHEN application_id BETWEEN (@cx_a + 462) AND (@cx_a + 464) THEN (@cx_a + 462)
         WHEN application_id BETWEEN (@cx_a + 465) AND (@cx_a + 466) THEN (@cx_a + 465)
         WHEN application_id BETWEEN (@cx_a + 467) AND (@cx_a + 468) THEN (@cx_a + 467)
         WHEN application_id BETWEEN (@cx_a + 469) AND (@cx_a + 471) THEN (@cx_a + 469)
         WHEN application_id BETWEEN (@cx_a + 472) AND (@cx_a + 473) THEN (@cx_a + 472)
         WHEN application_id BETWEEN (@cx_a + 474) AND (@cx_a + 475) THEN (@cx_a + 474)
       END
 WHERE @cx_ready = 1 AND application_id BETWEEN (@cx_a + 1) AND (@cx_a + 475);

-- 5. 참가자 (확정 순서대로 번호, entry_mmr은 확정 시점의 레이팅 = 직전 경기 반영 뒤 값)
INSERT INTO match_participant (participant_id, application_id, match_id, user_id, participation_status, match_side, entry_mmr, joined_at)
SELECT * FROM (
                 SELECT (@cx_p + 1) AS participant_id, (@cx_a + 4) AS application_id, (@cx_sm + 1) AS match_id, (@cx_u + 33) AS user_id, '확정' AS participation_status, 'A' AS match_side, 1000.00 AS entry_mmr, '2026-07-30 06:06:00' AS joined_at
       UNION ALL SELECT (@cx_p + 2), (@cx_a + 1), (@cx_sm + 1), (@cx_u + 31), '확정', 'A', 1000.00, '2026-07-30 06:15:00'
       UNION ALL SELECT (@cx_p + 3), (@cx_a + 2), (@cx_sm + 1), (@cx_u + 32), '확정', 'A', 1000.00, '2026-07-30 06:17:00'
       UNION ALL SELECT (@cx_p + 4), (@cx_a + 3), (@cx_sm + 1), (@cx_u + 34), '확정', 'A', 1000.00, '2026-07-30 06:20:00'
       UNION ALL SELECT (@cx_p + 5), (@cx_a + 6), (@cx_sm + 1), (@cx_u + 57), '확정', 'B', 1000.00, '2026-07-30 08:17:00'
       UNION ALL SELECT (@cx_p + 6), (@cx_a + 7), (@cx_sm + 1), (@cx_u + 60), '확정', 'B', 1000.00, '2026-07-30 08:34:00'
       UNION ALL SELECT (@cx_p + 7), (@cx_a + 5), (@cx_sm + 1), (@cx_u + 56), '확정', 'B', 1000.00, '2026-07-30 08:52:00'
       UNION ALL SELECT (@cx_p + 8), (@cx_a + 8), (@cx_sm + 1), (@cx_u + 61), '확정', 'B', 1000.00, '2026-07-30 09:04:00'
       UNION ALL SELECT (@cx_p + 9), (@cx_a + 9), (@cx_sm + 2), (@cx_u + 48), '확정', 'A', 1000.00, '2026-07-29 01:18:00'
       UNION ALL SELECT (@cx_p + 10), (@cx_a + 10), (@cx_sm + 2), (@cx_u + 51), '확정', 'A', 1000.00, '2026-07-29 01:58:00'
       UNION ALL SELECT (@cx_p + 11), (@cx_a + 11), (@cx_sm + 2), (@cx_u + 66), '확정', 'B', 1000.00, '2026-07-31 02:14:00'
       UNION ALL SELECT (@cx_p + 12), (@cx_a + 12), (@cx_sm + 2), (@cx_u + 68), '확정', 'B', 1000.00, '2026-07-31 02:38:00'
       UNION ALL SELECT (@cx_p + 13), (@cx_a + 14), (@cx_sm + 3), (@cx_u + 73), '확정', 'A', 1000.00, '2026-07-29 10:26:00'
       UNION ALL SELECT (@cx_p + 14), (@cx_a + 13), (@cx_sm + 3), (@cx_u + 70), '확정', 'A', 1000.00, '2026-07-29 10:44:00'
       UNION ALL SELECT (@cx_p + 15), (@cx_a + 15), (@cx_sm + 3), (@cx_u + 19), '확정', 'B', 1000.00, '2026-07-31 03:16:00'
       UNION ALL SELECT (@cx_p + 16), (@cx_a + 16), (@cx_sm + 3), (@cx_u + 22), '확정', 'B', 1000.00, '2026-07-31 04:44:00'
       UNION ALL SELECT (@cx_p + 17), (@cx_a + 19), (@cx_sm + 4), (@cx_u + 15), '확정', 'B', 1000.00, '2026-08-01 21:11:00'
       UNION ALL SELECT (@cx_p + 18), (@cx_a + 20), (@cx_sm + 4), (@cx_u + 18), '확정', 'B', 1000.00, '2026-08-01 22:20:00'
       UNION ALL SELECT (@cx_p + 19), (@cx_a + 17), (@cx_sm + 4), (@cx_u + 52), '확정', 'A', 1000.00, '2026-08-02 12:19:00'
       UNION ALL SELECT (@cx_p + 20), (@cx_a + 18), (@cx_sm + 4), (@cx_u + 55), '확정', 'A', 1000.00, '2026-08-02 12:20:00'
       UNION ALL SELECT (@cx_p + 21), (@cx_a + 21), (@cx_sm + 5), (@cx_u + 7), '확정', 'A', 1000.00, '2026-08-04 04:04:00'
       UNION ALL SELECT (@cx_p + 22), (@cx_a + 22), (@cx_sm + 5), (@cx_u + 8), '확정', 'A', 1000.00, '2026-08-04 05:15:00'
       UNION ALL SELECT (@cx_p + 23), (@cx_a + 23), (@cx_sm + 5), (@cx_u + 23), '확정', 'B', 1000.00, '2026-08-05 08:28:00'
       UNION ALL SELECT (@cx_p + 24), (@cx_a + 24), (@cx_sm + 5), (@cx_u + 24), '확정', 'B', 1000.00, '2026-08-05 09:42:00'
       UNION ALL SELECT (@cx_p + 25), (@cx_a + 28), (@cx_sm + 6), (@cx_u + 6), '확정', 'A', 1000.00, '2026-08-07 01:17:00'
       UNION ALL SELECT (@cx_p + 26), (@cx_a + 27), (@cx_sm + 6), (@cx_u + 4), '확정', 'A', 1000.00, '2026-08-07 01:29:00'
       UNION ALL SELECT (@cx_p + 27), (@cx_a + 26), (@cx_sm + 6), (@cx_u + 2), '확정', 'A', 1000.00, '2026-08-07 01:47:00'
       UNION ALL SELECT (@cx_p + 28), (@cx_a + 25), (@cx_sm + 6), (@cx_u + 1), '확정', 'A', 1000.00, '2026-08-07 02:08:00'
       UNION ALL SELECT (@cx_p + 29), (@cx_a + 29), (@cx_sm + 6), (@cx_u + 31), '확정', 'B', 968.00, '2026-08-07 06:48:00'
       UNION ALL SELECT (@cx_p + 30), (@cx_a + 32), (@cx_sm + 6), (@cx_u + 36), '확정', 'B', 1000.00, '2026-08-07 06:49:00'
       UNION ALL SELECT (@cx_p + 31), (@cx_a + 31), (@cx_sm + 6), (@cx_u + 35), '확정', 'B', 1000.00, '2026-08-07 07:03:00'
       UNION ALL SELECT (@cx_p + 32), (@cx_a + 30), (@cx_sm + 6), (@cx_u + 32), '확정', 'B', 968.00, '2026-08-07 07:24:00'
       UNION ALL SELECT (@cx_p + 33), (@cx_a + 35), (@cx_sm + 7), (@cx_u + 48), '확정', 'B', 968.00, '2026-08-07 15:13:00'
       UNION ALL SELECT (@cx_p + 34), (@cx_a + 36), (@cx_sm + 7), (@cx_u + 50), '확정', 'B', 1000.00, '2026-08-07 15:37:00'
       UNION ALL SELECT (@cx_p + 35), (@cx_a + 34), (@cx_sm + 7), (@cx_u + 13), '확정', 'A', 1000.00, '2026-08-07 21:04:00'
       UNION ALL SELECT (@cx_p + 36), (@cx_a + 33), (@cx_sm + 7), (@cx_u + 11), '확정', 'A', 1000.00, '2026-08-07 21:45:00'
       UNION ALL SELECT (@cx_p + 37), (@cx_a + 39), (@cx_sm + 8), (@cx_u + 27), '확정', 'B', 1000.00, '2026-08-10 16:11:00'
       UNION ALL SELECT (@cx_p + 38), (@cx_a + 40), (@cx_sm + 8), (@cx_u + 30), '확정', 'B', 1000.00, '2026-08-10 16:25:00'
       UNION ALL SELECT (@cx_p + 39), (@cx_a + 37), (@cx_sm + 8), (@cx_u + 23), '확정', 'A', 968.00, '2026-08-11 17:35:00'
       UNION ALL SELECT (@cx_p + 40), (@cx_a + 38), (@cx_sm + 8), (@cx_u + 26), '확정', 'A', 1000.00, '2026-08-11 19:29:00'
       UNION ALL SELECT (@cx_p + 41), (@cx_a + 42), (@cx_sm + 9), (@cx_u + 67), '확정', 'A', 1000.00, '2026-08-15 15:23:00'
       UNION ALL SELECT (@cx_p + 42), (@cx_a + 41), (@cx_sm + 9), (@cx_u + 66), '확정', 'A', 1032.00, '2026-08-15 15:51:00'
       UNION ALL SELECT (@cx_p + 43), (@cx_a + 43), (@cx_sm + 9), (@cx_u + 27), '확정', 'B', 998.53, '2026-08-16 14:08:00'
       UNION ALL SELECT (@cx_p + 44), (@cx_a + 44), (@cx_sm + 9), (@cx_u + 28), '확정', 'B', 1000.00, '2026-08-16 15:29:00'
       UNION ALL SELECT (@cx_p + 45), (@cx_a + 45), (@cx_sm + 10), (@cx_u + 11), '확정', 'A', 966.53, '2026-08-18 12:33:00'
       UNION ALL SELECT (@cx_p + 46), (@cx_a + 46), (@cx_sm + 10), (@cx_u + 14), '확정', 'A', 1000.00, '2026-08-18 13:50:00'
       UNION ALL SELECT (@cx_p + 47), (@cx_a + 47), (@cx_sm + 10), (@cx_u + 7), '확정', 'B', 1032.00, '2026-08-18 17:50:00'
       UNION ALL SELECT (@cx_p + 48), (@cx_a + 48), (@cx_sm + 10), (@cx_u + 10), '확정', 'B', 1000.00, '2026-08-18 18:11:00'
       UNION ALL SELECT (@cx_p + 49), (@cx_a + 49), (@cx_sm + 11), (@cx_u + 19), '확정', 'A', 1032.00, '2026-08-17 21:16:00'
       UNION ALL SELECT (@cx_p + 50), (@cx_a + 50), (@cx_sm + 11), (@cx_u + 21), '확정', 'A', 1000.00, '2026-08-17 21:17:00'
       UNION ALL SELECT (@cx_p + 51), (@cx_a + 51), (@cx_sm + 11), (@cx_u + 15), '확정', 'B', 1032.00, '2026-08-19 19:13:00'
       UNION ALL SELECT (@cx_p + 52), (@cx_a + 52), (@cx_sm + 11), (@cx_u + 17), '확정', 'B', 1000.00, '2026-08-19 19:24:00'
       UNION ALL SELECT (@cx_p + 53), (@cx_a + 59), (@cx_sm + 12), (@cx_u + 59), '확정', 'B', 1000.00, '2026-08-20 10:46:00'
       UNION ALL SELECT (@cx_p + 54), (@cx_a + 57), (@cx_sm + 12), (@cx_u + 56), '확정', 'B', 1032.00, '2026-08-20 10:48:00'
       UNION ALL SELECT (@cx_p + 55), (@cx_a + 60), (@cx_sm + 12), (@cx_u + 58), '확정', 'B', 1000.00, '2026-08-20 11:22:00'
       UNION ALL SELECT (@cx_p + 56), (@cx_a + 58), (@cx_sm + 12), (@cx_u + 57), '확정', 'B', 1032.00, '2026-08-20 11:34:00'
       UNION ALL SELECT (@cx_p + 57), (@cx_a + 54), (@cx_sm + 12), (@cx_u + 2), '확정', 'A', 1030.53, '2026-08-21 08:10:00'
       UNION ALL SELECT (@cx_p + 58), (@cx_a + 56), (@cx_sm + 12), (@cx_u + 5), '확정', 'A', 1000.00, '2026-08-21 08:41:00'
       UNION ALL SELECT (@cx_p + 59), (@cx_a + 55), (@cx_sm + 12), (@cx_u + 3), '확정', 'A', 1000.00, '2026-08-21 09:12:00'
       UNION ALL SELECT (@cx_p + 60), (@cx_a + 53), (@cx_sm + 12), (@cx_u + 1), '확정', 'A', 1030.53, '2026-08-21 09:21:00'
       UNION ALL SELECT (@cx_p + 61), (@cx_a + 61), (@cx_sm + 13), (@cx_u + 23), '확정', 'A', 969.47, '2026-08-24 12:51:00'
       UNION ALL SELECT (@cx_p + 62), (@cx_a + 62), (@cx_sm + 13), (@cx_u + 25), '확정', 'A', 1000.00, '2026-08-24 13:16:00'
       UNION ALL SELECT (@cx_p + 63), (@cx_a + 64), (@cx_sm + 13), (@cx_u + 9), '확정', 'B', 1000.00, '2026-08-24 14:49:00'
       UNION ALL SELECT (@cx_p + 64), (@cx_a + 63), (@cx_sm + 13), (@cx_u + 7), '확정', 'B', 1060.99, '2026-08-24 16:28:00'
       UNION ALL SELECT (@cx_p + 65), (@cx_a + 65), (@cx_sm + 14), (@cx_u + 19), '확정', 'A', 1000.00, '2026-08-26 16:07:00'
       UNION ALL SELECT (@cx_p + 66), (@cx_a + 66), (@cx_sm + 14), (@cx_u + 20), '확정', 'A', 1000.00, '2026-08-26 16:56:00'
       UNION ALL SELECT (@cx_p + 67), (@cx_a + 67), (@cx_sm + 14), (@cx_u + 52), '확정', 'B', 968.00, '2026-08-26 22:19:00'
       UNION ALL SELECT (@cx_p + 68), (@cx_a + 68), (@cx_sm + 14), (@cx_u + 53), '확정', 'B', 1000.00, '2026-08-26 22:47:00'
       UNION ALL SELECT (@cx_p + 69), (@cx_a + 70), (@cx_sm + 15), (@cx_u + 49), '확정', 'A', 1000.00, '2026-08-25 06:13:00'
       UNION ALL SELECT (@cx_p + 70), (@cx_a + 69), (@cx_sm + 15), (@cx_u + 48), '확정', 'A', 1001.47, '2026-08-25 07:28:00'
       UNION ALL SELECT (@cx_p + 71), (@cx_a + 72), (@cx_sm + 15), (@cx_u + 29), '확정', 'B', 1000.00, '2026-08-27 02:13:00'
       UNION ALL SELECT (@cx_p + 72), (@cx_a + 71), (@cx_sm + 15), (@cx_u + 27), '확정', 'B', 968.07, '2026-08-27 02:42:00'
       UNION ALL SELECT (@cx_p + 73), (@cx_a + 77), (@cx_sm + 16), (@cx_u + 56), '확정', 'B', 1063.93, '2026-08-25 08:34:00'
       UNION ALL SELECT (@cx_p + 74), (@cx_a + 80), (@cx_sm + 16), (@cx_u + 61), '확정', 'B', 1032.00, '2026-08-25 08:39:00'
       UNION ALL SELECT (@cx_p + 75), (@cx_a + 78), (@cx_sm + 16), (@cx_u + 57), '확정', 'B', 1063.93, '2026-08-25 09:10:00'
       UNION ALL SELECT (@cx_p + 76), (@cx_a + 79), (@cx_sm + 16), (@cx_u + 58), '확정', 'B', 1031.93, '2026-08-25 09:21:00'
       UNION ALL SELECT (@cx_p + 77), (@cx_a + 74), (@cx_sm + 16), (@cx_u + 32), '확정', 'A', 937.47, '2026-08-27 05:36:00'
       UNION ALL SELECT (@cx_p + 78), (@cx_a + 73), (@cx_sm + 16), (@cx_u + 31), '확정', 'A', 937.47, '2026-08-27 06:43:00'
       UNION ALL SELECT (@cx_p + 79), (@cx_a + 75), (@cx_sm + 16), (@cx_u + 35), '확정', 'A', 969.47, '2026-08-27 06:50:00'
       UNION ALL SELECT (@cx_p + 80), (@cx_a + 76), (@cx_sm + 16), (@cx_u + 36), '확정', 'A', 969.47, '2026-08-27 06:58:00'
       UNION ALL SELECT (@cx_p + 81), (@cx_a + 84), (@cx_sm + 17), (@cx_u + 38), '확정', 'B', 1000.00, '2026-08-29 19:35:00'
       UNION ALL SELECT (@cx_p + 82), (@cx_a + 83), (@cx_sm + 17), (@cx_u + 37), '확정', 'B', 1000.00, '2026-08-29 20:44:00'
       UNION ALL SELECT (@cx_p + 83), (@cx_a + 82), (@cx_sm + 17), (@cx_u + 54), '확정', 'A', 1000.00, '2026-08-30 05:38:00'
       UNION ALL SELECT (@cx_p + 84), (@cx_a + 81), (@cx_sm + 17), (@cx_u + 52), '확정', 'A', 937.47, '2026-08-30 06:48:00'
       UNION ALL SELECT (@cx_p + 85), (@cx_a + 88), (@cx_sm + 18), (@cx_u + 69), '확정', 'B', 1000.00, '2026-08-31 16:12:00'
       UNION ALL SELECT (@cx_p + 86), (@cx_a + 87), (@cx_sm + 18), (@cx_u + 66), '확정', 'B', 1062.46, '2026-08-31 16:19:00'
       UNION ALL SELECT (@cx_p + 87), (@cx_a + 85), (@cx_sm + 18), (@cx_u + 11), '확정', 'A', 937.54, '2026-09-01 19:17:00'
       UNION ALL SELECT (@cx_p + 88), (@cx_a + 86), (@cx_sm + 18), (@cx_u + 12), '확정', 'A', 1000.00, '2026-09-01 19:27:00'
       UNION ALL SELECT (@cx_p + 89), (@cx_a + 91), (@cx_sm + 19), (@cx_u + 7), '확정', 'B', 1088.80, '2026-09-07 12:31:00'
       UNION ALL SELECT (@cx_p + 90), (@cx_a + 92), (@cx_sm + 19), (@cx_u + 10), '확정', 'B', 1028.99, '2026-09-07 12:42:00'
       UNION ALL SELECT (@cx_p + 91), (@cx_a + 89), (@cx_sm + 19), (@cx_u + 27), '확정', 'A', 937.61, '2026-09-08 14:15:00'
       UNION ALL SELECT (@cx_p + 92), (@cx_a + 90), (@cx_sm + 19), (@cx_u + 28), '확정', 'A', 969.54, '2026-09-08 14:38:00'
       UNION ALL SELECT (@cx_p + 93), (@cx_a + 94), (@cx_sm + 20), (@cx_u + 71), '확정', 'A', 1000.00, '2026-09-09 03:33:00'
       UNION ALL SELECT (@cx_p + 94), (@cx_a + 93), (@cx_sm + 20), (@cx_u + 70), '확정', 'A', 968.00, '2026-09-09 04:15:00'
       UNION ALL SELECT (@cx_p + 95), (@cx_a + 95), (@cx_sm + 20), (@cx_u + 52), '확정', 'B', 908.34, '2026-09-09 06:06:00'
       UNION ALL SELECT (@cx_p + 96), (@cx_a + 96), (@cx_sm + 20), (@cx_u + 53), '확정', 'B', 969.47, '2026-09-09 06:16:00'
       UNION ALL SELECT (@cx_p + 97), (@cx_a + 99), (@cx_sm + 21), (@cx_u + 37), '확정', 'B', 1029.13, '2026-09-11 13:55:00'
       UNION ALL SELECT (@cx_p + 98), (@cx_a + 100), (@cx_sm + 21), (@cx_u + 40), '확정', 'B', 1000.00, '2026-09-11 14:04:00'
       UNION ALL SELECT (@cx_p + 99), (@cx_a + 97), (@cx_sm + 21), (@cx_u + 19), '확정', 'A', 1030.53, '2026-09-12 15:16:00'
       UNION ALL SELECT (@cx_p + 100), (@cx_a + 98), (@cx_sm + 21), (@cx_u + 22), '확정', 'A', 1032.00, '2026-09-12 16:16:00'
       UNION ALL SELECT (@cx_p + 101), (@cx_a + 101), (@cx_sm + 22), (@cx_u + 48), '확정', 'A', 1031.93, '2026-09-15 02:42:00'
       UNION ALL SELECT (@cx_p + 102), (@cx_a + 102), (@cx_sm + 22), (@cx_u + 51), '확정', 'A', 968.00, '2026-09-15 03:00:00'
       UNION ALL SELECT (@cx_p + 103), (@cx_a + 104), (@cx_sm + 22), (@cx_u + 69), '확정', 'B', 962.31, '2026-09-17 02:30:00'
       UNION ALL SELECT (@cx_p + 104), (@cx_a + 103), (@cx_sm + 22), (@cx_u + 66), '확정', 'B', 1024.77, '2026-09-17 02:33:00'
       UNION ALL SELECT (@cx_p + 105), (@cx_a + 111), (@cx_sm + 23), (@cx_u + 60), '확정', 'B', 1032.00, '2026-09-15 02:02:00'
       UNION ALL SELECT (@cx_p + 106), (@cx_a + 109), (@cx_sm + 23), (@cx_u + 56), '확정', 'B', 1087.44, '2026-09-15 02:06:00'
       UNION ALL SELECT (@cx_p + 107), (@cx_a + 110), (@cx_sm + 23), (@cx_u + 57), '확정', 'B', 1087.44, '2026-09-15 03:38:00'
       UNION ALL SELECT (@cx_p + 108), (@cx_a + 112), (@cx_sm + 23), (@cx_u + 59), '확정', 'B', 1031.93, '2026-09-15 03:58:00'
       UNION ALL SELECT (@cx_p + 109), (@cx_a + 108), (@cx_sm + 23), (@cx_u + 33), '확정', 'A', 968.00, '2026-09-17 07:20:00'
       UNION ALL SELECT (@cx_p + 110), (@cx_a + 105), (@cx_sm + 23), (@cx_u + 31), '확정', 'A', 913.96, '2026-09-17 08:08:00'
       UNION ALL SELECT (@cx_p + 111), (@cx_a + 107), (@cx_sm + 23), (@cx_u + 34), '확정', 'A', 968.00, '2026-09-17 08:55:00'
       UNION ALL SELECT (@cx_p + 112), (@cx_a + 106), (@cx_sm + 23), (@cx_u + 32), '확정', 'A', 913.96, '2026-09-17 08:58:00'
       UNION ALL SELECT (@cx_p + 113), (@cx_a + 113), (@cx_sm + 24), (@cx_u + 23), '확정', 'A', 941.66, '2026-09-21 11:37:00'
       UNION ALL SELECT (@cx_p + 114), (@cx_a + 114), (@cx_sm + 24), (@cx_u + 24), '확정', 'A', 968.00, '2026-09-21 13:08:00'
       UNION ALL SELECT (@cx_p + 115), (@cx_a + 116), (@cx_sm + 24), (@cx_u + 12), '확정', 'B', 1037.69, '2026-09-21 15:52:00'
       UNION ALL SELECT (@cx_p + 116), (@cx_a + 115), (@cx_sm + 24), (@cx_u + 11), '확정', 'B', 975.23, '2026-09-21 16:07:00'
       UNION ALL SELECT (@cx_p + 117), (@cx_a + 121), (@cx_sm + 25), (@cx_u + 47), '확정', 'A', 1000.00, '2026-09-22 18:10:00'
       UNION ALL SELECT (@cx_p + 118), (@cx_a + 118), (@cx_sm + 25), (@cx_u + 42), '확정', 'A', 1000.00, '2026-09-22 18:13:00'
       UNION ALL SELECT (@cx_p + 119), (@cx_a + 120), (@cx_sm + 25), (@cx_u + 46), '확정', 'A', 1000.00, '2026-09-22 18:20:00'
       UNION ALL SELECT (@cx_p + 120), (@cx_a + 119), (@cx_sm + 25), (@cx_u + 43), '확정', 'A', 1000.00, '2026-09-22 18:47:00'
       UNION ALL SELECT (@cx_p + 121), (@cx_a + 117), (@cx_sm + 25), (@cx_u + 41), '확정', 'A', 1000.00, '2026-09-22 19:15:00'
       UNION ALL SELECT (@cx_p + 122), (@cx_a + 125), (@cx_sm + 25), (@cx_u + 77), '확정', 'B', 1000.00, '2026-09-24 18:22:00'
       UNION ALL SELECT (@cx_p + 123), (@cx_a + 123), (@cx_sm + 25), (@cx_u + 75), '확정', 'B', 1000.00, '2026-09-24 18:51:00'
       UNION ALL SELECT (@cx_p + 124), (@cx_a + 124), (@cx_sm + 25), (@cx_u + 76), '확정', 'B', 1000.00, '2026-09-24 19:11:00'
       UNION ALL SELECT (@cx_p + 125), (@cx_a + 122), (@cx_sm + 25), (@cx_u + 74), '확정', 'B', 1000.00, '2026-09-24 19:21:00'
       UNION ALL SELECT (@cx_p + 126), (@cx_a + 126), (@cx_sm + 25), (@cx_u + 80), '확정', 'B', 1000.00, '2026-09-24 19:25:00'
       UNION ALL SELECT (@cx_p + 127), (@cx_a + 127), (@cx_sm + 26), (@cx_u + 81), '확정', 'A', 1000.00, '2026-09-26 12:15:00'
       UNION ALL SELECT (@cx_p + 128), (@cx_a + 131), (@cx_sm + 26), (@cx_u + 86), '확정', 'A', 1000.00, '2026-09-26 12:25:00'
       UNION ALL SELECT (@cx_p + 129), (@cx_a + 129), (@cx_sm + 26), (@cx_u + 83), '확정', 'A', 1000.00, '2026-09-26 12:37:00'
       UNION ALL SELECT (@cx_p + 130), (@cx_a + 130), (@cx_sm + 26), (@cx_u + 85), '확정', 'A', 1000.00, '2026-09-26 12:59:00'
       UNION ALL SELECT (@cx_p + 131), (@cx_a + 128), (@cx_sm + 26), (@cx_u + 82), '확정', 'A', 1000.00, '2026-09-26 13:05:00'
       UNION ALL SELECT (@cx_p + 132), (@cx_a + 135), (@cx_sm + 26), (@cx_u + 78), '확정', 'B', 1000.00, '2026-09-28 01:17:00'
       UNION ALL SELECT (@cx_p + 133), (@cx_a + 136), (@cx_sm + 26), (@cx_u + 79), '확정', 'B', 1000.00, '2026-09-28 01:21:00'
       UNION ALL SELECT (@cx_p + 134), (@cx_a + 132), (@cx_sm + 26), (@cx_u + 74), '확정', 'B', 1032.00, '2026-09-28 01:50:00'
       UNION ALL SELECT (@cx_p + 135), (@cx_a + 134), (@cx_sm + 26), (@cx_u + 76), '확정', 'B', 1032.00, '2026-09-28 02:32:00'
       UNION ALL SELECT (@cx_p + 136), (@cx_a + 133), (@cx_sm + 26), (@cx_u + 75), '확정', 'B', 1032.00, '2026-09-28 02:39:00'
       UNION ALL SELECT (@cx_p + 137), (@cx_a + 140), (@cx_sm + 27), (@cx_u + 16), '확정', 'B', 1000.00, '2026-09-27 21:12:00'
       UNION ALL SELECT (@cx_p + 138), (@cx_a + 138), (@cx_sm + 27), (@cx_u + 21), '확정', 'A', 968.00, '2026-09-27 22:09:00'
       UNION ALL SELECT (@cx_p + 139), (@cx_a + 139), (@cx_sm + 27), (@cx_u + 15), '확정', 'B', 1064.00, '2026-09-27 22:22:00'
       UNION ALL SELECT (@cx_p + 140), (@cx_a + 137), (@cx_sm + 27), (@cx_u + 19), '확정', 'A', 996.99, '2026-09-27 22:33:00'
       UNION ALL SELECT (@cx_p + 141), (@cx_a + 150), (@cx_sm + 28), (@cx_u + 93), '확정', 'B', 1000.00, '2026-09-26 21:24:00'
       UNION ALL SELECT (@cx_p + 142), (@cx_a + 149), (@cx_sm + 28), (@cx_u + 94), '확정', 'B', 1000.00, '2026-09-26 21:43:00'
       UNION ALL SELECT (@cx_p + 143), (@cx_a + 148), (@cx_sm + 28), (@cx_u + 90), '확정', 'B', 1000.00, '2026-09-26 21:44:00'
       UNION ALL SELECT (@cx_p + 144), (@cx_a + 146), (@cx_sm + 28), (@cx_u + 88), '확정', 'B', 1000.00, '2026-09-26 22:44:00'
       UNION ALL SELECT (@cx_p + 145), (@cx_a + 147), (@cx_sm + 28), (@cx_u + 89), '확정', 'B', 1000.00, '2026-09-26 22:51:00'
       UNION ALL SELECT (@cx_p + 146), (@cx_a + 145), (@cx_sm + 28), (@cx_u + 45), '확정', 'A', 1000.00, '2026-09-28 11:19:00'
       UNION ALL SELECT (@cx_p + 147), (@cx_a + 143), (@cx_sm + 28), (@cx_u + 43), '확정', 'A', 968.00, '2026-09-28 11:38:00'
       UNION ALL SELECT (@cx_p + 148), (@cx_a + 141), (@cx_sm + 28), (@cx_u + 41), '확정', 'A', 968.00, '2026-09-28 11:40:00'
       UNION ALL SELECT (@cx_p + 149), (@cx_a + 142), (@cx_sm + 28), (@cx_u + 42), '확정', 'A', 968.00, '2026-09-28 11:40:00'
       UNION ALL SELECT (@cx_p + 150), (@cx_a + 144), (@cx_sm + 28), (@cx_u + 44), '확정', 'A', 1000.00, '2026-09-28 12:21:00'
       UNION ALL SELECT (@cx_p + 151), (@cx_a + 155), (@cx_sm + 29), (@cx_u + 95), '확정', 'B', 1000.00, '2026-09-28 13:20:00'
       UNION ALL SELECT (@cx_p + 152), (@cx_a + 158), (@cx_sm + 29), (@cx_u + 98), '확정', 'B', 1000.00, '2026-09-28 13:33:00'
       UNION ALL SELECT (@cx_p + 153), (@cx_a + 156), (@cx_sm + 29), (@cx_u + 96), '확정', 'B', 1000.00, '2026-09-28 13:34:00'
       UNION ALL SELECT (@cx_p + 154), (@cx_a + 157), (@cx_sm + 29), (@cx_u + 97), '확정', 'B', 1000.00, '2026-09-28 14:23:00'
       UNION ALL SELECT (@cx_p + 155), (@cx_a + 154), (@cx_sm + 29), (@cx_u + 58), '확정', 'A', 1055.44, '2026-09-29 20:47:00'
       UNION ALL SELECT (@cx_p + 156), (@cx_a + 152), (@cx_sm + 29), (@cx_u + 57), '확정', 'A', 1108.91, '2026-09-29 20:49:00'
       UNION ALL SELECT (@cx_p + 157), (@cx_a + 151), (@cx_sm + 29), (@cx_u + 56), '확정', 'A', 1108.91, '2026-09-29 21:19:00'
       UNION ALL SELECT (@cx_p + 158), (@cx_a + 153), (@cx_sm + 29), (@cx_u + 60), '확정', 'A', 1053.47, '2026-09-29 21:22:00'
       UNION ALL SELECT (@cx_p + 159), (@cx_a + 159), (@cx_sm + 30), (@cx_u + 1), '확정', 'A', 998.60, '2026-09-30 02:06:00'
       UNION ALL SELECT (@cx_p + 160), (@cx_a + 162), (@cx_sm + 30), (@cx_u + 6), '확정', 'A', 1030.53, '2026-09-30 02:32:00'
       UNION ALL SELECT (@cx_p + 161), (@cx_a + 161), (@cx_sm + 30), (@cx_u + 5), '확정', 'A', 968.07, '2026-09-30 02:37:00'
       UNION ALL SELECT (@cx_p + 162), (@cx_a + 160), (@cx_sm + 30), (@cx_u + 2), '확정', 'A', 998.60, '2026-09-30 02:42:00'
       UNION ALL SELECT (@cx_p + 163), (@cx_a + 163), (@cx_sm + 30), (@cx_u + 31), '확정', 'B', 892.49, '2026-09-30 05:29:00'
       UNION ALL SELECT (@cx_p + 164), (@cx_a + 164), (@cx_sm + 30), (@cx_u + 32), '확정', 'B', 892.49, '2026-09-30 06:16:00'
       UNION ALL SELECT (@cx_p + 165), (@cx_a + 166), (@cx_sm + 30), (@cx_u + 33), '확정', 'B', 946.53, '2026-09-30 06:20:00'
       UNION ALL SELECT (@cx_p + 166), (@cx_a + 165), (@cx_sm + 30), (@cx_u + 35), '확정', 'B', 945.96, '2026-09-30 06:31:00'
       UNION ALL SELECT (@cx_p + 167), (@cx_a + 168), (@cx_sm + 31), (@cx_u + 75), '확정', 'A', 998.23, '2026-10-01 04:08:00'
       UNION ALL SELECT (@cx_p + 168), (@cx_a + 169), (@cx_sm + 31), (@cx_u + 76), '확정', 'A', 998.23, '2026-10-01 04:16:00'
       UNION ALL SELECT (@cx_p + 169), (@cx_a + 167), (@cx_sm + 31), (@cx_u + 74), '확정', 'A', 998.23, '2026-10-01 04:59:00'
       UNION ALL SELECT (@cx_p + 170), (@cx_a + 170), (@cx_sm + 31), (@cx_u + 80), '확정', 'A', 1032.00, '2026-10-01 05:13:00'
       UNION ALL SELECT (@cx_p + 171), (@cx_a + 171), (@cx_sm + 31), (@cx_u + 78), '확정', 'A', 966.23, '2026-10-01 05:15:00'
       UNION ALL SELECT (@cx_p + 172), (@cx_a + 176), (@cx_sm + 31), (@cx_u + 91), '확정', 'B', 1000.00, '2026-10-02 03:03:00'
       UNION ALL SELECT (@cx_p + 173), (@cx_a + 175), (@cx_sm + 31), (@cx_u + 92), '확정', 'B', 1000.00, '2026-10-02 03:28:00'
       UNION ALL SELECT (@cx_p + 174), (@cx_a + 172), (@cx_sm + 31), (@cx_u + 88), '확정', 'B', 966.23, '2026-10-02 03:33:00'
       UNION ALL SELECT (@cx_p + 175), (@cx_a + 174), (@cx_sm + 31), (@cx_u + 90), '확정', 'B', 966.23, '2026-10-02 04:27:00'
       UNION ALL SELECT (@cx_p + 176), (@cx_a + 173), (@cx_sm + 31), (@cx_u + 89), '확정', 'B', 966.23, '2026-10-02 04:38:00'
       UNION ALL SELECT (@cx_p + 177), (@cx_a + 179), (@cx_sm + 32), (@cx_u + 66), '확정', 'B', 1057.37, '2026-09-29 23:29:00'
       UNION ALL SELECT (@cx_p + 178), (@cx_a + 180), (@cx_sm + 32), (@cx_u + 67), '확정', 'B', 1030.46, '2026-09-30 00:56:00'
       UNION ALL SELECT (@cx_p + 179), (@cx_a + 178), (@cx_sm + 32), (@cx_u + 49), '확정', 'A', 1030.46, '2026-10-01 02:27:00'
       UNION ALL SELECT (@cx_p + 180), (@cx_a + 177), (@cx_sm + 32), (@cx_u + 48), '확정', 'A', 999.33, '2026-10-01 03:10:00'
       UNION ALL SELECT (@cx_p + 181), (@cx_a + 190), (@cx_sm + 33), (@cx_u + 45), '확정', 'B', 1033.77, '2026-10-02 12:01:00'
       UNION ALL SELECT (@cx_p + 182), (@cx_a + 186), (@cx_sm + 33), (@cx_u + 41), '확정', 'B', 1001.77, '2026-10-02 12:14:00'
       UNION ALL SELECT (@cx_p + 183), (@cx_a + 189), (@cx_sm + 33), (@cx_u + 46), '확정', 'B', 968.00, '2026-10-02 12:37:00'
       UNION ALL SELECT (@cx_p + 184), (@cx_a + 188), (@cx_sm + 33), (@cx_u + 43), '확정', 'B', 1001.77, '2026-10-02 13:01:00'
       UNION ALL SELECT (@cx_p + 185), (@cx_a + 187), (@cx_sm + 33), (@cx_u + 42), '확정', 'B', 1001.77, '2026-10-02 13:07:00'
       UNION ALL SELECT (@cx_p + 186), (@cx_a + 183), (@cx_sm + 33), (@cx_u + 83), '확정', 'A', 1033.77, '2026-10-03 13:03:00'
       UNION ALL SELECT (@cx_p + 187), (@cx_a + 184), (@cx_sm + 33), (@cx_u + 84), '확정', 'A', 1000.00, '2026-10-03 13:57:00'
       UNION ALL SELECT (@cx_p + 188), (@cx_a + 185), (@cx_sm + 33), (@cx_u + 87), '확정', 'A', 1000.00, '2026-10-03 14:11:00'
       UNION ALL SELECT (@cx_p + 189), (@cx_a + 181), (@cx_sm + 33), (@cx_u + 81), '확정', 'A', 1033.77, '2026-10-03 14:20:00'
       UNION ALL SELECT (@cx_p + 190), (@cx_a + 182), (@cx_sm + 33), (@cx_u + 82), '확정', 'A', 1033.77, '2026-10-03 14:36:00'
       UNION ALL SELECT (@cx_p + 191), (@cx_a + 192), (@cx_sm + 34), (@cx_u + 54), '확정', 'A', 970.87, '2026-10-03 15:43:00'
       UNION ALL SELECT (@cx_p + 192), (@cx_a + 191), (@cx_sm + 34), (@cx_u + 52), '확정', 'A', 944.47, '2026-10-03 16:40:00'
       UNION ALL SELECT (@cx_p + 193), (@cx_a + 193), (@cx_sm + 34), (@cx_u + 37), '확정', 'B', 1062.67, '2026-10-03 21:12:00'
       UNION ALL SELECT (@cx_p + 194), (@cx_a + 194), (@cx_sm + 34), (@cx_u + 39), '확정', 'B', 1000.00, '2026-10-03 22:44:00'
       UNION ALL SELECT (@cx_p + 195), (@cx_a + 199), (@cx_sm + 35), (@cx_u + 101), '확정', 'B', 1000.00, '2026-10-04 13:35:00'
       UNION ALL SELECT (@cx_p + 196), (@cx_a + 201), (@cx_sm + 35), (@cx_u + 103), '확정', 'B', 1000.00, '2026-10-04 13:39:00'
       UNION ALL SELECT (@cx_p + 197), (@cx_a + 202), (@cx_sm + 35), (@cx_u + 105), '확정', 'B', 1000.00, '2026-10-04 13:39:00'
       UNION ALL SELECT (@cx_p + 198), (@cx_a + 200), (@cx_sm + 35), (@cx_u + 102), '확정', 'B', 1000.00, '2026-10-04 14:29:00'
       UNION ALL SELECT (@cx_p + 199), (@cx_a + 198), (@cx_sm + 35), (@cx_u + 100), '확정', 'A', 1000.00, '2026-10-05 17:13:00'
       UNION ALL SELECT (@cx_p + 200), (@cx_a + 197), (@cx_sm + 35), (@cx_u + 99), '확정', 'A', 1000.00, '2026-10-05 17:53:00'
       UNION ALL SELECT (@cx_p + 201), (@cx_a + 196), (@cx_sm + 35), (@cx_u + 96), '확정', 'A', 1039.39, '2026-10-05 18:31:00'
       UNION ALL SELECT (@cx_p + 202), (@cx_a + 195), (@cx_sm + 35), (@cx_u + 95), '확정', 'A', 1039.39, '2026-10-05 18:53:00'
       UNION ALL SELECT (@cx_p + 203), (@cx_a + 203), (@cx_sm + 36), (@cx_u + 27), '확정', 'A', 915.02, '2026-10-05 14:01:00'
       UNION ALL SELECT (@cx_p + 204), (@cx_a + 204), (@cx_sm + 36), (@cx_u + 30), '확정', 'A', 998.53, '2026-10-05 14:53:00'
       UNION ALL SELECT (@cx_p + 205), (@cx_a + 206), (@cx_sm + 36), (@cx_u + 8), '확정', 'B', 1032.00, '2026-10-05 22:30:00'
       UNION ALL SELECT (@cx_p + 206), (@cx_a + 205), (@cx_sm + 36), (@cx_u + 7), '확정', 'B', 1111.39, '2026-10-05 22:40:00'
       UNION ALL SELECT (@cx_p + 207), (@cx_a + 210), (@cx_sm + 37), (@cx_u + 3), '확정', 'A', 968.07, '2026-10-08 10:41:00'
       UNION ALL SELECT (@cx_p + 208), (@cx_a + 209), (@cx_sm + 37), (@cx_u + 4), '확정', 'A', 1030.53, '2026-10-08 10:41:00'
       UNION ALL SELECT (@cx_p + 209), (@cx_a + 208), (@cx_sm + 37), (@cx_u + 2), '확정', 'A', 959.39, '2026-10-08 11:15:00'
       UNION ALL SELECT (@cx_p + 210), (@cx_a + 207), (@cx_sm + 37), (@cx_u + 1), '확정', 'A', 959.39, '2026-10-08 11:54:00'
       UNION ALL SELECT (@cx_p + 211), (@cx_a + 212), (@cx_sm + 37), (@cx_u + 102), '확정', 'B', 1033.81, '2026-10-09 03:07:00'
       UNION ALL SELECT (@cx_p + 212), (@cx_a + 213), (@cx_sm + 37), (@cx_u + 104), '확정', 'B', 1000.00, '2026-10-09 03:38:00'
       UNION ALL SELECT (@cx_p + 213), (@cx_a + 211), (@cx_sm + 37), (@cx_u + 101), '확정', 'B', 1033.81, '2026-10-09 04:48:00'
       UNION ALL SELECT (@cx_p + 214), (@cx_a + 214), (@cx_sm + 37), (@cx_u + 106), '확정', 'B', 1000.00, '2026-10-09 04:51:00'
       UNION ALL SELECT (@cx_p + 215), (@cx_a + 218), (@cx_sm + 38), (@cx_u + 62), '확정', 'B', 1000.00, '2026-10-07 05:09:00'
       UNION ALL SELECT (@cx_p + 216), (@cx_a + 220), (@cx_sm + 38), (@cx_u + 65), '확정', 'B', 1000.00, '2026-10-07 06:19:00'
       UNION ALL SELECT (@cx_p + 217), (@cx_a + 219), (@cx_sm + 38), (@cx_u + 63), '확정', 'B', 1000.00, '2026-10-07 06:51:00'
       UNION ALL SELECT (@cx_p + 218), (@cx_a + 217), (@cx_sm + 38), (@cx_u + 109), '확정', 'A', 1000.00, '2026-10-08 07:23:00'
       UNION ALL SELECT (@cx_p + 219), (@cx_a + 215), (@cx_sm + 38), (@cx_u + 107), '확정', 'A', 1000.00, '2026-10-08 08:32:00'
       UNION ALL SELECT (@cx_p + 220), (@cx_a + 216), (@cx_sm + 38), (@cx_u + 108), '확정', 'A', 1000.00, '2026-10-08 08:44:00'
       UNION ALL SELECT (@cx_p + 221), (@cx_a + 224), (@cx_sm + 39), (@cx_u + 113), '확정', 'B', 1000.00, '2026-10-08 04:39:00'
       UNION ALL SELECT (@cx_p + 222), (@cx_a + 223), (@cx_sm + 39), (@cx_u + 111), '확정', 'B', 1000.00, '2026-10-08 04:40:00'
       UNION ALL SELECT (@cx_p + 223), (@cx_a + 221), (@cx_sm + 39), (@cx_u + 70), '확정', 'A', 931.87, '2026-10-08 07:05:00'
       UNION ALL SELECT (@cx_p + 224), (@cx_a + 222), (@cx_sm + 39), (@cx_u + 72), '확정', 'A', 1000.00, '2026-10-08 07:21:00'
       UNION ALL SELECT (@cx_p + 225), (@cx_a + 229), (@cx_sm + 40), (@cx_u + 87), '확정', 'A', 1030.27, '2026-10-10 13:11:00'
       UNION ALL SELECT (@cx_p + 226), (@cx_a + 227), (@cx_sm + 40), (@cx_u + 83), '확정', 'A', 1064.04, '2026-10-10 13:16:00'
       UNION ALL SELECT (@cx_p + 227), (@cx_a + 225), (@cx_sm + 40), (@cx_u + 81), '확정', 'A', 1064.04, '2026-10-10 13:51:00'
       UNION ALL SELECT (@cx_p + 228), (@cx_a + 226), (@cx_sm + 40), (@cx_u + 82), '확정', 'A', 1064.04, '2026-10-10 14:09:00'
       UNION ALL SELECT (@cx_p + 229), (@cx_a + 228), (@cx_sm + 40), (@cx_u + 84), '확정', 'A', 1030.27, '2026-10-10 14:47:00'
       UNION ALL SELECT (@cx_p + 230), (@cx_a + 232), (@cx_sm + 40), (@cx_u + 90), '확정', 'B', 999.96, '2026-10-10 20:22:00'
       UNION ALL SELECT (@cx_p + 231), (@cx_a + 230), (@cx_sm + 40), (@cx_u + 88), '확정', 'B', 999.96, '2026-10-10 20:28:00'
       UNION ALL SELECT (@cx_p + 232), (@cx_a + 231), (@cx_sm + 40), (@cx_u + 89), '확정', 'B', 999.96, '2026-10-10 21:30:00'
       UNION ALL SELECT (@cx_p + 233), (@cx_a + 233), (@cx_sm + 40), (@cx_u + 93), '확정', 'B', 966.23, '2026-10-10 21:52:00'
       UNION ALL SELECT (@cx_p + 234), (@cx_a + 234), (@cx_sm + 40), (@cx_u + 94), '확정', 'B', 966.23, '2026-10-10 21:56:00'
       UNION ALL SELECT (@cx_p + 235), (@cx_a + 235), (@cx_sm + 41), (@cx_u + 11), '확정', 'A', 1002.51, '2026-10-09 20:07:00'
       UNION ALL SELECT (@cx_p + 236), (@cx_a + 236), (@cx_sm + 41), (@cx_u + 14), '확정', 'A', 971.01, '2026-10-09 20:32:00'
       UNION ALL SELECT (@cx_p + 237), (@cx_a + 237), (@cx_sm + 41), (@cx_u + 115), '확정', 'B', 1000.00, '2026-10-10 15:47:00'
       UNION ALL SELECT (@cx_p + 238), (@cx_a + 238), (@cx_sm + 41), (@cx_u + 116), '확정', 'B', 1000.00, '2026-10-10 16:48:00'
       UNION ALL SELECT (@cx_p + 239), (@cx_a + 239), (@cx_sm + 42), (@cx_u + 15), '확정', 'A', 1027.47, '2026-10-11 10:25:00'
       UNION ALL SELECT (@cx_p + 240), (@cx_a + 240), (@cx_sm + 42), (@cx_u + 16), '확정', 'A', 963.47, '2026-10-11 11:38:00'
       UNION ALL SELECT (@cx_p + 241), (@cx_a + 241), (@cx_sm + 42), (@cx_u + 37), '확정', 'B', 1055.98, '2026-10-12 12:05:00'
       UNION ALL SELECT (@cx_p + 242), (@cx_a + 242), (@cx_sm + 42), (@cx_u + 40), '확정', 'B', 1033.54, '2026-10-12 13:05:00'
       UNION ALL SELECT (@cx_p + 243), (@cx_a + 245), (@cx_sm + 43), (@cx_u + 43), '확정', 'A', 971.50, '2026-10-11 20:08:00'
       UNION ALL SELECT (@cx_p + 244), (@cx_a + 244), (@cx_sm + 43), (@cx_u + 42), '확정', 'A', 971.50, '2026-10-11 20:32:00'
       UNION ALL SELECT (@cx_p + 245), (@cx_a + 246), (@cx_sm + 43), (@cx_u + 44), '확정', 'A', 1033.77, '2026-10-11 20:37:00'
       UNION ALL SELECT (@cx_p + 246), (@cx_a + 243), (@cx_sm + 43), (@cx_u + 41), '확정', 'A', 971.50, '2026-10-11 20:48:00'
       UNION ALL SELECT (@cx_p + 247), (@cx_a + 247), (@cx_sm + 43), (@cx_u + 47), '확정', 'A', 968.00, '2026-10-11 20:54:00'
       UNION ALL SELECT (@cx_p + 248), (@cx_a + 248), (@cx_sm + 43), (@cx_u + 74), '확정', 'B', 964.50, '2026-10-12 14:07:00'
       UNION ALL SELECT (@cx_p + 249), (@cx_a + 251), (@cx_sm + 43), (@cx_u + 79), '확정', 'B', 966.23, '2026-10-12 14:07:00'
       UNION ALL SELECT (@cx_p + 250), (@cx_a + 249), (@cx_sm + 43), (@cx_u + 75), '확정', 'B', 964.50, '2026-10-12 14:10:00'
       UNION ALL SELECT (@cx_p + 251), (@cx_a + 252), (@cx_sm + 43), (@cx_u + 77), '확정', 'B', 1032.00, '2026-10-12 14:30:00'
       UNION ALL SELECT (@cx_p + 252), (@cx_a + 250), (@cx_sm + 43), (@cx_u + 76), '확정', 'B', 964.50, '2026-10-12 14:33:00'
       UNION ALL SELECT (@cx_p + 253), (@cx_a + 253), (@cx_sm + 44), (@cx_u + 52), '확정', 'A', 951.16, '2026-10-11 21:53:00'
       UNION ALL SELECT (@cx_p + 254), (@cx_a + 254), (@cx_sm + 44), (@cx_u + 55), '확정', 'A', 968.00, '2026-10-11 21:59:00'
       UNION ALL SELECT (@cx_p + 255), (@cx_a + 256), (@cx_sm + 44), (@cx_u + 112), '확정', 'B', 1000.00, '2026-10-12 14:50:00'
       UNION ALL SELECT (@cx_p + 256), (@cx_a + 255), (@cx_sm + 44), (@cx_u + 111), '확정', 'B', 1028.87, '2026-10-12 15:38:00'
       UNION ALL SELECT (@cx_p + 257), (@cx_a + 257), (@cx_sm + 45), (@cx_u + 7), '확정', 'A', 1069.18, '2026-10-12 05:24:00'
       UNION ALL SELECT (@cx_p + 258), (@cx_a + 258), (@cx_sm + 45), (@cx_u + 9), '확정', 'A', 1027.81, '2026-10-12 05:27:00'
       UNION ALL SELECT (@cx_p + 259), (@cx_a + 259), (@cx_sm + 45), (@cx_u + 115), '확정', 'B', 1030.78, '2026-10-15 02:32:00'
       UNION ALL SELECT (@cx_p + 260), (@cx_a + 260), (@cx_sm + 45), (@cx_u + 117), '확정', 'B', 1000.00, '2026-10-15 03:12:00'
       UNION ALL SELECT (@cx_p + 261), (@cx_a + 262), (@cx_sm + 46), (@cx_u + 20), '확정', 'A', 1030.53, '2026-10-13 20:02:00'
       UNION ALL SELECT (@cx_p + 262), (@cx_a + 261), (@cx_sm + 46), (@cx_u + 19), '확정', 'A', 1033.52, '2026-10-13 20:24:00'
       UNION ALL SELECT (@cx_p + 263), (@cx_a + 264), (@cx_sm + 46), (@cx_u + 72), '확정', 'B', 971.13, '2026-10-14 15:01:00'
       UNION ALL SELECT (@cx_p + 264), (@cx_a + 263), (@cx_sm + 46), (@cx_u + 70), '확정', 'B', 903.00, '2026-10-14 15:10:00'
       UNION ALL SELECT (@cx_p + 265), (@cx_a + 267), (@cx_sm + 47), (@cx_u + 64), '확정', 'A', 1000.00, '2026-10-14 05:00:00'
       UNION ALL SELECT (@cx_p + 266), (@cx_a + 266), (@cx_sm + 47), (@cx_u + 63), '확정', 'A', 968.00, '2026-10-14 05:41:00'
       UNION ALL SELECT (@cx_p + 267), (@cx_a + 265), (@cx_sm + 47), (@cx_u + 62), '확정', 'A', 968.00, '2026-10-14 06:58:00'
       UNION ALL SELECT (@cx_p + 268), (@cx_a + 268), (@cx_sm + 47), (@cx_u + 107), '확정', 'B', 1032.00, '2026-10-15 04:13:00'
       UNION ALL SELECT (@cx_p + 269), (@cx_a + 269), (@cx_sm + 47), (@cx_u + 108), '확정', 'B', 1032.00, '2026-10-15 04:14:00'
       UNION ALL SELECT (@cx_p + 270), (@cx_a + 270), (@cx_sm + 47), (@cx_u + 110), '확정', 'B', 1000.00, '2026-10-15 05:23:00'
       UNION ALL SELECT (@cx_p + 271), (@cx_a + 271), (@cx_sm + 48), (@cx_u + 88), '확정', 'A', 1037.80, '2026-10-15 10:43:00'
       UNION ALL SELECT (@cx_p + 272), (@cx_a + 272), (@cx_sm + 48), (@cx_u + 89), '확정', 'A', 1037.80, '2026-10-15 10:44:00'
       UNION ALL SELECT (@cx_p + 273), (@cx_a + 273), (@cx_sm + 48), (@cx_u + 90), '확정', 'A', 1037.80, '2026-10-15 10:46:00'
       UNION ALL SELECT (@cx_p + 274), (@cx_a + 275), (@cx_sm + 48), (@cx_u + 91), '확정', 'A', 1033.73, '2026-10-15 10:51:00'
       UNION ALL SELECT (@cx_p + 275), (@cx_a + 274), (@cx_sm + 48), (@cx_u + 92), '확정', 'A', 1033.73, '2026-10-15 11:24:00'
       UNION ALL SELECT (@cx_p + 276), (@cx_a + 278), (@cx_sm + 48), (@cx_u + 43), '확정', 'B', 939.05, '2026-10-16 03:29:00'
       UNION ALL SELECT (@cx_p + 277), (@cx_a + 276), (@cx_sm + 48), (@cx_u + 41), '확정', 'B', 939.05, '2026-10-16 03:54:00'
       UNION ALL SELECT (@cx_p + 278), (@cx_a + 277), (@cx_sm + 48), (@cx_u + 42), '확정', 'B', 939.05, '2026-10-16 04:01:00'
       UNION ALL SELECT (@cx_p + 279), (@cx_a + 280), (@cx_sm + 48), (@cx_u + 47), '확정', 'B', 935.55, '2026-10-16 04:26:00'
       UNION ALL SELECT (@cx_p + 280), (@cx_a + 279), (@cx_sm + 48), (@cx_u + 45), '확정', 'B', 1003.50, '2026-10-16 04:31:00'
       UNION ALL SELECT (@cx_p + 281), (@cx_a + 288), (@cx_sm + 49), (@cx_u + 5), '확정', 'B', 928.86, '2026-10-13 10:00:00'
       UNION ALL SELECT (@cx_p + 282), (@cx_a + 287), (@cx_sm + 49), (@cx_u + 4), '확정', 'B', 1001.97, '2026-10-13 10:05:00'
       UNION ALL SELECT (@cx_p + 283), (@cx_a + 285), (@cx_sm + 49), (@cx_u + 1), '확정', 'B', 930.83, '2026-10-13 10:12:00'
       UNION ALL SELECT (@cx_p + 284), (@cx_a + 286), (@cx_sm + 49), (@cx_u + 2), '확정', 'B', 930.83, '2026-10-13 10:48:00'
       UNION ALL SELECT (@cx_p + 285), (@cx_a + 281), (@cx_sm + 49), (@cx_u + 95), '확정', 'A', 1005.58, '2026-10-15 15:48:00'
       UNION ALL SELECT (@cx_p + 286), (@cx_a + 284), (@cx_sm + 49), (@cx_u + 97), '확정', 'A', 1039.39, '2026-10-15 15:52:00'
       UNION ALL SELECT (@cx_p + 287), (@cx_a + 282), (@cx_sm + 49), (@cx_u + 96), '확정', 'A', 1005.58, '2026-10-15 16:14:00'
       UNION ALL SELECT (@cx_p + 288), (@cx_a + 283), (@cx_sm + 49), (@cx_u + 98), '확정', 'A', 1039.39, '2026-10-15 16:56:00'
       UNION ALL SELECT (@cx_p + 289), (@cx_a + 289), (@cx_sm + 50), (@cx_u + 48), '확정', 'A', 1034.00, '2026-10-14 05:45:00'
       UNION ALL SELECT (@cx_p + 290), (@cx_a + 290), (@cx_sm + 50), (@cx_u + 50), '확정', 'A', 1033.47, '2026-10-14 06:15:00'
       UNION ALL SELECT (@cx_p + 291), (@cx_a + 292), (@cx_sm + 50), (@cx_u + 26), '확정', 'B', 1001.47, '2026-10-16 03:59:00'
       UNION ALL SELECT (@cx_p + 292), (@cx_a + 291), (@cx_sm + 50), (@cx_u + 23), '확정', 'B', 914.38, '2026-10-16 04:35:00'
       UNION ALL SELECT (@cx_p + 293), (@cx_a + 293), (@cx_sm + 51), (@cx_u + 37), '확정', 'A', 1019.47, '2026-10-16 00:25:00'
       UNION ALL SELECT (@cx_p + 294), (@cx_a + 294), (@cx_sm + 51), (@cx_u + 39), '확정', 'A', 993.31, '2026-10-16 00:36:00'
       UNION ALL SELECT (@cx_p + 295), (@cx_a + 295), (@cx_sm + 51), (@cx_u + 111), '확정', 'B', 1055.86, '2026-10-17 00:24:00'
       UNION ALL SELECT (@cx_p + 296), (@cx_a + 296), (@cx_sm + 51), (@cx_u + 114), '확정', 'B', 1000.00, '2026-10-17 01:58:00'
       UNION ALL SELECT (@cx_p + 297), (@cx_a + 299), (@cx_sm + 52), (@cx_u + 115), '확정', 'B', 1065.82, '2026-10-17 12:55:00'
       UNION ALL SELECT (@cx_p + 298), (@cx_a + 300), (@cx_sm + 52), (@cx_u + 118), '확정', 'B', 1000.00, '2026-10-17 13:18:00'
       UNION ALL SELECT (@cx_p + 299), (@cx_a + 298), (@cx_sm + 52), (@cx_u + 13), '확정', 'A', 966.53, '2026-10-17 17:32:00'
       UNION ALL SELECT (@cx_p + 300), (@cx_a + 297), (@cx_sm + 52), (@cx_u + 11), '확정', 'A', 971.73, '2026-10-17 17:49:00'
       UNION ALL SELECT (@cx_p + 301), (@cx_a + 307), (@cx_sm + 53), (@cx_u + 104), '확정', 'B', 1028.56, '2026-10-18 09:18:00'
       UNION ALL SELECT (@cx_p + 302), (@cx_a + 305), (@cx_sm + 53), (@cx_u + 101), '확정', 'B', 1062.37, '2026-10-18 09:34:00'
       UNION ALL SELECT (@cx_p + 303), (@cx_a + 308), (@cx_sm + 53), (@cx_u + 103), '확정', 'B', 1033.81, '2026-10-18 10:31:00'
       UNION ALL SELECT (@cx_p + 304), (@cx_a + 306), (@cx_sm + 53), (@cx_u + 102), '확정', 'B', 1062.37, '2026-10-18 10:51:00'
       UNION ALL SELECT (@cx_p + 305), (@cx_a + 302), (@cx_sm + 53), (@cx_u + 96), '확정', 'A', 1030.83, '2026-10-19 03:19:00'
       UNION ALL SELECT (@cx_p + 306), (@cx_a + 303), (@cx_sm + 53), (@cx_u + 100), '확정', 'A', 966.19, '2026-10-19 03:34:00'
       UNION ALL SELECT (@cx_p + 307), (@cx_a + 301), (@cx_sm + 53), (@cx_u + 95), '확정', 'A', 1030.83, '2026-10-19 03:43:00'
       UNION ALL SELECT (@cx_p + 308), (@cx_a + 304), (@cx_sm + 53), (@cx_u + 99), '확정', 'A', 966.19, '2026-10-19 03:54:00'
       UNION ALL SELECT (@cx_p + 309), (@cx_a + 315), (@cx_sm + 54), (@cx_u + 82), '확정', 'B', 1026.20, '2026-10-18 19:15:00'
       UNION ALL SELECT (@cx_p + 310), (@cx_a + 317), (@cx_sm + 54), (@cx_u + 86), '확정', 'B', 1033.77, '2026-10-18 19:36:00'
       UNION ALL SELECT (@cx_p + 311), (@cx_a + 314), (@cx_sm + 54), (@cx_u + 81), '확정', 'B', 1026.20, '2026-10-18 20:29:00'
       UNION ALL SELECT (@cx_p + 312), (@cx_a + 318), (@cx_sm + 54), (@cx_u + 85), '확정', 'B', 1033.77, '2026-10-18 20:36:00'
       UNION ALL SELECT (@cx_p + 313), (@cx_a + 316), (@cx_sm + 54), (@cx_u + 83), '확정', 'B', 1026.20, '2026-10-18 20:38:00'
       UNION ALL SELECT (@cx_p + 314), (@cx_a + 311), (@cx_sm + 54), (@cx_u + 43), '확정', 'A', 978.72, '2026-10-19 13:10:00'
       UNION ALL SELECT (@cx_p + 315), (@cx_a + 312), (@cx_sm + 54), (@cx_u + 44), '확정', 'A', 1001.32, '2026-10-19 14:02:00'
       UNION ALL SELECT (@cx_p + 316), (@cx_a + 309), (@cx_sm + 54), (@cx_u + 41), '확정', 'A', 978.72, '2026-10-19 14:10:00'
       UNION ALL SELECT (@cx_p + 317), (@cx_a + 313), (@cx_sm + 54), (@cx_u + 46), '확정', 'A', 937.73, '2026-10-19 14:18:00'
       UNION ALL SELECT (@cx_p + 318), (@cx_a + 310), (@cx_sm + 54), (@cx_u + 42), '확정', 'A', 978.72, '2026-10-19 14:24:00'
       UNION ALL SELECT (@cx_p + 319), (@cx_a + 320), (@cx_sm + 55), (@cx_u + 113), '확정', 'A', 1028.87, '2026-10-19 16:47:00'
       UNION ALL SELECT (@cx_p + 320), (@cx_a + 319), (@cx_sm + 55), (@cx_u + 111), '확정', 'A', 1085.88, '2026-10-19 17:59:00'
       UNION ALL SELECT (@cx_p + 321), (@cx_a + 322), (@cx_sm + 55), (@cx_u + 17), '확정', 'B', 1032.00, '2026-10-21 14:21:00'
       UNION ALL SELECT (@cx_p + 322), (@cx_a + 321), (@cx_sm + 55), (@cx_u + 15), '확정', 'B', 1063.98, '2026-10-21 15:51:00'
       UNION ALL SELECT (@cx_p + 323), (@cx_a + 325), (@cx_sm + 56), (@cx_u + 37), '확정', 'B', 989.45, '2026-10-20 06:10:00'
       UNION ALL SELECT (@cx_p + 324), (@cx_a + 326), (@cx_sm + 56), (@cx_u + 38), '확정', 'B', 1029.13, '2026-10-20 06:15:00'
       UNION ALL SELECT (@cx_p + 325), (@cx_a + 324), (@cx_sm + 56), (@cx_u + 71), '확정', 'A', 963.87, '2026-10-21 09:41:00'
       UNION ALL SELECT (@cx_p + 326), (@cx_a + 323), (@cx_sm + 56), (@cx_u + 70), '확정', 'A', 943.54, '2026-10-21 09:45:00'
       UNION ALL SELECT (@cx_p + 327), (@cx_a + 330), (@cx_sm + 57), (@cx_u + 77), '확정', 'A', 1064.45, '2026-10-20 09:06:00'
       UNION ALL SELECT (@cx_p + 328), (@cx_a + 327), (@cx_sm + 57), (@cx_u + 74), '확정', 'A', 996.95, '2026-10-20 09:48:00'
       UNION ALL SELECT (@cx_p + 329), (@cx_a + 331), (@cx_sm + 57), (@cx_u + 80), '확정', 'A', 998.27, '2026-10-20 10:17:00'
       UNION ALL SELECT (@cx_p + 330), (@cx_a + 328), (@cx_sm + 57), (@cx_u + 75), '확정', 'A', 996.95, '2026-10-20 10:31:00'
       UNION ALL SELECT (@cx_p + 331), (@cx_a + 329), (@cx_sm + 57), (@cx_u + 76), '확정', 'A', 996.95, '2026-10-20 10:46:00'
       UNION ALL SELECT (@cx_p + 332), (@cx_a + 332), (@cx_sm + 57), (@cx_u + 81), '확정', 'B', 1053.25, '2026-10-23 04:10:00'
       UNION ALL SELECT (@cx_p + 333), (@cx_a + 334), (@cx_sm + 57), (@cx_u + 83), '확정', 'B', 1053.25, '2026-10-23 04:53:00'
       UNION ALL SELECT (@cx_p + 334), (@cx_a + 336), (@cx_sm + 57), (@cx_u + 87), '확정', 'B', 992.43, '2026-10-23 04:53:00'
       UNION ALL SELECT (@cx_p + 335), (@cx_a + 333), (@cx_sm + 57), (@cx_u + 82), '확정', 'B', 1053.25, '2026-10-23 05:02:00'
       UNION ALL SELECT (@cx_p + 336), (@cx_a + 335), (@cx_sm + 57), (@cx_u + 86), '확정', 'B', 1060.82, '2026-10-23 05:26:00'
       UNION ALL SELECT (@cx_p + 337), (@cx_a + 339), (@cx_sm + 58), (@cx_u + 109), '확정', 'A', 1032.00, '2026-10-20 09:29:00'
       UNION ALL SELECT (@cx_p + 338), (@cx_a + 337), (@cx_sm + 58), (@cx_u + 107), '확정', 'A', 996.09, '2026-10-20 10:10:00'
       UNION ALL SELECT (@cx_p + 339), (@cx_a + 338), (@cx_sm + 58), (@cx_u + 108), '확정', 'A', 996.09, '2026-10-20 10:15:00'
       UNION ALL SELECT (@cx_p + 340), (@cx_a + 341), (@cx_sm + 58), (@cx_u + 63), '확정', 'B', 1003.91, '2026-10-21 09:11:00'
       UNION ALL SELECT (@cx_p + 341), (@cx_a + 342), (@cx_sm + 58), (@cx_u + 65), '확정', 'B', 968.00, '2026-10-21 09:33:00'
       UNION ALL SELECT (@cx_p + 342), (@cx_a + 340), (@cx_sm + 58), (@cx_u + 62), '확정', 'B', 1003.91, '2026-10-21 10:48:00'
       UNION ALL SELECT (@cx_p + 343), (@cx_a + 346), (@cx_sm + 59), (@cx_u + 29), '확정', 'B', 969.54, '2026-10-21 04:36:00'
       UNION ALL SELECT (@cx_p + 344), (@cx_a + 345), (@cx_sm + 59), (@cx_u + 27), '확정', 'B', 957.23, '2026-10-21 05:25:00'
       UNION ALL SELECT (@cx_p + 345), (@cx_a + 343), (@cx_sm + 59), (@cx_u + 115), '확정', 'A', 1028.01, '2026-10-22 08:56:00'
       UNION ALL SELECT (@cx_p + 346), (@cx_a + 344), (@cx_sm + 59), (@cx_u + 117), '확정', 'A', 1035.04, '2026-10-22 09:36:00'
       UNION ALL SELECT (@cx_p + 347), (@cx_a + 348), (@cx_sm + 60), (@cx_u + 96), '확정', 'A', 1067.25, '2026-10-23 02:15:00'
       UNION ALL SELECT (@cx_p + 348), (@cx_a + 349), (@cx_sm + 60), (@cx_u + 100), '확정', 'A', 1002.61, '2026-10-23 02:17:00'
       UNION ALL SELECT (@cx_p + 349), (@cx_a + 350), (@cx_sm + 60), (@cx_u + 98), '확정', 'A', 1064.64, '2026-10-23 02:35:00'
       UNION ALL SELECT (@cx_p + 350), (@cx_a + 353), (@cx_sm + 60), (@cx_u + 105), '확정', 'B', 1033.81, '2026-10-23 03:13:00'
       UNION ALL SELECT (@cx_p + 351), (@cx_a + 354), (@cx_sm + 60), (@cx_u + 106), '확정', 'B', 1028.56, '2026-10-23 03:32:00'
       UNION ALL SELECT (@cx_p + 352), (@cx_a + 347), (@cx_sm + 60), (@cx_u + 95), '확정', 'A', 1067.25, '2026-10-23 03:51:00'
       UNION ALL SELECT (@cx_p + 353), (@cx_a + 352), (@cx_sm + 60), (@cx_u + 102), '확정', 'B', 1025.95, '2026-10-23 04:12:00'
       UNION ALL SELECT (@cx_p + 354), (@cx_a + 351), (@cx_sm + 60), (@cx_u + 101), '확정', 'B', 1025.95, '2026-10-23 04:53:00'
       UNION ALL SELECT (@cx_p + 355), (@cx_a + 362), (@cx_sm + 61), (@cx_u + 59), '확정', 'B', 1053.40, '2026-10-21 01:45:00'
       UNION ALL SELECT (@cx_p + 356), (@cx_a + 358), (@cx_sm + 61), (@cx_u + 6), '확정', 'A', 991.32, '2026-10-21 02:17:00'
       UNION ALL SELECT (@cx_p + 357), (@cx_a + 359), (@cx_sm + 61), (@cx_u + 56), '확정', 'B', 1069.52, '2026-10-21 02:21:00'
       UNION ALL SELECT (@cx_p + 358), (@cx_a + 360), (@cx_sm + 61), (@cx_u + 57), '확정', 'B', 1069.52, '2026-10-21 02:34:00'
       UNION ALL SELECT (@cx_p + 359), (@cx_a + 356), (@cx_sm + 61), (@cx_u + 2), '확정', 'A', 905.58, '2026-10-21 02:51:00'
       UNION ALL SELECT (@cx_p + 360), (@cx_a + 361), (@cx_sm + 61), (@cx_u + 61), '확정', 'B', 1055.51, '2026-10-21 02:57:00'
       UNION ALL SELECT (@cx_p + 361), (@cx_a + 357), (@cx_sm + 61), (@cx_u + 3), '확정', 'A', 939.51, '2026-10-21 03:08:00'
       UNION ALL SELECT (@cx_p + 362), (@cx_a + 355), (@cx_sm + 61), (@cx_u + 1), '확정', 'A', 905.58, '2026-10-21 03:36:00'
       UNION ALL SELECT (@cx_p + 363), (@cx_a + 364), (@cx_sm + 62), (@cx_u + 112), '확정', 'A', 1026.99, '2026-10-25 00:26:00'
       UNION ALL SELECT (@cx_p + 364), (@cx_a + 363), (@cx_sm + 62), (@cx_u + 111), '확정', 'A', 1117.02, '2026-10-25 01:07:00'
       UNION ALL SELECT (@cx_p + 365), (@cx_a + 365), (@cx_sm + 62), (@cx_u + 15), '확정', 'B', 1032.84, '2026-10-25 10:34:00'
       UNION ALL SELECT (@cx_p + 366), (@cx_a + 366), (@cx_sm + 62), (@cx_u + 18), '확정', 'B', 1032.00, '2026-10-25 11:41:00'
       UNION ALL SELECT (@cx_p + 367), (@cx_a + 367), (@cx_sm + 63), (@cx_u + 66), '확정', 'A', 1022.70, '2026-10-24 20:30:00'
       UNION ALL SELECT (@cx_p + 368), (@cx_a + 368), (@cx_sm + 63), (@cx_u + 68), '확정', 'A', 1032.00, '2026-10-24 20:52:00'
       UNION ALL SELECT (@cx_p + 369), (@cx_a + 370), (@cx_sm + 63), (@cx_u + 116), '확정', 'B', 1030.78, '2026-10-25 14:03:00'
       UNION ALL SELECT (@cx_p + 370), (@cx_a + 369), (@cx_sm + 63), (@cx_u + 115), '확정', 'B', 1053.81, '2026-10-25 15:10:00'
       UNION ALL SELECT (@cx_p + 371), (@cx_a + 380), (@cx_sm + 64), (@cx_u + 93), '확정', 'B', 1004.07, '2026-10-26 20:40:00'
       UNION ALL SELECT (@cx_p + 372), (@cx_a + 376), (@cx_sm + 64), (@cx_u + 88), '확정', 'B', 998.13, '2026-10-26 20:59:00'
       UNION ALL SELECT (@cx_p + 373), (@cx_a + 377), (@cx_sm + 64), (@cx_u + 89), '확정', 'B', 998.13, '2026-10-26 21:03:00'
       UNION ALL SELECT (@cx_p + 374), (@cx_a + 378), (@cx_sm + 64), (@cx_u + 90), '확정', 'B', 998.13, '2026-10-26 21:37:00'
       UNION ALL SELECT (@cx_p + 375), (@cx_a + 379), (@cx_sm + 64), (@cx_u + 92), '확정', 'B', 994.06, '2026-10-26 21:39:00'
       UNION ALL SELECT (@cx_p + 376), (@cx_a + 375), (@cx_sm + 64), (@cx_u + 85), '확정', 'A', 1060.82, '2026-10-27 11:40:00'
       UNION ALL SELECT (@cx_p + 377), (@cx_a + 371), (@cx_sm + 64), (@cx_u + 81), '확정', 'A', 1082.32, '2026-10-27 12:09:00'
       UNION ALL SELECT (@cx_p + 378), (@cx_a + 374), (@cx_sm + 64), (@cx_u + 84), '확정', 'A', 992.43, '2026-10-27 12:12:00'
       UNION ALL SELECT (@cx_p + 379), (@cx_a + 373), (@cx_sm + 64), (@cx_u + 83), '확정', 'A', 1082.32, '2026-10-27 12:33:00'
       UNION ALL SELECT (@cx_p + 380), (@cx_a + 372), (@cx_sm + 64), (@cx_u + 82), '확정', 'A', 1082.32, '2026-10-27 12:51:00'
       UNION ALL SELECT (@cx_p + 381), (@cx_a + 382), (@cx_sm + 65), (@cx_u + 63), '확정', 'A', 1037.39, '2026-10-27 09:01:00'
       UNION ALL SELECT (@cx_p + 382), (@cx_a + 381), (@cx_sm + 65), (@cx_u + 62), '확정', 'A', 1037.39, '2026-10-27 09:19:00'
       UNION ALL SELECT (@cx_p + 383), (@cx_a + 383), (@cx_sm + 65), (@cx_u + 64), '확정', 'A', 1035.91, '2026-10-27 10:55:00'
       UNION ALL SELECT (@cx_p + 384), (@cx_a + 385), (@cx_sm + 65), (@cx_u + 108), '확정', 'B', 962.61, '2026-10-29 06:13:00'
       UNION ALL SELECT (@cx_p + 385), (@cx_a + 384), (@cx_sm + 65), (@cx_u + 107), '확정', 'B', 962.61, '2026-10-29 07:07:00'
       UNION ALL SELECT (@cx_p + 386), (@cx_a + 386), (@cx_sm + 65), (@cx_u + 110), '확정', 'B', 964.09, '2026-10-29 07:58:00'
       UNION ALL SELECT (@cx_p + 387), (@cx_a + 388), (@cx_sm + 66), (@cx_u + 96), '확정', 'A', 1097.24, '2026-10-27 10:03:00'
       UNION ALL SELECT (@cx_p + 388), (@cx_a + 390), (@cx_sm + 66), (@cx_u + 99), '확정', 'A', 1002.61, '2026-10-27 10:07:00'
       UNION ALL SELECT (@cx_p + 389), (@cx_a + 389), (@cx_sm + 66), (@cx_u + 97), '확정', 'A', 1064.64, '2026-10-27 10:12:00'
       UNION ALL SELECT (@cx_p + 390), (@cx_a + 387), (@cx_sm + 66), (@cx_u + 95), '확정', 'A', 1097.24, '2026-10-27 11:37:00'
       UNION ALL SELECT (@cx_p + 391), (@cx_a + 393), (@cx_sm + 66), (@cx_u + 106), '확정', 'B', 998.57, '2026-10-29 18:54:00'
       UNION ALL SELECT (@cx_p + 392), (@cx_a + 391), (@cx_sm + 66), (@cx_u + 101), '확정', 'B', 995.96, '2026-10-29 18:55:00'
       UNION ALL SELECT (@cx_p + 393), (@cx_a + 392), (@cx_sm + 66), (@cx_u + 102), '확정', 'B', 995.96, '2026-10-29 19:04:00'
       UNION ALL SELECT (@cx_p + 394), (@cx_a + 394), (@cx_sm + 66), (@cx_u + 103), '확정', 'B', 997.39, '2026-10-29 19:39:00'
       UNION ALL SELECT (@cx_p + 395), (@cx_a + 395), (@cx_sm + 67), (@cx_u + 23), '확정', 'A', 953.25, '2026-11-02 20:35:00'
       UNION ALL SELECT (@cx_p + 396), (@cx_a + 396), (@cx_sm + 67), (@cx_u + 25), '확정', 'A', 972.19, '2026-11-02 21:14:00'
       UNION ALL SELECT (@cx_p + 397), (@cx_a + 397), (@cx_sm + 67), (@cx_u + 115), '확정', 'B', 1084.43, '2026-11-03 16:45:00'
       UNION ALL SELECT (@cx_p + 398), (@cx_a + 398), (@cx_sm + 67), (@cx_u + 118), '확정', 'B', 962.19, '2026-11-03 16:49:00'
       UNION ALL SELECT (@cx_p + 399), (@cx_a + 400), (@cx_sm + 68), (@cx_u + 114), '확정', 'A', 1030.02, '2026-11-02 19:03:00'
       UNION ALL SELECT (@cx_p + 400), (@cx_a + 399), (@cx_sm + 68), (@cx_u + 111), '확정', 'A', 1081.39, '2026-11-02 19:37:00'
       UNION ALL SELECT (@cx_p + 401), (@cx_a + 401), (@cx_sm + 68), (@cx_u + 70), '확정', 'B', 916.62, '2026-11-03 11:13:00'
       UNION ALL SELECT (@cx_p + 402), (@cx_a + 402), (@cx_sm + 68), (@cx_u + 73), '확정', 'B', 968.00, '2026-11-03 12:02:00'
       UNION ALL SELECT (@cx_p + 403), (@cx_a + 403), (@cx_sm + 69), (@cx_u + 31), '확정', 'A', 931.70, '2026-11-04 06:03:00'
       UNION ALL SELECT (@cx_p + 404), (@cx_a + 404), (@cx_sm + 69), (@cx_u + 32), '확정', 'A', 931.70, '2026-11-04 07:45:00'
       UNION ALL SELECT (@cx_p + 405), (@cx_a + 406), (@cx_sm + 69), (@cx_u + 34), '확정', 'A', 946.53, '2026-11-04 07:47:00'
       UNION ALL SELECT (@cx_p + 406), (@cx_a + 405), (@cx_sm + 69), (@cx_u + 36), '확정', 'A', 945.96, '2026-11-04 07:48:00'
       UNION ALL SELECT (@cx_p + 407), (@cx_a + 409), (@cx_sm + 69), (@cx_u + 105), '확정', 'B', 1003.82, '2026-11-05 02:08:00'
       UNION ALL SELECT (@cx_p + 408), (@cx_a + 410), (@cx_sm + 69), (@cx_u + 104), '확정', 'B', 992.14, '2026-11-05 02:11:00'
       UNION ALL SELECT (@cx_p + 409), (@cx_a + 408), (@cx_sm + 69), (@cx_u + 102), '확정', 'B', 1034.19, '2026-11-05 02:14:00'
       UNION ALL SELECT (@cx_p + 410), (@cx_a + 407), (@cx_sm + 69), (@cx_u + 101), '확정', 'B', 1034.19, '2026-11-05 02:15:00'
       UNION ALL SELECT (@cx_p + 411), (@cx_a + 414), (@cx_sm + 70), (@cx_u + 62), '확정', 'B', 1030.70, '2026-11-03 11:02:00'
       UNION ALL SELECT (@cx_p + 412), (@cx_a + 416), (@cx_sm + 70), (@cx_u + 65), '확정', 'B', 1001.48, '2026-11-03 11:33:00'
       UNION ALL SELECT (@cx_p + 413), (@cx_a + 415), (@cx_sm + 70), (@cx_u + 63), '확정', 'B', 1030.70, '2026-11-03 11:47:00'
       UNION ALL SELECT (@cx_p + 414), (@cx_a + 413), (@cx_sm + 70), (@cx_u + 110), '확정', 'A', 970.78, '2026-11-05 08:02:00'
       UNION ALL SELECT (@cx_p + 415), (@cx_a + 411), (@cx_sm + 70), (@cx_u + 107), '확정', 'A', 969.30, '2026-11-05 08:51:00'
       UNION ALL SELECT (@cx_p + 416), (@cx_a + 412), (@cx_sm + 70), (@cx_u + 108), '확정', 'A', 969.30, '2026-11-05 09:52:00'
       UNION ALL SELECT (@cx_p + 417), (@cx_a + 418), (@cx_sm + 71), (@cx_u + 89), '확정', 'A', 1035.74, '2026-11-05 05:31:00'
       UNION ALL SELECT (@cx_p + 418), (@cx_a + 420), (@cx_sm + 71), (@cx_u + 94), '확정', 'A', 1004.07, '2026-11-05 05:32:00'
       UNION ALL SELECT (@cx_p + 419), (@cx_a + 421), (@cx_sm + 71), (@cx_u + 91), '확정', 'A', 994.06, '2026-11-05 05:33:00'
       UNION ALL SELECT (@cx_p + 420), (@cx_a + 417), (@cx_sm + 71), (@cx_u + 88), '확정', 'A', 1035.74, '2026-11-05 05:58:00'
       UNION ALL SELECT (@cx_p + 421), (@cx_a + 419), (@cx_sm + 71), (@cx_u + 90), '확정', 'A', 1035.74, '2026-11-05 06:59:00'
       UNION ALL SELECT (@cx_p + 422), (@cx_a + 423), (@cx_sm + 71), (@cx_u + 75), '확정', 'B', 967.88, '2026-11-05 15:01:00'
       UNION ALL SELECT (@cx_p + 423), (@cx_a + 422), (@cx_sm + 71), (@cx_u + 74), '확정', 'B', 967.88, '2026-11-05 15:36:00'
       UNION ALL SELECT (@cx_p + 424), (@cx_a + 424), (@cx_sm + 71), (@cx_u + 76), '확정', 'B', 967.88, '2026-11-05 15:44:00'
       UNION ALL SELECT (@cx_p + 425), (@cx_a + 426), (@cx_sm + 71), (@cx_u + 79), '확정', 'B', 998.68, '2026-11-05 16:04:00'
       UNION ALL SELECT (@cx_p + 426), (@cx_a + 425), (@cx_sm + 71), (@cx_u + 78), '확정', 'B', 932.50, '2026-11-05 16:34:00'
) v
 WHERE @cx_ready = 1;
-- 팀장: 팀에서 entry_mmr이 가장 높은 참가자(같으면 번호가 작은 쪽, TeamCaptainSelector)
UPDATE social_match
   SET captain_a_participant_id = CASE match_id
         WHEN (@cx_sm + 1) THEN (@cx_p + 1)
         WHEN (@cx_sm + 2) THEN (@cx_p + 9)
         WHEN (@cx_sm + 3) THEN (@cx_p + 13)
         WHEN (@cx_sm + 4) THEN (@cx_p + 19)
         WHEN (@cx_sm + 5) THEN (@cx_p + 21)
         WHEN (@cx_sm + 6) THEN (@cx_p + 25)
         WHEN (@cx_sm + 7) THEN (@cx_p + 35)
         WHEN (@cx_sm + 8) THEN (@cx_p + 40)
         WHEN (@cx_sm + 9) THEN (@cx_p + 42)
         WHEN (@cx_sm + 10) THEN (@cx_p + 46)
         WHEN (@cx_sm + 11) THEN (@cx_p + 49)
         WHEN (@cx_sm + 12) THEN (@cx_p + 57)
         WHEN (@cx_sm + 13) THEN (@cx_p + 62)
         WHEN (@cx_sm + 14) THEN (@cx_p + 65)
         WHEN (@cx_sm + 15) THEN (@cx_p + 70)
         WHEN (@cx_sm + 16) THEN (@cx_p + 79)
         WHEN (@cx_sm + 17) THEN (@cx_p + 83)
         WHEN (@cx_sm + 18) THEN (@cx_p + 88)
         WHEN (@cx_sm + 19) THEN (@cx_p + 92)
         WHEN (@cx_sm + 20) THEN (@cx_p + 93)
         WHEN (@cx_sm + 21) THEN (@cx_p + 100)
         WHEN (@cx_sm + 22) THEN (@cx_p + 101)
         WHEN (@cx_sm + 23) THEN (@cx_p + 109)
         WHEN (@cx_sm + 24) THEN (@cx_p + 114)
         WHEN (@cx_sm + 25) THEN (@cx_p + 117)
         WHEN (@cx_sm + 26) THEN (@cx_p + 127)
         WHEN (@cx_sm + 27) THEN (@cx_p + 140)
         WHEN (@cx_sm + 28) THEN (@cx_p + 146)
         WHEN (@cx_sm + 29) THEN (@cx_p + 156)
         WHEN (@cx_sm + 30) THEN (@cx_p + 160)
         WHEN (@cx_sm + 31) THEN (@cx_p + 170)
         WHEN (@cx_sm + 32) THEN (@cx_p + 179)
         WHEN (@cx_sm + 33) THEN (@cx_p + 186)
         WHEN (@cx_sm + 34) THEN (@cx_p + 191)
         WHEN (@cx_sm + 35) THEN (@cx_p + 201)
         WHEN (@cx_sm + 36) THEN (@cx_p + 204)
         WHEN (@cx_sm + 37) THEN (@cx_p + 208)
         WHEN (@cx_sm + 38) THEN (@cx_p + 218)
         WHEN (@cx_sm + 39) THEN (@cx_p + 224)
         WHEN (@cx_sm + 40) THEN (@cx_p + 226)
         WHEN (@cx_sm + 41) THEN (@cx_p + 235)
         WHEN (@cx_sm + 42) THEN (@cx_p + 239)
         WHEN (@cx_sm + 43) THEN (@cx_p + 245)
         WHEN (@cx_sm + 44) THEN (@cx_p + 254)
         WHEN (@cx_sm + 45) THEN (@cx_p + 257)
         WHEN (@cx_sm + 46) THEN (@cx_p + 262)
         WHEN (@cx_sm + 47) THEN (@cx_p + 265)
         WHEN (@cx_sm + 48) THEN (@cx_p + 271)
         WHEN (@cx_sm + 49) THEN (@cx_p + 286)
         WHEN (@cx_sm + 50) THEN (@cx_p + 289)
         WHEN (@cx_sm + 51) THEN (@cx_p + 293)
         WHEN (@cx_sm + 52) THEN (@cx_p + 300)
         WHEN (@cx_sm + 53) THEN (@cx_p + 305)
         WHEN (@cx_sm + 54) THEN (@cx_p + 315)
         WHEN (@cx_sm + 55) THEN (@cx_p + 320)
         WHEN (@cx_sm + 56) THEN (@cx_p + 325)
         WHEN (@cx_sm + 57) THEN (@cx_p + 327)
         WHEN (@cx_sm + 58) THEN (@cx_p + 337)
         WHEN (@cx_sm + 59) THEN (@cx_p + 346)
         WHEN (@cx_sm + 60) THEN (@cx_p + 347)
         WHEN (@cx_sm + 61) THEN (@cx_p + 356)
         WHEN (@cx_sm + 62) THEN (@cx_p + 364)
         WHEN (@cx_sm + 63) THEN (@cx_p + 368)
         WHEN (@cx_sm + 64) THEN (@cx_p + 377)
         WHEN (@cx_sm + 65) THEN (@cx_p + 381)
         WHEN (@cx_sm + 66) THEN (@cx_p + 387)
         WHEN (@cx_sm + 67) THEN (@cx_p + 396)
         WHEN (@cx_sm + 68) THEN (@cx_p + 400)
         WHEN (@cx_sm + 69) THEN (@cx_p + 405)
         WHEN (@cx_sm + 70) THEN (@cx_p + 414)
         WHEN (@cx_sm + 71) THEN (@cx_p + 417)
       END,
       captain_b_participant_id = CASE match_id
         WHEN (@cx_sm + 1) THEN (@cx_p + 5)
         WHEN (@cx_sm + 2) THEN (@cx_p + 11)
         WHEN (@cx_sm + 3) THEN (@cx_p + 15)
         WHEN (@cx_sm + 4) THEN (@cx_p + 17)
         WHEN (@cx_sm + 5) THEN (@cx_p + 23)
         WHEN (@cx_sm + 6) THEN (@cx_p + 30)
         WHEN (@cx_sm + 7) THEN (@cx_p + 34)
         WHEN (@cx_sm + 8) THEN (@cx_p + 37)
         WHEN (@cx_sm + 9) THEN (@cx_p + 44)
         WHEN (@cx_sm + 10) THEN (@cx_p + 47)
         WHEN (@cx_sm + 11) THEN (@cx_p + 51)
         WHEN (@cx_sm + 12) THEN (@cx_p + 54)
         WHEN (@cx_sm + 13) THEN (@cx_p + 64)
         WHEN (@cx_sm + 14) THEN (@cx_p + 68)
         WHEN (@cx_sm + 15) THEN (@cx_p + 71)
         WHEN (@cx_sm + 16) THEN (@cx_p + 73)
         WHEN (@cx_sm + 17) THEN (@cx_p + 81)
         WHEN (@cx_sm + 18) THEN (@cx_p + 86)
         WHEN (@cx_sm + 19) THEN (@cx_p + 89)
         WHEN (@cx_sm + 20) THEN (@cx_p + 96)
         WHEN (@cx_sm + 21) THEN (@cx_p + 97)
         WHEN (@cx_sm + 22) THEN (@cx_p + 104)
         WHEN (@cx_sm + 23) THEN (@cx_p + 106)
         WHEN (@cx_sm + 24) THEN (@cx_p + 115)
         WHEN (@cx_sm + 25) THEN (@cx_p + 122)
         WHEN (@cx_sm + 26) THEN (@cx_p + 134)
         WHEN (@cx_sm + 27) THEN (@cx_p + 139)
         WHEN (@cx_sm + 28) THEN (@cx_p + 141)
         WHEN (@cx_sm + 29) THEN (@cx_p + 151)
         WHEN (@cx_sm + 30) THEN (@cx_p + 165)
         WHEN (@cx_sm + 31) THEN (@cx_p + 172)
         WHEN (@cx_sm + 32) THEN (@cx_p + 177)
         WHEN (@cx_sm + 33) THEN (@cx_p + 181)
         WHEN (@cx_sm + 34) THEN (@cx_p + 193)
         WHEN (@cx_sm + 35) THEN (@cx_p + 195)
         WHEN (@cx_sm + 36) THEN (@cx_p + 206)
         WHEN (@cx_sm + 37) THEN (@cx_p + 211)
         WHEN (@cx_sm + 38) THEN (@cx_p + 215)
         WHEN (@cx_sm + 39) THEN (@cx_p + 221)
         WHEN (@cx_sm + 40) THEN (@cx_p + 230)
         WHEN (@cx_sm + 41) THEN (@cx_p + 237)
         WHEN (@cx_sm + 42) THEN (@cx_p + 241)
         WHEN (@cx_sm + 43) THEN (@cx_p + 251)
         WHEN (@cx_sm + 44) THEN (@cx_p + 256)
         WHEN (@cx_sm + 45) THEN (@cx_p + 259)
         WHEN (@cx_sm + 46) THEN (@cx_p + 263)
         WHEN (@cx_sm + 47) THEN (@cx_p + 268)
         WHEN (@cx_sm + 48) THEN (@cx_p + 280)
         WHEN (@cx_sm + 49) THEN (@cx_p + 282)
         WHEN (@cx_sm + 50) THEN (@cx_p + 291)
         WHEN (@cx_sm + 51) THEN (@cx_p + 295)
         WHEN (@cx_sm + 52) THEN (@cx_p + 297)
         WHEN (@cx_sm + 53) THEN (@cx_p + 302)
         WHEN (@cx_sm + 54) THEN (@cx_p + 310)
         WHEN (@cx_sm + 55) THEN (@cx_p + 322)
         WHEN (@cx_sm + 56) THEN (@cx_p + 324)
         WHEN (@cx_sm + 57) THEN (@cx_p + 336)
         WHEN (@cx_sm + 58) THEN (@cx_p + 340)
         WHEN (@cx_sm + 59) THEN (@cx_p + 343)
         WHEN (@cx_sm + 60) THEN (@cx_p + 350)
         WHEN (@cx_sm + 61) THEN (@cx_p + 357)
         WHEN (@cx_sm + 62) THEN (@cx_p + 365)
         WHEN (@cx_sm + 63) THEN (@cx_p + 370)
         WHEN (@cx_sm + 64) THEN (@cx_p + 371)
         WHEN (@cx_sm + 65) THEN (@cx_p + 386)
         WHEN (@cx_sm + 66) THEN (@cx_p + 391)
         WHEN (@cx_sm + 67) THEN (@cx_p + 397)
         WHEN (@cx_sm + 68) THEN (@cx_p + 402)
         WHEN (@cx_sm + 69) THEN (@cx_p + 409)
         WHEN (@cx_sm + 70) THEN (@cx_p + 411)
         WHEN (@cx_sm + 71) THEN (@cx_p + 425)
       END
 WHERE @cx_ready = 1 AND match_id BETWEEN (@cx_sm + 1) AND (@cx_sm + 71);

-- 6. 두 팀장의 결과 제출 (같은 결과라 확정)
INSERT INTO match_result_submission (match_id, captain_participant_id, reported_result, submission_status, submitted_at)
SELECT * FROM (
                 SELECT (@cx_sm + 1) AS match_id, (@cx_p + 1) AS captain_participant_id, 'B_WIN' AS reported_result, 'CONFIRMED' AS submission_status, '2026-08-01 13:25:00' AS submitted_at
       UNION ALL SELECT (@cx_sm + 1), (@cx_p + 5), 'B_WIN', 'CONFIRMED', '2026-08-01 13:19:00'
       UNION ALL SELECT (@cx_sm + 2), (@cx_p + 9), 'B_WIN', 'CONFIRMED', '2026-08-02 10:58:00'
       UNION ALL SELECT (@cx_sm + 2), (@cx_p + 11), 'B_WIN', 'CONFIRMED', '2026-08-02 11:50:00'
       UNION ALL SELECT (@cx_sm + 3), (@cx_p + 13), 'B_WIN', 'CONFIRMED', '2026-08-02 12:01:00'
       UNION ALL SELECT (@cx_sm + 3), (@cx_p + 15), 'B_WIN', 'CONFIRMED', '2026-08-02 11:59:00'
       UNION ALL SELECT (@cx_sm + 4), (@cx_p + 19), 'B_WIN', 'CONFIRMED', '2026-08-04 22:51:00'
       UNION ALL SELECT (@cx_sm + 4), (@cx_p + 17), 'B_WIN', 'CONFIRMED', '2026-08-04 23:22:00'
       UNION ALL SELECT (@cx_sm + 5), (@cx_p + 21), 'A_WIN', 'CONFIRMED', '2026-08-07 09:24:00'
       UNION ALL SELECT (@cx_sm + 5), (@cx_p + 23), 'A_WIN', 'CONFIRMED', '2026-08-07 11:15:00'
       UNION ALL SELECT (@cx_sm + 6), (@cx_p + 25), 'A_WIN', 'CONFIRMED', '2026-08-09 12:30:00'
       UNION ALL SELECT (@cx_sm + 6), (@cx_p + 30), 'A_WIN', 'CONFIRMED', '2026-08-09 12:44:00'
       UNION ALL SELECT (@cx_sm + 7), (@cx_p + 35), 'B_WIN', 'CONFIRMED', '2026-08-11 23:20:00'
       UNION ALL SELECT (@cx_sm + 7), (@cx_p + 34), 'B_WIN', 'CONFIRMED', '2026-08-12 01:31:00'
       UNION ALL SELECT (@cx_sm + 8), (@cx_p + 40), 'DRAW', 'CONFIRMED', '2026-08-13 21:59:00'
       UNION ALL SELECT (@cx_sm + 8), (@cx_p + 37), 'DRAW', 'CONFIRMED', '2026-08-13 22:01:00'
       UNION ALL SELECT (@cx_sm + 9), (@cx_p + 42), 'A_WIN', 'CONFIRMED', '2026-08-19 23:11:00'
       UNION ALL SELECT (@cx_sm + 9), (@cx_p + 44), 'A_WIN', 'CONFIRMED', '2026-08-19 23:42:00'
       UNION ALL SELECT (@cx_sm + 10), (@cx_p + 46), 'B_WIN', 'CONFIRMED', '2026-08-21 00:15:00'
       UNION ALL SELECT (@cx_sm + 10), (@cx_p + 47), 'B_WIN', 'CONFIRMED', '2026-08-21 00:10:00'
       UNION ALL SELECT (@cx_sm + 11), (@cx_p + 49), 'B_WIN', 'CONFIRMED', '2026-08-21 23:04:00'
       UNION ALL SELECT (@cx_sm + 11), (@cx_p + 51), 'B_WIN', 'CONFIRMED', '2026-08-21 23:46:00'
       UNION ALL SELECT (@cx_sm + 12), (@cx_p + 57), 'B_WIN', 'CONFIRMED', '2026-08-23 12:05:00'
       UNION ALL SELECT (@cx_sm + 12), (@cx_p + 54), 'B_WIN', 'CONFIRMED', '2026-08-23 12:16:00'
       UNION ALL SELECT (@cx_sm + 13), (@cx_p + 62), 'B_WIN', 'CONFIRMED', '2026-08-27 22:53:00'
       UNION ALL SELECT (@cx_sm + 13), (@cx_p + 64), 'B_WIN', 'CONFIRMED', '2026-08-28 00:23:00'
       UNION ALL SELECT (@cx_sm + 14), (@cx_p + 65), 'A_WIN', 'CONFIRMED', '2026-08-28 22:22:00'
       UNION ALL SELECT (@cx_sm + 14), (@cx_p + 68), 'A_WIN', 'CONFIRMED', '2026-08-28 23:43:00'
       UNION ALL SELECT (@cx_sm + 15), (@cx_p + 70), 'A_WIN', 'CONFIRMED', '2026-08-29 10:44:00'
       UNION ALL SELECT (@cx_sm + 15), (@cx_p + 71), 'A_WIN', 'CONFIRMED', '2026-08-29 11:26:00'
       UNION ALL SELECT (@cx_sm + 16), (@cx_p + 79), 'B_WIN', 'CONFIRMED', '2026-08-29 13:13:00'
       UNION ALL SELECT (@cx_sm + 16), (@cx_p + 73), 'B_WIN', 'CONFIRMED', '2026-08-29 14:09:00'
       UNION ALL SELECT (@cx_sm + 17), (@cx_p + 83), 'B_WIN', 'CONFIRMED', '2026-09-01 23:22:00'
       UNION ALL SELECT (@cx_sm + 17), (@cx_p + 81), 'B_WIN', 'CONFIRMED', '2026-09-02 00:16:00'
       UNION ALL SELECT (@cx_sm + 18), (@cx_p + 88), 'A_WIN', 'CONFIRMED', '2026-09-03 23:25:00'
       UNION ALL SELECT (@cx_sm + 18), (@cx_p + 86), 'A_WIN', 'CONFIRMED', '2026-09-03 23:30:00'
       UNION ALL SELECT (@cx_sm + 19), (@cx_p + 92), 'B_WIN', 'CONFIRMED', '2026-09-11 22:50:00'
       UNION ALL SELECT (@cx_sm + 19), (@cx_p + 89), 'B_WIN', 'CONFIRMED', '2026-09-11 23:54:00'
       UNION ALL SELECT (@cx_sm + 20), (@cx_p + 93), 'B_WIN', 'CONFIRMED', '2026-09-13 11:35:00'
       UNION ALL SELECT (@cx_sm + 20), (@cx_p + 96), 'B_WIN', 'CONFIRMED', '2026-09-13 13:33:00'
       UNION ALL SELECT (@cx_sm + 21), (@cx_p + 100), 'B_WIN', 'CONFIRMED', '2026-09-15 23:30:00'
       UNION ALL SELECT (@cx_sm + 21), (@cx_p + 97), 'B_WIN', 'CONFIRMED', '2026-09-15 23:57:00'
       UNION ALL SELECT (@cx_sm + 22), (@cx_p + 101), 'B_WIN', 'CONFIRMED', '2026-09-19 11:17:00'
       UNION ALL SELECT (@cx_sm + 22), (@cx_p + 104), 'B_WIN', 'CONFIRMED', '2026-09-19 11:07:00'
       UNION ALL SELECT (@cx_sm + 23), (@cx_p + 109), 'B_WIN', 'CONFIRMED', '2026-09-19 13:01:00'
       UNION ALL SELECT (@cx_sm + 23), (@cx_p + 106), 'B_WIN', 'CONFIRMED', '2026-09-19 14:02:00'
       UNION ALL SELECT (@cx_sm + 24), (@cx_p + 114), 'B_WIN', 'CONFIRMED', '2026-09-24 22:49:00'
       UNION ALL SELECT (@cx_sm + 24), (@cx_p + 115), 'B_WIN', 'CONFIRMED', '2026-09-24 23:38:00'
       UNION ALL SELECT (@cx_sm + 25), (@cx_p + 117), 'B_WIN', 'CONFIRMED', '2026-09-26 23:13:00'
       UNION ALL SELECT (@cx_sm + 25), (@cx_p + 122), 'B_WIN', 'CONFIRMED', '2026-09-26 23:33:00'
       UNION ALL SELECT (@cx_sm + 26), (@cx_p + 127), 'A_WIN', 'CONFIRMED', '2026-09-29 21:23:00'
       UNION ALL SELECT (@cx_sm + 26), (@cx_p + 134), 'A_WIN', 'CONFIRMED', '2026-09-29 21:39:00'
       UNION ALL SELECT (@cx_sm + 27), (@cx_p + 140), 'A_WIN', 'CONFIRMED', '2026-09-29 23:05:00'
       UNION ALL SELECT (@cx_sm + 27), (@cx_p + 139), 'A_WIN', 'CONFIRMED', '2026-09-29 23:32:00'
       UNION ALL SELECT (@cx_sm + 28), (@cx_p + 146), 'A_WIN', 'CONFIRMED', '2026-09-30 22:35:00'
       UNION ALL SELECT (@cx_sm + 28), (@cx_p + 141), 'A_WIN', 'CONFIRMED', '2026-09-30 22:52:00'
       UNION ALL SELECT (@cx_sm + 29), (@cx_p + 156), 'B_WIN', 'CONFIRMED', '2026-10-02 22:01:00'
       UNION ALL SELECT (@cx_sm + 29), (@cx_p + 151), 'B_WIN', 'CONFIRMED', '2026-10-02 22:09:00'
       UNION ALL SELECT (@cx_sm + 30), (@cx_p + 160), 'B_WIN', 'CONFIRMED', '2026-10-03 11:45:00'
       UNION ALL SELECT (@cx_sm + 30), (@cx_p + 165), 'B_WIN', 'CONFIRMED', '2026-10-03 11:34:00'
       UNION ALL SELECT (@cx_sm + 31), (@cx_p + 170), 'B_WIN', 'CONFIRMED', '2026-10-03 13:01:00'
       UNION ALL SELECT (@cx_sm + 31), (@cx_p + 172), 'B_WIN', 'CONFIRMED', '2026-10-03 13:25:00'
       UNION ALL SELECT (@cx_sm + 32), (@cx_p + 179), 'A_WIN', 'CONFIRMED', '2026-10-04 10:35:00'
       UNION ALL SELECT (@cx_sm + 32), (@cx_p + 177), 'A_WIN', 'CONFIRMED', '2026-10-04 12:20:00'
       UNION ALL SELECT (@cx_sm + 33), (@cx_p + 186), 'A_WIN', 'CONFIRMED', '2026-10-06 21:32:00'
       UNION ALL SELECT (@cx_sm + 33), (@cx_p + 181), 'A_WIN', 'CONFIRMED', '2026-10-06 22:41:00'
       UNION ALL SELECT (@cx_sm + 34), (@cx_p + 191), 'DRAW', 'CONFIRMED', '2026-10-06 22:58:00'
       UNION ALL SELECT (@cx_sm + 34), (@cx_p + 193), 'DRAW', 'CONFIRMED', '2026-10-07 00:55:00'
       UNION ALL SELECT (@cx_sm + 35), (@cx_p + 201), 'B_WIN', 'CONFIRMED', '2026-10-07 21:09:00'
       UNION ALL SELECT (@cx_sm + 35), (@cx_p + 195), 'B_WIN', 'CONFIRMED', '2026-10-07 20:31:00'
       UNION ALL SELECT (@cx_sm + 36), (@cx_p + 204), 'A_WIN', 'CONFIRMED', '2026-10-07 22:46:00'
       UNION ALL SELECT (@cx_sm + 36), (@cx_p + 206), 'A_WIN', 'CONFIRMED', '2026-10-08 00:11:00'
       UNION ALL SELECT (@cx_sm + 37), (@cx_p + 208), 'B_WIN', 'CONFIRMED', '2026-10-10 11:38:00'
       UNION ALL SELECT (@cx_sm + 37), (@cx_p + 211), 'B_WIN', 'CONFIRMED', '2026-10-10 11:47:00'
       UNION ALL SELECT (@cx_sm + 38), (@cx_p + 218), 'A_WIN', 'CONFIRMED', '2026-10-10 12:44:00'
       UNION ALL SELECT (@cx_sm + 38), (@cx_p + 215), 'A_WIN', 'CONFIRMED', '2026-10-10 13:58:00'
       UNION ALL SELECT (@cx_sm + 39), (@cx_p + 224), 'B_WIN', 'CONFIRMED', '2026-10-11 11:30:00'
       UNION ALL SELECT (@cx_sm + 39), (@cx_p + 221), 'B_WIN', 'CONFIRMED', '2026-10-11 12:56:00'
       UNION ALL SELECT (@cx_sm + 40), (@cx_p + 226), 'B_WIN', 'CONFIRMED', '2026-10-13 21:24:00'
       UNION ALL SELECT (@cx_sm + 40), (@cx_p + 230), 'B_WIN', 'CONFIRMED', '2026-10-13 23:34:00'
       UNION ALL SELECT (@cx_sm + 41), (@cx_p + 235), 'B_WIN', 'CONFIRMED', '2026-10-14 00:05:00'
       UNION ALL SELECT (@cx_sm + 41), (@cx_p + 237), 'B_WIN', 'CONFIRMED', '2026-10-14 00:57:00'
       UNION ALL SELECT (@cx_sm + 42), (@cx_p + 239), 'A_WIN', 'CONFIRMED', '2026-10-14 21:26:00'
       UNION ALL SELECT (@cx_sm + 42), (@cx_p + 241), 'A_WIN', 'CONFIRMED', '2026-10-14 22:37:00'
       UNION ALL SELECT (@cx_sm + 43), (@cx_p + 245), 'B_WIN', 'CONFIRMED', '2026-10-14 22:53:00'
       UNION ALL SELECT (@cx_sm + 43), (@cx_p + 251), 'B_WIN', 'CONFIRMED', '2026-10-15 00:43:00'
       UNION ALL SELECT (@cx_sm + 44), (@cx_p + 254), 'B_WIN', 'CONFIRMED', '2026-10-15 23:20:00'
       UNION ALL SELECT (@cx_sm + 44), (@cx_p + 256), 'B_WIN', 'CONFIRMED', '2026-10-15 23:10:00'
       UNION ALL SELECT (@cx_sm + 45), (@cx_p + 257), 'B_WIN', 'CONFIRMED', '2026-10-16 10:26:00'
       UNION ALL SELECT (@cx_sm + 45), (@cx_p + 259), 'B_WIN', 'CONFIRMED', '2026-10-16 10:15:00'
       UNION ALL SELECT (@cx_sm + 46), (@cx_p + 262), 'B_WIN', 'CONFIRMED', '2026-10-16 23:25:00'
       UNION ALL SELECT (@cx_sm + 46), (@cx_p + 263), 'B_WIN', 'CONFIRMED', '2026-10-17 00:30:00'
       UNION ALL SELECT (@cx_sm + 47), (@cx_p + 265), 'A_WIN', 'CONFIRMED', '2026-10-17 11:42:00'
       UNION ALL SELECT (@cx_sm + 47), (@cx_p + 268), 'A_WIN', 'CONFIRMED', '2026-10-17 13:32:00'
       UNION ALL SELECT (@cx_sm + 48), (@cx_p + 271), 'B_WIN', 'CONFIRMED', '2026-10-17 17:17:00'
       UNION ALL SELECT (@cx_sm + 48), (@cx_p + 280), 'B_WIN', 'CONFIRMED', '2026-10-17 17:48:00'
       UNION ALL SELECT (@cx_sm + 49), (@cx_p + 286), 'A_WIN', 'CONFIRMED', '2026-10-17 21:30:00'
       UNION ALL SELECT (@cx_sm + 49), (@cx_p + 282), 'A_WIN', 'CONFIRMED', '2026-10-17 22:38:00'
       UNION ALL SELECT (@cx_sm + 50), (@cx_p + 289), 'B_WIN', 'CONFIRMED', '2026-10-18 10:37:00'
       UNION ALL SELECT (@cx_sm + 50), (@cx_p + 291), 'B_WIN', 'CONFIRMED', '2026-10-18 10:58:00'
       UNION ALL SELECT (@cx_sm + 51), (@cx_p + 293), 'B_WIN', 'CONFIRMED', '2026-10-18 12:36:00'
       UNION ALL SELECT (@cx_sm + 51), (@cx_p + 295), 'B_WIN', 'CONFIRMED', '2026-10-18 12:37:00'
       UNION ALL SELECT (@cx_sm + 52), (@cx_p + 300), 'A_WIN', 'CONFIRMED', '2026-10-20 23:56:00'
       UNION ALL SELECT (@cx_sm + 52), (@cx_p + 297), 'A_WIN', 'CONFIRMED', '2026-10-21 01:59:00'
       UNION ALL SELECT (@cx_sm + 53), (@cx_p + 305), 'A_WIN', 'CONFIRMED', '2026-10-21 21:27:00'
       UNION ALL SELECT (@cx_sm + 53), (@cx_p + 302), 'A_WIN', 'CONFIRMED', '2026-10-21 22:37:00'
       UNION ALL SELECT (@cx_sm + 54), (@cx_p + 315), 'B_WIN', 'CONFIRMED', '2026-10-21 23:08:00'
       UNION ALL SELECT (@cx_sm + 54), (@cx_p + 310), 'B_WIN', 'CONFIRMED', '2026-10-21 22:39:00'
       UNION ALL SELECT (@cx_sm + 55), (@cx_p + 320), 'A_WIN', 'CONFIRMED', '2026-10-23 21:27:00'
       UNION ALL SELECT (@cx_sm + 55), (@cx_p + 322), 'A_WIN', 'CONFIRMED', '2026-10-23 20:33:00'
       UNION ALL SELECT (@cx_sm + 56), (@cx_p + 325), 'B_WIN', 'CONFIRMED', '2026-10-24 11:57:00'
       UNION ALL SELECT (@cx_sm + 56), (@cx_p + 324), 'B_WIN', 'CONFIRMED', '2026-10-24 12:13:00'
       UNION ALL SELECT (@cx_sm + 57), (@cx_p + 327), 'B_WIN', 'CONFIRMED', '2026-10-24 12:31:00'
       UNION ALL SELECT (@cx_sm + 57), (@cx_p + 336), 'B_WIN', 'CONFIRMED', '2026-10-24 12:30:00'
       UNION ALL SELECT (@cx_sm + 58), (@cx_p + 337), 'B_WIN', 'CONFIRMED', '2026-10-24 12:48:00'
       UNION ALL SELECT (@cx_sm + 58), (@cx_p + 340), 'B_WIN', 'CONFIRMED', '2026-10-24 14:23:00'
       UNION ALL SELECT (@cx_sm + 59), (@cx_p + 346), 'A_WIN', 'CONFIRMED', '2026-10-24 12:54:00'
       UNION ALL SELECT (@cx_sm + 59), (@cx_p + 343), 'A_WIN', 'CONFIRMED', '2026-10-24 13:53:00'
       UNION ALL SELECT (@cx_sm + 60), (@cx_p + 347), 'A_WIN', 'CONFIRMED', '2026-10-24 20:58:00'
       UNION ALL SELECT (@cx_sm + 60), (@cx_p + 350), 'A_WIN', 'CONFIRMED', '2026-10-24 20:45:00'
       UNION ALL SELECT (@cx_sm + 61), (@cx_p + 356), 'B_WIN', 'CONFIRMED', '2026-10-25 12:30:00'
       UNION ALL SELECT (@cx_sm + 61), (@cx_p + 357), 'B_WIN', 'CONFIRMED', '2026-10-25 13:05:00'
       UNION ALL SELECT (@cx_sm + 62), (@cx_p + 364), 'B_WIN', 'CONFIRMED', '2026-10-27 20:45:00'
       UNION ALL SELECT (@cx_sm + 62), (@cx_p + 365), 'B_WIN', 'CONFIRMED', '2026-10-27 21:38:00'
       UNION ALL SELECT (@cx_sm + 63), (@cx_p + 368), 'B_WIN', 'CONFIRMED', '2026-10-28 22:50:00'
       UNION ALL SELECT (@cx_sm + 63), (@cx_p + 370), 'B_WIN', 'CONFIRMED', '2026-10-28 22:43:00'
       UNION ALL SELECT (@cx_sm + 64), (@cx_p + 377), 'B_WIN', 'CONFIRMED', '2026-10-29 21:56:00'
       UNION ALL SELECT (@cx_sm + 64), (@cx_p + 371), 'B_WIN', 'CONFIRMED', '2026-10-29 23:19:00'
       UNION ALL SELECT (@cx_sm + 65), (@cx_p + 381), 'DRAW', 'CONFIRMED', '2026-10-31 11:39:00'
       UNION ALL SELECT (@cx_sm + 65), (@cx_p + 386), 'DRAW', 'CONFIRMED', '2026-10-31 12:40:00'
       UNION ALL SELECT (@cx_sm + 66), (@cx_p + 387), 'B_WIN', 'CONFIRMED', '2026-10-31 21:07:00'
       UNION ALL SELECT (@cx_sm + 66), (@cx_p + 391), 'B_WIN', 'CONFIRMED', '2026-10-31 21:47:00'
       UNION ALL SELECT (@cx_sm + 67), (@cx_p + 396), 'B_WIN', 'CONFIRMED', '2026-11-05 22:16:00'
       UNION ALL SELECT (@cx_sm + 67), (@cx_p + 397), 'B_WIN', 'CONFIRMED', '2026-11-05 23:13:00'
       UNION ALL SELECT (@cx_sm + 68), (@cx_p + 400), 'A_WIN', 'CONFIRMED', '2026-11-06 20:38:00'
       UNION ALL SELECT (@cx_sm + 68), (@cx_p + 402), 'A_WIN', 'CONFIRMED', '2026-11-06 22:03:00'
       UNION ALL SELECT (@cx_sm + 69), (@cx_p + 405), 'A_WIN', 'CONFIRMED', '2026-11-07 12:43:00'
       UNION ALL SELECT (@cx_sm + 69), (@cx_p + 409), 'A_WIN', 'CONFIRMED', '2026-11-07 14:53:00'
       UNION ALL SELECT (@cx_sm + 70), (@cx_p + 414), 'A_WIN', 'CONFIRMED', '2026-11-07 13:19:00'
       UNION ALL SELECT (@cx_sm + 70), (@cx_p + 411), 'A_WIN', 'CONFIRMED', '2026-11-07 13:23:00'
       UNION ALL SELECT (@cx_sm + 71), (@cx_p + 417), 'DRAW', 'CONFIRMED', '2026-11-07 17:12:00'
       UNION ALL SELECT (@cx_sm + 71), (@cx_p + 425), 'DRAW', 'CONFIRMED', '2026-11-07 17:16:00'
) v
 WHERE @cx_ready = 1;

-- 7. 레이팅 이력 (평가 없이 반영: evaluation_delta 0, evaluation_score NULL)
INSERT INTO sport_rating_history (match_id, participant_id, user_id, sport_id, mmr_before, mmr_after, elo_delta, evaluation_delta, final_delta, evaluation_score, actual_score, expected_score, team_avg_mmr_a, team_avg_mmr_b, algorithm_version, calculated_at)
SELECT * FROM (
                 SELECT (@cx_sm + 1) AS match_id, (@cx_p + 2) AS participant_id, (@cx_u + 31) AS user_id, @cx_s_농구 AS sport_id, 1000.00 AS mmr_before, 968.00 AS mmr_after, -32.00 AS elo_delta, 0.00 AS evaluation_delta, -32.00 AS final_delta, NULL AS evaluation_score, 0.0 AS actual_score, 0.5000 AS expected_score, 1000.00 AS team_avg_mmr_a, 1000.00 AS team_avg_mmr_b, 'ELO-K32-V2' AS algorithm_version, '2026-08-02 12:00:00' AS calculated_at
       UNION ALL SELECT (@cx_sm + 1), (@cx_p + 3), (@cx_u + 32), @cx_s_농구, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 12:00:00'
       UNION ALL SELECT (@cx_sm + 1), (@cx_p + 1), (@cx_u + 33), @cx_s_농구, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 12:00:00'
       UNION ALL SELECT (@cx_sm + 1), (@cx_p + 4), (@cx_u + 34), @cx_s_농구, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 12:00:00'
       UNION ALL SELECT (@cx_sm + 1), (@cx_p + 7), (@cx_u + 56), @cx_s_농구, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 12:00:00'
       UNION ALL SELECT (@cx_sm + 1), (@cx_p + 5), (@cx_u + 57), @cx_s_농구, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 12:00:00'
       UNION ALL SELECT (@cx_sm + 1), (@cx_p + 6), (@cx_u + 60), @cx_s_농구, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 12:00:00'
       UNION ALL SELECT (@cx_sm + 1), (@cx_p + 8), (@cx_u + 61), @cx_s_농구, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-02 12:00:00'
       UNION ALL SELECT (@cx_sm + 2), (@cx_p + 9), (@cx_u + 48), @cx_s_테니스, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-03 10:00:00'
       UNION ALL SELECT (@cx_sm + 2), (@cx_p + 10), (@cx_u + 51), @cx_s_테니스, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-03 10:00:00'
       UNION ALL SELECT (@cx_sm + 2), (@cx_p + 11), (@cx_u + 66), @cx_s_테니스, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-03 10:00:00'
       UNION ALL SELECT (@cx_sm + 2), (@cx_p + 12), (@cx_u + 68), @cx_s_테니스, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-03 10:00:00'
       UNION ALL SELECT (@cx_sm + 3), (@cx_p + 15), (@cx_u + 19), @cx_s_배드민턴, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-03 11:00:00'
       UNION ALL SELECT (@cx_sm + 3), (@cx_p + 16), (@cx_u + 22), @cx_s_배드민턴, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-03 11:00:00'
       UNION ALL SELECT (@cx_sm + 3), (@cx_p + 14), (@cx_u + 70), @cx_s_배드민턴, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-03 11:00:00'
       UNION ALL SELECT (@cx_sm + 3), (@cx_p + 13), (@cx_u + 73), @cx_s_배드민턴, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-03 11:00:00'
       UNION ALL SELECT (@cx_sm + 4), (@cx_p + 17), (@cx_u + 15), @cx_s_배드민턴, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-05 22:00:00'
       UNION ALL SELECT (@cx_sm + 4), (@cx_p + 18), (@cx_u + 18), @cx_s_배드민턴, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-05 22:00:00'
       UNION ALL SELECT (@cx_sm + 4), (@cx_p + 19), (@cx_u + 52), @cx_s_배드민턴, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-05 22:00:00'
       UNION ALL SELECT (@cx_sm + 4), (@cx_p + 20), (@cx_u + 55), @cx_s_배드민턴, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-05 22:00:00'
       UNION ALL SELECT (@cx_sm + 5), (@cx_p + 21), (@cx_u + 7), @cx_s_테니스, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-08 09:00:00'
       UNION ALL SELECT (@cx_sm + 5), (@cx_p + 22), (@cx_u + 8), @cx_s_테니스, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-08 09:00:00'
       UNION ALL SELECT (@cx_sm + 5), (@cx_p + 23), (@cx_u + 23), @cx_s_테니스, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-08 09:00:00'
       UNION ALL SELECT (@cx_sm + 5), (@cx_p + 24), (@cx_u + 24), @cx_s_테니스, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-08-08 09:00:00'
       UNION ALL SELECT (@cx_sm + 6), (@cx_p + 28), (@cx_u + 1), @cx_s_농구, 1000.00, 1030.53, 30.53, 0.00, 30.53, NULL, 1.0, 0.5230, 1000.00, 984.00, 'ELO-K32-V2', '2026-08-10 11:00:00'
       UNION ALL SELECT (@cx_sm + 6), (@cx_p + 27), (@cx_u + 2), @cx_s_농구, 1000.00, 1030.53, 30.53, 0.00, 30.53, NULL, 1.0, 0.5230, 1000.00, 984.00, 'ELO-K32-V2', '2026-08-10 11:00:00'
       UNION ALL SELECT (@cx_sm + 6), (@cx_p + 26), (@cx_u + 4), @cx_s_농구, 1000.00, 1030.53, 30.53, 0.00, 30.53, NULL, 1.0, 0.5230, 1000.00, 984.00, 'ELO-K32-V2', '2026-08-10 11:00:00'
       UNION ALL SELECT (@cx_sm + 6), (@cx_p + 25), (@cx_u + 6), @cx_s_농구, 1000.00, 1030.53, 30.53, 0.00, 30.53, NULL, 1.0, 0.5230, 1000.00, 984.00, 'ELO-K32-V2', '2026-08-10 11:00:00'
       UNION ALL SELECT (@cx_sm + 6), (@cx_p + 29), (@cx_u + 31), @cx_s_농구, 968.00, 937.47, -30.53, 0.00, -30.53, NULL, 0.0, 0.4770, 1000.00, 984.00, 'ELO-K32-V2', '2026-08-10 11:00:00'
       UNION ALL SELECT (@cx_sm + 6), (@cx_p + 32), (@cx_u + 32), @cx_s_농구, 968.00, 937.47, -30.53, 0.00, -30.53, NULL, 0.0, 0.4770, 1000.00, 984.00, 'ELO-K32-V2', '2026-08-10 11:00:00'
       UNION ALL SELECT (@cx_sm + 6), (@cx_p + 31), (@cx_u + 35), @cx_s_농구, 1000.00, 969.47, -30.53, 0.00, -30.53, NULL, 0.0, 0.4770, 1000.00, 984.00, 'ELO-K32-V2', '2026-08-10 11:00:00'
       UNION ALL SELECT (@cx_sm + 6), (@cx_p + 30), (@cx_u + 36), @cx_s_농구, 1000.00, 969.47, -30.53, 0.00, -30.53, NULL, 0.0, 0.4770, 1000.00, 984.00, 'ELO-K32-V2', '2026-08-10 11:00:00'
       UNION ALL SELECT (@cx_sm + 7), (@cx_p + 36), (@cx_u + 11), @cx_s_테니스, 1000.00, 966.53, -33.47, 0.00, -33.47, NULL, 0.0, 0.5230, 1000.00, 984.00, 'ELO-K32-V2', '2026-08-12 23:00:00'
       UNION ALL SELECT (@cx_sm + 7), (@cx_p + 35), (@cx_u + 13), @cx_s_테니스, 1000.00, 966.53, -33.47, 0.00, -33.47, NULL, 0.0, 0.5230, 1000.00, 984.00, 'ELO-K32-V2', '2026-08-12 23:00:00'
       UNION ALL SELECT (@cx_sm + 7), (@cx_p + 33), (@cx_u + 48), @cx_s_테니스, 968.00, 1001.47, 33.47, 0.00, 33.47, NULL, 1.0, 0.4770, 1000.00, 984.00, 'ELO-K32-V2', '2026-08-12 23:00:00'
       UNION ALL SELECT (@cx_sm + 7), (@cx_p + 34), (@cx_u + 50), @cx_s_테니스, 1000.00, 1033.47, 33.47, 0.00, 33.47, NULL, 1.0, 0.4770, 1000.00, 984.00, 'ELO-K32-V2', '2026-08-12 23:00:00'
       UNION ALL SELECT (@cx_sm + 8), (@cx_p + 39), (@cx_u + 23), @cx_s_테니스, 968.00, 969.47, 1.47, 0.00, 1.47, NULL, 0.5, 0.4770, 984.00, 1000.00, 'ELO-K32-V2', '2026-08-14 21:30:00'
       UNION ALL SELECT (@cx_sm + 8), (@cx_p + 40), (@cx_u + 26), @cx_s_테니스, 1000.00, 1001.47, 1.47, 0.00, 1.47, NULL, 0.5, 0.4770, 984.00, 1000.00, 'ELO-K32-V2', '2026-08-14 21:30:00'
       UNION ALL SELECT (@cx_sm + 8), (@cx_p + 37), (@cx_u + 27), @cx_s_테니스, 1000.00, 998.53, -1.47, 0.00, -1.47, NULL, 0.5, 0.5230, 984.00, 1000.00, 'ELO-K32-V2', '2026-08-14 21:30:00'
       UNION ALL SELECT (@cx_sm + 8), (@cx_p + 38), (@cx_u + 30), @cx_s_테니스, 1000.00, 998.53, -1.47, 0.00, -1.47, NULL, 0.5, 0.5230, 984.00, 1000.00, 'ELO-K32-V2', '2026-08-14 21:30:00'
       UNION ALL SELECT (@cx_sm + 9), (@cx_p + 43), (@cx_u + 27), @cx_s_테니스, 998.53, 968.07, -30.46, 0.00, -30.46, NULL, 0.0, 0.4759, 1016.00, 999.27, 'ELO-K32-V2', '2026-08-20 22:00:00'
       UNION ALL SELECT (@cx_sm + 9), (@cx_p + 44), (@cx_u + 28), @cx_s_테니스, 1000.00, 969.54, -30.46, 0.00, -30.46, NULL, 0.0, 0.4759, 1016.00, 999.27, 'ELO-K32-V2', '2026-08-20 22:00:00'
       UNION ALL SELECT (@cx_sm + 9), (@cx_p + 42), (@cx_u + 66), @cx_s_테니스, 1032.00, 1062.46, 30.46, 0.00, 30.46, NULL, 1.0, 0.5241, 1016.00, 999.27, 'ELO-K32-V2', '2026-08-20 22:00:00'
       UNION ALL SELECT (@cx_sm + 9), (@cx_p + 41), (@cx_u + 67), @cx_s_테니스, 1000.00, 1030.46, 30.46, 0.00, 30.46, NULL, 1.0, 0.5241, 1016.00, 999.27, 'ELO-K32-V2', '2026-08-20 22:00:00'
       UNION ALL SELECT (@cx_sm + 10), (@cx_p + 47), (@cx_u + 7), @cx_s_테니스, 1032.00, 1060.99, 28.99, 0.00, 28.99, NULL, 1.0, 0.5470, 983.27, 1016.00, 'ELO-K32-V2', '2026-08-21 23:00:00'
       UNION ALL SELECT (@cx_sm + 10), (@cx_p + 48), (@cx_u + 10), @cx_s_테니스, 1000.00, 1028.99, 28.99, 0.00, 28.99, NULL, 1.0, 0.5470, 983.27, 1016.00, 'ELO-K32-V2', '2026-08-21 23:00:00'
       UNION ALL SELECT (@cx_sm + 10), (@cx_p + 45), (@cx_u + 11), @cx_s_테니스, 966.53, 937.54, -28.99, 0.00, -28.99, NULL, 0.0, 0.4530, 983.27, 1016.00, 'ELO-K32-V2', '2026-08-21 23:00:00'
       UNION ALL SELECT (@cx_sm + 10), (@cx_p + 46), (@cx_u + 14), @cx_s_테니스, 1000.00, 971.01, -28.99, 0.00, -28.99, NULL, 0.0, 0.4530, 983.27, 1016.00, 'ELO-K32-V2', '2026-08-21 23:00:00'
       UNION ALL SELECT (@cx_sm + 11), (@cx_p + 51), (@cx_u + 15), @cx_s_배드민턴, 1032.00, 1064.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1016.00, 1016.00, 'ELO-K32-V2', '2026-08-22 22:00:00'
       UNION ALL SELECT (@cx_sm + 11), (@cx_p + 52), (@cx_u + 17), @cx_s_배드민턴, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1016.00, 1016.00, 'ELO-K32-V2', '2026-08-22 22:00:00'
       UNION ALL SELECT (@cx_sm + 11), (@cx_p + 49), (@cx_u + 19), @cx_s_배드민턴, 1032.00, 1000.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1016.00, 1016.00, 'ELO-K32-V2', '2026-08-22 22:00:00'
       UNION ALL SELECT (@cx_sm + 11), (@cx_p + 50), (@cx_u + 21), @cx_s_배드민턴, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1016.00, 1016.00, 'ELO-K32-V2', '2026-08-22 22:00:00'
       UNION ALL SELECT (@cx_sm + 12), (@cx_p + 60), (@cx_u + 1), @cx_s_농구, 1030.53, 998.60, -31.93, 0.00, -31.93, NULL, 0.0, 0.4989, 1015.27, 1016.00, 'ELO-K32-V2', '2026-08-24 11:00:00'
       UNION ALL SELECT (@cx_sm + 12), (@cx_p + 57), (@cx_u + 2), @cx_s_농구, 1030.53, 998.60, -31.93, 0.00, -31.93, NULL, 0.0, 0.4989, 1015.27, 1016.00, 'ELO-K32-V2', '2026-08-24 11:00:00'
       UNION ALL SELECT (@cx_sm + 12), (@cx_p + 59), (@cx_u + 3), @cx_s_농구, 1000.00, 968.07, -31.93, 0.00, -31.93, NULL, 0.0, 0.4989, 1015.27, 1016.00, 'ELO-K32-V2', '2026-08-24 11:00:00'
       UNION ALL SELECT (@cx_sm + 12), (@cx_p + 58), (@cx_u + 5), @cx_s_농구, 1000.00, 968.07, -31.93, 0.00, -31.93, NULL, 0.0, 0.4989, 1015.27, 1016.00, 'ELO-K32-V2', '2026-08-24 11:00:00'
       UNION ALL SELECT (@cx_sm + 12), (@cx_p + 54), (@cx_u + 56), @cx_s_농구, 1032.00, 1063.93, 31.93, 0.00, 31.93, NULL, 1.0, 0.5011, 1015.27, 1016.00, 'ELO-K32-V2', '2026-08-24 11:00:00'
       UNION ALL SELECT (@cx_sm + 12), (@cx_p + 56), (@cx_u + 57), @cx_s_농구, 1032.00, 1063.93, 31.93, 0.00, 31.93, NULL, 1.0, 0.5011, 1015.27, 1016.00, 'ELO-K32-V2', '2026-08-24 11:00:00'
       UNION ALL SELECT (@cx_sm + 12), (@cx_p + 55), (@cx_u + 58), @cx_s_농구, 1000.00, 1031.93, 31.93, 0.00, 31.93, NULL, 1.0, 0.5011, 1015.27, 1016.00, 'ELO-K32-V2', '2026-08-24 11:00:00'
       UNION ALL SELECT (@cx_sm + 12), (@cx_p + 53), (@cx_u + 59), @cx_s_농구, 1000.00, 1031.93, 31.93, 0.00, 31.93, NULL, 1.0, 0.5011, 1015.27, 1016.00, 'ELO-K32-V2', '2026-08-24 11:00:00'
       UNION ALL SELECT (@cx_sm + 13), (@cx_p + 64), (@cx_u + 7), @cx_s_테니스, 1060.99, 1088.80, 27.81, 0.00, 27.81, NULL, 1.0, 0.5655, 984.74, 1030.50, 'ELO-K32-V2', '2026-08-28 21:30:00'
       UNION ALL SELECT (@cx_sm + 13), (@cx_p + 63), (@cx_u + 9), @cx_s_테니스, 1000.00, 1027.81, 27.81, 0.00, 27.81, NULL, 1.0, 0.5655, 984.74, 1030.50, 'ELO-K32-V2', '2026-08-28 21:30:00'
       UNION ALL SELECT (@cx_sm + 13), (@cx_p + 61), (@cx_u + 23), @cx_s_테니스, 969.47, 941.66, -27.81, 0.00, -27.81, NULL, 0.0, 0.4345, 984.74, 1030.50, 'ELO-K32-V2', '2026-08-28 21:30:00'
       UNION ALL SELECT (@cx_sm + 13), (@cx_p + 62), (@cx_u + 25), @cx_s_테니스, 1000.00, 972.19, -27.81, 0.00, -27.81, NULL, 0.0, 0.4345, 984.74, 1030.50, 'ELO-K32-V2', '2026-08-28 21:30:00'
       UNION ALL SELECT (@cx_sm + 14), (@cx_p + 65), (@cx_u + 19), @cx_s_배드민턴, 1000.00, 1030.53, 30.53, 0.00, 30.53, NULL, 1.0, 0.5230, 1000.00, 984.00, 'ELO-K32-V2', '2026-08-29 22:00:00'
       UNION ALL SELECT (@cx_sm + 14), (@cx_p + 66), (@cx_u + 20), @cx_s_배드민턴, 1000.00, 1030.53, 30.53, 0.00, 30.53, NULL, 1.0, 0.5230, 1000.00, 984.00, 'ELO-K32-V2', '2026-08-29 22:00:00'
       UNION ALL SELECT (@cx_sm + 14), (@cx_p + 67), (@cx_u + 52), @cx_s_배드민턴, 968.00, 937.47, -30.53, 0.00, -30.53, NULL, 0.0, 0.4770, 1000.00, 984.00, 'ELO-K32-V2', '2026-08-29 22:00:00'
       UNION ALL SELECT (@cx_sm + 14), (@cx_p + 68), (@cx_u + 53), @cx_s_배드민턴, 1000.00, 969.47, -30.53, 0.00, -30.53, NULL, 0.0, 0.4770, 1000.00, 984.00, 'ELO-K32-V2', '2026-08-29 22:00:00'
       UNION ALL SELECT (@cx_sm + 15), (@cx_p + 72), (@cx_u + 27), @cx_s_테니스, 968.07, 937.61, -30.46, 0.00, -30.46, NULL, 0.0, 0.4760, 1000.74, 984.04, 'ELO-K32-V2', '2026-08-30 10:00:00'
       UNION ALL SELECT (@cx_sm + 15), (@cx_p + 71), (@cx_u + 29), @cx_s_테니스, 1000.00, 969.54, -30.46, 0.00, -30.46, NULL, 0.0, 0.4760, 1000.74, 984.04, 'ELO-K32-V2', '2026-08-30 10:00:00'
       UNION ALL SELECT (@cx_sm + 15), (@cx_p + 70), (@cx_u + 48), @cx_s_테니스, 1001.47, 1031.93, 30.46, 0.00, 30.46, NULL, 1.0, 0.5240, 1000.74, 984.04, 'ELO-K32-V2', '2026-08-30 10:00:00'
       UNION ALL SELECT (@cx_sm + 15), (@cx_p + 69), (@cx_u + 49), @cx_s_테니스, 1000.00, 1030.46, 30.46, 0.00, 30.46, NULL, 1.0, 0.5240, 1000.74, 984.04, 'ELO-K32-V2', '2026-08-30 10:00:00'
       UNION ALL SELECT (@cx_sm + 16), (@cx_p + 78), (@cx_u + 31), @cx_s_농구, 937.47, 913.96, -23.51, 0.00, -23.51, NULL, 0.0, 0.3673, 953.47, 1047.95, 'ELO-K32-V2', '2026-08-30 12:00:00'
       UNION ALL SELECT (@cx_sm + 16), (@cx_p + 77), (@cx_u + 32), @cx_s_농구, 937.47, 913.96, -23.51, 0.00, -23.51, NULL, 0.0, 0.3673, 953.47, 1047.95, 'ELO-K32-V2', '2026-08-30 12:00:00'
       UNION ALL SELECT (@cx_sm + 16), (@cx_p + 79), (@cx_u + 35), @cx_s_농구, 969.47, 945.96, -23.51, 0.00, -23.51, NULL, 0.0, 0.3673, 953.47, 1047.95, 'ELO-K32-V2', '2026-08-30 12:00:00'
       UNION ALL SELECT (@cx_sm + 16), (@cx_p + 80), (@cx_u + 36), @cx_s_농구, 969.47, 945.96, -23.51, 0.00, -23.51, NULL, 0.0, 0.3673, 953.47, 1047.95, 'ELO-K32-V2', '2026-08-30 12:00:00'
       UNION ALL SELECT (@cx_sm + 16), (@cx_p + 73), (@cx_u + 56), @cx_s_농구, 1063.93, 1087.44, 23.51, 0.00, 23.51, NULL, 1.0, 0.6327, 953.47, 1047.95, 'ELO-K32-V2', '2026-08-30 12:00:00'
       UNION ALL SELECT (@cx_sm + 16), (@cx_p + 75), (@cx_u + 57), @cx_s_농구, 1063.93, 1087.44, 23.51, 0.00, 23.51, NULL, 1.0, 0.6327, 953.47, 1047.95, 'ELO-K32-V2', '2026-08-30 12:00:00'
       UNION ALL SELECT (@cx_sm + 16), (@cx_p + 76), (@cx_u + 58), @cx_s_농구, 1031.93, 1055.44, 23.51, 0.00, 23.51, NULL, 1.0, 0.6327, 953.47, 1047.95, 'ELO-K32-V2', '2026-08-30 12:00:00'
       UNION ALL SELECT (@cx_sm + 16), (@cx_p + 74), (@cx_u + 61), @cx_s_농구, 1032.00, 1055.51, 23.51, 0.00, 23.51, NULL, 1.0, 0.6327, 953.47, 1047.95, 'ELO-K32-V2', '2026-08-30 12:00:00'
       UNION ALL SELECT (@cx_sm + 17), (@cx_p + 82), (@cx_u + 37), @cx_s_배드민턴, 1000.00, 1029.13, 29.13, 0.00, 29.13, NULL, 1.0, 0.5449, 968.74, 1000.00, 'ELO-K32-V2', '2026-09-02 22:00:00'
       UNION ALL SELECT (@cx_sm + 17), (@cx_p + 81), (@cx_u + 38), @cx_s_배드민턴, 1000.00, 1029.13, 29.13, 0.00, 29.13, NULL, 1.0, 0.5449, 968.74, 1000.00, 'ELO-K32-V2', '2026-09-02 22:00:00'
       UNION ALL SELECT (@cx_sm + 17), (@cx_p + 84), (@cx_u + 52), @cx_s_배드민턴, 937.47, 908.34, -29.13, 0.00, -29.13, NULL, 0.0, 0.4551, 968.74, 1000.00, 'ELO-K32-V2', '2026-09-02 22:00:00'
       UNION ALL SELECT (@cx_sm + 17), (@cx_p + 83), (@cx_u + 54), @cx_s_배드민턴, 1000.00, 970.87, -29.13, 0.00, -29.13, NULL, 0.0, 0.4551, 968.74, 1000.00, 'ELO-K32-V2', '2026-09-02 22:00:00'
       UNION ALL SELECT (@cx_sm + 18), (@cx_p + 87), (@cx_u + 11), @cx_s_테니스, 937.54, 975.23, 37.69, 0.00, 37.69, NULL, 1.0, 0.4111, 968.77, 1031.23, 'ELO-K32-V2', '2026-09-04 23:00:00'
       UNION ALL SELECT (@cx_sm + 18), (@cx_p + 88), (@cx_u + 12), @cx_s_테니스, 1000.00, 1037.69, 37.69, 0.00, 37.69, NULL, 1.0, 0.4111, 968.77, 1031.23, 'ELO-K32-V2', '2026-09-04 23:00:00'
       UNION ALL SELECT (@cx_sm + 18), (@cx_p + 86), (@cx_u + 66), @cx_s_테니스, 1062.46, 1024.77, -37.69, 0.00, -37.69, NULL, 0.0, 0.5889, 968.77, 1031.23, 'ELO-K32-V2', '2026-09-04 23:00:00'
       UNION ALL SELECT (@cx_sm + 18), (@cx_p + 85), (@cx_u + 69), @cx_s_테니스, 1000.00, 962.31, -37.69, 0.00, -37.69, NULL, 0.0, 0.5889, 968.77, 1031.23, 'ELO-K32-V2', '2026-09-04 23:00:00'
       UNION ALL SELECT (@cx_sm + 19), (@cx_p + 89), (@cx_u + 7), @cx_s_테니스, 1088.80, 1111.39, 22.59, 0.00, 22.59, NULL, 1.0, 0.6471, 953.58, 1058.90, 'ELO-K32-V2', '2026-09-12 22:00:00'
       UNION ALL SELECT (@cx_sm + 19), (@cx_p + 90), (@cx_u + 10), @cx_s_테니스, 1028.99, 1051.58, 22.59, 0.00, 22.59, NULL, 1.0, 0.6471, 953.58, 1058.90, 'ELO-K32-V2', '2026-09-12 22:00:00'
       UNION ALL SELECT (@cx_sm + 19), (@cx_p + 91), (@cx_u + 27), @cx_s_테니스, 937.61, 915.02, -22.59, 0.00, -22.59, NULL, 0.0, 0.3529, 953.58, 1058.90, 'ELO-K32-V2', '2026-09-12 22:00:00'
       UNION ALL SELECT (@cx_sm + 19), (@cx_p + 92), (@cx_u + 28), @cx_s_테니스, 969.54, 946.95, -22.59, 0.00, -22.59, NULL, 0.0, 0.3529, 953.58, 1058.90, 'ELO-K32-V2', '2026-09-12 22:00:00'
       UNION ALL SELECT (@cx_sm + 20), (@cx_p + 95), (@cx_u + 52), @cx_s_배드민턴, 908.34, 944.47, 36.13, 0.00, 36.13, NULL, 1.0, 0.4355, 984.00, 938.91, 'ELO-K32-V2', '2026-09-14 11:00:00'
       UNION ALL SELECT (@cx_sm + 20), (@cx_p + 96), (@cx_u + 53), @cx_s_배드민턴, 969.47, 1005.60, 36.13, 0.00, 36.13, NULL, 1.0, 0.4355, 984.00, 938.91, 'ELO-K32-V2', '2026-09-14 11:00:00'
       UNION ALL SELECT (@cx_sm + 20), (@cx_p + 94), (@cx_u + 70), @cx_s_배드민턴, 968.00, 931.87, -36.13, 0.00, -36.13, NULL, 0.0, 0.5645, 984.00, 938.91, 'ELO-K32-V2', '2026-09-14 11:00:00'
       UNION ALL SELECT (@cx_sm + 20), (@cx_p + 93), (@cx_u + 71), @cx_s_배드민턴, 1000.00, 963.87, -36.13, 0.00, -36.13, NULL, 0.0, 0.5645, 984.00, 938.91, 'ELO-K32-V2', '2026-09-14 11:00:00'
       UNION ALL SELECT (@cx_sm + 21), (@cx_p + 99), (@cx_u + 19), @cx_s_배드민턴, 1030.53, 996.99, -33.54, 0.00, -33.54, NULL, 0.0, 0.5240, 1031.27, 1014.57, 'ELO-K32-V2', '2026-09-16 22:00:00'
       UNION ALL SELECT (@cx_sm + 21), (@cx_p + 100), (@cx_u + 22), @cx_s_배드민턴, 1032.00, 998.46, -33.54, 0.00, -33.54, NULL, 0.0, 0.5240, 1031.27, 1014.57, 'ELO-K32-V2', '2026-09-16 22:00:00'
       UNION ALL SELECT (@cx_sm + 21), (@cx_p + 97), (@cx_u + 37), @cx_s_배드민턴, 1029.13, 1062.67, 33.54, 0.00, 33.54, NULL, 1.0, 0.4760, 1031.27, 1014.57, 'ELO-K32-V2', '2026-09-16 22:00:00'
       UNION ALL SELECT (@cx_sm + 21), (@cx_p + 98), (@cx_u + 40), @cx_s_배드민턴, 1000.00, 1033.54, 33.54, 0.00, 33.54, NULL, 1.0, 0.4760, 1031.27, 1014.57, 'ELO-K32-V2', '2026-09-16 22:00:00'
       UNION ALL SELECT (@cx_sm + 22), (@cx_p + 101), (@cx_u + 48), @cx_s_테니스, 1031.93, 999.33, -32.60, 0.00, -32.60, NULL, 0.0, 0.5093, 999.97, 993.54, 'ELO-K32-V2', '2026-09-20 10:00:00'
       UNION ALL SELECT (@cx_sm + 22), (@cx_p + 102), (@cx_u + 51), @cx_s_테니스, 968.00, 935.40, -32.60, 0.00, -32.60, NULL, 0.0, 0.5093, 999.97, 993.54, 'ELO-K32-V2', '2026-09-20 10:00:00'
       UNION ALL SELECT (@cx_sm + 22), (@cx_p + 104), (@cx_u + 66), @cx_s_테니스, 1024.77, 1057.37, 32.60, 0.00, 32.60, NULL, 1.0, 0.4907, 999.97, 993.54, 'ELO-K32-V2', '2026-09-20 10:00:00'
       UNION ALL SELECT (@cx_sm + 22), (@cx_p + 103), (@cx_u + 69), @cx_s_테니스, 962.31, 994.91, 32.60, 0.00, 32.60, NULL, 1.0, 0.4907, 999.97, 993.54, 'ELO-K32-V2', '2026-09-20 10:00:00'
       UNION ALL SELECT (@cx_sm + 23), (@cx_p + 110), (@cx_u + 31), @cx_s_농구, 913.96, 892.49, -21.47, 0.00, -21.47, NULL, 0.0, 0.3355, 940.98, 1059.70, 'ELO-K32-V2', '2026-09-20 12:00:00'
       UNION ALL SELECT (@cx_sm + 23), (@cx_p + 112), (@cx_u + 32), @cx_s_농구, 913.96, 892.49, -21.47, 0.00, -21.47, NULL, 0.0, 0.3355, 940.98, 1059.70, 'ELO-K32-V2', '2026-09-20 12:00:00'
       UNION ALL SELECT (@cx_sm + 23), (@cx_p + 109), (@cx_u + 33), @cx_s_농구, 968.00, 946.53, -21.47, 0.00, -21.47, NULL, 0.0, 0.3355, 940.98, 1059.70, 'ELO-K32-V2', '2026-09-20 12:00:00'
       UNION ALL SELECT (@cx_sm + 23), (@cx_p + 111), (@cx_u + 34), @cx_s_농구, 968.00, 946.53, -21.47, 0.00, -21.47, NULL, 0.0, 0.3355, 940.98, 1059.70, 'ELO-K32-V2', '2026-09-20 12:00:00'
       UNION ALL SELECT (@cx_sm + 23), (@cx_p + 106), (@cx_u + 56), @cx_s_농구, 1087.44, 1108.91, 21.47, 0.00, 21.47, NULL, 1.0, 0.6645, 940.98, 1059.70, 'ELO-K32-V2', '2026-09-20 12:00:00'
       UNION ALL SELECT (@cx_sm + 23), (@cx_p + 107), (@cx_u + 57), @cx_s_농구, 1087.44, 1108.91, 21.47, 0.00, 21.47, NULL, 1.0, 0.6645, 940.98, 1059.70, 'ELO-K32-V2', '2026-09-20 12:00:00'
       UNION ALL SELECT (@cx_sm + 23), (@cx_p + 108), (@cx_u + 59), @cx_s_농구, 1031.93, 1053.40, 21.47, 0.00, 21.47, NULL, 1.0, 0.6645, 940.98, 1059.70, 'ELO-K32-V2', '2026-09-20 12:00:00'
       UNION ALL SELECT (@cx_sm + 23), (@cx_p + 105), (@cx_u + 60), @cx_s_농구, 1032.00, 1053.47, 21.47, 0.00, 21.47, NULL, 1.0, 0.6645, 940.98, 1059.70, 'ELO-K32-V2', '2026-09-20 12:00:00'
       UNION ALL SELECT (@cx_sm + 24), (@cx_p + 116), (@cx_u + 11), @cx_s_테니스, 975.23, 1002.51, 27.28, 0.00, 27.28, NULL, 1.0, 0.5738, 954.83, 1006.46, 'ELO-K32-V2', '2026-09-25 21:30:00'
       UNION ALL SELECT (@cx_sm + 24), (@cx_p + 115), (@cx_u + 12), @cx_s_테니스, 1037.69, 1064.97, 27.28, 0.00, 27.28, NULL, 1.0, 0.5738, 954.83, 1006.46, 'ELO-K32-V2', '2026-09-25 21:30:00'
       UNION ALL SELECT (@cx_sm + 24), (@cx_p + 113), (@cx_u + 23), @cx_s_테니스, 941.66, 914.38, -27.28, 0.00, -27.28, NULL, 0.0, 0.4262, 954.83, 1006.46, 'ELO-K32-V2', '2026-09-25 21:30:00'
       UNION ALL SELECT (@cx_sm + 24), (@cx_p + 114), (@cx_u + 24), @cx_s_테니스, 968.00, 940.72, -27.28, 0.00, -27.28, NULL, 0.0, 0.4262, 954.83, 1006.46, 'ELO-K32-V2', '2026-09-25 21:30:00'
       UNION ALL SELECT (@cx_sm + 25), (@cx_p + 121), (@cx_u + 41), @cx_s_축구, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-09-27 22:00:00'
       UNION ALL SELECT (@cx_sm + 25), (@cx_p + 118), (@cx_u + 42), @cx_s_축구, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-09-27 22:00:00'
       UNION ALL SELECT (@cx_sm + 25), (@cx_p + 120), (@cx_u + 43), @cx_s_축구, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-09-27 22:00:00'
       UNION ALL SELECT (@cx_sm + 25), (@cx_p + 119), (@cx_u + 46), @cx_s_축구, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-09-27 22:00:00'
       UNION ALL SELECT (@cx_sm + 25), (@cx_p + 117), (@cx_u + 47), @cx_s_축구, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-09-27 22:00:00'
       UNION ALL SELECT (@cx_sm + 25), (@cx_p + 125), (@cx_u + 74), @cx_s_축구, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-09-27 22:00:00'
       UNION ALL SELECT (@cx_sm + 25), (@cx_p + 123), (@cx_u + 75), @cx_s_축구, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-09-27 22:00:00'
       UNION ALL SELECT (@cx_sm + 25), (@cx_p + 124), (@cx_u + 76), @cx_s_축구, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-09-27 22:00:00'
       UNION ALL SELECT (@cx_sm + 25), (@cx_p + 122), (@cx_u + 77), @cx_s_축구, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-09-27 22:00:00'
       UNION ALL SELECT (@cx_sm + 25), (@cx_p + 126), (@cx_u + 80), @cx_s_축구, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-09-27 22:00:00'
       UNION ALL SELECT (@cx_sm + 26), (@cx_p + 134), (@cx_u + 74), @cx_s_축구, 1032.00, 998.23, -33.77, 0.00, -33.77, NULL, 0.0, 0.5276, 1000.00, 1019.20, 'ELO-K32-V2', '2026-09-30 21:00:00'
       UNION ALL SELECT (@cx_sm + 26), (@cx_p + 136), (@cx_u + 75), @cx_s_축구, 1032.00, 998.23, -33.77, 0.00, -33.77, NULL, 0.0, 0.5276, 1000.00, 1019.20, 'ELO-K32-V2', '2026-09-30 21:00:00'
       UNION ALL SELECT (@cx_sm + 26), (@cx_p + 135), (@cx_u + 76), @cx_s_축구, 1032.00, 998.23, -33.77, 0.00, -33.77, NULL, 0.0, 0.5276, 1000.00, 1019.20, 'ELO-K32-V2', '2026-09-30 21:00:00'
       UNION ALL SELECT (@cx_sm + 26), (@cx_p + 132), (@cx_u + 78), @cx_s_축구, 1000.00, 966.23, -33.77, 0.00, -33.77, NULL, 0.0, 0.5276, 1000.00, 1019.20, 'ELO-K32-V2', '2026-09-30 21:00:00'
       UNION ALL SELECT (@cx_sm + 26), (@cx_p + 133), (@cx_u + 79), @cx_s_축구, 1000.00, 966.23, -33.77, 0.00, -33.77, NULL, 0.0, 0.5276, 1000.00, 1019.20, 'ELO-K32-V2', '2026-09-30 21:00:00'
       UNION ALL SELECT (@cx_sm + 26), (@cx_p + 127), (@cx_u + 81), @cx_s_축구, 1000.00, 1033.77, 33.77, 0.00, 33.77, NULL, 1.0, 0.4724, 1000.00, 1019.20, 'ELO-K32-V2', '2026-09-30 21:00:00'
       UNION ALL SELECT (@cx_sm + 26), (@cx_p + 131), (@cx_u + 82), @cx_s_축구, 1000.00, 1033.77, 33.77, 0.00, 33.77, NULL, 1.0, 0.4724, 1000.00, 1019.20, 'ELO-K32-V2', '2026-09-30 21:00:00'
       UNION ALL SELECT (@cx_sm + 26), (@cx_p + 129), (@cx_u + 83), @cx_s_축구, 1000.00, 1033.77, 33.77, 0.00, 33.77, NULL, 1.0, 0.4724, 1000.00, 1019.20, 'ELO-K32-V2', '2026-09-30 21:00:00'
       UNION ALL SELECT (@cx_sm + 26), (@cx_p + 130), (@cx_u + 85), @cx_s_축구, 1000.00, 1033.77, 33.77, 0.00, 33.77, NULL, 1.0, 0.4724, 1000.00, 1019.20, 'ELO-K32-V2', '2026-09-30 21:00:00'
       UNION ALL SELECT (@cx_sm + 26), (@cx_p + 128), (@cx_u + 86), @cx_s_축구, 1000.00, 1033.77, 33.77, 0.00, 33.77, NULL, 1.0, 0.4724, 1000.00, 1019.20, 'ELO-K32-V2', '2026-09-30 21:00:00'
       UNION ALL SELECT (@cx_sm + 27), (@cx_p + 139), (@cx_u + 15), @cx_s_배드민턴, 1064.00, 1027.47, -36.53, 0.00, -36.53, NULL, 0.0, 0.5708, 982.50, 1032.00, 'ELO-K32-V2', '2026-09-30 22:00:00'
       UNION ALL SELECT (@cx_sm + 27), (@cx_p + 137), (@cx_u + 16), @cx_s_배드민턴, 1000.00, 963.47, -36.53, 0.00, -36.53, NULL, 0.0, 0.5708, 982.50, 1032.00, 'ELO-K32-V2', '2026-09-30 22:00:00'
       UNION ALL SELECT (@cx_sm + 27), (@cx_p + 140), (@cx_u + 19), @cx_s_배드민턴, 996.99, 1033.52, 36.53, 0.00, 36.53, NULL, 1.0, 0.4292, 982.50, 1032.00, 'ELO-K32-V2', '2026-09-30 22:00:00'
       UNION ALL SELECT (@cx_sm + 27), (@cx_p + 138), (@cx_u + 21), @cx_s_배드민턴, 968.00, 1004.53, 36.53, 0.00, 36.53, NULL, 1.0, 0.4292, 982.50, 1032.00, 'ELO-K32-V2', '2026-09-30 22:00:00'
       UNION ALL SELECT (@cx_sm + 28), (@cx_p + 148), (@cx_u + 41), @cx_s_축구, 968.00, 1001.77, 33.77, 0.00, 33.77, NULL, 1.0, 0.4724, 980.80, 1000.00, 'ELO-K32-V2', '2026-10-01 22:00:00'
       UNION ALL SELECT (@cx_sm + 28), (@cx_p + 149), (@cx_u + 42), @cx_s_축구, 968.00, 1001.77, 33.77, 0.00, 33.77, NULL, 1.0, 0.4724, 980.80, 1000.00, 'ELO-K32-V2', '2026-10-01 22:00:00'
       UNION ALL SELECT (@cx_sm + 28), (@cx_p + 147), (@cx_u + 43), @cx_s_축구, 968.00, 1001.77, 33.77, 0.00, 33.77, NULL, 1.0, 0.4724, 980.80, 1000.00, 'ELO-K32-V2', '2026-10-01 22:00:00'
       UNION ALL SELECT (@cx_sm + 28), (@cx_p + 150), (@cx_u + 44), @cx_s_축구, 1000.00, 1033.77, 33.77, 0.00, 33.77, NULL, 1.0, 0.4724, 980.80, 1000.00, 'ELO-K32-V2', '2026-10-01 22:00:00'
       UNION ALL SELECT (@cx_sm + 28), (@cx_p + 146), (@cx_u + 45), @cx_s_축구, 1000.00, 1033.77, 33.77, 0.00, 33.77, NULL, 1.0, 0.4724, 980.80, 1000.00, 'ELO-K32-V2', '2026-10-01 22:00:00'
       UNION ALL SELECT (@cx_sm + 28), (@cx_p + 144), (@cx_u + 88), @cx_s_축구, 1000.00, 966.23, -33.77, 0.00, -33.77, NULL, 0.0, 0.5276, 980.80, 1000.00, 'ELO-K32-V2', '2026-10-01 22:00:00'
       UNION ALL SELECT (@cx_sm + 28), (@cx_p + 145), (@cx_u + 89), @cx_s_축구, 1000.00, 966.23, -33.77, 0.00, -33.77, NULL, 0.0, 0.5276, 980.80, 1000.00, 'ELO-K32-V2', '2026-10-01 22:00:00'
       UNION ALL SELECT (@cx_sm + 28), (@cx_p + 143), (@cx_u + 90), @cx_s_축구, 1000.00, 966.23, -33.77, 0.00, -33.77, NULL, 0.0, 0.5276, 980.80, 1000.00, 'ELO-K32-V2', '2026-10-01 22:00:00'
       UNION ALL SELECT (@cx_sm + 28), (@cx_p + 141), (@cx_u + 93), @cx_s_축구, 1000.00, 966.23, -33.77, 0.00, -33.77, NULL, 0.0, 0.5276, 980.80, 1000.00, 'ELO-K32-V2', '2026-10-01 22:00:00'
       UNION ALL SELECT (@cx_sm + 28), (@cx_p + 142), (@cx_u + 94), @cx_s_축구, 1000.00, 966.23, -33.77, 0.00, -33.77, NULL, 0.0, 0.5276, 980.80, 1000.00, 'ELO-K32-V2', '2026-10-01 22:00:00'
       UNION ALL SELECT (@cx_sm + 29), (@cx_p + 157), (@cx_u + 56), @cx_s_농구, 1108.91, 1069.52, -39.39, 0.00, -39.39, NULL, 0.0, 0.6154, 1081.68, 1000.00, 'ELO-K32-V2', '2026-10-03 21:00:00'
       UNION ALL SELECT (@cx_sm + 29), (@cx_p + 156), (@cx_u + 57), @cx_s_농구, 1108.91, 1069.52, -39.39, 0.00, -39.39, NULL, 0.0, 0.6154, 1081.68, 1000.00, 'ELO-K32-V2', '2026-10-03 21:00:00'
       UNION ALL SELECT (@cx_sm + 29), (@cx_p + 155), (@cx_u + 58), @cx_s_농구, 1055.44, 1016.05, -39.39, 0.00, -39.39, NULL, 0.0, 0.6154, 1081.68, 1000.00, 'ELO-K32-V2', '2026-10-03 21:00:00'
       UNION ALL SELECT (@cx_sm + 29), (@cx_p + 158), (@cx_u + 60), @cx_s_농구, 1053.47, 1014.08, -39.39, 0.00, -39.39, NULL, 0.0, 0.6154, 1081.68, 1000.00, 'ELO-K32-V2', '2026-10-03 21:00:00'
       UNION ALL SELECT (@cx_sm + 29), (@cx_p + 151), (@cx_u + 95), @cx_s_농구, 1000.00, 1039.39, 39.39, 0.00, 39.39, NULL, 1.0, 0.3846, 1081.68, 1000.00, 'ELO-K32-V2', '2026-10-03 21:00:00'
       UNION ALL SELECT (@cx_sm + 29), (@cx_p + 153), (@cx_u + 96), @cx_s_농구, 1000.00, 1039.39, 39.39, 0.00, 39.39, NULL, 1.0, 0.3846, 1081.68, 1000.00, 'ELO-K32-V2', '2026-10-03 21:00:00'
       UNION ALL SELECT (@cx_sm + 29), (@cx_p + 154), (@cx_u + 97), @cx_s_농구, 1000.00, 1039.39, 39.39, 0.00, 39.39, NULL, 1.0, 0.3846, 1081.68, 1000.00, 'ELO-K32-V2', '2026-10-03 21:00:00'
       UNION ALL SELECT (@cx_sm + 29), (@cx_p + 152), (@cx_u + 98), @cx_s_농구, 1000.00, 1039.39, 39.39, 0.00, 39.39, NULL, 1.0, 0.3846, 1081.68, 1000.00, 'ELO-K32-V2', '2026-10-03 21:00:00'
       UNION ALL SELECT (@cx_sm + 30), (@cx_p + 159), (@cx_u + 1), @cx_s_농구, 998.60, 959.39, -39.21, 0.00, -39.21, NULL, 0.0, 0.6126, 998.95, 919.37, 'ELO-K32-V2', '2026-10-04 11:00:00'
       UNION ALL SELECT (@cx_sm + 30), (@cx_p + 162), (@cx_u + 2), @cx_s_농구, 998.60, 959.39, -39.21, 0.00, -39.21, NULL, 0.0, 0.6126, 998.95, 919.37, 'ELO-K32-V2', '2026-10-04 11:00:00'
       UNION ALL SELECT (@cx_sm + 30), (@cx_p + 161), (@cx_u + 5), @cx_s_농구, 968.07, 928.86, -39.21, 0.00, -39.21, NULL, 0.0, 0.6126, 998.95, 919.37, 'ELO-K32-V2', '2026-10-04 11:00:00'
       UNION ALL SELECT (@cx_sm + 30), (@cx_p + 160), (@cx_u + 6), @cx_s_농구, 1030.53, 991.32, -39.21, 0.00, -39.21, NULL, 0.0, 0.6126, 998.95, 919.37, 'ELO-K32-V2', '2026-10-04 11:00:00'
       UNION ALL SELECT (@cx_sm + 30), (@cx_p + 163), (@cx_u + 31), @cx_s_농구, 892.49, 931.70, 39.21, 0.00, 39.21, NULL, 1.0, 0.3874, 998.95, 919.37, 'ELO-K32-V2', '2026-10-04 11:00:00'
       UNION ALL SELECT (@cx_sm + 30), (@cx_p + 164), (@cx_u + 32), @cx_s_농구, 892.49, 931.70, 39.21, 0.00, 39.21, NULL, 1.0, 0.3874, 998.95, 919.37, 'ELO-K32-V2', '2026-10-04 11:00:00'
       UNION ALL SELECT (@cx_sm + 30), (@cx_p + 165), (@cx_u + 33), @cx_s_농구, 946.53, 985.74, 39.21, 0.00, 39.21, NULL, 1.0, 0.3874, 998.95, 919.37, 'ELO-K32-V2', '2026-10-04 11:00:00'
       UNION ALL SELECT (@cx_sm + 30), (@cx_p + 166), (@cx_u + 35), @cx_s_농구, 945.96, 985.17, 39.21, 0.00, 39.21, NULL, 1.0, 0.3874, 998.95, 919.37, 'ELO-K32-V2', '2026-10-04 11:00:00'
       UNION ALL SELECT (@cx_sm + 31), (@cx_p + 169), (@cx_u + 74), @cx_s_축구, 998.23, 964.50, -33.73, 0.00, -33.73, NULL, 0.0, 0.5271, 998.58, 979.74, 'ELO-K32-V2', '2026-10-04 12:00:00'
       UNION ALL SELECT (@cx_sm + 31), (@cx_p + 167), (@cx_u + 75), @cx_s_축구, 998.23, 964.50, -33.73, 0.00, -33.73, NULL, 0.0, 0.5271, 998.58, 979.74, 'ELO-K32-V2', '2026-10-04 12:00:00'
       UNION ALL SELECT (@cx_sm + 31), (@cx_p + 168), (@cx_u + 76), @cx_s_축구, 998.23, 964.50, -33.73, 0.00, -33.73, NULL, 0.0, 0.5271, 998.58, 979.74, 'ELO-K32-V2', '2026-10-04 12:00:00'
       UNION ALL SELECT (@cx_sm + 31), (@cx_p + 171), (@cx_u + 78), @cx_s_축구, 966.23, 932.50, -33.73, 0.00, -33.73, NULL, 0.0, 0.5271, 998.58, 979.74, 'ELO-K32-V2', '2026-10-04 12:00:00'
       UNION ALL SELECT (@cx_sm + 31), (@cx_p + 170), (@cx_u + 80), @cx_s_축구, 1032.00, 998.27, -33.73, 0.00, -33.73, NULL, 0.0, 0.5271, 998.58, 979.74, 'ELO-K32-V2', '2026-10-04 12:00:00'
       UNION ALL SELECT (@cx_sm + 31), (@cx_p + 174), (@cx_u + 88), @cx_s_축구, 966.23, 999.96, 33.73, 0.00, 33.73, NULL, 1.0, 0.4729, 998.58, 979.74, 'ELO-K32-V2', '2026-10-04 12:00:00'
       UNION ALL SELECT (@cx_sm + 31), (@cx_p + 176), (@cx_u + 89), @cx_s_축구, 966.23, 999.96, 33.73, 0.00, 33.73, NULL, 1.0, 0.4729, 998.58, 979.74, 'ELO-K32-V2', '2026-10-04 12:00:00'
       UNION ALL SELECT (@cx_sm + 31), (@cx_p + 175), (@cx_u + 90), @cx_s_축구, 966.23, 999.96, 33.73, 0.00, 33.73, NULL, 1.0, 0.4729, 998.58, 979.74, 'ELO-K32-V2', '2026-10-04 12:00:00'
       UNION ALL SELECT (@cx_sm + 31), (@cx_p + 172), (@cx_u + 91), @cx_s_축구, 1000.00, 1033.73, 33.73, 0.00, 33.73, NULL, 1.0, 0.4729, 998.58, 979.74, 'ELO-K32-V2', '2026-10-04 12:00:00'
       UNION ALL SELECT (@cx_sm + 31), (@cx_p + 173), (@cx_u + 92), @cx_s_축구, 1000.00, 1033.73, 33.73, 0.00, 33.73, NULL, 1.0, 0.4729, 998.58, 979.74, 'ELO-K32-V2', '2026-10-04 12:00:00'
       UNION ALL SELECT (@cx_sm + 32), (@cx_p + 180), (@cx_u + 48), @cx_s_테니스, 999.33, 1034.00, 34.67, 0.00, 34.67, NULL, 1.0, 0.4583, 1014.90, 1043.92, 'ELO-K32-V2', '2026-10-05 10:00:00'
       UNION ALL SELECT (@cx_sm + 32), (@cx_p + 179), (@cx_u + 49), @cx_s_테니스, 1030.46, 1065.13, 34.67, 0.00, 34.67, NULL, 1.0, 0.4583, 1014.90, 1043.92, 'ELO-K32-V2', '2026-10-05 10:00:00'
       UNION ALL SELECT (@cx_sm + 32), (@cx_p + 177), (@cx_u + 66), @cx_s_테니스, 1057.37, 1022.70, -34.67, 0.00, -34.67, NULL, 0.0, 0.5417, 1014.90, 1043.92, 'ELO-K32-V2', '2026-10-05 10:00:00'
       UNION ALL SELECT (@cx_sm + 32), (@cx_p + 178), (@cx_u + 67), @cx_s_테니스, 1030.46, 995.79, -34.67, 0.00, -34.67, NULL, 0.0, 0.5417, 1014.90, 1043.92, 'ELO-K32-V2', '2026-10-05 10:00:00'
       UNION ALL SELECT (@cx_sm + 33), (@cx_p + 182), (@cx_u + 41), @cx_s_축구, 1001.77, 971.50, -30.27, 0.00, -30.27, NULL, 0.0, 0.4729, 1020.26, 1001.42, 'ELO-K32-V2', '2026-10-07 21:00:00'
       UNION ALL SELECT (@cx_sm + 33), (@cx_p + 185), (@cx_u + 42), @cx_s_축구, 1001.77, 971.50, -30.27, 0.00, -30.27, NULL, 0.0, 0.4729, 1020.26, 1001.42, 'ELO-K32-V2', '2026-10-07 21:00:00'
       UNION ALL SELECT (@cx_sm + 33), (@cx_p + 184), (@cx_u + 43), @cx_s_축구, 1001.77, 971.50, -30.27, 0.00, -30.27, NULL, 0.0, 0.4729, 1020.26, 1001.42, 'ELO-K32-V2', '2026-10-07 21:00:00'
       UNION ALL SELECT (@cx_sm + 33), (@cx_p + 181), (@cx_u + 45), @cx_s_축구, 1033.77, 1003.50, -30.27, 0.00, -30.27, NULL, 0.0, 0.4729, 1020.26, 1001.42, 'ELO-K32-V2', '2026-10-07 21:00:00'
       UNION ALL SELECT (@cx_sm + 33), (@cx_p + 183), (@cx_u + 46), @cx_s_축구, 968.00, 937.73, -30.27, 0.00, -30.27, NULL, 0.0, 0.4729, 1020.26, 1001.42, 'ELO-K32-V2', '2026-10-07 21:00:00'
       UNION ALL SELECT (@cx_sm + 33), (@cx_p + 189), (@cx_u + 81), @cx_s_축구, 1033.77, 1064.04, 30.27, 0.00, 30.27, NULL, 1.0, 0.5271, 1020.26, 1001.42, 'ELO-K32-V2', '2026-10-07 21:00:00'
       UNION ALL SELECT (@cx_sm + 33), (@cx_p + 190), (@cx_u + 82), @cx_s_축구, 1033.77, 1064.04, 30.27, 0.00, 30.27, NULL, 1.0, 0.5271, 1020.26, 1001.42, 'ELO-K32-V2', '2026-10-07 21:00:00'
       UNION ALL SELECT (@cx_sm + 33), (@cx_p + 186), (@cx_u + 83), @cx_s_축구, 1033.77, 1064.04, 30.27, 0.00, 30.27, NULL, 1.0, 0.5271, 1020.26, 1001.42, 'ELO-K32-V2', '2026-10-07 21:00:00'
       UNION ALL SELECT (@cx_sm + 33), (@cx_p + 187), (@cx_u + 84), @cx_s_축구, 1000.00, 1030.27, 30.27, 0.00, 30.27, NULL, 1.0, 0.5271, 1020.26, 1001.42, 'ELO-K32-V2', '2026-10-07 21:00:00'
       UNION ALL SELECT (@cx_sm + 33), (@cx_p + 188), (@cx_u + 87), @cx_s_축구, 1000.00, 1030.27, 30.27, 0.00, 30.27, NULL, 1.0, 0.5271, 1020.26, 1001.42, 'ELO-K32-V2', '2026-10-07 21:00:00'
       UNION ALL SELECT (@cx_sm + 34), (@cx_p + 193), (@cx_u + 37), @cx_s_배드민턴, 1062.67, 1055.98, -6.69, 0.00, -6.69, NULL, 0.5, 0.6045, 957.67, 1031.34, 'ELO-K32-V2', '2026-10-07 22:00:00'
       UNION ALL SELECT (@cx_sm + 34), (@cx_p + 194), (@cx_u + 39), @cx_s_배드민턴, 1000.00, 993.31, -6.69, 0.00, -6.69, NULL, 0.5, 0.6045, 957.67, 1031.34, 'ELO-K32-V2', '2026-10-07 22:00:00'
       UNION ALL SELECT (@cx_sm + 34), (@cx_p + 192), (@cx_u + 52), @cx_s_배드민턴, 944.47, 951.16, 6.69, 0.00, 6.69, NULL, 0.5, 0.3955, 957.67, 1031.34, 'ELO-K32-V2', '2026-10-07 22:00:00'
       UNION ALL SELECT (@cx_sm + 34), (@cx_p + 191), (@cx_u + 54), @cx_s_배드민턴, 970.87, 977.56, 6.69, 0.00, 6.69, NULL, 0.5, 0.3955, 957.67, 1031.34, 'ELO-K32-V2', '2026-10-07 22:00:00'
       UNION ALL SELECT (@cx_sm + 35), (@cx_p + 202), (@cx_u + 95), @cx_s_농구, 1039.39, 1005.58, -33.81, 0.00, -33.81, NULL, 0.0, 0.5283, 1019.70, 1000.00, 'ELO-K32-V2', '2026-10-08 20:00:00'
       UNION ALL SELECT (@cx_sm + 35), (@cx_p + 201), (@cx_u + 96), @cx_s_농구, 1039.39, 1005.58, -33.81, 0.00, -33.81, NULL, 0.0, 0.5283, 1019.70, 1000.00, 'ELO-K32-V2', '2026-10-08 20:00:00'
       UNION ALL SELECT (@cx_sm + 35), (@cx_p + 200), (@cx_u + 99), @cx_s_농구, 1000.00, 966.19, -33.81, 0.00, -33.81, NULL, 0.0, 0.5283, 1019.70, 1000.00, 'ELO-K32-V2', '2026-10-08 20:00:00'
       UNION ALL SELECT (@cx_sm + 35), (@cx_p + 199), (@cx_u + 100), @cx_s_농구, 1000.00, 966.19, -33.81, 0.00, -33.81, NULL, 0.0, 0.5283, 1019.70, 1000.00, 'ELO-K32-V2', '2026-10-08 20:00:00'
       UNION ALL SELECT (@cx_sm + 35), (@cx_p + 195), (@cx_u + 101), @cx_s_농구, 1000.00, 1033.81, 33.81, 0.00, 33.81, NULL, 1.0, 0.4717, 1019.70, 1000.00, 'ELO-K32-V2', '2026-10-08 20:00:00'
       UNION ALL SELECT (@cx_sm + 35), (@cx_p + 198), (@cx_u + 102), @cx_s_농구, 1000.00, 1033.81, 33.81, 0.00, 33.81, NULL, 1.0, 0.4717, 1019.70, 1000.00, 'ELO-K32-V2', '2026-10-08 20:00:00'
       UNION ALL SELECT (@cx_sm + 35), (@cx_p + 196), (@cx_u + 103), @cx_s_농구, 1000.00, 1033.81, 33.81, 0.00, 33.81, NULL, 1.0, 0.4717, 1019.70, 1000.00, 'ELO-K32-V2', '2026-10-08 20:00:00'
       UNION ALL SELECT (@cx_sm + 35), (@cx_p + 197), (@cx_u + 105), @cx_s_농구, 1000.00, 1033.81, 33.81, 0.00, 33.81, NULL, 1.0, 0.4717, 1019.70, 1000.00, 'ELO-K32-V2', '2026-10-08 20:00:00'
       UNION ALL SELECT (@cx_sm + 36), (@cx_p + 206), (@cx_u + 7), @cx_s_테니스, 1111.39, 1069.18, -42.21, 0.00, -42.21, NULL, 0.0, 0.6596, 956.78, 1071.70, 'ELO-K32-V2', '2026-10-08 22:00:00'
       UNION ALL SELECT (@cx_sm + 36), (@cx_p + 205), (@cx_u + 8), @cx_s_테니스, 1032.00, 989.79, -42.21, 0.00, -42.21, NULL, 0.0, 0.6596, 956.78, 1071.70, 'ELO-K32-V2', '2026-10-08 22:00:00'
       UNION ALL SELECT (@cx_sm + 36), (@cx_p + 203), (@cx_u + 27), @cx_s_테니스, 915.02, 957.23, 42.21, 0.00, 42.21, NULL, 1.0, 0.3404, 956.78, 1071.70, 'ELO-K32-V2', '2026-10-08 22:00:00'
       UNION ALL SELECT (@cx_sm + 36), (@cx_p + 204), (@cx_u + 30), @cx_s_테니스, 998.53, 1040.74, 42.21, 0.00, 42.21, NULL, 1.0, 0.3404, 956.78, 1071.70, 'ELO-K32-V2', '2026-10-08 22:00:00'
       UNION ALL SELECT (@cx_sm + 37), (@cx_p + 210), (@cx_u + 1), @cx_s_농구, 959.39, 930.83, -28.56, 0.00, -28.56, NULL, 0.0, 0.4462, 979.35, 1016.91, 'ELO-K32-V2', '2026-10-11 11:00:00'
       UNION ALL SELECT (@cx_sm + 37), (@cx_p + 209), (@cx_u + 2), @cx_s_농구, 959.39, 930.83, -28.56, 0.00, -28.56, NULL, 0.0, 0.4462, 979.35, 1016.91, 'ELO-K32-V2', '2026-10-11 11:00:00'
       UNION ALL SELECT (@cx_sm + 37), (@cx_p + 207), (@cx_u + 3), @cx_s_농구, 968.07, 939.51, -28.56, 0.00, -28.56, NULL, 0.0, 0.4462, 979.35, 1016.91, 'ELO-K32-V2', '2026-10-11 11:00:00'
       UNION ALL SELECT (@cx_sm + 37), (@cx_p + 208), (@cx_u + 4), @cx_s_농구, 1030.53, 1001.97, -28.56, 0.00, -28.56, NULL, 0.0, 0.4462, 979.35, 1016.91, 'ELO-K32-V2', '2026-10-11 11:00:00'
       UNION ALL SELECT (@cx_sm + 37), (@cx_p + 213), (@cx_u + 101), @cx_s_농구, 1033.81, 1062.37, 28.56, 0.00, 28.56, NULL, 1.0, 0.5538, 979.35, 1016.91, 'ELO-K32-V2', '2026-10-11 11:00:00'
       UNION ALL SELECT (@cx_sm + 37), (@cx_p + 211), (@cx_u + 102), @cx_s_농구, 1033.81, 1062.37, 28.56, 0.00, 28.56, NULL, 1.0, 0.5538, 979.35, 1016.91, 'ELO-K32-V2', '2026-10-11 11:00:00'
       UNION ALL SELECT (@cx_sm + 37), (@cx_p + 212), (@cx_u + 104), @cx_s_농구, 1000.00, 1028.56, 28.56, 0.00, 28.56, NULL, 1.0, 0.5538, 979.35, 1016.91, 'ELO-K32-V2', '2026-10-11 11:00:00'
       UNION ALL SELECT (@cx_sm + 37), (@cx_p + 214), (@cx_u + 106), @cx_s_농구, 1000.00, 1028.56, 28.56, 0.00, 28.56, NULL, 1.0, 0.5538, 979.35, 1016.91, 'ELO-K32-V2', '2026-10-11 11:00:00'
       UNION ALL SELECT (@cx_sm + 38), (@cx_p + 215), (@cx_u + 62), @cx_s_족구, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-10-11 12:00:00'
       UNION ALL SELECT (@cx_sm + 38), (@cx_p + 217), (@cx_u + 63), @cx_s_족구, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-10-11 12:00:00'
       UNION ALL SELECT (@cx_sm + 38), (@cx_p + 216), (@cx_u + 65), @cx_s_족구, 1000.00, 968.00, -32.00, 0.00, -32.00, NULL, 0.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-10-11 12:00:00'
       UNION ALL SELECT (@cx_sm + 38), (@cx_p + 219), (@cx_u + 107), @cx_s_족구, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-10-11 12:00:00'
       UNION ALL SELECT (@cx_sm + 38), (@cx_p + 220), (@cx_u + 108), @cx_s_족구, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-10-11 12:00:00'
       UNION ALL SELECT (@cx_sm + 38), (@cx_p + 218), (@cx_u + 109), @cx_s_족구, 1000.00, 1032.00, 32.00, 0.00, 32.00, NULL, 1.0, 0.5000, 1000.00, 1000.00, 'ELO-K32-V2', '2026-10-11 12:00:00'
       UNION ALL SELECT (@cx_sm + 39), (@cx_p + 223), (@cx_u + 70), @cx_s_배드민턴, 931.87, 903.00, -28.87, 0.00, -28.87, NULL, 0.0, 0.4511, 965.94, 1000.00, 'ELO-K32-V2', '2026-10-12 11:00:00'
       UNION ALL SELECT (@cx_sm + 39), (@cx_p + 224), (@cx_u + 72), @cx_s_배드민턴, 1000.00, 971.13, -28.87, 0.00, -28.87, NULL, 0.0, 0.4511, 965.94, 1000.00, 'ELO-K32-V2', '2026-10-12 11:00:00'
       UNION ALL SELECT (@cx_sm + 39), (@cx_p + 222), (@cx_u + 111), @cx_s_배드민턴, 1000.00, 1028.87, 28.87, 0.00, 28.87, NULL, 1.0, 0.5489, 965.94, 1000.00, 'ELO-K32-V2', '2026-10-12 11:00:00'
       UNION ALL SELECT (@cx_sm + 39), (@cx_p + 221), (@cx_u + 113), @cx_s_배드민턴, 1000.00, 1028.87, 28.87, 0.00, 28.87, NULL, 1.0, 0.5489, 965.94, 1000.00, 'ELO-K32-V2', '2026-10-12 11:00:00'
       UNION ALL SELECT (@cx_sm + 40), (@cx_p + 227), (@cx_u + 81), @cx_s_축구, 1064.04, 1026.20, -37.84, 0.00, -37.84, NULL, 0.0, 0.5912, 1050.53, 986.47, 'ELO-K32-V2', '2026-10-14 21:00:00'
       UNION ALL SELECT (@cx_sm + 40), (@cx_p + 228), (@cx_u + 82), @cx_s_축구, 1064.04, 1026.20, -37.84, 0.00, -37.84, NULL, 0.0, 0.5912, 1050.53, 986.47, 'ELO-K32-V2', '2026-10-14 21:00:00'
       UNION ALL SELECT (@cx_sm + 40), (@cx_p + 226), (@cx_u + 83), @cx_s_축구, 1064.04, 1026.20, -37.84, 0.00, -37.84, NULL, 0.0, 0.5912, 1050.53, 986.47, 'ELO-K32-V2', '2026-10-14 21:00:00'
       UNION ALL SELECT (@cx_sm + 40), (@cx_p + 229), (@cx_u + 84), @cx_s_축구, 1030.27, 992.43, -37.84, 0.00, -37.84, NULL, 0.0, 0.5912, 1050.53, 986.47, 'ELO-K32-V2', '2026-10-14 21:00:00'
       UNION ALL SELECT (@cx_sm + 40), (@cx_p + 225), (@cx_u + 87), @cx_s_축구, 1030.27, 992.43, -37.84, 0.00, -37.84, NULL, 0.0, 0.5912, 1050.53, 986.47, 'ELO-K32-V2', '2026-10-14 21:00:00'
       UNION ALL SELECT (@cx_sm + 40), (@cx_p + 231), (@cx_u + 88), @cx_s_축구, 999.96, 1037.80, 37.84, 0.00, 37.84, NULL, 1.0, 0.4088, 1050.53, 986.47, 'ELO-K32-V2', '2026-10-14 21:00:00'
       UNION ALL SELECT (@cx_sm + 40), (@cx_p + 232), (@cx_u + 89), @cx_s_축구, 999.96, 1037.80, 37.84, 0.00, 37.84, NULL, 1.0, 0.4088, 1050.53, 986.47, 'ELO-K32-V2', '2026-10-14 21:00:00'
       UNION ALL SELECT (@cx_sm + 40), (@cx_p + 230), (@cx_u + 90), @cx_s_축구, 999.96, 1037.80, 37.84, 0.00, 37.84, NULL, 1.0, 0.4088, 1050.53, 986.47, 'ELO-K32-V2', '2026-10-14 21:00:00'
       UNION ALL SELECT (@cx_sm + 40), (@cx_p + 233), (@cx_u + 93), @cx_s_축구, 966.23, 1004.07, 37.84, 0.00, 37.84, NULL, 1.0, 0.4088, 1050.53, 986.47, 'ELO-K32-V2', '2026-10-14 21:00:00'
       UNION ALL SELECT (@cx_sm + 40), (@cx_p + 234), (@cx_u + 94), @cx_s_축구, 966.23, 1004.07, 37.84, 0.00, 37.84, NULL, 1.0, 0.4088, 1050.53, 986.47, 'ELO-K32-V2', '2026-10-14 21:00:00'
       UNION ALL SELECT (@cx_sm + 41), (@cx_p + 235), (@cx_u + 11), @cx_s_테니스, 1002.51, 971.73, -30.78, 0.00, -30.78, NULL, 0.0, 0.4810, 986.76, 1000.00, 'ELO-K32-V2', '2026-10-14 23:00:00'
       UNION ALL SELECT (@cx_sm + 41), (@cx_p + 236), (@cx_u + 14), @cx_s_테니스, 971.01, 940.23, -30.78, 0.00, -30.78, NULL, 0.0, 0.4810, 986.76, 1000.00, 'ELO-K32-V2', '2026-10-14 23:00:00'
       UNION ALL SELECT (@cx_sm + 41), (@cx_p + 237), (@cx_u + 115), @cx_s_테니스, 1000.00, 1030.78, 30.78, 0.00, 30.78, NULL, 1.0, 0.5190, 986.76, 1000.00, 'ELO-K32-V2', '2026-10-14 23:00:00'
       UNION ALL SELECT (@cx_sm + 41), (@cx_p + 238), (@cx_u + 116), @cx_s_테니스, 1000.00, 1030.78, 30.78, 0.00, 30.78, NULL, 1.0, 0.5190, 986.76, 1000.00, 'ELO-K32-V2', '2026-10-14 23:00:00'
       UNION ALL SELECT (@cx_sm + 42), (@cx_p + 239), (@cx_u + 15), @cx_s_배드민턴, 1027.47, 1063.98, 36.51, 0.00, 36.51, NULL, 1.0, 0.4295, 995.47, 1044.76, 'ELO-K32-V2', '2026-10-15 21:00:00'
       UNION ALL SELECT (@cx_sm + 42), (@cx_p + 240), (@cx_u + 16), @cx_s_배드민턴, 963.47, 999.98, 36.51, 0.00, 36.51, NULL, 1.0, 0.4295, 995.47, 1044.76, 'ELO-K32-V2', '2026-10-15 21:00:00'
       UNION ALL SELECT (@cx_sm + 42), (@cx_p + 241), (@cx_u + 37), @cx_s_배드민턴, 1055.98, 1019.47, -36.51, 0.00, -36.51, NULL, 0.0, 0.5705, 995.47, 1044.76, 'ELO-K32-V2', '2026-10-15 21:00:00'
       UNION ALL SELECT (@cx_sm + 42), (@cx_p + 242), (@cx_u + 40), @cx_s_배드민턴, 1033.54, 997.03, -36.51, 0.00, -36.51, NULL, 0.0, 0.5705, 995.47, 1044.76, 'ELO-K32-V2', '2026-10-15 21:00:00'
       UNION ALL SELECT (@cx_sm + 43), (@cx_p + 246), (@cx_u + 41), @cx_s_축구, 971.50, 939.05, -32.45, 0.00, -32.45, NULL, 0.0, 0.5071, 983.25, 978.35, 'ELO-K32-V2', '2026-10-15 22:00:00'
       UNION ALL SELECT (@cx_sm + 43), (@cx_p + 244), (@cx_u + 42), @cx_s_축구, 971.50, 939.05, -32.45, 0.00, -32.45, NULL, 0.0, 0.5071, 983.25, 978.35, 'ELO-K32-V2', '2026-10-15 22:00:00'
       UNION ALL SELECT (@cx_sm + 43), (@cx_p + 243), (@cx_u + 43), @cx_s_축구, 971.50, 939.05, -32.45, 0.00, -32.45, NULL, 0.0, 0.5071, 983.25, 978.35, 'ELO-K32-V2', '2026-10-15 22:00:00'
       UNION ALL SELECT (@cx_sm + 43), (@cx_p + 245), (@cx_u + 44), @cx_s_축구, 1033.77, 1001.32, -32.45, 0.00, -32.45, NULL, 0.0, 0.5071, 983.25, 978.35, 'ELO-K32-V2', '2026-10-15 22:00:00'
       UNION ALL SELECT (@cx_sm + 43), (@cx_p + 247), (@cx_u + 47), @cx_s_축구, 968.00, 935.55, -32.45, 0.00, -32.45, NULL, 0.0, 0.5071, 983.25, 978.35, 'ELO-K32-V2', '2026-10-15 22:00:00'
       UNION ALL SELECT (@cx_sm + 43), (@cx_p + 248), (@cx_u + 74), @cx_s_축구, 964.50, 996.95, 32.45, 0.00, 32.45, NULL, 1.0, 0.4929, 983.25, 978.35, 'ELO-K32-V2', '2026-10-15 22:00:00'
       UNION ALL SELECT (@cx_sm + 43), (@cx_p + 250), (@cx_u + 75), @cx_s_축구, 964.50, 996.95, 32.45, 0.00, 32.45, NULL, 1.0, 0.4929, 983.25, 978.35, 'ELO-K32-V2', '2026-10-15 22:00:00'
       UNION ALL SELECT (@cx_sm + 43), (@cx_p + 252), (@cx_u + 76), @cx_s_축구, 964.50, 996.95, 32.45, 0.00, 32.45, NULL, 1.0, 0.4929, 983.25, 978.35, 'ELO-K32-V2', '2026-10-15 22:00:00'
       UNION ALL SELECT (@cx_sm + 43), (@cx_p + 251), (@cx_u + 77), @cx_s_축구, 1032.00, 1064.45, 32.45, 0.00, 32.45, NULL, 1.0, 0.4929, 983.25, 978.35, 'ELO-K32-V2', '2026-10-15 22:00:00'
       UNION ALL SELECT (@cx_sm + 43), (@cx_p + 249), (@cx_u + 79), @cx_s_축구, 966.23, 998.68, 32.45, 0.00, 32.45, NULL, 1.0, 0.4929, 983.25, 978.35, 'ELO-K32-V2', '2026-10-15 22:00:00'
       UNION ALL SELECT (@cx_sm + 44), (@cx_p + 253), (@cx_u + 52), @cx_s_배드민턴, 951.16, 937.67, -13.49, 0.00, -13.49, NULL, 0.0, 0.4217, 959.58, 1014.44, 'ELO-K32-V2', '2026-10-16 22:00:00'
       UNION ALL SELECT (@cx_sm + 44), (@cx_p + 254), (@cx_u + 55), @cx_s_배드민턴, 968.00, 941.01, -26.99, 0.00, -26.99, NULL, 0.0, 0.4217, 959.58, 1014.44, 'ELO-K32-V2', '2026-10-16 22:00:00'
       UNION ALL SELECT (@cx_sm + 44), (@cx_p + 256), (@cx_u + 111), @cx_s_배드민턴, 1028.87, 1055.86, 26.99, 0.00, 26.99, NULL, 1.0, 0.5783, 959.58, 1014.44, 'ELO-K32-V2', '2026-10-16 22:00:00'
       UNION ALL SELECT (@cx_sm + 44), (@cx_p + 255), (@cx_u + 112), @cx_s_배드민턴, 1000.00, 1026.99, 26.99, 0.00, 26.99, NULL, 1.0, 0.5783, 959.58, 1014.44, 'ELO-K32-V2', '2026-10-16 22:00:00'
       UNION ALL SELECT (@cx_sm + 45), (@cx_p + 257), (@cx_u + 7), @cx_s_테니스, 1069.18, 1051.66, -17.52, 0.00, -17.52, NULL, 0.0, 0.5475, 1048.50, 1015.39, 'ELO-K32-V2', '2026-10-17 09:00:00'
       UNION ALL SELECT (@cx_sm + 45), (@cx_p + 258), (@cx_u + 9), @cx_s_테니스, 1027.81, 992.77, -35.04, 0.00, -35.04, NULL, 0.0, 0.5475, 1048.50, 1015.39, 'ELO-K32-V2', '2026-10-17 09:00:00'
       UNION ALL SELECT (@cx_sm + 45), (@cx_p + 259), (@cx_u + 115), @cx_s_테니스, 1030.78, 1065.82, 35.04, 0.00, 35.04, NULL, 1.0, 0.4525, 1048.50, 1015.39, 'ELO-K32-V2', '2026-10-17 09:00:00'
       UNION ALL SELECT (@cx_sm + 45), (@cx_p + 260), (@cx_u + 117), @cx_s_테니스, 1000.00, 1035.04, 35.04, 0.00, 35.04, NULL, 1.0, 0.4525, 1048.50, 1015.39, 'ELO-K32-V2', '2026-10-17 09:00:00'
       UNION ALL SELECT (@cx_sm + 46), (@cx_p + 262), (@cx_u + 19), @cx_s_배드민턴, 1033.52, 1013.25, -20.27, 0.00, -20.27, NULL, 0.0, 0.6334, 1032.03, 937.07, 'ELO-K32-V2', '2026-10-17 22:00:00'
       UNION ALL SELECT (@cx_sm + 46), (@cx_p + 261), (@cx_u + 20), @cx_s_배드민턴, 1030.53, 989.99, -40.54, 0.00, -40.54, NULL, 0.0, 0.6334, 1032.03, 937.07, 'ELO-K32-V2', '2026-10-17 22:00:00'
       UNION ALL SELECT (@cx_sm + 46), (@cx_p + 264), (@cx_u + 70), @cx_s_배드민턴, 903.00, 943.54, 40.54, 0.00, 40.54, NULL, 1.0, 0.3666, 1032.03, 937.07, 'ELO-K32-V2', '2026-10-17 22:00:00'
       UNION ALL SELECT (@cx_sm + 46), (@cx_p + 263), (@cx_u + 72), @cx_s_배드민턴, 971.13, 1011.67, 40.54, 0.00, 40.54, NULL, 1.0, 0.3666, 1032.03, 937.07, 'ELO-K32-V2', '2026-10-17 22:00:00'
       UNION ALL SELECT (@cx_sm + 47), (@cx_p + 267), (@cx_u + 62), @cx_s_족구, 968.00, 1003.91, 35.91, 0.00, 35.91, NULL, 1.0, 0.4389, 978.67, 1021.33, 'ELO-K32-V2', '2026-10-18 11:00:00'
       UNION ALL SELECT (@cx_sm + 47), (@cx_p + 266), (@cx_u + 63), @cx_s_족구, 968.00, 1003.91, 35.91, 0.00, 35.91, NULL, 1.0, 0.4389, 978.67, 1021.33, 'ELO-K32-V2', '2026-10-18 11:00:00'
       UNION ALL SELECT (@cx_sm + 47), (@cx_p + 265), (@cx_u + 64), @cx_s_족구, 1000.00, 1035.91, 35.91, 0.00, 35.91, NULL, 1.0, 0.4389, 978.67, 1021.33, 'ELO-K32-V2', '2026-10-18 11:00:00'
       UNION ALL SELECT (@cx_sm + 47), (@cx_p + 268), (@cx_u + 107), @cx_s_족구, 1032.00, 996.09, -35.91, 0.00, -35.91, NULL, 0.0, 0.5611, 978.67, 1021.33, 'ELO-K32-V2', '2026-10-18 11:00:00'
       UNION ALL SELECT (@cx_sm + 47), (@cx_p + 269), (@cx_u + 108), @cx_s_족구, 1032.00, 996.09, -35.91, 0.00, -35.91, NULL, 0.0, 0.5611, 978.67, 1021.33, 'ELO-K32-V2', '2026-10-18 11:00:00'
       UNION ALL SELECT (@cx_sm + 47), (@cx_p + 270), (@cx_u + 110), @cx_s_족구, 1000.00, 964.09, -35.91, 0.00, -35.91, NULL, 0.0, 0.5611, 978.67, 1021.33, 'ELO-K32-V2', '2026-10-18 11:00:00'
       UNION ALL SELECT (@cx_sm + 48), (@cx_p + 277), (@cx_u + 41), @cx_s_축구, 939.05, 978.72, 39.67, 0.00, 39.67, NULL, 1.0, 0.3802, 1036.17, 951.24, 'ELO-K32-V2', '2026-10-18 16:00:00'
       UNION ALL SELECT (@cx_sm + 48), (@cx_p + 278), (@cx_u + 42), @cx_s_축구, 939.05, 978.72, 39.67, 0.00, 39.67, NULL, 1.0, 0.3802, 1036.17, 951.24, 'ELO-K32-V2', '2026-10-18 16:00:00'
       UNION ALL SELECT (@cx_sm + 48), (@cx_p + 276), (@cx_u + 43), @cx_s_축구, 939.05, 978.72, 39.67, 0.00, 39.67, NULL, 1.0, 0.3802, 1036.17, 951.24, 'ELO-K32-V2', '2026-10-18 16:00:00'
       UNION ALL SELECT (@cx_sm + 48), (@cx_p + 280), (@cx_u + 45), @cx_s_축구, 1003.50, 1043.17, 39.67, 0.00, 39.67, NULL, 1.0, 0.3802, 1036.17, 951.24, 'ELO-K32-V2', '2026-10-18 16:00:00'
       UNION ALL SELECT (@cx_sm + 48), (@cx_p + 279), (@cx_u + 47), @cx_s_축구, 935.55, 975.22, 39.67, 0.00, 39.67, NULL, 1.0, 0.3802, 1036.17, 951.24, 'ELO-K32-V2', '2026-10-18 16:00:00'
       UNION ALL SELECT (@cx_sm + 48), (@cx_p + 271), (@cx_u + 88), @cx_s_축구, 1037.80, 998.13, -39.67, 0.00, -39.67, NULL, 0.0, 0.6198, 1036.17, 951.24, 'ELO-K32-V2', '2026-10-18 16:00:00'
       UNION ALL SELECT (@cx_sm + 48), (@cx_p + 272), (@cx_u + 89), @cx_s_축구, 1037.80, 998.13, -39.67, 0.00, -39.67, NULL, 0.0, 0.6198, 1036.17, 951.24, 'ELO-K32-V2', '2026-10-18 16:00:00'
       UNION ALL SELECT (@cx_sm + 48), (@cx_p + 273), (@cx_u + 90), @cx_s_축구, 1037.80, 998.13, -39.67, 0.00, -39.67, NULL, 0.0, 0.6198, 1036.17, 951.24, 'ELO-K32-V2', '2026-10-18 16:00:00'
       UNION ALL SELECT (@cx_sm + 48), (@cx_p + 274), (@cx_u + 91), @cx_s_축구, 1033.73, 994.06, -39.67, 0.00, -39.67, NULL, 0.0, 0.6198, 1036.17, 951.24, 'ELO-K32-V2', '2026-10-18 16:00:00'
       UNION ALL SELECT (@cx_sm + 48), (@cx_p + 275), (@cx_u + 92), @cx_s_축구, 1033.73, 994.06, -39.67, 0.00, -39.67, NULL, 0.0, 0.6198, 1036.17, 951.24, 'ELO-K32-V2', '2026-10-18 16:00:00'
       UNION ALL SELECT (@cx_sm + 49), (@cx_p + 283), (@cx_u + 1), @cx_s_농구, 930.83, 905.58, -25.25, 0.00, -25.25, NULL, 0.0, 0.3946, 1022.49, 948.12, 'ELO-K32-V2', '2026-10-18 20:00:00'
       UNION ALL SELECT (@cx_sm + 49), (@cx_p + 284), (@cx_u + 2), @cx_s_농구, 930.83, 905.58, -25.25, 0.00, -25.25, NULL, 0.0, 0.3946, 1022.49, 948.12, 'ELO-K32-V2', '2026-10-18 20:00:00'
       UNION ALL SELECT (@cx_sm + 49), (@cx_p + 282), (@cx_u + 4), @cx_s_농구, 1001.97, 976.72, -25.25, 0.00, -25.25, NULL, 0.0, 0.3946, 1022.49, 948.12, 'ELO-K32-V2', '2026-10-18 20:00:00'
       UNION ALL SELECT (@cx_sm + 49), (@cx_p + 281), (@cx_u + 5), @cx_s_농구, 928.86, 903.61, -25.25, 0.00, -25.25, NULL, 0.0, 0.3946, 1022.49, 948.12, 'ELO-K32-V2', '2026-10-18 20:00:00'
       UNION ALL SELECT (@cx_sm + 49), (@cx_p + 285), (@cx_u + 95), @cx_s_농구, 1005.58, 1030.83, 25.25, 0.00, 25.25, NULL, 1.0, 0.6054, 1022.49, 948.12, 'ELO-K32-V2', '2026-10-18 20:00:00'
       UNION ALL SELECT (@cx_sm + 49), (@cx_p + 287), (@cx_u + 96), @cx_s_농구, 1005.58, 1030.83, 25.25, 0.00, 25.25, NULL, 1.0, 0.6054, 1022.49, 948.12, 'ELO-K32-V2', '2026-10-18 20:00:00'
       UNION ALL SELECT (@cx_sm + 49), (@cx_p + 286), (@cx_u + 97), @cx_s_농구, 1039.39, 1064.64, 25.25, 0.00, 25.25, NULL, 1.0, 0.6054, 1022.49, 948.12, 'ELO-K32-V2', '2026-10-18 20:00:00'
       UNION ALL SELECT (@cx_sm + 49), (@cx_p + 288), (@cx_u + 98), @cx_s_농구, 1039.39, 1064.64, 25.25, 0.00, 25.25, NULL, 1.0, 0.6054, 1022.49, 948.12, 'ELO-K32-V2', '2026-10-18 20:00:00'
       UNION ALL SELECT (@cx_sm + 50), (@cx_p + 292), (@cx_u + 23), @cx_s_테니스, 914.38, 953.25, 38.87, 0.00, 38.87, NULL, 1.0, 0.3926, 1033.74, 957.93, 'ELO-K32-V2', '2026-10-19 10:00:00'
       UNION ALL SELECT (@cx_sm + 50), (@cx_p + 291), (@cx_u + 26), @cx_s_테니스, 1001.47, 1040.34, 38.87, 0.00, 38.87, NULL, 1.0, 0.3926, 1033.74, 957.93, 'ELO-K32-V2', '2026-10-19 10:00:00'
       UNION ALL SELECT (@cx_sm + 50), (@cx_p + 289), (@cx_u + 48), @cx_s_테니스, 1034.00, 1014.56, -19.44, 0.00, -19.44, NULL, 0.0, 0.6074, 1033.74, 957.93, 'ELO-K32-V2', '2026-10-19 10:00:00'
       UNION ALL SELECT (@cx_sm + 50), (@cx_p + 290), (@cx_u + 50), @cx_s_테니스, 1033.47, 994.60, -38.87, 0.00, -38.87, NULL, 0.0, 0.6074, 1033.74, 957.93, 'ELO-K32-V2', '2026-10-19 10:00:00'
       UNION ALL SELECT (@cx_sm + 51), (@cx_p + 293), (@cx_u + 37), @cx_s_배드민턴, 1019.47, 989.45, -30.02, 0.00, -30.02, NULL, 0.0, 0.4690, 1006.39, 1027.93, 'ELO-K32-V2', '2026-10-19 12:00:00'
       UNION ALL SELECT (@cx_sm + 51), (@cx_p + 294), (@cx_u + 39), @cx_s_배드민턴, 993.31, 963.29, -30.02, 0.00, -30.02, NULL, 0.0, 0.4690, 1006.39, 1027.93, 'ELO-K32-V2', '2026-10-19 12:00:00'
       UNION ALL SELECT (@cx_sm + 51), (@cx_p + 295), (@cx_u + 111), @cx_s_배드민턴, 1055.86, 1085.88, 30.02, 0.00, 30.02, NULL, 1.0, 0.5310, 1006.39, 1027.93, 'ELO-K32-V2', '2026-10-19 12:00:00'
       UNION ALL SELECT (@cx_sm + 51), (@cx_p + 296), (@cx_u + 114), @cx_s_배드민턴, 1000.00, 1030.02, 30.02, 0.00, 30.02, NULL, 1.0, 0.5310, 1006.39, 1027.93, 'ELO-K32-V2', '2026-10-19 12:00:00'
       UNION ALL SELECT (@cx_sm + 52), (@cx_p + 300), (@cx_u + 11), @cx_s_테니스, 971.73, 990.64, 18.91, 0.00, 18.91, NULL, 1.0, 0.4092, 969.13, 1032.91, 'ELO-K32-V2', '2026-10-21 23:00:00'
       UNION ALL SELECT (@cx_sm + 52), (@cx_p + 299), (@cx_u + 13), @cx_s_테니스, 966.53, 1004.34, 37.81, 0.00, 37.81, NULL, 1.0, 0.4092, 969.13, 1032.91, 'ELO-K32-V2', '2026-10-21 23:00:00'
       UNION ALL SELECT (@cx_sm + 52), (@cx_p + 297), (@cx_u + 115), @cx_s_테니스, 1065.82, 1028.01, -37.81, 0.00, -37.81, NULL, 0.0, 0.5908, 969.13, 1032.91, 'ELO-K32-V2', '2026-10-21 23:00:00'
       UNION ALL SELECT (@cx_sm + 52), (@cx_p + 298), (@cx_u + 118), @cx_s_테니스, 1000.00, 962.19, -37.81, 0.00, -37.81, NULL, 0.0, 0.5908, 969.13, 1032.91, 'ELO-K32-V2', '2026-10-21 23:00:00'
       UNION ALL SELECT (@cx_sm + 53), (@cx_p + 307), (@cx_u + 95), @cx_s_농구, 1030.83, 1067.25, 36.42, 0.00, 36.42, NULL, 1.0, 0.4310, 998.51, 1046.78, 'ELO-K32-V2', '2026-10-22 20:00:00'
       UNION ALL SELECT (@cx_sm + 53), (@cx_p + 305), (@cx_u + 96), @cx_s_농구, 1030.83, 1067.25, 36.42, 0.00, 36.42, NULL, 1.0, 0.4310, 998.51, 1046.78, 'ELO-K32-V2', '2026-10-22 20:00:00'
       UNION ALL SELECT (@cx_sm + 53), (@cx_p + 308), (@cx_u + 99), @cx_s_농구, 966.19, 1002.61, 36.42, 0.00, 36.42, NULL, 1.0, 0.4310, 998.51, 1046.78, 'ELO-K32-V2', '2026-10-22 20:00:00'
       UNION ALL SELECT (@cx_sm + 53), (@cx_p + 306), (@cx_u + 100), @cx_s_농구, 966.19, 1002.61, 36.42, 0.00, 36.42, NULL, 1.0, 0.4310, 998.51, 1046.78, 'ELO-K32-V2', '2026-10-22 20:00:00'
       UNION ALL SELECT (@cx_sm + 53), (@cx_p + 302), (@cx_u + 101), @cx_s_농구, 1062.37, 1025.95, -36.42, 0.00, -36.42, NULL, 0.0, 0.5690, 998.51, 1046.78, 'ELO-K32-V2', '2026-10-22 20:00:00'
       UNION ALL SELECT (@cx_sm + 53), (@cx_p + 304), (@cx_u + 102), @cx_s_농구, 1062.37, 1025.95, -36.42, 0.00, -36.42, NULL, 0.0, 0.5690, 998.51, 1046.78, 'ELO-K32-V2', '2026-10-22 20:00:00'
       UNION ALL SELECT (@cx_sm + 53), (@cx_p + 303), (@cx_u + 103), @cx_s_농구, 1033.81, 997.39, -36.42, 0.00, -36.42, NULL, 0.0, 0.5690, 998.51, 1046.78, 'ELO-K32-V2', '2026-10-22 20:00:00'
       UNION ALL SELECT (@cx_sm + 53), (@cx_p + 301), (@cx_u + 104), @cx_s_농구, 1028.56, 992.14, -36.42, 0.00, -36.42, NULL, 0.0, 0.5690, 998.51, 1046.78, 'ELO-K32-V2', '2026-10-22 20:00:00'
       UNION ALL SELECT (@cx_sm + 54), (@cx_p + 316), (@cx_u + 41), @cx_s_축구, 978.72, 965.20, -13.52, 0.00, -13.52, NULL, 0.0, 0.4226, 975.04, 1029.23, 'ELO-K32-V2', '2026-10-22 22:00:00'
       UNION ALL SELECT (@cx_sm + 54), (@cx_p + 318), (@cx_u + 42), @cx_s_축구, 978.72, 965.20, -13.52, 0.00, -13.52, NULL, 0.0, 0.4226, 975.04, 1029.23, 'ELO-K32-V2', '2026-10-22 22:00:00'
       UNION ALL SELECT (@cx_sm + 54), (@cx_p + 314), (@cx_u + 43), @cx_s_축구, 978.72, 965.20, -13.52, 0.00, -13.52, NULL, 0.0, 0.4226, 975.04, 1029.23, 'ELO-K32-V2', '2026-10-22 22:00:00'
       UNION ALL SELECT (@cx_sm + 54), (@cx_p + 315), (@cx_u + 44), @cx_s_축구, 1001.32, 974.27, -27.05, 0.00, -27.05, NULL, 0.0, 0.4226, 975.04, 1029.23, 'ELO-K32-V2', '2026-10-22 22:00:00'
       UNION ALL SELECT (@cx_sm + 54), (@cx_p + 317), (@cx_u + 46), @cx_s_축구, 937.73, 910.68, -27.05, 0.00, -27.05, NULL, 0.0, 0.4226, 975.04, 1029.23, 'ELO-K32-V2', '2026-10-22 22:00:00'
       UNION ALL SELECT (@cx_sm + 54), (@cx_p + 311), (@cx_u + 81), @cx_s_축구, 1026.20, 1053.25, 27.05, 0.00, 27.05, NULL, 1.0, 0.5774, 975.04, 1029.23, 'ELO-K32-V2', '2026-10-22 22:00:00'
       UNION ALL SELECT (@cx_sm + 54), (@cx_p + 309), (@cx_u + 82), @cx_s_축구, 1026.20, 1053.25, 27.05, 0.00, 27.05, NULL, 1.0, 0.5774, 975.04, 1029.23, 'ELO-K32-V2', '2026-10-22 22:00:00'
       UNION ALL SELECT (@cx_sm + 54), (@cx_p + 313), (@cx_u + 83), @cx_s_축구, 1026.20, 1053.25, 27.05, 0.00, 27.05, NULL, 1.0, 0.5774, 975.04, 1029.23, 'ELO-K32-V2', '2026-10-22 22:00:00'
       UNION ALL SELECT (@cx_sm + 54), (@cx_p + 312), (@cx_u + 85), @cx_s_축구, 1033.77, 1060.82, 27.05, 0.00, 27.05, NULL, 1.0, 0.5774, 975.04, 1029.23, 'ELO-K32-V2', '2026-10-22 22:00:00'
       UNION ALL SELECT (@cx_sm + 54), (@cx_p + 310), (@cx_u + 86), @cx_s_축구, 1033.77, 1060.82, 27.05, 0.00, 27.05, NULL, 1.0, 0.5774, 975.04, 1029.23, 'ELO-K32-V2', '2026-10-22 22:00:00'
       UNION ALL SELECT (@cx_sm + 55), (@cx_p + 322), (@cx_u + 15), @cx_s_배드민턴, 1063.98, 1032.84, -31.14, 0.00, -31.14, NULL, 0.0, 0.4865, 1057.38, 1047.99, 'ELO-K32-V2', '2026-10-24 20:00:00'
       UNION ALL SELECT (@cx_sm + 55), (@cx_p + 321), (@cx_u + 17), @cx_s_배드민턴, 1032.00, 1000.86, -31.14, 0.00, -31.14, NULL, 0.0, 0.4865, 1057.38, 1047.99, 'ELO-K32-V2', '2026-10-24 20:00:00'
       UNION ALL SELECT (@cx_sm + 55), (@cx_p + 320), (@cx_u + 111), @cx_s_배드민턴, 1085.88, 1117.02, 31.14, 0.00, 31.14, NULL, 1.0, 0.5135, 1057.38, 1047.99, 'ELO-K32-V2', '2026-10-24 20:00:00'
       UNION ALL SELECT (@cx_sm + 55), (@cx_p + 319), (@cx_u + 113), @cx_s_배드민턴, 1028.87, 1060.01, 31.14, 0.00, 31.14, NULL, 1.0, 0.5135, 1057.38, 1047.99, 'ELO-K32-V2', '2026-10-24 20:00:00'
       UNION ALL SELECT (@cx_sm + 56), (@cx_p + 323), (@cx_u + 37), @cx_s_배드민턴, 989.45, 1002.91, 13.46, 0.00, 13.46, NULL, 1.0, 0.5793, 953.71, 1009.29, 'ELO-K32-V2', '2026-10-25 11:00:00'
       UNION ALL SELECT (@cx_sm + 56), (@cx_p + 324), (@cx_u + 38), @cx_s_배드민턴, 1029.13, 1056.05, 26.92, 0.00, 26.92, NULL, 1.0, 0.5793, 953.71, 1009.29, 'ELO-K32-V2', '2026-10-25 11:00:00'
       UNION ALL SELECT (@cx_sm + 56), (@cx_p + 326), (@cx_u + 70), @cx_s_배드민턴, 943.54, 916.62, -26.92, 0.00, -26.92, NULL, 0.0, 0.4207, 953.71, 1009.29, 'ELO-K32-V2', '2026-10-25 11:00:00'
       UNION ALL SELECT (@cx_sm + 56), (@cx_p + 325), (@cx_u + 71), @cx_s_배드민턴, 963.87, 936.95, -26.92, 0.00, -26.92, NULL, 0.0, 0.4207, 953.71, 1009.29, 'ELO-K32-V2', '2026-10-25 11:00:00'
       UNION ALL SELECT (@cx_sm + 57), (@cx_p + 328), (@cx_u + 74), @cx_s_축구, 996.95, 967.88, -29.07, 0.00, -29.07, NULL, 0.0, 0.4542, 1010.71, 1042.60, 'ELO-K32-V2', '2026-10-25 12:00:00'
       UNION ALL SELECT (@cx_sm + 57), (@cx_p + 330), (@cx_u + 75), @cx_s_축구, 996.95, 967.88, -29.07, 0.00, -29.07, NULL, 0.0, 0.4542, 1010.71, 1042.60, 'ELO-K32-V2', '2026-10-25 12:00:00'
       UNION ALL SELECT (@cx_sm + 57), (@cx_p + 331), (@cx_u + 76), @cx_s_축구, 996.95, 967.88, -29.07, 0.00, -29.07, NULL, 0.0, 0.4542, 1010.71, 1042.60, 'ELO-K32-V2', '2026-10-25 12:00:00'
       UNION ALL SELECT (@cx_sm + 57), (@cx_p + 327), (@cx_u + 77), @cx_s_축구, 1064.45, 1035.38, -29.07, 0.00, -29.07, NULL, 0.0, 0.4542, 1010.71, 1042.60, 'ELO-K32-V2', '2026-10-25 12:00:00'
       UNION ALL SELECT (@cx_sm + 57), (@cx_p + 329), (@cx_u + 80), @cx_s_축구, 998.27, 969.20, -29.07, 0.00, -29.07, NULL, 0.0, 0.4542, 1010.71, 1042.60, 'ELO-K32-V2', '2026-10-25 12:00:00'
       UNION ALL SELECT (@cx_sm + 57), (@cx_p + 332), (@cx_u + 81), @cx_s_축구, 1053.25, 1082.32, 29.07, 0.00, 29.07, NULL, 1.0, 0.5458, 1010.71, 1042.60, 'ELO-K32-V2', '2026-10-25 12:00:00'
       UNION ALL SELECT (@cx_sm + 57), (@cx_p + 335), (@cx_u + 82), @cx_s_축구, 1053.25, 1082.32, 29.07, 0.00, 29.07, NULL, 1.0, 0.5458, 1010.71, 1042.60, 'ELO-K32-V2', '2026-10-25 12:00:00'
       UNION ALL SELECT (@cx_sm + 57), (@cx_p + 333), (@cx_u + 83), @cx_s_축구, 1053.25, 1082.32, 29.07, 0.00, 29.07, NULL, 1.0, 0.5458, 1010.71, 1042.60, 'ELO-K32-V2', '2026-10-25 12:00:00'
       UNION ALL SELECT (@cx_sm + 57), (@cx_p + 336), (@cx_u + 86), @cx_s_축구, 1060.82, 1089.89, 29.07, 0.00, 29.07, NULL, 1.0, 0.5458, 1010.71, 1042.60, 'ELO-K32-V2', '2026-10-25 12:00:00'
       UNION ALL SELECT (@cx_sm + 57), (@cx_p + 334), (@cx_u + 87), @cx_s_축구, 992.43, 1021.50, 29.07, 0.00, 29.07, NULL, 1.0, 0.5458, 1010.71, 1042.60, 'ELO-K32-V2', '2026-10-25 12:00:00'
       UNION ALL SELECT (@cx_sm + 58), (@cx_p + 342), (@cx_u + 62), @cx_s_족구, 1003.91, 1037.39, 33.48, 0.00, 33.48, NULL, 1.0, 0.4768, 1008.06, 991.94, 'ELO-K32-V2', '2026-10-25 12:00:00'
       UNION ALL SELECT (@cx_sm + 58), (@cx_p + 340), (@cx_u + 63), @cx_s_족구, 1003.91, 1037.39, 33.48, 0.00, 33.48, NULL, 1.0, 0.4768, 1008.06, 991.94, 'ELO-K32-V2', '2026-10-25 12:00:00'
       UNION ALL SELECT (@cx_sm + 58), (@cx_p + 341), (@cx_u + 65), @cx_s_족구, 968.00, 1001.48, 33.48, 0.00, 33.48, NULL, 1.0, 0.4768, 1008.06, 991.94, 'ELO-K32-V2', '2026-10-25 12:00:00'
       UNION ALL SELECT (@cx_sm + 58), (@cx_p + 338), (@cx_u + 107), @cx_s_족구, 996.09, 962.61, -33.48, 0.00, -33.48, NULL, 0.0, 0.5232, 1008.06, 991.94, 'ELO-K32-V2', '2026-10-25 12:00:00'
       UNION ALL SELECT (@cx_sm + 58), (@cx_p + 339), (@cx_u + 108), @cx_s_족구, 996.09, 962.61, -33.48, 0.00, -33.48, NULL, 0.0, 0.5232, 1008.06, 991.94, 'ELO-K32-V2', '2026-10-25 12:00:00'
       UNION ALL SELECT (@cx_sm + 58), (@cx_p + 337), (@cx_u + 109), @cx_s_족구, 1032.00, 998.52, -33.48, 0.00, -33.48, NULL, 0.0, 0.5232, 1008.06, 991.94, 'ELO-K32-V2', '2026-10-25 12:00:00'
       UNION ALL SELECT (@cx_sm + 59), (@cx_p + 344), (@cx_u + 27), @cx_s_테니스, 957.23, 944.33, -12.90, 0.00, -12.90, NULL, 0.0, 0.4032, 1031.53, 963.39, 'ELO-K32-V2', '2026-10-25 12:00:00'
       UNION ALL SELECT (@cx_sm + 59), (@cx_p + 343), (@cx_u + 29), @cx_s_테니스, 969.54, 943.74, -25.80, 0.00, -25.80, NULL, 0.0, 0.4032, 1031.53, 963.39, 'ELO-K32-V2', '2026-10-25 12:00:00'
       UNION ALL SELECT (@cx_sm + 59), (@cx_p + 345), (@cx_u + 115), @cx_s_테니스, 1028.01, 1053.81, 25.80, 0.00, 25.80, NULL, 1.0, 0.5968, 1031.53, 963.39, 'ELO-K32-V2', '2026-10-25 12:00:00'
       UNION ALL SELECT (@cx_sm + 59), (@cx_p + 346), (@cx_u + 117), @cx_s_테니스, 1035.04, 1060.84, 25.80, 0.00, 25.80, NULL, 1.0, 0.5968, 1031.53, 963.39, 'ELO-K32-V2', '2026-10-25 12:00:00'
       UNION ALL SELECT (@cx_sm + 60), (@cx_p + 352), (@cx_u + 95), @cx_s_농구, 1067.25, 1097.24, 29.99, 0.00, 29.99, NULL, 1.0, 0.5314, 1050.44, 1028.57, 'ELO-K32-V2', '2026-10-25 20:00:00'
       UNION ALL SELECT (@cx_sm + 60), (@cx_p + 347), (@cx_u + 96), @cx_s_농구, 1067.25, 1097.24, 29.99, 0.00, 29.99, NULL, 1.0, 0.5314, 1050.44, 1028.57, 'ELO-K32-V2', '2026-10-25 20:00:00'
       UNION ALL SELECT (@cx_sm + 60), (@cx_p + 349), (@cx_u + 98), @cx_s_농구, 1064.64, 1094.63, 29.99, 0.00, 29.99, NULL, 1.0, 0.5314, 1050.44, 1028.57, 'ELO-K32-V2', '2026-10-25 20:00:00'
       UNION ALL SELECT (@cx_sm + 60), (@cx_p + 348), (@cx_u + 100), @cx_s_농구, 1002.61, 1032.60, 29.99, 0.00, 29.99, NULL, 1.0, 0.5314, 1050.44, 1028.57, 'ELO-K32-V2', '2026-10-25 20:00:00'
       UNION ALL SELECT (@cx_sm + 60), (@cx_p + 354), (@cx_u + 101), @cx_s_농구, 1025.95, 995.96, -29.99, 0.00, -29.99, NULL, 0.0, 0.4686, 1050.44, 1028.57, 'ELO-K32-V2', '2026-10-25 20:00:00'
       UNION ALL SELECT (@cx_sm + 60), (@cx_p + 353), (@cx_u + 102), @cx_s_농구, 1025.95, 995.96, -29.99, 0.00, -29.99, NULL, 0.0, 0.4686, 1050.44, 1028.57, 'ELO-K32-V2', '2026-10-25 20:00:00'
       UNION ALL SELECT (@cx_sm + 60), (@cx_p + 350), (@cx_u + 105), @cx_s_농구, 1033.81, 1003.82, -29.99, 0.00, -29.99, NULL, 0.0, 0.4686, 1050.44, 1028.57, 'ELO-K32-V2', '2026-10-25 20:00:00'
       UNION ALL SELECT (@cx_sm + 60), (@cx_p + 351), (@cx_u + 106), @cx_s_농구, 1028.56, 998.57, -29.99, 0.00, -29.99, NULL, 0.0, 0.4686, 1050.44, 1028.57, 'ELO-K32-V2', '2026-10-25 20:00:00'
       UNION ALL SELECT (@cx_sm + 61), (@cx_p + 362), (@cx_u + 1), @cx_s_농구, 905.58, 895.16, -10.42, 0.00, -10.42, NULL, 0.0, 0.3256, 935.50, 1061.99, 'ELO-K32-V2', '2026-10-26 11:00:00'
       UNION ALL SELECT (@cx_sm + 61), (@cx_p + 359), (@cx_u + 2), @cx_s_농구, 905.58, 895.16, -10.42, 0.00, -10.42, NULL, 0.0, 0.3256, 935.50, 1061.99, 'ELO-K32-V2', '2026-10-26 11:00:00'
       UNION ALL SELECT (@cx_sm + 61), (@cx_p + 361), (@cx_u + 3), @cx_s_농구, 939.51, 918.67, -20.84, 0.00, -20.84, NULL, 0.0, 0.3256, 935.50, 1061.99, 'ELO-K32-V2', '2026-10-26 11:00:00'
       UNION ALL SELECT (@cx_sm + 61), (@cx_p + 356), (@cx_u + 6), @cx_s_농구, 991.32, 970.48, -20.84, 0.00, -20.84, NULL, 0.0, 0.3256, 935.50, 1061.99, 'ELO-K32-V2', '2026-10-26 11:00:00'
       UNION ALL SELECT (@cx_sm + 61), (@cx_p + 357), (@cx_u + 56), @cx_s_농구, 1069.52, 1079.94, 10.42, 0.00, 10.42, NULL, 1.0, 0.6744, 935.50, 1061.99, 'ELO-K32-V2', '2026-10-26 11:00:00'
       UNION ALL SELECT (@cx_sm + 61), (@cx_p + 358), (@cx_u + 57), @cx_s_농구, 1069.52, 1079.94, 10.42, 0.00, 10.42, NULL, 1.0, 0.6744, 935.50, 1061.99, 'ELO-K32-V2', '2026-10-26 11:00:00'
       UNION ALL SELECT (@cx_sm + 61), (@cx_p + 355), (@cx_u + 59), @cx_s_농구, 1053.40, 1074.24, 20.84, 0.00, 20.84, NULL, 1.0, 0.6744, 935.50, 1061.99, 'ELO-K32-V2', '2026-10-26 11:00:00'
       UNION ALL SELECT (@cx_sm + 61), (@cx_p + 360), (@cx_u + 61), @cx_s_농구, 1055.51, 1076.35, 20.84, 0.00, 20.84, NULL, 1.0, 0.6744, 935.50, 1061.99, 'ELO-K32-V2', '2026-10-26 11:00:00'
       UNION ALL SELECT (@cx_sm + 62), (@cx_p + 365), (@cx_u + 15), @cx_s_배드민턴, 1032.84, 1050.65, 17.81, 0.00, 17.81, NULL, 1.0, 0.4433, 1072.01, 1032.42, 'ELO-K32-V2', '2026-10-28 20:00:00'
       UNION ALL SELECT (@cx_sm + 62), (@cx_p + 366), (@cx_u + 18), @cx_s_배드민턴, 1032.00, 1067.63, 35.63, 0.00, 35.63, NULL, 1.0, 0.4433, 1072.01, 1032.42, 'ELO-K32-V2', '2026-10-28 20:00:00'
       UNION ALL SELECT (@cx_sm + 62), (@cx_p + 364), (@cx_u + 111), @cx_s_배드민턴, 1117.02, 1081.39, -35.63, 0.00, -35.63, NULL, 0.0, 0.5567, 1072.01, 1032.42, 'ELO-K32-V2', '2026-10-28 20:00:00'
       UNION ALL SELECT (@cx_sm + 62), (@cx_p + 363), (@cx_u + 112), @cx_s_배드민턴, 1026.99, 991.36, -35.63, 0.00, -35.63, NULL, 0.0, 0.5567, 1072.01, 1032.42, 'ELO-K32-V2', '2026-10-28 20:00:00'
       UNION ALL SELECT (@cx_sm + 63), (@cx_p + 367), (@cx_u + 66), @cx_s_테니스, 1022.70, 1007.39, -15.31, 0.00, -15.31, NULL, 0.0, 0.4785, 1027.35, 1042.30, 'ELO-K32-V2', '2026-10-29 22:00:00'
       UNION ALL SELECT (@cx_sm + 63), (@cx_p + 368), (@cx_u + 68), @cx_s_테니스, 1032.00, 1001.38, -30.62, 0.00, -30.62, NULL, 0.0, 0.4785, 1027.35, 1042.30, 'ELO-K32-V2', '2026-10-29 22:00:00'
       UNION ALL SELECT (@cx_sm + 63), (@cx_p + 370), (@cx_u + 115), @cx_s_테니스, 1053.81, 1084.43, 30.62, 0.00, 30.62, NULL, 1.0, 0.5215, 1027.35, 1042.30, 'ELO-K32-V2', '2026-10-29 22:00:00'
       UNION ALL SELECT (@cx_sm + 63), (@cx_p + 369), (@cx_u + 116), @cx_s_테니스, 1030.78, 1061.40, 30.62, 0.00, 30.62, NULL, 1.0, 0.5215, 1027.35, 1042.30, 'ELO-K32-V2', '2026-10-29 22:00:00'
       UNION ALL SELECT (@cx_sm + 64), (@cx_p + 377), (@cx_u + 81), @cx_s_축구, 1082.32, 1063.52, -18.80, 0.00, -18.80, NULL, 0.0, 0.5876, 1060.04, 998.50, 'ELO-K32-V2', '2026-10-30 21:00:00'
       UNION ALL SELECT (@cx_sm + 64), (@cx_p + 380), (@cx_u + 82), @cx_s_축구, 1082.32, 1063.52, -18.80, 0.00, -18.80, NULL, 0.0, 0.5876, 1060.04, 998.50, 'ELO-K32-V2', '2026-10-30 21:00:00'
       UNION ALL SELECT (@cx_sm + 64), (@cx_p + 379), (@cx_u + 83), @cx_s_축구, 1082.32, 1063.52, -18.80, 0.00, -18.80, NULL, 0.0, 0.5876, 1060.04, 998.50, 'ELO-K32-V2', '2026-10-30 21:00:00'
       UNION ALL SELECT (@cx_sm + 64), (@cx_p + 378), (@cx_u + 84), @cx_s_축구, 992.43, 954.82, -37.61, 0.00, -37.61, NULL, 0.0, 0.5876, 1060.04, 998.50, 'ELO-K32-V2', '2026-10-30 21:00:00'
       UNION ALL SELECT (@cx_sm + 64), (@cx_p + 376), (@cx_u + 85), @cx_s_축구, 1060.82, 1023.21, -37.61, 0.00, -37.61, NULL, 0.0, 0.5876, 1060.04, 998.50, 'ELO-K32-V2', '2026-10-30 21:00:00'
       UNION ALL SELECT (@cx_sm + 64), (@cx_p + 372), (@cx_u + 88), @cx_s_축구, 998.13, 1035.74, 37.61, 0.00, 37.61, NULL, 1.0, 0.4124, 1060.04, 998.50, 'ELO-K32-V2', '2026-10-30 21:00:00'
       UNION ALL SELECT (@cx_sm + 64), (@cx_p + 373), (@cx_u + 89), @cx_s_축구, 998.13, 1035.74, 37.61, 0.00, 37.61, NULL, 1.0, 0.4124, 1060.04, 998.50, 'ELO-K32-V2', '2026-10-30 21:00:00'
       UNION ALL SELECT (@cx_sm + 64), (@cx_p + 374), (@cx_u + 90), @cx_s_축구, 998.13, 1035.74, 37.61, 0.00, 37.61, NULL, 1.0, 0.4124, 1060.04, 998.50, 'ELO-K32-V2', '2026-10-30 21:00:00'
       UNION ALL SELECT (@cx_sm + 64), (@cx_p + 375), (@cx_u + 92), @cx_s_축구, 994.06, 1031.67, 37.61, 0.00, 37.61, NULL, 1.0, 0.4124, 1060.04, 998.50, 'ELO-K32-V2', '2026-10-30 21:00:00'
       UNION ALL SELECT (@cx_sm + 64), (@cx_p + 371), (@cx_u + 93), @cx_s_축구, 1004.07, 1041.68, 37.61, 0.00, 37.61, NULL, 1.0, 0.4124, 1060.04, 998.50, 'ELO-K32-V2', '2026-10-30 21:00:00'
       UNION ALL SELECT (@cx_sm + 65), (@cx_p + 382), (@cx_u + 62), @cx_s_족구, 1037.39, 1030.70, -6.69, 0.00, -6.69, NULL, 0.5, 0.6046, 1036.90, 963.10, 'ELO-K32-V2', '2026-11-01 11:00:00'
       UNION ALL SELECT (@cx_sm + 65), (@cx_p + 381), (@cx_u + 63), @cx_s_족구, 1037.39, 1030.70, -6.69, 0.00, -6.69, NULL, 0.5, 0.6046, 1036.90, 963.10, 'ELO-K32-V2', '2026-11-01 11:00:00'
       UNION ALL SELECT (@cx_sm + 65), (@cx_p + 383), (@cx_u + 64), @cx_s_족구, 1035.91, 1029.22, -6.69, 0.00, -6.69, NULL, 0.5, 0.6046, 1036.90, 963.10, 'ELO-K32-V2', '2026-11-01 11:00:00'
       UNION ALL SELECT (@cx_sm + 65), (@cx_p + 385), (@cx_u + 107), @cx_s_족구, 962.61, 969.30, 6.69, 0.00, 6.69, NULL, 0.5, 0.3954, 1036.90, 963.10, 'ELO-K32-V2', '2026-11-01 11:00:00'
       UNION ALL SELECT (@cx_sm + 65), (@cx_p + 384), (@cx_u + 108), @cx_s_족구, 962.61, 969.30, 6.69, 0.00, 6.69, NULL, 0.5, 0.3954, 1036.90, 963.10, 'ELO-K32-V2', '2026-11-01 11:00:00'
       UNION ALL SELECT (@cx_sm + 65), (@cx_p + 386), (@cx_u + 110), @cx_s_족구, 964.09, 970.78, 6.69, 0.00, 6.69, NULL, 0.5, 0.3954, 1036.90, 963.10, 'ELO-K32-V2', '2026-11-01 11:00:00'
       UNION ALL SELECT (@cx_sm + 66), (@cx_p + 390), (@cx_u + 95), @cx_s_농구, 1097.24, 1078.13, -19.11, 0.00, -19.11, NULL, 0.0, 0.5973, 1065.43, 996.97, 'ELO-K32-V2', '2026-11-01 20:00:00'
       UNION ALL SELECT (@cx_sm + 66), (@cx_p + 387), (@cx_u + 96), @cx_s_농구, 1097.24, 1078.13, -19.11, 0.00, -19.11, NULL, 0.0, 0.5973, 1065.43, 996.97, 'ELO-K32-V2', '2026-11-01 20:00:00'
       UNION ALL SELECT (@cx_sm + 66), (@cx_p + 389), (@cx_u + 97), @cx_s_농구, 1064.64, 1026.41, -38.23, 0.00, -38.23, NULL, 0.0, 0.5973, 1065.43, 996.97, 'ELO-K32-V2', '2026-11-01 20:00:00'
       UNION ALL SELECT (@cx_sm + 66), (@cx_p + 388), (@cx_u + 99), @cx_s_농구, 1002.61, 964.38, -38.23, 0.00, -38.23, NULL, 0.0, 0.5973, 1065.43, 996.97, 'ELO-K32-V2', '2026-11-01 20:00:00'
       UNION ALL SELECT (@cx_sm + 66), (@cx_p + 392), (@cx_u + 101), @cx_s_농구, 995.96, 1034.19, 38.23, 0.00, 38.23, NULL, 1.0, 0.4027, 1065.43, 996.97, 'ELO-K32-V2', '2026-11-01 20:00:00'
       UNION ALL SELECT (@cx_sm + 66), (@cx_p + 393), (@cx_u + 102), @cx_s_농구, 995.96, 1034.19, 38.23, 0.00, 38.23, NULL, 1.0, 0.4027, 1065.43, 996.97, 'ELO-K32-V2', '2026-11-01 20:00:00'
       UNION ALL SELECT (@cx_sm + 66), (@cx_p + 394), (@cx_u + 103), @cx_s_농구, 997.39, 1035.62, 38.23, 0.00, 38.23, NULL, 1.0, 0.4027, 1065.43, 996.97, 'ELO-K32-V2', '2026-11-01 20:00:00'
       UNION ALL SELECT (@cx_sm + 66), (@cx_p + 391), (@cx_u + 106), @cx_s_농구, 998.57, 1036.80, 38.23, 0.00, 38.23, NULL, 1.0, 0.4027, 1065.43, 996.97, 'ELO-K32-V2', '2026-11-01 20:00:00'
       UNION ALL SELECT (@cx_sm + 67), (@cx_p + 395), (@cx_u + 23), @cx_s_테니스, 953.25, 940.01, -13.24, 0.00, -13.24, NULL, 0.0, 0.4137, 962.72, 1023.31, 'ELO-K32-V2', '2026-11-06 21:30:00'
       UNION ALL SELECT (@cx_sm + 67), (@cx_p + 396), (@cx_u + 25), @cx_s_테니스, 972.19, 945.71, -26.48, 0.00, -26.48, NULL, 0.0, 0.4137, 962.72, 1023.31, 'ELO-K32-V2', '2026-11-06 21:30:00'
       UNION ALL SELECT (@cx_sm + 67), (@cx_p + 397), (@cx_u + 115), @cx_s_테니스, 1084.43, 1097.67, 13.24, 0.00, 13.24, NULL, 1.0, 0.5863, 962.72, 1023.31, 'ELO-K32-V2', '2026-11-06 21:30:00'
       UNION ALL SELECT (@cx_sm + 67), (@cx_p + 398), (@cx_u + 118), @cx_s_테니스, 962.19, 988.67, 26.48, 0.00, 26.48, NULL, 1.0, 0.5863, 962.72, 1023.31, 'ELO-K32-V2', '2026-11-06 21:30:00'
       UNION ALL SELECT (@cx_sm + 68), (@cx_p + 401), (@cx_u + 70), @cx_s_배드민턴, 916.62, 905.66, -10.96, 0.00, -10.96, NULL, 0.0, 0.3424, 1055.71, 942.31, 'ELO-K32-V2', '2026-11-07 20:00:00'
       UNION ALL SELECT (@cx_sm + 68), (@cx_p + 402), (@cx_u + 73), @cx_s_배드민턴, 968.00, 946.09, -21.91, 0.00, -21.91, NULL, 0.0, 0.3424, 1055.71, 942.31, 'ELO-K32-V2', '2026-11-07 20:00:00'
       UNION ALL SELECT (@cx_sm + 68), (@cx_p + 400), (@cx_u + 111), @cx_s_배드민턴, 1081.39, 1092.35, 10.96, 0.00, 10.96, NULL, 1.0, 0.6576, 1055.71, 942.31, 'ELO-K32-V2', '2026-11-07 20:00:00'
       UNION ALL SELECT (@cx_sm + 68), (@cx_p + 399), (@cx_u + 114), @cx_s_배드민턴, 1030.02, 1051.93, 21.91, 0.00, 21.91, NULL, 1.0, 0.6576, 1055.71, 942.31, 'ELO-K32-V2', '2026-11-07 20:00:00'
       UNION ALL SELECT (@cx_sm + 69), (@cx_p + 403), (@cx_u + 31), @cx_s_농구, 931.70, 951.19, 19.49, 0.00, 19.49, NULL, 1.0, 0.3908, 938.97, 1016.09, 'ELO-K32-V2', '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_sm + 69), (@cx_p + 404), (@cx_u + 32), @cx_s_농구, 931.70, 951.19, 19.49, 0.00, 19.49, NULL, 1.0, 0.3908, 938.97, 1016.09, 'ELO-K32-V2', '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_sm + 69), (@cx_p + 405), (@cx_u + 34), @cx_s_농구, 946.53, 985.52, 38.99, 0.00, 38.99, NULL, 1.0, 0.3908, 938.97, 1016.09, 'ELO-K32-V2', '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_sm + 69), (@cx_p + 406), (@cx_u + 36), @cx_s_농구, 945.96, 984.95, 38.99, 0.00, 38.99, NULL, 1.0, 0.3908, 938.97, 1016.09, 'ELO-K32-V2', '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_sm + 69), (@cx_p + 410), (@cx_u + 101), @cx_s_농구, 1034.19, 1014.70, -19.49, 0.00, -19.49, NULL, 0.0, 0.6092, 938.97, 1016.09, 'ELO-K32-V2', '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_sm + 69), (@cx_p + 409), (@cx_u + 102), @cx_s_농구, 1034.19, 1014.70, -19.49, 0.00, -19.49, NULL, 0.0, 0.6092, 938.97, 1016.09, 'ELO-K32-V2', '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_sm + 69), (@cx_p + 408), (@cx_u + 104), @cx_s_농구, 992.14, 953.15, -38.99, 0.00, -38.99, NULL, 0.0, 0.6092, 938.97, 1016.09, 'ELO-K32-V2', '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_sm + 69), (@cx_p + 407), (@cx_u + 105), @cx_s_농구, 1003.82, 964.83, -38.99, 0.00, -38.99, NULL, 0.0, 0.6092, 938.97, 1016.09, 'ELO-K32-V2', '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_sm + 70), (@cx_p + 411), (@cx_u + 62), @cx_s_족구, 1030.70, 994.02, -36.68, 0.00, -36.68, NULL, 0.0, 0.5731, 969.79, 1020.96, 'ELO-K32-V2', '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_sm + 70), (@cx_p + 413), (@cx_u + 63), @cx_s_족구, 1030.70, 994.02, -36.68, 0.00, -36.68, NULL, 0.0, 0.5731, 969.79, 1020.96, 'ELO-K32-V2', '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_sm + 70), (@cx_p + 412), (@cx_u + 65), @cx_s_족구, 1001.48, 964.80, -36.68, 0.00, -36.68, NULL, 0.0, 0.5731, 969.79, 1020.96, 'ELO-K32-V2', '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_sm + 70), (@cx_p + 415), (@cx_u + 107), @cx_s_족구, 969.30, 1005.98, 36.68, 0.00, 36.68, NULL, 1.0, 0.4269, 969.79, 1020.96, 'ELO-K32-V2', '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_sm + 70), (@cx_p + 416), (@cx_u + 108), @cx_s_족구, 969.30, 1005.98, 36.68, 0.00, 36.68, NULL, 1.0, 0.4269, 969.79, 1020.96, 'ELO-K32-V2', '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_sm + 70), (@cx_p + 414), (@cx_u + 110), @cx_s_족구, 970.78, 1007.46, 36.68, 0.00, 36.68, NULL, 1.0, 0.4269, 969.79, 1020.96, 'ELO-K32-V2', '2026-11-08 12:00:00'
       UNION ALL SELECT (@cx_sm + 71), (@cx_p + 423), (@cx_u + 74), @cx_s_축구, 967.88, 970.35, 2.47, 0.00, 2.47, NULL, 0.5, 0.4228, 1021.07, 966.96, 'ELO-K32-V2', '2026-11-08 16:00:00'
       UNION ALL SELECT (@cx_sm + 71), (@cx_p + 422), (@cx_u + 75), @cx_s_축구, 967.88, 970.35, 2.47, 0.00, 2.47, NULL, 0.5, 0.4228, 1021.07, 966.96, 'ELO-K32-V2', '2026-11-08 16:00:00'
       UNION ALL SELECT (@cx_sm + 71), (@cx_p + 424), (@cx_u + 76), @cx_s_축구, 967.88, 970.35, 2.47, 0.00, 2.47, NULL, 0.5, 0.4228, 1021.07, 966.96, 'ELO-K32-V2', '2026-11-08 16:00:00'
       UNION ALL SELECT (@cx_sm + 71), (@cx_p + 426), (@cx_u + 78), @cx_s_축구, 932.50, 937.44, 4.94, 0.00, 4.94, NULL, 0.5, 0.4228, 1021.07, 966.96, 'ELO-K32-V2', '2026-11-08 16:00:00'
       UNION ALL SELECT (@cx_sm + 71), (@cx_p + 425), (@cx_u + 79), @cx_s_축구, 998.68, 1003.62, 4.94, 0.00, 4.94, NULL, 0.5, 0.4228, 1021.07, 966.96, 'ELO-K32-V2', '2026-11-08 16:00:00'
       UNION ALL SELECT (@cx_sm + 71), (@cx_p + 420), (@cx_u + 88), @cx_s_축구, 1035.74, 1033.27, -2.47, 0.00, -2.47, NULL, 0.5, 0.5772, 1021.07, 966.96, 'ELO-K32-V2', '2026-11-08 16:00:00'
       UNION ALL SELECT (@cx_sm + 71), (@cx_p + 417), (@cx_u + 89), @cx_s_축구, 1035.74, 1033.27, -2.47, 0.00, -2.47, NULL, 0.5, 0.5772, 1021.07, 966.96, 'ELO-K32-V2', '2026-11-08 16:00:00'
       UNION ALL SELECT (@cx_sm + 71), (@cx_p + 421), (@cx_u + 90), @cx_s_축구, 1035.74, 1033.27, -2.47, 0.00, -2.47, NULL, 0.5, 0.5772, 1021.07, 966.96, 'ELO-K32-V2', '2026-11-08 16:00:00'
       UNION ALL SELECT (@cx_sm + 71), (@cx_p + 419), (@cx_u + 91), @cx_s_축구, 994.06, 989.12, -4.94, 0.00, -4.94, NULL, 0.5, 0.5772, 1021.07, 966.96, 'ELO-K32-V2', '2026-11-08 16:00:00'
       UNION ALL SELECT (@cx_sm + 71), (@cx_p + 418), (@cx_u + 94), @cx_s_축구, 1004.07, 999.13, -4.94, 0.00, -4.94, NULL, 0.5, 0.5772, 1021.07, 966.96, 'ELO-K32-V2', '2026-11-08 16:00:00'
) v
 WHERE @cx_ready = 1;

COMMIT;

-- 확인 1: 넣은 행 수
SELECT (SELECT COUNT(*) FROM app_user u WHERE u.email LIKE 'club-user-___@spoit.test') AS new_users,
       (SELECT COUNT(*) FROM club_member m JOIN app_user u ON u.user_id = m.user_id WHERE u.email LIKE 'club-user-___@spoit.test' AND m.left_at IS NULL) AS new_members,
       (SELECT COUNT(DISTINCT ma.match_id) FROM match_application ma JOIN app_user u ON u.user_id = ma.applicant_user_id
          JOIN social_match sm ON sm.match_id = ma.match_id WHERE u.email LIKE 'club-user-___@spoit.test' AND ma.status = '확정' AND sm.status = '종료') AS past_matches,
       (SELECT COUNT(*) FROM sport_rating_history h JOIN app_user u ON u.user_id = h.user_id WHERE u.email LIKE 'club-user-___@spoit.test') AS history_rows,
       (SELECT COUNT(*) FROM match_application WHERE receipt_no IN ('RCPT-20261112-SNXM525S', 'RCPT-20261112-8BRQ6RTT', 'RCPT-20261112-VZBXLHXC', 'RCPT-20261112-7HL8ZJQU', 'RCPT-20261112-48Y2V5HM', 'RCPT-20261112-QEC2JNWG', 'RCPT-20261112-MKFAWJUK', 'RCPT-20261112-3VL668KG', 'RCPT-20261112-S9XHNPLE', 'RCPT-20261112-U2HEFXTX', 'RCPT-20261112-U2MPK3L6', 'RCPT-20261112-XDR4QBH2', 'RCPT-20261112-GNX36CVF', 'RCPT-20261112-UXRWTABZ', 'RCPT-20261112-8WTJJD2P', 'RCPT-20261112-LV4V8DKG', 'RCPT-20261112-HVJ7SEV9', 'RCPT-20261112-A3BK5TGM', 'RCPT-20261112-U9C88AQ7', 'RCPT-20261112-Y4FSXJXZ', 'RCPT-20261112-GQTRDC94', 'RCPT-20261112-8CGN2EAB', 'RCPT-20261112-CZATMTA3', 'RCPT-20261112-GSRNH8ZH', 'RCPT-20261112-3JEKVU2W', 'RCPT-20261112-4D2X7XXY', 'RCPT-20261112-UDUDEXPQ', 'RCPT-20261112-26D6V3UN', 'RCPT-20261112-D6T3HUQ2', 'RCPT-20261112-NG7MJZBS', 'RCPT-20261112-JX3HZG4W', 'RCPT-20261112-R7WY9WEA', 'RCPT-20261112-PSPX6GC9', 'RCPT-20261112-26F7HWXT', 'RCPT-20261112-J4REXXRD', 'RCPT-20261112-CAVRY2FG', 'RCPT-20261112-JZADVN35', 'RCPT-20261112-JHLVFXPZ', 'RCPT-20261112-S9WC5L5W', 'RCPT-20261112-GEVNWWD7', 'RCPT-20261112-F67QE43D', 'RCPT-20261112-QD9PFLGL', 'RCPT-20261112-R2XNUBW5', 'RCPT-20261112-TF75SRAF', 'RCPT-20261112-GXHA95GR', 'RCPT-20261112-NVTMN9KS', 'RCPT-20261112-E96EPRWN', 'RCPT-20261112-3C3C92GE', 'RCPT-20261112-HXWPHL6N') AND status = '대기' AND group_application_id IS NOT NULL) AS team_applications,
       (SELECT COUNT(*) FROM club WHERE club_name IN ('주말 풋살 클럽', '노원 농구회', '송파 셔틀콕', '마포 테니스 클럽', '서초 나이트 테니스', '성동 셔틀콕 모임', '광진 배드민턴회', '서초 테니스회', '영등포 테니스 클럽', '송파 농구 동호회', '송파 배드민턴회', '은평 풋살 클럽', '광진 테니스회', '강서 배드민턴회', '영등포 농구클럽', '노원 풋살회', '관악 족구회', '강서 테니스회', '송파 배드민턴', '잠실 농구회', '뚝섬 FC', '망원 풋살 크루', '잠실 풋살 클럽', '반포 농구단', '광나루 바스켓', '한강 족구회', '뚝섬 셔틀콕', '마포 랠리 테니스') AND deleted_at IS NULL AND intro LIKE '%[활동 안내]%') AS new_intros;
-- 기대값: new_users 118, new_members 118, past_matches 71, history_rows 426, team_applications 49, new_intros 28

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
