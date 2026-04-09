package org.example.model;

public abstract class User extends Entity {
    private String username;
    private String password;
    private String email;

    public User(String id, String username, String password, String email) {
        super(id);
        this.username = username;
        this.password = password;
        this.email = email;
    }

    public String getUsername() {
        return username;
    }
}
