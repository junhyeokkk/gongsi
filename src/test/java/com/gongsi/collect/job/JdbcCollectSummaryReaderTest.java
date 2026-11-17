package com.gongsi.collect.job;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** 요약 쿼리가 실제 스키마에서 돈다. Docker 가 있는 CI 에서만 실행 */
@SpringBootTest
@Testcontainers
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
class JdbcCollectSummaryReaderTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    JdbcCollectSummaryReader reader;

    @Test
    void 적재가_없는_날은_모두_0() {
        assertThat(reader.read(LocalDate.of(2026, 11, 17)))
                .isEqualTo(new CollectSummary(0, 0, 0, 0, 0, 0, 0, 0));
    }
}
