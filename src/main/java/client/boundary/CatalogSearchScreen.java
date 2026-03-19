package client.boundary;

import client.MenuNavigationHelper;
import client.control.SearchControl;
import common.dto.CitySearchResult;
import common.dto.CustomerProfileDTO;
import common.dto.MapSummary;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;
import client.LoginController;

/**
 * Controller for the Catalog Search screen.
 * Provides search functionality without requiring login.
 */
public class CatalogSearchScreen implements SearchControl.SearchResultCallback {

    @FXML
    private RadioButton cityModeRadio;
    @FXML
    private RadioButton poiModeRadio;
    @FXML
    private RadioButton bothModeRadio;
    @FXML
    private ToggleGroup searchModeGroup;

    @FXML
    private VBox cityInputBox;
    @FXML
    private VBox poiInputBox;
    @FXML
    private TextField citySearchField;
    @FXML
    private TextField poiSearchField;

    @FXML
    private Button searchButton;
    @FXML
    private Button showAllButton;
    @FXML
    private Button backButton;
    @FXML
    private VBox guestDashboardPane;
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
    @FXML
    private WebView navbarLogoView;

    @FXML
    private Label statusLabel;
    @FXML
    private Label resultCountLabel;

    @FXML
    private ListView<CitySearchResult> resultsListView;
    @FXML
    private ListView<MapSummary> mapsListView;

    @FXML
    private VBox detailsCard;
    @FXML
    private VBox emptyState;
    @FXML
    private VBox mapDetailsBox;

    @FXML
    private Label cityNameLabel;
    @FXML
    private Label cityDescLabel;
    @FXML
    private Label priceLabel;
    @FXML
    private Label mapNameLabel;
    @FXML
    private Label mapDescLabel;
    @FXML
    private Label poiCountLabel;
    @FXML
    private Label tourCountLabel;

    private SearchControl searchControl;
    private ObservableList<CitySearchResult> searchResults;
    private ObservableList<MapSummary> mapsList;
    private static final String BACK_BTN_BASE_STYLE =
            "-fx-background-color: transparent; -fx-border-color: transparent; -fx-text-fill: #7f8c8d; -fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 10; -fx-padding: 6 10;";
    private static final String BACK_BTN_HOVER_STYLE =
            "-fx-background-color: transparent; -fx-border-color: transparent; -fx-text-fill: #111111; -fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 10; -fx-padding: 6 10;";
    private static final String BACK_BTN_PRESSED_STYLE =
            "-fx-background-color: transparent; -fx-border-color: transparent; -fx-text-fill: #111111; -fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 10; -fx-padding: 6 10;";
    private static final String GUEST_NAV_BTN_BASE_STYLE =
            "-fx-background-color: white; -fx-text-fill: #00712d; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 10 14; -fx-cursor: hand; -fx-alignment: center-left;";
    private static final String GUEST_NAV_BTN_HOVER_STYLE =
            "-fx-background-color: #d5ed9f; -fx-text-fill: #00712d; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 10 14; -fx-cursor: hand; -fx-alignment: center-left;";
    private Button activeGuestNavButton;
    private static final String NAVBAR_LOGO_SVG_RESOURCE = "/client/assets/favicon.svg";

    @FXML
    public void initialize() {
        applyNavbarLogoSvg();
        if (backButton != null) {
            backButton.setStyle(BACK_BTN_BASE_STYLE);
        }
        MenuNavigationHelper.configureSidebarButtons(mapEditorNavBtn, myPurchasesNavBtn, profileNavBtn, customersNavBtn, pricingNavBtn, pricingApprovalNavBtn, supportNavBtn, agentConsoleNavBtn, editApprovalsNavBtn, reportsNavBtn, userManagementNavBtn);
        searchResults = FXCollections.observableArrayList();
        mapsList = FXCollections.observableArrayList();

        resultsListView.setItems(searchResults);
        mapsListView.setItems(mapsList);

        // Custom cell factory for results list
        resultsListView.setCellFactory(lv -> new ListCell<CitySearchResult>() {
            @Override
            protected void updateItem(CitySearchResult item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    setText(item.getCityName() + " (" + item.getTotalMaps() + " maps)");
                    if (isSelected()) {
                        setStyle(
                                "-fx-text-fill: #2c3e50; -fx-font-size: 14px; -fx-padding: 10; -fx-background-color: #e5e7eb; -fx-background-radius: 4;");
                    } else {
                        setStyle(
                                "-fx-text-fill: #2c3e50; -fx-font-size: 14px; -fx-padding: 10; -fx-background-color: transparent;");
                    }
                }
            }
        });

