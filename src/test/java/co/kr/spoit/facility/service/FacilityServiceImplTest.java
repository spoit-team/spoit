package co.kr.spoit.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.kr.spoit.facility.domain.FacilitySummary;
import co.kr.spoit.facility.dto.FacilityForm;
import co.kr.spoit.facility.dto.FacilityListItem;
import co.kr.spoit.facility.mapper.FacilityMapper;

@ExtendWith(MockitoExtension.class)
class FacilityServiceImplTest {

    @Mock
    private FacilityMapper facilityMapper;

    @InjectMocks
    private FacilityServiceImpl service;

    @Test
    @DisplayName("회원 ID가 없으면 매퍼를 호출하지 않고 빈 목록을 반환한다")
    void returnsEmptyWithoutUserId() {
        assertThat(service.findManagedFacilities(null)).isEmpty();
        verifyNoInteractions(facilityMapper);
    }

    @Test
    @DisplayName("매퍼 결과가 없으면 빈 불변 목록을 반환한다")
    void returnsEmptyWhenMapperReturnsNothing() {
        when(facilityMapper.selectManagedFacilities(7L)).thenReturn(List.of());

        assertThat(service.findManagedFacilities(7L)).isEmpty();
    }

    @Test
    @DisplayName("담당 시설을 조회해 화면 항목으로 변환하고 중복 없이 순서를 유지한다")
    void mapsManagedFacilitiesInMapperOrder() {
        when(facilityMapper.selectManagedFacilities(7L)).thenReturn(List.of(
                facility(10L, "첫 시설", "농구, 축구", 2, "운영 중"),
                facility(11L, "둘째 시설", null, 1, "점검 중")));

        List<FacilityListItem> items = service.findManagedFacilities(7L);

        assertThat(items).extracting(FacilityListItem::getFacilityId).containsExactly(10L, 11L);
        verify(facilityMapper).selectManagedFacilities(7L);
    }

    @Test
    @DisplayName("매퍼가 null 목록을 반환해도 빈 목록으로 방어한다")
    void handlesNullMapperResult() {
        when(facilityMapper.selectManagedFacilities(7L)).thenReturn(null);

        assertThat(service.findManagedFacilities(7L)).isEmpty();
    }

    @Test
    @DisplayName("여러 종목과 운영 상태를 화면 값으로 변환한다")
    void mapsMultipleSportsAndKnownStatus() {
        FacilitySummary facility = facility(10L, "테스트 체육관", "농구, 축구", 3, "운영 중");

        FacilityListItem item = FacilityListItem.from(facility);

        assertThat(item.getFacilityId()).isEqualTo(10L);
        assertThat(item.getName()).isEqualTo("테스트 체육관");
        assertThat(item.getAddress()).isEqualTo("주소 10");
        assertThat(item.getSports()).containsExactly("농구", "축구");
        assertThat(item.getSportsLabel()).isEqualTo("농구, 축구");
        assertThat(item.getConcurrentMatchLimit()).isEqualTo(3);
        assertThat(item.getStatusLabel()).isEqualTo("운영 중");
        assertThat(item.getStatusTone()).isEqualTo("good");
    }

    @Test
    @DisplayName("종목이 없고 상태가 미지 값이어도 빈 목록과 중립 배지로 안전하게 표시한다")
    void handlesMissingSportsAndUnknownStatus() {
        FacilitySummary facility = facility(10L, "테스트 체육관", null, null, "특별 운영");
        facility.setAddress(null);

        FacilityListItem item = FacilityListItem.from(facility);

        assertThat(item.getSports()).isEmpty();
        assertThat(item.getSportsLabel()).isEqualTo("미설정");
        assertThat(item.getAddress()).isEqualTo("주소 미설정");
        assertThat(item.getConcurrentMatchLimit()).isZero();
        assertThat(item.getStatusLabel()).isEqualTo("특별 운영");
        assertThat(item.getStatusTone()).isEqualTo("neutral");
    }

    @Test
    @DisplayName("상태가 비어 있으면 미설정 라벨을 사용하고 빈 종목은 제외한다")
    void handlesBlankStatusAndIgnoresBlankSports() {
        FacilityListItem item = FacilityListItem.from(
                facility(10L, "테스트 체육관", "  , 테니스 ,,", 1, "  "));

        assertThat(item.getStatusLabel()).isEqualTo("상태 미설정");
        assertThat(item.getStatusTone()).isEqualTo("neutral");
        assertThat(item.getSports()).containsExactly("테니스");
    }

    @Test
    @DisplayName("시설을 등록하고 담당자·종목 매핑까지 저장한 뒤 ID를 반환한다")
    void registersFacilityWithManagerAndSports() {
        when(facilityMapper.insertFacility(anyForm())).thenAnswer(invocation -> {
            FacilityForm form = invocation.getArgument(0);
            form.setFacilityId(21L);
            return 1;
        });
        when(facilityMapper.findSportIdsByNames(List.of("농구", "축구"))).thenReturn(List.of(2L, 4L));

        long facilityId = service.registerFacility(7L, form("송파 체육관", "주소", 3, "운영 중", "농구", "축구"));

        assertThat(facilityId).isEqualTo(21L);
        verify(facilityMapper).insertFacilityManager(21L, 7L);
        verify(facilityMapper).insertFacilitySports(21L, List.of(2L, 4L));
    }

