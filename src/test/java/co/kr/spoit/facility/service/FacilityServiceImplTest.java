package co.kr.spoit.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
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
