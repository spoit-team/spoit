SET NAMES utf8mb4;

CREATE TABLE password_reset_challenge (
    challenge_id        CHAR(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '서버 세션과 결합하는 256비트 무작위 도전 ID',
    user_id             BIGINT       NULL COMMENT '가입 회원이면 회원 ID, 아니면 NULL',
    email_fingerprint   CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '비밀키 HMAC으로 만든 이메일 제한 버킷',
    request_ip_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '비밀키 HMAC으로 만든 요청 IP 지문',
    code_hmac           CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '인증번호 HMAC; 원문 인증번호는 저장하지 않음',
    grant_hmac          CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '재설정 권한 HMAC; 원문 권한은 서버 세션에만 보관',
    created_at          DATETIME     NOT NULL COMMENT '인증번호 발급 시각',
    expires_at          DATETIME     NOT NULL COMMENT '인증번호 만료 시각',
    verified_at         DATETIME     NULL COMMENT '인증번호 확인 시각',
    grant_expires_at    DATETIME     NULL COMMENT '일회용 재설정 권한 만료 시각',
    consumed_at         DATETIME     NULL COMMENT '사용·폐기 시각',
    attempt_count       TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '현재 도전의 잘못된 인증번호 누적 횟수',
    PRIMARY KEY (challenge_id),
    KEY idx_password_reset_challenge_email (email_fingerprint, created_at),
    KEY idx_password_reset_challenge_expiry (expires_at),
    KEY idx_password_reset_challenge_user (user_id, created_at),
    CONSTRAINT fk_password_reset_challenge_user FOREIGN KEY (user_id) REFERENCES app_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='이메일 비밀번호 재설정 도전';

CREATE TABLE password_reset_rate_limit (
    bucket_type         VARCHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'EMAIL 또는 IP 제한 버킷',
    bucket_fingerprint  CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '비밀키 HMAC 지문',
    window_started_at   DATETIME     NOT NULL COMMENT '누적 제한 시간창 시작',
    request_count       SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '시간창 안의 코드 발급 요청 수',
    failure_count       SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '시간창 안의 잘못된 인증번호 수',
    last_requested_at   DATETIME     NULL COMMENT '마지막 코드 발급 요청 시각',
    PRIMARY KEY (bucket_type, bucket_fingerprint),
    KEY idx_password_reset_rate_limit_cleanup (window_started_at),
    CONSTRAINT ck_password_reset_rate_limit_type CHECK (bucket_type IN ('EMAIL', 'IP'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='비밀번호 재설정 이메일·IP 누적 제한';
