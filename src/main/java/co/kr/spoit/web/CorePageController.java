package co.kr.spoit.web;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 주요 화면 템플릿을 렌더링하는 페이지 컨트롤러이다.
 *
 * <p>현재 구현은 조회(GET) 요청에 대해 템플릿이 요구하는 모델 속성을
 * 빈 값·기본값으로 채우고 뷰 이름을 반환하는 데까지만 범위가 한정된다.
 * 회원, 매칭, 팀, 관리자 데이터는 아직 서비스나 매퍼/DB에서 조회하지 않으며,
 * 인증된 사용자 정보도 연결되어 있지 않다. 따라서 여기서 설정하는 값은
 * "데이터가 없는 현재 상태"를 표현하는 계약이지 실제 업무 데이터가 아니다.</p>
 *
 * <p>향후에는 각 핸들러가 Controller → Service → Mapper/DB 흐름으로 실제
 * 데이터를 조회해 모델에 담고, 등록·수정·신청 같은 상태 변경은 별도의
 * POST 핸들러와 서비스 계층에서 처리해야 한다. 이 클래스의 GET 핸들러는
 * 화면 진입점 역할을 유지하는 것이 의도된 방향이다.</p>
 */
@Controller
public class CorePageController {

    /** 데이터가 없는 목록 속성을 공유하기 위한 불변 빈 리스트이다. */
    private static final List<Object> EMPTY_LIST = Collections.emptyList();

    /* ------------------------------------------------------------------
     * 인증 화면: 로그인/회원가입 폼 렌더링만 담당한다.
     * 실제 인증·회원 생성 로직은 아직 없으며, 폼 검증 결과도 전달되지 않는다.
     * ------------------------------------------------------------------ */

    /**
     * 로그인 화면을 렌더링한다.
     *
     * <p>오류/안내 문구와 redirect, 이메일, 자동 로그인 여부는 폼이 참조하는
     * 속성들이지만, 현재는 항상 비어 있는 초기 상태로 전달된다. 인증 처리와
     * 로그인 후 이동 대상 계산은 향후 Security 연동 시 구현해야 한다.</p>
     *
     * @param model 로그인 템플릿에 전달할 모델
     * @return 로그인 뷰 이름
     */
    @GetMapping("/login")
    public String login(Model model) {
        // 검증 실패 메시지나 소셜 로그인 오류가 없는 최초 진입 상태를 표현한다.
        model.addAttribute("error", null);
        model.addAttribute("message", null);
        // 로그인 성공 후 돌아갈 대상과 미리 채울 이메일은 아직 없다.
        model.addAttribute("redirect", null);
        model.addAttribute("email", null);
        model.addAttribute("remember", false);
        return "auth/login";
    }

    /**
     * 회원가입 화면을 렌더링한다.
     *
     * <p>가입 폼 객체와 지역 목록은 아직 서비스/DB에서 준비하지 않으므로
     * {@code null}과 빈 목록으로 전달한다. 템플릿은 빈 목록일 때 선택지가
     * 없는 상태를 정상 렌더링해야 하며, 실제 가입 처리는 향후 POST 핸들러와
     * 서비스 계층에서 담당한다.</p>
     *
     * @param model 회원가입 템플릿에 전달할 모델
     * @return 회원가입 뷰 이름
     */
    @GetMapping("/signup")
    public String signup(Model model) {
        // 폼 바인딩용 객체가 아직 없으므로 null로 두고 템플릿의 빈 폼을 사용한다.
        model.addAttribute("signupForm", null);
        // 지역 선택지는 DB 연동 전까지 제공하지 않는다.
        model.addAttribute("districts", EMPTY_LIST);
        model.addAttribute("error", null);
        model.addAttribute("message", null);
        return "auth/signup";
    }

