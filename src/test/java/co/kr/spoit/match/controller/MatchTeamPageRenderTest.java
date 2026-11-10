package co.kr.spoit.match.controller;

import static co.kr.spoit.member.domain.MemberPrincipalFixture.member;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.kr.spoit.global.config.SecurityConfig;
import co.kr.spoit.match.dto.JoinPage;
import co.kr.spoit.match.dto.JoinPage.ConflictMatch;
import co.kr.spoit.match.dto.JoinPage.ConflictPerson;
import co.kr.spoit.match.dto.JoinPage.JoinMatch;
import co.kr.spoit.match.dto.JoinPage.Person;
import co.kr.spoit.match.dto.JoinPage.Selection;
import co.kr.spoit.match.dto.MatchDetailPage;
import co.kr.spoit.match.dto.MatchDetailRow;
import co.kr.spoit.match.dto.TeamClubRow;
import co.kr.spoit.match.dto.TeamMemberRow;
import co.kr.spoit.match.service.MatchApplicationService;
import co.kr.spoit.match.service.MatchDetailAssembler;
import co.kr.spoit.match.service.MatchService;
import co.kr.spoit.member.security.MemberLoginFailureHandler;
import co.kr.spoit.member.security.MemberLoginSuccessHandler;
import co.kr.spoit.member.service.MemberUserDetailsService;
import co.kr.spoit.payment.service.PaymentService;

/** RES-04 팀 신청 화면(상세 팀원 선택, 팀 신청 내용)이 실제 템플릿으로 렌더링되는지 검증한다. */
@WebMvcTest(controllers = MatchController.class)
@Import(SecurityConfig.class)
class MatchTeamPageRenderTest {

    private static final long MATCH_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MatchService matchService;

    @MockitoBean
    private MatchApplicationService applicationService;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private MemberLoginSuccessHandler loginSuccessHandler;

    @MockitoBean
    private MemberLoginFailureHandler loginFailureHandler;

    @MockitoBean
    private MemberUserDetailsService memberUserDetailsService;

