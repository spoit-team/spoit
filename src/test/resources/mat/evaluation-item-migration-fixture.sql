-- db/migrations/20261109-evaluation-items.sql 적용 전 상태.
-- EvaluationItemMigrationIntegrationTest 전용. 다른 fixture와 겹치지 않게 931번대를 쓴다.
-- 평가 항목은 정리 전 db/seed.sql과 같은 코드·이름·가중치·순서이고, 종목 번호만 다르다.
-- 마이그레이션은 item_code로 행을 찾으므로 종목 번호가 달라도 같은 결과가 나와야 한다.
-- 종목 936의 항목은 마이그레이션 대상이 아니어서 바뀌면 안 된다.

INSERT INTO sport (sport_id, name) VALUES
    (931, '평가 항목 테스트 테니스'),
    (932, '평가 항목 테스트 축구+풋살'),
    (933, '평가 항목 테스트 족구'),
    (934, '평가 항목 테스트 농구'),
    (935, '평가 항목 테스트 배드민턴'),
    (936, '평가 항목 테스트 기타');

INSERT INTO sport_evaluation_item (
    item_id, sport_id, item_code, item_name, weight, scale_max, display_order, is_active
) VALUES
    (931, 932, 'SOCCER_TECH', '개인 기술', 0.40, 10, 1, TRUE),
    (932, 932, 'SOCCER_STAMINA', '체력', 0.30, 10, 2, TRUE),
    (933, 932, 'SOCCER_TEAMWORK', '협동심', 0.30, 10, 3, TRUE),
    (934, 932, 'FUTSAL_TOUCH', '볼 터치', 0.35, 10, 4, TRUE),
    (935, 932, 'FUTSAL_PASS', '패스', 0.35, 10, 5, TRUE),
    (936, 932, 'FUTSAL_POSITION', '수비 위치', 0.30, 10, 6, TRUE),
    (937, 934, 'BASKET_SHOOT', '슈팅', 0.40, 10, 1, TRUE),
    (938, 934, 'BASKET_DEFENSE', '수비', 0.30, 10, 2, TRUE),
    (939, 934, 'BASKET_PASS', '패스', 0.30, 10, 3, TRUE),
    (940, 935, 'BADMINTON_SMASH', '스매시', 0.40, 10, 1, TRUE),
    (941, 935, 'BADMINTON_FOOTWORK', '풋워크', 0.30, 10, 2, TRUE),
    (942, 935, 'BADMINTON_SERVE', '서브', 0.30, 10, 3, TRUE),
    (943, 931, 'TENNIS_SERVE', '서브', 0.40, 10, 1, TRUE),
    (944, 931, 'TENNIS_RALLY', '랠리', 0.30, 10, 2, TRUE),
    (945, 931, 'TENNIS_POSITION', '포지셔닝', 0.30, 10, 3, TRUE),
    (946, 933, 'JOKGU_KICK', '발기술', 0.40, 10, 1, TRUE),
    (947, 933, 'JOKGU_TEAMWORK', '협동심', 0.30, 10, 2, TRUE),
    (948, 933, 'JOKGU_DEFENSE', '수비', 0.30, 10, 3, TRUE),
    (949, 936, 'MAT_MIGRATION_OTHER', '기타 테스트 항목', 0.50, 10, 1, TRUE);
