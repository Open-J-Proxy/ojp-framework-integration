package com.example.shopservice.controller;
import com.example.shopservice.entity.catalog.Product;
import com.example.shopservice.entity.catalog.Review;
import com.example.shopservice.entity.catalog.User;
import com.example.shopservice.repository.catalog.ProductRepository;
import com.example.shopservice.repository.catalog.ReviewRepository;
import com.example.shopservice.repository.catalog.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/reviews")
public class ReviewController {
    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProductRepository productRepository;

    @PostMapping
    public ResponseEntity<Review> create(@RequestBody Review review) {
        if (review.getUser() == null || review.getUser().getId() == null)
            return ResponseEntity.badRequest().build();
        if (review.getProduct() == null || review.getProduct().getId() == null)
            return ResponseEntity.badRequest().build();

        Optional<User> userOpt = userRepository.findById(review.getUser().getId());
        Optional<Product> prodOpt = productRepository.findById(review.getProduct().getId());

        if (userOpt.isEmpty() || prodOpt.isEmpty())
            return ResponseEntity.badRequest().build();

        review.setUser(userOpt.get());
        review.setProduct(prodOpt.get());
        return ResponseEntity.ok(reviewRepository.save(review));
    }

    @GetMapping
    public Page<Review> list(Pageable pageable, @RequestParam(required = false) Long userId, @RequestParam(required = false) Long productId) {
        if (userId != null) {
            return reviewRepository.findByUserId(userId, pageable);
        } else if (productId != null) {
            return reviewRepository.findByProductId(productId, pageable);
        } else {
            return reviewRepository.findAll(pageable);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Review> get(@PathVariable Long id) {
        return reviewRepository.findById(id).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Review> update(@PathVariable Long id, @RequestBody Review review) {
        return reviewRepository.findById(id)
                .map(existing -> {
                    if (review.getUser() != null && review.getUser().getId() != null) {
                        userRepository.findById(review.getUser().getId()).ifPresent(existing::setUser);
                    }
                    if (review.getProduct() != null && review.getProduct().getId() != null) {
                        productRepository.findById(review.getProduct().getId()).ifPresent(existing::setProduct);
                    }
                    existing.setRating(review.getRating());
                    existing.setComment(review.getComment());
                    return ResponseEntity.ok(reviewRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> delete(@PathVariable Long id) {
        return reviewRepository.findById(id)
                .map(existing -> {
                    reviewRepository.delete(existing);
                    return ResponseEntity.noContent().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
