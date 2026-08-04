package com.ravan.SpringBootLab.service;

import com.ravan.SpringBootLab.dto.CreateProductRequest;
import com.ravan.SpringBootLab.dto.PageResponse;
import com.ravan.SpringBootLab.dto.ProductResponse;
import com.ravan.SpringBootLab.dto.UpdateProductRequest;
import com.ravan.SpringBootLab.exception.ProductNotFoundException;
import com.ravan.SpringBootLab.model.Product;
import com.ravan.SpringBootLab.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    private ProductService productService;
    private Product product;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository);

        product = new Product(
                "Keyboard",
                "Mechanical keyboard",
                new BigDecimal("1999.00"),
                10
        );
        product.setId(1);
        product.setVersion(2);
        product.setCreatedAt(LocalDateTime.of(2026, 8, 1, 10, 0));
        product.setUpdatedAt(LocalDateTime.of(2026, 8, 2, 11, 0));
    }

    @Test
    void createProductPersistsMappedEntityAndReturnsResponse() {
        CreateProductRequest request = mock(CreateProductRequest.class);
        when(request.getName()).thenReturn("Keyboard");
        when(request.getDescription()).thenReturn("Mechanical keyboard");
        when(request.getPrice()).thenReturn(new BigDecimal("1999.00"));
        when(request.getStock()).thenReturn(10);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            saved.setId(1);
            saved.setVersion(0);
            return saved;
        });

        ProductResponse response = productService.createProduct(request);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());

        Product saved = captor.getValue();
        assertEquals("Keyboard", saved.getName());
        assertEquals("Mechanical keyboard", saved.getDescription());
        assertEquals(new BigDecimal("1999.00"), saved.getPrice());
        assertEquals(10, saved.getStock());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());

        assertEquals(1, response.getId());
        assertEquals("Keyboard", response.getName());
        assertEquals(0, response.getVersion());
    }

    @Test
    void getAllProductsMapsPageMetadataAndContent() {
        Pageable pageable = PageRequest.of(1, 2);
        Product second = new Product("Mouse", "Wireless mouse", new BigDecimal("899.00"), 5);
        second.setId(2);
        second.setVersion(1);

        when(productRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(product, second), pageable, 5));

        PageResponse<ProductResponse> response = productService.getAllProducts(pageable);

        assertEquals(2, response.getContent().size());
        assertEquals(1, response.getPage());
        assertEquals(2, response.getSize());
        assertEquals(5, response.getTotalElements());
        assertEquals(3, response.getTotalPages());
        assertEquals("Keyboard", response.getContent().get(0).getName());
        assertEquals("Mouse", response.getContent().get(1).getName());
        verify(productRepository).findAll(pageable);
    }

    @Test
    void getProductByIdReturnsMappedProduct() {
        when(productRepository.findById(1)).thenReturn(Optional.of(product));

        ProductResponse response = productService.getProductById(1);

        assertEquals(1, response.getId());
        assertEquals("Keyboard", response.getName());
        assertEquals(new BigDecimal("1999.00"), response.getPrice());
        assertEquals(10, response.getStock());
        assertEquals(2, response.getVersion());
        verify(productRepository).findById(1);
    }

    @Test
    void getProductByIdThrowsWhenMissing() {
        when(productRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class,
                () -> productService.getProductById(99));

        verify(productRepository).findById(99);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void updateProductMutatesAndSavesExistingEntity() {
        UpdateProductRequest request = mock(UpdateProductRequest.class);
        when(request.getName()).thenReturn("Updated keyboard");
        when(request.getDescription()).thenReturn("Updated description");
        when(request.getPrice()).thenReturn(new BigDecimal("2299.00"));
        when(request.getStock()).thenReturn(8);
        when(productRepository.findById(1)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        LocalDateTime previousUpdatedAt = product.getUpdatedAt();
        ProductResponse response = productService.updateProduct(1, request);

        assertEquals("Updated keyboard", product.getName());
        assertEquals("Updated description", product.getDescription());
        assertEquals(new BigDecimal("2299.00"), product.getPrice());
        assertEquals(8, product.getStock());
        assertTrue(product.getUpdatedAt().isAfter(previousUpdatedAt));
        assertEquals("Updated keyboard", response.getName());

        verify(productRepository).findById(1);
        verify(productRepository).save(product);
    }

    @Test
    void updateProductThrowsWhenMissingAndDoesNotSave() {
        UpdateProductRequest request = mock(UpdateProductRequest.class);
        when(productRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class,
                () -> productService.updateProduct(99, request));

        verify(productRepository).findById(99);
        verify(productRepository, never()).save(any());
    }

    @Test
    void deleteProductDeletesExistingProduct() {
        when(productRepository.existsById(1)).thenReturn(true);

        productService.deleteProduct(1);

        verify(productRepository).existsById(1);
        verify(productRepository).deleteById(1);
    }

    @Test
    void deleteProductThrowsWhenMissingAndDoesNotDelete() {
        when(productRepository.existsById(99)).thenReturn(false);

        assertThrows(ProductNotFoundException.class,
                () -> productService.deleteProduct(99));

        verify(productRepository).existsById(99);
        verify(productRepository, never()).deleteById(anyInt());
    }
}
