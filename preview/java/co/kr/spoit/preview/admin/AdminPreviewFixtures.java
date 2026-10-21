package co.kr.spoit.preview.admin;

import static co.kr.spoit.preview.admin.AdminCommonModels.AdminNotificationView;
import static co.kr.spoit.preview.admin.AdminCommonModels.AdminUserView;
import static co.kr.spoit.preview.admin.FacilityPreviewModels.BookingFiltersView;
import static co.kr.spoit.preview.admin.FacilityPreviewModels.CalendarDayView;
import static co.kr.spoit.preview.admin.FacilityPreviewModels.CalendarView;
import static co.kr.spoit.preview.admin.FacilityPreviewModels.ClosureView;
import static co.kr.spoit.preview.admin.FacilityPreviewModels.FacilityDashboardView;
import static co.kr.spoit.preview.admin.FacilityPreviewModels.FacilityRequestView;
import static co.kr.spoit.preview.admin.FacilityPreviewModels.FacilityRowView;
import static co.kr.spoit.preview.admin.FacilityPreviewModels.FacilityStatsView;
import static co.kr.spoit.preview.admin.FacilityPreviewModels.HourSlotView;
import static co.kr.spoit.preview.admin.FacilityPreviewModels.HourlyBarView;
import static co.kr.spoit.preview.admin.FacilityPreviewModels.MaintenanceEntryView;
import static co.kr.spoit.preview.admin.FacilityPreviewModels.MaintenanceFiltersView;
import static co.kr.spoit.preview.admin.FacilityPreviewModels.MaintenanceMetricsView;
import static co.kr.spoit.preview.admin.FacilityPreviewModels.MatchRowView;
import static co.kr.spoit.preview.admin.FacilityPreviewModels.NoticeView;
import static co.kr.spoit.preview.admin.FacilityPreviewModels.PopularHourView;
import static co.kr.spoit.preview.admin.FacilityPreviewModels.RecentRequestView;
import static co.kr.spoit.preview.admin.FacilityPreviewModels.RequestFiltersView;
import static co.kr.spoit.preview.admin.FacilityPreviewModels.RequestMetricsView;
import static co.kr.spoit.preview.admin.FacilityPreviewModels.RoomHourSlotView;
import static co.kr.spoit.preview.admin.FacilityPreviewModels.RuleView;
import static co.kr.spoit.preview.admin.FacilityPreviewModels.ScheduleConditionView;
import static co.kr.spoit.preview.admin.FacilityPreviewModels.SportView;
import static co.kr.spoit.preview.admin.FacilityPreviewModels.WeekdayStatView;
import static co.kr.spoit.preview.admin.FacilityPreviewModels.WeeklyHourView;
import static co.kr.spoit.preview.admin.OperationsPreviewModels.MatchApplicationView;
import static co.kr.spoit.preview.admin.OperationsPreviewModels.MatchOperationsView;
import static co.kr.spoit.preview.admin.OperationsPreviewModels.PendingGroupView;
import static co.kr.spoit.preview.admin.OperationsPreviewModels.RosterPersonView;
import static co.kr.spoit.preview.admin.OperationsPreviewModels.TeamView;
import static co.kr.spoit.preview.admin.SystemPreviewModels.AuditFiltersView;
import static co.kr.spoit.preview.admin.SystemPreviewModels.AuditLogView;
import static co.kr.spoit.preview.admin.SystemPreviewModels.CmsFaqView;
import static co.kr.spoit.preview.admin.SystemPreviewModels.CmsNoticeView;
import static co.kr.spoit.preview.admin.SystemPreviewModels.CmsTermView;
import static co.kr.spoit.preview.admin.SystemPreviewModels.DashboardBarView;
import static co.kr.spoit.preview.admin.SystemPreviewModels.ErrorFiltersView;
import static co.kr.spoit.preview.admin.SystemPreviewModels.ErrorLogView;
import static co.kr.spoit.preview.admin.SystemPreviewModels.ErrorMetricsView;
import static co.kr.spoit.preview.admin.SystemPreviewModels.EvidenceView;
import static co.kr.spoit.preview.admin.SystemPreviewModels.PaginationView;
import static co.kr.spoit.preview.admin.SystemPreviewModels.PenaltyFiltersView;
import static co.kr.spoit.preview.admin.SystemPreviewModels.PenaltyMetricsView;
import static co.kr.spoit.preview.admin.SystemPreviewModels.PenaltyView;
import static co.kr.spoit.preview.admin.SystemPreviewModels.RecentErrorView;
import static co.kr.spoit.preview.admin.SystemPreviewModels.ReportFiltersView;
import static co.kr.spoit.preview.admin.SystemPreviewModels.ReportHistoryView;
import static co.kr.spoit.preview.admin.SystemPreviewModels.ReportMetricsView;
import static co.kr.spoit.preview.admin.SystemPreviewModels.ReportRowView;
import static co.kr.spoit.preview.admin.SystemPreviewModels.ServiceStatusView;
import static co.kr.spoit.preview.admin.SystemPreviewModels.SystemDashboardView;
import static co.kr.spoit.preview.admin.SystemPreviewModels.SystemSettingsView;
import static co.kr.spoit.preview.admin.SystemPreviewModels.UserFiltersView;
import static co.kr.spoit.preview.admin.SystemPreviewModels.UserMetricsView;
import static co.kr.spoit.preview.admin.SystemPreviewModels.UserView;

