package com.prdc.mipower.gui;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import com.prdc.mipower.models.CaseStudy;
import com.prdc.mipower.models.RunResult;

/** Lists every MiPower execution across every Case Study, newest first. */
public class RunHistoryWindow {

    private final List<CaseStudy> caseStudies;

    public RunHistoryWindow(List<CaseStudy> caseStudies) {
        this.caseStudies = caseStudies;
    }

    public void show() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Run History");

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");
        root.setPadding(new Insets(16));

        Label title = new Label("\uD83D\uDD52  Run History -- Every Case Study");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        BorderPane.setMargin(title, new Insets(0, 0, 12, 0));
        root.setTop(title);

        root.setCenter(buildTable());

        HBox footer = new HBox();
        footer.setAlignment(Pos.CENTER_RIGHT);
        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("button-secondary");
        closeBtn.setOnAction(e -> stage.close());
        footer.getChildren().add(closeBtn);
        BorderPane.setMargin(footer, new Insets(12, 0, 0, 0));
        root.setBottom(footer);

        Scene scene = new Scene(root, 820, 560);
        scene.getStylesheets().add(getClass().getResource("/css/theme.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    private TableView<RunResult> buildTable() {
        TableView<RunResult> table = new TableView<>();
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<RunResult, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().statusText()));
        statusCol.setPrefWidth(130);

        TableColumn<RunResult, String> caseCol = new TableColumn<>("Case Study");
        caseCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().caseStudyName != null ? d.getValue().caseStudyName : ""));
        caseCol.setPrefWidth(160);

        TableColumn<RunResult, String> timeCol = new TableColumn<>("Execution Time");
        timeCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                String.format("%.2fs", d.getValue().executionSeconds)));
        timeCol.setPrefWidth(110);

        TableColumn<RunResult, String> messageCol = new TableColumn<>("Message");
        messageCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().message != null ? d.getValue().message : ""));
        messageCol.setPrefWidth(260);

        TableColumn<RunResult, String> whenCol = new TableColumn<>("Timestamp");
        whenCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().timestamp != null ? d.getValue().timestamp : ""));
        whenCol.setPrefWidth(150);

        table.getColumns().addAll(List.of(statusCol, caseCol, timeCol, messageCol, whenCol));

        List<RunResult> allRuns = new ArrayList<>();
        for (CaseStudy cs : caseStudies) {
            allRuns.addAll(cs.runHistory);
        }
        allRuns.sort((a, b) -> {
            String ta = (a.timestamp != null) ? a.timestamp : "";
            String tb = (b.timestamp != null) ? b.timestamp : "";
            return tb.compareTo(ta); // newest first
        });

        table.getItems().setAll(allRuns);
        table.setPlaceholder(new Label("No MiPower runs yet."));
        return table;
    }
}
