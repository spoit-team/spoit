# SPO:IT — 전체 화면 fixture 렌더링 계획

> **상태**: 계획만. 코드·리소스 변경과 Git 커밋은 하지 않는다.
> **프로젝트명**: `spoit`
> **기준 브랜치**: `chore/fixture-web-base` (현재 `develop`와 동일한 기준 커밋)
> **목표**: 기존 Thymeleaf 화면 14개를 fixture 데이터로 서버에서 HTTP 200 렌더링하고, `/`를 메인 홈으로 연결한다.
> **기술**: Java 17 · Spring Boot 3.5.16 · `spring-boot-starter-web` · Spring Security · MyBatis 3.0.5

---

## 1. 완료 기준

| ID | 완료 조건 | 검증 |
| --- | --- | --- |
| G1 | DB 없이 Spring Boot가 기동한다 | `Started SpoitApplication` 로그 |
| G2 | 첫 요청 `/`가 `/matches`로 리다이렉트된다 | HTTP 302 + `Location: /matches` |
| G3 | Spring Security 기본 로그인 화면이 자동으로 나타나지 않는다 | 비로그인 `GET /` 및 `GET /matches` 확인 |
| G4 | 기존 화면 URL 14개 전부 HTTP 200 | 페이지별 MockMvc 테스트 |
| G5 | Thymeleaf 모델 누락 오류가 없다 | `TemplateProcessingException`, `EL1007E` 0건 |
| G6 | 화면에 잘못된 `null` 표시가 없다 | 응답 본문 확인 + 브라우저 점검 |
| G7 | CSS·JS·이미지가 정상 응답한다 | 대표 정적 리소스 HTTP 200 |
| G8 | 템플릿·CSS·JS·이미지를 수정하지 않는다 | `git diff --stat` 허용 범위 확인 |
| G9 | fixture 화면과 실제 기능 구현을 구분한다 | POST·DB·인증은 이번 범위 아님을 문서화 |

**14개 페이지** 외에 `/` 리다이렉트 1개를 별도 검증한다.

---

## 2. 현재 상태 기준

| 항목 | 현재 상태 |
| --- | --- |
| Spring Boot | 3.5.16 |
| Java toolchain | 17 |
| 웹 스타터 | `spring-boot-starter-web` |
| 보안 | `spring-boot-starter-security` 선언됨 |
| MyBatis | `mybatis-spring-boot-starter:3.0.5` 선언됨 |
| Thymeleaf | 의존성 없음 — 추가 필요 |
| 컨트롤러 | `SpoitApplication` 외 없음 |
| 화면 템플릿 | 14페이지 + 공통 조각 2개 |
| 루트 진입 | `static/index.html`의 안내/리다이렉트 파일 존재 |
| 기본 프로필 | 없음 (`application.properties`에는 애플리케이션명만 있음) |
| docs | `src/main/resources/static/docs/`는 현재 저장소에 없음 |

### 프로필 결정

화면만 확인하는 환경과 DB 개발 환경을 분리한다.

| 프로필 | 역할 |
| --- | --- |
| `preview` | **기본값**. fixture 데이터, DB 미사용, 비로그인 화면 열람 |
| `local` | 추후 실제 로컬 DB 연동 시 명시 활성화. `application-local.properties.example` 참고 |

`application.properties`에 `spring.profiles.default=preview`를 둔다. 실행 예: DB 없는 기본 `bootRun`; DB 연결은 추후 `--spring.profiles.active=local`로 별도 구성한다.

---

## 3. 첫 화면과 Spring Security 설계

### 요청 흐름

```text
GET /  → HomeController 302 /matches
GET /matches → MatchController → matches/list.html + fixture DTO → 200
```

Spring Security는 모든 요청에 대해 사용자 정의 `SecurityFilterChain`을 등록한다. Boot 3.5의 기본 보안 설정에 맡기면 미인증 사용자가 기본 로그인 화면으로 이동할 수 있으므로, `preview` 프로필에서 허용 범위를 명시한다.

### `preview` 전용 보안 원칙

