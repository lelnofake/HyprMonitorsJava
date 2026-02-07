package controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import monitors.monitors;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class Controller implements Initializable {

    @FXML
    private Button ConfirmButton;
    @FXML
    private Pane pane;

    private final List<monitors> monitorList = new ArrayList<>();
    private final Map<monitors, Label> labels = new HashMap<>();
    private final Map<monitors, Point2D> lastGoodPos = new HashMap<>();
    private final Map<monitors, MonitorDimensions> monitorDimensions = new HashMap<>();

    private static final double POSITION_SCALE = 10.0;
    private static final double STROKE_OVERHANG = 1.0;

    String home = System.getProperty("user.home");
    Path hyprland = Paths.get(home, ".config/hypr/hyprland.conf");
    Path monitorsConf = Paths.get(home, ".config/hypr/monitorsJ.conf");
    String source = "source=~/.config/hypr/monitorsJ.conf";

    @FXML
    private void ConfirmChanges() throws IOException {
        Import();
        Create();
    }

    private void Import() throws IOException {
        if (!Files.exists(hyprland)) return;
        String content = Files.readString(hyprland);
        if (!content.contains(source)) {
            Files.writeString(hyprland, "\n" + source + "\n", StandardOpenOption.APPEND);
        }
    }

    private void Create() throws IOException {
        Path parent = monitorsConf.getParent();
        if (parent != null) Files.createDirectories(parent);

        enforceTouchingLayout();

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;

        for (monitors m : monitorList) {
            Bounds b = getLogicalBounds(m);
            if (b.getMinX() < minX) minX = b.getMinX();
            if (b.getMinY() < minY) minY = b.getMinY();
        }

        List<String> lines = new ArrayList<>();
        for (monitors monitor : monitorList) {
            MonitorDimensions dims = monitorDimensions.get(monitor);

            int rawWidth = dims != null ? dims.width() : (int) Math.round(monitor.getGroup().getLayoutBounds().getWidth() * POSITION_SCALE);
            int rawHeight = dims != null ? dims.height() : (int) Math.round(monitor.getGroup().getLayoutBounds().getHeight() * POSITION_SCALE);

            Bounds bounds = getLogicalBounds(monitor);

            int configX = (int) Math.round((bounds.getMinX() - minX) * POSITION_SCALE);
            int configY = (int) Math.round((bounds.getMinY() - minY) * POSITION_SCALE);

            String line = String.format(
                    Locale.ROOT,
                    "monitor = %s,%dx%d,%dx%d,1,transform,%d",
                    monitor.getName(),
                    rawWidth,
                    rawHeight,
                    configX,
                    configY,
                    monitor.getTransform()
            );
            lines.add(line);
        }

        Files.write(monitorsConf, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private Bounds getLogicalBounds(monitors monitor) {
        Bounds visual = monitor.getGroup().getBoundsInParent();
        return new BoundingBox(
                visual.getMinX() + STROKE_OVERHANG,
                visual.getMinY() + STROKE_OVERHANG,
                visual.getWidth() - (2 * STROKE_OVERHANG),
                visual.getHeight() - (2 * STROKE_OVERHANG)
        );
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        try {
            getMonitors();
        } catch (Exception e) {
            e.printStackTrace();
        }

        for (monitors monitor : monitorList) {
            pane.getChildren().add(monitor.getGroup());
            String labelText = String.valueOf(monitor.getGroup().getUserData());
            Label nameLabel = new Label(labelText);
            nameLabel.setMouseTransparent(true);
            labels.put(monitor, nameLabel);
            pane.getChildren().add(nameLabel);

            lastGoodPos.put(monitor, new Point2D(monitor.getGroup().getTranslateX(), monitor.getGroup().getTranslateY()));
            updateLabelPosition(monitor);
            draggable(monitor);
        }
    }

    private void getMonitors() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("hyprctl", "-j", "monitors").start();
        StringBuilder json = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) json.append(line).append('\n');
        }
        if (process.waitFor() != 0) return;

        String jsonStr = json.toString().trim();
        if (!(jsonStr.startsWith("[") && jsonStr.endsWith("]"))) return;
        jsonStr = jsonStr.substring(1, jsonStr.length() - 1);
        String[] monitorEntries = jsonStr.split("\\},\\s*\\{");

        for (String entry : monitorEntries) {
            entry = entry.trim();
            if (!entry.startsWith("{")) entry = "{" + entry;
            if (!entry.endsWith("}")) entry = entry + "}";

            try {
                String name = getDato(entry, "\"name\":", ",", true);
                String widthStr = getDato(entry, "\"width\":", ",", false);
                String heightStr = getDato(entry, "\"height\":", ",", false);
                String xStr = getDato(entry, "\"x\":", ",", false);
                String yStr = getDato(entry, "\"y\":", ",", false);
                String transformStr = getDato(entry, "\"transform\":", ",", false);

                if (name != null && widthStr != null && heightStr != null && xStr != null && yStr != null) {
                    int width = Integer.parseInt(widthStr.trim());
                    int height = Integer.parseInt(heightStr.trim());
                    int x = Integer.parseInt(xStr.trim());
                    int y = Integer.parseInt(yStr.trim());
                    int transform = transformStr != null ? Integer.parseInt(transformStr.trim()) : 0;

                    monitors monitor = new monitors(name, Math.max(10, width / 10), Math.max(10, height / 10), x / 10, y / 10, transform);
                    monitor.getGroup().setUserData(name);
                    monitorList.add(monitor);
                    monitorDimensions.put(monitor, new MonitorDimensions(width, height));
                }
            } catch (Exception ex) { ex.printStackTrace(); }
        }
    }

    private String getDato(String json, String key, String endDelim, boolean quotedString) {
        int keyIdx = json.indexOf(key);
        if (keyIdx == -1) return null;
        int start = keyIdx + key.length();
        if (quotedString) {
            while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
            if (start < json.length() && json.charAt(start) == '"') start++;
            int end = json.indexOf('"', start);
            return end == -1 ? null : json.substring(start, end);
        } else {
            int end = json.indexOf(endDelim, start);
            if (end == -1) end = json.indexOf('}', start);
            if (end == -1) end = json.length();
            return json.substring(start, end).replace("\"", "").trim();
        }
    }

    private void draggable(monitors monitor) {
        final double[] offset = new double[2];
        monitor.getGroup().setOnMousePressed(event -> {
            offset[0] = event.getSceneX() - monitor.getGroup().getTranslateX();
            offset[1] = event.getSceneY() - monitor.getGroup().getTranslateY();
            lastGoodPos.put(monitor, new Point2D(monitor.getGroup().getTranslateX(), monitor.getGroup().getTranslateY()));
            monitor.getGroup().toFront();
            if(labels.get(monitor) != null) labels.get(monitor).toFront();
            event.consume();
        });

        monitor.getGroup().setOnMouseDragged(event -> {
            double newX = event.getSceneX() - offset[0];
            double newY = event.getSceneY() - offset[1];
            monitor.setPosition((int) newX, (int) newY);
            if (wouldOverlap(monitor)) {
                monitor.setPosition((int)newX, (int)lastGoodPos.get(monitor).getY());
                if(wouldOverlap(monitor)) {
                    monitor.setPosition((int)lastGoodPos.get(monitor).getX(), (int)newY);
                    if(wouldOverlap(monitor)) {
                        Point2D last = lastGoodPos.get(monitor);
                        monitor.setPosition((int) last.getX(), (int) last.getY());
                    }
                }
            }
            if (!wouldOverlap(monitor)) {
                lastGoodPos.put(monitor, new Point2D(monitor.getGroup().getTranslateX(), monitor.getGroup().getTranslateY()));
            }
            updateLabelPosition(monitor);
            event.consume();
        });

        monitor.getGroup().setOnMouseReleased(event -> {
            snapToClosestNeighbor(monitor);
            updateLabelPosition(monitor);
            event.consume();
        });

        monitor.getGroup().setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                int nextTransform = (monitor.getTransform() + 1) % 4;
                monitor.setTransform(nextTransform);
                if (wouldOverlap(monitor)) monitor.setTransform((nextTransform + 3) % 4);
                updateLabelPosition(monitor);
                event.consume();
            }
        });
    }

    private boolean snapToClosestNeighbor(monitors moving) {
        return snapToClosestNeighbor(moving, monitorList, monitorList);
    }

    private boolean snapToClosestNeighbor(monitors moving, Collection<monitors> neighbors, Collection<monitors> overlapTargets) {
        Bounds mb = getLogicalBounds(moving);
        double currentTx = moving.getGroup().getTranslateX();
        double currentTy = moving.getGroup().getTranslateY();
        double bestTx = currentTx, bestTy = currentTy;
        double minDistance = Double.MAX_VALUE;
        boolean foundSnap = false;

        for (monitors neighbor : neighbors) {
            if (neighbor == moving) continue;
            Bounds nb = getLogicalBounds(neighbor);
            double deltaRightToLeft = nb.getMinX() - mb.getMaxX();
            double deltaLeftToRight = nb.getMaxX() - mb.getMinX();
            double deltaBottomToTop = nb.getMinY() - mb.getMaxY();
            double deltaTopToBottom = nb.getMaxY() - mb.getMinY();
            double alignY = alignOffset(mb.getMinY(), mb.getHeight(), nb.getMinY(), nb.getHeight());
            double alignX = alignOffset(mb.getMinX(), mb.getWidth(), nb.getMinX(), nb.getWidth());

            double[][] candidates = {{currentTx+deltaRightToLeft, currentTy+alignY}, {currentTx+deltaLeftToRight, currentTy+alignY}, {currentTx+alignX, currentTy+deltaBottomToTop}, {currentTx+alignX, currentTy+deltaTopToBottom}};

            for (double[] pos : candidates) {
                moving.setPosition((int)Math.round(pos[0]), (int)Math.round(pos[1]));
                if (!wouldOverlapAt(moving, overlapTargets)) {
                    double dist = Math.hypot(pos[0] - currentTx, pos[1] - currentTy);
                    if (dist < minDistance) {
                        minDistance = dist; bestTx = pos[0]; bestTy = pos[1]; foundSnap = true;
                    }
                }
            }
        }
        if (foundSnap) {
            moving.setPosition((int) Math.round(bestTx), (int) Math.round(bestTy));
            lastGoodPos.put(moving, new Point2D(bestTx, bestTy));
            updateLabelPosition(moving);
            return true;
        }
        return false;
    }

    private double alignOffset(double currentStart, double currentLen, double targetStart, double targetLen) {
        if (currentStart > targetStart + targetLen) return targetStart - currentStart;
        if (currentStart + currentLen < targetStart) return (targetStart + targetLen) - (currentStart + currentLen);
        return 0;
    }

    private boolean wouldOverlap(monitors moving) { return wouldOverlapAt(moving, monitorList); }

    private boolean wouldOverlapAt(monitors moving, Collection<monitors> obstacles) {
        Bounds mb = getLogicalBounds(moving);
        double shrink = 0.1;
        for (monitors other : obstacles) {
            if (other == moving) continue;
            Bounds ob = getLogicalBounds(other);
            if (mb.intersects(ob.getMinX() + shrink, ob.getMinY() + shrink, ob.getWidth() - 2*shrink, ob.getHeight() - 2*shrink)) return true;
        }
        return false;
    }

    private void enforceTouchingLayout() {
        if (monitorList.size() <= 1) return;
        List<monitors> remaining = new ArrayList<>(monitorList);
        Set<monitors> anchored = new LinkedHashSet<>();
        anchored.add(remaining.remove(0));
        while (!remaining.isEmpty()) {
            monitors next = remaining.remove(0);
            if (!snapToClosestNeighbor(next, anchored, anchored)) {
                Bounds ab = getLogicalBounds(anchored.iterator().next());
                Bounds nb = getLogicalBounds(next);
                next.setPosition((int)Math.round(next.getGroup().getTranslateX() + (ab.getMaxX() - nb.getMinX())), (int)Math.round(ab.getMinY()));
            }
            anchored.add(next);
            updateLabelPosition(next);
        }
    }

    private void updateLabelPosition(monitors monitor) {
        Label label = labels.get(monitor);
        if (label == null) return;
        Bounds b = monitor.getGroup().getBoundsInParent();
        if (label.getScene() != null) { label.applyCss(); label.layout(); }
        double lw = label.getWidth() > 0 ? label.getWidth() : label.prefWidth(-1);
        double lh = label.getHeight() > 0 ? label.getHeight() : label.prefHeight(-1);
        label.relocate((b.getMinX() + b.getWidth()/2.0) - lw/2.0, (b.getMinY() + b.getHeight()/2.0) - lh/2.0);
    }

    private record MonitorDimensions(int width, int height) {}
}