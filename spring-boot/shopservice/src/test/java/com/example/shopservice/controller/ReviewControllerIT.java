package com.example.shopservice.controller;

import com.example.shopservice.entity.catalog.Product;
import com.example.shopservice.entity.catalog.Review;
import com.example.shopservice.entity.catalog.User;
import com.example.shopservice.repository.catalog.ProductRepository;
import com.example.shopservice.repository.catalog.ReviewRepository;
import com.example.shopservice.repository.catalog.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ReviewControllerIT {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private User user;
    private Product product;

    @BeforeEach
    void setup() {
        reviewRepository.deleteAll();
        userRepository.deleteAll();
        productRepository.deleteAll();

        User userObj = new User();
        userObj.setUsername("bob");
        userObj.setEmail("bob@example.com");
        user = userRepository.save(userObj);
        Product productObj = new Product();
        productObj.setName("SuperToy");
        productObj.setPrice(new BigDecimal("99.99"));
        product = productRepository.save(productObj);
    }

    @Test
    void testCreateAndGetReview() throws Exception {
        String reviewJson = """
        {
          "user":{"id":%d},
          "product":{"id":%d},
          "rating":5,
          "comment":"Awesome!"
        }
        """.formatted(user.getId(), product.getId());

        String location = mockMvc.perform(post("/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.rating").value(5))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", not(empty())));
    }

    @Test
    void testDeleteReview() throws Exception {
        String reviewJson = """
        {
          "user":{"id":%d},
          "product":{"id":%d},
          "rating":3,
          "comment":"Not great"
        }
        """.formatted(user.getId(), product.getId());

        String response = mockMvc.perform(post("/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andReturn().getResponse().getContentAsString();

        Review review = objectMapper.readValue(response, Review.class);
        
        mockMvc.perform(delete("/reviews/" + review.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/reviews/" + review.getId()))
                .andExpect(status().isNotFound());
    }
}
