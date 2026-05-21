package org.example.model;

import java.io.Serial;
import java.io.Serializable;

public abstract class Entity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    protected String id;

    public Entity(String id) {
        this.id = id;
    }
    public String getId() {
        return id;
    }
}