package org.example.controller;

import javafx.fxml.FXMLLoader;
import javafx.collections.FXCollections;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import org.example.model.Bidder;
import org.example.state.ApplicationSession;
import org.example.viewmodel.AuctionEligibilityEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DashboardFxmlLoadTest {
    @BeforeAll
    static void initToolkit() {
        JavaFxTestSupport.startToolkit();
    }

    @AfterEach
    void clearSession() {
        JavaFxTestSupport.closeOpenDialogs();
        ApplicationSession.getInstance().logout();
    }

    @Test
    void dashboardFxmlLoadsWithReferenceShell() {
        JavaFxTestSupport.runAndWait(() -> {
            DashboardController controller = null;
            try {
                Bidder bidder = new Bidder("FXML-BIDDER", "fxml-bidder", "hash", "fxml@test.local", 500.0);
                bidder.setRole("BIDDER");
                bidder.setFullName("FXML Bidder");
                ApplicationSession.getInstance().login(bidder);

                FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/Dashboard.fxml"));
                Parent root = loader.load();
                controller = loader.getController();

                assertNotNull(root.lookup(".app-header"));
                assertNotNull(root.lookup(".sidebar"));
                TabPane tabPane = assertInstanceOf(TabPane.class, root.lookup(".content-tab-pane"));
                assertEquals(7, tabPane.getTabs().size());

                Button avatarButton = assertInstanceOf(Button.class, root.lookup(".avatar-button"));
                avatarButton.fire();
                assertEquals("Profile", tabPane.getSelectionModel().getSelectedItem().getText());

                @SuppressWarnings("unchecked")
                TableView<AuctionEligibilityEntry> auctionTable = field(controller, "auctionTable", TableView.class);
                auctionTable.setItems(FXCollections.observableArrayList(new AuctionEligibilityEntry(
                        "FXML-AUCTION",
                        "FXML Camera",
                        "RUNNING",
                        100.0,
                        110.0,
                        25.0,
                        500.0,
                        true,
                        true,
                        "29/05/2026 15:00",
                        120L
                )));
                auctionTable.getSelectionModel().selectFirst();
                assertEquals("Bidding", tabPane.getSelectionModel().getSelectedItem().getText());
            } catch (Exception exception) {
                throw new AssertionError(exception);
            } finally {
                if (controller != null) {
                    stopRefreshLoop(controller);
                }
            }
        });
    }

    private static void stopRefreshLoop(DashboardController controller) {
        try {
            Method method = DashboardController.class.getDeclaredMethod("stopRefreshLoop");
            method.setAccessible(true);
            method.invoke(controller);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static <T> T field(Object target, String name, Class<T> type) throws ReflectiveOperationException {
        Field field = DashboardController.class.getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }
}
