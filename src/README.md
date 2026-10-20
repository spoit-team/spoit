# SPO:IT 프런트엔드 리소스 안내

> **Spring Boot 적용 문서 홈페이지:** [HTML 문서 열기](main/resources/static/docs/index.html) · 기능별 SQL 매핑·화면 계약·서버 처리 기준을 정리한 문서입니다. 브라우저에서 해당 파일을 직접 열 수 있습니다. 서버 연결 후 주소는 `/docs/index.html`이며, Security의 접근 권한 설정에 따라 인증이 필요할 수 있습니다.

이 폴더는 Spring Boot + Thymeleaf 서비스에 연결할 프런트엔드 리소스입니다. 이 저장소에는 목업 데이터, 브라우저 저장소 기반 시연 기능, 테스트 계정, 컨트롤러·서비스·DB·인증·결제 구현, 실행 가능한 목업·프로토타입 서버가 없습니다. 화면은 대상 애플리케이션 서버가 제공하는 모델과 폼 경로에 연결해야 합니다.

## 명칭 규칙과 Spring Boot 바인딩

기준 스키마는 루트의 `db/schema.sql`입니다. SQL의 snake_case는 Java DTO·Thymeleaf·폼에서 camelCase로 사용합니다. **폼과 검색 파라미터 이름이 변경되었으므로 컨트롤러도 아래 계약을 사용해야 합니다.** 이전 이름을 중복 전송하거나 호환 별칭을 만들지 않습니다.

| SQL 식별자·컬럼 | 프런트/Java 이름 | 이전 이름 |
| --- | --- | --- |
| `social_match.match_id` | `matchId` | `roomId`, 매칭방의 `id` |
| `social_match.max_participants` | `maxParticipants` | 매칭방의 `capacity` |
| `social_match.team_a_capacity`, `team_b_capacity` | `teamACapacity`, `teamBCapacity` | `teamASeats`, `teamBSeats` |
| `social_match.fee_per_person` | `feePerPerson` | 생성 폼의 `fee` |
| `club.club_id`, `club_name` | `clubId`, `clubName` | `teamId`, 팀의 `id`·`name` |
| `club_member.club_member_id` | `clubMemberId`, 반복 선택 `clubMemberIds` | `memberId`, `memberIds` |
| `club_join_request.request_id` | `joinRequestId` | 팀 가입 신청의 `applicationId`·`id` |
| `match_application.application_id`, `application_type` | `applicationId`, `applicationType` | 매치 신청의 `id`, `kind` |
| `match_participant.participant_id`, `match_side` | `participantId`, `matchSide` | 참가자의 `id`, 팀 배정 폼의 `team` |
| `app_user.user_id`, `nickname` | `userId`, `nickname` | 회원의 `id`·`name` |
| `sport.sport_id`, `region.region_id` | `sportId`, `regionId` | `sport`, `district`, `area`, `preferredRegion` 등 ID 입력 |
| `facility.facility_id`, `concurrent_match_limit` | `facilityId`, `concurrentMatchLimit` | 시설의 `id`, `zoneCount`, `reservableZoneCount` |
| `schedule_change.schedule_change_id` | `scheduleChangeId` | 휴관 항목의 `id` |
| `report.report_id`, `penalty.penalty_id` | `reportId`, `penaltyId` | 신고·제재의 `id` |
| `notification.notification_id` | `notificationId` | 알림의 `id` |

- 모델 루트는 `match`/`matches`, `club`/`clubs`, `managedClub`/`managedClubs`를 사용합니다. 로그인 회원은 `currentMember`, 마이페이지 회원은 `member`를 유지합니다.
- 경로의 `/teams`, `/admin/rooms`, `/participants/.../team` 등 **실제 URL과 화면 기능은 유지**합니다. `{id}` 같은 경로 변수의 표기만 더 구체적일 수 있으며 실제 URL에는 영향이 없습니다. 쿼리 키는 매치 선택 `matchId`, 시설 선택 `facilityId`, 관리 팀 선택 `clubId`를 사용합니다.
- CSS 클래스·DOM ID·`data-team-*`/`data-room-*` 같은 이벤트 연결점은 유지합니다. 속성에 담기는 서버 모델 값과 폼의 `name`만 새 계약을 사용합니다.
- `sportName`, `feeText`, `dateLabel`, `ratingText`, `leaderName`, `capacityLabel`, `completion.roomTitle` 등은 표시용 DTO 값입니다. 엔티티 컬럼으로 억지 변환하지 않습니다. 시설의 사람 수 `capacity`는 매치의 `maxParticipants`나 동시 운영 수 `concurrentMatchLimit`와 다릅니다.
- `representativeSportId`는 프로필의 대표 종목 선택값이며 현재 SQL에는 직접 저장 컬럼이 없습니다. 경기 이력 필터의 `recordSport`는 기존 쿼리 이름을 유지합니다.
- 폼 값은 기존대로 `individual`/`team`, `recruiting`/`closed`, `A`/`B`/빈 문자열(미배정) 등을 사용합니다. 서버가 `application_type`, `recruit_status`, `match_side`의 저장값으로 검증·변환해야 합니다. 명칭 변경은 상태값·정책 변경이 아닙니다.
- 검색의 `all`·빈 문자열 같은 전체 선택값도 유지합니다. 이를 받는 검색 DTO는 문자열로 수신해 전체/미선택과 실제 ID를 구분한 뒤 검증하세요. `all`을 그대로 `Long` ID 필드에 바인딩하지 않습니다. 시설 등록의 `sports` 문자열은 종목 ID 목록과 다른 기존 입력 계약입니다.
- 평문 입력 `password`는 가입/로그인 요청값입니다. `passwordHash`로 이름을 바꿔 보내지 않으며 해시는 서버에서 생성합니다. `receiptNo`는 내부 `applicationId`와 구분합니다.

화면 브랜드는 SPO:IT으로 표기하고 공통 아이콘은 `static/images/spoit-mark.svg`를 사용합니다. 영문 슬로건은 1280px 이상에서만 작게 표시합니다. 주요 사용자 화면은 모바일 접이식 메뉴와 알림 버튼을 제공하고, 로그인한 회원 정보와 로그아웃 폼은 서버 모델 `currentMember`를 기준으로 렌더링합니다. 모든 화면의 종목 모델은 테니스·축구+풋살·족구·농구·배드민턴만 제공합니다.

## 1. 현재 상태

