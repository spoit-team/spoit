-- =====================================================================
-- 시설 예약 운영 일정: 모집 마감(deadline_hours) 되돌리기
--
-- 대상: 20261028-facility-reservation-schedule.sql 이 적용된 개발·운영 DB
-- 내용:
--   * facility_sport.deadline_hours 컬럼과 CHECK 제약을 제거한다.
--   * 마감 시간 기능(이용 1·3·6·24시간 전)은 신청 경로에 연결되기 전에
--     완전 제거가 결정됐다. 참가 안내(facility_sport.note)는 유지한다.
-- 원칙:
--   * 기존 행을 삭제·변환하지 않는다. 컬럼 제거는 해당 컬럼의 데이터만
--     사라지며 다른 컬럼·테이블에 영향을 주지 않는다.
--   * MySQL DDL은 문장별로 자동 커밋된다. 백업과 쓰기 중단을 준비하고,
--     중간에 실패하면 재실행하지 말고 적용된 스키마를 확인한 뒤 별도 복구한다.
--   * db/schema.sql 은 fresh fixture 용도이며 실제 DB에 실행하지 않는다.
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 사전 점검: 컬럼·제약 존재 확인
--
-- 마이그레이션 전 적용 여부를 담당자가 눈으로 확인한다. 아래 질의가
-- 행을 반환하지 않으면 이 스크립트를 실행하지 않는다(이미 제거됨).
-- ---------------------------------------------------------------------
SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_DEFAULT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'facility_sport'
   AND COLUMN_NAME = 'deadline_hours';

SELECT CONSTRAINT_NAME
  FROM information_schema.TABLE_CONSTRAINTS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'facility_sport'
   AND CONSTRAINT_NAME = 'ck_facility_sport_deadline';

-- ---------------------------------------------------------------------
-- 2. CHECK 제약 제거
--
-- MySQL 8.x에서 CHECK 제약은 컬럼 삭제 전에 먼저 내려야 한다.
-- (컬럼이 참조되는 동안 DROP COLUMN이 실패할 수 있다.)
-- ---------------------------------------------------------------------
ALTER TABLE facility_sport
    DROP CONSTRAINT ck_facility_sport_deadline;

-- ---------------------------------------------------------------------
-- 3. 컬럼 제거
--
-- 저장된 마감 값(1·3·6·24)은 함께 사라진다. 이 기능은 어떤 화면·API에서도
-- 조회·판정에 사용되지 않으므로 데이터 손실의 영향 범위는 이 컬럼 자체다.
-- 되돌릴 필요가 생기면 20261028 마이그레이션의 ADD COLUMN 문장을 다시
-- 실행하면 되고, 기존 행은 DEFAULT 1 로 시작한다.
-- ---------------------------------------------------------------------
ALTER TABLE facility_sport
    DROP COLUMN deadline_hours;

-- ---------------------------------------------------------------------
-- 4. 사후 확인
--
-- 아래 질의가 deadline_hours 행을 반환하면 제거가 완료되지 않은 것이다.
-- ---------------------------------------------------------------------
SELECT COLUMN_NAME
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'facility_sport'
   AND COLUMN_NAME = 'deadline_hours';