    /* ------------------------------------------------------------------
     * 마이페이지: 로그인 회원이 아직 연결되지 않아 모든 값이 빈 상태이다.
     * member/summary의 빈 문자열과 0은 템플릿의 널 안전한 표시를 위한 기본값이다.
     * ------------------------------------------------------------------ */

    /**
     * 마이페이지 요약 화면을 렌더링한다.
     *
     * <p>회원 정보, 활동 요약, 경기 기록, 예약, 알림을 모두 빈 상태로
     * 전달한다. 현재 로그인 사용자가 없으므로 {@code member}와 {@code summary}는
     * 빈 문자열/0으로 구성한 맵이며, 실제 값은 향후 인증 주체와
     * 회원·경기·예약 서비스에서 조회해야 한다.</p>
     *
     * @param model 마이페이지 템플릿에 전달할 모델
     * @return 마이페이지 뷰 이름
     */
    @GetMapping("/mypage")
    public String myPage(Model model) {
        // 비로그인/미연동 상태에서도 템플릿이 참조할 수 있는 빈 회원 표현이다.
        model.addAttribute("member", map(
                "initial", "",
                "nickname", "",
                "roleLabel", "",
                "email", "",
                "regionText", ""));
        // 활동 요약도 실제 집계 전이므로 0과 빈 문구로 초기화한다.
        model.addAttribute("summary", map(
                "representativeRatingText", "",
                "representativeSportId", "",
                "representativeSportName", "",
                "skillNote", "",
                "confirmedCount", 0,
                "completedCount", 0,
                "waitingCount", 0,
                "otherSportsNote", ""));
        model.addAttribute("otherSports", EMPTY_LIST);
        model.addAttribute("sports", EMPTY_LIST);
        // 종목 필터의 기본 선택값은 "전체"를 의미하는 all이다.
        model.addAttribute("selectedSport", "all");
        model.addAttribute("historyTotalCount", 0);
        model.addAttribute("historyFilterLabel", "전체 종목");
        model.addAttribute("historyCount", 0);
        model.addAttribute("history", EMPTY_LIST);
        // 기록이 없을 때 템플릿이 보여줄 안내 문구이다.
        model.addAttribute("historyEmptyNote", "확정된 경기 기록이 없습니다.");
        model.addAttribute("currentReservations", EMPTY_LIST);
        model.addAttribute("pastReservations", EMPTY_LIST);
        model.addAttribute("notifications", EMPTY_LIST);
        model.addAttribute("message", null);
        return "mypage/index";
    }

    /**
     * 프로필 수정 화면을 렌더링한다.
     *
     * <p>프로필 객체와 종목·지역 선택지는 아직 조회하지 않으므로 각각
     * {@code null}과 빈 목록으로 전달한다. 저장 처리는 향후 POST 핸들러와
     * 회원 서비스에서 구현해야 한다.</p>
     *
     * @param model 프로필 템플릿에 전달할 모델
     * @return 프로필 뷰 이름
     */
    @GetMapping("/mypage/profile")
    public String profile(Model model) {
        model.addAttribute("profile", null);
        model.addAttribute("sports", EMPTY_LIST);
        model.addAttribute("districts", EMPTY_LIST);
        model.addAttribute("error", null);
        model.addAttribute("message", null);
        return "mypage/profile";
    }

    /* ------------------------------------------------------------------
     * 매칭 화면: 목록/카드/상세/신청 화면을 GET으로 렌더링한다.
     * 검색 조건과 매칭 데이터는 아직 조회하지 않으며, 신청은 실제로 처리되지 않는다.
     * ------------------------------------------------------------------ */

    /**
     * 매칭 목록 화면을 렌더링한다.
     *
     * @param model 매칭 목록 템플릿에 전달할 모델
     * @return 매칭 목록 뷰 이름
     */
    @GetMapping("/matches")
    public String matches(Model model) {
        addMatchListModel(model);
        return "matches/list";
    }

