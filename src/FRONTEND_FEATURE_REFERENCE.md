# 기능별 SQL·프런트엔드 연동 분석

이 파일은 HTML 문서의 기능별 설명을 위한 기술 근거입니다. 사용자가 읽을 문서는 `main/resources/static/docs/index.html`입니다.

## 공통 기준

- **화면 계약:** 현재 HTML·JS에 있는 URL·폼·표시값입니다. 실제 컨트롤러 구현을 의미하지 않습니다.
- **SQL 사실:** `db/schema.sql`에 실제 선언된 컬럼·키·제약입니다. 주석만 있는 상태값은 CHECK 강제와 다릅니다.
- **구현 권고:** 아래 트랜잭션·잠금·멱등성·에러 처리 방법은 구현 시 검토할 설계이지, 이미 구현된 기능이 아닙니다.
- **결정 필요:** 화면과 SQL 사이에 저장 위치·상태 정책이 정해지지 않은 항목입니다. 임의의 테이블·API가 있다고 설명하지 않습니다.
- FK 삭제는 기본 RESTRICT입니다. `deleted_at`을 가진 엔티티의 논리 삭제, 소속 이력의 `left_at`, 알림의 삭제 정책은 서로 다릅니다.
- MySQL CHECK는 식이 UNKNOWN(NULL)인 경우도 통과할 수 있습니다. nullable 컬럼이 섞인 복합 CHECK를 완전한 업무 검증으로 해석하지 않습니다. 예를 들어 OBSERVED인데 score가 NULL인 요청은 서버에서 명시적으로 거부해야 합니다.
- 권한은 URL의 역할뿐 아니라 본인 소유·팀장·담당 시설·대상 리소스 상태까지 확인합니다. CSRF는 이 검사를 대체하지 않습니다.

## 08.1 회원·인증·프로필

### 화면과 요청

| 기능 | 실제 화면·요청 | 입력·모델 |
| --- | --- | --- |
| 가입 | `auth/signup.html`, GET/POST `/signup` | `role`, `facilityName`, `adminInviteCode`, `email`, `phone`, `password`, `passwordConfirm`, `nickname`, `regionId`, `agreeService`, `agreePrivacy`, `marketingOptIn`; `signupForm`, `districts`, `error`, `message` |
| 로그인 | `auth/login.html`, GET/POST `/login` | `email`, `password`, `remember`, `redirect`; Security의 usernameParameter는 email |
| 로그아웃 | 공통 헤더의 POST `/logout` | CSRF 외 별도 업무 필드 없음 |
| 프로필 | `mypage/profile.html`, GET/POST `/mypage/profile` | `nickname`, `phone`, `regionId`, `representativeSportId`; `profile`, `sports`, `districts` |
| 탈퇴 요청 | POST `/mypage/withdrawal-requests` | `nicknameConfirmation`. 즉시 계정 삭제 아님 |
| 마이페이지 | GET `/mypage?recordSport=...` | `member`, `summary`, `sports`, `history`, `currentReservations`, `pastReservations`, `notifications` |

### SQL 연결과 보장 범위

- `app_user`(SQL 24~35): `user_id` PK·AUTO_INCREMENT, `email` UNIQUE, `password_hash`, `nickname`, `phone`, `role`, `status`, `created_at`. role/status는 VARCHAR이며 허용값 CHECK가 없습니다.
- `user_region`(52~58): `(user_id, region_id)` 복합 PK와 FK. SQL은 여러 선호 지역을 허용하지만 화면은 한 곳만 선택합니다.
- `region`(44~50): `parent_region_id` 자기 FK. 같은 지역명 금지·계층 깊이·순환 방지는 DB 제약에 없습니다.
- `sport`(37~42): 종목명 UNIQUE. 기준 데이터는 별도 준비해야 합니다.
- `sport_rating`·`sport_rating_history`는 마이페이지 조회 원천 후보지만, 내부 MMR을 0~5 표시값으로 바꾸는 공식은 SQL에 없습니다.

### 권장 처리 순서

1. 가입 DTO를 검증하고 약관 필수 동의·전화번호·비밀번호 확인·역할 허용값을 확인합니다. 사용자 입력 role을 바로 권한으로 저장하지 않습니다.
2. PasswordEncoder로 password를 해시하여 password_hash에 저장합니다. 해시를 화면 DTO·로그에 넣지 않습니다.
3. 사용자와 선택 지역 연결을 같은 트랜잭션으로 처리합니다. 사전 이메일 중복 검사 후에도 UNIQUE 충돌을 정상적인 가입 오류로 변환합니다.
4. 관리 권한 요청은 초대/심사 정책을 검증합니다. `admin_request`와 `facility_manager` 연결은 08.6 참고.
5. 프로필 변경은 로그인 본인의 user_id로만 처리합니다. 지역을 한 곳으로 유지한다면 기존 연결 교체와 새 연결 삽입을 한 트랜잭션으로 처리합니다.

