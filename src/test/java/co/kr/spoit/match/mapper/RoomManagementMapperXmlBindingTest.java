package co.kr.spoit.match.mapper;

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
 * RoomManagementMapper XML이 MyBatis에서 실제로 파싱되고 인터페이스와 정확히 대응하는지 확인한다.
 *
 * <p>DB에 연결하지 않으므로 전용 DB 환경변수가 없어도 실행된다. namespace·문장 ID 불일치나
 * 잘못된 resultType처럼 애플리케이션 기동을 막는 문제를 여기서 먼저 드러낸다.</p>
 */
class RoomManagementMapperXmlBindingTest {

    private static final String MAPPER_RESOURCE = "mapper/match/RoomManagementMapper.xml";

    @Test
    @DisplayName("XML 문장이 인터페이스 메서드와 1:1로 대응하고 MyBatis가 파싱한다")
    void statementsMatchInterfaceMethods() throws Exception {
        Configuration configuration = new Configuration();
        configuration.addMapper(RoomManagementMapper.class);
        try (InputStream stream = resource()) {
            new XMLMapperBuilder(stream, configuration, MAPPER_RESOURCE, configuration.getSqlFragments())
                    .parse();
        }

        assertThat(configuration.hasMapper(RoomManagementMapper.class)).isTrue();
        Set<String> boundStatements = new LinkedHashSet<>();
        for (String name : statementIds()) {
            String statementId = RoomManagementMapper.class.getName() + "." + name;
            assertThat(configuration.hasStatement(statementId)).as(statementId).isTrue();
            boundStatements.add(name);
        }

        Set<String> methodNames = Arrays.stream(RoomManagementMapper.class.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .map(Method::getName)
                .collect(Collectors.toSet());
        assertThat(boundStatements).containsExactlyInAnyOrderElementsOf(methodNames);
    }

    @Test
    @DisplayName("모든 조회 문장은 담당자 식별자와 삭제 시설 제외로 범위를 제한한다")
    void everyStatementIsTenantScoped() throws Exception {
        Document document = readDocument();
        String sharedFrom = fragment(document, "managedRoomFrom");
        assertThat(sharedFrom).contains("fm.user_id = #{managerUserId}");
        assertThat(sharedFrom).contains("f.deleted_at IS NULL");

        NodeList statements = document.getElementsByTagName("select");
        for (int i = 0; i < statements.getLength(); i++) {
            Element statement = (Element) statements.item(i);
            String id = statement.getAttribute("id");
            if (usesFragment(statement, "managedRoomFrom")) {
                continue;
            }
            String sql = statement.getTextContent();
            assertThat(sql).as(id).contains("fm.user_id = #{managerUserId}");
            assertThat(sql).as(id).contains("f.deleted_at IS NULL");
        }
    }

    @Test
    @DisplayName("담당 시설 종목 선택지는 매칭방이 아니라 facility_sport에서 나온다")
    void sportsComeFromFacilitySport() throws Exception {
        Element findSports = statement(readDocument(), "findSports");

        assertThat(findSports.getTextContent()).contains("facility_sport");
        assertThat(findSports.getTextContent()).doesNotContain("social_match");
    }

    private static Set<String> statementIds() throws Exception {
        Set<String> ids = new LinkedHashSet<>();
        NodeList all = readDocument().getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Node node = all.item(i);
            String tag = node.getNodeName();
            if (Set.of("select", "insert", "update", "delete").contains(tag)) {
                ids.add(node.getAttributes().getNamedItem("id").getNodeValue());
            }
        }
        return ids;
    }

    private static Element statement(Document document, String id) {
        NodeList statements = document.getElementsByTagName("select");
        for (int i = 0; i < statements.getLength(); i++) {
            Element statement = (Element) statements.item(i);
            if (id.equals(statement.getAttribute("id"))) {
                return statement;
            }
        }
        throw new AssertionError("매퍼 문장을 찾을 수 없습니다: " + id);
    }

    private static String fragment(Document document, String id) {
        NodeList fragments = document.getElementsByTagName("sql");
        for (int i = 0; i < fragments.getLength(); i++) {
            Element fragment = (Element) fragments.item(i);
            if (id.equals(fragment.getAttribute("id"))) {
                return fragment.getTextContent();
            }
        }
        throw new AssertionError("공통 SQL 조각을 찾을 수 없습니다: " + id);
    }

    private static boolean usesFragment(Element statement, String refid) {
        NodeList includes = statement.getElementsByTagName("include");
        for (int i = 0; i < includes.getLength(); i++) {
            Element include = (Element) includes.item(i);
            if (refid.equals(include.getAttribute("refid"))) {
                return true;
            }
        }
        return false;
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
        InputStream stream = RoomManagementMapperXmlBindingTest.class.getClassLoader()
                .getResourceAsStream(MAPPER_RESOURCE);
        assertThat(stream).as("매퍼 XML 리소스 %s", MAPPER_RESOURCE).isNotNull();
        return stream;
    }
}
