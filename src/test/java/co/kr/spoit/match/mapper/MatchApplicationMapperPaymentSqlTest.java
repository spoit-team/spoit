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

/** 결제 관련 신청 SQL이 바인딩되고, 시작 후 반려는 결제한 대기 신청만 대상으로 하는지 확인한다. DB에 연결하지 않는다. */
class MatchApplicationMapperPaymentSqlTest {

    private static final String MAPPER_RESOURCE = "mapper/match/MatchApplicationMapper.xml";

    @Test
    @DisplayName("시작 후 미확정 반려 조회 문장이 인터페이스에 바인딩된다")
    void statementsAreBound() throws Exception {
        Configuration configuration = new Configuration();
        configuration.addMapper(MatchApplicationMapper.class);
        try (InputStream stream = resource()) {
            new XMLMapperBuilder(stream, configuration, MAPPER_RESOURCE, configuration.getSqlFragments()).parse();
        }
        assertThat(configuration.hasStatement(MatchApplicationMapper.class.getName() + ".findStartedMatchIdsWithWaiting")).isTrue();
        assertThat(configuration.hasStatement(MatchApplicationMapper.class.getName() + ".findWaitingApplications")).isTrue();
    }

    @Test
    @DisplayName("무료 대기 신청은 건드리지 않도록 결제한 대기 신청만 찾는다")
    void onlyPaidWaitingApplications() throws Exception {
        assertThat(statement("findStartedMatchIdsWithWaiting"))
                .contains("ma.payment_id IS NOT NULL")
                .contains("sm.start_at <= #{now}")
                .contains("sm.status <> #{cancelledMatchStatus}");
        assertThat(statement("findWaitingApplications")).contains("payment_id IS NOT NULL");
    }

    @Test
    @DisplayName("결제 금액 검증을 위해 매칭방 잠금 조회가 참가비를 읽는다")
    void lockMatchReadsFee() throws Exception {
        assertThat(statement("lockMatch")).contains("sm.fee_per_person").contains("FOR UPDATE OF sm");
    }

    private static String statement(String id) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        Document document;
        try (InputStream stream = resource()) {
            document = factory.newDocumentBuilder().parse(stream);
        }
        NodeList nodes = document.getDocumentElement().getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element element && id.equals(element.getAttribute("id"))) {
                return element.getTextContent().replaceAll("\\s+", " ");
            }
        }
        throw new AssertionError("statement not found: " + id);
    }

    private static InputStream resource() {
        InputStream stream = MatchApplicationMapperPaymentSqlTest.class.getClassLoader()
                .getResourceAsStream(MAPPER_RESOURCE);
        assertThat(stream).isNotNull();
        return stream;
    }
}
