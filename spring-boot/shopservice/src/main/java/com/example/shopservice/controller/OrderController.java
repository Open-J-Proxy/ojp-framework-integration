package com.example.shopservice.controller;

import com.example.shopservice.entity.checkout.Order;
import com.example.shopservice.entity.checkout.OrderItem;
import com.example.shopservice.repository.catalog.ProductRepository;
import com.example.shopservice.repository.catalog.UserRepository;
import com.example.shopservice.repository.checkout.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProductRepository productRepository;

    @PostMapping
    public ResponseEntity<Order> create(@RequestBody Order order) {
        if(!isUserValid(order.getUserId())) return ResponseEntity.badRequest().build();
        if(!isValidOrderItems(order)) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(orderRepository.save(order));
    }

    @GetMapping
    public Page<Order> list(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> get(@PathVariable Long id) {
        return orderRepository.findById(id).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Order> update(@PathVariable Long id, @RequestBody Order order) {
        return orderRepository.findById(id)
                .map(existing -> {
                    if(isUserValid(order.getUserId())) { existing.setUserId(order.getUserId()); }
                    if(order.getOrderDate() != null) { existing.setOrderDate(order.getOrderDate()); }
                    if(isValidOrderItems(order)){ existing.setOrderItems(order.getOrderItems()); }

                    return ResponseEntity.ok(orderRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> delete(@PathVariable Long id) {
        return orderRepository.findById(id)
                .map(existing -> {
                    orderRepository.delete(existing);
                    return ResponseEntity.noContent().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private Boolean isUserValid(Long userId) {
        if (userId == null) return false;
        return userRepository.findById(userId).isPresent();
    }

    private Boolean isValidOrderItems(Order order) {
        if(order.getOrderItems() != null && order.getOrderItems().isEmpty()) return false;

        for (OrderItem item : order.getOrderItems()) {
            if (item == null) return false;
            if (productRepository.findById(item.getProductId()).isEmpty()) return false;
        }

        return true;
    }
}
