package com.example.shopservice.datasource;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.openjproxy.jdbc.OjpDataSource;

import javax.sql.DataSource;

@Factory
public class DataSourceFactory {
    @Singleton
    @Named("default")
    public DataSource dataSource(
            @Value("${datasources.default.url}") String url,
            @Value("${datasources.default.username}") String user,
            @Value("${datasources.default.password}") String password
    ) {
        return new OjpDataSource(url, user, password);
    }
}
