package com.example.shopservice.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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
        basePackages = "com.example.shopservice.repository.checkout",
        entityManagerFactoryRef = "checkoutEntityManagerFactory",
        transactionManagerRef  = "checkoutTransactionManager"
)
public class CheckoutJpaConfig {

    @Bean @Primary
    @ConfigurationProperties(prefix = "spring.datasource.checkout")
    public DataSourceProperties checkoutProps() {
        return new DataSourceProperties();
    }

    // Sem pool local: use DriverManagerDataSource (ou SimpleDriverDataSource)
    @Bean(name = "checkoutDataSource") @Primary
    public DataSource checkoutDataSource(@Qualifier("checkoutProps") DataSourceProperties p) {
        var ds = new org.springframework.jdbc.datasource.DriverManagerDataSource();
        ds.setDriverClassName(p.getDriverClassName()); // org.openjproxy.jdbc.Driver
        ds.setUrl(p.getUrl());                         // jdbc:ojp[...]
        ds.setUsername(p.getUsername());
        ds.setPassword(p.getPassword());
        return ds;
    }

    @Bean(name = "checkoutVendorProps")
    @ConfigurationProperties("spring.jpa.checkout.properties")
    public java.util.Map<String, Object> checkoutVendorProps() {
        return new java.util.HashMap<>();
    }

    @Bean(name = "checkoutEntityManagerFactory") @Primary
    public LocalContainerEntityManagerFactoryBean checkoutEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("checkoutDataSource") DataSource ds,
            @Qualifier("checkoutVendorProps") Map<String, Object> vendorProps) {

        return builder
                .dataSource(ds)
                .packages("com.example.shopservice.entity.checkout")
                .persistenceUnit("checkout")
                .properties(vendorProps)
                .build();
    }

    @Bean @Primary
    public PlatformTransactionManager checkoutTransactionManager(@Qualifier("checkoutEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
