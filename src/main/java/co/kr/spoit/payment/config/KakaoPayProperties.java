package co.kr.spoit.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * 카카오페이 단건 결제 설정. 비밀 키는 환경 변수 KAKAOPAY_SECRET_KEY로만 주입하고 소스에 두지 않는다.
 *
 * <p>비밀 키가 없으면 유료 매치 결제를 열지 않는다. 가맹점 코드(CID)는 테스트 결제용 TC0ONETIME이 기본값이다.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "spoit.payment.kakaopay")
public class KakaoPayProperties {

    /** 개발자센터에서 발급한 Secret key (dev는 테스트 결제용) */
    private String secretKey = "";
    private String cid = "TC0ONETIME";
    private String apiBaseUrl = "https://open-api.kakaopay.com";
    /** 테스트 결제인지. 신청 화면에 "실제 금액이 청구되지 않습니다" 안내를 띄운다. */
    private boolean testMode = true;
    private int connectTimeoutSeconds = 5;
    private int readTimeoutSeconds = 15;

    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank();
    }
}
