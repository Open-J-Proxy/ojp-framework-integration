#include "shopservice/schema.hpp"

#include <stdexcept>

namespace shopservice {

void ensureDatabasesExist(const odbc::Environment& env, const DatabaseConfig& config) {
    odbc::Connection master(env, config.connectionString("master"));
    for (const std::string& name : {config.catalogDatabase, config.checkoutDatabase}) {
        // CREATE DATABASE cannot take a bound parameter, so only validated names are
        // interpolated (and bracket-quoted).
        if (!isValidDatabaseName(name)) {
            throw std::invalid_argument("Invalid database name");
        }
        auto rows = master.query("SELECT DB_ID(?)", {odbc::Param::string(name)});
        if (rows.empty() || !rows[0][0].has_value()) {
            master.executeBatch("CREATE DATABASE [" + name + "]");
        }
    }
}

void createCatalogSchema(odbc::Connection& conn) {
    conn.executeBatch(R"SQL(
IF OBJECT_ID(N'dbo.cpp_users', N'U') IS NULL
CREATE TABLE dbo.cpp_users (
    id         BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    username   VARCHAR(255) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    created_at DATETIME2(6) NULL
);

IF OBJECT_ID(N'dbo.cpp_products', N'U') IS NULL
CREATE TABLE dbo.cpp_products (
    id    BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    name  VARCHAR(255)   NOT NULL,
    price DECIMAL(19, 2) NOT NULL
);

IF OBJECT_ID(N'dbo.cpp_reviews', N'U') IS NULL
CREATE TABLE dbo.cpp_reviews (
    id         BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    rating     INT    NOT NULL,
    comment    NVARCHAR(MAX) NULL,
    CONSTRAINT fk_cpp_reviews_user    FOREIGN KEY (user_id)    REFERENCES dbo.cpp_users(id),
    CONSTRAINT fk_cpp_reviews_product FOREIGN KEY (product_id) REFERENCES dbo.cpp_products(id)
);
)SQL");
}

void createCheckoutSchema(odbc::Connection& conn) {
    conn.executeBatch(R"SQL(
IF OBJECT_ID(N'dbo.cpp_orders', N'U') IS NULL
CREATE TABLE dbo.cpp_orders (
    id         BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    order_date DATETIME2(6) NULL CONSTRAINT df_cpp_orders_order_date DEFAULT SYSDATETIME()
);

IF OBJECT_ID(N'dbo.cpp_order_items', N'U') IS NULL
CREATE TABLE dbo.cpp_order_items (
    id         BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    order_id   BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity   INT    NOT NULL,
    CONSTRAINT fk_cpp_order_items_order FOREIGN KEY (order_id) REFERENCES dbo.cpp_orders(id) ON DELETE CASCADE
);
)SQL");
}

}  // namespace shopservice
