package com.knowit.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Web 层启动冒烟（P0 验收标准第 1 条的自动化版本）。
 *
 * <p><b>为什么不接数据库</b>：数据库是必需项（见 application.yml）， 但这个测试要能在一台没有 Docker、没有数据库的机器上秒级跑完，
 * 否则每次改一行代码都要先起容器，开发节奏会被拖垮。 所以这里显式排除数据源与 Flyway 自动配置，只验证 Web 层能起来、 {@code /ping} 返回 200。
 *
 * <p><b>真正的数据层验证在 {@code KnowItDataLayerIT}</b>（T0.5）： 用 Testcontainers 起真实的 pgvector，验证 Flyway
 * 迁移能跑通。 那条测试标注了 {@code disabledWithoutDocker}，没有 Docker 时自动跳过， 而 CI（GitHub ubuntu runner 自带
 * Docker）会真跑。
 *
 * <p>为什么 Spring Boot 在只有 starter-jdbc 而没有 url 时也会创建 DataSource： {@code
 * PooledDataSourceConfiguration} 的条件是"classpath 上有连接池"， 不要求配置了 url。所以必须显式 exclude，不能靠"没配就不加载"。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
        })
@DisplayName("应用冒烟（Web 层，不接数据库）")
class KnowItApplicationSmokeTest {

    @Autowired private TestRestTemplate restTemplate;

    @Test
    @DisplayName("上下文能加载，/ping 返回服务身份")
    void contextLoadsAndPingWorks() {
        ResponseEntity<String> response = restTemplate.getForEntity("/ping", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody())
                .contains("\"service\":\"knowit\"")
                .contains("\"status\":\"UP\"");
    }
}
