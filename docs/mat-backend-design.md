# MAT 백엔드 최소 골격 설계

## 1. 분석 근거와 작업 범위

기준은 `develop`의 `87ed48b`이며, `README.md`, `CONTRIBUTING.md`, `db/schema.sql`, 기존 Java 소스 및 `build.gradle`을 확인했다.

- README는 MAT의 책임을 참가자 관리, A/B팀 배정, 경기 결과 제출, 팀장 기술평가, 종목별 MMR로 정의한다. 참가 신청·취소는 RES, 회원은 COM, 시설 운영은 FAC 영역이다.
- Java 17, Spring Boot 3.5.16, MyBatis 3.0.5, MySQL, Gradle, Lombok을 사용한다. JPA 계층이나 신규 의존성을 도입하지 않는다.
- 기존 기본 패키지는 `co.kr.spoit`이다. `web.CorePageController`는 페이지 렌더링과 빈 모델 제공을 담당하며, 주석에 향후 Controller → Service → Mapper/DB 연결 방향을 명시한다. 기본 소스에 재사용할 MAT Domain/Mapper/Service 구현은 없으므로, 기존에 확립된 매퍼 규칙이 있다고 가정하지 않는다.
- `preview/`는 선택적으로 포함하는 미리보기 소스다. 미리보기 데이터나 임시 레이팅 표시를 운영 정책으로 가져오지 않는다.
- CONTRIBUTING의 기본 흐름은 최신 develop에서 작업 브랜치를 만들고 PR로 통합하는 것이다. main/develop 직접 push와 민감정보 커밋은 금지되어 있다.

원본 `C:\workspace\spoit`에 있던 사용자 설정 변경을 보존하기 위해 `C:\Users\msnam\OneDrive\문서\ChatGPT\FinalProject\spoit-mat` worktree에서 `codex/mat-backend-skeleton` 브랜치로 작업했다. 원본은 `develop`에 그대로 있다. 커밋·push는 수행하지 않았다. RES·COM·FAC 코드, 스키마, 설정 파일은 변경하지 않았다.

이번 구현은 **경기 참가자 목록 조회와 회원·종목별 MMR 조회의 Domain / DTO / Mapper / Service 연결**까지다. HTTP 엔드포인트, 화면 연결, 쓰기 작업, 자동 팀 배정, 결과 확정, 평가 집계, 레이팅 계산·반영은 다음 단계로 남긴다.

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

## 7. 검증 결과

JDK 17과 저장소의 Gradle Wrapper 9.8.1을 사용했다.

| 검증 | 결과 |
|---|---|
| MAT 구현 전 `gradlew.bat compileJava --no-daemon --console=plain` | 기존 중복 클래스 3개로 실패 |
| MAT 구현 후 `gradlew.bat test --no-daemon --console=plain` | 동일한 중복 클래스가 컴파일을 차단하여 전체 테스트는 실행되지 않음 |
| `gradlew.bat -I build/mat-verification.init.gradle test --tests 'co.kr.spoit.mat.*' --no-daemon --console=plain` | MAT 프로덕션·테스트 소스 컴파일 성공, 단위 테스트 **13개 통과**, 실패·건너뜀 0 |
| `node --test src/test/frontend/match-selection.test.mjs` | 기존 참가 신청 동작 검사 통과 |

기본 브랜치 `87ed48b`에 다음 파일 쌍이 같은 package/class 이름을 선언하고 있다. 기존 공통 코드는 이번 작업 범위 밖이므로 수정하지 않았다.

- `common/HomeController.java`와 `global/common/HomeController.java`: `co.kr.spoit.common.HomeController`
- `config/SecurityConfig.java`와 `global/config/SecurityConfig.java`: `co.kr.spoit.config.SecurityConfig`
- `common/GlobalModelAdvice.java`와 `notification/service/GlobalModelAdvice.java`: `co.kr.spoit.common.GlobalModelAdvice`

위 경로의 기준은 `src/main/java/co/kr/spoit/`다. 기본 컴파일에서는 MAT의 Lombok 접근자를 찾지 못하는 후속 오류도 나타났지만, 중복 클래스와 분리한 MAT 컴파일 및 테스트에서는 접근자가 정상 생성됨을 확인했다. 전체 프로젝트 빌드가 성공한 것으로 해석하면 안 된다.

분리 검증용 `build/mat-verification.init.gradle`은 Git에서 무시되는 로컬 파일이며, 저장소의 `build.gradle`이나 소스를 바꾸지 않는다. 아래 내용으로 재생성할 수 있다.

```groovy
allprojects {
    afterEvaluate {
        layout.buildDirectory.set(layout.projectDirectory.dir('build/mat-verification'))
        sourceSets.main.java.setIncludes(['co/kr/spoit/mat/**'])
        sourceSets.main.resources.setSrcDirs([])
        sourceSets.test.java.setIncludes(['co/kr/spoit/mat/**'])
        sourceSets.test.resources.setSrcDirs([])
        tasks.withType(JavaCompile).configureEach {
            options.encoding = 'UTF-8'
        }
    }
}
```

서비스 테스트는 잘못된 식별자에서 DB 조회를 수행하지 않는지, 미등록 레이팅을 임의 생성하지 않는지, 참가 상태·미배정/취소 시각·소수 MMR을 그대로 전달하는지 확인한다. 테스트는 Mockito를 사용하며 로컬 DB나 비밀번호를 사용하지 않는다. MySQL SQL 실행, Spring 전체 컨텍스트의 매퍼 등록, 실제 트랜잭션 동작은 아직 통합 검증하지 않았다. 후속 통합 테스트는 공통 중복 클래스 정리와 별도 테스트 DB 준비 후 수행해야 한다.

추가 파일은 MAT 프로덕션 소스 8개, 서비스 테스트 2개, 이 설계 문서 1개다. 기존 추적 파일의 수정과 스테이징은 없다.
