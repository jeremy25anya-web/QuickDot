package ca.amartian.quickdot.relay;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ChunkFrameTest {

    @Test
    void roundTripsThroughEncodeDecode() {
        byte[] payload = "hello quickdot".getBytes(StandardCharsets.UTF_8);
        ChunkFrame original = new ChunkFrame("transfer-123", 7, payload);

        ChunkFrame decoded = ChunkFrame.decode(original.encode());

        assertThat(decoded.transferId()).isEqualTo("transfer-123");
        assertThat(decoded.sequence()).isEqualTo(7);
        assertThat(decoded.payload()).isEqualTo(payload);
    }

    @Test
    void handlesEmptyPayload() {
        ChunkFrame original = new ChunkFrame("t1", 0, new byte[0]);

        ChunkFrame decoded = ChunkFrame.decode(original.encode());

        assertThat(decoded.payload()).isEmpty();
    }
}
