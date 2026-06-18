package com.ravan.SpringBootLab.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;

    @Column(unique = true)
    private String email;

    private String skill;

    @Column(name = "password_hash")
    private String passwordHash;

    private String role;

    public User() {
    }

    public User(String name, String skill) {
        this.name = name;
        this.skill = skill;
        this.role = "USER";
    }

    public User(String name, String email, String skill, String passwordHash, String role) {
        this.name = name;
        this.email = email;
        this.skill = skill;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getSkill() {
        return skill;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRole() {
        return role;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setSkill(String skill) {
        this.skill = skill;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
