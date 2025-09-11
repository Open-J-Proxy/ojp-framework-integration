# ShopService

A sample Spring Boot RESTful API for validating integration with OJP(Open J proxy).
The sample app is intended for managing a shop’s users, products, orders, order items, and product reviews.  
This project demonstrates a multi-entity, relational domain model with CRUD operations, integration testing, and a PostgreSQL (or H2 for tests) backend.

---

## Features

- **User Management** – Create, read, update, and delete users.
- **Product Management** – CRUD operations on products with price handling.
- **Order Management** – Place orders for users, containing multiple order items.
- **Order Item Management** – Manage order items associated with orders and products.
- **Product Review System** – Users can review products with ratings and comments.
- **RESTful API** – All features exposed via REST endpoints.
- **Integration Tests** – Each controller has comprehensive integration tests using MockMvc and H2 in-memory DB.

---

## Tech Stack

- **Java 21**
- **Spring Boot 3.2.x**
- **Spring Data JPA**
- **PostgreSQL** (main DB, can be swapped for others)
- **H2** (in-memory DB for tests)
- **Maven** (build tool)
- **JUnit 5, MockMvc** (testing)

---

## Prerequisites

- Java 21+
- Maven 3.8+
- PostgreSQL (if running with real DB, not needed for tests)
- (Optional) Docker, Postman, etc.

---

## Getting Started

### 1. Clone the repository

>   git clone https://github.com/Open-JDBC-Proxy/ojp-framework-integration.git

>   cd spring-boot/shopservice


### 2. Configure the database

Edit `src/main/resources/application.properties` if you want to change the PostgreSQL connection (default assumes user and password as `postgres`):

Or use H2 for a quick start (see commented section in properties).

### 3. Build and run the application

This application is not intended to be run but instead to run validation tests using the OJP (Open J Proxy). Still you should be able to run it as a service with below commands.

>  mvn clean install

>  mvn spring-boot:run


The API will be available at [http://localhost:8080/](http://localhost:8080/).

---

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

All endpoints accept and return JSON.

---

## Running the Tests

Integration tests are provided for all controllers.  
**To run all tests, including integration tests, use:**


>  mvn clean verify


- This will build the project and execute all unit and integration tests.
- Integration tests use H2 in-memory database with test-specific properties (`src/test/resources/application-test.properties`).
- If you want to run only unit tests, use `mvn test` (not recommended for this project, as all tests are integration tests).

