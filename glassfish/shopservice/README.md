# ShopService – GlassFish / Jakarta EE 10

A REST-based shop service implemented with **GlassFish 7** and **Jakarta EE 10**, demonstrating OJP (Open JDBC Proxy) integration using the standard Jakarta EE stack.

---

## Tech Stack

| Component              | Technology                            |
|------------------------|---------------------------------------|
| **Language**           | Java 21                               |
| **Application Server** | GlassFish 7 (Jakarta EE 10)           |
| **REST**               | JAX-RS (Jersey 3, bundled in GlassFish)|
| **Persistence**        | JPA 3 / EclipseLink (bundled)         |
| **DI / Transactions**  | CDI 4 / JTA (bundled in GlassFish)    |
| **JSON**               | JSON-B / Jakarta JSON Binding (bundled)|
| **Database (prod)**    | PostgreSQL via OJP proxy              |
| **Database (test)**    | H2 in-memory via OJP proxy            |
| **Testing**            | Arquillian + GlassFish Embedded + REST-assured |
| **Build**              | Maven 3.8+, WAR packaging             |

---

## Features

- CRUD operations for **Users**, **Products**, **Orders**, **Order Items**, and **Reviews**
- Pure Jakarta EE 10 API – no framework-specific code
- CDI beans (`@ApplicationScoped`) for repositories with container-managed `EntityManager`
- JAX-RS resources (`@RequestScoped`) with container-managed transactions (`@Transactional`)
- JNDI-configured JDBC datasource via `WEB-INF/glassfish-resources.xml`
- OJP driver used as the JDBC driver for production; tests connect directly to H2 in-memory
- Arquillian integration tests that deploy the WAR to an embedded GlassFish 7 instance

---

## Project Structure

```
glassfish/shopservice/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/example/shopservice/
    │   │   ├── ShopServiceApplication.java      # @ApplicationPath("") JAX-RS entry point
    │   │   ├── entity/
    │   │   │   ├── User.java
    │   │   │   ├── Product.java
    │   │   │   ├── Order.java
    │   │   │   ├── OrderItem.java
    │   │   │   └── Review.java
    │   │   ├── repository/
    │   │   │   ├── UserRepository.java          # @ApplicationScoped CDI bean
    │   │   │   ├── ProductRepository.java
    │   │   │   ├── OrderRepository.java
    │   │   │   ├── OrderItemRepository.java
    │   │   │   └── ReviewRepository.java
    │   │   └── resource/
    │   │       ├── UserResource.java            # @Path("/users") JAX-RS resource
    │   │       ├── ProductResource.java
    │   │       ├── OrderResource.java
    │   │       ├── OrderItemResource.java
    │   │       └── ReviewResource.java
    │   ├── resources/
    │   │   └── META-INF/persistence.xml         # JTA persistence unit (jdbc/shopservice)
    │   └── webapp/
    │       └── WEB-INF/
    │           └── glassfish-resources.xml      # Production datasource (PostgreSQL via OJP)
    └── test/
        ├── java/com/example/shopservice/
        │   ├── DeploymentFactory.java           # Shared ShrinkWrap archive builder
        │   ├── TestDataSourceProducer.java      # @DataSourceDefinition (direct H2)
        │   └── resource/
        │       ├── ProductResourceTest.java
        │       ├── UserResourceTest.java
        │       ├── OrderResourceTest.java
        │       └── ReviewResourceTest.java
        └── resources/
            ├── arquillian.xml                   # Embedded GlassFish port config
            └── META-INF/
                └── persistence-test.xml         # Test persistence unit (drop-and-create)
```

---

## REST API

| Entity       | Method | Path                               | Description            |
|--------------|--------|------------------------------------|------------------------|
| Users        | GET    | `/users`                           | List all users         |
|              | POST   | `/users`                           | Create a user          |
|              | GET    | `/users/{id}`                      | Get user by ID         |
|              | PUT    | `/users/{id}`                      | Update user            |
|              | DELETE | `/users/{id}`                      | Delete user            |
| Products     | GET    | `/products`                        | List all products      |
|              | POST   | `/products`                        | Create a product       |
|              | GET    | `/products/{id}`                   | Get product by ID      |
|              | PUT    | `/products/{id}`                   | Update product         |
|              | DELETE | `/products/{id}`                   | Delete product         |
| Orders       | GET    | `/orders`                          | List all orders        |
|              | POST   | `/orders`                          | Create an order        |
|              | GET    | `/orders/{id}`                     | Get order by ID        |
|              | PUT    | `/orders/{id}`                     | Update order           |
|              | DELETE | `/orders/{id}`                     | Delete order           |
| Order Items  | GET    | `/orders/{orderId}/items`          | List items in order    |
|              | POST   | `/orders/{orderId}/items`          | Add item to order      |
|              | GET    | `/orders/{orderId}/items/{itemId}` | Get order item         |
|              | PUT    | `/orders/{orderId}/items/{itemId}` | Update order item      |
|              | DELETE | `/orders/{orderId}/items/{itemId}` | Remove order item      |
| Reviews      | GET    | `/reviews`                         | List all reviews       |
|              | POST   | `/reviews`                         | Create a review        |
|              | GET    | `/reviews/{id}`                    | Get review by ID       |
|              | PUT    | `/reviews/{id}`                    | Update review          |
|              | DELETE | `/reviews/{id}`                    | Delete review          |

