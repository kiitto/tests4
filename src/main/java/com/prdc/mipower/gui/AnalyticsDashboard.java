package com.prdc.mipower.gui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.StackedBarChart;
import javafx.scene.chart.StackedAreaChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import javax.imageio.ImageIO;

import com.prdc.mipower.models.Branch;
import com.prdc.mipower.models.Bus;
import com.prdc.mipower.models.CaseStudy;
import com.prdc.mipower.models.Out0Results;
import com.prdc.mipower.models.SavedCustomization;
import com.prdc.mipower.parser.Out0Parser;
import com.prdc.mipower.services.CaseStudyManager;
import com.prdc.mipower.services.CustomizationStorage;
import com.prdc.mipower.utils.Constants;

/**
 * Advanced Insight-First Power System Analytics Dashboard with Multi-Case Study Comparison,
 * 12+ Visualizations (including 3D), Snapshots, and Saved Customization Records.
 */
public class AnalyticsDashboard {

    private final List<CaseStudy> caseStudies;
    private final CaseStudyManager manager;

    private Out0Results baseResults;
    private final Map<Integer, Out0Results> solvedCases = new LinkedHashMap<>();
    private final List<CaseStudy> activeSelectedCases = new ArrayList<>();

    private final List<Category> categories = new ArrayList<>();
    private Category currentCategory;

    // UI Components
    private VBox sidebarList;
    private FlowPane categoryOverviewPane;
    private Label multiCaseSummaryLabel;
    private VBox multiCaseSummaryBox;
    private VBox insightHeaderBox;
    private ComboBox<String> chartTypeCombo;
    private StackPane chartHost;
    private FlowPane kpiHost;
    private TableView<MetricRow> table;
    private Label tableCaption;
    private Slider zoomSlider;
    private boolean showValueLabels = true;

    // Multi-Visualization Tabs
    private static class VizTab {
        String name;
        Category category;
        String chartType;
        boolean showValueLabels;
        double zoom;

        VizTab(String name, Category category, String chartType, boolean showValueLabels, double zoom) {
            this.name = name;
            this.category = category;
            this.chartType = chartType;
            this.showValueLabels = showValueLabels;
            this.zoom = zoom;
        }
    }

    private final List<VizTab> vizTabs = new ArrayList<>();
    private int activeTabIndex = 0;
    private HBox tabBarBox;
    private CheckBox showValuesCb;

    // Document Builder (Saved Customization)
    private final List<SavedCustomization.SavedChartItem> documentItems = new ArrayList<>();
    private ListView<String> documentListView;
    private Label docCountLabel;

    public static final String CHART_BAR = "Bar Chart";
    public static final String CHART_STACKED_BAR = "Stacked Bar Chart";
    public static final String CHART_LINE = "Line Chart";
    public static final String CHART_AREA = "Area Chart";
    public static final String CHART_SCATTER = "Scatter Chart";
    public static final String CHART_BUBBLE = "Bubble Chart";
    public static final String CHART_HISTOGRAM = "Histogram / Distribution";
    public static final String CHART_BOX = "Box / Statistical Range";
    public static final String CHART_CORRELATION = "Correlation Plot";
    public static final String CHART_RADAR = "Radar / Spider Chart";
    public static final String CHART_PIE = "Pie / Donut Chart";

    private static final List<String> ALL_CHART_TYPES = List.of(
            CHART_BAR, CHART_STACKED_BAR, CHART_LINE, CHART_AREA,
            CHART_SCATTER, CHART_RADAR, CHART_PIE
    );

    public AnalyticsDashboard(List<CaseStudy> caseStudies, CaseStudyManager manager) {
        this.caseStudies = caseStudies;
        this.manager = manager;
    }

    public void show() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("PRDC MiPower -- Advanced Insight-First Analytics & Multi-Case Comparison");

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        boolean baseReady = manager.baseOut0 != null && new File(manager.baseOut0).exists();
        if (!baseReady) {
            root.setCenter(buildNotReadyPane());
            Scene scene = new Scene(root, 900, 500);
            applyTheme(scene);
            stage.setScene(scene);
            stage.show();
            return;
        }

        try {
            baseResults = Out0Parser.parseFile(manager.baseOut0);
            loadSolvedCaseStudies();
        } catch (Exception ex) {
            root.setCenter(buildErrorPane(ex));
            Scene scene = new Scene(root, 900, 500);
            applyTheme(scene);
            stage.setScene(scene);
            stage.show();
            return;
        }

        buildCategories();

        if (!categories.isEmpty()) {
            vizTabs.add(new VizTab("Sheet 1", categories.get(0), CHART_BAR, true, 1.0));
        }

        root.setTop(buildHeader());

        SplitPane mainSplit = new SplitPane();
        mainSplit.getItems().addAll(buildSidebar(), buildContentArea(), buildDocumentDrawer());
        mainSplit.setDividerPositions(0.20, 0.78);
        root.setCenter(mainSplit);

        if (!categories.isEmpty()) {
            selectCategory(categories.get(0));
        }

