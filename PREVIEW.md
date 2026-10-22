# Optional preview UI

The default main build does not load preview controllers, fixtures, or preview models.
Those live only in the optional root `preview/` directory and are enabled explicitly with
`-Ppreview=true`.

```
preview/
  java/co/kr/spoit/preview/        # preview-only controllers, fixtures, view models
  test/java/co/kr/spoit/preview/   # preview-only JUnit smoke tests

src/main/java/                     # main application, config, core page routing
src/main/resources/templates/      # existing Thymeleaf screens
src/main/resources/static/         # existing CSS, JS, images
src/test/java/                     # core tests, including core route MockMvc tests
src/test/frontend/                 # frontend contract checks
```

## Default main build

No preview flag is needed for the main application:

```bash
./gradlew test
./gradlew bootJar
./gradlew bootRun
```

The core page controller renders every existing Thymeleaf page with empty lists and blank
fields. It is a normal main source file with no preview imports, strings, annotations, or
fixture data. It does not invent emails, nicknames, phone numbers, or other sample account
data.

The page routes reachable with empty rendering are:

```
/  /login  /signup  /mypage  /mypage/profile
/matches  /matches/cards  /matches/{matchId}  /matches/{matchId}/apply
/teams  /teams/{clubId}  /teams/manage
/admin/facility  /admin/operations  /admin/system
```

`/` forwards to `/matches` and returns HTTP 200.

## Preview build

Preview controllers and fixtures are included only with an explicit flag:

```bash
./gradlew test -Ppreview=true
./gradlew bootRun -Ppreview=true
```

When preview is enabled, the build adds the preview source roots and excludes the core
page controller, the real login controller, and their tests from the main/test source sets.
The preview controllers own the page routes, so there are no duplicate mappings and no
runtime conditional switch.

Excluded in preview builds:

```
co/kr/spoit/web/CorePageController.java
co/kr/spoit/member/controller/MemberAuthController.java   (+ MemberAuthControllerTest)
```

The login *domain* (`member.domain`, `member.mapper`, `member.service`, `member.security`)
still compiles in preview builds; only the page controller that would duplicate
`GET /login` is excluded. `POST /login` therefore performs real authentication even with
`-Ppreview=true`; only the rendered login screen comes from fixtures.

## Removing or disabling preview

Deleting `preview/` is equivalent to the default build:

```bash
rm -rf preview
./gradlew clean test bootJar
```

The shared templates/static assets remain under `src/main/resources`, and all page routes
still render the existing templates with empty values and return HTTP 200.

## Docker

The Dockerfile builds only the main application sources. Preview code is not included in
the image, and the build does not require the `preview/` directory.
