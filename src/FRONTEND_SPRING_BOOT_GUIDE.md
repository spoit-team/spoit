# SPO:IT 프런트엔드 Spring Boot 적용 가이드

> 문서 홈페이지: [HTML 버전 열기](main/resources/static/docs/index.html). 이 파일은 기본 적용 절차의 텍스트 참고본입니다. **기능별 SQL 분석·화면 계약·처리 흐름의 상세 내용은 HTML 문서를 기준으로 확인하세요.**

이 문서는 **현재 저장소의 프런트엔드를 Spring Boot MVC + Thymeleaf + MyBatis에 연결하는 방법**을 설명합니다.

프런트엔드는 이미 표준 리소스 위치에 있습니다. HTML을 다시 옮기거나 React/Vue 프로젝트로 바꾸는 작업은 필요하지 않습니다. 대신 **템플릿 엔진, 컨트롤러, 화면 모델, 인증, DB 조회·저장 로직**을 연결해야 합니다.

> 이 문서의 Java·Gradle·설정 코드는 적용 방법을 보여 주는 예시입니다. 해당 기능을 실제 소스에 추가한 상태가 아니며, 예시 전체를 실행 검증한 것도 아닙니다. 파일별 상세 모델·폼 계약은 [README.md](README.md)를 기준으로 합니다.

## 목차