- `@Profile("preview")` 보안 체인을 둔다.
- preview 화면 참조가 목적이므로 요청 인가는 허용한다 (`permitAll`).
- `formLogin`과 `httpBasic`을 켜지 않아 Spring Security 생성 로그인 화면을 띄우지 않는다.
- CSRF는 기본 활성 상태를 유지한다. 기존 POST 템플릿에는 `_csrf != null`일 때 토큰을 출력하는 바인딩이 있다.
- preview는 로컬 화면 확인 전용이다. 운영에 이 허용 체인을 적용하지 않는다.
- `GET /login`은 `/login` URL이 아니라 `auth/login.html`을 반환하는 일반 화면이다. 실제 인증은 이번 범위에서 처리하지 않는다.

> 작업 구현 시 Spring Boot 3.5.16/Spring Security에 맞는 DSL과 자동설정 동작을 확인한다. Boot 4 전용 패키지의 클래스명이나 설정 예시를 복사하지 않는다.

### 데이터 자동설정

`application-preview.properties`에서 DB/DataSource와 MyBatis 자동설정을 끈다. Security 자동설정을 통째로 제외하지 말고 사용자 정의 체인으로 동작을 고정한다. 기본 생성 사용자 자동설정이 임의 비밀번호를 출력하는지 확인하고, preview 프로필에서만 적절히 억제한다.

---

## 4. 구현 단계

### 단계 A — 기동 기반

| 작업 | 계획 산출물 |
| --- | --- |
| Thymeleaf 의존성 추가 | `build.gradle` |
| 기본 preview 프로필 설정 | `application.properties` |
| DB/MyBatis 제외 및 Thymeleaf cache 설정 | `application-preview.properties` (신규) |
| preview `SecurityFilterChain` | `config/PreviewSecurityConfig.java` (신규) |
| 홈 리다이렉트 | `common/HomeController.java` (신규) |
| 공통 헤더 모델 | `common/GlobalModelAdvice.java` (신규) |
| 정적 진입 파일 제거 | `static/index.html` 삭제 (계획 범위) |

단계 A에서 `/` 리다이렉트와 정적 리소스가 확인되어도 `/matches`는 컨트롤러가 없으므로 아직 404가 정상이다.

### 단계 B — typed DTO 규칙 확정

**B안**을 사용한다. 화면 DTO와 중첩 DTO를 Java 타입으로 선언하고 Lombok `@Getter`로 Thymeleaf SpEL 프로퍼티 접근을 지원한다. 각 화면 DTO는 템플릿의 실제 속성 구조에 맞춘다.

공통 원칙:

1. DTO는 `record` 대신 getter가 있는 클래스로 작성한다.
2. 식별자는 `Long`/`Integer` 등 박싱 타입을 사용한다.
3. 템플릿이 반복하는 컬렉션은 항상 빈 목록 또는 샘플 목록으로 제공하고 null로 두지 않는다.
4. nested property는 템플릿에서 사용하는 모든 중간 객체까지 채운다.
5. `photoUrl`, `sportImageUrl`, calendar/filter의 URL처럼 링크·src에 사용되는 값은 null이 아닌 유효한 앱 내부 경로를 준다.
6. `th:selected` 비교 양쪽 ID 타입을 일치시킨다. 전체 선택은 화면 계약에 따라 `null`로 표현한다.
7. 날짜 선택은 `date` 요청값과 `search.dateIdx` 간 매핑을 컨트롤러/Assembler에서 명시한다.
8. 모든 화면 컨트롤러는 `@Controller`로 뷰 이름을 반환한다.
9. admin 화면의 숨겨진 패널도 서버에서 표현식이 실행될 수 있으므로 필요한 모델을 준비한다.

모델 수는 템플릿 접근 경로 수를 기준으로 한다. `matches/list`와 `matches/cards`는 접근 경로가 동일(각 69개)이므로 같은 DTO 계약을 공유한다.

### 단계 C — 화면 작업 단위 14개

4인 **고정 레인으로 묶지 않고**, 아래 작업을 각각 분리한다. 각자는 자기 작업 단위를 맡으며, 사람별 업무가 다르면 요구사항 ID 기준으로 적합한 항목을 골라 배정한다. 일정은 이번 계획에서 정하지 않는다.

