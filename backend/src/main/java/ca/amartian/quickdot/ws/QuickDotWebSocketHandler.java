package ca.amartian.quickdot.ws;

import ca.amartian.quickdot.audit.AuditLog;
import ca.amartian.quickdot.audit.IpHasher;
import ca.amartian.quickdot.relay.BlockedExtensions;
import ca.amartian.quickdot.relay.ChunkFrame;
import ca.amartian.quickdot.relay.FileTransfer;
import ca.amartian.quickdot.relay.FileTransferRegistry;
import ca.amartian.quickdot.relay.MimeSniffer;
import ca.amartian.quickdot.relay.TextRateLimiter;
import ca.amartian.quickdot.relay.UploadRateLimiter;
import ca.amartian.quickdot.room.Participant;
import ca.amartian.quickdot.room.Room;
import ca.amartian.quickdot.room.RoomFullException;
import ca.amartian.quickdot.room.RoomRegistry;
import ca.amartian.quickdot.ws.protocol.ErrorMessage;
import ca.amartian.quickdot.ws.protocol.FileEndMessage;
import ca.amartian.quickdot.ws.protocol.FileStartMessage;
import ca.amartian.quickdot.ws.protocol.JoinedMessage;
import ca.amartian.quickdot.ws.protocol.LeftMessage;
import ca.amartian.quickdot.ws.protocol.OutgoingTextMessage;
import ca.amartian.quickdot.ws.protocol.ParticipantView;
import ca.amartian.quickdot.ws.protocol.WelcomeMessage;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * One connection per participant at {@code /ws/{roomCode}} (spec §5).
 * Extends {@code AbstractWebSocketHandler} directly (not {@code
 * TextWebSocketHandler}) because file-chunk frames (Stage 4) are binary.
 */
@Component
public class QuickDotWebSocketHandler extends AbstractWebSocketHandler {

    static final String ATTR_PARTICIPANT_ID = "participantId";

    private final RoomRegistry roomRegistry;
    private final FunNameGenerator nameGenerator;
    private final SessionRegistry sessionRegistry;
    private final TextRateLimiter textRateLimiter;
    private final UploadRateLimiter uploadRateLimiter;
    private final FileTransferRegistry fileTransferRegistry;
    private final BlockedExtensions blockedExtensions;
    private final MimeSniffer mimeSniffer;
    private final ObjectMapper objectMapper;
    private final AuditLog auditLog;
    private final IpHasher ipHasher;
    private final int maxItemsPerRoom;
    private final long maxFileSizeBytes;

