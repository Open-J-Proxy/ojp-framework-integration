package com.example.shopservice.repository.catalog;
import com.example.shopservice.entity.catalog.Product;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProductRepository extends JpaRepository<Product, Long> {}
