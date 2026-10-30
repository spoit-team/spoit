package co.kr.spoit.web;

import static co.kr.spoit.member.domain.MemberPrincipalFixture.facilityManager;
import static co.kr.spoit.member.domain.MemberPrincipalFixture.member;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.kr.spoit.facility.controller.FacilityAdminController;
import co.kr.spoit.facility.domain.FacilitySummary;
import co.kr.spoit.facility.dto.FacilityListItem;
import co.kr.spoit.facility.service.FacilityService;
import co.kr.spoit.global.config.SecurityConfig;
import co.kr.spoit.match.dto.RoomManagementPage;
import co.kr.spoit.match.service.RoomManagementService;
import co.kr.spoit.member.security.MemberLoginFailureHandler;
import co.kr.spoit.member.security.MemberLoginSuccessHandler;
import co.kr.spoit.member.service.MemberUserDetailsService;

/** 시설 관리 화면이 인증 주체의 담당 시설·매칭방만 실제 서비스 조회로 렌더링하는지 검증한다. */
@WebMvcTest(controllers = FacilityAdminController.class)
@Import(SecurityConfig.class)
class AdminFacilityPageTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FacilityService facilityService;

    @MockitoBean
    private co.kr.spoit.facility.service.FacilityScheduleService facilityScheduleService;

    @MockitoBean
    private RoomManagementService roomManagementService;

    @MockitoBean
    private MemberLoginSuccessHandler loginSuccessHandler;

    @MockitoBean
    private MemberLoginFailureHandler loginFailureHandler;

    @MockitoBean
    private MemberUserDetailsService memberUserDetailsService;

    @Test
    @DisplayName("로그인한 시설 관리자의 회원 ID로 담당 시설을 조회해 목록을 렌더링한다")
    void rendersFacilitiesLookedUpByPrincipalUserId() throws Exception {
        when(roomManagementService.findRooms(7L, null, null, null, null)).thenReturn(emptyRooms());
        when(facilityService.findManagedFacilities(7L)).thenReturn(List.of(
                facility(10L, "송파 풋살파크", "농구, 축구", 3, "운영 중"),
                facility(11L, "마포 체육관", null, 2, "특별 운영")));

        mockMvc.perform(get("/admin/facility").with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/facility"))
                .andExpect(content().string(containsString("송파 풋살파크")))
                .andExpect(content().string(containsString("농구, 축구")))
                .andExpect(content().string(containsString("마포 체육관")))
                .andExpect(content().string(containsString("미설정")))
                .andExpect(content().string(containsString("특별 운영")))
                .andExpect(content().string(containsString("2구역")))
                .andExpect(content().string(not(containsString("수용 인원"))))
                .andExpect(content().string(not(containsString("facility-capacity"))))
                .andExpect(content().string(not(containsString("등록된 시설이 없습니다"))))
                .andExpect(content().string(not(containsString("/admin/system"))));

        verify(facilityService).findManagedFacilities(7L);
    }

    @Test
    @DisplayName("담당 시설이 없으면 빈 상태 문구를 렌더링한다")
    void rendersEmptyStateWhenNoManagedFacility() throws Exception {
        when(roomManagementService.findRooms(7L, null, null, null, null)).thenReturn(emptyRooms());
        when(facilityService.findManagedFacilities(7L)).thenReturn(List.of());

        mockMvc.perform(get("/admin/facility").with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("등록된 시설이 없습니다")));
    }

    @Test
    @DisplayName("해시로 섹션을 바꾸는 화면이라 view가 없어도 매칭방 목록은 실제 값으로 채운다")
    void fillsBookingListOnEveryGetBecauseSectionsSwitchByHash() throws Exception {
        when(roomManagementService.findRooms(7L, null, null, null, null)).thenReturn(emptyRooms());
        when(facilityService.findManagedFacilities(7L)).thenReturn(List.of());

        mockMvc.perform(get("/admin/facility").with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(model().attribute("view", "dashboard"))
                .andExpect(model().attribute("matches", List.of()))
                .andExpect(model().attribute("bookingSports", List.of()));

        verify(roomManagementService).findRooms(7L, null, null, null, null);
    }

    @Test
    @DisplayName("view=bookings 요청은 검색어·상태·종목 필터를 인증 주체 ID로 서비스에 전달한다")
    void passesBookingFiltersWithPrincipalUserId() throws Exception {
        when(roomManagementService.findRooms(7L, "방 이름", "recruiting", 3L, null)).thenReturn(
                RoomManagementPage.of(List.of(), List.of(),
                        new RoomManagementPage.Filters("방 이름", "recruiting", 3L, null)));
        when(facilityService.findManagedFacilities(7L)).thenReturn(List.of());

        mockMvc.perform(get("/admin/facility")
                        .param("view", "bookings")
                        .param("q", "방 이름")
                        .param("status", "recruiting")
                        .param("sportId", "3")
                        .with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(model().attribute("view", "bookings"))
                .andExpect(model().attribute("bookingFilters",
                        org.hamcrest.Matchers.hasProperty("q", org.hamcrest.Matchers.is("방 이름"))))
                .andExpect(content().string(containsString("data-view=\"bookings\"")));

        verify(roomManagementService).findRooms(7L, "방 이름", "recruiting", 3L, null);
    }

    @Test
    @DisplayName("매칭방 목록은 실제 조회 결과의 방 정보와 상세 링크·선택된 필터를 렌더링한다")
    void rendersBookingRowsWithMatchIdLinkAndSelectedFilters() throws Exception {
        RoomManagementPage page = RoomManagementPage.of(
                List.of(new RoomManagementPage.Match(501L, "토요 풋살", "테스트 체육관", "풋살",
                        "3 / 8명", "11/1(토) 10:00~12:00", "모집 중", "good")),
                List.of(new RoomManagementPage.Sport(3L, "풋살")),
                new RoomManagementPage.Filters("풋살", "recruiting", 3L, null));
        when(roomManagementService.findRooms(7L, "풋살", "recruiting", 3L, null)).thenReturn(page);
        when(facilityService.findManagedFacilities(7L)).thenReturn(List.of());

        String html = mockMvc.perform(get("/admin/facility")
                        .param("view", "bookings")
                        .param("q", "풋살")
                        .param("status", "recruiting")
                        .param("sportId", "3")
                        .with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(model().attribute("matches", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(content().string(containsString("토요 풋살")))
                .andExpect(content().string(containsString("테스트 체육관")))
                .andExpect(content().string(containsString("3 / 8명")))
                .andExpect(content().string(containsString("11/1(토) 10:00~12:00")))
                .andExpect(content().string(containsString("모집 중")))
                .andExpect(content().string(containsString("/admin/operations?matchId=501")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // 필터 선택 상태는 정규화된 값으로 되살아나야 한다. 템플릿 속성 순서 변화에 흔들리지 않게 태그 단위로 확인한다.
        org.assertj.core.api.Assertions.assertThat(optionTagContaining(html, "value=\"recruiting\""))
                .contains("selected");
        org.assertj.core.api.Assertions.assertThat(optionTagContaining(html, "value=\"3\""))
                .contains("selected");

        verify(roomManagementService).findRooms(7L, "풋살", "recruiting", 3L, null);
    }

    @Test
    @DisplayName("일정 화면의 facilityId·sportId는 매칭방 필터·종목 선택지로 새지 않는다")
    void keepsScheduleFacilityAndSportOutOfBookingFilters() throws Exception {
        when(roomManagementService.findRooms(7L, null, null, null, null)).thenReturn(emptyRooms());
        when(facilityService.findManagedFacilities(7L)).thenReturn(List.of());
        when(facilityScheduleService.findSchedulePage(7L, 10L, null, 3L, "schedule-hours", null))
                .thenReturn(co.kr.spoit.facility.dto.SchedulePage.empty());

        mockMvc.perform(get("/admin/facility")
                        .param("view", "schedule")
                        .param("facilityId", "10")
                        .param("sportId", "3")
                        .param("tab", "schedule-hours")
                        .with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(model().attribute("view", "schedule"))
                // 일정 화면의 종목 선택지를 매칭방 종목이 덮어쓰지 않는다.
                .andExpect(model().attribute("sports", List.of()))
                .andExpect(model().attribute("bookingSports", List.of()))
                .andExpect(model().attribute("bookingFilters",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.hasProperty("facilityId",
                                        org.hamcrest.Matchers.nullValue()),
                                org.hamcrest.Matchers.hasProperty("sportId",
                                        org.hamcrest.Matchers.nullValue()))));

        verify(roomManagementService).findRooms(7L, null, null, null, null);
    }

    @Test
    @DisplayName("view=bookings 요청은 시설 필터를 담당 시설 범위 안에서 그대로 전달하고 선택 상태로 되돌려준다")
    void passesBookingFacilityFilterOnBookingsView() throws Exception {
        when(roomManagementService.findRooms(7L, null, null, null, 10L)).thenReturn(
                RoomManagementPage.of(List.of(), List.of(),
                        new RoomManagementPage.Filters(null, "all", null, 10L)));
        when(facilityService.findManagedFacilities(7L)).thenReturn(List.of(
                facility(10L, "송파 풋살파크", "풋살", 3, "운영 중"),
                facility(11L, "마포 체육관", "농구", 2, "운영 중")));

        mockMvc.perform(get("/admin/facility")
                        .param("view", "bookings")
                        .param("facilityId", "10")
                        .with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(model().attribute("view", "bookings"))
                .andExpect(model().attribute("bookingFilters",
                        org.hamcrest.Matchers.hasProperty("facilityId",
                                org.hamcrest.Matchers.is(10L))))
                // 선택지 모델은 담당 시설 목록 그대로다. 화면은 이 목록에서 옵션을 만든다.
                .andExpect(model().attribute("facilities", org.hamcrest.Matchers.hasSize(2)));

        verify(roomManagementService).findRooms(7L, null, null, null, 10L);
        verify(facilityService).findManagedFacilities(7L);
    }

    @Test
    @DisplayName("시설 필터를 지정하지 않은 매칭방 화면은 전체를 뜻하는 null로 조회한다")
    void defaultsBookingFacilityFilterToAll() throws Exception {
        when(roomManagementService.findRooms(7L, null, null, null, null)).thenReturn(emptyRooms());
        when(facilityService.findManagedFacilities(7L)).thenReturn(List.of());

        mockMvc.perform(get("/admin/facility")
                        .param("view", "bookings")
                        .with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(model().attribute("bookingFilters",
                        org.hamcrest.Matchers.hasProperty("facilityId",
                                org.hamcrest.Matchers.nullValue())));

        verify(roomManagementService).findRooms(7L, null, null, null, null);
    }

    @Test
    @DisplayName("view가 없어도 일정 화면용 facilityId는 매칭방 필터로 넘기지 않는다")
    void keepsFacilityIdOutOfBookingFilterWithoutBookingsView() throws Exception {
        when(roomManagementService.findRooms(7L, null, null, null, null)).thenReturn(emptyRooms());
        when(facilityService.findManagedFacilities(7L)).thenReturn(List.of());

        mockMvc.perform(get("/admin/facility")
                        .param("facilityId", "10")
                        .with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(model().attribute("view", "dashboard"));

        verify(roomManagementService).findRooms(7L, null, null, null, null);
    }

    @Test
    @DisplayName("시설 관리 권한이 없는 회원은 조회 서비스를 호출하지 못한다")
    void memberWithoutFacilityRoleCannotReadFacilities() throws Exception {
        mockMvc.perform(get("/admin/facility").with(user(member())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(facilityService);
    }

    @Test
    @DisplayName("변경 성공 메시지를 시설 화면에 렌더링한다")
    void rendersSuccessMessageFromQuery() throws Exception {
        when(roomManagementService.findRooms(7L, null, null, null, null)).thenReturn(emptyRooms());
        when(facilityService.findManagedFacilities(7L)).thenReturn(List.of());

        mockMvc.perform(get("/admin/facility").param("message", "시설 정보를 수정했습니다.")
                        .with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("시설 정보를 수정했습니다.")))
                .andExpect(content().string(containsString("admin-flash")))
                .andExpect(content().string(not(containsString("admin-flash is-error"))));
    }

    @Test
    @DisplayName("변경 실패 메시지를 시설 화면에 오류 안내로 렌더링한다")
    void rendersErrorMessageFromQuery() throws Exception {
        when(roomManagementService.findRooms(7L, null, null, null, null)).thenReturn(emptyRooms());
        when(facilityService.findManagedFacilities(7L)).thenReturn(List.of());

        mockMvc.perform(get("/admin/facility").param("error", "담당 시설만 관리할 수 있습니다.")
                        .with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("담당 시설만 관리할 수 있습니다.")))
                .andExpect(content().string(containsString("admin-flash is-error")))
                .andExpect(content().string(containsString("role=\"alert\"")));
    }

    /**
     * 매칭방 화면 모델의 조립 규칙은 서비스 책임이므로 이 화면 테스트는 빈 목록짜리 값을 만든다.
     * 템플릿이 목록·선택지를 순회하므로 null 대신 항상 빈 목록을 돌려주는 팩토리를 쓴다.
     */
    private static RoomManagementPage emptyRooms() {
        return RoomManagementPage.empty();
    }

    /** 선택 상태를 확인할 때 다른 태그의 같은 값에 흔들리지 않도록 option 태그 하나만 잘라 온다. */
    private static String optionTagContaining(String html, String valueAttribute) {
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("<option\\b[^>]*>").matcher(html);
        while (matcher.find()) {
            if (matcher.group().contains(valueAttribute)) {
                return matcher.group();
            }
        }
        throw new AssertionError("option 태그를 찾지 못했습니다: " + valueAttribute);
    }

    private static FacilityListItem facility(Long id, String name, String sportsLabel,
            Integer concurrentMatchLimit, String status) {
        FacilitySummary facility = new FacilitySummary();
        facility.setFacilityId(id);
        facility.setName(name);
        facility.setAddress("서울특별시 테스트구 " + id);
        facility.setSportsLabel(sportsLabel);
        facility.setConcurrentMatchLimit(concurrentMatchLimit);
        facility.setStatus(status);
        return FacilityListItem.from(facility);
    }
}
