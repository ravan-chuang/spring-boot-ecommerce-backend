package com.ravan.SpringBootLab.dto;

public class AuthResponse {

    private final String token;
    private final String refreshToken;
    private final String tokenType;
    private final Integer userId;
    private final String name;
    private final String email;
    private final String role;

    public AuthResponse(
            String token,
            String refreshToken,
            Integer userId,
            String name,
            String email,
            String role
    ) {
        this.token = token;
        this.refreshToken = refreshToken;
        this.tokenType = "Bearer";
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.role = role;
    }

    public String getToken() {
        return token;
    }

    public String getAccessToken() {
        return token;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public Integer getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }
}
