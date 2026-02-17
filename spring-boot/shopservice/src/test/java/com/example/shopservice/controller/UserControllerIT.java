package com.example.shopservice.controller;

import com.example.shopservice.entity.catalog.User;
import com.example.shopservice.repository.catalog.ReviewRepository;
import com.example.shopservice.repository.catalog.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(config.SqlInitConfig.class)
public class UserControllerIT {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ReviewRepository reviewRepository;

    @BeforeEach
    void setup() {
        reviewRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void testCreateAndGetUser() throws Exception {
        String json = "{\"username\":\"alice\",\"email\":\"alice@example.com\",\"createdAt\":\"2024-01-15T10:30:00\"}";
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.createdAt").value("2024-01-15T10:30:00"));

        User user = userRepository.findAll().get(0);
        mockMvc.perform(get("/users/" + user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.createdAt").value("2024-01-15T10:30:00"));
    }

    @Test
    void testUpdateUser() throws Exception {
        String createJson = "{\"username\":\"bob\",\"email\":\"bob@example.com\"}";
        String response = mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        User user = userRepository.findAll().get(0);
        
        String updateJson = "{\"username\":\"bob_updated\",\"email\":\"bob_updated@example.com\"}";
        mockMvc.perform(put("/users/" + user.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("bob_updated"))
                .andExpect(jsonPath("$.email").value("bob_updated@example.com"));
    }

    @Test
    void testDeleteUser() throws Exception {
        String createJson = "{\"username\":\"charlie\",\"email\":\"charlie@example.com\"}";
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson))
                .andExpect(status().isOk());

        User user = userRepository.findAll().get(0);
        
        mockMvc.perform(delete("/users/" + user.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/users/" + user.getId()))
                .andExpect(status().isNotFound());
    }
}
