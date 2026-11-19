-- 대상: deleted_at 컬럼과 uk_app_user_email 유일 키가 있는 기존 MySQL DB.
-- 새 DB에 최신 db/schema.sql을 적용했다면 이 파일은 적용하지 않는다.
-- 백업 후 애플리케이션 쓰기를 중단하고, 새 버전을 시작하기 전에 한 번 적용한다.
-- 회원 ID와 기존 이메일은 보존한다. 탈퇴하지 않은 계정에만 이메일 유일성을 적용한다.

SET NAMES utf8mb4;

-- 사전 확인: email은 utf8mb4_0900_ai_ci, deleted_at은 존재, active_email은 없어야 한다.
SELECT COLUMN_NAME, COLUMN_TYPE, COLLATION_NAME, EXTRA
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'app_user'
   AND COLUMN_NAME IN ('email', 'deleted_at', 'active_email');

-- 사전 확인: uk_app_user_email은 email 한 열의 유일 키(NON_UNIQUE=0)여야 한다.
SELECT INDEX_NAME, COLUMN_NAME, NON_UNIQUE
  FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'app_user'
   AND INDEX_NAME IN ('uk_app_user_email', 'uk_app_user_active_email');

-- 한 ALTER에서 기존 유일 키를 교체한다. 새 유일 키가 중복으로 실패하면 변경도 적용되지 않는다.
ALTER TABLE app_user
    ADD COLUMN active_email VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci
        AS (CASE WHEN deleted_at IS NULL THEN email END) STORED
        COMMENT '탈퇴하지 않은 계정의 이메일 중복 방지용 값 (생성 컬럼)',
    ADD UNIQUE KEY uk_app_user_active_email (active_email),
    DROP INDEX uk_app_user_email;

-- 사후 확인: active_email을 참조하는 uk_app_user_active_email 한 행만 출력된다.
SELECT INDEX_NAME, COLUMN_NAME, NON_UNIQUE
  FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'app_user'
   AND INDEX_NAME IN ('uk_app_user_email', 'uk_app_user_active_email');
