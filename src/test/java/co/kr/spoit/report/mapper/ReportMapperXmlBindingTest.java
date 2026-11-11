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
 * 신고 매퍼 XML이 MyBatis에서 실제로 파싱되고 인터페이스와 정확히 대응하는지 확인한다.
 *
 * <p>DB에 연결하지 않으므로 전용 DB 환경변수가 없어도 실행된다. 특히 목록 조회가 신고 원문
 * ({@code summary})을 선택하지 않고, 담당 시설·신고자 소유 조건·offset 페이지·건수 제한을 유지하는지,
 * 중복 비교가 DB 시계와 BINARY 정확 비교를 쓰는지 고정한다.</p>
 */
class ReportMapperXmlBindingTest {

    private static final String MAPPER_RESOURCE = "mapper/report/ReportMapper.xml";

    @Test
    @DisplayName("XML 문장이 인터페이스 메서드와 1:1로 대응하고 MyBatis가 파싱한다")
    void statementsMatchInterfaceMethods() throws Exception {
        Configuration configuration = new Configuration();
        configuration.addMapper(ReportMapper.class);
        try (InputStream stream = resource()) {
            new XMLMapperBuilder(stream, configuration, MAPPER_RESOURCE, configuration.getSqlFragments())
                    .parse();
        }

        assertThat(configuration.hasMapper(ReportMapper.class)).isTrue();
        Set<String> boundStatements = new LinkedHashSet<>();
        for (String name : statementIds()) {
            String statementId = ReportMapper.class.getName() + "." + name;
            assertThat(configuration.hasStatement(statementId)).as(statementId).isTrue();
            boundStatements.add(name);
        }

        Set<String> methodNames = Arrays.stream(ReportMapper.class.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .map(Method::getName)
                .collect(Collectors.toSet());
        assertThat(boundStatements).containsExactlyInAnyOrderElementsOf(methodNames);
    }

    @Test
    @DisplayName("목록 조회는 신고 원문을 선택하지 않고 상세만 원문을 싣는다")
    void listQueriesDoNotSelectSummary() throws Exception {
        assertThat(statement("findReportsForFacilityManager").getTextContent()).doesNotContain("r.summary");
        assertThat(statement("findSystemReports").getTextContent()).doesNotContain("r.summary");
        assertThat(statement("findFacilityManagerReport").getTextContent()).contains("r.summary");
        assertThat(statement("findSystemReport").getTextContent()).contains("r.summary");
    }

    @Test
    @DisplayName("시설 관리자 조회는 신고자 소유·담당 활성 시설·offset 페이지를 유지한다")
    void facilityQueriesStayScoped() throws Exception {
        for (String id : Set.of("findReportsForFacilityManager", "findFacilityManagerReport")) {
            String sql = statement(id).getTextContent();
            assertThat(sql).as(id).contains("r.reporter_user_id = #{reporterUserId}");
            assertThat(sql).as(id).contains("fm.user_id = #{managerUserId}");
            assertThat(sql).as(id).contains("f.deleted_at IS NULL");
        }
        String list = statement("findReportsForFacilityManager").getTextContent();
        assertThat(list).contains("LIMIT #{limit} OFFSET #{offset}");
        assertThat(statement("findSystemReports").getTextContent())
                .contains("LIMIT #{limit} OFFSET #{offset}");
    }

    @Test
    @DisplayName("경기 선택지는 담당 활성 시설·종료 상태·offset 페이지로 제한한다")
    void reportableMatchesAreScoped() throws Exception {
        String sql = statement("findReportableMatches").getTextContent();

        assertThat(sql).contains("fm.user_id = #{managerUserId}");
        assertThat(sql).contains("f.deleted_at IS NULL");
        assertThat(sql).contains("m.status = #{matchStatus}");
        assertThat(sql).contains("LIMIT #{limit} OFFSET #{offset}");
        // 예정 시각을 게이트로 쓰지 않는다.
        assertThat(sql).doesNotContain("start_at <");
        assertThat(sql).doesNotContain("NOW()");
    }

    @Test
    @DisplayName("페이지 밖 링크 선택은 담당 활성 시설·종료 상태를 단건 쿼리에서도 강제한다")
    void linkedMatchLookupStaysScoped() throws Exception {
        String sql = statement("findReportableMatch").getTextContent();

        assertThat(sql).contains("fm.user_id = #{managerUserId}");
        assertThat(sql).contains("f.deleted_at IS NULL");
        assertThat(sql).contains("m.status = #{matchStatus}");
        assertThat(sql).contains("m.match_id = #{matchId}");
    }

    @Test
    @DisplayName("대상 선택지는 확정 참가자 전부를 임의 절단 없이 조회한다")
    void participantsAreConfirmedAndNotTruncated() throws Exception {
        String sql = statement("findParticipants").getTextContent();

        assertThat(sql).contains("p.participation_status = #{participationStatus}");
        assertThat(sql).doesNotContain("LIMIT");
        assertThat(statement("existsParticipant").getTextContent())
                .contains("p.participation_status = #{participationStatus}");
    }

    @Test
    @DisplayName("중복 후보는 DB 시계·BINARY 정확 비교로 찾고 후보 행만 PK로 잠근다")
    void duplicateLookupUsesDbClockAndBinaryCompare() throws Exception {
        String duplicate = statement("findRecentDuplicate").getTextContent();
        assertThat(duplicate).contains("BINARY r.summary = BINARY #{summary}");
        assertThat(duplicate).contains("r.category = #{category}");
        // 애플리케이션 시각 인자를 받지 않고 DB 현재 시각 기준으로 창을 계산한다.
        assertThat(duplicate).contains("TIMESTAMPADD(SECOND, 0 - #{windowSeconds}, CURRENT_TIMESTAMP)");
        assertThat(duplicate).doesNotContain("#{cutoff}");
        // 보조 인덱스가 없는 report 전체 범위 잠금을 만들지 않는다.
        assertThat(duplicate).doesNotContain("FOR UPDATE");

        String lock = statement("lockReportById").getTextContent();
        assertThat(lock).contains("WHERE report_id = #{reportId}");
        assertThat(lock).contains("FOR UPDATE");
    }

    @Test
    @DisplayName("경기·신고 잠금과 상태 조건부 갱신, DB 레벨 완결 보호가 유지된다")
    void lockingAndConditionalUpdatesArePresent() throws Exception {
        assertThat(statement("findMatchSnapshotForUpdate").getTextContent()).contains("FOR UPDATE");
        assertThat(statement("findReportForUpdate").getTextContent()).contains("FOR UPDATE");

        for (String id : Set.of("markReviewing", "applyFinalDecision")) {
            String sql = statement(id).getTextContent();
            assertThat(sql).as(id).contains("AND status = #{expectedStatus}");
        }

        // 서비스가 전이를 판정하더라도 DB에서도 완결 건을 보호한다.
        assertThat(statement("markReviewing").getTextContent())
                .as("markReviewing은 접수에서만 허용")
                .contains("AND status = '접수'")
                .doesNotContain("AND status IN");
        assertThat(statement("applyFinalDecision").getTextContent())
                .as("applyFinalDecision은 접수·검토 중에서만 허용")
                .contains("AND status IN ('접수', '검토 중')");

        assertThat(statement("applyFinalDecision").getTextContent())
                .contains("resolution = #{resolution}")
                .contains("reviewed_at = CURRENT_TIMESTAMP");
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
        InputStream stream = ReportMapperXmlBindingTest.class.getClassLoader()
                .getResourceAsStream(MAPPER_RESOURCE);
        assertThat(stream).as("매퍼 XML 리소스 %s", MAPPER_RESOURCE).isNotNull();
        return stream;
    }
}
