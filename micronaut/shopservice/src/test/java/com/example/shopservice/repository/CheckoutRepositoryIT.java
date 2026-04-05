package com.example.shopservice.repository;

import com.example.shopservice.entity.Order;
import com.example.shopservice.entity.OrderItem;
import com.example.shopservice.entity.Product;
import com.example.shopservice.entity.User;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
public class CheckoutRepositoryIT {

    @Inject
    private UserRepository userRepository;
    @Inject
    private ProductRepository productRepository;
    @Inject
    private OrderRepository orderRepository;
    @Inject
    private OrderItemRepository orderItemRepository;
    @Inject
    private ReviewRepository reviewRepository;

    @BeforeEach
    void setup() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        reviewRepository.deleteAll();
        userRepository.deleteAll();
        productRepository.deleteAll();
    }

    // ── Order ─────────────────────────────────────────────────────────────────

    @Test
    void testOrderSaveAll() {
        User u = userRepository.save(user("alice", "alice@example.com"));

        List<Order> saved = orderRepository.saveAll(List.of(order(u), order(u)));

        assertEquals(2, saved.size());
        saved.forEach(o -> assertNotNull(o.getId()));
    }

    @Test
    void testOrderFindAll() {
        User u = userRepository.save(user("alice", "alice@example.com"));
        orderRepository.saveAll(List.of(order(u), order(u), order(u)));

        List<Order> all = orderRepository.findAll();

        assertEquals(3, all.size());
    }

    @Test
    void testOrderDeleteAll() {
        User u = userRepository.save(user("alice", "alice@example.com"));
        orderRepository.saveAll(List.of(order(u), order(u)));
        assertEquals(2, orderRepository.count());

        orderRepository.deleteAll();

        assertTrue(orderRepository.findAll().isEmpty());
    }

    // ── OrderItem ─────────────────────────────────────────────────────────────

    @Test
    void testOrderItemSaveAll() {
        User u = userRepository.save(user("bob", "bob@example.com"));
        Product p = productRepository.save(product("Widget", "9.99"));
        Order o = orderRepository.save(order(u));

        List<OrderItem> saved = orderItemRepository.saveAll(List.of(
                orderItem(o, p, 2),
                orderItem(o, p, 1)));

        assertEquals(2, saved.size());
        saved.forEach(i -> assertNotNull(i.getId()));
    }

    @Test
    void testOrderItemFindAll() {
        User u = userRepository.save(user("bob", "bob@example.com"));
        Product p = productRepository.save(product("Widget", "9.99"));
        Order o = orderRepository.save(order(u));
        orderItemRepository.saveAll(List.of(
                orderItem(o, p, 2),
                orderItem(o, p, 1),
                orderItem(o, p, 5)));

        List<OrderItem> all = orderItemRepository.findAll();

        assertEquals(3, all.size());
    }

    @Test
    void testOrderItemDeleteAll() {
        User u = userRepository.save(user("bob", "bob@example.com"));
        Product p = productRepository.save(product("Widget", "9.99"));
        Order o = orderRepository.save(order(u));
        orderItemRepository.saveAll(List.of(
                orderItem(o, p, 2),
                orderItem(o, p, 1)));
        assertEquals(2, orderItemRepository.count());

        orderItemRepository.deleteAll();

        assertTrue(orderItemRepository.findAll().isEmpty());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static User user(String username, String email) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        return u;
    }

    private static Product product(String name, String price) {
        Product p = new Product();
        p.setName(name);
        p.setPrice(new BigDecimal(price));
        return p;
    }

    private static Order order(User user) {
        Order o = new Order();
        o.setUser(user);
        return o;
    }

    private static OrderItem orderItem(Order order, Product product, int quantity) {
        OrderItem i = new OrderItem();
        i.setOrder(order);
        i.setProduct(product);
        i.setQuantity(quantity);
        return i;
    }
}
