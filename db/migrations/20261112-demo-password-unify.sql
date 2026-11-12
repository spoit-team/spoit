-- =====================================================================
-- 데모·레이팅 회원 비밀번호를 seed 회원과 같은 개발용 비밀번호(spoit1234)로 통일
-- 대상: 20261111-demo-match-data.sql / 20261111-rating-demo-data.sql을 이미 적용한 DB
-- 실행: mysql --default-character-set=utf8mb4 <DB명> -e "source db/migrations/20261112-demo-password-unify.sql"
-- 규칙
--   * 20261111 두 파일은 seed(spoit1234)와 다른 비밀번호(spoit1234!)로 회원을 만들었다.
--     이 파일은 그 비밀번호 해시가 그대로 남아 있는 행만 새 해시로 바꾼다.
--     회원이 직접 바꾼 비밀번호는 해시가 달라 건드리지 않는다.
--   * 대상: demo-001~100@spoit.test, demo-presenter@spoit.test, rating-01~24@spoit.test
--   * 데이터만 바꾸며 여러 번 실행해도 안전하다(두 번째부터는 바뀌는 행이 0).
--   * 평문 비밀번호는 저장하지 않는다. 새 해시는 BCryptPasswordEncoder(강도 10)로 만들었다.
-- =====================================================================

SET NAMES utf8mb4;
START TRANSACTION;

SET @old_demo_hash = '$2b$10$nJEWjTEEvGGRB89hDIxRNemCToYJvZWZ.8cQ2wwzZV6TsYxvLuTpe';  -- spoit1234!
SET @new_demo_hash = '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG';  -- spoit1234

-- ---------------------------------------------------------------------
-- 1. 사전 확인: 바꿀 행 수(처음이면 demo_targets 101, rating_targets 24, 이미 적용했으면 둘 다 0)
-- ---------------------------------------------------------------------
SELECT COALESCE(SUM(email LIKE 'demo-%@spoit.test'), 0)   AS demo_targets,
       COALESCE(SUM(email LIKE 'rating-%@spoit.test'), 0) AS rating_targets
  FROM app_user
 WHERE password_hash = @old_demo_hash
   AND (email LIKE 'demo-%@spoit.test' OR email LIKE 'rating-%@spoit.test');

-- ---------------------------------------------------------------------
-- 2. 비밀번호 해시 교체
-- ---------------------------------------------------------------------
UPDATE app_user
   SET password_hash = @new_demo_hash
 WHERE password_hash = @old_demo_hash
   AND (email LIKE 'demo-%@spoit.test' OR email LIKE 'rating-%@spoit.test');

COMMIT;

-- ---------------------------------------------------------------------
-- 3. 사후 확인: old_hash_left 0이면 적용됨.
--    new_hash_users는 처음 적용한 DB라면 125(데모 101 + 레이팅 24)다.
-- ---------------------------------------------------------------------
SELECT (SELECT COUNT(*) FROM app_user WHERE password_hash = @old_demo_hash) AS old_hash_left,
       (SELECT COUNT(*) FROM app_user WHERE password_hash = @new_demo_hash) AS new_hash_users;
