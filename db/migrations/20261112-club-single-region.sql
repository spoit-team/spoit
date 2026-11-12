-- =====================================================================
-- 동호회 활동 지역을 동호회마다 1개로 정리
-- 대상: 20261111-demo-match-data.sql까지 적용한 DB (로컬·운영). 그 전 DB에도 실행할 수 있다.
-- 이유: 동호회 만들기·수정은 지역을 1개만 저장하는데(시설도 지역 1개), seed와 시연 데이터에 2개짜리가 남아
--       목록 카드에 "송파구, 강동구"처럼 두 지역이 보인다.
-- 내용
--   * 아래 목록의 동호회는 이름에 맞는 구만 남긴다(그 구가 없으면 건드리지 않는다).
--   * 목록에 없는데 지역이 2개 이상인 동호회는 수정 화면이 보여 주는 지역(가장 작은 번호)만 남긴다.
--   * 시연 데이터 동호회 4곳의 소개 글에서 두 지역 문구를 한 지역으로 바꾼다(글이 처음 그대로일 때만).
-- 원칙: 한 트랜잭션. 여러 번 실행해도 결과가 같다. 동호회 행은 지우지 않는다.
-- =====================================================================

SET NAMES utf8mb4;
START TRANSACTION;

-- 0. 동호회별로 남길 구 (seed 4곳 + 20261111 시연 데이터 4곳)
CREATE TEMPORARY TABLE tmp_club_keep_region (
    club_name  VARCHAR(50) NOT NULL,
    keep_name  VARCHAR(50) NOT NULL,
    PRIMARY KEY (club_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO tmp_club_keep_region (club_name, keep_name) VALUES
  ('주말 풋살 클럽', '성동구'),
  ('노원 농구회', '노원구'),
  ('송파 셔틀콕', '송파구'),
  ('광진 테니스회', '광진구'),
  ('잠실 풋살 클럽', '송파구'),
  ('광나루 바스켓', '강동구'),
  ('한강 족구회', '광진구'),
  ('뚝섬 셔틀콕', '광진구');

-- 1. 지울 지역 행을 먼저 모은다(같은 표를 읽으면서 지울 수 없어서)
CREATE TEMPORARY TABLE tmp_club_region_delete (
    club_id    BIGINT NOT NULL,
    region_id  BIGINT NOT NULL,
    PRIMARY KEY (club_id, region_id)
) ENGINE=InnoDB;

-- 1-1. 목록에 있는 동호회: 남길 구가 실제로 연결돼 있을 때만 나머지를 지운다
INSERT INTO tmp_club_region_delete (club_id, region_id)
SELECT cr.club_id, cr.region_id
  FROM club_region cr
  JOIN club c ON c.club_id = cr.club_id
  JOIN tmp_club_keep_region k ON k.club_name = c.club_name
  JOIN region r ON r.region_id = cr.region_id
 WHERE r.name <> k.keep_name
   AND EXISTS (SELECT 1 FROM club_region kr JOIN region kn ON kn.region_id = kr.region_id
                WHERE kr.club_id = cr.club_id AND kn.name = k.keep_name);

-- 1-2. 그 밖에 지역이 2개 이상인 동호회: 가장 작은 번호만 남긴다
INSERT IGNORE INTO tmp_club_region_delete (club_id, region_id)
SELECT cr.club_id, cr.region_id
  FROM club_region cr
  JOIN (SELECT club_id, MIN(region_id) AS keep_id FROM club_region GROUP BY club_id HAVING COUNT(*) > 1) m
    ON m.club_id = cr.club_id
  JOIN club c ON c.club_id = cr.club_id
 WHERE cr.region_id <> m.keep_id
   AND NOT EXISTS (SELECT 1 FROM tmp_club_keep_region k WHERE k.club_name = c.club_name);

SELECT COUNT(*) AS region_rows_to_delete FROM tmp_club_region_delete;

DELETE cr FROM club_region cr
  JOIN tmp_club_region_delete d ON d.club_id = cr.club_id AND d.region_id = cr.region_id;

-- 2. 소개 글의 두 지역 문구 (20261111 시연 데이터가 넣은 글 그대로일 때만 바꾼다)
UPDATE club SET intro = '송파구에서 함께 풋살 하는 동호회입니다. 초보도 환영합니다.'
 WHERE club_name = '잠실 풋살 클럽' AND intro = '송파구, 강동구에서 함께 풋살 하는 동호회입니다. 초보도 환영합니다.';
UPDATE club SET intro = '강동구에서 함께 농구 하는 동호회입니다. 초보도 환영합니다.'
 WHERE club_name = '광나루 바스켓' AND intro = '강동구, 송파구에서 함께 농구 하는 동호회입니다. 초보도 환영합니다.';
UPDATE club SET intro = '광진구에서 함께 족구 하는 동호회입니다. 초보도 환영합니다.'
 WHERE club_name = '한강 족구회' AND intro = '광진구, 강동구에서 함께 족구 하는 동호회입니다. 초보도 환영합니다.';
UPDATE club SET intro = '광진구에서 함께 배드민턴 하는 동호회입니다. 초보도 환영합니다.'
 WHERE club_name = '뚝섬 셔틀콕' AND intro = '광진구, 성동구에서 함께 배드민턴 하는 동호회입니다. 초보도 환영합니다.';

DROP TEMPORARY TABLE tmp_club_region_delete;
DROP TEMPORARY TABLE tmp_club_keep_region;

COMMIT;

-- 확인: 둘 다 0이면 적용된 것이다
SELECT (SELECT COUNT(*) FROM (SELECT club_id FROM club_region GROUP BY club_id HAVING COUNT(*) > 1) t) AS clubs_with_many_regions,
       (SELECT COUNT(*) FROM club WHERE intro LIKE '%구, %구에서 함께%') AS intros_with_two_regions;