import java.util.List;

public final class AdminPreviewFixtures {

    private AdminPreviewFixtures() {
    }

    public static AdminUserView facilityAdmin() {
        return new AdminUserView("facility", "SPO:IT 강남 테니스센터", "시", "시설 관리자", "시설 관리자");
    }

    public static AdminUserView systemAdmin() {
        return new AdminUserView("system", null, "S", "시스템 관리자", "시스템 관리자");
    }

    public static int unreadAdminNotificationCount() {
        return 2;
    }

    public static String adminNotificationSummary() {
        return "알림 3건 · 미확인 2건";
    }

    public static List<AdminNotificationView> adminNotifications() {
        return List.of(
                new AdminNotificationView(1L, false, "예약", "오늘 19:00 매칭방 확정", "참가 인원이 정원에 도달해 A/B팀 배정이 완료되었습니다.", "10분 전"),
                new AdminNotificationView(2L, false, "시설", "정기 점검 일정 안내", "내일 09:00–11:00 코트 2면 점검이 예정되어 있습니다.", "1시간 전"),
                new AdminNotificationView(3L, true, "문의", "민원 답변 등록 완료", "접수된 시설 이용 문의에 답변이 등록되었습니다.", "어제"));
    }

    public static FacilityDashboardView facilityDashboard() {
        return new FacilityDashboardView(
                12,
                4,
                3,
                2,
                List.of(
                        new HourlyBarView(45, 25, "08시"),
                        new HourlyBarView(70, 40, "12시"),
                        new HourlyBarView(90, 55, "18시"),
                        new HourlyBarView(60, 35, "20시")),
                List.of(
                        new RecentRequestView("red", "야간 조명 점검 요청", "코트 A · 접수 20분 전", "20분 전"),
                        new RecentRequestView("amber", "라커룸 청결 상태 확인", "시설 관리 · 접수 1시간 전", "1시간 전")),
                List.of(
                        matchRow(1L, "강남 저녁 테니스 복식", "테니스", "4 / 4명", "2026-10-21 19:00", "good", "모집 완료"),
                        matchRow(2L, "주말 풋살 3:3", "풋살", "5 / 6명", "2026-10-24 10:00", "warn", "모집 중")));
    }

    public static List<FacilityRowView> facilities() {
        return List.of(
                new FacilityRowView(1L, "SPO:IT 강남 테니스센터", "서울 강남구 테헤란로 123", "테니스, 배드민턴", 120, 6, "good", "운영 중"),
                new FacilityRowView(2L, "SPO:IT 송파 풋살파크", "서울 송파구 올림픽로 88", "축구+풋살, 족구", 80, 4, "warn", "점검 중"));
    }