- `templates/`에는 14개 화면 템플릿과 2개 공통 조각이 있습니다. 별도의 정적 HTML 화면과 목업 스크립트는 없습니다.
- 화면 스크립트는 `localStorage`·`sessionStorage`를 사용하지 않습니다. 상태 변경은 모두 명시적인 서버 폼 전송입니다.
- 테스트 계정, 역할 선택 로그인, 샘플 회원·시설·팀·참가자·신고·감사 로그, 고정 통계 수치는 템플릿에 들어 있지 않습니다. 값은 서버 모델이 제공해야 합니다.
- `static/index.html`은 정적 진입용 안내이며 `matches`로 메타 리다이렉트합니다. 실제 서비스에서는 서버의 `/` 라우트가 `/matches`로 리다이렉트해야 합니다.
- 실행 가능한 미리보기 서버는 제공하지 않습니다. Thymeleaf 화면의 렌더링과 폼 전송은 대상 서비스에 연결한 뒤 확인해야 합니다.

## 2. 폴더 구조

```text
src/main/resources/
├─ templates/
│  ├─ fragments/
│  │  ├─ layout.html              # head·일반 헤더·간단 헤더·푸터
│  │  └─ admin.html               # 관리자 head·사이드바·상단 바·알림 모달
│  ├─ matches/
│  │  ├─ list.html                # /matches 카드 그리드 메인
│  │  ├─ cards.html               # /matches/cards 카드형 비교 화면
│  │  ├─ detail.html              # /matches/{matchId} 상세·참가자 선택
│  │  └─ join.html                # /matches/{matchId}/apply 신청·완료
│  ├─ auth/
│  │  ├─ login.html               # /login
│  │  └─ signup.html              # /signup
│  ├─ mypage/
│  │  ├─ index.html               # /mypage
│  │  └─ profile.html             # /mypage/profile
│  ├─ teams/
│  │  ├─ list.html                # /teams
│  │  ├─ detail.html              # /teams/{clubId}
│  │  └─ manage.html              # /teams/manage
│  └─ admin/
│     ├─ facility.html            # /admin/facility
│     ├─ operations.html          # /admin/operations
│     └─ system.html              # /admin/system
└─ static/
   ├─ index.html                  # 정적 진입 안내(서버 라우팅 전 사용 안 함)
   ├─ css/
   │  ├─ styles.css               # 공통 토큰·기본 컴포넌트
   │  ├─ notification-center.css  # 공통 알림 스타일(현재 템플릿에서 미사용)
   │  ├─ admin.css                # 관리자 공통
   │  ├─ admin-schedule.css       # 시설 일정·캘린더
   │  ├─ admin-system.css         # 시스템 관리자 보정
   │  ├─ playon-matches.css       # 매칭 목록·상세 공통
   │  ├─ playon-match-cards.css   # /matches 카드 그리드(필수)
   │  ├─ playon-list.css          # 이전 세로 목록 스타일(현재 미참조)
   │  ├─ match-detail.css         # 매칭 상세
   │  ├─ match-join.css           # 매칭 신청
   │  ├─ match-operations.css     # 매치 운영
   │  ├─ team-pages.css           # 팀 목록·상세·관리
   │  └─ mypage.css               # 마이페이지·프로필
   ├─ js/                         # 화면별 UI 동작만 담당(15개)
   └─ images/                     # 로고·시설 사진
```

## 3. 화면 경로

| 경로 | 템플릿 | 설명 |
| --- | --- | --- |
| `GET /` | - | `/matches`로 리다이렉트 |
| `GET /matches` | `matches/list.html` | 종목·지역·날짜·시간 검색, 카드형 매칭방 목록 |
| `GET /matches/cards` | `matches/cards.html` | 같은 데이터 계약을 사용하는 별도 카드형 비교 화면 |
| `GET /matches/{matchId}` | `matches/detail.html` | 방 상세, A/B 참가 현황, 개인·팀 참가자 선택 |
| `GET /matches/{matchId}/apply` | `matches/join.html` | 신청 내용·결제 안내 확인, 접수 결과 |
| `POST /matches/{matchId}/selection` | - | 선택한 참가 방식·팀·팀원·연락처를 서버 세션에 보관 |
| `POST /matches/{matchId}/apply` | - | 참가 신청 접수(결제 실행 아님), 결과 화면으로 리다이렉트 |
| `GET /login` | `auth/login.html` | 일반 로그인 |
| `POST /login` | - | 이메일·비밀번호 인증 |
| `GET /login/find-account` | 서버 구현 필요 | 아이디 찾기 안내 |
| `GET /login/find-password` | 서버 구현 필요 | 비밀번호 재설정 안내 |
| `GET /signup` | `auth/signup.html` | 회원가입 |
| `POST /signup` | - | 계정 생성 요청 |
| `POST /logout` | - | 세션 종료 |
| `GET /mypage` | `mypage/index.html` | 프로필·레이팅·경기 이력·신청 내역·알림 |
| `GET /mypage/profile` | `mypage/profile.html` | 프로필 수정·회원 탈퇴 확인 |
| `POST /mypage/profile` | - | 프로필 저장 |
| `POST /mypage/withdrawal-requests` | - | 회원 탈퇴 요청 생성 |
| `POST /mypage/applications/{applicationId}/cancel` | - | 신청 취소 |
| `POST /mypage/notifications/{notificationId}/read` | - | 알림 읽음 |
| `POST /mypage/notifications/{notificationId}/delete` | - | 알림 삭제 |
| `POST /mypage/notifications/read-all` | - | 알림 전체 읽음 |
| `GET /teams` | `teams/list.html` | 팀 검색·목록 |
| `GET /teams/{clubId}` | `teams/detail.html` | 팀 소개·모집 상태·가입 신청 |
| `GET /teams/manage` | `teams/manage.html` | 내 팀·팀원·받은 가입 신청 관리 |
| `GET /admin/facility` | `admin/facility.html` | 시설 관리자 대시보드와 운영 화면 |
| `GET /admin/operations` | `admin/operations.html` | 매칭방 신청·A/B팀·이용 완료 관리 |
| `GET /admin/system` | `admin/system.html` | 시스템 모니터링·회원·신고·콘텐츠·설정·감사 로그 |
| `GET /terms` | 서버 구현 필요 | 이용약관 |
| `GET /privacy` | 서버 구현 필요 | 개인정보처리방침 |

`/matches`와 `/matches/cards`는 같은 `matches` 데이터 계약을 사용하지만 서로 다른 화면입니다. 두 화면 사이에는 `카드형` ↔ `메인 화면` 전환 링크가 있습니다. `/matches`는 카드 그리드가 기본이며, 세로 목록 레이아웃으로 되돌리지 않습니다.

## 4. 공통 모델과 조각

