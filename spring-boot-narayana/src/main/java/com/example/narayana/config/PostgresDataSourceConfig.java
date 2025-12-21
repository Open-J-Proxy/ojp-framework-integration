package com.example.narayana.config;

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
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for the first PostgreSQL datasource.
 * Uses OJP XA DataSource directly WITHOUT connection pooling as required.
 * Narayana manages the XA transactions without an intermediate connection pool.
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
    @Bean(name = "postgresDataSource")
    public DataSource postgresDataSource(@Qualifier("postgresDataSourceProperties") DataSourceProperties properties) {
        // Using OJP XA DataSource directly without pooling (as required)
        OjpXADataSource dataSource = new OjpXADataSource();
        dataSource.setUrl(properties.getUrl());
        dataSource.setUser(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        
        return dataSource;
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