    public static List<String> hourOptions() {
        return List.of("06:00", "07:00", "08:00", "09:00", "10:00", "11:00", "12:00", "13:00",
                "14:00", "15:00", "16:00", "17:00", "18:00", "19:00", "20:00", "21:00", "22:00");
    }

    public static List<WeeklyHourView> weeklyHours() {
        return List.of(
                new WeeklyHourView("mon", "월요일", true, "09:00", "22:00"),
                new WeeklyHourView("tue", "화요일", true, "09:00", "22:00"),
                new WeeklyHourView("wed", "수요일", true, "09:00", "22:00"),
                new WeeklyHourView("thu", "목요일", true, "09:00", "22:00"),
                new WeeklyHourView("fri", "금요일", true, "09:00", "22:00"),
                new WeeklyHourView("sat", "토요일", true, "08:00", "20:00"),
                new WeeklyHourView("sun", "일요일", false, "08:00", "18:00"));
    }

    public static CalendarView calendar() {
        return new CalendarView(
                "/admin/facility?view=schedule&facilityId=1&month=2026-09",
                "/admin/facility?view=schedule&facilityId=1&month=2026-11",
                "2026년 10월",
                "2026-10-21",
                "2026년 10월 21일 (수)",
                "주간 운영 시간을 기준으로 표시합니다.",
                12,
                "09:00–12:00, 18:00–22:00",
                "야간 조명 점검으로 20시 이후 이용이 제한됩니다.",
                "선택한 12시간 동안 예약 가능한 매칭방은 3개입니다.",
                List.of(
                        new CalendarDayView(18, "/admin/facility?view=schedule&date=2026-10-18", "2026년 10월 18일", true, false, false, null, null),
                        new CalendarDayView(20, "/admin/facility?view=schedule&date=2026-10-20", "2026년 10월 20일", false, false, false, null, null),
                        new CalendarDayView(21, "/admin/facility?view=schedule&date=2026-10-21", "2026년 10월 21일", false, true, false, "시간 지정", null),
                        new CalendarDayView(22, "/admin/facility?view=schedule&date=2026-10-22", "2026년 10월 22일", false, false, true, null, "휴관"),
                        new CalendarDayView(23, "/admin/facility?view=schedule&date=2026-10-23", "2026년 10월 23일", false, false, false, null, null)),
                List.of(
                        new HourSlotView("09:00", "09:00 예약 가능", true, false, false, null),
                        new HourSlotView("10:00", "10:00 예약 가능", true, false, false, null),
                        new HourSlotView("11:00", "11:00 예약 가능", true, false, false, null),
                        new HourSlotView("12:00", "12:00 운영 시간 아님", false, false, true, "운영 시간 아님"),
                        new HourSlotView("18:00", "18:00 예약 가능", true, false, false, null),
                        new HourSlotView("19:00", "19:00 예약 가능", true, false, false, null),
                        new HourSlotView("20:00", "20:00 점검으로 차단", false, true, true, "점검")));
    }

    public static List<SportView> sports() {
        return List.of(
                new SportView(1L, "테니스"),
                new SportView(2L, "풋살"),
                new SportView(3L, "배드민턴"));
    }

    public static ScheduleConditionView conditions() {
        return new ScheduleConditionView(1L, 4, 6, "실내 운동화와 개인 라켓을 준비해 주세요.");
    }

    public static List<ClosureView> closures() {
        return List.of(
                new ClosureView(11L, "2026-10-22 (목)", "SPO:IT 강남 테니스센터", "09:00–11:00", "정기 조명 점검", "warn", "예정"),
                new ClosureView(12L, "2026-10-27 (화)", "SPO:IT 강남 테니스센터", "18:00–22:00", "배수로 보수 공사", "neutral", "완료"));
    }

    public static List<MatchRowView> matches() {
        return List.of(
                matchRow(1L, "강남 저녁 테니스 복식", "테니스", "4 / 4명", "2026-10-21 19:00", "good", "모집 완료"),
                matchRow(2L, "주말 풋살 3:3", "풋살", "5 / 6명", "2026-10-24 10:00", "warn", "모집 중"),
                matchRow(3L, "배드민턴 초급 더블스", "배드민턴", "4 / 4명", "2026-10-25 14:00", "neutral", "이용 완료"));
    }