- `currentMember`: 로그인 회원(`userId`, `nickname`, 필수 `phone`, 표시용 `initial` 등). `null`이면 비로그인입니다.
- `unreadCount`: 읽지 않은 일반 알림 수. 비로그인은 `0`을 제공합니다.
- `_csrf`: Spring Security 사용 시 CSRF 객체입니다. 템플릿은 `_csrf != null`일 때만 hidden input을 렌더링합니다.
- `active`: `siteHeader(active)`의 현재 메뉴 강조 값입니다. `matches`, `teams`, `mypage`, `admin` 중 하나를 전달합니다.
- `styles`: `head(title, description, styles)` 조각에 전달하는 페이지 전용 CSS 파일명 목록입니다.
- 관리자 공통: `adminUser{userId, role, roleLabel, nickname, initial, facilityName}`, `adminNotifications[{notificationId, title, body, time, read, href}]`, `unreadAdminNotificationCount`, `adminNotificationSummary`.
- 일반 사용자 헤더의 로그아웃과 관리자 알림 변경은 모두 `POST` 폼입니다. 링크나 JavaScript로 로그아웃·읽음·삭제를 흉내 내지 않습니다.
- 모델에서 전달하는 이동 URL(`sport.url`, `item.href`, 페이지네이션·캘린더 URL 등)은 애플리케이션 상대 경로 또는 외부 문서 URL이어야 합니다. 템플릿은 `@{...}`로 context path를 적용합니다.

## 5. 화면별 계약

### 5.1 매칭 목록 `/matches`, `/matches/cards`

검색 쿼리 파라미터는 `sportId`, `regionId`, `date`, `hours`(반복)입니다. 검색 폼은 `GET`이며 CSRF가 필요 없습니다.

- `search{sportId, regionId, dateIdx, hours}`
- `sports[{sportId, name, count}]` — 첫 항목은 전체 종목. 실제 종목은 테니스·축구+풋살·족구·농구·배드민턴만 제공합니다.
- `districts[{regionId, name}]`, `dates[{idx, label}]`, `hourOptions[]`
- `selectedHours[]`, `selectedHoursLabel`, `selectedSportName`, `sportImageUrl`
- `activeFilters[{label, url}]`
- `matches[]`, `roomCount`, `recruitingCount`, `matchedCount`, `totalRoomCount` (집계·표시용 필드명은 유지)

매칭방 카드 필드:

`matchId, title, sportName, status, statusLabel, statusClass, facilityDistrictName, levelLabel, dateLabel, time, place, feeText, gender, age, photoUrl, photoAlt, avatarParticipants, extraParticipantCount, participantSummary, remainingLabel, remainingSeats, pendingConfirmationCount, maxParticipants, participantCount, occupiedSeats, occupiedPercent, showTeams, teamACount, teamBCount, canApply, actionLabel, detail`

`status`는 SQL의 모집 중·매칭 완료·종료·취소 상태를 서버가 매핑합니다. `statusClass`는 `is-recruiting`, `is-matched`, `is-completed`, `is-cancelled` 중 서버가 선택해 전달하며 모든 비모집 상태를 매칭 완료로 취급하지 않습니다.

카드 상세 모달 필드:

`detail{badge, title, facilityName, facts[{label, value}], note, facilityVisible, facilityFacts[{label, value}], matched, teamA/teamB[{nickname, host}], teamACount, teamBCount, participants[{nickname, host}], pendingParticipants[{nickname}], canJoin}`

`statusClass`는 SQL의 모집 중·매칭 완료·종료·취소 상태를 각각 표현하도록 서버가 매핑합니다. `feeText`는 `12,000`처럼 숫자와 쉼표만 전달하고 화면에서 `원`을 붙입니다. `occupiedPercent`는 0~100 정수입니다. 카드 상단 사진은 16:9 비율이며, 640px 미만 1열, 640px 이상 2열, 1080px 이상 3열로 표시합니다.

### 5.2 방 상세 `/matches/{matchId}`

- `match{matchId, title, sportName, statusClass, statusLabel, dateLabel, time, place, feeText, maxParticipants, participantCount, confirmedCount, pendingConfirmationCount, occupiedSeats, occupancyPercent, occupancyLabel, occupancyCaption, canApply, hasTeams, note, ratingNote, applyMessage, applyMessageClass, availableSeats, capacityLabel, remainingLabel, levelLabel, facilityName}`
- `match.facts[{label, value}]`
- `match.facility{image, imageAlt, caption, fallbackName, visible, facts[{label, value}]}`
- `match.myApplication{visible, kindLabel, seatsText, applicationId, receiptNo, createdAtText, statusClass, statusLabel, teamLabel, note}` (`applicationId`는 내부 키, `receiptNo`는 사용자 표시용 접수 번호)
- `match.teamA/teamB{averageText, members[{nickname, host, meta}]}`
- `match.pendingParticipants[{nickname}]` — `match_participant.participation_status='대기'`인 확정 대기 참가자
- `match.clubs[{clubId, clubName, leaderName, leaderInitial, leaderCaption, memberCount, averageRatingText, selectionCount, leaderAuto, leaderIsApplicant, members[{clubMemberId, nickname, initial, ratingText, leaderOnly, applicant}]}]` (`clubMemberId`는 `club_member.club_member_id`; `applicant`는 현재 신청 회원인지 표시)
- `currentMember`, `unavailableMessage`

`match.teamA`와 `match.teamB`는 항상 제공하고, 참가자가 없으면 `members`를 빈 목록으로 전달합니다. `pendingConfirmationCount`와 `pendingParticipants`는 `match_participant.participation_status='대기'`인 확정 대기 참가자를 뜻하며 대기열이 아닙니다. 확정 대기가 좌석을 점유하는 시점은 서버 정책으로 정하고, 모든 신청은 정원을 초과할 수 없습니다. `match.myApplication.statusClass`는 서버 상태를 화면 상태 클래스 `is-pending`으로 표현합니다. 레이팅은 서버가 제공하는 별도 표시값 `0.00`~`5.00` 또는 `기록 없음`입니다. 프런트는 MMR이나 0~10 평가점수에서 표시값을 계산하지 않습니다. 현재 SQL의 활성 중복 방지 생성 컬럼은 정확히 `대기`·`확정`을 인식하므로 화면용 `waiting`·`confirmed` 등을 그대로 저장하면 안 됩니다.

참가 선택 폼:

```text
POST /matches/{matchId}/selection
applicationType   individual | team
clubId            팀 참가일 때 club.club_id
clubMemberIds     선택한 club_member.club_member_id(반복)
```

