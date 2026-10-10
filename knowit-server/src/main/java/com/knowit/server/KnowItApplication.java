package com.knowit.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * KnowIt 启动类。
 *
 * <p>P0 骨架阶段此启动类刻意不依赖任何中间件： 没有 DataSource、没有 Flyway、没有 Spring AI 客户端。 因此 {@code mvn
 * spring-boot:run} 零外部依赖即可返回 200， 这也是 P0 验收标准第 1 条能快速验证的前提。
 *
 * <p>T0.2 起再追加 jdbc / flyway / pgvector，T0.3 追加 Spring AI OpenAI 兼容客户端。
 */
@SpringBootApplication
public class KnowItApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowItApplication.class, args);
    }
}