    public static BookingFiltersView bookingFilters() {
        return new BookingFiltersView("", "all", "all");
    }

    public static FacilityStatsView facilityStats() {
        return new FacilityStatsView(
                30,
                128,
                342,
                "수요일",
                "19:00–20:00",
                "최근 30일",
                List.of(
                        new WeekdayStatView("월", "good", 14),
                        new WeekdayStatView("화", "good", 18),
                        new WeekdayStatView("수", "good", 24),
                        new WeekdayStatView("목", "warn", 20),
                        new WeekdayStatView("금", "warn", 16),
                        new WeekdayStatView("토", "neutral", 5),
                        new WeekdayStatView("일", "neutral", 3)),
                List.of(
                        new PopularHourView("09:00", 12, 35),
                        new PopularHourView("18:00", 28, 80),
                        new PopularHourView("19:00", 34, 100),
                        new PopularHourView("20:00", 21, 62)));
    }

    private static MatchRowView matchRow(Long matchId, String title, String sportName, String participation,
            String dateLabel, String statusTone, String statusLabel) {
        return new MatchRowView(matchId, title, "SPO:IT 강남 테니스센터", sportName, participation, dateLabel,
                statusTone, statusLabel);
    }

    public static RequestMetricsView requestMetrics() {
        return new RequestMetricsView(3, 2, 11, "2.4");
    }

    public static RequestFiltersView requestFilters() {
        return new RequestFiltersView("", "all");
    }

    public static List<FacilityRequestView> facilityRequests() {
        return List.of(
                new FacilityRequestView(101L, "야간 조명이 너무 어둡습니다.", "김민수", "2026-10-21 18:10", null, "open", "warn", "접수"),
                new FacilityRequestView(102L, "라커룸 온수 공급 확인 요청", "이서연", "2026-10-21 15:30", "박지훈", "in-progress", "neutral", "확인 중"),
                new FacilityRequestView(103L, "주차장 안내 표지판 보수", "정하늘", "2026-10-20 11:05", "박지훈", "done", "good", "처리 완료"));
    }

    public static MaintenanceMetricsView maintenanceMetrics() {
        return new MaintenanceMetricsView(2, 1, 7, 1);
    }

    public static MaintenanceFiltersView maintenanceFilters() {
        return new MaintenanceFiltersView("all");
    }

    public static List<MaintenanceEntryView> maintenance() {
        return List.of(
                new MaintenanceEntryView("2026-10-22 09:00", "코트 A·B", "정기 안전 점검", "박지훈", "warn", "예정", "네트 장력과 조명 상태를 확인할 예정입니다."),
                new MaintenanceEntryView("2026-10-20 14:00", "라커룸", "배수·위생 점검", "이서연", "good", "완료", "배수구 청소와 환기 팬 작동을 확인했습니다."),
                new MaintenanceEntryView("2026-10-19 10:30", "코트 C", "바닥·네트 점검", "박지훈", "neutral", "조치 필요", "코트 라인 일부 재도색이 필요합니다."));
    }

    public static List<NoticeView> notices() {
        return List.of(
                new NoticeView("10월 시설 정기 점검 안내", "good", "게시 중", "2026-10-20 ~ 2026-10-31", "시스템 관리자", "2026-10-20 09:00"),
                new NoticeView("야간 이용 수칙 변경 안내", "good", "게시 중", "2026-10-01 ~ 상시", "시설 관리자", "2026-10-01 13:20"),
                new NoticeView("추석 연휴 운영 시간", "neutral", "임시 저장", "2026-09-24 ~ 2026-09-27", "시설 관리자", "2026-09-18 16:40"));
    }

    public static List<RuleView> rules() {
        return List.of(
                new RuleView("실내 운동화 착용 및 음식물 반입 금지", "이용 수칙", "good", "게시 중", "2026-10-05 10:00"),
                new RuleView("코트 예약 취소 및 노쇼 기준", "예약 정책", "good", "게시 중", "2026-09-28 15:30"));
    }

