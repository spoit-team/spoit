-- =====================================================================
-- 회원 탈퇴·이용정지(#36): app_user.status 를 날짜 컬럼으로 대체
--
-- 대상: 개발·운영 DB
-- 내용:
--   * app_user.deleted_at(탈퇴 시각), app_user.penalty_time(이용정지 종료 시각)을 추가한다.
--   * app_user.status 를 제거한다. 코드는 #36부터 status 를 읽지도 쓰지도 않는다.
-- 원칙:
--   * status 에는 'ACTIVE'·'승인' 말고도 '정지'(이용정지)와 '대기'·'거절'(시설 관리자 승인 상태)이
--     들어 있을 수 있다. 그대로 지우면 정지 회원의 정지가 풀리고, 승인 대기·거절된 시설 관리자가
--     로그인할 수 있게 된다. 이 스크립트는 그런 행을 임의로 바꾸지 않고 "2. 사전 점검"에서 멈춘다.
--   * 2단계에서 멈추면 1단계(컬럼 추가)는 이미 적용된 상태다. 이 파일을 다시 실행하지 말고,
--     담당자가 목록의 회원을 처리한 뒤(예: 정지 → penalty_time 에 정지 종료 시각 지정,
--     대기·거절 → 승인 여부를 정해 role 조정) status 를 'ACTIVE' 로 바꾸고
--     3단계 ALTER 와 4단계 확인 질의만 실행한다.
--   * 배포 순서: status 를 지우면 이를 쓰는 이전 버전 앱은 회원 조회에 실패한다.
--     #36 이후 버전 배포 직전에 적용한다.
--   * MySQL DDL은 문장별로 자동 커밋된다. 중간에 실패하면 재실행하지 말고
--     적용된 스키마를 확인한 뒤 별도 복구한다.
--   * db/schema.sql 은 fresh fixture 용도이며 실제 DB에 실행하지 않는다.
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 0. 적용 여부: 아래 질의가 status 만 반환하면 실행한다.
--    deleted_at·penalty_time 이 이미 있으면 1단계는 적용된 것이므로 위 원칙대로 3단계부터 진행하고,
--    status 가 없으면 이미 모두 적용된 것이므로 실행하지 않는다.
-- ---------------------------------------------------------------------
SELECT COLUMN_NAME
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'app_user'
   AND COLUMN_NAME IN ('status', 'deleted_at', 'penalty_time');

-- ---------------------------------------------------------------------
-- 1. 탈퇴·이용정지 컬럼 추가
-- ---------------------------------------------------------------------
ALTER TABLE app_user
    ADD COLUMN deleted_at DATETIME NULL
        COMMENT '탈퇴 시각. NULL이면 미탈퇴, 값이 있으면 탈퇴 회원'
        AFTER created_at,
    ADD COLUMN penalty_time DATETIME NULL
        COMMENT '이용정지 종료 시각. NULL이면 정지 종료 시각 미설정'
        AFTER deleted_at;

-- ---------------------------------------------------------------------
-- 2. 사전 점검: 지우면 의미가 사라지는 status
--
-- 먼저 처리가 필요한 회원 목록을 보여 준다. 목록이 비어 있지 않으면 바로 아래 임시 테이블의
-- CHECK 제약 위반으로 이 지점에서 실행이 중단된다.
-- (mysql 클라이언트는 --force 없이 첫 오류에서 중단한다.)
-- ---------------------------------------------------------------------
SELECT user_id, email, nickname, role, status
  FROM app_user
 WHERE status NOT IN ('ACTIVE', '승인')
 ORDER BY user_id;

DROP TEMPORARY TABLE IF EXISTS app_user_status_guard;
CREATE TEMPORARY TABLE app_user_status_guard (
    status VARCHAR(30) NOT NULL,
    CONSTRAINT ck_app_user_status_guard CHECK (status IN ('ACTIVE', '승인'))
);

INSERT INTO app_user_status_guard (status)
SELECT DISTINCT status
  FROM app_user;

DROP TEMPORARY TABLE app_user_status_guard;

-- ---------------------------------------------------------------------
-- 3. status 제거
-- ---------------------------------------------------------------------
ALTER TABLE app_user
    DROP COLUMN status;

-- ---------------------------------------------------------------------
-- 4. 사후 확인: deleted_at, penalty_time 2행이 나오고 status 는 없어야 한다.
-- ---------------------------------------------------------------------
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'app_user'
   AND COLUMN_NAME IN ('status', 'deleted_at', 'penalty_time')
 ORDER BY ORDINAL_POSITION;
