package com.ravan.SpringBootLab.controller;

import com.ravan.SpringBootLab.dto.ApiResponse;
import com.ravan.SpringBootLab.dto.CreateProductRequest;
import com.ravan.SpringBootLab.dto.PageResponse;
import com.ravan.SpringBootLab.dto.ProductResponse;
import com.ravan.SpringBootLab.dto.UpdateProductRequest;
import com.ravan.SpringBootLab.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Product API", description = "Product CRUD APIs")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "Create product", description = "Create a new product")
    @PostMapping("/api/products")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody CreateProductRequest request
    ) {
        ProductResponse product = productService.createProduct(request);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Product created successfully",
                        product
                )
        );
    }

    @Operation(summary = "Get products", description = "Get paginated products with sorting")
    @GetMapping("/api/products")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        Sort sort = direction.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        PageResponse<ProductResponse> products = productService.getAllProducts(pageable);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Success",
                        products
                )
        );
    }

    @Operation(summary = "Get product by id", description = "Get a single product by id")
    @GetMapping("/api/products/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable Integer id) {
        ProductResponse product = productService.getProductById(id);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Success",
                        product
                )
        );
    }

    @Operation(summary = "Update product", description = "Update product by id")
    @PutMapping("/api/products/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        ProductResponse product = productService.updateProduct(id, request);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Product updated successfully",
                        product
                )
        );
    }

    @Operation(summary = "Delete product", description = "Delete product by id")
    @DeleteMapping("/api/products/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Integer id) {
        productService.deleteProduct(id);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Product deleted successfully",
                        null
                )
        );
    }
}