    public static List<RoomHourSlotView> roomEditorHourSlots() {
        return List.of(
                new RoomHourSlotView("09:00", false, "예약 가능"),
                new RoomHourSlotView("10:00", false, "예약 가능"),
                new RoomHourSlotView("11:00", true, "점검 중"),
                new RoomHourSlotView("18:00", false, "예약 가능"),
                new RoomHourSlotView("19:00", false, "예약 가능"));
    }

    public static MatchOperationsView matchOperations() {
        RosterPersonView teamAMember1 = new RosterPersonView(1001L, "김민수", "개인 신청", "3.5", "A");
        RosterPersonView teamAMember2 = new RosterPersonView(1002L, "이서연", "개인 신청", "3.2", "A");
        RosterPersonView teamBMember1 = new RosterPersonView(1003L, "박지훈", "팀 신청", "3.4", "B");
        RosterPersonView teamBMember2 = new RosterPersonView(1004L, "정하늘", "팀 신청", "3.1", "B");
        return new MatchOperationsView(
                1L,
                "강남 저녁 테니스 복식",
                "2026-10-21",
                "19:00–21:00",
                "matched",
                "팀 배정 완료",
                "코트 A",
                4,
                1,
                6,
                true,
                true,
                List.of(
                        new MatchApplicationView(2001L, "김민수", 1, "good", "확정", "개인 신청", "김민수", "2026-10-18 10:12", "R-20261018-001", null, false),
                        new MatchApplicationView(2002L, "이서연", 1, "good", "확정", "개인 신청", "이서연", "2026-10-18 10:20", "R-20261018-002", null, false),
                        new MatchApplicationView(2003L, "박지훈", 1, "good", "확정", "팀 신청", "박지훈, 정하늘", "2026-10-18 11:05", "R-20261018-003", null, false),
                        new MatchApplicationView(2004L, "정하늘", 1, "warn", "확정 대기", "개인 신청", "정하늘", "2026-10-19 09:40", "R-20261019-004", null, true)),
                List.of(new PendingGroupView(1, "정하늘", "R-20261019-004")),
                new TeamView(3, List.of(teamAMember1, teamAMember2)),
                new TeamView(3, List.of(teamBMember1, teamBMember2)),
                List.of());
    }

    public static SystemDashboardView systemDashboard() {
        ReportRowView report = reportRow("RPT-2026-1042", "욕설·비방", "member_104", "member_077", "2026-10-21 17:42",
                4, "warn", "높음", "warn", "조사 필요", "경기 중 상대 참가자에게 반복적인 비방 발언이 있었습니다.",
                "신고 대상 계정의 최근 경기 채팅 로그 확인이 필요합니다.", List.of(
                        new EvidenceView("2026-10-21 17:30", "warn", "경기 채팅 로그", "비방성 메시지 3건이 확인되었습니다."),
                        new EvidenceView("2026-10-21 17:15", "neutral", "경기 참가 이력", "동일 경기에서 2회 신고가 접수되었습니다.")),
                List.of(new ReportHistoryView("2026-09-30", "욕설·비방", "경고")));
        return new SystemDashboardView(
                "2026-10-21 18:05",
                "정상",
                "99.98%",
                42,
                "71",
                63,
                "8 GB / 16 GB",
                2,
                "2",
                48,
                false,
                List.of(
                        new DashboardBarView(35, 52, "12시"),
                        new DashboardBarView(48, 60, "14시"),
                        new DashboardBarView(42, 63, "16시"),
                        new DashboardBarView(56, 61, "18시")),
                List.of(
                        new ServiceStatusView("good", "API 서버", "정상 · 평균 86ms"),
                        new ServiceStatusView("good", "데이터베이스", "정상 · 연결 12/50"),
                        new ServiceStatusView("warn", "배치 작업", "지연 · 다음 실행 19:00")),
                List.of(
                        new RecentErrorView("18:02", "warn", "경고", "reservation-api", "결제 승인 응답 지연", "PAY-SLOW-01", 3),
                        new RecentErrorView("17:48", "error", "오류", "notification-worker", "알림 발송 재시도 실패", "NTF-RETRY-02", 1)),
                List.of(report));
    }

