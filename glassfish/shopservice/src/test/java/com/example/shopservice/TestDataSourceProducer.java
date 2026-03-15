package com.example.shopservice;

import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Registers the test JDBC datasource via {@link DataSourceDefinition}.
 *
 * <p>Using {@code @DataSourceDefinition} rather than {@code WEB-INF/glassfish-resources.xml}
 * is intentional: in GlassFish Embedded (Arquillian), the {@code jdbc-resource} element from
 * {@code glassfish-resources.xml} inside a WAR is not bound to JNDI before
 * {@code ResourceValidator.validateJNDIRefs} runs, causing deployment to fail with
 * {@code NameNotFoundException: shopservice not found}.  Annotation-based registration
 * happens earlier in the deployment pipeline and is visible to the validator.
 *
 * <p>The datasource uses {@code org.openjproxy.jdbc.OjpDataSource} — the {@link javax.sql.DataSource}
 * implementation shipped by the OJP JDBC driver — routing through a local OJP proxy server to an
 * H2 in-memory backend.  The OJP URL format is
 * {@code jdbc:ojp[<ojp-host>:<ojp-port>]_<backend-jdbc-url>}.  This matches the approach
 * used in the Quarkus and Spring Boot test configurations in this repository.
 *
 * <p>The datasource is registered in the {@code java:app/} namespace so that
 * {@code persistence-test.xml} can reference it as {@code java:app/jdbc/shopservice}.
 *
 * <p>This class is only included in the ShrinkWrap archive used by tests (see
 * {@link DeploymentFactory}) and is never bundled into the production WAR.
 */
@DataSourceDefinition(
        name      = "java:app/jdbc/shopservice",
        className = "org.openjproxy.jdbc.OjpDataSource",
        url       = "jdbc:ojp[localhost:1059]_h2:mem:shopdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        user      = "sa",
        password  = "")
@ApplicationScoped
public class TestDataSourceProducer {
}
