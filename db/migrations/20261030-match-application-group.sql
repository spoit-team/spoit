-- =====================================================================
-- RES-04 팀 단위 신청: match_application 팀 신청 묶음 컬럼
--
-- 대상: 개발·운영 DB
-- 내용:
--   * match_application.group_application_id 컬럼, 인덱스, 자기 참조 FK를 추가한다.
--   * 팀 신청은 팀원마다 신청 행을 하나씩 만들고, 대표 행의 application_id로 묶는다.
--     대표 행은 자기 자신을 가리키고, 개인 신청은 NULL이다.
-- 원칙:
--   * 기존 행은 바꾸지 않는다. 이전 방식의 동호회 신청(신청 1건 : 참가자 N명)은
--     NULL로 남아 단독 신청으로 처리된다.
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
   AND TABLE_NAME = 'match_application'
   AND COLUMN_NAME = 'group_application_id';

-- ---------------------------------------------------------------------
-- 2. 컬럼·인덱스·FK 추가
-- ---------------------------------------------------------------------
ALTER TABLE match_application
    ADD COLUMN group_application_id BIGINT NULL
        COMMENT 'RES-04 팀 신청 묶음: 대표 행의 application_id (대표 행은 자기 자신), 개인 신청이면 NULL'
        AFTER cancelled_by,
    ADD KEY idx_match_application_group (group_application_id),
    ADD CONSTRAINT fk_match_application_group
        FOREIGN KEY (group_application_id) REFERENCES match_application (application_id);

-- ---------------------------------------------------------------------
-- 3. 사후 확인: 컬럼 1행, FK 1행이 나와야 한다.
-- ---------------------------------------------------------------------
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'match_application'
   AND COLUMN_NAME = 'group_application_id';

SELECT CONSTRAINT_NAME
  FROM information_schema.TABLE_CONSTRAINTS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'match_application'
   AND CONSTRAINT_NAME = 'fk_match_application_group';
