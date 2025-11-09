package com.example.shopservice.controller;

import com.example.shopservice.entity.checkout.Order;
import com.example.shopservice.entity.catalog.Product;
import com.example.shopservice.entity.catalog.User;
import com.example.shopservice.repository.checkout.OrderItemRepository;
import com.example.shopservice.repository.checkout.OrderRepository;
import com.example.shopservice.repository.catalog.ProductRepository;
import com.example.shopservice.repository.catalog.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class OrderAndOrderItemControllerIT {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private User user;
    private Product product1, product2;

    @BeforeEach
    void setup() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        userRepository.deleteAll();
        productRepository.deleteAll();

        User userObj = new User();
        userObj.setUsername("bob");
        userObj.setEmail("bob@example.com");
        user = userRepository.save(userObj);

        Product prod1 = new Product();
        prod1.setName("Thing");
        prod1.setPrice(new BigDecimal("10.00"));
        product1 = productRepository.save(prod1);

        Product prod2 = new Product();
        prod2.setName("Gadget");
        prod2.setPrice(new BigDecimal("5.50"));
        product2 = productRepository.save(prod2);
    }

    @Test
    void createOrderWithItemsAndGetItems() throws Exception {
        String orderJson = """
        {
          "userId":%d,
          "orderItems":[
            {"productId":%d,"quantity":2},
            {"productId":%d,"quantity":1}
          ]
        }
        """.formatted(user.getId(), product1.getId(), product2.getId());

        String response = mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andReturn().getResponse().getContentAsString();

        Order order = objectMapper.readValue(response, Order.class);

        mockMvc.perform(get("/orders/" + order.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderItems", hasSize(2)));

        mockMvc.perform(get("/orders/" + order.getId() + "/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void testDeleteOrder() throws Exception {
        String orderJson = """
        {
          "userId":%d,
          "orderItems":[
            {"productId":%d,"quantity":2},
            {"productId":%d,"quantity":1}
          ]
        }
        """.formatted(user.getId(), product1.getId(), product2.getId());

        String response = mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andReturn().getResponse().getContentAsString();

        Order order = objectMapper.readValue(response, Order.class);

        mockMvc.perform(delete("/orders/" + order.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/orders/" + order.getId()))
                .andExpect(status().isNotFound());
    }
}
