-- =====================================================================
-- 동호회 해시태그 테이블 삭제
--
-- 대상: hashtag, club_hashtag 테이블이 있는 개발·운영 DB
-- 내용:
--   * club_hashtag(동호회-태그 연결)와 hashtag(태그) 테이블을 지운다.
--   * 태그 입력 화면이 없고 자바 코드에서 쓰는 곳이 없어 기능을 빼기로 했다.
-- 원칙:
--   * 다른 테이블은 건드리지 않는다.
--   * MySQL DDL은 문장별로 자동 커밋된다. 중간에 실패하면 다시 실행하지 말고
--     4번 사후 확인 질의로 어디까지 적용됐는지 본다.
-- =====================================================================

SET NAMES utf8mb4;

-- 1. 사전 점검: 두 테이블이 있는지 확인 (아무 행도 안 나오면 이미 적용됨, 실행하지 않는다)
SELECT TABLE_NAME
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME IN ('hashtag', 'club_hashtag');

-- 2. club_hashtag 먼저 삭제 (hashtag를 FK로 가리키는 쪽이라 먼저 지워야 함)
DROP TABLE club_hashtag;

-- 3. hashtag 삭제
DROP TABLE hashtag;

-- 4. 사후 확인: 행이 나오면 삭제가 끝나지 않은 것
SELECT TABLE_NAME
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME IN ('hashtag', 'club_hashtag');
