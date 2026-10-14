#include "shopservice/app.hpp"

#include "shopservice/schema.hpp"

#include <httplib.h>
#include <nlohmann/json.hpp>

#include <functional>
#include <iostream>
#include <limits>
#include <stdexcept>

namespace shopservice {

using nlohmann::json;

namespace {

constexpr const char* kJson = "application/json";
constexpr std::int64_t kMaxPageSize = 2000;

// Thrown for malformed request input; mapped to 400 Bad Request.
struct BadRequest : std::runtime_error {
    using std::runtime_error::runtime_error;
};

void sendJson(httplib::Response& res, int status, const json& body) {
    res.status = status;
    res.set_content(body.dump(), kJson);
}

void sendStatus(httplib::Response& res, int status) {
    res.status = status;
    res.body.clear();
}

using Handler = std::function<void(const httplib::Request&, httplib::Response&)>;

// Maps exceptions to HTTP status codes. Database error details are logged, never returned.
Handler guarded(Handler handler) {
    return [handler = std::move(handler)](const httplib::Request& req, httplib::Response& res) {
        try {
            handler(req, res);
        } catch (const BadRequest&) {
            sendStatus(res, 400);
        } catch (const json::exception&) {
            sendStatus(res, 400);
        } catch (const std::exception& e) {
            std::cerr << "[shopservice] " << req.method << ' ' << req.path << " failed: " << e.what() << '\n';
            sendJson(res, 500, json{{"error", "Internal Server Error"}});
        }
    };
}

json parseBody(const httplib::Request& req) {
    json body = json::parse(req.body);  // json::parse_error -> 400
    if (!body.is_object()) {
        throw BadRequest("Request body must be a JSON object");
    }
    return body;
}

bool present(const json& obj, const char* key) {
    return obj.is_object() && obj.contains(key) && !obj.at(key).is_null();
}

std::optional<std::int64_t> optInt64(const json& obj, const char* key) {
    if (!present(obj, key)) return std::nullopt;
    const json& v = obj.at(key);
    if (v.is_number_unsigned() && v.get<std::uint64_t>() > static_cast<std::uint64_t>(std::numeric_limits<std::int64_t>::max())) {
        throw BadRequest(key);
    }
    if (!v.is_number_integer()) throw BadRequest(key);
    return v.get<std::int64_t>();
}

std::optional<int> optInt(const json& obj, const char* key) {
    auto v = optInt64(obj, key);
    if (!v) return std::nullopt;
    if (*v < std::numeric_limits<int>::min() || *v > std::numeric_limits<int>::max()) throw BadRequest(key);
    return static_cast<int>(*v);
}

std::optional<std::string> optString(const json& obj, const char* key) {
    if (!present(obj, key)) return std::nullopt;
    if (!obj.at(key).is_string()) throw BadRequest(key);
    return obj.at(key).get<std::string>();
}

std::string requiredString(const json& obj, const char* key) {
    auto v = optString(obj, key);
    if (!v) throw BadRequest(key);
    return *v;
}

std::optional<std::string> optDateTime(const json& obj, const char* key) {
    auto v = optString(obj, key);
    if (v && !isIsoLocalDateTime(*v)) throw BadRequest(key);
    return v;
}

// Reads {"<key>": {"id": N}} as used for review.user / review.product.
std::optional<std::int64_t> nestedId(const json& obj, const char* key) {
    if (!present(obj, key)) return std::nullopt;
    return optInt64(obj.at(key), "id");
}

// Path ids are matched by (\d+) but may still overflow int64.
std::optional<std::int64_t> pathId(const httplib::Request& req, std::size_t index) {
    try {
        return std::stoll(req.matches[index].str());
    } catch (const std::exception&) {
        return std::nullopt;
    }
}

std::optional<std::int64_t> queryInt64(const httplib::Request& req, const char* name) {
    if (!req.has_param(name)) return std::nullopt;
    try {
        std::size_t consumed = 0;
        const std::string raw = req.get_param_value(name);
        const std::int64_t v = std::stoll(raw, &consumed);
        if (consumed != raw.size()) throw BadRequest(name);
        return v;
    } catch (const BadRequest&) {
        throw;
    } catch (const std::exception&) {
        throw BadRequest(name);
    }
}

// Like Spring's Pageable resolver: invalid values fall back to the defaults.
PageRequest pageRequest(const httplib::Request& req) {
    PageRequest page;
    try {
        if (auto p = queryInt64(req, "page"); p && *p >= 0) page.page = *p;
    } catch (const BadRequest&) {
    }
    try {
        if (auto s = queryInt64(req, "size"); s && *s >= 1) page.size = std::min(*s, kMaxPageSize);
    } catch (const BadRequest&) {
    }
    return page;
}

// Spring Data compatible Page<T> JSON.
template <typename T>
json pageJson(const std::vector<T>& items, std::int64_t total, const PageRequest& page) {
    json content = json::array();
    for (const auto& item : items) content.push_back(toJson(item));
    const std::int64_t totalPages = (total + page.size - 1) / page.size;
    const json sort = {{"sorted", false}, {"unsorted", true}, {"empty", true}};
    return json{
        {"content", content},
        {"pageable",
         {{"pageNumber", page.page},
          {"pageSize", page.size},
          {"sort", sort},
          {"offset", page.page * page.size},
          {"paged", true},
          {"unpaged", false}}},
        {"totalElements", total},
        {"totalPages", totalPages},
        {"last", page.page + 1 >= totalPages},
        {"size", page.size},
        {"number", page.page},
        {"sort", sort},
        {"first", page.page == 0},
        {"numberOfElements", static_cast<std::int64_t>(items.size())},
        {"empty", items.empty()},
    };
}

std::vector<OrderItem> parseOrderItems(const json& array) {
    if (!array.is_array()) throw BadRequest("orderItems");
    std::vector<OrderItem> items;
    for (const auto& element : array) {
        if (!element.is_object()) throw BadRequest("orderItems");
        OrderItem item;
        auto productId = optInt64(element, "productId");
        if (!productId) throw BadRequest("productId");
        item.productId = *productId;
        item.quantity = optInt(element, "quantity").value_or(0);
        items.push_back(item);
    }
    return items;
}

}  // namespace

ShopService::ShopService(const DatabaseConfig& config)
    : env_(std::make_shared<odbc::Environment>()),
      catalog_(env_, config.connectionString(config.catalogDatabase)),
      checkout_(env_, config.connectionString(config.checkoutDatabase)),
      users_(catalog_),
      products_(catalog_),
      reviews_(catalog_),
      orders_(checkout_),
      orderItems_(checkout_) {
    ensureDatabasesExist(*env_, config);
    {
        auto conn = catalog_.acquire();
        createCatalogSchema(*conn);
    }
    {
        auto conn = checkout_.acquire();
        createCheckoutSchema(*conn);
    }
}

void ShopService::registerRoutes(httplib::Server& server) {
    using httplib::Request;
    using httplib::Response;

    // ── /users ──────────────────────────────────────────────────────────────
    server.Post("/users", guarded([this](const Request& req, Response& res) {
        json body = parseBody(req);
        User user;
        user.username = requiredString(body, "username");
        user.email = requiredString(body, "email");
        user.createdAt = optDateTime(body, "createdAt");
        sendJson(res, 200, toJson(users_.save(user)));
    }));

    server.Get("/users", guarded([this](const Request& req, Response& res) {
        PageRequest page = pageRequest(req);
        sendJson(res, 200, pageJson(users_.findAll(page), users_.count(), page));
    }));

    server.Get(R"(/users/(\d+))", guarded([this](const Request& req, Response& res) {
        auto id = pathId(req, 1);
        auto user = id ? users_.findById(*id) : std::nullopt;
        if (!user) return sendStatus(res, 404);
        sendJson(res, 200, toJson(*user));
    }));

    server.Put(R"(/users/(\d+))", guarded([this](const Request& req, Response& res) {
        auto id = pathId(req, 1);
        auto existing = id ? users_.findById(*id) : std::nullopt;
        if (!existing) return sendStatus(res, 404);
        json body = parseBody(req);
        existing->username = requiredString(body, "username");
        existing->email = requiredString(body, "email");
        sendJson(res, 200, toJson(users_.save(*existing)));
    }));

    server.Delete(R"(/users/(\d+))", guarded([this](const Request& req, Response& res) {
        auto id = pathId(req, 1);
        auto existing = id ? users_.findById(*id) : std::nullopt;
        if (!existing) return sendStatus(res, 404);
        users_.deleteById(*existing->id);
        sendStatus(res, 204);
    }));

    // ── /products ───────────────────────────────────────────────────────────
    server.Post("/products", guarded([this](const Request& req, Response& res) {
        json body = parseBody(req);
        Product product;
        product.name = requiredString(body, "name");
        auto price = present(body, "price") ? decimalFromJson(body.at("price")) : std::nullopt;
        if (!price) throw BadRequest("price");
        product.price = *price;
        sendJson(res, 200, toJson(products_.save(product)));
    }));

    server.Get("/products", guarded([this](const Request& req, Response& res) {
        PageRequest page = pageRequest(req);
        sendJson(res, 200, pageJson(products_.findAll(page), products_.count(), page));
    }));

    server.Get(R"(/products/(\d+))", guarded([this](const Request& req, Response& res) {
        auto id = pathId(req, 1);
        auto product = id ? products_.findById(*id) : std::nullopt;
        if (!product) return sendStatus(res, 404);
        sendJson(res, 200, toJson(*product));
    }));

    // Same (non-persisting) behaviour as the Spring Boot "PUT /products" endpoint.
    server.Put("/products", guarded([](const Request& req, Response& res) {
        json body = parseBody(req);
        auto id = optInt64(body, "id");
        res.set_header("Location", "/products/" + (id ? std::to_string(*id) : std::string("null")));
        sendStatus(res, 201);
    }));

    server.Put(R"(/products/(\d+))", guarded([this](const Request& req, Response& res) {
        auto id = pathId(req, 1);
        auto existing = id ? products_.findById(*id) : std::nullopt;
        if (!existing) return sendStatus(res, 404);
        json body = parseBody(req);
        existing->name = requiredString(body, "name");
        auto price = present(body, "price") ? decimalFromJson(body.at("price")) : std::nullopt;
        if (!price) throw BadRequest("price");
        existing->price = *price;
        sendJson(res, 200, toJson(products_.save(*existing)));
    }));

    server.Delete(R"(/products/(\d+))", guarded([this](const Request& req, Response& res) {
        auto id = pathId(req, 1);
        auto existing = id ? products_.findById(*id) : std::nullopt;
        if (!existing) return sendStatus(res, 404);
        products_.deleteById(*existing->id);
        sendStatus(res, 204);
    }));

    // ── /reviews ────────────────────────────────────────────────────────────
    server.Post("/reviews", guarded([this](const Request& req, Response& res) {
        json body = parseBody(req);
        auto userId = nestedId(body, "user");
        auto productId = nestedId(body, "product");
        if (!userId || !productId) return sendStatus(res, 400);
        auto user = users_.findById(*userId);
        auto product = products_.findById(*productId);
        if (!user || !product) return sendStatus(res, 400);

        Review review;
        review.user = *user;
        review.product = *product;
        review.rating = optInt(body, "rating").value_or(0);
        review.comment = optString(body, "comment");
        sendJson(res, 200, toJson(reviews_.save(review)));
    }));

    server.Get("/reviews", guarded([this](const Request& req, Response& res) {
        PageRequest page = pageRequest(req);
        if (auto userId = queryInt64(req, "userId")) {
            sendJson(res, 200, pageJson(reviews_.findByUserId(*userId, page), reviews_.countByUserId(*userId), page));
        } else if (auto productId = queryInt64(req, "productId")) {
            sendJson(res, 200,
                     pageJson(reviews_.findByProductId(*productId, page), reviews_.countByProductId(*productId), page));
        } else {
            sendJson(res, 200, pageJson(reviews_.findAll(page), reviews_.count(), page));
        }
    }));

    server.Get(R"(/reviews/(\d+))", guarded([this](const Request& req, Response& res) {
        auto id = pathId(req, 1);
        auto review = id ? reviews_.findById(*id) : std::nullopt;
        if (!review) return sendStatus(res, 404);
        sendJson(res, 200, toJson(*review));
    }));

    server.Put(R"(/reviews/(\d+))", guarded([this](const Request& req, Response& res) {
        auto id = pathId(req, 1);
        auto existing = id ? reviews_.findById(*id) : std::nullopt;
        if (!existing) return sendStatus(res, 404);
        json body = parseBody(req);
        if (auto userId = nestedId(body, "user")) {
            if (auto user = users_.findById(*userId)) existing->user = *user;
        }
        if (auto productId = nestedId(body, "product")) {
            if (auto product = products_.findById(*productId)) existing->product = *product;
        }
        existing->rating = optInt(body, "rating").value_or(0);
        existing->comment = optString(body, "comment");
        sendJson(res, 200, toJson(reviews_.save(*existing)));
    }));

    server.Delete(R"(/reviews/(\d+))", guarded([this](const Request& req, Response& res) {
        auto id = pathId(req, 1);
        auto existing = id ? reviews_.findById(*id) : std::nullopt;
        if (!existing) return sendStatus(res, 404);
        reviews_.deleteById(*existing->id);
        sendStatus(res, 204);
    }));

    // ── /orders ─────────────────────────────────────────────────────────────
    auto isUserValid = [this](const std::optional<std::int64_t>& userId) {
        return userId && users_.findById(*userId).has_value();
    };
    // Mirrors OrderController#isValidOrderItems: an empty list is invalid and every
    // item must reference an existing product.
    auto areOrderItemsValid = [this](const std::optional<std::vector<OrderItem>>& items) {
        if (!items) return true;
        if (items->empty()) return false;
        for (const auto& item : *items) {
            if (!products_.findById(item.productId)) return false;
        }
        return true;
    };
    auto readItems = [](const json& body) -> std::optional<std::vector<OrderItem>> {
        if (!present(body, "orderItems")) return std::nullopt;
        return parseOrderItems(body.at("orderItems"));
    };

    server.Post("/orders", guarded([this, isUserValid, areOrderItemsValid, readItems](const Request& req, Response& res) {
        json body = parseBody(req);
        auto userId = optInt64(body, "userId");
        if (!isUserValid(userId)) return sendStatus(res, 400);
        auto items = readItems(body);
        if (!areOrderItemsValid(items)) return sendStatus(res, 400);

        Order order;
        order.userId = *userId;
        order.orderDate = optDateTime(body, "orderDate");
        if (items) order.orderItems = std::move(*items);
        sendJson(res, 200, toJson(orders_.save(order)));
    }));

    server.Get("/orders", guarded([this](const Request& req, Response& res) {
        PageRequest page = pageRequest(req);
        sendJson(res, 200, pageJson(orders_.findAll(page), orders_.count(), page));
    }));

    server.Get(R"(/orders/(\d+))", guarded([this](const Request& req, Response& res) {
        auto id = pathId(req, 1);
        auto order = id ? orders_.findById(*id) : std::nullopt;
        if (!order) return sendStatus(res, 404);
        sendJson(res, 200, toJson(*order));
    }));

    server.Put(R"(/orders/(\d+))", guarded([this, isUserValid, areOrderItemsValid, readItems](const Request& req, Response& res) {
        auto id = pathId(req, 1);
        auto existing = id ? orders_.findById(*id) : std::nullopt;
        if (!existing) return sendStatus(res, 404);
        json body = parseBody(req);
        auto userId = optInt64(body, "userId");
        if (isUserValid(userId)) existing->userId = *userId;
        if (auto orderDate = optDateTime(body, "orderDate")) existing->orderDate = orderDate;
        auto items = readItems(body);
        if (items && areOrderItemsValid(items)) existing->orderItems = std::move(*items);
        sendJson(res, 200, toJson(orders_.save(*existing)));
    }));

    server.Delete(R"(/orders/(\d+))", guarded([this](const Request& req, Response& res) {
        auto id = pathId(req, 1);
        auto existing = id ? orders_.findById(*id) : std::nullopt;
        if (!existing) return sendStatus(res, 404);
        orders_.deleteById(*existing->id);
        sendStatus(res, 204);
    }));

    // ── /orders/{orderId}/items ─────────────────────────────────────────────
    server.Post(R"(/orders/(\d+)/items)", guarded([this](const Request& req, Response& res) {
        auto orderId = pathId(req, 1);
        auto order = orderId ? orders_.findById(*orderId) : std::nullopt;
        if (!order) return sendStatus(res, 400);
        json body = parseBody(req);
        auto productId = optInt64(body, "productId");
        if (!productId || !products_.findById(*productId)) return sendStatus(res, 400);

        OrderItem item;
        item.orderId = order->id;
        item.productId = *productId;
        item.quantity = optInt(body, "quantity").value_or(0);
        sendJson(res, 200, toJson(orderItems_.save(item)));
    }));

    server.Get(R"(/orders/(\d+)/items)", guarded([this](const Request& req, Response& res) {
        auto orderId = pathId(req, 1);
        json items = json::array();
        if (orderId) {
            for (const auto& item : orderItems_.findByOrderId(*orderId)) items.push_back(toJson(item));
        }
        sendJson(res, 200, items);
    }));

    auto findItemInOrder = [this](const Request& req) -> std::optional<OrderItem> {
        auto orderId = pathId(req, 1);
        auto itemId = pathId(req, 2);
        if (!orderId || !itemId) return std::nullopt;
        auto item = orderItems_.findById(*itemId);
        if (!item || item->orderId != orderId) return std::nullopt;
        return item;
    };

    server.Get(R"(/orders/(\d+)/items/(\d+))", guarded([findItemInOrder](const Request& req, Response& res) {
        auto item = findItemInOrder(req);
        if (!item) return sendStatus(res, 404);
        sendJson(res, 200, toJson(*item));
    }));

    server.Put(R"(/orders/(\d+)/items/(\d+))", guarded([this, findItemInOrder](const Request& req, Response& res) {
        json body = parseBody(req);
        auto productId = optInt64(body, "productId");
        if (productId && !products_.findById(*productId)) return sendStatus(res, 400);
        auto item = findItemInOrder(req);
        if (!item) return sendStatus(res, 404);
        if (productId) item->productId = *productId;
        if (auto quantity = optInt(body, "quantity")) item->quantity = *quantity;
        sendJson(res, 200, toJson(orderItems_.save(*item)));
    }));

    server.Delete(R"(/orders/(\d+)/items/(\d+))", guarded([this, findItemInOrder](const Request& req, Response& res) {
        auto item = findItemInOrder(req);
        if (!item) return sendStatus(res, 404);
        orderItems_.deleteById(*item->id);
        sendStatus(res, 204);
    }));
}

}  // namespace shopservice
