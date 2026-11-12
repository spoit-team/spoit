-- =====================================================================
-- 발표 시연 데이터: 공공 체육시설 매칭방 189개, 데모 회원 101명, 동호회 8개
-- 이 파일은 scripts/demo-match-data.py가 만든다. 직접 고치지 말고 스크립트를 고쳐 다시 만든다.
-- 대상: 20261110-seoul-public-facilities.sql과 20261109-evaluation-items.sql을 적용한 DB (로컬·운영)
-- 내용
--   * 데모 회원 demo-001~100@spoit.test, 발표자 demo-presenter@spoit.test (비밀번호 모두 spoit1234)
--   * 11/12~11/30 매일 매칭방 9개 (정원이 찬 방은 시작 시각에 자동 배정되어 경기 후 결과·평가 입력 가능)
--   * 11/1~11/9 지난 경기 18개: 결과·평가까지 넣고 rating_status WAITING으로 둔다.
--     앱 스케줄러가 결과 마감(종료 24시간 후) 처리로 레이팅을 반영하고 종료로 바꾼다.
--   * 매칭방 사진: 사진이 없는 모든 매칭방과 seed의 /uploads/matches/seed/ 사진(git에 없어 운영에서 열리지 않음)을
--     종목별 정적 사진 /images/matches/종목.jpg로 채운다. 직접 올린 사진은 바꾸지 않는다.
-- 원칙
--   * 한 트랜잭션. 사전 확인에 실패하면 멈추고 아무 행도 넣지 않는다.
--   * demo-001@spoit.test가 이미 있으면 이미 적용된 것으로 보고 데모 행은 넣지 않는다(오류 없음). 사진 채우기는 다시 실행해도 결과가 같다.
--   * 시설·종목·평가 항목·담당자는 이름으로 찾는다(DB마다 번호가 다르다). 새 행 번호는 각 테이블의 현재 최대 번호 다음부터 쓴다.
-- =====================================================================

SET NAMES utf8mb4;
START TRANSACTION;

-- 0. 이름으로 번호 찾기
SET @ds_futsal = (SELECT MAX(sport_id) FROM sport WHERE name = '축구+풋살');
SET @ds_tennis = (SELECT MAX(sport_id) FROM sport WHERE name = '테니스');
SET @ds_badminton = (SELECT MAX(sport_id) FROM sport WHERE name = '배드민턴');
SET @ds_basketball = (SELECT MAX(sport_id) FROM sport WHERE name = '농구');
SET @ds_jokgu = (SELECT MAX(sport_id) FROM sport WHERE name = '족구');
SET @df1 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '송파구' AND f.name = '잠실종합운동장 풋살경기장' AND f.deleted_at IS NULL);
SET @df1_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df1 AND u.email LIKE 'facility-__@spoit.test');
SET @df2 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '동작구' AND f.name = '보라매공원 인조잔디축구장' AND f.deleted_at IS NULL);
SET @df2_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df2 AND u.email LIKE 'facility-__@spoit.test');
SET @df3 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '금천구' AND f.name = '독산테니스장' AND f.deleted_at IS NULL);
SET @df3_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df3 AND u.email LIKE 'facility-__@spoit.test');
SET @df4 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '강남구' AND f.name = '마루공원' AND f.deleted_at IS NULL);
SET @df4_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df4 AND u.email LIKE 'facility-__@spoit.test');
SET @df5 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '광진구' AND f.name = '뚝섬한강공원 배드민턴장 1' AND f.deleted_at IS NULL);
SET @df5_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df5 AND u.email LIKE 'facility-__@spoit.test');
SET @df6 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '성동구' AND f.name = '중랑물재생센터 배드민턴장' AND f.deleted_at IS NULL);
SET @df6_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df6 AND u.email LIKE 'facility-__@spoit.test');
SET @df7 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '서초구' AND f.name = '반포한강공원 농구장(1,2)' AND f.deleted_at IS NULL);
SET @df7_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df7 AND u.email LIKE 'facility-__@spoit.test');
SET @df8 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '용산구' AND f.name = '이촌농구장(인라인장옆)' AND f.deleted_at IS NULL);
SET @df8_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df8 AND u.email LIKE 'facility-__@spoit.test');
SET @df9 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '마포구' AND f.name = '난지한강공원 족구장' AND f.deleted_at IS NULL);
SET @df9_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df9 AND u.email LIKE 'facility-__@spoit.test');
SET @df10 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '동대문구' AND f.name = '한국외대 인조잔디 구장' AND f.deleted_at IS NULL);
SET @df10_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df10 AND u.email LIKE 'facility-__@spoit.test');
SET @df11 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '구로구' AND f.name = '고척스카이돔' AND f.deleted_at IS NULL);
SET @df11_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df11 AND u.email LIKE 'facility-__@spoit.test');
SET @df12 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '종로구' AND f.name = '삼청테니스장' AND f.deleted_at IS NULL);
SET @df12_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df12 AND u.email LIKE 'facility-__@spoit.test');
SET @df13 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '도봉구' AND f.name = '다락원체육공원' AND f.deleted_at IS NULL);
SET @df13_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df13 AND u.email LIKE 'facility-__@spoit.test');
SET @df14 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '광진구' AND f.name = '뚝섬한강공원 배드민턴장 2' AND f.deleted_at IS NULL);
SET @df14_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df14 AND u.email LIKE 'facility-__@spoit.test');
SET @df15 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '강동구' AND f.name = '광나루한강공원 농구장 1, 2' AND f.deleted_at IS NULL);
SET @df15_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df15 AND u.email LIKE 'facility-__@spoit.test');
SET @df16 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '송파구' AND f.name = '잠실 농구장' AND f.deleted_at IS NULL);
SET @df16_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df16 AND u.email LIKE 'facility-__@spoit.test');
SET @df17 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '강서구' AND f.name = '강서한강공원 족구장(1,2)' AND f.deleted_at IS NULL);
SET @df17_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df17 AND u.email LIKE 'facility-__@spoit.test');
SET @df18 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '영등포구' AND f.name = '영등포공원 풋살경기장' AND f.deleted_at IS NULL);
SET @df18_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df18 AND u.email LIKE 'facility-__@spoit.test');
SET @df19 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '양천구' AND f.name = '서서울호수공원' AND f.deleted_at IS NULL);
SET @df19_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df19 AND u.email LIKE 'facility-__@spoit.test');
SET @df20 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '서대문구' AND f.name = '가좌테니스장' AND f.deleted_at IS NULL);
SET @df20_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df20 AND u.email LIKE 'facility-__@spoit.test');
SET @df21 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '관악구' AND f.name = '선우공원' AND f.deleted_at IS NULL);
SET @df21_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df21 AND u.email LIKE 'facility-__@spoit.test');
SET @df22 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '동작구' AND f.name = '현충실내배드민턴장' AND f.deleted_at IS NULL);
SET @df22_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df22 AND u.email LIKE 'facility-__@spoit.test');
SET @df23 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '마포구' AND f.name = '난지농구장' AND f.deleted_at IS NULL);
SET @df23_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df23 AND u.email LIKE 'facility-__@spoit.test');
SET @df24 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '성동구' AND f.name = '이촌농구장(금호나들목)' AND f.deleted_at IS NULL);
SET @df24_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df24 AND u.email LIKE 'facility-__@spoit.test');
SET @df25 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '용산구' AND f.name = '이촌족구장1(한남)' AND f.deleted_at IS NULL);
SET @df25_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df25 AND u.email LIKE 'facility-__@spoit.test');
SET @df26 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '서초구' AND f.name = '반포한강공원 축구장' AND f.deleted_at IS NULL);
SET @df26_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df26 AND u.email LIKE 'facility-__@spoit.test');
SET @df27 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '강동구' AND f.name = '광나루한강공원 축구장 2' AND f.deleted_at IS NULL);
SET @df27_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df27 AND u.email LIKE 'facility-__@spoit.test');
SET @df28 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '성북구' AND f.name = '정릉테니스장' AND f.deleted_at IS NULL);
SET @df28_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df28 AND u.email LIKE 'facility-__@spoit.test');
SET @df29 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '중구' AND f.name = '장충테니스장' AND f.deleted_at IS NULL);
SET @df29_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df29 AND u.email LIKE 'facility-__@spoit.test');
SET @df30 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '광진구' AND f.name = '뚝섬한강공원 배드민턴장 3' AND f.deleted_at IS NULL);
SET @df30_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df30 AND u.email LIKE 'facility-__@spoit.test');
SET @df31 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '성동구' AND f.name = '서울숲 배드민턴장2' AND f.deleted_at IS NULL);
SET @df31_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df31 AND u.email LIKE 'facility-__@spoit.test');
SET @df32 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '광진구' AND f.name = '뚝섬한강공원 농구장 1' AND f.deleted_at IS NULL);
SET @df32_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df32 AND u.email LIKE 'facility-__@spoit.test');
SET @df33 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '서초구' AND f.name = '잠원한강공원 농구장(1,2,3,4)' AND f.deleted_at IS NULL);
SET @df33_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df33 AND u.email LIKE 'facility-__@spoit.test');
SET @df34 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '강남구' AND f.name = '에코파크' AND f.deleted_at IS NULL);
SET @df34_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df34 AND u.email LIKE 'facility-__@spoit.test');
SET @df35 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '강서구' AND f.name = '서남물재생센터' AND f.deleted_at IS NULL);
SET @df35_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df35 AND u.email LIKE 'facility-__@spoit.test');
SET @df36 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '용산구' AND f.name = '효창운동장(축구장,육상트랙)' AND f.deleted_at IS NULL);
SET @df36_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df36 AND u.email LIKE 'facility-__@spoit.test');
SET @df37 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '영등포구' AND f.name = '남부도로사업소 테니스장' AND f.deleted_at IS NULL);
SET @df37_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df37 AND u.email LIKE 'facility-__@spoit.test');
SET @df38 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '구로구' AND f.name = '계남근린공원' AND f.deleted_at IS NULL);
SET @df38_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df38 AND u.email LIKE 'facility-__@spoit.test');
SET @df39 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '송파구' AND f.name = '광나루 농구장(올림픽대로 방향)' AND f.deleted_at IS NULL);
SET @df39_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df39 AND u.email LIKE 'facility-__@spoit.test');
SET @df40 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '마포구' AND f.name = '망원한강공원 농구장(1,2)' AND f.deleted_at IS NULL);
SET @df40_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df40 AND u.email LIKE 'facility-__@spoit.test');
SET @df41 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '강동구' AND f.name = '광나루한강공원 족구장3(안내센터상류)' AND f.deleted_at IS NULL);
SET @df41_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df41 AND u.email LIKE 'facility-__@spoit.test');
SET @df42 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '마포구' AND f.name = '서울월드컵경기장 풋살구장' AND f.deleted_at IS NULL);
SET @df42_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df42 AND u.email LIKE 'facility-__@spoit.test');
SET @df43 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '서초구' AND f.name = '잠원한강테니스장' AND f.deleted_at IS NULL);
SET @df43_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df43 AND u.email LIKE 'facility-__@spoit.test');
SET @df44 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '용산구' AND f.name = '이촌농구(청소년광장)' AND f.deleted_at IS NULL);
SET @df44_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df44 AND u.email LIKE 'facility-__@spoit.test');
SET @df45 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '송파구' AND f.name = '잠실한강공원 족구장' AND f.deleted_at IS NULL);
SET @df45_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df45 AND u.email LIKE 'facility-__@spoit.test');
SET @df46 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '서대문구' AND f.name = '현저테니스장' AND f.deleted_at IS NULL);
SET @df46_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df46 AND u.email LIKE 'facility-__@spoit.test');
SET @df47 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '동작구' AND f.name = '보라매공원' AND f.deleted_at IS NULL);
SET @df47_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df47 AND u.email LIKE 'facility-__@spoit.test');
SET @df48 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '서초구' AND f.name = '반포 농구장(안내센터 앞)' AND f.deleted_at IS NULL);
SET @df48_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df48 AND u.email LIKE 'facility-__@spoit.test');
SET @df49 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '동작구' AND f.name = '노들나루공원(노들배수지공원)' AND f.deleted_at IS NULL);
SET @df49_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df49 AND u.email LIKE 'facility-__@spoit.test');
SET @df50 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '구로구' AND f.name = '안양천 체육시설 풋살경기장(구일역 하부)' AND f.deleted_at IS NULL);
SET @df50_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df50 AND u.email LIKE 'facility-__@spoit.test');
SET @df51 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '성북구' AND f.name = '월곡테니스장' AND f.deleted_at IS NULL);
SET @df51_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df51 AND u.email LIKE 'facility-__@spoit.test');
SET @df52 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '관악구' AND f.name = '난우공원 테니스장' AND f.deleted_at IS NULL);
SET @df52_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df52 AND u.email LIKE 'facility-__@spoit.test');
SET @df53 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '광진구' AND f.name = '뚝섬한강공원 농구장 2' AND f.deleted_at IS NULL);
SET @df53_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df53 AND u.email LIKE 'facility-__@spoit.test');
SET @df54 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '영등포구' AND f.name = '양화한강공원 축구장' AND f.deleted_at IS NULL);
SET @df54_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df54 AND u.email LIKE 'facility-__@spoit.test');
SET @df55 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '양천구' AND f.name = '서울에너지공사 목동 테니스장 1면' AND f.deleted_at IS NULL);
SET @df55_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df55 AND u.email LIKE 'facility-__@spoit.test');
SET @df56 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '마포구' AND f.name = '망원족구' AND f.deleted_at IS NULL);
SET @df56_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df56 AND u.email LIKE 'facility-__@spoit.test');
SET @df57 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '서초구' AND f.name = '양재근린공원 축구장' AND f.deleted_at IS NULL);
SET @df57_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df57 AND u.email LIKE 'facility-__@spoit.test');
SET @df58 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '구로구' AND f.name = '계남근린공원 인조잔디축구장' AND f.deleted_at IS NULL);
SET @df58_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df58 AND u.email LIKE 'facility-__@spoit.test');
SET @df59 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '강동구' AND f.name = '광나루 한강공원 테니스장' AND f.deleted_at IS NULL);
SET @df59_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df59 AND u.email LIKE 'facility-__@spoit.test');
SET @df60 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '용산구' AND f.name = '이촌족구장2,3(보광나들목1,2)' AND f.deleted_at IS NULL);
SET @df60_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df60 AND u.email LIKE 'facility-__@spoit.test');
SET @df61 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '송파구' AND f.name = '잠실종합운동장 제2풋살경기장' AND f.deleted_at IS NULL);
SET @df61_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df61 AND u.email LIKE 'facility-__@spoit.test');
SET @df62 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '마포구' AND f.name = '망원유수지 풋살장' AND f.deleted_at IS NULL);
SET @df62_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df62 AND u.email LIKE 'facility-__@spoit.test');
SET @df63 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '서대문구' AND f.name = '홍은테니스장' AND f.deleted_at IS NULL);
SET @df63_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df63 AND u.email LIKE 'facility-__@spoit.test');
SET @df64 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '강동구' AND f.name = '광나루한강공원 족구장1(안내센터상류)' AND f.deleted_at IS NULL);
SET @df64_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df64 AND u.email LIKE 'facility-__@spoit.test');
SET @df65 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '구로구' AND f.name = '신도림테니스장' AND f.deleted_at IS NULL);
SET @df65_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df65 AND u.email LIKE 'facility-__@spoit.test');
SET @df66 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '서초구' AND f.name = '방배배수지체육공원' AND f.deleted_at IS NULL);
SET @df66_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df66 AND u.email LIKE 'facility-__@spoit.test');
SET @df67 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '강동구' AND f.name = '광나루한강공원 족구장2(안내센터상류)' AND f.deleted_at IS NULL);
SET @df67_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df67 AND u.email LIKE 'facility-__@spoit.test');
SET @df68 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '강동구' AND f.name = '광나루한강공원 축구장 3' AND f.deleted_at IS NULL);
SET @df68_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df68 AND u.email LIKE 'facility-__@spoit.test');
SET @df69 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '양천구' AND f.name = '서울에너지공사 목동 테니스장 2면' AND f.deleted_at IS NULL);
SET @df69_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df69 AND u.email LIKE 'facility-__@spoit.test');
SET @df70 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '마포구' AND f.name = '망원한강테니스장' AND f.deleted_at IS NULL);
SET @df70_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df70 AND u.email LIKE 'facility-__@spoit.test');
SET @df71 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '강남구' AND f.name = '동부도로사업소 테니스장' AND f.deleted_at IS NULL);
SET @df71_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df71 AND u.email LIKE 'facility-__@spoit.test');
SET @df72 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '용산구' AND f.name = '이촌족구장4,5(한강대교 북단 상류 1,2)' AND f.deleted_at IS NULL);
SET @df72_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df72 AND u.email LIKE 'facility-__@spoit.test');
SET @df73 = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = '강남구' AND f.name = '탄천센터' AND f.deleted_at IS NULL);
SET @df73_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = @df73 AND u.email LIKE 'facility-__@spoit.test');
SET @dr_강남구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '강남구');
SET @dr_강동구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '강동구');
SET @dr_강서구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '강서구');
SET @dr_관악구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '관악구');
SET @dr_광진구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '광진구');
SET @dr_구로구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '구로구');
SET @dr_금천구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '금천구');
SET @dr_도봉구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '도봉구');
SET @dr_동대문구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '동대문구');
SET @dr_동작구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '동작구');
SET @dr_마포구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '마포구');
SET @dr_서대문구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '서대문구');
SET @dr_서초구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '서초구');
SET @dr_성동구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '성동구');
SET @dr_성북구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '성북구');
SET @dr_송파구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '송파구');
SET @dr_양천구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '양천구');
SET @dr_영등포구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '영등포구');
SET @dr_용산구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '용산구');
SET @dr_종로구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '종로구');
SET @dr_중구 = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = '중구');
SET @di_futsal_0 = (SELECT MAX(item_id) FROM sport_evaluation_item WHERE item_code = 'SOCCER_TECH' AND is_active = TRUE AND sport_id = @ds_futsal);
SET @di_futsal_1 = (SELECT MAX(item_id) FROM sport_evaluation_item WHERE item_code = 'FUTSAL_PASS' AND is_active = TRUE AND sport_id = @ds_futsal);
SET @di_futsal_2 = (SELECT MAX(item_id) FROM sport_evaluation_item WHERE item_code = 'FUTSAL_POSITION' AND is_active = TRUE AND sport_id = @ds_futsal);
SET @di_tennis_0 = (SELECT MAX(item_id) FROM sport_evaluation_item WHERE item_code = 'TENNIS_SERVE' AND is_active = TRUE AND sport_id = @ds_tennis);
SET @di_tennis_1 = (SELECT MAX(item_id) FROM sport_evaluation_item WHERE item_code = 'TENNIS_RALLY' AND is_active = TRUE AND sport_id = @ds_tennis);
SET @di_tennis_2 = (SELECT MAX(item_id) FROM sport_evaluation_item WHERE item_code = 'TENNIS_POSITION' AND is_active = TRUE AND sport_id = @ds_tennis);
SET @di_badminton_0 = (SELECT MAX(item_id) FROM sport_evaluation_item WHERE item_code = 'BADMINTON_SMASH' AND is_active = TRUE AND sport_id = @ds_badminton);
SET @di_badminton_1 = (SELECT MAX(item_id) FROM sport_evaluation_item WHERE item_code = 'BADMINTON_FOOTWORK' AND is_active = TRUE AND sport_id = @ds_badminton);
SET @di_badminton_2 = (SELECT MAX(item_id) FROM sport_evaluation_item WHERE item_code = 'BADMINTON_SERVE' AND is_active = TRUE AND sport_id = @ds_badminton);
SET @di_basketball_0 = (SELECT MAX(item_id) FROM sport_evaluation_item WHERE item_code = 'BASKET_SHOOT' AND is_active = TRUE AND sport_id = @ds_basketball);
SET @di_basketball_1 = (SELECT MAX(item_id) FROM sport_evaluation_item WHERE item_code = 'BASKET_PASS' AND is_active = TRUE AND sport_id = @ds_basketball);
SET @di_basketball_2 = (SELECT MAX(item_id) FROM sport_evaluation_item WHERE item_code = 'BASKET_DEFENSE' AND is_active = TRUE AND sport_id = @ds_basketball);
SET @di_jokgu_0 = (SELECT MAX(item_id) FROM sport_evaluation_item WHERE item_code = 'JOKGU_KICK' AND is_active = TRUE AND sport_id = @ds_jokgu);
SET @di_jokgu_1 = (SELECT MAX(item_id) FROM sport_evaluation_item WHERE item_code = 'JOKGU_TOSS' AND is_active = TRUE AND sport_id = @ds_jokgu);
SET @di_jokgu_2 = (SELECT MAX(item_id) FROM sport_evaluation_item WHERE item_code = 'JOKGU_DEFENSE' AND is_active = TRUE AND sport_id = @ds_jokgu);

-- 사전 확인. 출력되는 값이 모두 1이어야 진행한다. already_applied가 1이면 이미 적용된 DB라 아무것도 넣지 않는다.
SELECT ((@ds_futsal IS NOT NULL) + (@ds_tennis IS NOT NULL) + (@ds_badminton IS NOT NULL) + (@ds_basketball IS NOT NULL) + (@ds_jokgu IS NOT NULL)) = 5, ((@df1 IS NOT NULL AND @df1_mgr IS NOT NULL) + (@df2 IS NOT NULL AND @df2_mgr IS NOT NULL) + (@df3 IS NOT NULL AND @df3_mgr IS NOT NULL) + (@df4 IS NOT NULL AND @df4_mgr IS NOT NULL) + (@df5 IS NOT NULL AND @df5_mgr IS NOT NULL) + (@df6 IS NOT NULL AND @df6_mgr IS NOT NULL) + (@df7 IS NOT NULL AND @df7_mgr IS NOT NULL) + (@df8 IS NOT NULL AND @df8_mgr IS NOT NULL) + (@df9 IS NOT NULL AND @df9_mgr IS NOT NULL) + (@df10 IS NOT NULL AND @df10_mgr IS NOT NULL) + (@df11 IS NOT NULL AND @df11_mgr IS NOT NULL) + (@df12 IS NOT NULL AND @df12_mgr IS NOT NULL) + (@df13 IS NOT NULL AND @df13_mgr IS NOT NULL) + (@df14 IS NOT NULL AND @df14_mgr IS NOT NULL) + (@df15 IS NOT NULL AND @df15_mgr IS NOT NULL) + (@df16 IS NOT NULL AND @df16_mgr IS NOT NULL) + (@df17 IS NOT NULL AND @df17_mgr IS NOT NULL) + (@df18 IS NOT NULL AND @df18_mgr IS NOT NULL) + (@df19 IS NOT NULL AND @df19_mgr IS NOT NULL) + (@df20 IS NOT NULL AND @df20_mgr IS NOT NULL) + (@df21 IS NOT NULL AND @df21_mgr IS NOT NULL) + (@df22 IS NOT NULL AND @df22_mgr IS NOT NULL) + (@df23 IS NOT NULL AND @df23_mgr IS NOT NULL) + (@df24 IS NOT NULL AND @df24_mgr IS NOT NULL) + (@df25 IS NOT NULL AND @df25_mgr IS NOT NULL) + (@df26 IS NOT NULL AND @df26_mgr IS NOT NULL) + (@df27 IS NOT NULL AND @df27_mgr IS NOT NULL) + (@df28 IS NOT NULL AND @df28_mgr IS NOT NULL) + (@df29 IS NOT NULL AND @df29_mgr IS NOT NULL) + (@df30 IS NOT NULL AND @df30_mgr IS NOT NULL) + (@df31 IS NOT NULL AND @df31_mgr IS NOT NULL) + (@df32 IS NOT NULL AND @df32_mgr IS NOT NULL) + (@df33 IS NOT NULL AND @df33_mgr IS NOT NULL) + (@df34 IS NOT NULL AND @df34_mgr IS NOT NULL) + (@df35 IS NOT NULL AND @df35_mgr IS NOT NULL) + (@df36 IS NOT NULL AND @df36_mgr IS NOT NULL) + (@df37 IS NOT NULL AND @df37_mgr IS NOT NULL) + (@df38 IS NOT NULL AND @df38_mgr IS NOT NULL) + (@df39 IS NOT NULL AND @df39_mgr IS NOT NULL) + (@df40 IS NOT NULL AND @df40_mgr IS NOT NULL) + (@df41 IS NOT NULL AND @df41_mgr IS NOT NULL) + (@df42 IS NOT NULL AND @df42_mgr IS NOT NULL) + (@df43 IS NOT NULL AND @df43_mgr IS NOT NULL) + (@df44 IS NOT NULL AND @df44_mgr IS NOT NULL) + (@df45 IS NOT NULL AND @df45_mgr IS NOT NULL) + (@df46 IS NOT NULL AND @df46_mgr IS NOT NULL) + (@df47 IS NOT NULL AND @df47_mgr IS NOT NULL) + (@df48 IS NOT NULL AND @df48_mgr IS NOT NULL) + (@df49 IS NOT NULL AND @df49_mgr IS NOT NULL) + (@df50 IS NOT NULL AND @df50_mgr IS NOT NULL) + (@df51 IS NOT NULL AND @df51_mgr IS NOT NULL) + (@df52 IS NOT NULL AND @df52_mgr IS NOT NULL) + (@df53 IS NOT NULL AND @df53_mgr IS NOT NULL) + (@df54 IS NOT NULL AND @df54_mgr IS NOT NULL) + (@df55 IS NOT NULL AND @df55_mgr IS NOT NULL) + (@df56 IS NOT NULL AND @df56_mgr IS NOT NULL) + (@df57 IS NOT NULL AND @df57_mgr IS NOT NULL) + (@df58 IS NOT NULL AND @df58_mgr IS NOT NULL) + (@df59 IS NOT NULL AND @df59_mgr IS NOT NULL) + (@df60 IS NOT NULL AND @df60_mgr IS NOT NULL) + (@df61 IS NOT NULL AND @df61_mgr IS NOT NULL) + (@df62 IS NOT NULL AND @df62_mgr IS NOT NULL) + (@df63 IS NOT NULL AND @df63_mgr IS NOT NULL) + (@df64 IS NOT NULL AND @df64_mgr IS NOT NULL) + (@df65 IS NOT NULL AND @df65_mgr IS NOT NULL) + (@df66 IS NOT NULL AND @df66_mgr IS NOT NULL) + (@df67 IS NOT NULL AND @df67_mgr IS NOT NULL) + (@df68 IS NOT NULL AND @df68_mgr IS NOT NULL) + (@df69 IS NOT NULL AND @df69_mgr IS NOT NULL) + (@df70 IS NOT NULL AND @df70_mgr IS NOT NULL) + (@df71 IS NOT NULL AND @df71_mgr IS NOT NULL) + (@df72 IS NOT NULL AND @df72_mgr IS NOT NULL) + (@df73 IS NOT NULL AND @df73_mgr IS NOT NULL)) = 73, ((@dr_강남구 IS NOT NULL) + (@dr_강동구 IS NOT NULL) + (@dr_강서구 IS NOT NULL) + (@dr_관악구 IS NOT NULL) + (@dr_광진구 IS NOT NULL) + (@dr_구로구 IS NOT NULL) + (@dr_금천구 IS NOT NULL) + (@dr_도봉구 IS NOT NULL) + (@dr_동대문구 IS NOT NULL) + (@dr_동작구 IS NOT NULL) + (@dr_마포구 IS NOT NULL) + (@dr_서대문구 IS NOT NULL) + (@dr_서초구 IS NOT NULL) + (@dr_성동구 IS NOT NULL) + (@dr_성북구 IS NOT NULL) + (@dr_송파구 IS NOT NULL) + (@dr_양천구 IS NOT NULL) + (@dr_영등포구 IS NOT NULL) + (@dr_용산구 IS NOT NULL) + (@dr_종로구 IS NOT NULL) + (@dr_중구 IS NOT NULL)) = 21, ((@di_futsal_0 IS NOT NULL) + (@di_futsal_1 IS NOT NULL) + (@di_futsal_2 IS NOT NULL) + (@di_tennis_0 IS NOT NULL) + (@di_tennis_1 IS NOT NULL) + (@di_tennis_2 IS NOT NULL) + (@di_badminton_0 IS NOT NULL) + (@di_badminton_1 IS NOT NULL) + (@di_badminton_2 IS NOT NULL) + (@di_basketball_0 IS NOT NULL) + (@di_basketball_1 IS NOT NULL) + (@di_basketball_2 IS NOT NULL) + (@di_jokgu_0 IS NOT NULL) + (@di_jokgu_1 IS NOT NULL) + (@di_jokgu_2 IS NOT NULL)) = 15,
       NOT EXISTS (SELECT 1 FROM club WHERE active_club_name IN ('뚝섬 FC', '망원 풋살 크루', '잠실 풋살 클럽', '반포 농구단', '광나루 바스켓', '한강 족구회', '뚝섬 셔틀콕', '마포 랠리 테니스')),
       EXISTS (SELECT 1 FROM app_user WHERE email = 'demo-001@spoit.test')
  INTO @demo_sports, @demo_facilities, @demo_regions, @demo_items, @demo_club_names_free, @demo_already_applied;
SET @demo_checks = @demo_sports AND @demo_facilities AND @demo_regions AND @demo_items AND @demo_club_names_free;
SET @demo_ready = @demo_checks AND NOT @demo_already_applied;
SELECT @demo_sports AS sports, @demo_facilities AS facilities, @demo_regions AS districts, @demo_items AS evaluation_items,
       @demo_club_names_free AS club_names_free, @demo_already_applied AS already_applied;
-- 이미 적용된 DB가 아닌데 확인에 실패하면 "Subquery returns more than 1 row" 오류로 멈춘다.
DO (SELECT 1 UNION ALL SELECT 1 FROM DUAL WHERE @demo_checks = 0 AND @demo_already_applied = 0);

SELECT MAX(user_id), (SELECT COALESCE(MAX(club_id), 0) FROM club), (SELECT COALESCE(MAX(club_member_id), 0) FROM club_member),
       (SELECT COALESCE(MAX(match_id), 0) FROM social_match), (SELECT COALESCE(MAX(application_id), 0) FROM match_application),
       (SELECT COALESCE(MAX(participant_id), 0) FROM match_participant), (SELECT COALESCE(MAX(evaluation_id), 0) FROM match_evaluation)
  INTO @du, @dc, @dcm, @dm, @da, @dp, @de
  FROM app_user;

-- 1. 회원
INSERT INTO app_user (user_id, email, password_hash, nickname, phone, role, created_at)
SELECT * FROM (
                 SELECT (@du + 1) AS user_id, 'demo-001@spoit.test' AS email, '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG' AS password_hash, '류은채' AS nickname, '010-7001-2870' AS phone, 'MEMBER' AS role, '2026-10-13 17:00:00' AS created_at
       UNION ALL SELECT (@du + 2), 'demo-002@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '장서윤', '010-7002-5363', 'MEMBER', '2026-10-02 19:00:00'
       UNION ALL SELECT (@du + 3), 'demo-003@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '류서윤', '010-7003-3952', 'MEMBER', '2026-10-15 18:00:00'
       UNION ALL SELECT (@du + 4), 'demo-004@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '임예린', '010-7004-4772', 'MEMBER', '2026-10-08 14:00:00'
       UNION ALL SELECT (@du + 5), 'demo-005@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '강건우', '010-7005-6258', 'MEMBER', '2026-10-25 15:00:00'
       UNION ALL SELECT (@du + 6), 'demo-006@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '이하준', '010-7006-1386', 'MEMBER', '2026-10-18 19:00:00'
       UNION ALL SELECT (@du + 7), 'demo-007@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '서수아', '010-7007-0992', 'MEMBER', '2026-10-26 15:00:00'
       UNION ALL SELECT (@du + 8), 'demo-008@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '최하은', '010-7008-7789', 'MEMBER', '2026-10-19 12:00:00'
       UNION ALL SELECT (@du + 9), 'demo-009@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '오지유', '010-7009-1327', 'MEMBER', '2026-10-09 12:00:00'
       UNION ALL SELECT (@du + 10), 'demo-010@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '최승현', '010-7010-6789', 'MEMBER', '2026-10-23 10:00:00'
       UNION ALL SELECT (@du + 11), 'demo-011@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '박채원', '010-7011-1787', 'MEMBER', '2026-10-10 10:00:00'
       UNION ALL SELECT (@du + 12), 'demo-012@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '서가은', '010-7012-1062', 'MEMBER', '2026-10-20 15:00:00'
       UNION ALL SELECT (@du + 13), 'demo-013@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '권민재', '010-7013-1405', 'MEMBER', '2026-10-01 21:00:00'
       UNION ALL SELECT (@du + 14), 'demo-014@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '서지아', '010-7014-8078', 'MEMBER', '2026-10-09 09:00:00'
       UNION ALL SELECT (@du + 15), 'demo-015@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '강지민', '010-7015-2028', 'MEMBER', '2026-10-01 17:00:00'
       UNION ALL SELECT (@du + 16), 'demo-016@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '김서연', '010-7016-1476', 'MEMBER', '2026-10-09 08:00:00'
       UNION ALL SELECT (@du + 17), 'demo-017@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '신하준', '010-7017-9654', 'MEMBER', '2026-10-05 19:00:00'
       UNION ALL SELECT (@du + 18), 'demo-018@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '정윤서', '010-7018-6614', 'MEMBER', '2026-10-01 08:00:00'
       UNION ALL SELECT (@du + 19), 'demo-019@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '권지우', '010-7019-2064', 'MEMBER', '2026-10-21 14:00:00'
       UNION ALL SELECT (@du + 20), 'demo-020@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '이시우', '010-7020-3442', 'MEMBER', '2026-10-04 21:00:00'
       UNION ALL SELECT (@du + 21), 'demo-021@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '한지아', '010-7021-2686', 'MEMBER', '2026-10-08 08:00:00'
       UNION ALL SELECT (@du + 22), 'demo-022@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '류재원', '010-7022-7854', 'MEMBER', '2026-10-26 09:00:00'
       UNION ALL SELECT (@du + 23), 'demo-023@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '오예준', '010-7023-4790', 'MEMBER', '2026-10-11 14:00:00'
       UNION ALL SELECT (@du + 24), 'demo-024@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '신서연', '010-7024-1563', 'MEMBER', '2026-10-22 17:00:00'
       UNION ALL SELECT (@du + 25), 'demo-025@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '오하린', '010-7025-5592', 'MEMBER', '2026-10-01 13:00:00'
       UNION ALL SELECT (@du + 26), 'demo-026@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '이민준', '010-7026-4522', 'MEMBER', '2026-10-03 08:00:00'
       UNION ALL SELECT (@du + 27), 'demo-027@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '류채원', '010-7027-7450', 'MEMBER', '2026-10-20 16:00:00'
       UNION ALL SELECT (@du + 28), 'demo-028@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '장현우', '010-7028-6841', 'MEMBER', '2026-10-01 21:00:00'
       UNION ALL SELECT (@du + 29), 'demo-029@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '박민준', '010-7029-3877', 'MEMBER', '2026-10-03 18:00:00'
       UNION ALL SELECT (@du + 30), 'demo-030@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '윤승현', '010-7030-9027', 'MEMBER', '2026-10-08 10:00:00'
       UNION ALL SELECT (@du + 31), 'demo-031@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '신소율', '010-7031-5086', 'MEMBER', '2026-10-08 22:00:00'
       UNION ALL SELECT (@du + 32), 'demo-032@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '김민재', '010-7032-5338', 'MEMBER', '2026-10-19 08:00:00'
       UNION ALL SELECT (@du + 33), 'demo-033@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '조은우', '010-7033-3274', 'MEMBER', '2026-10-15 21:00:00'
       UNION ALL SELECT (@du + 34), 'demo-034@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '김지유', '010-7034-5972', 'MEMBER', '2026-10-07 22:00:00'
       UNION ALL SELECT (@du + 35), 'demo-035@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '김유나', '010-7035-7770', 'MEMBER', '2026-10-16 18:00:00'
       UNION ALL SELECT (@du + 36), 'demo-036@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '한승현', '010-7036-3100', 'MEMBER', '2026-10-16 11:00:00'
       UNION ALL SELECT (@du + 37), 'demo-037@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '권윤서', '010-7037-8511', 'MEMBER', '2026-10-16 18:00:00'
       UNION ALL SELECT (@du + 38), 'demo-038@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '송소율', '010-7038-0770', 'MEMBER', '2026-10-04 21:00:00'
       UNION ALL SELECT (@du + 39), 'demo-039@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '류지민', '010-7039-1349', 'MEMBER', '2026-10-16 18:00:00'
       UNION ALL SELECT (@du + 40), 'demo-040@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '홍다은', '010-7040-4792', 'MEMBER', '2026-10-13 21:00:00'
       UNION ALL SELECT (@du + 41), 'demo-041@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '윤주원', '010-7041-0816', 'MEMBER', '2026-10-09 14:00:00'
       UNION ALL SELECT (@du + 42), 'demo-042@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '황현우', '010-7042-4646', 'MEMBER', '2026-10-24 10:00:00'
       UNION ALL SELECT (@du + 43), 'demo-043@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '조건우', '010-7043-8377', 'MEMBER', '2026-10-02 22:00:00'
       UNION ALL SELECT (@du + 44), 'demo-044@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '권선우', '010-7044-1466', 'MEMBER', '2026-10-04 11:00:00'
       UNION ALL SELECT (@du + 45), 'demo-045@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '신예린', '010-7045-6705', 'MEMBER', '2026-10-09 12:00:00'
       UNION ALL SELECT (@du + 46), 'demo-046@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '송지우', '010-7046-0802', 'MEMBER', '2026-10-07 13:00:00'
       UNION ALL SELECT (@du + 47), 'demo-047@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '한재원', '010-7047-0917', 'MEMBER', '2026-10-13 21:00:00'
       UNION ALL SELECT (@du + 48), 'demo-048@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '한우진', '010-7048-1532', 'MEMBER', '2026-10-22 16:00:00'
       UNION ALL SELECT (@du + 49), 'demo-049@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '강민준', '010-7049-5776', 'MEMBER', '2026-10-04 18:00:00'
       UNION ALL SELECT (@du + 50), 'demo-050@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '임지민', '010-7050-7697', 'MEMBER', '2026-10-21 11:00:00'
       UNION ALL SELECT (@du + 51), 'demo-051@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '한도윤', '010-7051-7093', 'MEMBER', '2026-10-10 15:00:00'
       UNION ALL SELECT (@du + 52), 'demo-052@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '정준혁', '010-7052-3693', 'MEMBER', '2026-10-26 12:00:00'
       UNION ALL SELECT (@du + 53), 'demo-053@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '윤하준', '010-7053-3347', 'MEMBER', '2026-10-03 20:00:00'
       UNION ALL SELECT (@du + 54), 'demo-054@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '신건우', '010-7054-4243', 'MEMBER', '2026-10-09 19:00:00'
       UNION ALL SELECT (@du + 55), 'demo-055@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '류도윤', '010-7055-9793', 'MEMBER', '2026-10-14 19:00:00'
       UNION ALL SELECT (@du + 56), 'demo-056@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '신윤서', '010-7056-2930', 'MEMBER', '2026-10-24 13:00:00'
       UNION ALL SELECT (@du + 57), 'demo-057@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '윤민준', '010-7057-7384', 'MEMBER', '2026-10-10 17:00:00'
       UNION ALL SELECT (@du + 58), 'demo-058@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '장하준', '010-7058-2807', 'MEMBER', '2026-10-13 11:00:00'
       UNION ALL SELECT (@du + 59), 'demo-059@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '안재원', '010-7059-9663', 'MEMBER', '2026-10-08 19:00:00'
       UNION ALL SELECT (@du + 60), 'demo-060@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '김태윤', '010-7060-4549', 'MEMBER', '2026-10-10 10:00:00'
       UNION ALL SELECT (@du + 61), 'demo-061@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '최지우', '010-7061-3860', 'MEMBER', '2026-10-14 12:00:00'
       UNION ALL SELECT (@du + 62), 'demo-062@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '송은채', '010-7062-7960', 'MEMBER', '2026-10-23 13:00:00'
       UNION ALL SELECT (@du + 63), 'demo-063@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '안다은', '010-7063-2744', 'MEMBER', '2026-10-26 13:00:00'
       UNION ALL SELECT (@du + 64), 'demo-064@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '정우진', '010-7064-8402', 'MEMBER', '2026-10-15 13:00:00'
       UNION ALL SELECT (@du + 65), 'demo-065@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '박윤서', '010-7065-3818', 'MEMBER', '2026-10-11 20:00:00'
       UNION ALL SELECT (@du + 66), 'demo-066@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '강은채', '010-7066-9236', 'MEMBER', '2026-10-26 16:00:00'
       UNION ALL SELECT (@du + 67), 'demo-067@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '정승현', '010-7067-3480', 'MEMBER', '2026-10-11 09:00:00'
       UNION ALL SELECT (@du + 68), 'demo-068@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '오예린', '010-7068-1672', 'MEMBER', '2026-10-16 13:00:00'
       UNION ALL SELECT (@du + 69), 'demo-069@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '황민재', '010-7069-5040', 'MEMBER', '2026-10-01 17:00:00'
       UNION ALL SELECT (@du + 70), 'demo-070@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '김주원', '010-7070-5152', 'MEMBER', '2026-10-14 12:00:00'
       UNION ALL SELECT (@du + 71), 'demo-071@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '신연우', '010-7071-0922', 'MEMBER', '2026-10-24 08:00:00'
       UNION ALL SELECT (@du + 72), 'demo-072@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '윤건우', '010-7072-4316', 'MEMBER', '2026-10-14 17:00:00'
       UNION ALL SELECT (@du + 73), 'demo-073@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '안하린', '010-7073-2922', 'MEMBER', '2026-10-17 10:00:00'
       UNION ALL SELECT (@du + 74), 'demo-074@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '박지유', '010-7074-9264', 'MEMBER', '2026-10-05 13:00:00'
       UNION ALL SELECT (@du + 75), 'demo-075@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '임하린', '010-7075-8145', 'MEMBER', '2026-10-14 17:00:00'
       UNION ALL SELECT (@du + 76), 'demo-076@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '정지호', '010-7076-4209', 'MEMBER', '2026-10-12 16:00:00'
       UNION ALL SELECT (@du + 77), 'demo-077@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '서하린', '010-7077-8526', 'MEMBER', '2026-10-13 10:00:00'
       UNION ALL SELECT (@du + 78), 'demo-078@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '서다은', '010-7078-4506', 'MEMBER', '2026-10-02 18:00:00'
       UNION ALL SELECT (@du + 79), 'demo-079@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '안서윤', '010-7079-2556', 'MEMBER', '2026-10-25 15:00:00'
       UNION ALL SELECT (@du + 80), 'demo-080@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '장태윤', '010-7080-4289', 'MEMBER', '2026-10-04 18:00:00'
       UNION ALL SELECT (@du + 81), 'demo-081@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '장나연', '010-7081-0870', 'MEMBER', '2026-10-14 09:00:00'
       UNION ALL SELECT (@du + 82), 'demo-082@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '이수빈', '010-7082-7326', 'MEMBER', '2026-10-03 18:00:00'
       UNION ALL SELECT (@du + 83), 'demo-083@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '김서현', '010-7083-4568', 'MEMBER', '2026-10-13 20:00:00'
       UNION ALL SELECT (@du + 84), 'demo-084@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '정민재', '010-7084-2154', 'MEMBER', '2026-10-20 18:00:00'
       UNION ALL SELECT (@du + 85), 'demo-085@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '강민재', '010-7085-7277', 'MEMBER', '2026-10-12 08:00:00'
       UNION ALL SELECT (@du + 86), 'demo-086@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '오가은', '010-7086-4275', 'MEMBER', '2026-10-06 17:00:00'
       UNION ALL SELECT (@du + 87), 'demo-087@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '황지아', '010-7087-4688', 'MEMBER', '2026-10-09 15:00:00'
       UNION ALL SELECT (@du + 88), 'demo-088@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '강정우', '010-7088-0573', 'MEMBER', '2026-10-11 16:00:00'
       UNION ALL SELECT (@du + 89), 'demo-089@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '박수빈', '010-7089-8830', 'MEMBER', '2026-10-24 09:00:00'
       UNION ALL SELECT (@du + 90), 'demo-090@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '강수빈', '010-7090-4690', 'MEMBER', '2026-10-18 17:00:00'
       UNION ALL SELECT (@du + 91), 'demo-091@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '윤은채', '010-7091-9877', 'MEMBER', '2026-10-13 18:00:00'
       UNION ALL SELECT (@du + 92), 'demo-092@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '김시우', '010-7092-0126', 'MEMBER', '2026-10-23 12:00:00'
       UNION ALL SELECT (@du + 93), 'demo-093@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '권채원', '010-7093-9583', 'MEMBER', '2026-10-21 17:00:00'
       UNION ALL SELECT (@du + 94), 'demo-094@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '권승현', '010-7094-5160', 'MEMBER', '2026-10-10 16:00:00'
       UNION ALL SELECT (@du + 95), 'demo-095@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '조지유', '010-7095-5285', 'MEMBER', '2026-10-03 19:00:00'
       UNION ALL SELECT (@du + 96), 'demo-096@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '김다은', '010-7096-1409', 'MEMBER', '2026-10-15 12:00:00'
       UNION ALL SELECT (@du + 97), 'demo-097@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '최민준', '010-7097-8372', 'MEMBER', '2026-10-02 19:00:00'
       UNION ALL SELECT (@du + 98), 'demo-098@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '강연우', '010-7098-6589', 'MEMBER', '2026-10-10 14:00:00'
       UNION ALL SELECT (@du + 99), 'demo-099@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '서현우', '010-7099-5187', 'MEMBER', '2026-10-12 08:00:00'
       UNION ALL SELECT (@du + 100), 'demo-100@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '안수아', '010-7100-4050', 'MEMBER', '2026-10-25 11:00:00'
       UNION ALL SELECT (@du + 101), 'demo-presenter@spoit.test', '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG', '발표자', '010-7101-1335', 'MEMBER', '2026-10-05 18:00:00'
) v
 WHERE @demo_ready = 1;

INSERT INTO user_region (user_id, region_id)
SELECT * FROM (
                 SELECT (@du + 1) AS user_id, @dr_구로구 AS region_id
       UNION ALL SELECT (@du + 2), @dr_강동구
       UNION ALL SELECT (@du + 3), @dr_용산구
       UNION ALL SELECT (@du + 4), @dr_동대문구
       UNION ALL SELECT (@du + 5), @dr_구로구
       UNION ALL SELECT (@du + 6), @dr_구로구
       UNION ALL SELECT (@du + 7), @dr_도봉구
       UNION ALL SELECT (@du + 8), @dr_도봉구
       UNION ALL SELECT (@du + 9), @dr_용산구
       UNION ALL SELECT (@du + 10), @dr_금천구
       UNION ALL SELECT (@du + 11), @dr_강서구
       UNION ALL SELECT (@du + 12), @dr_성동구
       UNION ALL SELECT (@du + 13), @dr_송파구
       UNION ALL SELECT (@du + 14), @dr_서초구
       UNION ALL SELECT (@du + 15), @dr_중구
       UNION ALL SELECT (@du + 16), @dr_강동구
       UNION ALL SELECT (@du + 17), @dr_종로구
       UNION ALL SELECT (@du + 18), @dr_서대문구
       UNION ALL SELECT (@du + 19), @dr_중구
       UNION ALL SELECT (@du + 20), @dr_용산구
       UNION ALL SELECT (@du + 21), @dr_서초구
       UNION ALL SELECT (@du + 22), @dr_영등포구
       UNION ALL SELECT (@du + 23), @dr_성북구
       UNION ALL SELECT (@du + 24), @dr_서대문구
       UNION ALL SELECT (@du + 25), @dr_서초구
       UNION ALL SELECT (@du + 26), @dr_용산구
       UNION ALL SELECT (@du + 27), @dr_서초구
       UNION ALL SELECT (@du + 28), @dr_성동구
       UNION ALL SELECT (@du + 29), @dr_종로구
       UNION ALL SELECT (@du + 30), @dr_영등포구
       UNION ALL SELECT (@du + 31), @dr_강남구
       UNION ALL SELECT (@du + 32), @dr_성북구
       UNION ALL SELECT (@du + 33), @dr_동대문구
       UNION ALL SELECT (@du + 34), @dr_중구
       UNION ALL SELECT (@du + 35), @dr_강서구
       UNION ALL SELECT (@du + 36), @dr_성북구
       UNION ALL SELECT (@du + 37), @dr_성동구
       UNION ALL SELECT (@du + 38), @dr_종로구
       UNION ALL SELECT (@du + 39), @dr_성동구
       UNION ALL SELECT (@du + 40), @dr_마포구
       UNION ALL SELECT (@du + 41), @dr_강동구
       UNION ALL SELECT (@du + 42), @dr_양천구
       UNION ALL SELECT (@du + 43), @dr_강동구
       UNION ALL SELECT (@du + 44), @dr_동작구
       UNION ALL SELECT (@du + 45), @dr_강동구
       UNION ALL SELECT (@du + 46), @dr_동대문구
       UNION ALL SELECT (@du + 47), @dr_금천구
       UNION ALL SELECT (@du + 48), @dr_강동구
       UNION ALL SELECT (@du + 49), @dr_강동구
       UNION ALL SELECT (@du + 50), @dr_용산구
       UNION ALL SELECT (@du + 51), @dr_구로구
       UNION ALL SELECT (@du + 52), @dr_용산구
       UNION ALL SELECT (@du + 53), @dr_종로구
       UNION ALL SELECT (@du + 54), @dr_마포구
       UNION ALL SELECT (@du + 55), @dr_중구
       UNION ALL SELECT (@du + 56), @dr_서초구
       UNION ALL SELECT (@du + 57), @dr_동작구
       UNION ALL SELECT (@du + 58), @dr_중구
       UNION ALL SELECT (@du + 59), @dr_광진구
       UNION ALL SELECT (@du + 60), @dr_양천구
       UNION ALL SELECT (@du + 61), @dr_도봉구
       UNION ALL SELECT (@du + 62), @dr_동작구
       UNION ALL SELECT (@du + 63), @dr_송파구
       UNION ALL SELECT (@du + 64), @dr_용산구
       UNION ALL SELECT (@du + 65), @dr_송파구
       UNION ALL SELECT (@du + 66), @dr_성북구
       UNION ALL SELECT (@du + 67), @dr_양천구
       UNION ALL SELECT (@du + 68), @dr_중구
       UNION ALL SELECT (@du + 69), @dr_관악구
       UNION ALL SELECT (@du + 70), @dr_강서구
       UNION ALL SELECT (@du + 71), @dr_도봉구
       UNION ALL SELECT (@du + 72), @dr_동대문구
       UNION ALL SELECT (@du + 73), @dr_송파구
       UNION ALL SELECT (@du + 74), @dr_영등포구
       UNION ALL SELECT (@du + 75), @dr_금천구
       UNION ALL SELECT (@du + 76), @dr_서초구
       UNION ALL SELECT (@du + 77), @dr_송파구
       UNION ALL SELECT (@du + 78), @dr_용산구
       UNION ALL SELECT (@du + 79), @dr_영등포구
       UNION ALL SELECT (@du + 80), @dr_서대문구
       UNION ALL SELECT (@du + 81), @dr_관악구
       UNION ALL SELECT (@du + 82), @dr_금천구
       UNION ALL SELECT (@du + 83), @dr_마포구
       UNION ALL SELECT (@du + 84), @dr_금천구
       UNION ALL SELECT (@du + 85), @dr_금천구
       UNION ALL SELECT (@du + 86), @dr_중구
       UNION ALL SELECT (@du + 87), @dr_영등포구
       UNION ALL SELECT (@du + 88), @dr_양천구
       UNION ALL SELECT (@du + 89), @dr_강동구
       UNION ALL SELECT (@du + 90), @dr_도봉구
       UNION ALL SELECT (@du + 91), @dr_송파구
       UNION ALL SELECT (@du + 92), @dr_마포구
       UNION ALL SELECT (@du + 93), @dr_성북구
       UNION ALL SELECT (@du + 94), @dr_동작구
       UNION ALL SELECT (@du + 95), @dr_성동구
       UNION ALL SELECT (@du + 96), @dr_동대문구
       UNION ALL SELECT (@du + 97), @dr_서초구
       UNION ALL SELECT (@du + 98), @dr_금천구
       UNION ALL SELECT (@du + 99), @dr_강서구
       UNION ALL SELECT (@du + 100), @dr_강남구
       UNION ALL SELECT (@du + 101), @dr_광진구
) v
 WHERE @demo_ready = 1;

INSERT INTO user_preferred_sport (user_id, sport_id)
SELECT * FROM (
                 SELECT (@du + 1) AS user_id, @ds_futsal AS sport_id
       UNION ALL SELECT (@du + 2), @ds_badminton
       UNION ALL SELECT (@du + 3), @ds_basketball
       UNION ALL SELECT (@du + 4), @ds_futsal
       UNION ALL SELECT (@du + 5), @ds_badminton
       UNION ALL SELECT (@du + 6), @ds_basketball
       UNION ALL SELECT (@du + 7), @ds_jokgu
       UNION ALL SELECT (@du + 8), @ds_tennis
       UNION ALL SELECT (@du + 9), @ds_basketball
       UNION ALL SELECT (@du + 10), @ds_badminton
       UNION ALL SELECT (@du + 11), @ds_jokgu
       UNION ALL SELECT (@du + 12), @ds_jokgu
       UNION ALL SELECT (@du + 13), @ds_basketball
       UNION ALL SELECT (@du + 14), @ds_badminton
       UNION ALL SELECT (@du + 15), @ds_basketball
       UNION ALL SELECT (@du + 16), @ds_tennis
       UNION ALL SELECT (@du + 17), @ds_tennis
       UNION ALL SELECT (@du + 18), @ds_basketball
       UNION ALL SELECT (@du + 19), @ds_basketball
       UNION ALL SELECT (@du + 20), @ds_futsal
       UNION ALL SELECT (@du + 21), @ds_tennis
       UNION ALL SELECT (@du + 22), @ds_jokgu
       UNION ALL SELECT (@du + 23), @ds_jokgu
       UNION ALL SELECT (@du + 24), @ds_tennis
       UNION ALL SELECT (@du + 25), @ds_badminton
       UNION ALL SELECT (@du + 26), @ds_futsal
       UNION ALL SELECT (@du + 27), @ds_basketball
       UNION ALL SELECT (@du + 28), @ds_basketball
       UNION ALL SELECT (@du + 29), @ds_tennis
       UNION ALL SELECT (@du + 30), @ds_basketball
       UNION ALL SELECT (@du + 31), @ds_futsal
       UNION ALL SELECT (@du + 32), @ds_badminton
       UNION ALL SELECT (@du + 33), @ds_tennis
       UNION ALL SELECT (@du + 34), @ds_futsal
       UNION ALL SELECT (@du + 35), @ds_tennis
       UNION ALL SELECT (@du + 36), @ds_basketball
       UNION ALL SELECT (@du + 37), @ds_jokgu
       UNION ALL SELECT (@du + 38), @ds_jokgu
       UNION ALL SELECT (@du + 39), @ds_basketball
       UNION ALL SELECT (@du + 40), @ds_jokgu
       UNION ALL SELECT (@du + 41), @ds_jokgu
       UNION ALL SELECT (@du + 42), @ds_jokgu
       UNION ALL SELECT (@du + 43), @ds_futsal
       UNION ALL SELECT (@du + 44), @ds_futsal
       UNION ALL SELECT (@du + 45), @ds_futsal
       UNION ALL SELECT (@du + 46), @ds_badminton
       UNION ALL SELECT (@du + 47), @ds_futsal
       UNION ALL SELECT (@du + 48), @ds_tennis
       UNION ALL SELECT (@du + 49), @ds_futsal
       UNION ALL SELECT (@du + 50), @ds_basketball
       UNION ALL SELECT (@du + 51), @ds_futsal
       UNION ALL SELECT (@du + 52), @ds_badminton
       UNION ALL SELECT (@du + 53), @ds_tennis
       UNION ALL SELECT (@du + 54), @ds_futsal
       UNION ALL SELECT (@du + 55), @ds_basketball
       UNION ALL SELECT (@du + 56), @ds_tennis
       UNION ALL SELECT (@du + 57), @ds_badminton
       UNION ALL SELECT (@du + 58), @ds_futsal
       UNION ALL SELECT (@du + 59), @ds_futsal
       UNION ALL SELECT (@du + 60), @ds_basketball
       UNION ALL SELECT (@du + 61), @ds_futsal
       UNION ALL SELECT (@du + 62), @ds_basketball
       UNION ALL SELECT (@du + 63), @ds_badminton
       UNION ALL SELECT (@du + 64), @ds_badminton
       UNION ALL SELECT (@du + 65), @ds_basketball
       UNION ALL SELECT (@du + 66), @ds_tennis
       UNION ALL SELECT (@du + 67), @ds_jokgu
       UNION ALL SELECT (@du + 68), @ds_futsal
       UNION ALL SELECT (@du + 69), @ds_futsal
       UNION ALL SELECT (@du + 70), @ds_tennis
       UNION ALL SELECT (@du + 71), @ds_futsal
       UNION ALL SELECT (@du + 72), @ds_futsal
       UNION ALL SELECT (@du + 73), @ds_tennis
       UNION ALL SELECT (@du + 74), @ds_tennis
       UNION ALL SELECT (@du + 75), @ds_basketball
       UNION ALL SELECT (@du + 76), @ds_basketball
       UNION ALL SELECT (@du + 77), @ds_tennis
       UNION ALL SELECT (@du + 78), @ds_futsal
       UNION ALL SELECT (@du + 79), @ds_basketball
       UNION ALL SELECT (@du + 80), @ds_futsal
       UNION ALL SELECT (@du + 81), @ds_futsal
       UNION ALL SELECT (@du + 82), @ds_tennis
       UNION ALL SELECT (@du + 83), @ds_basketball
       UNION ALL SELECT (@du + 84), @ds_tennis
       UNION ALL SELECT (@du + 85), @ds_jokgu
       UNION ALL SELECT (@du + 86), @ds_futsal
       UNION ALL SELECT (@du + 87), @ds_basketball
       UNION ALL SELECT (@du + 88), @ds_basketball
       UNION ALL SELECT (@du + 89), @ds_tennis
       UNION ALL SELECT (@du + 90), @ds_futsal
       UNION ALL SELECT (@du + 91), @ds_futsal
       UNION ALL SELECT (@du + 92), @ds_badminton
       UNION ALL SELECT (@du + 93), @ds_tennis
       UNION ALL SELECT (@du + 94), @ds_tennis
       UNION ALL SELECT (@du + 95), @ds_futsal
       UNION ALL SELECT (@du + 96), @ds_basketball
       UNION ALL SELECT (@du + 97), @ds_basketball
       UNION ALL SELECT (@du + 98), @ds_tennis
       UNION ALL SELECT (@du + 99), @ds_tennis
       UNION ALL SELECT (@du + 100), @ds_futsal
       UNION ALL SELECT (@du + 101), @ds_futsal
) v
 WHERE @demo_ready = 1;

INSERT INTO sport_rating (user_id, sport_id, current_mmr, rated_match_count, updated_at)
SELECT * FROM (
                 SELECT (@du + 1) AS user_id, @ds_futsal AS sport_id, 1328 AS current_mmr, 6 AS rated_match_count, '2026-10-31 22:00:00' AS updated_at
       UNION ALL SELECT (@du + 1), @ds_badminton, 1061, 16, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 2), @ds_badminton, 1001, 10, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 2), @ds_futsal, 961, 9, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 2), @ds_tennis, 1088, 18, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 3), @ds_basketball, 818, 4, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 3), @ds_futsal, 927, 11, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 4), @ds_futsal, 897, 17, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 4), @ds_tennis, 1125, 12, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 5), @ds_badminton, 1011, 16, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 6), @ds_basketball, 964, 11, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 6), @ds_jokgu, 833, 13, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 7), @ds_jokgu, 1217, 5, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 7), @ds_basketball, 1062, 10, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 8), @ds_tennis, 1084, 3, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 8), @ds_badminton, 932, 13, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 8), @ds_basketball, 1020, 13, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 9), @ds_basketball, 899, 4, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 10), @ds_badminton, 926, 16, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 10), @ds_basketball, 1144, 13, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 11), @ds_jokgu, 989, 6, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 11), @ds_futsal, 1082, 6, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 12), @ds_jokgu, 938, 11, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 12), @ds_badminton, 979, 8, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 13), @ds_basketball, 997, 13, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 14), @ds_badminton, 1101, 8, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 14), @ds_basketball, 1063, 10, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 14), @ds_jokgu, 845, 6, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 15), @ds_basketball, 996, 14, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 15), @ds_badminton, 760, 8, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 15), @ds_jokgu, 870, 6, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 16), @ds_tennis, 979, 4, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 16), @ds_jokgu, 893, 15, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 17), @ds_tennis, 923, 6, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 17), @ds_futsal, 1097, 15, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 18), @ds_basketball, 1054, 18, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 18), @ds_badminton, 760, 12, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 19), @ds_basketball, 1015, 15, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 19), @ds_futsal, 1173, 15, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 19), @ds_tennis, 1071, 6, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 20), @ds_futsal, 1183, 12, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 21), @ds_tennis, 889, 10, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 22), @ds_jokgu, 1059, 12, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 22), @ds_futsal, 903, 7, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 22), @ds_badminton, 863, 12, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 23), @ds_jokgu, 849, 9, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 23), @ds_futsal, 1005, 4, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 23), @ds_tennis, 1004, 17, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 24), @ds_tennis, 1160, 6, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 24), @ds_badminton, 875, 8, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 24), @ds_basketball, 987, 6, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 25), @ds_badminton, 1225, 12, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 26), @ds_futsal, 872, 9, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 26), @ds_tennis, 760, 8, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 26), @ds_basketball, 951, 15, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 27), @ds_basketball, 1053, 11, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 27), @ds_jokgu, 1092, 18, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 28), @ds_basketball, 889, 4, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 28), @ds_tennis, 1008, 4, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 28), @ds_jokgu, 846, 7, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 29), @ds_tennis, 868, 10, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 29), @ds_badminton, 760, 8, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 29), @ds_basketball, 1142, 8, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 30), @ds_basketball, 897, 17, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 30), @ds_futsal, 984, 4, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 30), @ds_tennis, 956, 5, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 31), @ds_futsal, 920, 16, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 31), @ds_tennis, 990, 3, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 32), @ds_badminton, 921, 18, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 33), @ds_tennis, 822, 13, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 33), @ds_jokgu, 866, 8, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 34), @ds_futsal, 969, 6, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 34), @ds_tennis, 900, 11, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 35), @ds_tennis, 824, 6, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 35), @ds_futsal, 1048, 16, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 36), @ds_basketball, 983, 4, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 36), @ds_badminton, 959, 14, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 36), @ds_jokgu, 1181, 6, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 37), @ds_jokgu, 950, 12, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 37), @ds_badminton, 921, 14, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 38), @ds_jokgu, 915, 16, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 38), @ds_futsal, 951, 10, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 38), @ds_tennis, 1037, 4, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 39), @ds_basketball, 983, 8, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 39), @ds_badminton, 1075, 6, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 40), @ds_jokgu, 963, 6, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 40), @ds_basketball, 1002, 8, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 41), @ds_jokgu, 1027, 8, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 41), @ds_futsal, 984, 11, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 41), @ds_tennis, 849, 15, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 42), @ds_jokgu, 854, 11, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 42), @ds_futsal, 1122, 17, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 42), @ds_badminton, 885, 4, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 43), @ds_futsal, 975, 16, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 43), @ds_tennis, 924, 5, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 43), @ds_badminton, 962, 12, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 44), @ds_futsal, 924, 5, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 44), @ds_jokgu, 991, 7, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 45), @ds_futsal, 1011, 11, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 45), @ds_tennis, 929, 9, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 45), @ds_badminton, 864, 6, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 46), @ds_badminton, 1033, 5, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 46), @ds_futsal, 1035, 11, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 46), @ds_basketball, 1209, 17, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 47), @ds_futsal, 1130, 9, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 47), @ds_jokgu, 970, 16, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 48), @ds_tennis, 972, 4, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 48), @ds_jokgu, 1071, 8, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 49), @ds_futsal, 928, 17, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 50), @ds_basketball, 1033, 9, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 50), @ds_tennis, 1188, 13, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 51), @ds_futsal, 1050, 17, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 51), @ds_badminton, 895, 14, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 52), @ds_badminton, 1201, 18, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 52), @ds_tennis, 878, 7, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 52), @ds_jokgu, 900, 15, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 53), @ds_tennis, 1177, 13, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 54), @ds_futsal, 848, 10, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 55), @ds_basketball, 1094, 17, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 56), @ds_tennis, 874, 4, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 56), @ds_futsal, 982, 6, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 56), @ds_badminton, 891, 11, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 57), @ds_badminton, 1000, 18, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 57), @ds_futsal, 832, 5, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 57), @ds_tennis, 1076, 7, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 58), @ds_futsal, 1084, 15, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 58), @ds_basketball, 1186, 17, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 58), @ds_jokgu, 944, 3, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 59), @ds_futsal, 844, 4, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 59), @ds_badminton, 991, 15, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 59), @ds_jokgu, 1014, 18, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 60), @ds_basketball, 961, 8, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 60), @ds_tennis, 998, 13, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 60), @ds_jokgu, 1049, 8, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 61), @ds_futsal, 945, 15, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 61), @ds_jokgu, 931, 16, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 62), @ds_basketball, 793, 18, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 62), @ds_badminton, 1006, 5, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 63), @ds_badminton, 1020, 3, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 63), @ds_futsal, 817, 9, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 63), @ds_basketball, 960, 10, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 64), @ds_badminton, 760, 10, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 64), @ds_tennis, 973, 9, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 65), @ds_basketball, 1157, 16, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 65), @ds_futsal, 1217, 18, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 66), @ds_tennis, 1053, 6, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 66), @ds_badminton, 760, 16, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 66), @ds_basketball, 904, 8, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 67), @ds_jokgu, 970, 6, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 67), @ds_badminton, 1084, 12, '2026-10-31 22:00:00'
) v
 WHERE @demo_ready = 1;

INSERT INTO sport_rating (user_id, sport_id, current_mmr, rated_match_count, updated_at)
SELECT * FROM (
                 SELECT (@du + 67) AS user_id, @ds_basketball AS sport_id, 893 AS current_mmr, 15 AS rated_match_count, '2026-10-31 22:00:00' AS updated_at
       UNION ALL SELECT (@du + 68), @ds_futsal, 956, 11, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 68), @ds_tennis, 1014, 17, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 68), @ds_jokgu, 924, 3, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 69), @ds_futsal, 1010, 11, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 69), @ds_tennis, 1038, 15, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 69), @ds_jokgu, 944, 15, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 70), @ds_tennis, 910, 9, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 70), @ds_badminton, 909, 4, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 70), @ds_basketball, 1125, 12, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 71), @ds_futsal, 864, 17, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 71), @ds_basketball, 973, 17, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 72), @ds_futsal, 871, 12, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 72), @ds_basketball, 1130, 5, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 72), @ds_jokgu, 974, 16, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 73), @ds_tennis, 1074, 17, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 73), @ds_basketball, 1045, 10, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 73), @ds_jokgu, 950, 9, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 74), @ds_tennis, 1147, 13, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 74), @ds_futsal, 985, 5, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 75), @ds_basketball, 937, 7, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 75), @ds_badminton, 1058, 17, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 75), @ds_jokgu, 798, 3, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 76), @ds_basketball, 1087, 15, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 76), @ds_tennis, 1060, 7, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 77), @ds_tennis, 1097, 17, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 77), @ds_futsal, 931, 5, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 78), @ds_futsal, 860, 8, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 79), @ds_basketball, 940, 7, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 79), @ds_futsal, 1097, 16, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 79), @ds_tennis, 1051, 13, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 80), @ds_futsal, 860, 6, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 80), @ds_tennis, 1097, 13, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 81), @ds_futsal, 957, 8, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 81), @ds_tennis, 860, 15, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 81), @ds_jokgu, 820, 15, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 82), @ds_tennis, 1047, 6, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 82), @ds_futsal, 991, 15, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 82), @ds_jokgu, 956, 18, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 83), @ds_basketball, 1043, 11, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 83), @ds_futsal, 775, 4, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 83), @ds_jokgu, 995, 11, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 84), @ds_tennis, 1027, 16, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 84), @ds_basketball, 899, 11, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 85), @ds_jokgu, 1023, 3, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 85), @ds_futsal, 1120, 4, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 85), @ds_badminton, 1282, 16, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 86), @ds_futsal, 796, 18, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 86), @ds_jokgu, 990, 17, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 87), @ds_basketball, 980, 18, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 88), @ds_basketball, 808, 12, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 88), @ds_badminton, 816, 17, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 89), @ds_tennis, 877, 10, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 89), @ds_badminton, 944, 6, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 89), @ds_jokgu, 1064, 12, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 90), @ds_futsal, 987, 17, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 90), @ds_tennis, 980, 3, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 91), @ds_futsal, 1059, 18, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 92), @ds_badminton, 918, 4, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 93), @ds_tennis, 980, 16, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 93), @ds_jokgu, 902, 14, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 94), @ds_tennis, 1073, 12, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 94), @ds_badminton, 1093, 15, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 95), @ds_futsal, 861, 13, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 96), @ds_basketball, 935, 8, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 96), @ds_tennis, 1199, 17, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 96), @ds_badminton, 990, 15, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 97), @ds_basketball, 922, 6, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 97), @ds_futsal, 996, 18, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 97), @ds_jokgu, 1019, 17, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 98), @ds_tennis, 1166, 13, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 99), @ds_tennis, 1050, 16, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 99), @ds_futsal, 1108, 14, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 99), @ds_basketball, 1052, 9, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 100), @ds_futsal, 998, 10, '2026-10-31 22:00:00'
       UNION ALL SELECT (@du + 100), @ds_badminton, 855, 5, '2026-10-31 22:00:00'
) v
 WHERE @demo_ready = 1;

-- 2. 동호회
INSERT INTO club (club_id, sport_id, club_name, intro, max_members, recruit_status, activity_days, activity_start_time, activity_end_time, created_at)
SELECT * FROM (
                 SELECT (@dc + 1) AS club_id, @ds_futsal AS sport_id, '뚝섬 FC' AS club_name, '광진구에서 함께 풋살 하는 동호회입니다. 초보도 환영합니다.' AS intro, 20 AS max_members, '모집중' AS recruit_status, '6,7' AS activity_days, '10:00:00' AS activity_start_time, '12:00:00' AS activity_end_time, '2026-09-01 00:00:00' AS created_at
       UNION ALL SELECT (@dc + 2), @ds_futsal, '망원 풋살 크루', '마포구에서 함께 풋살 하는 동호회입니다. 초보도 환영합니다.', 20, '모집중', '2,4', '19:00:00', '21:00:00', '2026-09-04 00:00:00'
       UNION ALL SELECT (@dc + 3), @ds_futsal, '잠실 풋살 클럽', '송파구, 강동구에서 함께 풋살 하는 동호회입니다. 초보도 환영합니다.', 20, '모집중', '6', '14:00:00', '16:00:00', '2026-09-07 00:00:00'
       UNION ALL SELECT (@dc + 4), @ds_basketball, '반포 농구단', '서초구에서 함께 농구 하는 동호회입니다. 초보도 환영합니다.', 20, '모집중', '3,6', '18:00:00', '20:00:00', '2026-09-10 00:00:00'
       UNION ALL SELECT (@dc + 5), @ds_basketball, '광나루 바스켓', '강동구, 송파구에서 함께 농구 하는 동호회입니다. 초보도 환영합니다.', 20, '모집중', '7', '14:00:00', '16:00:00', '2026-09-13 00:00:00'
       UNION ALL SELECT (@dc + 6), @ds_jokgu, '한강 족구회', '광진구, 강동구에서 함께 족구 하는 동호회입니다. 초보도 환영합니다.', 20, '모집중', '6,7', '10:00:00', '12:00:00', '2026-09-16 00:00:00'
       UNION ALL SELECT (@dc + 7), @ds_badminton, '뚝섬 셔틀콕', '광진구, 성동구에서 함께 배드민턴 하는 동호회입니다. 초보도 환영합니다.', 20, '모집중', '2,5', '18:00:00', '20:00:00', '2026-09-19 00:00:00'
       UNION ALL SELECT (@dc + 8), @ds_tennis, '마포 랠리 테니스', '마포구에서 함께 테니스 하는 동호회입니다. 초보도 환영합니다.', 20, '모집중', '6', '10:00:00', '12:00:00', '2026-09-22 00:00:00'
) v
 WHERE @demo_ready = 1;

INSERT INTO club_region (club_id, region_id)
SELECT * FROM (
                 SELECT (@dc + 1) AS club_id, @dr_광진구 AS region_id
       UNION ALL SELECT (@dc + 2), @dr_마포구
       UNION ALL SELECT (@dc + 3), @dr_송파구
       UNION ALL SELECT (@dc + 3), @dr_강동구
       UNION ALL SELECT (@dc + 4), @dr_서초구
       UNION ALL SELECT (@dc + 5), @dr_강동구
       UNION ALL SELECT (@dc + 5), @dr_송파구
       UNION ALL SELECT (@dc + 6), @dr_광진구
       UNION ALL SELECT (@dc + 6), @dr_강동구
       UNION ALL SELECT (@dc + 7), @dr_광진구
       UNION ALL SELECT (@dc + 7), @dr_성동구
       UNION ALL SELECT (@dc + 8), @dr_마포구
) v
 WHERE @demo_ready = 1;

INSERT INTO club_member (club_member_id, club_id, user_id, role, joined_at)
SELECT * FROM (
                 SELECT (@dcm + 1) AS club_member_id, (@dc + 1) AS club_id, (@du + 51) AS user_id, '동호회장' AS role, '2026-09-01 00:00:00' AS joined_at
       UNION ALL SELECT (@dcm + 2), (@dc + 1), (@du + 54), '동호회원', '2026-09-02 00:00:00'
       UNION ALL SELECT (@dcm + 3), (@dc + 1), (@du + 23), '동호회원', '2026-09-03 00:00:00'
       UNION ALL SELECT (@dcm + 4), (@dc + 1), (@du + 72), '동호회원', '2026-09-04 00:00:00'
       UNION ALL SELECT (@dcm + 5), (@dc + 1), (@du + 65), '동호회원', '2026-09-05 00:00:00'
       UNION ALL SELECT (@dcm + 6), (@dc + 1), (@du + 4), '동호회원', '2026-09-06 00:00:00'
       UNION ALL SELECT (@dcm + 7), (@dc + 1), (@du + 59), '동호회원', '2026-09-07 00:00:00'
       UNION ALL SELECT (@dcm + 8), (@dc + 1), (@du + 83), '동호회원', '2026-09-08 00:00:00'
       UNION ALL SELECT (@dcm + 9), (@dc + 1), (@du + 74), '동호회원', '2026-09-09 00:00:00'
       UNION ALL SELECT (@dcm + 10), (@dc + 2), (@du + 81), '동호회장', '2026-09-04 00:00:00'
       UNION ALL SELECT (@dcm + 11), (@dc + 2), (@du + 85), '동호회원', '2026-09-05 00:00:00'
       UNION ALL SELECT (@dcm + 12), (@dc + 2), (@du + 47), '동호회원', '2026-09-06 00:00:00'
       UNION ALL SELECT (@dcm + 13), (@dc + 2), (@du + 91), '동호회원', '2026-09-07 00:00:00'
       UNION ALL SELECT (@dcm + 14), (@dc + 2), (@du + 31), '동호회원', '2026-09-08 00:00:00'
       UNION ALL SELECT (@dcm + 15), (@dc + 2), (@du + 61), '동호회원', '2026-09-09 00:00:00'
       UNION ALL SELECT (@dcm + 16), (@dc + 2), (@du + 79), '동호회원', '2026-09-10 00:00:00'
       UNION ALL SELECT (@dcm + 17), (@dc + 2), (@du + 78), '동호회원', '2026-09-11 00:00:00'
       UNION ALL SELECT (@dcm + 18), (@dc + 2), (@du + 77), '동호회원', '2026-09-12 00:00:00'
       UNION ALL SELECT (@dcm + 19), (@dc + 3), (@du + 49), '동호회장', '2026-09-07 00:00:00'
       UNION ALL SELECT (@dcm + 20), (@dc + 3), (@du + 45), '동호회원', '2026-09-08 00:00:00'
       UNION ALL SELECT (@dcm + 21), (@dc + 3), (@du + 100), '동호회원', '2026-09-09 00:00:00'
       UNION ALL SELECT (@dcm + 22), (@dc + 3), (@du + 41), '동호회원', '2026-09-10 00:00:00'
       UNION ALL SELECT (@dcm + 23), (@dc + 3), (@du + 1), '동호회원', '2026-09-11 00:00:00'
       UNION ALL SELECT (@dcm + 24), (@dc + 3), (@du + 58), '동호회원', '2026-09-12 00:00:00'
       UNION ALL SELECT (@dcm + 25), (@dc + 3), (@du + 34), '동호회원', '2026-09-13 00:00:00'
       UNION ALL SELECT (@dcm + 26), (@dc + 3), (@du + 3), '동호회원', '2026-09-14 00:00:00'
       UNION ALL SELECT (@dcm + 27), (@dc + 4), (@du + 18), '동호회장', '2026-09-10 00:00:00'
       UNION ALL SELECT (@dcm + 28), (@dc + 4), (@du + 75), '동호회원', '2026-09-11 00:00:00'
       UNION ALL SELECT (@dcm + 29), (@dc + 4), (@du + 62), '동호회원', '2026-09-12 00:00:00'
       UNION ALL SELECT (@dcm + 30), (@dc + 4), (@du + 96), '동호회원', '2026-09-13 00:00:00'
       UNION ALL SELECT (@dcm + 31), (@dc + 4), (@du + 9), '동호회원', '2026-09-14 00:00:00'
       UNION ALL SELECT (@dcm + 32), (@dc + 4), (@du + 27), '동호회원', '2026-09-15 00:00:00'
       UNION ALL SELECT (@dcm + 33), (@dc + 4), (@du + 46), '동호회원', '2026-09-16 00:00:00'
       UNION ALL SELECT (@dcm + 34), (@dc + 4), (@du + 13), '동호회원', '2026-09-17 00:00:00'
       UNION ALL SELECT (@dcm + 35), (@dc + 5), (@du + 8), '동호회장', '2026-09-13 00:00:00'
       UNION ALL SELECT (@dcm + 36), (@dc + 5), (@du + 14), '동호회원', '2026-09-14 00:00:00'
       UNION ALL SELECT (@dcm + 37), (@dc + 5), (@du + 60), '동호회원', '2026-09-15 00:00:00'
       UNION ALL SELECT (@dcm + 38), (@dc + 5), (@du + 7), '동호회원', '2026-09-16 00:00:00'
       UNION ALL SELECT (@dcm + 39), (@dc + 5), (@du + 50), '동호회원', '2026-09-17 00:00:00'
       UNION ALL SELECT (@dcm + 40), (@dc + 5), (@du + 19), '동호회원', '2026-09-18 00:00:00'
       UNION ALL SELECT (@dcm + 41), (@dc + 5), (@du + 67), '동호회원', '2026-09-19 00:00:00'
       UNION ALL SELECT (@dcm + 42), (@dc + 5), (@du + 6), '동호회원', '2026-09-20 00:00:00'
       UNION ALL SELECT (@dcm + 43), (@dc + 6), (@du + 69), '동호회장', '2026-09-16 00:00:00'
       UNION ALL SELECT (@dcm + 44), (@dc + 6), (@du + 82), '동호회원', '2026-09-17 00:00:00'
       UNION ALL SELECT (@dcm + 45), (@dc + 6), (@du + 47), '동호회원', '2026-09-18 00:00:00'
       UNION ALL SELECT (@dcm + 46), (@dc + 6), (@du + 48), '동호회원', '2026-09-19 00:00:00'
       UNION ALL SELECT (@dcm + 47), (@dc + 6), (@du + 37), '동호회원', '2026-09-20 00:00:00'
       UNION ALL SELECT (@dcm + 48), (@dc + 6), (@du + 89), '동호회원', '2026-09-21 00:00:00'
       UNION ALL SELECT (@dcm + 49), (@dc + 6), (@du + 33), '동호회원', '2026-09-22 00:00:00'
       UNION ALL SELECT (@dcm + 50), (@dc + 7), (@du + 46), '동호회장', '2026-09-19 00:00:00'
       UNION ALL SELECT (@dcm + 51), (@dc + 7), (@du + 52), '동호회원', '2026-09-20 00:00:00'
       UNION ALL SELECT (@dcm + 52), (@dc + 7), (@du + 51), '동호회원', '2026-09-21 00:00:00'
       UNION ALL SELECT (@dcm + 53), (@dc + 7), (@du + 75), '동호회원', '2026-09-22 00:00:00'
       UNION ALL SELECT (@dcm + 54), (@dc + 7), (@du + 57), '동호회원', '2026-09-23 00:00:00'
       UNION ALL SELECT (@dcm + 55), (@dc + 7), (@du + 56), '동호회원', '2026-09-24 00:00:00'
       UNION ALL SELECT (@dcm + 56), (@dc + 7), (@du + 8), '동호회원', '2026-09-25 00:00:00'
       UNION ALL SELECT (@dcm + 57), (@dc + 8), (@du + 84), '동호회장', '2026-09-22 00:00:00'
       UNION ALL SELECT (@dcm + 58), (@dc + 8), (@du + 35), '동호회원', '2026-09-23 00:00:00'
       UNION ALL SELECT (@dcm + 59), (@dc + 8), (@du + 79), '동호회원', '2026-09-24 00:00:00'
       UNION ALL SELECT (@dcm + 60), (@dc + 8), (@du + 82), '동호회원', '2026-09-25 00:00:00'
       UNION ALL SELECT (@dcm + 61), (@dc + 8), (@du + 8), '동호회원', '2026-09-26 00:00:00'
       UNION ALL SELECT (@dcm + 62), (@dc + 8), (@du + 17), '동호회원', '2026-09-27 00:00:00'
       UNION ALL SELECT (@dcm + 63), (@dc + 8), (@du + 4), '동호회원', '2026-09-28 00:00:00'
) v
 WHERE @demo_ready = 1;

-- 3. 매칭방 (지난 경기는 팀 배정을 끝낸 매칭 완료 상태로 넣고, 팀장은 참가자를 넣은 뒤 지정한다)
INSERT INTO social_match (match_id, facility_id, sport_id, created_by, title, start_at, end_at, min_participants, max_participants, team_a_capacity, team_b_capacity, fee_per_person, required_skill, status, assignment_method, assigned_by, assignment_confirmed_at, result_status, final_result, result_confirmed_at, rating_status, photo_url)
SELECT * FROM (
                 SELECT (@dm + 1) AS match_id, @df1 AS facility_id, @ds_futsal AS sport_id, @df1_mgr AS created_by, '송파 목요일 저녁 풋살 5:5' AS title, '2026-11-12 19:00:00' AS start_at, '2026-11-12 21:00:00' AS end_at, 10 AS min_participants, 10 AS max_participants, 5 AS team_a_capacity, 5 AS team_b_capacity, 0 AS fee_per_person, '중급' AS required_skill, '모집 중' AS status, 'MANUAL' AS assignment_method, NULL AS assigned_by, NULL AS assignment_confirmed_at, NULL AS result_status, NULL AS final_result, NULL AS result_confirmed_at, 'WAITING' AS rating_status, '/images/matches/soccer.jpg' AS photo_url
       UNION ALL SELECT (@dm + 2), @df2, @ds_futsal, @df2_mgr, '동작 목요일 저녁 풋살 6:6', '2026-11-12 20:00:00', '2026-11-12 22:00:00', 12, 12, 6, 6, 8000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 3), @df3, @ds_tennis, @df3_mgr, '금천 목요일 저녁 테니스 2:2', '2026-11-12 18:00:00', '2026-11-12 20:00:00', 4, 4, 2, 2, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 4), @df4, @ds_tennis, @df4_mgr, '강남 목요일 저녁 테니스 2:2', '2026-11-12 19:00:00', '2026-11-12 21:00:00', 4, 4, 2, 2, 4000, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 5), @df5, @ds_badminton, @df5_mgr, '광진 목요일 저녁 배드민턴 2:2', '2026-11-12 18:00:00', '2026-11-12 20:00:00', 4, 4, 2, 2, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 6), @df6, @ds_badminton, @df6_mgr, '성동 목요일 저녁 배드민턴 2:2', '2026-11-12 18:00:00', '2026-11-12 20:00:00', 4, 4, 2, 2, 5000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 7), @df7, @ds_basketball, @df7_mgr, '서초 목요일 저녁 농구 3:3', '2026-11-12 18:00:00', '2026-11-12 20:00:00', 6, 6, 3, 3, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 8), @df8, @ds_basketball, @df8_mgr, '용산 목요일 저녁 농구 4:4', '2026-11-12 18:00:00', '2026-11-12 20:00:00', 8, 8, 4, 4, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 9), @df9, @ds_jokgu, @df9_mgr, '마포 목요일 저녁 족구 3:3', '2026-11-12 18:00:00', '2026-11-12 20:00:00', 6, 6, 3, 3, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/jokgu.jpg'
       UNION ALL SELECT (@dm + 10), @df10, @ds_futsal, @df10_mgr, '동대문 금요일 저녁 풋살 5:5', '2026-11-13 19:00:00', '2026-11-13 21:00:00', 10, 10, 5, 5, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 11), @df11, @ds_futsal, @df11_mgr, '구로 금요일 저녁 풋살 6:6', '2026-11-13 20:00:00', '2026-11-13 22:00:00', 12, 12, 6, 6, 8000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 12), @df12, @ds_tennis, @df12_mgr, '종로 금요일 저녁 테니스 2:2', '2026-11-13 18:00:00', '2026-11-13 20:00:00', 4, 4, 2, 2, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 13), @df13, @ds_tennis, @df13_mgr, '도봉 금요일 저녁 테니스 2:2', '2026-11-13 19:00:00', '2026-11-13 21:00:00', 4, 4, 2, 2, 4000, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 14), @df14, @ds_badminton, @df14_mgr, '광진 금요일 저녁 배드민턴 2:2', '2026-11-13 18:00:00', '2026-11-13 20:00:00', 4, 4, 2, 2, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 15), @df6, @ds_badminton, @df6_mgr, '성동 금요일 저녁 배드민턴 2:2', '2026-11-13 18:00:00', '2026-11-13 20:00:00', 4, 4, 2, 2, 5000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 16), @df15, @ds_basketball, @df15_mgr, '강동 금요일 저녁 농구 3:3', '2026-11-13 18:00:00', '2026-11-13 20:00:00', 6, 6, 3, 3, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 17), @df16, @ds_basketball, @df16_mgr, '송파 금요일 저녁 농구 4:4', '2026-11-13 18:00:00', '2026-11-13 20:00:00', 8, 8, 4, 4, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 18), @df17, @ds_jokgu, @df17_mgr, '강서 금요일 저녁 족구 3:3', '2026-11-13 18:00:00', '2026-11-13 20:00:00', 6, 6, 3, 3, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/jokgu.jpg'
       UNION ALL SELECT (@dm + 19), @df18, @ds_futsal, @df18_mgr, '영등포 토요일 오전 풋살 5:5', '2026-11-14 10:00:00', '2026-11-14 12:00:00', 10, 10, 5, 5, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 20), @df19, @ds_futsal, @df19_mgr, '양천 토요일 오후 풋살 6:6', '2026-11-14 14:00:00', '2026-11-14 16:00:00', 12, 12, 6, 6, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 21), @df20, @ds_tennis, @df20_mgr, '서대문 토요일 오후 테니스 2:2', '2026-11-14 13:00:00', '2026-11-14 15:00:00', 4, 4, 2, 2, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 22), @df21, @ds_tennis, @df21_mgr, '관악 토요일 오전 테니스 2:2', '2026-11-14 10:00:00', '2026-11-14 12:00:00', 4, 4, 2, 2, 4000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 23), @df22, @ds_badminton, @df22_mgr, '동작 토요일 오전 배드민턴 2:2', '2026-11-14 10:00:00', '2026-11-14 12:00:00', 4, 4, 2, 2, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 24), @df22, @ds_badminton, @df22_mgr, '동작 토요일 오후 배드민턴 2:2', '2026-11-14 14:00:00', '2026-11-14 16:00:00', 4, 4, 2, 2, 5000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 25), @df23, @ds_basketball, @df23_mgr, '마포 토요일 오후 농구 4:4', '2026-11-14 14:00:00', '2026-11-14 16:00:00', 8, 8, 4, 4, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 26), @df24, @ds_basketball, @df24_mgr, '성동 토요일 오전 농구 3:3', '2026-11-14 10:00:00', '2026-11-14 12:00:00', 6, 6, 3, 3, 6000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 27), @df25, @ds_jokgu, @df25_mgr, '용산 토요일 오전 족구 3:3', '2026-11-14 10:00:00', '2026-11-14 12:00:00', 6, 6, 3, 3, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/jokgu.jpg'
       UNION ALL SELECT (@dm + 28), @df26, @ds_futsal, @df26_mgr, '서초 일요일 오전 풋살 5:5', '2026-11-15 10:00:00', '2026-11-15 12:00:00', 10, 10, 5, 5, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 29), @df27, @ds_futsal, @df27_mgr, '강동 일요일 오후 풋살 6:6', '2026-11-15 14:00:00', '2026-11-15 16:00:00', 12, 12, 6, 6, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 30), @df28, @ds_tennis, @df28_mgr, '성북 일요일 오후 테니스 2:2', '2026-11-15 13:00:00', '2026-11-15 15:00:00', 4, 4, 2, 2, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 31), @df29, @ds_tennis, @df29_mgr, '중구 일요일 오전 테니스 2:2', '2026-11-15 10:00:00', '2026-11-15 12:00:00', 4, 4, 2, 2, 4000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 32), @df30, @ds_badminton, @df30_mgr, '광진 일요일 오전 배드민턴 2:2', '2026-11-15 10:00:00', '2026-11-15 12:00:00', 4, 4, 2, 2, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 33), @df31, @ds_badminton, @df31_mgr, '성동 일요일 오후 배드민턴 2:2', '2026-11-15 14:00:00', '2026-11-15 16:00:00', 4, 4, 2, 2, 5000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 34), @df32, @ds_basketball, @df32_mgr, '광진 일요일 오후 농구 4:4', '2026-11-15 14:00:00', '2026-11-15 16:00:00', 8, 8, 4, 4, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 35), @df33, @ds_basketball, @df33_mgr, '서초 일요일 오전 농구 3:3', '2026-11-15 10:00:00', '2026-11-15 12:00:00', 6, 6, 3, 3, 6000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 36), @df34, @ds_jokgu, @df34_mgr, '강남 일요일 오전 족구 3:3', '2026-11-15 10:00:00', '2026-11-15 12:00:00', 6, 6, 3, 3, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/jokgu.jpg'
       UNION ALL SELECT (@dm + 37), @df35, @ds_futsal, @df35_mgr, '강서 월요일 저녁 풋살 5:5', '2026-11-16 19:00:00', '2026-11-16 21:00:00', 10, 10, 5, 5, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 38), @df36, @ds_futsal, @df36_mgr, '용산 월요일 저녁 풋살 6:6', '2026-11-16 20:00:00', '2026-11-16 22:00:00', 12, 12, 6, 6, 8000, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 39), @df37, @ds_tennis, @df37_mgr, '영등포 월요일 저녁 테니스 2:2', '2026-11-16 18:00:00', '2026-11-16 20:00:00', 4, 4, 2, 2, 0, '고급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 40), @df38, @ds_tennis, @df38_mgr, '구로 월요일 저녁 테니스 2:2', '2026-11-16 19:00:00', '2026-11-16 21:00:00', 4, 4, 2, 2, 4000, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 41), @df30, @ds_badminton, @df30_mgr, '광진 월요일 저녁 배드민턴 2:2', '2026-11-16 18:00:00', '2026-11-16 20:00:00', 4, 4, 2, 2, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 42), @df6, @ds_badminton, @df6_mgr, '성동 월요일 저녁 배드민턴 2:2', '2026-11-16 18:00:00', '2026-11-16 20:00:00', 4, 4, 2, 2, 5000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 43), @df39, @ds_basketball, @df39_mgr, '송파 월요일 저녁 농구 3:3', '2026-11-16 18:00:00', '2026-11-16 20:00:00', 6, 6, 3, 3, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 44), @df40, @ds_basketball, @df40_mgr, '마포 월요일 저녁 농구 4:4', '2026-11-16 18:00:00', '2026-11-16 20:00:00', 8, 8, 4, 4, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 45), @df41, @ds_jokgu, @df41_mgr, '강동 월요일 저녁 족구 3:3', '2026-11-16 18:00:00', '2026-11-16 20:00:00', 6, 6, 3, 3, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/jokgu.jpg'
       UNION ALL SELECT (@dm + 46), @df34, @ds_futsal, @df34_mgr, '강남 화요일 저녁 풋살 5:5', '2026-11-17 19:00:00', '2026-11-17 21:00:00', 10, 10, 5, 5, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 47), @df42, @ds_futsal, @df42_mgr, '마포 화요일 저녁 풋살 6:6', '2026-11-17 20:00:00', '2026-11-17 22:00:00', 12, 12, 6, 6, 8000, '고급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 48), @df35, @ds_tennis, @df35_mgr, '강서 화요일 저녁 테니스 2:2', '2026-11-17 18:00:00', '2026-11-17 20:00:00', 4, 4, 2, 2, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 49), @df43, @ds_tennis, @df43_mgr, '서초 화요일 저녁 테니스 2:2', '2026-11-17 19:00:00', '2026-11-17 21:00:00', 4, 4, 2, 2, 4000, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 50), @df5, @ds_badminton, @df5_mgr, '광진 화요일 저녁 배드민턴 2:2', '2026-11-17 18:00:00', '2026-11-17 20:00:00', 4, 4, 2, 2, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 51), @df6, @ds_badminton, @df6_mgr, '성동 화요일 저녁 배드민턴 2:2', '2026-11-17 18:00:00', '2026-11-17 20:00:00', 4, 4, 2, 2, 5000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 52), @df44, @ds_basketball, @df44_mgr, '용산 화요일 저녁 농구 3:3', '2026-11-17 18:00:00', '2026-11-17 20:00:00', 6, 6, 3, 3, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 53), @df15, @ds_basketball, @df15_mgr, '강동 화요일 저녁 농구 4:4', '2026-11-17 18:00:00', '2026-11-17 20:00:00', 8, 8, 4, 4, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 54), @df45, @ds_jokgu, @df45_mgr, '송파 화요일 저녁 족구 3:3', '2026-11-17 18:00:00', '2026-11-17 20:00:00', 6, 6, 3, 3, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/jokgu.jpg'
       UNION ALL SELECT (@dm + 55), @df19, @ds_futsal, @df19_mgr, '양천 수요일 저녁 풋살 5:5', '2026-11-18 19:00:00', '2026-11-18 21:00:00', 10, 10, 5, 5, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 56), @df10, @ds_futsal, @df10_mgr, '동대문 수요일 저녁 풋살 6:6', '2026-11-18 20:00:00', '2026-11-18 22:00:00', 12, 12, 6, 6, 8000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 57), @df46, @ds_tennis, @df46_mgr, '서대문 수요일 저녁 테니스 2:2', '2026-11-18 18:00:00', '2026-11-18 20:00:00', 4, 4, 2, 2, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 58), @df47, @ds_tennis, @df47_mgr, '동작 수요일 저녁 테니스 2:2', '2026-11-18 19:00:00', '2026-11-18 21:00:00', 4, 4, 2, 2, 4000, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 59), @df14, @ds_badminton, @df14_mgr, '광진 수요일 저녁 배드민턴 2:2', '2026-11-18 18:00:00', '2026-11-18 20:00:00', 4, 4, 2, 2, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 60), @df6, @ds_badminton, @df6_mgr, '성동 수요일 저녁 배드민턴 2:2', '2026-11-18 18:00:00', '2026-11-18 20:00:00', 4, 4, 2, 2, 5000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 61), @df48, @ds_basketball, @df48_mgr, '서초 수요일 저녁 농구 3:3', '2026-11-18 18:00:00', '2026-11-18 20:00:00', 6, 6, 3, 3, 0, '고급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 62), @df16, @ds_basketball, @df16_mgr, '송파 수요일 저녁 농구 4:4', '2026-11-18 18:00:00', '2026-11-18 20:00:00', 8, 8, 4, 4, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 63), @df49, @ds_jokgu, @df49_mgr, '동작 수요일 저녁 족구 3:3', '2026-11-18 18:00:00', '2026-11-18 20:00:00', 6, 6, 3, 3, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/jokgu.jpg'
       UNION ALL SELECT (@dm + 64), @df50, @ds_futsal, @df50_mgr, '구로 목요일 저녁 풋살 5:5', '2026-11-19 19:00:00', '2026-11-19 21:00:00', 10, 10, 5, 5, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 65), @df4, @ds_futsal, @df4_mgr, '강남 목요일 저녁 풋살 6:6', '2026-11-19 20:00:00', '2026-11-19 22:00:00', 12, 12, 6, 6, 8000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 66), @df51, @ds_tennis, @df51_mgr, '성북 목요일 저녁 테니스 2:2', '2026-11-19 18:00:00', '2026-11-19 20:00:00', 4, 4, 2, 2, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 67), @df13, @ds_tennis, @df13_mgr, '도봉 목요일 저녁 테니스 2:2', '2026-11-19 19:00:00', '2026-11-19 21:00:00', 4, 4, 2, 2, 4000, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 68), @df5, @ds_badminton, @df5_mgr, '광진 목요일 저녁 배드민턴 2:2', '2026-11-19 18:00:00', '2026-11-19 20:00:00', 4, 4, 2, 2, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 69), @df6, @ds_badminton, @df6_mgr, '성동 목요일 저녁 배드민턴 2:2', '2026-11-19 18:00:00', '2026-11-19 20:00:00', 4, 4, 2, 2, 5000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 70), @df40, @ds_basketball, @df40_mgr, '마포 목요일 저녁 농구 3:3', '2026-11-19 18:00:00', '2026-11-19 20:00:00', 6, 6, 3, 3, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 71), @df44, @ds_basketball, @df44_mgr, '용산 목요일 저녁 농구 4:4', '2026-11-19 18:00:00', '2026-11-19 20:00:00', 8, 8, 4, 4, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 72), @df17, @ds_jokgu, @df17_mgr, '강서 목요일 저녁 족구 3:3', '2026-11-19 18:00:00', '2026-11-19 20:00:00', 6, 6, 3, 3, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/jokgu.jpg'
       UNION ALL SELECT (@dm + 73), @df10, @ds_futsal, @df10_mgr, '동대문 금요일 저녁 풋살 5:5', '2026-11-20 19:00:00', '2026-11-20 21:00:00', 10, 10, 5, 5, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 74), @df19, @ds_futsal, @df19_mgr, '양천 금요일 저녁 풋살 6:6', '2026-11-20 20:00:00', '2026-11-20 22:00:00', 12, 12, 6, 6, 8000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 75), @df52, @ds_tennis, @df52_mgr, '관악 금요일 저녁 테니스 2:2', '2026-11-20 18:00:00', '2026-11-20 20:00:00', 4, 4, 2, 2, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 76), @df3, @ds_tennis, @df3_mgr, '금천 금요일 저녁 테니스 2:2', '2026-11-20 19:00:00', '2026-11-20 21:00:00', 4, 4, 2, 2, 4000, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 77), @df30, @ds_badminton, @df30_mgr, '광진 금요일 저녁 배드민턴 2:2', '2026-11-20 18:00:00', '2026-11-20 20:00:00', 4, 4, 2, 2, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 78), @df6, @ds_badminton, @df6_mgr, '성동 금요일 저녁 배드민턴 2:2', '2026-11-20 18:00:00', '2026-11-20 20:00:00', 4, 4, 2, 2, 5000, '고급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 79), @df15, @ds_basketball, @df15_mgr, '강동 금요일 저녁 농구 3:3', '2026-11-20 18:00:00', '2026-11-20 20:00:00', 6, 6, 3, 3, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 80), @df53, @ds_basketball, @df53_mgr, '광진 금요일 저녁 농구 4:4', '2026-11-20 18:00:00', '2026-11-20 20:00:00', 8, 8, 4, 4, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 81), @df4, @ds_jokgu, @df4_mgr, '강남 금요일 저녁 족구 3:3', '2026-11-20 18:00:00', '2026-11-20 20:00:00', 6, 6, 3, 3, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/jokgu.jpg'
       UNION ALL SELECT (@dm + 82), @df54, @ds_futsal, @df54_mgr, '영등포 토요일 오전 풋살 5:5', '2026-11-21 10:00:00', '2026-11-21 12:00:00', 10, 10, 5, 5, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 83), @df35, @ds_futsal, @df35_mgr, '강서 토요일 오후 풋살 6:6', '2026-11-21 14:00:00', '2026-11-21 16:00:00', 12, 12, 6, 6, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 84), @df55, @ds_tennis, @df55_mgr, '양천 토요일 오후 테니스 2:2', '2026-11-21 13:00:00', '2026-11-21 15:00:00', 4, 4, 2, 2, 0, '고급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 85), @df12, @ds_tennis, @df12_mgr, '종로 토요일 오전 테니스 2:2', '2026-11-21 10:00:00', '2026-11-21 12:00:00', 4, 4, 2, 2, 4000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 86), @df22, @ds_badminton, @df22_mgr, '동작 토요일 오전 배드민턴 2:2', '2026-11-21 10:00:00', '2026-11-21 12:00:00', 4, 4, 2, 2, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 87), @df22, @ds_badminton, @df22_mgr, '동작 토요일 오후 배드민턴 2:2', '2026-11-21 14:00:00', '2026-11-21 16:00:00', 4, 4, 2, 2, 5000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 88), @df39, @ds_basketball, @df39_mgr, '송파 토요일 오후 농구 4:4', '2026-11-21 14:00:00', '2026-11-21 16:00:00', 8, 8, 4, 4, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 89), @df8, @ds_basketball, @df8_mgr, '용산 토요일 오전 농구 3:3', '2026-11-21 10:00:00', '2026-11-21 12:00:00', 6, 6, 3, 3, 6000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 90), @df56, @ds_jokgu, @df56_mgr, '마포 토요일 오전 족구 3:3', '2026-11-21 10:00:00', '2026-11-21 12:00:00', 6, 6, 3, 3, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/jokgu.jpg'
       UNION ALL SELECT (@dm + 91), @df57, @ds_futsal, @df57_mgr, '서초 일요일 오전 풋살 5:5', '2026-11-22 10:00:00', '2026-11-22 12:00:00', 10, 10, 5, 5, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 92), @df58, @ds_futsal, @df58_mgr, '구로 일요일 오후 풋살 6:6', '2026-11-22 14:00:00', '2026-11-22 16:00:00', 12, 12, 6, 6, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 93), @df29, @ds_tennis, @df29_mgr, '중구 일요일 오후 테니스 2:2', '2026-11-22 13:00:00', '2026-11-22 15:00:00', 4, 4, 2, 2, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 94), @df59, @ds_tennis, @df59_mgr, '강동 일요일 오전 테니스 2:2', '2026-11-22 10:00:00', '2026-11-22 12:00:00', 4, 4, 2, 2, 4000, '고급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 95), @df22, @ds_badminton, @df22_mgr, '동작 일요일 오전 배드민턴 2:2', '2026-11-22 10:00:00', '2026-11-22 12:00:00', 4, 4, 2, 2, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 96), @df22, @ds_badminton, @df22_mgr, '동작 일요일 오후 배드민턴 2:2', '2026-11-22 14:00:00', '2026-11-22 16:00:00', 4, 4, 2, 2, 5000, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 97), @df24, @ds_basketball, @df24_mgr, '성동 일요일 오후 농구 4:4', '2026-11-22 14:00:00', '2026-11-22 16:00:00', 8, 8, 4, 4, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 98), @df23, @ds_basketball, @df23_mgr, '마포 일요일 오전 농구 3:3', '2026-11-22 10:00:00', '2026-11-22 12:00:00', 6, 6, 3, 3, 6000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 99), @df60, @ds_jokgu, @df60_mgr, '용산 일요일 오전 족구 3:3', '2026-11-22 10:00:00', '2026-11-22 12:00:00', 6, 6, 3, 3, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/jokgu.jpg'
       UNION ALL SELECT (@dm + 100), @df61, @ds_futsal, @df61_mgr, '송파 월요일 저녁 풋살 5:5', '2026-11-23 19:00:00', '2026-11-23 21:00:00', 10, 10, 5, 5, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 101), @df62, @ds_futsal, @df62_mgr, '마포 월요일 저녁 풋살 6:6', '2026-11-23 20:00:00', '2026-11-23 22:00:00', 12, 12, 6, 6, 8000, '고급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 102), @df37, @ds_tennis, @df37_mgr, '영등포 월요일 저녁 테니스 2:2', '2026-11-23 18:00:00', '2026-11-23 20:00:00', 4, 4, 2, 2, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 103), @df63, @ds_tennis, @df63_mgr, '서대문 월요일 저녁 테니스 2:2', '2026-11-23 19:00:00', '2026-11-23 21:00:00', 4, 4, 2, 2, 4000, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 104), @df14, @ds_badminton, @df14_mgr, '광진 월요일 저녁 배드민턴 2:2', '2026-11-23 18:00:00', '2026-11-23 20:00:00', 4, 4, 2, 2, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 105), @df6, @ds_badminton, @df6_mgr, '성동 월요일 저녁 배드민턴 2:2', '2026-11-23 18:00:00', '2026-11-23 20:00:00', 4, 4, 2, 2, 5000, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 106), @df7, @ds_basketball, @df7_mgr, '서초 월요일 저녁 농구 3:3', '2026-11-23 18:00:00', '2026-11-23 20:00:00', 6, 6, 3, 3, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 107), @df15, @ds_basketball, @df15_mgr, '강동 월요일 저녁 농구 4:4', '2026-11-23 18:00:00', '2026-11-23 20:00:00', 8, 8, 4, 4, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 108), @df64, @ds_jokgu, @df64_mgr, '강동 월요일 저녁 족구 3:3', '2026-11-23 18:00:00', '2026-11-23 20:00:00', 6, 6, 3, 3, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/jokgu.jpg'
       UNION ALL SELECT (@dm + 109), @df36, @ds_futsal, @df36_mgr, '용산 화요일 저녁 풋살 5:5', '2026-11-24 19:00:00', '2026-11-24 21:00:00', 10, 10, 5, 5, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 110), @df34, @ds_futsal, @df34_mgr, '강남 화요일 저녁 풋살 6:6', '2026-11-24 20:00:00', '2026-11-24 22:00:00', 12, 12, 6, 6, 8000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 111), @df65, @ds_tennis, @df65_mgr, '구로 화요일 저녁 테니스 2:2', '2026-11-24 18:00:00', '2026-11-24 20:00:00', 4, 4, 2, 2, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 112), @df21, @ds_tennis, @df21_mgr, '관악 화요일 저녁 테니스 2:2', '2026-11-24 19:00:00', '2026-11-24 21:00:00', 4, 4, 2, 2, 4000, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 113), @df30, @ds_badminton, @df30_mgr, '광진 화요일 저녁 배드민턴 2:2', '2026-11-24 18:00:00', '2026-11-24 20:00:00', 4, 4, 2, 2, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 114), @df6, @ds_badminton, @df6_mgr, '성동 화요일 저녁 배드민턴 2:2', '2026-11-24 18:00:00', '2026-11-24 20:00:00', 4, 4, 2, 2, 5000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 115), @df33, @ds_basketball, @df33_mgr, '서초 화요일 저녁 농구 3:3', '2026-11-24 18:00:00', '2026-11-24 20:00:00', 6, 6, 3, 3, 0, '고급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 116), @df39, @ds_basketball, @df39_mgr, '송파 화요일 저녁 농구 4:4', '2026-11-24 18:00:00', '2026-11-24 20:00:00', 8, 8, 4, 4, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 117), @df17, @ds_jokgu, @df17_mgr, '강서 화요일 저녁 족구 3:3', '2026-11-24 18:00:00', '2026-11-24 20:00:00', 6, 6, 3, 3, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/jokgu.jpg'
       UNION ALL SELECT (@dm + 118), @df10, @ds_futsal, @df10_mgr, '동대문 수요일 저녁 풋살 5:5', '2026-11-25 19:00:00', '2026-11-25 21:00:00', 10, 10, 5, 5, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 119), @df35, @ds_futsal, @df35_mgr, '강서 수요일 저녁 풋살 6:6', '2026-11-25 20:00:00', '2026-11-25 22:00:00', 12, 12, 6, 6, 8000, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 120), @df28, @ds_tennis, @df28_mgr, '성북 수요일 저녁 테니스 2:2', '2026-11-25 18:00:00', '2026-11-25 20:00:00', 4, 4, 2, 2, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 121), @df29, @ds_tennis, @df29_mgr, '중구 수요일 저녁 테니스 2:2', '2026-11-25 19:00:00', '2026-11-25 21:00:00', 4, 4, 2, 2, 4000, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 122), @df14, @ds_badminton, @df14_mgr, '광진 수요일 저녁 배드민턴 2:2', '2026-11-25 18:00:00', '2026-11-25 20:00:00', 4, 4, 2, 2, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 123), @df6, @ds_badminton, @df6_mgr, '성동 수요일 저녁 배드민턴 2:2', '2026-11-25 18:00:00', '2026-11-25 20:00:00', 4, 4, 2, 2, 5000, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 124), @df8, @ds_basketball, @df8_mgr, '용산 수요일 저녁 농구 3:3', '2026-11-25 18:00:00', '2026-11-25 20:00:00', 6, 6, 3, 3, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 125), @df23, @ds_basketball, @df23_mgr, '마포 수요일 저녁 농구 4:4', '2026-11-25 18:00:00', '2026-11-25 20:00:00', 8, 8, 4, 4, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 126), @df45, @ds_jokgu, @df45_mgr, '송파 수요일 저녁 족구 3:3', '2026-11-25 18:00:00', '2026-11-25 20:00:00', 6, 6, 3, 3, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/jokgu.jpg'
       UNION ALL SELECT (@dm + 127), @df19, @ds_futsal, @df19_mgr, '양천 목요일 저녁 풋살 5:5', '2026-11-26 19:00:00', '2026-11-26 21:00:00', 10, 10, 5, 5, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 128), @df11, @ds_futsal, @df11_mgr, '구로 목요일 저녁 풋살 6:6', '2026-11-26 20:00:00', '2026-11-26 22:00:00', 12, 12, 6, 6, 8000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 129), @df12, @ds_tennis, @df12_mgr, '종로 목요일 저녁 테니스 2:2', '2026-11-26 18:00:00', '2026-11-26 20:00:00', 4, 4, 2, 2, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 130), @df13, @ds_tennis, @df13_mgr, '도봉 목요일 저녁 테니스 2:2', '2026-11-26 19:00:00', '2026-11-26 21:00:00', 4, 4, 2, 2, 4000, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 131), @df5, @ds_badminton, @df5_mgr, '광진 목요일 저녁 배드민턴 2:2', '2026-11-26 18:00:00', '2026-11-26 20:00:00', 4, 4, 2, 2, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 132), @df6, @ds_badminton, @df6_mgr, '성동 목요일 저녁 배드민턴 2:2', '2026-11-26 18:00:00', '2026-11-26 20:00:00', 4, 4, 2, 2, 5000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 133), @df15, @ds_basketball, @df15_mgr, '강동 목요일 저녁 농구 3:3', '2026-11-26 18:00:00', '2026-11-26 20:00:00', 6, 6, 3, 3, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 134), @df32, @ds_basketball, @df32_mgr, '광진 목요일 저녁 농구 4:4', '2026-11-26 18:00:00', '2026-11-26 20:00:00', 8, 8, 4, 4, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 135), @df34, @ds_jokgu, @df34_mgr, '강남 목요일 저녁 족구 3:3', '2026-11-26 18:00:00', '2026-11-26 20:00:00', 6, 6, 3, 3, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/jokgu.jpg'
       UNION ALL SELECT (@dm + 136), @df49, @ds_futsal, @df49_mgr, '동작 금요일 저녁 풋살 5:5', '2026-11-27 19:00:00', '2026-11-27 21:00:00', 10, 10, 5, 5, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 137), @df66, @ds_futsal, @df66_mgr, '서초 금요일 저녁 풋살 6:6', '2026-11-27 20:00:00', '2026-11-27 22:00:00', 12, 12, 6, 6, 8000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 138), @df3, @ds_tennis, @df3_mgr, '금천 금요일 저녁 테니스 2:2', '2026-11-27 18:00:00', '2026-11-27 20:00:00', 4, 4, 2, 2, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 139), @df37, @ds_tennis, @df37_mgr, '영등포 금요일 저녁 테니스 2:2', '2026-11-27 19:00:00', '2026-11-27 21:00:00', 4, 4, 2, 2, 4000, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 140), @df6, @ds_badminton, @df6_mgr, '성동 금요일 저녁 배드민턴 2:2', '2026-11-27 18:00:00', '2026-11-27 20:00:00', 4, 4, 2, 2, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 141), @df30, @ds_badminton, @df30_mgr, '광진 금요일 저녁 배드민턴 2:2', '2026-11-27 18:00:00', '2026-11-27 20:00:00', 4, 4, 2, 2, 5000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 142), @df48, @ds_basketball, @df48_mgr, '서초 금요일 저녁 농구 3:3', '2026-11-27 18:00:00', '2026-11-27 20:00:00', 6, 6, 3, 3, 0, '고급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 143), @df44, @ds_basketball, @df44_mgr, '용산 금요일 저녁 농구 4:4', '2026-11-27 18:00:00', '2026-11-27 20:00:00', 8, 8, 4, 4, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 144), @df67, @ds_jokgu, @df67_mgr, '강동 금요일 저녁 족구 3:3', '2026-11-27 18:00:00', '2026-11-27 20:00:00', 6, 6, 3, 3, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/jokgu.jpg'
       UNION ALL SELECT (@dm + 145), @df54, @ds_futsal, @df54_mgr, '영등포 토요일 오전 풋살 5:5', '2026-11-28 10:00:00', '2026-11-28 12:00:00', 10, 10, 5, 5, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 146), @df68, @ds_futsal, @df68_mgr, '강동 토요일 오후 풋살 6:6', '2026-11-28 14:00:00', '2026-11-28 16:00:00', 12, 12, 6, 6, 0, '고급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 147), @df69, @ds_tennis, @df69_mgr, '양천 토요일 오후 테니스 2:2', '2026-11-28 13:00:00', '2026-11-28 15:00:00', 4, 4, 2, 2, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 148), @df70, @ds_tennis, @df70_mgr, '마포 토요일 오전 테니스 2:2', '2026-11-28 10:00:00', '2026-11-28 12:00:00', 4, 4, 2, 2, 4000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 149), @df22, @ds_badminton, @df22_mgr, '동작 토요일 오전 배드민턴 2:2', '2026-11-28 10:00:00', '2026-11-28 12:00:00', 4, 4, 2, 2, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 150), @df22, @ds_badminton, @df22_mgr, '동작 토요일 오후 배드민턴 2:2', '2026-11-28 14:00:00', '2026-11-28 16:00:00', 4, 4, 2, 2, 5000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
) v
 WHERE @demo_ready = 1;

INSERT INTO social_match (match_id, facility_id, sport_id, created_by, title, start_at, end_at, min_participants, max_participants, team_a_capacity, team_b_capacity, fee_per_person, required_skill, status, assignment_method, assigned_by, assignment_confirmed_at, result_status, final_result, result_confirmed_at, rating_status, photo_url)
SELECT * FROM (
                 SELECT (@dm + 151) AS match_id, @df24 AS facility_id, @ds_basketball AS sport_id, @df24_mgr AS created_by, '성동 토요일 오후 농구 4:4' AS title, '2026-11-28 14:00:00' AS start_at, '2026-11-28 16:00:00' AS end_at, 8 AS min_participants, 8 AS max_participants, 4 AS team_a_capacity, 4 AS team_b_capacity, 0 AS fee_per_person, '고급' AS required_skill, '모집 중' AS status, 'MANUAL' AS assignment_method, NULL AS assigned_by, NULL AS assignment_confirmed_at, NULL AS result_status, NULL AS final_result, NULL AS result_confirmed_at, 'WAITING' AS rating_status, '/images/matches/basketball.jpg' AS photo_url
       UNION ALL SELECT (@dm + 152), @df16, @ds_basketball, @df16_mgr, '송파 토요일 오전 농구 3:3', '2026-11-28 10:00:00', '2026-11-28 12:00:00', 6, 6, 3, 3, 6000, '고급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 153), @df17, @ds_jokgu, @df17_mgr, '강서 토요일 오전 족구 3:3', '2026-11-28 10:00:00', '2026-11-28 12:00:00', 6, 6, 3, 3, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/jokgu.jpg'
       UNION ALL SELECT (@dm + 154), @df18, @ds_futsal, @df18_mgr, '영등포 일요일 오전 풋살 5:5', '2026-11-29 10:00:00', '2026-11-29 12:00:00', 10, 10, 5, 5, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 155), @df10, @ds_futsal, @df10_mgr, '동대문 일요일 오후 풋살 6:6', '2026-11-29 14:00:00', '2026-11-29 16:00:00', 12, 12, 6, 6, 0, '고급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 156), @df71, @ds_tennis, @df71_mgr, '강남 일요일 오후 테니스 2:2', '2026-11-29 13:00:00', '2026-11-29 15:00:00', 4, 4, 2, 2, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 157), @df52, @ds_tennis, @df52_mgr, '관악 일요일 오전 테니스 2:2', '2026-11-29 10:00:00', '2026-11-29 12:00:00', 4, 4, 2, 2, 4000, '고급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 158), @df22, @ds_badminton, @df22_mgr, '동작 일요일 오전 배드민턴 2:2', '2026-11-29 10:00:00', '2026-11-29 12:00:00', 4, 4, 2, 2, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 159), @df22, @ds_badminton, @df22_mgr, '동작 일요일 오후 배드민턴 2:2', '2026-11-29 14:00:00', '2026-11-29 16:00:00', 4, 4, 2, 2, 5000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 160), @df40, @ds_basketball, @df40_mgr, '마포 일요일 오후 농구 4:4', '2026-11-29 14:00:00', '2026-11-29 16:00:00', 8, 8, 4, 4, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 161), @df15, @ds_basketball, @df15_mgr, '강동 일요일 오전 농구 3:3', '2026-11-29 10:00:00', '2026-11-29 12:00:00', 6, 6, 3, 3, 6000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 162), @df72, @ds_jokgu, @df72_mgr, '용산 일요일 오전 족구 3:3', '2026-11-29 10:00:00', '2026-11-29 12:00:00', 6, 6, 3, 3, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/jokgu.jpg'
       UNION ALL SELECT (@dm + 163), @df50, @ds_futsal, @df50_mgr, '구로 월요일 저녁 풋살 5:5', '2026-11-30 19:00:00', '2026-11-30 21:00:00', 10, 10, 5, 5, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 164), @df1, @ds_futsal, @df1_mgr, '송파 월요일 저녁 풋살 6:6', '2026-11-30 20:00:00', '2026-11-30 22:00:00', 12, 12, 6, 6, 8000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 165), @df51, @ds_tennis, @df51_mgr, '성북 월요일 저녁 테니스 2:2', '2026-11-30 18:00:00', '2026-11-30 20:00:00', 4, 4, 2, 2, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 166), @df20, @ds_tennis, @df20_mgr, '서대문 월요일 저녁 테니스 2:2', '2026-11-30 19:00:00', '2026-11-30 21:00:00', 4, 4, 2, 2, 4000, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 167), @df5, @ds_badminton, @df5_mgr, '광진 월요일 저녁 배드민턴 2:2', '2026-11-30 18:00:00', '2026-11-30 20:00:00', 4, 4, 2, 2, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 168), @df6, @ds_badminton, @df6_mgr, '성동 월요일 저녁 배드민턴 2:2', '2026-11-30 18:00:00', '2026-11-30 20:00:00', 4, 4, 2, 2, 5000, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 169), @df48, @ds_basketball, @df48_mgr, '서초 월요일 저녁 농구 3:3', '2026-11-30 18:00:00', '2026-11-30 20:00:00', 6, 6, 3, 3, 0, '중급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 170), @df53, @ds_basketball, @df53_mgr, '광진 월요일 저녁 농구 4:4', '2026-11-30 18:00:00', '2026-11-30 20:00:00', 8, 8, 4, 4, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 171), @df4, @ds_jokgu, @df4_mgr, '강남 월요일 저녁 족구 3:3', '2026-11-30 18:00:00', '2026-11-30 20:00:00', 6, 6, 3, 3, 0, '초급', '모집 중', 'MANUAL', NULL, NULL, NULL, NULL, NULL, 'WAITING', '/images/matches/jokgu.jpg'
       UNION ALL SELECT (@dm + 172), @df35, @ds_futsal, @df35_mgr, '강서 일요일 오전 풋살 5:5', '2026-11-01 10:00:00', '2026-11-01 12:00:00', 10, 10, 5, 5, 0, '고급', '매칭 완료', 'AUTO', @df35_mgr, '2026-11-01 04:00:00', 'CONFIRMED', 'A_WIN', '2026-11-01 13:10:00', 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 173), @df13, @ds_tennis, @df13_mgr, '도봉 일요일 오후 테니스 2:2', '2026-11-01 14:00:00', '2026-11-01 16:00:00', 4, 4, 2, 2, 0, '중급', '매칭 완료', 'AUTO', @df13_mgr, '2026-11-01 08:00:00', 'CONFIRMED', 'DRAW', '2026-11-01 17:10:00', 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 174), @df6, @ds_badminton, @df6_mgr, '성동 월요일 저녁 배드민턴 2:2', '2026-11-02 18:00:00', '2026-11-02 20:00:00', 4, 4, 2, 2, 0, '초급', '매칭 완료', 'AUTO', @df6_mgr, '2026-11-02 12:00:00', 'CONFIRMED', 'A_WIN', '2026-11-02 21:10:00', 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 175), @df44, @ds_basketball, @df44_mgr, '용산 월요일 저녁 농구 3:3', '2026-11-02 18:00:00', '2026-11-02 20:00:00', 6, 6, 3, 3, 0, '중급', '매칭 완료', 'AUTO', @df44_mgr, '2026-11-02 12:00:00', 'CONFIRMED', 'A_WIN', '2026-11-02 21:10:00', 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 176), @df9, @ds_jokgu, @df9_mgr, '마포 화요일 저녁 족구 3:3', '2026-11-03 18:00:00', '2026-11-03 20:00:00', 6, 6, 3, 3, 0, '고급', '매칭 완료', 'AUTO', @df9_mgr, '2026-11-03 12:00:00', 'CONFIRMED', 'A_WIN', '2026-11-03 21:10:00', 'WAITING', '/images/matches/jokgu.jpg'
       UNION ALL SELECT (@dm + 177), @df19, @ds_futsal, @df19_mgr, '양천 화요일 저녁 풋살 5:5', '2026-11-03 18:00:00', '2026-11-03 20:00:00', 10, 10, 5, 5, 0, '중급', '매칭 완료', 'AUTO', @df19_mgr, '2026-11-03 12:00:00', 'CONFIRMED', 'A_WIN', '2026-11-03 21:10:00', 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 178), @df3, @ds_tennis, @df3_mgr, '금천 수요일 저녁 테니스 2:2', '2026-11-04 18:00:00', '2026-11-04 20:00:00', 4, 4, 2, 2, 0, '중급', '매칭 완료', 'AUTO', @df3_mgr, '2026-11-04 12:00:00', 'CONFIRMED', 'A_WIN', '2026-11-04 21:10:00', 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 179), @df14, @ds_badminton, @df14_mgr, '광진 수요일 저녁 배드민턴 2:2', '2026-11-04 18:00:00', '2026-11-04 20:00:00', 4, 4, 2, 2, 0, '중급', '매칭 완료', 'AUTO', @df14_mgr, '2026-11-04 12:00:00', 'CONFIRMED', 'B_WIN', '2026-11-04 21:10:00', 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 180), @df16, @ds_basketball, @df16_mgr, '송파 목요일 저녁 농구 3:3', '2026-11-05 18:00:00', '2026-11-05 20:00:00', 6, 6, 3, 3, 0, '초급', '매칭 완료', 'AUTO', @df16_mgr, '2026-11-05 12:00:00', 'CONFIRMED', 'A_WIN', '2026-11-05 21:10:00', 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 181), @df45, @ds_jokgu, @df45_mgr, '송파 목요일 저녁 족구 3:3', '2026-11-05 18:00:00', '2026-11-05 20:00:00', 6, 6, 3, 3, 0, '중급', '매칭 완료', 'AUTO', @df45_mgr, '2026-11-05 12:00:00', 'CONFIRMED', 'A_WIN', '2026-11-05 21:10:00', 'WAITING', '/images/matches/jokgu.jpg'
       UNION ALL SELECT (@dm + 182), @df18, @ds_futsal, @df18_mgr, '영등포 금요일 저녁 풋살 5:5', '2026-11-06 18:00:00', '2026-11-06 20:00:00', 10, 10, 5, 5, 0, '고급', '매칭 완료', 'AUTO', @df18_mgr, '2026-11-06 12:00:00', 'CONFIRMED', 'A_WIN', '2026-11-06 21:10:00', 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 183), @df29, @ds_tennis, @df29_mgr, '중구 금요일 저녁 테니스 2:2', '2026-11-06 18:00:00', '2026-11-06 20:00:00', 4, 4, 2, 2, 0, '고급', '매칭 완료', 'AUTO', @df29_mgr, '2026-11-06 12:00:00', 'CONFIRMED', 'DRAW', '2026-11-06 21:10:00', 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 184), @df22, @ds_badminton, @df22_mgr, '동작 토요일 오전 배드민턴 2:2', '2026-11-07 10:00:00', '2026-11-07 12:00:00', 4, 4, 2, 2, 0, '중급', '매칭 완료', 'AUTO', @df22_mgr, '2026-11-07 04:00:00', 'CONFIRMED', 'A_WIN', '2026-11-07 13:10:00', 'WAITING', '/images/matches/badminton.jpg'
       UNION ALL SELECT (@dm + 185), @df24, @ds_basketball, @df24_mgr, '성동 토요일 오후 농구 3:3', '2026-11-07 14:00:00', '2026-11-07 16:00:00', 6, 6, 3, 3, 0, '중급', '매칭 완료', 'AUTO', @df24_mgr, '2026-11-07 08:00:00', 'CONFIRMED', 'A_WIN', '2026-11-07 17:10:00', 'WAITING', '/images/matches/basketball.jpg'
       UNION ALL SELECT (@dm + 186), @df49, @ds_jokgu, @df49_mgr, '동작 일요일 오전 족구 3:3', '2026-11-08 10:00:00', '2026-11-08 12:00:00', 6, 6, 3, 3, 0, '초급', '매칭 완료', 'AUTO', @df49_mgr, '2026-11-08 04:00:00', 'CONFIRMED', 'B_WIN', '2026-11-08 13:10:00', 'WAITING', '/images/matches/jokgu.jpg'
       UNION ALL SELECT (@dm + 187), @df73, @ds_futsal, @df73_mgr, '강남 일요일 오후 풋살 5:5', '2026-11-08 14:00:00', '2026-11-08 16:00:00', 10, 10, 5, 5, 0, '초급', '매칭 완료', 'AUTO', @df73_mgr, '2026-11-08 08:00:00', 'CONFIRMED', 'B_WIN', '2026-11-08 17:10:00', 'WAITING', '/images/matches/soccer.jpg'
       UNION ALL SELECT (@dm + 188), @df12, @ds_tennis, @df12_mgr, '종로 월요일 저녁 테니스 2:2', '2026-11-09 18:00:00', '2026-11-09 20:00:00', 4, 4, 2, 2, 0, '고급', '매칭 완료', 'AUTO', @df12_mgr, '2026-11-09 12:00:00', 'CONFIRMED', 'DRAW', '2026-11-09 21:10:00', 'WAITING', '/images/matches/tennis.jpg'
       UNION ALL SELECT (@dm + 189), @df30, @ds_badminton, @df30_mgr, '광진 월요일 저녁 배드민턴 2:2', '2026-11-09 18:00:00', '2026-11-09 20:00:00', 4, 4, 2, 2, 0, '중급', '매칭 완료', 'AUTO', @df30_mgr, '2026-11-09 12:00:00', 'CONFIRMED', 'B_WIN', '2026-11-09 21:10:00', 'WAITING', '/images/matches/badminton.jpg'
) v
 WHERE @demo_ready = 1;

-- 4. 신청 (팀 신청은 팀원마다 한 행, 묶음 번호는 아래에서 대표 행 번호로 채운다)
INSERT INTO match_application (application_id, match_id, applicant_user_id, club_id, application_type, status, receipt_no, created_at, confirmed_at)
SELECT * FROM (
                 SELECT (@da + 1) AS application_id, (@dm + 1) AS match_id, (@du + 68) AS applicant_user_id, NULL AS club_id, '개인' AS application_type, '확정' AS status, 'RCPT-20261110-VC6BXZB8' AS receipt_no, '2026-11-10 04:35:02' AS created_at, '2026-11-10 06:35:02' AS confirmed_at
       UNION ALL SELECT (@da + 2), (@dm + 1), (@du + 31), NULL, '개인', '확정', 'RCPT-20261109-BJZRBFK7', '2026-11-09 20:59:36', '2026-11-09 21:59:36'
       UNION ALL SELECT (@da + 3), (@dm + 1), (@du + 81), NULL, '개인', '확정', 'RCPT-20261111-2E8DXB3K', '2026-11-11 02:04:21', '2026-11-11 03:04:21'
       UNION ALL SELECT (@da + 4), (@dm + 1), (@du + 97), NULL, '개인', '확정', 'RCPT-20261110-337XTWPP', '2026-11-10 00:35:57', '2026-11-10 06:35:57'
       UNION ALL SELECT (@da + 5), (@dm + 1), (@du + 91), NULL, '개인', '확정', 'RCPT-20261109-NX6PNY6K', '2026-11-09 20:02:09', '2026-11-10 01:02:09'
       UNION ALL SELECT (@da + 6), (@dm + 1), (@du + 69), NULL, '개인', '확정', 'RCPT-20261110-D5N7JPKX', '2026-11-10 15:54:36', '2026-11-10 17:54:36'
       UNION ALL SELECT (@da + 7), (@dm + 1), (@du + 30), NULL, '개인', '확정', 'RCPT-20261109-GTQZDZ72', '2026-11-09 13:08:48', '2026-11-09 15:08:48'
       UNION ALL SELECT (@da + 8), (@dm + 1), (@du + 1), NULL, '개인', '확정', 'RCPT-20261111-G9X9QDU8', '2026-11-11 01:31:52', '2026-11-11 02:31:52'
       UNION ALL SELECT (@da + 9), (@dm + 1), (@du + 78), NULL, '개인', '확정', 'RCPT-20261111-A2ZY9M8Y', '2026-11-11 08:26:08', '2026-11-11 09:26:08'
       UNION ALL SELECT (@da + 10), (@dm + 1), (@du + 74), NULL, '개인', '확정', 'RCPT-20261109-AZYWLSDQ', '2026-11-09 19:15:09', '2026-11-09 22:15:09'
       UNION ALL SELECT (@da + 11), (@dm + 3), (@du + 66), NULL, '개인', '확정', 'RCPT-20261111-QXCYHJVK', '2026-11-11 08:16:11', '2026-11-11 10:16:11'
       UNION ALL SELECT (@da + 12), (@dm + 3), (@du + 82), NULL, '개인', '확정', 'RCPT-20261109-RJLLUK7T', '2026-11-09 18:51:41', '2026-11-10 00:51:41'
       UNION ALL SELECT (@da + 13), (@dm + 3), (@du + 50), NULL, '개인', '확정', 'RCPT-20261110-JKTSADEP', '2026-11-10 18:18:57', '2026-11-11 00:18:57'
       UNION ALL SELECT (@da + 14), (@dm + 3), (@du + 19), NULL, '개인', '확정', 'RCPT-20261110-C4S84MGZ', '2026-11-10 22:26:27', '2026-11-11 03:26:27'
       UNION ALL SELECT (@da + 15), (@dm + 5), (@du + 85), NULL, '개인', '확정', 'RCPT-20261111-B8WT5NTQ', '2026-11-11 00:12:30', '2026-11-11 04:12:30'
       UNION ALL SELECT (@da + 16), (@dm + 5), (@du + 25), NULL, '개인', '확정', 'RCPT-20261110-NNNYXNXA', '2026-11-10 20:01:21', '2026-11-10 23:01:21'
       UNION ALL SELECT (@da + 17), (@dm + 5), (@du + 43), NULL, '개인', '대기', 'RCPT-20261111-8AY82E3D', '2026-11-11 07:14:37', NULL
       UNION ALL SELECT (@da + 18), (@dm + 7), (@du + 6), NULL, '개인', '확정', 'RCPT-20261111-NDKGAEP5', '2026-11-11 08:02:25', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 19), (@dm + 7), (@du + 73), NULL, '개인', '확정', 'RCPT-20261110-TUK5RG23', '2026-11-10 00:22:30', '2026-11-10 04:22:30'
       UNION ALL SELECT (@da + 20), (@dm + 7), (@du + 71), NULL, '개인', '확정', 'RCPT-20261111-WSGXB89R', '2026-11-11 00:22:45', '2026-11-11 03:22:45'
       UNION ALL SELECT (@da + 21), (@dm + 7), (@du + 14), NULL, '개인', '확정', 'RCPT-20261109-NWQUJGZZ', '2026-11-09 09:53:27', '2026-11-09 13:53:27'
       UNION ALL SELECT (@da + 22), (@dm + 7), (@du + 79), NULL, '개인', '확정', 'RCPT-20261109-F79NN52U', '2026-11-09 22:02:59', '2026-11-10 04:02:59'
       UNION ALL SELECT (@da + 23), (@dm + 7), (@du + 96), NULL, '개인', '확정', 'RCPT-20261110-5RT9BWZ8', '2026-11-10 10:37:58', '2026-11-10 15:37:58'
       UNION ALL SELECT (@da + 24), (@dm + 8), (@du + 29), NULL, '개인', '확정', 'RCPT-20261110-FSZQ6TSR', '2026-11-10 21:57:31', '2026-11-10 22:57:31'
       UNION ALL SELECT (@da + 25), (@dm + 8), (@du + 39), NULL, '개인', '확정', 'RCPT-20261110-H2F8VMBZ', '2026-11-10 08:59:52', '2026-11-10 12:59:52'
       UNION ALL SELECT (@da + 26), (@dm + 8), (@du + 67), NULL, '개인', '확정', 'RCPT-20261109-KA2P639K', '2026-11-09 21:54:25', '2026-11-09 23:54:25'
       UNION ALL SELECT (@da + 27), (@dm + 8), (@du + 84), NULL, '개인', '확정', 'RCPT-20261111-QR4KVTUY', '2026-11-11 06:42:57', '2026-11-11 10:42:57'
       UNION ALL SELECT (@da + 28), (@dm + 8), (@du + 40), NULL, '개인', '확정', 'RCPT-20261110-QVTZQU74', '2026-11-10 03:55:13', '2026-11-10 05:55:13'
       UNION ALL SELECT (@da + 29), (@dm + 8), (@du + 7), NULL, '개인', '대기', 'RCPT-20261111-9XRLMY2K', '2026-11-11 08:31:10', NULL
       UNION ALL SELECT (@da + 30), (@dm + 8), (@du + 55), NULL, '개인', '대기', 'RCPT-20261109-55FBS4NA', '2026-11-09 18:03:37', NULL
       UNION ALL SELECT (@da + 31), (@dm + 9), (@du + 23), NULL, '개인', '확정', 'RCPT-20261110-B8RLNDMY', '2026-11-10 13:16:50', '2026-11-10 16:16:50'
       UNION ALL SELECT (@da + 32), (@dm + 9), (@du + 27), NULL, '개인', '확정', 'RCPT-20261109-XU7HG5Q5', '2026-11-09 11:23:51', '2026-11-09 15:23:51'
       UNION ALL SELECT (@da + 33), (@dm + 9), (@du + 75), NULL, '개인', '확정', 'RCPT-20261110-NUVDHEL9', '2026-11-10 02:35:38', '2026-11-10 07:35:38'
       UNION ALL SELECT (@da + 34), (@dm + 10), (@du + 34), NULL, '개인', '확정', 'RCPT-20261110-XC25LK76', '2026-11-10 15:03:55', '2026-11-10 17:03:55'
       UNION ALL SELECT (@da + 35), (@dm + 10), (@du + 51), NULL, '개인', '확정', 'RCPT-20261109-WDS368N7', '2026-11-09 22:00:26', '2026-11-10 02:00:26'
       UNION ALL SELECT (@da + 36), (@dm + 10), (@du + 30), NULL, '개인', '확정', 'RCPT-20261110-ZPKBNHYP', '2026-11-10 14:30:43', '2026-11-10 18:30:43'
       UNION ALL SELECT (@da + 37), (@dm + 10), (@du + 49), NULL, '개인', '확정', 'RCPT-20261110-75UYKQTS', '2026-11-10 19:37:40', '2026-11-10 21:37:40'
       UNION ALL SELECT (@da + 38), (@dm + 10), (@du + 77), NULL, '개인', '확정', 'RCPT-20261110-HCXLF9YX', '2026-11-10 07:35:27', '2026-11-10 08:35:27'
       UNION ALL SELECT (@da + 39), (@dm + 10), (@du + 22), NULL, '개인', '확정', 'RCPT-20261110-RD7KQARH', '2026-11-10 13:02:20', '2026-11-10 19:02:20'
       UNION ALL SELECT (@da + 40), (@dm + 10), (@du + 58), NULL, '개인', '확정', 'RCPT-20261110-Y9WDG333', '2026-11-10 05:54:16', '2026-11-10 07:54:16'
       UNION ALL SELECT (@da + 41), (@dm + 10), (@du + 74), NULL, '개인', '확정', 'RCPT-20261110-JNU46Z2P', '2026-11-10 03:45:11', '2026-11-10 04:45:11'
       UNION ALL SELECT (@da + 42), (@dm + 10), (@du + 72), NULL, '개인', '확정', 'RCPT-20261109-95EEFCRE', '2026-11-09 19:51:42', '2026-11-10 00:51:42'
       UNION ALL SELECT (@da + 43), (@dm + 10), (@du + 2), NULL, '개인', '확정', 'RCPT-20261111-RMXDCPMT', '2026-11-11 06:30:03', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 44), (@dm + 12), (@du + 64), NULL, '개인', '확정', 'RCPT-20261111-7ZQ5EQM5', '2026-11-11 06:34:23', '2026-11-11 07:34:23'
       UNION ALL SELECT (@da + 45), (@dm + 12), (@du + 81), NULL, '개인', '확정', 'RCPT-20261110-83769CGT', '2026-11-10 10:53:32', '2026-11-10 12:53:32'
       UNION ALL SELECT (@da + 46), (@dm + 12), (@du + 33), NULL, '개인', '확정', 'RCPT-20261109-ZGHXUNFC', '2026-11-09 15:44:01', '2026-11-09 21:44:01'
       UNION ALL SELECT (@da + 47), (@dm + 12), (@du + 21), NULL, '개인', '확정', 'RCPT-20261110-XRZP9ZKV', '2026-11-10 09:21:31', '2026-11-10 13:21:31'
       UNION ALL SELECT (@da + 48), (@dm + 14), (@du + 92), NULL, '개인', '확정', 'RCPT-20261110-NP62GRCX', '2026-11-10 22:11:26', '2026-11-11 04:11:26'
       UNION ALL SELECT (@da + 49), (@dm + 14), (@du + 46), NULL, '개인', '확정', 'RCPT-20261109-UH2PJJZ4', '2026-11-09 12:46:21', '2026-11-09 17:46:21'
       UNION ALL SELECT (@da + 50), (@dm + 14), (@du + 15), NULL, '개인', '대기', 'RCPT-20261110-8FFSPFHA', '2026-11-10 08:11:18', NULL
       UNION ALL SELECT (@da + 51), (@dm + 16), (@du + 29), NULL, '개인', '확정', 'RCPT-20261109-WJHX5BAU', '2026-11-09 11:17:56', '2026-11-09 15:17:56'
       UNION ALL SELECT (@da + 52), (@dm + 16), (@du + 63), NULL, '개인', '확정', 'RCPT-20261110-PQ6KJEL9', '2026-11-10 10:11:27', '2026-11-10 13:11:27'
       UNION ALL SELECT (@da + 53), (@dm + 16), (@du + 67), NULL, '개인', '확정', 'RCPT-20261111-LJSFKNF5', '2026-11-11 00:43:22', '2026-11-11 04:43:22'
       UNION ALL SELECT (@da + 54), (@dm + 16), (@du + 10), NULL, '개인', '확정', 'RCPT-20261110-TGUASR4T', '2026-11-10 07:17:15', '2026-11-10 10:17:15'
       UNION ALL SELECT (@da + 55), (@dm + 16), (@du + 83), NULL, '개인', '확정', 'RCPT-20261110-7TXD39G2', '2026-11-10 21:43:54', '2026-11-11 01:43:54'
       UNION ALL SELECT (@da + 56), (@dm + 16), (@du + 7), NULL, '개인', '확정', 'RCPT-20261109-RELAUQP4', '2026-11-09 18:54:17', '2026-11-09 23:54:17'
       UNION ALL SELECT (@da + 57), (@dm + 17), (@du + 18), NULL, '개인', '확정', 'RCPT-20261110-R9XUJ76N', '2026-11-10 20:56:42', '2026-11-11 01:56:42'
       UNION ALL SELECT (@da + 58), (@dm + 17), (@du + 84), NULL, '개인', '확정', 'RCPT-20261109-LZDR75KH', '2026-11-09 10:27:02', '2026-11-09 12:27:02'
       UNION ALL SELECT (@da + 59), (@dm + 17), (@du + 28), NULL, '개인', '확정', 'RCPT-20261110-R73JZF2X', '2026-11-10 05:09:54', '2026-11-10 08:09:54'
       UNION ALL SELECT (@da + 60), (@dm + 17), (@du + 3), NULL, '개인', '확정', 'RCPT-20261111-KZHKX6CC', '2026-11-11 07:12:03', '2026-11-11 09:12:03'
       UNION ALL SELECT (@da + 61), (@dm + 17), (@du + 27), NULL, '개인', '확정', 'RCPT-20261109-965GF63H', '2026-11-09 18:35:21', '2026-11-09 20:35:21'
       UNION ALL SELECT (@da + 62), (@dm + 17), (@du + 62), NULL, '개인', '대기', 'RCPT-20261111-6VMP4V25', '2026-11-11 00:25:15', NULL
       UNION ALL SELECT (@da + 63), (@dm + 17), (@du + 55), NULL, '개인', '대기', 'RCPT-20261109-TVWAXBRF', '2026-11-09 17:29:40', NULL
       UNION ALL SELECT (@da + 64), (@dm + 18), (@du + 97), NULL, '개인', '확정', 'RCPT-20261111-CD5ZKCXA', '2026-11-11 10:18:13', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 65), (@dm + 18), (@du + 85), NULL, '개인', '확정', 'RCPT-20261110-XPSZXJRH', '2026-11-10 02:39:30', '2026-11-10 08:39:30'
       UNION ALL SELECT (@da + 66), (@dm + 18), (@du + 60), NULL, '개인', '확정', 'RCPT-20261110-47CBTE8V', '2026-11-10 08:51:03', '2026-11-10 13:51:03'
       UNION ALL SELECT (@da + 67), (@dm + 19), (@du + 49), (@dc + 3), '동호회', '확정', 'RCPT-20261110-PWVBDASC', '2026-11-10 13:01:21', '2026-11-10 16:01:21'
       UNION ALL SELECT (@da + 68), (@dm + 19), (@du + 58), (@dc + 3), '동호회', '확정', 'RCPT-20261110-SSTS5P2Z', '2026-11-10 13:01:21', '2026-11-10 16:01:21'
       UNION ALL SELECT (@da + 69), (@dm + 19), (@du + 3), (@dc + 3), '동호회', '확정', 'RCPT-20261110-UNGT45P5', '2026-11-10 13:01:21', '2026-11-10 16:01:21'
       UNION ALL SELECT (@da + 70), (@dm + 19), (@du + 45), (@dc + 3), '동호회', '확정', 'RCPT-20261110-39FMH5ES', '2026-11-10 13:01:21', '2026-11-10 16:01:21'
       UNION ALL SELECT (@da + 71), (@dm + 19), (@du + 1), (@dc + 3), '동호회', '확정', 'RCPT-20261110-LKB3UML9', '2026-11-10 13:01:21', '2026-11-10 16:01:21'
       UNION ALL SELECT (@da + 72), (@dm + 19), (@du + 86), NULL, '개인', '확정', 'RCPT-20261110-SBVFV4E4', '2026-11-10 21:49:31', '2026-11-11 00:49:31'
       UNION ALL SELECT (@da + 73), (@dm + 19), (@du + 22), NULL, '개인', '확정', 'RCPT-20261110-KY9V42EQ', '2026-11-10 00:57:18', '2026-11-10 06:57:18'
       UNION ALL SELECT (@da + 74), (@dm + 19), (@du + 11), NULL, '개인', '확정', 'RCPT-20261110-59ZMUB4J', '2026-11-10 01:40:50', '2026-11-10 03:40:50'
       UNION ALL SELECT (@da + 75), (@dm + 19), (@du + 35), NULL, '개인', '확정', 'RCPT-20261109-DLDVHAME', '2026-11-09 20:57:31', '2026-11-09 22:57:31'
       UNION ALL SELECT (@da + 76), (@dm + 19), (@du + 42), NULL, '개인', '확정', 'RCPT-20261110-CXKP2MH3', '2026-11-10 01:19:28', '2026-11-10 07:19:28'
       UNION ALL SELECT (@da + 77), (@dm + 20), (@du + 51), (@dc + 1), '동호회', '대기', 'RCPT-20261110-KY4C8PGC', '2026-11-10 10:21:05', NULL
       UNION ALL SELECT (@da + 78), (@dm + 20), (@du + 59), (@dc + 1), '동호회', '대기', 'RCPT-20261110-6CX4CPTK', '2026-11-10 10:21:05', NULL
       UNION ALL SELECT (@da + 79), (@dm + 20), (@du + 65), (@dc + 1), '동호회', '대기', 'RCPT-20261110-MDJ9MQF3', '2026-11-10 10:21:05', NULL
       UNION ALL SELECT (@da + 80), (@dm + 20), (@du + 91), NULL, '개인', '확정', 'RCPT-20261111-A8TTLF8L', '2026-11-11 11:35:03', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 81), (@dm + 20), (@du + 41), NULL, '개인', '확정', 'RCPT-20261111-GPCFXCTA', '2026-11-11 05:37:14', '2026-11-11 08:37:14'
       UNION ALL SELECT (@da + 82), (@dm + 20), (@du + 35), NULL, '개인', '확정', 'RCPT-20261111-WDCZGTCF', '2026-11-11 02:56:03', '2026-11-11 05:56:03'
       UNION ALL SELECT (@da + 83), (@dm + 20), (@du + 72), NULL, '개인', '확정', 'RCPT-20261111-PJKLBAST', '2026-11-11 03:32:00', '2026-11-11 06:32:00'
       UNION ALL SELECT (@da + 84), (@dm + 21), (@du + 31), NULL, '개인', '확정', 'RCPT-20261111-33YVL7GD', '2026-11-11 02:13:35', '2026-11-11 07:13:35'
       UNION ALL SELECT (@da + 85), (@dm + 21), (@du + 74), NULL, '개인', '확정', 'RCPT-20261110-LUMXCDN8', '2026-11-10 08:48:55', '2026-11-10 10:48:55'
       UNION ALL SELECT (@da + 86), (@dm + 21), (@du + 60), NULL, '개인', '대기', 'RCPT-20261109-VEL92Y2K', '2026-11-09 19:42:44', NULL
       UNION ALL SELECT (@da + 87), (@dm + 23), (@du + 8), NULL, '개인', '확정', 'RCPT-20261109-ZUGDUBTF', '2026-11-09 17:03:18', '2026-11-09 21:03:18'
       UNION ALL SELECT (@da + 88), (@dm + 23), (@du + 39), NULL, '개인', '확정', 'RCPT-20261109-U54CDPG3', '2026-11-09 23:18:50', '2026-11-10 03:18:50'
       UNION ALL SELECT (@da + 89), (@dm + 23), (@du + 85), NULL, '개인', '확정', 'RCPT-20261110-SVF3LZV6', '2026-11-10 10:49:31', '2026-11-10 11:49:31'
       UNION ALL SELECT (@da + 90), (@dm + 23), (@du + 46), NULL, '개인', '확정', 'RCPT-20261111-XWJTPSUY', '2026-11-11 00:42:47', '2026-11-11 03:42:47'
       UNION ALL SELECT (@da + 91), (@dm + 25), (@du + 18), (@dc + 4), '동호회', '대기', 'RCPT-20261109-DNUW6RJK', '2026-11-09 11:16:06', NULL
       UNION ALL SELECT (@da + 92), (@dm + 25), (@du + 46), (@dc + 4), '동호회', '대기', 'RCPT-20261109-8LWJDY4L', '2026-11-09 11:16:06', NULL
       UNION ALL SELECT (@da + 93), (@dm + 25), (@du + 9), (@dc + 4), '동호회', '대기', 'RCPT-20261109-DXULQTMD', '2026-11-09 11:16:06', NULL
       UNION ALL SELECT (@da + 94), (@dm + 25), (@du + 75), (@dc + 4), '동호회', '대기', 'RCPT-20261109-V9CHMPW4', '2026-11-09 11:16:06', NULL
       UNION ALL SELECT (@da + 95), (@dm + 25), (@du + 73), NULL, '개인', '확정', 'RCPT-20261110-8LR36J8N', '2026-11-10 17:54:23', '2026-11-10 20:54:23'
       UNION ALL SELECT (@da + 96), (@dm + 25), (@du + 8), NULL, '개인', '확정', 'RCPT-20261110-U85BVZPJ', '2026-11-10 04:03:02', '2026-11-10 08:03:02'
       UNION ALL SELECT (@da + 97), (@dm + 27), (@du + 97), NULL, '개인', '확정', 'RCPT-20261109-M6YR3SU6', '2026-11-09 09:27:49', '2026-11-09 13:27:49'
       UNION ALL SELECT (@da + 98), (@dm + 27), (@du + 28), NULL, '개인', '확정', 'RCPT-20261109-NWQQ2JUE', '2026-11-09 17:13:18', '2026-11-09 19:13:18'
       UNION ALL SELECT (@da + 99), (@dm + 27), (@du + 68), NULL, '개인', '확정', 'RCPT-20261110-3BVVL5J4', '2026-11-10 17:14:22', '2026-11-10 23:14:22'
       UNION ALL SELECT (@da + 100), (@dm + 27), (@du + 16), NULL, '개인', '확정', 'RCPT-20261109-LZFXWGRD', '2026-11-09 19:44:56', '2026-11-09 23:44:56'
       UNION ALL SELECT (@da + 101), (@dm + 27), (@du + 59), NULL, '개인', '확정', 'RCPT-20261110-NUSZGBGM', '2026-11-10 16:00:41', '2026-11-10 20:00:41'
       UNION ALL SELECT (@da + 102), (@dm + 27), (@du + 83), NULL, '개인', '확정', 'RCPT-20261110-U2Q6TMBZ', '2026-11-10 12:10:25', '2026-11-10 13:10:25'
       UNION ALL SELECT (@da + 103), (@dm + 28), (@du + 51), (@dc + 1), '동호회', '확정', 'RCPT-20261110-RP6JWMV8', '2026-11-10 16:30:54', '2026-11-10 19:30:54'
       UNION ALL SELECT (@da + 104), (@dm + 28), (@du + 72), (@dc + 1), '동호회', '확정', 'RCPT-20261110-AYZBXM2X', '2026-11-10 16:30:54', '2026-11-10 19:30:54'
       UNION ALL SELECT (@da + 105), (@dm + 28), (@du + 59), (@dc + 1), '동호회', '확정', 'RCPT-20261110-SF394BPU', '2026-11-10 16:30:54', '2026-11-10 19:30:54'
       UNION ALL SELECT (@da + 106), (@dm + 28), (@du + 23), (@dc + 1), '동호회', '확정', 'RCPT-20261110-LYPYJY4G', '2026-11-10 16:30:54', '2026-11-10 19:30:54'
       UNION ALL SELECT (@da + 107), (@dm + 28), (@du + 65), (@dc + 1), '동호회', '확정', 'RCPT-20261110-853GTXH6', '2026-11-10 16:30:54', '2026-11-10 19:30:54'
       UNION ALL SELECT (@da + 108), (@dm + 28), (@du + 19), NULL, '개인', '확정', 'RCPT-20261110-C72W5QT2', '2026-11-10 08:29:10', '2026-11-10 09:29:10'
       UNION ALL SELECT (@da + 109), (@dm + 28), (@du + 61), NULL, '개인', '확정', 'RCPT-20261110-K9Z7ZY6J', '2026-11-10 19:16:40', '2026-11-10 21:16:40'
       UNION ALL SELECT (@da + 110), (@dm + 28), (@du + 17), NULL, '개인', '확정', 'RCPT-20261110-UY63EDWH', '2026-11-10 03:05:43', '2026-11-10 09:05:43'
       UNION ALL SELECT (@da + 111), (@dm + 28), (@du + 34), NULL, '개인', '확정', 'RCPT-20261109-4376536X', '2026-11-09 22:04:39', '2026-11-09 23:04:39'
       UNION ALL SELECT (@da + 112), (@dm + 28), (@du + 45), NULL, '개인', '확정', 'RCPT-20261110-93UC6PDG', '2026-11-10 11:24:49', '2026-11-10 14:24:49'
       UNION ALL SELECT (@da + 113), (@dm + 29), (@du + 81), (@dc + 2), '동호회', '대기', 'RCPT-20261110-BKNR85DT', '2026-11-10 22:30:41', NULL
       UNION ALL SELECT (@da + 114), (@dm + 29), (@du + 91), (@dc + 2), '동호회', '대기', 'RCPT-20261110-VTNT2Y8E', '2026-11-10 22:30:41', NULL
       UNION ALL SELECT (@da + 115), (@dm + 29), (@du + 47), (@dc + 2), '동호회', '대기', 'RCPT-20261110-9NZ5JTEN', '2026-11-10 22:30:41', NULL
       UNION ALL SELECT (@da + 116), (@dm + 29), (@du + 69), NULL, '개인', '확정', 'RCPT-20261111-MHLZBCEF', '2026-11-11 09:56:34', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 117), (@dm + 29), (@du + 68), NULL, '개인', '확정', 'RCPT-20261111-SQZL8AWB', '2026-11-11 11:39:32', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 118), (@dm + 29), (@du + 56), NULL, '개인', '확정', 'RCPT-20261111-MCAYPJDR', '2026-11-11 01:19:38', '2026-11-11 07:19:38'
       UNION ALL SELECT (@da + 119), (@dm + 29), (@du + 31), NULL, '개인', '확정', 'RCPT-20261109-JVHKFKXZ', '2026-11-09 21:00:31', '2026-11-10 01:00:31'
       UNION ALL SELECT (@da + 120), (@dm + 30), (@du + 48), NULL, '개인', '확정', 'RCPT-20261109-HCQM68LG', '2026-11-09 23:41:09', '2026-11-10 00:41:09'
       UNION ALL SELECT (@da + 121), (@dm + 30), (@du + 26), NULL, '개인', '확정', 'RCPT-20261109-L7TK95UP', '2026-11-09 18:50:35', '2026-11-09 23:50:35'
       UNION ALL SELECT (@da + 122), (@dm + 30), (@du + 8), NULL, '개인', '대기', 'RCPT-20261111-27T3MZQM', '2026-11-11 11:00:01', NULL
       UNION ALL SELECT (@da + 123), (@dm + 32), (@du + 92), NULL, '개인', '확정', 'RCPT-20261111-Y7P7494C', '2026-11-11 07:55:34', '2026-11-11 08:55:34'
       UNION ALL SELECT (@da + 124), (@dm + 32), (@du + 29), NULL, '개인', '확정', 'RCPT-20261111-H8WPE87F', '2026-11-11 01:52:49', '2026-11-11 03:52:49'
       UNION ALL SELECT (@da + 125), (@dm + 32), (@du + 8), NULL, '개인', '확정', 'RCPT-20261110-6WNRDM2N', '2026-11-10 08:29:09', '2026-11-10 13:29:09'
       UNION ALL SELECT (@da + 126), (@dm + 32), (@du + 32), NULL, '개인', '확정', 'RCPT-20261111-WK2EV7ZX', '2026-11-11 05:34:55', '2026-11-11 09:34:55'
       UNION ALL SELECT (@da + 127), (@dm + 34), (@du + 7), NULL, '개인', '확정', 'RCPT-20261111-CZ5Q5LE3', '2026-11-11 11:24:01', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 128), (@dm + 34), (@du + 97), NULL, '개인', '확정', 'RCPT-20261111-U8Y8ZN74', '2026-11-11 00:22:43', '2026-11-11 02:22:43'
       UNION ALL SELECT (@da + 129), (@dm + 36), (@du + 37), NULL, '개인', '확정', 'RCPT-20261110-L7FCA8BQ', '2026-11-10 10:20:05', '2026-11-10 15:20:05'
       UNION ALL SELECT (@da + 130), (@dm + 36), (@du + 93), NULL, '개인', '확정', 'RCPT-20261109-3SH5YZ6W', '2026-11-09 22:47:23', '2026-11-10 00:47:23'
       UNION ALL SELECT (@da + 131), (@dm + 36), (@du + 58), NULL, '개인', '확정', 'RCPT-20261111-JPYTMAWD', '2026-11-11 06:39:38', '2026-11-11 11:39:38'
       UNION ALL SELECT (@da + 132), (@dm + 36), (@du + 12), NULL, '개인', '확정', 'RCPT-20261109-BAAENJML', '2026-11-09 18:00:36', '2026-11-09 21:00:36'
       UNION ALL SELECT (@da + 133), (@dm + 36), (@du + 97), NULL, '개인', '확정', 'RCPT-20261110-CCQJZDTK', '2026-11-10 22:45:51', '2026-11-10 23:45:51'
       UNION ALL SELECT (@da + 134), (@dm + 36), (@du + 48), NULL, '개인', '확정', 'RCPT-20261109-QZD4STWZ', '2026-11-09 20:56:42', '2026-11-10 02:56:42'
       UNION ALL SELECT (@da + 135), (@dm + 37), (@du + 45), NULL, '개인', '확정', 'RCPT-20261110-XHYCY7LX', '2026-11-10 04:35:46', '2026-11-10 05:35:46'
       UNION ALL SELECT (@da + 136), (@dm + 37), (@du + 4), NULL, '개인', '확정', 'RCPT-20261109-AHNEQLR5', '2026-11-09 19:19:27', '2026-11-10 00:19:27'
       UNION ALL SELECT (@da + 137), (@dm + 37), (@du + 49), NULL, '개인', '확정', 'RCPT-20261109-T5YZBD3Y', '2026-11-09 13:27:06', '2026-11-09 14:27:06'
       UNION ALL SELECT (@da + 138), (@dm + 37), (@du + 20), NULL, '개인', '확정', 'RCPT-20261111-YD5MSGT4', '2026-11-11 00:44:29', '2026-11-11 01:44:29'
       UNION ALL SELECT (@da + 139), (@dm + 37), (@du + 68), NULL, '개인', '확정', 'RCPT-20261110-PMMKHNDG', '2026-11-10 13:48:55', '2026-11-10 17:48:55'
       UNION ALL SELECT (@da + 140), (@dm + 37), (@du + 41), NULL, '개인', '확정', 'RCPT-20261110-8AG3MXQC', '2026-11-10 05:27:59', '2026-11-10 09:27:59'
       UNION ALL SELECT (@da + 141), (@dm + 37), (@du + 95), NULL, '개인', '확정', 'RCPT-20261109-NMFYA3WL', '2026-11-09 18:19:49', '2026-11-09 21:19:49'
       UNION ALL SELECT (@da + 142), (@dm + 37), (@du + 31), NULL, '개인', '확정', 'RCPT-20261110-GF6XZL3W', '2026-11-10 20:08:30', '2026-11-11 02:08:30'
       UNION ALL SELECT (@da + 143), (@dm + 37), (@du + 23), NULL, '개인', '확정', 'RCPT-20261109-KAW8W9HH', '2026-11-09 10:31:56', '2026-11-09 11:31:56'
       UNION ALL SELECT (@da + 144), (@dm + 37), (@du + 38), NULL, '개인', '확정', 'RCPT-20261110-8PTGGGSC', '2026-11-10 06:30:26', '2026-11-10 11:30:26'
       UNION ALL SELECT (@da + 145), (@dm + 39), (@du + 89), NULL, '개인', '확정', 'RCPT-20261110-TVTJ5VW3', '2026-11-10 23:00:39', '2026-11-11 01:00:39'
       UNION ALL SELECT (@da + 146), (@dm + 39), (@du + 77), NULL, '개인', '확정', 'RCPT-20261110-LKZGXTYS', '2026-11-10 01:55:19', '2026-11-10 02:55:19'
       UNION ALL SELECT (@da + 147), (@dm + 39), (@du + 53), NULL, '개인', '확정', 'RCPT-20261109-MSULJN47', '2026-11-09 19:11:35', '2026-11-09 20:11:35'
       UNION ALL SELECT (@da + 148), (@dm + 39), (@du + 69), NULL, '개인', '확정', 'RCPT-20261110-V9JPJ85C', '2026-11-10 07:33:35', '2026-11-10 09:33:35'
       UNION ALL SELECT (@da + 149), (@dm + 41), (@du + 29), NULL, '개인', '확정', 'RCPT-20261111-DLBL8TXU', '2026-11-11 06:54:00', '2026-11-11 10:54:00'
       UNION ALL SELECT (@da + 150), (@dm + 41), (@du + 85), NULL, '개인', '확정', 'RCPT-20261109-SNSA4EV9', '2026-11-09 17:33:39', '2026-11-09 22:33:39'
) v
 WHERE @demo_ready = 1;

INSERT INTO match_application (application_id, match_id, applicant_user_id, club_id, application_type, status, receipt_no, created_at, confirmed_at)
SELECT * FROM (
                 SELECT (@da + 151) AS application_id, (@dm + 41) AS match_id, (@du + 70) AS applicant_user_id, NULL AS club_id, '개인' AS application_type, '대기' AS status, 'RCPT-20261110-DCDT4N8Q' AS receipt_no, '2026-11-10 00:06:42' AS created_at, NULL AS confirmed_at
       UNION ALL SELECT (@da + 152), (@dm + 43), (@du + 60), NULL, '개인', '확정', 'RCPT-20261110-JD48UWYB', '2026-11-10 20:58:18', '2026-11-11 00:58:18'
       UNION ALL SELECT (@da + 153), (@dm + 43), (@du + 36), NULL, '개인', '확정', 'RCPT-20261110-YK4HVXLZ', '2026-11-10 15:00:16', '2026-11-10 20:00:16'
       UNION ALL SELECT (@da + 154), (@dm + 43), (@du + 39), NULL, '개인', '확정', 'RCPT-20261110-4PTWDPZL', '2026-11-10 09:10:15', '2026-11-10 10:10:15'
       UNION ALL SELECT (@da + 155), (@dm + 43), (@du + 27), NULL, '개인', '확정', 'RCPT-20261110-6LL4HCEG', '2026-11-10 22:57:20', '2026-11-11 01:57:20'
       UNION ALL SELECT (@da + 156), (@dm + 43), (@du + 73), NULL, '개인', '확정', 'RCPT-20261111-ZEH7PJSY', '2026-11-11 02:30:09', '2026-11-11 05:30:09'
       UNION ALL SELECT (@da + 157), (@dm + 43), (@du + 55), NULL, '개인', '확정', 'RCPT-20261110-REJSSWZK', '2026-11-10 07:24:39', '2026-11-10 11:24:39'
       UNION ALL SELECT (@da + 158), (@dm + 44), (@du + 79), NULL, '개인', '확정', 'RCPT-20261109-NVX5YW5A', '2026-11-09 15:16:15', '2026-11-09 16:16:15'
       UNION ALL SELECT (@da + 159), (@dm + 44), (@du + 14), NULL, '개인', '확정', 'RCPT-20261111-LWAULAN9', '2026-11-11 04:48:30', '2026-11-11 09:48:30'
       UNION ALL SELECT (@da + 160), (@dm + 44), (@du + 72), NULL, '개인', '확정', 'RCPT-20261110-C728U3CK', '2026-11-10 15:12:19', '2026-11-10 18:12:19'
       UNION ALL SELECT (@da + 161), (@dm + 44), (@du + 88), NULL, '개인', '확정', 'RCPT-20261111-STY7TUHB', '2026-11-11 05:48:29', '2026-11-11 09:48:29'
       UNION ALL SELECT (@da + 162), (@dm + 44), (@du + 24), NULL, '개인', '확정', 'RCPT-20261110-NEEZGFRY', '2026-11-10 18:11:48', '2026-11-10 22:11:48'
       UNION ALL SELECT (@da + 163), (@dm + 44), (@du + 63), NULL, '개인', '대기', 'RCPT-20261111-H7SYA4GE', '2026-11-11 03:21:43', NULL
       UNION ALL SELECT (@da + 164), (@dm + 44), (@du + 75), NULL, '개인', '대기', 'RCPT-20261111-73VSNYUT', '2026-11-11 00:48:00', NULL
       UNION ALL SELECT (@da + 165), (@dm + 45), (@du + 6), NULL, '개인', '확정', 'RCPT-20261110-LWSBS22F', '2026-11-10 22:14:06', '2026-11-11 00:14:06'
       UNION ALL SELECT (@da + 166), (@dm + 45), (@du + 58), NULL, '개인', '확정', 'RCPT-20261109-CSDHU8RJ', '2026-11-09 13:42:36', '2026-11-09 14:42:36'
       UNION ALL SELECT (@da + 167), (@dm + 45), (@du + 37), NULL, '개인', '확정', 'RCPT-20261110-TULQGYKE', '2026-11-10 17:39:21', '2026-11-10 23:39:21'
       UNION ALL SELECT (@da + 168), (@dm + 46), (@du + 1), NULL, '개인', '확정', 'RCPT-20261110-DCJ9TAMN', '2026-11-10 01:26:56', '2026-11-10 03:26:56'
       UNION ALL SELECT (@da + 169), (@dm + 46), (@du + 31), NULL, '개인', '확정', 'RCPT-20261109-X4MVJYJ5', '2026-11-09 10:00:35', '2026-11-09 11:00:35'
       UNION ALL SELECT (@da + 170), (@dm + 46), (@du + 79), NULL, '개인', '확정', 'RCPT-20261109-ERP4YNJD', '2026-11-09 11:32:59', '2026-11-09 14:32:59'
       UNION ALL SELECT (@da + 171), (@dm + 46), (@du + 99), NULL, '개인', '확정', 'RCPT-20261110-ZTBNATVS', '2026-11-10 16:12:56', '2026-11-10 22:12:56'
       UNION ALL SELECT (@da + 172), (@dm + 46), (@du + 61), NULL, '개인', '확정', 'RCPT-20261109-NMVHE7JZ', '2026-11-09 09:30:31', '2026-11-09 11:30:31'
       UNION ALL SELECT (@da + 173), (@dm + 46), (@du + 11), NULL, '개인', '확정', 'RCPT-20261110-4KJS3SUC', '2026-11-10 03:37:47', '2026-11-10 06:37:47'
       UNION ALL SELECT (@da + 174), (@dm + 46), (@du + 46), NULL, '개인', '확정', 'RCPT-20261109-M4L5MGS8', '2026-11-09 14:46:13', '2026-11-09 18:46:13'
       UNION ALL SELECT (@da + 175), (@dm + 46), (@du + 22), NULL, '개인', '확정', 'RCPT-20261109-YACMEXAR', '2026-11-09 09:40:32', '2026-11-09 12:40:32'
       UNION ALL SELECT (@da + 176), (@dm + 46), (@du + 100), NULL, '개인', '확정', 'RCPT-20261110-LHAB8VFC', '2026-11-10 11:48:50', '2026-11-10 17:48:50'
       UNION ALL SELECT (@da + 177), (@dm + 46), (@du + 19), NULL, '개인', '확정', 'RCPT-20261111-VR67YBM8', '2026-11-11 07:06:11', '2026-11-11 09:06:11'
       UNION ALL SELECT (@da + 178), (@dm + 48), (@du + 34), NULL, '개인', '확정', 'RCPT-20261110-RCDBNHN6', '2026-11-10 05:19:42', '2026-11-10 08:19:42'
       UNION ALL SELECT (@da + 179), (@dm + 48), (@du + 26), NULL, '개인', '확정', 'RCPT-20261111-QUPRB44B', '2026-11-11 11:31:55', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 180), (@dm + 48), (@du + 41), NULL, '개인', '확정', 'RCPT-20261109-38V339LB', '2026-11-09 23:53:06', '2026-11-10 03:53:06'
       UNION ALL SELECT (@da + 181), (@dm + 48), (@du + 29), NULL, '개인', '확정', 'RCPT-20261109-NPHR3PW3', '2026-11-09 17:41:11', '2026-11-09 22:41:11'
       UNION ALL SELECT (@da + 182), (@dm + 50), (@du + 96), NULL, '개인', '확정', 'RCPT-20261110-7G98SY3B', '2026-11-10 15:26:22', '2026-11-10 19:26:22'
       UNION ALL SELECT (@da + 183), (@dm + 50), (@du + 89), NULL, '개인', '확정', 'RCPT-20261110-9SSFANWV', '2026-11-10 11:53:02', '2026-11-10 15:53:02'
       UNION ALL SELECT (@da + 184), (@dm + 50), (@du + 64), NULL, '개인', '대기', 'RCPT-20261111-47WMAAJS', '2026-11-11 06:52:04', NULL
       UNION ALL SELECT (@da + 185), (@dm + 52), (@du + 72), NULL, '개인', '확정', 'RCPT-20261110-ND8A2QC3', '2026-11-10 10:40:40', '2026-11-10 13:40:40'
       UNION ALL SELECT (@da + 186), (@dm + 52), (@du + 62), NULL, '개인', '확정', 'RCPT-20261110-UGBMFJME', '2026-11-10 11:56:36', '2026-11-10 16:56:36'
       UNION ALL SELECT (@da + 187), (@dm + 52), (@du + 60), NULL, '개인', '확정', 'RCPT-20261111-ZV7M2VBC', '2026-11-11 03:58:06', '2026-11-11 04:58:06'
       UNION ALL SELECT (@da + 188), (@dm + 52), (@du + 28), NULL, '개인', '확정', 'RCPT-20261110-MGYY2PVD', '2026-11-10 08:55:08', '2026-11-10 09:55:08'
       UNION ALL SELECT (@da + 189), (@dm + 52), (@du + 10), NULL, '개인', '확정', 'RCPT-20261110-4AXBK8AV', '2026-11-10 04:58:00', '2026-11-10 07:58:00'
       UNION ALL SELECT (@da + 190), (@dm + 52), (@du + 27), NULL, '개인', '확정', 'RCPT-20261109-FD79VCF4', '2026-11-09 23:06:04', '2026-11-10 02:06:04'
       UNION ALL SELECT (@da + 191), (@dm + 53), (@du + 40), NULL, '개인', '확정', 'RCPT-20261111-R5J3YP6G', '2026-11-11 05:20:24', '2026-11-11 10:20:24'
       UNION ALL SELECT (@da + 192), (@dm + 53), (@du + 66), NULL, '개인', '확정', 'RCPT-20261110-BSDLUCVV', '2026-11-10 10:51:41', '2026-11-10 16:51:41'
       UNION ALL SELECT (@da + 193), (@dm + 53), (@du + 6), NULL, '개인', '확정', 'RCPT-20261111-Q8B7BG7X', '2026-11-11 02:24:57', '2026-11-11 04:24:57'
       UNION ALL SELECT (@da + 194), (@dm + 53), (@du + 84), NULL, '개인', '확정', 'RCPT-20261110-W5DYYU6E', '2026-11-10 11:42:42', '2026-11-10 15:42:42'
       UNION ALL SELECT (@da + 195), (@dm + 53), (@du + 70), NULL, '개인', '확정', 'RCPT-20261110-JYBGDXFK', '2026-11-10 02:33:55', '2026-11-10 08:33:55'
       UNION ALL SELECT (@da + 196), (@dm + 53), (@du + 15), NULL, '개인', '대기', 'RCPT-20261111-H4N7FNYB', '2026-11-11 02:05:18', NULL
       UNION ALL SELECT (@da + 197), (@dm + 53), (@du + 3), NULL, '개인', '대기', 'RCPT-20261110-28PUPMGM', '2026-11-10 23:19:34', NULL
       UNION ALL SELECT (@da + 198), (@dm + 54), (@du + 36), NULL, '개인', '확정', 'RCPT-20261110-Q23YKJWK', '2026-11-10 04:40:49', '2026-11-10 08:40:49'
       UNION ALL SELECT (@da + 199), (@dm + 54), (@du + 59), NULL, '개인', '확정', 'RCPT-20261109-66PFMZ8W', '2026-11-09 20:42:29', '2026-11-09 22:42:29'
       UNION ALL SELECT (@da + 200), (@dm + 54), (@du + 67), NULL, '개인', '확정', 'RCPT-20261110-3LRPQUAM', '2026-11-10 04:32:42', '2026-11-10 06:32:42'
       UNION ALL SELECT (@da + 201), (@dm + 55), (@du + 68), NULL, '개인', '확정', 'RCPT-20261110-SYJHLUAU', '2026-11-10 06:08:36', '2026-11-10 11:08:36'
       UNION ALL SELECT (@da + 202), (@dm + 55), (@du + 82), NULL, '개인', '확정', 'RCPT-20261109-BKJZY84C', '2026-11-09 23:29:16', '2026-11-10 04:29:16'
       UNION ALL SELECT (@da + 203), (@dm + 55), (@du + 59), NULL, '개인', '확정', 'RCPT-20261110-WQWBK9D8', '2026-11-10 05:36:47', '2026-11-10 07:36:47'
       UNION ALL SELECT (@da + 204), (@dm + 55), (@du + 74), NULL, '개인', '확정', 'RCPT-20261111-3Y4W84J6', '2026-11-11 01:33:23', '2026-11-11 05:33:23'
       UNION ALL SELECT (@da + 205), (@dm + 55), (@du + 4), NULL, '개인', '확정', 'RCPT-20261109-87R6JUCP', '2026-11-09 10:36:54', '2026-11-09 15:36:54'
       UNION ALL SELECT (@da + 206), (@dm + 55), (@du + 11), NULL, '개인', '확정', 'RCPT-20261110-RPJPWG7K', '2026-11-10 13:35:29', '2026-11-10 18:35:29'
       UNION ALL SELECT (@da + 207), (@dm + 55), (@du + 79), NULL, '개인', '확정', 'RCPT-20261109-KG5DF2WE', '2026-11-09 20:05:17', '2026-11-09 23:05:17'
       UNION ALL SELECT (@da + 208), (@dm + 55), (@du + 90), NULL, '개인', '확정', 'RCPT-20261109-QZM476FS', '2026-11-09 10:59:43', '2026-11-09 16:59:43'
       UNION ALL SELECT (@da + 209), (@dm + 55), (@du + 99), NULL, '개인', '확정', 'RCPT-20261111-7HKEDVL8', '2026-11-11 06:55:30', '2026-11-11 10:55:30'
       UNION ALL SELECT (@da + 210), (@dm + 55), (@du + 95), NULL, '개인', '확정', 'RCPT-20261110-MMUEX95J', '2026-11-10 18:38:03', '2026-11-10 19:38:03'
       UNION ALL SELECT (@da + 211), (@dm + 57), (@du + 28), NULL, '개인', '확정', 'RCPT-20261110-GB7GESCU', '2026-11-10 04:19:28', '2026-11-10 10:19:28'
       UNION ALL SELECT (@da + 212), (@dm + 57), (@du + 64), NULL, '개인', '확정', 'RCPT-20261111-ZT5EBFAP', '2026-11-11 11:46:02', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 213), (@dm + 57), (@du + 21), NULL, '개인', '확정', 'RCPT-20261109-UXRXZMMA', '2026-11-09 11:58:43', '2026-11-09 16:58:43'
       UNION ALL SELECT (@da + 214), (@dm + 57), (@du + 17), NULL, '개인', '확정', 'RCPT-20261110-S28BXW75', '2026-11-10 14:34:40', '2026-11-10 19:34:40'
       UNION ALL SELECT (@da + 215), (@dm + 59), (@du + 70), NULL, '개인', '확정', 'RCPT-20261110-ZAJTYG6K', '2026-11-10 06:34:39', '2026-11-10 12:34:39'
       UNION ALL SELECT (@da + 216), (@dm + 59), (@du + 29), NULL, '개인', '확정', 'RCPT-20261111-SDCHZ92W', '2026-11-11 06:16:42', '2026-11-11 10:16:42'
       UNION ALL SELECT (@da + 217), (@dm + 59), (@du + 75), NULL, '개인', '대기', 'RCPT-20261110-BKTKJUTV', '2026-11-10 23:43:26', NULL
       UNION ALL SELECT (@da + 218), (@dm + 61), (@du + 63), NULL, '개인', '확정', 'RCPT-20261111-CYXTUSDE', '2026-11-11 05:08:43', '2026-11-11 08:08:43'
       UNION ALL SELECT (@da + 219), (@dm + 61), (@du + 83), NULL, '개인', '확정', 'RCPT-20261109-3RH3WGXC', '2026-11-09 11:43:09', '2026-11-09 12:43:09'
       UNION ALL SELECT (@da + 220), (@dm + 61), (@du + 62), NULL, '개인', '확정', 'RCPT-20261109-83PBRGJ5', '2026-11-09 23:46:54', '2026-11-10 05:46:54'
       UNION ALL SELECT (@da + 221), (@dm + 61), (@du + 84), NULL, '개인', '확정', 'RCPT-20261110-KTJ6NGEV', '2026-11-10 11:42:33', '2026-11-10 17:42:33'
       UNION ALL SELECT (@da + 222), (@dm + 61), (@du + 97), NULL, '개인', '확정', 'RCPT-20261109-3ALFG6DM', '2026-11-09 11:13:15', '2026-11-09 17:13:15'
       UNION ALL SELECT (@da + 223), (@dm + 61), (@du + 7), NULL, '개인', '확정', 'RCPT-20261109-S4BMTUFE', '2026-11-09 22:31:55', '2026-11-10 01:31:55'
       UNION ALL SELECT (@da + 224), (@dm + 62), (@du + 6), NULL, '개인', '확정', 'RCPT-20261110-VNNB25S8', '2026-11-10 03:28:07', '2026-11-10 09:28:07'
       UNION ALL SELECT (@da + 225), (@dm + 62), (@du + 3), NULL, '개인', '확정', 'RCPT-20261109-GESYJNRJ', '2026-11-09 17:06:37', '2026-11-09 20:06:37'
       UNION ALL SELECT (@da + 226), (@dm + 62), (@du + 73), NULL, '개인', '확정', 'RCPT-20261110-2LZ8G8N7', '2026-11-10 18:32:19', '2026-11-10 23:32:19'
       UNION ALL SELECT (@da + 227), (@dm + 62), (@du + 76), NULL, '개인', '확정', 'RCPT-20261109-KKPAA5VB', '2026-11-09 12:08:25', '2026-11-09 14:08:25'
       UNION ALL SELECT (@da + 228), (@dm + 62), (@du + 39), NULL, '개인', '확정', 'RCPT-20261110-7358ZZRQ', '2026-11-10 07:02:10', '2026-11-10 13:02:10'
       UNION ALL SELECT (@da + 229), (@dm + 62), (@du + 96), NULL, '개인', '대기', 'RCPT-20261110-ZMQZDW9F', '2026-11-10 15:39:27', NULL
       UNION ALL SELECT (@da + 230), (@dm + 62), (@du + 72), NULL, '개인', '대기', 'RCPT-20261111-55CKFLES', '2026-11-11 02:34:42', NULL
       UNION ALL SELECT (@da + 231), (@dm + 63), (@du + 81), NULL, '개인', '확정', 'RCPT-20261109-ASC26HB5', '2026-11-09 17:40:32', '2026-11-09 18:40:32'
       UNION ALL SELECT (@da + 232), (@dm + 63), (@du + 23), NULL, '개인', '확정', 'RCPT-20261110-7DYT4MP7', '2026-11-10 01:34:38', '2026-11-10 07:34:38'
       UNION ALL SELECT (@da + 233), (@dm + 63), (@du + 85), NULL, '개인', '확정', 'RCPT-20261110-SPZ2DGKQ', '2026-11-10 11:14:13', '2026-11-10 16:14:13'
       UNION ALL SELECT (@da + 234), (@dm + 64), (@du + 83), NULL, '개인', '확정', 'RCPT-20261110-GYUCV35U', '2026-11-10 20:56:45', '2026-11-10 21:56:45'
       UNION ALL SELECT (@da + 235), (@dm + 64), (@du + 81), NULL, '개인', '확정', 'RCPT-20261111-HSA3F69N', '2026-11-11 04:23:43', '2026-11-11 05:23:43'
       UNION ALL SELECT (@da + 236), (@dm + 64), (@du + 72), NULL, '개인', '확정', 'RCPT-20261111-7N6J5RSE', '2026-11-11 06:47:29', '2026-11-11 10:47:29'
       UNION ALL SELECT (@da + 237), (@dm + 64), (@du + 79), NULL, '개인', '확정', 'RCPT-20261110-2THHW4JM', '2026-11-10 20:33:12', '2026-11-10 23:33:12'
       UNION ALL SELECT (@da + 238), (@dm + 64), (@du + 54), NULL, '개인', '확정', 'RCPT-20261111-FM78T67Y', '2026-11-11 04:37:05', '2026-11-11 08:37:05'
       UNION ALL SELECT (@da + 239), (@dm + 64), (@du + 57), NULL, '개인', '확정', 'RCPT-20261110-YG9SXKBP', '2026-11-10 08:28:24', '2026-11-10 09:28:24'
       UNION ALL SELECT (@da + 240), (@dm + 64), (@du + 35), NULL, '개인', '확정', 'RCPT-20261109-HQQ6NRK4', '2026-11-09 10:10:45', '2026-11-09 11:10:45'
       UNION ALL SELECT (@da + 241), (@dm + 64), (@du + 69), NULL, '개인', '확정', 'RCPT-20261111-3WB7WPMA', '2026-11-11 11:06:15', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 242), (@dm + 64), (@du + 56), NULL, '개인', '확정', 'RCPT-20261110-MFCQV9SD', '2026-11-10 02:09:31', '2026-11-10 03:09:31'
       UNION ALL SELECT (@da + 243), (@dm + 64), (@du + 85), NULL, '개인', '확정', 'RCPT-20261110-75F3G3A5', '2026-11-10 13:56:43', '2026-11-10 19:56:43'
       UNION ALL SELECT (@da + 244), (@dm + 66), (@du + 16), NULL, '개인', '확정', 'RCPT-20261110-C72RNMZS', '2026-11-10 00:38:37', '2026-11-10 02:38:37'
       UNION ALL SELECT (@da + 245), (@dm + 66), (@du + 60), NULL, '개인', '확정', 'RCPT-20261111-BMZD39NC', '2026-11-11 08:08:43', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 246), (@dm + 66), (@du + 98), NULL, '개인', '확정', 'RCPT-20261110-8KJQL8FF', '2026-11-10 05:49:32', '2026-11-10 06:49:32'
       UNION ALL SELECT (@da + 247), (@dm + 66), (@du + 31), NULL, '개인', '확정', 'RCPT-20261110-3AK7MHPU', '2026-11-10 07:41:25', '2026-11-10 08:41:25'
       UNION ALL SELECT (@da + 248), (@dm + 68), (@du + 94), NULL, '개인', '확정', 'RCPT-20261109-Q9QWT85K', '2026-11-09 23:33:39', '2026-11-10 04:33:39'
       UNION ALL SELECT (@da + 249), (@dm + 68), (@du + 46), NULL, '개인', '확정', 'RCPT-20261111-6268AUMA', '2026-11-11 03:24:57', '2026-11-11 04:24:57'
       UNION ALL SELECT (@da + 250), (@dm + 68), (@du + 92), NULL, '개인', '대기', 'RCPT-20261111-F72ZG96P', '2026-11-11 07:13:46', NULL
       UNION ALL SELECT (@da + 251), (@dm + 70), (@du + 55), NULL, '개인', '확정', 'RCPT-20261110-BHHCLL6S', '2026-11-10 19:36:36', '2026-11-11 00:36:36'
       UNION ALL SELECT (@da + 252), (@dm + 70), (@du + 88), NULL, '개인', '확정', 'RCPT-20261110-5P8GCKGG', '2026-11-10 13:07:21', '2026-11-10 17:07:21'
       UNION ALL SELECT (@da + 253), (@dm + 70), (@du + 6), NULL, '개인', '확정', 'RCPT-20261110-2GBSR8CH', '2026-11-10 00:50:01', '2026-11-10 05:50:01'
       UNION ALL SELECT (@da + 254), (@dm + 70), (@du + 63), NULL, '개인', '확정', 'RCPT-20261109-8LWFMSE9', '2026-11-09 17:51:53', '2026-11-09 20:51:53'
       UNION ALL SELECT (@da + 255), (@dm + 70), (@du + 8), NULL, '개인', '확정', 'RCPT-20261111-JK9CYF83', '2026-11-11 09:33:15', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 256), (@dm + 70), (@du + 65), NULL, '개인', '확정', 'RCPT-20261110-W3JAXC7L', '2026-11-10 05:56:38', '2026-11-10 07:56:38'
       UNION ALL SELECT (@da + 257), (@dm + 71), (@du + 40), NULL, '개인', '확정', 'RCPT-20261110-X3C69ZYJ', '2026-11-10 01:03:27', '2026-11-10 06:03:27'
       UNION ALL SELECT (@da + 258), (@dm + 71), (@du + 14), NULL, '개인', '확정', 'RCPT-20261110-W39MU33T', '2026-11-10 13:27:14', '2026-11-10 19:27:14'
       UNION ALL SELECT (@da + 259), (@dm + 71), (@du + 50), NULL, '개인', '확정', 'RCPT-20261110-LBJFEU76', '2026-11-10 21:07:25', '2026-11-10 22:07:25'
       UNION ALL SELECT (@da + 260), (@dm + 71), (@du + 66), NULL, '개인', '확정', 'RCPT-20261110-9BEWXBTD', '2026-11-10 23:57:21', '2026-11-11 04:57:21'
       UNION ALL SELECT (@da + 261), (@dm + 71), (@du + 75), NULL, '개인', '확정', 'RCPT-20261110-T4MWDF72', '2026-11-10 11:15:39', '2026-11-10 15:15:39'
       UNION ALL SELECT (@da + 262), (@dm + 71), (@du + 28), NULL, '개인', '대기', 'RCPT-20261110-TYG9CJGM', '2026-11-10 04:07:01', NULL
       UNION ALL SELECT (@da + 263), (@dm + 71), (@du + 9), NULL, '개인', '대기', 'RCPT-20261109-U7QL4WM4', '2026-11-09 20:52:18', NULL
       UNION ALL SELECT (@da + 264), (@dm + 72), (@du + 33), NULL, '개인', '확정', 'RCPT-20261111-4Q8BWHJV', '2026-11-11 01:50:35', '2026-11-11 04:50:35'
       UNION ALL SELECT (@da + 265), (@dm + 72), (@du + 73), NULL, '개인', '확정', 'RCPT-20261110-5VTTCUQR', '2026-11-10 06:19:55', '2026-11-10 09:19:55'
       UNION ALL SELECT (@da + 266), (@dm + 72), (@du + 38), NULL, '개인', '확정', 'RCPT-20261109-K26MH7TK', '2026-11-09 17:04:49', '2026-11-09 18:04:49'
       UNION ALL SELECT (@da + 267), (@dm + 73), (@du + 20), NULL, '개인', '확정', 'RCPT-20261110-E4344N6C', '2026-11-10 21:59:01', '2026-11-11 02:59:01'
       UNION ALL SELECT (@da + 268), (@dm + 73), (@du + 1), NULL, '개인', '확정', 'RCPT-20261111-BZL8QLJN', '2026-11-11 01:25:11', '2026-11-11 02:25:11'
       UNION ALL SELECT (@da + 269), (@dm + 73), (@du + 42), NULL, '개인', '확정', 'RCPT-20261111-SKJ2FQ75', '2026-11-11 04:29:52', '2026-11-11 08:29:52'
       UNION ALL SELECT (@da + 270), (@dm + 73), (@du + 65), NULL, '개인', '확정', 'RCPT-20261109-NYUJQ4CH', '2026-11-09 21:34:10', '2026-11-10 01:34:10'
       UNION ALL SELECT (@da + 271), (@dm + 73), (@du + 47), NULL, '개인', '확정', 'RCPT-20261111-BB29KH3R', '2026-11-11 08:52:59', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 272), (@dm + 73), (@du + 63), NULL, '개인', '확정', 'RCPT-20261111-5GRZQRPD', '2026-11-11 00:16:29', '2026-11-11 05:16:29'
       UNION ALL SELECT (@da + 273), (@dm + 73), (@du + 85), NULL, '개인', '확정', 'RCPT-20261111-HS8243US', '2026-11-11 03:47:58', '2026-11-11 06:47:58'
       UNION ALL SELECT (@da + 274), (@dm + 73), (@du + 44), NULL, '개인', '확정', 'RCPT-20261111-HDVQ9RWV', '2026-11-11 07:50:21', '2026-11-11 08:50:21'
       UNION ALL SELECT (@da + 275), (@dm + 73), (@du + 59), NULL, '개인', '확정', 'RCPT-20261109-QBB6FTQQ', '2026-11-09 21:16:18', '2026-11-09 22:16:18'
       UNION ALL SELECT (@da + 276), (@dm + 73), (@du + 99), NULL, '개인', '확정', 'RCPT-20261110-PX8LDSE3', '2026-11-10 03:39:54', '2026-11-10 06:39:54'
       UNION ALL SELECT (@da + 277), (@dm + 75), (@du + 79), NULL, '개인', '확정', 'RCPT-20261109-DHD4BWWE', '2026-11-09 18:03:38', '2026-11-09 22:03:38'
       UNION ALL SELECT (@da + 278), (@dm + 75), (@du + 16), NULL, '개인', '확정', 'RCPT-20261111-K9R5KBHL', '2026-11-11 01:54:11', '2026-11-11 07:54:11'
       UNION ALL SELECT (@da + 279), (@dm + 75), (@du + 19), NULL, '개인', '확정', 'RCPT-20261111-54F7AWAT', '2026-11-11 02:42:09', '2026-11-11 08:42:09'
       UNION ALL SELECT (@da + 280), (@dm + 75), (@du + 33), NULL, '개인', '확정', 'RCPT-20261110-PTHYFLWV', '2026-11-10 06:30:33', '2026-11-10 11:30:33'
       UNION ALL SELECT (@da + 281), (@dm + 77), (@du + 43), NULL, '개인', '확정', 'RCPT-20261109-YTZE7PSF', '2026-11-09 16:00:05', '2026-11-09 20:00:05'
       UNION ALL SELECT (@da + 282), (@dm + 77), (@du + 5), NULL, '개인', '확정', 'RCPT-20261109-KCS8EQW5', '2026-11-09 16:59:32', '2026-11-09 19:59:32'
       UNION ALL SELECT (@da + 283), (@dm + 77), (@du + 46), NULL, '개인', '대기', 'RCPT-20261110-KGZAJ8JA', '2026-11-10 06:05:19', NULL
       UNION ALL SELECT (@da + 284), (@dm + 79), (@du + 97), NULL, '개인', '확정', 'RCPT-20261109-FC4QBGBN', '2026-11-09 18:48:44', '2026-11-09 21:48:44'
       UNION ALL SELECT (@da + 285), (@dm + 79), (@du + 14), NULL, '개인', '확정', 'RCPT-20261110-E78WKJ4A', '2026-11-10 01:37:36', '2026-11-10 06:37:36'
       UNION ALL SELECT (@da + 286), (@dm + 79), (@du + 75), NULL, '개인', '확정', 'RCPT-20261110-BWQRVKD9', '2026-11-10 01:06:48', '2026-11-10 05:06:48'
       UNION ALL SELECT (@da + 287), (@dm + 79), (@du + 76), NULL, '개인', '확정', 'RCPT-20261110-MQUBQ5WW', '2026-11-10 07:08:35', '2026-11-10 09:08:35'
       UNION ALL SELECT (@da + 288), (@dm + 79), (@du + 27), NULL, '개인', '확정', 'RCPT-20261110-DWY9NDDF', '2026-11-10 15:44:58', '2026-11-10 20:44:58'
       UNION ALL SELECT (@da + 289), (@dm + 79), (@du + 15), NULL, '개인', '확정', 'RCPT-20261110-AZDEHUJ5', '2026-11-10 21:58:22', '2026-11-11 03:58:22'
       UNION ALL SELECT (@da + 290), (@dm + 80), (@du + 83), NULL, '개인', '확정', 'RCPT-20261110-4WSFHN8K', '2026-11-10 04:08:10', '2026-11-10 06:08:10'
       UNION ALL SELECT (@da + 291), (@dm + 80), (@du + 24), NULL, '개인', '확정', 'RCPT-20261111-UADT23HL', '2026-11-11 07:36:11', '2026-11-11 08:36:11'
       UNION ALL SELECT (@da + 292), (@dm + 80), (@du + 3), NULL, '개인', '확정', 'RCPT-20261111-ZND6U43F', '2026-11-11 11:13:19', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 293), (@dm + 80), (@du + 26), NULL, '개인', '확정', 'RCPT-20261111-D38C5XBH', '2026-11-11 07:11:39', '2026-11-11 10:11:39'
       UNION ALL SELECT (@da + 294), (@dm + 80), (@du + 13), NULL, '개인', '확정', 'RCPT-20261110-M7KBR8AJ', '2026-11-10 11:42:12', '2026-11-10 12:42:12'
       UNION ALL SELECT (@da + 295), (@dm + 80), (@du + 58), NULL, '개인', '대기', 'RCPT-20261109-XH68T9AT', '2026-11-09 11:06:18', NULL
       UNION ALL SELECT (@da + 296), (@dm + 80), (@du + 73), NULL, '개인', '대기', 'RCPT-20261110-FLFBVFWK', '2026-11-10 17:21:42', NULL
       UNION ALL SELECT (@da + 297), (@dm + 81), (@du + 61), NULL, '개인', '확정', 'RCPT-20261110-UQ45Q4ZV', '2026-11-10 19:55:16', '2026-11-10 20:55:16'
       UNION ALL SELECT (@da + 298), (@dm + 81), (@du + 28), NULL, '개인', '확정', 'RCPT-20261109-L4ZL4HPK', '2026-11-09 12:55:07', '2026-11-09 13:55:07'
       UNION ALL SELECT (@da + 299), (@dm + 81), (@du + 38), NULL, '개인', '확정', 'RCPT-20261110-FJZRYSPC', '2026-11-10 16:18:13', '2026-11-10 22:18:13'
       UNION ALL SELECT (@da + 300), (@dm + 82), (@du + 51), (@dc + 1), '동호회', '확정', 'RCPT-20261110-63BQUAJY', '2026-11-10 02:43:32', '2026-11-10 05:43:32'
) v
 WHERE @demo_ready = 1;

INSERT INTO match_application (application_id, match_id, applicant_user_id, club_id, application_type, status, receipt_no, created_at, confirmed_at)
SELECT * FROM (
                 SELECT (@da + 301) AS application_id, (@dm + 82) AS match_id, (@du + 59) AS applicant_user_id, (@dc + 1) AS club_id, '동호회' AS application_type, '확정' AS status, 'RCPT-20261110-9ETZU5AB' AS receipt_no, '2026-11-10 02:43:32' AS created_at, '2026-11-10 05:43:32' AS confirmed_at
       UNION ALL SELECT (@da + 302), (@dm + 82), (@du + 4), (@dc + 1), '동호회', '확정', 'RCPT-20261110-ZF83SWDK', '2026-11-10 02:43:32', '2026-11-10 05:43:32'
       UNION ALL SELECT (@da + 303), (@dm + 82), (@du + 65), (@dc + 1), '동호회', '확정', 'RCPT-20261110-HVBGW7P5', '2026-11-10 02:43:32', '2026-11-10 05:43:32'
       UNION ALL SELECT (@da + 304), (@dm + 82), (@du + 83), (@dc + 1), '동호회', '확정', 'RCPT-20261110-J5UXSC7H', '2026-11-10 02:43:32', '2026-11-10 05:43:32'
       UNION ALL SELECT (@da + 305), (@dm + 82), (@du + 30), NULL, '개인', '확정', 'RCPT-20261110-HFLWN44D', '2026-11-10 00:30:43', '2026-11-10 05:30:43'
       UNION ALL SELECT (@da + 306), (@dm + 82), (@du + 42), NULL, '개인', '확정', 'RCPT-20261111-RCJWGTH8', '2026-11-11 09:46:51', '2026-11-11 11:46:51'
       UNION ALL SELECT (@da + 307), (@dm + 82), (@du + 58), NULL, '개인', '확정', 'RCPT-20261110-9N5X9RG4', '2026-11-10 16:22:52', '2026-11-10 18:22:52'
       UNION ALL SELECT (@da + 308), (@dm + 82), (@du + 2), NULL, '개인', '확정', 'RCPT-20261109-J9YHYVP7', '2026-11-09 21:57:22', '2026-11-09 23:57:22'
       UNION ALL SELECT (@da + 309), (@dm + 82), (@du + 95), NULL, '개인', '확정', 'RCPT-20261110-8KAQ7XZL', '2026-11-10 10:18:10', '2026-11-10 16:18:10'
       UNION ALL SELECT (@da + 310), (@dm + 83), (@du + 81), (@dc + 2), '동호회', '대기', 'RCPT-20261110-V3YC3UEZ', '2026-11-10 09:37:49', NULL
       UNION ALL SELECT (@da + 311), (@dm + 83), (@du + 79), (@dc + 2), '동호회', '대기', 'RCPT-20261110-2U3JF8YM', '2026-11-10 09:37:49', NULL
       UNION ALL SELECT (@da + 312), (@dm + 83), (@du + 77), (@dc + 2), '동호회', '대기', 'RCPT-20261110-JTTM8GJW', '2026-11-10 09:37:49', NULL
       UNION ALL SELECT (@da + 313), (@dm + 83), (@du + 45), NULL, '개인', '확정', 'RCPT-20261110-TKUCJGG8', '2026-11-10 15:52:44', '2026-11-10 20:52:44'
       UNION ALL SELECT (@da + 314), (@dm + 83), (@du + 46), NULL, '개인', '확정', 'RCPT-20261111-MZRGR4EX', '2026-11-11 06:13:44', '2026-11-11 10:13:44'
       UNION ALL SELECT (@da + 315), (@dm + 83), (@du + 41), NULL, '개인', '확정', 'RCPT-20261111-N5P3N3X8', '2026-11-11 09:48:58', '2026-11-11 11:48:58'
       UNION ALL SELECT (@da + 316), (@dm + 83), (@du + 58), NULL, '개인', '확정', 'RCPT-20261110-AGUMV3B3', '2026-11-10 12:31:18', '2026-11-10 13:31:18'
       UNION ALL SELECT (@da + 317), (@dm + 84), (@du + 56), NULL, '개인', '확정', 'RCPT-20261109-RPGBZ5B4', '2026-11-09 14:23:01', '2026-11-09 18:23:01'
       UNION ALL SELECT (@da + 318), (@dm + 84), (@du + 82), NULL, '개인', '확정', 'RCPT-20261111-QXQ7EQPM', '2026-11-11 08:38:32', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 319), (@dm + 84), (@du + 38), NULL, '개인', '대기', 'RCPT-20261110-3RMJXZH8', '2026-11-10 04:26:38', NULL
       UNION ALL SELECT (@da + 320), (@dm + 86), (@du + 24), NULL, '개인', '확정', 'RCPT-20261109-QCQWWMM5', '2026-11-09 10:41:15', '2026-11-09 11:41:15'
       UNION ALL SELECT (@da + 321), (@dm + 86), (@du + 66), NULL, '개인', '확정', 'RCPT-20261111-YWS2LVKT', '2026-11-11 09:08:25', '2026-11-11 11:08:25'
       UNION ALL SELECT (@da + 322), (@dm + 86), (@du + 92), NULL, '개인', '확정', 'RCPT-20261110-7JZWE4L6', '2026-11-10 12:14:01', '2026-11-10 14:14:01'
       UNION ALL SELECT (@da + 323), (@dm + 86), (@du + 5), NULL, '개인', '확정', 'RCPT-20261109-7FE9BL7Q', '2026-11-09 22:18:37', '2026-11-09 23:18:37'
       UNION ALL SELECT (@da + 324), (@dm + 88), (@du + 8), (@dc + 5), '동호회', '대기', 'RCPT-20261111-JPAGAXEA', '2026-11-11 10:39:17', NULL
       UNION ALL SELECT (@da + 325), (@dm + 88), (@du + 6), (@dc + 5), '동호회', '대기', 'RCPT-20261111-R8H5SWUM', '2026-11-11 10:39:17', NULL
       UNION ALL SELECT (@da + 326), (@dm + 88), (@du + 50), (@dc + 5), '동호회', '대기', 'RCPT-20261111-ALWX5VFJ', '2026-11-11 10:39:17', NULL
       UNION ALL SELECT (@da + 327), (@dm + 88), (@du + 14), (@dc + 5), '동호회', '대기', 'RCPT-20261111-FXS96ZUX', '2026-11-11 10:39:17', NULL
       UNION ALL SELECT (@da + 328), (@dm + 88), (@du + 39), NULL, '개인', '확정', 'RCPT-20261111-AQM36GLY', '2026-11-11 04:13:09', '2026-11-11 08:13:09'
       UNION ALL SELECT (@da + 329), (@dm + 88), (@du + 96), NULL, '개인', '확정', 'RCPT-20261110-TYHXGUPC', '2026-11-10 04:51:21', '2026-11-10 08:51:21'
       UNION ALL SELECT (@da + 330), (@dm + 90), (@du + 28), NULL, '개인', '확정', 'RCPT-20261111-WFU4BBPW', '2026-11-11 01:51:02', '2026-11-11 05:51:02'
       UNION ALL SELECT (@da + 331), (@dm + 90), (@du + 86), NULL, '개인', '확정', 'RCPT-20261109-H3ERYC62', '2026-11-09 09:06:55', '2026-11-09 12:06:55'
       UNION ALL SELECT (@da + 332), (@dm + 90), (@du + 89), NULL, '개인', '확정', 'RCPT-20261111-NBVEFCEB', '2026-11-11 05:45:09', '2026-11-11 08:45:09'
       UNION ALL SELECT (@da + 333), (@dm + 90), (@du + 68), NULL, '개인', '확정', 'RCPT-20261109-ST2MLULQ', '2026-11-09 21:27:59', '2026-11-10 03:27:59'
       UNION ALL SELECT (@da + 334), (@dm + 90), (@du + 47), NULL, '개인', '확정', 'RCPT-20261110-R9P2MR9M', '2026-11-10 22:04:04', '2026-11-11 04:04:04'
       UNION ALL SELECT (@da + 335), (@dm + 90), (@du + 85), NULL, '개인', '확정', 'RCPT-20261111-CMP5CKRX', '2026-11-11 06:40:08', '2026-11-11 09:40:08'
       UNION ALL SELECT (@da + 336), (@dm + 91), (@du + 81), (@dc + 2), '동호회', '확정', 'RCPT-20261110-NJTFG256', '2026-11-10 11:29:35', '2026-11-10 14:29:35'
       UNION ALL SELECT (@da + 337), (@dm + 91), (@du + 31), (@dc + 2), '동호회', '확정', 'RCPT-20261110-NZ4RMBF5', '2026-11-10 11:29:35', '2026-11-10 14:29:35'
       UNION ALL SELECT (@da + 338), (@dm + 91), (@du + 47), (@dc + 2), '동호회', '확정', 'RCPT-20261110-DPXACQ6F', '2026-11-10 11:29:35', '2026-11-10 14:29:35'
       UNION ALL SELECT (@da + 339), (@dm + 91), (@du + 85), (@dc + 2), '동호회', '확정', 'RCPT-20261110-LX3YJC92', '2026-11-10 11:29:35', '2026-11-10 14:29:35'
       UNION ALL SELECT (@da + 340), (@dm + 91), (@du + 77), (@dc + 2), '동호회', '확정', 'RCPT-20261110-NU48AQUV', '2026-11-10 11:29:35', '2026-11-10 14:29:35'
       UNION ALL SELECT (@da + 341), (@dm + 91), (@du + 74), NULL, '개인', '확정', 'RCPT-20261110-WPN9ZHZX', '2026-11-10 00:25:32', '2026-11-10 01:25:32'
       UNION ALL SELECT (@da + 342), (@dm + 91), (@du + 41), NULL, '개인', '확정', 'RCPT-20261110-YQ8KVXA6', '2026-11-10 17:42:18', '2026-11-10 21:42:18'
       UNION ALL SELECT (@da + 343), (@dm + 91), (@du + 2), NULL, '개인', '확정', 'RCPT-20261110-KF7PARGF', '2026-11-10 08:10:55', '2026-11-10 11:10:55'
       UNION ALL SELECT (@da + 344), (@dm + 91), (@du + 30), NULL, '개인', '확정', 'RCPT-20261109-C2DYWKL6', '2026-11-09 15:12:48', '2026-11-09 18:12:48'
       UNION ALL SELECT (@da + 345), (@dm + 91), (@du + 72), NULL, '개인', '확정', 'RCPT-20261109-RCBRP65P', '2026-11-09 14:04:20', '2026-11-09 15:04:20'
       UNION ALL SELECT (@da + 346), (@dm + 92), (@du + 49), (@dc + 3), '동호회', '대기', 'RCPT-20261111-SGHYCV5D', '2026-11-11 05:29:24', NULL
       UNION ALL SELECT (@da + 347), (@dm + 92), (@du + 1), (@dc + 3), '동호회', '대기', 'RCPT-20261111-R9J98M39', '2026-11-11 05:29:24', NULL
       UNION ALL SELECT (@da + 348), (@dm + 92), (@du + 41), (@dc + 3), '동호회', '대기', 'RCPT-20261111-XSGZJR5Q', '2026-11-11 05:29:24', NULL
       UNION ALL SELECT (@da + 349), (@dm + 92), (@du + 83), NULL, '개인', '확정', 'RCPT-20261109-D2PZVJGG', '2026-11-09 18:26:07', '2026-11-09 22:26:07'
       UNION ALL SELECT (@da + 350), (@dm + 92), (@du + 2), NULL, '개인', '확정', 'RCPT-20261110-YU9KUYVG', '2026-11-10 01:07:56', '2026-11-10 02:07:56'
       UNION ALL SELECT (@da + 351), (@dm + 92), (@du + 99), NULL, '개인', '확정', 'RCPT-20261109-MHC3STAC', '2026-11-09 09:13:00', '2026-11-09 13:13:00'
       UNION ALL SELECT (@da + 352), (@dm + 92), (@du + 71), NULL, '개인', '확정', 'RCPT-20261110-228494AZ', '2026-11-10 22:08:37', '2026-11-11 00:08:37'
       UNION ALL SELECT (@da + 353), (@dm + 93), (@du + 96), NULL, '개인', '확정', 'RCPT-20261110-KH285FX9', '2026-11-10 14:44:26', '2026-11-10 17:44:26'
       UNION ALL SELECT (@da + 354), (@dm + 93), (@du + 24), NULL, '개인', '확정', 'RCPT-20261111-4R5QA943', '2026-11-11 00:33:51', '2026-11-11 02:33:51'
       UNION ALL SELECT (@da + 355), (@dm + 93), (@du + 74), NULL, '개인', '대기', 'RCPT-20261111-Z8L5QZAZ', '2026-11-11 09:04:19', NULL
       UNION ALL SELECT (@da + 356), (@dm + 95), (@du + 5), NULL, '개인', '확정', 'RCPT-20261111-N5V6DFUH', '2026-11-11 07:51:43', '2026-11-11 10:51:43'
       UNION ALL SELECT (@da + 357), (@dm + 95), (@du + 8), NULL, '개인', '확정', 'RCPT-20261109-XQAG9RLH', '2026-11-09 09:05:53', '2026-11-09 15:05:53'
       UNION ALL SELECT (@da + 358), (@dm + 95), (@du + 22), NULL, '개인', '확정', 'RCPT-20261109-JWDCETXW', '2026-11-09 14:46:28', '2026-11-09 19:46:28'
       UNION ALL SELECT (@da + 359), (@dm + 95), (@du + 12), NULL, '개인', '확정', 'RCPT-20261109-KBLWTR7X', '2026-11-09 15:58:45', '2026-11-09 16:58:45'
       UNION ALL SELECT (@da + 360), (@dm + 97), (@du + 18), (@dc + 4), '동호회', '대기', 'RCPT-20261110-PRF5N4DW', '2026-11-10 11:47:26', NULL
       UNION ALL SELECT (@da + 361), (@dm + 97), (@du + 62), (@dc + 4), '동호회', '대기', 'RCPT-20261110-UVB8MYQ4', '2026-11-10 11:47:26', NULL
       UNION ALL SELECT (@da + 362), (@dm + 97), (@du + 75), (@dc + 4), '동호회', '대기', 'RCPT-20261110-SRSKTGKJ', '2026-11-10 11:47:26', NULL
       UNION ALL SELECT (@da + 363), (@dm + 97), (@du + 9), (@dc + 4), '동호회', '대기', 'RCPT-20261110-3FUPWHXA', '2026-11-10 11:47:26', NULL
       UNION ALL SELECT (@da + 364), (@dm + 97), (@du + 79), NULL, '개인', '확정', 'RCPT-20261109-Z7262KFL', '2026-11-09 23:27:21', '2026-11-10 04:27:21'
       UNION ALL SELECT (@da + 365), (@dm + 97), (@du + 88), NULL, '개인', '확정', 'RCPT-20261109-JKSQ7VFZ', '2026-11-09 20:46:13', '2026-11-09 22:46:13'
       UNION ALL SELECT (@da + 366), (@dm + 99), (@du + 59), NULL, '개인', '확정', 'RCPT-20261111-DG7NZ8AJ', '2026-11-11 09:14:47', '2026-11-11 11:14:47'
       UNION ALL SELECT (@da + 367), (@dm + 99), (@du + 83), NULL, '개인', '확정', 'RCPT-20261110-BQVGZJP2', '2026-11-10 19:53:13', '2026-11-11 01:53:13'
       UNION ALL SELECT (@da + 368), (@dm + 99), (@du + 33), NULL, '개인', '확정', 'RCPT-20261109-XXWC6ZV7', '2026-11-09 15:23:05', '2026-11-09 20:23:05'
       UNION ALL SELECT (@da + 369), (@dm + 99), (@du + 82), NULL, '개인', '확정', 'RCPT-20261110-ZNY9AK4C', '2026-11-10 14:23:50', '2026-11-10 20:23:50'
       UNION ALL SELECT (@da + 370), (@dm + 99), (@du + 93), NULL, '개인', '확정', 'RCPT-20261109-AJFNV2CT', '2026-11-09 09:38:06', '2026-11-09 12:38:06'
       UNION ALL SELECT (@da + 371), (@dm + 99), (@du + 15), NULL, '개인', '확정', 'RCPT-20261109-YKQQT42A', '2026-11-09 11:26:14', '2026-11-09 16:26:14'
       UNION ALL SELECT (@da + 372), (@dm + 100), (@du + 47), NULL, '개인', '확정', 'RCPT-20261111-LL2AYQMY', '2026-11-11 07:54:53', '2026-11-11 11:54:53'
       UNION ALL SELECT (@da + 373), (@dm + 100), (@du + 23), NULL, '개인', '확정', 'RCPT-20261109-M2YJKV9Y', '2026-11-09 12:21:49', '2026-11-09 13:21:49'
       UNION ALL SELECT (@da + 374), (@dm + 100), (@du + 38), NULL, '개인', '확정', 'RCPT-20261110-ZXCTXJKS', '2026-11-10 14:34:04', '2026-11-10 20:34:04'
       UNION ALL SELECT (@da + 375), (@dm + 100), (@du + 69), NULL, '개인', '확정', 'RCPT-20261109-LVXUUMDL', '2026-11-09 20:49:15', '2026-11-09 22:49:15'
       UNION ALL SELECT (@da + 376), (@dm + 100), (@du + 91), NULL, '개인', '확정', 'RCPT-20261109-YSUBTLZ7', '2026-11-09 15:06:17', '2026-11-09 21:06:17'
       UNION ALL SELECT (@da + 377), (@dm + 100), (@du + 95), NULL, '개인', '확정', 'RCPT-20261109-VHN54QUZ', '2026-11-09 10:07:52', '2026-11-09 14:07:52'
       UNION ALL SELECT (@da + 378), (@dm + 100), (@du + 45), NULL, '개인', '확정', 'RCPT-20261109-VCDC8HZP', '2026-11-09 09:27:20', '2026-11-09 14:27:20'
       UNION ALL SELECT (@da + 379), (@dm + 100), (@du + 58), NULL, '개인', '확정', 'RCPT-20261109-8P54BJ8K', '2026-11-09 15:07:19', '2026-11-09 21:07:19'
       UNION ALL SELECT (@da + 380), (@dm + 100), (@du + 4), NULL, '개인', '확정', 'RCPT-20261111-CW9N5QX3', '2026-11-11 06:25:34', '2026-11-11 11:25:34'
       UNION ALL SELECT (@da + 381), (@dm + 100), (@du + 19), NULL, '개인', '확정', 'RCPT-20261110-QESFSN5J', '2026-11-10 19:10:44', '2026-11-10 21:10:44'
       UNION ALL SELECT (@da + 382), (@dm + 102), (@du + 84), NULL, '개인', '확정', 'RCPT-20261109-R68RJ4SF', '2026-11-09 13:08:20', '2026-11-09 19:08:20'
       UNION ALL SELECT (@da + 383), (@dm + 102), (@du + 43), NULL, '개인', '확정', 'RCPT-20261110-JNTCDSWN', '2026-11-10 16:26:38', '2026-11-10 17:26:38'
       UNION ALL SELECT (@da + 384), (@dm + 102), (@du + 68), NULL, '개인', '확정', 'RCPT-20261109-AQTH2AT2', '2026-11-09 23:43:02', '2026-11-10 03:43:02'
       UNION ALL SELECT (@da + 385), (@dm + 102), (@du + 66), NULL, '개인', '확정', 'RCPT-20261109-85KQ4F3W', '2026-11-09 18:05:49', '2026-11-09 23:05:49'
       UNION ALL SELECT (@da + 386), (@dm + 104), (@du + 100), NULL, '개인', '확정', 'RCPT-20261110-F68N2KJK', '2026-11-10 17:47:00', '2026-11-10 21:47:00'
       UNION ALL SELECT (@da + 387), (@dm + 104), (@du + 42), NULL, '개인', '확정', 'RCPT-20261110-2LGBVQGC', '2026-11-10 05:47:10', '2026-11-10 06:47:10'
       UNION ALL SELECT (@da + 388), (@dm + 104), (@du + 52), NULL, '개인', '대기', 'RCPT-20261110-YUA8GKZR', '2026-11-10 16:04:39', NULL
       UNION ALL SELECT (@da + 389), (@dm + 106), (@du + 76), NULL, '개인', '확정', 'RCPT-20261110-897MLSVB', '2026-11-10 13:31:03', '2026-11-10 14:31:03'
       UNION ALL SELECT (@da + 390), (@dm + 106), (@du + 55), NULL, '개인', '확정', 'RCPT-20261111-U3KTHYZ9', '2026-11-11 06:52:51', '2026-11-11 08:52:51'
       UNION ALL SELECT (@da + 391), (@dm + 106), (@du + 29), NULL, '개인', '확정', 'RCPT-20261110-5GT5QKJ6', '2026-11-10 00:10:07', '2026-11-10 05:10:07'
       UNION ALL SELECT (@da + 392), (@dm + 106), (@du + 27), NULL, '개인', '확정', 'RCPT-20261109-TQD9F8DF', '2026-11-09 10:46:35', '2026-11-09 15:46:35'
       UNION ALL SELECT (@da + 393), (@dm + 106), (@du + 28), NULL, '개인', '확정', 'RCPT-20261111-GMR84YTY', '2026-11-11 00:02:29', '2026-11-11 02:02:29'
       UNION ALL SELECT (@da + 394), (@dm + 106), (@du + 65), NULL, '개인', '확정', 'RCPT-20261109-G6PP5QTQ', '2026-11-09 20:51:37', '2026-11-09 21:51:37'
       UNION ALL SELECT (@da + 395), (@dm + 107), (@du + 67), NULL, '개인', '확정', 'RCPT-20261110-UC8ANZYH', '2026-11-10 03:31:36', '2026-11-10 09:31:36'
       UNION ALL SELECT (@da + 396), (@dm + 107), (@du + 60), NULL, '개인', '확정', 'RCPT-20261111-8C3ARPPZ', '2026-11-11 09:24:39', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 397), (@dm + 107), (@du + 39), NULL, '개인', '확정', 'RCPT-20261111-42ADD6MC', '2026-11-11 11:28:01', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 398), (@dm + 107), (@du + 3), NULL, '개인', '확정', 'RCPT-20261109-665JMFN9', '2026-11-09 11:43:13', '2026-11-09 15:43:13'
       UNION ALL SELECT (@da + 399), (@dm + 107), (@du + 96), NULL, '개인', '확정', 'RCPT-20261109-NJRJ5W5L', '2026-11-09 17:41:29', '2026-11-09 22:41:29'
       UNION ALL SELECT (@da + 400), (@dm + 107), (@du + 83), NULL, '개인', '대기', 'RCPT-20261109-QH4RQ7UL', '2026-11-09 15:33:33', NULL
       UNION ALL SELECT (@da + 401), (@dm + 107), (@du + 87), NULL, '개인', '대기', 'RCPT-20261111-LG2PUJ43', '2026-11-11 03:33:24', NULL
       UNION ALL SELECT (@da + 402), (@dm + 108), (@du + 59), NULL, '개인', '확정', 'RCPT-20261110-TDB38SQV', '2026-11-10 10:33:37', '2026-11-10 11:33:37'
       UNION ALL SELECT (@da + 403), (@dm + 108), (@du + 81), NULL, '개인', '확정', 'RCPT-20261110-DBSXP73C', '2026-11-10 19:17:43', '2026-11-10 21:17:43'
       UNION ALL SELECT (@da + 404), (@dm + 108), (@du + 11), NULL, '개인', '확정', 'RCPT-20261109-HXLNZUGL', '2026-11-09 11:14:22', '2026-11-09 14:14:22'
       UNION ALL SELECT (@da + 405), (@dm + 109), (@du + 4), NULL, '개인', '확정', 'RCPT-20261110-5ELPCY7K', '2026-11-10 02:31:18', '2026-11-10 06:31:18'
       UNION ALL SELECT (@da + 406), (@dm + 109), (@du + 59), NULL, '개인', '확정', 'RCPT-20261110-NP2PAMJT', '2026-11-10 23:28:17', '2026-11-11 03:28:17'
       UNION ALL SELECT (@da + 407), (@dm + 109), (@du + 83), NULL, '개인', '확정', 'RCPT-20261109-PTC85ZU4', '2026-11-09 16:10:06', '2026-11-09 20:10:06'
       UNION ALL SELECT (@da + 408), (@dm + 109), (@du + 77), NULL, '개인', '확정', 'RCPT-20261110-S2L59SHM', '2026-11-10 07:16:53', '2026-11-10 09:16:53'
       UNION ALL SELECT (@da + 409), (@dm + 109), (@du + 56), NULL, '개인', '확정', 'RCPT-20261109-XS6Q9FAJ', '2026-11-09 11:38:39', '2026-11-09 17:38:39'
       UNION ALL SELECT (@da + 410), (@dm + 109), (@du + 23), NULL, '개인', '확정', 'RCPT-20261109-PR3DYHLZ', '2026-11-09 17:30:19', '2026-11-09 18:30:19'
       UNION ALL SELECT (@da + 411), (@dm + 109), (@du + 61), NULL, '개인', '확정', 'RCPT-20261110-LS8F4JYP', '2026-11-10 16:46:22', '2026-11-10 19:46:22'
       UNION ALL SELECT (@da + 412), (@dm + 109), (@du + 90), NULL, '개인', '확정', 'RCPT-20261110-9UCPY5R4', '2026-11-10 07:31:48', '2026-11-10 11:31:48'
       UNION ALL SELECT (@da + 413), (@dm + 109), (@du + 81), NULL, '개인', '확정', 'RCPT-20261111-PPGV33ZG', '2026-11-11 05:15:30', '2026-11-11 11:15:30'
       UNION ALL SELECT (@da + 414), (@dm + 109), (@du + 19), NULL, '개인', '확정', 'RCPT-20261111-PE4SD9M8', '2026-11-11 07:55:39', '2026-11-11 10:55:39'
       UNION ALL SELECT (@da + 415), (@dm + 111), (@du + 48), NULL, '개인', '확정', 'RCPT-20261111-272WXUQD', '2026-11-11 08:33:32', '2026-11-11 10:33:32'
       UNION ALL SELECT (@da + 416), (@dm + 111), (@du + 79), NULL, '개인', '확정', 'RCPT-20261109-ED8U83WW', '2026-11-09 21:23:33', '2026-11-10 02:23:33'
       UNION ALL SELECT (@da + 417), (@dm + 111), (@du + 98), NULL, '개인', '확정', 'RCPT-20261110-RNCSA3BK', '2026-11-10 07:45:03', '2026-11-10 08:45:03'
       UNION ALL SELECT (@da + 418), (@dm + 111), (@du + 57), NULL, '개인', '확정', 'RCPT-20261110-69HLEDTV', '2026-11-10 20:43:08', '2026-11-10 22:43:08'
       UNION ALL SELECT (@da + 419), (@dm + 113), (@du + 100), NULL, '개인', '확정', 'RCPT-20261111-V22FZ9HK', '2026-11-11 03:15:56', '2026-11-11 04:15:56'
       UNION ALL SELECT (@da + 420), (@dm + 113), (@du + 66), NULL, '개인', '확정', 'RCPT-20261111-29V22E7Y', '2026-11-11 08:00:29', '2026-11-11 11:00:29'
       UNION ALL SELECT (@da + 421), (@dm + 113), (@du + 18), NULL, '개인', '대기', 'RCPT-20261110-5ALRX9SF', '2026-11-10 16:10:35', NULL
       UNION ALL SELECT (@da + 422), (@dm + 115), (@du + 75), NULL, '개인', '확정', 'RCPT-20261110-WYV5RDSJ', '2026-11-10 00:42:47', '2026-11-10 03:42:47'
       UNION ALL SELECT (@da + 423), (@dm + 115), (@du + 50), NULL, '개인', '확정', 'RCPT-20261111-F4GVCB55', '2026-11-11 10:32:28', '2026-11-11 11:32:28'
       UNION ALL SELECT (@da + 424), (@dm + 115), (@du + 63), NULL, '개인', '확정', 'RCPT-20261109-KJBXP3SX', '2026-11-09 21:32:40', '2026-11-09 22:32:40'
       UNION ALL SELECT (@da + 425), (@dm + 115), (@du + 71), NULL, '개인', '확정', 'RCPT-20261111-L98P7R2W', '2026-11-11 05:41:02', '2026-11-11 11:41:02'
       UNION ALL SELECT (@da + 426), (@dm + 115), (@du + 3), NULL, '개인', '확정', 'RCPT-20261110-SRVFUF7Q', '2026-11-10 04:51:35', '2026-11-10 09:51:35'
       UNION ALL SELECT (@da + 427), (@dm + 115), (@du + 87), NULL, '개인', '확정', 'RCPT-20261109-3M8EJYRT', '2026-11-09 15:06:09', '2026-11-09 18:06:09'
       UNION ALL SELECT (@da + 428), (@dm + 116), (@du + 8), NULL, '개인', '확정', 'RCPT-20261109-MY6Z7C2V', '2026-11-09 23:29:18', '2026-11-10 00:29:18'
       UNION ALL SELECT (@da + 429), (@dm + 116), (@du + 60), NULL, '개인', '확정', 'RCPT-20261111-7EPW4FGF', '2026-11-11 01:36:42', '2026-11-11 02:36:42'
       UNION ALL SELECT (@da + 430), (@dm + 116), (@du + 26), NULL, '개인', '확정', 'RCPT-20261111-D243G89V', '2026-11-11 11:50:04', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 431), (@dm + 116), (@du + 72), NULL, '개인', '확정', 'RCPT-20261110-6PBX2TR2', '2026-11-10 18:05:47', '2026-11-10 21:05:47'
       UNION ALL SELECT (@da + 432), (@dm + 116), (@du + 13), NULL, '개인', '확정', 'RCPT-20261110-3FZ6PFRD', '2026-11-10 08:20:06', '2026-11-10 13:20:06'
       UNION ALL SELECT (@da + 433), (@dm + 116), (@du + 76), NULL, '개인', '대기', 'RCPT-20261110-VYRKWMEV', '2026-11-10 00:06:51', NULL
       UNION ALL SELECT (@da + 434), (@dm + 116), (@du + 9), NULL, '개인', '대기', 'RCPT-20261110-QV2HPPNH', '2026-11-10 01:24:07', NULL
       UNION ALL SELECT (@da + 435), (@dm + 117), (@du + 7), NULL, '개인', '확정', 'RCPT-20261110-X6F63W6A', '2026-11-10 23:52:52', '2026-11-11 04:52:52'
       UNION ALL SELECT (@da + 436), (@dm + 117), (@du + 47), NULL, '개인', '확정', 'RCPT-20261111-LA3NLLK8', '2026-11-11 10:49:18', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 437), (@dm + 117), (@du + 85), NULL, '개인', '확정', 'RCPT-20261111-QJTCS4KL', '2026-11-11 11:05:25', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 438), (@dm + 118), (@du + 95), NULL, '개인', '확정', 'RCPT-20261110-C3AWFDK7', '2026-11-10 01:29:58', '2026-11-10 02:29:58'
       UNION ALL SELECT (@da + 439), (@dm + 118), (@du + 68), NULL, '개인', '확정', 'RCPT-20261110-FR7U3Z6E', '2026-11-10 17:39:45', '2026-11-10 20:39:45'
       UNION ALL SELECT (@da + 440), (@dm + 118), (@du + 31), NULL, '개인', '확정', 'RCPT-20261110-MSNB2SEJ', '2026-11-10 00:21:38', '2026-11-10 04:21:38'
       UNION ALL SELECT (@da + 441), (@dm + 118), (@du + 83), NULL, '개인', '확정', 'RCPT-20261111-C8SDTG26', '2026-11-11 00:24:27', '2026-11-11 03:24:27'
       UNION ALL SELECT (@da + 442), (@dm + 118), (@du + 61), NULL, '개인', '확정', 'RCPT-20261111-7PPXQ8QC', '2026-11-11 07:36:40', '2026-11-11 11:36:40'
       UNION ALL SELECT (@da + 443), (@dm + 118), (@du + 57), NULL, '개인', '확정', 'RCPT-20261110-4WWJ52FU', '2026-11-10 11:10:27', '2026-11-10 15:10:27'
       UNION ALL SELECT (@da + 444), (@dm + 118), (@du + 34), NULL, '개인', '확정', 'RCPT-20261109-652B3BE4', '2026-11-09 19:59:45', '2026-11-09 23:59:45'
       UNION ALL SELECT (@da + 445), (@dm + 118), (@du + 42), NULL, '개인', '확정', 'RCPT-20261109-9VTEDQZA', '2026-11-09 09:57:12', '2026-11-09 10:57:12'
       UNION ALL SELECT (@da + 446), (@dm + 118), (@du + 69), NULL, '개인', '확정', 'RCPT-20261111-CV2ZN6ZH', '2026-11-11 10:46:19', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 447), (@dm + 118), (@du + 91), NULL, '개인', '확정', 'RCPT-20261110-4NFAWTP5', '2026-11-10 21:47:05', '2026-11-10 23:47:05'
       UNION ALL SELECT (@da + 448), (@dm + 120), (@du + 94), NULL, '개인', '확정', 'RCPT-20261109-YBZSG5VN', '2026-11-09 15:21:27', '2026-11-09 18:21:27'
       UNION ALL SELECT (@da + 449), (@dm + 120), (@du + 93), NULL, '개인', '확정', 'RCPT-20261111-S4QCYEV8', '2026-11-11 08:04:47', '2026-11-11 09:04:47'
       UNION ALL SELECT (@da + 450), (@dm + 120), (@du + 56), NULL, '개인', '확정', 'RCPT-20261110-AQ3ZD5D2', '2026-11-10 02:34:50', '2026-11-10 08:34:50'
) v
 WHERE @demo_ready = 1;

INSERT INTO match_application (application_id, match_id, applicant_user_id, club_id, application_type, status, receipt_no, created_at, confirmed_at)
SELECT * FROM (
                 SELECT (@da + 451) AS application_id, (@dm + 120) AS match_id, (@du + 53) AS applicant_user_id, NULL AS club_id, '개인' AS application_type, '확정' AS status, 'RCPT-20261110-LRD6PD52' AS receipt_no, '2026-11-10 11:22:46' AS created_at, '2026-11-10 17:22:46' AS confirmed_at
       UNION ALL SELECT (@da + 452), (@dm + 122), (@du + 63), NULL, '개인', '확정', 'RCPT-20261111-M56QHFUH', '2026-11-11 08:31:48', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 453), (@dm + 122), (@du + 5), NULL, '개인', '확정', 'RCPT-20261111-RW3F3D3Z', '2026-11-11 07:10:36', '2026-11-11 10:10:36'
       UNION ALL SELECT (@da + 454), (@dm + 122), (@du + 25), NULL, '개인', '대기', 'RCPT-20261110-B48H88X6', '2026-11-10 01:34:29', NULL
       UNION ALL SELECT (@da + 455), (@dm + 124), (@du + 71), NULL, '개인', '확정', 'RCPT-20261109-XHM6VZQJ', '2026-11-09 09:31:37', '2026-11-09 12:31:37'
       UNION ALL SELECT (@da + 456), (@dm + 124), (@du + 7), NULL, '개인', '확정', 'RCPT-20261109-WQELFZVL', '2026-11-09 15:46:34', '2026-11-09 21:46:34'
       UNION ALL SELECT (@da + 457), (@dm + 124), (@du + 84), NULL, '개인', '확정', 'RCPT-20261110-GAJ8LU3D', '2026-11-10 12:00:36', '2026-11-10 17:00:36'
       UNION ALL SELECT (@da + 458), (@dm + 124), (@du + 76), NULL, '개인', '확정', 'RCPT-20261110-9LXHUEDX', '2026-11-10 19:47:29', '2026-11-11 01:47:29'
       UNION ALL SELECT (@da + 459), (@dm + 124), (@du + 24), NULL, '개인', '확정', 'RCPT-20261110-SZ62VJGM', '2026-11-10 06:11:21', '2026-11-10 11:11:21'
       UNION ALL SELECT (@da + 460), (@dm + 124), (@du + 15), NULL, '개인', '확정', 'RCPT-20261110-3CC4XTF6', '2026-11-10 12:53:32', '2026-11-10 18:53:32'
       UNION ALL SELECT (@da + 461), (@dm + 125), (@du + 3), NULL, '개인', '확정', 'RCPT-20261109-SUR9BEG3', '2026-11-09 21:45:13', '2026-11-10 03:45:13'
       UNION ALL SELECT (@da + 462), (@dm + 125), (@du + 13), NULL, '개인', '확정', 'RCPT-20261110-MLDN278C', '2026-11-10 05:49:34', '2026-11-10 08:49:34'
       UNION ALL SELECT (@da + 463), (@dm + 125), (@du + 9), NULL, '개인', '확정', 'RCPT-20261111-GX2SMX97', '2026-11-11 09:50:50', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 464), (@dm + 125), (@du + 55), NULL, '개인', '확정', 'RCPT-20261111-RDMRFUAH', '2026-11-11 07:51:28', '2026-11-11 08:51:28'
       UNION ALL SELECT (@da + 465), (@dm + 125), (@du + 96), NULL, '개인', '확정', 'RCPT-20261111-V84G95M8', '2026-11-11 11:15:12', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 466), (@dm + 125), (@du + 39), NULL, '개인', '대기', 'RCPT-20261110-KJQTAW32', '2026-11-10 13:27:14', NULL
       UNION ALL SELECT (@da + 467), (@dm + 125), (@du + 50), NULL, '개인', '대기', 'RCPT-20261110-K3DCQHU7', '2026-11-10 05:04:14', NULL
       UNION ALL SELECT (@da + 468), (@dm + 126), (@du + 52), NULL, '개인', '확정', 'RCPT-20261111-LX5KYSVU', '2026-11-11 03:24:51', '2026-11-11 09:24:51'
       UNION ALL SELECT (@da + 469), (@dm + 126), (@du + 47), NULL, '개인', '확정', 'RCPT-20261109-JJKHTG9R', '2026-11-09 19:07:56', '2026-11-09 22:07:56'
       UNION ALL SELECT (@da + 470), (@dm + 126), (@du + 11), NULL, '개인', '확정', 'RCPT-20261111-MQSNFG4L', '2026-11-11 08:14:40', '2026-11-11 11:14:40'
       UNION ALL SELECT (@da + 471), (@dm + 127), (@du + 72), NULL, '개인', '확정', 'RCPT-20261110-KFXW79L2', '2026-11-10 22:31:52', '2026-11-11 00:31:52'
       UNION ALL SELECT (@da + 472), (@dm + 127), (@du + 78), NULL, '개인', '확정', 'RCPT-20261110-LQDB2GMJ', '2026-11-10 21:28:57', '2026-11-11 00:28:57'
       UNION ALL SELECT (@da + 473), (@dm + 127), (@du + 1), NULL, '개인', '확정', 'RCPT-20261110-NUZDWMSZ', '2026-11-10 08:35:07', '2026-11-10 10:35:07'
       UNION ALL SELECT (@da + 474), (@dm + 127), (@du + 22), NULL, '개인', '확정', 'RCPT-20261110-L242NHWM', '2026-11-10 08:13:54', '2026-11-10 09:13:54'
       UNION ALL SELECT (@da + 475), (@dm + 127), (@du + 3), NULL, '개인', '확정', 'RCPT-20261110-TUCC3F9M', '2026-11-10 18:04:52', '2026-11-10 20:04:52'
       UNION ALL SELECT (@da + 476), (@dm + 127), (@du + 65), NULL, '개인', '확정', 'RCPT-20261109-FK3USLFN', '2026-11-09 14:38:25', '2026-11-09 18:38:25'
       UNION ALL SELECT (@da + 477), (@dm + 127), (@du + 59), NULL, '개인', '확정', 'RCPT-20261110-CHCL3H2Q', '2026-11-10 09:52:01', '2026-11-10 11:52:01'
       UNION ALL SELECT (@da + 478), (@dm + 127), (@du + 56), NULL, '개인', '확정', 'RCPT-20261110-3F3H8U5M', '2026-11-10 14:09:36', '2026-11-10 17:09:36'
       UNION ALL SELECT (@da + 479), (@dm + 127), (@du + 97), NULL, '개인', '확정', 'RCPT-20261110-FHBS24WT', '2026-11-10 12:55:40', '2026-11-10 13:55:40'
       UNION ALL SELECT (@da + 480), (@dm + 127), (@du + 35), NULL, '개인', '확정', 'RCPT-20261111-RCVBU64B', '2026-11-11 04:06:54', '2026-11-11 09:06:54'
       UNION ALL SELECT (@da + 481), (@dm + 129), (@du + 48), NULL, '개인', '확정', 'RCPT-20261110-2AZVQ8XN', '2026-11-10 15:50:57', '2026-11-10 21:50:57'
       UNION ALL SELECT (@da + 482), (@dm + 129), (@du + 89), NULL, '개인', '확정', 'RCPT-20261109-NA2V3ZX4', '2026-11-09 23:19:12', '2026-11-10 04:19:12'
       UNION ALL SELECT (@da + 483), (@dm + 129), (@du + 79), NULL, '개인', '확정', 'RCPT-20261109-QV4QG8AL', '2026-11-09 22:07:16', '2026-11-10 00:07:16'
       UNION ALL SELECT (@da + 484), (@dm + 129), (@du + 24), NULL, '개인', '확정', 'RCPT-20261110-3SA6P6PE', '2026-11-10 18:44:14', '2026-11-10 22:44:14'
       UNION ALL SELECT (@da + 485), (@dm + 131), (@du + 2), NULL, '개인', '확정', 'RCPT-20261110-PRRJFNC3', '2026-11-10 12:13:52', '2026-11-10 18:13:52'
       UNION ALL SELECT (@da + 486), (@dm + 131), (@du + 51), NULL, '개인', '확정', 'RCPT-20261110-HWJXHXZL', '2026-11-10 08:15:56', '2026-11-10 13:15:56'
       UNION ALL SELECT (@da + 487), (@dm + 131), (@du + 96), NULL, '개인', '대기', 'RCPT-20261109-85L2NMCZ', '2026-11-09 16:26:42', NULL
       UNION ALL SELECT (@da + 488), (@dm + 133), (@du + 87), NULL, '개인', '확정', 'RCPT-20261111-76RSBHY4', '2026-11-11 08:30:40', '2026-11-11 09:30:40'
       UNION ALL SELECT (@da + 489), (@dm + 133), (@du + 14), NULL, '개인', '확정', 'RCPT-20261110-A8QCVHEA', '2026-11-10 16:35:26', '2026-11-10 22:35:26'
       UNION ALL SELECT (@da + 490), (@dm + 133), (@du + 26), NULL, '개인', '확정', 'RCPT-20261111-V26Y2TRX', '2026-11-11 02:54:06', '2026-11-11 06:54:06'
       UNION ALL SELECT (@da + 491), (@dm + 133), (@du + 58), NULL, '개인', '확정', 'RCPT-20261110-XKU3TMMT', '2026-11-10 10:32:54', '2026-11-10 13:32:54'
       UNION ALL SELECT (@da + 492), (@dm + 133), (@du + 7), NULL, '개인', '확정', 'RCPT-20261109-9DES7A79', '2026-11-09 14:03:43', '2026-11-09 18:03:43'
       UNION ALL SELECT (@da + 493), (@dm + 133), (@du + 70), NULL, '개인', '확정', 'RCPT-20261110-BLSDNS4E', '2026-11-10 09:22:50', '2026-11-10 11:22:50'
       UNION ALL SELECT (@da + 494), (@dm + 134), (@du + 13), NULL, '개인', '확정', 'RCPT-20261111-7B52YVBJ', '2026-11-11 08:17:54', '2026-11-11 09:17:54'
       UNION ALL SELECT (@da + 495), (@dm + 134), (@du + 76), NULL, '개인', '확정', 'RCPT-20261110-UDZHD8EB', '2026-11-10 16:32:18', '2026-11-10 19:32:18'
       UNION ALL SELECT (@da + 496), (@dm + 134), (@du + 36), NULL, '개인', '확정', 'RCPT-20261109-GQCYG8VX', '2026-11-09 11:53:53', '2026-11-09 14:53:53'
       UNION ALL SELECT (@da + 497), (@dm + 134), (@du + 60), NULL, '개인', '확정', 'RCPT-20261110-PDC239XU', '2026-11-10 15:44:09', '2026-11-10 16:44:09'
       UNION ALL SELECT (@da + 498), (@dm + 134), (@du + 63), NULL, '개인', '확정', 'RCPT-20261109-7ZPMNK6Y', '2026-11-09 15:47:24', '2026-11-09 17:47:24'
       UNION ALL SELECT (@da + 499), (@dm + 134), (@du + 18), NULL, '개인', '대기', 'RCPT-20261110-K7DSWEYF', '2026-11-10 14:12:05', NULL
       UNION ALL SELECT (@da + 500), (@dm + 134), (@du + 9), NULL, '개인', '대기', 'RCPT-20261110-4XZSHARA', '2026-11-10 02:36:16', NULL
       UNION ALL SELECT (@da + 501), (@dm + 135), (@du + 27), NULL, '개인', '확정', 'RCPT-20261110-ME2JDPEX', '2026-11-10 08:48:49', '2026-11-10 10:48:49'
       UNION ALL SELECT (@da + 502), (@dm + 135), (@du + 12), NULL, '개인', '확정', 'RCPT-20261110-RD4YRLKB', '2026-11-10 16:36:52', '2026-11-10 21:36:52'
       UNION ALL SELECT (@da + 503), (@dm + 135), (@du + 41), NULL, '개인', '확정', 'RCPT-20261110-AZ3U2RCM', '2026-11-10 11:46:27', '2026-11-10 17:46:27'
       UNION ALL SELECT (@da + 504), (@dm + 136), (@du + 95), NULL, '개인', '확정', 'RCPT-20261109-A4KGX35M', '2026-11-09 13:48:05', '2026-11-09 17:48:05'
       UNION ALL SELECT (@da + 505), (@dm + 136), (@du + 79), NULL, '개인', '확정', 'RCPT-20261111-BLSN5YGX', '2026-11-11 10:54:45', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 506), (@dm + 136), (@du + 42), NULL, '개인', '확정', 'RCPT-20261109-PEQ35N55', '2026-11-09 09:16:10', '2026-11-09 11:16:10'
       UNION ALL SELECT (@da + 507), (@dm + 136), (@du + 20), NULL, '개인', '확정', 'RCPT-20261109-6VVCHP5P', '2026-11-09 11:31:33', '2026-11-09 16:31:33'
       UNION ALL SELECT (@da + 508), (@dm + 136), (@du + 100), NULL, '개인', '확정', 'RCPT-20261109-5CJTUY78', '2026-11-09 23:50:16', '2026-11-10 02:50:16'
       UNION ALL SELECT (@da + 509), (@dm + 136), (@du + 1), NULL, '개인', '확정', 'RCPT-20261109-GYP6Q9PY', '2026-11-09 10:42:36', '2026-11-09 11:42:36'
       UNION ALL SELECT (@da + 510), (@dm + 136), (@du + 99), NULL, '개인', '확정', 'RCPT-20261109-XSLW2Q9F', '2026-11-09 10:46:01', '2026-11-09 14:46:01'
       UNION ALL SELECT (@da + 511), (@dm + 136), (@du + 45), NULL, '개인', '확정', 'RCPT-20261110-5JKBUUNN', '2026-11-10 12:15:02', '2026-11-10 15:15:02'
       UNION ALL SELECT (@da + 512), (@dm + 136), (@du + 80), NULL, '개인', '확정', 'RCPT-20261109-SD26CB7N', '2026-11-09 09:56:17', '2026-11-09 12:56:17'
       UNION ALL SELECT (@da + 513), (@dm + 136), (@du + 51), NULL, '개인', '확정', 'RCPT-20261111-EFL9LQL5', '2026-11-11 06:47:57', '2026-11-11 11:47:57'
       UNION ALL SELECT (@da + 514), (@dm + 138), (@du + 93), NULL, '개인', '확정', 'RCPT-20261110-24M236DG', '2026-11-10 06:41:57', '2026-11-10 08:41:57'
       UNION ALL SELECT (@da + 515), (@dm + 138), (@du + 60), NULL, '개인', '확정', 'RCPT-20261110-LRGCNFYQ', '2026-11-10 02:28:18', '2026-11-10 03:28:18'
       UNION ALL SELECT (@da + 516), (@dm + 138), (@du + 82), NULL, '개인', '확정', 'RCPT-20261110-B8GYQDZ3', '2026-11-10 11:39:41', '2026-11-10 13:39:41'
       UNION ALL SELECT (@da + 517), (@dm + 138), (@du + 34), NULL, '개인', '확정', 'RCPT-20261111-DCXA6T3S', '2026-11-11 11:03:39', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 518), (@dm + 140), (@du + 59), NULL, '개인', '확정', 'RCPT-20261109-MWN57DK3', '2026-11-09 21:09:14', '2026-11-09 23:09:14'
       UNION ALL SELECT (@da + 519), (@dm + 140), (@du + 56), NULL, '개인', '확정', 'RCPT-20261110-QV7F7D82', '2026-11-10 19:57:52', '2026-11-10 21:57:52'
       UNION ALL SELECT (@da + 520), (@dm + 140), (@du + 10), NULL, '개인', '대기', 'RCPT-20261109-KUY9HQF9', '2026-11-09 16:07:39', NULL
       UNION ALL SELECT (@da + 521), (@dm + 142), (@du + 27), NULL, '개인', '확정', 'RCPT-20261109-LJPPL6MU', '2026-11-09 13:28:20', '2026-11-09 14:28:20'
       UNION ALL SELECT (@da + 522), (@dm + 142), (@du + 72), NULL, '개인', '확정', 'RCPT-20261110-NRE5S6ML', '2026-11-10 03:24:30', '2026-11-10 08:24:30'
       UNION ALL SELECT (@da + 523), (@dm + 142), (@du + 6), NULL, '개인', '확정', 'RCPT-20261110-SFJH9FA3', '2026-11-10 12:20:06', '2026-11-10 17:20:06'
       UNION ALL SELECT (@da + 524), (@dm + 142), (@du + 88), NULL, '개인', '확정', 'RCPT-20261110-KQJSJB45', '2026-11-10 05:26:10', '2026-11-10 10:26:10'
       UNION ALL SELECT (@da + 525), (@dm + 142), (@du + 15), NULL, '개인', '확정', 'RCPT-20261110-W6UUD4N5', '2026-11-10 03:16:41', '2026-11-10 06:16:41'
       UNION ALL SELECT (@da + 526), (@dm + 142), (@du + 97), NULL, '개인', '확정', 'RCPT-20261111-AG557H3L', '2026-11-11 02:32:29', '2026-11-11 07:32:29'
       UNION ALL SELECT (@da + 527), (@dm + 143), (@du + 50), NULL, '개인', '확정', 'RCPT-20261111-CBH8K6JV', '2026-11-11 02:08:35', '2026-11-11 06:08:35'
       UNION ALL SELECT (@da + 528), (@dm + 143), (@du + 30), NULL, '개인', '확정', 'RCPT-20261111-HYP2QHHD', '2026-11-11 10:20:45', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 529), (@dm + 143), (@du + 70), NULL, '개인', '확정', 'RCPT-20261110-A37MH3C4', '2026-11-10 04:20:06', '2026-11-10 06:20:06'
       UNION ALL SELECT (@da + 530), (@dm + 143), (@du + 18), NULL, '개인', '확정', 'RCPT-20261111-RLC2VPJ9', '2026-11-11 00:17:37', '2026-11-11 02:17:37'
       UNION ALL SELECT (@da + 531), (@dm + 143), (@du + 63), NULL, '개인', '확정', 'RCPT-20261109-5MQUFN89', '2026-11-09 18:13:50', '2026-11-09 23:13:50'
       UNION ALL SELECT (@da + 532), (@dm + 143), (@du + 3), NULL, '개인', '대기', 'RCPT-20261110-7ANDT273', '2026-11-10 06:57:47', NULL
       UNION ALL SELECT (@da + 533), (@dm + 143), (@du + 9), NULL, '개인', '대기', 'RCPT-20261109-MW78AAXK', '2026-11-09 21:24:49', NULL
       UNION ALL SELECT (@da + 534), (@dm + 144), (@du + 67), NULL, '개인', '확정', 'RCPT-20261109-P75WDUVK', '2026-11-09 14:32:33', '2026-11-09 19:32:33'
       UNION ALL SELECT (@da + 535), (@dm + 144), (@du + 73), NULL, '개인', '확정', 'RCPT-20261110-AYFTYKWD', '2026-11-10 01:01:17', '2026-11-10 05:01:17'
       UNION ALL SELECT (@da + 536), (@dm + 144), (@du + 36), NULL, '개인', '확정', 'RCPT-20261110-CY3B855V', '2026-11-10 02:09:39', '2026-11-10 03:09:39'
       UNION ALL SELECT (@da + 537), (@dm + 145), (@du + 81), (@dc + 2), '동호회', '확정', 'RCPT-20261111-W6LTJEZR', '2026-11-11 02:51:36', '2026-11-11 05:51:36'
       UNION ALL SELECT (@da + 538), (@dm + 145), (@du + 85), (@dc + 2), '동호회', '확정', 'RCPT-20261111-6DHSQL4A', '2026-11-11 02:51:36', '2026-11-11 05:51:36'
       UNION ALL SELECT (@da + 539), (@dm + 145), (@du + 61), (@dc + 2), '동호회', '확정', 'RCPT-20261111-DGFMTTHT', '2026-11-11 02:51:36', '2026-11-11 05:51:36'
       UNION ALL SELECT (@da + 540), (@dm + 145), (@du + 78), (@dc + 2), '동호회', '확정', 'RCPT-20261111-KTSBXPWQ', '2026-11-11 02:51:36', '2026-11-11 05:51:36'
       UNION ALL SELECT (@da + 541), (@dm + 145), (@du + 91), (@dc + 2), '동호회', '확정', 'RCPT-20261111-8L4JTSHP', '2026-11-11 02:51:36', '2026-11-11 05:51:36'
       UNION ALL SELECT (@da + 542), (@dm + 145), (@du + 74), NULL, '개인', '확정', 'RCPT-20261110-QKHS5RW7', '2026-11-10 18:06:14', '2026-11-10 23:06:14'
       UNION ALL SELECT (@da + 543), (@dm + 145), (@du + 1), NULL, '개인', '확정', 'RCPT-20261109-R48GTHYP', '2026-11-09 20:40:04', '2026-11-09 23:40:04'
       UNION ALL SELECT (@da + 544), (@dm + 145), (@du + 72), NULL, '개인', '확정', 'RCPT-20261111-S3542Z57', '2026-11-11 01:43:08', '2026-11-11 04:43:08'
       UNION ALL SELECT (@da + 545), (@dm + 145), (@du + 86), NULL, '개인', '확정', 'RCPT-20261109-B4VPAUQD', '2026-11-09 17:22:23', '2026-11-09 22:22:23'
       UNION ALL SELECT (@da + 546), (@dm + 145), (@du + 20), NULL, '개인', '확정', 'RCPT-20261111-LJJH9E2V', '2026-11-11 06:58:13', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 547), (@dm + 146), (@du + 49), (@dc + 3), '동호회', '대기', 'RCPT-20261109-HDWWWWDK', '2026-11-09 10:10:51', NULL
       UNION ALL SELECT (@da + 548), (@dm + 146), (@du + 58), (@dc + 3), '동호회', '대기', 'RCPT-20261109-8X9663ZT', '2026-11-09 10:10:51', NULL
       UNION ALL SELECT (@da + 549), (@dm + 146), (@du + 3), (@dc + 3), '동호회', '대기', 'RCPT-20261109-MSXNCW5A', '2026-11-09 10:10:51', NULL
       UNION ALL SELECT (@da + 550), (@dm + 146), (@du + 30), NULL, '개인', '확정', 'RCPT-20261110-7E9228TE', '2026-11-10 12:26:31', '2026-11-10 13:26:31'
       UNION ALL SELECT (@da + 551), (@dm + 146), (@du + 78), NULL, '개인', '확정', 'RCPT-20261111-L4MKNQ3E', '2026-11-11 09:59:05', '2026-11-11 11:59:05'
       UNION ALL SELECT (@da + 552), (@dm + 146), (@du + 97), NULL, '개인', '확정', 'RCPT-20261111-JMRWG3NQ', '2026-11-11 01:03:37', '2026-11-11 06:03:37'
       UNION ALL SELECT (@da + 553), (@dm + 146), (@du + 61), NULL, '개인', '확정', 'RCPT-20261110-HXE99X4Q', '2026-11-10 10:09:44', '2026-11-10 14:09:44'
       UNION ALL SELECT (@da + 554), (@dm + 147), (@du + 56), NULL, '개인', '확정', 'RCPT-20261110-U46C4W7N', '2026-11-10 00:49:44', '2026-11-10 03:49:44'
       UNION ALL SELECT (@da + 555), (@dm + 147), (@du + 43), NULL, '개인', '확정', 'RCPT-20261111-9T34UCAB', '2026-11-11 04:43:04', '2026-11-11 09:43:04'
       UNION ALL SELECT (@da + 556), (@dm + 147), (@du + 89), NULL, '개인', '대기', 'RCPT-20261110-3U23NVRN', '2026-11-10 23:25:24', NULL
       UNION ALL SELECT (@da + 557), (@dm + 149), (@du + 100), NULL, '개인', '확정', 'RCPT-20261109-DJT3WWAA', '2026-11-09 16:44:27', '2026-11-09 19:44:27'
       UNION ALL SELECT (@da + 558), (@dm + 149), (@du + 63), NULL, '개인', '확정', 'RCPT-20261110-ZG85948Q', '2026-11-10 10:58:00', '2026-11-10 12:58:00'
       UNION ALL SELECT (@da + 559), (@dm + 149), (@du + 67), NULL, '개인', '확정', 'RCPT-20261110-7ZRA9UBW', '2026-11-10 04:17:29', '2026-11-10 10:17:29'
       UNION ALL SELECT (@da + 560), (@dm + 149), (@du + 66), NULL, '개인', '확정', 'RCPT-20261110-PE4GN4FX', '2026-11-10 02:32:17', '2026-11-10 05:32:17'
       UNION ALL SELECT (@da + 561), (@dm + 151), (@du + 18), (@dc + 4), '동호회', '대기', 'RCPT-20261109-AAYQV7LM', '2026-11-09 23:51:23', NULL
       UNION ALL SELECT (@da + 562), (@dm + 151), (@du + 62), (@dc + 4), '동호회', '대기', 'RCPT-20261109-DQSN8Y7X', '2026-11-09 23:51:23', NULL
       UNION ALL SELECT (@da + 563), (@dm + 151), (@du + 46), (@dc + 4), '동호회', '대기', 'RCPT-20261109-YKSHS7MV', '2026-11-09 23:51:23', NULL
       UNION ALL SELECT (@da + 564), (@dm + 151), (@du + 96), (@dc + 4), '동호회', '대기', 'RCPT-20261109-34TXGU47', '2026-11-09 23:51:23', NULL
       UNION ALL SELECT (@da + 565), (@dm + 151), (@du + 63), NULL, '개인', '확정', 'RCPT-20261111-A4J8RZK9', '2026-11-11 10:46:52', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 566), (@dm + 151), (@du + 66), NULL, '개인', '확정', 'RCPT-20261111-2C4QLGNN', '2026-11-11 01:26:03', '2026-11-11 05:26:03'
       UNION ALL SELECT (@da + 567), (@dm + 153), (@du + 75), NULL, '개인', '확정', 'RCPT-20261109-2EG8JWS6', '2026-11-09 18:26:46', '2026-11-09 20:26:46'
       UNION ALL SELECT (@da + 568), (@dm + 153), (@du + 28), NULL, '개인', '확정', 'RCPT-20261110-HWBGQ932', '2026-11-10 23:01:55', '2026-11-11 04:01:55'
       UNION ALL SELECT (@da + 569), (@dm + 153), (@du + 59), NULL, '개인', '확정', 'RCPT-20261111-WZ4A5QFV', '2026-11-11 04:15:36', '2026-11-11 10:15:36'
       UNION ALL SELECT (@da + 570), (@dm + 153), (@du + 12), NULL, '개인', '확정', 'RCPT-20261111-GPUD33DX', '2026-11-11 10:36:14', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 571), (@dm + 153), (@du + 37), NULL, '개인', '확정', 'RCPT-20261111-RVKVRHZC', '2026-11-11 11:27:25', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 572), (@dm + 153), (@du + 60), NULL, '개인', '확정', 'RCPT-20261109-JJ72JMZM', '2026-11-09 09:02:20', '2026-11-09 14:02:20'
       UNION ALL SELECT (@da + 573), (@dm + 154), (@du + 49), (@dc + 3), '동호회', '확정', 'RCPT-20261110-ZTCE4LTA', '2026-11-10 23:27:10', '2026-11-11 02:27:10'
       UNION ALL SELECT (@da + 574), (@dm + 154), (@du + 45), (@dc + 3), '동호회', '확정', 'RCPT-20261110-95CXS8N3', '2026-11-10 23:27:10', '2026-11-11 02:27:10'
       UNION ALL SELECT (@da + 575), (@dm + 154), (@du + 41), (@dc + 3), '동호회', '확정', 'RCPT-20261110-9WCKMEGA', '2026-11-10 23:27:10', '2026-11-11 02:27:10'
       UNION ALL SELECT (@da + 576), (@dm + 154), (@du + 58), (@dc + 3), '동호회', '확정', 'RCPT-20261110-EEDZ53QD', '2026-11-10 23:27:10', '2026-11-11 02:27:10'
       UNION ALL SELECT (@da + 577), (@dm + 154), (@du + 34), (@dc + 3), '동호회', '확정', 'RCPT-20261110-YB8YPMTH', '2026-11-10 23:27:10', '2026-11-11 02:27:10'
       UNION ALL SELECT (@da + 578), (@dm + 154), (@du + 56), NULL, '개인', '확정', 'RCPT-20261111-ELPF4B8E', '2026-11-11 01:55:37', '2026-11-11 05:55:37'
       UNION ALL SELECT (@da + 579), (@dm + 154), (@du + 44), NULL, '개인', '확정', 'RCPT-20261111-L6MPEJED', '2026-11-11 06:30:14', '2026-11-11 10:30:14'
       UNION ALL SELECT (@da + 580), (@dm + 154), (@du + 85), NULL, '개인', '확정', 'RCPT-20261111-EUUZ7EYT', '2026-11-11 04:00:32', '2026-11-11 06:00:32'
       UNION ALL SELECT (@da + 581), (@dm + 154), (@du + 81), NULL, '개인', '확정', 'RCPT-20261109-8N7FPZHG', '2026-11-09 21:38:55', '2026-11-09 23:38:55'
       UNION ALL SELECT (@da + 582), (@dm + 154), (@du + 95), NULL, '개인', '확정', 'RCPT-20261109-ADR7S2CJ', '2026-11-09 16:29:25', '2026-11-09 22:29:25'
       UNION ALL SELECT (@da + 583), (@dm + 155), (@du + 51), (@dc + 1), '동호회', '대기', 'RCPT-20261111-QZABV7RQ', '2026-11-11 05:18:56', NULL
       UNION ALL SELECT (@da + 584), (@dm + 155), (@du + 74), (@dc + 1), '동호회', '대기', 'RCPT-20261111-S6XYLB25', '2026-11-11 05:18:56', NULL
       UNION ALL SELECT (@da + 585), (@dm + 155), (@du + 72), (@dc + 1), '동호회', '대기', 'RCPT-20261111-2Y4TUE7H', '2026-11-11 05:18:56', NULL
       UNION ALL SELECT (@da + 586), (@dm + 155), (@du + 61), NULL, '개인', '확정', 'RCPT-20261109-ZKWVMN5K', '2026-11-09 10:33:51', '2026-11-09 11:33:51'
       UNION ALL SELECT (@da + 587), (@dm + 155), (@du + 17), NULL, '개인', '확정', 'RCPT-20261110-YMZ3ZPGR', '2026-11-10 06:37:30', '2026-11-10 08:37:30'
       UNION ALL SELECT (@da + 588), (@dm + 155), (@du + 2), NULL, '개인', '확정', 'RCPT-20261111-3KSK2AFN', '2026-11-11 05:49:39', '2026-11-11 10:49:39'
       UNION ALL SELECT (@da + 589), (@dm + 155), (@du + 45), NULL, '개인', '확정', 'RCPT-20261109-W6S8V5W6', '2026-11-09 18:30:58', '2026-11-09 20:30:58'
       UNION ALL SELECT (@da + 590), (@dm + 156), (@du + 31), NULL, '개인', '확정', 'RCPT-20261110-3ADSFN35', '2026-11-10 00:18:53', '2026-11-10 06:18:53'
       UNION ALL SELECT (@da + 591), (@dm + 156), (@du + 35), NULL, '개인', '확정', 'RCPT-20261110-CNWBLGJJ', '2026-11-10 19:05:45', '2026-11-10 23:05:45'
       UNION ALL SELECT (@da + 592), (@dm + 156), (@du + 28), NULL, '개인', '대기', 'RCPT-20261110-ZDLT4MKP', '2026-11-10 14:14:00', NULL
       UNION ALL SELECT (@da + 593), (@dm + 158), (@du + 89), NULL, '개인', '확정', 'RCPT-20261110-9PQ2QZ35', '2026-11-10 14:52:46', '2026-11-10 20:52:46'
       UNION ALL SELECT (@da + 594), (@dm + 158), (@du + 8), NULL, '개인', '확정', 'RCPT-20261111-UJ5HK5NM', '2026-11-11 00:13:36', '2026-11-11 02:13:36'
       UNION ALL SELECT (@da + 595), (@dm + 158), (@du + 29), NULL, '개인', '확정', 'RCPT-20261110-RHBXR3JY', '2026-11-10 02:56:26', '2026-11-10 08:56:26'
       UNION ALL SELECT (@da + 596), (@dm + 158), (@du + 66), NULL, '개인', '확정', 'RCPT-20261109-3HMMWS25', '2026-11-09 12:11:23', '2026-11-09 18:11:23'
       UNION ALL SELECT (@da + 597), (@dm + 160), (@du + 8), (@dc + 5), '동호회', '대기', 'RCPT-20261110-EQADYE5D', '2026-11-10 02:33:49', NULL
       UNION ALL SELECT (@da + 598), (@dm + 160), (@du + 19), (@dc + 5), '동호회', '대기', 'RCPT-20261110-8D3DH4ZA', '2026-11-10 02:33:49', NULL
       UNION ALL SELECT (@da + 599), (@dm + 160), (@du + 60), (@dc + 5), '동호회', '대기', 'RCPT-20261110-BX9232PD', '2026-11-10 02:33:49', NULL
       UNION ALL SELECT (@da + 600), (@dm + 160), (@du + 14), (@dc + 5), '동호회', '대기', 'RCPT-20261110-DBJPSB2A', '2026-11-10 02:33:49', NULL
) v
 WHERE @demo_ready = 1;

INSERT INTO match_application (application_id, match_id, applicant_user_id, club_id, application_type, status, receipt_no, created_at, confirmed_at)
SELECT * FROM (
                 SELECT (@da + 601) AS application_id, (@dm + 160) AS match_id, (@du + 46) AS applicant_user_id, NULL AS club_id, '개인' AS application_type, '확정' AS status, 'RCPT-20261109-BXRT8KE5' AS receipt_no, '2026-11-09 19:08:59' AS created_at, '2026-11-10 01:08:59' AS confirmed_at
       UNION ALL SELECT (@da + 602), (@dm + 160), (@du + 40), NULL, '개인', '확정', 'RCPT-20261111-SDW8HGEF', '2026-11-11 09:28:39', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 603), (@dm + 162), (@du + 48), NULL, '개인', '확정', 'RCPT-20261111-T9VTJA8X', '2026-11-11 03:54:55', '2026-11-11 08:54:55'
       UNION ALL SELECT (@da + 604), (@dm + 162), (@du + 37), NULL, '개인', '확정', 'RCPT-20261109-ABDZUH8M', '2026-11-09 19:52:23', '2026-11-10 00:52:23'
       UNION ALL SELECT (@da + 605), (@dm + 162), (@du + 93), NULL, '개인', '확정', 'RCPT-20261110-UQ7WGQPH', '2026-11-10 23:15:28', '2026-11-11 03:15:28'
       UNION ALL SELECT (@da + 606), (@dm + 162), (@du + 83), NULL, '개인', '확정', 'RCPT-20261110-A6GRVJ2J', '2026-11-10 13:56:23', '2026-11-10 17:56:23'
       UNION ALL SELECT (@da + 607), (@dm + 162), (@du + 12), NULL, '개인', '확정', 'RCPT-20261110-CJ9LBX9Z', '2026-11-10 04:05:10', '2026-11-10 08:05:10'
       UNION ALL SELECT (@da + 608), (@dm + 162), (@du + 6), NULL, '개인', '확정', 'RCPT-20261110-7FWRKKVG', '2026-11-10 10:00:33', '2026-11-10 16:00:33'
       UNION ALL SELECT (@da + 609), (@dm + 163), (@du + 46), NULL, '개인', '확정', 'RCPT-20261110-DU8XEJPF', '2026-11-10 12:22:53', '2026-11-10 16:22:53'
       UNION ALL SELECT (@da + 610), (@dm + 163), (@du + 42), NULL, '개인', '확정', 'RCPT-20261111-FESFDHXD', '2026-11-11 10:17:06', '2026-11-11 12:00:00'
       UNION ALL SELECT (@da + 611), (@dm + 163), (@du + 91), NULL, '개인', '확정', 'RCPT-20261110-Z6JVUR36', '2026-11-10 03:03:23', '2026-11-10 08:03:23'
       UNION ALL SELECT (@da + 612), (@dm + 163), (@du + 95), NULL, '개인', '확정', 'RCPT-20261109-YWWJ2FNF', '2026-11-09 10:50:22', '2026-11-09 12:50:22'
       UNION ALL SELECT (@da + 613), (@dm + 163), (@du + 35), NULL, '개인', '확정', 'RCPT-20261109-NJKL6UCW', '2026-11-09 10:19:00', '2026-11-09 11:19:00'
       UNION ALL SELECT (@da + 614), (@dm + 163), (@du + 99), NULL, '개인', '확정', 'RCPT-20261109-J7MBJLME', '2026-11-09 19:38:37', '2026-11-09 22:38:37'
       UNION ALL SELECT (@da + 615), (@dm + 163), (@du + 65), NULL, '개인', '확정', 'RCPT-20261110-QXQYL42E', '2026-11-10 10:33:12', '2026-11-10 13:33:12'
       UNION ALL SELECT (@da + 616), (@dm + 163), (@du + 4), NULL, '개인', '확정', 'RCPT-20261109-2SPNW6D2', '2026-11-09 20:26:19', '2026-11-10 00:26:19'
       UNION ALL SELECT (@da + 617), (@dm + 163), (@du + 30), NULL, '개인', '확정', 'RCPT-20261110-EJLLS3W6', '2026-11-10 05:15:25', '2026-11-10 06:15:25'
       UNION ALL SELECT (@da + 618), (@dm + 163), (@du + 86), NULL, '개인', '확정', 'RCPT-20261110-3FD5LGGC', '2026-11-10 02:02:01', '2026-11-10 06:02:01'
       UNION ALL SELECT (@da + 619), (@dm + 165), (@du + 48), NULL, '개인', '확정', 'RCPT-20261110-2YCHHPDU', '2026-11-10 19:10:04', '2026-11-10 21:10:04'
       UNION ALL SELECT (@da + 620), (@dm + 165), (@du + 53), NULL, '개인', '확정', 'RCPT-20261110-CF9UJSUG', '2026-11-10 01:15:40', '2026-11-10 06:15:40'
       UNION ALL SELECT (@da + 621), (@dm + 165), (@du + 8), NULL, '개인', '확정', 'RCPT-20261109-658ZUHUW', '2026-11-09 11:18:40', '2026-11-09 16:18:40'
       UNION ALL SELECT (@da + 622), (@dm + 165), (@du + 33), NULL, '개인', '확정', 'RCPT-20261111-NZESN2NS', '2026-11-11 03:07:54', '2026-11-11 05:07:54'
       UNION ALL SELECT (@da + 623), (@dm + 167), (@du + 36), NULL, '개인', '확정', 'RCPT-20261109-G6E4J5SH', '2026-11-09 16:08:00', '2026-11-09 22:08:00'
       UNION ALL SELECT (@da + 624), (@dm + 167), (@du + 14), NULL, '개인', '확정', 'RCPT-20261110-KMXTYQTV', '2026-11-10 17:37:15', '2026-11-10 18:37:15'
       UNION ALL SELECT (@da + 625), (@dm + 167), (@du + 39), NULL, '개인', '대기', 'RCPT-20261109-8XR68KEZ', '2026-11-09 19:42:40', NULL
       UNION ALL SELECT (@da + 626), (@dm + 169), (@du + 3), NULL, '개인', '확정', 'RCPT-20261110-PXVKBNK6', '2026-11-10 13:29:19', '2026-11-10 14:29:19'
       UNION ALL SELECT (@da + 627), (@dm + 169), (@du + 28), NULL, '개인', '확정', 'RCPT-20261110-D6P943NR', '2026-11-10 07:06:48', '2026-11-10 10:06:48'
       UNION ALL SELECT (@da + 628), (@dm + 169), (@du + 19), NULL, '개인', '확정', 'RCPT-20261109-D7VZAMGH', '2026-11-09 16:04:46', '2026-11-09 21:04:46'
       UNION ALL SELECT (@da + 629), (@dm + 169), (@du + 27), NULL, '개인', '확정', 'RCPT-20261110-PZA7RVAG', '2026-11-10 13:05:49', '2026-11-10 14:05:49'
       UNION ALL SELECT (@da + 630), (@dm + 169), (@du + 29), NULL, '개인', '확정', 'RCPT-20261109-EKQ3XSJE', '2026-11-09 22:35:44', '2026-11-10 00:35:44'
       UNION ALL SELECT (@da + 631), (@dm + 169), (@du + 7), NULL, '개인', '확정', 'RCPT-20261110-3CCMLYDB', '2026-11-10 14:43:15', '2026-11-10 15:43:15'
       UNION ALL SELECT (@da + 632), (@dm + 170), (@du + 67), NULL, '개인', '확정', 'RCPT-20261110-VXHTQ6Q4', '2026-11-10 01:57:33', '2026-11-10 03:57:33'
       UNION ALL SELECT (@da + 633), (@dm + 170), (@du + 79), NULL, '개인', '확정', 'RCPT-20261109-GQXZA22U', '2026-11-09 15:50:31', '2026-11-09 17:50:31'
       UNION ALL SELECT (@da + 634), (@dm + 170), (@du + 75), NULL, '개인', '확정', 'RCPT-20261111-VDRTVALT', '2026-11-11 00:45:35', '2026-11-11 04:45:35'
       UNION ALL SELECT (@da + 635), (@dm + 170), (@du + 15), NULL, '개인', '확정', 'RCPT-20261109-K48CCKNC', '2026-11-09 18:04:14', '2026-11-09 19:04:14'
       UNION ALL SELECT (@da + 636), (@dm + 170), (@du + 9), NULL, '개인', '확정', 'RCPT-20261109-5LHFS64Z', '2026-11-09 12:41:25', '2026-11-09 15:41:25'
       UNION ALL SELECT (@da + 637), (@dm + 170), (@du + 58), NULL, '개인', '대기', 'RCPT-20261111-8KDY8SVH', '2026-11-11 09:58:01', NULL
       UNION ALL SELECT (@da + 638), (@dm + 170), (@du + 71), NULL, '개인', '대기', 'RCPT-20261111-HV63Z88U', '2026-11-11 03:28:26', NULL
       UNION ALL SELECT (@da + 639), (@dm + 171), (@du + 6), NULL, '개인', '확정', 'RCPT-20261110-E9TE9CWT', '2026-11-10 05:30:47', '2026-11-10 08:30:47'
       UNION ALL SELECT (@da + 640), (@dm + 171), (@du + 97), NULL, '개인', '확정', 'RCPT-20261110-89N4KUAL', '2026-11-10 17:00:38', '2026-11-10 19:00:38'
       UNION ALL SELECT (@da + 641), (@dm + 171), (@du + 69), NULL, '개인', '확정', 'RCPT-20261110-F93BMSYF', '2026-11-10 14:28:39', '2026-11-10 18:28:39'
       UNION ALL SELECT (@da + 642), (@dm + 172), (@du + 31), NULL, '개인', '확정', 'RCPT-20261029-FECQGAD7', '2026-10-29 10:00:00', '2026-10-29 14:00:00'
       UNION ALL SELECT (@da + 643), (@dm + 172), (@du + 74), NULL, '개인', '확정', 'RCPT-20261028-GN5889P7', '2026-10-28 10:00:00', '2026-10-28 13:00:00'
       UNION ALL SELECT (@da + 644), (@dm + 172), (@du + 49), NULL, '개인', '확정', 'RCPT-20261030-J4XSPH5N', '2026-10-30 06:00:00', '2026-10-30 12:00:00'
       UNION ALL SELECT (@da + 645), (@dm + 172), (@du + 81), NULL, '개인', '확정', 'RCPT-20261029-TPD44CGR', '2026-10-29 01:00:00', '2026-10-29 04:00:00'
       UNION ALL SELECT (@da + 646), (@dm + 172), (@du + 65), NULL, '개인', '확정', 'RCPT-20261029-EMZQQQTS', '2026-10-29 01:00:00', '2026-10-29 06:00:00'
       UNION ALL SELECT (@da + 647), (@dm + 172), (@du + 58), NULL, '개인', '확정', 'RCPT-20261027-KVP5WJ5K', '2026-10-27 01:00:00', '2026-10-27 04:00:00'
       UNION ALL SELECT (@da + 648), (@dm + 172), (@du + 83), NULL, '개인', '확정', 'RCPT-20261030-3G9HQMTG', '2026-10-30 07:00:00', '2026-10-30 13:00:00'
       UNION ALL SELECT (@da + 649), (@dm + 172), (@du + 42), NULL, '개인', '확정', 'RCPT-20261030-RC4CJNAQ', '2026-10-30 09:00:00', '2026-10-30 12:00:00'
       UNION ALL SELECT (@da + 650), (@dm + 172), (@du + 80), NULL, '개인', '확정', 'RCPT-20261030-PGZ3HJJX', '2026-10-30 02:00:00', '2026-10-30 08:00:00'
       UNION ALL SELECT (@da + 651), (@dm + 172), (@du + 41), NULL, '개인', '확정', 'RCPT-20261027-BQ5NVFX9', '2026-10-27 05:00:00', '2026-10-27 08:00:00'
       UNION ALL SELECT (@da + 652), (@dm + 173), (@du + 26), NULL, '개인', '확정', 'RCPT-20261030-QUKZCE9C', '2026-10-30 10:00:00', '2026-10-30 13:00:00'
       UNION ALL SELECT (@da + 653), (@dm + 173), (@du + 96), NULL, '개인', '확정', 'RCPT-20261028-NHA4EAEE', '2026-10-28 11:00:00', '2026-10-28 13:00:00'
       UNION ALL SELECT (@da + 654), (@dm + 173), (@du + 41), NULL, '개인', '확정', 'RCPT-20261029-ZNE3H32B', '2026-10-29 08:00:00', '2026-10-29 10:00:00'
       UNION ALL SELECT (@da + 655), (@dm + 173), (@du + 48), NULL, '개인', '확정', 'RCPT-20261027-VYMF5QXE', '2026-10-27 11:00:00', '2026-10-27 13:00:00'
       UNION ALL SELECT (@da + 656), (@dm + 174), (@du + 24), NULL, '개인', '확정', 'RCPT-20261031-EUKYHHB3', '2026-10-31 16:00:00', '2026-10-31 21:00:00'
       UNION ALL SELECT (@da + 657), (@dm + 174), (@du + 67), NULL, '개인', '확정', 'RCPT-20261030-3NY7H4KV', '2026-10-30 14:00:00', '2026-10-30 20:00:00'
       UNION ALL SELECT (@da + 658), (@dm + 174), (@du + 1), NULL, '개인', '확정', 'RCPT-20261028-W9WMWFT4', '2026-10-28 10:00:00', '2026-10-28 15:00:00'
       UNION ALL SELECT (@da + 659), (@dm + 174), (@du + 10), NULL, '개인', '확정', 'RCPT-20261030-ELLTYH6G', '2026-10-30 09:00:00', '2026-10-30 14:00:00'
       UNION ALL SELECT (@da + 660), (@dm + 175), (@du + 99), NULL, '개인', '확정', 'RCPT-20261031-26URGP46', '2026-10-31 11:00:00', '2026-10-31 16:00:00'
       UNION ALL SELECT (@da + 661), (@dm + 175), (@du + 72), NULL, '개인', '확정', 'RCPT-20261028-YZKPHUV4', '2026-10-28 09:00:00', '2026-10-28 10:00:00'
       UNION ALL SELECT (@da + 662), (@dm + 175), (@du + 15), NULL, '개인', '확정', 'RCPT-20261028-DZ6K62C6', '2026-10-28 18:00:00', '2026-10-28 22:00:00'
       UNION ALL SELECT (@da + 663), (@dm + 175), (@du + 96), NULL, '개인', '확정', 'RCPT-20261029-H22X99W2', '2026-10-29 14:00:00', '2026-10-29 15:00:00'
       UNION ALL SELECT (@da + 664), (@dm + 175), (@du + 30), NULL, '개인', '확정', 'RCPT-20261029-PV3UJ2BN', '2026-10-29 14:00:00', '2026-10-29 16:00:00'
       UNION ALL SELECT (@da + 665), (@dm + 175), (@du + 26), NULL, '개인', '확정', 'RCPT-20261030-GTHV667T', '2026-10-30 18:00:00', '2026-10-30 20:00:00'
       UNION ALL SELECT (@da + 666), (@dm + 176), (@du + 75), NULL, '개인', '확정', 'RCPT-20261029-LJ5KXRWF', '2026-10-29 10:00:00', '2026-10-29 12:00:00'
       UNION ALL SELECT (@da + 667), (@dm + 176), (@du + 40), NULL, '개인', '확정', 'RCPT-20261030-TJFVDQJS', '2026-10-30 11:00:00', '2026-10-30 14:00:00'
       UNION ALL SELECT (@da + 668), (@dm + 176), (@du + 86), NULL, '개인', '확정', 'RCPT-20261101-9CQ8S287', '2026-11-01 13:00:00', '2026-11-01 17:00:00'
       UNION ALL SELECT (@da + 669), (@dm + 176), (@du + 15), NULL, '개인', '확정', 'RCPT-20261030-UQS7MD28', '2026-10-30 12:00:00', '2026-10-30 13:00:00'
       UNION ALL SELECT (@da + 670), (@dm + 176), (@du + 22), NULL, '개인', '확정', 'RCPT-20261031-C3YD9Z9Q', '2026-10-31 08:00:00', '2026-10-31 09:00:00'
       UNION ALL SELECT (@da + 671), (@dm + 176), (@du + 47), NULL, '개인', '확정', 'RCPT-20261101-XDF53YR5', '2026-11-01 09:00:00', '2026-11-01 10:00:00'
       UNION ALL SELECT (@da + 672), (@dm + 177), (@du + 95), NULL, '개인', '확정', 'RCPT-20261101-2MQ6VXM9', '2026-11-01 17:00:00', '2026-11-01 19:00:00'
       UNION ALL SELECT (@da + 673), (@dm + 177), (@du + 34), NULL, '개인', '확정', 'RCPT-20261101-VF6A6WQR', '2026-11-01 18:00:00', '2026-11-02 00:00:00'
       UNION ALL SELECT (@da + 674), (@dm + 177), (@du + 58), NULL, '개인', '확정', 'RCPT-20261101-CAFQF45K', '2026-11-01 15:00:00', '2026-11-01 19:00:00'
       UNION ALL SELECT (@da + 675), (@dm + 177), (@du + 78), NULL, '개인', '확정', 'RCPT-20261030-JL8DLBPK', '2026-10-30 14:00:00', '2026-10-30 18:00:00'
       UNION ALL SELECT (@da + 676), (@dm + 177), (@du + 97), NULL, '개인', '확정', 'RCPT-20261029-97K5X96J', '2026-10-29 17:00:00', '2026-10-29 23:00:00'
       UNION ALL SELECT (@da + 677), (@dm + 177), (@du + 61), NULL, '개인', '확정', 'RCPT-20261029-HP38FAGN', '2026-10-29 08:00:00', '2026-10-29 10:00:00'
       UNION ALL SELECT (@da + 678), (@dm + 177), (@du + 51), NULL, '개인', '확정', 'RCPT-20261029-ECF5DE4Z', '2026-10-29 08:00:00', '2026-10-29 09:00:00'
       UNION ALL SELECT (@da + 679), (@dm + 177), (@du + 26), NULL, '개인', '확정', 'RCPT-20261031-C76LQZVL', '2026-10-31 12:00:00', '2026-10-31 16:00:00'
       UNION ALL SELECT (@da + 680), (@dm + 177), (@du + 54), NULL, '개인', '확정', 'RCPT-20261029-F7MEKYDF', '2026-10-29 18:00:00', '2026-10-29 23:00:00'
       UNION ALL SELECT (@da + 681), (@dm + 177), (@du + 79), NULL, '개인', '확정', 'RCPT-20261101-G9276MS2', '2026-11-01 08:00:00', '2026-11-01 14:00:00'
       UNION ALL SELECT (@da + 682), (@dm + 178), (@du + 56), NULL, '개인', '확정', 'RCPT-20261030-YZRE88A2', '2026-10-30 09:00:00', '2026-10-30 10:00:00'
       UNION ALL SELECT (@da + 683), (@dm + 178), (@du + 16), NULL, '개인', '확정', 'RCPT-20261030-7EQWEDES', '2026-10-30 08:00:00', '2026-10-30 14:00:00'
       UNION ALL SELECT (@da + 684), (@dm + 178), (@du + 30), NULL, '개인', '확정', 'RCPT-20261102-N4KLMB24', '2026-11-02 17:00:00', '2026-11-02 18:00:00'
       UNION ALL SELECT (@da + 685), (@dm + 178), (@du + 77), NULL, '개인', '확정', 'RCPT-20261102-YTRS6C77', '2026-11-02 14:00:00', '2026-11-02 19:00:00'
       UNION ALL SELECT (@da + 686), (@dm + 179), (@du + 15), NULL, '개인', '확정', 'RCPT-20261031-ERGLW3H2', '2026-10-31 17:00:00', '2026-10-31 22:00:00'
       UNION ALL SELECT (@da + 687), (@dm + 179), (@du + 59), NULL, '개인', '확정', 'RCPT-20261031-6W8HBU5S', '2026-10-31 14:00:00', '2026-10-31 17:00:00'
       UNION ALL SELECT (@da + 688), (@dm + 179), (@du + 39), NULL, '개인', '확정', 'RCPT-20261102-FX3TZDY9', '2026-11-02 09:00:00', '2026-11-02 12:00:00'
       UNION ALL SELECT (@da + 689), (@dm + 179), (@du + 88), NULL, '개인', '확정', 'RCPT-20261030-NNCNUPQ2', '2026-10-30 09:00:00', '2026-10-30 10:00:00'
       UNION ALL SELECT (@da + 690), (@dm + 180), (@du + 7), NULL, '개인', '확정', 'RCPT-20261102-TUPZ8UCM', '2026-11-02 13:00:00', '2026-11-02 17:00:00'
       UNION ALL SELECT (@da + 691), (@dm + 180), (@du + 6), NULL, '개인', '확정', 'RCPT-20261102-HNMSCXXQ', '2026-11-02 10:00:00', '2026-11-02 14:00:00'
       UNION ALL SELECT (@da + 692), (@dm + 180), (@du + 96), NULL, '개인', '확정', 'RCPT-20261101-H7XMUNVZ', '2026-11-01 08:00:00', '2026-11-01 10:00:00'
       UNION ALL SELECT (@da + 693), (@dm + 180), (@du + 30), NULL, '개인', '확정', 'RCPT-20261031-F5RSDA83', '2026-10-31 12:00:00', '2026-10-31 14:00:00'
       UNION ALL SELECT (@da + 694), (@dm + 180), (@du + 63), NULL, '개인', '확정', 'RCPT-20261103-262Q5KH9', '2026-11-03 13:00:00', '2026-11-03 14:00:00'
       UNION ALL SELECT (@da + 695), (@dm + 180), (@du + 60), NULL, '개인', '확정', 'RCPT-20261101-3LSHCAP2', '2026-11-01 15:00:00', '2026-11-01 21:00:00'
       UNION ALL SELECT (@da + 696), (@dm + 181), (@du + 61), NULL, '개인', '확정', 'RCPT-20261031-YNMBST2N', '2026-10-31 18:00:00', '2026-10-31 22:00:00'
       UNION ALL SELECT (@da + 697), (@dm + 181), (@du + 42), NULL, '개인', '확정', 'RCPT-20261103-DBSCHPN2', '2026-11-03 17:00:00', '2026-11-03 22:00:00'
       UNION ALL SELECT (@da + 698), (@dm + 181), (@du + 89), NULL, '개인', '확정', 'RCPT-20261101-AFG7L7RF', '2026-11-01 08:00:00', '2026-11-01 09:00:00'
       UNION ALL SELECT (@da + 699), (@dm + 181), (@du + 23), NULL, '개인', '확정', 'RCPT-20261101-DBS55GAP', '2026-11-01 16:00:00', '2026-11-01 18:00:00'
       UNION ALL SELECT (@da + 700), (@dm + 181), (@du + 12), NULL, '개인', '확정', 'RCPT-20261101-6AVZSMRW', '2026-11-01 13:00:00', '2026-11-01 19:00:00'
       UNION ALL SELECT (@da + 701), (@dm + 181), (@du + 67), NULL, '개인', '확정', 'RCPT-20261102-E9GD3X8W', '2026-11-02 14:00:00', '2026-11-02 19:00:00'
       UNION ALL SELECT (@da + 702), (@dm + 182), (@du + 85), NULL, '개인', '확정', 'RCPT-20261104-3KS7CJC5', '2026-11-04 18:00:00', '2026-11-04 19:00:00'
       UNION ALL SELECT (@da + 703), (@dm + 182), (@du + 38), NULL, '개인', '확정', 'RCPT-20261103-DAQL3NAB', '2026-11-03 14:00:00', '2026-11-03 19:00:00'
       UNION ALL SELECT (@da + 704), (@dm + 182), (@du + 99), NULL, '개인', '확정', 'RCPT-20261101-PRDDA3PJ', '2026-11-01 13:00:00', '2026-11-01 14:00:00'
       UNION ALL SELECT (@da + 705), (@dm + 182), (@du + 11), NULL, '개인', '확정', 'RCPT-20261104-P65R3BXB', '2026-11-04 17:00:00', '2026-11-04 22:00:00'
       UNION ALL SELECT (@da + 706), (@dm + 182), (@du + 91), NULL, '개인', '확정', 'RCPT-20261103-8BBKDJH6', '2026-11-03 17:00:00', '2026-11-03 21:00:00'
       UNION ALL SELECT (@da + 707), (@dm + 182), (@du + 90), NULL, '개인', '확정', 'RCPT-20261104-QDE524V9', '2026-11-04 17:00:00', '2026-11-04 23:00:00'
       UNION ALL SELECT (@da + 708), (@dm + 182), (@du + 77), NULL, '개인', '확정', 'RCPT-20261101-N5RQEU6W', '2026-11-01 16:00:00', '2026-11-01 18:00:00'
       UNION ALL SELECT (@da + 709), (@dm + 182), (@du + 78), NULL, '개인', '확정', 'RCPT-20261102-9ZWJYP55', '2026-11-02 18:00:00', '2026-11-02 21:00:00'
       UNION ALL SELECT (@da + 710), (@dm + 182), (@du + 82), NULL, '개인', '확정', 'RCPT-20261104-9S9UZ7KF', '2026-11-04 14:00:00', '2026-11-04 20:00:00'
       UNION ALL SELECT (@da + 711), (@dm + 182), (@du + 35), NULL, '개인', '확정', 'RCPT-20261102-M8M4CEMT', '2026-11-02 16:00:00', '2026-11-02 20:00:00'
       UNION ALL SELECT (@da + 712), (@dm + 183), (@du + 31), NULL, '개인', '확정', 'RCPT-20261102-Y9469DM8', '2026-11-02 14:00:00', '2026-11-02 19:00:00'
       UNION ALL SELECT (@da + 713), (@dm + 183), (@du + 68), NULL, '개인', '확정', 'RCPT-20261104-3D7FG389', '2026-11-04 10:00:00', '2026-11-04 13:00:00'
       UNION ALL SELECT (@da + 714), (@dm + 183), (@du + 26), NULL, '개인', '확정', 'RCPT-20261102-9BMDETP7', '2026-11-02 12:00:00', '2026-11-02 14:00:00'
       UNION ALL SELECT (@da + 715), (@dm + 183), (@du + 28), NULL, '개인', '확정', 'RCPT-20261103-8YNHECAT', '2026-11-03 16:00:00', '2026-11-03 17:00:00'
       UNION ALL SELECT (@da + 716), (@dm + 184), (@du + 100), NULL, '개인', '확정', 'RCPT-20261104-CK2N2WBS', '2026-11-04 01:00:00', '2026-11-04 04:00:00'
       UNION ALL SELECT (@da + 717), (@dm + 184), (@du + 2), NULL, '개인', '확정', 'RCPT-20261105-X2U5S5NW', '2026-11-05 06:00:00', '2026-11-05 10:00:00'
       UNION ALL SELECT (@da + 718), (@dm + 184), (@du + 51), NULL, '개인', '확정', 'RCPT-20261103-E4CDRGQN', '2026-11-03 01:00:00', '2026-11-03 02:00:00'
       UNION ALL SELECT (@da + 719), (@dm + 184), (@du + 10), NULL, '개인', '확정', 'RCPT-20261103-2HLE86LA', '2026-11-03 10:00:00', '2026-11-03 16:00:00'
       UNION ALL SELECT (@da + 720), (@dm + 185), (@du + 39), NULL, '개인', '확정', 'RCPT-20261102-BFNDAUCS', '2026-11-02 10:00:00', '2026-11-02 11:00:00'
       UNION ALL SELECT (@da + 721), (@dm + 185), (@du + 50), NULL, '개인', '확정', 'RCPT-20261102-TPSY7CXW', '2026-11-02 09:00:00', '2026-11-02 10:00:00'
       UNION ALL SELECT (@da + 722), (@dm + 185), (@du + 76), NULL, '개인', '확정', 'RCPT-20261103-GRBKH55T', '2026-11-03 08:00:00', '2026-11-03 09:00:00'
       UNION ALL SELECT (@da + 723), (@dm + 185), (@du + 26), NULL, '개인', '확정', 'RCPT-20261104-5C9F5HBB', '2026-11-04 08:00:00', '2026-11-04 10:00:00'
       UNION ALL SELECT (@da + 724), (@dm + 185), (@du + 67), NULL, '개인', '확정', 'RCPT-20261104-CGENNBWP', '2026-11-04 13:00:00', '2026-11-04 16:00:00'
       UNION ALL SELECT (@da + 725), (@dm + 185), (@du + 14), NULL, '개인', '확정', 'RCPT-20261104-8WABJZ5K', '2026-11-04 10:00:00', '2026-11-04 15:00:00'
       UNION ALL SELECT (@da + 726), (@dm + 186), (@du + 81), NULL, '개인', '확정', 'RCPT-20261105-ETBQ3H96', '2026-11-05 06:00:00', '2026-11-05 07:00:00'
       UNION ALL SELECT (@da + 727), (@dm + 186), (@du + 44), NULL, '개인', '확정', 'RCPT-20261104-9SP8GRQ7', '2026-11-04 07:00:00', '2026-11-04 12:00:00'
       UNION ALL SELECT (@da + 728), (@dm + 186), (@du + 89), NULL, '개인', '확정', 'RCPT-20261106-S43B45TA', '2026-11-06 00:00:00', '2026-11-06 06:00:00'
       UNION ALL SELECT (@da + 729), (@dm + 186), (@du + 14), NULL, '개인', '확정', 'RCPT-20261103-DX2RYF8M', '2026-11-03 09:00:00', '2026-11-03 10:00:00'
       UNION ALL SELECT (@da + 730), (@dm + 186), (@du + 59), NULL, '개인', '확정', 'RCPT-20261105-XBQS4Z4A', '2026-11-05 05:00:00', '2026-11-05 06:00:00'
       UNION ALL SELECT (@da + 731), (@dm + 186), (@du + 11), NULL, '개인', '확정', 'RCPT-20261105-WBBLEVAD', '2026-11-05 08:00:00', '2026-11-05 11:00:00'
       UNION ALL SELECT (@da + 732), (@dm + 187), (@du + 49), NULL, '개인', '확정', 'RCPT-20261104-FWPXLGMF', '2026-11-04 12:00:00', '2026-11-04 16:00:00'
       UNION ALL SELECT (@da + 733), (@dm + 187), (@du + 69), NULL, '개인', '확정', 'RCPT-20261104-9Y9GSWNB', '2026-11-04 05:00:00', '2026-11-04 11:00:00'
       UNION ALL SELECT (@da + 734), (@dm + 187), (@du + 46), NULL, '개인', '확정', 'RCPT-20261103-HWMQ8M85', '2026-11-03 07:00:00', '2026-11-03 10:00:00'
       UNION ALL SELECT (@da + 735), (@dm + 187), (@du + 54), NULL, '개인', '확정', 'RCPT-20261103-EVN2GYVU', '2026-11-03 05:00:00', '2026-11-03 11:00:00'
       UNION ALL SELECT (@da + 736), (@dm + 187), (@du + 19), NULL, '개인', '확정', 'RCPT-20261104-82QXUAAW', '2026-11-04 14:00:00', '2026-11-04 19:00:00'
       UNION ALL SELECT (@da + 737), (@dm + 187), (@du + 91), NULL, '개인', '확정', 'RCPT-20261104-X7MUPHKV', '2026-11-04 09:00:00', '2026-11-04 12:00:00'
       UNION ALL SELECT (@da + 738), (@dm + 187), (@du + 44), NULL, '개인', '확정', 'RCPT-20261105-WSTTXDEY', '2026-11-05 13:00:00', '2026-11-05 19:00:00'
       UNION ALL SELECT (@da + 739), (@dm + 187), (@du + 100), NULL, '개인', '확정', 'RCPT-20261105-EFHWPB5T', '2026-11-05 13:00:00', '2026-11-05 17:00:00'
       UNION ALL SELECT (@da + 740), (@dm + 187), (@du + 72), NULL, '개인', '확정', 'RCPT-20261104-6BHS5XQY', '2026-11-04 05:00:00', '2026-11-04 10:00:00'
       UNION ALL SELECT (@da + 741), (@dm + 187), (@du + 81), NULL, '개인', '확정', 'RCPT-20261105-4N7DLCF4', '2026-11-05 08:00:00', '2026-11-05 11:00:00'
       UNION ALL SELECT (@da + 742), (@dm + 188), (@du + 64), NULL, '개인', '확정', 'RCPT-20261107-DTECCRL7', '2026-11-07 16:00:00', '2026-11-07 17:00:00'
       UNION ALL SELECT (@da + 743), (@dm + 188), (@du + 99), NULL, '개인', '확정', 'RCPT-20261106-GYUWVTAZ', '2026-11-06 17:00:00', '2026-11-06 18:00:00'
       UNION ALL SELECT (@da + 744), (@dm + 188), (@du + 93), NULL, '개인', '확정', 'RCPT-20261105-LFP88MFE', '2026-11-05 15:00:00', '2026-11-05 17:00:00'
       UNION ALL SELECT (@da + 745), (@dm + 188), (@du + 48), NULL, '개인', '확정', 'RCPT-20261106-G2HJMV6Q', '2026-11-06 17:00:00', '2026-11-06 19:00:00'
       UNION ALL SELECT (@da + 746), (@dm + 189), (@du + 36), NULL, '개인', '확정', 'RCPT-20261105-R948JX4F', '2026-11-05 08:00:00', '2026-11-05 13:00:00'
       UNION ALL SELECT (@da + 747), (@dm + 189), (@du + 67), NULL, '개인', '확정', 'RCPT-20261106-PJHWEN93', '2026-11-06 15:00:00', '2026-11-06 17:00:00'
       UNION ALL SELECT (@da + 748), (@dm + 189), (@du + 43), NULL, '개인', '확정', 'RCPT-20261104-HSMTDUPR', '2026-11-04 15:00:00', '2026-11-04 18:00:00'
       UNION ALL SELECT (@da + 749), (@dm + 189), (@du + 8), NULL, '개인', '확정', 'RCPT-20261105-UPTLQBDT', '2026-11-05 16:00:00', '2026-11-05 17:00:00'
) v
 WHERE @demo_ready = 1;

UPDATE match_application SET group_application_id = (@da + 67) WHERE @demo_ready = 1 AND application_id IN ((@da + 67), (@da + 68), (@da + 69), (@da + 70), (@da + 71));
UPDATE match_application SET group_application_id = (@da + 77) WHERE @demo_ready = 1 AND application_id IN ((@da + 77), (@da + 78), (@da + 79));
UPDATE match_application SET group_application_id = (@da + 91) WHERE @demo_ready = 1 AND application_id IN ((@da + 91), (@da + 92), (@da + 93), (@da + 94));
UPDATE match_application SET group_application_id = (@da + 103) WHERE @demo_ready = 1 AND application_id IN ((@da + 103), (@da + 104), (@da + 105), (@da + 106), (@da + 107));
UPDATE match_application SET group_application_id = (@da + 113) WHERE @demo_ready = 1 AND application_id IN ((@da + 113), (@da + 114), (@da + 115));
UPDATE match_application SET group_application_id = (@da + 300) WHERE @demo_ready = 1 AND application_id IN ((@da + 300), (@da + 301), (@da + 302), (@da + 303), (@da + 304));
UPDATE match_application SET group_application_id = (@da + 310) WHERE @demo_ready = 1 AND application_id IN ((@da + 310), (@da + 311), (@da + 312));
UPDATE match_application SET group_application_id = (@da + 324) WHERE @demo_ready = 1 AND application_id IN ((@da + 324), (@da + 325), (@da + 326), (@da + 327));
UPDATE match_application SET group_application_id = (@da + 336) WHERE @demo_ready = 1 AND application_id IN ((@da + 336), (@da + 337), (@da + 338), (@da + 339), (@da + 340));
UPDATE match_application SET group_application_id = (@da + 346) WHERE @demo_ready = 1 AND application_id IN ((@da + 346), (@da + 347), (@da + 348));
UPDATE match_application SET group_application_id = (@da + 360) WHERE @demo_ready = 1 AND application_id IN ((@da + 360), (@da + 361), (@da + 362), (@da + 363));
UPDATE match_application SET group_application_id = (@da + 537) WHERE @demo_ready = 1 AND application_id IN ((@da + 537), (@da + 538), (@da + 539), (@da + 540), (@da + 541));
UPDATE match_application SET group_application_id = (@da + 547) WHERE @demo_ready = 1 AND application_id IN ((@da + 547), (@da + 548), (@da + 549));
UPDATE match_application SET group_application_id = (@da + 561) WHERE @demo_ready = 1 AND application_id IN ((@da + 561), (@da + 562), (@da + 563), (@da + 564));
UPDATE match_application SET group_application_id = (@da + 573) WHERE @demo_ready = 1 AND application_id IN ((@da + 573), (@da + 574), (@da + 575), (@da + 576), (@da + 577));
UPDATE match_application SET group_application_id = (@da + 583) WHERE @demo_ready = 1 AND application_id IN ((@da + 583), (@da + 584), (@da + 585));
UPDATE match_application SET group_application_id = (@da + 597) WHERE @demo_ready = 1 AND application_id IN ((@da + 597), (@da + 598), (@da + 599), (@da + 600));

-- 5. 확정 참가자
INSERT INTO match_participant (participant_id, application_id, match_id, user_id, participation_status, match_side, entry_mmr, joined_at)
SELECT * FROM (
                 SELECT (@dp + 1) AS participant_id, (@da + 1) AS application_id, (@dm + 1) AS match_id, (@du + 68) AS user_id, '확정' AS participation_status, NULL AS match_side, 956 AS entry_mmr, '2026-11-10 06:35:02' AS joined_at
       UNION ALL SELECT (@dp + 2), (@da + 2), (@dm + 1), (@du + 31), '확정', NULL, 920, '2026-11-09 21:59:36'
       UNION ALL SELECT (@dp + 3), (@da + 3), (@dm + 1), (@du + 81), '확정', NULL, 957, '2026-11-11 03:04:21'
       UNION ALL SELECT (@dp + 4), (@da + 4), (@dm + 1), (@du + 97), '확정', NULL, 996, '2026-11-10 06:35:57'
       UNION ALL SELECT (@dp + 5), (@da + 5), (@dm + 1), (@du + 91), '확정', NULL, 1059, '2026-11-10 01:02:09'
       UNION ALL SELECT (@dp + 6), (@da + 6), (@dm + 1), (@du + 69), '확정', NULL, 1010, '2026-11-10 17:54:36'
       UNION ALL SELECT (@dp + 7), (@da + 7), (@dm + 1), (@du + 30), '확정', NULL, 984, '2026-11-09 15:08:48'
       UNION ALL SELECT (@dp + 8), (@da + 8), (@dm + 1), (@du + 1), '확정', NULL, 1328, '2026-11-11 02:31:52'
       UNION ALL SELECT (@dp + 9), (@da + 9), (@dm + 1), (@du + 78), '확정', NULL, 860, '2026-11-11 09:26:08'
       UNION ALL SELECT (@dp + 10), (@da + 10), (@dm + 1), (@du + 74), '확정', NULL, 985, '2026-11-09 22:15:09'
       UNION ALL SELECT (@dp + 11), (@da + 11), (@dm + 3), (@du + 66), '확정', NULL, 1053, '2026-11-11 10:16:11'
       UNION ALL SELECT (@dp + 12), (@da + 12), (@dm + 3), (@du + 82), '확정', NULL, 1047, '2026-11-10 00:51:41'
       UNION ALL SELECT (@dp + 13), (@da + 13), (@dm + 3), (@du + 50), '확정', NULL, 1188, '2026-11-11 00:18:57'
       UNION ALL SELECT (@dp + 14), (@da + 14), (@dm + 3), (@du + 19), '확정', NULL, 1071, '2026-11-11 03:26:27'
       UNION ALL SELECT (@dp + 15), (@da + 15), (@dm + 5), (@du + 85), '확정', NULL, 1282, '2026-11-11 04:12:30'
       UNION ALL SELECT (@dp + 16), (@da + 16), (@dm + 5), (@du + 25), '확정', NULL, 1225, '2026-11-10 23:01:21'
       UNION ALL SELECT (@dp + 17), (@da + 18), (@dm + 7), (@du + 6), '확정', NULL, 964, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 18), (@da + 19), (@dm + 7), (@du + 73), '확정', NULL, 1045, '2026-11-10 04:22:30'
       UNION ALL SELECT (@dp + 19), (@da + 20), (@dm + 7), (@du + 71), '확정', NULL, 973, '2026-11-11 03:22:45'
       UNION ALL SELECT (@dp + 20), (@da + 21), (@dm + 7), (@du + 14), '확정', NULL, 1063, '2026-11-09 13:53:27'
       UNION ALL SELECT (@dp + 21), (@da + 22), (@dm + 7), (@du + 79), '확정', NULL, 940, '2026-11-10 04:02:59'
       UNION ALL SELECT (@dp + 22), (@da + 23), (@dm + 7), (@du + 96), '확정', NULL, 935, '2026-11-10 15:37:58'
       UNION ALL SELECT (@dp + 23), (@da + 24), (@dm + 8), (@du + 29), '확정', NULL, 1142, '2026-11-10 22:57:31'
       UNION ALL SELECT (@dp + 24), (@da + 25), (@dm + 8), (@du + 39), '확정', NULL, 983, '2026-11-10 12:59:52'
       UNION ALL SELECT (@dp + 25), (@da + 26), (@dm + 8), (@du + 67), '확정', NULL, 893, '2026-11-09 23:54:25'
       UNION ALL SELECT (@dp + 26), (@da + 27), (@dm + 8), (@du + 84), '확정', NULL, 899, '2026-11-11 10:42:57'
       UNION ALL SELECT (@dp + 27), (@da + 28), (@dm + 8), (@du + 40), '확정', NULL, 1002, '2026-11-10 05:55:13'
       UNION ALL SELECT (@dp + 28), (@da + 31), (@dm + 9), (@du + 23), '확정', NULL, 849, '2026-11-10 16:16:50'
       UNION ALL SELECT (@dp + 29), (@da + 32), (@dm + 9), (@du + 27), '확정', NULL, 1092, '2026-11-09 15:23:51'
       UNION ALL SELECT (@dp + 30), (@da + 33), (@dm + 9), (@du + 75), '확정', NULL, 798, '2026-11-10 07:35:38'
       UNION ALL SELECT (@dp + 31), (@da + 34), (@dm + 10), (@du + 34), '확정', NULL, 969, '2026-11-10 17:03:55'
       UNION ALL SELECT (@dp + 32), (@da + 35), (@dm + 10), (@du + 51), '확정', NULL, 1050, '2026-11-10 02:00:26'
       UNION ALL SELECT (@dp + 33), (@da + 36), (@dm + 10), (@du + 30), '확정', NULL, 984, '2026-11-10 18:30:43'
       UNION ALL SELECT (@dp + 34), (@da + 37), (@dm + 10), (@du + 49), '확정', NULL, 928, '2026-11-10 21:37:40'
       UNION ALL SELECT (@dp + 35), (@da + 38), (@dm + 10), (@du + 77), '확정', NULL, 931, '2026-11-10 08:35:27'
       UNION ALL SELECT (@dp + 36), (@da + 39), (@dm + 10), (@du + 22), '확정', NULL, 903, '2026-11-10 19:02:20'
       UNION ALL SELECT (@dp + 37), (@da + 40), (@dm + 10), (@du + 58), '확정', NULL, 1084, '2026-11-10 07:54:16'
       UNION ALL SELECT (@dp + 38), (@da + 41), (@dm + 10), (@du + 74), '확정', NULL, 985, '2026-11-10 04:45:11'
       UNION ALL SELECT (@dp + 39), (@da + 42), (@dm + 10), (@du + 72), '확정', NULL, 871, '2026-11-10 00:51:42'
       UNION ALL SELECT (@dp + 40), (@da + 43), (@dm + 10), (@du + 2), '확정', NULL, 961, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 41), (@da + 44), (@dm + 12), (@du + 64), '확정', NULL, 973, '2026-11-11 07:34:23'
       UNION ALL SELECT (@dp + 42), (@da + 45), (@dm + 12), (@du + 81), '확정', NULL, 860, '2026-11-10 12:53:32'
       UNION ALL SELECT (@dp + 43), (@da + 46), (@dm + 12), (@du + 33), '확정', NULL, 822, '2026-11-09 21:44:01'
       UNION ALL SELECT (@dp + 44), (@da + 47), (@dm + 12), (@du + 21), '확정', NULL, 889, '2026-11-10 13:21:31'
       UNION ALL SELECT (@dp + 45), (@da + 48), (@dm + 14), (@du + 92), '확정', NULL, 918, '2026-11-11 04:11:26'
       UNION ALL SELECT (@dp + 46), (@da + 49), (@dm + 14), (@du + 46), '확정', NULL, 1033, '2026-11-09 17:46:21'
       UNION ALL SELECT (@dp + 47), (@da + 51), (@dm + 16), (@du + 29), '확정', NULL, 1142, '2026-11-09 15:17:56'
       UNION ALL SELECT (@dp + 48), (@da + 52), (@dm + 16), (@du + 63), '확정', NULL, 960, '2026-11-10 13:11:27'
       UNION ALL SELECT (@dp + 49), (@da + 53), (@dm + 16), (@du + 67), '확정', NULL, 893, '2026-11-11 04:43:22'
       UNION ALL SELECT (@dp + 50), (@da + 54), (@dm + 16), (@du + 10), '확정', NULL, 1144, '2026-11-10 10:17:15'
       UNION ALL SELECT (@dp + 51), (@da + 55), (@dm + 16), (@du + 83), '확정', NULL, 1043, '2026-11-11 01:43:54'
       UNION ALL SELECT (@dp + 52), (@da + 56), (@dm + 16), (@du + 7), '확정', NULL, 1062, '2026-11-09 23:54:17'
       UNION ALL SELECT (@dp + 53), (@da + 57), (@dm + 17), (@du + 18), '확정', NULL, 1054, '2026-11-11 01:56:42'
       UNION ALL SELECT (@dp + 54), (@da + 58), (@dm + 17), (@du + 84), '확정', NULL, 899, '2026-11-09 12:27:02'
       UNION ALL SELECT (@dp + 55), (@da + 59), (@dm + 17), (@du + 28), '확정', NULL, 889, '2026-11-10 08:09:54'
       UNION ALL SELECT (@dp + 56), (@da + 60), (@dm + 17), (@du + 3), '확정', NULL, 818, '2026-11-11 09:12:03'
       UNION ALL SELECT (@dp + 57), (@da + 61), (@dm + 17), (@du + 27), '확정', NULL, 1053, '2026-11-09 20:35:21'
       UNION ALL SELECT (@dp + 58), (@da + 64), (@dm + 18), (@du + 97), '확정', NULL, 1019, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 59), (@da + 65), (@dm + 18), (@du + 85), '확정', NULL, 1023, '2026-11-10 08:39:30'
       UNION ALL SELECT (@dp + 60), (@da + 66), (@dm + 18), (@du + 60), '확정', NULL, 1049, '2026-11-10 13:51:03'
       UNION ALL SELECT (@dp + 61), (@da + 67), (@dm + 19), (@du + 49), '확정', NULL, 928, '2026-11-10 16:01:21'
       UNION ALL SELECT (@dp + 62), (@da + 68), (@dm + 19), (@du + 58), '확정', NULL, 1084, '2026-11-10 16:01:21'
       UNION ALL SELECT (@dp + 63), (@da + 69), (@dm + 19), (@du + 3), '확정', NULL, 927, '2026-11-10 16:01:21'
       UNION ALL SELECT (@dp + 64), (@da + 70), (@dm + 19), (@du + 45), '확정', NULL, 1011, '2026-11-10 16:01:21'
       UNION ALL SELECT (@dp + 65), (@da + 71), (@dm + 19), (@du + 1), '확정', NULL, 1328, '2026-11-10 16:01:21'
       UNION ALL SELECT (@dp + 66), (@da + 72), (@dm + 19), (@du + 86), '확정', NULL, 796, '2026-11-11 00:49:31'
       UNION ALL SELECT (@dp + 67), (@da + 73), (@dm + 19), (@du + 22), '확정', NULL, 903, '2026-11-10 06:57:18'
       UNION ALL SELECT (@dp + 68), (@da + 74), (@dm + 19), (@du + 11), '확정', NULL, 1082, '2026-11-10 03:40:50'
       UNION ALL SELECT (@dp + 69), (@da + 75), (@dm + 19), (@du + 35), '확정', NULL, 1048, '2026-11-09 22:57:31'
       UNION ALL SELECT (@dp + 70), (@da + 76), (@dm + 19), (@du + 42), '확정', NULL, 1122, '2026-11-10 07:19:28'
       UNION ALL SELECT (@dp + 71), (@da + 80), (@dm + 20), (@du + 91), '확정', NULL, 1059, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 72), (@da + 81), (@dm + 20), (@du + 41), '확정', NULL, 984, '2026-11-11 08:37:14'
       UNION ALL SELECT (@dp + 73), (@da + 82), (@dm + 20), (@du + 35), '확정', NULL, 1048, '2026-11-11 05:56:03'
       UNION ALL SELECT (@dp + 74), (@da + 83), (@dm + 20), (@du + 72), '확정', NULL, 871, '2026-11-11 06:32:00'
       UNION ALL SELECT (@dp + 75), (@da + 84), (@dm + 21), (@du + 31), '확정', NULL, 990, '2026-11-11 07:13:35'
       UNION ALL SELECT (@dp + 76), (@da + 85), (@dm + 21), (@du + 74), '확정', NULL, 1147, '2026-11-10 10:48:55'
       UNION ALL SELECT (@dp + 77), (@da + 87), (@dm + 23), (@du + 8), '확정', NULL, 932, '2026-11-09 21:03:18'
       UNION ALL SELECT (@dp + 78), (@da + 88), (@dm + 23), (@du + 39), '확정', NULL, 1075, '2026-11-10 03:18:50'
       UNION ALL SELECT (@dp + 79), (@da + 89), (@dm + 23), (@du + 85), '확정', NULL, 1282, '2026-11-10 11:49:31'
       UNION ALL SELECT (@dp + 80), (@da + 90), (@dm + 23), (@du + 46), '확정', NULL, 1033, '2026-11-11 03:42:47'
       UNION ALL SELECT (@dp + 81), (@da + 95), (@dm + 25), (@du + 73), '확정', NULL, 1045, '2026-11-10 20:54:23'
       UNION ALL SELECT (@dp + 82), (@da + 96), (@dm + 25), (@du + 8), '확정', NULL, 1020, '2026-11-10 08:03:02'
       UNION ALL SELECT (@dp + 83), (@da + 97), (@dm + 27), (@du + 97), '확정', NULL, 1019, '2026-11-09 13:27:49'
       UNION ALL SELECT (@dp + 84), (@da + 98), (@dm + 27), (@du + 28), '확정', NULL, 846, '2026-11-09 19:13:18'
       UNION ALL SELECT (@dp + 85), (@da + 99), (@dm + 27), (@du + 68), '확정', NULL, 924, '2026-11-10 23:14:22'
       UNION ALL SELECT (@dp + 86), (@da + 100), (@dm + 27), (@du + 16), '확정', NULL, 893, '2026-11-09 23:44:56'
       UNION ALL SELECT (@dp + 87), (@da + 101), (@dm + 27), (@du + 59), '확정', NULL, 1014, '2026-11-10 20:00:41'
       UNION ALL SELECT (@dp + 88), (@da + 102), (@dm + 27), (@du + 83), '확정', NULL, 995, '2026-11-10 13:10:25'
       UNION ALL SELECT (@dp + 89), (@da + 103), (@dm + 28), (@du + 51), '확정', NULL, 1050, '2026-11-10 19:30:54'
       UNION ALL SELECT (@dp + 90), (@da + 104), (@dm + 28), (@du + 72), '확정', NULL, 871, '2026-11-10 19:30:54'
       UNION ALL SELECT (@dp + 91), (@da + 105), (@dm + 28), (@du + 59), '확정', NULL, 844, '2026-11-10 19:30:54'
       UNION ALL SELECT (@dp + 92), (@da + 106), (@dm + 28), (@du + 23), '확정', NULL, 1005, '2026-11-10 19:30:54'
       UNION ALL SELECT (@dp + 93), (@da + 107), (@dm + 28), (@du + 65), '확정', NULL, 1217, '2026-11-10 19:30:54'
       UNION ALL SELECT (@dp + 94), (@da + 108), (@dm + 28), (@du + 19), '확정', NULL, 1173, '2026-11-10 09:29:10'
       UNION ALL SELECT (@dp + 95), (@da + 109), (@dm + 28), (@du + 61), '확정', NULL, 945, '2026-11-10 21:16:40'
       UNION ALL SELECT (@dp + 96), (@da + 110), (@dm + 28), (@du + 17), '확정', NULL, 1097, '2026-11-10 09:05:43'
       UNION ALL SELECT (@dp + 97), (@da + 111), (@dm + 28), (@du + 34), '확정', NULL, 969, '2026-11-09 23:04:39'
       UNION ALL SELECT (@dp + 98), (@da + 112), (@dm + 28), (@du + 45), '확정', NULL, 1011, '2026-11-10 14:24:49'
       UNION ALL SELECT (@dp + 99), (@da + 116), (@dm + 29), (@du + 69), '확정', NULL, 1010, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 100), (@da + 117), (@dm + 29), (@du + 68), '확정', NULL, 956, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 101), (@da + 118), (@dm + 29), (@du + 56), '확정', NULL, 982, '2026-11-11 07:19:38'
       UNION ALL SELECT (@dp + 102), (@da + 119), (@dm + 29), (@du + 31), '확정', NULL, 920, '2026-11-10 01:00:31'
       UNION ALL SELECT (@dp + 103), (@da + 120), (@dm + 30), (@du + 48), '확정', NULL, 972, '2026-11-10 00:41:09'
       UNION ALL SELECT (@dp + 104), (@da + 121), (@dm + 30), (@du + 26), '확정', NULL, 760, '2026-11-09 23:50:35'
       UNION ALL SELECT (@dp + 105), (@da + 123), (@dm + 32), (@du + 92), '확정', NULL, 918, '2026-11-11 08:55:34'
       UNION ALL SELECT (@dp + 106), (@da + 124), (@dm + 32), (@du + 29), '확정', NULL, 760, '2026-11-11 03:52:49'
       UNION ALL SELECT (@dp + 107), (@da + 125), (@dm + 32), (@du + 8), '확정', NULL, 932, '2026-11-10 13:29:09'
       UNION ALL SELECT (@dp + 108), (@da + 126), (@dm + 32), (@du + 32), '확정', NULL, 921, '2026-11-11 09:34:55'
       UNION ALL SELECT (@dp + 109), (@da + 127), (@dm + 34), (@du + 7), '확정', NULL, 1062, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 110), (@da + 128), (@dm + 34), (@du + 97), '확정', NULL, 922, '2026-11-11 02:22:43'
       UNION ALL SELECT (@dp + 111), (@da + 129), (@dm + 36), (@du + 37), '확정', NULL, 950, '2026-11-10 15:20:05'
       UNION ALL SELECT (@dp + 112), (@da + 130), (@dm + 36), (@du + 93), '확정', NULL, 902, '2026-11-10 00:47:23'
       UNION ALL SELECT (@dp + 113), (@da + 131), (@dm + 36), (@du + 58), '확정', NULL, 944, '2026-11-11 11:39:38'
       UNION ALL SELECT (@dp + 114), (@da + 132), (@dm + 36), (@du + 12), '확정', NULL, 938, '2026-11-09 21:00:36'
       UNION ALL SELECT (@dp + 115), (@da + 133), (@dm + 36), (@du + 97), '확정', NULL, 1019, '2026-11-10 23:45:51'
       UNION ALL SELECT (@dp + 116), (@da + 134), (@dm + 36), (@du + 48), '확정', NULL, 1071, '2026-11-10 02:56:42'
       UNION ALL SELECT (@dp + 117), (@da + 135), (@dm + 37), (@du + 45), '확정', NULL, 1011, '2026-11-10 05:35:46'
       UNION ALL SELECT (@dp + 118), (@da + 136), (@dm + 37), (@du + 4), '확정', NULL, 897, '2026-11-10 00:19:27'
       UNION ALL SELECT (@dp + 119), (@da + 137), (@dm + 37), (@du + 49), '확정', NULL, 928, '2026-11-09 14:27:06'
       UNION ALL SELECT (@dp + 120), (@da + 138), (@dm + 37), (@du + 20), '확정', NULL, 1183, '2026-11-11 01:44:29'
       UNION ALL SELECT (@dp + 121), (@da + 139), (@dm + 37), (@du + 68), '확정', NULL, 956, '2026-11-10 17:48:55'
       UNION ALL SELECT (@dp + 122), (@da + 140), (@dm + 37), (@du + 41), '확정', NULL, 984, '2026-11-10 09:27:59'
       UNION ALL SELECT (@dp + 123), (@da + 141), (@dm + 37), (@du + 95), '확정', NULL, 861, '2026-11-09 21:19:49'
       UNION ALL SELECT (@dp + 124), (@da + 142), (@dm + 37), (@du + 31), '확정', NULL, 920, '2026-11-11 02:08:30'
       UNION ALL SELECT (@dp + 125), (@da + 143), (@dm + 37), (@du + 23), '확정', NULL, 1005, '2026-11-09 11:31:56'
       UNION ALL SELECT (@dp + 126), (@da + 144), (@dm + 37), (@du + 38), '확정', NULL, 951, '2026-11-10 11:30:26'
       UNION ALL SELECT (@dp + 127), (@da + 145), (@dm + 39), (@du + 89), '확정', NULL, 877, '2026-11-11 01:00:39'
       UNION ALL SELECT (@dp + 128), (@da + 146), (@dm + 39), (@du + 77), '확정', NULL, 1097, '2026-11-10 02:55:19'
       UNION ALL SELECT (@dp + 129), (@da + 147), (@dm + 39), (@du + 53), '확정', NULL, 1177, '2026-11-09 20:11:35'
       UNION ALL SELECT (@dp + 130), (@da + 148), (@dm + 39), (@du + 69), '확정', NULL, 1038, '2026-11-10 09:33:35'
       UNION ALL SELECT (@dp + 131), (@da + 149), (@dm + 41), (@du + 29), '확정', NULL, 760, '2026-11-11 10:54:00'
       UNION ALL SELECT (@dp + 132), (@da + 150), (@dm + 41), (@du + 85), '확정', NULL, 1282, '2026-11-09 22:33:39'
       UNION ALL SELECT (@dp + 133), (@da + 152), (@dm + 43), (@du + 60), '확정', NULL, 961, '2026-11-11 00:58:18'
       UNION ALL SELECT (@dp + 134), (@da + 153), (@dm + 43), (@du + 36), '확정', NULL, 983, '2026-11-10 20:00:16'
       UNION ALL SELECT (@dp + 135), (@da + 154), (@dm + 43), (@du + 39), '확정', NULL, 983, '2026-11-10 10:10:15'
       UNION ALL SELECT (@dp + 136), (@da + 155), (@dm + 43), (@du + 27), '확정', NULL, 1053, '2026-11-11 01:57:20'
       UNION ALL SELECT (@dp + 137), (@da + 156), (@dm + 43), (@du + 73), '확정', NULL, 1045, '2026-11-11 05:30:09'
       UNION ALL SELECT (@dp + 138), (@da + 157), (@dm + 43), (@du + 55), '확정', NULL, 1094, '2026-11-10 11:24:39'
       UNION ALL SELECT (@dp + 139), (@da + 158), (@dm + 44), (@du + 79), '확정', NULL, 940, '2026-11-09 16:16:15'
       UNION ALL SELECT (@dp + 140), (@da + 159), (@dm + 44), (@du + 14), '확정', NULL, 1063, '2026-11-11 09:48:30'
       UNION ALL SELECT (@dp + 141), (@da + 160), (@dm + 44), (@du + 72), '확정', NULL, 1130, '2026-11-10 18:12:19'
       UNION ALL SELECT (@dp + 142), (@da + 161), (@dm + 44), (@du + 88), '확정', NULL, 808, '2026-11-11 09:48:29'
       UNION ALL SELECT (@dp + 143), (@da + 162), (@dm + 44), (@du + 24), '확정', NULL, 987, '2026-11-10 22:11:48'
       UNION ALL SELECT (@dp + 144), (@da + 165), (@dm + 45), (@du + 6), '확정', NULL, 833, '2026-11-11 00:14:06'
       UNION ALL SELECT (@dp + 145), (@da + 166), (@dm + 45), (@du + 58), '확정', NULL, 944, '2026-11-09 14:42:36'
       UNION ALL SELECT (@dp + 146), (@da + 167), (@dm + 45), (@du + 37), '확정', NULL, 950, '2026-11-10 23:39:21'
       UNION ALL SELECT (@dp + 147), (@da + 168), (@dm + 46), (@du + 1), '확정', NULL, 1328, '2026-11-10 03:26:56'
       UNION ALL SELECT (@dp + 148), (@da + 169), (@dm + 46), (@du + 31), '확정', NULL, 920, '2026-11-09 11:00:35'
       UNION ALL SELECT (@dp + 149), (@da + 170), (@dm + 46), (@du + 79), '확정', NULL, 1097, '2026-11-09 14:32:59'
       UNION ALL SELECT (@dp + 150), (@da + 171), (@dm + 46), (@du + 99), '확정', NULL, 1108, '2026-11-10 22:12:56'
) v
 WHERE @demo_ready = 1;

INSERT INTO match_participant (participant_id, application_id, match_id, user_id, participation_status, match_side, entry_mmr, joined_at)
SELECT * FROM (
                 SELECT (@dp + 151) AS participant_id, (@da + 172) AS application_id, (@dm + 46) AS match_id, (@du + 61) AS user_id, '확정' AS participation_status, NULL AS match_side, 945 AS entry_mmr, '2026-11-09 11:30:31' AS joined_at
       UNION ALL SELECT (@dp + 152), (@da + 173), (@dm + 46), (@du + 11), '확정', NULL, 1082, '2026-11-10 06:37:47'
       UNION ALL SELECT (@dp + 153), (@da + 174), (@dm + 46), (@du + 46), '확정', NULL, 1035, '2026-11-09 18:46:13'
       UNION ALL SELECT (@dp + 154), (@da + 175), (@dm + 46), (@du + 22), '확정', NULL, 903, '2026-11-09 12:40:32'
       UNION ALL SELECT (@dp + 155), (@da + 176), (@dm + 46), (@du + 100), '확정', NULL, 998, '2026-11-10 17:48:50'
       UNION ALL SELECT (@dp + 156), (@da + 177), (@dm + 46), (@du + 19), '확정', NULL, 1173, '2026-11-11 09:06:11'
       UNION ALL SELECT (@dp + 157), (@da + 178), (@dm + 48), (@du + 34), '확정', NULL, 900, '2026-11-10 08:19:42'
       UNION ALL SELECT (@dp + 158), (@da + 179), (@dm + 48), (@du + 26), '확정', NULL, 760, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 159), (@da + 180), (@dm + 48), (@du + 41), '확정', NULL, 849, '2026-11-10 03:53:06'
       UNION ALL SELECT (@dp + 160), (@da + 181), (@dm + 48), (@du + 29), '확정', NULL, 868, '2026-11-09 22:41:11'
       UNION ALL SELECT (@dp + 161), (@da + 182), (@dm + 50), (@du + 96), '확정', NULL, 990, '2026-11-10 19:26:22'
       UNION ALL SELECT (@dp + 162), (@da + 183), (@dm + 50), (@du + 89), '확정', NULL, 944, '2026-11-10 15:53:02'
       UNION ALL SELECT (@dp + 163), (@da + 185), (@dm + 52), (@du + 72), '확정', NULL, 1130, '2026-11-10 13:40:40'
       UNION ALL SELECT (@dp + 164), (@da + 186), (@dm + 52), (@du + 62), '확정', NULL, 793, '2026-11-10 16:56:36'
       UNION ALL SELECT (@dp + 165), (@da + 187), (@dm + 52), (@du + 60), '확정', NULL, 961, '2026-11-11 04:58:06'
       UNION ALL SELECT (@dp + 166), (@da + 188), (@dm + 52), (@du + 28), '확정', NULL, 889, '2026-11-10 09:55:08'
       UNION ALL SELECT (@dp + 167), (@da + 189), (@dm + 52), (@du + 10), '확정', NULL, 1144, '2026-11-10 07:58:00'
       UNION ALL SELECT (@dp + 168), (@da + 190), (@dm + 52), (@du + 27), '확정', NULL, 1053, '2026-11-10 02:06:04'
       UNION ALL SELECT (@dp + 169), (@da + 191), (@dm + 53), (@du + 40), '확정', NULL, 1002, '2026-11-11 10:20:24'
       UNION ALL SELECT (@dp + 170), (@da + 192), (@dm + 53), (@du + 66), '확정', NULL, 904, '2026-11-10 16:51:41'
       UNION ALL SELECT (@dp + 171), (@da + 193), (@dm + 53), (@du + 6), '확정', NULL, 964, '2026-11-11 04:24:57'
       UNION ALL SELECT (@dp + 172), (@da + 194), (@dm + 53), (@du + 84), '확정', NULL, 899, '2026-11-10 15:42:42'
       UNION ALL SELECT (@dp + 173), (@da + 195), (@dm + 53), (@du + 70), '확정', NULL, 1125, '2026-11-10 08:33:55'
       UNION ALL SELECT (@dp + 174), (@da + 198), (@dm + 54), (@du + 36), '확정', NULL, 1181, '2026-11-10 08:40:49'
       UNION ALL SELECT (@dp + 175), (@da + 199), (@dm + 54), (@du + 59), '확정', NULL, 1014, '2026-11-09 22:42:29'
       UNION ALL SELECT (@dp + 176), (@da + 200), (@dm + 54), (@du + 67), '확정', NULL, 970, '2026-11-10 06:32:42'
       UNION ALL SELECT (@dp + 177), (@da + 201), (@dm + 55), (@du + 68), '확정', NULL, 956, '2026-11-10 11:08:36'
       UNION ALL SELECT (@dp + 178), (@da + 202), (@dm + 55), (@du + 82), '확정', NULL, 991, '2026-11-10 04:29:16'
       UNION ALL SELECT (@dp + 179), (@da + 203), (@dm + 55), (@du + 59), '확정', NULL, 844, '2026-11-10 07:36:47'
       UNION ALL SELECT (@dp + 180), (@da + 204), (@dm + 55), (@du + 74), '확정', NULL, 985, '2026-11-11 05:33:23'
       UNION ALL SELECT (@dp + 181), (@da + 205), (@dm + 55), (@du + 4), '확정', NULL, 897, '2026-11-09 15:36:54'
       UNION ALL SELECT (@dp + 182), (@da + 206), (@dm + 55), (@du + 11), '확정', NULL, 1082, '2026-11-10 18:35:29'
       UNION ALL SELECT (@dp + 183), (@da + 207), (@dm + 55), (@du + 79), '확정', NULL, 1097, '2026-11-09 23:05:17'
       UNION ALL SELECT (@dp + 184), (@da + 208), (@dm + 55), (@du + 90), '확정', NULL, 987, '2026-11-09 16:59:43'
       UNION ALL SELECT (@dp + 185), (@da + 209), (@dm + 55), (@du + 99), '확정', NULL, 1108, '2026-11-11 10:55:30'
       UNION ALL SELECT (@dp + 186), (@da + 210), (@dm + 55), (@du + 95), '확정', NULL, 861, '2026-11-10 19:38:03'
       UNION ALL SELECT (@dp + 187), (@da + 211), (@dm + 57), (@du + 28), '확정', NULL, 1008, '2026-11-10 10:19:28'
       UNION ALL SELECT (@dp + 188), (@da + 212), (@dm + 57), (@du + 64), '확정', NULL, 973, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 189), (@da + 213), (@dm + 57), (@du + 21), '확정', NULL, 889, '2026-11-09 16:58:43'
       UNION ALL SELECT (@dp + 190), (@da + 214), (@dm + 57), (@du + 17), '확정', NULL, 923, '2026-11-10 19:34:40'
       UNION ALL SELECT (@dp + 191), (@da + 215), (@dm + 59), (@du + 70), '확정', NULL, 909, '2026-11-10 12:34:39'
       UNION ALL SELECT (@dp + 192), (@da + 216), (@dm + 59), (@du + 29), '확정', NULL, 760, '2026-11-11 10:16:42'
       UNION ALL SELECT (@dp + 193), (@da + 218), (@dm + 61), (@du + 63), '확정', NULL, 960, '2026-11-11 08:08:43'
       UNION ALL SELECT (@dp + 194), (@da + 219), (@dm + 61), (@du + 83), '확정', NULL, 1043, '2026-11-09 12:43:09'
       UNION ALL SELECT (@dp + 195), (@da + 220), (@dm + 61), (@du + 62), '확정', NULL, 793, '2026-11-10 05:46:54'
       UNION ALL SELECT (@dp + 196), (@da + 221), (@dm + 61), (@du + 84), '확정', NULL, 899, '2026-11-10 17:42:33'
       UNION ALL SELECT (@dp + 197), (@da + 222), (@dm + 61), (@du + 97), '확정', NULL, 922, '2026-11-09 17:13:15'
       UNION ALL SELECT (@dp + 198), (@da + 223), (@dm + 61), (@du + 7), '확정', NULL, 1062, '2026-11-10 01:31:55'
       UNION ALL SELECT (@dp + 199), (@da + 224), (@dm + 62), (@du + 6), '확정', NULL, 964, '2026-11-10 09:28:07'
       UNION ALL SELECT (@dp + 200), (@da + 225), (@dm + 62), (@du + 3), '확정', NULL, 818, '2026-11-09 20:06:37'
       UNION ALL SELECT (@dp + 201), (@da + 226), (@dm + 62), (@du + 73), '확정', NULL, 1045, '2026-11-10 23:32:19'
       UNION ALL SELECT (@dp + 202), (@da + 227), (@dm + 62), (@du + 76), '확정', NULL, 1087, '2026-11-09 14:08:25'
       UNION ALL SELECT (@dp + 203), (@da + 228), (@dm + 62), (@du + 39), '확정', NULL, 983, '2026-11-10 13:02:10'
       UNION ALL SELECT (@dp + 204), (@da + 231), (@dm + 63), (@du + 81), '확정', NULL, 820, '2026-11-09 18:40:32'
       UNION ALL SELECT (@dp + 205), (@da + 232), (@dm + 63), (@du + 23), '확정', NULL, 849, '2026-11-10 07:34:38'
       UNION ALL SELECT (@dp + 206), (@da + 233), (@dm + 63), (@du + 85), '확정', NULL, 1023, '2026-11-10 16:14:13'
       UNION ALL SELECT (@dp + 207), (@da + 234), (@dm + 64), (@du + 83), '확정', NULL, 775, '2026-11-10 21:56:45'
       UNION ALL SELECT (@dp + 208), (@da + 235), (@dm + 64), (@du + 81), '확정', NULL, 957, '2026-11-11 05:23:43'
       UNION ALL SELECT (@dp + 209), (@da + 236), (@dm + 64), (@du + 72), '확정', NULL, 871, '2026-11-11 10:47:29'
       UNION ALL SELECT (@dp + 210), (@da + 237), (@dm + 64), (@du + 79), '확정', NULL, 1097, '2026-11-10 23:33:12'
       UNION ALL SELECT (@dp + 211), (@da + 238), (@dm + 64), (@du + 54), '확정', NULL, 848, '2026-11-11 08:37:05'
       UNION ALL SELECT (@dp + 212), (@da + 239), (@dm + 64), (@du + 57), '확정', NULL, 832, '2026-11-10 09:28:24'
       UNION ALL SELECT (@dp + 213), (@da + 240), (@dm + 64), (@du + 35), '확정', NULL, 1048, '2026-11-09 11:10:45'
       UNION ALL SELECT (@dp + 214), (@da + 241), (@dm + 64), (@du + 69), '확정', NULL, 1010, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 215), (@da + 242), (@dm + 64), (@du + 56), '확정', NULL, 982, '2026-11-10 03:09:31'
       UNION ALL SELECT (@dp + 216), (@da + 243), (@dm + 64), (@du + 85), '확정', NULL, 1120, '2026-11-10 19:56:43'
       UNION ALL SELECT (@dp + 217), (@da + 244), (@dm + 66), (@du + 16), '확정', NULL, 979, '2026-11-10 02:38:37'
       UNION ALL SELECT (@dp + 218), (@da + 245), (@dm + 66), (@du + 60), '확정', NULL, 998, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 219), (@da + 246), (@dm + 66), (@du + 98), '확정', NULL, 1166, '2026-11-10 06:49:32'
       UNION ALL SELECT (@dp + 220), (@da + 247), (@dm + 66), (@du + 31), '확정', NULL, 990, '2026-11-10 08:41:25'
       UNION ALL SELECT (@dp + 221), (@da + 248), (@dm + 68), (@du + 94), '확정', NULL, 1093, '2026-11-10 04:33:39'
       UNION ALL SELECT (@dp + 222), (@da + 249), (@dm + 68), (@du + 46), '확정', NULL, 1033, '2026-11-11 04:24:57'
       UNION ALL SELECT (@dp + 223), (@da + 251), (@dm + 70), (@du + 55), '확정', NULL, 1094, '2026-11-11 00:36:36'
       UNION ALL SELECT (@dp + 224), (@da + 252), (@dm + 70), (@du + 88), '확정', NULL, 808, '2026-11-10 17:07:21'
       UNION ALL SELECT (@dp + 225), (@da + 253), (@dm + 70), (@du + 6), '확정', NULL, 964, '2026-11-10 05:50:01'
       UNION ALL SELECT (@dp + 226), (@da + 254), (@dm + 70), (@du + 63), '확정', NULL, 960, '2026-11-09 20:51:53'
       UNION ALL SELECT (@dp + 227), (@da + 255), (@dm + 70), (@du + 8), '확정', NULL, 1020, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 228), (@da + 256), (@dm + 70), (@du + 65), '확정', NULL, 1157, '2026-11-10 07:56:38'
       UNION ALL SELECT (@dp + 229), (@da + 257), (@dm + 71), (@du + 40), '확정', NULL, 1002, '2026-11-10 06:03:27'
       UNION ALL SELECT (@dp + 230), (@da + 258), (@dm + 71), (@du + 14), '확정', NULL, 1063, '2026-11-10 19:27:14'
       UNION ALL SELECT (@dp + 231), (@da + 259), (@dm + 71), (@du + 50), '확정', NULL, 1033, '2026-11-10 22:07:25'
       UNION ALL SELECT (@dp + 232), (@da + 260), (@dm + 71), (@du + 66), '확정', NULL, 904, '2026-11-11 04:57:21'
       UNION ALL SELECT (@dp + 233), (@da + 261), (@dm + 71), (@du + 75), '확정', NULL, 937, '2026-11-10 15:15:39'
       UNION ALL SELECT (@dp + 234), (@da + 264), (@dm + 72), (@du + 33), '확정', NULL, 866, '2026-11-11 04:50:35'
       UNION ALL SELECT (@dp + 235), (@da + 265), (@dm + 72), (@du + 73), '확정', NULL, 950, '2026-11-10 09:19:55'
       UNION ALL SELECT (@dp + 236), (@da + 266), (@dm + 72), (@du + 38), '확정', NULL, 915, '2026-11-09 18:04:49'
       UNION ALL SELECT (@dp + 237), (@da + 267), (@dm + 73), (@du + 20), '확정', NULL, 1183, '2026-11-11 02:59:01'
       UNION ALL SELECT (@dp + 238), (@da + 268), (@dm + 73), (@du + 1), '확정', NULL, 1328, '2026-11-11 02:25:11'
       UNION ALL SELECT (@dp + 239), (@da + 269), (@dm + 73), (@du + 42), '확정', NULL, 1122, '2026-11-11 08:29:52'
       UNION ALL SELECT (@dp + 240), (@da + 270), (@dm + 73), (@du + 65), '확정', NULL, 1217, '2026-11-10 01:34:10'
       UNION ALL SELECT (@dp + 241), (@da + 271), (@dm + 73), (@du + 47), '확정', NULL, 1130, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 242), (@da + 272), (@dm + 73), (@du + 63), '확정', NULL, 817, '2026-11-11 05:16:29'
       UNION ALL SELECT (@dp + 243), (@da + 273), (@dm + 73), (@du + 85), '확정', NULL, 1120, '2026-11-11 06:47:58'
       UNION ALL SELECT (@dp + 244), (@da + 274), (@dm + 73), (@du + 44), '확정', NULL, 924, '2026-11-11 08:50:21'
       UNION ALL SELECT (@dp + 245), (@da + 275), (@dm + 73), (@du + 59), '확정', NULL, 844, '2026-11-09 22:16:18'
       UNION ALL SELECT (@dp + 246), (@da + 276), (@dm + 73), (@du + 99), '확정', NULL, 1108, '2026-11-10 06:39:54'
       UNION ALL SELECT (@dp + 247), (@da + 277), (@dm + 75), (@du + 79), '확정', NULL, 1051, '2026-11-09 22:03:38'
       UNION ALL SELECT (@dp + 248), (@da + 278), (@dm + 75), (@du + 16), '확정', NULL, 979, '2026-11-11 07:54:11'
       UNION ALL SELECT (@dp + 249), (@da + 279), (@dm + 75), (@du + 19), '확정', NULL, 1071, '2026-11-11 08:42:09'
       UNION ALL SELECT (@dp + 250), (@da + 280), (@dm + 75), (@du + 33), '확정', NULL, 822, '2026-11-10 11:30:33'
       UNION ALL SELECT (@dp + 251), (@da + 281), (@dm + 77), (@du + 43), '확정', NULL, 962, '2026-11-09 20:00:05'
       UNION ALL SELECT (@dp + 252), (@da + 282), (@dm + 77), (@du + 5), '확정', NULL, 1011, '2026-11-09 19:59:32'
       UNION ALL SELECT (@dp + 253), (@da + 284), (@dm + 79), (@du + 97), '확정', NULL, 922, '2026-11-09 21:48:44'
       UNION ALL SELECT (@dp + 254), (@da + 285), (@dm + 79), (@du + 14), '확정', NULL, 1063, '2026-11-10 06:37:36'
       UNION ALL SELECT (@dp + 255), (@da + 286), (@dm + 79), (@du + 75), '확정', NULL, 937, '2026-11-10 05:06:48'
       UNION ALL SELECT (@dp + 256), (@da + 287), (@dm + 79), (@du + 76), '확정', NULL, 1087, '2026-11-10 09:08:35'
       UNION ALL SELECT (@dp + 257), (@da + 288), (@dm + 79), (@du + 27), '확정', NULL, 1053, '2026-11-10 20:44:58'
       UNION ALL SELECT (@dp + 258), (@da + 289), (@dm + 79), (@du + 15), '확정', NULL, 996, '2026-11-11 03:58:22'
       UNION ALL SELECT (@dp + 259), (@da + 290), (@dm + 80), (@du + 83), '확정', NULL, 1043, '2026-11-10 06:08:10'
       UNION ALL SELECT (@dp + 260), (@da + 291), (@dm + 80), (@du + 24), '확정', NULL, 987, '2026-11-11 08:36:11'
       UNION ALL SELECT (@dp + 261), (@da + 292), (@dm + 80), (@du + 3), '확정', NULL, 818, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 262), (@da + 293), (@dm + 80), (@du + 26), '확정', NULL, 951, '2026-11-11 10:11:39'
       UNION ALL SELECT (@dp + 263), (@da + 294), (@dm + 80), (@du + 13), '확정', NULL, 997, '2026-11-10 12:42:12'
       UNION ALL SELECT (@dp + 264), (@da + 297), (@dm + 81), (@du + 61), '확정', NULL, 931, '2026-11-10 20:55:16'
       UNION ALL SELECT (@dp + 265), (@da + 298), (@dm + 81), (@du + 28), '확정', NULL, 846, '2026-11-09 13:55:07'
       UNION ALL SELECT (@dp + 266), (@da + 299), (@dm + 81), (@du + 38), '확정', NULL, 915, '2026-11-10 22:18:13'
       UNION ALL SELECT (@dp + 267), (@da + 300), (@dm + 82), (@du + 51), '확정', NULL, 1050, '2026-11-10 05:43:32'
       UNION ALL SELECT (@dp + 268), (@da + 301), (@dm + 82), (@du + 59), '확정', NULL, 844, '2026-11-10 05:43:32'
       UNION ALL SELECT (@dp + 269), (@da + 302), (@dm + 82), (@du + 4), '확정', NULL, 897, '2026-11-10 05:43:32'
       UNION ALL SELECT (@dp + 270), (@da + 303), (@dm + 82), (@du + 65), '확정', NULL, 1217, '2026-11-10 05:43:32'
       UNION ALL SELECT (@dp + 271), (@da + 304), (@dm + 82), (@du + 83), '확정', NULL, 775, '2026-11-10 05:43:32'
       UNION ALL SELECT (@dp + 272), (@da + 305), (@dm + 82), (@du + 30), '확정', NULL, 984, '2026-11-10 05:30:43'
       UNION ALL SELECT (@dp + 273), (@da + 306), (@dm + 82), (@du + 42), '확정', NULL, 1122, '2026-11-11 11:46:51'
       UNION ALL SELECT (@dp + 274), (@da + 307), (@dm + 82), (@du + 58), '확정', NULL, 1084, '2026-11-10 18:22:52'
       UNION ALL SELECT (@dp + 275), (@da + 308), (@dm + 82), (@du + 2), '확정', NULL, 961, '2026-11-09 23:57:22'
       UNION ALL SELECT (@dp + 276), (@da + 309), (@dm + 82), (@du + 95), '확정', NULL, 861, '2026-11-10 16:18:10'
       UNION ALL SELECT (@dp + 277), (@da + 313), (@dm + 83), (@du + 45), '확정', NULL, 1011, '2026-11-10 20:52:44'
       UNION ALL SELECT (@dp + 278), (@da + 314), (@dm + 83), (@du + 46), '확정', NULL, 1035, '2026-11-11 10:13:44'
       UNION ALL SELECT (@dp + 279), (@da + 315), (@dm + 83), (@du + 41), '확정', NULL, 984, '2026-11-11 11:48:58'
       UNION ALL SELECT (@dp + 280), (@da + 316), (@dm + 83), (@du + 58), '확정', NULL, 1084, '2026-11-10 13:31:18'
       UNION ALL SELECT (@dp + 281), (@da + 317), (@dm + 84), (@du + 56), '확정', NULL, 874, '2026-11-09 18:23:01'
       UNION ALL SELECT (@dp + 282), (@da + 318), (@dm + 84), (@du + 82), '확정', NULL, 1047, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 283), (@da + 320), (@dm + 86), (@du + 24), '확정', NULL, 875, '2026-11-09 11:41:15'
       UNION ALL SELECT (@dp + 284), (@da + 321), (@dm + 86), (@du + 66), '확정', NULL, 760, '2026-11-11 11:08:25'
       UNION ALL SELECT (@dp + 285), (@da + 322), (@dm + 86), (@du + 92), '확정', NULL, 918, '2026-11-10 14:14:01'
       UNION ALL SELECT (@dp + 286), (@da + 323), (@dm + 86), (@du + 5), '확정', NULL, 1011, '2026-11-09 23:18:37'
       UNION ALL SELECT (@dp + 287), (@da + 328), (@dm + 88), (@du + 39), '확정', NULL, 983, '2026-11-11 08:13:09'
       UNION ALL SELECT (@dp + 288), (@da + 329), (@dm + 88), (@du + 96), '확정', NULL, 935, '2026-11-10 08:51:21'
       UNION ALL SELECT (@dp + 289), (@da + 330), (@dm + 90), (@du + 28), '확정', NULL, 846, '2026-11-11 05:51:02'
       UNION ALL SELECT (@dp + 290), (@da + 331), (@dm + 90), (@du + 86), '확정', NULL, 990, '2026-11-09 12:06:55'
       UNION ALL SELECT (@dp + 291), (@da + 332), (@dm + 90), (@du + 89), '확정', NULL, 1064, '2026-11-11 08:45:09'
       UNION ALL SELECT (@dp + 292), (@da + 333), (@dm + 90), (@du + 68), '확정', NULL, 924, '2026-11-10 03:27:59'
       UNION ALL SELECT (@dp + 293), (@da + 334), (@dm + 90), (@du + 47), '확정', NULL, 970, '2026-11-11 04:04:04'
       UNION ALL SELECT (@dp + 294), (@da + 335), (@dm + 90), (@du + 85), '확정', NULL, 1023, '2026-11-11 09:40:08'
       UNION ALL SELECT (@dp + 295), (@da + 336), (@dm + 91), (@du + 81), '확정', NULL, 957, '2026-11-10 14:29:35'
       UNION ALL SELECT (@dp + 296), (@da + 337), (@dm + 91), (@du + 31), '확정', NULL, 920, '2026-11-10 14:29:35'
       UNION ALL SELECT (@dp + 297), (@da + 338), (@dm + 91), (@du + 47), '확정', NULL, 1130, '2026-11-10 14:29:35'
       UNION ALL SELECT (@dp + 298), (@da + 339), (@dm + 91), (@du + 85), '확정', NULL, 1120, '2026-11-10 14:29:35'
       UNION ALL SELECT (@dp + 299), (@da + 340), (@dm + 91), (@du + 77), '확정', NULL, 931, '2026-11-10 14:29:35'
       UNION ALL SELECT (@dp + 300), (@da + 341), (@dm + 91), (@du + 74), '확정', NULL, 985, '2026-11-10 01:25:32'
) v
 WHERE @demo_ready = 1;

INSERT INTO match_participant (participant_id, application_id, match_id, user_id, participation_status, match_side, entry_mmr, joined_at)
SELECT * FROM (
                 SELECT (@dp + 301) AS participant_id, (@da + 342) AS application_id, (@dm + 91) AS match_id, (@du + 41) AS user_id, '확정' AS participation_status, NULL AS match_side, 984 AS entry_mmr, '2026-11-10 21:42:18' AS joined_at
       UNION ALL SELECT (@dp + 302), (@da + 343), (@dm + 91), (@du + 2), '확정', NULL, 961, '2026-11-10 11:10:55'
       UNION ALL SELECT (@dp + 303), (@da + 344), (@dm + 91), (@du + 30), '확정', NULL, 984, '2026-11-09 18:12:48'
       UNION ALL SELECT (@dp + 304), (@da + 345), (@dm + 91), (@du + 72), '확정', NULL, 871, '2026-11-09 15:04:20'
       UNION ALL SELECT (@dp + 305), (@da + 349), (@dm + 92), (@du + 83), '확정', NULL, 775, '2026-11-09 22:26:07'
       UNION ALL SELECT (@dp + 306), (@da + 350), (@dm + 92), (@du + 2), '확정', NULL, 961, '2026-11-10 02:07:56'
       UNION ALL SELECT (@dp + 307), (@da + 351), (@dm + 92), (@du + 99), '확정', NULL, 1108, '2026-11-09 13:13:00'
       UNION ALL SELECT (@dp + 308), (@da + 352), (@dm + 92), (@du + 71), '확정', NULL, 864, '2026-11-11 00:08:37'
       UNION ALL SELECT (@dp + 309), (@da + 353), (@dm + 93), (@du + 96), '확정', NULL, 1199, '2026-11-10 17:44:26'
       UNION ALL SELECT (@dp + 310), (@da + 354), (@dm + 93), (@du + 24), '확정', NULL, 1160, '2026-11-11 02:33:51'
       UNION ALL SELECT (@dp + 311), (@da + 356), (@dm + 95), (@du + 5), '확정', NULL, 1011, '2026-11-11 10:51:43'
       UNION ALL SELECT (@dp + 312), (@da + 357), (@dm + 95), (@du + 8), '확정', NULL, 932, '2026-11-09 15:05:53'
       UNION ALL SELECT (@dp + 313), (@da + 358), (@dm + 95), (@du + 22), '확정', NULL, 863, '2026-11-09 19:46:28'
       UNION ALL SELECT (@dp + 314), (@da + 359), (@dm + 95), (@du + 12), '확정', NULL, 979, '2026-11-09 16:58:45'
       UNION ALL SELECT (@dp + 315), (@da + 364), (@dm + 97), (@du + 79), '확정', NULL, 940, '2026-11-10 04:27:21'
       UNION ALL SELECT (@dp + 316), (@da + 365), (@dm + 97), (@du + 88), '확정', NULL, 808, '2026-11-09 22:46:13'
       UNION ALL SELECT (@dp + 317), (@da + 366), (@dm + 99), (@du + 59), '확정', NULL, 1014, '2026-11-11 11:14:47'
       UNION ALL SELECT (@dp + 318), (@da + 367), (@dm + 99), (@du + 83), '확정', NULL, 995, '2026-11-11 01:53:13'
       UNION ALL SELECT (@dp + 319), (@da + 368), (@dm + 99), (@du + 33), '확정', NULL, 866, '2026-11-09 20:23:05'
       UNION ALL SELECT (@dp + 320), (@da + 369), (@dm + 99), (@du + 82), '확정', NULL, 956, '2026-11-10 20:23:50'
       UNION ALL SELECT (@dp + 321), (@da + 370), (@dm + 99), (@du + 93), '확정', NULL, 902, '2026-11-09 12:38:06'
       UNION ALL SELECT (@dp + 322), (@da + 371), (@dm + 99), (@du + 15), '확정', NULL, 870, '2026-11-09 16:26:14'
       UNION ALL SELECT (@dp + 323), (@da + 372), (@dm + 100), (@du + 47), '확정', NULL, 1130, '2026-11-11 11:54:53'
       UNION ALL SELECT (@dp + 324), (@da + 373), (@dm + 100), (@du + 23), '확정', NULL, 1005, '2026-11-09 13:21:49'
       UNION ALL SELECT (@dp + 325), (@da + 374), (@dm + 100), (@du + 38), '확정', NULL, 951, '2026-11-10 20:34:04'
       UNION ALL SELECT (@dp + 326), (@da + 375), (@dm + 100), (@du + 69), '확정', NULL, 1010, '2026-11-09 22:49:15'
       UNION ALL SELECT (@dp + 327), (@da + 376), (@dm + 100), (@du + 91), '확정', NULL, 1059, '2026-11-09 21:06:17'
       UNION ALL SELECT (@dp + 328), (@da + 377), (@dm + 100), (@du + 95), '확정', NULL, 861, '2026-11-09 14:07:52'
       UNION ALL SELECT (@dp + 329), (@da + 378), (@dm + 100), (@du + 45), '확정', NULL, 1011, '2026-11-09 14:27:20'
       UNION ALL SELECT (@dp + 330), (@da + 379), (@dm + 100), (@du + 58), '확정', NULL, 1084, '2026-11-09 21:07:19'
       UNION ALL SELECT (@dp + 331), (@da + 380), (@dm + 100), (@du + 4), '확정', NULL, 897, '2026-11-11 11:25:34'
       UNION ALL SELECT (@dp + 332), (@da + 381), (@dm + 100), (@du + 19), '확정', NULL, 1173, '2026-11-10 21:10:44'
       UNION ALL SELECT (@dp + 333), (@da + 382), (@dm + 102), (@du + 84), '확정', NULL, 1027, '2026-11-09 19:08:20'
       UNION ALL SELECT (@dp + 334), (@da + 383), (@dm + 102), (@du + 43), '확정', NULL, 924, '2026-11-10 17:26:38'
       UNION ALL SELECT (@dp + 335), (@da + 384), (@dm + 102), (@du + 68), '확정', NULL, 1014, '2026-11-10 03:43:02'
       UNION ALL SELECT (@dp + 336), (@da + 385), (@dm + 102), (@du + 66), '확정', NULL, 1053, '2026-11-09 23:05:49'
       UNION ALL SELECT (@dp + 337), (@da + 386), (@dm + 104), (@du + 100), '확정', NULL, 855, '2026-11-10 21:47:00'
       UNION ALL SELECT (@dp + 338), (@da + 387), (@dm + 104), (@du + 42), '확정', NULL, 885, '2026-11-10 06:47:10'
       UNION ALL SELECT (@dp + 339), (@da + 389), (@dm + 106), (@du + 76), '확정', NULL, 1087, '2026-11-10 14:31:03'
       UNION ALL SELECT (@dp + 340), (@da + 390), (@dm + 106), (@du + 55), '확정', NULL, 1094, '2026-11-11 08:52:51'
       UNION ALL SELECT (@dp + 341), (@da + 391), (@dm + 106), (@du + 29), '확정', NULL, 1142, '2026-11-10 05:10:07'
       UNION ALL SELECT (@dp + 342), (@da + 392), (@dm + 106), (@du + 27), '확정', NULL, 1053, '2026-11-09 15:46:35'
       UNION ALL SELECT (@dp + 343), (@da + 393), (@dm + 106), (@du + 28), '확정', NULL, 889, '2026-11-11 02:02:29'
       UNION ALL SELECT (@dp + 344), (@da + 394), (@dm + 106), (@du + 65), '확정', NULL, 1157, '2026-11-09 21:51:37'
       UNION ALL SELECT (@dp + 345), (@da + 395), (@dm + 107), (@du + 67), '확정', NULL, 893, '2026-11-10 09:31:36'
       UNION ALL SELECT (@dp + 346), (@da + 396), (@dm + 107), (@du + 60), '확정', NULL, 961, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 347), (@da + 397), (@dm + 107), (@du + 39), '확정', NULL, 983, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 348), (@da + 398), (@dm + 107), (@du + 3), '확정', NULL, 818, '2026-11-09 15:43:13'
       UNION ALL SELECT (@dp + 349), (@da + 399), (@dm + 107), (@du + 96), '확정', NULL, 935, '2026-11-09 22:41:29'
       UNION ALL SELECT (@dp + 350), (@da + 402), (@dm + 108), (@du + 59), '확정', NULL, 1014, '2026-11-10 11:33:37'
       UNION ALL SELECT (@dp + 351), (@da + 403), (@dm + 108), (@du + 81), '확정', NULL, 820, '2026-11-10 21:17:43'
       UNION ALL SELECT (@dp + 352), (@da + 404), (@dm + 108), (@du + 11), '확정', NULL, 989, '2026-11-09 14:14:22'
       UNION ALL SELECT (@dp + 353), (@da + 405), (@dm + 109), (@du + 4), '확정', NULL, 897, '2026-11-10 06:31:18'
       UNION ALL SELECT (@dp + 354), (@da + 406), (@dm + 109), (@du + 59), '확정', NULL, 844, '2026-11-11 03:28:17'
       UNION ALL SELECT (@dp + 355), (@da + 407), (@dm + 109), (@du + 83), '확정', NULL, 775, '2026-11-09 20:10:06'
       UNION ALL SELECT (@dp + 356), (@da + 408), (@dm + 109), (@du + 77), '확정', NULL, 931, '2026-11-10 09:16:53'
       UNION ALL SELECT (@dp + 357), (@da + 409), (@dm + 109), (@du + 56), '확정', NULL, 982, '2026-11-09 17:38:39'
       UNION ALL SELECT (@dp + 358), (@da + 410), (@dm + 109), (@du + 23), '확정', NULL, 1005, '2026-11-09 18:30:19'
       UNION ALL SELECT (@dp + 359), (@da + 411), (@dm + 109), (@du + 61), '확정', NULL, 945, '2026-11-10 19:46:22'
       UNION ALL SELECT (@dp + 360), (@da + 412), (@dm + 109), (@du + 90), '확정', NULL, 987, '2026-11-10 11:31:48'
       UNION ALL SELECT (@dp + 361), (@da + 413), (@dm + 109), (@du + 81), '확정', NULL, 957, '2026-11-11 11:15:30'
       UNION ALL SELECT (@dp + 362), (@da + 414), (@dm + 109), (@du + 19), '확정', NULL, 1173, '2026-11-11 10:55:39'
       UNION ALL SELECT (@dp + 363), (@da + 415), (@dm + 111), (@du + 48), '확정', NULL, 972, '2026-11-11 10:33:32'
       UNION ALL SELECT (@dp + 364), (@da + 416), (@dm + 111), (@du + 79), '확정', NULL, 1051, '2026-11-10 02:23:33'
       UNION ALL SELECT (@dp + 365), (@da + 417), (@dm + 111), (@du + 98), '확정', NULL, 1166, '2026-11-10 08:45:03'
       UNION ALL SELECT (@dp + 366), (@da + 418), (@dm + 111), (@du + 57), '확정', NULL, 1076, '2026-11-10 22:43:08'
       UNION ALL SELECT (@dp + 367), (@da + 419), (@dm + 113), (@du + 100), '확정', NULL, 855, '2026-11-11 04:15:56'
       UNION ALL SELECT (@dp + 368), (@da + 420), (@dm + 113), (@du + 66), '확정', NULL, 760, '2026-11-11 11:00:29'
       UNION ALL SELECT (@dp + 369), (@da + 422), (@dm + 115), (@du + 75), '확정', NULL, 937, '2026-11-10 03:42:47'
       UNION ALL SELECT (@dp + 370), (@da + 423), (@dm + 115), (@du + 50), '확정', NULL, 1033, '2026-11-11 11:32:28'
       UNION ALL SELECT (@dp + 371), (@da + 424), (@dm + 115), (@du + 63), '확정', NULL, 960, '2026-11-09 22:32:40'
       UNION ALL SELECT (@dp + 372), (@da + 425), (@dm + 115), (@du + 71), '확정', NULL, 973, '2026-11-11 11:41:02'
       UNION ALL SELECT (@dp + 373), (@da + 426), (@dm + 115), (@du + 3), '확정', NULL, 818, '2026-11-10 09:51:35'
       UNION ALL SELECT (@dp + 374), (@da + 427), (@dm + 115), (@du + 87), '확정', NULL, 980, '2026-11-09 18:06:09'
       UNION ALL SELECT (@dp + 375), (@da + 428), (@dm + 116), (@du + 8), '확정', NULL, 1020, '2026-11-10 00:29:18'
       UNION ALL SELECT (@dp + 376), (@da + 429), (@dm + 116), (@du + 60), '확정', NULL, 961, '2026-11-11 02:36:42'
       UNION ALL SELECT (@dp + 377), (@da + 430), (@dm + 116), (@du + 26), '확정', NULL, 951, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 378), (@da + 431), (@dm + 116), (@du + 72), '확정', NULL, 1130, '2026-11-10 21:05:47'
       UNION ALL SELECT (@dp + 379), (@da + 432), (@dm + 116), (@du + 13), '확정', NULL, 997, '2026-11-10 13:20:06'
       UNION ALL SELECT (@dp + 380), (@da + 435), (@dm + 117), (@du + 7), '확정', NULL, 1217, '2026-11-11 04:52:52'
       UNION ALL SELECT (@dp + 381), (@da + 436), (@dm + 117), (@du + 47), '확정', NULL, 970, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 382), (@da + 437), (@dm + 117), (@du + 85), '확정', NULL, 1023, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 383), (@da + 438), (@dm + 118), (@du + 95), '확정', NULL, 861, '2026-11-10 02:29:58'
       UNION ALL SELECT (@dp + 384), (@da + 439), (@dm + 118), (@du + 68), '확정', NULL, 956, '2026-11-10 20:39:45'
       UNION ALL SELECT (@dp + 385), (@da + 440), (@dm + 118), (@du + 31), '확정', NULL, 920, '2026-11-10 04:21:38'
       UNION ALL SELECT (@dp + 386), (@da + 441), (@dm + 118), (@du + 83), '확정', NULL, 775, '2026-11-11 03:24:27'
       UNION ALL SELECT (@dp + 387), (@da + 442), (@dm + 118), (@du + 61), '확정', NULL, 945, '2026-11-11 11:36:40'
       UNION ALL SELECT (@dp + 388), (@da + 443), (@dm + 118), (@du + 57), '확정', NULL, 832, '2026-11-10 15:10:27'
       UNION ALL SELECT (@dp + 389), (@da + 444), (@dm + 118), (@du + 34), '확정', NULL, 969, '2026-11-09 23:59:45'
       UNION ALL SELECT (@dp + 390), (@da + 445), (@dm + 118), (@du + 42), '확정', NULL, 1122, '2026-11-09 10:57:12'
       UNION ALL SELECT (@dp + 391), (@da + 446), (@dm + 118), (@du + 69), '확정', NULL, 1010, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 392), (@da + 447), (@dm + 118), (@du + 91), '확정', NULL, 1059, '2026-11-10 23:47:05'
       UNION ALL SELECT (@dp + 393), (@da + 448), (@dm + 120), (@du + 94), '확정', NULL, 1073, '2026-11-09 18:21:27'
       UNION ALL SELECT (@dp + 394), (@da + 449), (@dm + 120), (@du + 93), '확정', NULL, 980, '2026-11-11 09:04:47'
       UNION ALL SELECT (@dp + 395), (@da + 450), (@dm + 120), (@du + 56), '확정', NULL, 874, '2026-11-10 08:34:50'
       UNION ALL SELECT (@dp + 396), (@da + 451), (@dm + 120), (@du + 53), '확정', NULL, 1177, '2026-11-10 17:22:46'
       UNION ALL SELECT (@dp + 397), (@da + 452), (@dm + 122), (@du + 63), '확정', NULL, 1020, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 398), (@da + 453), (@dm + 122), (@du + 5), '확정', NULL, 1011, '2026-11-11 10:10:36'
       UNION ALL SELECT (@dp + 399), (@da + 455), (@dm + 124), (@du + 71), '확정', NULL, 973, '2026-11-09 12:31:37'
       UNION ALL SELECT (@dp + 400), (@da + 456), (@dm + 124), (@du + 7), '확정', NULL, 1062, '2026-11-09 21:46:34'
       UNION ALL SELECT (@dp + 401), (@da + 457), (@dm + 124), (@du + 84), '확정', NULL, 899, '2026-11-10 17:00:36'
       UNION ALL SELECT (@dp + 402), (@da + 458), (@dm + 124), (@du + 76), '확정', NULL, 1087, '2026-11-11 01:47:29'
       UNION ALL SELECT (@dp + 403), (@da + 459), (@dm + 124), (@du + 24), '확정', NULL, 987, '2026-11-10 11:11:21'
       UNION ALL SELECT (@dp + 404), (@da + 460), (@dm + 124), (@du + 15), '확정', NULL, 996, '2026-11-10 18:53:32'
       UNION ALL SELECT (@dp + 405), (@da + 461), (@dm + 125), (@du + 3), '확정', NULL, 818, '2026-11-10 03:45:13'
       UNION ALL SELECT (@dp + 406), (@da + 462), (@dm + 125), (@du + 13), '확정', NULL, 997, '2026-11-10 08:49:34'
       UNION ALL SELECT (@dp + 407), (@da + 463), (@dm + 125), (@du + 9), '확정', NULL, 899, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 408), (@da + 464), (@dm + 125), (@du + 55), '확정', NULL, 1094, '2026-11-11 08:51:28'
       UNION ALL SELECT (@dp + 409), (@da + 465), (@dm + 125), (@du + 96), '확정', NULL, 935, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 410), (@da + 468), (@dm + 126), (@du + 52), '확정', NULL, 900, '2026-11-11 09:24:51'
       UNION ALL SELECT (@dp + 411), (@da + 469), (@dm + 126), (@du + 47), '확정', NULL, 970, '2026-11-09 22:07:56'
       UNION ALL SELECT (@dp + 412), (@da + 470), (@dm + 126), (@du + 11), '확정', NULL, 989, '2026-11-11 11:14:40'
       UNION ALL SELECT (@dp + 413), (@da + 471), (@dm + 127), (@du + 72), '확정', NULL, 871, '2026-11-11 00:31:52'
       UNION ALL SELECT (@dp + 414), (@da + 472), (@dm + 127), (@du + 78), '확정', NULL, 860, '2026-11-11 00:28:57'
       UNION ALL SELECT (@dp + 415), (@da + 473), (@dm + 127), (@du + 1), '확정', NULL, 1328, '2026-11-10 10:35:07'
       UNION ALL SELECT (@dp + 416), (@da + 474), (@dm + 127), (@du + 22), '확정', NULL, 903, '2026-11-10 09:13:54'
       UNION ALL SELECT (@dp + 417), (@da + 475), (@dm + 127), (@du + 3), '확정', NULL, 927, '2026-11-10 20:04:52'
       UNION ALL SELECT (@dp + 418), (@da + 476), (@dm + 127), (@du + 65), '확정', NULL, 1217, '2026-11-09 18:38:25'
       UNION ALL SELECT (@dp + 419), (@da + 477), (@dm + 127), (@du + 59), '확정', NULL, 844, '2026-11-10 11:52:01'
       UNION ALL SELECT (@dp + 420), (@da + 478), (@dm + 127), (@du + 56), '확정', NULL, 982, '2026-11-10 17:09:36'
       UNION ALL SELECT (@dp + 421), (@da + 479), (@dm + 127), (@du + 97), '확정', NULL, 996, '2026-11-10 13:55:40'
       UNION ALL SELECT (@dp + 422), (@da + 480), (@dm + 127), (@du + 35), '확정', NULL, 1048, '2026-11-11 09:06:54'
       UNION ALL SELECT (@dp + 423), (@da + 481), (@dm + 129), (@du + 48), '확정', NULL, 972, '2026-11-10 21:50:57'
       UNION ALL SELECT (@dp + 424), (@da + 482), (@dm + 129), (@du + 89), '확정', NULL, 877, '2026-11-10 04:19:12'
       UNION ALL SELECT (@dp + 425), (@da + 483), (@dm + 129), (@du + 79), '확정', NULL, 1051, '2026-11-10 00:07:16'
       UNION ALL SELECT (@dp + 426), (@da + 484), (@dm + 129), (@du + 24), '확정', NULL, 1160, '2026-11-10 22:44:14'
       UNION ALL SELECT (@dp + 427), (@da + 485), (@dm + 131), (@du + 2), '확정', NULL, 1001, '2026-11-10 18:13:52'
       UNION ALL SELECT (@dp + 428), (@da + 486), (@dm + 131), (@du + 51), '확정', NULL, 895, '2026-11-10 13:15:56'
       UNION ALL SELECT (@dp + 429), (@da + 488), (@dm + 133), (@du + 87), '확정', NULL, 980, '2026-11-11 09:30:40'
       UNION ALL SELECT (@dp + 430), (@da + 489), (@dm + 133), (@du + 14), '확정', NULL, 1063, '2026-11-10 22:35:26'
       UNION ALL SELECT (@dp + 431), (@da + 490), (@dm + 133), (@du + 26), '확정', NULL, 951, '2026-11-11 06:54:06'
       UNION ALL SELECT (@dp + 432), (@da + 491), (@dm + 133), (@du + 58), '확정', NULL, 1186, '2026-11-10 13:32:54'
       UNION ALL SELECT (@dp + 433), (@da + 492), (@dm + 133), (@du + 7), '확정', NULL, 1062, '2026-11-09 18:03:43'
       UNION ALL SELECT (@dp + 434), (@da + 493), (@dm + 133), (@du + 70), '확정', NULL, 1125, '2026-11-10 11:22:50'
       UNION ALL SELECT (@dp + 435), (@da + 494), (@dm + 134), (@du + 13), '확정', NULL, 997, '2026-11-11 09:17:54'
       UNION ALL SELECT (@dp + 436), (@da + 495), (@dm + 134), (@du + 76), '확정', NULL, 1087, '2026-11-10 19:32:18'
       UNION ALL SELECT (@dp + 437), (@da + 496), (@dm + 134), (@du + 36), '확정', NULL, 983, '2026-11-09 14:53:53'
       UNION ALL SELECT (@dp + 438), (@da + 497), (@dm + 134), (@du + 60), '확정', NULL, 961, '2026-11-10 16:44:09'
       UNION ALL SELECT (@dp + 439), (@da + 498), (@dm + 134), (@du + 63), '확정', NULL, 960, '2026-11-09 17:47:24'
       UNION ALL SELECT (@dp + 440), (@da + 501), (@dm + 135), (@du + 27), '확정', NULL, 1092, '2026-11-10 10:48:49'
       UNION ALL SELECT (@dp + 441), (@da + 502), (@dm + 135), (@du + 12), '확정', NULL, 938, '2026-11-10 21:36:52'
       UNION ALL SELECT (@dp + 442), (@da + 503), (@dm + 135), (@du + 41), '확정', NULL, 1027, '2026-11-10 17:46:27'
       UNION ALL SELECT (@dp + 443), (@da + 504), (@dm + 136), (@du + 95), '확정', NULL, 861, '2026-11-09 17:48:05'
       UNION ALL SELECT (@dp + 444), (@da + 505), (@dm + 136), (@du + 79), '확정', NULL, 1097, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 445), (@da + 506), (@dm + 136), (@du + 42), '확정', NULL, 1122, '2026-11-09 11:16:10'
       UNION ALL SELECT (@dp + 446), (@da + 507), (@dm + 136), (@du + 20), '확정', NULL, 1183, '2026-11-09 16:31:33'
       UNION ALL SELECT (@dp + 447), (@da + 508), (@dm + 136), (@du + 100), '확정', NULL, 998, '2026-11-10 02:50:16'
       UNION ALL SELECT (@dp + 448), (@da + 509), (@dm + 136), (@du + 1), '확정', NULL, 1328, '2026-11-09 11:42:36'
       UNION ALL SELECT (@dp + 449), (@da + 510), (@dm + 136), (@du + 99), '확정', NULL, 1108, '2026-11-09 14:46:01'
       UNION ALL SELECT (@dp + 450), (@da + 511), (@dm + 136), (@du + 45), '확정', NULL, 1011, '2026-11-10 15:15:02'
) v
 WHERE @demo_ready = 1;

INSERT INTO match_participant (participant_id, application_id, match_id, user_id, participation_status, match_side, entry_mmr, joined_at)
SELECT * FROM (
                 SELECT (@dp + 451) AS participant_id, (@da + 512) AS application_id, (@dm + 136) AS match_id, (@du + 80) AS user_id, '확정' AS participation_status, NULL AS match_side, 860 AS entry_mmr, '2026-11-09 12:56:17' AS joined_at
       UNION ALL SELECT (@dp + 452), (@da + 513), (@dm + 136), (@du + 51), '확정', NULL, 1050, '2026-11-11 11:47:57'
       UNION ALL SELECT (@dp + 453), (@da + 514), (@dm + 138), (@du + 93), '확정', NULL, 980, '2026-11-10 08:41:57'
       UNION ALL SELECT (@dp + 454), (@da + 515), (@dm + 138), (@du + 60), '확정', NULL, 998, '2026-11-10 03:28:18'
       UNION ALL SELECT (@dp + 455), (@da + 516), (@dm + 138), (@du + 82), '확정', NULL, 1047, '2026-11-10 13:39:41'
       UNION ALL SELECT (@dp + 456), (@da + 517), (@dm + 138), (@du + 34), '확정', NULL, 900, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 457), (@da + 518), (@dm + 140), (@du + 59), '확정', NULL, 991, '2026-11-09 23:09:14'
       UNION ALL SELECT (@dp + 458), (@da + 519), (@dm + 140), (@du + 56), '확정', NULL, 891, '2026-11-10 21:57:52'
       UNION ALL SELECT (@dp + 459), (@da + 521), (@dm + 142), (@du + 27), '확정', NULL, 1053, '2026-11-09 14:28:20'
       UNION ALL SELECT (@dp + 460), (@da + 522), (@dm + 142), (@du + 72), '확정', NULL, 1130, '2026-11-10 08:24:30'
       UNION ALL SELECT (@dp + 461), (@da + 523), (@dm + 142), (@du + 6), '확정', NULL, 964, '2026-11-10 17:20:06'
       UNION ALL SELECT (@dp + 462), (@da + 524), (@dm + 142), (@du + 88), '확정', NULL, 808, '2026-11-10 10:26:10'
       UNION ALL SELECT (@dp + 463), (@da + 525), (@dm + 142), (@du + 15), '확정', NULL, 996, '2026-11-10 06:16:41'
       UNION ALL SELECT (@dp + 464), (@da + 526), (@dm + 142), (@du + 97), '확정', NULL, 922, '2026-11-11 07:32:29'
       UNION ALL SELECT (@dp + 465), (@da + 527), (@dm + 143), (@du + 50), '확정', NULL, 1033, '2026-11-11 06:08:35'
       UNION ALL SELECT (@dp + 466), (@da + 528), (@dm + 143), (@du + 30), '확정', NULL, 897, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 467), (@da + 529), (@dm + 143), (@du + 70), '확정', NULL, 1125, '2026-11-10 06:20:06'
       UNION ALL SELECT (@dp + 468), (@da + 530), (@dm + 143), (@du + 18), '확정', NULL, 1054, '2026-11-11 02:17:37'
       UNION ALL SELECT (@dp + 469), (@da + 531), (@dm + 143), (@du + 63), '확정', NULL, 960, '2026-11-09 23:13:50'
       UNION ALL SELECT (@dp + 470), (@da + 534), (@dm + 144), (@du + 67), '확정', NULL, 970, '2026-11-09 19:32:33'
       UNION ALL SELECT (@dp + 471), (@da + 535), (@dm + 144), (@du + 73), '확정', NULL, 950, '2026-11-10 05:01:17'
       UNION ALL SELECT (@dp + 472), (@da + 536), (@dm + 144), (@du + 36), '확정', NULL, 1181, '2026-11-10 03:09:39'
       UNION ALL SELECT (@dp + 473), (@da + 537), (@dm + 145), (@du + 81), '확정', NULL, 957, '2026-11-11 05:51:36'
       UNION ALL SELECT (@dp + 474), (@da + 538), (@dm + 145), (@du + 85), '확정', NULL, 1120, '2026-11-11 05:51:36'
       UNION ALL SELECT (@dp + 475), (@da + 539), (@dm + 145), (@du + 61), '확정', NULL, 945, '2026-11-11 05:51:36'
       UNION ALL SELECT (@dp + 476), (@da + 540), (@dm + 145), (@du + 78), '확정', NULL, 860, '2026-11-11 05:51:36'
       UNION ALL SELECT (@dp + 477), (@da + 541), (@dm + 145), (@du + 91), '확정', NULL, 1059, '2026-11-11 05:51:36'
       UNION ALL SELECT (@dp + 478), (@da + 542), (@dm + 145), (@du + 74), '확정', NULL, 985, '2026-11-10 23:06:14'
       UNION ALL SELECT (@dp + 479), (@da + 543), (@dm + 145), (@du + 1), '확정', NULL, 1328, '2026-11-09 23:40:04'
       UNION ALL SELECT (@dp + 480), (@da + 544), (@dm + 145), (@du + 72), '확정', NULL, 871, '2026-11-11 04:43:08'
       UNION ALL SELECT (@dp + 481), (@da + 545), (@dm + 145), (@du + 86), '확정', NULL, 796, '2026-11-09 22:22:23'
       UNION ALL SELECT (@dp + 482), (@da + 546), (@dm + 145), (@du + 20), '확정', NULL, 1183, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 483), (@da + 550), (@dm + 146), (@du + 30), '확정', NULL, 984, '2026-11-10 13:26:31'
       UNION ALL SELECT (@dp + 484), (@da + 551), (@dm + 146), (@du + 78), '확정', NULL, 860, '2026-11-11 11:59:05'
       UNION ALL SELECT (@dp + 485), (@da + 552), (@dm + 146), (@du + 97), '확정', NULL, 996, '2026-11-11 06:03:37'
       UNION ALL SELECT (@dp + 486), (@da + 553), (@dm + 146), (@du + 61), '확정', NULL, 945, '2026-11-10 14:09:44'
       UNION ALL SELECT (@dp + 487), (@da + 554), (@dm + 147), (@du + 56), '확정', NULL, 874, '2026-11-10 03:49:44'
       UNION ALL SELECT (@dp + 488), (@da + 555), (@dm + 147), (@du + 43), '확정', NULL, 924, '2026-11-11 09:43:04'
       UNION ALL SELECT (@dp + 489), (@da + 557), (@dm + 149), (@du + 100), '확정', NULL, 855, '2026-11-09 19:44:27'
       UNION ALL SELECT (@dp + 490), (@da + 558), (@dm + 149), (@du + 63), '확정', NULL, 1020, '2026-11-10 12:58:00'
       UNION ALL SELECT (@dp + 491), (@da + 559), (@dm + 149), (@du + 67), '확정', NULL, 1084, '2026-11-10 10:17:29'
       UNION ALL SELECT (@dp + 492), (@da + 560), (@dm + 149), (@du + 66), '확정', NULL, 760, '2026-11-10 05:32:17'
       UNION ALL SELECT (@dp + 493), (@da + 565), (@dm + 151), (@du + 63), '확정', NULL, 960, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 494), (@da + 566), (@dm + 151), (@du + 66), '확정', NULL, 904, '2026-11-11 05:26:03'
       UNION ALL SELECT (@dp + 495), (@da + 567), (@dm + 153), (@du + 75), '확정', NULL, 798, '2026-11-09 20:26:46'
       UNION ALL SELECT (@dp + 496), (@da + 568), (@dm + 153), (@du + 28), '확정', NULL, 846, '2026-11-11 04:01:55'
       UNION ALL SELECT (@dp + 497), (@da + 569), (@dm + 153), (@du + 59), '확정', NULL, 1014, '2026-11-11 10:15:36'
       UNION ALL SELECT (@dp + 498), (@da + 570), (@dm + 153), (@du + 12), '확정', NULL, 938, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 499), (@da + 571), (@dm + 153), (@du + 37), '확정', NULL, 950, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 500), (@da + 572), (@dm + 153), (@du + 60), '확정', NULL, 1049, '2026-11-09 14:02:20'
       UNION ALL SELECT (@dp + 501), (@da + 573), (@dm + 154), (@du + 49), '확정', NULL, 928, '2026-11-11 02:27:10'
       UNION ALL SELECT (@dp + 502), (@da + 574), (@dm + 154), (@du + 45), '확정', NULL, 1011, '2026-11-11 02:27:10'
       UNION ALL SELECT (@dp + 503), (@da + 575), (@dm + 154), (@du + 41), '확정', NULL, 984, '2026-11-11 02:27:10'
       UNION ALL SELECT (@dp + 504), (@da + 576), (@dm + 154), (@du + 58), '확정', NULL, 1084, '2026-11-11 02:27:10'
       UNION ALL SELECT (@dp + 505), (@da + 577), (@dm + 154), (@du + 34), '확정', NULL, 969, '2026-11-11 02:27:10'
       UNION ALL SELECT (@dp + 506), (@da + 578), (@dm + 154), (@du + 56), '확정', NULL, 982, '2026-11-11 05:55:37'
       UNION ALL SELECT (@dp + 507), (@da + 579), (@dm + 154), (@du + 44), '확정', NULL, 924, '2026-11-11 10:30:14'
       UNION ALL SELECT (@dp + 508), (@da + 580), (@dm + 154), (@du + 85), '확정', NULL, 1120, '2026-11-11 06:00:32'
       UNION ALL SELECT (@dp + 509), (@da + 581), (@dm + 154), (@du + 81), '확정', NULL, 957, '2026-11-09 23:38:55'
       UNION ALL SELECT (@dp + 510), (@da + 582), (@dm + 154), (@du + 95), '확정', NULL, 861, '2026-11-09 22:29:25'
       UNION ALL SELECT (@dp + 511), (@da + 586), (@dm + 155), (@du + 61), '확정', NULL, 945, '2026-11-09 11:33:51'
       UNION ALL SELECT (@dp + 512), (@da + 587), (@dm + 155), (@du + 17), '확정', NULL, 1097, '2026-11-10 08:37:30'
       UNION ALL SELECT (@dp + 513), (@da + 588), (@dm + 155), (@du + 2), '확정', NULL, 961, '2026-11-11 10:49:39'
       UNION ALL SELECT (@dp + 514), (@da + 589), (@dm + 155), (@du + 45), '확정', NULL, 1011, '2026-11-09 20:30:58'
       UNION ALL SELECT (@dp + 515), (@da + 590), (@dm + 156), (@du + 31), '확정', NULL, 990, '2026-11-10 06:18:53'
       UNION ALL SELECT (@dp + 516), (@da + 591), (@dm + 156), (@du + 35), '확정', NULL, 824, '2026-11-10 23:05:45'
       UNION ALL SELECT (@dp + 517), (@da + 593), (@dm + 158), (@du + 89), '확정', NULL, 944, '2026-11-10 20:52:46'
       UNION ALL SELECT (@dp + 518), (@da + 594), (@dm + 158), (@du + 8), '확정', NULL, 932, '2026-11-11 02:13:36'
       UNION ALL SELECT (@dp + 519), (@da + 595), (@dm + 158), (@du + 29), '확정', NULL, 760, '2026-11-10 08:56:26'
       UNION ALL SELECT (@dp + 520), (@da + 596), (@dm + 158), (@du + 66), '확정', NULL, 760, '2026-11-09 18:11:23'
       UNION ALL SELECT (@dp + 521), (@da + 601), (@dm + 160), (@du + 46), '확정', NULL, 1209, '2026-11-10 01:08:59'
       UNION ALL SELECT (@dp + 522), (@da + 602), (@dm + 160), (@du + 40), '확정', NULL, 1002, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 523), (@da + 603), (@dm + 162), (@du + 48), '확정', NULL, 1071, '2026-11-11 08:54:55'
       UNION ALL SELECT (@dp + 524), (@da + 604), (@dm + 162), (@du + 37), '확정', NULL, 950, '2026-11-10 00:52:23'
       UNION ALL SELECT (@dp + 525), (@da + 605), (@dm + 162), (@du + 93), '확정', NULL, 902, '2026-11-11 03:15:28'
       UNION ALL SELECT (@dp + 526), (@da + 606), (@dm + 162), (@du + 83), '확정', NULL, 995, '2026-11-10 17:56:23'
       UNION ALL SELECT (@dp + 527), (@da + 607), (@dm + 162), (@du + 12), '확정', NULL, 938, '2026-11-10 08:05:10'
       UNION ALL SELECT (@dp + 528), (@da + 608), (@dm + 162), (@du + 6), '확정', NULL, 833, '2026-11-10 16:00:33'
       UNION ALL SELECT (@dp + 529), (@da + 609), (@dm + 163), (@du + 46), '확정', NULL, 1035, '2026-11-10 16:22:53'
       UNION ALL SELECT (@dp + 530), (@da + 610), (@dm + 163), (@du + 42), '확정', NULL, 1122, '2026-11-11 12:00:00'
       UNION ALL SELECT (@dp + 531), (@da + 611), (@dm + 163), (@du + 91), '확정', NULL, 1059, '2026-11-10 08:03:23'
       UNION ALL SELECT (@dp + 532), (@da + 612), (@dm + 163), (@du + 95), '확정', NULL, 861, '2026-11-09 12:50:22'
       UNION ALL SELECT (@dp + 533), (@da + 613), (@dm + 163), (@du + 35), '확정', NULL, 1048, '2026-11-09 11:19:00'
       UNION ALL SELECT (@dp + 534), (@da + 614), (@dm + 163), (@du + 99), '확정', NULL, 1108, '2026-11-09 22:38:37'
       UNION ALL SELECT (@dp + 535), (@da + 615), (@dm + 163), (@du + 65), '확정', NULL, 1217, '2026-11-10 13:33:12'
       UNION ALL SELECT (@dp + 536), (@da + 616), (@dm + 163), (@du + 4), '확정', NULL, 897, '2026-11-10 00:26:19'
       UNION ALL SELECT (@dp + 537), (@da + 617), (@dm + 163), (@du + 30), '확정', NULL, 984, '2026-11-10 06:15:25'
       UNION ALL SELECT (@dp + 538), (@da + 618), (@dm + 163), (@du + 86), '확정', NULL, 796, '2026-11-10 06:02:01'
       UNION ALL SELECT (@dp + 539), (@da + 619), (@dm + 165), (@du + 48), '확정', NULL, 972, '2026-11-10 21:10:04'
       UNION ALL SELECT (@dp + 540), (@da + 620), (@dm + 165), (@du + 53), '확정', NULL, 1177, '2026-11-10 06:15:40'
       UNION ALL SELECT (@dp + 541), (@da + 621), (@dm + 165), (@du + 8), '확정', NULL, 1084, '2026-11-09 16:18:40'
       UNION ALL SELECT (@dp + 542), (@da + 622), (@dm + 165), (@du + 33), '확정', NULL, 822, '2026-11-11 05:07:54'
       UNION ALL SELECT (@dp + 543), (@da + 623), (@dm + 167), (@du + 36), '확정', NULL, 959, '2026-11-09 22:08:00'
       UNION ALL SELECT (@dp + 544), (@da + 624), (@dm + 167), (@du + 14), '확정', NULL, 1101, '2026-11-10 18:37:15'
       UNION ALL SELECT (@dp + 545), (@da + 626), (@dm + 169), (@du + 3), '확정', NULL, 818, '2026-11-10 14:29:19'
       UNION ALL SELECT (@dp + 546), (@da + 627), (@dm + 169), (@du + 28), '확정', NULL, 889, '2026-11-10 10:06:48'
       UNION ALL SELECT (@dp + 547), (@da + 628), (@dm + 169), (@du + 19), '확정', NULL, 1015, '2026-11-09 21:04:46'
       UNION ALL SELECT (@dp + 548), (@da + 629), (@dm + 169), (@du + 27), '확정', NULL, 1053, '2026-11-10 14:05:49'
       UNION ALL SELECT (@dp + 549), (@da + 630), (@dm + 169), (@du + 29), '확정', NULL, 1142, '2026-11-10 00:35:44'
       UNION ALL SELECT (@dp + 550), (@da + 631), (@dm + 169), (@du + 7), '확정', NULL, 1062, '2026-11-10 15:43:15'
       UNION ALL SELECT (@dp + 551), (@da + 632), (@dm + 170), (@du + 67), '확정', NULL, 893, '2026-11-10 03:57:33'
       UNION ALL SELECT (@dp + 552), (@da + 633), (@dm + 170), (@du + 79), '확정', NULL, 940, '2026-11-09 17:50:31'
       UNION ALL SELECT (@dp + 553), (@da + 634), (@dm + 170), (@du + 75), '확정', NULL, 937, '2026-11-11 04:45:35'
       UNION ALL SELECT (@dp + 554), (@da + 635), (@dm + 170), (@du + 15), '확정', NULL, 996, '2026-11-09 19:04:14'
       UNION ALL SELECT (@dp + 555), (@da + 636), (@dm + 170), (@du + 9), '확정', NULL, 899, '2026-11-09 15:41:25'
       UNION ALL SELECT (@dp + 556), (@da + 639), (@dm + 171), (@du + 6), '확정', NULL, 833, '2026-11-10 08:30:47'
       UNION ALL SELECT (@dp + 557), (@da + 640), (@dm + 171), (@du + 97), '확정', NULL, 1019, '2026-11-10 19:00:38'
       UNION ALL SELECT (@dp + 558), (@da + 641), (@dm + 171), (@du + 69), '확정', NULL, 944, '2026-11-10 18:28:39'
       UNION ALL SELECT (@dp + 559), (@da + 642), (@dm + 172), (@du + 31), '확정', 'A', 920, '2026-10-29 14:00:00'
       UNION ALL SELECT (@dp + 560), (@da + 643), (@dm + 172), (@du + 74), '확정', 'A', 985, '2026-10-28 13:00:00'
       UNION ALL SELECT (@dp + 561), (@da + 644), (@dm + 172), (@du + 49), '확정', 'B', 928, '2026-10-30 12:00:00'
       UNION ALL SELECT (@dp + 562), (@da + 645), (@dm + 172), (@du + 81), '확정', 'B', 957, '2026-10-29 04:00:00'
       UNION ALL SELECT (@dp + 563), (@da + 646), (@dm + 172), (@du + 65), '확정', 'A', 1217, '2026-10-29 06:00:00'
       UNION ALL SELECT (@dp + 564), (@da + 647), (@dm + 172), (@du + 58), '확정', 'B', 1084, '2026-10-27 04:00:00'
       UNION ALL SELECT (@dp + 565), (@da + 648), (@dm + 172), (@du + 83), '확정', 'B', 775, '2026-10-30 13:00:00'
       UNION ALL SELECT (@dp + 566), (@da + 649), (@dm + 172), (@du + 42), '확정', 'B', 1122, '2026-10-30 12:00:00'
       UNION ALL SELECT (@dp + 567), (@da + 650), (@dm + 172), (@du + 80), '확정', 'A', 860, '2026-10-30 08:00:00'
       UNION ALL SELECT (@dp + 568), (@da + 651), (@dm + 172), (@du + 41), '확정', 'A', 984, '2026-10-27 08:00:00'
       UNION ALL SELECT (@dp + 569), (@da + 652), (@dm + 173), (@du + 26), '확정', 'A', 760, '2026-10-30 13:00:00'
       UNION ALL SELECT (@dp + 570), (@da + 653), (@dm + 173), (@du + 96), '확정', 'A', 1199, '2026-10-28 13:00:00'
       UNION ALL SELECT (@dp + 571), (@da + 654), (@dm + 173), (@du + 41), '확정', 'B', 849, '2026-10-29 10:00:00'
       UNION ALL SELECT (@dp + 572), (@da + 655), (@dm + 173), (@du + 48), '확정', 'B', 972, '2026-10-27 13:00:00'
       UNION ALL SELECT (@dp + 573), (@da + 656), (@dm + 174), (@du + 24), '확정', 'A', 875, '2026-10-31 21:00:00'
       UNION ALL SELECT (@dp + 574), (@da + 657), (@dm + 174), (@du + 67), '확정', 'A', 1084, '2026-10-30 20:00:00'
       UNION ALL SELECT (@dp + 575), (@da + 658), (@dm + 174), (@du + 1), '확정', 'B', 1061, '2026-10-28 15:00:00'
       UNION ALL SELECT (@dp + 576), (@da + 659), (@dm + 174), (@du + 10), '확정', 'B', 926, '2026-10-30 14:00:00'
       UNION ALL SELECT (@dp + 577), (@da + 660), (@dm + 175), (@du + 99), '확정', 'B', 1052, '2026-10-31 16:00:00'
       UNION ALL SELECT (@dp + 578), (@da + 661), (@dm + 175), (@du + 72), '확정', 'A', 1130, '2026-10-28 10:00:00'
       UNION ALL SELECT (@dp + 579), (@da + 662), (@dm + 175), (@du + 15), '확정', 'B', 996, '2026-10-28 22:00:00'
       UNION ALL SELECT (@dp + 580), (@da + 663), (@dm + 175), (@du + 96), '확정', 'A', 935, '2026-10-29 15:00:00'
       UNION ALL SELECT (@dp + 581), (@da + 664), (@dm + 175), (@du + 30), '확정', 'B', 897, '2026-10-29 16:00:00'
       UNION ALL SELECT (@dp + 582), (@da + 665), (@dm + 175), (@du + 26), '확정', 'A', 951, '2026-10-30 20:00:00'
       UNION ALL SELECT (@dp + 583), (@da + 666), (@dm + 176), (@du + 75), '확정', 'B', 798, '2026-10-29 12:00:00'
       UNION ALL SELECT (@dp + 584), (@da + 667), (@dm + 176), (@du + 40), '확정', 'A', 963, '2026-10-30 14:00:00'
       UNION ALL SELECT (@dp + 585), (@da + 668), (@dm + 176), (@du + 86), '확정', 'B', 990, '2026-11-01 17:00:00'
       UNION ALL SELECT (@dp + 586), (@da + 669), (@dm + 176), (@du + 15), '확정', 'A', 870, '2026-10-30 13:00:00'
       UNION ALL SELECT (@dp + 587), (@da + 670), (@dm + 176), (@du + 22), '확정', 'A', 1059, '2026-10-31 09:00:00'
       UNION ALL SELECT (@dp + 588), (@da + 671), (@dm + 176), (@du + 47), '확정', 'B', 970, '2026-11-01 10:00:00'
       UNION ALL SELECT (@dp + 589), (@da + 672), (@dm + 177), (@du + 95), '확정', 'A', 861, '2026-11-01 19:00:00'
       UNION ALL SELECT (@dp + 590), (@da + 673), (@dm + 177), (@du + 34), '확정', 'A', 969, '2026-11-02 00:00:00'
       UNION ALL SELECT (@dp + 591), (@da + 674), (@dm + 177), (@du + 58), '확정', 'B', 1084, '2026-11-01 19:00:00'
       UNION ALL SELECT (@dp + 592), (@da + 675), (@dm + 177), (@du + 78), '확정', 'A', 860, '2026-10-30 18:00:00'
       UNION ALL SELECT (@dp + 593), (@da + 676), (@dm + 177), (@du + 97), '확정', 'A', 996, '2026-10-29 23:00:00'
       UNION ALL SELECT (@dp + 594), (@da + 677), (@dm + 177), (@du + 61), '확정', 'B', 945, '2026-10-29 10:00:00'
       UNION ALL SELECT (@dp + 595), (@da + 678), (@dm + 177), (@du + 51), '확정', 'B', 1050, '2026-10-29 09:00:00'
       UNION ALL SELECT (@dp + 596), (@da + 679), (@dm + 177), (@du + 26), '확정', 'B', 872, '2026-10-31 16:00:00'
       UNION ALL SELECT (@dp + 597), (@da + 680), (@dm + 177), (@du + 54), '확정', 'B', 848, '2026-10-29 23:00:00'
       UNION ALL SELECT (@dp + 598), (@da + 681), (@dm + 177), (@du + 79), '확정', 'A', 1097, '2026-11-01 14:00:00'
       UNION ALL SELECT (@dp + 599), (@da + 682), (@dm + 178), (@du + 56), '확정', 'A', 874, '2026-10-30 10:00:00'
       UNION ALL SELECT (@dp + 600), (@da + 683), (@dm + 178), (@du + 16), '확정', 'B', 979, '2026-10-30 14:00:00'
) v
 WHERE @demo_ready = 1;

INSERT INTO match_participant (participant_id, application_id, match_id, user_id, participation_status, match_side, entry_mmr, joined_at)
SELECT * FROM (
                 SELECT (@dp + 601) AS participant_id, (@da + 684) AS application_id, (@dm + 178) AS match_id, (@du + 30) AS user_id, '확정' AS participation_status, 'B' AS match_side, 956 AS entry_mmr, '2026-11-02 18:00:00' AS joined_at
       UNION ALL SELECT (@dp + 602), (@da + 685), (@dm + 178), (@du + 77), '확정', 'A', 1097, '2026-11-02 19:00:00'
       UNION ALL SELECT (@dp + 603), (@da + 686), (@dm + 179), (@du + 15), '확정', 'A', 760, '2026-10-31 22:00:00'
       UNION ALL SELECT (@dp + 604), (@da + 687), (@dm + 179), (@du + 59), '확정', 'B', 991, '2026-10-31 17:00:00'
       UNION ALL SELECT (@dp + 605), (@da + 688), (@dm + 179), (@du + 39), '확정', 'A', 1075, '2026-11-02 12:00:00'
       UNION ALL SELECT (@dp + 606), (@da + 689), (@dm + 179), (@du + 88), '확정', 'B', 816, '2026-10-30 10:00:00'
       UNION ALL SELECT (@dp + 607), (@da + 690), (@dm + 180), (@du + 7), '확정', 'A', 1062, '2026-11-02 17:00:00'
       UNION ALL SELECT (@dp + 608), (@da + 691), (@dm + 180), (@du + 6), '확정', 'B', 964, '2026-11-02 14:00:00'
       UNION ALL SELECT (@dp + 609), (@da + 692), (@dm + 180), (@du + 96), '확정', 'A', 935, '2026-11-01 10:00:00'
       UNION ALL SELECT (@dp + 610), (@da + 693), (@dm + 180), (@du + 30), '확정', 'B', 897, '2026-10-31 14:00:00'
       UNION ALL SELECT (@dp + 611), (@da + 694), (@dm + 180), (@du + 63), '확정', 'A', 960, '2026-11-03 14:00:00'
       UNION ALL SELECT (@dp + 612), (@da + 695), (@dm + 180), (@du + 60), '확정', 'B', 961, '2026-11-01 21:00:00'
       UNION ALL SELECT (@dp + 613), (@da + 696), (@dm + 181), (@du + 61), '확정', 'A', 931, '2026-10-31 22:00:00'
       UNION ALL SELECT (@dp + 614), (@da + 697), (@dm + 181), (@du + 42), '확정', 'A', 854, '2026-11-03 22:00:00'
       UNION ALL SELECT (@dp + 615), (@da + 698), (@dm + 181), (@du + 89), '확정', 'A', 1064, '2026-11-01 09:00:00'
       UNION ALL SELECT (@dp + 616), (@da + 699), (@dm + 181), (@du + 23), '확정', 'B', 849, '2026-11-01 18:00:00'
       UNION ALL SELECT (@dp + 617), (@da + 700), (@dm + 181), (@du + 12), '확정', 'B', 938, '2026-11-01 19:00:00'
       UNION ALL SELECT (@dp + 618), (@da + 701), (@dm + 181), (@du + 67), '확정', 'B', 970, '2026-11-02 19:00:00'
       UNION ALL SELECT (@dp + 619), (@da + 702), (@dm + 182), (@du + 85), '확정', 'A', 1120, '2026-11-04 19:00:00'
       UNION ALL SELECT (@dp + 620), (@da + 703), (@dm + 182), (@du + 38), '확정', 'A', 951, '2026-11-03 19:00:00'
       UNION ALL SELECT (@dp + 621), (@da + 704), (@dm + 182), (@du + 99), '확정', 'B', 1108, '2026-11-01 14:00:00'
       UNION ALL SELECT (@dp + 622), (@da + 705), (@dm + 182), (@du + 11), '확정', 'B', 1082, '2026-11-04 22:00:00'
       UNION ALL SELECT (@dp + 623), (@da + 706), (@dm + 182), (@du + 91), '확정', 'A', 1059, '2026-11-03 21:00:00'
       UNION ALL SELECT (@dp + 624), (@da + 707), (@dm + 182), (@du + 90), '확정', 'B', 987, '2026-11-04 23:00:00'
       UNION ALL SELECT (@dp + 625), (@da + 708), (@dm + 182), (@du + 77), '확정', 'A', 931, '2026-11-01 18:00:00'
       UNION ALL SELECT (@dp + 626), (@da + 709), (@dm + 182), (@du + 78), '확정', 'B', 860, '2026-11-02 21:00:00'
       UNION ALL SELECT (@dp + 627), (@da + 710), (@dm + 182), (@du + 82), '확정', 'B', 991, '2026-11-04 20:00:00'
       UNION ALL SELECT (@dp + 628), (@da + 711), (@dm + 182), (@du + 35), '확정', 'A', 1048, '2026-11-02 20:00:00'
       UNION ALL SELECT (@dp + 629), (@da + 712), (@dm + 183), (@du + 31), '확정', 'B', 990, '2026-11-02 19:00:00'
       UNION ALL SELECT (@dp + 630), (@da + 713), (@dm + 183), (@du + 68), '확정', 'A', 1014, '2026-11-04 13:00:00'
       UNION ALL SELECT (@dp + 631), (@da + 714), (@dm + 183), (@du + 26), '확정', 'A', 760, '2026-11-02 14:00:00'
       UNION ALL SELECT (@dp + 632), (@da + 715), (@dm + 183), (@du + 28), '확정', 'B', 1008, '2026-11-03 17:00:00'
       UNION ALL SELECT (@dp + 633), (@da + 716), (@dm + 184), (@du + 100), '확정', 'A', 855, '2026-11-04 04:00:00'
       UNION ALL SELECT (@dp + 634), (@da + 717), (@dm + 184), (@du + 2), '확정', 'A', 1001, '2026-11-05 10:00:00'
       UNION ALL SELECT (@dp + 635), (@da + 718), (@dm + 184), (@du + 51), '확정', 'B', 895, '2026-11-03 02:00:00'
       UNION ALL SELECT (@dp + 636), (@da + 719), (@dm + 184), (@du + 10), '확정', 'B', 926, '2026-11-03 16:00:00'
       UNION ALL SELECT (@dp + 637), (@da + 720), (@dm + 185), (@du + 39), '확정', 'A', 983, '2026-11-02 11:00:00'
       UNION ALL SELECT (@dp + 638), (@da + 721), (@dm + 185), (@du + 50), '확정', 'B', 1033, '2026-11-02 10:00:00'
       UNION ALL SELECT (@dp + 639), (@da + 722), (@dm + 185), (@du + 76), '확정', 'A', 1087, '2026-11-03 09:00:00'
       UNION ALL SELECT (@dp + 640), (@da + 723), (@dm + 185), (@du + 26), '확정', 'A', 951, '2026-11-04 10:00:00'
       UNION ALL SELECT (@dp + 641), (@da + 724), (@dm + 185), (@du + 67), '확정', 'B', 893, '2026-11-04 16:00:00'
       UNION ALL SELECT (@dp + 642), (@da + 725), (@dm + 185), (@du + 14), '확정', 'B', 1063, '2026-11-04 15:00:00'
       UNION ALL SELECT (@dp + 643), (@da + 726), (@dm + 186), (@du + 81), '확정', 'B', 820, '2026-11-05 07:00:00'
       UNION ALL SELECT (@dp + 644), (@da + 727), (@dm + 186), (@du + 44), '확정', 'B', 991, '2026-11-04 12:00:00'
       UNION ALL SELECT (@dp + 645), (@da + 728), (@dm + 186), (@du + 89), '확정', 'A', 1064, '2026-11-06 06:00:00'
       UNION ALL SELECT (@dp + 646), (@da + 729), (@dm + 186), (@du + 14), '확정', 'A', 845, '2026-11-03 10:00:00'
       UNION ALL SELECT (@dp + 647), (@da + 730), (@dm + 186), (@du + 59), '확정', 'B', 1014, '2026-11-05 06:00:00'
       UNION ALL SELECT (@dp + 648), (@da + 731), (@dm + 186), (@du + 11), '확정', 'A', 989, '2026-11-05 11:00:00'
       UNION ALL SELECT (@dp + 649), (@da + 732), (@dm + 187), (@du + 49), '확정', 'B', 928, '2026-11-04 16:00:00'
       UNION ALL SELECT (@dp + 650), (@da + 733), (@dm + 187), (@du + 69), '확정', 'A', 1010, '2026-11-04 11:00:00'
       UNION ALL SELECT (@dp + 651), (@da + 734), (@dm + 187), (@du + 46), '확정', 'B', 1035, '2026-11-03 10:00:00'
       UNION ALL SELECT (@dp + 652), (@da + 735), (@dm + 187), (@du + 54), '확정', 'B', 848, '2026-11-03 11:00:00'
       UNION ALL SELECT (@dp + 653), (@da + 736), (@dm + 187), (@du + 19), '확정', 'A', 1173, '2026-11-04 19:00:00'
       UNION ALL SELECT (@dp + 654), (@da + 737), (@dm + 187), (@du + 91), '확정', 'B', 1059, '2026-11-04 12:00:00'
       UNION ALL SELECT (@dp + 655), (@da + 738), (@dm + 187), (@du + 44), '확정', 'A', 924, '2026-11-05 19:00:00'
       UNION ALL SELECT (@dp + 656), (@da + 739), (@dm + 187), (@du + 100), '확정', 'A', 998, '2026-11-05 17:00:00'
       UNION ALL SELECT (@dp + 657), (@da + 740), (@dm + 187), (@du + 72), '확정', 'A', 871, '2026-11-04 10:00:00'
       UNION ALL SELECT (@dp + 658), (@da + 741), (@dm + 187), (@du + 81), '확정', 'B', 957, '2026-11-05 11:00:00'
       UNION ALL SELECT (@dp + 659), (@da + 742), (@dm + 188), (@du + 64), '확정', 'B', 973, '2026-11-07 17:00:00'
       UNION ALL SELECT (@dp + 660), (@da + 743), (@dm + 188), (@du + 99), '확정', 'A', 1050, '2026-11-06 18:00:00'
       UNION ALL SELECT (@dp + 661), (@da + 744), (@dm + 188), (@du + 93), '확정', 'B', 980, '2026-11-05 17:00:00'
       UNION ALL SELECT (@dp + 662), (@da + 745), (@dm + 188), (@du + 48), '확정', 'A', 972, '2026-11-06 19:00:00'
       UNION ALL SELECT (@dp + 663), (@da + 746), (@dm + 189), (@du + 36), '확정', 'B', 959, '2026-11-05 13:00:00'
       UNION ALL SELECT (@dp + 664), (@da + 747), (@dm + 189), (@du + 67), '확정', 'A', 1084, '2026-11-06 17:00:00'
       UNION ALL SELECT (@dp + 665), (@da + 748), (@dm + 189), (@du + 43), '확정', 'B', 962, '2026-11-04 18:00:00'
       UNION ALL SELECT (@dp + 666), (@da + 749), (@dm + 189), (@du + 8), '확정', 'A', 932, '2026-11-05 17:00:00'
) v
 WHERE @demo_ready = 1;

UPDATE social_match SET captain_a_participant_id = (@dp + 563), captain_b_participant_id = (@dp + 566) WHERE @demo_ready = 1 AND match_id = (@dm + 172);
UPDATE social_match SET captain_a_participant_id = (@dp + 570), captain_b_participant_id = (@dp + 572) WHERE @demo_ready = 1 AND match_id = (@dm + 173);
UPDATE social_match SET captain_a_participant_id = (@dp + 574), captain_b_participant_id = (@dp + 575) WHERE @demo_ready = 1 AND match_id = (@dm + 174);
UPDATE social_match SET captain_a_participant_id = (@dp + 578), captain_b_participant_id = (@dp + 577) WHERE @demo_ready = 1 AND match_id = (@dm + 175);
UPDATE social_match SET captain_a_participant_id = (@dp + 587), captain_b_participant_id = (@dp + 585) WHERE @demo_ready = 1 AND match_id = (@dm + 176);
UPDATE social_match SET captain_a_participant_id = (@dp + 598), captain_b_participant_id = (@dp + 591) WHERE @demo_ready = 1 AND match_id = (@dm + 177);
UPDATE social_match SET captain_a_participant_id = (@dp + 602), captain_b_participant_id = (@dp + 600) WHERE @demo_ready = 1 AND match_id = (@dm + 178);
UPDATE social_match SET captain_a_participant_id = (@dp + 605), captain_b_participant_id = (@dp + 604) WHERE @demo_ready = 1 AND match_id = (@dm + 179);
UPDATE social_match SET captain_a_participant_id = (@dp + 607), captain_b_participant_id = (@dp + 608) WHERE @demo_ready = 1 AND match_id = (@dm + 180);
UPDATE social_match SET captain_a_participant_id = (@dp + 615), captain_b_participant_id = (@dp + 618) WHERE @demo_ready = 1 AND match_id = (@dm + 181);
UPDATE social_match SET captain_a_participant_id = (@dp + 619), captain_b_participant_id = (@dp + 621) WHERE @demo_ready = 1 AND match_id = (@dm + 182);
UPDATE social_match SET captain_a_participant_id = (@dp + 630), captain_b_participant_id = (@dp + 632) WHERE @demo_ready = 1 AND match_id = (@dm + 183);
UPDATE social_match SET captain_a_participant_id = (@dp + 634), captain_b_participant_id = (@dp + 636) WHERE @demo_ready = 1 AND match_id = (@dm + 184);
UPDATE social_match SET captain_a_participant_id = (@dp + 639), captain_b_participant_id = (@dp + 642) WHERE @demo_ready = 1 AND match_id = (@dm + 185);
UPDATE social_match SET captain_a_participant_id = (@dp + 645), captain_b_participant_id = (@dp + 647) WHERE @demo_ready = 1 AND match_id = (@dm + 186);
UPDATE social_match SET captain_a_participant_id = (@dp + 653), captain_b_participant_id = (@dp + 654) WHERE @demo_ready = 1 AND match_id = (@dm + 187);
UPDATE social_match SET captain_a_participant_id = (@dp + 660), captain_b_participant_id = (@dp + 661) WHERE @demo_ready = 1 AND match_id = (@dm + 188);
UPDATE social_match SET captain_a_participant_id = (@dp + 664), captain_b_participant_id = (@dp + 665) WHERE @demo_ready = 1 AND match_id = (@dm + 189);

-- 6. 지난 경기 결과 제출·평가
INSERT INTO match_result_submission (match_id, captain_participant_id, reported_result, submission_status, submitted_at)
SELECT * FROM (
                 SELECT (@dm + 172) AS match_id, (@dp + 563) AS captain_participant_id, 'A_WIN' AS reported_result, 'CONFIRMED' AS submission_status, '2026-11-01 12:40:00' AS submitted_at
       UNION ALL SELECT (@dm + 172), (@dp + 566), 'A_WIN', 'CONFIRMED', '2026-11-01 13:10:00'
       UNION ALL SELECT (@dm + 173), (@dp + 570), 'DRAW', 'CONFIRMED', '2026-11-01 16:40:00'
       UNION ALL SELECT (@dm + 173), (@dp + 572), 'DRAW', 'CONFIRMED', '2026-11-01 17:10:00'
       UNION ALL SELECT (@dm + 174), (@dp + 574), 'A_WIN', 'CONFIRMED', '2026-11-02 20:40:00'
       UNION ALL SELECT (@dm + 174), (@dp + 575), 'A_WIN', 'CONFIRMED', '2026-11-02 21:10:00'
       UNION ALL SELECT (@dm + 175), (@dp + 578), 'A_WIN', 'CONFIRMED', '2026-11-02 20:40:00'
       UNION ALL SELECT (@dm + 175), (@dp + 577), 'A_WIN', 'CONFIRMED', '2026-11-02 21:10:00'
       UNION ALL SELECT (@dm + 176), (@dp + 587), 'A_WIN', 'CONFIRMED', '2026-11-03 20:40:00'
       UNION ALL SELECT (@dm + 176), (@dp + 585), 'A_WIN', 'CONFIRMED', '2026-11-03 21:10:00'
       UNION ALL SELECT (@dm + 177), (@dp + 598), 'A_WIN', 'CONFIRMED', '2026-11-03 20:40:00'
       UNION ALL SELECT (@dm + 177), (@dp + 591), 'A_WIN', 'CONFIRMED', '2026-11-03 21:10:00'
       UNION ALL SELECT (@dm + 178), (@dp + 602), 'A_WIN', 'CONFIRMED', '2026-11-04 20:40:00'
       UNION ALL SELECT (@dm + 178), (@dp + 600), 'A_WIN', 'CONFIRMED', '2026-11-04 21:10:00'
       UNION ALL SELECT (@dm + 179), (@dp + 605), 'B_WIN', 'CONFIRMED', '2026-11-04 20:40:00'
       UNION ALL SELECT (@dm + 179), (@dp + 604), 'B_WIN', 'CONFIRMED', '2026-11-04 21:10:00'
       UNION ALL SELECT (@dm + 180), (@dp + 607), 'A_WIN', 'CONFIRMED', '2026-11-05 20:40:00'
       UNION ALL SELECT (@dm + 180), (@dp + 608), 'A_WIN', 'CONFIRMED', '2026-11-05 21:10:00'
       UNION ALL SELECT (@dm + 181), (@dp + 615), 'A_WIN', 'CONFIRMED', '2026-11-05 20:40:00'
       UNION ALL SELECT (@dm + 181), (@dp + 618), 'A_WIN', 'CONFIRMED', '2026-11-05 21:10:00'
       UNION ALL SELECT (@dm + 182), (@dp + 619), 'A_WIN', 'CONFIRMED', '2026-11-06 20:40:00'
       UNION ALL SELECT (@dm + 182), (@dp + 621), 'A_WIN', 'CONFIRMED', '2026-11-06 21:10:00'
       UNION ALL SELECT (@dm + 183), (@dp + 630), 'DRAW', 'CONFIRMED', '2026-11-06 20:40:00'
       UNION ALL SELECT (@dm + 183), (@dp + 632), 'DRAW', 'CONFIRMED', '2026-11-06 21:10:00'
       UNION ALL SELECT (@dm + 184), (@dp + 634), 'A_WIN', 'CONFIRMED', '2026-11-07 12:40:00'
       UNION ALL SELECT (@dm + 184), (@dp + 636), 'A_WIN', 'CONFIRMED', '2026-11-07 13:10:00'
       UNION ALL SELECT (@dm + 185), (@dp + 639), 'A_WIN', 'CONFIRMED', '2026-11-07 16:40:00'
       UNION ALL SELECT (@dm + 185), (@dp + 642), 'A_WIN', 'CONFIRMED', '2026-11-07 17:10:00'
       UNION ALL SELECT (@dm + 186), (@dp + 645), 'B_WIN', 'CONFIRMED', '2026-11-08 12:40:00'
       UNION ALL SELECT (@dm + 186), (@dp + 647), 'B_WIN', 'CONFIRMED', '2026-11-08 13:10:00'
       UNION ALL SELECT (@dm + 187), (@dp + 653), 'B_WIN', 'CONFIRMED', '2026-11-08 16:40:00'
       UNION ALL SELECT (@dm + 187), (@dp + 654), 'B_WIN', 'CONFIRMED', '2026-11-08 17:10:00'
       UNION ALL SELECT (@dm + 188), (@dp + 660), 'DRAW', 'CONFIRMED', '2026-11-09 20:40:00'
       UNION ALL SELECT (@dm + 188), (@dp + 661), 'DRAW', 'CONFIRMED', '2026-11-09 21:10:00'
       UNION ALL SELECT (@dm + 189), (@dp + 664), 'B_WIN', 'CONFIRMED', '2026-11-09 20:40:00'
       UNION ALL SELECT (@dm + 189), (@dp + 665), 'B_WIN', 'CONFIRMED', '2026-11-09 21:10:00'
) v
 WHERE @demo_ready = 1;

INSERT INTO match_evaluation (evaluation_id, match_id, sport_id, evaluator_participant_id, target_participant_id, overall_score, scale_max, status, submitted_at)
SELECT * FROM (
                 SELECT (@de + 1) AS evaluation_id, (@dm + 172) AS match_id, @ds_futsal AS sport_id, (@dp + 563) AS evaluator_participant_id, (@dp + 566) AS target_participant_id, 7.00 AS overall_score, 10 AS scale_max, '제출 완료' AS status, '2026-11-01 13:10:00' AS submitted_at
       UNION ALL SELECT (@de + 2), (@dm + 172), @ds_futsal, (@dp + 563), (@dp + 564), 6.33, 10, '제출 완료', '2026-11-01 13:10:00'
       UNION ALL SELECT (@de + 3), (@dm + 172), @ds_futsal, (@dp + 563), (@dp + 560), 5.33, 10, '제출 완료', '2026-11-01 13:10:00'
       UNION ALL SELECT (@de + 4), (@dm + 172), @ds_futsal, (@dp + 563), (@dp + 568), 4.67, 10, '제출 완료', '2026-11-01 13:10:00'
       UNION ALL SELECT (@de + 5), (@dm + 172), @ds_futsal, (@dp + 563), (@dp + 562), 6.33, 10, '제출 완료', '2026-11-01 13:10:00'
       UNION ALL SELECT (@de + 6), (@dm + 172), @ds_futsal, (@dp + 563), (@dp + 561), 4.67, 10, '제출 완료', '2026-11-01 13:10:00'
       UNION ALL SELECT (@de + 7), (@dm + 172), @ds_futsal, (@dp + 563), (@dp + 559), 5.33, 10, '제출 완료', '2026-11-01 13:10:00'
       UNION ALL SELECT (@de + 8), (@dm + 172), @ds_futsal, (@dp + 563), (@dp + 567), 5.67, 10, '제출 완료', '2026-11-01 13:10:00'
       UNION ALL SELECT (@de + 9), (@dm + 172), @ds_futsal, (@dp + 563), (@dp + 565), 5.33, 10, '제출 완료', '2026-11-01 13:10:00'
       UNION ALL SELECT (@de + 10), (@dm + 172), @ds_futsal, (@dp + 566), (@dp + 563), 6.33, 10, '제출 완료', '2026-11-01 13:30:00'
       UNION ALL SELECT (@de + 11), (@dm + 172), @ds_futsal, (@dp + 566), (@dp + 564), 6.33, 10, '제출 완료', '2026-11-01 13:30:00'
       UNION ALL SELECT (@de + 12), (@dm + 172), @ds_futsal, (@dp + 566), (@dp + 560), 4.67, 10, '제출 완료', '2026-11-01 13:30:00'
       UNION ALL SELECT (@de + 13), (@dm + 172), @ds_futsal, (@dp + 566), (@dp + 568), 5.33, 10, '제출 완료', '2026-11-01 13:30:00'
       UNION ALL SELECT (@de + 14), (@dm + 172), @ds_futsal, (@dp + 566), (@dp + 562), 6.00, 10, '제출 완료', '2026-11-01 13:30:00'
       UNION ALL SELECT (@de + 15), (@dm + 172), @ds_futsal, (@dp + 566), (@dp + 561), 5.00, 10, '제출 완료', '2026-11-01 13:30:00'
       UNION ALL SELECT (@de + 16), (@dm + 172), @ds_futsal, (@dp + 566), (@dp + 559), 3.67, 10, '제출 완료', '2026-11-01 13:30:00'
       UNION ALL SELECT (@de + 17), (@dm + 172), @ds_futsal, (@dp + 566), (@dp + 567), 5.00, 10, '제출 완료', '2026-11-01 13:30:00'
       UNION ALL SELECT (@de + 18), (@dm + 172), @ds_futsal, (@dp + 566), (@dp + 565), 4.67, 10, '제출 완료', '2026-11-01 13:30:00'
       UNION ALL SELECT (@de + 19), (@dm + 173), @ds_tennis, (@dp + 570), (@dp + 572), 6.33, 10, '제출 완료', '2026-11-01 17:10:00'
       UNION ALL SELECT (@de + 20), (@dm + 173), @ds_tennis, (@dp + 570), (@dp + 571), 5.67, 10, '제출 완료', '2026-11-01 17:10:00'
       UNION ALL SELECT (@de + 21), (@dm + 173), @ds_tennis, (@dp + 570), (@dp + 569), 3.67, 10, '제출 완료', '2026-11-01 17:10:00'
       UNION ALL SELECT (@de + 22), (@dm + 173), @ds_tennis, (@dp + 572), (@dp + 570), 6.33, 10, '제출 완료', '2026-11-01 17:30:00'
       UNION ALL SELECT (@de + 23), (@dm + 173), @ds_tennis, (@dp + 572), (@dp + 571), 5.00, 10, '제출 완료', '2026-11-01 17:30:00'
       UNION ALL SELECT (@de + 24), (@dm + 173), @ds_tennis, (@dp + 572), (@dp + 569), 3.67, 10, '제출 완료', '2026-11-01 17:30:00'
       UNION ALL SELECT (@de + 25), (@dm + 174), @ds_badminton, (@dp + 574), (@dp + 575), 6.00, 10, '제출 완료', '2026-11-02 21:10:00'
       UNION ALL SELECT (@de + 26), (@dm + 174), @ds_badminton, (@dp + 574), (@dp + 576), 5.33, 10, '제출 완료', '2026-11-02 21:10:00'
       UNION ALL SELECT (@de + 27), (@dm + 174), @ds_badminton, (@dp + 574), (@dp + 573), 5.00, 10, '제출 완료', '2026-11-02 21:10:00'
       UNION ALL SELECT (@de + 28), (@dm + 174), @ds_badminton, (@dp + 575), (@dp + 574), 6.33, 10, '제출 완료', '2026-11-02 21:30:00'
       UNION ALL SELECT (@de + 29), (@dm + 174), @ds_badminton, (@dp + 575), (@dp + 576), 4.67, 10, '제출 완료', '2026-11-02 21:30:00'
       UNION ALL SELECT (@de + 30), (@dm + 174), @ds_badminton, (@dp + 575), (@dp + 573), 4.67, 10, '제출 완료', '2026-11-02 21:30:00'
       UNION ALL SELECT (@de + 31), (@dm + 175), @ds_basketball, (@dp + 578), (@dp + 577), 6.00, 10, '제출 완료', '2026-11-02 21:10:00'
       UNION ALL SELECT (@de + 32), (@dm + 175), @ds_basketball, (@dp + 578), (@dp + 579), 6.00, 10, '제출 완료', '2026-11-02 21:10:00'
       UNION ALL SELECT (@de + 33), (@dm + 175), @ds_basketball, (@dp + 578), (@dp + 582), 5.67, 10, '제출 완료', '2026-11-02 21:10:00'
       UNION ALL SELECT (@de + 34), (@dm + 175), @ds_basketball, (@dp + 578), (@dp + 580), 5.33, 10, '제출 완료', '2026-11-02 21:10:00'
       UNION ALL SELECT (@de + 35), (@dm + 175), @ds_basketball, (@dp + 578), (@dp + 581), 5.00, 10, '제출 완료', '2026-11-02 21:10:00'
       UNION ALL SELECT (@de + 36), (@dm + 175), @ds_basketball, (@dp + 577), (@dp + 578), 6.00, 10, '제출 완료', '2026-11-02 21:30:00'
       UNION ALL SELECT (@de + 37), (@dm + 175), @ds_basketball, (@dp + 577), (@dp + 579), 5.67, 10, '제출 완료', '2026-11-02 21:30:00'
       UNION ALL SELECT (@de + 38), (@dm + 175), @ds_basketball, (@dp + 577), (@dp + 582), 5.67, 10, '제출 완료', '2026-11-02 21:30:00'
       UNION ALL SELECT (@de + 39), (@dm + 175), @ds_basketball, (@dp + 577), (@dp + 580), 4.33, 10, '제출 완료', '2026-11-02 21:30:00'
       UNION ALL SELECT (@de + 40), (@dm + 175), @ds_basketball, (@dp + 577), (@dp + 581), 6.00, 10, '제출 완료', '2026-11-02 21:30:00'
       UNION ALL SELECT (@de + 41), (@dm + 176), @ds_jokgu, (@dp + 587), (@dp + 585), 5.00, 10, '제출 완료', '2026-11-03 21:10:00'
       UNION ALL SELECT (@de + 42), (@dm + 176), @ds_jokgu, (@dp + 587), (@dp + 588), 6.33, 10, '제출 완료', '2026-11-03 21:10:00'
       UNION ALL SELECT (@de + 43), (@dm + 176), @ds_jokgu, (@dp + 587), (@dp + 584), 4.00, 10, '제출 완료', '2026-11-03 21:10:00'
       UNION ALL SELECT (@de + 44), (@dm + 176), @ds_jokgu, (@dp + 587), (@dp + 586), 4.00, 10, '제출 완료', '2026-11-03 21:10:00'
       UNION ALL SELECT (@de + 45), (@dm + 176), @ds_jokgu, (@dp + 587), (@dp + 583), 5.00, 10, '제출 완료', '2026-11-03 21:10:00'
       UNION ALL SELECT (@de + 46), (@dm + 176), @ds_jokgu, (@dp + 585), (@dp + 587), 5.67, 10, '제출 완료', '2026-11-03 21:30:00'
       UNION ALL SELECT (@de + 47), (@dm + 176), @ds_jokgu, (@dp + 585), (@dp + 588), 4.33, 10, '제출 완료', '2026-11-03 21:30:00'
       UNION ALL SELECT (@de + 48), (@dm + 176), @ds_jokgu, (@dp + 585), (@dp + 584), 5.67, 10, '제출 완료', '2026-11-03 21:30:00'
       UNION ALL SELECT (@de + 49), (@dm + 176), @ds_jokgu, (@dp + 585), (@dp + 586), 4.00, 10, '제출 완료', '2026-11-03 21:30:00'
       UNION ALL SELECT (@de + 50), (@dm + 176), @ds_jokgu, (@dp + 585), (@dp + 583), 4.00, 10, '제출 완료', '2026-11-03 21:30:00'
       UNION ALL SELECT (@de + 51), (@dm + 177), @ds_futsal, (@dp + 598), (@dp + 591), 7.00, 10, '제출 완료', '2026-11-03 21:10:00'
       UNION ALL SELECT (@de + 52), (@dm + 177), @ds_futsal, (@dp + 598), (@dp + 595), 7.00, 10, '제출 완료', '2026-11-03 21:10:00'
       UNION ALL SELECT (@de + 53), (@dm + 177), @ds_futsal, (@dp + 598), (@dp + 593), 6.00, 10, '제출 완료', '2026-11-03 21:10:00'
       UNION ALL SELECT (@de + 54), (@dm + 177), @ds_futsal, (@dp + 598), (@dp + 590), 4.00, 10, '제출 완료', '2026-11-03 21:10:00'
       UNION ALL SELECT (@de + 55), (@dm + 177), @ds_futsal, (@dp + 598), (@dp + 594), 6.00, 10, '제출 완료', '2026-11-03 21:10:00'
       UNION ALL SELECT (@de + 56), (@dm + 177), @ds_futsal, (@dp + 598), (@dp + 596), 5.33, 10, '제출 완료', '2026-11-03 21:10:00'
       UNION ALL SELECT (@de + 57), (@dm + 177), @ds_futsal, (@dp + 598), (@dp + 589), 4.33, 10, '제출 완료', '2026-11-03 21:10:00'
       UNION ALL SELECT (@de + 58), (@dm + 177), @ds_futsal, (@dp + 598), (@dp + 592), 4.33, 10, '제출 완료', '2026-11-03 21:10:00'
       UNION ALL SELECT (@de + 59), (@dm + 177), @ds_futsal, (@dp + 598), (@dp + 597), 3.00, 10, '제출 완료', '2026-11-03 21:10:00'
       UNION ALL SELECT (@de + 60), (@dm + 177), @ds_futsal, (@dp + 591), (@dp + 598), 6.00, 10, '제출 완료', '2026-11-03 21:30:00'
       UNION ALL SELECT (@de + 61), (@dm + 177), @ds_futsal, (@dp + 591), (@dp + 595), 7.00, 10, '제출 완료', '2026-11-03 21:30:00'
       UNION ALL SELECT (@de + 62), (@dm + 177), @ds_futsal, (@dp + 591), (@dp + 593), 4.67, 10, '제출 완료', '2026-11-03 21:30:00'
       UNION ALL SELECT (@de + 63), (@dm + 177), @ds_futsal, (@dp + 591), (@dp + 590), 6.33, 10, '제출 완료', '2026-11-03 21:30:00'
       UNION ALL SELECT (@de + 64), (@dm + 177), @ds_futsal, (@dp + 591), (@dp + 594), 5.67, 10, '제출 완료', '2026-11-03 21:30:00'
       UNION ALL SELECT (@de + 65), (@dm + 177), @ds_futsal, (@dp + 591), (@dp + 596), 4.67, 10, '제출 완료', '2026-11-03 21:30:00'
       UNION ALL SELECT (@de + 66), (@dm + 177), @ds_futsal, (@dp + 591), (@dp + 589), 4.33, 10, '제출 완료', '2026-11-03 21:30:00'
       UNION ALL SELECT (@de + 67), (@dm + 177), @ds_futsal, (@dp + 591), (@dp + 592), 6.00, 10, '제출 완료', '2026-11-03 21:30:00'
       UNION ALL SELECT (@de + 68), (@dm + 177), @ds_futsal, (@dp + 591), (@dp + 597), 5.33, 10, '제출 완료', '2026-11-03 21:30:00'
       UNION ALL SELECT (@de + 69), (@dm + 178), @ds_tennis, (@dp + 602), (@dp + 600), 5.00, 10, '제출 완료', '2026-11-04 21:10:00'
       UNION ALL SELECT (@de + 70), (@dm + 178), @ds_tennis, (@dp + 602), (@dp + 601), 4.00, 10, '제출 완료', '2026-11-04 21:10:00'
       UNION ALL SELECT (@de + 71), (@dm + 178), @ds_tennis, (@dp + 602), (@dp + 599), 5.00, 10, '제출 완료', '2026-11-04 21:10:00'
       UNION ALL SELECT (@de + 72), (@dm + 178), @ds_tennis, (@dp + 600), (@dp + 602), 7.00, 10, '제출 완료', '2026-11-04 21:30:00'
       UNION ALL SELECT (@de + 73), (@dm + 178), @ds_tennis, (@dp + 600), (@dp + 601), 5.67, 10, '제출 완료', '2026-11-04 21:30:00'
       UNION ALL SELECT (@de + 74), (@dm + 178), @ds_tennis, (@dp + 600), (@dp + 599), 5.00, 10, '제출 완료', '2026-11-04 21:30:00'
       UNION ALL SELECT (@de + 75), (@dm + 179), @ds_badminton, (@dp + 605), (@dp + 604), 7.00, 10, '제출 완료', '2026-11-04 21:10:00'
       UNION ALL SELECT (@de + 76), (@dm + 179), @ds_badminton, (@dp + 605), (@dp + 606), 4.33, 10, '제출 완료', '2026-11-04 21:10:00'
       UNION ALL SELECT (@de + 77), (@dm + 179), @ds_badminton, (@dp + 605), (@dp + 603), 5.00, 10, '제출 완료', '2026-11-04 21:10:00'
       UNION ALL SELECT (@de + 78), (@dm + 179), @ds_badminton, (@dp + 604), (@dp + 605), 6.00, 10, '제출 완료', '2026-11-04 21:30:00'
       UNION ALL SELECT (@de + 79), (@dm + 179), @ds_badminton, (@dp + 604), (@dp + 606), 5.00, 10, '제출 완료', '2026-11-04 21:30:00'
       UNION ALL SELECT (@de + 80), (@dm + 179), @ds_badminton, (@dp + 604), (@dp + 603), 4.33, 10, '제출 완료', '2026-11-04 21:30:00'
       UNION ALL SELECT (@de + 81), (@dm + 180), @ds_basketball, (@dp + 607), (@dp + 608), 5.67, 10, '제출 완료', '2026-11-05 21:10:00'
       UNION ALL SELECT (@de + 82), (@dm + 180), @ds_basketball, (@dp + 607), (@dp + 612), 5.00, 10, '제출 완료', '2026-11-05 21:10:00'
       UNION ALL SELECT (@de + 83), (@dm + 180), @ds_basketball, (@dp + 607), (@dp + 611), 6.67, 10, '제출 완료', '2026-11-05 21:10:00'
       UNION ALL SELECT (@de + 84), (@dm + 180), @ds_basketball, (@dp + 607), (@dp + 609), 5.67, 10, '제출 완료', '2026-11-05 21:10:00'
       UNION ALL SELECT (@de + 85), (@dm + 180), @ds_basketball, (@dp + 607), (@dp + 610), 5.33, 10, '제출 완료', '2026-11-05 21:10:00'
       UNION ALL SELECT (@de + 86), (@dm + 180), @ds_basketball, (@dp + 608), (@dp + 607), 5.67, 10, '제출 완료', '2026-11-05 21:30:00'
       UNION ALL SELECT (@de + 87), (@dm + 180), @ds_basketball, (@dp + 608), (@dp + 612), 5.33, 10, '제출 완료', '2026-11-05 21:30:00'
       UNION ALL SELECT (@de + 88), (@dm + 180), @ds_basketball, (@dp + 608), (@dp + 611), 5.67, 10, '제출 완료', '2026-11-05 21:30:00'
       UNION ALL SELECT (@de + 89), (@dm + 180), @ds_basketball, (@dp + 608), (@dp + 609), 5.67, 10, '제출 완료', '2026-11-05 21:30:00'
       UNION ALL SELECT (@de + 90), (@dm + 180), @ds_basketball, (@dp + 608), (@dp + 610), 4.33, 10, '제출 완료', '2026-11-05 21:30:00'
       UNION ALL SELECT (@de + 91), (@dm + 181), @ds_jokgu, (@dp + 615), (@dp + 618), 6.33, 10, '제출 완료', '2026-11-05 21:10:00'
       UNION ALL SELECT (@de + 92), (@dm + 181), @ds_jokgu, (@dp + 615), (@dp + 617), 4.33, 10, '제출 완료', '2026-11-05 21:10:00'
       UNION ALL SELECT (@de + 93), (@dm + 181), @ds_jokgu, (@dp + 615), (@dp + 613), 6.33, 10, '제출 완료', '2026-11-05 21:10:00'
       UNION ALL SELECT (@de + 94), (@dm + 181), @ds_jokgu, (@dp + 615), (@dp + 614), 4.33, 10, '제출 완료', '2026-11-05 21:10:00'
       UNION ALL SELECT (@de + 95), (@dm + 181), @ds_jokgu, (@dp + 615), (@dp + 616), 4.67, 10, '제출 완료', '2026-11-05 21:10:00'
       UNION ALL SELECT (@de + 96), (@dm + 181), @ds_jokgu, (@dp + 618), (@dp + 615), 5.00, 10, '제출 완료', '2026-11-05 21:30:00'
       UNION ALL SELECT (@de + 97), (@dm + 181), @ds_jokgu, (@dp + 618), (@dp + 617), 5.33, 10, '제출 완료', '2026-11-05 21:30:00'
       UNION ALL SELECT (@de + 98), (@dm + 181), @ds_jokgu, (@dp + 618), (@dp + 613), 3.67, 10, '제출 완료', '2026-11-05 21:30:00'
       UNION ALL SELECT (@de + 99), (@dm + 181), @ds_jokgu, (@dp + 618), (@dp + 614), 4.67, 10, '제출 완료', '2026-11-05 21:30:00'
       UNION ALL SELECT (@de + 100), (@dm + 181), @ds_jokgu, (@dp + 618), (@dp + 616), 4.33, 10, '제출 완료', '2026-11-05 21:30:00'
       UNION ALL SELECT (@de + 101), (@dm + 182), @ds_futsal, (@dp + 619), (@dp + 621), 8.00, 10, '제출 완료', '2026-11-06 21:10:00'
       UNION ALL SELECT (@de + 102), (@dm + 182), @ds_futsal, (@dp + 619), (@dp + 622), 6.67, 10, '제출 완료', '2026-11-06 21:10:00'
       UNION ALL SELECT (@de + 103), (@dm + 182), @ds_futsal, (@dp + 619), (@dp + 623), 5.33, 10, '제출 완료', '2026-11-06 21:10:00'
       UNION ALL SELECT (@de + 104), (@dm + 182), @ds_futsal, (@dp + 619), (@dp + 628), 6.33, 10, '제출 완료', '2026-11-06 21:10:00'
       UNION ALL SELECT (@de + 105), (@dm + 182), @ds_futsal, (@dp + 619), (@dp + 627), 6.33, 10, '제출 완료', '2026-11-06 21:10:00'
       UNION ALL SELECT (@de + 106), (@dm + 182), @ds_futsal, (@dp + 619), (@dp + 624), 5.67, 10, '제출 완료', '2026-11-06 21:10:00'
       UNION ALL SELECT (@de + 107), (@dm + 182), @ds_futsal, (@dp + 619), (@dp + 620), 6.00, 10, '제출 완료', '2026-11-06 21:10:00'
       UNION ALL SELECT (@de + 108), (@dm + 182), @ds_futsal, (@dp + 619), (@dp + 625), 7.00, 10, '제출 완료', '2026-11-06 21:10:00'
       UNION ALL SELECT (@de + 109), (@dm + 182), @ds_futsal, (@dp + 619), (@dp + 626), 3.67, 10, '제출 완료', '2026-11-06 21:10:00'
       UNION ALL SELECT (@de + 110), (@dm + 182), @ds_futsal, (@dp + 621), (@dp + 619), 6.00, 10, '제출 완료', '2026-11-06 21:30:00'
       UNION ALL SELECT (@de + 111), (@dm + 182), @ds_futsal, (@dp + 621), (@dp + 622), 8.00, 10, '제출 완료', '2026-11-06 21:30:00'
       UNION ALL SELECT (@de + 112), (@dm + 182), @ds_futsal, (@dp + 621), (@dp + 623), 6.67, 10, '제출 완료', '2026-11-06 21:30:00'
       UNION ALL SELECT (@de + 113), (@dm + 182), @ds_futsal, (@dp + 621), (@dp + 628), 6.67, 10, '제출 완료', '2026-11-06 21:30:00'
       UNION ALL SELECT (@de + 114), (@dm + 182), @ds_futsal, (@dp + 621), (@dp + 627), 5.33, 10, '제출 완료', '2026-11-06 21:30:00'
       UNION ALL SELECT (@de + 115), (@dm + 182), @ds_futsal, (@dp + 621), (@dp + 624), 6.67, 10, '제출 완료', '2026-11-06 21:30:00'
       UNION ALL SELECT (@de + 116), (@dm + 182), @ds_futsal, (@dp + 621), (@dp + 620), 4.67, 10, '제출 완료', '2026-11-06 21:30:00'
       UNION ALL SELECT (@de + 117), (@dm + 182), @ds_futsal, (@dp + 621), (@dp + 625), 5.67, 10, '제출 완료', '2026-11-06 21:30:00'
       UNION ALL SELECT (@de + 118), (@dm + 182), @ds_futsal, (@dp + 621), (@dp + 626), 4.00, 10, '제출 완료', '2026-11-06 21:30:00'
       UNION ALL SELECT (@de + 119), (@dm + 183), @ds_tennis, (@dp + 630), (@dp + 632), 6.33, 10, '제출 완료', '2026-11-06 21:10:00'
       UNION ALL SELECT (@de + 120), (@dm + 183), @ds_tennis, (@dp + 630), (@dp + 629), 6.00, 10, '제출 완료', '2026-11-06 21:10:00'
       UNION ALL SELECT (@de + 121), (@dm + 183), @ds_tennis, (@dp + 630), (@dp + 631), 3.00, 10, '제출 완료', '2026-11-06 21:10:00'
       UNION ALL SELECT (@de + 122), (@dm + 183), @ds_tennis, (@dp + 632), (@dp + 630), 5.00, 10, '제출 완료', '2026-11-06 21:30:00'
       UNION ALL SELECT (@de + 123), (@dm + 183), @ds_tennis, (@dp + 632), (@dp + 629), 5.00, 10, '제출 완료', '2026-11-06 21:30:00'
       UNION ALL SELECT (@de + 124), (@dm + 183), @ds_tennis, (@dp + 632), (@dp + 631), 5.00, 10, '제출 완료', '2026-11-06 21:30:00'
       UNION ALL SELECT (@de + 125), (@dm + 184), @ds_badminton, (@dp + 634), (@dp + 636), 4.67, 10, '제출 완료', '2026-11-07 13:10:00'
       UNION ALL SELECT (@de + 126), (@dm + 184), @ds_badminton, (@dp + 634), (@dp + 635), 4.67, 10, '제출 완료', '2026-11-07 13:10:00'
       UNION ALL SELECT (@de + 127), (@dm + 184), @ds_badminton, (@dp + 634), (@dp + 633), 5.33, 10, '제출 완료', '2026-11-07 13:10:00'
       UNION ALL SELECT (@de + 128), (@dm + 184), @ds_badminton, (@dp + 636), (@dp + 634), 5.67, 10, '제출 완료', '2026-11-07 13:30:00'
       UNION ALL SELECT (@de + 129), (@dm + 184), @ds_badminton, (@dp + 636), (@dp + 635), 5.33, 10, '제출 완료', '2026-11-07 13:30:00'
       UNION ALL SELECT (@de + 130), (@dm + 184), @ds_badminton, (@dp + 636), (@dp + 633), 5.33, 10, '제출 완료', '2026-11-07 13:30:00'
       UNION ALL SELECT (@de + 131), (@dm + 185), @ds_basketball, (@dp + 639), (@dp + 642), 6.67, 10, '제출 완료', '2026-11-07 17:10:00'
       UNION ALL SELECT (@de + 132), (@dm + 185), @ds_basketball, (@dp + 639), (@dp + 638), 7.00, 10, '제출 완료', '2026-11-07 17:10:00'
       UNION ALL SELECT (@de + 133), (@dm + 185), @ds_basketball, (@dp + 639), (@dp + 637), 6.33, 10, '제출 완료', '2026-11-07 17:10:00'
       UNION ALL SELECT (@de + 134), (@dm + 185), @ds_basketball, (@dp + 639), (@dp + 640), 4.00, 10, '제출 완료', '2026-11-07 17:10:00'
       UNION ALL SELECT (@de + 135), (@dm + 185), @ds_basketball, (@dp + 639), (@dp + 641), 5.67, 10, '제출 완료', '2026-11-07 17:10:00'
       UNION ALL SELECT (@de + 136), (@dm + 185), @ds_basketball, (@dp + 642), (@dp + 639), 5.67, 10, '제출 완료', '2026-11-07 17:30:00'
       UNION ALL SELECT (@de + 137), (@dm + 185), @ds_basketball, (@dp + 642), (@dp + 638), 6.33, 10, '제출 완료', '2026-11-07 17:30:00'
       UNION ALL SELECT (@de + 138), (@dm + 185), @ds_basketball, (@dp + 642), (@dp + 637), 6.33, 10, '제출 완료', '2026-11-07 17:30:00'
       UNION ALL SELECT (@de + 139), (@dm + 185), @ds_basketball, (@dp + 642), (@dp + 640), 4.00, 10, '제출 완료', '2026-11-07 17:30:00'
       UNION ALL SELECT (@de + 140), (@dm + 185), @ds_basketball, (@dp + 642), (@dp + 641), 3.67, 10, '제출 완료', '2026-11-07 17:30:00'
       UNION ALL SELECT (@de + 141), (@dm + 186), @ds_jokgu, (@dp + 645), (@dp + 647), 5.67, 10, '제출 완료', '2026-11-08 13:10:00'
       UNION ALL SELECT (@de + 142), (@dm + 186), @ds_jokgu, (@dp + 645), (@dp + 644), 5.67, 10, '제출 완료', '2026-11-08 13:10:00'
       UNION ALL SELECT (@de + 143), (@dm + 186), @ds_jokgu, (@dp + 645), (@dp + 648), 6.33, 10, '제출 완료', '2026-11-08 13:10:00'
       UNION ALL SELECT (@de + 144), (@dm + 186), @ds_jokgu, (@dp + 645), (@dp + 646), 4.33, 10, '제출 완료', '2026-11-08 13:10:00'
       UNION ALL SELECT (@de + 145), (@dm + 186), @ds_jokgu, (@dp + 645), (@dp + 643), 3.67, 10, '제출 완료', '2026-11-08 13:10:00'
       UNION ALL SELECT (@de + 146), (@dm + 186), @ds_jokgu, (@dp + 647), (@dp + 645), 5.33, 10, '제출 완료', '2026-11-08 13:30:00'
       UNION ALL SELECT (@de + 147), (@dm + 186), @ds_jokgu, (@dp + 647), (@dp + 644), 6.00, 10, '제출 완료', '2026-11-08 13:30:00'
       UNION ALL SELECT (@de + 148), (@dm + 186), @ds_jokgu, (@dp + 647), (@dp + 648), 4.00, 10, '제출 완료', '2026-11-08 13:30:00'
       UNION ALL SELECT (@de + 149), (@dm + 186), @ds_jokgu, (@dp + 647), (@dp + 646), 5.00, 10, '제출 완료', '2026-11-08 13:30:00'
       UNION ALL SELECT (@de + 150), (@dm + 186), @ds_jokgu, (@dp + 647), (@dp + 643), 4.67, 10, '제출 완료', '2026-11-08 13:30:00'
) v
 WHERE @demo_ready = 1;

INSERT INTO match_evaluation (evaluation_id, match_id, sport_id, evaluator_participant_id, target_participant_id, overall_score, scale_max, status, submitted_at)
SELECT * FROM (
                 SELECT (@de + 151) AS evaluation_id, (@dm + 187) AS match_id, @ds_futsal AS sport_id, (@dp + 653) AS evaluator_participant_id, (@dp + 654) AS target_participant_id, 4.67 AS overall_score, 10 AS scale_max, '제출 완료' AS status, '2026-11-08 17:10:00' AS submitted_at
       UNION ALL SELECT (@de + 152), (@dm + 187), @ds_futsal, (@dp + 653), (@dp + 651), 5.67, 10, '제출 완료', '2026-11-08 17:10:00'
       UNION ALL SELECT (@de + 153), (@dm + 187), @ds_futsal, (@dp + 653), (@dp + 650), 5.67, 10, '제출 완료', '2026-11-08 17:10:00'
       UNION ALL SELECT (@de + 154), (@dm + 187), @ds_futsal, (@dp + 653), (@dp + 656), 4.67, 10, '제출 완료', '2026-11-08 17:10:00'
       UNION ALL SELECT (@de + 155), (@dm + 187), @ds_futsal, (@dp + 653), (@dp + 658), 5.67, 10, '제출 완료', '2026-11-08 17:10:00'
       UNION ALL SELECT (@de + 156), (@dm + 187), @ds_futsal, (@dp + 653), (@dp + 649), 5.33, 10, '제출 완료', '2026-11-08 17:10:00'
       UNION ALL SELECT (@de + 157), (@dm + 187), @ds_futsal, (@dp + 653), (@dp + 655), 4.33, 10, '제출 완료', '2026-11-08 17:10:00'
       UNION ALL SELECT (@de + 158), (@dm + 187), @ds_futsal, (@dp + 653), (@dp + 657), 4.67, 10, '제출 완료', '2026-11-08 17:10:00'
       UNION ALL SELECT (@de + 159), (@dm + 187), @ds_futsal, (@dp + 653), (@dp + 652), 4.00, 10, '제출 완료', '2026-11-08 17:10:00'
       UNION ALL SELECT (@de + 160), (@dm + 187), @ds_futsal, (@dp + 654), (@dp + 653), 7.00, 10, '제출 완료', '2026-11-08 17:30:00'
       UNION ALL SELECT (@de + 161), (@dm + 187), @ds_futsal, (@dp + 654), (@dp + 651), 6.00, 10, '제출 완료', '2026-11-08 17:30:00'
       UNION ALL SELECT (@de + 162), (@dm + 187), @ds_futsal, (@dp + 654), (@dp + 650), 6.33, 10, '제출 완료', '2026-11-08 17:30:00'
       UNION ALL SELECT (@de + 163), (@dm + 187), @ds_futsal, (@dp + 654), (@dp + 656), 6.00, 10, '제출 완료', '2026-11-08 17:30:00'
       UNION ALL SELECT (@de + 164), (@dm + 187), @ds_futsal, (@dp + 654), (@dp + 658), 5.00, 10, '제출 완료', '2026-11-08 17:30:00'
       UNION ALL SELECT (@de + 165), (@dm + 187), @ds_futsal, (@dp + 654), (@dp + 649), 5.00, 10, '제출 완료', '2026-11-08 17:30:00'
       UNION ALL SELECT (@de + 166), (@dm + 187), @ds_futsal, (@dp + 654), (@dp + 655), 4.00, 10, '제출 완료', '2026-11-08 17:30:00'
       UNION ALL SELECT (@de + 167), (@dm + 187), @ds_futsal, (@dp + 654), (@dp + 657), 6.00, 10, '제출 완료', '2026-11-08 17:30:00'
       UNION ALL SELECT (@de + 168), (@dm + 187), @ds_futsal, (@dp + 654), (@dp + 652), 3.33, 10, '제출 완료', '2026-11-08 17:30:00'
       UNION ALL SELECT (@de + 169), (@dm + 188), @ds_tennis, (@dp + 660), (@dp + 661), 5.33, 10, '제출 완료', '2026-11-09 21:10:00'
       UNION ALL SELECT (@de + 170), (@dm + 188), @ds_tennis, (@dp + 660), (@dp + 659), 7.00, 10, '제출 완료', '2026-11-09 21:10:00'
       UNION ALL SELECT (@de + 171), (@dm + 188), @ds_tennis, (@dp + 660), (@dp + 662), 4.67, 10, '제출 완료', '2026-11-09 21:10:00'
       UNION ALL SELECT (@de + 172), (@dm + 188), @ds_tennis, (@dp + 661), (@dp + 660), 5.67, 10, '제출 완료', '2026-11-09 21:30:00'
       UNION ALL SELECT (@de + 173), (@dm + 188), @ds_tennis, (@dp + 661), (@dp + 659), 5.33, 10, '제출 완료', '2026-11-09 21:30:00'
       UNION ALL SELECT (@de + 174), (@dm + 188), @ds_tennis, (@dp + 661), (@dp + 662), 7.33, 10, '제출 완료', '2026-11-09 21:30:00'
       UNION ALL SELECT (@de + 175), (@dm + 189), @ds_badminton, (@dp + 664), (@dp + 665), 5.67, 10, '제출 완료', '2026-11-09 21:10:00'
       UNION ALL SELECT (@de + 176), (@dm + 189), @ds_badminton, (@dp + 664), (@dp + 663), 6.33, 10, '제출 완료', '2026-11-09 21:10:00'
       UNION ALL SELECT (@de + 177), (@dm + 189), @ds_badminton, (@dp + 664), (@dp + 666), 5.33, 10, '제출 완료', '2026-11-09 21:10:00'
       UNION ALL SELECT (@de + 178), (@dm + 189), @ds_badminton, (@dp + 665), (@dp + 664), 6.00, 10, '제출 완료', '2026-11-09 21:30:00'
       UNION ALL SELECT (@de + 179), (@dm + 189), @ds_badminton, (@dp + 665), (@dp + 663), 5.00, 10, '제출 완료', '2026-11-09 21:30:00'
       UNION ALL SELECT (@de + 180), (@dm + 189), @ds_badminton, (@dp + 665), (@dp + 666), 5.33, 10, '제출 완료', '2026-11-09 21:30:00'
) v
 WHERE @demo_ready = 1;

INSERT INTO match_evaluation_score (evaluation_id, item_id, sport_id, score, observation_status)
SELECT * FROM (
                 SELECT (@de + 1) AS evaluation_id, @di_futsal_0 AS item_id, @ds_futsal AS sport_id, 5 AS score, 'OBSERVED' AS observation_status
       UNION ALL SELECT (@de + 1), @di_futsal_1, @ds_futsal, 8, 'OBSERVED'
       UNION ALL SELECT (@de + 1), @di_futsal_2, @ds_futsal, 8, 'OBSERVED'
       UNION ALL SELECT (@de + 2), @di_futsal_0, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 2), @di_futsal_1, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 2), @di_futsal_2, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 3), @di_futsal_0, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 3), @di_futsal_1, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 3), @di_futsal_2, @ds_futsal, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 4), @di_futsal_0, @ds_futsal, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 4), @di_futsal_1, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 4), @di_futsal_2, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 5), @di_futsal_0, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 5), @di_futsal_1, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 5), @di_futsal_2, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 6), @di_futsal_0, @ds_futsal, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 6), @di_futsal_1, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 6), @di_futsal_2, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 7), @di_futsal_0, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 7), @di_futsal_1, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 7), @di_futsal_2, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 8), @di_futsal_0, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 8), @di_futsal_1, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 8), @di_futsal_2, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 9), @di_futsal_0, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 9), @di_futsal_1, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 9), @di_futsal_2, @ds_futsal, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 10), @di_futsal_0, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 10), @di_futsal_1, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 10), @di_futsal_2, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 11), @di_futsal_0, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 11), @di_futsal_1, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 11), @di_futsal_2, @ds_futsal, 8, 'OBSERVED'
       UNION ALL SELECT (@de + 12), @di_futsal_0, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 12), @di_futsal_1, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 12), @di_futsal_2, @ds_futsal, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 13), @di_futsal_0, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 13), @di_futsal_1, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 13), @di_futsal_2, @ds_futsal, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 14), @di_futsal_0, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 14), @di_futsal_1, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 14), @di_futsal_2, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 15), @di_futsal_0, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 15), @di_futsal_1, @ds_futsal, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 15), @di_futsal_2, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 16), @di_futsal_0, @ds_futsal, 1, 'OBSERVED'
       UNION ALL SELECT (@de + 16), @di_futsal_1, @ds_futsal, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 16), @di_futsal_2, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 17), @di_futsal_0, @ds_futsal, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 17), @di_futsal_1, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 17), @di_futsal_2, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 18), @di_futsal_0, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 18), @di_futsal_1, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 18), @di_futsal_2, @ds_futsal, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 19), @di_tennis_0, @ds_tennis, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 19), @di_tennis_1, @ds_tennis, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 19), @di_tennis_2, @ds_tennis, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 20), @di_tennis_0, @ds_tennis, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 20), @di_tennis_1, @ds_tennis, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 20), @di_tennis_2, @ds_tennis, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 21), @di_tennis_0, @ds_tennis, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 21), @di_tennis_1, @ds_tennis, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 21), @di_tennis_2, @ds_tennis, 1, 'OBSERVED'
       UNION ALL SELECT (@de + 22), @di_tennis_0, @ds_tennis, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 22), @di_tennis_1, @ds_tennis, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 22), @di_tennis_2, @ds_tennis, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 23), @di_tennis_0, @ds_tennis, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 23), @di_tennis_1, @ds_tennis, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 23), @di_tennis_2, @ds_tennis, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 24), @di_tennis_0, @ds_tennis, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 24), @di_tennis_1, @ds_tennis, 2, 'OBSERVED'
       UNION ALL SELECT (@de + 24), @di_tennis_2, @ds_tennis, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 25), @di_badminton_0, @ds_badminton, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 25), @di_badminton_1, @ds_badminton, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 25), @di_badminton_2, @ds_badminton, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 26), @di_badminton_0, @ds_badminton, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 26), @di_badminton_1, @ds_badminton, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 26), @di_badminton_2, @ds_badminton, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 27), @di_badminton_0, @ds_badminton, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 27), @di_badminton_1, @ds_badminton, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 27), @di_badminton_2, @ds_badminton, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 28), @di_badminton_0, @ds_badminton, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 28), @di_badminton_1, @ds_badminton, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 28), @di_badminton_2, @ds_badminton, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 29), @di_badminton_0, @ds_badminton, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 29), @di_badminton_1, @ds_badminton, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 29), @di_badminton_2, @ds_badminton, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 30), @di_badminton_0, @ds_badminton, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 30), @di_badminton_1, @ds_badminton, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 30), @di_badminton_2, @ds_badminton, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 31), @di_basketball_0, @ds_basketball, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 31), @di_basketball_1, @ds_basketball, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 31), @di_basketball_2, @ds_basketball, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 32), @di_basketball_0, @ds_basketball, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 32), @di_basketball_1, @ds_basketball, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 32), @di_basketball_2, @ds_basketball, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 33), @di_basketball_0, @ds_basketball, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 33), @di_basketball_1, @ds_basketball, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 33), @di_basketball_2, @ds_basketball, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 34), @di_basketball_0, @ds_basketball, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 34), @di_basketball_1, @ds_basketball, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 34), @di_basketball_2, @ds_basketball, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 35), @di_basketball_0, @ds_basketball, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 35), @di_basketball_1, @ds_basketball, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 35), @di_basketball_2, @ds_basketball, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 36), @di_basketball_0, @ds_basketball, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 36), @di_basketball_1, @ds_basketball, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 36), @di_basketball_2, @ds_basketball, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 37), @di_basketball_0, @ds_basketball, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 37), @di_basketball_1, @ds_basketball, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 37), @di_basketball_2, @ds_basketball, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 38), @di_basketball_0, @ds_basketball, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 38), @di_basketball_1, @ds_basketball, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 38), @di_basketball_2, @ds_basketball, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 39), @di_basketball_0, @ds_basketball, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 39), @di_basketball_1, @ds_basketball, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 39), @di_basketball_2, @ds_basketball, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 40), @di_basketball_0, @ds_basketball, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 40), @di_basketball_1, @ds_basketball, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 40), @di_basketball_2, @ds_basketball, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 41), @di_jokgu_0, @ds_jokgu, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 41), @di_jokgu_1, @ds_jokgu, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 41), @di_jokgu_2, @ds_jokgu, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 42), @di_jokgu_0, @ds_jokgu, 8, 'OBSERVED'
       UNION ALL SELECT (@de + 42), @di_jokgu_1, @ds_jokgu, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 42), @di_jokgu_2, @ds_jokgu, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 43), @di_jokgu_0, @ds_jokgu, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 43), @di_jokgu_1, @ds_jokgu, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 43), @di_jokgu_2, @ds_jokgu, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 44), @di_jokgu_0, @ds_jokgu, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 44), @di_jokgu_1, @ds_jokgu, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 44), @di_jokgu_2, @ds_jokgu, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 45), @di_jokgu_0, @ds_jokgu, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 45), @di_jokgu_1, @ds_jokgu, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 45), @di_jokgu_2, @ds_jokgu, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 46), @di_jokgu_0, @ds_jokgu, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 46), @di_jokgu_1, @ds_jokgu, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 46), @di_jokgu_2, @ds_jokgu, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 47), @di_jokgu_0, @ds_jokgu, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 47), @di_jokgu_1, @ds_jokgu, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 47), @di_jokgu_2, @ds_jokgu, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 48), @di_jokgu_0, @ds_jokgu, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 48), @di_jokgu_1, @ds_jokgu, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 48), @di_jokgu_2, @ds_jokgu, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 49), @di_jokgu_0, @ds_jokgu, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 49), @di_jokgu_1, @ds_jokgu, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 49), @di_jokgu_2, @ds_jokgu, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 50), @di_jokgu_0, @ds_jokgu, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 50), @di_jokgu_1, @ds_jokgu, 2, 'OBSERVED'
       UNION ALL SELECT (@de + 50), @di_jokgu_2, @ds_jokgu, 6, 'OBSERVED'
) v
 WHERE @demo_ready = 1;

INSERT INTO match_evaluation_score (evaluation_id, item_id, sport_id, score, observation_status)
SELECT * FROM (
                 SELECT (@de + 51) AS evaluation_id, @di_futsal_0 AS item_id, @ds_futsal AS sport_id, 7 AS score, 'OBSERVED' AS observation_status
       UNION ALL SELECT (@de + 51), @di_futsal_1, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 51), @di_futsal_2, @ds_futsal, 8, 'OBSERVED'
       UNION ALL SELECT (@de + 52), @di_futsal_0, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 52), @di_futsal_1, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 52), @di_futsal_2, @ds_futsal, 8, 'OBSERVED'
       UNION ALL SELECT (@de + 53), @di_futsal_0, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 53), @di_futsal_1, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 53), @di_futsal_2, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 54), @di_futsal_0, @ds_futsal, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 54), @di_futsal_1, @ds_futsal, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 54), @di_futsal_2, @ds_futsal, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 55), @di_futsal_0, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 55), @di_futsal_1, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 55), @di_futsal_2, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 56), @di_futsal_0, @ds_futsal, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 56), @di_futsal_1, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 56), @di_futsal_2, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 57), @di_futsal_0, @ds_futsal, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 57), @di_futsal_1, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 57), @di_futsal_2, @ds_futsal, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 58), @di_futsal_0, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 58), @di_futsal_1, @ds_futsal, 2, 'OBSERVED'
       UNION ALL SELECT (@de + 58), @di_futsal_2, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 59), @di_futsal_0, @ds_futsal, 2, 'OBSERVED'
       UNION ALL SELECT (@de + 59), @di_futsal_1, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 59), @di_futsal_2, @ds_futsal, 2, 'OBSERVED'
       UNION ALL SELECT (@de + 60), @di_futsal_0, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 60), @di_futsal_1, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 60), @di_futsal_2, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 61), @di_futsal_0, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 61), @di_futsal_1, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 61), @di_futsal_2, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 62), @di_futsal_0, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 62), @di_futsal_1, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 62), @di_futsal_2, @ds_futsal, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 63), @di_futsal_0, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 63), @di_futsal_1, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 63), @di_futsal_2, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 64), @di_futsal_0, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 64), @di_futsal_1, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 64), @di_futsal_2, @ds_futsal, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 65), @di_futsal_0, @ds_futsal, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 65), @di_futsal_1, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 65), @di_futsal_2, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 66), @di_futsal_0, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 66), @di_futsal_1, @ds_futsal, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 66), @di_futsal_2, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 67), @di_futsal_0, @ds_futsal, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 67), @di_futsal_1, @ds_futsal, 8, 'OBSERVED'
       UNION ALL SELECT (@de + 67), @di_futsal_2, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 68), @di_futsal_0, @ds_futsal, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 68), @di_futsal_1, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 68), @di_futsal_2, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 69), @di_tennis_0, @ds_tennis, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 69), @di_tennis_1, @ds_tennis, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 69), @di_tennis_2, @ds_tennis, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 70), @di_tennis_0, @ds_tennis, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 70), @di_tennis_1, @ds_tennis, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 70), @di_tennis_2, @ds_tennis, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 71), @di_tennis_0, @ds_tennis, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 71), @di_tennis_1, @ds_tennis, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 71), @di_tennis_2, @ds_tennis, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 72), @di_tennis_0, @ds_tennis, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 72), @di_tennis_1, @ds_tennis, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 72), @di_tennis_2, @ds_tennis, 8, 'OBSERVED'
       UNION ALL SELECT (@de + 73), @di_tennis_0, @ds_tennis, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 73), @di_tennis_1, @ds_tennis, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 73), @di_tennis_2, @ds_tennis, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 74), @di_tennis_0, @ds_tennis, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 74), @di_tennis_1, @ds_tennis, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 74), @di_tennis_2, @ds_tennis, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 75), @di_badminton_0, @ds_badminton, 8, 'OBSERVED'
       UNION ALL SELECT (@de + 75), @di_badminton_1, @ds_badminton, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 75), @di_badminton_2, @ds_badminton, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 76), @di_badminton_0, @ds_badminton, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 76), @di_badminton_1, @ds_badminton, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 76), @di_badminton_2, @ds_badminton, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 77), @di_badminton_0, @ds_badminton, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 77), @di_badminton_1, @ds_badminton, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 77), @di_badminton_2, @ds_badminton, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 78), @di_badminton_0, @ds_badminton, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 78), @di_badminton_1, @ds_badminton, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 78), @di_badminton_2, @ds_badminton, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 79), @di_badminton_0, @ds_badminton, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 79), @di_badminton_1, @ds_badminton, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 79), @di_badminton_2, @ds_badminton, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 80), @di_badminton_0, @ds_badminton, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 80), @di_badminton_1, @ds_badminton, 2, 'OBSERVED'
       UNION ALL SELECT (@de + 80), @di_badminton_2, @ds_badminton, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 81), @di_basketball_0, @ds_basketball, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 81), @di_basketball_1, @ds_basketball, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 81), @di_basketball_2, @ds_basketball, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 82), @di_basketball_0, @ds_basketball, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 82), @di_basketball_1, @ds_basketball, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 82), @di_basketball_2, @ds_basketball, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 83), @di_basketball_0, @ds_basketball, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 83), @di_basketball_1, @ds_basketball, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 83), @di_basketball_2, @ds_basketball, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 84), @di_basketball_0, @ds_basketball, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 84), @di_basketball_1, @ds_basketball, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 84), @di_basketball_2, @ds_basketball, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 85), @di_basketball_0, @ds_basketball, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 85), @di_basketball_1, @ds_basketball, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 85), @di_basketball_2, @ds_basketball, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 86), @di_basketball_0, @ds_basketball, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 86), @di_basketball_1, @ds_basketball, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 86), @di_basketball_2, @ds_basketball, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 87), @di_basketball_0, @ds_basketball, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 87), @di_basketball_1, @ds_basketball, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 87), @di_basketball_2, @ds_basketball, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 88), @di_basketball_0, @ds_basketball, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 88), @di_basketball_1, @ds_basketball, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 88), @di_basketball_2, @ds_basketball, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 89), @di_basketball_0, @ds_basketball, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 89), @di_basketball_1, @ds_basketball, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 89), @di_basketball_2, @ds_basketball, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 90), @di_basketball_0, @ds_basketball, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 90), @di_basketball_1, @ds_basketball, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 90), @di_basketball_2, @ds_basketball, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 91), @di_jokgu_0, @ds_jokgu, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 91), @di_jokgu_1, @ds_jokgu, 9, 'OBSERVED'
       UNION ALL SELECT (@de + 91), @di_jokgu_2, @ds_jokgu, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 92), @di_jokgu_0, @ds_jokgu, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 92), @di_jokgu_1, @ds_jokgu, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 92), @di_jokgu_2, @ds_jokgu, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 93), @di_jokgu_0, @ds_jokgu, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 93), @di_jokgu_1, @ds_jokgu, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 93), @di_jokgu_2, @ds_jokgu, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 94), @di_jokgu_0, @ds_jokgu, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 94), @di_jokgu_1, @ds_jokgu, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 94), @di_jokgu_2, @ds_jokgu, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 95), @di_jokgu_0, @ds_jokgu, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 95), @di_jokgu_1, @ds_jokgu, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 95), @di_jokgu_2, @ds_jokgu, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 96), @di_jokgu_0, @ds_jokgu, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 96), @di_jokgu_1, @ds_jokgu, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 96), @di_jokgu_2, @ds_jokgu, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 97), @di_jokgu_0, @ds_jokgu, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 97), @di_jokgu_1, @ds_jokgu, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 97), @di_jokgu_2, @ds_jokgu, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 98), @di_jokgu_0, @ds_jokgu, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 98), @di_jokgu_1, @ds_jokgu, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 98), @di_jokgu_2, @ds_jokgu, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 99), @di_jokgu_0, @ds_jokgu, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 99), @di_jokgu_1, @ds_jokgu, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 99), @di_jokgu_2, @ds_jokgu, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 100), @di_jokgu_0, @ds_jokgu, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 100), @di_jokgu_1, @ds_jokgu, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 100), @di_jokgu_2, @ds_jokgu, 5, 'OBSERVED'
) v
 WHERE @demo_ready = 1;

INSERT INTO match_evaluation_score (evaluation_id, item_id, sport_id, score, observation_status)
SELECT * FROM (
                 SELECT (@de + 101) AS evaluation_id, @di_futsal_0 AS item_id, @ds_futsal AS sport_id, 7 AS score, 'OBSERVED' AS observation_status
       UNION ALL SELECT (@de + 101), @di_futsal_1, @ds_futsal, 8, 'OBSERVED'
       UNION ALL SELECT (@de + 101), @di_futsal_2, @ds_futsal, 9, 'OBSERVED'
       UNION ALL SELECT (@de + 102), @di_futsal_0, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 102), @di_futsal_1, @ds_futsal, 8, 'OBSERVED'
       UNION ALL SELECT (@de + 102), @di_futsal_2, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 103), @di_futsal_0, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 103), @di_futsal_1, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 103), @di_futsal_2, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 104), @di_futsal_0, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 104), @di_futsal_1, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 104), @di_futsal_2, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 105), @di_futsal_0, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 105), @di_futsal_1, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 105), @di_futsal_2, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 106), @di_futsal_0, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 106), @di_futsal_1, @ds_futsal, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 106), @di_futsal_2, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 107), @di_futsal_0, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 107), @di_futsal_1, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 107), @di_futsal_2, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 108), @di_futsal_0, @ds_futsal, 8, 'OBSERVED'
       UNION ALL SELECT (@de + 108), @di_futsal_1, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 108), @di_futsal_2, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 109), @di_futsal_0, @ds_futsal, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 109), @di_futsal_1, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 109), @di_futsal_2, @ds_futsal, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 110), @di_futsal_0, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 110), @di_futsal_1, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 110), @di_futsal_2, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 111), @di_futsal_0, @ds_futsal, 8, 'OBSERVED'
       UNION ALL SELECT (@de + 111), @di_futsal_1, @ds_futsal, 8, 'OBSERVED'
       UNION ALL SELECT (@de + 111), @di_futsal_2, @ds_futsal, 8, 'OBSERVED'
       UNION ALL SELECT (@de + 112), @di_futsal_0, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 112), @di_futsal_1, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 112), @di_futsal_2, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 113), @di_futsal_0, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 113), @di_futsal_1, @ds_futsal, 8, 'OBSERVED'
       UNION ALL SELECT (@de + 113), @di_futsal_2, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 114), @di_futsal_0, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 114), @di_futsal_1, @ds_futsal, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 114), @di_futsal_2, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 115), @di_futsal_0, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 115), @di_futsal_1, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 115), @di_futsal_2, @ds_futsal, 8, 'OBSERVED'
       UNION ALL SELECT (@de + 116), @di_futsal_0, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 116), @di_futsal_1, @ds_futsal, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 116), @di_futsal_2, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 117), @di_futsal_0, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 117), @di_futsal_1, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 117), @di_futsal_2, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 118), @di_futsal_0, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 118), @di_futsal_1, @ds_futsal, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 118), @di_futsal_2, @ds_futsal, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 119), @di_tennis_0, @ds_tennis, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 119), @di_tennis_1, @ds_tennis, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 119), @di_tennis_2, @ds_tennis, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 120), @di_tennis_0, @ds_tennis, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 120), @di_tennis_1, @ds_tennis, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 120), @di_tennis_2, @ds_tennis, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 121), @di_tennis_0, @ds_tennis, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 121), @di_tennis_1, @ds_tennis, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 121), @di_tennis_2, @ds_tennis, 2, 'OBSERVED'
       UNION ALL SELECT (@de + 122), @di_tennis_0, @ds_tennis, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 122), @di_tennis_1, @ds_tennis, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 122), @di_tennis_2, @ds_tennis, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 123), @di_tennis_0, @ds_tennis, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 123), @di_tennis_1, @ds_tennis, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 123), @di_tennis_2, @ds_tennis, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 124), @di_tennis_0, @ds_tennis, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 124), @di_tennis_1, @ds_tennis, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 124), @di_tennis_2, @ds_tennis, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 125), @di_badminton_0, @ds_badminton, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 125), @di_badminton_1, @ds_badminton, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 125), @di_badminton_2, @ds_badminton, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 126), @di_badminton_0, @ds_badminton, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 126), @di_badminton_1, @ds_badminton, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 126), @di_badminton_2, @ds_badminton, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 127), @di_badminton_0, @ds_badminton, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 127), @di_badminton_1, @ds_badminton, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 127), @di_badminton_2, @ds_badminton, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 128), @di_badminton_0, @ds_badminton, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 128), @di_badminton_1, @ds_badminton, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 128), @di_badminton_2, @ds_badminton, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 129), @di_badminton_0, @ds_badminton, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 129), @di_badminton_1, @ds_badminton, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 129), @di_badminton_2, @ds_badminton, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 130), @di_badminton_0, @ds_badminton, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 130), @di_badminton_1, @ds_badminton, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 130), @di_badminton_2, @ds_badminton, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 131), @di_basketball_0, @ds_basketball, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 131), @di_basketball_1, @ds_basketball, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 131), @di_basketball_2, @ds_basketball, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 132), @di_basketball_0, @ds_basketball, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 132), @di_basketball_1, @ds_basketball, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 132), @di_basketball_2, @ds_basketball, 8, 'OBSERVED'
       UNION ALL SELECT (@de + 133), @di_basketball_0, @ds_basketball, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 133), @di_basketball_1, @ds_basketball, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 133), @di_basketball_2, @ds_basketball, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 134), @di_basketball_0, @ds_basketball, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 134), @di_basketball_1, @ds_basketball, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 134), @di_basketball_2, @ds_basketball, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 135), @di_basketball_0, @ds_basketball, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 135), @di_basketball_1, @ds_basketball, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 135), @di_basketball_2, @ds_basketball, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 136), @di_basketball_0, @ds_basketball, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 136), @di_basketball_1, @ds_basketball, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 136), @di_basketball_2, @ds_basketball, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 137), @di_basketball_0, @ds_basketball, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 137), @di_basketball_1, @ds_basketball, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 137), @di_basketball_2, @ds_basketball, 8, 'OBSERVED'
       UNION ALL SELECT (@de + 138), @di_basketball_0, @ds_basketball, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 138), @di_basketball_1, @ds_basketball, 9, 'OBSERVED'
       UNION ALL SELECT (@de + 138), @di_basketball_2, @ds_basketball, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 139), @di_basketball_0, @ds_basketball, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 139), @di_basketball_1, @ds_basketball, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 139), @di_basketball_2, @ds_basketball, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 140), @di_basketball_0, @ds_basketball, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 140), @di_basketball_1, @ds_basketball, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 140), @di_basketball_2, @ds_basketball, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 141), @di_jokgu_0, @ds_jokgu, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 141), @di_jokgu_1, @ds_jokgu, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 141), @di_jokgu_2, @ds_jokgu, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 142), @di_jokgu_0, @ds_jokgu, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 142), @di_jokgu_1, @ds_jokgu, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 142), @di_jokgu_2, @ds_jokgu, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 143), @di_jokgu_0, @ds_jokgu, 8, 'OBSERVED'
       UNION ALL SELECT (@de + 143), @di_jokgu_1, @ds_jokgu, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 143), @di_jokgu_2, @ds_jokgu, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 144), @di_jokgu_0, @ds_jokgu, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 144), @di_jokgu_1, @ds_jokgu, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 144), @di_jokgu_2, @ds_jokgu, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 145), @di_jokgu_0, @ds_jokgu, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 145), @di_jokgu_1, @ds_jokgu, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 145), @di_jokgu_2, @ds_jokgu, 2, 'OBSERVED'
       UNION ALL SELECT (@de + 146), @di_jokgu_0, @ds_jokgu, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 146), @di_jokgu_1, @ds_jokgu, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 146), @di_jokgu_2, @ds_jokgu, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 147), @di_jokgu_0, @ds_jokgu, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 147), @di_jokgu_1, @ds_jokgu, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 147), @di_jokgu_2, @ds_jokgu, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 148), @di_jokgu_0, @ds_jokgu, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 148), @di_jokgu_1, @ds_jokgu, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 148), @di_jokgu_2, @ds_jokgu, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 149), @di_jokgu_0, @ds_jokgu, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 149), @di_jokgu_1, @ds_jokgu, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 149), @di_jokgu_2, @ds_jokgu, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 150), @di_jokgu_0, @ds_jokgu, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 150), @di_jokgu_1, @ds_jokgu, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 150), @di_jokgu_2, @ds_jokgu, 5, 'OBSERVED'
) v
 WHERE @demo_ready = 1;

INSERT INTO match_evaluation_score (evaluation_id, item_id, sport_id, score, observation_status)
SELECT * FROM (
                 SELECT (@de + 151) AS evaluation_id, @di_futsal_0 AS item_id, @ds_futsal AS sport_id, 4 AS score, 'OBSERVED' AS observation_status
       UNION ALL SELECT (@de + 151), @di_futsal_1, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 151), @di_futsal_2, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 152), @di_futsal_0, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 152), @di_futsal_1, @ds_futsal, 8, 'OBSERVED'
       UNION ALL SELECT (@de + 152), @di_futsal_2, @ds_futsal, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 153), @di_futsal_0, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 153), @di_futsal_1, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 153), @di_futsal_2, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 154), @di_futsal_0, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 154), @di_futsal_1, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 154), @di_futsal_2, @ds_futsal, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 155), @di_futsal_0, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 155), @di_futsal_1, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 155), @di_futsal_2, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 156), @di_futsal_0, @ds_futsal, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 156), @di_futsal_1, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 156), @di_futsal_2, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 157), @di_futsal_0, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 157), @di_futsal_1, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 157), @di_futsal_2, @ds_futsal, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 158), @di_futsal_0, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 158), @di_futsal_1, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 158), @di_futsal_2, @ds_futsal, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 159), @di_futsal_0, @ds_futsal, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 159), @di_futsal_1, @ds_futsal, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 159), @di_futsal_2, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 160), @di_futsal_0, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 160), @di_futsal_1, @ds_futsal, 8, 'OBSERVED'
       UNION ALL SELECT (@de + 160), @di_futsal_2, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 161), @di_futsal_0, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 161), @di_futsal_1, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 161), @di_futsal_2, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 162), @di_futsal_0, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 162), @di_futsal_1, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 162), @di_futsal_2, @ds_futsal, 8, 'OBSERVED'
       UNION ALL SELECT (@de + 163), @di_futsal_0, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 163), @di_futsal_1, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 163), @di_futsal_2, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 164), @di_futsal_0, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 164), @di_futsal_1, @ds_futsal, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 164), @di_futsal_2, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 165), @di_futsal_0, @ds_futsal, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 165), @di_futsal_1, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 165), @di_futsal_2, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 166), @di_futsal_0, @ds_futsal, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 166), @di_futsal_1, @ds_futsal, 3, 'OBSERVED'
       UNION ALL SELECT (@de + 166), @di_futsal_2, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 167), @di_futsal_0, @ds_futsal, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 167), @di_futsal_1, @ds_futsal, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 167), @di_futsal_2, @ds_futsal, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 168), @di_futsal_0, @ds_futsal, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 168), @di_futsal_1, @ds_futsal, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 168), @di_futsal_2, @ds_futsal, 2, 'OBSERVED'
       UNION ALL SELECT (@de + 169), @di_tennis_0, @ds_tennis, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 169), @di_tennis_1, @ds_tennis, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 169), @di_tennis_2, @ds_tennis, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 170), @di_tennis_0, @ds_tennis, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 170), @di_tennis_1, @ds_tennis, 8, 'OBSERVED'
       UNION ALL SELECT (@de + 170), @di_tennis_2, @ds_tennis, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 171), @di_tennis_0, @ds_tennis, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 171), @di_tennis_1, @ds_tennis, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 171), @di_tennis_2, @ds_tennis, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 172), @di_tennis_0, @ds_tennis, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 172), @di_tennis_1, @ds_tennis, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 172), @di_tennis_2, @ds_tennis, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 173), @di_tennis_0, @ds_tennis, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 173), @di_tennis_1, @ds_tennis, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 173), @di_tennis_2, @ds_tennis, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 174), @di_tennis_0, @ds_tennis, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 174), @di_tennis_1, @ds_tennis, 8, 'OBSERVED'
       UNION ALL SELECT (@de + 174), @di_tennis_2, @ds_tennis, 9, 'OBSERVED'
       UNION ALL SELECT (@de + 175), @di_badminton_0, @ds_badminton, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 175), @di_badminton_1, @ds_badminton, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 175), @di_badminton_2, @ds_badminton, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 176), @di_badminton_0, @ds_badminton, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 176), @di_badminton_1, @ds_badminton, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 176), @di_badminton_2, @ds_badminton, 8, 'OBSERVED'
       UNION ALL SELECT (@de + 177), @di_badminton_0, @ds_badminton, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 177), @di_badminton_1, @ds_badminton, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 177), @di_badminton_2, @ds_badminton, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 178), @di_badminton_0, @ds_badminton, 7, 'OBSERVED'
       UNION ALL SELECT (@de + 178), @di_badminton_1, @ds_badminton, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 178), @di_badminton_2, @ds_badminton, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 179), @di_badminton_0, @ds_badminton, 4, 'OBSERVED'
       UNION ALL SELECT (@de + 179), @di_badminton_1, @ds_badminton, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 179), @di_badminton_2, @ds_badminton, 5, 'OBSERVED'
       UNION ALL SELECT (@de + 180), @di_badminton_0, @ds_badminton, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 180), @di_badminton_1, @ds_badminton, 6, 'OBSERVED'
       UNION ALL SELECT (@de + 180), @di_badminton_2, @ds_badminton, 4, 'OBSERVED'
) v
 WHERE @demo_ready = 1;

-- 7. 매칭방 사진. 사전 확인을 통과한 DB(이미 적용된 DB 포함)에서만 바꾼다.
UPDATE social_match sm JOIN sport s ON s.sport_id = sm.sport_id
   SET sm.photo_url = CASE s.name WHEN '축구+풋살' THEN '/images/matches/soccer.jpg' WHEN '테니스' THEN '/images/matches/tennis.jpg' WHEN '배드민턴' THEN '/images/matches/badminton.jpg' WHEN '농구' THEN '/images/matches/basketball.jpg' WHEN '족구' THEN '/images/matches/jokgu.jpg' END
 WHERE (sm.photo_url IS NULL OR sm.photo_url LIKE '/uploads/matches/seed/%')
   AND s.name IN ('축구+풋살', '테니스', '배드민턴', '농구', '족구')
   AND (@demo_ready = 1 OR @demo_already_applied = 1);

COMMIT;

-- 확인: 이 파일로 넣은 행 수 (이미 적용된 DB에서도 같은 값이 나와야 한다)
SELECT (SELECT COUNT(*) FROM app_user WHERE email LIKE 'demo-%@spoit.test') AS demo_users,
       (SELECT COUNT(*) FROM club WHERE club_name IN ('뚝섬 FC', '망원 풋살 크루', '잠실 풋살 클럽', '반포 농구단', '광나루 바스켓', '한강 족구회', '뚝섬 셔틀콕', '마포 랠리 테니스') AND deleted_at IS NULL) AS demo_clubs,
       (SELECT COUNT(*) FROM social_match sm JOIN app_user u ON u.user_id = sm.created_by
         WHERE u.email LIKE 'facility-__@spoit.test' AND sm.start_at >= '2026-11-01' AND sm.title REGEXP '^[가-힣]+ [월화수목금토일]요일 (오전|오후|저녁) (풋살|테니스|배드민턴|농구|족구) [0-9]+:[0-9]+$') AS demo_matches,
       (SELECT COUNT(*) FROM match_application a JOIN app_user u ON u.user_id = a.applicant_user_id WHERE u.email LIKE 'demo-%@spoit.test') AS demo_applications,
       (SELECT COUNT(*) FROM social_match WHERE photo_url IS NULL OR photo_url LIKE '/uploads/matches/seed/%') AS rooms_without_photo;
-- 기대값: demo_users 101, demo_clubs 8, demo_matches 189, demo_applications 749 이상(데모 회원이 앱에서 신청하면 늘어남), rooms_without_photo 0
