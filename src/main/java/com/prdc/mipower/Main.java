package com.prdc.mipower;

import javafx.application.Application;
import javafx.stage.Stage;

import com.prdc.mipower.gui.Workspace;

/**
 * Entry point. Launches directly into {@link Workspace} -- there is no
 * Login page and no separate Dashboard/home screen to click through
 * first. The user's first action is Browse, right there in the
 * Workspace's own Base File card.
 *
 * <p>Run with: {@code mvn javafx:run}
 */
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        new Workspace(null).show(primaryStage);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
