package co.kr.spoit.payment.kakao;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.kr.spoit.payment.config.KakaoPayProperties;

/**
 * 카카오페이 온라인 단건 결제 API 클라이언트.
 *
 * <p>호스트 open-api.kakaopay.com, 인증 헤더 {@code Authorization: SECRET_KEY <키>}, 요청·응답은 JSON이다.
 * 응답은 필요한 필드만 읽고 모르는 필드는 무시한다.</p>
 */
@Component
public class RestKakaoPayClient implements KakaoPayClient {

    private static final String READY = "/online/v1/payment/ready";
    private static final String APPROVE = "/online/v1/payment/approve";
    private static final String ORDER = "/online/v1/payment/order";
    private static final String CANCEL = "/online/v1/payment/cancel";

    private final KakaoPayProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public RestKakaoPayClient(KakaoPayProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()));
        this.restClient = RestClient.builder()
                .baseUrl(properties.getApiBaseUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public ReadyResult ready(String partnerOrderId, String partnerUserId, String itemName, int quantity,
                             long totalAmount, String approvalUrl, String cancelUrl, String failUrl) {
        Map<String, Object> body = base();
        body.put("partner_order_id", partnerOrderId);
        body.put("partner_user_id", partnerUserId);
        body.put("item_name", itemName);
        body.put("quantity", quantity);
        body.put("total_amount", totalAmount);
        body.put("tax_free_amount", 0);
        body.put("approval_url", approvalUrl);
        body.put("cancel_url", cancelUrl);
        body.put("fail_url", failUrl);
        JsonNode root = post(READY, body);
        return new ReadyResult(text(root, "tid"), text(root, "next_redirect_pc_url"),
                text(root, "next_redirect_mobile_url"));
    }

    @Override
    public ApproveResult approve(String tid, String partnerOrderId, String partnerUserId, String pgToken) {
        Map<String, Object> body = base();
        body.put("tid", tid);
        body.put("partner_order_id", partnerOrderId);
        body.put("partner_user_id", partnerUserId);
        body.put("pg_token", pgToken);
        JsonNode root = post(APPROVE, body);
        return new ApproveResult(root.path("amount").path("total").asLong(0), parseTime(text(root, "approved_at")));
    }

    @Override
    public OrderResult order(String tid) {
        Map<String, Object> body = base();
        body.put("tid", tid);
        JsonNode root = post(ORDER, body);
        return new OrderResult(text(root, "status"), root.path("amount").path("total").asLong(0),
                root.path("canceled_amount").path("total").asLong(0));
    }

    @Override
    public void cancel(String tid, long cancelAmount, long cancelAvailableAmount) {
        Map<String, Object> body = base();
        body.put("tid", tid);
        body.put("cancel_amount", cancelAmount);
        body.put("cancel_tax_free_amount", 0);
        body.put("cancel_available_amount", cancelAvailableAmount);
        post(CANCEL, body);
    }

    private Map<String, Object> base() {
        if (!properties.isConfigured()) {
            throw new KakaoPayException("NOT_CONFIGURED", "카카오페이 비밀 키가 설정되지 않았습니다.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cid", properties.getCid());
        return body;
    }

    private JsonNode post(String path, Map<String, Object> body) {
        try {
            String response = restClient.post()
                    .uri(path)
                    .header("Authorization", "SECRET_KEY " + properties.getSecretKey().strip())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(response == null ? "{}" : response);
        } catch (RestClientResponseException e) {
            throw toException(e);
        } catch (ResourceAccessException e) {
            throw new KakaoPayException("NETWORK", "카카오페이에 연결하지 못했습니다.", e);
        } catch (KakaoPayException e) {
            throw e;
        } catch (Exception e) {
            throw new KakaoPayException("INVALID_RESPONSE", "카카오페이 응답을 해석하지 못했습니다.", e);
        }
    }

    KakaoPayException toException(RestClientResponseException e) {
        String code = "HTTP_" + e.getStatusCode().value();
        String message = "카카오페이 요청이 실패했습니다. (" + e.getStatusCode().value() + ")";
        try {
            JsonNode root = objectMapper.readTree(e.getResponseBodyAsString());
            if (root != null && root.hasNonNull("error_code")) {
                code = root.get("error_code").asText();
            }
            if (root != null && root.hasNonNull("error_message")) {
                message = root.get("error_message").asText();
            }
        } catch (Exception ignored) {
            // 본문이 JSON이 아니면 상태 코드만 남긴다.
        }
        return new KakaoPayException(code, message, e);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    /** 카카오페이 시각은 "2026-11-08T14:03:00" 같은 한국 시간이다. */
    static LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.length() > 19 ? value.substring(0, 19) : value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
