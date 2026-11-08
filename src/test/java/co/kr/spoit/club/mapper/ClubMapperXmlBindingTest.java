package co.kr.spoit.club.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * 회원 탈퇴 차단 사유 조회 SQL이 MyBatis에 바인딩되고, 각 카테고리 조건을 실제로 거는지 확인한다.
 *
 * <p>
 * DB에 연결하지 않으므로 전용 DB 환경변수가 없어도 실행된다.
 * </p>
 */
class ClubMapperXmlBindingTest {

    private static final String MAPPER_RESOURCE = "mapper/club/ClubMapper.xml";

    @Test
    @DisplayName("탈퇴 차단 사유 조회 문장이 인터페이스에 바인딩된다")
    void withdrawalBlockStatementsAreBound() throws Exception {
        Configuration configuration = new Configuration();
        configuration.addMapper(ClubMapper.class);
        try (InputStream stream = resource()) {
            new XMLMapperBuilder(stream, configuration, MAPPER_RESOURCE, configuration.getSqlFragments())
                    .parse();
        }

        assertThat(configuration.hasStatement(
                ClubMapper.class.getName() + ".selectLeaderClubBlocks")).isTrue();

    }

    @Test
    @DisplayName("가입 사유는 활성 소속(동호회장·동호회원)이고 해체된 동호회는 제외한다")
    void joinedClubNamesQueryFiltersActiveMember() throws Exception {
        String sql = statement("selectLeaderClubBlocks").getTextContent();

        assertThat(sql).doesNotContain("m.role");
        assertThat(sql).contains("m.left_at IS NULL");
        assertThat(sql).contains("c.deleted_at IS NULL");
        // 같은 이름의 다른 동호회가 가려지지 않도록 동호회 id 기준으로 접는다.
        assertThat(sql).contains("c.club_id AS clubId");
        assertThat(sql).contains("GROUP BY c.club_id, c.club_name");
    }

    private static Element statement(String id) throws Exception {
        NodeList statements = readDocument().getElementsByTagName("select");
        for (int i = 0; i < statements.getLength(); i++) {
            Element statement = (Element) statements.item(i);
            if (id.equals(statement.getAttribute("id"))) {
                return statement;
            }
        }
        throw new AssertionError("매퍼 문장을 찾을 수 없습니다: " + id);
    }

    private static Document readDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        try (InputStream stream = resource()) {
            return factory.newDocumentBuilder().parse(stream);
        }
    }

    private static InputStream resource() {
        InputStream stream = ClubMapperXmlBindingTest.class.getClassLoader()
                .getResourceAsStream(MAPPER_RESOURCE);
        assertThat(stream).as("매퍼 XML 리소스 %s", MAPPER_RESOURCE).isNotNull();
        return stream;
    }
}
