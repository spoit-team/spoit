package co.kr.spoit.notification.mapper;

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
 * NotificationMapper XML이 MyBatis에서 실제로 파싱되고 인터페이스와 정확히 대응하는지 확인한다.
 *
 * <p>DB에 연결하지 않으므로 전용 DB 환경변수가 없어도 실행된다. 특히 목록 조회가
 * {@code club_join_request}를 조인해 {@code clubId}를 읽으면서도 {@code recipient_user_id}
 * 소유권 조건을 잃지 않는지, 조인이 다른 알림 종류의 행 수를 늘리지 않는지 검증한다.</p>
 */
class NotificationMapperXmlBindingTest {

    private static final String MAPPER_RESOURCE = "mapper/notification/NotificationMapper.xml";

    @Test
    @DisplayName("XML 문장이 인터페이스 메서드와 1:1로 대응하고 MyBatis가 파싱한다")
    void statementsMatchInterfaceMethods() throws Exception {
        Configuration configuration = new Configuration();
        configuration.addMapper(NotificationMapper.class);
        try (InputStream stream = resource()) {
            new XMLMapperBuilder(stream, configuration, MAPPER_RESOURCE, configuration.getSqlFragments())
                    .parse();
        }

        assertThat(configuration.hasMapper(NotificationMapper.class)).isTrue();
        Set<String> boundStatements = new LinkedHashSet<>();
        for (String name : statementIds()) {
            String statementId = NotificationMapper.class.getName() + "." + name;
            assertThat(configuration.hasStatement(statementId)).as(statementId).isTrue();
            boundStatements.add(name);
        }

        Set<String> methodNames = Arrays.stream(NotificationMapper.class.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .map(Method::getName)
                .collect(Collectors.toSet());
        assertThat(boundStatements).containsExactlyInAnyOrderElementsOf(methodNames);
    }

    @Test
    @DisplayName("목록 조회는 가입 신청의 clubId를 조인해 읽는다")
    void findByRecipientReadsClubIdFromJoinRequest() throws Exception {
        String sql = statement("findByRecipient").getTextContent();

        assertThat(sql).contains("LEFT JOIN club_join_request");
        assertThat(sql).contains("cj.request_id = n.join_request_id");
        assertThat(sql).contains("COALESCE(n.club_id, cj.club_id) AS clubId");
        // request_id는 PK이므로 조인으로 행이 늘지 않는다.
        assertThat(sql).contains("LEFT JOIN");
        assertThat(sql).doesNotContain("JOIN notification");
    }

    @Test
    @DisplayName("목록 조회는 조인 뒤에도 수신자 소유 조건과 최신순·건수 제한을 유지한다")
    void findByRecipientStaysOwnershipScoped() throws Exception {
        String sql = statement("findByRecipient").getTextContent();

        assertThat(sql).contains("WHERE n.recipient_user_id = #{recipientUserId}");
        assertThat(sql).contains("ORDER BY n.created_at DESC, n.notification_id DESC");
        assertThat(sql).contains("LIMIT #{limit}");
    }

    @Test
    @DisplayName("clubId를 저장하고 기존 가입 신청 알림의 조인 정보도 유지한다")
    void insertPersistsClubId() throws Exception {
        String sql = statement("insert").getTextContent();

        assertThat(sql).contains("club_id");
        assertThat(sql).contains("clubId");
        assertThat(sql).contains("join_request_id");
    }

    @Test
    @DisplayName("모든 변경 문장은 수신자 조건으로 소유권을 강제한다")
    void everyMutationRequiresRecipient() throws Exception {
        Document document = readDocument();
        for (String id : Set.of("markRead", "markAllRead", "delete")) {
            String sql = statement(document, id).getTextContent();
            assertThat(sql).as(id).contains("recipient_user_id = #{recipientUserId}");
        }
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

    private static Element statement(String id) throws Exception {
        return statement(readDocument(), id);
    }

    private static Element statement(Document document, String id) {
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
        InputStream stream = NotificationMapperXmlBindingTest.class.getClassLoader()
                .getResourceAsStream(MAPPER_RESOURCE);
        assertThat(stream).as("매퍼 XML 리소스 %s", MAPPER_RESOURCE).isNotNull();
        return stream;
    }
}
