package ca.amartian.quickdot.relay;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Binary wire framing for {@code file-chunk} (spec §5: "binary frame,
 * prefixed/framed with transferId + sequence"):
 *
 * <pre>
 * [4 bytes: transferId UTF-8 byte length (int, big-endian)]
 * [N bytes: transferId, UTF-8]
 * [8 bytes: sequence number (long, big-endian)]
 * [remaining bytes: chunk payload]
 * </pre>
 */
public record ChunkFrame(String transferId, long sequence, byte[] payload) {

    public ByteBuffer encode() {
        byte[] idBytes = transferId.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(4 + idBytes.length + 8 + payload.length);
        buffer.putInt(idBytes.length);
        buffer.put(idBytes);
        buffer.putLong(sequence);
        buffer.put(payload);
        buffer.flip();
        return buffer;
    }

    public static ChunkFrame decode(ByteBuffer buffer) {
        int idLength = buffer.getInt();
        byte[] idBytes = new byte[idLength];
        buffer.get(idBytes);
        long sequence = buffer.getLong();
        byte[] payload = new byte[buffer.remaining()];
        buffer.get(payload);
        return new ChunkFrame(new String(idBytes, StandardCharsets.UTF_8), sequence, payload);
    }
}
