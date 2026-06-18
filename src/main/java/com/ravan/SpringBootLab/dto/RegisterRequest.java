package com.ravan.SpringBootLab.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank(message = "name cannot be blank")
    @Size(max = 50, message = "name cannot exceed 50 characters")
    private String name;

    @Email(message = "email format is invalid")
    @NotBlank(message = "email cannot be blank")
    private String email;

    @NotBlank(message = "password cannot be blank")
    @Size(min = 8, max = 100, message = "password length must be between 8 and 100 characters")
    private String password;

    @NotBlank(message = "skill cannot be blank")
    @Size(max = 100, message = "skill cannot exceed 100 characters")
    private String skill;

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getSkill() {
        return skill;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setSkill(String skill) {
        this.skill = skill;
    }
}
