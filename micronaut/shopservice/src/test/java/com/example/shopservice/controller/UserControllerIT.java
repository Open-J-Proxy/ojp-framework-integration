package com.example.shopservice.controller;

import com.example.shopservice.entity.User;
import com.example.shopservice.repository.OrderItemRepository;
import com.example.shopservice.repository.OrderRepository;
import com.example.shopservice.repository.ReviewRepository;
import com.example.shopservice.repository.UserRepository;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
public class UserControllerIT {
    @Inject
    @Client("/")
    HttpClient client;
    
    @Inject
    private UserRepository userRepository;
    @Inject
    private OrderItemRepository orderItemRepository;
    @Inject
    private OrderRepository orderRepository;
    @Inject
    private ReviewRepository reviewRepository;

    @BeforeEach
    void setup() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        reviewRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void testCreateUser() throws Exception {
        String json = "{\"username\":\"alice\",\"email\":\"alice@example.com\",\"createdAt\":\"2024-01-15T10:30:00\"}";
        var response = client.toBlocking().exchange(
            HttpRequest.POST("/users", json)
                .contentType(MediaType.APPLICATION_JSON),
            User.class
        );
        
        assertEquals(HttpStatus.OK, response.status());
        User created = response.body();
        assertNotNull(created.getId());
        assertEquals("alice", created.getUsername());
        assertEquals("alice@example.com", created.getEmail());
        assertNotNull(created.getCreatedAt());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        assertEquals("2024-01-15T10:30:00", created.getCreatedAt().format(formatter));
    }

    @Test
    void testGetUser() throws Exception {
        String json = "{\"username\":\"bob\",\"email\":\"bob@example.com\",\"createdAt\":\"2024-01-16T14:20:00\"}";
        var response = client.toBlocking().exchange(
            HttpRequest.POST("/users", json)
                .contentType(MediaType.APPLICATION_JSON),
            User.class
        );
        
        Long id = response.body().getId();

        var getResponse = client.toBlocking().exchange(
            HttpRequest.GET("/users/" + id),
            User.class
        );
        
        assertEquals(HttpStatus.OK, getResponse.status());
        User retrieved = getResponse.body();
        assertEquals("bob", retrieved.getUsername());
        assertEquals("bob@example.com", retrieved.getEmail());
        assertNotNull(retrieved.getCreatedAt());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        assertEquals("2024-01-16T14:20:00", retrieved.getCreatedAt().format(formatter));
    }

    @Test
    void testUpdateUser() throws Exception {
        // First create a user
        String json = "{\"username\":\"carol\",\"email\":\"carol@example.com\"}";
        var response = client.toBlocking().exchange(
            HttpRequest.POST("/users", json)
                .contentType(MediaType.APPLICATION_JSON),
            User.class
        );
        
        Long id = response.body().getId();

        // Use JSON for update
        String updateJson = "{\"username\":\"carol_updated\",\"email\":\"carol_updated@example.com\"}";
        var updateResponse = client.toBlocking().exchange(
            HttpRequest.PUT("/users/" + id, updateJson)
                .contentType(MediaType.APPLICATION_JSON),
            User.class
        );
        
        assertEquals(HttpStatus.OK, updateResponse.status());
        User updated = updateResponse.body();
        assertEquals("carol_updated", updated.getUsername());
        assertEquals("carol_updated@example.com", updated.getEmail());
    }

    @Test
    void testDeleteUser() throws Exception {
        String json = "{\"username\":\"dave\",\"email\":\"dave@example.com\"}";
        var response = client.toBlocking().exchange(
            HttpRequest.POST("/users", json)
                .contentType(MediaType.APPLICATION_JSON),
            User.class
        );
        
        Long id = response.body().getId();

        var deleteResponse = client.toBlocking().exchange(
            HttpRequest.DELETE("/users/" + id)
        );
        
        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.status());

        // Use exception handling for 404 check
        try {
            client.toBlocking().exchange(
                HttpRequest.GET("/users/" + id),
                User.class
            );
            fail("Expected 404 Not Found");
        } catch (io.micronaut.http.client.exceptions.HttpClientResponseException e) {
            assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        }
    }
}