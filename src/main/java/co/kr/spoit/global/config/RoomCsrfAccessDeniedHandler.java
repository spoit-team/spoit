package co.kr.spoit.global.config;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.SessionFlashMapManager;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import co.kr.spoit.global.common.FlashFeedback;

/**
 * 매칭방 생성·수정·취소({@code POST /admin/rooms/**}) 요청이 CSRF 토큰 불일치로 막히면
 * 403 기본 화면 대신 매칭방 관리 화면으로 돌려보내고 안내 모달을 띄운다.
 *
 * <p>CSRF 토큰은 세션에 있으므로 서버 재시작·세션 만료 뒤 예전 화면에서 저장하면 이 경우가 된다.
 * 안내는 {@code RoomAdminController}와 같은 공용 실패 안내 flash 속성({@code errorTitle}, {@code error})으로
 * 넘긴다. 필터 단계라 DispatcherServlet의 FlashMapManager를 쓸 수 없어, 같은 기본 구현인
 * {@link SessionFlashMapManager}로 직접 저장한다. 그 밖의 브라우저 폼 CSRF 실패는 같은 출처 화면으로
 * 돌아가고 UI가 일회성 안내 표식을 소비한다. 권한 없음 등 CSRF 이외의 거부는 기본 처리(403)를 따른다.</p>
 */
public class RoomCsrfAccessDeniedHandler implements AccessDeniedHandler {

    static final String SESSION_EXPIRED_MESSAGE = "세션이 만료되었습니다. 다시 시도해 주세요.";

    private static final String ROOMS_PATH = "/admin/rooms";
    private static final String BOOKINGS_PATH = "/admin/facility";
    private static final String RECOVERY_QUERY_PARAM = "csrfRecovery";
    private static final MediaType XHTML = MediaType.parseMediaType("application/xhtml+xml");

    private final AccessDeniedHandler fallback = new AccessDeniedHandlerImpl();
    private final SessionFlashMapManager flashMapManager = new SessionFlashMapManager();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException, jakarta.servlet.ServletException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (exception instanceof CsrfException && isRoomPath(path)) {
            FlashMap flashMap = new FlashMap();
            flashMap.put(FlashFeedback.ERROR_TITLE, titleFor(path));
            flashMap.put(FlashFeedback.ERROR, SESSION_EXPIRED_MESSAGE);
            flashMap.setTargetRequestPath(BOOKINGS_PATH);
            flashMap.addTargetRequestParam("view", "bookings");
            flashMapManager.saveOutputFlashMap(flashMap, request, response);
            response.sendRedirect(request.getContextPath() + BOOKINGS_PATH + "?view=bookings#facility-bookings");
            return;
        }
        if (exception instanceof CsrfException && recoverBrowserFormPost(request, response)) {
            return;
        }
        fallback.handle(request, response, exception);
    }

    /** 잘못된 POST를 재실행하지 않고, 브라우저 폼 제출만 원래의 같은 출처 GET으로 돌려보낸다. */
    private boolean recoverBrowserFormPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (!isBrowserFormPost(request)) {
            return false;
        }
        SafeDestination destination = safeRefererDestination(request);
        if (destination == null) {
            return false;
        }

        String location = destination.rawPath() + "?" + queryWithRecoveryMarker(destination.rawQuery());
        response.setStatus(HttpServletResponse.SC_SEE_OTHER);
        response.setHeader("Location", response.encodeRedirectURL(location));
        return true;
    }

    private static String queryWithRecoveryMarker(String rawQuery) {
        List<String> retainedParameters = new ArrayList<>();
        if (rawQuery != null && !rawQuery.isEmpty()) {
            for (String parameter : rawQuery.split("&", -1)) {
                String rawName = parameter.split("=", 2)[0];
                String name;
                try {
                    name = URLDecoder.decode(rawName, StandardCharsets.UTF_8);
                } catch (IllegalArgumentException exception) {
                    name = rawName;
                }
                if (!RECOVERY_QUERY_PARAM.equals(name)) {
                    retainedParameters.add(parameter);
                }
            }
        }
        retainedParameters.add(RECOVERY_QUERY_PARAM + "=1");
        return String.join("&", retainedParameters);
    }

    private static boolean isBrowserFormPost(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod()) || !acceptsHtml(request.getHeader("Accept"))) {
            return false;
        }
        String contentType = request.getContentType();
        if (contentType == null) {
            return false;
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            return MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(mediaType)
                    || MediaType.MULTIPART_FORM_DATA.isCompatibleWith(mediaType)
                    || MediaType.TEXT_PLAIN.isCompatibleWith(mediaType);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean acceptsHtml(String acceptHeader) {
        if (acceptHeader == null || acceptHeader.isBlank()) {
            return false;
        }
        try {
            return MediaType.parseMediaTypes(acceptHeader).stream()
                    .anyMatch(mediaType -> mediaType.getQualityValue() > 0
                            && isHtmlMediaType(mediaType));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isHtmlMediaType(MediaType mediaType) {
        return ("text".equalsIgnoreCase(mediaType.getType())
                        && "html".equalsIgnoreCase(mediaType.getSubtype()))
                || XHTML.getType().equalsIgnoreCase(mediaType.getType())
                        && XHTML.getSubtype().equalsIgnoreCase(mediaType.getSubtype());
    }

    private static SafeDestination safeRefererDestination(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(referer);
            String requestScheme = request.getScheme();
            String requestHost = request.getServerName();
            if (!uri.isAbsolute() || uri.isOpaque() || uri.getHost() == null || uri.getRawUserInfo() != null
                    || !("http".equalsIgnoreCase(requestScheme) || "https".equalsIgnoreCase(requestScheme))
                    || !requestScheme.equalsIgnoreCase(uri.getScheme())
                    || !requestHost.equalsIgnoreCase(uri.getHost())
                    || effectivePort(uri.getScheme(), uri.getPort())
                            != effectivePort(requestScheme, request.getServerPort())) {
                return null;
            }

            String rawPath = uri.getRawPath();
            String targetPath = uri.getPath();
            if (rawPath == null || rawPath.isEmpty()) {
                rawPath = "/";
            }
            if (targetPath == null || targetPath.isEmpty()) {
                targetPath = "/";
            }
            String contextPath = request.getContextPath();
            if (!isSafeLocalPath(rawPath, targetPath, contextPath)) {
                return null;
            }
            return new SafeDestination(rawPath, uri.getRawQuery());
        } catch (URISyntaxException | IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean isSafeLocalPath(String rawPath, String targetPath, String contextPath) {
        if (!rawPath.startsWith("/") || rawPath.startsWith("//")
                || !targetPath.startsWith("/") || targetPath.startsWith("//")
                || targetPath.indexOf('\\') >= 0) {
            return false;
        }
        for (String segment : targetPath.split("/", -1)) {
            if (".".equals(segment) || "..".equals(segment)) {
                return false;
            }
        }
        return contextPath.isEmpty() || targetPath.equals(contextPath) || targetPath.startsWith(contextPath + "/");
    }

    private static int effectivePort(String scheme, int port) {
        if (port >= 0) {
            return port;
        }
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
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

    private record SafeDestination(String rawPath, String rawQuery) {
    }
}
