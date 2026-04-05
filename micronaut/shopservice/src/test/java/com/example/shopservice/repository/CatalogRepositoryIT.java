package com.example.shopservice.repository;

import com.example.shopservice.entity.Product;
import com.example.shopservice.entity.Review;
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
public class CatalogRepositoryIT {

    @Inject
    private UserRepository userRepository;
    @Inject
    private ProductRepository productRepository;
    @Inject
    private ReviewRepository reviewRepository;

    @BeforeEach
    void setup() {
        reviewRepository.deleteAll();
        userRepository.deleteAll();
        productRepository.deleteAll();
    }

    // ── User ──────────────────────────────────────────────────────────────────

    @Test
    void testUserSaveAll() {
        List<User> saved = userRepository.saveAll(List.of(
                user("alice", "alice@example.com"),
                user("bob", "bob@example.com")));

        assertEquals(2, saved.size());
        saved.forEach(u -> assertNotNull(u.getId()));
    }

    @Test
    void testUserFindAll() {
        userRepository.saveAll(List.of(
                user("alice", "alice@example.com"),
                user("bob", "bob@example.com"),
                user("charlie", "charlie@example.com")));

        List<User> all = userRepository.findAll();

        assertEquals(3, all.size());
    }

    @Test
    void testUserDeleteAll() {
        userRepository.saveAll(List.of(
                user("alice", "alice@example.com"),
                user("bob", "bob@example.com")));
        assertEquals(2, userRepository.count());

        userRepository.deleteAll();

        assertTrue(userRepository.findAll().isEmpty());
    }

    // ── Product ───────────────────────────────────────────────────────────────

    @Test
    void testProductSaveAll() {
        List<Product> saved = productRepository.saveAll(List.of(
                product("Widget", "9.99"),
                product("Gadget", "19.99")));

        assertEquals(2, saved.size());
        saved.forEach(p -> assertNotNull(p.getId()));
    }

    @Test
    void testProductFindAll() {
        productRepository.saveAll(List.of(
                product("Widget", "9.99"),
                product("Gadget", "19.99"),
                product("Doohickey", "4.50")));

        List<Product> all = productRepository.findAll();

        assertEquals(3, all.size());
    }

    @Test
    void testProductDeleteAll() {
        productRepository.saveAll(List.of(
                product("Widget", "9.99"),
                product("Gadget", "19.99")));
        assertEquals(2, productRepository.count());

        productRepository.deleteAll();

        assertTrue(productRepository.findAll().isEmpty());
    }

    // ── Review ────────────────────────────────────────────────────────────────

    @Test
    void testReviewSaveAll() {
        User u = userRepository.save(user("alice", "alice@example.com"));
        Product p = productRepository.save(product("Widget", "9.99"));

        List<Review> saved = reviewRepository.saveAll(List.of(
                review(u, p, 5, "Excellent!"),
                review(u, p, 3, "Average")));

        assertEquals(2, saved.size());
        saved.forEach(r -> assertNotNull(r.getId()));
    }

    @Test
    void testReviewFindAll() {
        User u = userRepository.save(user("alice", "alice@example.com"));
        Product p = productRepository.save(product("Widget", "9.99"));
        reviewRepository.saveAll(List.of(
                review(u, p, 5, "Excellent!"),
                review(u, p, 4, "Good"),
                review(u, p, 2, "Disappointing")));

        List<Review> all = reviewRepository.findAll();

        assertEquals(3, all.size());
    }

    @Test
    void testReviewDeleteAll() {
        User u = userRepository.save(user("alice", "alice@example.com"));
        Product p = productRepository.save(product("Widget", "9.99"));
        reviewRepository.saveAll(List.of(
                review(u, p, 5, "Excellent!"),
                review(u, p, 3, "Average")));
        assertEquals(2, reviewRepository.count());

        reviewRepository.deleteAll();

        assertTrue(reviewRepository.findAll().isEmpty());
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
}
