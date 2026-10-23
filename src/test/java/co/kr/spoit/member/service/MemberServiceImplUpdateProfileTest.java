package co.kr.spoit.member.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import co.kr.spoit.member.dto.ProfileUpdateForm;
import co.kr.spoit.member.mapper.MemberMapper;

/** 프로필 저장이 실제로 매퍼에 쓰기를 전달하는지 검증한다. 컨트롤러 테스트는 서비스를 목으로 대체하므로 이 경로가 비어 있었다. */
class MemberServiceImplUpdateProfileTest {

    private static final Long USER_ID = 7L;
    private static final String NICKNAME = "플레이어";
    private static final String PHONE = "010-1234-5678";
    private static final Long SPORT_ID = 5L;
    private static final Long REGION_ID = 11L;

    private MemberMapper memberMapper;
    private MemberServiceImpl memberService;

    @BeforeEach
    void setUp() {
        memberMapper = mock(MemberMapper.class);
        memberService = new MemberServiceImpl(memberMapper, new BCryptPasswordEncoder());
    }

    @Test
    @DisplayName("유효한 프로필 저장은 기본정보 갱신과 선호종목·지역 교체를 수행한다")
    void savesValidProfileWithSportAndRegion() {
        stubExists(true, true);

        memberService.updateProfile(USER_ID, form(NICKNAME, PHONE, SPORT_ID, REGION_ID));

        verify(memberMapper).updateProfileBasics(USER_ID, NICKNAME, PHONE);
        verify(memberMapper).upsertUserPreferredSport(USER_ID, SPORT_ID);
        verify(memberMapper, never()).deleteUserPreferredSport(USER_ID);
        verify(memberMapper).deleteUserRegions(USER_ID);
        verify(memberMapper).insertUserRegion(USER_ID, REGION_ID);
    }

    @Test
    @DisplayName("선호 종목을 미선택으로 저장하면 기존 설정이 해제된다")
    void clearsSportWhenNotSelected() {
        memberService.updateProfile(USER_ID, form(NICKNAME, PHONE, null, null));

        verify(memberMapper).updateProfileBasics(USER_ID, NICKNAME, PHONE);
        verify(memberMapper).deleteUserPreferredSport(USER_ID);
        verify(memberMapper, never()).upsertUserPreferredSport(anyLong(), anyLong());
        verify(memberMapper, never()).insertUserRegion(anyLong(), anyLong());
    }

    @Test
    @DisplayName("존재하지 않는 종목이면 저장하지 않고 예외를 던진다")
    void rejectsUnknownSport() {
        stubExists(false, true);

        assertThatThrownBy(() -> memberService.updateProfile(USER_ID, form(NICKNAME, PHONE, 9999L, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("종목");

        verify(memberMapper, never()).updateProfileBasics(anyLong(), anyString(), anyString());
        verify(memberMapper, never()).upsertUserPreferredSport(anyLong(), anyLong());
        verify(memberMapper, never()).deleteUserRegions(anyLong());
        verify(memberMapper, never()).insertUserRegion(anyLong(), anyLong());
    }

    @Test
    @DisplayName("존재하지 않는 지역이면 저장하지 않고 예외를 던진다")
    void rejectsUnknownRegion() {
        stubExists(true, false);

        assertThatThrownBy(() -> memberService.updateProfile(USER_ID, form(NICKNAME, PHONE, SPORT_ID, 9999L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("지역");

        verify(memberMapper, never()).updateProfileBasics(anyLong(), anyString(), anyString());
        verify(memberMapper, never()).upsertUserPreferredSport(anyLong(), anyLong());
        verify(memberMapper, never()).deleteUserRegions(anyLong());
    }

    @Test
    @DisplayName("닉네임 형식 위반이면 어떤 쓰기도 수행하지 않는다")
    void rejectsInvalidNickname() {
        assertThatThrownBy(() -> memberService.updateProfile(USER_ID, form("!", PHONE, SPORT_ID, REGION_ID)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("닉네임");

        verify(memberMapper, never()).updateProfileBasics(anyLong(), anyString(), anyString());
        verify(memberMapper, never()).upsertUserPreferredSport(anyLong(), anyLong());
        verify(memberMapper, never()).deleteUserRegions(anyLong());
    }

    @Test
    @DisplayName("잘못된 userId면 예외를 던지고 매퍼를 호출하지 않는다")
    void rejectsInvalidUserId() {
        assertThatThrownBy(() -> memberService.updateProfile(0L, form(NICKNAME, PHONE, SPORT_ID, REGION_ID)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(memberMapper);
    }

    /** 검증 순서상 쓰기 전에 호출되는 존재 확인 스텁. 사용되지 않는 조합이 있어 lenient로 둔다. */
    private void stubExists(boolean sport, boolean region) {
        lenient().when(memberMapper.sportExists(any())).thenReturn(sport);
        lenient().when(memberMapper.regionExists(any())).thenReturn(region);
    }

    private static ProfileUpdateForm form(String nickname, String phone, Long sportId, Long regionId) {
        ProfileUpdateForm form = new ProfileUpdateForm();
        form.setNickname(nickname);
        form.setPhone(phone);
        form.setRepresentativeSportId(sportId);
        form.setRegionId(regionId);
        return form;
    }
}
