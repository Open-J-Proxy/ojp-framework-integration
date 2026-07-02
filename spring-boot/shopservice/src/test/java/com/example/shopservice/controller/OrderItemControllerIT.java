package com.example.shopservice.controller;

import com.example.shopservice.ShopServiceApplication;

import com.example.shopservice.entity.catalog.Product;
import com.example.shopservice.entity.catalog.User;
import com.example.shopservice.entity.checkout.Order;
import com.example.shopservice.entity.checkout.OrderItem;
import com.example.shopservice.repository.catalog.ProductRepository;
import com.example.shopservice.repository.catalog.UserRepository;
import com.example.shopservice.repository.checkout.OrderItemRepository;
import com.example.shopservice.repository.checkout.OrderRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ShopServiceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(config.SqlInitConfig.class)
public class OrderItemControllerIT {
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
    private Product product;
    private Order order;

    @BeforeEach
    void setup() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        
        User userObj = new User();
        userObj.setUsername("testuser");
        userObj.setEmail("testuser@example.com");
        user = userRepository.save(userObj);

        Product prodObj = new Product();
        prodObj.setName("TestProduct");
        prodObj.setPrice(new BigDecimal("25.99"));
        product = productRepository.save(prodObj);
        
        Order orderObj = new Order();
        orderObj.setUserId(user.getId());
        order = orderRepository.save(orderObj);
    }

    @Test
    void testCreateOrderItem() throws Exception {
        String itemJson = """
        {
          "productId":%d,
          "quantity":3
        }
        """.formatted(product.getId());

        mockMvc.perform(post("/orders/" + order.getId() + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(itemJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.productId").value(product.getId()));
    }

    @Test
    void testGetOrderItems() throws Exception {
        // First create an order item
        String itemJson = """
        {
          "productId":%d,
          "quantity":2
        }
        """.formatted(product.getId());

        mockMvc.perform(post("/orders/" + order.getId() + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(itemJson))
                .andExpect(status().isOk());

        // Then get the list of items
        mockMvc.perform(get("/orders/" + order.getId() + "/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].quantity").value(2));
    }

    @Test
    void testGetOrderItem() throws Exception {
        // First create an order item
        String itemJson = """
        {
          "productId":%d,
          "quantity":4
        }
        """.formatted(product.getId());

        String response = mockMvc.perform(post("/orders/" + order.getId() + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(itemJson))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        OrderItem orderItem = objectMapper.readValue(response, OrderItem.class);

        // Then get the specific item
        mockMvc.perform(get("/orders/" + order.getId() + "/items/" + orderItem.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderItem.getId()))
                .andExpect(jsonPath("$.quantity").value(4));
    }

    @Test
    void testUpdateOrderItem() throws Exception {
        // First create an order item
        String itemJson = """
        {
          "productId":%d,
          "quantity":1
        }
        """.formatted(product.getId());

        String response = mockMvc.perform(post("/orders/" + order.getId() + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(itemJson))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        OrderItem orderItem = objectMapper.readValue(response, OrderItem.class);

        // Then update the item
        String updateJson = """
        {
          "productId":%d,
          "quantity":5
        }
        """.formatted(product.getId());

        mockMvc.perform(put("/orders/" + order.getId() + "/items/" + orderItem.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(5));
    }

    @Test
    void testDeleteOrderItem() throws Exception {
        // First create an order item
        String itemJson = """
        {
          "productId":%d,
          "quantity":1
        }
        """.formatted(product.getId());

        String response = mockMvc.perform(post("/orders/" + order.getId() + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(itemJson))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        OrderItem orderItem = objectMapper.readValue(response, OrderItem.class);

        // Then delete the item
        mockMvc.perform(delete("/orders/" + order.getId() + "/items/" + orderItem.getId()))
                .andExpect(status().isNoContent());

        // Verify it's deleted
        mockMvc.perform(get("/orders/" + order.getId() + "/items/" + orderItem.getId()))
                .andExpect(status().isNotFound());
    }
}