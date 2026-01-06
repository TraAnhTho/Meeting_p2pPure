package com.example.dacs4.network;

import java.io.IOException;
import java.util.function.IntSupplier;

/**
 * Centralized service for meeting discovery across multiple strategies:
 * - Multicast discovery (host announces periodically)
 * - LAN broadcast discovery (request/reply)
 * - Database registry lookup (same machine only)
 */
public class MeetingDiscoveryService {

    private final LanMeetingDiscovery lanDiscovery = new LanMeetingDiscovery();
    private final LanMeetingMulticast multicast = new LanMeetingMulticast();

    /**
     * Start announcing this meeting as a host via multicast and LAN responder
     */
    public void startHostAnnouncement(String meetingId, String ip, int port, IntSupplier participantCountSupplier)
            throws IOException {
        // Start multicast announcement
        try {
            multicast.startAnnounce(meetingId, ip, port, participantCountSupplier);
            System.out.println("📡 Multicast announcement started for meeting: " + meetingId);
        } catch (Exception e) {
            System.err.println("❌ Failed to start multicast announce: " + e.getMessage());
            System.err.println("⚠️ Warning: LAN multicast discovery may not work on this network");
        }

        // Start LAN discovery responder
        try {
            lanDiscovery.startHostResponder(meetingId, port);
            System.out.println("✅ LAN discovery responder started for meeting: " + meetingId);
        } catch (Exception e) {
            System.err.println("❌ Failed to start LAN discovery responder: " + e.getMessage());
            e.printStackTrace();
            System.err.println(
                    "⚠️ Warning: Participants on other machines may not be able to discover this meeting via LAN");
        }
    }

    /**
     * Stop all host announcements
     */
    public void stopHostAnnouncement() {
        try {
            multicast.stopAnnounce();
        } catch (Exception ignored) {
        }

        try {
            lanDiscovery.stopHostResponder();
        } catch (Exception ignored) {
        }

        System.out.println("🛑 Host announcement stopped");
    }

    /**
     * Announce that a meeting has been closed
     */
    public void announceMeetingClosed(String meetingId) {
        try {
            multicast.announceClosed(meetingId);
            System.out.println("📡 Announced meeting closed via multicast");
        } catch (Exception e) {
            System.err.println("❌ Failed to announce meeting closed: " + e.getMessage());
        }
    }

    /**
     * Discover host using all available strategies with fallback
     * Strategy order:
     * 1. Multicast discovery (fastest, works across machines)
     * 2. LAN broadcast discovery (fallback, works across machines)
     * 3. Database registry (last resort, same machine only)
     * 
     * @return HostInfo if found, null otherwise
     */
    public MeetingRegistry.HostInfo discoverHost(String meetingId) {
        System.out.println("🔍 Starting multi-strategy discovery for meeting: " + meetingId);

        // Step 1: Try multicast discovery
        MeetingRegistry.HostInfo host = discoverViaMulticast(meetingId, 5000);
        if (host != null) {
            System.out.println("📡 Found meeting via multicast: " + host.ip + ":" + host.port);
            // Save to database for future reference
            registerMeetingQuietly(meetingId, host.ip, host.port);
            return host;
        }

        // Step 2: Try LAN broadcast discovery
        System.out.println("🔍 Multicast failed, trying LAN broadcast discovery...");
        host = discoverViaLan(meetingId, 3);
        if (host != null) {
            System.out.println("📡 Found meeting via LAN discovery: " + host.ip + ":" + host.port);
            // Save to database for future reference
            registerMeetingQuietly(meetingId, host.ip, host.port);
            return host;
        }

        // Step 3: Try database registry
        System.out.println("🔍 Discovery failed, checking local database...");
        host = discoverViaDatabase(meetingId);
        if (host != null) {
            System.out.println("📝 Found meeting in local database: " + host.ip + ":" + host.port);
            return host;
        }

        System.err.println("❌ Meeting not found via any discovery method: " + meetingId);
        return null;
    }

    /**
     * Discover host via multicast only
     */
    public MeetingRegistry.HostInfo discoverViaMulticast(String meetingId, int timeoutMs) {
        try {
            LanMeetingMulticast.HostInfo hi = multicast.discoverHost(meetingId, timeoutMs);
            if (hi != null) {
                return new MeetingRegistry.HostInfo(hi.ip, hi.port);
            }
        } catch (Exception e) {
            System.err.println("❌ Multicast discovery error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Discover host via LAN broadcast only
     */
    public MeetingRegistry.HostInfo discoverViaLan(String meetingId, int retries) {
        for (int i = 1; i <= retries; i++) {
            try {
                MeetingRegistry.HostInfo discovered = lanDiscovery.discoverHost(meetingId);
                if (discovered != null) {
                    return discovered;
                }
            } catch (IOException ignored) {
            }

            if (i < retries) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return null;
    }

    /**
     * Discover host via database registry only
     */
    public MeetingRegistry.HostInfo discoverViaDatabase(String meetingId) {
        return MeetingRegistry.getMeetingHost(meetingId);
    }

    /**
     * Register meeting in database
     */
    public void registerMeeting(String meetingId, String ip, int port) {
        MeetingRegistry.registerMeeting(meetingId, ip, port);
        System.out.println("📝 Meeting registered in database: " + meetingId + " at " + ip + ":" + port);
    }

    /**
     * Register meeting in database without logging (for background saves)
     */
    private void registerMeetingQuietly(String meetingId, String ip, int port) {
        try {
            MeetingRegistry.registerMeeting(meetingId, ip, port);
        } catch (Exception ignored) {
        }
    }

    /**
     * Unregister meeting from database
     */
    public void unregisterMeeting(String meetingId) {
        MeetingRegistry.unregisterMeeting(meetingId);
        System.out.println("🗑️ Meeting unregistered from database: " + meetingId);
    }
}
