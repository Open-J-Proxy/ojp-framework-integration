package com.example.shopservice.controller;
import com.example.shopservice.entity.catalog.Product;
import com.example.shopservice.repository.catalog.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/products")
public class ProductController {
    @Autowired
    private ProductRepository productRepository;
    @PostMapping
    public Product create(@RequestBody Product product) {
        return productRepository.save(product);
    }
    @GetMapping
    public Page<Product> list(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> get(@PathVariable Long id) {
        return productRepository.findById(id).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    @PutMapping("/{id}")
    public ResponseEntity<Product> update(@PathVariable Long id, @RequestBody Product product) {
        return productRepository.findById(id)
                .map(existing -> {
                    existing.setName(product.getName());
                    existing.setPrice(product.getPrice());
                    return ResponseEntity.ok(productRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping()
    public ResponseEntity<Product> save(@RequestBody Product product) {
        return ResponseEntity.created(URI.create("/products/" + product.getId())).build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> delete(@PathVariable Long id) {
        return productRepository.findById(id)
                .map(existing -> {
                    productRepository.delete(existing);
                    return ResponseEntity.noContent().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