| ID | URL | 뷰 | 모델 경로 수 | 난이도 | 관련 요구사항 |
| --- | --- | --- | ---: | --- | --- |
| W01 | `/matches` | `matches/list` | 69 | 상 | RES-01, RES-02 |
| W02 | `/matches/cards` | `matches/cards` | 69 | 중 | RES-01, RES-02 |
| W03 | `/matches/{matchId}` | `matches/detail` | 74 | 상 | RES-02, RES-03, RES-04 |
| W04 | `/matches/{matchId}/apply` | `matches/join` | 36 | 중 | RES-03, RES-04 |
| W05 | `/login` | `auth/login` | 0 nested paths | 하 | COM-02 |
| W06 | `/signup` | `auth/signup` | 11 | 하 | COM-01 |
| W07 | `/mypage` | `mypage/index` | 64 | 상 | COM-03~05, RES-05~06, MAT-02 표시 |
| W08 | `/mypage/profile` | `mypage/profile` | 14 | 하 | COM-03~04 |
| W09 | `/teams` | `teams/list` | 34 | 중 | RES-07~09 |
| W10 | `/teams/{clubId}` | `teams/detail` | 29 | 중 | RES-07~08 |
| W11 | `/teams/manage` | `teams/manage` | 57 | 상 | RES-08~09 |
| W12 | `/admin/facility` | `admin/facility` | **90** | 최상 | FAC-01~07 |
| W13 | `/admin/operations?matchId=1` | `admin/operations` | 41 | 중 | MAT-01 |
| W14 | `/admin/system` | `admin/system` | **105** | 최상 | ADM-01~03 |

`GET /`는 위 14개 페이지가 아니라 별도 리다이렉트 테스트다.

#### 작업 단위 완료 산출물

```text
1. 해당 GET 컨트롤러/뷰 매핑
2. 페이지 전용 typed PageView DTO 및 nested DTO
3. @Profile("preview") fixture 1~3건 (빈 목록 상태가 있으면 그 상태도 제공)
4. 해당 URL MockMvc 200 테스트
5. 템플릿 변경 없음 확인
```

W02는 W01의 매치 샘플과 별도 DTO를 써도 되며, 어떤 타입을 공유할지 소유자가 미리 정해 다른 작업자의 파일을 수정하지 않게 한다. W01/W02는 같은 모델 계약을 제공해야 한다.

### 단계 D — 통합

- 14개 작업 각각 별도 스모크 테스트 파일 또는 충돌 없이 분리된 테스트 클래스를 둔다.
- 전체 통합 테스트에서 14개 URL이 모두 200인지 확인한다.
- `/` 302 → `/matches`, `/matches` 200을 연속으로 확인한다.
- CSS·JS·이미지의 대표 URL도 200인지 확인한다.

---

## 5. 요구사항 명세서와 현재 화면 연결

표시 상태와 실제 기능 구현 상태를 구분한다. 이 단계의 완료는 **fixture 화면 렌더링 완료**이지 기능 구현 완료가 아니다.

| 그룹 | 요구사항 | 화면/작업 | 이번 단계의 상태 |
| --- | --- | --- | --- |
| COM | COM-01~05 | W05~W08 | 화면 렌더링. 인증·가입·저장·알림 처리 미구현 |
| RES | RES-01~06 | W01~W04, W07 | 화면 렌더링. 검색 데이터 조회·신청·취소·정원 변경 미구현 |
| RES | RES-07~09 | W09~W11 | 화면 렌더링. 팀 생성·승인·권한 처리 미구현 |
| MAT | MAT-01 | W13 | 운영 화면 렌더링. 실시간 관리 미구현 |
| MAT | MAT-02 | W03/W07 표시 영역 | 표시용 fixture만. 레이팅 산정 미구현 |
| MAT | MAT-03 | — | **미구현 (화면 없음)**. 이번 단계에서 화면 추가 금지 |
| MAT | MAT-04 | — | **미구현 (화면 없음)**. 이번 단계에서 화면 추가 금지 |
| MAT | MAT-05 | 기존 팀 평균 표시 | 화면 표시 계약만. 계산 미구현 |
| FAC | FAC-01~07 | W12 | 화면 렌더링. 저장·운영·통계 기능 미구현 |
| FAC | FAC-02-01 | — | 지도 API는 낮은 우선순위, 이번 범위 제외 |
| ADM | ADM-01~03 | W14 | 화면 렌더링. 사용자·신고·페널티 조치 미구현 |

