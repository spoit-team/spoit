package co.kr.spoit.notification;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** 배포 컨테이너를 교체하기 전에 알림 마이그레이션 적용 여부를 확인한다. */
@ImportAutoConfiguration(DataSourceAutoConfiguration.class)
public final class NotificationSchemaCheck {

    private NotificationSchemaCheck() {
    }

    public static void main(String[] args) throws SQLException {
        // 데이터소스 설정만 읽는다. 웹 서버와 업무 스케줄러는 기동하지 않는다.
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(NotificationSchemaCheck.class)
                     .web(WebApplicationType.NONE)
                     .run(args);
                Connection connection = context.getBean(DataSource.class).getConnection();
                ResultSet keys = connection.getMetaData().getImportedKeys(
                        connection.getCatalog(), connection.getSchema(), "notification")) {
            while (keys.next()) {
                if ("club_id".equals(keys.getString("FKCOLUMN_NAME"))
                        && "club".equals(keys.getString("PKTABLE_NAME"))
                        && "club_id".equals(keys.getString("PKCOLUMN_NAME"))
                        && Objects.equals(keys.getString("FKTABLE_CAT"), keys.getString("PKTABLE_CAT"))
                        && Objects.equals(keys.getString("FKTABLE_SCHEM"), keys.getString("PKTABLE_SCHEM"))) {
                    return;
                }
            }
            throw new IllegalStateException(
                    "배포 전에 db/migrations/20261105-notification-club.sql을 적용하세요.");
        }
    }
}
