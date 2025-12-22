package com.example.narayana.config;

import org.postgresql.xa.PGXADataSource;
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
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Configuration for the first PostgreSQL datasource.
 * Uses PostgreSQL XA DataSource directly WITHOUT connection pooling for testing.
 * Wraps XADataSource in a simple DataSource adapter for Spring compatibility.
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
        // Using PostgreSQL XA DataSource directly without pooling (for testing)
        PGXADataSource xaDataSource = new PGXADataSource();
        xaDataSource.setUrl(properties.getUrl());
        xaDataSource.setUser(properties.getUsername());
        xaDataSource.setPassword(properties.getPassword());
        
        return xaDataSource;
    }

    @Primary
    @Bean(name = "postgresDataSource")
    public DataSource postgresDataSource(@Qualifier("postgresXADataSource") XADataSource xaDataSource) {
        // Wrap XADataSource in a simple DataSource adapter
        // This allows Spring/Hibernate to work with it while maintaining no connection pooling
        return new XADataSourceAdapter(xaDataSource);
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

    /**
     * Simple DataSource adapter that wraps an XADataSource.
     * Does NOT provide connection pooling - connections are obtained directly from XADataSource.
     */
    private static class XADataSourceAdapter implements DataSource {
        private final XADataSource xaDataSource;

        public XADataSourceAdapter(XADataSource xaDataSource) {
            this.xaDataSource = xaDataSource;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return xaDataSource.getXAConnection().getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return xaDataSource.getXAConnection(username, password).getConnection();
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return xaDataSource.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            xaDataSource.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            xaDataSource.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return xaDataSource.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return xaDataSource.getParentLogger();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            if (iface.isInstance(xaDataSource)) {
                return iface.cast(xaDataSource);
            }
            throw new SQLException("DataSource of type [" + getClass().getName() +
                    "] cannot be unwrapped as [" + iface.getName() + "]");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return iface.isInstance(this) || iface.isInstance(xaDataSource);
        }
    }
}
