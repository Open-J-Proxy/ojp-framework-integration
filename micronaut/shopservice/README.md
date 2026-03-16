# ShopService - Micronaut Implementation

> [!WARNING]
> This application was built primarily for **integration testing** with OJP (Open JDBC Proxy).
> Running it outside the integration-testing context (as a standalone service) will require
> additional changes: a live OJP proxy, a PostgreSQL instance, and adjustments to the
> datasource configuration in `application.properties`.

A Micronaut implementation of the ShopService application, providing the same REST API as the Spring Boot version.

## Features

- **User Management** – Create, read, update, and delete users.
- **Product Management** – CRUD operations on products with price handling.
- **Order Management** – Place orders for users, containing multiple order items.
- **Order Item Management** – Manage order items associated with orders and products.
- **Product Review System** – Users can review products with ratings and comments.
- **RESTful API** – All features exposed via REST endpoints.
- **Integration Tests** – Each controller has comprehensive integration tests using Micronaut Test and H2 in-memory DB.

## Tech Stack

- **Java 17**
- **Micronaut 4.2.x**
- **Micronaut Data JPA**
- **H2** (in-memory DB)
- **Maven** (build tool)
- **JUnit 5, Micronaut Test** (testing)

## Prerequisites

- Java 17+
- Maven 3.8+

## Getting Started

### 1. Build and run tests

```sh
mvn clean test
```

### 2. Run the application

```sh
mvn compile exec:java -Dexec.mainClass=com.example.shopservice.ShopServiceApplication
```

The API will be available at [http://localhost:8080/](http://localhost:8080/).

## API Overview

| Entity     | Endpoints (base path)     | Methods             |
|------------|--------------------------|---------------------|
| Users      | `/users`                 | GET, POST           |
|            | `/users/{id}`            | GET, PUT, DELETE    |
| Products   | `/products`              | GET, POST           |
|            | `/products/{id}`         | GET, PUT, DELETE    |
| Orders     | `/orders`                | GET, POST           |
|            | `/orders/{id}`           | GET, PUT, DELETE    |
| OrderItems | `/orders/{orderId}/items`| GET, POST           |
|            | `/orders/{orderId}/items/{itemId}` | GET, PUT, DELETE |
| Reviews    | `/reviews`               | GET, POST           |
|            | `/reviews/{id}`          | GET, PUT, DELETE    |

All endpoints accept and return JSON, matching the Spring Boot implementation exactly.

## Differences from Spring Boot Version

- Uses Micronaut annotations (`@Controller`, `@Get`, etc.) instead of Spring annotations
- Uses Micronaut Data JPA instead of Spring Data JPA  
- Uses Micronaut Test instead of Spring Boot Test
- Configuration in `application.properties` instead of Spring Boot format
- Dependency injection using Jakarta `@Inject` instead of Spring `@Autowired`

## Running the Tests

```sh
mvn clean test
```
Integration tests use H2 in-memory database with test-specific properties (`src/test/resources/application.properties`).