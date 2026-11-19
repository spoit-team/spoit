-- =====================================================================
-- 매칭방 실력 조건을 추천 티어로 변경
-- 대상: 20261113-club-existing-data.sql까지 적용한 DB (로컬·운영). 그 전 DB에도 실행할 수 있다.
-- 이유: 매칭방 생성 폼의 실력 수준이 초급·중급·고급에서 레이팅 티어(브론즈~다이아)로 바뀌었다.
--       기존 값이 남아 있으면 목록에 예전 값이 보이고, 관리자가 그 방을 수정하면 저장이 거부된다.
-- 내용: social_match.required_skill만 바꾼다. 초급→브론즈, 중급→골드, 고급→다이아.
--       facility_sport.default_skill은 매칭방 생성에 쓰지 않으므로 건드리지 않는다.
-- 원칙: 한 트랜잭션. 여러 번 실행해도 결과가 같다. 테이블 구조는 바꾸지 않는다.
-- =====================================================================

SET NAMES utf8mb4;
START TRANSACTION;

UPDATE social_match SET required_skill = '브론즈' WHERE required_skill = '초급';
UPDATE social_match SET required_skill = '골드'   WHERE required_skill = '중급';
UPDATE social_match SET required_skill = '다이아' WHERE required_skill = '고급';

COMMIT;

-- 확인: 0이면 적용된 것이다
SELECT COUNT(*) AS rooms_with_old_skill
  FROM social_match
 WHERE required_skill IN ('초급', '중급', '고급');
