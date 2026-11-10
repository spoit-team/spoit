package co.kr.spoit.payment.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 결제 설정 등록 */
@Configuration
@EnableConfigurationProperties(KakaoPayProperties.class)
public class PaymentConfig {
}
