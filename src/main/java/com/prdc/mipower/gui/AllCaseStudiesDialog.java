package com.prdc.mipower.gui;

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
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import com.prdc.mipower.gui.CaseStudyTreeModel.Node;
import com.prdc.mipower.models.CaseStudy;
import com.prdc.mipower.services.CaseStudyManager;

/**
 * "All Case Studies" -- replaces the old permanent Case Study Hierarchy
 * panel that used to sit in the left sidebar all the time. That panel took
 * up space whether or not you needed it; this is the same set of Case
 * Studies, but opened only when you actually want to browse or manage
 * them, via a toolbar/sidebar button. Every saved Case Study always
 * appears here, exactly as it did in the old panel -- nothing about what's
 * SHOWN changed, only when/where it's shown and how it's laid out: a
 * single linear list (Base File first, then every Case Study in creation
 * order) rather than a nested tree. Each Case Study's row still names its
 * parent, so the relationship is visible without indentation.
 */
public final class AllCaseStudiesDialog {

    private AllCaseStudiesDialog() {
    }

    public interface Callbacks {
        /** The user clicked "Open" -- {@code cs == null} means the Base File. */
        void onOpen(CaseStudy cs);

        /** The list changed (create/rename/duplicate/delete) -- caller should refresh anything showing it. */
        void onTreeChanged();
    }

    public static void show(Window owner, CaseStudyManager manager, CaseStudy currentSelection, Callbacks callbacks) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle("All Case Studies");

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");
        root.setPadding(new Insets(16));

        VBox header = new VBox(4);
        Label title = new Label("\uD83D\uDDC2 All Case Studies");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Label hint = new Label("Every saved Case Study, listed in one place. Select one and click Open to edit it, "
                + "or use the actions below.");
        hint.getStyleClass().add("muted-label");
        hint.setWrapText(true);
        header.getChildren().addAll(title, hint);
        root.setTop(header);
        BorderPane.setMargin(header, new Insets(0, 0, 10, 0));

        ListView<Node> list = new ListView<>();
        list.getItems().setAll(CaseStudyTreeModel.buildList(manager));
        VBox.setVgrow(list, Priority.ALWAYS);
        root.setCenter(list);

        Node defaultSelection = CaseStudyTreeModel.find(list.getItems(), currentSelection);
        if (defaultSelection != null) {
            list.getSelectionModel().select(defaultSelection);
        }

        HBox actions = new HBox(8);
        actions.setPadding(new Insets(12, 0, 0, 0));
        actions.setAlignment(Pos.CENTER_LEFT);

        Button openBtn = new Button("Open");
        openBtn.getStyleClass().add("button-primary");
        Button newCaseBtn = new Button("New Case");
        Button renameBtn = new Button("Rename");
        Button duplicateBtn = new Button("Duplicate");
        Button deleteBtn = new Button("Delete");
        Button closeBtn = new Button("Close");
        for (Button b : new Button[]{newCaseBtn, renameBtn, duplicateBtn, deleteBtn, closeBtn}) {
            b.getStyleClass().add("button-secondary");
        }

        Runnable rebuildList = () -> {
            CaseStudy toReselect = selectedCaseStudy(list);
            List<Node> nodes = CaseStudyTreeModel.buildList(manager);
            list.getItems().setAll(nodes);
            Node item = CaseStudyTreeModel.find(nodes, toReselect);
            if (item != null) {
                list.getSelectionModel().select(item);
            }
            callbacks.onTreeChanged();
        };

        openBtn.setOnAction(e -> {
            Node selected = list.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            callbacks.onOpen(selected.isBase ? null : selected.caseStudy);
            stage.close();
        });

        newCaseBtn.setOnAction(e -> {
            CaseStudy preselect = selectedCaseStudy(list);
            NewCaseDialog.showAndCreate(stage, manager, preselect).ifPresent(cs -> rebuildList.run());
        });

        renameBtn.setOnAction(e -> {
            CaseStudy cs = selectedCaseStudy(list);
            if (cs == null) {
                showAlert(stage, "Select a Case Study", "The Base File can't be renamed.");
                return;
            }
            TextInputDialog dialog = new TextInputDialog(cs.name);
            dialog.setTitle("Rename Case Study");
            dialog.setHeaderText(null);
            dialog.setContentText("New name (the underlying reference/hierarchy is unaffected):");
            dialog.initOwner(stage);
            dialog.showAndWait().ifPresent(newName -> {
                if (manager.rename(cs, newName)) {
                    rebuildList.run();
                } else {
                    showAlert(stage, "Rename Failed", "That name is empty or already in use.");
                }
            });
        });

        duplicateBtn.setOnAction(e -> {
            CaseStudy cs = selectedCaseStudy(list);
            if (cs == null) {
                showAlert(stage, "Select a Case Study", "The Base File can't be duplicated -- create a new Case Study from it instead.");
                return;
            }
            try {
                manager.duplicate(cs, cs.name + " (Copy)");
                rebuildList.run();
            } catch (IOException ex) {
                showAlert(stage, "Error", "Could not duplicate the Case Study:\n" + ex.getMessage());
            }
        });

        deleteBtn.setOnAction(e -> {
            CaseStudy cs = selectedCaseStudy(list);
            if (cs == null) {
                showAlert(stage, "Select a Case Study", "The Base File can't be deleted.");
                return;
            }
            int descendants = countDescendants(manager, cs);
            String warning = "Delete " + cs.name + (descendants > 0
                    ? " and its " + descendants + " child Case Study(ies)" : "") + "? This cannot be undone.";
            Alert confirm = new Alert(AlertType.CONFIRMATION, warning, ButtonType.YES, ButtonType.NO);
            confirm.initOwner(stage);
            confirm.setTitle("Delete Case Study");
            confirm.setHeaderText(null);
            confirm.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    manager.delete(cs);
                    rebuildList.run();
                }
            });
        });

        closeBtn.setOnAction(e -> stage.close());

        actions.getChildren().addAll(openBtn, newCaseBtn, renameBtn, duplicateBtn, deleteBtn, closeBtn);
        root.setBottom(actions);

        Scene scene = new Scene(root, 620, 560);
        scene.getStylesheets().add(AllCaseStudiesDialog.class.getResource("/css/theme.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    private static CaseStudy selectedCaseStudy(ListView<Node> list) {
        Node selected = list.getSelectionModel().getSelectedItem();
        if (selected == null || selected.isBase) {
            return null;
        }
        return selected.caseStudy;
    }

    private static int countDescendants(CaseStudyManager manager, CaseStudy cs) {
        int count = 0;
        for (CaseStudy child : manager.childrenOf(cs)) {
            count += 1 + countDescendants(manager, child);
        }
        return count;
    }

    private static void showAlert(Window owner, String title, String message) {
        Alert alert = new Alert(AlertType.INFORMATION, message, ButtonType.OK);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
