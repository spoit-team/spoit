-- =====================================================================
-- 평가 항목 정리: 종목마다 공격·연결·수비 3개
--
-- 대상: 개발·운영 DB (평가 항목 시드가 들어 있는 DB)
-- 내용:
--   * 경기 중 팀장이 직접 보기 어려운 항목(체력·협동심·볼 터치)을 끈다.
--   * 족구 '토스·연결'(JOKGU_TOSS)을 새 행으로 추가한다.
--   * 농구 '수비'는 범위만 넓혀 '수비·리바운드'로 이름을 고친다.
--   * 활성 항목의 가중치를 모두 1.00으로, 표시 순서를 1~3으로 맞춘다.
-- 원칙:
--   * 저장된 평가 점수가 항목 행을 참조하므로 행은 지우지 않고 is_active = FALSE로 끈다.
--   * 뜻이 바뀌는 교체는 새 행으로 넣는다. 기존 행 이름을 바꾸면 과거 점수의 의미가 바뀐다.
--   * 종목 번호는 DB마다 다를 수 있어 item_code로 행을 찾는다.
--   * 여러 번 실행해도 결과가 같다. 데이터만 바꾸고 스키마는 바꾸지 않는다.
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 끌 항목
-- ---------------------------------------------------------------------
UPDATE sport_evaluation_item
   SET is_active = FALSE
 WHERE item_code IN ('SOCCER_STAMINA', 'SOCCER_TEAMWORK', 'FUTSAL_TOUCH', 'JOKGU_TEAMWORK');

-- ---------------------------------------------------------------------
-- 2. 범위만 넓히는 이름 변경
-- ---------------------------------------------------------------------
UPDATE sport_evaluation_item
   SET item_name = '수비·리바운드'
 WHERE item_code = 'BASKET_DEFENSE';

-- ---------------------------------------------------------------------
-- 3. 족구 토스·연결 추가. 같은 종목에 이미 있으면 넣지 않는다.
--    INSERT 대상 테이블은 서브쿼리로 읽을 수 없어 존재 확인을 LEFT JOIN으로 한다.
-- ---------------------------------------------------------------------
INSERT INTO sport_evaluation_item (sport_id, item_code, item_name, weight, scale_max, display_order, is_active)
SELECT kick.sport_id, 'JOKGU_TOSS', '토스·연결', 1.00, 10, 2, TRUE
  FROM sport_evaluation_item kick
  LEFT JOIN sport_evaluation_item toss
         ON toss.sport_id = kick.sport_id
        AND toss.item_code = 'JOKGU_TOSS'
 WHERE kick.item_code = 'JOKGU_KICK'
   AND toss.item_id IS NULL;

-- ---------------------------------------------------------------------
-- 4. 활성 항목의 가중치와 순서(공격·연결·수비)
-- ---------------------------------------------------------------------
UPDATE sport_evaluation_item
   SET weight = 1.00,
       is_active = TRUE,
       display_order = CASE item_code
           WHEN 'SOCCER_TECH' THEN 1
           WHEN 'FUTSAL_PASS' THEN 2
           WHEN 'FUTSAL_POSITION' THEN 3
           WHEN 'JOKGU_KICK' THEN 1
           WHEN 'JOKGU_TOSS' THEN 2
           WHEN 'JOKGU_DEFENSE' THEN 3
           WHEN 'BASKET_SHOOT' THEN 1
           WHEN 'BASKET_PASS' THEN 2
           WHEN 'BASKET_DEFENSE' THEN 3
           WHEN 'BADMINTON_SMASH' THEN 1
           WHEN 'BADMINTON_FOOTWORK' THEN 2
           WHEN 'BADMINTON_SERVE' THEN 3
           WHEN 'TENNIS_SERVE' THEN 1
           WHEN 'TENNIS_RALLY' THEN 2
           WHEN 'TENNIS_POSITION' THEN 3
       END
 WHERE item_code IN ('SOCCER_TECH', 'FUTSAL_PASS', 'FUTSAL_POSITION',
                     'JOKGU_KICK', 'JOKGU_TOSS', 'JOKGU_DEFENSE',
                     'BASKET_SHOOT', 'BASKET_PASS', 'BASKET_DEFENSE',
                     'BADMINTON_SMASH', 'BADMINTON_FOOTWORK', 'BADMINTON_SERVE',
                     'TENNIS_SERVE', 'TENNIS_RALLY', 'TENNIS_POSITION');

-- ---------------------------------------------------------------------
-- 5. 사후 확인: 종목마다 active_items 3, weight_sum 3.00이 나와야 한다.
-- ---------------------------------------------------------------------
SELECT s.name AS sport,
       COUNT(*) AS active_items,
       SUM(i.weight) AS weight_sum,
       GROUP_CONCAT(i.item_name ORDER BY i.display_order SEPARATOR ', ') AS items
  FROM sport_evaluation_item i
  JOIN sport s ON s.sport_id = i.sport_id
 WHERE i.is_active = TRUE
 GROUP BY s.sport_id, s.name
 ORDER BY s.sport_id;
