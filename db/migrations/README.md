# 로컬 DB 마이그레이션 적용 방법

이미 데이터가 들어 있는 로컬 DB를 지우지 않고 최신 `db/schema.sql`과 같은 구조로 맞출 때 이 폴더의 SQL을 실행합니다.

- **새로 DB를 만드는 경우**에는 마이그레이션이 필요 없습니다. [README의 개발용 데이터베이스 준비](../../README.md#3-개발용-데이터베이스-준비)대로 `schema.sql`과 `seed.sql`을 처음부터 적용하세요. 다만 `20261110-seoul-public-facilities.sql`은 seed에 없는 공공 체육시설 데이터라 `seed.sql` 다음에 적용합니다.
- **운영 RDS**는 팀장이 따로 반영합니다. 이 문서는 각자의 로컬 DB용입니다.

## 마이그레이션 목록

파일 이름의 날짜 순서대로, DB마다 한 번씩만 적용합니다.

| 파일 | 내용 | 적용 여부 확인 기준 |
| --- | --- | --- |
| `20261027-app-user-withdrawal.sql` | 회원 탈퇴·이용정지(#36): `app_user.deleted_at`·`penalty_time` 추가, `status` 제거 | `app_user.status` 컬럼이 없으면 적용됨 |
| `20261027-club-active-name-unique.sql` | 동호회(#37): 활성 동호회 이름 유일 키, 이탈 유형 '해체' 허용 | `club.active_club_name` 컬럼이 있으면 적용됨 |
| `20261028-facility-reservation-schedule.sql` | 시설 예약 운영 일정 (날짜별 예외, 자정 넘김, 종목별 안내) | `facility_date_override` 테이블이 있으면 적용됨 |
| `20261029-drop-facility-sport-deadline-hours.sql` | 모집 마감(`deadline_hours`) 컬럼 제거 | `facility_sport.deadline_hours` 컬럼이 없으면 적용할 필요 없음 |
| `20261030-match-application-group.sql` | RES-04 팀 신청 묶음 컬럼 `match_application.group_application_id` 추가 | 컬럼이 있으면 적용됨 |
| `20261103-social-match-photo.sql` | 매칭방 시설 사진 컬럼 `social_match.photo_url` 추가 | 컬럼이 있으면 적용됨 |
| `20261104-drop-club-hashtag.sql` | 동호회 태그 연결·태그 테이블을 순서대로 삭제 | `club_hashtag`와 `hashtag`가 모두 없으면 적용됨 |
| `20261105-notification-club.sql` | `notification.club_id`와 `club` 참조 외래 키 추가 | 컬럼과 `fk_notification_club` 외래 키가 모두 있으면 적용됨 |
| `20261108-payment.sql` | 유료 결제(카카오페이): `payment` 테이블, `match_application.payment_id`·`refunded_at` 추가 | `payment` 테이블이 있으면 적용됨 |
| `20261109-evaluation-items.sql` | 평가 항목을 종목마다 공격·연결·수비 3개, 가중치 1.00으로 정리(축구+풋살 3개·족구 협동심 끔, 족구 토스·연결 추가, 농구 수비 → 수비·리바운드). 데이터만 바꾸고 여러 번 실행해도 안전하며, 시드가 없는 `spoit_mat_test`에는 필요 없음 | 파일 끝 확인 질의에서 종목마다 `active_items` 3, `weight_sum` 3.00이면 적용됨 |
| `20261110-seoul-public-facilities.sql` | 서울시 공공서비스예약 체육시설 113곳, 서울 구 14개, 시설 관리자 계정 10개 등록. `scripts/public-facility-import.ps1`이 만든 파일이며 데이터만 넣고 여러 번 실행해도 안전함. 새 DB도 `seed.sql` 다음에 적용하고 `spoit_mat_test`에는 적용하지 않음. [공공 체육시설 등록](#공공-체육시설-등록-20261110) 참고 | 파일 끝 확인 질의에서 `facilities` 113, `skipped`가 NULL이면 적용됨 |
| `20261111-demo-match-data.sql` | 발표 시연 데이터: 데모 회원 101명·동호회 8개, 공공 체육시설 매칭방 189개(11/12~11/30 매일 9개, 11/1~11/9 지난 경기 18개), 매칭방 사진을 종목별 기본 사진으로 채움. `scripts/demo-match-data.py`가 만든 파일이며 여러 번 실행해도 안전함. `20261109`·`20261110` 다음에 적용. [발표 시연 데이터](#발표-시연-데이터-20261111) 참고 | 파일 끝 확인 질의에서 `demo_matches` 189, `rooms_without_photo` 0이면 적용됨 |
| `20261111-rating-demo-data.sql` | 레이팅 시연 데이터: 회원 24명(`rating-01~24@spoit.test`)과 10월 경기 34개의 결과·평가·레이팅 이력 226행. 마이페이지에서 배치 중·배치 완료(브론즈·실버·골드)가 모두 보인다. `scripts/rating-demo-data.ps1`이 만든 파일이며 여러 번 실행해도 안전함. `20261111-demo-match-data.sql` 다음에 적용. [레이팅 시연 데이터](#레이팅-시연-데이터-20261111) 참고 | 파일 끝 확인 질의에서 `rating_users` 24, `history_rows` 226이면 적용됨 |
| `20261112-demo-password-unify.sql` | 데모·레이팅 회원 125명의 비밀번호를 `spoit1234!`에서 seed 회원과 같은 `spoit1234`로 통일. 예전 해시가 남은 행만 바꾸며 여러 번 실행해도 안전함. 20261111 두 파일 다음에 적용. [데모 회원 비밀번호 통일](#데모-회원-비밀번호-통일-20261112) 참고 | 파일 끝 확인 질의에서 `old_hash_left` 0이면 적용됨 |

## 적용할 DB

| DB | 누가 | 이유 |
| --- | --- | --- |
| `spoit` | 모두 | 로컬에서 앱을 실행하는 개발 DB |
| `spoit_mat_test` | `MAT_TEST_DB_URL` 환경 변수를 설정해 통합 테스트를 돌리는 사람만 | 통합 테스트가 이 DB에 직접 접속한다. 새 컬럼이 없으면 `Unknown column` 오류로 실패한다 |

## 순서 (Windows PowerShell)

아래 명령은 **저장소 루트**(`spoit` 폴더)에서 실행합니다. 비밀번호는 `-p`가 물어볼 때 입력하고, 명령에 직접 쓰지 마세요.

> **`개발용계정`은 그대로 입력하는 값이 아닙니다.** 본인 MySQL 계정 이름으로 바꾸세요. 그대로 실행하면 "개발용계정"이라는 계정으로 로그인을 시도해 `ERROR 1045 Access denied`가 납니다(오류 메시지의 사용자 이름은 한글이 깨져 보입니다). 앱 실행용 환경 변수 `DB_USERNAME`을 설정해 두었다면 `-u 개발용계정` 대신 `-u $env:DB_USERNAME`을 쓰면 됩니다. `spoit_mat_test`에 다른 계정으로 접속한다면 그 계정(`$env:MAT_TEST_DB_USERNAME`)을 쓰세요.

### 0. 준비

앱(`bootRun`)과 IDE의 DB 연결을 끕니다. 접속이 남아 있으면 `ALTER TABLE`이 잠금을 기다리며 멈출 수 있습니다.

MySQL 기본 설치 경로를 변수로 둡니다. 다른 경로에 설치했다면 그 경로로 바꾸세요.

```powershell
$mysqlBin = "C:\Program Files\MySQL\MySQL Server 8.0\bin"
$db = "spoit"   # 테스트 DB에 적용할 때는 "spoit_mat_test"
```

### 1. 백업

MySQL의 DDL은 실행 즉시 확정되어 되돌릴 수 없습니다. 적용 전에 백업합니다. 백업 파일은 **저장소 밖**에 두고 커밋하지 마세요.

```powershell
& "$mysqlBin\mysqldump.exe" -u 개발용계정 -p --default-character-set=utf8mb4 $db --result-file="$HOME\$db-before-migration.sql"
```

### 2. 적용 여부 확인

```powershell
$check = @'
SELECT
  (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_user' AND COLUMN_NAME = 'status') AS m1027_user_applied,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'club' AND COLUMN_NAME = 'active_club_name') AS m1027_club_applied,
  (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'facility_date_override') AS m1028_applied,
  (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'facility_sport' AND COLUMN_NAME = 'deadline_hours') AS m1029_skip,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'match_application' AND COLUMN_NAME = 'group_application_id') AS m1030_applied,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'social_match' AND COLUMN_NAME = 'photo_url') AS m1103_applied,
  (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('club_hashtag', 'hashtag')) AS m1104_tables,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification' AND COLUMN_NAME = 'club_id') AS m1105_column,
  (SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification'
      AND CONSTRAINT_NAME = 'fk_notification_club' AND COLUMN_NAME = 'club_id'
      AND REFERENCED_TABLE_SCHEMA = DATABASE() AND REFERENCED_TABLE_NAME = 'club'
      AND REFERENCED_COLUMN_NAME = 'club_id') AS m1105_fk,
  (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payment') AS m1108_applied;
'@
& "$mysqlBin\mysql.exe" -u 개발용계정 -p --default-character-set=utf8mb4 $db -e $check
```

| 열 | `1`이면 | `0`이면 |
| --- | --- | --- |
| `m1027_user_applied` | 이미 적용됨, 건너뜀 | 3단계에서 `20261027-app-user-withdrawal` 적용 |
| `m1027_club_applied` | 이미 적용됨, 건너뜀 | 3단계에서 `20261027-club-active-name-unique` 적용 |
| `m1028_applied` | 이미 적용됨, 건너뜀 | 3단계에서 `20261028` 적용 |
| `m1029_skip` | 적용할 필요 없음, 건너뜀 | 3단계에서 `20261029` 적용 (`20261028` 다음에) |
| `m1030_applied` | 이미 적용됨, 건너뜀 | 3단계에서 `20261030` 적용 |
| `m1103_applied` | 이미 적용됨, 건너뜀 | 3단계에서 `20261103` 적용 |
| `m1105_column` | `club_id` 존재 | 컬럼 없음 |
| `m1105_fk` | 지정된 이름과 참조 대상의 외래 키 존재 | 해당 외래 키 없음 |
| `m1108_applied` | 이미 적용됨, 건너뜀 | 3단계에서 `20261108` 적용 |

- `m1104_tables`는 남은 태그 테이블 수입니다. `0`이면 `20261104`를 건너뛰고, `2`이면 3단계에서 적용합니다. `1`이면 부분 적용 상태이므로 파일 전체를 재실행하지 말고 남은 테이블과 참조 관계를 확인한 뒤 팀과 복구 방법을 정하세요.
- `m1105_column`과 `m1105_fk`가 모두 `1`이면 `20261105`를 건너뛰고, 모두 `0`이면 3단계에서 적용합니다. 값이 다르면 부분 적용 또는 다른 스키마 상태이므로 파일을 실행하지 말고 실제 컬럼·외래 키 정의를 확인해 팀과 처리하세요. 이름이 다른 외래 키도 먼저 확인해야 합니다.

### 3. 적용

2단계의 판정에 따라 필요한 파일만 날짜 순서대로 실행합니다. `20261104`는 두 테이블이 모두 있을 때만, `20261105`는 컬럼과 외래 키가 모두 없을 때만 실행합니다. 경로에는 슬래시(`/`)를 씁니다. 아래 명령을 모두 한꺼번에 실행하지 말고 해당하는 것만 선택하세요.

`20261104`는 태그와 연결 데이터를 삭제합니다. 반드시 앞 단계에서 대상 DB와 백업을 확인하세요. 삭제한 데이터는 백업 없이 복구할 수 없습니다.

```powershell
& "$mysqlBin\mysql.exe" -u 개발용계정 -p --default-character-set=utf8mb4 $db -e "source db/migrations/20261027-app-user-withdrawal.sql"
& "$mysqlBin\mysql.exe" -u 개발용계정 -p --default-character-set=utf8mb4 $db -e "source db/migrations/20261027-club-active-name-unique.sql"
& "$mysqlBin\mysql.exe" -u 개발용계정 -p --default-character-set=utf8mb4 $db -e "source db/migrations/20261028-facility-reservation-schedule.sql"
& "$mysqlBin\mysql.exe" -u 개발용계정 -p --default-character-set=utf8mb4 $db -e "source db/migrations/20261029-drop-facility-sport-deadline-hours.sql"
& "$mysqlBin\mysql.exe" -u 개발용계정 -p --default-character-set=utf8mb4 $db -e "source db/migrations/20261030-match-application-group.sql"
& "$mysqlBin\mysql.exe" -u 개발용계정 -p --default-character-set=utf8mb4 $db -e "source db/migrations/20261103-social-match-photo.sql"
& "$mysqlBin\mysql.exe" -u 개발용계정 -p --default-character-set=utf8mb4 $db -e "source db/migrations/20261104-drop-club-hashtag.sql"
& "$mysqlBin\mysql.exe" -u 개발용계정 -p --default-character-set=utf8mb4 $db -e "source db/migrations/20261105-notification-club.sql"
& "$mysqlBin\mysql.exe" -u 개발용계정 -p --default-character-set=utf8mb4 $db -e "source db/migrations/20261108-payment.sql"
```

- 사전·사후 확인 질의가 있는 파일은 그 출력도 확인하세요. `20261030`은 컬럼 1행(`group_application_id`, `bigint`, `YES`)과 FK 1행(`fk_match_application_group`)이 나오면 성공입니다. `20261103`은 컬럼 1행(`photo_url`, `varchar(500)`, `YES`)이 나오면 성공입니다. `20261104`는 마지막 확인 질의에서 행이 나오지 않아야 합니다. `20261108`은 마지막 확인 질의에서 테이블 1행(`payment`)과 컬럼 2행(`payment_id`, `refunded_at`)이 나오면 성공입니다.
- `20261105`에는 별도의 확인 질의가 없으므로 아래 4단계에서 컬럼과 외래 키를 모두 확인하세요. 기존 알림의 새 `club_id`는 NULL로 유지됩니다. 미적용 상태에서는 알림 저장과 배포 전 스키마 검사가 실패할 수 있습니다.
- `20261028`은 시설별 요일 운영 시간이 중복되어 있으면 사전 점검에서 일부러 멈춥니다. 이때는 데이터를 임의로 지우지 말고 시설 담당자와 확인하세요.
- `20261027-app-user-withdrawal`은 `app_user.status`가 이전 코드에서 로그인할 수 있던 값(`ACTIVE`·`활성`·`NORMAL`)이 아닌 회원(정지, 시설 관리자 승인 대기·거절 등)이 있으면 컬럼 추가 뒤 사전 점검에서 멈춥니다. 출력된 회원을 처리하고(정지 → `penalty_time` 지정, 대기·거절 → 승인 여부를 정해 `role` 조정) `status`를 `ACTIVE`로 바꾼 뒤, 파일을 다시 실행하지 말고 파일의 3단계 `ALTER`와 4단계 확인 질의만 실행하세요.
- `20261027-club-active-name-unique`은 해체되지 않은 동호회끼리 이름이 겹치면 사전 점검에서 멈춥니다. 출력된 동호회 중 어느 쪽 이름을 바꿀지 동호회 담당자와 정한 뒤 다시 실행하세요.

### 4. 다시 확인

2단계 확인 명령을 다시 실행해 기존 여섯 열과 `m1105_column`·`m1105_fk`·`m1108_applied`는 모두 `1`, `m1104_tables`는 `0`인지 봅니다. 이 확인은 각 마이그레이션의 모든 스키마 세부 사항을 검증하지는 않습니다. 테스트 DB도 적용하는 사람은 `$db = "spoit_mat_test"`로 바꾸고 1~4단계를 반복합니다.

## 공공 체육시설 등록 (`20261110`)

서울시 공공서비스예약 체육시설 113곳과 담당 시설 관리자 계정 10개(`facility-01@spoit.test`~`facility-10@spoit.test`, 비밀번호는 `facility@spoit.test`와 같음)를 넣는 데이터 마이그레이션입니다. 위 2단계의 확인 기준과 상관없이 아래대로 적용합니다.

- **적용할 DB**: `spoit`. 기존 DB와 새로 만든 DB(`seed.sql` 다음) 모두 적용합니다. `spoit_mat_test`에는 적용하지 않습니다. 통합 테스트(`PublicFacilityMigrationIntegrationTest`)가 이 파일을 직접 실행해 확인합니다.
- **적용**: 1단계처럼 백업한 뒤 실행합니다. 다시 실행해도 이미 있는 행은 건너뜁니다.

  ```powershell
  & "$mysqlBin\mysql.exe" -u 개발용계정 -p --default-character-set=utf8mb4 $db -e "source db/migrations/20261110-seoul-public-facilities.sql"
  ```

- **확인**: 첫 출력(사전 확인)의 다섯 값이 모두 `1`이어야 합니다. 마지막 두 출력이 `places`·`facilities` 113, `skipped` NULL, `seoul_districts` 25, `manager_accounts` 10, `facility_sports` 118, `operating_rules` 791, `facility_managers` 113이면 적용된 것입니다.
- **`Subquery returns more than 1 row`로 멈추면**: 사전 확인에서 `0`인 값을 보세요. 한 트랜잭션이라 넣은 행은 없습니다.
  - `demo_account`: `facility@spoit.test`가 없음
  - `sports`: 종목 5개의 이름이 seed와 다름
  - `seoul`: 서울특별시(`region_id` 1)가 없음
  - `unique_districts`: 서울특별시 아래 이름이 같은 구가 두 번 있음
  - `region_ids_free`: 새 구에 쓸 번호(6·7·10·11·12·13·14·17·19·21·22·24·27·29)를 다른 이름의 지역이 쓰고 있음
    - `region_ids_free`가 `0`이고 `SELECT region_id, name FROM region WHERE region_id >= 29`에 "탈퇴검증 …" 지역이 보이면 `db/withdrawal-scenarios.sql`이 만든 테스트 지역입니다. 아래 SQL로 이 지역을 비어 있는 번호로 옮긴 뒤 마이그레이션을 다시 실행하세요. 외래 키 검사를 끄지 않고 새 번호 행을 만들어 참조를 옮긴 다음 옛 행을 지웁니다. 탈퇴 테스트 지역이 없으면 아무것도 바꾸지 않습니다.
      ```sql
      SET NAMES utf8mb4;
      START TRANSACTION;
      SET @wd_p = NULL, @wd_m = NULL, @wd_s = NULL;
      SELECT MAX(region_id) INTO @wd_p FROM region WHERE parent_region_id IS NULL AND name = '탈퇴검증 광역시';
      SELECT MAX(region_id) INTO @wd_m FROM region WHERE parent_region_id = @wd_p AND name = '탈퇴검증 중구';
      SELECT MAX(region_id) INTO @wd_s FROM region WHERE parent_region_id = @wd_p AND name = '탈퇴검증 남구';
      SELECT GREATEST(MAX(region_id), 29) INTO @wd_base FROM region;
      INSERT INTO region (region_id, parent_region_id, name) SELECT @wd_base + 1, NULL, '탈퇴검증 광역시' FROM DUAL WHERE @wd_p IS NOT NULL;
      INSERT INTO region (region_id, parent_region_id, name) SELECT @wd_base + 2, @wd_base + 1, '탈퇴검증 중구' FROM DUAL WHERE @wd_m IS NOT NULL;
      INSERT INTO region (region_id, parent_region_id, name) SELECT @wd_base + 3, @wd_base + 1, '탈퇴검증 남구' FROM DUAL WHERE @wd_s IS NOT NULL;
      UPDATE user_region SET region_id = CASE region_id WHEN @wd_p THEN @wd_base + 1 WHEN @wd_m THEN @wd_base + 2 ELSE @wd_base + 3 END WHERE region_id IN (@wd_p, @wd_m, @wd_s);
      UPDATE facility SET region_id = CASE region_id WHEN @wd_p THEN @wd_base + 1 WHEN @wd_m THEN @wd_base + 2 ELSE @wd_base + 3 END WHERE region_id IN (@wd_p, @wd_m, @wd_s);
      UPDATE club_region SET region_id = CASE region_id WHEN @wd_p THEN @wd_base + 1 WHEN @wd_m THEN @wd_base + 2 ELSE @wd_base + 3 END WHERE region_id IN (@wd_p, @wd_m, @wd_s);
      DELETE FROM region WHERE region_id IN (@wd_m, @wd_s);
      DELETE FROM region WHERE region_id = @wd_p;
      COMMIT;
      ```
- **`skipped`에 장소가 나오면**: 이름과 구가 같은 다른 시설이 이미 있어 그 장소만 건너뛴 것입니다. 기존 시설은 바꾸지 않았으니 같은 시설인지 확인해 팀과 처리하세요.
- **파일을 다시 만들 때**: 원본 JSON을 `out/public-facilities.json`에 두고 저장소 루트에서 `pwsh -File scripts/public-facility-import.ps1`을 실행합니다. 이름·운영 시간 수정 목록은 스크립트 안에 있습니다. 검수용 `out/public-facilities-review.csv`도 함께 만들어지며, 원본과 CSV는 커밋하지 않습니다(`out/`은 git 무시 대상).

## 발표 시연 데이터 (`20261111`)

11/23 발표 시연용으로 공공 체육시설에 매칭방과 데모 회원을 넣고, 매칭방 사진을 종목별 기본 사진으로 채우는 데이터 마이그레이션입니다. `20261109-evaluation-items.sql`과 `20261110-seoul-public-facilities.sql` 다음에 적용합니다.

- **넣는 것**
  - 데모 회원 101명: `demo-001@spoit.test`~`demo-100@spoit.test`, 발표자 `demo-presenter@spoit.test`(신청 내역 없음). 비밀번호는 모두 seed 회원과 같은 `spoit1234`입니다(이 파일을 예전에 적용한 DB는 `20261112-demo-password-unify.sql`로 바꿉니다). 종목별 레이팅과 선호 지역이 들어 있습니다.
  - 동호회 8개: 뚝섬 FC, 망원 풋살 크루, 잠실 풋살 클럽, 반포 농구단, 광나루 바스켓, 한강 족구회, 뚝섬 셔틀콕, 마포 랠리 테니스(대표는 각 동호회의 첫 데모 회원)
  - 11/12~11/30 매칭방 171개(하루 9개): 정원이 찬 무료 방 3개(시작 시각에 자동 배정, 경기 후 결과·평가 입력), 일부만 찬 방 3개(대기 신청 포함, 주말은 동호회 팀 신청), 빈 유료 방 3개(결제 테스트). 방을 만든 사람은 그 시설의 담당 관리자 계정입니다.
  - 11/1~11/9 지난 경기 18개: 결과·평가까지 넣고 레이팅은 반영하지 않은 상태로 넣습니다. 앱이 켜지면 1분 안에 결과 마감 스케줄러가 레이팅을 반영하고 `종료`로 바꿉니다.
  - 매칭방 사진: 사진이 없는 모든 매칭방과 seed의 `/uploads/matches/seed/` 사진(파일이 git에 없어 운영에서 열리지 않음)을 `/images/matches/종목.jpg`(저장소의 정적 파일)로 바꿉니다. 직접 올린 사진은 바꾸지 않습니다.
- **적용할 DB**: `spoit`(로컬·운영). `spoit_mat_test`에는 적용하지 않습니다.
- **적용**: 백업한 뒤 실행합니다. `demo-001@spoit.test`가 이미 있으면 데모 행은 건너뛰고 사진만 다시 맞추므로 여러 번 실행해도 안전합니다.

  ```powershell
  & "$mysqlBin\mysql.exe" -u 개발용계정 -p --default-character-set=utf8mb4 $db -e "source db/migrations/20261111-demo-match-data.sql"
  ```

- **확인**: 첫 출력의 `sports`·`facilities`·`districts`·`evaluation_items`가 모두 `1`이고(처음 적용이면 `club_names_free`도 `1`, `already_applied`는 `0`), 마지막 출력이 `demo_users` 101, `demo_clubs` 8, `demo_matches` 189, `demo_applications` 753 이상, `rooms_without_photo` 0이면 적용된 것입니다.
- **`Subquery returns more than 1 row`로 멈추면**: 사전 확인에서 `0`인 값을 보세요. 한 트랜잭션이라 넣은 행은 없습니다. `facilities`가 `0`이면 `20261110` 공공 체육시설이 없거나 담당자가 없는 것이고, `evaluation_items`가 `0`이면 `20261109` 평가 항목 정리가 적용되지 않은 것입니다. `club_names_free`가 `0`이면 같은 이름의 동호회가 이미 있습니다.
- **로컬에서 확인할 때**: 스케줄러는 앱이 켜져 있을 때만 돕니다. 정원이 찬 방은 시작 시각에 앱이 떠 있어야 자동 배정되고, 경기 종료 뒤에 앱을 켜면 `종료 시각 경과`로 자동 취소됩니다.
- **파일을 다시 만들 때**: 저장소 루트에서 `python scripts/demo-match-data.py > db/migrations/20261111-demo-match-data.sql`을 실행합니다(Python 3.8 이상). 같은 시드로 만들어서 다시 실행해도 같은 파일이 나옵니다.

## 레이팅 시연 데이터 (`20261111`)

마이페이지의 레이팅 화면을 시연하기 위해, 배치 중인 회원과 배치를 마친 회원이 모두 있는 레이팅 전용 회원 24명과 2026년 10월 경기 이력을 넣는 데이터 마이그레이션입니다. `20261111-demo-match-data.sql` 다음에 적용합니다(데모 회원 `demo-001@spoit.test`의 비밀번호 해시와 역할을 복사합니다).

- **넣는 것**
  - 회원 24명: `rating-01@spoit.test`~`rating-24@spoit.test`(비밀번호는 데모 회원과 같은 `spoit1234`). 종목은 축구+풋살(5:5)과 테니스(2:2)이고, 4명은 두 종목 모두 이력이 있습니다.
  - 10/1~10/31 공공 체육시설 경기 34개(축구+풋살 15, 테니스 19): 수동 배정(담당 시설 관리자)·결과 확정·레이팅 반영까지 끝난 `종료` 상태입니다. 27개는 두 팀장의 평가가 들어온 시점에, 7개는 평가 없이 결과 마감(종료 24시간 뒤)에 반영된 모양입니다.
  - 레이팅 이력 226행과 종목별 레이팅 28장: 실제 계산식(`RatingCalculator`: 팀 평균 → 기대 승률 → K 64/32 → Elo 증감 → 평가 보정)으로 1000부터 시간순으로 계산한 값입니다. 알림 행은 넣지 않습니다.
- **적용할 DB**: `spoit`(로컬·운영). `spoit_mat_test`에는 적용하지 않습니다. 통합 테스트(`RatingDemoMigrationIntegrationTest`)가 이 파일을 직접 실행해 모든 이력을 `RatingCalculator`로 다시 계산해 확인합니다.
- **적용**: 백업한 뒤 실행합니다. `rating-01@spoit.test`가 이미 있으면 아무것도 넣지 않으므로 여러 번 실행해도 안전합니다.

  ```powershell
  & "$mysqlBin\mysql.exe" -u 개발용계정 -p --default-character-set=utf8mb4 $db -e "source db/migrations/20261111-rating-demo-data.sql"
  ```

- **확인**: 첫 출력의 `sports`·`facilities`·`districts`·`evaluation_items`·`demo_account`·`receipts_free`가 모두 `1`이고(처음 적용이면 `already_applied`는 `0`), 마지막 출력이 `rating_users` 24, `history_rows` 226, `rated_matches` 34, `placed_cards` 18, `placing_cards` 10이면 적용된 것입니다.
- **`Subquery returns more than 1 row`로 멈추면**: 사전 확인에서 `0`인 값을 보세요. 한 트랜잭션이라 넣은 행은 없습니다. `facilities`가 `0`이면 `20261110` 공공 체육시설이 없거나 담당자가 없는 것이고, `evaluation_items`가 `0`이면 `20261109` 평가 항목 정리가, `demo_account`가 `0`이면 `20261111-demo-match-data`가 적용되지 않은 것입니다.
- **파일을 다시 만들 때**: 저장소 루트에서 `pwsh -File scripts/rating-demo-data.ps1`을 실행합니다(PowerShell 7). 같은 시드로 만들어서 다시 실행해도 같은 파일이 나오며, 검수용 `out/rating-demo-review.csv`(회원·종목·경기 수·최종 MMR·티어·배치 상태)도 함께 만들어집니다. `out/`은 git 무시 대상입니다.

## 데모 회원 비밀번호 통일 (`20261112`)

`20261111`의 데모 회원 101명과 레이팅 회원 24명은 seed 회원(`spoit1234`)과 다른 `spoit1234!`로 만들어졌습니다. 이 파일은 그 회원들의 비밀번호를 `spoit1234`로 맞춥니다. `20261111-demo-match-data.sql`과 `20261111-rating-demo-data.sql` 다음에 적용합니다.

- **바꾸는 것**: `demo-%@spoit.test`·`rating-%@spoit.test` 중 비밀번호 해시가 예전 `spoit1234!` 해시 그대로인 행만 새 해시로 바꿉니다. 직접 비밀번호를 바꾼 회원은 해시가 달라서 그대로 둡니다.
- **적용할 DB**: 20261111 두 파일을 적용한 `spoit`(로컬·운영). 20261111 파일을 이번 수정 뒤에 처음 적용하는 DB는 이미 `spoit1234`로 들어가므로 실행해도 바뀌는 행이 없습니다. `spoit_mat_test`에는 적용하지 않습니다.
- **적용**: 백업한 뒤 실행합니다. 여러 번 실행해도 안전합니다.

  ```powershell
  & "$mysqlBin\mysql.exe" -u 개발용계정 -p --default-character-set=utf8mb4 $db -e "source db/migrations/20261112-demo-password-unify.sql"
  ```

- **확인**: 첫 출력이 처음 적용이면 `demo_targets` 101, `rating_targets` 24(이미 적용했으면 둘 다 0)이고, 마지막 출력의 `old_hash_left`가 0이면 적용된 것입니다. 이후 `demo-001@spoit.test` / `spoit1234`로 로그인되는지 확인하세요.

## 문제가 생기면

- **`Duplicate column name` / `Duplicate key name` / `already exists`**: 이미 적용된 파일을 다시 실행한 것입니다. 2단계로 상태를 확인하고 다음 파일로 넘어가세요.
- **중간에 실패**: 같은 파일을 다시 실행하지 마세요. MySQL DDL은 문장마다 확정되므로 앞부분만 적용됐을 수 있습니다. 2단계와 파일의 사후 확인 쿼리로 어디까지 적용됐는지 확인한 뒤 팀에 공유하세요.
- **태그 테이블이 하나만 남음**: `20261104`가 부분 적용됐을 수 있습니다. 파일을 다시 실행하지 말고 남은 테이블 및 다른 참조 관계를 확인해 팀과 처리한 뒤 4단계를 다시 확인하세요.
- **복구가 필요할 때**: 1단계 백업으로 되돌립니다. 백업에는 테이블을 지우고 다시 만드는 문장이 들어 있어, 백업 이후의 변경은 사라집니다.

  ```powershell
  $backup = "$HOME\$db-before-migration.sql" -replace '\\', '/'
  & "$mysqlBin\mysql.exe" -u 개발용계정 -p --default-character-set=utf8mb4 $db -e "source $backup"
  ```

- **`--force`로 오류를 무시하지 마세요.** 실패한 문장 뒤의 변경까지 이어서 실행되어 상태 파악이 어려워집니다.
