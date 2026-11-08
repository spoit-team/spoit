package co.kr.spoit.match.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * 탈퇴 차단용 개인 매치 신청 조회 SQL이 MyBatis에 바인딩되고 상태 조건을 실제로 거는지 확인한다.
 *
 * <p>DB에 연결하지 않으므로 전용 DB 환경변수가 없어도 실행된다.</p>
 */
class MatchApplicationMapperWithdrawalBlockTest {

    private static final String MAPPER_RESOURCE = "mapper/match/MatchApplicationMapper.xml";

    @Test
    @DisplayName("탈퇴 차단용 개인 매치 신청 조회 문장이 인터페이스에 바인딩된다")
    void statementIsBound() throws Exception {
        Configuration configuration = new Configuration();
        configuration.addMapper(MatchApplicationMapper.class);
        try (InputStream stream = resource()) {
            new XMLMapperBuilder(stream, configuration, MAPPER_RESOURCE, configuration.getSqlFragments())
                    .parse();
        }

        assertThat(configuration.hasStatement(
                MatchApplicationMapper.class.getName() + ".findActiveIndividualMatchTitles")).isTrue();
    }

    @Test
    @DisplayName("차단 조회는 개인 신청·신청자 본인 조건과 매치 id 기준 중복 제거를 건다")
    void queryFiltersIndividualApplicationsOfApplicant() throws Exception {
        String sql = statement("findActiveIndividualMatchTitles").getTextContent();

        // 대기 신청은 match_participant 행이 없을 수 있으므로 신청자 컬럼으로 찾아야 한다.
        assertThat(sql).contains("ma.applicant_user_id = #{userId}");
        assertThat(sql).doesNotContain("ma.application_type");
        assertThat(sql).contains("ma.status IN");
        assertThat(sql).contains("sm.status IN");
        // 중복은 경기 id로 접는다. 제목으로 접으면 제목이 같은 다른 경기가 사라진다.
        assertThat(sql).contains("sm.match_id AS matchId");
        assertThat(sql).contains("GROUP BY sm.match_id, sm.title");
        assertThat(sql).doesNotContain("DISTINCT sm.title");
        // 매칭방 상태는 파라미터로만 받아 종료·취소가 실수로 포함되지 않게 한다.
        assertThat(sql).doesNotContain("'모집 중'");
        assertThat(sql).doesNotContain("'매칭 완료'");
        assertThat(sql).doesNotContain("'종료'");
    }

    private static Element statement(String id) throws Exception {
        Document document = readDocument();
        NodeList statements = document.getElementsByTagName("select");
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
        InputStream stream = MatchApplicationMapperWithdrawalBlockTest.class.getClassLoader()
                .getResourceAsStream(MAPPER_RESOURCE);
        assertThat(stream).as("매퍼 XML 리소스 %s", MAPPER_RESOURCE).isNotNull();
        return stream;
    }
}
