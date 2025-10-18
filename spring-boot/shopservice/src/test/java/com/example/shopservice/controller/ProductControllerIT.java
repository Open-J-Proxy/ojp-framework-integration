package com.example.shopservice.controller;

import com.example.shopservice.entity.catalog.Product;
import com.example.shopservice.repository.catalog.ProductRepository;
import com.example.shopservice.repository.catalog.ReviewRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ProductControllerIT {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setup() {
        productRepository.deleteAll();
        reviewRepository.deleteAll();
    }

    @Test
    void testCreateAndGetProduct() throws Exception {
        String json = "{\"name\":\"Widget\",\"price\":19.99}";
        mockMvc.perform(post("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Widget"));

        Product prod = productRepository.findAll().get(0);
        mockMvc.perform(get("/products/" + prod.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(19.99));
    }

    @Test
    void testUpdateProduct() throws Exception {
        String createJson = "{\"name\":\"Gadget\",\"price\":10.50}";
        mockMvc.perform(post("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson))
                .andExpect(status().isOk());

        Product product = productRepository.findAll().get(0);
        
        String updateJson = "{\"name\":\"Gadget Updated\",\"price\":15.75}";
        mockMvc.perform(put("/products/" + product.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Gadget Updated"))
                .andExpect(jsonPath("$.price").value(15.75));
    }

    @Test
    void testDeleteProduct() throws Exception {
        String createJson = "{\"name\":\"ToDelete\",\"price\":5.00}";
        mockMvc.perform(post("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson))
                .andExpect(status().isOk());

        Product product = productRepository.findAll().get(0);
        
        mockMvc.perform(delete("/products/" + product.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/products/" + product.getId()))
                .andExpect(status().isNotFound());
    }
}