### 결정 필요와 검증

- 대표 종목, 마케팅·약관 버전/동의 시각, 비밀번호 재설정 토큰, 탈퇴 요청의 전용 저장 구조가 없습니다. 저장소·보존 정책을 정해야 합니다.
- 아이디/비밀번호 찾기 링크는 있지만 현재 프런트엔드에 해당 처리 화면·인증 흐름은 없습니다.
- app_user에는 deleted_at이 없습니다. 탈퇴 요청 수신만으로 사용자 물리 삭제나 임의의 상태값 변경을 구현했다고 가정하지 않습니다.
- 검증: 동시 이메일 가입 충돌, 미동의 요청, 관리 권한 조작, 타인 프로필 수정, 지역 미선택, 로그인 성공 후 외부 redirect 차단.

## 08.2 매치 탐색·선택·신청·취소

### 화면과 요청

| 기능 | 실제 요청 | 입력·모델 |
| --- | --- | --- |
| 검색 | GET `/matches`, `/matches/cards` | `sportId`, `regionId`, `date`, 반복 `hours`; `search`, `matches`, 종목·지역·날짜·시간 선택지·집계 |
| 상세 | GET `/matches/{matchId}` | `match`, `currentMember`, `unavailableMessage`; `match.clubs`, A/B 배정·대기 참가자 |
| 선택 보관 | POST `/matches/{matchId}/selection` | `applicationType`(허용값 `individual`, `team`), `clubId`, 반복 `clubMemberIds` |
| 신청 확인·접수 | GET/POST `/matches/{matchId}/apply` | POST `agreeRules`, `agreePayment`; `selection`, `canReserve`, `completed`, `completion`, `feeTotalText` |
| 취소 | POST `/mypage/applications/{applicationId}/cancel` | 업무 필드 없음. 로그인 소유권·취소 가능 상태 검증 |

### SQL 연결과 핵심 제약

- `social_match`(221~265): `match_id`, 시설·종목 조합 FK, `start_at`, `end_at`, `min_participants`, `max_participants`, `team_a_capacity`, `team_b_capacity`, `fee_per_person`, `required_skill`, 상태·배정·결과·레이팅 처리 정보.
- 기간은 end_at>start_at, 최소 인원≥1, 최대≥최소, A/B 정원 합계=최대, 각 팀 정원≥0, 요금≥0 CHECK입니다. 이는 실제 신청 수가 정원 안인지까지 보장하지 않습니다.
- `match_application`(267~286): `application_id`, `match_id`, `applicant_user_id`, nullable `club_id`, `application_type`, `status`, UNIQUE `receipt_no`, 확인·취소 시각/처리자.
- `match_participant`(288~308): `participant_id`, `application_id`, `match_id`, `user_id`, `participation_status`, nullable `match_side`, `entry_mmr`, 취소 시각. `(application_id, match_id)` 복합 FK는 신청과 경기의 일치를 확인합니다.
- **중요:** 생성 컬럼 active_match_user는 participation_status가 정확히 **`대기` 또는 `확정`**일 때 user_id를 반환합니다. UNIQUE `(match_id, active_match_user)`가 활성 중복 참가를 막습니다. 영어 `waiting`/`confirmed`를 그대로 저장하면 이 방어가 적용되지 않습니다.
- 신청·참가 상태 자체에는 허용값 CHECK가 없습니다. 문서/화면 상태와 DB 저장 상태를 서버에서 명시적으로 매핑합니다. `rating_status='WAITING'`은 다른 목적의 상태입니다.

### 조회·서버 처리 분석

