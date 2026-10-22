# Spring Boot Atomikos - Distributed Transaction Testing

> [!WARNING]
> This application was built primarily for **integration testing** with OJP (Open JDBC Proxy).
> Running it outside the integration-testing context will require additional changes: a live
> OJP proxy, two PostgreSQL instances, Docker for Testcontainers, and proper XA transaction
> configuration.

A Spring Boot application demonstrating distributed (XA) transactions using Atomikos transaction manager across two separate PostgreSQL databases.

## Overview

This project focuses on validating distributed transaction behavior using:
- **Atomikos JTA Transaction Manager** - For coordinating distributed transactions
- **PostgreSQL Database 1** - Primary database for Account entities
- **PostgreSQL Database 2** - Secondary database for AuditLog entities
- **Testcontainers** - For integration testing with real database instances

## Features

- **Two-Phase Commit (2PC)** transactions across two PostgreSQL databases
- Comprehensive integration tests covering:
  - Successful commits across both databases
  - Rollback on failures  
  - Partial failure handling and atomicity guarantees
  - Transaction timeout scenarios
  - Connection management and isolation

## Architecture

### Entities
- **Account** (PostgreSQL) - Represents bank accounts with balance tracking
- **AuditLog** (PostgreSQL) - Records all transaction operations for audit purposes

### Transaction Scenarios
All integration tests validate that operations on both databases are atomic:
- If one database operation fails, changes in both databases are rolled back
- Successful operations are committed to both databases together
- Timeouts properly rollback all changes

## Tech Stack

- **Java 25** (development) / Java 25 (target - requires environment support)
- **Spring Boot 3.2.6**
- **Atomikos 6.0.0** (Spring Boot 3 compatible version with Jakarta EE 9+)
- **PostgreSQL** (two separate instances via Testcontainers)
- **JUnit 5**
- **Testcontainers**

## Prerequisites

- Java 25+ (Java 25 for production deployment)
- Maven 3.8+
- Docker (for Testcontainers)

## REST API

The application exposes RESTful endpoints for transaction operations:

### Endpoints

- **POST** `/api/transactions/accounts` - Create a new account with audit logging
- **POST** `/api/transactions/transfer` - Transfer funds between accounts  
- **POST** `/api/transactions/accounts/with-failure` - Create account with simulated failure (for testing rollback)
- **POST** `/api/transactions/accounts/with-timeout` - Create account with timeout scenario

All integration tests validate distributed transactions through these REST API endpoints.

## Running Tests

All tests are integration tests that use Testcontainers to spin up two separate PostgreSQL instances and test the REST API:

```bash
mvn clean verify
```

This will:
1. Start two PostgreSQL containers
2. Configure Atomikos transaction manager
3. Run all integration tests
4. Shut down containers

## Integration Tests

All integration tests validate distributed transactions via REST API endpoints:

### DistributedTransactionSuccessIT
Tests successful transaction commits across both databases via REST API:
- Account creation with audit logging
- Balance transfers with audit trails
- Multiple sequential transactions

### DistributedTransactionRollbackIT
Tests rollback scenarios via REST API:
- Exception-triggered rollbacks
- Insufficient balance checks
- Invalid account handling
- Partial failure atomicity

### DistributedTransactionTimeoutIT
Tests transaction timeout behavior via REST API:
- Long-running operations exceeding timeout limits
- Proper rollback on timeout

### DistributedTransactionConnectionIT
Tests connection management and isolation via REST API:
- Multiple concurrent transactions
- Transaction isolation levels
- Connection pooling behavior

## Project Structure

```
src/main/java/com/example/atomikos/
├── AtomikosApplication.java                # Main application class
├── config/
│   ├── PostgresDataSourceConfig.java       # PostgreSQL 1 XA DataSource configuration
│   ├── Postgres2DataSourceConfig.java      # PostgreSQL 2 XA DataSource configuration
│   └── TransactionManagerConfig.java       # JTA Transaction Manager configuration
├── entity/
│   ├── postgres/
│   │   └── Account.java                    # Account entity (DB1)
│   └── postgres2/
│       └── AuditLog.java                   # AuditLog entity (DB2)
├── repository/
│   ├── postgres/
│   │   └── AccountRepository.java          # Account repository (DB1)
│   └── postgres2/
│       └── AuditLogRepository.java         # AuditLog repository (DB2)
└── service/
    └── TransactionService.java             # Business logic with @Transactional

src/test/java/com/example/atomikos/integration/
├── DistributedTransactionSuccessIT.java
├── DistributedTransactionRollbackIT.java
├── DistributedTransactionTimeoutIT.java
└── DistributedTransactionConnectionIT.java
```

## Key Configuration

The application uses Atomikos to coordinate XA transactions across two separate PostgreSQL databases:

- **PostgreSQL Database 1**: Primary entity manager for Account entities
  - Configured with `max_prepared_transactions=10` to enable XA support
- **PostgreSQL Database 2**: Secondary entity manager for AuditLog entities
  - Configured with `max_prepared_transactions=10` to enable XA support
- **Atomikos Transaction Manager**: Coordinates 2PC across both databases

Each datasource is configured as an `AtomikosDataSourceBean` with XA support.

### PostgreSQL XA Configuration

PostgreSQL requires `max_prepared_transactions` to be set to a non-zero value to support XA transactions. In the integration tests, this is configured via:
```java
.withCommand("postgres -c max_prepared_transactions=10")
```

Without this configuration, you will encounter errors like:
```
org.postgresql.xa.PGXAException: Error preparing transaction
```

### Dual PostgreSQL Configuration

Both PostgreSQL databases are configured identically with XA support. Using two PostgreSQL instances provides:
- **Consistent XA behavior** - PostgreSQL has robust XA transaction support
- **Simplified configuration** - Both databases use the same driver and settings

## Notes

- This project is for testing distributed transactions only; it's not intended as a production application
- All tests use Testcontainers, so Docker must be running
- Transaction timeouts are configured at method level using `@Transactional(timeout = n)`
- The application demonstrates how Atomikos ensures ACID properties across multiple databases
- XA transactions require proper configuration of both database drivers and the Atomikos transaction manager
- **Currently configured for Java 25** for development and testing. The target Java version is 21, but can be adjusted based on the deployment environment.

## Known Issues

Tests should now run reliably with two PostgreSQL databases. PostgreSQL has mature XA transaction support.

If you encounter any XA-related issues:
1. Ensure Docker has sufficient resources (memory and CPU)
2. Check that `max_prepared_transactions` is properly set on both PostgreSQL containers
3. Verify that both containers are fully started before tests begin
