package com.example.shopservice.controller;
import com.example.shopservice.entity.catalog.User;
import com.example.shopservice.repository.catalog.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {
    @Autowired
    private UserRepository userRepository;
    @PostMapping
    public User create(@RequestBody User user) {
        return userRepository.save(user);
    }
    @GetMapping
    public Page<User> list(Pageable pageable) {
        return userRepository.findAll(pageable);
    }
    @GetMapping("/{id}")
    public ResponseEntity<User> get(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    @PutMapping("/{id}")
    public ResponseEntity<User> update(@PathVariable Long id, @RequestBody User user) {
        return userRepository.findById(id)
                .map(existing -> {
                    existing.setUsername(user.getUsername());
                    existing.setEmail(user.getEmail());
                    return ResponseEntity.ok(userRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> delete(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(existing -> {
                    userRepository.delete(existing);
                    return ResponseEntity.noContent().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