1. 검색의 all·빈값을 정규화하고 date를 dates[].idx에 대응시켜 search.dateIdx로 복원합니다. 시설 날짜별 설정의 date 문자열과 구분합니다.
2. facility·sport·region 조인과 시간 구간 조건을 구성합니다. 선택 hours가 여러 개면 모두 포함되는 방을 검색해야 합니다. idx 자체를 DB 날짜로 해석하지 않습니다.
3. 선택 단계는 서버 세션에 본인·matchId와 함께 저장합니다. clubMemberIds는 user_id가 아니라 소속 이력 ID이므로 활성 소속 회원으로 조회한 후 실제 참가자 user_id를 구성합니다.
4. 신청 시 경기 행 잠금 등으로 동시 신청을 직렬화하는 방법을 검토합니다. 관련 모든 신청·취소·확정 경로에서 잠금 순서가 일관돼야 합니다.
5. 최신 모집 상태·남은 좌석·중복·팀 소속을 확인하고 match_application 1행과 실제 참가자별 match_participant를 한 트랜잭션으로 생성합니다. 접수번호 충돌·활성 참가 UNIQUE 충돌 시 전체 롤백합니다.
6. 취소도 신청과 참가자 상태/취소 시각을 함께 변경해야 합니다. 신청만 취소하고 활성 참가자를 남기면 좌석·중복 검사가 어긋납니다. 재전송은 이중 취소나 이중 알림 없이 처리하도록 설계합니다.

### 결정 필요와 검증

- 확정 대기가 좌석을 점유하는 시점은 제품 정책입니다. SQL의 활성 중복 방지와 좌석 계산은 별개입니다.
- 팀장과 신청자 중복 제거, 팀 대표 단독 신청 허용, 정원 도달 시 배정은 서버 정책입니다. UI 비활성만으로 보장되지 않습니다.
- 신청 화면의 결제 수단은 안내 UI입니다. 결제 상태·거래 테이블이나 실결제 요청이 구현돼 있지 않습니다.
- 검증: 마지막 한 자리에 동시 요청, 개인/팀 중복 회원, 소속이 끝난 clubMemberId, 재전송, 취소 후 재신청, receiptNo와 applicationId 혼동.

## 08.3 동호회·가입·팀장

### 화면과 요청

| 기능 | 요청 | 필드 |
| --- | --- | --- |
| 목록 | GET `/teams` | `sportId`, `q`, `regionId`, `level`, `sort`, `recruiting` |
| 상세·관리 | GET `/teams/{clubId}`, `/teams/manage` | 관리 쿼리 `clubId`, `tab`(허용값 `team`, `members`, `applications`); `club`, `managedClub`, `managedClubs` |
| 가입·취소 | POST `/teams/{clubId}/applications`; POST `/teams/{clubId}/applications/{joinRequestId}/cancel` | 가입 `message`; 취소는 업무 필드 없음 |
| 생성·수정 | POST `/teams`; POST `/teams/{clubId}` | `clubName`, `sportId`, `regionId`, `level`, `maxMembers`, `recruitStatus`, 반복 `activityDays`, `activityStartTime`, `activityEndTime`, `intro` |
| 승인·거절 | POST `/teams/{clubId}/applications/{joinRequestId}/approve` 또는 `/reject` | 업무 필드 없음 |
| 위임·추방 | POST `/teams/{clubId}/leadership`, `/members/remove` | `clubMemberId` |
| 탈퇴·해체 | POST `/teams/{clubId}/leave`, `/disband` | 해체만 `clubNameConfirmation` |

### SQL 연결

- `club`(133~151): PK club_id, sport_id FK, club_name, intro, max_members, recruit_status, 활동 요일/시각, deleted_at. 최대 인원 2~30 CHECK, 모집 상태는 정확히 `모집중`/`마감` CHECK입니다. 화면의 recruiting/closed를 변환해야 합니다.
- `club_region`(153~159): 복합 PK/FK. SQL은 복수 지역, 화면은 단일 선택입니다.
- `hashtag`·`club_hashtag`(161~177): 태그명 UNIQUE, 연결 복합 PK, sort_order는 1~5 CHECK. 같은 팀의 표시 순서 중복은 DB가 막지 않습니다. 현재 생성/수정 폼에는 태그 입력이 없습니다.
- `club_member`(179~198): PK club_member_id, club_id/user_id FK, role=`동호회장`/`동호회원` CHECK. 활성은 left_at IS NULL, 이탈 시 leave_type과 kick_reason 조합을 검증합니다. 재가입은 새 이력 행입니다.
- `club_join_request`(200~216): PK request_id를 화면에서는 joinRequestId로 사용합니다. 상태 CHECK는 `대기중`/`승인`/`거절`/`취소`. 대기중이면 processed_at NULL, 처리 상태면 NOT NULL이어야 합니다.

### 처리·경합 분석

