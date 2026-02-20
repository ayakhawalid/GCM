package client.boundary;

import client.GCMClient;
import common.MessageType;
import common.Request;
import common.Response;
import common.dto.CreateTicketRequest;
import common.dto.SupportTicketDTO;
import common.dto.TicketMessageDTO;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;

/**
 * Support Screen controller for customers.
 * Allows creating tickets, viewing responses, and escalating to agents.
 */
public class SupportScreen {

    @FXML
    private ListView<SupportTicketDTO> ticketListView;
    @FXML
    private TextField searchField;
    @FXML
    private Label ticketSubjectLabel;
    @FXML
    private Label ticketStatusLabel;
    @FXML
    private Button escalateBtn;
    @FXML
    private Button closeBtn;
    @FXML
    private ScrollPane messagesScrollPane;
    @FXML
    private VBox messagesContainer;
    @FXML
    private HBox messageInputBox;
    @FXML
    private TextField messageInput;
    @FXML
    private Label statusLabel;

    private SupportTicketDTO selectedTicket;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, HH:mm");

    @FXML
    public void initialize() {
        // Custom cell factory for ticket list
        ticketListView.setCellFactory(lv -> new ListCell<SupportTicketDTO>() {
            @Override
            protected void updateItem(SupportTicketDTO ticket, boolean empty) {
                super.updateItem(ticket, empty);
                if (empty || ticket == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    VBox cell = new VBox(5);
                    cell.setPadding(new Insets(10));
                    cell.setStyle("-fx-background-color: #16213e; -fx-background-radius: 10;");

                    Label subject = new Label(ticket.getSubject());
                    subject.setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;");

                    Label status = new Label(ticket.getStatusDisplay() + " • " +
                            dateFormat.format(ticket.getCreatedAt()));
                    status.setStyle("-fx-text-fill: #888; -fx-font-size: 11px;");

                    cell.getChildren().addAll(subject, status);
                    setGraphic(cell);
                    setStyle("-fx-background-color: transparent;");
                }
            }
        });

        // Selection listener
        ticketListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadTicketDetails(newVal.getId());
            }
        });

        // Load tickets
        refreshTickets();
    }

    @FXML
    private void refreshTickets() {
        statusLabel.setText("Loading tickets...");

        new Thread(() -> {
            try {
                GCMClient client = GCMClient.getInstance();
                Request request = new Request(MessageType.GET_MY_TICKETS);
                request.setUserId(getCurrentUserId());

                Response response = client.sendRequestSync(request);

                Platform.runLater(() -> {
                    if (response.isOk()) {
                        @SuppressWarnings("unchecked")
                        List<SupportTicketDTO> tickets = (List<SupportTicketDTO>) response.getPayload();
                        ticketListView.getItems().clear();
                        ticketListView.getItems().addAll(tickets);
                        statusLabel.setText(tickets.size() + " ticket(s)");
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
                Request request = new Request(MessageType.GET_TICKET_DETAILS, ticketId);
                request.setUserId(getCurrentUserId());

                Response response = client.sendRequestSync(request);

                Platform.runLater(() -> {
                    if (response.isOk()) {
                        selectedTicket = (SupportTicketDTO) response.getPayload();
                        displayTicket(selectedTicket);
                        statusLabel.setText("Ticket #" + ticketId + " loaded");
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
        ticketSubjectLabel.setText(ticket.getSubject());
        ticketStatusLabel.setText(ticket.getStatusDisplay() + " • Created: " +
                dateFormat.format(ticket.getCreatedAt()));

        // Update button visibility based on status
        boolean isClosed = ticket.getStatus() == SupportTicketDTO.Status.CLOSED;
        boolean isEscalated = ticket.getStatus() == SupportTicketDTO.Status.ESCALATED;

        escalateBtn.setVisible(!isClosed && !isEscalated);
        closeBtn.setVisible(!isClosed);
        messageInputBox.setVisible(!isClosed);

        // Display messages
        messagesContainer.getChildren().clear();

        if (ticket.getMessages() != null) {
            for (TicketMessageDTO msg : ticket.getMessages()) {
                messagesContainer.getChildren().add(createMessageBubble(msg));
            }
        }

        // Scroll to bottom
        Platform.runLater(() -> messagesScrollPane.setVvalue(1.0));
    }

    private VBox createMessageBubble(TicketMessageDTO msg) {
        VBox bubble = new VBox(5);
        bubble.setPadding(new Insets(10, 15, 10, 15));
        bubble.setMaxWidth(500);

        // Style based on sender type
        String bgColor, textColor, alignment;
        switch (msg.getSenderType()) {
            case CUSTOMER:
                bgColor = "#00b894";
                textColor = "white";
                alignment = "CENTER_RIGHT";
                break;
            case BOT:
                bgColor = "#3498db";
                textColor = "white";
                alignment = "CENTER_LEFT";
                break;
            case AGENT:
                bgColor = "#9b59b6";
                textColor = "white";
                alignment = "CENTER_LEFT";
                break;
            default:
                bgColor = "#333";
                textColor = "white";
                alignment = "CENTER_LEFT";
        }

        bubble.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 15;");

        Label senderLabel = new Label(msg.getSenderDisplay());
        senderLabel.setStyle("-fx-text-fill: " + textColor + "; -fx-font-size: 10px; -fx-font-weight: bold;");

        Label messageLabel = new Label(msg.getMessage());
        messageLabel.setWrapText(true);
        messageLabel.setStyle("-fx-text-fill: " + textColor + "; -fx-font-size: 13px;");

        Label timeLabel = new Label(dateFormat.format(msg.getCreatedAt()));
        timeLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.7); -fx-font-size: 9px;");

        bubble.getChildren().addAll(senderLabel, messageLabel, timeLabel);

        // Wrap in HBox for alignment
        HBox wrapper = new HBox(bubble);
        wrapper.setAlignment("CENTER_RIGHT".equals(alignment) ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        VBox container = new VBox(wrapper);
        return container;
    }

    @FXML
    private void showNewTicketDialog() {
        Dialog<CreateTicketRequest> dialog = new Dialog<>();
        dialog.setTitle("New Support Ticket");
        dialog.initModality(Modality.APPLICATION_MODAL);

        // Create form
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: #f8f9fa;");

        TextField subjectField = new TextField();
        subjectField.setPromptText("Subject");
        subjectField.setStyle("-fx-font-size: 14px; -fx-padding: 10;");

        TextArea messageArea = new TextArea();
        messageArea.setPromptText("Describe your issue...");
        messageArea.setPrefRowCount(5);
        messageArea.setWrapText(true);
        messageArea.setStyle("-fx-font-size: 14px;");

        ComboBox<SupportTicketDTO.Priority> priorityBox = new ComboBox<>();
        priorityBox.getItems().addAll(SupportTicketDTO.Priority.values());
        priorityBox.setValue(SupportTicketDTO.Priority.MEDIUM);
        priorityBox.setStyle("-fx-font-size: 13px;");

        Label subjectLabel = new Label("Subject:");
        subjectLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        Label descLabel = new Label("Description:");
        descLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        Label priorityLabel = new Label("Priority:");
        priorityLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        content.getChildren().addAll(
                subjectLabel,
                subjectField,
                descLabel,
                messageArea,
                priorityLabel,
                priorityBox);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(450);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                return new CreateTicketRequest(
                        subjectField.getText(),
                        messageArea.getText(),
                        priorityBox.getValue());
            }
            return null;
        });

        dialog.showAndWait().ifPresent(this::createTicket);
    }

    private void createTicket(CreateTicketRequest ticketReq) {
        if (ticketReq.getSubject() == null || ticketReq.getSubject().trim().isEmpty() ||
                ticketReq.getMessage() == null || ticketReq.getMessage().trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Validation Error", "Please fill in all fields");
            return;
        }

        statusLabel.setText("Creating ticket...");

        new Thread(() -> {
            try {
                GCMClient client = GCMClient.getInstance();
                Request request = new Request(MessageType.CREATE_TICKET, ticketReq);
                request.setUserId(getCurrentUserId());

                Response response = client.sendRequestSync(request);

                Platform.runLater(() -> {
                    if (response.isOk()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> result = (Map<String, Object>) response.getPayload();
                        SupportTicketDTO ticket = (SupportTicketDTO) result.get("ticket");
                        boolean wasDuplicate = (Boolean) result.get("wasDuplicate");

                        if (wasDuplicate) {
                            showAlert(Alert.AlertType.INFORMATION, "Similar Ticket Found",
                                    "Your message was added to an existing ticket: " + ticket.getSubject());
                        } else {
                            showAlert(Alert.AlertType.INFORMATION, "Ticket Created",
                                    "Your support ticket has been created. Our bot will respond shortly.");
                        }

                        refreshTickets();

                        // Select the new/updated ticket
                        Platform.runLater(() -> {
                            for (SupportTicketDTO t : ticketListView.getItems()) {
                                if (t.getId() == ticket.getId()) {
                                    ticketListView.getSelectionModel().select(t);
                                    break;
                                }
                            }
                        });

                    } else {
                        showAlert(Alert.AlertType.ERROR, "Error", response.getErrorMessage());
                    }
                    statusLabel.setText("Ready");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.ERROR, "Error", "Connection failed");
                    statusLabel.setText("Connection error");
                });
            }
        }).start();
    }

    @FXML
    private void escalateTicket() {
        if (selectedTicket == null)
            return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Escalate Ticket");
        confirm.setHeaderText("Escalate to Human Agent?");
        confirm.setContentText("This will assign a support agent to your ticket.");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            statusLabel.setText("Escalating ticket...");

            new Thread(() -> {
                try {
                    GCMClient client = GCMClient.getInstance();
                    Request request = new Request(MessageType.ESCALATE_TICKET, selectedTicket.getId());
                    request.setUserId(getCurrentUserId());

                    Response response = client.sendRequestSync(request);

                    Platform.runLater(() -> {
                        if (response.isOk()) {
                            showAlert(Alert.AlertType.INFORMATION, "Escalated",
                                    "Your ticket has been escalated. An agent will respond soon.");
                            loadTicketDetails(selectedTicket.getId());
                            refreshTickets();
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
    }

    @FXML
    private void closeTicket() {
        if (selectedTicket == null)
            return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Close Ticket");
        confirm.setHeaderText("Close this ticket?");
        confirm.setContentText("This will mark the ticket as resolved.");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            statusLabel.setText("Closing ticket...");

            new Thread(() -> {
                try {
                    GCMClient client = GCMClient.getInstance();
                    Request request = new Request(MessageType.CLOSE_TICKET, selectedTicket.getId());
                    request.setUserId(getCurrentUserId());

                    Response response = client.sendRequestSync(request);

                    Platform.runLater(() -> {
                        if (response.isOk()) {
                            showAlert(Alert.AlertType.INFORMATION, "Closed",
                                    "Your ticket has been closed. Thank you for contacting support!");
                            loadTicketDetails(selectedTicket.getId());
                            refreshTickets();
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
    }

    @FXML
    private void sendMessage() {
        if (selectedTicket == null)
            return;
        String message = messageInput.getText().trim();
        if (message.isEmpty())
            return;

        // For now, creating a new ticket is the only way customer can add messages
        // This could be extended to add messages to existing escalated tickets
        messageInput.clear();
        showAlert(Alert.AlertType.INFORMATION, "Note",
                "To add more information, please create a new ticket or escalate this one.");
    }

    @FXML
    private void goBack() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/dashboard.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) ticketListView.getScene().getWindow();
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
