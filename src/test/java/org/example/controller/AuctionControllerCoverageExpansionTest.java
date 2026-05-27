package org.example.controller;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.example.auction.AuctionDepositResult;
import org.example.auction.AuctionSessionRegistry;
import org.example.auction.BidValidationResult;
import org.example.client.AuctionApiClient;
import org.example.model.ApprovalStatus;
import org.example.model.Bid;
import org.example.model.Bidder;
import org.example.model.Item;
import org.example.model.Seller;
import org.example.service.AuthenticationService;
import org.example.service.MarketplaceDashboardService;
import org.example.state.ApplicationSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionControllerCoverageExpansionTest {
    private static final String PIN = "2468";

    private final ApplicationSession session = ApplicationSession.getInstance();
    private final AuthenticationService authenticationService = AuthenticationService.getInstance();
    private final MarketplaceDashboardService dashboardService = MarketplaceDashboardService.getInstance();

    @BeforeAll
    static void initToolkit() {
        JavaFxTestSupport.startToolkit();
    }

    @AfterEach
    void tearDown() {
        session.logout();
        AuctionSessionRegistry.getInstance().clear();
    }

    @Test
    void localSnapshotRenderingObserverAndTrustedWalletBranchesUseRealAuctionState() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Bidder bidder = bidder("acovb" + suffix, 700.0);
        Seller seller = seller("acovs" + suffix);
        dashboardService.setWalletPin(bidder, PIN);
        dashboardService.setWalletPin(seller, PIN);

        Item item = dashboardService.addSellerItem(
                seller,
                "electronics",
                "Auction Controller Camera " + suffix,
                "Controller coverage item",
                100.0,
                LocalDateTime.now().minusMinutes(2),
                LocalDateTime.now().plusMinutes(20),
                "Brand",
                12
        );
        dashboardService.updateItemApproval(item.getId(), ApprovalStatus.APPROVED);
        assertTrue(dashboardService.startAuction(seller, item.getId()));
        AuctionDepositResult deposit = dashboardService.confirmAuctionEntry(item.getId(), bidder, PIN);
        assertTrue(deposit.accepted(), deposit.message());
        BidValidationResult bid = dashboardService.placeBidWithDeposit(item.getId(), bidder, 130.0, PIN);
        assertTrue(bid.accepted(), bid.message());

        session.login(bidder);
        AuctionController controller = auctionController();
        setField(controller, "selectedAuctionId", item.getId());

        Object snapshot = invoke(controller, "loadAuctionViewSnapshot");
        assertEquals(false, invoke(snapshot, "missingAuction"));
        assertEquals(item.getItemName(), invoke(snapshot, "itemName"));
        assertEquals(true, invoke(snapshot, "depositConfirmed"));
        assertEquals(true, invoke(snapshot, "canBid"));

        invoke(controller, "applyAuctionViewSnapshot", snapshot);
        assertEquals(item.getItemName(), field(controller, "itemNameLabel", Label.class).getText());
        assertEquals("$130.00", field(controller, "currentPriceLabel", Label.class).getText());
        assertFalse(field(controller, "placeBidButton", Button.class).isDisabled());
        assertTrue(field(controller, "confirmEntryButton", Button.class).isDisabled());
        assertFalse(field(controller, "bidTable", TableView.class).getItems().isEmpty());
        assertTrue(field(controller, "currentWinnerLabel", Label.class).getText().contains(bidder.getFullName()));

        assertEquals(bidder.getFullName(), invoke(controller, "displayBidderName", bidder.getId()));
        assertEquals("Unknown bidder", invoke(controller, "displayBidderName", " "));
        assertEquals("missing-bidder", invoke(controller, "displayBidderName", "missing-bidder"));

        session.trustWalletAuthorization(bidder.getId(), "wa_controller_token", Duration.ofMinutes(5));
        assertEquals("wa_controller_token", invoke(controller, "requestWalletPin", "Trusted Wallet"));

        invoke(controller, "refreshViewAsync", false);
        setField(controller, "lastRefreshFailureMessage", "same");
        invoke(controller, "handleRefreshFailure", "same", false);

        JavaFxTestSupport.runAndWait(() -> {
            try {
                invoke(controller, "startRefreshLoop");
                invoke(controller, "stopRefreshLoop");
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
        assertNull(field(controller, "refreshTimeline"));
    }

    @Test
    void apiSettlementStateActionStateAndBidSuggestionBranchesUpdateControls() throws Exception {
        Bidder bidder = bidder("acovstate" + UUID.randomUUID().toString().substring(0, 8), 300.0);
        session.login(bidder);
        AuctionController controller = auctionController();

        Object missingSettlement = invoke(controller, "apiSettlementState", null, bidder.getId());
        assertEquals("Settlement: N/A", invoke(missingSettlement, "summary"));
        assertEquals(true, invoke(missingSettlement, "admitDisabled"));

        AuctionApiClient.SettlementDetail admitDetail = settlementDetail("AWAITING_WINNER_ADMISSION", bidder.getId());
        Object admitState = invoke(controller, "apiSettlementState", admitDetail, bidder.getId());
        assertEquals(false, invoke(admitState, "admitDisabled"));
        assertEquals(true, invoke(admitState, "confirmDisabled"));

        AuctionApiClient.SettlementDetail confirmDetail = settlementDetail("AWAITING_BUYER_CONFIRMATION", bidder.getId());
        Object confirmState = invoke(controller, "apiSettlementState", confirmDetail, bidder.getId());
        assertEquals(true, invoke(confirmState, "admitDisabled"));
        assertEquals(false, invoke(confirmState, "confirmDisabled"));

        Object finishedSnapshot = newNested(
                "org.example.controller.AuctionController$AuctionViewSnapshot",
                new Class<?>[]{
                        boolean.class, String.class, String.class, String.class, double.class, double.class,
                        String.class, long.class, List.class, double.class, boolean.class, boolean.class,
                        boolean.class, String.class, boolean.class, boolean.class
                },
                false,
                "Finished Item",
                "Done",
                "FINISHED",
                200.0,
                210.0,
                "27/05/2026 12:00",
                0L,
                List.of(new Bid("BID-STATE", bidder.getId(), "ITEM", 200.0, LocalDateTime.of(2026, 5, 27, 10, 0))),
                20.0,
                true,
                true,
                true,
                "Settlement: done",
                false,
                false
        );
        invoke(controller, "applyActionState", finishedSnapshot, 0L);
        assertTrue(field(controller, "placeBidButton", Button.class).isDisabled());
        assertTrue(field(controller, "confirmEntryButton", Button.class).isDisabled());
        assertFalse(field(controller, "admitResultButton", Button.class).isDisabled());

        field(controller, "bidAmountCombo", ComboBox.class).setDisable(false);
        invoke(controller, "refreshBidAmountSuggestions", 210.0, true);
        assertEquals(List.of("210.0", "220.0", "260.0", "310.0"), field(controller, "bidAmountCombo", ComboBox.class).getItems());
        assertEquals("210.00", field(controller, "bidAmountCombo", ComboBox.class).getValue());
        invoke(controller, "readyBidAmountInput", 200.0, false);
        assertEquals("210.00", field(controller, "bidAmountCombo", ComboBox.class).getValue());
        field(controller, "bidAmountCombo", ComboBox.class).getEditor().setText("bad");
        invoke(controller, "readyBidAmountInput", 220.0, false);
        assertEquals("220.00", field(controller, "bidAmountCombo", ComboBox.class).getValue());

        setField(controller, "refreshActive", false);
        invoke(controller, "onNewBid", new Bid("BID-IGNORED", bidder.getId(), "ITEM", 220.0, LocalDateTime.now()));
    }

    private AuctionController auctionController() throws Exception {
        AuctionController controller = new AuctionController();
        setField(controller, "userLabel", new Label());
        setField(controller, "itemNameLabel", new Label());
        setField(controller, "descriptionLabel", new Label());
        setField(controller, "statusLabel", new Label());
        setField(controller, "currentPriceLabel", new Label());
        setField(controller, "minimumBidLabel", new Label());
        setField(controller, "endTimeLabel", new Label());
        setField(controller, "timeRemainingLabel", new Label());
        setField(controller, "bidEntryTimeRemainingLabel", new Label());
        setField(controller, "depositLabel", new Label());
        setField(controller, "settlementLabel", new Label());
        setField(controller, "currentWinnerLabel", new Label());
        setField(controller, "bidNotificationList", new ListView<String>());
        setField(controller, "bidTable", new TableView<Bid>());
        setField(controller, "bidderColumn", new TableColumn<Bid, String>());
        setField(controller, "amountColumn", new TableColumn<Bid, Double>());
        setField(controller, "timeColumn", new TableColumn<Bid, LocalDateTime>());
        ComboBox<String> bidAmount = new ComboBox<>();
        bidAmount.setEditable(true);
        setField(controller, "bidAmountCombo", bidAmount);
        setField(controller, "placeBidButton", new Button());
        setField(controller, "confirmEntryButton", new Button());
        setField(controller, "admitResultButton", new Button());
        setField(controller, "confirmReceivedButton", new Button());
        return controller;
    }

    private Bidder bidder(String username, double balance) {
        Bidder bidder = (Bidder) authenticationService.registerManualBidder(
                username,
                "secret",
                username + "@test.local",
                "Auction Controller Bidder " + username
        );
        bidder.setBalance(balance);
        authenticationService.updateUser(bidder);
        return bidder;
    }

    private Seller seller(String username) {
        return (Seller) authenticationService.registerManualSeller(
                username,
                "secret",
                username + "@test.local",
                "Auction Controller Seller " + username
        );
    }

    private static AuctionApiClient.SettlementDetail settlementDetail(String status, String winnerId) {
        return new AuctionApiClient.SettlementDetail(
                "ITEM",
                "Item",
                "SELLER",
                status,
                winnerId,
                200.0,
                20.0,
                10.0,
                210.0,
                190.0,
                20.0,
                180.0,
                190.0,
                0.0,
                0.0,
                "27/05/2026 12:00",
                "summary"
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    private static <T> T field(Object target, String name, Class<T> type) throws Exception {
        return type.cast(field(target, name));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object invoke(Object target, String name, Object... args) throws Exception {
        Class<?>[] parameterTypes = new Class<?>[args.length];
        for (int index = 0; index < args.length; index++) {
            parameterTypes[index] = args[index] == null ? Object.class : primitiveAwareType(args[index].getClass());
        }
        Method method = findMethod(target.getClass(), name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static Method findMethod(Class<?> type, String name, Class<?>[] argumentTypes) throws NoSuchMethodException {
        for (Method method : type.getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != argumentTypes.length) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            boolean compatible = true;
            for (int index = 0; index < parameterTypes.length; index++) {
                if (argumentTypes[index] == Object.class && !parameterTypes[index].isPrimitive()) {
                    continue;
                }
                if (!wrap(parameterTypes[index]).isAssignableFrom(wrap(argumentTypes[index]))) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) {
                return method;
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static Class<?> primitiveAwareType(Class<?> type) {
        if (type == Boolean.class) {
            return boolean.class;
        }
        if (type == Long.class) {
            return long.class;
        }
        if (type == Double.class) {
            return double.class;
        }
        if (type == Integer.class) {
            return int.class;
        }
        return type;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        return type;
    }

    private static Object newNested(String className, Class<?>[] parameterTypes, Object... args) throws Exception {
        Class<?> type = Class.forName(className);
        Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
        constructor.setAccessible(true);
        return constructor.newInstance(args);
    }
}
