package com.ravan.SpringBootLab.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateProductSuccessfully() throws Exception {
        String requestJson = """
                {
                  "name": "Test Product",
                  "description": "Product created by integration test",
                  "price": 1000.00,
                  "stock": 10
                }
                """;

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("Test Product")))
                .andExpect(jsonPath("$.data.description", is("Product created by integration test")))
                .andExpect(jsonPath("$.data.price", is(1000.00)))
                .andExpect(jsonPath("$.data.stock", is(10)));
    }

    @Test
    void shouldGetProductByIdSuccessfully() throws Exception {
        Integer productId = createProductAndReturnId();

        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(productId)))
                .andExpect(jsonPath("$.data.name", is("Test Product")));
    }

    @Test
    void shouldUpdateProductSuccessfully() throws Exception {
        Integer productId = createProductAndReturnId();

        String requestJson = """
                {
                  "name": "Updated Product",
                  "description": "Updated by integration test",
                  "price": 2000.00,
                  "stock": 20
                }
                """;

        mockMvc.perform(put("/api/products/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("Updated Product")))
                .andExpect(jsonPath("$.data.description", is("Updated by integration test")))
                .andExpect(jsonPath("$.data.price", is(2000.00)))
                .andExpect(jsonPath("$.data.stock", is(20)));
    }

    @Test
    void shouldDeleteProductSuccessfully() throws Exception {
        Integer productId = createProductAndReturnId();

        mockMvc.perform(delete("/api/products/{id}", productId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isNotFound());
    }

    private Integer createProductAndReturnId() throws Exception {
        String requestJson = """
                {
                  "name": "Test Product",
                  "description": "Product created by integration test",
                  "price": 1000.00,
                  "stock": 10
                }
                """;

        String response = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode jsonNode = objectMapper.readTree(response);
        return jsonNode.get("data").get("id").asInt();
    }
}
