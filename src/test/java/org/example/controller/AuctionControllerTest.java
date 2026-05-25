package org.example.controller;

import javafx.scene.control.*;
import org.example.model.Bid;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class AuctionControllerTest {

    private AuctionController controller;

    @BeforeAll
    static void initToolkit() {
        JavaFxTestSupport.startToolkit();
    }

    @BeforeEach
    void setup() throws Exception {

        controller = new AuctionController();

        setField("userLabel", new Label());
        setField("itemNameLabel", new Label());
        setField("descriptionLabel", new Label());
        setField("statusLabel", new Label());
        setField("currentPriceLabel", new Label());
        setField("minimumBidLabel", new Label());
        setField("endTimeLabel", new Label());
        setField("timeRemainingLabel", new Label());
        setField("bidEntryTimeRemainingLabel", new Label());
        setField("depositLabel", new Label());
        setField("settlementLabel", new Label());
        setField("currentWinnerLabel", new Label());

        setField("bidNotificationList", new ListView<String>());
        setField("bidTable", new TableView<Bid>());

        setField("bidderColumn", new TableColumn<>());
        setField("amountColumn", new TableColumn<>());
        setField("timeColumn", new TableColumn<>());

        setField("bidAmountCombo", new ComboBox<String>());

        setField("placeBidButton", new Button());
        setField("confirmEntryButton", new Button());
        setField("admitResultButton", new Button());
        setField("confirmReceivedButton", new Button());
    }

    private void setField(
            String fieldName,
            Object value
    ) throws Exception {

        Field field =
                AuctionController.class
                        .getDeclaredField(fieldName);

        field.setAccessible(true);

        field.set(controller, value);
    }

    @Test
    void shouldFormatAmountInput()
            throws Exception {

        Method method =
                AuctionController.class
                        .getDeclaredMethod(
                                "formatAmountInput",
                                double.class
                        );

        method.setAccessible(true);

        String result =
                (String) method.invoke(
                        controller,
                        1500.0
                );

        assertEquals("1500.00", result);
    }

    @Test
    void shouldParseAmount()
            throws Exception {

        Method method =
                AuctionController.class
                        .getDeclaredMethod(
                                "parseAmount",
                                String.class
                        );

        method.setAccessible(true);

        double result =
                (double) method.invoke(
                        controller,
                        "$1,500"
                );

        assertEquals(1500.0, result);
    }

    @Test
    void shouldFormatBidNotificationTime()
            throws Exception {

        Method method =
                AuctionController.class
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

    @Test
    void shouldDisableSettlementButtons()
            throws Exception {

        Method method =
                AuctionController.class
                        .getDeclaredMethod(
                                "setBuyerSettlementButtonsDisabled",
                                boolean.class,
                                boolean.class
                        );

        method.setAccessible(true);

        method.invoke(
                controller,
                true,
                true
        );

        Button admitButton =
                (Button) getField(
                        "admitResultButton"
                );

        Button confirmButton =
                (Button) getField(
                        "confirmReceivedButton"
                );

        assertTrue(admitButton.isDisabled());

        assertTrue(confirmButton.isDisabled());
    }

    @Test
    void shouldAddBidNotification()
            throws Exception {

        Method method =
                AuctionController.class
                        .getDeclaredMethod(
                                "addBidActivityNotification",
                                String.class,
                                double.class,
                                LocalDateTime.class,
                                String.class
                        );

        method.setAccessible(true);

        method.invoke(
                controller,
                "Alice",
                2000.0,
                LocalDateTime.now(),
                "accepted"
        );

        @SuppressWarnings("unchecked")
        ListView<String> list =
                (ListView<String>) getField(
                        "bidNotificationList"
                );

        assertFalse(
                list.getItems().isEmpty()
        );
    }

    private Object getField(
            String fieldName
    ) throws Exception {

        Field field =
                AuctionController.class
                        .getDeclaredField(fieldName);

        field.setAccessible(true);

        return field.get(controller);
    }
}
