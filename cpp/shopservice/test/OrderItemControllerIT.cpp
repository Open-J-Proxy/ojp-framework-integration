// Port of spring-boot/shopservice OrderItemControllerIT.

#include "test_support.hpp"

#include <gtest/gtest.h>

namespace shopservice::test {

using nlohmann::json;

class OrderItemControllerIT : public ::testing::Test {
protected:
    void SetUp() override {
        shop().orderItems().deleteAll();
        shop().orders().deleteAll();

        savedUser = shop().users().save(user("testuser", "testuser@example.com"));
        savedProduct = shop().products().save(product("TestProduct", "25.99"));
        savedOrder = shop().orders().save(order(*savedUser.id));
    }

    std::string itemsPath() const { return "/orders/" + std::to_string(*savedOrder.id) + "/items"; }

    std::string itemJson(int quantity) const {
        return json{{"productId", *savedProduct.id}, {"quantity", quantity}}.dump();
    }

    std::int64_t createItem(int quantity) {
        auto created = httpPost(itemsPath(), itemJson(quantity));
        EXPECT_EQ(200, created.status);
        return created.json()["id"].get<std::int64_t>();
    }

    User savedUser;
    Product savedProduct;
    Order savedOrder;
};

TEST_F(OrderItemControllerIT, testCreateOrderItem) {
    auto created = httpPost(itemsPath(), itemJson(3));

    ASSERT_EQ(200, created.status);
    auto body = created.json();
    EXPECT_FALSE(body["id"].is_null());
    EXPECT_EQ(3, body["quantity"]);
    EXPECT_EQ(*savedProduct.id, body["productId"].get<std::int64_t>());
}

TEST_F(OrderItemControllerIT, testGetOrderItems) {
    // First create an order item
    ASSERT_EQ(200, httpPost(itemsPath(), itemJson(2)).status);

    // Then get the list of items
    auto list = httpGet(itemsPath());
    ASSERT_EQ(200, list.status);
    auto body = list.json();
    ASSERT_EQ(1u, body.size());
    EXPECT_EQ(2, body[0]["quantity"]);
}

TEST_F(OrderItemControllerIT, testGetOrderItem) {
    // First create an order item
    const auto itemId = createItem(4);

    // Then get the specific item
    auto fetched = httpGet(itemsPath() + "/" + std::to_string(itemId));
    ASSERT_EQ(200, fetched.status);
    EXPECT_EQ(itemId, fetched.json()["id"].get<std::int64_t>());
    EXPECT_EQ(4, fetched.json()["quantity"]);
}

TEST_F(OrderItemControllerIT, testUpdateOrderItem) {
    // First create an order item
    const auto itemId = createItem(1);

    // Then update the item
    auto updated = httpPut(itemsPath() + "/" + std::to_string(itemId), itemJson(5));
    ASSERT_EQ(200, updated.status);
    EXPECT_EQ(5, updated.json()["quantity"]);
}

TEST_F(OrderItemControllerIT, testDeleteOrderItem) {
    // First create an order item
    const auto itemId = createItem(1);

    // Then delete the item
    EXPECT_EQ(204, httpDelete(itemsPath() + "/" + std::to_string(itemId)).status);

    // Verify it's deleted
    EXPECT_EQ(404, httpGet(itemsPath() + "/" + std::to_string(itemId)).status);
}

}  // namespace shopservice::test
