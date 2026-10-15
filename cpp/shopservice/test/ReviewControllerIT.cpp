// Port of spring-boot/shopservice ReviewControllerIT.

#include "test_support.hpp"

#include <gtest/gtest.h>

namespace shopservice::test {

using nlohmann::json;

class ReviewControllerIT : public ::testing::Test {
protected:
    void SetUp() override {
        shop().reviews().deleteAll();
        shop().users().deleteAll();
        shop().products().deleteAll();

        savedUser = shop().users().save(user("bob", "bob@example.com"));
        savedProduct = shop().products().save(product("SuperToy", "99.99"));
    }

    std::string reviewJson(int rating, const std::string& comment) const {
        return json{{"user", {{"id", *savedUser.id}}},
                    {"product", {{"id", *savedProduct.id}}},
                    {"rating", rating},
                    {"comment", comment}}
            .dump();
    }

    User savedUser;
    Product savedProduct;
};

TEST_F(ReviewControllerIT, testCreateAndGetReview) {
    auto created = httpPost("/reviews", reviewJson(5, "Awesome!"));
    ASSERT_EQ(200, created.status);
    EXPECT_FALSE(created.json()["id"].is_null());
    EXPECT_EQ(5, created.json()["rating"]);

    auto list = httpGet("/reviews");
    ASSERT_EQ(200, list.status);
    EXPECT_FALSE(list.json()["content"].empty());
}

TEST_F(ReviewControllerIT, testDeleteReview) {
    auto created = httpPost("/reviews", reviewJson(3, "Not great"));
    ASSERT_EQ(200, created.status);
    ASSERT_FALSE(created.json()["id"].is_null());

    const auto reviewId = created.json()["id"].get<std::int64_t>();

    EXPECT_EQ(204, httpDelete("/reviews/" + std::to_string(reviewId)).status);
    EXPECT_EQ(404, httpGet("/reviews/" + std::to_string(reviewId)).status);
}

}  // namespace shopservice::test