    /**
     * 매칭 카드 화면을 렌더링한다.
     *
     * <p>목록 화면과 동일한 모델 계약을 공유하되 뷰만 카드 형태로 다르다.</p>
     *
     * @param model 매칭 카드 템플릿에 전달할 모델
     * @return 매칭 카드 뷰 이름
     */
    @GetMapping("/matches/cards")
    public String matchCards(Model model) {
        addMatchListModel(model);
        return "matches/cards";
    }

    /**
     * 매칭 상세 화면을 렌더링한다.
     *
     * <p>경로의 {@code matchId}를 받지만 현재는 매칭 조회에 사용하지 않는다.
     * 따라서 {@code match}는 {@code null}이고
     * 항상 "찾을 수 없음" 안내 문구를 함께 전달한다. 향후 서비스 조회 결과에
     * 따라 존재/미존재 상태를 구분해야 한다.</p>
     *
     * @param matchId 경로에서 전달된 매칭 식별자
     * @param model 매칭 상세 템플릿에 전달할 모델
     * @return 매칭 상세 뷰 이름
     */
    @GetMapping("/matches/{matchId}")
    public String matchDetail(@PathVariable long matchId, Model model) {
        // 데이터 연동 전에는 상세 정보를 표시할 수 없음을 알리는 기본 문구이다.
        model.addAttribute("unavailableMessage", "매칭방 정보를 찾을 수 없습니다.");
        model.addAttribute("match", null);
        return "matches/detail";
    }

    /**
     * 매칭 신청 화면을 렌더링한다.
     *
     * <p>현재는 신청 폼을 보여주기만 하며 예약/결제/인원 계산은 수행하지 않는다.
     * {@code completed=false}, {@code canReserve=false}는 아직 신청이 완료되지
     * 않았고 예약 가능 여부도 판단되지 않았음을 나타낸다. 실제 신청 처리는
     * 향후 POST 엔드포인트와 매칭·예약 서비스에서 담당한다.</p>
     *
     * @param matchId 경로에서 전달된 매칭 식별자
     * @param model 매칭 신청 템플릿에 전달할 모델
     * @return 매칭 신청 뷰 이름
     */
    @GetMapping("/matches/{matchId}/apply")
    public String matchApply(@PathVariable long matchId, Model model) {
        model.addAttribute("matchId", matchId);
        // 신청 완료 화면이 아니라 최초 진입 상태임을 나타낸다.
        model.addAttribute("completed", false);
        model.addAttribute("unavailableMessage", "신청 내용을 확인할 수 없습니다.");
        model.addAttribute("match", null);
        model.addAttribute("selection", null);
        // 좌석/잔여 인원을 조회하지 않았으므로 예약 가능으로 단정하지 않는다.
        model.addAttribute("canReserve", false);
        model.addAttribute("capacityNotice", "");
        model.addAttribute("error", null);
        // 요금 계산 결과가 없으므로 빈 문구로 초기화한다.
        model.addAttribute("feeTotalText", "");
        model.addAttribute("feeSummaryNote", "");
        model.addAttribute("completion", null);
        return "matches/join";
    }

    /* ------------------------------------------------------------------
     * 팀/클럽 화면: 목록과 상세, 관리 탭을 렌더링한다.
     * 클럽 데이터와 필터 선택지는 아직 서비스에서 조회하지 않는다.
     * ------------------------------------------------------------------ */

    /**
     * 팀 목록 화면을 렌더링한다.
     *
     * @param model 팀 목록 템플릿에 전달할 모델
     * @return 팀 목록 뷰 이름
     */
    @GetMapping("/teams")
    public String teams(Model model) {
        model.addAttribute("sportTabs", EMPTY_LIST);
        model.addAttribute("statistics", EMPTY_LIST);
        // 필터 폼 객체가 아직 없으므로 템플릿이 기본 필터 UI를 사용한다.
        model.addAttribute("filters", null);
        model.addAttribute("districts", EMPTY_LIST);
        model.addAttribute("resultCount", 0);
        model.addAttribute("clubs", EMPTY_LIST);
        return "teams/list";
    }