        Scene scene = new Scene(root, 1580, 960);
        applyTheme(scene);
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();
    }

    private void loadSolvedCaseStudies() {
        solvedCases.clear();
        for (CaseStudy cs : caseStudies) {
            if (cs.modifiedOut0 != null && new File(cs.modifiedOut0).exists()) {
                try {
                    Out0Results res = Out0Parser.parseFile(cs.modifiedOut0);
                    solvedCases.put(cs.id, res);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void applyTheme(Scene scene) {
        var themeUrl = getClass().getResource("/css/theme.css");
        if (themeUrl != null) {
            scene.getStylesheets().add(themeUrl.toExternalForm());
        }
    }

    private VBox buildNotReadyPane() {
        VBox box = new VBox(10);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        Label icon = new Label("\uD83D\uDCC8");
        icon.setStyle("-fx-font-size: 40px;");
        Label title = new Label("Analytics isn't ready yet");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Label body = new Label("Run MiPower on the Base File first. Every analytic on this dashboard is "
                + "computed directly from the Base Case's solved .out0 results, so a solved Base Case is required "
                + "before anything can be shown here.");
        body.setWrapText(true);
        body.setMaxWidth(560);
        body.setStyle("-fx-font-size: 13px; -fx-text-fill: #6B7280;");
        body.setAlignment(Pos.CENTER);
        box.getChildren().addAll(icon, title, body);
        return box;
    }

    private VBox buildErrorPane(Exception ex) {
        VBox box = new VBox(10);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        Label title = new Label("Unable to read the Base Case results");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #B91C1C;");
        Label body = new Label(String.valueOf(ex.getMessage()));
        body.setWrapText(true);
        body.setMaxWidth(560);
        box.getChildren().addAll(title, body);
        return box;
    }

    // ------------------------------------------------------------------- //
    // Header & Multi-Case Study Checkboxes
    // ------------------------------------------------------------------- //
    private VBox buildHeader() {
        VBox header = new VBox(8);
        header.setPadding(new Insets(14, 20, 14, 20));
        header.setStyle("-fx-background-color: #0F172A; -fx-border-color: #334155; -fx-border-width: 0 0 2 0;");

        HBox topRow = new HBox(16);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("⚡ MiPower Power System Analytics Suite");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #38BDF8;");

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button manageSavedBtn = new Button("📂 Saved Customizations");
        manageSavedBtn.setStyle("-fx-background-color: #1E293B; -fx-text-fill: #F8FAFC; -fx-font-weight: bold; "
                + "-fx-border-color: #475569; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 6 14;");
        manageSavedBtn.setOnAction(e -> openSavedCustomizationsDialog());

        topRow.getChildren().addAll(title, spacer, manageSavedBtn);

        Label subtitle = new Label("Authoritative Base-Case scaling is applied across all metrics. "
                + "Select multiple case studies below to compare against the Base Case.");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #94A3B8;");

        VBox casesSection = new VBox(6);
        casesSection.setPadding(new Insets(6, 10, 6, 10));
        casesSection.setStyle("-fx-background-color: #1E293B; -fx-background-radius: 6; -fx-border-color: #334155; -fx-border-radius: 6;");

        HBox caseTitleRow = new HBox(8);
        caseTitleRow.setAlignment(Pos.CENTER_LEFT);
        Label casesLabel = new Label("📋 Case Studies Comparison (Multi-Selection):");
        casesLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #F1F5F9;");

        Label basePill = new Label("📌 Base Case (Authoritative Reference)");
        basePill.setStyle("-fx-background-color: #2563EB; -fx-text-fill: white; -fx-padding: 2 8; "
                + "-fx-background-radius: 12; -fx-font-size: 11px; -fx-font-weight: bold;");

        caseTitleRow.getChildren().addAll(casesLabel, basePill);

        FlowPane checksPane = new FlowPane(14, 6);
        checksPane.setAlignment(Pos.CENTER_LEFT);

        for (CaseStudy cs : caseStudies) {
            boolean solved = solvedCases.containsKey(cs.id);
            String parentName = (manager.getParent(cs) != null) ? manager.getParent(cs).name : "Base File";
            CheckBox cb = new CheckBox(cs.name);
            cb.setStyle("-fx-text-fill: " + (solved ? "#E2E8F0;" : "#64748B;") + " -fx-font-size: 12px;");
            cb.setDisable(!solved);
            if (!solved) {
                cb.setTooltip(new Tooltip("Run MiPower for this case study to enable comparison."));
            }
            cb.setOnAction(e -> {
                if (cb.isSelected()) {
                    if (!activeSelectedCases.contains(cs)) activeSelectedCases.add(cs);
                } else {
                    activeSelectedCases.remove(cs);
                }
                onCasesSelectionChanged();
            });
            checksPane.getChildren().add(cb);
        }

        if (caseStudies.isEmpty()) {
            Label noCases = new Label("No Case Studies created yet. Create Case Studies in the editor to compare scenarios.");
            noCases.setStyle("-fx-text-fill: #64748B; -fx-font-size: 11.5px;");
            checksPane.getChildren().add(noCases);
        }

        casesSection.getChildren().addAll(caseTitleRow, checksPane);

        multiCaseSummaryBox = new VBox(4);
        multiCaseSummaryBox.setPadding(new Insets(8, 12, 8, 12));
        multiCaseSummaryBox.setStyle("-fx-background-color: #064E3B; -fx-background-radius: 6; -fx-border-color: #059669; -fx-border-radius: 6;");
        Label summaryTitle = new Label("💡 Multi-Case Analysis & Executive Summary");
        summaryTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #34D399;");
        multiCaseSummaryLabel = new Label("Select one or more case studies to generate an automatic cross-case comparative synthesis.");
        multiCaseSummaryLabel.setWrapText(true);
        multiCaseSummaryLabel.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #A7F3D0;");
        multiCaseSummaryBox.getChildren().addAll(summaryTitle, multiCaseSummaryLabel);

        header.getChildren().addAll(topRow, subtitle, casesSection, multiCaseSummaryBox);
        return header;
    }

    private void onCasesSelectionChanged() {
        updateMultiCaseSummary();
        if (currentCategory != null) {
            renderCategory(currentCategory);
        }
    }

    private void updateMultiCaseSummary() {
        if (activeSelectedCases.isEmpty()) {
            multiCaseSummaryLabel.setText("No cases selected. Check one or more case studies above to compare against Base Case.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Comparing ").append(activeSelectedCases.size()).append(" case(s) vs Base Case:\n");

        for (int i = 0; i < activeSelectedCases.size(); i++) {
            CaseStudy cs = activeSelectedCases.get(i);
            Out0Results res = solvedCases.get(cs.id);
            if (res == null) continue;

            double baseLoss = baseResults.summary.getOrDefault("real_loss_mw", 0.0);
            double caseLoss = res.summary.getOrDefault("real_loss_mw", 0.0);
            double deltaLoss = caseLoss - baseLoss;

            long baseOvl = baseResults.branches().stream().filter(Branch::isOverloaded).count();
            long caseOvl = res.branches().stream().filter(Branch::isOverloaded).count();

            long baseViol = baseResults.voltageMinViolations + baseResults.voltageMaxViolations;
            long caseViol = res.voltageMinViolations + res.voltageMaxViolations;

            String lossIcon = deltaLoss <= 0 ? "✅" : "⚠️";
            String ovlIcon  = caseOvl <= baseOvl ? "✅" : "🔴";
            String vIcon    = caseViol <= baseViol ? "✅" : "🔴";

            sb.append(String.format(
                    " [%d] %s  %s Losses: %.2f MW (Δ %+.2f MW)  %s Overloads: %d  %s V-Violations: %d\n",
                    i + 1, cs.name, lossIcon, caseLoss, deltaLoss, ovlIcon, caseOvl, vIcon, caseViol));
        }

        multiCaseSummaryLabel.setText(sb.toString().trim());
    }

    // ------------------------------------------------------------------- //
    // Sidebar: Visual overview & Category list
    // ------------------------------------------------------------------- //
    private ScrollPane buildSidebar() {
        sidebarList = new VBox(6);
        sidebarList.setPadding(new Insets(12));

        Label overviewHeader = new Label("ANALYTICS CATEGORIES");
        overviewHeader.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #64748B; -fx-padding: 0 0 4 4;");
        sidebarList.getChildren().add(overviewHeader);

        categoryOverviewPane = new FlowPane(6, 6);
        categoryOverviewPane.setPadding(new Insets(0, 0, 10, 0));
        for (Category c : categories) {
            Label chip = new Label(c.shortName);
            chip.setStyle("-fx-background-color: #E2E8F0; -fx-text-fill: #334155; -fx-font-size: 10px; "
                    + "-fx-padding: 3 6; -fx-background-radius: 4; -fx-cursor: hand;");
            chip.setOnMouseClicked(e -> selectCategory(c));
            categoryOverviewPane.getChildren().add(chip);
        }
        sidebarList.getChildren().add(categoryOverviewPane);

        String lastGroup = null;
        for (Category c : categories) {
            if (!c.group.equals(lastGroup)) {
                Label groupLabel = new Label(c.group.toUpperCase());
                groupLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #94A3B8; -fx-padding: 10 0 2 4;");
                sidebarList.getChildren().add(groupLabel);
                lastGroup = c.group;
            }
            sidebarList.getChildren().add(buildCategoryButton(c));
        }

        ScrollPane scroll = new ScrollPane(sidebarList);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: #F8FAFC; -fx-border-color: #E2E8F0; -fx-border-width: 0 1 0 0;");
        return scroll;
    }

    private VBox buildCategoryButton(Category c) {
        VBox box = new VBox(2);
        box.setPadding(new Insets(8, 10, 8, 10));
        box.getStyleClass().add("card");
        box.setCursor(javafx.scene.Cursor.HAND);
        Label title = new Label(c.title);
        title.setWrapText(true);
        title.setStyle("-fx-font-size: 12px; -fx-font-weight: 600;");
        box.getChildren().add(title);
        box.setUserData(c);
        box.setOnMouseClicked(e -> selectCategory(c));
        return box;
    }

    private void selectCategory(Category c) {
        currentCategory = c;
        if (activeTabIndex >= 0 && activeTabIndex < vizTabs.size()) {
            vizTabs.get(activeTabIndex).category = c;
        }
        updateSidebarSelection(c);
        updateTabBar();
        renderCategory(c);
    }

    private void updateSidebarSelection(Category c) {
        if (sidebarList == null) return;
        for (Node n : sidebarList.getChildren()) {
            if (n instanceof VBox vb && vb.getUserData() == c) {
                vb.setStyle("-fx-background-color: #E0E7FF; -fx-background-radius: 6; -fx-border-color: #4F46E5; -fx-border-radius: 6; -fx-border-width: 1.5;");
            } else if (n instanceof VBox vb && vb.getUserData() instanceof Category) {
                vb.setStyle("-fx-background-color: white; -fx-background-radius: 6; -fx-border-color: #E2E8F0; -fx-border-radius: 6; -fx-border-width: 1;");
            }
        }
    }

    private void updateTabBar() {
        if (tabBarBox == null) return;
        tabBarBox.getChildren().clear();

        for (int i = 0; i < vizTabs.size(); i++) {
            final int index = i;
            VizTab tab = vizTabs.get(i);
            boolean isActive = (i == activeTabIndex);

            HBox tabBtn = new HBox(6);
            tabBtn.setAlignment(Pos.CENTER_LEFT);
            tabBtn.setCursor(javafx.scene.Cursor.HAND);

            String labelText = tab.name + (tab.category != null ? ": " + tab.category.shortName : "");
            Label tabLabel = new Label(labelText);

            if (isActive) {
                tabBtn.setStyle("-fx-background-color: #FFFFFF; -fx-padding: 6 12 6 12; -fx-background-radius: 6 6 0 0; "
                        + "-fx-border-color: #10B981 #CBD5E1 #FFFFFF #CBD5E1; -fx-border-width: 3 1 2 1; -fx-border-radius: 6 6 0 0;");
                tabLabel.setStyle("-fx-font-size: 11.5px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");
            } else {
                tabBtn.setStyle("-fx-background-color: #E2E8F0; -fx-padding: 6 10 6 10; -fx-background-radius: 6 6 0 0; "
                        + "-fx-border-color: #CBD5E1; -fx-border-width: 1 1 1 1; -fx-border-radius: 6 6 0 0;");
                tabLabel.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 500; -fx-text-fill: #64748B;");
            }

            tabBtn.getChildren().add(tabLabel);

            if (vizTabs.size() > 1) {
                Button closeBtn = new Button("×");
                closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #94A3B8; -fx-font-size: 12px; -fx-padding: 0 0 0 2; -fx-cursor: hand;");
                closeBtn.setOnAction(e -> {
                    e.consume();
                    closeTab(index);
                });
                tabBtn.getChildren().add(closeBtn);
            }

            tabBtn.setOnMouseClicked(e -> switchToTab(index));
            tabBarBox.getChildren().add(tabBtn);
        }

        Button addTabBtn = new Button("+");
        addTabBtn.setTooltip(new Tooltip("Add a new visualization sheet/tab"));
        addTabBtn.setStyle("-fx-background-color: #E2E8F0; -fx-text-fill: #2563EB; -fx-font-size: 13px; -fx-font-weight: bold; "
                + "-fx-background-radius: 4; -fx-border-color: #CBD5E1; -fx-border-radius: 4; -fx-padding: 2 8; -fx-cursor: hand;");
        addTabBtn.setOnAction(e -> addNewTab());
        tabBarBox.getChildren().add(addTabBtn);
    }

    private void switchToTab(int index) {
        if (index < 0 || index >= vizTabs.size()) return;
        activeTabIndex = index;
        VizTab tab = vizTabs.get(activeTabIndex);
        currentCategory = tab.category;
        chartTypeCombo.setValue(tab.chartType);
        showValueLabels = tab.showValueLabels;
        if (showValuesCb != null) {
            showValuesCb.setSelected(showValueLabels);
        }
        if (zoomSlider != null) {
            zoomSlider.setValue(tab.zoom);
        }
        updateSidebarSelection(tab.category);
        updateTabBar();
        if (tab.category != null) {
            renderCategory(tab.category);
        }
    }

    private void addNewTab() {
        int newNum = vizTabs.size() + 1;
        Category cat = currentCategory != null ? currentCategory : (categories.isEmpty() ? null : categories.get(0));
        VizTab newTab = new VizTab("Sheet " + newNum, cat, CHART_BAR, true, 1.0);
        vizTabs.add(newTab);
        switchToTab(vizTabs.size() - 1);
    }

    private void closeTab(int index) {
        if (vizTabs.size() <= 1) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Close Chart Visualization");
        alert.setHeaderText(null);
        alert.setContentText("Are you sure you want to close this chart visualization?");

        ButtonType confirmBtn = new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(confirmBtn, cancelBtn);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == confirmBtn) {
            vizTabs.remove(index);
            if (activeTabIndex >= vizTabs.size()) {
                activeTabIndex = vizTabs.size() - 1;
            }
            switchToTab(activeTabIndex);
        }
    }

    // ------------------------------------------------------------------- //
    // Content Area: Insight-First Header, Charting, Interactive Controls & Table
    // ------------------------------------------------------------------- //
    private ScrollPane buildContentArea() {
        VBox content = new VBox(14);
        content.setPadding(new Insets(16));

        insightHeaderBox = new VBox(8);
        insightHeaderBox.getStyleClass().add("card");
        insightHeaderBox.setPadding(new Insets(14));
        insightHeaderBox.setStyle("-fx-background-color: #F0FDF4; -fx-border-color: #BBF7D0; -fx-border-radius: 8; -fx-background-radius: 8;");

        // Excel-styled Top Tabs for Multi-Visualization Sheets
        tabBarBox = new HBox(4);
        tabBarBox.setAlignment(Pos.BOTTOM_LEFT);
        tabBarBox.setPadding(new Insets(6, 8, 0, 8));
        tabBarBox.setStyle("-fx-background-color: #F1F5F9; -fx-border-color: #CBD5E1; -fx-border-width: 0 0 1 0;");
        updateTabBar();

        FlowPane controlsRow = new FlowPane(8, 8);
        controlsRow.setAlignment(Pos.CENTER_LEFT);
        controlsRow.setPadding(new Insets(8, 12, 8, 12));
        controlsRow.setStyle("-fx-background-color: #F8FAFC; -fx-background-radius: 6; -fx-border-color: #CBD5E1; -fx-border-radius: 6;");

        // 1. Chart Type Selector
        HBox chartTypeGroup = new HBox(6);
        chartTypeGroup.setAlignment(Pos.CENTER_LEFT);
        Label chartTypeLabel = new Label("📊 Chart:");
        chartTypeLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #1E293B;");

        chartTypeCombo = new ComboBox<>(FXCollections.observableArrayList(ALL_CHART_TYPES));
        chartTypeCombo.setValue(CHART_BAR);
        chartTypeCombo.setMinWidth(140);
        chartTypeCombo.setPrefWidth(150);
        chartTypeCombo.setStyle("-fx-font-size: 11.5px; -fx-background-radius: 4;");
        chartTypeCombo.setOnAction(e -> {
            if (activeTabIndex >= 0 && activeTabIndex < vizTabs.size()) {
                vizTabs.get(activeTabIndex).chartType = chartTypeCombo.getValue();
            }
            if (currentCategory != null) renderCategory(currentCategory);
        });

        showValuesCb = new CheckBox("Value Labels");
        showValuesCb.setSelected(showValueLabels);
        showValuesCb.setStyle("-fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: #334155;");
        showValuesCb.setTooltip(new Tooltip("Toggle numeric value badges above chart data points"));
        showValuesCb.setOnAction(e -> {
            showValueLabels = showValuesCb.isSelected();
            if (activeTabIndex >= 0 && activeTabIndex < vizTabs.size()) {
                vizTabs.get(activeTabIndex).showValueLabels = showValueLabels;
            }
            if (currentCategory != null) renderCategory(currentCategory);
        });

        chartTypeGroup.getChildren().addAll(chartTypeLabel, chartTypeCombo, showValuesCb);

        // 2. Zoom Controls Pill
        HBox zoomGroup = new HBox(4);
        zoomGroup.setAlignment(Pos.CENTER_LEFT);
        zoomGroup.setPadding(new Insets(2, 6, 2, 6));
        zoomGroup.setStyle("-fx-background-color: #EEF2F6; -fx-background-radius: 6; -fx-border-color: #CBD5E1; -fx-border-radius: 6;");

        Label zoomIcon = new Label("🔍");
        zoomIcon.setStyle("-fx-font-size: 11px;");

        Button zoomOutBtn = new Button("−");
        zoomOutBtn.setTooltip(new Tooltip("Zoom Out (Decrease Magnification)"));
        zoomOutBtn.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #334155; -fx-background-color: #FFFFFF; "
                + "-fx-background-radius: 4; -fx-border-color: #CBD5E1; -fx-border-radius: 4; -fx-cursor: hand; -fx-padding: 0;");
        zoomOutBtn.setMinWidth(28);
        zoomOutBtn.setPrefWidth(28);
        zoomOutBtn.setMinHeight(26);
        zoomOutBtn.setMaxHeight(26);

        zoomSlider = new Slider(0.5, 2.5, 1.0);
        zoomSlider.setMinWidth(70);
        zoomSlider.setPrefWidth(85);
        zoomSlider.setMaxWidth(100);
        zoomSlider.setTooltip(new Tooltip("Drag to adjust magnification (50%–250%)  |  Ctrl+Scroll anywhere on chart to zoom"));

        Button zoomInBtn = new Button("+");
        zoomInBtn.setTooltip(new Tooltip("Zoom In (Ctrl+Scroll also works)"));
        zoomInBtn.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #334155; -fx-background-color: #FFFFFF; "
                + "-fx-background-radius: 4; -fx-border-color: #CBD5E1; -fx-border-radius: 4; -fx-cursor: hand; -fx-padding: 0;");
        zoomInBtn.setMinWidth(28);
        zoomInBtn.setPrefWidth(28);
        zoomInBtn.setMinHeight(26);
        zoomInBtn.setMaxHeight(26);

        Label zoomPctLabel = new Label("100%");
        zoomPctLabel.setStyle("-fx-font-size: 10.5px; -fx-font-weight: bold; -fx-text-fill: #475569; -fx-alignment: center;");
        zoomPctLabel.setMinWidth(36);
        zoomPctLabel.setPrefWidth(36);

        zoomSlider.valueProperty().addListener((obs, oldV, newV) -> {
            int pct = (int) Math.round(newV.doubleValue() * 100);
            zoomPctLabel.setText(pct + "%");
            if (activeTabIndex >= 0 && activeTabIndex < vizTabs.size()) {
                vizTabs.get(activeTabIndex).zoom = newV.doubleValue();
            }
            if (chartHost != null) {
                chartHost.setScaleX(newV.doubleValue());
                chartHost.setScaleY(newV.doubleValue());
            }
        });

        zoomInBtn.setOnAction(e -> zoomSlider.setValue(Math.min(2.5, Math.round((zoomSlider.getValue() + 0.15) * 100.0) / 100.0)));
        zoomOutBtn.setOnAction(e -> zoomSlider.setValue(Math.max(0.5, Math.round((zoomSlider.getValue() - 0.15) * 100.0) / 100.0)));

        Button resetZoomBtn = new Button("↺ Reset");
        resetZoomBtn.setTooltip(new Tooltip("Reset chart zoom to 100% and center view"));
        resetZoomBtn.setStyle("-fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: #334155; -fx-background-color: #FFFFFF; "
                + "-fx-background-radius: 4; -fx-border-color: #CBD5E1; -fx-border-radius: 4; -fx-cursor: hand;");
        resetZoomBtn.setMinWidth(60);
        resetZoomBtn.setPrefWidth(64);
        resetZoomBtn.setMinHeight(26);
        resetZoomBtn.setOnAction(e -> {
            zoomSlider.setValue(1.0);
            if (chartHost != null) {
                chartHost.setTranslateX(0);
                chartHost.setTranslateY(0);
            }
        });

        zoomGroup.getChildren().addAll(zoomIcon, zoomOutBtn, zoomSlider, zoomInBtn, zoomPctLabel, resetZoomBtn);

        // 3. Action Buttons Group
        HBox actionsGroup = new HBox(8);
        actionsGroup.setAlignment(Pos.CENTER_LEFT);

        Button deepAnalysisBtn = new Button("🧠 Full Engineering Analysis");
        deepAnalysisBtn.setTooltip(new Tooltip("Open in-depth power system engineering assessment, root cause factors, and multi-case diagnosis"));
        deepAnalysisBtn.setStyle("-fx-background-color: #7C3AED; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11.5px; "
                + "-fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 6 12;");
        deepAnalysisBtn.setOnAction(e -> showDeepAnalysisDialog());

        Button snapshotBtn = new Button("📷 Snapshot");
        snapshotBtn.setTooltip(new Tooltip("Save high-resolution snapshot of this chart as PNG"));
        snapshotBtn.setStyle("-fx-background-color: #0284C7; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11.5px; "
                + "-fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 6 12;");
        snapshotBtn.setOnAction(e -> takeChartSnapshot());

        Button addToDocBtn = new Button("➕ Add to Report");
        addToDocBtn.setTooltip(new Tooltip("Add current chart and engineering insight to Document Builder"));
        addToDocBtn.setStyle("-fx-background-color: #10B981; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11.5px; "
                + "-fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 6 12;");
        addToDocBtn.setOnAction(e -> addCurrentChartToDocument());

        actionsGroup.getChildren().addAll(deepAnalysisBtn, snapshotBtn, addToDocBtn);

        controlsRow.getChildren().addAll(chartTypeGroup, zoomGroup, actionsGroup);

        chartHost = new StackPane();
        chartHost.setMinHeight(430);
        chartHost.setPrefHeight(430);
        enablePanDragging(chartHost);

        ScrollPane chartScroll = new ScrollPane(chartHost);
        chartScroll.setFitToWidth(true);
        chartScroll.setFitToHeight(true);
        chartScroll.setPannable(false);
        chartScroll.setStyle("-fx-background-color: transparent; -fx-border-color: #E2E8F0; -fx-border-radius: 8;");
        chartScroll.setPrefHeight(440);

        // Ctrl + Scroll = zoom anywhere over the chart area (updates the shared zoomSlider)
        chartScroll.setOnScroll(se -> {
            if (se.isControlDown()) {
                se.consume();
                double delta = se.getDeltaY() > 0 ? 0.1 : -0.1;
                double next = Math.max(0.5, Math.min(2.5, Math.round((zoomSlider.getValue() + delta) * 100.0) / 100.0));
                zoomSlider.setValue(next);
            }
        });


        kpiHost = new FlowPane(12, 12);
        kpiHost.setPadding(new Insets(4, 0, 4, 0));

        Separator divider = new Separator();
        divider.setStyle("-fx-padding: 8 0 8 0; -fx-opacity: 0.6;");

        table = new TableView<>();
        table.setPrefHeight(340);

        content.getChildren().addAll(insightHeaderBox, tabBarBox, controlsRow, chartScroll, kpiHost, divider, table);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        return scroll;
    }

    private void enablePanDragging(Node node) {
        final double[] dragContext = new double[2];
        node.setOnMousePressed(e -> {
            dragContext[0] = e.getSceneX() - node.getTranslateX();
            dragContext[1] = e.getSceneY() - node.getTranslateY();
            node.setCursor(javafx.scene.Cursor.MOVE);
        });
        node.setOnMouseDragged(e -> {
            node.setTranslateX(e.getSceneX() - dragContext[0]);
            node.setTranslateY(e.getSceneY() - dragContext[1]);
        });
        node.setOnMouseReleased(e -> {
            node.setCursor(javafx.scene.Cursor.DEFAULT);
        });
        node.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                node.setTranslateX(0);
                node.setTranslateY(0);
                if (zoomSlider != null) zoomSlider.setValue(1.0);
            }
        });
    }

    private void showDeepAnalysisDialog() {
        if (currentCategory == null) return;
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("🧠 Deep Engineering Analysis — " + currentCategory.title);

        VBox root = new VBox(14);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #0F172A; -fx-font-family: 'Segoe UI', system-ui, sans-serif;");

        // Header
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Label titleLbl = new Label("⚡ " + currentCategory.title);
        titleLbl.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #38BDF8;");
        Label groupBadge = new Label(currentCategory.group);
        groupBadge.setStyle("-fx-background-color: #1E293B; -fx-text-fill: #94A3B8; -fx-font-size: 11px; -fx-padding: 3 8; -fx-background-radius: 4;");
        header.getChildren().addAll(titleLbl, groupBadge);

        // Content Scroll
        VBox scrollContent = new VBox(14);

        // 1. Dynamic System Insight Card
        VBox insightCard = new VBox(6);
        insightCard.setPadding(new Insets(14));
        insightCard.setStyle("-fx-background-color: #1E293B; -fx-background-radius: 8; -fx-border-color: #334155; -fx-border-radius: 8;");
        Label insightHeading = new Label("🔍 REAL-TIME NETWORK DIAGNOSIS");
        insightHeading.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #4ADE80;");
        String dynamicInsight = (currentCategory.insightFn != null)
                ? currentCategory.insightFn.apply(baseResults, activeSelectedCases)
                : currentCategory.description;
        Label insightText = new Label(dynamicInsight);
        insightText.setWrapText(true);
        insightText.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #E2E8F0; -fx-line-spacing: 2;");
        insightCard.getChildren().addAll(insightHeading, insightText);

        // 2. Exact Proven Data Provenance Card (.dat0 / .out0 Tables & Formulas)
        VBox provenanceCard = new VBox(8);
        provenanceCard.setPadding(new Insets(14));
        provenanceCard.setStyle("-fx-background-color: #1E293B; -fx-background-radius: 8; -fx-border-color: #0284C7; -fx-border-radius: 8;");
        Label provHeading = new Label("📄 PROVEN DATA PROVENANCE & TABLE REFERENCES (.dat0 & .out0)");
        provHeading.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #38BDF8;");

        Label provDat0 = new Label("• Input Data Source (.dat0 file): " + (currentCategory.dat0Table != null ? currentCategory.dat0Table : "MiPower Input Deck"));
        provDat0.setWrapText(true);
        provDat0.setStyle("-fx-font-size: 12px; -fx-text-fill: #BAE6FD;");

        Label provOut0 = new Label("• Solved Output Table (.out0 report): " + (currentCategory.out0Table != null ? currentCategory.out0Table : currentCategory.sourceColumns));
        provOut0.setWrapText(true);
        provOut0.setStyle("-fx-font-size: 12px; -fx-text-fill: #BAE6FD;");

        Label provFormula = new Label("• Governing Equation & Proof: " + (currentCategory.formulaProof != null ? currentCategory.formulaProof : "AC Power Flow Derivation"));
        provFormula.setWrapText(true);
        provFormula.setStyle("-fx-font-size: 12px; -fx-text-fill: #FDE047; -fx-font-family: 'Consolas', monospace;");

        Label provWhy = new Label("• Operational Rationale (Why Analyzed): " + (currentCategory.simpleExplanation != null ? currentCategory.simpleExplanation : currentCategory.description));
        provWhy.setWrapText(true);
        provWhy.setStyle("-fx-font-size: 12px; -fx-text-fill: #E2E8F0;");

        provenanceCard.getChildren().addAll(provHeading, provDat0, provOut0, provFormula, provWhy);

        // 3. Cross-Case Study Comparative Analysis
        VBox caseComparisonCard = new VBox(8);
        caseComparisonCard.setPadding(new Insets(14));
        caseComparisonCard.setStyle("-fx-background-color: #1E293B; -fx-background-radius: 8; -fx-border-color: #334155; -fx-border-radius: 8;");
        Label compHeading = new Label("📊 MULTI-CASE STUDY COMPARATIVE SYNTHESIS");
        compHeading.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #FBBF24;");

        StringBuilder compSb = new StringBuilder();
        compSb.append("• Base Case (Authoritative Reference): Baseline network configuration.\n");
        for (CaseStudy cs : activeSelectedCases) {
            Out0Results res = solvedCases.get(cs.id);
            if (res != null) {
                double deltaLoss = res.summary.getOrDefault("real_loss_mw", 0.0) - baseResults.summary.getOrDefault("real_loss_mw", 0.0);
                long csOverloads = res.branches().stream().filter(Branch::isOverloaded).count();
                long baseOverloads = baseResults.branches().stream().filter(Branch::isOverloaded).count();
                compSb.append(String.format("• %s: Real Losses = %.2f MW (Δ %+.2f MW vs Base) | Overloaded Branches = %d (Base: %d) | Below-Min Voltage Violations = %d (Base: %d)\n",
                        cs.name, res.summary.getOrDefault("real_loss_mw", 0.0), deltaLoss, csOverloads, baseOverloads, res.voltageMinViolations, baseResults.voltageMinViolations));
            }
        }
        Label compText = new Label(compSb.toString().strip());
        compText.setWrapText(true);
        compText.setStyle("-fx-font-size: 12px; -fx-text-fill: #CBD5E1; -fx-line-spacing: 2;");
        caseComparisonCard.getChildren().addAll(compHeading, compText);

        // 4. Underlying Engineering Factors & Sensitivity
        VBox factorsCard = new VBox(6);
        factorsCard.setPadding(new Insets(14));
        factorsCard.setStyle("-fx-background-color: #1E293B; -fx-background-radius: 8; -fx-border-color: #334155; -fx-border-radius: 8;");
        Label factorsHeading = new Label("⚙️ INFLUENCING ELECTRICAL ENGINEERING FACTORS");
        factorsHeading.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #60A5FA;");
        Label factorsText = new Label(
                "• Primary Drivers: Conductor thermal rating (MVA), branch inductive reactance (X), operating voltage (V), and reactive power transfer (Q).\n" +
                "• Governing Equations: S = √(P² + Q²), P_loss = I²R = ((P² + Q²)/V²)R, Q_loss = I²X, Voltage Drop ΔV ≈ (PR + QX)/V.\n" +
                "• Sensitivity: High reactive flow (low PF) exponentially increases transmission losses and accelerates transformer insulation aging (Arrhenius Law: 10°C rise halves winding life).\n" +
                "• Calculation Source: " + currentCategory.sourceColumns + " | Scaling Reference: " + currentCategory.scalingSource);
        factorsText.setWrapText(true);
        factorsText.setStyle("-fx-font-size: 12px; -fx-text-fill: #CBD5E1; -fx-line-spacing: 2;");
        factorsCard.getChildren().addAll(factorsHeading, factorsText);

        // 5. Operator Remedial Action Plan
        VBox actionCard = new VBox(6);
        actionCard.setPadding(new Insets(14));
        actionCard.setStyle("-fx-background-color: #1E293B; -fx-background-radius: 8; -fx-border-color: #334155; -fx-border-radius: 8;");
        Label actionHeading = new Label("🛡️ RECOMMENDED OPERATOR MITIGATION STRATEGY");
        actionHeading.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #F87171;");
        Label actionText = new Label(currentCategory.action);
        actionText.setWrapText(true);
        actionText.setStyle("-fx-font-size: 12px; -fx-text-fill: #CBD5E1; -fx-line-spacing: 2;");
        actionCard.getChildren().addAll(actionHeading, actionText);

        scrollContent.getChildren().addAll(insightCard, provenanceCard, caseComparisonCard, factorsCard, actionCard);

        ScrollPane scrollArea = new ScrollPane(scrollContent);
        scrollArea.setFitToWidth(true);
        scrollArea.setStyle("-fx-background-color: transparent; -fx-background: #0F172A;");
        scrollArea.setPrefHeight(520);

        // Close Button
        Button closeBtn = new Button("Close Analysis");
        closeBtn.setStyle("-fx-background-color: #334155; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 16; -fx-background-radius: 6; -fx-cursor: hand;");
        closeBtn.setOnAction(e -> dialog.close());
        HBox footer = new HBox(closeBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(header, scrollArea, footer);

        Scene scene = new Scene(root, 820, 620);
        dialog.setScene(scene);
        dialog.show();
    }

    // ------------------------------------------------------------------- //
    // Saved Customization Document Drawer
    // ------------------------------------------------------------------- //
    private VBox buildDocumentDrawer() {
        VBox drawer = new VBox(10);
        drawer.setPadding(new Insets(12));
        drawer.setPrefWidth(290);
        drawer.setStyle("-fx-background-color: #F8FAFC; -fx-border-color: #E2E8F0; -fx-border-width: 0 0 0 1;");

        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("📑 Document Builder");
        title.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1E293B;");

        docCountLabel = new Label("(0 charts)");
        docCountLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");
        titleRow.getChildren().addAll(title, docCountLabel);

        Label hint = new Label("Add charts & insights into this session document. Reorder, review, and save as a reusable Saved Customization record.");
        hint.setWrapText(true);
        hint.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #64748B;");

        documentListView = new ListView<>();
        VBox.setVgrow(documentListView, Priority.ALWAYS);

        HBox reorderBtns = new HBox(6);
        Button upBtn = new Button("▲ Move Up");
        upBtn.setOnAction(e -> moveDocItem(-1));
        Button downBtn = new Button("▼ Move Down");
        downBtn.setOnAction(e -> moveDocItem(1));
        Button removeBtn = new Button("✕ Remove");
        removeBtn.setOnAction(e -> removeDocItem());
        reorderBtns.getChildren().addAll(upBtn, downBtn, removeBtn);

        Button saveCustomizationBtn = new Button("💾 Save as Customization Record");
        saveCustomizationBtn.setMaxWidth(Double.MAX_VALUE);
        saveCustomizationBtn.setStyle("-fx-background-color: #2563EB; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8;");
        saveCustomizationBtn.setOnAction(e -> saveCustomizationRecord());

        drawer.getChildren().addAll(titleRow, hint, documentListView, reorderBtns, saveCustomizationBtn);
        return drawer;
    }

    private void addCurrentChartToDocument() {
        if (currentCategory == null) return;

        List<String> activeNames = activeSelectedCases.stream().map(c -> c.name).collect(Collectors.toList());
        String insight = currentCategory.insightFn != null ? currentCategory.insightFn.apply(baseResults, activeSelectedCases) : "";

        SavedCustomization.SavedChartItem item = new SavedCustomization.SavedChartItem(
                currentCategory.id, currentCategory.title, chartTypeCombo.getValue(),
                currentCategory.axisLabel, currentCategory.unit, currentCategory.unit,
                insight, "", activeNames
        );

        documentItems.add(item);
        refreshDocumentList();

        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Added '" + currentCategory.title + "' (" + chartTypeCombo.getValue() + ") to Document Record.", ButtonType.OK);
        alert.setTitle("Document Record Updated");
        alert.setHeaderText(null);
        alert.show();
    }

    private void refreshDocumentList() {
        documentListView.getItems().clear();
        for (int i = 0; i < documentItems.size(); i++) {
            var item = documentItems.get(i);
            documentListView.getItems().add((i + 1) + ". " + item.categoryTitle + " [" + item.chartType + "]");
        }
        docCountLabel.setText("(" + documentItems.size() + " charts)");
    }

    private void moveDocItem(int delta) {
        int sel = documentListView.getSelectionModel().getSelectedIndex();
        if (sel < 0) return;
        int target = sel + delta;
        if (target >= 0 && target < documentItems.size()) {
            Collections.swap(documentItems, sel, target);
            refreshDocumentList();
            documentListView.getSelectionModel().select(target);
        }
    }

    private void removeDocItem() {
        int sel = documentListView.getSelectionModel().getSelectedIndex();
        if (sel >= 0 && sel < documentItems.size()) {
            documentItems.remove(sel);
            refreshDocumentList();
        }
    }

    private void saveCustomizationRecord() {
        if (documentItems.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Add at least one chart to the Document before saving a customization record.", ButtonType.OK);
            alert.setHeaderText(null);
            alert.showAndWait();
            return;
        }

        TextInputDialog dlg = new TextInputDialog("Grid Stability & Loading Analysis");
        dlg.setTitle("Save Customization Record");
        dlg.setHeaderText("Save Current Analytics Document");
        dlg.setContentText("Customization Name:");

        Optional<String> res = dlg.showAndWait();
        if (res.isPresent() && !res.get().isBlank()) {
            String name = res.get().trim();
            SavedCustomization sc = new SavedCustomization(UUID.randomUUID().toString(), name, "Custom dashboard with " + documentItems.size() + " charts.");
            sc.selectedCaseIds = activeSelectedCases.stream().map(c -> String.valueOf(c.id)).collect(Collectors.toList());
            sc.chartItems.addAll(documentItems);

            boolean ok = CustomizationStorage.save(sc);
            Alert finish = new Alert(ok ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR,
                    ok ? "Customization record '" + name + "' successfully saved!" : "Failed to write customization record.",
                    ButtonType.OK);
            finish.setHeaderText(null);
            finish.showAndWait();
        }
    }

    private void openSavedCustomizationsDialog() {
        List<SavedCustomization> all = CustomizationStorage.loadAll();
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Saved Customization Records");
        dlg.setHeaderText("Load or Manage Saved Customizations");
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox box = new VBox(10);
        box.setPadding(new Insets(12));
        box.setPrefWidth(460);

        ListView<String> lv = new ListView<>();
        for (SavedCustomization sc : all) {
            lv.getItems().add(sc.name + " (" + sc.chartItems.size() + " charts) -- " + sc.createdAt.substring(0, Math.min(16, sc.createdAt.length())));
        }
        if (all.isEmpty()) {
            lv.getItems().add("No saved customizations found.");
        }

        HBox actions = new HBox(8);
        Button loadBtn = new Button("Load Document");
        loadBtn.setStyle("-fx-background-color: #2563EB; -fx-text-fill: white; -fx-font-weight: bold;");
        loadBtn.setOnAction(e -> {
            int idx = lv.getSelectionModel().getSelectedIndex();
            if (idx >= 0 && idx < all.size()) {
                SavedCustomization target = all.get(idx);
                documentItems.clear();
                documentItems.addAll(target.chartItems);
                refreshDocumentList();
                dlg.close();
                Alert loaded = new Alert(Alert.AlertType.INFORMATION, "Loaded " + target.chartItems.size() + " charts from '" + target.name + "'.", ButtonType.OK);
                loaded.setHeaderText(null);
                loaded.show();
            }
        });

        Button deleteBtn = new Button("Delete");
        deleteBtn.setOnAction(e -> {
            int idx = lv.getSelectionModel().getSelectedIndex();
            if (idx >= 0 && idx < all.size()) {
                CustomizationStorage.delete(all.get(idx).id);
                all.remove(idx);
                lv.getItems().remove(idx);
            }
        });

        actions.getChildren().addAll(loadBtn, deleteBtn);
        box.getChildren().addAll(new Label("Saved Records:"), lv, actions);
        dlg.getDialogPane().setContent(box);
        dlg.showAndWait();
    }

    private void takeChartSnapshot() {
        try {
            WritableImage image = chartHost.snapshot(new SnapshotParameters(), null);
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Chart Snapshot");
            fileChooser.setInitialFileName((currentCategory != null ? currentCategory.id : "chart") + "_snapshot.png");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Image", "*.png"));
            File file = fileChooser.showSaveDialog(null);
            if (file != null) {
                ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
                Alert ok = new Alert(Alert.AlertType.INFORMATION, "Snapshot saved to " + file.getName(), ButtonType.OK);
                ok.setHeaderText(null);
                ok.show();
            }
        } catch (Exception ex) {
            Alert err = new Alert(Alert.AlertType.ERROR, "Failed to capture snapshot: " + ex.getMessage(), ButtonType.OK);
            err.setHeaderText(null);
            err.show();
        }
    }

    // ------------------------------------------------------------------- //
    // Rendering Current Category with Multi-Case Overlays
    // ------------------------------------------------------------------- //
    private void renderCategory(Category c) {
        insightHeaderBox.getChildren().clear();

        Label insightBadge = new Label("💡 KEY SYSTEM INSIGHT");
        insightBadge.setStyle("-fx-background-color: #059669; -fx-text-fill: white; -fx-font-size: 10.5px; -fx-font-weight: bold; -fx-padding: 2 6; -fx-background-radius: 4;");

        Label title = new Label(c.title);
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #065F46;");

        Label provenanceLabel = new Label(String.format(
                "📁 Input Data (.dat0): %s\n📊 Solved Output (.out0): %s\n📐 Mathematical Proof: %s",
                c.dat0Table != null ? c.dat0Table : "Input Data Deck",
                c.out0Table != null ? c.out0Table : c.sourceColumns,
                c.formulaProof != null ? c.formulaProof : "AC Power Flow Formulation"
        ));
        provenanceLabel.setWrapText(true);
        provenanceLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569; -fx-font-family: 'Consolas', monospace; " +
                "-fx-background-color: rgba(255,255,255,0.7); -fx-padding: 6 8; -fx-background-radius: 4; -fx-border-color: #CBD5E1; -fx-border-radius: 4;");
        provenanceLabel.setVisible(false);
        provenanceLabel.setManaged(false);

        Button sourcesBtn = new Button("Sources");
        sourcesBtn.setTooltip(new Tooltip("Toggle source data & mathematical proof details"));
        sourcesBtn.setStyle("-fx-background-color: #E2E8F0; -fx-text-fill: #334155; -fx-font-size: 11px; -fx-font-weight: 600; "
                + "-fx-padding: 2 8; -fx-background-radius: 4; -fx-border-color: #CBD5E1; -fx-border-radius: 4; -fx-cursor: hand;");
        sourcesBtn.setOnAction(e -> {
            boolean visible = !provenanceLabel.isVisible();
            provenanceLabel.setVisible(visible);
            provenanceLabel.setManaged(visible);
            sourcesBtn.setStyle(visible
                    ? "-fx-background-color: #38BDF8; -fx-text-fill: #0F172A; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 2 8; -fx-background-radius: 4; -fx-border-color: #0284C7; -fx-border-radius: 4; -fx-cursor: hand;"
                    : "-fx-background-color: #E2E8F0; -fx-text-fill: #334155; -fx-font-size: 11px; -fx-font-weight: 600; -fx-padding: 2 8; -fx-background-radius: 4; -fx-border-color: #CBD5E1; -fx-border-radius: 4; -fx-cursor: hand;");
        });

        HBox titleRow = new HBox(8, insightBadge, title, sourcesBtn);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label healthyBadge = new Label("🌿 NORMAL / HEALTHY OPERATING RANGE: " + (c.healthyRange != null ? c.healthyRange : "Within statutory grid code limits"));
        healthyBadge.setWrapText(true);
        healthyBadge.setStyle("-fx-background-color: #ECFDF5; -fx-text-fill: #065F46; -fx-font-size: 11.5px; -fx-font-weight: bold; "
                + "-fx-padding: 4 8; -fx-background-radius: 4; -fx-border-color: #10B981; -fx-border-radius: 4;");

        String dynamicInsight = (c.insightFn != null) ? c.insightFn.apply(baseResults, activeSelectedCases) : c.description;
        Label insightText = new Label("📋 Current Grid Status: " + dynamicInsight);
        insightText.setWrapText(true);
        insightText.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: #047857;");

        insightHeaderBox.getChildren().addAll(titleRow, healthyBadge, insightText, provenanceLabel);

        chartTypeCombo.setDisable(c.kpiOnly);
        chartHost.setVisible(!c.kpiOnly);
        chartHost.setManaged(!c.kpiOnly);
        kpiHost.setVisible(c.kpiOnly);
        kpiHost.setManaged(c.kpiOnly);

        List<MetricRow> baseRows = c.rowsFn.apply(baseResults, baseResults);

        Map<String, List<MetricRow>> multiCaseRows = new LinkedHashMap<>();
        for (CaseStudy cs : activeSelectedCases) {
            Out0Results res = solvedCases.get(cs.id);
            if (res != null) {
                multiCaseRows.put(cs.name, c.rowsFn.apply(res, baseResults));
            }
        }

        List<MetricRow> merged = mergeMultiCaseRows(baseRows, multiCaseRows);

        if (c.kpiOnly) {
            renderKpis(merged);
        } else {
            String selectedType = chartTypeCombo.getValue();
            chartHost.getChildren().setAll(buildChart(merged, selectedType, c));
        }

        buildMultiCaseTable(merged, c.unit);
    }

    private List<MetricRow> mergeMultiCaseRows(List<MetricRow> baseRows, Map<String, List<MetricRow>> multiCaseRows) {
        List<MetricRow> merged = new ArrayList<>();
        for (MetricRow r : baseRows) {
            MetricRow mr = new MetricRow(r.label, r.baseValue);
            for (Map.Entry<String, List<MetricRow>> entry : multiCaseRows.entrySet()) {
                String cName = entry.getKey();
                double cVal = entry.getValue().stream()
                        .filter(x -> x.label.equals(r.label))
                        .mapToDouble(x -> x.baseValue)
                        .findFirst().orElse(0.0);
                mr.caseValues.put(cName, cVal);
            }
            merged.add(mr);
        }
        return merged;
    }

    // ------------------------------------------------------------------- //
    // Chart Engine: 12+ Visualizations with Visible Values
    // ------------------------------------------------------------------- //
    private Node buildChart(List<MetricRow> allRows, String chartType, Category category) {
        List<MetricRow> rows = (category.topN > 0 && allRows.size() > category.topN)
                ? new ArrayList<>(allRows.subList(0, category.topN))
                : allRows;

        if (rows.isEmpty()) {
            Label empty = new Label("No data available for this view in the solved output.");
            empty.setStyle("-fx-text-fill: #94A3B8;");
            return empty;
        }

        if (CHART_PIE.equals(chartType)) {
            return buildPieChart(rows, category.unit);
        }
        if (CHART_RADAR.equals(chartType)) {
            return buildRadarChart(rows, category.unit);
        }
        if (CHART_CORRELATION.equals(chartType)) {
            return buildCorrelationChart(rows, category);
        }

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel(category.axisLabel);
        xAxis.setTickLabelRotation(rows.size() > 7 ? 45 : 0);

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel(category.unit);

        XYChart<String, Number> chart;

        if (CHART_LINE.equals(chartType)) {
            chart = new LineChart<>(xAxis, yAxis);
        } else if (CHART_AREA.equals(chartType)) {
            chart = new AreaChart<>(xAxis, yAxis);
        } else if (CHART_STACKED_BAR.equals(chartType)) {
            chart = new StackedBarChart<>(xAxis, yAxis);
        } else if (CHART_SCATTER.equals(chartType) || CHART_BUBBLE.equals(chartType)) {
            chart = new ScatterChart<>(xAxis, yAxis);
        } else {
            chart = new BarChart<>(xAxis, yAxis);
        }

        chart.setAnimated(false);
        chart.setLegendVisible(true);
        chart.setLegendSide(Side.TOP);
        chart.setPrefHeight(410);

        // Outlier protection: if a diverged case has blown-up values (e.g., 1e10), lock Y-axis to sensible Base Case range so Base Case is not crushed
        double maxBase = rows.stream().mapToDouble(r -> Math.abs(r.baseValue)).max().orElse(100.0);
        boolean hasExtremeOutlier = false;
        for (CaseStudy cs : activeSelectedCases) {
            for (MetricRow r : rows) {
                double v = r.caseValues.getOrDefault(cs.name, 0.0);
                if (Math.abs(v) > Math.max(maxBase * 10.0, 2000.0) || Math.abs(v) > 1e6) {
                    hasExtremeOutlier = true;
                    break;
                }
            }
            if (hasExtremeOutlier) break;
        }

        if (hasExtremeOutlier) {
            double minBase = rows.stream().mapToDouble(r -> r.baseValue).min().orElse(0.0);
            double upper = Math.max(maxBase * 1.35, 100.0);
            double lower = Math.min(0.0, minBase * 1.1);
            yAxis.setAutoRanging(false);
            yAxis.setLowerBound(lower);
            yAxis.setUpperBound(upper);
            yAxis.setTickUnit(Math.max(1.0, Math.round((upper - lower) / 6.0)));
        }

        XYChart.Series<String, Number> baseSeries = new XYChart.Series<>();
        baseSeries.setName("Base Case (Ref)");
        for (MetricRow r : rows) {
            baseSeries.getData().add(new XYChart.Data<>(r.label, r.baseValue));
        }
        chart.getData().add(baseSeries);

        for (CaseStudy cs : activeSelectedCases) {
            XYChart.Series<String, Number> csSeries = new XYChart.Series<>();
            csSeries.setName(cs.name);
            for (MetricRow r : rows) {
                double val = r.caseValues.getOrDefault(cs.name, 0.0);
                csSeries.getData().add(new XYChart.Data<>(r.label, val));
            }
            chart.getData().add(csSeries);
        }

        StackPane wrapper = new StackPane();
        Pane overlay = new Pane();
        overlay.setMouseTransparent(true);
        wrapper.getChildren().addAll(chart, overlay);

        attachVisibleNumericLabels(chart, overlay);
        return wrapper;
    }

    private Node buildPieChart(List<MetricRow> rows, String unit) {
        List<PieChart.Data> data = new ArrayList<>();
        for (MetricRow r : rows) {
            data.add(new PieChart.Data(r.label + " (" + formatValue(r.baseValue) + " " + unit + ")",
                    Math.max(r.baseValue, 0)));
        }
        PieChart pie = new PieChart(FXCollections.observableArrayList(data));
        pie.setLabelsVisible(true);
        pie.setLegendVisible(true);
        pie.setPrefHeight(410);
        pie.setAnimated(false);

        for (PieChart.Data d : pie.getData()) {
            d.nodeProperty().addListener((obs, oldN, newN) -> {
                if (newN != null) {
                    Tooltip tip = new Tooltip(d.getName());
                    tip.setShowDelay(javafx.util.Duration.millis(50));
                    Tooltip.install(newN, tip);
                    newN.setOnMouseEntered(e -> {
                        newN.setScaleX(1.04);
                        newN.setScaleY(1.04);
                        newN.setCursor(javafx.scene.Cursor.HAND);
                    });
                    newN.setOnMouseExited(e -> {
                        newN.setScaleX(1.0);
                        newN.setScaleY(1.0);
                    });
                }
            });
        }
        return pie;
    }

    private Node buildRadarChart(List<MetricRow> rows, String unit) {
        StackPane radarPane = new StackPane();
        radarPane.setMinSize(400, 400);
        radarPane.setStyle("-fx-background-color: #0F172A; -fx-background-radius: 8;");

        Pane canvas = new Pane();
        double cx = 200, cy = 200, maxR = 150;

        for (int r = 1; r <= 5; r++) {
            Circle ring = new Circle(cx, cy, (maxR / 5.0) * r);
            ring.setFill(Color.TRANSPARENT);
            ring.setStroke(Color.rgb(51, 65, 85));
            ring.setStrokeWidth(1);
            canvas.getChildren().add(ring);
        }

        int count = Math.min(rows.size(), 8);
        if (count > 0) {
            double maxVal = rows.stream().mapToDouble(x -> x.baseValue).max().orElse(1.0);
            maxVal = Math.max(maxVal, 0.001);

            Polygon poly = new Polygon();
            for (int i = 0; i < count; i++) {
                MetricRow row = rows.get(i);
                double angle = (2 * Math.PI / count) * i - (Math.PI / 2);
                double radius = (row.baseValue / maxVal) * maxR;
                double px = cx + radius * Math.cos(angle);
                double py = cy + radius * Math.sin(angle);
                poly.getPoints().addAll(px, py);

                double lx = cx + (maxR + 25) * Math.cos(angle);
                double ly = cy + (maxR + 25) * Math.sin(angle);
                Label vLabel = new Label(row.label + "\n" + formatValue(row.baseValue));
                vLabel.setStyle("-fx-font-size: 9.5px; -fx-text-fill: #38BDF8; -fx-alignment: center; -fx-background-color: rgba(15,23,42,0.8); -fx-padding: 1 3; -fx-background-radius: 3;");
                vLabel.relocate(lx - 25, ly - 10);
                canvas.getChildren().add(vLabel);

                // Add interactive vertex point
                Circle dot = new Circle(px, py, 4.5, Color.rgb(56, 189, 248));
                Tooltip dotTip = new Tooltip(row.label + ": " + formatValue(row.baseValue) + " " + unit);
                dotTip.setShowDelay(javafx.util.Duration.millis(50));
                Tooltip.install(dot, dotTip);
                dot.setOnMouseEntered(e -> {
                    dot.setRadius(7.0);
                    dot.setFill(Color.rgb(250, 204, 21));
                    dot.setCursor(javafx.scene.Cursor.HAND);
                });
                dot.setOnMouseExited(e -> {
                    dot.setRadius(4.5);
                    dot.setFill(Color.rgb(56, 189, 248));
                });
                canvas.getChildren().add(dot);
            }
            poly.setFill(Color.rgb(56, 189, 248, 0.35));
            poly.setStroke(Color.rgb(56, 189, 248));
            poly.setStrokeWidth(2);
            canvas.getChildren().add(poly);
        }

        radarPane.getChildren().add(canvas);
        return radarPane;
    }

    private Node buildCorrelationChart(List<MetricRow> rows, Category category) {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Base Case Index / Rank");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel(category.unit);

        ScatterChart<Number, Number> chart = new ScatterChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setTitle("Correlation Matrix & Value Distribution");

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Data Points");
        for (int i = 0; i < rows.size(); i++) {
            MetricRow r = rows.get(i);
            XYChart.Data<Number, Number> d = new XYChart.Data<>((Number) Double.valueOf(i + 1), (Number) Double.valueOf(r.baseValue));
            series.getData().add(d);
            d.nodeProperty().addListener((obs, oldN, newN) -> {
                if (newN != null) {
                    Tooltip tip = new Tooltip(r.label + "\n• Rank: #" + (d.getXValue()) + "\n• Value: " + formatValue(r.baseValue) + " " + category.unit);
                    tip.setShowDelay(javafx.util.Duration.millis(50));
                    Tooltip.install(newN, tip);
                    newN.setOnMouseEntered(e -> {
                        newN.setScaleX(1.4);
                        newN.setScaleY(1.4);
                        newN.setCursor(javafx.scene.Cursor.HAND);
                    });
                    newN.setOnMouseExited(e -> {
                        newN.setScaleX(1.0);
                        newN.setScaleY(1.0);
                    });
                }
            });
        }
        chart.getData().add(series);
        return chart;
    }

    private static class ChartLabelItem {
        final XYChart.Data<String, Number> data;
        Node node;
        final Label label;
        final String seriesName;
        final int seriesIndex;
        final double rawValue;

        ChartLabelItem(XYChart.Data<String, Number> data, Node node, Label label, String seriesName, int seriesIndex, double rawValue) {
            this.data = data;
            this.node = node;
            this.label = label;
            this.seriesName = seriesName;
            this.seriesIndex = seriesIndex;
            this.rawValue = rawValue;
        }
    }

    private static final String[] SERIES_COLORS = {"#0369A1", "#C2410C", "#15803D", "#7E22CE", "#BE123C", "#B45309"};
    private static final String[] SERIES_BORDER_COLORS = {"#BAE6FD", "#FED7AA", "#BBF7D0", "#E9D5FF", "#FECDD3", "#FDE68A"};

    private void attachVisibleNumericLabels(XYChart<String, Number> chart, Pane overlay) {
        overlay.getChildren().clear();
        if (!showValueLabels) return;

        List<ChartLabelItem> labelItems = new ArrayList<>();
        ObservableList<XYChart.Series<String, Number>> seriesList = chart.getData();

        for (int sIdx = 0; sIdx < seriesList.size(); sIdx++) {
            XYChart.Series<String, Number> s = seriesList.get(sIdx);
            final int seriesIndex = sIdx;
            final String seriesName = s.getName() != null ? s.getName() : "Series " + (sIdx + 1);
            final String textColor = SERIES_COLORS[seriesIndex % SERIES_COLORS.length];
            final String borderColor = SERIES_BORDER_COLORS[seriesIndex % SERIES_BORDER_COLORS.length];

            for (XYChart.Data<String, Number> d : s.getData()) {
                double raw = d.getYValue() != null ? d.getYValue().doubleValue() : 0.0;
                if (Math.abs(raw) > 1e9) continue; // skip diverged exploded values
                if (Math.abs(raw) < 0.0001) continue; // skip zero values to prevent label clutter

                Label label = new Label(formatValue(raw));
                label.setStyle(String.format(
                        "-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: %s; "
                        + "-fx-background-color: rgba(255,255,255,0.95); -fx-background-radius: 3; "
                        + "-fx-border-color: %s; -fx-border-radius: 3; -fx-padding: 1 3; "
                        + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 2, 0, 0, 1);",
                        textColor, borderColor));
                label.setVisible(false); // only show once placed accurately
                overlay.getChildren().add(label);

                // Tooltip on the label itself
                Tooltip lblTip = new Tooltip(seriesName + ": " + d.getXValue() + " = " + formatValue(raw));
                lblTip.setShowDelay(javafx.util.Duration.millis(50));
                label.setTooltip(lblTip);

                final ChartLabelItem item = new ChartLabelItem(d, d.getNode(), label, seriesName, seriesIndex, raw);
                labelItems.add(item);

                if (d.getNode() != null) {
                    setupDataNode(d.getNode(), d, seriesName, raw, labelItems, overlay);
                }
                d.nodeProperty().addListener((obs, oldN, newN) -> {
                    if (newN != null) {
                        item.node = newN;
                        setupDataNode(newN, d, seriesName, raw, labelItems, overlay);
                    }
                });
            }
        }

        overlay.widthProperty().addListener((obs, o, n) -> repositionAllLabels(labelItems, overlay));
        overlay.heightProperty().addListener((obs, o, n) -> repositionAllLabels(labelItems, overlay));
        javafx.application.Platform.runLater(() -> repositionAllLabels(labelItems, overlay));
    }

    private void setupDataNode(Node node, XYChart.Data<String, Number> d, String seriesName, double raw,
                               List<ChartLabelItem> labelItems, Pane overlay) {
        // 1. Rich interactive tooltip on the data point
        String tipText = String.format("%s\n• Parameter: %s\n• Value: %s", seriesName, d.getXValue(), formatValue(raw));
        Tooltip tip = new Tooltip(tipText);
        tip.setShowDelay(javafx.util.Duration.millis(50));
        tip.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 500;");
        Tooltip.install(node, tip);

        // 2. Interactive hover scaling
        node.setOnMouseEntered(e -> {
            node.setScaleX(1.3);
            node.setScaleY(1.3);
            node.setCursor(javafx.scene.Cursor.HAND);
        });
        node.setOnMouseExited(e -> {
            node.setScaleX(1.0);
            node.setScaleY(1.0);
        });

        node.boundsInParentProperty().addListener((obs, oldB, newB) -> repositionAllLabels(labelItems, overlay));
        javafx.application.Platform.runLater(() -> repositionAllLabels(labelItems, overlay));
    }

    private void repositionAllLabels(List<ChartLabelItem> items, Pane overlay) {
        if (overlay == null || items == null || items.isEmpty()) return;
        if (!showValueLabels) {
            for (ChartLabelItem item : items) {
                if (item.label != null) item.label.setVisible(false);
            }
            return;
        }

        double ovWidth = overlay.getWidth();
        double ovHeight = overlay.getHeight();
        if (ovWidth <= 0 || ovHeight <= 0) return;

        // Position data struct
        class PlacedPoint {
            ChartLabelItem item;
            double px, py;
            double lblW, lblH;
            double finalX, finalY;
        }

        List<PlacedPoint> placed = new ArrayList<>();
        for (ChartLabelItem item : items) {
            if (item.node == null || !item.node.isVisible() || item.node.getScene() == null) {
                if (item.label != null) item.label.setVisible(false);
                continue;
            }
            javafx.geometry.Bounds sceneBounds = item.node.localToScene(item.node.getBoundsInLocal());
            if (sceneBounds == null || (sceneBounds.getWidth() <= 0 && sceneBounds.getHeight() <= 0)) {
                if (item.label != null) item.label.setVisible(false);
                continue;
            }
            javafx.geometry.Bounds local = overlay.sceneToLocal(sceneBounds);
            if (local == null) {
                if (item.label != null) item.label.setVisible(false);
                continue;
            }

            PlacedPoint pp = new PlacedPoint();
            pp.item = item;
            pp.px = local.getMinX() + local.getWidth() / 2.0;
            pp.py = local.getMinY();
            pp.lblW = Math.max(item.label.prefWidth(-1), 28.0);
            pp.lblH = Math.max(item.label.prefHeight(-1), 14.0);
            placed.add(pp);
        }

        if (placed.isEmpty()) return;

        // Group points by X coordinate proximity (within 24px)
        placed.sort((a, b) -> Double.compare(a.px, b.px));
        List<List<PlacedPoint>> xGroups = new ArrayList<>();
        List<PlacedPoint> curGroup = new ArrayList<>();

        for (PlacedPoint p : placed) {
            if (curGroup.isEmpty()) {
                curGroup.add(p);
            } else {
                PlacedPoint prev = curGroup.get(curGroup.size() - 1);
                if (Math.abs(p.px - prev.px) < 24.0) {
                    curGroup.add(p);
                } else {
                    xGroups.add(new ArrayList<>(curGroup));
                    curGroup.clear();
                    curGroup.add(p);
                }
            }
        }
        if (!curGroup.isEmpty()) xGroups.add(curGroup);

        // Dynamic collision avoidance within each X group
        for (List<PlacedPoint> group : xGroups) {
            // Sort group points by Y (lowest py is highest on screen)
            group.sort((a, b) -> Double.compare(a.py, b.py));

            if (group.size() == 1) {
                PlacedPoint p = group.get(0);
                p.finalX = p.px - p.lblW / 2.0;
                p.finalY = p.py - p.lblH - 3.0;
            } else if (group.size() == 2) {
                PlacedPoint p1 = group.get(0); // higher point
                PlacedPoint p2 = group.get(1); // lower point

                if (Math.abs(p2.py - p1.py) < 20.0) {
                    // Close together vertically -> stagger left and right
                    p1.finalX = p1.px - p1.lblW - 3.0;
                    p1.finalY = p1.py - p1.lblH - 3.0;

                    p2.finalX = p2.px + 3.0;
                    p2.finalY = p2.py - p2.lblH - 3.0;
                } else {
                    // Distinct Y positions -> place above each point
                    p1.finalX = p1.px - p1.lblW / 2.0;
                    p1.finalY = p1.py - p1.lblH - 3.0;

                    p2.finalX = p2.px - p2.lblW / 2.0;
                    p2.finalY = p2.py - p2.lblH - 3.0;
                }
            } else {
                // 3 or more series at the same X (e.g. Base Case, Case 1, Case 2)
                for (int i = 0; i < group.size(); i++) {
                    PlacedPoint p = group.get(i);
                    if (i == 0) {
                        // Topmost point: place above centered
                        p.finalX = p.px - p.lblW / 2.0;
                        p.finalY = p.py - p.lblH - 12.0;
                    } else if (i == 1) {
                        // Second point: place slightly left
                        p.finalX = p.px - p.lblW - 4.0;
                        p.finalY = p.py - (p.lblH / 2.0);
                    } else if (i == 2) {
                        // Third point: place slightly right
                        p.finalX = p.px + 4.0;
                        p.finalY = p.py - (p.lblH / 2.0);
                    } else {
                        // 4th+ point: place below
                        p.finalX = p.px - p.lblW / 2.0;
                        p.finalY = p.py + 14.0 + (i - 3) * 16.0;
                    }
                }
            }

            // Screen boundary clamping and visibility update
            for (PlacedPoint p : group) {
                p.finalX = Math.max(2.0, Math.min(ovWidth - p.lblW - 2.0, p.finalX));
                p.finalY = Math.max(2.0, Math.min(ovHeight - p.lblH - 2.0, p.finalY));
                p.item.label.relocate(p.finalX, p.finalY);
                p.item.label.setVisible(true);
            }
        }
    }

    private void renderKpis(List<MetricRow> rows) {
        kpiHost.getChildren().clear();
        for (MetricRow r : rows) {
            VBox card = new VBox(4);
            card.getStyleClass().add("card");
            card.setPadding(new Insets(12));
            card.setPrefWidth(240);
            card.setStyle("-fx-background-color: white; -fx-border-color: #E2E8F0; -fx-border-radius: 8;");

            Label label = new Label(r.label);
            label.setWrapText(true);
            label.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #64748B; -fx-font-weight: 600;");

            Label value = new Label(formatValue(r.baseValue));
            value.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #1E3A8A;");

            card.getChildren().addAll(label, value);

            for (Map.Entry<String, Double> entry : r.caseValues.entrySet()) {
                Label cmp = new Label(entry.getKey() + ": " + formatValue(entry.getValue()));
                cmp.setWrapText(true);
                cmp.setStyle("-fx-font-size: 11px; -fx-text-fill: #D97706; -fx-font-weight: bold;");
                card.getChildren().add(cmp);
            }
            kpiHost.getChildren().add(card);
        }
    }

    private void buildMultiCaseTable(List<MetricRow> rows, String unit) {
        table.getColumns().clear();
        table.getItems().clear();

        TableColumn<MetricRow, String> labelCol = new TableColumn<>(currentCategory.axisLabel);
        labelCol.setCellValueFactory(new PropertyValueFactory<>("label"));
        labelCol.setPrefWidth(260);

        TableColumn<MetricRow, String> baseCol = new TableColumn<>("Base Case (" + unit + ")");
        baseCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                formatValue(d.getValue().baseValue)));
        baseCol.setPrefWidth(140);

        table.getColumns().addAll(labelCol, baseCol);

        for (CaseStudy cs : activeSelectedCases) {
            String cName = cs.name;
            TableColumn<MetricRow, String> valCol = new TableColumn<>(cName + " (" + unit + ")");
            valCol.setCellValueFactory(d -> {
                Double v = d.getValue().caseValues.get(cName);
                return new javafx.beans.property.SimpleStringProperty(v != null ? formatValue(v) : "\u2014");
            });
            valCol.setPrefWidth(140);

            TableColumn<MetricRow, String> deltaCol = new TableColumn<>("Δ vs Base (" + cs.hierarchicalId + ")");
            deltaCol.setCellValueFactory(d -> {
                Double v = d.getValue().caseValues.get(cName);
                if (v == null) return new javafx.beans.property.SimpleStringProperty("\u2014");
                double delta = v - d.getValue().baseValue;
                return new javafx.beans.property.SimpleStringProperty((delta >= 0 ? "+" : "") + formatValue(delta));
            });
            deltaCol.setPrefWidth(130);

            table.getColumns().addAll(valCol, deltaCol);
        }

        table.getItems().setAll(rows);
    }

    private static String formatValue(double v) {
        if (Math.abs(v) >= 1000) return String.format("%,.1f", v);
        if (Math.abs(v) < 10 && v != Math.rint(v)) return String.format("%.4f", v);
        return String.format("%.2f", v);
    }

    public static final class MetricRow {
        public final String label;
        public final double baseValue;
        public final Map<String, Double> caseValues = new LinkedHashMap<>();

        public MetricRow(String label, double baseValue) {
            this.label = label;
            this.baseValue = baseValue;
        }

        public String getLabel() {
            return label;
        }
    }

    private static final class Category {
        String id;
        String shortName;
        String group;
        String title;
        String axisLabel;
        String unit;
        String dat0Table;
        String out0Table;
        String formulaProof;
        String sourceColumns;
        String scalingSource;
        String description;
        String healthyRange;
        String simpleExplanation;
        String action;
        int topN;
        boolean kpiOnly;
        BiFunction<Out0Results, Out0Results, List<MetricRow>> rowsFn;
        BiFunction<Out0Results, List<CaseStudy>, String> insightFn;
    }

    private void buildCategories() {
        categories.clear();

        // 1. Voltage Profile Assessment
        Category vProfile = new Category();
        vProfile.id = "voltage_profile";
        vProfile.shortName = "V-Profile";
        vProfile.group = "Voltage Profile Assessment";
        vProfile.title = "Voltage Profile Distribution & Dispersion";
        vProfile.axisLabel = "Voltage Band (p.u.)";
        vProfile.unit = "buses";
        vProfile.dat0Table = "Section 1.2 % Bus Specifications (Columns: Bus ID, Voltage Min (Vmin=0.95), Voltage Max (Vmax=1.05), Bus Type)";
        vProfile.out0Table = "|***** BUS VOLTAGES AND POWERS *****| (Columns: NODE NO., FROM NAME, V-MAG (p.u.), % VR, ANGLE)";
        vProfile.formulaProof = "V_pu = V_actual / V_nominal,  %VR = ((V_pu - 1.0) / 1.0) × 100%,  Bucketed in 0.02 p.u. intervals.";
        vProfile.sourceColumns = "BUS VOLTAGES AND POWERS -> V-MAG (p.u.)";
        vProfile.scalingSource = "Base Case min/max voltage bounds (p.u.)";
        vProfile.description = "Distribution of all network bus voltages bucketed into 0.02 p.u. intervals.";
        vProfile.healthyRange = "0.95 to 1.05 p.u. (Nominal: 1.00 p.u.)";
        vProfile.simpleExplanation = "We analyze bus voltages to verify that all customer and substation equipment receive electrical potential within safe operational limits (typically 0.95 to 1.05 p.u.). Voltages outside this band cause motor burnout, inverter tripping, and transmission line instability.";
        vProfile.action = "Tight voltage distribution around 1.00 p.u. signals stable network voltage. Drifts indicate reactive compensation needs.";
        vProfile.topN = 0;
        vProfile.rowsFn = this::voltageProfileRows;
        vProfile.insightFn = (base, cases) -> {
            double avgV = base.buses.stream().mapToDouble(b -> b.voltagePu).average().orElse(1.0);
            long total = base.buses.size();
            long lowV = base.buses.stream().filter(b -> b.voltagePu < 0.95).count();
            return String.format("Base Case average voltage is %.4f p.u. across %d buses. %d bus(es) are operating below 0.95 p.u.",
                    avgV, total, lowV);
        };
        categories.add(vProfile);

        Category vViolations = new Category();
        vViolations.id = "voltage_violations";
        vViolations.shortName = "V-Violations";
        vViolations.group = "Voltage Profile Assessment";
        vViolations.title = "Voltage Limit Violations & Outliers";
        vViolations.axisLabel = "Bus Identifier";
        vViolations.unit = "p.u.";
        vViolations.dat0Table = "Section 1.2 % Bus Data (Columns: BusNo, V_min_limit, V_max_limit) & Common Control Options";
        vViolations.out0Table = "|***** BUS VOLTAGES AND POWERS *****| (Flag '@' indicates Undervoltage < Vmin, '#' indicates Overvoltage > Vmax)";
        vViolations.formulaProof = "Condition: V_MAG < V_min (Undervoltage) OR V_MAG > V_max (Overvoltage). Outlier magnitude = |V_MAG - 1.0|.";
        vViolations.sourceColumns = "BUS VOLTAGES AND POWERS -> NODE NO., FROM NAME, V-MAG, % VR, FLAG (@ below / # above)";
        vViolations.scalingSource = "Base Case .dat0 voltage limit specifications";
        vViolations.description = "Buses violating upper/lower statutory voltage bounds.";
        vViolations.healthyRange = "0 Violations (100% of buses within 0.95 - 1.05 p.u.)";
        vViolations.simpleExplanation = "Pinpoints exact buses where voltage exceeds statutory boundaries. Undervoltage causes induction motor stalling and voltage collapse; overvoltage breaks down transformer dielectric insulation.";
        vViolations.action = "Priority list for capacitor/reactor switching, transformer tap adjustments, or generator setpoint tuning.";
        vViolations.topN = 25;
        vViolations.rowsFn = this::voltageViolationRows;
        vViolations.insightFn = (base, cases) -> {
            long count = base.buses.stream().filter(Bus::hasVoltageViolation).count();
            return count == 0 ? "No voltage violations present in Base Case."
                    : String.format("Detected %d voltage limit violation(s) in Base Case. Correct high/low voltage outliers.", count);
        };
        categories.add(vViolations);

        Category angleSpread = new Category();
        angleSpread.id = "angle_spread";
        angleSpread.shortName = "Angle Spread";
        angleSpread.group = "Voltage Profile Assessment";
        angleSpread.title = "Voltage Angle Spread (Rotor Stability Indicator)";
        angleSpread.axisLabel = "Bus";
        angleSpread.unit = "deg";
        angleSpread.dat0Table = "Section 1.2 % Bus Specifications (Slack Bus angle fixed at θ_ref = 0.0°)";
        angleSpread.out0Table = "|***** BUS VOLTAGES AND POWERS *****| (Columns: NODE NO., FROM NAME, ANGLE DEGREE)";
        angleSpread.formulaProof = "Δθ_max = max(θ_bus) - min(θ_bus).  Power transfer: P_ij ≈ (V_i V_j / X_ij) sin(θ_i - θ_j).";
        angleSpread.sourceColumns = "BUS VOLTAGES AND POWERS -> ANGLE DEGREE";
        angleSpread.scalingSource = "Base Case slack bus reference angle (0 deg)";
        angleSpread.description = "Angular displacement across the network relative to the slack bus.";
        angleSpread.healthyRange = "< 30.0° (Rotor stability limit: 45°-60°)";
        angleSpread.simpleExplanation = "Phase angle separation is the direct mechanical tension on the power grid. When angle spread between generators exceeds 40°-50°, generators lose synchronism and trip offline, leading to catastrophic blackout.";
        angleSpread.action = "Higher angle spread indicates heavier stress and lower transient stability margins.";
        angleSpread.topN = 15;
        angleSpread.rowsFn = this::angleSpreadRows;
        categories.add(angleSpread);

        Category voltageDev = new Category();
        voltageDev.id = "voltage_deviation";
        voltageDev.shortName = "V-Deviation";
        voltageDev.group = "Voltage Profile Assessment";
        voltageDev.title = "Voltage Deviation from Nominal (p.u.) — Stress Map";
        voltageDev.axisLabel = "Bus";
        voltageDev.unit = "% deviation";
        voltageDev.dat0Table = "Section 1.2 % Bus Data (Nominal Base Voltage = 1.0 p.u.)";
        voltageDev.out0Table = "|***** BUS VOLTAGES AND POWERS *****| (Columns: NODE NO., FROM NAME, V-MAG, % VR)";
        voltageDev.formulaProof = "ΔV% = ((V_bus_pu - 1.0) / 1.0) × 100%.  Positive = Overvoltage (+ΔV), Negative = Undervoltage (-ΔV).";
        voltageDev.sourceColumns = "BUS VOLTAGES AND POWERS -> V-MAG (p.u.), computed as (V - 1.0) × 100%";
        voltageDev.scalingSource = "Base Case bus voltage, nominal = 1.0 p.u.";
        voltageDev.description = "Percentage deviation of each bus voltage from the 1.0 p.u. nominal. Negative = undervoltage (reactive deficit), Positive = overvoltage (leading reactive surplus or lightly loaded network).";
        voltageDev.healthyRange = "Within ±3.0% deviation from nominal (Grid code trip limit: ±5.0%)";
        voltageDev.simpleExplanation = "Visualizes voltage stress across all nodes in percentage terms. Deviations beyond ±5% violate grid codes and degrade consumer appliance performance.";
        voltageDev.action = "Sustained deviation >±5% degrades motor efficiency (motor torque ∝ V²), increases cable losses, and triggers equipment protection relays. Undervoltage buses need shunt capacitors or tap adjustment; overvoltage buses may need shunt reactors or tap reduction.";
        voltageDev.topN = 20;
        voltageDev.rowsFn = this::voltageDeviationRows;
        voltageDev.insightFn = (base, cases) -> {
            double avgDev = base.buses.stream().mapToDouble(b -> Math.abs(b.voltagePu - 1.0) * 100).average().orElse(0);
            long severeCount = base.buses.stream().filter(b -> Math.abs(b.voltagePu - 1.0) * 100 > 5.0).count();
            return String.format("Average voltage deviation: %.2f%%. %d bus(es) exceed ±5%% deviation threshold — these require immediate voltage control action (tap adjustment, VAr injection, or generator AVR setpoint change).", avgDev, severeCount);
        };
        categories.add(voltageDev);

        // 2. Thermal & Loading (Only Transformer Loading and Line Loading)
        Category xfmrLoading = new Category();
        xfmrLoading.id = "transformer_loading";
        xfmrLoading.shortName = "XFMR Load";
        xfmrLoading.group = "Thermal & Loading";
        xfmrLoading.title = "Transformer Loading Ranking (Thermal Utilization)";
        xfmrLoading.axisLabel = "Transformer (From->To)";
        xfmrLoading.unit = "% loading";
        xfmrLoading.dat0Table = "Section 1.3 % Total 2Wdg Transformers (Columns: FromBus, ToBus, MVA Nominal Rating, % Tap)";
        xfmrLoading.out0Table = "|***** LINE & TRANSFORMER FLOWS *****| (Filter: Equipment Kind = Transformer, % LOADING)";
        xfmrLoading.formulaProof = "% Loading = (√(MW_flow² + MVAr_flow²) / MVA_rating_xfmr) × 100%.";
        xfmrLoading.sourceColumns = "TRANSFORMER FLOWS -> % Loading";
        xfmrLoading.scalingSource = "Base Case transformer MVA thermal ratings";
        xfmrLoading.description = "Ranks all transformers by thermal capacity utilization in the Base Case.";
        xfmrLoading.healthyRange = "< 80.0% continuous loading (100% maximum continuous thermal rating)";
        xfmrLoading.simpleExplanation = "Substation power transformers are the most expensive assets on the power grid. Operating them over 100% causes rapid winding paper insulation degradation (Arrhenius Law: every 10°C rise doubles insulation aging rate).";
        xfmrLoading.action = "Overloaded transformers experience accelerated insulation aging (Arrhenius law: every 10°C rise halves insulation life). They are priority candidates for on-load tap changer adjustment, parallel transformer addition, or generator re-dispatch to reduce transfer.";
        xfmrLoading.topN = 20;
        xfmrLoading.rowsFn = this::transformerLoadingRows;
        xfmrLoading.insightFn = (base, cases) -> {
            long ovl = base.branches().stream()
                    .filter(b -> "Transformer".equalsIgnoreCase(b.getKind()) && b.isOverloaded()).count();
            long total = base.branches().stream().filter(b -> "Transformer".equalsIgnoreCase(b.getKind())).count();
            return String.format("%d of %d transformers are OVERLOADED in Base Case. Overloaded transformers cause irreversible insulation damage and are the leading cause of forced outages in aging power systems.", ovl, total);
        };
        categories.add(xfmrLoading);

        Category lineLoading = new Category();
        lineLoading.id = "line_loading";
        lineLoading.shortName = "Line Load";
        lineLoading.group = "Thermal & Loading";
        lineLoading.title = "Transmission Line Loading Ranking (Thermal Utilization)";
        lineLoading.axisLabel = "Line (From->To)";
        lineLoading.unit = "% loading";
        lineLoading.dat0Table = "Section 1.5 % Total Lines (Columns: FromBus, ToBus, R, X, B, MVA Rating)";
        lineLoading.out0Table = "|***** LINE & TRANSFORMER FLOWS *****| (Filter: Equipment Kind = Line, % LOADING)";
        lineLoading.formulaProof = "% Loading = (√(MW_flow² + MVAr_flow²) / MVA_rating_line) × 100%.";
        lineLoading.sourceColumns = "LINE FLOWS -> % Loading";
        lineLoading.scalingSource = "Base Case line thermal ratings (normal/emergency)";
        lineLoading.description = "Ranks all transmission lines (excluding transformers) by thermal capacity utilization.";
        lineLoading.healthyRange = "< 80.0% continuous loading (Ground clearance & thermal sag limit: 100%)";
        lineLoading.simpleExplanation = "Ranks overhead transmission lines by percentage thermal loading. Overheated aluminum conductors expand and sag into trees or roadways, risking short-circuit ground faults.";
        lineLoading.action = "Highly loaded lines experience increased conductor sag (clearance violation risk at peak temperatures), high I²R Joule losses, and reduced N-1 security. Identify these for emergency rating checks, reconductoring, or generation redispatch to relieve congestion.";
        lineLoading.topN = 20;
        lineLoading.rowsFn = this::lineLoadingRows;
        lineLoading.insightFn = (base, cases) -> {
            double avgLoad = base.branches().stream()
                    .filter(b -> "Line".equalsIgnoreCase(b.getKind()))
                    .mapToDouble(b -> b.loadingPercent).average().orElse(0);
            long ovl = base.branches().stream()
                    .filter(b -> "Line".equalsIgnoreCase(b.getKind()) && b.isOverloaded()).count();
            return String.format("Average transmission line loading: %.1f%%. %d line(s) OVERLOADED. High average loading reduces N-1 security — a single contingency can trigger cascading overloads.", avgLoad, ovl);
        };
        categories.add(lineLoading);

        // 3. Loss Analysis (Real Power Losses, Active Power Losses, Reactive Power Losses — Split Lines vs Transformers)
        Category lineRealLosses = new Category();
        lineRealLosses.id = "line_real_losses";
        lineRealLosses.shortName = "Line Real Loss";
        lineRealLosses.group = "Loss Analysis";
        lineRealLosses.title = "Transmission Line Real Power Losses (MW)";
        lineRealLosses.axisLabel = "Line (From -> To)";
        lineRealLosses.unit = "MW loss";
        lineRealLosses.dat0Table = "Section 1.5 % Transmission Lines (Columns: Line R in p.u., Reactance X in p.u.)";
        lineRealLosses.out0Table = "|***** LINE & TRANSFORMER FLOWS *****| (Filter: Kind = Line, MW LOSS = P_from + P_to)";
        lineRealLosses.formulaProof = "P_loss = 3 × I² × R = ((P² + Q²) / V²) × R  [Joule Heating Dissipation in Lines].";
        lineRealLosses.sourceColumns = "LINE FLOWS -> MW Loss";
        lineRealLosses.scalingSource = "Base Case Line I²R Joule heating losses";
        lineRealLosses.description = "Ranks transmission lines causing greatest energy dissipation through conductor resistance.";
        lineRealLosses.healthyRange = "Top line < 10.0 MW loss";
        lineRealLosses.simpleExplanation = "Identifies transmission lines wasting power as heat due to high resistance and current. Upgrading conductors or reducing reactive flows recovers lost transmission revenue.";
        lineRealLosses.action = "Prime targets for HTLS conductor upgrades, phase balancing, or voltage boosting to minimize grid transmission losses.";
        lineRealLosses.topN = 20;
        lineRealLosses.rowsFn = this::lineRealLossRows;
        lineRealLosses.insightFn = (base, cases) -> {
            double totalLoss = base.branches().stream().filter(b -> "Line".equalsIgnoreCase(b.getKind())).mapToDouble(b -> b.mwLoss).sum();
            var top = base.branches().stream().filter(b -> "Line".equalsIgnoreCase(b.getKind())).max((a, b) -> Double.compare(a.mwLoss, b.mwLoss));
            return top.map(b -> String.format("Top loss transmission line: %d->%d dissipating %.2f MW (%.1f%% of total %.2f MW line losses).",
                    b.fromBus, b.toBus, b.mwLoss, (b.mwLoss / Math.max(totalLoss, 0.001)) * 100.0, totalLoss))
                    .orElse("No line loss data available.");
        };
        categories.add(lineRealLosses);

        Category xfmrRealLosses = new Category();
        xfmrRealLosses.id = "xfmr_real_losses";
        xfmrRealLosses.shortName = "XFMR Real Loss";
        xfmrRealLosses.group = "Loss Analysis";
        xfmrRealLosses.title = "Transformer Real Power Losses (MW)";
        xfmrRealLosses.axisLabel = "Transformer (From -> To)";
        xfmrRealLosses.unit = "MW loss";
        xfmrRealLosses.dat0Table = "Section 1.3 % Transformers (Columns: Copper Loss R in p.u., Core Loss G in p.u.)";
        xfmrRealLosses.out0Table = "|***** LINE & TRANSFORMER FLOWS *****| (Filter: Kind = Transformer, MW LOSS)";
        xfmrRealLosses.formulaProof = "P_loss_xfmr = I² R_copper + V² G_core  [Winding Copper + Core Iron Losses].";
        xfmrRealLosses.sourceColumns = "TRANSFORMER FLOWS -> MW Loss";
        xfmrRealLosses.scalingSource = "Base Case transformer copper and core losses";
        xfmrRealLosses.description = "Ranks substation power transformers by active real power losses.";
        xfmrRealLosses.healthyRange = "Transformer efficiency > 98.5% (Low winding copper dissipation)";
        xfmrRealLosses.simpleExplanation = "Measures energy lost as heat within transformer copper windings and magnetic core. Overloaded transformers exhibit steep quadratic increases in copper losses.";
        xfmrRealLosses.action = "High loss transformers indicate chronic overloading or excessive winding resistance. Consider tap adjustment, parallel transformer loading, or replacement with high-efficiency units.";
        xfmrRealLosses.topN = 20;
        xfmrRealLosses.rowsFn = this::xfmrRealLossRows;
        xfmrRealLosses.insightFn = (base, cases) -> {
            double totalLoss = base.branches().stream().filter(b -> "Transformer".equalsIgnoreCase(b.getKind())).mapToDouble(b -> b.mwLoss).sum();
            var top = base.branches().stream().filter(b -> "Transformer".equalsIgnoreCase(b.getKind())).max((a, b) -> Double.compare(a.mwLoss, b.mwLoss));
            return top.map(b -> String.format("Top loss transformer: %d->%d dissipating %.2f MW (%.1f%% of total %.2f MW transformer losses).",
                    b.fromBus, b.toBus, b.mwLoss, (b.mwLoss / Math.max(totalLoss, 0.001)) * 100.0, totalLoss))
                    .orElse("No transformer loss data available.");
        };
        categories.add(xfmrRealLosses);

        Category lineLossIntensity = new Category();
        lineLossIntensity.id = "line_loss_intensity";
        lineLossIntensity.shortName = "Line Active Loss";
        lineLossIntensity.group = "Loss Analysis";
        lineLossIntensity.title = "Transmission Line Active Power Loss Intensity (MW)";
        lineLossIntensity.axisLabel = "Line (From -> To)";
        lineLossIntensity.unit = "MW loss";
        lineLossIntensity.dat0Table = "Section 1.5 % Transmission Lines (Columns: Series Resistance R in p.u.)";
        lineLossIntensity.out0Table = "|***** LINE & TRANSFORMER FLOWS *****| (Filter: Kind = Line, MW LOSS = P_ij + P_ji)";
        lineLossIntensity.formulaProof = "P_loss_ij = I_ij² × R_ij = ((P_ij² + Q_ij²) / V_i²) × R_ij.";
        lineLossIntensity.sourceColumns = "LINE FLOWS -> MW Loss";
        lineLossIntensity.scalingSource = "Base Case transmission line I²R losses";
        lineLossIntensity.description = "Pinpoints individual transmission lines causing greatest loss bottlenecks.";
        lineLossIntensity.healthyRange = "Top 5 lines contribute < 30% of total line losses";
        lineLossIntensity.simpleExplanation = "Pinpoints lines carrying excessive current density relative to their conductor size.";
        lineLossIntensity.action = "Reconductor with larger or composite-core conductors, or redistribute power flow to parallel corridors.";
        lineLossIntensity.topN = 20;
        lineLossIntensity.rowsFn = this::lineLossIntensityRows;
        lineLossIntensity.insightFn = (base, cases) -> {
            double totalLoss = base.branches().stream().filter(b -> "Line".equalsIgnoreCase(b.getKind())).mapToDouble(b -> b.mwLoss).sum();
            var top = base.branches().stream().filter(b -> "Line".equalsIgnoreCase(b.getKind())).max((a, b) -> Double.compare(a.mwLoss, b.mwLoss));
            return top.map(b -> String.format("Highest loss line corridor: LINE %d->%d dissipating %.2f MW (%.1f%% of total line losses).",
                    b.fromBus, b.toBus, b.mwLoss, (b.mwLoss / Math.max(totalLoss, 0.001)) * 100.0))
                    .orElse("No line loss data available.");
        };
        categories.add(lineLossIntensity);

        Category xfmrLossIntensity = new Category();
        xfmrLossIntensity.id = "xfmr_loss_intensity";
        xfmrLossIntensity.shortName = "XFMR Active Loss";
        xfmrLossIntensity.group = "Loss Analysis";
        xfmrLossIntensity.title = "Transformer Active Power Loss Intensity (MW)";
        xfmrLossIntensity.axisLabel = "Transformer (From -> To)";
        xfmrLossIntensity.unit = "MW loss";
        xfmrLossIntensity.dat0Table = "Section 1.3 % Transformers (Columns: Series Resistance R in p.u.)";
        xfmrLossIntensity.out0Table = "|***** LINE & TRANSFORMER FLOWS *****| (Filter: Kind = Transformer, MW LOSS = P_ij + P_ji)";
        xfmrLossIntensity.formulaProof = "P_loss_xfmr = ((P² + Q²) / V²) × R_winding.";
        xfmrLossIntensity.sourceColumns = "TRANSFORMER FLOWS -> MW Loss";
        xfmrLossIntensity.scalingSource = "Base Case transformer winding I²R losses";
        xfmrLossIntensity.description = "Pinpoints transformers experiencing heavy active power dissipation.";
        xfmrLossIntensity.healthyRange = "Top transformer loss < 2.0% of unit MVA rating";
        xfmrLossIntensity.simpleExplanation = "Highlights transformers generating high heat dissipation due to high through-flow.";
        xfmrLossIntensity.action = "Rebalance substation loads or adjust tap ratios to alleviate thermal stress.";
        xfmrLossIntensity.topN = 20;
        xfmrLossIntensity.rowsFn = this::xfmrLossIntensityRows;
        xfmrLossIntensity.insightFn = (base, cases) -> {
            double totalLoss = base.branches().stream().filter(b -> "Transformer".equalsIgnoreCase(b.getKind())).mapToDouble(b -> b.mwLoss).sum();
            var top = base.branches().stream().filter(b -> "Transformer".equalsIgnoreCase(b.getKind())).max((a, b) -> Double.compare(a.mwLoss, b.mwLoss));
            return top.map(b -> String.format("Highest loss transformer: XFMR %d->%d dissipating %.2f MW (%.1f%% of total transformer losses).",
                    b.fromBus, b.toBus, b.mwLoss, (b.mwLoss / Math.max(totalLoss, 0.001)) * 100.0))
                    .orElse("No transformer loss data available.");
        };
        categories.add(xfmrLossIntensity);

        Category lineReactiveLosses = new Category();
        lineReactiveLosses.id = "line_reactive_losses";
        lineReactiveLosses.shortName = "Line Q-Loss";
        lineReactiveLosses.group = "Loss Analysis";
        lineReactiveLosses.title = "Transmission Line Reactive Power Losses (MVAr)";
        lineReactiveLosses.axisLabel = "Line (From -> To)";
        lineReactiveLosses.unit = "MVAr loss";
        lineReactiveLosses.dat0Table = "Section 1.5 % Transmission Lines (Columns: Line Reactance X in p.u., Susceptance B in p.u.)";
        lineReactiveLosses.out0Table = "|***** LINE & TRANSFORMER FLOWS *****| (Filter: Kind = Line, MVAR LOSS = Q_from + Q_to)";
        lineReactiveLosses.formulaProof = "Q_loss = 3 × I² × X - V² × B  [Line Series Reactance Consumption minus Shunt Capacitive Charging].";
        lineReactiveLosses.sourceColumns = "LINE FLOWS -> MVAr Loss";
        lineReactiveLosses.scalingSource = "Base Case line reactive losses";
        lineReactiveLosses.description = "Ranks transmission lines causing the highest reactive power absorption.";
        lineReactiveLosses.healthyRange = "Low net inductive consumption on lines";
        lineReactiveLosses.simpleExplanation = "Shows transmission lines consuming high magnetic reactive power, causing downstream voltage sag.";
        lineReactiveLosses.action = "Install series capacitors to cancel line reactance or shunt capacitors at line endpoints.";
        lineReactiveLosses.topN = 20;
        lineReactiveLosses.rowsFn = this::lineReactiveLossRows;
        lineReactiveLosses.insightFn = (base, cases) -> {
            double totalQ = base.branches().stream().filter(b -> "Line".equalsIgnoreCase(b.getKind())).mapToDouble(b -> b.mvarLoss).sum();
            return String.format("Total transmission line reactive losses: %.1f MVAr. High line reactive losses cause voltage decay along long transmission corridors.", totalQ);
        };
        categories.add(lineReactiveLosses);

        Category xfmrReactiveLosses = new Category();
        xfmrReactiveLosses.id = "xfmr_reactive_losses";
        xfmrReactiveLosses.shortName = "XFMR Q-Loss";
        xfmrReactiveLosses.group = "Loss Analysis";
        xfmrReactiveLosses.title = "Transformer Reactive Power Losses (MVAr)";
        xfmrReactiveLosses.axisLabel = "Transformer (From -> To)";
        xfmrReactiveLosses.unit = "MVAr loss";
        xfmrReactiveLosses.dat0Table = "Section 1.3 % Transformers (Columns: Leakage Reactance X in p.u.)";
        xfmrReactiveLosses.out0Table = "|***** LINE & TRANSFORMER FLOWS *****| (Filter: Kind = Transformer, MVAR LOSS = Q_from + Q_to)";
        xfmrReactiveLosses.formulaProof = "Q_loss_xfmr = 3 × I² × X_leakage  [Transformer Leakage Inductance Consumption].";
        xfmrReactiveLosses.sourceColumns = "TRANSFORMER FLOWS -> MVAr Loss";
        xfmrReactiveLosses.scalingSource = "Base Case transformer leakage reactive losses";
        xfmrReactiveLosses.description = "Ranks transformers consuming highest reactive power across their leakage reactance.";
        xfmrReactiveLosses.healthyRange = "Leakage VAr loss proportional to MVA through-flow (X_leakage ~ 8-15%)";
        xfmrReactiveLosses.simpleExplanation = "Shows transformers consuming reactive power due to internal magnetic leakage fields under heavy loading.";
        xfmrReactiveLosses.action = "Add local shunt capacitors on transformer secondary/tertiary busbars to relieve upstream grid of transformer reactive burden.";
        xfmrReactiveLosses.topN = 20;
        xfmrReactiveLosses.rowsFn = this::xfmrReactiveLossRows;
        xfmrReactiveLosses.insightFn = (base, cases) -> {
            double totalQ = base.branches().stream().filter(b -> "Transformer".equalsIgnoreCase(b.getKind())).mapToDouble(b -> b.mvarLoss).sum();
            return String.format("Total transformer reactive losses: %.1f MVAr. Supplying this locally prevents voltage drop across substation transformers.", totalQ);
        };
        categories.add(xfmrReactiveLosses);

        // 4. Power Flow & Transfers (Split Lines vs Transformers)
        Category lineFlow = new Category();
        lineFlow.id = "line_flow";
        lineFlow.shortName = "Line Flow";
        lineFlow.group = "Power Flow & Transfers";
        lineFlow.title = "Transmission Line Real Power Flow (MW)";
        lineFlow.axisLabel = "Line (From -> To)";
        lineFlow.unit = "MW";
        lineFlow.dat0Table = "Section 1.5 % Transmission Lines (Columns: Line R, X, B, FromBus, ToBus)";
        lineFlow.out0Table = "|***** LINE & TRANSFORMER FLOWS *****| (Filter: Kind = Line, Columns: FROM, TO, MW FLOW)";
        lineFlow.formulaProof = "P_ij = V_i² G_ij - V_i V_j (G_ij cos θ_ij + B_ij sin θ_ij)  [Line AC Transfer].";
        lineFlow.sourceColumns = "LINE FLOWS -> MW Flow";
        lineFlow.scalingSource = "Base Case solved line power flow magnitudes";
        lineFlow.description = "Identifies bulk transmission lines carrying the highest real power transfers.";
        lineFlow.healthyRange = "Balanced line flows (No single line carry >50% of corridor capacity)";
        lineFlow.simpleExplanation = "Identifies the highest loaded transmission lines transferring bulk power across the grid.";
        lineFlow.action = "Monitored for N-1 line outages and power wheeling transfer limits.";
        lineFlow.topN = 20;
        lineFlow.rowsFn = this::lineFlowRows;
        categories.add(lineFlow);

        Category xfmrFlow = new Category();
        xfmrFlow.id = "xfmr_flow";
        xfmrFlow.shortName = "XFMR Flow";
        xfmrFlow.group = "Power Flow & Transfers";
        xfmrFlow.title = "Transformer Real Power Flow (MW)";
        xfmrFlow.axisLabel = "Transformer (From -> To)";
        xfmrFlow.unit = "MW";
        xfmrFlow.dat0Table = "Section 1.3 % Transformers (Columns: FromBus, ToBus, MVA Rating)";
        xfmrFlow.out0Table = "|***** LINE & TRANSFORMER FLOWS *****| (Filter: Kind = Transformer, Columns: FROM, TO, MW FLOW)";
        xfmrFlow.formulaProof = "P_xfmr = V_from V_to / (a × X_k) × sin(θ_from - θ_to).";
        xfmrFlow.sourceColumns = "TRANSFORMER FLOWS -> MW Flow";
        xfmrFlow.scalingSource = "Base Case transformer MW through-flows";
        xfmrFlow.description = "Identifies substation transformers carrying highest bulk power step-up/step-down transfers.";
        xfmrFlow.healthyRange = "Through-power within continuous nameplate rating";
        xfmrFlow.simpleExplanation = "Shows the active power being converted between voltage levels by substation transformers.";
        xfmrFlow.action = "Key indicators for substation capacity expansion and transformer bank load sharing.";
        xfmrFlow.topN = 20;
        xfmrFlow.rowsFn = this::xfmrFlowRows;
        categories.add(xfmrFlow);

        Category reactiveBalance = new Category();
        reactiveBalance.id = "reactive_balance";
        reactiveBalance.shortName = "Q-Balance";
        reactiveBalance.group = "Power Flow & Transfers";
        reactiveBalance.title = "Reactive Power Balance by Bus (Net VAr Deficit/Surplus)";
        reactiveBalance.axisLabel = "Bus";
        reactiveBalance.unit = "MVAr";
        reactiveBalance.dat0Table = "Section 1.13 % Total Load & Section 1.12 % Total Gen+WindGen (MVAr Demand & Capabilities)";
        reactiveBalance.out0Table = "|***** BUS VOLTAGES AND POWERS *****| (Columns: MVAR LOAD, MVAR GEN, SHUNT MVAR)";
        reactiveBalance.formulaProof = "Q_net_deficit = Q_load - Q_gen - Q_shunt_cap.  Positive = Deficit (Inductive Draw), Negative = Surplus.";
        reactiveBalance.sourceColumns = "BUS VOLTAGES AND POWERS -> MVAr LOAD, MVAr GEN";
        reactiveBalance.scalingSource = "Base Case bus reactive dispatch values";
        reactiveBalance.description = "Net reactive imbalance (MVAr Load minus MVAr Generation) at each bus. Positive = reactive deficit (lagging), Negative = reactive surplus (leading).";
        reactiveBalance.healthyRange = "Net VAr Deficit near 0 MVAr (Local reactive power compensation)";
        reactiveBalance.simpleExplanation = "Reactive power cannot travel long distances across inductive lines without causing extreme voltage drops. This chart reveals substations that urgently require local capacitor banks or STATCOMs.";
        reactiveBalance.action = "High net reactive draw at a bus signals need for local VAr compensation (shunt capacitors, SVCs). This prevents voltage decay and reduces reactive power transmission losses. Reactive power cannot be transported efficiently over long distances — it must be generated locally.";
        reactiveBalance.topN = 20;
        reactiveBalance.rowsFn = this::reactiveBalanceRows;
        reactiveBalance.insightFn = (base, cases) -> {
            double totalDeficit = base.buses.stream()
                    .mapToDouble(b -> Math.max(0, b.mvarLoad - b.mvarGeneration)).sum();
            return String.format("Total reactive deficit across Base Case network: %.1f MVAr. Buses with high deficit must import reactive power via transmission lines, causing high I²R reactive losses and voltage drops.", totalDeficit);
        };
        categories.add(reactiveBalance);

        // 5. Generation & Load
        Category genDist = new Category();
        genDist.id = "generation_dist";
        genDist.shortName = "Generation";
        genDist.group = "Generation & Load";
        genDist.title = "Generation Distribution by Generating Bus";
        genDist.axisLabel = "Generating Bus";
        genDist.unit = "MW";
        genDist.dat0Table = "Section 1.12 % Total Gen+WindGen (Columns: BusNo, Pgen_scheduled, Qmin, Qmax, V_spec)";
        genDist.out0Table = "|***** BUS VOLTAGES AND POWERS *****| (Columns: NODE NO., FROM NAME, MW GEN, MVAR GEN)";
        genDist.formulaProof = "P_gen_bus = Solved Real Power Output in MW,  Q_gen_bus = Solved Reactive Output in MVAr within [Qmin, Qmax].";
        genDist.sourceColumns = "BUS VOLTAGES AND POWERS -> MW GEN (buses with Gen > 0)";
        genDist.scalingSource = "Base Case generator dispatch schedule";
        genDist.description = "Real power output profile across all active generating units.";
        genDist.healthyRange = "Dispersed spinning reserves (No single generator >25% of total grid load)";
        genDist.simpleExplanation = "Shows which power plants are supplying the bulk of the power. Heavy reliance on a single generator creates severe supply risk if that generator trips offline.";
        genDist.action = "Verifies spinning reserve dispersion and unit dispatch concentration risks.";
        genDist.topN = 15;
        genDist.rowsFn = this::generationDistRows;
        categories.add(genDist);

        Category loadDist = new Category();
        loadDist.id = "load_dist";
        loadDist.shortName = "Load";
        loadDist.group = "Generation & Load";
        loadDist.title = "Load Distribution across Major Load Centers";
        loadDist.axisLabel = "Load Bus";
        loadDist.unit = "MW";
        loadDist.dat0Table = "Section 1.13 % Total Load (Columns: BusNo, MW Load, MVAr Load, Load Model)";
        loadDist.out0Table = "|***** BUS VOLTAGES AND POWERS *****| (Columns: NODE NO., FROM NAME, MW LOAD, MVAR LOAD)";
        loadDist.formulaProof = "P_load = Active Power Demand in MW,  S_load = √(P_load² + Q_load²) in MVA.";
        loadDist.sourceColumns = "BUS VOLTAGES AND POWERS -> MW LOAD";
        loadDist.scalingSource = "Base Case bus load demand values";
        loadDist.description = "Ranks major consumption nodes across the power grid.";
        loadDist.healthyRange = "Dual-feed redundant supply for major load centers (>50 MW)";
        loadDist.simpleExplanation = "Identifies the highest electricity consuming cities, factories, and substations. These represent priority zones for power reliability and emergency load shedding schemes during grid emergencies.";
        loadDist.action = "Defines prioritization order for demand response, load shedding, and sub-transmission expansion.";
        loadDist.topN = 15;
        loadDist.rowsFn = this::loadDistRows;
        categories.add(loadDist);

        Category powerFactor = new Category();
        powerFactor.id = "power_factor";
        powerFactor.shortName = "Power Factor";
        powerFactor.group = "Generation & Load";
        powerFactor.title = "Bus Power Factor (Heavily Loaded Nodes)";
        powerFactor.axisLabel = "Bus";
        powerFactor.unit = "p.f.";
        powerFactor.dat0Table = "Section 1.13 % Total Load (Columns: MW Load, MVAr Load)";
        powerFactor.out0Table = "|***** BUS VOLTAGES AND POWERS *****| (Columns: MW LOAD, MVAR LOAD)";
        powerFactor.formulaProof = "Power Factor (PF) = P / S = P_load / √(P_load² + Q_load²).  Range: 0.0 to 1.0 (Lagging/Leading).";
        powerFactor.sourceColumns = "BUS VOLTAGES AND POWERS -> MW / √(MW² + MVAr²)";
        powerFactor.scalingSource = "Base Case load power factor ratios";
        powerFactor.description = "Computes power factor at heavily loaded buses; low pf denotes excessive reactive draw.";
        powerFactor.healthyRange = "≥ 0.90 lagging (Grid code power factor standard)";
        powerFactor.simpleExplanation = "Measures how efficiently load buses convert apparent electrical power into actual useful work. A low power factor (<0.90) means massive inductive currents are circulating unnecessarily, clogging transmission lines.";
        powerFactor.action = "Install shunt capacitor banks at low-pf nodes to unload upstream transmission lines.";
        powerFactor.topN = 15;
        powerFactor.rowsFn = this::powerFactorRows;
        categories.add(powerFactor);

        Category busMva = new Category();
        busMva.id = "bus_mva_intensity";
        busMva.shortName = "Bus MVA";
        busMva.group = "Generation & Load";
        busMva.title = "Bus MVA Loading Intensity (Apparent Power Demand)";
        busMva.axisLabel = "Load Bus";
        busMva.unit = "MVA";
        busMva.dat0Table = "Section 1.13 % Total Load (Columns: MW Load, MVAr Load)";
        busMva.out0Table = "|***** BUS VOLTAGES AND POWERS *****| (Columns: NODE NO., FROM NAME, MW LOAD, MVAR LOAD)";
        busMva.formulaProof = "S_demand = √(P_load² + Q_load²)  [Total Vector Apparent Power in MVA].";
        busMva.sourceColumns = "BUS VOLTAGES AND POWERS -> √(MW² + MVAr²) per bus";
        busMva.scalingSource = "Base Case bus apparent power (combined active + reactive demand)";
        busMva.description = "Apparent power (MVA) demand at load buses, combining both active and reactive components.";
        busMva.healthyRange = "Apparent MVA within substation transformer continuous rating";
        busMva.simpleExplanation = "Combines real power (MW) and reactive power (MVAr) into apparent power (MVA). This defines the total thermal ampacity required from the substation feeding transformers and cables.";
        busMva.action = "MVA demand at a bus determines total apparent power the feeding transformer and line must carry. High MVA buses with low power factor are prime targets for reactive compensation (capacitor banks) to reduce thermal stress on feeding equipment and improve voltage.";
        busMva.topN = 20;
        busMva.rowsFn = this::busMvaRows;
        busMva.insightFn = (base, cases) -> {
            var top = base.buses.stream().filter(b -> b.mwLoad > 0)
                    .max((a, b) -> Double.compare(Math.hypot(a.mwLoad, a.mvarLoad), Math.hypot(b.mwLoad, b.mvarLoad)));
            return top.map(b -> String.format("Highest MVA demand bus: %s %s at %.1f MVA (%.1f MW + %.1f MVAr). The feeding transformer must handle this full apparent power — check if rated adequately.",
                    b.number, b.name, Math.hypot(b.mwLoad, b.mvarLoad), b.mwLoad, b.mvarLoad))
                    .orElse("No load bus data available.");
        };
        categories.add(busMva);

        // 6. System Overview
        Category powerBalance = new Category();
        powerBalance.id = "power_balance";
        powerBalance.shortName = "Balance";
        powerBalance.group = "System Overview";
        powerBalance.title = "System Power Balance & Loss Share";
        powerBalance.axisLabel = "Quantity";
        powerBalance.unit = "MW / MVAr";
        powerBalance.dat0Table = "System Specifications & Section 1.2 Bus Demand Totals";
        powerBalance.out0Table = "|***** SYSTEM SUMMARY *****| (Columns: TOTAL GENERATION, TOTAL LOAD, TOTAL LOSSES in MW & MVAr)";
        powerBalance.formulaProof = "Conservation of Energy: Σ P_Gen = Σ P_Load + Σ P_Loss;  Σ Q_Gen = Σ Q_Load + Σ Q_Loss - Σ Q_Cap_Charging.";
        powerBalance.sourceColumns = "Summary Block -> Real/Reactive Generation, Load, Losses";
        powerBalance.scalingSource = "Base Case overall network totals";
        powerBalance.description = "System-wide generation, demand, and transmission loss breakdown.";
        powerBalance.healthyRange = "Loss Share < 4.0% of total generation, Power Balance ΔP ≈ 0";
        powerBalance.simpleExplanation = "Compares total electrical energy generated vs consumed vs lost across the entire network. The ratio of Losses to Total Generation measures the overall economic and electrical efficiency of the power system.";
        powerBalance.action = "Evaluates overall efficiency: loss percentage of total generation is the primary system health indicator.";
        powerBalance.topN = 0;
        powerBalance.rowsFn = this::powerBalanceRows;
        categories.add(powerBalance);

        Category convergence = new Category();
        convergence.id = "convergence";
        convergence.shortName = "Convergence";
        convergence.group = "System Overview";
        convergence.title = "Solved Case Quality, Convergence & Voltage Bounds";
        convergence.axisLabel = "Metric";
        convergence.unit = "";
        convergence.dat0Table = "Common Control Options (LFA Option, Tolerance, Max Iterations)";
        convergence.out0Table = "Load Flow Header Block & Solved System Summary (P/Q Iterations, Min/Max/Avg Voltages)";
        convergence.formulaProof = "Convergence Criterion: max(|ΔP_i|, |ΔQ_i|) < ε (typically 0.0001 p.u. within max 20 iterations).";
        convergence.sourceColumns = "Solver P/Q Iterations, Min/Max/Avg Voltages, Violation Counters";
        convergence.scalingSource = "Base Case numerical convergence tolerances";
        convergence.description = "Sanity verification for numerical convergence and power flow solvability.";
        convergence.healthyRange = "P Iterations ≤ 6, Q Iterations ≤ 8, Mismatch < 0.0001 p.u., 0 Violations";
        convergence.simpleExplanation = "Verifies mathematical solver convergence and numerical integrity of the power flow algorithm. Ensures results are physical and trustworthy before operators commit dispatch schedules.";
        convergence.action = "Ensure robust convergence before approving operational dispatch.";
        convergence.kpiOnly = true;
        convergence.topN = 0;
        convergence.rowsFn = this::convergenceRows;
        categories.add(convergence);

        // 7. Asset Utilization & Analytics
        Category underutilized = new Category();
        underutilized.id = "underutilized_lines";
        underutilized.shortName = "Underutilized";
        underutilized.group = "Asset Utilization & Analytics";
        underutilized.title = "Underutilized Transmission Lines (<30% Loading — Ferranti & Capital Asset Analysis)";
        underutilized.axisLabel = "Transmission Line";
        underutilized.unit = "% loading";
        underutilized.dat0Table = "Section 1.5 % Total Lines (Columns: Line Length, Voltage Rating, MVA Nominal Rating)";
        underutilized.out0Table = "|***** LINE & TRANSFORMER FLOWS *****| (Filter: Equipment Kind = Line, % LOADING < 30.0%)";
        underutilized.formulaProof = "% Loading = (S_flow / S_rating) × 100% < 30.0%.  Ferranti Charging: Q_c = V² × ωC × L (MVAr generated).";
        underutilized.sourceColumns = "LINE & TRANSFORMER FLOWS -> Loading % (<30%)";
        underutilized.scalingSource = "Base Case line loading percentages";
        underutilized.description = "Identifies transmission lines with low asset utilization (<30%). Highly underutilized EHV lines generate substantial capacitive charging (Ferranti effect: Qc = V²ωC), driving receiving bus voltages above limits and locking up capital without active power transfer.";
        underutilized.healthyRange = "≥ 30.0% loading (Prevents Ferranti effect capacitive overvoltage rise)";
        underutilized.simpleExplanation = "Identifies lines carrying little to no power (<30% loading). When long high-voltage lines are lightly loaded, their line capacitance generates excess reactive power (Ferranti effect), pushing receiving voltages dangerously high.";
        underutilized.action = "Operate shunt reactors at line terminals to absorb excess line charging, reconfigure bus splits, or transfer power from congested parallel routes to balance corridor utilization.";
        underutilized.topN = 20;
        underutilized.rowsFn = this::underutilizedLinesRows;
        underutilized.insightFn = (base, cases) -> {
            long underCount = base.branches().stream().filter(b -> "Line".equalsIgnoreCase(b.getKind()) && b.loadingPercent < 30.0).count();
            return String.format("Detected %d underutilized transmission line(s) (<30%% loading). These lines contribute to high Ferranti capacitive charging and under-utilized capital investment.", underCount);
        };
        categories.add(underutilized);
    }


    private List<MetricRow> voltageProfileRows(Out0Results target, Out0Results scaleRef) {
        double[] refV = scaleRef.buses.stream().mapToDouble(b -> b.voltagePu).toArray();
        if (refV.length == 0) return List.of();
        double min = Arrays.stream(refV).min().orElse(0.9);
        double max = Arrays.stream(refV).max().orElse(1.1);
        double bin = 0.02;
        double lo = Math.floor(min / bin) * bin;
        double hi = Math.ceil(max / bin) * bin;
        if (hi <= lo) hi = lo + bin;
        int nBins = Math.max(1, (int) Math.round((hi - lo) / bin));
        int[] counts = new int[nBins];
        for (Bus b : target.buses) {
            int idx = (int) Math.floor((b.voltagePu - lo) / bin);
            idx = Math.max(0, Math.min(nBins - 1, idx));
            counts[idx]++;
        }
        List<MetricRow> rows = new ArrayList<>();
        for (int i = 0; i < nBins; i++) {
            double a = lo + i * bin;
            rows.add(new MetricRow(String.format("%.2f-%.2f", a, a + bin), counts[i]));
        }
        return rows;
    }

    private List<MetricRow> voltageViolationRows(Out0Results target, Out0Results scaleRef) {
        List<MetricRow> rows = new ArrayList<>();
        for (Bus b : target.buses) {
            if (b.hasVoltageViolation()) {
                String flag = b.isBelowMinVoltage() ? "Below Min" : "Above Max";
                rows.add(new MetricRow(b.number + " " + b.name + " (" + flag + ")", b.voltagePu));
            }
        }
        rows.sort((a, c) -> Double.compare(a.baseValue, c.baseValue));
        return rows;
    }

    private List<MetricRow> branchLoadingRows(Out0Results target, Out0Results scaleRef) {
        return target.branches().stream()
                .sorted((a, b) -> Double.compare(b.loadingPercent, a.loadingPercent))
                .map(b -> new MetricRow(b.getKind() + " " + b.fromBus + "->" + b.toBus, b.loadingPercent))
                .collect(Collectors.toList());
    }

    private List<MetricRow> branchRankRows(Out0Results target, java.util.function.ToDoubleFunction<Branch> valueFn) {
        return target.branches().stream()
                .sorted((a, b) -> Double.compare(valueFn.applyAsDouble(b), valueFn.applyAsDouble(a)))
                .map(b -> new MetricRow(b.getKind() + " " + b.fromBus + "->" + b.toBus, valueFn.applyAsDouble(b)))
                .collect(Collectors.toList());
    }

    private List<MetricRow> overloadSummaryRows(Out0Results target, Out0Results scaleRef) {
        long overloaded = target.branches().stream().filter(Branch::isOverloaded).count();
        long high = target.branches().stream().filter(Branch::isHighlyLoaded).count();
        long total = target.branches().size();
        long normal = total - overloaded - high;
        List<MetricRow> rows = new ArrayList<>();
        rows.add(new MetricRow("Overloaded (>=100%)", overloaded));
        rows.add(new MetricRow("High Load (80-100%)", high));
        rows.add(new MetricRow("Normal (<80%)", normal));
        return rows;
    }

    private List<MetricRow> powerBalanceRows(Out0Results target, Out0Results scaleRef) {
        List<MetricRow> rows = new ArrayList<>();
        rows.add(new MetricRow("Real Generation (MW)", target.summary.getOrDefault("real_generation_mw", 0.0)));
        rows.add(new MetricRow("Real Load (MW)", target.summary.getOrDefault("real_load_mw", 0.0)));
        rows.add(new MetricRow("Real Loss (MW)", target.summary.getOrDefault("real_loss_mw", 0.0)));
        rows.add(new MetricRow("Reactive Gen (MVAr)", target.summary.getOrDefault("reactive_generation_mvar", 0.0)));
        rows.add(new MetricRow("Reactive Load (MVAr)", target.summary.getOrDefault("reactive_load_mvar", 0.0)));
        rows.add(new MetricRow("Reactive Loss (MVAr)", target.summary.getOrDefault("reactive_loss_mvar", 0.0)));
        return rows;
    }

    private List<MetricRow> generationDistRows(Out0Results target, Out0Results scaleRef) {
        return target.buses.stream()
                .filter(Bus::isGenerating)
                .sorted((a, b) -> Double.compare(b.mwGeneration, a.mwGeneration))
                .map(b -> new MetricRow(b.number + " " + b.name, b.mwGeneration))
                .collect(Collectors.toList());
    }

    private List<MetricRow> loadDistRows(Out0Results target, Out0Results scaleRef) {
        return target.buses.stream()
                .filter(b -> b.mwLoad > 0.0)
                .sorted((a, b) -> Double.compare(b.mwLoad, a.mwLoad))
                .map(b -> new MetricRow(b.number + " " + b.name, b.mwLoad))
                .collect(Collectors.toList());
    }

    private List<MetricRow> powerFactorRows(Out0Results target, Out0Results scaleRef) {
        return target.buses.stream()
                .filter(b -> b.mwLoad > 0.0)
                .sorted((a, b) -> Double.compare(b.mwLoad, a.mwLoad))
                .limit(40)
                .map(b -> {
                    double s = Math.hypot(b.mwLoad, b.mvarLoad);
                    double pf = s > 0 ? b.mwLoad / s : 1.0;
                    return new MetricRow(b.number + " " + b.name, pf);
                })
                .sorted((a, b) -> Double.compare(a.baseValue, b.baseValue))
                .collect(Collectors.toList());
    }

    private List<MetricRow> angleSpreadRows(Out0Results target, Out0Results scaleRef) {
        List<MetricRow> rows = new ArrayList<>();
        if (!target.buses.isEmpty()) {
            double maxAngle = target.buses.stream().mapToDouble(b -> b.angleDeg).max().orElse(0);
            double minAngle = target.buses.stream().mapToDouble(b -> b.angleDeg).min().orElse(0);
            rows.add(new MetricRow("Angle Spread (Max - Min)", maxAngle - minAngle));
        }
        target.buses.stream()
                .sorted((a, b) -> Double.compare(Math.abs(b.angleDeg), Math.abs(a.angleDeg)))
                .limit(14)
                .forEach(b -> rows.add(new MetricRow(b.number + " " + b.name, b.angleDeg)));
        return rows;
    }

    private List<MetricRow> convergenceRows(Out0Results target, Out0Results scaleRef) {
        var metrics = target.analysisMetrics();
        List<MetricRow> rows = new ArrayList<>();
        rows.add(new MetricRow("P Iterations", target.pIterations != null ? target.pIterations : 0));
        rows.add(new MetricRow("Q Iterations", target.qIterations != null ? target.qIterations : 0));
        rows.add(new MetricRow("Below-Min Voltage Violations", target.voltageMinViolations));
        rows.add(new MetricRow("Above-Max Voltage Violations", target.voltageMaxViolations));
        rows.add(new MetricRow("Minimum Voltage (p.u.)", metrics.minimumVoltagePu() != null ? metrics.minimumVoltagePu() : 0));
        rows.add(new MetricRow("Maximum Voltage (p.u.)", metrics.maximumVoltagePu() != null ? metrics.maximumVoltagePu() : 0));
        rows.add(new MetricRow("Average Voltage (p.u.)", metrics.averageVoltagePu() != null ? metrics.averageVoltagePu() : 0));
        return rows;
    }

    // ------------------------------------------------------------------- //
    // Additional Row Functions for Extended Analytics Categories
    // ------------------------------------------------------------------- //

    private List<MetricRow> reactiveBalanceRows(Out0Results target, Out0Results scaleRef) {
        return target.buses.stream()
                .filter(b -> b.mvarLoad > 0.0 || b.mvarGeneration > 0.0)
                .map(b -> new MetricRow(b.number + " " + b.name, b.mvarLoad - b.mvarGeneration))
                .sorted((a, c) -> Double.compare(Math.abs(c.baseValue), Math.abs(a.baseValue)))
                .limit(20)
                .collect(Collectors.toList());
    }

    private List<MetricRow> transformerLoadingRows(Out0Results target, Out0Results scaleRef) {
        return target.branches().stream()
                .filter(b -> "Transformer".equalsIgnoreCase(b.getKind()))
                .sorted((a, b) -> Double.compare(b.loadingPercent, a.loadingPercent))
                .map(b -> new MetricRow("XFMR " + b.fromBus + "->" + b.toBus, b.loadingPercent))
                .collect(Collectors.toList());
    }

    private List<MetricRow> lineLoadingRows(Out0Results target, Out0Results scaleRef) {
        return target.branches().stream()
                .filter(b -> "Line".equalsIgnoreCase(b.getKind()))
                .sorted((a, b) -> Double.compare(b.loadingPercent, a.loadingPercent))
                .map(b -> new MetricRow("LINE " + b.fromBus + "->" + b.toBus, b.loadingPercent))
                .collect(Collectors.toList());
    }

    private List<MetricRow> lineFlowRows(Out0Results target, Out0Results scaleRef) {
        return target.branches().stream()
                .filter(b -> "Line".equalsIgnoreCase(b.getKind()))
                .sorted((a, b) -> Double.compare(Math.abs(b.mwFlow), Math.abs(a.mwFlow)))
                .map(b -> new MetricRow("LINE " + b.fromBus + "->" + b.toBus, Math.abs(b.mwFlow)))
                .collect(Collectors.toList());
    }

    private List<MetricRow> xfmrFlowRows(Out0Results target, Out0Results scaleRef) {
        return target.branches().stream()
                .filter(b -> "Transformer".equalsIgnoreCase(b.getKind()))
                .sorted((a, b) -> Double.compare(Math.abs(b.mwFlow), Math.abs(a.mwFlow)))
                .map(b -> new MetricRow("XFMR " + b.fromBus + "->" + b.toBus, Math.abs(b.mwFlow)))
                .collect(Collectors.toList());
    }

    private List<MetricRow> lineRealLossRows(Out0Results target, Out0Results scaleRef) {
        return target.branches().stream()
                .filter(b -> "Line".equalsIgnoreCase(b.getKind()))
                .sorted((a, b) -> Double.compare(b.mwLoss, a.mwLoss))
                .map(b -> new MetricRow("LINE " + b.fromBus + "->" + b.toBus, b.mwLoss))
                .collect(Collectors.toList());
    }

    private List<MetricRow> xfmrRealLossRows(Out0Results target, Out0Results scaleRef) {
        return target.branches().stream()
                .filter(b -> "Transformer".equalsIgnoreCase(b.getKind()))
                .sorted((a, b) -> Double.compare(b.mwLoss, a.mwLoss))
                .map(b -> new MetricRow("XFMR " + b.fromBus + "->" + b.toBus, b.mwLoss))
                .collect(Collectors.toList());
    }

    private List<MetricRow> lineLossIntensityRows(Out0Results target, Out0Results scaleRef) {
        return target.branches().stream()
                .filter(b -> "Line".equalsIgnoreCase(b.getKind()))
                .sorted((a, b) -> Double.compare(b.mwLoss, a.mwLoss))
                .limit(20)
                .map(b -> new MetricRow("LINE " + b.fromBus + "->" + b.toBus, b.mwLoss))
                .collect(Collectors.toList());
    }

    private List<MetricRow> xfmrLossIntensityRows(Out0Results target, Out0Results scaleRef) {
        return target.branches().stream()
                .filter(b -> "Transformer".equalsIgnoreCase(b.getKind()))
                .sorted((a, b) -> Double.compare(b.mwLoss, a.mwLoss))
                .limit(20)
                .map(b -> new MetricRow("XFMR " + b.fromBus + "->" + b.toBus, b.mwLoss))
                .collect(Collectors.toList());
    }

    private List<MetricRow> lineReactiveLossRows(Out0Results target, Out0Results scaleRef) {
        return target.branches().stream()
                .filter(b -> "Line".equalsIgnoreCase(b.getKind()))
                .sorted((a, b) -> Double.compare(b.mvarLoss, a.mvarLoss))
                .limit(20)
                .map(b -> new MetricRow("LINE " + b.fromBus + "->" + b.toBus, b.mvarLoss))
                .collect(Collectors.toList());
    }

    private List<MetricRow> xfmrReactiveLossRows(Out0Results target, Out0Results scaleRef) {
        return target.branches().stream()
                .filter(b -> "Transformer".equalsIgnoreCase(b.getKind()))
                .sorted((a, b) -> Double.compare(b.mvarLoss, a.mvarLoss))
                .limit(20)
                .map(b -> new MetricRow("XFMR " + b.fromBus + "->" + b.toBus, b.mvarLoss))
                .collect(Collectors.toList());
    }

    private List<MetricRow> busMvaRows(Out0Results target, Out0Results scaleRef) {
        return target.buses.stream()
                .filter(b -> b.mwLoad > 0.0)
                .map(b -> new MetricRow(b.number + " " + b.name, Math.hypot(b.mwLoad, b.mvarLoad)))
                .sorted((a, c) -> Double.compare(c.baseValue, a.baseValue))
                .limit(20)
                .collect(Collectors.toList());
    }

    private List<MetricRow> voltageDeviationRows(Out0Results target, Out0Results scaleRef) {
        return target.buses.stream()
                .map(b -> new MetricRow(b.number + " " + b.name, (b.voltagePu - 1.0) * 100.0))
                .sorted((a, c) -> Double.compare(Math.abs(c.baseValue), Math.abs(a.baseValue)))
                .limit(20)
                .collect(Collectors.toList());
    }

    private List<MetricRow> underutilizedLinesRows(Out0Results target, Out0Results scaleRef) {
        return target.branches().stream()
                .filter(b -> "Line".equalsIgnoreCase(b.getKind()))
                .sorted((a, b) -> Double.compare(a.loadingPercent, b.loadingPercent))
                .limit(20)
                .map(b -> new MetricRow("LINE " + b.fromBus + "->" + b.toBus, b.loadingPercent))
                .collect(Collectors.toList());
    }

    private List<MetricRow> n1VulnerabilityRows(Out0Results target, Out0Results scaleRef) {
        return target.branches().stream()
                .filter(b -> b.loadingPercent >= 50.0)
                .sorted((a, b) -> Double.compare(b.loadingPercent, a.loadingPercent))
                .limit(20)
                .map(b -> new MetricRow(b.getKind() + " " + b.fromBus + "->" + b.toBus, b.loadingPercent))
                .collect(Collectors.toList());
    }
}