- 로그인 세션이 필요합니다. 비로그인 사용자는 상세만 볼 수 있고 참가 선택 대신 `/login?redirect=/matches/{matchId}` 안내를 봅니다.
- 서버는 선택 내용을 세션에 보관한 뒤 `GET /matches/{matchId}/apply`로 리다이렉트합니다.
- 개인 신청의 신청자와 연락처는 로그인 회원(`APP_USER`)에서 읽고, 클라이언트가 보낸 신청자 ID나 연락처를 신뢰하지 않습니다. 가입 연락처는 필수이며 상세 화면에서는 읽기 전용으로 표시합니다.
- 팀 신청은 현재 선택한 팀의 체크된 `clubMemberIds`만 전송하며, 다른 팀·팀장·현재 신청 회원·개인 신청의 팀 필드는 비활성화됩니다. 서버는 `club_member.club_member_id`를 현재 소속 회원으로 해석해 실제 `app_user.user_id`를 구성하고 팀 소속·중복을 검증해야 합니다.
- 팀 대표가 팀원 목록에도 들어 있을 때 중복 참가자를 제거해야 합니다. 팀 대표 단독 신청 허용 여부는 미확정 정책입니다.
- 남은 좌석보다 인원이 많은 팀 신청은 접수하지 않고 대기 신청으로 전환하지 않습니다.

### 5.3 참가 신청 `/matches/{matchId}/apply`

- `matchId`, `match`, `selection{applicationType, kindLabel, applicantName, applicantPhone, seats, participants[{nickname, roleLabel}]}`. `applicantPhone`은 서버가 로그인 회원에서 읽습니다.
- `canReserve`, `capacityNotice`
- `feeTotalText`, `feeSummaryNote` — 서버가 참가비 필드와 선택 인원을 기준으로 만든 안내 표시값
- `completed`, `completion{title, message, roomTitle, applicantText, participantText, receiptNo, stateLabel, paymentGuidance, totalText, teamAssigned, teamACount, teamBCount}`
- `unavailableMessage`, `error`

신청 폼:

```text
POST /matches/{matchId}/apply
agreeRules          매치 운영 규칙 동의
agreePayment        참가비·취소 안내 확인
```

- 결제 수단 라디오는 안내 UI일 뿐 제출 직전에 비활성화되어 폼에 포함되지 않습니다. `agreePayment`는 안내 확인값입니다. 이 SQL/화면 계약에는 결제 상태나 거래 기록이 없고, 실제 결제·거래 상태 저장은 범위 밖입니다.
- 신청 접수·확정 대기·참가 확정은 서버 상태로 표시합니다. `paymentGuidance`는 안내 문구이며 결제 상태가 아닙니다.
- 정원이 찼거나 선택 인원이 남은 자리보다 많으면 `canReserve=false`와 `capacityNotice`를 제공하고 접수 버튼을 비활성화합니다. 남은 좌석보다 인원이 많은 팀 신청은 대기 신청으로 전환하지 않습니다.
- 접수 결과에는 SQL의 사용자 표시용 `MATCH_APPLICATION.receipt_no`를 `receiptNo`로 전달하고, 내부 `application_id`와 구분합니다. 신청 상태·안내 금액·A/B 배정 여부와 `/mypage#reservations` 경로를 제공합니다. 결제 상태 대신 안내 문구만 표시합니다.

### 5.4 로그인·회원가입

로그인:

- `GET /login`: `redirect`, `email`, `remember`, `error`, `message`
- `POST /login`: `email`, `password`, `remember`, `redirect`
- 테스트 계정·역할 선택 UI는 없습니다. 실제 비밀번호 검증과 세션 고정 방지, 권한 부여는 서버 책임입니다.
- `redirect`는 내부 경로만 허용해야 합니다. 외부 URL·스킴은 서버에서 거부합니다.
- 이미 로그인한 회원은 서버가 마이페이지나 `redirect` 대상으로 이동시킵니다.

회원가입:

- `GET /signup`: `signupForm{role, facilityName, email, phone, nickname, regionId, agreeService, agreePrivacy, marketingOptIn}`, `districts[{regionId, name}]`, `error`, `message`. 서버 오류로 돌아올 때 입력과 동의값을 보존합니다.
- `POST /signup`: `role`, `facilityName`, `adminInviteCode`, `email`, `phone`, `password`, `passwordConfirm`, `nickname`, `regionId`, `agreeService`, `agreePrivacy`, `marketingOptIn`
- `role`은 `member`, `facility`, `system`입니다. 시설 관리자 신청과 시스템 관리자 초대 등록은 서버가 초대 코드·권한을 검증합니다.
- 전화번호는 가입·프로필에서 필수이며 화면은 8~20자의 숫자·공백·`+()-`만 허용합니다. 서버는 같은 형식과 저장 정규화 규칙을 검증합니다. 필수 약관 동의와 비밀번호 확인도 서버에서 다시 검증합니다. 선택 마케팅 동의는 필수가 아닙니다.
- 선호 지역은 한 곳만 선택합니다.

아이디 찾기·비밀번호 찾기는 링크와 안내 경로만 있으며, 실제 본인 확인·재설정 처리는 서버에서 구현해야 합니다.

### 5.5 마이페이지 `/mypage`

쿼리: `recordSport`(선택 종목 ID, 없으면 전체).

- `member{userId, nickname, initial, email, roleLabel, regionText}`
- `summary{representativeSportId, representativeSportName, representativeRatingText, skillNote, otherSportsNote, confirmedCount, completedCount, waitingCount}`
- `selectedSport`, `otherSports`
- `sports[{sportId, name, count, grade, ratingText, hasAverage, meterPercent, note, caption, selected}]`
- `history[]`, `historyFilterLabel`, `historyCount`, `historyTotalCount`, `historyEmptyNote`
- `currentReservations[]`, `pastReservations[]`
- `notifications[{notificationId, type, title, body, time, read, href}]`
- `message`, `unreadCount`

경기 이력 항목:

`id, sportName, date, time, place, title, statusLabel, statusComplete, applicationType, myTeam, ratingText, hasRating, ratingPercent, hasTeamAverages, averageAText, averageBText, facts[{label, value}], note`

신청 내역 항목:

`applicationId, receiptNo, sportName, typeLabel, stateClass, stateLabel, title, metaLine, seatLine, statusNote, canViewPaymentGuidance, paymentGuidanceUrl, canCancel, cancelLabel`

- `currentReservations`와 `pastReservations`는 항상 목록으로 전달합니다. 비어 있으면 화면의 빈 상태 문구를 사용합니다.
- `waitingCount`는 확정 대기 신청 수이며 별도 대기열 수가 아닙니다. 만석·초과 인원 신규 신청은 받지 않습니다.
- 신청 취소·알림 읽음·삭제는 모두 `POST`이며 CSRF hidden input을 포함합니다.
- 개인 레이팅은 `0.00`~`5.00` 두 자리 숫자로 표시하고, 서버 값이 없으면 `기록 없음`으로 표시합니다. 기본값·랜덤값·브라우저 저장값을 만들지 않습니다.
- 개인·팀 평균 레이팅은 SQL 내부 MMR/0~10 평가와 별도인 서버 표시값(0.00~5.00)으로 제공합니다. 프런트는 산식이나 변환을 수행하지 않습니다.
- 개인 레이팅과 매칭방·팀의 실력 단계는 별도 값입니다. 실력 단계는 숫자 `1`~`5`와 `1단계`~`5단계`로만 표시합니다.
- 이용자 상호 평가 기능은 제공하지 않습니다.

