-- =====================================================================
-- 시설 예약 운영 일정: 추가(additive) 마이그레이션
--
-- 대상: 이미 db/schema.sql 이전 버전이 적용된 개발·운영 DB
-- 원칙:
--   * 기존 행을 삭제·변환하지 않는다. 임의 데이터 보정은 하지 않는다.
--   * 요일 중복 등 사람이 먼저 판단해야 하는 데이터가 있으면 이 스크립트는
--     안내와 함께 중단된다. 먼저 아래 "1. 사전 점검" 결과를 검토한 뒤
--     담당자가 별도 이행 스크립트로 정리해야 한다.
--   * db/schema.sql 은 fresh fixture 용도이며 실제 DB에 실행하지 않는다.
--   * Spring 자동 DDL·시드·재생성은 사용하지 않는다.
--   * MySQL DDL은 문장별로 자동 커밋된다. 백업과 쓰기 중단을 준비하고,
--     중간에 실패하면 재실행하지 말고 적용된 스키마를 확인한 뒤 별도 복구한다.
--   * 운영 규칙이 없는 기존 시설은 이행 후 신규 참가 신청이 차단된다.
--     배포 전에 담당 시설의 운영 시간 등록 여부를 확인해야 한다.
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 사전 점검: 요일 중복
--
-- operating_rule 은 (facility_id, weekday) 유일 제약을 새로 얻는다.
-- 중복 행을 임의로 지우면 관리자가 설정한 시간이 조용히 사라지므로
-- 자동 삭제하지 않고 여기서 멈춘다.
-- 아래 임시 테이블은 기존 요일 행을 그대로 복사한다. 중복이 하나라도
-- 있으면 PRIMARY KEY 위반으로 이 문장에서 실행이 중단되므로, 담당자가
-- 중복 행을 별도 이행으로 정리한 뒤 다시 실행해야 한다.
-- (mysql 클라이언트는 --force 없이 첫 오류에서 중단한다.)
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS operating_rule_duplicate_guard;
CREATE TEMPORARY TABLE operating_rule_duplicate_guard (
    facility_id BIGINT  NOT NULL,
    weekday     TINYINT NOT NULL,
    PRIMARY KEY (facility_id, weekday)
);

INSERT INTO operating_rule_duplicate_guard (facility_id, weekday)
SELECT facility_id, weekday
  FROM operating_rule;

DROP TEMPORARY TABLE operating_rule_duplicate_guard;

-- ---------------------------------------------------------------------
-- 2. 요일별 운영 시간: 자정 넘김 표현과 시설·요일 유일 제약
--
-- end_next_day=true 는 close_time 이 다음 날 시각이라는 뜻이다.
-- 예) 월요일 open_time 22:00, close_time 06:00, end_next_day true
--     → 월요일 22:00 ~ 화요일 06:00
-- 휴무 행(is_open=false)은 기존 NOT NULL 시간을 그대로 두고 00:00 으로
-- 보관하는 규칙을 쓴다. 미설정(행 없음)과 전체 휴무(7행, is_open=false)는
-- 서로 다르며 이 값으로 구분한다.
-- ---------------------------------------------------------------------
ALTER TABLE operating_rule
    ADD COLUMN end_next_day BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT '종료 시각이 다음 날인지'
        AFTER close_time;

ALTER TABLE operating_rule
    ADD UNIQUE KEY uk_operating_rule_facility_weekday (facility_id, weekday);

-- ---------------------------------------------------------------------
-- 3. 날짜별 예외 헤더·슬롯
--
-- 헤더가 없으면 주간 규칙을 상속하고, 헤더는 있는데 슬롯이 0개면 그 날짜
-- 전체가 휴무다. 0슬롯 저장을 표현하려면 헤더 행을 반드시 보존한다.
-- slot_hour 0~23 은 그 자연일 00:00~24:00 의 1시간 슬롯이며, 자정 이후
-- 시간은 다음 날짜의 슬롯으로 나누어 저장한다.
-- ---------------------------------------------------------------------
CREATE TABLE facility_date_override (
    facility_date_override_id  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '날짜별 예외 ID',
    facility_id                BIGINT       NOT NULL COMMENT '예외가 적용되는 시설',
    override_date              DATE         NOT NULL COMMENT '예외가 적용되는 날짜',
    note                       VARCHAR(500) NULL COMMENT '날짜별 안내문',
    PRIMARY KEY (facility_date_override_id),
    UNIQUE KEY uk_facility_date_override (facility_id, override_date),
    CONSTRAINT fk_facility_date_override_facility FOREIGN KEY (facility_id) REFERENCES facility (facility_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='날짜별 예외 헤더(슬롯 0개면 그날 휴무)';

CREATE TABLE facility_date_override_slot (
    facility_date_override_id  BIGINT  NOT NULL COMMENT '슬롯이 속한 날짜별 예외',
    slot_hour                  TINYINT NOT NULL COMMENT '0~23시 1시간 슬롯',
    PRIMARY KEY (facility_date_override_id, slot_hour),
    CONSTRAINT fk_facility_date_override_slot_header FOREIGN KEY (facility_date_override_id)
        REFERENCES facility_date_override (facility_date_override_id),
    CONSTRAINT ck_facility_date_override_slot_hour CHECK (slot_hour BETWEEN 0 AND 23)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='날짜별 예외의 1시간 이용 가능 슬롯';

-- ---------------------------------------------------------------------
-- 4. 종목별 참가 안내 (facility_sport.note)
--
-- 참가 안내만 새로 추가한다. 신규 예약에만 적용하며 기존 매칭방에
-- 소급 적용하지 않는다.
ALTER TABLE facility_sport
    ADD COLUMN note VARCHAR(500) NULL
        COMMENT '참가자에게 표시할 신규 예약 참가 안내'
        AFTER default_skill;
-- ---------------------------------------------------------------------
-- 5. 휴관 일정 조회 인덱스
--
-- change_type/status 값은 임의의 기존 값을 휴관으로 승격하지 않는다.
-- 휴관 등록은 FACILITY_CLOSURE/ACTIVE, 취소는 CANCELLED 만 쓴다.
-- 기존 schedule_change 행(임시 휴관·시설 점검·운영시간 변경 등)은 자동으로
-- 휴관으로 취급되지 않으므로, 실제 휴관으로 반영해야 하는 행이 있으면
-- 담당자가 유형·상태를 확인해 별도 이행해야 한다.
-- ---------------------------------------------------------------------
ALTER TABLE schedule_change
    ADD KEY idx_schedule_change_facility_period (facility_id, start_at, end_at);

-- ---------------------------------------------------------------------
-- 6. 사후 확인: 기존 schedule_change 유형 분포
--
-- 아래 결과에 휴관으로 반영해야 할 행이 남아 있는지 담당자가 확인한다.
-- 이 스크립트는 그 행들을 수정하지 않는다.
-- ---------------------------------------------------------------------
SELECT change_type, status, COUNT(*) AS row_count
  FROM schedule_change
 GROUP BY change_type, status
 ORDER BY change_type, status;
