package org.example.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiResourceNotFoundExceptionTest {
    @Test
    void preservesMessageForApiHandlers() {
        assertEquals("missing auction", new ApiResourceNotFoundException("missing auction").getMessage());
    }
}
