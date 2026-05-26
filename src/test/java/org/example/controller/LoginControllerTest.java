package org.example.controller;

import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class LoginControllerTest {

    private LoginController controller;

    @BeforeAll
    static void initToolkit() {
        JavaFxTestSupport.startToolkit();
    }

    @BeforeEach
    void setup() throws Exception {

        controller = new LoginController();

        setField(
                "usernameField",
                new TextField()
        );

        setField(
                "passwordField",
                new PasswordField()
        );

        setField(
                "hintLabel",
                new Label()
        );
    }

    private void setField(
            String fieldName,
            Object value
    ) throws Exception {

        Field field =
                LoginController.class
                        .getDeclaredField(fieldName);

        field.setAccessible(true);

        field.set(controller, value);
    }

    private Object getField(
            String fieldName
    ) throws Exception {

        Field field =
                LoginController.class
                        .getDeclaredField(fieldName);

        field.setAccessible(true);

        return field.get(controller);
    }

    @Test
    void shouldInitializeHintLabel()
            throws Exception {

        controller.initialize();

        Label label =
                (Label) getField(
                        "hintLabel"
                );

        assertNotNull(label.getText());
    }

    @Test
    void shouldSetUsername()
            throws Exception {

        TextField username =
                (TextField) getField(
                        "usernameField"
                );

        username.setText("admin");

        assertEquals(
                "admin",
                username.getText()
        );
    }

    @Test
    void shouldSetPassword()
            throws Exception {

        PasswordField password =
                (PasswordField) getField(
                        "passwordField"
                );

        password.setText("123456");

        assertEquals(
                "123456",
                password.getText()
        );
    }

    @Test
    void shouldReturnFailureMessage()
            throws Exception {

        Method method =
                LoginController.class
                        .getDeclaredMethod(
                                "failureMessage",
                                Throwable.class
                        );

        method.setAccessible(true);

        String result =
                (String) method.invoke(
                        controller,
                        new RuntimeException("error")
                );

        assertEquals(
                "error",
                result
        );
    }

    @Test
    void shouldReturnDefaultFailureMessage()
            throws Exception {

        Method method =
                LoginController.class
                        .getDeclaredMethod(
                                "failureMessage",
                                Throwable.class
                        );

        method.setAccessible(true);

        String result =
                (String) method.invoke(
                        controller,
                        new RuntimeException()
                );

        assertEquals(
                "Dashboard data could not be loaded.",
                result
        );
    }
}
