package co.kr.spoit.report.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * 제재 매퍼 XML이 MyBatis에서 파싱되고 인터페이스와 정확히 대응하는지 확인한다.
 *
 * <p>DB에 연결하지 않으므로 전용 DB 환경변수가 없어도 실행된다. 목록이 기간 만료를 DB 시각 기준으로
 * 판정하고, 조기 해제가 '적용 중' 행만 바꾸며, 저장 문장이 영구 정지({@code end_at NULL})를 만들지
 * 않는지 고정한다.</p>
 */
class PenaltyMapperXmlBindingTest {

    private static final String MAPPER_RESOURCE = "mapper/report/PenaltyMapper.xml";

    @Test
    @DisplayName("XML 문장이 인터페이스 메서드와 1:1로 대응하고 MyBatis가 파싱한다")
    void statementsMatchInterfaceMethods() throws Exception {
        Configuration configuration = new Configuration();
        configuration.addMapper(PenaltyMapper.class);
        try (InputStream stream = resource()) {
            new XMLMapperBuilder(stream, configuration, MAPPER_RESOURCE, configuration.getSqlFragments())
                    .parse();
        }

        assertThat(configuration.hasMapper(PenaltyMapper.class)).isTrue();
        Set<String> bound = new LinkedHashSet<>();
        for (String name : statementIds()) {
            String statementId = PenaltyMapper.class.getName() + "." + name;
            assertThat(configuration.hasStatement(statementId)).as(statementId).isTrue();
            bound.add(name);
        }
        Set<String> methods = Arrays.stream(PenaltyMapper.class.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .map(Method::getName)
                .collect(Collectors.toSet());
        assertThat(bound).containsExactlyInAnyOrderElementsOf(methods);
    }

    @Test
    @DisplayName("목록 조회는 검색·상태 조건과 기간 만료 판정을 DB 시각 기준으로 유지한다")
    void listQueryKeepsFiltersAndExpiryCheck() throws Exception {
        String sql = statement("findPenalties").getTextContent();

        assertThat(sql).contains("p.status = '적용 중'");
        assertThat(sql).contains("p.end_at > #{now}");
        assertThat(sql).contains("LIMIT #{limit} OFFSET #{offset}");
        assertThat(sql).contains("ORDER BY p.start_at DESC");
        assertThat(statement("countActiveRestrictedUsers").getTextContent())
                .contains("p.penalty_type = '이용 제한'")
                .contains("p.end_at > #{now}");
    }

    @Test
    @DisplayName("처리 완료 필터는 저장 상태가 만료이거나 종료 시각이 지난 적용 중 행을 포함한다")
    void doneFilterIncludesNaturallyExpiredRows() throws Exception {
        String sql = statement("findPenalties").getTextContent();

        // 자연 만료(적용 중 + end_at <= now)와 저장 상태 '만료'를 함께 포함한다.
        assertThat(sql).contains("p.status = '만료'");
        assertThat(sql).contains("p.status = '적용 중' AND p.end_at IS NOT NULL AND p.end_at <= #{now}");
    }

    @Test
    @DisplayName("조기 해제는 적용 중 행만 갱신하고 해제자·해제 시각을 함께 남긴다")
    void releaseIsConditional() throws Exception {
        String sql = statement("releasePenalty").getTextContent();

        assertThat(sql).contains("status = '해제'");
        assertThat(sql).contains("released_by = #{releasedBy}");
        assertThat(sql).contains("released_at = #{releasedAt}");
        assertThat(sql).contains("AND status = '적용 중'");
    }

    @Test
    @DisplayName("해제 대상 행은 PK로 잠그고, 남은 이용 제한은 현재 읽기로 잠가 다시 센다")
    void locksPenaltyAndRecomputesRemainingEnd() throws Exception {
        assertThat(statement("findPenaltyForUpdate").getTextContent()).contains("FOR UPDATE");
        String remaining = statement("findLatestActiveRestrictionEndAtForUpdate").getTextContent();
        // REPEATABLE READ 스냅샷을 피하려면 잠금 읽기여야 한다.
        assertThat(remaining).contains("FOR UPDATE");
        assertThat(remaining).contains("p.end_at");
        assertThat(remaining).contains("p.target_user_id = #{targetUserId}");
        assertThat(remaining).contains("p.status = '적용 중'");
    }

    @Test
    @DisplayName("제재 저장은 이용 제한 기간을 포함하고 해제 컬럼은 비운 채로 넣는다")
    void insertCarriesPeriodAndClearsRelease() throws Exception {
        String sql = statement("insertPenalty").getTextContent();

        assertThat(sql).contains("penalty_type");
        assertThat(sql).contains("end_at");
        assertThat(sql).contains("NULL, NULL");
        assertThat(sql).doesNotContain("CURRENT_TIMESTAMP");
    }

    private static Set<String> statementIds() throws Exception {
        Set<String> ids = new LinkedHashSet<>();
        NodeList all = readDocument().getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Node node = all.item(i);
            if (Set.of("select", "insert", "update", "delete").contains(node.getNodeName())) {
                ids.add(node.getAttributes().getNamedItem("id").getNodeValue());
            }
        }
        return ids;
    }

    private static Element statement(String id) throws Exception {
        Document document = readDocument();
        for (String tag : Set.of("select", "insert", "update", "delete")) {
            NodeList statements = document.getElementsByTagName(tag);
            for (int i = 0; i < statements.getLength(); i++) {
                Element statement = (Element) statements.item(i);
                if (id.equals(statement.getAttribute("id"))) {
                    return statement;
                }
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
        InputStream stream = PenaltyMapperXmlBindingTest.class.getClassLoader()
                .getResourceAsStream(MAPPER_RESOURCE);
        assertThat(stream).as("매퍼 XML 리소스 %s", MAPPER_RESOURCE).isNotNull();
        return stream;
    }
}
