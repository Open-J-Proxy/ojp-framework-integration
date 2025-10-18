package com.example.atomikos.config;

import com.atomikos.jdbc.AtomikosDataSourceBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Configuration
@EnableJpaRepositories(
    basePackages = "com.example.atomikos.repository.postgres2",
    entityManagerFactoryRef = "postgres2EntityManagerFactory"
)
public class Postgres2DataSourceConfig {

    @Bean(name = "postgres2DataSourceProperties")
    @ConfigurationProperties("spring.datasource.postgres2")
    public DataSourceProperties postgres2DataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "postgres2DataSource", destroyMethod = "close")
    public DataSource postgres2DataSource(@Qualifier("postgres2DataSourceProperties") DataSourceProperties properties) {
        AtomikosDataSourceBean dataSource = new AtomikosDataSourceBean();
        dataSource.setUniqueResourceName("postgres2DS");
        dataSource.setXaDataSourceClassName("org.postgresql.xa.PGXADataSource");
        
        Properties xaProperties = new Properties();
        xaProperties.setProperty("url", properties.getUrl());
        xaProperties.setProperty("user", properties.getUsername());
        xaProperties.setProperty("password", properties.getPassword());
        dataSource.setXaProperties(xaProperties);
        
        dataSource.setMinPoolSize(1);
        dataSource.setMaxPoolSize(5);
        dataSource.setTestQuery("SELECT 1");
        
        return dataSource;
    }

    @Bean(name = "postgres2EntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean postgres2EntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("postgres2DataSource") DataSource dataSource) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "create-drop");
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.put("hibernate.show_sql", "false");
        
        LocalContainerEntityManagerFactoryBean emf = builder
                .dataSource(dataSource)
                .packages("com.example.atomikos.entity.postgres2")
                .persistenceUnit("postgres2")
                .properties(properties)
                .jta(true)
                .build();
        emf.setJtaDataSource(dataSource);
        return emf;
    }
}
