# C++ ODBC ShopService

A C++17 implementation of the ShopService reference application, modelled on `spring-boot/shopservice` and the Node.js port. It exposes the same REST API, domain model, catalog/checkout database split and integration test suite. Database access goes through the **standard ODBC 3.x C API** (unixODBC driver manager plus the Microsoft ODBC Driver 18 for SQL Server) against **Microsoft SQL Server**.

> This first version connects **directly** to SQL Server. OJP is not used yet.

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
| Database access | ODBC 3.x via unixODBC + Microsoft ODBC Driver 18 for SQL Server |
| HTTP server/client | [cpp-httplib](https://github.com/yhirose/cpp-httplib) |
| JSON | [nlohmann/json](https://github.com/nlohmann/json) |
| Tests | GoogleTest |
| Database | SQL Server 2022 (`mcr.microsoft.com/mssql/server:2022-latest`) |

On Ubuntu 24.04, install the dependencies with:

```bash
sudo ACCEPT_EULA=Y apt-get install -y unixodbc-dev msodbcsql18 libgtest-dev nlohmann-json3-dev libcpp-httplib-dev
```

`msodbcsql18` comes from the [Microsoft package repository](https://learn.microsoft.com/sql/connect/odbc/linux-mac/installing-the-microsoft-odbc-driver-for-sql-server).

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
| `DB_HOST` | SQL Server host | `localhost` |
| `DB_PORT` | SQL Server port | `1433` |
| `DB_USER` | SQL Server login | `sa` |
| `DB_PASSWORD` | SQL Server password | *(empty)* |
| `ODBC_DRIVER` | ODBC driver name, as registered in `odbcinst.ini` | `ODBC Driver 18 for SQL Server` |
| `CATALOG_DB` | Catalog database name | `cpp_catalog_db` |
| `CHECKOUT_DB` | Checkout database name | `cpp_checkout_db` |
| `DB_ENCRYPT` | ODBC `Encrypt` attribute | `false` |
| `DB_TRUST_SERVER_CERTIFICATE` | ODBC `TrustServerCertificate` attribute | `true` |
| `BIND_HOST` | HTTP bind address (application only) | `0.0.0.0` |
| `PORT` | HTTP port (application only) | `8080` |

---

## Building and running

```bash
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build --parallel

DB_PASSWORD='<sa password>' ./build/shopservice
```

To start a local SQL Server:

```bash
docker run -d --name mssql -e ACCEPT_EULA=Y -e 'MSSQL_SA_PASSWORD=<sa password>' \
  -p 1433:1433 mcr.microsoft.com/mssql/server:2022-latest
```

---

## Running the integration tests

The tests need a reachable SQL Server, configured with the environment variables above. The test binary creates the databases and tables, starts the HTTP API in-process on a random local port (the equivalent of MockMvc), and runs the suite:

```bash
DB_PASSWORD='<sa password>' ctest --test-dir build --output-on-failure
# or, for per-test output:
DB_PASSWORD='<sa password>' ./build/shopservice_it
```

The `.github/workflows/cpp-shopservice.yml` workflow runs the same steps in CI. It starts SQL Server 2022 in Docker with a randomly generated password.

### Integration test suites

These match the seven Spring Boot test classes (30 tests in total):

- `CatalogRepositoryIT`: repository tests for User, Product and Review.
- `CheckoutRepositoryIT`: repository tests for Order and OrderItem.
- `UserControllerIT`: create/get (including `createdAt` round-tripping), update and delete users.
- `ProductControllerIT`: create/get, update and delete products with decimal prices.
- `ReviewControllerIT`: create, list and delete reviews.
- `OrderItemControllerIT`: CRUD under `/orders/{orderId}/items`.
- `OrderAndOrderItemControllerIT`: create an order with nested items, then fetch and delete it.

The test `SetUp` methods delete reviews before users and products. The Spring suite instead depends on JUnit's class execution order, whereas GoogleTest does not guarantee suite order. Because of this, the suite also passes with `--gtest_shuffle`.
