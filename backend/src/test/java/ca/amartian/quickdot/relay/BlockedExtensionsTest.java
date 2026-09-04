package ca.amartian.quickdot.relay;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BlockedExtensionsTest {

    private final BlockedExtensions blockedExtensions = new BlockedExtensions(
            "exe,bat,cmd,com,msi,scr,ps1,sh,bash,vbs,vbe,jar,apk,deb,dmg,pkg,app,gadget,cpl,msc,wsf,hta,reg");

    @Test
    void blocksKnownDangerousExtensions() {
        assertThat(blockedExtensions.isBlocked("virus.exe")).isTrue();
        assertThat(blockedExtensions.isBlocked("script.SH")).isTrue(); // case-insensitive
        assertThat(blockedExtensions.isBlocked("installer.MSI")).isTrue();
    }

    @Test
    void allowsSafeExtensions() {
        assertThat(blockedExtensions.isBlocked("photo.png")).isFalse();
        assertThat(blockedExtensions.isBlocked("notes.txt")).isFalse();
        assertThat(blockedExtensions.isBlocked("archive.zip")).isFalse();
    }

    @Test
    void fileWithNoExtensionIsNotBlocked() {
        assertThat(blockedExtensions.isBlocked("README")).isFalse();
    }
}