1. 팀 생성은 club, 단일 지역 연결, 생성자의 동호회장 소속 행을 한 트랜잭션으로 생성합니다. 활성 팀명 중복은 DB UNIQUE가 없으므로 서버 정책으로 처리합니다.
2. 가입 요청은 모집 상태·현재 활성 소속·기존 대기 신청을 검증합니다. 동일 회원-팀의 활성 소속/대기 신청 중복 UNIQUE가 없어 동시 요청을 별도로 방어해야 합니다.
3. 가입 승인은 팀 행 등 일관된 잠금 기준으로 정원·신청 상태를 재조회하고, club_member 삽입·신청 상태/processed_at 갱신을 함께 처리합니다. 승인 버튼을 두 번 눌러도 소속 행이 두 개 생기면 안 됩니다.
4. 팀장 위임은 현재 팀장의 권한과 대상 활성 소속을 확인한 후 기존/새 팀장 역할을 함께 변경합니다. DB는 팀당 활성 팀장 1명이나 최소 1명을 강제하지 않습니다.
5. 탈퇴/추방은 소속 행을 삭제하지 않고 left_at·leave_type·필요한 사유를 기록합니다. 추방 사유는 현재 폼에 없어 수집·정책이 필요합니다. kick_reason의 NOT NULL이 SQL로 항상 강제된다고 단정하지 않습니다.
6. 해체는 현재 UI 정책상 팀장 외 회원이 없어야 하며 팀명 확인 후 club.deleted_at을 기록합니다. FK를 고려해 소속 이력을 무조건 물리 삭제하지 않습니다.

### 공백과 검증

- 팀 실력 level, 모집 포지션, 예정 일정은 현재 SQL에 직접 대응 저장 모델이 없습니다. 화면에 보인다고 컬럼이 있는 것은 아닙니다.
- 활동 요일은 1~7 쉼표 문자열 CHECK이지만 중복 요일·활동 종료>시작을 모두 보장하지 않습니다. 서버 검증이 필요합니다.
- UI 소개 240자·가입 메시지 200자는 SQL 허용 500자보다 좁은 현재 입력 계약입니다. 명칭 정리만 하면서 입력 제한을 바꾸지 않습니다.
- 검증: 동시 승인 마지막 자리, 이미 탈퇴한 소속 ID, 팀장 위임 중 해체, 중복 요일, 타 팀 joinRequestId, 처리 완료 신청 재승인.

## 08.4 시설·일정·매치 생성

### 화면과 요청

| 기능 | 요청 | 필드·모델 |
| --- | --- | --- |
| 시설 조회 | GET `/admin/facility` | `view`, `facilityId`; `facilities`, `selectedFacility`, `adminUser`, `calendar`, `weeklyHours` 등 |
| 시설 생성·수정 | POST `/admin/facilities`, `/admin/facilities/{facilityId}` | `name`, `address`, `sports`(종목명 쉼표 문자열), `capacity`, `concurrentMatchLimit`, `status` |
| 주간 시간 | POST `/admin/facility/schedule/weekly` | `facilityId`, 반복 `openDays`, `start-{day}`, `end-{day}` |
| 날짜별 시간 | POST `/admin/facility/schedule/date` | `facilityId`, `date`, 반복 `hours`, `note` |
| 모집 조건 | POST `/admin/facility/schedule/conditions` | `facilityId`, `sportId`, `capacity`, `deadline`, `note` |
| 휴관·취소 | POST `/admin/facility/schedule/closures`; POST `/admin/facility/schedule/closures/{scheduleChangeId}/cancel` | 생성: `facilityId`, `date`, `start`, `end`, `reason` |
| 매치 생성 | POST `/admin/rooms` | `facilityId`, `sportId`, `title`, `date`, 반복 `hours`, `teamACapacity`, `teamBCapacity`, `feePerPerson`, `level`, `note` |
| 시설 요청 | GET `/admin/facility?view=requests`; POST `/admin/facility/requests/{id}/status`; GET `/admin/facility/requests/export` | 검색 q/status, 변경 status=open/in-progress/done. SQL 전용 테이블 없음 |
| 점검 일지 | POST `/admin/facility/maintenance` | `date`, `area`, `category`, `status`, `result`, `owner`. 전용 저장 테이블 없음 |

### SQL 연결

