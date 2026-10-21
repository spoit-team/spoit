package co.kr.spoit.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 사이트 루트 경로를 매칭 목록 화면으로 연결한다.
 *
 * <p>리다이렉트가 아닌 서버 내부 포워드({@code forward:})를 사용하므로
 * 브라우저 주소는 {@code /}로 유지되고, {@code /matches}의 컨트롤러가
 * 모델과 뷰를 그대로 처리한다. 즉 별도의 루트 전용 뷰나 모델은 없다.</p>
 */
@Controller
public class HomeController {

    /**
     * 루트 요청을 매칭 목록 페이지로 포워드한다.
     *
     * @return 매칭 목록 핸들러로의 포워드 뷰 이름
     */
    @GetMapping("/")
    public String home() {
        return "forward:/matches";
    }
}
