package ca.amartian.quickdot.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.amartian.quickdot.relay.BlockedExtensions;
import ca.amartian.quickdot.relay.ChunkFrame;
import ca.amartian.quickdot.relay.FileTransferRegistry;
import ca.amartian.quickdot.relay.MimeSniffer;
import ca.amartian.quickdot.relay.TextRateLimiter;
import ca.amartian.quickdot.relay.UploadRateLimiter;
import ca.amartian.quickdot.room.Room;
import ca.amartian.quickdot.room.RoomCodeGenerator;
import ca.amartian.quickdot.room.RoomRegistry;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

class QuickDotWebSocketHandlerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"));
    private static final String BLOCKED_EXTENSIONS_CSV =
            "exe,bat,cmd,com,msi,scr,ps1,sh,bash,vbs,vbe,jar,apk,deb,dmg,pkg,app,gadget,cpl,msc,wsf,hta,reg";

    private RoomRegistry roomRegistry;
    private SessionRegistry sessionRegistry;
    private QuickDotWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        roomRegistry = new RoomRegistry(new RoomCodeGenerator(), CLOCK, 30, 30, 8);
        sessionRegistry = new SessionRegistry(CLOCK);
        handler = newHandler(roomRegistry, sessionRegistry, new TextRateLimiter(CLOCK, 30, 10),
                new UploadRateLimiter(CLOCK, 10, 60), new FileTransferRegistry(), 200, 26_214_400L);
    }

    private QuickDotWebSocketHandler newHandler(RoomRegistry roomRegistry, SessionRegistry sessionRegistry,
            TextRateLimiter textRateLimiter, UploadRateLimiter uploadRateLimiter,
            FileTransferRegistry fileTransferRegistry, int maxItemsPerRoom, long maxFileSizeBytes) {
        return new QuickDotWebSocketHandler(roomRegistry, new FunNameGenerator(), sessionRegistry, textRateLimiter,
                uploadRateLimiter, fileTransferRegistry, new BlockedExtensions(BLOCKED_EXTENSIONS_CSV),
                new MimeSniffer(), new ObjectMapper(), maxItemsPerRoom, maxFileSizeBytes);
    }

    private WebSocketSession mockSession(String roomCode) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(RoomCodeHandshakeInterceptor.ATTR_ROOM_CODE, roomCode);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.getRemoteAddress()).thenReturn(new InetSocketAddress("203.0.113.5", 12345));
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private String fileStartJson(String transferId, String name, long size) {
        return "{\"type\":\"file-start\",\"transferId\":\"" + transferId + "\",\"name\":\"" + name
                + "\",\"mime\":\"application/octet-stream\",\"size\":" + size + "}";
    }

    @Test
    void joiningSendsWelcomeWithSelfAndNoOtherParticipants() throws Exception {
        Room room = roomRegistry.createRoom();
        WebSocketSession session = mockSession(room.code());

        handler.afterConnectionEstablished(session);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        String payload = captor.getValue().getPayload();
        assertThat(payload).contains("\"type\":\"welcome\"");
        assertThat(payload).contains("\"participants\":[]");
        assertThat(room.participantCount()).isEqualTo(1);
    }

    @Test
    void secondJoinerBroadcastsJoinedToFirst() throws Exception {
        Room room = roomRegistry.createRoom();
        WebSocketSession first = mockSession(room.code());
        handler.afterConnectionEstablished(first);
        clearInvocations(first);

        WebSocketSession second = mockSession(room.code());
        handler.afterConnectionEstablished(second);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(first).sendMessage(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("\"type\":\"joined\"");
    }

    @Test
    void unknownRoomSendsErrorAndCloses() throws Exception {
        WebSocketSession session = mockSession("ZZZZZZ");

        handler.afterConnectionEstablished(session);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("ROOM_NOT_FOUND");
        verify(session).close(CloseStatus.NORMAL);
    }

    @Test
    void disconnectRemovesParticipantAndBroadcastsLeft() throws Exception {
        Room room = roomRegistry.createRoom();
        WebSocketSession first = mockSession(room.code());
        handler.afterConnectionEstablished(first);
        WebSocketSession second = mockSession(room.code());
        handler.afterConnectionEstablished(second);
        clearInvocations(first, second);

        handler.afterConnectionClosed(second, CloseStatus.NORMAL);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(first).sendMessage(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("\"type\":\"left\"");
        assertThat(room.participantCount()).isEqualTo(1);
    }

    @Test
    void pongMessageMarksSessionFresh() throws Exception {
        Room room = roomRegistry.createRoom();
        WebSocketSession session = mockSession(room.code());
        handler.afterConnectionEstablished(session);
        String participantId = room.participants().iterator().next().id();

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"pong\"}"));

        assertThat(sessionRegistry.isStale(participantId, Duration.ZERO)).isFalse();
    }

    @Test
    void textIsRelayedToOthersNotBackToSender() throws Exception {
        Room room = roomRegistry.createRoom();
        WebSocketSession sender = mockSession(room.code());
        handler.afterConnectionEstablished(sender);
        WebSocketSession other = mockSession(room.code());
        handler.afterConnectionEstablished(other);
        clearInvocations(sender, other);

        handler.handleTextMessage(sender, new TextMessage("{\"type\":\"text\",\"text\":\"hello\"}"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(other).sendMessage(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("\"type\":\"text\"").contains("hello");
        verify(sender, never()).sendMessage(any());
    }

    @Test
    void textOverRateLimitSendsErrorToSender() throws Exception {
        RoomRegistry limitedRoomRegistry = new RoomRegistry(new RoomCodeGenerator(), CLOCK, 30, 30, 8);
        QuickDotWebSocketHandler limitedHandler = newHandler(limitedRoomRegistry, new SessionRegistry(CLOCK),
                new TextRateLimiter(CLOCK, 1, 10), new UploadRateLimiter(CLOCK, 10, 60),
                new FileTransferRegistry(), 200, 26_214_400L);
        Room room = limitedRoomRegistry.createRoom();
        WebSocketSession session = mockSession(room.code());
        limitedHandler.afterConnectionEstablished(session);
        clearInvocations(session);

        limitedHandler.handleTextMessage(session, new TextMessage("{\"type\":\"text\",\"text\":\"one\"}"));
        limitedHandler.handleTextMessage(session, new TextMessage("{\"type\":\"text\",\"text\":\"two\"}"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("RATE_LIMITED");
    }

    @Test
    void textOverRoomItemCapSendsError() throws Exception {
        RoomRegistry cappedRoomRegistry = new RoomRegistry(new RoomCodeGenerator(), CLOCK, 30, 30, 8);
        QuickDotWebSocketHandler cappedHandler = newHandler(cappedRoomRegistry, new SessionRegistry(CLOCK),
                new TextRateLimiter(CLOCK, 30, 10), new UploadRateLimiter(CLOCK, 10, 60),
                new FileTransferRegistry(), 1, 26_214_400L);
        Room room = cappedRoomRegistry.createRoom();
        WebSocketSession session = mockSession(room.code());
        cappedHandler.afterConnectionEstablished(session);
        clearInvocations(session);

        cappedHandler.handleTextMessage(session, new TextMessage("{\"type\":\"text\",\"text\":\"one\"}"));
        cappedHandler.handleTextMessage(session, new TextMessage("{\"type\":\"text\",\"text\":\"two\"}"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("ROOM_ITEM_LIMIT");
    }

    @Test
    void fileStartRelaysToOthersWithFrom() throws Exception {
        Room room = roomRegistry.createRoom();
        WebSocketSession sender = mockSession(room.code());
        handler.afterConnectionEstablished(sender);
        WebSocketSession other = mockSession(room.code());
        handler.afterConnectionEstablished(other);
        clearInvocations(sender, other);

        handler.handleTextMessage(sender, new TextMessage(fileStartJson("t1", "photo.png", 1000)));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(other).sendMessage(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("\"type\":\"file-start\"").contains("photo.png");
        verify(sender, never()).sendMessage(any());
    }

    @Test
    void fileStartOverSizeLimitSendsError() throws Exception {
        Room room = roomRegistry.createRoom();
        WebSocketSession sender = mockSession(room.code());
        handler.afterConnectionEstablished(sender);
        clearInvocations(sender);

        handler.handleTextMessage(sender, new TextMessage(fileStartJson("t1", "big.png", 26_214_401L)));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(sender).sendMessage(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("FILE_TOO_LARGE");
    }

    @Test
    void fileStartBlockedExtensionSendsError() throws Exception {
        Room room = roomRegistry.createRoom();
        WebSocketSession sender = mockSession(room.code());
        handler.afterConnectionEstablished(sender);
        clearInvocations(sender);

        handler.handleTextMessage(sender, new TextMessage(fileStartJson("t1", "virus.exe", 1000)));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(sender).sendMessage(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("TYPE_BLOCKED");
    }

    @Test
    void secondConcurrentUploadFromSameConnectionIsRejected() throws Exception {
        Room room = roomRegistry.createRoom();
        WebSocketSession sender = mockSession(room.code());
        handler.afterConnectionEstablished(sender);
        handler.handleTextMessage(sender, new TextMessage(fileStartJson("t1", "one.png", 1000)));
        clearInvocations(sender);

        handler.handleTextMessage(sender, new TextMessage(fileStartJson("t2", "two.png", 1000)));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(sender).sendMessage(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("UPLOAD_IN_PROGRESS");
    }

    @Test
    void uploadFloodOverRoomRateLimitSendsError() throws Exception {
        RoomRegistry limitedRoomRegistry = new RoomRegistry(new RoomCodeGenerator(), CLOCK, 30, 30, 8);
        QuickDotWebSocketHandler limitedHandler = newHandler(limitedRoomRegistry, new SessionRegistry(CLOCK),
                new TextRateLimiter(CLOCK, 30, 10), new UploadRateLimiter(CLOCK, 1, 60),
                new FileTransferRegistry(), 200, 26_214_400L);
        Room room = limitedRoomRegistry.createRoom();
        WebSocketSession sender = mockSession(room.code());
        limitedHandler.afterConnectionEstablished(sender);
        limitedHandler.handleTextMessage(sender, new TextMessage(fileStartJson("t1", "one.png", 1000)));
        limitedHandler.handleTextMessage(sender, new TextMessage("{\"type\":\"file-end\",\"transferId\":\"t1\"}"));
        clearInvocations(sender);

        limitedHandler.handleTextMessage(sender, new TextMessage(fileStartJson("t2", "two.png", 1000)));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(sender).sendMessage(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("RATE_LIMITED");
    }

    @Test
    void fileChunkIsRelayedAsBinaryToOthers() throws Exception {
        Room room = roomRegistry.createRoom();
        WebSocketSession sender = mockSession(room.code());
        handler.afterConnectionEstablished(sender);
        WebSocketSession other = mockSession(room.code());
        handler.afterConnectionEstablished(other);
        handler.handleTextMessage(sender, new TextMessage(fileStartJson("t1", "note.txt", 5)));
        clearInvocations(sender, other);

        byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
        handler.handleBinaryMessage(sender, new BinaryMessage(new ChunkFrame("t1", 0, payload).encode()));

        ArgumentCaptor<BinaryMessage> captor = ArgumentCaptor.forClass(BinaryMessage.class);
        verify(other).sendMessage(captor.capture());
        ChunkFrame relayed = ChunkFrame.decode(captor.getValue().getPayload());
        assertThat(relayed.transferId()).isEqualTo("t1");
        assertThat(relayed.payload()).isEqualTo(payload);
        verify(sender, never()).sendMessage(any());
    }

    @Test
    void fileChunkExceedingDeclaredSizeAbortsTransfer() throws Exception {
        Room room = roomRegistry.createRoom();
        WebSocketSession sender = mockSession(room.code());
        handler.afterConnectionEstablished(sender);
        handler.handleTextMessage(sender, new TextMessage(fileStartJson("t1", "note.txt", 3)));
        clearInvocations(sender);

        byte[] payload = "toolong".getBytes(StandardCharsets.UTF_8);
        handler.handleBinaryMessage(sender, new BinaryMessage(new ChunkFrame("t1", 0, payload).encode()));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(sender).sendMessage(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("FILE_TOO_LARGE");
    }

    @Test
    void fileChunkWithExecutableMagicBytesIsBlocked() throws Exception {
        Room room = roomRegistry.createRoom();
        WebSocketSession sender = mockSession(room.code());
        handler.afterConnectionEstablished(sender);
        WebSocketSession other = mockSession(room.code());
        handler.afterConnectionEstablished(other);
        handler.handleTextMessage(sender, new TextMessage(fileStartJson("t1", "totally-safe.png", 1000)));
        clearInvocations(sender, other);

        byte[] mzHeader = new byte[64];
        mzHeader[0] = 'M';
        mzHeader[1] = 'Z';
        handler.handleBinaryMessage(sender, new BinaryMessage(new ChunkFrame("t1", 0, mzHeader).encode()));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(sender).sendMessage(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("TYPE_BLOCKED");
        verify(other, never()).sendMessage(any());
    }

    @Test
    void fileEndRelaysToOthersAndFreesUploadSlot() throws Exception {
        Room room = roomRegistry.createRoom();
        WebSocketSession sender = mockSession(room.code());
        handler.afterConnectionEstablished(sender);
        WebSocketSession other = mockSession(room.code());
        handler.afterConnectionEstablished(other);
        handler.handleTextMessage(sender, new TextMessage(fileStartJson("t1", "note.txt", 5)));
        clearInvocations(sender, other);

        handler.handleTextMessage(sender, new TextMessage("{\"type\":\"file-end\",\"transferId\":\"t1\"}"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(other).sendMessage(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("\"type\":\"file-end\"");

        // Upload slot freed — a new upload from the same connection is now allowed.
        clearInvocations(sender);
        handler.handleTextMessage(sender, new TextMessage(fileStartJson("t2", "note2.txt", 5)));
        ArgumentCaptor<TextMessage> secondCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(sender, never()).sendMessage(secondCaptor.capture());
    }
}
