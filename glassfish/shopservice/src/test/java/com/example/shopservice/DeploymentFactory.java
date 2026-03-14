package com.example.shopservice;

import com.example.shopservice.entity.Order;
import com.example.shopservice.entity.OrderItem;
import com.example.shopservice.entity.Product;
import com.example.shopservice.entity.Review;
import com.example.shopservice.entity.User;
import com.example.shopservice.repository.OrderItemRepository;
import com.example.shopservice.repository.OrderRepository;
import com.example.shopservice.repository.ProductRepository;
import com.example.shopservice.repository.ReviewRepository;
import com.example.shopservice.repository.UserRepository;
import com.example.shopservice.resource.OrderItemResource;
import com.example.shopservice.resource.OrderResource;
import com.example.shopservice.resource.ProductResource;
import com.example.shopservice.resource.ReviewResource;
import com.example.shopservice.resource.UserResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;

/**
 * Factory that creates a ShrinkWrap {@link WebArchive} used by all Arquillian tests.
 *
 * <p>The archive contains:
 * <ul>
 *   <li>All application classes (entities, repositories, JAX-RS resources, application class).</li>
 *   <li>A test-specific {@code persistence.xml} configured for H2 with drop-and-create.</li>
 *   <li>A test-specific {@code glassfish-resources.xml} that defines a JDBC connection pool
 *       pointing to an H2 in-memory database via the OJP proxy driver.</li>
 * </ul>
 */
public final class DeploymentFactory {

    private DeploymentFactory() {}

    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class, "shopservice.war")
                // Application entry point
                .addClass(ShopServiceApplication.class)
                // Entities
                .addClass(User.class)
                .addClass(Product.class)
                .addClass(Order.class)
                .addClass(OrderItem.class)
                .addClass(Review.class)
                // Repositories
                .addClass(UserRepository.class)
                .addClass(ProductRepository.class)
                .addClass(OrderRepository.class)
                .addClass(OrderItemRepository.class)
                .addClass(ReviewRepository.class)
                // JAX-RS resources
                .addClass(UserResource.class)
                .addClass(ProductResource.class)
                .addClass(OrderResource.class)
                .addClass(OrderItemResource.class)
                .addClass(ReviewResource.class)
                // Use test persistence.xml (H2, drop-and-create schema)
                .addAsResource("META-INF/persistence-test.xml", "META-INF/persistence.xml")
                // Use test glassfish-resources.xml (H2 datasource via OJP)
                .addAsWebInfResource("glassfish-resources-test.xml", "glassfish-resources.xml");
    }
}