### 5.6 프로필 수정·탈퇴 `/mypage/profile`

- `profile{email, nickname, phone, regionId, representativeSportId}` (`phone`은 필수)
- `sports[{sportId, name, ratingText, ratingNote}]`
- `districts[{regionId, name}]`
- `error`, `message`

프로필 폼:

```text
POST /mypage/profile
nickname
phone
regionId
representativeSportId
```

회원 탈퇴 폼:

```text
POST /mypage/withdrawal-requests
nicknameConfirmation   현재 닉네임 확인
```

- 레이팅은 프로필에서 직접 입력·수정할 수 없습니다.
- 프로필 연락처는 가입 계정의 필수 `APP_USER.phone`이며, 매치 신청 화면에서는 읽기 전용으로 표시합니다.
- 탈퇴 폼은 현재 닉네임으로 확인한 뒤 요청을 생성합니다. 이 리소스 패키지에는 실제 계정 삭제 기능이 없으며, 대상 서버도 이 폼 요청만으로 계정을 자동 삭제하지 않아야 합니다.
- 확인 대화상자는 `<dialog>`를 사용하고, ESC·배경 클릭·포커스 복귀를 유지합니다.

### 5.7 팀 `/teams`, `/teams/{clubId}`, `/teams/manage`

팀 목록:

- 쿼리: `sportId`, `q`, `regionId`, `level`, `sort`, `recruiting`
- `sportTabs[{sportId, name, emoji, count, active, url}]`, `districts[{regionId, name}]`, `filters{sportId, query, regionId, level, sort, recruitingOnly}`
- `statistics[{label, value, unit}]`, `clubs[]`, `resultCount`, `message`
- 팀 카드: `clubId, clubName, initials, sportId, sportName, areaLabel, recruiting, statusLabel, level, tags[], memberCount, maxMembers, ratingText, time, footerNote`
- `ratingText`는 서버 계산값 또는 `기록 없음`입니다.

팀 상세:

- `club{clubId, clubName, initials, sportId, sportName, areaLabel, recruiting, level, tags[], memberCount, maxMembers, ratingText, time, activityDays[], activityStartTime, activityEndTime, founded, intro, leaderName, leaderInitials, manager, member, pendingJoinRequestId, recruitPositions[{name, count}], members[{nickname, initials, leader}]}`
- `message`, `applicationError`
- `POST /teams/{clubId}/applications`: `message`
- `POST /teams/{clubId}/applications/{joinRequestId}/cancel`: 가입 신청 취소

팀 관리:

- 쿼리: `clubId`, `tab=team|members|applications`
- `managedClubs[{clubId, clubName, memberCount, active}]`, `managedClub`, `tab`, `sports`, `districts`, `message`, `error`
- `managedClub{clubId, clubName, initials, sportId, sportName, regionId, areaLabel, leader, leaderName, memberCount, maxMembers, recruitStatus, level, activityDays[], activityStartTime, activityEndTime, founded, time, intro, otherMembersRemain, pendingApplicationCount, schedules[{date, dateLabel, title, place, time}], members[{clubMemberId, nickname, initials, handle, joined, leader, current}], applications[{joinRequestId, receiptNo, nickname, handle, date, message, pending, approved, statusLabel}]}`
- 예정 일정은 읽기 전용입니다.

팀 관리 폼:

```text
POST /teams                              clubName, sportId, regionId, level, maxMembers, recruitStatus, activityDays(1..7, 반복), activityStartTime, activityEndTime, intro
POST /teams/{clubId}                      clubName, sportId, regionId, level, maxMembers, recruitStatus, activityDays(1..7, 반복), activityStartTime, activityEndTime, intro
POST /teams/{clubId}/leadership           clubMemberId
POST /teams/{clubId}/members/remove       clubMemberId
POST /teams/{clubId}/leave
POST /teams/{clubId}/disband              clubNameConfirmation
POST /teams/{clubId}/applications/{joinRequestId}/approve
POST /teams/{clubId}/applications/{joinRequestId}/reject
```

- 팀 정보 수정, 팀장 위임, 팀원 추방, 가입 신청 승인·거절은 팀장 권한입니다. 서버가 매 요청마다 권한을 확인해야 합니다.
- 팀 해체는 팀장 외 팀원이 없을 때만 가능하며, 확인창에 팀 이름을 입력해야 합니다.
- `clubName/sportId/regionId`는 `club.club_name/sport_id`와 `club_region.region_id`에, `maxMembers/recruitStatus`는 `club.max_members/recruit_status`에 매핑합니다. 선택 지역은 현재 하나이며, `activityDays`의 숫자(1=월~7=일)는 SQL의 쉼표 구분 `activity_days`로 정규화하고 시각 필드는 `activity_start_time/activity_end_time`에 저장합니다.
- `memberCount`는 `left_at IS NULL`인 활성 `CLUB_MEMBER` 행 수이며 팀장을 포함합니다. 서버는 `maxMembers`가 현재 인원 이상인지, 2~30 범위인지 검증합니다.
- 목록·상세의 `club.time`은 구조화된 요일/시각 필드에서 서버가 만든 표시용 문자열입니다. 현재 화면은 활동 지역을 한 곳만 보내므로 서버는 한 개의 `club_region` 관계로 저장합니다.
- 최대 인원은 2~30명이며 현재 소속 인원보다 작게 변경할 수 없습니다. `recruitStatus`는 `recruiting` 또는 `closed`를 명시 전송합니다. UI의 팀 소개 240자와 가입 메시지 200자는 현재 제품 입력 제한이므로 서버도 검증합니다(SQL 설명 허용 길이는 더 큼).
- 팀 실력 `level`은 SQL `CLUB`에 저장 컬럼이 없으므로 별도 백엔드 저장 계약이 필요합니다. `recruitPositions`와 예정 일정도 이 SQL에 직접 저장 모델이 없어 다른 서비스/모델 출처를 정해야 합니다.
- 폼의 `clubMemberId`는 `club_member.club_member_id`를 가리키며, 서버가 현재 소속 여부를 확인해 해당 `user_id`에 작업을 적용합니다. 소속 이력의 재가입·이탈 상태도 서버가 검증합니다.
- 팀 평균 레이팅은 서버가 제공하는 표시 레이팅을 사용하며, 내부 MMR과 0~10 평가에서 프런트가 환산하지 않습니다.

### 5.8 관리자 화면

