package com.example.narayana.config;

import dev.snowdrop.boot.narayana.core.jdbc.GenericXADataSourceWrapper;
import org.openjproxy.jdbc.xa.OjpXADataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

import javax.sql.DataSource;
import javax.sql.XADataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for the first PostgreSQL datasource.
 * Uses OJP XA DataSource directly WITHOUT connection pooling as required.
 * Narayana wraps the XADataSource for transaction management via GenericXADataSourceWrapper.
 */
@Configuration
@EnableJpaRepositories(
    basePackages = "com.example.narayana.repository.postgres",
    entityManagerFactoryRef = "postgresEntityManagerFactory"
)
public class PostgresDataSourceConfig {

    @Primary
    @Bean(name = "postgresDataSourceProperties")
    @ConfigurationProperties("spring.datasource.postgres")
    public DataSourceProperties postgresDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean(name = "postgresXADataSource")
    public XADataSource postgresXADataSource(@Qualifier("postgresDataSourceProperties") DataSourceProperties properties) {
        // Using OJP XA DataSource directly without pooling (as required)
        OjpXADataSource xaDataSource = new OjpXADataSource();
        xaDataSource.setUrl(properties.getUrl());
        xaDataSource.setUser(properties.getUsername());
        xaDataSource.setPassword(properties.getPassword());
        
        return xaDataSource;
    }

    @Primary
    @Bean(name = "postgresDataSource")
    public DataSource postgresDataSource(
            @Qualifier("postgresXADataSource") XADataSource xaDataSource,
            GenericXADataSourceWrapper xaDataSourceWrapper) throws Exception {
        // Wrap the XA DataSource with Narayana's wrapper for transaction management
        // This does NOT add connection pooling - it only adds XA transaction support
        // The wrapper is auto-configured by Narayana and injected by Spring
        return xaDataSourceWrapper.wrapDataSource(xaDataSource);
    }

    @Primary
    @Bean(name = "postgresEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean postgresEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("postgresDataSource") DataSource dataSource) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "create-drop");
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.put("hibernate.show_sql", "false");
        
        LocalContainerEntityManagerFactoryBean emf = builder
                .dataSource(dataSource)
                .packages("com.example.narayana.entity.postgres")
                .persistenceUnit("postgres")
                .properties(properties)
                .jta(true)
                .build();
        emf.setJtaDataSource(dataSource);
        return emf;
    }
}
