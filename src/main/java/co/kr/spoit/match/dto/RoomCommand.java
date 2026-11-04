package co.kr.spoit.match.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

/**
 * 매칭방 저장 파라미터다. 서비스가 검증·정규화를 마친 값만 담는다.
 *
 * <p>생성 시 {@code matchId}는 {@code useGeneratedKeys}로 채워진다. 일정을 잠근 수정에서는
 * 시설·종목·시작·종료 값을 쓰지 않는다. 수정에서 {@code photoChanged}가 false이면 사진 URL을 그대로 둔다.</p>
 */
@Getter
@Builder
public class RoomCommand {

    private Long matchId;
    private final Long facilityId;
    private final Long sportId;
    private final Long createdBy;
    private final String title;
    private final LocalDateTime startAt;
    private final LocalDateTime endAt;
    private final int minParticipants;
    private final int maxParticipants;
    private final int teamACapacity;
    private final int teamBCapacity;
    private final int feePerPerson;
    private final String requiredSkill;
    private final String status;
    /** 시설 사진 공개 URL. 없으면 null */
    private final String photoUrl;
    /** 수정 때 사진 URL을 바꿀지 여부 */
    private final boolean photoChanged;

    /** MyBatis {@code useGeneratedKeys}가 생성 ID를 돌려줄 때 쓴다. */
    public void setMatchId(Long matchId) {
        this.matchId = matchId;
    }
}
