package com.ravan.SpringBootLab.security;

import com.ravan.SpringBootLab.exception.OrderNotFoundException;
import com.ravan.SpringBootLab.model.Order;
import com.ravan.SpringBootLab.model.User;
import com.ravan.SpringBootLab.repository.OrderRepository;
import com.ravan.SpringBootLab.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    public CurrentUserService(
            UserRepository userRepository,
            OrderRepository orderRepository
    ) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
    }

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        String email = authentication.getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authentication"));
    }

    public void requireUserIdOrAdmin(Integer userId) {
        User currentUser = getCurrentUser();

        if (isAdmin(currentUser)) {
            return;
        }

        if (!currentUser.getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }

    public void requireOrderOwnerOrAdmin(Integer orderId) {
        User currentUser = getCurrentUser();

        if (isAdmin(currentUser)) {
            return;
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!order.getUser().getId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }

    private boolean isAdmin(User user) {
        return "ADMIN".equals(user.getRole());
    }
}
