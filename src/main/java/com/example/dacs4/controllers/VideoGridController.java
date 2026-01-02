package com.example.dacs4.controllers;

import com.example.dacs4.models.Participant;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VideoGridController {

    @FXML
    private GridPane videoGrid;

    private final List<Participant> participants = new ArrayList<>();
    private final Map<String, ImageView> videoViews = new HashMap<>();
    private final Map<String, Image> lastFrames = new HashMap<>();
    private String currentUserId;

    private ImageView screenShareView; // For screen sharing display (created programmatically)
    private String currentScreenSharerId; // Who is currently sharing screen

    public void initialize() {
        // Create screen share view programmatically
        screenShareView = new ImageView();
        screenShareView.setPreserveRatio(true);
        screenShareView.setFitWidth(900);
        screenShareView.setFitHeight(520);
        screenShareView.setVisible(false);
        screenShareView.setManaged(false);
        screenShareView.getStyleClass().add("screen-share-view");
        // loadMockParticipants(); // test: tự render
    }

    // private void loadMockParticipants() {
    // List<Participant> list = new ArrayList<>();
    // list.add(new Participant("1", "A", true, true, false));
    // list.add(new Participant("2", "B", true, false, false));
    // list.add(new Participant("3", "C", false, false, false));
    //
    // // ❌ KHÔNG dùng videoGridController.set...
    // // ✔ Gọi chính controller hiện tại
    // setCurrentUserId("1");
    // setParticipants(list);
    // }

    // SET ====

    public void setParticipants(List<Participant> list) {
        participants.clear();
        participants.addAll(list);
        renderGrid();
    }

    public void setCurrentUserId(String id) {
        this.currentUserId = id;
    }

    // RENDER ====

    private void renderGrid() {
        // Remove screen share view temporarily to rebuild grid
        if (screenShareView != null && videoGrid.getChildren().contains(screenShareView)) {
            videoGrid.getChildren().remove(screenShareView);
        }

        videoGrid.getChildren().clear();
        videoGrid.getColumnConstraints().clear();
        videoGrid.getRowConstraints().clear();
        videoViews.clear();

        // Nếu có screen share, layout khác: screen share to nhất, cam nhỏ ở góc
        if (currentScreenSharerId != null && screenShareView != null && screenShareView.isVisible()) {
            renderGridWithScreenShare();
        } else {
            renderGridNormal();
        }
    }

    private void renderGridNormal() {
        int count = participants.size();
        int cols = calcColumns(count);

        for (int i = 0; i < cols; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / cols);
            videoGrid.getColumnConstraints().add(cc);
        }

        int row = 0, col = 0;
        for (Participant p : participants) {
            StackPane tile = createTile(p);
            videoGrid.add(tile, col, row);
            col++;
            if (col >= cols) {
                col = 0;
                row++;
            }
        }
    }

    private void renderGridWithScreenShare() {
        // Layout: Screen share to nhất ở giữa, cam nhỏ ở góc phải dưới
        // Tạo 2 columns: screen share chiếm hầu hết, cam nhỏ ở cột phải
        ColumnConstraints screenCol = new ColumnConstraints();
        screenCol.setPercentWidth(75); // Screen share 75%
        ColumnConstraints camCol = new ColumnConstraints();
        camCol.setPercentWidth(25); // Cams 25%
        videoGrid.getColumnConstraints().addAll(screenCol, camCol);

        // Row constraints
        RowConstraints screenRow = new RowConstraints();
        screenRow.setPercentHeight(100);
        videoGrid.getRowConstraints().add(screenRow);

        // Add screen share view to left column, spanning full height
        if (screenShareView != null) {
            videoGrid.add(screenShareView, 0, 0);
            GridPane.setRowSpan(screenShareView, 1);
            GridPane.setColumnSpan(screenShareView, 1);
            GridPane.setHgrow(screenShareView, Priority.ALWAYS);
            GridPane.setVgrow(screenShareView, Priority.ALWAYS);
        }

        // Add participant cams to right column (small tiles)
        VBox camContainer = new VBox(8);
        camContainer.setStyle("-fx-padding: 8;");
        camContainer.setFillWidth(true);

        for (Participant p : participants) {
            StackPane tile = createSmallTile(p); // Smaller tiles for screen share mode
            camContainer.getChildren().add(tile);
        }

        videoGrid.add(camContainer, 1, 0);
        GridPane.setVgrow(camContainer, Priority.ALWAYS);
    }

    private StackPane createSmallTile(Participant p) {
        StackPane tile = new StackPane();
        tile.getStyleClass().add("video-tile-small");
        tile.setMaxHeight(120);
        tile.setMaxWidth(180);

        if (p.isVideoOn()) {
            ImageView view = new ImageView();
            view.setPreserveRatio(true);
            view.setFitWidth(160);
            view.setFitHeight(100);
            videoViews.put(p.getId(), view);
            Image last = lastFrames.get(p.getId());
            if (last != null) {
                view.setImage(last);
            }
            tile.getChildren().add(view);
        } else {
            Label avatar = new Label(p.getName().substring(0, 1).toUpperCase());
            avatar.getStyleClass().add("avatar-circle-small");
            tile.getChildren().add(avatar);
        }

        HBox bottom = new HBox(5);
        bottom.getStyleClass().add("bottom-info-small");

        Label name = new Label(p.getName() + (p.getId().equals(currentUserId) ? " (Bạn)" : ""));
        name.getStyleClass().add("info-name-small");
        name.setStyle("-fx-font-size: 10px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label mic = new Label(p.isAudioOn() ? "🎤" : "🔇");
        mic.setStyle("-fx-font-size: 10px;");
        if (!p.isAudioOn())
            mic.getStyleClass().add("red");

        bottom.getChildren().addAll(name, spacer, mic);
        BorderPane wrapper = new BorderPane();
        wrapper.setBottom(bottom);
        tile.getChildren().add(wrapper);

        return tile;
    }

    private int calcColumns(int n) {
        if (n <= 1)
            return 1;
        if (n <= 2)
            return 2;
        if (n <= 4)
            return 2;
        if (n <= 6)
            return 3;
        return 4;
    }

    private StackPane createTile(Participant p) {
        StackPane tile = new StackPane();
        tile.getStyleClass().add("video-tile");

        if (p.isVideoOn()) {
            // ImageView để hiển thị frame webcam
            ImageView view = new ImageView();
            view.setPreserveRatio(true);
            view.setFitWidth(240);
            view.setFitHeight(180);
            videoViews.put(p.getId(), view);
            Image last = lastFrames.get(p.getId());
            if (last != null) {
                view.setImage(last);
            }
            tile.getChildren().add(view);
        } else {
            Label avatar = new Label(p.getName().substring(0, 1).toUpperCase());
            avatar.getStyleClass().add("avatar-circle");
            tile.getChildren().add(avatar);
        }

        HBox bottom = new HBox(10);
        bottom.getStyleClass().add("bottom-info");

        Label name = new Label(
                p.getName() + (p.getId().equals(currentUserId) ? " (Bạn)" : ""));
        name.getStyleClass().add("info-name");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label mic = new Label(p.isAudioOn() ? "🎤" : "🔇");
        mic.getStyleClass().add("status-icon");
        if (!p.isAudioOn())
            mic.getStyleClass().add("red");

        Label cam = new Label(p.isVideoOn() ? "" : "📵");
        cam.getStyleClass().add("status-icon");
        if (!p.isVideoOn())
            cam.getStyleClass().add("red");

        bottom.getChildren().addAll(name, spacer, mic, cam);

        BorderPane wrapper = new BorderPane();
        wrapper.setBottom(bottom);

        tile.getChildren().add(wrapper);

        return tile;
    }

    /**
     * Cập nhật frame video cho participant có userId tương ứng.
     */
    public void updateVideoFrame(String userId, Image image) {
        if (userId == null || image == null)
            return;
        lastFrames.put(userId, image);
        ImageView view = videoViews.get(userId);
        if (view != null) {
            view.setImage(image);
        }
    }

    /**
     * Hiển thị screen share từ một participant.
     */
    public void showScreen(String sharerId, Image image) {
        if (screenShareView == null) {
            screenShareView = new ImageView();
            screenShareView.setPreserveRatio(true);
            screenShareView.getStyleClass().add("screen-share-view");
        }

        currentScreenSharerId = sharerId;
        screenShareView.setImage(image);
        screenShareView.setVisible(true);
        screenShareView.setManaged(true);

        // Re-render grid với layout mới (screen share to nhất)
        renderGrid();
    }

    /**
     * Ẩn screen share.
     */
    public void hideScreen() {
        currentScreenSharerId = null;
        if (screenShareView != null) {
            screenShareView.setImage(null);
            screenShareView.setVisible(false);
            screenShareView.setManaged(false);
        }
        // Re-render grid về layout bình thường
        renderGrid();
    }
}
