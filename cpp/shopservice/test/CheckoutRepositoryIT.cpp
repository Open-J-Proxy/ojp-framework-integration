// Port of spring-boot/shopservice CheckoutRepositoryIT.

#include "test_support.hpp"

#include <gtest/gtest.h>

namespace shopservice::test {

class CheckoutRepositoryIT : public ::testing::Test {
protected:
    void SetUp() override {
        shop().orderItems().deleteAll();
        shop().orders().deleteAll();
    }

    OrderRepository& orderRepository = shop().orders();
    OrderItemRepository& orderItemRepository = shop().orderItems();
};

// ── Order ───────────────────────────────────────────────────────────────────

TEST_F(CheckoutRepositoryIT, testOrderSaveAll) {
    auto saved = orderRepository.saveAll({order(1), order(2)});

    ASSERT_EQ(2u, saved.size());
    for (const auto& o : saved) EXPECT_TRUE(o.id.has_value());
}

TEST_F(CheckoutRepositoryIT, testOrderFindAll) {
    orderRepository.saveAll({order(1), order(2), order(3)});

    auto all = orderRepository.findAll();

    EXPECT_EQ(3u, all.size());
}

TEST_F(CheckoutRepositoryIT, testOrderDeleteAll) {
    orderRepository.saveAll({order(1), order(2)});
    ASSERT_EQ(2, orderRepository.count());

    orderRepository.deleteAll();

    EXPECT_TRUE(orderRepository.findAll().empty());
}

// ── OrderItem ───────────────────────────────────────────────────────────────

TEST_F(CheckoutRepositoryIT, testOrderItemSaveAll) {
    Order savedOrder = orderRepository.save(order(1));

    auto saved = orderItemRepository.saveAll({orderItem(savedOrder, 10, 2), orderItem(savedOrder, 20, 1)});

    ASSERT_EQ(2u, saved.size());
    for (const auto& i : saved) EXPECT_TRUE(i.id.has_value());
}

TEST_F(CheckoutRepositoryIT, testOrderItemFindAll) {
    Order savedOrder = orderRepository.save(order(1));
    orderItemRepository.saveAll(
        {orderItem(savedOrder, 10, 2), orderItem(savedOrder, 20, 1), orderItem(savedOrder, 30, 5)});

    auto all = orderItemRepository.findAll();

    EXPECT_EQ(3u, all.size());
}

TEST_F(CheckoutRepositoryIT, testOrderItemDeleteAll) {
    Order savedOrder = orderRepository.save(order(1));
    orderItemRepository.saveAll({orderItem(savedOrder, 10, 2), orderItem(savedOrder, 20, 1)});
    ASSERT_EQ(2, orderItemRepository.count());

    orderItemRepository.deleteAll();

    EXPECT_TRUE(orderItemRepository.findAll().empty());
}

}  // namespace shopservice::test
