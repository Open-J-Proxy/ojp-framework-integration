package com.example.shopservice.repository;

import com.example.shopservice.DeploymentFactory;
import com.example.shopservice.entity.Order;
import com.example.shopservice.entity.OrderItem;
import com.example.shopservice.entity.Product;
import com.example.shopservice.entity.Review;
import com.example.shopservice.entity.User;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In-container Arquillian tests that exercise {@code saveAll}, {@code findAll} and
 * {@code deleteAll} on the GlassFish CDI repository beans directly, without going through
 * the JAX-RS layer.
 *
 * <p>The deployment is {@code testable = true} (the default), so this test class is
 * deployed inside the embedded GlassFish container and CDI injection works normally.
 */
@ExtendWith(ArquillianExtension.class)
public class SaveAllRepositoryTest {

    @Deployment
    public static WebArchive createDeployment() {
        return DeploymentFactory.createDeployment();
    }

    @Inject
    private UserRepository userRepository;
    @Inject
    private ProductRepository productRepository;
    @Inject
    private ReviewRepository reviewRepository;
    @Inject
    private OrderRepository orderRepository;
    @Inject
    private OrderItemRepository orderItemRepository;

    @AfterEach
    @Transactional
    void cleanup() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        reviewRepository.deleteAll();
        userRepository.deleteAll();
        productRepository.deleteAll();
    }

    // ── User ──────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    public void testUserSaveAll() {
        List<User> saved = userRepository.saveAll(List.of(
                user("alice", "alice@example.com"),
                user("bob", "bob@example.com")));

        assertEquals(2, saved.size());
        saved.forEach(u -> assertNotNull(u.getId()));
    }

    @Test
    @Transactional
    public void testUserFindAll() {
        userRepository.saveAll(List.of(
                user("alice2", "alice2@example.com"),
                user("bob2", "bob2@example.com"),
                user("charlie2", "charlie2@example.com")));

        List<User> all = userRepository.findAll();

        assertEquals(3, all.size());
    }

    @Test
    @Transactional
    public void testUserDeleteAll() {
        userRepository.saveAll(List.of(
                user("alice3", "alice3@example.com"),
                user("bob3", "bob3@example.com")));

        userRepository.deleteAll();

        assertTrue(userRepository.findAll().isEmpty());
    }

    // ── Product ───────────────────────────────────────────────────────────────

    @Test
    @Transactional
    public void testProductSaveAll() {
        List<Product> saved = productRepository.saveAll(List.of(
                product("Widget", "9.99"),
                product("Gadget", "19.99")));

        assertEquals(2, saved.size());
        saved.forEach(p -> assertNotNull(p.getId()));
    }

    @Test
    @Transactional
    public void testProductFindAll() {
        productRepository.saveAll(List.of(
                product("Widget2", "9.99"),
                product("Gadget2", "19.99"),
                product("Doohickey", "4.50")));

        List<Product> all = productRepository.findAll();

        assertEquals(3, all.size());
    }

    @Test
    @Transactional
    public void testProductDeleteAll() {
        productRepository.saveAll(List.of(
                product("Widget3", "9.99"),
                product("Gadget3", "19.99")));

        productRepository.deleteAll();

        assertTrue(productRepository.findAll().isEmpty());
    }

    // ── Review ────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    public void testReviewSaveAll() {
        User u = userRepository.save(user("rev_alice", "rev_alice@example.com"));
        Product p = productRepository.save(product("Rev Widget", "9.99"));

        List<Review> saved = reviewRepository.saveAll(List.of(
                review(u, p, 5, "Excellent!"),
                review(u, p, 3, "Average")));

        assertEquals(2, saved.size());
        saved.forEach(r -> assertNotNull(r.getId()));
    }

    @Test
    @Transactional
    public void testReviewFindAll() {
        User u = userRepository.save(user("rev_bob", "rev_bob@example.com"));
        Product p = productRepository.save(product("Rev Gadget", "19.99"));
        reviewRepository.saveAll(List.of(
                review(u, p, 5, "Excellent!"),
                review(u, p, 4, "Good"),
                review(u, p, 2, "Disappointing")));

        List<Review> all = reviewRepository.findAll();

        assertEquals(3, all.size());
    }

    @Test
    @Transactional
    public void testReviewDeleteAll() {
        User u = userRepository.save(user("rev_charlie", "rev_charlie@example.com"));
        Product p = productRepository.save(product("Rev Doohickey", "4.50"));
        reviewRepository.saveAll(List.of(
                review(u, p, 5, "Excellent!"),
                review(u, p, 3, "Average")));

        reviewRepository.deleteAll();

        assertTrue(reviewRepository.findAll().isEmpty());
    }

    // ── Order ─────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    public void testOrderSaveAll() {
        User u = userRepository.save(user("ord_alice", "ord_alice@example.com"));

        List<Order> saved = orderRepository.saveAll(List.of(order(u), order(u)));

        assertEquals(2, saved.size());
        saved.forEach(o -> assertNotNull(o.getId()));
    }

    @Test
    @Transactional
    public void testOrderFindAll() {
        User u = userRepository.save(user("ord_bob", "ord_bob@example.com"));
        orderRepository.saveAll(List.of(order(u), order(u), order(u)));

        List<Order> all = orderRepository.findAll();

        assertEquals(3, all.size());
    }

    @Test
    @Transactional
    public void testOrderDeleteAll() {
        User u = userRepository.save(user("ord_charlie", "ord_charlie@example.com"));
        orderRepository.saveAll(List.of(order(u), order(u)));

        orderRepository.deleteAll();

        assertTrue(orderRepository.findAll().isEmpty());
    }

    // ── OrderItem ─────────────────────────────────────────────────────────────

    @Test
    @Transactional
    public void testOrderItemSaveAll() {
        User u = userRepository.save(user("oi_alice", "oi_alice@example.com"));
        Product p = productRepository.save(product("OI Widget", "10.00"));
        Order o = orderRepository.save(order(u));

        List<OrderItem> saved = orderItemRepository.saveAll(List.of(
                orderItem(o, p, 2),
                orderItem(o, p, 1)));

        assertEquals(2, saved.size());
        saved.forEach(i -> assertNotNull(i.getId()));
    }

    @Test
    @Transactional
    public void testOrderItemFindAll() {
        User u = userRepository.save(user("oi_bob", "oi_bob@example.com"));
        Product p = productRepository.save(product("OI Gadget", "5.50"));
        Order o = orderRepository.save(order(u));
        orderItemRepository.saveAll(List.of(
                orderItem(o, p, 2),
                orderItem(o, p, 1),
                orderItem(o, p, 3)));

        List<OrderItem> all = orderItemRepository.findAll();

        assertEquals(3, all.size());
    }

    @Test
    @Transactional
    public void testOrderItemDeleteAll() {
        User u = userRepository.save(user("oi_charlie", "oi_charlie@example.com"));
        Product p = productRepository.save(product("OI Doohickey", "4.00"));
        Order o = orderRepository.save(order(u));
        orderItemRepository.saveAll(List.of(
                orderItem(o, p, 2),
                orderItem(o, p, 1)));

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

    private static Review review(User user, Product product, int rating, String comment) {
        Review r = new Review();
        r.setUser(user);
        r.setProduct(product);
        r.setRating(rating);
        r.setComment(comment);
        return r;
    }

    private static Order order(User user) {
        Order o = new Order();
        o.setUser(user);
        o.setOrderDate(LocalDateTime.now());
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
