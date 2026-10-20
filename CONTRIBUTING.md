# SPO:IT 팀 작업 규칙

## 1. 브랜치 구조

| 브랜치 | 용도 | 병합 권한 |
|---|---|---|
| `main` | 배포용. 병합되면 자동 배포 | 팀장만 |
| `develop` | 개발 통합 브랜치 (기본 브랜치) | 팀원 승인 1명 이상 후 누구나 |
| `feature/*`, `fix/*` 등 | 개인 작업 브랜치 | - |

`main`, `develop`에는 직접 push할 수 없습니다. 모든 변경은 PR로 올립니다.

## 2. 작업 흐름

```bash
# 1) 항상 최신 develop에서 시작
git checkout develop
git pull origin develop

# 2) 작업 브랜치 생성
git checkout -b feature/RES-03-match-apply

# 3) 작업 후 커밋
git add .
git commit -m "feat(match): 개인 참가 신청 API 추가"

# 4) push 후 GitHub에서 develop으로 PR 생성
git push origin feature/RES-03-match-apply
```

- PR은 다른 팀원 **1명 이상의 승인**을 받아야 병합할 수 있습니다.
- feature → develop 병합은 **Squash and merge**를 사용합니다.
- develop → main 병합은 팀장이 **Create a merge commit**으로 진행합니다.
- **병합된 브랜치는 다시 사용하지 않습니다.** 새 작업은 최신 develop에서 새 브랜치로 시작합니다.
- 병합된 브랜치는 PR 화면의 **Delete branch**로 정리합니다.

## 3. 브랜치 이름

```
{타입}/{요구사항ID}-{짧은-설명}
```

| 타입 | 용도 | 예시 |
|---|---|---|
| `feature` | 새 기능 | `feature/RES-03-match-apply` |
| `fix` | 버그 수정 | `fix/RES-05-cancel-capacity` |
| `refactor` | 기능 변화 없는 코드 개선 | `refactor/MAT-02-rating-service` |
| `docs` | 문서 | `docs/readme-setup` |
| `chore` | 설정, 빌드, 의존성 | `chore/gradle-mybatis` |

- 요구사항 ID는 요구사항 정의서의 ID(COM-01, RES-03, MAT-02, FAC-01, ADM-01 등)를 씁니다. 해당 ID가 없는 작업이면 생략합니다.
- 설명은 영어 소문자와 하이픈(`-`)만 씁니다.

## 4. 커밋 메시지

```
{타입}({범위}): {요약}
```

| 타입 | 의미 |
|---|---|
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `refactor` | 리팩터링 |
| `style` | 코드 포맷, 세미콜론 등 (로직 변화 없음) |
| `docs` | 문서 |
| `test` | 테스트 추가·수정 |
| `chore` | 빌드, 설정, 의존성 |

- 범위(scope)는 도메인 이름을 씁니다: `user`, `facility`, `match`, `club`, `rating`, `admin`, `common`
- 요약은 한글로, 50자 이내, 마침표 없이 씁니다.

```
feat(match): 동호회 단위 참가 신청 기능 추가
fix(club): 탈퇴 후 재가입 시 중복 소속 오류 수정
chore(common): MyBatis 매퍼 경로 설정
```

## 5. 로컬 설정

1. `src/main/resources/application-local.properties.example`을 복사해 `application-local.properties`로 이름을 바꿉니다.
2. 본인 DB 비밀번호를 입력합니다.
3. `application-local.properties`는 `.gitignore`에 등록되어 있어 Git에 올라가지 않습니다. **비밀번호, API 키는 절대 커밋하지 않습니다.**

## 6. DB 스키마 변경

- 테이블·컬럼 변경은 ERD와 스키마 SQL 파일을 **함께** 수정해 PR에 포함합니다.
- PR 설명에 변경한 테이블과 이유를 적고, 팀원에게 공유합니다.
