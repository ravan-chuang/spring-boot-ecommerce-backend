package com.ravan.SpringBootLab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateUserRequest {

    @NotBlank(message = "name cannot be blank")
    @Size(max = 50, message = "name cannot exceed 50 characters")
    private String name;

    @NotBlank(message = "skill cannot be blank")
    @Size(max = 100, message = "skill cannot exceed 100 characters")
    private String skill;

    public String getName() {
        return name;
    }

    public String getSkill() {
        return skill;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setSkill(String skill) {
        this.skill = skill;
    }
}