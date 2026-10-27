package co.kr.spoit.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.DefaultResourceLoader;

import co.kr.spoit.web.CorePageController;

/**
 * 테스트 클래스가 런타임 클래스패스에 포함된 상태에서도 애플리케이션의 일반 컴포넌트 스캔이
 * 보안 테스트 전용 중첩 구성({@code SecurityConfigTest.StubRoutes})을 후보로 잡지 않는지 검증한다.
 *
 * <p>테스트 슬라이스의 타입 제외 필터에 의존하지 않고, {@code @ComponentScan}이 사용하는
 * 기본 필터로 직접 스캔한다.</p>
 */
class ComponentScanIsolationTest {

    @Test
    @DisplayName("애플리케이션 컴포넌트 스캔은 보안 테스트 전용 중첩 구성을 등록하지 않는다")
    void testOnlyConfigurationIsNotPartOfApplicationComponentScan() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(true);
        scanner.setResourceLoader(new DefaultResourceLoader(getClass().getClassLoader()));

        Set<String> scannedClassNames = scanner.findCandidateComponents("co.kr.spoit").stream()
                .map(BeanDefinition::getBeanClassName)
                .collect(Collectors.toSet());

        // 스캔 자체가 동작하는지 확인하는 양성 대조군이다.
        assertThat(scannedClassNames).contains(CorePageController.class.getName());
        // 테스트 클래스패스에 테스트 전용 구성이 존재해도 애플리케이션 스캔 후보에는 없어야 한다.
        assertThat(scannedClassNames)
                .noneMatch(name -> name.startsWith(SecurityConfigTest.class.getName()));
    }
}
