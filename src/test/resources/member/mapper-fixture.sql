-- Member Mapper integration fixture for an isolated, disposable MySQL test database.
-- INSERT statements only: the enclosing test transaction rolls these rows back.

INSERT INTO app_user (
    user_id, email, password_hash, nickname, phone, role, created_at, deleted_at, penalty_time
) VALUES
    (1, 'member-active@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
     '활성 회원', '010-0000-0001', '일반 회원', '2026-10-20 09:00:00', NULL, '2026-10-25 09:00:00'),
    (2, 'member-suspended@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
     '정지 회원', '010-0000-0002', '일반 회원', '2026-10-20 09:00:00', NULL, '2026-11-01 00:00:00'),
    (3, 'member-withdrawn@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
     '탈퇴 회원', '010-0000-0003', '일반 회원', '2026-10-20 09:00:00', '2026-10-01 10:00:00', NULL);
