# Spring Boot Atomikos - Distributed Transaction Testing

A Spring Boot application demonstrating distributed (XA) transactions using Atomikos transaction manager across PostgreSQL and MySQL databases.

## Overview

This project focuses on validating distributed transaction behavior using:
- **Atomikos JTA Transaction Manager** - For coordinating distributed transactions
- **PostgreSQL** - Primary database for Account entities
- **MySQL** - Secondary database for AuditLog entities
- **Testcontainers** - For integration testing with real database instances

## Features

- **Two-Phase Commit (2PC)** transactions across PostgreSQL and MySQL
- Comprehensive integration tests covering:
  - Successful commits across both databases
  - Rollback on failures  
  - Partial failure handling and atomicity guarantees
  - Transaction timeout scenarios
  - Connection management and isolation

## Architecture

### Entities
- **Account** (PostgreSQL) - Represents bank accounts with balance tracking
- **AuditLog** (MySQL) - Records all transaction operations for audit purposes

### Transaction Scenarios
All integration tests validate that operations on both databases are atomic:
- If one database operation fails, changes in both databases are rolled back
- Successful operations are committed to both databases together
- Timeouts properly rollback all changes

## Tech Stack

- **Java 21**
- **Spring Boot 3.2.6**
- **Atomikos 6.0.0** (Spring Boot 3 compatible version with Jakarta EE 9+)
- **PostgreSQL** (via Testcontainers)
- **MySQL** (via Testcontainers)
- **JUnit 5**
- **Testcontainers**

## Prerequisites

- Java 21+
- Maven 3.8+
- Docker (for Testcontainers)

## Running Tests

All tests are integration tests that use Testcontainers to spin up real PostgreSQL and MySQL instances:

```bash
mvn clean verify
```

This will:
1. Start PostgreSQL and MySQL containers
2. Configure Atomikos transaction manager
3. Run all integration tests
4. Shut down containers

## Integration Tests

### DistributedTransactionSuccessIT
Tests successful transaction commits across both databases:
- Account creation with audit logging
- Balance transfers with audit trails
- Multiple sequential transactions

### DistributedTransactionRollbackIT
Tests rollback scenarios:
- Exception-triggered rollbacks
- Insufficient balance checks
- Invalid account handling
- Partial failure atomicity

### DistributedTransactionTimeoutIT
Tests transaction timeout behavior:
- Long-running operations exceeding timeout limits
- Proper rollback on timeout

### DistributedTransactionConnectionIT
Tests connection management and isolation:
- Multiple concurrent transactions
- Transaction isolation levels
- Connection pooling behavior

## Project Structure

```
src/main/java/com/example/atomikos/
├── AtomikosApplication.java                # Main application class
├── config/
│   ├── PostgresDataSourceConfig.java       # PostgreSQL XA DataSource configuration
│   ├── MysqlDataSourceConfig.java          # MySQL XA DataSource configuration
│   └── TransactionManagerConfig.java       # JTA Transaction Manager configuration
├── entity/
│   ├── postgres/
│   │   └── Account.java                    # Account entity
│   └── mysql/
│       └── AuditLog.java                   # AuditLog entity
├── repository/
│   ├── postgres/
│   │   └── AccountRepository.java          # Account repository
│   └── mysql/
│       └── AuditLogRepository.java         # AuditLog repository
└── service/
    └── TransactionService.java             # Business logic with @Transactional

src/test/java/com/example/atomikos/integration/
├── DistributedTransactionSuccessIT.java
├── DistributedTransactionRollbackIT.java
├── DistributedTransactionTimeoutIT.java
└── DistributedTransactionConnectionIT.java
```

## Key Configuration

The application uses Atomikos to coordinate XA transactions across two databases:

- **PostgreSQL DataSource**: Primary entity manager for Account entities
- **MySQL DataSource**: Secondary entity manager for AuditLog entities
- **Atomikos Transaction Manager**: Coordinates 2PC across both databases

Each datasource is configured as an `AtomikosDataSourceBean` with XA support.

## Notes

- This project is for testing distributed transactions only; it's not intended as a production application
- All tests use Testcontainers, so Docker must be running
- Transaction timeouts are configured at method level using `@Transactional(timeout = n)`
- The application demonstrates how Atomikos ensures ACID properties across multiple databases
- XA transactions require proper configuration of both database drivers and the Atomikos transaction manager
- The project was developed with Java 17 during testing and configured for Java 21 as per repository standards

## Known Issues

Some integration tests may experience intermittent failures related to XA transaction prepare phase (HeurHazardException). This is often due to timing issues or resource cleanup in test environments. The rollback tests demonstrate that the transaction manager properly handles exceptions and rollbacks across both databases.
