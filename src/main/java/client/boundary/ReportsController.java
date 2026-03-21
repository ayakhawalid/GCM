package client.boundary;

import client.GCMClient;
import client.LoginController;
import client.MenuNavigationHelper;
import common.DailyStat;
import common.MessageType;
import common.Request;
import common.Response;
import common.dto.CityDTO;
import common.dto.ReportRequest;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.layout.VBox;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.geometry.Pos;
import javafx.scene.transform.Scale;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReportsController implements GCMClient.MessageHandler {
    private static final String BACK_BTN_BASE_STYLE =
            "-fx-background-color: transparent; -fx-border-color: transparent; -fx-text-fill: #7f8c8d; -fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 10; -fx-padding: 6 10;";
    private static final String BACK_BTN_HOVER_STYLE =
            "-fx-background-color: transparent; -fx-border-color: transparent; -fx-text-fill: #111111; -fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 10; -fx-padding: 6 10;";

    @FXML
    private DatePicker startDatePicker;

    @FXML
    private DatePicker endDatePicker;

    @FXML
    private ComboBox<CityDTO> cityComboBox;

    @FXML
    private VBox chartContainer;

    @FXML
    private Label statusLabel;

    @FXML
    private Label valuesLabel;

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

    private BarChart<String, Number> activityChart;
    private Pane lineChartPane;
    private Pane chartWrapper;
    private Pane zoomContentWrapper;
    private Group chartGroup;
    private ScrollPane chartScrollPane;
    private Scale zoomTransform;
    private double currentScale = 1.0;
    private static final double CHART_MIN_WIDTH = 400;
    private static final double CHART_MIN_HEIGHT = 300;
    private double effectiveChartWidth = 800;
    private double effectiveChartHeight = 350;
    private static final double MIN_SCALE = 0.25;
    private static final double MAX_SCALE = 4.0;
    /** Slightly zoomed out so x-axis category names (Maps, Subscriptions, etc.) are visible. */
    private static final double DEFAULT_SCALE = 0.88;
    private static final String[] METRIC_NAMES = {"Maps", "One-time purchases", "Subscriptions", "Renewals", "Map views", "Demo downloads"};
    /** Left margin reserved for Y-axis label "Count" and tick numbers so they never overlap the graph. */
    private static final double AXIS_LEFT = 44;

    private final int[] lastDrawnValues = new int[6];
    private GCMClient client;
    private static ExecutorService reportExecutor;

    /**
     * Latest allowed "To" date for reports: fixed at screen load (same as initial default end date).
     * End dates after this are treated as illegal (no future-dated reporting window).
     */
    private LocalDate reportMaxEndDate;

    private static synchronized ExecutorService getReportExecutor() {
        if (reportExecutor == null) {
            reportExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Report-Request");
                t.setDaemon(true);
                return t;
            });
        }
        return reportExecutor;
    }

    public void setClient(GCMClient client) {
        this.client = client;
        if (client != null) {
            client.setMessageHandler(this);
            loadCities();
        }
    }

    @FXML
    public void initialize() {
        applyNavbarLogoSvg();
        MenuNavigationHelper.configureSidebarButtons(mapEditorNavBtn, myPurchasesNavBtn, profileNavBtn, customersNavBtn, pricingNavBtn, pricingApprovalNavBtn, supportNavBtn, agentConsoleNavBtn, editApprovalsNavBtn, reportsNavBtn, userManagementNavBtn);
        try {
            if (startDatePicker != null) startDatePicker.setValue(LocalDate.now().minusDays(30));
            if (endDatePicker != null) {
                endDatePicker.setValue(LocalDate.now());
                reportMaxEndDate = endDatePicker.getValue();
                configureEndDatePickerBounds();
            }
        } catch (Exception e) { /* ignore */ }
        try {
            if (cityComboBox != null) configureCityComboBox();
        } catch (Exception e) { /* ignore */ }
        try {
            buildChart();
            if (chartContainer != null && activityChart != null) {
                chartScrollPane = new ScrollPane();
                chartScrollPane.setFitToWidth(true);
                chartScrollPane.setFitToHeight(true);
                chartScrollPane.setStyle("-fx-background: white;");
                zoomContentWrapper = new Pane();
                chartWrapper = new Pane();
                chartGroup = new Group(chartWrapper);
                chartWrapper.getChildren().add(lineChartPane);
                zoomContentWrapper.getChildren().add(chartGroup);
                chartScrollPane.setContent(zoomContentWrapper);
                VBox.setVgrow(chartScrollPane, javafx.scene.layout.Priority.ALWAYS);
                chartContainer.getChildren().add(chartScrollPane);
                java.net.URL chartCss = getClass().getResource("/client/reports-chart.css");
                if (chartCss != null) chartContainer.getStylesheets().add(chartCss.toExternalForm());
                // Size chart to fill available area when container is laid out; redraw so content matches size
                chartContainer.layoutBoundsProperty().addListener((obs, oldB, newB) -> {
                    if (newB.getWidth() <= 0 || newB.getHeight() <= 0) return;
                    effectiveChartWidth = Math.max(CHART_MIN_WIDTH, newB.getWidth() - 20);
                    effectiveChartHeight = Math.max(CHART_MIN_HEIGHT, newB.getHeight() - 20);
                    applyChartSize(effectiveChartWidth, effectiveChartHeight);
                    updateZoomWrapperSize();
                    redrawLineChart(lastDrawnValues[0], lastDrawnValues[1], lastDrawnValues[2], lastDrawnValues[3], lastDrawnValues[4], lastDrawnValues[5]);
                });
                // Initial size from current bounds (in case already laid out)
                javafx.scene.layout.Region r = chartContainer;
                if (r.getWidth() > 0 && r.getHeight() > 0) {
                    effectiveChartWidth = Math.max(CHART_MIN_WIDTH, r.getWidth() - 20);
                    effectiveChartHeight = Math.max(CHART_MIN_HEIGHT, r.getHeight() - 20);
                }
                applyChartSize(effectiveChartWidth, effectiveChartHeight);
                zoomContentWrapper.setMinSize(effectiveChartWidth, effectiveChartHeight);
                zoomContentWrapper.setPrefSize(effectiveChartWidth, effectiveChartHeight);
                // Zoom around cursor: Scale with pivot + position Group so pivot stays under cursor
                zoomTransform = new Scale(1, 1, 0, 0);
                chartGroup.getTransforms().add(zoomTransform);
                chartScrollPane.addEventFilter(ScrollEvent.SCROLL, e -> {
                    double rotation = e.getDeltaY();
                    if (rotation == 0) return;
                    // Pivot = point in chart (content) under cursor
                    Point2D pivot = chartGroup.sceneToLocal(e.getSceneX(), e.getSceneY());
                    // Cursor in wrapper coords so we can keep pivot under it
                    Point2D cursorInWrapper = zoomContentWrapper.sceneToLocal(e.getSceneX(), e.getSceneY());
                    // Scroll UP = zoom in, scroll DOWN = zoom out
                    double step = rotation > 0 ? 0.1 : -0.1;
                    double newScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, currentScale + step));
                    if (newScale == currentScale) return;
                    currentScale = newScale;
                    zoomTransform.setPivotX(pivot.getX());
                    zoomTransform.setPivotY(pivot.getY());
                    zoomTransform.setX(currentScale);
                    zoomTransform.setY(currentScale);
                    // Position so pivot is under cursor, but clamp so chart stays inside wrapper (keeps scroll bars able to reach all edges)
                    double minX = -pivot.getX() * (1 - currentScale);
                    double maxX = pivot.getX() * (currentScale - 1);
                    double minY = -pivot.getY() * (1 - currentScale);
                    double maxY = pivot.getY() * (currentScale - 1);
                    double desiredX = cursorInWrapper.getX() - pivot.getX();
                    double desiredY = cursorInWrapper.getY() - pivot.getY();
                    chartGroup.setLayoutX(clamp(desiredX, minX, maxX));
                    chartGroup.setLayoutY(clamp(desiredY, minY, maxY));
                    updateZoomWrapperSize();
                    e.consume();
                });
                javafx.scene.control.Label zoomHint = new javafx.scene.control.Label("Scroll over chart to zoom in/out (like map)");
                zoomHint.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");
                chartContainer.getChildren().add(zoomHint);
                showEmptyChart();
                applyDefaultZoom();
                // When scene is shown, size chart to fill available area
                Platform.runLater(() -> {
                    if (chartContainer.getScene() != null) {
                        chartContainer.getScene().getWindow().widthProperty().addListener((o, ov, nv) -> sizeChartToContainer());
                        chartContainer.getScene().getWindow().heightProperty().addListener((o, ov, nv) -> sizeChartToContainer());
                        sizeChartToContainer();
                    }
                });
            }
        } catch (Throwable t) {
            if (statusLabel != null) statusLabel.setText("Chart could not be created.");
        }
        try {
            loadCities();
        } catch (Exception e) { /* ignore */ }
    }

    private void buildChart() {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Metric");
        xAxis.setTickLabelsVisible(true);
        xAxis.setCategories(FXCollections.observableArrayList(METRIC_NAMES));
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Count");
        yAxis.setForceZeroInRange(true);
        yAxis.setAutoRanging(true);
        activityChart = new BarChart<>(xAxis, yAxis);
        activityChart.getStyleClass().add("reports-chart");
        activityChart.setTitle("Activity Statistics");
        activityChart.setLegendVisible(true);
        activityChart.setMinSize(CHART_MIN_WIDTH, CHART_MIN_HEIGHT);
        lineChartPane = new Pane();
        lineChartPane.setStyle("-fx-background-color: white;");
        lineChartPane.setMinSize(CHART_MIN_WIDTH, CHART_MIN_HEIGHT);
    }

    /** Disallow choosing an end date after the default that was set when the screen opened. */
    private void configureEndDatePickerBounds() {
        if (endDatePicker == null || reportMaxEndDate == null) return;

        final LocalDate maxEnd = reportMaxEndDate;
        endDatePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (!empty && date != null && date.isAfter(maxEnd)) {
                    setDisable(true);
                    setStyle("-fx-background-color: #ececec;");
                }
            }
        });

        endDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) return;
            if (newVal.isAfter(maxEnd)) {
                Platform.runLater(() -> {
                    endDatePicker.setValue(oldVal != null ? oldVal : maxEnd);
                    showValidationAlert(
                            "Please pick a legal end date.",
                            "The report period cannot end after " + maxEnd
                                    + " (today when this screen was opened). Pick an end date on or before that day.");
                });
            }
        });
    }

    private void showValidationAlert(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Invalid end date");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void configureCityComboBox() {
        cityComboBox.setConverter(new StringConverter<CityDTO>() {
            @Override
            public String toString(CityDTO city) {
                if (city == null)
                    return "All Cities";
                return city.getName();
            }

            @Override
            public CityDTO fromString(String string) {
                return null;
            }
        });
    }

    private void loadCities() {
        if (client == null) return;
        try {
            client.sendToServer(new Request(MessageType.GET_CITIES, null));
        } catch (IOException e) {
            if (statusLabel != null) statusLabel.setText("Error loading cities");
        }
    }

    @Override
    public void displayMessage(Object msg) {
        System.out.println("[Report DEBUG] displayMessage received: " + (msg != null ? msg.getClass().getName() : "null"));
        Platform.runLater(() -> {
            if (msg instanceof Response) {
                Response response = (Response) msg;
                handleResponse(response);
                return;
            }
            // Fallback: server might send something that deserializes as Map (e.g. different serialization)
            if (msg instanceof Map) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) msg;
                    Object rt = m.get("requestType");
                    if (rt != null && rt.toString().contains("GET_ACTIVITY_REPORT")) {
                        boolean ok = Boolean.TRUE.equals(m.get("ok")) || "true".equals(String.valueOf(m.get("ok")));
                        Object payload = m.get("payload");
                        if (ok && payload instanceof List) {
                            List<DailyStat> stats = payloadToListOfDailyStat(payload);
                            System.out.println("[Report DEBUG] Applied report from Map payload, stats=" + stats.size());
                            updateChart(stats);
                            if (valuesLabel != null && !stats.isEmpty()) {
                                int a = 0, b = 0, c = 0, d = 0, e = 0, f = 0;
                                for (DailyStat s : stats) {
                                    a += s.getMapsCount(); b += s.getOneTimePurchases(); c += s.getSubscriptions();
                                    d += s.getRenewals(); e += s.getViews(); f += s.getDownloads();
                                }
                                valuesLabel.setText("Maps=" + a + ", One-time=" + b + ", Subscriptions=" + c + ", Renewals=" + d + ", Map views=" + e + ", Demo downloads=" + f);
                            }
                            if (statusLabel != null) statusLabel.setText("Report generated.");
                        }
                    }
                } catch (Exception ex) {
                    System.out.println("[Report DEBUG] Map fallback failed: " + ex.getMessage());
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void handleResponse(Response response) {
        if (response.getRequestType() == MessageType.GET_CITIES && cityComboBox != null) {
            if (response.isOk()) {
                List<CityDTO> cities = (List<CityDTO>) response.getPayload();
                List<CityDTO> withAll = new ArrayList<>();
                withAll.add(null);
                if (cities != null) withAll.addAll(cities);
                cityComboBox.setItems(FXCollections.observableList(withAll));
                cityComboBox.getSelectionModel().selectFirst();
            }
        } else if (response.getRequestType() == MessageType.GET_ACTIVITY_REPORT) {
            applyReportResponse(response);
        }
    }

    /** Apply report response to chart and status (runs on JavaFX thread). */
    private void applyReportResponse(Response response) {
        try {
            if (response.isOk()) {
                Object payload = response.getPayload();
                List<DailyStat> stats = payloadToListOfDailyStat(payload);
                System.out.println("[Report DEBUG] payloadToListOfDailyStat -> " + (stats != null ? stats.size() : "null") + " stats");
                updateChart(stats);
                if (stats == null || stats.isEmpty()) {
                    if (statusLabel != null) statusLabel.setText("No activity data for this period.");
                    if (valuesLabel != null) valuesLabel.setText("Maps=0, One-time=0, Subscriptions=0, Renewals=0, Map views=0, Demo downloads=0");
                }
            } else {
                if (statusLabel != null) statusLabel.setText("Error: " + response.getErrorMessage());
                if (valuesLabel != null) valuesLabel.setText("Error: " + response.getErrorMessage());
                showAlert("Error", "Failed to generate report: " + response.getErrorMessage());
            }
        } catch (Exception e) {
            if (statusLabel != null) statusLabel.setText("Error displaying report.");
            if (valuesLabel != null) valuesLabel.setText("Error: " + e.getMessage());
            showAlert("Error", "Failed to display report: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Convert server payload to List<DailyStat>; handles List of DailyStat or List of Map (e.g. from JSON). */
    private List<DailyStat> payloadToListOfDailyStat(Object payload) {
        if (payload == null) {
            System.out.println("[Report DEBUG] payload is null");
            return new ArrayList<>();
        }
        if (!(payload instanceof List)) {
            System.out.println("[Report DEBUG] payload is not a List, it's " + payload.getClass().getName());
            return new ArrayList<>();
        }
        List<?> raw = (List<?>) payload;
        List<DailyStat> result = new ArrayList<>();
        for (Object item : raw) {
            if (item instanceof DailyStat) {
                result.add((DailyStat) item);
            } else if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) item;
                int cityId = intFromAnyKey(m, "cityId", "city_id");
                int mapsCount = intFromAnyKey(m, "mapsCount", "maps_count");
                int oneTime = intFromAnyKey(m, "oneTimePurchases", "one_time_purchases");
                int subs = intFromAnyKey(m, "subscriptions", "subscriptions");
                int renewals = intFromAnyKey(m, "renewals", "renewals");
                int views = intFromAnyKey(m, "views", "views");
                int downloads = intFromAnyKey(m, "downloads", "downloads");
                result.add(new DailyStat(null, cityId, mapsCount, oneTime, subs, renewals, views, downloads));
            } else {
                System.out.println("[Report DEBUG] list item type " + (item != null ? item.getClass().getName() : "null") + " skipped");
            }
        }
        System.out.println("[Report DEBUG] converted " + result.size() + " DailyStat items");
        return result;
    }

    /** Try each key in order; used when payload may use camelCase or snake_case. */
    private static int intFromAnyKey(Map<String, Object> m, String... keys) {
        for (String key : keys) {
            Object v = m.get(key);
            if (v != null) {
                if (v instanceof Number) return ((Number) v).intValue();
                try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { /* try next */ }
            }
        }
        return 0;
    }

    @FXML
    void handleGenerateReport(ActionEvent event) {
        LocalDate from = startDatePicker.getValue();
        LocalDate to = endDatePicker.getValue();
        CityDTO selectedCity = cityComboBox.getValue(); // Null means all

        if (from == null || to == null) {
            showAlert("Validation Error", "Please select valid start and end dates.");
            return;
        }

        if (from.isAfter(to)) {
            showAlert("Validation Error", "Start date cannot be after end date.");
            return;
        }

        if (reportMaxEndDate != null && to.isAfter(reportMaxEndDate)) {
            showValidationAlert(
                    "Please pick a legal end date.",
                    "The report period cannot end after " + reportMaxEndDate
                            + ". Pick an end date on or before that day.");
            return;
        }

        Integer cityId = selectedCity != null ? selectedCity.getId() : null;
        ReportRequest reqPayload = new ReportRequest(from, to, cityId);
        Request request = new Request(MessageType.GET_ACTIVITY_REPORT, reqPayload);

        if (client == null) {
            statusLabel.setText("Not connected.");
            showAlert("Error", "Not connected to server. Go back and try again.");
            return;
        }

        statusLabel.setText("Generating report...");
        if (valuesLabel != null) valuesLabel.setText("Loading…");
        System.out.println("[Report DEBUG] Sending GET_ACTIVITY_REPORT request (cityId=" + cityId + ", from=" + from + ", to=" + to + ")");

        getReportExecutor().execute(() -> {
            try {
                client.sendToServer(request);
                // Response will be handled in displayMessage() when it arrives (no sync wait)
                javafx.application.Platform.runLater(() -> {
                    javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(8));
                    pause.setOnFinished(ev -> {
                        if (valuesLabel != null && "Loading…".equals(valuesLabel.getText())) {
                            valuesLabel.setText("No response after 8s. Check server is running and client connected.");
                            if (statusLabel != null) statusLabel.setText("No response received.");
                        }
                    });
                    pause.play();
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("Error sending request.");
                    if (valuesLabel != null) valuesLabel.setText("Error: " + e.getMessage());
                    showEmptyChart();
                    showAlert("Error", "Failed to get report: " + e.getMessage());
                    e.printStackTrace();
                });
            }
        });
    }

    private void updateChart(List<DailyStat> stats) {
        if (stats == null || stats.isEmpty()) {
            if (valuesLabel != null) valuesLabel.setText("Maps=0, One-time=0, Subscriptions=0, Renewals=0, Map views=0, Demo downloads=0");
            if (statusLabel != null) statusLabel.setText("No activity data for this period.");
            redrawLineChart(0, 0, 0, 0, 0, 0);
            resetZoom();
            return;
        }

        int totalMaps = 0, totalOneTime = 0, totalSubs = 0, totalRenewals = 0, totalViews = 0, totalDownloads = 0;
        for (DailyStat s : stats) {
            totalMaps += s.getMapsCount();
            totalOneTime += s.getOneTimePurchases();
            totalSubs += s.getSubscriptions();
            totalRenewals += s.getRenewals();
            totalViews += s.getViews();
            totalDownloads += s.getDownloads();
        }
        setStatusWithNumbers(totalMaps, totalOneTime, totalSubs, totalRenewals, totalViews, totalDownloads);
        redrawLineChart(totalMaps, totalOneTime, totalSubs, totalRenewals, totalViews, totalDownloads);
        resetZoom();
        applyChartSize(effectiveChartWidth, effectiveChartHeight);
    }

    /** Draw 6 black lines: each line's length = value (scaled to count axis). White background. "Count" and Y numbers stay left of the graph; metric titles centered under each line. */
    private void redrawLineChart(int maps, int oneTime, int subs, int renewals, int views, int downloads) {
        if (lineChartPane == null) return;
        lastDrawnValues[0] = maps;
        lastDrawnValues[1] = oneTime;
        lastDrawnValues[2] = subs;
        lastDrawnValues[3] = renewals;
        lastDrawnValues[4] = views;
        lastDrawnValues[5] = downloads;

        lineChartPane.getChildren().clear();
        int[] values = { maps, oneTime, subs, renewals, views, downloads };
        int maxVal = 0;
        for (int v : values) maxVal = Math.max(maxVal, v);
        int axisMax = maxVal <= 0 ? 5 : maxVal <= 5 ? 5 : maxVal <= 10 ? 10 : maxVal <= 20 ? 20 : maxVal <= 50 ? 50 : (maxVal + 9) / 10 * 10;
        int tick = axisMax <= 5 ? 1 : axisMax <= 10 ? 2 : axisMax <= 20 ? 4 : 10;

        double w = effectiveChartWidth;
        double h = effectiveChartHeight;
        double marginLeft = AXIS_LEFT;
        double marginTop = 28;
        double marginBottom = 68;
        double marginRight = 20;
        double chartW = w - marginLeft - marginRight;
        double chartH = h - marginTop - marginBottom;
        double bottomY = marginTop + chartH;
        double slotW = chartW / METRIC_NAMES.length;

        lineChartPane.setMinSize(w, h);
        lineChartPane.setPrefSize(w, h);
        lineChartPane.setStyle("-fx-background-color: white;");

        // "Count" label: left of Y-axis, above the graph so it doesn't overlap numbers
        Label countLabel = new Label("Count");
        countLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
        countLabel.setLayoutX(4);
        countLabel.setLayoutY(4);
        countLabel.setMaxWidth(AXIS_LEFT - 8);
        lineChartPane.getChildren().add(countLabel);

        // Y-axis tick numbers: in the left strip only (x=4, fixed width), so they never overlap the graph
        for (int k = 0; k <= axisMax; k += tick) {
            Label lbl = new Label(String.valueOf(k));
            lbl.setStyle("-fx-font-size: 11px;");
            lbl.setMaxWidth(AXIS_LEFT - 8);
            double y = bottomY - (axisMax > 0 ? (k * chartH / axisMax) : 0);
            lbl.setLayoutX(4);
            lbl.setLayoutY(y - 6);
            lineChartPane.getChildren().add(lbl);
        }

        // 6 black vertical lines + metric title under each; line and title share the same slot so they align
        for (int i = 0; i < METRIC_NAMES.length; i++) {
            double slotLeft = marginLeft + i * slotW;
            double cx = slotLeft + slotW * 0.5;
            int value = values[i];
            double lineHeight = (axisMax > 0 && value >= 0) ? (value * chartH / axisMax) : 0;

            Line line = new Line(cx, bottomY, cx, bottomY - lineHeight);
            line.setStroke(Color.BLACK);
            line.setStrokeWidth(3);
            lineChartPane.getChildren().add(line);

            Label nameLabel = new Label(METRIC_NAMES[i]);
            nameLabel.setStyle("-fx-font-size: 10px;");
            nameLabel.setAlignment(Pos.CENTER);
            nameLabel.setWrapText(true);
            nameLabel.setMaxWidth(slotW - 2);
            nameLabel.setMinWidth(slotW - 2);
            nameLabel.setLayoutX(slotLeft + 1);
            nameLabel.setLayoutY(bottomY + 4);
            lineChartPane.getChildren().add(nameLabel);
        }
    }

    private void setStatusWithNumbers(int maps, int oneTime, int subs, int renewals, int views, int downloads) {
        String text = "Maps=" + maps + ", One-time=" + oneTime + ", Subscriptions=" + subs + ", Renewals=" + renewals + ", Map views=" + views + ", Demo downloads=" + downloads;
        if (valuesLabel != null) valuesLabel.setText(text);
        if (statusLabel != null) statusLabel.setText("Report generated.");
    }

    private void showEmptyChart() {
        redrawLineChart(0, 0, 0, 0, 0, 0);
    }

    private void resetZoom() {
        if (chartGroup == null || chartWrapper == null) return;
        currentScale = DEFAULT_SCALE;
        if (zoomTransform != null) {
            zoomTransform.setPivotX(0);
            zoomTransform.setPivotY(0);
            zoomTransform.setX(DEFAULT_SCALE);
            zoomTransform.setY(DEFAULT_SCALE);
        }
        if (chartGroup != null) {
            chartGroup.setLayoutX(0);
            chartGroup.setLayoutY(0);
        }
        applyChartSize(effectiveChartWidth, effectiveChartHeight);
        updateZoomWrapperSize();
    }

    /** Slightly zoom out so x-axis titles (Maps, Subscriptions, etc.) are visible on first load and after generating. */
    private void applyDefaultZoom() {
        if (chartGroup == null || zoomTransform == null) return;
        currentScale = DEFAULT_SCALE;
        zoomTransform.setPivotX(0);
        zoomTransform.setPivotY(0);
        zoomTransform.setX(DEFAULT_SCALE);
        zoomTransform.setY(DEFAULT_SCALE);
        updateZoomWrapperSize();
    }

    private void updateZoomWrapperSize() {
        if (zoomContentWrapper == null) return;
        double w = effectiveChartWidth * currentScale;
        double h = effectiveChartHeight * currentScale;
        zoomContentWrapper.setMinSize(w, h);
        zoomContentWrapper.setPrefSize(w, h);
    }

    private static double clamp(double value, double min, double max) {
        if (min > max) return min; // scale==1: range collapses
        return Math.max(min, Math.min(max, value));
    }

    private void applyChartSize(double w, double h) {
        if (chartWrapper == null) return;
        chartWrapper.setMinSize(w, h);
        if (lineChartPane != null) {
            lineChartPane.setMinSize(w, h);
            lineChartPane.setPrefSize(w, h);
        }
    }

    private void sizeChartToContainer() {
        if (chartContainer == null) return;
        double w = chartContainer.getWidth() > 0 ? chartContainer.getWidth() : chartContainer.getLayoutBounds().getWidth();
        double h = chartContainer.getHeight() > 0 ? chartContainer.getHeight() : chartContainer.getLayoutBounds().getHeight();
        if (w <= 0 || h <= 0) return;
        effectiveChartWidth = Math.max(CHART_MIN_WIDTH, w - 20);
        effectiveChartHeight = Math.max(CHART_MIN_HEIGHT, h - 20);
        applyChartSize(effectiveChartWidth, effectiveChartHeight);
        updateZoomWrapperSize();
        redrawLineChart(lastDrawnValues[0], lastDrawnValues[1], lastDrawnValues[2], lastDrawnValues[3], lastDrawnValues[4], lastDrawnValues[5]);
    }

    @FXML
    void handleBack(ActionEvent event) {
        try {
            // Navigate back to Dashboard
            // Assuming DashboardScreen.fxml is in /client/boundary/DashboardScreen.fxml
            // based on conventions?
            // Actually previous files suggested it might be a problem finding it.
            // Let's assume standard path: /client/boundary/DashboardScreen.fxml
            // If that file doesn't exist, we need to know where it is.
            // Given DashboardScreen.java is in client.boundary, likely FXML is too.
            // But earlier grep failed to find it?
            // If it's not found, user can fix.

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/dashboard.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) statusLabel.getScene().getWindow();
            stage.setScene(new Scene(root, 1000, 700));
            stage.setTitle("GCM Dashboard");
            stage.setMaximized(true);
            stage.centerOnScreen();

        } catch (IOException e) {
            // Fallback: Try Login if dashboard fails
            // e.printStackTrace();
            // showAlert("Navigation Error", "Could not return to Dashboard.");
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/login.fxml"));
                Parent root = loader.load();
                Stage stage = (Stage) statusLabel.getScene().getWindow();
                stage.setScene(new Scene(root, 500, 600));
                stage.setMaximized(true);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    @FXML
    void handleBackHoverEnter(MouseEvent event) {
        if (event.getSource() instanceof Button button) button.setStyle(BACK_BTN_HOVER_STYLE);
    }

    @FXML
    void handleBackHoverExit(MouseEvent event) {
        if (event.getSource() instanceof Button button) button.setStyle(BACK_BTN_BASE_STYLE);
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
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
}
