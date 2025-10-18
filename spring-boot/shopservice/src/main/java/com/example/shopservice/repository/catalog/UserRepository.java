package com.example.shopservice.repository.catalog;
import com.example.shopservice.entity.catalog.User;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UserRepository extends JpaRepository<User, Long> {}
