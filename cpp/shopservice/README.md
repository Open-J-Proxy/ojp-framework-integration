# C++ ODBC ShopService

A C++17 implementation of the ShopService reference application, modelled on `spring-boot/shopservice` and the Node.js port. It exposes the same REST API, domain model, catalog/checkout database split and integration test suite. All database access goes through the **standard ODBC 3.x C API** and the **OJP ODBC driver** against **Microsoft SQL Server**.

```text
C++ → unixODBC → OJP ODBC driver ─gRPC→ OJP server(s) ─JDBC (mssql-jdbc)→ SQL Server
```

---

## Features

- **User management**: CRUD on users (`/users`, `/users/{id}`).
- **Product management**: CRUD on products with `DECIMAL(19,2)` prices (`/products`, `/products/{id}`).
- **Product reviews**: users review products with a rating and comment (`/reviews`, `/reviews/{id}`). The list endpoint can be filtered with `?userId=` or `?productId=`.
- **Order management**: orders with cascaded order items (`/orders`, `/orders/{id}`).
- **Order item management**: items within an order (`/orders/{orderId}/items`, `/orders/{orderId}/items/{itemId}`).
- **Plain ODBC**: uses only `SQLDriverConnect`, `SQLPrepare`, `SQLBindParameter`, `SQLExecute`, `SQLFetch`, `SQLGetData`, `SQLEndTran` and related calls. There is no ORM and no vendor client library.
- **Two datasources**: `cpp_catalog_db` and `cpp_checkout_db`. Each operation opens a fresh database connection, which is closed when the operation or transaction ends. Every table name has the `cpp_` prefix so it cannot clash with the other framework modules.

---

## Tech stack

