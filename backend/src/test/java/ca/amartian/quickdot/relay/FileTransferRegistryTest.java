package ca.amartian.quickdot.relay;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FileTransferRegistryTest {

    private final FileTransferRegistry registry = new FileTransferRegistry();

    @Test
    void startedTransferIsFindable() {
        FileTransfer transfer = new FileTransfer("t1", "ROOM01", "p1", "photo.png", 1000);

        assertThat(registry.start(transfer)).isTrue();
        assertThat(registry.find("t1")).contains(transfer);
    }

    @Test
    void startingTheSameTransferIdTwiceFails() {
        registry.start(new FileTransfer("t1", "ROOM01", "p1", "photo.png", 1000));

        boolean second = registry.start(new FileTransfer("t1", "ROOM01", "p2", "other.png", 500));

        assertThat(second).isFalse();
    }

    @Test
    void removedTransferIsGone() {
        registry.start(new FileTransfer("t1", "ROOM01", "p1", "photo.png", 1000));

        registry.remove("t1");

        assertThat(registry.find("t1")).isEmpty();
    }

    @Test
    void hasActiveUploadReflectsSenderState() {
        registry.start(new FileTransfer("t1", "ROOM01", "p1", "photo.png", 1000));

        assertThat(registry.hasActiveUpload("p1")).isTrue();
        assertThat(registry.hasActiveUpload("p2")).isFalse();
    }
}
