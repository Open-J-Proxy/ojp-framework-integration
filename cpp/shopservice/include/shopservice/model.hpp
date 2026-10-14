#pragma once

#include <nlohmann/json.hpp>

#include <cstdint>
#include <optional>
#include <string>
#include <vector>

namespace shopservice {

// ── Catalog domain (cpp_catalog_db) ─────────────────────────────────────────

struct User {
    std::optional<std::int64_t> id;
    std::string username;
    std::string email;
    // ISO-8601 local date-time, e.g. "2024-01-15T10:30:00".
    std::optional<std::string> createdAt;
};

struct Product {
    std::optional<std::int64_t> id;
    std::string name;
    // DECIMAL(19,2) kept in its textual form to avoid binary floating point rounding.
    std::string price;
};

struct Review {
    std::optional<std::int64_t> id;
    User user;
    Product product;
    int rating = 0;
    std::optional<std::string> comment;
};

// ── Checkout domain (cpp_checkout_db) ───────────────────────────────────────

struct OrderItem {
    std::optional<std::int64_t> id;
    // Owning order; not serialised (mirrors @JsonBackReference in the Spring model).
    std::optional<std::int64_t> orderId;
    std::int64_t productId = 0;
    int quantity = 0;
};

struct Order {
    std::optional<std::int64_t> id;
    std::int64_t userId = 0;
    std::optional<std::string> orderDate;
    std::vector<OrderItem> orderItems;
};

nlohmann::json toJson(const User& user);
nlohmann::json toJson(const Product& product);
nlohmann::json toJson(const Review& review);
nlohmann::json toJson(const OrderItem& item);
nlohmann::json toJson(const Order& order);

// Converts a JSON number (or numeric string) into a canonical decimal string with at most
// 17 integer digits and 2 fractional digits. Returns nullopt when the value is not valid.
std::optional<std::string> decimalFromJson(const nlohmann::json& value);

// Validates an ISO-8601 local date-time ("yyyy-MM-ddTHH:mm:ss[.fffffff]").
bool isIsoLocalDateTime(const std::string& value);

}  // namespace shopservice
