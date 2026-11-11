-- db/migrations/20261111-rating-demo-data.sql 적용 전 상태.
-- RatingDemoMigrationIntegrationTest 전용. 마이그레이션이 이름으로 찾는 행만 둔다:
-- 서울특별시와 시설이 있는 구, 종목 2개, 활성 평가 항목 6개, 시설 8곳과 담당자 계정, 해시·역할을 복사할 demo-001@spoit.test.
-- 번호는 다른 fixture와 겹치지 않게 9301번대를 쓴다.

INSERT INTO region (region_id, parent_region_id, name) VALUES
    (1, NULL, '서울특별시'),
    (6, 1, '중구'),
    (7, 1, '용산구'),
    (9, 1, '광진구'),
    (18, 1, '마포구'),
    (24, 1, '동작구'),
    (26, 1, '서초구'),
    (28, 1, '송파구');

INSERT INTO sport (sport_id, name) VALUES
    (1, '테니스'),
    (2, '축구+풋살');

INSERT INTO sport_evaluation_item (item_id, sport_id, item_code, item_name, weight, scale_max, display_order, is_active) VALUES
    (9301, 2, 'SOCCER_TECH', '개인 기술', 1.00, 10, 1, TRUE),
    (9302, 2, 'FUTSAL_PASS', '패스', 1.00, 10, 2, TRUE),
    (9303, 2, 'FUTSAL_POSITION', '수비 위치', 1.00, 10, 3, TRUE),
    (9304, 1, 'TENNIS_SERVE', '서브', 1.00, 10, 1, TRUE),
    (9305, 1, 'TENNIS_RALLY', '랠리', 1.00, 10, 2, TRUE),
    (9306, 1, 'TENNIS_POSITION', '포지셔닝', 1.00, 10, 3, TRUE);

INSERT INTO app_user (user_id, email, password_hash, nickname, phone, role) VALUES
    (9301, 'facility-01@spoit.test', 'rating-demo-test-manager-hash', '시설관리자01', '010-0000-9301', 'FACILITY_MANAGER'),
    (9302, 'facility-02@spoit.test', 'rating-demo-test-manager-hash', '시설관리자02', '010-0000-9302', 'FACILITY_MANAGER'),
    (9303, 'facility-03@spoit.test', 'rating-demo-test-manager-hash', '시설관리자03', '010-0000-9303', 'FACILITY_MANAGER'),
    (9304, 'facility-04@spoit.test', 'rating-demo-test-manager-hash', '시설관리자04', '010-0000-9304', 'FACILITY_MANAGER'),
    (9306, 'facility-06@spoit.test', 'rating-demo-test-manager-hash', '시설관리자06', '010-0000-9306', 'FACILITY_MANAGER'),
    (9307, 'facility-07@spoit.test', 'rating-demo-test-manager-hash', '시설관리자07', '010-0000-9307', 'FACILITY_MANAGER'),
    (9308, 'facility-08@spoit.test', 'rating-demo-test-manager-hash', '시설관리자08', '010-0000-9308', 'FACILITY_MANAGER'),
    (9309, 'demo-001@spoit.test', 'rating-demo-test-demo-hash', '데모 회원', '010-0000-9309', 'MEMBER');

INSERT INTO facility (facility_id, region_id, name, address, latitude, longitude, concurrent_match_limit, status, deleted_at) VALUES
    (9301, 28, '잠실종합운동장 풋살경기장', '서울특별시 송파구 잠실종합운동장 풋살경기장', 37.5134800, 127.0766200, 1, '운영 중', NULL),
    (9302, 18, '서울월드컵경기장 풋살구장', '서울특별시 마포구 서울월드컵경기장 풋살구장', 37.5725400, 126.8990040, 1, '운영 중', NULL),
    (9303, 9, '뚝섬한강공원 축구장 1', '서울특별시 광진구 뚝섬한강공원 축구장 1', 37.5280370, 127.0764000, 1, '운영 중', NULL),
    (9304, 24, '보라매공원 인조잔디축구장', '서울특별시 동작구 보라매공원 인조잔디축구장', 37.4910100, 126.9160500, 1, '운영 중', NULL),
    (9305, 7, '한남테니스장', '서울특별시 용산구 한남테니스장', 37.5467000, 127.0039300, 1, '운영 중', NULL),
    (9306, 26, '잠원한강테니스장', '서울특별시 서초구 잠원한강테니스장', 37.5207500, 127.0122800, 1, '운영 중', NULL),
    (9307, 18, '월드컵공원 테니스장', '서울특별시 마포구 월드컵공원 테니스장', 37.5633500, 126.8979100, 1, '운영 중', NULL),
    (9308, 6, '장충테니스장', '서울특별시 중구 장충테니스장', 37.5540060, 127.0016720, 1, '운영 중', NULL);

INSERT INTO facility_sport (facility_id, sport_id, default_min, default_max, default_fee, default_skill) VALUES
    (9301, 2, 6, 12, 8000.00, '중급'),
    (9302, 2, 6, 12, 8000.00, '중급'),
    (9303, 2, 6, 12, 8000.00, '중급'),
    (9304, 2, 6, 12, 8000.00, '중급'),
    (9305, 1, 2, 4, 4000.00, '초급'),
    (9306, 1, 2, 4, 4000.00, '초급'),
    (9307, 1, 2, 4, 4000.00, '초급'),
    (9308, 1, 2, 4, 4000.00, '초급');

INSERT INTO facility_manager (facility_id, user_id) VALUES
    (9301, 9307),
    (9302, 9301),
    (9303, 9302),
    (9304, 9308),
    (9305, 9303),
    (9306, 9304),
    (9307, 9301),
    (9308, 9306);
