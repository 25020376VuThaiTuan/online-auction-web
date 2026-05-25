package org.example.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class UtilityStressTest {

    @Test
    void normalizeUsername_ShouldHandleConcurrentRequests() throws Exception {

        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {

            executor.submit(() -> {
                try {

                    String result = AccountInputValidator.normalizeUsername(
                            "  USER_NAME  "
                    );

                    assertEquals("user_name", result);

                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);

        executor.shutdown();

        assertTrue(completed);
    }

    @Test
    void normalizeIdentityLabel_ShouldHandleConcurrentRequests() throws Exception {

        int threadCount = 200;

        ExecutorService executor = Executors.newFixedThreadPool(20);

        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {

            executor.submit(() -> {
                try {

                    String result =
                            AccountInputValidator.normalizeIdentityLabel(
                                    " John-Doe_123 "
                            );

                    assertEquals("johndoe123", result);

                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);

        executor.shutdown();

        assertTrue(completed);
    }

    @Test
    void validateRegistration_ShouldHandleConcurrentRequests() throws Exception {

        int threadCount = 100;

        ExecutorService executor = Executors.newFixedThreadPool(15);

        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {

            int finalI = i;

            executor.submit(() -> {

                try {

                    AccountInputValidator.RegistrationInput result =
                            AccountInputValidator.validateRegistration(
                                    "user" + finalI,
                                    "password123",
                                    "user" + finalI + "@gmail.com",
                                    "User " + finalI
                            );

                    assertEquals("user" + finalI, result.username());

                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(15, TimeUnit.SECONDS);

        executor.shutdown();

        assertTrue(completed);
    }

    @Test
    void validateRegistration_ShouldRejectInvalidInputsUnderStress() throws Exception {

        int threadCount = 50;

        ExecutorService executor = Executors.newFixedThreadPool(10);

        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {

            executor.submit(() -> {

                try {

                    assertThrows(
                            IllegalArgumentException.class,
                            () -> AccountInputValidator.validateRegistration(
                                    "",
                                    "123",
                                    "invalid-email",
                                    ""
                            )
                    );

                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);

        executor.shutdown();

        assertTrue(completed);
    }
}