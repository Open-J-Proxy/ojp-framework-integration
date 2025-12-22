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
import javax.sql.XADataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for the first PostgreSQL datasource.
 * Uses OJP XA DataSource directly WITHOUT connection pooling as required.
 * The XADataSource is used directly - Narayana will handle XA transaction coordination.
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
    @Bean(name = "postgresEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean postgresEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("postgresXADataSource") XADataSource xaDataSource) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "create-drop");
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.put("hibernate.show_sql", "false");
        
        // Use XADataSource directly as DataSource - Spring's JTA support will handle it
        LocalContainerEntityManagerFactoryBean emf = builder
                .dataSource((DataSource) xaDataSource)
                .packages("com.example.narayana.entity.postgres")
                .persistenceUnit("postgres")
                .properties(properties)
                .jta(true)
                .build();
        emf.setJtaDataSource((DataSource) xaDataSource);
        return emf;
    }
}
