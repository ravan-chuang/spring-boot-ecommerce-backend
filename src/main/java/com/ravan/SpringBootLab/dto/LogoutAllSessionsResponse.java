package com.ravan.SpringBootLab.dto;

public record LogoutAllSessionsResponse(
        int revokedSessionCount
) {
}
