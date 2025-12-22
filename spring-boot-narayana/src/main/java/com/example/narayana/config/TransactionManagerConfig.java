package com.example.narayana.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Configuration for Narayana transaction manager.
 * 
 * Narayana is auto-configured by Spring Boot through the spring-boot-starter-jta-narayana dependency.
 * This class exists to enable transaction management and provide a place for any custom configuration if needed.
 * 
 * Note: Connection pooling is disabled as required - Narayana uses direct XA datasources without pooling.
 */
@Configuration
@EnableTransactionManagement
public class TransactionManagerConfig {
    // Narayana transaction manager is auto-configured by Spring Boot
    // Configuration properties can be set in application properties with prefix: spring.jta.narayana
}
