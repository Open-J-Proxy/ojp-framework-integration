package com.example.shopservice.controller;

import com.example.shopservice.entity.catalog.Product;
import com.example.shopservice.entity.checkout.Order;
import com.example.shopservice.entity.checkout.OrderItem;
import com.example.shopservice.repository.catalog.ProductRepository;
import com.example.shopservice.repository.checkout.OrderItemRepository;
import com.example.shopservice.repository.checkout.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/orders/{orderId}/items")
public class OrderItemController {
    @Autowired
    private OrderItemRepository orderItemRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ProductRepository productRepository;

    @PostMapping
    public ResponseEntity<OrderItem> create(@PathVariable Long orderId, @RequestBody OrderItem orderItem) {

        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) return ResponseEntity.badRequest().build();

        Optional<Product> prodOpt = productRepository.findById(orderItem.getProductId());
        if (prodOpt.isEmpty()) return ResponseEntity.badRequest().build();

        orderItem.setOrder(orderOpt.get());

        return ResponseEntity.ok(orderItemRepository.save(orderItem));
    }

    @GetMapping
    public List<OrderItem> list(@PathVariable Long orderId) {
        return orderItemRepository.findByOrderId(orderId);
    }

    @GetMapping("/{itemId}")
    public ResponseEntity<OrderItem> get(@PathVariable Long orderId, @PathVariable Long itemId) {
        Optional<OrderItem> itemOpt = orderItemRepository.findById(itemId);
        return itemOpt.filter(item -> item.getOrder().getId().equals(orderId))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{itemId}")
    public ResponseEntity<OrderItem> update(@PathVariable Long orderId, @PathVariable Long itemId, @RequestBody OrderItem orderItem) {
        if (isValidOrderItem(orderItem)) return ResponseEntity.badRequest().build();

        Optional<OrderItem> itemOpt = orderItemRepository.findById(itemId);
        if (itemOpt.isEmpty() || !itemOpt.get().getOrder().getId().equals(orderId)) return ResponseEntity.notFound().build();

        OrderItem existing = itemOpt.get();
        if(existing.getProductId() != orderItem.getProductId()){
            existing.setProductId(orderItem.getProductId());
        }

        existing.setQuantity(orderItem.getQuantity());
        return ResponseEntity.ok(orderItemRepository.save(existing));
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> delete(@PathVariable Long orderId, @PathVariable Long itemId) {
        Optional<OrderItem> itemOpt = orderItemRepository.findById(itemId);
        if (itemOpt.isEmpty() || !itemOpt.get().getOrder().getId().equals(orderId))
            return ResponseEntity.notFound().build();
        orderItemRepository.delete(itemOpt.get());
        return ResponseEntity.noContent().build();
    }

    private Boolean isValidOrderItem(OrderItem orderItem) {
        if(orderItem.getProductId() == null) return false;
        return productRepository.findById(orderItem.getProductId()).isEmpty();
    }
}