    public static ErrorMetricsView errorMetrics() {
        return new ErrorMetricsView(6, 2, 14, 2, "118", "2026-10-21 18:05");
    }

    public static ErrorFiltersView errorFilters() {
        return new ErrorFiltersView("", "all", 24);
    }

    public static List<ErrorLogView> errors() {
        return List.of(
                new ErrorLogView("2026-10-21 18:02", "warn", "경고", "reservation-api", "PAY-SLOW-01", "결제 승인 응답 지연", 3,
                        "PG 응답 시간이 3초를 초과했습니다. 재시도 큐에서 처리 상태를 확인하세요."),
                new ErrorLogView("2026-10-21 17:48", "error", "오류", "notification-worker", "NTF-RETRY-02", "알림 발송 재시도 실패", 1,
                        "푸시 발송 대상 토큰이 만료되었습니다. 사용자 알림 설정을 확인하세요."),
                new ErrorLogView("2026-10-21 16:20", "info", "정보", "auth-api", "AUTH-ROTATE-01", "세션 키 교체 완료", 1,
                        "정기 세션 키 교체 작업이 정상 완료되었습니다."));
    }

    public static ReportMetricsView reportMetrics() {
        return new ReportMetricsView(4, 1, 6, 2);
    }

    public static ReportFiltersView reportFilters() {
        return new ReportFiltersView("", "all", "all");
    }

    public static List<ReportRowView> reports() {
        return List.of(
                reportRow("RPT-2026-1042", "욕설·비방", "member_104", "member_077", "2026-10-21 17:42", 4,
                        "warn", "높음", "warn", "조사 필요", "경기 중 상대 참가자에게 반복적인 비방 발언이 있었습니다.",
                        "신고 대상 계정의 최근 경기 채팅 로그 확인이 필요합니다.", List.of(
                                new EvidenceView("2026-10-21 17:30", "warn", "경기 채팅 로그", "비방성 메시지 3건이 확인되었습니다."),
                                new EvidenceView("2026-10-21 17:15", "neutral", "경기 참가 이력", "동일 경기에서 2회 신고가 접수되었습니다.")),
                        List.of(new ReportHistoryView("2026-09-30", "욕설·비방", "경고"))),
                reportRow("RPT-2026-1041", "노쇼", "member_088", "member_023", "2026-10-21 14:10", 1,
                        "neutral", "보통", "good", "처리 완료", "예약 시간에 참석하지 않았습니다.",
                        "예약 이력과 취소 기록을 확인한 결과 노쇼로 판단했습니다.", List.of(
                                new EvidenceView("2026-10-21 14:02", "warn", "예약 이력", "예약 10분 전까지 취소 기록이 없습니다.")),
                        List.of()));
    }

    public static PaginationView reportPagination() {
        return new PaginationView(1, 1, "/admin/system?view=reports&page=1", "/admin/system?view=reports&page=1");
    }

    public static PenaltyMetricsView penaltyMetrics() {
        return new PenaltyMetricsView(2, 5, 3);
    }

    public static PenaltyFiltersView penaltyFilters() {
        return new PenaltyFiltersView("", "all");
    }

    public static List<PenaltyView> penalties() {
        return List.of(
                new PenaltyView(11L, "member_104", "member104@example.com", "3일 이용 정지", "2026-10-21 18:10", "2026-10-24 18:10",
                        "RPT-2026-1042", "반복적인 비방 발언", "warn", "적용 중", true),
                new PenaltyView(12L, "member_088", "member088@example.com", "경고", "2026-10-21 14:30", null,
                        "RPT-2026-1041", "예약 노쇼", "neutral", "처리 완료", false));
    }

    public static UserMetricsView userMetrics() {
        return new UserMetricsView(1248, 3, 9, 2, 7, 4);
    }