    @Test
    @DisplayName("등록 폼에 시설명이 비어 있으면 저장하지 않는다")
    void rejectsRegistrationWithoutName() {
        assertThatThrownBy(() -> service.registerFacility(7L, form("  ", "주소", 3, "운영 중")))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(facilityMapper);
    }

    @Test
    @DisplayName("등록 폼의 구역 수가 범위를 벗어나면 저장하지 않는다")
    void rejectsRegistrationWithOutOfRangeZones() {
        assertThatThrownBy(() -> service.registerFacility(7L, form("체육관", "주소", 0, "운영 중")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.registerFacility(7L, form("체육관", "주소", 101, "운영 중")))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(facilityMapper);
    }

    @Test
    @DisplayName("등록 폼의 종목을 하나도 선택하지 않으면 저장하지 않는다")
    void rejectsRegistrationWithoutSports() {
        assertThatThrownBy(() -> service.registerFacility(7L, form("체육관", "주소", 3, "운영 중")))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(facilityMapper);
    }

    @Test
    @DisplayName("등록 폼에 없는 종목 이름이 섞여 있으면 저장하지 않는다")
    void rejectsRegistrationWithUnknownSport() {
        when(facilityMapper.findSportIdsByNames(List.of("농구"))).thenReturn(List.of());

        assertThatThrownBy(() -> service.registerFacility(7L, form("체육관", "주소", 3, "운영 중", "농구")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(facilityMapper, never()).insertFacility(anyForm());
    }

    @Test
    @DisplayName("담당 시설을 수정하면 기본 정보와 종목 매핑을 갱신한다")
    void updatesOwnedFacility() {
        when(facilityMapper.existsFacilityManager(10L, 7L)).thenReturn(true);
        when(facilityMapper.findSportIdsByNames(List.of("테니스"))).thenReturn(List.of(1L));
        when(facilityMapper.updateFacility(eq(10L), anyForm())).thenReturn(1);

        service.updateFacility(7L, 10L, form("수정 체육관", "새 주소", 5, "점검 중", "테니스"));

        verify(facilityMapper).updateFacility(eq(10L), anyForm());
        verify(facilityMapper).deleteFacilitySports(10L);
        verify(facilityMapper).insertFacilitySports(10L, List.of(1L));
    }

    @Test
    @DisplayName("담당하지 않는 시설은 수정할 수 없다")
    void rejectsUpdateByNonManager() {
        when(facilityMapper.existsFacilityManager(10L, 7L)).thenReturn(false);

        assertThatThrownBy(() -> service.updateFacility(7L, 10L, form("수정 체육관", "새 주소", 5, "점검 중", "테니스")))
                .isInstanceOf(FacilityAccessDeniedException.class);

        verify(facilityMapper, never()).updateFacility(eq(10L), anyForm());
    }

    @Test
    @DisplayName("이미 삭제된 시설을 수정하려 하면 예외를 던진다")
    void rejectsUpdateOfDeletedFacility() {
        when(facilityMapper.existsFacilityManager(10L, 7L)).thenReturn(true);
        when(facilityMapper.findSportIdsByNames(List.of("테니스"))).thenReturn(List.of(1L));
        when(facilityMapper.updateFacility(eq(10L), anyForm())).thenReturn(0);

        assertThatThrownBy(() -> service.updateFacility(7L, 10L, form("수정 체육관", "새 주소", 5, "점검 중", "테니스")))
                .isInstanceOf(FacilityNotFoundException.class);
    }

    @Test
    @DisplayName("담당 시설을 논리 삭제한다")
    void deletesOwnedFacility() {
        when(facilityMapper.existsFacilityManager(10L, 7L)).thenReturn(true);
        when(facilityMapper.deleteFacility(10L)).thenReturn(1);

        service.deleteFacility(7L, 10L);

        verify(facilityMapper).deleteFacility(10L);
    }

    @Test
    @DisplayName("담당하지 않는 시설은 삭제할 수 없다")
    void rejectsDeleteByNonManager() {
        when(facilityMapper.existsFacilityManager(10L, 7L)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteFacility(7L, 10L))
                .isInstanceOf(FacilityAccessDeniedException.class);

        verify(facilityMapper, never()).deleteFacility(10L);
    }

    private static FacilityForm form(String name, String address, Integer zones, String status, String... sports) {
        FacilityForm form = new FacilityForm();
        form.setName(name);
        form.setAddress(address);
        form.setSports(List.of(sports));
        form.setConcurrentMatchLimit(zones);
        form.setStatus(status);
        return form;
    }

    private static FacilityForm anyForm() {
        return org.mockito.ArgumentMatchers.any(FacilityForm.class);
    }

    private static FacilitySummary facility(Long id, String name, String sportsLabel,
            Integer concurrentMatchLimit, String status) {
        FacilitySummary facility = new FacilitySummary();
        facility.setFacilityId(id);
        facility.setName(name);
        facility.setAddress("주소 " + id);
        facility.setSportsLabel(sportsLabel);
        facility.setConcurrentMatchLimit(concurrentMatchLimit);
        facility.setStatus(status);
        return facility;
    }
}