- `facility`(63~78): PK facility_id, region_id FK, name, address, latitude, longitude, concurrent_match_limit, status, deleted_at. 좌표 범위와 동시 한도≥1 CHECK.
- `facility_manager`(80~87): 시설-사용자 복합 PK/FK. 관리자 역할만이 아니라 담당 시설 관계를 확인합니다.
- `facility_sport`(89~101): 시설-종목 복합 PK. default_min/default_max/default_fee/default_skill. 최소≥1, 최대≥최소, 요금≥0 CHECK.
- `operating_rule`(103~113): operating_rule_id, facility_id, weekday, open_time, close_time, is_open. 요일 1~7 CHECK. 시설-요일 UNIQUE와 시간 순서 CHECK은 없습니다.
- `schedule_change`(115~128): schedule_change_id, facility_id, created_by, start_at/end_at, change_type, reason, status. 종료>시작 CHECK. 변경 종류·상태의 허용값 및 중복 구간 제약은 없습니다.
- `social_match`는 시설-종목 조합을 facility_sport 복합 FK로 참조합니다. 지원 종목 연결을 먼저 준비해야 합니다.

### 변환·트랜잭션 분석

1. 시설 폼의 종목명 목록을 sport 기준 데이터와 매핑하고 시설·담당자·종목별 기본값 저장을 일관되게 처리합니다. 없는 종목·중복 종목은 검증합니다.
2. 닫힌 요일의 start/end는 disabled여서 미전송됩니다. openDays와 요일별 동적 필드로 주간 규칙을 구성하고, 같은 시설·요일 중복 규칙의 유지/교체 정책을 정합니다.
3. date+hours 또는 date+start/end를 실제 일시 구간으로 변환합니다. 시간대·종료 경계·비연속 블록을 명확히 정하고 운영 시간·휴관 예외와 겹치는지 검사합니다.
4. 새 매치를 만들기 전에 최신 운영 가능 구간과 겹치는 매치 수를 확인합니다. 동시 생성에도 concurrent_match_limit를 넘지 않게 시설 단위 잠금 등 동일한 직렬화 기준을 검토합니다. 단순 SELECT COUNT 후 INSERT만으로는 동시성을 보장하지 않습니다.
5. A/B 정원 합계를 max_participants로 구성합니다. min_participants, required_skill, created_by 등 필수값을 서버에서 정한 뒤 저장합니다. 프런트 feePerPerson은 BigDecimal로 검증하고 원화 표시와 구분합니다.

### 결정 필요와 검증

- 시설의 사람 수 capacity는 facility 컬럼이 아닙니다. concurrentMatchLimit와 동일하게 저장하면 안 됩니다. region_id·위도·경도도 현재 폼에 없습니다.
- 날짜별 선택 시간을 schedule_change의 개방/폐쇄 구간으로 어떻게 나타낼지, note와 deadline의 저장 위치, 주간/날짜별 예외 우선순위를 정해야 합니다.
- 시설 요청·점검 결과·공지/이용 수칙의 전용 테이블은 없습니다. schedule_change는 운영 예외 구간 테이블이지 점검 일지 전체의 자동 대체물이 아닙니다.
- 별도 예약 테이블은 없습니다. 현재 시설 예약 표시는 social_match의 일시와 참가 현황을 기반으로 구성할 수 있으나 실제 정책을 정해야 합니다.
- 검증: 타 시설 ID 조작, 날짜 경계와 휴관 겹침, 동일 시간 동시 생성, 지원하지 않는 종목, 모든 요일 닫기 요청, 비연속 시간 블록.

## 08.5 A/B 배정·경기 완료

| 기능 | 요청 | 필드 |
| --- | --- | --- |
| 운영 화면 | GET `/admin/operations?matchId=...` | 루트 `match`와 하위 `match.applications`, `match.teamA`, `match.teamB`, `match.unassigned` 등 |
| 확정·반려 | POST `/admin/rooms/{matchId}/applications/{applicationId}/confirm` 또는 `/reject` | 반려만 reason 필수 |
| 배정 | POST `/admin/rooms/{matchId}/participants/{participantId}/team` | `matchSide`(허용값 `A`, `B`, 빈 문자열) |
| 이용 완료 | POST `/admin/rooms/{matchId}/complete` | 업무 필드 없음 |

