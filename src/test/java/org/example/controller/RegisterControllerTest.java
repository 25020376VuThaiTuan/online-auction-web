package org.example.controller;

import javafx.scene.control.ChoiceBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class RegisterControllerTest {

    private RegisterController controller;

    @BeforeAll
    static void initToolkit() {
        JavaFxTestSupport.startToolkit();
    }

    @BeforeEach
    void setup() throws Exception {

        controller = new RegisterController();

        setField(
                "accountRoleChoiceBox",
                new ChoiceBox<String>()
        );

        setField(
                "fullNameField",
                new TextField()
        );

        setField(
                "usernameField",
                new TextField()
        );

        setField(
                "emailField",
                new TextField()
        );

        setField(
                "passwordField",
                new PasswordField()
        );

        setField(
                "confirmPasswordField",
                new PasswordField()
        );
    }

    private void setField(
            String fieldName,
            Object value
    ) throws Exception {

        Field field =
                RegisterController.class
                        .getDeclaredField(fieldName);

        field.setAccessible(true);

        field.set(controller, value);
    }

    private Object getField(
            String fieldName
    ) throws Exception {

        Field field =
                RegisterController.class
                        .getDeclaredField(fieldName);

        field.setAccessible(true);

        return field.get(controller);
    }

    @Test
    void shouldInitializeChoiceBox()
            throws Exception {

        controller.initialize();

        @SuppressWarnings("unchecked")
        ChoiceBox<String> box =
                (ChoiceBox<String>) getField(
                        "accountRoleChoiceBox"
                );

        assertEquals(
                "BIDDER",
                box.getValue()
        );

        assertEquals(
                2,
                box.getItems().size()
        );
    }

    @Test
    void shouldTrimValue()
            throws Exception {

        Method method =
                RegisterController.class
                        .getDeclaredMethod(
                                "value",
                                String.class
                        );

        method.setAccessible(true);

        String result =
                (String) method.invoke(
                        controller,
                        "  hello  "
                );

        assertEquals(
                "hello",
                result
        );
    }

    @Test
    void shouldReturnEmptyForNull()
            throws Exception {

        Method method =
                RegisterController.class
                        .getDeclaredMethod(
                                "value",
                                String.class
                        );

        method.setAccessible(true);

        String result =
                (String) method.invoke(
                        controller,
                        new Object[]{null}
                );

        assertEquals(
                "",
                result
        );
    }

    @Test
    void shouldSetFieldsCorrectly()
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
    void shouldSetPasswordField()
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
}
