package com.ravan.SpringBootLab.service;

import com.ravan.SpringBootLab.dto.CreateUserRequest;
import com.ravan.SpringBootLab.dto.CreateUserResponse;
import com.ravan.SpringBootLab.dto.UpdateUserRequest;
import com.ravan.SpringBootLab.dto.UserResponse;
import com.ravan.SpringBootLab.exception.UserNotFoundException;
import com.ravan.SpringBootLab.model.User;
import com.ravan.SpringBootLab.repository.UserRepository;
import com.ravan.SpringBootLab.dto.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public CreateUserResponse createUser(CreateUserRequest request) {
        User user = new User(
                request.getName(),
                request.getSkill()
        );

        User savedUser = userRepository.save(user);

        return new CreateUserResponse(
                "User created by PostgreSQL",
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getSkill()
        );
    }

    public PageResponse<UserResponse> getAllUsers(Pageable pageable) {
        Page<User> userPage = userRepository.findAll(pageable);

        List<UserResponse> content = userPage.getContent()
                .stream()
                .map(user -> new UserResponse(
                        user.getId(),
                        user.getName(),
                        user.getSkill()
                ))
                .collect(Collectors.toList());

        return new PageResponse<>(
            content,
            userPage.getNumber(),
            userPage.getSize(),
            userPage.getTotalElements(),
            userPage.getTotalPages()
        );
    }

    public UserResponse getUserById(int id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getSkill()
        );
    }

    public UserResponse updateUserById(int id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        user.setName(request.getName());
        user.setSkill(request.getSkill());

        User savedUser = userRepository.save(user);

        return new UserResponse(
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getSkill()
        );
    }

    public void deleteUserById(int id) {
        if (!userRepository.existsById(id)) {
            throw new UserNotFoundException(id);
        }

        userRepository.deleteById(id);
    }
}