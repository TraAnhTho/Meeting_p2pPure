package com.example.dacs4;

import com.example.dacs4.controllers.DashboardController;
import com.example.dacs4.controllers.MeetingRoomController;
import com.example.dacs4.network.PeerDiscovery;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

public class App extends Application {

    private static Stage mainStage;

    // ===============================
    // GLOBAL SOCKET CLIENT
    // ===============================
    private static PeerDiscovery socketClient;

    public static void setSocketClient(PeerDiscovery client) {
        socketClient = client;
    }

    public static PeerDiscovery getSocketClient() {
        return socketClient;
    }

    @Override
    public void start(Stage stage) {
        this.mainStage = stage;
        goToLogin();
    }

    private URL getFXML(String name) {
        return getClass().getResource("/fxml/" + name);
    }

    // ==========================
    // SERVER CONFIG SCREEN (DISABLED)
    // ==========================
    /*
     * public static void goToServerConfig() {
     * try {
     * URL url = App.class.getResource("/fxml/serverConfig.fxml");
     * FXMLLoader loader = new FXMLLoader(url);
     * Parent ui = loader.load();
     * 
     * Scene scene = new Scene(ui, 600, 400);
     * mainStage.setScene(scene);
     * mainStage.setTitle("Kết nối server");
     * mainStage.show();
     * 
     * } catch (Exception e) {
     * e.printStackTrace();
     * }
     * }
     */

    // ==========================
    // LOGIN SCREEN
    // ==========================
    public static void goToLogin() {
        try {
            URL url = App.class.getResource("/fxml/login.fxml");
            FXMLLoader loader = new FXMLLoader(url);
            Parent ui = loader.load();

            Scene scene = new Scene(ui, 1200, 800);
            mainStage.setScene(scene);
            mainStage.setTitle("MeetHub - Login");
            mainStage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==========================
    // DASHBOARD
    // ==========================
    public static String currentUserId;
    public static String currentUserName;
    public static String currentUserEmail;
    public static String currentUserAvatar;

    public static void goToDashboard() {
        try {
            App tempApp = new App();
            FXMLLoader loader = new FXMLLoader(tempApp.getFXML("dashboard.fxml"));
            Parent ui = loader.load();

            DashboardController controller = loader.getController();

            // Truyền thông tin user
            if (currentUserId != null) {
                controller.setUser(currentUserId, currentUserName, currentUserEmail, currentUserAvatar);
            }

            controller.setOnJoinMeeting(meetingId -> {
                goToMeetingRoom(meetingId);
            });

            controller.setOnLogout(() -> {
                goToLogin();
            });

            Scene scene = new Scene(ui, 1200, 800);
            mainStage.setScene(scene);
            mainStage.setTitle("MeetHub - Dashboard");
            mainStage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==========================
    // MEETING ROOM
    // ==========================
    public static void goToMeetingRoom(String meetingId) {
        try {
            App tempApp = new App();
            FXMLLoader loader = new FXMLLoader(tempApp.getFXML("meetingRoom.fxml"));
            Parent ui = loader.load();

            MeetingRoomController controller = loader.getController();
            controller.setMeetingId(meetingId);
            // Note: P2PManager sẽ được khởi tạo bên trong MeetingRoomController

            // Truyền thông tin user
            if (currentUserId != null) {
                controller.setUser(currentUserId, currentUserName, currentUserAvatar);

                // Set role dựa trên meeting history
                try (com.example.dacs4.DB.SQLiteConnection db = new com.example.dacs4.DB.SQLiteConnection()) {
                    db.createTables();
                    java.util.List<com.example.dacs4.models.MeetingHistory> history = db
                            .getMeetingHistory(currentUserId);
                    for (com.example.dacs4.models.MeetingHistory h : history) {
                        if (h.getMeetingId().equals(meetingId)) {
                            controller.setUserRole(h.getRole());
                            break;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // Callback khi rời meeting
            controller.setOnLeaveMeeting(() -> {
                goToDashboard();
            });

            Scene scene = new Scene(ui, 1200, 800);
            mainStage.setScene(scene);
            mainStage.setTitle("Room " + meetingId);
            mainStage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch();
    }
}
