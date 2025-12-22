package com.example.narayana.config;

import com.arjuna.ats.internal.jta.recovery.arjunacore.XARecoveryModule;
import dev.snowdrop.boot.narayana.core.jdbc.GenericXADataSourceWrapper;
import org.postgresql.xa.PGXADataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

import javax.sql.DataSource;
import javax.sql.XADataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for the second PostgreSQL datasource.
 * 
 * Uses PostgreSQL XA DataSource with Narayana's GenericXADataSourceWrapper.
 * Connection pooling is DISABLED - direct XA datasource usage.
 * 
 * WHY THE WRAPPER IS NEEDED:
 * 1. JPA/Hibernate API requires a DataSource interface, not XADataSource
 * 2. GenericXADataSourceWrapper:
 *    - Implements DataSource interface (required by JPA)
 *    - Registers XADataSource with Narayana for XA recovery
 *    - Enlists XA resources in JTA transactions
 *    - Does NOT add connection pooling (connections come directly from XADataSource)
 * 3. Without this wrapper, XA resources would not be properly enlisted in transactions,
 *    causing rollback failures
 * 
 * This is similar to Atomikos' AtomikosDataSourceBean, but Narayana's wrapper is simpler
 * and doesn't force connection pooling (Atomikos requires pooling, Narayana doesn't).
 */
@Configuration
@EnableJpaRepositories(
    basePackages = "com.example.narayana.repository.postgres2",
    entityManagerFactoryRef = "postgres2EntityManagerFactory"
)
public class Postgres2DataSourceConfig {

    @Bean(name = "postgres2DataSourceProperties")
    @ConfigurationProperties("spring.datasource.postgres2")
    public DataSourceProperties postgres2DataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "postgres2XADataSource")
    public XADataSource postgres2XADataSource(@Qualifier("postgres2DataSourceProperties") DataSourceProperties properties) {
        // Using PostgreSQL XA DataSource directly without pooling (for testing)
        PGXADataSource xaDataSource = new PGXADataSource();
        xaDataSource.setUrl(properties.getUrl());
        xaDataSource.setUser(properties.getUsername());
        xaDataSource.setPassword(properties.getPassword());
        
        return xaDataSource;
    }

    @Bean(name = "postgres2DataSource")
    public DataSource postgres2DataSource(
            @Qualifier("postgres2XADataSource") XADataSource xaDataSource,
            XARecoveryModule xaRecoveryModule) throws Exception {
        // Use Narayana's GenericXADataSourceWrapper to properly wrap and enlist XA resources
        GenericXADataSourceWrapper wrapper = new GenericXADataSourceWrapper(xaRecoveryModule);
        return wrapper.wrapDataSource(xaDataSource);
    }

    @Bean(name = "postgres2EntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean postgres2EntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("postgres2DataSource") DataSource dataSource) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "create-drop");
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.put("hibernate.show_sql", "false");
        properties.put("hibernate.transaction.jta.platform", "org.hibernate.engine.transaction.jta.platform.internal.JBossStandAloneJtaPlatform");
        properties.put("jakarta.persistence.transactionType", "JTA");
        
        return builder
                .dataSource(dataSource)
                .packages("com.example.narayana.entity.postgres2")
                .persistenceUnit("postgres2")
                .properties(properties)
                .jta(true)
                .build();
    }
}
