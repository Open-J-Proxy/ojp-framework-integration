#pragma once

#include "shopservice/config.hpp"
#include "shopservice/odbc.hpp"
#include "shopservice/repositories.hpp"

#include <memory>

namespace httplib {
class Server;
}

namespace shopservice {

// Wires the two ODBC datasources (catalog and checkout), the repositories and the
// REST controllers together, like the Spring Boot ShopServiceApplication.
class ShopService {
public:
    // Connects to SQL Server, creates the databases and cpp_* tables when missing.
    explicit ShopService(const DatabaseConfig& config);

    // Registers all REST endpoints on the given HTTP server.
    void registerRoutes(httplib::Server& server);

    UserRepository& users() { return users_; }
    ProductRepository& products() { return products_; }
    ReviewRepository& reviews() { return reviews_; }
    OrderRepository& orders() { return orders_; }
    OrderItemRepository& orderItems() { return orderItems_; }

private:
    std::shared_ptr<odbc::Environment> env_;
    odbc::DataSource catalog_;
    odbc::DataSource checkout_;
    UserRepository users_;
    ProductRepository products_;
    ReviewRepository reviews_;
    OrderRepository orders_;
    OrderItemRepository orderItems_;
};

}  // namespace shopservice
