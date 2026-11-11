package co.kr.spoit.payment.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.transaction.BeforeTransaction;

import co.kr.spoit.payment.domain.Payment;
import co.kr.spoit.payment.domain.RefundableApplicationRow;
import co.kr.spoit.match.domain.MatchApplication;
import co.kr.spoit.match.mapper.MatchApplicationMapper;

/**
 * 전용 MySQL DB에서 결제·환불 SQL을 검증한다. 환경변수가 없으면 실행되지 않으며 변경은 테스트 트랜잭션 롤백으로 되돌린다.
 *
 * <p>매칭방 9401: 대기 개인 신청 9501, 확정 개인 신청 9502, 취소 신청 9503 (room-management-fixture.sql).</p>
 */
@EnabledIfEnvironmentVariable(named = "MAT_TEST_DB_URL", matches = ".+")
@MybatisTest(properties = {
        "spring.config.import=",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "/match/room-management-fixture.sql", config = @SqlConfig(encoding = "UTF-8"))
class PaymentMapperIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 8, 12, 0);
    private static final List<String> ACTIVE = List.of("대기", "확정");

    @Autowired
    private PaymentMapper paymentMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MatchApplicationMapper applicationMapper;

    @DynamicPropertySource
    static void configureTestDatabase(DynamicPropertyRegistry registry) {
        String url = validateUrl(required("MAT_TEST_DB_URL"));
        registry.add("spring.datasource.url", () -> url
                + "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false");
        registry.add("spring.datasource.username", () -> required("MAT_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> required("MAT_TEST_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @BeforeTransaction
    void verifyDatabaseBeforeFixture() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        if (!"spoit_mat_test".equals(database)) {
            throw new IllegalStateException("전용 테스트 DB가 아니므로 fixture 실행을 중단합니다.");
        }
    }

    @Test
    @DisplayName("팀 결제 5명 중 2명 부분 환불 → 3명 전액 환불: 상태와 누적 금액이 바뀌고 같은 행은 다시 환불되지 않는다")
    void partialThenFullRefund() {
        insertPayment("pay-team", 9000, 5);
        assertThat(paymentMapper.saveTid("pay-team", "T-1", NOW)).isEqualTo(1);
        assertThat(paymentMapper.findById("pay-team").getTid()).isEqualTo("T-1");
        assertThat(paymentMapper.markPaid("pay-team", NOW, NOW)).isEqualTo(1);
        assertThat(paymentMapper.markPaid("pay-team", NOW, NOW)).as("두 번째 승인 기록은 0행").isZero();
        assertThat(paymentMapper.saveTid("pay-team", "T-2", NOW)).as("승인 뒤 tid는 바꾸지 않는다").isZero();
        assertThat(paymentMapper.linkApplications("pay-team", List.of(9501L, 9502L))).isEqualTo(2);
        assertThat(paymentMapper.findReceiptNo("pay-team")).isEqualTo("ROOM-TEST-9501");

        List<RefundableApplicationRow> refundable = paymentMapper.findRefundableApplications(List.of(9501L, 9502L, 9503L));
        assertThat(refundable).extracting(RefundableApplicationRow::getApplicationId).containsExactly(9501L, 9502L);
        assertThat(refundable.get(0).getUnitAmount()).isEqualByComparingTo("9000");
        assertThat(paymentMapper.findRefundableApplicationsOfMatch(9401L, ACTIVE))
                .extracting(RefundableApplicationRow::getApplicationId).containsExactly(9501L, 9502L);

        assertThat(paymentMapper.lockById("pay-team").getStatus()).isEqualTo("PAID");
        assertThat(paymentMapper.addRefunded("pay-team", new BigDecimal("18000"), NOW)).isEqualTo(1);
        Payment partial = paymentMapper.findById("pay-team");
        assertThat(partial.getStatus()).isEqualTo("PARTIAL_REFUNDED");
        assertThat(partial.getRefundedAmount()).isEqualByComparingTo("18000");

        assertThat(paymentMapper.markApplicationsRefunded(List.of(9501L, 9502L), NOW)).isEqualTo(2);
        assertThat(paymentMapper.markApplicationsRefunded(List.of(9501L), NOW)).isZero();
        assertThat(paymentMapper.findRefundableApplications(List.of(9501L, 9502L))).isEmpty();

        assertThat(paymentMapper.addRefunded("pay-team", new BigDecimal("27000"), NOW)).isEqualTo(1);
        assertThat(paymentMapper.findById("pay-team").getStatus()).isEqualTo("REFUNDED");
        assertThat(paymentMapper.addRefunded("pay-team", new BigDecimal("9000"), NOW)).as("결제 금액 초과").isZero();
    }

    @Test
    @DisplayName("승인하지 않은 주문만 실패·승인 직후 취소로 닫을 수 있다")
    void closesOnlyReadyOrders() {
        insertPayment("pay-ready", 9000, 2);
        insertPayment("pay-cancel", 9000, 1);
        insertPayment("pay-paid", 9000, 1);
        paymentMapper.markPaid("pay-paid", NOW, NOW);

        assertThat(paymentMapper.markFailed("pay-cancel", "결제를 취소했습니다.", NOW)).isEqualTo(1);
        assertThat(paymentMapper.findById("pay-cancel").getFailReason()).isEqualTo("결제를 취소했습니다.");
        assertThat(paymentMapper.markFailed("pay-paid", "x", NOW)).isZero();

        assertThat(paymentMapper.markRefundedUnapplied("pay-ready", "신청을 처리하지 못했습니다.", NOW)).isEqualTo(1);
        Payment refunded = paymentMapper.findById("pay-ready");
        assertThat(refunded.getStatus()).isEqualTo("REFUNDED");
        assertThat(refunded.getRefundedAmount()).isEqualByComparingTo("18000");
        assertThat(paymentMapper.findReceiptNo("pay-ready")).isNull();
        assertThat(paymentMapper.markRefundedUnapplied("pay-paid", "x", NOW)).as("신청이 있는 결제").isZero();
    }

    @Test
    @DisplayName("금액 제약: 결제 금액은 1인 참가비 × 인원이어야 한다")
    void rejectsInconsistentAmount() {
        Payment payment = payment("pay-bad", 9000, 2);
        payment.setTotalAmount(new BigDecimal("10000"));

        assertThatThrownBy(() -> paymentMapper.insert(payment)).isInstanceOf(DataAccessException.class).hasMessageContaining("ck_payment_amount");
    }

    @Test
    @DisplayName("신청 조회 SQL이 결제 연결 컬럼(payment_id·refunded_at)과 함께 실제 DB에서 실행된다")
    void applicationQueriesReadPaymentColumns() {
        insertPayment("pay-1", 9000, 1);
        paymentMapper.markPaid("pay-1", NOW, NOW);
        paymentMapper.linkApplications("pay-1", List.of(9501L));
        paymentMapper.markApplicationsRefunded(List.of(9501L), NOW);

        MatchApplication byId = applicationMapper.findById(9501L);
        assertThat(byId.getPaymentId()).isEqualTo("pay-1");
        assertThat(byId.getRefundedAt()).isEqualTo(NOW);
        assertThat(applicationMapper.findByReceiptNo("ROOM-TEST-9501").getPaymentId()).isEqualTo("pay-1");
        assertThat(applicationMapper.findMatchSchedule(9401L).getFacilityId()).isEqualTo(9301L);
    }

    private void insertPayment(String id, long unit, int seats) {
        assertThat(paymentMapper.insert(payment(id, unit, seats))).isEqualTo(1);
    }

    private static Payment payment(String id, long unit, int seats) {
        Payment p = new Payment();
        p.setPaymentId(id);
        p.setMatchId(9401L);
        p.setPayerUserId(9204L);
        p.setApplicationType("개인");
        p.setUnitAmount(BigDecimal.valueOf(unit));
        p.setSeatCount(seats);
        p.setTotalAmount(BigDecimal.valueOf(unit * seats));
        p.setCreatedAt(NOW);
        return p;
    }

    /** 로컬 전용 테스트 DB만 허용한다 (RoomManagementMapperIntegrationTest와 같은 규칙). */
    private static String validateUrl(String url) {
        if (url == null || !url.matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]{1,5}/spoit_mat_test")) {
            throw new IllegalArgumentException(
                    "MAT_TEST_DB_URL은 localhost 또는 127.0.0.1의 명시적 포트와 spoit_mat_test DB를 지정해야 합니다.");
        }
        return url;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 환경변수가 필요합니다.");
        }
        return value;
    }
}
