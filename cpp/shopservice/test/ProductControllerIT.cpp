// Port of spring-boot/shopservice ProductControllerIT.

#include "test_support.hpp"

#include <gtest/gtest.h>

namespace shopservice::test {

class ProductControllerIT : public ::testing::Test {
protected:
    void SetUp() override {
        // Reviews reference products, so they are removed first (the Spring test relies
        // on JUnit class ordering instead).
        shop().reviews().deleteAll();
        shop().products().deleteAll();
    }

    ProductRepository& productRepository = shop().products();
};

TEST_F(ProductControllerIT, testCreateAndGetProduct) {
    auto created = httpPost("/products", R"({"name":"Widget","price":19.99})");
    ASSERT_EQ(200, created.status);
    EXPECT_FALSE(created.json()["id"].is_null());
    EXPECT_EQ("Widget", created.json()["name"]);

    Product prod = productRepository.findAll().at(0);
    auto fetched = httpGet("/products/" + std::to_string(*prod.id));
    ASSERT_EQ(200, fetched.status);
    EXPECT_DOUBLE_EQ(19.99, fetched.json()["price"].get<double>());
}

TEST_F(ProductControllerIT, testUpdateProduct) {
    ASSERT_EQ(200, httpPost("/products", R"({"name":"Gadget","price":10.50})").status);

    Product prod = productRepository.findAll().at(0);

    auto updated = httpPut("/products/" + std::to_string(*prod.id), R"({"name":"Gadget Updated","price":15.75})");
    ASSERT_EQ(200, updated.status);
    EXPECT_EQ("Gadget Updated", updated.json()["name"]);
    EXPECT_DOUBLE_EQ(15.75, updated.json()["price"].get<double>());
}

TEST_F(ProductControllerIT, testDeleteProduct) {
    ASSERT_EQ(200, httpPost("/products", R"({"name":"ToDelete","price":5.00})").status);

    Product prod = productRepository.findAll().at(0);

    EXPECT_EQ(204, httpDelete("/products/" + std::to_string(*prod.id)).status);
    EXPECT_EQ(404, httpGet("/products/" + std::to_string(*prod.id)).status);
}

}  // namespace shopservice::test
