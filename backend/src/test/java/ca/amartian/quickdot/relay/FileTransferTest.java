package ca.amartian.quickdot.relay;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class FileTransferTest {

    @Test
    void digestHexMatchesSha256OfAllChunksInOrder() throws Exception {
        FileTransfer transfer = new FileTransfer("t1", "ROOM01", "p1", "notes.txt", "text/plain", 100);

        transfer.addBytes("hello ".getBytes(StandardCharsets.UTF_8));
        transfer.addBytes("world".getBytes(StandardCharsets.UTF_8));

        String expected = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest("hello world".getBytes(StandardCharsets.UTF_8)));
        assertThat(transfer.digestHex()).isEqualTo(expected);
    }

    @Test
    void addBytesReturnsCumulativeCount() {
        FileTransfer transfer = new FileTransfer("t1", "ROOM01", "p1", "notes.txt", "text/plain", 100);

        assertThat(transfer.addBytes(new byte[10])).isEqualTo(10);
        assertThat(transfer.addBytes(new byte[5])).isEqualTo(15);
        assertThat(transfer.bytesReceived()).isEqualTo(15);
    }
}
