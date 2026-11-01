-- =====================================================================
-- 동호회 이름 중복 방지·해체 이탈 유형(#37)
--
-- 대상: 개발·운영 DB
-- 내용:
--   * club.active_club_name 생성 컬럼(해체되지 않은 동호회만 이름을 가짐)과
--     유일 키 uk_club_active_name 을 추가한다. 해체된 동호회 이름은 다시 쓸 수 있다.
--   * club_member.leave_type 에 '해체'를 허용하도록 ck_club_member_leave 를 다시 만든다.
-- 원칙:
--   * 기존 행을 바꾸지 않는다. 활성 동호회 이름이 이미 겹치면 어느 쪽을 바꿀지 사람이
--     정해야 하므로 "1. 사전 점검"에서 멈춘다.
--   * 열 설명(COMMENT)만 바뀐 부분(club.club_name, club_member.leave_type,
--     club_join_request.message)은 동작에 영향이 없어 반영하지 않는다.
--   * MySQL DDL은 문장별로 자동 커밋된다. 중간에 실패하면 재실행하지 말고
--     적용된 스키마를 확인한 뒤 별도 복구한다.
--   * db/schema.sql 은 fresh fixture 용도이며 실제 DB에 실행하지 않는다.
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 0. 적용 여부: 아래 질의가 행을 반환하면 이미 적용된 것이므로 실행하지 않는다.
-- ---------------------------------------------------------------------
SELECT COLUMN_NAME
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'club'
   AND COLUMN_NAME = 'active_club_name';

-- ---------------------------------------------------------------------
-- 1. 사전 점검: 활성 동호회 이름 중복
--
-- 겹치는 이름 목록을 먼저 보여 준다. 목록이 비어 있지 않으면 바로 아래 임시 테이블의
-- PRIMARY KEY 위반으로 이 지점에서 실행이 중단된다. 유일 키와 같은 콜레이션으로 비교한다.
-- (mysql 클라이언트는 --force 없이 첫 오류에서 중단한다.)
-- ---------------------------------------------------------------------
SELECT club_name, COUNT(*) AS active_count, GROUP_CONCAT(club_id ORDER BY club_id) AS club_ids
  FROM club
 WHERE deleted_at IS NULL
 GROUP BY club_name
HAVING COUNT(*) > 1;

DROP TEMPORARY TABLE IF EXISTS club_active_name_guard;
CREATE TEMPORARY TABLE club_active_name_guard (
    club_name VARCHAR(50) NOT NULL,
    PRIMARY KEY (club_name)
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO club_active_name_guard (club_name)
SELECT club_name
  FROM club
 WHERE deleted_at IS NULL;

DROP TEMPORARY TABLE club_active_name_guard;

-- ---------------------------------------------------------------------
-- 2. 활성 동호회 이름 생성 컬럼과 유일 키
-- ---------------------------------------------------------------------
ALTER TABLE club
    ADD COLUMN active_club_name VARCHAR(50)
        AS (CASE WHEN deleted_at IS NULL THEN club_name END) STORED
        COMMENT '활성 동호회 이름 중복 방지용 값 (생성 컬럼)'
        AFTER created_at,
    ADD UNIQUE KEY uk_club_active_name (active_club_name);

-- ---------------------------------------------------------------------
-- 3. 이탈 유형 '해체' 허용
--
-- 같은 이름의 제약을 한 문장에서 지우고 만들 수 없어 두 문장으로 나눈다.
-- 두 문장 사이에는 이탈 유형 검사가 없으므로 앱 쓰기를 멈춘 상태에서 실행한다.
-- ---------------------------------------------------------------------
ALTER TABLE club_member
    DROP CHECK ck_club_member_leave;

ALTER TABLE club_member
    ADD CONSTRAINT ck_club_member_leave CHECK (
        (left_at IS NULL AND leave_type IS NULL AND kick_reason IS NULL)
        OR (left_at IS NOT NULL AND leave_type = '탈퇴' AND kick_reason IS NULL)
        OR (left_at IS NOT NULL AND leave_type = '내보내기')
        OR (left_at IS NOT NULL AND leave_type = '해체' AND kick_reason IS NULL)
    );

-- ---------------------------------------------------------------------
-- 4. 사후 확인: 생성 컬럼 1행, 유일 키 1행이 나오고, leave_type_terms 가 4여야 한다.
--    (적용 전 3: IS NULL·탈퇴·내보내기, 적용 후 4: 해체 추가. 한글 값은 데이터 사전에
--    인코딩된 형태로 저장되어 문자열 검색으로는 확인할 수 없다.)
-- ---------------------------------------------------------------------
SELECT COLUMN_NAME, COLUMN_TYPE, EXTRA
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'club'
   AND COLUMN_NAME = 'active_club_name';

SELECT INDEX_NAME, NON_UNIQUE
  FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'club'
   AND INDEX_NAME = 'uk_club_active_name';

SELECT CONSTRAINT_NAME,
       (CHAR_LENGTH(CHECK_CLAUSE) - CHAR_LENGTH(REPLACE(CHECK_CLAUSE, '`leave_type`', '')))
           / CHAR_LENGTH('`leave_type`') AS leave_type_terms
  FROM information_schema.CHECK_CONSTRAINTS
 WHERE CONSTRAINT_SCHEMA = DATABASE()
   AND CONSTRAINT_NAME = 'ck_club_member_leave';
