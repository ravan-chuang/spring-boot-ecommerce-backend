package com.ravan.SpringBootLab.dto;

public class UserResponse {

    private Integer id;
    private String name;
    private String skill;

    public UserResponse(Integer id, String name, String skill) {
        this.id = id;
        this.name = name;
        this.skill = skill;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSkill() {
        return skill;
    }
}