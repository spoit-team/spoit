# MAT 백엔드 설계와 MySQL Mapper 통합 테스트

## 1. 분석 근거와 작업 범위

현재 기준은 MAT 최소 골격이 병합된 PR #17의 `1b205c1`이다. `README.md`, `CONTRIBUTING.md`, `db/schema.sql`, 기존 Java 소스와 빌드·CI 설정을 함께 확인했다.

- README는 MAT의 책임을 참가자 관리, A/B팀 배정, 경기 결과 제출, 팀장 기술평가, 종목별 MMR로 정의한다. 참가 신청·취소는 RES, 회원은 COM, 시설 운영은 FAC 영역이다.
- Java 17, Spring Boot 3.5.16, MyBatis 3.0.5, MySQL, Gradle, Lombok을 사용한다. JPA 계층이나 신규 테스트 의존성은 추가하지 않는다.
- 기본 패키지는 `co.kr.spoit`이다. PR #17의 `mat.domain`, `mat.dto`, `mat.mapper`, `mat.service` 구조와 기존 조회 계약을 그대로 검증한다.
- `web.CorePageController`는 페이지 렌더링과 빈 모델 제공을 담당한다. `preview/`의 미리보기 데이터나 임시 레이팅 표시는 운영 정책으로 사용하지 않는다.
- 이전에 전체 컴파일을 막았던 공통 중복 클래스는 정리되었다. 현재 `HomeController`, `SecurityConfig`, `GlobalModelAdvice`의 패키지는 각각 `global.common`, `global.config`, `notification.service`와 일치한다. MAT 소스만 분리하는 임시 Gradle 스크립트 없이 기본 전체 소스를 컴파일하는 상태다.

이번 작업은 기존 체크아웃의 `feature/mat-mapper-integration-test` 브랜치에서 수행한다. 별도 worktree 생성, 커밋·push·merge·PR 생성은 수행하지 않았다. RES·COM·FAC 및 MAT 프로덕션 코드, `build.gradle`, `db/schema.sql`, 애플리케이션 설정, 기존 테스트는 변경하지 않는다.

기존 최소 골격은 **경기 참가자 목록 조회와 회원·종목별 MMR 조회의 Domain / DTO / Mapper / Service 연결**이다. 이번에는 실제 MySQL에서 두 Mapper의 SQL·필드 매핑·복합 키 조건을 검증하는 테스트와 실행 환경을 추가한다. HTTP 엔드포인트, 화면 연결, 쓰기 작업, 자동 팀 배정, 결과 확정, 평가 집계, 레이팅 계산·반영은 범위에 포함하지 않는다.

## 2. 테이블과 담당 경계

| 테이블 | 역할과 관계 | 이번 구현 |
|---|---|---|
| `social_match` | 경기 중심. `(facility_id, sport_id)`로 FAC의 `facility_sport`를 참조하고, 작성·배정·검토 회원을 참조한다. | 관계 분석만 수행 |
| `match_application` | 경기별 개인·동호회 신청. 경기 1:N 신청이며 회원·동호회를 참조한다. RES 소유 흐름과 연결된다. | 기존 신청 구조를 유지 |
| `match_participant` | 신청 1:N 참가자. 경기·회원·참가 상태·A/B 배정·신청 당시 MMR을 보관한다. | 경기별 전체 참가자 조회 |
| `match_result_submission` | 같은 경기 참가자의 승무패 제출 이력. | 향후 결과 제출 기능의 확장 지점 |
| `sport_evaluation_item` | 종목별 평가 문항·가중치·활성 상태. | 향후 평가 기능의 확장 지점 |
| `match_evaluation` | 같은 경기의 평가자와 대상 참가자 사이 평가. | 향후 평가 기능의 확장 지점 |
| `match_evaluation_score` | 평가×항목의 점수와 관찰 여부. | 향후 평가 기능의 확장 지점 |
| `sport_rating` | `(user_id, sport_id)`를 키로 하는 현재 MMR·반영 경기 수. | 회원·종목별 단건 조회 |
| `sport_rating_history` | 경기 참가자별 이전/이후 MMR, 증감, 기대값, 공식 버전의 반영 이력. | 향후 반영 기능의 확장 지점 |

주요 관계는 `social_match → match_application → match_participant`다. `social_match`의 팀장 참조는 다시 참가자로 향하므로 순환 참조가 있으며, DDL은 참가자 테이블 생성 후 복합 FK를 추가한다. 평가의 항목별 점수는 평가·문항 양쪽을 복합 FK로 참조한다. 현재 MMR과 반영 이력은 별도 테이블이므로, 이후 갱신 단계에서는 두 테이블 및 경기 처리 상태의 일관성을 함께 관리해야 한다.

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

다음은 DDL만으로 완전히 보장되지 않는다. 현재 읽기 전용 골격이 이 규칙을 구현하거나 검증한다고 간주하면 안 된다.

