package com.prdc.mipower.gui;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.prdc.mipower.models.CaseStudy;
import com.prdc.mipower.models.ChangeRecord;
import com.prdc.mipower.models.DatRecord;
import com.prdc.mipower.models.DatSection;
import com.prdc.mipower.models.HistoryEntry;
import com.prdc.mipower.models.RunResult;
import com.prdc.mipower.services.CaseStudyManager;
import com.prdc.mipower.services.CaseStudyStorage;
import com.prdc.mipower.services.ChangeResolver;
import com.prdc.mipower.services.ChangeResolver.FieldStatus;
import com.prdc.mipower.services.FieldEditabilityService;
import com.prdc.mipower.services.MiPowerRunner;
import com.prdc.mipower.utils.Constants;
import com.prdc.mipower.utils.ValidationUtils;

import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * The main editor. Implements the full hierarchical Case Study workflow
 * from the spec:
 *
 * <ul>
 *   <li>Browse ONE Base/Reference File, which is never modified.</li>
 *   <li>Build any number of Case Studies underneath it
 *       ({@link CaseStudyManager}), shown as a single linear list rather
 *       than a nested tree.</li>
 *   <li>Every section/record/field shows its real Unchanged / Local
 *       Change / Modified status ({@link ChangeResolver}), never
 *       hardcoded to one field like Slack Bus Angle.</li>
 *   <li>Every Case Study saves to its OWN file
 *       ({@link CaseStudyStorage}) -- Base File, parent, and siblings are
 *       never overwritten.</li>
 *   <li>Run MiPower automatically compares against the right reference:
 *       Base File's output for a root Case Study, the parent Case Study's
 *       output for a child.</li>
 * </ul>
 */
public class Workspace {

    private final String initialFilePath;
    private final FieldEditabilityService fieldEditability = new FieldEditabilityService();

    private Stage stage;
    private final CaseStudyManager manager = new CaseStudyManager();
    private ChangeResolver resolver;
    private CaseStudyStorage storage;

    /** null while the Base File node is selected in the tree. */
    private CaseStudy currentCaseStudy;
    private String currentSectionName;
    private List<DatRecord> currentRecords;

    // ---- widgets ---- //
    private TextField pathField;
    private Label fileStatusLabel;
    private ProgressIndicator progress;
    private Button browseBtn;
    private Button saveBtn;
    private Button runBtn;
    private Tooltip runBtnTooltip;
    private Button newCaseBtn;
    private Button renameBtn;
    private Button deleteBtn;
    private ComboBox<String> sectionCombo;
    private TextField searchField;
    private ComboBox<String> filterCombo;
    private TableView<DatRecord> table;
    private GridPane singleRecordPane;
    private ScrollPane singleRecordScroll;
    private Label statusLabel;
    private Label studyChipLabel;
    private Label breadcrumbLabel;
    private VBox historyListBox;
    private Label historyCountLabel;
    private Button undoLastBtn;
    private Button redoBtn;
    private Button clearAllBtn;
    private VBox recordDetailBox;

    // ---- inline Case Studies list, part of the unified sidebar card ---- //
    private VBox caseStudyListBox;

    private static final String FILTER_ALL = "All Records";
    private static final String FILTER_MODIFIED = "Modified Only";
    private static final String FILTER_UNCHANGED = "Unchanged";
    private static final String FILTER_INHERITED = "Inherited";
    private static final String FILTER_LOCAL = "Local Changes";
    private static final String FILTER_EDITABLE = "Editable";
    private static final String FILTER_NONEDITABLE = "Non-Editable";

    public Workspace(String initialFilePath) {
        this.initialFilePath = initialFilePath;
        this.resolver = new ChangeResolver(manager);
    }

    public void show(Stage stage) {
        this.stage = stage;
        stage.setTitle("PRDC Dynamic MiPower Input File Editor -- Workspace");

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");
        root.setTop(buildToolbar());

        BorderPane center = new BorderPane();
        center.setLeft(buildSidebar());
        center.setCenter(buildTableArea());
        center.setRight(buildRightPanel());
        root.setCenter(center);
        root.setBottom(buildStatusBar());

        // Size the window to whatever screen is actually available instead
        // of a fixed 1760x920 -- on a smaller/laptop screen that fixed size
        // pushed Save/Undo/Redo and other right-hand controls off-screen.
        javafx.geometry.Rectangle2D visualBounds = javafx.stage.Screen.getPrimary().getVisualBounds();
        double sceneWidth = Math.min(1760, visualBounds.getWidth());
        double sceneHeight = Math.min(920, visualBounds.getHeight());

        Scene scene = new Scene(root, sceneWidth, sceneHeight);
        scene.getStylesheets().add(getClass().getResource("/css/theme.css").toExternalForm());
        stage.setScene(scene);
        stage.setX(visualBounds.getMinX());
        stage.setY(visualBounds.getMinY());
        stage.setMaximized(true);
        stage.show();

        updateToolbarState();
        setStatus("Ready.");

        if (initialFilePath != null && !initialFilePath.isBlank()) {
            pathField.setText(initialFilePath);
            startParsing(initialFilePath);
        }
    }

    // ------------------------------------------------------------------- //
    private ToolBar buildToolbar() {
        ToolBar toolbar = new ToolBar();
        toolbar.getStyleClass().add("toolbar-bar");
        toolbar.setPadding(new Insets(10, 16, 10, 16));

        Label title = new Label("\uD83D\uDDF2 PRDC Dynamic MiPower File Editor");
        title.getStyleClass().add("toolbar-title");

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button reportsBtn = toolbarButton("\uD83D\uDCC4 Case Reports", this::openReportWindow);
        Button runHistoryBtn = toolbarButton("\uD83D\uDD52 Run History", this::openRunHistoryWindow);
        Button manageCasesBtn = toolbarButton("\uD83D\uDDC2 Manage Case Studies", this::openAllCaseStudiesDialog);
        Button analyticsBtn = toolbarButton("\uD83D\uDCC8 Analytics", this::openAnalyticsDashboard);

        saveBtn = primaryButton("\uD83D\uDCBE Save", this::onSave);
        runBtn = primaryButton("\u25B6 Run MiPower", this::onRunMiPower);
        runBtnTooltip = new Tooltip("Load a Base File first.");
        runBtn.setTooltip(runBtnTooltip);

        toolbar.getItems().addAll(title, spacer,
                reportsBtn, runHistoryBtn, manageCasesBtn, analyticsBtn, saveBtn, runBtn);
        return toolbar;
    }

    private Button toolbarButton(String text, Runnable action) {
        Button b = secondaryButton(text, action);
        b.setStyle(b.getStyle() + "-fx-text-fill: white; -fx-border-color: rgba(255,255,255,0.4);");
        return b;
    }

    private Button primaryButton(String text, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add("button-primary");
        b.setOnAction(e -> action.run());
        return b;
    }

    private Button secondaryButton(String text, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add("button-secondary");
        b.setOnAction(e -> action.run());
        return b;
    }

    // ------------------------------------------------------------------- //
    // Sidebar: Base File card + hierarchical Case Study tree
    // ------------------------------------------------------------------- //
    private VBox buildSidebar() {
        VBox sidebar = new VBox(12);
        sidebar.setPadding(new Insets(14));
        sidebar.setPrefWidth(340);
        sidebar.getStyleClass().add("sidebar");

        sidebar.getChildren().add(buildFileCard());
        sidebar.getChildren().add(buildCaseStudiesCard());
        return sidebar;
    }

    private VBox buildFileCard() {
        VBox card = new VBox(8);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(14));
        card.setStyle(card.getStyle() + "-fx-background-color: white;");

