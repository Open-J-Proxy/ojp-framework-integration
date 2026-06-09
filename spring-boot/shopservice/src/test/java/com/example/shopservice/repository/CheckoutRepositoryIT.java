package com.example.shopservice.repository;

import com.example.shopservice.ShopServiceApplication;

import com.example.shopservice.entity.checkout.Order;
import com.example.shopservice.entity.checkout.OrderItem;
import com.example.shopservice.repository.checkout.OrderItemRepository;
import com.example.shopservice.repository.checkout.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = ShopServiceApplication.class)
@ActiveProfiles("test")
@Import(config.SqlInitConfig.class)
public class CheckoutRepositoryIT {

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;

    @BeforeEach
    void setup() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
    }

    // ── Order ────────────────────────────────────────────────────────────────

    @Test
    void testOrderSaveAll() {
        List<Order> saved = orderRepository.saveAll(List.of(order(1L), order(2L)));

        assertEquals(2, saved.size());
        saved.forEach(o -> assertNotNull(o.getId()));
    }

    @Test
    void testOrderFindAll() {
        orderRepository.saveAll(List.of(order(1L), order(2L), order(3L)));

        List<Order> all = orderRepository.findAll();

        assertEquals(3, all.size());
    }

    @Test
    void testOrderDeleteAll() {
        orderRepository.saveAll(List.of(order(1L), order(2L)));
        assertEquals(2, orderRepository.count());

        orderRepository.deleteAll();

        assertTrue(orderRepository.findAll().isEmpty());
    }

    // ── OrderItem ────────────────────────────────────────────────────────────

    @Test
    void testOrderItemSaveAll() {
        Order savedOrder = orderRepository.save(order(1L));

        List<OrderItem> saved = orderItemRepository.saveAll(List.of(
                orderItem(savedOrder, 10L, 2),
                orderItem(savedOrder, 20L, 1)));

        assertEquals(2, saved.size());
        saved.forEach(i -> assertNotNull(i.getId()));
    }

    @Test
    void testOrderItemFindAll() {
        Order savedOrder = orderRepository.save(order(1L));
        orderItemRepository.saveAll(List.of(
                orderItem(savedOrder, 10L, 2),
                orderItem(savedOrder, 20L, 1),
                orderItem(savedOrder, 30L, 5)));

        List<OrderItem> all = orderItemRepository.findAll();

        assertEquals(3, all.size());
    }

    @Test
    void testOrderItemDeleteAll() {
        Order savedOrder = orderRepository.save(order(1L));
        orderItemRepository.saveAll(List.of(
                orderItem(savedOrder, 10L, 2),
                orderItem(savedOrder, 20L, 1)));
        assertEquals(2, orderItemRepository.count());

        orderItemRepository.deleteAll();

        assertTrue(orderItemRepository.findAll().isEmpty());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Order order(Long userId) {
        Order o = new Order();
        o.setUserId(userId);
        return o;
    }

    private static OrderItem orderItem(Order order, Long productId, int quantity) {
        OrderItem i = new OrderItem();
        i.setOrder(order);
        i.setProductId(productId);
        i.setQuantity(quantity);
        return i;
    }
}
