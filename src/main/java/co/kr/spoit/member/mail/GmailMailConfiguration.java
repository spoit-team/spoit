package co.kr.spoit.member.mail;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GmailMailProperties.class)
public class GmailMailConfiguration {
}
