# 선택적 preview UI

기본 main 빌드는 preview 컨트롤러, fixture, preview 모델을 로드하지 않습니다.
이들은 선택적 루트 디렉터리인 `preview/`에만 있으며 `-Ppreview=true`로 명시적으로 활성화합니다.

```
preview/
  java/co/kr/spoit/preview/        # preview 전용 컨트롤러, fixture, 뷰 모델
  test/java/co/kr/spoit/preview/   # preview 전용 JUnit 스모크 테스트

src/main/java/                     # main 애플리케이션, 설정, core 페이지 라우팅
src/main/resources/templates/      # 기존 Thymeleaf 화면
src/main/resources/static/         # 기존 CSS, JS, 이미지
src/test/java/                     # core 테스트, core 라우트 MockMvc 테스트 포함
src/test/frontend/                 # 프론트엔드 계약 검사
```

## 기본 main 빌드

main 애플리케이션에는 preview 플래그가 필요 없습니다.

```bash
./gradlew test
./gradlew bootJar
./gradlew bootRun
```

core 페이지 컨트롤러는 `/login`을 제외한 기존 Thymeleaf 페이지를 빈 목록과 빈 필드로 렌더링합니다. `/login`은 `MemberAuthController`가 담당합니다.
preview import, 문자열, annotation, fixture 데이터가 없는 일반 main 소스 파일입니다.
이메일, 닉네임, 전화번호 같은 샘플 계정 데이터를 만들어 넣지 않습니다.

빈 렌더링으로 접근 가능한 페이지 라우트는 다음과 같습니다. (`/login`은 `MemberAuthController`가 담당하므로 제외)

```
/signup  /mypage  /mypage/profile
/matches  /matches/cards  /matches/{matchId}  /matches/{matchId}/apply
/teams  /teams/{clubId}  /teams/manage
/admin/facility  /admin/operations  /admin/system
```

`/`는 `HomeController`가 `/matches`로 forward하고 HTTP 200을 반환합니다.

## preview 빌드

preview 컨트롤러와 fixture는 명시적 플래그가 있을 때만 포함됩니다.

```bash
./gradlew test -Ppreview=true
./gradlew bootRun -Ppreview=true
```

preview를 활성화하면 빌드가 preview 소스 루트를 추가하고, core 페이지 컨트롤러와 실제 로그인 컨트롤러 및 그 테스트를 main/test 소스 세트에서 제외합니다.
페이지 라우트는 preview 컨트롤러가 담당하므로 중복 매핑도, 런타임 조건 분기도 없습니다.

preview 빌드에서 제외되는 대상:

```
co/kr/spoit/web/CorePageController.java
co/kr/spoit/web/CorePageRouteTest.java
co/kr/spoit/member/controller/MemberAuthController.java   (+ MemberAuthControllerTest.java)
```

로그인 *도메인*(`member.domain`, `member.mapper`, `member.service`, `member.security`)은 preview 빌드에서도 계속 컴파일됩니다. 제외되는 것은 `GET /login`을 중복 매핑할 페이지 컨트롤러뿐입니다.
따라서 `-Ppreview=true`에서도 `POST /login`은 실제 인증을 수행하며, fixture에서 오는 것은 렌더링되는 로그인 화면뿐입니다.

## preview 제거 또는 비활성화

`preview/`를 삭제하면 기본 빌드와 같습니다.

```bash
rm -rf preview
./gradlew clean test bootJar
```

공용 템플릿과 정적 자산은 `src/main/resources` 아래에 그대로 남고, 모든 페이지 라우트는 기존 템플릿을 빈 값으로 렌더링하며 HTTP 200을 반환합니다.

## Docker

Dockerfile은 main 애플리케이션 소스만 빌드합니다. preview 코드는 이미지에 포함되지 않으며, 빌드에 `preview/` 디렉터리가 필요하지 않습니다.