    public QuickDotWebSocketHandler(
            RoomRegistry roomRegistry,
            FunNameGenerator nameGenerator,
            SessionRegistry sessionRegistry,
            TextRateLimiter textRateLimiter,
            UploadRateLimiter uploadRateLimiter,
            FileTransferRegistry fileTransferRegistry,
            BlockedExtensions blockedExtensions,
            MimeSniffer mimeSniffer,
            ObjectMapper objectMapper,
            AuditLog auditLog,
            IpHasher ipHasher,
            @Value("${room.max-items:200}") int maxItemsPerRoom,
            @Value("${relay.file.max-size-bytes:26214400}") long maxFileSizeBytes) {
        this.roomRegistry = roomRegistry;
        this.nameGenerator = nameGenerator;
        this.sessionRegistry = sessionRegistry;
        this.textRateLimiter = textRateLimiter;
        this.uploadRateLimiter = uploadRateLimiter;
        this.fileTransferRegistry = fileTransferRegistry;
        this.blockedExtensions = blockedExtensions;
        this.mimeSniffer = mimeSniffer;
        this.objectMapper = objectMapper;
        this.auditLog = auditLog;
        this.ipHasher = ipHasher;
        this.maxItemsPerRoom = maxItemsPerRoom;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String roomCode = (String) session.getAttributes().get(RoomCodeHandshakeInterceptor.ATTR_ROOM_CODE);
        Optional<Room> roomOpt = roomRegistry.findActiveRoom(roomCode);
        if (roomOpt.isEmpty()) {
            sendQuiet(session, ErrorMessage.of("ROOM_NOT_FOUND", "Room not found or expired"));
            session.close(CloseStatus.NORMAL);
            return;
        }

        Participant participant;
        try {
            participant = roomRegistry.join(roomCode, nameGenerator.generate(), clientIp(session));
        } catch (RoomFullException e) {
            sendQuiet(session, ErrorMessage.of("ROOM_FULL", "Room is full"));
            session.close(CloseStatus.NORMAL);
            return;
        }

        session.getAttributes().put(ATTR_PARTICIPANT_ID, participant.id());
        sessionRegistry.register(participant.id(), session);
        auditLog.participantJoined(roomCode, ipHasher.hash(participant.ip()));

        Room room = roomOpt.get();
        List<ParticipantView> others = room.participants().stream()
                .filter(p -> !p.id().equals(participant.id()))
                .map(p -> new ParticipantView(p.id(), p.name()))
                .toList();

        sendQuiet(session, WelcomeMessage.of(
                new ParticipantView(participant.id(), participant.name()), others, room.expiresAt()));

        broadcastToOthers(room, participant.id(), JoinedMessage.of(participant.id(), participant.name()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String participantId = (String) session.getAttributes().get(ATTR_PARTICIPANT_ID);
        if (participantId == null) {
            return;
        }
        JsonNode node = objectMapper.readTree(message.getPayload());
        String type = node.path("type").asString("");
        switch (type) {
            case "pong" -> sessionRegistry.markPong(participantId);
            case "text" -> handleText(session, participantId, node);
            case "file-start" -> handleFileStart(session, participantId, node);
            case "file-end" -> handleFileEnd(session, participantId, node);
            default -> {
                // Unknown type — ignore.
            }
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        String participantId = (String) session.getAttributes().get(ATTR_PARTICIPANT_ID);
        if (participantId == null) {
            return;
        }
        handleFileChunk(session, participantId, message.getPayload());
    }

    private void handleText(WebSocketSession session, String participantId, JsonNode node) {
        String text = node.path("text").asString("");
        if (text.isBlank()) {
            return;
        }
        Room room = activeRoomOrNull(session);
        if (room == null) {
            return;
        }

        if (!textRateLimiter.tryAcquire(participantId)) {
            sendQuiet(session, ErrorMessage.of("RATE_LIMITED", "Too many messages — slow down"));
            return;
        }
        if (!room.tryIncrementItemCount(maxItemsPerRoom)) {
            sendQuiet(session, ErrorMessage.of("ROOM_ITEM_LIMIT", "This room has reached its item limit"));
            return;
        }

        Participant sender = findParticipant(room, participantId);
        if (sender == null) {
            return;
        }
        OutgoingTextMessage outgoing = OutgoingTextMessage.of(
                new ParticipantView(sender.id(), sender.name()), UUID.randomUUID().toString(), text);
        broadcastToOthers(room, participantId, outgoing);
    }

    private void handleFileStart(WebSocketSession session, String participantId, JsonNode node) {
        String transferId = node.path("transferId").asString("");
        String name = node.path("name").asString("");
        long declaredSize = node.path("size").asLong(-1);
        if (transferId.isBlank() || name.isBlank() || declaredSize < 0) {
            return;
        }

        Room room = activeRoomOrNull(session);
        if (room == null) {
            return;
        }

        if (declaredSize > maxFileSizeBytes) {
            sendQuiet(session, ErrorMessage.of("FILE_TOO_LARGE", "File exceeds the 25 MB limit"));
            return;
        }
        if (blockedExtensions.isBlocked(name)) {
            sendQuiet(session, ErrorMessage.of("TYPE_BLOCKED", "This file type isn't allowed"));
            return;
        }
        if (fileTransferRegistry.hasActiveUpload(participantId)) {
            sendQuiet(session, ErrorMessage.of("UPLOAD_IN_PROGRESS", "Finish your current upload first"));
            return;
        }
        if (!uploadRateLimiter.tryAcquire(room.code())) {
            sendQuiet(session, ErrorMessage.of("RATE_LIMITED", "Too many uploads in this room — slow down"));
            return;
        }
        if (!room.tryIncrementItemCount(maxItemsPerRoom)) {
            sendQuiet(session, ErrorMessage.of("ROOM_ITEM_LIMIT", "This room has reached its item limit"));
            return;
        }

        Participant sender = findParticipant(room, participantId);
        if (sender == null) {
            return;
        }
        String mime = node.path("mime").asString("");
        fileTransferRegistry.start(new FileTransfer(transferId, room.code(), participantId, name, mime, declaredSize));

        broadcastToOthers(room, participantId, FileStartMessage.of(
                new ParticipantView(sender.id(), sender.name()), transferId, name, mime, declaredSize));
    }

    private void handleFileChunk(WebSocketSession session, String participantId, ByteBuffer raw) {
        ChunkFrame frame;
        try {
            frame = ChunkFrame.decode(raw);
        } catch (RuntimeException e) {
            return; // malformed frame — drop silently, no state to clean up
        }

        Optional<FileTransfer> transferOpt = fileTransferRegistry.find(frame.transferId());
        if (transferOpt.isEmpty()) {
            return;
        }
        FileTransfer transfer = transferOpt.get();
        if (!transfer.senderId().equals(participantId)) {
            return; // not this connection's transfer
        }
        if (!transfer.acceptSequence(frame.sequence())) {
            abortTransfer(session, transfer, "FILE_TRANSFER_CORRUPT", "Chunk sequence out of order");
            return;
        }

        long totalAfterThisChunk = transfer.addBytes(frame.payload());
        if (totalAfterThisChunk > transfer.declaredSize() || totalAfterThisChunk > maxFileSizeBytes) {
            abortTransfer(session, transfer, "FILE_TOO_LARGE", "File exceeds the 25 MB limit");
            return;
        }

        if (transfer.isFirstChunk()) {
            String sniffed = mimeSniffer.detect(frame.payload());
            if (mimeSniffer.isDangerous(sniffed)) {
                abortTransfer(session, transfer, "TYPE_BLOCKED", "This file type isn't allowed");
                return;
            }
        }

        roomRegistry.findActiveRoom(transfer.roomCode())
                .ifPresent(room -> broadcastBinaryToOthers(room, participantId, frame));
    }

    private void handleFileEnd(WebSocketSession session, String participantId, JsonNode node) {
        String transferId = node.path("transferId").asString("");
        Optional<FileTransfer> transferOpt = fileTransferRegistry.find(transferId);
        if (transferOpt.isEmpty()) {
            return;
        }
        FileTransfer transfer = transferOpt.get();
        if (!transfer.senderId().equals(participantId)) {
            return;
        }
        fileTransferRegistry.remove(transferId);

        roomRegistry.findActiveRoom(transfer.roomCode()).ifPresent(room -> {
            Participant sender = findParticipant(room, participantId);
            if (sender != null) {
                auditLog.fileRelayed(room.code(), ipHasher.hash(sender.ip()),
                        transfer.digestHex(), transfer.bytesReceived(), transfer.mime());
                broadcastToOthers(room, participantId,
                        FileEndMessage.of(new ParticipantView(sender.id(), sender.name()), transferId));
            }
        });
    }

    /** Drops the transfer and tells the uploader why — peers just see the transfer stop. */
    private void abortTransfer(WebSocketSession session, FileTransfer transfer, String code, String message) {
        fileTransferRegistry.remove(transfer.transferId());
        sendQuiet(session, ErrorMessage.of(code, message));
    }

    private Room activeRoomOrNull(WebSocketSession session) {
        String roomCode = (String) session.getAttributes().get(RoomCodeHandshakeInterceptor.ATTR_ROOM_CODE);
        return roomRegistry.findActiveRoom(roomCode).orElse(null);
    }

    private Participant findParticipant(Room room, String participantId) {
        return room.participants().stream()
                .filter(p -> p.id().equals(participantId))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String roomCode = (String) session.getAttributes().get(RoomCodeHandshakeInterceptor.ATTR_ROOM_CODE);
        String participantId = (String) session.getAttributes().get(ATTR_PARTICIPANT_ID);
        if (roomCode == null || participantId == null) {
            return;
        }
        roomRegistry.leave(roomCode, participantId);
        sessionRegistry.unregister(participantId);
        textRateLimiter.clear(participantId);
        roomRegistry.findActiveRoom(roomCode)
                .ifPresent(room -> broadcastToOthers(room, participantId, LeftMessage.of(participantId)));
    }

    private void broadcastToOthers(Room room, String excludeParticipantId, Object message) {
        for (Participant participant : room.participants()) {
            if (participant.id().equals(excludeParticipantId)) {
                continue;
            }
            sessionRegistry.find(participant.id()).ifPresent(session -> sendQuiet(session, message));
        }
    }

    private void broadcastBinaryToOthers(Room room, String excludeParticipantId, ChunkFrame frame) {
        for (Participant participant : room.participants()) {
            if (participant.id().equals(excludeParticipantId)) {
                continue;
            }
            sessionRegistry.find(participant.id()).ifPresent(session -> sendBinaryQuiet(session, frame));
        }
    }

    private void sendQuiet(WebSocketSession session, Object message) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        } catch (IOException e) {
            // Best-effort: a broken pipe here just means the peer is already gone.
        }
    }

    private void sendBinaryQuiet(WebSocketSession session, ChunkFrame frame) {
        try {
            session.sendMessage(new BinaryMessage(frame.encode()));
        } catch (IOException e) {
            // Best-effort: a broken pipe here just means the peer is already gone.
        }
    }

    private String clientIp(WebSocketSession session) {
        InetSocketAddress address = session.getRemoteAddress();
        return address != null ? address.getAddress().getHostAddress() : "unknown";
    }
}