---

## 6. 보안 및 폼 처리의 명확한 경계

- Preview에서는 Spring Security 필터 체인은 존재하지만 접근은 허용하고, form login/basic auth는 사용하지 않는다. 그래서 첫 방문이 생성 로그인 화면으로 바뀌지 않는다.
- CSRF는 활성 상태를 유지한다. 기존 POST 폼의 hidden 토큰 binding을 유지한다.
- `GET /login`은 사용자 화면을 렌더링한다. 실제 `POST /login` 인증 처리는 이번 단계에 없다.
- 다른 POST 폼도 화면 요소만 제공한다. 신청·회원가입·팀 관리·관리자 저장이 동작한다고 표시하지 않는다.
- 모든 화면을 허용하는 preview 보안 설정은 로컬/화면 확인 전용이며 운영 프로필에 재사용하지 않는다.

---

## 7. 검증 계획

| 게이트 | 검증 | 통과 기준 |
| --- | --- | --- |
| V0 | `gradlew dependencies --configuration compileClasspath` | Thymeleaf 3.5.16 의존성 해석 성공 |
| V1 | `gradlew bootRun` | DB 연결 없이 기동 성공 |
| V2 | `GET /` | 302, `Location: /matches` |
| V3 | 비로그인 GET | 기본 로그인 페이지 대신 애플리케이션 응답 |
| V4 | W01~W14 GET | 14 URL 모두 200 |
| V5 | 서버 로그 | `TemplateProcessingException`, `EL1007E` 없음 |
| V6 | 응답 HTML/브라우저 | `null` 표시·빈 필수 모델 없음 |
| V7 | 정적 리소스 | CSS·JS·이미지 대표 경로 200 |
| V8 | `gradlew test` | 전체 테스트 성공 |
| V9 | `npx.cmd --yes bun src/test/frontend/check-contracts.mjs` | PASS, 템플릿 구조 그대로 |
| V10 | 금지 경로 diff 검사 | `templates/**`, `static/css/**`, `static/js/**`, `static/images/**`, `db/**` 수정 0건 |

현재 `static/docs/`는 없으므로 `docs-contracts.test.mjs`는 이 계획의 검증 항목이 아니다.

### 반드시 확인할 화면 품질

HTTP 200만으로 합격 처리하지 않는다. 템플릿이 optional chaining으로 null을 허용하는 관리자 화면에서는 모델 누락이 에러가 아닌 빈 표시로 끝날 수 있다. 응답 본문과 브라우저에서 카드·표·패널에 fixture 내용이 렌더링됐는지 확인한다.

---

## 8. 범위 잠금 및 작업/커밋 규칙

### 절대 수정하지 않는 경로

```text
src/main/resources/templates/**
src/main/resources/static/css/**
src/main/resources/static/js/**
src/main/resources/static/images/**
db/**
README.md
CONTRIBUTING.md
.github/**
```

모델이 맞지 않으면 DTO를 맞춘다. 템플릿을 변경해야 할 것 같으면 작업을 멈추고 사용자에게 보고한다.

### 브랜치 및 커밋

- 기준 브랜치는 현재의 `chore/fixture-web-base`를 사용한다.
- 개별 작업은 `feature/{요구사항ID}-{짧은-설명}` 형식으로 분리할 수 있다. 요구사항 ID가 없는 기반 작업은 `chore/{짧은-설명}`을 사용한다.
- 작업 단위마다 변경 파일 소유권을 명시한다. 다른 작업 단위 DTO·fixture 파일을 동시에 수정하지 않는다.
- **명시적인 사용자 승인 전에는 커밋하지 않는다.** 현재 계획 실행 요청만으로 커밋을 자동 수행하지 않는다.

---

## 9. 이번 계획의 최종 상태

```text
DB 없이 기동
GET / → /matches
Security 생성 로그인 화면 없음
14개 기존 템플릿 전부 fixture로 200 렌더링
기존 templates/CSS/JS/images/DB 파일 무수정
실제 인증·저장·권한·예약 기능은 별도 후속 작업
MAT-03/04는 미구현 상태 유지
```

**코드 실행은 계획 승인과 실제 작업 요청 이후에만 진행한다.**
