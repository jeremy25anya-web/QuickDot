package ca.amartian.quickdot.relay;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MimeSnifferTest {

    private final MimeSniffer sniffer = new MimeSniffer();

    @Test
    void windowsExecutableHeaderIsDangerous() {
        byte[] mz = new byte[64];
        mz[0] = 'M';
        mz[1] = 'Z';

        String detected = sniffer.detect(mz);

        assertThat(sniffer.isDangerous(detected)).isTrue();
    }

    @Test
    void elfExecutableHeaderIsDangerous() {
        byte[] elf = {0x7f, 'E', 'L', 'F', 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0x3e, 0};

        String detected = sniffer.detect(elf);

        assertThat(sniffer.isDangerous(detected)).isTrue();
    }

    @Test
    void shellScriptShebangIsDangerous() {
        byte[] script = "#!/bin/bash\necho hi\n".getBytes(StandardCharsets.UTF_8);

        String detected = sniffer.detect(script);

        assertThat(sniffer.isDangerous(detected)).isTrue();
    }

    @Test
    void plainTextIsNotDangerous() {
        byte[] text = "just a normal message".getBytes(StandardCharsets.UTF_8);

        String detected = sniffer.detect(text);

        assertThat(sniffer.isDangerous(detected)).isFalse();
    }

    @Test
    void pngHeaderIsNotDangerous() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

        String detected = sniffer.detect(png);

        assertThat(sniffer.isDangerous(detected)).isFalse();
    }
}