관리자 화면은 해시 기반 내부 뷰 전환을 사용합니다. 첫 진입과 필터 검색의 짧은 `view` 값은 JavaScript가 관리자 유형에 맞춰 화면 ID로 변환합니다(예: 시설 `schedule` → `facility-schedule`, 시스템 `users` → `system-users`). 유효한 해시가 있으면 해시가 우선합니다. 서버는 `view` 쿼리와 `adminUser.role`을 검증하고 필요한 데이터만 렌더링합니다. 화면의 버튼 숨김은 권한 통제가 아닙니다.

#### 시설 관리자 `/admin/facility`

- `view`: `dashboard`, `assets`, `schedule`, `bookings`, `requests`, `maintenance`, `stats`, `content`. 시설 선택 쿼리는 `facilityId`입니다.
- `adminUser`, `scopeName`, `selectedFacility`, `facilities[]`, `sports[]`, `hourOptions[]`, `todayLabel`
- 대시보드: `dashboard`, `matches[]`, `notices[]`, `stats`
- 대시보드 표시 필드: `dashboard.matchCount`, `dashboard.recentMatches[]`, 차트의 `bar.matchHeight`. 시설은 `facilityId`·`name`·`concurrentMatchLimit`, 휴관 항목은 `scheduleChangeId`를 사용합니다.
- 시설·일정: `weeklyHours`, `rules[]`, `closures[]`, `calendar{days[], monthLabel, prevUrl, nextUrl}`, `roomEditorHourSlots`, `conditions`
- 예약·요청: `bookingFilters`, `matches[]`, `requestFilters`, `requests[]`, `requestMetrics`
- 점검: `maintenance`, `maintenanceFilters`, `maintenanceMetrics`
- 통계: `stats{bookingCount, participantCount, periodLabel, weekdays[{label, percent, tone}], popularHours[], ...}`, `chartCollectedAt`
- 콘텐츠: `notices[]`, `rules[]`

주요 폼:

```text
POST /admin/facilities                     시설 등록
POST /admin/facilities/{facilityId}        시설 수정(스크립트가 action을 변경)
POST /admin/rooms                          매칭방 생성
POST /admin/facility/schedule/weekly       주간 운영 시간
POST /admin/facility/schedule/date         날짜별 예약 시간
POST /admin/facility/schedule/conditions   예약 조건
POST /admin/facility/schedule/closures     휴관·차단 등록
POST /admin/facility/schedule/closures/{scheduleChangeId}/cancel
POST /admin/facility/requests/{id}/status  status=open|in-progress|done
POST /admin/facility/maintenance           점검 등록
GET  /admin/facility/requests/export       요청 내역 내보내기(서버 구현)
```

시설 등록 폼 필드: `name, address, sports, capacity, concurrentMatchLimit, status`. `sports`는 `테니스`, `축구+풋살`, `족구`, `농구`, `배드민턴` 중 선택한 값을 쉼표로 연결한 문자열입니다. `concurrentMatchLimit`는 `facility.concurrent_match_limit`에 대응합니다. 사람 기준 `capacity`와 SQL 필수 `region_id`, 위도·경도는 현재 폼/DDL 간 매핑이 없어 서버 계약 또는 별도 데이터 출처가 필요합니다. 시설 모델 식별자는 `facilityId`입니다.
매칭방 생성 폼 필드: `title, facilityId, sportId, level, date, hours, teamACapacity, teamBCapacity, feePerPerson, note`. A팀·B팀 좌석 수가 최대 참가 인원을 정하며 최소 인원 필드는 없습니다.
예약 조건 폼 필드: `facilityId, sportId, capacity, deadline, note`. 모집 인원은 최대 인원만 설정합니다.
SQL의 `FACILITY_SPORT.default_min`과 `SOCIAL_MATCH.min_participants`는 필수지만 현재 제품은 최대 인원만 사용합니다. 프런트에 최소 인원 필드를 추가하지 않으며, 대상 백엔드/스키마에서 이 충돌을 해소해야 합니다. 시설 종목별 기본 모집값의 생성 방식도 서버 계약으로 정해야 합니다.

#### 매치 운영 `/admin/operations?matchId={matchId}`

- `match{matchId, title, status, statusLabel, canAssign, applications[{applicationId, receiptNo, ...}], teamA{members[], limit}, teamB{members[], limit}, unassigned[], pendingConfirmationCount, pendingGroups[], ...}`
- 참가자: `person{participantId, nickname, matchSide, ...}`. 화면의 접수 번호는 `receiptNo`; 내부 `application_id`는 관리 요청 경로용입니다.
- 팀 배정 select는 변경 후 저장 버튼을 눌러야 `POST`됩니다. 선택 즉시 제출되지 않습니다.

```text
POST /admin/rooms/{matchId}/applications/{applicationId}/confirm
POST /admin/rooms/{matchId}/applications/{applicationId}/reject    reason(필수)
POST /admin/rooms/{matchId}/participants/{participantId}/team   matchSide=A|B|빈 문자열(미배정)
POST /admin/rooms/{matchId}/complete
```

- 정원 도달 시 서버가 A/B팀을 자동 배정·확정하고, 이용 완료 전에는 운영자 재배정을 허용합니다. 이 정책은 SQL 주석의 `MANUAL` 기본·`AUTO` 추천 설명과 불일치하므로 서버가 `assignment_method` 및 확정 메타데이터 의미를 이 제품 결정에 맞춰 저장해야 합니다.
- 신청 확정·확정 대기·이용 완료 상태는 서버가 검증한 값만 표시합니다. SQL 참가 상태의 `대기`는 확정 대기이며 대기열이 아닙니다. 초과 인원이나 만석 신규 신청은 접수하지 않습니다. 레이팅 처리 상태 `WAITING`과도 구분합니다.
- 이 SQL/화면 계약에는 결제 상태가 없고, 이 화면에서 결제 요청도 발생하지 않습니다.
- 이용 완료 후에는 팀 구성을 잠급니다.

#### 시스템 관리자 `/admin/system`

- `view`: `dashboard`, `errors`, `reports`, `penalties`, `users`, `cms`, `settings`, `audit`
- 모니터링: `dashboard{...}`
- 오류: `errors[]`, `errorFilters`, `errorMetrics`
- 신고: `reports[]`, `reportFilters`, `reportMetrics`, `reportPagination`
- 페널티: `penalties[]`, `penaltyFilters`, `penaltyMetrics`
- 회원: `users[]`, `userFilters`, `userMetrics`, `userPagination`
- 콘텐츠: `cmsNotices[]`, `cmsFaqs[]`, `cmsTerms[]`
- 설정: `settings{reservationsEnabled, signupEnabled, maintenanceBanner, adminTwoFactor, adminSessionMinutes, approvalDelayAlerts, incidentAlerts, dailyDigest, passwordResetNotice, supportEmail, supportHours, timezone, lastChangedLabel}`
- 감사: `auditLogs[]`, `auditFilters`, `auditPagination`