1. [현재 상태와 연결 순서](#1-현재-상태와-연결-순서)
2. [의존성과 DB 설정](#2-의존성과-db-설정)
3. [폴더와 URL 연결](#3-폴더와-url-연결)
4. [컨트롤러와 화면 모델](#4-컨트롤러와-화면-모델)
5. [SQL·Java·폼 명칭 규칙](#5-sqljava폼-명칭-규칙)
6. [POST 폼과 참가 신청 흐름](#6-post-폼과-참가-신청-흐름)
7. [로그인·권한·CSRF](#7-로그인권한csrf)
8. [팀·마이페이지·관리자 연결](#8-팀마이페이지관리자-연결)
9. [실행과 검증](#9-실행과-검증)
10. [자주 발생하는 문제](#10-자주-발생하는-문제)
11. [운영 전 확인사항](#11-운영-전-확인사항)

## 1. 현재 상태와 연결 순서

### 현재 있는 것

| 항목 | 위치·상태 |
| --- | --- |
| Spring Boot 시작 클래스 | `src/main/java/co/kr/spoit/SpoitApplication.java` |
| 화면 14개·공통 조각 2개 | `src/main/resources/templates/` |
| CSS·JavaScript·이미지 | `src/main/resources/static/` |
| 프런트엔드 모델·폼 계약 | `src/README.md` |
| MySQL 8 DDL | 루트의 `db/schema.sql` |
| 기본 설정 | `application.properties`에는 현재 애플리케이션 이름만 있음 |
| 빌드 구성 | Spring Boot `4.0.8`, Java `17`, Security·Web MVC·MyBatis·MySQL 드라이버 선언 |

### 아직 연결해야 하는 것

- Thymeleaf 의존성: 현재 `build.gradle`에 없습니다.
- DB 접속 정보와 스키마 준비.
- URL별 `@Controller`, 요청 DTO, 조회·저장 서비스, MyBatis Mapper.
- DB 기반 로그인·회원가입, 역할·시설·팀 소유권 검증.
- 템플릿이 읽는 모델과 서버 처리 결과·오류 메시지.

### 권장 순서

```text
의존성·DB 연결
  → /matches 목록 렌더링
  → /matches/{matchId} 상세 렌더링
  → 회원가입·로그인·공통 회원 모델
  → 참가 선택·신청·마이페이지
  → 팀 기능
  → 시설 관리자·매치 운영·시스템 관리자
  → 보안·회귀·운영 점검
```

한 번에 모든 화면을 연결하지 말고, **목록 한 화면을 실제 DB 조회까지 연결한 뒤** 같은 구조를 확장하는 편이 쉽습니다.

## 2. 의존성과 DB 설정

### 2.1 Gradle 의존성

기존 루트 `build.gradle`의 `dependencies`에 다음 의존성을 추가합니다.

```groovy
implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
implementation 'org.springframework.boot:spring-boot-starter-validation'
```

- Thymeleaf는 HTML의 `th:*` 속성을 처리하는 데 필요합니다.
- Validation은 요청 DTO의 `@NotBlank`, `@NotNull`, `@Size`, `@Min` 등을 사용할 때 필요합니다.
- Web MVC·Security·MyBatis·MySQL 드라이버는 이미 선언돼 있으므로 중복 추가하지 않습니다.
- Spring Boot가 관리하는 라이브러리 버전은 임의로 개별 지정하지 않습니다. Boot 4에 맞는 의존성을 사용합니다.
- 기존 Spring Boot 버전의 실제 다운로드 가능 여부와 저장소 접근은 Gradle로 확인해야 합니다. 이 가이드는 버전을 변경하지 않습니다.

**루트 빌드 파일 수정은 이후 백엔드 연결 작업에 해당합니다. 문서를 추가하는 이번 작업에서는 변경하지 않습니다.**

### 2.2 로컬 설정 예시

`src/main/resources/application-local.properties` 같은 로컬 프로필 파일에 설정합니다.

```properties
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}

mybatis.configuration.map-underscore-to-camel-case=true
mybatis.mapper-locations=classpath:/mappers/**/*.xml

# 개발 중 HTML 수정 내용을 바로 확인하기 위한 설정
spring.thymeleaf.cache=false
```

`DB_URL`에는 `jdbc:mysql://localhost:3306/spoit`처럼 실제로 사용할 JDBC URL을 넣습니다. DB 이름·계정·권한·접속 옵션은 개발 환경에 맞춰 설정합니다. 비밀번호는 Git에 커밋하지 않습니다.

- `mybatis.mapper-locations`는 **XML Mapper를 사용할 때의 예시 경로**입니다. 해당 XML을 실제로 작성해야 합니다. 애너테이션 Mapper만 사용한다면 생략할 수 있습니다.
- Mapper 인터페이스에 `@Mapper`를 붙이거나 설정 클래스에 `@MapperScan`을 적용합니다.
- 스키마 파일은 `src/main/resources/schema.sql`이 아니라 루트의 `db/schema.sql`입니다. 서버 실행만으로 자동 적용된다고 가정하지 않습니다.
- **이 SQL에는 `DROP TABLE IF EXISTS`가 있습니다. 기존 데이터가 있는 DB에 그대로 실행하지 마세요.** 비어 있는 개발용 DB에 검토 후 적용하거나, 운영용 마이그레이션을 별도로 작성합니다.
- 종목·지역 등 기준 데이터도 준비해야 합니다. DDL만 적용하면 목록 선택지에 필요한 데이터가 생기는 것은 아닙니다.

## 3. 폴더와 URL 연결

권장 구조는 다음과 같습니다. 아래 Java 패키지·클래스명은 새로 만들 때의 제안이며 현재 구현된 파일 목록이 아닙니다.

```text
src/main/java/co/kr/spoit/
├─ SpoitApplication.java
├─ config/                 # Security·MyBatis 등 설정
├─ common/                 # 공통 화면 모델·예외 처리
├─ match/                  # 매치 Controller·Service·Mapper·DTO
├─ member/                 # 회원가입·로그인·프로필
├─ club/                   # 팀·팀원·가입 신청
├─ facility/               # 시설·운영 일정
└─ admin/                  # 운영·시스템 관리

src/main/resources/
├─ application.properties
├─ application-local.properties
├─ mappers/                # XML Mapper를 쓰는 경우 새로 작성
├─ templates/              # 기존 HTML 유지
└─ static/                 # 기존 CSS·JS·이미지 유지
```

시작 클래스가 `co.kr.spoit`에 있으므로 구성 요소를 그 하위 패키지에 두면 기본 컴포넌트 스캔 범위에 들어갑니다.

### 컨트롤러가 반환할 뷰 이름

| 브라우저 요청 | 반환할 뷰 이름 |
| --- | --- |
| `GET /matches` | `matches/list` |
| `GET /matches/cards` | `matches/cards` |
| `GET /matches/{matchId}` | `matches/detail` |
| `GET /matches/{matchId}/apply` | `matches/join` |
| `GET /login` | `auth/login` |
| `GET /signup` | `auth/signup` |
| `GET /mypage` | `mypage/index` |
| `GET /mypage/profile` | `mypage/profile` |
| `GET /teams` | `teams/list` |
| `GET /teams/{clubId}` | `teams/detail` |
| `GET /teams/manage` | `teams/manage` |
| `GET /admin/facility` | `admin/facility` |
| `GET /admin/operations?matchId=...` | `admin/operations` |
| `GET /admin/system` | `admin/system` |

주의사항:

- HTML 화면은 `@RestController`가 아니라 **`@Controller`**에서 반환합니다.
- `return "matches/list";`처럼 반환합니다. `templates/` 접두사나 `.html` 확장자는 붙이지 않습니다.
- `fragments/layout.html`, `fragments/admin.html`은 공통 조각이며 별도 페이지 URL이 필요하지 않습니다.
- CSS URL은 `/css/styles.css`입니다. `/static/css/styles.css`가 아닙니다.
- HTML을 더블클릭하거나 `/templates/matches/list.html`로 접속해도 Thymeleaf 렌더링을 확인할 수 없습니다.
- 같은 서버가 HTML과 폼을 처리하므로 기본 적용에 별도 프런트 서버·REST API·CORS 설정은 필요하지 않습니다.

루트 진입 컨트롤러는 다음처럼 작성할 수 있습니다.

```java
package co.kr.spoit.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    @GetMapping("/")
    public String home() {
        return "redirect:/matches";
    }
}
```

기존 `static/index.html`은 진입 안내용입니다. 실제 서비스의 `/` 동작은 위처럼 서버에서 정합니다.

## 4. 컨트롤러와 화면 모델

### 4.1 DB 객체를 그대로 넘기지 않기

다음 흐름으로 책임을 나눕니다.

```text
Controller: 요청값 수신·검증 결과 처리·뷰 선택
  → Service: 권한·업무 규칙·트랜잭션·화면 데이터 구성
  → Mapper: SQL 조회·저장
  → 화면 DTO: 템플릿이 읽을 속성과 표시값 제공
```

DB 행에는 `matchId`, `startAt`, `feePerPerson` 등이 있지만 화면은 `dateLabel`, `feeText`, `statusLabel`, `canApply`도 읽습니다. 컬럼명만 맞춰도 화면이 완성되는 것은 아닙니다.

- DTO는 record 또는 getter가 있는 Java 클래스로 만들 수 있습니다.
- 숫자 ID는 실제 엔티티·저장 DTO에서 `Long`, 금액은 `BigDecimal`, 날짜·시각은 용도에 맞게 `LocalDate`, `LocalTime`, `LocalDateTime` 등을 사용합니다.
- 표시값은 서버에서 일관되게 포맷합니다. 예를 들어 `feeText`는 화면에서 `원`을 붙이므로 `12,000` 형태로 제공합니다.
- 빈 컬렉션은 `List.of()`처럼 빈 목록으로 제공합니다. 화면이 접근하는 중첩 객체의 필수 속성도 채웁니다.
- 익명 사용자의 `currentMember`는 `null`, `unreadCount`는 `0`으로 제공합니다. 실제 회원 객체 대신 임시 로그인 데이터를 넣지 않습니다.

### 4.2 매치 목록의 필수 모델

`matches/list`와 `matches/cards`는 같은 계약을 사용합니다.

| 모델명 | 구성·주의사항 |
| --- | --- |
| `search` | `sportId`, `regionId`, `dateIdx`, `hours` |
| `sports` | `sportId`, `name`, `count`; 전체 선택 항목 포함 |
| `districts` | `regionId`, `name`; 전체 지역 선택 항목 포함 |
| `dates` | `idx`, `label` |
| `hourOptions` | `09:00`처럼 정해진 형식의 시간 문자열 목록 |
| `selectedHours`, `selectedHoursLabel` | 선택 시간과 표시 문구 |
| `selectedSportName`, `sportImageUrl` | 종목 표시·배경; 이미지가 없으면 `null` 가능 |
| `activeFilters` | `label`, 조건 해제용 `url` |
| `matches` | 매치 카드 DTO 목록; 상세 필드는 README 참고 |
| `roomCount`, `totalRoomCount`, `recruitingCount`, `matchedCount` | 기존 표시용 집계 이름 유지 |
| `currentMember`, `unreadCount` | 공통 헤더에서 사용 |

**요청 파라미터 `date`와 모델 속성 `search.dateIdx`는 이름이 다릅니다.** 현재 화면의 날짜 선택값 `dates[].idx`를 서버가 실제 날짜로 해석한 뒤, 선택값을 `search.dateIdx`로 돌려줍니다. DTO에 `dateIdx`만 선언하고 `date`가 자동 바인딩될 것으로 기대하지 마세요.

### 4.3 컨트롤러 연결 형태

다음은 **서비스를 구현한 뒤 사용할 구조 예시**입니다. `MatchPageService`는 현재 저장소에 없으며 직접 작성해야 합니다. `listModel(...)`은 위 표의 화면 모델 전체를 반환하는 메서드로 가정합니다. 반환값에는 검증되지 않은 요청값이나 비밀번호를 넣지 않습니다.

```java
package co.kr.spoit.match;

import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class MatchController {
    private final MatchPageService matchPageService;

    public MatchController(MatchPageService matchPageService) {
        this.matchPageService = matchPageService;
    }

    @GetMapping({"/matches", "/matches/cards"})
    public String list(
            @RequestParam(name = "sportId", defaultValue = "all") String sportId,
            @RequestParam(name = "regionId", defaultValue = "all") String regionId,
            @RequestParam(name = "date", required = false) String date,
            @RequestParam(name = "hours", required = false) List<String> hours,
            jakarta.servlet.http.HttpServletRequest request,
            Model model) {
        model.addAllAttributes(matchPageService.listModel(
                sportId, regionId, date, hours == null ? List.of() : hours));
        return request.getServletPath().equals("/matches/cards")
                ? "matches/cards" : "matches/list";
    }
}
```

이 예시는 목록 표시의 골격입니다. 공통 로그인 정보는 `@ControllerAdvice`의 `@ModelAttribute` 등으로 인증 정보에서 제공하고, 화면 서비스가 잘못 덮어쓰지 않도록 합니다.

구현 시 주의사항:

- 검색에는 `all`·빈 문자열이 들어오므로 검색 입구에서는 문자열로 받고 검증 후 수치로 변환합니다.
- `sportId`·`regionId` 타입은 선택지 DTO와 `search`에서 맞춥니다. 문자열 `"1"`과 수치 `1L` 비교에 의존하지 않습니다.
- `hours`는 반복 파라미터입니다. 단일 문자열로 받거나 첫 항목만 검색하지 않습니다.
- 시간·정렬·ID를 검증하고 SQL은 MyBatis `#{...}`로 바인딩합니다. 요청값을 `${...}`에 직접 넣지 않습니다.
- 관리자는 한 템플릿에 여러 패널이 있습니다. `hidden`은 서버 평가를 멈추지 않으므로 비표시 패널이 참조하는 모델도 준비합니다.

## 5. SQL·Java·폼 명칭 규칙

SQL은 snake_case, Java·Thymeleaf·폼은 camelCase를 사용합니다.

| SQL | Java·화면 |
| --- | --- |
| `social_match.match_id` | `matchId` |
| `social_match.max_participants` | `maxParticipants` |
| `social_match.team_a_capacity`, `team_b_capacity` | `teamACapacity`, `teamBCapacity` |
| `social_match.fee_per_person` | `feePerPerson` |
| `club.club_id`, `club_name` | `clubId`, `clubName` |
| `club_member.club_member_id` | `clubMemberId` / 복수 선택 `clubMemberIds` |
| `club_join_request.request_id` | `joinRequestId` |
| `match_application.application_id`, `application_type` | `applicationId`, `applicationType` |
| `match_participant.participant_id`, `match_side` | `participantId`, `matchSide` |
| `app_user.user_id`, `nickname` | `userId`, `nickname` |
| `facility.concurrent_match_limit` | `concurrentMatchLimit` |
| `sport_id`, `region_id`, `facility_id` | `sportId`, `regionId`, `facilityId` |
| `schedule_change.schedule_change_id` | `scheduleChangeId` |
| `notification.notification_id` | `notificationId` |

`map-underscore-to-camel-case=true`는 `match_id → matchId`를 처리하지만 `request_id → joinRequestId` 같은 **의미를 붙인 별칭**까지 처리하지 않습니다. 이때는 SQL 별칭이나 `<resultMap>`을 사용합니다.

```sql
-- Mapper SELECT 예시. 실제 조회·권한 조건은 서비스에 맞게 구현합니다.
SELECT request_id AS join_request_id, club_id, user_id, message, status
FROM club_join_request
WHERE club_id = #{clubId}
```

모델의 루트 이름도 구분합니다.

```text
match / matches             매치 본체·목록
club / clubs                등록 팀 본체·목록
managedClub / managedClubs   관리 중인 팀·선택 가능한 팀
currentMember               로그인 공통 회원 정보
member                      마이페이지 회원 표시
```

`teamA`·`teamB`는 해당 경기의 A/B 배정으로 등록 팀 `club`과 다릅니다. CSS·DOM의 `team-*`·`data-room-*`는 이벤트 연결점이므로 변경하지 않습니다.

## 6. POST 폼과 참가 신청 흐름

### 6.1 요청 수신 방식

기존 폼은 일반 HTML 폼입니다. JSON 전송이 아니므로 보통 `@ModelAttribute` 또는 `@RequestParam`으로 받습니다. **`@RequestBody`를 전제로 구현하지 않습니다.**

팀 참가 선택에는 다음 형태의 DTO를 준비합니다.

```java
// DTO 구조 예시. 각 필드의 getter/setter를 구현해야 합니다.
public class MatchSelectionForm {
    private String applicationType;       // individual | team
    private Long clubId;                  // 개인 참가에서는 전송되지 않음
    private List<Long> clubMemberIds = new ArrayList<>();
}
```

위 코드에는 `java.util.List`·`java.util.ArrayList` import가 필요합니다. private 필드만 선언하면 일반 프로퍼티 바인딩이 되지 않습니다. getter/setter 또는 적절한 생성자 바인딩을 구현합니다.

- `applicationType`은 허용값을 검증합니다. `team`이면 `clubId`와 대상 팀원을 검증합니다.
- 개인 참가에서는 팀 관련 입력이 `disabled`여서 전송되지 않습니다. 필드 누락을 정상적으로 처리합니다.
- 체크하지 않은 체크박스도 파라미터가 아예 없을 수 있습니다. 복수 입력은 빈 목록, 동의값은 미동의로 처리합니다.
- `clubMemberIds`는 회원 ID가 아니라 **`club_member.club_member_id`** 목록입니다.
- 신청자의 `userId`·연락처·권한·요금·잔여 좌석은 전송값을 믿지 않고 인증 정보와 DB에서 읽습니다.

### 6.2 매치 참가 신청

```text
GET /matches/{matchId}
  1. DB에서 매치·참가 현황·본인이 선택할 수 있는 등록 팀 조회
  2. match 모델과 currentMember를 설정하고 matches/detail 반환

POST /matches/{matchId}/selection
  3. applicationType / clubId / clubMemberIds 수신
  4. 본인·소속·중복·잔여 좌석 검증
  5. 선택 내용을 서버 세션에 저장
  6. /matches/{matchId}/apply로 리다이렉트

GET /matches/{matchId}/apply
  7. 세션 선택과 최신 DB 상태로 selection / match / canReserve 구성
  8. matches/join 반환

POST /matches/{matchId}/apply
  9. agreeRules / agreePayment 수신 후 동의·잔여 좌석·중복·권한 재검증
 10. 트랜잭션 안에서 신청·참가자 등록
 11. 결과 GET으로 리다이렉트하고 completed / completion으로 완료 화면 표시
```

세션 선택은 본인·매치에 연결하고, 다른 매치에 재사용하거나 오래된 선택을 다시 보내지 못하게 합니다. 재전송·동시 신청에서도 중복 등록이나 정원 초과가 발생하지 않도록 DB 제약과 적절한 잠금 등을 조합합니다.

신청 완료는 **결제 완료가 아닙니다**. 현재 폼은 참가비·취소 안내 확인이며 실제 결제 서비스는 별도 설계가 필요합니다. 표시용 결제 수단 라디오값을 결제 요청으로 처리하지 않습니다.

POST 후에는 원칙적으로 PRG(POST → Redirect → GET)를 적용합니다. 성공 문구는 저장 성공 후에만 표시하고, 업무 오류는 입력값과 화면 계약에 맞는 `error` 등을 반환합니다. `@Valid`를 쓰면 `BindingResult`를 대상 폼 인자 바로 뒤에 두고, 오류 시에도 선택지·공통 모델을 다시 설정합니다.

## 7. 로그인·권한·CSRF

### 7.1 Security 스타터만으로는 완성되지 않음

기본 Security 설정에서는 공개 화면이나 CSS도 인증 대상이 될 수 있습니다. 자체 로그인 화면과 DB 회원에 맞는 `SecurityFilterChain`, `UserDetailsService`, `PasswordEncoder`를 구현합니다.

공개·인증·권한 규칙의 기준:

| 대상 | 규칙 |
| --- | --- |
| `/css/**`, `/js/**`, `/images/**` | 공개 |
| `GET /`, `/matches`, `/matches/cards`, `/matches/{matchId}` | 공개 |
| `/login`, `/signup`, 약관·개인정보·계정 안내 | 공개. 각 POST의 입력 검증은 필요 |
| `GET /teams`, `/teams/{clubId}` | 공개 |
| `/matches/{matchId}/selection`, `/matches/{matchId}/apply` | 로그인 필수 |
| `/teams/manage`, 팀 생성·수정·가입 신청 | 로그인 필수. 작업별 소속·팀장 검증 |
| `/mypage/**` | 로그인 필수. 본인 정보만 처리 |
| `/admin/system/**` | 시스템 관리 권한 |
| 시설 관리·매치 운영·관리자 알림 | 해당 관리 권한과 담당 시설 검증 |

`/teams/manage`는 `/teams/*`에도 해당하므로 **인증 필수 규칙을 먼저 두는** 등 적용 순서에 주의합니다. `/matches/**`·`/teams/**`를 통째로 허용하면 신청 화면이나 관리 작업까지 공개됩니다. GET과 POST도 구분합니다.

### 7.2 기존 로그인 폼과의 대응

| 폼 | Security 설정·서버 처리 |
| --- | --- |
| `POST /login` | `loginProcessingUrl("/login")`. 보통 Security 필터가 처리 |
| `email` | `usernameParameter("email")`. 기본값인 `username`이 아님 |
| `password` | `passwordParameter("password")` |
| `remember` | 기능 구현 시 `rememberMeParameter("remember")` |
| `redirect` | 인증 성공 핸들러에서 내부 이동 경로 검증 |
| `POST /logout` | 세션 종료. GET 링크로 로그아웃하지 않음 |

Security DSL의 해당 부분은 다음 형태입니다. **완성된 SecurityConfig가 아닙니다.** 공개 범위·사용자 조회·실패/성공 핸들러 등을 별도로 구성합니다.

```java
http.formLogin(form -> form
        .loginPage("/login")
        .loginProcessingUrl("/login")
        .usernameParameter("email")
        .passwordParameter("password")
        .failureUrl("/login?error")
        .permitAll());
```

- `GET /login` 컨트롤러에서 `auth/login`을 반환합니다. `?error`가 있으면 사용자용 문구를 `error` 모델에 설정합니다. 원본 예외나 SQL 오류를 노출하지 않습니다.
- `UserDetailsService`는 이메일로 `app_user`를 조회하고 저장된 `password_hash`와 역할을 Security에 전달합니다. 비밀번호는 적절한 `PasswordEncoder`로 검증합니다.
- 인증 성공 후 검증된 `redirect`, 저장된 요청, 기본 페이지 등 이동 우선순위를 정합니다. 외부 URL·`//` 시작·역슬래시·제어문자 등을 거부하고 허용한 앱 내부 경로로만 이동합니다. hidden 값을 무조건 `sendRedirect`하지 않습니다.
- 매치 상세의 로그인 링크는 해당 상세 화면으로 돌아갈 경로를 전달합니다. 성공 핸들러를 구현하지 않으면 이 명시적 복귀값이 자동 적용되지 않습니다.
- 로그인 상태 유지는 체크박스만으로 작동하지 않습니다. remember-me 서비스·비밀키 관리·Cookie 속성·만료 및 로그아웃 처리를 구현합니다.
- 폼의 `member`/`facility`/`system`과 Security의 GrantedAuthority는 별개입니다. 예를 들어 `hasRole("SYSTEM")`이면 인증 측에서 `ROLE_SYSTEM`을 부여합니다. 신청값 `role=system`만으로 관리 권한을 주지 않습니다.

### 7.3 CSRF와 공통 회원 모델

기존 POST 폼에는 다음 hidden 입력이 있습니다.

```html
<input type="hidden" th:if="${_csrf != null}"
       th:name="${_csrf.parameterName}" th:value="${_csrf.token}" />
```

- CSRF는 원칙적으로 유지합니다. 403을 피하려고 일괄 비활성화하지 않습니다.
- `_csrf`는 Security가 제공합니다. 고정 토큰을 임의 생성하지 않습니다.
- `currentMember`는 인증 사용자에서 만들고 `userId`·`nickname`·`phone`·`initial` 등 필요한 표시 정보만 담습니다. 비밀번호 해시는 포함하지 않습니다.
- 마이페이지·관리자·팀장 권한은 서버에서 매번 검증합니다. 버튼 숨김이나 JavaScript 검사만으로는 보호되지 않습니다.

## 8. 팀·마이페이지·관리자 연결

### 팀

- `/teams`에는 `clubs`, 상세에는 `club`, 관리에는 `managedClub`·`managedClubs`를 설정합니다.
- 관리 대상 선택 쿼리는 `clubId`, 탭은 `tab=team|members|applications`입니다.
- 생성/수정 폼은 `clubName`, `sportId`, `regionId`, `level`, `maxMembers`, `recruitStatus`, `activityDays`, `activityStartTime`, `activityEndTime`, `intro`를 받습니다.
- `activityDays`는 반복 입력입니다. 1=월~7=일 값을 검증하고 SQL의 쉼표 구분 형식으로 변환합니다.
- 팀원 작업의 `clubMemberId`와 가입 신청 작업의 `joinRequestId`를 구분합니다.
- `recruiting`/`closed`는 서버에서 SQL 저장값 `모집중`/`마감`으로 대응시킵니다.

### 마이페이지

- `member`, `summary`, `sports`, `otherSports`, `history`, `currentReservations`, `pastReservations`, `notifications` 등을 설정합니다.
- 표시용 레이팅은 0.00~5.00 또는 `기록 없음`으로 반환합니다. 내부 MMR을 그대로 표시하지 않습니다.
- 프로필 저장은 `nickname`, `phone`, `regionId`, `representativeSportId`입니다. `regionId`의 빈 문자열은 미선택으로 처리합니다.
- 신청 취소는 `applicationId`, 알림 작업은 `notificationId`를 쓰고 본인 소유인지 검증합니다.
- 탈퇴 폼은 요청 생성 계약입니다. 수신만으로 계정을 즉시 삭제하지 않습니다.

### 관리자

- 시설 선택은 `facilityId`, 운영 대상 매치 선택은 `matchId`입니다.
- `view=schedule` 등은 서버에 전달되는 쿼리입니다. `#facility-schedule` 같은 URL 해시는 **서버로 전송되지 않습니다**. 둘을 혼동하지 않습니다.
- `adminUser`, `adminNotifications`, `unreadAdminNotificationCount`, `adminNotificationSummary` 등 공통 모델도 설정합니다.
- `/admin/rooms` URL은 유지하고 서비스 내부 대상은 `social_match`로 처리합니다.
- 재배정 폼의 `matchSide`는 `A`/`B`/빈 문자열입니다. 빈 값은 미배정으로 처리하거나 업무상 불가한 상태라면 명확한 오류를 반환합니다.
- 저장 성공·권한 오류·경합·빈 목록을 구분합니다. 설정이나 내보내기를 실제 저장/생성 없이 성공으로 처리하지 않습니다.

## 9. 실행과 검증

### 9.1 Windows PowerShell 실행

의존성·DB 설정·첫 컨트롤러와 필요한 모델을 준비한 뒤 **저장소 루트**에서 실행합니다.

```powershell
$env:DB_URL = 'jdbc:mysql://localhost:3306/spoit'
$env:DB_USERNAME = '실제_개발DB_사용자명'
$env:DB_PASSWORD = '실제_개발DB_비밀번호'
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

```text
http://localhost:8080/matches
```

포트·컨텍스트 경로를 변경했다면 맞춰 접속합니다. CSS/JS/이미지의 200 응답, 브라우저 콘솔 오류, 서버의 템플릿 예외를 확인합니다.

### 9.2 기존 프런트엔드 검사

Bun이 설치돼 있으면 저장소 루트에서 실행합니다. 추가 패키지는 필요 없습니다.

```powershell
bun src/test/frontend/check-contracts.mjs
bun src/test/frontend/match-selection.test.mjs
bun src/test/frontend/docs-contracts.test.mjs
```

앞의 두 검사는 프런트엔드 계약·구문과 일부 UI 동작을, 마지막 검사는 HTML 문서의 목차·기능 항목·내부 링크·정적 파일 참조를 확인합니다. 문서 검사만으로 SQL 설명의 사실관계까지 검증되지는 않습니다. **Spring Boot 실행·Thymeleaf 렌더링·DB 저장·실제 브라우저 테스트를 대체하지 않습니다.**

### 9.3 백엔드 연결 후 체크리스트

- [ ] 비로그인 목록·상세와 CSS/JS/이미지가 정상 표시된다.
- [ ] 빈 목록·존재하지 않는 매치·권한 없음·입력 오류를 적절히 표시한다.
- [ ] 검색의 `all`·미선택·복수 시간·잘못된 ID를 처리한다.
- [ ] 로그인 실패 안내·안전한 복귀·POST 로그아웃이 동작한다.
- [ ] 정상 CSRF POST는 처리하고 토큰 누락·타인 ID 작업은 거부한다.
- [ ] 개인/팀 신청에 중복 참가자가 없고 동시 신청에도 정원을 넘지 않는다.
- [ ] 저장 후 DB 값을 다시 조회하며 새로고침으로 중복 등록되지 않는다.
- [ ] 팀장·시설 담당자·시스템 관리자의 작업 범위가 분리돼 있다.
- [ ] 모바일 너비·키보드·다이얼로그 열기와 닫기를 확인했다.
- [ ] 필요한 테스트 DB·설정을 준비하고 `./gradlew test`(Windows: `.\gradlew.bat test`)를 실행했다.

## 10. 자주 발생하는 문제

| 증상 | 확인 사항 |
| --- | --- |
| 실행 시 DataSource 오류 | MySQL 실행·DB 생성·환경변수·local 프로필 활성화 |
| `TemplateInputException` / 템플릿 없음 | Thymeleaf 의존성·`templates/` 위치·반환 뷰 이름 |
| `Property or field ... cannot be found` | 새 모델명·DTO getter·필수 중간 객체·숨겨진 패널의 모델 |
| `/matches`가 404 | Controller 매핑·패키지 스캔 범위 |
| 화면에 `matches/list` 글자만 표시 | `@RestController` / `@ResponseBody` 사용 여부 |
| CSS가 적용되지 않음 | `/static/css/...` 오기·Security 리다이렉트·CDN 실패 |
| 의도하지 않은 기본 로그인 화면 | 자체 Security 설정·`GET /login` 컨트롤러·공개 URL |
| 로그인 항상 실패 | `usernameParameter("email")`·DB 조회·PasswordEncoder와 저장 해시 |
| POST가 403 | CSRF 토큰·세션 Cookie·로그인·권한 |
| `all`을 Long으로 변환 실패 | 검색 DTO 타입·전체 선택값 정규화 |
| 선택 조건이 복원되지 않음 | 선택지와 검색 모델의 ID 타입·`date`에서 `dateIdx`로의 대응 |
| 팀 ID·팀원이 전송되지 않음 | `clubId` / `clubMemberIds` 이름·disabled·미선택 |
| 관리자 화면만 전환되고 데이터 없음 | `view`별 데이터 조회·해시는 서버에 전달되지 않음 |

## 11. 운영 전 확인사항

### SQL만으로 정할 수 없는 항목

기존 화면을 유지하기 위해 다음 항목은 백엔드 설계에서 명시적으로 정합니다. 프런트에서 임의값을 만들어 저장하지 않습니다.

- SQL 필수값 `min_participants` / `facility_sport.default_min`의 출처. 화면은 최대 인원만 입력합니다.
- 시설 `region_id`·위도·경도 출처. 사람 수 `capacity`와 동시 운영 수 `concurrentMatchLimit`는 별개입니다.
- 팀 실력 `level`·대표 종목 `representativeSportId`·일부 운영/CMS 데이터의 저장소.
- UI/SQL 상태 대응·정원 도달 시 A/B 배정·확정 대기의 좌석 점유 규칙.
- 표시용 레이팅 산출·알림 삭제 저장 정책·탈퇴 요청 처리.
- 참가비 안내와 실제 결제의 책임 분리.

운영 적용 전에는 다음도 확인합니다.

- HTTPS·세션 Cookie·remember-me·인가·입력 검증·감사 로그.
- 약관 `/terms`·개인정보 `/privacy`·계정 찾기·비밀번호 재설정 구현.
- Tailwind Browser CDN·외부 폰트 의존성을 고려해 빌드 CSS·자체 호스팅·CSP 검토. CSP 강화 시 기존 인라인 처리의 호환성 확인.
- DB 마이그레이션 관리. 파괴적 DDL을 운영 서버 실행 시 적용하지 않음.

**첫 완료 목표는 실제 DB에서 조회한 목록을 `/matches`에 표시하는 것입니다. 이후 인증과 POST를 단계적으로 연결하세요.**

### 공식 참고 문서

- [Spring Boot 시스템 요구사항](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring Boot SQL 데이터베이스 연결](https://docs.spring.io/spring-boot/reference/data/sql.html)
- [Spring Security 폼 로그인](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/form.html)
- [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [MyBatis Spring Boot 설정](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)

공식 사이트의 기본 문서는 최신 버전으로 바뀔 수 있으므로, 실제 프로젝트에 선언한 Spring Boot·Spring Security·MyBatis 버전과 맞춰 확인합니다.
