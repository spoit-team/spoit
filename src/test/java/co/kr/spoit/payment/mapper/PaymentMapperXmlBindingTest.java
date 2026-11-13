package co.kr.spoit.payment.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.lang.reflect.Method;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * 결제 매퍼 SQL이 인터페이스에 모두 바인딩되고, 상태 전이가 "현재 상태" 조건을 거는지 확인한다.
 * DB에 연결하지 않는다.
 */
class PaymentMapperXmlBindingTest {

    private static final String MAPPER_RESOURCE = "mapper/payment/PaymentMapper.xml";

    @Test
    @DisplayName("인터페이스의 모든 메서드에 SQL 문장이 있다")
    void everyMethodIsBound() throws Exception {
        Configuration configuration = new Configuration();
        configuration.addMapper(PaymentMapper.class);
        try (InputStream stream = resource()) {
            new XMLMapperBuilder(stream, configuration, MAPPER_RESOURCE, configuration.getSqlFragments()).parse();
        }
        for (Method method : PaymentMapper.class.getDeclaredMethods()) {
            assertThat(configuration.hasStatement(PaymentMapper.class.getName() + "." + method.getName()))
                    .as(method.getName())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("결제 상태 전이는 현재 상태 조건으로만 바꾼다")
    void transitionsAreConditional() throws Exception {
        assertThat(statement("markPaid")).contains("AND status = 'READY'");
        assertThat(statement("markFailed")).contains("AND status = 'READY'");
        assertThat(statement("markRefundedUnapplied")).contains("AND status = 'READY'");
        assertThat(statement("saveTid")).contains("AND status = 'READY'");
        assertThat(statement("addRefunded"))
                .contains("AND status IN ('PAID', 'PARTIAL_REFUNDED')")
                .contains("refunded_amount + #{amount} <= total_amount");
        // SET 절은 왼쪽부터 적용되므로 status를 refunded_amount보다 먼저 계산해야 한다.
        String addRefunded = statement("addRefunded");
        assertThat(addRefunded.indexOf("SET status")).isLessThan(addRefunded.indexOf("refunded_amount = refunded_amount"));
    }

    @Test
    @DisplayName("이미 환불한 신청 행은 다시 환불 대상이 되지 않는다")
    void refundTargetsExcludeRefundedRows() throws Exception {
        assertThat(statement("findRefundableApplications")).contains("ma.refunded_at IS NULL");
        assertThat(statement("findRefundableApplicationsOfMatch")).contains("ma.refunded_at IS NULL");
        assertThat(statement("markApplicationsRefunded")).contains("WHERE refunded_at IS NULL");
        assertThat(statement("linkApplications")).contains("WHERE payment_id IS NULL");
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
        InputStream stream = PaymentMapperXmlBindingTest.class.getClassLoader().getResourceAsStream(MAPPER_RESOURCE);
        assertThat(stream).isNotNull();
        return stream;
    }
}