주요 폼:

```text
POST /admin/system/reports/{id}/decision   action, reason, notify
POST /admin/system/penalties/{id}/release
POST /admin/system/users/{id}/approve
POST /admin/system/users/{id}/reject
POST /admin/system/users/{id}/suspend       reason(필수)
POST /admin/system/users/{id}/restore
POST /admin/system/users/role              userId, role, reason
POST /admin/system/settings                설정 필드
GET  /admin/system/users/export
GET  /admin/system/audit/export
GET  /admin/system/errors/export
```

- 신고 조치, 제재, 회원 승인·정지, 역할 변경은 모두 서버 권한 검증과 감사 로그 기록이 필요합니다.
- 계정 정지 요청은 `reason`을 필수로 전송합니다. 사유를 미리 채운 숨김 필드로 대체하지 않습니다.
- 내보내기 링크는 서버 엔드포인트입니다. 브라우저에서 "내보냈습니다" 같은 가짜 성공 메시지를 만들지 않습니다.
- 역할 변경 모달은 `data-user-role` 버튼이 서버가 렌더링한 사용자 정보를 채우고, `POST /admin/system/users/role`로 전송합니다.

#### 관리자 공통 알림

```text
POST /admin/notifications/{id}/read
POST /admin/notifications/{id}/delete
POST /admin/notifications/read-all
```

- `adminNotifications[{notificationId, title, body, time, read, href}]`와 `unreadAdminNotificationCount`를 제공합니다.
- 알림 항목의 `href`는 관련 관리자 화면으로 이동합니다.
- 읽음·삭제는 서버 폼 전송이며, 토스트는 처리 결과 안내에만 사용합니다.

### 5.9 약관·개인정보

`GET /terms`, `GET /privacy`는 서버가 문서를 렌더링해야 합니다. 푸터의 `환불 · 취소 규정`은 `/terms#refund`로 연결됩니다. 준비되지 않은 도움말·제휴·정책 항목은 비활성 버튼과 안내 `title`로 표시하며, 가짜 이동이나 가짜 저장을 수행하지 않습니다.

## 6. 폼·CSRF·권한 규칙

- 상태를 바꾸는 모든 요청은 `POST`입니다. 검색·필터만 `GET`입니다.
- 폼은 `method`와 `th:action="@{...}"`를 명시합니다.
- Spring Security를 사용하는 경우 모든 `POST` 폼에 다음 hidden input을 둡니다.

```html
<input type="hidden" th:if="${_csrf != null}" th:name="${_csrf.parameterName}" th:value="${_csrf.token}" />
```

- 가짜 CSRF 토큰을 만들지 않습니다. `_csrf`가 없으면 서버 설정 문제로 보고, 템플릿에서 임의 값을 생성하지 않습니다.
- 서버는 로그인·회원·팀장·시설 관리자·시스템 관리자 권한을 매 요청마다 확인합니다.
- 화면에서 버튼을 숨기는 것은 보조 수단일 뿐이며, 서버 권한 검사를 대체하지 않습니다.
- 로그아웃, 알림 읽음·삭제, 신청 취소, 프로필 저장, 탈퇴, 팀 변경, 관리자 조치는 모두 서버 폼 전송입니다.

## 7. 표시·상태 규칙

- 개인·팀 평균 레이팅은 `0.00`~`5.00` 두 자리 숫자로 표시합니다. 서버 값이 없으면 `기록 없음`입니다.
- 레이팅을 랜덤 생성하거나 기본값으로 채우지 않습니다. 브라우저 저장값을 읽지 않습니다.
- 매칭방·팀의 실력 단계는 숫자 `1`~`5`와 `1단계`~`5단계`만 사용합니다. 실력 단계와 개인·팀 평균 레이팅은 별도 값이며 화면에서도 구분합니다.
- 정원 또는 남은 좌석보다 선택 인원이 많으면 접수하지 않습니다. 초과 인원 팀을 대기 신청으로 등록하지 않습니다.
- SQL의 `FACILITY_SPORT.default_min`과 `SOCIAL_MATCH.min_participants`는 필수지만 현재 제품은 최대 인원만 사용합니다. 프런트에 최소 인원 필드를 추가하지 않으며, 대상 백엔드/스키마에서 이 충돌을 해소해야 합니다.
- `db/schema.sql`은 `AUTO_INCREMENT`, `DEFAULT`, `CHECK`, 외래 키를 선언합니다. 서버도 권한·상태 전이·중복 신청과 같은 업무 규칙을 검증해야 합니다.
- 신청 접수·확정 대기·참가 확정·이용 완료는 서버 상태로 구분합니다. 이 SQL/화면 계약에는 결제 상태가 없으므로 결제 안내를 결제 완료/미완료 상태처럼 표시하지 않습니다.
- 날짜·시간·금액은 서버가 포맷한 문자열을 우선 사용합니다. 템플릿은 빈 목록과 `null`을 안전하게 처리합니다.
- 빈 상태, 오류 상태, 비로그인 안내, 권한 없음 안내를 유지합니다.

## 8. 서버가 구현해야 할 책임

- 세션 기반 인증, 비밀번호 해시, 로그인 실패 제한, 세션 고정 방지, 안전한 `redirect` 검증
- 회원가입·프로필·탈퇴·관리자 초대 코드 검증과 트랜잭션 처리
- 매칭 검색 조건(모든 선택 시간대 포함), 정원·중복 신청 검증, 신청 취소
- 참가 선택 세션 보관과 신청 저장의 원자적 처리
- 최대 인원·동시 신청의 원자적 검증, 만석/초과 신청 거절, 팀 신청의 A/B팀 자동 확정 배정과 이용 완료 전 운영자 조정, 완료 후 구성 잠금
- SQL 참가 상태 `대기`를 화면의 참가 확정 대기로 매핑하고, 대기열 및 레이팅 처리 상태 `WAITING`과 구분
- 서버 표시 레이팅(0~5) 산출·집계·팀 평균 계산. 내부 MMR 및 0~10 평가에서 프런트는 환산하지 않음
- 팀 생성·수정·팀장 위임·팀원 추방·탈퇴·해체 권한 검증
- 관리자 역할 검증, 신고·제재·회원 상태 변경, 감사 로그 기록
- 알림 생성·읽음·삭제와 관리자 알림 라우팅
- 내보내기 파일 생성과 다운로드 응답
- 약관·개인정보·환불 규정 문서 렌더링
- 사업자 정보와 실제 고객센터 연락처 제공
- CSRF, 보안 헤더, 입력 검증, HTML 이스케이프

## 9. 서버 정책으로 확정해야 할 항목

