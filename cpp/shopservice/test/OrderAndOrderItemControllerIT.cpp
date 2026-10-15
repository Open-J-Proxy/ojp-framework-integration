// Port of spring-boot/shopservice OrderAndOrderItemControllerIT.

#include "test_support.hpp"

#include <gtest/gtest.h>

namespace shopservice::test {

using nlohmann::json;

class OrderAndOrderItemControllerIT : public ::testing::Test {
protected:
    void SetUp() override {
        shop().orderItems().deleteAll();
        shop().orders().deleteAll();
        // Reviews reference users/products, so they are removed first (the Spring test
        // relies on JUnit class ordering instead).
        shop().reviews().deleteAll();
        shop().users().deleteAll();
        shop().products().deleteAll();

        savedUser = shop().users().save(user("bob", "bob@example.com"));
        product1 = shop().products().save(product("Thing", "10.00"));
        product2 = shop().products().save(product("Gadget", "5.50"));
    }

    std::string orderJson() const {
        return json{{"userId", *savedUser.id},
                    {"orderItems",
                     json::array({{{"productId", *product1.id}, {"quantity", 2}},
                                  {{"productId", *product2.id}, {"quantity", 1}}})}}
            .dump();
    }

    User savedUser;
    Product product1;
    Product product2;
};

TEST_F(OrderAndOrderItemControllerIT, createOrderWithItemsAndGetItems) {
    auto created = httpPost("/orders", orderJson());
    ASSERT_EQ(200, created.status);
    ASSERT_FALSE(created.json()["id"].is_null());
    const auto orderId = created.json()["id"].get<std::int64_t>();

    auto fetched = httpGet("/orders/" + std::to_string(orderId));
    ASSERT_EQ(200, fetched.status);
    EXPECT_EQ(2u, fetched.json()["orderItems"].size());

    auto items = httpGet("/orders/" + std::to_string(orderId) + "/items");
    ASSERT_EQ(200, items.status);
    EXPECT_EQ(2u, items.json().size());
}

TEST_F(OrderAndOrderItemControllerIT, testDeleteOrder) {
    auto created = httpPost("/orders", orderJson());
    ASSERT_EQ(200, created.status);
    ASSERT_FALSE(created.json()["id"].is_null());
    const auto orderId = created.json()["id"].get<std::int64_t>();

    EXPECT_EQ(204, httpDelete("/orders/" + std::to_string(orderId)).status);
    EXPECT_EQ(404, httpGet("/orders/" + std::to_string(orderId)).status);
}

}  // namespace shopservice::test
