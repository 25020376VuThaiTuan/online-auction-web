package org.example.controller;

import javafx.application.Platform;
import javafx.scene.control.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DashboardControllerTest {

    private DashboardController controller;

    @BeforeAll
    static void initToolkit() {

        Platform.startup(() -> {
        });
    }

    @BeforeEach
    void setup() throws Exception {

        controller = new DashboardController();

        setField("signedInUserLabel", new Label());
        setField("roleLabel", new Label());
        setField("emailLabel", new Label());

        setField("balanceLabel", new Label());
        setField("lockedBalanceLabel", new Label());
        setField("availableBalanceLabel", new Label());

        setField("walletBalanceLabel", new Label());
        setField("walletLockedLabel", new Label());
        setField("walletAvailableLabel", new Label());

        setField("walletPinStatusLabel", new Label());

        setField("notificationList", new ListView<String>());

        setField("walletPinField", new PasswordField());
        setField("newWalletPinField", new PasswordField());

        setField("recoveryCodeField", new TextField());

        setField("rememberWalletPinCheckBox", new CheckBox());

        setField("fullNameField", new TextField());
        setField("phoneField", new TextField());

        setField("addressArea", new TextArea());

        setField("avatarUrlField", new TextField());

        setField("testConnectionButton", new Button());

        setField("setWalletPinButton", new Button());
    }

    private void setField(
            String fieldName,
            Object value
    ) throws Exception {

        Field field =
                DashboardController.class
                        .getDeclaredField(fieldName);

        field.setAccessible(true);

        field.set(controller, value);
    }

    private Object getField(
            String fieldName
    ) throws Exception {

        Field field =
                DashboardController.class
                        .getDeclaredField(fieldName);

        field.setAccessible(true);

        return field.get(controller);
    }

    @Test
    void shouldSetFullNameField()
            throws Exception {

        TextField field =
                (TextField) getField(
                        "fullNameField"
                );

        field.setText("Alice");

        assertEquals(
                "Alice",
                field.getText()
        );
    }

    @Test
    void shouldSetPhoneField()
            throws Exception {

        TextField field =
                (TextField) getField(
                        "phoneField"
                );

        field.setText("0123456789");

        assertEquals(
                "0123456789",
                field.getText()
        );
    }

    @Test
    void shouldSetAddressArea()
            throws Exception {

        TextArea area =
                (TextArea) getField(
                        "addressArea"
                );

        area.setText("Ha Noi");

        assertEquals(
                "Ha Noi",
                area.getText()
        );
    }

    @Test
    void shouldSetWalletPin()
            throws Exception {

        PasswordField field =
                (PasswordField) getField(
                        "walletPinField"
                );

        field.setText("123456");

        assertEquals(
                "123456",
                field.getText()
        );
    }

    @Test
    void shouldSetRecoveryCode()
            throws Exception {

        TextField field =
                (TextField) getField(
                        "recoveryCodeField"
                );

        field.setText("RECOVERY");

        assertEquals(
                "RECOVERY",
                field.getText()
        );
    }

    @Test
    void shouldSelectRememberWalletPin()
            throws Exception {

        CheckBox checkBox =
                (CheckBox) getField(
                        "rememberWalletPinCheckBox"
                );

        checkBox.setSelected(true);

        assertTrue(
                checkBox.isSelected()
        );
    }

    @Test
    void shouldSetSignedInUserLabel()
            throws Exception {

        Label label =
                (Label) getField(
                        "signedInUserLabel"
                );

        label.setText("Signed in");

        assertEquals(
                "Signed in",
                label.getText()
        );
    }

    @Test
    void shouldInvokeDateFormatter()
            throws Exception {

        Method method =
                DashboardController.class
                        .getDeclaredMethod(
                                "formatBidNotificationTime",
                                LocalDateTime.class
                        );

        method.setAccessible(true);

        String result =
                (String) method.invoke(
                        controller,
                        LocalDateTime.now()
                );

        assertNotNull(result);
    }
}