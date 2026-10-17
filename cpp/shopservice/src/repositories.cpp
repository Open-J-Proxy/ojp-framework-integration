#include "shopservice/repositories.hpp"

#include <map>
#include <set>
#include <stdexcept>
#include <string>

namespace shopservice {

using odbc::Connection;
using odbc::Param;
using odbc::Row;

namespace {

// Text representation used for DATETIME2 columns (ISO-8601, style 126).
constexpr const char* kUserColumns =
    "u.id, u.username, u.email, CONVERT(VARCHAR(33), u.created_at, 126)";
constexpr const char* kProductColumns = "p.id, p.name, CONVERT(VARCHAR(40), p.price)";
constexpr const char* kReviewSelect =
    "SELECT r.id, r.rating, r.comment, "
    "u.id, u.username, u.email, CONVERT(VARCHAR(33), u.created_at, 126), "
    "p.id, p.name, CONVERT(VARCHAR(40), p.price) "
    "FROM dbo.cpp_reviews r "
    "JOIN dbo.cpp_users u ON u.id = r.user_id "
    "JOIN dbo.cpp_products p ON p.id = r.product_id";
constexpr const char* kOrderColumns = "id, user_id, CONVERT(VARCHAR(33), order_date, 126)";
constexpr const char* kOrderItemColumns = "id, order_id, product_id, quantity";
constexpr const char* kPaging = " ORDER BY id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

const std::string& required(const std::optional<std::string>& value) {
    if (!value) {
        throw std::runtime_error("Unexpected NULL value in result set");
    }
    return *value;
}

std::int64_t asInt64(const std::optional<std::string>& value) { return std::stoll(required(value)); }
int asInt(const std::optional<std::string>& value) { return std::stoi(required(value)); }

// SQL Server returns e.g. "2024-01-15T10:30:00.000000"; drop a zero fraction so the
// value round-trips like java.time.LocalDateTime#toString.
std::optional<std::string> asDateTime(const std::optional<std::string>& value) {
    if (!value) {
        return std::nullopt;
    }
    std::string text = *value;
    const auto dot = text.find('.');
    if (dot != std::string::npos) {
        auto end = text.find_last_not_of('0');
        text.erase(end == dot ? dot : end + 1);
    }
    return text;
}

User userFromRow(const Row& row, std::size_t offset = 0) {
    User u;
    u.id = asInt64(row[offset]);
    u.username = required(row[offset + 1]);
    u.email = required(row[offset + 2]);
    u.createdAt = asDateTime(row[offset + 3]);
    return u;
}

Product productFromRow(const Row& row, std::size_t offset = 0) {
    Product p;
    p.id = asInt64(row[offset]);
    p.name = required(row[offset + 1]);
    p.price = required(row[offset + 2]);
    return p;
}

Review reviewFromRow(const Row& row) {
    Review r;
    r.id = asInt64(row[0]);
    r.rating = asInt(row[1]);
    r.comment = row[2];
    r.user = userFromRow(row, 3);
    r.product = productFromRow(row, 7);
    return r;
}

Order orderFromRow(const Row& row) {
    Order o;
    o.id = asInt64(row[0]);
    o.userId = asInt64(row[1]);
    o.orderDate = asDateTime(row[2]);
    return o;
}

OrderItem orderItemFromRow(const Row& row) {
    OrderItem i;
    i.id = asInt64(row[0]);
    i.orderId = asInt64(row[1]);
    i.productId = asInt64(row[2]);
    i.quantity = asInt(row[3]);
    return i;
}

template <typename T, typename Mapper>
std::vector<T> mapRows(const std::vector<Row>& rows, Mapper mapper) {
    std::vector<T> result;
    result.reserve(rows.size());
    for (const auto& row : rows) {
        result.push_back(mapper(row));
    }
    return result;
}

std::vector<Param> pagingParams(const PageRequest& page, std::vector<Param> params = {}) {
    params.push_back(Param::int64(page.page * page.size));
    params.push_back(Param::int64(page.size));
    return params;
}

std::int64_t scalar(Connection& conn, const std::string& sql, std::vector<Param> params = {}) {
    auto rows = conn.query(sql, std::move(params));
    return asInt64(rows.at(0).at(0));
}

Param priceParam(const std::string& price) { return Param::decimal(price, 19, 2); }

// ── Per-connection operations (usable inside transactions) ──────────────────

void saveUser(Connection& conn, User& user) {
    if (user.id) {
        conn.execute("UPDATE dbo.cpp_users SET username = ?, email = ?, created_at = ? WHERE id = ?",
                     {Param::string(user.username), Param::string(user.email),
                      Param::optionalString(user.createdAt), Param::int64(*user.id)});
        return;
    }
    conn.execute(
        "INSERT INTO dbo.cpp_users (username, email, created_at) VALUES (?, ?, ?)",
        {Param::string(user.username), Param::string(user.email), Param::optionalString(user.createdAt)});
    user.id = scalar(conn, "SELECT CAST(@@IDENTITY AS BIGINT)");
}

void saveProduct(Connection& conn, Product& product) {
    if (product.id) {
        conn.execute("UPDATE dbo.cpp_products SET name = ?, price = ? WHERE id = ?",
                     {Param::string(product.name), priceParam(product.price), Param::int64(*product.id)});
        return;
    }
    conn.execute("INSERT INTO dbo.cpp_products (name, price) VALUES (?, ?)",
                 {Param::string(product.name), priceParam(product.price)});
    product.id = scalar(conn, "SELECT CAST(@@IDENTITY AS BIGINT)");
}

void saveReview(Connection& conn, Review& review) {
    if (!review.user.id || !review.product.id) {
        throw std::invalid_argument("A review requires a persisted user and product");
    }
    if (review.id) {
        conn.execute(
            "UPDATE dbo.cpp_reviews SET user_id = ?, product_id = ?, rating = ?, comment = ? WHERE id = ?",
            {Param::int64(*review.user.id), Param::int64(*review.product.id), Param::int32(review.rating),
             Param::optionalString(review.comment), Param::int64(*review.id)});
        return;
    }
    conn.execute(
        "INSERT INTO dbo.cpp_reviews (user_id, product_id, rating, comment) "
        "VALUES (?, ?, ?, ?)",
        {Param::int64(*review.user.id), Param::int64(*review.product.id), Param::int32(review.rating),
         Param::optionalString(review.comment)});
    review.id = scalar(conn, "SELECT CAST(@@IDENTITY AS BIGINT)");
}

void saveOrderItem(Connection& conn, OrderItem& item) {
    if (!item.orderId) {
        throw std::invalid_argument("An order item requires a persisted order");
    }
    if (item.id) {
        conn.execute("UPDATE dbo.cpp_order_items SET order_id = ?, product_id = ?, quantity = ? WHERE id = ?",
                     {Param::int64(*item.orderId), Param::int64(item.productId), Param::int32(item.quantity),
                      Param::int64(*item.id)});
        return;
    }
    conn.execute(
        "INSERT INTO dbo.cpp_order_items (order_id, product_id, quantity) VALUES (?, ?, ?)",
        {Param::int64(*item.orderId), Param::int64(item.productId), Param::int32(item.quantity)});
    item.id = scalar(conn, "SELECT CAST(@@IDENTITY AS BIGINT)");
}

void saveOrder(Connection& conn, Order& order) {
    if (order.id) {
        conn.execute("UPDATE dbo.cpp_orders SET user_id = ?, order_date = ? WHERE id = ?",
                     {Param::int64(order.userId), Param::optionalString(order.orderDate),
                      Param::int64(*order.id)});
    } else {
        // When no date is supplied the column default (SYSDATETIME) applies,
        // like the "orderDate = LocalDateTime.now()" initialiser of the JPA entity.
        conn.execute(
            "INSERT INTO dbo.cpp_orders (user_id, order_date) "
            "VALUES (?, COALESCE(?, SYSDATETIME()))",
            {Param::int64(order.userId), Param::optionalString(order.orderDate)});
        order.id = scalar(conn, "SELECT CAST(@@IDENTITY AS BIGINT)");
        auto rows = conn.query("SELECT CONVERT(VARCHAR(33), order_date, 126) FROM dbo.cpp_orders WHERE id = ?",
                               {Param::int64(*order.id)});
        order.orderDate = asDateTime(rows.at(0).at(0));
    }

    // Cascade to items; items no longer referenced are removed (orphanRemoval).
    std::set<std::int64_t> keep;
    for (auto& item : order.orderItems) {
        if (item.id) {
            auto owner = conn.query("SELECT order_id FROM dbo.cpp_order_items WHERE id = ?",
                                    {Param::int64(*item.id)});
            if (owner.empty() || asInt64(owner[0][0]) != *order.id) {
                item.id.reset();  // unknown or foreign item: persist as a new one
            }
        }
        item.orderId = order.id;
        saveOrderItem(conn, item);
        keep.insert(*item.id);
    }
    auto existing = conn.query("SELECT id FROM dbo.cpp_order_items WHERE order_id = ?", {Param::int64(*order.id)});
    for (const auto& row : existing) {
        const std::int64_t id = asInt64(row[0]);
        if (keep.count(id) == 0) {
            conn.execute("DELETE FROM dbo.cpp_order_items WHERE id = ?", {Param::int64(id)});
        }
    }
}

void attachItems(Connection& conn, std::vector<Order>& orders) {
    if (orders.empty()) {
        return;
    }
    // Orders are always read ordered by id, so a page is a contiguous id range.
    auto rows = conn.query(std::string("SELECT ") + kOrderItemColumns +
                               " FROM dbo.cpp_order_items WHERE order_id BETWEEN ? AND ? ORDER BY order_id, id",
                           {Param::int64(*orders.front().id), Param::int64(*orders.back().id)});
    std::map<std::int64_t, std::vector<OrderItem>> byOrder;
    for (const auto& row : rows) {
        OrderItem item = orderItemFromRow(row);
        byOrder[*item.orderId].push_back(std::move(item));
    }
    for (auto& order : orders) {
        auto it = byOrder.find(*order.id);
        if (it != byOrder.end()) {
            order.orderItems = std::move(it->second);
        }
    }
}

}  // namespace

// ── UserRepository ──────────────────────────────────────────────────────────

User UserRepository::save(User user) {
    // A transaction pins the backend connection through INSERT and identity lookup.
    ds_.inTransaction([&](Connection& conn) { saveUser(conn, user); });
    return user;
}

std::vector<User> UserRepository::saveAll(std::vector<User> users) {
    ds_.inTransaction([&](Connection& conn) {
        for (auto& u : users) saveUser(conn, u);
    });
    return users;
}

std::optional<User> UserRepository::findById(std::int64_t id) {
    auto conn = ds_.acquire();
    auto rows = conn->query(std::string("SELECT ") + kUserColumns + " FROM dbo.cpp_users u WHERE u.id = ?",
                            {Param::int64(id)});
    if (rows.empty()) return std::nullopt;
    return userFromRow(rows[0]);
}

std::vector<User> UserRepository::findAll() {
    auto conn = ds_.acquire();
    return mapRows<User>(conn->query(std::string("SELECT ") + kUserColumns + " FROM dbo.cpp_users u ORDER BY u.id"),
                         [](const Row& r) { return userFromRow(r); });
}

std::vector<User> UserRepository::findAll(const PageRequest& page) {
    auto conn = ds_.acquire();
    return mapRows<User>(
        conn->query(std::string("SELECT ") + kUserColumns + " FROM dbo.cpp_users u" + kPaging, pagingParams(page)),
        [](const Row& r) { return userFromRow(r); });
}

std::int64_t UserRepository::count() {
    auto conn = ds_.acquire();
    return scalar(*conn, "SELECT COUNT_BIG(*) FROM dbo.cpp_users");
}

void UserRepository::deleteById(std::int64_t id) {
    auto conn = ds_.acquire();
    conn->execute("DELETE FROM dbo.cpp_users WHERE id = ?", {Param::int64(id)});
}

void UserRepository::deleteAll() {
    auto conn = ds_.acquire();
    conn->execute("DELETE FROM dbo.cpp_users");
}

// ── ProductRepository ───────────────────────────────────────────────────────

Product ProductRepository::save(Product product) {
    ds_.inTransaction([&](Connection& conn) { saveProduct(conn, product); });
    return product;
}

std::vector<Product> ProductRepository::saveAll(std::vector<Product> products) {
    ds_.inTransaction([&](Connection& conn) {
        for (auto& p : products) saveProduct(conn, p);
    });
    return products;
}

std::optional<Product> ProductRepository::findById(std::int64_t id) {
    auto conn = ds_.acquire();
    auto rows = conn->query(std::string("SELECT ") + kProductColumns + " FROM dbo.cpp_products p WHERE p.id = ?",
                            {Param::int64(id)});
    if (rows.empty()) return std::nullopt;
    return productFromRow(rows[0]);
}

std::vector<Product> ProductRepository::findAll() {
    auto conn = ds_.acquire();
    return mapRows<Product>(
        conn->query(std::string("SELECT ") + kProductColumns + " FROM dbo.cpp_products p ORDER BY p.id"),
        [](const Row& r) { return productFromRow(r); });
}

std::vector<Product> ProductRepository::findAll(const PageRequest& page) {
    auto conn = ds_.acquire();
    return mapRows<Product>(conn->query(std::string("SELECT ") + kProductColumns + " FROM dbo.cpp_products p" +
                                            kPaging,
                                        pagingParams(page)),
                            [](const Row& r) { return productFromRow(r); });
}

std::int64_t ProductRepository::count() {
    auto conn = ds_.acquire();
    return scalar(*conn, "SELECT COUNT_BIG(*) FROM dbo.cpp_products");
}

void ProductRepository::deleteById(std::int64_t id) {
    auto conn = ds_.acquire();
    conn->execute("DELETE FROM dbo.cpp_products WHERE id = ?", {Param::int64(id)});
}

void ProductRepository::deleteAll() {
    auto conn = ds_.acquire();
    conn->execute("DELETE FROM dbo.cpp_products");
}

// ── ReviewRepository ────────────────────────────────────────────────────────

Review ReviewRepository::save(Review review) {
    ds_.inTransaction([&](Connection& conn) { saveReview(conn, review); });
    return review;
}

std::vector<Review> ReviewRepository::saveAll(std::vector<Review> reviews) {
    ds_.inTransaction([&](Connection& conn) {
        for (auto& r : reviews) saveReview(conn, r);
    });
    return reviews;
}

std::optional<Review> ReviewRepository::findById(std::int64_t id) {
    auto conn = ds_.acquire();
    auto rows = conn->query(std::string(kReviewSelect) + " WHERE r.id = ?", {Param::int64(id)});
    if (rows.empty()) return std::nullopt;
    return reviewFromRow(rows[0]);
}

std::vector<Review> ReviewRepository::findAll() {
    auto conn = ds_.acquire();
    return mapRows<Review>(conn->query(std::string(kReviewSelect) + " ORDER BY r.id"), reviewFromRow);
}

std::vector<Review> ReviewRepository::findAll(const PageRequest& page) {
    auto conn = ds_.acquire();
    return mapRows<Review>(
        conn->query(std::string(kReviewSelect) + " ORDER BY r.id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                    pagingParams(page)),
        reviewFromRow);
}

std::vector<Review> ReviewRepository::findByUserId(std::int64_t userId, const PageRequest& page) {
    auto conn = ds_.acquire();
    return mapRows<Review>(
        conn->query(std::string(kReviewSelect) +
                        " WHERE r.user_id = ? ORDER BY r.id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                    pagingParams(page, {Param::int64(userId)})),
        reviewFromRow);
}

std::vector<Review> ReviewRepository::findByProductId(std::int64_t productId, const PageRequest& page) {
    auto conn = ds_.acquire();
    return mapRows<Review>(
        conn->query(std::string(kReviewSelect) +
                        " WHERE r.product_id = ? ORDER BY r.id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                    pagingParams(page, {Param::int64(productId)})),
        reviewFromRow);
}

std::int64_t ReviewRepository::count() {
    auto conn = ds_.acquire();
    return scalar(*conn, "SELECT COUNT_BIG(*) FROM dbo.cpp_reviews");
}

std::int64_t ReviewRepository::countByUserId(std::int64_t userId) {
    auto conn = ds_.acquire();
    return scalar(*conn, "SELECT COUNT_BIG(*) FROM dbo.cpp_reviews WHERE user_id = ?", {Param::int64(userId)});
}

std::int64_t ReviewRepository::countByProductId(std::int64_t productId) {
    auto conn = ds_.acquire();
    return scalar(*conn, "SELECT COUNT_BIG(*) FROM dbo.cpp_reviews WHERE product_id = ?",
                  {Param::int64(productId)});
}

void ReviewRepository::deleteById(std::int64_t id) {
    auto conn = ds_.acquire();
    conn->execute("DELETE FROM dbo.cpp_reviews WHERE id = ?", {Param::int64(id)});
}

void ReviewRepository::deleteAll() {
    auto conn = ds_.acquire();
    conn->execute("DELETE FROM dbo.cpp_reviews");
}

// ── OrderRepository ─────────────────────────────────────────────────────────

Order OrderRepository::save(Order order) {
    ds_.inTransaction([&](Connection& conn) { saveOrder(conn, order); });
    return order;
}

std::vector<Order> OrderRepository::saveAll(std::vector<Order> orders) {
    ds_.inTransaction([&](Connection& conn) {
        for (auto& o : orders) saveOrder(conn, o);
    });
    return orders;
}

std::optional<Order> OrderRepository::findById(std::int64_t id) {
    auto conn = ds_.acquire();
    auto rows = conn->query(std::string("SELECT ") + kOrderColumns + " FROM dbo.cpp_orders WHERE id = ?",
                            {Param::int64(id)});
    if (rows.empty()) return std::nullopt;
    std::vector<Order> orders{orderFromRow(rows[0])};
    attachItems(*conn, orders);
    return orders[0];
}

std::vector<Order> OrderRepository::findAll() {
    auto conn = ds_.acquire();
    auto orders = mapRows<Order>(
        conn->query(std::string("SELECT ") + kOrderColumns + " FROM dbo.cpp_orders ORDER BY id"), orderFromRow);
    attachItems(*conn, orders);
    return orders;
}

std::vector<Order> OrderRepository::findAll(const PageRequest& page) {
    auto conn = ds_.acquire();
    auto orders = mapRows<Order>(
        conn->query(std::string("SELECT ") + kOrderColumns + " FROM dbo.cpp_orders" + kPaging, pagingParams(page)),
        orderFromRow);
    attachItems(*conn, orders);
    return orders;
}

std::int64_t OrderRepository::count() {
    auto conn = ds_.acquire();
    return scalar(*conn, "SELECT COUNT_BIG(*) FROM dbo.cpp_orders");
}

void OrderRepository::deleteById(std::int64_t id) {
    auto conn = ds_.acquire();
    conn->execute("DELETE FROM dbo.cpp_orders WHERE id = ?", {Param::int64(id)});
}

void OrderRepository::deleteAll() {
    auto conn = ds_.acquire();
    conn->execute("DELETE FROM dbo.cpp_orders");
}

// ── OrderItemRepository ─────────────────────────────────────────────────────

OrderItem OrderItemRepository::save(OrderItem item) {
    ds_.inTransaction([&](Connection& conn) { saveOrderItem(conn, item); });
    return item;
}

std::vector<OrderItem> OrderItemRepository::saveAll(std::vector<OrderItem> items) {
    ds_.inTransaction([&](Connection& conn) {
        for (auto& i : items) saveOrderItem(conn, i);
    });
    return items;
}

std::optional<OrderItem> OrderItemRepository::findById(std::int64_t id) {
    auto conn = ds_.acquire();
    auto rows = conn->query(std::string("SELECT ") + kOrderItemColumns + " FROM dbo.cpp_order_items WHERE id = ?",
                            {Param::int64(id)});
    if (rows.empty()) return std::nullopt;
    return orderItemFromRow(rows[0]);
}

std::vector<OrderItem> OrderItemRepository::findAll() {
    auto conn = ds_.acquire();
    return mapRows<OrderItem>(
        conn->query(std::string("SELECT ") + kOrderItemColumns + " FROM dbo.cpp_order_items ORDER BY id"),
        orderItemFromRow);
}

std::vector<OrderItem> OrderItemRepository::findByOrderId(std::int64_t orderId) {
    auto conn = ds_.acquire();
    return mapRows<OrderItem>(conn->query(std::string("SELECT ") + kOrderItemColumns +
                                              " FROM dbo.cpp_order_items WHERE order_id = ? ORDER BY id",
                                          {Param::int64(orderId)}),
                              orderItemFromRow);
}

std::int64_t OrderItemRepository::count() {
    auto conn = ds_.acquire();
    return scalar(*conn, "SELECT COUNT_BIG(*) FROM dbo.cpp_order_items");
}

void OrderItemRepository::deleteById(std::int64_t id) {
    auto conn = ds_.acquire();
    conn->execute("DELETE FROM dbo.cpp_order_items WHERE id = ?", {Param::int64(id)});
}

void OrderItemRepository::deleteAll() {
    auto conn = ds_.acquire();
    conn->execute("DELETE FROM dbo.cpp_order_items");
}

}  // namespace shopservice
