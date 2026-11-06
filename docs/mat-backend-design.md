# MAT 백엔드 설계와 MySQL Mapper 통합 테스트

> **읽는 법:** 1·5·7·9절은 PR #17 시점의 초기 조회 골격과 Mapper 테스트 기록이며, 범위와 검증 결과는 당시 기준이다. 2~4·6·8·10~13절은 현재 develop 코드 기준이고, 근거가 되는 클래스·메서드 이름을 함께 적는다.
>
> 현재 MAT에는 팀 배정·경기 시작 처리, 결과 제출·확정, 팀장 평가, Elo·평가 보정 반영과 제외, 결과 마감·종료 스케줄러가 있다. 결과 화면과 알림(#89, #94), 마이페이지 레이팅 표시(#92, #97), 매치 운영 상세의 레이팅 제외 버튼(#99)도 연결되어 있다.

## 1. 분석 근거와 작업 범위

초기 분석 기준은 MAT 최소 골격이 병합된 PR #17의 `1b205c1`이다. 당시 `README.md`, `CONTRIBUTING.md`, `db/schema.sql`, 기존 Java 소스와 빌드·CI 설정을 함께 확인했다.

- README는 MAT의 책임을 참가자 관리, A/B팀 배정, 경기 결과 제출, 팀장 기술평가, 종목별 MMR로 정의한다. 참가 신청·취소는 RES, 회원은 COM, 시설 운영은 FAC 영역이다.
- Java 17, Spring Boot 3.5.16, MyBatis 3.0.5, MySQL, Gradle, Lombok을 사용한다. JPA 계층이나 신규 테스트 의존성은 추가하지 않는다.
- 기본 패키지는 `co.kr.spoit`이다. PR #17의 `mat.domain`, `mat.dto`, `mat.mapper`, `mat.service` 구조와 기존 조회 계약을 그대로 검증한다.
- `admin.controller.SystemAdminController`는 페이지 렌더링과 빈 모델 제공을 담당한다. `preview/`의 미리보기 데이터나 임시 레이팅 표시는 운영 정책으로 사용하지 않는다.
- 이전에 전체 컴파일을 막았던 공통 중복 클래스는 정리되었다. 현재 `HomeController`, `SecurityConfig`, `GlobalModelAdvice`의 패키지는 각각 `global.common`, `global.config`, `notification.service`와 일치한다. MAT 소스만 분리하는 임시 Gradle 스크립트 없이 기본 전체 소스를 컴파일하는 상태다.

이 문단은 원래 `feature/mat-mapper-integration-test` 작업의 범위를 기록한다. 그 작업에서는 별도 worktree 생성, 커밋·push·merge·PR 생성 없이 RES·COM·FAC 및 MAT 프로덕션 코드, `build.gradle`, `db/schema.sql`, 애플리케이션 설정, 기존 테스트를 변경하지 않았다.

당시 최소 골격은 **경기 참가자 목록 조회와 회원·종목별 MMR 조회의 Domain / DTO / Mapper / Service 연결**이었다. 해당 단계에서는 실제 MySQL에서 두 Mapper의 SQL·필드 매핑·복합 키 조건을 검증했고, HTTP 엔드포인트·쓰기 작업·자동 팀 배정은 범위 밖이었다. 이후 추가된 팀 배정 구현은 8절에 정리한다.

## 2. 테이블과 담당 경계

| 테이블 | 역할과 관계 | 현재 쓰는 곳 |
|---|---|---|
| `social_match` | 경기 중심. `(facility_id, sport_id)`로 FAC의 `facility_sport`를 참조하고, 작성·배정·검토 회원을 참조한다. | RES가 만들고 수정·취소한다. MAT는 행을 잠근 뒤(`lockAssignmentMatch`, `lockResultMatch`) 배정·결과·레이팅 상태를 바꾸고, 종료 전환(`markEnded`)을 한다. |
| `match_application` | 경기별 개인·동호회 신청. 경기 1:N 신청이며 회원·동호회를 참조한다. RES 소유 흐름과 연결된다. | RES가 쓴다. MAT는 읽기만 한다(자동 취소 알림 수신자, 배정 화면의 동호회명, 마이페이지 이력의 신청 유형). |
| `match_participant` | 신청 1:N 참가자. 경기·회원·참가 상태·A/B 배정·`entry_mmr`을 보관한다. | RES의 신청 확정(`MatchApplicationService.confirm`)이 만든다. MAT는 `match_side`만 바꾸고(`updateMatchSide`, `clearMatchSides`) 확정 참가자를 읽는다. |
| `match_result_submission` | 같은 경기 팀장의 승무패 제출 이력. | `MatchResultService.submit`이 쓴다. |
| `sport_evaluation_item` | 종목별 평가 문항·가중치·활성 상태. | `MatchEvaluationMapper.findActiveItems`가 활성 항목을 읽는다. |
| `match_evaluation` | 같은 경기의 평가자와 대상 참가자 사이 평가. | `MatchEvaluationService.submit`이 쓰고, 반영 때 `findReceivedEvaluations`로 읽는다. |
| `match_evaluation_score` | 평가×항목의 점수와 관찰 여부. | `MatchEvaluationService.submit`이 평가 행과 함께 쓴다. |
| `sport_rating` | `(user_id, sport_id)`를 키로 하는 현재 MMR·반영 경기 수. | 쓰기는 `RatingApplyService.apply`만 한다. 마이페이지(`MyRatingRecordMapper.findRatings`), 신청 확정의 `entry_mmr`, 동호회(`SportRatingService`)가 읽는다. |
| `sport_rating_history` | 경기 참가자별 이전/이후 MMR, 증감, 기대값, 공식 버전의 반영 이력. | `RatingApplyService.apply`가 쓴다. 마이페이지 이력(`findHistory`)과 운영 참가자 이력 모달(`ParticipantHistoryMapper.findRecentChanges`)이 읽는다. |

주요 관계는 `social_match → match_application → match_participant`다. `social_match`의 팀장 참조는 다시 참가자로 향하므로 순환 참조가 있으며, DDL은 참가자 테이블 생성 후 복합 FK를 추가한다. 평가의 항목별 점수는 평가·문항 양쪽을 복합 FK로 참조한다. 현재 MMR·반영 이력·경기 레이팅 상태는 한 트랜잭션에서 함께 바뀐다(10절 "동시성 보장").

## 3. DDL이 보장하는 사항

- 경기 종료는 시작 이후이며, 최소 인원은 1 이상이고 최대 인원은 최소 인원 이상이다. A/B 정원은 각각 0 이상이고 합계가 최대 인원과 같다. 비용은 0 이상이다.
- 배정 방식은 `MANUAL/AUTO`, 확정 결과는 `A_WIN/DRAW/B_WIN` 또는 NULL, 레이팅 상태는 `WAITING/READY/APPLIED/EXCLUDED`다.
- 참가자의 `(application_id, match_id)` 복합 FK로 신청과 참가자의 경기 일치를 보장한다.
- `active_match_user` 생성 컬럼과 `(match_id, active_match_user)` UNIQUE로 같은 경기에서 `대기/확정` 상태인 동일 회원의 중복 참가를 막는다. 비활성 상태에는 NULL이 생성되므로 과거 취소·반려 행은 남길 수 있다.
- 팀장과 결과 제출자·평가자·평가 대상은 복합 FK를 통해 같은 경기의 참가자여야 한다. 두 팀장을 모두 지정한 경우 서로 다른 참가자여야 하며, 자기 평가는 금지한다.
- 평가×항목 PK로 동일 평가의 동일 항목 중복을 막는다. 항목별 점수의 복합 FK는 평가와 항목의 종목 일치를 보장한다.
- 평가 척도는 10, 평가 종합 점수는 NULL 또는 0~10, 가중치는 0 이상이다. 항목별 관찰 여부와 점수의 CHECK에는 아래의 NULL 주의점이 있다.
- 현재 MMR의 반영 경기 수는 0 이상이다. 레이팅 이력은 참가자별 UNIQUE이며, 실제 결과는 0/0.5/1, 기대값은 0~1이다.
- 이력은 `final_delta = elo_delta + evaluation_delta`와 `mmr_after = mmr_before + final_delta`를 만족해야 한다.

## 4. 이후 서비스에서 확인할 무결성

다음은 초기 조회 골격을 분석할 때 식별한, DDL만으로 완전히 보장되지 않는 사항과 지금 이를 처리하는 곳이다.

| 확인 사항 | DDL의 한계 | 현재 처리 |
|---|---|---|
| 팀장 자격 | 같은 경기 참가자라는 사실만 보장한다. A팀장의 A 소속과 확정 참가 상태는 보장하지 않는다. | 팀장은 배정 때 그 팀 안에서 고른다(`TeamCaptainSelector`, `TeamAssignmentService.captainOf`). 결과·평가에서는 확정 참가자 중 팀장 참가자 ID와 같은 사람만 팀장으로 인정한다(`MatchResultService.captainsOf`, `MatchEvaluationService.evaluatorOf`). |
| 결과 제출·평가 권한 | FK만으로 실제 지정 팀장인지 알 수 없다. | 로그인 주체의 회원 ID로 위 두 메서드가 확인하고, 아니면 `AccessDeniedException`(403)이다. |
| 경기 종목 일치 | `match_evaluation.sport_id`와 `social_match.sport_id`의 일치를 보장하는 복합 FK는 없다. | 평가 저장 때 잠근 경기의 `sport_id`를 그대로 쓴다(`MatchEvaluationService.submit`). |
| 이력의 회원·종목 일치 | 이력의 회원·종목이 참가자·경기와 같은지 보장하지 않는다. | 참가자 행의 `user_id`와 경기의 `sport_id`로 이력을 만든다(`RatingApplyService.apply`). |
| 실제 정원과 상태 | 일반 상태 컬럼은 다수가 주석만 있으며 CHECK가 없다. | 배정 전에 모집 상태·정원·확정 인원을 확인하고(`AutoAssignmentService.checkAssignable`), 수동 배정은 팀 구성을 확인한다(`isValidTeams`). 상태 전이는 조건부 UPDATE로 한다(10절). |
| 제출·평가 중복 | 경기별 팀장 결과 제출, 평가자별 평가에 업무 중복 방지 UNIQUE가 없다. | 매칭방 행을 잠근 뒤 이미 낸 제출·평가가 있는지 확인한다(두 서비스의 `checkSubmittable`). 결과는 CONFLICT일 때만 다시 낼 수 있고, 평가는 고칠 수 없다. |
| 관찰 점수 NULL | `OBSERVED`와 `score=NULL`이면 점수 CHECK가 SQL의 UNKNOWN으로 평가되어 통과할 수 있다. | `MatchEvaluationService.isValidScore`가 `OBSERVED`는 0~10 정수, `NOT_OBSERVED`는 NULL만 받는다. 0점은 정상 점수다. |
| 평가 집계 | 집계·결측 규칙이 DDL에 없다. | 활성 항목 전부를 한 번씩 매겨야 제출되고(`checkRequest`), 집계는 `RatingCalculator.overallScore`·`playerScore`가 한다(10절 표). |
| 반영 원자성과 동시성 | 참가자별 이력 UNIQUE만으로 전체 정합성은 보장되지 않는다. | MMR·반영 수·이력·레이팅 상태를 한 트랜잭션에서 바꾸고, 매칭방 잠금·조건부 UPDATE·이력 UNIQUE로 이중 반영을 막는다(10절 "동시성 보장"). 반영 실패의 재시도 제한은 없다(13절). |

스키마 개선이 필요한 경우에는 담당자와 별도 협의하고 CONTRIBUTING에 따라 ERD와 SQL을 함께 변경한다. 이번 작업에서는 제약을 임의로 추가하지 않는다.

## 5. 초기 조회 골격의 패키지 및 계약 (당시 범위)

기존 기본 패키지 아래에 MAT 책임을 모아 다른 담당 코드와의 변경 경계를 분명하게 한다.

```text
co.kr.spoit.mat
├── domain
│   ├── MatchParticipant
│   └── SportRating
├── dto
│   ├── MatchParticipantResponse
│   └── SportRatingResponse
├── mapper
│   ├── MatchParticipantMapper
│   └── SportRatingMapper
└── service
    ├── MatchParticipantService
    └── SportRatingService
```

- Domain은 MyBatis가 채우는 Lombok POJO다. DB의 `BIGINT`는 `Long`, MMR의 `DECIMAL`은 `BigDecimal`, `DATETIME`은 `LocalDateTime`으로 표현한다. DB NULL 가능성은 유지한다. DB 중복 방지 전용 생성 컬럼 `active_match_user`는 참가자 Domain과 DTO에서 제외한다.
- DTO는 Java 17 `record`로 만들고 Domain에서 응답으로 변환한다. DB 매핑 객체와 서비스 반환 계약을 분리하며, 화면별 요구가 생기면 별도 DTO로 확장한다. 현재 DTO는 외부 HTTP 응답으로 공개되어 있지 않다.
- Mapper는 `@Mapper`, `@Select`, `@Param`을 사용한다. SQL의 각 컬럼에 Java 속성명 별칭을 명시하여 전역 underscore-to-camel-case 설정에 의존하지 않는다. 두 조회만을 위해 XML 매퍼나 공용 MyBatis 설정을 추가하지 않는다.
- Service는 생성자 주입을 사용하고, 조회에 `@Transactional(readOnly = true)`를 적용한다. 식별자가 NULL이거나 0 이하이면 조회 전에 `IllegalArgumentException`으로 거부한다. read-only 표시는 서비스의 조회 의도이며, 향후 쓰기·권한 검증을 대체하지 않는다.

| 서비스 메서드 | 반환·조회 계약 |
|---|---|
| `MatchParticipantService.findByMatchId(Long)` | `List<MatchParticipantResponse>`. 지정 경기의 모든 참가 상태를 포함하고 `participant_id` 오름차순으로 반환한다. 결과가 없으면 빈 목록이며, 경기 자체의 존재 여부는 이 조회만으로 구분하지 않는다. |
| `SportRatingService.findByUserIdAndSportId(Long, Long)` | `Optional<SportRatingResponse>`. 실제 저장 행이 없으면 `Optional.empty()`이며 조회 중 MMR을 생성하거나 기본값을 합성하지 않는다. |

참가 상태를 조회 단계에서 임의로 제한하지 않으므로 취소·반려 내역도 확인할 수 있다. 추후 팀 배정 기능은 자신의 확정된 참가 대상 규칙에 따라 별도 조회·필터링을 설계해야 한다. 인증·인가와 데이터 공개 범위는 HTTP 연결 전에 결정한다.

## 6. 저장 기본값과 미확정 정책

DDL에 명시된 값은 저장 계약으로 인정하되, 계산 정책으로 확대 해석하지 않는다.

| DDL에 명시된 계약 | 해석 |
|---|---|
| `sport_rating.current_mmr DEFAULT 1000` | 행 생성 시 컬럼을 생략할 때 사용하는 DB 기본값이다. 미존재 회원·종목 조회에 가상 1000점을 반환한다는 뜻은 아니다. |
| `match_participant.entry_mmr DEFAULT 1000` | MMR 스냅샷 컬럼의 DB 기본값이다. 현재는 신청 확정(`MatchApplicationService.confirm`) 때 그 회원의 `sport_rating.current_mmr`을 넣고, 레이팅 행이 없으면 1000을 넣는다. 스키마 설명은 "신청 당시"지만 실제 저장 시점은 확정 때다(13절 "팀 평균 기준 시점"). |
| `rated_match_count DEFAULT 0` | 반영 경기 수의 초기 저장값이다. 배치 경기 수나 티어 기준을 뜻하지 않는다. |
| `evaluation_delta DEFAULT 0` | 이력의 평가 보정 컬럼 기본값이다. 평가 보정을 영구적으로 사용하지 않는다는 정책은 아니다. |
| 평가 `scale_max DEFAULT 10` 및 CHECK | 10점 척도는 DDL이 명시적으로 보장한다. 평가 가중치와 결측 집계 공식을 확정하지는 않는다. |
| `assignment_method DEFAULT MANUAL`, `rating_status DEFAULT WAITING` | 초기 저장 상태다. 자동 배정 방식이나 상태 전이 조건을 정하지 않는다. |

초기 조회 골격 작업 당시에는 Elo K/D, 배치 경기 수, 평가 보정식과 계수, 팀 평균 산정 대상·시점, 반올림, MMR 상·하한, 티어, 결측 평가, 재반영·정정 정책을 확정하지 않았다. 이후 구현된 정책은 `RatingPolicy`와 10절을 참고한다. 별도 Python 실험의 가정·수치를 운영 정책으로 사용하지 않는다.

## 7. MySQL 통합 테스트 구성

### 검증 범위

아래 표의 테스트 건수는 초기 조회 골격 작업 당시의 기록이다. 이후 테스트가 추가될 수 있으므로 현재 테스트 수나 통과 결과를 뜻하지 않는다.

`AbstractMySqlMapperTest`는 `@MybatisTest`로 실제 프로덕션 Mapper를 등록하고 `@AutoConfigureTestDatabase(replace = NONE)`으로 실제 MySQL을 사용한다. Mock Mapper나 H2로 대체하지 않는다. underscore-to-camel-case 설정을 끈 상태에서 SQL의 명시적 별칭이 Java 필드에 매핑되는지 확인한다.

| 테스트 | 검증 내용 |
|---|---|
| `MatchParticipantMapperIntegrationTest` 3개 | 참가자 ID 오름차순, 다른 경기 배제, 전체 9개 필드, 대기·확정·취소 상태, 미배정·취소 시각의 NULL, 빈 목록, DECIMAL 소수 두 자리 |
| `SportRatingMapperIntegrationTest` 3개 | 회원·종목 두 키 조건, 전체 5개 필드, 미존재 조합의 빈 Optional, 조회 전후 행 수 불변, DECIMAL 양수·음수 저장 한계와 소수 두 자리 |
| `MySqlTestConnectionValidationTest` 24개 | 허용 주소 4개와 거부 주소 20개. 개발 DB명·원격 주소·쿼리·사용자 정보·다중 호스트·잘못된 포트 등을 연결 전에 거부 |
| 기존 서비스 테스트 13개 | 잘못된 식별자, 저장값 보존, 미등록 레이팅 비합성 |
| 기존 Spring 애플리케이션·화면 테스트 16개 | 전체 컨텍스트와 기존 화면 경로 회귀 검증 |

초기 작업 당시 전체 기본 테스트 실행 결과는 59개 통과였다(2026-10-22 기록). DECIMAL 경계 데이터는 MySQL 저장 타입과 Java `BigDecimal` 매핑을 확인하기 위한 값이며, 허용 MMR의 상·하한 정책을 정하지 않는다.

Fixture는 `src/test/resources/mat/mapper-fixture.sql`에 있다. FK를 만족하는 최소 회원·종목·지역·시설·경기·신청·참가자·레이팅 데이터를 INSERT하고, 참가자는 `30, 10, 20, 5` 순으로 넣어 정렬 여부를 검증한다. `@Sql`에서 UTF-8을 명시하고 각 테스트의 Spring 트랜잭션이 종료될 때 롤백한다. Fixture와 테스트 Java 코드에는 DDL·TRUNCATE가 없다.

### 연결 차단 조건과 DB 격리

테스트는 `MAT_TEST_DB_URL`, `MAT_TEST_DB_USERNAME`, `MAT_TEST_DB_PASSWORD` 세 환경변수가 모두 있어야 시작한다. 누락하면 개발 DB 설정으로 대체하지 않고 실패한다. URL은 `jdbc:mysql://localhost:포트/spoit_mat_test` 또는 `jdbc:mysql://127.0.0.1:포트/spoit_mat_test`만 허용한다. 포트는 1~65535이며 사용자 지정 쿼리, 사용자 정보, 다중 호스트는 허용하지 않는다. 필요한 JDBC 옵션은 검증 후 고정값으로 추가한다. 오류에는 입력한 URL·비밀번호를 출력하지 않는다.

`spring.config.import`를 비우고 SQL 자동 초기화를 끈다. 검증한 연결 값과 JDBC 드라이버는 `@DynamicPropertySource`로 지정한다. `@BeforeTransaction`에서 `SELECT DATABASE()`가 `spoit_mat_test`인지 확인하므로 다른 DB에서는 fixture를 실행하지 않는다. 테스트 자체가 `db/schema.sql`을 실행하거나 기존 DB를 초기화하지 않는다.

로컬 실행 스크립트 `scripts/test-mat-mysql.ps1`은 매번 새 임시 datadir과 동적 loopback 포트로 별도 MySQL 서버를 시작하고, 그 서버의 새 `spoit_mat_test` DB에만 저장소 스키마를 적용한다. 실행 중 난수 비밀번호의 테스트 계정을 생성하며 권한은 전용 DB의 `SELECT/INSERT/UPDATE/DELETE`로 제한한다. root 비밀번호도 난수이며 비밀번호를 명령 인수·로그·설정 파일에 기록하지 않는다. 종료 시 임시 서버·디렉터리를 정리하고 호출 전 환경변수를 복원한다.

스크립트는 기존 `@SpringBootTest`도 같은 임시 DB를 사용하도록 해당 프로세스의 `SPRING_DATASOURCE_*`를 설정한다. 개발용 DB 계정이나 비밀번호 파일을 복사·수정하지 않는다. 실패 시 기존 MySQL로 전환하는 대체 경로는 없다.

### 실행 방법

로컬 준비물은 Windows의 PowerShell 7, JDK 17, MySQL Server 8.4 실행 파일이다. 이번 검증 환경의 MySQL은 8.4.13이며 CI는 기존 8.4.11 이미지를 사용한다. 스크립트가 새 서버를 시작하므로 개발 MySQL 서비스에 스키마를 적용할 필요가 없다.

기본 실행은 저장소 루트에서 전체 `clean build`를 수행한다.

```powershell
pwsh -File scripts/test-mat-mysql.ps1 -JavaHome '<JDK17 경로>'
```

MySQL이 기본 설치 경로와 다른 곳에 있다면 `-MySqlBin '<MySQL bin 경로>'`를 추가한다. PowerShell 7 세션에서 Mapper 및 연결 검증 테스트만 선택할 수도 있다.

```powershell
& ./scripts/test-mat-mysql.ps1 -JavaHome '<JDK17 경로>' -GradleArgs @('test', '--tests', 'co.kr.spoit.mat.mapper.*', '--no-daemon', '--console=plain')
```

일반 `gradlew test`만 실행하면 새 통합 테스트에 필요한 전용 환경변수가 없으므로 실패한다. 로컬에서는 위 스크립트로 준비와 실행을 함께 수행한다.

CI는 기존 MySQL 8.4.11 서비스의 DB명을 `spoit_mat_test`로 바꾸고 `MAT_TEST_DB_*`를 제공한다. 기존 전체 컨텍스트 테스트의 `SPRING_DATASOURCE_*`와 스키마 적용 대상도 같은 임시 DB를 사용하며 `./gradlew build`에서 새 테스트를 함께 실행한다.

### 실행 결과

2026-10-22, JDK 17 / Gradle Wrapper 9.8.1 / 로컬 MySQL **8.4.13**에서 확인했다.

| 검증 | 결과 |
|---|---|
| 실행 스크립트의 기본 `clean build --no-daemon --console=plain` | **BUILD SUCCESSFUL**. 전체 프로덕션·테스트 컴파일, JAR 생성, 테스트 완료 |
| Gradle 테스트 결과 | **59개 통과, 실패 0, 오류 0, 건너뜀 0** |
| 실제 MySQL Mapper 테스트 | 참가자 3개 + 레이팅 3개 통과 |
| 테스트 DB URL 차단 검사 | 24개 통과 |
| 기존 서비스·애플리케이션·화면 테스트 | 29개 통과 |
| 스크립트 성공 종료 정리 | 새 mysqld 프로세스·임시 폴더가 남지 않고 환경변수 복원 확인 |
| 의도된 Gradle 실패 후 정리 | 존재하지 않는 태스크를 호출해 실패시킨 뒤에도 동일한 정리·환경 복원 확인 |

HTML 보고서는 `build/reports/tests/test/index.html`, JUnit XML은 `build/test-results/test/`에서 확인할 수 있다. 이전 `87ed48b`의 중복 클래스 실패 기록과 MAT 단독 컴파일 우회 절차는 현재 검증 절차에서 제거했다.

GitHub Actions는 설정만 변경했고 원격 실행은 하지 않았다. CI의 MySQL **8.4.11** 실행 결과를 로컬 8.4.13 검증으로 대신 주장하지 않는다.

위 2026-10-22 검증은 두 조회 Mapper의 실제 SQL 실행·매핑 및 테스트 데이터 롤백을 대상으로 했다. 이후 추가된 상태 전이, 동시성 제어, 쓰기 서비스 및 레이팅 계산·반영에 대한 검증 결과를 뜻하지 않는다.

## 8. 이후 구현 상태 (2026-10-30)

- `TeamAssignmentService`는 매칭방 행만 잠근다(`lockAssignmentMatch`의 `FOR UPDATE OF sm`, 삭제된 시설의 방은 조회하지 않음). 잠근 뒤 담당 시설 관리자인지와 상태·정원·확정 참가자를 다시 확인하고 자동 또는 수동 배정을 저장한다. 초기화는 팀·팀장 정보를 지우고 배정 방식을 기본값 `MANUAL`로 둔 채 모집 상태로 되돌린다.
- 배정 후보 MMR(`TeamCandidate`)은 `entry_mmr` 컬럼 범위(-999,999.99~999,999.99)와 소수 둘째 자리만 확인한다. 레이팅에 하한이 없으므로 음수 MMR 참가자도 자동·수동 배정된다(#96).
- `MatchAssignmentMapper`는 `social_match`와 `match_participant`의 배정 조회·잠금·저장·초기화 SQL을 애노테이션으로 제공한다. `MatchAssignmentMapperIntegrationTest`의 9개 테스트가 실제 MySQL 쿼리 계약을 확인한다.
- `MatchStartScheduler`는 서울 시간 기준 매분 시작 시각이 지난 모집 중 경기를 찾아 `TeamAssignmentService.startMatch`를 호출한다. 경기별 트랜잭션으로 자동 배정하거나, 이미 종료됐거나 참가 인원/정원이 맞지 않으면 취소한다. 한 경기 처리 실패가 다음 경기를 막지 않는다.
- 스케줄러 설정은 `spoit.match-start-scheduler.enabled`이며 `matchIfMissing=true`로 기본 활성화된다. 로컬 데이터에 대한 자동 변경을 원하지 않으면 실행 설정에서 `false`로 끌 수 있다. 테스트에서는 Gradle 설정으로 스케줄링을 비활성화한다.
- 배정 API는 2026-11-02에 관리자 HTML 화면과 연결됐다. `admin/operations.html`의 `#ops-assignment`가 `data-assignment-url`로 `/admin/operations/matches/{matchId}`를 넘기고, `match-assignment.js`가 같은 경로의 `mat` API(`TeamAssignmentController`)로 배정 조회·수동 확정·자동 배정·되돌리기를 호출한다. 초기 문서의 “팀 배정 미구현” 서술은 당시 상태 기록으로 읽어야 한다.
- 2026-10-30 재base 검증 기록: `fix/optimeze`의 `b169040`에서 격리 MySQL을 사용한 `gradlew build`가 성공했고 516개 테스트가 통과했다. 이는 최적화 테스트 변경도 포함한 검증 결과이며, 개발 DB는 사용하지 않았다.

### 2026-11-02 추가 구현 상태

- 매칭방 생성·수정·취소가 관리자 화면에서 동작한다. `match.controller.RoomAdminController`의 `POST /admin/rooms`, `POST /admin/rooms/{matchId}`, `POST /admin/rooms/{matchId}/cancel`이 `RoomCommandService`로 위임하고, 생성 시와 신청자가 없어 시설·종목·일정까지 수정하는 경우 `FacilityReservationService`(운영 일정·휴관)와 `FacilityCapacityService`(동시 대관 한도)가 최신 값으로 다시 검증한다. 취소는 방 상태와 함께 활성 신청·참가자를 취소한다.
- 팀 단위 신청(RES-04)이 구현됐다. 대표자가 고른 팀원마다 신청 행을 만들고 `match_application.group_application_id`(대표 행 ID)로 묶어 조회·취소를 함께 처리한다.
- 시설 대시보드가 실제 집계를 사용한다. `RoomManagementService.findDashboard`가 담당 활성 시설의 매칭방 행을 상태·시각으로 집계해 `FacilityAdminController`의 화면 모델로 전달한다.
- 시스템 관리자 페이지 컨트롤러는 `web.CorePageController`에서 `admin.controller.SystemAdminController`로 옮겨졌고, 모니터링 dashboard는 `SystemMonitoringService`의 실제 측정치를 쓰며 `POST /admin/system/monitoring`이 같은 모델을 JSON으로 돌려준다.

### 2026-11-06 추가 구현 상태

- 참가 신청 승인·반려 API가 추가됐다(`ce842cc`). `MatchApplicationDecisionController`가 `/admin/operations/**`의 `ROLE_FACILITY_MANAGER` 규칙 아래에서 `POST /admin/operations/applications/{applicationId}/confirm`·`/reject`를 노출하고, `MatchApplicationService.confirm`·`reject`에 위임한다. 서비스는 신청 row와 담당 시설 여부(`countManagedApplication`)를 확인하고 매칭방을 잠근 뒤 모집 중·시작 전 조건을 재검증한다. 승인은 `markConfirmed`로 신청을 확정하고 `insertParticipant`로 참가자 행을 만들며 확정 알림(MATCH_APPLICATION_CONFIRMED)을 커밋 후 보내고, 반려는 `markRejected`로 참가자 행 없이 반려 처리한다. `MatchApplicationMapper`에는 `markConfirmed`·`markRejected`·`countManagedApplication`이 추가됐다.
- 운영 화면에 승인·반려 UI가 연결됐다(`1596b50`). `admin/operations.html`의 `#ops-applications`가 `data-decision-url`로 API 기본 경로를 넘기고 `match-application-decision.js`가 승인·반려를 fetch + CSRF 헤더로 호출하며 성공 시 페이지를 새로 고친다. `RoomManagementService.findDetail`은 모집 중이고 시작 전인 경기의 대기 신청에 `decidable`을 표시하며, `RoomManagementMapper.xml`의 집계도 대기 신청과 확정 참가자를 같은 기준으로 센다.
- 프런트엔드 계약 검사를 승인·반려 API에 맞춰 갱신했다. `src/test/frontend/check-contracts.mjs`가 라우트·폼·마크업 계약을 검증한다.
- 시설 통계 집계와 조회 화면이 구현됐다(`353dce6`). `FacilityAdminController`는 `view=stats`일 때 `FacilityStatsService.load`를 호출하며 기존 `stats` null 하드코딩을 대체했다. `FacilityStatsService`(신규)는 `existsFacilityManager`로 담당 시설을 선검사한 뒤 `FacilityStatsMapper`(신규 XML)의 5개 쿼리(매칭방 수·참가 인원·신청 수·요일별 방 수·시간대별 방 수)로 집계한다. 모든 쿼리는 `social_match → facility(deleted_at IS NULL) → facility_manager(fm.user_id = 인증 주체)` 조인으로 담당 범위를 SQL 레벨에서도 재제한한다. 기간은 매칭방 `start_at` 기준이며 `status <> '취소'`만 세고, 7/30/90일 또는 직접 입력 기간(시작일 00:00:00~종료일 23:59:59)으로 정규화한다. 신청 수는 대표 행만(`group_application_id IS NULL OR = application_id`) 세고 개인·동호회·확정 건수를 함께 돌려준다. 화면(`admin/facility.html` stats 섹션)은 지표 카드 5개와 요일별·시간대별 CSS 막대 차트(최대값 대비 상대 %)로 표시하고, `POST /admin/facility/stats`는 조건을 쿼리로 되돌리는 PRG 리다이렉트다. `admin-facility.js`는 기간 select와 date 입력을 동기화하고 시설 미선택 제출을 막는다.

## 9. 이번 변경 파일과 이유 (초기 조회 골격 작업)

| 파일 | 이유 |
|---|---|
| `src/test/java/co/kr/spoit/mat/mapper/AbstractMySqlMapperTest.java` | 실제 MySQL 연결, 전용 DB 검증, UTF-8 fixture, 롤백을 공유 |
| `src/test/java/co/kr/spoit/mat/mapper/MatchParticipantMapperIntegrationTest.java` | 참가자 SQL 조건·정렬·필드 매핑 검증 |
| `src/test/java/co/kr/spoit/mat/mapper/SportRatingMapperIntegrationTest.java` | 회원·종목 키 조건과 레이팅 저장값 매핑 검증 |
| `src/test/java/co/kr/spoit/mat/mapper/MySqlTestConnectionValidationTest.java` | 잘못된 DB 대상으로의 연결을 사전에 차단하는 규칙 검증 |
| `src/test/resources/mat/mapper-fixture.sql` | 실제 FK를 만족하는 최소 통합 테스트 데이터 |
| `scripts/test-mat-mysql.ps1` | 기존 개발 DB와 분리된 임시 MySQL 실행·정리 |
| `.github/workflows/ci.yml` | 기존 CI MySQL 서비스에 전용 DB와 MAT 테스트 연결 값 제공 |
| `docs/mat-backend-design.md` | 현재 구조·검증 경계·재현 명령을 문서화 |
| PR 초안 문서 | 당시 작업 기록이며 해당 파일은 현재 저장소에 없다 |

프로덕션 코드와 스키마를 바꾸지 않고 저장소의 기존 의존성을 사용한다. 미확정 레이팅 정책은 추가하지 않는다.

## 10. 경기 종료 뒤 상태 흐름

§6의 미확정 항목 가운데 Elo 계수, 배치 경기 수, 티어, 반올림, 결과 제출 기간, 평가 보정은 이후 PR에서 정해 `RatingPolicy`에 모았다. 시각은 모두 Asia/Seoul이고, 상태 전환은 매분 실행하는 스케줄러가 맡는다. 결과 스케줄러는 리마인더 → 제출 마감 → 종료 전환 순서로 실행해, 마감에서 처리된 경기를 같은 실행에서 종료한다. 스케줄러는 `spoit.match-start-scheduler.enabled=false`로 함께 끈다.

경기 하나의 흐름은 다음과 같다(`end` = 종료 시각, `D` = `end` + 24시간).

1. **시작**: 시작 시각에 `TeamAssignmentService.startMatch`가 자동 배정하거나(모집 중 → 매칭 완료) 인원·정원이 맞지 않으면 취소한다.
2. **결과 제출**: `end` 이후 `D` 전까지 두 팀장이 `MatchResultService.submit`으로 낸다. 두 결과가 같으면 `result_status = CONFIRMED`다.
3. **평가 제출**: 같은 기간에 각 팀장이 `MatchEvaluationService.submit`으로 낸다. 결과 확정 전에도 받는다.
4. **즉시 반영**: 결과가 확정되고 두 팀장의 평가가 모두 들어오면, 마지막 제출 요청 안에서 `applyRatingIfReady` → `RatingApplyService.apply`가 반영한다(`rating_status = APPLIED`).
5. **마감**: `D`가 되면 `closeResult`가 결과가 확정된 경기는 그때까지 들어온 평가로 반영하고, 아니면 `EXCLUDED`로 둔다.
6. **종료**: `markEnded`가 반영·제외가 끝난 매칭 완료 경기를 종료로 바꾸고, 확정 참가자에게 결과 산정 완료 알림을 보낸다.

표시 티어는 저장하지 않고 MMR로 계산한다(`TierRank`). 브론즈·실버·골드·플래티넘·다이아마다 4단계(4가 가장 낮음)를 두고, MMR 800부터 50마다 한 단계씩 오른다(브론즈 4 = 800 이상 850 미만, 다이아 1 = 1750 이상). 포인트는 단계 시작부터 오른 MMR × 100 / 50을 버림한 값이다. 800 미만은 브론즈 4 · 0P이고, 다이아 1 위로는 단계 없이 포인트만 늘어난다(1800 → 100P). 반영 경기가 5경기 미만이면 "배치 중 (n/5)"로 표시한다.

| 경기 상태 `status` | 조건 |
|---|---|
| 모집 중 → 매칭 완료 또는 취소 | 시작 시각에 자동 배정한다. 인원이나 정원이 맞지 않으면 취소한다. |
| 매칭 완료 → 종료 | `end_at <= now`이고 `rating_status`가 APPLIED 또는 EXCLUDED. 종료는 결과·평가 처리가 모두 끝난 경기를 뜻한다. 처리 중인 경기는 아직 진행 중으로 보고, WAITING이면 종료 시각이 지나도 매칭 완료로 둔다. 경기 중에 레이팅에서 제외된 경기도 종료 시각이 지나야 바뀐다. |

평가는 WAITING일 때만 받고 APPLIED는 아래 반영 시점에만 되므로, 종료 전환은 레이팅 상태만 보고도 "결과·평가 처리가 끝나야 종료"가 된다. 처리가 끝나기 전(최대 `end_at + 24시간`)에는 시설 삭제가 막히고, 운영자 매칭방 관리 화면에는 매칭 완료로 보인다.

결과는 확정 참가 중인 A·B팀장만 제출한다. 경기 상태가 매칭 완료 또는 종료이고, `end_at <= now < end_at + 24시간`이며, `rating_status = WAITING`이어야 한다. 종료 경기는 레이팅 처리가 끝났으므로 실제로는 매칭 완료 경기만 제출된다. 취소·종료된 경기는 시설 삭제를 막지 않으므로, 결과 조회와 마감 처리는 삭제된 시설의 경기도 대상으로 한다.

| 상황 | 제출 행 `submission_status` | 경기 `result_status` |
|---|---|---|
| 한 팀장만 제출 | SUBMITTED | SUBMITTED (미제출이면 NULL) |
| 두 결과가 같음 | 둘 다 CONFIRMED | CONFIRMED, `final_result` 저장 |
| 두 결과가 다름 | 둘 다 CONFLICT, 양 팀장에게 알림 | CONFLICT |
| CONFLICT 상태에서 다시 제출 | 이전 행은 REPLACED, 새 행으로 다시 비교. 직전과 같은 결과면 `ALREADY_SUBMITTED`로 거절한다(`checkSameResubmission`, #96) | 위 규칙을 따른다 |

| 레이팅 상태 `rating_status` | 조건 |
|---|---|
| WAITING → APPLIED | 결과가 확정되고 두 팀장의 평가가 모두 들어오면 바로 반영한다. 결과가 확정될 때와 평가가 제출될 때 둘 다 확인한다. 마감 때는 결과만 확정돼 있으면 평가가 덜 들어왔어도 그때까지 들어온 평가로 반영한다. 두 조건은 `isReadyToApplyNow`와 `isReadyToApplyAtDeadline`으로 나눠 둔다. |
| WAITING → EXCLUDED | `end_at + 24시간`까지 확정되지 않으면 경기 상태와 관계없이 제외한다. 담당 시설 관리자는 시작 뒤 노쇼 경기를 제외할 수 있고, `result_reviewed_by`에 남는다. 화면은 매치 운영 상세의 제외 버튼이다(#99, `RatingExclusionController` → `excludeRating`). 버튼 표시는 `RoomManagementService.ratingOf`가 같은 조건으로 정하고, 서버가 잠금 아래에서 다시 확인한다. |

`READY`는 쓰지 않는다. 마감 1시간 전(`end_at + 23시간`)이 실행 시각의 1분 구간에 드는 경기에서는 결과 제출 행이나 평가 행이 없는 팀장에게 리마인더("경기 결과·평가 미제출")를 보낸다. 알림 테이블로 중복을 확인하지 않으므로, 그 1분에 앱이 멈춰 있었으면 리마인더는 다시 보내지 않는다.

팀장 평가는 결과 제출과 같은 팀장·기간·`rating_status = WAITING` 조건에서 받는다(`/mypage/matches/{matchId}/evaluation`). 팀장 한 명이 팀이 배정된 확정 참가자 전원(자기 자신 제외)을 경기 종목의 활성 항목 전부에 대해 한 번에 제출하고, 제출한 평가는 고칠 수 없다. 항목 점수는 0~10 정수(OBSERVED) 또는 관찰 불가(NOT_OBSERVED, 점수 NULL)다. (경기, 평가자) UNIQUE가 없으므로 결과 제출처럼 `social_match`를 잠근 뒤 이미 제출했는지 확인해 중복을 막는다. 평가할 참가자나 활성 항목이 없으면 제출을 받지 않는다.

| 값 | 계산 (소수 2자리 HALF_UP) |
|---|---|
| 평가자별 점수 `match_evaluation.overall_score` | 관찰한 항목만 Σ(점수×가중치)/Σ가중치. 관찰한 항목이 없거나 그 가중치 합이 0이면 NULL |
| 선수 평가 점수 `evaluation_score` | 그 경기에서 받은 `overall_score` 중 NULL이 아닌 값의 평균. 없으면 NULL |
| 평가 보정 `evaluation_delta` | (평가 점수 − 5) × 1. 평가 점수가 NULL이면 0이고, 배치 중에도 적용한다 |
| 최종 증감 `final_delta` | `elo_delta + evaluation_delta`. 공식 버전은 `ELO-K32-V2` |


운영 화면의 참가자 이력 모달은 `GET /admin/operations/matches/{matchId}/participants/{userId}/history`로 요약만 받는다. 팀 배정 화면과 같이 그 경기 시설의 시설 관리자만 볼 수 있고(아니면 403), 경기가 없거나 그 경기에 신청 행(`match_application`)도 참가자 행(`match_participant`)도 없는 회원이면 본문 없이 404다. 응답은 `nickname`, 경기 종목 기준 `tierLabel`(레이팅 행이 없으면 "기록 없음")과 `ratedMatchCount`, 그 종목 `sport_rating_history`의 최근 5건 `recentChanges`(`calculatedAt`, `finalDelta`, 최신순), 확정 참가한 경기 중 상태가 매칭 완료·종료이고 `rating_status = EXCLUDED`인 경기 수 `excludedMatchCount`(전 종목, 자동 취소 경기 제외), 대상 신고 중 '처리 완료'만 센 `confirmedReportCount`, '적용 중'이고 `end_at`이 없거나 지금보다 뒤인 제재의 `penaltyType`·`endAt` 목록 `activePenalties`다. 신고 내용·신고자·제재 사유는 내보내지 않고, `report`·`penalty`는 읽기만 한다.

### 동시성 보장

MAT 쓰기 경로는 아래 네 가지를 함께 쓴다. 근거는 코드의 실행 순서이며, MAT에는 실제 DB 동시성 테스트가 없다.

1. **매칭방 행 잠금이 트랜잭션의 첫 읽기다.** 결과·평가 제출, 마감, 레이팅 제외, 배정 확정·취소, 시작 처리는 먼저 `social_match` 한 행을 `FOR UPDATE`로 잠근다(`lockResultMatch`, `lockAssignmentMatch`). MySQL 기본 격리 수준(REPEATABLE READ)에서는 첫 일반 SELECT 때 스냅숏이 생긴다. 그래서 잠금 앞에 일반 조회를 두면 잠금을 기다리는 동안 커밋된 다른 제출을 보지 못한다(`MatchEvaluationService.submit` 주석).
2. **잠근 값으로 다시 검사한다.** 스케줄러는 대상 ID를 잠금 없이 찾고(`findExpiredMatchIds`, `findStartableMatchIds`), 잠근 뒤 상태·시각을 다시 확인해 그 사이 처리된 경기는 건너뛴다(`closeResult`, `startMatch`). 요청도 잠근 값으로 권한·기간·레이팅 상태를 확인한다.
3. **상태 변경은 조건부 UPDATE와 행 수 검사로 한다.** `markAssigned`, `clearAssignment`, `markCancelled`, `markRatingProcessed`, `markEnded`는 이전 상태를 WHERE에 넣는다. 잠금 아래에서 바꾼 행이 1행이 아니면 예외로 전체를 롤백한다. 조건이 없는 `markResult`는 잠금과 검사 뒤에서만 불린다.
4. **이력 UNIQUE가 마지막으로 막는다.** `sport_rating_history`는 참가자별 UNIQUE다. 위 검사가 뚫려도 두 번째 반영은 이력 INSERT에서 실패하고, 같은 트랜잭션의 MMR 갱신까지 롤백된다.

| 경우 | 동작 | 근거 |
|---|---|---|
| 두 팀장의 동시 결과 제출 | 하나씩 처리되고, 뒤 요청이 앞 제출을 보고 비교한다. | `MatchResultService.submit` |
| 같은 팀장의 중복 제출 | 잠근 뒤 이미 낸 제출·평가가 있으면 거절한다. 평가는 (경기, 평가자) UNIQUE가 없어 이 확인으로만 막는다. | 두 서비스의 `checkSubmittable` |
| 레이팅 이중 반영 | WAITING 재확인, 조건부 UPDATE, 이력 UNIQUE가 한 트랜잭션 안에서 막는다. | `applyRatingIfReady`, `markRatingProcessed`, `RatingApplyService.apply` |
| 평가 제출과 마감이 겹침 | 같은 행을 잠그므로 먼저 잡은 쪽 기준으로 반영 또는 제외가 한 번만 일어난다. | `closeResult`, `MatchEvaluationService.submit` |
| 종료 전환을 잠금 없이 실행 | UPDATE가 최신 커밋 값으로 조건을 평가하므로 WAITING 경기는 종료되지 않는다. 1행을 바꾼 실행에서만 완료 알림을 보낸다. | `markEnded` |
| 스케줄러에서 한 경기 실패 | 경기마다 별도 트랜잭션과 try/catch로 처리해 다음 경기를 계속한다. 리마인더는 한 번의 호출로 그 회차 전체를 보내므로, 예외가 나면 남은 경기에는 보내지 않는다(13절 "알림 저장이 업무 트랜잭션 안에 있음"). | `MatchResultScheduler.process`, `MatchStartScheduler.startMatches` |

### 잠금 순서와 시간 경계

MAT 트랜잭션은 `social_match`를 한 행만 잠그고, `sport_rating`은 회원 ID 오름차순으로 잠근다(`RatingApplyService.apply`). 없는 레이팅 행은 먼저 만든 뒤 잠근다(`lockRating`의 `insertIfAbsent`).

| 경로 | 잠금 순서 |
|---|---|
| 결과 제출, 평가 제출, 마감 처리 | `social_match` → 반영할 때 `sport_rating`(회원 ID 오름차순) |
| 레이팅 제외, 팀 배정 확정·취소, 시작 처리 | `social_match` |
| 종료 전환 | `social_match` 1행 조건부 UPDATE(잠금 읽기 없음) |
| 위 경로의 INSERT(레이팅 행·이력·알림) | FK 확인으로 `app_user` 등 부모 행에 공유 잠금 |
| (RES) 개인 신청 | 시설 → `social_match` → 신청자 `app_user` (#98) |
| (RES) 팀 신청 | 시설 → `social_match` → 동호회 → 대표·팀원 `app_user`(ID 오름차순) (#98) |
| (RES) 신청 확정·반려·취소 | `social_match` |
| (RES) 매칭방 수정 / 취소 | 시설 → `social_match` / 시설 → `social_match` → 관련 `app_user`(공유 잠금, NOWAIT) |
| (회원) 탈퇴 | `app_user` |

시각은 모두 `LocalDateTime.now(Asia/Seoul)`이다. `start` = 시작 시각이고, `end`·`D`는 위와 같다.

| 판정 | 조건 | 근거 |
|---|---|---|
| 결과·평가 제출 가능 | `end <= now < D` | 두 서비스의 `checkSubmittable` |
| 마이페이지 결과 제출 버튼 | `now - 24h < end <= now` | `MatchResultPageService.findResultSubmittableMatchIds` |
| 마감 대상 조회 / 마감 처리 | `end <= now - 24h` / 잠근 값으로 `D <= now` | `findExpiredMatchIds` / `closeResult` |
| 리마인더 | `D - 1h`가 `(실행 분 - 1분, 실행 분]` 안 | `sendReminders`, `findReminderTargets` |
| 종료 전환 | `end <= now`이고 APPLIED 또는 EXCLUDED | `findEndedMatchIds`, `markEnded` |
| 레이팅 제외 | `start <= now`이고 WAITING | `excludeRating` |
| 배정 변경 차단 | `start <= now` | `TeamAssignmentService.isStarted` |
| 시작 처리 / 자동 취소 | `start <= now` / 그때 `end <= now`이면 취소 | `findStartableMatchIds`, `startMatch` |

## 11. 알림과 결과 화면 연결

결과 화면 `GET /mypage/matches/{matchId}/result-form`은 `MatchResultPageController`가 `getSummary`로 확정 참가자인지 확인한 뒤 렌더링한다. 경기가 없으면 404, 확정 참가자가 아니면 403이다. 화면은 아래 세 조회를 쓴다(경로 앞부분은 `/mypage/matches/{matchId}`).

| 조회 | 대상 | 응답 | 화면에서 쓰는 곳 |
|---|---|---|---|
| `GET /result/summary` (`MatchResultService.getSummary`) | 확정 참가자 | `MatchResultSummary`: 경기 일시·시설·제출 마감, 결과·레이팅 상태, 최종 결과, 내 진영, 양 팀 로스터(닉네임·팀장 여부). 본인이 팀장이면 본인 제출 결과만 담는다. | 요약 카드·로스터·상태 안내(`match-result.js`의 `loadSummary`) |
| `GET /result` (`MatchResultService.getView`) | 확정 참가 중인 팀장 | `MatchResultView`: 내 제출, 상대 제출 여부, 불일치일 때만 상대 결과, 최종 결과, 레이팅 상태, 마감, `canSubmit` | 결과 제출 영역, 단계 바의 결과·반영 단계(`loadCaptain`) |
| `GET /evaluation` (`MatchEvaluationService.getView`) | 확정 참가 중인 팀장 | `MatchEvaluationView`: 평가 대상·활성 항목, `submitted`, `canSubmit`, 마감 | 평가 영역(`match-evaluation.js`), 단계 바의 평가 단계 |

- 요약 조회가 성공한 뒤에만 팀장 조회를 부른다. 팀장 조회가 403이면 제출·평가 영역과 단계 바를 숨기고, 팀장이면 평가 영역을 열어 `match-result:captain` 이벤트로 평가 조회를 시작한다.
- 로스터에는 회원 번호를 넣지 않고 팀장을 목록 앞에 둔다. `match-result.js`는 값을 `textContent`로 그린다.
- **단계 바(#94)**: 팀장에게만 보이며 결과 제출 → 선수 평가 → 레이팅 반영의 세 단계다. 결과·반영 단계는 팀장 조회의 `finalResult`·`ratingStatus`·`canSubmit`·`myResult`로, 평가 단계는 평가 조회의 `submitted`·`canSubmit`으로 정한다. 평가 응답은 `match-evaluation.js`가 `match-evaluation:loaded` 이벤트로 넘긴다. 서버가 결과 확정 전에도 평가를 받으므로 결과·평가 단계가 함께 진행 중일 수 있다. 팀장 재조회가 실패하면 마지막으로 그린 단계를 그대로 둔다.
- **평가 진행률 바(#94)**: 서버 응답이 아니라 화면 입력으로 센다. 전체는 대상 수 × 항목 수이고, 채운 칸은 제출 검사와 같은 빈 칸 기준이다(`progressCounts`). 제출한 뒤에는 "평가 제출 완료"를 보인다.
- 평가를 제출하면 `match-evaluation:submitted` 이벤트로, 결과를 제출하면 그 응답 뒤에 요약과 팀장 조회를 다시 불러 상태와 단계를 갱신한다.
- 마이페이지 지난 참여 카드의 결과 제출 버튼은 `MatchResultPageService.findResultSubmittableMatchIds`가 정한다(10절 시간 경계).

알림은 다음과 같이 연결된다.

- 자동·수동 팀 배정, 자동 취소, 결과 불일치·리마인더는 `MatchNotifier`에서 공통 `NotificationDispatcher`로 위임한다. `markEnded`가 실제로 한 행을 종료로 바꾼 경우에만 확정 참가자에게 `MATCH_RESULT_COMPLETED`를 예약한다. 레이팅 반영·제외 모두 같은 완료 안내를 사용한다.
- 현재 `NotificationDispatcher`는 수신자를 중복 제거한 뒤 `NotificationService.notifyRecipients`로 즉시 위임한다. `notifyRecipients`의 `@Transactional`(기본 `REQUIRED`)은 호출자의 업무 트랜잭션에 참여해 알림을 저장하고, 커밋 후 동기화 콜백에서 `/user/queue/notifications` 전송을 예약한다. 업무 트랜잭션이 있으면 저장 실패가 업무 롤백으로 이어질 수 있고, 스케줄러 리마인더처럼 업무 트랜잭션이 없는 호출은 알림 서비스가 새 트랜잭션으로 저장한다. 아웃박스와 재시도 작업자는 없어 커밋 직후 전송 실패나 서버 종료 시 실시간 전송은 놓칠 수 있지만 DB 알림함에서 복구한다. 전송 실패는 로그만 남기고 저장 결과에 영향을 주지 않는다. 초기 계획의 수신자별 별도 저장 트랜잭션과는 다른 현재 동작이다.
- 매칭 참가 신청은 대기 상태로 접수된다. 시설 관리자가 매치 운영 상세에서 승인하면(`MatchApplicationDecisionController` → `MatchApplicationService.confirm`) 참가자 행이 생기고 신청자에게 확정 알림이 간다. 팀 배정은 이미 확정된 참가자를 대상으로 하며 신청을 자동 승인하지 않는다.

### 초기 구현 시 검증 기록

격리 Docker MySQL 8.4에서 전체 `gradlew build`를 한 번 실행했다. 827건 중 37건이 실패했으며, 최초 원인은 시설 동시성 테스트의 정리 코드가 새 알림 FK 참조를 먼저 삭제하지 못한 것이었다. 해당 테스트의 자체 데이터 삭제 순서를 보완한 뒤 실패한 여섯 클래스의 37건만 새 격리 DB에서 재실행해 모두 통과했다. 전체 빌드를 두 번째로 실행한 결과는 아니다.

당시 프런트엔드 계약 831개와 알림·결과 화면 JavaScript 테스트가 통과했다. 두 HTTP 요청의 서로 다른 XOR 마스킹 CSRF 토큰을 검증하는 테스트와 타인 큐 `MESSAGE` 발행 차단 테스트를 포함한 보안 검토도 통과했다. 이 기록은 이후 알림 저장 트랜잭션·`club_id` 스키마·목록 조회 변경 전 결과로, 현재 소스 전체의 검증 결과를 뜻하지 않는다. 실제 브라우저 시각·실서버 종단 간 검증은 별도다. 이번 문서 갱신에서는 소스·문서 대조와 링크 확인만 수행한다.

## 12. 마이페이지 레이팅 표시

마이페이지(`MyPageController`)는 레이팅을 읽기만 하고 계산·저장하지 않는다(`MyRatingRecordService`는 `@Transactional(readOnly = true)`). 회원 ID는 로그인 주체에서만 받는다.

| 표시 | 읽는 곳 | 규칙 |
|---|---|---|
| 종목 카드 (#92) | `MyRatingRecordMapper.findRatings`: `sport_rating` JOIN `sport`, `rated_match_count > 0` | 반영된 경기가 있는 종목만 보인다. 문구는 `RatingTier.labelOf`(5경기 미만은 "배치 중 (n/5)"), 진행도는 `TierRank`의 포인트다. |
| 경기 이력 (#92) | `findHistory`·`countHistory`: `sport_rating_history` JOIN `social_match`·`sport`·`match_participant`, LEFT JOIN `facility`·`match_application`(참가자 행의 `application_id`) | 최신 20건이다. 이력은 반영 때만 생기므로 반영 전·제외·취소 경기는 따로 거르지 않아도 빠진다. 종목 필터 값이 숫자가 아니거나 내 레이팅에 없는 종목이면 전체로 본다. |
| 대표 종목 레이팅 (#97) | 저장된 대표 종목(`MemberService.findRepresentativeSport`, `user_preferred_sport`)과 위 종목 카드 | 대표 종목을 정했으면 그 종목 카드의 문구이고, 카드가 없으면 "기록 없음"이다. 정하지 않았으면 반영 경기가 가장 많은 종목(같으면 종목 번호가 작은 쪽)을 "경기 기록 기준"으로 보인다. 둘 다 없으면 "기록 없음"이고 종목 링크를 그리지 않는다(`representativeOf`). |
| 다른 종목 (#97) | 위 종목 카드 | 대표 종목을 뺀 카드다(`otherSportsView`). 종목 카드와 같은 조회 결과를 다시 쓴다. |
| 프로필 수정 화면 (#97) | `MyRatingRecordService.findSportCards` | 종목 option마다 그 종목 문구를 `data-rating`에 싣고, 처음 미리보기는 저장된 대표 종목의 값이다. |

## 13. 알려진 한계와 정책 결정이 필요한 것

[정책]은 동작을 확인했고, 그 동작이 의도인지 정해야 하는 항목이다.

- **리마인더 유실·중복**: `sendReminders`는 실행 시각의 1분 구간만 보고, 보낸 기록을 남기지 않는다. 그 분의 실행이 늦어지거나 빠지면 다시 보내지 않고, 앱을 여러 대 띄우면 인스턴스마다 보낸다.
- **반영 실패 재시도**: `closeResult`의 반영이 예외로 실패하면 롤백되고 다음 분에 다시 대상이 된다. 재시도 횟수 제한과 실패 기록이 없어서, 데이터가 어긋난 경기는 매 분 실패하며 매칭 완료로 남는다.
- **팀 평균 기준 시점 [정책]**: 기대 승률의 팀 평균은 확정 때 저장한 `entry_mmr`의 평균이다(`RatingApplyService.averageOf`). 스키마 설명은 "반영 시점 A팀 평균 MMR"이다.
- **교착 가능성**: 레이팅 반영과 알림 저장은 `social_match`를 잡은 채 FK 확인으로 `app_user`에 공유 잠금을 건다. 점검 때 본 교착은 신청 접수가 계정을 매칭방보다 먼저 잠그는 순서를 전제로 했다. #98 이후 신청 접수는 매칭방을 계정보다 먼저 잠그고, 계정을 먼저 잠그는 다른 경로(탈퇴, 동호회 생성·가입 승인·회장 위임)는 매칭방을 잠그지 않는다. 코드상 해당 경로는 없으며, 동시 실행으로 재현하지는 않았다.
- **레이팅 제외 조건 [정책]**: 시작 뒤 WAITING이면 결과가 확정된 경기도 제외할 수 있다(`excludeRating`). 제외할 때 팀장·참가자에게 따로 알리지 않고, 종료 전환 때 결과 산정 완료 알림만 간다.
- **알림 저장이 업무 트랜잭션 안에 있음**: `NotificationService.notifyRecipients`가 호출자의 트랜잭션에 참여한다. 그래서 알림 저장이 실패하면 배정·시작 처리·종료 전환·결과 불일치 제출도 함께 롤백된다(`MatchNotifierTest.propagatesStorageFailureSoMatchWorkCanRollBack`). 시작 처리와 종료 전환은 다음 분에 다시 시도하고, 리마인더는 그 회차의 남은 경기에 보내지 않는다.
- **MMR 하한 없음 (#96) [정책]**: 레이팅 계산(`RatingApplyService.apply`)에는 하한이 없고, 배정 입력(`TeamCandidate`)은 컬럼 범위만 확인한다. 음수 MMR을 둘지는 정책으로 정해야 한다.
- **시간대**: MAT는 시각을 `LocalDateTime.now(Asia/Seoul)`로 직접 구하고 cron에도 `zone`을 둔다. 다만 다음 두 설정에 기댄다.
  - JVM 기본 시간대: MAT 알림의 생성·읽음 시각은 기본 시간대의 `LocalDateTime.now()`다(`NotificationServiceImpl.notifyRecipients`, `NotificationApiController`). 배포 이미지는 `TZ=Asia/Seoul`로 이 값을 맞춘다(`Dockerfile`).
  - DB 시간대: DB가 채우는 `CURRENT_TIMESTAMP` 열(MAT에서는 `sport_rating.updated_at`)은 MySQL 세션 시간대를 따른다. 저장소의 DB URL에는 `serverTimezone=Asia/Seoul`이 있지만, 운영 URL은 환경 변수 `SPRING_DATASOURCE_URL`로 주입한다(`Dockerfile` 주석). 운영 URL에 이 값이 있는지, 이 값이 세션 시간대까지 바꾸는지는 확인이 필요하다.
- **단식 경기의 평가 [정책]**: 단식(팀당 1명)은 평가자가 상대 한 명뿐이라 감정이 보정에 섞일 수 있다. 보정은 (평가 점수 − 5) × 1이라 ±5로 제한되어(`RatingCalculator.evaluationDelta`) 영향은 작다고 보고, 팀 논의로 지금처럼 평가하기로 했다. 운영 데이터가 쌓이면 단식 평가 보정과 실제 결과의 관계를 보고 다시 정한다(예: 단식만 보정 폭 축소).
## 14. 이후 수정된 문제와 방법 (yongminp42 커밋 기준, 2026-10-28 이후)

아래는 2026-10-28 이후 yongminp42 작성 커밋을 대상으로, 커밋 메시지가 아니라 실제 diff와 현재 소스를 대조해 정리한 기록이다. 다른 작성자의 커밋은 분석에서 제외했다.

### 결과 화면 정보 소실과 복원

`af5a0b8`(refactor(mat), 2026-11-05)은 같은 결과 화면을 서버 모델(`MatchResultPage` + `MatchResultPageService.getPage`)과 JSON API(`MatchResultService.getSummary` + `match-result.js`) 두 경로로 구현하면서 `scheduleOf`·`teamOf`·팀장 판정·마감 계산이 각각 복제돼 있던 것을 JSON 단일화로 정리했다. 이 커밋에서 `MatchResultPage` DTO와 `getPage`가 삭제되고, `MatchResultPageService`에는 마이페이지 카드용 `findResultSubmittableMatchIds`만 남았다.

직후 리베이스(develop 화면 병합)에서 경기 일시·시설명·제출 마감·A/B 로스터가 화면에서 사라졌다. `dd1d645`(fix(match), 2026-11-05, 리베이스 후 `26ee4a7`)이 `MatchResultSummary`에 `scheduleLabel`·`facilityName`·`deadlineLabel`·`teams`를 추가하고, `MatchResultService.getSummary`가 `ResultPageMatch`의 시설명과 확정 참가자 로스터로 그 값을 채우게 했다. 이어 `ddaebc1`이 `title`·`sportName`까지 summary로 옮겨, JS가 제목을 고정 문구로 덮어쓰던 문제를 없앴다.

`match-result.js`는 서버 값을 `createElement` + `textContent`로만 렌더해 닉네임에 섞인 마크업이 실행되지 않게 한다. 템플릿(`mypage/match-result.html`)에는 `page.matchId`, `page.mySide` 같은 `page.*` 참조가 일부 남아 있으나 서버가 그 모델을 더 이상 넘기지 않는 죽은 참조이며, 미정리 상태다.

### 알림 기능 후속 결함 5종

`ddaebc1`(fix(common), 2026-11-06, 리베이스 전 `8c214ba`)에서 알림 클라이언트와 서버 쪽 결함을 함께 고쳤다.

- 숨은 탭: `notification-center.js`의 `paused` 초기값이 false여서 숨은 탭에서도 조회·소켓 연결이 시작됐다 → `let paused = document.hidden`으로 초기화하고 `refresh` 진입부에 `paused` 가드를 추가했다.
- 폴링·배지: STOMP `CONNECTED`에서 `stopPolling()`을 호출해 전송이 유실되면 배지가 뒤처졌고, 첫 연결이 실패한 구간에는 폴링 자체가 없었다 → `connect()`가 소켓 생성 전에 항상 `startPolling()`을 호출하고 `CONNECTED`의 `stopPolling()` 호출을 제거했다.
- 포커스: 같은 알림이 헤더·관리자 등 여러 surface에 렌더되는데 초점 복원이 첫 surface로 튀었다 → 포커스 토큰에 대상 `surface`를 포함해 `restoreFocus`가 같은 surface 안에서 찾게 했고, 읽은 알림의 읽음 버튼은 `hidden` 처리 대신 DOM에서 `remove()`한다.
- 배포 스키마: 알림 스키마가 적용되지 않은 DB로 배포하면 알림 저장이 실패한다 → `NotificationSchemaCheck`(신규, `main`)이 `notification.club_id → club.club_id` FK 존재를 메타데이터로 확인하고 없으면 예외로 배포를 중단하며, `deploy.yml`이 기존 컨테이너 교체 전에 이 검사를 실행한다.
- 매칭방 취소 교착: 취소가 시설·매칭방 락을 잡은 채 알림 저장이 필요로 하는 `app_user` 공유락을 기다려 지연·교착 위험이 있었다(신청 경로는 계정 → 매칭방 순서로 락을 잡는다) → `RoomCommandService.cancel`에 `Isolation.READ_COMMITTED`를 적용하고, 수신자 확정 직후 `lockCancellationAccounts()`가 `app_user`를 id 오름차순으로 `FOR SHARE NOWAIT` 조회하며, MySQL 락 대기 타임아웃(3572)이면 사용자 안내 예외로 변환한다.

### 머지·리베이스 잔재 결함

`c8a8a5a`(fix, 2026-11-05): 복구된 머지에서 `MatchApplicationService.applyTeam`에 `LocalDateTime now`가 중복 선언되고 `LinkedHashMap` import가 겹쳐 컴파일이 깨졌으며, `requireApplicableMember` 본문이 잘려 탈퇴·이용정지 검사가 사라져 있었다 → 중복 선언·import를 제거하고 두 검사를 복원했다.

`46855fb`(fix(common), 2026-10-27): 관리자 스크립트 IIFE를 `})();`로 호출해 인자 없이 실행되면서 `global`(window)이 undefined가 되어 모달·탭·사이드바 초기화가 전부 죽었다 → `})(window);`로 마감했다.

### 스케줄러 등록·토글 결함

`c7ff7cb`(fix(common), 2026-11-03): `spoit.match-start-scheduler.enabled=false`로 꺼도 `MatchResultScheduler`가 `@Component`로 컴포넌트 스캔에 등록돼 계속 실행됐다 → `@Component`를 제거하고 `MatSchedulingConfig`의 `@Bean`으로만 등록해 설정값이 실제로 효력을 갖게 했다.

`98d8458`(feat(admin), 2026-11-02): `@EnableScheduling`이 mat 설정에 있어 매치 스케줄러를 끄면 시스템 모니터링 수집까지 함께 멈췄다 → `@EnableScheduling`을 전역 `global.config.SchedulingConfig`로 옮겨 두 기능의 활성화 조건을 분리했다.

### 시설 종목·삭제 보호

`38ae978`(fix(facility), 2026-10-28): 종목 수정이 전량 삭제 후 재삽입이라 유지한 종목의 `default_*` 값이 초기화됐고, 매칭방이 참조 중인 종목을 해제하면 FK 오류(1451)가 그대로 노출됐으며, 모집 중·매칭 완료 방이 있는 시설도 삭제가 시도됐다 → 시설·매칭방 행을 `FOR UPDATE`로 잠근 뒤 현재 매핑을 읽어 추가/삭제 diff만 반영하고, 삭제는 차단 상태(RECRUITING·MATCHED)만 막으며, 경합 중 발생한 FK 오류는 사용자 안내 예외로 변환한다.

`26f2d8f`(feat(facility), 2026-10-29): 삭제가 막혀도 어떤 매칭방 때문인지 알 수 없었다 → `FacilityDeleteBlockedException`(신규)이 차단 매칭방 목록을 담아 화면 경고로 전달한다.

`693deca`(feat(facility), 2026-10-27): 시설 등록·수정·삭제 POST가 보안 matcher 누락으로 403이었다 → `/admin/facilities`, `/admin/facilities/**` 규칙과 핸들러를 추가했다.

### 운영 절 개선 계열(참고)

- `b66359c`(fix(facility), 2026-10-29): 일정 화면의 달력·시간 선택 동작과 앵커 이동 시 가려지는 문제를 고쳤다.
- `0ee7250`(fix(common), 2026-11-04): 모바일 메뉴 바깥 클릭 판정을 `contains(event.target)`에서 `event.composedPath().includes(...)`로 바꿔 그림자 DOM·재렌더 환경에서도 메뉴가 닫히게 했다.
- `1b90f19`(feat(admin), 2026-11-03): 대시보드의 최근 요청 목록을 스텁에서 담당 시설 매칭방 신청 실데이터 조회로 바꿨다.
- `c08939e`(feat(user), 2026-11-03): 탈퇴 차단 사유를 클라이언트가 아니라 서버가 판정해 내려주도록 정리했다.
- `894875e`(refactor(common), 2026-11-04): 연결되지 않은 민원·유지보수 모달과 요청 모델 참조를 정리했다.
- `bd645bf`(refactor(facility), 2026-10-29): 화면에 연결되지 않은 모집 마감 기능과 `deadline_hours` 컬럼을 제거했다.

이 절은 yongminp42 작성 커밋만 분석했으며, 다른 작성자의 fix 커밋(예: `ed62c15` 결과 제출 후 안내, `2d388b7` 클럽 티어 배치 기준 변경)은 별도 문서화 대상이다.