- 관련 SQL: social_match·match_application·match_participant(SQL 221~315). 화면 접수번호 receiptNo와 내부 applicationId·participantId는 서로 다른 키입니다.
- match_side는 SQL에서 NULL 또는 A/B CHECK입니다. 미배정 빈 문자열은 NULL로 정규화해야 합니다. 선택 변경만으로 저장되지 않고 저장 버튼의 POST가 있어야 합니다.
- 배정 시 참가자가 같은 matchId 소속인지, 확정/취소 상태, 대상 팀 정원, 담당 시설 권한을 검사합니다. 여러 명을 배정하면 팀별 인원 검사와 변경을 같은 트랜잭션으로 처리합니다.
- social_match.assignment_method는 MANUAL/AUTO CHECK이며 기본값 MANUAL입니다. 정원 도달 시 자동 배정·완료 후 잠금은 현재 화면 정책이고 DB가 자동 수행하는 기능이 아닙니다. 자동 배정 알고리즘과 확정 메타데이터 기준은 서비스가 정합니다.
- captain_a_participant_id/captain_b_participant_id의 복합 FK는 같은 경기 참가자임을 검증하고 두 값의 동일 여부를 제한합니다. 실제 A/B 소속·활성 상태·팀장 권한은 서버 검증입니다.
- 이용 완료 폼은 서버의 match.status가 matched일 때 표시되고 버튼은 서버가 제공한 match.bothTeamsAssigned가 참일 때 활성화됩니다. 템플릿이 양 팀 명단을 직접 세지는 않습니다. 화면 안내는 양 팀에 한 명 이상 배정되어야 한다고 설명하지만, 해당 모델 값의 산출과 전체 확정 참가자 배정·팀 정원 등 완료 요건은 서버에서 검증해야 합니다.
- 이용 완료는 승무패 확정이나 레이팅 반영과 다릅니다. result_status/final_result/rating_status를 자동으로 갱신한다고 가정하지 않습니다.
- 반려 reason의 전용 저장 컬럼은 match_application에 없습니다. UI에서 사유를 받으므로 기록 위치·보존·노출 정책을 정해야 합니다.
- 검증: 다른 경기 participantId, 완료 후 변경, 동일 팀으로 재저장, 동시 팀 이동의 정원 초과, 신청만 확정하고 참가자 상태를 갱신하지 않은 경우.

## 08.6 신고·제재·권한·CMS

### 화면과 요청

| 기능 | 요청·뷰 | 필드 |
| --- | --- | --- |
| 신고 조회·처리 | GET `/admin/system?view=reports`; POST `/admin/system/reports/{id}/decision` | 조회 q/status/priority; 처리 action/reason/notify |
| 제재 해제 | POST `/admin/system/penalties/{id}/release` | 업무 필드 없음 |
| 회원 승인·거절 | POST `/admin/system/users/{id}/approve`, `/reject` | 현재 경로 키는 userId |
| 회원 정지·복구 | POST `/admin/system/users/{id}/suspend`, `/restore` | 정지만 reason 필수 |
| 역할 변경 | POST `/admin/system/users/role` | userId/role/reason |
| 시스템 설정 | POST `/admin/system/settings` | reservationsEnabled, signupEnabled, maintenanceBanner, adminTwoFactor, adminSessionMinutes, approvalDelayAlerts, incidentAlerts, dailyDigest, passwordResetNotice, supportEmail, supportHours, timezone |
| CMS | view=cms | cmsNotices/cmsFaqs/cmsTerms 읽기 표시만. 작성/편집 POST 없음 |
| 오류·회원·감사 내보내기 | GET `/admin/system/errors/export`, `/users/export`, `/audit/export` | 실제 서버 파일 생성 필요 |

### SQL 연결과 서버 책임

- `report`(439~456): report_id, nullable reporter_user_id, target_user_id, nullable match_id/reviewed_by, category, summary, status, resolution, created_at/reviewed_at. 상태값/전이는 CHECK가 아닙니다. 신고 우선순위는 컬럼이 없습니다.
- `report_evidence`(458~467): 신고 FK, evidence_type, reference_code, summary. 실제 업로드 파일 저장 테이블이라고 단정하지 않습니다.
- `penalty`(469~489): penalty_id, target_user_id, report_id, issued_by, penalty_type, start_at/end_at, status, reason, released_by/released_at. 종료는 시작 후 또는 NULL, 해제자/해제시각은 둘 다 NULL 또는 둘 다 값인 CHECK입니다.
- `admin_request`(513~526): request_id, user_id, nullable facility_id, requested_role, status, reviewed_by, created_at/reviewed_at. 승인 시 사용자 역할이나 담당 시설이 자동 갱신되는 트리거는 없습니다.
- `audit_log`(528~539): 작업자·행위·대상·상세 기록. 신고 처리·권한 변경 이력을 저장할 수 있는 구조이며, 실제 기록은 서비스 구현이 필요합니다.

