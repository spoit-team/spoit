package co.kr.spoit.match.dto;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import lombok.Getter;
import lombok.Setter;

/**
 * 시설 관리자 매칭방 생성·수정 폼 값이다.
 *
 * <p>입력값 검증은 {@code RoomCommandService}가 담당한다(이 저장소 컨벤션). 신청자가 있는 방을
 * 수정할 때는 화면이 시설·종목·날짜·시간 입력을 비활성화하므로 해당 값이 비어서 올 수 있고,
 * 서비스도 그 값을 쓰지 않는다. 최소 인원은 정책에서 빠져 입력받지 않는다.</p>
 */
@Getter
@Setter
public class RoomForm {

    private Long facilityId;

    private Long sportId;

    private String title;

    /** 이용 날짜, ISO 형식(yyyy-MM-dd) */
    private String date;

    /** 선택한 1시간 블록의 시작 시각("HH:00") 목록. 연속이어야 한다. */
    private List<String> hours;

    /** 팀당 정원. A팀·B팀 정원은 항상 같으므로 하나만 받는다. */
    private Integer teamCapacity;

    private Integer feePerPerson;

    /** social_match.required_skill 값(초급·중급·고급) */
    private String requiredSkill;

    /** 시설 사진(선택). 수정 때 비어 있으면 기존 사진을 유지한다. */
    private MultipartFile photo;

    /** 수정 때 기존 사진을 지운다. 새 사진을 함께 올리면 새 사진으로 바꾼다. */
    private boolean removePhoto;
}
