# Python SQLAlchemy ShopService

A Python implementation of the ShopService reference application, modelled on `spring-boot/shopservice`. It exposes the same REST API, domain model, catalog/checkout database split and integration test suite. Database access goes through **SQLAlchemy 2** with the `mssql+pyodbc` dialect, which wraps the **native Microsoft ODBC Driver 18 for SQL Server**.

> This first version connects **directly** to SQL Server. OJP is not used yet.

---

## Features

- **User management**: CRUD on users (`/users`, `/users/{id}`).
- **Product management**: CRUD on products with `DECIMAL(19,2)` prices (`/products`, `/products/{id}`).
- **Product reviews**: users review products with a rating and comment (`/reviews`, `/reviews/{id}`). The list endpoint can be filtered with `?userId=` or `?productId=`.
- **Order management**: orders with cascaded order items (`/orders`, `/orders/{id}`). Replacing `orderItems` on update removes the orphaned items, like JPA `orphanRemoval`.
- **Order item management**: items within an order (`/orders/{orderId}/items`, `/orders/{orderId}/items/{itemId}`).
- **Two datasources**: `py_catalog_db` and `py_checkout_db`, each with its own SQLAlchemy engine, connection pool and declarative base (the equivalent of `CatalogJpaConfig`/`CheckoutJpaConfig`). Every table name has the `py_` prefix so it cannot clash with the other framework modules.

---

## Tech stack

| Component | Library |
|---|---|
| Language | Python ≥ 3.10 |
| ORM | SQLAlchemy 2 (`mssql+pyodbc` dialect) |
| Database driver | pyodbc + unixODBC + Microsoft ODBC Driver 18 for SQL Server |
| HTTP | FastAPI, served by uvicorn |
| Tests | pytest + FastAPI `TestClient` |
| Database | SQL Server 2022 (`mcr.microsoft.com/mssql/server:2022-latest`) |

The ODBC driver has to be installed on the system. On Ubuntu:

```bash
sudo ACCEPT_EULA=Y apt-get install -y unixodbc msodbcsql18
```

`msodbcsql18` comes from the [Microsoft package repository](https://learn.microsoft.com/sql/connect/odbc/linux-mac/installing-the-microsoft-odbc-driver-for-sql-server).

---

## Database layout

| Database | Table | Notes |
|---|---|---|
| `py_catalog_db` | `py_users` | `id`, `username`, `email`, `created_at DATETIME2` |
| | `py_products` | `id`, `name`, `price DECIMAL(19,2)` |
| | `py_reviews` | FK to `py_users` and `py_products`, `rating`, `comment NVARCHAR(MAX)` |
| `py_checkout_db` | `py_orders` | `id`, `user_id`, `order_date DATETIME2 DEFAULT SYSDATETIME()` |
| | `py_order_items` | FK to `py_orders` (`ON DELETE CASCADE`), `product_id`, `quantity` |

At startup the service creates any missing databases and tables (`metadata.create_all`).

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

Paged endpoints take `page` (zero-based) and `size` (default 20, maximum 2000). They return the same JSON structure as a Spring Data `Page` (`content`, `totalElements`, `totalPages`, `number`, `size`, ...). Malformed input returns `400`, as in Spring.

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
| `CATALOG_DB` | Catalog database name | `py_catalog_db` |
| `CHECKOUT_DB` | Checkout database name | `py_checkout_db` |
| `DB_ENCRYPT` | ODBC `Encrypt` attribute | `false` |
| `DB_TRUST_SERVER_CERTIFICATE` | ODBC `TrustServerCertificate` attribute | `true` |
| `BIND_HOST` | HTTP bind address (application only) | `0.0.0.0` |
| `PORT` | HTTP port (application only) | `8080` |

---

## Installing and running

```bash
python -m venv .venv && . .venv/bin/activate
pip install -e ".[test]"

DB_PASSWORD='<sa password>' shopservice
```

To start a local SQL Server:

```bash
docker run -d --name mssql -e ACCEPT_EULA=Y -e 'MSSQL_SA_PASSWORD=<sa password>' \
  -p 1433:1433 mcr.microsoft.com/mssql/server:2022-latest
```

---

## Running the integration tests

The tests need a reachable SQL Server, configured with the environment variables above. The test session creates the databases and tables and runs the API in-process with FastAPI's `TestClient` (the equivalent of MockMvc):

```bash
DB_PASSWORD='<sa password>' python -m pytest -v
```

The `.github/workflows/python-shopservice.yml` workflow runs the same steps in CI. It starts SQL Server 2022 in Docker with a randomly generated password.

### Integration test suites

These match the seven Spring Boot test classes (30 tests in total):

- `test_catalog_repository_it.py` (`CatalogRepositoryIT`): repository tests for User, Product and Review.
- `test_checkout_repository_it.py` (`CheckoutRepositoryIT`): repository tests for Order and OrderItem.
- `test_user_controller_it.py` (`UserControllerIT`): create/get (including `createdAt` round-tripping), update and delete users.
- `test_product_controller_it.py` (`ProductControllerIT`): create/get, update and delete products with decimal prices.
- `test_review_controller_it.py` (`ReviewControllerIT`): create, list and delete reviews.
- `test_order_item_controller_it.py` (`OrderItemControllerIT`): CRUD under `/orders/{orderId}/items`.
- `test_order_and_order_item_controller_it.py` (`OrderAndOrderItemControllerIT`): create an order with nested items, then fetch and delete it.

Fixtures delete reviews before users and products, so the suite doesn't depend on test module order.