    @Test
    @DisplayName("상세 팀원 선택: 신청 대표는 위쪽 고정 칸, 이미 신청한 회원은 체크된 채 잠긴다")
    void detailRendersTeamRoster() throws Exception {
        given(matchService.findDetail(eq(MATCH_ID), anyLong())).willReturn(Optional.of(detailWithClub()));

        mockMvc.perform(get("/matches/{matchId}", MATCH_ID).with(user(member())))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("스매시클럽"),
                        containsString("신청 대표 · 본인"),
                        containsString("박민수 · 동호회장"),
                        containsString("is-applied"),
                        containsString("이미 신청"),
                        containsString("data-applied=\"true\""),
                        containsString("신청자 포함"))));
    }

    @Test
    @DisplayName("팀 신청 내용 화면은 팀 접수 경로로 동호회·팀원을 hidden 값으로 보낸다")
    void teamJoinFormPostsSelection() throws Exception {
        given(applicationService.prepareTeam(MATCH_ID, 7L, 5L, List.of(21L, 22L))).willReturn(Optional.of(teamJoinPage()));

        mockMvc.perform(get("/matches/{matchId}/apply/team", MATCH_ID)
                        .param("clubId", "5")
                        .param("clubMemberIds", "21,22")
                        .with(user(member())))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("action=\"/matches/1/apply/team\""),
                        containsString("name=\"clubId\" value=\"5\""),
                        containsString("name=\"clubMemberIds\" value=\"21\""),
                        containsString("name=\"clubMemberIds\" value=\"22\""),
                        containsString("팀 단위 참가"))));
    }

    @Test
    @DisplayName("신청 내용 화면에 운영 규칙·취소·노쇼 안내 모달과 계정 정지 문구를 그린다")
    void joinPageRendersRulesGuide() throws Exception {
        given(applicationService.prepareTeam(MATCH_ID, 7L, 5L, List.of(21L, 22L))).willReturn(Optional.of(teamJoinPage()));

        mockMvc.perform(get("/matches/{matchId}/apply/team", MATCH_ID)
                        .param("clubId", "5")
                        .param("clubMemberIds", "21,22")
                        .with(user(member())))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("id=\"rules-guide-open\""),
                        containsString("id=\"rules-guide-dialog\""),
                        containsString("저녁 배드민턴 · 내일 19:00~21:00"),
                        containsString("매치 운영 규칙"),
                        containsString("다른 매치와 시간이 겹치면 신청할 수 없습니다."),
                        containsString("취소 안내"),
                        containsString("노쇼 안내"),
                        containsString("잦은 노쇼 시 일주일에서 한 달 사이의 계정 정지가 있을 수 있습니다."),
                        containsString("id=\"rules-guide-confirm\""))));
    }

    @Test
    @DisplayName("시간이 겹치면 신청 화면에 겹치는 회원·매치를 담은 모달을 그리고, 신청 버튼을 막는다")
    void joinPageRendersTimeConflictModal() throws Exception {
        JoinPage page = JoinPage.builder()
                .match(teamJoinPage().getMatch())
                .selection(teamJoinPage().getSelection())
                .canReserve(false)
                .capacityNotice("회원님 외 1명이 이미 참가 신청한 다른 매치와 시간이 겹칩니다.")
                .timeConflicts(List.of(
                        new ConflictPerson("회원", true, List.of(
                                new ConflictMatch("성수 풋살 리그", "11/7(토) 18:00~20:00", "성수 구장", "참가 확정"))),
                        new ConflictPerson("박민수", false, List.of(
                                new ConflictMatch("주말 배드민턴", "11/7(토) 20:00~22:00", "강남 스포츠센터", "확정 대기")))))
                .feeTotalText("0원")
                .feeSummaryNote("")
                .build();
        given(applicationService.prepareTeam(MATCH_ID, 7L, 5L, List.of(21L, 22L))).willReturn(Optional.of(page));

        mockMvc.perform(get("/matches/{matchId}/apply/team", MATCH_ID)
                        .param("clubId", "5")
                        .param("clubMemberIds", "21,22")
                        .with(user(member())))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("id=\"time-conflict-dialog\""),
                        containsString("신청 시간이 겹칩니다"),
                        containsString("회원 (나)"),
                        containsString("성수 풋살 리그"),
                        containsString("11/7(토) 18:00~20:00 · 성수 구장"),
                        containsString("박민수"),
                        containsString("확정 대기"),
                        containsString("href=\"/mypage#reservations\""),
                        containsString("id=\"continue-button\" type=\"submit\" class=\"join-button join-button-primary\" disabled"))));
    }

    @Test
    @DisplayName("겹치지 않으면 시간 겹침 모달을 그리지 않는다")
    void joinPageWithoutConflictHasNoModal() throws Exception {
        given(applicationService.prepareTeam(MATCH_ID, 7L, 5L, List.of(21L, 22L))).willReturn(Optional.of(teamJoinPage()));

        mockMvc.perform(get("/matches/{matchId}/apply/team", MATCH_ID)
                        .param("clubId", "5")
                        .param("clubMemberIds", "21,22")
                        .with(user(member())))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("time-conflict-dialog"))));
    }

    private static MatchDetailPage detailWithClub() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        MatchDetailRow row = new MatchDetailRow();
        row.setMatchId(MATCH_ID);
        row.setTitle("저녁 배드민턴");
        row.setSportId(2L);
        row.setSportName("배드민턴");
        row.setFacilityName("강남 스포츠센터");
        row.setRegionName("강남구");
        row.setAddress("서울");
        row.setStartAt(now.plusDays(1));
        row.setEndAt(now.plusDays(1).plusHours(2));
        row.setMinParticipants(4);
        row.setMaxParticipants(8);
        row.setFeePerPerson(new BigDecimal("5000"));
        row.setRequiredSkill("초급");
        row.setStatus("모집 중");
        row.setTeamACapacity(4);
        row.setTeamBCapacity(4);

        TeamClubRow club = new TeamClubRow();
        club.setClubId(5L);
        club.setClubName("스매시클럽");
        club.setLeaderName("박민수");
        club.setMemberCount(3);

        MatchDetailAssembler assembler = new MatchDetailAssembler();
        return assembler.toPage(row, List.of(), 7L, now, now.toLocalDate(), assembler.toTeamOptions(List.of(club),
                List.of(teamMember(100L, 7L, "회원", "동호회원", false),
                        teamMember(101L, 21L, "박민수", "동호회장", false),
                        teamMember(102L, 22L, "정하늘", "동호회원", true)),
                7L));
    }

    private static TeamMemberRow teamMember(long clubMemberId, long userId, String nickname, String role,
                                            boolean applied) {
        TeamMemberRow m = new TeamMemberRow();
        m.setClubId(5L);
        m.setClubMemberId(clubMemberId);
        m.setUserId(userId);
        m.setNickname(nickname);
        m.setRole(role);
        m.setAlreadyApplied(applied);
        return m;
    }

    private static JoinPage teamJoinPage() {
        return JoinPage.builder()
                .match(JoinMatch.builder()
                        .matchId(MATCH_ID).title("저녁 배드민턴").sportName("배드민턴").statusLabel("모집 중")
                        .facilityName("강남 스포츠센터").dateLabel("내일").time("19:00~21:00").place("강남")
                        .levelLabel("초급").feeText("5,000").capacityLabel("0 / 8명").maxParticipants(8)
                        .remainingLabel("남은 자리 8명").build())
                .selection(new Selection("팀 신청 · 스매시클럽", "회원", "010-0000-0000", "team", 3,
                        List.of(new Person("회원", "신청 대표"), new Person("박민수", "동호회장"), new Person("이서연", "팀원")),
                        5L, List.of(21L, 22L)))
                .canReserve(true)
                .capacityNotice("")
                .feeTotalText("0원")
                .feeSummaryNote("현재는 결제 없이 무료로 신청이 접수됩니다.")
                .build();
    }

    @Test
    @DisplayName("유료 팀 신청 화면은 3단계와 카카오페이·결제 금액·환불 안내를 그리고 결제 준비 주소를 넘긴다")
    void paidJoinPageRendersPaymentStep() throws Exception {
        JoinPage page = JoinPage.builder()
                .match(teamJoinPage().getMatch())
                .selection(teamJoinPage().getSelection())
                .canReserve(true)
                .capacityNotice("")
                .paid(true)
                .unitAmount(9000)
                .totalAmount(27000)
                .feeTotalText("27,000원")
                .feeSummaryNote("9,000원 × 3명")
                .build();
        given(applicationService.prepareTeam(MATCH_ID, 7L, 5L, List.of(21L, 22L))).willReturn(Optional.of(page));
        given(paymentService.isAvailable()).willReturn(true);
        given(paymentService.isTestMode()).willReturn(true);

        mockMvc.perform(get("/matches/{matchId}/apply/team", MATCH_ID)
                        .param("clubId", "5")
                        .param("clubMemberIds", "21,22")
                        .with(user(member())))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("data-paid=\"true\""),
                        containsString("data-payment-prepare-url=\"/matches/1/apply/payment/prepare\""),
                        containsString("STEP 01 / 03"),
                        containsString("다음: 참가비 결제"),
                        containsString("id=\"payment-method\""),
                        containsString("카카오페이"),
                        containsString("9,000원 × 3명"),
                        containsString("카카오페이로 27,000원 결제"),
                        containsString("테스트 결제입니다. 실제 금액이 청구되지 않습니다."),
                        containsString("인원만큼 즉시 환불됩니다."))));
    }

    @Test
    @DisplayName("무료 신청 화면은 결제 단계 없이 2단계로 바로 접수한다")
    void freeJoinPageSkipsPaymentStep() throws Exception {
        given(applicationService.prepareTeam(MATCH_ID, 7L, 5L, List.of(21L, 22L))).willReturn(Optional.of(teamJoinPage()));

        mockMvc.perform(get("/matches/{matchId}/apply/team", MATCH_ID)
                        .param("clubId", "5")
                        .param("clubMemberIds", "21,22")
                        .with(user(member())))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("data-paid=\"false\""),
                        containsString("STEP 01 / 02"),
                        containsString(">참가 신청하기<"),
                        not(containsString("data-step-panel=\"2\"")))));
    }
}
