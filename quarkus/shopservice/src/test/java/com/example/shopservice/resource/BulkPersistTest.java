package com.example.shopservice.resource;

import com.example.shopservice.entity.Order;
import com.example.shopservice.entity.OrderItem;
import com.example.shopservice.entity.Product;
import com.example.shopservice.entity.Review;
import com.example.shopservice.entity.User;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests bulk persistence using Panache's {@code persist(Iterable)} — the equivalent of
 * {@code saveAll} for Quarkus PanacheEntity-based entities.
 *
 * <p>Each test method runs inside a transaction that is automatically rolled back after
 * the method completes (via {@link TestTransaction}), so there is no cross-test state.
 */
@QuarkusTest
public class BulkPersistTest {

    // ── User ─────────────────────────────────────────────────────────────────

    @Test
    @TestTransaction
    public void testPersistAllUsers() {
        User u1 = new User();
        u1.username = "bulk_alice";
        u1.email = "bulk_alice@example.com";
        User u2 = new User();
        u2.username = "bulk_bob";
        u2.email = "bulk_bob@example.com";

        User.persist(List.of(u1, u2));

        assertNotNull(u1.id);
        assertNotNull(u2.id);
        assertTrue(User.count() >= 2);
    }

    @Test
    @TestTransaction
    public void testFindAllUsers() {
        User u1 = new User();
        u1.username = "findall_alice";
        u1.email = "findall_alice@example.com";
        User u2 = new User();
        u2.username = "findall_bob";
        u2.email = "findall_bob@example.com";
        User u3 = new User();
        u3.username = "findall_charlie";
        u3.email = "findall_charlie@example.com";
        User.persist(List.of(u1, u2, u3));

        List<User> all = User.listAll();

        assertTrue(all.size() >= 3);
    }

    @Test
    @TestTransaction
    public void testDeleteAllUsers() {
        User u1 = new User();
        u1.username = "del_alice";
        u1.email = "del_alice@example.com";
        User u2 = new User();
        u2.username = "del_bob";
        u2.email = "del_bob@example.com";
        User.persist(List.of(u1, u2));

        User.deleteAll();

        assertEquals(0, User.count());
    }

    // ── Product ───────────────────────────────────────────────────────────────

    @Test
    @TestTransaction
    public void testPersistAllProducts() {
        Product p1 = new Product();
        p1.name = "Bulk Widget";
        p1.price = new BigDecimal("9.99");
        Product p2 = new Product();
        p2.name = "Bulk Gadget";
        p2.price = new BigDecimal("19.99");

        Product.persist(List.of(p1, p2));

        assertNotNull(p1.id);
        assertNotNull(p2.id);
        assertTrue(Product.count() >= 2);
    }

    @Test
    @TestTransaction
    public void testFindAllProducts() {
        Product p1 = new Product();
        p1.name = "FA Widget";
        p1.price = new BigDecimal("9.99");
        Product p2 = new Product();
        p2.name = "FA Gadget";
        p2.price = new BigDecimal("19.99");
        Product p3 = new Product();
        p3.name = "FA Doohickey";
        p3.price = new BigDecimal("4.50");
        Product.persist(List.of(p1, p2, p3));

        List<Product> all = Product.listAll();

        assertTrue(all.size() >= 3);
    }

    @Test
    @TestTransaction
    public void testDeleteAllProducts() {
        Product p1 = new Product();
        p1.name = "Del Widget";
        p1.price = new BigDecimal("9.99");
        Product p2 = new Product();
        p2.name = "Del Gadget";
        p2.price = new BigDecimal("19.99");
        Product.persist(List.of(p1, p2));

        Product.deleteAll();

        assertEquals(0, Product.count());
    }

    // ── Review ────────────────────────────────────────────────────────────────

    @Test
    @TestTransaction
    public void testPersistAllReviews() {
        User u = new User();
        u.username = "rev_user_" + System.nanoTime();
        u.email = u.username + "@example.com";
        u.persist();

        Product p = new Product();
        p.name = "Rev Product";
        p.price = new BigDecimal("5.00");
        p.persist();

        Review r1 = new Review();
        r1.user = u;
        r1.product = p;
        r1.rating = 5;
        r1.comment = "Excellent!";
        Review r2 = new Review();
        r2.user = u;
        r2.product = p;
        r2.rating = 3;
        r2.comment = "Average";

        Review.persist(List.of(r1, r2));

        assertNotNull(r1.id);
        assertNotNull(r2.id);
        assertTrue(Review.count() >= 2);
    }

    @Test
    @TestTransaction
    public void testDeleteAllReviews() {
        User u = new User();
        u.username = "del_rev_user_" + System.nanoTime();
        u.email = u.username + "@example.com";
        u.persist();

        Product p = new Product();
        p.name = "Del Rev Product";
        p.price = new BigDecimal("5.00");
        p.persist();

        Review r1 = new Review();
        r1.user = u;
        r1.product = p;
        r1.rating = 5;
        r1.comment = "Great!";
        Review r2 = new Review();
        r2.user = u;
        r2.product = p;
        r2.rating = 1;
        r2.comment = "Poor";
        Review.persist(List.of(r1, r2));

        Review.deleteAll();

        assertEquals(0, Review.count());
    }

    // ── Order ─────────────────────────────────────────────────────────────────

    @Test
    @TestTransaction
    public void testPersistAllOrders() {
        User u = new User();
        u.username = "ord_user_" + System.nanoTime();
        u.email = u.username + "@example.com";
        u.persist();

        Order o1 = new Order();
        o1.user = u;
        o1.createdAt = LocalDateTime.now();
        Order o2 = new Order();
        o2.user = u;
        o2.createdAt = LocalDateTime.now();

        Order.persist(List.of(o1, o2));

        assertNotNull(o1.id);
        assertNotNull(o2.id);
        assertTrue(Order.count() >= 2);
    }

    @Test
    @TestTransaction
    public void testDeleteAllOrders() {
        User u = new User();
        u.username = "del_ord_user_" + System.nanoTime();
        u.email = u.username + "@example.com";
        u.persist();

        Order o1 = new Order();
        o1.user = u;
        o1.createdAt = LocalDateTime.now();
        Order o2 = new Order();
        o2.user = u;
        o2.createdAt = LocalDateTime.now();
        Order.persist(List.of(o1, o2));

        OrderItem.deleteAll();
        Order.deleteAll();

        assertEquals(0, Order.count());
    }

    // ── OrderItem ─────────────────────────────────────────────────────────────

    @Test
    @TestTransaction
    public void testPersistAllOrderItems() {
        User u = new User();
        u.username = "oi_user_" + System.nanoTime();
        u.email = u.username + "@example.com";
        u.persist();

        Product p = new Product();
        p.name = "OI Product";
        p.price = new BigDecimal("10.00");
        p.persist();

        Order o = new Order();
        o.user = u;
        o.createdAt = LocalDateTime.now();
        o.persist();

        OrderItem i1 = new OrderItem();
        i1.order = o;
        i1.product = p;
        i1.quantity = 2;
        OrderItem i2 = new OrderItem();
        i2.order = o;
        i2.product = p;
        i2.quantity = 1;

        OrderItem.persist(List.of(i1, i2));

        assertNotNull(i1.id);
        assertNotNull(i2.id);
        assertTrue(OrderItem.count() >= 2);
    }
}
