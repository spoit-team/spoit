package co.kr.spoit.facility.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 신규 facility XML 매퍼가 인터페이스와 실제로 바인딩되는지 확인한다. */
@SpringBootTest
class FacilityMapperXmlBindingTest {

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Test
    @DisplayName("FacilityMapper XML 문이 인터페이스 네임스페이스에 바인딩된다")
    void xmlStatementIsBoundToInterface() {
        var configuration = sqlSessionFactory.getConfiguration();

        assertThat(configuration.hasMapper(FacilityMapper.class)).isTrue();
        assertThat(configuration.hasStatement(
                "co.kr.spoit.facility.mapper.FacilityMapper.selectManagedFacilities")).isTrue();
    }
}
