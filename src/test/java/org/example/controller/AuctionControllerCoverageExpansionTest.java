package org.example.controller;

import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import com.sun.net.httpserver.HttpServer;
import org.example.auction.AuctionDepositResult;
import org.example.auction.AuctionSettlement;
import org.example.auction.AuctionSettlementStatus;
import org.example.auction.AuctionSessionRegistry;
import org.example.auction.BidValidationResult;
import org.example.client.AuctionApiClient;
import org.example.model.ApprovalStatus;
import org.example.model.Bid;
import org.example.model.Bidder;
import org.example.model.Item;
import org.example.model.Seller;
import org.example.server.ApiSessionService;
import org.example.server.AuctionApiHandler;
import org.example.server.AuctionRealtimeBroker;
import org.example.service.AuthenticationService;
import org.example.service.AuctionWorkflowService;
import org.example.service.MarketplaceDashboardService;
import org.example.state.ApplicationSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
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
        JavaFxTestSupport.closeOpenDialogs();
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
    void initializeCellFactoryAndSuccessfulLocalBidFlowUseRealHandlers() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Bidder bidder = bidder("acovflow" + suffix, 800.0);
        Seller seller = seller("acovseller" + suffix);
        dashboardService.setWalletPin(bidder, PIN);
        dashboardService.setWalletPin(seller, PIN);

        Item item = dashboardService.addSellerItem(
                seller,
                "electronics",
                "Auction Controller Flow Camera " + suffix,
                "Controller success branch item",
                100.0,
                LocalDateTime.now().minusMinutes(2),
                LocalDateTime.now().plusMinutes(20),
                "Brand",
                12
        );
        dashboardService.updateItemApproval(item.getId(), ApprovalStatus.APPROVED);
        assertTrue(dashboardService.startAuction(seller, item.getId()));
        var admin = authenticationService.registerManualBidder(
                "acovadmin" + suffix,
                "secret",
                "acovadmin" + suffix + "@test.local",
                "Auction Controller Admin " + suffix
        );
        authenticationService.updateUserRole(admin.getId(), "ADMIN");

        session.login(bidder);
        session.setSelectedAuctionId(item.getId());
        AuctionController controller = auctionController();
        JavaFxTestSupport.runAndWait(() -> {
            try {
                controller.initialize();
                invoke(controller, "stopRefreshLoop");
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });

        assertEquals("Signed in as: " + session.getCurrentUserLabel(),
                field(controller, "userLabel", Label.class).getText());
        TableColumn<Bid, LocalDateTime> timeColumn = field(controller, "timeColumn");
        TableCell<Bid, LocalDateTime> cell = timeColumn.getCellFactory().call(timeColumn);
        invoke(cell, "updateItem", LocalDateTime.of(2026, 5, 27, 12, 0), false);
        assertFalse(cell.getText().isBlank());
        invoke(cell, "updateItem", null, true);
        assertEquals("", cell.getText());

        var authorization = dashboardService.authorizeWallet(bidder, PIN, Duration.ofMinutes(5));
        session.trustWalletAuthorization(bidder.getId(), authorization.token(), authorization.expiresAt());
        setField(controller, "selectedAuctionId", item.getId());
        setField(controller, "refreshActive", false);

        JavaFxTestSupport.closeNextDialog(ButtonType.OK);
        invokeOnFx(controller, "handleConfirmEntryDeposit");
        assertTrue(dashboardService.hasConfirmedEntryDeposit(item.getId(), bidder));

        ComboBox<String> bidAmountCombo = field(controller, "bidAmountCombo");
        bidAmountCombo.setDisable(false);
        bidAmountCombo.setValue("130.00");
        bidAmountCombo.getEditor().setText("130.00");

        JavaFxTestSupport.closeNextDialog(ButtonType.YES);
        invokeOnFx(controller, "handlePlaceBid");

        assertFalse(field(controller, "bidNotificationList", ListView.class).getItems().isEmpty());
        assertTrue(dashboardService.getBidHistory(item.getId()).stream()
                .anyMatch(bid -> bid.getBidderId().equals(bidder.getId()) && bid.getAmount() >= 130.0));

        assertTrue(dashboardService.finishAuction(seller, item.getId()));
        JavaFxTestSupport.closeNextDialog(ButtonType.OK);
        invokeOnFx(controller, "handleAdmitResult");
        assertEquals(AuctionSettlementStatus.AWAITING_SELLER_CONFIRMATION,
                dashboardService.getSettlement(item.getId()).orElseThrow().getStatus());

        dashboardService.markGoodsShipped(item.getId(), seller, PIN);
        JavaFxTestSupport.closeNextDialog(ButtonType.OK);
        invokeOnFx(controller, "handleConfirmReceived");
        assertEquals(AuctionSettlementStatus.PAYMENT_RELEASED,
                dashboardService.getSettlement(item.getId()).orElseThrow().getStatus());

        session.clearTrustedWalletAuthorization();
        JavaFxTestSupport.answerNextPasswordDialog(PIN, true, ButtonType.OK);
        assertTrue(invokeOnFx(controller, "requestWalletPin", "Remember PIN").toString().startsWith("wa_"));
    }

    @Test
    void apiSnapshotAndCurrentUserRefreshUseHttpBackedClientBranches() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", new AuctionApiHandler(
                AuthenticationService.getInstance(),
                AuctionWorkflowService.getInstance(),
                new ApiSessionService(),
                new AuctionRealtimeBroker()
        ));
        server.start();

        String previousBaseUrl = System.getProperty("auction.api.baseUrl");
        try {
            System.setProperty("auction.api.baseUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/api");
            AuctionApiClient client = newApiClient();
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            var auth = client.registerManualBidder(
                    "acov_api_" + suffix,
                    "secret123",
                    "acov_api_" + suffix + "@test.local",
                    "Auction API Bidder " + suffix
            );
            Seller seller = seller("acovapiseller" + suffix);
            Item item = dashboardService.addSellerItem(
                    seller,
                    "electronics",
                    "Auction API Camera " + suffix,
                    "Controller API snapshot item",
                    100.0,
                    LocalDateTime.now().minusMinutes(2),
                    LocalDateTime.now().plusMinutes(20),
                    "Brand",
                    12
            );
            dashboardService.updateItemApproval(item.getId(), ApprovalStatus.APPROVED);
            assertTrue(dashboardService.startAuction(seller, item.getId()));

            session.login(auth.user(), auth.token());
            session.setSelectedAuctionId(item.getId());
            AuctionController controller = auctionController();
            setField(controller, "apiClient", client);
            setField(controller, "selectedAuctionId", item.getId());

            Object snapshot = invoke(controller, "loadAuctionViewSnapshot");

            assertEquals(false, invoke(snapshot, "missingAuction"));
            assertEquals(item.getItemName(), invoke(snapshot, "itemName"));
            assertEquals("RUNNING", invoke(snapshot, "status"));
            assertEquals(false, invoke(snapshot, "depositConfirmed"));
            assertEquals("Auction API Bidder " + suffix, session.getCurrentUser().orElseThrow().getFullName());

            invoke(controller, "refreshApiCurrentUser");

            assertEquals(auth.user().getId(), session.getCurrentUser().orElseThrow().getId());
            assertEquals(auth.token(), invoke(controller, "apiToken"));
        } finally {
            if (previousBaseUrl == null) {
                System.clearProperty("auction.api.baseUrl");
            } else {
                System.setProperty("auction.api.baseUrl", previousBaseUrl);
            }
            server.stop(0);
        }
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

    @Test
    void publicHandlersAndClockBranchesHandleValidationCancellationAndSettlementStates() throws Exception {
        Bidder bidder = bidder("acovhandlers" + UUID.randomUUID().toString().substring(0, 8), 400.0);
        session.login(bidder);
        AuctionController controller = auctionController();
        setField(controller, "selectedAuctionId", "missing-auction");
        setField(controller, "refreshActive", false);

        invokeWithClosedDialog(controller, "handlePlaceBid");
        field(controller, "bidAmountCombo", ComboBox.class).setValue("bad");
        invokeWithClosedDialog(controller, "handlePlaceBid");
        field(controller, "bidAmountCombo", ComboBox.class).setValue("100");
        JavaFxTestSupport.closeNextDialog(ButtonType.NO);
        invokeOnFx(controller, "handlePlaceBid");

        JavaFxTestSupport.closeNextDialog(ButtonType.CANCEL);
        invokeOnFx(controller, "handleConfirmEntryDeposit");
        JavaFxTestSupport.answerNextPasswordDialogThenCloseAlert("", false);
        assertNull(invokeOnFx(controller, "requestWalletPin", "Blank PIN"));

        JavaFxTestSupport.answerNextPasswordDialogThenCloseAlert(PIN, false);
        invokeOnFx(controller, "handleAdmitResult");
        JavaFxTestSupport.answerNextPasswordDialogThenCloseAlert(PIN, false);
        invokeOnFx(controller, "handleConfirmReceived");

        AuctionSettlement missing = null;
        Object missingState = invoke(controller, "localSettlementState", missing, bidder.getId());
        assertEquals("Settlement: N/A", invoke(missingState, "summary"));
        assertEquals(true, invoke(missingState, "admitDisabled"));

        AuctionSettlement admitSettlement = settlement("SETTLE-ADMIT", bidder.getId(), AuctionSettlementStatus.AWAITING_WINNER_ADMISSION);
        Object admitState = invoke(controller, "localSettlementState", admitSettlement, bidder.getId());
        assertEquals(false, invoke(admitState, "admitDisabled"));
        assertEquals(true, invoke(admitState, "confirmDisabled"));

        AuctionSettlement confirmSettlement = settlement("SETTLE-CONFIRM", bidder.getId(), AuctionSettlementStatus.AWAITING_BUYER_CONFIRMATION);
        Object confirmState = invoke(controller, "localSettlementState", confirmSettlement, bidder.getId());
        assertEquals(true, invoke(confirmState, "admitDisabled"));
        assertEquals(false, invoke(confirmState, "confirmDisabled"));

        Object missingSnapshot = invokeStaticSnapshotMissing();
        setField(controller, "lastSnapshot", missingSnapshot);
        setField(controller, "refreshActive", true);
        invoke(controller, "updateClockOnly");

        Object runningSnapshot = newNested(
                "org.example.controller.AuctionController$AuctionViewSnapshot",
                new Class<?>[]{
                        boolean.class, String.class, String.class, String.class, double.class, double.class,
                        String.class, long.class, List.class, double.class, boolean.class, boolean.class,
                        boolean.class, String.class, boolean.class, boolean.class
                },
                false,
                "Clock Item",
                "Clock description",
                "RUNNING",
                100.0,
                110.0,
                "27/05/2026 12:00",
                0L,
                List.of(),
                20.0,
                true,
                true,
                false,
                "Settlement: N/A",
                true,
                true
        );
        setField(controller, "lastSnapshot", runningSnapshot);
        setField(controller, "lastSnapshotAppliedAtMillis", System.currentTimeMillis());
        setField(controller, "expirationRefreshRequested", false);
        field(controller, "refreshInFlight", java.util.concurrent.atomic.AtomicBoolean.class).set(true);
        invoke(controller, "updateClockOnly");
        assertEquals(true, field(controller, "expirationRefreshRequested"));
        assertEquals(true, field(controller, "refreshPending", java.util.concurrent.atomic.AtomicBoolean.class).get());
        setField(controller, "refreshActive", false);

        assertEquals(0L, invoke(controller, "currentSecondsRemaining", runningSnapshot));
        assertTrue((boolean) invoke(controller, "isFinishedStatus", "paid"));
        assertTrue((boolean) invoke(controller, "isFinishedStatus", "cancelled"));
    }

    @Test
    void rejectedBidDepositSettlementAndObserverBranchesUseRealAuctionState() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Bidder bidder = bidder("acovreject" + suffix, 500.0);
        Seller seller = seller("acovrejectseller" + suffix);
        dashboardService.setWalletPin(bidder, PIN);
        dashboardService.setWalletPin(seller, PIN);
        Item item = dashboardService.addSellerItem(
                seller,
                "electronics",
                "Auction Controller Rejection Camera " + suffix,
                "Controller rejection branch item",
                100.0,
                LocalDateTime.now().minusMinutes(2),
                LocalDateTime.now().plusMinutes(20),
                "Brand",
                12
        );
        dashboardService.updateItemApproval(item.getId(), ApprovalStatus.APPROVED);
        assertTrue(dashboardService.startAuction(seller, item.getId()));

        session.login(bidder);
        AuctionController controller = auctionController();
        setField(controller, "selectedAuctionId", item.getId());
        setField(controller, "refreshActive", false);
        var bidderAuthorization = dashboardService.authorizeWallet(bidder, PIN, Duration.ofMinutes(5));
        session.trustWalletAuthorization(bidder.getId(), bidderAuthorization.token(), bidderAuthorization.expiresAt());

        ComboBox<String> bidAmountCombo = field(controller, "bidAmountCombo");
        bidAmountCombo.setValue("130.00");
        bidAmountCombo.getEditor().setText("130.00");
        JavaFxTestSupport.closeNextDialog(ButtonType.YES);
        JavaFxTestSupport.closeNextDialog(ButtonType.OK);
        invokeOnFx(controller, "handlePlaceBid");
        assertTrue(dashboardService.getBidHistory(item.getId()).isEmpty());

        field(controller, "refreshInFlight", java.util.concurrent.atomic.AtomicBoolean.class).set(true);
        setField(controller, "refreshActive", true);
        invoke(controller, "onNewBid", new Bid("BID-OBSERVER", bidder.getId(), item.getId(), 130.0, LocalDateTime.now()));
        JavaFxTestSupport.runAndWait(() -> {
        });
        assertTrue(field(controller, "refreshPending", java.util.concurrent.atomic.AtomicBoolean.class).get());
        invoke(controller, "stopRefreshLoop");

        session.login(seller);
        AuctionController sellerController = auctionController();
        setField(sellerController, "selectedAuctionId", item.getId());
        setField(sellerController, "refreshActive", false);
        var sellerAuthorization = dashboardService.authorizeWallet(seller, PIN, Duration.ofMinutes(5));
        session.trustWalletAuthorization(seller.getId(), sellerAuthorization.token(), sellerAuthorization.expiresAt());
        JavaFxTestSupport.closeNextDialog(ButtonType.OK);
        invokeOnFx(sellerController, "handleConfirmEntryDeposit");
        assertFalse(dashboardService.hasConfirmedEntryDeposit(item.getId(), seller));

        JavaFxTestSupport.closeNextDialog(ButtonType.OK);
        invokeOnFx(sellerController, "runSettlementAction", "Rejected settlement",
                (Runnable) () -> {
                    throw new IllegalStateException("settlement unavailable");
                });
        invoke(sellerController, "stopRefreshLoop");
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

    private static Object invokeOnFx(Object target, String name, Object... args) {
        java.util.concurrent.atomic.AtomicReference<Object> result = new java.util.concurrent.atomic.AtomicReference<>();
        JavaFxTestSupport.runAndWait(() -> {
            try {
                result.set(invoke(target, name, args));
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
        return result.get();
    }

    private static void invokeWithClosedDialog(Object target, String name) {
        JavaFxTestSupport.closeNextDialog(ButtonType.OK);
        invokeOnFx(target, name);
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

    private static AuctionApiClient newApiClient() throws Exception {
        Constructor<AuctionApiClient> constructor = AuctionApiClient.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static Object invokeStaticSnapshotMissing() throws Exception {
        Class<?> type = Class.forName("org.example.controller.AuctionController$AuctionViewSnapshot");
        Method method = type.getDeclaredMethod("missing");
        method.setAccessible(true);
        return method.invoke(null);
    }

    private static AuctionSettlement settlement(String itemId, String winnerId, AuctionSettlementStatus status) {
        AuctionSettlement settlement = new AuctionSettlement(
                itemId,
                "Settlement Item",
                "seller",
                winnerId,
                200.0,
                20.0,
                10.0,
                210.0,
                190.0,
                20.0,
                180.0,
                LocalDateTime.of(2026, 5, 27, 10, 0)
        );
        settlement.setStatus(status);
        return settlement;
    }
}
