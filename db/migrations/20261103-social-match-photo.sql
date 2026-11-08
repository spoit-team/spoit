-- =====================================================================
-- 매칭방 시설 사진: social_match.photo_url 컬럼
--
-- 대상: 개발·운영 DB
-- 내용:
--   * social_match.photo_url 컬럼을 추가한다. 시설 관리자가 매칭방을 만들거나 수정할 때 올린
--     시설 사진의 공개 URL이다(로컬 개발은 /uploads/..., 운영은 S3 주소).
--   * 사진이 없으면 NULL이고, 매칭방 목록 카드는 지금처럼 종목명을 보여 준다.
-- 원칙:
--   * 기존 행은 바꾸지 않는다(모두 NULL).
--   * MySQL DDL은 문장별로 자동 커밋된다. 중간에 실패하면 재실행하지 말고
--     적용된 스키마를 확인한 뒤 별도 복구한다.
--   * db/schema.sql 은 fresh fixture 용도이며 실제 DB에 실행하지 않는다.
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 사전 점검: 아래 질의가 행을 반환하면 이미 적용된 것이므로 실행하지 않는다.
-- ---------------------------------------------------------------------
SELECT COLUMN_NAME
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'social_match'
   AND COLUMN_NAME = 'photo_url';

-- ---------------------------------------------------------------------
-- 2. 컬럼 추가
-- ---------------------------------------------------------------------
ALTER TABLE social_match
    ADD COLUMN photo_url VARCHAR(500) NULL
        COMMENT '시설 사진 공개 URL (로컬 /uploads/..., 운영 S3), 없으면 종목명 표시'
        AFTER status;

-- ---------------------------------------------------------------------
-- 3. 사후 확인: 컬럼 1행(photo_url, varchar(500), YES)이 나와야 한다.
-- ---------------------------------------------------------------------
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'social_match'
   AND COLUMN_NAME = 'photo_url';