    /**
     * 팀 상세 화면을 렌더링한다.
     *
     * <p>클럽을 조회하지 않으므로 {@code club}은 {@code null}이며, 템플릿은
     * 미존재 상태를 처리해야 한다. 향후 클럽 서비스 연동 지점이다.</p>
     *
     * @param clubId 경로에서 전달된 클럽 식별자
     * @param model 팀 상세 템플릿에 전달할 모델
     * @return 팀 상세 뷰 이름
     */
    @GetMapping("/teams/{clubId}")
    public String teamDetail(@PathVariable long clubId, Model model) {
        model.addAttribute("club", null);
        return "teams/detail";
    }

    /**
     * 팀 관리 화면을 렌더링한다.
     *
     * <p>기본 탭은 "team"이며, 관리 권한 확인이나 소유 클럽 조회는 아직 없다.
     * 따라서 {@code managedClubs}는 빈 목록이고 {@code managedClub}은
     * {@code null}이다. 향후 인증 주체 기준의 권한 검사와 클럽 조회가 필요하다.</p>
     *
     * @param model 팀 관리 템플릿에 전달할 모델
     * @return 팀 관리 뷰 이름
     */
    @GetMapping("/teams/manage")
    public String teamManage(Model model) {
        // 관리 화면 진입 시 첫 번째 탭을 기본으로 선택한다.
        model.addAttribute("tab", "team");
        model.addAttribute("managedClubs", EMPTY_LIST);
        model.addAttribute("managedClub", null);
        model.addAttribute("sports", EMPTY_LIST);
        model.addAttribute("districts", EMPTY_LIST);
        return "teams/manage";
    }

    /* ------------------------------------------------------------------
     * 관리자 화면: 공통 관리자 셸(addAdminShell) 위에 화면별 빈 모델을 얹는다.
     * 관리자 인증/권한 검사는 아직 없으며, 모든 통계·목록은 빈 상태이다.
     * ------------------------------------------------------------------ */

    /**
     * 시설 관리 화면을 렌더링한다.
     *
     * <p>대시보드, 시설, 운영 시간, 휴무, 예약, 요청, 정비, 공지, 규칙 등
     * 여러 탭이 하나의 템플릿에서 사용하는 속성을 한 번에 초기화한다.
     * 모든 목록은 빈 목록, 집계/선택 객체는 {@code null}이며, 기본 보기는
     * {@code dashboard}이다. 실제 데이터 조회와 탭별 필터링은 향후 서비스
     * 연동 시 구현해야 한다.</p>
     *
     * @param model 시설 관리 템플릿에 전달할 모델
     * @return 시설 관리 뷰 이름
     */
    @GetMapping("/admin/facility")
    public String adminFacility(Model model) {
        addAdminShell(model);
        // 탭 전환은 서버 상태가 아니라 뷰의 기본 선택값으로만 사용된다.
        model.addAttribute("view", "dashboard");
        model.addAttribute("scopeName", null);
        model.addAttribute("todayLabel", null);
        model.addAttribute("chartCollectedAt", null);
        model.addAttribute("dashboard", null);
        model.addAttribute("facilities", EMPTY_LIST);
        model.addAttribute("selectedFacility", null);
        model.addAttribute("weeklyHours", EMPTY_LIST);
        model.addAttribute("hourOptions", EMPTY_LIST);
        model.addAttribute("calendar", null);
        model.addAttribute("sports", EMPTY_LIST);
        model.addAttribute("conditions", null);
        model.addAttribute("closures", EMPTY_LIST);
        model.addAttribute("matches", EMPTY_LIST);
        model.addAttribute("bookingFilters", null);
        model.addAttribute("stats", null);
        model.addAttribute("requestMetrics", null);
        model.addAttribute("requestFilters", null);
        model.addAttribute("requests", EMPTY_LIST);
        model.addAttribute("maintenanceMetrics", null);
        model.addAttribute("maintenanceFilters", null);
        model.addAttribute("maintenance", EMPTY_LIST);
        model.addAttribute("notices", EMPTY_LIST);
        model.addAttribute("rules", EMPTY_LIST);
        model.addAttribute("roomEditorHourSlots", EMPTY_LIST);
        return "admin/facility";
    }

