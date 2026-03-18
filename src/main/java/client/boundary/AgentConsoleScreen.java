package client.boundary;

import client.GCMClient;
import client.LoginController;
import client.MenuNavigationHelper;
import common.MessageType;
import common.Request;
import common.Response;
import common.dto.SupportTicketDTO;
import common.dto.TicketMessageDTO;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent Console screen for support agents.
 * Allows viewing, claiming, responding to, and resolving tickets.
 */
public class AgentConsoleScreen {
    private static final String BACK_BTN_BASE_STYLE =
            "-fx-background-color: transparent; -fx-border-color: transparent; -fx-text-fill: #7f8c8d; -fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 10; -fx-padding: 6 10;";
    private static final String BACK_BTN_HOVER_STYLE =
            "-fx-background-color: transparent; -fx-border-color: transparent; -fx-text-fill: #111111; -fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 10; -fx-padding: 6 10;";

    @FXML private WebView navbarLogoView1;
    @FXML private VBox guestDashboardPane;
    @FXML private Button mapEditorNavBtn;
    @FXML private Button myPurchasesNavBtn;
    @FXML private Button profileNavBtn;
    @FXML private Button customersNavBtn;
    @FXML private Button pricingNavBtn;
    @FXML private Button pricingApprovalNavBtn;
    @FXML private Button supportNavBtn;
    @FXML private Button agentConsoleNavBtn;
    @FXML private Button editApprovalsNavBtn;
    @FXML private Button reportsNavBtn;
    @FXML private Button userManagementNavBtn;
    private static final String NAVBAR_LOGO_SVG_RESOURCE = "/client/assets/favicon.svg";

    @FXML
    private TabPane queueTabPane;
    @FXML
    private ListView<SupportTicketDTO> myTicketsList;
    @FXML
    private ListView<SupportTicketDTO> pendingTicketsList;
    @FXML
    private Label agentInfoLabel;
    @FXML
    private Label ticketSubjectLabel;
    @FXML
    private Label ticketInfoLabel;
    @FXML
    private Button resolveBtn;
    @FXML
    private ScrollPane messagesScrollPane;
    @FXML
    private VBox messagesContainer;
    @FXML
    private VBox replyBox;
    @FXML
    private TextArea replyInput;
    @FXML
    private Label statusLabel;

    private SupportTicketDTO selectedTicket;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, HH:mm");