        // Custom cell factory for maps list
        mapsListView.setCellFactory(lv -> new ListCell<MapSummary>() {
            @Override
            protected void updateItem(MapSummary item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    setText(item.getName());
                    if (isSelected()) {
                        setStyle("-fx-text-fill: #2c3e50; -fx-font-size: 13px; -fx-padding: 8; -fx-background-color: #dfe4ea; -fx-background-radius: 4;");
                    } else {
                        setStyle("-fx-text-fill: #2c3e50; -fx-font-size: 13px; -fx-padding: 8; -fx-background-color: transparent;");
                    }
                }
            }
        });

        // Selection listeners
        resultsListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    showCityDetails(newVal);
                    resultsListView.refresh();
                });

        mapsListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> showMapDetails(newVal));

        // Search mode change listeners
        searchModeGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            updateSearchInputs();
        });

        // Enter key triggers search
        citySearchField.setOnAction(e -> handleSearch());
        if (poiSearchField != null) {
            poiSearchField.setOnAction(e -> handleSearch());
        }

        // Connect to server
        connectToServer();
    }

    private void setActiveGuestNavButton(Button button) {
        if (button == null) {
            return;
        }
        if (activeGuestNavButton != null && activeGuestNavButton != button) {
            activeGuestNavButton.setStyle(GUEST_NAV_BTN_BASE_STYLE);
        }
        activeGuestNavButton = button;
        activeGuestNavButton.setStyle(GUEST_NAV_BTN_HOVER_STYLE);
    }

    private void applyNavbarLogoSvg() {
        if (navbarLogoView == null) {
            return;
        }
        java.net.URL svgUrl = getClass().getResource(NAVBAR_LOGO_SVG_RESOURCE);
        if (svgUrl == null) {
            navbarLogoView.setVisible(false);
            navbarLogoView.setManaged(false);
            return;
        }
        try {
            navbarLogoView.getEngine().load(svgUrl.toExternalForm());
        } catch (Exception e) {
            navbarLogoView.setVisible(false);
            navbarLogoView.setManaged(false);
        }
    }

    private void connectToServer() {
        try {
            searchControl = new SearchControl("localhost", 5555);
            searchControl.setResultCallback(this);
            updateStatus("Connected to server. Ready to search!", "#27ae60");
        } catch (IOException e) {
            updateStatus("Could not connect to server. Is it running?", "#e74c3c");
            searchButton.setDisable(true);
            showAllButton.setDisable(true);
        }
    }

    private void updateSearchInputs() {
        boolean showCity = cityModeRadio.isSelected() || bothModeRadio.isSelected();
        boolean showPoi = poiModeRadio.isSelected() || bothModeRadio.isSelected();

        cityInputBox.setVisible(showCity);
        cityInputBox.setManaged(showCity);
        poiInputBox.setVisible(showPoi);
        poiInputBox.setManaged(showPoi);

        if (!showCity)
            citySearchField.clear();
        if (!showPoi && poiSearchField != null)
            poiSearchField.clear();
    }

    @FXML
    private void handleSearch() {
        if (searchControl == null) {
            updateStatus("Not connected to server", "#e74c3c");
            return;
        }

        String cityName = citySearchField.getText().trim();
        String poiName = poiSearchField != null ? poiSearchField.getText().trim() : "";

        updateStatus("Searching...", "#667eea");

        if (cityModeRadio.isSelected()) {
            if (cityName.isEmpty()) {
                updateStatus("Please enter a city name", "#e74c3c");
                return;
            }
            searchControl.searchByCityName(cityName);
        } else if (poiModeRadio.isSelected()) {
            if (poiName.isEmpty()) {
                updateStatus("Please enter a POI name", "#e74c3c");
                return;
            }
            searchControl.searchByPoiName(poiName);
        } else { // bothModeRadio
            if (cityName.isEmpty() && poiName.isEmpty()) {
                updateStatus("Please enter at least one search term", "#e74c3c");
                return;
            }
            searchControl.searchByCityAndPoi(cityName, poiName);
        }
    }

    @FXML
    private void handleShowAll() {
        if (searchControl == null) {
            updateStatus("Not connected to server", "#e74c3c");
            return;
        }

        updateStatus("Loading catalog...", "#667eea");
        searchControl.getCatalog();
    }

    @FXML
    private void handleBack() {
        try {
            // Disconnect from server
            if (searchControl != null) {
                searchControl.disconnect();
            }

            // Determine where to go based on login state
            String targetFxml = "/client/dashboard.fxml";
            String title = "GCM Dashboard";
            int width = 1000;
            int height = 700;

            String username = client.LoginController.getCurrentUsername();
            if (username == null || username.isEmpty()) {
                // Not logged in (accessed via "Browse Catalog") -> Go back to Login
                targetFxml = "/client/login.fxml";
                title = "GCM Login";
                width = 500;
                height = 600;
            }

            // Load Target
            FXMLLoader loader = new FXMLLoader(getClass().getResource(targetFxml));
            Parent root = loader.load();

            Stage stage = (Stage) backButton.getScene().getWindow();
            stage.setScene(new Scene(root, width, height));
            stage.setTitle(title);
            stage.centerOnScreen();
            javafx.application.Platform.runLater(() -> stage.setMaximized(true));

        } catch (IOException e) {
            e.printStackTrace();
            updateStatus("Error returning to previous screen", "#e74c3c");
        }
    }

    @FXML
    private void toggleGuestDashboard(ActionEvent event) {
        if (guestDashboardPane == null) {
            return;
        }
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
    private void handleGuestNavHoverEnter(MouseEvent event) {
        if (event.getSource() instanceof Button button) {
            button.setStyle(GUEST_NAV_BTN_HOVER_STYLE);
        }
    }

    @FXML
    private void handleGuestNavHoverExit(MouseEvent event) {
        if (event.getSource() instanceof Button button) {
            if (button != activeGuestNavButton) {
                button.setStyle(GUEST_NAV_BTN_BASE_STYLE);
            }
        }
    }

    @FXML
    private void handleBackHoverEnter(MouseEvent event) {
        if (event.getSource() instanceof Button button) {
            button.setStyle(BACK_BTN_HOVER_STYLE);
        }
    }

    @FXML
    private void handleBackHoverExit(MouseEvent event) {
        if (event.getSource() instanceof Button button) {
            button.setStyle(BACK_BTN_BASE_STYLE);
        }
    }

    @FXML
    private void handleBackMousePressed(MouseEvent event) {
        if (event.getSource() instanceof Button button) {
            button.setStyle(BACK_BTN_PRESSED_STYLE);
        }
    }

    @FXML
    private void handleBackMouseReleased(MouseEvent event) {
        if (event.getSource() instanceof Button button) {
            button.setStyle(button.isHover() ? BACK_BTN_HOVER_STYLE : BACK_BTN_BASE_STYLE);
        }
    }

    @FXML
    private VBox purchaseBox;

    private boolean isEligibleForDiscount = false;
    private Label discountMessageLabel;
    private boolean isRenewalEligible = false;

    private Button activeSubscriptionButton;
    private int activeSubscriptionMonths;
    private double activeSubscriptionOriginalPrice;
    private int activeDisplayedCityId = -1;

    private void showCityDetails(CitySearchResult city) {
        if (city == null) {
            detailsCard.setVisible(false);
            detailsCard.setManaged(false);
            emptyState.setVisible(true);
            emptyState.setManaged(true);
            mapDetailsBox.setVisible(false);
            mapDetailsBox.setManaged(false);
            return;
        }

        emptyState.setVisible(false);
        emptyState.setManaged(false);
        detailsCard.setVisible(true);
        detailsCard.setManaged(true);

        cityNameLabel.setText(city.getCityName());
        cityDescLabel
                .setText(city.getCityDescription() != null ? city.getCityDescription() : "No description available");
        priceLabel.setText(String.format("$%.2f", city.getCityPrice()));
        activeDisplayedCityId = city.getCityId();

        // Reset discount state for the newly displayed city.
        isEligibleForDiscount = false;
        isRenewalEligible = false;
        if (discountMessageLabel != null) {
            discountMessageLabel.setVisible(false);
            discountMessageLabel.setManaged(false);
        }

        // Maps
        mapsList.clear();
        mapsList.addAll(city.getMaps());
        mapDetailsBox.setVisible(false);
        mapDetailsBox.setManaged(false);

        // Purchase Buttons (Phase 5)
        // Only show if not just browsing (conceptually, though here we allow clicking
        // but it might fail if not logged in)
        // We will make it visible and rely on server rejection if anonymous.
        purchaseBox.setVisible(true);
        purchaseBox.setManaged(true);
        purchaseBox.getChildren().clear();

        Label lbl = new Label("Purchase Options:");
        lbl.setStyle("-fx-text-fill: #2c3e50; -fx-font-weight: bold;");
        purchaseBox.getChildren().add(lbl);

        // Precalculate dynamic prices based on backend formula
        double basePrice = city.getCityPrice();
        double price1m = basePrice * 0.80;
        double price3m = basePrice * 2.10;
        double price6m = basePrice * 3.60;

        // Purchase Options Horizontal Layout
        HBox optionsContainer = new HBox(15);
        optionsContainer.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        String commonBtnStyle = "-fx-font-size: 14px; -fx-padding: 8 15; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 4;";

        // One Time Option
        Button buyBtn = new Button(String.format("Buy One-Time ($%.2f)", basePrice));
        buyBtn.setStyle("-fx-background-color: #00712d; " + commonBtnStyle);
        buyBtn.setPrefHeight(45);
        buyBtn.setMaxWidth(Double.MAX_VALUE);
        buyBtn.setOnAction(e -> showPaymentDialog(city.getCityId(), 0, basePrice, city.getCityName()));
        javafx.scene.layout.HBox.setHgrow(buyBtn, javafx.scene.layout.Priority.ALWAYS);

        // Subscription Option
        HBox subRow = new HBox(8);
        subRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        subRow.setMaxWidth(Double.MAX_VALUE);
        javafx.scene.layout.HBox.setHgrow(subRow, javafx.scene.layout.Priority.ALWAYS);

        ComboBox<Integer> monthsLink = new ComboBox<>(FXCollections.observableArrayList(1, 3, 6));
        monthsLink.setValue(1);
        monthsLink.setPrefWidth(65);
        monthsLink.setPrefHeight(45);
        monthsLink.setStyle("-fx-font-size: 14px;");

        Button subBtn = new Button(String.format("Subscribe for %d %s ($%.2f)",
                monthsLink.getValue(),
                monthsLink.getValue() == 1 ? "month" : "months",
                price1m));
        subBtn.setStyle("-fx-background-color: #00712d; " + commonBtnStyle);
        subBtn.setPrefHeight(45);
        subBtn.setMaxWidth(Double.MAX_VALUE);
        javafx.scene.layout.HBox.setHgrow(subBtn, javafx.scene.layout.Priority.ALWAYS);
        activeSubscriptionButton = subBtn;
        activeSubscriptionMonths = monthsLink.getValue();
        activeSubscriptionOriginalPrice = price1m;
        subBtn.setOnAction(e -> {
            int m = monthsLink.getValue();
            double p = m == 1 ? price1m : (m == 3 ? price3m : price6m);
            showPaymentDialog(city.getCityId(), m, p, city.getCityName());
        });

        // Update button text and trigger eligibility check dynamically when combobox
        // changes
        monthsLink.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                double p = newVal == 1 ? price1m : (newVal == 3 ? price3m : price6m);
                // Reset to non-renew until server confirms eligibility.
                isEligibleForDiscount = false;
                isRenewalEligible = false;
                if (discountMessageLabel != null) {
                    discountMessageLabel.setVisible(false);
                    discountMessageLabel.setManaged(false);
                }
                activeSubscriptionMonths = newVal;
                activeSubscriptionOriginalPrice = p;
                subBtn.setText(String.format("Subscribe for %d %s ($%.2f)",
                        newVal,
                        newVal == 1 ? "month" : "months",
                        p));

                // Trigger discount check if logged in
                if (!client.LoginController.isAnonymousUser() && searchControl != null) {
                    searchControl.checkDiscountEligibility(city.getCityId(), newVal);
                }
            }
        });

        subRow.getChildren().addAll(subBtn, monthsLink);

        optionsContainer.getChildren().addAll(buyBtn, subRow);
        purchaseBox.getChildren().add(optionsContainer);

        // Add a placeholder label for the discount message below the buttons
        discountMessageLabel = new Label();
        discountMessageLabel.setStyle("-fx-text-fill: #f1c40f; -fx-font-weight: bold; -fx-padding: 10 0 0 0;");
        discountMessageLabel.setVisible(false);
        discountMessageLabel.setManaged(false);
        purchaseBox.getChildren().add(discountMessageLabel);

        // Trigger initial discount check for the default 1-month selection
        if (!LoginController.isAnonymousUser() && searchControl != null) {
            searchControl.checkDiscountEligibility(city.getCityId(), monthsLink.getValue());
        }
    }

    private void showPaymentDialog(int cityId, int months, double originalPrice, String cityName) {
        if (LoginController.isAnonymousUser()) {
            updateStatus("Guests cannot purchase - Please log in first", "#e74c3c");
            return;
        }
        if (searchControl == null) {
            updateStatus("Not connected to server", "#e74c3c");
            return;
        }
        updateStatus("Loading payment options...", "#667eea");
        searchControl.getMyProfile(profile -> Platform.runLater(() -> {
            updateStatus("", "#2c3e50");
            showPaymentDialogWithProfile(cityId, months, originalPrice, cityName, profile);
        }));
    }

    private void showPaymentDialogWithProfile(int cityId, int months, double originalPrice, String cityName,
            CustomerProfileDTO profile) {
        Stage dialog = new Stage();
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dialog.setTitle("Payment Simulation");

        VBox root = new VBox(15);
        root.setStyle("-fx-background-color: #f5f6fa; -fx-padding: 20; -fx-border-color: #dcdfe3; -fx-border-width: 1;");
        root.setPrefWidth(400);

        // Header
        Label title = new Label("Secure Checkout");
        title.setStyle("-fx-font-size: 20px; -fx-text-fill: #2c3e50; -fx-font-weight: bold;");

        // Summary
        VBox summary = new VBox(5);
        summary.setStyle("-fx-background-color: white; -fx-padding: 10; -fx-background-radius: 8; -fx-border-color: #e3e6ea; -fx-border-radius: 8;");
        String typeStr = months == 0 ? "One-Time Purchase" : months + "-Month Subscription";
        Label sumCity = new Label("City: " + cityName);
        Label sumType = new Label("Type: " + typeStr);
        sumCity.setStyle("-fx-text-fill: #2c3e50;");
        sumType.setStyle("-fx-text-fill: #2c3e50;");

        summary.getChildren().addAll(sumCity, sumType);

        if (months > 0 && isEligibleForDiscount) {
            Label origPriceLbl = new Label(String.format("Original Price: $%.2f", originalPrice));
            origPriceLbl.setStyle("-fx-text-fill: #7f8c8d; -fx-strikethrough: true;");

            Label discountLbl = new Label("Renewal Discount: -10%");
            discountLbl.setStyle("-fx-text-fill: #2ecc71; -fx-font-weight: bold;");

            double finalPrice = originalPrice * 0.90;
            Label sumPrice = new Label(String.format("Final Price: $%.2f", finalPrice));
            sumPrice.setStyle("-fx-text-fill: #f1c40f; -fx-font-weight: bold; -fx-font-size: 14px;");

            summary.getChildren().addAll(origPriceLbl, discountLbl, sumPrice);
        } else {
            Label sumPrice = new Label(String.format("Total Price: $%.2f", originalPrice));
            sumPrice.setStyle("-fx-text-fill: #f1c40f; -fx-font-weight: bold; -fx-font-size: 14px;");
            summary.getChildren().add(sumPrice);
        }

        root.getChildren().add(title);
        root.getChildren().add(summary);

        // Only show saved card if we have real last4 (not masked e.g. for managers)
        boolean hasSavedCard = profile != null && profile.getCardLast4() != null && !profile.getCardLast4().isEmpty()
                && !"****".equals(profile.getCardLast4());
        String savedExpiry = hasSavedCard && profile.getCardExpiry() != null ? profile.getCardExpiry() : "";

        // Form container (for new card entry). When saved card exists, hide and exclude from
        // layout so dialog stays compact; when user clicks "Use different card", show and resize.
        VBox formBox = new VBox(15);
        formBox.setVisible(!hasSavedCard);
        formBox.setManaged(!hasSavedCard);

        // Form fields (declared before if-block so "Use different card" can reference rememberCardBox)
        TextField cardField = new TextField();
        cardField.setPromptText("Credit Card Number (13-16 digits)");
        cardField.setStyle("-fx-background-color: white; -fx-text-fill: #2c3e50; -fx-prompt-text-fill: #95a5a6;");

        TextField idField = new TextField();
        idField.setPromptText("Israeli ID (9 digits)");
        idField.setStyle("-fx-background-color: white; -fx-text-fill: #2c3e50; -fx-prompt-text-fill: #95a5a6;");

        HBox row = new HBox(10);
        TextField expField = new TextField();
        expField.setPromptText("Expiry MM/YY");
        expField.setPrefWidth(120);
        expField.setStyle("-fx-background-color: white; -fx-text-fill: #2c3e50; -fx-prompt-text-fill: #95a5a6;");

        TextField cvvField = new TextField();
        cvvField.setPromptText("CVV (3 digits)");
        cvvField.setPrefWidth(100);
        cvvField.setStyle("-fx-background-color: white; -fx-text-fill: #2c3e50; -fx-prompt-text-fill: #95a5a6;");
        row.getChildren().addAll(expField, cvvField);

        CheckBox rememberCardBox = new CheckBox("Remember this card");
        rememberCardBox.setStyle("-fx-text-fill: #2c3e50;");

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #e74c3c;");

        HBox btnBox = new HBox(10);
        btnBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white;");
        cancelBtn.setOnAction(e -> dialog.close());

        Button payBtn = new Button("Pay Now");
        payBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;");
        payBtn.setOnAction(e -> {
            String validationError = validatePayment(cardField.getText(), idField.getText(), expField.getText(),
                    cvvField.getText());
            if (validationError != null) {
                errorLabel.setText(validationError);
            } else {
                dialog.close();
                boolean saveCard = rememberCardBox.isSelected();
                String cardNumber = cardField.getText().trim();
                String cardLast4 = cardNumber.length() > 4 ? cardNumber.substring(cardNumber.length() - 4) : cardNumber;
                String cardExpiry = expField.getText().trim();
                handlePurchase(cityId, months, saveCard, cardLast4, cardExpiry);
            }
        });

        btnBox.getChildren().addAll(cancelBtn, payBtn);

        if (hasSavedCard) {
            VBox savedCardBox = new VBox(8);
            savedCardBox.setStyle("-fx-background-color: #e8f5e9; -fx-padding: 12; -fx-background-radius: 8; -fx-border-color: #c8e6c9; -fx-border-radius: 8;");
            Label savedCardLabel = new Label(String.format("Saved card: **** **** **** %s  (Exp: %s)", profile.getCardLast4(), savedExpiry));
            savedCardLabel.setStyle("-fx-text-fill: #2e7d32; -fx-font-size: 14px;");
            HBox savedCardBtnRow = new HBox(10);
            Button payWithSavedBtn = new Button("Pay with saved card");
            payWithSavedBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;");
            payWithSavedBtn.setOnAction(e -> {
                dialog.close();
                handlePurchase(cityId, months, true, profile.getCardLast4(), savedExpiry);
            });
            Button useDifferentBtn = new Button("Use different card");
            useDifferentBtn.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white;");
            useDifferentBtn.setOnAction(e -> {
                savedCardBox.setVisible(false);
                savedCardBox.setManaged(false);
                formBox.setVisible(true);
                formBox.setManaged(true);
                rememberCardBox.setSelected(true);
                Platform.runLater(dialog::sizeToScene);
            });
            savedCardBtnRow.getChildren().addAll(payWithSavedBtn, useDifferentBtn);
            savedCardBox.getChildren().addAll(savedCardLabel, savedCardBtnRow);
            root.getChildren().add(savedCardBox);
        }

        formBox.getChildren().addAll(cardField, idField, row, rememberCardBox, errorLabel, btnBox);
        root.getChildren().add(formBox);

        Scene scene = new Scene(root);
        dialog.setScene(scene);
        dialog.sizeToScene();
        dialog.showAndWait();
    }

    private String validatePayment(String card, String id, String exp, String cvv) {
        if (card == null || !card.matches("\\d{13,16}"))
            return "Card must be 13-16 digits.";
        if (id == null || !id.matches("\\d{9}"))
            return "ID must be exactly 9 digits.";
        if (cvv == null || !cvv.matches("\\d{3}"))
            return "CVV must be exactly 3 digits.";
        if (exp == null || !exp.matches("(0[1-9]|1[0-2])/\\d{2}"))
            return "Expiry must be MM/YY.";

        try {
            String[] parts = exp.split("/");
            int month = Integer.parseInt(parts[0]);
            int year = Integer.parseInt(parts[1]) + 2000;
            java.time.YearMonth current = java.time.YearMonth.now();
            java.time.YearMonth expYield = java.time.YearMonth.of(year, month);
            if (expYield.isBefore(current))
                return "Card is expired.";
        } catch (Exception e) {
            return "Invalid expiry date.";
        }

        // Basic Luhn (optional, but implemented for robust validation)
        int sum = 0;
        boolean alternate = false;
        for (int i = card.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(card.substring(i, i + 1));
            if (alternate) {
                n *= 2;
                if (n > 9)
                    n = (n % 10) + 1;
            }
            sum += n;
            alternate = !alternate;
        }
        if (sum % 10 != 0)
            return "Invalid credit card number (Luhn check failed).";

        return null;
    }

    private void handlePurchase(int cityId, int months, boolean saveCard, String cardLast4, String cardExpiry) {
        // Guest restriction: Block guests from purchasing (Phase 5 fix)
        if (LoginController.isAnonymousUser()) {
            updateStatus("Guests cannot purchase - Please log in first", "#e74c3c");
            return;
        }

        // Send request (include session token so server can identify the customer)
        String token = LoginController.currentSessionToken;
        if (token == null || token.isEmpty()) {
            updateStatus("Session expired - Please log in again", "#e74c3c");
            return;
        }
        try {
            common.Request req;
            if (months == 0) {
                req = new common.Request(common.MessageType.PURCHASE_ONE_TIME,
                        new common.dto.PurchaseRequest(cityId, saveCard, cardLast4, cardExpiry),
                        token);
            } else {
                req = new common.Request(common.MessageType.PURCHASE_SUBSCRIPTION,
                        new common.dto.PurchaseRequest(cityId, months, saveCard, cardLast4, cardExpiry), token);
            }
            if (searchControl != null) {
                searchControl.sendPurchaseRequest(req);
                updateStatus("Payment success! Purchase complete.", "#27ae60");
            }
        } catch (Exception e) {
            updateStatus("Purchase failed: " + e.getMessage(), "red");
        }
    }

    private void showMapDetails(MapSummary map) {
        if (map == null) {
            mapDetailsBox.setVisible(false);
            mapDetailsBox.setManaged(false);
            return;
        }

        mapDetailsBox.setVisible(true);
        mapDetailsBox.setManaged(true);

        mapNameLabel.setText(map.getName());
        mapDescLabel.setText(map.getShortDescription() != null ? map.getShortDescription() : "No description");
        poiCountLabel.setText("📍 " + map.getPoiCount() + " Points of Interest");
        tourCountLabel.setText("🚶 " + map.getTourCount() + " Tours");
    }

    private void updateStatus(String message, String color) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            statusLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px;");
        });
    }

    // ==================== SearchResultCallback Implementation ====================

    @Override
    public void onSearchResults(List<CitySearchResult> results) {
        Platform.runLater(() -> {
            searchResults.clear();
            searchResults.addAll(results);

            if (results.isEmpty()) {
                updateStatus("No results found. Try a different search term.", "#f39c12");
                resultCountLabel.setText("0 results");
            } else {
                int totalMaps = results.stream().mapToInt(CitySearchResult::getTotalMaps).sum();
                updateStatus("Found " + results.size() + " cities with " + totalMaps + " maps", "#27ae60");
                resultCountLabel.setText(results.size() + " cities, " + totalMaps + " maps");
            }

            // Clear selection
            resultsListView.getSelectionModel().clearSelection();
            showCityDetails(null);
        });
    }

    @Override
    public void onDiscountEligibility(common.dto.DiscountEligibilityResponse response) {
        Platform.runLater(() -> {
            if (response == null) return;

            // Ignore eligibility responses that don't match the currently-visible city/month selection.
            if (response.getCityId() != activeDisplayedCityId || response.getMonths() != activeSubscriptionMonths) {
                return;
            }

            this.isEligibleForDiscount = response.isDiscountEligible();
            this.isRenewalEligible = response.isRenewalEligible();

            if (discountMessageLabel != null) {
                if (isEligibleForDiscount) {
                    discountMessageLabel.setText("You are eligible for a 10% renewal discount on this subscription.");
                    discountMessageLabel.setVisible(true);
                    discountMessageLabel.setManaged(true);
                } else {
                    discountMessageLabel.setVisible(false);
                    discountMessageLabel.setManaged(false);
                }
            }

            // Toggle the button label: subscribe vs renew for the selected duration.
            if (activeSubscriptionButton != null) {
                int m = activeSubscriptionMonths;
                double original = activeSubscriptionOriginalPrice;
                double shownPrice = isEligibleForDiscount ? original * 0.90 : original;
                activeSubscriptionButton.setText(
                        isRenewalEligible
                                ? String.format("Renew subscription for %d %s ($%.2f)",
                                        m, m == 1 ? "month" : "months", shownPrice)
                                : String.format("Subscribe for %d %s ($%.2f)",
                                        m, m == 1 ? "month" : "months", original));
            }
        });
    }

    @Override
    public void onError(String errorCode, String errorMessage) {
        Platform.runLater(() -> {
            updateStatus("Error: " + errorMessage, "#e74c3c");
            resultCountLabel.setText("Error");
        });
    }
}
