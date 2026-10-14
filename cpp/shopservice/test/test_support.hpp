#pragma once

// Shared fixtures for the integration tests: a single ShopService instance (backed by
// SQL Server through ODBC) and an embedded HTTP server, started once for the whole run
// like a Spring Boot test application context, plus a tiny HTTP client playing the
// role of MockMvc.

#include "shopservice/app.hpp"
#include "shopservice/model.hpp"

#include <nlohmann/json.hpp>

#include <cstdint>
#include <string>

namespace shopservice::test {

ShopService& shop();

struct HttpResponse {
    int status = 0;
    std::string body;

    nlohmann::json json() const { return nlohmann::json::parse(body); }
};

HttpResponse httpGet(const std::string& path);
HttpResponse httpPost(const std::string& path, const std::string& jsonBody);
HttpResponse httpPut(const std::string& path, const std::string& jsonBody);
HttpResponse httpDelete(const std::string& path);

inline User user(const std::string& username, const std::string& email) {
    User u;
    u.username = username;
    u.email = email;
    return u;
}

inline Product product(const std::string& name, const std::string& price) {
    Product p;
    p.name = name;
    p.price = price;
    return p;
}

inline Review review(const User& u, const Product& p, int rating, const std::string& comment) {
    Review r;
    r.user = u;
    r.product = p;
    r.rating = rating;
    r.comment = comment;
    return r;
}

inline Order order(std::int64_t userId) {
    Order o;
    o.userId = userId;
    return o;
}

inline OrderItem orderItem(const Order& o, std::int64_t productId, int quantity) {
    OrderItem i;
    i.orderId = o.id;
    i.productId = productId;
    i.quantity = quantity;
    return i;
}

}  // namespace shopservice::test
