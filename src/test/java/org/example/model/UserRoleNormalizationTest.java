package org.example.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserRoleNormalizationTest {
    @Test
    void setRoleNormalizesWhitespaceAndCase() {
        Admin admin = new Admin("U-ADM-TEST", "admin_test", "secret", "admin@test.local");

        admin.setRole(" admin ");

        assertEquals("ADMIN", admin.getRole());
    }
}
