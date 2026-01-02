package com.example.dacs4.network;

import com.example.dacs4.models.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class FileTransferManager {
    private static final int CHUNK_SIZE = 16 * 1024; // 64KB per chunk
    private P2PManager p2pManager;

    // Đang gửi: transferId -> FileTransferState
    private Map<String, FileTransferState> outgoingTransfers = new ConcurrentHashMap<>();

    // Đang nhận: transferId -> FileReceiveState
    private Map<String, FileReceiveState> incomingTransfers = new ConcurrentHashMap<>();

    // Callback UI khi nhận file xong (truyền tên file)
    private Consumer<String> onFileReceivedUI;

    public FileTransferManager(P2PManager p2pManager) {
        this.p2pManager = p2pManager;
    }

    public void setOnFileReceivedUI(Consumer<String> onFileReceivedUI) {
        this.onFileReceivedUI = onFileReceivedUI;
    }

    /**
     * Gửi file đến tất cả peers
     */
    public void sendFile(File file, String senderId, String senderName) throws IOException {
        String transferId = UUID.randomUUID().toString();
        long fileSize = file.length();

        // Tạo metadata
        P2PMessage request = new P2PMessage(MessageType.FILE_SHARE_REQUEST, senderId, "all");
        request.addPayload("transferId", transferId);
        request.addPayload("fileName", file.getName());
        request.addPayload("fileSize", String.valueOf(fileSize));
        request.addPayload("senderName", senderName);
        request.addPayload("totalChunks", String.valueOf((fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE));

        // Lưu state
        FileTransferState state = new FileTransferState(transferId, file, fileSize);
        outgoingTransfers.put(transferId, state);

        // Broadcast request
        p2pManager.broadcast(request);

        System.out.println("📤 Sent file share request: " + file.getName());
    }

    /**
     * Xử lý file share request từ peer
     */
    public void handleFileShareRequest(P2PMessage message) {
        String transferId = message.getPayloadString("transferId");
        String fileName = message.getPayloadString("fileName");
        long fileSize = Long.parseLong(message.getPayloadString("fileSize"));
        String senderName = message.getPayloadString("senderName");
        int totalChunks = Integer.parseInt(message.getPayloadString("totalChunks"));

        System.out.println("📥 Received file share request: " + fileName + " from " + senderName);

        // Auto-accept (hoặc show dialog cho user)
        acceptFileTransfer(transferId, fileName, fileSize, totalChunks, message.getFrom());
    }

    /**
     * Chấp nhận nhận file
     */
    private void acceptFileTransfer(String transferId, String fileName, long fileSize,
                                    int totalChunks, String senderId) {
        try {
            // Tạo file để nhận
            Path downloadPath = Paths.get("downloads", fileName);
            Files.createDirectories(downloadPath.getParent());

            FileReceiveState state = new FileReceiveState(
                    transferId, downloadPath.toFile(), fileSize, totalChunks
            );
            incomingTransfers.put(transferId, state);

            // Gửi accept
            P2PMessage accept = new P2PMessage(MessageType.FILE_SHARE_ACCEPT,
                    p2pManager.getCurrentUserId(), senderId);
            accept.addPayload("transferId", transferId);
            p2pManager.sendToPeer(senderId, accept);

            System.out.println("✅ Accepted file transfer: " + fileName);

        } catch (IOException e) {
            System.err.println("❌ Error accepting file: " + e.getMessage());
        }
    }

    /**
     * Xử lý accept từ peer - bắt đầu gửi chunks
     */
    public void handleFileShareAccept(P2PMessage message) {
        String transferId = message.getPayloadString("transferId");
        FileTransferState state = outgoingTransfers.get(transferId);

        if (state == null) {
            System.err.println("❌ Unknown transfer ID: " + transferId);
            return;
        }

        // Bắt đầu gửi chunks trong background thread
        new Thread(() -> sendFileChunks(transferId, state, message.getFrom())).start();
    }

    /**
     * Gửi file chunks
     */
    private void sendFileChunks(String transferId, FileTransferState state, String receiverId) {
        try (FileInputStream fis = new FileInputStream(state.file)) {
            byte[] buffer = new byte[CHUNK_SIZE];
            int chunkIndex = 0;
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) != -1) {
                // Tạo chunk message
                P2PMessage chunk = new P2PMessage(MessageType.FILE_CHUNK,
                        p2pManager.getCurrentUserId(), receiverId);
                chunk.addPayload("transferId", transferId);
                chunk.addPayload("chunkIndex", String.valueOf(chunkIndex));
                chunk.addPayload("chunkSize", String.valueOf(bytesRead));

                // Encode data as Base64
                String encodedData = Base64.getEncoder().encodeToString(
                        Arrays.copyOf(buffer, bytesRead)
                );
                chunk.addPayload("data", encodedData);

                p2pManager.sendToPeer(receiverId, chunk);

                chunkIndex++;
                state.sentChunks++;

                // Progress
                int progress = (int) ((state.sentChunks * 100.0) / state.totalChunks);
                System.out.println("📤 Sending chunk " + chunkIndex + "/" + state.totalChunks +
                        " (" + progress + "%)");

                // Small delay để không overwhelm network
                Thread.sleep(10);
            }

            System.out.println("✅ File sent completely: " + state.file.getName());
            outgoingTransfers.remove(transferId);

        } catch (Exception e) {
            System.err.println("❌ Error sending file chunks: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Xử lý chunk nhận được
     */
    public void handleFileChunk(P2PMessage message) {
        String transferId = message.getPayloadString("transferId");
        FileReceiveState state = incomingTransfers.get(transferId);

        if (state == null) {
            System.err.println("❌ Unknown transfer ID: " + transferId);
            return;
        }

        try {
            int chunkIndex = Integer.parseInt(message.getPayloadString("chunkIndex"));
            String encodedData = message.getPayloadString("data");
            byte[] data = Base64.getDecoder().decode(encodedData);

            // Ghi vào file
            synchronized (state) {
                if (state.fos == null) {
                    state.fos = new FileOutputStream(state.file);
                }
                state.fos.write(data);
                state.receivedChunks++;
            }

            // Progress
            int progress = (int) ((state.receivedChunks * 100.0) / state.totalChunks);
            System.out.println("📥 Received chunk " + (chunkIndex + 1) + "/" + state.totalChunks +
                    " (" + progress + "%)");

            // Send ACK
            P2PMessage ack = new P2PMessage(MessageType.CHUNK_ACK,
                    p2pManager.getCurrentUserId(), message.getFrom());
            ack.addPayload("transferId", transferId);
            ack.addPayload("chunkIndex", String.valueOf(chunkIndex));
            p2pManager.sendToPeer(message.getFrom(), ack);

            // Check if complete
            if (state.receivedChunks >= state.totalChunks) {
                completeFileReceive(transferId, state, message.getFrom());
            }

        } catch (Exception e) {
            System.err.println("❌ Error handling chunk: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Hoàn thành nhận file
     */
    private void completeFileReceive(String transferId, FileReceiveState state, String senderId) {
        try {
            if (state.fos != null) {
                state.fos.close();
            }

            System.out.println("✅ File received completely: " + state.file.getName());

            // Gọi callback UI nếu có
            if (onFileReceivedUI != null) {
                onFileReceivedUI.accept(state.file.getName());
            }

            // Send complete message
            P2PMessage complete = new P2PMessage(MessageType.FILE_COMPLETE,
                    p2pManager.getCurrentUserId(), senderId);
            complete.addPayload("transferId", transferId);
            complete.addPayload("fileName", state.file.getName());
            p2pManager.sendToPeer(senderId, complete);

            incomingTransfers.remove(transferId);

        } catch (IOException e) {
            System.err.println("❌ Error completing file receive: " + e.getMessage());
        }
    }

    // State classes
    private static class FileTransferState {
        File file;
        long fileSize;
        int totalChunks;
        int sentChunks = 0;

        FileTransferState(String transferId, File file, long fileSize) {
            this.file = file;
            this.fileSize = fileSize;
            this.totalChunks = (int) ((fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE);
        }
    }

    private static class FileReceiveState {
        File file;
        long fileSize;
        int totalChunks;
        int receivedChunks = 0;
        FileOutputStream fos;

        FileReceiveState(String transferId, File file, long fileSize, int totalChunks) {
            this.file = file;
            this.fileSize = fileSize;
            this.totalChunks = totalChunks;
        }
    }
}

