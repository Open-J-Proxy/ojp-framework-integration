// Port of spring-boot/shopservice CatalogRepositoryIT.

#include "test_support.hpp"

#include <gtest/gtest.h>

namespace shopservice::test {

class CatalogRepositoryIT : public ::testing::Test {
protected:
    void SetUp() override {
        shop().reviews().deleteAll();
        shop().users().deleteAll();
        shop().products().deleteAll();
    }

    UserRepository& userRepository = shop().users();
    ProductRepository& productRepository = shop().products();
    ReviewRepository& reviewRepository = shop().reviews();
};

// ── User ────────────────────────────────────────────────────────────────────

TEST_F(CatalogRepositoryIT, testUserSaveAll) {
    auto saved = userRepository.saveAll({user("alice", "alice@example.com"), user("bob", "bob@example.com")});

    ASSERT_EQ(2u, saved.size());
    for (const auto& u : saved) EXPECT_TRUE(u.id.has_value());
}

TEST_F(CatalogRepositoryIT, testUserFindAll) {
    userRepository.saveAll({user("alice", "alice@example.com"), user("bob", "bob@example.com"),
                            user("charlie", "charlie@example.com")});

    auto all = userRepository.findAll();

    EXPECT_EQ(3u, all.size());
}

TEST_F(CatalogRepositoryIT, testUserDeleteAll) {
    userRepository.saveAll({user("alice", "alice@example.com"), user("bob", "bob@example.com")});
    ASSERT_EQ(2, userRepository.count());

    userRepository.deleteAll();

    EXPECT_TRUE(userRepository.findAll().empty());
}

// ── Product ─────────────────────────────────────────────────────────────────

TEST_F(CatalogRepositoryIT, testProductSaveAll) {
    auto saved = productRepository.saveAll({product("Widget", "9.99"), product("Gadget", "19.99")});

    ASSERT_EQ(2u, saved.size());
    for (const auto& p : saved) EXPECT_TRUE(p.id.has_value());
}

TEST_F(CatalogRepositoryIT, testProductFindAll) {
    productRepository.saveAll({product("Widget", "9.99"), product("Gadget", "19.99"), product("Doohickey", "4.50")});

    auto all = productRepository.findAll();

    EXPECT_EQ(3u, all.size());
}

TEST_F(CatalogRepositoryIT, testProductDeleteAll) {
    productRepository.saveAll({product("Widget", "9.99"), product("Gadget", "19.99")});
    ASSERT_EQ(2, productRepository.count());

    productRepository.deleteAll();

    EXPECT_TRUE(productRepository.findAll().empty());
}

// ── Review ──────────────────────────────────────────────────────────────────

TEST_F(CatalogRepositoryIT, testReviewSaveAll) {
    User u = userRepository.save(user("alice", "alice@example.com"));
    Product p = productRepository.save(product("Widget", "9.99"));

    auto saved = reviewRepository.saveAll({review(u, p, 5, "Excellent!"), review(u, p, 3, "Average")});

    ASSERT_EQ(2u, saved.size());
    for (const auto& r : saved) EXPECT_TRUE(r.id.has_value());
}

TEST_F(CatalogRepositoryIT, testReviewFindAll) {
    User u = userRepository.save(user("alice", "alice@example.com"));
    Product p = productRepository.save(product("Widget", "9.99"));
    reviewRepository.saveAll(
        {review(u, p, 5, "Excellent!"), review(u, p, 4, "Good"), review(u, p, 2, "Disappointing")});

    auto all = reviewRepository.findAll();

    EXPECT_EQ(3u, all.size());
}

TEST_F(CatalogRepositoryIT, testReviewDeleteAll) {
    User u = userRepository.save(user("alice", "alice@example.com"));
    Product p = productRepository.save(product("Widget", "9.99"));
    reviewRepository.saveAll({review(u, p, 5, "Excellent!"), review(u, p, 3, "Average")});
    ASSERT_EQ(2, reviewRepository.count());

    reviewRepository.deleteAll();

    EXPECT_TRUE(reviewRepository.findAll().empty());
}

}  // namespace shopservice::test
