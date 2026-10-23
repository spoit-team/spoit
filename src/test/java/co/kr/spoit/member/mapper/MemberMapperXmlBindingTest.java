package co.kr.spoit.member.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * XML 매퍼가 resources/mapper/member 하위에 있어도 실제로 바인딩되는지 확인한다.
 * mapper-locations 패턴이 바뀌면 이 테스트가 먼저 깨진다.
 */
@SpringBootTest
class MemberMapperXmlBindingTest {

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Test
    void xmlStatementIsBoundToInterface() {
        var configuration = sqlSessionFactory.getConfiguration();

        assertThat(configuration.hasMapper(MemberMapper.class)).isTrue();
        assertThat(configuration.hasStatement("co.kr.spoit.member.mapper.MemberMapper.findByEmail"))
                .isTrue();
        assertThat(configuration.hasStatement("co.kr.spoit.member.mapper.MemberMapper.insert"))
                .isTrue();
        assertThat(configuration
                .hasStatement("co.kr.spoit.member.mapper.MemberMapper.findSignupRegions"))
                .isTrue();
        assertThat(configuration
                .hasStatement("co.kr.spoit.member.mapper.MemberMapper.regionExists"))
                .isTrue();
        assertThat(configuration
                .hasStatement("co.kr.spoit.member.mapper.MemberMapper.insertUserRegion"))
                .isTrue();
        assertThat(configuration
                .hasStatement("co.kr.spoit.member.mapper.MemberMapper.findRegions"))
                .isTrue();
        assertThat(configuration
                .hasStatement("co.kr.spoit.member.mapper.MemberMapper.findSports"))
                .isTrue();
        assertThat(configuration
                .hasStatement("co.kr.spoit.member.mapper.MemberMapper.sportExists"))
                .isTrue();
        assertThat(configuration
                .hasStatement("co.kr.spoit.member.mapper.MemberMapper.findRepresentativeSport"))
                .isTrue();
        assertThat(configuration
                .hasStatement("co.kr.spoit.member.mapper.MemberMapper.updateProfileBasics"))
                .isTrue();
        assertThat(configuration
                .hasStatement("co.kr.spoit.member.mapper.MemberMapper.upsertUserPreferredSport"))
                .isTrue();
        assertThat(configuration
                .hasStatement("co.kr.spoit.member.mapper.MemberMapper.deleteUserPreferredSport"))
                .isTrue();
        assertThat(configuration
                .hasStatement("co.kr.spoit.member.mapper.MemberMapper.deleteUserRegions"))
                .isTrue();
    }
}