### 처리·공백 분석

1. action은 UI에서 warn/suspend-3/suspend-7/suspend-permanent/dismiss를 보냅니다. 이를 report.resolution, penalty 종류·기간·상태로 어떻게 변환할지 서비스가 정의합니다. UI action 문자열을 모든 상태 컬럼에 그대로 저장하지 않습니다.
2. 권한과 신고 현재 상태를 확인하고 신고 결론·필요한 제재·감사 로그·알림 생성의 원자적 처리 범위를 정합니다. 중복 판단 요청으로 제재가 여러 개 생기지 않게 합니다.
3. 제재 해제는 이미 해제됐는지 확인하고 released_by/released_at를 같이 기록합니다. 제재가 로그인/신청을 차단하는 방식과 app_user.status 동기화는 별도 업무 규칙입니다.
4. 현재 회원 승인 URL은 userId 기반입니다. admin_request.request_id와 동일한 키가 아닙니다. 어떤 미처리 신청을 심사하는지 조회·단일성·권한을 정해야 합니다. 시설 민원 requests[]도 admin_request와 다른 개념입니다.
5. 시스템 설정, CMS, 오류 모니터링의 전용 저장 테이블은 없습니다. settings DTO에 값이 있다고 자동으로 DB에 저장되지는 않습니다. 읽기 전용 CMS에 새 POST가 존재하는 것처럼 설명하지 않습니다.
6. 일반 사용자 신고 접수 화면·경로는 현재 템플릿에 없습니다. 관리자 신고 검토 UI와 report 저장 구조만 확인됩니다.

검증: 미허용 role 승격, 타 시설 권한 승인, 다른 관리자의 동시 제재 해제, 신고 처리 재전송, 빈 reason, unchecked notify, 개인정보 포함 내보내기 권한·마스킹.

## 08.7 알림·감사 로그

| 기능 | 현재 요청·모델 | SQL 연결 |
| --- | --- | --- |
| 개인 알림 | notifications, unreadCount; POST `/mypage/notifications/{notificationId}/read`, `/delete`, `/mypage/notifications/read-all` | notification.recipient_user_id로 소유권 확인 |
| 관리자 알림 | adminNotifications, unreadAdminNotificationCount; POST `/admin/notifications/{id}/read`, `/delete`, `/admin/notifications/read-all` | 동일 notification 저장소를 사용할 수 있으나 수신자/역할 범위 검증 필요 |
| 감사 조회 | GET `/admin/system?view=audit`, q/action/period; auditLogs | audit_log 및 사용자 조인·화면 DTO |
| 감사 내보내기 | GET `/admin/system/audit/export` | 조회 권한과 개인정보 범위 적용 |

- `notification`(491~511): notification_id PK, recipient_user_id 필수 FK; application_id/match_id/report_id/join_request_id 선택 FK; notification_type/title/body, created_at/read_at/delivered_at.
- 미읽음은 read_at IS NULL로 표현할 수 있습니다. 개별 읽음은 본인 수신자 조건으로 UPDATE하고 전체 읽음도 본인·미읽음 범위에 제한합니다. 재전송 시 읽음시각 보존 여부를 정합니다.
- 알림에 deleted_at이 없습니다. 모든 테이블을 무조건 물리 삭제하지 않는다는 기본 안내와 충돌하지 않도록 삭제를 물리 삭제·숨김·별도 보존 중 어떻게 구현할지 정해야 합니다.
- delivered_at은 전달 시각을 저장할 뿐 이메일·WebSocket 발송·재시도·전달 보장을 구현하지 않습니다. 같은 업무 이벤트의 중복 알림 방지를 위한 고유 이벤트 키도 없습니다.
- 업무 저장과 DB 알림 삽입을 함께 처리할지, 외부 전달 실패는 outbox 등으로 분리할지 결정합니다. outbox는 현재 SQL에 없는 설계 제안입니다.
- `audit_log`(528~539): audit_log_id, nullable actor_user_id, action, target_type, 문자열 target_id, detail, created_at. 대상 레코드 FK가 아니며 append-only나 변경 불가도 DB가 강제하지 않습니다.
- 감사 detail에 비밀번호·초대 코드·세션 토큰 등을 남기지 않습니다. 감사 로그 보존·변경 권한·삭제 정책을 정합니다. 화면 log.requestId 같은 추적 표시는 SQL audit_log_id와 다릅니다.
- 검증: 타인 알림 ID, 전체 읽음 범위, 반복 삭제/읽음, 없는 대상 href, 접근 불가한 관리자 링크, 업무 롤백 때 알림/감사가 남는지.

