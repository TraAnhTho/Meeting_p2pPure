package com.example.dacs4.models;

import java.util.HashMap;
import java.util.Map;

public class P2PMessage {
    private MessageType type;
    private String from;
    private String to; // "all" for broadcast, specific userId for direct message
    private String meetingId;
    private long timestamp;
    private Map<String, Object> payload;

    public P2PMessage() {
        this.timestamp = System.currentTimeMillis();
        this.payload = new HashMap<>();
    }

    public P2PMessage(MessageType type, String from, String to) {
        this();
        this.type = type;
        this.from = from;
        this.to = to;
    }

    // Simple JSON serialization (without external library)
    public String toJson() {
        StringBuilder json = new StringBuilder("{");
        json.append("\"type\":\"").append(type).append("\",");
        json.append("\"from\":\"").append(escapeJson(from)).append("\",");
        json.append("\"to\":\"").append(escapeJson(to)).append("\",");
        json.append("\"meetingId\":\"").append(escapeJson(meetingId)).append("\",");
        json.append("\"timestamp\":").append(timestamp).append(",");
        json.append("\"payload\":{");

        boolean first = true;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (!first)
                json.append(",");
            json.append("\"").append(escapeJson(entry.getKey())).append("\":");

            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(escapeJson((String) value)).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                json.append(value);
            } else {
                json.append("\"").append(escapeJson(String.valueOf(value))).append("\"");
            }
            first = false;
        }

        json.append("}}");
        return json.toString();
    }

    // Simple JSON deserialization
    public static P2PMessage fromJson(String json) {
        P2PMessage message = new P2PMessage();

        try {
            // Remove outer braces
            json = json.trim();
            if (json.startsWith("{"))
                json = json.substring(1);
            if (json.endsWith("}"))
                json = json.substring(0, json.length() - 1);

            // Parse fields
            String[] parts = splitJson(json);
            for (String part : parts) {
                String[] keyValue = part.split(":", 2);
                if (keyValue.length != 2)
                    continue;

                String key = keyValue[0].trim().replace("\"", "");
                String value = keyValue[1].trim();

                switch (key) {
                    case "type":
                        message.type = MessageType.valueOf(value.replace("\"", ""));
                        break;
                    case "from":
                        message.from = value.replace("\"", "");
                        break;
                    case "to":
                        message.to = value.replace("\"", "");
                        break;
                    case "meetingId":
                        message.meetingId = value.replace("\"", "");
                        break;
                    case "timestamp":
                        message.timestamp = Long.parseLong(value);
                        break;
                    case "payload":
                        // Simple payload parsing
                        if (value.startsWith("{") && value.endsWith("}")) {
                            String payloadContent = value.substring(1, value.length() - 1);
                            String[] payloadParts = splitJson(payloadContent);
                            for (String payloadPart : payloadParts) {
                                String[] payloadKV = payloadPart.split(":", 2);
                                if (payloadKV.length == 2) {
                                    String pKey = payloadKV[0].trim().replace("\"", "");
                                    String pValue = payloadKV[1].trim().replace("\"", "");
                                    message.payload.put(pKey, pValue);
                                }
                            }
                        }
                        break;
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing JSON: " + e.getMessage());
        }

        return message;
    }

    private static String[] splitJson(String json) {
        // Simple split by comma, ignoring commas inside nested objects
        java.util.List<String> parts = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        int braceLevel = 0;

        for (char c : json.toCharArray()) {
            if (c == '{')
                braceLevel++;
            else if (c == '}')
                braceLevel--;
            else if (c == ',' && braceLevel == 0) {
                parts.add(current.toString());
                current = new StringBuilder();
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }

        return parts.toArray(new String[0]);
    }

    private String escapeJson(String str) {
        if (str == null)
            return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    // Getters and Setters
    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getMeetingId() {
        return meetingId;
    }

    public void setMeetingId(String meetingId) {
        this.meetingId = meetingId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    // Helper methods for payload
    public void addPayload(String key, Object value) {
        this.payload.put(key, value);
    }

    public Object getPayloadValue(String key) {
        return this.payload.get(key);
    }

    public String getPayloadString(String key) {
        Object value = this.payload.get(key);
        return value != null ? value.toString() : null;
    }

    @Override
    public String toString() {
        return "P2PMessage{" +
                "type=" + type +
                ", from='" + from + '\'' +
                ", to='" + to + '\'' +
                ", meetingId='" + meetingId + '\'' +
                ", timestamp=" + timestamp +
                ", payload=" + payload +
                '}';
    }
}
