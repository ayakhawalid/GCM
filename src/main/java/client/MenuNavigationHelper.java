package client;

import client.boundary.ReportsController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Shared helper for dashboard menu sidebar: role-based button visibility
 * and navigation to all dashboard screens. Use across all pages that show
 * the same sidebar menu.
 */
public final class MenuNavigationHelper {

    private MenuNavigationHelper() {}

    /**
     * Configure visibility of the 11 role-based sidebar buttons.
     * Order: mapEditor, myPurchases, profile, customers, pricing, pricingApproval,
     * support, agentConsole, editApprovals, reports, userManagement.
     * Pass null for any button not present; it will be skipped.
     */
    public static void configureSidebarButtons(
            javafx.scene.control.Button mapEditorNavBtn,
            javafx.scene.control.Button myPurchasesNavBtn,
            javafx.scene.control.Button profileNavBtn,
            javafx.scene.control.Button customersNavBtn,
            javafx.scene.control.Button pricingNavBtn,
            javafx.scene.control.Button pricingApprovalNavBtn,
            javafx.scene.control.Button supportNavBtn,
            javafx.scene.control.Button agentConsoleNavBtn,
            javafx.scene.control.Button editApprovalsNavBtn,
            javafx.scene.control.Button reportsNavBtn,
            javafx.scene.control.Button userManagementNavBtn) {
        LoginController.UserRole role = LoginController.currentUserRole;
        String exactRole = LoginController.currentRoleString;
        boolean isManager = role == LoginController.UserRole.MANAGER;
        boolean isCompanyManager = "COMPANY_MANAGER".equals(exactRole);
        boolean isSupportAgent = role == LoginController.UserRole.SUPPORT_AGENT;
        boolean isCustomer = role == LoginController.UserRole.CUSTOMER;
        boolean isAnonymous = role == LoginController.UserRole.ANONYMOUS;

        setVisible(mapEditorNavBtn, !isCustomer && !isAnonymous);
        setVisible(myPurchasesNavBtn, !isAnonymous);
        setVisible(profileNavBtn, !isAnonymous);
        setVisible(customersNavBtn, isManager);
        setVisible(pricingNavBtn, isManager);
        setVisible(pricingApprovalNavBtn, isCompanyManager);
        setVisible(supportNavBtn, !isAnonymous);
        setVisible(agentConsoleNavBtn, isSupportAgent);
        setVisible(editApprovalsNavBtn, isManager);
        setVisible(reportsNavBtn, isManager);
        setVisible(userManagementNavBtn, isCompanyManager);
    }

    private static void setVisible(javafx.scene.control.Button btn, boolean show) {
        if (btn != null) {
            btn.setVisible(show);
            btn.setManaged(show);
        }
    }

    private static Stage getStage(Node node) {
        if (node == null) return null;
        return node.getScene() != null && node.getScene().getWindow() instanceof Stage
                ? (Stage) node.getScene().getWindow()
                : null;
    }

    private static void navigate(Node node, String fxml, String title) {
        Stage stage = getStage(node);
        if (stage == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(LoginController.class.getResource(fxml));
            Parent root = loader.load();
            stage.setScene(new Scene(root));
            stage.setTitle(title);
            stage.setMaximized(true);
            stage.centerOnScreen();
        } catch (IOException e) {
            throw new RuntimeException("Failed to navigate to " + fxml, e);
        }
    }

    public static void navigateToDashboard(Node node) {
        navigate(node, "/client/dashboard.fxml", "GCM Dashboard");
    }

    public static void navigateToCatalog(Node node) {
        navigate(node, "/client/catalog_search.fxml", "GCM - City Catalog");
    }

    public static void navigateToMapEditor(Node node) {
        navigate(node, "/client/map_editor.fxml", "GCM - Map Editor");
    }

    public static void navigateToMyPurchases(Node node) {
        navigate(node, "/client/my_purchases.fxml", "My Purchases");
    }

    public static void navigateToProfile(Node node) {
        navigate(node, "/client/profile.fxml", "My Profile");
    }

    public static void navigateToAdminCustomers(Node node) {
        navigate(node, "/client/admin_customers.fxml", "Customer Management");
    }

    public static void navigateToPricing(Node node) {
        navigate(node, "/client/pricing_screen.fxml", "Pricing Management");
    }

    public static void navigateToPricingApproval(Node node) {
        navigate(node, "/client/pricing_approval.fxml", "Pricing Approval");
    }

    public static void navigateToSupport(Node node) {
        navigate(node, "/client/support_screen.fxml", "GCM Support Center");
    }

    public static void navigateToAgentConsole(Node node) {
        Stage stage = getStage(node);
        if (stage == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(LoginController.class.getResource("/client/agent_console.fxml"));
            Parent root = loader.load();
            stage.setScene(new Scene(root, 1000, 700));
            stage.setTitle("Agent Console");
            stage.setMaximized(true);
            stage.centerOnScreen();
        } catch (IOException e) {
            throw new RuntimeException("Failed to open Agent Console", e);
        }
    }

    public static void navigateToEditApprovals(Node node) {
        navigate(node, "/client/map_approvals.fxml", "Edit Request Approvals");
    }

    public static void navigateToReports(Node node) {
        Stage stage = getStage(node);
        if (stage == null) return;
        try {
            java.net.URL reportFxml = ReportsController.class.getResource("/client/reports.fxml");
            if (reportFxml == null) {
                reportFxml = ReportsController.class.getClassLoader().getResource("client/reports.fxml");
            }
            if (reportFxml == null) {
                throw new IOException("reports.fxml not found");
            }
            FXMLLoader loader = new FXMLLoader(reportFxml);
            Parent root = loader.load();
            ReportsController ctrl = loader.getController();
            if (ctrl != null) {
                try {
                    ctrl.setClient(GCMClient.getInstance());
                } catch (IOException e) {
                    System.err.println("MenuNavigationHelper: GCMClient not available for reports: " + e.getMessage());
                }
            }
            stage.setScene(new Scene(root, 900, 700));
            stage.setTitle("GCM - City Reports");
            stage.setMaximized(true);
            stage.centerOnScreen();
        } catch (Exception e) {
            System.err.println("MenuNavigationHelper: Failed to open reports: " + e.getMessage());
            e.printStackTrace();
            javafx.application.Platform.runLater(() -> {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
                alert.setTitle("Cannot open City Reports");
                alert.setHeaderText("Navigation failed");
                alert.setContentText(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                alert.showAndWait();
            });
        }
    }

    public static void navigateToUserManagement(Node node) {
        navigate(node, "/client/user_management.fxml", "User Management");
    }
}
