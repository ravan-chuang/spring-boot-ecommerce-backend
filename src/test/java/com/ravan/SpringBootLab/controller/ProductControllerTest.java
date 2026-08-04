package com.ravan.SpringBootLab.controller;

import com.ravan.SpringBootLab.dto.ApiResponse;
import com.ravan.SpringBootLab.dto.CreateProductRequest;
import com.ravan.SpringBootLab.dto.PageResponse;
import com.ravan.SpringBootLab.dto.ProductResponse;
import com.ravan.SpringBootLab.dto.UpdateProductRequest;
import com.ravan.SpringBootLab.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock
    private ProductService productService;

    private ProductController controller;
    private ProductResponse product;

    @BeforeEach
    void setUp() {
        controller = new ProductController(productService);
        product = new ProductResponse(
                1,
                "Keyboard",
                "Mechanical keyboard",
                new BigDecimal("1999.00"),
                10,
                0,
                LocalDateTime.of(2026, 8, 4, 10, 0),
                LocalDateTime.of(2026, 8, 4, 10, 0)
        );
    }

    @Test
    void createProductReturnsCreatedProduct() {
        CreateProductRequest request = mock(CreateProductRequest.class);
        when(productService.createProduct(request)).thenReturn(product);

        ResponseEntity<ApiResponse<ProductResponse>> response =
                controller.createProduct(request);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getStatus());
        assertEquals("Product created successfully", response.getBody().getMessage());
        assertSame(product, response.getBody().getData());
        verify(productService).createProduct(request);
    }

    @Test
    void getAllProductsUsesAscendingSortByDefaultDirection() {
        PageResponse<ProductResponse> pageResponse =
                new PageResponse<>(List.of(product), 0, 10, 1, 1);
        when(productService.getAllProducts(any(Pageable.class))).thenReturn(pageResponse);

        ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> response =
                controller.getAllProducts(0, 10, "name", "asc");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productService).getAllProducts(captor.capture());

        Pageable pageable = captor.getValue();
        Sort.Order order = pageable.getSort().getOrderFor("name");

        assertNotNull(order);
        assertTrue(order.isAscending());
        assertEquals(0, pageable.getPageNumber());
        assertEquals(10, pageable.getPageSize());
        assertNotNull(response.getBody());
        assertSame(pageResponse, response.getBody().getData());
    }

    @Test
    void getAllProductsUsesDescendingSortIgnoringCase() {
        PageResponse<ProductResponse> pageResponse =
                new PageResponse<>(List.of(product), 2, 5, 11, 3);
        when(productService.getAllProducts(any(Pageable.class))).thenReturn(pageResponse);

        controller.getAllProducts(2, 5, "price", "DeSc");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productService).getAllProducts(captor.capture());

        Pageable pageable = captor.getValue();
        Sort.Order order = pageable.getSort().getOrderFor("price");

        assertNotNull(order);
        assertTrue(order.isDescending());
        assertEquals(2, pageable.getPageNumber());
        assertEquals(5, pageable.getPageSize());
    }

    @Test
    void getProductReturnsServiceResult() {
        when(productService.getProductById(1)).thenReturn(product);

        ResponseEntity<ApiResponse<ProductResponse>> response =
                controller.getProduct(1);

        assertNotNull(response.getBody());
        assertEquals("Success", response.getBody().getMessage());
        assertSame(product, response.getBody().getData());
        verify(productService).getProductById(1);
    }

    @Test
    void updateProductReturnsUpdatedProduct() {
        UpdateProductRequest request = mock(UpdateProductRequest.class);
        when(productService.updateProduct(1, request)).thenReturn(product);

        ResponseEntity<ApiResponse<ProductResponse>> response =
                controller.updateProduct(1, request);

        assertNotNull(response.getBody());
        assertEquals("Product updated successfully", response.getBody().getMessage());
        assertSame(product, response.getBody().getData());
        verify(productService).updateProduct(1, request);
    }

    @Test
    void deleteProductReturnsSuccessWithNullData() {
        ResponseEntity<ApiResponse<Void>> response =
                controller.deleteProduct(1);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Product deleted successfully", response.getBody().getMessage());
        assertNull(response.getBody().getData());
        verify(productService).deleteProduct(1);
        verifyNoMoreInteractions(productService);
    }
}