## 08.8 결과·평가·레이팅 — 스키마 전용

**현재 결과 제출·평가 입력 화면과 전송 경로는 없습니다. 마이페이지의 레이팅·경기 이력은 읽기 표시입니다. 다음 내용은 SQL 확장 구조 분석이며 새 기능이 구현됐다는 뜻이 아닙니다.**

| 테이블 | 실제 SQL 구조·보장 | 서버에서 정할 사항 |
| --- | --- | --- |
| match_result_submission (317~329) | submission_id, match_id, captain_participant_id, reported_result, submission_status. 결과 CHECK는 A_WIN/DRAW/B_WIN; 같은 경기 참가자 FK | 실제 팀장 권한, 중복 제출·수정 정책. 경기-팀장 UNIQUE 없음 |
| sport_evaluation_item (334~348) | 종목별 item_id, item_code, item_name, weight, display_order, is_active; scale_max=10 CHECK, weight≥0 | 코드/순서 중복·가중치 합계·항목 변경 시 과거 평가 해석 |
| match_evaluation (350~371) | 평가자·대상은 같은 경기 참가자 FK, 자기 평가 금지 CHECK, 점수 NULL 또는 0~10, scale_max=10 | 같은 경기 종목 일치·확정 참가 여부·중복 평가·평가 완료 조건. 평가자-대상 UNIQUE 없음 |
| match_evaluation_score (373~389) | (evaluation_id,item_id) PK; 평가·평가항목의 sport_id 일치 복합 FK. CHECK는 OBSERVED의 0~10 점수 또는 NOT_OBSERVED의 NULL 조합을 검사하나 nullable 식의 UNKNOWN 통과 가능 | OBSERVED의 NULL을 서버에서 거부; 미관찰 항목의 분모·가중치와 종합 점수 산출 |
| sport_rating (391~401) | (user_id,sport_id) PK, current_mmr 기본1000, rated_match_count 기본0 및 ≥0 | 화면 0~5 표시값 변환, 초기 사용자 표시 원칙 |
| sport_rating_history (403~434) | participant_id UNIQUE, MMR 전후·Elo/평가 변화·실제/기대 점수·팀 평균·알고리즘 버전. final_delta=elo_delta+evaluation_delta, mmr_after=mmr_before+final_delta CHECK | 동일 경기 재반영·정정·알고리즘 버전 및 반올림 정책 |
| social_match 결과/레이팅 필드 | final_result는 NULL/A_WIN/DRAW/B_WIN; rating_status WAITING/READY/APPLIED/EXCLUDED CHECK. result_status는 CHECK 없음 | 결과 합의·이의제기·검토·반영 가능 상태 전이 |

권장 연결 순서(미구현): 경기 완료 조건 확인 → 팀장 제출/결과 검토 → 최종 결과 확정 → 평가 집계 정책 적용 → 레이팅 이력·현재 MMR·rated_match_count·rating_status를 한 트랜잭션으로 반영합니다. 실제 산식은 SQL에 없습니다.

참가자당 이력 UNIQUE는 중복 반영의 방어선입니다. 현재 MMR만 바꾸거나 APPLIED만 먼저 기록하면 이력이 어긋날 수 있으므로 실패 시 전체 롤백과 재실행의 멱등성을 설계합니다. 재계산은 새 history를 무조건 추가하면 UNIQUE 충돌이 나므로 별도 정정 정책이 필요합니다.

검증: 팀장이 아닌 제출자, 양 팀 결과 충돌, 같은 대상 반복 평가, 자기 평가, 미관찰 점수 입력, 다른 종목 평가 항목, 동시 레이팅 작업, 반영 중 실패 후 재실행, 0~10 평가·내부 MMR·0~5 표시 레이팅의 혼동.

## 근거 파일

- `db/schema.sql` 24~58: 회원·종목·지역.
- 63~128: 시설·담당자·종목·시간·예외.
- 133~216: 팀·태그·소속·가입 신청.
- 221~329: 경기·신청·참가자·결과 제출.
- 334~434: 평가·레이팅.
- 439~539: 신고·제재·알림·관리자 신청·감사.
- `src/main/resources/templates/auth/`, `matches/`, `teams/`, `mypage/`, `admin/`, `fragments/`: 현재 화면 계약. HTTP 경로의 실제 컨트롤러는 아직 별도 구현 대상입니다.
