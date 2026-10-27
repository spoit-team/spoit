package co.kr.spoit.facility.dto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import co.kr.spoit.facility.domain.FacilitySummary;
import lombok.Getter;

/**
 * 관리자 시설 목록 화면에 전달하는 읽기 전용 항목이다.
 *
 * <p>DB 값이 비어 있거나 매핑되지 않아도 화면이 깨지지 않도록 표시용 기본값을 만든다.
 * 시설 상태는 자유 문자열이므로 알려지지 않은 값도 원문을 유지한 채 중립 배지로 표시한다.
 * 수용 인원 컬럼은 스키마에 없으므로 값을 만들지 않고 미설정으로 전달한다.</p>
 */
@Getter
public class FacilityListItem {

    private static final String UNSET = "미설정";

    private final Long facilityId;
    private final String name;
    private final String address;
    private final List<String> sports;
    private final String sportsLabel;
    private final String capacityLabel;
    private final Integer concurrentMatchLimit;
    private final String statusLabel;
    private final String statusTone;

    private FacilityListItem(Long facilityId, String name, String address, List<String> sports,
            Integer concurrentMatchLimit, String status) {
        this.facilityId = facilityId;
        this.name = isBlank(name) ? "시설명 미설정" : name.strip();
        this.address = isBlank(address) ? "주소 미설정" : address.strip();
        this.sports = List.copyOf(sports);
        this.sportsLabel = sports.isEmpty() ? UNSET : String.join(", ", sports);
        this.capacityLabel = UNSET;
        this.concurrentMatchLimit = concurrentMatchLimit == null ? 0 : concurrentMatchLimit;
        this.statusLabel = isBlank(status) ? "상태 미설정" : status.strip();
        this.statusTone = statusTone(status);
    }

    public static FacilityListItem from(FacilitySummary facility) {
        List<String> sports = splitSports(facility.getSportsLabel());
        return new FacilityListItem(
                facility.getFacilityId(),
                facility.getName(),
                facility.getAddress(),
                sports,
                facility.getConcurrentMatchLimit(),
                facility.getStatus());
    }

    private static List<String> splitSports(String sportsLabel) {
        if (isBlank(sportsLabel)) {
            return List.of();
        }
        List<String> sports = new ArrayList<>();
        for (String sport : Arrays.asList(sportsLabel.split(","))) {
            if (!isBlank(sport)) {
                sports.add(sport.strip());
            }
        }
        return sports;
    }

    private static String statusTone(String status) {
        if (isBlank(status)) {
            return "neutral";
        }
        String normalized = status.replace(" ", "").strip();
        return switch (normalized) {
            case "운영중", "정상", "운영" -> "good";
            case "점검중", "점검", "공사중", "일시휴관" -> "warn";
            case "운영중지", "사용중지", "폐쇄", "중지" -> "bad";
            default -> "neutral";
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
