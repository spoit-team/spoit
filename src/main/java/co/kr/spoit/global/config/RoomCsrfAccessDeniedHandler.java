package co.kr.spoit.global.config;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.SessionFlashMapManager;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 매칭방 생성·수정·취소({@code POST /admin/rooms/**}) 요청이 CSRF 토큰 불일치로 막히면
 * 403 기본 화면 대신 매칭방 관리 화면으로 돌려보내고 안내 모달을 띄운다.
 *
 * <p>CSRF 토큰은 세션에 있으므로 서버 재시작·세션 만료 뒤 예전 화면에서 저장하면 이 경우가 된다.
 * 안내는 {@code RoomAdminController}와 같은 flash 속성({@code roomErrorTitle}, {@code roomError})으로
 * 넘긴다. 필터 단계라 DispatcherServlet의 FlashMapManager를 쓸 수 없어, 같은 기본 구현인
 * {@link SessionFlashMapManager}로 직접 저장한다. 그 밖의 거부(권한 없음 등)는 기본 처리(403)를 따른다.</p>
 */
public class RoomCsrfAccessDeniedHandler implements AccessDeniedHandler {

    static final String SESSION_EXPIRED_MESSAGE = "세션이 만료되었습니다. 다시 시도해 주세요.";

    private static final String ROOMS_PATH = "/admin/rooms";
    private static final String BOOKINGS_PATH = "/admin/facility";

    private final AccessDeniedHandler fallback = new AccessDeniedHandlerImpl();
    private final SessionFlashMapManager flashMapManager = new SessionFlashMapManager();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException, jakarta.servlet.ServletException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!(exception instanceof CsrfException) || !isRoomPath(path)) {
            fallback.handle(request, response, exception);
            return;
        }
        FlashMap flashMap = new FlashMap();
        flashMap.put("roomErrorTitle", titleFor(path));
        flashMap.put("roomError", SESSION_EXPIRED_MESSAGE);
        flashMap.setTargetRequestPath(BOOKINGS_PATH);
        flashMap.addTargetRequestParam("view", "bookings");
        flashMapManager.saveOutputFlashMap(flashMap, request, response);
        response.sendRedirect(request.getContextPath() + BOOKINGS_PATH + "?view=bookings#facility-bookings");
    }

    private static boolean isRoomPath(String path) {
        return path.equals(ROOMS_PATH) || path.startsWith(ROOMS_PATH + "/");
    }

    /** RoomAdminController의 실패 제목과 같은 문구를 쓴다. */
    private static String titleFor(String path) {
        if (path.equals(ROOMS_PATH)) {
            return "매칭방을 만들 수 없습니다";
        }
        if (path.endsWith("/cancel")) {
            return "매칭방을 취소할 수 없습니다";
        }
        return "매칭방을 수정할 수 없습니다";
    }
}
