# ShopService (Quarkus Edition)

A sample Java RESTful API for managing a shop’s users, products, orders, order items, and product reviews, built with [Quarkus](https://quarkus.io/).  
This project demonstrates a multi-entity, relational domain model with CRUD operations, integration testing, and an H2 in-memory database for tests integrated with OJP (Open J Proxy).

---

## Features

- **User Management:** Create, read, update, and delete users.
- **Product Management:** CRUD operations on products with price handling.
- **Order Management:** Place orders for users, containing multiple order items.
- **Order Item Management:** Manage order items within orders.
- **Product Review System:** Users can review products with ratings and comments.
- **RESTful API:** All features exposed via REST endpoints.
- **Integration Tests:** Comprehensive tests for all controllers using QuarkusTest, REST-assured, and H2.

---

## Tech Stack

- **Java 21**
- **Quarkus 3.x**
- **Hibernate ORM with Panache**
- **JAX-RS (RESTEasy Reactive)**
- **Jackson for JSON (add `quarkus-resteasy-reactive-jackson` to your dependencies!)**
- **H2 (in-memory DB for dev & tests)**
- **JUnit 5, REST-assured (for testing)**
- **Maven**

---

## Prerequisites

- Java 21+
- Maven 3.8+
- No need for a local database—H2 is used for development and testing.
- Docker only if you plan to run with a production database (not needed for defaults).

---

## Getting Started

### 1. Clone the repository

>   git clone https://github.com/Open-JDBC-Proxy/ojp-framework-integration.git

>   cd quarkus/shopservice


### 2. Build and run the application

>  mvn clean install
> 
>  mvn quarkus:dev


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

All endpoints accept and return JSON.

---

## Running the Tests

Integration tests are provided for all controllers.  
**To run all tests, including integration tests, use:**

>  mvn clean verify


- All tests use H2 in-memory database. No external dependencies are required.
- Tests use `@QuarkusTest`, JUnit 5, and REST-assured.

---

## Project Structure

```
src/
  main/
    java/com/example/shopservice/
      entity/         # JPA entities (User, Product, Order, OrderItem, Review)
      resource/       # REST resources/controllers
  test/
    java/com/example/shopservice/resource/
      # Integration tests for each controller
```

---

## Configuration

Main configurations are in `src/main/resources/application.properties`:


---

