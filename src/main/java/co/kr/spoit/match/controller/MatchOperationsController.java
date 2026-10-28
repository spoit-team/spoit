package co.kr.spoit.match.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import co.kr.spoit.web.AdminShellModel;

/**
 * 시설 관리자의 매칭 참가자 운영 화면 컨트롤러다.
 *
 * <p>{@code CorePageController}에서 시설 관리자 전용 운영 화면을 분리했다. 승인·배정·완료 같은
 * 변경 흐름은 아직 구현되어 있지 않으므로, 화면에 {@code match=null}만 전달한다.</p>
 */
@Controller
public class MatchOperationsController {

    /** 운영 관리 화면을 렌더링한다. 관리자 셸과 match=null만 전달한다. */
    @GetMapping("/admin/operations")
    public String adminOperations(Model model) {
        AdminShellModel.apply(model);
        model.addAttribute("match", null);
        return "admin/operations";
    }
}
