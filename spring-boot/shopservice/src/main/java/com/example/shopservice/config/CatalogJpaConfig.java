package com.example.shopservice.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Map;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "com.example.shopservice.repository.catalog",
        entityManagerFactoryRef = "catalogEntityManagerFactory",
        transactionManagerRef  = "catalogTransactionManager"
)
public class CatalogJpaConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.catalog")
    public DataSourceProperties catalogProps() {
        return new DataSourceProperties();
    }

    @Bean(name = "catalogDataSource")
    public DataSource catalogDataSource(@Qualifier("catalogProps") DataSourceProperties p) {
        var ds = new org.springframework.jdbc.datasource.DriverManagerDataSource();
        ds.setDriverClassName(p.getDriverClassName()); // org.openjproxy.jdbc.Driver
        ds.setUrl(p.getUrl());                         // jdbc:ojp[...]
        ds.setUsername(p.getUsername());
        ds.setPassword(p.getPassword());
        return ds;
    }

    @Bean(name = "catalogVendorProps")
    @ConfigurationProperties("spring.jpa.catalog.properties")
    public java.util.Map<String, Object> catalogVendorProps() {
        return new java.util.HashMap<>();
    }

    @Bean(name = "catalogEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean catalogEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("catalogDataSource") DataSource ds,
            @Qualifier("catalogVendorProps") Map<String, Object> vendorProps) {

        // For tests: ensure schema is created automatically
        if (!vendorProps.containsKey("hibernate.hbm2ddl.auto")) {
            vendorProps.put("hibernate.hbm2ddl.auto", "create-drop");
        }
        
        return builder
                .dataSource(ds)
                .packages("com.example.shopservice.entity.catalog")
                .persistenceUnit("catalog")
                .properties(vendorProps)
                .build();
    }

    @Bean
    public PlatformTransactionManager catalogTransactionManager(@Qualifier("catalogEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
