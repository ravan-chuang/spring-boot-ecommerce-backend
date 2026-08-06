package com.ravan.SpringBootLab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class DeadLetterActionRequest {

    @NotBlank
    @Size(max = 500)
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