    /**
     * 운영 관리 화면을 렌더링한다.
     *
     * <p>현재는 관리자 셸과 {@code match=null}만 전달하며, 운영 대상 매칭을
     * 조회하거나 상태를 변경하지 않는다. 상태 변경은 향후 POST 업무 흐름으로
     * 분리되어야 한다.</p>
     *
     * @param model 운영 관리 템플릿에 전달할 모델
     * @return 운영 관리 뷰 이름
     */
    @GetMapping("/admin/operations")
    public String adminOperations(Model model) {
        addAdminShell(model);
        model.addAttribute("match", null);
        return "admin/operations";
    }

    /**
     * 시스템 관리 화면을 렌더링한다.
     *
     * <p>모니터링 대시보드와 오류/신고/제재/사용자/CMS/설정/감사 로그 탭이
     * 요구하는 속성을 빈 값으로 초기화한다. 대시보드 맵의 0과 {@code false}는
     * "수집된 데이터 없음"과 "정상 상태로 단정할 수 없음"을 표현하는
     * 기본값이며, 실제 지표나 목록은 향후 모니터링·관리 서비스에서 조회해야
     * 한다. 페이징 객체가 {@code null}인 것도 조회 결과가 없기 때문이다.</p>
     *
     * @param model 시스템 관리 템플릿에 전달할 모델
     * @return 시스템 관리 뷰 이름
     */
    @GetMapping("/admin/system")
    public String adminSystem(Model model) {
        addAdminShell(model);
        model.addAttribute("view", "dashboard");
        model.addAttribute("dashboard", map(
                "collectedAt", null,
                "apiStatus", null,
                "apiUptime", null,
                "cpuPercent", 0,
                "cpuPeak", null,
                "memoryPercent", 0,
                "memoryCapacity", null,
                "diskPercent", 0,
                "recentErrorCount", 0,
                "recentErrorAttention", null,
                // 상태를 확인하지 않았으므로 전체 정상(true)으로 표시하지 않는다.
                "allHealthy", false,
                "bars", EMPTY_LIST,
                "services", EMPTY_LIST,
                "recentErrors", EMPTY_LIST,
                "recentReports", EMPTY_LIST));
        model.addAttribute("errorMetrics", null);
        model.addAttribute("errorFilters", null);
        model.addAttribute("errors", EMPTY_LIST);
        model.addAttribute("reportMetrics", null);
        model.addAttribute("reportFilters", null);
        model.addAttribute("reports", EMPTY_LIST);
        model.addAttribute("reportPagination", null);
        model.addAttribute("penaltyMetrics", null);
        model.addAttribute("penaltyFilters", null);
        model.addAttribute("penalties", EMPTY_LIST);
        model.addAttribute("userMetrics", null);
        model.addAttribute("userFilters", null);
        model.addAttribute("users", EMPTY_LIST);
        model.addAttribute("userPagination", null);
        model.addAttribute("cmsNotices", EMPTY_LIST);
        model.addAttribute("cmsFaqs", EMPTY_LIST);
        model.addAttribute("cmsTerms", EMPTY_LIST);
        model.addAttribute("settings", null);
        model.addAttribute("auditFilters", null);
        model.addAttribute("auditLogs", EMPTY_LIST);
        model.addAttribute("auditPagination", null);
        return "admin/system";
    }