        Label label = new Label("\uD83D\uDCC1  Base / Reference File");
        label.getStyleClass().add("card-title");

        pathField = new TextField();
        pathField.setEditable(false);
        pathField.setPromptText("No file selected...");

        browseBtn = new Button("Browse");
        browseBtn.getStyleClass().add("button-primary");
        browseBtn.setMaxWidth(Double.MAX_VALUE);
        browseBtn.setOnAction(e -> onBrowse());

        fileStatusLabel = new Label("No Base File loaded yet. This file is the original reference "
                + "and is never modified directly -- create a Case Study to make changes.");
        fileStatusLabel.getStyleClass().add("muted-label");
        fileStatusLabel.setWrapText(true);

        studyChipLabel = new Label("");
        studyChipLabel.getStyleClass().add("muted-label");

        progress = new ProgressIndicator();
        progress.setPrefSize(18, 18);
        progress.setVisible(false);
        progress.setManaged(false);

        card.getChildren().addAll(label, pathField, browseBtn, fileStatusLabel, studyChipLabel, progress);
        return card;
    }

    /**
     * Replaces the old permanent Case Study Hierarchy tree panel. Only the
     * CURRENTLY OPEN case (or the Base File) is shown here, as a
     * breadcrumb -- the full tree lives in {@link AllCaseStudiesDialog},
     * opened from the toolbar, so it doesn't have to permanently occupy
     * sidebar space just to switch which case you're looking at.
     */
    private void openAllCaseStudiesDialog() {
        if (!manager.hasBaseFile()) {
            showAlert(AlertType.INFORMATION, "No Base File Loaded", "Load a Base File first.");
            return;
        }
        AllCaseStudiesDialog.show(stage, manager, currentCaseStudy, new AllCaseStudiesDialog.Callbacks() {
            @Override
            public void onOpen(CaseStudy cs) {
                selectCaseStudy(cs);
            }

            @Override
            public void onTreeChanged() {
                // The currently open case may have been renamed, or even
                // deleted, from inside the dialog -- re-validate.
                if (currentCaseStudy != null && manager.getById(currentCaseStudy.id) == null) {
                    selectCaseStudy(null);
                } else {
                    refreshCurrentCaseCard();
                }
                refreshCaseStudyListPanel();
            }
        });
    }

    private void refreshCurrentCaseCard() {
        if (!manager.hasBaseFile()) {
            breadcrumbLabel.setText("No Base File loaded.");
            return;
        }
        if (currentCaseStudy == null) {
            breadcrumbLabel.setText(Constants.BASE_FILE_LABEL + "  (read-only)");
            return;
        }
        StringBuilder sb = new StringBuilder(Constants.BASE_FILE_LABEL);
        for (CaseStudy ancestor : manager.ancestorChain(currentCaseStudy)) {
            sb.append("  \u203A  ").append(ancestor.name);
        }
        sb.append("  \u203A  ").append(currentCaseStudy.name);
        breadcrumbLabel.setText(sb.toString());
    }

    // ------------------------------------------------------------------- //
    // Unified "Case Studies" card -- merges what used to be two separate
    // sidebar sections (a "Current Case" breadcrumb card, plus a second,
    // largely duplicate "All Case Studies" list card underneath it) into
    // one single card: breadcrumb + New/Rename/Delete actions on top, then
    // every Case Study (Base File included) listed below, click-to-open.
    // ------------------------------------------------------------------- //
    private VBox buildCaseStudiesCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(14));
        card.setStyle(card.getStyle() + "-fx-background-color: white;");

        Label label = new Label("\uD83D\uDDC2  Case Studies");
        label.getStyleClass().add("card-title");
        label.setStyle(label.getStyle() + " -fx-font-size: 14.5px;");

        breadcrumbLabel = new Label("No Base File loaded.");
        breadcrumbLabel.setWrapText(true);
        breadcrumbLabel.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 600;");

        GridPane actions = new GridPane();
        actions.setHgap(6);
        actions.setVgap(6);
        newCaseBtn = secondaryButton("+ New Case", this::onNewCaseStudy);
        renameBtn = secondaryButton("Rename", this::onRenameCaseStudy);
        deleteBtn = secondaryButton("Delete", this::onDeleteCaseStudy);
        actions.add(newCaseBtn, 0, 0);
        actions.add(renameBtn, 1, 0);
        actions.add(deleteBtn, 0, 1, 2, 1);
        for (var b : List.of(newCaseBtn, renameBtn, deleteBtn)) {
            ((Button) b).setMaxWidth(Double.MAX_VALUE);
            GridPane.setHgrow((Button) b, Priority.ALWAYS);
        }

        Label listLabel = new Label("All Case Studies");
        listLabel.setStyle("-fx-font-size: 12.5px; -fx-font-weight: bold; -fx-padding: 6 0 0 0;");

        Label hint = new Label("Click any row below to open it as the Current Case above.");
        hint.getStyleClass().add("muted-label");
        hint.setWrapText(true);
        hint.setStyle("-fx-font-size: 11px;");

        caseStudyListBox = new VBox(4);

        card.getChildren().addAll(label, breadcrumbLabel, actions, listLabel, hint, caseStudyListBox);
        refreshCaseStudyListPanel();
        return card;
    }

    /** Rebuilds the inline list -- call after any create/rename/delete/duplicate
     *  AND whenever the Current Case selection changes, so the "open" highlight stays right. */
    private void refreshCaseStudyListPanel() {
        if (caseStudyListBox == null) {
            return;
        }
        caseStudyListBox.getChildren().clear();

        if (!manager.hasBaseFile()) {
            Label empty = new Label("Load a Base File to see Case Studies here.");
            empty.getStyleClass().add("muted-label");
            empty.setWrapText(true);
            empty.setStyle("-fx-font-size: 11.5px;");
            caseStudyListBox.getChildren().add(empty);
            return;
        }

        for (CaseStudyTreeModel.Node node : CaseStudyTreeModel.buildList(manager)) {
            caseStudyListBox.getChildren().add(buildCaseStudyRow(node));
        }
    }

    private HBox buildCaseStudyRow(CaseStudyTreeModel.Node node) {
        boolean isCurrent = node.isBase ? (currentCaseStudy == null) : (node.caseStudy == currentCaseStudy);

        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 8, 6, 8));
        row.getStyleClass().add("card");
        row.setCursor(javafx.scene.Cursor.HAND);
        row.setStyle("-fx-background-color: " + (isCurrent ? "#eaf2ff;" : "#fafafa;")
                + " -fx-background-radius: 6; -fx-border-color: "
                + (isCurrent ? "#4a86e8;" : "#e2e2e2;") + " -fx-border-radius: 6; -fx-border-width: 1;");

        VBox textBox = new VBox(1);
        Label nameLabel = new Label(node.isBase ? "Base File" : node.caseStudy.name);
        nameLabel.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 600;");
        String parentName = (node.parent != null) ? node.parent.name : "Base File";
        String refTag = node.isBase
                ? "Selected reference : Base File \u00B7 never modified"
                : "Selected reference : " + parentName + (isCurrent ? "  \u00B7  \u25CF open now" : "");
        Label refLabel = new Label(refTag);
        refLabel.getStyleClass().add("muted-label");
        refLabel.setStyle("-fx-font-size: 10.5px;");
        refLabel.setWrapText(true);
        textBox.getChildren().addAll(nameLabel, refLabel);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        row.setOnMouseClicked(e -> selectCaseStudy(node.isBase ? null : node.caseStudy));

        row.getChildren().add(textBox);
        return row;
    }

    private void selectCaseStudy(CaseStudy cs) {
        if (cs == null) {
            currentCaseStudy = null;
            showBaseFileInTable();
        } else {
            currentCaseStudy = cs;
            List<String> sectionNames = currentCaseStudy.parser.getSectionNames();
            sectionCombo.setDisable(false);
            sectionCombo.getItems().setAll(sectionNames);
            if (!sectionNames.isEmpty()) {
                sectionCombo.setValue(sectionNames.get(0));
                onSectionChanged(sectionNames.get(0));
            } else {
                table.getColumns().clear();
                table.getItems().clear();
            }
            setStatus("Switched to " + currentCaseStudy.name + " (reference: " + manager.referenceLabel(currentCaseStudy) + ")");
        }
        updateToolbarState();
        refreshHistoryPanel();
        refreshRecordDetail(null);
        refreshCurrentCaseCard();
        refreshCaseStudyListPanel();
    }

    private void showBaseFileInTable() {
        if (!manager.hasBaseFile()) {
            return;
        }
        List<String> sectionNames = manager.getBaseParser().getSectionNames();
        sectionCombo.setDisable(false);
        sectionCombo.getItems().setAll(sectionNames);
        if (!sectionNames.isEmpty()) {
            sectionCombo.setValue(sectionNames.get(0));
            onSectionChanged(sectionNames.get(0));
        }
        setStatus("Viewing Base File (read-only). Create a Case Study to make changes.");
    }

    // ------------------------------------------------------------------- //
    // Center: Section Explorer + searchable/filterable records table
    // ------------------------------------------------------------------- //
    private VBox buildTableArea() {
        VBox area = new VBox(8);
        area.setPadding(new Insets(14));
        area.getStyleClass().add("card");
        area.setStyle(area.getStyle() + "-fx-background-color: white;");
        VBox.setVgrow(area, Priority.ALWAYS);

        // FlowPane (not a fixed-width HBox) so that on a narrow window this
        // row WRAPS onto a second line instead of clipping/truncating its
        // first child -- that clipping is what used to make the "Section:"
        // label render as just "Se...".
        FlowPane sectionRow = new FlowPane(12, 8);
        sectionRow.setAlignment(Pos.CENTER_LEFT);
        Label sectionLabel = new Label("Section:");
        sectionLabel.getStyleClass().add("card-title");
        // Never let this label be compressed narrower than its own text --
        // the actual root cause of the "Se..." truncation.
        sectionLabel.setMinWidth(Region.USE_PREF_SIZE);
        sectionCombo = new ComboBox<>();
        sectionCombo.setPrefWidth(260);
        sectionCombo.setDisable(true);
        sectionCombo.setOnAction(e -> {
            String selected = sectionCombo.getValue();
            if (selected != null) {
                onSectionChanged(selected);
            }
        });

        searchField = new TextField();
        searchField.setPromptText("Search any field (From Bus, Bus Name, Generator ID, ...)");
        searchField.setPrefWidth(300);
        searchField.setMinWidth(180);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.textProperty().addListener((o, ov, nv) -> applyFilters());

        filterCombo = new ComboBox<>();
        filterCombo.getItems().addAll(FILTER_ALL, FILTER_MODIFIED, FILTER_UNCHANGED, FILTER_INHERITED,
                FILTER_LOCAL, FILTER_EDITABLE, FILTER_NONEDITABLE);
        filterCombo.setValue(FILTER_ALL);
        filterCombo.setPrefWidth(160);
        filterCombo.setOnAction(e -> applyFilters());

        sectionRow.getChildren().addAll(sectionLabel, sectionCombo, searchField, filterCombo);

        table = new TableView<>();
        table.setEditable(true);
        table.setPlaceholder(new Label("Select the Base File or a Case Study to see its sections and records."));
        VBox.setVgrow(table, Priority.ALWAYS);
        table.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> refreshRecordDetail(nv));

        // Compact view used instead of the table when a section has exactly
        // one record (e.g. System Specifications) -- a wide table with one
        // row wastes most of the screen. Shown instead as a plain two-column
        // list (Field Name | Value), one row per field, so nothing ever
        // needs a horizontal scrollbar.
        singleRecordPane = new GridPane();
        singleRecordPane.setHgap(18);
        singleRecordPane.setVgap(4);
        singleRecordPane.setPadding(new Insets(4, 4, 4, 4));
        ColumnConstraints nameColumn = new ColumnConstraints();
        nameColumn.setMinWidth(160);
        nameColumn.setPrefWidth(220);
        nameColumn.setHgrow(Priority.NEVER);
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);
        valueColumn.setFillWidth(true);
        singleRecordPane.getColumnConstraints().setAll(nameColumn, valueColumn);

        singleRecordScroll = new ScrollPane(singleRecordPane);
        singleRecordScroll.setFitToWidth(true);
        singleRecordScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        // ALWAYS (not AS_NEEDED) so a long section like "Common Control
        // Options" always shows a visible, grabbable scrollbar -- previously
        // some rows near the bottom were easy to miss because nothing on
        // screen hinted there was more to scroll to.
        singleRecordScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        singleRecordScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        singleRecordScroll.setVisible(false);
        singleRecordScroll.setManaged(false);
        VBox.setVgrow(singleRecordScroll, Priority.ALWAYS);

        Label hint = new Label("Every record and field is always visible, including static/non-editable ones. "
                + "\u270E editable  \uD83D\uDD12 read-only. Use the \"Modified/Unchanged/Inherited/Local\" "
                + "filter above to see which fields changed, without a dedicated Status column.");
        hint.getStyleClass().add("muted-label");
        hint.setWrapText(true);

        area.getChildren().addAll(sectionRow, table, singleRecordScroll, hint);
        return area;
    }

    private void onSectionChanged(String sectionName) {
        currentSectionName = sectionName;
        DatSection section;
        if (currentCaseStudy != null) {
            section = currentCaseStudy.parser.getSection(sectionName);
        } else {
            section = manager.hasBaseFile() ? manager.getBaseParser().getSection(sectionName) : null;
        }
        currentRecords = (section != null) ? new ArrayList<>(section.records) : List.of();
        List<String> columns = (section != null) ? section.fieldNames() : List.of();

        boolean commonControlOptions =
        "Common Control Options".equalsIgnoreCase(sectionName);

        boolean singleRecordView =
                currentRecords.size() == 1 || commonControlOptions;

        table.setVisible(!singleRecordView);
        table.setManaged(!singleRecordView);
        singleRecordScroll.setVisible(singleRecordView);
        singleRecordScroll.setManaged(singleRecordView);

        if (commonControlOptions) {
            buildCommonControlOptionsView(sectionName, currentRecords);

            if (!currentRecords.isEmpty()) {
                refreshRecordDetail(currentRecords.get(0));
            }

            return;
        }

        if (singleRecordView) {
            buildSingleRecordView(
                    sectionName,
                    currentRecords.get(0),
                    columns
            );

            refreshRecordDetail(currentRecords.get(0));
            return;
        }
        singleRecordPane.getChildren().clear();

        table.getColumns().clear();

        for (String fieldName : columns) {
            TableColumn<DatRecord, String> col = new TableColumn<>();
            boolean editable = (currentCaseStudy != null) && fieldEditability.isEditable(sectionName, fieldName);
            col.setText((editable ? "\u270E " : "\uD83D\uDD12 ") + fieldName);
            boolean colEditable = editable;
            col.setCellValueFactory(data -> {
                String value = data.getValue().fields.getOrDefault(fieldName, "");
                // Read-only cells show "--" for a genuinely blank value, matching
                // the single-record view, so a blank cell never looks like a
                // loading/display bug. Editable cells keep the raw value (which
                // may legitimately be "") so editing round-trips correctly.
                return new SimpleStringProperty((!colEditable && value.isBlank()) ? "--" : value);
            });
            col.setPrefWidth(Math.max(90, Math.min(220, 11 * Math.max(fieldName.length(), 6))));

            if (editable) {
                col.setCellFactory(TextFieldTableCell.forTableColumn());
                col.setEditable(true);
                col.setOnEditCommit(event -> handleEdit(event.getRowValue(), fieldName, event.getNewValue()));
            } else {
                col.setEditable(false);
            }
            table.getColumns().add(col);
        }

        applyFilters();
    }

    /**
     * Compact view used instead of the table when a section has exactly one
     * record (e.g. System Specifications): every field is one row of a
     * plain two-column grid -- Field Name in column 0, Value in column 1 --
     * instead of one wide table row that needs a horizontal scrollbar.
     * Editable fields (only possible inside a Case Study) get an inline
     * text box in the value column; everything else is a plain label.
     */
    private void buildSingleRecordView(String sectionName, DatRecord record, List<String> columns) {
        singleRecordPane.getChildren().clear();
        for (int row = 0; row < columns.size(); row++) {
            String fieldName = columns.get(row);
            boolean editable = (currentCaseStudy != null) && fieldEditability.isEditable(sectionName, fieldName);
            String value = record.fields.getOrDefault(fieldName, "").trim();
            String rowBg = (row % 2 == 1) ? "-fx-background-color: #F8FAFC;" : "";

            Label nameLabel = new Label((editable ? "\u270E " : "\uD83D\uDD12 ") + fieldName);
            nameLabel.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #374151; -fx-font-weight: bold; "
                    + "-fx-padding: 3 6 3 6;" + rowBg);
            nameLabel.setMaxWidth(Double.MAX_VALUE);
            singleRecordPane.add(nameLabel, 0, row);

            if (editable) {
                TextField valueField = new TextField(value);
                valueField.setStyle("-fx-font-size: 12.5px;");
                valueField.setMaxWidth(Double.MAX_VALUE);
                valueField.setOnAction(e -> handleEdit(record, fieldName, valueField.getText()));
                valueField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                    if (!isFocused) {
                        handleEdit(record, fieldName, valueField.getText());
                    }
                });
                GridPane.setHgrow(valueField, Priority.ALWAYS);
                singleRecordPane.add(valueField, 1, row);
            } else {
                Label valueLabel = new Label(value.isEmpty() ? "--" : value);
                valueLabel.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #111827; -fx-padding: 3 6 3 6;" + rowBg);
                valueLabel.setMaxWidth(Double.MAX_VALUE);
                GridPane.setHgrow(valueLabel, Priority.ALWAYS);
                singleRecordPane.add(valueLabel, 1, row);
            }
        }
    }

    private void buildCommonControlOptionsView(
        String sectionName,
        List<DatRecord> records) {

        singleRecordPane.getChildren().clear();

        int row = 0;

        for (DatRecord record : records) {

            for (Map.Entry<String, String> entry : record.fields.entrySet()) {

                String fieldName = entry.getKey();

                String value = entry.getValue() == null
                        ? ""
                        : entry.getValue().trim();

                boolean editable =
                        currentCaseStudy != null
                        && fieldEditability.isEditable(
                                sectionName,
                                fieldName
                        );

                String rowBg =
                        (row % 2 == 1)
                                ? "-fx-background-color: #F8FAFC;"
                                : "";

                Label nameLabel = new Label(
                        (editable ? "\u270E " : "\uD83D\uDD12 ")
                                + fieldName
                );

                nameLabel.setStyle(
                        "-fx-font-size: 12.5px; "
                        + "-fx-text-fill: #374151; "
                        + "-fx-font-weight: bold; "
                        + "-fx-padding: 3 6 3 6;"
                        + rowBg
                );

                nameLabel.setMaxWidth(Double.MAX_VALUE);

                singleRecordPane.add(
                        nameLabel,
                        0,
                        row
                );

                if (editable) {

                    TextField valueField =
                            new TextField(value);

                    valueField.setStyle(
                            "-fx-font-size: 12.5px;"
                    );

                    valueField.setMaxWidth(
                            Double.MAX_VALUE
                    );

                    valueField.setOnAction(
                            e -> handleEdit(
                                    record,
                                    fieldName,
                                    valueField.getText()
                            )
                    );

                    valueField.focusedProperty().addListener(
                            (obs, wasFocused, isFocused) -> {

                                if (!isFocused) {
                                    handleEdit(
                                            record,
                                            fieldName,
                                            valueField.getText()
                                    );
                                }
                            }
                    );

                    GridPane.setHgrow(
                            valueField,
                            Priority.ALWAYS
                    );

                    singleRecordPane.add(
                            valueField,
                            1,
                            row
                    );

                } else {

                    Label valueLabel = new Label(
                            value.isEmpty()
                                    ? "--"
                                    : value
                    );

                    valueLabel.setStyle(
                            "-fx-font-size: 12.5px; "
                            + "-fx-text-fill: #111827; "
                            + "-fx-padding: 3 6 3 6;"
                            + rowBg
                    );

                    valueLabel.setMaxWidth(
                            Double.MAX_VALUE
                    );

                    GridPane.setHgrow(
                            valueLabel,
                            Priority.ALWAYS
                    );

                    singleRecordPane.add(
                            valueLabel,
                            1,
                            row
                    );
                }

                row++;
            }
        }
    }

    private void applyFilters() {
        if (currentRecords == null) {
            table.getItems().clear();
            return;
        }
        String query = (searchField.getText() == null) ? "" : searchField.getText().strip().toLowerCase();
        String filter = filterCombo.getValue();
        List<DatRecord> filtered = new ArrayList<>();
        for (DatRecord r : currentRecords) {
            if (!query.isEmpty() && !matchesSearch(r, query)) {
                continue;
            }
            if (!matchesFilter(r, filter)) {
                continue;
            }
            filtered.add(r);
        }
        table.getItems().setAll(filtered);
    }

    private boolean matchesSearch(DatRecord record, String query) {
        for (String value : record.fields.values()) {
            if (value != null && value.toLowerCase().contains(query)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesFilter(DatRecord record, String filter) {
        if (filter == null || FILTER_ALL.equals(filter)) {
            return true;
        }
        if (FILTER_EDITABLE.equals(filter)) {
            return record.fields.keySet().stream().anyMatch(f -> fieldEditability.isEditable(currentSectionName, f));
        }
        if (FILTER_NONEDITABLE.equals(filter)) {
            return record.fields.keySet().stream().noneMatch(f -> fieldEditability.isEditable(currentSectionName, f));
        }
        if (currentCaseStudy == null) {
            // Status filters are meaningless on the read-only Base File view.
            return FILTER_UNCHANGED.equals(filter);
        }
        FieldStatus status = resolver.recordStatus(currentCaseStudy, currentSectionName, record);
        return switch (filter) {
            case FILTER_MODIFIED -> status == FieldStatus.MODIFIED;
            case FILTER_UNCHANGED -> status == FieldStatus.UNCHANGED;
            case FILTER_INHERITED -> status == FieldStatus.INHERITED;
            case FILTER_LOCAL -> status == FieldStatus.LOCAL_CHANGE;
            default -> true;
        };
    }

    // ------------------------------------------------------------------- //
    // Right panel: Change History + Record Detail, in tabs
    // ------------------------------------------------------------------- //
    private VBox buildRightPanel() {
        VBox panel = new VBox(8);
        panel.setPrefWidth(360);
        VBox.setVgrow(panel, Priority.ALWAYS);

        // The "Record Detail" panel itself is still built -- it initializes
        // recordDetailBox, which refreshRecordDetail() (fired on every table
        // row click) still writes to -- but is no longer shown as a tab.
        buildRecordDetailPanel();

        TabPane tabs = new TabPane();
        tabs.getTabs().add(new Tab("Change History", buildHistoryPanel()));
        for (Tab t : tabs.getTabs()) {
            t.setClosable(false);
        }
        VBox.setVgrow(tabs, Priority.ALWAYS);
        panel.getChildren().add(tabs);
        return panel;
    }

    private VBox buildRecordDetailPanel() {
        VBox panel = new VBox(8);
        panel.setPadding(new Insets(14));

        Label title = new Label("\uD83D\uDD0E  Record Details");
        title.getStyleClass().add("card-title");
        Label hint = new Label("Click a row in the table to see every field's Original (Base File) vs "
                + "Current value, and why it changed.");
        hint.getStyleClass().add("muted-label");
        hint.setWrapText(true);

        recordDetailBox = new VBox(6);
        ScrollPane scroll = new ScrollPane(recordDetailBox);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        panel.getChildren().addAll(title, hint, scroll);
        VBox.setVgrow(panel, Priority.ALWAYS);
        return panel;
    }

    private void refreshRecordDetail(DatRecord record) {
        recordDetailBox.getChildren().clear();
        if (record == null || currentSectionName == null) {
            Label empty = new Label("No record selected.");
            empty.getStyleClass().add("muted-label");
            recordDetailBox.getChildren().add(empty);
            return;
        }

        Label recordLabel = new Label(currentSectionName + "  \u2014  " + record.label());
        recordLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        recordDetailBox.getChildren().add(recordLabel);

        if (currentCaseStudy != null) {
            Label refLabel = new Label("Reference: " + manager.referenceLabel(currentCaseStudy)
                    + "   \u2192   Current: " + currentCaseStudy.name);
            refLabel.getStyleClass().add("muted-label");
            recordDetailBox.getChildren().add(refLabel);

            for (var detail : resolver.recordDetail(currentCaseStudy, currentSectionName, record, fieldEditability)) {
                recordDetailBox.getChildren().add(buildFieldDetailRow(detail));
            }
        } else {
            for (Map.Entry<String, String> e : record.fields.entrySet()) {
                Label row = new Label(e.getKey() + " = " + e.getValue()
                        + (fieldEditability.isEditable(currentSectionName, e.getKey()) ? "  (editable in a Case Study)" : "  (read-only)"));
                row.setStyle("-fx-font-size: 12px;");
                row.setWrapText(true);
                recordDetailBox.getChildren().add(row);
            }
        }
    }

    private VBox buildFieldDetailRow(ChangeResolver.FieldDetail detail) {
        VBox row = new VBox(2);
        row.setPadding(new Insets(6, 8, 6, 8));
        row.setStyle("-fx-background-color: #F8FAFC; -fx-background-radius: 6; -fx-border-color: "
                + detail.status().color + "; -fx-border-width: 0 0 0 3;");

        HBox top = new HBox(6);
        Label name = new Label((detail.editable() ? "\u270E " : "\uD83D\uDD12 ") + detail.fieldName());
        name.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        Label status = new Label(detail.status().glyph + " " + detail.status().label);
        status.setStyle("-fx-font-size: 10px; -fx-text-fill: " + detail.status().color + ";");
        top.getChildren().addAll(name, status);

        Label values = new Label(detail.changed()
                ? ("Original: " + detail.originalValue() + "   \u2192   Current: " + detail.currentValue())
                : ("Value: " + detail.currentValue()));
        values.setStyle("-fx-font-size: 12px;");
        values.setWrapText(true);

        row.getChildren().addAll(top, values);
        return row;
    }

    // ------------------------------------------------------------------- //
    // Change History panel (unchanged behavior from the flat-model version,
    // just re-wired to the currently selected Case Study from the tree)
    // ------------------------------------------------------------------- //
    private VBox buildHistoryPanel() {
        VBox panel = new VBox(8);
        panel.setPadding(new Insets(14));

        historyCountLabel = new Label("No changes yet.");
        historyCountLabel.getStyleClass().add("muted-label");

        historyListBox = new VBox(6);
        ScrollPane scroll = new ScrollPane(historyListBox);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        HBox actions = new HBox(6);
        undoLastBtn = secondaryButton("Undo Last", this::onUndoLast);
        redoBtn = secondaryButton("Redo", this::onRedo);
        clearAllBtn = secondaryButton("Clear All", this::onClearAll);
        undoLastBtn.setDisable(true);
        redoBtn.setDisable(true);
        clearAllBtn.setDisable(true);
        actions.getChildren().addAll(undoLastBtn, redoBtn, clearAllBtn);

        panel.getChildren().addAll(historyCountLabel, scroll, actions);
        VBox.setVgrow(panel, Priority.ALWAYS);
        return panel;
    }

    private void refreshHistoryPanel() {
        historyListBox.getChildren().clear();
        if (currentCaseStudy == null || currentCaseStudy.history.isEmpty()) {
            historyCountLabel.setText(currentCaseStudy == null
                    ? "Base File is read-only -- select or create a Case Study to see its changes."
                    : "No changes yet.");
            Label empty = new Label(currentCaseStudy == null ? "" : "Edit a cell to see it appear here.");
            empty.getStyleClass().add("muted-label");
            historyListBox.getChildren().add(empty);
            undoLastBtn.setDisable(true);
            redoBtn.setDisable(true);
            clearAllBtn.setDisable(true);
            return;
        }

        List<HistoryEntry> entries = new ArrayList<>(currentCaseStudy.history);
        java.util.Collections.reverse(entries);
        for (HistoryEntry entry : entries) {
            historyListBox.getChildren().add(buildHistoryRow(entry));
        }

        var counts = currentCaseStudy.statusCounts();
        historyCountLabel.setText(counts.total() + " total  \u00b7  " + counts.pending() + " Pending  \u00b7  "
                + counts.saved() + " Saved  \u00b7  " + counts.undone() + " Undone  \u00b7  "
                + counts.deleted() + " Deleted");

        undoLastBtn.setDisable(!currentCaseStudy.modManager.canUndo());
        redoBtn.setDisable(!currentCaseStudy.modManager.canRedo());
        clearAllBtn.setDisable(currentCaseStudy.modManager.count() == 0);
    }

    private VBox buildHistoryRow(HistoryEntry entry) {
        VBox row = new VBox(3);
        row.setPadding(new Insets(8, 10, 8, 10));
        String accentColor = switch (entry.getStatus()) {
            case Constants.STATUS_PENDING -> "#F59E0B";
            case Constants.STATUS_SAVED -> "#10B981";
            case Constants.STATUS_UNDONE -> "#9CA3AF";
            case Constants.STATUS_DELETED -> "#EF4444";
            default -> "#D1D5DB";
        };
        row.setStyle("-fx-background-color: #F8FAFC; -fx-background-radius: 8; "
                + "-fx-border-color: " + accentColor + "; -fx-border-width: 0 0 0 3;");

        HBox statusRow = new HBox(6);
        Label statusLabel = new Label(entry.getStatus());
        statusLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + accentColor + ";");
        Label sectionLabel = new Label(entry.getSection());
        sectionLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #6B7280;");
        statusRow.getChildren().addAll(statusLabel, sectionLabel);

        Label fieldLabel = new Label((entry.getField() != null ? entry.getField() : "(value)"));
        fieldLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");

        // Primary-key row identifier -- prefer the human-readable record
        // label captured when the edit was made (e.g. "Bus 1604002 (TEEG1)"
        // or "Line 1604002 -> 1604003"); fall back to the raw match
        // conditions if no label was recorded, so it's always clear which
        // exact row in the section this change came from, not just which
        // field/section.
        String rowKey = entry.getRecordLabel();
        if (rowKey == null || rowKey.isBlank()) {
            rowKey = entry.getChange().formatConditionStr();
        }
        Label rowKeyLabel = new Label("Row: " + rowKey);
        rowKeyLabel.setWrapText(true);
        rowKeyLabel.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #4B5563; -fx-font-weight: 600;");

        Label changeLabel = new Label(entry.getOldValue() + "  \u2192  " + entry.getNewValue());
        changeLabel.setStyle("-fx-font-size: 12px;");

        Label timeLabel = new Label(entry.getTimestamp());
        timeLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: #9CA3AF;");

        row.getChildren().addAll(statusRow, fieldLabel, rowKeyLabel, changeLabel, timeLabel);

        if (entry.isActive()) {
            Button removeBtn = new Button("Remove");
            removeBtn.setStyle("-fx-font-size: 10px; -fx-background-color: transparent; "
                    + "-fx-text-fill: #EF4444; -fx-border-color: #EF4444; -fx-border-radius: 6; "
                    + "-fx-background-radius: 6; -fx-padding: 2 8 2 8; -fx-cursor: hand;");
            removeBtn.setOnAction(e -> onRemoveEntry(entry));
            row.getChildren().add(removeBtn);
        }

        return row;
    }

    private void revertFieldValue(ChangeRecord change) {
        if (currentCaseStudy == null) {
            return;
        }
        DatSection section = currentCaseStudy.parser.getSection(change.section);
        if (section == null) {
            return;
        }
        List<DatRecord> matches = section.recordsWithCondition(change.conditions);
        String fieldToRevert = (change.field != null) ? change.field : "Value";
        for (DatRecord record : matches) {
            if (record.fields.containsKey(fieldToRevert)) {
                record.fields.put(fieldToRevert, change.oldValue);
            }
        }
        if (currentSectionName != null && currentSectionName.equals(change.section)) {
            table.refresh();
        }
    }

    private void reapplyFieldValue(ChangeRecord change) {
        if (currentCaseStudy == null) {
            return;
        }
        DatSection section = currentCaseStudy.parser.getSection(change.section);
        if (section == null) {
            return;
        }
        List<DatRecord> matches = section.recordsWithCondition(change.conditions);
        String fieldToApply = (change.field != null) ? change.field : "Value";
        for (DatRecord record : matches) {
            if (record.fields.containsKey(fieldToApply)) {
                record.fields.put(fieldToApply, change.newValue);
            }
        }
        if (currentSectionName != null && currentSectionName.equals(change.section)) {
            table.refresh();
        }
    }

    private void onRemoveEntry(HistoryEntry entry) {
        ChangeRecord change = entry.getChange();
        List<ChangeRecord> pending = currentCaseStudy.modManager.getPending();
        int idx = pending.indexOf(change);
        if (idx >= 0) {
            currentCaseStudy.modManager.removeChange(idx);
        }
        revertFieldValue(change);
        entry.setStatus(Constants.STATUS_UNDONE);
        refreshHistoryPanel();
        applyFilters();
        updateToolbarState();
        setStatus("Removed: " + entry.getSection() + " / " + (entry.getField() != null ? entry.getField() : "(value)"));
    }

    private void onUndoLast() {
        if (currentCaseStudy == null) {
            return;
        }
        ChangeRecord undone = currentCaseStudy.modManager.undo();
        if (undone == null) {
            return;
        }
        revertFieldValue(undone);
        markMostRecentActiveEntryStatus(undone, Constants.STATUS_UNDONE);
        refreshHistoryPanel();
        applyFilters();
        updateToolbarState();
        setStatus("Undid last change in " + currentCaseStudy.name);
    }

    private void onRedo() {
        if (currentCaseStudy == null) {
            return;
        }
        ChangeRecord redone = currentCaseStudy.modManager.redo();
        if (redone == null) {
            return;
        }
        reapplyFieldValue(redone);
        markMostRecentActiveEntryStatus(redone, Constants.STATUS_PENDING);
        refreshHistoryPanel();
        applyFilters();
        updateToolbarState();
        setStatus("Redid change in " + currentCaseStudy.name);
    }

    private void onClearAll() {
        if (currentCaseStudy == null || currentCaseStudy.modManager.count() == 0) {
            return;
        }
        Alert confirm = new Alert(AlertType.CONFIRMATION,
                "Remove all " + currentCaseStudy.modManager.count() + " pending change(s) in "
                        + currentCaseStudy.name + "? This reverts every unsaved edit.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Clear All Changes");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(response -> {
            if (response != ButtonType.YES) {
                return;
            }
            for (ChangeRecord change : currentCaseStudy.modManager.getPending()) {
                revertFieldValue(change);
            }
            currentCaseStudy.modManager.clear();
            for (HistoryEntry entry : currentCaseStudy.history) {
                if (entry.isActive()) {
                    entry.setStatus(Constants.STATUS_DELETED);
                }
            }
            refreshHistoryPanel();
            applyFilters();
            updateToolbarState();
            setStatus("Cleared all pending changes in " + currentCaseStudy.name);
        });
    }

    private void markMostRecentActiveEntryStatus(ChangeRecord change, String status) {
        for (int i = currentCaseStudy.history.size() - 1; i >= 0; i--) {
            HistoryEntry entry = currentCaseStudy.history.get(i);
            if (entry.getChange() == change) {
                entry.setStatus(status);
                return;
            }
        }
    }

    private HBox buildStatusBar() {
        HBox bar = new HBox();
        bar.getStyleClass().add("status-bar");
        bar.setPadding(new Insets(6, 14, 6, 14));
        statusLabel = new Label("Ready.");
        statusLabel.getStyleClass().add("muted-label");
        bar.getChildren().add(statusLabel);
        return bar;
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }

    // ------------------------------------------------------------------- //
    // Base File browsing / parsing (PART 1)
    // ------------------------------------------------------------------- //
    private void onBrowse() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select the MiPower Base/Reference Input File");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("MiPower Input Files (*.dat0)", "*.dat0"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All Files", "*.*"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        pathField.setText(file.getAbsolutePath());
        startParsing(file.getAbsolutePath());
    }

    private void startParsing(String path) {
        browseBtn.setDisable(true);
        progress.setVisible(true);
        progress.setManaged(true);
        fileStatusLabel.setText("Parsing sections, records, and fields...");
        setStatus("Parsing Base File...");

        Task<Object> task = new Task<>() {
            @Override
            protected Object call() {
                try {
                    manager.loadBaseFile(path);
                    return manager;
                } catch (Exception e) {
                    return e;
                }
            }
        };
        task.setOnSucceeded(e -> onParseFinished(task.getValue()));
        task.setOnFailed(e -> onParseFinished(task.getException()));
        new Thread(task).start();
    }

    private void onParseFinished(Object result) {
        progress.setVisible(false);
        progress.setManaged(false);
        browseBtn.setDisable(false);

        if (result instanceof Throwable t) {
            fileStatusLabel.setText("Failed to parse the Base File: " + t.getMessage());
            fileStatusLabel.getStyleClass().setAll("status-danger");
            showAlert(AlertType.ERROR, "Parse Error", "Failed to parse the Base File:\n" + t.getMessage());
            return;
        }

        storage = new CaseStudyStorage(manager.getBaseFilePath());
        List<String> sectionNames = manager.getBaseParser().getSectionNames();
        fileStatusLabel.setText("\u2713 Loaded -- " + sectionNames.size() + " section(s) detected. "
                + "This is the reference; it is never modified.");
        fileStatusLabel.getStyleClass().setAll("status-success");
        studyChipLabel.setText("Study: " + manager.getBaseParser().detectStudyType());

        currentCaseStudy = null;
        selectCaseStudy(null);
        updateToolbarState();
        setStatus("Loaded Base File: " + manager.getBaseFilePath());
    }

    // ------------------------------------------------------------------- //
    // Case Study management (PART 1: hierarchical create/rename/duplicate/delete)
    // ------------------------------------------------------------------- //
    private void onNewCaseStudy() {
        if (!manager.hasBaseFile()) {
            showAlert(AlertType.INFORMATION, "No Base File Loaded", "Load a Base File first.");
            return;
        }
        // Corrected New Case dialog: always shows the WHOLE Case Study tree
        // (not just Base File vs. whatever happens to be selected right
        // now) so the user can pick literally any existing case as the
        // reference, from any screen. Opening the new case immediately
        // means its inherited data (everything the reference had, plus
        // this case's own future edits) is visible right away.
        NewCaseDialog.showAndCreate(stage, manager, currentCaseStudy).ifPresent(cs -> {
            selectCaseStudy(cs);
            setStatus(cs.name + " created -- reference: " + manager.referenceLabel(cs) + ".");
        });
    }

    private void onRenameCaseStudy() {
        if (currentCaseStudy == null) {
            showAlert(AlertType.INFORMATION, "No Case Study Selected", "Select a Case Study to rename.");
            return;
        }
        TextInputDialog dialog = new TextInputDialog(currentCaseStudy.name);
        dialog.setTitle("Rename Case Study");
        dialog.setHeaderText(null);
        dialog.setContentText("New name (the underlying reference/hierarchy is unaffected):");
        dialog.showAndWait().ifPresent(newName -> {
            if (manager.rename(currentCaseStudy, newName)) {
                refreshCurrentCaseCard();
                refreshCaseStudyListPanel();
            } else {
                showAlert(AlertType.WARNING, "Rename Failed", "That name is empty or already in use.");
            }
        });
    }

    private void onDuplicateCaseStudy() {
        if (currentCaseStudy == null) {
            showAlert(AlertType.INFORMATION, "No Case Study Selected", "Select a Case Study to duplicate.");
            return;
        }
        try {
            var result = manager.duplicate(currentCaseStudy, currentCaseStudy.name + " (Copy)");
            selectCaseStudy(result.caseStudy());
            setStatus(result.caseStudy().name + " created -- " + result.replayed() + " change(s) replayed"
                    + (result.skipped() > 0 ? ", " + result.skipped() + " skipped (row no longer uniquely identifiable)." : "."));
        } catch (IOException e) {
            showAlert(AlertType.ERROR, "Error", "Could not duplicate the Case Study:\n" + e.getMessage());
        }
    }

    private void onDeleteCaseStudy() {
        if (currentCaseStudy == null) {
            showAlert(AlertType.INFORMATION, "No Case Study Selected", "Select a Case Study to delete.");
            return;
        }
        int childCount = countDescendants(currentCaseStudy);
        String warning = "Delete " + currentCaseStudy.name
                + (childCount > 0 ? " and its " + childCount + " child Case Study(ies)" : "")
                + "? This cannot be undone.";
        Alert confirm = new Alert(AlertType.CONFIRMATION, warning, ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Delete Case Study");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                manager.delete(currentCaseStudy);
                selectCaseStudy(null);
            }
        });
    }

    private int countDescendants(CaseStudy cs) {
        int count = 0;
        for (CaseStudy child : manager.childrenOf(cs)) {
            count += 1 + countDescendants(child);
        }
        return count;
    }

    // ------------------------------------------------------------------- //
    // Table editing
    // ------------------------------------------------------------------- //
    private Map<String, String> buildRowConditions(DatRecord record) {
        if (currentRecords != null && currentRecords.size() == 1) {
            return new LinkedHashMap<>();
        }
        Map<String, String> locked = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : record.fields.entrySet()) {
            if (!fieldEditability.isEditable(currentSectionName, e.getKey())) {
                locked.put(e.getKey(), e.getValue());
            }
        }
        if (!locked.isEmpty()) {
            return locked;
        }
        Map<String, String> fallback = new LinkedHashMap<>();
        for (String kf : record.keyFields) {
            if (record.fields.containsKey(kf)) {
                fallback.put(kf, record.fields.get(kf));
            }
        }
        return fallback;
    }

    private void handleEdit(DatRecord record, String fieldName, String newValueRaw) {
        if (currentCaseStudy == null) {
            return; // Base File is read-only.
        }
        String newValue = (newValueRaw == null) ? "" : newValueRaw.strip();
        String oldValue = record.fields.getOrDefault(fieldName, "");
        if (newValue.equals(oldValue)) {
            return;
        }

        String dtype = ValidationUtils.detectType(oldValue);
        ValidationUtils.ValidationResult validation = ValidationUtils.validateValue(newValue, dtype);
        if (!validation.valid()) {
            showAlert(AlertType.WARNING, "Invalid Value", validation.message());
            table.refresh();
            return;
        }

        Map<String, String> conditions = new LinkedHashMap<>();
        Integer targetRow = null;
        if ("tabular".equals(record.formatType) || "two_row_table".equals(record.formatType)) {
            conditions = buildRowConditions(record);
            if ("two_row_table".equals(record.formatType)) {
                targetRow = record.fieldRows.get(fieldName);
            }
        }

        ChangeRecord change = new ChangeRecord(
                currentSectionName,
                "simple".equals(record.formatType) ? null : fieldName,
                oldValue, newValue, record.formatType,
                conditions, targetRow, record.label());
        currentCaseStudy.modManager.addChange(change);
        currentCaseStudy.addHistoryEntry(change);

        record.fields.put(fieldName, newValue);
        table.refresh();
        refreshHistoryPanel();
        refreshRecordDetail(record);

        setStatus("Change added: " + fieldName + ": " + oldValue + " \u2192 " + newValue
                + "  (" + currentCaseStudy.modManager.count() + " pending in " + currentCaseStudy.name + ")");
        updateToolbarState();
    }

    // ------------------------------------------------------------------- //
    // Save (PART 6) -- every Case Study writes to its OWN file
    // ------------------------------------------------------------------- //
    private void onSave() {
        if (currentCaseStudy == null) {
            showAlert(AlertType.INFORMATION, "Nothing To Save", "The Base File is read-only. Select a Case Study.");
            return;
        }
        if (currentCaseStudy.modManager.count() == 0 && currentCaseStudy.outputFile != null) {
            showAlert(AlertType.INFORMATION, "Nothing To Save", "There are no pending changes to save.");
            return;
        }

        String resolvedText = manager.resolveText(currentCaseStudy);
        String outPath;
        try {
            outPath = storage.save(currentCaseStudy, resolvedText);
        } catch (IOException e) {
            showAlert(AlertType.ERROR, "Save Error", "Could not write " + currentCaseStudy.name + "'s file:\n" + e.getMessage());
            return;
        }

        currentCaseStudy.outputFile = outPath;
        currentCaseStudy.lastAppliedChanges = currentCaseStudy.modManager.getPending();
        currentCaseStudy.lastSavedAt = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        for (HistoryEntry entry : currentCaseStudy.history) {
            if (Constants.STATUS_PENDING.equals(entry.getStatus())) {
                entry.setStatus(Constants.STATUS_SAVED);
            }
        }
        refreshHistoryPanel();

        showAlert(AlertType.INFORMATION, "Saved",
                currentCaseStudy.name + " saved to its own file (Base File, parent, and siblings untouched):\n" + outPath);
        setStatus("Saved " + currentCaseStudy.name + " to " + outPath);
        updateToolbarState();
    }

    // ------------------------------------------------------------------- //
    // Run MiPower (PART 5) -- always compares against the right reference:
    // Base File's output for a root Case Study, the PARENT Case Study's
    // output for a child.
    // ------------------------------------------------------------------- //
    private record RunOutcome(RunResult baseResult, RunResult caseResult, boolean ranBase) {
    }

    private void onRunMiPower() {
        if (!manager.hasBaseFile()) {
            showAlert(AlertType.INFORMATION, "Nothing To Run", "Load a Base File first.");
            return;
        }

        if (currentCaseStudy == null) {
            runBaseOnly();
            return;
        }

        CaseStudy cs = currentCaseStudy;
        // Always run on the LATEST resolved data -- auto-save first so
        // "Run MiPower" never runs stale text.
        String resolvedText = manager.resolveText(cs);
        try {
            storage.save(cs, resolvedText);
        } catch (IOException e) {
            showAlert(AlertType.ERROR, "Save Error", "Could not stage " + cs.name + " for MiPower:\n" + e.getMessage());
            return;
        }
        cs.outputFile = storage.dat0Path(cs);

        CaseStudy parent = manager.getParent(cs);
        boolean needBaseRun = (parent == null) && (manager.baseOut0 == null);

        runBtn.setDisable(true);
        saveBtn.setDisable(true);
        progress.setVisible(true);
        progress.setManaged(true);
        setStatus("Running MiPower for " + cs.name + "...");

        long startTime = System.currentTimeMillis();
        Task<RunOutcome> task = new Task<>() {
            @Override
            protected RunOutcome call() {
                RunResult baseResult = null;
                if (needBaseRun) {
                    baseResult = MiPowerRunner.runMiPower(manager.getBaseFilePath());
                }
                RunResult caseResult = MiPowerRunner.runMiPower(storage.runnableDat0Path(cs));
                return new RunOutcome(baseResult, caseResult, needBaseRun);
            }
        };
        task.setOnSucceeded(e -> onCaseRunFinished(cs, task.getValue(), startTime));
        task.setOnFailed(e -> onCaseRunFinished(cs,
                new RunOutcome(null, new RunResult(false, "Failed to run MiPower: " + task.getException()), false),
                startTime));
        new Thread(task).start();
    }

    private void runBaseOnly() {
        runBtn.setDisable(true);
        progress.setVisible(true);
        progress.setManaged(true);
        setStatus("Running MiPower on the Base File...");

        Task<RunResult> task = new Task<>() {
            @Override
            protected RunResult call() {
                return MiPowerRunner.runMiPower(manager.getBaseFilePath());
            }
        };
        task.setOnSucceeded(e -> {
            progress.setVisible(false);
            progress.setManaged(false);
            updateToolbarState();
            RunResult result = task.getValue();
            if (result.success) {
                manager.baseOut0 = result.outputFiles.get("OUT File (.out0)");
                setStatus("MiPower run completed for the Base File.");
            } else {
                setStatus("MiPower run failed for the Base File: " + result.message);
            }
            showAlert(result.success ? AlertType.INFORMATION : AlertType.ERROR, "MiPower (Base File)",
                    (result.success ? "\u2714 Executed Successfully\n\n" : "\u2716 Execution Failed\n\n") + result.message);
        });
        task.setOnFailed(e -> {
            progress.setVisible(false);
            progress.setManaged(false);
            updateToolbarState();
            setStatus("MiPower run failed for the Base File.");
        });
        new Thread(task).start();
    }

    private void onCaseRunFinished(CaseStudy cs, RunOutcome outcome, long startTime) {
        progress.setVisible(false);
        progress.setManaged(false);
        updateToolbarState();

        if (outcome.ranBase() && outcome.baseResult() != null && outcome.baseResult().success) {
            manager.baseOut0 = outcome.baseResult().outputFiles.get("OUT File (.out0)");
        }

        RunResult result = outcome.caseResult();
        result.caseStudyName = cs.name;
        result.executionSeconds = (System.currentTimeMillis() - startTime) / 1000.0;
        cs.recordRun(result);

        StringBuilder sb = new StringBuilder();
        if (outcome.ranBase() && outcome.baseResult() != null) {
            sb.append(outcome.baseResult().success
                    ? "Base File run completed (first-time reference baseline).\n\n"
                    : "Base File run FAILED -- comparison will not be available.\n\n");
        }

        if (result.success) {
            String out0Path = result.outputFiles.get("OUT File (.out0)");
            cs.latestOut0Path = out0Path;
            cs.modifiedOut0 = out0Path;

            CaseStudy parent = manager.getParent(cs);
            cs.originalOut0 = (parent == null) ? manager.baseOut0 : parent.modifiedOut0;

            sb.append("\u2714 Executed Successfully for ").append(cs.name).append("\n\n").append(result.message).append("\n\n");
            for (Map.Entry<String, String> e : result.outputFiles.entrySet()) {
                sb.append(e.getKey()).append(":\n  ").append(e.getValue()).append("\n");
            }
            if (cs.originalOut0 == null) {
                sb.append("\nNote: the reference (").append(manager.referenceLabel(cs))
                        .append(") hasn't been run yet, so Analytics/Comparison isn't ready. Run it too, then come back.");
            }
            setStatus("MiPower run completed for " + cs.name + " (execution time: "
                    + String.format("%.1f", result.executionSeconds) + "s).");
        } else {
            sb.append("\u2716 Execution Failed for ").append(cs.name).append("\n\n").append(result.message);
            setStatus("MiPower run failed for " + cs.name + ": " + result.message);
        }
        showAlert(result.success ? AlertType.INFORMATION : AlertType.ERROR, "MiPower", sb.toString());
    }

    // ------------------------------------------------------------------- //
    private void openReportWindow() {
        if (currentCaseStudy == null) {
            showAlert(AlertType.INFORMATION, "No Case Study", "Select a Case Study first.");
            return;
        }
        new ReportWindow(currentCaseStudy, manager.getBaseFilePath()).show();
    }

    private void openRunHistoryWindow() {
        new RunHistoryWindow(manager.allCaseStudies()).show();
    }

    private void openAnalyticsDashboard() {
        try {
            new AnalyticsDashboard(manager.allCaseStudies(), manager).show();
        } catch (Exception e) {
            // Safety net: without this, any exception thrown while building the dashboard
            // (e.g. triggered by a freshly-added, not-yet-run Case Study) propagates
            // uncaught on the FX thread and the toolbar button appears to do nothing.
            e.printStackTrace();
            showAlert(AlertType.ERROR, "Analytics",
                    "Could not open the Analytics dashboard:\n" + e);
        }
    }

    // ------------------------------------------------------------------- //
    private void updateToolbarState() {
        boolean hasBase = manager.hasBaseFile();
        boolean hasCase = currentCaseStudy != null;
        saveBtn.setDisable(!hasCase);

        newCaseBtn.setDisable(!hasBase);
        renameBtn.setDisable(!hasCase);
        deleteBtn.setDisable(!hasCase);

        if (hasBase) {
            runBtn.setDisable(false);
            if (hasCase) {
                runBtnTooltip.setText("Run Load Flow Analysis for " + currentCaseStudy.name
                        + " (auto-saves first) and set its reference to " + manager.referenceLabel(currentCaseStudy) + ".");
            } else {
                runBtnTooltip.setText("Run Load Flow Analysis on the Base File to create the reference baseline.");
            }
        } else {
            runBtn.setDisable(true);
            runBtnTooltip.setText("Load a Base File first.");
        }
    }

    private void showAlert(AlertType type, String title, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.getDialogPane().setPrefWidth(480);
        alert.showAndWait();
    }
}
