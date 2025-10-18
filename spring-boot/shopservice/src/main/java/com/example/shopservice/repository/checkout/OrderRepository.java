package com.example.shopservice.repository.checkout;
import com.example.shopservice.entity.checkout.Order;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    @EntityGraph(attributePaths = "orderItems")
    Optional<Order> findById(Long id);
}
