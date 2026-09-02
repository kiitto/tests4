package com.prdc.mipower.gui;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import com.prdc.mipower.models.CaseStudy;
import com.prdc.mipower.models.ChangeRecord;
import com.prdc.mipower.models.HistoryEntry;
import com.prdc.mipower.services.ReportGenerator;

/**
 * Shows one Case Study's full Change History (every status, not just the
 * last-saved snapshot) and exports it as TXT, CSV, or PDF via
 * {@link ReportGenerator}.
 */
public class ReportWindow {

    private final CaseStudy caseStudy;
    private final String inputFile;
    private final ReportGenerator reportGenerator = new ReportGenerator();

    public ReportWindow(CaseStudy caseStudy, String inputFile) {
        this.caseStudy = caseStudy;
        this.inputFile = inputFile;
    }

    public void show() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Case Study Report -- " + caseStudy.name);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");
        root.setPadding(new Insets(16));

        VBox header = new VBox(6);
        Label title = new Label("\uD83D\uDCC4  " + caseStudy.name);
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        var counts = caseStudy.statusCounts();
        Label summary = new Label(String.format(
                "%d total  \u00b7  %d Pending  \u00b7  %d Saved  \u00b7  %d Undone  \u00b7  %d Deleted",
                counts.total(), counts.pending(), counts.saved(), counts.undone(), counts.deleted()));
        summary.getStyleClass().add("muted-label");

        Label lastSaved = new Label("Last Saved: "
                + (caseStudy.lastSavedAt != null ? caseStudy.lastSavedAt : "Not saved yet"));
        lastSaved.getStyleClass().add("muted-label");

        header.getChildren().addAll(title, summary, lastSaved);
        root.setTop(header);

        TableView<HistoryEntry> table = buildTable();
        BorderPane.setMargin(table, new Insets(12, 0, 12, 0));
        root.setCenter(table);

        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_RIGHT);
        Button exportTxt = new Button("Export TXT");
        exportTxt.getStyleClass().add("button-secondary");
        exportTxt.setOnAction(e -> export("txt"));

        Button exportCsv = new Button("Export CSV");
        exportCsv.getStyleClass().add("button-secondary");
        exportCsv.setOnAction(e -> export("csv"));

        Button exportPdf = new Button("Export PDF");
        exportPdf.getStyleClass().add("button-primary");
        exportPdf.setOnAction(e -> export("pdf"));

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("button-secondary");
        closeBtn.setOnAction(e -> stage.close());

        footer.getChildren().addAll(exportTxt, exportCsv, exportPdf, closeBtn);
        root.setBottom(footer);

        Scene scene = new Scene(root, 900, 620);
        scene.getStylesheets().add(getClass().getResource("/css/theme.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    private TableView<HistoryEntry> buildTable() {
        TableView<HistoryEntry> table = new TableView<>();
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<HistoryEntry, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getStatus()));
        statusCol.setPrefWidth(80);

        TableColumn<HistoryEntry, String> sectionCol = new TableColumn<>("Section");
        sectionCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getSection()));
        sectionCol.setPrefWidth(160);

        TableColumn<HistoryEntry, String> fieldCol = new TableColumn<>("Field");
        fieldCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getField() != null ? d.getValue().getField() : "(value)"));
        fieldCol.setPrefWidth(140);

        TableColumn<HistoryEntry, String> oldCol = new TableColumn<>("Old Value");
        oldCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getOldValue()));
        oldCol.setPrefWidth(100);

        TableColumn<HistoryEntry, String> newCol = new TableColumn<>("New Value");
        newCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getNewValue()));
        newCol.setPrefWidth(100);

        TableColumn<HistoryEntry, String> timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getTimestamp()));
        timeCol.setPrefWidth(150);

        table.getColumns().addAll(List.of(statusCol, sectionCol, fieldCol, oldCol, newCol, timeCol));
        table.getItems().setAll(caseStudy.history);
        table.setPlaceholder(new Label("No changes yet."));
        return table;
    }

    private void export(String format) {
        List<ChangeRecord> changes = (caseStudy.lastAppliedChanges != null)
                ? caseStudy.lastAppliedChanges
                : caseStudy.modManager.getPending();
        if (changes.isEmpty()) {
            showAlert(AlertType.INFORMATION, "Nothing To Export", "This Case Study has no changes to export yet.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export " + format.toUpperCase() + " Report");
        String defaultName = "Changes_Report_" + caseStudy.slug() + "." + format;
        chooser.setInitialFileName(defaultName);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                format.toUpperCase() + " Files", "*." + format));
        File file = chooser.showSaveDialog(null);
        if (file == null) {
            return;
        }

        try {
            String path = switch (format) {
                case "csv" -> reportGenerator.generateCsv(changes, file.getAbsolutePath());
                case "pdf" -> reportGenerator.generatePdf(changes, file.getAbsolutePath(), inputFile,
                        caseStudy.outputFile);
                default -> reportGenerator.generateTxt(changes, file.getAbsolutePath(), inputFile,
                        caseStudy.outputFile);
            };
            caseStudy.reportPath = path;
            showAlert(AlertType.INFORMATION, "Exported", "Report exported to:\n" + path);
        } catch (IOException e) {
            showAlert(AlertType.ERROR, "Export Error", "Could not write the report:\n" + e.getMessage());
        }
    }

    private void showAlert(AlertType type, String title, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
