package com.prdc.mipower.gui;

import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;

public class ThreeDViewPane extends BorderPane {

    public static class DataPoint3D {
        public final String name;
        public final double x;
        public final double y;
        public final double z;
        public final boolean isOverload;

        public DataPoint3D(String name, double x, double y, double z, boolean isOverload) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.isOverload = isOverload;
        }
    }

    private final Rotate rotateX = new Rotate(-20, Rotate.X_AXIS);
    private final Rotate rotateY = new Rotate(-35, Rotate.Y_AXIS);
    private final Translate cameraTranslate = new Translate(0, 0, -680);

    private double mouseOldX, mouseOldY;

    public ThreeDViewPane(String title, String xLabel, String yLabel, String zLabel, List<DataPoint3D> points) {
        getStyleClass().add("card");
        setPadding(new Insets(10));
        setStyle("-fx-background-color: #0F172A; -fx-background-radius: 8; -fx-border-color: #334155; -fx-border-radius: 8;");

        // ── Calculate Scales & Statistics ────────────────────────────────────
        double minX = 0, maxX = 100, minY = 0, maxY = 100, minZ = 0.8, maxZ = 1.2;
        long countOverload = 0;
        long countHighLoad = 0;
        long countNormal = 0;

        if (points != null && !points.isEmpty()) {
            minX = points.stream().mapToDouble(p -> p.x).min().orElse(0);
            maxX = points.stream().mapToDouble(p -> p.x).max().orElse(100);
            minY = points.stream().mapToDouble(p -> p.y).min().orElse(0);
            maxY = points.stream().mapToDouble(p -> p.y).max().orElse(100);
            minZ = points.stream().mapToDouble(p -> p.z).min().orElse(0.8);
            maxZ = points.stream().mapToDouble(p -> p.z).max().orElse(1.2);

            countOverload = points.stream().filter(p -> p.isOverload).count();
            countHighLoad = points.stream().filter(p -> !p.isOverload && p.x >= 80.0).count();
            countNormal = points.size() - countOverload - countHighLoad;
        }

        // ── Header: Title & Interactive Scale HUD ────────────────────────────
        VBox headerBox = new VBox(8);
        headerBox.setPadding(new Insets(4, 8, 8, 8));

        HBox titleRow = new HBox(12);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label titleLbl = new Label("🧊 " + title);
        titleLbl.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #38BDF8;");

        Label countBadge = new Label(points != null ? points.size() + " Total Elements" : "0 Points");
        countBadge.setStyle("-fx-background-color: #1E293B; -fx-text-fill: #94A3B8; -fx-font-size: 11px; -fx-font-weight: 600; -fx-padding: 3 8; -fx-background-radius: 4;");

        Label redBadge = new Label(String.format("🔴 Overloaded: %d", countOverload));
        redBadge.setStyle("-fx-background-color: rgba(239, 68, 68, 0.2); -fx-text-fill: #F87171; -fx-font-size: 10.5px; -fx-font-weight: bold; -fx-padding: 2 6; -fx-background-radius: 4; -fx-border-color: #EF4444; -fx-border-radius: 4;");

        Label amberBadge = new Label(String.format("🟠 High Load: %d", countHighLoad));
        amberBadge.setStyle("-fx-background-color: rgba(245, 158, 11, 0.2); -fx-text-fill: #FBBF24; -fx-font-size: 10.5px; -fx-font-weight: bold; -fx-padding: 2 6; -fx-background-radius: 4; -fx-border-color: #F59E0B; -fx-border-radius: 4;");

        Label greenBadge = new Label(String.format("🟢 Normal: %d", countNormal));
        greenBadge.setStyle("-fx-background-color: rgba(34, 197, 94, 0.2); -fx-text-fill: #4ADE80; -fx-font-size: 10.5px; -fx-font-weight: bold; -fx-padding: 2 6; -fx-background-radius: 4; -fx-border-color: #22C55E; -fx-border-radius: 4;");

        titleRow.getChildren().addAll(titleLbl, countBadge, redBadge, amberBadge, greenBadge);

        // ── 3-Axis Scale Breakdown Banner ────────────────────────────────────
        FlowPane scaleBanner = new FlowPane(10, 6);
        scaleBanner.setPadding(new Insets(6, 8, 6, 8));
        scaleBanner.setStyle("-fx-background-color: #1E293B; -fx-background-radius: 6; -fx-border-color: #334155; -fx-border-radius: 6;");

        Label scaleTitle = new Label("📏 3D AXIS SCALES:");
        scaleTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #CBD5E1;");

        Label xScaleLbl = new Label(String.format("🟦 X-Axis [%s]: %.2f ➔ %.2f", xLabel, minX, maxX));
        xScaleLbl.setStyle("-fx-font-size: 10.5px; -fx-font-weight: bold; -fx-text-fill: #38BDF8; -fx-background-color: rgba(56, 189, 248, 0.15); -fx-padding: 2 6; -fx-background-radius: 4;");

        Label yScaleLbl = new Label(String.format("🟨 Y-Axis [%s]: %.2f ➔ %.2f", yLabel, minY, maxY));
        yScaleLbl.setStyle("-fx-font-size: 10.5px; -fx-font-weight: bold; -fx-text-fill: #FDE047; -fx-background-color: rgba(250, 204, 21, 0.15); -fx-padding: 2 6; -fx-background-radius: 4;");

        Label zScaleLbl = new Label(String.format("🟪 Z-Axis [%s]: %.4f ➔ %.4f", zLabel, minZ, maxZ));
        zScaleLbl.setStyle("-fx-font-size: 10.5px; -fx-font-weight: bold; -fx-text-fill: #C084FC; -fx-background-color: rgba(192, 132, 252, 0.15); -fx-padding: 2 6; -fx-background-radius: 4;");

        Label controlHint = new Label("🖱️ Controls: Left-Drag = Orbit 360° | Scroll = Zoom In/Out | Hover = Element Metrics");
        controlHint.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #94A3B8;");

        scaleBanner.getChildren().addAll(scaleTitle, xScaleLbl, yScaleLbl, zScaleLbl, controlHint);

        headerBox.getChildren().addAll(titleRow, scaleBanner);
        setTop(headerBox);

        // ── 3D Scene Assembly ──────────────────────────────────────────────────
        Group root3D = new Group();

        // 1. Lighting Setup
        AmbientLight ambient = new AmbientLight(Color.rgb(190, 190, 190));
        PointLight pointLight1 = new PointLight(Color.WHITE);
        pointLight1.setTranslateX(200);
        pointLight1.setTranslateY(-400);
        pointLight1.setTranslateZ(-400);

        PointLight pointLight2 = new PointLight(Color.rgb(160, 180, 220));
        pointLight2.setTranslateX(-300);
        pointLight2.setTranslateY(200);
        pointLight2.setTranslateZ(300);

        root3D.getChildren().addAll(ambient, pointLight1, pointLight2);

        // 2. Axes, Scale Ticks and Reference Ground Grid
        buildCoordinateSystem(root3D, xLabel, yLabel, zLabel);

        // 3. Data Spheres
        if (points != null && !points.isEmpty()) {
            double spanX = Math.max(maxX - minX, 1.0);
            double spanY = Math.max(maxY - minY, 1.0);
            double spanZ = Math.max(maxZ - minZ, 0.05);

            double boxSize = 220.0;

            for (DataPoint3D pt : points) {
                double nx = ((pt.x - minX) / spanX - 0.5) * boxSize;
                double ny = -((pt.y - minY) / spanY - 0.5) * boxSize; // Invert Y so higher values point UP
                double nz = ((pt.z - minZ) / spanZ - 0.5) * boxSize;

                double radius = pt.isOverload ? 8.0 : (pt.x >= 80.0 ? 6.5 : 5.0);
                Sphere sphere = new Sphere(radius);

                PhongMaterial mat = new PhongMaterial();
                if (pt.isOverload) {
                    mat.setDiffuseColor(Color.rgb(239, 68, 68));
                    mat.setSpecularColor(Color.rgb(254, 202, 202));
                } else if (pt.x >= 80.0) {
                    mat.setDiffuseColor(Color.rgb(245, 158, 11));
                    mat.setSpecularColor(Color.rgb(253, 230, 138));
                } else {
                    mat.setDiffuseColor(Color.rgb(34, 197, 94));
                    mat.setSpecularColor(Color.rgb(187, 247, 208));
                }
                sphere.setMaterial(mat);
                sphere.setTranslateX(nx);
                sphere.setTranslateY(ny);
                sphere.setTranslateZ(nz);

                Tooltip tip = new Tooltip(String.format("📍 %s\n• %s (X): %.2f\n• %s (Y): %.2f\n• %s (Z): %.4f\n• Thermal/Voltage Status: %s",
                        pt.name, xLabel, pt.x, yLabel, pt.y, zLabel, pt.z,
                        pt.isOverload ? "OVERLOADED (≥100%)" : (pt.x >= 80 ? "HIGH LOAD (80-100%)" : "NORMAL (<80%)")));
                tip.setShowDelay(javafx.util.Duration.millis(30));
                Tooltip.install(sphere, tip);

                sphere.setOnMouseEntered(e -> {
                    sphere.setScaleX(1.4);
                    sphere.setScaleY(1.4);
                    sphere.setScaleZ(1.4);
                });
                sphere.setOnMouseExited(e -> {
                    sphere.setScaleX(1.0);
                    sphere.setScaleY(1.0);
                    sphere.setScaleZ(1.0);
                });

                root3D.getChildren().add(sphere);
            }
        }

        // 4. Orbit Camera Setup
        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setNearClip(1.0);
        camera.setFarClip(8000.0);

        Group cameraGroup = new Group();
        cameraGroup.getChildren().add(camera);
        camera.getTransforms().add(cameraTranslate);
        cameraGroup.getTransforms().addAll(rotateY, rotateX);

        Group world = new Group(root3D, cameraGroup);

        SubScene subScene = new SubScene(world, 780, 420, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.rgb(15, 23, 42));
        subScene.setCamera(camera);

        subScene.setOnMousePressed(me -> {
            mouseOldX = me.getSceneX();
            mouseOldY = me.getSceneY();
        });

        subScene.setOnMouseDragged(me -> {
            double dx = me.getSceneX() - mouseOldX;
            double dy = me.getSceneY() - mouseOldY;
            rotateY.setAngle(rotateY.getAngle() + dx * 0.4);
            rotateX.setAngle(Math.max(-85, Math.min(85, rotateX.getAngle() - dy * 0.4)));
            mouseOldX = me.getSceneX();
            mouseOldY = me.getSceneY();
        });

        subScene.setOnScroll(se -> {
            double delta = se.getDeltaY();
            cameraTranslate.setZ(Math.min(-200, Math.max(-2000, cameraTranslate.getZ() + delta * 1.5)));
        });

        StackPane centerWrapper = new StackPane(subScene);
        centerWrapper.setAlignment(Pos.CENTER);
        centerWrapper.setMinWidth(400);
        centerWrapper.setMinHeight(380);

        centerWrapper.widthProperty().addListener((obs, oldW, newW) -> {
            if (newW.doubleValue() > 100) subScene.setWidth(newW.doubleValue());
        });
        centerWrapper.heightProperty().addListener((obs, oldH, newH) -> {
            if (newH.doubleValue() > 100) subScene.setHeight(newH.doubleValue());
        });

        setCenter(centerWrapper);

        // ── Comprehensive 3D Engineering Explanation Panel ───────────────────
        VBox explanationBox = new VBox(6);
        explanationBox.setPadding(new Insets(10, 14, 10, 14));
        explanationBox.setStyle("-fx-background-color: #1E293B; -fx-background-radius: 6; -fx-border-color: #334155; -fx-border-radius: 6;");

        Label expTitle = new Label("⚡ 3D STATE SPACE ELECTRICAL ENGINEERING EXPLANATION");
        expTitle.setStyle("-fx-font-size: 11.5px; -fx-font-weight: bold; -fx-text-fill: #38BDF8;");

        Label expBody = new Label(
                "• Spatial Dimensionality: 2D charts only show 1 or 2 isolated variables. In AC power networks, Thermal Loading (X), " +
                "Real Power Flow (Y), and Voltage Magnitude (Z) are non-linearly coupled through governing AC power equations:\n" +
                "  P_ij = V_i V_j Y_ij cos(θ_ij - δ_ij)  and  Q_loss = I²X = ((P² + Q²)/V²)X.\n" +
                "• Critical Quadrant Diagnosis:\n" +
                "  - Top-Right Front (High Flow + High Loading + Depressed Voltage): Highest vulnerability zone. High current density causes quadratic reactive consumption (I²X), accelerating voltage collapse and line sag.\n" +
                "  - Bottom-Left (Low Flow + Low Loading + Elevated Voltage): Lightly loaded EHV lines experiencing Ferranti effect overvoltage from line capacitance (Qc = V²ωC).\n" +
                "  - Center Green Zone (Moderate Flow + Loading < 80% + ~1.0 p.u. Voltage): Secure operational envelope with robust N-1 contingency margins.\n" +
                "• Color Hierarchy: 🔴 Red = Overloaded (≥100%), 🟠 Amber = High Load (80-100%), 🟢 Green = Normal (<80%).");
        expBody.setWrapText(true);
        expBody.setStyle("-fx-font-size: 11px; -fx-text-fill: #CBD5E1; -fx-line-spacing: 2;");

        explanationBox.getChildren().addAll(expTitle, expBody);

        Button resetBtn = new Button("↺ Reset 3D Camera");
        resetBtn.setStyle("-fx-background-color: #334155; -fx-text-fill: #F8FAFC; -fx-font-size: 11px; -fx-font-weight: 600; " +
                "-fx-border-color: #475569; -fx-border-radius: 4; -fx-background-radius: 4; -fx-cursor: hand; -fx-padding: 6 12;");
        resetBtn.setOnAction(e -> {
            rotateX.setAngle(-20);
            rotateY.setAngle(-35);
            cameraTranslate.setZ(-680);
        });

        HBox bottomBar = new HBox(12, explanationBox, resetBtn);
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(explanationBox, Priority.ALWAYS);
        bottomBar.setPadding(new Insets(8, 4, 4, 4));

        setBottom(bottomBar);
    }

    private void buildCoordinateSystem(Group root, String xLabel, String yLabel, String zLabel) {
        double size = 260.0;
        double half = size / 2.0;

        // Ground reference grid box
        Box floorGrid = new Box(size, 2, size);
        PhongMaterial floorMat = new PhongMaterial(Color.rgb(30, 41, 59, 0.45));
        floorGrid.setMaterial(floorMat);
        floorGrid.setTranslateY(half);

        // Materials
        PhongMaterial axisMatX = new PhongMaterial(Color.rgb(56, 189, 248));
        PhongMaterial axisMatY = new PhongMaterial(Color.rgb(234, 179, 8));
        PhongMaterial axisMatZ = new PhongMaterial(Color.rgb(168, 85, 247));
        PhongMaterial tickMat = new PhongMaterial(Color.rgb(148, 163, 184));

        // X Axis (Cyan/Blue) -> Left to Right
        Cylinder axisX = new Cylinder(1.5, size);
        axisX.setMaterial(axisMatX);
        axisX.setRotationAxis(Rotate.Z_AXIS);
        axisX.setRotate(90);
        axisX.setTranslateY(half);
        axisX.setTranslateZ(-half);

        Sphere tipX = new Sphere(5.5);
        tipX.setMaterial(axisMatX);
        tipX.setTranslateX(half);
        tipX.setTranslateY(half);
        tipX.setTranslateZ(-half);

        // Y Axis (Yellow/Amber) -> Bottom to Top
        Cylinder axisY = new Cylinder(1.5, size);
        axisY.setMaterial(axisMatY);
        axisY.setTranslateX(-half);
        axisY.setTranslateZ(-half);

        Sphere tipY = new Sphere(5.5);
        tipY.setMaterial(axisMatY);
        tipY.setTranslateX(-half);
        tipY.setTranslateY(-half);
        tipY.setTranslateZ(-half);

        // Z Axis (Purple) -> Front to Back
        Cylinder axisZ = new Cylinder(1.5, size);
        axisZ.setMaterial(axisMatZ);
        axisZ.setRotationAxis(Rotate.X_AXIS);
        axisZ.setRotate(90);
        axisZ.setTranslateX(-half);
        axisZ.setTranslateY(half);

        Sphere tipZ = new Sphere(5.5);
        tipZ.setMaterial(axisMatZ);
        tipZ.setTranslateX(-half);
        tipZ.setTranslateY(half);
        tipZ.setTranslateZ(half);

        // Origin Marker (White Box)
        Box originBox = new Box(6, 6, 6);
        originBox.setMaterial(new PhongMaterial(Color.WHITE));
        originBox.setTranslateX(-half);
        originBox.setTranslateY(half);
        originBox.setTranslateZ(-half);

        root.getChildren().addAll(floorGrid, axisX, axisY, axisZ, tipX, tipY, tipZ, originBox);

        // Intermediate scale tick markers along axes (at 25%, 50%, 75%)
        for (int i = 1; i <= 3; i++) {
            double fraction = i / 4.0;
            double pos = -half + fraction * size;

            // X-axis tick
            Box tickX = new Box(1.5, 6, 6);
            tickX.setMaterial(tickMat);
            tickX.setTranslateX(pos);
            tickX.setTranslateY(half);
            tickX.setTranslateZ(-half);

            // Y-axis tick
            Box tickY = new Box(6, 1.5, 6);
            tickY.setMaterial(tickMat);
            tickY.setTranslateX(-half);
            tickY.setTranslateY(half - fraction * size);
            tickY.setTranslateZ(-half);

            // Z-axis tick
            Box tickZ = new Box(6, 6, 1.5);
            tickZ.setMaterial(tickMat);
            tickZ.setTranslateX(-half);
            tickZ.setTranslateY(half);
            tickZ.setTranslateZ(-half + fraction * size);

            root.getChildren().addAll(tickX, tickY, tickZ);
        }
    }
}