    public static UserFiltersView userFilters() {
        return new UserFiltersView("", "all", "all");
    }

    public static List<UserView> users() {
        return List.of(
                new UserView("member_104", "김민수", "member104@example.com", "member", "neutral", "회원", "good", "승인 완료",
                        "warn", "이용 정지", "3일 이용 정지 · 2026-10-24까지", "2025-03-12", "2026-10-21 18:02", false, true),
                new UserView("member_088", "이서연", "member088@example.com", "facility", "good", "시설 관리자", "pending", "승인 대기",
                        "good", "정상", null, "2026-10-20", "2026-10-21 16:40", true, false),
                new UserView("admin_002", "박지훈", "admin002@example.com", "system", "warn", "시스템 관리자", "good", "승인 완료",
                        "good", "정상", null, "2024-11-05", "2026-10-21 18:04", false, false));
    }

    public static PaginationView userPagination() {
        return new PaginationView(3, 1, "/admin/system?view=users&page=1", "/admin/system?view=users&page=2");
    }

    public static List<CmsNoticeView> cmsNotices() {
        return List.of(
                new CmsNoticeView("서비스 정기 점검 안내", "공지사항", "good", "게시 중", "시스템 관리자", "2026-10-20 09:00", "2026-10-25 ~ 2026-10-26"),
                new CmsNoticeView("개인정보 처리방침 개정 안내", "공지사항", "neutral", "임시 저장", "시스템 관리자", "2026-10-18 14:20", "2026-11-01 ~"));
    }

    public static List<CmsFaqView> cmsFaqs() {
        return List.of(
                new CmsFaqView("예약 취소는 언제까지 가능한가요?", "예약", "good", "게시 중", 1, "2026-10-15 11:00"),
                new CmsFaqView("시설 관리자 권한은 어떻게 신청하나요?", "계정", "good", "게시 중", 2, "2026-10-12 10:30"));
    }

    public static List<CmsTermView> cmsTerms() {
        return List.of(
                new CmsTermView("이용약관", "v2.1", "2026-10-01", "good", "시행 중", "시스템 관리자", "/terms/usage"),
                new CmsTermView("개인정보 처리방침", "v2.0", "2026-09-15", "good", "시행 중", "시스템 관리자", "/terms/privacy"));
    }

    public static SystemSettingsView systemSettings() {
        return new SystemSettingsView(
                true,
                true,
                true,
                false,
                60,
                true,
                true,
                true,
                false,
                "support@spoit.example",
                "평일 09:00–18:00",
                "Asia/Seoul",
                "2026-10-20 14:20 시스템 관리자 변경");
    }

    public static AuditFiltersView auditFilters() {
        return new AuditFiltersView("", "all", 7);
    }

    public static List<AuditLogView> auditLogs() {
        return List.of(
                new AuditLogView("2026-10-21 18:10", "시스템 관리자", "시스템 관리자", "제재 적용", "member_104", "good", "성공", "REQ-9F2A1", "3일 이용 정지 적용"),
                new AuditLogView("2026-10-21 16:40", "시스템 관리자", "시스템 관리자", "권한 변경", "member_088", "good", "성공", "REQ-7C1B4", "회원에서 시설 관리자로 변경"),
                new AuditLogView("2026-10-20 14:20", "시스템 관리자", "시스템 관리자", "환경 설정 변경", "system-settings", "warn", "검토 필요", "REQ-3D8E2", "일일 요약 리포트 설정 변경"));
    }

    public static PaginationView auditPagination() {
        return new PaginationView(1, 1, "/admin/system?view=audit&page=1", "/admin/system?view=audit&page=1");
    }

    private static ReportRowView reportRow(String reportId, String type, String target, String reporter,
            String receivedAt, int relatedLogs, String priorityTone, String priority, String statusTone,
            String statusLabel, String summary, String detail, List<EvidenceView> evidence,
            List<ReportHistoryView> history) {
        return new ReportRowView(reportId, type, target, reporter, receivedAt, relatedLogs, priorityTone, priority,
                statusTone, statusLabel, summary, detail, evidence, history);
    }
}
