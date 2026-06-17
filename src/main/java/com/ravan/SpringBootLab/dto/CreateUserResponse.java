package com.ravan.SpringBootLab.dto;

public class CreateUserResponse {

    private String message;
    private Integer id;
    private String name;
    private String skill;

    public CreateUserResponse(String message, Integer id, String name, String skill) {
        this.message = message;
        this.id = id;
        this.name = name;
        this.skill = skill;
    }

    public String getMessage() {
        return message;
    }

    public Integer getId(){
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSkill() {
        return skill;
    }
}