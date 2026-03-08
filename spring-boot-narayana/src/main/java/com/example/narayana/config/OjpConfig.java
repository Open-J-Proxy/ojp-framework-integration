package com.example.narayana.config;

import org.openjproxy.autoconfigure.OjpSystemPropertiesBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Registers the OJP system-properties bridge for this multi-datasource application.
 *
 * The starter's OjpAutoConfiguration only activates when spring.datasource.url is present,
 * which is not the case here (we use spring.datasource.postgres.url / postgres2.url).
 * Registering the bridge manually ensures that ojp.* and named-pool {poolName}.ojp.*
 * properties declared in application.properties are forwarded to JVM system properties
 * before any DataSource bean is created, making them available to the OJP JDBC driver.
 */
@Configuration
public class OjpConfig {

    @Bean
    public OjpSystemPropertiesBridge ojpSystemPropertiesBridge(Environment environment) {
        return new OjpSystemPropertiesBridge(environment);
    }
}
