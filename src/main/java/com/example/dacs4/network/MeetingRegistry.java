package com.example.dacs4.network;

import com.example.dacs4.DB.SQLiteConnection;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Meeting Registry - Database-backed for cross-instance discovery
 */
public class MeetingRegistry {

    public static void registerMeeting(String meetingId, String hostIp, int hostPort) {
        try (SQLiteConnection db = new SQLiteConnection()) {
            db.createTables();
            db.registerActiveMeeting(meetingId, hostIp, hostPort);
            System.out.println("📝 Meeting registered: " + meetingId + " at " + hostIp + ":" + hostPort);
        } catch (SQLException e) {
            System.err.println("❌ Error registering meeting: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static HostInfo getMeetingHost(String meetingId) {
        try (SQLiteConnection db = new SQLiteConnection()) {
            db.createTables();
            ResultSet rs = db.getActiveMeeting(meetingId);

            if (rs.next()) {
                String ip = rs.getString("host_ip");
                int port = rs.getInt("host_port");
                return new HostInfo(ip, port);
            }
        } catch (SQLException e) {
            System.err.println("❌ Error getting meeting host: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public static void unregisterMeeting(String meetingId) {
        try (SQLiteConnection db = new SQLiteConnection()) {
            db.createTables();
            db.unregisterActiveMeeting(meetingId);
            System.out.println("🗑️ Meeting unregistered: " + meetingId);
        } catch (SQLException e) {
            System.err.println("❌ Error unregistering meeting: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static boolean meetingExists(String meetingId) {
        try (SQLiteConnection db = new SQLiteConnection()) {
            db.createTables();
            ResultSet rs = db.getActiveMeeting(meetingId);
            return rs.next();
        } catch (SQLException e) {
            System.err.println("❌ Error checking meeting existence: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static class HostInfo {
        public String ip;
        public int port;

        public HostInfo(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }

        @Override
        public String toString() {
            return ip + ":" + port;
        }
    }
}
