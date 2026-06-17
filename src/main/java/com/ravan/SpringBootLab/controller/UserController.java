package com.ravan.SpringBootLab.controller;

import com.ravan.SpringBootLab.dto.ApiResponse;
import com.ravan.SpringBootLab.dto.CreateUserRequest;
import com.ravan.SpringBootLab.dto.CreateUserResponse;
import com.ravan.SpringBootLab.dto.UpdateUserRequest;
import com.ravan.SpringBootLab.dto.UserResponse;
import com.ravan.SpringBootLab.service.UserService;
import com.ravan.SpringBootLab.dto.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@Tag(name = "User API", description = "User CRUD APIs")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Create user", description = "Create a new user")
    @PostMapping("/api/users")
    public ResponseEntity<ApiResponse<CreateUserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request
    ) {
        CreateUserResponse response = userService.createUser(request);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "User created successfully",
                        response
                )
        );
    }

    @Operation(summary = "Get users", description = "Get paginated users with sorting")
    @GetMapping("/api/users")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> getAllUsers(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(defaultValue = "id") String sortBy,
        @RequestParam(defaultValue = "asc") String direction
    ){
        Sort sort = direction.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);

        PageResponse<UserResponse> users = userService.getAllUsers(pageable);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Success",
                        users
                )
        );
    }

    @Operation(summary = "Get user by id", description = "Get a single user by id")
    @GetMapping("/api/users/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable int id) {
        UserResponse user = userService.getUserById(id);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Success",
                        user
                )
        );
    }

    @Operation(summary = "Update user", description = "Update user by id")
    @PutMapping("/api/users/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable int id,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        UserResponse updatedUser = userService.updateUserById(id, request);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "User updated successfully",
                        updatedUser
                )
        );
    }

    @Operation(summary = "Delete user", description = "Delete user by id")
    @DeleteMapping("/api/users/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable int id) {
        userService.deleteUserById(id);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "User deleted successfully",
                        null
                )
        );
    }
}