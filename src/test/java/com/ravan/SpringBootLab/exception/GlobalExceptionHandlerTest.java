package com.ravan.SpringBootLab.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void validationExceptionReturnsAllFieldErrors() {
        MethodArgumentNotValidException exception =
                mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);

        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("request", "email", "must be a well-formed email address"),
                new FieldError("request", "password", "must not be blank")
        ));

        ResponseEntity<Map<String, Object>> response =
                handler.handleValidationException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .containsEntry("status", 400)
                .containsEntry("message", "Validation failed");

        assertThat(response.getBody().get("errors"))
                .isEqualTo(Map.of(
                        "email", "must be a well-formed email address",
                        "password", "must not be blank"
                ));
    }

    @Test
    void notFoundExceptionsReturn404AndOriginalMessage() {
        assertExceptionResponse(
                UserNotFoundException.class,
                "User not found",
                handler::handleUserNotFoundException,
                HttpStatus.NOT_FOUND
        );
        assertExceptionResponse(
                ProductNotFoundException.class,
                "Product not found",
                handler::handleProductNotFoundException,
                HttpStatus.NOT_FOUND
        );
        assertExceptionResponse(
                CartItemNotFoundException.class,
                "Cart item not found",
                handler::handleCartItemNotFoundException,
                HttpStatus.NOT_FOUND
        );
        assertExceptionResponse(
                OrderNotFoundException.class,
                "Order not found",
                handler::handleOrderNotFoundException,
                HttpStatus.NOT_FOUND
        );
        assertExceptionResponse(
                PaymentNotFoundException.class,
                "Payment not found",
                handler::handlePaymentNotFoundException,
                HttpStatus.NOT_FOUND
        );
    }

    @Test
    void badRequestExceptionsReturn400AndOriginalMessage() {
        assertExceptionResponse(
                InsufficientStockException.class,
                "Insufficient stock",
                handler::handleInsufficientStockException,
                HttpStatus.BAD_REQUEST
        );
        assertExceptionResponse(
                EmptyCartException.class,
                "Cart is empty",
                handler::handleEmptyCartException,
                HttpStatus.BAD_REQUEST
        );
        assertExceptionResponse(
                OrderAlreadyPaidException.class,
                "Order already paid",
                handler::handleOrderAlreadyPaidException,
                HttpStatus.BAD_REQUEST
        );
        assertExceptionResponse(
                InvalidOrderStatusException.class,
                "Invalid order status",
                handler::handleInvalidOrderStatusException,
                HttpStatus.BAD_REQUEST
        );
        assertExceptionResponse(
                IdempotencyKeyRequiredException.class,
                "Idempotency-Key header is required",
                handler::handleIdempotencyKeyRequiredException,
                HttpStatus.BAD_REQUEST
        );
        assertExceptionResponse(
                InvalidIdempotencyKeyException.class,
                "Idempotency-Key cannot exceed 255 characters",
                handler::handleInvalidIdempotencyKeyException,
                HttpStatus.BAD_REQUEST
        );
    }

    @Test
    void concurrencyConflictReturns409AndOriginalMessage() {
        assertExceptionResponse(
                ConcurrencyConflictException.class,
                "Concurrent update conflict",
                handler::handleConcurrencyConflictException,
                HttpStatus.CONFLICT
        );
        assertExceptionResponse(
                IdempotencyConflictException.class,
                "Idempotency-Key was already used with a different request payload",
                handler::handleIdempotencyConflictException,
                HttpStatus.CONFLICT
        );
    }

    @Test
    void optimisticLockFailureReturns409WithRetryMessage() {
        ObjectOptimisticLockingFailureException exception =
                mock(ObjectOptimisticLockingFailureException.class);

        ResponseEntity<Map<String, Object>> response =
                handler.handleOptimisticLockException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody())
                .containsEntry("status", 409)
                .containsEntry(
                        "message",
                        "Product stock was updated by another request. Please retry."
                );
    }

    private <T extends RuntimeException> void assertExceptionResponse(
            Class<T> exceptionType,
            String message,
            Function<T, ResponseEntity<Map<String, Object>>> handlerFunction,
            HttpStatus expectedStatus
    ) {
        T exception = mock(exceptionType);
        when(exception.getMessage()).thenReturn(message);

        ResponseEntity<Map<String, Object>> response =
                handlerFunction.apply(exception);

        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getBody())
                .containsEntry("status", expectedStatus.value())
                .containsEntry("message", message);
    }
}
