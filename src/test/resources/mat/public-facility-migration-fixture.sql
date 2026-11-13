-- db/migrations/20261110-seoul-public-facilities.sql 적용 전 상태.
-- PublicFacilityMigrationIntegrationTest 전용. 마이그레이션이 기대하는 db/seed.sql의 행만 같은 번호로 둔다:
-- 서울특별시와 기존 구 11개, 종목 5개, 새 계정이 해시와 역할을 복사할 facility@spoit.test.
-- 해시는 실제 비밀번호의 값이 아니라 복사됐는지만 확인하는 표시 값이다.

INSERT INTO region (region_id, parent_region_id, name) VALUES
    (1, NULL, '서울특별시'),
    (5, 1, '종로구'),
    (8, 1, '성동구'),
    (9, 1, '광진구'),
    (15, 1, '노원구'),
    (16, 1, '은평구'),
    (18, 1, '마포구'),
    (20, 1, '강서구'),
    (23, 1, '영등포구'),
    (25, 1, '관악구'),
    (26, 1, '서초구'),
    (28, 1, '송파구');

INSERT INTO sport (sport_id, name) VALUES
    (1, '테니스'),
    (2, '축구+풋살'),
    (3, '족구'),
    (4, '농구'),
    (5, '배드민턴');

INSERT INTO app_user (user_id, email, password_hash, nickname, phone, role) VALUES
    (2, 'facility@spoit.test', 'public-facility-migration-test-hash', '시설 테스트', '010-0000-0002', 'FACILITY_MANAGER');
