-- =====================================================================
-- 유료 결제 (카카오페이 단건 결제, 테스트 결제): payment 테이블과 신청 행 연결 컬럼
--
-- 대상: 개발·운영 DB
-- 내용:
--   * payment: 결제 1건. 팀 신청은 대표가 인원수만큼 한 번에 결제한다.
--   * match_application.payment_id: 이 신청 행을 결제한 결제 (팀원 행이 대표 결제를 함께 가리킨다).
--   * match_application.refunded_at: 이 신청 행 몫(1인 참가비)을 환불한 시각.
-- 원칙:
--   * 기존 행은 바꾸지 않는다. 기존 신청은 무료 신청이라 두 컬럼 모두 NULL로 남는다.
--   * MySQL DDL은 문장별로 자동 커밋된다. 중간에 실패하면 재실행하지 말고
--     적용된 스키마를 확인한 뒤 별도 복구한다.
--   * db/schema.sql 은 fresh fixture 용도이며 실제 DB에 실행하지 않는다.
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 사전 점검: 아래 질의가 행을 반환하면 이미 적용된 것이므로 실행하지 않는다.
-- ---------------------------------------------------------------------
SELECT TABLE_NAME
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'payment';

-- ---------------------------------------------------------------------
-- 2. 테이블 추가
-- ---------------------------------------------------------------------
CREATE TABLE payment (
    payment_id        VARCHAR(64)   NOT NULL COMMENT '결제 건 ID (서버가 생성, 카카오페이 partner_order_id)',
    match_id          BIGINT        NOT NULL COMMENT '결제 대상 매칭방',
    payer_user_id     BIGINT        NOT NULL COMMENT '결제한 회원 (팀 신청이면 대표)',
    club_id           BIGINT        NULL COMMENT '팀 신청이면 동호회, 개인 신청이면 NULL',
    application_type  VARCHAR(10)   NOT NULL COMMENT '개인 또는 동호회 (match_application.application_type 값)',
    club_member_ids   VARCHAR(1000) NULL COMMENT '팀 신청 때 고른 동호회원 ID (쉼표 구분, 대표 제외)',
    unit_amount       DECIMAL(10,2) NOT NULL COMMENT '결제 시점 1인 참가비',
    seat_count        INT           NOT NULL COMMENT '결제 인원',
    total_amount      DECIMAL(10,2) NOT NULL COMMENT '결제 금액 (1인 참가비 × 인원)',
    refunded_amount   DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '환불한 누적 금액',
    status            VARCHAR(20)   NOT NULL COMMENT 'READY, PAID, PARTIAL_REFUNDED, REFUNDED, FAILED',
    tid               VARCHAR(50)   NULL COMMENT '카카오페이 결제 고유번호 (결제 준비 응답)',
    fail_reason       VARCHAR(300)  NULL COMMENT '결제를 승인하지 않았거나 승인 직후 취소한 사유',
    created_at        DATETIME      NOT NULL COMMENT '결제 준비 시각',
    paid_at           DATETIME      NULL COMMENT '결제 승인 시각',
    updated_at        DATETIME      NOT NULL COMMENT '마지막 상태 변경 시각',
    PRIMARY KEY (payment_id),
    KEY idx_payment_payer (payer_user_id),
    CONSTRAINT fk_payment_match FOREIGN KEY (match_id)      REFERENCES social_match (match_id),
    CONSTRAINT fk_payment_payer FOREIGN KEY (payer_user_id) REFERENCES app_user (user_id),
    CONSTRAINT fk_payment_club  FOREIGN KEY (club_id)       REFERENCES club (club_id),
    CONSTRAINT ck_payment_amount   CHECK (unit_amount > 0 AND seat_count > 0 AND total_amount = unit_amount * seat_count),
    CONSTRAINT ck_payment_refunded CHECK (refunded_amount >= 0 AND refunded_amount <= total_amount),
    CONSTRAINT ck_payment_status   CHECK (status IN ('READY', 'PAID', 'PARTIAL_REFUNDED', 'REFUNDED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='참가비 결제';

-- ---------------------------------------------------------------------
-- 3. 신청 행 연결 컬럼·인덱스·FK 추가
-- ---------------------------------------------------------------------
ALTER TABLE match_application
    ADD COLUMN payment_id VARCHAR(64) NULL
        COMMENT '참가비 결제 (팀 신청은 팀원 행이 대표 결제를 함께 가리킴), 무료면 NULL'
        AFTER group_application_id,
    ADD COLUMN refunded_at DATETIME NULL
        COMMENT '이 행 몫(1인 참가비)을 환불한 시각, 환불 전이면 NULL'
        AFTER payment_id,
    ADD KEY idx_match_application_payment (payment_id),
    ADD CONSTRAINT fk_match_application_payment
        FOREIGN KEY (payment_id) REFERENCES payment (payment_id);

-- ---------------------------------------------------------------------
-- 4. 사후 확인: 테이블 1행, 컬럼 2행이 나와야 한다.
-- ---------------------------------------------------------------------
SELECT TABLE_NAME
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'payment';

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'match_application'
   AND COLUMN_NAME IN ('payment_id', 'refunded_at');