아래 항목은 화면에 계약만 있고 이 저장소에서 정책을 확정하지 않았습니다. 임의로 구현하지 말고 서비스 정책으로 결정해야 합니다.

- 확정 대기 참가자가 잔여 좌석을 점유하는 시점과 확정·취소 전이
- 화면은 선호 지역과 팀 활동 지역을 한 곳만 선택하지만 `USER_REGION`/`CLUB_REGION`은 다중 행을 허용합니다. 서버는 현재 단일 지역 정책을 검증하거나 대표 지역 규칙을 정해야 합니다.
- 결제 안내 문구의 출처와 안내 금액 산출. SQL에는 결제 안내·결제 상태 저장 모델이 없습니다.
- 팀 신청에서 전체 팀원 배정과 선택 명단 중 적용할 규칙, 팀장 단독 신청 허용 여부 및 신청자 자기 선택 중복 처리
- MMR/평가에서 사용자 표시 레이팅(0~5)을 산출하는 공식·집계 기간·팀 평균 반올림
- 시설 폼의 사람 기준 수용 인원, region·좌표 취득, 시설·종목별 필수 기본값의 출처
- SQL의 `DECIMAL(10,2)` 참가비를 UI의 원화 표시값으로 반올림·정규화하는 기준과 통화
- 날짜별 예약 가능 시간과 `SCHEDULE_CHANGE` 구간·운영 변경 사유의 매핑
- 시설 요청·점검·공지/FAQ·운영 설정·시스템 오류/모니터링 데이터의 저장 위치(API/별도 서비스/별도 스키마)
- `ADMIN_REQUEST.request_id` 기준 심사와 현재 사용자 ID 기반 승인 폼의 매핑
- `facilityName` 문자열로 들어오는 시설 관리자 신청을 기존 `facility_id`로 어떻게 확인·연결할지
- 신고 우선순위, 감사 결과·요청 ID의 저장 위치, 내보내기 파일 형식과 필터 범위
- 가입 동의의 약관 버전·동의 시각 보존 위치(루트 SQL에 동의 이력 모델 없음)
- 팀장 위임·팀원 추방·팀 해체의 세부 조건과 감사 로그 형식
- 로그인·회원가입·계정 찾기 엔드포인트의 최종 URL과 오류 코드, 관리자 역할·시설 배정 방식
- 매칭방 수정·삭제 화면과 엔드포인트(현재 템플릿은 생성만 제공)
- 약관·개인정보 문서의 최종 문구와 버전 관리

현재 `db/schema.sql`은 MySQL 8용 DDL입니다. `app_user`에 대표 종목 필드가 없고, `user_region`/`club_region`은 여러 지역을 허용하지만 현재 화면은 한 지역만 받습니다. 시설의 사람 수 `capacity`, 시설 region/좌표 취득, `facility_sport` 필수 기본값, 매치 안내 `note`, 팀 실력 `level`, 시설 요청·점검·CMS·운영 설정·오류 모델, 신고 우선순위, 감사 결과·요청 ID는 SQL과 화면 사이에 직접 대응되는 저장 계약이 없습니다. 또한 `facility_sport.default_min`·`social_match.min_participants`는 SQL에서 필수지만 현재 제품은 최대 인원만 사용하며, A/B 자동 확정 정책은 SQL의 `MANUAL` 기본·`AUTO` 추천 설명과 다릅니다. 서버/스키마가 이 차이를 해소해야 하며 프런트는 이를 임의로 추정하지 않습니다.

## 10. 디자인·접근성

- 기존 SPO:IT 색상 토큰, 타이포그래피, 얇은 경계선, 플랫한 표면을 유지합니다.
- `/matches`는 16:9 시설 사진이 상단에 오는 카드 그리드입니다. 640px 미만 1열, 640px 이상 2열, 1080px 이상 3열입니다.
- `playon-match-cards.css`는 `/matches`와 `/matches/cards`에서 사용하므로 삭제하지 않습니다.
- 키보드 포커스, `aria-current`, `aria-expanded`, `aria-selected`, 모달 포커스 트랩, ESC 닫기, 배경 클릭 닫기를 유지합니다.
- 모바일 메뉴, 관리자 사이드바, 탭, `<dialog>`의 포커스 복귀 동작을 유지합니다.
- `prefers-reduced-motion`과 공통 포커스 스타일을 약화하지 않습니다.
- 이미지에는 의미 있는 `alt`를 제공하고, 장식 이미지는 `alt=""`와 `aria-hidden="true"`를 사용합니다.

## 11. Spring Boot 이관 방법

1. 이 폴더의 `templates/`와 `static/`을 대상 Spring Boot 프로젝트의 `src/main/resources/`로 복사합니다.
2. `spring-boot-starter-thymeleaf`와 필요한 보안·검증 의존성을 대상 프로젝트에 추가합니다.
3. `README`의 경로 계약에 맞춰 컨트롤러를 만들고, 각 템플릿이 요구하는 모델 속성을 채웁니다.
4. `GET /`는 `/matches`로 리다이렉트합니다. `static/index.html`은 서버 라우팅 전 정적 확인용입니다.
5. 모든 링크·리소스는 `@{...}`를 사용하므로 context path가 달라도 동작합니다.
6. 현재 템플릿은 Tailwind Browser CDN과 Pretendard CDN을 사용합니다. 운영 환경에서는 Tailwind CLI 빌드와 자체 호스팅 적용 여부를 결정합니다.
7. 시설 사진은 저장소에 포함된 이미지를 사용하되, 운영 전 사용 권한을 확인합니다.

## 12. 프런트엔드 검증

저장소 루트에서 Bun으로 실행합니다. 별도 패키지 설치나 빌드 설정 변경은 필요 없습니다.

```text
bun src/test/frontend/check-contracts.mjs
bun src/test/frontend/match-selection.test.mjs
```

- 계약 검사: 15개 화면 스크립트·13개 CSS 구문, 16개 템플릿의 폼·CSRF·정적 파일 참조, 변경된 폼 이름과 JavaScript 선택자를 검사합니다.
- 참가 선택 검사: 개인/팀 단계 전환, 팀장·신청자 중복 제외, 정원 초과 차단, 비활성 입력 제외와 제출값을 가벼운 DOM 대역으로 확인합니다.
- 리팩터링 전 커밋과 비교하려면 두 명령 뒤에 `--baseline=<커밋>`을 붙입니다. 계약 검사는 마크업 구조·스타일/이벤트 연결점·검증 속성·실제 URL 경로를, 참가 선택 검사는 정규화한 동작 결과를 비교합니다.
- 이 검사는 Thymeleaf 엔진이나 실제 브라우저를 실행하지 않습니다. 서버 모델 렌더링·인증·CSRF 검증·DB 저장·모바일 실화면은 Spring Boot 연결 후 별도로 확인해야 합니다.