    @FXML
    public void initialize() {
        applyNavbarLogoSvg();
        MenuNavigationHelper.configureSidebarButtons(mapEditorNavBtn, myPurchasesNavBtn, profileNavBtn, customersNavBtn, pricingNavBtn, pricingApprovalNavBtn, supportNavBtn, agentConsoleNavBtn, editApprovalsNavBtn, reportsNavBtn, userManagementNavBtn);
        try {
            agentInfoLabel.setText("Agent: " + GCMClient.getInstance().getCurrentUsername());
        } catch (IOException e) {
            agentInfoLabel.setText("Agent: Disconnected");
        }

        // Setup cell factories
        setupTicketListView(myTicketsList);
        setupTicketListView(pendingTicketsList);

        // Selection listeners
        myTicketsList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                pendingTicketsList.getSelectionModel().clearSelection();
                loadTicketDetails(newVal.getId());
            }
        });

        pendingTicketsList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                myTicketsList.getSelectionModel().clearSelection();
                loadTicketDetails(newVal.getId());
            }
        });

        // Load initial data
        refreshMyTickets();
        refreshPendingQueue();
    }

    private void setupTicketListView(ListView<SupportTicketDTO> listView) {
        listView.setCellFactory(lv -> new ListCell<SupportTicketDTO>() {
            @Override
            protected void updateItem(SupportTicketDTO ticket, boolean empty) {
                super.updateItem(ticket, empty);
                if (empty || ticket == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    VBox cell = new VBox(5);
                    cell.setPadding(new Insets(10));

                    // White-themed styling (match Support page)
                    String bgColor = "#f8f9fa";
                    String borderColor = "#e9ecef";
                    cell.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 10; -fx-border-color: " + borderColor + "; -fx-border-radius: 10; -fx-border-width: 1;");

                    Label subject = new Label(ticket.getSubject() != null ? ticket.getSubject() : "");
                    subject.setStyle("-fx-text-fill: #2c3e50; -fx-font-size: 12px; -fx-font-weight: bold;");

                    Label info = new Label(ticket.getUsername() + " • " + ticket.getStatusDisplay());
                    info.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 10px;");

                    Label time = new Label(dateFormat.format(ticket.getCreatedAt()));
                    time.setStyle("-fx-text-fill: #95a5a6; -fx-font-size: 9px;");

                    cell.getChildren().addAll(subject, info, time);
                    setGraphic(cell);
                    setStyle("-fx-background-color: transparent;");
                }
            }
        });
    }

    @FXML
    private void refreshMyTickets() {
        statusLabel.setText("Loading my tickets...");

        new Thread(() -> {
            try {
                GCMClient client = GCMClient.getInstance();
                Integer uid = getCurrentUserId();
                Request request = new Request(MessageType.AGENT_LIST_ASSIGNED, null, LoginController.currentSessionToken, uid != null ? uid : 0);

                Response response = client.sendRequestSync(request);

                Platform.runLater(() -> {
                    if (response == null) {
                        statusLabel.setText("Connection error");
                        return;
                    }
                    if (response.isOk()) {
                        List<SupportTicketDTO> tickets = parseTicketList(response.getPayload());
                        myTicketsList.getItems().clear();
                        myTicketsList.getItems().addAll(tickets);
                        statusLabel.setText("My tickets: " + tickets.size());
                    } else {
                        statusLabel.setText("Error: " + response.getErrorMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Connection error"));
            }
        }).start();
    }

    /** Safely parse payload as List of tickets (handles List or single SupportTicketDTO from server). */
    @SuppressWarnings("unchecked")
    private static List<SupportTicketDTO> parseTicketList(Object payload) {
        if (payload instanceof List) {
            List<?> list = (List<?>) payload;
            List<SupportTicketDTO> out = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof SupportTicketDTO) out.add((SupportTicketDTO) o);
            }
            return out;
        }
        if (payload instanceof SupportTicketDTO) {
            return Collections.singletonList((SupportTicketDTO) payload);
        }
        return Collections.emptyList();
    }

    @FXML
    private void refreshPendingQueue() {
        statusLabel.setText("Loading pending queue...");

        new Thread(() -> {
            try {
                GCMClient client = GCMClient.getInstance();
                Integer uid = getCurrentUserId();
                Request request = new Request(MessageType.AGENT_LIST_PENDING, null, LoginController.currentSessionToken, uid != null ? uid : 0);

                Response response = client.sendRequestSync(request);

                Platform.runLater(() -> {
                    if (response == null) {
                        statusLabel.setText("Connection error");
                        return;
                    }
                    if (response.isOk()) {
                        List<SupportTicketDTO> tickets = parseTicketList(response.getPayload());
                        pendingTicketsList.getItems().clear();
                        pendingTicketsList.getItems().addAll(tickets);
                        statusLabel.setText("Pending queue: " + tickets.size());
                    } else {
                        statusLabel.setText("Error: " + response.getErrorMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Connection error"));
            }
        }).start();
    }

    private void loadTicketDetails(int ticketId) {
        statusLabel.setText("Loading ticket #" + ticketId + "...");

        new Thread(() -> {
            try {
                GCMClient client = GCMClient.getInstance();
                Integer uid = getCurrentUserId();
                Request request = new Request(MessageType.GET_TICKET_DETAILS, ticketId, LoginController.currentSessionToken, uid != null ? uid : 0);

                Response response = client.sendRequestSync(request);

                Platform.runLater(() -> {
                    if (response == null) {
                        statusLabel.setText("Connection error");
                        return;
                    }
                    if (response.isOk()) {
                        Object pl = response.getPayload();
                        selectedTicket = (pl instanceof SupportTicketDTO) ? (SupportTicketDTO) pl : null;
                        if (selectedTicket != null) {
                            displayTicket(selectedTicket);
                            statusLabel.setText("Ticket #" + ticketId + " loaded");
                        } else {
                            statusLabel.setText("Invalid ticket data");
                        }
                    } else {
                        statusLabel.setText("Error: " + response.getErrorMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Connection error"));
            }
        }).start();
    }

    private void displayTicket(SupportTicketDTO ticket) {
        ticketSubjectLabel.setText(ticket.getSubject() != null ? ticket.getSubject() : "");
        ticketInfoLabel.setText("Customer: " + ticket.getUsername() + " • " +
                ticket.getStatusDisplay() + " • Priority: " + ticket.getPriority());

        // Show controls only for assigned tickets
        boolean isAssignedToMe = ticket.getAssignedAgentId() != null &&
                ticket.getAssignedAgentId().equals(getCurrentUserId());
        boolean isClosed = ticket.getStatus() == SupportTicketDTO.Status.CLOSED;

        resolveBtn.setVisible(isAssignedToMe && !isClosed);
        replyBox.setVisible(isAssignedToMe && !isClosed);

        // Display messages (customer shown by username on agent view)
        messagesContainer.getChildren().clear();
        String customerUsername = ticket.getUsername() != null ? ticket.getUsername() : "Customer";

        if (ticket.getMessages() != null) {
            for (TicketMessageDTO msg : ticket.getMessages()) {
                messagesContainer.getChildren().add(createMessageBubble(msg, customerUsername));
            }
        }

        Platform.runLater(() -> messagesScrollPane.setVvalue(1.0));
    }

    /** Strip emojis from conversation text. */
    private static String stripEmojis(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); ) {
            int cp = Character.codePointAt(s, i);
            boolean isEmoji = (cp >= 0x2600 && cp <= 0x26FF) || (cp >= 0x2700 && cp <= 0x27BF)
                    || (cp >= 0x1F300 && cp <= 0x1F9FF);
            if (!isEmoji) sb.appendCodePoint(cp);
            i += Character.charCount(cp);
        }
        return sb.toString();
    }

    /** Message bubble style matching Support page (white themed). On agent view, customer is shown by username. */
    private VBox createMessageBubble(TicketMessageDTO msg, String customerUsername) {
        VBox bubble = new VBox(5);
        bubble.setPadding(new Insets(10, 15, 10, 15));
        bubble.setMaxWidth(500);

        String bgStyle;
        String textColor = "black";
        Pos alignment;
        String timeColor = "#666666";
        switch (msg.getSenderType()) {
            case BOT:
                bgStyle = "-fx-background-color: transparent;";
                alignment = Pos.CENTER_LEFT;
                break;
            case CUSTOMER:
                bgStyle = "-fx-background-color: #F2F2F2;";
                alignment = Pos.CENTER_RIGHT;
                break;
            case AGENT:
                bgStyle = "-fx-background-color: #F2F2F2;";
                alignment = Pos.CENTER_LEFT;
                break;
            default:
                bgStyle = "-fx-background-color: #F2F2F2;";
                alignment = Pos.CENTER_LEFT;
        }

        bubble.setStyle(bgStyle + " -fx-background-radius: 15;");

        // On agent page show customer username for CUSTOMER messages, not "You"
        String senderDisplay;
        if (msg.getSenderType() == TicketMessageDTO.SenderType.CUSTOMER) {
            senderDisplay = customerUsername != null ? customerUsername : "Customer";
        } else {
            senderDisplay = msg.getSenderDisplay() != null ? msg.getSenderDisplay() : "";
        }
        Label senderLabel = new Label(senderDisplay);
        senderLabel.setStyle("-fx-text-fill: " + textColor + "; -fx-font-size: 13px; -fx-font-weight: bold;");

        String messageText = msg.getMessage() != null ? msg.getMessage() : "";
        Label messageLabel = new Label(stripEmojis(messageText));
        messageLabel.setWrapText(true);
        messageLabel.setStyle("-fx-text-fill: " + textColor + "; -fx-font-size: 16px;");

        Label timeLabel = new Label(dateFormat.format(msg.getCreatedAt()));
        timeLabel.setStyle("-fx-text-fill: " + timeColor + "; -fx-font-size: 11px;");

        bubble.getChildren().addAll(senderLabel, messageLabel, timeLabel);

        HBox wrapper = new HBox(bubble);
        wrapper.setAlignment(alignment);

        VBox container = new VBox(wrapper);
        return container;
    }

    @FXML
    private void claimTicket() {
        SupportTicketDTO selected = pendingTicketsList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a ticket to claim.");
            return;
        }

        statusLabel.setText("Claiming ticket #" + selected.getId() + "...");

        new Thread(() -> {
            try {
                GCMClient client = GCMClient.getInstance();
                Integer uid = getCurrentUserId();
                Request request = new Request(MessageType.AGENT_CLAIM_TICKET, selected.getId(), LoginController.currentSessionToken, uid != null ? uid : 0);

                Response response = client.sendRequestSync(request);

                Platform.runLater(() -> {
                    if (response == null) {
                        statusLabel.setText("Connection error");
                        return;
                    }
                    if (response.isOk()) {
                        showAlert(Alert.AlertType.INFORMATION, "Claimed",
                                "Ticket #" + selected.getId() + " is now assigned to you.");
                        refreshMyTickets();
                        refreshPendingQueue();

                        // Switch to My Tickets tab
                        queueTabPane.getSelectionModel().select(0);
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Error", response.getErrorMessage());
                    }
                    statusLabel.setText("Ready");
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Connection error"));
            }
        }).start();
    }

    @FXML
    private void sendReply() {
        if (selectedTicket == null)
            return;
        String message = replyInput.getText().trim();
        if (message.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Empty", "Please enter a reply message.");
            return;
        }

        statusLabel.setText("Sending reply...");

        new Thread(() -> {
            try {
                GCMClient client = GCMClient.getInstance();

                Map<String, Object> payload = new HashMap<>();
                payload.put("ticketId", selectedTicket.getId());
                payload.put("message", message);

                Request request = new Request(MessageType.AGENT_REPLY, payload);
                request.setUserId(getCurrentUserId());

                Response response = client.sendRequestSync(request);

                Platform.runLater(() -> {
                    if (response == null) {
                        statusLabel.setText("Connection error");
                        return;
                    }
                    if (response.isOk()) {
                        replyInput.clear();
                        loadTicketDetails(selectedTicket.getId());
                        statusLabel.setText("Reply sent");
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Error", response.getErrorMessage());
                        statusLabel.setText("Error");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Connection error"));
            }
        }).start();
    }

    @FXML
    private void resolveTicket() {
        if (selectedTicket == null)
            return;

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Resolve Ticket");
        dialog.setHeaderText("Add a closing message (optional):");
        dialog.setContentText("Message:");

        dialog.showAndWait().ifPresent(closingMessage -> {
            statusLabel.setText("Resolving ticket...");

            new Thread(() -> {
                try {
                    GCMClient client = GCMClient.getInstance();

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("ticketId", selectedTicket.getId());
                    payload.put("message", closingMessage);

                    Integer uid = getCurrentUserId();
                    Request request = new Request(MessageType.AGENT_CLOSE_TICKET, payload, LoginController.currentSessionToken, uid != null ? uid : 0);

                    Response response = client.sendRequestSync(request);

                    Platform.runLater(() -> {
                        if (response == null) {
                            statusLabel.setText("Connection error");
                            return;
                        }
                        if (response.isOk()) {
                            showAlert(Alert.AlertType.INFORMATION, "Resolved",
                                    "Ticket #" + selectedTicket.getId() + " has been resolved and closed.");
                            refreshMyTickets();
                            selectedTicket = null;
                            ticketSubjectLabel.setText("Select a ticket");
                            ticketInfoLabel.setText("");
                            messagesContainer.getChildren().clear();
                            resolveBtn.setVisible(false);
                            replyBox.setVisible(false);
                        } else {
                            showAlert(Alert.AlertType.ERROR, "Error", response.getErrorMessage());
                        }
                        statusLabel.setText("Ready");
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> statusLabel.setText("Connection error"));
                }
            }).start();
        });
    }

    @FXML
    private void handleBack(ActionEvent event) {
        goBack();
    }

    @FXML
    private void handleBackHoverEnter(MouseEvent event) {
        if (event.getSource() instanceof Button button) button.setStyle(BACK_BTN_HOVER_STYLE);
    }

    @FXML
    private void handleBackHoverExit(MouseEvent event) {
        if (event.getSource() instanceof Button button) button.setStyle(BACK_BTN_BASE_STYLE);
    }

    private void applyNavbarLogoSvg() {
        if (navbarLogoView1 == null) return;
        java.net.URL svgUrl = getClass().getResource(NAVBAR_LOGO_SVG_RESOURCE);
        if (svgUrl == null) {
            navbarLogoView1.setVisible(false);
            navbarLogoView1.setManaged(false);
            return;
        }
        try {
            navbarLogoView1.getEngine().load(svgUrl.toExternalForm());
        } catch (Exception e) {
            navbarLogoView1.setVisible(false);
            navbarLogoView1.setManaged(false);
        }
    }

    @FXML
    private void toggleGuestDashboard(ActionEvent event) {
        if (guestDashboardPane == null) return;
        boolean nextVisible = !guestDashboardPane.isVisible();
        guestDashboardPane.setVisible(nextVisible);
        guestDashboardPane.setManaged(nextVisible);
    }

    @FXML private void navigateToHome(ActionEvent e) { MenuNavigationHelper.navigateToDashboard((Node) e.getSource()); }
    @FXML private void openSearchScreenFromAction(ActionEvent e) { MenuNavigationHelper.navigateToCatalog(guestDashboardPane); }
    @FXML private void openMapEditorFromMenu(ActionEvent e) { MenuNavigationHelper.navigateToMapEditor(guestDashboardPane); }
    @FXML private void openMyPurchasesFromMenu(ActionEvent e) { MenuNavigationHelper.navigateToMyPurchases(guestDashboardPane); }
    @FXML private void openProfileFromMenu(ActionEvent e) { MenuNavigationHelper.navigateToProfile(guestDashboardPane); }
    @FXML private void openAdminCustomersFromMenu(ActionEvent e) { MenuNavigationHelper.navigateToAdminCustomers(guestDashboardPane); }
    @FXML private void openPricingFromMenu(ActionEvent e) { MenuNavigationHelper.navigateToPricing(guestDashboardPane); }
    @FXML private void openPricingApprovalFromMenu(ActionEvent e) { MenuNavigationHelper.navigateToPricingApproval(guestDashboardPane); }
    @FXML private void openSupportFromMenu(ActionEvent e) { MenuNavigationHelper.navigateToSupport(guestDashboardPane); }
    @FXML private void openAgentConsoleFromMenu(ActionEvent e) { MenuNavigationHelper.navigateToAgentConsole(guestDashboardPane); }
    @FXML private void openEditApprovalsFromMenu(ActionEvent e) { MenuNavigationHelper.navigateToEditApprovals(guestDashboardPane); }
    @FXML private void openReportsFromMenu(ActionEvent e) { MenuNavigationHelper.navigateToReports(guestDashboardPane); }
    @FXML private void openUserManagementFromMenu(ActionEvent e) { MenuNavigationHelper.navigateToUserManagement(guestDashboardPane); }
    @FXML
    private void goBack() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/dashboard.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) queueTabPane.getScene().getWindow();
            stage.setScene(new Scene(root, 1000, 700));
            stage.setTitle("GCM Dashboard");
            stage.setMaximized(true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Integer getCurrentUserId() {
        try {
            return GCMClient.getInstance().getCurrentUserId();
        } catch (IOException e) {
            return null;
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