    /**
     * 매칭 목록/카드 화면이 공유하는 검색·요약 모델을 구성한다.
     *
     * <p>검색 조건 맵은 선택된 종목/지역/날짜가 없는 상태를 {@code null}로,
     * 시간 필터는 빈 목록으로 표현한다. 각 카운트가 0인 것은 실제 집계 결과가
     * 아니라 조회 전 기본값이다. 향후에는 요청 파라미터를 검색 조건 객체로
     * 바인딩하고 매칭 서비스에서 목록과 집계를 조회해 이 값들을 대체해야 한다.</p>
     *
     * @param model 매칭 목록 계열 템플릿에 전달할 모델
     */
    private void addMatchListModel(Model model) {
        // 검색 폼이 참조하는 조건들을 "미선택" 상태로 초기화한다.
        model.addAttribute("search", map(
                "sportId", null,
                "regionId", null,
                "dateIdx", null,
                "hours", EMPTY_LIST));
        model.addAttribute("sports", EMPTY_LIST);
        model.addAttribute("districts", EMPTY_LIST);
        model.addAttribute("dates", EMPTY_LIST);
        model.addAttribute("hourOptions", EMPTY_LIST);
        model.addAttribute("selectedHours", EMPTY_LIST);
        model.addAttribute("selectedHoursLabel", null);
        model.addAttribute("selectedSportName", "");
        model.addAttribute("sportImageUrl", null);
        // 전체/표시/모집/매칭 건수는 조회 전이므로 모두 0이다.
        model.addAttribute("totalRoomCount", 0);
        model.addAttribute("roomCount", 0);
        model.addAttribute("recruitingCount", 0);
        model.addAttribute("matchedCount", 0);
        model.addAttribute("activeFilters", EMPTY_LIST);
        model.addAttribute("matches", EMPTY_LIST);
    }

    /**
     * 관리자 화면들이 공통으로 사용하는 셸 속성을 구성한다.
     *
     * <p>현재 관리자 인증이 없으므로 {@code adminUser}는 {@code null}이고,
     * 알림 목록/건수/요약도 비어 있다. 템플릿은 관리자 정보가 없는 상태를
     * 전제로 렌더링해야 하며, 향후 인증 주체와 관리자 알림 서비스가 이 값을
     * 채우는 지점이 된다.</p>
     *
     * @param model 관리자 템플릿에 전달할 모델
     */
    private void addAdminShell(Model model) {
        model.addAttribute("adminUser", null);
        model.addAttribute("adminNotifications", EMPTY_LIST);
        model.addAttribute("unreadAdminNotificationCount", 0);
        model.addAttribute("adminNotificationSummary", "");
    }

    /**
     * 키/값을 번갈아 받아 순서가 보존된 맵을 만든다.
     *
     * <p>템플릿에 전달할 소규모 기본값 묶음을 간결하게 표현하기 위한 내부
     * 헬퍼이다. 키와 값은 반드시 쌍으로 전달해야 하며, 인자 개수가 홀수이면
     * 마지막 키의 값을 읽는 과정에서 {@link ArrayIndexOutOfBoundsException}이
     * 발생한다. 키가 {@link String}이 아니면 {@link ClassCastException}이
     * 발생하고, 같은 키가 다시 나오면 기존 값을 덮어쓰되 최초 삽입 위치는
     * 유지된다. 실제 데이터 모델이 도입되면 DTO나 서비스 반환 객체로 대체하는
     * 것이 바람직하다.</p>
     *
     * @param entries 키({@link String})와 값이 번갈아 나열된 가변 인자
     * @return 입력 순서를 유지하는 새 {@link LinkedHashMap}
     * @throws ArrayIndexOutOfBoundsException 키/값 쌍이 맞지 않을 때
     * @throws ClassCastException 키가 {@link String}이 아닐 때
     */
    private static Map<String, Object> map(Object... entries) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            values.put((String) entries[i], entries[i + 1]);
        }
        return values;
    }
}
