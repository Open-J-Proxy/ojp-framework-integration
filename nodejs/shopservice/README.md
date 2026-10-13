# Node.js ShopService

A Node.js implementation of the ShopService reference application modeled after `spring-boot/shopservice`. It provides the exact same REST API endpoints, domain model, multi-datasource separation (catalog and checkout domains), and integration test suite, built with **TypeScript**, **Express**, and **TypeORM**, connecting directly to **Microsoft SQL Server**.

---

## Features

- **User Management** – CRUD operations on users (`/users`, `/users/:id`).
- **Product Management** – CRUD operations on products with decimal price handling (`/products`, `/products/:id`).
- **Product Reviews** – Users review products with ratings and comments (`/reviews`, `/reviews/:id`), with user and product filtering (`/reviews/user/:userId`, `/reviews/product/:productId`).
- **Order Management** – Place orders for users, managing associated order items (`/orders`, `/orders/:id`).
- **Order Item Management** – Manage order items within orders (`/orders/:orderId/items`, `/orders/:orderId/items/:itemId`).
- **Direct SQL Server Access** – Direct database connections via TypeORM to SQL Server without OJP proxying.
- **Multi-DataSource Architecture** – Distinct catalog (`nodejs_catalog_db`) and checkout (`nodejs_checkout_db`) databases mirroring the Spring Boot shopservice domain segregation with prefixed table names (`nodejs_*`) preventing naming conflicts.
- **Identical Integration Test Suite** – Equivalent integration tests covering repositories and controllers matching all 7 Spring Boot test classes.

---

## Tech Stack

- **Node.js** (v18+)
- **TypeScript** (5.x)
- **Express** (4.x)
- **TypeORM** (0.3.x)
- **mssql** (driver for Microsoft SQL Server)
- **Jest** & **Supertest** (testing framework and HTTP assertions)
- **Testcontainers** (`@testcontainers/mssql` using `mcr.microsoft.com/mssql/server:2022-latest`)

---

## Multi-DataSource Architecture

Mirrors the Spring Boot domain split with prefixed table names to avoid any conflict:
- **Catalog Domain (`nodejs_catalog_db`)**:
  - `User` entity (`nodejs_users` table)
  - `Product` entity (`nodejs_products` table)
  - `Review` entity (`nodejs_reviews` table, relates to `User` and `Product`)
- **Checkout Domain (`nodejs_checkout_db`)**:
  - `Order` entity (`nodejs_orders` table, relates to `User` by ID)
  - `OrderItem` entity (`nodejs_order_items` table, relates to `Order` and references `Product` by ID)

---

## API Overview

| Domain | Entity | Endpoints | Methods |
|---|---|---|---|
| Catalog | Users | `/users` | GET (paged), POST |
| | | `/users/:id` | GET, PUT, DELETE |
| Catalog | Products | `/products` | GET (paged), POST |
| | | `/products/:id` | GET, PUT, DELETE |
| Catalog | Reviews | `/reviews` | GET (paged), POST |
| | | `/reviews/:id` | GET, PUT, DELETE |
| | | `/reviews/user/:userId` | GET |
| | | `/reviews/product/:productId` | GET |
| Checkout | Orders | `/orders` | GET (paged), POST |
| | | `/orders/:id` | GET, PUT, DELETE |
| Checkout | OrderItems | `/orders/:orderId/items` | GET, POST |
| | | `/orders/:orderId/items/:itemId` | GET, PUT, DELETE |

Pagination parameters: `page` (0-indexed) and `size` (default: 20). Returns a Spring Data-compatible page structure:
```json
{
  "content": [ ... ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0,
  "first": true,
  "last": true,
  "numberOfElements": 1,
  "empty": false
}
```

---

## Configuration

Environment variables can configure the SQL Server connection:

| Variable | Description | Default |
|---|---|---|
| `DB_HOST` | SQL Server hostname | `localhost` |
| `DB_PORT` | SQL Server port | `1433` |
| `DB_USER` | SQL Server user | `sa` |
| `DB_PASSWORD` | SQL Server password | `yourStrong(!)Password` |
| `CATALOG_DB` | Catalog database name | `nodejs_catalog_db` |
| `CHECKOUT_DB` | Checkout database name | `nodejs_checkout_db` |
| `PORT` | HTTP server port | `8080` |

---

## Running the Application

### 1. Install dependencies
```bash
npm install
```

### 2. Build TypeScript
```bash
npm run build
```

### 3. Start service
```bash
npm start
```

---

## Running Integration Tests

The integration test suite automatically starts an ephemeral Microsoft SQL Server 2022 container via Testcontainers, provisions the catalog and checkout databases, executes all 7 integration test suites, and tears down the container:

```bash
npm test
```

### Integration Test Suites
- `CatalogRepositoryIT`: Repository tests for User, Product, and Review entities.
- `CheckoutRepositoryIT`: Repository tests for Order and OrderItem entities with cascade operations.
- `UserControllerIT`: REST API tests for user creation, retrieval, update, deletion, and pagination.
- `ProductControllerIT`: REST API tests for product CRUD operations and decimal price handling.
- `ReviewControllerIT`: REST API tests for review creation, user/product filtering, and validation.
- `OrderItemControllerIT`: REST API tests for order item CRUD under `/orders/:orderId/items`.
- `OrderAndOrderItemControllerIT`: REST API tests for full order lifecycle with nested order items.