---

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.8+
- GlassFish 7 (for manual deployment)
- OJP JDBC driver (`ojp-jdbc-driver-0.0.1-SNAPSHOT-INT-TEST-TMP-VERSION.jar`) installed in your local Maven repository

### Build the WAR

```bash
mvn clean package -DskipTests
```

This produces `target/shopservice-1.0.0.war`.

### Run integration tests (embedded GlassFish)

```bash
mvn clean verify
```

Arquillian starts an embedded GlassFish 7 instance on port **9090**, deploys the test WAR, runs the REST-assured integration tests, and stops the server.

---

## Production Deployment

### 1. Install the OJP JDBC Driver

Copy the OJP driver JAR to GlassFish's domain library directory so it is accessible to the connection pool manager:

```bash
cp ojp-jdbc-driver-*.jar $GLASSFISH_HOME/domains/domain1/lib/
```

### 2. Start GlassFish

```bash
$GLASSFISH_HOME/bin/asadmin start-domain
```

### 3. Deploy the WAR

```bash
$GLASSFISH_HOME/bin/asadmin deploy target/shopservice-1.0.0.war
```

GlassFish will process `WEB-INF/glassfish-resources.xml` during deployment and create the JDBC connection pool (`ShopServicePool`) and JNDI resource (`jdbc/shopservice`).

### 4. Access the API

The application is accessible at:

```
http://localhost:8080/shopservice/products
http://localhost:8080/shopservice/users
...
```

---

## Key Differences from Other Framework Implementations

| Aspect               | Spring Boot                    | Micronaut                    | Quarkus                       | **GlassFish / Jakarta EE**         |
|----------------------|--------------------------------|------------------------------|-------------------------------|-------------------------------------|
| DI                   | `@Autowired` / Spring DI       | `@Inject` / Micronaut DI     | `@Inject` / CDI               | `@Inject` / CDI (standard)          |
| REST                 | `@RestController`              | `@Controller` (Micronaut)    | `@Path` (JAX-RS / RESTEasy)   | `@Path` (JAX-RS / Jersey)           |
| Persistence          | Spring Data JPA                | Micronaut Data JPA           | Hibernate ORM + Panache        | JPA 3 + EclipseLink (standard)      |
| Transactions         | `@Transactional` (Spring)      | `@Transactional` (CDI)       | `@Transactional` (CDI)        | `@Transactional` (CDI / JTA)        |
| Datasource config    | `application.properties`       | `application.properties`     | `application.properties`      | `glassfish-resources.xml` (JNDI)    |
| Packaging            | Fat JAR (embedded Tomcat)      | Fat JAR (embedded Netty)     | Fast JAR / native             | WAR → deployed to GlassFish         |
| Test framework       | Spring Boot Test / MockMvc     | MicronautTest                | `@QuarkusTest` / REST-assured  | Arquillian + GlassFish Embedded     |
| Server lifecycle     | Embedded, starts automatically | Embedded, starts automatically | Embedded, starts automatically | External server required (or embedded for tests) |

### Notable GlassFish / Jakarta EE specifics

1. **WAR packaging**: Unlike embedded-server frameworks, GlassFish requires a WAR file deployed to the server. The application does not contain its own HTTP server.

2. **No `main()` method**: The application entry point is the `@ApplicationPath`-annotated `Application` subclass. There is no equivalent to `SpringApplication.run()`.

3. **JNDI datasource**: Datasource configuration is done at the server level via `glassfish-resources.xml` (or the GlassFish admin console / `asadmin` CLI), not in an `application.properties` file. The persistence unit references the datasource by JNDI name (`jdbc/shopservice`).

4. **OJP driver placement**: For production use, the OJP driver JAR must be placed in GlassFish's `domain/lib/` directory so it can be loaded by the server-level JDBC pool manager. For embedded testing, the driver is available on the JVM system classpath via Maven test-scope dependencies.

5. **EclipseLink as JPA provider**: GlassFish bundles EclipseLink (the Jakarta EE Reference Implementation for JPA), not Hibernate. Hibernate-specific features (e.g., `@Formula`, Panache) are not available; standard JPA APIs are used throughout.

6. **Client-side connection pooling disabled**: The GlassFish JDBC pool is configured with `max-connection-usage-count="1"` and `steady-pool-size="0"` so that each connection is discarded after a single use and no connections are held idle — exactly equivalent to `SimpleDriverDataSource` (Spring Boot), the bare `DriverManager` wrapper (Micronaut), and `unpooled=true` (Quarkus). OJP manages all connection pooling at the proxy (server) level; an active client-side pool would interfere with OJP's connection management.

7. **JSON-B for serialization**: GlassFish uses JSON-B (via EclipseLink MOXy) as the default JSON provider in JAX-RS. The `@JsonbTransient` annotation (from `jakarta.json.bind.annotation`) is used to break the circular reference between `Order` and `OrderItem`.
