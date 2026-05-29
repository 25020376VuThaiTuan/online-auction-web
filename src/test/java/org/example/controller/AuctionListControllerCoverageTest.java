package org.example.controller;

import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.example.model.Bidder;
import org.example.state.ApplicationSession;
import org.example.util.AuctionCatalogFilters;
import org.example.viewmodel.AuctionListEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionListControllerCoverageTest {
    private final ApplicationSession session = ApplicationSession.getInstance();

    @BeforeAll
    static void initToolkit() {
        JavaFxTestSupport.startToolkit();
    }

    @AfterEach
    void tearDown() {
        JavaFxTestSupport.closeOpenDialogs();
        session.clearWatchedAuctions();
        session.logout();
    }

    @Test
    void initializeConfiguresCellsAndRefreshLoopWithoutNavigatingAway() throws Exception {
        session.login(bidder());
        AuctionListController controller = controller();

        JavaFxTestSupport.runAndWait(() -> {
            controller.initialize();
            try {
                invoke(controller, "stopRefreshLoop");
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });

        assertTrue(field(controller, "welcomeLabel", Label.class).getText().startsWith("Signed in as:"));
        assertEquals(AuctionCatalogFilters.ALL_STATUSES, field(controller, "statusFilterChoiceBox", ChoiceBox.class).getValue());
        assertEquals(AuctionCatalogFilters.SORT_ENDING_SOON, field(controller, "sortChoiceBox", ChoiceBox.class).getValue());

        TableColumn<AuctionListEntry, String> statusColumn = field(controller, "statusColumn");
        TableCell<AuctionListEntry, String> statusCell = statusColumn.getCellFactory().call(statusColumn);
        invokeUpdateItem(statusCell, "RUNNING", false);
        assertEquals("RUNNING", statusCell.getText());
        assertTrue(statusCell.getStyleClass().contains("status-pill"));
        invokeUpdateItem(statusCell, null, true);
        assertEquals(null, statusCell.getText());

        TableColumn<AuctionListEntry, String> watchColumn = field(controller, "watchColumn");
        TableCell<AuctionListEntry, String> watchCell = watchColumn.getCellFactory().call(watchColumn);
        invokeUpdateItem(watchCell, "Watching", false);
        assertEquals("Watching", watchCell.getText());
        assertTrue(watchCell.getStyleClass().contains("watch-active"));
        invokeUpdateItem(watchCell, "", false);
        assertEquals(null, watchCell.getText());

        TableView<AuctionListEntry> table = field(controller, "auctionTable");
        TableRow<AuctionListEntry> row = table.getRowFactory().call(table);
        setField(controller, "selectedAuctionId", "A-ROW");
        invokeUpdateItem(row, entry("A-ROW", "Watched Camera", "RUNNING", 30L), false);
        assertTrue(row.getPseudoClassStates().stream().anyMatch(pseudoClass -> "active-auction".equals(pseudoClass.getPseudoClassName())));
        invokeUpdateItem(row, null, true);
    }

    @Test
    void filtersWatchHandlersSelectionAndFailureMessagesUpdateControls() throws Exception {
        session.login(bidder());
        AuctionListController controller = controller();
        JavaFxTestSupport.runAndWait(() -> {
            try {
                invoke(controller, "configureMarketplaceControls");
                List<AuctionListEntry> entries = List.of(
                        entry("A-1", "Camera", "RUNNING", 60L),
                        entry("A-2", "Lamp", "OPEN", 120L),
                        entry("A-3", "Book", "FINISHED", 0L)
                );
                setField(controller, "latestEntries", entries);

                invoke(controller, "applyAuctionFilters");
                assertEquals(3, field(controller, "auctionTable", TableView.class).getItems().size());
                assertEquals("3 auctions available", field(controller, "resultCountLabel", Label.class).getText());
                assertTrue(field(controller, "openAuctionButton", Button.class).isDisabled());

                TableView<AuctionListEntry> table = field(controller, "auctionTable");
                table.getSelectionModel().select(entries.getFirst());
                invoke(controller, "updateAuctionSelectionActions");
                assertFalse(field(controller, "openAuctionButton", Button.class).isDisabled());
                assertEquals("Watch", field(controller, "toggleWatchButton", Button.class).getText());

                invoke(controller, "handleToggleWatch");
                assertTrue(session.isAuctionWatched("A-1"));
                assertEquals("Unwatch", field(controller, "toggleWatchButton", Button.class).getText());

                field(controller, "watchedOnlyCheckBox", CheckBox.class).setSelected(true);
                invoke(controller, "applyAuctionFilters");
                assertEquals(1, table.getItems().size());

                invoke(controller, "handleWatchVisible");
                assertTrue(session.isAuctionWatched("A-1"));
                invoke(controller, "handleClearWatched");
                assertFalse(session.isAuctionWatched("A-1"));
                assertEquals("No watched auctions match the current filters.", field(controller, "resultCountLabel", Label.class).getText());

                field(controller, "watchedOnlyCheckBox", CheckBox.class).setSelected(false);
                field(controller, "searchField", TextField.class).setText("camera");
                invoke(controller, "applyAuctionFilters");
                assertEquals("Showing 1 of 3 auctions", field(controller, "resultCountLabel", Label.class).getText());

                invoke(controller, "handleClearFilters");
                assertEquals("", field(controller, "searchField", TextField.class).getText());
                assertEquals(3, table.getItems().size());

                JavaFxTestSupport.closeNextDialog(javafx.scene.control.ButtonType.OK);
                table.getSelectionModel().clearSelection();
                invoke(controller, "handleOpenAuction");
                JavaFxTestSupport.closeNextDialog(javafx.scene.control.ButtonType.OK);
                invoke(controller, "handleToggleWatch");
                JavaFxTestSupport.closeNextDialog(javafx.scene.control.ButtonType.OK);
                invoke(controller, "handleRefreshFailure", "offline", true);
                invoke(controller, "handleRefreshFailure", "offline", false);
                invoke(controller, "updateResultCountLabel", 0, 0);
                assertEquals("No auctions are available right now.", field(controller, "resultCountLabel", Label.class).getText());
                assertThrows(IllegalStateException.class, () -> {
                    try {
                        invoke(controller, "apiToken");
                    } catch (IllegalStateException e) {
                        throw e;
                    } catch (Exception e) {
                        Throwable cause = e.getCause();
                        if (cause instanceof IllegalStateException illegalStateException) {
                            throw illegalStateException;
                        }
                        throw new AssertionError(e);
                    }
                });
                assertEquals("deep", invoke(controller, "refreshFailureMessage", new CompletionException(new IllegalStateException("deep"))));
                assertEquals("Auction data could not be refreshed.", invoke(controller, "refreshFailureMessage", new RuntimeException(" ")));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    private AuctionListController controller() throws Exception {
        AuctionListController controller = new AuctionListController();
        setField(controller, "welcomeLabel", new Label());
        setField(controller, "auctionTable", new TableView<AuctionListEntry>());
        setField(controller, "watchColumn", new TableColumn<AuctionListEntry, String>());
        setField(controller, "nameColumn", new TableColumn<AuctionListEntry, String>());
        setField(controller, "statusColumn", new TableColumn<AuctionListEntry, String>());
        setField(controller, "currentPriceColumn", new TableColumn<AuctionListEntry, Double>());
        setField(controller, "minimumBidColumn", new TableColumn<AuctionListEntry, Double>());
        setField(controller, "timeRemainingColumn", new TableColumn<AuctionListEntry, String>());
        setField(controller, "endTimeColumn", new TableColumn<AuctionListEntry, String>());
        setField(controller, "searchField", new TextField());
        setField(controller, "statusFilterChoiceBox", new ChoiceBox<String>());
        setField(controller, "sortChoiceBox", new ChoiceBox<String>());
        setField(controller, "openOnlyCheckBox", new CheckBox());
        setField(controller, "watchedOnlyCheckBox", new CheckBox());
        setField(controller, "resultCountLabel", new Label());
        setField(controller, "toggleWatchButton", new Button());
        setField(controller, "openAuctionButton", new Button());
        return controller;
    }

    private static AuctionListEntry entry(String id, String name, String status, long remainingSeconds) {
        return new AuctionListEntry(id, name, status, 100.0, 110.0, "27/05/2026 12:00", remainingSeconds);
    }

    private static Bidder bidder() {
        Bidder bidder = new Bidder("AUCTION-LIST-BIDDER", "auction-list-bidder", "hash", "list@test.local", 100.0);
        bidder.setRole("BIDDER");
        bidder.setFullName("Auction List Bidder");
        return bidder;
    }

    private static void invokeUpdateItem(Object cell, Object value, boolean empty) throws Exception {
        Method method = findUpdateItem(cell);
        method.invoke(cell, value, empty);
    }

    private static Method findUpdateItem(Object cell) throws NoSuchMethodException {
        Class<?> type = cell.getClass();
        while (type != null) {
            for (Method method : type.getDeclaredMethods()) {
                if ("updateItem".equals(method.getName()) && method.getParameterCount() == 2) {
                    method.setAccessible(true);
                    return method;
                }
            }
            type = type.getSuperclass();
        }
        throw new NoSuchMethodException("updateItem");
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
        if (type == int.class) {
            return Integer.class;
        }
        return type;
    }
}
