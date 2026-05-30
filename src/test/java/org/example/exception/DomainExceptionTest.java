package org.example.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DomainExceptionTest {
    @Test
    void invalidPasswordValidatorUsesSharedPasswordPolicy() {
        assertDoesNotThrow(() -> InvalidPasswordException.checkValid("password123"));
    }

    @Test
    void invalidPasswordValidatorRejectsMissingRequirements() {
        assertThrows(InvalidPasswordException.class, () -> InvalidPasswordException.checkValid(null));
        assertThrows(InvalidPasswordException.class, () -> InvalidPasswordException.checkValid("123"));
        assertThrows(InvalidPasswordException.class, () -> InvalidPasswordException.checkValid(" password123 "));
        assertThrows(InvalidPasswordException.class, () -> InvalidPasswordException.checkValid("a".repeat(73)));
    }

    @Test
    void simpleDomainExceptionsPreserveMessage() {
        assertEquals("bid too low", new InvalidBidException("bid too low").getMessage());
        assertEquals("balance too low", new InsufficientBalanceException("balance too low").getMessage());
        assertEquals("missing user", new UserNotFound("missing user").getMessage());
    }
}
