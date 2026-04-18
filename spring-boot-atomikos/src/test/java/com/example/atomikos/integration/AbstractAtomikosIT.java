package com.example.atomikos.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared base class for all Atomikos integration tests.
 *
 * <p>Containers are started once (static final) so that all subclasses share the same
 * PostgreSQL URLs. Spring's test context caching then reuses a single application context
 * across all test classes, which means only one XA pool is created on the OJP server side.
 * This prevents the OJP XA pool from being exhausted by orphaned connections left over from
 * context shutdowns when @DirtiesContext was used per class.
 */
public abstract class AbstractAtomikosIT {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("accountsdb")
            .withUsername("test")
            .withPassword("test")
            .withCommand("postgres -c max_prepared_transactions=10");

    static final PostgreSQLContainer<?> POSTGRES2 = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("auditdb")
            .withUsername("test")
            .withPassword("test")
            .withCommand("postgres -c max_prepared_transactions=10");

    static {
        POSTGRES.start();
        POSTGRES2.start();
    }

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        String urlPostgres = POSTGRES.getJdbcUrl().replaceAll("jdbc:postgresql",
                "jdbc:ojp[localhost:1059(postgres),localhost:1060(postgres),localhost:1061(postgres)]_postgresql");
        registry.add("spring.datasource.postgres.url", () -> urlPostgres);
        registry.add("spring.datasource.postgres.username", POSTGRES::getUsername);
        registry.add("spring.datasource.postgres.password", POSTGRES::getPassword);

        String urlPostgres2 = POSTGRES2.getJdbcUrl().replaceAll("jdbc:postgresql",
                "jdbc:ojp[localhost:1059(postgres2),localhost:1060(postgres2),localhost:1061(postgres2)]_postgresql");
        registry.add("spring.datasource.postgres2.url", () -> urlPostgres2);
        registry.add("spring.datasource.postgres2.username", POSTGRES2::getUsername);
        registry.add("spring.datasource.postgres2.password", POSTGRES2::getPassword);
    }
}
