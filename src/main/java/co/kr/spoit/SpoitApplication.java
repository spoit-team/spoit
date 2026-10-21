package co.kr.spoit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spoit 애플리케이션의 실행 진입점이다.
 *
 * <p>{@link SpringBootApplication}은 컴포넌트 스캔, 자동 설정, 설정 클래스 등록을
 * 하나로 묶는 메타 애노테이션이며, 기준 패키지는 {@code co.kr.spoit}이다.
 * 컨트롤러·설정·서비스는 이 패키지 하위에 적절한 스테레오타입 애노테이션을
 * 선언하면 스캔 대상이 된다. MyBatis 매퍼는 별도로 {@code @Mapper} 또는
 * {@code @MapperScan}을 통한 등록이 필요하다.</p>
 */
@SpringBootApplication
public class SpoitApplication {

	/**
	 * 내장 서블릿 컨테이너를 기동하고 스프링 컨텍스트를 초기화한다.
	 *
	 * @param args 실행 시 전달되는 커맨드라인 인자
	 */
	public static void main(String[] args) {
		SpringApplication.run(SpoitApplication.class, args);
	}

}
