// Port of spring-boot/shopservice UserControllerIT.

#include "test_support.hpp"

#include <gtest/gtest.h>

namespace shopservice::test {

class UserControllerIT : public ::testing::Test {
protected:
    void SetUp() override {
        shop().reviews().deleteAll();
        shop().users().deleteAll();
    }

    UserRepository& userRepository = shop().users();
};

TEST_F(UserControllerIT, testCreateAndGetUser) {
    auto created = httpPost("/users",
                            R"({"username":"alice","email":"alice@example.com","createdAt":"2024-01-15T10:30:00"})");
    ASSERT_EQ(200, created.status);
    auto body = created.json();
    EXPECT_FALSE(body["id"].is_null());
    EXPECT_EQ("alice", body["username"]);
    EXPECT_FALSE(body["createdAt"].is_null());

    User u = userRepository.findAll().at(0);
    ASSERT_TRUE(u.createdAt.has_value());
    EXPECT_EQ("2024-01-15T10:30:00", *u.createdAt);

    auto fetched = httpGet("/users/" + std::to_string(*u.id));
    ASSERT_EQ(200, fetched.status);
    EXPECT_EQ("alice@example.com", fetched.json()["email"]);
    EXPECT_FALSE(fetched.json()["createdAt"].is_null());

    auto retrieved = userRepository.findById(*u.id);
    ASSERT_TRUE(retrieved.has_value());
    EXPECT_EQ("2024-01-15T10:30:00", retrieved->createdAt.value_or(""));
}

TEST_F(UserControllerIT, testUpdateUser) {
    ASSERT_EQ(200, httpPost("/users", R"({"username":"bob","email":"bob@example.com"})").status);

    User u = userRepository.findAll().at(0);

    auto updated = httpPut("/users/" + std::to_string(*u.id),
                           R"({"username":"bob_updated","email":"bob_updated@example.com"})");
    ASSERT_EQ(200, updated.status);
    EXPECT_EQ("bob_updated", updated.json()["username"]);
    EXPECT_EQ("bob_updated@example.com", updated.json()["email"]);
}

TEST_F(UserControllerIT, testDeleteUser) {
    ASSERT_EQ(200, httpPost("/users", R"({"username":"charlie","email":"charlie@example.com"})").status);

    User u = userRepository.findAll().at(0);

    EXPECT_EQ(204, httpDelete("/users/" + std::to_string(*u.id)).status);
    EXPECT_EQ(404, httpGet("/users/" + std::to_string(*u.id)).status);
}

}  // namespace shopservice::test
