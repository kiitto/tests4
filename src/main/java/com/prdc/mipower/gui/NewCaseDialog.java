package com.prdc.mipower.gui;

import java.io.IOException;
import java.util.Optional;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import com.prdc.mipower.gui.CaseStudyTreeModel.Node;
import com.prdc.mipower.models.CaseStudy;
import com.prdc.mipower.services.CaseStudyManager;

/**
 * "Create New Case Study" -- the CORRECTED dialog. The previous version of
 * this dialog only offered two radio options ("Base File" / "Current Case
 * Study"), which meant a user could never create a new child underneath
 * anything except whatever happened to be selected in the sidebar right
 * now. That was wrong: this dialog always shows the WHOLE Case Study tree,
 * from every screen, and the user can pick literally any node -- Base
 * File, or any Case Study at any depth -- as the new Case Study's
 * reference, regardless of what's currently selected elsewhere in the
 * application.
 *
 * <p>Examples the tree selection supports directly:
 * <ul>
 *   <li>Select Case Study 1 -> creates Case Study 1_3 (its next child)</li>
 *   <li>Select Case Study 2 -> creates Case Study 2_1 (its first child)</li>
 *   <li>Select Case Study 1_1 -> creates Case Study 1_1_1</li>
 *   <li>Select the Base File node -> creates a new root Case Study</li>
 * </ul>
 *
 * <p>This class only collects the user's choice and hands off to
 * {@link CaseStudyManager#createChildCaseStudy(CaseStudy, String)} (which
 * already treats a {@code null} reference as "create at the root, from the
 * Base File" -- see its Javadoc) -- it does not itself decide anything
 * about hierarchy or inheritance. The reference is picked from a single
 * linear list (Base File first, then every Case Study), not a nested tree.
 */
public final class NewCaseDialog {

    private NewCaseDialog() {
    }

    /**
     * Shows the dialog and, if the user confirms, creates the new Case
     * Study. Returns the created {@link CaseStudy}, or empty if the user
     * cancelled or creation failed (an error alert is shown in that case).
     *
     * @param owner        the window to center the dialog over
     * @param manager      the Case Study tree to show and create into
     * @param preselect    optionally highlighted by default (e.g. whatever
     *                     is currently selected in the sidebar) -- purely a
     *                     convenience default, never a restriction: any
     *                     other node in the tree remains fully selectable
     */
    public static Optional<CaseStudy> showAndCreate(Window owner, CaseStudyManager manager, CaseStudy preselect) {
        Dialog<CaseStudy> dialog = new Dialog<>();
        dialog.setTitle("Create New Case Study");
        dialog.initOwner(owner);

        ButtonType createButtonType = new ButtonType("Create Case", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefSize(560, 560);

        VBox content = new VBox(10);
        content.setPadding(new Insets(14));

        Label chooseLabel = new Label("Choose Reference Case:");
        chooseLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        Label hint = new Label("The new Case Study starts with an exact copy of whatever you pick here -- "
                + "the Base File, or ANY existing Case Study at any depth -- regardless of what's "
                + "currently open elsewhere in the app.");
        hint.getStyleClass().add("muted-label");
        hint.setWrapText(true);

        ListView<Node> list = new ListView<>();
        list.getItems().setAll(CaseStudyTreeModel.buildList(manager));
        list.setPrefHeight(300);
        VBox.setVgrow(list, Priority.ALWAYS);

        Label selectedLabel = new Label("Selected Reference: (none)");
        selectedLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2563EB;");

        Label nameLabel = new Label("New Case Name:");
        TextField nameField = new TextField();
        nameField.setPromptText("Leave blank for an automatic name");

        Button createBtn = (Button) dialog.getDialogPane().lookupButton(createButtonType);
        createBtn.setDisable(true);

        list.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem == null) {
                selectedLabel.setText("Selected Reference: (none)");
                createBtn.setDisable(true);
                return;
            }
            selectedLabel.setText("Selected Reference: " + (newItem.isBase ? "Base File" : newItem.caseStudy.name));
            createBtn.setDisable(false);
        });

        // Convenience default only -- every other entry stays fully selectable.
        Node defaultSelection = (preselect != null)
                ? CaseStudyTreeModel.find(list.getItems(), preselect)
                : (list.getItems().isEmpty() ? null : list.getItems().get(0));
        if (defaultSelection != null) {
            list.getSelectionModel().select(defaultSelection);
        }

        content.getChildren().addAll(chooseLabel, hint, list, selectedLabel, nameLabel, nameField);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(buttonType -> {
            if (buttonType != createButtonType) {
                return null;
            }
            Node selected = list.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return null;
            }
            CaseStudy reference = selected.isBase ? null : selected.caseStudy;
            try {
                return manager.createChildCaseStudy(reference, nameField.getText());
            } catch (IOException e) {
                showError("Could not create the Case Study:\n" + e.getMessage());
                return null;
            }
        });

        return dialog.showAndWait();
    }

    private static void showError(String message) {
        Alert alert = new Alert(AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
