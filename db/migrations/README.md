# 로컬 DB 마이그레이션 적용 방법

이미 데이터가 들어 있는 로컬 DB를 지우지 않고 최신 `db/schema.sql`과 같은 구조로 맞출 때 이 폴더의 SQL을 실행합니다.

- **새로 DB를 만드는 경우**에는 마이그레이션이 필요 없습니다. [README의 개발용 데이터베이스 준비](../../README.md#3-개발용-데이터베이스-준비)대로 `schema.sql`과 `seed.sql`을 처음부터 적용하세요.
- **운영 RDS**는 팀장이 따로 반영합니다. 이 문서는 각자의 로컬 DB용입니다.

## 마이그레이션 목록

파일 이름의 날짜 순서대로, DB마다 한 번씩만 적용합니다.

| 파일 | 내용 | 적용 여부 확인 기준 |
| --- | --- | --- |
| `20261028-facility-reservation-schedule.sql` | 시설 예약 운영 일정 (날짜별 예외, 자정 넘김, 종목별 안내) | `facility_date_override` 테이블이 있으면 적용됨 |
| `20261029-drop-facility-sport-deadline-hours.sql` | 모집 마감(`deadline_hours`) 컬럼 제거 | `facility_sport.deadline_hours` 컬럼이 없으면 적용할 필요 없음 |
| `20261030-match-application-group.sql` | RES-04 팀 신청 묶음 컬럼 `match_application.group_application_id` 추가 | 컬럼이 있으면 적용됨 |

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
  (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'facility_date_override') AS m1028_applied,
  (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'facility_sport' AND COLUMN_NAME = 'deadline_hours') AS m1029_skip,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'match_application' AND COLUMN_NAME = 'group_application_id') AS m1030_applied;
'@
& "$mysqlBin\mysql.exe" -u 개발용계정 -p --default-character-set=utf8mb4 $db -e $check
```

| 열 | `1`이면 | `0`이면 |
| --- | --- | --- |
| `m1028_applied` | 이미 적용됨, 건너뜀 | 3단계에서 `20261028` 적용 |
| `m1029_skip` | 적용할 필요 없음, 건너뜀 | 3단계에서 `20261029` 적용 (`20261028` 다음에) |
| `m1030_applied` | 이미 적용됨, 건너뜀 | 3단계에서 `20261030` 적용 |

### 3. 적용

2단계에서 `0`이 나온 파일만 날짜 순서대로 실행합니다. 경로에는 슬래시(`/`)를 씁니다.

```powershell
& "$mysqlBin\mysql.exe" -u 개발용계정 -p --default-character-set=utf8mb4 $db -e "source db/migrations/20261028-facility-reservation-schedule.sql"
& "$mysqlBin\mysql.exe" -u 개발용계정 -p --default-character-set=utf8mb4 $db -e "source db/migrations/20261029-drop-facility-sport-deadline-hours.sql"
& "$mysqlBin\mysql.exe" -u 개발용계정 -p --default-character-set=utf8mb4 $db -e "source db/migrations/20261030-match-application-group.sql"
```

- 각 파일은 사전 점검, 변경, 사후 확인 순서로 되어 있습니다. 출력 마지막의 사후 확인 결과를 보세요. `20261030`은 컬럼 1행(`group_application_id`, `bigint`, `YES`)과 FK 1행(`fk_match_application_group`)이 나오면 성공입니다.
- `20261028`은 시설별 요일 운영 시간이 중복되어 있으면 사전 점검에서 일부러 멈춥니다. 이때는 데이터를 임의로 지우지 말고 시설 담당자와 확인하세요.

### 4. 다시 확인

2단계 확인 명령을 다시 실행해 세 열이 모두 `1`인지 봅니다. 테스트 DB도 적용하는 사람은 `$db = "spoit_mat_test"`로 바꾸고 1~4단계를 반복합니다.

## 문제가 생기면

- **`Duplicate column name` / `Duplicate key name` / `already exists`**: 이미 적용된 파일을 다시 실행한 것입니다. 2단계로 상태를 확인하고 다음 파일로 넘어가세요.
- **중간에 실패**: 같은 파일을 다시 실행하지 마세요. MySQL DDL은 문장마다 확정되므로 앞부분만 적용됐을 수 있습니다. 2단계와 파일의 사후 확인 쿼리로 어디까지 적용됐는지 확인한 뒤 팀에 공유하세요.
- **복구가 필요할 때**: 1단계 백업으로 되돌립니다. 백업에는 테이블을 지우고 다시 만드는 문장이 들어 있어, 백업 이후의 변경은 사라집니다.

  ```powershell
  $backup = "$HOME\$db-before-migration.sql" -replace '\\', '/'
  & "$mysqlBin\mysql.exe" -u 개발용계정 -p --default-character-set=utf8mb4 $db -e "source $backup"
  ```

- **`--force`로 오류를 무시하지 마세요.** 실패한 문장 뒤의 변경까지 이어서 실행되어 상태 파악이 어려워집니다.
