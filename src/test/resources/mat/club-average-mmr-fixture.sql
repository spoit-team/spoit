-- ClubMapper 팀 실력 평균: 배치를 마친 회원(5경기)만 평균에 들어가고 배치 중 회원(4경기)은 빠진다.
-- ClubAverageMmrMapperIntegrationTest 전용. 다른 fixture와 겹치지 않게 901번부터 쓴다.
-- INSERT statements only: the enclosing test transaction rolls these rows back.
-- 예전 조건(1경기 이상)이면 평균이 (1100 + 700) / 2 = 900으로 실버 구간(1000 이상 1200 미만) 밖이다.

INSERT INTO app_user (
    user_id, email, password_hash, nickname, phone, role, created_at
) VALUES
    (901, 'club-tier-901@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
     '동호회 평균 테스트 회원 901', '010-0000-0901', 'USER', '2026-10-20 09:00:00'),
    (902, 'club-tier-902@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
     '동호회 평균 테스트 회원 902', '010-0000-0902', 'USER', '2026-10-20 09:00:00');

INSERT INTO sport (sport_id, name) VALUES
    (901, '동호회 평균 테스트 종목');

INSERT INTO club (
    club_id, sport_id, club_name, intro, max_members, recruit_status,
    activity_days, activity_start_time, activity_end_time, created_at
) VALUES
    (901, 901, '동호회 평균 테스트 팀', '테스트 전용 소개', 10, '모집중',
     '2,4', '19:00:00', '21:00:00', '2026-10-20 09:00:00');

INSERT INTO club_member (club_member_id, club_id, user_id, role, joined_at) VALUES
    (901, 901, 901, '동호회장', '2026-10-20 09:00:00'),
    (902, 901, 902, '동호회원', '2026-10-20 09:00:00');

INSERT INTO sport_rating (
    user_id, sport_id, current_mmr, rated_match_count, updated_at
) VALUES
    (901, 901, 1100.00, 5, '2026-10-21 09:30:00'),
    (902, 901, 700.00, 4, '2026-10-21 09:30:00');
