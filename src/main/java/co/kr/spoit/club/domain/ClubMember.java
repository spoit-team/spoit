package co.kr.spoit.club.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter 
@Setter 
@NoArgsConstructor 
public class ClubMember {

    private Long userId;
    private String role;
    private String nickname;
    private LocalDateTime joinedAt;

}
