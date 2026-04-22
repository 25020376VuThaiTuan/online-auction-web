package org.example.model;

public abstract class User extends Entity {
    private String username;
    private String password;
    private String email;
    protected String role;

    public void setRole(String role) {
        this.role = role;
    }

    public String getRole(){
        return role;
    }
    @Override
    public String getId(){
        return id;
    }

    public User(String id, String username, String password, String email) {
        super(id);
        this.username = username;
        this.password = password;
        this.email = email;
    }

    public String getUsername() {
        return username;
    }
    public String getPassword() {
        return password;
    }

    public String getEmail() {
        return email;
    }

    public abstract void displayRole();
}
