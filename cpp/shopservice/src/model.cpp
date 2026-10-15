#include "shopservice/model.hpp"

#include <regex>

namespace shopservice {

using nlohmann::json;

namespace {

json optionalId(const std::optional<std::int64_t>& id) {
    return id ? json(*id) : json(nullptr);
}

json optionalString(const std::optional<std::string>& value) {
    return value ? json(*value) : json(nullptr);
}

}  // namespace

json toJson(const User& user) {
    return json{{"id", optionalId(user.id)},
                {"username", user.username},
                {"email", user.email},
                {"createdAt", optionalString(user.createdAt)}};
}

json toJson(const Product& product) {
    // Serialised as a JSON number, like a BigDecimal in the Spring Boot model.
    return json{{"id", optionalId(product.id)},
                {"name", product.name},
                {"price", product.price.empty() ? json(nullptr) : json::parse(product.price)}};
}

json toJson(const Review& review) {
    return json{{"id", optionalId(review.id)},
                {"user", toJson(review.user)},
                {"product", toJson(review.product)},
                {"rating", review.rating},
                {"comment", optionalString(review.comment)}};
}

json toJson(const OrderItem& item) {
    return json{{"id", optionalId(item.id)}, {"productId", item.productId}, {"quantity", item.quantity}};
}

json toJson(const Order& order) {
    json items = json::array();
    for (const auto& item : order.orderItems) {
        items.push_back(toJson(item));
    }
    return json{{"id", optionalId(order.id)},
                {"userId", order.userId},
                {"orderItems", items},
                {"orderDate", optionalString(order.orderDate)}};
}

std::optional<std::string> decimalFromJson(const json& value) {
    std::string text;
    if (value.is_number_integer() || value.is_number_float()) {
        text = value.dump();
    } else if (value.is_string()) {
        text = value.get<std::string>();
    } else {
        return std::nullopt;
    }
    static const std::regex pattern(R"(^-?\d{1,17}(\.\d{1,2})?$)");
    if (!std::regex_match(text, pattern)) {
        return std::nullopt;
    }
    return text;
}

bool isIsoLocalDateTime(const std::string& value) {
    static const std::regex pattern(R"(^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d{1,7})?)?$)");
    return std::regex_match(value, pattern);
}

}  // namespace shopservice