| 확인 사항 | DDL의 한계와 필요한 처리 |
|---|---|
| 팀장 자격 | 같은 경기 참가자라는 사실만 보장한다. A팀장은 A 소속, B팀장은 B 소속인지와 확정 참가 상태를 확인해야 한다. |
| 결과 제출·평가 권한 | FK만으로 실제 지정 팀장인지 알 수 없다. 요청 주체와 참가자·팀장 관계를 확인해야 한다. |
| 경기 종목 일치 | `match_evaluation.sport_id`와 `social_match.sport_id`의 일치를 보장하는 복합 FK는 없다. |
| 이력의 회원·종목 일치 | 이력의 회원이 참가자의 회원인지, 이력의 종목이 경기 종목인지 별도 확인해야 한다. |
| 실제 정원과 상태 | 실제 A/B 인원, 신청과 참가 상태, 상태 전이, 처리 시각의 정합성을 확인해야 한다. 일반 상태 컬럼은 다수가 주석만 있으며 CHECK가 없다. |
| 제출·평가 중복 | 경기별 팀장 결과 제출, 평가자별 대상 평가에 업무 중복 방지 UNIQUE가 없다. 재제출·수정 규칙부터 확정해야 한다. |
| 관찰 점수 NULL | `OBSERVED`와 `score=NULL`이면 점수 CHECK가 SQL의 UNKNOWN으로 평가되어 통과할 수 있다. 쓰기 서비스에서 `OBSERVED → score != null && 0 <= score <= 10`, `NOT_OBSERVED → score == null`을 명시적으로 확인해야 한다. 0점은 정상 점수다. |
| 평가 집계 | 활성 항목 적용, 필수 항목 완성, 가중치 합계, 종합 점수 산출·결측 처리 규칙은 별도 결정이 필요하다. |
| 반영 원자성과 동시성 | 현재 MMR·반영 수·이력·경기 레이팅 상태를 함께 갱신할 트랜잭션, 잠금·충돌 처리, 재시도 방식을 설계해야 한다. 참가자별 이력 UNIQUE만으로 전체 정합성은 보장되지 않는다. |

스키마 개선이 필요한 경우에는 담당자와 별도 협의하고 CONTRIBUTING에 따라 ERD와 SQL을 함께 변경한다. 이번 작업에서는 제약을 임의로 추가하지 않는다.

## 5. 패키지 및 최소 구현 계약

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
| `match_participant.entry_mmr DEFAULT 1000` | 신청 시 MMR 스냅샷 컬럼의 DB 기본값이다. 실제 스냅샷 생성 흐름은 RES와의 연결 시 결정한다. |
| `rated_match_count DEFAULT 0` | 반영 경기 수의 초기 저장값이다. 배치 경기 수나 티어 기준을 뜻하지 않는다. |
| `evaluation_delta DEFAULT 0` | 이력의 평가 보정 컬럼 기본값이다. 평가 보정을 영구적으로 사용하지 않는다는 정책은 아니다. |
| 평가 `scale_max DEFAULT 10` 및 CHECK | 10점 척도는 DDL이 명시적으로 보장한다. 평가 가중치와 결측 집계 공식을 확정하지는 않는다. |
| `assignment_method DEFAULT MANUAL`, `rating_status DEFAULT WAITING` | 초기 저장 상태다. 자동 배정 방식이나 상태 전이 조건을 정하지 않는다. |

Elo K/D, 배치 경기 수, 평가 보정식과 계수, 팀 평균 산정 대상·시점, 반올림, MMR 상·하한, 티어, 결측 평가, 재반영·정정 정책은 확정하지 않았다. `algorithm_version` 컬럼이 있다는 이유만으로 버전값이나 공식을 생성하지 않는다. 별도 Python 실험의 가정·수치를 운영 정책으로 사용하지 않는다.

## 7. MySQL 통합 테스트 구성

### 검증 범위

`AbstractMySqlMapperTest`는 `@MybatisTest`로 실제 프로덕션 Mapper를 등록하고 `@AutoConfigureTestDatabase(replace = NONE)`으로 실제 MySQL을 사용한다. Mock Mapper나 H2로 대체하지 않는다. underscore-to-camel-case 설정을 끈 상태에서 SQL의 명시적 별칭이 Java 필드에 매핑되는지 확인한다.

| 테스트 | 검증 내용 |
|---|---|
| `MatchParticipantMapperIntegrationTest` 3개 | 참가자 ID 오름차순, 다른 경기 배제, 전체 9개 필드, 대기·확정·취소 상태, 미배정·취소 시각의 NULL, 빈 목록, DECIMAL 소수 두 자리 |
| `SportRatingMapperIntegrationTest` 3개 | 회원·종목 두 키 조건, 전체 5개 필드, 미존재 조합의 빈 Optional, 조회 전후 행 수 불변, DECIMAL 양수·음수 저장 한계와 소수 두 자리 |
| `MySqlTestConnectionValidationTest` 24개 | 허용 주소 4개와 거부 주소 20개. 개발 DB명·원격 주소·쿼리·사용자 정보·다중 호스트·잘못된 포트 등을 연결 전에 거부 |
| 기존 서비스 테스트 13개 | 잘못된 식별자, 저장값 보존, 미등록 레이팅 비합성 |
| 기존 Spring 애플리케이션·화면 테스트 16개 | 전체 컨텍스트와 기존 화면 경로 회귀 검증 |

전체 기본 테스트 실행 결과는 59개 통과다. DECIMAL 경계 데이터는 MySQL 저장 타입과 Java `BigDecimal` 매핑을 확인하기 위한 값이며, 허용 MMR의 상·하한 정책을 정하지 않는다.

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

이 검증은 두 조회 Mapper의 실제 SQL 실행·매핑 및 테스트 데이터 롤백을 대상으로 한다. 미구현 상태 전이, 동시성 제어, 쓰기 서비스, 레이팅 계산·반영의 정합성까지 검증하는 것은 아니다.

## 8. 이번 변경 파일과 이유

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
| `docs/mat-mapper-integration-test-pr.md` | 팀 템플릿에 따른 PR 초안 |

프로덕션 코드와 스키마를 바꾸지 않고 저장소의 기존 의존성을 사용한다. 미확정 레이팅 정책은 추가하지 않는다.
