#pragma once

#include "shopservice/model.hpp"
#include "shopservice/odbc.hpp"

#include <cstdint>
#include <optional>
#include <vector>

namespace shopservice {

// Zero-based page request (mirrors Spring Data's Pageable).
struct PageRequest {
    std::int64_t page = 0;
    std::int64_t size = 20;
};

// Repositories mirror the Spring Data JPA repositories of spring-boot/shopservice
// and talk to SQL Server through plain ODBC calls.

class UserRepository {
public:
    explicit UserRepository(odbc::DataSource& ds) : ds_(ds) {}

    User save(User user);
    std::vector<User> saveAll(std::vector<User> users);
    std::optional<User> findById(std::int64_t id);
    std::vector<User> findAll();
    std::vector<User> findAll(const PageRequest& page);
    std::int64_t count();
    void deleteById(std::int64_t id);
    void deleteAll();

private:
    odbc::DataSource& ds_;
};

class ProductRepository {
public:
    explicit ProductRepository(odbc::DataSource& ds) : ds_(ds) {}

    Product save(Product product);
    std::vector<Product> saveAll(std::vector<Product> products);
    std::optional<Product> findById(std::int64_t id);
    std::vector<Product> findAll();
    std::vector<Product> findAll(const PageRequest& page);
    std::int64_t count();
    void deleteById(std::int64_t id);
    void deleteAll();

private:
    odbc::DataSource& ds_;
};

class ReviewRepository {
public:
    explicit ReviewRepository(odbc::DataSource& ds) : ds_(ds) {}

    // review.user.id and review.product.id must reference existing rows.
    Review save(Review review);
    std::vector<Review> saveAll(std::vector<Review> reviews);
    std::optional<Review> findById(std::int64_t id);
    std::vector<Review> findAll();
    std::vector<Review> findAll(const PageRequest& page);
    std::vector<Review> findByUserId(std::int64_t userId, const PageRequest& page);
    std::vector<Review> findByProductId(std::int64_t productId, const PageRequest& page);
    std::int64_t count();
    std::int64_t countByUserId(std::int64_t userId);
    std::int64_t countByProductId(std::int64_t productId);
    void deleteById(std::int64_t id);
    void deleteAll();

private:
    odbc::DataSource& ds_;
};

class OrderRepository {
public:
    explicit OrderRepository(odbc::DataSource& ds) : ds_(ds) {}

    // Persists the order and cascades to its items (insert/update, orphans removed).
    Order save(Order order);
    std::vector<Order> saveAll(std::vector<Order> orders);
    std::optional<Order> findById(std::int64_t id);
    std::vector<Order> findAll();
    std::vector<Order> findAll(const PageRequest& page);
    std::int64_t count();
    // Deletes the order; its items are removed by ON DELETE CASCADE.
    void deleteById(std::int64_t id);
    void deleteAll();

private:
    odbc::DataSource& ds_;
};

class OrderItemRepository {
public:
    explicit OrderItemRepository(odbc::DataSource& ds) : ds_(ds) {}

    // item.orderId must reference an existing order.
    OrderItem save(OrderItem item);
    std::vector<OrderItem> saveAll(std::vector<OrderItem> items);
    std::optional<OrderItem> findById(std::int64_t id);
    std::vector<OrderItem> findAll();
    std::vector<OrderItem> findByOrderId(std::int64_t orderId);
    std::int64_t count();
    void deleteById(std::int64_t id);
    void deleteAll();

private:
    odbc::DataSource& ds_;
};

}  // namespace shopservice