| Component | Library |
|---|---|
| Language | C++17 (CMake ≥ 3.16) |
| Database access | ODBC 3.x via unixODBC + OJP ODBC driver (`ojp-client-cpp-odbc`) |
| Proxy | OJP server(s) with `mssql-jdbc` |
| HTTP server/client | [cpp-httplib](https://github.com/yhirose/cpp-httplib) |
| JSON | [nlohmann/json](https://github.com/nlohmann/json) |
| Tests | GoogleTest |
| Database | SQL Server 2022 (`mcr.microsoft.com/mssql/server:2022-latest`) |

On Ubuntu 24.04, install the dependencies with:

```bash
sudo apt-get install -y unixodbc-dev libgtest-dev nlohmann-json3-dev libcpp-httplib-dev
```

Build the OJP ODBC driver from [Open-JDBC-Proxy/ojp](https://github.com/Open-JDBC-Proxy/ojp) and register it with unixODBC under the name `OJP`. The Microsoft ODBC driver is not needed. The OJP server needs Java 25 and the SQL Server JDBC driver in its `ojp-libs` directory. The complete build, driver registration, and three-server startup are in `.github/workflows/cpp-shopservice.yml`, following the Python workflow.

From an existing OJP source checkout, build and start a single local server:

```bash
mvn clean install -U -pl ojp-server -am -DskipTests -Dgpg.skip=true
bash ojp-server/download-drivers.sh
mvn -q dependency:copy -Dartifact=com.microsoft.sqlserver:mssql-jdbc:12.8.2.jre11 \
  -DoutputDirectory=ojp-libs -Dmdep.stripVersion=false
OJP_JAR=$(ls ojp-server/target/ojp-server-*-shaded.jar | head -1)
java -Duser.timezone=UTC -Dojp.libs.path=./ojp-libs \
  -Dojp.server.port=1059 -Dojp.prometheus.port=9159 -jar "$OJP_JAR"
```

The C++ client opens fresh connections without client-side pooling; OJP owns the backend pools. Transactions request autocommit-off **before** connecting because the OJP driver does not export `SQLSetConnectAttrW`. Inserts use a separate `SELECT CAST(@@IDENTITY AS BIGINT)` rather than `OUTPUT INSERTED`, which JDBC updates do not support. Both calls run inside a transaction to pin the same backend connection. The application schema has no identity-generating triggers; adding them would require revisiting `@@IDENTITY`.

---

## Database layout

| Database | Table | Notes |
|---|---|---|
| `cpp_catalog_db` | `cpp_users` | `id`, `username`, `email`, `created_at DATETIME2` |
| | `cpp_products` | `id`, `name`, `price DECIMAL(19,2)` |
| | `cpp_reviews` | FK to `cpp_users` and `cpp_products`, `rating`, `comment NVARCHAR(MAX)` |
| `cpp_checkout_db` | `cpp_orders` | `id`, `user_id`, `order_date DATETIME2 DEFAULT SYSDATETIME()` |
| | `cpp_order_items` | FK to `cpp_orders` (`ON DELETE CASCADE`), `product_id`, `quantity` |

At startup the service creates any missing databases and tables.

---

## API overview

| Domain | Entity | Endpoints | Methods |
|---|---|---|---|
| Catalog | Users | `/users` | GET (paged), POST |
| | | `/users/{id}` | GET, PUT, DELETE |
| Catalog | Products | `/products` | GET (paged), POST, PUT |
| | | `/products/{id}` | GET, PUT, DELETE |
| Catalog | Reviews | `/reviews` | GET (paged, optional `userId`/`productId`), POST |
| | | `/reviews/{id}` | GET, PUT, DELETE |
| Checkout | Orders | `/orders` | GET (paged), POST |
| | | `/orders/{id}` | GET, PUT, DELETE |
| Checkout | OrderItems | `/orders/{orderId}/items` | GET, POST |
| | | `/orders/{orderId}/items/{itemId}` | GET, PUT, DELETE |

Paged endpoints take `page` (zero-based) and `size` (default 20, maximum 2000). They return the same JSON structure as a Spring Data `Page` (`content`, `totalElements`, `totalPages`, `number`, `size`, ...).

---

## Configuration

Configuration is read from environment variables:

| Variable | Description | Default |
|---|---|---|
| `OJP_SERVER` | OJP endpoint(s), `host:port` or a comma-separated list | `localhost:1059` |
| `DB_HOST` | SQL Server host as reached by the OJP server | `localhost` |
| `DB_PORT` | SQL Server port as reached by the OJP server | `1433` |
| `DB_USER` | SQL Server login | `sa` |
| `DB_PASSWORD` | SQL Server password | *(empty)* |
| `ODBC_DRIVER` | OJP ODBC driver name, as registered in `odbcinst.ini` | `OJP` |
| `CATALOG_DB` | Catalog database name | `cpp_catalog_db` |
| `CHECKOUT_DB` | Checkout database name | `cpp_checkout_db` |
| `DB_ENCRYPT` | Backend JDBC `encrypt` property | `false` |
| `DB_TRUST_SERVER_CERTIFICATE` | Backend JDBC `trustServerCertificate` property | `true` |
| `BIND_HOST` | HTTP bind address (application only) | `0.0.0.0` |
| `PORT` | HTTP port (application only) | `8080` |

---

## Building and running

```bash
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build --parallel

DB_PASSWORD='<sa password>' ./build/shopservice
```

This requires the registered OJP driver and a running OJP server (see above). To start a local SQL Server:

```bash
docker run -d --name mssql -e ACCEPT_EULA=Y -e 'MSSQL_SA_PASSWORD=<sa password>' \
  -p 1433:1433 mcr.microsoft.com/mssql/server:2022-latest
```

---

## Running the integration tests

The integration tests need a reachable OJP server and SQL Server, configured with the environment variables above. The test binary creates the databases and tables through OJP, starts the HTTP API in-process on a random local port (the equivalent of MockMvc), and runs the suite:

```bash
DB_PASSWORD='<sa password>' ctest --test-dir build --output-on-failure
# or, for per-test output:
DB_PASSWORD='<sa password>' ./build/shopservice_it
```

Configuration unit tests can run without OJP or SQL Server:

```bash
ctest --test-dir build -R shopservice_config_test --output-on-failure
```

The `.github/workflows/cpp-shopservice.yml` workflow runs the same steps in CI. It starts SQL Server 2022 in Docker with a randomly generated password, builds the OJP server and ODBC driver from `Open-JDBC-Proxy/ojp` `main`, downloads `mssql-jdbc`, and starts three OJP servers on ports 1059, 1060 and 1061 with separate Prometheus ports. Tests use all three endpoints through `OJP_SERVER`. The workflow prints server logs and stops the servers and database even on failure.

### Integration test suites

These match the seven Spring Boot test classes, with additional generated-ID and transaction-rollback regression tests:

- `CatalogRepositoryIT`: repository tests for User, Product and Review.
- `CheckoutRepositoryIT`: repository tests for Order and OrderItem.
- `UserControllerIT`: create/get (including `createdAt` round-tripping), update and delete users.
- `ProductControllerIT`: create/get, update and delete products with decimal prices.
- `ReviewControllerIT`: create, list and delete reviews.
- `OrderItemControllerIT`: CRUD under `/orders/{orderId}/items`.
- `OrderAndOrderItemControllerIT`: create an order with nested items, then fetch and delete it.

The test `SetUp` methods delete reviews before users and products. The Spring suite instead depends on JUnit's class execution order, whereas GoogleTest does not guarantee suite order. Because of this, the suite also passes with `--gtest_shuffle`.
